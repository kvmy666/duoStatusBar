# Duo Status Bar — Implementation Plan v2 (Multi-UI Update)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Implementation Plan v2 |
| Version | 1.0 — draft for owner approval |
| Date | 2026-10-26 |
| Owner | Abdulkarim |
| Author | Kilo (orchestrator) |
| Status | **Draft. No code until Requirements + Test Plan + this plan are approved.** |
| Inputs | `agent/REQUIREMENTS.md` v1.0, `agent/TEST-PLAN.md` v1.0, `agent/REPAIR-CATALOG.md`, measured CPH2747 evidence |
| Change model | Additive. No new core architecture or hook framework. Follow the existing `DuoIconHost` / `ContainerFinder` / `RomAdapter` patterns. |

## 1. Objective

Ship a stable multi-UI update that:

1. puts Duo where the stock battery/status icons live, starting with the shade/QS header on CPH2747;
2. adds a full testing/logging/repair layer;
3. rebuilds the app into three pages with full Duo customization, two engines, backup/import, and live event icons;
4. fixes cell accuracy;
5. adds liquid glass and the Rive animation system;
6. runs with the same behaviour on 8 vendor UIs + AOSP, with measured evidence.

Success is **not** "it compiles". Success is: the touched feature passes its tests, the stock bar still works with the module off, and the measured evidence exists for the claimed UI.

## 2. Principles

- Evidence over guessing. No class/id/hook is invented.
- Reproduce before fixing. A bug needs a dump/log that shows it.
- One change at a time; diff the diagnostic dump.
- Fail silent: leave the stock bar working, log the reason, disable the surface.
- One property, one owner.
- Every Rive design has a Canvas clone.
- No new core architecture. Additive strategies inside the existing code paths.
- Every function that can fail logs enough to identify UI/surface/class/hook/reason.
- Performance and battery are acceptance criteria, not cleanup.
- No code before the three documents are approved.

## 3. Branch model

- One branch per phase: `phase/N-name`.
- UI sub-branches for parallel lanes: `phase/N/<ui-key>`; merge back into the phase branch only after the lane gate.
- Never commit to `main`. Merge only after the phase gate and owner approval.
- A phase branch starts from the previous phase branch (or `main` if the previous is merged).
- Keep the working tree clean per phase; do not mix unrelated changes.

## 4. Phase overview

| Phase | Branch | Goal | Parallel lanes | Exit gate | Weapons / tools |
|---|---|---|---|---|---|
| 0 | `phase/0-docs-evidence` | Docs, tools, evidence baseline | Docs / tools | G0-G2; docs complete | REA, JADX, Apktool, Rive CLI, git, Gradle |
| 1 | `phase/1-shade-qs` | Duo replaces the battery/icon cluster in shade/QS on CPH2747 | Probe / resolver / renderer | G3: dump+screenshot+log; stock restored | REA adb + JADX + Apktool, module `ShadeProbe`/`Diag`, `uiautomator`, `dumpsys`, LSPosed log, Gradle |
| 2 | `phase/2-testing-logs` | Tests for untested code; structured logs; report zip; repair catalog | Unit / Robolectric / device / logging | G4: coverage + report round-trip | Gradle test, Robolectric, instrumentation, REA adb/logcat, Python parser |
| 3 | `phase/3-app-customization` | 3-page app, engines, customization, backup/import, live events | UI / engine / backup / live | G4: app cases pass | Gradle/Compose, Rive CLI, JSON tests, Frida/Kahlo, REA adb |
| 4 | `phase/4-cell-accuracy` | 4-dot mapping matching system bars | Mapping / dual-SIM / 5G | G4: C-7/C-8/C-11 | REA adb, `dumpsys telephony.registry`, `dumpsys connectivity`, JADX |
| 5 | `phase/5-liquid-glass` | Liquid glass + final 3-page shell | UI / fallback | G4: API 31/33/older | Gradle/Compose, `gfxinfo`, screenshots |
| 6 | `phase/6-rive` | Animation system, Canvas parity | Rive / Canvas / perf | G4: headless + device | Rive CLI (`--verify/--screenshot/--bench/--data`), Canvas tests, adb |
| 7 | `phase/7-multi-ui-perf` | 8 UIs + AOSP, parallel lanes, performance/battery | Per-UI lanes | G5: per-UI evidence + budgets | REA adb per UI, JADX/Apktool, `gfxinfo`/`meminfo`/`batterystats` |
| 8 | `phase/8-release` | Release hardening/docs | Docs / release | G6: owner approval | Gradle, git, Rive hash, log checks, owner approval |

