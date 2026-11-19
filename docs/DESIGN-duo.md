# Duo — locked visual design spec

**Status: LOCKED** (v1, 2026-09-20). Sources, in priority order:

1. `Screenshot_2026-09-20-19-38-19-21*.jpg` — the element with the percentage **off** (continuous ring).
2. `Screenshot_2026-09-20-22-05-08-52*.jpg` — the element with the percentage **on**: `50` (normal) and `16`
   (critical, red arc), plus Apple's **Battery Percentage** toggle (green `#34C759`).
3. Geometry cross-check: `lingyired/status-trio` vector source (independent re-creation of the same
   Apple Duo element) — its coordinates match the screenshots, so they are used as the numeric basis.
4. Colour check: Apple system colours (green `#34C759`, red `#FF3B30`, yellow `#F2B900`); measured
   means from image 2 agree (green 58/191/92, red 239/78/82 with JPEG + anti-aliasing).

Nothing here is guessed: every number below is either measured from the screenshots or taken from the
matching vector source.

## 1. Canvas

The element is drawn in a **normalized 120 × 120 box** (the reference vector's coordinate space) and
scaled to the status-bar icon height. All values below are in those units. Strokes use **round caps
and round joins**.

| Part | Geometry | Notes |
|---|---|---|
| Battery ring | centre `(59.5, 61.5)`, radius `51.5`, stroke `8` | the outer ring |
| Ring bottom gap | `117.4°`, i.e. each endpoint sits **58.7°** either side of straight-down | the 4 cell spheres live in this gap |
| Ring top gap — percentage ON | `71.3°` centred on top | holds the 2–3 digits |
| Ring top gap — charging | `55.6°` centred on top | narrower: holds the bolt |
| Ring top gap — percentage OFF | `0°` (ring drawn continuously) | image 1 |
| Wi-Fi — layer 1 (strongest) | arc radius `31`, stroke `7`, centre `(59.5, 78.5)` | drawn bottom→up |
| Wi-Fi — layer 2 | arc radius `18.5`, stroke `7`, same centre | |
| Wi-Fi — dot | ≈ `12 × 11` rounded triangular blob at `(59.5, ~75)` | present whenever connected |
| Cell sphere 1..4 | radius `5.5`, centres `(33, 104.2)`, `(50.5, 111.2)`, `(68.5, 111.7)`, `(86, 105.8)` | outer spheres sit **higher** (concentric with the ring) |
| Value text | digits centred at the top gap, ≈ `32 / 120` cap height, bold **rounded** face, tabular figures | `50` / `16` in image 2 |

## 2. Battery fill mapping (measured)

The fill starts at the **bottom-left endpoint** and travels **clockwise** (up the left side, over the top):

* `0 %` → nothing drawn
* `16 %` → a short arc just above the bottom-left endpoint (the red segment in image 2, right element)
* `50 %` → the **whole left half**, ending exactly at the top gap's left edge (image 2, left element)
* `50 % → 100 %` → resumes at the top gap's **right** edge and descends to the bottom-right endpoint
* `100 %` → full ring minus the top gap (and minus the bottom gap)

Implementation in Rive: one path for the ring, cut by a top gap, driven by a **trim path** bound to the
battery level — this reproduces the mapping above without the dash-offset trick used by static clones.

## 3. Colours

| State | Colour | Source |
|---|---|---|
| Normal | status-bar foreground (white on dark, black on light) | images 1 + 2 |
| Track (unfilled ring) | foreground at **22 % opacity** | measured ≈ `#C7C7CC` look on light |
| Critical (`< 20 %`, saver off) | **`#FF3B30`** | image 2 (measured mean 239/78/82) |
| Charging | **`#34C759`** | Apple system green; matches the toggle in image 2 |
| Battery saver on | **`#F2B900`** | Apple Low Power Mode yellow |
| Charging + saver | charging green wins | iOS behaviour (user-confirmed default) |
| Digits / bolt | same colour as the fill, with a soft drop shadow (`dy .75`, blur `.75`, 38 % black) | reference vector |

## 4. State matrix

| Condition | Ring | Top gap | Inside the ring | Bottom spheres |
|---|---|---|---|---|
| Percentage on | trim = level | digits, gap 71.3° | Wi-Fi (or chosen middle item) | signal level |
| Percentage off | trim = level | gap 0° (continuous) | Wi-Fi | signal level |
| Charging | trim = level, green | bolt, gap 55.6° | Wi-Fi | signal level |
| Low battery `< 20 %` | trim = level, red | digits, red | Wi-Fi | signal level |
| Saver on | trim = level, yellow | digits, yellow | Wi-Fi | signal level |
| Wi-Fi level 0..3 | — | — | arcs appear bottom→up, layer 3→1 | — |
| Cellular level 0..4 | — | — | — | spheres light 1..N |
| Airplane on | — | — | arcs merge to the dot, then the plane scales 0→1 from inside and settles in the slot | unchanged |
| DND on | — | — | configurable: badge or middle-slot occupancy | — |
| Wi-Fi + cellular + airplane all active | — | — | **app setting decides which one owns the middle slot** | — |

## 5. Animation timeline (FR-25) — trigger: screen-on, unlock, or keyguard appearance

| Window | Motion |
|---|---|
| `0 – 100 ms` | whole group scale `1.00 → 1.12` (ease-out cubic) |
| `0 – 200 ms` | battery trim `0 → level %`; digits count `0 → level`; Wi-Fi arcs appear bottom→up (layer 3 → 1, 60 ms stagger); cell spheres light 1 → N (40 ms stagger) |
| `200 – 300 ms` | group scale `1.12 → 1.05` |
| `300 – 500 ms` | spring back `1.05 → 1.00`, ~2 visible bounces (damping ≈ 0.35) |
| **total** | **≤ 500 ms** |

> **Prototype timing (2026-09-21):** on user feedback the reveal was raised first to 1 s, then **4 s**
> (240 frames) — the 500 ms version was too subtle to notice on boot. The table above is the target; the
> prototype runs the same shape at 8×. It comes back down once the motion reads well. The ring fill is also
> animated: the value chases the battery level over 2.4 s (`DuoStateMonitor`), from 0 on first attach and
> again on every reveal, instead of snapping — the "fill from 0" the table's `0 – 200 ms` row describes,
> done in the host because a keyed Rive value cannot target a live level.
>
> **Size ceiling (2026-09-21):** the element was capped at the 61 px icon strip, so the size setting did
> nothing above ~75 % (100 % and 140 % were identical). The view is now a square that follows the setting,
> is centred on the status bar window rather than the strip, and is capped only at the window's own height;
> `DuoPrefs.MAX_SIZE` is 200.

All parts belong to one Rive state machine and one scale group, so they start and end together (FR-25).
Airplane toggle: Wi-Fi arcs collapse into the centre dot (120 ms), then the plane scales `0 → 1` from
inside that circle (180 ms) and settles; reverse on disable.

## 6. Measured on the device (Phase 0) — no longer assumed

Read from `com.android.systemui` on the target phone (full evidence in
[`devicereport-oos16.md`](devicereport-oos16.md)). Density `3.025` (484 dpi override), portrait:

| Item | px | dp |
|---|---|---|
| Status-bar window height | 141 | 46.6 |
| Icon row height / top offset | 90 @ y38 | 29.8 |
| Icon height (the strip) | 61 | 20.2 |
| End-side cluster width (`status_bar_end_side_content`) | 329 | 108.8 |
| `statusIcons` container width | 246 | 81.3 |
| Battery slot (`id=battery`) | 83 × 61 @ x1105 | 27.4 × 20.2 |
| Wi-Fi slot (`id=wifi_combo`) | 70 × 61 @ x913 | 23.1 × 20.2 |
| Mobile slot (`id=mobile_combo`, ×2) | 61 × 61 @ x983, x1044 | 20.2 × 20.2 |
| Clock (`id=clock`) | 128 × 90 @ x74 (left side, kept) | 42.3 |

**Width budget for the Duo element:** the freed cluster spans `x 859 … 1188` (329 px). Its default home is
the battery column (`x 1105 … 1188`, 83 px), so the element is drawn in an **83 × 83 px square** rendered
at 3.025× density — which is exactly the reference element's proportions (the reference images show a
square-ish bounding box: value, ring, then the cell spheres). Landscape is measured in Phase 3.

