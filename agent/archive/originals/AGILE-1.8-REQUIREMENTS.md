# Duo Status Bar — Agile 1.8 Requirements

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Agile 1.8 (selector effects, live preview, 1.8.0 review baseline) |
| Date | 2026-11-05 |
| Owner | Abdulkarim |
| Status | **Requirements draft for owner review. No 1.8 product code before this file, the plan and the test plan are approved.** |
| Evidence | Owner brief 2026-11-05; session exploration 2026-11-05; ColorDuo 1.1.7 APK (`io.github.fengwenhua.colorduo`, versionCode 10107); Kyant Backdrop 1.0.1 API; current code at `feat/1.8-selector-live-preview` (`eafdcb8`); `agent/AGILE-1.7-COLORDUO.md`; 449 unit tests on 1.7.0 |
| Baseline | 1.7.0 stable (versionCode 89, 449 unit tests), CPH2747 measured device; `fix/1.7.0-review-fixes` at `eafdcb8` is isolated and preserved |

This phase is additive. It does not reopen the 1.6 fatal fix, does not change the 1.7 transition model, and does not add a new settings schema. Anything that conflicts with `AGENTS.md` stops and returns to the owner.

---

## 1. Owner intent

Finish the bottom navigation as a liquid-glass selector with the measured ColorDuo/Backdrop light behaviour, make hover and selection legible with four tabs, make the 2 SIM battery line truly live, and make Invert center a real, persisted status-bar behaviour. Then remove the duplicate static preview and the whole layout simulator, keep one Live Preview Controls card, correct the right-anchored Position model, add honest runtime design demos, and rewrite the user-facing copy into short, plain English.

The 1.8.0 review must be able to prove every statement with a requirement ID, a test ID, and an evidence path. Device truth stays with the owner on CPH2747.

---

## 2. Requirements

### 2.1 Bottom selector liquid glass (GLASS3)

| ID | Requirement | Acceptance |
|---|---|---|
| GLASS3-01 | Use Kyant Backdrop 1.0.1's supported highlight/refraction surface for the live bottom selector. No app-authored AGSL. The live path uses the measured directional Highlight: width **0.75 dp**, blur **0.4 dp**, alpha **0.78 dark / 0.96 light**, intensity **0.5**, angle **45°**, falloff **1.0**, blend **Plus**. Disable Backdrop's unintended default **24 dp shadow**. The live path has exactly **one rim owner** (the Backdrop highlight), replacing Duo's manual double rim; the manual rim remains only on the non-live fallback. minSdk stays 33. | Code and measurements match the token table; no app-authored `RuntimeShader`/AGSL; no 24 dp shadow; one live rim draw path; fallback draws one manual rim; dark/light screenshots; Backdrop dependency is still 1.0.1. |
| GLASS3-02 | Replace the current vertical press reflection with ColorDuo's radial **white 0.16 × press** glow: radius **1.5 × selector height**, **8 dp inset**, only while pressed. | Code uses a radial brush centred on the selector; alpha = `0.16 * press`; radius and inset match; the old vertical-gradient press reflection is absent; still/video evidence. |

### 2.2 Selector hover and icons (NAV3)

| ID | Requirement | Acceptance |
|---|---|---|
| NAV3-01 | While the selector is dragged over a tab, that tab's icon and label turn blue **#3C88F8**. On release, the selected tab becomes the large **pure-white** icon (about **24 dp**) centred in the selector, with a small label below. The tab count stays **four**: Home, Custom, Report, Settings. | Hover index is pure logic and tested; hovered tab is #3C88F8; selected icon is white, about 24 dp, centred in the selector; label is below and one line; four tabs unchanged; screenshots. |
| NAV3-02 | Keep the current prototype vectors. Do not redraw owner art. Route all four icons through one replaceable icon source so the owner's later icons drop in without layout changes. | No vector path edits in this phase; one icon source/map; the selected/hover layout consumes a 24 dp icon slot independent of the vector; a test asset swap proves the layer is replaceable. |

### 2.3 2 SIM / 8-dot battery line (BATT3)

| ID | Requirement | Acceptance |
|---|---|---|
| BATT3-01 | The small bottom battery arc in the 2 SIM design must follow real battery level 0–100 in the real status-bar icon and in the remaining live preview. It must not stay full at 100 while only the number changes. Classic scene behaviour is unchanged. | At 0/19/49/80/100 the arc is monotonic and clamped; real icon and live preview agree; Classic renders exactly its 1.7 baseline; screenshots/measurements. |
| BATT3-02 | Fix the mapping once for the new Rive scene and for the Kotlin/Canvas/preview path. Both surfaces read the same level-derived value; no second source of truth. | Unit mapping table passes; Canvas fallback and preview show the same arc as Rive at the same level; Classic path unchanged. |