### Weapons per phase (execution map)

- **P0:** `rea_open_binary`, `rea_inspect_android_package`, `rea_decode_android_resources`; `jadx` CLI; `apktool`; `rive --verify/--screenshot`; `git`; Gradle.
- **P1:** `rea_list_adb_devices`, `rea_inspect_adb_window`, `rea_inspect_adb_package`, `rea_capture_adb_screen`; `rea_search_android_classes`, `rea_inspect_android_class`, `rea_inspect_android_method`; module `ShadeProbe`/`Diag`; `uiautomator dump`; `dumpsys window`; LSPosed log; `adb install`; Gradle.
- **P2:** `./gradlew :app:testDebugUnitTest`; Robolectric; instrumentation; `rea_read_adb_logcat`; Python log parser; `agent/REPAIR-CATALOG.md`.
- **P3:** Gradle/Compose; Rive CLI; JSON backup unit tests; Robolectric; Frida/Kahlo MCP for live events; `rea_capture_adb_screen`.
- **P4:** `rea_inspect_adb_*`; `dumpsys telephony.registry`; `dumpsys connectivity`; JADX `SignalStrength`; module logs.
- **P5:** Gradle/Compose; `dumpsys gfxinfo`; `rea_capture_adb_screen`.
- **P6:** `rive --verify`, `rive --screenshot`, `rive --bench`, `rive --data`; Canvas tests; `rea_capture_adb_screen`.
- **P7:** per-UI `rea_*` + JADX/Apktool; `dumpsys gfxinfo/meminfo/batterystats`; screenshot/dump matrix.
- **P8:** Gradle, git, Rive hash compare, log checks, owner approval.

## 5. Phase 0 — Docs, tools, evidence

**Branch:** `phase/0-docs-evidence`

Tasks:
- [x] Requirements v1.0.
- [x] Test Plan v1.0.
- [x] Repair catalog seed.
- [x] Weapons inventory and tool installs.
- [ ] `UI-MATRIX.md`.
- [ ] `AGENT.md`, `README.md`, `BRAINSTORM.md`, `MODEL-ROUTING.md`, `EVIDENCE.md`.
- [ ] Owner approval on all three core docs.

Exit: G0-G2; no code yet.

## 6. Phase 1 — Shade/QS battery host

**Branch:** `phase/1-shade-qs`

**Goal:** on CPH2747, Duo replaces the stock battery/status-icon cluster in the shade/QS header, with the same element and behaviour as the status bar. No force/remove; restore exactly.

**Status (2026-10-27):** safe build complete on `phase/1-shade-qs`; 221 unit tests pass; release builds; log check clean. The previous duplicate-fix build caused LSPosed safe mode (forced remove/reparent); that path is removed. **Device gate pending** — phone offline; test sheet in `agent/PHASE-1-TEST.md`; rollback in `build/duo-phone-rollback-1.4.3.apk`.

**Measured facts (CPH2747, 2026-10-26):**
- Real QS cluster: `quick_qs_status_icons` -> `icons` -> `statusIcons` (plain `LinearLayout`) + `batteryRemainingIcon` -> `battery_icon_view`.
- `batteryRemainingIcon` bounds `[1116,153][1202,254]`; `battery_icon_view` bounds `[1119,178][1201,229]`.
- The old path looked for `shade_header_system_icons` and `StatusIconContainer.addView`; neither matches this build.
- LSPosed log shows the module active in SystemUI (`DuoSB` lines, pid 10266); the shade header still shows the stock battery. No scope blocker.

