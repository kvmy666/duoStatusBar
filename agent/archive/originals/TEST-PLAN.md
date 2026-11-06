# Duo Status Bar — Test Plan v2 (Multi-UI Update)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Test Plan v2 |
| Version | 1.0 — draft for owner approval |
| Date | 2026-10-26 |
| Owner | Abdulkarim |
| Author | Kilo (orchestrator) |
| Status | **Draft. No code until Requirements + this plan + PLAN-v2 are approved.** |
| Baseline | `agent/REQUIREMENTS.md` v1.0, `docs/test-cases.md`, `docs/rom-contract.md` |
| Change model | Additive. Existing cases stay valid; v2 adds multi-UI, surfaces, customization, live events, performance. |

## 1. Scope

### In scope

- 8 vendor UIs + AOSP: OnePlus OxygenOS, OPPO/realme ColorOS, Xiaomi HyperOS, Samsung One UI, vivo OriginOS, Honor MagicOS, Motorola MyOS, Nothing OS, AOSP/GSI.
- Surfaces: status bar, keyguard, shade header, QS header, QS panel/tile (only if it hosts the battery), app preview, landscape.
- Engines: Rive, Canvas, Auto fallback.
- Customization: middle slot, 4+4 dots, battery bar, icon packs, per-icon show/hide, 3 presets, live preview, backup/import.
- Live events: charging, alarm, screenshot, mic, location (P0); volume, unlock (P1).
- Cell accuracy: 4-dot mapping, dual-SIM, 5G NSA.
- Logging, repair catalog, report zip, fail-safe.
- Performance and battery budgets.

### Out of scope

- AOD and the Settings app battery.
- iOS or non-Android targets.
- Cloud sync of settings.
- Support channel implementation (Telegram is later).

## 2. Quality gates

| Gate | When | Contents | Pass condition |
|---|---|---|---|
| **G0** | now | Requirements approved | Owner approval |
| **G1** | after this doc | Test Plan approved | Owner approval |
| **G2** | after PLAN-v2 | Plan approved | Owner approval |
| **G3** | Phase 1 | Shade/QS on CPH2747 | Dump + screenshot + log; stock restored on disable |
| **G4** | per UI | Measured evidence on that UI | Dump + screenshot + log; no guessed ids |
| **G5** | Phase 7 | Performance/battery | All budgets below |
| **G6** | Phase 8 | Release | Owner approval |

## 3. Environments

| Environment | Purpose | Notes |
|---|---|---|
| CPH2747 (OnePlus 16, OxygenOS 16.0.9.400) | First real target | Wireless debugging `...245:6666`; module active in SystemUI; shade header still stock |
| AOSP emulator (`ae_api36_root`, `ae_api34_root`) | Baseline/rooted verification | `google_apis`, `adb root` expected; later phase |
| 8 vendor devices | Per-UI acceptance | One device/dump per UI; otherwise `unverified` |
| Root modes | Diagnostics | Magisk, KernelSU, APatch, Shizuku, none |
| Windows logs | User-corpus analysis | NTFS `OS` partition; read-only mount; owner action |

## 4. Test levels

| Level | What | Tool |
|---|---|---|
| L1 Unit | Pure logic: resolver, mapping, presets, backup codec, log schema | JVM tests |
| L2 Robolectric | View/hook decisions, tree fixtures, attach/hide paths | Robolectric |
| L3 Instrumentation | Real view attach/draw/restore on device | AndroidX test |
| L4 Device smoke | Per-UI status/keyguard/shade/QS | adb + screenshots |
| L5 Visual | Before/after, light/dark, Rive vs Canvas | screenshots |
| L6 Performance | Frame time, memory, battery | `gfxinfo`, `meminfo`, `batterystats` |
| L7 Fail-safe | Missing hook, missing permission, bad backup, provider down | fault injection |
| L8 Security/privacy | Redaction, no secrets, no PII | log/report audit |

## 5. Matrices

### 5.1 UI × surface

