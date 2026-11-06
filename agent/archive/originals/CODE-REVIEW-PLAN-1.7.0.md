# Duo Status Bar — Code Review Plan (1.7.0 stable)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Post-1.7.0 stable review (branch `fix/1.6-stability`, HEAD `7361c73`) |
| Date | 2026-11-04 |
| Owner | Abdulkarim |
| Requirements | `agent/AGILE-1.7-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.7-PLAN.md` |
| Tests | `agent/AGILE-1.7-TEST-PLAN.md` |
| Release evidence | `agent/RELEASE-NOTES-1.7.0.md`, `agent/AGILE-1.8-NOTES.md` |
| Rules | `AGENTS.md`, `agent/AGENT.md` |
| Status | Review plan approved for execution; no code change before findings are reviewed |

## 1. Purpose

Validate the shipped 1.7.0 code against the approved requirements, plan and test plan, then find
and fix performance and battery leaks **without changing behaviour that already meets the
requirements**. A behaviour change is allowed only when the code contradicts a requirement or
violates a safety rule from `AGENTS.md` / `agent/AGENT.md`.

This review does not reopen the 1.6 fatal fix, does not add architecture, and does not replace the
Rive/Canvas split.

## 2. Scope

In scope:

- Runtime path inside SystemUI: `hook/**` (hook, host, monitor, readers, drawing, transitions).
- App path: `ui/**`, `DuoPreview.kt`, `MainActivity.kt`, `DuoApp.kt`, settings, update, reporting.
- Requirement traceability for BAT, RING2, HOME2, NAV2, ICON2, GLASS2, UX2, QA2.
- Unit tests and the project gates that protect the frozen surface.

Out of scope:

- Style, naming, formatting and generic refactors.
- New features or new hook frameworks.
- Re-testing vendor ROMs that remain `unverified`.
- The platform ART JIT fault recorded as `mitigated/platform` in `AGILE-1.8-NOTES.md`.

## 3. Non-negotiable rules for this review

1. Evidence over guessing: every finding cites a changed or reviewed file and line.
2. One property, one owner: geometry in Kotlin, easing in Rive.
3. Fail silent: any failure leaves the stock status bar working.
4. Defaults must not change: a fix maps to the same visible pixels unless a requirement is missed.
5. No secrets in the APK or in reports.
6. No `android.util.Log` inside SystemUI; use `L`.
7. No behaviour change without a requirement ID or a measured defect.
8. No fix that grows the Rive asset, adds a second renderer, or reparents views per frame.

## 4. Requirement traceability matrix

The matrix is the review checklist. `code` names the primary owner file(s); `test` names the
existing unit/device case; `status` is the review verdict after evidence.

### 4.1 Battery behaviour and transitions (BAT)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| BAT-01 | Stock battery host/lifecycle study, or documented safe overlay | `hook/DuoIconHost.kt`, `hook/DuoHook.kt`, `docs/rom-contract.md` | device walkthrough | pending |
| BAT-02 | Smooth HUD/QS/shade transitions, no snap/size jump/flash/blank | `hook/PanelHandoff.kt`, `hook/DuoIconHost.kt` | `PanelHandoffTest`, transition capture | pending |
| BAT-03 | Duo present whenever stock battery is present | `hook/DuoIconHost.kt` | transition capture | pending |
| BAT-04 | 1.6 crash guards remain | `hook/DuoGuard.kt`, `hook/DuoIconHost.kt`, `hook/DuoRiveView.kt` | P0 unit suite | pending |
| BAT-05 | Transition logs classified before lifecycle change | `docs/evidence/agile-1.7/**`, `AGILE-1.8-NOTES.md` | evidence review | pending |

