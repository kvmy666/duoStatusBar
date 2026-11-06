# Liquid Glass in Duo Status Bar

**Technical reference — app UI only**

| Item | Value |
|---|---|
| Product state | `1.8.0-alpha.8`, versionCode 97 |
| Scope | The Android app shell (`GlassBackdrop`, `GlassSurface`, `GlassControls`, `DuoShell`) |
| Not in scope | The SystemUI module. Its status-bar element stays on the Canvas/Rive path and never links Backdrop (`GlassSurface.kt:118-126`). |
| Dependency | `io.github.kyant0:backdrop:1.0.1` |
| SDK floor | `minSdk = 33`, `targetSdk = 36`, `compileSdk = 36` (`app/build.gradle.kts:37-48`) |
| Document date | 2026-11-06 |

This document describes the glass as it is built, not a proposed redesign. The measured ColorDuo numbers are
factual literals obtained from a read-only APK analysis (`agent/archive/originals/AGILE-1.7-COLORDUO.md`); no ColorDuo source,
shader text or drawable asset is copied into Duo. The implementation in this repository is independent.

**Naming traps before you read further**

- `LensNavigationBar(pageBackdrop = ...)` receives `barBackdrop`, which is already
  `rememberCombinedBackdrop(wallpaper, page)`, not the `page` layer alone (`DuoShell.kt:98-116`,
  `GlassSurface.kt:134-159`).
- `tabsBackdrop` in alpha.8 records the pill material only. It no longer records the tab row
  (`DuoShell.kt:288-291`).
- `rowBackdrop` records the tab row and is used only by the moving selector
  (`DuoShell.kt:165-168`, `449`).
- "Frosted" in this code means a translucent color overlay in `onDrawSurface`, not a blur.

---

## 1. What Kyant0 Backdrop is

### 1.1 The library in one paragraph

`io.github.kyant0:backdrop` is a Compose library for **backdrop sampling**. A composable subtree is recorded
into a layer; a `Modifier.drawBackdrop` surface then samples that recording inside a shape and applies an
ordered GPU effect chain (color controls, blur, lens/refraction, optional vibrancy), then draws a surface
tint, a directional highlight and an optional shadow. It is the rendering primitive behind Duo's
"liquid glass" app shell.

"Liquid Glass" is Apple's material language. Kyant Backdrop is an independent library that provides similar
building blocks. Duo uses the library API; it does not use Apple code or assets. The ColorDuo application was
measured only to calibrate the visual tokens that a competing implementation exposes.

### 1.2 Exact dependency and build constraints

```kotlin
// gradle/libs.versions.toml
backdrop = "1.0.1"
backdrop = { module = "io.github.kyant0:backdrop", version.ref = "backdrop" }

// app/build.gradle.kts
implementation(libs.backdrop)
```

| Constraint | Value | Why it matters |
|---|---|---|
| Backdrop | 1.0.1, AAR | The API used here (`drawBackdrop`, `rememberCombinedBackdrop`, `Highlight`) is the 1.0.1 surface. |
| Kotlin | 2.2.21 | 1.0.1 was resolved against Kotlin 2.2.21. |
| Compose BOM | `2025.10.01` | The library raises Compose UI/foundation to 1.9.5. |
| JVM target | 17 | AGP/Compose requirement. |
| `minSdk` | 33 | Runtime shader/lens path; the app ships no API 31/32 path. |
| `targetSdk` / `compileSdk` | 36 | Current platform target. |

The Phase 5 dependency audit rejected `2.0.1`: it is a Compose Multiplatform JAR requiring Kotlin 2.4.10 and
CMP 1.12, which is a different stack. Do not "upgrade" the coordinate without re-testing the effect order and
the Highlight constructor (`agent/archive/originals/PHASE-5-PLAN.md:15-29`).

### 1.3 API surface actually used

| API | Where used | Contract |
|---|---|---|
| `Backdrop` | `GlassBackdrop` callbacks, `LensNavigationBar` parameter, `GlassSurface` parameter | A sample source. It can be a recorded layer or a combination of layers. |
| `LayerBackdrop` | `rememberLayerBackdrop()` results, `LocalGlassBackdrop` | A concrete recording of a composable subtree. |
| `rememberLayerBackdrop()` | `wallpaper`, `page`, `tabsBackdrop`, `rowBackdrop` | Creates a recording. It must be attached to a composable with `Modifier.layerBackdrop`. |
| `Modifier.layerBackdrop(layer)` | Wallpaper box, page box, pill box, row | Records the subtree into that layer. |
| `rememberCombinedBackdrop(a, b)` | `barBackdrop`, `selectorBackdrop`, `selectorMovingBackdrop` | Combines recordings in argument order into one sample source. |
| `Modifier.drawBackdrop(...)` | `GlassSurface`, selector, switch track, slider thumb, scroll edges | Samples the backdrop inside `shape` and runs the effect chain. |
| `effects.colorControls(brightness, contrast, saturation)` | Pill, selector, controls, scroll edge | Color filter applied before blur. |
| `effects.blur(radiusPx)` | Pill, controls, scroll edge | Blur radius in **pixels**; convert with `Dp.toPx()`. |
| `effects.lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)` | Pill, selector, controls | Refraction shader. All distance values are pixels. |
| `effects.vibrancy()` | Unused in alpha.8; Duo uses `colorControls` explicitly | Convenience saturation. It was used in earlier selector iterations. |
| `Highlight(width, blurRadius, alpha, style)` | Selector, `GlassSurface` default | Directional rim. Sizes are `Dp`. |
| `HighlightStyle.Default(intensity, angleDegrees, falloff)` | Selector | Measured style is 0.5 white, 45°, falloff 1.0. |
| `Shadow` / `Shadow.Default` | `GlassSurface` default, never the selector | Default shadow is 24 dp; pass `null` when not wanted. |
| `onDrawSurface` | All glass surfaces | Draws the tint/frost/inner edge on top of the sampled, filtered backdrop. |
| `layerBlock` | `GlassSurface` press scale | Transforms the layer without scaling the sampled backdrop. |

### 1.4 What it is not

- It is not a system-wide blur or a window overlay. It only sees content recorded by the app process.
- It does not read the device wallpaper. Duo's `Wallpaper()` composes its own dark gradient and two wine
  blooms (`GlassSurface.kt:163-180`).
- It is not used by the SystemUI module. The status-bar element has a different renderer and a different
  failure domain.
- It is not free. Each live surface records and samples content; the card fallback and emulator quality tier
  exist because of measured frame-time failures (`GlassSurface.kt:95-113, 268-292`).

---

## 2. The rendering model

### 2.1 Three recordings, one acyclic graph

`GlassBackdrop` deliberately records two layers and combines them for the floating bar
(`GlassSurface.kt:128-161`):