**Tasks:**
1. [x] Confirm the module is active in SystemUI (`DuoSB` lines under `(com.android.systemui)`, pid 10266). No scope blocker.
2. [ ] Capture baseline: shade/QS screenshot, `uiautomator dump`, `dumpsys window`, LSPosed log. Save under `docs/evidence/v2/phase1/`.
3. [ ] Add a **debug-only read-only probe**: log `quick_qs_status_icons`, `batteryRemainingIcon`, `battery_icon_view`, their bounds, and the `ViewGroup.addView` route for the shade header. No behaviour change; no hiding.
4. [ ] Extend `ContainerFinder.findShadeIconsArea` additively:
   - prefer `quick_qs_status_icons`;
   - fall back to `shade_header_system_icons`;
   - fall back to the current `statusIcons` parent;
   - log every candidate and the chosen one.
5. [ ] Add a small `BatteryHostResolver` (additive helper, existing package/style) that returns the actual battery host and its draw point:
   - `batteryRemainingIcon` (container);
   - `battery_icon_view` (ImageView/drawable);
   - candidate classes/ids/tree path/bounds;
   - a structured trace on failure.
6. [ ] **Host rendering spike** (evidence before code):
   - Canvas: custom `Drawable` inside the battery ImageView; draw Duo, suppress the stock draw, restore.
   - Rive: determine whether a child view can live inside `batteryRemainingIcon` without a window overlay; if not, keep Canvas for this host and log the limitation.
   - Record the decision and the reason in `agent/DECISIONS.md`.
7. [ ] Implement the chosen host binding:
   - draw Duo in the host bounds;
   - hide only the stock draw inside that host;
   - restore exactly on disable;
   - no forced removal, no separate window overlay;
   - same element/geometry as the status bar.
8. [ ] Log: `ui=oneplus surface=shade|qs stage=resolve|hook|attach|draw|hide|restore class=<fqcn> id=<id> hook=<method> result=<ok|fail|skip> reason=<code> detail=<bounded> build=<fingerprint>`.
9. [ ] Tests:
   - `BatteryHostResolverTest` with the measured tree fixture;
   - `ContainerFinderTest` for the new fallback order;
   - Robolectric attach/hide/restore;
   - on-device TP-001..TP-010.
10. [ ] Evidence: before/after screenshot, dump diff, log; stock battery visible with the module off.
11. [ ] Do **not** touch QS tiles, AOD, or the Settings app in this phase.

**Files likely touched (confirm before editing):**
- `app/src/main/java/.../hook/DuoHook.kt`
- `app/src/main/java/.../hook/DuoIconHost.kt`
- `app/src/main/java/.../hook/ContainerFinder.kt`
- `app/src/main/java/.../hook/Diag.kt`
- `app/src/main/assets/rom-profiles.json` (OnePlus profile notes only)
- tests under `app/src/test/.../hook/`
- `agent/REPAIR-CATALOG.md`, `docs/evidence/v2/phase1/`

**Exit gate (G3):**
- Duo replaces the battery/icon cluster in shade/QS on CPH2747.
- Stock battery returns exactly when the module is disabled.
- No crash/ANR; dump diff empty except timestamps/pids.
- Screenshot + tree + log archived.
- If the host cannot be resolved: stock left untouched, trace logged, status `unverified`; no half-attach.

**Risks:**
- The battery host draws through a drawable, not `onDraw`; the spike must confirm the actual draw point.
- Rive may require a child view; no separate window overlay. If it cannot be done safely, Canvas is the Phase 1 renderer for this host, with a logged limitation.
- The ROM may repopulate the cluster; the hide must re-apply on the actual insertion route.
- The shade header attach/resolve path is still unknown; the module is active in SystemUI.

## 7. Phase 2 — Testing, logging, repair

**Branch:** `phase/2-testing-logs`

Tasks:
- [ ] Add `DuoLog` structured schema (`ui/surface/stage/class/id/hook/result/reason/detail/build`).
- [ ] Add a 2 MB ring log + session cap; redact PII.
- [ ] Add unit/path tests for untested code; 80% changed-code lines/branches.
- [ ] Add Robolectric/instrumentation for resolver/attach/hide.
- [ ] Add report zip (dump + logcat + screenshot + build) and share sheet.
- [ ] Extend `agent/REPAIR-CATALOG.md` with every new reason code.
- [ ] Add CI gates: tests, logs check, Rive check, authority grep, `.riv` hash.
- [ ] Run TP-070..TP-079.