### 4.2 Ring designs, battery, PHDP, cells (RING2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| RING2-01 | Classic 4-dot and 2 SIM 8-dot selectable and persistent | `hook/DuoMapping.kt`, `hook/DuoRiveView.kt`, `hook/DuoCanvasView.kt`, `settings/DuoPrefs.kt` | `DuoPrefsTest`, `DuoMappingTest` | pending |
| RING2-02 | New battery bar is live, not static 100% | `hook/DuoMapping.kt`, `hook/DuoStateMonitor.kt` | `DuoMappingTest` | pending |
| RING2-03 | PHDP progress uses selected design | `hook/DownloadWash.kt`, `hook/DuoMapping.kt` | `DownloadWashTest` | pending |
| RING2-04 | 8-dot battery bar is a continuous circular ring | `hook/RingGeometry.kt`, Rive scene | headless/device compare | pending |
| RING2-05 | Cell mapping matches stock per SIM (4/8 dots) | `hook/SignalMapping.kt`, `hook/CellReader.kt` | `SignalMappingTest` | pending |
| RING2-06 | Camera moves current design to cutout only on main bar | `hook/DuoIconHost.kt`, `hook/rom/RomAdapter.kt` | device walkthrough | pending |
| RING2-07 | Custom editor + per-dot simulation (4 full, 4+2, custom) | `ui/PreviewSimulator.kt`, `ui/DuoHomeScreen.kt`, `settings/DuoPrefs.kt` | `PreviewSimulatorTest` | pending |
| RING2-08 | Battery colour mapping preserved (100% plugged = normal) | `hook/DuoMapping.kt`, `hook/DuoStateMonitor.kt` | `DuoMappingTest` | pending |

### 4.3 Home, size, position, preview (HOME2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| HOME2-01 | Home is the design/customisation surface with live preview | `ui/DuoHomeScreen.kt`, `ui/DuoShell.kt` | device/Compose walkthrough | pending |
| HOME2-02 | One logical 0..100 scale, `physical = 50 + 1.7 * logical` | `settings/DuoPrefs.kt` | `DuoPrefsTest` | pending |
| HOME2-03 | Position drag full range, pointer stays attached | `ui/DuoSettingsComponents.kt`, `ui/GlassControls.kt` | device walkthrough | pending |
| HOME2-04 | Preview: charge slider, saver, 8-dot, centre inversion | `ui/PreviewSimulator.kt`, `ui/DuoHomeScreen.kt` | `PreviewSimulatorTest` | pending |
| HOME2-05 | Animated previews for Alarm/Flashlight/Screenshot/Unlock | `ui/DuoSettingPreview.kt`, `DuoPreview.kt` | device walkthrough | pending |
| HOME2-06 | Supplied design previews, Classic vs 2 SIM visibly different | `ui/**`, `res/drawable/**` | asset check | pending |

### 4.4 Navigation, icons, settings (NAV2/ICON2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| NAV2-01 | "Custom" short label | `ui/DuoShell.kt`, `ui/DuoNavIcons.kt` | device walkthrough | pending |
| NAV2-02 | Settings is the fourth pill tab | `ui/DuoShell.kt` | device walkthrough | pending |
| ICON2-01 | Owner-supplied icons used unchanged | `res/drawable/**` | asset check | pending |
| ICON2-02 | Per-icon hiding + All master switch | `settings/StockIconHider.kt`, `settings/StockIconInventory.kt`, `hook/StockIconHider.kt` | `StockIconHiderTest` | pending |
| ICON2-03 | All discovered slots incl. clock/battery; battery anchor survives | `hook/StockIconHider.kt`, `hook/DuoIconHost.kt` | device walkthrough | pending |
| ICON2-04 | Wi-Fi outside ring keeps Duo Wi-Fi glyph | `hook/DuoIconHost.kt`, `hook/DuoMapping.kt` | device walkthrough | pending |

