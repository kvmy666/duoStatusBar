# Duo Status Bar — Code Review Report (1.7.0 stable)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Reviewed state | Branch `fix/1.6-stability`, HEAD `7361c73` (`1.7.0`, versionCode 89) plus the review fixes below |
| Date | 2026-11-04 |
| Requirements | `agent/AGILE-1.7-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.7-PLAN.md` |
| Test plan | `agent/AGILE-1.7-TEST-PLAN.md` |
| Review plan | `agent/CODE-REVIEW-PLAN-1.7.0.md` |
| Walkthrough plan | `agent/WALKTHROUGH-PLAN-1.7.0.md` |
| Scope | Full 1.7.0 runtime and app code, not a diff-only review |
| Device state | CPH2747 not attached during this pass; device-only acceptance stays `unverified` |

## 1. Method

- Read the requirements, plan, test plan, 1.7 release notes and 1.8 notes.
- Ran five read-only sub-agents in parallel: RING2/HOME2, BAT/NAV2/ICON2/GLASS2/UX2, SystemUI
  performance/battery, app performance/battery, tests/dead-code/deploy.
- Verified every applied fix against the current source; no fix was applied from a sub-agent claim
  alone.
- Ran `:app:testDebugUnitTest :app:assembleDebug` and the two project gates.

## 2. Performance and battery fixes applied

These fixes preserve the visible output. Each one removes work that had no on-screen effect.

| # | File | Change | Why it matters |
|---|---|---|---|
| 1 | `hook/DuoHook.kt:137` | Stop `DuoStateMonitor` on the live-disable path before `host.teardown()` | The receiver, ContentObserver, screenshot FileObservers and 20 s heartbeat stayed alive with no element attached. |
| 2 | `hook/DuoHook.kt:651` | Keep one status-bar layout listener per root; remove the old one when the root changes | Disable/re-enable stacked a second listener, so every layout ran the hide pass twice. |
| 3 | `hook/DuoIconHost.kt:1015` | Stop the main copy's renderer when the main bar is hidden; restart on reveal | Hidden main copy kept restarting a looping Rive renderer on every status change. |
| 4 | `hook/DuoIconHost.kt:1291` | Tie each QS/shade copy's renderer to its alpha target; stop it at zero alpha and on latch | Hidden extras re-armed the Rive renderer on every download/RSSI update. |
| 5 | `hook/DuoCanvasView.kt:120` | Add `setRenderActive` to the Canvas fallback: store snapshots but skip invalidate/tick while hidden | Hidden Canvas copies no longer redraw on every state change. |
| 6 | `hook/CellReader.kt:31` | Cache reflective `Method` handles for `createForSubscriptionId`, per-RAT getters and `ServiceState.getNrState` | Reflection ran on SystemUI's main thread on every connectivity/RSSI refresh. |
| 7 | `hook/WifiReader.kt:55` | One active-network/capability snapshot per sample; read `connectionInfo` only as fallback | Three duplicate binder queries plus an unconditional `connectionInfo` read per refresh. |
| 8 | `DuoPreview.kt:70` | Add the 2 s idle renderer stop to the Home live preview and stop it on dispose | The Home lab had the same continuous idle Rive loop that `DuoRiveStill` already guards against. |
| 9 | `DuoPreview.kt:466` | Add `rememberResumed()` and gate preview frame loops/reveal loops | Preview loops kept running after the app went to the background. |
| 10 | `ui/DuoSettingPreview.kt:117` | Gate the event-row replay loop on the resumed lifecycle | Four event previews kept replaying while the tab was backgrounded. |
| 11 | `ui/DuoSettingsScreen.kt:213` | Pause the 1.5 s status re-read while the tab is not resumed | The loop woke SharedPreferences, Settings.Global, PackageManager and Shizuku every 1.5 s off-screen. |
| 12 | `RootLogs.kt:295` | Destroy the `su` process and use a daemon worker on timeout | Every timed-out root capture leaked a worker thread and a child process. |
| 13 | `hook/DuoRiveView.kt:187` | Stop the view-model poll after `teardown()` | A disposed Rive view could keep polling and bind late. |

## 3. Transition and requirement fixes applied

| # | File | Change | Requirement |
|---|---|---|---|
| 14 | `hook/DuoIconHost.kt:757` | Do not cancel an in-flight collapse hand-off when `setPanelOwner(null)` follows `onPanelCollapsed()`; start it only when none is running | BAT-02: the slide was cancelled before it could draw. |
| 15 | `hook/DuoIconHost.kt:1233` | Apply the incoming QS/shade copy first, then the outgoing one | BAT-02: `anotherCopyVisible()` saw the outgoing alpha already at 0, so QS→shade was a hard cut while shade→QS faded. |
| 16 | `ui/DuoHomeScreen.kt:212` | Pass the selected `rawRes` to the Home live preview | HOME2-01/HOME2-06: the live preview always showed the 2 SIM drawing. |

