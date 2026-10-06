# UI polish 2 implementation plan

> **For agentic workers:** execute inline with superpowers:executing-plans (no subagents), with
> superpowers:test-driven-development wherever logic changes. Steps use `- [x]` checkboxes; tick them as each is done
> and verified.

**Status:** implemented and verified, 6 Oct 2026 (D1–D4 as recommended). 531/531 tests. Report: [`docs/ui-polish-2/REPORT.md`](ui-polish-2/REPORT.md). Deviations are listed under "Not done" there.

**Goal:** fix the rough edges the audit found in buttons, spacing, copy, states and consistency, with no new features.

**Architecture:** first make the shared layer right (one money formatter, theme-aware money colours, spacing tokens,
one component per button role, a loading wrapper), then move each screen onto it, most visible first. Logic lives in
small pure functions with unit tests; screens only call them.

**Tech stack:** Kotlin, Jetpack Compose, Material 3, Glance (widget), JUnit 4 + Robolectric.

**Spec:** the user's brief and the audit, [`docs/ui-polish-2/AUDIT.md`](ui-polish-2/AUDIT.md). Before screenshots:
[`docs/ui-polish-2/before/`](ui-polish-2/before/).

## Global constraints

- Branch `ui-polish` in worktree `.claude/worktrees/ui-polish`, from `main` @ ef89566. Never touch `main`.
- Build and test only with `bash build.sh <tasks>`; unit tests `bash build.sh testDebugUnitTest`. One Gradle run at a
  time, long runs in the background.
- Install only as `bash build.sh assembleDebug -PappIdSuffix=.polish` (`com.pft.financetracker.debug.polish`). Check
  `topResumedActivity` before any tap. Never clear the shared app's data.
- No behaviour or data change unless it fixes a visible bug; each such fix starts with a failing test.
- Money: one format everywhere, Indian grouping (₹1,05,000). Paise shown only when non-zero (D1).
- Copy: sentence case, verb-first buttons, no trailing full stop on buttons, no "txn", no raw reason codes.
- Commits: explicit paths only, no AI attribution. No push, merge or release unless asked.
- Baseline: 510 unit tests, 0 failures (6 Oct 2026).

## Decisions to confirm (my recommendation first)

- **D1 Money decimals.** Show paise only when the amount has them: ₹2,400 and ₹2,400.50. Today split screens always
  print ".00" and everything else rounds to the rupee (so ₹2,400.50 shows as ₹2,401 on Home). *Alternative:* always
  round outside the split screens.