```
wallpaper layer     static gradient + two wine blooms
page layer          page content, cards, scrolling text

barBackdrop = rememberCombinedBackdrop(wallpaper, page)
```

- `content` receives `wallpaper`, not the combined layer. Cards and controls inside the page therefore sample
  the static wallpaper and never the layer that contains themselves. This is the structural fix for the
  RC-017 recursive-capture crash (`GlassSurface.kt:117-126`, `agent/archive/originals/PHASE-5-REPORT.md` C-01).
- `floatingBar` and `scrollEdge` receive `barBackdrop`. The bar can refract the page content; the scroll edge
  can blur the page under the top/bottom edge.
- The nav adds two more recordings (`DuoShell.kt:165-168`):

```
tabsBackdrop              pill material + press glow (not the tab row)
rowBackdrop               tab icons and labels

selectorBackdrop          = rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)
selectorMovingBackdrop    = rememberCombinedBackdrop(selectorBackdrop, rowBackdrop)
```

The graph is acyclic: the pill samples `pageBackdrop`; the selector samples the pill output through
`tabsBackdrop`; the row is separate. No surface samples itself.

### 2.2 `drawBackdrop` effect order

The documented order is **color filter -> blur -> lens** (`GlassSurface.kt:213-227`). The surface tint is
drawn by `onDrawSurface` after the sampled content is filtered; the highlight and shadow are library-level
layers around it.

```
backdrop recording
    |
    v
colorControls(brightness, contrast, saturation)
    |
    v
blur(radiusPx)
    |
    v
lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)
    |
    v
onDrawSurface { tint / frost / inner shadow / soft rim }
    |
    v
highlight (directional rim) + shadow (optional)
```

The effect lambda is not where the highlight or shadow is applied. `DrawBackdropNode` draws the backdrop, then
`onDrawSurface`, then the content; `Highlight` and `Shadow` are separate library modifier nodes around that draw.
Treat their exact compositing order as library-owned and verify it if you pin a new version.

**Color controls.** Duo uses brightness `0.04`, contrast `1.0`, saturation `1.5`
(`GlassSurface.kt:54-57`). Saturation 1.5 matches the measured ColorDuo vibrancy matrix; the matrix is a
Rec.709 saturation matrix, not an arbitrary color grade (`agent/archive/originals/AGILE-1.7-COLORDUO.md:89-104`).

**Blur.** The argument is in pixels. `GlassTokens.NavigationBlur = 12.dp` is converted at the draw site with
`toPx()` (`GlassSurface.kt:220`). Passing `16f` as if it were dp was the early flat-glass bug
(`GlassSurface.kt:46-53`).

**Lens.** `refractionHeight` and `refractionAmount` are pixel distances. The measured liquid values are
24 dp / 24 dp with `depthEffect = true` and `chromaticAberration = true`
(`agent/archive/originals/AGILE-1.7-COLORDUO.md:47-50, 106-115`). The lens is a runtime shader; it needs API 33+ and is the most
expensive effect in the chain. Duo does not author AGSL: it calls the library's lens with the measured uniforms.
That is a requirement (GLASS3-01), not an implementation detail. The measured reference shader uses a seven-tap
dispersion for chromatic aberration; Duo only toggles the boolean.

**Surface draw.** `onDrawSurface` draws in the local shape coordinate space. It is where the material tint,
frost, moving tint, inner shadow and soft rim live (`DuoShell.kt:405-445`; `GlassSurface.kt:236`). It is not
part of the sampled backdrop, so it cannot recursively capture the surface.

**Highlight.** A directional rim drawn by the library. Duo's selector passes an explicit `Highlight`
(`DuoShell.kt:395-403`); the pill passes `highlight = null` on the live path and keeps a manual rim only for
the static fallback (`DuoShell.kt:292-331`). `Highlight.Default` is not a neutral value; it is a concrete
rim that you must replace or disable.

**Shadow.** `Shadow.Default` is the library's default (24 dp). Duo passes `shadow = null` on the selector and
pill and uses a Compose `Modifier.shadow(8.dp)` only on the selector (`DuoShell.kt:379, 404`). Cards keep the
iOS-style Compose shadow in `GlassSurface` (`GlassSurface.kt:197-208`) but `GlassCard` itself is a static
Material card, not a live Backdrop surface.

**Layer block.** `GlassSurface` uses `layerBlock` to apply the press scale to the layer, not to the sampled
backdrop (`GlassSurface.kt:229-235`). This keeps the page content stable under a press animation. The same
idea is why the selector animates width/height through layout, never `scale` (`DuoShell.kt:370-373`).

### 2.3 Quality tiers and fallbacks

`GlassQuality` is a deterministic quality switch, not a fake result (`GlassSurface.kt:95-113`):

| Flag | Phone | Emulator |
|---|---:|---:|
| `lensEnabled` | true | false |
| `liveSurfaces` | true | false |
| `liveControls` | true | false |
| `blurScale` | 1.0 | 0.5 |

The trigger is `Build.FINGERPRINT` / `Build.HARDWARE` (`generic`, `emulator`, `ranchu`, `goldfish`). The
comment records the reason: the API 36 emulator's software GPU produced 263 ms frames and an ANR on the live
lens path (`GlassSurface.kt:95-99`). Phase 7 measured a full live-glass ANR; three surfaces were reduced to one
live bar on phones, static cards, and static controls on the emulator (`agent/archive/originals/PHASE-7-REPORT.md:27-34`,
`agent/archive/originals/WALKTHROUGH-REVIEW-1.5.0.md:53-64`).

Fallback paths:

| Surface | Live path | Fallback |
|---|---|---|
| Pill | `GlassSurface(liquid = true)`: blur + tint + lens | Static vertical tint gradient + manual rim when `!liveSurfaces` |
| Selector | Always `drawBackdrop`; lens gated by `lensEnabled` | Still draws frost/highlight; no lens |
| Switch | `drawBackdrop` track, backdrop = wallpaper | Material `Switch` when `LocalGlassBackdrop == null`; static gradient when no live controls |
| Slider | `drawBackdrop` track/thumb | Material `Slider` when no backdrop; static track/thumb when no live controls |
| Scroll edge | Masked blur (`DstIn`) | Static vertical shade |
| Cards | Not a live surface | `GlassCard`: static Material card at 0.94 alpha |

The shipped app has `minSdk = 33`, so neither the API 31–32 nor the below-31 branch is deployed; they exist for
previews, tests and portability. On API 31–32 a port would have blur but no lens; below 31 it needs a translucent
fallback.

---

## 3. Duo architecture and the final alpha.8 layer order

### 3.1 Composition tree

