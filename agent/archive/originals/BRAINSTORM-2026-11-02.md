# Brainstorm — Duo 1.6 Customization, Ring Decomposition, Downloads, Stability

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Brainstorm / discovery for the 2026-11-02 revision |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Open. Five owner answers are required before Requirements/Test Plan/final Plan.** |
| Inputs | Owner brief 2026-11-02; `agent/LIST-THE-PLAN.md`; current source; PHDP v2.11.0; existing device logs |
| Output after answers | Requirements additions → Test Plan additions → phased Plan with weapons → approval → code |

The owner explicitly forbids guessing. This brainstorm separates **evidence**, **decisions the owner must make**, **options with tradeoffs**, and **the draft phase shape**. It is not a commitment to any implementation.

---

## 1. Objective

Deliver a coherent 1.6-era update that:

1. Fixes the SystemUI FATAL/safe-mode risk first.
2. Rebuilds the app navigation/pill so the selector is transparent, the icon changes state, and the selector can be dragged from anywhere on the pill.
3. Reorganizes customization into a real Customization page with a live, on-change preview and persistent Default/Compact/Custom presets.
4. Decomposes the ring into independently positioned Rive objects: centre occupant, charge percentage, split battery + SIM pair, clock.
5. Corrects download-vs-charging behaviour and integrates PHDP honestly.
6. Replaces the simulation/data sections with generated GIF previews and removes unfinished surfaces.
7. Preserves parity across all vendor UIs and the Canvas fallback.

---

## 2. Evidence that constrains the design

### 2.1 App shell

- Current pages: Home, Graphics, Report, Settings (`DuoShell.kt:112`).
- Graphics is only the engine switch; Home has a continuously looping preview and Classic/Compact/Bold presets; Settings has all customization.
- The selector is a wine glass capsule with its own drag handler; the tabs are separately selectable.
- Preset choice is transient (`rememberSaveable`), not persisted.
- Live preview loops every 5.2s instead of responding only to changes.

### 2.2 Ring / Rive

- One 120×136 artboard; ring 103 diameter, stroke 8; no constraints/rig.
- Wi-Fi and live events are separate sibling groups at different seats.
- Charge % is a Text inside `ringGroup` at the top gap.
- Four cellular dots are fixed seats; `splitIndicators` is a **view split**, not a battery split.
- Camera ring is not implemented; `lockRing` only centres the element.
- Clock is `HH:mm`, shared with the network label, and too wide for its 56-unit box.
- Canvas is not a matching clone: ring scale, DND/bolt seats, event sizes, unlock/call art, and event motion differ.
- The `.riv` hash in docs is stale; the easing check currently reports 6 inert keyframes.

### 2.3 Download / PHDP

- Duo already receives PHDP’s private same-UID broadcast.
- PHDP v2.11.0 is the latest stable; no public test API; test uses the real publisher.
- Duo app cannot send the broadcast because the receiver is non-exported and the app has a different UID.
- PHDP’s default `showLabel=false`; the owner wants a number.
- Current charging precedence hides download digits.
- Only the ring has the download tap override; the split indicator view does not.
- No Download Progress UI exists.

### 2.4 Stability

- The owner’s current FATAL bundle is missing; the only owner crash capture is 2026-10-28.
- That capture shows QS/shade surface churn, a counted SystemUI crash 4.5s after collapse, and a separate LuckyTool/DexKit SIGABRT 15 minutes later.
- No rotation line in the capture. The current rotation and camera crashes are not yet classified.
- Live Rive resize and live reparent are still in HEAD; the `fix/landscape-safe-mode` guard is unmerged and untested.
- Any cosmetic change before classification would be reckless.

### 2.5 Images (now retrieved)

The six files are in `/storage/emulated/0/kilo images`, pulled to `/tmp/duo-kilo-images/`, and archived at `reverse/kilo-images/` (gitignored).

- They are **icon and selector references plus one current-preview crop**, not full app screens.
- Home reference: filled navy house icon.
- Customization reference: white three-row sliders icon.
- Report reference: two-document + trend-arrow outline icon (Icons8 stock asset; licensing must be checked before packaging).
- `new duo customisition icon.jpg` is the **New Duo Customization** target: number above, Wi-Fi centre, **eight SIM dots (four per side)**, and a colored battery line at the bottom. It is not the old rejected screen.
- Selector resting: 225×179 px inside a 207 px-high pill; held: 296×231 px, overflows; ratio ≈1.30×; active Home icon also grows ≈1.25–1.41×.
- Owner answer: final tabs are **Home / Customization / Report**; Settings opens from a gear on Home. The mockup's Home/Components/About labels are not final.
- The images do not define the camera cutout, the split-transition frames, full page layouts, or status-bar-scale placements.

