# Duo Status Bar — Implementation Plan 1.6

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Version | 1.6 |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Plan ready for owner review. No 1.6 code started by this plan; the worktree already contains the pre-1.6 beta.45 build input (see §2.0). P0 must be approved first.** |
| Requirements | `agent/REQUIREMENTS-1.6.md` |
| Tests | `agent/TEST-PLAN-1.6.md` |
| Brainstorm | `agent/BRAINSTORM-2026-11-02.md` |
| Evidence notes | `agent/LIST-THE-PLAN.md`; `reverse/fatal-logs/`; `reverse/kilo-images/` |
| Baseline | beta.45 (code 75), `app/build/outputs/apk/release/app-release.apk`, SHA-256 `0e359a7953ca3be7f19b7dcefaa8e1da316e834646bbdeed87d60ca7dee3f1ba`; APK path is gitignored, copy it to rollback before any clean |

This plan is traceable: every task maps to requirement IDs, and every requirement has a test in `TEST-PLAN-1.6.md`. No code before P0 review/approval. No cosmetic work may land before P0's exit gate.

---

## 1. Objective

Ship a stable 1.6 that:

1. Eliminates the two proven SystemUI fatal paths.
2. Delivers the three-tab shell with the new icon set, transparent selector, hold-drag interaction, and the measured resting/held sizes.
3. Moves all customization into Customization with persistent Default/Compact/Custom snapshots and a change-driven live preview.
4. Decomposes the ring into Rive objects: one centre object (Wi-Fi + event), a two-seat **battery percentage** (not the removed Charging %), a dedicated clock, eight SIM dots, and a bottom battery line with a split transition.
5. Makes download progress override charging, returns to normal at completion/100%, and integrates PHDP honestly.
6. Replaces simulation/data sections with GIF previews and removes the approved dead controls.
7. Preserves Canvas parity and marks other UIs truthfully.

---

## 2. Ground truth

### 2.0 Current worktree baseline (must preserve before P0)

The true baseline is **HEAD `670734d` + a dirty working tree**, not HEAD alone:

- Branch `phase/8-release`; HEAD is beta.36 and does not compile against the worktree because `DuoShell.kt` imports untracked `DuoHomeScreen.kt`, `DuoReportScreen.kt`, `DuoDesign.kt`.
- 18 modified files, 20 untracked top-level entries; no stash/branch contains the worktree blobs.
- The dirty tree is the beta.45 build input; `app-release.apk` SHA-256 `0e359a…f1ba` matches the plan and embeds the worktree `duo.riv` (`e4c4f4…716d`), not HEAD.
- P0 files (`DuoIconHost.kt`, `DuoRiveView.kt`, `DuoHook.kt`, `DuoGuard.kt`) are clean.
- `AppContextResolver.kt` contains an accidental ~5.7 KB tool-list paste in a KDoc; fix in its own commit, never mix with P0.
- Pre-1.6 UI/report/relay/unlock changes are mixed into the same dirty tree; they conflict with parts of 1.6 (4 tabs, wine selector, Material icons) and must not be assumed to be 1.6 work.
- The only beta.45 APK is under gitignored `app/build/outputs/apk/release/`; copy it to a stable rollback path before any clean.

**P0.0 (preserve baseline, no feature code):**
1. Create a WIP branch/commit from HEAD with only app/rive/relay/gradle/agent work; exclude `opencode-arabic/`, `report-bot/`, `from users/`, `docs/evidence/`, `.github/`, and PII-bearing logs.
2. Copy the beta.45 APK to a stable rollback path.
3. Revert the `AppContextResolver.kt` comment corruption separately.
4. Branch `fix/1.6-stability` from the preserved WIP baseline, not HEAD.
5. Only then apply P0.

### 2.1 Crash evidence (P0)

