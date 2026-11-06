# Duo Status Bar — Agile 1.8 Test Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.8 |
| Date | 2026-11-05 |
| Status | Draft; mirrors `AGILE-1.8-REQUIREMENTS.md` |
| Baseline | 1.7.0 stable (versionCode 89), 449 unit tests, CPH2747 |
| Branch | `feat/1.8-selector-live-preview` |
| Pass rule | PASS only with the named evidence and exact acceptance value; otherwise FAIL or `unverified` |

## 1. Gates

| Gate | Evidence |
|---|---|
| Requirements audit | Every requirement has one phase and one primary test here and in `AGILE-1.8-PLAN.md`; every test maps back to one requirement |
| Compile/unit | `:app:testDebugUnitTest :app:assembleDebug --offline`; the existing **449 tests** stay green and the new tests pass |
| Release build | `:app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline`; build artifact only, no publication |
| Route logs | `python3 tools/route-module-logs.py --check` |
| Rive ease | `python3 tools/check-rive-eases.py` covers both scenes, or the script is extended to do so |
| Rive hashes | fresh `rive rive/duo --once` output matches **both** `duo.riv` and `duo_old.riv` by SHA-256 |
| Property parity | both `.rml` property sets equal the `DuoBinder` write set; `PROPERTY_COUNT` matches |
| Device | Owner-run CPH2747 summary; all other ROMs `unverified` |
| Process | branch/commit hashes preserved; no `.kilo/` or evidence committed; no release/push/main merge |

## 2. Cases

Each case covers exactly one requirement. Sub-cases inside a case are evidence steps, not separate requirements.

### Glass and navigation

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-GLASS3-01 | GLASS3-01 | Read the selector implementation. Confirm Backdrop 1.0.1 is the only glass library, the highlight API is called with 0.75 dp width, 0.4 dp blur, 0.78/0.96 alpha, intensity 0.5, 45°, falloff 1.0 and Plus. Confirm the default 24 dp shadow is disabled. Grep for `RuntimeShader`/AGSL in the selector. Count the rim draw paths on the live path and on the fallback. Capture dark and light screenshots. | Token table matches exactly; no app-authored AGSL; no 24 dp shadow; one live rim owner; one manual rim only on the fallback. | `docs/evidence/agile-1.8/glass/highlight-shadow.txt` |
| T-GLASS3-02 | GLASS3-02 | Trigger press from 0 to 1 and inspect the reflection. Confirm a radial white brush with alpha `0.16 * press`, radius `1.5 * selector height`, 8 dp inset, press-only. Confirm the vertical press gradient is gone. Capture still/video. | Radial glow matches all values; no vertical press reflection; no glow when press = 0. | `docs/evidence/agile-1.8/glass/press-glow.txt` |
| T-NAV3-01 | NAV3-01 | Unit-test the pure hover index at slot boundaries and all four slots. Drag across Home → Custom → Report → Settings; capture hover colour. Release on each tab; capture selected icon size/colour and label position. Count tabs. | Hover index maps to the correct tab and clamps; hovered icon/label is `#3C88F8`; selected icon is pure white, about 24 dp, centred in the selector; label is below and one line; four tabs remain. | `docs/evidence/agile-1.8/nav/hover-selected.txt` |
| T-NAV3-02 | NAV3-02 | Inspect the icon layer and vector diff. Confirm no prototype vector paths changed. Confirm one icon source/map feeds all four tabs. Swap a test-only vector through the source and confirm the layout still uses the 24 dp slot. | No owner art redrawn; one source/map; test swap preserves layout and alignment. | `docs/evidence/agile-1.8/nav/icon-layer.txt` |

### Battery and invert

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-BATT3-01 | BATT3-01 | Drive the 2 SIM design at 0, 19, 49, 80 and 100. Measure the bottom arc in the real status-bar icon and in the remaining live preview. Repeat with out-of-range inputs. Render the Classic scene at the same levels. | Real and preview arcs are monotonic and clamped 0–100; 0 is empty and 100 is full; Classic matches the 1.7 baseline; no static full arc while the number changes. | `docs/evidence/agile-1.8/battery/levels.txt` |
| T-BATT3-02 | BATT3-02 | Compare the Kotlin level mapping, Rive battery-line property, Canvas fallback and preview at the same levels. Confirm one derived value feeds all surfaces. Confirm no duplicate battery geometry. | All surfaces agree at every level; one mapping source; Classic unchanged; no second battery line. | `docs/evidence/agile-1.8/battery/parity.txt` |
| T-INV3-01 | INV3-01 | Toggle Invert center in Classic and 2 SIM. Capture the real icon and the live preview. Confirm the percentage is centred, the normal middle occupant is above at the smaller size, and exactly one percentage is visible. Step through charging/saver states. | Both designs match the simulator behaviour; one number; centre number; upper smaller icon; no duplicate or stale number. | `docs/evidence/agile-1.8/invert/designs.txt` |

