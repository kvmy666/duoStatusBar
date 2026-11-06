# Evidence Protocol

## Tiers

| Tier | Meaning | Claim? |
|---|---|---|
| measured | Captured on a named device/build with the current code | Yes |
| log-confirmed | Proven by a dump/LSPosed log, not re-run after a change | Yes, with build named |
| public-source | AOSP/LineageOS source or public module | Only as a hypothesis |
| unverified | No device evidence | No |

## Sources and commands

| Need | Source | Command |
|---|---|---|
| Module log | LSPosed file | `adb shell su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'` |
| Module log lines | LSPosed | `adb shell su -c 'cat <log>' | grep 'DuoSB'` |
| logcat | adb | `adb logcat -s DuoSB` (may be empty from SystemUI on OxygenOS) |
| View tree | adb | `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` |
| Window focus/bounds | adb | `adb shell dumpsys window windows` |
| Frame timing | adb | `adb shell dumpsys gfxinfo com.android.systemui framestats` |
| Memory | adb | `adb shell dumpsys meminfo com.android.systemui` |
| Battery | adb | `adb shell dumpsys batterystats` |
| Screenshot | adb | `adb exec-out screencap -p > shot.png` |
| Static APK facts | jadx / REA | `jadx -d out app.apk`; `rea decode_android_resources` |
| Live hooks | Kahlo / Frida | `frida -U -l script.js com.android.systemui` |

## Rules

1. A case that cannot be confirmed from the LSPosed log is **not confirmed**. On OxygenOS, `android.util.Log` from SystemUI is dropped; use `L`.
2. Reproduce before fixing. Capture the failing dump/log first.
3. Change one thing. Re-run the same case. Diff the dump.
4. A dump diff on a working ROM (except timestamps/pids) is a regression.
5. If a surface cannot be resolved, leave the stock bar and log the candidate trace; never half-attach.
6. Record the exact command and the output path with every claim.
7. Redact PII: SSID, IMEI, serial, accounts, location, notification text.
8. Every new failure adds/updates an entry in `agent/REPAIR-CATALOG.md`.
9. A UI without a measured dump is `unverified`; do not claim support.
10. The `.riv` used in production must be byte-identical to a fresh build of `rive/duo/scene.rml`.

## Evidence paths

- Phase 1: `docs/evidence/v2/phase1/`
- Per-UI: `docs/evidence/v2/<ui>/`
- Performance: `docs/evidence/v2/perf/`
- Reports: in-app zip + Telegram later
