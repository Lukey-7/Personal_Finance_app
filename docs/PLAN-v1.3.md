# v1.3 Plan: Everyday money, beyond the SMS

**Status:** draft, waiting for go-ahead (5 Oct 2026). Nothing is built yet.
**Source:** market research on 5 Oct 2026 (Axio, Fold, INDmoney, CRED, PennyWise, Bluecoins, YNAB, Monarch, Copilot, Cleo; Play Store, Trustpilot and comparison-blog reviews). The user picked features 1 to 11 from the ranked shortlist.
**Executor:** `superpowers:executing-plans`, inline in one session with no subagents, plus one whole-branch review at the end. The progress ledger lives in `.superpowers/sdd/PLAN-v1.3/progress.md`.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done and verified

---

## 1. Why these eleven

The research gave five reasons people stop using money apps:

- SMS parsing breaks silently. Axio has missed transactions since Aug 2026, and TRAI is changing SMS templates.
- Subscriptions stay hidden. People underestimate their recurring spend by two to three times.
- Manual entry is tiring, and cash goes untracked.
- Refunds and failed-payment reversals clutter the numbers.
- "Lose the phone, lose the data" for apps with no account.

Fold and INDmoney win on net worth, bills and cards, but they need cloud sync and Account Aggregator, which needs a regulator licence. PennyWise is the closest privacy-first rival: on-device AI, subscriptions, and a Pro paywall on imports. v1.3 closes these gaps **without adding an account, a server or analytics.**

## 2. Invariants (unchanged from v1.0 to v1.2)

1. **No new network endpoint.** The only one stays `api.openai.com`, and it stays optional. Every new dependency is audited the same way ML Kit OCR was: no `clearcut` / `phenotype` / `datatransport` / `firelog` classes in the shipped artifacts, and an APK URL scan after the build. A dependency that fails the audit does not ship (see D7).
2. Money is integer paise. Nothing new counts as spend unless its `Flow` says so.
3. Every automatic change can be undone and never overwrites a row the user edited (same rule as auto-split).
4. One Room migration, **5 → 6**, covered by `MigrationTest`.
5. `FLAG_SECURE`, `allowBackup=false` and SQLCipher stay as they are. The new backup (feature 9) is the only way data leaves the phone, and only to a place the user picks, with a passphrase.

## 3. Decisions (my defaults, to confirm or change)

| # | Question | Default |
|---|---|---|
| D1 | Where to build | A new branch `v1.3-features` from `b18b468`, in a **git worktree** at `../Personal_Finance_app-v1.3`, so parallel chats on the main checkout are not disturbed |
| D2 | New Android permission | `POST_NOTIFICATIONS` (runtime, asked the first time a reminder is switched on). No exact-alarm permission: daily WorkManager checks are enough |
| D3 | New dependencies | `androidx.work:work-runtime-ktx` (reminders), `androidx.glance:glance-appwidget` (widget), `com.google.mlkit:genai-prompt` (on-device AI, alpha). Each is audited against invariant 1 |
| D4 | Backup format | One `.ftbackup` file: a JSON dump of every table, encrypted with AES-256-GCM using a key derived from a passphrase (PBKDF2-HMAC-SHA256, 600k iterations, random salt). Written and read through the system file picker. **No Google Drive** (it would need OAuth and network). Restoring replaces all data, after a typed confirmation |
| D5 | Subscription alerts | A notification 2 days before an expected charge, plus a "price went up" flag when a charge is ≥5% above the last one |
| D6 | Refunds | Pair each refund with its debit. A **failed-payment reversal** (same amount, ≤3 days, same ref or "reversed/failed" wording) is hidden from lists by default, with a toggle. A credit filed as INCOME but clearly a refund is moved to REFUND, with an "Auto" badge and undo |
| D7 | On-device AI | Gemini Nano through the ML Kit GenAI Prompt API on supported phones (Pixel 9/10, Galaxy S24+ and others), used for "Ask FinTrack" and the monthly summary. On other phones and the emulator, a **rule-based Ask** answers fixed question shapes ("how much on food last month", "biggest merchant", "subscriptions"). OpenAI stays as an option. **If the GenAI artifact fails the telemetry audit, it is left out** and only the rule-based Ask ships; you will be told |
| D8 | Net worth | Manual accounts, assets and liabilities, plus automatic bank balances from the "Avl bal" in SMS, plus mutual-fund holdings from a **CAMS/KFintech CAS PDF** (password-protected, read on the phone, password never stored). No stock-price fetching (that would need network); value = units × NAV as printed in the CAS |
| D9 | Cards | The user enters the statement day, due day and an optional reward rate per card (card SMS carry the last 4 digits). RuPay credit card used through UPI is detected and booked as card spend, not bank debit |
| D10 | Tax helper | Tags by keyword rules for 80C (LIC, PPF, ELSS, EPF/VPF, tuition, home-loan principal), 80D (health insurance), 80E, 80G, 24(b), HRA rent. Summary for the Indian financial year (Apr–Mar) with CSV export. Labelled "for your records, not tax advice" |
| D11 | Parser teaching | When you confirm a review item, the app saves a *template*: the message with digits and names masked, plus where the amount, direction and merchant were. A later SMS from the same sender with the same template is parsed by it. Templates are stored encrypted and can be seen and deleted in Settings |
| D12 | Version | `1.3.0`, `versionCode` +1. A release only when you ask (same as earlier releases, with the personal APK and its key, as you chose before) |

