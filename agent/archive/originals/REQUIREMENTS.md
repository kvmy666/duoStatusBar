# Duo Status Bar — Requirements v2 (Multi-UI Update)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Requirements v2 (multi-UI update) |
| Version | 1.0 — draft for owner approval |
| Date | 2026-10-26 |
| Owner | Abdulkarim |
| Author | Kilo (orchestrator) |
| Status | **Draft. No code until this document, TEST-PLAN, and PLAN-v2 are complete and approved.** |
| Sources | `brainstorms/2026-10-26-multi-ui-update-discovery.md`, measured CPH2747 evidence, `docs/REQUIREMENTS.md` (FR-01..28 / NFR-1..5), `agent/REPAIR-CATALOG.md` |
| Change model | Additive v2. Existing FR/NFR IDs stay valid and are referenced, not renumbered. |

## Legend

**Type:** `Feature` · `Bug` · `Refactor` · `Performance` · `Security` · `Test` · `Docs` · `Tooling`
**Priority:** `P0` blocks the phase · `P1` required for the release · `P2` can follow
**Status:** `[ ]` todo · `[~]` in progress · `[x]` done and verified · `[!]` blocked · `[u]` unverified

## Scope

Eight vendor UIs plus AOSP as the baseline:

1. OnePlus OxygenOS
2. OPPO / realme ColorOS
3. Xiaomi HyperOS / MIUI
4. Samsung One UI
5. vivo OriginOS / Funtouch
6. Honor MagicOS
7. Motorola MyOS / Hello UI
8. Nothing OS
9. AOSP / GSI (baseline)

A UI without a measured dump stays `unverified` and may not be claimed as supported.

## Non-negotiables

- Never guess a class, id, hook, or resource. Read it from the device or the APK.
- Never force/remove the stock battery or patch vendor code. Hook the existing host, draw Duo, restore exactly.
- Fail silent: a failure leaves the stock bar working and the module disabled for that surface.
- No new core architecture or hook framework. Follow the existing `DuoIconHost` / `ContainerFinder` / `RomAdapter` code paths.
- Every function that can fail must log enough to identify the UI, surface, class/function, hook, and reason.
- Every Rive design must have a Canvas clone.
- All files and file contents are English.
- No code before Requirements + Test Plan + Plan v2 are complete and approved.

---

