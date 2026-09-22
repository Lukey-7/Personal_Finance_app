# FinTrack — project plan and status

On-device Android personal finance tracker (Kotlin, Compose, Room on SQLCipher).
This file is the single status board: what is finished, what is in progress, and what is left.

**Last updated:** 2026-09-22 · **Branch:** `v1.1-accuracy-split` · **Target release:** v1.1.0

Legend: `[x]` done and verified · `[~]` in progress · `[ ]` not started

---

## 1. Progress at a glance

| Area | Status | Evidence |
|---|---|---|
| v1.0.0 release | Done | tag `v1.0.0`, signed APK published |
| Phase 0 — Toolchain setup | Done | `build.sh` wraps JDK 17 + Gradle 8.9 |
| Phase 1 — Calculation accuracy | Done | 85 unit tests, 0 failures |
| Phase 2 — Safe DB migration (v1→v2) | Done | `MigrationTest`, verified on a device upgrade |
| Phase 3 — SMS log | Done | `ui/screens/smslog`, `sms_log` table |
| Phase 4 — Summary & dashboard | Done | drill-downs verified on device |
| Phase 5 — Bill split + on-device OCR | Done | device test: ₹990 bill split exact to the paisa |
| Phase 6 — AI mode (unchanged privacy) | Done | payload uses the corrected net figures |
| Device testing (emulator) | Done | `docs/DEVICE-TEST-v1.1.md`; found and fixed 3 defects |
| Post-test fixes | Done | regression tests green |
| Buro UI redesign | Done | all 11 screens migrated; each walked and captured on a debug build |
| Open problems (duplicates, split rounding, APK size) | Done | all three fixed and verified on device |
| Second device pass (airplane, camera, settle, CSV, review, clear) | Done | `docs/DEVICE-TEST-v1.1.md` second pass |
| Phase 7 — Release (docs, tag, APK) | **Partly done** | docs done; tag + APK await your go-ahead |

**Test suite:** 90 tests across 8 files — parser, categoriser, duplicate detection and retrospective
cleanup, insights, split maths, bill parser, v1→v2 migration. Last run 2026-09-22: **90 passed, 0 failed.**

---

## 2. Shipped — v1.0.0 (2026-09-17)

- [x] Layered, bank-agnostic SMS parser with confidence scoring and a manual review queue
- [x] Encrypted on-device storage (Room on SQLCipher, Keystore-backed key)
- [x] Rule-based auto-categorisation with manual override; manual add, edit, delete
- [x] Dashboard, weekly and monthly trends, reduce-spending insights
- [x] Per-category budgets with overspend alerts
- [x] Optional, on-demand OpenAI monthly summary (aggregated totals only)
- [x] CSV export and clear-all-data
- [x] Signed release APK, `v1.0.0` tag, GitHub release published with SHA-256

---

## 3. v1.1.0 — implemented

Full rationale for each item is in [docs/PLAN-v1.1.md](docs/PLAN-v1.1.md).

### 3.1 Calculation accuracy

- [x] **Duplicate SMS (same message, two paths).** The hash is now `sender + normalised body + day`, so the live receiver and a later inbox scan agree.
- [x] **One payment, two SMS.** Reference numbers (`UPI Ref`, `IMPS`, `RRN`, `Txn ID`, `UTR`) are extracted and stored; matching refs, or the same amount within ten minutes from a different sender, merge into one record. The richer record is kept and the other is logged as `DUPLICATE`, never silently dropped.
- [x] **Flow classification.** Every transaction carries an editable flow: `EXPENSE`, `INCOME`, `REFUND`, `TRANSFER`, `INVESTMENT`, `CASH_WITHDRAWAL`, `SETTLEMENT`.
- [x] Credit-card bill payments and self-transfers detected and kept out of spend.
- [x] **Refunds reduce spend** instead of posing as income; they come off the matching category when the merchant matches a recent debit.
- [x] Summary shows gross spend, refunds and net spend, so the arithmetic is visible.
- [x] **Money stored as `Long` paise** end to end; rupees only at the UI edge. Budgets in paise too. CSV still exports rupees with two decimals.
- [x] Parser fixes: promo/login/KYC filters no longer drop real debits, duplicate `spent` keyword scoring removed, weak hints tightened to word boundaries, body dates keep the SMS time of day instead of midnight.
- [x] Engine fixes: numeric sort field on `Insight`, recurring-merchant matching checked against real data.

### 3.2 Safe database migration

