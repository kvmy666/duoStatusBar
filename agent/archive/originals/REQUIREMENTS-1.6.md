# Duo Status Bar — Requirements 1.6

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Version | 1.6 requirements (additive to `agent/REQUIREMENTS.md` v2) |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Draft for owner review. No implementation until this file, `TEST-PLAN-1.6.md`, `PLAN-1.6.md`, and the brainstorm are consistent and approved.** |
| Evidence | Owner answers 2026-11-02; `reverse/kilo-images/`; `reverse/fatal-logs/`; current source; `agent/LIST-THE-PLAN.md`; `agent/BRAINSTORM-2026-11-02.md` |
| Precedence | These requirements are additive; where they conflict with v2, the more specific 1.6 requirement wins. Safety/non-negotiables in `AGENTS.md` still win. |

Legend: **P0** = release blocker; **P1** = required for 1.6; **P2** = polish. Every row has a test ID in `agent/TEST-PLAN-1.6.md` and a phase in `agent/PLAN-1.6.md`.

---

## 1. Non-negotiables

1. One unguarded view/API call must never be able to take SystemUI down; fail silent and keep the stock bar.
2. No fix without a reproduced signature or a deterministic test. No guessing.
3. No `.riv` import, Rive view creation, or reparent on a layout pass. One view per surface/lifetime; reuse it.
4. No injected child without a unique, stable ID when the host is a `ConstraintLayout`.
5. All surfaces must be tested or explicitly marked `unverified`; a CPH2747 pass cannot claim One UI/HyperOS/ColorOS/Vector/AOSP.
6. Every Rive object has a Canvas clone driven by the same geometry table.
7. Defaults preserve current pixels except where the owner explicitly approved a redesign.
8. No stock asset is packaged without a licence; reference images are redrawn as original vectors/Rive shapes.
9. Flows must store secrets only in the relay/app, never in the APK or report card.
10. The 1.6 release is blocked until P0 and all P1 acceptance tests pass.

**Owner decision recorded (Q5, 2026-11-02):** the **Charging %** middle option is removed; the separate **Battery %** middle option remains and is the two-seat percentage object in RING-02/RING-06/RING-07. If this inference is wrong, only the Battery % centre seat is dropped; the Charging % removal stands.

---

## 2. Stability and crash requirements (P0)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| STB-01 | Every view injected into a `ConstraintLayout` must have a unique non-NO_ID before `addView`, or the host must be a container that does not run `ConstraintSet.clone`. | QS open + rotate portrait→landscape 20×; no `All children of ConstraintLayout must have ids` fatal; SystemUI PID unchanged; log proves the injected child's ID. | T-STB-01 | P0 |
| STB-02 | A Rive view and its `.riv` import must be created once per **surface lifetime** (a bar + configuration pair) and reused. Never remove/re-add or reparent the same cached view on layout, shade, size, split, or preset changes; attach count is 1 per surface, not merely 1 import. A rotation may create one new surface after destroying the old one; that is not a per-layout reattach. | 60 s shade open/close + settings changes: attach count stays 1 per surface; `File::import` does not grow per layout; `Duo view ready` does not storm; 51,200 global-ref limit never approached. | T-STB-02 | P0 |
| STB-03 | Camera/lock-ring mode must not change view identity, reparent, or re-import on every layout. It must use one fixed surface with inner art translation. | Enable lock/camera mode; hold shade open 60 s; attach count stable; no JNI overflow; no SystemUI restart. | T-STB-03 | P0 |
| STB-04 | Rotation must be graceful: cancel/defer pending attaches, do not resize/reparent a live Rive view; after the new configuration is known, destroy the old surface and create at most one new surface. | 20 rotations QS closed/open; no fatal; no ANR; ring returns; covers portrait and landscape. | T-STB-04 | P0 |
| STB-05 | Both identified signatures (ConstraintSet.clone; JNI global ref table overflow) must be added to the repair catalog and the diagnostic report. Counters/flags must be persisted before the risky attach/import so the next boot can report which path preceded a SystemUI death. | Pre-crash markers survive a SystemUI restart; report contains `qs_constraint`, `rive_import`, `jni_refs`; forced-path test proves detection. | T-STB-05 | P0 |
| STB-06 | At 100% battery while plugged, charging visuals must stop and the ring must behave like a non-charging phone. Bolt/charging animation must not loop at 100%. | Force 100% + plugged; bolt hidden, no charging animation, number normal; unplug/replug still correct. | T-STB-06 | P0 |
| STB-07 | A Rive failure must fall back to Canvas, never crash or leave an empty bar; the breaker/kill switch stays. | Force bad `.riv`/no renderer; bar draws Canvas; log has reason; no SystemUI restart. | T-STB-07 | P0 |
| STB-08 | The crash fixes must not hide the bar on any surface. QS, shade, keyguard, and main bar remain visible or stock, never blank. | Four surfaces after fix; screenshots + dumps. | T-STB-08 | P0 |

