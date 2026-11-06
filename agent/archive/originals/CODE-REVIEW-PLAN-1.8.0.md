# Duo Status Bar — Code Review Plan (1.8.0)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Post-1.8.0 implementation review |
| Date | 2026-11-05 |
| Owner | Abdulkarim |
| Requirements | `agent/AGILE-1.8-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.8-PLAN.md` |
| Tests | `agent/AGILE-1.8-TEST-PLAN.md` |
| Walkthrough | `agent/WALKTHROUGH-PLAN-1.8.0.md` |
| Branch | `feat/1.8-selector-live-preview` |
| Baseline | 1.7.0 stable (versionCode 89), 449 unit tests, CPH2747 |
| Preserved | `fix/1.7.0-review-fixes` at `eafdcb8`; commits `745fc5f`, `eafdcb8` |
| Rules | `AGENTS.md`, `agent/AGENT.md` |
| Status | Review plan only; no code change before findings are reviewed |

## 1. Purpose

Validate the 1.8.0 implementation against the approved requirements, plan and test plan. Find correctness, accessibility, performance and battery defects in the changed code, then fix only confirmed findings. Do not change behaviour that already satisfies a requirement. Do not reopen the 1.6 fatal fix, the 1.7 transition model, or the per-icon hiding surface.

The review is evidence-first. Every finding cites a current file and line, a requirement ID or a measured defect, and a proof. A hypothesis is not a finding.

## 2. Scope

In scope:

- App navigation and glass: `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt`, `ui/DuoNavIcons.kt`.
- Home, previews, demos and copy: `ui/DuoHomeScreen.kt`, `ui/DuoPreview.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsScreen.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsComponents.kt`, `ui/DuoReportScreen.kt`, `ui/LogReporter.kt`, `res/values/strings.xml`.
- Deleted simulator: `ui/PreviewSimulator.kt` absent and unreferenced.
- Position and persistence: `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt`.
- Battery/invert visual mapping: `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, `hook/DuoBinder.kt`, `hook/DuoCanvasView.kt`, `rive/duo/scene.rml`, `rive/duo/scene_old.rml`, `app/src/main/res/raw/duo.riv`, `duo_old.riv`.
- Tests and gates for the 18 requirements.

Out of scope:

- Style, naming, formatting and generic refactors.
- New features, new hook frameworks or new architecture.
- Re-testing vendor ROMs that remain `unverified`.
- A whole-app performance re-audit beyond the changed paths.
- The platform ART JIT fault already recorded in `AGILE-1.8-NOTES.md`.
- Release, tag, push, main merge or LSPosed store action.

## 3. Non-negotiable review rules

1. Evidence over guessing: every finding names the current file, line and proof.
2. One property, one owner: geometry in Kotlin, easing in Rive; one live rim owner; one battery-level source.
3. Fail silent: any failure leaves the stock status bar working.
4. Defaults must not change: Classic scene, untouched settings and untouched surfaces map to the 1.7 pixels.
5. No app-authored AGSL; Backdrop 1.0.1 is the only glass library; minSdk stays 33.
6. No unintended 24 dp Backdrop shadow; no manual double rim on the live path.
7. Both Rive scenes must declare every `DuoBinder` property.
8. No secrets, vendor assets or evidence committed; `.kilo/` never committed.
9. No behaviour change without a requirement ID or a measured defect.
10. Preserve `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`; no rebase/amend/delete.
11. No release, no main merge, no push, no LSPosed store.

## 4. Requirement review matrix

`code` names the primary owner files; `test` names the primary case. `status` is the verdict after evidence.

### 4.1 Glass and navigation

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| GLASS3-01 | Backdrop 1.0.1 highlight, no AGSL, no 24 dp shadow, one live rim owner | `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt` | T-GLASS3-01 | pending |
| GLASS3-02 | Radial white 0.16×press glow, 1.5×height, 8 dp inset | `ui/GlassControls.kt`, `ui/GlassSurface.kt` | T-GLASS3-02 | pending |
| NAV3-01 | Hover #3C88F8; selected white ~24 dp icon/label; four tabs | `ui/DuoShell.kt`, `ui/DuoNavIcons.kt` | T-NAV3-01 | pending |
| NAV3-02 | Replaceable icon layer, no owner-art redraw | `ui/DuoNavIcons.kt`, `ui/DuoShell.kt` | T-NAV3-02 | pending |

### 4.2 Battery and invert

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| BATT3-01 | 2 SIM arc follows 0–100 in real icon and preview; Classic frozen | `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, Rive scenes, `ui/DuoPreview.kt` | T-BATT3-01 | pending |
| BATT3-02 | One level mapping for Rive/Canvas/preview | `hook/DuoBinder.kt`, `hook/DuoMapping.kt`, `hook/DuoCanvasView.kt`, both scenes | T-BATT3-02 | pending |
| INV3-01 | Invert center in both designs; no duplicate number | `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, both scenes, `ui/DuoPreview.kt` | T-INV3-01 | pending |

### 4.3 Home, invert persistence, position

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| INV3-02 | `middle_slot=percent`; no new column; Invert center toggle; migration | `ui/DuoHomeScreen.kt`, `settings/DuoPrefs.kt`, docs | T-INV3-02 | pending |
| HOME3-01 | Remove static card + Layout simulation + `PreviewSimulator.kt`; one card | `ui/DuoHomeScreen.kt`, `ui/PreviewSimulator.kt` (deleted), `res/values/strings.xml` | T-HOME3-01 | pending |
| HOME3-02 | Move real-drivable controls; delete per-dot/custom controls | `ui/DuoHomeScreen.kt`, `ui/DuoPreview.kt`, `res/values/strings.xml` | T-HOME3-02 | pending |
| POS3-01 | 0 = right edge; negative left; centered `lockRing`; RTL; UI/native parity | `ui/DuoSettingsComponents.kt`, `ui/DuoSettingsSections.kt`, `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt` | T-POS3-01 | pending |

### 4.4 Demos and copy

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| DEMO3-01 | Fixed Classic/2 SIM demos; events follow design; no GIF claim | `ui/DuoHomeScreen.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsScreen.kt` | T-DEMO3-01 | pending |
| DEMO3-02 | Exactly one design selected; disabled; `selectableGroup`; no second selector | `ui/DuoHomeScreen.kt`, `ui/DuoSettingsScreen.kt` | T-DEMO3-02 | pending |
| COPY3-01 | Short English, no dev terms, placeholders, strings.xml | `res/values/strings.xml`, all UI files above | T-COPY3-01 | pending |

### 4.5 Process and quality

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| QA3-01 | Closed 1:1 traceability | this plan, requirements, test plan | T-QA3-01 | pending |
| QA3-02 | Full gate set, 449 baseline, release build, owner device truth | tests, `tools/`, `WALKTHROUGH-PLAN-1.8.0.md` | T-QA3-02 | pending |
| QA3-03 | Both scenes declare binder properties; rebuild/hash both `.riv` | both `.rml`, `DuoBinder.kt`, both `.riv`, `tools/check-rive-eases.py` | T-QA3-03 | pending |
| QA3-04 | Preserve branches/commits; no `.kilo/`/evidence; no release/merge/push/store | git state, process evidence | T-QA3-04 | pending |

## 5. Review tracks

Each track returns findings or `NO_FINDINGS`. A finding must include the shape in §7.

### C1 — Glass and selector correctness

1. Confirm Backdrop 1.0.1 is the only selector glass; no app-authored AGSL or `RuntimeShader`.
2. Confirm the measured highlight tokens and the disabled 24 dp default shadow.
3. Confirm exactly one live rim owner and one fallback manual rim; no double rim remains.
4. Confirm the radial press glow values and that it is press-only.
5. Confirm hover index pure logic, correct boundary/clamping, #3C88F8, selected white ~24 dp icon and small label below, four tabs.
6. Confirm the icon source is replaceable without editing prototype vectors.
7. Check accessibility: tab semantics, selected state, disabled state, label announcement, touch targets.
8. Check RTL and font-scale behaviour of the four-tab pill.

### C2 — Battery and invert mapping

1. Confirm one clamped level-derived value feeds the 2 SIM line in Rive, Canvas and preview.
2. Confirm the Classic scene is pixel-mapped to the 1.7 baseline and no classic property changed meaning.
3. Confirm invert center in both designs: centre number, upper smaller normal occupant, one number.
4. Confirm `middle_slot=percent` is the only persistence; no new column/schema.
5. Confirm both scenes declare every property the binder writes; `PROPERTY_COUNT` matches.
6. Confirm both `.riv` files are byte-identical to fresh builds; do not trust only the new scene.
7. Check that a missing property degrades to a warning and does not blank the element (existing binder contract).

### C3 — Home, preview and position

1. Confirm the static **Live preview** card and the **Layout simulation** card are gone; `PreviewSimulator.kt` absent and unreferenced.
2. Confirm one Live Preview Controls card and the required control inventory; per-dot/custom controls deleted.
3. Confirm Invert center is a persisted toggle, while battery/charging/saver/icon simulations are clearly preview-only.
4. Confirm right-anchored normal Position (`0 = right`, `-limit … 0`), explicit centered `lockRing` mode (`-limit … +limit`), and one predicate in UI/prefs/native.
5. Confirm RTL uses physical right/left and does not mirror the numeric field.
6. Confirm pointer capture, no disappearance, no reset-to-grab, and no out-of-range write.
7. Confirm no stale strings, dead composables or unreachable simulator code.

### C4 — Demos and copy

1. Confirm Classic card always plays the old scene; 2 SIM card always plays the new scene; both are animated runtime Rive.
2. Confirm event previews follow the selected design and use the ring part in split mode when supported.
3. Confirm no deleted-GIF claim and no literal GIF dependency.
4. Confirm exactly one design selected, normalization, disabled state, `selectableGroup`, and no second selector.
5. Confirm full copy pass: one-line labels, helper text no more than 1.5 short lines, forbidden terms absent, placeholders unchanged.
6. Confirm report/preview hardcoded text moved to `strings.xml`; dead strings removed.
7. Check all screens at 360 dp and with large font/accessibility scaling.

### C5 — Performance and battery (changed paths only)

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P18-1 | The selector keeps a live Backdrop/highlight loop when the app is idle | `GlassSurface.kt`, `GlassControls.kt`, `DuoShell.kt` | No frame callback while not pressed/dragging; idle sample |
| P18-2 | Hover recomposes the whole bar or writes state per frame | `DuoShell.kt` | Pure hover index; only icon/label state changes |
| P18-3 | Fixed design demos keep two Rive renderers alive off-screen | `DuoHomeScreen.kt`, `DuoSettingPreview.kt` | Lifecycle pause/idle stop |
| P18-4 | Event previews loop forever when disabled/backgrounded | `DuoSettingPreview.kt` | `playKey` stops; coroutine cancels |
| P18-5 | Invert/battery changes restart the whole Rive machine unnecessarily | `DuoMapping.kt`, `DuoBinder.kt` | Identical-snapshot skip; no extra surfaces |
| P18-6 | Position/`lockRing` writes trigger repeated layout passes | `DuoIconHost.kt`, `SlotGeometry.kt`, `DuoPrefs.kt` | One write per change; no per-frame re-anchor |
| P18-7 | Two battery-level owners cause duplicate work or disagreement | `DuoMapping.kt`, `RingGeometry.kt`, previews | One value path; mapping tests |
| P18-8 | Copy/structure changes add allocations or strings in a hot path | `DuoHomeScreen.kt`, `strings.xml` | No hot-path string formatting; no per-tick allocations |

A track with no solid issue returns `NO_FINDINGS`.

### C6 — Tests and deploy safety

1. Confirm all 18 requirements have a test and evidence path; reverse map has no orphan.
2. Confirm the existing 449 tests stay green and the new tests cover the required sub-checks.
3. Confirm both `.riv` hashes, property parity, route-log gate and ease gate.
4. Confirm no `.kilo/` or `docs/evidence/` staged; branch heads and preserved commits unchanged.
5. Confirm no release/tag/push/main merge/store action.

## 6. Sub-agent assignments

Sub-agents are read-only reviewers. They return findings in the shape below; the main agent verifies each line and applies only approved fixes.

| Agent | Track | Scope |
|---|---|---|
| A | C1 | glass, highlight/shadow, press glow, hover/selected, icon layer, accessibility |
| B | C2 | battery line, invert mapping, both scenes, binder parity, both `.riv` |
| C | C3 | Home cleanup, preview controls, position, `lockRing`, RTL |
| D | C4 | design demos, event previews, selector semantics, full copy |
| E | C5/C6 | performance of changed paths, tests, gates, process/branch safety |

Finding shape:

- `path`
- `line` (exact current line)
- `requirement` (ID or `PERF`/`BATTERY`)
- `confidence` (`high` only)
- `why` (1–2 sentences with evidence)
- `finding` (specific)
- `suggestion` (one safe fix direction, or `none` if investigation is needed)

## 7. Severity

- **CRITICAL**: fatal/SystemUI crash, data loss, security exposure, broken stock bar, or a requirement violated in a way that breaks the product.
- **WARNING**: measurable battery/perf regression, logic error, unhandled failure, accessibility blocker, drift risk.
- **SUGGESTION**: non-blocking improvement tied to a requirement or measured cost.

## 8. Fix policy

1. Fix only findings confirmed by the main agent against the current code.
2. A fix cites the requirement ID or measured defect; no opportunistic refactors.
3. Performance fixes must not change visible output; compare bound properties and pixels.
4. One logical change per commit-sized edit; no concurrent edits to the same file.
5. After fixes: run the full test plan and both Rive gates. If a device measurement is unavailable, record `unverified`.
6. No release, no main merge, no push, no LSPosed store. Preserve the review-fix branch and commits.

## 9. Verification commands

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline
./gradlew :app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline
python3 tools/route-module-logs.py --check
python3 tools/check-rive-eases.py
rive rive/duo --once
sha256sum app/src/main/res/raw/duo.riv app/src/main/res/raw/duo_old.riv
```

Device evidence follows `WALKTHROUGH-PLAN-1.8.0.md`; it is owner-run and CPH2747-only truth.

## 10. Exit criteria

- Every requirement row has a verdict: pass, fail, or `unverified` with the missing evidence.
- Every confirmed finding is fixed or explicitly deferred with a reason.
- No new fatal, no new permission, no new secret, no new architecture, no new settings column.
- 449 baseline tests plus new tests pass; debug and release builds succeed; route/ease/hash gates clean.
- Review report and owner walkthrough summary written under `agent/`.
- No release, no main merge, no push, no LSPosed store; branch and review-fix commits preserved.
