# Duo Status Bar — Walkthrough Plan (1.8.0)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | 1.8.0 owner walkthrough |
| Date | 2026-11-05 |
| Branch | `feat/1.8-selector-live-preview` |
| Preserved | `fix/1.7.0-review-fixes` at `eafdcb8`; commits `745fc5f`, `eafdcb8` |
| Build under test | 1.8.0 candidate APK produced by the plan; not a release |
| Requirements | `agent/AGILE-1.8-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.8-PLAN.md` |
| Test plan | `agent/AGILE-1.8-TEST-PLAN.md` |
| Review plan | `agent/CODE-REVIEW-PLAN-1.8.0.md` |
| Measured device | OnePlus CPH2747 (OxygenOS 16, Android 16 / SDK 36) |
| Secondary evidence | Xiaomi 24129PN74G (HyperOS 3) if available; everything else `unverified` |
| Status | Ready for owner execution after requirements/plan/test approval; device steps are owner-run |

## 1. Purpose

Walk the 1.8.0 candidate through the approved requirements and the changed surfaces. A step passes only with the exact log, screenshot, recording or measurement named here. Anything without evidence is `unverified`, not passed. The walkthrough is owner-run; the agent does not take over the phone. CPH2747 is the only measured truth for this phase.

## 2. Preconditions

1. Device booted; `adb devices` shows it; `adb shell getprop ro.build.version.sdk` returns 36 on CPH2747.
2. LSPosed is active and the module is enabled for **System UI**; SystemUI is restarted after install.
3. Install the candidate build:

   ```bash
   adb install -r build/duo-1.8.0-debug.apk
   adb shell su -c 'killall com.android.systemui'   # or the ROM's restart path
   ```

4. Clear only the app-side log buffer, never the persistent crash history:

   ```bash
   adb logcat -c
   adb logcat -s DuoSB:* > /tmp/duo-1.8-walkthrough.log
   ```

5. Record the baseline battery and display state:

   ```bash
   adb shell dumpsys battery
   adb shell wm size
   adb shell wm density
   adb shell dumpsys batterystats --reset
   ```

6. Take a baseline screenshot and save the module diagnostic dump.
7. Confirm the branch and preserved commits before testing:

   ```bash
   git rev-parse --short fix/1.7.0-review-fixes
   git rev-parse --short feat/1.8-selector-live-preview
   git log --oneline -3 fix/1.7.0-review-fixes
   ```

## 3. Static walkthrough (code, before touching the device)

Execute in this order; each step cites the file that owns the behaviour.

### S1 — Requirements and traceability

- Open `AGILE-1.8-REQUIREMENTS.md` §4, `AGILE-1.8-PLAN.md` §3 and `AGILE-1.8-TEST-PLAN.md` §5.
- Confirm every requirement has exactly one phase, one test and one evidence path.
- Confirm no orphan test, no duplicated phase ownership and no requirement without an acceptance value.
- Mark visual or device-measured rows `unverified` until the runtime section proves them.

### S2 — Selector glass and navigation

- `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt`: Backdrop 1.0.1 highlight tokens, disabled 24 dp shadow, one live rim owner, one fallback manual rim.
- `ui/GlassControls.kt`: radial white `0.16 * press` glow, `1.5 * height`, 8 dp inset; no vertical press gradient.
- `ui/DuoShell.kt`: hover index pure logic, #3C88F8 hover, selected white ~24 dp icon, label below, four tabs.
- `ui/DuoNavIcons.kt`: no prototype vector edits; one replaceable icon source.
- Confirm no app-authored AGSL or `RuntimeShader`.

### S3 — Battery and invert

- `hook/DuoMapping.kt`, `hook/RingGeometry.kt`: one clamped 0–100 level source for the 2 SIM bottom arc; Classic path unchanged.
- `hook/DuoCanvasView.kt`, `ui/DuoPreview.kt`: same level source in Canvas and preview.
- Both `.rml` scenes and `DuoBinder.kt`: every written property declared; `PROPERTY_COUNT` matches; invert visual has a measured seat, not a guessed value.
- `settings/DuoPrefs.kt`, `ui/DuoHomeScreen.kt`: Invert center maps to `middle_slot=percent`, no new column.
- Confirm both `duo.riv` and `duo_old.riv` are rebuilt from the current scenes.

### S4 — Home, preview and position

- `ui/DuoHomeScreen.kt`: one Live Preview Controls card; no static Live preview card; no Layout simulation card; no `PreviewSimulator` reference.
- `ui/PreviewSimulator.kt`: absent.
- `ui/DuoPreview.kt`: battery, charging, saver, Invert center, Wi-Fi/5G/4G/flashlight and other real-model controls; no per-dot/custom distribution.
- `ui/DuoSettingsComponents.kt`, `ui/DuoSettingsSections.kt`, `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt`: normal Position `0 = right edge`, `-limit … 0`; `lockRing` centered mode `-limit … +limit`; one predicate; RTL physical rule documented.

