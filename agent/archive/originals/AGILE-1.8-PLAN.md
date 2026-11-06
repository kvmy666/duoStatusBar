# Duo Status Bar — Agile 1.8 Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.8 |
| Date | 2026-11-05 |
| Requirements | `AGILE-1.8-REQUIREMENTS.md` |
| Tests | `AGILE-1.8-TEST-PLAN.md` |
| Review | `CODE-REVIEW-PLAN-1.8.0.md` |
| Walkthrough | `WALKTHROUGH-PLAN-1.8.0.md` |
| Baseline | 1.7.0 stable (versionCode 89), 449 unit tests, CPH2747 |
| Branch | `feat/1.8-selector-live-preview` at `eafdcb8` |
| Preserved branch | `fix/1.7.0-review-fixes` at `eafdcb8`; commits `745fc5f`, `eafdcb8` |
| Status | **Plan only. No 1.8 product code until requirements, plan and test plan are approved.** |

## 1. What changes

- The bottom selector adopts the measured Backdrop 1.0.1 highlight and radial press glow, with one rim owner on the live path and no unintended 24 dp shadow.
- Hover turns the tab blue; release makes the selected tab a large white icon with a small label, inside the existing four-tab pill.
- The 2 SIM battery line becomes live in the real icon and the remaining live preview; the Classic scene is frozen.
- Invert center becomes a real, persisted status-bar behaviour stored in the existing `middle_slot=percent` setting.
- The static preview card and the whole layout simulator are removed; one Live Preview Controls card remains.
- Position becomes right-anchored in normal mode; camera/compact centered mode stays explicit.
- Classic/2 SIM design cards become fixed runtime Rive demos; event previews follow the selected design.
- All user-facing copy is rewritten in short, plain English and moved to string resources where hardcoded.
- Both Rive scenes gain the same property contract; both `.riv` files are rebuilt and hash-checked.

This plan does not reopen 1.6/1.7 safety work and does not add architecture.

## 2. Evidence baseline