Rows: AOSP, OnePlus, ColorOS, HyperOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS.
Columns: status, keyguard, shade header, QS header, QS panel/tile, app preview, landscape.

Each cell: `PASS` / `FAIL` / `SKIP` / `UNVERIFIED` + evidence path.

### 5.2 State matrix

Battery: 0, 10, 19, 20, 50, 100.
Charging: on/off, 20% shake.
Saver: on/off.
Wi-Fi: 0-3, off, no internet.
Cell: 0-4, 4G, 5G NSA, dual-SIM (SIM1/SIM2/both).
DND, silent, airplane, Bluetooth, alarm, screenshot, volume, location, mic, download progress.
Percentage: on/off.
Middle slot: Wi-Fi, cell, battery %, clock, charging.
Layout: 4+4 dots, battery bar, Wi-Fi in charge slot.

### 5.3 Engine × renderer

Auto → Rive; Auto → Canvas on failure; forced Rive; forced Canvas.
Every visual case must pass in both Rive and Canvas, with the same state logic and geometry.

### 5.4 Colour

Light/dark; auto/black/white tint; every foreground shape follows the tint.

### 5.5 Root matrix

Magisk, KernelSU, APatch, Shizuku, none; `adb root` on the emulator.
Diagnostics must name the route and never produce an empty report.

## 6. Test cases

### 6.1 Shade / QS (Phase 1)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-001 | UI-002, UI-003 | On CPH2747, open the shade; screenshot + tree + log | Duo replaces the battery host; no stock battery under it |
| TP-002 | UI-004 | Compare the measured tree ids/classes | `quick_qs_status_icons`, `batteryRemainingIcon`, `battery_icon_view` resolved in the log |
| TP-003 | UI-005 | Capture shade and QS header in one run | Same result; one fix covers both |
| TP-004 | UI-003 | Disable the module; restart System UI | Stock battery returns exactly; screenshot |
| TP-005 | UI-008 | Force an unknown host fixture | Stock left untouched; structured trace logged |
| TP-006 | UI-009 | Lock/unlock | Same element/behaviour on keyguard |
| TP-007 | UI-010 | Expand QS panel | Header correct; panel only if it hosts the battery |
| TP-008 | TST-005 | Read the LSPosed log | `ui/surface/stage/class/id/hook/result/reason` present |
| TP-009 | UI-006 | Rotate (if allowed) | No 0x0; no half-attach; restore on failure |
| TP-010 | UI-007 | Change position/zoom | Clipped to the host; no overlap with clock |

### 6.2 Surfaces and hiding (Phase 1/7)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-011 | UI-002 | Status bar, keyguard, shade, QS | Same Duo element; no duplicate |
| TP-012 | UI-011 | Toggle each stock icon | Only the selected icons hide; others stay |
| TP-013 | UI-002 | Notification arrives | Notification icons unaffected |
| TP-014 | UI-006 | Unknown ROM fixture | No icon hidden; stock bar intact |
| TP-015 | UI-003 | Disable module | Every surface restores exactly |
| TP-016 | UI-010 | QS panel with battery | Correct host or `UNVERIFIED` |

### 6.3 Engines and customization (Phase 3)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-020 | CUS-001 | Choose Rive/Canvas/Auto | Setting persists; log shows engine |
| TP-021 | CUS-002 | Force a Rive failure | Auto switches to Canvas; reason logged |
| TP-022 | CUS-003 | Change middle slot | Live preview and bar match |
| TP-023 | CUS-004 | 4+4 dots / battery bar / Wi-Fi in charge slot | Geometry matches; no overlap |
| TP-024 | CUS-005 | Switch icon packs | Icon changes in preview and bar; fallback on bad asset |
| TP-025 | CUS-006 | Per-icon show/hide | Correct icons only |
| TP-026 | CUS-007 | Apply each preset | Preset matches; advanced remains editable |
| TP-027 | CUS-008 | Toggle preview + question mark | Static preview + simulation play |
| TP-028 | CUS-009 | First-run usability | No long text; visual-first; obvious defaults |
| TP-029 | CUS-010 | Export -> import | Round-trip identical; bad JSON rejected |
| TP-030 | CUS-011 | Navigate Home/Engine/Settings | Three pages; back stack correct |
| TP-031 | CUS-012 | Liquid glass on API 31/33/older | Effect or graceful fallback; no jank |
| TP-032 | CUS-013 | String audit | English only |
| TP-033 | UI-014 | Rive vs Canvas side by side | Same geometry/state |