- [x] `fallbackToDestructiveMigration()` removed
- [x] `MIGRATION_1_2`: amounts to paise, `flow` and `refNumber` columns, `sms_log` table, split tables
- [x] Schema `2.json` exported alongside `1.json`
- [x] Migration test opens a v1 database with sample rows, migrates it, and checks every row and total
- [x] Verified on device: v1.0.0 with data → v1.1.0 installed over the top, **no crash, no data loss**

### 3.3 SMS log

- [x] `sms_log` table: sender, time, outcome (`SAVED` / `REVIEW` / `IGNORED` / `DUPLICATE`), reason, amount, transaction id, hash
- [x] **Message bodies are not stored.** Tapping a row re-reads that one message from the inbox on demand.
- [x] SMS Log screen: filter by outcome, search by sender, row detail, "this was wrong" → send to review
- [x] 12-month retention with pruning on import; the import dialog links to the log filtered to that run

### 3.4 Summary and dashboard

- [x] Month summary card: income, gross spend, refunds, net spend, savings; transfers and investments listed separately under "not counted as spend"
- [x] Category breakdown on net amounts with percentage of total
- [x] Top merchants, per-account/per-card totals, daily average, month-end projection
- [x] Period picker: this month, last month, this week, custom range
- [x] Every total is tappable and opens the transactions behind it, so any figure can be checked by hand
- [x] AI payload rebuilt on the corrected totals; the "What is sent?" preview still shows the exact payload

### 3.5 Bill split with on-device OCR

- [x] Split tab with three entry points: camera, Photo Picker, or type the amount
- [x] No `CAMERA` and no storage permission: system camera intent into app-private cache, deleted after OCR; Photo Picker for gallery. `FLAG_SECURE` stays on.
- [x] ML Kit Text Recognition, **bundled model** — works in airplane mode, nothing is downloaded
- [x] Bill parser: grand total, subtotal, tax (CGST/SGST/VAT/service), discount, tip/round-off, line items, merchant, bill date — all unit tested on sample OCR text
- [x] Every extracted value editable before any calculation, with a sanity check when items + tax − discount ≠ total
- [x] Split modes: equal, custom amounts/percentages, shares, per item with tax and discount spread proportionally
- [x] All maths in paise; leftover paise go to the payer, so the parts always sum exactly to the total
- [x] Only **your share** counts as your spend; the rest is tracked as owed to you / you owe
- [x] Settlement matching when the repayment credit SMS arrives; balances view with partial settlement
- [x] `splits`, `split_people`, `split_items`, `split_shares` tables in the same encrypted database; no bill images stored
- [x] Plain-text share-sheet summary; splits included in CSV export and in "Clear all data"

### 3.6 AI mode

- [x] Privacy model unchanged: your own key, tap to run, aggregated totals only
- [x] Fed the corrected net figures from §3.1 and §3.4
- [ ] *Deferred past v1.1:* AI-assisted categorisation of unknown merchants (would send merchant names — needs its own explicit opt-in)

---

## 4. Device test and the defects it found

Full write-up: [docs/DEVICE-TEST-v1.1.md](docs/DEVICE-TEST-v1.1.md). Emulator `fintest` (Android 14, x86_64),
signed release APK, seeded 17-message inbox built so that each new rule has something to get right or wrong.

- [x] Baseline captured on v1.0.0 with the same inbox: spend ₹25,298 — every target bug reproduced
- [x] Upgrade v1.0.0 → v1.1.0 with data present: migration clean, no crash, no data loss
- [x] Fresh v1.1.0 install: **net spend ₹4,218**, and all eleven expected figures matched exactly; the categories sum to the headline total
- [x] OCR and split: bundled model loaded from inside the APK, ₹990 bill read and split three ways, and net spend rose by your share only (the uneven ₹330.04 / ₹329.98 rounding seen here has since been fixed — it is now ₹330 each)

### Defects found and fixed (uncommitted on this branch)

- [x] **Rescan double-counted eight transactions.** Legacy rows sat at midnight while re-parsed ones keep the SMS time, so the ±10-minute window never matched. `findLikelyDuplicate` now also searches the candidate's whole calendar day, with same-day matches held to a stricter same-merchant test. The rescan result went from *Saved 8 / Duplicate 5* to *Saved 1 / Duplicate 12*.
- [x] **Own card used as a merchant** ("your Hdfc Bank Credit Card Xx3344"). The `towards|for` pattern now excludes `your`, `ur` and `my`.
- [x] **Merged duplicates kept a stale flow**, so a card-bill payment carried over from v1.0.0 stayed counted as spend. A merge now always adopts the freshly parsed flow and category — a rescan is the moment a mis-filed transfer corrects itself.
- [x] Four regression tests added in `DuplicateDetectionTest`, incl. `legacyMidnightRowIsMatchedOnRescan`, `ownCardIsNotUsedAsMerchant`, `rescanCorrectsTheFlowOfALegacyRow`
- [x] **Committed** to `v1.1-accuracy-split`