- **A. QS rotation Java crash:** `system_app_crash@1793641719781.txt` — `ConstraintSet.clone` throws because an injected `DuoRiveView` has `id=-` inside OnePlus's `OplusQSRootViewComponent` `ConstraintLayout`. `DuoIconHost.kt:972` calls `addView`; target logged as `ConstraintLayout[1272x2772]` at `DuoIconHost.kt:984-987`.
- **B. JNI overflow:** `tombstones/tombstone_03` — 444 `FallbackAssetLoader` refs (≈444 `.riv` imports inferred) and 48,771 `Paint` global refs (~109.8 Paint/import), hitting the 51,200 limit. The LSPosed log shows the attach/import storm while `lock_ring=true`; the same storm repeated on restarts. Stack: `DuoHook.addShadeLayoutListener → attachExtraBars → DuoIconHost.attachInto → syncExtraIndicators → ensureCluster → RiveAnimationView.onAttachedToWindow → File::import`.
- **C. LuckyTool:** `tombstone_01`/`tombstone_28` — separate boot SIGABRT in `libdexkit.so` on Oct 31; different time/PID. Not the Nov 2 cause.

### 2.2 Owner decisions

- Three tabs: Home / Customization / Report; Settings from a gear on Home.
- Camera/Compact: main status bar only; QS/shade/keyguard normal; tap opens Resize sheet; no live view resize.
- Remove Charging animation + Charging % middle option.
- Keep Icon Colour + add white↔black GIF.
- Calls: UI removed, event permanently off, code kept.
- Rive primary, Canvas kept.
- PHDP test button opens the PHDP app; the user runs PHDP’s own test.
- Split battery design is `new duo customization`: number above, Wi-Fi centre, eight SIM dots, battery line bottom.

### 2.3 Design assets

- Icons: filled house, sliders, documents+trend. Redraw as original vectors; the report reference is Icons8 stock and must not be shipped as-is.
- Selector: rest 225×179 px inside 207 px pill; held 296×231 px; ≈1.30×; held overflows; active icon grows.
- Ring: 8 dots (4/side), battery line, number above, Wi-Fi centre.
- GIFs: original Duo appear/disappear on black; event previews; icon-colour white↔black.

---

## 3. Branch and safety model

- Base: current stable branch after beta.45. Create one branch per phase: `fix/1.6-stability`, `feat/1.6-shell`, `feat/1.6-ring`, `feat/1.6-downloads`, `feat/1.6-burnin`, `release/1.6`.
- P0 is the only branch allowed to touch `DuoIconHost`/`DuoRiveView`/`DuoHook` first; all other branches rebase on it.
- Keep beta.45 APK and settings export as rollback.
- Never clear logs on the owner's phone before pulling.
- A failed P0 test stops the line; do not proceed to UI.
- Any new surface/class/hook must be measured; otherwise mark `unverified`.

---

## 4. Phase overview

| Phase | Goal | Requirements | Exit gate |
|---|---|---|---|
| P0 | Stop QS rotation + JNI crashes; 100% charging; preserve beta.45 baseline | STB-01…08; CUS-16 main-bar guard | STB-01…08 pass; 20 rotations + 60 s shade/camera stress: zero fatal, stable PID, no safe mode |
| P1 | Three-tab shell, icons, selector, presets, settings cleanup, GIFs, sliders, report move | NAV-01…08; CUS-01…15, CUS-17 | Compose/device cases; no dead control; preset round-trip; storyboard drafted |
| P2 | Rive ring decomposition, clock, eight dots, split, camera, Canvas parity | RING-01…10; CUS-16 full camera/Resize | Headless storyboards; owner approves split before RING-05; hash/ease; Canvas parity |
| P3 | Download/PHDP precedence and UX | DL-01…09 | Real/simulated download cases; no charging regression |
| P4 | Burn-in protection | BURN-01…03 | 2 cycles + 8 h battery |
| P5 | All-UI verification, regression, docs, release candidate | QA-01…05 | Matrix + full suite + owner install |

---

## 5. P0 — Stop SystemUI deaths (blocker)