### Home, invert persistence, position

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-INV3-02 | INV3-02 | Toggle Invert center from Live Preview Controls. Read `middle_slot` from settings; confirm `percent`. Restart the app/SystemUI; confirm it persists. Seed an old percent-in-network value and confirm migration to Invert center. Inspect the provider schema and backup format for a new column/version. | Writes/reads `middle_slot=percent`; persists; old value migrates; no new column/schema version; toggle copy is clear. | `docs/evidence/agile-1.8/invert/persistence.txt` |
| T-HOME3-01 | HOME3-01 | Open Home. Confirm one Live Preview Controls card, no static **Live preview** card and no **Layout simulation** card. Confirm `ui/PreviewSimulator.kt` is absent and unreferenced. Search for dead card/string references. Exercise the remaining preview. | Exactly one preview card; static and layout cards gone; file gone and unreferenced; preview updates; no dead strings. | `docs/evidence/agile-1.8/home/cleanup.txt` |
| T-HOME3-02 | HOME3-02 | Inventory every control in Live Preview Controls. Confirm battery, charging, battery saver, Invert center, Wi-Fi/5G/4G/flashlight and other real-model controls are present. Confirm per-dot/custom-distribution controls are absent. Toggle each; confirm Invert persists and the rest are preview-only. | Inventory matches the real model; no per-dot/custom controls or strings; Invert persists; other controls are clearly preview-only. | `docs/evidence/agile-1.8/home/controls.txt` |
| T-POS3-01 | POS3-01 | Unit-test the right-anchored preview mapping: offset 0 → right edge, `-limit` → left edge, positive clamps to right edge. Repeat in centered mode: 0 → centre, ±limit → both edges. Verify native `place` 0 = right edge and native `placeLocked` 0 = centre. Toggle `lockRing` and confirm UI, prefs and native all switch mode together. Repeat in RTL. Drag 0 → `-limit` → 0 and release mid-range. | Normal preview/native agree (0 = right, negative = left, positive clamps); centered mode is explicit for `lockRing`; no UI/native mismatch; RTL keeps the physical rule; pointer stays attached; no disappearance. | `docs/evidence/agile-1.8/position/right-anchor.txt` |

### Demos and copy

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-DEMO3-01 | DEMO3-01 | Select Classic, then 2 SIM. Confirm the Classic card always plays the old scene and the 2 SIM card always plays the new scene. Confirm both are animated runtime Rive, not GIFs/static. Select each design and exercise the four event previews; confirm they use the selected design and the ring part in split mode. Confirm no deleted-GIF claim exists. | Fixed card demos are correct and animated; event previews follow the selected design; split mode uses the ring part; no GIF/deleted-asset claim. | `docs/evidence/agile-1.8/demos/design-events.txt` |
| T-DEMO3-02 | DEMO3-02 | Unit-test design normalization for valid, invalid and empty values. Confirm exactly one option is selected. Toggle module-disabled state and confirm the option is visibly disabled and cannot be selected. Inspect semantics for `selectableGroup`. Search Home and Settings for design selectors. | Exactly one selected; invalid normalizes; disabled state clear and blocked; `selectableGroup` present; only one design selector remains. | `docs/evidence/agile-1.8/demos/selector-state.txt` |
| T-COPY3-01 | COPY3-01 | Scan every user-facing string and hardcoded UI text. Check label/heading one-line fit at 360 dp and helper text no more than 1.5 short lines (at most two lines, second short). Grep for forbidden terms: System UI, LSPosed/scope, adb/stage, PHDP, JSON, QS/shade/keyguard, px, animation internals. Compare every format placeholder before/after. Confirm report/preview hardcoded text is in `strings.xml`. | Limits pass; forbidden-term count is zero in user strings; placeholders are byte-identical; no hardcoded report/preview text; full screen coverage. | `docs/evidence/agile-1.8/copy/audit.txt` |

