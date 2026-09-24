# Changelog

All notable changes are recorded here. Versions follow [Semantic Versioning](https://semver.org): `MAJOR.MINOR.PATCH`.
Each release is a git tag `vX.Y.Z` with the signed APK attached on the GitHub Releases page.

## [Unreleased]

### Fixed
- A rescan no longer puts the full bank amount back on a transaction you split in v1.1.0. Database version 4 (no new columns) records the bank amount on those rows, and restores your share where a v1.1.1 rescan already reset it.
- A rescan only corrects rows an older version stored the wrong way round. It never changes an amount, so amounts you corrected before v1.1.1 stay as you left them.
- Transactions imported by v1.0 that you delete stay deleted after a rescan.
- A same-day refund of the same amount no longer merges into a v1.0 debit and turns it into income.
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