### P0.1 Stable ID for injected views

**Requirement:** STB-01, STB-04, STB-08  
**Files:** `app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoIconHost.kt` (attach path around 960–1000), any other `addView` path in `DuoIconHost`/`DuoHook`; tests.  
**Task:**
- Assign a stable unique ID at **every injected-view creation** before any `addView`, not only when the runtime parent happens to be a `ConstraintLayout`. Audit all sites: `DuoIconHost.kt` 972, 1414, 1439, 1485, 1722, 1797–1798, 1811/1819, 2036, 2108, 2307, 2314; `DuoRiveView.kt:154`; `ContainerFinder.kt:330` fallback parent.
- Use one constant ID per surface/instance; never reuse duplicate IDs in the same parent.
- Prefer a host container that does not require IDs if that is the existing measured route; fall back to a plain `FrameLayout` only if the contract proves it.
- Log parent class + id in every attach line; add a debug detector that fails if any injected child has `View.NO_ID` in a `ConstraintLayout`.
- Do not change view hierarchy/geometry in this task.

**Weapons:** `rg`, `logcat`, LSPosed verbose, `dumpsys`, `./gradlew test`, `T-STB-01`.

**Exit:** QS rotation 20×; no `ConstraintSet.clone`; log shows the ID.

### P0.2 One Rive view/import per surface

**Requirement:** STB-02, STB-03, STB-07  
**Files:** `DuoRiveView.kt` (load/attach path, `ensureCluster` callers), `DuoIconHost.kt` (`ensureCluster`, `syncExtraIndicators`, `attachInto`, `attachExtra`), `DuoHook.kt` (`attachExtraBars`, shade layout listener), `DuoGuard` (counters/breaker), tests.  
**Task:**
- Remove `.riv` import from any layout/attach callback that can run per pass. Cache one view per surface lifetime and reuse it.
- Never remove/re-add or reparent the cached Rive view on layout, shade, size, split, or preset changes. `onAttachedToWindow` runs **once per surface lifetime** (bar + configuration).
- A configuration change may destroy the old surface and create one new surface after the new config is known; account for native refs, and never create a new view while the old one is attached. No other detach/reattach is allowed.
- Add counters: `riveImports`, `riveAttachCount`, `liveRiveViews`, `duoViewReady`; log only on change; expose in diagnostics.
- Add a hard guard: if imports/attaches exceed a safe threshold per process, stop creating views and use Canvas; log the reason.
- Keep the Rive breaker and Canvas fallback.

**Weapons:** `T-STB-02`, LSPosed import/global-ref count, tombstone, `DuoGuard`, `review-strict`.

**Exit:** 60 s shade stress: one attach per surface lifetime, imports do not grow per layout; no JNI abort; no restart.

### P0.3 Camera/lock mode without reattach

**Requirement:** STB-03, CUS-16  
**Files:** `DuoIconHost.kt` (camera/lock placement), `DuoHook.kt` (layout listener), `DuoPrefs` (`lockRing`), Rive pose.  
**Task:**
- Camera/lock mode uses one fixed view and inner translation; no reparent/resize on shade/layout.
- QS/shade/keyguard force the Default placement; only the main bar gets camera placement.
- “Resize” opens the sheet; it does not resize the live view.
- Add a device test that toggles only `lock_ring=1` and watches attach rate.

**Weapons:** `T-STB-03`, `T-CUS-16`, phone, LSPosed logs.

**Exit:** 60 s camera mode: attach count stable; no JNI overflow.

### P0.4 Rotation lifecycle

**Requirement:** STB-04, STB-08  
**Files:** `DuoHook.kt:501-514`, `DuoIconHost.kt:1305-1328`, `DuoRiveView.kt`.  
**Task:**
- Cancel/defer pending attach on configuration change; wait for the new configuration before creating a surface.
- No live `TextureView` resize/reparent; use the current measured capture-on-attach rule.
- Test portrait↔landscape with QS closed/open and camera on/off.

