# iOS glyph geometry — the one source of truth

`hook/IosGeometry.kt` holds these numbers; `rive/ios/scene.rml` repeats them. The battery body is
`H = 100` design units. The reference captures are kept outside the repository; this document is the
frozen record of the numbers, thresholds and ±px.

The real-iPhone references are ref 6 (single-SIM), ref 5 (dual-SIM) and ref 1 (owner target); ref 7 is
the owner device running our current glyphs. Every number below was printed by a 50 %-threshold
measurement script: the original table is reproducible from `.pm/ref/out-r2/measurements.md` /
`measurements.json`, and the R5B data-label numbers from `.pm/ref/out-r5/r5b-measurements.md` /
`.json` (ref 1, 1272x2772, battery body H = 42.377 px).

> **The old "annotated" step-3 target is superseded.** The `ios-reference-arrows` mockup specified
> 15.2 x 36.8 for the cap and 48 / 63.4 / 81.2 / 99 for the single bars. That mockup is the outlier:
> the recovered ref 6 (original upload sha256 `3563a34b…bb50b0`, 1239x726) confirms the light capture's
> 9.5 x 26.0 cap and 36 / 52.8 / 73 / 95.5 bars. The mockup's 30 % taller bars and 55 % wider cap are
> not adopted.

## Battery (artboard `Ios`, 217.2 x 102, frozen envelope)

The artboard keeps the pre-R2 217.2 x 102 even though the cap is now smaller, so every runtime,
preview and evidence placement that pins the battery artboard does not move. Do not derive the
artboard width from the cap again.

| Quantity | Value | Source |
|---|---:|---|
| Body width / height | 192.5 / 100 | 1.933 H (light); refs 193.3-194.9 |
| Body corner radius | 33.0 | 0.319 H (light area fit) / 0.328 H dark |
| Cap width / height | 9.5 / 26.0 | mid-range of refs 1/5/6 (8.75-10.26 x 23.75-28.09); supersedes annotated 15.2 x 36.8 |
| Cap gap | 7.5 | measured 7.04-7.87 |
| Cap lift above body centre | 0.0 | measured cap centre on the body centre |
| Cap shape | right-hand half-superellipse n=1.8, 25-gon, flat base on the body side | same shape in every reference |
| Cap colour / alpha | neutral tint (0:1003) at 0.50, same as the remainder (not batteryFillColor) | refs 1/5/6 partial charge (0.49-0.57); owner rule 0.50 |
| Uncharged remainder | neutral tint (0:1003) at 0.50 alpha | measured 0.496-0.537 |
| Fill | left `level * 192.5`, clipped by the body | light solid 1.00; dark split 58.4 % |
| Bolt | 61 x 96, 21-point polygon; white fill + 3.0 dark outline (6-unit stroke; ref ring ~4.5, capped to fit); inline 40 x 59, no outline | refs 3 / 1 / 2 / 4 |

The cap and the uncharged remainder use the neutral tint at 0.50, not `batteryFillColor`. At 100 % the
light reference draws the cap at full neutral ink; that exception is **runtime-only** and is not
adopted by the scene (see the caveats).

The old 7-point invented bolt (24 x 55 in the source, 30 x 55 as previously documented here) is
removed. The scene now repeats `IosGeometry.Battery.BOLT_POINTS`: the 21-point polygon measured from
ref 3 (and cross-checked against refs 1/2/4), normalised to 61 x 96 at the body centre, filled white,
and painted with the 3.0-unit dark outline (`BOLT_OUTLINE`, authored as a 6-unit round stroke) before
the fill. The inline bolt is the same normalised polygon scaled to 40 x 59 and has no outline.

The ref-3 ring measures about 4.5 units, but the white bolt is 96 units tall and the frozen battery
artboard is 102 units, so only 3 units are available above and below it. A 4.5-unit half-stroke would
put the stroked extent at -1.5..103.1, outside the artboard; the shipped 3.0-unit outline keeps it at
0..101.6 and fits exactly (96 + 2 x 3.0 = 102). The same fit guard
(`2 * BOLT_OUTLINE + 96 <= ARTBOARD_H + 0.01`) is asserted in `IosGeometryParityTest`, so the scene and
the constant cannot drift apart again. Whether the host view bounds clip the 0/101.6 edges cleanly on a
real device is still **UNVERIFIED**.

## Percentage inside (R3D, digit-v6 filled outlines)

Runtime toggle `ios_percent_inside` (default **off**). When on, the battery percentage is drawn inside
the body from `hook/IosDigits.kt` (our own filled contours, no font); `IosDigits` is the matching plain
Rive artboard in `rive/ios/scene.rml` (no state machine, no view model).

The shipped design is **digit-v6** (R3D.1): every digit is a filled outline at cap 57, drawn with
`Path.FillType.EVEN_ODD`. The first contour is the outer edge and every further contour is a separate
counter (hole), so winding order does not matter. There is no runtime stroke and no constant advance:
each glyph carries its measured ink width, advance and bearing `(advance - width) / 2`; layout is
`sum(advances) - firstBearing - lastBearing`.

All ten digits are rebuilt from the regular reference (ref 4 for 1-9, ref 6 for 0) with **one
homogeneous construction**: a uniform Euclidean boundary dilation `R` (padded, no cap clamp) followed
by a uniform cap rescale to 57 units. The per-digit `R` is binary-searched so the traced 16 px/unit
glyph's run-stroke is **11.40** (window 10.83..11.97). That is the measured bold in-battery weight:
the native bold refs 3b/6B/9B measure **12.27 / 11.40 / 10.69** (median 11.40). The v5 mix
(0/1/2/4/5/7/8 at the regular weight, 3/6/9 area-tuned to bold) is gone; it made `100` thin and `69`
bold.