```
DuoShell
└─ GlassBackdrop
   ├─ wallpaper Box            layerBackdrop(wallpaper)
   │  └─ Wallpaper()           gradient + two wine blooms
   ├─ page Box                 layerBackdrop(page)
   │  └─ content(wallpaper)    cards/controls receive LocalGlassBackdrop = wallpaper
   └─ bottom Box
      ├─ ScrollEdgeGlass(barBackdrop)
      └─ LensNavigationBar(pageBackdrop = barBackdrop)
         ├─ tabsBackdrop       layerBackdrop(pill + press glow)
         ├─ rowBackdrop        layerBackdrop(tab row)
         ├─ selectorBackdrop   combined(pageBackdrop, tabsBackdrop)
         └─ selectorMovingBackdrop combined(selectorBackdrop, rowBackdrop)
```

Source: `GlassSurface.kt:128-161`, `DuoShell.kt:94-116, 148-497`.

### 3.2 Ownership table

| Layer | Records | Who samples it | Why |
|---|---|---|---|
| `wallpaper` | Synthetic gradient + blooms | `content` cards/controls; `barBackdrop` | Static structure for refraction without self-capture. |
| `page` | Page content | `barBackdrop` only | Scrolling text is refracted by the floating bar. |
| `tabsBackdrop` | Pill material + press glow | `selectorBackdrop` | Selector lens sees the actual pill output. |
| `rowBackdrop` | Tab icons/labels | `selectorMovingBackdrop` only | Movement lens has high-contrast text to bend. |
| `barBackdrop` | wallpaper + page | Pill and scroll edge | The bar sees page content. |
| `selectorBackdrop` | barBackdrop + pill | Selector at idle | Idle selector samples pill/page but has no lens effects. |
| `selectorMovingBackdrop` | selectorBackdrop + row | Selector while moving | Moving lens samples page + pill + row. |
| `LocalGlassBackdrop` | wallpaper | `GlassSwitch`, `GlassSlider` | Controls never sample their container. |

### 3.3 Idle versus moving: the alpha.8 rule

The key state expression is:

```kotlin
val moving = dragging || press > 0.01f
```

`press` is the ColorDuo press spring `spring(dampingRatio = 0.7f, stiffness = 500f)`
(`DuoShell.kt:204-210`). The z-order and the selector backdrop are both functions of `moving`:

| State | Selector z | Row z | Selector backdrop | Effects | Surface | Rim/shadow |
|---|---:|---:|---|---|---|---|
| Idle | 0 | 1 | `selectorBackdrop` (page + pill) | none | Frost overlay | Always-on highlight at 0.45x; inner + soft rim; 8 dp drop shadow |
| Moving | 1 | 0 | `selectorMovingBackdrop` (page + pill + row) | `blur(0)` + lens 24/24, depth + chroma | Frost + 0.04 moving tint | Full highlight; inner + soft rim; 8 dp drop shadow |

Code: `DuoShell.kt:370-447` (selector), `448-494` (row), `375` and `449` (z-order), `381` (backdrop switch),
`383-394` (effects), `395-403` (highlight), `405-445` (surface).

Why this ordering exists:

- **Idle:** the row is above the selector so icons and labels are drawn crisply, not through a lens or a
  blurred/tinted capsule. The selector is a frosted capsule under the text.
- **Moving:** the selector must be above the row for the lens to have the icons/labels in its sampled backdrop.
  The row remains visible around the selector; the selector's lens bends the row content.
- **No blur on the selector:** the pill already owns blur. A second blur on the selector would soften the
  labels that the movement lens is supposed to reveal and would add a costly pass at the exact moment the user
  is dragging.

### 3.4 The pill

The pill is a `GlassSurface(liquid = true)` inside the pill recording box (`DuoShell.kt:288-331`):

| Property | Value | Source |
|---|---|---|
| Height | 64 dp | `DuoShell.kt:169`; measured ColorDuo liquid |
| Outer bounds | 80 dp tall, 24 dp horizontal padding, 7 dp vertical | `DuoShell.kt:101-109`; room for the 73 dp held selector |
| Shape | `RoundedCornerShape(50)` | `DuoShell.kt:322`; measured 50% |
| Blur | 12 dp | `GlassSurface.kt:59`, `220` |
| Lens | 24 / 24 dp, depth + chroma | `GlassSurface.kt:221-226` |
| Tint | `#121212` @ 0.40 dark; `#FAFAFA` @ 0.40 light | `GlassSurface.kt:76-77`, `92-93` |
| Border | none (`showBorder = false`) | `DuoShell.kt:327` |
| Highlight | `null` on the live path | `DuoShell.kt:329` |
| Shadow | `null` | `DuoShell.kt:330` |
| Fallback rim | Manual two-pass rim, only when `!liveSurfaces` | `DuoShell.kt:292-320` |

The pill is not blur-only: `GlassSurface(liquid = true)` applies its own 24/24 lens as well as the 12 dp blur.
That lens bends the page content behind the pill. The selector is a second, movement-only lens that later bends
the pill + row. The pill does not sample its own recorded layer: it samples `pageBackdrop`, while
`tabsBackdrop` records the pill's rendered output. That acyclic order is what prevents recursion.

The press glow is drawn inside the pill recording box after the pill surface (`DuoShell.kt:332-365`). It is
therefore part of `tabsBackdrop` and is sampled by the selector on the next frame. It is a radial white
`0.16 * press`, radius `1.5 * selectorHeight`, inset 8 dp, clipped to the pill shape. This is a measured
ColorDuo behaviour (`agent/archive/originals/AGILE-1.7-COLORDUO.md:165-186`).

### 3.5 The selector

The selector is a separate `Box` with no click handler; the row owns selection (`DuoShell.kt:370-447`).

| Property | Value | Source |
|---|---|---|
| Shape | `RoundedCornerShape(50)` | `DuoShell.kt:236` |
| Rest size | `min(71.dp, itemWidth * 0.86f)` x 57 dp | `DuoShell.kt:174-176` |
| Held size | `min(94.dp, itemWidth * 0.98f)` x 73 dp | `DuoShell.kt:177-178` |
| Width/height animation | `spring(0.8f, 350f)` | `DuoShell.kt:221-231` |
| Position | `Animatable` station X; 0.78/420 settle | `DuoShell.kt:185-219` |
| Drop shadow | Compose `Modifier.shadow(8.dp)` | `DuoShell.kt:379` |
| Backdrop | `selectorBackdrop` idle; `selectorMovingBackdrop` moving | `DuoShell.kt:381` |
| Idle effects | none | `DuoShell.kt:383-394` |
| Moving effects | `blur(0)` + lens 24/24, depth + chroma | `DuoShell.kt:384-394` |
| Frost | white 0.12 dark / black 0.06 light, `*(1 - 0.6 * press)` | `DuoShell.kt:406-417` |
| Moving tint | `navTint.copy(alpha = 0.04f)` | `DuoShell.kt:418-420` |
| Inner shadow | 1.5 dp inset, black 0.18, 0.75 dp stroke | `DuoShell.kt:421-432` |
| Soft rim | `rimColor` at 0.45x alpha, 0.75 dp stroke | `DuoShell.kt:433-444` |
| Highlight | `Highlight(0.75.dp, 0.4.dp, alpha, Default(0.5f, 45f, 1f))` | `DuoShell.kt:395-403` |
| Highlight alpha | 0.78 dark / 0.96 light; 0.45x idle, 1x moving | `DuoShell.kt:196-202, 399-400` |

