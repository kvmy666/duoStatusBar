# Phase 5 — Liquid glass + three-page shell (plan)

Branch: `phase/5-liquid-glass`, created from `phase/4-cell-accuracy`.

## 1. Scope (requirements)

| Requirement | Meaning | Test |
|---|---|---|
| CUS-011 / TP-030 | Home, Engine and Settings are three pages with a correct back stack | manual UI |
| CUS-012 / TP-031 | Liquid glass on the app surfaces; API 31+ blur, API 33+ shader, graceful fallback below 31 | screenshots on API 31/33/older + `dumpsys gfxinfo` |
| CUS-013 / TP-032 | User-facing strings remain English | string audit |

The effect is **app-only**. It must never be applied to SystemUI, the status bar, keyguard or shade.

## 2. Dependency audit (measured 2026-10-28)

The plan named `io.github.kyant0:backdrop:2.0.1`. Maven metadata and the published POM show:

- 2.0.1: Kotlin 2.4.10, Compose Multiplatform 1.12.0, packaging `jar` (CMP module).
- 1.0.1: Kotlin 2.2.21, AndroidX Compose foundation/ui 1.9.5, packaging `aar`.
- The project currently uses Kotlin 2.1.0 and Compose BOM `2024.12.01` (Compose 1.7.6).

2.0.1 cannot be consumed by the current Kotlin/Compose stack without a Compose Multiplatform
migration. 1.0.1 still requires Kotlin 2.2.21 and Compose 1.9.5.

**Decision:** use `backdrop:1.0.1` (Apache-2.0) and upgrade Kotlin to 2.2.21 and Compose BOM to
`2025.10.01` (UI 1.9.4; the library raises UI/foundation to 1.9.5). This is a gated spike: if the
upgrade breaks Rive/tests or cannot fetch offline, revert the dependency and implement the same
surface with a small `RenderEffect` wrapper instead of shipping a broken build.

## 2.1 ColorDuo reference (measured 2026-10-29)

Owner pointed at `ColorDuo`; pulled from CPH2747 and decompiled with REA/JADX.

- Package: `io.github.fengwenhua.colorduo`, version `1.1.7` (code 10107), APK SHA-256
  `d7441541b5ac1c4802ca0312ba1a1debf873580efa534cc16c1e69c2c45e8cd2`.
- **The package names are R8-obfuscated**, so the first pass incorrectly said "no library". The
  shader/class surface (`Refraction`, `LocalDropdownBackdrop`, `drawBackdrop`, progressive alpha
  mask) matches **Kyant Backdrop** (the same library we use), bundled or forked into the app and
  renamed to `defpackage`/`ws`/`zt`. This is why the original package search was empty.
- `DockedNavigationBar` / `FloatingNavigationBar` + `NavigationPill`:
  - `BarBlurRadius = 40.0f`.
  - API 33+ progressive alpha mask:
    `uniform shader content; uniform float2 size; blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);`
  - API 33+ refraction shader (`Refraction`) with `cornerRadii`, `refractionHeight`,
    `refractionAmount`, `depthEffect`, optional `chromaticAberration`.
  - Compose `graphicsLayer`/`RenderNode` capture and `RenderEffect.createBlurEffect(...)` +
    `RenderEffect.createRuntimeShaderEffect(..., "content")`, chained with the blur.
  - Measured lens values on the settings page: `16f` height, `32f` amount, `depthEffect=true`,
    `chromaticAberration=true`.
- This is a **progressive, refractive** glass; a plain uniform blur over a gradient is not the same
  effect. Our `backdrop` surface now uses `blur(40f)` + `lens(16f, 32f, depthEffect=true,
  chromaticAberration=true)` on the navigation bar, and keeps a calmer `blur(18f)` for content cards.

## 3. Architecture

- A single `GlassSurface` composable owns shape, tint, border and shadow.
- API gates live in one place:
  - API 33+: allow the runtime-shader/lens path if supported.
  - API 31–32: blur only.
  - <31: translucent fallback without blur; no crash, no invisible surface.
