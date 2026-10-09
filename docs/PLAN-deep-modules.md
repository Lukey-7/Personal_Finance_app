# Deep modules plan: Same payment, Ledger, Books

**Status:** approved 9 Oct 2026, in progress.

**Goal:** stop the class of bugs fixed on 9 Oct, where one caller forgot a rule (cash setting, refund unpairing, duplicate
check, follow-up). Three deep modules, built in order, each one small interface with the rules inside it.

**Who does what:** I write all the code myself, in sequence, one module at a time. When all three are done, one reviewer
agent checks the code read-only (prompt at the end). I fix what it confirms.

**Vocabulary** (goes into a new `CONTEXT.md` as each module lands):
- **Same payment**: the rule that decides whether two rows are one payment, and what survives when they merge.
- **Ledger**: the only module that writes payments; keeps the books straight after every change.
- **Books**: your payments read through the counting rules; every figure, and the payments behind it, comes from here.
- **Counting rules**: what counts as spend and income (today: whether ATM cash counts).
- **Figure**: one number on screen (spend, income, a category, a budget) that Books can also list the payments of.

## Global rules

- Branch `deep-modules` in a new worktree, from `v1.5-fixes`. Local files copied in. Nothing pushed or merged.
- No behaviour change: every existing test must still pass, unchanged, except where a test reached past an interface
  that is being removed (then it moves to the new interface, same assertions).
- No database change (still version 7). No new network, no new dependency.
- One Gradle run at a time; the emulator stays off while tests run (the solver timing test fails with it on).
- Commit after each phase, explicit paths, plain message, no attribution.

## Phase 1 · Same payment (pure, in-process)

- [ ] New `domain/ledger/SamePayment.kt`: `match(a, b): Match` (`Same(reason)` / `Different(reason)`) and
      `merge(kept, other): Transaction`.
- [ ] Move the rules into it, unchanged: ref match (same type, amount within 5 paise or split original, 3 days,
      normalised refs), 10-minute window, generic merchant, different reporter, legacy midnight rows, statement party
      match, which fields win on merge.
- [ ] Callers become thin: `findLikelyDuplicate`, the clean-up sweep, `StatementImporter`, `SmsImporter.mergeDuplicate`,
      `insertReviewed`.
- [ ] Tests: one table-driven `SamePaymentTest` (pairs in, verdict out), built from every case in
      `DuplicateDetectionTest` and `StatementImporterTest`. Old DB tests keep passing.
- [ ] Deletion test: removing `SamePayment` would put the rules back in 4 places. Passes.

## Phase 2 · Ledger (local-substitutable: in-memory Room in tests)

- [ ] New `data/ledger/Ledger.kt`, interface:
      `add(t, from)`, `correct(t)`, `remove(id)`, `approve(reviewId, t)`, `recategorise(ids, category)`.
      `add` returns `Added(id)` or `Merged(id)` (uses Same payment).
- [ ] Inside, every time: flow fits direction (`FlowRules`), user-edited mark on corrections, duplicate check on adds
      and approvals, refund unpairing and deletion memory on removes, then one follow-up (refunds, splits, widget),
      run in the background so callers never wait or forget.
- [ ] Callers switch: AppViewModel (save, delete, recategorise, resolveReview, the split's payment writes),
      QuickAddActivity, SmsImporter's insert, StatementImporter's commit.
- [ ] Nothing outside `data/ledger` and `data/repository` writes to the transactions table (checked by a grep in the
      reviewer's list).
- [ ] Tests: `LedgerTest` through the interface only, one per rule (quick add's money-in can't be spend, removing a
      refunded purchase restores the refund, approving a duplicate merges, every write runs the follow-up once).

## Phase 3 · Books (in-process)

- [ ] New `domain/books/Books.kt`: `Books.of(payments, rules)`, interface:
      `summary(period)`, `payments(period, figure)`, `budgets(month, limits)`, `trends(current, previous)`, `tips(...)`.
- [ ] InsightsEngine's counting functions become Books' implementation; the cash flag leaves every interface.
- [ ] AppViewModel exposes one `books` flow (payments + counting rules). Home, Insights, Budgets, drill-down, Ask
      (rules, Nano, ChatGPT facts), widget, goals and the AI summary read from it.
- [ ] Tests: `BooksTest` invariant over generated data: for every figure, the figure equals the sum of
      `payments(period, figure)` (net of refunds), with cash counted and not counted. Existing Insights tests move to
      Books, same numbers.
- [ ] Grep check: no `includeCash` / `isSpend(` outside `domain/books`.

## Phase 4 · Verify

- [ ] All unit tests green (emulator off).
- [ ] Debug build installed as `.v15` on the emulator; walk Home (months and weeks), Activity, edit, split create and
      delete, a bill, Insights, Ask. Numbers the same as before the refactor. No SMS injected (it reaches the shared
      installs).
- [ ] Reviewer agent (below), then fix what it confirms, retest.

## Reviewer agent prompt

> Read-only review of branch `deep-modules` against `v1.5-fixes` in `<worktree path>`. Do not run Gradle or adb.
> The branch replaces scattered rules with three modules: `domain/ledger/SamePayment.kt`, `data/ledger/Ledger.kt`,
> `domain/books/Books.kt`. It must change no behaviour. Check, with file:line evidence:
> 1. Behaviour: for each rule moved (duplicate matching, merging, flow normalising, refund unpairing, deletion memory,
>    follow-up, spend counting, cash setting, refunds netting, drill-down totals), the old and new code give the same
>    result. Name any input where they differ.
> 2. Completeness: no write to the transactions table outside `data/ledger` and `data/repository`; no `includeCash` or
>    `isSpend(` outside `domain/books`; no caller still runs its own follow-up.
> 3. Interfaces: each module's interface is small and every test goes through it, not past it.
> 4. Threading: no database or counting work on the main thread; the follow-up cannot run twice at once or be
>    cancelled half way leaving the books wrong.
> 5. Tests: list behaviour that lost a test in the move.
> Report only confirmed issues, ranked by money impact, each with the failing input and the fix.
