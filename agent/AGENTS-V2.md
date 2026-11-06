# AGENTS-V2.md — Duo Status Bar single source of truth

**Status:** active from 2026-11-06. Owner: Abdulkarim.
**Scope:** project state, working agreement, architecture, timeline, mistakes registry, review, and release gates.
**Supersedes:** the old `agent/*` documents listed in section 1.3. They are archived under `agent/archive/originals/` and concatenated in `agent/archive/AGENT-DOCS-DEPRECATED.md`.
**Authority:** this file is the operational source of truth for `agent/`. The root `AGENTS.md` remains the short project charter; its C/M/m registry is reproduced and extended here. If a statement conflicts, the current-state section of this file wins for *current state*; safety rules never become weaker.

---

## 1. Purpose and authority

### 1.1 Why this file exists

The `agent/` folder accumulated overlapping plans, requirements, test plans, phase reports, review reports, evidence notes and mistake ledgers from Plan v2 through 1.8.0-alpha.8. Several of them describe states that no longer exist (for example `PreviewSimulator.kt`, live Rive design cards, and the pre-Phase-1 shade seat). A reader could follow an obsolete instruction and regress the module.

This file replaces that set with one current document. It records:

- what is installed and verified today;
- the rules that must not be broken;
- how the code is organised;
- how the project got here;
- every known mistake, including the 1.8 regressions;
- the remaining dead code and documentation debt;
- the exact build, test and release gates;
- where the evidence lives;
- what is still undecided.

### 1.2 How to use it

Read this file first, then the current device dump/log. Do not read an old plan as current unless this file points to it. If a change cannot be reconciled with this file, stop and ask the owner.

### 1.3 Documents superseded by this file

The following are historical after this file. The files now live under `agent/archive/originals/`, and their full contents are concatenated in `agent/archive/AGENT-DOCS-DEPRECATED.md`; the former `agent/README.md` index was replaced by the new short `agent/README.md`.

- `agent/archive/originals/AGENT.md`, the former `README.md` index (replaced)
- `agent/archive/originals/PLAN-v2.md`, `agent/archive/originals/PLAN-1.6.md`
- `agent/archive/originals/REQUIREMENTS.md`, `agent/archive/originals/REQUIREMENTS-1.6.md`, `agent/archive/originals/REQUIREMENTS-GAP-AUDIT.md`
- `agent/archive/originals/TEST-PLAN.md`, `agent/archive/originals/TEST-PLAN-1.6.md`, `agent/archive/originals/APP-TEST-PLAN.md`
- `agent/archive/originals/AGILE-1.7-*.md`, `agent/archive/originals/AGILE-1.8-*.md`
- `agent/archive/originals/CODE-REVIEW-PLAN-1.7.0.md`, `agent/archive/originals/CODE-REVIEW-REPORT-1.7.0.md`
- `agent/archive/originals/CODE-REVIEW-PLAN-1.8.0.md`, `agent/archive/originals/WALKTHROUGH-PLAN-1.7.0.md`, `agent/archive/originals/WALKTHROUGH-PLAN-1.8.0.md`
- `agent/archive/originals/PHASE-*-PLAN.md`, `agent/archive/originals/PHASE-*-REPORT.md`, `agent/archive/originals/PHASE-1-5-WALKTHROUGH.md`
- `agent/archive/originals/REPAIR-CATALOG.md`, `agent/archive/originals/ICON-MISTAKES.md`, `agent/archive/originals/EVENT-ARCHITECTURE.md`
- `agent/archive/originals/DECISIONS.md`, `agent/archive/originals/EVIDENCE.md`, `agent/archive/originals/UI-MATRIX.md`
- the other one-off reports now in `agent/archive/originals/`

They remain useful as evidence and history. They are **not** current instructions.

### 1.4 Documentation and organisation rules

1. Keep this file current. A state change updates section 2; a new mistake adds a registry row; a new gate updates section 8.
2. Do not create another parallel plan, requirements file or test plan for the same release. Add a dated subsection here instead.
3. All file names and file contents are English. The owner communicates in Arabic; the repository stays English.
4. Evidence stays under `docs/evidence/` (git-ignored). Never commit evidence, vendor APKs, user dumps, `.env`, keystores, or `.kilo/`.
5. Keep old `agent/` files under `agent/archive/originals/` (one archive file plus raw originals); do not delete history silently.
6. Every user-facing string belongs in `res/values/strings.xml` when practical; no developer jargon in UI copy.
7. Keep the root `AGENTS.md` short and stable. Keep the detail in this file.

---

## 2. Current state — 2026-11-06

### 2.1 Build identity

| Field | Value | Source |
|---|---|---|
| Date | 2026-11-06 (device timezone +03) | current session |
| Branch | `feat/1.8-selector-live-preview` | `git branch --show-current` |
| HEAD | `3d5ea0d` (`fix: 1.8 alpha.8 moving selector lens`), committed 13:56:48 +0300 | `git log -1` |
| Version | `1.8.0-alpha.8` | `app/build.gradle.kts:47-48` |
| versionCode | `97` | `app/build.gradle.kts:47` |
| APK | `app/build/outputs/apk/release/app-release.apk`, 7,511,605 bytes, built 13:48:18 | file stat |
| APK SHA-256 | `28b19f230d705baed4b55faac6616e15319bc1c53578034706f29999a8a1c833` | `sha256sum`; `agent/archive/originals/AGILE-1.8-NOTES.md` |
| Install | installed on CPH2747 at 13:50:03; delivered through Telegram message 45 | `agent/archive/originals/AGILE-1.8-NOTES.md` |
| Worktree | no app source diff; the documentation/organization changes are committed on the branch (not untracked); older agent docs live under `agent/archive/` | `git status --short` |
| Rollback baseline | 1.7.0 stable, versionCode 89, 449 tests, APK SHA-256 `3e15a8a92502f73773c459baffe9c7fae80e19a93417ea4d041126192519a2a6` | `agent/archive/originals/RELEASE-NOTES-1.7.0.md` |

### 2.2 Device truth

| Device | State | Evidence tier |
|---|---|---|
| CPH2747, OxygenOS 16 / Android 16 / SDK 36 | measured; alpha.8 installed | owner-confirmed install; no alpha.8 host log |
| Xiaomi HyperOS 3 (24129PN74G) | measured historically | `AGENTS.md`; `agent/archive/originals/UI-MATRIX.md` |
| AOSP emulator API 36 | prototype evidence only | `agent/archive/originals/PHASE-7-REPORT.md` |
| Every other vendor UI | `unverified` | `agent/archive/originals/UI-MATRIX.md` |

CPH2747 is the only current device truth. A visual confirmation on it does not promote any other ROM.

### 2.3 Tests and gates verified in this pass

These were re-run on 2026-11-06 against the current worktree:

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 482 tests across 57 suites, 0 failures, 0 errors, 0 skipped |
| Release build | current `app-release.apk` exists, 7,511,605 bytes |
| `tools/route-module-logs.py --check` | 0 files to fix, 0 log calls left for review |
| `tools/check-rive-eases.py rive/duo/scene.rml` | 158 keyframes, 158 carry a curve, 0 inert |
| `tools/check-rive-eases.py rive/duo/scene_old.rml` | 158 keyframes, 158 carry a curve, 0 inert |
| `duo.riv` hash | `fa32b3f9…1567` |
| `duo_old.riv` hash | `436d2358…bf09` |
| Fresh new-scene build | `rive/duo/build/duo.riv` hash equals `duo.riv`; `cmp` returns 0 |
| Packaged APK Rive assets | `res/zy.riv` equals `duo.riv`; `res/U1.riv` equals `duo_old.riv` |