The idle selector has **no effects block**. It still samples the backdrop (so the pill/page show through the
frost), but it applies no color controls, no blur and no lens. The frost is the only surface fill; the highlight,
inner shadow, soft rim and drop shadow are depth layers. This is deliberate: it keeps the idle capsule readable
over the page and avoids a second blur pass behind the labels.

The selector also draws a manual soft rim at 0.45x alpha inside `onDrawSurface` (`DuoShell.kt:433-444`) while the
library highlight is the crisp rim. That is a subordinate echo, not a second equal-weight rim. If you want strict
one-owner semantics, drop the echo; the earlier double-rim defect was a full-strength manual rim plus the default
Highlight, which read as two edges.

The moving selector is transparent glass in the literal sense: `blur(0)` and a lens. The 0.04 tint is a faint
identity tint, not the measured reference's full-row 0.16 tint. The reference liquid mode does not have a
filled selector at all; it uses a full-row 0.16 tint plus the rim. Duo's filled/frosted selector is a hybrid of
the reference liquid rim and the reference floating selector, so it is a design choice, not a pixel match.

### 3.6 Tab row and colors

The row is a `Row` with `selectableGroup()` and one `selectable` per tab (`DuoShell.kt:449-494`).

- Selected icon: 24 dp; other icons: 17 dp (`DuoShell.kt:481`).
- Label: `labelSmall` 11 sp; selected `SemiBold`, others `Medium` (`DuoShell.kt:484-490`).
- Colors are owned by the pure function `navTabColor` (`NavHover.kt:19-24`):
  - hovered while moving: `DuoColors.NavAccent`;
  - selected while not moving: `DuoColors.NavAccent`;
  - otherwise: the caller's idle color, which is `Color.White` in `DuoShell.kt:458`.
- `DuoColors.NavAccent` is `#3C88F8` (`DuoDesign.kt:28`).
- In alpha.8 the selected tab is blue, not white. Older requirement text (NAV3-01) describes a white selected
  icon; the shipped code is the authority (`NavHover.kt:21-24`, `AGILE-1.8-NOTES.md:53-54`).

The hover index is pure logic and is unit-testable (`NavHover.kt:6-17`). The `derivedStateOf` is keyed on
`itemWidthPx` so a rotation does not keep a stale width (`DuoShell.kt:191-192`).

### 3.7 Cards, controls and scroll edge

- `GlassCard` is intentionally **not** a live backdrop. It is a Material `Card` with a 0.94-alpha container and
  a 1 dp hairline border (`GlassSurface.kt:268-292`). Ten simultaneously scrolling `LayerBackdrop` cards plus
  the scroll edge and the navigation material caused an ANR on the API 36 emulator. Apple puts liquid glass on
  navigation and controls, not on every content card; Duo follows that allocation.
- `GlassSwitch` and `GlassSlider` sample `LocalGlassBackdrop`, which is the wallpaper only
  (`GlassControls.kt:61-178, 184-361`). This avoids sampling the card that contains the control. If no backdrop
  is available, they fall back to Material `Switch`/`Slider` so previews and tests still work.
- `ScrollEdgeGlass` draws a 72 dp top and 92 dp bottom material (`GlassSurface.kt:299-367`). It blurs the
  combined backdrop at `CardBlur` (24 dp) inside a `CompositingStrategy.Offscreen` layer, then masks the blur
  with a `DstIn` vertical gradient (black -> 0.72 black -> transparent). The mask makes the blur fade instead
  of ending on a hard rectangle. The emulator/static path replaces the blur with a three-stop background
  shade.

---

## 4. Measured tokens and provenance

### 4.1 Token table

Legend:

- **M** = statically measured literal from ColorDuo 1.1.7 (APK SHA-256
  `d7441541b5ac1c4802ca0312ba1a1debf873580efa534cc16c1e69c2c45e8cd2`; read-only; APK never executed).
- **R** = Apple/Kyant reference transcription, not a ColorDuo literal.
- **D** = Duo design choice, tuned or chosen for this product.

| Token | Value | Class | Notes |
|---|---|---:|---|
| `SATURATION` | 1.5 | M (derived) | ColorDuo vibrancy matrix reduces to saturation 1.5 with Rec.709 weights. |
| `BRIGHTNESS` | 0.04 | R/D | Apple/Kyant reference; not a ColorDuo matrix literal. |
| `CONTRAST` | 1.0 | R/D | Neutral; kept at 1.0. |
| `NavigationBlur` | 12 dp | M | ColorDuo liquid pill. |
| `CardBlur` | 24 dp | D | Cards are static; used by the scroll edge. |
| `ControlBlur` | 12 dp | D | Control-scale material. |
| `ThinBlur` | 8 dp | D | Slider thumb. |
| `NavigationLensHeight` | 24 dp | M | Liquid pill/selector. |
| `NavigationLensAmount` | 24 dp | M | Liquid pill/selector. |
| `ControlLensHeight` / `Amount` | 8 / 12 dp | D | Defined but **not used**; switch and slider hardcode 6/9 and 5/7. |
| `NavigationTintDark` | `#121212` @ 0.40 | M | |
| `NavigationTintLight` | `#FAFAFA` @ 0.40 | M | |
| `NavigationSelectionAlpha` | 0.16 | M (unused) | Reference liquid full-row tint. Duo's moving selector uses 0.04. |
| `NavigationReflectionAlpha` | 0.16 | M | Press glow multiplier. |
| `NavigationRimWidth` | 0.75 dp | M | |
| `NavigationRimBlur` | 0.4 dp | M | |
| `NavigationRimAlphaDark` | 0.78 | M | |
| `NavigationRimAlphaLight` | 0.96 | M | |
| Highlight style | 0.5 white / 45° / falloff 1.0 | M | `HighlightStyle.Default(0.5f, 45f, 1f)`. |
| Pill height | 64 dp | M | |
| Pill shape | 50% | M | |
| Selector rest | 71 x 57 dp, capped by slot | D | Reference liquid has no filled selector; floating reference is 48 dp high. |
| Selector held | 94 x 73 dp, capped by slot | D | Held capsule exceeds the 64 dp pill; outer box is 80 dp. |
| Selector frost | white 0.12 dark / black 0.06 light | D | `*(1 - 0.6 * press)`. |
| Idle highlight | 0.45x full alpha | D | Alpha.7 choice; keeps a static edge without a full rim. |
| Inner shadow | 1.5 dp inset, black 0.18 | D | Added for depth in alpha.7. |
| Selector drop shadow | 8 dp | D | Reference navigation has no shadow. |
| Press glow | white 0.16 x press; radius 1.5 x height; 8 dp inset | M | Only while pressed. |
| Card tint | white 0.07 | D | `GlassSurface` cards; `GlassCard` uses 0.94 surface instead. |
| Control tint | white 0.09 | D | Defined but unused; controls use their own wine/white tints (0.10–0.90). |
| Border highlight | white 0.20 | D | Card border brush only. |
| `minSdk` | 33 | D/constraint | Blur needs 31+; lens shader needs 33+. |
| Emulator quality | no lens, no live surfaces/controls, half blur | D | Test/emulator tier. |
| Press spring | 0.7 / 500 | M | ColorDuo. |
| Stretch spring | 0.8 / 350 | M | ColorDuo. |
| Settle spring | 0.78 / 420 | M | ColorDuo; visibility threshold 0.01 in the reference. |

