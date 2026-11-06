# Emulator + cross-UI test matrix

Date: 2026-10-31. This is the current evidence matrix, not a claim for every vendor runtime.

## Runtime (emulator / phone)

| Scenario | Environment | Result | Log |
|---|---|---|---|
| Screenshot | CPH2747 | **PASS** | `live event -> SCREENSHOT source=file:/storage/emulated/0/Pictures/Screenshots/...` |
| Lock screen → home | API 36 emulator, PIN 1234 | **PASS** | `keyguard faded away -> unlock fallback` / `live event -> UNLOCK` |
| Incoming call | API 36 emulator, `gsm call 12345` | **PASS** | `phone state=RINGING callEnabled=true` / `live event -> CALL` |
| Flashlight | CPH2747 | event path works; art still evolving | `EventFlashlight` |
| Alarm | CPH2747 | event path works; art redrawn | `EventAlarm` |

## Cross-UI static matrix

| UI | Keyguard class + 2 methods | AOSP screenshot executor | Screenshot file watcher |
|---|---|---|---|
| OnePlus | yes | yes | yes (shared) |
| Samsung | yes | yes | yes (shared) |
| Xiaomi | yes | yes (+ MIUI helper) | yes (shared) |
| vivo | yes | yes | yes (shared) |
| Motorola | yes | yes | yes (shared) |
| Nothing | yes | yes | yes (shared) |
| OPPO 3.0.4 | yes | no (old) | yes (shared) |
| Honor 11 | yes | no (old) | yes (shared) |

Static presence is not runtime proof. The keyguard and file-watcher paths are vendor-neutral; the AOSP
screenshot executor is a bonus source for six UIs.

## Remaining gate

- Runtime lock/call on the other seven vendor ROMs.
- Mic/location/volume sources.
- True gradient/blur flashlight glow.
- Device performance (`p95`), visual owner approval, stable release.