The fresh **old**-scene build is not verified: `rive/duo/rive.yaml` excludes `scene_old.rml`, and CI checks only `scene.rml`. See sections 8 and 10.

### 2.4 Owner-confirmed behaviour (movement lens pending)

The owner-confirmed final state for alpha.8 is:

1. **Shade seat fixed** with the 1.7.0/v89 battery-anchor placement and no shade-only clamp.
2. **Selector idle layering fixed:** the idle row sits above the selector; no blur on the selector.

The **movement** layering (selector above the row, combined row backdrop, lens/chromatic aberration) was restored in alpha.8 (code 97, commit `3d5ea0d`), but the owner has **not yet visually confirmed** the movement lens. Treat shade and idle selector as owner-confirmed; treat the movement lens as pending confirmation.

### 2.5 Verified vs unverified

**Verified**

- 482 unit tests pass; release APK exists and matches the alpha.8 version.
- Log-routing gate and both Rive ease gates pass.
- New-scene fresh build and both packaged APK Rive assets match their checked-in hashes.
- Owner-confirmed: alpha.8 installed; shade seat fixed; selector idle layering fixed.
- Restored in alpha.8 code: movement selector above the row with the combined backdrop and lens (commit `3d5ea0d`, code 97).

**Unverified**

- Alpha.8 device log, screenshot and SystemUI PID stability; the owner installed it, but no host-side capture was attached.
- Movement-lens visibility and the movement layering as a visual result; restored in alpha.8 but not yet owner-confirmed.
- A fresh reproducible build of `duo_old.riv`; the repository has no automated old-scene build (section 8).
- Device performance/battery on CPH2747; the emulator budget failed and is not a phone measurement.
- Runtime support for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS, and AOSP; static maps only.
- Mic/location/volume live-event sources; no measured source.
- The remaining 1.7 residuals: Camera-mode panel hand-off `too-far` skips, Classic shade copy in the 127 px strip, and a true 60 fps capture.
- A release with a real direct Telegram bot token; the relay path is the only safe release path today.

### 2.6 Source conflicts and the current decision

| Conflict | Sources | Current state / decision |
|---|---|---|
| Alpha.8 "device verification pending" vs the reported final state | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.8 vs the 2026-11-06 owner brief | Shade fixed and idle selector are owner-confirmed; movement lens restored in alpha.8 but not yet visually confirmed; no fresh dump is claimed |
| 1.8 requirement says animated runtime Rive design cards | `agent/archive/originals/AGILE-1.8-REQUIREMENTS.md` DEMO3-01 | Current implementation uses static PNG cards; the owner later chose static assets to stop the crash |
| `classic_demo.gif` vs `classic_demo.png` | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.2 vs `app/src/main/res/drawable/classic_demo.png` | The file on disk is `classic_demo.png`; the note is stale |
| Shade seat: pre-Phase-1 `place()` vs v89 battery anchor | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.5 vs alpha.7/alpha.8 | Final: v89 battery-anchor placement, no clamp; owner confirmed |
| `1.8.0-alpha.6` | No notes entry; git jumps from code 94 to 96 | Treat alpha.6/code 95 as undocumented and unreleased; do not invent changes |
| 483 tests (alpha.3) vs 482 (alpha.4-alpha.8) | `agent/archive/originals/AGILE-1.8-NOTES.md` | Current suite is 482; the one-test difference is not explained by a current source |

---

## 3. Working agreement and non-negotiable rules

These rules are inherited from the root `AGENTS.md` and updated with the 1.8 incidents.

### 3.1 Evidence and reproduction

1. **Never guess.** Hook names, resource ids, class names, Rive property names and geometry are read from a device, an APK, the scene source, or a measured document. Mark anything unmeasured `unverified`.
2. **Reproduce before fixing.** A bug report needs the diagnostic dump, LSPosed log, logcat or screen capture that shows it. If the report is empty, fix the reporting first.
3. **No blind fixes.** Change one thing, rerun the same case, and diff the diagnostic dump. A changed dump on a working ROM is a regression.
4. **No overconfidence.** Write down what is unknown. When two explanations fit, test which one. A confident wrong answer costs users a broken status bar.
5. **Fail silent, keep the bar alive.** Every hook is guarded. A failure leaves the stock bar working and the surface disabled, never half-attached. Native faults cannot be caught in-process; the Rive breaker and kill switch exist for those.

### 3.2 Ownership and defaults

6. **One property, one owner.** Geometry in Kotlin; easing in Rive. One live rim owner. One battery-level source. One shade-seat owner. Nothing is keyed by two simultaneously active animations.
7. **Defaults must not change.** A new feature is off, or maps to exactly the old pixels. Prove it with a test or a measured number. Classic scene behaviour is frozen unless the owner explicitly reopens it.
8. **Test the feature you touched**, individually, then the linked cases. Do not run the whole world for a one-feature change.
9. **No new core architecture.** Follow `DuoIconHost` / `ContainerFinder` / `RomAdapter`. App-only UI libraries are allowed; a new core dependency needs a written reason and owner approval.

### 3.3 Safety, secrets and process

10. **No secrets in the APK or logs.** The Telegram bot token must never ship in a release; uploads go through the relay. Never print environment variables, provider keys, tokens, keystores or headers in tool output. If a key is exposed, rotate it and redact it; never repeat the value.
11. **No `git stash` in shared worktrees.** It has already produced a conflict/recovery incident. Use `git diff > backup.patch`, a backup branch, or copied files. Do not `stash pop`, `stash drop`, `reset --hard` or rewrite a shared branch without the owner's explicit approval. The retained `stash@{0}` must not be dropped yet.
12. **Verify the Rive binary in both scenes.** Both `scene.rml` and `scene_old.rml` must declare every property `DuoBinder` writes. Rebuild both `.riv` files, compare their hashes, and check the packaged APK assets. CI currently checks only the new scene; the old scene is a manual gate.
13. **No release, merge to `main`, tag, push, or LSPosed store action without the owner's approval.** Betas are pre-releases and never go to the store. A release build is an artifact, not permission to publish.
14. **Never force/remove/reparent a live SystemUI view.** One owner per surface; a live slot is never removed or reparented from a layout callback. If a target is unresolved, leave stock and log the candidate trace.
15. **No PII.** Logs/reports must not contain SSID, IMEI, serial, accounts, location, notification text, or raw secrets. Use `DuoLog.sanitize` and the existing redaction path.
16. **Keep the branch model.** One branch per phase; no `main` commits; preserve `fix/1.7.0-review-fixes` at `eafdcb8` and its commits `745fc5f`, `eafdcb8` unless the owner says otherwise.
17. **Docs are part of the product.** Every new failure adds or updates a registry row here. Every gate result states the exact command and evidence path. Do not claim a UI is supported without a measured dump, screenshot and log.

---

## 4. Architecture map

### 4.1 Runtime flow