### 2.4 Invert center (INV3)

| ID | Requirement | Acceptance |
|---|---|---|
| INV3-01 | Apply the simulator's existing invert behaviour to the real status-bar icon in **both** designs: the percentage moves to the centre, the normal middle icon moves above at a smaller size, and there is no duplicate number. | Both designs show one percentage, centred; the middle occupant is above at the smaller size; real icon and live preview match; no duplicate number in any frame; screenshots. |
| INV3-02 | Persist the behaviour using the existing `middle_slot=percent` setting. Do not add a new settings column or schema migration. Expose a clear **Invert center** toggle in Live Preview Controls. Document the decision and the migration from the old broken percent-in-network behaviour. | Toggle writes and reads `middle_slot=percent`; an existing percent-in-network value becomes Invert center; no new provider column/version bump; toggle label/detail are clear; the decision and migration are recorded in the plan and copy. |

### 2.5 Live preview cleanup (HOME3)

| ID | Requirement | Acceptance |
|---|---|---|
| HOME3-01 | Remove the separate static **Live preview** card from Home. Remove the entire **Layout simulation** card and delete `ui/PreviewSimulator.kt`. Keep exactly one **Live Preview Controls** card with the interactive preview. | Home contains one preview card; no static preview card; no Layout simulation card; `PreviewSimulator.kt` is absent and unreferenced; controls update the preview; dead strings removed. |
| HOME3-02 | Move only controls that can truly drive the real element: battery level, charging, battery saver, Invert center, Wi-Fi/5G/4G/flashlight, and any other control the real model supports. Do not move per-dot or custom-distribution controls; delete them with the simulator because the real model has no independent left/right dots. | Control inventory matches the real model; per-dot/custom distribution controls and strings are gone; Invert center persists; the other simulation controls are explicitly preview-only; no fake independent-dot state remains. |

### 2.6 Position (POS3)

| ID | Requirement | Acceptance |
|---|---|---|
| POS3-01 | In normal mode (not camera/locked), Position **0 dp means the right edge**, not the centre. The editor preview is right-anchored and negative values move left. Camera/Compact centered mode remains a separate explicit mode and may use 0 = centre. Do not use 0 = centre for the normal field. Fix the UI/native mismatch around `lockRing` and document RTL. | Normal range is `-limit … 0`; 0 renders at the right edge; negative moves left; preview and native agree; `lockRing` switches both UI and native to centered `-limit … +limit`; RTL keeps the same physical rule; tests and screenshots. |

### 2.7 Design demos and media (DEMO3)

