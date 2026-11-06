# Phase 1 — Shade/QS Test Sheet

Status: safe build prepared; **device verification pending**.
Branch: `phase/1-shade-qs`.
Last updated: 2026-10-27.

> **SAFE-MODE WARNING:** the previous duplicate-fix release caused LSPosed safe mode. Do **not** install it. If safe mode is active: exit safe mode, install `build/duo-phone-rollback-1.4.3.apk` first, verify the known state, then test `build/duo-shade-safe-release.apk` (untested on device) with logs. If safe mode recurs, stop and report; do not loop installs.

## Artifacts

| Artifact | Path | Notes |
|---|---|---|
| Rollback (known working before the duplicate fix) | `build/duo-phone-rollback-1.4.3.apk` | 6,712,200 bytes; same signature; use first if safe mode |
| Safe release (untested on device) | `build/duo-shade-safe-release.apk` | 6,736,680 bytes; no forced remove/reparent |
| Debug test (probe on) | `build/duo-shade-test-debug.apk` | 36,241,821 bytes; emulator/debug signature; not for the phone release |
| Source branch | `phase/1-shade-qs` | No commit |

## Prerequisites

- Android SDK emulator at `[redacted]/Android/Sdk/emulator/emulator`.
- AVD `ae_api36_root` (google_apis, x86_64, rootable).
- LSPosed (or an equivalent Xposed loader) inside the emulator. If it is missing, the SystemUI hook cannot be tested; only the app UI can.
- `adb` on PATH.

## Emulator boot

```bash
[redacted]/Android/Sdk/emulator/emulator -avd ae_api36_root -no-snapshot -no-audio -gpu swiftshader_indirect &
adb wait-for-device
adb root
adb shell getprop ro.build.version.sdk
```

If `adb root` is denied, the AVD is not the userdebug/google_apis image; stop and report.

## Check the loader

```bash
adb shell pm list packages | grep -iE 'lsposed|xposed|magisk|kernelsu|apatch'
adb shell su -c 'ls -d /data/adb/lspd /data/adb/modules 2>/dev/null'
```

If LSPosed is absent, install it on the rooted AVD or use the owner's phone later. Do not claim the shade hook passed without a loader.

## Install and scope

```bash
adb install -r build/duo-shade-test-debug.apk
```

1. Open LSPosed Manager.
2. Modules → Duo Status Bar → enable.
3. Scope → check **System UI**.
4. Save; restart System UI:
   ```bash
   adb shell su -c 'kill $(pidof com.android.systemui)'
   ```
5. Wait for SystemUI to come back; confirm:
   ```bash
   adb shell pidof com.android.systemui
   ```

## Test A — generic/AOSP shade path

This is what the emulator can prove. The OnePlus vendor hooks do not run on AOSP.

1. Open the shade:
   ```bash
   adb shell cmd statusbar expand-notifications
   ```
2. Wait 2 s; screenshot:
   ```bash
   adb exec-out screencap -p > /tmp/phase1-aosp-shade.png
   ```
3. Capture the log:
   ```bash
   adb shell su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'
   adb shell su -c 'cat <log>' | grep DuoSB
   ```
4. Pass criteria:
   - Duo is visible in the status bar and, if the AOSP build exposes a shade cluster, in the shade header.
   - The stock battery is not visible under Duo in the same slot.
   - Logs show the resolved source/container and `battery hidden`; no crash/ANR.
5. Disable the module, restart SystemUI, open the shade:
   - the stock battery/icons return exactly.

## Test B — OnePlus vendor path (phone only, deferred)

The owner said use the emulator, not the phone. Do not install the debug build over the release on CPH2747.
When the phone is allowed again:

1. Install the matching release build (same signature).
2. Enable LSPosed scope System UI; restart.
3. Open the shade; expect:
   - `OplusQSSimpleHeader.onInit` or `OplusSplitShadeHeaderControllerExImpl.init` logs;
   - `source=OplusQSSimpleHeader|OplusSplitShade`;
   - the visible cluster `quick_qs_status_icons` gets Duo; `statusIcons` + `batteryRemainingIcon` hidden;
   - stock restored on disable.
4. Capture screenshot + `DuoSB` log; add to `agent/REPAIR-CATALOG.md`.

## Failure capture

If a case fails, record:

- device/build: `getprop ro.build.fingerprint`, `ro.build.display.id`;
- surface: status/keyguard/shade/QS;
- screenshot path;
- LSPosed log path + the last `DuoSB` lines;
- the exact `source`, `container`, `battery`, `stage`, `reason` fields;
- whether the stock battery was visible, hidden, or restored.

Add a `RC-XXX` entry to `agent/REPAIR-CATALOG.md` **before** changing code. Do not guess.

## Known limitations

- The emulator cannot validate the OnePlus vendor hooks.
- The debug APK is debug-signed; do not install it over the phone's release build.
- `ShadeProbe` is enabled only in debug builds (`BuildConfig.DEBUG`).
- Six vendor UIs have static APK maps only; no runtime dumps.
- The working tree has mixed CRLF/LF; normalize before a real commit.
