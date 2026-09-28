# v1.2.1 Edge-Case Hardening

**Goal:** Find and fix the edge-case bugs in the v1.2 features (split intelligence, statement import, screenshot import) before real users hit them. Every item is written as a failing test first, then fixed.

**Branch:** `v1.2.1-edge-cases` from `main` (e515922), in a separate worktree · **Build:** `bash build.sh testDebugUnitTest` · **Device:** AVD `fintest-split` (port 5556) only

Legend: `[ ]` not started, dropped or not changed (reason given) · `[x]` done and verified by a test that failed first · **Likely bug** = I found it by reading the code and expect the test to fail · **Probe** = a test that pins behaviour, may already pass

Test types: **U** plain JVM unit test · **R** Robolectric + in-memory Room (`@Config(sdk = [33], application = android.app.Application::class)`) · **D** device check on `fintest-split`

## Coverage today and the gaps

| Area | Existing tests | Gap |
|---|---|---|
| SplitSolver | 13 scenario tests (weekend, rounding, two parts, day 13/15, advance, salary) | the exact day-14 boundary, groups over 60, same friend in two groups, same-amount twin payments, paise shares, run time on a busy month |
| SplitJudge (AI) | parse garbage, malicious amounts, disagreement, anonymised request | 27+ people (label collision), duplicate groups, string numbers, non-object JSON, leaks through merchant text |
| SplitEngine | 9 Robolectric flows (reject, delete, user edit, AI cache) | user edits *after* a suggestion or applied split, then accept or reject; deleting the payment; rejected transfers being reused; re-run stability |
| Statement parsing | 10 bank-shaped fixtures | Dr/Cr prefixes, trailing minus, unparseable dates, identical rows, placeholder refs, XLSX float noise, empty files |
| Screenshot OCR | 2 tests | October dates, Indian grouping after a misread ₹, apps without a ₹ glyph, one amount per screen |
| Migrations | one test per step | the full 1→5 chain in one go, a v1.1 user's data under the split engine |
| File security | none | zip bomb, huge column refs, DOCTYPE/entities, pathological HTML, huge PDF pages |
| PayerClassifier | 3 tests | business-like names, initials, merchant vs personal VPAs, P2M/P2A markers |
| AI failures | none | timeouts, 429, bad key, empty or cut-off answers, cached bad answers |
| Concurrency, time zones | none | refresh during import, back-to-back refreshes, IST vs UTC |
| SMS + statement together | 2 tests | split first then the other source arrives, rescans, day-late booking |
| Password PDFs, undo import | 1 test | wrong/empty/owner-only passwords, double undo, undo after edits |

---

## P0: likely money-data bugs (wrong totals or lost rows, silently)

### 1. Identical rows in one statement: the second is lost
`StatementImporter.hash()` is `date|amount|type|narration|balance`. Two real ₹20 chai payments on one day in a statement with no balance column (credit cards) get the same hash, and `insert` is `OnConflictStrategy.IGNORE` on the unique `smsHash` index, so the second one is dropped with no message.
- [x] **R** Likely bug: a card CSV with two identical rows imports 2 transactions, and re-importing it adds 0
- [x] Fix: add an occurrence number to the hash for rows that are otherwise identical within one file (stable across re-imports of the same file)

### 2. Placeholder reference numbers mark real rows as duplicates
`findExisting` matches on `findAnyByRef(ref)` with only an amount check and no date limit. Many banks fill the Chq/Ref column with `0`, `-`, `NA` or `0000000000000000`. A ₹500 row in this month's statement then "matches" any ₹500 row with the same placeholder from months ago, and is skipped.
- [x] **R** Likely bug: import September then October statements (HDFC-style `0000000000000000` refs), the same amount in both, and both are kept
- [x] **U** Probe: `-`, `NA`, `0`, `000000`, `N/A` are not treated as references
- [x] Fix: ignore refs with no non-zero digit or fewer than 6 characters, and require the ref match to be within a few days of the row

