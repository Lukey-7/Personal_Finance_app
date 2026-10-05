# Changelog

All notable changes are recorded here. Versions follow [Semantic Versioning](https://semver.org): `MAJOR.MINOR.PATCH`.
Each release is a git tag `vX.Y.Z` with the signed APK attached on the GitHub Releases page.

## [1.3.0] - 2026-10-05

Focus: **everyday money beyond the SMS**: subscriptions, bills and EMIs, cards, goals, tax, net worth, a widget,
backups and on-phone answers. Still no account, no server and no analytics. Existing data is migrated in place
(database version 7: new tables only, on top of 1.2.1's version 6).

### New: Money tools (Home and Insights, top bar)
- **Subscriptions.** Finds charges that repeat weekly, monthly, quarterly or yearly at a steady amount, plus UPI
  AutoPay / mandate / NACH charges from the first one. Shows what they cost a month and a year, flags price rises,
  and remembers "Keep", "Not one" and "I cancelled it" (and tells you if a cancelled one charges again).
- **Bills & EMIs.** Rent, phone, insurance or a loan: due dates (monthly to yearly, month-end safe), paid
  automatically when a matching payment shows up near the due date, or marked paid by hand. Loans work out the EMI
  and show "EMI 10 of 36, Rs X still owed". Card statement SMS create and move the card's bill by themselves.
- **Credit cards.** Each card's billing cycle: spend on it so far (refunds taken off), statement and due dates,
  and an estimate of rewards at your rate.
- **Goals.** Save toward a target and date: progress, what it needs a month, on track or behind, with last month's
  savings offered as the top-up.
- **Tax helper.** 80C, 80D, 80CCD(1B) NPS, 80E, 80G, 24(b) and rent payments per financial year, from payee names;
  tag or untag any payment (also from the transaction screen); CSV for your records. Not tax advice.
- **Net worth.** Bank balances from "Avl Bal" in SMS, mutual funds from a CAMS / KFintech CAS PDF (password used once,
  never stored), FDs, gold and debts you type in, and loans from Bills; a month-by-month history.
- **Ask FinTrack.** Questions like "food last month", "Swiggy in September", "how much did I save?", "any bills
  due?", answered by rules on the phone, plus "This month in words". On phones with Android AICore (Pixel 9 and
  later, Galaxy S24 and later, some others), questions the rules do not understand go to **Gemini Nano on the
  phone**, with your totals only; its answers are labelled. Settings shows whether the model is ready.

### New elsewhere
- **Reminders** (Settings, off by default): a notification 3 and 1 days before a bill and 2 days before a
  subscription renews; amounts stay off the lock screen.
- **Home-screen widget**: spend this month, budget left and the next bill, with amounts hidden unless you choose to
  show them, and one-tap "+ Expense" / "+ Cash". Long-press the app icon for the same quick add.
- **Encrypted backup** (Settings → Backup): every table in one file locked with your passphrase (AES-256-GCM,
  PBKDF2), saved wherever you choose; restore replaces everything in one step or changes nothing.

### Accuracy
- **Refunds and reversals** are paired with the purchase they give money back for (same reference, or same merchant
  within 60 days, never more than was paid). A failed payment that came straight back is hidden from Activity
  ("Show reversed payments") and a refund the bank worded like income is counted as a refund. Undo any pairing.
- **The parser learns from Review**: confirming a message teaches FinTrack that sender's wording (numbers and names
  masked), so the next one needs no review. See and delete learned shapes in Settings.
- **TRAI sender suffixes** (-S, -T, -G) are read correctly, including headers shown without an operator prefix
  ("HDFCBK-S"); promotional **-P** senders are skipped.
- **Possible misses** in the SMS log: skipped messages that still carry an amount and an account, one tap to Review.

### Privacy
- New libraries: WorkManager (reminders), Glance (widget) and ML Kit GenAI Prompt (Gemini Nano). The README's
  OCR section is replaced by **What ML Kit sends**: ML Kit (text recognition since v1.1, and now GenAI) carries
  Google's anonymous usage logging, which is left on. Earlier README claims that it had none were wrong.
- Toolchain: Kotlin 2.1.21 and KSP 2.1.21-2.0.2 (KSP1 mode for Room), needed by the GenAI library.