---

## 3. Navigation, pill, and selector (P1)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| NAV-01 | Bottom navigation has exactly three tabs: **Home / Customization / Report**. Settings is a gear action on Home, not a fourth tab. | Three tabs at every width; gear opens Settings; no overflow/missing tab. | T-NAV-01 | P1 |
| NAV-02 | Use original vector/Rive redraws inspired by all six references: filled house (Home), sliders (Customization), documents + trend arrow (Report). Do not package the supplied JPG/PNG stock images or trace their path data; the report icon is Icons8 and every reference needs a licence review. | Icons match silhouette/proportions; no stock raster or copied path data in APK; licence note covers all six sources. | T-NAV-02 | P1 |
| NAV-03 | The selector is transparent (no wine fill); the active icon/label changes to the active accent (reference blue `#3C88F8`), inactive icons stay neutral. | Screenshot compare; active tint only under selector; selector has no wine wash. | T-NAV-03 | P1 |
| NAV-04 | Selector geometry: at rest 225×179 px in a 207 px pill (≈71.1×56.6 dp at 506 dpi); held 296×231 px (≈93.6×73.0 dp), ≈1.30× scale, overflows the pill; active icon grows ≈1.25–1.41×; inactive items do not grow. Use dp, not pixels. The held frame is mid-motion; the settled held size must be confirmed before it is frozen. | Measured screenshot at 1272×2772; ratios within ±5%; no clipping of labels. | T-NAV-04 | P1 |
| NAV-05 | Press-and-hold anywhere on the pill starts selector drag. The selector jumps to the finger and follows it; release snaps to the nearest tab. A normal tap still selects the tab. | Drag from each tab; selector jumps; page changes only on release; no accidental scroll; TalkBack still exposes tabs. | T-NAV-05 | P1 |
| NAV-06 | Page navigation, selector animation, and haptic/press feedback remain coherent during drag; dragging on Report/Home does not open a page until release. | Repeated drags, reversal, cancel; no page flash; selector returns. | T-NAV-06 | P1 |
| NAV-07 | Replace the copied Duo on/off GIF with an original: Duo icon appears, then disappears, on a black background, matching the existing GIF style/loop. | Asset is original; black background; correct loop; no Shutterstock frame; documented source/hash. | T-NAV-07 | P1 |
| NAV-08 | Home shows health/status, a non-interactive preview and Settings gear; it does not contain editing controls. Report is a full page; Customization is a full page with the live editing preview. | Page inventory matches; one shared preview state, two independent surfaces; no editing on Home; gear reachable. | T-NAV-01/08 | P1 |

---