```text
LSPosed
  -> assets/xposed_init
  -> MainHook.handleLoadPackage
     -> SystemUiProcess.isTarget(packageName, processName)
     -> DuoHook.install()
        -> WindowManagerImpl.addView hook / existing-window adoption
        -> status-bar, keyguard, QS and shade surfaces
        -> OnePlusShadeHooks (measured OnePlus vendor paths)
        -> status-icon container / tint / rotation hooks
  -> DuoIconHost
     -> ContainerFinder: resolve the strip / shade cluster
     -> StockIconHider: hide only the replaced icons; keep the battery anchor
     -> DuoElement: Rive or Canvas
     -> SlotGeometry / RingGeometry / DuoIconHost.place:
        size, right seat, battery anchor, extra seats, panel hand-off
     -> DuoStateMonitor:
        CellReader / WifiReader / DeviceStateReader / Battery / DND / events
        -> DuoMapping -> DuoVisual
        -> DuoBinder (Rive) or DuoCanvasView (Canvas)
  -> settings channel:
     app SharedPreferences -> DuoSettingsProvider / SettingsBridge -> SystemUI
```

### 4.2 Package responsibilities

| Package | Responsibility | Key files |
|---|---|---|
| Root | Xposed entry, app startup, native Rive bootstrap, log router, root capture, device facts, process identity, app preview | `MainHook.kt`, `DuoApp.kt`, `MainActivity.kt`, `RiveInit.kt`, `L.kt`, `RootLogs.kt`, `RootProbe.kt`, `DeviceFacts.kt`, `SystemUiProcess.kt`, `DuoPreview.kt` |
| `hook/` | SystemUI hook entry, host attach/hide/position, surfaces, transitions, renderers, mapping, state, diagnostics, events | `DuoHook.kt`, `DuoIconHost.kt`, `ContainerFinder.kt`, `DuoElement.kt`, `DuoRiveView.kt`, `DuoCanvasView.kt`, `DuoBinder.kt`, `DuoMapping.kt`, `DuoVisual.kt`, `SlotGeometry.kt`, `RingGeometry.kt`, `PanelHandoff.kt`, `DuoStateMonitor.kt`, `DuoGuard.kt`, `DuoLog.kt`, `Diag.kt` |
| `hook/rom/` | ROM adapter/detection, measured profile asset, resource lookup | `RomAdapter.kt`, `RomDetection`, `RomProfiles.kt`, `RomResources.kt` |
| `hook/events/` | Pure live-event contract and arbiter | `LiveEvent.kt`, `EventArbiter.kt` |
| `settings/` | Settings schema/provider/bridge, backup, actions, health, stock-icon inventory, updates, Telegram delivery, stage override | `DuoPrefs.kt`, `DuoSettingsProvider.kt`, `SettingsBridge.kt`, `BackupCodec.kt`, `DuoActions.kt`, `ModuleHealth.kt`, `StockIconInventory.kt`, `UpdateChecker.kt`, `TelegramLog.kt`, `StageOverride.kt`, `ShellService.kt` |
| `ui/` | Four-tab Compose shell, Home, Custom/Settings, Report, glass controls, previews, nav icons, theme | `DuoShell.kt`, `DuoHomeScreen.kt`, `DuoSettingsScreen.kt`, `DuoSettingsSections.kt`, `DuoSettingsComponents.kt`, `DuoReportScreen.kt`, `GlassSurface.kt`, `GlassControls.kt`, `DuoSettingPreview.kt`, `DesignDemo.kt`, `DuoTheme.kt` |
| `rive/duo/` | Authored Rive scenes and build project | `scene.rml` (new/2 SIM), `scene_old.rml` (Classic), `rive.yaml` |
| `app/src/main/res/raw/` | Shipped Rive binaries | `duo.riv`, `duo_old.riv` |
| `app/src/main/assets/` | Measured/static ROM profiles and Xposed entry | `rom-profiles.json`, `xposed_init` |
| `tools/` | Static gates and measurement helpers | `route-module-logs.py`, `check-rive-eases.py`, `check-perf.py`, `check-cell-report.py`, `check-rom-profile-assets.py` |
| `docs/evidence/` | Screenshots, captures, logs, measurements (git-ignored) | `agile-1.7/`, `agile-1.8/`, `phase7/`, `full-app/` |

### 4.3 Important ownership boundaries

- **Size:** `SlotGeometry` pins the main slot width once at attach. Extras reuse that width; only the bar height caps them. Never re-measure after hiding the stock icons.
- **Seat:** the main ring is right-seated by default (`0 = physical right edge`); Camera/`lockRing` is a separate centered mode. QS uses the measured battery anchor with `alignEnd`; the shade header uses the v89 extra seat. Extras never use the main `rightSeat` path.
- **Battery line:** one level-derived `batteryLineStart` feeds Rive, Canvas and preview. The line's moving start is the left end; 0% draws a zero-length line at that end, 100% fills the span.
- **Invert center:** persisted as `middle_slot=percent`; no new settings column. `middleY = -43.35`, `middleScale = 0.55`, `boltY = 49.5`; the Rive `eventGroup` y is 17. One percentage only.
- **Panel hand-off:** `PanelHandoff` owns the crossfade/swap order; outgoing copy is held until the incoming copy is drawable. Do not add a second owner in a layout callback.
- **Rive:** `DuoBinder` property names and `PROPERTY_COUNT` are a frozen contract. Both scenes must match. `DuoRiveView` resolves the raw resource by id, not path.
- **Glass:** Backdrop 1.0.1 is the only glass library. The app-side pill owns the blur. The selector has no blur. The live path has one rim owner (Backdrop highlight); the manual rim is fallback-only.

---

## 5. Timeline: Plan v2 to alpha.8

### 5.1 Plan v2 phases 0-8

| Step | Version / branch | Key outcome | Evidence |
|---|---|---|---|
| Phase 0 | docs/evidence, no code | Requirements, test plan, repair catalog, tools, measured baseline; 8 vendor maps planned | `agent/archive/originals/PLAN-v2.md` §5; `agent/archive/originals/UI-MATRIX.md` |
| Phase 1 | `1.4.3-beta.1` (code 24), `phase/1-shade-qs` | Shade/QS/header owner follows SystemUI panel state; one visible copy; stock restored; unsafe reparent path removed; CPH2747 owner-approved | `agent/archive/originals/PHASE-1-REPORT.md` |
| Phase 2 | `1.4.3-beta.1` (code 24), `phase/2-multi-ui` | `DuoLog` structured line, 2 MB ring, report ZIP, PII redaction, capability fields; 261 tests | `agent/archive/originals/PHASE-2-REPORT.md` |
| Phase 3 | `1.4.3-beta.4` (code 27), `phase/3-app-customization` | Three-page shell, engine selector, presets, backup/import core, middle-slot choices, per-icon Wi-Fi/cell toggles, event prototype; 317 tests | `agent/archive/originals/PHASE-3-REPORT.md` |
| Phase 4 | `1.4.4-beta.3` (code 30), `phase/4-cell-accuracy` | Explicit stock-level-to-four-dot mapping, slot-aware SIM selection, raw/max/dots diagnostics; 319 tests; CPH2747 full/5G measured | `agent/archive/originals/PHASE-4-REPORT.md` |
| Phase 5 | `1.5.0-beta.14` (code 44), `phase/5-liquid-glass` | Liquid-glass shell locked: selector physics, glass tokens, controls, scroll edges; 319 tests | `agent/archive/originals/PHASE-5-REPORT.md` |
| Phase 6 | `1.5.0` prototype on beta.14, `phase/6-rive` | Charging settle pulse, 20% shake, alarm/flashlight/screenshot motions and Canvas clones; 321 tests | `agent/archive/originals/PHASE-6-REPORT.md` |
| Phase 7 | `1.5.0-beta.20` (code 50), `phase/7-multi-ui-perf` | 9 ROM profiles (2 measured, 7 static), adaptive glass, `check-perf.py`; emulator p95 216.88 ms fails 32 ms budget; device pending | `agent/archive/originals/PHASE-7-REPORT.md` |
| Phase 8 | `1.5.0-beta.20` (code 50), `phase/8-release` | Code/gates complete; stable blocked on device, frame budget and owner approval | `agent/archive/originals/PHASE-8-REPORT.md` |