Shape acceptance has two columns:

- **Primary (R3D.1), inverse weight-normalised IoU:** undo the exact construction on the shipped
  contour set (scale by `(57 + 2R) / 57`, erode by exactly `R` with a padded Euclidean distance
  transform, crop and cap-match), then compare the recovered glyph to the **unmodified** ref-4/6
  mask with `L.iou`. `iou_inverse_ref` is **0.974-0.989 at 32 px/unit** (0.977-0.992 at 16) for all
  ten; the untraced-construction control through the same inverse is **0.980-0.997**, proving the
  inverse is accurate. This is a real comparison to the ref, not a construction identity.
- **Secondary/legacy (R3M), equal-area-erosion IoU:** `L.eval_iou(candidate, ref_regular)`. This is
  **not weight-invariant** for these bold glyphs: the untraced control scores the same **0.657-0.849**
  (digit 0 = 0.665), because `eval_iou` erodes to the regular ink area and then `resize_cap` inflates
  the width. It is a normalisation artifact, not a closed-counter effect, and is reported only for
  continuity.

| Quantity | Value | Source |
|---|---|---|
| Source data | `.pm/ref/out-r3/digit-v4/digits-v6.json` (cap 57, body 100, 2,251 points) | refs 1/2/4/6 |
| Fill rule | even-odd, counters as separate closed contours | filled outline, no font |
| Cap height | 57 | ref 4 and ref 6; 1/2 for bold weight |
| Group gap (digits -> inline bolt) | 5 | measured bold refs 3/6/9 |
| Inline bolt | 40 x 59, follows the digits when charging | same 21-point bolt, no outline |
| Layout | the group is centred on the battery body | owner rule |
| Construction | ref-4/6 shape + uniform Euclidean dilation `R` = 1.436-2.722 u, no cap clamp, rescale to cap 57 | per-digit, traced run target |
| Weight | traced run-stroke 11.38-11.44 (window 10.83..11.97); EDT 9.27-11.50 | refs 1/2 |

| Digit | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Width | 39.06 | 23.12 | 39.50 | 38.12 | 43.38 | 39.00 | 38.50 | 35.94 | 38.19 | 39.38 |
| Advance | 43.81 | 30.91 | 44.81 | 49.47 | 46.54 | 43.71 | 50.59 | 39.34 | 43.55 | 50.59 |
| R (u) | 2.562 | 2.500 | 1.575 | 2.273 | 2.312 | 2.722 | 1.575 | 1.985 | 2.334 | 1.436 |
| Contours | 2 | 1 | 1 | 1 | 2 | 1 | 2 | 1 | 3 | 2 |