---

## 3. Options and proposed direction

### 3.1 Navigation and selector

**Goal:** transparent selector, icon under it becomes active, long-press anywhere starts a drag, selector jumps to the finger, and the selector size matches the reference.

Options:

A. **Overlay selector with gesture ownership on the whole pill** (recommended)
- Keep the selector as a separate overlay, but remove its wine fill; use only a soft glass lens/outline.
- Move `pointerInput` from the selector box to the pill container. On long-press (or immediate drag after a threshold), capture the pointer, set selector centre to the finger, and follow.
- Tabs remain `selectable` for accessibility and tap navigation; drag is a separate gesture that suppresses the click.
- Active icon uses a tinted/duotone state; inactive icons use a muted foreground.
- At rest, selector is a transparent glass ring/capsule; when held, it expands to the reference size.
- Use `awaitEachGesture`/`detectDragGesturesAfterLongPress` and `change.consume()` to avoid page scroll conflicts.
- On drag end, snap to nearest station and select that page.

B. **Make the selector itself the only drag handle**
- Simpler, but contradicts “hold the pill anywhere”.

C. **Use a single custom nav bar that draws icons and selector together**
- More control, but a larger rewrite and risk of losing accessibility.

**Recommendation:** A. The existing selector can stay; its wine fill is removed, it becomes a large translucent ellipse around the active item, and its gesture is promoted to the parent. Measured targets: rest 225×179 px inside a 207 px pill; held 296×231 px; effective scale ≈1.30×. Translate to dp at run time, not hard-coded pixels.

**Resolved by images:** the selector is an ellipse/capsule around the active item, not a narrow capsule around the text; the active icon/label grow with it. The held frame is mid-motion, so the settled held size still needs owner confirmation.

### 3.2 App information architecture

The owner’s references show Home, Customization, Report. Current app has Home, Graphics, Report, Settings. Possible structures:

A. **Three top-level pages + Settings from a gear/overlay** (recommended if the references show three nav items)
- Home: status + live preview + preset selector.
- Customization: the full ring/page/engine/preview surface.
- Report: report form + contact developer.
- Settings: module health, diagnostics, backup, updates; reached from Home header or a gear, not the bottom pill.

B. **Four bottom items: Home, Customization, Report, Settings**
- Closest to current code; only rename Graphics→Customization and move sections.
- Keeps one-tap access to diagnostics/backup.

C. **Three items exactly; move backup/update/health into Report or Home**
- Cleanest visual, but diagnostics/backup are not “report” content and could be hard to find.

**Recommendation:** Decide from the Home/Customization/Report images. Do not delete Settings functionality before deciding; move it, do not throw it away. “About/Report in Settings is not needed” means the duplicate report block, not module health/backup/update.

### 3.3 Preset model

Current: three transforms, no persisted selection.

Proposed model:

```
DuoPresetState {
  selected: DEFAULT | COMPACT | CUSTOM
  lastCustom: DuoSettings
  default: DuoSettings       // fixed snapshot
  compact: DuoSettings       // fixed snapshot
}
```

Rules:

- Applying Default or Compact saves the *current* settings to `lastCustom` only if the user was in Custom and had unsaved custom edits? Safer: always keep the last explicit Custom snapshot, updated on any manual edit while Custom is selected.
- Entering Custom restores `lastCustom`.
- Manual edit in Default/Compact switches selection to Custom and writes the edited settings to `lastCustom`.
- Applying a preset must not destroy the other preset’s stored values.
- Old Classic/Compact/Bold migration: map old `classic`→Default, old `compact`→Compact? But the new Compact is a camera ring, not the old split layout. This is a breaking semantic change; require explicit owner decision. Old Bold has no successor.
- Persist `selectedPreset` in the settings store, not `rememberSaveable`.
- Backup must include the preset state and version it.

**Risk:** a camera-ring Compact cannot be represented by the current `DuoSettings` fields; a new `ringPlacement`/camera mode field is required, with an explicit single-surface rule.

