# How FinTrack works inside

The parser, its regression corpus, and the rules that turn transactions into the numbers on screen. The
[README](../README.md) has the short version.

## How the SMS parser works

The parser lives in `domain/parser`. It has no Android dependencies, so it runs as a plain JVM unit test.

1. **Filters** drop OTPs, promotions, future or scheduled debits, failed transactions, payment requests and statement reminders, but only when the message reports **no completed money movement**. A real debit with a "never share your OTP" footer, a refund for a "cancelled" order or a reversal of a "failed" payment is kept. A verb after "will be", "to be" or "if amount" does not count as completed. The one rule that always wins is "has not been debited".
2. **Type detection** reads direction from the **first** verb that reports money moving (`debited`, `spent`, `paid`, `sent`, `credited`, `received`, `Dr.`/`Cr.` after an amount...), because that verb is about your account and later mentions describe the other party. "Credited to beneficiary" and "transferred to" are debits; "paid you" and "transferred to your a/c" are credits. Keyword scoring is the fallback when no such verb exists.
3. **Amount extraction** handles `Rs`, `Rs.`, `INR` and `₹` before or after the number, plus Indian digit grouping. It ignores amounts that follow "Avl bal" or "limit", and digits glued to an account or card mask (`XX1234 Rs 750` is ₹750, not ₹1,234).
4. **Account extraction** keeps only the last four digits, from forms like `XX1234`, `a/c **1234` or `card ending 1234`.
5. **Bank detection** reads the sender ID or message body, and falls back to the sender ID itself.
6. **Merchant extraction** understands UPI handles, `UPI/P2M/...`, `Info:`, `at X`, `to X` and `from X`.
7. **Date extraction** reads a date from the body in many formats, and falls back to the SMS timestamp (keeping the SMS time of day when the body has only a date).
8. **Reference extraction** picks up `UPI Ref No`, `UPI:<number>`, `IMPS Ref`, `RRN`, `Txn ID`, `UTR` for duplicate detection.
9. **Flow classification** decides whether the transaction is an expense, income, refund, transfer (incl. credit-card bill payments and self-transfers), investment or cash withdrawal.

Each result gets a confidence score. Results below 60 go to the review queue instead of being saved.

### The SMS regression corpus

`app/src/test/java/com/pft/financetracker/SmsCorpus.kt` is a table of real-world SMS shapes, each with the outcome it must produce
(saved with direction, amount, flow, merchant and reference; sent to review; or ignored). `SmsCorpusTest`
runs every row through the parser, categoriser and flow classifier and reports **all** mismatches in one
failure, so a change that fixes one bank and breaks another is caught immediately.

**Fixing a misread message** is always the same two steps:

1. Add the message (card and account digits masked) as a new `CorpusCase` row with the outcome it should have. Run `testDebugUnitTest` and watch it fail.
2. Fix the right layer, usually one line, and re-run until the whole corpus is green:
   - A new ignore rule: append to `TextFilters.ignoreRules` (it yields to completed movements automatically).
   - A new direction verb: add it to `TypeDetector.primaryVerb`.
   - A new merchant shape: add a regex to `MerchantExtractor.patterns` at the right priority.
   - A new bank: append to `BankExtractor.knownIssuers`.
   - A new category keyword: add it to the list on the `Category` enum.

### Reporting a misread SMS

Open **Settings › SMS log**, find the message (every scanned message is listed with its outcome and
reason), tap it to see the text, and open an issue with the text and what it should have been. Mask
account and card digits first. A message marked *ignored* that was really a payment can also be sent to
the review queue from the same screen.

## How the numbers are calculated

- **Spend** counts transactions with flow `EXPENSE` (and `CASH` unless turned off in Settings).
- **Refunds** (`REFUND` flow) are subtracted from spend, and from the matching category when the merchant matches a spend in the same period.
- **Income** counts only `INCOME`. **Transfers**, **investments** and split **settlements** are listed separately.
- **Same message twice:** the live receiver and an inbox scan see one SMS seconds apart; it is stored once. Every message the importer looks at is recorded in the SMS log, which is how it remembers what it has already handled.
- **Identical alerts are separate payments:** two word-for-word identical alerts more than five minutes apart (two ₹180 coffees on one card) are two transactions, not one.
- **One payment, two senders:** a bank alert and a UPI-app alert for the same payment are stored once when their reference numbers match, or when the same amount and direction arrive within ten minutes from a different bank/app (or one has no real merchant). Two references that both exist and differ are always two payments. Same merchant and amount hours apart is two payments.
- **Your edits win:** once you edit a transaction, duplicate merging only fills in missing identifiers and rescans never rewrite it.
- **Splits:** only your share is your expense. If you paid and an SMS debit for the full amount exists, that transaction is trimmed to your share (the bank's original amount is remembered, so a second alert for the full bill is still recognised as the same payment); otherwise your share is recorded as a `SPLIT` expense. Money friends pay back is a `SETTLEMENT`, not income.

Every rule above has a unit test under `app/src/test` (parser corpus, duplicate detection, import memory,
insights, split maths, database migrations). Run them with `testDebugUnitTest`.

### Known limits

- Wording no bank in the corpus uses can still be misread. The defence is the review queue plus one new corpus row per report.
- A "You paid ₹200 ... cashback ₹20 credited" message records the ₹200 spend; the ₹20 cashback is not recorded separately.
- A balance alert that also mentions the last debit is imported. If the bank's real debit alert arrived more than ten minutes apart, **Clean up duplicates** in Settings will merge them.
- Bank alerts sent from ordinary phone numbers (not sender IDs) are skipped, to keep personal SMS private.