**Primary acceptance** is the inverse weight-normalised IoU (**0.974-0.989** at 32 px/unit and
**0.977-0.992** at 16; every digit >= 0.90). The legacy equal-area-erosion number is **0.657-0.844**
and is reported only as a secondary diagnostic (its untraced control is 0.657-0.849). The per-digit
tables are `.pm/ref/out-r3/iou-r3d.csv` and `.pm/ref/out-r3/stroke-r3d.csv`; the build-time table is
`.pm/ref/out-r3/digit-v4/iou-r3d-build.csv`. Overlaps are `.pm/ref/out-r3/r3d-overlay-0..9.png`
(shipped Kotlin contours' inverse against the unmodified ref-4/6).

The v6 layout fits the frozen body: `100` ink is 112.3 (body margin 40.1 per side) and `100` plus the
inline bolt is 157.3 (margin 17.6); `69` plus the bolt is 134.5 (margin 29.0). The scene's `IosDigits`
artboard and the runtime `IosDigits` object carry the same values; `IosGeometryParityTest` pins every
contour vertex and the even-odd fill, and `IosDigitsTest` pins the widths, advances, contour/point
counts and the canonical digest.

## Cellular (artboard `IosCell`, 150.1 x 105.6, frozen envelope)

| Quantity | Value | Source |
|---|---:|---|
| Column pitch | 41.0 | 0.410 H light / 0.409 H dark |
| Single bar width / heights | 25.1 / 36.0, 52.8, 73.0, 95.5 | ref 6 light/native 24.7-25.8 x 35.96-95.51; supersedes the annotated 48-99 mockup |
| Dual bar width / heights | 25.3 / 30.3, 37.5, 49.9, 62.7 | 0.253 H, 0.303-0.627 H (unchanged) |
| Bar corner radius | width / 2 | capsules |
| Dot width x height | 26.0 x 27.4 | 0.253 x 0.274 H (measured 18.0 x 19.5 px at dark) |
| Bar row -> dot row gap | 12.7 | measured 12.68 (old 13.5) |
| Inactive alpha | 0.25 | measured 0.257-0.268 |
| Block width | 148.1 | 3*pitch + width |
| Dual block height | 102.8 | 62.7 + 12.7 + 27.4 |
| Artboard height | 105.6 | frozen envelope, not `DUAL_BLOCK_H + 2` |

Single mode draws the four tall bars inside the same artboard, bottom-aligned at `SINGLE_BAR_BOTTOM`
= 96.5 (bar 4 top = 1.0, level with the battery body top; bar 4 bottom 4.5 units above the body
bottom); the dot row is hidden. `DUAL_BLOCK_H` is 102.8 but the artboard stays 105.6 so the
scene/preview/compare placements do not move.

Dual mode has two runtime paths (IOS-REPLACE Revision 3):

1. **True replacement, per stock slot (default on measured Oplus/HyperOS/Samsung/AOSP View bars):**
   the data slot draws only the compressed DATA bar row and the other slot draws only the OTHER dot
   row. Neither stock leaf is hidden; each leaf keeps its own slot and size. The row artboards are
   `ROW_ARTBOARD_W` = 150.1 x `DATA_ROW_ARTBOARD_H` = 64.7 (bars bottom at `TOP+DUAL_ROW_H`) and
   150.1 x `OTHER_ROW_ARTBOARD_H` = 29.4 (dots translated by `otherRowOffsetY()`); they are runtime
   viewports into the same `IosGeometry`, not new design geometry.
2. **Fallback overlay (Compose/mixed or a slot with no replaceable leaf):** one `BOTH` glyph over the
   union of both stock bounds, with both stock leaves suppressed by the approved hide-in-place skin.

If one stock slot disappears the glyph becomes single bars over the remaining slot; if anything is in
doubt both stock icons are restored and nothing is drawn. `Strip.DUAL_CELL_TOP_OFFSET` is used only by
the preview/evidence composition; the runtime glyph fills the bounds it is given.

## Wi-Fi (artboard `IosWifi`, 129.53 x 96.77)

Measured from the light single-SIM reference capture; the reference glyph bbox origin is light px
(243.24, 30.56) and the artboard adds a 1-unit margin on every side. This is the iOS glyph now, not
Duo's.

| Quantity | Value | Source |
|---|---:|---|
| Artboard | 129.53 x 96.77 | reference bbox 127.53 x 94.77 + 1-unit margin |
| Arc centre (concentric) | (64.809, 93.522) | viewBox (63.809, 92.522) + 1 |
| Outer arc | R 83.843, stroke 16.903, trim end 0.232575, offset 0.884175 | span 83.727 deg = -131.697 .. -47.970 deg, round caps |
| Inner arc | R 50.053, stroke 17.794, trim end 0.220078, offset 0.889917 | span 79.228 deg = -129.630 .. -50.402 deg, round caps |
| Dot | 32-gon, bbox x 47.50..82.14, y 69.09..95.77 | measured left-right symmetric contour |
| Level alphas (0..3) | outer 0.3/0.3/0.3/1; inner 0.3/0.3/1/1 | unchanged Duo binding rule; the dot stays full tint |
| IoU vs light reference | outer 0.976, inner 0.982, dot 0.988 | 8x supersampled fit; noise floor 0.979 / 0.981 |

The dark reference uses a **different stroke weight** (radii 4-6% smaller, strokes 4-15% thicker), so
the same geometry measures outer 0.836 / inner 0.930 there even though the spans agree (84.2/79.7 vs
83.7/79.2 deg). The light reference is the owner's design target; the dark frame is not a Wi-Fi gate.
The full fit and the dark cross-check are in the B4 measurement notes (kept outside the repository), sections 2 and 3. The old
"Duo uniformly scaled" glyph has been removed.

## Data-type label (R5B)

When the active network is not Wi-Fi, the Wi-Fi slot can draw a data-type label ("LTE", "4G", "5G",
"5G+", "3G", "E") from `IosGeometry.DataLabel`.

**Ref 1 is the first real iOS status-bar capture of this label.** `.pm/ref/r5/1-real-iphone-5g-dual.jpg`
(sha256 `0e0096738eb3b246…`, 1272x2772, dark, dual-SIM) reads "5G"; its right cluster (cell, label,
battery) was measured with the 50 %-threshold connected-component script. Battery body H = 42.377 px =
100 design units, so 1 px = 2.360 units and ±0.5 px = **±1.18 units**. Evidence:
`.pm/ref/out-r5/r5b-measurements.md` / `.json`, the annotated crop
`.pm/ref/out-r5/r5b-ref1-statusbar.png`, and the side-by-side
`.pm/ref/out-r5/` evidence written by R5B (`r5b-ours-strip-dark-4x.png`,
`r5b-compare-ref1-vs-ours.png`). The SF strip
`.pm/ref/r4/5-sf-labels-LTE-E-4G-5G-5Gplus.jpg` stays the authority for the glyph SHAPES, ink widths
and internal spacing only; its 36 px cap is the fraction denominator below.

### Before / after (design units, battery body H = 100)

| Quantity | Before (R4B) | After (R5B, measured) | Constant | Source |
|---|---:|---:|---|---|
| Label cap height | 66.85 | **77.69** | `DataLabel.CAP_HEIGHT` | 5 cap 32.93 px x 2.360 |
| Cap-box top | 14.96 (Wi-Fi-centred) | **14.95 (pinned, top-anchored)** | `DataLabel.TOP` | cap top 5.90 px below body top; artboard top = body top - 1 |
| Baseline | 81.81 | **92.64** | `TOP + CAP_HEIGHT` | measured 92.63 |
| Label stroke | 13.37 (0.200 cap) | **11.1 (0.143 cap)** | `DataLabel.STROKE` | median of 5 stem 9.87 / G stem 12.33 |
| Cell -> Wi-Fi gap | 57.5 | 57.5 (kept) | `Strip.CELL_WIFI_GAP` | ref 6 light, ink-to-ink |
| Wi-Fi -> battery gap | 59.77 | 59.77 (kept) | `Strip.WIFI_BATTERY_GAP` | ref 6 light, ink-to-ink |
| Cell -> label ink gap | 57.5 (Wi-Fi rule) | **52.07** | `Strip.CELL_LABEL_GAP` (new) | cell ink right -> label ink left 22.06 px |
| Label -> battery ink gap | 59.77 (Wi-Fi rule) | **54.04** | `Strip.LABEL_BATTERY_GAP` (new) | label ink right -> body left 22.90 px |
| Battery remainder alpha | 0.50 | 0.50 confirmed | `Battery.REMAINDER_ALPHA` | 0.4975 (RGB 0.501/0.497/0.499) |
| Battery cap alpha | 0.50 | 0.50 confirmed | `Battery.CAP_ALPHA` | 0.5021 (cap interior) |
| Inactive bar/dot alpha | 0.25 | 0.25 **UNVERIFIED** | `Cell.INACTIVE_ALPHA` | ref 1 has all four bars + dots active |
| Battery cap W / H | 9.5 / 26.0 | unchanged (low confidence) | `Battery.CAP_W/H` | measured 13.7 / 34.3, outside this change |
| Scene / `ios.riv` | — | **untouched** | — | no scene-represented alpha changes; Rive CLI absent |

### Measured label (ref 1) and the top-anchored mapping

| Reference quantity | Measured | / cap (77.69) |
|---|---:|---:|
| Cap height (5 flat top -> baseline) | 32.93 px | 1.000 |
| Cap top below battery body top | 13.95 u | 0.180 |
| Cap top in label/Wi-Fi artboard | 14.95 u | 0.192 |
| Baseline in label/Wi-Fi artboard | 92.63 u | 1.192 |
| Cap centre minus body centre | +2.79 u | 0.036 |
| 5 ink width | 52.85 u | 0.680 |
| G ink width | 67.88 u | 0.874 |
| Label ink width (5G) | 132.48 u | 1.705 |
| 5 -> G ink gap | 11.75 u | 0.151 |
| Label stroke (median 4.70 px) | 11.10 u | 0.143 |
| Cell ink right -> label ink left | 52.07 u | 0.670 |
| Label ink right -> body left | 54.04 u | 0.696 |
| Cell ink right -> body left (span) | 238.58 u | 3.071 |

The strip composition aligns the label/Wi-Fi artboard top with the battery artboard top at `y = 0`,
and the battery body top is at `y = 1` in its own artboard. With the measured body top 715.57 px and
0.4238 px/unit, the artboard top is 715.15 px; the measured cap top 721.48 px gives
**`DataLabel.TOP = 14.95`**. The label is therefore **top-anchored**: the baseline sits 8.37 u above
the body bottom and 35.19 u below the cell bar baseline, and the cap is **not** centred on the Wi-Fi
ink. The old formula `1 + (94.77 - cap)/2` gives 9.54 at this cap and is removed.

The ref-1 label is slightly **tighter** than the Wi-Fi slot: its two ink gaps are 52.07 / 54.04 versus
57.5 / 59.77, and its cell->body span is 238.58 u versus the ref-6-derived 244.8 u. The measured gaps
are adopted for the label strip only; the Wi-Fi constants are unchanged.

The shipped constants:

| Quantity | Value | Source |
|---|---:|---|
| Cap height | 77.69 | measured ref-1 5 cap 32.93 px x 2.360 (0.777 of the body, 0.814 of the tallest bar) |
| Stroke | 11.10 | measured ref-1 label weight, 0.143 cap |
| Cap-box top | 14.95 | measured ref-1 cap top; baseline 92.64 (top-anchored) |
| L / T / E / G width | 47.48 / 62.58 / 51.79 / 73.37 | measured SF 0.611 / 0.806 / 0.667 / 0.944 x cap |
| + width | 58.27 | 0.750 x cap square (measured SF height; SF width 0.778) |
| Letter side bearing | 3.11 | 0.04 x cap (measured T..E 0.083 cap gap) |
| Plus side bearing | 4.66 | 0.06 x cap (G..+ gap 0.10 cap, closer to the measured 0.167) |
| Cap / tallest bar | 0.814 | 77.69 / 95.5 |
| Cap / Wi-Fi ink | 0.820 | 77.69 / 94.77 |
| Artboard | dynamic, `ink width + 2 x 1` | the group ink starts at the 1-unit margin; height stays 96.77 |
| Labels | LTE, 4G, 5G, 5G+, 3G, E | reader/reporting order |
| Digits 3/4/5 | `IosDigits` widths, advances and even-odd contours x (77.69 / 57) | fixed bold battery glyphs (0.20 cap); never retyped or thinned |
| Vertical placement | cap top = 14.95, top-anchored | measured ref 1 |
| Horizontal placement | ink group starts at the 1-unit margin | group layout `sum(advances) - firstBearing - lastBearing` |

**Known digit-weight limitation.** Ref 1's label is a 0.143-cap weight, but the 3/4/5 are the fixed
bold `IosDigits` battery glyphs (0.20 cap). Adopting the measured 11.1 stroke lightens every letter,
the plus and the G, while the digit stays bold: our "5" is therefore heavier than ref 1's. The digit
widths, advances and `DIGIT_SCALE = cap / 57` are unchanged.

SF shape/width reference (cap 36 px, flat letters L/T/E/4):

| Reference quantity | Measured | / cap (36) |
|---|---:|---:|
| L ink width | 22 px | 0.611 |
| T ink width | 29 px | 0.806 |
| E ink width | 24 px | 0.667 |
| G ink width | 34 px (33 at 50 % of peak) | 0.944 (0.917) |
| + ink width x height | 28 x 27 px | 0.778 x 0.750 |
| Digit 3 ink width | 25 px | 0.694 |
| Digit 4 ink width | 27 px | 0.750 |
| Digit 5 ink width | 24 px | 0.667 |
| G bar y (G ink box y27..64) | 44..48 px | 0.447..0.573 of the 38 px G box |
| G bar left | x204 (G box 184..216) | 0.625 raw, 0.607 after ink-box normalisation |
| G upper-right terminal | tip ~(214, 34) | ~0.19 of the G box, ~319 deg |
| G right stem bottom | y60 left / y59 right | 0.975 / 0.920 of the G box |
| G counter/bar aperture | ~36 deg visible above the bar | band gap 318.93..20.66 deg |
| Gaps T..E / 4..G / 5..G / G..+ | 3 / 4 / 5 / 6 px | 0.083 / 0.111 / 0.139 / 0.167 |
| Gaps 3..4 / 4..5 | 4 / 5 px | 0.111 / 0.139 |

**Filled outlines, no strokes/font.** Every letter and the plus are one or more closed contours,
consistently clockwise, filled with `Path.FillType.WINDING` so overlapping shapes union without alpha
seams. L is a stem+foot polygon; T a top-bar+stem polygon; E four overlapping rectangles (stem +
top/middle/bottom arms, middle arm 0.95 of full width). The plus is two equal-arm rectangles forming a
0.750-cap square centred at C/2.

**G construction (v3, measured).** The owner-review defect was that the R4B G read as a C with a
G-style corner glued on. It was a symmetric `W/2, C/2` oval band with a short stem; the v3 G is rebuilt
analytically from the 50 % mask of the three Gs in the reference strip (the raw measurement script and
output are `.pm/ref/out-r4/g-v3/measure_g.py` / `measure_g.json`). Reference G ink box: 33 x 38 px
(cap 36, so the round bowl overshoots 1 px top and bottom); direct left-band spans are 5-6 px (the
distance-transform median x2 is 4.0 px, a lower bound).

The shipped shape is a union of three clockwise contours (WINDING):

- **Bowl band:** outer centre `(0.61188 W, 0.5 C)` with radii `(0.61188 W, 0.5 C)`; weight-1 inner
  radii `(0.43272 W, 0.37898 C)`. The band sweeps counter-clockwise from `318.932 deg` (the measured
  upper-right terminal, tip ~(214, 34)) through the top, left and bottom to `20.661 deg` at the lower
  right. The gap crossing 3 o'clock is the open upper aperture; the visible gap above the bar is
  ~36 deg. Outer x is clamped to the ink-box right edge because the measured bowl ellipse continues
  under the stem.
- **Bar:** centre y `0.5102 C`, half thickness `0.06288 C` at weight 1, inner end `0.60653 W`, right
  edge `W`. This is the measured mid-height bar reaching into the counter (reference rows y44..48).
- **Stem:** right edge `W`, left edge `0.84951 W` at weight 1 (the measured x212), from the bar down
  to a slanted bottom `0.97497 C` on the left and `0.92007 C` on the right (the measured y60/y59
  stem-to-bowl joint). This is the visible right stem the old shape lacked.

`G_REFERENCE_STEM_STROKE = (1 - 0.84951) x 34/36 x cap = 11.04` design units. Production scales every
G stroke by `G_PRODUCTION_WEIGHT = STROKE / 11.04 = 1.006`, so the stem is exactly the measured label
`STROKE` (11.1) and the band/bar keep the measured proportions. The same builder is exposed at weight 1 for
the weight-normalised IoU evidence. The counter opens through the upper-right aperture (0 closed
holes: the `IosDataLabelTest` flood-fill requires no enclosed background), and the right stem is
inked below the bar, so the glyph is distinguishable from a C.

**Per-glyph IoU (v3).** Reference: the 50 % mask of the same strip. Alignment: ink bbox + a +/-0.6
reference-pixel translation search. L/T/E/+/G are rebuilt by the shipping code at each glyph's measured
reference stroke (L 5.8 px, T/E/+ 5.0 px, G weight 1); the digits are the fixed bold `IosDigits` and
the reference is dilated to that bold weight. The Android renderer writes the masks
(`r4-glyph-*-refweight-v3.png`); `compare_v3.py` computes the table and `overlay-g.png`. A lightly
smoothed trace of the same 50 % boundary reaches 0.945, so ~0.91 is the resolution-limited amplitude of
the reference, not a geometry failure.

| Glyph | IoU | Weight normalisation |
|---|---:|---|
| L | 0.939 | rebuilt at 5.8 px |
| T | 0.920 | rebuilt at 5.0 px |
| E | 0.914 | rebuilt at 5.0 px |
| **G** | **0.908** | rebuilt at weight 1 (measured stem) |
| + | 0.902 | rebuilt at 5.0 px |
| 3 | 0.723 | IosDigits bold; reference dilated to bold |
| 4 | 0.788 | IosDigits bold; reference dilated to bold |
| 5 | 0.735 | IosDigits bold; reference dilated to bold |

Digits are reported, not reworked: they are the measured battery digits (`IosDigits`, never retyped)
and their widths already match the reference within 4 %; their lower IoU is the bold/thin weight and
the 5 px reference stroke, not a shape fix in this change. The earlier R4B stroked arc (which started at
300 deg) with a centre-to-edge bar read as "6" in owner review; the v3 filled band + measured bar leaves
most of the counter open.

**Spacing and the plus.** The letter side bearing is 0.04 C, which reproduces the measured 0.083 C
T..E gap. The plus uses the measured 0.750 C height as an equal-arm square with a 0.06 C side bearing,
so the G..+ gap is 0.10 C (closer to the measured 0.167 C than the earlier 0.02 C). Honouring the
reference plus makes "5G+" wider than the production slot's height-limited width: at the 80x50 Wi-Fi
slot (4x supersampled) "5G+" is **width-limited** at an artboard of ~200.9, a rendered cap of
~19.4 px (about 0.63 of the Wi-Fi ink). At the measured cap **LTE is width-limited too** (L+T+E ink =
174.28 u, artboard ~176.3, rendered cap ~22.1 px / 0.72 of the Wi-Fi ink), while 4G/5G/3G/E stay
height-limited at ~25.1 px (0.820 of the Wi-Fi ink). This is the slot constraint, not a geometry bug;
the alternative is a smaller cap or plus
(~0.49-0.51 C), closer to the constant cap but deviating from the reference. The runtime renderer is
`IosGlyphRenderer.drawDataLabel` (Canvas).

`rive/ios/scene.rml` and `app/src/main/res/raw/ios.riv` are **untouched by R5B**: no alpha represented
in the scene changes (the measured 0.4975/0.5021 confirm the authored 0.50, and the label is
Canvas-only), the `rive` CLI is absent on this machine, and editing the scene without rebuilding would
break the hash gate. The shipped hash is still
`0a46bfb42aa61d1157b2f8d10b633744bf1215aa72a25cb6b29dd04dec57a9fe` (40,985 bytes). V2/R4B evidence
fixtures are still written by `IosDataLabelRasterTest`
(`r4-label-{lte,4g,5g,5g-plus,3g,e}-{light,dark}-v2.png`, `r4-label-family-{light,dark}-v2.png`,
`measurements-v2.md`); the v3 G evidence adds `r4-glyph-*-refweight-v3.png`,
`r4-glyph-g-bold-v3.png`, `r4-label-{4g,5g,5g-plus,3g}-{light,dark}-v3.png` and the v3 family row.
R5B adds the end-to-end strip fixture `r5b-ours-strip-dark-4x.png` (written by `IosR5LabelStripTest`
when `IOS_RENDER_OUT` is set) and the side-by-side `r5b-compare-ref1-vs-ours.png`.

**UNVERIFIED / known limits.** The cap, top, baseline, stroke and label gaps are measured from ref 1
(±1.18 u); the G v3 shape and the per-glyph IoU above remain measured against the SF strip. The
vertical placement and the on-device size still need owner/device review. The 3/4/5 digits stay the
fixed bold `IosDigits` glyphs (0.20 cap), so our "5" is bolder than ref 1's 0.143-cap label; the G ink
fraction (0.944 shipped vs 0.874 measured here) and the shorter cell bars are outside this change.
Honouring the measured plus makes "5G+" width-limited at the slot (a slot constraint, not a geometry
bug). Owner/device review is required before release.

**R5M runtime shift (device visual still UNVERIFIED).** The label record stays anchored to the data
SIM's `mobile_type`, but when the stock type box overlaps the cell leaf the host translates only its
own overlay canvas into the free cell->battery gap (`labelShiftPx`; >=28 px gap, zero shift when the
boxes do not overlap). No stock view is resized or re-laid out; `mobile_type` keeps its size and
visibility.

## Relative sizes and per-kind draw scale

All ratios use the battery body height = 1.0, measured from ref 6 (clock digit cap 30 px union,
28-29 flat = 96.8 units). The clock column below is the adopted constant x 1.033.

| Element | Ratio to battery body | Ratio to ref 6 clock cap |
|---|---:|---:|
| Clock digit cap | 0.968 (30 px / 31 px) | 1.000 |
| Battery body | 1.000 | 1.033 |
| Wi-Fi H | 0.944 | 0.975 |
| Wi-Fi W | 1.256 | 1.297 |
| Single block H | 0.955 | 0.987 |
| Dual block H | 1.042 | 1.077 |
| Cap H (adopted 26.0) | 0.260 | 0.269 |
| Cap W (adopted 9.5) | 0.095 | 0.098 |
| Cap gap | 0.075 | 0.077 |

The adopted constants use the 1.00 clock/battery simplification, so the clock column is the constant
x 1.033; the direct ref-6 measurements (Wi-Fi W 1.300 / H 0.967, single H 0.967, dual H 1.077, cap H
0.281 light / 0.254 dual) are within the +/-3 % clock uncertainty.

The ref 7 (owner phone) before/target calibration. **R4 = R3 x1.10**: the R4 defaults are the R3
calibration multiplied by 1.10, and a 100 % user scale stays relative to 100, so it equals the new
default (the identity user transform still draws the new default).

| Kind | R4 draw scale | R3 -> R4 | Ref-7 before (clock = 1.0) | R3 target | R4 target (x1.10) | Measured production (clock = 1.0) | Confidence |
|---|---:|---|---:|---:|---:|---|---|
| Battery | 0.891 | 0.81 -> 0.891 | body 37 px / 1.233x | 1.000 | 1.100 | 1.100 (33.0 / 30, box 71x34) | high (direct bbox) |
| Wi-Fi | 0.627 | 0.57 -> 0.627 | bbox 50 px / 1.667x | 0.944 | 1.0384 | 1.025 (30.75 / 30, box 50x31) | high (direct bbox) |
| Cell single | 0.825 | 0.75 -> 0.825 | 1.316x on ref 7 (1.267x after the 95.5-unit bar 4) | 0.955 | 1.0505 | 1.0583 (31.75 / 30, box 66x35) | medium (inferred; device showed the side-by-side bug) |
| Cell dual | 0.836 | 0.76 -> 0.836 | 1.377x | 1.042 | 1.1462 | 1.1417 (34.25 / 30, box 67x35) | medium (stacked block inferred, not observed) |

The R4 target is the R3 target x1.10, not a measured device value. The measured production column is
the rendered ink height / 30 px clock through the production path (full stock slot +
`relativeSize = true`, 4x supersampled) for the ref-7 slot; it is a unit render, not a device
capture. Integer rounding therefore moves Wi-Fi from the 1.0384 target to the measured production
ratio 1.025 (30.75 / 30): the 1x integer `sizeFor` box is 50x31, while the 4x production render box is
50.25x31.25. The battery stays at 1.100; the +/-3 % band in `IosRelativeSizeTest` is the
integer-rounding tolerance. The 1.10 growth itself is **UNVERIFIED**: it has not been measured on a
device yet; the old ref-7 calibration is the only measured baseline.

`IosGeometry.Relative.sizeFor(kind, slotWidth, slotHeight, dualCell)` is exposed for the host
integration (R2A/R2C, outside R2B). R2B does not wire it, so the current drawable still fills its
stock bounds unless the host passes `relativeSize`; the "measured production" pixels above are the
unit-render result at the `sizeFor` boxes, not device pixels. The renderer keeps its
`min(w/W, h/H)` CONTAIN + centred placement, so an extreme slot letterboxes instead of squashing;
`IosDrawablesTest` pins that with the 200x40 and 40x200 slots. `IosRelativeSizeTest` pins the
production integer-box ink heights (battery 1.100, Wi-Fi 1.025, single 1.0583, dual 1.1417 of the
30 px clock) and keeps the +/-3 % comparison against the R4 targets. The scales are data only:
neither the renderer nor the drawable applies a base scale; the host applies `sizeFor` when it wires
the inset.

The ref 6 provenance: the original upload (`6-25b39679.jpg`) was overwritten on 2026-11-21 22:43 by a
green-grid probe (1072x880, 73 428 B). The original was recovered from the upload cache
(`ref6-recovered-original.jpg`, 158 105 B, 1239x726, sha256 `3563a34b…bb50b0`); every surviving ref-6
crop is an exact crop of it (template MSE 0.37-1.03). The evidence is in `.pm/ref/out-r2/`.

## Strip composition (in-app preview and R5B evidence)

All gaps are **ink-to-ink**: every artboard carries a 1-unit margin and `Battery.BODY_LEFT = 1`.

The two consumers are separate, and neither is used at runtime:

- The **Wi-Fi gaps** (`CELL_WIFI_GAP` 57.5 / `WIFI_BATTERY_GAP` 59.77) drive the in-app
  `IosPreview` composition. `IosPreview.kt` reads only those two constants.
- The **label gaps** (`CELL_LABEL_GAP` 52.07 / `LABEL_BATTERY_GAP` 54.04) drive the R5B 5G evidence
  strip (`labelBatteryX("5G")`, `LABEL_BATTERY_X_5G`, `LABEL_WIDTH_5G`, used by
  `IosR5LabelStripTest`). The in-app preview does not use them.
- The **runtime** does not use `Strip` at all: each glyph is drawn into its stock icon slot with
  `Fit.CONTAIN`; the host-applied `Relative.sizeFor` inset is not wired in R2B.

| Gap | Value | Source |
|---|---:|---|
| Cell -> Wi-Fi (Wi-Fi strip, preview) | 57.5 | 51.14 light px (ref 6) |
| Wi-Fi -> battery (Wi-Fi strip, preview) | 59.77 | 53.15 light px (ref 6) |
| Cell -> label (label strip, R5B evidence) | 52.07 | ref 1: 22.06 px x 2.360 |
| Label -> battery (label strip, R5B evidence) | 54.04 | ref 1: 22.90 px x 2.360 |
| Wi-Fi strip width | 602.4 | 148.1 + 57.5 + 127.53 + 59.77 + 209.5 (smaller cap) |
| 5G label strip width | ~596.55 | `LABEL_WIDTH_5G` = `LABEL_BATTERY_X_5G + 192.5 + 7.5 + 9.5` |
| Top alignment | battery, Wi-Fi, label and single cell share y=0 | measured |
| Dual cell top | -4.2 relative to battery | measured 3 px above at dark scale |

The label artboard origins are derived from the ink gaps:

```
LABEL_X = Cell.BLOCK_W + CELL_LABEL_GAP

fun labelBatteryX(label: String): Float {
    val artboard = requireNotNull(DataLabel.artboardWidth(label))  // reject unsupported names
    return LABEL_X + artboard - Wifi.ARTBOARD_MARGIN + LABEL_BATTERY_GAP - Battery.BODY_LEFT
}

LABEL_BATTERY_X_5G = labelBatteryX("5G")
LABEL_WIDTH_5G = LABEL_BATTERY_X_5G + Battery.BODY_W + Battery.CAP_GAP + Battery.CAP_W
```

so the label ink starts at `LABEL_X + 1`, the battery body at `labelBatteryX(label) + 1`, and the
drawn gaps equal the constants (`IosGeometryTest` pins this, including the rejection of an
unsupported label name). `Strip.WIFI_X`, `BATTERY_X` and `WIDTH` keep their Wi-Fi semantics and are
unchanged by R5B.

## Rive build and verification (R3D)

The scene is built offline with **Rive 1.8.0** (`rive rive/ios --once`). Rive **1.9.2** is used only to
verify (`rive . --verify`), including `tools/check-rive-shipped.sh`. The brief's 1.9.2 build path is not
possible here: 1.9.2 removed `--once`, and `--publish` needs a login, so it cannot write an unsigned
`.riv` offline. Building the same scene twice with 1.8.0 is byte-identical, and the committed
`app/src/main/res/raw/ios.riv` is that build (**40,985 bytes**, sha256
`0a46bfb42aa61d1157b2f8d10b633744bf1215aa72a25cb6b29dd04dec57a9fe`). The `IosDigits` artboard now
carries the v6 filled contours (one closed `PointsPath` per contour, one `Fill fillRule="evenOdd"` per
digit; all ten built from ref 4/6 with the uniform bold construction); the runtime `IosDigits` and the
scene are pinned together by `IosGeometryParityTest`. If the installed CLI's cached minimum version
blocks 1.8.0, run it with an isolated `RIVE_HOME` whose `update-check` minimum is 1.8.0; the build
itself is offline.

**R5B does not rebuild the scene.** The measured battery remainder/cap alphas (0.4975 / 0.5021)
confirm the authored 0.50, the label lives on the Canvas path, and `Cell.INACTIVE_ALPHA` is unchanged
and still UNVERIFIED. Because the `rive` CLI is absent on this machine, editing `rive/ios/scene.rml`
without rebuilding would make `ios.riv` stale and break the hash gate. The scene and
`app/src/main/res/raw/ios.riv` are therefore untouched, the hash stays
`0a46bfb42aa61d1157b2f8d10b633744bf1215aa72a25cb6b29dd04dec57a9fe`, and there is no scene-parity
drift.

## UNVERIFIED / caveats

- **Cap at 100 %:** the light reference draws the cap at full neutral ink (alpha 1.0). The owner rule
  (and every partial-charge reference, 0.49-0.57) is 0.50; the 100 % case is runtime-only and is not
  adopted by the scene.
- **Bolt outline clipping:** the shipped 3.0-unit outline keeps the stroked 96-unit bolt inside the
  102-high battery artboard (extent 0..101.6, guard-tested). Whether the host view bounds clip the
  0/101.6 edges cleanly on a real device is UNVERIFIED.
- **Percentage digits:** v6 (R3D.1) rebuilds all ten from the regular reference (ref 4 for 1-9, ref 6
  for 0) with the same uniform dilation + cap rescale, tuned to the measured bold run-stroke 11.40.
  The primary shape gate is the **inverse weight-normalised** IoU against the unmodified ref
  (0.974-0.989 at 32 px per unit, 0.977-0.992 at 16; every digit >= 0.90; untraced-construction
  control 0.980-0.997).
  The legacy equal-area-erosion IoU (0.657-0.844) is not weight-invariant; its control is 0.657-0.849,
  so it is a normalisation artifact, not a closed-counter effect. The cap row traces to 56.94 rather
  than exactly 57.0 (1/16 = 0.0625 units), a raster artefact, not a short glyph.
- **Cell draw scales:** the ref 7 phone rendered the 4 bars + 4 dots side-by-side (the known dual-SIM
  bug), so the single and stacked-dual blocks are inferred from the measured drawable px/unit. Medium
  confidence; the battery and Wi-Fi scales are direct bbox measurements.
- **Clock metric:** the union of the digits is 30 px but flat digits measure 28-29 px, a ±1 px (±3 %)
  uncertainty. The target ratios carry that tolerance.
- **Data-type label (R5B):** the cap (77.69), top (14.95), baseline (92.64), stroke (11.1) and label
  gaps (52.07 / 54.04) are measured from ref 1 with ±1.18 u uncertainty. The 3/4/5 remain the fixed
  bold `IosDigits` glyphs (0.20 cap), so our "5" is bolder than ref 1's 0.143-cap label — a known
  mismatch, not a geometry bug. `Cell.INACTIVE_ALPHA` stays 0.25 **UNVERIFIED** (ref 1 has all bars
  and dots active; ref 2 is only weakly consistent at ~0.36-0.37). The battery cap W/H measure
  13.7 / 34.3 versus the frozen 9.5 / 26.0 (low confidence, unchanged). The G ink fraction is 0.874
  measured vs 0.944 shipped, and the shorter cell bars measure 35.7/43.4/53.8/63.9 vs
  30.3/37.5/49.9/62.7; both are outside this change and unchanged. Evidence:
  `.pm/ref/out-r5/r5b-measurements.md` / `.json`, `r5b-ref1-statusbar.png`,
  `r5b-ours-strip-dark-4x.png`, `r5b-compare-ref1-vs-ours.png`.
- **Cap height variant:** the single-SIM promo measures 28.1 units and the dual refs 23.8-25.6; the
  recommended 26.0 splits them. Which is the iOS 27 final is unconfirmed.
- **Row artboards:** the per-row runtime viewports are derived from the same design geometry; the stock
  slot heights per ROM are inferred from the glyph pixels (CONTAIN assumption), not read from a view.