Exit: coverage + report round-trip + no PII + catalog coverage.

## 8. Phase 3 — App, engines, customization, backup, live events

**Branch:** `phase/3-app-customization`

Tasks:
- [ ] 3-page shell: Home / Engine / Settings; bottom navigation.
- [ ] Engine selector: Rive / Canvas / Auto; Auto fallback reason logged.
- [ ] Middle slot: Wi-Fi, cell, battery %, clock, charging.
- [ ] Layouts: 4+4 dots; battery bar in the cell position; Wi-Fi in the charging slot.
- [ ] Icon packs: per-state selection; OEM packs local/prototype; fallback.
- [ ] Per-icon show/hide for stock icons.
- [ ] 3 presets + advanced + live preview box.
- [ ] Toggle previews + question-mark simulation.
- [ ] Backup/import: plain versioned JSON; all settings/preferences/customization; validate + confirm.
- [ ] Live events: alarm, screenshot, mic, location (P0); volume, unlock (P1); hooks first; consent for UsageStats/NotificationListener; fallback.
- [ ] Every Rive design gets a Canvas clone.
- [ ] English-only strings.
- [ ] Run TP-020..TP-045.

Exit: app cases pass on CPH2747; backup round-trip; no crash.

## 9. Phase 4 — Cell accuracy

**Branch:** `phase/4-cell-accuracy`

Tasks:
- [ ] 4-dot mapping to the system bar count.
- [ ] Dual-SIM: active subscription per slot.
- [ ] 5G NSA: radio NR state, not LTE data type.
- [ ] Per-UI override only if the measurement requires it (the owner asked for 4-dot mapping first).
- [ ] Log raw level/max/network type per update.
- [ ] Run TP-050..TP-054.

Exit: full bars on the phone show full dots on CPH2747; dual-SIM/5G cases pass.

**Status (2026-10-28):** `phase/4-cell-accuracy` created from the Phase 3 prototype merge
(`10f9ac7`). Raw cellular logging and the stock-level-to-4-dot mapper are implemented (318 tests);
device TP-050..TP-054 remain pending.

## 10. Phase 5 — Liquid glass + three-page shell

**Branch:** `phase/5-liquid-glass`

Tasks:
- [ ] Add `io.github.kyant0:backdrop:2.0.1` (Apache-2.0).
- [ ] Apply to the app surfaces only, never SystemUI.
- [ ] API 31+ blur; API 33+ shader; graceful fallback below 31.
- [ ] Measure jank/memory; cap blur radius.
- [ ] Finalize Home/Engine/Settings layout.
- [ ] Run TP-030..TP-032.

Exit: visual approval + no jank on CPH2747.

**Status (2026-10-30):** **Phase 5 prototype locked** at beta.14 (code 44). The slime selector,
glass toggles/sliders, scroll edges, Apple tokens and depth shadow are frozen; further changes belong to
Phase 6/7. Device visual sign-off is still pending because CPH2747 was offline.

## 11. Phase 6 — Rive animation system

**Branch:** `phase/6-rive`

Tasks:
- [ ] Author RML with Rive CLI 1.8.0; `--verify`, `--screenshot`, `--bench`.
- [ ] Charging pulse from 0 to level; pulse at level.
- [ ] 20% charge shake (~200 ms).
- [ ] Alarm bounce/shake; screenshot flash; volume pulse; location/mic state.
- [ ] Every design gets a Canvas clone.
- [ ] Pause renderer when idle/hidden; restart on change.
- [ ] Run TP-033, TP-042, TP-061, TP-066.

Exit: headless poses + on-device screenshots; no regression of existing animations.

**Status (2026-10-30):** branch `phase/6-rive`; charging settle pulse (Rive state `0:261` +
`ChargeSettle` animation), renderer-neutral 20 % shake (`ChargeMotion` + `DuoIconHost.shakeAll`),
headless idle/charge screenshots and `duo.riv` hash `636e8c9a…` recorded. Alarm/flashlight/screenshot
motions remain; mic/location/volume sources are still unmeasured. Emulator/device runs pending.

