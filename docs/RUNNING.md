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
adb install -r app\build\outputs\apk\release\FinTrack-v1.1.0-arm64-v8a-release.apk
```

Pick the APK that matches the device:

| File | Use it for |
|---|---|
| `FinTrack-v1.1.0-arm64-v8a-release.apk` | any phone from the last several years (~22 MB) |
| `FinTrack-v1.1.0-armeabi-v7a-release.apk` | older 32-bit phones (~17 MB) |
| `FinTrack-v1.1.0-x86_64-release.apk` | the emulator |
| `FinTrack-v1.1.0-universal-release.apk` | when you do not know the target (~66 MB) |

The size difference is the on-device OCR model, which ships once per architecture.

## Testing AI mode with your key from the environment

AI mode needs your own OpenAI key. Rather than typing it on the phone every time you reinstall, a
**debug** build can pick it up from the environment at build time:

```
set OPENAI_API_KEY=sk-your-key-here
build.cmd assembleDebug
adb install -r app\build\outputs\apk\debug\FinTrack-v1.1.0-arm64-v8a-debug.apk
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

### What this does, and why it is debug-only

The key is written into `BuildConfig` and therefore **into the APK**, where anyone holding the file can
read it. That is an acceptable trade for a build that never leaves your machine, and unacceptable for one
you share. So:

- Release builds always compile `SEED_OPENAI_KEY` as `""`, whatever is in the environment.
- The seed replaces a key a **previous build** seeded, so rebuilding with a different key takes effect
  without clearing app data. A key you typed into Settings yourself is never overwritten.
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

90 tests: parser, categoriser, duplicate detection and cleanup, insights arithmetic, split maths, bill
parser, and the v1 to v2 database migration. The HTML report lands in
`app\build\reports\tests\testDebugUnitTest\index.html`.
