# UI polish: icons and spacing

Status: **implemented and verified on the emulator, 2026-09-23. Not yet committed.**

Source: a screen-by-screen audit of the debug build on the `fintest` emulator (1080×2340, density 440, so
393dp wide), 2026-09-23, checked against the Buro design system in
`stitch_buro_fintech_app/boro_design_system/DESIGN.md`. Before and after screenshots are in the session
scratchpad (`audit/`, `after/`).

The Buro rules this plan applies:

- 24dp screen gutter, 8dp grid: 16dp between items inside a card, 32dp between sections
- 24dp padding inside cards, 22dp card radius
- Rows: 40dp soft circle for the icon, title and subtitle stacked, value right-aligned, hairline inset
  so it lines up with the text
- Icons are minimal outline glyphs. Filled only for the selected nav item.

No calculation or data change is part of this work. The unit tests (89) pass unchanged.

---

## A. Global layout (affects every tab)

- [x] **A1 bug: content was cut off above the nav bar.** The NavHost now fills the window, and tab
      content scrolls underneath the floating pill (a fade behind the pill keeps it legible). Each list
      pads its end by `LocalBottomBarPadding`, the pill's measured height. Two causes: the outer
      Scaffold clipped the NavHost above the bar, and each tab's own Scaffold added the system nav inset
      a second time. Verified: the last row of Home, Activity and Settings scrolls fully clear.
- [x] **A2: the + button covered numbers.** Lists now reserve `FabClearance` (88dp) at their end, so the
      last row can always be scrolled clear. There is one style everywhere (`AddFab` / `AddFabExtended`,
      solid accent). *Still true:* while a card is under the button's position, the button sits over it
      until you scroll. That is inherent to a floating button.
- [x] **A3: gutters.** `Gutter` is 24dp, `CardPadding` 24dp, 16dp inside cards, 16dp between cards.
- [x] **A4: chip rows.** A shared `ChipRow` scrolls edge to edge, with the first pill on the content edge
      and the last one scrolling fully in. *Changed from the plan:* the pill edge lines up with the
      content, not the chip's text, as in the reference. For that edge to be visible when the first
      chip is unselected, unselected chips now carry a hairline outline.
- [x] **A5: dividers.** `Hairline` takes an end inset; list dividers stop at the gutter and start at the
      row text.
- [x] **A6: title-to-content gap.** Every tab starts its content 8dp below the top bar.

## B. Icons

- [x] **B1: outline icons throughout.** The selected tab uses the filled glyph. The Activity tab icon is
      now `ReceiptLong` (the old `List` had no outline/filled pair).
- [x] **B2: category icons.** `categoryIcon()` in `ui/components/Icons.kt`, used in Budgets, Top merchants,
      the donut legend, and the category chips on Activity, Edit and New split.
- [x] **B3: header icon buttons.** Inbox with badge (review), Sms (SMS log), Savings (Budgets).
- [x] **B4: Settings section icons.**
- [x] **B5: SMS log status icons**, each in a tinted circle, with the status word kept in the row.
- [x] **B6: empty states** for Split, Review, Activity (no results), SMS log and drill-down.

## C. Screen by screen

### New split
- [x] **C1 bug: the "5 people" chip squashed into a vertical strip.** People, suggestions and quick-add
      chips are FlowRows. Verified: "5 people" wraps to its own line.
- [x] C2: the name field and the Add button now line up. Total and date are fields of the same shape,
      and the date opens the picker. They sit side by side (1 : 1.4) where the card is at least 290dp
      wide, and stack on smaller phones.
- [x] C3: "Tax", "Tip" and "Discount" with a ₹ prefix, in an `AdaptiveRow`: one line where each field gets
      90dp, stacked otherwise.
- [x] C4: "2 · Items" with the subtitle "Optional · needed to split by item".

### Home
- [x] C5: legend below the donut, as full-width rows (icon · name · % · amount) with fixed number
      columns.
- [x] C6: Top merchants rows are 64dp with category icons. Short merchant names show in capitals
      ("ATM"); this is display only, and the stored name is unchanged.