## 12. Phase 7 — Multi-UI parallel + performance

**Branch:** `phase/7-multi-ui-perf`

Parallel lanes (one per UI):
- `phase/7/aosp`
- `phase/7/oneplus`
- `phase/7/coloros`
- `phase/7/hyperos`
- `phase/7/oneui`
- `phase/7/originos`
- `phase/7/magicos`
- `phase/7/myos`
- `phase/7/nothingos`

Rules:
- **Acquisition gate:** all 8 vendor SystemUI APKs are now local in `corpus/` (hashes in `corpus/index.json`), and their static maps are in `agent/UI-MATRIX.md` (per-UI header map). Runtime dumps are still missing for 6 UIs, so those lanes stay `UNVERIFIED` until a device dump exists.
- Shared core changes land first (resolver/profile schema); lanes only add profiles/adapters/tests.
- A lane may not edit another lane's files.
- Each lane produces a measured dump + screenshot + log or `UNVERIFIED`.
- Merge lanes into the phase branch after the per-UI gate.

Tasks:
- [ ] **Profile schema v2 (shared core, first):** add optional `shadeContainerIds`, `shadeBatteryId`, `shadeStatusIconId` to `RomAdapter` (default empty); parse them in `RomProfiles`; keep the old status-bar path unchanged.
- [ ] **Per-UI static maps:** OnePlus (measured status + `quick_qs_status_icons`; `combined_qs_header` fallback), Samsung (`shade_header_system_icons` + `system_icons`), Xiaomi (`system_icons` measured + `shade_header_system_icons`), vivo (`shade_header_system_icons` + `system_icons`), Motorola/Nothing (`shade_header_system_icons` + `system_icons`), OPPO (`quick_qs_status_icons` + `system_icons`; old APK), Honor (`hw_system_icons`/`system_icon_area`; old APK). Mark all unmeasured maps `measured=false` / `UNVERIFIED`.
- [ ] `ContainerFinder.findShadeIconsArea` uses the profile-specific shade IDs first, then the common `quick_qs_status_icons` -> `shade_header_system_icons` -> `system_icons` fallbacks; log every candidate.
- [ ] Unit tests for every profile map and the fallback order.
- [ ] Per-UI battery/status host resolution.
- [ ] Per-UI cell mapping.
- [ ] Per-UI performance/battery numbers.
- [ ] Run TP-080..TP-083, TP-060..TP-067.

**Status (2026-10-27):** profile schema, 8 static maps, and candidate resolver probes are built and unit-tested; OnePlus is `shadeMeasured=true`. Runtime dumps for six UIs are still missing, so the per-UI lanes remain `UNVERIFIED`; no device claim yet.

**Status (2026-10-31):** branch `phase/7-multi-ui-perf`; 9 profiles in the shipped asset (2 measured, 7 static-only), `check-perf.py` added and header-corrected. Emulator: all-live glass ANR; static controls/surfaces remove the ANR but the API 36 emulator still measures p95 **216.88 ms** (budget 32 ms), so the Phase 7 performance gate **fails** on the emulator and is open on CPH2747. Screenshots and crash logs in `docs/evidence/phase7/`; report `agent/PHASE-7-REPORT.md`.

Exit: 8 UIs + AOSP with evidence; budgets pass; unknown UI left untouched.

## 13. Phase 8 — Release

**Branch:** `phase/8-release`

Tasks:
- [ ] README/release notes.
- [ ] Version bump.
- [ ] Full gates: unit, instrumentation, per-UI evidence, perf/battery, `.riv` hash, logs check.
- [ ] Owner approval.
- [ ] Beta pre-release only; no store until approved.
- [ ] Remove OEM assets at the owner's chosen time before official launch.

Exit: G6 owner approval.

**Status (2026-10-31):** branch `phase/8-release`; beta.20 (code 50) built and relayed, 322 unit tests
pass, `.riv`/APK hashes recorded, log and easing gates pass. Stable release is **blocked**: device
visual/perf run, frame budget and owner approval are open. See `agent/PHASE-8-REPORT.md` and
`agent/WALKTHROUGH-REVIEW-1.5.0.md`.