---

## 5. Done — Buro UI redesign

A visual overhaul derived from the reference screens in `stitch_buro_fintech_app/`: a warm off-white page,
white cards separated by hairlines rather than shadows, one saturated blue for anything actionable, and
numbers treated as the hero element.

- [x] Palette and typography sampled from the reference and rebuilt in `ui/theme/Theme.kt`
- [x] Inter font family bundled (`res/font/`) with its OFL licence in `licenses/`
- [x] Shared component set in `ui/components/Buro.kt`: `FinCard`, `SoftPanel`, `Hairline`, `CapsLabel`, `PillChip`, `PrimaryPill`, `SecondaryPill`, `LetterAvatar`, `SectionHeader`
- [x] `TransactionRow` rebuilt on the new language (tinted initial, quiet metadata, right-aligned amount)
- [x] Dashboard rebuilt on the new language: the net-spend figure itself leads the page, with the arithmetic in a tonal panel below
- [x] Transactions, split home and the bottom bar migrated; chips are pills, the bar is a floating white pill
- [x] Dark palette defined alongside the light one (the reference is light-only, so this keeps its structure rather than copying it)
- [x] Visual check on a device — `FLAG_SECURE` is now release-only, so debug builds screenshot normally
- [x] Two layout bugs fixed: nested Scaffolds double-applied the status-bar inset; the nav bar was stock
- [x] Reference assets committed under `stitch_buro_fintech_app/` (7.9 MB) so the palette can be re-derived
- [x] **All 11 screens migrated** onto `FinCard` / `SoftPanel` / `PillChip` / `PrimaryPill` / `CapsLabel`: dashboard, transactions, insights, budgets, split home, split detail, new split, smslog, review, drilldown, edit, settings, onboarding. Every screen walked on a debug build, captured, no crashes.

---

## 6. Left to do — Phase 7 release

- [x] `README.md` updated: features, security table (OCR library, SMS log behaviour), project structure
- [x] `CHANGELOG.md` entry for 1.1.0 written
- [x] `appVersionCode` = 2 and `appVersionName` = `1.1.0` in `app/build.gradle.kts`
- [x] Full unit test run including the migration test (85 passed)
- [x] Manual device test: fresh install, upgrade from v1.0.0, inbox scan, live SMS, bill photo split
- [x] R8 keep rules checked against a **minified release** build — four signed APKs build clean and run
- [x] Airplane-mode run: a full rescan with the radio off reported *0 added, 13 duplicates, 3 ignored*; everything but AI mode works offline
- [x] Per-ABI APKs: arm64 **22.5 MB** (was 68 MB), armeabi-v7a 16.8 MB, x86_64 23.6 MB, universal 66.3 MB
- [x] Commit the outstanding fixes and the UI redesign (§4, §5)
- [ ] Document ML Kit's usage-metrics behaviour honestly in the README, incl. whether it can be disabled in the manifest
- [ ] Mark `CHANGELOG.md` 1.1.0 as released, with its date
- [ ] Build the signed release APK
- [ ] Tag `v1.1.0` and publish the GitHub release with the SHA-256 — **waits for explicit go-ahead**

---

## 6b. Not covered by any test yet

- **The OpenAI request itself.** It needs your own key; I have not asked for one or entered one. Everything
  around it is verified: with no key the app offers only the key field and *What would be sent?*, and
  nothing leaves the phone. To try it, set `OPENAI_API_KEY` and build a debug APK — see
  [docs/RUNNING.md](docs/RUNNING.md).
- **OCR on real, creased, dimly lit receipts.** The emulator camera only offers a synthetic scene, so the
  capture path was verified end to end (FileProvider → system camera → recognition → graceful "no text")
  while accuracy was proven through the gallery path on a rendered bill.
- **Real bank SMS wording** beyond the seeded 17-message set.

---

## 7. Ground rules (unchanged)

- **Local first.** Parsing, calculations, summaries, the SMS log, OCR, split and storage never touch the network.
- **AI mode stays optional and online.** Your own key, tap to run, aggregated totals only — never SMS text, merchant names or account numbers. It is the sole reason the `INTERNET` permission exists.
- **No data loss on update.** Every schema change ships with a tested migration.
- **Every calculation rule gets a unit test.** Parser and maths stay pure Kotlin, so they run as plain JVM tests.
- **No new cloud services, accounts, analytics or ads.**