**Weapons:** `T-STB-04`, rotation harness, `dumpsys`, screencap.

**Exit:** 20 rotations per mode; no fatal/ANR; ring returns.

### P0.5 100% charging

**Requirement:** STB-06  
**Files:** `DuoStateMonitor.kt` charging state, `DuoMapping.kt`, `DuoIconHost`/Rive bindings, `DuoCanvasView.kt`.  
**Task:**
- Treat 100% + plugged as non-charging for visuals; hide bolt/stop charging loop; keep battery normal.
- Test 100%, 99%, unplugged, replug.

**Weapons:** battery dumpsys/root or emulator, `T-STB-06`, screenshots.

### P0.6 Crash telemetry and repair catalog

**Requirement:** STB-05  
**Files:** `Diag.kt`, `DuoGuard`, `agent/REPAIR-CATALOG.md`, `agent/AGENT.md`.  
**Task:**
- Persist `qs_constraint`, `rive_import`, `jni_refs` markers **before** the risky attach/import so the next boot can report which path preceded a SystemUI death.
- Record both signatures and the safe recovery (Canvas, restart prompt).
- Update the mistakes registry.

**Weapons:** `T-STB-05`, report export, `review-strict`.

**P0 exit:** all STB tests pass; owner confirms no safe mode; no UI work merged.

---

## 6. P1 — Shell, settings, interactions, GIFs

### P1.1 Three-tab shell + Settings gear

**Requirements:** NAV-01, NAV-08  
**Files:** `DuoShell.kt` (enum, nav items), `DuoScreen`/`MainActivity`, `DuoHomeScreen.kt`, `DuoReportScreen.kt`, strings.  
**Task:**
- Pages: `HOME`, `CUSTOMIZATION`, `REPORT`.
- Remove bottom `SETTINGS` and `ENGINE` pages; Settings opens as a sheet/page from a gear on Home.
- Home: status/health, preview, gear; no editing controls.
- Customization: presets + live preview + controls.
- Report: form + Contact Developer.

**Weapons:** Compose tests, screenshots, `T-NAV-01/08`. Add the missing `app/src/androidTest` Compose harness for nav/selector/slider interactions (none exists today).

### P1.2 Redraw icons

**Requirements:** NAV-02  
**Files:** `DuoIcons.kt`/`ui/icons`, drawables, `strings.xml`; no stock JPG/PNG.  
**Task:**
- Redraw house, sliders, documents+trend as original vectors matching all six references.
- Licence-review every reference; the report icon is Icons8. Do not ship any stock raster or traced path data.
- Add 1×/2× screenshots and a licence note covering all six sources.

**Weapons:** VectorPath/Compose, ImageMagick, `T-NAV-02`.

### P1.3 Selector

**Requirements:** NAV-03…06  
**Files:** `DuoShell.kt` selector block, `DuoColors`, `GlassTokens`.  
**Task:**
- Remove wine fill; add a `NavAccent` token (reference blue `#3C88F8`, no blue exists today); active icon/label accent; inactive neutral.
- Rebuild selector as a large ellipse around the active item; rest/held sizes from measurements; active icon scales.
- Move gesture ownership to the pill parent; long-press starts drag, selector jumps to pointer, follows, snaps.
- Preserve tap select/accessibility; no scroll theft.

**Weapons:** Compose `pointerInput`, screenshot measurement, `T-NAV-03/04/05/06`.

### P1.4 Slider and position interaction

**Requirements:** CUS-12, CUS-13  
**Files:** `GlassControls.kt:214-240`, `DuoSettingsComponents.kt:237-291`.  
**Task:**
- Knob-only drag with a ≥48 dp hit target; track taps no-op except TalkBack `setProgress`.
- Position icon-only drag; rail no drag.
- Test vertical swipes over tracks.

**Weapons:** Compose tests, `T-CUS-12/13`.

### P1.5 Preset state

