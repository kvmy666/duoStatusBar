# Emulator lock → home test — PASS

Date: 2026-10-31. AVD `ae_api36_root` (API 36), LSPosed, SystemUI stage 2.

## Setup

- LSPosed module `io.github.kvmy666.duostatusbar` (`mid=45`) was disabled.
- Corrected:
  - `enabled=1`
  - `apk_path` refreshed to the installed APK
  - scope `45 | com.android.systemui | 0`
  - `duo_statusbar_stage=2`
- SystemUI restarted.

## Hooks installed

```
DuoSB: shade/QS coverage hook installed
DuoSB: bar appearance hook installed (StatusBarIconView) - FR-15b
DuoSB: flashlight event hook installed
DuoSB: screenshot event hook installed
DuoSB: keyguard unlock hook installed
```

## Transition test

- Set a real PIN (`locksettings set-pin 1234`).
- Locked/unlocked with the PIN.
- Log:

```
keyguard unlock hook installed
keyguard faded away -> unlock fallback
unlock received enabled=true active=null
live event -> UNLOCK
```

This is the lock screen → home transition, not an unlock API shortcut. The keyguard hook fires and the
UNLOCK event reaches the renderer.

## Scope

- The hook is `StatusBarKeyguardViewManager.onKeyguardFadedAway` with
  `notifyKeyguardAuthenticated(true)` as the first path. This is the AOSP-derived class present in six of
  the eight UI APKs; the other UIs still need their own runtime check before claiming support.
- Call/ringing remains unverified.
- Screenshot is separately verified through the shared `ScreenshotWatcher` file source.