## 4. Customization and settings (P1)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| CUS-01 | Customization contains all ring presets, toggles, sliders, shape choices, engine choice, and a persistent live preview. Tapping the Customization ring opens/focuses its controls. | Every old customization control has a new home; no dead end; preview stays visible while scrolling. | T-CUS-01 | P1 |
| CUS-02 | Presets are persistent snapshots: **Default** (Wi-Fi centre + auto 5G/4G + battery % above), **Compact** (camera ring + Resize sheet), **Custom** (last custom settings). Switching to Custom restores its last state. Editing while in Default/Compact switches to Custom and saves there. | Kill/relaunch app; selection and all fields survive. Default→Custom→Default cycle restores exact values. | T-CUS-02 | P1 |
| CUS-03 | Migrate old presets without losing pixels: `classic` → Default; old `compact`/`bold` → Custom (not the new camera Compact). Store a schema version. | Import old backup/prefs; old compact/bold render exactly as before; no camera surprise. | T-CUS-03 | P1 |
| CUS-04 | Remove the “Smooth Graphics” UI toggle. Rive is primary; Canvas remains an explicit fallback selector in Customization with a note that it is automatic when Rive fails. | No Smooth Graphics switch; Rive selected by default; Canvas selectable; note visible. | T-CUS-04 | P1 |
| CUS-05 | Keep the Icon Colour control (Auto/Black/White) and add a white↔black animated GIF preview. | GIF loops; control changes icon tint; preview matches. | T-CUS-05 | P1 |
| CUS-06 | Remove the System-Font Clock toggle. Clock uses the system font/format without a user switch. | No toggle; clock matches system font; no 24h hard-code. | T-CUS-06 | P1 |
| CUS-07 | Remove the Charging animation toggle (`chargingEnabled`) and `MIDDLE_SLOT_CHARGING` **only**. Do not remove the separate `MIDDLE_SLOT_PERCENT` Battery % option, which is the two-seat percentage object in RING-02. Keep actual charging bolt/state behaviour. | No Charging switch; no Charging % option; Battery % still present; charging still works; no dead pref. | T-CUS-07 | P1 |
| CUS-08 | Remove Microphone, Location, and Volume event toggles from UI and code (prefs, mapping, art references, tests). | No rows; no dead enum/art/tests; event mask migration safe. | T-CUS-08 | P1 |
| CUS-09 | Remove the Calls toggle from UI; keep Calls permanently off. Retain code/art for a future version, clearly documented as off. | No Calls row; event cannot fire; code path still off by default. | T-CUS-09 | P1 |
| CUS-10 | Remove the Simulation and Cell Simulation sections. Each remaining live event row (Alarm, Flashlight, Screenshot, Unlock) gets an animated GIF preview; remove Play buttons and level simulator. This is **four event GIFs**, separate from the Duo on/off and Icon Colour GIFs. | No simulation sections; 4 event rows show GIFs; no dead strings; GIF inventory matches. | T-CUS-10 | P1 |
| CUS-11 | Remove the duplicate About/Report block from Settings. Settings keeps module health, diagnostics/root/Shizuku, backup, update, donate, version. Contact Developer moves to Report. | Settings has no report form; Report has Contact; health/backup/update still reachable. | T-CUS-11 | P1 |
| CUS-12 | Sliders respond only on the knob/circle, not the track. Track taps do nothing; vertical swipes over a track scroll the page. Keep TalkBack `setProgress`. | Drag knob works; track swipe scrolls; value unchanged; accessibility action works. | T-CUS-12 | P1 |
| CUS-13 | Position editors move only from their icon/knob, not the rail. | Drag icon works; rail drag does nothing; page scroll unaffected. | T-CUS-13 | P1 |
| CUS-14 | Fix the Wi-Fi glyph toggle. It must change the ring on every supported surface without a UI restart. | Toggle off/on; glyph appears/disappears; dump/log; no restart needed. | T-CUS-14 | P1 |
| CUS-15 | The live preview is always present in Customization but does not animate continuously. It invalidates only when a toggle/slider/shape/preset changes, then returns to a stable frame. | Idle preview uses no render loop; change updates within 100 ms; no 5.2 s loop. | T-CUS-15 | P1 |
| CUS-16 | Camera/Compact uses the **main status bar only**. QS, shade, and keyguard keep the Default placement. Tapping the camera ring opens a **Resize** sheet; no live view resize/reparent. The P0 guard is main-only; the full camera art/Resize UI is P2. | Camera on main; normal on other surfaces; Resize sheet opens; no attach storm. | T-CUS-16 | P0/P2 |
| CUS-17 | Settings search covers the new/moved rows and does not point to deleted sections. | Search each removed/added row; no dead result. | T-CUS-17 | P1 |

---