---

## 4. Milestones and checklist

The order follows dependencies: shared plumbing first, then features that create data, then features that read all of it (backup and AI come last, so they cover every new table).

### M0. Setup and shared plumbing
- [x] T0.1 Worktree and branch (D1); baseline `bash build.sh testDebugUnitTest` passes
- [x] T0.2 Room v6 migration skeleton: tables `recurring`, `bills`, `refund_links`, `parser_templates`, `accounts`, `holdings`, `cards`, `goals`, `goal_contributions`, `tax_tags`; `MigrationTest` 5→6 RED→GREEN
- [x] T0.3 Notification channel + `ReminderWorker` (daily, WorkManager) + POST_NOTIFICATIONS flow (D2); unit test for the "what is due" calculation
- [x] T0.4 Dependency audit script (`scripts/audit-deps.sh`): scans the resolved artifacts for telemetry packages and the release APK for URLs. Run it on the baseline APK

### M1. Refund and reversal matching (feature 5)
- [x] T1.1 `RefundMatcher` (domain, pure): pairs by ref number → same merchant + amount ≤ debit within 60 days → reversal rule (D6). Tests: full refund, partial refund, failed-UPI reversal, two equal debits with one refund, an INCOME credit that should be a REFUND
- [x] T1.2 Persist links, run after SMS, statement import and rescan; undo; never touch user-edited rows
- [x] T1.3 UI: "Refunded ₹X" / "Reversed" badges, hide-reversals toggle on Transactions, drill-down shows the pair

### M2. Parser resilience (feature 2)
- [x] T2.1 TRAI headers: strip `-T/-S/-P/-G` suffixes in `BankExtractor`; add corpus cases for each bank with suffixes
- [x] T2.2 `TemplateLearner` (D11): mask, learn, match; tests covering learn-then-parse, different sender doesn't match, changed wording doesn't match, amount never taken from a masked account number
- [x] T2.3 Hook into Review confirm + `SmsParser` (template tried before the generic layers only for that sender)
- [x] T2.4 "Possible misses" view in the SMS log: ignored bank-sender messages that contain an amount and a money verb, with one tap to send to Review
- [x] T2.5 Settings → Learned templates (list, delete)

### M3. Subscriptions and UPI AutoPay (feature 1)
- [ ] T3.1 `RecurringDetector` (domain): clusters by merchant and amount band, infers period (weekly, monthly, quarterly, yearly) and next date; boosts on AutoPay / mandate / SI / e-mandate wording. Replaces the old ad-hoc check in `InsightsEngine.suggestions` (the Insight now reads from the detector). Tests: Netflix monthly, yearly Prime, weekly, price hike, irregular grocery (must NOT match), mandate keyword with only one charge
- [ ] T3.2 Store detected items; user can confirm, dismiss or mark cancelled (dismissals survive re-detection)
- [ ] T3.3 "Recurring" screen: monthly total, yearly cost, next charge, price-hike flag; Insights card links to it
- [ ] T3.4 Renewal reminders through T0.3 (D5)

### M4. Bills, EMIs and loans (feature 3)
- [ ] T4.1 `Bill` model: name, amount (fixed or "varies"), due rule (day of month / every N months), optional loan (principal, rate, tenure → EMI schedule). `Amortization` tests against a known loan table
- [ ] T4.2 Auto-mark paid: a matching debit within ±5 days of the due date (by merchant keyword or amount); tests
- [ ] T4.3 Card bills from statement SMS ("total amount due … due by …") create or update the bill automatically
- [ ] T4.4 Bills screen (upcoming, overdue, paid this month) + reminders 3 days and 1 day before