## A. Multi-UI and surfaces

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| UI-001 | Feature | P0 | Support the 8 vendor UIs above plus AOSP; each UI has a measured profile or is marked `unverified`. | 7 | [ ] | `rom-profiles.json` + per-UI dump; no guessed ids |
| UI-002 | Feature | P0 | Duo replaces the battery and the status-icon cluster wherever the status bar shows them: status bar, keyguard, shade header, QS header. | 1, 7 | [ ] | per-surface dump + screenshot + log |
| UI-003 | Feature | P0 | Hook the existing battery host; draw Duo inside its bounds; restore the original drawing on disable. No removal, no separate overlay, no forced behaviour. | 1 | [ ] | before/after dump; original battery returns |
| UI-004 | Feature | P0 | Use the measured OnePlus QS tree: `quick_qs_status_icons` -> `icons` -> `statusIcons` (plain `LinearLayout`) + `batteryRemainingIcon` -> `battery_icon_view`. Do not assume `shade_header_system_icons` or `StatusIconContainer`. | 1 | [ ] | measured tree in `agent/REPAIR-CATALOG.md` |
| UI-005 | Feature | P0 | QS and shade are one surface for verification: the QS lives in the shade. Capture both, but a fix that works on one must work on the other. | 1 | [ ] | one probe, two captures |
| UI-006 | Feature | P0 | Same behaviour on every surface: status bar, keyguard, shade header, QS header. QS panel/tile only if it hosts the battery separately. AOD and the Settings app are out of scope. | 1, 7 | [ ] | surface matrix |
| UI-007 | Feature | P0 | Duo size/position is flexible right/left/top/bottom within the host bounds, with zoom. The same model applies to every surface, clipped to that surface. | 3 | [ ] | preview + device screenshots |
| UI-008 | Feature | P0 | If a battery/icon host cannot be resolved, leave the stock bar untouched and log a structured resolution trace (candidate ids/classes, tree path, bounds, hook attempt, reason). | 1, 7 | [ ] | `RC-010` in the repair catalog |
| UI-009 | Feature | P0 | Keyguard/lock screen uses the same element and behaviour as the status bar. | 1 | [ ] | lock/unlock cases |
| UI-010 | Feature | P1 | QS panel/tile is handled only after the header is proven and only if it hosts the battery separately. | 7 | [ ] | QS panel probe |
| UI-011 | Feature | P0 | The user controls which stock icons are hidden or shown. Do not force-hide every icon; silent/vibrate/alarm/others are per-icon choices. | 3 | [ ] | per-icon toggles + screenshot |
| UI-012 | Bug | P0 | Cell accuracy: four dots map to the system bar count; full bars on the phone must show full dots. Cover dual-SIM and 5G NSA. | 4 | [~] | raw logging + 5→4 mapper; device TP-050..054 pending · agent/PHASE-4-PLAN.md |
| UI-013 | Feature | P1 | OEM icons are extracted as-is for prototype analysis and engine testing. The owner removes them at the official launch; release builds must not rely on them. | 3 | [x] | `agent/EVENT-ICONS-PULL-REPORT.md` + `reverse/icon-packs/manifest.json` · owner sign-off still required |
| UI-014 | Feature | P0 | Every Rive design has a Canvas clone with the same state logic and geometry. | 3, 6 | [ ] | side-by-side render |
| UI-015 | Feature | P0 | Parallel per-UI work: each UI lane can be developed and tested independently, then merged into the phase branch. | 7 | [ ] | branch/PR map |
| UI-016 | Test | P0 | Acceptance is same behaviour on every supported UI. A UI without a device/dump is `unverified`, not "supported". | 7, 8 | [ ] | matrix + evidence per UI |

## B. Engines and customization

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| CUS-001 | Feature | P0 | Two engines: Rive and Canvas. User chooses; there is Auto/Recommended. | 3 | [~] | settings + renderer log · agent/PHASE-3-REPORT.md |
| CUS-002 | Feature | P0 | Auto tries Rive; on capability failure, bind timeout, or native failure, it switches to Canvas automatically. The user can force either. | 3 | [~] | reason logged; fallback screenshot · agent/PHASE-3-REPORT.md |
| CUS-003 | Feature | P0 | Middle-slot choice: Wi-Fi, cell, battery %, clock, charging number. Not only Wi-Fi/5G. | 3 | [~] | middle-slot choices implemented (mapping/channel tests); clock/charging partial until Rive visual verified · agent/PHASE-3-REPORT.md |
| CUS-004 | Feature | P0 | Layout options: 4+4 cell dots with the battery as a bar where the dots were; Wi-Fi can take the charging-number slot; battery % can take the middle. | 3 | [ ] | preview + device |
| CUS-005 | Feature | P0 | Icon packs: the user can change the shape of any icon and switch packs (e.g. Apple Wi-Fi, OnePlus Wi-Fi). OEM assets are prototype-only. | 3 | [ ] | pack list + preview |
| CUS-006 | Feature | P0 | Per-icon show/hide for stock icons; no forced hiding. | 3 | [~] | Duo component toggles (Wi-Fi glyph, cell dots) implemented (6649a56); stock-icon matrix remains partial. |
| CUS-007 | Feature | P0 | 3 ready-made presets + advanced customization. Presets are data-driven and changeable without code. | 3 | [~] | preset JSON + preview · agent/PHASE-3-REPORT.md |
| CUS-008 | Feature | P0 | Every toggle has a small static visual preview; a question mark plays a simulation of the toggle. | 3 | [ ] | UI test |
| CUS-009 | Feature | P0 | "Assume the user is easily confused": minimal text, visual-first, logical grouping, obvious defaults. | 3 | [ ] | usability review |
| CUS-010 | Feature | P0 | Settings backup/import: one plain versioned JSON file with all settings, preferences, and customization; no encryption, no secrets. Import validates and confirms before overwrite. | 3 | [~] | round-trip test · agent/PHASE-3-REPORT.md |
| CUS-011 | Feature | P0 | Three-page app with bottom navigation: Home, Engine, Settings. Backup/import lives in Settings. | 3, 5 | [~] | navigation test · agent/PHASE-3-REPORT.md |
| CUS-012 | Feature | P1 | Liquid-glass UI using `kyant0/androidliquidglass` (`io.github.kyant0:backdrop:2.0.1`); API 31+ blur, API 33+ shader, graceful fallback. | 5 | [ ] | device screenshots |
| CUS-013 | Feature | P0 | App UI is English only. | 3 | [~] | string audit · agent/PHASE-3-REPORT.md |
| CUS-014 | Feature | P2 | New sections can be added later without breaking the three-page model. | 3 | [ ] | navigation structure |