Plan v2's phase numbering stops being the delivery model after Phase 8; the 1.6/1.7/1.8 agiles below are the actual line to alpha.8.

### 5.2 1.6 and 1.7

| Step | Version / code | Key outcome | Evidence |
|---|---|---|---|
| 1.6.0 | code 76 | Fatal fixes, one Rive surface/import, safe fallback, per-icon hiding, event icons, PHDP download card, eight SIM dots/battery line | `git log`; `agent/archive/originals/PHASE-3-REPORT.md` |
| 1.7.0-alpha.1 | code 77 | ColorDuo glass values and nav foundation | commit `4a50bc4` |
| 1.7.0-alpha.2 | code 78 | Unified 0-100 logical size scale (`physical = 50 + 1.7 x logical`) | commit `c18786d` / `d7f46bb` |
| 1.7.0-alpha.3 | code 79 | Animated live-event previews | commit `9e937a0` / `46bc18c` |
| 1.7.0-alpha.4 | code 80 | Camera mode on Home | commit `da284ff` / `f64a433` |
| 1.7.0-alpha.5 | code 81 | Classic ring design and optical scale | commit `c07fa73` / `b2ccf51` |
| 1.7.0-alpha.6 | code 82 | Home ring-design selector | commit `c858a58` / `69b8702` |
| 1.7.0-alpha.7 | code 83 | Preview follows selected ring design | commit `8ffec8c` / `e0a1ffb` |
| 1.7.0-alpha.8 | code 84 | Live preview lab on Home | commit `0e1f9fc` / `571241e` |
| 1.7.0-alpha.9 | code 85 | Complete layout simulator | commit `3827c03` / `4ec95f7` |
| 1.7.0-alpha.10 | code 86 | Pause idle app preview renderer | commit `128edc0` / `63a70d8` |
| 1.7.0-alpha.11 | code 87 | Smooth horizontal panel hand-off; owner capture later measured 62.855 fps VFR | commit `37d2ece` / `5805a69`; `agent/archive/originals/AGILE-1.8-ALPHA11.md` |
| 1.7.0-alpha.12 | code 88 | Remove one-frame hand-off gaps; lock outgoing extra | commit `652024d`, `4b54538`, `ba5e301` |
| 1.7.0 stable | code 89 | 449 tests/51 suites; 158/158 easing; APK `3e15a8…a2a6`; owner accepted alpha.12 transition behaviour | `agent/archive/originals/RELEASE-NOTES-1.7.0.md` |

### 5.3 1.8.0 alpha series

| Step | Version / code | Key outcome | Evidence |
|---|---|---|---|
| alpha.1 | 1.8.0-alpha.1 / 90 | Selector, invert, preview, position groundwork; installed 18:14:24 with data kept; APK `fb3d1d…e4e8` | commit `0fc12e4`; `agent/archive/originals/AGILE-1.8-NOTES.md` |
| alpha.2 | 1.8.0-alpha.2 / 91 | Design-switch fatal fixed with two persistent Rive preview panes and static design cards; `batteryLineStart`; event seat `INVERTED_MIDDLE_Y=-43.35`; selector under tabs with 0.04 tint/no blur; installed 19:31:22; APK `5d0ddf…2a2f9` | commit `a2bb594`; notes |
| alpha.3 | 1.8.0-alpha.3 / 92 | Navigation icons packaged/tinted; 5-bar mapping 1,1,2,3,4; explicit `rightSeat` shade/home seat; gray 22% 2-SIM track; static Classic card; previews follow selected design; 483 tests; APK `6c1c38…e591` | commit `9467e98`; notes |
| alpha.4 | 1.8.0-alpha.4 / 93 | v89 extras placement; static selector rim with movement-only lens; invert bolt at ring centre in both designs and Canvas; hour-only clock; unsnapped Wi-Fi/cell/5G sliders; Classic card `#101014`; 482 tests; APK `6b3dc0…e9cf` | commit `e047324`; notes |
| alpha.5 | 1.8.0-alpha.5 / 94 | Shade returned to pre-Phase-1 `place()` seat with no battery anchor/clamp; Default/Compact/Custom chips and Compact shortcut removed; current settings become Custom baseline; blue selected/hovered icon, white others; APK `1bc96c…af86`; delivered message 43 | commit `d82fbd7`; notes |
| alpha.6 | code 95 | **No entry in the repository.** Git jumps from 94 to 96; do not invent changes. | `git log -p -- app/build.gradle.kts`; notes |
| alpha.7 | 1.8.0-alpha.7 / 96 | Pill owns all blur; selector below row with no blur; frosted 0.12 dark/0.06 light; inner/drop shadow and always-on highlight; blue selected/hovered; shade restored to v89 battery anchor; `H:mm`/`h:mm`; installed 13:34:13; APK `96892f…c068`; message 44 | commit `6611fe2`; notes |
| alpha.8 | 1.8.0-alpha.8 / 97 | Idle: row above selector, no blur; moving: selector above row, combined `selectorMovingBackdrop = page + pill + row`, lens/chromatic restored; shade remains v89 anchor; installed 13:50:03; APK `28b19f…c833`; message 45 | commit `3d5ea0d`; notes; owner confirmation |

The current alpha.8 line is a pre-release. It is not a stable release and has no store approval.

---

## 6. Mistakes registry

The registry uses five columns: ID, symptom, root cause, rule/fix, evidence. Existing C/M/m rows are preserved from the root `AGENTS.md`; new rows are the Plan v2 to 1.8 errors. Security and process rows use S/P prefixes.

### 6.1 Critical — can break System UI, lose icons, or mislead every user