### M5. Credit-card cycles and rewards (feature 8)
- [ ] T5.1 `Card` config (last 4 digits, statement day, due day, reward % optional) (D9); auto-suggest cards from SMS account refs
- [ ] T5.2 `CardCycle` calc: spend per cycle, days to due, estimated reward; tests for month-end and Feb edge cases
- [ ] T5.3 RuPay-credit-on-UPI detection in the parser (card wording + UPI) → card account; corpus cases
- [ ] T5.4 Cards screen; the card bill links to M4

### M6. Home-screen widget and quick add (feature 4)
- [ ] T6.1 Glance widget: month-to-date spend, budget left, next bill; refreshed after each import and daily
- [ ] T6.2 "+ Cash / + Expense" button on the widget and as an app shortcut → a small quick-add sheet (amount, category chips, note), saved as MANUAL
- [ ] T6.3 Widget content respects privacy: an "Hide amounts on widget" setting (default **on** for a public release; shows "••••")

### M7. Goals (feature 10)
- [ ] T7.1 Goal: name, target, date, contributions (manual, or "put this month's savings in"); `monthlyNeeded` calc + tests
- [ ] T7.2 Goals screen + dashboard card

### M8. Tax helper (feature 11)
- [ ] T8.1 `TaxTagger` rules (D10) with tests; manual tag/untag on any transaction
- [ ] T8.2 FY summary screen (per section, with limits such as 80C ₹1.5L shown as progress) + CSV export through the existing exporter

### M9. Net worth (feature 6)
- [ ] T9.1 Accounts, assets and liabilities (manual); loans from M4 appear as liabilities automatically
- [ ] T9.2 Bank balance from the SMS "Avl bal / available balance" → latest balance per account; tests on corpus shapes
- [ ] T9.3 `CasParser` for CAMS and KFintech CAS PDFs (folio, scheme, units, NAV, value), using the existing PDFBox + password flow; synthetic fixtures; tests
- [ ] T9.4 Net-worth screen: total, breakdown, monthly snapshot history (stored once a month)

### M10. Encrypted backup and restore (feature 9)
- [ ] T10.1 `BackupCodec`: every table (v1.3 schema) ↔ JSON, AES-GCM + PBKDF2 (D4); tests: round-trip equality, wrong passphrase fails cleanly, tampered file fails, a backup from schema 6 restores into a fresh DB
- [ ] T10.2 Settings → Backup now / Restore (typed confirmation "RESTORE"), plus an optional monthly reminder to back up
- [ ] T10.3 README security table updated

### M11. On-device AI and Ask FinTrack (feature 7)
- [ ] T11.1 `AiProvider` interface over the existing OpenAI client + new `GeminiNanoProvider`; availability check; the provider picker in Settings
- [ ] T11.2 Audit `genai-prompt` (D7) and record the result in the README "phones home?" section, or drop it
- [ ] T11.3 `AskEngine` (rule-based, works everywhere): parses question shapes into queries over the summaries; tests for each shape
- [ ] T11.4 Ask screen: rule answer first; on capable phones, Nano rephrases and handles open questions from an aggregated, local-only context
- [ ] T11.5 Monthly summary uses Nano when available, otherwise OpenAI if a key is set, otherwise a rule-built summary

### M12. Release prep
- [ ] T12.1 Full `testDebugUnitTest`, release build, APK URL scan (invariant 1)
- [ ] T12.2 Emulator smoke test of every new screen; write `docs/DEVICE-TEST-v1.3.md`
- [ ] T12.3 Final whole-branch review (fresh reviewer), fix pass
- [ ] T12.4 README features + CHANGELOG 1.3.0 + version bump. No tag or GitHub release until you say so

## 5. Testing

- Every domain unit (`RefundMatcher`, `TemplateLearner`, `RecurringDetector`, `Amortization`, `CardCycle`, `TaxTagger`, `CasParser`, `BackupCodec`, `AskEngine`) is pure Kotlin and written test-first.
- Migration and DAOs are tested with Robolectric (`@Config(application = android.app.Application::class)`).
- The emulator checks screens, the widget, notifications and the backup round-trip. **Gemini Nano cannot run on the emulator**: it is tested with a fake provider, and the real-phone check is left to you on a supported device.

## 6. Risks

| Risk | Plan |
|---|---|
| Size: 11 features is about three releases of work in one go | Milestones are independent commits; the ledger lets work resume after a context reset; each milestone ends green |
| GenAI Prompt API is alpha and may carry telemetry | D7: audit; drop it if it fails |
| CAS PDF layouts vary | Synthetic fixtures from the public CAMS/KFintech samples; unknown rows go to a review list, as statements do |
| Shared checkout and emulator with other chats | Worktree (D1); short notice before using the emulator |
| Restore is destructive | Typed confirmation; an automatic backup of current data to app-private cache before restoring, kept until the next app start |