### 4.2 What is genuinely measured, and what is not

**Measured navigation literals:** blur 12 dp; lens 24/24 with depth + chroma; dark/light tint 0.40; 64 dp pill;
50% shape; rim 0.75/0.4 dp at 0.78/0.96; white 0.5 highlight at 45°, falloff 1.0; press reflection 0.16;
reference full-row selection tint 0.16; saturation 1.5; press/stretch/settle springs. These are the numbers
you can defend with `agent/archive/originals/AGILE-1.7-COLORDUO.md`.

**Not measured:** card blur/tint, control blur/tint/lens, selector frost, idle highlight factor, inner shadow,
selector drop shadow, selector geometry, tab icon sizes/label style, the 0.04 moving tint, the 0.45x idle
highlight, the 8 dp selector shadow, and the emulator quality tier. These are Duo design choices. Do not label
them "ColorDuo values."

**Impossible exact match:** the reference liquid mode has no filled selector. It uses a full-row 0.16 tint and
the pill rim as the selection indicator. Duo's filled/frosted capsule with its own lens is closer to the
reference *floating* selector plus the liquid rim. That hybrid is intentional and should be described as Duo's
material, not as a clone.

### 4.3 Two measured-literal warnings

1. **`BarBlurRadius = 40.0f` is not a blur radius.** In ColorDuo it is constant-folded into the highlight angle
   sweep `45° + 40 * velocity` (`agent/archive/originals/AGILE-1.7-COLORDUO.md:117-125`). The actual glass blur is 12 dp. Do not
   port 40 as a blur.
2. **`PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` has no consumer in the measured APK.** The string exists but no smali
   reference uses it (`agent/archive/originals/AGILE-1.7-COLORDUO.md:127-129`). Do not infer a progressive-blur implementation
   from its presence; Duo's scroll edge is its own `DstIn` mask.

---

## 5. API contracts and short snippets

### 5.1 `GlassBackdrop` contract

```kotlin
@Composable
fun GlassBackdrop(
    floatingBar: @Composable (Backdrop) -> Unit = {},
    scrollEdge: @Composable (Backdrop) -> Unit = {},
    content: @Composable (Backdrop) -> Unit
)
```

Invariants:

1. Record the static scene and the page separately.
2. Give `content` the wallpaper, never the combined layer.
3. Give `floatingBar`/`scrollEdge` the combined wallpaper + page.
4. Never let a glass surface sample the layer that contains it.

Minimal recording:

```kotlin
val wallpaper = rememberLayerBackdrop()
val page = rememberLayerBackdrop()
val bar = rememberCombinedBackdrop(wallpaper, page)

Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().layerBackdrop(wallpaper)) { Wallpaper() }
    Box(Modifier.fillMaxSize().layerBackdrop(page)) {
        CompositionLocalProvider(LocalGlassBackdrop provides wallpaper) {
            Content(wallpaper)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Bar(bar)
    }
}
```

### 5.2 `GlassSurface` contract

```kotlin
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.CardShape,
    liquid: Boolean = false,
    pressed: Boolean = false,
    blurRadius: Dp = if (liquid) GlassTokens.NavigationBlur else GlassTokens.CardBlur,
    tint: Color = if (liquid) glassNavigationTint() else GlassTokens.CardTint,
    borderBrush: Brush? = null,
    showBorder: Boolean = true,
    highlight: (() -> Highlight)? = { Highlight.Default },
    shadow: (() -> Shadow)? = { Shadow.Default },
    content: @Composable () -> Unit
)
```

Invariants:

- `liquid = true` selects navigation blur/tint and the 24/24 lens; `liquid = false` selects card blur/tint and
  no lens.
- `highlight` and `shadow` are nullable on purpose. Pass `null` when the surface owns no rim or no shadow.
- The border is a separate `Modifier.border`; it is not part of Backdrop.
- The static fallback keeps the tint and shape but drops the shader/blur.

### 5.3 State-gated selector

```kotlin
val moving = dragging || press > 0.01f
val selectorBackdrop = if (moving) selectorMovingBackdrop else selectorBackdropIdle

Box(
    Modifier
        .zIndex(if (moving) 1f else 0f)
        .offset { IntOffset(selectorX.roundToInt(), selectorY.roundToInt()) }
        .size(selectorWidth, selectorHeight)
        .drawBackdrop(
            backdrop = selectorBackdrop,
            shape = { RoundedCornerShape(50) },
            effects = {
                if (moving) {
                    blur(0.dp.toPx())
                    lens(
                        refractionHeight = 24.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                }
            },
            highlight = {
                Highlight(
                    width = 0.75.dp,
                    blurRadius = 0.4.dp,
                    alpha = if (moving) 0.78f else 0.78f * 0.45f,
                    style = HighlightStyle.Default(0.5f, 45f, 1f)
                )
            },
            shadow = null,
            onDrawSurface = {
                drawRect(Color.White.copy(alpha = 0.12f * (1f - 0.6f * press)))
                if (moving) drawRect(Color.White.copy(alpha = 0.04f))
            }
        )
)
```

The row uses the opposite z-order:

```kotlin
Row(
    Modifier
        .zIndex(if (moving) 0f else 1f)
        .fillMaxSize()
        .selectableGroup()
        .layerBackdrop(rowBackdrop)
) { /* tabs */ }
```

### 5.4 Press layer block

```kotlin
Modifier.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = { /* colorControls -> blur -> lens */ },
    layerBlock = {
        val scale = if (pressed) 1.012f else 1f
        scaleX = scale
        scaleY = scale
    },
    onDrawSurface = { drawRect(tint) }
)
```

### 5.5 Controls contract

```kotlin
val wallpaper = LocalGlassBackdrop.current
if (wallpaper == null) {
    MaterialFallback()
    return
}
// The control samples wallpaper, never the card that contains it.
```