| Fact | Evidence |
|---|---|
| 1.7.0 stable is the rollback baseline | `agent/RELEASE-NOTES-1.7.0.md`; versionCode 89 |
| 449 unit tests pass on the baseline | `agent/CODE-REVIEW-REPORT-1.7.0.md` |
| ColorDuo 1.1.7 measured nav values | `agent/AGILE-1.7-COLORDUO.md` |
| Backdrop dependency is 1.0.1, minSdk 33 | `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| Current selector has a manual double rim and a vertical press gradient | `ui/GlassSurface.kt`, `ui/DuoShell.kt` |
| Current Home has a static preview card plus a Layout simulation card | `ui/DuoHomeScreen.kt`, `ui/PreviewSimulator.kt` |
| Current Position editor is centred in every mode | `ui/DuoSettingsComponents.kt` (`positionPreviewOffset`, `positionOffsetLimit`) |
| Current `lockRing` centres natively but the editor only exposes centered mode when `splitIndicators` is also on | `hook/DuoIconHost.kt`, `ui/DuoSettingsSections.kt`, `settings/DuoPrefs.kt` |
| Both scenes currently declare the same 55 view-model properties | `rive/duo/scene.rml`, `rive/duo/scene_old.rml` |
| No literal design-option GIF exists in git history | verified in session exploration 2026-11-05 |
| `.kilo/` is untracked; `docs/evidence/` is git-ignored | `git status`, `.gitignore` |

A0 records the exact Backdrop 1.0.1 highlight/shadow API symbols. No API name is guessed in this plan.

## 3. Phases

Every requirement has exactly one phase. Phase details name the primary test IDs from the test plan.

| Phase | Name | Requirements | Main deliverables | Primary tests |
|---|---|---|---|---|
| A0 | Evidence and contract freeze | QA3-01, QA3-04 | Backdrop API note, current-code inventory, Rive property contract, branch/process record, closed traceability table. No product code. | T-QA3-01, T-QA3-04 |
| A1 | Selector glass and navigation | GLASS3-01, GLASS3-02, NAV3-01, NAV3-02 | One-owner live highlight, no 24 dp shadow, radial press glow, hover index, selected white icon/label, replaceable icon layer, four tabs. | T-GLASS3-01, T-GLASS3-02, T-NAV3-01, T-NAV3-02 |
| A2 | Battery line and invert visual | BATT3-01, BATT3-02, INV3-01 | Live 2 SIM battery line in Rive, Canvas and preview; invert-center visual mapping in both designs; both scenes property-parity and rebuilt `.riv`. | T-BATT3-01, T-BATT3-02, T-INV3-01 |
| A3 | Home cleanup, invert toggle, position | INV3-02, HOME3-01, HOME3-02, POS3-01 | Delete static preview and `PreviewSimulator.kt`; one Live Preview Controls card; real-drivable controls only; Invert center persisted as `middle_slot=percent`; right-anchored Position and unified `lockRing` mode. | T-INV3-02, T-HOME3-01, T-HOME3-02, T-POS3-01 |
| A4 | Design demos and copy | DEMO3-01, DEMO3-02, COPY3-01 | Fixed Classic/2 SIM runtime demos, design-following event previews, selector semantics, full short-English copy pass, hardcoded report/preview text moved to `strings.xml`. | T-DEMO3-01, T-DEMO3-02, T-COPY3-01 |
| A5 | Gates and review handoff | QA3-02, QA3-03 | Full unit/release/route/ease/hash gates, both-scene property parity, device handoff, review and walkthrough evidence. | T-QA3-02, T-QA3-03 |

### A0 — Evidence and contract freeze (blocking)

1. Measure the Backdrop 1.0.1 highlight API: parameter names for width, blur, alpha, intensity, angle, falloff, blend mode, and shadow. Record the exact symbol that disables the default 24 dp shadow. Do not invent a call.
2. Record the current selector rim and press-reflection sites, hover/drag state, design selectors, `PreviewSimulator` control inventory, position mapping, `lockRing` predicate, and the old `middle_slot=percent` behaviour.
3. Parse `DuoBinder` property names and both scene property lists; freeze the current 55-name baseline. Any later addition must update both scene lists and `PROPERTY_COUNT`.
4. Confirm the no-GIF finding with the repository history and record it. Do not claim deleted assets.
5. Record branch heads `eafdcb8` for both `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`, and the preserved commits `745fc5f`, `eafdcb8`.
6. Write the traceability audit and evidence placeholders. No app code, Rive file, string resource or commit is changed in A0.

**Exit:** Backdrop API note, current-code inventory, property contract, process record, traceability audit. A0 evidence is under `docs/evidence/agile-1.8/`.

### A1 — Selector glass and navigation

1. Replace the manual live-path double rim with the Backdrop highlight as the single owner; keep one manual rim only in the non-live fallback.
2. Set the measured highlight tokens: 0.75 dp width, 0.4 dp blur, 0.78/0.96 alpha, intensity 0.5, 45°, falloff 1.0, Plus. Disable the 24 dp default shadow.
3. Replace the vertical press reflection with the radial white `0.16 * press` glow, radius `1.5 * height`, 8 dp inset.
4. Add a pure `hoverIndex` function for the drag position; use it to tint the hovered icon and label #3C88F8.
5. On release, render the selected icon about 24 dp, pure white, centred in the selector, with a small label below; keep four tabs.
6. Add one replaceable icon source/map. Do not edit the prototype vector paths.
7. Add `selectableGroup`/selected semantics where the nav is selectable; keep labels out of the blurred backdrop layer.

**Files:** `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt`, `ui/DuoNavIcons.kt`.

**Do not touch:** Rive scenes, `DuoMapping`, `DuoBinder`, app settings, user-facing copy. A1 may add an accessibility string key; A4 owns the final copy pass.

### A2 — Battery line and invert visual

1. Derive one clamped level value 0–100 for the 2 SIM bottom arc. Bind the new scene's `batteryLine` to a level-derived trim property; if a new property is required, declare it in **both** `scene.rml` and `scene_old.rml`, add it to `DuoBinder`, and update `PROPERTY_COUNT`.
2. Keep the Classic ring-fill path exactly as the 1.7 baseline.
3. Apply the invert-center visual mapping to both designs: percentage centre, normal middle occupant above and smaller, one number only. Use a measured geometry value; do not guess the centre seat.
4. If invert needs a new draw property or state-machine input, add it to both scenes and the binder; the property-parity test must stay green.
5. Rebuild `app/src/main/res/raw/duo.riv` and `duo_old.riv` from `rive/duo --once`; hash-compare both.
6. Keep the Canvas fallback and the in-app preview on the same mapping; no second source of truth.

**Files:** `rive/duo/scene.rml`, `rive/duo/scene_old.rml`, `app/src/main/res/raw/duo.riv`, `app/src/main/res/raw/duo_old.riv`, `hook/DuoBinder.kt`, `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, `hook/DuoCanvasView.kt`.

**Do not touch:** nav glass, position code, user-facing copy, or preview/home structure. A2 exposes the mapping; A3 integrates it into the preview.

### A3 — Home cleanup, invert toggle, position

