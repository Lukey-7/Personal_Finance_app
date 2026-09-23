# FinTrack v1.1.0 device test

Run on the `fintest` AVD (Android 14, x86_64) on 2026-09-22, against the **signed release** APK, using a
seeded SMS inbox of 17 messages designed so that every new rule has something to get right or wrong.

The app sets `FLAG_SECURE`, so screenshots come out black. Verification reads the accessibility tree
(`uiautomator dump`) instead, which is a better way to check numbers anyway.

## The seeded inbox

| # | Sender | Message (abbreviated) | Should be |
|---|---|---|---|
| 1 | AD-SBIINB | credited by Rs.45,000.00 ... ACME CORP SALARY | income 45,000 |
| 2 | VM-HDFCBK | Payment of Rs.12,500.00 received towards your Credit Card | **transfer** (card bill) |
| 3 | VM-HDFCBK | Rs.5,000.00 debited ... ZERODHA BROKING SIP | **investment** |
| 4 | KOTAKB | Rs 2000.00 withdrawn ... at ATM | cash |
| 5 | VM-HDFCBK | Rs.3,000.00 debited ... self transfer to a/c XX9876 | **transfer** |
| 6 | VM-HDFCBK | Rs.250.00 debited ... swiggy.upi@axisbank (UPI Ref No 422312345678) | expense 250 |
| 7 | JD-PAYTMB | Paid Rs.250 to Swiggy ... UPI Ref: 422312345678 | **duplicate of #6** |
| 8 | VK-ICICIB | INR 1,299.00 spent ... at AMAZON | expense 1,299 |
| 9 | VM-HDFCBK | Rs.899.00 debited ... netflix@icici ... **Click here if not you** | expense 899 |
| 10 | VM-ICICIB | **Refund** of INR 450.00 credited ... from AMAZON | refund 450 |
| 11 | VM-HDFCBK | Rs.50.00 debited ... chaipoint@ybl | expense 50 |
| 12 | VM-HDFCBK | Rs.50.00 debited ... autowala@paytm | expense 50 (**not** a dup of #11) |
| 13 | JD-PAYTMB | Paid Rs.120 to Zomato ... **Complete your KYC** | expense 120 |
| 14 | VM-HDFCBK | 123456 is your OTP ... | ignored |
| 15 | BZ-OFFERS | Get up to Rs.500 cashback ... Apply now | ignored |
| 16 | VM-HDFCBK | Rs.999 **will be debited** ... autopay | ignored |
| 17 | +919876543210 | personal message | never read |

Expected v1.1 totals for September 2026: gross spend 4,668, refunds 450, **net spend 4,218**,
income 45,000, transfers out 3,000, transfers in 12,500, investments 5,000.

## Part 1 - what v1.0.0 did with the same inbox (the "before" picture)

Installed the v1.0.0 release built from the `v1.0.0` tag, granted SMS, let it import.

| Figure | v1.0.0 |
|---|---|
| "Spent in Sep 2026" | **₹25,298** |
| Income | **₹45,450** |
| Transactions | 12 |

Every bug the release set out to fix showed up:

- **Swiggy ₹250 listed twice**, once from HDFC Bank and once from Paytm - the same payment double counted.
- **The ₹450 Amazon refund appears as income** (`Amazon · Income · +₹450`), while the ₹1,299 purchase it
  refunds still counts as spend.
- **The ₹12,500 credit-card bill payment counted as spend**, filed under Other.
- **The ₹3,000 self-transfer counted as spend**, as `Payment (HDFC Bank)`.
- **The ₹5,000 SIP counted as spend.**
- **The Zomato ₹120 message was silently dropped** by the "login/KYC" filter - it is not in the list and
  there is no record anywhere that it was seen.

## Part 2 - upgrade v1.0.0 -> v1.1.0, no data wipe

Installed v1.1.0 over the top (same signing key), no `pm clear`.

- App launched normally, **no crash, no data loss**: all 12 transactions still present.
- Room migration 1->2 ran clean; no Room/SQLite errors in logcat.
- Dashboard now reads: net spend **₹20,298**, income ₹45,450, savings ₹25,152, and a separate
  **Investments ₹5,000** line under "Not counted as spend".
  - ₹20,298 = the old ₹25,298 minus the ₹5,000 SIP, which the migration classified as `INVESTMENT`
    from its category. Migrated rows keep heuristic flows (credits -> income, ATM -> cash,
    investment/transfer categories -> their flow, everything else -> expense), because the original SMS
    bodies were never stored and cannot be re-classified. The card-bill and self-transfer rows stay
    counted as spend until re-imported or corrected by hand - expected, and correctable per transaction.

## Part 3 - the bug this test found, and the fix

Running **Rescan last 12 months** on the upgraded install should have found everything already known.
The SMS log instead reported **Saved 8, Duplicate 5, Ignored 3** - eight messages were imported a
second time, so eight transactions were double counted.

**Cause.** Two v1.1 changes interact. The SMS hash changed (sender + normalised body + day), so no
legacy row matched by hash and every message was re-parsed. The fuzzy duplicate check then compares
timestamps within ±10 minutes - but v1.0.0's `DateExtractor` stored a body-dated transaction at
**midnight** (it took the date and dropped the time of day), while v1.1 keeps the SMS time of day. The
stored row and the re-parsed one therefore sit hours apart and the window never matched.

**Fix.** `TransactionRepository.findLikelyDuplicate` now also searches the candidate's whole calendar
day. Matches inside the ±10 minute window keep the original, looser test (same merchant, or a different
bank/app reporting it, or a generic merchant). Matches that are hours apart on the same day must have
**the same merchant**, so two different payments that happen to share an amount are still both kept. A
pair of references that both exist and disagree still means two genuine payments, so a real repeat
purchase survives.

Four regression tests were added in `DuplicateDetectionTest`, including
`legacyMidnightRowIsMatchedOnRescan`, which reproduces this exact upgrade case.

A second defect showed up in the same run: the card-bill payment was listed with the merchant
**"your Hdfc Bank Credit Card Xx3344"**. The `towards|for` merchant pattern excluded `a/c`, `account`
and `card` but not `your`, so it captured the customer's own card as if it were a payee. The pattern
now excludes `your`, `ur` and `my`, covered by `ownCardIsNotUsedAsMerchant`.

A third change came out of the same observation. On a duplicate merge the code kept the richer record
but left its flow alone, so a card-bill payment carried over from v1.0.0 stayed counted as spend even
after its SMS was re-read. A merge now always adopts the freshly parsed **flow and category**: the
importer is holding the full message text, whereas a migrated row only ever had a flow guessed from its
category. A rescan is therefore the moment a mis-filed transfer corrects itself. Covered by
`rescanCorrectsTheFlowOfALegacyRow`.

## Part 4 - results after the fixes

**Upgrade path, re-run from a clean v1.0.0 install:**

| | before fix | after fix |
|---|---|---|
| Saved | 8 | **1** (only the message v1.0.0 had dropped) |
| Duplicate | 5 | **12** (every row already known) |
| Ignored | 3 | 3 |

No crashes, no Room errors, all data intact.

**Fresh v1.1.0 install against the same inbox** - `Scanned 16 SMS: 12 added, 0 to review, 1 duplicate,
3 ignored`, and every figure matches the prediction exactly:

| Figure | Expected | Shown |
|---|---|---|
| Net spend | 4,218 | **₹4,218** |
| Gross spend | 4,668 | ₹4,668 |
| Refunds | 450 | ₹450 |
| Income | 45,000 | ₹45,000 |
| Transfers (card bill + self) | 15,500 | ₹15,500 |
| Investments | 5,000 | ₹5,000 |
| Food (250+120+50) | 420 | ₹420 |
| Shopping (1,299 - 450 refund) | 849 | ₹849 |
| Entertainment | 899 | ₹899 |
| Cash / ATM | 2,000 | ₹2,000 |
| Other (autowala) | 50 | ₹50 |

Categories sum to 4,218, which is the headline net spend - the arithmetic on screen closes. The Swiggy
pair collapsed to one transaction, the refund reduced Shopping instead of posing as income, the card
bill and self-transfer sat outside spend, and the two ₹50 payments five minutes apart both survived.

## Part 5 - OCR and bill split

Receipt rendered as a PNG (TRUFFLES, 3 items, CGST/SGST, service charge, grand total ₹990) and pushed
to the gallery.

- The **Android Photo Picker** opened with the "This app can only access the photos you select" notice:
  no storage permission, as designed.
- OCR ran on-device and reported **"Read 3 items, total ₹990.00"**. Logcat shows the **bundled** model
  loading from inside the APK (`mlkit-google-ocr-models/gocr/.../model.tflite`, "Finished preloading a
  recognizer for Latn") - nothing was downloaded.
- Extracted correctly: merchant `TRUFFLES`, total `990`, date `12 Sep 2026` (from the bill, not today),
  `Paneer Tikka 1 x 280`, `Veg Biryani 2 x 220`, `Masala Chaas 3 x 60`.
- Split by item across 3 people: **₹330.04 + ₹329.98 + ₹329.98 = ₹990.00**, exact to the paisa, with
  the remainder going to the payer.
- After saving: net spend went from ₹4,218 to **₹4,548** - only the ₹330.04 share, not the ₹990 bill -
  and the Split tab showed **₹660 owed to you**, ₹330 from each person.

No crashes anywhere in the run.


---

# Second pass - Buro restyle, the four open problems, and the remaining flows

Same emulator, 2026-09-22, against the signed release APK unless noted.

## The four problems that were open

| # | Problem | Fix | Verified |
|---|---|---|---|
| 1 | v1.0-era duplicate rows survive an upgrade | **Clean up duplicates** in Settings: finds same amount + direction + day where the references agree, a different app reported each, or one has no real merchant; shows what it would remove before touching anything | v1.0.0 import (double-counted Swiggy) -> upgrade -> sweep found *"1 duplicate worth ₹250 - keeping the HDFC Bank record, removing the Paytm one"*; after removal net spend fell from ₹20,298 to **₹20,048**, exactly ₹250, and a re-scan reported "No duplicates found" |
| 2 | By-item split produced ₹330.04 / ₹329.98 / ₹329.98 | Each item's leftover paise are pooled and settled once at the end instead of going to the payer item by item | ₹990 across 3 is now exactly **₹330 / ₹330 / ₹330**; a pooled-remainder case still sums to the exact total |
| 3 | 68 MB APK | ABI splits, universal APK still built for sideloading | **arm64-v8a 22.5 MB**, armeabi-v7a 16.8 MB, x86_64 23.6 MB, universal 66.3 MB |
| 4 | Stock Material 3 UI | Restyled to the Buro reference | See below |

## The restyle

Palette sampled directly from `stitch_buro_fintech_app/`: page `#FDFCFB`, cards `#FFFFFF` with a 1px
`#E7E6E6` hairline and **no shadows**, tonal panel `#F4F5F6`, accent `#0000FF`, gain `#00A62D`, loss
`#B50000`. Inter is bundled (OFL, `licenses/Inter-OFL.txt`) in four weights, with the reference's tight
tracking on large figures.

- Dashboard leads with the figure itself - `NET SPEND · SEP 2026` over a 56sp `₹4,218` - rather than a
  filled card, with the arithmetic in a tonal panel underneath.
- Transaction rows use the reference's shape: tinted circular initial, merchant, quiet metadata, amount
  right-aligned over a secondary line.
- Chips are pills (soft accent fill when selected, plain text otherwise); the bottom bar is a floating
  white pill.
- Two layout bugs fixed on the way: nested Scaffolds were both applying the status-bar inset, leaving a
  large gap above every title; and `FLAG_SECURE` is now applied only in release builds, so debug builds
  can be screenshotted for docs and QA. Release builds are unchanged.

**Nothing behavioural changed:** the same seeded inbox still produces net spend **₹4,218**, gross
₹4,668, refunds ₹450, income ₹45,000, savings ₹40,782.

## Remaining flows

| Flow | Result |
|---|---|
| **Airplane mode** | Everything works offline. A full rescan with the radio off reported `16 SMS: 0 added, 0 to review, 13 duplicates, 3 ignored` - nothing re-imported, no network needed |
| **Camera capture** | `Photo` launches the system camera through our FileProvider with no `SecurityException` or `FileUriExposedException`; the capture returns and OCR runs on it. The emulator only offers its synthetic scene, so the result was the graceful `No text found. Try a sharper, well-lit photo.` **OCR quality on a real receipt is proven through the gallery path** (TRUFFLES, ₹990, 3 items); camera quality still wants a real phone |
| **Review queue** | An ambiguous SMS (`Txn alert: a/c 1234 amount 500 processed`) queued with `Reason: type ambiguous · guessed ₹500`; entering details saved it and the SMS log flipped that row from Review to Saved (`Saved (2)`, `Review (0)`) |
| **Settlement** | ₹900 across 3 = exactly ₹300 each. Partial payment of ₹120 showed `₹120.00 paid · ₹180.00 left`; paying the rest showed `Settled`. Balances then correctly listed only Person 3 owing ₹300 |
| **CSV export** | System file picker wrote `fintrack-20260922-1517.csv`; contents verified - 12 rows with flow, reference and account columns, and the card-bill merchant now reads `Payment (HDFC Bank)` rather than `your Hdfc Bank Credit Card Xx3344` |
| **Clear all data** | Wipes transactions, budgets, splits, SMS log and key, and returns to onboarding |
| **AI summary** | With no key the app offers only the key field and *What would be sent?*; nothing leaves the phone. **The real API call is untested - it needs your own OpenAI key, which I did not ask for or enter** |

## A privacy bug this pass found

Taking a photo that yields no text left `bill_capture.jpg` (73 KB) sitting in the app cache, because the
file was only deleted on the success path. That contradicts the promise that bill photos are not stored.
The capture is now discarded when recognition finishes **either way**, and again when the screen is
disposed (cancelled capture, back button). Verified: after a failed capture the cache no longer contains
the file.

## Still not covered

- OCR accuracy on real, creased, dimly lit receipts photographed with a real camera.
- The OpenAI request itself.
- Any real bank's SMS wording beyond the seeded set.

## SMS accuracy hardening (2026-09-23)

Plan: `docs/PLAN-sms-accuracy.md`. Unit tests: **118 tests, 0 failures** (`testDebugUnitTest`), including a
36-message regression corpus (`SmsCorpusTest`) covering the nine audit bugs.

Device run on `fintest` (emulator-5554), debug build installed with `install -r` over the existing v2
database, so the v2 -> v3 migration ran on real data. Then Settings -> *Rescan the last 12 months*.

| Figure (Sep 2026) | Before install | After upgrade + rescan |
|---|---|---|
| Net spend | ₹4,218 | **₹4,218** |
| Income | ₹45,000 | ₹45,000 |
| Gross spend | ₹4,668 | ₹4,668 |
| Refunds & cashback | ₹450 | ₹450 |
| Savings | ₹40,782 | ₹40,782 |
| Transfers & card bill payments (out) | ₹15,500 | ₹3,000 |
| Transfers in | (not shown) | ₹12,500 |
| Investments | ₹5,000 | ₹5,000 |
| Review queue | 1 | 1 |

Rescan result: *Scanned 17 SMS: 0 added, 0 to review, 14 duplicates, 3 ignored*. Nothing was re-imported or
lost. Seeded #2 (card bill payment received) was stored as a debit by the old parser; the rescan repaired it
in place to a credit, so it moved from transfers out to transfers in. That is the split this document always
expected (transfers out 3,000, in 12,500). No total that counts as spend or income changed.

One ANR occurred while another Gradle build was loading the host. The trace showed the main thread waiting on
the render thread (`HardwareRenderer.setStopped`) with no app frames, so the emulator was starved, not the app
hung. A relaunch did not reproduce it.

Still not covered: bank SMS wording beyond the corpus. Each misread message reported from a real phone should
become one more `SmsCorpus` row.
