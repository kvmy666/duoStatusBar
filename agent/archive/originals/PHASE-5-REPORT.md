# Phase 5 report — beta.14 (2026-10-29)

**Prototype lock:** Phase 5 is frozen at `1.5.0-beta.14` (code 44), APK
`e5957a7e6e1fdd12aabc2cdfd134500f7ba8bc0e82edf42495fe31a541a6209a`. The selector physics, glass
tokens, toggle/slider materials and scroll edges are no longer changed by Phase 6–8 work. Later builds
may contain the phase-6 charging motion, but no Phase 5 visual retune.

Build: `1.5.0-beta.14`, versionCode 44, branch `phase/5-liquid-glass`.
APK: `7,093,116` bytes, SHA-256 `e5957a7e6e1fdd12aabc2cdfd134500f7ba8bc0e82edf42495fe31a541a6209a`.

Fixes are sorted by severity, as requested: **critical**, then **moderate**, then **minor**. This is a
build-and-static review; the device has not run this APK yet (ADB was empty during the build).

## Critical fixes

### C-01 — Recursive glass capture (RC-017) hardened
- **Symptom:** opening the app killed it immediately with `SIGSEGV` in `RenderThread` (stack overflow).
- **Cause:** cards were recorded into a `LayerBackdrop` and also sampled the same layer.
- **Fix:** `GlassBackdrop` now records two separate layers: `wallpaper` (gradient only) for content,
  cards and controls, and `page` (content) for the floating bar through `rememberCombinedBackdrop`.
  No glass surface samples the layer that contains itself.
- **Why it matters:** this is the only known crash that takes the whole app down. It gets the critical
  bucket even though the immediate trigger was fixed before beta.12; the new architecture removes the
  mechanism instead of relying on "do not add a card there".
- **Verification:** compile + 319 tests + release build pass. **Device launch is still required.**

## Moderate fixes

### M-01 — Stationary selector was a pill, not a circle
- **User report:** "وقت ما يكون ثابت حقنا وضعيته بيل مو دايري" / "السيليكتور زي السلايم".
- **Change:** idle selector is now a 54 dp circle (`width == height`, 50% capsule shape). While dragging
  its width grows to +42 dp and its height gives 6 dp, so it becomes a capsule; release springs the
  stretch back to zero and the selector returns to a circle on the nearest tab.
- **Slime behaviour:** stretch is driven by distance from the nearest tab plus a small velocity term,
  so it visibly grows and shrinks while crossing tabs.
- **Not yet device-shot.**

### M-02 — Pill was too transparent at rest
- **User report:** "البيل وقت ما أكون مو ماسك السيليكتور شفاف جداً".
- **Change:** the pill is blurred at rest (`12 dp`) and stronger while held (`16 dp`); lens is 24/32 dp
  at rest and 30/42 dp interactive, with the Apple 1.5 saturation / 0.04 brightness / 1.5 contrast.
- **Scope:** `GlassTokens` + `GlassSurface`.

### M-03 — The selector did not use the pill/logos as its backdrop
- **Change:** selector uses `rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)`, is drawn after the
  pill layer, and uses `RoundedCornerShape(50)` so it can overflow the pill without clipping.
- **Effect:** moving the selector refracts the page, the pill and the tab icons, not only wallpaper.

### M-04 — Every toggle was still Material
- **Change:** `SettingSwitch` now calls `GlassSwitch`; `DuoPreview` uses the same control. The track is a
  glass capsule (`12 dp` blur, lens 6/9), the knob is a bright bead, and the checked state is a wine
  tint instead of an opaque fill. Material `Switch` remains only as a no-backdrop fallback.
- **Scope:** all settings toggles (master, percent, bolt, split, live apply, lock ring, animations,
  renderer, clock font, airplane, Wi-Fi dots, events, updates, icon hiding).

### M-05 — Every slider was still Material
- **Change:** `LabelledSlider` now calls `GlassSlider`; the preview sliders use it too. The track is
  glass with a wine fill, the thumb is a glass bead with shadow, and the semantics
  (`progressBarRangeInfo` / `setProgress`) are preserved. Steps still snap.