`GlassSwitch` and `GlassSlider` keep semantics (`Role.Switch`, `toggleableState`, `ProgressBarRangeInfo`,
`setProgress`) and preserve the Material fallback for previews/tests (`GlassControls.kt:61-178, 184-361`).

---

## 6. Evolution and decision history

### 6.1 Timeline

| Build | Code | Commit | Glass/decision | Result |
|---|---:|---|---|---|
| `1.5.0-beta.14` | 44 | `ed99ecc` | Phase 5 prototype lock: wallpaper/page split; `GlassTokens`; selector circle at rest, slime capsule on drag; glass switch/slider; scroll edge; iOS depth shadow | 319 tests; APK not installed at build time |
| `1.5.0-beta.20` | 50 | `d848cc6` | Emulator all-live glass ANR; `GlassQuality`; static cards; one live surface | ANR removed; frame budget still failed on emulator |
| `1.5.0-beta.45` | 75 | `7efbdd5` | Selector behind labels; 80 dp outer box; selector-over-row fog fixed | Pre-1.6 shell |
| 1.6.0 | 76 | `0ba5c2b` | ColorDuo measured values: 12 dp blur, 24/24 lens, 0.40 tint, 0.75/0.4 rim, 64 dp pill, no nav shadow | Wrong turns: double rim, default 24 dp shadow, dark/foggy material |
| 1.7.0 | 89 | `745fc5f` | 1.7 review fixes; selector behind labels preserved | Stable baseline |
| `1.8.0-alpha.1` | 90 | `0fc12e4` | Single live rim owner on the pill; radial press glow; 0.04 selector tint; blue hover | Installed; selector blur still produced fog |
| `1.8.0-alpha.2` | 91 | `a2bb594` | Design-switch fatal fixed with two persistent Rive panes; row recorded under the selector lens; selector blur/vibrancy removed | Installed; lens sampled text but idle text was under the capsule |
| `1.8.0-alpha.3` | 92 | `9467e98` | Packaged nav icons; movement-only lens; 5-bar cell map; explicit seats | Delivered |
| `1.8.0-alpha.4` | 93 | `e047324` | v89 extras, center bolt, clock, unsnapped sliders | Delivered |
| `1.8.0-alpha.5` | 94 | `d82fbd7` | Shade seat; presets removed; selected blue / others white; idle static rim; lens movement-only | Delivered; phone was offline for direct install |
| `1.8.0-alpha.6` | — | not recorded | Not described in `AGILE-1.8-NOTES.md` | Unknown |
| `1.8.0-alpha.7` | 96 | `6611fe2` | Pill owns blur; selector below row with no blur; frost 0.12/0.06; inner + drop shadow; always-on highlight | Movement lens disappeared |
| `1.8.0-alpha.8` | 97 | `3d5ea0d` | Idle: row above selector; moving: selector above row and samples page + pill + row; lens/chroma restored | Current; owner visual pass pending in the notes |

The Phase 5 report declared beta.14 "locked" against Phase 6–8 visual retuning. The 1.6 ColorDuo pass and the
1.8 selector work later revised the navigation material. Treat that lock as scoped to the Phase 5 prototype,
not as a promise that the navigation glass would never change (`agent/archive/originals/PHASE-5-REPORT.md:1-6`).

### 6.2 Wrong turns and the rule each produced

| Wrong turn | Symptom | Root cause | Rule | Fixed in |
|---|---|---|---|---|
| Selector blur over icons | Labels/icons looked fogged under the capsule; the glass read as milk over text | The selector carried `vibrancy()` + `blur(4.dp)` (beta.45, `5496fac`/`9c24ede`) and later `blur(12.dp)` in alpha.1 (`0fc12e4`); in beta.45 the selector was painted after the row. Alpha.2 removed the blur but recorded the row into the lens backdrop, so text sat under the capsule | The pill owns blur; the selector has **no blur**. Text is above the selector at idle and is sampled but not blurred during movement | alpha.2 removed selector vibrancy/blur; alpha.7 moved the row above; alpha.8 keeps zero blur |
| Lens with no backdrop content | The lens looked invisible or flat; only a tint remained | Early selector sampled the page only, or page + pill, with no high-contrast row content | Record the layers that should visibly bend. The movement backdrop must include the row: `page + pill + row` | `22358e0` added a combined backdrop; alpha.8 added `rowBackdrop` |
| Double rim / default shadow | Two bright edges and a heavy 24 dp halo | A manual rim plus `Highlight.Default`; `drawBackdrop` was called without `shadow`, so `Shadow.Default` (24 dp) applied | One rim owner per surface. Pass an explicit `Highlight`; pass `shadow = null` when no depth is wanted. The manual rim is fallback-only | alpha.1 (`0fc12e4`) made the live pill use one explicit highlight; alpha.7 moved the selector rim |
| Tint too dark/foggy | The pill/selector looked like dark fog, not clear glass | The beta.45/1.6 pre-ColorDuo pass used a 0.72 background tint and 36 dp blur; the selector added a 4–12 dp blur | Use the measured 0.40 tint, 12 dp pill blur, zero selector blur, and a 0.12 frost overlay. Verify in both themes | `0ba5c2b` (12 dp / 0.40); alpha.2 (0.04); alpha.7 (frost) |
| Icons above selector permanently | Idle was crisp, but the liquid lens disappeared while dragging | Alpha.7 kept the row above the selector in every state and the selector's backdrop was page + pill only | Z-order is a state, not a constant: idle row above; moving selector above | alpha.8 (`3d5ea0d`) |
| Moving backdrop missing | Dragging showed no refraction or chromatic aberration | `selectorBackdrop` was page + pill; the row was not recorded into a layer the selector could sample | If the effect is movement-only, build a movement-only combined backdrop and gate it with `moving` | alpha.8 (`3d5ea0d`) |
| Live Rive preview crash | Switching the design fatally crashed the app preview | The preview destroyed and recreated a Rive view; the on-disk tombstone is a settings-app Rive global-reference overflow (the pre-reboot SystemUI log was wiped) | Keep persistent native panes; switch visibility/active scene, never destroy/recreate on a design change. Static card demos are safer than live Rive in every card | alpha.2 (`a2bb594`) |
| Shade/position mistakes | Wrong shade seat, size or position | Separate slot/geometry work, not glass | Do not mix shade/position changes into a glass change; each has its own anchor and evidence | alpha.3–alpha.8, separate commits |

### 6.3 Verification status

`AGILE-1.8-NOTES.md` records alpha.8 as installed on CPH2747 (13:50:03) and delivered to the owner, with the
owner visual pass still pending. Gates at that build: 482 unit tests, 0 failures; release build passes; route
log check clean; both Rive scenes 158/158 keyframes with easing. The notes explicitly leave the movement lens
unverified on device. Other ROMs remain `unverified`.