- **D2 Colour values.** Change light Income green #00A62D → #007F22 and light muted grey #70757D → #6A6F77, and give
  dark mode its own money colours (Income #4CD07D, Expense #FF7B72, Neutral #9AA0A8). All measured ≥4.5:1. Hues stay
  the same family. *Alternative:* keep light values, fix dark only.
- **D3 Part-period comparisons.** Compare "this month so far" with the same number of days of the previous period
  ("↓ 20% vs 1–6 Sep") instead of the whole previous month, and hide the comparison and projection while spend is ₹0.
  This changes a displayed percentage, so it gets a test. *Alternative:* hide the comparison until the period ends.
- **D4 Nav labels at large font.** Labels stay one line and stop growing past 1.3× (icons keep their size; the bar grows
  to fit). *Alternative:* hide labels above 1.5× and rely on the TalkBack names.

## Review focus

Inputs the tests below pin, most likely to bite first:

1. Negative, zero, sub-rupee and crore amounts through the one formatter (−₹50, ₹0, ₹0.50, ₹1,00,00,000).
2. A scan that fails or finds nothing must say so, not "Scanned 0 SMS: 0 added…".
3. A period with ₹0 spend, and the first day of a period, must not produce "100%" or "÷0".
4. An unknown reason code (a future parser rule) must still read as words, not crash or show `snake_case`.
5. Amount search with and without commas, and with a decimal point.

---

## Phase A: foundations (everything later builds on these)

### Task A1: one money formatter, Indian grouping 🔴

**Files:**
- Create: `app/src/main/java/com/pft/financetracker/domain/model/Rupees.kt`
- Modify: `ui/components/Format.kt` (`money`), `domain/insights/InsightsEngine.kt:297-301` (`fmt`),
  `domain/split/SplitSolver.kt:314-319` (`rupees`), `domain/widget/WidgetSnapshot.kt:30`
- Test: `app/src/test/java/com/pft/financetracker/RupeesTest.kt`

**Interfaces:** produces `object Rupees { fun format(paise: Long, paiseMode: Paise = Paise.WHEN_NONZERO): String; fun group(n: Long): String }`
with `enum class Paise { NEVER, WHEN_NONZERO, ALWAYS }`. `money(paise, decimals)` keeps its signature and delegates
(`decimals = true` → `ALWAYS`, used only by editable text). `InsightsEngine.fmt` and `SplitSolver.rupees` delegate and
lose the "1.1L" compaction.

- [x] **Step 1: failing test**

```kotlin
package com.pft.financetracker

import com.pft.financetracker.domain.model.Paise
import com.pft.financetracker.domain.model.Rupees
import org.junit.Assert.assertEquals
import org.junit.Test

class RupeesTest {
    @Test fun groupsTheIndianWay() {
        assertEquals("0", Rupees.group(0))
        assertEquals("999", Rupees.group(999))
        assertEquals("1,000", Rupees.group(1_000))
        assertEquals("1,05,000", Rupees.group(1_05_000))
        assertEquals("12,34,567", Rupees.group(12_34_567))
        assertEquals("1,00,00,000", Rupees.group(1_00_00_000))
    }
    @Test fun paiseOnlyWhenPresent() {
        assertEquals("₹2,400", Rupees.format(2_400_00))
        assertEquals("₹2,400.50", Rupees.format(2_400_50))
        assertEquals("₹0.50", Rupees.format(50))
        assertEquals("₹0", Rupees.format(0))
    }
    @Test fun negativesKeepTheSignOutside() {
        assertEquals("-₹50", Rupees.format(-50_00))
        assertEquals("-₹1,05,000.05", Rupees.format(-1_05_000_05))
    }
    @Test fun modes() {
        assertEquals("₹2,401", Rupees.format(2_400_50, Paise.NEVER))     // rounds half up, as money() did
        assertEquals("₹2,400.00", Rupees.format(2_400_00, Paise.ALWAYS))
    }
}
```

- [x] **Step 2:** `bash build.sh testDebugUnitTest --tests '*RupeesTest'` → fails to compile (no `Rupees`).
- [x] **Step 3: implement**

```kotlin
package com.pft.financetracker.domain.model

import kotlin.math.abs

enum class Paise { NEVER, WHEN_NONZERO, ALWAYS }

/** The one rupee format: Indian grouping (1,05,000), sign before the ₹, paise per [Paise]. */
object Rupees {
    fun group(n: Long): String {
        val s = abs(n).toString()
        if (s.length <= 3) return (if (n < 0) "-" else "") + s
        val head = s.dropLast(3); val tail = s.takeLast(3)
        val pairs = head.reversed().chunked(2).joinToString(",").reversed()
        return (if (n < 0) "-" else "") + pairs + "," + tail
    }

    fun format(paise: Long, mode: Paise = Paise.WHEN_NONZERO): String {
        val sign = if (paise < 0) "-" else ""
        val a = abs(paise)
        val body = when {
            mode == Paise.NEVER -> group(Math.round(a / 100.0))
            mode == Paise.ALWAYS || a % 100 != 0L -> group(a / 100) + "." + (a % 100).toString().padStart(2, '0')
            else -> group(a / 100)
        }
        return "$sign₹$body"
    }
}
```

- [x] **Step 4:** run the test → passes.
- [x] **Step 5: delegate the three old formatters.** `money(paise, decimals)` → `Rupees.format(paise, if (decimals) Paise.ALWAYS else Paise.WHEN_NONZERO)`;
  `money(rupees: Double)` unchanged (goes through the paise version). `InsightsEngine.fmt(p)` →
  `(if (p < 0) "-" else "") + Rupees.format(abs(p)).removePrefix("₹")` (callers prepend ₹); `SplitSolver.rupees(p)` → `Rupees.format(p)`.
  Search for callers that prepend "₹" to `fmt` (`AskEngine`, `MonthlySummary`, `NanoPrompt`, `Bills.kt`, `WidgetSnapshot`)
  and leave them as they are.
- [x] **Step 6:** full suite `bash build.sh testDebugUnitTest` → 510 + 4 pass. If any existing test pinned "1.2L" or
  "₹105,000", update it to the Indian form in the same commit and note it here.
- [x] **Step 7:** commit `Format money one way everywhere: Indian grouping, paise only when present`.

### Task A2: theme-aware money colours and contrast 🔴 (D2)

**Files:** `ui/theme/Theme.kt`; every reference to `Income`, `Expense`, `Neutral` (14 files, found with
`grep -rlw 'Income\|Expense\|Neutral' ui`).

**Interfaces:** produces `@Immutable data class MoneyColors(val income: Color, val expense: Color, val neutral: Color)`,
`val LocalMoneyColors`, and accessors `MaterialTheme.money` (an extension `val MaterialTheme.money: MoneyColors @Composable get()`).
The top-level `Income`/`Expense`/`Neutral` vals are removed so the compiler finds every use.

- [x] Light: income #007F22, expense #B50000, neutral #6A6F77; `Muted` (onSurfaceVariant) #6A6F77.
  Dark: income #4CD07D, expense #FF7B72, neutral #9AA0A8.
- [x] Provide them from `FinTrackTheme` via `CompositionLocalProvider`.
- [x] Replace every `Income`/`Expense`/`Neutral` use with `MaterialTheme.money.income` etc. Zero amounts use
  `onSurface`, never a money colour (`moneyTone(paise)` helper in `Format.kt`: `>0 → income`, `<0 → expense`, `0 → onSurface`).
- [x] Evidence: `dark-home2` (over-budget line), `dark-activity`, `05-home-lastmonth-1` (Income line) before/after.
- [x] Commit `Give money colours a dark variant and lift both themes to 4.5:1`.

### Task A3: spacing and shape tokens, one component per button role 🟡

**Files:** `ui/components/Buro.kt`, `ui/components/Rows.kt`.

**Interfaces (produced, used by every later task):**
- `object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }`,
  `val CardRadius = 22.dp`, `val CardShape = RoundedCornerShape(CardRadius)`. `Gutter`/`CardPadding` stay (= `Space.xl`).
- `PrimaryButton(text, onClick, modifier, enabled, fill = true, icon: ImageVector? = null)`: solid accent pill, 52dp.
- `SecondaryButton(text, onClick, modifier, enabled, fill = false, icon = null)`: pill, 52dp, 1dp hairline outline,
  accent text, transparent fill.
- `TextAction(text, onClick, modifier, enabled, tone = Tone.Accent | Tone.Danger, alignStart = false)`: 48dp touch
  height; `alignStart` drops the start padding so the label lines up with the card text edge.
- `PrimaryPill`/`SecondaryPill` become thin aliases for the two above, so nothing breaks mid-migration; Task H3
  deletes them.
- `ActionRow`: min height 48dp (vertical padding 14dp), optional `subtitle`, `tone`.
- `PillChip`: keeps its 38dp look, gains `Modifier.minimumInteractiveComponentSize()` for a 48dp touch target.
- `AmountRow(label, paise, sign = "", tone, onClick)`: shared version of Home's private `MathRow`/`LineRow`, 48dp min
  height when clickable.
- `@Composable fun TextStyle.cappedScale(max: Float = 1.3f): TextStyle`: rescales `fontSize`/`lineHeight` so the text
  grows with the system font only up to `max`. Used by the nav labels (H1) and chart labels (D1) only.
- `FinDialog(title, confirmText, onConfirm, onDismiss, confirmEnabled, danger = false, content)`: `AlertDialog` with the
  white surface, 28dp corners, `TextAction`s.

- [x] Write the components (presentation only).
- [x] Before/after: a scratch "components" preview is not shipped; evidence is the Settings and New split screenshots
  after Tasks E1/C2.
- [x] Commit `Add spacing tokens and one component per button role`.

### Task A4: loading state for the first frame 🟡

**Files:** Create `ui/Loadable.kt`; modify `ui/AppViewModel.kt:107-124`; Test `app/src/test/java/com/pft/financetracker/LoadableTest.kt`.

**Interfaces:** produces `sealed interface Loadable<out T> { data object Loading : Loadable<Nothing>; data class Ready<T>(val value: T) : Loadable<T> }`,
`fun <T> Flow<T>.asLoadable(): Flow<Loadable<T>>`, and in the VM `val loaded: StateFlow<Boolean>` (true once
transactions, splits and budgets have each emitted once). Existing `StateFlow<List<…>>` stay as they are, so screens
change only where they show an empty state.

- [x] **Step 1: failing test**

```kotlin
package com.pft.financetracker

import com.pft.financetracker.ui.Loadable
import com.pft.financetracker.ui.asLoadable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadableTest {
    @Test fun startsLoadingThenReady() = runBlocking {
        assertEquals(listOf(Loadable.Loading, Loadable.Ready(emptyList<Int>())), flowOf(emptyList<Int>()).asLoadable().toList())
    }
    @Test fun anEmptyListIsReadyNotLoading() = runBlocking {
        val last = flowOf(emptyList<Int>()).asLoadable().toList().last()
        assertEquals(Loadable.Ready(emptyList<Int>()), last)
    }
}
```

- [x] **Step 2:** run → fails (no `Loadable`).
- [x] **Step 3:** implement `asLoadable() = map<T, Loadable<T>> { Loadable.Ready(it) }.onStart { emit(Loadable.Loading) }`;
  in the VM `loaded = combine(c.transactions.all, c.splits.all, c.budgets.all) { _, _, _ -> true }.stateIn(viewModelScope, Eagerly, false)`.
- [x] **Step 4:** run → passes.
- [x] **Step 5:** add `LoadingState()` to `Buro.kt`: a centred 24dp `CircularProgressIndicator` with "Loading…" under it
  for TalkBack. Screens show it instead of `EmptyState` while `!loaded` (wired in each screen task below).
- [x] Commit `Show a loading state instead of flashing empty states on launch`.

### Task A5: pure copy helpers 🟡 (TDD)

**Files:** Create `ui/components/Copy.kt`; Test `app/src/test/java/com/pft/financetracker/CopyTest.kt`.

**Interfaces:** produces
- `fun reasonLabel(code: String): String`: plain words for every parser/importer reason, sentence fallback for unknown codes.
- `fun scanResultLine(stats: ImportStats?, failed: Boolean): String`.
- `fun countLabel(n: Int, one: String, many: String = one + "s"): String`.
- `fun matchesAmount(paise: Long, query: String): Boolean`.

- [x] **Step 1: failing test**

```kotlin
package com.pft.financetracker

import com.pft.financetracker.data.sms.ImportStats
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.matchesAmount
import com.pft.financetracker.ui.components.reasonLabel
import com.pft.financetracker.ui.components.scanResultLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTest {
    @Test fun knownReasonsReadAsWords() {
        assertEquals("Couldn't tell if money came in or went out", reasonLabel("type_ambiguous"))
        assertEquals("No amount found", reasonLabel("amount_not_found"))
        assertEquals("A one-time code", reasonLabel("otp"))
        assertEquals("An offer or promotion", reasonLabel("promo"))
        assertEquals("An offer or promotion", reasonLabel("promotional_sender"))
        assertEquals("A payment due later, not yet made", reasonLabel("future"))
        assertEquals("Statement balance doesn't add up", reasonLabel("balance_mismatch"))
        assertEquals("You dismissed it", reasonLabel("dismissed_by_user"))
        assertEquals("Not sure enough to save it", reasonLabel("low_confidence_40"))
    }
    @Test fun unknownReasonsStillReadAsASentence() {
        assertEquals("Some new rule", reasonLabel("some_new_rule"))
        assertEquals("Unknown", reasonLabel(""))
    }
    @Test fun scanLines() {
        assertEquals("12 added · 1 to review · 4 skipped", scanResultLine(ImportStats(1, 17, 12, 1, 3, 1), failed = false))
        assertEquals("No new transactions", scanResultLine(ImportStats(1, 0, 0, 0, 0, 0), failed = false))
        assertEquals("No new transactions · 3 skipped", scanResultLine(ImportStats(1, 3, 0, 0, 2, 1), failed = false))
        assertEquals("Couldn't read your SMS. Check the permission in Settings.", scanResultLine(null, failed = true))
    }
    @Test fun counts() {
        assertEquals("1 payment", countLabel(1, "payment"))
        assertEquals("3 payments", countLabel(3, "payment"))
        assertEquals("2 people", countLabel(2, "person", "people"))
    }
    @Test fun amountSearchIgnoresFormatting() {
        assertTrue(matchesAmount(1_299_00, "1299"))
        assertTrue(matchesAmount(1_299_00, "1,299"))
        assertTrue(matchesAmount(1_05_000_00, "105000"))
        assertTrue(matchesAmount(2_400_50, "2400.5"))
        assertFalse(matchesAmount(1_299_00, "1300"))
        assertFalse(matchesAmount(1_299_00, "food"))
    }
}
```

- [x] **Step 2:** run → fails.
- [x] **Step 3:** implement (the full reason table covers every code listed in AUDIT §1 U5: `not_moved, otp, promo,
  future, future_soft, failed, request, balance_only, statement, login, empty, no_amount_no_type, no_transaction_hint,
  promotional_sender, amount_not_found, type_ambiguous, low_confidence_*, user_flagged, balance_mismatch,
  statement_balance_mismatch, same_sms, same_ref, dismissed_by_user, deleted_by_user`); fallback
  `code.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Unknown" }`. `matchesAmount`: strip
  everything but digits and `.` from the query; return false if no digit; compare against the plain rupee string,
  the paise-precise string and the grouped string with commas removed (`startsWith`).
- [x] **Step 4:** run → passes.
- [x] Commit `Add plain-language helpers for reasons, scan results, counts and amount search`.

### Task A6: like-for-like period comparison 🔴 (D3, TDD)

**Files:** `domain/insights/InsightsEngine.kt` (or `Periods`); Test: add to `InsightsEngineTest.kt`.

**Interfaces:** produces `fun Periods.sameSpanBefore(current: Period, previous: Period, now: Long): Period` (the first
`now - current.start` of `previous`, capped at `previous`'s end; `previous` unchanged when `now` is past `current`) and
`fun changePercent(now: Long, before: Long): Int?` (null when `before == 0` or `now == 0`).

- [x] **Step 1: failing tests**

```kotlin
@Test fun partMonthComparesWithTheSameDaysOfThePreviousMonth() {
    val cur = Periods.month(); val prev = Periods.month(-1)
    val now = cur.start + 5 * 86_400_000L + 3_600_000L          // day 6, 01:00
    val span = Periods.sameSpanBefore(cur, prev, now)
    assertEquals(prev.start, span.start)
    assertEquals(prev.start + (now - cur.start), span.end)
}
@Test fun aFinishedPeriodComparesWithTheWholePreviousOne() {
    val cur = Periods.month(-1); val prev = Periods.month(-2)
    assertEquals(prev, Periods.sameSpanBefore(cur, prev, System.currentTimeMillis()))
}
@Test fun noPercentAgainstNothingOrForNothing() {
    assertNull(InsightsEngine.changePercent(0, 4_218_00))
    assertNull(InsightsEngine.changePercent(800_00, 0))
    assertEquals(-81, InsightsEngine.changePercent(800_00, 4_218_00))
    assertEquals(90, InsightsEngine.changePercent(800_00, 420_00))
}
```

- [x] **Step 2:** run → fails. **Step 3:** implement. **Step 4:** run → passes.
- [x] Commit `Compare a part period with the same days of the previous one`.

---

## Phase B: Home (most visible)

### Task B1: Home ✅ uses A1–A6

**File:** `ui/screens/dashboard/DashboardScreen.kt`. Evidence pairs: `02-home-top`, `05-home-lastmonth-1..4`,
`dark-home1..3`, `big-home1..3`.

- [x] **Scan snackbar (U1):** `duration = SnackbarDuration.Long`, text from `scanResultLine`, action "View log" only
  when something was added or skipped. A failed scan sets `ImportUiState.Failed` in `AppViewModel.scanInbox` (today a
  failure is swallowed into an all-zero result) and shows the failure line. Fixes a visible bug: covered by `CopyTest.scanLines`.
- [x] **Hero line (U2/D3):** use `sameSpanBefore` + `changePercent`; at ₹0 spend show only "Nothing spent yet in Oct";
  words not arrows ("20% less than 1–6 Sep", "20% more than …"); colour from `MaterialTheme.money`, not accent (blue is
  for taps). Daily average and projection only when spend > 0 and at least 3 days in.
- [x] **Rows:** `MathRow`/`LineRow` → `AmountRow` (48dp touch); budget rows get 48dp touch; "By account / card" amounts
  use `moneyTone` so ₹0 is not red.
- [x] **Copy:** donut centre `countLabel(n, "payment")`; Top merchants "1 payment · Food & Dining".
- [x] **FAB clearance (U4):** the list already ends with `FabClearance`, so the fix is a check, not a layout change:
  scroll to the end and confirm "See all", "Set budgets" and the last amount all sit fully clear of the + button. If
  one does not, raise `FabClearance` to 96dp.
- [x] **Suggestions card:** three `TextAction`s → `PrimaryButton(fill=false)` "Split it" + `TextAction` "Not shared" +
  `TextAction` "Details", start-aligned.
- [x] **Loading:** `LoadingState` instead of the Recent empty state while `!loaded`.
- [x] Screenshot after: light, dark, 200% font. Commit `Polish Home: …`.

## Phase C: Activity and Split

### Task C1: Activity

**File:** `ui/screens/transactions/TransactionsScreen.kt`. Evidence: `11-activity-1/2`, `dark-activity`, `big-activity`.

- [x] Search uses `matchesAmount` (U7, covered by `CopyTest`).
- [x] Inset hairlines between rows inside a day (same as Home's Recent).
- [x] Empty state: no transactions → `ReceiptLong` icon, "No transactions yet. Scan your SMS or add one." +
  `SecondaryButton("Add transaction")`; no match → `SearchOff`, "Nothing matches “{query}”." + `TextAction("Clear filters")`.
- [x] Header icons (U8): keep the three, change the SMS-log glyph from a chat bubble to `History` (as in Settings).
- [x] Loading state.
- [x] Commit.

### Task C2: Split home, New split, Split detail

**Files:** `ui/screens/split/SplitHomeScreen.kt`, `NewSplitScreen.kt`, `SplitDetailScreen.kt`. Evidence:
`14-split-empty`, `44-newsplit-1..3`, `50-newsplit-filled`, `51-split-detail`, `52-split-home`, `big-split`, `dark-split`.

- [x] Header (big-split 🔴): two `Column(Modifier.weight(1f))`, the right one end-aligned, labels `maxLines = 2`; ₹0
  in `onSurface`.
- [x] Balances (big-split 🔴): name `weight(1f)` + 12dp gap + amount `softWrap = false`.
- [x] Money: through A1, so "₹2,400.00" becomes "₹2,400" (D1). "Owes ₹800" and "₹1,600 open" lose the accent colour
  (not tappable): `onSurfaceVariant`.
- [x] New split buttons: Photo/Gallery → `SecondaryButton(icon=…)`; "Add" → `SecondaryButton`; quick-add and suggestion
  `AssistChip`s → `PillChip`; category `ChipRow` bleeds to the card edge (`edgeToEdge(CardPadding)` + `inset = CardPadding`).
- [x] In-form labels ("Category for your share", "Quick add", "Who paid the bill?") → `CapsLabel`.
- [x] Copy: "Split equally. Any odd paisa goes to whoever paid." and the matching Shares/Custom/By-item hints.
- [x] Detail: "Settle" → `SecondaryButton`; "Undo: this wasn't a split" → `TextAction(tone = Danger)`.
- [x] Loading state on Split home.
- [x] Commit.

## Phase D: Insights

### Task D1: Insights

**File:** `ui/screens/insights/InsightsScreen.kt`, `ui/components/Charts.kt`. Evidence: `15-insights-1/2`,
`dark-insights`, `big-insights`.

- [x] Trend line uses `sameSpanBefore`/`changePercent` (D3): "So far 81% less than 1–6 Sep (₹…)"; nothing when spend is ₹0.
- [x] `InsightCard` uses `CardShape` and theme tokens instead of its own `Card` setup.
- [x] Bar chart at large font: when a value label is wider than its bar, draw no value labels on the bars; the
  sentence under the chart already states the current figure. Month labels `maxLines = 1`, `softWrap = false`, font
  scale capped at 1.3× (same helper as H1).
- [x] Empty hints → `EmptyState` (small variant) so they match other screens.
- [x] Commit.

## Phase E: Settings

### Task E1: Settings

**File:** `ui/screens/settings/SettingsScreen.kt`. Evidence: `16-settings-1..6`, `dark-settings*`, `big-settings*`.

- [x] Raw `Button`/`OutlinedButton` (8 places) → `PrimaryButton(fill=false)`/`SecondaryButton`: Grant SMS permission,
  Find duplicates, Remove n, Save name, Generate, What is sent?, Save key, Save new key.
- [x] `ActionRow` label "SMS log" with subtitle "Every message scanned and what happened to it".
- [x] AI section body text uses `onSurfaceVariant` like every other card's description.
- [x] Copy: hyphens → en dashes/full stops; "No duplicates found."; AI error "Couldn't get a summary. {message}";
  clear-all confirm "Delete everything" in the error colour (`FinDialog(danger = true)`).
- [x] Commit.

## Phase F: Money tools

### Task F1: Tools list, Ask, Subscriptions, Bills, Cards, Goals, Tax, Net worth

**Files:** `ui/screens/tools/ToolsScreen.kt`, `ask/AskScreen.kt`, `recurring/RecurringScreen.kt`, `bills/BillsScreen.kt`,
`cards/CardsScreen.kt`, `goals/GoalsScreen.kt`, `tax/TaxScreen.kt`, `networth/NetWorthScreen.kt`. Evidence: `20*`,
`21-ask-empty`, `22..27-*`, `30..37-*`.

- [x] Empty states carry their action: Bills "Add a bill", Cards "Add a card", Goals "Add a goal" (`SecondaryButton`;
  the FAB stays for the non-empty list). Same vertical position (top of content, 32dp down) on every tools screen.
- [x] Ask: input uses the `SearchField` style with a send `IconButton` tinted accent when enabled; suggestion `ChipRow`
  bleeds to the screen edge with a gutter inset; an intro `EmptyState` ("Ask about your spending, e.g. …") until the first question.
- [x] Bills dialog: "Repeats" chips in a bleeding `ChipRow` (no clipped "Half-yearl…"); dialogs → `FinDialog`.
- [x] Cards: label the progress bar ("Day 5 of 31 in this cycle") (U10).
- [x] Goals: date via the date picker (U6), "Goal name" / "Target date (optional)"; card actions `TextAction(alignStart)`.
- [x] Net worth: "owe ₹0" → "You owe ₹0"; caps labels unchanged.
- [x] Commit.

## Phase G: Secondary screens

### Task G1: Review, SMS log, Import, Budgets, drill-down, transaction editor, onboarding

**Files:** `review/ReviewScreen.kt`, `smslog/SmsLogScreen.kt`, `importer/ImportScreen.kt`, `budgets/BudgetsScreen.kt`,
`drilldown/DrillDownScreen.kt`, `edit/EditTransactionScreen.kt`, `onboarding/OnboardingScreen.kt`. Evidence: `10-smslog`,
`12-review`, `13-import*`, `42-budgets`, `43-budget-dialog`, `40-edit-1..3`, `01-onboarding`.

- [x] Review: `reasonLabel` (U5, covered by `CopyTest`); "Looks like ₹500 going out"; "Enter details" →
  `PrimaryButton(fill=false)`, "Dismiss" → `TextAction`.
- [x] SMS log: `reasonLabel`; status words "Saved / To review / Skipped / Duplicate"; one selected chip (U9: "This
  import" becomes a filter that replaces "All", not a second selected chip); rows → list rows with inset hairlines
  instead of a bordered card each.
- [x] Drill-down hero through A1 (no forced ".00").
- [x] Editor: "Tax: not a deduction" moves below the form as an `ActionRow` "Tax section · Not a deduction"; chip rows
  bleed to the screen edge; "Category"/"Counts as" → `CapsLabel`; the date control becomes an outlined field of the
  same shape as the others (read-only, calendar icon, opens the picker).
- [x] Budgets: unchanged layout; dialog → `FinDialog`.
- [x] Onboarding: "Allow SMS access & import" → "Allow SMS and import"; skip link copy "Skip – I'll add them myself".
- [x] Commit.

## Phase H: widget, quick-add, navigation, cleanup

### Task H1: Navigation bar at large font (D4) 🔴

**File:** `ui/nav/AppNav.kt:139-170`. Evidence: `big-*`.

- [x] Remove the fixed `height(64.dp)` (use `heightIn(min = 64.dp)`), labels `maxLines = 1`, `softWrap = false`, font
  size capped at `labelSmall × min(fontScale, 1.3) / fontScale`. Badge offset unchanged.
- [x] Verify at 200%: no broken words, icons not clipped, `LocalBottomBarPadding` still clears the last row.
- [x] Commit.

### Task H2: Widget and quick-add

**Files:** `ui/widget/FinTrackWidget.kt`, `QuickAddActivity.kt`, `res/xml/*widget*info*.xml`. Evidence: `48-widget`, `49-quickadd`.

- [x] Widget colours from a Glance `ColorProviders` pair (light/dark) using the Buro accent; actions become pill
  buttons ≥48dp tall; content vertically centred in the 3×2 cell; `previewLayout` so the picker shows the widget,
  not the green app icon.
- [x] Quick-add: category `ChipRow` bleeds to the dialog edge; dialog → `FinDialog`.
- [x] Commit.

### Task H3: Cleanup

- [x] Replace remaining ad-hoc paddings/`spacedBy`/`Spacer` values with `Space.*` (values off the 4dp grid — 6, 10,
  14, 18dp — move to the nearest token; check the screenshot where that shifts anything).
- [x] Replace `RoundedCornerShape(22.dp)` literals with `CardShape`; delete `PrimaryPill`/`SecondaryPill` aliases.
- [x] `grep` checks, all must be empty: raw `Button(`/`OutlinedButton(`/`AssistChip(` in `screens/`; `Color(0x` outside
  `theme/` and the widget colour provider; `"%,d"`; the words `txn`, `Error:`.
- [x] Commit.

---

## Phase V: verification (superpowers:verification-before-completion)

- [x] `bash build.sh testDebugUnitTest`: all green, count = 510 + new tests. Paste the count here.
- [x] `bash build.sh assembleDebug -PappIdSuffix=.polish`, install, fresh emulator pass over every screen in light,
  dark and 200% font.
- [x] Save after screenshots to `docs/ui-polish-2/after/` with the same names as `before/`, and a side-by-side
  `docs/ui-polish-2/pairs/<name>.jpg` for each changed screen.
- [x] Restore the emulator: font scale 1.0, night mode off, remove the `.polish` widget from the home screen.
- [x] Final report in `docs/ui-polish-2/REPORT.md`: what changed per screen with pair links, anything not done and why.
- [x] superpowers:finishing-a-development-branch (no push/merge unless asked).