### Process and gates

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-QA3-01 | QA3-01 | Read `AGILE-1.8-REQUIREMENTS.md` §4, `AGILE-1.8-PLAN.md` §3 and this file. For each requirement confirm one phase, one test and one evidence path. Reverse every test to exactly one requirement. | 1:1 both directions; no orphans; no duplicated ownership. | `docs/evidence/agile-1.8/gates/traceability.txt` |
| T-QA3-02 | QA3-02 | Aggregate gate: confirm every case in §2 has a verdict, including T-BATT3-01, T-INV3-01, T-POS3-01, T-NAV3-01, T-DEMO3-02, T-QA3-03 and T-COPY3-01. Run the existing 449 tests plus the new tests. Build debug and release. Run the route-log gate, Rive ease gate and both hash checks. Collect the owner-run CPH2747 summary. Mark other ROMs `unverified`. | Every case has a verdict; the named cases pass; 449 baseline tests stay green; release build produced but not published; route/ease/hash gates clean; owner summary present; other ROMs `unverified`. | `docs/evidence/agile-1.8/gates/suite.txt` (contains the CPH2747 summary link) |
| T-QA3-03 | QA3-03 | Parse the `ViewModelProperty` names from `scene.rml` and `scene_old.rml`; parse every property name `DuoBinder.write` uses. Compare set equality for both. Confirm `PROPERTY_COUNT`. Run `rive rive/duo --once`; hash both `duo.riv` and `duo_old.riv` against the checked-in files. Confirm the ease gate covers both scenes. | Both scene sets equal the binder set; count matches; both hashes equal; ease gate covers both. | `docs/evidence/agile-1.8/rive/parity.txt` |
| T-QA3-04 | QA3-04 | Record `git rev-parse` for `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`; confirm `745fc5f` and `eafdcb8` are present. Check `git status` for `.kilo/` and `docs/evidence/`. Check there is no release/tag/push/main merge. | Branch/commits preserved; no `.kilo/`/evidence staged; no release/tag/push/main merge/store. | `docs/evidence/agile-1.8/gates/process.txt` |

## 3. Required sub-checks

The following checks are mandatory inside the named case; they cannot be replaced by a code read alone.

| Check | Case |
|---|---|
| Battery 0/19/49/80/100 monotonic and clamped | T-BATT3-01 |
| Invert mapping in both designs, no duplicate number | T-INV3-01 |
| Position 0 = right in preview and native, including RTL | T-POS3-01 |
| Nav hover index pure logic | T-NAV3-01 |
| Design selection exactly one and normalization | T-DEMO3-02 |
| Rive property parity in both scenes | T-QA3-03 |
| Copy length and placeholder checks | T-COPY3-01 |
| Existing 449 unit tests | T-QA3-02 |
| Release build | T-QA3-02 |
| Route-log gate | T-QA3-02 |
| Rive ease and both-file hash gates | T-QA3-02, T-QA3-03 |

## 4. Evidence layout

- `docs/evidence/agile-1.8/glass/` — highlight/shadow, press glow.
- `docs/evidence/agile-1.8/nav/` — hover/selected, icon layer.
- `docs/evidence/agile-1.8/battery/` — level table, parity.
- `docs/evidence/agile-1.8/invert/` — both designs, persistence.
- `docs/evidence/agile-1.8/home/` — cleanup, controls.
- `docs/evidence/agile-1.8/position/` — right anchor, RTL, lockRing.
- `docs/evidence/agile-1.8/demos/` — design/event demos, selector state.
- `docs/evidence/agile-1.8/copy/` — audit.
- `docs/evidence/agile-1.8/rive/` — property parity, hashes.
- `docs/evidence/agile-1.8/gates/` — traceability, suite, process.
- `docs/evidence/agile-1.8/device/` — owner-run CPH2747 summary.

`docs/evidence/` is git-ignored. Never commit it. Never commit `.kilo/`.

## 5. Traceability

| Requirement | Phase | Primary test |
|---|---|---|
| GLASS3-01 | A1 | T-GLASS3-01 |
| GLASS3-02 | A1 | T-GLASS3-02 |
| NAV3-01 | A1 | T-NAV3-01 |
| NAV3-02 | A1 | T-NAV3-02 |
| BATT3-01 | A2 | T-BATT3-01 |
| BATT3-02 | A2 | T-BATT3-02 |
| INV3-01 | A2 | T-INV3-01 |
| INV3-02 | A3 | T-INV3-02 |
| HOME3-01 | A3 | T-HOME3-01 |
| HOME3-02 | A3 | T-HOME3-02 |
| POS3-01 | A3 | T-POS3-01 |
| DEMO3-01 | A4 | T-DEMO3-01 |
| DEMO3-02 | A4 | T-DEMO3-02 |
| COPY3-01 | A4 | T-COPY3-01 |
| QA3-01 | A0 | T-QA3-01 |
| QA3-02 | A5 | T-QA3-02 |
| QA3-03 | A5 | T-QA3-03 |
| QA3-04 | A0 | T-QA3-04 |

## 6. Pass/fail rules

- PASS only with the named evidence and the exact acceptance value.
- FAIL when evidence contradicts the requirement.
- `unverified` when the device, recording, build or measurement is missing; do not claim it.
- Device verification is owner-run and CPH2747-only truth. Other ROMs stay `unverified`.
- A visual change without a requirement ID is a regression, not a pass.

## 7. Exit

- All 18 requirements have a verdict with evidence.
- Existing 449 tests and the new tests pass; debug and release builds succeed.
- Both Rive scenes satisfy the property contract; both `.riv` hashes match.
- Owner accepts the CPH2747 walkthrough.
- No release, no main merge, no push, no LSPosed store; branch and review-fix commits preserved.