The owner feedback that drove alpha.8 was concrete: the shade was fixed and the idle selector was right, but
the liquid lens disappeared during movement. Alpha.8 restores it by changing the z-order and the moving
backdrop, not by adding blur. The remaining device question is whether the movement lens and chromatic
aberration are visible enough on the phone at the shipped 24/24 values.

---

## 7. Pitfalls and anti-patterns

| Pitfall | What it looks like | Rule |
|---|---|---|
| Raw pixels passed as dp | Glass looks flat or barely blurred | Every distance is `dp.toPx()` at the draw site. `blur(16f)` is 16 px, not 16 dp. |
| Sampling the layer that contains the surface | `SIGSEGV` in RenderThread / process death | Split wallpaper and page. Content gets wallpaper; the bar gets the combined layer. Never sample your own layer. |
| Live glass on every card | ANR, 200+ ms frames | One live surface per screen is the budget. Cards are static 0.94-alpha material. |
| Blurring the selector | Fogged labels, soft icons | Pill owns blur. Selector is a lens-only layer with zero blur. |
| Lens with a flat backdrop | Invisible effect; "it does nothing" | Record the content that should bend. A lens over a uniform tint has no structure to refract. |
| Relying on `Highlight.Default` / `Shadow.Default` | Unexpected rim or 24 dp halo | Defaults are concrete values. Pass explicit tokens or `null`. |
| Two equal-weight rims | Double edge | One rim owner. If you keep a soft echo, it must be clearly subordinate (Duo uses 0.45x). |
| Constant z-order | Either labels are fogged or the movement lens disappears | `moving` gates both the selector z-index and the backdrop. |
| Scaling the glass to animate size | The sampled backdrop stretches or shimmers | Animate layout width/height; use `layerBlock` for press scale, not the backdrop. |
| Over-tinting the pill | Dark, muddy, low-contrast material | Measured 0.40 tint; frost is 0.12, not 0.40. Check light mode separately. |
| Hardcoded control values that drift from tokens | Switch/slider don't match the token table | `ControlLensHeight/Amount` and `ControlTint` are currently unused; controls hardcode 6/9 and 5/7. Either use the tokens or delete them. |
| Porting `BarBlurRadius = 40` | A 40 px blur that is not the reference | 40 is the highlight angle sweep, not blur. The measured blur is 12 dp. |
| Porting the unused progressive shader | Dead code or a wrong scroll effect | No consumer exists in the measured APK. Use the `DstIn` mask. |
| Attributing a border/shadow to ColorDuo | "Measured" values that do not exist | The reference navigation has no border and no drop shadow. Duo's card border and selector shadow are design additions. |
| Treating the emulator as a phone | Lens looks absent on the emulator | `GlassQuality` deliberately disables lens/live surfaces on emulators. Test the phone path on the phone. |
| Assuming API 33 everywhere | Crash or no effect on lower APIs | Blur needs 31+; lens needs 33+; below that use a translucent fallback. |
| Ignoring text contrast | Glass looks good but labels are unreadable | Keep text above the glass or on a near-opaque surface. Check contrast in dark and light. |
| Rebuilding a Rive view on design change | App crash / native reference overflow | Persistent panes, visibility switch only. |

---

## 8. Portability checklist

### 8.1 SDK and dependency

- Pin `io.github.kyant0:backdrop:1.0.1` (or a deliberately tested version). Do not assume 2.x is a drop-in; it
  is a Compose Multiplatform artifact on a different Kotlin/Compose stack.
- Use Kotlin 2.2.21 and Compose BOM `2025.10.01`, or re-run the compatibility spike with your own versions.
- Set `minSdk` to 33 for the full lens path. If you need 31–32, ship blur-only; below 31, ship a translucent
  fallback. Guard every API-level branch.
- Keep the dependency and the token table in one place. Do not scatter literal dp/alpha values in screens.

### 8.2 Fallback and API behavior

- Provide a **no-backdrop path**: if the composition local is null, render a Material/opaque control. This is
  what makes previews, tests and older devices safe.
- Provide a **no-live-surface path**: static gradient + border + manual rim. The static path must be visually
  recognizable, not an invisible rectangle.
- Detect emulators or software GPUs and lower quality deterministically. Do not silently change the look on a
  real phone; log the tier.
- Test API 31, 33 and 36 if you widen the floor. A lens that compiles on 33 can still fail at runtime if the
  shader is unsupported by the GPU.

### 8.3 Performance

- Budget **one live backdrop surface per screen**. Cards and large lists are static.
- Keep live surfaces small. A full-screen live blur plus a scrolling list is the expensive case.
- Avoid animating the blur radius. Animate position/size; the selector uses layout animation, not scale.
- Reuse combined backdrops; do not rebuild the graph every recomposition.
- Record only what is needed. The page layer is full-screen and updates while scrolling; that is the main cost.
- Measure with `dumpsys gfxinfo` on the target phone. The emulator baseline is not a phone budget.
- Suspend or pause live effects when the screen is not visible or the app is in the background.

### 8.4 Accessibility

- Check text contrast over the glass in dark and light themes; the 0.94 card and the 0.12 frost exist for this.
- Do not encode selection only with tint. Duo also changes icon size and weight; keep a non-color cue.
- Keep touch targets at least 48 dp. The 52 x 32 switch needs a larger parent hit area if it is the only target.
- Preserve semantics: `Role.Tab`, `selectableGroup`, `Role.Switch`, `toggleableState`,
  `ProgressBarRangeInfo`, `setProgress`.
- Keep labels above the selector at idle. If a movement lens includes them, keep the lens small and avoid blur so
  they remain recognizable; Duo samples the row during movement but never blurs it.
- Respect reduced motion: skip or shorten the lens/press animation; the static frost/rim still communicates the
  selection.

### 8.5 Tests

- Unit-test the pure state mapping: `idle` vs `moving`, z-order, backdrop selection, hover index, selected color.
- Pin the token table in a test so a refactor cannot silently change blur/lens/alpha.
- Test the no-backdrop and no-live-surface fallbacks; they are real code paths, not dead branches.
- Grep that no app-authored `RuntimeShader`/AGSL exists if the requirement is "library-only."
- Pin the dependency version.
- Capture screenshots for dark/light, idle/moving, and both fallback paths. The current project has unit tests
  but no selector screenshot/instrumented test; a port should add one.
- Run a performance capture after a deliberate scroll or drag, not on an idle screen.
- Keep a device matrix; mark unmeasured ROMs `unverified` instead of claiming support.

### 8.6 Integration and assets

- Keep glass app-only. Do not inject a Backdrop surface into SystemUI; the module has its own native/Canvas
  failure domain.
- Never sample the layer that contains the surface. The wallpaper/page split is the minimum viable structure.
- Do not ship proprietary reference assets. Recreate the look from measured numbers and your own drawing.
- Document whether a value is measured, reference-derived or a design choice. The distinction is the only way a
  future reader can tell a bug from a deliberate deviation.