### S5 — Demos, selector and copy

- `ui/DuoHomeScreen.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsScreen.kt`: Classic card fixed to old scene; 2 SIM card fixed to new scene; event previews follow selected design and ring part in split mode.
- Design selector: exactly one selected, normalization, disabled state, `selectableGroup`, no second selector.
- `res/values/strings.xml` and the UI files: one-line labels, helper text no more than 1.5 short lines, no developer terms, placeholders preserved, report/preview hardcoded text moved.
- Confirm no literal design-option GIF exists in history and no deleted-GIF claim is present.

### S6 — Performance of changed paths

- Selector: no Backdrop/highlight loop when idle; only press/drag work.
- Hover: only icon/label state changes; no whole-bar recomposition per frame.
- Demos: fixed cards pause off-screen; event previews stop when disabled/backgrounded.
- Battery/invert: identical snapshots do not restart the Rive machine; no extra Rive surfaces.
- Position/`lockRing`: one write per change; no per-frame re-anchor.

### S7 — Process and gates

- Confirm the 449 baseline tests and new tests pass; debug and release builds produced.
- Run `tools/route-module-logs.py --check`, `tools/check-rive-eases.py`, fresh `rive rive/duo --once`, and both hash comparisons.
- Confirm no `.kilo/` or `docs/evidence/` staged; branch heads and preserved commits unchanged.
- Confirm no release, tag, push, main merge or LSPosed store action.

## 4. Runtime walkthrough

### R1 — Boot, attach and restore

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R1.1 | Restart SystemUI with the module enabled | `adb logcat -s DuoSB:*` | `Duo view ready`, no FATAL |
| R1.2 | Check the stock bar and Duo | screenshot | stock icons intact; Duo present |
| R1.3 | Disable the module and restart SystemUI | screenshot + log | stock bar restored exactly |
| R1.4 | Re-enable | log | attaches once; no duplicate views |

### R2 — Selector glass and navigation (GLASS3-01, GLASS3-02, NAV3-01, NAV3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R2.1 | Compare the selector with ColorDuo 1.1.7 in dark and light | side-by-side screenshots | measured highlight/blur/tint match; no 24 dp shadow |
| R2.2 | Inspect the live edge | screenshot/zoom | one rim owner; no double rim |
| R2.3 | Press and hold the selector | still/video | radial white 0.16×press glow; radius 1.5×height; 8 dp inset |
| R2.4 | Drag across Home → Custom → Report → Settings | video | hovered icon/label #3C88F8; smooth tracking; no lost pointer |
| R2.5 | Release on each tab | screenshots | selected icon white, ~24 dp, centred in selector; small label below; four tabs |
| R2.6 | Inspect the icon assets | APK/screenshot | prototype vectors unchanged; drop-in layer only |
| R2.7 | Rotate and large-font check | screenshots | four tabs fit; label readable; touch targets intact |

### R3 — 2 SIM battery line (BATT3-01, BATT3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R3.1 | Drive the 2 SIM design at 100, 80, 49, 19, 0 | screenshots + measured pixels | arc decreases monotonically; 100 full; 0 empty |
| R3.2 | Repeat out-of-range values | screenshots | clamped, no wrap or overshoot |
| R3.3 | Compare the real icon and the live preview at each level | side-by-side | same level, same arc |
| R3.4 | Render Classic at the same levels | screenshots | matches the 1.7 baseline; no battery-line change |
| R3.5 | Check Canvas fallback if the engine falls back | screenshot/log | same arc; no blank battery |

### R4 — Invert center (INV3-01, INV3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R4.1 | Enable Invert center in Classic | screenshot | percentage centred; middle icon above, smaller; one number |
| R4.2 | Enable Invert center in 2 SIM | screenshot | same rules; no duplicate number |
| R4.3 | Toggle charging/saver while inverted | screenshots | one number; state colours/bolt correct |
| R4.4 | Read `middle_slot`; restart app/SystemUI | settings dump + screenshot | value is `percent`; survives restart |
| R4.5 | Seed an old percent-in-network value | settings dump + screenshot | migrates to Invert center; no second mode |
| R4.6 | Inspect provider/backup schema | code/dump | no new column or version |
| R4.7 | Disable Invert center | screenshot | returns to the normal seat; no stale duplicate |

### R5 — Live preview cleanup (HOME3-01, HOME3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R5.1 | Open Home | screenshot | one Live Preview Controls card; no static Live preview card |
| R5.2 | Search Home | screenshot/code | no Layout simulation card; `PreviewSimulator.kt` absent |
| R5.3 | Exercise battery, charging, saver, invert, Wi-Fi/5G/4G/flashlight | recording | preview updates; controls match the real model |
| R5.4 | Search for per-dot/custom distribution | screenshot/code | controls and strings gone |
| R5.5 | Confirm Invert persists; other controls are preview-only | settings dump | only Invert writes a setting; no fake dot state |
| R5.6 | Idle 60 s on Home | log/CPU sample | previews pause when idle/off-screen; no frame loop |