| ID | Symptom | Root cause | Rule / fix | Evidence |
|---|---|---|---|---|
| C1 | SystemUI SIGSEGV on Rive start | `Rive.init` left `defaultRendererType` null; ReLinker looked in SystemUI's package and threw; the null reached native `Renderer.make()` | `RiveInit` loads the `.so` by absolute path, pins the type, initialises the C++ env itself; never call the library initialiser | `AGENTS.md`; `RiveInit.kt` |
| C2 | SystemUI crash while resizing Rive | Re-assigning `layoutParams`/resizing the `TextureView` while System UI ran | Capture size once on attach; re-assign bounds only when changed; size changes need a restart | `AGENTS.md`; `SlotGeometry.kt` |
| C3 | Rive breaker latched or tripped by sibling bars | Counted creations, not deaths; unrelated restarts latched it; every bar fell to Canvas | Count once per process before the risky creation; stale count re-arms; `DuoGuard` | `AGENTS.md`; `DuoGuard.kt` |
| C4 | A working module looked dead | `android.util.Log` from SystemUI was dropped | Use `L` (logcat + `XposedBridge`) everywhere; `route-module-logs.py --check` | `AGENTS.md`; `L.kt` |
| C5 | Module sat at stage 0; provider rejected every call | Bootstrapped from `ActivityThread.mSystemContext`, whose package is `android` while the uid is SystemUI | Accept only the app's package or SystemUI's own window context | `AGENTS.md`; `AppContextResolver.kt` |
| C6 | Icons hidden, element 0x0 | Injected into a `GONE` legacy strip (Android 17 AOSP) | Skip a `GONE` container; use the Compose icon view | `AGENTS.md`; `ContainerFinder.kt` |
| C7 | Icons hidden, ring missing on HyperOS | Injected into `MiuiStatusBatteryContainer`, which lays out only its own children | Inject into a plain `FrameLayout` and anchor over the battery | `AGENTS.md`; `ContainerFinder.kt` |
| C8 | 130 mAh vs ~15 mAh drain | Looping Rive idle animation drew for the process lifetime | Pause the renderer when hidden/idle; restart on a real change | `AGENTS.md`; `DuoIconHost.kt` |
| C9 | "Wi-Fi icon not show" | Canvas fallback drew no Wi-Fi while stock Wi-Fi was hidden | Every occupant the Rive path draws must exist in Canvas from the same geometry | `AGENTS.md`; `DuoCanvasView.kt` |
| C10 | `useRive=true` but stage 1 forever | Leftover `duo_statusbar_stage` override, invisible in the app | Surface the override, explain it, make it clearable; never hide a developer switch | `AGENTS.md`; `StageOverride.kt` |
| C11 | Module gave up at stage 0 forever | "Provider unreachable" was treated as "user switched it off"; first read landed before the app process was ready | Distinguish unreachable from off; retry; re-read on every change | `AGENTS.md`; `DuoSettingsClient.kt` |
| C12 | App/SystemUI crash on design switch or repeated preview imports | Rive views were re-created/imported on layout/design changes; each `File::import` leaked ~110 Paint global refs; 444 imports produced 48,771 refs against the 51,200 limit; 8-12 attaches/s; persistent panes now keep one view per scene | One Rive view/import per surface; never re-import on layout/design switch; keep persistent preview panes (`DuoPreview.kt`); use static design cards; add import/global-ref counters and a hard guard | `agent/archive/originals/BRAINSTORM-2026-11-02.md` §3.9; `agent/archive/originals/LIST-THE-PLAN.md` §6.6; `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.2; `DuoPreview.kt` |
| C13 | Home design cards with live Rive views crashed the app on design switch | Multiple live Rive surfaces were created/destroyed by the design cards, repeating the import/global-ref path | Home cards use static assets (`classic_demo.png`, `design_dual.png`) on a `#101014` background; no live Rive view is created per card; live preview remains one pair of persistent panes | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.2; `ui/DesignDemo.kt`; `ui/DuoHomeScreen.kt` |

### 6.2 Moderate — broken behaviour, misleading reports, wasted battery

| ID | Symptom | Root cause | Rule / fix | Evidence |
|---|---|---|---|---|
| M1 | First Rive frame only; later changes not drawn | Animations defaulted to `oneShot`; state machine stopped | Idle animations `loopValue="loop"`; start the machine explicitly | `AGENTS.md`; `rive/duo/scene.rml` |
| M2 | 27 keyframes ran linear while looking finished | Cubic keyframes with no interpolator | `tools/check-rive-eases.py` in CI | `AGENTS.md`; `tools/check-rive-eases.py` |
| M3 | Nothing attached on HyperOS/One UI/Vector | Window hook installed too late; System UI already running | Install the window hook first; adopt an existing window | `AGENTS.md`; `DuoHook.kt` |
| M4 | Every hook skipped on HyperOS | Matched the process by package only; HyperOS reports `packageName = "system"` | Match the process name too (`SystemUiProcess.isTarget`) | `AGENTS.md`; `SystemUiProcess.kt` |
| M5 | Wi-Fi/cells/DND/airplane stayed white in light mode | Only ring + percentage followed the bar colour | Bind every foreground shape to the same colour property | `AGENTS.md`; `BarTint.kt` |
| M6 | Strong Wi-Fi showed as 2/3 bars | Fixed Wi-Fi thresholds | Read the active network's own `WifiInfo` and platform mapping; Wi-Fi only when validated | `AGENTS.md`; `WifiReader.kt` |
| M7 | First frame drew a full ring | Battery monitor started at 100 | Read sticky `ACTION_BATTERY_CHANGED` at start | `AGENTS.md`; `DuoStateMonitor.kt` |
| M8 | Element fell back to Canvas; `.riv` not found | Loaded `duo.riv` by path; R8 renamed the resource | Resolve by resource id (`openRawResource`), with fallbacks | `AGENTS.md`; `DuoRiveView.kt` |
| M9 | Every root report empty | Root capture only tried `su`; locked-bootloader root has no `su` on the app PATH | Discover root at known paths; always emit non-root device facts | `AGENTS.md`; `RootLogs.kt` |
| M10 | Report capture could deadlock or hang | Read two process streams in sequence with no timeout | Merge stderr, read once, bound the wait | `AGENTS.md`; `RootLogs.kt` |
| M11 | Restart button dead on some ROMs | Restart receiver registered after the enable gate | Register before the gate; fall back to root/Shizuku | `AGENTS.md`; `DuoHook.kt` |
| M12 | DND crescent drawn with DND off | A silenced ringer counted as Do Not Disturb | DND is the interruption filter only; silent is separate; unit-test the boundary | `AGENTS.md`; `DeviceStateReaderTest` |
| M13 | Closed track drawn under the digits | Canvas fallback hard-coded `drawCircle` while Rive used bound `leftArc`/`rightArc` | Canvas draws from the same bound geometry as Rive | `AGENTS.md`; `DuoCanvasView.kt` |
| M14 | Keyguard/shade fell to Canvas after main bar | Breaker counted attempts but `riveAllowed` still consulted the full counter | Once a Rive view is created this process and the process is alive, allow Rive for the rest of the process; unit-test `shouldAllowRive` | `AGENTS.md`; `DuoGuard.kt` |
| M15 | Ring bigger on lock screen/shade | Each extra bar captured its own slot width | One size owner: extras reuse the width pinned for the main bar; only this bar's height caps it | `AGENTS.md`; `SlotGeometry.kt` |
| M16 | Every report `FileNotFoundException: rom-profiles.json` | `RomProfiles.load` read from the host SystemUI context | Read module assets through the module package's context (`createPackageContext`) | `AGENTS.md`; `RomProfiles.kt` |
| M17 | Element flipped black/white several times a second | `BarTint` read a `darkIntensity` int as an icon colour; HyperOS passes the intensity as an int | Only accept an int with a non-zero alpha byte; unit-test `BarTint.pickTint` | `AGENTS.md`; `BarTint.kt` |
| M18 | Icons and labels blurred inside the selector | Blur was applied to the selector/layer that also sampled the row, so the tab art was softened | Blur belongs to the background/pill only. Selector has **no blur** in either state. Idle: row above selector (owner-confirmed). Moving: selector above row, combined `page + pill + row` backdrop only for the lens/chromatic effect (restored in alpha.8, not yet visually confirmed). | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.7/alpha.8; `ui/DuoShell.kt` z-order/backdrops; project memory decisions; owner brief 2026-11-06 |
| M19 | Shade copy oscillated/jumped between seats | The shade seat changed through pre-Phase-1 `place()`, battery anchor, a removed clamp, then a `rightSeat` path; each change moved the copy | Final: use the 1.7.0/v89 extra placement for the shade header, `anchorOnBattery(alignEnd = true)` for the QS cluster, and **no shade-only clamp**. Owner confirmed fixed. | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.5/alpha.7; `agent/archive/originals/CODE-REVIEW-REPORT-1.7.0.md` §6.3; `DuoIconHost.applyExtraLayout` / `anchorOnBattery`; owner brief 2026-11-06 |
| M20 | Shade jumped after the "right seat" change | `rightSeat` was intended for the main ring but was applied to extras/indicators | `rightSeat` is main-ring only; extras keep their fixed battery/flow seat; indicators always pass `rightSeat = false` | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.3; `DuoIconHost.place` comment and call sites |
| M21 | Battery line did not follow level or grew from the wrong end | The line was static or its trim endpoint was bound to the wrong edge | One moving start, `batteryLineStart`: the left end is the zero origin; 0% draws zero length, 100% fills the span; Canvas and preview read the same value | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.2/alpha.4; `hook/RingGeometry.kt`; `hook/DuoCanvasView.kt`; `hook/DuoMapping.kt` |
| M22 | Full signal showed 1/3 dots; raw 0 looked like no service | 5-level stock source was mapped as if it were a 4-level index; raw 0 was conflated with "no service" | 5-bar table: raw 0..4 with service -> 1,1,2,3,4 dots. Raw 0 is a weak-but-present first bar, not no-service. `hasService`/raw < 0 is the separate no-service case. | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.3; `hook/SignalMapping.kt`; `DuoMappingTest` |
| M23 | Invert center had a stray bolt/event overlay, a duplicated number or a mis-seated middle occupant | The bolt/event group and the middle group were not compensated for the invert seat; the percentage and old network text could both draw | One percentage in the centre; normal middle occupant above at `middleScale = 0.55`; `middleY = -43.35`; `boltY = 49.5`; Rive `eventGroup` y = 17; Canvas uses the same seats | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.2/alpha.4; `hook/RingGeometry.kt`; `hook/DuoMapping.kt`; `rive/duo/scene.rml:229`; `DuoMappingTest` |
| M24 | Clock showed AM/PM or only the hour | The 12-hour format included AM/PM; a later fix over-corrected to hour-only | Final: `H:mm` for 24-hour and `h:mm` for 12-hour; minutes always shown; no AM/PM text in the ring | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.4/alpha.7; `hook/DuoStateMonitor.kt`; `ClockTimeHelperTest` |
| M25 | Sliders stuck at an endpoint | The drag accumulator added snapped values, so repeated snapping lost the drag fraction at the ends | Keep an unsnapped `dragFraction`; start from the pointer position on the whole track; snap only the emitted/display value; never feed the snapped value back into the accumulator | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.4; `ui/GlassControls.kt`; `GlassSliderTest`; owner brief 2026-11-06 |
| M26 | The Custom tab showed no presets, but preset code/tests stayed behind | Default/Compact/Custom chips and the Compact shortcut were removed; `DuoPresetStore`, `DuoPresets` and their tests were not deleted | Current settings are the Custom baseline. Delete the dead store/presets and update `DuoPresetsTest`, `DuoSizePersistenceTest`, `RingDesignTest`, `CameraModeTest`; do not reintroduce a second design/preset selector | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.5; `settings/DuoPresetStore.kt`; `settings/DuoPresets.kt`; test references |
| M27 | Classic card was animated with a maroon background | The design card used a live/animated surface and a wine background | Classic card is a still image on a black `#101014` background; no live Rive view per card; the fixed demo must not follow the selected design | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.3/alpha.4; `ui/DesignDemo.kt`; `ui/DuoHomeScreen.kt`; owner brief 2026-11-06 |

