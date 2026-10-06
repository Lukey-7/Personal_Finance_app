# UI polish 2: audit

**Build:** `main` @ ef89566 (v1.3.0), installed beside the shared app as `com.pft.financetracker.debug.polish`.
**Device:** emulator `fintest`, 1080×2340 at density 440 (393dp wide). Seeded 17-SMS inbox, then a bill (Rent ₹25,000),
a card (ICICI Amazon Pay ••9012), a goal (Goa trip ₹1,05,000), a Food budget (₹300) and one split (Dinner ₹2,400, 3 people).
**Date:** 6 Oct 2026. Light mode, dark mode, and the largest system font (200%).

Screenshots are in [`before/`](before/) (scaled to 540px wide). `big-*` is the 200% font, `dark-*` is dark mode.
The plan that fixes these is [`docs/PLAN-ui-polish-2.md`](../PLAN-ui-polish-2.md).

Severity: 🔴 visible bug or fails WCAG AA · 🟡 inconsistent or confusing · 🟢 minor.

---

## 1. Design critique (against the Buro language)

**First impression.** The skeleton is right: warm page, white hairline cards, one blue, big figures. Onboarding, Money
tools and the Settings card structure are close to the reference. What breaks it is accumulated drift: four button
styles for the same role, two money formats, chip rows that clip in some places and scroll edge to edge in others, and
a few states nobody designed (zero spend, a scan that found nothing, the first frame before the database loads).

### Usability

