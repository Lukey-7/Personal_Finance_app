# FinTrack v1.1 plan

Status key: `[ ]` not started, `[~]` in progress, `[x]` done and verified.

**Progress (2026-09-22):** Phases 0–5 implemented on branch `v1.1-accuracy-split`; 80 unit tests pass (parser, flows, dedupe, split maths, bill parser, insights, v1→v2 migration). Open items are marked `[ ]` below. Phases 6–7 (release) not started; device testing still to do.

## Goal

Make FinTrack a calculator you can trust. The focus is on:

- correct expense maths from SMS,
- a clear summary,
- a full log of every SMS the app looked at,
- a bill-split module with on-device OCR.

Everything is stored locally and encrypted, on Android.

## Ground rules

- **Local first.** SMS parsing, calculations, summaries, the SMS log, OCR, bill split and storage never touch the network.
- **AI mode stays.** It is optional and online, uses your own OpenAI key, and only runs when you tap it. It sends aggregated totals only, never SMS text, merchant names or account numbers. It is the only reason the `INTERNET` permission exists.
- **No data loss on update.** v1.0.0 users keep their transactions, budgets and review queue.
- **Every calculation rule gets a unit test.** Parser and maths code stays pure Kotlin with no Android imports, so it runs as a plain JVM test.
- The build adds no new cloud services, accounts, analytics or ads.

## Phase 0: Setup

- [x] Get a working JDK 17 for Gradle. `JAVA_HOME` is not set on this machine, so tests cannot run from the command line yet. Either point `JAVA_HOME` at Android Studio's bundled `jbr` folder or install Temurin 17.
- [x] Run `gradlew.bat testDebugUnitTest` and record the baseline. All existing parser and categoriser tests must pass before anything changes.
- [x] Work happens on branch `v1.1-accuracy-split` (already created, no commits yet).

## Phase 1: Calculation accuracy

This is the most important phase. Every item below is a way the current totals can be wrong.

### 1.1 Same SMS imported twice

**Problem.** `Hashing.smsHash` mixes the timestamp into the fingerprint. The live receiver (`SmsReceiver`) uses `timestampMillis`, which is the time the message was sent. The inbox scan (`SmsReader`) uses `Telephony.Sms.DATE`, which is the time it was received. These can differ by seconds or minutes, so one SMS can produce two different hashes and be saved twice.

- [x] Read `DATE_SENT` in `SmsReader` and use it for hashing when it is non-zero, falling back to `DATE`. The live receiver already uses the sent time, so the two paths then agree.
- [x] Alternatively, hash `sender + normalised body + calendar day`. Decide after checking real messages for repeated identical bodies on the same day. An example is two ₹10 payments to the same shop with the same wording and no reference number.
- [x] Test: the same message through both paths produces one transaction.
- [x] Migration note: existing rows keep their old hashes. After the update, a full rescan could re-import them under new hashes. Handle this with 1.2's fuzzy matcher, which will catch them as duplicates.

### 1.2 One payment, two SMS

**Problem.** A UPI payment often triggers an alert from the bank and another from the UPI app or the card issuer. Both are saved, so the spend is doubled.

- [x] Add a fuzzy duplicate check in `SmsImporter.process` before inserting. It matches on same type, same amount, timestamps within about 10 minutes, and either a different sender or a matching reference number.
- [x] Extract the UPI or transaction reference number in the parser (`Ref No`, `UPI Ref`, `RRN`, `Txn ID`) and store it in a new `refNumber` column. A matching reference is a certain duplicate. Without a reference, use the amount and time window rule.
- [x] When a duplicate is found, keep the richer record, meaning the one with a merchant and account, and log the other as `DUPLICATE` in the SMS log. Never silently drop it.
- [x] Tests: bank and app pair merges into one; two real ₹50 payments five minutes apart to different merchants both stay.

### 1.3 "Spend" includes things that are not spending

**Problem.** `InsightsEngine.summarize` adds up every debit. That counts self-transfers, credit-card bill payments, investments, wallet top-ups and ATM withdrawals as spend. A card bill payment is the worst case, because the purchases on that card were already counted when they happened.

- [x] Add a `flow` classification to each transaction: `EXPENSE`, `INCOME`, `TRANSFER`, `REFUND`, `INVESTMENT`, `CASH_WITHDRAWAL`. Store it as a column so you can override it per transaction.
- [x] Detect credit-card bill payments ("payment received towards your credit card", "CC bill", "card payment", CRED, BillDesk card payments) and mark them `TRANSFER`.
- [x] Detect self-transfers: a debit and a credit of the same amount within a short window across your own accounts, or text such as "self", "own account".
- [x] Spend total counts `EXPENSE` only. ATM withdrawals count as cash spend by default, with a setting to exclude them. Investments and transfers are shown as their own lines and never counted inside spend.
- [x] Tests for each rule, including a full month fixture with a known correct total.

### 1.4 Refunds and cashback counted as income

**Problem.** `Categorizer` sends every credit to `INCOME`. A ₹2,000 refund from Amazon shows as income while the original ₹2,000 still counts as spend. Both numbers are wrong.

