# v1.1.1 Code Review Fixes

**Goal:** Fix the money-data bugs and lint errors found in the 2026-09-24 code review of v1.1.0 → v1.1.1, so that a rescan never silently changes an amount the person or a split set, and deleted or mis-directed legacy rows stay the way the person left them.

**Branch:** `v1.1-accuracy-split` · **Build:** `bash build.sh testDebugUnitTest assembleDebug lintDebug`

Legend: `[x]` done and verified · `[ ]` not started

## Checklist

### 1. Critical: rescan resets split transactions to the full bank amount
v1.1.0's split flow shrank the linked SMS row to "my share" without recording `originalAmountPaise`. The migration left it null, so `repairIfWrong` overwrites the row with the full bank amount.
- [x] New `MIGRATION_3_4` (DB v4, no schema change): for each SMS row a split links to with `originalAmountPaise IS NULL`, set `originalAmountPaise = split total` and `amountPaise = my share`, but only when the row's amount is still the total or my share. This also restores rows a v1.1.1 rescan already reset.
- [x] Export `schemas/.../4.json`
- [x] Test: build a v3 DB with a split-shrunk row and an already-reset row, migrate to v4, check both hold my share with the total remembered
- [x] Test: a rescan of a split-shrunk row leaves the amount unchanged

### 2. Important: rescan can overwrite pre-v1.1.1 edits
All rows from before the migration have `userEdited = 0`, and `repairIfWrong` rewrites the amount whenever the parser disagrees.
- [x] `repairIfWrong` fixes only the backwards-direction bug: repair when the parse's amount equals the stored amount and the direction differs. Never rewrite `amountPaise` automatically.
- [x] Test: a row whose amount differs from the parse is left untouched on rescan
- [x] Existing test `rescanRepairsARowTheOldParserGotBackwards` still passes

### 3. Important: deleted v1.0 transactions come back on rescan
v1.0 rows have no `sms_log` row and an older hash, so `forgetDeleted` records nothing.
- [x] `updateOutcome` returns the number of rows changed. When it is 0, `forgetDeleted` writes a tombstone log row (`deleted:<old hash>`, amount, day, reason `deleted_by_user`).
- [x] Before inserting a parsed SMS, the importer checks for an unused tombstone with the same amount on the same day. If one matches, the SMS is logged as deleted by the user, the tombstone is marked used (one tombstone blocks one message), and nothing is inserted.
- [x] Test: delete a legacy midnight row, rescan its SMS, and no row comes back. A second same-amount SMS that day is still imported.

### 4. Important: legacy midnight match ignores direction
A same-day refund of the same amount merges into a v1.0 debit and flips it into a credit.
- [x] In `findLikelyDuplicate`, check same-direction candidates first. A legacy row that an SMS already matched (an `sms_log` row points at it) is never matched again, so the day's debit claims it and a later same-amount refund becomes its own row.
- [x] ~~Opposite direction only with a named same merchant~~. Dropped: two existing tests (`legacyMidnightRowMatchesEitherDirection`, `rescanCorrectsTheFlowOfALegacyRow`) show that generic-sender legacy rows stored backwards must still be corrected. The claimed check covers the refund case.
- [x] Test: once claimed, a same-merchant refund is not matched (`claimedLegacyRowIsNotTakenAgain`)
- [x] Test: the debit SMS claims the legacy row, then the same-day refund leaves it a debit (`legacyRowMatchedByItsDebitIsNotFlippedByASameDayRefund`)

### 5. Lint errors (3)
- [x] `SmsImporter.kt:265`: use `getColumnIndexOrThrow` (it can return -1 → crash)
- [x] `AndroidManifest.xml`: add `<uses-feature android:name="android.hardware.telephony" android:required="false"/>` so the SMS permissions don't hide the app on tablets and Chromebooks

### 6. Verify and record
- [x] `bash build.sh testDebugUnitTest assembleDebug lintDebug`: all tests pass, 0 lint errors
- [x] CHANGELOG entry under Unreleased
- [x] Commit with explicit paths only

**Result (2026-09-24):** 127 unit tests pass (120 before, 7 new), the debug build succeeds, and lint reports 0 errors.

### 7. Emulator upgrade check (2026-09-24)
Three builds were installed in turn as a separate test app (`com.pft.financetracker.upgradetest`), so the regular app's data was not touched. The seeded 17-SMS inbox was used.
- [x] **v1.1.0:** import, then split the ₹1,299 Amazon card spend with Asha (my share ₹649.50). Net spend ₹4,218 → ₹3,569.
- [x] **v1.1.1:** upgrade, then "Rescan the last 12 months". Net spend is back to ₹4,218 while Asha still owes ₹650. **Bug reproduced.**
- [x] **This fix:** upgrade (DB v4 migration). Net spend is ₹3,569 again. A second full rescan keeps it at ₹3,569. No crash in logcat.
- Noticed: the split screen's date field opens its picker only on a held press. A very quick tap (adb `input tap`) did nothing. Worth checking with a real finger.

### 8. Minor review items
- [x] "Rs 500 paid to you by X" is income ("paid to your card" stays a spend). Two corpus rows added.
- [x] Bill items priced with 5 bare digits ("Speaker 12500") are kept. 6+ digits, or 5 digits with no real word before them ("Inv 88213", OCR "Iny"), are still skipped as codes. Found by the real-photo test `groceryAngled`.
- [x] `OcrEngine`: a failed Hindi reading still falls back to Latin, but cancellation now propagates. Removed the unused `decodeLegacy`. Peak memory was already bounded: the photo is capped at 2000px and both readers share one bitmap.
- [x] Log pruning keeps the user's own decisions (deleted, dismissed) and the v1.0 tombstones.
- [x] A debug APK with a built-in OpenAI key is named `-personal` (RUNNING.md updated).
- [x] 128 unit tests pass, the build succeeds, lint 0 errors
