# v1.2 Plan: Import Anything + Split Intelligence

**Status:** built and released as v1.2.0 on 28 Sep 2026 (M0–M7). Device test: `docs/DEVICE-TEST-v1.2.md`. Open items are marked `[ ]` with a note.
**Source:** Ronak's feedback (26 Sep 2026) and the repayment gap found in the code on 28 Sep 2026.
**Replaces:** `PLAN-v1.1.3.md` (too narrow: no statements, no AI, no shared cabs).

Legend: `[ ]` not started · `[~]` in progress · `[x]` done and verified

---

## 1. What Ronak asked for

1. **Parse everything, with no per-user or per-bank customization:** SMS, bank statements (PDF, Excel, CSV, including annual ones), and payment-app history screenshots (Amazon Pay, GPay, PhonePe, Paytm).
2. **Split intelligence, automated with AI.** "If I pay for 12 people at SBOW and they pay me back, only my share is my expense." OCR only reads text; something smarter has to recognise group payments.
3. **Every shared expense, not only the big one.** Shared Uber and Rapido rides between point A and point B are split too, and their paybacks arrive mixed in with the dinner paybacks.
4. **Fully automatic, because one mistake skews the whole month.** "If you crack split intelligence in an automated way, people will pay for it."

## 2. Decisions (28 Sep 2026)

| # | Question | Decision |
|---|---|---|
| 1 | Which AI | **OpenAI** (already in the app), behind a provider interface so other providers or an on-device model can be added later |
| 2 | What is sent to the AI | **Anonymised:** amounts, dates and times, direction, category, merchant *type*. People become "Person A/B/C". Real names and the rest of the text stay on the phone. |
| 3 | Whose API key | **The user's own key**, as today. Releases keep the built-in key (your choice). A paid service is out of scope. |
| 4 | Combined paybacks (one transfer covering dinner + cab) | **Yes**, handled in v1.2 |
| 5 | Payback window | **2 weeks (14 days).** A setting for 7 / 14 / 30 days can come later. |
| 6 | Advance collection (friends pay you *before* you pay) | **Included, but always asks.** The app suggests it and the user confirms or rejects; it is never applied automatically. |
| 7 | Automatic vs asking | **High confidence applies automatically** with an "Auto-split" badge and one-tap undo. **Medium confidence asks.** Low is left alone. |
| 8 | Which banks and apps | **Cover them broadly.** Generic by design. Fixtures for the major Indian banks (SBI, HDFC, ICICI, Axis, Kotak, PNB, Bank of Baroda, Canara, Union, IDFC First, Yes, IndusInd) and apps (Amazon Pay, GPay, PhonePe, Paytm, CRED), modelled on public demo/sample statements found online. |
| 9 | Sample files | **Synthetic and public demo samples for now.** Real ones can be added later. |
| 10 | Locked PDFs | Ask for the password once. It is used on the phone only and never stored. |
| 11 | AI cost on big files | Accepted. Only the transactions around possible shared spends are sent, in batches, so an annual statement costs a few rupees, not the whole file. |
| 12 | Release | **v1.1.2 ships first** as a signed release with the built-in key, like v1.1.1. This work is **v1.2**. |

## 3. The core problem, as one real weekend

```
Sat 20:10  −₹12,000  SBOW              (12 people)
Sat 23:40  −₹600     Uber → Bandra     (shared by 3)
Sun 10:05  −₹180     Rapido            (just me)
Sun 11:00  +₹1,000   Rahul             SBOW share
Sun 11:20  +₹1,200   Priya             SBOW + Uber share in ONE payment
Sun 12:00  +₹200     Amit              Uber share only
Mon        +₹1,000 × 9 more people     SBOW shares
Tue        +₹85,000  ACME CORP SALARY  not a payback
```