## 5. Ring object model and geometry (P2)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| RING-01 | Centre is one object containing Wi-Fi and the live-event art. Moving the centre moves both; an active event uses the same transform and restores the centre occupant when it ends. | Event while Wi-Fi centre; both stay in the same seat; no jump; restore. | T-RING-01 | P2 |
| RING-02 | **Battery percentage** (`MIDDLE_SLOT_PERCENT`, not the removed Charging %) is a separate object with two seats: above (default) or centre. In centre, Wi-Fi/selected centre occupant takes the above seat. Both stay functional. | Toggle seat; number live; Wi-Fi/occupant moves; no overlap; split off; Charging % stays absent. | T-RING-02 | P2 |
| RING-03 | Clock is a dedicated text object, uses system 12/24h, locale/AM-PM, updates on time/time-zone/locale changes, stays on one line, and never touches the ring edges. | 12:34, 23:59, 12:34 PM all one line within safe width; system changes update. | T-RING-03 | P2 |
| RING-04 | Default ring layout matches `new duo customization`: number above, Wi-Fi centre, **eight SIM dots (four per side)**, battery line at the bottom. SIM1 right/SIM2 left is the current interpretation and must be confirmed from the image/owner before freeze; dots follow the ring curvature. | Headless render compare to reference; dot count/angle/right-left assignment; battery line correct. | T-RING-04 | P2 |
| RING-05 | Split battery: the battery line/arc separates into two halves; each half moves to the old four-dot position and keeps the same curvature; the eight SIM dots keep their right/left SIM mapping. The transition must be authored in Rive from the owner's design, not guessed. | Storyboard frames approved; no jump; reverse animation; Canvas clone. | T-RING-05 | P2 |
| RING-06 | When Battery % is centre, the layout uses a single in-ring view (no split-view icons) so the swap is physically possible; if split is selected, show a clear constraint/disabled state rather than a broken layout. | Toggle split + centre percent; UI explains; no overlap/missing icons. | T-RING-06 | P2 |
| RING-07 | Event priority: live event takes the centre/its object; after the event it restores the selected occupant. Charging does not occupy the middle (Charging % removed). | Alarm/flash/screenshot/unlock in each center mode; restore; no stuck icon. | T-RING-07 | P2 |
| RING-08 | Canvas is a clone from the same geometry table: ring, stroke, centre, percent, clock, eight dots, battery line, event scale/motion. | Side-by-side screenshots; pixel positions within tolerance; no missing Wi-Fi. | T-RING-08 | P2 |
| RING-09 | The `.riv` in the APK is byte-identical to a fresh build; no inert eases; layer order follows the documented rules. | Hash compare; `check-rive-eases.py`; `rive verify`; RML review. | T-RING-09 | P2 |
| RING-10 | Idle animation pauses when hidden/static; no continuous full-rate renderer. Preview/renderer wakeups only on real changes. | Battery/frame profile; no 130 mAh regression; preview idle 0 fps. | T-RING-10 | P2 |

---

## 6. Download progress and PHDP (P3)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| DL-01 | Add a Download Progress toggle with a direct latest-release link, a Simulate action, and a note that real PHDP status-bar simulation requires PHDP. | Toggle persists; link opens latest release; note visible; no PHDP duplication. | T-DL-01 | P3 |
| DL-02 | When a download starts, download progress overrides charging: show the download number and bar; hide the charging bolt/number. | Start download while charging at 50%; ring shows download only. | T-DL-02 | P3 |
| DL-03 | While download is active, tapping the ring switches to charging/battery. Tapping again may return to download. The download itself continues. | Two-way tap; state stays consistent; no lost progress. | T-DL-03 | P3 |
| DL-04 | At download completion, return to the previous state (charging if plugged/under 100, otherwise normal). At 100% charging, return to normal non-charging. | Complete at 100/99/unplugged; correct final ring. | T-DL-04 | P3 |
| DL-05 | PHDP detection is honest: show installed/absent; if absent, show the toggle GIF and a clear PHDP link; do not claim a live simulation. | PHDP installed/not installed; GIF vs open PHDP; no false status. | T-DL-05 | P3 |
| DL-06 | “Simulate” opens the PHDP app so the user can run its own test when PHDP is installed. If PHDP exposes no verified exported test component, Duo must not claim to start the test directly. Duo does not inject into PHDP’s non-exported receiver or add an unauthorized test broadcast. | Button opens PHDP; user runs the test; no broadcast injection; no security bypass. | T-DL-06 | P3 |
| DL-07 | Duo’s download percentage visibility is explicit. If PHDP `showLabel` is false, Duo may show its own number only with a documented switch; no hidden override. | PHDP label off/on + Duo switch; documented; test. | T-DL-07 | P3 |
| DL-08 | The download bar/number works on the main bar and every visible surface; only the ring has the tap override; split indicator view must not silently lose the download state. | Main/keyguard/QS/shade; split on/off; tap; no blank. | T-DL-08 | P3 |
| DL-09 | Use the real GitHub release endpoint; no fake stable APK URL. Show version and compatibility before download. | Link resolves; APK asset/version; no 404. | T-DL-09 | P3 |