- **Scope:** percent height, size, edge padding, animation speed, battery/Wi-Fi/cellular previews.

### M-06 — Scrolling had no glass edge
- **Change:** `ScrollEdgeGlass` adds a top 72 dp and bottom 92 dp material over the page, blurred from
  the page backdrop and masked by a `DstIn` vertical gradient inside an offscreen layer. The content
  scrolls under it; the edge fades instead of ending as a hard rectangle.
- **Scope:** app shell only; the SystemUI shade/QS is untouched.

### M-07 — Glass numbers were ad hoc
- **Change:** one `GlassTokens` object now owns blur, saturation, brightness, lens, tint and shape.
  Values come from the published iOS 26 design-kit transcription (light −45°, opacity 60%, frost 14,
  depth 16, shadow 0/8/28 at 14%), Apple's material ranges (40–48 dp navigation, 140–160% saturation,
  18% tint) and the Kyant tutorial (1.5 saturation, lens 16/32, colour → blur → lens order).
  `dp.toPx()` is applied at the draw site; raw `16f` was the earlier flat-glass bug.
- **Scope:** `GlassSurface.kt`, `GlassControls.kt`.

### M-08 — Cards had silently fallen back to Material
- **Change:** with the wallpaper/page split, `GlassCard` uses real glass again at 24 dp with a 12%
  white tint. This is the visual improvement, but also the main performance risk; measure `gfxinfo`.
- **Rollback:** set `GlassTokens.CardBlur` down or restore the Material fallback if frame time regresses.

### M-09 — Glass lacked the iOS depth shadow
- **Change:** `GlassSurface` now applies the iOS 26 layered shadow (0/8/28 at 14%) through Compose
  `shadow` with matching ambient/spot colours; navigation uses 18 dp, cards 8 dp. The shadow is drawn
  before the glass and is not part of the sampled layer.
- **Scope:** `GlassSurface.kt`.

## Minor fixes

### m-01 — `check-rive-eases.py` default path typo
- `riva/duo/scene.rml` → `rive/duo/scene.rml`. The checker had been failing with `FileNotFoundError`
  instead of checking the scene. Now: **138 easing keyframes, 138 carry a curve, 0 inert**.

### m-02 — Documentation
- Added `agent/PHASE-1-5-WALKTHROUGH.md`: the phase-by-phase walkthrough, review matrix, enhancement
  backlog, gap-closure waves and the severity policy.
- Updated `agent/PHASE-5-PLAN.md` with the Apple-reference table, selector physics, control/scroll
  work and the beta.13 evidence.

### m-03 — Code hygiene
- Removed the selector's old `lensX`/`lensScale` path and unused animation import.
- Added `GlassControls.kt` so the switch/slider logic is not duplicated in each section.
- `L.kt` still imports `android.util.Log`; that file is the sanctioned log router, not a violation.

## Verification performed

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 319 tests, 0 failures, 0 errors, 0 skipped |
| `:app:assembleRelease` | success |
| `tools/route-module-logs.py --check` | 0 files to fix, 0 log calls left |
| `tools/check-rive-eases.py` | 138/138 eased, 0 inert |
| `duo.riv` SHA-256 | `540bb0884324b1f4303b22debdfb943cb4ad9bc04ce3fba0cacaa3896269e201` (unchanged) |
| `adb devices` | empty; no install/launch on CPH2747 |
| Telegram relay | HTTP 200, `{"ok":true}`; final ZIP SHA-256 `034d9f52900a3d9bedca8d9c733e6ba01a9209d55938fb062fd55deb100c9b56` |

## Open after this report

- Install beta.13 and capture: selector idle circle, drag capsule, release circle, both scroll ends,
  switch on/off, slider drag.
- `dumpsys gfxinfo` while scrolling/dragging; reduce card blur if needed.
- Light-mode screenshot: the shell wallpaper is still dark, so black text in light mode may be weak.
- Device launch check for RC-017 (no crash, `RenderThread` clean).
- Phase 1–4 evidence walk is still pending; see `PHASE-1-5-WALKTHROUGH.md`.