- [x] C7: "1 SMS needs review" / "n SMS need review".

### Activity
- [x] C8: no per-row date under day headers; Home's Recent list keeps it.
- [x] C9: one-word flow tags ("Refund", "Transfer", …), so the subtitle keeps its room.
- [x] C10: one `SearchField` style, used on Activity and SMS log.

### Insights
- [x] C11: "Apr", "May", … with the year only where it changes; empty periods are a hairline baseline.
- [x] C12: the "Current: ₹…" line is shown only when there is a previous period to compare against.

### Settings
- [x] C13: SMS log, export, remove key and clear data are `ActionRow`s aligned with the card text.
- [x] C14: the key status is a secondary-colour line with a lock icon.
- [x] C15: *Changed from the plan:* "Clear all data" is a red action row rather than an outlined button,
      consistent with the other rows. The confirmation dialog is unchanged.
- [x] C16: "Save name" appears only when the name has been edited.

### Budgets
- [x] C17: one card, one row per category: icon, name, amount, "No limit · tap to set one" or a
      progress bar when a limit is set.
- [x] C18: **left as is**, per your decision. Transfers and Investments stay listed.

### Edit transaction
- [x] **C19 bug: title flashed "Add transaction".** The title now comes from the route id. *Verified in
      code only:* the flash lasted a single frame, too short for a screenshot to catch before or after.
- [x] C20: both chip rows now start on the same edge.

## D. Screens not in the original audit

- Split detail and the Split list with a split in it: checked after creating a real ₹2,500 five-way
  split. Maths correct (₹500 each; only ₹500 counts as my spend). The test split and its transaction
  were deleted afterwards, and the data is back to net spend ₹4,218.
- Drill-down (Home → Gross spend): checked.
- Onboarding: only its two icons changed (outline). **Not visually checked:** it appears only on first
  run and would need the emulator's data cleared.

## F. Found during verification, and fixed

Things the before/after pass, the large-font check and the narrow-width check turned up:

- **Home at font scale 1.3: amounts wrapped one digit per line** ("₹15,5" / "0"). This bug existed
  before this work. The income/spend/transfer rows had no weight on their labels, so a long label
  took the whole width. The label now flexes and the figure never wraps.
- **Settings: both switches were unlabelled for TalkBack.** This also existed before this work. Each
  switch row is now one toggle, so the label is read with the state and the whole row can be tapped.
- **"New split" + button was unlabelled for TalkBack.** It now has a content description.
- **Split-mode selector clipped "Custom amounts"** (the enum label is two words) at every width. It is
  now a wrapping row of pills with the full labels.
- **Settings buttons clipped or wrapped** at the 24dp gutter ("Rescan last 12 months", "Generate
  summary"). The scan actions are rows now; Generate / What is sent? are an equal-width pair.
- **New split at 360dp:** "Galler y" wrapped, "23 Sep 2(" and "Discou" clipped. Fixed by tighter button
  padding and the adaptive rows above.
- "Me  · paid the bill" had a double space.
- `docs/RUNNING.md` said 90 tests; there are 89 `@Test` methods, and all of them ran.

## E. Verification

- [x] `build.cmd testDebugUnitTest`: 89 tests, 0 failures
- [x] Every screen re-screenshotted after the change and checked against A1–C20
- [x] Accessibility scan (`uiautomator dump`, clickable nodes with no text or description) on Home,
      Activity, Insights, Settings, Split, Split detail, Edit, SMS log and Review: none left. The one
      remaining hit on Activity was the chip cut off at the screen edge; scrolled into view, it reads
      "Bills & Utilities".
- [x] Font scale 1.3: Home, Activity and Settings checked, nothing clipped after the fixes in F
- [x] Narrow width: tested at 360dp (945×2048 on this 440dpi emulator). The plan's 720×1560 would have
      been 274dp, narrower than any real phone. Home, Split and New split checked.
- [x] Font scale and screen size reset (1.0, 1080×2340)
