# UI polish 2: report

**Branch:** `ui-polish` (from `main` @ ef89566), 22 commits, not pushed. **Date:** 6 Oct 2026.
**Plan:** [`docs/PLAN-ui-polish-2.md`](../PLAN-ui-polish-2.md) · **Audit:** [`AUDIT.md`](AUDIT.md) ·
**Before/after pairs:** [`pairs/`](pairs/) (62 pairs; `dark-*` dark mode, `big-*` 200% font).

**Tests:** `bash build.sh testDebugUnitTest` → **531 tests, 0 failures** (baseline 510, plus 21 new).
**Device:** emulator `fintest`, 393dp wide, as `com.pft.financetracker.debug.polish`, light, dark and 200% font.

Decisions applied (approved): D1 paise only when present · D2 new money colours with dark variants · D3 part periods
compared with the same days of the previous one · D4 nav labels one line, capped at 1.3×.

---

## Everywhere

- **One money format.** `Rupees.format` (Indian grouping, sign before the ₹, paise only when present) behind `money()`,
  Ask, the AI prompts, bill reminders, the widget and the split maths. ₹105,000 is now ₹1,05,000, "1.1L" is gone,
  "₹-5,000" is now "-₹5,000". Estimates (a daily average, a projection, a goal's monthly amount) round to the rupee.
  Pairs: `35-goals-list`, `51-split-detail`, `05-home-lastmonth-1`.
- **Colour and contrast.** Money colours are theme tokens with a dark set. Light income green is 4.7:1 (was 3.0 on
  panels), dark expense red is 7.4:1 (was 2.6), muted text 4.6:1 on panels. ₹0 is never green or red. Pairs: `dark-*`.
- **Buttons.** One component per role: `PrimaryButton`, `SecondaryButton`, `TextAction`, icon buttons. Eight raw M3
  `Button`/`OutlinedButton`s and three `AssistChip`s are gone. Dialogs use the white card surface.
- **Touch targets.** 48dp for `ActionRow`, Home's amount and budget rows, chips (touch area), the widget's actions.
- **Loading.** A spinner until the database answers, instead of flashing "No transactions yet" and ₹0.
- **Large font.** Nav labels stay on one line, the Split header and balances, the donut legend, chart labels and
  budget rows all fit at 200%. Pairs: `big-*`.
- **Copy.** Plain reasons instead of codes ("A one-time code", "Couldn't tell if money came in or went out"),
  "payment" instead of "txn", verb-first buttons, en dashes for hyphens, a scan result that says "12 added · 1 to
  review · 4 skipped" or "No new transactions".
- **Spacing tokens.** `Space.xs…xxl`, `CardShape`, `ButtonHeight`; on-grid literals moved onto them.

## Screen by screen

| Screen | What changed | Pairs |
|---|---|---|
| Home | Scan snackbar goes away by itself (it waited forever); "about ₹133 a day · on track for ₹4,133"; "20% less than 1–6 Sep" instead of "↘ 100% vs previous" at ₹0; "Nothing spent yet"; 48dp sum rows; "1 payment"; suggestion actions as one primary and two text actions | `02-home-top`, `05-*`, `dark-home*`, `big-home*` |
| Activity | Amount search without commas ("1299" finds ₹1,299; "1mg" is a name); hairlines between rows; empty states with an action; SMS-log icon | `11-*` |
| Split | Header halves and balances fit at large font; ₹2,400 not ₹2,400.00; "Owes ₹800" no longer blue; one button and chip style; "Split equally… any odd paisa goes to whoever paid" | `52-split-home`, `51-split-detail`, `44-*`, `big-split` |
| Insights | Trend and category cards compare like with like; chart values in whole rupees that fit at 200% | `15-*`, `big-insights` |
| Settings | Button roles; "SMS log" row with a subtitle; plainer AI and duplicate copy; "Delete everything" in red | `16-*` |
| Money tools | Empty states carry their action; Ask input as a pill with an intro; Bills' repeat chips wrap; card cycle bar labelled "Day 5 of 31"; goal and first-EMI dates use the date picker | `20b`, `21`–`37` |
| Review / SMS log | Plain reasons, "looks like ₹500 going out", list rows instead of a card per message, "Skipped / To review" | `10-smslog`, `12-review` |
| Editor | Tax section is a row below the form; date is a field like the others (works with TalkBack); caps labels | `40-*` |
| Onboarding | "Allow SMS and import", "Skip – I'll add them myself" | `01-onboarding` |
| Widget / quick-add | Buro blue with a dark variant (was the old green), 48dp pill actions, centred content, whole rupees, a real preview in the widget picker; quick-add chips wrap | `48-widget`, `49-quickadd` |

## Bugs fixed on the way (each with a failing test first)

- Snackbar that never dismissed; a failed scan reported as "Scanned 0 SMS" (`CopyTest.scanLines`).
- "100%"/"81% down" from comparing a part month with a whole one (`PeriodCompareTest`).
- "₹-5,000" in Ask and AI prompts (`AskEngineTest.aNegativeFigurePutsTheSignBeforeTheRupee`).
- Amount search matching digits inside names (`CopyTest.amountSearchOnlyForQueriesThatLookLikeAnAmount`).
- Widget figure overflowing with paise (`WidgetSnapshotTest.theWidgetShowsWholeRupees…`).
- **Found on the device pass:** a fresh install with no splits or budgets would spin forever, because a `StateFlow`
  dropped the empty answer as "equal" to the not-loaded marker (`LoadedTest.anEmptyAnswerReplacesTheMarkerInAStateFlow`).
- New date fields could not be opened with TalkBack or a keyboard (verified on device: a clickable "Date: 06 Oct
  2026" button node; no Compose UI-test dependency to unit-test it).

## Not done, and why

- **Off-grid spacing (2/6/10/14/18dp)** left as it is; only on-grid values moved to tokens. Moving them would shift
  many text gaps with no reported problem.
- **"Reduce spending" suggestions** still compare whole months (a separate engine from the category cards).
- **Insights' "not enough data" hints** stay plain text; there is no small `EmptyState` variant.
- **Deferred minors from the final review:** the hero can show "-₹500" with "Nothing spent yet" when refunds exceed
  spend; a 0% change reads "0% more"; two minus glyphs (ASCII "-₹50" vs "− ₹500" in sums); Activity's empty message
  when only hidden reversals remain; missing SMS permission reads as "No new transactions"; static sample text in the
  widget preview; a duplicate import in `Charts.kt`.
- **No onboarding "before" at the same data** and no `14-split-empty` after (the split exists in the after data).
