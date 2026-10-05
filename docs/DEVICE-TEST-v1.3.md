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

## Checked in the second round (after Gemini Nano and the follow-up fixes)

- [x] **Reversal, live SMS**: a ₹650 UPI debit and its "Reversal of UPI txn" credit sent to the emulator are both tagged
  "Reversed" and hidden behind "Show 2 reversed payments".
- [x] **A real reminder notification**: bill "Phone bill" ₹799 due the next day, reminders on, worker run (clock moved
  forward 13 h with `adb root`, then restored) → notification "Phone bill is due soon · ₹799 due 6 Oct".
- [x] **CAS PDF through the file picker**: a synthetic CAMS-layout PDF in Downloads → Net worth → Import → two funds read
  (₹1,05,467.89 + ₹40,000), Mutual funds ₹1,45,468, net worth updated.
- [x] **Backup → restore through the file picker**: saved `FinTrack-2026-10-05.ftbackup` (9.6 KB, starts with `FTBK1`, no
  readable merchant or table names inside) with "Locking your backup…" shown; deleted a bill; restored with the passphrase
  and RESTORE ("Opening the backup…") → the bill is back.
- [x] **Home-screen widget**: Settings → "Add the widget to your home screen" → Android's "Add to home screen" sheet → the
  widget shows "Spent this month ₹••••" (amounts hidden by default) and "Next: Phone bill · 6 Oct"; "+ Expense" opens the
  quick-add sheet, and ₹240 "Chai and samosa" appears in Activity.
- [x] **Gemini Nano status** on a phone without AICore: Settings says "Not available on this phone…"; Ask keeps its rule answers.

## Not checked here

- [ ] **Gemini Nano answering**: needs a phone with Android AICore (Pixel 9 or later, Galaxy S24 or later). Unit tests
  cover the facts and prompt it is given.
- [ ] **A real CAS** from CAMS or KFintech (the layout used above is synthetic).

## Found and fixed during this check

- "This month in words" said "₹0 in Oct, 100% less than Sep" on the 5th of the month; it now says
  "Nothing spent yet in Oct 2026 (Sep 2026: ₹4,218)".
- The `.v13` install from an early v1.3 build crashed after later builds added tables to the same (unreleased) database
  version. Only a development install can hit this; upgrades from 1.2 run the final migration, which `MigrationV6Test`
  checks against the final schema.