**Requirements:** CUS-02, CUS-03  
**Files:** `DuoPresets.kt`, `DuoPrefs.kt`, `DuoHomeScreen.kt`, Customization page, backup.  
**Task:**
- `DuoPresetState` with `selected`, fixed `default`, fixed `compact`, `lastCustom`; persisted **per orientation** (portrait and landscape).
- Default = Wi-Fi centre, auto 5G/4G, percent above.
- Compact = camera ring + Resize; main bar only.
- Editing while Default/Compact switches to Custom and saves; Custom restores exactly.
- Migrate old `classic` → Default; old `compact`/`bold` → Custom to preserve pixels (new Compact is a camera ring, so it must not inherit old compact). Verify with old prefs/backup.
- Version backup schema.

**Weapons:** unit tests, preference/backup round-trip, `T-CUS-02/03`.

### P1.6 Settings cleanup and GIFs

**Requirements:** CUS-04…11, CUS-14, CUS-17  
**Files:** `DuoSettingsScreen.kt:534-663`, `DuoSettingsSections.kt`, `DuoPrefs.kt`, `DuoMapping.kt`, `DuoStateMonitor.kt`, drawables/strings.  
**Task:**
- Remove Smooth Graphics, System-Font Clock, the charging animation toggle (`chargingEnabled`), and `MIDDLE_SLOT_CHARGING` only; keep the separate `MIDDLE_SLOT_PERCENT` Battery % option and the charging bolt.
- Keep Icon Colour + white/black GIF.
- Remove Mic/Location/Volume from UI/code; Calls UI off/code kept.
- Remove Simulation + Cell Simulation; the four remaining event rows get GIF previews (Alarm, Flashlight, Screenshot, Unlock); no Play/level. Duo on/off and Icon Colour GIFs are separate.
- Remove duplicate About/Report; move Contact to Report; keep health/backup/update.
- Fix Wi-Fi glyph; no restart.
- Update search index.

**Weapons:** Compose, `AnimatedImageDrawable`, headless GIF generator, `T-CUS-*`, `T-CUS-14/17`.

### P1.7 Engine and preview

**Requirements:** CUS-01, CUS-04, CUS-15  
**Files:** `DuoShell.kt` EnginePage, Customization page, `DuoRivePreview`, `DuoPrefs.engineMode`.  
**Task:**
- Move engine selector into Customization; Rive default, Canvas fallback with note.
- Live preview always visible; no continuous loop; invalidate on edits; persist.
- Tapping the ring focuses/scrolls to controls.

**Weapons:** frame stats, Compose tests, `T-CUS-15`.

### P1.8 Duo on/off GIF

**Requirement:** NAV-07  
**Files:** `res/drawable`/assets, docs media, generator.  
**Task:**
- Generate original icon-appear/disappear on black; same loop style as prior GIFs.
- Record source frames/hash; no Shutterstock frames.
- Use `AnimatedImageDrawable`; start on attach.

**Weapons:** Rive frames/ImageMagick, `T-NAV-07`.

### P1.9 Split-transition storyboard (design, no code)

**Requirement:** RING-05 (draft only)  
**Task:** turn the `new duo customization` static layout into a short frame storyboard for the battery-line split and SIM-dot/battery reflow. No code; owner reviews the frames.  
**Weapons:** ImageMagick/Inkscape, Rive storyboard, owner review.

**P1 exit:** NAV and CUS-01…15/17 tests; preset persistence; no dead controls; screenshot review; split storyboard drafted. CUS-16 full camera is P2; its main-bar guard is P0.

---

## 7. P2 — Rive ring decomposition

### P2.0 Read and freeze

**Requirement:** RING-09  
**Files:** `docs/rive-pipeline.md`, `rive/duo/AGENTS.md`, `rive/duo/scene.rml`, `tools/check-rive-eases.py`.  
**Task:** read the Duo Rive system; record artboard, layer order, states, 8-digit ARGB, `StraightVertex` rule, 0.2 s final hold, no constraints.