## 4. Requirement validation

Status values: **PASS** = code and existing tests satisfy the requirement; **PARTIAL** = implemented
but a required sub-case is missing; **FAIL** = code contradicts the requirement; **UNVERIFIED** =
needs the device walkthrough or a measurement that is not available in this pass.

### BAT

| ID | Status | Evidence |
|---|---|---|
| BAT-01 | PASS (documented) | Overlay/host decision and measured ROM contract are in `AGENTS.md`, `docs/rom-contract.md`, `AGILE-1.8-NOTES.md`. |
| BAT-02 | PARTIAL | Hand-off cancel and QS/shade cross-fade order fixed. Device log shows normal 26–40 px/120 ms slides; 3 `too-far` skips occurred while `lockRing`/Camera was enabled. Six-direction capture is still required. |
| BAT-03 | PARTIAL | `mainCopyReady`/`coverOrder` hold the outgoing copy; device frame capture still required. |
| BAT-04 | PASS (unit) | 449 unit tests, P0 guards present; device P0 run still required. |
| BAT-05 | PASS | Transition measurements and residuals recorded in `AGILE-1.8-NOTES.md`. |

### RING2

| ID | Status | Evidence |
|---|---|---|
| RING2-01 | PARTIAL | Rive selects `duo`/`duo_old`; `DuoCanvasView` always draws the 2 SIM line/eight dots, so the Canvas fallback cannot render Classic. |
| RING2-02 | **FAIL** | `RingGeometry.kt` fixes `BATTERY_LINE_START/END` and `scene.rml:181` fixes the trim; the code comment says the reference draws the full arc at 80/49/19. The bar does not follow the level. |
| RING2-03 | **FAIL** | `DownloadWash.of` has no design input and always uses the old ring trims; `DownloadProgress.toWash` calls it without a design. |
| RING2-04 | UNVERIFIED | Needs headless/device curvature comparison. |
| RING2-05 | **FAIL** | `DuoVisual` carries one `cellLevel`; `scene.rml:991-1009` binds `cell5..cell8` to the same properties as `cell1..cell4`. Both sides mirror one SIM. |
| RING2-06 | PARTIAL | `lockRing` centers the ring; there is no measured camera-cutout binding and no enforced “Wi-Fi outside visible” mode. |
| RING2-07 | **FAIL** | `DuoSettings` has `offsetX`, `indicatorsOffsetX` and `percentHeight` only; no rotation, Wi-Fi detach/pin, 2D centre move or per-dot module layout. The Home simulator does support 4 full/4+2/custom masks. |
| RING2-08 | PASS | Level/charging/saver mapping and the 100% plugged rule are covered by `DuoMappingTest`/`shouldShowCharging`. |

### HOME2

| ID | Status | Evidence |
|---|---|---|
| HOME2-01 | PARTIAL | Home has design cards and the live lab; the full Custom editor is on the Custom tab. `DuoPresetStore.apply("custom")` falls back to current settings when no snapshot exists, so Custom is not blank. |
| HOME2-02 | PARTIAL | `DuoPrefs.logicalToPhysical` is `50 + 1.7 × logical` and clamped 50..220; optical equality uses one whole-view `OLD_OPTICAL_SCALE`, not per-art normalisation. |
| HOME2-03 | UNVERIFIED | Drag code exists; the full-range/pointer test is device-only. |
| HOME2-04 | PASS (simulator) | `PreviewSimulator` exposes charge, saver, design, 4-full/4+2/custom dot masks, centre inversion and middle-slot choice. |
| HOME2-05 | PASS | `DuoEventRowPreview`/`DuoEventSimulationPreview` animate Alarm, Flashlight, Screenshot and Unlock. |
| HOME2-06 | PARTIAL | The Home live preview now uses the selected drawing; the supplied still/GIF design references still need an asset check. |

### NAV2 / ICON2

| ID | Status | Evidence |
|---|---|---|
| NAV2-01 | PASS | `nav_engine` is “Custom” and the pill label is not clipped by the responsive selector width. |
| NAV2-02 | PASS | Four items: Home, Custom, Report, Settings; Settings is the fourth. |
| ICON2-01 | **FAIL** | `DuoNavIcons.kt:10-13` says the Home/Custom/Report icons are redrawn Compose vectors; no owner-supplied asset is used. |
| ICON2-02 | **FAIL** | The UI still exposes the legacy “Hide other status icons” switch (`DuoSettingsSections.kt:606`); `StockIconSelection.withAll` has no production caller, so the required All master switch is absent. |
| ICON2-03 | PARTIAL | The inventory walks the `system_icons` strip; the clock and the sound/vibrate group live outside it on CPH2747, so they cannot be listed or hidden. The battery anchor is preserved (`StockIconHider` keeps it INVISIBLE). |
| ICON2-04 | PARTIAL | Split indicators exist; the “Wi-Fi outside + stock Wi-Fi hidden” case needs the device walkthrough. |

