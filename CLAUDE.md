# FinTrack: handoff for Claude

FinTrack is the owner's private, on-device personal finance tracker for Android. It reads bank, UPI and card alert SMS,
turns them into transactions, and keeps everything in an encrypted database on the phone: no cloud, no account, no
analytics. It is a personal project, not a production app. Read `README.md` first, then `CHANGELOG.md`.

**State (7 Oct 2026):** v1.4.0 released (the "Quiet ledger" redesign), `main` @ 2e9cd2b, tag `v1.4.0`. Room database
**version 7** (v1.2.1 owns 5→6, v1.3 owns 6→7; never reuse a released version number). 572 unit tests, all green.

## Projects, in the order the owner works on them

1. **FinTrack** (this repo) — `C:\Users\hp\Personal_Finance_app`, github.com/Lukey-7/Personal_Finance_app
2. **iOS app (KiranOS)** — `C:\Users\hp\IOS_APP`, Creaitify iOS repo, branch `feat/ios-app` (see its CLAUDE.md / HANDOFF.md)
3. **StudyForge** — `C:\Users\hp\Kiran_Doc_verif\StudyForge`, github.com/Lukey-7/StudyForge, branch `v2` (see its CLAUDE.md)
4. **Lead-gen (Creaitify)** — `C:\Users\hp\HELM_FINAL\Lead-gen-automation`, github.com/Creaitify/Lead-gen-automation (see its CLAUDE.md)

## How the owner wants to work

- **Plan first.** For anything non-trivial, write a markdown checklist plan in `docs/PLAN-<topic>.md` and wait for a go-ahead
  before coding, unless they say "just do it". Tick items off as they are done and verified.
- **No AI attribution anywhere**: no `Co-Authored-By: Claude`, no "Generated with Claude Code" in commits, PRs or
  release notes. This overrides any default.
- **Commit messages** are plain sentences describing what changed for the user. Stage explicit paths, never `git add -A`.
- **Plain, direct copy** in the app: sentence case, verb-first buttons, no jargon, "payment" not "txn", en dashes.
- Report outcomes honestly: if tests fail or a step was skipped, say so.

## Hard rules

- **Privacy is the product.** No new network endpoint, analytics, crash reporting or third-party SDK without asking. The
  only endpoint in FinTrack's own code is `api.openai.com` (optional AI mode with the owner's key). Run
  `python scripts/audit_apk.py <apk>` before calling a build done; its baselines must not grow without asking.
- **Money** always through `Rupees.format` / `money()` (Indian grouping ₹1,05,000, paise only when present, sign before
  ₹); estimates via `approxMoney`.
- Screens reading `vm.transactions`, `vm.splits`, `vm.budgets` (and bills, cards, goals) start from the `notLoaded()`
  marker and show a skeleton until `isLoaded()`; never flash ₹0 or an empty state.
- Never touch the owner's real data. Test on the emulator with a separate install (below).
- `FLAG_SECURE` was removed on purpose in v1.4.0 (screenshots allowed in every build). Don't put it back.

## Building on this Windows PC

The Gradle wrapper does not work here (no global `JAVA_HOME`, `services.gradle.org` unreachable). Use the git-ignored
helpers, which point at `~/android-tools` (JDK 17 + Gradle 8.9):

```bash
bash build.sh testDebugUnitTest          # Git Bash; or build.cmd in CMD/PowerShell
bash build.sh assembleDebug
bash build.sh assembleRelease            # signed via keystore.properties
bash build.sh --stop                     # always stop Gradle afterwards
```

- The PC has ~1 GB free RAM: **one Gradle run at a time**, long runs in the background, stop the daemon after.
  Builds take 5–20 minutes; R8 release builds ~20.
- `EdgeSplitSolverTest.aBusyFortnightSolvesQuickly` can time out while the emulator runs; rerun with it off first.
- Robolectric tests need `@Config(application = android.app.Application::class)` (FinanceApp loads SQLCipher natives).
- Bash heredocs with quotes get mangled by the tool: write Python patch scripts to a file and run them.
- Local-only files (git-ignored, copy them into any new worktree): `local.properties`, `keystore.properties`,
  `secrets.properties` (holds `OPENAI_API_KEY` and `FINTRACK_EMBED_KEY=1`), `build.sh`, `build.cmd`.