## C. Live events

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| LIVE-001 | Feature | P0 | Live event set: alarm, unlock, charging (exists), screenshot, volume up/down, location use, microphone use (exists). | 3, 6 | [ ] | event matrix |
| LIVE-002 | Feature | P0 | P0 events: charging, alarm, screenshot, mic, location. P1: volume, unlock. | 3, 6 | [ ] | priority table |
| LIVE-003 | Feature | P0 | When an event fires, the current middle occupant fades and the event icon appears with a Rive animation; Canvas clone matches. | 6 | [ ] | live capture |
| LIVE-004 | Feature | P0 | Each event icon has its own logic (e.g. alarm bounce/shake; screenshot flash; volume pulse). | 6 | [ ] | per-icon spec |
| LIVE-005 | Feature | P0 | Detection uses SystemUI hooks first. UsageStats/NotificationListener only with explicit user consent; Accessibility only if no alternative. | 3 | [ ] | permission flow |
| LIVE-006 | Feature | P0 | Missing permission or unavailable source degrades to the normal Duo state and logs the reason; no crash. | 3 | [ ] | failure cases |
| LIVE-007 | Performance | P0 | Live events are event-driven; no polling and no renderer running while idle. | 6, 7 | [ ] | `gfxinfo` + battery |

## D. Rive and animation

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| RIV-001 | Feature | P0 | Motion language: quiet luxury, short, purposeful, no idle loops. Improve the current animations; do not regress. | 6 | [ ] | design review |
| RIV-002 | Feature | P0 | Charging: a very subtle, almost invisible pulse travels from 0 to the current level over the green; when it reaches the level, it pulses. | 6 | [ ] | headless render + device |
| RIV-003 | Feature | P0 | At 20% charging, the Duo icon shakes (~200 ms). | 6 | [ ] | headless render |
| RIV-004 | Feature | P0 | Each live event has a defined motion (alarm bounce/shake, screenshot flash, volume pulse, etc.). | 6 | [ ] | spec + render |
| RIV-005 | Feature | P0 | Every Rive design has a Canvas clone with the same states and geometry. | 3, 6 | [ ] | side-by-side |
| RIV-006 | Tooling | P1 | Rive CLI 1.8.0 RML authoring; headless `--verify`, `--screenshot`, `--bench`, `--data`, `--advance`. | 0, 6 | [x] | `weapons/INVENTORY.md` |
| RIV-007 | Performance | P0 | Pause the renderer when hidden/idle; restart on a real change. | 6, 7 | [ ] | `gfxinfo`, battery |
| RIV-008 | Test | P0 | Headless pose screenshots per state + on-device screenshots; no unverified motion. | 6 | [ ] | evidence paths |