### GLASS2 / UX2 / QA2

| ID | Status | Evidence |
|---|---|---|
| GLASS2-01 | UNVERIFIED | The measured tokens are used, but the navigation surface also applies brightness/contrast; side-by-side pixel comparison is still required. |
| GLASS2-02 | PARTIAL | `DuoShell` uses `detectDragGesturesAfterLongPress`, snaps the selector to the finger and has no velocity/haptic crossing; ColorDuo starts on press and settles by velocity. |
| GLASS2-03 | PASS | Active blue and null wine fill are in the theme. |
| GLASS2-04 | PASS | ColorDuo evidence is recorded in the phase notes. |
| UX2-01 | UNVERIFIED | Needs a 1272×2772 screenshot of the report page. |
| UX2-02 | PARTIAL | Idle renderer stop and lifecycle gating added; the <100 ms update measurement is device-only. |
| UX2-03 | PASS | The four event previews replace the old static rows. |
| QA2-01 | PASS | Only CPH2747 and HyperOS 3 are marked measured; other ROMs remain `unverified`. |
| QA2-02 | PARTIAL | 449 unit tests pass; the device P0/transition matrix is still required. |
| QA2-03 | PASS | The requirement-to-test traceability is in the review plan. |
| QA2-04 | PARTIAL | No PII audit was run; the deploy findings below still apply. |

## 5. Additional review findings (not fixed)

### Deploy safety

- **Direct Telegram bot token can be compiled into the APK.** `app/build.gradle.kts:59` injects
  `TELEGRAM_BOT_TOKEN` from `.env`; `TelegramLog.kt` uses it as a fallback. The current `.env` value
  is a placeholder and the shipped 1.7.0 APK has no token pattern, but a local release with a real
  token would violate `AGENTS.md` rule 9. Keep relay-only delivery, or restrict the token to a
  non-publishable build type.
- **Release tag is not checked against Gradle’s version.** `.github/workflows/build-release.yml`
  derives the release name from the tag but does not compare it with `versionCode`/`versionName`;
  a tag such as `90-1.7.1` would publish an APK that still reports 89/1.7.0.

### Dead code and duplication (suggestions)

- `DuoCellSimulationPreview` has no caller after the old Simulation section was removed.
- `StockIconHider.isReplaced` has no production caller; only its test uses it.
- `PreviewSimulator` duplicates the Wi-Fi glyph/flashlight path from `DuoCanvasView`; a geometry
  change in one copy can make the preview lie.
- `RingGeometry.CELL_DOT_POSITIONS`/`BATTERY_LINE_*` are duplicated as literals in
  `rive/duo/scene.rml`; there is no parser test that keeps the two tables equal.
- The Customization page uses a scrolling `Column`, so all preview rows stay composed; the resumed
  gate stops background work, but off-screen rows still hold their Compose state. A `LazyColumn`
  would remove that residual.

### App preview residual

- `DuoRivePreview` still requests a frame every vsync while the page is resumed, including the hold
  phases. The renderer now idles, so the remaining cost is the frame callback, not drawing. Moving
  the hold windows to `delay` would reduce it further; this is a suggestion, not a leak.

## 6. Verification

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 449 tests, 0 failures, 0 errors, 0 skipped |
| `:app:assembleDebug` | Success |
| CPH2747 device log | 6,216 `DuoSB` lines; 0 FATAL/SIGSEGV/SIGABRT; 248 idle pauses; 85 normal hand-off slides; 3 `too-far` skips in Camera mode |
| CPH2747 test log 22:16–22:41 | 3,573 `DuoSB` lines; 0 FATAL; Classic shade copy stayed in the 127 px strip; restart request did not restart SystemUI |
| Shade behavior | Owner selected build 1 (`a534…`) as the reference; the later shade-parent change and its 6 tests were reverted; 449 tests pass |
| `tools/route-module-logs.py --check` | 0 files to fix, 0 log calls left for review |
| `tools/check-rive-eases.py` | 158 easing keyframes, 158 carry a curve, 0 inert |
| Rive asset | Unchanged; no `duo.riv` rebuild was required |
| Java | Build used `/usr/lib/jvm/java-21-openjdk-amd64`; the default Java 25 is not supported by this AGP |

## 6.1 Device verification (CPH2747, 2026-11-04)

- Device: CPH2747, Android 16 / SDK 36, build `CPH2747_16.0.9.400(EX01)`, KernelSU root.
- LSPosed: module `io.github.kvmy666.duostatusbar` enabled, scope `com.android.systemui`.
- Installed APK SHA-256 `a534437544a9ee72b2b9b0b489c93271da9c27705139646d6650a1704db01788`,
  identical to the review build.
