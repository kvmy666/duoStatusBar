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
| DND / silent | — | — | configurable: badge or middle-slot occupancy | — |
| Wi-Fi + cellular + airplane all active | — | — | **app setting decides which one owns the middle slot** | — |

## 5. Animation timeline (FR-25) — trigger: screen-on, unlock, or keyguard appearance

| Window | Motion |
|---|---|
| `0 – 100 ms` | whole group scale `1.00 → 1.12` (ease-out cubic) |
| `0 – 200 ms` | battery trim `0 → level %`; digits count `0 → level`; Wi-Fi arcs appear bottom→up (layer 3 → 1, 60 ms stagger); cell spheres light 1 → N (40 ms stagger) |
| `200 – 300 ms` | group scale `1.12 → 1.05` |
| `300 – 500 ms` | spring back `1.05 → 1.00`, ~2 visible bounces (damping ≈ 0.35) |
| **total** | **≤ 500 ms** |

All parts belong to one Rive state machine and one scale group, so they start and end together (FR-25).
Airplane toggle: Wi-Fi arcs collapse into the centre dot (120 ms), then the plane scales `0 → 1` from
inside that circle (180 ms) and settles; reverse on disable.

## 6. Still to measure on the device (never assumed)

* Real status-bar height and the icon slot height in **portrait and landscape** on OOS 16.
* Available width in the right-hand icon cluster (how many px the Duo element may occupy).
* Device density: physical 560 dpi, **override 484** → all sizes are computed in `dp` at runtime.