## E. Testing, logging, reporting

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| TST-001 | Test | P0 | Write tests for code that has none; every new function gets unit + path tests. | 2 | [ ] | coverage report |
| TST-002 | Test | P0 | Path coverage gate on changed code: 80% lines and branches. | 2 | [ ] | CI report |
| TST-003 | Test | P0 | Robolectric for resolver/hook decisions; instrumentation for attach/hide; on-device smoke per UI. | 2 | [ ] | test matrix |
| TST-004 | Test | P0 | Tests must pass on every supported UI with the same result; otherwise the UI is `unverified`. | 2, 7 | [ ] | per-UI evidence |
| TST-005 | Feature | P0 | Every function that can fail logs: `ui`, `surface`, `stage`, `class`, `id`, `hook`, `result`, `reason`, `detail`, `build`. | 2 | [ ] | log schema test |
| TST-006 | Feature | P0 | Log retention: ring file capped at 2 MB, last N sessions; redact PII; verbose in debug, errors in release; one line per state change, never per frame. | 2 | [ ] | log audit |
| TST-007 | Feature | P0 | Everything useful for repair is logged, no matter how small, within the cap and redaction rules. | 2 | [ ] | repair catalog coverage |
| TST-008 | Feature | P0 | In-app report produces a zip with dump + logcat + screenshot + build; share sheet; Telegram support channel later. | 2 | [ ] | report round-trip |
| TST-009 | Security | P0 | No PII in logs/reports: no SSID, IMEI, serial, accounts, location, notification text. | 2 | [ ] | redaction test |
| TST-010 | Test | P0 | Regression/linked tests: B↔C↔D, F↔B, H↔all. | 2, 7 | [ ] | test plan |
| TST-011 | Docs | P0 | `agent/REPAIR-CATALOG.md` maps log signature -> cause -> evidence -> tool -> fix -> verification; every new log adds an entry. | 2 | [x] | catalog |
| TST-012 | Test | P0 | Test matrix: 9 UI rows × surfaces × states × renderers × light/dark. | 2, 7 | [ ] | matrix in TEST-PLAN |

## F. Performance and battery

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| PERF-001 | Performance | P0 | Idle: 0 rendered frames; renderer paused when hidden/idle. | 7 | [ ] | `gfxinfo` frame count |
| PERF-002 | Performance | P0 | Animation: p95 frame time ≤16.7 ms; no frame >50 ms. | 7 | [ ] | `gfxinfo framestats` |
| PERF-003 | Performance | P0 | Graphics memory delta ≤3 MB for the Duo views. | 7 | [ ] | `dumpsys meminfo` |
| PERF-004 | Performance | P0 | Battery: 8 h idle ≤ stock +1% and ≤5 mAh, no wake locks. | 7 | [ ] | `dumpsys batterystats` |
| PERF-005 | Performance | P0 | Active use: 1 h scripted use ≤ stock +2% under the same script (proposed; owner to confirm). | 7 | [ ] | `batterystats` delta |
| PERF-006 | Performance | P0 | No polling loops; all updates are event/broadcast driven. | 7 | [ ] | code audit |
| PERF-007 | Performance | P0 | No per-frame allocation in the Canvas path; Rive reuse. | 7 | [ ] | heap/allocation trace |
| PERF-008 | Performance | P0 | Measure every supported UI, not only OnePlus; record device/build. | 7 | [ ] | per-UI perf table |

