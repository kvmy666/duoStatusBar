# iOS glyph geometry — the one source of truth

`hook/IosGeometry.kt` holds these numbers; `rive/ios/scene.rml` repeats them. The battery body is
`H = 100` design units. The reference captures are kept outside the repository; this document is the
frozen record of the numbers, thresholds and ±px.

The real-iPhone references are ref 6 (single-SIM), ref 5 (dual-SIM) and ref 1 (owner target); ref 7 is
the owner device running our current glyphs. Every number below was printed by `measure-reference.py`
and is reproducible from `.pm/ref/out-r2/measurements.md` / `measurements.json`.

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
| Cap colour / alpha | batteryFillColor at 0.50, same as the remainder (not the tint) | refs 1/5/6 partial charge (0.49-0.57); owner rule 0.50 |
| Uncharged remainder | fill colour at 0.50 alpha | measured 0.496-0.537 |
| Fill | left `level * 192.5`, clipped by the body | light solid 1.00; dark split 58.4 % |
| Bolt | 30 x 55, 7-point path | design invention (no reference image) |

The cap is the inactive grey, not the dynamic tint. At 100 % the light reference draws the cap at full
ink; that one case is **UNVERIFIED** and is not adopted (see the caveats).

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

The ref 7 (owner phone) before/target calibration:

| Kind | Draw scale | Before -> target (clock = 1.0) | Confidence |
|---|---:|---|---|
| Battery | 0.81 | body 37 px / 1.233x -> 30 px / 1.000x | high (direct bbox) |
| Wi-Fi | 0.57 | bbox 50 px / 1.667x -> 28.3 px / 0.944x | high (direct bbox) |
| Cell single | 0.75 | 1.316x on ref 7 (1.267x after the 95.5-unit bar 4) -> 0.955x | medium (inferred; device showed the side-by-side bug) |
| Cell dual | 0.76 | 1.377x -> 1.042x | medium (stacked block inferred, not observed) |

The "target" column is not device pixels yet: it is what `Relative.sizeFor` produces once the host
integration applies it.

`IosGeometry.Relative.sizeFor(kind, slotWidth, slotHeight, dualCell)` is exposed for the host
integration (R2A/R2C, outside R2B). R2B does not wire it, so the current drawable still fills its
stock bounds and the "target" pixels in the ref-7 table are targets, not device pixels yet. The
renderer keeps its `min(w/W, h/H)` CONTAIN + centred placement, so an extreme slot letterboxes
instead of squashing; `IosDrawablesTest` pins that with the 200x40 and 40x200 slots.
`IosRelativeSizeTest` proves the rendered ink at those `sizeFor` boxes: battery 1.000, Wi-Fi 0.950,
single 0.967, dual 1.042 of the 30 px clock. The scales are data only: neither the renderer nor the
drawable applies a base scale; the host applies `sizeFor` when it wires the inset.

The ref 6 provenance: the original upload (`6-25b39679.jpg`) was overwritten on 2026-11-21 22:43 by a
green-grid probe (1072x880, 73 428 B). The original was recovered from the upload cache
(`ref6-recovered-original.jpg`, 158 105 B, 1239x726, sha256 `3563a34b…bb50b0`); every surviving ref-6
crop is an exact crop of it (template MSE 0.37-1.03). The evidence is in `.pm/ref/out-r2/`.

## Strip composition (preview and evidence)

| Gap | Value | Source |
|---|---:|---|
| Cell -> Wi-Fi | 57.5 | 51.14 light px |
| Wi-Fi -> battery | 59.77 | 53.15 light px |
| Full strip width | 602.4 | 148.1 + 57.5 + 127.53 + 59.77 + 209.5 (smaller cap) |
| Top alignment | battery, Wi-Fi, single cell share y=0 | measured |
| Dual cell top | -4.2 relative to battery | measured 3 px above at dark scale |

The runtime still gives each glyph the stock icon's slot and uses `Fit.CONTAIN`; the strip constants
are for the preview and the PNG evidence at the reference scale. The host-applied
`Relative.sizeFor` inset is not wired in R2B.

## UNVERIFIED / caveats

- **Cap at 100 %:** the light reference draws the cap at full ink (alpha 1.0). The owner rule (and every
  partial-charge reference, 0.49-0.57) is 0.50; the 100 % case is recorded as an exception, not averaged.
- **Cell draw scales:** the ref 7 phone rendered the 4 bars + 4 dots side-by-side (the known dual-SIM
  bug), so the single and stacked-dual blocks are inferred from the measured drawable px/unit. Medium
  confidence; the battery and Wi-Fi scales are direct bbox measurements.
- **Clock metric:** the union of the digits is 30 px but flat digits measure 28-29 px, a ±1 px (±3 %)
  uncertainty. The target ratios carry that tolerance.
- **Cap height variant:** the single-SIM promo measures 28.1 units and the dual refs 23.8-25.6; the
  recommended 26.0 splits them. Which is the iOS 27 final is unconfirmed.
- **Row artboards:** the per-row runtime viewports are derived from the same design geometry; the stock
  slot heights per ROM are inferred from the glyph pixels (CONTAIN assumption), not read from a view.