### P2.1 Shared geometry table

**Requirement:** RING-08  
**Files:** Kotlin geometry (`DuoMapping`/new table), Canvas, Rive bindings.  
**Task:** one source for ring radius/stroke, centre, percent/clock boxes, 8 dot angles, battery line angles, camera offset. Unit-test values. Rive constants derived from the table.

### P2.2 Centre object + event

**Requirement:** RING-01, RING-07  
**Files:** `scene.rml` `middleFadeGroup`/`eventGroup`, `DuoMapping`, `DuoCanvasView`.  
**Task:**
- Parent both Wi-Fi and event art under one `centerSlot`.
- Event takes the slot, then restores occupant; no separate seat.
- Add Rive state/transition; Canvas clone.

### P2.3 Battery % two seats

**Requirement:** RING-02, RING-06  
**Files:** `scene.rml` percent group, `DuoMapping` seat flag, `DuoPrefs` constraints.  
**Task:**
- `percentSlot` at top/centre; when centre, Wi-Fi moves top.
- Functional number; force single-view (`splitIndicators=false`) or explain disabled split.
- Add state; Canvas clone.

### P2.4 Clock

**Requirement:** RING-03  
**Files:** `DuoStateMonitor.kt:295`, time filters, `scene.rml` `clock` text, Canvas, strings/enum.  
**Task:**
- System 12/24 via `DateFormat`; update on time/timezone/locale/config.
- Dedicated text object; no wrap; fit width; never touch ring.
- Test 12:34 / 23:59 / 12:34 PM.

### P2.5 Eight SIM dots + battery line

**Requirement:** RING-04  
**Files:** `scene.rml`, `DuoMapping` SIM levels, `DuoCanvasView`.  
**Task:**
- Author 8 dots (4/side) with **SIM mapping UNVERIFIED** until measured/owner-confirmed; bottom battery line.
- Number above, Wi-Fi centre; match image.
- Canvas clone; headless screenshot compare.

### P2.6 Split battery transition

**Requirement:** RING-05  
**Files:** `scene.rml`, state machine, `DuoMapping`, Canvas.  
**Task:**
- Translate the owner's static image into a storyboard: two battery halves move to the old dot positions, keep curvature; 8 SIM dots maintain mapping.
- Owner reviews frames before code.
- Implement forward/reverse; no jump; Canvas clone.
- **Blocked until storyboard approval.**

### P2.7 Camera/Compact

**Requirement:** CUS-16, RING-06  
**Files:** `DuoPrefs` placement, `DuoIconHost`, `scene.rml`, `DuoShell` camera page/sheet.  
**Task:**
- Main bar only; QS/shade/keyguard use Default.
- Fixed outer view; inner translation; Resize sheet; no live resize.
- Use measured cutout only; otherwise `unverified`.

### P2.8 Canvas parity

**Requirement:** RING-08  
**Files:** `DuoCanvasView.kt`, geometry table.  
**Task:**
- Port every new object and transition; side-by-side diff.
- Fix current known mismatches (ring scale, DND/bolt seat, event scales, unlock/call art, event motion).

### P2.9 Assets and gates

**Requirement:** RING-09, RING-10  
**Task:** `rive verify`, ease check, hash compare, idle renderer pause, performance.

**P2 exit:** owner approves split storyboard; headless frames; Canvas parity; hash/ease; no performance regression.

---

## 8. P3 — Download progress and PHDP

**Requirements:** DL-01…09  
**Files:** `DuoStateMonitor.kt:156-185`, `DownloadProgress.kt`, `DuoMapping.kt:194-200`, `DuoIconHost` tap path, Customization UI, strings, PHDP link.

Tasks:
- Add the Download Progress section: toggle, latest release link, PHDP status, Simulate button, explanation.
- State machine: download active → download number/bar, charging hidden; tap → charging/battery; tap again → download; completion → previous state; 100% → normal.
- Only the ring owns the tap override; split view must not silently lose it.
- PHDP detection; if absent show GIF + link; Simulate opens the PHDP app for its own test; no injection.
- Use versioned release URL/API; no fake latest APK.

