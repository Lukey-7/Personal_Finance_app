# Building and running FinTrack on this machine

The Gradle wrapper does not work here: `JAVA_HOME` is not set globally and `services.gradle.org` is
unreachable, so `gradlew` tries to download a distribution and fails. Both helpers below point Gradle at
the JDK 17 and Gradle 8.9 already unpacked under `%USERPROFILE%\android-tools`.

| Shell | Helper |
|---|---|
| Windows CMD / PowerShell | `build.cmd <tasks>` |
| Git Bash | `bash build.sh <tasks>` |

Both are git-ignored, so they stay out of the repo history.

## From a Command Prompt

```
cd C:\Users\hp\Personal_Finance_app

build.cmd assembleDebug
build.cmd assembleRelease
build.cmd testDebugUnitTest
```

## Installing on a phone or the emulator

`adb` lives at `%USERPROFILE%\android-tools\sdk\platform-tools\adb.exe`. Add it to `PATH` once:

```
setx PATH "%PATH%;%USERPROFILE%\android-tools\sdk\platform-tools"
```

Then, from a new prompt:

```
adb devices
adb install -r app\build\outputs\apk\release\FinTrack-v1.1.1-arm64-v8a-release.apk
```

Pick the APK that matches the device:

| File | Use it for |
|---|---|
| `FinTrack-v1.1.1-arm64-v8a-release.apk` | any phone from the last several years (~22 MB) |
| `FinTrack-v1.1.1-armeabi-v7a-release.apk` | older 32-bit phones (~17 MB) |
| `FinTrack-v1.1.1-x86_64-release.apk` | the emulator |
| `FinTrack-v1.1.1-universal-release.apk` | when you do not know the target (~66 MB) |

The size difference is the on-device OCR model, which ships once per architecture.

## Testing AI mode with your key from the environment

AI mode needs your own OpenAI key. Rather than typing it on the phone every time you reinstall, a
**debug** build can pick it up from the environment at build time:

```
set OPENAI_API_KEY=sk-your-key-here
build.cmd assembleDebug
adb install -r app\build\outputs\apk\debug\FinTrack-v1.1.1-arm64-v8a-debug.apk
```

The app then starts with that key already saved, and **Settings → Generate summary** works immediately -
no typing on the device.

Verified end to end with throwaway keys: the key is picked up, the request reaches OpenAI, and the reply
is surfaced verbatim (an invalid key comes back as *"Incorrect API key provided: sk-…"*). Rebuilding with
a different key replaces the previous one.

In PowerShell the first line is instead:

```
$env:OPENAI_API_KEY = "sk-your-key-here"
```

## A personal release build with your key built in

For your own phone you can build the key into a signed **release** APK too. Set one extra variable:

```
set OPENAI_API_KEY=sk-your-key-here
set FINTRACK_EMBED_KEY=1
build.cmd assembleRelease
adb install -r app\build\outputs\apk\release\FinTrack-v1.1.1-arm64-v8a-release-personal.apk
```

Or put both in a `secrets.properties` file at the repo root, so every build picks them up without
`set` commands. The file is git-ignored; environment variables override it:

```
OPENAI_API_KEY=sk-your-key-here
FINTRACK_EMBED_KEY=1
```

Files built this way end in **`-personal`**. They carry your key, so never share them or attach them
to a GitHub release. Clear both variables (`set FINTRACK_EMBED_KEY=` and `set OPENAI_API_KEY=`) before
building APKs to share; without `FINTRACK_EMBED_KEY=1` a release build carries no key at all.

In the app, **Settings → AI monthly summary** says whether it is using the built-in key or your own.
**Change key** replaces it and **Remove key** removes it; either way the key is then yours, and the
built-in one is not put back on the next launch. **Clear all data** resets that too.

### What this does, and why it is opt-in

The key is written into `BuildConfig` and therefore **into the APK**, where anyone holding the file can
read it. That is an acceptable trade for a build that never leaves your hands, and unacceptable for one
you share. So:

- Debug builds use `OPENAI_API_KEY` whenever it is set. Release builds use it only when
  `FINTRACK_EMBED_KEY=1` is also set, and then the file names end in `-personal`. Otherwise
  `SEED_OPENAI_KEY` is compiled as `""`.
- The built-in key replaces a key a **previous build** put there, so rebuilding with a different key
  takes effect without clearing app data. A key you typed, changed or removed in Settings is never
  overwritten.
- The key is never logged, and `OpenAiClient` already strips anything resembling a key from error text.
- Do not send anyone a debug APK built this way. Use the release APKs for that.

To go back to entering the key by hand, build without the variable set:

```
set OPENAI_API_KEY=
build.cmd assembleDebug
```

Or clear it inside the app with **Settings → Remove key**.

## Screenshots

Release builds set `FLAG_SECURE`, so screenshots come out black. Debug builds leave it off, so
`adb exec-out screencap -p > shot.png` works against a debug install.

## Running the tests

```
build.cmd testDebugUnitTest
```

89 tests: parser, categoriser, duplicate detection and cleanup, insights arithmetic, split maths, bill
parser, and the v1 to v2 database migration. The HTML report lands in
`app\build\reports\tests\testDebugUnitTest\index.html`.