### 3. Rows with a date the parser cannot read vanish without a trace
In `StatementInterpreter.interpret`, a row whose date does not parse is treated as a wrapped narration line (or a footer). If it has amounts, it is dropped: not imported, not sent to review.
- [x] **U** Likely bug: a row dated `2026-09-28T10:15:00Z`, `28 Sept 2026` or `09/28/2026` (US order) with amounts shows up in `problems` as `date_unknown`, not nowhere
- [x] **U** Probe: each of those formats (and `28-Sep-26`, `28.09.2026`, `28 SEP 2026`) parses when it can
- [x] Fix: a dateless row that has an amount in a debit/credit/amount column becomes a `date_unknown` problem

### 4. Screenshot dates in October fail
`AppHistoryParser.parseWhen` does `trimEnd(',', ' ', 'a', 't')` to strip "at". "12 Oct" becomes "12 Oc", which no parser reads. The row then takes the date of the last section header, which is a wrong date, or is dropped.
- [x] **U** Likely bug: "12 Oct", "12 Oct, 8:30 PM", "Oct 12", "12 Oct at 8:30 pm" all give 12 October
- [x] **U** Probe: "Today", "Yesterday, 11:59 PM" just after midnight, "12 Sep" when today is 2 Jan (last year), "31 Dec" on 1 Jan
- [x] Fix: strip a trailing " at" word, not the letters

### 5. A misread ₹ with Indian grouping is taken as a real digit
With no box widths, a comma keeps the leading 7 ("contains a comma, so it's real"). But "712,000" is not valid Indian grouping (₹7,12,000 would be), so it can only be ₹12,000 with the ₹ read as 7. Today it imports as ₹7,12,000.
- [x] **U** Likely bug: "712,000" and "+ 71,20,000"-style cases resolve to the amount whose grouping is valid Indian style
- [x] **U** Probe: real "₹7,300", "7,300" (₹ dropped) and "₹7,12,000" stay as they are
- [x] Fix: when only one reading has valid Indian grouping, take it

### 6. Apps that print no ₹ at all
The width model assumes every amount box includes a ₹ glyph. In an app that shows "- 750.00" with no ₹, the literal reading is one glyph short and "₹ read as 7, amount 50.00" fits the box exactly. The user flagged this case.
- [x] **U** Probe (expect fail): a screen of "- 300.00", "- 750.00", "- 1,250.00" with true widths and no ₹ gives 300 / 750 / 1,250
- [x] Fix if it fails: fit the glyph width under both "₹ glyph present" and "no ₹ glyph" models and use whichever fits all the amounts better

