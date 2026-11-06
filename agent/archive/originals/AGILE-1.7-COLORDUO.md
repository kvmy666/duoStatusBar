# ColorDuo 1.1.7 - navigation/selector glass values

Read-only evidence report for AGILE 1.7 (GLASS2 / A5). No app code, build, install, or device setting was changed.

## 0. Artifact and method

| Item | Value |
|---|---|
| APK | `/tmp/colorduo-pull/io.github.fengwenhua.colorduo/base.apk` |
| APK SHA-256 | `d7441541b5ac1c4802ca0312ba1a1debf873580efa534cc16c1e69c2c45e8cd2` |
| Package | `io.github.fengwenhua.colorduo` |
| Version | 1.1.7 (versionCode 10107) |
| Screenshot | `/tmp/colorduo-screen/screen.png` (1272 x 2772, RGBA) |
| Tools | REA 0.7.1 JADX headless (`inspect_android_class`, `inspect_android_method`), local JADX source output, apktool smali |
| Runtime | APK was never executed; no phone used |

Important correction to the request: `LiquidNavigationBar`, `NavigationPill_N85Pf5M`, and `NavigationTabRow_FU0evQE` are **methods**, not static fields. The class has exactly two fields:

- `private static final float BarBlurRadius = 40.0f`
- `public static final String PROGRESSIVE_BLUR_ALPHA_MASK_SHADER = "\nuniform shader content;\nuniform float2 size;\nhalf4 main(float2 coord) {\n    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);\n    return content.eval(coord) * blurAlpha;\n}\n"`

DEX method names and descriptors:

- `LiquidNavigationBar(IIZLii0;Lm70;Lgq;I)V`
- `m20NavigationPillN85Pf5M(IIZLii0;Lzg1;JFFFFZZLm70;Lgq;III)V` (`NavigationPill_N85Pf5M` in JADX naming)
- `m21NavigationTabRowFU0evQE(IIJLes0;Lb70;Lm70;Lgq;II)V` (`NavigationTabRow_FU0evQE` in JADX naming)

The `NavigationPill_N85Pf5M$...` names are synthetic lambdas. The three navigation bar composables are variants of the same pill/tab system, selected by `SettingsNavigationKt` from `Prefs` (`DockedNavigationBar`, `FloatingNavigationBar`, `LiquidNavigationBar`).

The screenshot is the three-tab liquid layout in dark theme (`SettingsTab` entries: `Drawing`, `Parts`, `About`, displayed as Home, Components, About). For that layout the width formula below gives `0.54805998` (54.806% of available width); the dark-theme tint is `#121212` at 0.40 and the static highlight alpha is 0.78.

## 1. Exact values by navigation style

All dp values are Compose `Dp` literals multiplied by density at the draw site. All colors are Compose `Color` values; `copy(alpha)` is applied at the call site.

| Value | Liquid | Floating | Docked |
|---|---:|---:|---:|
| Pill height | **64.0 dp** | **56.0 dp** | no pill; full-width bar, 64.0 dp tab item |
| Pill width fraction | **0.47805998 + 0.07 * (tabs - 2)** | **0.62 + 0.07 * (tabs - 2)** | full width (`fillMaxWidth(1.0f)`) |
| Pill width, 3 tabs | **0.54805998 (54.806%)** | **0.69 (69%)** | 100% |
| Pill width, 4 tabs | **0.61805998 (61.806%)** | **0.76 (76%)** | 100% |
| Pill shape | **RoundedCornerShape(50%)** | **RoundedCornerShape(50%)** | RectangleShape |
| Effective pill radius | **32.0 dp** (50% of 64) | **28.0 dp** (50% of 56) | 0 dp |
| Tint, dark | **`#121212` at alpha 0.40** | **black at alpha 0.36** | **black at alpha 0.58** |
| Tint, light | **`#FAFAFA` at alpha 0.40** | **white at alpha 0.38** | **white at alpha 0.62** |
| Glass blur | **12.0 dp** | **12.0 dp** | **12.0 dp** |
| Lens refraction height | **24.0 dp** | **16.0 dp** | none |
| Lens refraction amount | **24.0 dp** | **32.0 dp** | none |
| Lens depth effect | true | true | n/a |
| Lens chromatic aberration | **true** | false | n/a |
| Moving selector height | none (rim/highlight follows the tab) | **48.0 dp** (56 - 8) | none |
| Moving selector width | none | **`(availableWidthDp - 8.0) / tabs`** | none |
| Moving selector background | none; full-width tint at 0.16 + highlight | **white 0.18 (dark) / black 0.08 (light)** | none |
| Liquid surface tint overlay | **tint color at 0.16** | none | none |

