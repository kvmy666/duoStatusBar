# Duo Status Bar — Agile 1.7 Requirements

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Agile 1.7 (after 1.6.0 installed on CPH2747) |
| Date | 2026-11-03 |
| Owner | Abdulkarim |
| Status | **Draft for owner answers before implementation.** |
| Evidence | Owner notes 2026-11-03; 1.6 code + screenshots; `reverse/fatal-logs/`; ColorDuo 1.1.7 APK `/tmp/colorduo-pull/.../base.apk`; CPH2747 screenshot `/tmp/colorduo-screen/screen.png` |
| Baseline | 1.6.0 code 76, fatal paths fixed, 8-dot ring/battery line, liquid selector, 3 tabs |

This phase is additive. It does not reopen the 1.6 fatal fix. Any requirement that conflicts with 1.6 safety follows `AGENTS.md` and `REQUIREMENTS-1.6.md`.

---

## 1. Owner intent

Make Duo behave like the stock battery, not a separate overlay that disappears or jumps between heads-up, Quick Settings, and the shade. The visual design stays, but the user can choose the old single-SIM four-dot ring or the new dual-SIM eight-dot ring, and the new ring must be live, not a static 100% drawing.

The Home page becomes the design/customization switcher and live simulator. Settings returns to the bottom pill (short label "Custom"), the supplied icons are used, and the status-bar icons gain per-icon hide controls plus a master "all" toggle. The glass selector must match ColorDuo (same Kyant Backdrop library) in transparency, blur, edge refraction, and touch behaviour.

---

## 1.1 Clarifications received 2026-11-03

### Design states (authoritative)

| State | Definition |
|---|---|
| **Default** | Old design: battery is almost a full ring; the charge number cuts the ring; Wi-Fi sits inside; four cellular dots at the bottom (single-SIM style). |
| **2 SIM** | New design: eight dots (four per side), centre icon, battery line at the bottom, charge number above. |
| **Camera** | Existing toggle that moves the currently selected design onto the camera cutout and binds it to the camera-ring size; not an independent art style. |
| **Custom** | Constrained editor: switch old/new (one/two SIM), swap Wi-Fi/charge number, resize and move the centre icon up/down/left/right. Not arbitrary geometry. |

### Clarified behaviour

- Both Default and 2 SIM use **one shared size scale 0…100**. 0 maps to today's 50%; 100 maps to a new physical maximum of **220** (current 200 + 10%). The element stays visible at 0. Switching designs must not change size.
- Icon artwork is normalized between designs: a Wi-Fi icon drawn at 10 in the old art must render at the same optical size as the new art (currently ~8). One shared resize control for both.
- The overlay is rated 90% good; the remaining defect is the harsh HUD/QS/shade transition, not the overlay concept. Study inheritance/stock-battery host as an option, but keep the overlay if it is safer.
- Camera is the existing toggle that places the current design on the camera cutout; it is not a new art style. QS/shade/keyguard stay normal.
- Settings is a **fourth tab** inside the pill.
- ColorDuo must be matched in full: same library, same transparency/blur/refraction/selector size/touch values.
- Per-icon status-bar hiding includes the clock and battery. Hiding the stock battery must **not** hide Duo's own battery; the invisible anchor must survive. The “light icon” clarification was a typo: it meant the Duo battery itself.
- The per-icon list is built **dynamically** from the status-bar slots that actually exist on the device.
- Custom is a **blank, fully editable layout**: move the whole centre icon, detach Wi-Fi and pin it separately, move the charge number, resize, and rotate left/right. It starts without preset edits. The ready presets are editable templates; editing them behaves like editing a template.
- Camera is a fixed option/toggle: it puts the ring around the cutout, keeps Wi-Fi outside and visible, and allows size/rotation edits in that mode. Custom cannot move the physical camera; the Camera option owns the cutout placement.

---

## 2. Requirements

### 2.1 Battery behaviour and surface transitions (BAT)

| ID | Requirement | Acceptance |
|---|---|---|
| BAT-01 | Study how the stock battery view is hosted/lifecycled on CPH2747 and, where safe, make Duo inherit/attach to the same battery host instead of a separate overlay. If inheritance/override is unsafe, document why and reproduce the stock lifecycle with one cached view. | Evidence shows the chosen host, no new fatal, and the ring follows the battery's visibility. |
| BAT-02 | Heads-up ↔ Quick Settings ↔ shade transitions must be smooth: no snap, no size jump, no flash, no empty frame. Cover all six directions. | Screen recording at 60 fps; no jump >2 px, no blank frame, no size change beyond the measured animation. |
| BAT-03 | Duo must not disappear during a surface transition. If the stock battery is transitioning, Duo follows the same interpolation rather than hiding early. | 20 transitions each direction; Duo present on every frame where the stock battery is present. |
| BAT-04 | The battery-like implementation must remain fail-silent and keep the 1.6 crash guards (no id-less child, one Rive surface/import, no reparent storm). | P0 tests still pass; no `ConstraintSet.clone`/JNI/SIGABRT. |
| BAT-05 | Read and classify the current transition logs before changing the lifecycle; record the exact views, timestamps, and parent changes. | A short transition report with log excerpts and the measured jump source. |

