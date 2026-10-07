# Redesign "Quiet ledger": report

**Branch:** `redesign` (from `main` @ 32c6ddb, v1.3.1), not pushed. **Date:** 7 Oct 2026.
**Plan:** [`docs/PLAN-redesign.md`](../PLAN-redesign.md) · **Mock:** [`mock/index.html`](mock/index.html) ·
**Tokens and contrast:** [`tokens.md`](tokens.md) · **Before/after page:** [`index.html`](index.html) ·
**Pairs:** [`pairs/`](pairs/) (70, same names as `docs/ui-polish-2/before`; `dark-*` dark mode, `big-*` 200% font).

**Tests:** `bash build.sh testDebugUnitTest` → **572 tests, 0 failures** (538 before, 34 new).
**Privacy audit:** `scripts/audit_apk.py` on the debug APK (the strict check): telemetry classes at baseline
(datatransport 630, firebase 266, firelog 2), the only endpoint is `api.openai.com`. No permission, SDK or endpoint
added; the one new dependency is a font file (Inter Tight, OFL, `licenses/InterTight-OFL.txt`).
**Device:** emulator `fintest` (393dp, density 440) as `com.pft.financetracker.debug.redesign`, seeded with the
17-SMS inbox, Rent ₹25,000 (day 8), card ••9012, Goa trip ₹1,05,000 + ₹5,000 top-up, Food budget ₹300 and a ₹2,400
dinner split for 3.

## What changed

**Tokens (`ui/theme`).** Deep ink-blue accent (#1F3BD6 / #9DA8FF) as the one tappable colour; four surface tiers
(page, card, raised, sunken) in `LocalSurfaces`; money in Inter Tight with tabular figures (`MoneyType`); the type
scale display 56/44/36, title 22/20/17, body 16/14/13, label 12/11; springs in `Motion` with `motion()` snapping when
"Remove animations" is on; haptics (confirm, tick, reject). 114 colour pairs measured, none below AA.

**Components (`ui/components`).** `AmountDisplay` (rolling digits, small raised ₹, steps down a size instead of
wrapping), `LedgerRow`/`TransactionRow` (lined-up amount column, spoken label, shared elements), `DayHeader`,
`SegmentedControl`, `FilterButton` + `AppliedTag`, `FinSheet`, `InfoSheet`/`LearnMore`, `NumberPad`, `SpendChart`
(tap or drag to read, 44dp+ columns, TalkBack per bar), `CategoryRing`, `ProgressMeter` (stripes when over),
`Skeleton*`, `EmptyState` (title, why, action), `ErrorState`, `ExpandableCard`, `SwipeActions` (also TalkBack actions),
`FinSnackbarHost`, `AddButton` (extended at the top, a circle once scrolled, hidden while scrolling down). Previews
for each in light, dark and 200% (`Previews.kt`). Removed: `DonutChart`, `Legend`, `BarChart`, `LoadingState`,
`AddFab`, `AddFabExtended`.

**Screens.** Home as a statement (hero, spark chart, tiles, cards by urgency with one-line summaries); quick add as a
sheet (also from the widget and shortcuts) with "More details" into the grouped editor; Activity with sticky day
headers, a filter sheet, swipe to categorise or delete with undo, long-press multi-select; a new read-first
transaction detail; Split as a balance board and receipts with a settle-up sheet; Insights with one interactive chart,
ranked category changes and dismissible tips; Money tools as a grid of live tiles, each tool with a hero; Settings as
an index of eight pages with a typed DELETE to clear data; onboarding in three steps with a live first scan; the
widget in two sizes with the new palette.

**Also.** `FLAG_SECURE` removed in every build (requested). Bills, cards and goals start "not loaded" so their screens
show a skeleton instead of flashing the empty state. README restructured; parser and counting rules moved to
[`docs/HOW-IT-WORKS.md`](../HOW-IT-WORKS.md).

## Tests added (written first)

| Test | Covers |
|---|---|
| `ActivityFilterTest` | each filter, combining within and across groups, date range ends, search alongside filters, badge count, removing a tag, accounts by use, a day's net |
| `LedgerEditsTest` | swipe delete hides at once, undo restores, a second delete finishes the first, delete happens once; recategorise writes only changed rows and marks them edited; selection |
| `QuickAddDraftTest` | the number pad's rules (decimals, leading zero, length), backspace, what Save writes, cash rule, no save without amount or category, last-used categories first |
| `TransactionDetailTest` | sign and tone, "counts as" words, source, SMS present, split share note and link, refunds both ways, reversal, missing other side, bill and tax lines, paise only when present |
| `ChartAndSummaryTest` | value at a touch position, drags past the edges, empty charts, bar heights, spoken labels; Home's one-line summaries for budgets, categories, merchants and money not counted |

## Not done, and why

- **Split's empty state after**: its screenshot came out black during an emulator capture fault, and the seeded
  split now exists, so it was not retaken. The screen uses the shared `EmptyState` with a "New split" button.
- **Widget small size** is built (`SizeMode.Responsive`) but only the 4×2 placement was captured.
- **60 fps scroll** was not measured: this machine has ~1 GB free and the emulator ran system-wide ANRs during the
  session; a debug build is also interpreted after each install (a cold start took 10–13 s here). Worth a check on a
  phone with a release build.
- **Release audit**: run on the debug APK (the strict check, per the script); no release build was made.
