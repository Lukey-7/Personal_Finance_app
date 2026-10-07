# Redesign: "Quiet ledger"

**Branch:** `redesign` (worktree `C:\Users\hp\Personal_Finance_app-redesign`, from `main` @ 32c6ddb, v1.3.1).
**Goal:** keep every feature, every number and all data behaviour; change how FinTrack looks, moves and reads.
**Direction:** Buro's calm (warm page, hairlines, one accent, money as the hero) grown into a confident, tactile app:
real surface depth, a display face for money, springy motion, sheets instead of dialogs, and one read-first screen per
transaction.

Mock of Home, Activity and the add sheet: [`docs/redesign/mock/index.html`](redesign/mock/index.html).
Tokens and contrast: [`docs/redesign/tokens.md`](redesign/tokens.md).

Rules kept: no network endpoint, analytics or new SDK (fonts are resources); `Rupees.format` for every figure,
`approxMoney` for estimates; the `notLoaded()`/`isLoaded()` marker and its tests; `heroLine` wording and its tests; no
database change (still version 7).

---

## 0. Tokens and shared components (`ui/theme`, `ui/components`)

### Colour (`Theme.kt`)
- [x] Accent: light **#1F3BD6** (7.9:1 on card), dark **#9DA8FF** (8.2:1 on card). The only tappable colour.
- [x] `accentSoft` (selected chip, tinted squares): light #EBEEFC, dark #1E2340; accent text on it ≥ 6.8:1.
- [x] Surface tiers, as a `Surfaces` token object in a `LocalSurfaces` composition local:
      page #FDFCFB / #0B0B0C · card #FFFFFF / #151618 · raised #FFFFFF + soft shadow / #1D1E21 · sunken #F3F2EE / #101113.
- [x] Ink #111214 / #F2F2F3, muted #62666E / #A3A7AE, hairline #E6E4DF / #2A2B2F, outline (UI 3:1) #85888F / #6E727A.
- [x] MoneyColors unchanged: income #007F22 / #4CD07D, expense #B50000 / #FF7B72, neutral #6A6F77 / #9AA0A8.
- [x] `docs/redesign/tokens.md`: every text pair ≥ 4.5:1 and every UI pair ≥ 3:1, computed by `scripts/contrast.py`.

### Type
- [x] Inter for UI. **Inter Tight** (variable, OFL, bundled in `res/font`) for money only: `MoneyDisplay` styles.
- [x] Scale: display 56/44/36, title 22/20/17, body 16/14/13, label 12/11. `tnum` on every money style and column.
- [x] Money never wraps or truncates: `AmountDisplay` and `LedgerAmount` are `softWrap = false`, `maxLines = 1`, and
      the display sizes step down (56 → 44 → 36) to fit the width instead of clipping.

### Motion and haptics (`Motion.kt`)
- [x] `Motion.spring*` specs (Expressive-style: bouncy for spatial, critically damped for colour/alpha).
- [x] `reducedMotion()` reads `Settings.Global.ANIMATOR_DURATION_SCALE == 0`; every animation snaps when it is set.
- [x] Haptics helper: `confirm` on save, `tick` on chip/segment changes, `reject` on invalid input.
- [x] Rolling totals (`AmountDisplay` digits slide to a new value), list items settle in, spring sheets.
- [x] Shared-element transition from a ledger row into Transaction detail (`SharedTransitionLayout` around the NavHost).
- [x] Predictive back: `android:enableOnBackInvokedCallback="true"`; nav transitions use the system back progress.