### 2.2 Ring designs, battery, PHDP, and cells (RING2)

| ID | Requirement | Acceptance |
|---|---|---|
| RING2-01 | Restore the old four-dot single-SIM design and keep the new eight-dot dual-SIM design. Home lets the user choose **Default (old)** / **2 SIM (new)** / **Custom**, with a separate **Camera** toggle that applies to the current design. | Both designs render; switching is persistent; no deleted art. |
| RING2-02 | The new design's bottom battery bar is live: it must decrease/increase with the real battery level, not stay at 100. | 100/80/49/19 renders; bar length/trim follows level on device. |
| RING2-03 | PHDP download progress uses the selected ring design (old design → old progress style; 2SIM → new battery-line style). No stale old progress geometry. | PHDP/test download renders in both designs; no mixed geometry. |
| RING2-04 | In the new 8-dot design, the battery bar and dots form a clean circular ring; no flattened/straight bar or gaps that break the silhouette. | Headless/reference compare; ring curvature continuous; screenshots. |
| RING2-05 | Cellular dot mapping must match the stock icon on each device: 4 dots (single SIM) / 8 dots (dual SIM), with full/3/1-dot cases accurate. No “full signal → one dot”. | Read cell logs and level changes; matrix of stock level → dots; CPH2747 + at least one other ROM or static evidence. |
| RING2-06 | Camera is a toggle that moves the **currently selected design** into the camera cutout and binds it to the camera-ring size; QS/shade/keyguard stay normal. | Camera only on main bar; current design transferred; transitions tested. |
| RING2-07 | Custom is unrestricted: move any element, change Wi-Fi/charge-number placement, mix old/new choices, and set the eight dots individually. Home can simulate 4 full, 4 right + 2 left, or fully custom per dot. | Custom editor exposes per-element position/visibility; preview updates; no module write required for simulation. |
| RING2-08 | Battery-bar color follows the existing level/charging/saver mapping; 100% while plugged returns to normal (1.6 behavior preserved). | Level/charging/saver cases pass. |

### 2.3 Home, size, position, and preview (HOME2)

| ID | Requirement | Acceptance |
|---|---|---|
| HOME2-01 | Home is the design/customization surface: Default/2 SIM/Custom plus a Camera toggle, static/GIF preview, size/position controls, and live preview that updates on every change. Ready presets are editable templates; Custom starts blank. | All states/toggle; template edits persist; Custom blank; preview updates. |
| HOME2-02 | Both designs share one logical **0…100** scale: `physical = 50 + 1.7 × logical`; 0 → 50, 100 → 220. The element remains visible at 0. Icon art is normalized across designs so the same logical size looks identical. | Unit table + screenshots at 0/50/100; old/new optical comparison. |
| HOME2-03 | Position drag works through its full range; the element must not disappear after ~20 dp or require Reset to grab it again. The drag cursor must remain attached to the finger. | Drag from 0 to max and back, 10×; visible selection, no lost pointer, no reset. |
| HOME2-04 | Live preview supports: charging level slider, low-power mode, 8-dot simulation, and moving the centre icons (Wi-Fi/5G/4G/flashlight) to the top seat with a smaller scale while the number takes the centre. | Each control changes the preview; inverted layout matches the real mapping. |
| HOME2-05 | Live-event rows have animated previews (GIF/Rive) for Alarm, Flashlight, Screenshot, Unlock. | Four previews loop/play; no separate Simulation section. |
| HOME2-06 | Home shows the supplied design previews as still/GIF references; Default and 2SIM are visibly different. | Screenshots match assets/ring. |

### 2.4 Navigation, icons, and settings (NAV2/ICON2)