**Weapons:** PHDP source/APK on emulator, Duo mapping unit tests, real download capture, `T-DL-*`.

**Exit:** download at 50% charging, tap, complete; no stuck; P0 still passes.

---

## 9. P4 — Burn-in

**Requirement:** BURN-01…03  
**Files:** new `BurnIn` preference/controller, `DuoIconHost` translation, diagnostics.

Tasks:
- Detect whether OxygenOS already shifts; if yes disable/note.
- 1 px left hour, hold, return, repeat; no render loop/wake lock; stop screen off.
- Measure 8 h battery and visual movement.
- Main bar only; document other surfaces.

**Weapons:** `AlarmManager`/WorkManager, batterystats, `T-BURN-*`.

---

## 10. P5 — All-UI, regression, release

**Requirements:** QA-01…05

Tasks:
- CPH2747 full pass; every other corpus UI including AOSP with evidence or `unverified`; static checks for unmeasured ROMs.
- Full unit/Compose/Rive/Canvas/gates.
- Reports/privacy/logs; no PII.
- Update `REPAIR-CATALOG`, `AGENT`, phase report, release notes.
- Build release candidate; owner installs; keep beta.45 rollback.

**Weapons:** `REA`/JADX for vendor APKs, `adb`, Gradle, `review-strict`, owner install log.

---

## 11. Work waves and parallelization

| Wave | Tasks | Can run in parallel? |
|---|---|---|
| W0 | P0.1, P0.2, P0.6 | P0.1 and P0.2 touch different functions but same files; serial by file ownership. Only one agent edits `DuoIconHost` at a time. |
| W1 | P0.3, P0.4, P0.5 | P0.3/P0.4 share host; serial. P0.5 can parallel (state/mapping) after P0.2. |
| W2 | P1.1/P1.2, P1.4, P1.6 | UI files overlap; divide by file ownership. P1.2 assets can parallel with P1.4. |
| W3 | P1.3/P1.5/P1.7, P1.8 | Serial for `DuoShell`; GIF asset can parallel. |
| W4 | P2.1/P2.2/P2.3 | Shared `scene.rml`; one Rive owner. |
| W5 | P2.4/P2.5/P2.6 | Clock is separate; dots/battery share scene; serial. |
| W6 | P2.7/P2.8 | Camera host and Canvas separate after pose freeze. |
| W7 | P3, P4 | Separate files; parallel. |
| W8 | P5 | Serial final gate. |

Rules: no two agents edit `DuoIconHost.kt`/`scene.rml` concurrently; every branch builds before handoff; P0 must be merged/rebased before UI.

---

## 12. Requirement traceability