### Components (each with `@Preview` light / dark / 200% font in `ui/components/Previews.kt`)
- [x] `AmountDisplay` (display face, sign before ₹, rolling digits, auto step-down size).
- [x] `LedgerRow` (avatar · name/meta · amount column of fixed min width, tabular, aligned across rows).
- [x] `DayHeader` (sticky; date on the left, the day's net on the right).
- [x] `SegmentedControl` (replaces most two-to-four chip choices; sliding thumb; tick haptic).
- [x] `FilterSheet` (category, account, flow, date range, reversed) + count badge on the trigger.
- [x] `NumberPad` (large keys, ₹ display, backspace long-press clears, decimal guard).
- [x] `SpendChart` (bars or line; tap/drag to read a value; 44dp+ hit columns; TalkBack label per bar).
- [x] `CategoryRing` (donut; tap a slice or legend row to drill; centre label capped for 200%).
- [x] `ProgressMeter` (budgets, goals; over state with a pattern + words, never colour alone).
- [x] `Skeleton` (list rows, cards, hero) with a shimmer that stops under reduced motion; replaces `LoadingState` spinners.
- [x] `EmptyState` (what this is · why it is empty · how to start, + one action).
- [x] `SwipeActions` (left: categorise; right: delete with undo / split).
- [x] `FinSnackbar` style (raised surface, accent action) and `InfoSheet` ("Learn more").
- [x] `ExpandableCard` (title, one-line summary when collapsed, chevron rotates, content animates).
- [x] Remove what these replace: `BarChart`, `DonutChart`, `Legend`, `LoadingState`, `SoftPanel` (→ sunken card),
      `AddFab` (→ `AddButton`, extended→circle), `PillChip` rows where a segment or sheet now does the job.

## 1. Home ("Today's statement")
- [x] Period as a `SegmentedControl` (This month · Last month · This week) + a calendar icon for a custom range.
- [x] Hero: `AmountDisplay(netSpend)`, `heroLine` under it (wording and tests unchanged), then a tappable 6-period
      spark-bar `SpendChart` (tap a bar → that period on Home; hold to read the value).
- [x] Three compact tiles in a row: Income · Spend · Savings (each drills as today). At 200% they stack.
- [x] `ExpandableCard`s ordered by urgency: needs review → shared payments → budgets over/near → where it went
      (`CategoryRing`) → top merchants → not counted as spend (one line + info sheet) → by account. Collapsed summary
      lines ("2 budgets over · Food ₹500 over"). Urgent cards start expanded.
- [x] Recent list as `LedgerRow`s.
- [x] `AddButton`: extended "Add" that shrinks to a circle on scroll; lists reserve its height.
- [x] Skeleton while loading, never ₹0.
- **Check:** every figure on Home still drills to its transactions; heroLine tests green.

## 2. Add a transaction
- [x] Quick-add `ModalBottomSheet`: amount (`NumberPad`) → category grid (last used first) → optional note → Save.
      "More details" opens the full editor pre-filled. Same sheet from Home, Activity, the widget and the app shortcut
      (QuickAddActivity hosts it over the launcher).
- [x] `QuickAddDraft` (pure): builds the `Transaction`, including the cash rule (cash + counted ATM = transfer).
- [x] Full editor: amount hero at top, then grouped sections: Amount & type · What it was · When & where · Counts as ·
      Tax. Category and flow chips wrap (no clipping).
- **Tests:** `QuickAddDraftTest` (saving, cash rule, invalid amounts), `RecentCategoriesTest` (last used first).

## 3. Activity
- [x] Search in the top bar (expands), overflow menu with text labels: "Import statement", "SMS log", "Review".
- [x] Filters in a `FilterSheet` (category, account, flow, date range, show reversed) with a count badge.
- [x] Sticky `DayHeader`s with each day's net; `LedgerRow`s with a lined-up amount column.
- [x] `SwipeActions`: start-to-end categorise; end-to-start delete with an Undo snackbar (split from the row menu).
- [x] Long-press to select several; a contextual bar recategorises them all.
- **Tests:** `ActivityFilterTest` (each filter, count badge, search), `PendingDeleteTest` (delete + undo, commit on
  timeout), `BulkRecategoriseTest`, `DayNetTest` (day's net total).

## 4. Transaction detail (new, read-first)
- [x] Route `txn/{id}`; every list row opens it (shared-element on the amount and avatar).
- [x] Amount hero, merchant, category chip, account, date/time, original SMS (if kept), linked split / refund / bill,
      tax section, then "Edit" (opens the editor). Delete lives in the overflow with undo.
- **Tests:** `TransactionDetailTest` (mapping: sign, flow words, links, tax line, SMS present or not).

## 5. Split
- [x] Balance board: two columns (Owed to you · You owe) with avatars (contrast-checked initials) and amounts.
- [x] Each split as a receipt card with a perforated edge; settle-up as a sheet listing that person's matching
      payments first (`settleCandidates`).

## 6. Insights
- [x] One interactive `SpendChart` with a Monthly/Weekly `SegmentedControl`.
- [x] Category trends as a ranked list with mini bars and arrow + words ("up 40%").
- [x] "Reduce spending" as dismissible tip cards (dismissed for the session).
- **Tests:** `ChartHitTest` (value at a position, edges, empty bars).

## 7. Money tools
- [x] Two-column grid of tiles with a live number each (subscriptions ₹/month, next bill + date, card cycle spend,
      goal %, net worth, tax found, Ask).
- [x] Each tool screen opens with its own hero `AmountDisplay`.

## 8. Settings
- [x] Grouped index: SMS & import · Calculation · Splits · Reminders · Backup · Widget · AI · Your data, icons in small
      tinted squares; each opens its own page (route `settings/{section}`).
- [x] Descriptions: one line + "Learn more" `InfoSheet`.
- [x] "Clear all data" last, red, with a typed confirmation ("DELETE").

## 9. Onboarding
- [x] Three short steps (what it does → why SMS → privacy promise) with one illustration style (drawn in Compose),
      then a live first scan with progress that ends on Home.

## 10. Widget (Glance)
- [x] Two sizes via `SizeMode.Responsive`: small (spent this month), medium (spent + budget left + next bill +
      Add / Cash). New accent, light/dark, ₹•••• by default.

## Fix while there
- [x] "Not counted as spend": one-line summary + info sheet.
- [x] Settings paragraphs → one line + Learn more.
- [x] Chip rows that cut off → wrap or FilterSheet.
- [x] Donut centre and chart labels at 200%; nav bar one line at 200%.
- [x] Skeleton, empty and error states on every screen.

## Done when
- [x] `bash build.sh testDebugUnitTest` green (538 + new), `assembleDebug` builds.
- [x] `scripts/audit_apk.py` baselines unchanged (no new permission, endpoint or SDK).
- [x] After screenshots (light, dark, 200%) in `docs/redesign/after/`, pairs in `docs/redesign/pairs/`, one page.
- [x] Test data cleared from the `.redesign` install; emulator left running normally; Gradle stopped.

See [`docs/redesign/REPORT.md`](redesign/REPORT.md) for what was not done (60 fps not measured on this machine, Split empty state not re-shot).