- Blur radius is capped (start 16–20) and measured; no continuous animation over the blur.
- The glass layers are app cards/sheets/navigation only; the module's SystemUI views are untouched.
- Existing Compose screens keep their current data flow; this phase changes presentation, not hooks.

## 4. Work breakdown

| # | Task | Verification |
|---|---|---|
| 5.1 | Dependency spike: Kotlin 2.2.21, Compose BOM 2025.10.01, backdrop 1.0.1 | `assembleDebug`/unit tests |
| 5.2 | Add `GlassSurface` + API capability probe; no-op fallback | Robolectric/unit + manual |
| 5.3 | Apply to Home/Engine/Settings surfaces, keep three-page navigation | TP-030 |
| 5.4 | Cap radius and measure with `dumpsys gfxinfo` on CPH2747 | TP-031 |
| 5.5 | String audit: English only | TP-032 |
| 5.6 | Screenshots API 31/33/older + visual sign-off | owner |

## 5. Test plan

- TP-030: open/back through Home → Engine → Settings; each page keeps state; system back returns.
- TP-031: on API 31 and 33, the glass renders; below 31 the fallback is visible and opaque enough;
  `gfxinfo` frame times stay within the existing budget.
- TP-032: `grep` user-facing strings; no Arabic or mixed-language copy in the app resource set.
- Regression: existing unit suite (319) stays green; Rive binary untouched.

## 6. Exit gate

Three pages approved, glass visible on API 31/33, no fallback crash on older, no jank measurement
regression, and no SystemUI surface touched. No merge until owner visual approval.

## 7. Current status

- Branch created.
- Dependency spike is green:
  - Kotlin upgraded to 2.2.21; Compose BOM `2025.10.01`; `backdrop:1.0.1`.
  - `:app:compileDebugKotlin`, unit tests, and release assembly succeed.
- 5.2/5.3 first pass implemented:
  - `GlassBackdrop` records a dark gradient + two colour blobs into a `LayerBackdrop`.
  - `GlassSurface` draws the backdrop through a rounded shape with `blur(18f)`, a low tint and border.
  - Navigation bar, Home cards, Engine panel, and all settings Cards use the glass surface.
  - The SystemUI module is untouched; glass is app-only.
- 319 tests, 0 failures.
- APK `2a45a60c48332a40829d3d04ce59e8cf133ada8261314d48d4be3d258de66afa`
  (`7,071,160` bytes, `1.5.0-beta.8`, code 38). The navigation bar is a floating pill over the page
  content, with `vibrancy()` + `blur(8dp)` + `lens(24dp, 32dp, depthEffect=true,
  chromaticAberration=true)` and `onDrawSurface` tint. Page content draws behind the bar.
- Pressing/dragging now reacts on the whole pill: scale `1.02`, blur `12dp`, lens `30/42dp`, brighter
  surface. The selector scales to `1.16` while held and follows the finger, then snaps.
- Backdrop docs correction: `blur`/`lens` are **pixel** arguments, so the earlier `16f/32f` was
  ~5/10dp at this density. The current code uses `dp.toPx()` and `vibrancy()`.
- The selector is a moving **capsule the width of a tab** (not a 52dp circle), with
  `lens(10dp, 14dp, chromaticAberration=true)` and a blue/white surface. It is draggable with
  `detectDragGestures`: while held it scales/focuses its lens, follows the finger, and on release
  snaps to the nearest tab with a spring and selects that page. Taps on the bar select directly
  without a Material ripple/box.
- RC-017 fixed the app crash: cards inside the recorded `LayerBackdrop` were recursively sampling the
  same layer. The page content now sees a null local backdrop; only the floating bar samples the layer.
  Device verification: `pidof` alive after launch, crash buffer empty, activity focused.
