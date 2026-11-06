# List the Plan — Pre-Plan Evidence and Change Inventory

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Pre-plan notes / change inventory ("list the plan") |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Draft. No code until brainstorming answers, Requirements, Test Plan, and final phased Plan are approved.** |
| Scope | OnePlus CPH2747 first; every change must be proven or marked `unverified` on the other 7 vendor UIs + AOSP |
| Sources | Owner brief 2026-11-02; `agent/AGENT.md`; `agent/REQUIREMENTS.md`; `agent/TEST-PLAN.md`; `agent/PLAN-v2.md`; `agent/UI-MATRIX.md`; current app/Rive/hook source; PHDP v2.11.0 public source; device logs already in the repo |
| Related files | `agent/BRAINSTORM-2026-11-02.md`, later `agent/REQUIREMENTS.md`, `agent/TEST-PLAN.md`, `agent/PLAN-v2.md` |

This file is **not** the final plan. It records what exists, what the owner asked for, what is proven, and what is still unknown. The final plan must be traceable to a requirement and a test.

---

## 1. Non-negotiable rules for this round

1. No guessing. Read the device/APK/log/source before naming a class, id, hook, resource, coordinate, or API.
2. No code before Brainstorm answers → Requirements → Test Plan → phased Plan are approved.
3. The FATAL/safe-mode fix is P0. No cosmetic work may hide or postpone it.
4. Every change must be evaluated on all supported surfaces: status bar, keyguard, shade header, QS header, app preview, landscape. A UI without evidence stays `unverified`.
5. Every Rive design needs a Canvas clone from the same geometry; the current Canvas is **not** yet a matching clone.
6. Defaults must preserve today’s pixels unless the owner explicitly changes them.
7. One property, one owner. Geometry in Kotlin; easing/art in Rive. Never animate a bound property in two places.
8. Fail silent: a bad new object/config leaves the stock bar working and logs the reason.
9. All repository files and file contents stay English.
10. The six reference images are on the owner’s phone in the folder `kilo images`. They are **not** local; local lookalikes must not be used.

---

## 2. Owner change inventory

This is the literal translated inventory, grouped for planning. It is not yet the requirements checklist.

### A. App shell, pill, selector, and motion

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| A1 | Current pill shape/icons are rejected; three references define Home, Customization, Report icons. | `DuoScreen` is Home / Graphics (`ENGINE`) / Report / Settings (`DuoShell.kt:112,132-137`). Current icons are Material `Home`, `Palette`, `Email`, `Settings`. References are a filled house, sliders, and documents+trend icon, not full page layouts. Mockup labels are Home/Components/About. | **PARTIAL; tab set unresolved** |
| A2 | Selector must be transparent; the icon under it changes colour instead of the selector turning red/blue. | Selector is a wine-tinted glass capsule (`DuoShell.kt:269-298`); tab tint also changes (`:187-194`). Reference: dark translucent ellipse, active Home blue, no wine. | Design change |
| A3 | Press-and-hold the pill, then drag the selector across pages; if the selector is on Home but the user holds Report, the selector jumps to the finger and follows it. | Drag is registered only on the selector itself (`DuoShell.kt:299-357`); tabs use `selectable` (`:200-206`). | Interaction change |
| A4 | Match pill/selector size to the two reference images: resting and held/enlarged. | Current selector: capsule height 44dp, width `itemWidth-32dp` min 72dp, stretch 26dp (`DuoShell.kt:145-149,234-237`). Reference: 225×179 px at rest in a 207 px pill; 296×231 px held; ≈1.30× scale; large ellipse around the active item. | Measured; settled held size unconfirmed |
| A5 | Replace the old Duo on/off GIF copied from Shutterstock with a new one: Duo icon appears, then disappears, on a black background, in the same style as previous GIFs. | Existing animated GIF path exists (`DuoSettingsComponents.kt:600-630`); only `guide_root_superuser.gif` is packaged. No Duo on/off GIF asset found. | New asset |
| A6 | Live preview must not animate continuously; it changes only when the user changes toggles/sliders/shapes. | Home preview loops every 5200 ms (`DuoHomeScreen.kt:202-220`); setting previews use phases. | Behaviour change |