| Requirement | Phase | Primary files | Test |
|---|---|---|---|
| STB-01 | P0.1 | `DuoIconHost.kt` | T-STB-01 |
| STB-02 | P0.2 | `DuoRiveView.kt`, `DuoIconHost.kt`, `DuoGuard` | T-STB-02 |
| STB-03 | P0.3 | `DuoIconHost.kt`, `DuoHook.kt` | T-STB-03 |
| STB-04 | P0.4 | `DuoHook.kt`, `DuoRiveView.kt` | T-STB-04 |
| STB-05 | P0.6 | `Diag.kt`, `REPAIR-CATALOG` | T-STB-05 |
| STB-06 | P0.5 | `DuoStateMonitor.kt`, `DuoMapping.kt` | T-STB-06 |
| STB-07 | P0.2 | `DuoGuard`, Canvas | T-STB-07 |
| STB-08 | P0.1/P0.4 | `DuoIconHost` | T-STB-08 |
| NAV-01/08 | P1.1 | `DuoShell.kt`, `DuoHomeScreen.kt` | T-NAV-01/08 |
| NAV-02 | P1.2 | icons/drawables | T-NAV-02 |
| NAV-03/04 | P1.3 | `DuoShell.kt` | T-NAV-03/04 |
| NAV-05/06 | P1.3 | `DuoShell.kt` | T-NAV-05/06 |
| NAV-07 | P1.8 | drawable/assets | T-NAV-07 |
| CUS-01 | P1.7 | Customization page | T-CUS-01 |
| CUS-02/03 | P1.5 | `DuoPresets.kt`, `DuoPrefs.kt` | T-CUS-02/03 |
| CUS-04 | P1.6/P1.7 | engine/settings UI | T-CUS-04 |
| CUS-05 | P1.6 | `DuoSettingsSections.kt`, drawable | T-CUS-05 |
| CUS-06 | P1.6 | settings/clock | T-CUS-06 |
| CUS-07 | P1.6 | settings/`DuoPrefs` | T-CUS-07 |
| CUS-08/09 | P1.6 | events/prefs | T-CUS-08/09 |
| CUS-10 | P1.6 | simulation/event rows | T-CUS-10 |
| CUS-11 | P1.1/P1.6 | settings/report | T-CUS-11 |
| CUS-12/13 | P1.4 | `GlassControls.kt`, `DuoSettingsComponents.kt` | T-CUS-12/13 |
| CUS-14 | P1.6 | `DuoIconHost`, `DuoMapping` | T-CUS-14 |
| CUS-15 | P1.7 | `DuoRivePreview` | T-CUS-15 |
| CUS-16 | P0.3/P2.7 | host/scene | T-CUS-16 |
| CUS-17 | P1.6 | settings search | T-CUS-17 |
| RING-01/07 | P2.2 | `scene.rml`, mapping | T-RING-01/07 |
| RING-02/06 | P2.3 | percent/constraints | T-RING-02/06 |
| RING-03 | P2.4 | clock/monitor | T-RING-03 |
| RING-04 | P2.5 | scene/Canvas | T-RING-04 |
| RING-05 | P2.6 | scene | T-RING-05 |
| RING-08 | P2.8 | Canvas | T-RING-08 |
| RING-09/10 | P2.9 | scene/assets | T-RING-09/10 |
| DL-01…09 | P3 | monitor/mapping/UI | T-DL-01…09 |
| BURN-01…03 | P4 | new controller | T-BURN-01…03 |
| QA-01…05 | P5 | all | T-QA-01…05 |

---

## 13. Risks and rollback

| Risk | Mitigation |
|---|---|
| P0 fix makes view invisible/mis-sized | ID-only change; no geometry change; screenshots; Canvas fallback |
| Rive reuse breaks animations | Reuse controller, not static; headless state tests; fallback |
| Camera mode still unstable | Main bar only; no live resize; kill switch; stage 1 |
| Preset migration loses custom | Versioned schema; export before migration; tests |
| Icon licensing | Redraw original; no stock asset; record source |
| Split transition guessed | Storyboard approval before coding; no Rive work until then |
| PHDP test unavailable | Open PHDP; GIF fallback; no injection |
| Burn-in battery | Detect OS shift; low-frequency schedule; measure |
| Other ROMs regress | P0–P4 only CPH2747 claimed; other UIs `unverified` |

Rollback: keep beta.45 APK + settings export; revert to `stage=1` Canvas if Rive fails; P0 branch is independently revertible.

---

## 14. Owner gates

1. Approve this plan/requirements/test plan.
2. Approve split-battery storyboard before RING-05.
3. Approve icon redraws/licence.
4. Approve camera “Resize” sheet visuals.
5. Install each beta and confirm no safe mode before P5 release.

---

## 15. Immediate next action

After approval: start **P0.1 (stable ID)** and **P0.2 (Rive reuse)** on the stability branch only. Do not start UI/Rive art until P0 exit.

This plan added no code. The worktree already contains the pre-1.6 beta.45 build input; preserve it per §2.0 before P0.