### R6 — Position and `lockRing` (POS3-01)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R6.1 | Normal mode: set Position 0 | screenshot + measured x | element at the right edge, not centre |
| R6.2 | Move to `-limit` and back | recording | negative moves left; 0 returns to right; pointer attached |
| R6.3 | Try a positive value | UI/measurement | clamps to 0 (right edge); no off-screen element |
| R6.4 | Enable Camera/Compact/`lockRing` | screenshot + measured x | centered mode; 0 = centre; range ±limit |
| R6.5 | Toggle `lockRing` off/on | UI + native screenshot | UI, prefs and native switch together; no mismatch |
| R6.6 | Repeat R6.1–R6.4 in RTL | screenshots + measured x | 0 is still the physical right edge; negative is left |
| R6.7 | Release mid-range and re-grab | recording | no disappearance, no reset-to-grab |

### R7 — Design demos and copy (DEMO3-01, DEMO3-02, COPY3-01)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R7.1 | Select Classic | recording | Classic card plays old scene; 2 SIM card still plays new scene |
| R7.2 | Select 2 SIM | recording | 2 SIM card plays new scene; Classic card still plays old scene |
| R7.3 | Play four Live Events in each design | recording | previews follow selected design; split uses ring part |
| R7.4 | Inspect design selector | screenshot/semantics | exactly one selected; disabled state clear; `selectableGroup`; no second selector |
| R7.5 | Review every screen copy | screenshots | labels one line at 360 dp; helper text no more than 1.5 short lines; no developer terms |
| R7.6 | Check placeholders and report/preview text | code/string dump | placeholders unchanged; no hardcoded report/preview text |
| R7.7 | Confirm no GIF claim | docs/evidence | runtime Rive only; no deleted-asset claim |

### R8 — Gates, regression and process (QA3-01, QA3-02, QA3-03, QA3-04)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R8.1 | Run the 449 baseline tests plus 1.8 tests | test results | 0 failures/errors/skipped |
| R8.2 | Build debug and release | build logs/APK hashes | both succeed; release is not published |
| R8.3 | Route-log gate | command output | clean |
| R8.4 | Rive ease gate and both hash comparisons | command output | clean; both `.riv` match fresh builds |
| R8.5 | Property parity both scenes | test output | binder set equals both scene sets |
| R8.6 | 1.6 P0 regression: QS rotation, shade stress, camera/lock, 100% charging | logs/video | no fatal, no safe mode |
| R8.7 | Branch/process audit | git output | `745fc5f`/`eafdcb8` and branches preserved; no `.kilo/`/evidence staged; no release/tag/push/main merge |

## 5. Evidence layout

Store under `docs/evidence/agile-1.8/device/`:

- `cph2747.txt` — device build facts, owner summary, requirement verdicts.
- `screens/` — selector, nav, battery, invert, home, position, demos, copy.
- `recordings/` — hover/drag/press, position drag, invert transitions.
- `logs/` — `DuoSB` slices, diagnostic dumps, crash buffer.
- `gates/` — unit/release/route/ease/hash outputs and property parity.
- `summary.md` — pass/fail/unverified per requirement with evidence links.

`docs/evidence/` is git-ignored. Never commit it. Never commit `.kilo/`.

## 6. Pass/fail rules

- PASS only with the named evidence and exact acceptance value.
- FAIL when the evidence contradicts the requirement.
- `unverified` when the device, recording rate, build or measurement is missing.
- A visual change without a requirement ID is a regression.
- Other ROMs stay `unverified`; do not generalize CPH2747 results.
- A performance fix is PASS only when visible output is unchanged and the redundant work is provably removed.

## 7. Rollback

- Keep the installed 1.7.0 stable APK and its SHA-256 as the rollback baseline.
- If the candidate regresses a transition, fatal guard, position or stock restore, reinstall 1.7.0, restart SystemUI, and record the failing evidence before a second attempt.
- Never leave the device half-attached: disable the module and restore the stock bar.
- No release, no main merge, no push, no LSPosed store at any point.

## 8. Exit criteria

- Every one of the 18 requirements has a verdict with evidence.
- Existing 449 tests plus the new tests pass; debug and release builds succeed.
- Both Rive scenes satisfy the property contract; both `.riv` hashes match.
- CPH2747 owner summary is complete; other ROMs are `unverified`.
- No fatal, no safe mode, no new permission, no new secret, no new settings column.
- Branch heads and `745fc5f`/`eafdcb8` preserved; no `.kilo/`/evidence committed; no release/tag/push/main merge/store.