### 6.3 Minor — noise, duplication, cosmetic, docs

| ID | Symptom | Root cause | Rule / fix | Evidence |
|---|---|---|---|---|
| m1 | Duplicated resource-id lookups | Repeated direct lookups | Route through `RomResources.id` | `AGENTS.md` |
| m2 | Repeated detector re-creation / duplicate log lines | No once-guard | Log once; keep the log worth reading | `AGENTS.md` |
| m3 | Duplicate entries in `Diag.CANDIDATE_IDS` | Probe list drift | Keep the probe list clean | `AGENTS.md` |
| m4 | Hard-coded `"com.android.systemui"` | Vendor string duplication | Use `RomAdapter.systemUiPackage` | `AGENTS.md` |
| m5 | `restartSystemUi` always returned `true` | Ignored the real outcome | Report the real outcome, including timeout | `AGENTS.md` |
| m6 | README emoji/marketing tone | Casual copy | User docs are formal, short and plain | `AGENTS.md` |
| m7 | Previews white-on-light, invisible | Preview background did not show the real thing | Previews must show the real thing on a readable background | `AGENTS.md` |
| m8 | `SlotGeometryTest` is an empty class | The `needsTallerParent`/shadeHeader change was reverted, but its test file remained | Either write the real slot-geometry tests or delete the empty file; an empty suite is false coverage | `agent/archive/originals/CODE-REVIEW-REPORT-1.7.0.md` §6.3; `app/src/test/.../SlotGeometryTest.kt` |
| m9 | Dead preview code and stale references | `PreviewSimulator.kt` was deleted; a comment still names it; `DuoCellSimulationPreview` was removed; `StockIconHider.isReplaced` has no production caller | Remove stale comments/references; delete test-only code or give it a production caller | `agent/archive/originals/CODE-REVIEW-REPORT-1.7.0.md` §5; `hook/RingGeometry.kt:163`; `StockIconHider.kt` |
| m10 | Duplicate/superseded documents | Plans, requirements, test plans, phase reports and review reports accumulated over eight phases | This file is the canonical summary; old files live under `agent/archive/originals/`; do not create another parallel plan | `agent/` listing; section 1.3 |
| m11 | Mixed EOL and whitespace-only churn | Windows/Linux editing and broad rewrites | Normalize before a real commit; do not mass-rewrite; use `git diff --check`; keep one feature per diff | `agent/archive/originals/AGENT.md` EOL caveat; `agent/archive/originals/TEST-PLAN.md`; owner brief |
| m12 | Notes say `classic_demo.gif`; the asset is `classic_demo.png` | The note was written before/after the static-asset change | Keep the note and asset names in sync; cite the file actually on disk | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.2; `app/src/main/res/drawable/classic_demo.png` |
| m13 | Custom-preset comments still describe a removed feature | `DuoHomeScreen` comments mention Default/Compact/Custom and the Custom snapshot | Update comments with the code; no stale design guidance | `ui/DuoHomeScreen.kt` comments near `writeCameraMode`/`writeRingDesign` |

### 6.4 Security and process

