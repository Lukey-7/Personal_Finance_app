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

- [x] New `domain/ledger/SamePayment.kt`. Its interface turned out as three checks and three merges, not one
      `match(a, b)`: the import check, the statement check and the clean-up sweep use different rules today (only the
      import knows statement and claimed rows; only the sweep pairs two stored rows), so one pairwise rule would have
      changed behaviour. `find(incoming, stored)` returns the row and why (`REF`, `STATEMENT`, `LEGACY`, `WINDOW`);
      `findForStatement(row, stored, consumed)`; `twinsIn(rows)`; `merge`, `addIdentifiers`, `combine`.
      Stored rows come through a small `Stored` seam: Room in the app, a list in `SamePaymentTest`.
- [x] Rules moved unchanged: ref match (same type, 5 paise or split original, 3 days, normalised refs), 10-minute
      window, generic merchant, different reporter, legacy midnight rows, statement party match, merge precedence.
- [x] Callers thin: `findLikelyDuplicate`, `findExistingDuplicates`, `mergeDuplicates`, `insertReviewed` delegate;
      `SmsImporter.mergeDuplicate` and `StatementImporter.findExisting`'s rules are gone (the same-file hash stays there).
- [x] `SamePaymentTest`: 21 incoming cases, 9 statement cases, the sweep, combining and merging. Old DB tests unchanged
      and green (DuplicateDetection 26, StatementImporter 7, EdgeStatementImporter 13, ReviewFixesDb 11, ImportMemory 12).
- [x] Deletion test: removing `SamePayment` puts the rules back in the repository, SMS importer and statement importer.

## Phase 2 · Ledger (local-substitutable: in-memory Room in tests)

- [x] New `data/ledger/Ledger.kt`: `add`, `correct`, `reshape` (a split shrinking a payment: not a person's
      correction), `remove`, `recategorise`, `approve`, `mergeTwins`, `undoImport`, `together { }` (one change, one
      follow-up), `followUp(useAi)` and `catchUp(useAi)` (waits; for the SMS worker and restore).
- [x] Inside, every time: flow fits direction (`FlowRules`), the corrected mark, the twin check on approvals, refund
      give-back and deletion memory on removes, then one follow-up in an app-wide background scope. Requests during a
      run fold into one next run (AI if any asked), so it never runs twice at once and leaving a screen can't cancel it.
      `AppContainer.afterChange` is now private to the ledger.
- [x] Callers switched: AppViewModel (save, delete, recategorise, resolveReview, mergeDuplicates, saveSplit, scan,
      statement import and undo, restore, refreshSplits), QuickAddActivity, the SMS worker.
- [x] Changed on purpose (bugs of the kind this phase is for): the duplicate clean-up now runs the follow-up (it
      didn't, so a refund paired with a dropped twin waited for the next scan); undoing a statement import now gives
      back the refunds paired with the purchases it removes (it left them lowering spend); recategorise reads the rows
      from the database rather than the screen's copy.
- [x] Not moved, by design: the importers' bulk inserts (SMS, statements) keep their own pipelines and ask for one
      follow-up at the end; refund pairing and split detection write flows as part of the follow-up itself; split
      actions write through the split engine. No write to payments is left in `ui/` (grep in the reviewer's list).
- [x] `LedgerTest` (10) through the interface: money in never spend, corrections marked and fitted, reshape unmarked,
      removing a refunded purchase restores the refund, removed SMS remembered, recategorise writes only changes,
      approving a stored twin merges, undoing an import restores refunds, one change one follow-up, requests during a
      run fold into one more run.

## Phase 3 · Books (in-process)

- [x] New `domain/books/Books.kt`: `Books.of(payments, rules)` with `summary(period)` (worked out once per period),
      `payments(period, bucket, category)`, `Books.total(list, bucket)`, `budgets(limits, month)`, `trends(current,
      previous)`, `tips(budgets, now)`, `isSpend(t)`, and `all`. `CountingRules(cashIsSpend)` holds the counting rules.
- [x] InsightsEngine's counting moved into Books verbatim; InsightsEngine keeps the shared types, periods and the
      merchant key. The cash flag left every interface (Ask's context, the widget, Home's figures).
- [x] AppViewModel exposes one `books` flow (payments + rules); `loaded` now reads it, so once a screen shows figures the
      books it reads are loaded too. Home, Insights, Budgets, drill-down, Ask (rules, Nano, ChatGPT facts), the widget,
      goals' "last month's savings" and the AI summary read from it. The unused `vm.summary`/`vm.drillDown` are gone
      (the latter ignored the cash setting).
- [x] Changed on purpose (the books' promise found it): Home's "Transfers & card bill payments", "Transfers in" and
      "Paid back by friends" all opened one list of both directions, and "Investments" listed redemptions too, so the
      list's total never matched the row tapped. Each now opens its own figure's payments (`TRANSFERS_OUT`,
      `TRANSFERS_IN`, `PAID_BACK`, money-out `INVESTMENTS`).
- [x] `BooksTest`: on 40 generated months, each under both rules, every figure equals the total of its payments (net
      spend, each category, refunds, income, transfers out/in, paid back, investments, cash, counts); budgets read the
      summary's category figures; cash counts only under its rule. Existing Insights, monthly summary, period compare,
      recurring tips, split and Home tests moved to Books with the same assertions.
- [x] Grep: no `includeCash` anywhere; `isSpend` only inside Books (Ask asks `books.isSpend`).

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