## 14. Function inventory (names are proposals; confirm before coding)

| Function / class | Purpose | Inputs / outputs | Logging | Tests | Phase |
|---|---|---|---|---|---|
| `ContainerFinder.findShadeIconsArea` (extend) | Resolve the shade/QS cluster | header view -> ViewGroup? | candidates + chosen | unit + Robolectric | 1 |
| `BatteryHostResolver.resolve` | Find the battery host/draw point | root + surface -> host? | candidates, tree path, bounds, reason | unit fixture | 1 |
| `BatteryHostBinding.attach` | Draw Duo in the host; hide stock draw | host + renderer -> binding | stage/result/reason | Robolectric + device | 1 |
| `BatteryHostBinding.restore` | Put the stock drawing back | binding -> void | result | device | 1 |
| `QsHeaderProbe.install` | Debug-only addView/tree probe | window -> void | class/id/parent/bounds | device | 1 |
| `DuoLog.event` | Structured log | fields -> void | schema | unit | 2 |
| `DuoReport.export` | zip dump+logcat+screenshot+build | context -> Uri | result | instrumentation | 2 |
| `EngineSelector.resolve` | Rive/Canvas/Auto | device + prefs -> engine + reason | reason | unit | 3 |
| `MiddleSlotModel` | Middle occupant | state + prefs -> slot | state changes | unit | 3 |
| `CellMapper.map` | 4-dot mapping | level/max/network -> 0..4 | raw/mapped | unit | 4 |
| `BackupCodec.export/import` | JSON backup | prefs -> JSON / JSON -> prefs | version/result | unit | 3 |
| `PresetStore.apply` | 3 presets | preset id -> prefs | result | unit | 3 |
| `LiveEventMonitor` | Event sources | hooks/consent -> event | event/reason | unit + device | 3 |
| `PerfProbe` | gfxinfo/meminfo/battery capture | adb/device -> report | metrics | device | 7 |

## 15. Parallelism rules

- Phase 1 is sequential: scope -> probe -> resolver -> binding -> tests. Do not parallelize code that edits the same hook files.
- Phase 3 can split into app-shell / engine / backup / live-events lanes, but shared `DuoPrefs`/provider changes land first.
- Phase 7 is the main parallel phase: one lane per UI, separate files; shared resolver changes land first.
- Any lane that must edit `DuoHook.kt`/`DuoIconHost.kt`/`ContainerFinder.kt` waits for the shared core to merge.
- Use separate worktrees for parallel agents; never two agents on the same files.

## 16. Human gates

| Phase | Owner action |
|---|---|
| 1 | Enable module for System UI; restart; keep the phone connected |
| 1 | Visual check of shade/QS before/after |
| 3 | Review the 3 pages/presets and backup file |
| 5 | Visual sign-off on liquid glass |
| 6 | Visual sign-off on animations |
| 7 | Provide one device/dump per UI, or accept `unverified` |
| 8 | Approve release; remove OEM assets before official launch |

## 17. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Shade header path not resolved | Stock battery remains | Probe the header; use the measured `quick_qs_status_icons` tree |
| Battery host uses a drawable, not `onDraw` | Wrong hook | Spike both draw points; log candidates |
| Rive cannot live inside the battery host without an overlay | Engine parity | Canvas for Phase 1; record limitation; revisit |
| ROM repopulates icons | Icons return | Probe the real insertion route; re-apply on that route |
| 6 UIs have no device | False "support" | `unverified` label; no claim |
| Native Rive crash | SystemUI death | Existing breaker/kill switch; guard |
| Liquid glass blur jank | Bad UX | Cap radius; fallback below API 31 |
| Disk 8.5 GB free | Build/download limits | Clean build artifacts; no large firmware |
| Windows logs not mounted | Missing user evidence | Owner mounts read-only NTFS |
| Backup schema drift | Broken import | Versioned JSON + validation + tests |

## 18. Immediate next action

1. Owner approves Requirements + Test Plan + Plan v2.
2. Capture the shade/QS baseline; the module is already active in SystemUI.
3. Start `phase/1-shade-qs`: probe, then the host binding.
4. No code before steps 1-2 are green.

*End of Implementation Plan v2.*
