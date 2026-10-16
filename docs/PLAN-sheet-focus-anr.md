# Plan: no focused window after a sheet closes (ANR)

**Status:** done 16 Oct 2026 on branch `claude/reverent-cohen-3abc89`; handed to the health check session for its own pass.

## The bug

"Input dispatching timed out (Application does not have a focused window)", seen three times on 13 Oct 2026 on the
emulator (`com.pft.financetracker.debug.v151`), each right after a `FinSheet` was removed from composition at once by
its caller:

1. Settings › AI provider picker: pick a provider, then Back does nothing. Already worked around in v1.5.1
   (`AiProviderSection.kt` awaits `pickerState.hide()` before `picking = false`).
2. and 3. Settings › Backup › Restore from a backup: the sheet (passphrase + RESTORE) is shown straight after the
   system document picker returns, with the keyboard up; Restore sets `restoreAsk = null`, the restore finishes, the
   next Back key is dropped ("no focused window") and the ANR follows. The main thread is idle in the traces, so this
   is window focus, not a blocked thread.

## What the code says so far

- Material 3 1.3.1's `ModalBottomSheet` is its own dialog window (`ModalBottomSheetDialogWrapper`, a `ComponentDialog`).
  Removing it from composition calls `dismiss()` on that window at once, with no slide-out, while it may still hold
  focus and the keyboard.
- `FinSheet` (`ui/components/Controls.kt`) creates its sheet state internally unless the caller passes one, so most
  callers can't hide it first even if they wanted to. Some screens (Cards, Budgets, Bills, Goals, Recurring, quick
  add) already have their own `close(after)` that awaits `state.hide()`; Settings, Transactions, Split, Tax, Import,
  `InfoSheet` and `BackupPassphraseDialog` do not.
- The restore path has not changed since v1.5.0 (same restore code, same Compose BOM 2024.12.01 / Material 3 1.3.1;
  only the version number differs in `build.gradle.kts`), so v1.5.0 very likely has the same bug. To be confirmed.
- Not yet known: whether the trigger is the instant removal itself, the keyboard still being up, the dialog being
  added while the activity window is still coming back from the picker, or a mix. The log line "ActivityRecord ... may
  eventually add a window" suggests the activity's own window was not focusable at that moment (not drawn yet, or not
  visible), so the reproduction will capture the window state, not just the ANR.

## Findings (16 Oct 2026)

- **On a freshly booted emulator the freeze does not happen.** The restore flow (pick the fixture, type the passphrase
  and RESTORE with the keyboard up, submit by the keyboard's Done or by the Restore button, then Back) passed 7 of 7
  times on the v1.5.1 code: focus moved from the sheet's window to the screen, Back worked, the payments were restored.
- **Before that reboot the emulator was in a broken state**, and that is where the freeze comes from. Window
  animations had stopped finishing: the app's own window sat frozen mid-slide off screen (`frame=[1134,0][2214,2340]`)
  with the CPU idle; the system document picker, after returning a file, stayed `finishing=true, state=STOPPING` with
  its invisible window above the app's in the same task; taps on the sheet were dropped as "untrusted touch due to
  occlusion" by that leftover window; the window shell reported `mIsReady=false`. The other session's `.v151` install
  was in exactly the ANR state at that moment (`FocusRequests ... result='NO_WINDOW'`, no focused window).
- In that state a sheet that slid away by Back still handed focus back correctly. A sheet pulled out of composition
  at once is the remaining way to end up with no focused window, which matches all three sightings.
- So: the trigger is the emulator's stuck window transitions (likely after long uptime with several installs and
  sessions); the app's part is that it removed a focused window abruptly in that state. Making every sheet let go of
  the keyboard and slide away before it is removed is cheap and is what fixed the AI picker, so it is done
  centrally (owner's choice, 16 Oct). It cannot be proven against the broken emulator state, which could not be
  recreated on demand.
- v1.5.0: same restore code and same Material 3 version, so it behaves the same; not rebuilt, since the freeze needs
  the broken emulator state rather than a code change between versions.
- If it happens again on the emulator: `adb reboot` clears it.

## Steps

### 1. Reproduce
- [x] Fixture backup from a throwaway Robolectric test (`BackupService.export`, passphrase `fixture-pass`), pushed to
      the emulator's Download folder as `fintrack-fixture.ftbackup`. The throwaway test was not kept.
- [x] Debug build `-PappIdSuffix=.sheetanr`; restore flow scripted over adb (scripts kept outside the repo).
- [x] Focus, picker state and logcat captured: see Findings. Broken emulator: stuck. Fresh boot: 7 of 7 fine.

### 2. Narrow the cause
- [x] The broken emulator state (frozen transitions, leftover picker window) is the precondition; a sheet closed by
      Back (animated) still handed focus back in that state, an instant removal is what is left. The one-line
      experiments were dropped: the broken state could not be recreated on demand to run them against.

### 3. v1.5.0
- [x] Not rebuilt (owner's call to fix and test): same restore code and Material 3 version, and the freeze needs the
      broken emulator state, not a code change between versions.

### 4. Fix in `FinSheet`
- [x] `rememberFinSheet()` gives a `FinSheetControl`; `sheet.close { … }` clears focus, hides the keyboard, slides the
      sheet away and only then runs the caller's code. A second call while closing does nothing (`isClosing`).
- [x] Safety net: a sheet removed at once still clears focus and hides the keyboard as it leaves.
- [x] Every button that ends a sheet moved onto it: restore/backup passphrase, AI provider picker, Learn more ("Got
      it"), Delete everything, AI payload, statement password (Cancel), category picker, Activity filter, tax section,
      settle up / pay back, bills, cards, goals, budgets, recurring and quick add (their own `close` helpers removed).

### 5. Tests
- [x] `FinSheetTest` (Robolectric): the caller runs only once the sheet has slid away; a second tap does nothing; a sheet
      dropped at once leaves cleanly. Robolectric does not move focus inside the sheet's own window, so the keyboard
      part is checked on the emulator instead. Adds Compose `ui-test-junit4` (test only) and `ui-test-manifest` (debug
      only); nothing new ships in a release APK.
- [x] Full suite: 786 tests green (`EdgeSplitSolverTest.aBusyFortnightSolvesQuickly` took 2007 ms against 2000 on the
      first run, passed on a rerun alone).

### 6. Emulator (fixed build, fresh boot)
- [x] Restore 6 of 6 (3 by keyboard Done, 3 by the button): keyboard down at once, focus back on the app's window,
      Back works, payments restored.
- [x] AI provider picker switched twice, Learn more › Got it: sheet gone, focus back, Back works.
- [x] `.sheetanr` uninstalled. Emulator left running, freshly booted, for the health check session's own pass.

### 7. Wrap up
- [x] CHANGELOG "Unreleased" entry. Committed on `claude/reverent-cohen-3abc89`; no version bump or release.

## Out of scope
- Upgrading Compose / Material 3 (a newer sheet implementation may behave differently, but that is a bigger change).
- Any change to the backup format or restore logic.