---

## 7. Burn-in protection (P4)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| BURN-01 | Add a Burn-in protection toggle. If OxygenOS/SystemUI already shifts the element, the app must detect/document it and not double-shift. | System check/log; toggle disabled with note if already active. | T-BURN-01 | P4 |
| BURN-02 | When enabled, shift the whole Duo element 1 px left, hold for one hour, return, and repeat without a render loop or wake lock. | 2 cycles measured; 1 px; no frame loop; no visible jump. | T-BURN-02 | P4 |
| BURN-03 | Burn-in must not drain battery: no continuous timer, no full-rate renderer; use a low-frequency scheduler and pause when screen off. | 8 h idle battery delta within budget; no wakeups beyond schedule. | T-BURN-03 | P4 |

---

## 8. Every-UI, quality, and release (P5)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| QA-01 | Every change is verified on CPH2747 and marked `unverified` on every other corpus UI, including AOSP, until screenshots/logs exist. | Matrix status including AOSP; no false "supported" claims. | T-QA-01 | P5 |
| QA-02 | Public report cards are sanitized (no username/file); the private owner copy may contain the file/username. No PII in module logs. | Report test; grep; card screenshot; private vs public. | T-QA-02 | P5 |
| QA-03 | New crash signatures, settings, and phase evidence are documented. | `REPAIR-CATALOG`, `LIST-THE-PLAN`, test report updated. | T-QA-03 | P5 |
| QA-04 | Test/quality gates: unit, Compose, Rive headless, Canvas parity, `.riv` hash, ease check, route-log check, full app tests. Lint-vital exclusions must be explicit and justified, not silent. | All commands pass; no undocumented skipped gates. | T-QA-04 | P5 |
| QA-05 | Release candidate is built and installed on the owner's phone only after P0–P4 gates; rollback is documented. | Owner install log; rollback APK; no safe mode. | T-QA-05 | P5 |

---

## 9. Requirement → phase summary

| Phase | Requirements |
|---|---|
| P0 | STB-01…STB-08; main-bar camera guard (CUS-16 partial) |
| P1 | NAV-01…NAV-08; CUS-01…CUS-15, CUS-17 |
| P2 | RING-01…RING-10; CUS-16 full camera/Resize |
| P3 | DL-01…DL-09 |
| P4 | BURN-01…BURN-03 |
| P5 | QA-01…QA-05; all-UI verification and release |

Note: P2 and P3 are ordered for implementation convenience; download precedence can start after P1 because it touches the same rendering state machine, but no Rive art changes are allowed before P2's object-model gate.

---

## 10. Open items requiring owner/design confirmation

1. Split-battery **transition frames**: the static 8-dot image is clear; the motion (“battery moves to the old four-dot position”) is not. A short storyboard is required before RING-05 is coded.
2. Camera cutout geometry (center/radius per ROM) is unmeasured; start with the main CPH2747 and keep other ROMs `unverified`.
3. Whether `splitIndicators` should be force-disabled or merely warn when centre-percent/camera is selected.
4. Whether burn-in is already provided by OxygenOS; measure before implementing BURN-02.
5. Licensing: the report icon is Icons8 and the house/sliders references may also be stock; do not ship any reference raster or traced path data without a licence review.
6. Preview ownership: Home has a non-interactive preview; Customization has the live editing preview. Both share one `DuoSettings` state; only the Customization preview invalidates on changes.

---

## 11. Change log

- 2026-11-02: Initial 1.6 requirements from owner answers and crash evidence. This document did not change code; the worktree already contains the pre-1.6 beta.45 build input.
