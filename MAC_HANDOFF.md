# FinTrack: moving to a Mac

Written 10 Oct 2026 on the Windows PC, for the owner and for Claude Code on the new Mac. Read it together with
`CLAUDE.md` (how the owner works, the hard rules, the release steps). Where the two disagree on paths or tooling, this file
wins on the Mac.

> **Before you leave the PC — two must-dos:**
> 1. **Back up `C:\Users\hp\android-tools\keys\fintrack-release.jks`** and copy it to the Mac (section 3). Lose it and
>    no future build can update the installed release app.
> 2. **Check out `deep-modules`** (section 4). It is on GitHub (pushed 10 Oct) and holds every unreleased change since v1.4.0.
>    `git switch deep-modules` after cloning.

## 1. What it is

FinTrack is the owner's private, on-device personal finance tracker for Android. It reads bank, UPI and card alert SMS,
turns them into payments and keeps them in an encrypted database on the phone: no cloud, no account, no analytics.
Package `com.pft.financetracker`. Released: **v1.4.0** (versionCode 9), tag `v1.4.0`. Room database **version 7**.

Stack: Kotlin 2.1.21, Jetpack Compose (BOM 2024.12.01, Material 3 1.3.1), Room 2.6.1 on SQLCipher 4.6.1, KSP
2.1.21-2.0.2 in KSP1 mode (`ksp.useKSP2=false`), WorkManager, Glance widget, ML Kit OCR plus GenAI prompt (Gemini Nano),
PDFBox-Android. AGP 8.7.3, Gradle 8.9 (wrapper). Unit tests: JUnit 4 plus Robolectric 4.14.1. Versions live in
`gradle/libs.versions.toml`.

## 2. Mac setup from zero

| Thing | Version (as pinned) |
|---|---|
| JDK | **17** (`compileOptions` / `jvmTarget` 17) |
| Gradle | 8.9 via `./gradlew` (on the Mac the wrapper works, so drop `build.sh` / `build.cmd`) |
| Android Gradle Plugin | 8.7.3 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 31 |
| SDK packages | `platforms;android-35`, `build-tools;35.0.0` (PC also had `34.0.0`), `platform-tools`, `emulator` |
| Emulator image | PC used `system-images;android-34;default;x86_64`. On Apple silicon use **`system-images;android-34;default;arm64-v8a`** |
| Android Studio | Ladybug (2024.2) or newer |

```zsh
# Tools
xcode-select --install                       # git, python3 (for scripts/audit_apk.py)
brew install --cask temurin@17 android-studio
brew install gh

# JDK 17 for Gradle (~/.zshrc)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin

# Open Android Studio once: the setup wizard installs the SDK to ~/Library/Android/sdk.
# SDK Manager > SDK Tools: tick "Android SDK Command-line Tools (latest)". Then:
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" "emulator" \
           "system-images;android-34;default;arm64-v8a"

# Clone
mkdir -p ~/code && cd ~/code
gh auth login                                 # log in as Lukey-7 (that account has write access)
git clone https://github.com/Lukey-7/Personal_Finance_app.git
cd Personal_Finance_app
git switch deep-modules                       # on GitHub; the newest work

# local.properties: generate it, do NOT copy it from Windows
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # or just open the project in Android Studio

# Build, test, install
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease                     # signed via keystore.properties (section 3)
./gradlew --stop
python3 scripts/audit_apk.py app/build/outputs/apk/release/<apk>   # before calling any build done
```

**Emulator** (replaces the Windows AVD `fintest`, 1080x2340, density 440, 393dp wide):

```zsh
avdmanager create avd -n fintest -k "system-images;android-34;default;arm64-v8a" -d pixel_6
# then in ~/.android/avd/fintest.avd/config.ini set:
#   hw.lcd.width=1080  hw.lcd.height=2340  hw.lcd.density=440
emulator -avd fintest -no-boot-anim &         # never -no-snapshot-save; stop with: adb emu kill
adb devices
```

