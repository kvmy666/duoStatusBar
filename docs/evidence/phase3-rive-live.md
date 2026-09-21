# Phase 3 evidence: Rive is live in the status bar

Date: 2026-09-21. Device: OnePlus 15 / OxygenOS 16 (`192.168.100.245`). Reproduce with
`./tools/duo-verify.ps1 -Stage rive`.

## Result

`duo-verify.ps1 -Stage rive` reports **all 9 claims PASS**, and the element is the real Rive drawing, not
the Canvas fallback:

```
status bar window found: com.android.systemui.statusbar.window.StatusBarWindowView
ROM adapter: coloros (OxygenOS / ColorOS) - measured on OxygenOS 16 (CPH2747): system_icons -> LinearLayout, battery 83x61 px
container system_icons -> LinearLayout
Rive runtime ready: defaultRendererType=Canvas
element: Rive (stage 2)
hardware accelerated, runtime: true
verdict: a TextureView can get a Surface here - Rive is worth attempting (stage 2)
Duo attached on attempt 0
status -> app: stage=2 · renderer=Rive · attached=true · riveAttempts=1
Duo view ready (machines=1, inputs=[])
stock status-bar views removed (GONE + 0x0), not overlaid - FR-08
Duo injected into LinearLayout (83px wide, 100%)
```

Screenshot: [`verify-rive.png`](verify-rive.png) - top-right shows the Duo ring, the stock icons are gone.

`-Stage off` is also green (2/2): `gated off - nothing hooked`, and the screenshot
[`verify-off.png`](verify-off.png) shows the stock Wi-Fi / signal / battery icons back.

## Finding 5: the view model instance binds *after* the view is attached to a window

The previous attempt fell back to Canvas with `riveAttempts=0` and the log line
`no view model instance bound - binds will not run` (`DuoRiveView`). The window fact was fine - the bar **is**
hardware accelerated and the verdict said Rive was worth attempting - so the failure was purely that the
instance was read too early.

`DuoRiveView.start()` built the `RiveAnimationView` and then read
`view.stateMachines.firstOrNull()?.viewModelInstance` **synchronously**. At that moment the view had been
added to the element's `FrameLayout`, but the element itself had not yet been added to the status bar, so the
Rive view was not attached to a window and the state machine had not bound its instance. The value was
therefore always null and the element always fell back to Canvas.

The app preview had already solved this: `DuoPreview.watchForViewModelInstance` polls for the instance after
the view is attached. The status-bar path did not.

### Fix

* `DuoElement` gained `onReady(action)` and `onFailed(action)`. Readiness is now an event, not a synchronous
  read.
* `DuoRiveView.start()` returns true once the **runtime** is usable and the view is constructed; it no longer
  requires the instance. It then polls (`postDelayed`, 25 × 100 ms - the timing measured in the preview) and
  fires `onReady` when the machine binds, or `onFailed` when the polls are exhausted. A `render()` that
  arrives before binding is cached and replayed, so the element never shows a stale first frame.
* `DuoCanvasView` reports ready immediately (unchanged behaviour, now expressed through `onReady`).
* `DuoIconHost.attach()` adds the element, then hides the stock icons and fires the reveal **from `onReady`**.
  If Rive never binds, `onFailed` swaps in the Canvas element, so the slot is never left empty (FR-21).
* `DuoIconHost.reapplyHiding()` refuses to hide the stock icons while the element is not ready: a layout pass
  can arrive before Rive binds, and hiding then would leave a blank stretch of status bar.

## Verify-script fixes found while doing this

* **Log-file rotation.** The script sliced the module log by line count taken from *the previous* file. When
  LSPosed rolled to a new `modules_*.log` across the restart, the count came from the old file and the new
  file was sliced from the wrong place, silently hiding the very lines the checks look for (this is what made
  `element: Rive`, `container system_icons` and `verdict:` appear as failures on the first green run). The
  script now compares the file name and only slices within the same file.
* **`off` stage checks.** The injection checks were demanded even at `off`, where the correct outcome is that
  nothing was hooked. They are now only required for the `icons`/`rive` stages.

## Still open in Phase 3

* Keyguard status bar and landscape: the attach is layout-driven, so it follows a re-inflate, but neither has
  been verified on the device yet.
* DND in `DuoStateMonitor`.
* Visual pass on the reveal bounce and the airplane morph (FR-25) - the state machine reports `inputs=[]`
  because the reveal/airplane are view model properties, which is the intended binding route.