## 7. References and licences

Reviewed before writing anything of our own. Licences matter: everything reused here is permissive,
and the Apple-derived geometry is public design information (icons/simple geometric shapes are not
copyrightable in the way artwork is), reproduced as our own vector data.

| Project | What it is | What we take | What we do not |
|---|---|---|---|
| **`lingyired/status-trio`** (Apache-2.0) | macOS app that combines Wi-Fi + battery + volume into one status icon, explicitly *"inspired by the iPhone Duo combined status bar"* | **The numeric geometry basis** of `DESIGN-duo.md`: ring radius 51.5 / stroke 8, the two-segment ring split by a top gap for the value, 3 Wi-Fi arcs + dot, 4 cellular spheres, green `#34C759` / yellow `#F2B900` / 22 % track. Cross-checked against the user's iPhone screenshots, which matched. | Its Swift implementation, its `--battery-top-gap` dash-offset trick (we animate a real `TrimPath` instead), its volume indicator |
| **`LinYi-7/Duo-Status`** (MIT, Kotlin) | **Android *overlay* widget** — its README states plainly that a normal APK cannot write into the real status bar, so it floats a draggable window; no root | Its signal mapping tables and UX defaults once reviewed: Wi-Fi 3 levels, 4 cellular dots, grey for inactive parts, size range 52 %–120 %, and the black/white/green colour options — all of which match our own measurements | The whole approach. An overlay cannot satisfy FR-08 (the stock icons must be *really* removed), FR-03 (must behave identically on the lock screen and in every app), FR-05 (gestures inside the status bar) or FR-17 (drag inside the status bar). It is also not animated (FR-04/FR-25) |
| **`Block-Network/StatusBarLyric`** (GPL-3.0) | LSPosed module that injects its own view into the status bar and claims broad ROM support | *Idea/technique* only: add a custom view to the status bar instead of overlaying it | Code (GPL-3.0 would relicense our project; we keep our own implementation) |
| **`MonwF/customiuizer`, `tianma8023/XMiTools`** | SystemUI tuning modules for MIUI | *Idea* only: hide icons by acting on the icon controller rather than by covering them | Code (MIUI-specific) |
| **Rive CLI samples** (`Montserrat.ttf`, OFL) | Font shipped with the Rive CLI | The placeholder font for the digits until a rounded face is chosen | — |

### Conclusion

**No existing project can be adopted wholesale**, and that is not a style preference: the only way to
meet FR-08 (real removal of the stock icons), FR-03 (identical behaviour on home screen, in apps, on
the lock screen, in both orientations), FR-05/FR-18 (gestures that coexist with Auto Expand) and
FR-04/FR-25 (Rive animation of a live element) is to run *inside* `com.android.systemui` as an LSPosed
module — which is exactly what Phase 0 proved possible on this device, including loading the Rive
runtime there.

What we did reuse is the expensive part nobody should redo: **the measured geometry and colour
endpoints** (from `status-trio`, validated against the user's screenshots) and the **state mapping
conventions**. Everything else is authored here, in text, and verified with `rive --verify` plus
screenshots — cheaper and more auditable than clicking in an editor.