### 4.5 ColorDuo glass and touch (GLASS2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| GLASS2-01 | Measured ColorDuo transparency/blur/refraction/rim/shadow | `ui/GlassSurface.kt`, `ui/GlassControls.kt` | pixel compare | pending |
| GLASS2-02 | Measured selector touch model | `ui/GlassControls.kt`, `ui/DuoShell.kt` | device recording | pending |
| GLASS2-03 | Active blue and null wine fill preserved | `ui/DuoTheme.kt`, `ui/GlassSurface.kt` | screenshot | pending |
| GLASS2-04 | ColorDuo REA evidence recorded | `agent/AGILE-1.7-*` | evidence review | pending |

### 4.6 Report, preview, events (UX2) and quality (QA2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| UX2-01 | Report title below blur, not clipped | `ui/DuoReportScreen.kt` | device screenshot | pending |
| UX2-02 | Live preview interactive, updates <100 ms, no idle loop | `ui/PreviewSimulator.kt`, `DuoPreview.kt` | device + logs | pending |
| UX2-03 | Four animated event previews, no old Simulation section | `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt` | device walkthrough | pending |
| QA2-01 | Only measured ROMs claimed supported | `hook/rom/RomProfiles.kt`, docs | matrix review | pending |
| QA2-02 | No fatal in transition/camera/rotation/per-icon | `hook/**` | P0 suite + walkthrough | pending |
| QA2-03 | Requirement → test → plan traceability | this file | audit | pending |
| QA2-04 | No PII, assets allowed | `L.kt`, `ui/LogReporter.kt`, `settings/TelegramLog.kt` | grep/audit | pending |

## 5. Performance and battery review tracks

Each hypothesis is a candidate, not a finding. A finding needs code evidence and a measured or
provable cost.

### 5.1 SystemUI runtime (highest risk)

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P1 | Renderer keeps advancing while the element is hidden | `DuoRiveView.kt`, `DuoIconHost.kt` | `setRenderActive(false)` on every hidden path; idle-stop logs |
| P2 | Repeated identical snapshots re-start the renderer | `DuoRiveView.render`, `DuoBinder` | equality skip present; verify every caller |
| P3 | A retry/survival timer keeps firing after detach | `DuoIconHost.kt` (`SURVIVAL_MS`, shade retries) | `removeCallbacks` on teardown; no orphan `postDelayed` |
| P4 | Receivers/content observers leak per attach | `DuoHook.kt`, `DuoStateMonitor.kt` | one register, one unregister; count on re-attach |
| P5 | High-frequency broadcasts trigger expensive reads/logging | `DuoStateMonitor.refresh`, `CellReader`, `WifiReader` | RSSI/connectivity burst; reflection count; log volume |
| P6 | Reflection is repeated on every refresh | `CellReader.kt` (`createForSubscriptionId`, signal getters) | cache `Method` handles or equivalent |
| P7 | Heartbeat write is too frequent / keeps the process awake | `DuoStateMonitor.heartbeatRunnable`, `DuoGuard` | settings write count; doze behaviour |
| P8 | FileObserver or screenshot watcher runs while disabled | `ScreenshotWatcher.kt` | start/stop pairing with module state |
| P9 | Download/charge/moon animations continue when idle | `DownloadWash.kt`, `ChargeMotion.kt`, `DotMoonMotion.kt`, `DuoRiveView` | animation end conditions; no frame loop after settle |
| P10 | Canvas fallback redraws continuously | `DuoCanvasView.kt`, `DuoElement` | invalidate only on change; no `postInvalidateOnAnimation` loop |