- [x] Classify credits: salary, interest and dividends are `INCOME`. Refunds, reversals and cashback are `REFUND`. Credits from your own accounts are `TRANSFER`.
- [x] A `REFUND` reduces spend. It comes off the matching category when the merchant matches a recent debit, otherwise off the month's total.
- [x] Summary shows gross spend, refunds and net spend so the maths is visible.

### 1.5 Money stored as `Double`

**Problem.** Floating point drifts by paise across hundreds of sums, and splitting bills makes this worse.

- [x] Store amounts as `Long` paise in the database and domain model. Format to rupees only at the UI edge (`Format.kt`, `InsightsEngine.fmt`).
- [x] Migration converts existing values with `ROUND(amount * 100)`.
- [x] Budgets move to paise too.
- [x] CSV export still writes rupees with two decimals.

### 1.6 Parser fixes

- [x] The `promo`, `login` and `future` filters can drop real transactions. Examples are a debit SMS that ends "click here if not you", or one that mentions "KYC". Change the rule: if a strong transaction verb and an amount are both present, never ignore. Send the message to review instead.
- [x] `TypeDetector` lists "spent" in both the strong and weak debit lists, so it double-scores. Remove the duplicate.
- [x] The weak hints `"at "` and `"from "` match almost any sentence. Tighten them to word boundaries.
- [x] `DateExtractor` returns midnight when the body has a date but no time. That breaks ordering and the 10-minute duplicate window. Use the date from the body combined with the time of day from the SMS timestamp when they fall on the same day.
- [x] Review-queue resolve: confirm the saved transaction carries the review item's `smsHash`, so a rescan cannot re-queue the same SMS.
- [x] Add a test case for every change, as the README already requires.

### 1.7 Small engine fixes

- [x] `categoryTrends` sorts by pulling digits out of the title string. Replace it with a numeric field on `Insight`.
- [x] Recurring-subscription detection uses the first 12 characters of the merchant. Check it against real data for false merges, such as "Amazon Pay" and "Amazon Prime".

## Phase 2: Safe database migration

**Problem.** `AppDatabase` uses `fallbackToDestructiveMigration()`. Any schema change deletes all user data without warning.

- [x] Remove `fallbackToDestructiveMigration()`.
- [x] Write `MIGRATION_1_2` covering: amounts to paise, new `flow` and `refNumber` columns, the `sms_log` table, and the split tables from Phase 5.
- [x] Export schema `2.json` next to the existing `1.json`.
- [x] Add a migration test that opens a v1 database with sample rows, migrates it, and checks every row and total.
- [ ] Before shipping, test by installing the signed v1.0.0 APK with data, then installing v1.1.0 over it.

## Phase 3: SMS log

You asked to see everything the app did with your SMS. Right now ignored messages vanish with no record.

- [x] New `sms_log` table: `id`, `sender`, `receivedAt`, `outcome` (`SAVED`, `REVIEW`, `IGNORED`, `DUPLICATE`), `reason` (such as `otp`, `promo`, `low_confidence_45`), `amountPaise` if found, `transactionId` if saved, and `smsHash`.
- [x] Decision taken: **no body stored**. The log keeps sender, time, outcome, reason and amount; tapping a row re-reads the message from the inbox. The README currently promises raw text is kept only for the review queue. Options:
  - **No body (recommended).** The log shows sender, time, outcome, reason and amount. Tapping a row re-reads that one message from the phone's inbox on demand, so nothing extra is stored.
  - **Store body.** Simpler and works even if you delete the SMS, but it keeps a copy of all bank SMS inside the app. It would still be encrypted.
- [x] New **SMS Log** screen: filter by outcome, search by sender, tap to see details. It includes a "this was wrong" action that sends an ignored message to review or lets you add it manually.
- [x] Log retention: keep 12 months and prune older rows on import.
- [x] Import result dialog links to the log, filtered to that run.

## Phase 4: Summary and dashboard

The dashboard should answer "where did my money go" with numbers that add up.

- [x] Month summary card: income, gross spend, refunds, net spend and savings (income minus net spend). Transfers and investments are shown separately below.
- [x] Category breakdown using net amounts, with percentage of total.
- [x] Top merchants for the period.
- [x] Per-account or per-card totals, using the last four digits already captured.
- [x] Daily average and projected month-end spend.
- [x] Period picker: this month, last month, custom range, week view.
- [x] Every total is tappable and opens the list of transactions that make it up, so any number can be checked by hand.
- [x] The AI payload in `OpenAiClient.buildPayload` is updated to use the corrected totals. The "What is sent?" preview must still show the exact payload.

## Phase 5: Bill split with OCR

### 5.1 Input

- [x] New **Split** tab or a dashboard entry. There are three ways to start: take a photo, pick an image, or type an amount.
- [x] Image picking uses the Android Photo Picker, which needs no storage permission. The camera uses the system camera intent with a temporary file in app-private cache, so there is no `CAMERA` permission and no image is left behind. The cached image is deleted after OCR.
- [x] `FLAG_SECURE` stays on for these screens.

### 5.2 OCR (fully on-device)