### 6.4 Live events (Phase 3/6)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-040 | LIVE-001/002 | Trigger alarm, screenshot, mic, location, volume, unlock, charging | Correct icon; no wrong event |
| TP-041 | LIVE-003 | Event fires with Wi-Fi active | Wi-Fi fades; event icon appears; returns after |
| TP-042 | LIVE-004 | Per-icon motion | Motion matches the spec; Canvas clone |
| TP-043 | LIVE-005 | Permission granted/denied | Hook path first; consent path; fallback |
| TP-044 | LIVE-006 | Missing source/permission | Normal Duo state; reason logged; no crash |
| TP-045 | LIVE-007 | Idle after event | Renderer pauses; no polling |

### 6.5 Cell accuracy (Phase 4)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-050 | UI-012 | Compare phone bars vs 4 dots | Full = 4; 3/4 = 3; etc. |
| TP-051 | UI-012 | Dual-SIM | Correct per active subscription |
| TP-052 | UI-012 | 5G NSA | Reads 5G, not 4G |
| TP-053 | UI-012 | Weak/strong transitions | No flicker; monotonic mapping |
| TP-054 | UI-012 | Per-UI measurement | Mapping matches that UI's bars |

### 6.6 Performance and battery (Phase 7)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-060 | PERF-001 | Idle 5 min, screen on | 0 new frames; renderer paused |
| TP-061 | PERF-002 | Reveal + state changes | p95 ≤16.7 ms; no frame >50 ms |
| TP-062 | PERF-003 | `dumpsys meminfo` | Graphics delta ≤3 MB |
| TP-063 | PERF-004 | 8 h idle | ≤ stock +1% and ≤5 mAh; no wake locks |
| TP-064 | PERF-005 | 1 h scripted active use | ≤ stock +2% (proposed) |
| TP-065 | PERF-006 | Code audit | No polling loops |
| TP-066 | PERF-007 | Canvas path | No per-frame allocation |
| TP-067 | PERF-008 | Each claimed UI | Per-UI numbers recorded |

### 6.7 Logging, reporting, fail-safe (Phase 2)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-070 | TST-005 | Trigger a failure | Full structured line with reason |
| TP-071 | TST-006 | Fill the log | 2 MB cap; old entries rotate |
| TP-072 | TST-009 | Inspect log/report | No SSID/IMEI/serial/accounts/notification text |
| TP-073 | TST-008 | Export report | Zip with dump+logcat+screenshot+build; opens share |
| TP-074 | TST-011 | New log | Repair catalog has a matching entry |
| TP-075 | TST-001/002 | Changed code | 80% line/branch coverage |
| TP-076 | TST-003 | Resolver/hook paths | Robolectric + instrumentation pass |
| TP-077 | TST-004 | Per-UI | Same result or `UNVERIFIED` |
| TP-078 | TST-010 | Regression | Linked tests re-run |
| TP-079 | K fail-safe | Kill the hook, provider down, bad backup | Stock bar works; module off; no crash |

### 6.8 Multi-UI (Phase 7)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-080 | UI-001 | Each UI | Measured profile or `UNVERIFIED` |
| TP-081 | UI-015 | Parallel lane merge | No conflicts; phase gate green |
| TP-082 | UI-016 | Per-UI acceptance | Same behaviour; evidence path |
| TP-083 | PRC-002 | Branch/PR audit | One phase branch; no main commit |

## 7. Performance protocol

### Frame timing

```bash
adb shell dumpsys gfxinfo com.android.systemui reset
# run the case
adb shell dumpsys gfxinfo com.android.systemui framestats
```