## G. Process and quality

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| PRC-001 | Docs | P0 | Order: Brainstorm -> Requirements -> Test Plan -> Plan v2 -> owner approval -> code. | 0 | [~] | this document |
| PRC-002 | Process | P0 | One branch per phase; never commit to `main`; merge only after the phase gate. | all | [ ] | git log/PRs |
| PRC-003 | Process | P0 | No new core architecture or hook framework; follow the old code paths. App-only UI libraries allowed; a new core dependency needs a written reason and approval. | all | [ ] | code review |
| PRC-004 | Process | P0 | Evidence over guessing; use the tools; reproduce before fixing; diff the dump. | all | [x] | AGENTS.md |
| PRC-005 | Process | P0 | One-shot clean code; no rushed code; quality over speed. | all | [ ] | review |
| PRC-006 | Process | P0 | Cheapest adequate model: BEAM for non-sensitive work; DeepSeek for architecture/code/debug; no GLM. | all | [x] | memory + agents |
| PRC-007 | Docs | P0 | All files and file contents in English. | all | [x] | this document |
| PRC-008 | Process | P0 | Owner approves every release; betas are pre-releases. | 8 | [ ] | release checklist |
| PRC-009 | Process | P1 | OEM assets are local/prototype only; the owner removes them at the official launch; no redistribution commitment. | 3, 8 | [ ] | owner sign-off |
| PRC-010 | Process | P0 | Any new requirement observed during development is added here before implementation. | all | [ ] | changelog |

## H. Documentation and release

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| DOC-001 | Docs | P0 | `agent/AGENT.md` is the session anchor and is read first. | 0 | [ ] | file |
| DOC-002 | Docs | P0 | `agent/README.md` indexes the folder and read order. | 0 | [ ] | file |
| DOC-003 | Docs | P0 | UI matrix with 8 vendor UIs + AOSP, evidence tiers, acquisition protocol. | 0, 7 | [ ] | file |
| DOC-004 | Docs | P0 | Repair catalog is maintained with every new log. | 2 | [x] | `agent/REPAIR-CATALOG.md` |
| DOC-005 | Docs | P1 | Backup JSON schema/version documented. | 3 | [ ] | schema |
| DOC-006 | Docs | P1 | Release notes + README update for the multi-UI update. | 8 | [ ] | release |

---

## Traceability to phases and weapons

No implementation without a weapon. If the weapon cannot confirm the claim, the status is `unverified`.

| Phase | Branch | Requirements | Weapons / tools |
|---|---|---|---|
| 0 — Docs, tools, evidence | `phase/0-docs-evidence` | PRC-001, PRC-007, DOC-001..004, RIV-006 | REA (`open_binary`, `inspect_android_package`, `decode_android_resources`), JADX 1.5.6, Apktool 3.0.3, Rive CLI 1.8.0, git, Gradle |
| 1 — Shade/QS battery host | `phase/1-shade-qs` | UI-002..UI-006, UI-008, UI-009 | REA adb (`list_adb_devices`, `inspect_adb_window`, `inspect_adb_package`, `capture_adb_screen`), JADX (`search_android_classes`, `inspect_android_class`, `inspect_android_method`), Apktool resources, module `Diag` + `ShadeProbe`, `uiautomator`, `dumpsys window`, `adb install`, LSPosed log |
| 2 — Testing/logging/repair | `phase/2-testing-logs` | TST-001..TST-012 | Gradle `testDebugUnitTest`, Robolectric, instrumentation, REA adb/logcat, Python log parser, `agent/REPAIR-CATALOG.md` |
| 3 — App, engines, customization, backup | `phase/3-app-customization` | CUS-001..CUS-011, CUS-013, LIVE-001..LIVE-007, PERF-004..007 | Gradle/Compose, Rive CLI, adb screenshots, JSON unit tests, Robolectric, Frida/Kahlo MCP, REA Frida/adb |
| 4 — Cell accuracy | `phase/4-cell-accuracy` | UI-012 | REA adb, `dumpsys telephony.registry`, `dumpsys connectivity`, module logs, JADX for `SignalStrength` |
| 5 — Liquid glass + 3 pages | `phase/5-liquid-glass` | CUS-011, CUS-012 | Gradle/Compose, `dumpsys gfxinfo`, adb screenshots |
| 6 — Rive animations | `phase/6-rive` | RIV-001..RIV-008 | Rive CLI (`--verify`, `--screenshot`, `--bench`, `--data`), Canvas tests, adb screenshots |
| 7 — Multi-UI parallel + performance | `phase/7-multi-ui-perf` | UI-001, UI-007, UI-010, UI-014..016, PERF-001..008 | REA adb per UI, JADX/Apktool per SystemUI, `gfxinfo`/`meminfo`/`batterystats`, screenshots |
| 8 — Release | `phase/8-release` | PRC-008, PRC-009, DOC-005, DOC-006 | Gradle, git, Rive hash compare, log check scripts, owner approval |