- [x] Add ML Kit Text Recognition, **bundled model** (`com.google.mlkit:text-recognition`). The model ships inside the APK, so it works offline and never downloads anything. It adds about 4 MB per ABI.
- [ ] Note for the README: ML Kit is a Google library (still to document; the bundled model itself never downloads) and can send anonymous usage metrics when the network is available. Check whether this can be turned off in the manifest. Document the result honestly either way.
- [x] Bill parser (pure Kotlin, unit tested with sample OCR text):
  - find the grand total, preferring lines such as "Grand Total", "Total", "Net Amount", "Amount Payable", and falling back to the largest amount near the bottom,
  - find subtotal, tax (CGST, SGST, VAT, service charge), discount and tip or round-off,
  - extract line items as name, quantity and price where the layout allows,
  - extract restaurant or shop name and date.
- [x] Every extracted value is shown in an editable form before any calculation. OCR on crumpled or faded bills will make mistakes, so the user always confirms the total.
- [x] Sanity check: if items plus tax minus discount do not equal the total, show the difference and let the user fix it.

### 5.3 Split calculation

- [x] People: enter a count ("4 people") or names. Names are typed in; there is no contacts permission. Recently used names are remembered locally.
- [x] Split modes:
  - **Equal.** Total divided by the number of people.
  - **Custom amounts or percentages.** Must add up to the total, with the remaining amount shown live.
  - **Shares.** For example 2 shares for a couple, 1 for a single person.
  - **Per item.** Assign each line item to one or more people. Tax, service charge, discount and tip are spread in proportion to each person's item subtotal.
- [x] All maths in paise. Leftover paise from division go to the payer, so the parts always add up exactly to the total.
- [x] "Who paid" selector: you or someone else.
- [x] Tests: equal split with remainder (₹100 among 3), per-item with proportional tax, discount handling, shares, and the rule that parts always add up to the total.

### 5.4 Link to personal tracking

- [x] If you paid, the full amount will already arrive as an SMS debit. The split links to that transaction. It matches by amount and time, or you pick one. Only **your share** counts as your spend. The rest is recorded as "owed to you" and is not counted as expense.
- [x] If someone else paid, your share is added as a manual expense and recorded as "you owe".
- [x] When a friend pays you back and the credit SMS arrives, offer to match it to the open split. It is then classified as a settlement and not as income.
- [x] Balances view: who owes you, who you owe, and a mark-as-settled action. Supports partial settlement.

### 5.5 Storage and sharing

- [x] New tables: `splits` (total, merchant, date, payer, linked transaction, note), `split_people`, `split_items`, `split_shares` (person, amount, settled amount).
- [x] All in the same SQLCipher database. Bill images are not stored.
- [x] Share a split summary as plain text through the Android share sheet, for example "Dinner at X: Rahul ₹450, Priya ₹380". You choose to share it, and the app sends nothing itself.
- [x] Splits are included in CSV export and in "Clear all data".

## Phase 6: AI mode (kept, small improvements)

- [ ] No change to the privacy model: own key, tap to run, aggregates only.
- [ ] Feed it the corrected net figures from Phase 1 and Phase 4.
- [ ] Optional later idea, not part of v1.1: let AI mode help categorise unknown merchants. This would mean sending merchant names, so it needs its own clear opt-in. It is left out unless you ask for it.

## Phase 7: Release

- [ ] Update `README.md`: features, security table (OCR library, SMS log behaviour), project structure.
- [ ] Add a `CHANGELOG.md` entry for 1.1.0.
- [ ] Bump `appVersionCode` to 2 and `appVersionName` to `1.1.0` in `app/build.gradle.kts`.
- [ ] Check the R8 rules for ML Kit in `proguard-rules.pro`.
- [ ] Run all unit tests and the migration test.
- [ ] Manual test on a real phone: fresh install, upgrade from v1.0.0, full inbox scan, live SMS, bill photo split, and airplane-mode run to prove everything except AI works offline.
- [ ] Build the signed release APK, tag `v1.1.0`, and publish the GitHub release with the SHA-256. This step waits for your go-ahead.

## Order of work

1. Phase 0, then Phase 2's migration framework, since everything else changes the schema.
2. Phase 1, the accuracy fixes.
3. Phase 3, the SMS log.
4. Phase 4, the summary.
5. Phase 5, bill split.
6. Phase 6 and Phase 7.

## Decisions taken (defaults, change any of them)

1. **SMS log body:** not stored; re-read from the inbox on tap.
2. **ATM withdrawals:** counted as spend by default, with a Settings toggle to show them separately.
3. **Split entry point:** its own bottom tab. Budgets moved off the bar and is reachable from Home and Insights.
4. **JDK/Gradle:** `~/android-tools/jdk-17.0.20.1+1` and `~/android-tools/gradle-8.9` (the wrapper cannot download here). `build.sh` in the repo root wraps them; it is git-ignored.

## Still open

- Device test: fresh install, upgrade from v1.0.0, full inbox scan, live SMS, bill photo split, airplane-mode run.
- Manual test of the Robolectric-untestable parts: camera intent, photo picker, ML Kit recognition quality on real receipts.
- README/CHANGELOG/release (Phase 7).