| ID | Symptom | Root cause | Rule / fix | Evidence |
|---|---|---|---|---|
| S1 | Local tool output exposed a provider key | A command printed environment/config containing the key; the value is not recorded here | Never print environment variables, provider keys, tokens or full tool output. Redact before sharing. Rotate the key if it was exposed; do not repeat the value in docs, logs or commits. | Owner report 2026-11-06; no secret value is committed in the repository |
| S2 | A release could bake in a direct Telegram bot token | `app/build.gradle.kts` injects `TELEGRAM_BOT_TOKEN` from `.env`/CI as a fallback; the 1.7.0 shipped APK had a placeholder and no token pattern, but a local/CI release with a real token would violate the rule | Relay-only delivery. Keep the direct token empty in release builds or restrict it to a non-publishable build type. Add a release secret scan. | `app/build.gradle.kts:59`; `settings/TelegramLog.kt`; `agent/archive/originals/CODE-REVIEW-REPORT-1.7.0.md` deploy findings |
| P1 | Shared worktree conflicted during a stash recovery; work was recovered from `stash@{0}` without `git reset` or `git stash pop`; the stash is still retained | `git stash` was used in a shared/worktree workflow where another worktree or concurrent edits changed the base | Do not use `git stash` in shared worktrees. Use `git diff > backup.patch`, a backup branch, or copied files. Never pop/drop/reset without the owner. Keep `stash@{0}` until the owner says otherwise. | `agent/archive/originals/AGILE-1.8-NOTES.md` alpha.5; `git stash list` (stash from `a2bb594` retained) |

---

## 7. Current logic and structure review

### 7.1 What is clean

- **Entry and scope:** `MainHook` guards every step; `SystemUiProcess` matches both package and process name; `DuoGuard` owns the Rive allow/block decision.
- **Host resolution:** `ContainerFinder` keeps the measured ids first, then the anchor/role/Compose fallbacks, and logs every candidate. `RomProfiles` reads the module's own asset.
- **Rendering:** Rive and Canvas share `DuoMapping`/`DuoVisual`; both Rive scenes carry 158/158 eased keyframes; the new scene's fresh build matches the checked-in binary.
- **Settings:** the provider row and the signature-permission broadcast bridge are separate paths for ROMs where the provider is invisible; backup parsing is versioned and allowlisted.
- **Diagnostics:** `DuoLog` is the single structured sink; `L` is the only log router; the report ZIP has PII redaction and pruning.
- **Navigation:** four tabs, exactly one selected design, pure `hoverIndex`, Backdrop highlight as the single live rim owner, selector no-blur layering.
- **State math:** cell/Wi-Fi mapping, battery line, invert seats, clock format, position/right-seat, and slider fraction have pure tests.

### 7.2 Dead and orphaned code after the preset removal

| Item | Status | Evidence | Recommendation |
|---|---|---|---|
| `settings/DuoPresetStore.kt` | No production caller; class methods and companion are only referenced by tests | `grep -rn "DuoPresetStore" app/src` | Delete after the tests are rewritten, or keep only if a new preset UI is approved |
| `settings/DuoPresets.kt` | `DUO_PRESETS` and migration aliases are only used by the dead store/tests | `grep -rn "DUO_PRESETS\\|DuoPresets" app/src` | Delete with the store; remove the aliases `normalizePresetId`/`legacyPresetToNew` if unused |
| `settings/DuoPresetsTest.kt` | Tests the dead preset API | file exists | Delete or replace with a test of the current Custom baseline |
| `ui/RingDesignTest.kt` | References `DuoPresetStore.PREFS_NAME`/`setSelected`/`selected` only to assert a dead preset selection | test file | Keep the design-write assertions, drop the preset-store setup |
| `ui/CameraModeTest.kt` | Same dead preset-store setup | test file | Same |
| `settings/DuoSizePersistenceTest.kt` | Uses `DUO_PRESETS.first { "default" }` at line 84 | test file | Replace with a direct `DuoSettings` fixture |
| `hook/SlotGeometryTest.kt` | Empty class; contributes no tests | file exists; 57 suites/482 tests do not include a real slot test | Write real size/seat tests or delete |
| `runtime comment in hook/RingGeometry.kt:163` | Says geometry was moved from `ui/PreviewSimulator.kt`; that file is deleted | `grep -rn PreviewSimulator app/src` | Update the comment to name the current owner |
| `settings/StockIconHider.kt` `isReplaced` | Production method has only test callers | `grep -rn "isReplaced" app/src` | Delete or use it in the hide path |
| `ui/DuoHomeScreen.kt` comments near `writeCameraMode`/`writeRingDesign` | Describe Default/Compact/Custom and the Custom snapshot | source comments | Update to the current one-design-selector behaviour |
| `agent/archive/originals/AGILE-1.8-NOTES.md` `classic_demo.gif` | Asset on disk is `classic_demo.png` | file listing | Correct the note or provide the GIF deliberately |

No other dead production class was confirmed in this pass. `ModuleHealth` is used by `ModuleHealthCheck`; `DesignDemo` is used by Home; the Compose helpers in `DuoHomeScreen` are used inside that file.

### 7.3 Folder organisation recommendations (partly executed)

1. **Archive, do not delete (done 2026-11-06):** the superseded `agent/*` documents now live in `agent/archive/originals/`, concatenated in `agent/archive/AGENT-DOCS-DEPRECATED.md`; this file stays at `agent/AGENTS-V2.md`.
2. **One current plan:** keep one requirements/test/release section in this file; move phase history to the archive.
3. **Evidence index:** add a short `docs/evidence/README.md` index (dates, device, build, path). Evidence remains git-ignored.
4. **Hook subpackages:** `hook/` is large (35+ files). A future, owner-approved refactor could split it into `hook/host`, `hook/render`, `hook/state`, `hook/diagnostics`; do not do this during a behaviour fix.
5. **Dead-code pass:** after owner approval, remove the preset store/presets/tests and the empty `SlotGeometryTest`; update the three tests that reference the dead store.
6. **Rive build:** add a dedicated old-scene build step (separate project or a generated project directory) so `duo_old.riv` is reproducible; CI currently checks only `scene.rml`.
7. **Docs naming:** use `AGENTS-V2.md` until the owner renames it to `AGENTS.md`; do not keep two active copies.

### 7.4 Structure risks found

- `DuoIconHost` is the largest file and owns attach, hide, seat, transitions, size and renderer lifecycle. The ownership boundaries are documented, but a future refactor must freeze the diagnostic dump first.
- `DuoPresetStore`/`DuoPresets` still compile into the app; their tests give a false sense that presets are supported.
- The old Rive scene has no automated fresh-build/hash gate; the checked-in hash can drift from `scene_old.rml` without CI noticing.
- The direct Telegram token fallback remains compiled into `BuildConfig` even though the relay is the intended route.
- The static design-card implementation intentionally conflicts with the 1.8 DEMO3-01 requirement; the requirement must be amended to the owner's final decision rather than silently left contradictory.

---

## 8. Build, test and release checklist

### 8.1 Pre-change

```bash
git status --short
git branch --show-current
git log -1 --oneline
# no git stash in a shared worktree
git diff > /tmp/duo-before-change.patch
```

- Confirm the branch and that `.kilo/` is the only untracked entry.
- Confirm the owner's current device/version and the rollback APK.
- Read this file and the current dump before editing.

### 8.2 Build and unit tests