- Install test builds **beside** the real ones: `./gradlew assembleDebug -PappIdSuffix=.something`, then
  `adb install -r app/build/outputs/apk/debug/FinTrack-v<ver>-arm64-v8a-debug*.apk`. On the Mac emulator use the
  **arm64-v8a** APK, not x86_64.
- The Windows emulator's seeded inbox (17 SMS) and its installs do not move. Re-seed with
  `adb emu sms send <sender> "<text>"` before SMS testing.
- Windows-only notes in `CLAUDE.md` that no longer apply: `build.sh` / `build.cmd`, `~/android-tools/...`, `adb.exe`,
  `Start-Process` to launch the emulator, `MSYS_NO_PATHCONV=1`, "~1 GB free RAM, one Gradle run at a time" (still keep
  one Gradle run at a time if the Mac is short of memory). Still true: `EdgeSplitSolverTest.aBusyFortnightSolvesQuickly`
  can time out while the emulator runs; Robolectric tests need `@Config(application = android.app.Application::class)`.
- `docs/RUNNING.md` and README "Building" still describe the Windows setup; update them on the Mac when convenient.

## 3. Secrets and local-only files (git-ignored, never committed)

| File | Keys (names only) | On the Mac |
|---|---|---|
| `keystore.properties` | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` | Copy it over, then **change `storeFile`** to the new absolute Mac path of the `.jks` |
| `secrets.properties` | `OPENAI_API_KEY`, `FINTRACK_EMBED_KEY` | Copy it over as is |
| `local.properties` | `sdk.dir` | **Regenerate** (`sdk.dir=/Users/<you>/Library/Android/sdk`); never copy the Windows one |
| `build.sh`, `build.cmd` | n/a | Not needed; use `./gradlew` |

> ### !!! THE RELEASE KEYSTORE !!!
> **`C:/Users/hp/android-tools/keys/fintrack-release.jks`** signs every release of FinTrack. It is **not in git**
> (`*.jks` is ignored) and exists only on this PC. If it is lost, the installed release app can never be updated again:
> the owner would have to uninstall it, losing the on-phone data unless it was backed up first.
>
> 1. Back it up now to at least two places (an encrypted USB drive plus a password manager attachment or encrypted
>    cloud vault). Keep the passwords from `keystore.properties` with it, separately from the file.
> 2. Copy it to the Mac, e.g. `~/android-keys/fintrack-release.jks`, then `chmod 600` it.
> 3. Edit `keystore.properties`: `storeFile=/Users/<you>/android-keys/fintrack-release.jks` (absolute path).
> 4. Check it: `./gradlew assembleRelease`, then `$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs <apk>`
>    should show the same certificate as the v1.4.0 APK on GitHub. If `storeFile` is wrong, the build **silently falls
>    back to the debug key** (see `hasReleaseKey` in `app/build.gradle.kts`): that APK will not update the phone.

Move these files by USB or AirDrop, never by email, chat, or a commit.

## 4. Branch state at migration (10 Oct 2026)

The history is one straight line: `main` → `v15-fixA..E` (merged together) → `v1.5-fixes` → `deep-modules`. Each
later branch contains all of the earlier ones (each is 0 behind `main`, 0 behind its predecessor). **Pushing
`deep-modules` alone carries every unreleased commit.** No worktree has uncommitted changes.

| Branch (worktree) | Pushed? | vs `main` | Latest commit / purpose |
|---|---|---|---|
| `main` (`Personal_Finance_app`) | yes, up to date | n/a | `ac81cdc` Add CLAUDE.md handoff. One commit on top of the v1.4.0 merge `2e9cd2b`. |
| `deep-modules` (`PF-deep`) | pushed 10 Oct | +29 | `565dfc8` Each figure has one definition the total and its list share… Refactor into three deep modules (Same payment, Ledger, Books) with no behaviour change. `docs/PLAN-deep-modules.md`: "done 9 Oct, not merged". **Newest work.** |
| `v1.5-fixes` (`Personal_Finance_app-v1.5`) | pushed 10 Oct | +24 | `16a5dfe` Plan to move the duplicate rules, the payment writes and the counting rules into one place. Integration branch for the v1.5 review fixes: merges fixA–E plus fixes found by using the app. Not released or version-bumped yet. |
| `v15-fixA` (`PF-fixA`) | pushed 10 Oct | +8 | `67fe2e0` Insights and Budgets follow the cash setting; Ask fixes; budgets. Merged into v1.5-fixes. |
| `v15-fixB` (`PF-fixB`) | pushed 10 Oct | +8 | `51177aa` Editor and activity fixes from review (money in/out, dates, rotation, refunds). Merged. |
| `v15-fixC` (`PF-fixC`) | pushed 10 Oct | +9 | `21c6834` A restore reports when the backup was made, so the next SMS scan picks up from that day (plus bills/goals). Merged. |
| `v15-fixD` (`PF-fixD`) | pushed 10 Oct | +8 | `0ac3035` SMS reading and statement import fixes. Merged. |
| `v15-fixE` (`PF-fixE`) | pushed 10 Oct | +11 | `210d8ac` Splits keep the numbers right (paid-with payment, settle up, bill photos). Merged. |
| `v1.3-features` (`Personal_Finance_app-v1.3`) | pushed 10 Oct | 0 ahead, 43 behind | `2b4c459` Add the widget from Settings in one tap… Old v1.3.0 work, fully in `main`. Safe to drop. |
| `redesign` (`Personal_Finance_app-redesign`) | yes | 0 ahead, 2 behind | `d594972` Release v1.4.0: the Quiet ledger redesign. Fully in `main`. Safe to drop. |
| `v1.1-accuracy-split` | pushed 10 Oct | 0 ahead, 87 behind | `b18b468` Stop cashback matching Cash / ATM… That one unpushed commit is already in `main`. Nothing to save. |

Other local branches (`ui-polish`, `v1.2.1-edge-cases`, `claude/sad-margulis-61a349`, three `worktree-agent-*`) are
already in `deep-modules` (the agent ones as equivalent cherry-picks). Nothing there needs saving.

**Everything is on GitHub:** every branch, this file included, was pushed as Lukey-7 on 10 Oct.
A fresh clone plus `git fetch --all` has it all.

**Suggested merge order:** the history makes it simple. Review and test `deep-modules` (it already includes v1.5-fixes
and A–E), then cut v1.5.0 from it: bump `appVersionCode` to 10 and `appVersionName` to `1.5.0`, add the CHANGELOG
section, `git merge --no-ff deep-modules` into `main` ("Merge v1.5.0: …"), tag `v1.5.0`. If the owner wants the fixes
shipped without the refactor, release `v1.5-fixes` first, then merge `deep-modules` as v1.5.1 or v1.6.0.

**`C:\Users\hp\pf-main`** (not a git repo, last touched 5 Oct 2026) is a plain copy of the **v1.3.0** source
(versionName 1.3.0, code 7). Every one of its files matches a file already somewhere in git history (line endings
ignored), including `stitch_buro_fintech_app/`, the design mockups removed in `ef89566`. **No unique content; no need to
move it.**

## 5. First prompt for Claude on the Mac

> I've moved FinTrack from Windows to this Mac. Read `CLAUDE.md`, then `MAC_HANDOFF.md`, `README.md` and the top of
> `CHANGELOG.md`; `MAC_HANDOFF.md` overrides `CLAUDE.md` on paths and tooling. Check the setup: `java -version` is
> 17, `local.properties` points at `~/Library/Android/sdk`, `keystore.properties` `storeFile` points at the `.jks` on
> this Mac and the file exists (key names only, never print the values), and `secrets.properties` is present. Switch to
> `deep-modules`, run `./gradlew testDebugUnitTest` and report the result honestly. Then update `CLAUDE.md` and
> `docs/RUNNING.md` for the Mac (`./gradlew`, `~/Library/Android/sdk`, the arm64 `fintest` emulator) and drop the
> Windows-only notes. Write the plan for releasing v1.5.0 from `deep-modules` in `docs/PLAN-v1.5-release.md` and wait
> for my go-ahead before merging or pushing. Usual rules: no AI attribution, explicit `git add` paths, plain commit
> messages.