| | Correct answer |
|---|---|
| SBOW | spent **₹1,000** (₹12,000 − ₹11,000 received) |
| Uber | spent **₹200** (₹600 − ₹400 received: Priya's ₹200 part + Amit) |
| Rapido | spent **₹180** (untouched) |
| Paybacks | **₹11,400** marked "Split settlement": not income |
| Salary | income, untouched |

This weekend is **acceptance test #1**. It has to come out exactly right from SMS, from a CSV, from a PDF and from screenshots.

## 4. Architecture

```
 SMS ─────────┐
 CSV ─────────┤    ┌──────────────┐   ┌──────────────┐   ┌───────────┐   ┌──────────────┐
 Excel ───────┼──► │ 1. Get rows  │─► │ 2. Columns   │─► │ 3. Rows → │─► │ 4. Duplicates│─► Transactions
 PDF (text) ──┤    │  per format  │   │  by meaning  │   │  txns     │   │  vs existing │        │
 PDF (scan) ──┤    └──────────────┘   │  (no bank    │   │ + balance │   └──────────────┘        │
 Screenshot ──┘                       │   templates) │   │   check   │                           ▼
                                      └──────────────┘   └───────────┘          ┌──────────────────────────────┐
                                                                                │ 5. SPLIT INTELLIGENCE        │
                                                                                │  a. Who is a person?         │
                                                                                │  b. Local solver (maths)     │
                                                                                │  c. AI judge (anonymised)    │
                                                                                │  d. Verifier (hard rules)    │
                                                                                │  e. Decide: auto / ask / skip│
                                                                                └──────────────────────────────┘
```

**Design rules**
- **No templates.** Columns are found by meaning (header words, then content: date-like, amount-like, balance-like). The same idea as the SMS parser.
- **AI judges, code verifies.** The AI proposes which paybacks belong to which spend. Local code checks that the numbers are possible before anything is applied. An AI answer that fails the check can never be auto-applied.
- **Nothing is lost.** Bank amounts are kept, every automatic change can be undone, and user edits and decisions always win.
- **Works without AI.** With no key, or offline, the local solver still handles the clear cases (for example 11 × ₹1,000 after ₹12,000). AI adds the hard ones.

## 5. Milestones and checklist

### M0: Release v1.1.2
- [x] Version 1.1.2, CHANGELOG "Unreleased" → 1.1.2, README check
- [x] Signed release APKs build, tag `v1.1.2`, push
- [x] GitHub release with the signed `-personal` arm64 APK, like v1.1.1

### M1: Import foundation and CSV/Excel
- [x] `ImportSource` on transactions (SMS / CSV / XLSX / XLS / PDF / IMAGE / MANUAL) and an `importBatchId` so a whole import can be undone. DB v5 with a migration test.
- [x] `TableRow` model: a list of cells with an optional x-position, the common output of every reader
- [x] **CSV reader:** delimiter sniffing (`,` `;` tab `|`), quotes, UTF-8/UTF-16/BOM, junk lines above the header (bank name, address, account summary)
- [x] **XLSX reader** with no heavy library: zip + XmlPullParser, shared strings, number and date cells (Excel date serials), first sheet with a transaction table
- [x] **XLS (old Excel) spike:** pick a small library with a compatible licence, or fall back to "save as CSV/XLSX" with a clear message. Record the decision here. **Decision:** HTML ".xls" (what most banks send) is read; a genuine binary .xls shows "save it as .xlsx or .csv".
- [x] **Column detector:** header synonyms (Date / Txn Date / Value Date; Narration / Description / Particulars / Remarks; Withdrawal / Debit / Dr; Deposit / Credit / Cr; Amount + Dr/Cr; Balance), with a content-based fallback when headers are missing or odd
- [x] **Row interpreter:** many date formats (dd/MM/yy, dd-MMM-yyyy, yyyy-MM-dd, with time), Indian number format, Dr/Cr suffixes, counterparty from the narration (UPI `UPI/DR/ref/NAME/BANK/vpa`, NEFT, IMPS, POS, ATM), reference numbers, reusing `MerchantExtractor`, `Categorizer` and `FlowClassifier`
- [x] **Balance check:** previous balance ± row = next balance. Failing rows go to review, never guessed.
- [x] **Duplicates vs SMS:** same reference number, or same amount + direction within ±1 day. Matched rows fill in missing details (reference, time) and are not added twice.
- [x] **Import screen:** pick a file (system picker, no storage permission) → preview (new / duplicate / needs review, balance check result) → confirm → import log. "Undo this import" is available.
- [x] Fixtures: synthetic HDFC, SBI, ICICI, Axis, Kotak statements (CSV and XLSX) of about 50 rows, plus an annual one with 1,500 rows (speed test)

### M2: PDF statements
- [x] **Library spike:** PdfBox-Android (Apache 2.0) for text with positions and for password-protected PDFs. Measure APK size. Android's own `PdfRenderer` cannot open locked PDFs. **Decision:** pdfbox-android 2.0.27.0 (Apache 2.0).
- [x] Text PDFs: words with x/y → lines (same idea as `OcrRows`) → columns from the header's x-ranges → `TableRow`s → the same M1 pipeline
- [x] Tables that continue across pages, repeated headers, page footers, totals rows skipped
- [x] **Password:** a prompt when needed. Used in memory only, never stored or logged. Wrong password → a clear retry.
- [x] **Scanned PDFs:** render each page → on-device ML Kit OCR → the same line and column building. The balance check matters most here.
- [ ] Fixtures: a text PDF layout in unit tests and a locked 2-page PDF on the device are done; **a scanned PDF is implemented (render + OCR) but not yet tested**

### M3: Screenshots and images
- [x] Statement photos or screenshots → OCR → the same table pipeline (with the balance check when a balance column exists)
- [x] **Payment-app history** (Amazon Pay, GPay, PhonePe, Paytm): generic rules, not per-app templates. A row is name + amount (± sign or "Paid to" / "Received from" / "Sent" / "Received") + date or time. Handles "Today", "Yesterday" and relative dates. Several screenshots at once, with the overlap between them removed.
- [ ] Fixtures: Google Pay (real OCR output from the device) and PhonePe shapes are tested; **Paytm, Amazon Pay and a tilted photo still need real samples**

### M4: Split intelligence, local (works without AI)
- [x] **PayerClassifier:** is the counterparty a person? Phone-number VPAs and personal handles/names count as people. Merchants (Categorizer rules), gateways, employers (salary, pvt ltd, corp), banks and refunds do not. Corpus of 30+ shapes.
- [x] **Candidates:** shared-spend candidates are any expense (dinners, cabs, tickets, groceries). Payback candidates are credits from people within the window after a spend. **Advance candidates** are credits from people *before* a spend.
- [x] **Local solver:** assigns paybacks to spends using equal-share fit (bill ÷ n), several senders, sum ≤ bill, and time order. **Combined paybacks** are split via bounded subset-sum, for example ₹1,200 = ₹1,000 (SBOW) + ₹200 (Uber). No payback is used twice. It returns a confidence and human-readable reasons.
- [x] **Verifier (hard rules, also applied to AI answers):** allocations ≥ 0 and ≤ the payback; the total allocated to a spend ≤ the spend; each payback fully accounted for; only person counterparties; inside the window; the share for "me" ≥ 0
- [x] **Storage (DB v5):** `split_links` (splitId, transactionId, role PAYMENT / PAYBACK / ADVANCE, allocatedPaise, source MANUAL / AUTO_LOCAL / AUTO_AI, confidence, reasons) and `split_decisions` (the user said "not a split", which is remembered forever, like dismissed SMS)
- [x] **Apply:** my share = spend − allocated paybacks (the bank amount is kept), paybacks become "Split settlement", and a split appears in the Split tab with people = senders
- [x] **Undo:** restores everything exactly and records the decision
- [x] **Keeping up to date:** runs after every SMS scan, import and manual entry. Late paybacks join their group.
- [x] **Manual splits:** the Settle button offers matching incoming payments, which fixes the repayment gap from 28 Sep
- [x] Acceptance tests: Ronak's weekend (section 3), a payback on day 13 (joins) and on day 15 (does not), advance collection (always a suggestion, never auto-applied)
- [x] **Trap tests (must NOT split):** salary, monthly rent from a flatmate, a single loan repayment, a refund, ₹1,000 from a friend after an unrelated electronics purchase, a friend paying you for something you bought them long ago, a spend already split by hand

### M5: AI judge
- [x] `SplitAiProvider` interface; `OpenAiSplitProvider` first (the existing client, the user's key); room for Claude and on-device later
- [x] **Anonymised payload:** per candidate window, the spends (amount, time, category, merchant type such as "restaurant" or "cab") and paybacks (amount, time, "Person C"). No names, no account numbers, no raw text. The **"What is sent?"** preview is extended to show this exact payload.
- [x] **Strict JSON answer:** groups → spend id, payback allocations, people count, confidence, a short reason. Invalid JSON → retry once → otherwise local only.
- [x] **Batching:** only windows that contain possible shared spends. A token and cost estimate is shown before a large import. Rate-limit and timeout handling.
- [x] **Decide:**

  | Local solver | AI | Verifier | Result |
  |---|---|---|---|
  | same answer | same answer | pass | **auto-apply** |
  | no answer | high confidence | pass | **auto-apply** |
  | different answers | – | pass | **ask** (show both) |
  | – | medium | pass | **ask** |
  | – | any | **fail** | local answer only, or skip |
- [x] Tests with a **fake provider** (fixed answers, including wrong and malicious ones, to prove the verifier stops them). One live smoke test with your key, run only with your go-ahead because it costs money.
- [x] Settings: "Use AI to detect splits" (on when a key is set)

### M6: Screens
- [x] "Auto-split" badge on transactions with a **"Why?"** sheet ("11 people paid back ₹1,000 each within a day; Priya's ₹1,200 covers SBOW and the Uber")
- [x] Home: suggestion cards (Apply / Not a split / Edit)
- [x] Split tab: auto and manual splits together, with who has paid, who still likely owes, and settle
- [x] Dashboard: spend uses my share, and paybacks show under "Not counted as spend"
- [x] Import entry points: Settings → Import statement, and Activity → Import

### M7: Verify and release v1.2.0
- [x] All unit, corpus, fixture and scenario tests pass, and lint shows 0 errors
- [x] **Emulator** on a separate AVD `fintest-split`, so the shared `fintest` and its ₹4,218 baseline are untouched: Ronak's weekend as real SMS, then the same weekend as CSV, PDF and screenshots, and check the dashboard shows exactly ₹1,000 / ₹200 / ₹180
- [x] Upgrade test v1.1.2 → v1.2.0 with existing data (like the v4 check)
- [x] README privacy section updated: what the split AI sends
- [x] CHANGELOG, version 1.2.0, tag, signed APKs, GitHub release

## 6. Risks

| Risk | Mitigation |
|---|---|
| A false split hides real spending | Verifier, strict person check, "ask" when unsure, undo, the "Why?" sheet |
| The AI returns something wrong or strange | It never applies directly; the verifier and decision table gate it; fake-provider tests with bad answers |
| Privacy | Anonymised payload, opt-in, exact preview, user's own key |
| A friend pays from someone else's account | The amount and timing still fit, but the person differs → medium → ask |
| Cash paybacks | No transaction exists; settle by hand as today |
| Odd statement layouts | Content-based column fallback, the balance check, failing rows go to review, real samples added as fixtures |
| PDF library size | Measured in the M2 spike; per-ABI APKs keep downloads small |
| Cost of annual statements | Only candidate windows are sent, with an estimate shown first |

## 7. Found on the device and fixed
Nine issues, from AI noise to the ₹ sign misread by OCR. Each has a unit test; see `docs/DEVICE-TEST-v1.2.md`.

## 8. Later (not blocking)
- Real statements and screenshots from you and Ronak, added as fixtures when available
- Real statements and screenshots for Paytm / Amazon Pay, a scanned PDF
- A setting for the payback window (7 / 14 / 30 days)