| ID | Requirement | Acceptance |
|---|---|---|
| NAV2-01 | Rename "Customization" to **"Custom"** (short label) in the pill and page title. | Visible label; no clipped text. |
| NAV2-02 | Put **Settings** as the **fourth tab** inside the pill. The page remains the health/backup/update/about surface, not duplicated customization. | Four tabs; one-tap Settings; no duplicate controls; back works. |
| ICON2-01 | Use the owner-supplied Home/sliders/report icons; do not redraw replacements. | APK contains the supplied assets; screenshots match. |
| ICON2-02 | Replace “hide all other icons” with per-icon controls. Add an **All** switch next to the “Status bar icons” header that enables/disables every per-icon switch. | Each switch hides only its icon; All toggles all; state persists. |
| ICON2-03 | Individual controls cover all discovered status-bar icons, including clock and battery, plus sound/general, silent, vibrate, Wi-Fi speed, VoWiFi, Wi-Fi/cellular replaced icons. Battery hiding must keep the invisible anchor and must not hide the “light” icon (owner term to confirm). | Each tested on CPH2747; missing vendors marked unverified; battery anchor survives. |
| ICON2-04 | Moving Wi-Fi outside the ring must keep the Wi-Fi glyph visible. Hidden-icon handling must not hide Duo's own Wi-Fi art. | Wi-Fi outside + hidden stock Wi-Fi; Duo glyph remains. |

### 2.5 ColorDuo glass and touch (GLASS2)

| ID | Requirement | Acceptance |
|---|---|---|
| GLASS2-01 | Match ColorDuo 1.1.7's navigation glass: transparency, blur radius, edge refraction, highlight/rim, shadow, and selector reflection. Use the same Kyant Backdrop API and measured values, not guesses. | Side-by-side screenshots; measured alpha/blur/rim; pixel diff justification. |
| GLASS2-02 | Match ColorDuo's selector touch model: press feedback, drag start, finger tracking, velocity/settle, crossing, and haptics. | Side-by-side recording; no label blur; drag from anywhere. |
| GLASS2-03 | Keep Duo's active-icon blue and the null wine fill. | Screenshot. |
| GLASS2-04 | Reverse ColorDuo with REA: package `io.github.fengwenhua.colorduo`, v1.1.7 (10107), Compose + libxposed, `NavigationDragState` exposes velocity/settling/travel. Record exact source/asset values in the phase notes. | Evidence file with class/method/strings and screenshots. |

### 2.6 Report, live preview, and event GIFs (UX2)

| ID | Requirement | Acceptance |
|---|---|---|
| UX2-01 | Report page top: reduce the blur overlap and move “Report a problem” down so it is not clipped/behind the glass. | Screenshot at 1272×2772. |
| UX2-02 | The Home live preview is interactive: charging slider, saver toggle, 8-dot simulation, centre inversion. | Each updates within 100 ms; no continuous loop when idle. |
| UX2-03 | Live-event GIFs/previews replace the old static rows and Simulation section. | Four event previews; no Play/level simulator. |

### 2.7 Quality and all-UI (QA2)

| ID | Requirement | Acceptance |
|---|---|---|
| QA2-01 | CPH2747 is the measured device; every other ROM/AOSP stays `unverified` until evidence exists. | Matrix. |
| QA2-02 | No fatal/safe mode in transition, camera, rotation, or per-icon toggles. | 1.6 P0 tests + new transition matrix. |
| QA2-03 | Test the requirements themselves before code: a requirement-to-test traceability table is approved. | This file ↔ test plan ↔ plan. |
| QA2-04 | No PII in logs; no stock/raster asset without owner permission; supplied assets are allowed only for this app. | Evidence. |

---

## 3. Open questions (blocking)

1. **Size 100% anchor:** with 0% = old 50%, what is 100%? Options: 100% = old 130% (normal, max 130), 100% = old 200% (full old range), or 100% = old 180%. Should sizes above 130 old be reachable?
2. **Settings placement:** fourth item in the pill labelled Settings, or a gear inside the existing three-item pill? The owner said “inside the pill”; the exact form is still open.
3. **Status-bar icon list:** confirm the definitive list. Is it all of: general/sound, silent, vibrate, Wi-Fi speed, VoWiFi, clock, battery, alarm, Bluetooth, DND, location, microphone, hotspot, rotation lock? Hiding the battery is special because Duo anchors to it.
4. **Custom editor scope:** “unrestricted” can mean (a) per-element position/visibility/scale with snap, or (b) a freeform canvas with arbitrary drag. Which one? Does Custom start from a copy of Default/2SIM, or from the last custom state?
5. **Camera ring design source:** when Camera is selected, does it always transfer the **current** Home design (Default/2SIM/Custom), or a fixed camera-specific layout? (The brief says current design; confirm.)

**Answered:** battery overlay is 90% good; study inheritance but prioritize the transition fix. Home states are Default/2SIM/Camera/Custom. Camera is current design in the camera ring. ColorDuo values must be matched exactly.

---

## 4. Out of scope for this phase

- Reopening the 1.6 fatal fix (must stay green).
- New onboarding, cloud accounts, or relay changes.
- Other vendors' live testing without a device; they remain `unverified`.