| ID | Requirement | Acceptance |
|---|---|---|
| DEMO3-01 | No literal design-option GIF exists in git history (verified in this phase's exploration). Do not claim deleted assets. Implement animated **runtime Rive** demos: the Classic design card always demonstrates Classic, the 2 SIM card always demonstrates 2 SIM, and Live Events previews follow the selected design (and split mode when supported). | Classic card plays the old scene regardless of selection; 2 SIM card plays the new scene regardless of selection; event previews use the selected design's raw resource; split mode uses the ring part; no GIF-deletion claim; previews animate and pause when idle/off-screen. |
| DEMO3-02 | The ring design selector must have exactly one selected option, a clear disabled state, `selectableGroup` semantics, and no second always-enabled design selector after the simulator is deleted. | Normalized invalid value selects exactly one; disabled options are visibly disabled and cannot be selected; `selectableGroup` is present; only one design selector remains on Home/Settings; tests. |

### 2.8 Copy (COPY3)

| ID | Requirement | Acceptance |
|---|---|---|
| COPY3-01 | Rewrite all user-facing UI copy in short, simple English: at most one rendered line for labels/headings, and no more than **1.5 short lines** for helper text (at most two lines, with the second line short). Avoid developer terms: System UI, LSPosed/scope, adb/stage, PHDP, JSON, QS/shade/keyguard, px, animation internals. Keep the meaning and every format placeholder. Move hardcoded report/preview text into `strings.xml`. This is a full pass, not only Home. | Copy audit covers Home, Custom, Settings, Report, preview, dialogs/toasts, update/backup; labels fit one line at 360 dp; helper text is no more than 1.5 short lines; forbidden-term grep is zero in user strings; placeholders are byte-identical; no hardcoded report/preview text remains. |

### 2.9 Process and quality (QA3)

| ID | Requirement | Acceptance |
|---|---|---|
| QA3-01 | Requirements IDs, acceptance criteria, phase IDs, test IDs and evidence paths form a closed 1:1 chain: every requirement has one phase and one primary test with one evidence path, and the reverse map has no orphans. | Traceability audit passes both directions; no requirement without a phase/test/evidence; no orphan test. |
| QA3-02 | Verification must include: battery 0/19/49/80/100 monotonic/clamped; invert mapping in both designs with no duplicate number; position 0 = right in preview and native, including RTL; nav hover index as pure logic; design selection exactly one/normalization; Rive property parity in both scenes; copy length/placeholder checks; the existing **449 unit tests**; a release build; the route-log and Rive ease/hash gates. Device verification is owner-run, CPH2747-only truth; other ROMs remain `unverified`. | All non-device gates pass; device-only checks are pass or `unverified` with the missing evidence; the 449 existing tests stay green; release build is produced but not published; owner-run device summary exists; no other ROM is claimed. |
| QA3-03 | Both `rive/duo/scene.rml` and `rive/duo/scene_old.rml` must declare every property `DuoBinder` writes. Rebuild both `app/src/main/res/raw/duo.riv` and `duo_old.riv` and hash-compare them, because CI checks only the new scene. | Property-name set equality for both scenes against the binder; `PROPERTY_COUNT` matches; both fresh `.riv` hashes equal the checked-in files; the ease gate covers both scenes. |
| QA3-04 | Preserve the isolated `fix/1.7.0-review-fixes` commits (`745fc5f`, `eafdcb8`) and the current `feat/1.8-selector-live-preview` branch. Never commit `.kilo/` or evidence. No release, no merge to `main`, no push, no LSPosed store. | Branch heads and required commit hashes unchanged; no `.kilo/` or `docs/evidence/` staged; no tag/release/push; no main merge; owner approval before any release action. |

---

## 3. Open decisions locked

These interpretations are final for 1.8 unless the owner explicitly reopens them. They exist because several phrases in the brief are ambiguous; locking them prevents incompatible implementations.

| # | Topic | Locked interpretation |
|---|---|---|
| 1 | "Middle icon" with four tabs | Four tabs have no single centre tab. "Middle icon" means the **selected icon centred inside the selector**, not a fifth/centre tab. The tab count stays four: Home, Custom, Report, Settings. |
| 2 | Hover and selected appearance | Hovered tab icon and label are **#3C88F8** during the drag. On release the selected tab becomes a **pure-white ~24 dp icon centred in the selector**, with a **small label below**; the selector remains the moving liquid capsule. |
| 3 | Glass library and effects | Use **Backdrop 1.0.1** only. Use the measured directional Highlight tokens (0.75 dp / 0.4 dp, 0.78 dark / 0.96 light, intensity 0.5, 45°, falloff 1.0, Plus). Do not author AGSL. Disable the unintended default **24 dp shadow**. minSdk stays **33**. |
| 4 | Rim ownership | The live path has **one rim owner** (Backdrop highlight); remove Duo's manual double rim from the live path. Keep **one manual rim** only for the non-live fallback so the selector still has an edge when the live path is unavailable. |
| 5 | Press reflection | Replace the vertical press gradient with ColorDuo's **radial white 0.16 × press** glow, radius **1.5 × selector height**, **8 dp inset**, press-only. |
| 6 | Battery line | The 2 SIM bottom arc follows **0–100** in the real icon and in the remaining live preview. Classic scene behaviour is frozen. One level-derived value feeds Rive and the Kotlin/Canvas/preview path. |
| 7 | Invert center persistence | Invert center is stored as **`middle_slot=percent`**. No new settings column and no schema version bump. The old percent-in-network meaning is **migrated** to Invert center, not preserved as a second mode. The old `networkText = level` render is removed; it was the duplicate. The existing Settings item for the same value is renamed **Invert center**; it is the same setting, not a second mode. |
| 8 | Invert center geometry | The percentage sits in the centre; the **normal middle occupant** (the one Auto would choose: airplane > DND > Wi-Fi > network) moves above at a smaller size. There is **no duplicate number**, and no second icon-choice setting is added. The Live Preview Controls may simulate Wi-Fi/5G/4G/flashlight, but only the invert state persists. |
| 9 | Position origin | Normal mode: **0 dp = physical right edge**, range `-limit … 0`; negative moves left. Camera/Compact centered mode is a separate mode using `-limit … +limit` with 0 = centre. RTL keeps the same physical rule: 0 is the right edge, negative is left; do not reinterpret the number as start/end. |
| 10 | `lockRing` ownership | `lockRing` is the centered/camera mode. The UI, preference clamp and native placement must all use the **same predicate** and the same range; no UI showing a normal field while native centres, or the reverse. |
| 11 | Design demos | There was **no literal design-option GIF** in git history; no deleted asset is claimed. Use **runtime Rive demos**. Classic and 2 SIM cards are **fixed demos**; Live Events previews follow the selected design and split mode when supported. |
| 12 | Design selector | Exactly **one** selected option at all times. Invalid values normalize to one option. Disabled options are visibly disabled and cannot be selected. Use `selectableGroup` semantics. After `PreviewSimulator.kt` is deleted there must be **no second always-enabled design selector**. |
| 13 | Copy | "1 line or 1.5 short lines" means: labels/headings fit one line at 360 dp; helper text is no more than 1.5 short lines (at most two lines, second line short); no ellipsis at the target width. Developer terms are forbidden in user-facing strings. Placeholders are preserved exactly. Report/preview hardcoded text moves to `strings.xml`. |
| 14 | Icons | Current vectors are prototypes. Do not redraw them and do not use icon replacement as a 1.8.0 acceptance item. Structure the icon layer for a later drop-in owner delivery. |
| 15 | Evidence and process | `docs/evidence/` is git-ignored and never committed. `.kilo/` is never committed. Preserve `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`. No release, no main merge, no push, no LSPosed store. |
| 16 | Device truth | The owner runs device verification. CPH2747 is the only measured truth for this phase. Every other ROM/device is `unverified` until it has its own evidence. |

---

## 4. Traceability contract

This table is the 1:1 chain. The plan uses the phase ID; the test plan uses the test ID; evidence lives under the named path. Every test covers exactly one requirement.

| Requirement | Phase | Primary test | Evidence path |
|---|---|---|---|
| GLASS3-01 | A1 | T-GLASS3-01 | `docs/evidence/agile-1.8/glass/highlight-shadow.txt` |
| GLASS3-02 | A1 | T-GLASS3-02 | `docs/evidence/agile-1.8/glass/press-glow.txt` |
| NAV3-01 | A1 | T-NAV3-01 | `docs/evidence/agile-1.8/nav/hover-selected.txt` |
| NAV3-02 | A1 | T-NAV3-02 | `docs/evidence/agile-1.8/nav/icon-layer.txt` |
| BATT3-01 | A2 | T-BATT3-01 | `docs/evidence/agile-1.8/battery/levels.txt` |
| BATT3-02 | A2 | T-BATT3-02 | `docs/evidence/agile-1.8/battery/parity.txt` |
| INV3-01 | A2 | T-INV3-01 | `docs/evidence/agile-1.8/invert/designs.txt` |
| INV3-02 | A3 | T-INV3-02 | `docs/evidence/agile-1.8/invert/persistence.txt` |
| HOME3-01 | A3 | T-HOME3-01 | `docs/evidence/agile-1.8/home/cleanup.txt` |
| HOME3-02 | A3 | T-HOME3-02 | `docs/evidence/agile-1.8/home/controls.txt` |
| POS3-01 | A3 | T-POS3-01 | `docs/evidence/agile-1.8/position/right-anchor.txt` |
| DEMO3-01 | A4 | T-DEMO3-01 | `docs/evidence/agile-1.8/demos/design-events.txt` |
| DEMO3-02 | A4 | T-DEMO3-02 | `docs/evidence/agile-1.8/demos/selector-state.txt` |
| COPY3-01 | A4 | T-COPY3-01 | `docs/evidence/agile-1.8/copy/audit.txt` |
| QA3-01 | A0 | T-QA3-01 | `docs/evidence/agile-1.8/gates/traceability.txt` |
| QA3-02 | A5 | T-QA3-02 | `docs/evidence/agile-1.8/gates/suite.txt` (contains the CPH2747 summary link) |
| QA3-03 | A5 | T-QA3-03 | `docs/evidence/agile-1.8/rive/parity.txt` |
| QA3-04 | A0 | T-QA3-04 | `docs/evidence/agile-1.8/gates/process.txt` |

---

## 5. Out of scope

- Changing the tab count (four tabs stay) or adding a centre tab.
- A new settings column, schema, or backup format version. The `middle_slot=percent` behaviour migration is allowed and required; it is not a schema change.
- Per-dot independent left/right masks or custom distribution in the real model; those controls are deleted with the simulator.
- SystemUI glass, `RenderEffect`, AGSL, or applying Backdrop inside the status bar. Backdrop stays app-side only.
- Vendor assets, copied ColorDuo assets, owner icon redraws, or new icon packs.
- Reopening the 1.6 fatal fix, the 1.7 transition fix, per-icon hiding, or other 1.7 surfaces.
- Unrelated refactors, new hook frameworks, new architecture, or a whole-app performance re-audit.
- Literal GIF recreation, deleted-asset claims, or marketing copy.
- Agent-run device testing, release, merge to `main`, push, tags, or LSPosed store publication.

---

## 6. Approval

No 1.8 product code starts before the owner approves these requirements, `AGILE-1.8-PLAN.md` and `AGILE-1.8-TEST-PLAN.md`. The 1.7.0 stable build remains the rollback baseline. Only documentation was produced in this step; no app code, Rive file, string resource, or git history was changed.
