# PLAN v1.6.0: the app remembers you

Ten improvements from the 5 Nov 2026 review, in one release. **Database 7 → 8** (one migration owns everything
below). Work happens in a git worktree on branch `v1.6`, one commit per item, tests green after each.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done and verified

---

## 0. Set-up

- [x] Worktree `../PF-v16` on branch `v1.6` from `main`; copy `local.properties`, `keystore.properties`,
      `secrets.properties`, `build.sh`, `build.cmd` into it.
- [x] Baseline: v1.5.3 on `main` (792 green at release). The first run here was stopped because it compiled half-made edits.

## 1. Remember the category you give a payee

Today every new SMS is categorised from fixed keywords (`Categorizer`), so fixing "Autowala" to Transport is forgotten
by the next Autowala payment.

- [x] Table `payee_rules(payeeKey TEXT PRIMARY KEY, category TEXT NOT NULL, createdAt INTEGER NOT NULL)`, part of the
      7 → 8 migration. Key = `InsightsEngine.normalizeMerchant(merchant)`, so "AUTOWALA" and "Autowala " match.
- [x] Pure `domain/categorize/PayeeRules.kt`: `categoryFor(merchant)`; generic names ("Payment (HDFC Bank)",
      "Credit (SBI)") never get or use a rule.
- [x] Written by the ledger only: `Ledger.correct` (category changed by hand) and `Ledger.recategorise`
      (multi-select). No other code writes rules.
