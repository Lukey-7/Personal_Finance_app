# FinTrack domain terms

The words the code and the docs use for money ideas. Keep names in code matching these.

- **Payment**: one stored transaction row (a debit or a credit). Never "txn" in the app.
- **Same payment**: the rule that decides whether two records are one payment (a bank alert and its UPI-app twin, a
  statement row an SMS already reported, a v1.0.0 midnight row), and what survives when they merge. Lives in
  `domain/ledger/SamePayment.kt`; nothing else decides it.
- **Twins**: two stored rows that are one payment counted twice; the clean-up sweep finds and combines them.
- **Generic name**: the placeholder a parser gives a payment whose SMS names no merchant ("Payment (HDFC Bank)",
  "Credit (SBI)").
- **Ledger**: the only way a person's change reaches the payments (`data/ledger/Ledger.kt`). Keeps the books straight
  after each change: flow fits direction, corrections marked, refunds given back, deletions remembered.
- **Follow-up**: the work after any change (refund pairing, split detection, the widget). Asked for through the ledger,
  never run twice at once.
- **Correction**: a change a person made to a payment (`userEdited`); automatic rewrites leave it alone. A split
  shrinking a payment is a **reshape**, not a correction.