---

## 9. Minimal portable skeleton

This is a compact structure, not the full Duo code. It keeps the layer graph and the idle/moving gate; it omits
Duo's icon assets, press glow, inner shadow, geometry springs and quality tiers.

### 9.1 Shell

```kotlin
@Composable
fun GlassShell(
    content: @Composable (Backdrop) -> Unit,
    bar: @Composable (Backdrop) -> Unit
) {
    val wallpaper = rememberLayerBackdrop()
    val page = rememberLayerBackdrop()
    val barBackdrop = rememberCombinedBackdrop(wallpaper, page)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(wallpaper)) {
            Wallpaper()
        }
        Box(Modifier.fillMaxSize().layerBackdrop(page)) {
            CompositionLocalProvider(LocalGlassBackdrop provides wallpaper) {
                content(wallpaper)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            bar(barBackdrop)
        }
    }
}
```

### 9.2 Navigation with state-gated layering

```kotlin
@Composable
fun GlassNav(pageBackdrop: Backdrop) {
    val pillLayer = rememberLayerBackdrop()
    val rowLayer = rememberLayerBackdrop()
    val idleBackdrop = rememberCombinedBackdrop(pageBackdrop, pillLayer)
    val movingBackdrop = rememberCombinedBackdrop(idleBackdrop, rowLayer)

    var dragging by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "press"
    )
    val moving = dragging || press > 0.01f

    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Box(Modifier.fillMaxSize().layerBackdrop(pillLayer)) {
            GlassSurface(
                backdrop = pageBackdrop,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(50),
                liquid = true,
                tint = glassNavigationTint(),
                showBorder = false,
                highlight = null,
                shadow = null
            ) {}
        }

        Box(
            Modifier
                .zIndex(if (moving) 1f else 0f)
                .offset { IntOffset(selectorX.roundToInt(), selectorY.roundToInt()) }
                .size(selectorWidth, selectorHeight)
                .drawBackdrop(
                    backdrop = if (moving) movingBackdrop else idleBackdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        if (moving) {
                            blur(0.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        Highlight(
                            width = 0.75.dp,
                            blurRadius = 0.4.dp,
                            alpha = if (moving) 0.78f else 0.78f * 0.45f,
                            style = HighlightStyle.Default(0.5f, 45f, 1f)
                        )
                    },
                    shadow = null,
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.12f * (1f - 0.6f * press)))
                        if (moving) drawRect(Color.White.copy(alpha = 0.04f))
                    }
                )
        )

        Row(
            Modifier
                .zIndex(if (moving) 0f else 1f)
                .fillMaxSize()
                .selectableGroup()
                .layerBackdrop(rowLayer)
        ) {
            // tabs
        }
    }
}
```

`selectorX`, `selectorY`, `selectorWidth` and `selectorHeight` are placeholders for your layout animation. The
important part is the graph, not those numbers. The real Duo wraps the 64 dp pill in an 80 dp outer box so the
73 dp held selector is not clipped (`DuoShell.kt:101-109`); use dark-theme white frost or light-theme black frost
in `onDrawSurface`, not a single hardcoded color.

### 9.3 Pure state contract

```kotlin
enum class SelectorState { Idle, Moving }

fun selectorState(dragging: Boolean, press: Float): SelectorState =
    if (dragging || press > 0.01f) SelectorState.Moving else SelectorState.Idle
```

Test this function. Then make z-order, backdrop choice and effects depend on its result. That is the alpha.8
contract in one place.

### 9.4 Mapping to Duo

| Skeleton | Duo |
|---|---|
| `GlassShell` | `GlassBackdrop` (`GlassSurface.kt:128-161`) |
| `Wallpaper()` | Dark gradient + two wine blooms (`GlassSurface.kt:163-180`) |
| `GlassSurface` | `GlassSurface` (`GlassSurface.kt:182-266`) |
| `GlassNav` | `LensNavigationBar` (`DuoShell.kt:148-497`) |
| `moving` | `dragging` or `press > 0.01f` (`DuoShell.kt:210`) |
| `idleBackdrop` | `selectorBackdrop` (`DuoShell.kt:167`) |
| `movingBackdrop` | `selectorMovingBackdrop` (`DuoShell.kt:168`) |
| `pillLayer` | `tabsBackdrop` (`DuoShell.kt:165`) |
| `rowLayer` | `rowBackdrop` (`DuoShell.kt:166`) |
| `GlassSwitch` / `GlassSlider` | `GlassControls.kt:61-178, 184-361` |
| Static cards | `GlassCard` (`GlassSurface.kt:268-292`) |
| Scroll edge | `ScrollEdgeGlass` (`GlassSurface.kt:299-367`) |

---

## 10. Reference index

### Source files

- `app/src/main/java/io/github/kvmy666/duostatusbar/ui/GlassSurface.kt`
- `app/src/main/java/io/github/kvmy666/duostatusbar/ui/GlassControls.kt`
- `app/src/main/java/io/github/kvmy666/duostatusbar/ui/DuoShell.kt`
- `app/src/main/java/io/github/kvmy666/duostatusbar/ui/DuoHomeScreen.kt`
- `app/src/main/java/io/github/kvmy666/duostatusbar/ui/DuoDesign.kt`
- `app/src/main/java/io/github/kvmy666/duostatusbar/ui/NavHover.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

### Project evidence and decisions

- `agent/archive/originals/AGILE-1.7-COLORDUO.md` — statically measured ColorDuo 1.1.7 values, warnings, porting map.
- `agent/archive/originals/AGILE-1.8-NOTES.md` — alpha.2 through alpha.8 build notes and open verification.
- `agent/archive/originals/AGILE-1.8-REQUIREMENTS.md` — GLASS3-01/02, NAV3-01.
- `agent/archive/originals/AGILE-1.8-PLAN.md` — rim ownership, one live surface, no custom AGSL.
- `agent/archive/originals/PHASE-5-PLAN.md` — dependency audit, Apple-reference implementation, beta.14.
- `agent/archive/originals/PHASE-5-REPORT.md` — beta.14 lock, RC-017, selector/controls/scroll work.
- `agent/archive/originals/PHASE-7-REPORT.md` — adaptive glass quality and the ANR finding.
- `agent/archive/originals/WALKTHROUGH-REVIEW-1.5.0.md` — all-live glass review and performance baseline.

### Measured ColorDuo identity

- Package `io.github.fengwenhua.colorduo`, version 1.1.7 (versionCode 10107).
- APK SHA-256 `d7441541b5ac1c4802ca0312ba1a1debf873580efa534cc16c1e69c2c45e8cd2`.
- Analysis was static; the APK was never executed. Values are measurements, not copied source or assets.