### 3.4 Sliders and position editor

Current: tap/drag anywhere in the 48dp slider; position drags anywhere in the 40dp editor.

Proposed:

- Keep the visual track; attach tap/drag only to the 28–32dp thumb, with a 48dp hit target (invisible padding).
- Track taps do nothing except accessibility `setProgress`; vertical scroll over the track must not be consumed.
- Position editor: only the icon/knob starts the drag. Tapping the rail left/right can remain an optional accessibility action, but no drag from the rail.
- Test with a vertical swipe over each slider at several scroll positions; verify the settings page scrolls, not the value.

**Unknown:** Should tapping the track still jump the value for accessibility? The owner says “only the circle”; recommendation is no pointer-track control, but keep TalkBack `setProgress`.

### 3.5 Ring as independent objects

The current Rive artboard is flat. The requested object model is best implemented with **parent slot Nodes** and **rail rotations**, not duplicated art:

```text
duoGroup
├── percentSlot (Node at ring centre)
│   └── percent Text
├── ringGroup
│   ├── download wash
│   ├── batteryHalfL / batteryHalfR (separate trimmed arcs)
│   └── bolt
├── centerSlot (Node)
│   ├── eventGroup
│   └── middleFadeGroup (Wi-Fi, airplane, DND, network/clock)
└── simGroup
    ├── simRail1..4 (rotation angle)
    └── simDot1..4 at (0,-R)
```

Principles:

- `centerSlot` owns the transform; Wi-Fi and event art are children, so moving the slot moves both.
- `percentSlot` owns the number; it has two seats (above/centre) and can cross-fade/move as one object.
- Battery halves are separate arc shapes; a single trimmed ellipse cannot move independently.
- SIM dots use rail rotations; `x=R·sinθ`, `y=-R·cosθ`, or a child at `(0,-R)` with parent rotation θ. Exact, cheap, no unverified follow-path constraint.
- Keep live data in Kotlin; Rive owns art, opacity, trim, and short transitions.

**Hard constraints found:**

- The Wi-Fi glyph at authored size does not fit the top-gap percentage seat. It needs a scaled/alternate top-gap pose.
- `splitIndicators` puts the middle icons in a separate view. A Wi-Fi↔percentage swap inside the ring cannot work in split mode. Recommendation: force `splitIndicators=false` for the charge-centre and camera presets, or hide/disable the option with an explanation.
- Two battery halves (171.3°) do not fit the 117.4° bottom gap. The “battery moves to the old four-dot position” phrase needs the image and an explicit answer.
- Charging bolt currently owns the top gap. If charge % is centre and Wi-Fi is top, the bolt collides; a priority rule is required.

### 3.6 Camera ring / Compact

What is proven:

- No camera cutout geometry in the repo.
- `lockRing` centres the ring on the status bar; it does not detect a camera.
- No “Resize” action.
- Moving/resizing the live Rive view is the C2 risk.

Proposed safe shape:

- Camera mode only on the main status bar. Keyguard/QS/shade use the Default placement (owner explicitly asked for normal QS/shade placement).
- Use a **single Rive view** with a fixed outer size; move the artboard/ring inside it via bound translation. Do not resize/reparent the view live.
- The camera cutout must be measured per device/build; do not guess a centre/radius.
- “Resize” should be a tap affordance that opens a size control, not a live resize of the TextureView. The exact wording/timeout/placement is unknown.
- If the camera ring cannot be safe, it must be disabled with a logged reason rather than risk SystemUI.

### 3.7 Clock

