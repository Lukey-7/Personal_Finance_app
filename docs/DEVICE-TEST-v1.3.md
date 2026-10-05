# Device test: v1.3

**Build:** `FinTrack-v1.3.0-x86_64-debug-personal.apk`, branch `v1.3-features`, installed beside the shared debug app as
`com.pft.financetracker.debug.v13` (`bash build.sh assembleDebug -PappIdSuffix=.v13`) so the shared install's data was
never migrated.
**Device:** emulator `fintest` (Android 14, 1080×2340, density 440), seeded 17-SMS inbox. **Date:** 5 Oct 2026.

Legend: `[x]` checked on the emulator · `[ ]` not checked here (reason given)

## Checked

- [x] Fresh install, onboarding, SMS scan: 17 SMS, 12 added, 1 to review (same as v1.2).
- [x] Home top bar → **Money tools** lists Ask, Subscriptions, Bills & EMIs, Credit cards, Goals, Tax helper, Net worth.
- [x] **Net worth** shows ₹8,000 straight away: the "Avl Bal" figures in the seeded SMS.
- [x] **Ask FinTrack**: "This month in words" → September net spend ₹4,218 (matches the v1.2 baseline);
  typed "food last month" → "₹420 on Food & Dining last month (3 payments)" with "Show 3 payments".
- [x] Subscriptions, Bills, Credit cards, Goals, Tax helper (FY 2026-27 / 2025-26 chips) and Net worth all open; empty
  states read correctly.
- [x] **Add bill** "Rent", ₹25,000, day 8 → listed as "Due 8 Oct · ₹25,000".
- [x] **Reminders** switch → Android notification permission dialog → Allow → switch on; WorkManager job scheduled.
- [x] **Review → Save** teaches a shape: Settings lists "UNKNWN · money out — Txn alert: a/c {N} amount {AMT} processed".
- [x] Long-press shortcuts `quick_add` and `quick_cash` are published; the widget provider is registered.
- [x] APK audit (`scripts/audit_apk.py`): telemetry counts at or below the v1.2 baseline; one endpoint, `api.openai.com`.

## Not checked here (please try on your phone)

- [ ] **Widget on a home screen** and its "+ Expense / + Cash" buttons (placing a widget needs the launcher's UI).
- [ ] **A real CAS PDF** from CAMS or KFintech (the parser is tested on synthetic text in their layout).
- [ ] **Backup → Restore** through the system file picker (the round trip is unit-tested on every table).
- [ ] **A real reminder notification** (needs a bill or renewal 1-3 days ahead and the twice-daily worker to run).
- [ ] **Refund pairing and "Show reversed payments"** with a real failed-UPI reversal (unit-tested).

## Found and fixed during this check

- "This month in words" said "₹0 in Oct, 100% less than Sep" on the 5th of the month; it now says
  "Nothing spent yet in Oct 2026 (Sep 2026: ₹4,218)".
- The `.v13` install from an early v1.3 build crashed after later builds added tables to the same (unreleased) database
  version. Only a development install can hit this; upgrades from 1.2 run the final migration, which `MigrationV6Test`
  checks against the final schema.