| # | Finding | Sev | Screens |
|---|---|---|---|
| U1 | The SMS-scan snackbar never goes away. It has an action ("View log"), so Material 3 makes it `Indefinite`; it sits over the middle of Home until tapped, even for "Scanned 0 SMS: 0 added, 0 to review, 0 duplicates, 0 ignored". | 🔴 | 02, 03, 04, 05-* |
| U2 | Zero-spend periods read as nonsense: "₹0 · ↘ 100% vs previous · ≈₹0/day · on track for ₹0". Insights says "Down 100% vs previous" (and "Down 81%" six days into a month) because a part-month is compared with a full one. | 🔴 | 02, 15-1, dark-insights |
| U3 | Every screen flashes its empty state ("No transactions yet", "No splits yet", ₹0 heroes) for the first frames after launch, because each list `StateFlow` starts as `emptyList()`. There is no loading state anywhere. | 🟡 | all lists |
| U4 | The + button covers the right-hand column: the last amount in a row and the "Set budgets"/"See all" actions sit under it until you scroll. | 🟡 | 02, 03, 05-1..4, 11-2 |
| U5 | Raw reason codes are shown to people: "Ignored · future", "Ignored · otp", "Needs review · type ambiguous", "Reason: statement balance mismatch · guessed ₹120 · DEBIT". | 🟡 | 10, 12 |
| U6 | Goal date is typed as `YYYY-MM-DD` text. Every other date in the app uses the date picker. | 🟡 | 34 |
| U7 | Activity's amount search only matches the formatted string: "1299" does not find "₹1,299". | 🟡 | 11-* |
| U8 | Activity has three unlabeled header icons (inbox, upload, chat bubble); the chat bubble means "SMS log". | 🟢 | 11-1 |
| U9 | SMS log shows two "selected" chips at once ("This import" and "All (17)") and an empty "This import" list after a scan that found nothing. | 🟢 | 10 |
| U10 | The Credit cards row shows a progress bar with no label (it is the cycle's elapsed days, but reads like spend). | 🟢 | 33 |

### Visual hierarchy

- **What draws the eye first:** the hero figure on Home and Split, which is correct. On the editor it is a floating blue
  "Tax: not a deduction" link above the form (40-1), which is not.
- **Emphasis:** blue is used for non-tappable text in several places ("Owes ₹800.00" and "₹1,600 open" on splits, the
  "↘ 81% vs previous" line on Home), so blue no longer reliably means "tap me".
- **Zero values in money colours:** "₹0" painted green or red (Split header, Savings ₹0, "SBI ••5678 +₹45,000 ₹0" in red).

### Consistency

| Element | Issue | Recommendation |
|---|---|---|
| Money | `money()` and `InsightsEngine.fmt()` use Western grouping (₹105,000) and `fmt()` compacts lakhs ("1.1L"); `SplitSolver.rupees()` uses en-IN grouping; split screens always print paise ("₹2,400.00", "₹420.00" on drill-down) while everything else rounds. | One formatter, Indian grouping, paise shown only when non-zero (decision D1). |
| Buttons | Primary actions come in four shapes: `PrimaryPill` (56dp), raw M3 `Button` (40dp, Settings, Review, New split "Add"), `OutlinedButton` (Find duplicates, Photo/Gallery, Settle, What is sent?), `SecondaryPill`. | Four roles, one component each (section 4). |
| Inline actions | Card-level actions are `TextButton`s with default 12dp padding, so "Add money / Edit", "Yes, split it / Not a split / Details" sit at odd insets. | `TextAction` with the card's text edge. |
| Chips | `PillChip` everywhere except New split, which uses `AssistChip` (8dp corners, different height). Chip rows scroll edge to edge on Home/Activity, but are clipped at the card or dialog edge in the editor, bill dialog, quick-add and New split ("Bills & U", "Cash wit", "Half-yearl"). | `PillChip` only; every chip row is a `ChipRow` that bleeds to its container edge. |
| Section labels inside forms | "Category"/"Counts as" in titleSmall (editor), "REPEATS" in caps (bill dialog), "Quick add" in bodyLarge (New split). | `CapsLabel` for every in-form label. |
| Lists | Home's recent list has inset hairlines between rows; Activity has none inside a day; SMS log wraps every row in its own bordered card. | One list-row rhythm: inset hairlines. |
| Empty states | Split's has a primary action; Bills, Cards, Goals and Subscriptions have none (only the FAB). Vertical position differs per screen. | `EmptyState` always states what, why, and the next step; action when one exists. |
| Dialog surfaces | Dialogs use the grey SoftPanel container, not the white card. | White surface, 28dp corners. |
| Widget | Accent is a dark green (`0xFF0E4F3E`) left from the old brand; no dark variant. | Buro blue, dark variant. |
| Text fields | Ask's input is an outlined rectangle; search fields are filled pills. | Ask input uses the search-field style. |

### What works well

- The card + hairline language, the 24dp gutter and the caps labels over figures are applied almost everywhere.
- Icon buttons all have TalkBack labels; switches are whole-row toggles with `Role.Switch`.
- The Money tools list and the Settings cards scan well; destructive "Clear all data" is red and set apart.

---

## 2. Accessibility (WCAG 2.1 AA)

### Contrast (measured)

| Element | Fg | Bg | Ratio | Need | Pass |
|---|---|---|---|---|---|
| Income green, light | #00A62D | #FFFFFF | 3.23 | 4.5 | ❌ |
| Income green on SoftPanel (Home's maths panel) | #00A62D | #F4F5F6 | 2.96 | 4.5 | ❌ |
| Expense red, light | #B50000 | #FFFFFF | 7.08 | 4.5 | ✅ |
| Expense red, **dark** ("Over budget by ₹500", every debit amount) | #B50000 | #121314 | 2.63 | 4.5 | ❌ |
| Neutral grey, dark (transfer amounts) | #70757D | #121314 | 4.01 | 4.5 | ❌ |
| Muted text on SoftPanel, light | #70757D | #F4F5F6 | 4.25 | 4.5 | ❌ (just) |
| Muted text, light | #70757D | #FFFFFF | 4.64 | 4.5 | ✅ |
| Accent, light / dark primary | #0000FF / #9DA8FF | white / #121314 | 8.59 / 8.40 | 4.5 | ✅ |

Cause: `Income`, `Expense` and `Neutral` are fixed `Color`s outside the colour scheme, so dark mode never gets its own.
Proposed values (measured): light Income **#007F22** (4.74 on SoftPanel), light Muted **#6A6F77**; dark Income
**#4CD07D** (9.4), dark Expense **#FF7B72** (7.4), dark Neutral **#9AA0A8** (7.1).

### Touch targets (48dp)

| Element | Size | |
|---|---|---|
| Home maths rows (Income / Gross spend / Refunds), budget rows, Not-counted rows | ~20dp tall, clickable | ❌ |
| `ActionRow` (Settings, Import, Net worth) | 40dp (10dp padding + 20dp icon) | ❌ |
| Widget "+ Expense" / "+ Cash" | ~27dp | ❌ |
| Hero figure on Home (drills into spend) | text height only | 🟡 |
| `PillChip` | 38dp tall | 🟡 (Material minimum is 48dp touch; visual can stay 38dp with a larger touch area) |
| Icon buttons, FAB, pills | ≥48dp | ✅ |

### Large font (200%)

| Screen | Break |
|---|---|
| Nav bar (all tabs) | Labels break mid-word ("Activ/ity", "Hom", "Setti/ngs"); icons clipped by the fixed 64dp bar. 🔴 big-* |
| Split header | "OWED TO YOUYOU OWE" collide; "₹0" drops to a second line. 🔴 big-split |
| Split balances | "Person 2owes you" with no gap; amount wraps under it. 🔴 big-split |
| Home donut legend | "Food & …", "100 / %" wraps. 🟡 big-home2 |
| Insights bars | Value labels become "₹…", months "M…", "A…". 🟡 big-insights |
| Transaction rows | Subtitles truncate early ("Food & Dining ·…"); acceptable, amounts stay intact. 🟢 |

### Screen reader / state

- Category and budget state rely on colour alone in two places: the over-budget bar (red, but the "Over budget by" text
  is there, so it passes) and Split's green/red "owes you"/"you owe" (text carries it; passes).
- The Home "↘ 81% vs previous" arrow is a text glyph; TalkBack reads "south east arrow". Use words.
- The donut centre "1 txns" is read as "1 t x n s".

---

## 3. UX copy

One verb style: **sentence case, verb first, no trailing punctuation on buttons**. No jargon: no "txn", no reason codes,
no "paise" outside the split maths.

| Where | Now | Proposed |
|---|---|---|
| Scan snackbar | "Scanned 17 SMS: 12 added, 1 to review, 1 duplicates, 3 ignored" | "12 added · 1 to review · 4 skipped" (nothing new: "No new transactions") |
| Donut centre / Top merchants | "1 txns", "1 txn · Food & Dining" | "1 payment", "1 payment · Food & Dining" |
| Review reason | "Reason: type ambiguous · guessed ₹500" | "Couldn't tell if money came in or went out · looks like ₹500" |
| SMS log outcome | "Ignored · future", "Ignored · otp", "Ignored · promo" | "Skipped · a future payment", "Skipped · a one-time code", "Skipped · an offer" |
| AI error | "Error: {message}" | "Couldn't get a summary. {message}" |
| Duplicates empty | "No duplicates found - every transaction looks distinct." | "No duplicates found." |
| Clear-all confirm | "Delete all" (TextButton, not red) | "Delete everything" in the error colour |
| Split mode hint | "Total ÷ 3. Any leftover paise go to the payer." | "Split equally. Any odd paisa goes to whoever paid." |
| Goal dialog | "What for", "By (YYYY-MM-DD), blank for none" | "Goal name", date picker "Target date (optional)" |
| Settings action | "SMS log: every message scanned and what happened to it" | "SMS log" with the explanation as the row's subtitle |
| Hyphen as dash | "Looks for the same payment stored twice - usually…" (several) | en dash or a full stop |
| Empty states | "No cards yet. Add one with its statement and due days." | "No cards yet. Add one to track spend per billing cycle." + **Add card** |

---

## 4. Design-system audit

**Token coverage**

| Category | Defined | Ad hoc in screens |
|---|---|---|
| Colour | full M3 scheme + `Income`/`Expense`/`Neutral` (not theme-aware) | widget: 4 hex literals; InsightCard builds its own `Card` |
| Spacing | `Gutter` 24, `CardPadding` 24 | padding literals 4/6/8/10/12/14/16/18/20/24/32dp; `spacedBy` 2/4/6/8/10/12/16dp; Spacers 4/6/8/12/20/24dp |
| Shape | M3 shapes 10/14/20/24/28 | `RoundedCornerShape` literals 10/12/14/20/22/24/28dp; cards use 22dp, which is not a token |
| Type | full scale + `LabelCaps` | consistent (no raw `fontSize` in screens) ✅ |

**Components**

| Role | Today | Proposal |
|---|---|---|
| Primary | `PrimaryPill` 56dp, raw `Button` 40dp | `PrimaryButton` (pill, 52dp, fill or wrap width) |
| Secondary | `SecondaryPill`, `OutlinedButton` | `SecondaryButton` (pill, 52dp, hairline outline, accent text) |
| Text | `TextButton` with ad-hoc paddings | `TextAction` (48dp touch, zero start inset option) |
| Destructive | red text on `TextButton`/`ActionRow` | `tone = Danger` on `TextAction`/`ActionRow` |
| Icon | `IconButton` | unchanged ✅ |
| Chip | `PillChip`, `AssistChip` | `PillChip` only, 48dp touch |
| Row | `ActionRow` 40dp, `MathRow`/`LineRow` private to Home | `ActionRow` 48dp min; `AmountRow` shared |

**Spacing tokens proposed:** `Space.xs 4`, `sm 8`, `md 12`, `lg 16`, `xl 24`, `xxl 32` and `CardRadius 22`. Screens
move onto these; the values stay the same where they already are on the 4dp grid.