### Fixed after review
- The same subscription paid by UPI and by card (or under the company's full name) is one subscription.
- Bills, cards and goals move on at midnight without waiting for new data.
- Backup and restore show progress while the passphrase key is worked out.

## [1.2.1] - 2026-09-28

Focus: **edge cases in split intelligence and statement import**, found with 130 new tests (each written to fail
first), a code review and a security review, then checked on an emulator. Existing data is migrated in place
(database version 6).

### Fixed: money counted wrong or lost
- Two identical payments in one statement or screenshot (two Rs 20 teas) are both kept; before, the second was dropped.
- Placeholder reference numbers ("0", "-", zeros) no longer make a new row look like a months-old duplicate.
- A row whose date can't be read goes to review instead of vanishing.
- Accepting or rejecting a split never overwrites your own edit; accepting after you correct the bill splits the
  corrected amount.
- One friend's transfer can't pay for a manual split and an automatic one at the same time; a manual split made later
  takes its friend's transfer back from an automatic guess.
- After "Not a split", those transfers are only suggested for another payment, never applied on their own.
- An SMS arriving for a settled transfer, or while an import preview is open, no longer counts money twice.
- "Clean up duplicates" keeps the copy a split uses; undoing an import keeps rows another import or an SMS also has.
- Deleting (or un-importing) a transfer that settled a manual split makes that share owed again.
- An AI outage no longer undoes AI-found splits; only readable AI answers are cached.

### Fixed: reading files and screenshots
- Dr/Cr before or after amounts, trailing minus, "Sept", ISO dates with +05:30, Excel formula noise and scientific
  numbers, UTF-16 files, stray quotes, late or two-line headers, card-number columns, footers vs wrapped narrations,
  narration lines above or below their date line in PDFs, rows after a statement summary.
- Screenshots: October dates, "Sep 12" day headers, month headers, a misread rupee sign with Indian grouping or paise,
  failed / pending / requested rows skipped even when the status line says more.
- Groups of up to 100 people; senders with no name ("AK", a phone number) count as one person; placeholder names
  ("Credit", "Friend") and "cash back" are never a friend paying back.
- Cashback is no longer categorised as Cash / ATM; the review banner says "items" for SMS and statement rows alike.

### Security
- Spreadsheets: zip bombs, huge column references and XML DOCTYPE / entity tricks are refused (an external entity
  could read a local file before). The ".xls" HTML reader runs in linear time.
- PDFs load with a memory limit and huge pages render at a capped size; an empty password counts as no password.

## [1.2.0] - 2026-09-28

Focus: **split intelligence** (when you pay for a group and friends pay you back, only your share counts) and
**importing statements and screenshots**. Existing data is migrated in place (database version 5).

### Split intelligence (new)
- FinTrack spots group payments by itself: one payment, then friends sending back about the bill divided by the
  number of people, within 2 weeks. Your spend counts only your share; their transfers are marked "Split settlement"
  instead of income. Example: Rs 12,000 for 12 at dinner, 11 friends pay Rs 1,000 back → Rs 1,000 spent.
- Handles shared cabs next to the dinner, one transfer covering two bills (Rs 1,200 = dinner Rs 1,000 + cab Rs 200),
  a share paid in two parts, rounded shares (Rs 1,030 for Rs 1,028.33), friends who pay late (they join the split
  when they pay) and friends who haven't paid yet (their share stays in your spend until they do).
- Money collected **before** you pay (a trip) is recognised too, and always waits for your yes.
- Clear cases apply automatically with an "Auto-split" tag and one-tap undo; unsure ones appear on Home as
  "Looks like a shared payment" with Yes / Not a split. Every split shows why it was found. "Not a split" is remembered.
- **AI for the unclear cases** (uneven shares): with an OpenAI key, the app asks the model about payments the rules
  cannot explain. Only amounts, days and payment types are sent; people appear as "Person A", never by name. Every AI
  answer passes the same hard checks before it can change a number: a wrong or strange answer is thrown away.
- Manual splits: "Settle" now offers the friend's actual transfer, so it stops counting as income.
- Home shows friends' paybacks on their own line, "Paid back by friends".

### Import statements and screenshots (new)
- Bank statements from any bank as **PDF** (including password-protected and scanned), **Excel** (.xlsx, and the
  HTML ".xls" many banks send) or **CSV**, and **payment-app screenshots** (Google Pay, PhonePe, Paytm, Amazon Pay).
  Settings → Import statements, or the upload icon on Activity.
- No per-bank templates: columns are found by what they mean (Date / Narration / Withdrawal / Deposit / Balance and
  their synonyms), with a fallback that reads the content itself.
- The running balance is checked on every row; a row that doesn't add up goes to the review list instead of being guessed.
- Payments you already have from SMS are recognised and skipped (and an SMS arriving after an import is not counted twice).
- A preview shows what will be added before anything is saved; every import can be undone.
- On-device OCR misreads the ₹ sign in screenshots (as a 7, a letter, or not at all); the width of each amount's box
  decides the right reading.

### Device test
- `docs/DEVICE-TEST-v1.2.md`: SMS, CSV, locked PDF, Excel and screenshots on an emulator with real OpenAI calls; nine
  issues found there are fixed and covered by tests.

## [1.1.2] - 2026-09-28

### Fixed
- A rescan no longer puts the full bank amount back on a transaction you split in v1.1.0. Database version 4 (no new columns) records the bank amount on those rows, and restores your share where a v1.1.1 rescan already reset it.
- A rescan only corrects rows an older version stored the wrong way round. It never changes an amount, so amounts you corrected before v1.1.1 stay as you left them.
- Transactions imported by v1.0 that you delete stay deleted after a rescan.
- A same-day refund of the same amount no longer merges into a v1.0 debit and turns it into income.
- The date on a new split can be changed with TalkBack, a keyboard or switch access, and a very quick tap no longer misses.
- "Rs 500 paid to you by X" is income, not a spend.
- Bill items priced without decimals at ₹10,000 or more ("Speaker 12500") are no longer dropped.
- Leaving the bill screen while a photo is being read now stops the reading.
- Deletions and dismissals are remembered even after old SMS log rows are cleaned up.
- A debug APK carrying a built-in OpenAI key is named "-personal", like a personal release build.
- Reading one message's text for the SMS log detail can no longer crash on a missing column. The app is no longer hidden from tablets and Chromebooks on Play because it asks for SMS access.

## [1.1.1] - 2026-09-23

### SMS accuracy
- Direction comes from the first verb about your own account, so "Acct debited ...; SHOP credited" (ICICI), "Rs 500 Dr. ... Cr. to x@ybl" (Bank of Baroda) and "You paid ... cashback credited" are spends, not income or refunds.
- An account number next to "Rs" ("A/c XX1234 Rs 750") is never taken as the amount.
- Real debits with an OTP footer, balance-first alerts, refunds for cancelled orders and reversals of failed payments are no longer dropped. "Has not been debited" is still ignored.
- Two identical card alerts on one day are two payments. A payment reported by a second app after you split it is recognised as the same payment.
- "Rescan the last 12 months" no longer brings back transactions you deleted or review items you dismissed. It recovers messages an older version ignored, and corrects rows an older version stored backwards, except rows you edited yourself.
- Database version 3 (adds two columns; existing data migrates in place).

### Bill reading (OCR)
- Bills printed in Hindi: ML Kit's bundled Devanagari recogniser now runs alongside the Latin one, and its reading is used when a bill actually contains Hindi script. Hindi labels (कुल योग, उप योग, जीएसटी, छूट), Devanagari digits and "रु." are understood. About 4 MB more per APK.
- Item / Qty / Amount bills now keep the quantity and unit price instead of gluing the quantity onto the name.
- Tilted photos no longer pair each price with the item above it: rows are straightened using the recognised text's angle.
- OCR slips in amounts are repaired ("360.0e", "1,551. 00", "120,00"), so a misread digit no longer drops a line or the total.
- Invoice numbers and dates are no longer read as items, and "You saved Rs.50" is no longer counted as a second discount.
- Checked end to end on the emulator with photographed test receipts (clean, tilted, faded, angled, dim, Hinglish): items, quantities, totals and discounts all read exactly. Bills that print prices in Devanagari digits still need their item prices typed in; ML Kit misreads those digits.

## [1.1.0] - 2026-09-23

Focus: numbers you can trust, a log of every SMS, and bill splitting with on-device OCR. Existing data is migrated in place.

### Calculation accuracy
- Every transaction now has a **flow**: expense, income, refund, transfer, investment, cash or settlement. Only expenses (and, by default, ATM cash) count as spend. Credit-card bill payments, self-transfers and investments are shown separately and never inflate spend. Refunds and cashback reduce spend instead of counting as income.
- **Net spend = gross spend - refunds; savings = income - net spend.** The dashboard shows the arithmetic, and every number is tappable to list the transactions behind it.
- Amounts are stored as whole paise (integers), so totals never drift.
- **One payment, two SMS** (bank alert + UPI app alert) is now stored once. The parser extracts UPI/IMPS/RRN reference numbers; matching references, or the same amount within ten minutes from a different sender, are treated as duplicates and the richer record is kept.
- The same SMS seen by the live receiver and a later inbox scan hashes identically (sender + normalised body + day).
- Filters for promo/login/reminder wording no longer drop messages that clearly report money moving.
- Body dates without a time now keep the SMS time of day instead of midnight.

### SMS log
- New **SMS log** (Activity > SMS log, or Settings): every scanned message with outcome (saved / review / duplicate / ignored), reason and amount. Message text is not stored; tapping a row re-reads it from your inbox. Ignored messages can be sent to the review queue with one tap.

### Dashboard and summary
- Period picker (this month, last month, this week, custom range), daily average and month-end projection, top merchants, per-account totals, and a "not counted as spend" card.
- Budgets moved off the bottom bar to make room for Split; reachable from Home and Insights.

### Bill split (new)
- New **Split** tab. Take a photo or pick an image; text is read **on the phone** with ML Kit's bundled model (works offline). Totals, tax, service, discount and line items are extracted into editable fields, or type the total yourself.
- Split equally, by shares, by custom amounts, or by item with tax/service/discount spread proportionally. All maths in paise; parts always add up to the total.
- Only **your share** counts as your spend. If you paid, the matching SMS debit is trimmed to your share and the rest is tracked as owed to you. Balances, partial settlements, plain-text share sheet, CSV export.

### Look and feel
- Restyled to the Buro reference: warm off-white page, white cards separated by hairlines instead of
  shadows, one saturated blue for anything actionable, and numbers as the hero element. Inter is
  bundled (OFL).
- Dashboard leads with the net-spend figure itself, with the arithmetic in a tonal panel below.
- Pill chips and a floating pill bottom bar; transaction rows use tinted circular initials.
- Outline icons throughout, with one icon per category (budgets, top merchants, the spending legend,
  category chips), icons on Settings sections and SMS log statuses, and proper empty states.
- One 24dp gutter and spacing rhythm on every screen. Lists scroll underneath the bottom bar instead of
  stopping short of it, and always leave room to scroll their last row clear of the + button.
- Layouts hold up at large font sizes and on small phones: amounts never wrap, and fields and buttons
  stack instead of being cut off.
- Accessibility: the Settings switches and the "New split" button are now announced by name.

### Housekeeping
- **Clean up duplicates** in Settings finds the same payment stored twice - typically rows imported by
  v1.0.0, before the app could tell that a bank and a UPI app were reporting one payment. It shows what
  it would remove before anything is deleted.
- Per-ABI APKs: arm64 is ~22 MB instead of ~68 MB. A universal APK is still produced for sideloading.

### Under the hood
- Room schema v2 with a tested migration (no data loss). `fallbackToDestructiveMigration` removed.
- New professional navy/teal theme; dynamic colour disabled for consistency.
- AI summary (unchanged privacy model) now receives the corrected net figures, and *What would be sent?*
  is available before you save a key.
- By-item splits pool their rounding remainder once instead of per item, so a bill that divides evenly
  now looks like it (990 across 3 is 330 each, not 330.04 / 329.98 / 329.98).
- A captured bill photo is discarded whether or not recognition succeeds, and when the screen closes.
- `FLAG_SECURE` is applied in release builds only, so debug builds can be screenshotted for docs.

## [1.0.0] - 2026-09-17

First release.

- Layered, bank-agnostic SMS parser with confidence scoring and a manual review queue
- Encrypted on-device storage (Room on SQLCipher, Keystore-backed key)
- Rule-based auto-categorisation with manual override; manual add, edit, delete
- Dashboard, weekly and monthly trends, reduce-spending insights
- Per-category budgets with overspend alerts
- Optional, on-demand OpenAI monthly summary using aggregated totals only
- CSV export and clear-all-data