1. Remove the static **Live preview** card and the **Layout simulation** card from Home.
2. Delete `ui/PreviewSimulator.kt` and every reference; delete per-dot/custom-distribution controls and strings with it.
3. Keep one **Live Preview Controls** card with the interactive preview. Move battery level, charging, battery saver, Invert center, Wi-Fi/5G/4G/flashlight and other real-model controls.
4. Invert center writes/reads `middle_slot=percent`; no new column/schema. Explain the migration from the old percent-in-network behaviour in the UI copy and docs.
5. Normal Position becomes right-anchored: `0 = right edge`, range `-limit … 0`, negative moves left. Camera/Compact/`lockRing` centered mode uses `-limit … +limit` and 0 = centre.
6. Use one predicate for UI, preference clamp and native placement. Fix the current mismatch where `lockRing` centres natively but the editor exposes centered mode only for `splitIndicators`.
7. Document RTL: the normal field is physical right/left, not start/end; 0 is the right edge in both LTR and RTL. Test the sign convention.
8. Preserve the pointer-capture behaviour: no lost drag, no disappearing element, no reset-to-grab.

**Files:** `ui/DuoHomeScreen.kt`, `ui/PreviewSimulator.kt` (delete), `ui/DuoPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsComponents.kt`, `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt`.

**Starts after A2** because the invert visual contract and the preview must match the final mapping.

### A4 — Design demos and copy

1. Classic design card always plays the Classic scene; 2 SIM card always plays the new scene. These are fixed demos, independent of the selected design.
2. Live Events previews follow the selected design; in split mode use the ring part when supported. Do not fake a second view.
3. Add `selectableGroup`, exactly-one-selected normalization, and a clear disabled state to the ring design selector. After `PreviewSimulator.kt` is deleted, there must be no second always-enabled design selector.
4. Rewrite all user-facing copy: labels/headings one line at 360 dp, helper text no more than 1.5 short lines (at most two lines, second short), no developer terms, placeholders preserved.
5. Move hardcoded report/preview text into `strings.xml`; remove dead simulator strings.

**Files:** `ui/DuoHomeScreen.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsScreen.kt`, `ui/DuoReportScreen.kt`, `ui/LogReporter.kt`, `res/values/strings.xml`, remaining UI files with hardcoded user text.

**Starts after A3** because it touches the same Home/preview/settings files. One writer only.

### A5 — Gates and review handoff

1. Run the full unit suite and the release build; keep the existing 449 tests green and add the 1.8 tests.
2. Run the route-log gate, Rive ease gate (both scenes if the script only covers the new scene), and both `.riv` hash comparisons.
3. Verify property parity for both scenes and `DuoBinder.PROPERTY_COUNT`.
4. Hand the device walkthrough to the owner; mark CPH2747-only truth and all other ROMs `unverified`.
5. Write the 1.8.0 review and walkthrough reports from evidence. No release, no main merge, no push, no store.

**Files:** tests, `tools/check-rive-eases.py` only if the old scene is not covered, review/walkthrough documents. Product code changes only for confirmed findings.

## 4. Parallel subagent waves and file serialization

Implementers must be code-capable agents (the main agent or `deepseek-v4.1-flash`, per `AGENT.md`); BEAM never edits code. Read-only verifiers may run in parallel. The main agent owns integration and the final diff.

| Wave | Runs in parallel | Files | Rule |
|---|---|---|---|
| 0 | A0 only | evidence/docs | Blocks all waves. No product code. |
| 1 | A1 selector/nav + A2 Rive/mapping | A1: `GlassSurface.kt`, `GlassControls.kt`, `DuoShell.kt`, `DuoNavIcons.kt`; A2: both `.rml`, both `.riv`, `DuoBinder.kt`, `DuoMapping.kt`, `RingGeometry.kt`, `DuoCanvasView.kt` | Disjoint file sets. No shared file. Both rebuild their own area. |
| 2 | A3 only | Home/preview/settings/native position files listed in A3 | Starts after A2 freezes the invert visual contract. One writer for `DuoHomeScreen.kt`, `DuoSettingsSections.kt`, `DuoPrefs.kt`, `DuoIconHost.kt`. |
| 3 | A4 only | design demos, event previews, strings/copy files | Starts after A3. Strings/copy is a single lane across all UI files. |
| 4 | Test authors + read-only verifiers | test files by area; no product files | Parallel only where test files differ. The main agent runs integration and fixes. |

Serialization rules:

- No two agents edit the same file in the same wave. If a file must change in two phases, the later phase starts after the earlier phase's handoff.
- `DuoHomeScreen.kt`: A3 cleanup first, A4 demos/copy second.
- `DuoSettingsSections.kt`: A3 position/invert UI first, A4 copy second.
- `DuoPrefs.kt`: A3 only (position clamp/invert read-back); A4 does not change storage.
- `DuoBinder.kt`: A2 only; A4 copy never changes property names.
- `strings.xml`: A4 only after all UI structure is frozen; no concurrent string edits.
- `DuoIconHost.kt`/`SlotGeometry.kt`: A3 only; no glass/nav or Rive changes in the same wave.
- Test files: one owner per existing test file; new test files may be created in parallel.
- The main agent merges by reading the final diff and running the gates; no rebase, amend, force-push or branch deletion.

## 5. Process guardrails

1. Work only on `feat/1.8-selector-live-preview`. Do not commit to `main`.
2. Preserve `fix/1.7.0-review-fixes` at `eafdcb8`, including `745fc5f` and `eafdcb8`. Do not rebase, amend, delete or rewrite them.
3. Never stage or commit `.kilo/` or `docs/evidence/`. Evidence stays on disk and git-ignored.
4. No release, tag, push, main merge, or LSPosed store action. A release build is a build artifact only.
5. No secrets, tokens, keystores, vendor APKs or owner assets in the repository.
6. Every code change cites a requirement ID. No opportunistic refactor.
7. If a fix cannot be reconciled with `AGENTS.md`, stop and ask the owner.

## 6. Open decisions locked

The canonical list is `AGILE-1.8-REQUIREMENTS.md` §3. The implementation must not reinterpret these:

- Four tabs; "middle icon" means the selected icon centred in the selector.
- Hover #3C88F8; selected pure white ~24 dp with a small label below.
- Backdrop 1.0.1; measured highlight tokens; no custom AGSL; no 24 dp shadow; one live rim owner.
- Radial white `0.16 * press` glow, `1.5 * height`, 8 dp inset.
- 2 SIM battery arc follows 0–100; Classic is frozen.
- Invert center is `middle_slot=percent`; no new column; old percent-in-network migrates to it; no duplicate number; normal middle occupant moves above.
- Normal Position `0 = right edge`, range `-limit … 0`; `lockRing` centered mode is separate; RTL uses physical left/right.
- No GIF exists; Classic/2 SIM cards are fixed runtime Rive demos; event previews follow the selected design.
- Exactly one design option selected; `selectableGroup`; disabled state clear; no second selector.
- Full short-English copy pass; placeholders preserved; report/preview text in `strings.xml`.
- Keep prototype icons; structure for drop-in owner delivery; do not redraw.
- CPH2747 is the only measured device truth; other ROMs `unverified`.

## 7. Risks

| Risk | Mitigation |
|---|---|
| Backdrop 1.0.1 API differs from the measured names | A0 reads the exact symbol; no guessed calls; screenshot proof |
| Default 24 dp shadow remains or a second rim is drawn | Code grep for shadow/rim; one live path; fallback-only manual rim |
| Press glow costs frames | Press-only, no idle loop; profile on CPH2747 |
| Hover updates recompose the whole bar | Pure `hoverIndex`; only icon/label state changes; no per-frame writes |
| Selected 24 dp icon plus label overflows a slot | Measure at 360 dp; one-line label; four-tab layout stays |
| Battery line property exists in only one scene | Property-parity test; rebuild both `.riv`; hash compare |
| Invert center geometry guessed | A0/A2 measured centre/upper seats; no-duplicate-number test |
| `middle_slot=percent` migration surprises old users | Read old value as Invert center; document; no new column |
| UI/native position disagreement around `lockRing` | One predicate and one range in UI, prefs and native; RTL test |
| Deleting the simulator breaks references/strings | Compile + grep; delete controls and strings together |
| Copy rewrite breaks placeholders | Placeholder parity test; forbidden-term scan |
| Evidence or `.kilo/` accidentally staged | Process guard; `git status` before every commit |
| Device test claimed without the phone | Owner-run walkthrough; everything else `unverified` |

## 8. Gates

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline
./gradlew :app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline
python3 tools/route-module-logs.py --check
python3 tools/check-rive-eases.py
rive rive/duo --once
sha256sum app/src/main/res/raw/duo.riv app/src/main/res/raw/duo_old.riv
```

The fresh-build hashes must equal the checked-in hashes for **both** files. A5 runs the full test plan; device steps are owner-run.

## 9. Approval and exit

No 1.8 product code starts before owner approval of requirements, plan and test plan. The phase exits only when every requirement row has a verdict, the 449 baseline tests and the new tests pass, both Rive scenes satisfy the property contract, and the owner accepts the CPH2747 walkthrough. No release, no main merge, no push, no LSPosed store.