- Use `DateFormat.getTimeFormat(context)` or `DateFormat.is24HourFormat(context)` to honour system 12/24h, locale, and AM/PM.
- Watch `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `ACTION_LOCALE_CHANGED`, and configuration changes.
- Add a dedicated clock Text object; do not reuse the network `5G/4G` node.
- Set `noWrap`, auto-width or fit-font, origin centre, and a max width that leaves ≥4 units to the inner ring.
- If AM/PM makes the string too wide, use a smaller font or a compact format; decide from the image.

### 3.8 Download / PHDP

Two separate problems:

1. **Charging precedence** (fixable in Duo): when a download is active, ignore the charging visual, show the download number/bar, and let a ring/icon tap return to charging. At completion (`active=false`, `progress=0`), return to normal; at 100% while plugged, return to normal charging or non-charging according to the final decision.
2. **Simulation** (blocked by PHDP API): PHDP has no public trigger and its test uses the real publisher. A Duo button cannot start PHDP’s test. Options:
   - A. Simulate only in the app preview; “Test in status bar” opens PHDP so the user presses its test. (honest, recommended)
   - B. Add a Duo-owned test injection to its own module via a guarded one-shot provider/broadcast, independent of PHDP. This expands attack surface and may violate “real simulation requires PHDP”.
   - C. Ask PHDP upstream for a `simulated` flag and a public test action; ship after it lands.

- Number visibility: honour PHDP `showLabel` (default false) or add a Duo “Show download percentage” switch. Recommendation: Duo switch default ON, with a note that it overrides PHDP’s label preference.
- Toggle-off warning: turning Duo’s download toggle off while PHDP Duo mode is on removes PHDP’s ring too; surface a warning and an “Open PHDP” action.
- Latest link: `https://github.com/hxreborn/punch-hole-download-progress/releases/latest`; direct APK is versioned, so resolve via the release page/API rather than a fake stable URL.

### 3.9 Stability / FATAL — evidence now identifies two bugs

The owner-authorized pull is in `reverse/fatal-logs/` (92 files). The Nov 2 incidents are **two independent Duo bugs**, not LuckyTool:

1. **Java crash on QS rotation** (`system_app_crash@1793641719781.txt`): `ConstraintSet.clone` throws because Duo adds an id-less `DuoRiveView` to the OnePlus `OplusQSRootViewComponent` `ConstraintLayout`. The log shows `id=-`, the target `ConstraintLayout[1272x2772]`, injection 468 ms before the FATAL. Fix direction: assign a stable ID before `addView`, or inject only into a host that does not require IDs; verify with QS rotation.
2. **Native JNI overflow** (`tombstone_03`): `File::import` leaks ~110 `Paint` global refs per import; 444 imports produced 48,771 Paint refs and hit the 51,200 limit. Duo re-created Rive views 8–12×/s during shade layout when `lock_ring=true`; the storm repeated on restarts. Fix direction: one Rive view/import per surface, reuse it; never re-import on layout; add import/global-ref counters and a hard guard.

LuckyTool `libdexkit.so` SIGABRT at 2026-10-31 02:34 is a separate boot crash (different PID/time). It must not be used to explain Nov 2.

Safety constraints:

- No live resize/reparent of a Rive TextureView across rotation.
- No per-layout reattach or `.riv` import.
- Camera mode must not require moving a live Rive view; QS/shade use the normal placement.
- Keep the breaker/kill switch, add both signatures to the repair catalog.
- Prefer one Rive surface for the camera layout and a stable ID for every injected child.

### 3.10 Burn-in

- First measure whether OxygenOS already shifts the status bar. If yes, do nothing and document it.
- If not, the safest implementation is a small periodic translation of the whole element inside its fixed view, not a view move/resize.
- 1 px every hour, hold, return after one hour; use a low-frequency scheduler, no frame loop, no wake lock. Measure battery over 8h idle.
- Needs an owner decision: exact cadence (1px left for one hour then back, repeat) and whether it applies to all surfaces or only the main bar.

### 3.11 All-UI parity

- The ring objects are inside the artboard, so they scale with the host view.
- Any mode that requires split views, camera cutout, or per-surface placement must be gated to the main bar and marked `unverified` elsewhere.
- The Canvas clone must be updated from a shared geometry table (ring radius, stroke, seats, event scale, clock box, SIM angles).
- Per-UI test cells remain `UNVERIFIED` until a dump/screenshot/log exists.

---

## 4. Draft phase shape (not final)

The final phases will be written only after the five answers. The likely sequence:

| Phase | Goal | Main weapons |
|---|---|---|
| P0 | Evidence + crash classification + safe rollback | REA adb, logcat, LSPosed logs, tombstones, dumpsys, `DuoGuard`, settings-only A/B |
| P1 | App shell + selector + slider interaction + preset state model | Compose, Robolectric/Compose tests, screenshots, adb |
| P2 | Settings IA cleanup, GIF previews, report move, removals | Compose, ImageDecoder/AnimatedImageDrawable, Rive screenshot/GIF generator, tests |
| P3 | Rive ring decomposition + clock + camera mode + Canvas parity | Rive CLI 1.8.0 (`verify`, `screenshot`, `inspect`, `schema`), `scene.rml`, headless pixel measurement, Kotlin geometry tests |
| P4 | Download/PHDP UX + precedence state machine + simulation decision | PHDP contract, Duo mapping tests, module logs, real download capture |
| P5 | Burn-in + all-UI verification + performance/battery | adb/dumpsys/gfxinfo/batterystats, per-UI matrix, REA/JADX |
| P6 | Full regression, docs, release candidate | Gradle, log/easing/hash gates, review-strict, owner approval |

No phase starts before the previous gate; the crash gate is first.

---

## 5. Draft test shape (not final)

- Unit: selector state machine, preset state, knob-only slider value mapping, download-over-charging arbitration, clock formatting, geometry seats, SIM angles, burn-in cadence.
- Robolectric/Compose: long-press drag, icon tint, page snap, scroll-vs-slider gesture, preview invalidation.
- Rive headless: default/compact/custom poses; percent-centre + Wi-Fi-top; split battery/SIM; camera ring; clock 12/24h; each event GIF source frame.
- Canvas parity: same geometry table, side-by-side screenshots.
- Device: fatal A/B matrix; all surfaces; rotation; camera; download real/PHDP test; 100% charge; 8h idle battery; per-UI evidence.
- Report: no PII; new crash signatures added to `agent/REPAIR-CATALOG.md`.

---

## 6. Five questions for the owner

These are the blockers that cannot be solved from the code or the current images.

### Q1 — Navigation structure and tab names

The images are now available, but they conflict: the selector mockup shows **Home / Components / About**, while the owner named **Home / Customization / Report**, and the current app is **Home / Graphics / Report / Settings**. The icon references are a filled house, a sliders icon, and a documents+trend icon.

**Question:** What is the final bottom navigation?
- A. Three tabs: Home / Customization / Report; Settings opens from a gear on Home; use the house/sliders/report icons. (recommended, matches the three references)
- B. Four tabs: Home / Customization / Report / Settings; use the house/sliders/report/gear icons.
- C. Three tabs: Home / Components / About (literal mockup labels); Report is a page action inside About.
- D. Other (describe).

Recommended: A if the three reference icons are the complete set; B if Settings must remain one tap away. Do not guess the tab count from the mockup alone.

### Q2 — FATAL evidence

There is no owner capture for the latest safe-mode incidents. The only tombstone in the repo is a separate LuckyTool/DexKit abort.

**Question:** Will you provide the LSPosed log + tombstone from the last safe-mode incident, or authorize one controlled reproduction per boot (streaming logcat + tombstone pull + settings A/B)?