- Still pending: visual approval/screenshots, `gfxinfo` measurement, string audit, and the API-31
  fallback (the app's minSdk is 33, so only the API 33+ path is currently shipped).
- `1.5.0-beta.4` was installed and launch-verified on CPH2747; `beta.6` was built and relayed after
  ADB went offline, so it still needs installation/visual check.

## 8. Apple-reference implementation (2026-10-29, beta.13)

The owner asked for real Apple numbers, not tuned guesses. The values below are the published iOS 26
design-kit transcriptions (light angle −45°, material opacity 60%, frost 14 px large, depth 16,
layered shadow 0/8/28 at 14%), Apple's regular/thick material ranges (140–160% saturation, 40–48 px
nav blur, 18% tint), and the Kyant Backdrop tutorial (`vibrancy()` = 1.5 saturation, lens 16/32 dp,
effect order colour filter → blur → lens). They live in one place: `GlassTokens`.

| Material | Blur | Tint | Lens | Notes |
|---|---|---|---|---|
| Navigation pill | 40 dp | white 16% | 16/32 dp, depth + chroma | picks up press to +4 dp |
| Settings card | 24 dp | white 12% | none | readable on the dark wallpaper |
| Switch track / slider track | 12 dp | white 10–14% | 6/9 dp | check/slider states |
| Slider thumb | 8 dp | white 82% | 5/7 dp | glass bead |
| Scroll edge | 24 dp | mask | none | `DstIn` gradient mask |

### 8.1 Selector = circle at rest, slime capsule while dragging

The old selector was a tab-width capsule even at rest (the owner's complaint). It is now:

- idle: a 54 dp circle with `RoundedCornerShape(50)`; width == height;
- drag: width grows to 54 + 42 dp and height gives 6 dp, so it becomes a capsule;
- the stretch is driven by distance from the nearest tab plus a small velocity term, so it visibly
  grows and shrinks as it passes tab centres;
- release: spring to the nearest station and spring the stretch back to 0 (circle again);
- the halo follows the selector centre and leads it by 10 dp while dragging.

### 8.2 Every toggle and scroll

- `SettingSwitch` → `GlassSwitch` (glass track + bright bead; Material fallback when no backdrop).
- `LabelledSlider` → `GlassSlider` (glass track, wine fill, glass thumb; semantics kept).
- `DuoPreview` toggle/slider use the same controls.
- Settings scroll gets `ScrollEdgeGlass`: top 72 dp / bottom 92 dp, blurred content masked by a
  `DstIn` gradient inside an offscreen layer, so the edge fades instead of showing a hard rectangle.

### 8.3 Backdrop separation (RC-017 hardening)

`GlassBackdrop` now records two layers:

1. `wallpaper` — gradient only, provided to content/cards/controls;
2. `page` — page content, used only by the floating bar through `rememberCombinedBackdrop`.

Cards and controls therefore sample the wallpaper, not the layer that contains themselves. This keeps
the earlier RC-017 SIGSEGV fix while allowing real glass cards again.

### 8.4 Remaining before sign-off

- Device screenshots of Home/Engine/Settings, selector idle/drag/release, and both scroll ends.
- `gfxinfo` while scrolling and dragging; lower `CardBlur` if frame time regresses.
- Light-mode text screenshot (the shell wallpaper is dark).
- Report severity buckets are now defined in `agent/PHASE-1-5-WALKTHROUGH.md` §6.

### 8.5 Beta.14 build (2026-10-29)

- Version `1.5.0-beta.14`, versionCode 44.
- `:app:testDebugUnitTest`: 319 tests, 0 failures, 0 errors, 0 skipped.
- `:app:assembleRelease`: success.
- APK `7,093,116` bytes, SHA-256
  `e5957a7e6e1fdd12aabc2cdfd134500f7ba8bc0e82edf42495fe31a541a6209a`.
- Adds the iOS 26 layered depth shadow to glass surfaces (18 dp nav, 8 dp cards, 14% ambient/spot).
- `tools/route-module-logs.py --check`: 0 files to fix, 0 log calls left.
- `tools/check-rive-eases.py`: 138 easing keyframes, 138 carry a curve, 0 inert (the tool's
  default path typo `riva/` was corrected to `rive/` in this change).
- ADB saw no device during the build, so this APK is **not installed** yet.