### B. Settings / customization information architecture

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| B1 | Move all customization from Home/Settings into a Customization section; tapping the Customization ring opens its toggles/sliders/shape controls. | Home has live preview + presets; Settings holds sections (`DuoSettingsScreen.kt:534-593`). Target page count/name is not yet confirmed. | Needs IA decision |
| B2 | Move the engine selector into Customization; show Rive and Canvas; note Canvas is the automatic fallback when Rive fails. | Separate Graphics page with Auto/Rive/Canvas (`DuoShell.kt:362-416`). | Move + simplify |
| B3 | Remove from the UI only: Smooth Graphics (force ON always), System-Font Clock, Icon Colour Match. Separately add a white↔black GIF for icon colour matching. | Smooth Graphics is actually the Rive/Canvas switch (`DuoSettingsSections.kt:362-370`); System-Font Clock is `systemClockFont` (`:371-376`); Icon Colour is an Auto/Black/White picker (`:377-386`). The GIF instruction conflicts with removal. | **CONTRADICTION** |
| B4 | Remove the “Charging” item. | “Charging” appears both as an Animations switch (`DuoSettingsSections.kt:320-328`) and as a Middle-of-ring option “Charging %” (`:399`). Owner sentence is ambiguous. | **CONTRADICTION** |
| B5 | Fix the Wi-Fi glyph toggle; it does not work (may need a UI restart; untested). | `showWifiGlyph` exists (`DuoPrefs.kt:157`), read by host (`DuoIconHost.kt:1168-1169`), mapped to zero opacities (`DuoMapping.kt:182-184`), and exposed in the Icons section (`DuoSettingsSections.kt:554-559`). Needs reproduction. | Bug |
| B6 | Delete microphone, location, volume toggles from UI and code. | Bits and switches exist (`DuoPrefs.kt:295-297`; `DuoSettingsSections.kt:928-951`). | Remove |
| B7 | Calls: keep the event off and delete it from the UI. | Switch exists (`DuoSettingsSections.kt:960-967`); bit `EVENT_CALL` exists (`DuoPrefs.kt:299`). | UI remove; code retention TBD |
| B8 | Make simulations (flash, lock, screenshot, etc.) animated GIF previews like the others; delete the Simulation section and the Data/Cell-simulation section. | Simulation section is a live Rive control (`DuoSettingsSections.kt:972-1047`); Cell simulation is separate (`:1049-1089`). | Replace with GIFs |
| B9 | About/Report in Settings is no longer needed because Report is its own section. | Settings ends with AboutSection (`DuoSettingsScreen.kt:592-663`); Report page already exists (`DuoShell.kt:105`). | Move/remove carefully |
| B10 | Move Contact Developer to Report. | Contact button lives in About (`DuoSettingsSections.kt:733-743`). | Move |
| B11 | Sliders must respond only on the knob/circle, never the whole track; Position must move only from its icon/knob. | GlassSlider registers tap and drag on the whole 48dp box (`GlassControls.kt:228-240`); PositionEditor registers tap and drag across the full editor (`DuoSettingPreview.kt`/`DuoSettingsComponents.kt:269-291). | Interaction change |
| B12 | Presets: Default = Wi-Fi centre + auto 5G/4G + charge % above; Compact = camera ring with “Resize” on tap; Custom = last custom settings. Settings apply live, persist, and return when switching back to Custom. | Current presets are Classic/Compact/Bold (`DuoPresets.kt:15-65`); selected preset is transient UI state (`DuoHomeScreen.kt:66`); no Custom snapshot; “Compact” is not a camera ring. | Rework |
| B13 | When a preset is applied, change all its toggles; modifications are saved under Custom; returning to Default changes to Default, returning to Custom restores the last Custom state. | Preset application is a pure field transform, not a stored complete state (`DuoPresets.kt:4-13`). | New state model |

### C. Ring contents and object decomposition

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| C1 | Centre is one object: Wi-Fi and live-event icon; if Wi-Fi moves, the live icon moves with it. | Wi-Fi is in `middleFadeGroup`; live events are a sibling `eventGroup` at a different seat (`scene.rml:253,573-777`). | Rive refactor |
| C2 | Charge percentage is a separate object with two seats: original above, or centre. If it is centre, Wi-Fi takes its old above place; the number stays functional. | Percent is inside `ringGroup` at `y=-76` (`scene.rml:33-55`); Wi-Fi seat is `y=17`; no seat swap. | Rive + geometry |
| C3 | Split battery half-state: eight SIM dots (four per side), SIM1 right and SIM2 left, battery line at the bottom of the ring; Wi-Fi centre, number above. | Owner confirms `new duo customisition icon.jpg` is the design source. `splitIndicators` currently means separate views, not a split battery (`DuoPrefs.kt:44-55`); four fixed dots (`scene.rml:786-805`); no eight-dot/SIM-pair split state exists. | Image available; transition still unverified |
| C4 | Middle-of-ring “Charging”: Wi-Fi takes the charging-number place and size; it is functional, not a preview. | `middleSlot=charging` puts battery level in the network Text, Wi-Fi disappears (`DuoMapping.kt:166-177`). | Rework |
| C5 | Clock must use system time (not hard-coded 24h), one line, not touch ring edges. | Clock uses `SimpleDateFormat("HH:mm")` (`DuoStateMonitor.kt:295`); shared `network` Text width 56, wrap (`scene.rml:762-776`); measured 5-char times need 72-77 units at font 30. | Bug + new text object |
| C6 | Move Contact Developer to Report. | See B10. | UI move |
| C7 | Camera/Compact ring: tap shows “Resize”; ring in head-up/status bar only, QS/shade keep normal position. | No camera cutout geometry; `lockRing` only centres the ring (`DuoPrefs.kt:52-55`); no “Resize” action. | **BLOCKED on image + Q** |
| C8 | FATAL/restarts happened around ring centre in status bar, rotation, and camera ring; shade/QS is the suspected trigger. | Owner report; no current owner capture. Old capture shows QS/shade surface churn and a separate LuckyTool tombstone. See section 6. | P0 evidence |
| C9 | At 100% charging, return to normal non-charging behaviour. | Charging remains charging while plugged; no 100% special case. | New state rule |
| C10 | Add burn-in toggle: shift the whole Duo icon 1 px left, return after one hour, repeat, without battery drain; skip if SystemUI already does it. | No burn-in setting or motion found. SystemUI capability not measured. | New feature + evidence |

### D. Download progress / PHDP

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| D1 | When a download starts, ignore charging; show download number + bar. Tap the icon to return to charging. Before this, download is overwritten by the charging bolt. | `chargingVisual` suppresses download digits (`DuoMapping.kt:194-200`); test asserts the bolt wins (`DuoMappingTest.kt:309-317`). | Fix state machine |
| D2 | Add a Download Progress toggle, latest-release direct link, Simulate button; if PHDP is absent show the toggle GIF; real status-bar simulation requires PHDP. | No download preference, no PHDP detection, no link, no test button, no GIF. PHDP has no public test trigger. | New feature + Q |
| D3 | Only PHDP provides real progress; Duo consumes PHDP rather than duplicating notification hooks. | Duo-PHDP broadcast contract exists (`DownloadProgress.kt:44-65`; `DuoStateMonitor.kt:156-185`); PHDP v2.11.0 is authoritative. | Contract exists |
| D4 | Download state should override charging from 0 to 100; at completion return to normal, not remain in download. | PHDP completion holds 100 then sends `active=false`; Duo has no selection state. | New state machine |

### E. Stability, all-UI parity, documentation

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| E1 | Fix the FATAL/safe-mode loop first. | See section 6. | P0 |
| E2 | Keep the ring normal in QS/shade when the camera-ring mode is used. | No camera ring; QS/shade attach is separate and measured. | Design rule |
| E3 | Every change must match all vendor UIs. | 2 measured UIs, 6 static-only; `agent/UI-MATRIX.md`. | Test gate |
| E4 | Read Rive system/animation docs before authoring. | `docs/rive-pipeline.md`, `rive/duo/AGENTS.md`, Rive CLI docs. | Mandatory |
| E5 | Write notes, brainstorm, ask 5 questions, then Requirements/Test Plan/Plan. | This file + `agent/BRAINSTORM-2026-11-02.md`. | This round |

---

## 3. Current app baseline (evidence)

### 3.1 Pages and navigation

- `DuoShell.kt:112`: `enum class DuoPage { HOME, ENGINE, REPORT, SETTINGS }`.
- Bottom nav items: Home, Graphics, Report, Settings (`DuoShell.kt:132-137`; strings `nav_engine=Graphics`, `nav_report=Report`).
- Home: live preview, “Customize ring” button to Settings, presets Classic/Compact/Bold (`DuoHomeScreen.kt:61-178`).
- Graphics: only engine selector Auto/Rive/Canvas (`DuoShell.kt:362-416`).
- Report: independent page (`DuoReportScreen.kt`).
- Settings: status/health, Customize, Animations, Appearance, Surfaces, Icons, Actions, Live Events, Simulation, Cell Simulation, Backup, About (`DuoSettingsScreen.kt:534-663`).
- Selected preset is `rememberSaveable` only; not persisted (`DuoHomeScreen.kt:66`).

### 3.2 Navigation pill/selector

- Wine glass selector: 44dp high, width `itemWidth-32dp` min 72dp, 26dp stretch, 4dp squash; wine tint alpha 0.10/0.16 (`DuoShell.kt:145-149,234-298`).
- Only the selector has `pointerInput`/`detectDragGestures`; tabs are individually `selectable` (`DuoShell.kt:200-206,299-357`).
- The selector already refracts the page and the pill via a separate layer backdrop (`:169-180,269-298`).
- Owner wants the selector transparent, icon colour change, and drag from anywhere on the pill.

### 3.3 Sliders and position editor

- `GlassSlider` has one `Semantics` box, tap on the whole 48dp height and drag on the whole width (`GlassControls.kt:214-240`).
- The thumb is 28dp; track height 10dp (`:197-200,242-312`).
- `PositionEditor` has tap left/right and drag across the whole 40dp editor (`DuoSettingsComponents.kt:237-291`). Owner wants only the icon/knob.

### 3.4 Presets

- `DUO_PRESETS` = Classic / Compact / Bold (`DuoPresets.kt:15-65`).
- A preset is a `DuoSettings` field transform; it does not store a complete snapshot (`:4-13`).
- Current Compact sets `size=100`, `splitIndicators=true`, `lockRing=true`, `hideOtherIcons=true`, `edgePadding=0` (`:33-49`) — not a camera ring.
- No `Default`/`Custom` persisted IDs or last-custom snapshot.

### 3.5 Clock

- `DuoStateMonitor.kt:295`: `SimpleDateFormat("HH:mm", Locale.getDefault())` → always 24-hour.
- Only `ACTION_TIME_TICK` updates; manual time/time-zone/locale changes are not in the filter (`:306-320`).
- Clock is rendered through the shared `network` Text (56×34, font 30, wrap) (`scene.rml:762-776`); Canvas uses the same 30-unit font (`DuoCanvasView.kt:318-327`).
- Measured text widths at font 30: 12:34 = 72, 23:59 = 77, 12:34 PM = 125 design units. The current 56-unit box cannot keep one line.

### 3.6 Rive / Canvas / ring

- Artboard 120×136, ring ellipse 103×103, stroke 8 (`scene.rml:17,164-169`).
- No constraints/rig; all seats are authored constants or bound numbers.
- `splitIndicators` splits Rive views, not the battery (`DuoPrefs.kt:44-55`; `DuoPart.kt`).
- Camera ring is not implemented; `lockRing` centres the ring.
- Canvas parity defects exist: different ring scale, DND seat, bolt seat, event scales, missing unlock/call art, no event motion (`DuoCanvasView.kt:167-171,477-533`).
- Current `.riv` SHA-256 `e4c4f4ec09dc0ccb47451b34dcfd0bf3085c42a0231566b474969798bc29716d`; docs record older hashes.
- `tools/check-rive-eases.py` currently reports 6 inert keyframes at `scene.rml:3492,3549-3552`.

### 3.7 Download / PHDP

- Duo consumes PHDP broadcasts: `io.github.kvmy666.duostatusbar.action.DOWNLOAD` and `DOWNLOAD_QUERY`.
- PHDP v2.11.0 latest (2026-10-21); direct asset `phdp-v2.11.0-release.apk`; no stable latest-APK URL.
- PHDP has no public test trigger. Its Duo test uses the same publisher as a real download; no `simulated` flag.
- Duo app cannot broadcast into the SystemUI-located receiver: receiver is `RECEIVER_NOT_EXPORTED`, different UID.
- PHDP default `showLabel=false`, so Duo must decide to honour it or add its own percentage switch.
- Charging currently wins over download digits (`DuoMapping.kt:194-200`).
- Only the ring has the download tap override; the split indicator view does not (`DuoIconHost.kt:323-324,1593-1633`).

---

## 4. Current settings inventory (for movement/deletion)

Order in `DuoSettingsScreen.kt:534-663`:

1. Customize
2. Animations
3. Appearance
4. Surfaces
5. Icons
6. Actions
7. Live Events
8. Simulation
9. Cell Simulation
10. Backup
11. About

Relevant rows:

- Appearance: Smooth Graphics (`useRive`), System-Font Clock, Icon Colour, Middle of ring (Auto/Wi-Fi/Cellular/Percent/Clock/Charging %), Airplane, DND mode, Wi-Fi dots, SIM choice.
- Animations: master, speed, arrival, departure, charging.
- Icons: hide other icons, Wi-Fi glyph, cellular dots, Shizuku hiding.
- Actions: tap/double/long press, indicator actions, download note.
- Live Events: Alarm, Flashlight, Screenshot, Microphone, Location, Volume, Unlock, Calls.
- Simulation: alarm/flashlight/screenshot live preview + Play.
- Cell Simulation: 0–4 levels.
- About: module status, problem text, send log, Contact Developer, root, export, update, donate, version.

---

## 5. Image evidence (retrieved 2026-11-02)

The owner connected the phone. The folder is `/storage/emulated/0/kilo images` on CPH2747. It contains exactly six files; they were pulled to `/tmp/duo-kilo-images/` and copied to the gitignored `reverse/kilo-images/` with matching SHA-256.

| Slot | File | Dimensions | SHA-256 | Content |
|---|---|---:|---|---|
| New Duo Customization reference | `new duo customisition icon.jpg` | 1122×872 | `e563548678d5cc88cb0f23fb11512027548d2c4af59ceb870e2212fbf76849ef` | Target ring crop: three rings (80 green / 49 yellow / 19 red); number above, black Wi-Fi centre, **eight SIM dots (four per side)**, colored battery line at the bottom; no app chrome |
| Home reference | `simple-rounded-house-icon-symbol-homepage-building-navigation-element-vector.jpg` | 980×980 | `757430018cd2c9f6f4e1ff38558d0b977cdf70f6d56638effdcf1629aed6d7ed` | Filled dark-navy house silhouette with arched door, 664×664 icon |
| Customization reference | `pill customasition icon.png` | 2464×1642 | `a5b7e15ddf8f99170758b49a5ade54a1d88fae1c71d146f3f28f8eda9596fa13` | White three-row sliders/tune icon, 83-unit rounded bars, 85-unit gaps, dark grid canvas |
| Report reference | `graph-report.jpg` | 1200×1200 | `60b1c668fd312a8d93c54fbced406d5a04bbee8f1fcac3c6fc4366f85bfb9ab9` | Two overlapping outline documents + rising trend arrow; Icons8 “Graph Report” stock icon |
| Selector resting | `static selector.jpg` | 756×238 | `5a98129d39927f5b6a32de5729fad8c72b33c25305cb6cc770a17c3c17635578` | Nav pill crop, active Home in blue; selector 225×179 px inside a 207 px-high pill |
| Selector held | `on movment.jpg` | 791×273 | `ccc59b7720259bb51dbbfb52b4c00b00b22074ff97aa0663c9d7613cf148de00` | Mid-motion crop; selector 296×231 px, overflows pill; active Home icon 76×90, label ~136×39 |

### 5.1 Measured selector/pill geometry

- Pill height: 207 px at rest, 209 px held; effectively unchanged (1.01×).
- Selector at rest: 225×179 px, centre inside the pill, 17 px below top and 11 px above bottom.
- Selector held: 296×231 px, 9 px above and 13 px below the pill; overlaps the next label.
- Held/resting: width 1.316×, height 1.290×, area ~1.60–1.70×; effective scale 1.26–1.30×.
- Resting fill: frosted charcoal `#232425`, rim `#323233`; held fill lighter `#343536`, rim with chromatic fringe.
- Active Home: blue `#3c88f8` at rest; label 98×29; icon 61×64. Held: icon 76×90 (width 1.246×, height 1.406×), label ~136×39.
- The active item grows with the selector; inactive items keep their resting size.
- The held image is a transient mid-motion frame; exact settled enlarged size still needs confirmation.

### 5.2 What these references are and are not

They are **icon/selector references plus one ring-design crop**, not full Home/Customization/Report screen layouts. They do not show:
- the final app page structure, cards, sections, or report form;
- the camera cutout or “Resize” affordance;
- the split-battery transition frames (only the target static 8-dot ring is shown);
- the ring at status-bar scale in all surfaces.

Owner answer 2026-11-02: **three tabs: Home / Customization / Report**, with Settings opened from a gear on Home. The selector mockup's Home/Components/About labels are not the final tab names; use the house/sliders/report icons.

Owner answer on the ring: the `new duo customization` image is the design source: eight SIM dots, battery line at the bottom, Wi-Fi centre, number above. The earlier "current rejected screen" label was wrong and is corrected here.

Local substitute images were not used. The originals are archived at `reverse/kilo-images/` and ignored by git (`.gitignore:30: reverse/`).

---

## 6. FATAL / safe-mode evidence

### 6.1 Owner report

- FATAL appeared after the shade/QS feature.
- Occurs on portrait→landscape rotation.
- Occurs when the ring is placed in the camera position.
- LSPosed reportedly blamed LuckyTool; LuckyTool’s log reportedly blamed the status bar/module.
- Highest priority.

### 6.2 Captured evidence (2026-11-02/03, CPH2747)

Owner authorized a read-only pull. Evidence is in `reverse/fatal-logs/` (gitignored): 92 files, 31 MB, with `MANIFEST.tsv`.

- LSPosed coverage: 2026-11-02 20:46:06 → 22:17:02, 22:17:02 → 23:09:40, 23:10:00 → 00:08:40 (+03). Oct 31 LSPosed logs have rotated away.
- Tombstones: 11 text files. Dropbox: 59 entries. Logcat: crash/main/system/events tails (30k each).
- Current SystemUI at capture: pid 7530, beta.45, 3 Rive attaches since 23:10, stable.
- Current Duo settings (rev 3864): `stage=2`, `engine_mode=auto`, `use_rive=1`, `show_in_qs=1`, `show_in_shade=1`, `live_apply=1`, `size=130`, `show_percent=1`, `percent_height=56`, `offset_x=0`, `edge_padding=100`, `split=0`, `lock_ring=0`, `middle_slot=auto`, `icon_color=auto`, `event_mask=255`.

### 6.3 Crash A — Java FATAL in QS `ConstraintLayout` (rotation)

Exact signature, `dropbox/system_app_crash@1793641719781.txt:11-26`:

```text
java.lang.RuntimeException: All children of ConstraintLayout must have ids to use ConstraintSet
  at androidx.constraintlayout.widget.ConstraintSet.clone(...)
  at com.oplus.systemui.plugins.qs.OplusQSRootViewComponent.updateResource(...:955)
  at ...cellCalculatorListener$1.onGridInfoChange(...:6)
  at ...CellCalculatorManager.setNoPersonalRowCountLandRight(...:26)
```

The same signature is in `system_app_crash@1793641993087.txt:11-20`, called from `OplusQSRootViewComponent$onViewAttached...emit(...:645)`.

Causal chain in `lsposed/log.old/verbose_2026-11-02T20:46:06.552425.log`:

```text
20:48:39.074  panel owner -> qs header
20:48:39.119  shade route slot=qs header cluster=391x63 target=RelativeLayout[1272x63]
20:48:39.130  element: Rive (stage 2, ALL)
20:48:39.301  qs header layout target=androidx.constraintlayout.widget.ConstraintLayout[1272x2772] ... parent=ConstraintLayout
20:48:39.302  Duo injected into qs header (RelativeLayout, 124px)
20:48:39.770  FATAL (landscape grid: setNoPersonalRowCountLandRight)
20:48:43.578  orientation is now portrait
```

The injected `DuoRiveView` has `id=-` (NO_ID), and `DuoIconHost.attachInto` calls `target.addView(candidate.ui)` without assigning an id (`DuoIconHost.kt:972`); the target is a `ConstraintLayout` (`DuoIconHost.kt:984-987`). Beta.45 still logs the same `ConstraintLayout` target at `23:13:44.660`. No QS rotation has happened since the 23:10 boot, so beta.45 has not re-observed it.

This is **not LuckyTool**: the Java stack is OnePlus QS + Duo's id-less child. It is the rotation crash.

### 6.4 Crash B — JNI global-reference overflow (Rive re-import storm)

Exact tombstone, `tombstones/tombstone_03:15-35`:

```text
Abort message: 'JNI ERROR (app bug): global reference table overflow (max=51200)'
48771 of android.graphics.Paint (48771 unique instances)
  444 of app.rive.runtime.kotlin.core.FallbackAssetLoader
   33 of app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
```

Backtrace, `tombstone_03:233-261`:

```text
art::JNI<false>::NewGlobalRef
  ← rive_android::CanvasRenderPaint::CanvasRenderPaint()
  ← rive_android::AndroidCanvasFactory::makeRenderPaint()
  ← rive::File::import()
  ← Java_app_rive_runtime_kotlin_core_File_import
  ← RiveAnimationView.loadFileFromResource
  ← RiveAnimationView.onAttachedToWindow
  ← DuoIconHost.ensureCluster
  ← DuoIconHost.syncExtraIndicators
  ← DuoIconHost.attachInto
  ← DuoIconHost.attachExtra
  ← DuoHook.attachExtraBars
  ← DuoHook.addShadeLayoutListener$lambda$0
```

Quantitative link: **48,771 Paint refs / 444 FallbackAssetLoader refs (≈444 imports inferred) = ~109.8 new Paint global refs per import.**

Immediate trigger in the LSPosed log:

```text
22:15:31.470  settings rev 3356: size 130%, percent=true, split=false, lock=false, edge=100%
22:15:32.167  settings rev 3357: size 100%, percent=false, split=true, lock=true, edge=0%
22:15:32.169  ring locked to PhoneStatusBarView 1272x141
22:15:32.180  element: Rive (stage 2, INDICATORS)
...           8-12 "Duo view ready" per second
22:16:01.573  SIGABRT (global ref table full)
```

The storm repeated on each restart: pid 1184 **269** Rive views in 32 s; pid 7975 **188** in 25 s + 64 in 9 s; pid 10588 **291** in 33 s. This is the camera/lock-ring crash path; `lock_ring` is the module's camera-cutout lock (`DuoPrefs.kt:282`).

### 6.5 Crash C — LuckyTool only at boot (not the Nov 2 cause)

`tombstones/tombstone_01:7-38` and `tombstone_28` show SIGABRT at 2026-10-31 02:34:14, pid 7620, uptime 4 s, 12 frames in `com.luckyzyx.luckytool/base.apk!libdexkit.so` (frame `0x1e948` repeated 3×). No `Abort message`. These are **different timestamps and PIDs** from every Nov 2 Duo crash. LuckyTool is scoped in SystemUI and can crash at boot, but it did not cause the Nov 2 QS/JNI crashes.

### 6.6 Ranked conclusions

| Rank | Conclusion | Confidence | Evidence |
|---|---|---|---|
| 1 | Crash A: id-less `DuoRiveView` in OnePlus `ConstraintLayout`; rotation/grid update calls `ConstraintSet.clone` | **Very high** | Java stack + `id=-` + `addView` path + 468 ms timing |
| 2 | Crash B: re-creating/importing Rive views on every shade layout pass while lock/camera mode is on; each import leaks ~110 global Paint refs until ART aborts | **Very high** | 444 FallbackAssetLoader refs (≈444 imports) → 48,771 Paints; 8–12 attaches/s; 29 s to abort; pure Duo/Rive stack |
| 3 | LuckyTool boot aborts are real but separate | High for Oct 31 | 12/12 frames in `libdexkit.so`; different time/PID |
| 4 | Generic Rive runtime leak independent of Duo's attach rate | Low | The leak exists, but only 444 imports in ~30 s reached the 51,200 limit |

The exact sub-setting that started rev 3357 is not isolated because five values changed together; the fix must remove the re-attach/re-import path, not merely reverse one setting.

### 6.7 Minimum verification after the fix

- QS open, rotate portrait→landscape, pull logcat + LSPosed + dropbox; expect **zero** `ConstraintSet.clone` FATALs and no new SystemUI pid.
- Hold the shade open and toggle size/split/lock for 60 s; count `File::import` and `Duo view ready`; assert imports do not grow per layout and `global refs << 51200`.
- Camera/lock mode: one Rive surface, no reattach storm; 60 s stress; no JNI abort.
- Keep a settings-only A/B (`stage=1` Canvas vs `stage=2` Rive) to separate the two mechanisms.

---

## 7. Contradictions and unknowns

### Contradictions resolved by owner answers (2026-11-02)

1. **Smooth Graphics:** remove the UI switch; Rive is the default/primary renderer; keep Canvas as the explicit fallback selector in Customization.
2. **Icon Colour Match:** keep the control; add a white↔black GIF preview.
3. **Charging:** remove the Charging animation section and the “Charging %” Middle-of-ring option. Charging state/bolt behavior remains unless a later requirement removes it.
4. **Calls:** remove the toggle from the UI; keep the event permanently off; retain code/art for a later version.
5. **App structure:** three tabs Home / Customization / Report; Settings opens from a gear on Home.
6. **Simulate:** PHDP has no public test trigger; the button will open the PHDP app so the user can run its own test. Without PHDP, show the GIF preview only.
7. **Split battery:** the `new duo customization` image is authoritative: eight SIM dots (four per side), battery line at the bottom, Wi-Fi centre, number above. The split transition still needs an explicit storyboard before implementation.
8. **Live preview:** always present in Customization; updates only when a setting changes; persists; no continuous loop.

### Existing contradictions still requiring a design decision

- The 8-dot image shows the static target, not how the battery line “moves to the old four-dot position” during the split transition.
- “Battery line at bottom” plus “battery moves to the old four-dot position” could mean a reflow of the arc halves; no transition frames yet.
- The ring number is above and Wi-Fi centre in the image; when charge % is centre, Wi-Fi must move above. The image does not show that swapped state.

### Unknowns requiring evidence

- Camera cutout geometry and “Resize” interaction on the final Compact design.
- Whether the Wi-Fi glyph fits the old percentage seat at full size; current geometry says it does not (top-gap inner width ≈55.4 units; Wi-Fi bbox ≈49.6×50; its centre would sit outside the ring). A scaled pose or a different top-gap variant is required.
- Whether `splitIndicators` must be disabled for the charge-centre and camera modes (currently split moves the inner icons to a different view, so an in-ring swap is impossible there).
- Event vs center vs charging priority.
- Whether SystemUI already applies a burn-in shift on CPH2747.
- Exact transition frames for the eighth-dot battery split; the static design is clear, the motion is not.
- PHDP simulation: the owner accepted opening PHDP’s own test; direct injection is not approved.

---

## 8. Preliminary risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Id-less child in OnePlus `ConstraintLayout` | Rotation FATAL / safe mode | P0: assign a stable ID before `addView` or inject into a compatible host; QS rotation test |
| Rive re-import storm under shade layout | JNI global-ref overflow / safe mode | P0: one view/import per surface; reuse; no per-layout reattach; import/global-ref counters; stress test |
| LuckyTool boot abort | Separate safe-mode cause | Keep separate; do not blame for Nov 2; test with LuckyTool off if needed |
| Preset snapshots corrupt settings | User loses custom ring | Versioned `DuoPresetState`; backup round-trip; no silent migration |
| Ring object decomposition breaks Canvas | Fallback differs | Shared geometry table + parity tests before Rive art |
| Wi-Fi top-gap swap does not fit | Overlap/clipping | Measure with image; author scaled pose; verify headless |
| Split battery transition wrong | Wasted Rive work / visual mismatch | Image-derived static layout; owner review of transition storyboard |
| Clock string too wide | Wrap/touch ring | Dedicated text object, no-wrap/fit, system format |
| PHDP simulation impossible | Requirement gap | Open PHDP test; GIF fallback; no unauthorized injection |
| Slider knob-only hurts accessibility | Lower usability | Keep semantics `setProgress`; knob hit target ≥48dp; test |
| Burn-in hourly timer drains battery | Battery regression | Use existing SystemUI capability if present; otherwise measure scheduler/8h idle |
| All-UI claim without evidence | False support | Keep `unverified`; per-UI matrix gate |

---

## 9. Immediate next actions (no code)

1. ~~Pull/read the six `kilo images`~~ — done; 8-dot ring reference and selector measurements captured.
2. ~~Capture FATAL evidence~~ — done; two root causes identified.
3. ~~Answer the five brainstorm questions~~ — done.
4. ~~Write `agent/REQUIREMENTS-1.6.md` with stable IDs.~~ — done.
5. ~~Write `agent/TEST-PLAN-1.6.md` with traceable cases and gates.~~ — done.
6. ~~Write `agent/PLAN-1.6.md` with phases, weapons, and traceability.~~ — done.
7. Review Requirements ↔ Test Plan ↔ Plan; then implement P0 only after owner approval.
8. Preserve the dirty beta.45 worktree per `PLAN-1.6.md §2.0` before P0.

---

## 10. Change log

- 2026-11-02: Initial pre-plan evidence and change inventory. No code, build, or install performed.