### 7. User edits overwritten by accept or reject
`accept()` applies a suggestion using the amounts saved when it was created. `reject()` restores `prevAmountPaise`/`prevFlow`. Neither checks whether a person edited the row in between.
- [x] **R** Likely bug: a suggested split, the user edits the payment amount, then taps Accept. The edit must survive (don't apply, or re-derive and ask)
- [x] **R** Likely bug: an applied split, the user edits the payment or a transfer, then taps "Not a split". The user's edit must stay
- [x] Fix: skip rows with `userEdited = true` in `applyEffects` and `undo`, and drop the split's claim on them

---

## P1: split intelligence edge cases

### 8. Window boundaries and time
- [x] **U** Probe: a payback exactly 14 × 24 h after the payment is used; 14 × 24 h + 1 ms is not. The solver, verifier and AI request all agree
- [x] **U** Probe: a payback in the same minute, and a payback 1 ms before the payment (that is an advance, not a payback)
- [x] **Question for you** (see end): is the window 14 × 24 h, or "by the end of day 14"?

### 9. Groups and people
- [x] **U** Probe: 50 people, 49 paid back ₹200 each on a ₹10,000 bill. Found, k = 50, high confidence
- [x] **U** Probe: 75 people (over `MAX_PEOPLE = 60`). Expect a suggestion or nothing, never a wrong k
- [x] **U** Probe: one friend in two groups (dinner ₹3,000 / 3 and cab ₹600 / 3) paying two separate transfers. Each goes to its own payment
- [x] **U** Probe: one friend paying both shares in one transfer (₹1,200 = 1,000 + 200) while a second friend pays separately
- [x] **U** Probe: two twin payments (₹3,000 on day 1 and day 3) with ₹1,000 paybacks from the same two friends on day 2 and day 4. Each payback goes to the nearest payment and nothing is used twice
- [x] **U** Probe: shares with paise: ₹1,000.01 / 3 paid as ₹333.33, ₹333.34 and ₹333.00. All three fit; "your share" is exact to the paisa
- [x] **U** Probe: senders with no usable name tokens ("AK", "9876543210") paying two parts are not counted as two people
- [ ] ~~**R** Probe: two different friends both named "Rahul"~~ Dropped: the solver already treats one display name as one person (its sender key comes from the name), so `create` can never see two different people with the same name

### 10. Things that are not paybacks
- [x] **U** Probe: a refund from Swiggy, cashback from Google Pay and a reversal inside the 14 days next to a ₹1,000 food payment are never allocated
- [x] **R** Probe: rows migrated from v1.1 have `counterpartyKind = null`. A refund row whose merchant looks like a name is judged with the SMS text, not the merchant alone

### 11. Engine lifecycle
- [x] **R** Probe: re-run stability. Run → run → run on a busy month (combined transfers, a two-part share, one suggestion, one AI answer) gives `applied = suggested = undone = 0` after the first run, and identical rows
- [x] **R** Probe: deleting the *payment* of an applied split restores every transfer to income
- [x] **R** Probe: deleting one transfer that covers two applied splits (₹1,200 = dinner + cab) undoes both cleanly, with no half-settled transfer left
- [x] **R** Probe: accept → run → reject → run: the numbers return exactly to the bank's
- [x] **R** Likely bug / **question**: after "Not a split", the same transfers are free and the next run can auto-apply them to a *different* payment of a similar share. See the question at the end
- [x] **R** Probe: undoing a statement import whose rows a *manual* split settlement is linked to leaves no dangling link and no share still marked paid
- [x] **U** Probe: 300 payments and 300 transfers inside one fortnight solve in under 2 s (the combination step is cubic)

### 12. AI answers
- [x] **U** Likely bug: with 27+ people, "Person A" is replaced inside "Person AB" when names are restored in reasons ("RahulB")
- [x] **U** Probe: two groups for the same payment; the same transfer in two groups adding up to more than it; `"amount": "1000"` as a string; `1e20`; `-0`; confidence 250 or "high"; a JSON array root; a fenced answer with text before it; an unknown `P99` / `C99` id. None of these can apply anything wrong
- [x] **U** Probe: an AI answer that uses a frozen, manual or already-settled transfer is thrown away by the verifier
- [x] **U** Probe: an AI "advance" with confidence 100 is still only a suggestion

---

## P1: statement parsing edge cases

### 13. Amounts, Dr/Cr in every position
- [x] **U** Probe table for `Amounts.parseSigned`: `Dr 500.00`, `DR. 500`, `500.00 Dr`, `500.00(Dr)`, `500.00-`, `-₹500`, `₹-500`, `Rs.500.00 CR`, `500 Cr.`, `(1,250.00)`, `1,00,000.00`, `1 234.00`, `- `, `0.00`, `.50`. Each gives the right paise and direction or a clean null. Expect fails on prefix Dr/Cr and trailing minus
- [x] **U** Probe: a separate Dr/Cr column holding `D`, `C`, `DR`, `CR`, `Debit`, `Credit`, `W`, `D/W`
- [x] **U** Probe: both debit and credit filled on one row goes to review, not silently dropped

### 14. XLSX specifics
- [x] **U** Likely bug: formula cells store `1234.5599999999999` or `1.5E3`, which the amount regex rejects, so the row is lost. Normalise numeric cells to 2 decimals in `XlsxReader`
- [x] **U** Probe: a date serial with a time and no date style (`46293.520833333336`) still reads as a date
- [x] **U** Probe: inline strings, a cover sheet before the statement, cells with no `r` attribute

### 15. Layout
- [x] **U** Probe: "Total" / "Page 2 of 5" / "Closing balance" rows mid-table are skipped, and a wrapped narration line containing "TOTAL ENERGIES" is still appended
- [x] **U** Probe: a PDF narration that starts on the line *above* its date line (vertically centred rows) is not glued onto the previous transaction. Expect fail; fix only if cheap, otherwise document it
- [x] **U** Probe: header row after 80 lines of account summary; two-line header in CSV/XLSX ("Withdrawal" over "Amt")
- [x] **U** Probe: a "Credit Card No" column does not become the CREDIT column
- [x] **U** Probe: headerless CSV whose amounts are in the Excel-serial range 30,000–60,000 still finds the right date column

### 16. Encodings and sizes
- [x] **U** Probe: UTF-16 LE with BOM and a ₹ header, UTF-16 BE *without* BOM (expect fail), Windows-1252 with `£`
- [x] **U** Probe: empty file, header-only file, one-row file, a stray unmatched `"` at the start of a narration (must not swallow the rest of the file)
- [x] **U** Probe: 60,000-row CSV parses in reasonable time and every row keeps its own date (`order * 1 s` passes midnight after 43,200 rows)

### 17. Balances, duplicates, re-import, undo
- [x] **U** Probe: newest-first statement with the opening-balance row at the bottom; one missing row causes exactly one mismatch, not a cascade
- [x] **R** Probe: a statement row matching an SMS row that a split already shrank (`originalAmountPaise`) is a duplicate, not new
- [x] **R** Probe: the same file imported as CSV and then as PDF adds nothing the second time
- [x] **R** Probe: undo after a split used an imported row restores the other rows' numbers exactly

---

## P1: security of file import and AI

### 18. Malicious files (all in `TableReaders` / `StatementFiles`)
- [x] **U** Likely bug: XLSX zip bomb. `z.readBytes()` inflates every part fully (a 40 MB file can inflate to many GB). Fix: cap bytes per part and in total, and the number of parts read
- [x] **U** Likely bug: a cell ref like `XFD1` or `ZZZZZZZ1` makes every row a list of 16k+ cells (or an `Int` overflow). Fix: cap the column index and the row count
- [x] **U** Likely bug: `<!DOCTYPE>` with entities (billion laughs) or an external entity. On the JVM the SAX parser resolves `file://` entities. Fix: reject any part with a DOCTYPE and turn off external entities where the parser supports it
- [x] **U** Probe: 100,000 nested XML elements; no `StackOverflowError`
- [x] **U** Likely bug: the `.xls` HTML reader uses `<tr[^>]*>(.*?)</tr>` over up to 40 MB. Many `<tr>` with no closing tag take quadratic time. Also `<track>` matches `<tr`. Fix: a linear scan
- [x] Fix: cap render size per scanned PDF page by pixel count (`PdfLimits.renderDpi`, unit-tested) and load PDFs with a 96 MB memory limit (not unit-testable here: needs real rendering)
- [x] **U** Probe: every reader failure becomes a friendly `Read.Error`, never a crash, and the message never contains file content or the password

### 19. What reaches OpenAI
- [x] **U** Probe: merchant names containing a phone number, a VPA, an account number, an email and a PAN appear nowhere in `SplitAiRequest.json`. Only amounts, relative days, clock times, categories and Person labels travel
- [x] Review: grep every `Log.` call and every string the key flows through; `sanitize` covers `sk-proj-…` keys. Debug-only logs stay behind `BuildConfig.DEBUG`

---

## P2: database upgrades

### 20. Migrations with real data
- [x] **R** Probe: one v1 database with transactions, a review item, a budget and a split, migrated 1→2→3→4→5 in one chain and validated against `5.json`. Amounts in paise and split links intact
- [x] **R** Probe: 3→4 edge rows: two splits linked to one transaction, a split with no "me" person, a non-SMS row
- [x] **R** Likely bug / probe: a v1.1 user with a *manual* split whose friends' transfers were never marked as settlements. After the upgrade, the engine must not claim those transfers for a different payment (or it should at least only suggest)

---

## P1: added after the first review (gaps in the first draft)

### 21. Person or organisation (PayerClassifier)
- [x] **U** Probe: business-like names are organisations: "Sharma Traders", "Om Sai Enterprises", "Krishna Medicals", "Hotel Saravana Bhavan", "Rahul Tech Solutions"
- [x] **U** Probe: people with initials, honorifics and short names are people: "R K Sharma", "Mr. Anil Kumar", "Priya K Nair", "Om", "Md Arif"
- [x] **U** Probe: merchant VPAs are organisations (`paytmqr28100505@paytm`, `q123456789@ybl`, `swiggy.stores@icici`, `bharatpe.9000@fbl`); personal VPAs are people (`rahul.sharma@okhdfcbank`, `9876543210@ybl`)
- [x] **U** Probe: a P2M marker wins over a name-like merchant; P2A/P2P with a name gives a person; "NEFT from ACME CORP LTD SALARY" is an organisation
- [x] **U** Probe: "Martin" is a person (word boundaries hold). Not fixed: a surname that *is* a business word ("Bank", "Capital") still reads as an organisation. Rare, and loosening it would let real businesses through

### 22. When the AI can't be asked or answers badly
- [x] **R** Likely bug: `ai.judge(req)?.also { cache.put(...) }` caches the answer *before* it is parsed. A cut-off or non-JSON answer is stored, and that week is never asked again. Fix: cache only answers that parse
- [x] **R** Probe: a timeout, a 429 rate limit, a 401 bad key, a blank key and no key all leave the local results in force, cache nothing, and mark nothing as "asked by AI"
- [x] **R** Probe: an empty answer (`""`, `{}`, `{"groups":[]}`): `{"groups":[]}` counts as "AI says no split" and blocks auto-apply for those payments; `""` and `{}` count as "not asked"
- [x] **U** Probe: a cut-off answer (`{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amo`) gives null, not a partial list
- [x] **R** Probe: a corrupt value in the answer cache (edited prefs, older format) is ignored, not trusted
- [x] Review: "Clear all data" also clears the AI answer cache. Already true (`SettingsRepository.clearAll`); pinned by `SettingsClearTest`

### 23. Two things at once
- [x] **R** Probe: a split refresh started while a statement import is committing. When both finish, every transaction is counted exactly once and every applied split's rows agree with its links
- [x] **R** Probe: two refreshes back to back (SMS arrives during a refresh). The second waits on the engine lock and changes nothing
- [x] **R** Probe: accept or reject tapped while a refresh is running is applied after it, and the result holds (not undone by the refresh's stale view)
- [ ] Review: `commit` is not in a database transaction. Not changed: the batch row is written first and every added row carries its id, so a crash halfway still leaves an import that "Undo" removes completely

### 24. Time zones
- [x] **U** Probe: an Excel serial and a `dd/MM/yyyy` date read with the phone in Asia/Kolkata and in UTC land on the same calendar day
- [ ] ~~**R** Probe: IST then UTC split refresh~~ Dropped: the solver works on epoch times only (no time zone). The AI request deliberately uses the phone's local clock ("dinner at 20:10"), so a zone change re-asks once; that is intended
- [x] **U** Probe: a screenshot's "Today, 12:30 AM" and "Yesterday, 11:45 PM" around midnight, with `now` just after midnight

### 25. SMS and statement together
- [x] **R** Probe: a statement row is imported and auto-split, then its SMS arrives. The SMS is recognised as the same payment; the split and the shrunk amount stay
- [x] **R** Probe: an SMS row is split, then the statement for that month is imported. No new row, and the split stays
- [x] **R** Probe: an inbox rescan after a statement import adds nothing and doesn't move timestamps back and forth (`SmsImporter` swaps in the SMS timestamp for statement rows)
- [x] **R** Probe: a statement books the payment a day later than the SMS, near midnight (SMS 23:58 on the 5th, statement date the 6th). They match

### 26. Screenshots
- [x] **U** Probe: one transaction cut in half by two overlapping screenshots (name at the bottom of the first, amount at the top of the second) is read once, with the right name and amount, or not at all. Never with the wrong name
- [x] **U** Probe: each status word (Failed, Declined, Pending, Processing, Cancelled, Expired, Reversed, Requested, Scheduled) skips only its own row, not the row above or below
- [x] **U** Probe: a merchant whose name contains a status word ("Pending Bills Store") is not skipped
- [x] **U** Probe: two genuine identical payments in one screenshot with only a date (no time) are both kept. The overlap de-dupe should only merge rows from *different* screenshots
- [x] **U** Probe: a screen with only one amount and a known box width (the glyph width can't be fitted from one amount; fall back to the line height)

### 27. Password-protected PDFs (R, with small PDFs generated by PDFBox in the test)
- [x] **R** Probe: wrong password gives `NeedsPassword(wrong = true)`; no password gives `NeedsPassword(wrong = false)`
- [x] **R** Probe: an empty password string is treated as "no password", not as a wrong one
- [x] **R** Probe: a PDF with only an owner password (opens without asking, printing/copy restricted) is read without prompting
- [x] Review: the password never reaches a log, an error message, saved state or the batch record

### 28. Undo import
- [x] **R** Probe: undo twice (a double tap, or undo on a batch already undone) is harmless
- [x] **R** Probe: undo after the user edited an imported row (category, amount). Decide: the row still goes (it came from the file), and the result is shown clearly. Pin whichever we choose
- [x] **R** Probe: undo after a later import matched rows of this one as duplicates. The later batch keeps its own rows, and the rows it matched don't silently vanish from both

### 29. More migration shapes
- [x] **R** Probe: an empty v1 database migrates to v5 and opens
- [x] **R** Probe: a v4 database with a split that has shares but no people rows (possible after a crash in v1.1) migrates and the engine run doesn't crash on it
- [x] **R** Probe: a v4 split linked to a transaction that no longer exists migrates and is left alone as a manual split

---

## Found along the way (each with a failing test first)
- [x] An SMS arriving for a statement row that a split had settled turned the friend's transfer back into income while the split stayed applied (counted twice). `SmsImporter` now keeps a SETTLEMENT flow
- [x] Import commit wrote the preview's stale copy of matched rows back, undoing a split applied while the preview was on screen. Rows are re-read before the refs are filled in
- [x] Deleting (or un-importing) a transfer that settled a manual split left a dangling link and the share still marked paid. Links now remember the share (`split_links.shareId`, DB v6) and the engine makes it owed again
- [x] The solver took 4.4 s on a busy fortnight (300 payments, 300 transfers): the "one transfer covers two or three payments" step listed every triple. Now it walks shares smallest-first and stops early
- [x] XXE confirmed on the JVM: an XLSX part with an external entity read a local file into the table. DOCTYPEs are now refused before parsing
- [x] Headerless CSVs dropped a credit column that was mostly empty (one salary among thirty spends)
- [x] A month header in a screenshot ("September 2026") was read as 20 September and given to undated rows below it

## Device pass (D, on `fintest-split` only, after the unit work)
- [ ] Seed via root sqlite as in `docs/DEVICE-TEST-v1.2.md`: a busy fortnight with the P0 split cases, then run split refresh and check the numbers on the Home and Split screens
- [ ] Import a CSV with identical rows, placeholder refs and an October screenshot; check counts and dates on screen
- [ ] Accept / edit / reject flows from item 7 by hand

## Finish
- [ ] `code-review` skill at max effort over the whole branch diff
- [ ] `security-review` of the file-import code
- [ ] Fix what's real; report what was skipped and why
- [ ] Commit with explicit paths, no attribution trailer. No push, tag or release without asking

---

## Decisions (taken with the recommended defaults)
1. **Rejected splits:** when you tap "Not a split", should those friends' transfers be blocked from being auto-applied to another payment (still allowed as a suggestion)? I'd recommend yes.
2. **Window:** keep "14 × 24 hours after the payment", or "any time up to the end of day 14"? I'd keep 14 × 24 h and just pin it with tests.
3. **Branch:** the checkout is on `v1.1-accuracy-split` (same commit as `main`). OK to create `v1.2.1-edge-cases` from `main` for this work?