Pass: p95 ≤16.7 ms; no frame >50 ms; idle: 0 new frames over 5 minutes.

### Memory

```bash
adb shell dumpsys meminfo com.android.systemui
```

Pass: graphics delta ≤3 MB vs module off; no growth after repeated reveals.

### Battery

```bash
adb shell dumpsys batterystats --reset
# 8 h idle, same brightness/network
adb shell dumpsys batterystats com.android.systemui
```

Pass: ≤ stock +1% and ≤5 mAh over 8 h; `Wake Locks` zero for Duo; no alarm/wakelock from the module.
Active 1 h: ≤ stock +2% under the same script (proposed).

### Log

Use the LSPosed log, not `android.util.Log` alone:

```bash
adb shell su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'
adb shell su -c 'cat <log>' | grep 'DuoSB'
```

## 8. Entry and exit criteria

### Entry per phase

- Requirements approved.
- Plan approved.
- Previous phase gate green.
- Device/emulator available and scope enabled.
- Baseline dump captured.

### Exit per phase

- Phase tasks complete.
- Tests for the touched feature pass.
- Linked tests pass.
- Dump diff empty except timestamps/pids on a working ROM.
- No crash/ANR; stock bar works with the module off.
- Evidence paths recorded.

## 9. Current status (2026-10-26)

| Item | State |
|---|---|
| CPH2747 | **Offline/unreachable** (No route to host) after the owner left; LSPosed entered safe mode on the duplicate-fix build. Rollback prepared. |
| LSPosed | Module active in SystemUI (`DuoSB` lines, pid 10266); installed shade header still stock |
| Shade/QS tree | Measured: `quick_qs_status_icons` -> `batteryRemainingIcon` -> `battery_icon_view`; `statusIcons` is `StatusIconContainer` at 0x0 before layout |
| AOSP emulator | AVDs exist (`ae_api36_root`, `ae_api34_root`); not booted; emulator test pending |
| Windows logs | NTFS not mounted; owner action |
| UIs measured | OnePlus, HyperOS (existing profiles) |
| UIs unverified | ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS |
| Rive CLI | 1.8.0 installed |
| Liquid glass | Library identified; not integrated |
| Phase 1 build | `phase/1-shade-qs`; 221 unit tests pass; safe release 6,736,680; log check 0; **device not run** |
| LSPosed safe mode | Duplicate-fix build forced removeView/dropSlot/ourViews; owner-reported safe mode; exact crash line UNKNOWN (phone offline); forcing removed; safe build untested |
| 8-UI resolver | `shadeContainerIds`/`shadeBatteryId` candidate probes built; OnePlus `shadeMeasured=true`; six runtime dumps missing |
| Vendor APKs | 8 downloaded into `corpus/`; static maps in `UI-MATRIX`; 6 lack runtime dumps |
| EOL | mixed CRLF/LF in the working tree; normalize before a real commit |

## 10. Manual owner gates

- Visual sign-off per UI.
- OEM icon removal at official launch.
- Support channel creation (later).
- Release approval.
- Real-device test on each claimed UI.

## 11. Defect workflow

1. Reproduce with the diagnostic dump + logcat + screenshot.
2. Find or add a `RC-XXX` entry in `agent/REPAIR-CATALOG.md`.
3. Confirm the exact log string in the source.
4. Fix one thing; re-run the same case.
5. Diff the dump; update the catalog tier to `measured`.
6. Add a regression test.

## 12. Traceability

| Requirement group | Test cases |
|---|---|
| UI-001..UI-016 | TP-001..TP-016, TP-080..TP-083 |
| CUS-001..CUS-014 | TP-020..TP-033 |
| LIVE-001..LIVE-007 | TP-040..TP-045 |
| RIV-001..RIV-008 | TP-033, TP-042, TP-061, TP-066 |
| TST-001..TST-012 | TP-008, TP-070..TP-079 |
| PERF-001..PERF-008 | TP-060..TP-067 |
| PRC-001..PRC-010 | TP-083 + gate audit |
| DOC-001..DOC-006 | file audit |