Use Java 21; the default Java 25 is not supported by this AGP.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
./gradlew :app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline --console=plain
```

Current result: 482 tests / 57 suites, 0 failures; release APK built.

### 8.3 Static gates

```bash
python3 tools/route-module-logs.py --check
python3 tools/check-private-data.py
python3 tools/check-rive-eases.py rive/duo/scene.rml
python3 tools/check-rive-eases.py rive/duo/scene_old.rml
git diff --check
```

Current result: route 0; private-data check clean; both scenes 158/158 with 0 inert. `git diff --check` is the only whitespace gate; there is no repository-level whitespace checker.

### 8.4 Rive hashes and parity

```bash
bash tools/build-rive-both.sh
```

- The script builds `scene.rml` and `scene_old.rml` through a temporary scene swap in an isolated copy, then requires each fresh `.riv` to equal its shipped file and the two hashes to differ.
- It fails closed: the runner must provide the Rive CLI (the current scenes require **1.9.1**) and a missing or outdated CLI fails the job instead of skipping the gate.
- Check both `.rml` property sets against `DuoBinder` and `PROPERTY_COUNT` before release.
- Check the packaged APK entries (`res/zy.riv`, `res/U1.riv`) because resource shrinking renames them.

### 8.5 Device checklist (owner-run)

1. Install `app-release.apk` with `adb install -r`; confirm `versionCode=97`, `versionName=1.8.0-alpha.8`.
2. Restart SystemUI after the module update.
3. Confirm the module is active in SystemUI and `stage=2`, `renderer=Rive`.
4. Shade/QS: confirm the v89 battery-anchor seat, no clamp, no jump between collapsed/QS/shade.
5. Selector: idle row above the selector; icons/labels sharp; no blur on the selector; moving selector above the row; lens/chromatic aberration visible while dragging.
6. Battery line: 0/19/49/80/100 monotonic and clamped; Classic unchanged.
7. Invert center: one percentage, centred; middle occupant above at the smaller size; bolt/event not duplicated.
8. Clock: `H:mm` in 24-hour and `h:mm` in 12-hour; minutes present; no AM/PM.
9. Wi-Fi/cell/5G sliders: drag from both ends; no endpoint sticking; whole track responds.
10. Design cards: Classic still on black, 2 SIM still on black; no live Rive crash.
11. Capture: LSPosed `DuoSB` log, screenshot, `dumpsys package` version, and any crash buffer. Confirm no FATAL/SIGSEGV/SIGABRT.
12. Other ROMs remain `unverified` until they have their own dump/screenshot/log.

### 8.6 Current hashes

| Artifact | SHA-256 |
|---|---|
| `app/build/outputs/apk/release/app-release.apk` | `28b19f230d705baed4b55faac6616e15319bc1c53578034706f29999a8a1c833` |
| `app/src/main/res/raw/duo.riv` | `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567` |
| `app/src/main/res/raw/duo_old.riv` | `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09` |
| `rive/duo/build/duo.riv` (fresh new scene) | `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567` |
| Packaged `res/zy.riv` | `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567` |
| Packaged `res/U1.riv` | `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09` |
| 1.7.0 rollback APK | `3e15a8a92502f73773c459baffe9c7fae80e19a93417ea4d041126192519a2a6` |

### 8.7 Release checklist

- Owner approval is mandatory before merge, tag, push, release or store action.
- Betas are pre-releases; never promote them to the LSPosed store.
- Verify both Rive scenes, both hashes, the packaged APK assets, the 482-test baseline and the new tests.
- Scan the APK for a direct bot token; prefer the relay only.
- Include rollback instructions and the 1.7.0 APK hash.
- Do not release while any owner-confirmed surface is unresolved or while a safety gate is failing.

---

## 9. Where to find evidence and archive

> The evidence tree was moved out of the repository to `../duoStatusBar-private-evidence/` on
> 2026-11-06. Paths below are relative to that external archive; never add it back to git. The branch's
> older commits still contain the evidence and must not be pushed until history is purged.

| Evidence | Location | Notes |
|---|---|---|
| Current session state | this file, section 2 | update after each owner test |
| Alpha.8 notes and prior alpha history | `agent/archive/originals/AGILE-1.8-NOTES.md` | historical; alpha.8 owner confirmation is in this file |
| 1.7/1.8 transition captures | `docs/evidence/agile-1.7/`, `docs/evidence/agile-1.8/` | git-ignored; owner video/report included |
| 1.8 device/tombstone captures | `docs/evidence/agile-1.8/device/`, `docs/evidence/agile-1.8/tombstones/` | check before claiming a device result |
| Phase 7 performance | `docs/evidence/phase7/` | emulator p95 failure and baseline |
| Full-app/legacy captures | `docs/evidence/full-app/`, `docs/evidence/` | mixed-era; do not treat as current |
| Owner Telegram deliveries | Telegram messages 36-45 | message 45 is alpha.8 |
| LSPosed module log | `/data/adb/lspd/log/modules_*.log`, `DuoSB` lines | pull through root; use `EVIDENCE.md` commands |
| Vendor APKs/corpus | `reverse/`, `corpus/` | git-ignored; never commit |
| User dumps | `diagnose from users/`, `from users/` | git-ignored; PII risk |
| Build artifacts/rollback | `build/`, `app/build/outputs/apk/release/` | ignored build outputs |
| Old agent docs | `agent/archive/originals/` | archived 2026-11-06; concatenated in `agent/archive/AGENT-DOCS-DEPRECATED.md` |

**Archive status (executed 2026-11-06):** the documents listed in section 1.3 live in `agent/archive/originals/`; their contents are concatenated in `agent/archive/AGENT-DOCS-DEPRECATED.md`. `agent/AGENTS-V2.md` and the new `agent/README.md` are the only current `agent/` documents. Do not delete evidence or old APKs.

---

## 10. Open decisions and next actions

### 10.1 Open decisions

1. **Alpha.8 acceptance:** the owner confirmed the shade seat and idle selector; decide whether the restored movement lens needs a visual confirmation/screenshot/log before closing alpha.8.
2. **Design cards:** amend DEMO3-01 from "animated runtime Rive" to "static owner-approved stills" to match the current implementation and the crash history, or reintroduce a safe one-view demo.
3. **Preset code:** delete `DuoPresetStore`/`DuoPresets` and their tests, or keep them for a future preset UI. The current UI has no preset chips.
4. **Old Rive scene:** implemented as `tools/build-rive-both.sh`; the remaining requirement is a CI runner with Rive CLI 1.9.1.
5. **Telegram token:** remove the direct-token fallback from release builds and enforce relay-only, or document an owner-approved exception.
6. **Provider key:** rotate it if the exposure was real; confirm no key or token appears in logs, reports, docs or commits.
7. **Stash:** keep `stash@{0}` until the owner confirms; decide whether it is still needed, then archive the patch instead of popping it.
8. **1.7 residuals:** decide whether Camera-mode `too-far` hand-off, Classic 127 px shade strip and the true-60 fps capture block 1.8 or move to 1.9.
9. **Docs:** approve the archive move and the one-document rule.

### 10.2 Ordered next actions

1. Owner installs/restarts on CPH2747 and captures the alpha.8 device evidence (shade, selector idle/moving, lens, battery/invert/clock/sliders, no FATAL).
2. Reconcile the alpha.8 note's "verification pending" with the owner-confirmed shade/idle result and the pending movement-lens check; attach the log/screenshot if available.
3. Run the both-scene Rive parity/hash check; add the old-scene build gate.
4. Remove or repair the dead preset code/tests and the empty `SlotGeometryTest` after owner approval; update the three referencing tests.
5. Resolve the DEMO3-01 static-vs-runtime conflict in the requirements and copy.
6. Remove the direct Telegram token from release builds; scan the APK; rotate any exposed provider key.
7. Keep the old `agent/*` docs archived under `agent/archive/originals/`; this file and `README.md` are the only current `agent/` documents.
8. Keep other ROMs `unverified`; do not claim support without a dump, screenshot and log.
9. Do not release, merge, tag, push or publish until the owner explicitly approves.

---

*End of AGENTS-V2.md. This file is documentation only; it does not change app code, tests, Rive scenes, or binaries.*