### 5.2 App and preview path

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P11 | Looping previews drive a frame callback for the whole screen life | `DuoPreview.kt` (`DuoRivePreview`), `DuoSettingPreview.kt` | `withFrameNanos` loop; lifecycle pause |
| P12 | Previews keep Rive renderers running while off-screen/backgrounded | `DuoPreview.kt`, `ui/DuoHomeScreen.kt`, `ui/DuoSettingsScreen.kt` | idle stop + lifecycle; renderer logs |
| P13 | Compose recomposes on every frame during holds | `DuoPreview.kt`, `PreviewSimulator.kt` | phase updates only when needed |
| P14 | Handlers/coroutines outlive composition | `DuoPreview.kt`, `ui/**` | `DisposableEffect` coverage |
| P15 | Update/download workers poll or retry too aggressively | `UpdateWorker.kt`, `UpdateChecker.kt`, `UpdateDownloadWorker.kt` | WorkManager constraints; backoff |
| P16 | Log collection or reporting holds a wake lock/thread | `RootLogs.kt`, `settings/TelegramLog.kt`, `ui/LogReporter.kt` | thread lifetime; timeout |

### 5.3 Cross-cutting

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P17 | Allocation churn on every status tick | `DuoMapping.kt`, `DuoStateMonitor.render`, `DuoIconHost` | object creation per tick; strings in hot path |
| P18 | Logging in the hot path floods logcat/XposedBridge | `L.kt`, `DuoStateMonitor`, `DuoIconHost` | lines per second during RSSI/battery bursts |
| P19 | Duplicate geometry/state owners cause extra work | `RingGeometry.kt`, `SlotGeometry.kt`, `DuoMapping.kt` | one owner per property |
| P20 | Dead code left by 1.7 paths (old simulation, old hide-all) | `ui/**`, `hook/**` | only code made obsolete by 1.7 |

## 6. Sub-agent assignments

Sub-agents are read-only. They return findings in the exact shape below; the main agent verifies
each line against the code and applies only approved fixes.

| Agent | Track | Scope |
|---|---|---|
| A | Business logic (RING2/HOME2) | ring designs, battery bar, PHDP, cells, camera, custom, size, position, previews |
| B | Business logic (BAT/NAV2/ICON2/GLASS2/UX2) | transitions, host lifecycle, navigation, per-icon hiding, glass, report |
| C | Performance/battery (SystemUI) | P1–P10, P17–P20 in `hook/**` |
| D | Performance/battery (app) | P11–P16 in `ui/**`, previews, workers, logging |
| E | Tests and deploy safety | requirement coverage, P0 guards, gates, frozen-surface diff |

Finding shape:

- `path`
- `line` (exact current line)
- `requirement` (ID or `PERF`/`BATTERY`)
- `confidence` (`high` only)
- `why` (1–2 sentences with evidence)
- `finding` (specific)
- `suggestion` (one safe fix direction, or `none` if investigation is needed)

A track with no solid issue returns `NO_FINDINGS`.

## 7. Severity

- **CRITICAL**: fatal/SystemUI crash, data loss, security exposure, requirement violated in a way
  that breaks the product.
- **WARNING**: measurable battery/perf regression, logic error, unhandled failure, drift risk.
- **SUGGESTION**: non-blocking improvement tied to a requirement or measured cost.

## 8. Fix policy

1. Fix only findings confirmed by the main agent.
2. Performance fixes must not change visible output: compare bound properties and pixels.
3. Requirement fixes must cite the requirement ID and update the traceability table.
4. One logical change per commit-sized edit; no opportunistic refactors.
5. After fixes: `./gradlew :app:testDebugUnitTest :app:assembleDebug` and the project gates.
6. If a fix needs a device measurement that is unavailable, record `unverified`; do not guess.

## 9. Verification commands

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 tools/route-module-logs.py --check
python3 tools/check-rive-eases.py
sha256sum app/src/main/res/raw/duo.riv   # compare with a fresh Rive build when the scene changes
```

Device walkthrough evidence follows `agent/WALKTHROUGH-PLAN-1.7.0.md`.

## 10. Exit criteria

- Every requirement row has a verdict: pass, fail, or `unverified` with the missing evidence.
- Every confirmed performance/battery finding is fixed or explicitly deferred with a reason.
- No new fatal, no new permission, no new secret, no new architecture.
- Unit suite and debug build green; gates clean.
- Review report and walkthrough report written under `agent/`.