- Runtime: `stage=2`, `renderer=Rive`, `attached=true`, size 169 %, design `new`, `lock=false`,
  `split=false`.
- Log evidence: 6,216 filtered `DuoSB` lines; 0 `FATAL`/`SIGSEGV`/`SIGABRT`; 248
  `Rive renderer paused (idle)` lines; 85 normal hand-off slides of 26–40 px over 120 ms.
- Residual found on device: three `panel handoff: no x slide (too-far)` events at
  21:56:27–21:56:31 while `lockRing` (Camera/Compact) was enabled. The hand-off is deliberately
  skipped above `HANDOFF_MAX_DP = 24`; in Camera mode the centred main copy and the QS/shade copy
  are farther apart, so the collapse does not slide. This needs either a measured Camera-specific
  bound or a crossfade; it is not fixed without a second device pass.
- The phone dropped off Wi-Fi during the transition test (AC powered, level 20 %), so the
  six-direction capture, per-icon matrix, size/design screenshots and PHDP check remain
  incomplete. The filtered log and summary were sent to Telegram as messages 36 and 37.

## 6.2 Fatal / safe-mode evidence (pending)

- `2026-11-04T21:56:37.000 E/LSPosedService-SafeMode: System UI crashed too many times, stop all
  modules and enter safe mode ...` happened 0.4 s after
  `21:56:36.584 DuoSB | restart requested by the app - restarting System UI`.
- The pulled logcat and `crash` buffer contain no SystemUI `FATAL`/`SIGSEGV`/`SIGABRT` stack; the
  only fatal entries are unrelated `app.source.getcontact` Keystore crashes at 03:02–03:52 and
  22:31–22:33.
- The fresh 22:16–22:41 log has no fatal or safe-mode line. It shows
  `22:39:50.587 restart requested by the app - restarting System UI`; the next module line at
  22:40:43 still reports PID `10266` but a different LSPosed token, so the log does not confirm
  whether the process restarted. Do not claim success or failure without a process-start line.
- The 21:56 SystemUI crash stack/tombstone is still missing. Do not change the Rive startup path
  until it is pulled. The silent restart failure and the repeated-restart safe-mode risk need their
  own check; the owner-run checklist is in `agent/DEVICE-TEST-REQUEST-2026-11-04.md`.

## 6.3 Shade anchor position

- The legacy fix commit `0ffeb3f` ("restore legacy shade anchor position") removed a
  `if (view.translationY < 0f) view.translationY = 0f` clamp. Current `applyExtraLayout` still uses
  the same legacy path: `anchorOnBattery(..., alignEnd = true)` for the QS cluster and
  `place(..., slot.center)` otherwise, with no clamp. It is also identical in 1.6.0.
- The old dump (new design, 141 px) had the shade copy at `@1044,-18` inside the 438 px header,
  matching the documented `(1102,112)-(1209,235)`.
- The 22:40 device log found the regression cause: with Classic the optically scaled view is
  134x151 and `shade header layout target=android.widget.LinearLayout[1170x127]`; the old
  `target.height * 10 < desired * 9` rule (127/134 = 94.8 %) kept the copy inside the 127 px stock
  strip, so it moved down. The new design at 141/142 px moved to the 438 px parent. The owner's
  shade screenshot confirms the lower Classic ring.
- The owner reviewed build 1 (`a5344375…01788`) and chose its shade/QS/HUD behavior as the
  reference. The later `needsTallerParent`/`shadeHeader` change was therefore reverted, along with
  its `SlotGeometryTest`; `DuoIconHost.attachExtraView` is back to the build-1 shortfall rule. The
  Classic shade copy remains in the stock 127 px strip, which is the owner-accepted build-1
  position. 449 unit tests pass after the revert; the build-2 APK (`c185…`, Telegram 38) is
  superseded and must not be used.

## 7. Recommendation

**NEEDS CHANGES before claiming 1.7.0 meets the full requirements.** The performance/battery leaks
found in the SystemUI and app paths are fixed and the suite is green, but the device pass found a
SystemUI safe-mode event whose crash stack is still missing, and the requirement audit found real
product gaps: the 2 SIM battery line is static (RING2-02), PHDP ignores the selected design
(RING2-03), the eight dots mirror one SIM (RING2-05), the Custom editor is narrower than the plan
(RING2-07), the All icon switch and the owner-supplied nav icons are missing (ICON2-01/02), and the
ColorDuo touch model is not matched (GLASS2-02). The shade anchor code matches the legacy position;
no position change was made without a measured delta. These are feature/safety work, not performance
fixes, and need the owner-run tests in `agent/DEVICE-TEST-REQUEST-2026-11-04.md` plus the device
walkthrough in `agent/WALKTHROUGH-PLAN-1.7.0.md`.
