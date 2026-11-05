# Handoff: finishing the FinTrack v1.6.0 release

Written 5 Nov 2026 for the next agent. Read `CLAUDE.md` (house rules, build commands), then `CHANGELOG.md` [1.6.0],
then `docs/PLAN-v1.6.md` (the checklist, with notes on what was left out).

## Where it stands

- **Code:** done and merged. `main` = `218d45a` "Merge v1.6.0…", tag `v1.6.0` points at it, both pushed. Branch `v1.6`
  is pushed too (its last commit says "work in progress… not yet built or tested"; that message is wrong, it was built
  and tested afterwards, and it is left as is rather than force-pushed).
- **Database:** version 8 (`MIGRATION_7_8` in `data/local/AppDatabase.kt`, schema `app/schemas/.../8.json`). Never
  change version 8 now that the tag exists; any further schema change is version 9.
- **Tests:** 819 unit tests green (`EdgeSplitSolverTest.aBusyFortnightSolvesQuickly` is a timing test that can fail on
  a busy machine; it passed on its own rerun).
- **Audit:** `py -3 scripts/audit_apk.py` on the release arm64 APK passed: no new endpoints, logging classes at baseline.
- **GitHub release:** a **draft** exists for `v1.6.0` with `FinTrack-v1.6.0-arm64-v8a-release-personal.apk` uploaded
  (SHA-256 `581bfcb7f97b7c09ba090d40a393acde472565491301997d1661d26abdb0861c`). It is **not published**: nobody can see
  it. The owner wants it published **only after the checks below pass**.

## What still has to be checked before publishing

Checked so far, on a **debug** build (`.debug.v16test`): payee rules (Autowala → Transport, the rule shows in
Settings › Categories), the "Move 1 earlier payment too?" prompt, adding Pets, filing payments under it, Pets in
Budgets and in Home's "Where it went", the Pets drill-down (₹250, one payment), "No income yet" on Savings, and
turning on weekly backup. On the **release** build: it upgraded the emulator's v1.5.3 install (database 7 → 8) without
losing data and opened Home. Then the emulator froze ("Process system isn't responding") because the PC had ~370 MB
free; close Chrome and other agents first.

Still to do, on the **release** APK (R8-minified, so it can break what debug does not):

1. Open Activity, a payment's page, Insights, Split, Budgets, Money tools, and every Settings page. No crash, no ₹0
   flash before data loads.
2. Settings › Backup › "Back up every week": pick a folder (Documents works on the emulator), set a passphrase, then
   `adb shell ls /sdcard/Documents/`: there must be **exactly one** `FinTrack-auto-<date>.ftbackup`. Before the fix,
   turning it on wrote two (the first run raced the newly booked WorkManager job); the fix is a `Mutex` plus a due-check
   under the lock in `data/backup/AutoBackupWorker.kt`, tested only by reasoning and unit tests so far. Then "Back up
   into the folder now" and the off switch ("Stop backups").
3. Settings › AI: OpenAI stays the active provider. Pick Anthropic and its key field should already be filled (the
   built-in backup key, `SettingsRepository.seedProviderKey`). **Test connection** there: the key starts `sk-ant-usr-`,
   not the usual `sk-ant-api03-`, so it may be rejected; tell the owner if so. Switch back to OpenAI afterwards.
4. Settings › Categories: the payee rule should show the payee's name ("Autowala"), not the key ("autowala").

If all pass, publish the draft:

```bash
GH_TOKEN=$(gh auth token --user Lukey-7) gh release edit v1.6.0 -R Lukey-7/Personal_Finance_app --draft=false
```

If something fails: fix it on a branch, test, rebuild with `bash build.sh testDebugUnitTest assembleRelease`, audit,
replace the asset (`gh release upload v1.6.0 <apk> --clobber`, same `GH_TOKEN`), and update the SHA-256 line in the
draft's notes. A code fix after the tag means a **v1.6.1** (new tag, CHANGELOG section, `appVersionCode` 15); don't
move the `v1.6.0` tag.

## Things only on the owner's Windows PC

Git-ignored and needed for a release build that can update the installed app: `keystore.properties` and the keystore
it points to (same signing key, or Android refuses the update), `secrets.properties` (`OPENAI_API_KEY`,
`ANTHROPIC_API_KEY`, `FINTRACK_EMBED_KEY=1`), `local.properties`, `build.sh`/`build.cmd`. They are in
`C:\Users\hp\Personal_Finance_app` and `C:\Users\hp\PF-v16`. Never commit them, never print the keys. On another
machine without the keystore, **don't build or upload a release**; run tests only.

## What v1.6.0 changed (where to look)

- Payee rules: `domain/categorize/PayeeRules.kt`, `data/categories/CategoryStore.kt`; learned only by
  `data/ledger/Ledger.kt` (`correct`, `recategorise`); used in `data/sms/SmsImporter.kt`,
  `data/importer/StatementImporter.kt` and the editor's suggestion.
- Your own categories: `CustomCategory` / `CategoryChoice` in `domain/model/Models.kt` (key `"FOOD"` or `"custom:3"`);
  `Transaction.customCategoryId`; payments keep the built-in "parent" in `category`, so flow, splits and tax are
  unchanged. Grouping in `domain/books/Books.kt`; names in the UI via `ui/components/CategoryNames.kt`
  (`LocalCustomCategories`); Settings page `ui/screens/settings/CategoriesSection.kt`. Anything sent to an AI provider
  uses `PeriodSummary.byBuiltIn`, so a typed category name never leaves the phone.
- Automatic backup: `domain/backup/AutoBackup.kt` (names, pruning, due), `data/backup/AutoBackupWorker.kt`,
  `ui/BackupViewModel.kt`, the Automatic group in `SettingsSections.kt` `BackupSection`.
- Cashback: `domain/parser/Cashback.kt`, booked in `SmsImporter.bookCashback`.
- View models: `ui/MoneyToolsViewModel.kt` and `ui/BackupViewModel.kt` were split out of `AppViewModel`.
- Backup Anthropic key: `app/build.gradle.kts` (`SEED_ANTHROPIC_KEY`), `FinanceApp.seedBuiltInApiKey`. It is never
  used automatically: the privacy rule is that a provider is contacted only once picked in Settings › AI, so there is
  deliberately no automatic switch to Anthropic when OpenAI fails. Ask the owner before adding one.

## Left for later (not needed for 1.6.0)

- Quick add still offers built-in categories only.
- Splits and AI are still in `AppViewModel`.
- Screen tests for the Home skeleton, quick add and swipe-delete.
- New screenshots for the README (Settings › Categories, Backup › Automatic).

## House rules worth repeating

Plan first in `docs/PLAN-<topic>.md` for anything non-trivial; British English with the owner; plain commit messages
with **no AI attribution**; stage explicit paths; push as Lukey-7 with the token command in `CLAUDE.md`; one Gradle run
at a time and `bash build.sh --stop` afterwards (the PC has very little free RAM, and the emulator freezes if Gradle is
running too); never touch the emulator's `.debug` install or the owner's real data.
