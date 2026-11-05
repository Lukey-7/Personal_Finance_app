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
- **Splits:** only your share is your expense. If you paid, you pick the payment under "Paid with" (the clear match, within 2% and 3 days, is picked for you) and it is trimmed to your share (the bank's original amount is remembered, so a second alert for the full bill is still recognised as the same payment); only when you say it isn't in FinTrack yet is your share recorded as a `SPLIT` expense, so a bill is never counted twice. Money friends pay back is a `SETTLEMENT`, not income, and so is your own payment to whoever paid. Deleting a manual split puts the payment back to its full amount and removes a share row it added. Automatic splits that share a friend's transfer are applied, suggested and accepted together, so a transfer is only a settlement when all of it is accounted for.
- **Payee rules and your own categories (v1.6):** a category you give a payment by hand is remembered for its payee (the merchant name lower-cased, letters and digits only); the next SMS or statement row from that payee takes it before the keyword lists, as long as the direction fits (a spend rule never files money in). Placeholder names ("Payment (HDFC Bank)") never make a rule. A category of your own is stored as a built-in "parent" plus its id: flow, splits and tax use the parent; Home, Insights, Budgets, drill-downs and the CSV group by your category, so each line still adds up to the payments behind it.
- **Cashback (v1.6):** a payment alert that says a cashback "of Rs X (has been) credited" saves the payment and, a second later, a `REFUND` of X for the same payee and category, so it reduces that category. "Will be credited", "eligible", "up to" and offers are ignored, and a cashback can never be as large as its payment. A cashback credit SMS within a week of one already saved this way, for the same amount, is a duplicate.

Every rule above has a unit test under `app/src/test` (parser corpus, duplicate detection, import memory,
insights, split maths, database migrations). Run them with `testDebugUnitTest`.

### Known limits

- Wording no bank in the corpus uses can still be misread. The defence is the review queue plus one new corpus row per report.
- A cashback only promised ("will be credited") is not recorded; it shows up when the bank's own credit SMS arrives.
- A balance alert that also mentions the last debit is imported. If the bank's real debit alert arrived more than ten minutes apart, **Clean up duplicates** in Settings will merge them.
- Bank alerts sent from ordinary phone numbers (not sender IDs) are skipped, to keep personal SMS private.