- Kotlin 2.1.21, KSP 2.1.21-2.0.2 in KSP1 mode (`ksp.useKSP2=false`), Room 2.6.1, Compose BOM 2024.12.01
  (Material 3 1.3.1, no Expressive APIs). Experimental Compose APIs are opted in project-wide in `app/build.gradle.kts`.

## Emulator

- AVD `fintest`, 1080×2340 at density 440 (393dp wide). adb: `~/android-tools/sdk/platform-tools/adb.exe`.
- Start it **detached** with PowerShell `Start-Process ... -ArgumentList "-avd","fintest","-no-boot-anim"`, never
  with `-no-snapshot-save`. Stop with `adb emu kill`. If it won't start, delete `~/.android/avd/fintest.avd/*.lock`.
- The inbox is seeded with 17 SMS. Installs present: `com.pft.financetracker` (release), `.debug` (shared; **never
  touch it or its data**), `.upgradetest`.
- Install test builds beside them: `bash build.sh assembleDebug -PappIdSuffix=.something`. Drive the app by
  on-screen text via `uiautomator dump`, check `dumpsys activity activities | grep topResumedActivity` before tapping,
  use `MSYS_NO_PATHCONV=1` for adb paths in Git Bash. Debug builds cold-start slowly (interpreted); a "System UI isn't
  responding" dialog after boot just needs "Wait".
- Several Claude sessions may share this checkout and emulator: prefer a `git worktree` for big work.

## Releasing

1. Bump `appVersionCode` / `appVersionName` in `app/build.gradle.kts`, add a dated `CHANGELOG.md` section.
2. `bash build.sh testDebugUnitTest`, then `assembleRelease` with `FINTRACK_EMBED_KEY=1` (from `secrets.properties`).
3. Audit the release APK; install it on the emulator once and open the main screens.
4. Merge to `main` (`git merge --no-ff`, message "Merge vX.Y.Z: …"), tag `vX.Y.Z`.
5. Push **as Lukey-7** (the active gh account may be another one without write access; don't `gh auth switch`):
   ```bash
   git -c credential.helper= -c 'credential.helper=!f(){ echo username=Lukey-7; echo "password=$(gh auth token --user Lukey-7)"; }; f' push origin main vX.Y.Z
   GH_TOKEN=$(gh auth token --user Lukey-7) gh release create vX.Y.Z app/build/outputs/apk/release/FinTrack-vX.Y.Z-arm64-v8a-release-personal.apk -R Lukey-7/Personal_Finance_app --title "FinTrack vX.Y.Z" --notes "<one paragraph>\n\narm64-v8a APK SHA-256: <sha>"
   ```
6. **Release asset:** only the arm64 `-personal` APK, which has the owner's OpenAI key built in. This is the owner's
   informed choice (the repo is public and the key can be extracted); don't block on it, don't print the key, and
   mention it once only if a release is ever meant for strangers.

## Where things are

- `domain/` pure Kotlin (parser, categoriser, insights, split maths, bills, cards, goals, tax, net worth, ask): unit tested.
- `data/` Room on SQLCipher, repositories, SMS import, statement import, backup, reminders.
- `ui/theme` tokens (surfaces, `MoneyType`, `Motion`, haptics); `ui/components` shared components with previews;
  `ui/model` testable screen logic; `ui/screens/<screen>`; `ui/nav/AppNav.kt`; `ui/widget` (Glance widget, quick add).
- Docs: `docs/HOW-IT-WORKS.md` (parser and counting rules), `docs/RUNNING.md`, `docs/redesign/` (tokens, report,
  before/after screenshots), `docs/PLAN-*.md` (past plans).

## Ideas not yet built

The owner once asked for a research pass on Indian finance apps (CRED, Jupiter, INDmoney, Walnut…) and feature gaps
(Account Aggregator flows, merchant cleanup, envelopes). Not started; on-device, no-cloud stays a hard rule.