## Weapons per requirement group

| Requirements | Implementation | Weapons / tools |
|---|---|---|
| UI-001..UI-016 | Multi-UI host resolution, surface attach, measurement | REA adb (`list_adb_devices`, `inspect_adb_window`, `inspect_adb_package`, `capture_adb_screen`), JADX (`search_android_classes`, `inspect_android_class`, `inspect_android_method`), Apktool (`decode_android_resources`), module `Diag` + `ShadeProbe`, `uiautomator`, `dumpsys window/activity`, `adb install`, LSPosed log |
| CUS-001..CUS-014 | Engines, customization, backup, 3 pages | Gradle/Compose, Rive CLI (`--verify`, `--screenshot`, `--bench`), adb screenshots, JSON unit tests, Robolectric, `dumpsys gfxinfo` |
| LIVE-001..LIVE-007 | Live event detection and animation | Frida/Kahlo MCP, REA Frida/adb, SystemUI hooks, `dumpsys`, logcat, consent flow tests |
| RIV-001..RIV-008 | Animation authoring and parity | Rive CLI (`--verify`, `--screenshot`, `--bench`, `--data`), Canvas tests, adb screenshots |
| TST-001..TST-012 | Tests, logs, repair catalog | Gradle `testDebugUnitTest`, Robolectric, instrumentation, REA adb, Python log parser, `agent/REPAIR-CATALOG.md` |
| PERF-001..PERF-008 | Performance and battery | `dumpsys gfxinfo ... framestats`, `dumpsys meminfo`, `dumpsys batterystats`, REA adb, per-UI scripts |
| PRC-001..PRC-010 | Process and quality | git, review-strict, `agent/REPAIR-CATALOG.md`, REA/JADX evidence |
| DOC-001..DOC-006 | Docs and release | git, markdown, Rive hash compare, log check scripts |

## Locked decisions

1. Hook the existing battery host; do not force/remove or overlay.
2. QS and shade are one verification surface.
3. Measured OnePlus QS tree is authoritative for this build.
4. Every Rive design has a Canvas clone.
5. Auto fallback: Rive -> Canvas on failure; user can force either.
6. App UI and support channel are English only.
7. Backup is a plain versioned JSON, no encryption.
8. No new core architecture; follow the old code paths.
9. 8 vendor UIs + AOSP; unmeasured UI stays `unverified`.
10. No code before Requirements + Test Plan + Plan v2 are approved.

## Open flags

- Windows "Duo logs" folder is not mounted; owner must run the read-only NTFS mount.
- CPH2747 module is active in SystemUI; the shade header still shows the stock battery (measured 2026-10-27).
- Support channel is later; no platform details yet.
- Tester availability per UI is not yet confirmed; those UIs stay `unverified`.
- All eight vendor SystemUI APKs are acquired in `corpus/` (static maps in `agent/UI-MATRIX.md`); runtime dumps for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS are still missing, so those UIs remain `unverified`.
- PERF-005 active-use +2% is proposed; owner must confirm.

## Acceptance gates

- **G0** Requirements approved.
- **G1** Test Plan approved.
- **G2** Plan v2 approved.
- **G3** Phase 1 shade/QS: measured dump + screenshot + log on CPH2747; stock battery restored on disable.
- **G4** Per-UI: measured evidence for each claimed UI.
- **G5** Performance/battery budgets pass.
- **G6** Owner release approval.