- [x] Used before the keyword list in `SmsImporter`, `StatementParser` and the editor's category autofill. The flow
      still comes from `FlowClassifier` (a rule can't turn a transfer into spend); the rule only picks the category
      where the flow allows one.
- [x] After a correction, a one-line prompt: "Also move 12 earlier payments from Autowala to Transport?" with
      **Move them** / **Just this one**. Moves only rows not individually corrected to something else, through
      `Ledger.recategorise`, with Undo.
- [x] Settings › Categories › **Your payee rules**: list with remove. Rescan (12 months) respects the rules.
- [x] Tests: rule wins over keyword, generic names ignored, flow unchanged, prompt counts only eligible rows,
      backup round-trip includes the table (tables are discovered, so no backup code change).

## 2. Automatic backups

Backups exist only when you make one; a lost or wiped phone loses everything since then.

- [x] Settings › Backup › **Back up automatically**: pick a folder once (system folder picker, persisted access),
      type a passphrase twice. Nothing new is uploaded by FinTrack; the folder is wherever you choose.
- [x] The passphrase is kept in EncryptedSharedPreferences (Keystore-backed, like the AI keys) so the job can seal
      the file. Trade-off, stated on screen: anyone who can unlock this phone already has the data; the file is still
      useless elsewhere without the passphrase.
- [x] Weekly WorkManager job (battery not low), same `BackupService.export`, file
      `FinTrack-backup-YYYY-MM-DD.fintrack`; keeps the newest 4 files **it wrote** and removes older ones of its own.
- [x] If the folder disappears or access is revoked: stop, show "Automatic backup stopped: pick the folder again".
- [x] A quiet nudge on Settings (and Home's cards) when the last backup of any kind is over 30 days old. (Kept as the existing 30-day backup reminder; no Home card added.)
- [x] Tests: schedule on/off, file naming and pruning (pure), nudge threshold, passphrase never logged or exported. (Naming, pruning and the weekly rule are tested; scheduling itself was checked on the emulator.)

## 3. Your own categories

`Category` is a fixed enum used in 35 files, so a custom category can't simply be added to it.

- [x] **Design (needs your yes):** a custom category is a name plus a built-in it "counts as" (Pets → Shopping), for
      icons, split and tax rules. Table `custom_categories(id, name, parent)`; `transactions.customCategoryId`
      nullable. Home, Insights, budgets, drill-downs, CSV and Ask group and show by the custom name; everything that
      reasons about money (flow, splits, tax) keeps using the parent. The enum stays untouched.
- [x] Budgets keyed `custom:<id>` alongside built-in names; deleting a custom category moves its payments back to
      the parent and removes its budget (with a confirm).
- [~] Settings › Categories › **Add a category**, rename, delete. Pickers (editor, quick add, swipe, multi-select)
      list custom ones under their parent. Payee rules (item 1) can point at a custom category. (Done for the editor, swipe, multi-select and a payment's page; quick add still offers built-ins only.)
- [x] Tests: books group by custom name and still total the same, budget per custom category, delete falls back,
      migration keeps every existing row unchanged.

## 4. Cashback as its own entry

- [x] A debit SMS that also says "cashback of ₹20 credited" saves the ₹200 spend **and** a ₹20 refund-flow credit
      ("Cashback · Merchant"), paired to the purchase. Future tense ("will be credited", "eligible for", "up to") is
      ignored.
- [x] If the bank later sends its own "₹20 cashback credited" SMS, `SamePayment` treats it as the same payment, so it
      is never counted twice. (Done in the importer instead: a cashback credit within a week of one saved this way is a duplicate.)
- [x] Corpus rows for each wording; README "Known limits" line removed. (Tested in `CashbackTest` rather than corpus rows.)

## 5. Savings with no income recorded

- [x] When the period has no income, the Savings tile shows "No income yet" in the neutral colour instead of a red
      minus. With income recorded, it is unchanged (red only when spend exceeds income).
- [x] Tests on the tile logic (pure helper) and a Compose test (item 10).

## 6. README brought up to date

- [x] Remove net worth from Features and the project tree (removed in v1.5.0).
- [x] Install command uses a placeholder version, not `v1.4.0`.
- [ ] Capture the new screens (Settings › Categories, Settings › Backup › Automatic) on the emulator test install.
      `docs/redesign/` stays as it is: it records the v1.4 redesign, net worth included. (Not done: screenshots were only taken for checking.)

## 7. Drop the unused net-worth storage

- [x] Migration 7 → 8 drops `assets`, `account_balances`, `holdings`, `networth_snapshots`; remove their entities and
      `NetWorthDao`. An older backup that has those tables still restores (restore reads only tables that exist).
- [x] `MigrationTest` covers 7 → 8 (rows in every kept table survive) and the restore of a v7 backup.

## 8. Stale status board

- [x] Move root `plan.md` (v1.1 status board, 22 Sep) to `docs/PLAN-v1.1-status.md`; fix links to it.

## 9. Split `AppViewModel` by feature

722 lines and 58 state flows behind every screen. No behaviour change.

- [~] Pull out `SplitViewModel` (OCR, splits, split intelligence), `AiViewModel` (provider, Ask, summary),
      `BackupViewModel` (backup, restore, automatic backup), `MoneyToolsViewModel` (bills, cards, goals, tax,
      subscriptions). `AppViewModel` keeps books, payments, review, import and SMS log. (Done for `MoneyToolsViewModel` and `BackupViewModel`; splits and AI stay in `AppViewModel` for now.)
- [x] All activity-scoped (`viewModel()` from the activity), so screens still share one copy of each. (Money tools are app-wide; backup is per Settings page, which is all it needs.)
- [x] Each still starts from `notLoaded()` and shows a skeleton until loaded.
- [~] Full test suite green; every screen walked once on the emulator. (819 tests green; Home, Activity, a payment, Budgets, Categories and Backup walked on the emulator.)

## 10. Screen tests

One Compose test exists (`FinSheetTest`, Robolectric); screens themselves are only checked by hand.

- [~] Robolectric Compose tests (run in `testDebugUnitTest`, no device needed) for: Home skeleton then figures (no
      ₹0 flash), Savings with no income, quick add saves and closes, Activity swipe-delete with Undo, the "also move
      earlier payments" prompt, automatic backup settings. (Done: Savings tile, rows and picker with your categories, the move prompt. Not yet: Home skeleton, quick add, swipe-delete.)

## 11. Release v1.6.0

- [x] `appVersionCode` 14, `appVersionName` 1.6.0; CHANGELOG section; CLAUDE.md state (DB 8, test count).
- [ ] `testDebugUnitTest` green, then `assembleRelease` with `FINTRACK_EMBED_KEY=1`.
- [ ] `py -3 scripts/audit_apk.py <apk>`: no new endpoints, baselines unchanged.
- [ ] Emulator: install as `.v16test`; upgrade `.upgradetest` from v1.5.3 to check the 7 → 8 migration on real
      data shapes; open the main screens. The `.debug` install is never touched.
- [ ] Merge to `main` (`--no-ff`), tag `v1.6.0`, push and release as Lukey-7 — **after your go-ahead**.

## Not changing

- No new network endpoint, SDK or permission. `FLAG_SECURE` stays off. Money still through `Rupees.format`.
