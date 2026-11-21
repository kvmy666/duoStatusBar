# iOS glyph geometry — the one source of truth

`hook/IosGeometry.kt` holds these numbers; `rive/ios/scene.rml` repeats them. The battery body is
`H = 100` design units. The reference captures are kept outside the repository; this document is the
frozen record of the numbers, thresholds and ±px.

The light image is the trustworthy source for proportions. The dark image is 0.798x its scale and is
used only for the dual-SIM layout, the 25% inactive alpha and the battery split.

**Step 3 polish target:** the cap and single-bar sizes below follow the owner's annotated reference
capture, measured in the measured polish tables (kept outside the repository). Its cap is 55% wider /
26% taller than the light PNG
(15.2 x 36.8 vs 9.8 x 28.8); that size difference is annotated-only and recorded as **UNVERIFIED**,
not averaged with the light reference.

## Battery (artboard `Ios`, 217.2 x 102)

| Quantity | Value | Source |
|---|---:|---|
| Body width / height | 192.5 / 100 | 1.927 H (light) |
| Body corner radius | 33.0 | 0.328 H top / 0.334 H dark |
| Cap width / height | 15.2 / 36.8 | owner-annotated 19.62 x 47.64 px at s=1.2930 (light 9.8 x 28.8 is the smaller variant, UNVERIFIED) |
| Cap gap | 7.5 | annotated 9.73 px = 7.53 units |
| Cap lift above body centre | 0.0 | annotated cap centre delta -0.07 units |
| Cap shape | right-hand half-superellipse n=1.8, 25-gon, flat base on the body side | annotated rounded triangular/trapezoid; annotated IoU 0.971 vs 0.789 for the old 24-gon; light shape IoU 0.987 |
| Cap IoU | 0.987 light / 0.971 annotated | shape confirmed on the light PNG; the annotated size is the step-3 target |
| Uncharged remainder | fill colour at 0.50 alpha | dark RGB 128 vs 246 |
| Fill | left `level * 192.5`, clipped by the body | light solid 1.00; dark split 58.6% |
| Bolt | 30 x 55, 7-point path | design invention (no reference image) |

## Cellular (artboard `IosCell`, 150.1 x 105.6)

| Quantity | Value | Source |
|---|---:|---|
| Column pitch | 41.0 | 0.410 H light / 0.409 H dark |
| Single bar width / heights | 25.1 / 48.0, 63.4, 81.2, 99.0 | 0.251 H, 0.480–0.990 H owner-annotated (light PNG's 0.364–0.948 is a shorter variant, UNVERIFIED) |
| Dual bar width / heights | 25.3 / 30.3, 37.5, 49.9, 62.7 | 0.253 H, 0.303–0.627 H |
| Bar corner radius | width / 2 | capsules |
| Dot width x height | 26.0 x 27.4 | 0.253 x 0.274 H (measured 18.0 x 19.5 px at dark) |
| Bar row -> dot row gap | 13.5 | 0.135 H |
| Inactive alpha | 0.25 | measured 0.243 |
| Block width / dual height | 148.1 / 103.6 | 3*pitch + width; 62.7 + 13.5 + 27.4 |

Single mode draws the four tall bars inside the same artboard, bottom-aligned at y=100 (design units,
1-unit margin included); the dot row is hidden.

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

## Strip composition (preview and evidence)

| Gap | Value | Source |
|---|---:|---|
| Cell -> Wi-Fi | 57.5 | 51.14 light px |
| Wi-Fi -> battery | 59.77 | 53.15 light px |
| Full strip width | 608.1 | 148.1 + 57.5 + 127.53 + 59.77 + 215.2 |
| Top alignment | battery, Wi-Fi, single cell share y=0 | measured |
| Dual cell top | -4.2 relative to battery | measured 3 px above at dark scale |

The runtime still gives each glyph the stock icon's slot and uses `Fit.CONTAIN`; the strip constants are
for the preview and the PNG evidence at the reference scale.