Options:
- A. I will send the existing logs/tombstone. (recommended)
- B. Run the controlled capture; one action per boot.
- C. Try to fix without reproduction (not allowed by the project's evidence rule).

Recommended: A, then B only if A is incomplete.

### Q3 — Split battery / SIM geometry

The current ring has no split battery. The bottom gap is 117.4°, two 85.6° halves do not fit, and “four dots” could mean two per SIM. **The split-battery design image is not among the six supplied files; the owner said another image would be provided.**

**Question:** Please provide the split-battery image, then confirm which is correct:
- A. Both battery halves move to the old four-dot area; four dots = 2 per SIM, SIM1 right, SIM2 left, all on the same curvature.
- B. One battery half moves; the other stays; four dots total.
- C. The full battery (ring) moves; four dots split 2 right/2 left.
- D. Other (describe with the image).

Recommended: do not implement until the split-battery image exists. A only if the image shows both halves moving.

### Q4 — Camera ring, “Resize”, and surfaces

The camera ring is not implemented; “Resize” has no current action; live resizing is a known SystemUI crash risk.

**Question:** What should the camera/Compact mode do?
- A. Main status bar only; QS/shade/keyguard use Default; tap opens a size control labelled “Resize”; no live view resize. (recommended)
- B. All surfaces use the camera ring; tap shows a transient “Resize” label and opens a sheet.
- C. Camera ring is a static placement only; no resize control.
- D. Different (describe with the image).

Recommended: A, because it matches the owner’s “QS/shade normal” sentence and the C2 risk.

### Q5 — Contradictory removals

The brief contains conflicts that change the requirements.

**Question:** Which statements are correct? (select all that apply)
- A. Remove the **Charging animation section** only; keep “Charging %” as a Middle-of-ring option.
- B. Remove “Charging %” from Middle of ring too.
- C. Remove **Icon Colour Match**; add its white↔black GIF only as a preview, not a control.
- D. Keep Icon Colour Match and add the GIF to it.
- E. Remove the **Calls toggle** from the UI and keep the call event permanently off; keep the code/art for a future version.
- F. Remove Calls from UI **and code/art/tests**.
- G. Remove **Smooth Graphics** and always use Rive, while keeping a Canvas fallback selector in Customization.
- H. Remove Smooth Graphics and also remove the Rive/Canvas selector; only Rive.

Recommended: A, C, E, G. These preserve the owner’s intent while keeping the Canvas fallback available.

### 6.1 Owner answers — 2026-11-02

| Question | Answer | Planning consequence |
|---|---|---|
| Q1 navigation | **Three tabs: Home / Customization / Report** | Rename Graphics to Customization and use the house/sliders/report icons; Settings reached by a gear on Home. No fourth bottom tab. |
| Q2 fatal evidence | **Pull the last two days of LSPosed + LuckyTool logs from the phone** | A read-only forensic pull is running now. P0 remains evidence-first; no crash fix before timestamp/pid correlation. |
| Q3 split battery | The image is **`new duo customization`**: eight SIM dots, battery line at the bottom, Wi-Fi centre, number above. “You must see the image.” | Use the image as the static target. It shows 8 dots (4 per side), not 4 total. The split transition frames still need authoring/owner review. |
| Q4 camera | **Main status bar + size sheet; QS/shade/keyguard normal** | Camera placement is main-surface only; no live Rive view resize; “Resize” opens a sheet/control. |
| Q5 contradictions | **Remove Charging option too; keep Icon Colour + add white↔black GIF; Calls UI off but code kept; Rive always with Canvas kept; PHDP opens its test** | Requirements: remove Animations “Charging” and Middle “Charging %”; keep Icon Colour with GIF; Calls off/UI removed; keep Canvas fallback; PHDP test opens PHDP. |

**Correction:** the earlier mapping of `new duo customisition icon.jpg` as “current rejected screen” was wrong. The owner names it “new duo customization”; it is the **target** ring layout. The current rejected screen was not among the six files or was not needed.

---

## 7. After the answers

1. Update this file with the answers and decisions.
2. Write `agent/REQUIREMENTS.md` additions with stable new IDs (UI/CUS/RING/DL/STAB/BURN/QA).
3. Update `agent/TEST-PLAN.md` with traceable cases and gates.
4. Write the final phased plan with per-phase files, weapons, dependencies, rollback, and exit gates.
5. Review Requirements ↔ Test Plan ↔ Plan for 1:1 traceability.
6. Only then start P0 (crash evidence) and no cosmetic code before the crash gate.

---

## 8. Open decision log

| Decision | Status | Source |
|---|---|---|
| Selector transparent, icon active tint | Proposed | Owner |
| Drag from anywhere on pill after hold | Proposed | Owner |
| Resting/held selector dimensions | Measured; settled held size needs confirmation | Images |
| Home/Customization/Report/Settings structure | Blocked | Images + Q1 |
| Preset model with last-custom snapshot | Proposed | Owner |
| Slider knob-only drag | Proposed | Owner |
| Remove Smooth Graphics / clock font / icon colour | Contradictory | Q5 |
| Remove Mic/Location/Volume | Confirmed | Owner |
| Calls off, UI removed | Confirmed | Owner |
| Simulation GIFs replace section | Confirmed | Owner |
| Contact Developer moves to Report | Confirmed | Owner |
| Center = Wi-Fi + event object | Confirmed | Owner |
| Percent two seats | Confirmed | Owner |
| Split battery/SIM geometry | Blocked | Q3 + image |
| Camera ring/Resize | Blocked | Q4 + image |
| Clock system format, one line | Confirmed | Owner |
| Download over charging | Confirmed | Owner |
| PHDP simulation trigger | Blocked by PHDP API | Q2/PHDP |
| Fatal fix | Blocked by owner logs | Q2 |
| Burn-in toggle | Proposed | Owner + measurement |
| All-UI parity | Required | Owner/AGENT |