Exact source literals:

- Liquid: `m20NavigationPillN85Pf5M(..., new la1(f31(50.0f) x4), z ? tl.b(gf0.I(4279374354L), 0.4f) : tl.b(gf0.I(4294638330L), 0.4f), 0.47805998f, 64.0f, 24.0f, 24.0f, true, true, ...)`
- Floating: `m20NavigationPillN85Pf5M(..., new la1(f31(50.0f) x4), z ? tl.b(tl.b, 0.36f) : tl.b(tl.d, 0.38f), 0.62f, 56.0f, 16.0f, 32.0f, false, false, ...)`
- Docked: `z ? tl.b(tl.b, 0.58f) : tl.b(tl.d, 0.62f)`; `fillMaxWidth(1.0f)`; no `m20NavigationPillN85Pf5M` call.

`4279374354` = `0xFF121212`; `4294638330` = `0xFFFAFAFA`; `tl.b` = `0xFF000000`; `tl.d` = `0xFFFFFFFF`.

## 2. Selector geometry and movement

From `NavigationPill_N85Pf5M$lambda$12$0`:

```java
float fB = (jhVar.b() - 8.0f) / i;          // selector width, dp
float fZ = jvVar.z(fB);                     // same in px
...
gu1.w(xh1.c(xh1.h(es0VarG2, f4), f2 - 8.0f), zg1Var2);
```

- `jhVar.b()` is the available width in dp; `i` is the tab count.
- `xh1.h` is `Modifier.width`; `xh1.c` is `Modifier.height`.
- Selector width = `(availableWidthDp - 8.0) / tabs`; this is narrower than a tab slot by `8/tabs` dp.
- Selector height = `pillHeight - 8.0` for the non-liquid selector path: 48 dp floating. The same expression would give 56 dp for a 64 dp pill, but the liquid flag skips this branch; liquid selection is the rim/highlight, not a filled selector.
- Selector shape = `zt.u` = **`RectangleShape` (0 dp corner)**. It is clipped by the pill shape with `gu1.w(...)`, so the visible ends take the pill's 50% radius.
- Selector offset X = `round((position - (tabs - 1) / 2.0f) * direction * selectorWidthPx)`. `direction` is `-1`/`+1`; no extra 4 dp offset.
- Selector background:
  - dark theme: white (`tl.d`) at **0.18**;
  - light theme: black (`tl.b`) at **0.08**;
  - drawn with `gu1.r(..., zt.u)`.
- Liquid mode does not draw this per-tab selector. It draws the tint color at **0.16** over the full row (`tl.b(j2, 0.16f)`) and indicates selection with the highlight/rim below.

For the non-liquid selector path, 3 tabs: width = `(availableWidthDp - 8) / 3`; 4 tabs: `(availableWidthDp - 8) / 4`. Liquid has no filled selector; its selection indicator is the rim/highlight.

## 3. Glass pipeline (vibrancy -> blur -> lens)

The liquid/floating pill is built by the custom `ff0.u` glass surface with the same Kyant-style order used by Duo: colour filter, then blur, then lens. The exact callbacks are in `SettingsNavigationKt`:

1. **Vibrancy/saturation** - `vl.a(nd)` applies an Android `ColorMatrixColorFilter` (API 31+ as a `RenderEffect.createColorFilterEffect`). The literal matrix is:

```text
1.3935  -0.3575  -0.036   0   0
-0.1065  1.1425  -0.036   0   0
-0.1065 -0.3575   1.464   0   0
0        0        0       1   0
```

This is a saturation matrix. Derived from the coefficients (not a separate literal), it is **saturation = 1.5** with Rec.709 luminance weights (0.213, 0.715, 0.072). Duo's `GlassTokens.SATURATION = 1.5f` already matches this exactly.

2. **Blur** - `zt.g(nd, uy.e * 12.0f)`: a RenderEffect blur with **radius 12.0 dp** (converted to px with density). `zt.g` is a blur modifier, not a colour filter. It also records the max blur radius in `uy.i` for layer bleed.

3. **Lens/refraction** - `ws.F(nd, f * density, density * f2, true, chromatic)` sets the runtime shader uniforms:
   - `refractionHeight` = first arg (dp -> px);
   - `refractionAmount` = second arg (dp -> px), passed to the shader as `-f2`;
   - `depthEffect` = `true` -> 1.0;
   - `chromaticAberration` = `true` -> 1.0.
   - Liquid: **24.0 dp height / 24.0 dp amount**, depth true, chromatic true.
   - Floating: **16.0 dp height / 32.0 dp amount**, depth true, chromatic false.
   - Docked: no lens, only vibrancy + 12 dp blur.

The shader source (`ws.java`) confirms the uniform names and that chromatic mode uses a seven-tap dispersion (`red, orange, yellow, green, cyan, blue, purple`). `refractionAmount` is negated at upload; report the positive parameter values above.

### `BarBlurRadius` warning

`BarBlurRadius = 40.0f` is **not** the glass blur radius. R8 constant-folded the field; the only use is:

```java
new da0((navigationDragState.getVelocity() * BarBlurRadius) + 45.0f, 11)
```

That is the **highlight angle** sweep: `45 deg + 40 * velocity`. The actual glass blur is 12 dp. Do not port `40.0f` as a blur radius.

### `PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` warning

The exact public shader string is present in the field, but there is no smali reference to `SettingsNavigationKt;->PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` anywhere in the APK. It is defined but unused by this class; do not infer a progressive-blur implementation from its presence.

## 4. Tint, highlight/rim, border, shadow, reflection

### Tint

- Liquid: `Color(0xFF121212).copy(alpha = 0.40f)` in dark; `Color(0xFFFAFAFA).copy(alpha = 0.40f)` in light.
- Floating: black at 0.36 dark; white at 0.38 light.
- Docked: black at 0.58 dark; white at 0.62 light.
- Liquid full-row surface tint: the same tint color at **0.16** (`tl.b(j2, 0.16f)`).
- Floating selector: white 0.18 dark; black 0.08 light.

### Highlight / rim (this is the closest thing to a border)

The nav path has **no `BorderStroke` and no border draw**. It draws a highlight/rim via the custom `y90` highlight and `aa0` modifier:

- Static highlight (`NavigationPill_N85Pf5M$lambda$7$0`):
  - `y90.a(y90.e, 0.75f, z ? 0.78f : 0.96f, null, 8)`
  - width = **0.75 dp**
  - blur radius = **0.4 dp** (the `i = 8` mask leaves the blur at the factory's 0.4 default)
  - alpha = **0.78 dark / 0.96 light**
  - style = default `ca0.b` = `da0(45 deg, mask 15)` -> **white at 0.5 alpha**, angle 45 deg, falloff 1.0, blend mode id 12 (`Plus` in Compose's public ordering).
- Pressed/dynamic highlight (`NavigationPill_N85Pf5M$lambda$12$0$12$0`):
  - `y90.a(y90.e, 0.8f, clamp(press, 0, 1) * 0.65f + 0.25f, new da0(velocity * 40.0f + 45.0f, 11), 2)`
  - width = **0.8 dp**
  - blur radius = **0.25 dp**
  - alpha = **0.25 + 0.65 * press** (range 0.25 to 0.90)
  - style = white 0.5 shader, angle **45 deg + 40 * velocity**, falloff 1.0.
- `aa0` draws the highlight as a stroked rounded rect: `paint.strokeWidth = ceil(widthPx) * 2.0f`. The nominal stroke is **2 x width = 1.5 dp static / 1.6 dp dynamic**, but the `ceil` is applied in pixels, so the actual stroke quantises up by up to 2 px depending on density (for example 0.75 dp at 2.75x is 2.06 px -> 3 px -> 6 px = 2.18 dp). The layer alpha is `y90.c`; the shader colour is white 0.5.

If the requirement's "border alpha" means a solid border, the answer is **none**. If it means the rim, use the highlight values above.

### Shadow

There is **no drop shadow/elevation** in the ColorDuo navigation path. `vy.D` is not a shadow: it is the layer bleed derived from the maximum blur radius (`uy.i`), and for the measured lens/blur pairs it becomes 0 (blur 12 - lens height 24 or 16). Duo's `GlassSurface` shadow (18 dp, 14%) is extra and must not be attributed to ColorDuo.

### Reflection

`NavigationPill_N85Pf5M$lambda$10$0` draws a press-only specular reflection in the liquid branch:

```java
if (liquid && press > 0.0f) {
    brush = linearGradient(
        white.copy(alpha = clamp(press, 0f, 1f) * 0.16f),
        transparent
    )
    centerX = ((widthPx - 8.dp.toPx()) *
               (position - (tabs - 1) / 2.0f) * direction) / tabs
              + widthPx / 2.0f
    centerY = heightPx / 2.0f
    height = heightPx * 1.5f
}
```

- Colour: **white at 0.16 * press**, fading to transparent (`tl.g`).
- Height: **1.5x** the layer height.
- Horizontal inset: **8.0 dp** in the centre calculation.
- Only when liquid mode is active and press > 0; it is not a permanent reflection.

## 5. Motion, drag, and haptics

### Animation specs

| Property | Exact spec |
|---|---|
| Press progress (`navigationPress`) | `spring(dampingRatio = 0.7f, stiffness = 500.0f)` |
| Stretch (`navigationStretch`) | `spring(dampingRatio = 0.8f, stiffness = 350.0f)` |
| Panel shift (`navigationPanelShift`) | `spring(dampingRatio = 0.75f, stiffness = 300.0f)` |
| Drag settle | `spring(dampingRatio = 0.78f, stiffness = 420.0f, visibilityThreshold = 0.01f)` |
| Tap settle | `tween(durationMillis = 240, delayMillis = 90, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))` |
| Resting animatable | visibility threshold `0.01f` |

### Scale and shift values

`zt.V(240, 4, g10.a)` maps to `tween(durationMillis = 240, delayMillis = 90, easing = CubicBezierEasing(0.4, 0.0, 0.2, 1.0))`: mask bit 2 is not set, so `zt.V` supplies its default 90 ms delay.

- Press scale of a tab: selected `1.0 + 0.025 * press`; unselected `1.0 - 0.03 * press`.
- Stretch scale: X `1.0 + 0.16 * stretch`; Y `1.0 - 0.08 * stretch`; both multiplied by `1.0 + 0.35 * press`.
- Panel shift: `4.0 dp * clamp(travel, -1.0f, 1.0f)` (the `navigationPanelShift` offset; `travel` is the normalised drag travel).
- These are float multipliers; no dp values except the panel shift.

### Drag model (`NavigationDragState`)

- `tabWidth = availableWidth / tabCount`; position is a float tab index in `0 .. tabCount-1`.
- `move(dx, width, ...)`: `position += (dx / tabWidth) * direction`, clamped to `[0, tabCount-1]`.
- Crossing/haptic: when `round(position)` changes, `onCrossing` fires; it calls `e41.a(9)` -> `View.performHapticFeedback(9)` on API >= 30.
- `travel += dx / width` (not dx/tabWidth).
- `velocity = clamp(((dx / tabWidth) * 250.0f) / dt, -1.0f, 1.0f)`; `dt` is at least 1 ms.
- On release:
  - no velocity or non-positive width: target = `round(position)`;
  - otherwise target = `round((isRtl ? width - x : x) / width * tabCount)`;
  - clamp to `[0, tabCount - 1]`;
  - `settleTo(target, pressed && velocity == null, velocity != null)`, then `onSelect(target)`.
- There is no explicit drag slop/long-press threshold in this state machine; the pointer input starts the drag and the crossing is defined by the rounded tab index. The tab row itself uses `jv0` and is not the drag surface.
- Haptic type is numeric **9**; the platform name for 9 on older API levels is not asserted (the `e41` mapper changes it below API 30).

## 6. Tab row typography and sizing

From `m21NavigationTabRowFU0evQE`:

| Value | Exact |
|---|---|
| Icon size | **23.0 dp** (`xh1.e(es0, 23.0f)` = `Modifier.size(23.dp)`) |
| Label with icon | **12.3 sp** (`lm1.f(0x100000000L, 12.3f)`) |
| Label without icon | **14 sp** (`lm1.d(14)`) |
| Font weight | **500** (`u60.h` = `FontWeight(500)`) |
| Text vertical padding | **1.6 dp** (`gf0.b0(..., 0.0f, 1.6f, 0.0f, 0.0f, 13)`) |
| Tab row horizontal padding | **4.0 dp** (`gf0.a0(..., 4.0f, 0.0f, 2)`) |
| Docked tab item height | **64.0 dp** (`xh1.c(..., 64.0f)` in `jv0.b`) |

The 23 dp is the icon size (`Modifier.size(23.dp)`), not padding. The tab row itself is drawn after the glass layer so labels are not blurred.

## 7. Obfuscated helper map (actual constants vs helpers)

| Obfuscated call | What it actually is | Consequence |
|---|---|---|
| `f31(50.0f)` | `CornerSize(percent = 50)` | **50%**, not 50 dp |
| `la1(...)` / `ma1.a` | `RoundedCornerShape` / cached 50% shape | pill shape |
| `zt.u` (`ra0(2)`) | `RectangleShape` | selector intrinsic radius 0, clipped by pill |
| `zt.P(a, b, null, 4)` | `spring(dampingRatio = a, stiffness = b)` | motion values above |
| `zt.g(nd, px)` | RenderEffect blur modifier | **12 dp** actual blur |
| `ws.F(nd, a, b, true, z)` | custom lens shader | `refractionHeight = a`, `refractionAmount = b`, depth = true, chromatic = z |
| `vl.a(nd)` | saturation `ColorMatrixColorFilter` | matrix above; saturation 1.5 |
| `y90` / `da0` | custom highlight / highlight style | width, blur, alpha, angle, falloff |
| `tl.b(color, alpha)` | `Color.copy(alpha)` | tint/selector/reflection alpha |
| `gf0.I(long)` | `Color(long)` | `0xFF121212` / `0xFFFAFAFA` |
| `bq0.R(float)` | `roundToInt` | selector/velocity rounding |
| `i9.b` | `animateFloatAsState` | press/stretch/panel |
| `a12.a(i, 0.01f)` | `Animatable(initial, visibilityThreshold = 0.01)` | resting position |
| `g10.a` / `vt(0.4f, 0.2f)` | `CubicBezierEasing(0.4, 0.0, 0.2, 1.0)` | tap settle easing |
| `u60.h` | `FontWeight(500)` | label weight |
| `lm1.f(0x100000000L, 12.3f)` | `12.3.sp` | label with icon |
| `lm1.d(14)` | `14.sp` | label-only |
| `xh1.b` / `xh1.c` / `xh1.h` | `fillMaxWidth` / `height` / `width` | pill and selector geometry |
| `xh1.e` / `gf0.a0` / `gf0.b0` | `Modifier.size` / padding modifiers | 23 dp icon size, 4 dp row, 1.6 dp text |
| `e41.a(9)` | haptic feedback type 9 | crossing haptic |

## 8. What cannot be extracted statically

- **Rendered appearance after compositing.** The literal tint alphas, blur and lens parameters are exact, but the final pixels depend on the wallpaper, theme, OEM GPU, and API level. The screenshot is a visual reference, not a pixel-value source.
- **Runtime shader availability.** The lens requires API 33+ and blur requires API 31+ in this code. Behaviour on older API levels is a code path, not a measured render.
- **`BarBlurRadius` intent.** The field is named "blur radius" but is constant-folded into the highlight angle. There is no static evidence that 40.0f was ever intended as a blur radius; the actual blur is 12 dp.
- **`PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` consumer.** The string is defined but has no smali reference in the APK. It may be dead code or consumed by a path not present in this build; do not guess.
- **Border and shadow.** No `BorderStroke`, `drawBorder`, elevation, or shadow call exists in the ColorDuo navigation path. Any border/shadow seen in a screenshot comes from the highlight/rim or the system, not a measured nav token.
- **Haptic platform name.** Type 9 is passed to `View.performHapticFeedback` on API >= 30; the symbolic Android constant name for 9 is not asserted here.
- **Docked selected-tab styling.** `DockedNavigationBar` uses `jv0.b` for the tab items; its selected-state internals were not decompiled to a token list because it has no pill/selector glass. The bar tint and blur are measured; item-level selected colours are not.
- **Exact `SettingsTab` count at runtime** is not dynamic: the enum has three entries in 1.1.7. The width formula is written for any count so Duo's planned four tabs can use it.

## 9. How to port to Duo (mapping only, no code changes)

Existing Duo tokens live in `app/src/main/java/io/github/kvmy666/duostatusbar/ui/GlassSurface.kt` (`GlassTokens`), `DuoShell.kt` (`LensNavigationBar`), and `DuoDesign.kt` (`DuoRadius`).

| Duo token / value | ColorDuo 1.1.7 value | Mapping note |
|---|---|---|
| `GlassTokens.SATURATION = 1.5f` | saturation 1.5 (matrix above) | **Already exact**; no change. |
| `GlassTokens.NavigationBlur = 36.dp` | **12.0 dp** | ColorDuo's nav glass is much thinner. Exact match means a 12 dp nav blur; keeping 36 dp is a deliberate Duo look, not a ColorDuo match. |
| `GlassTokens.LensHeight = 14.dp`, `LensAmount = 26.dp` | liquid **24 / 24 dp**; floating **16 / 32 dp** | Add separate navigation lens tokens; do not reuse the card values. Duo's selector already uses 16/32, which matches ColorDuo floating. |
| `GlassTokens.NavigationTint = DuoColors.Background.copy(alpha = 0.72f)` | **`#121212` @ 0.40 dark / `#FAFAFA` @ 0.40 light** | Use the measured colours/alphas for an exact match; 0.72 is Duo-specific. |
| `GlassSurface(shape = RoundedCornerShape(50))` | **50%** | Already exact. |
| `LensNavigationBar.pillHeight = 66.dp` | **64.0 dp** | Set 64 dp for liquid. |
| `restHeight = 57.dp` | **56.0 dp** (pill - 8) | Set 56 dp. |
| `restWidth = 71.dp` / `heldWidth = 94.dp` | **`(availableWidth - 8.dp) / tabs`** | ColorDuo does not expand the selector to 94 dp; width is tab-slot based. For Duo's 4 tabs, use `(maxWidth - 8.dp) / 4`. |
| `GlassTokens.NavigationBorder` gradient | **no border** | Exact ColorDuo match sets `borderBrush = null`; the rim is the highlight. Keeping the gradient is a Duo identity choice. |
| `GlassSurface` 18 dp / 14% shadow | **no shadow** | Exact match removes the shadow; Duo's shadow is extra. |
| `spring(0.76f, 420f)` settle | **0.78 / 420 / 0.01** | Close; add visibility threshold 0.01. |
| `spring(0.66f, 420f)` tap | **240 ms tween + 90 ms delay, `CubicBezier(0.4, 0, 0.2, 1)`** | ColorDuo uses a delayed tween, not a spring, for tap settle. |
| press / stretch / panel springs | **0.7/500, 0.8/350, 0.75/300** | Add if matching ColorDuo's press/stretch/panel feel. |
| no crossing haptic in Duo | **type 9 on rounded-index crossing** | Add `View.performHapticFeedback(9)` on crossing. |
| Duo selector separate lens (4 dp blur, 16/32) | ColorDuo liquid uses **12 dp glass + 16% tint + rim**; floating uses **0.18/0.08 selector** | Choose one: keep Duo's selector lens (closer to floating) or add ColorDuo's tint + highlight for liquid. |
| Duo icon 18 dp, `labelSmall` | **23 dp icon size, 12.3 sp with icon / 14 sp label-only, FontWeight 500** | Adjust icon/label tokens; text vertical padding 1.6 dp, row horizontal 4 dp. |
| no reflection | **white 0.16 * press gradient, 1.5x height, 8 dp inset** | Add only in liquid mode. |

Do **not** port `BarBlurRadius = 40.0f` as a blur value. Do **not** port `PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` as a live shader unless a consumer is found.

## 10. Evidence index

Primary smali (apktool output):

- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/SettingsNavigationKt.smali:15` - `BarBlurRadius` field = 40.0f.
- `...SettingsNavigationKt.smali:17` - `PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` exact string.
- `...SettingsNavigationKt.smali:2780-2908` - `LiquidNavigationBar`: 50%, `0xFF121212`/`0xFFFAFAFA` @ 0.4, `0.47805998`, 64/24/24, true/true.
- `...SettingsNavigationKt.smali:2105-2245` - `FloatingNavigationBar`: 0.36/0.38, `0.62`, 56/16/32, false/false.
- `...SettingsNavigationKt.smali:350-450` - `DockedNavigationBar`: 0.58/0.62, full width.
- `...SettingsNavigationKt.smali:3857` - `0.07f` width-fraction increment.
- `...SettingsNavigationKt.smali:6292-6310` - selector `(jh.b() - 8)/tabs`, `height - 8`, white 0.18 / black 0.08.
- `...SettingsNavigationKt.smali:6514-6560` - selector width/height and alpha.
- `...SettingsNavigationKt.smali:7193-7301` - liquid full-width tint at 0.16.
- `...SettingsNavigationKt.smali:7608-7706` - dynamic highlight 0.8/0.25/0.65/0.25/40/45.
- `...SettingsNavigationKt.smali:8666-8705` - static highlight 0.75/0.78/0.96.
- `...SettingsNavigationKt.smali:8604-8625` - vibrancy call, 12 dp blur, lens `ws.F`.
- `...SettingsNavigationKt.smali:8072-8100` - selector offset formula.
- `...SettingsNavigationKt.smali:10150`, `10317`, `10339`, `10393`, `10402` - 23 dp icon, 12.3 sp, 14 sp, 1.6 dp, 4 dp.
- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/NavigationDragKt.smali:627-635` - haptic `0x9`.
- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/NavigationDragState$settleTo$1.smali:384-423` - 240 ms and spring 0.78/420/0.01.
- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/NavigationDragState.smali:152` - resting threshold 0.01.

JADX source:

- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/SettingsNavigationKt.java:13,15` - fields.
- `...SettingsNavigationKt.java:281-310` - liquid literals.
- `...SettingsNavigationKt.java:1040-1120` - selector width/height/alpha and offset.
- `...SettingsNavigationKt.java:1120-1200` - liquid glass wiring.
- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/NavigationDragState.java:166,250-330` - resting/finish/move.
- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/NavigationDragKt.java:183` - haptic call.
- `/tmp/colorduo-jadx/sources/defpackage/vl.java:8-9` - vibrancy matrix.
- `/tmp/colorduo-jadx/sources/defpackage/y90.java:8-11,30-40` - highlight defaults/factory.
- `/tmp/colorduo-jadx/sources/defpackage/da0.java:9-15` - white 0.5 shader, angle/falloff.
- `/tmp/colorduo-jadx/sources/defpackage/aa0.java:29-60` - stroke width/alpha/blur.
- `/tmp/colorduo-jadx/sources/defpackage/ws.java:44-100` - lens uniforms and dispersion shader.
- `/tmp/colorduo-jadx/sources/defpackage/zt.java:1665-1678` - blur modifier.
- `/tmp/colorduo-jadx/sources/defpackage/zt.java:615-624` - `tween` factory; `V(240, 4, g10.a)` yields 240 ms + 90 ms delay.
- `/tmp/colorduo-jadx/sources/defpackage/xh1.java:47-49` - `xh1.e` = `Modifier.size` (23 dp icon).
- `/tmp/colorduo-jadx/sources/defpackage/f31.java` - percent corner size.
- `/tmp/colorduo-jadx/sources/defpackage/ma1.java` - cached 50% shape.
- `/tmp/colorduo-jadx/sources/defpackage/vt.java` - CubicBezierEasing(0.4, 0.0, 0.2, 1.0).
- `/tmp/colorduo-jadx/sources/defpackage/u60.java` - FontWeight(500).
- `/tmp/colorduo-jadx/sources/defpackage/lm1.java:33-80` - sp text units.
- `/tmp/colorduo-jadx/sources/defpackage/jv0.java:256` - docked tab item 64 dp.
- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/SettingsTab.java` - three tabs.

REA evidence IDs retained in this session (JADX headless, APK digest above):

- `ev_4e3e430d6aac2603dfc076e6f06a00bc2dcfcd2b33a65fe5462d3770b60ae535` - class inventory (fields/methods).
- `ev_92741a71cbd17bfb77836743b11123f06d38fa567f583cd97e02f915e6c6c418` - `LiquidNavigationBar` body.
- `ev_284457fd8222450bf509521f44521701410619822bcbb75ebc87042109062378` - `FloatingNavigationBar` body.
- `ev_e9a38419016815e42fa2ccb470444af81d522b0554ca00c5ff686e36273ea571` - `DockedNavigationBar` body.
- `ev_918f2b4634acebd43c64de3d7b9ee61ab8acfa3d30ebf5a8f856bcac7925632b` - `lambda$6` (vibrancy/blur/lens).
- `ev_bb0d62bb13195f9af49386b48c12e4efc12f739f093e19e3e12911d1f28ae853` - `lambda$7` (static highlight).
- `ev_9fb5e4db5672818f85b5a7fe430992eaf925261ef1777dd12d850a9e78580626` - `lambda$12$0$12` (dynamic highlight).
- `ev_805bf0e3f48906f308fe932ea273d821615ec555f5968cbc2efa840f27c1af81` - `lambda$10` (reflection).
- `ev_9efe3497af1943f518b841412e6758cc1113fe2adc000b874e935e7ee100e253` - stretch X.
- `ev_ea259cbced22441dd1e700bd1667a34e1879640cb38895ec613125049e8ac091` - stretch Y.
- `ev_3497dcce4afff51318ca25db0a9732dac23912f6c02ba54bde1131ec473d22ed` - press scale.
- `ev_d6d0c08408aa196edaa27887dafec45a9c452bb09dead54e1c65080e5a20287f` - selector offset.
- `ev_6b7b8df852747bcedee3d5cf7cba9c1ddfe5a5a6c9214a462c48f2a2ada8f714` - tab row lambda.

Screenshot: `/tmp/colorduo-screen/screen.png` - 1272 x 2772, three-tab liquid layout; used only as a visual reference.
