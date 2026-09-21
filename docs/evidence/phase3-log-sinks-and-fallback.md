# Phase 3 evidence: why the module looked dead, and why Rive did not run

Date: 2026-09-21. Device: OnePlus 15 / OxygenOS 16 (`192.168.100.245`). All lines below are copied from
LSPosed's own module log, `/data/adb/lspd/log/modules_2026-09-21T03:20:05.944639.log`.

## What the module was actually doing

```
13:39:39.804  MainHook loaded into com.android.systemui (process=com.android.systemui)
13:39:39.805  === Duo Status Bar: SystemUI integration ===
13:39:40.153  application ready: com.android.systemui (stage 2)
13:39:40.157  listening for app settings changes
13:39:40.692  status bar window found: com.android.systemui.statusbar.window.StatusBarWindowView
13:39:42.156  MainHook loaded into com.oplus.keyguard.personality.clocks (process=com.android.systemui)
13:39:43.704  Duo attached on attempt 0
13:39:43.705  status -> app: stage=2 · renderer=Canvas · attached=true · riveAttempts=0
```

So the hook, the window discovery, the attach and the status report to the app all worked. The status bar
was *not* untouched: the element was in it, and the stock icons were gone. What the user saw was the
Canvas renderer, not Rive.

## Finding 1: `android.util.Log` is dropped by this ROM, so the module was undiagnosable

`adb logcat -s DuoSB` returns **nothing** on this device, and no `DuoSB` line exists anywhere in the
buffer - although the code that writes them demonstrably ran (the same lines arrive through
`XposedBridge.log`, which is what LSPosed stores in its own log file). OxygenOS ships a filtered logd;
`dumpsys logd` is not even present.

Consequence: every check in `tools/duo-verify.ps1` reported FAIL while the module was working, because it
read `logcat -s DuoSB`. Two whole verification rounds were spent on a false negative (the size of the
screen claims, the stock icons gone, etc. were all invisible).

Fixed in two places, both permanent:
* `L` is now the only logger the module uses (`tools/route-module-logs.py` rewrites `Log.x(TAG, ...)` →
  `L.x(...)`, adds/drops the import, and is idempotent + `--check`-able). `w`/`d`/`v`/`e` all go to both
  sinks now: a dropped warning is worse than no warning, because it looks like silence.
* `tools/duo-verify.ps1` reads LSPosed's log file as its primary source, and keeps logcat as a secondary
  one so the checks still work on ROMs that do not filter.

## Finding 2: at stage 2 the element fell back to Canvas

`status -> app: … renderer=Canvas …` at `stage=2`. `riveAttempts=0` narrows it to the two paths that
return before a native renderer is created, and *both* of those paths log their reason through
`Log.*` - which is why the reason was invisible:

* `status bar window is not hardware accelerated - Rive needs a Surface, using Canvas`
  (`DuoIconHost.riveElement`, `!root.isHardwareAccelerated`) - never attempted, counter untouched.
* `Rive runtime unavailable …` / `no view model instance bound …` / `start failed: …`
  (`DuoRiveView.start` returned false → the attempt is handed back deliberately, counter back to 0).

Both are now visible (Finding 1's fix), and `element: Rive` / `element: Canvas (stage N)` is logged for
whichever one is chosen, so the next run names the reason.

## Finding 3: the stage the user was looking at was not theirs

```
stage = 1        (Settings.Global duo_statusbar_stage)
attempts = 0
```

`stage 1` was left behind by an earlier diagnostic run. Precedence is *by design*: the `Settings.Global`
override beats the app's settings, so the module ignored the app's switch and drew the stage-1 Canvas
element. Toggling anything in the app could not change that. This is documented behaviour (`DuoGuard`),
but it is a trap: nothing in the app said "an override is in force".

## Finding 4: rive-android 10.2.0 has no software-only renderer path

`classes.jar` of `app.rive:rive-android:10.2.0` contains `RiveAnimationView`, `RiveTextureView` and
`RiveViewLifecycleObserver` - and no drawable-based widget. Both packaged views are `TextureView`-backed,
so a `Surface` from a hardware accelerated window is a hard requirement for *any* Rive rendering here.
If the window fact really is "not accelerated", the answer is not a different Rive widget: it is to give
the element its own hardware accelerated window (an overlay positioned on the icon slot) instead of
borrowing the status bar's.

## What the user was actually looking at

`docs/evidence/stage1-canvas-ondevice-1323.png` (captured by the verify script at 13:23, stage 1, while
`duo_statusbar_stage = 1` was in force):

* top-left: the stock clock, untouched - the module only claims the icon strip, not the clock;
* top-right: the Duo element - the stock signal/Wi-Fi/battery icons are **gone** (hidden, not covered), and
  in their place is the Canvas element drawing a ring with **91** in it.

`DuoCanvasView` deliberately draws only what is known exactly: the ring, its dim track, the two progress
arcs and the percentage (or the bolt while charging). The Wi-Fi arcs and the four cellular spheres live in
the `.riv` only - redrawing them here would ship a second, unverified geometry - and `reveal()` is a no-op
in Canvas because the animation is Rive's. So "totally different design, 0 animations" was precisely: the
fallback renderer, doing its job.

## Also measured

* LSPosed scope is correct: `ON io.github.kvmy666.duostatusbar → com.android.systemui`
  (`tools/dump-lsposed-scope.py docs/evidence/modules_config.db`).
* Build and tests green after the logging change: **26 tests, 0 failures**, `assembleDebug` OK.

## Reproduce

```powershell
python tools/dump-lsposed-scope.py docs\evidence\modules_config.db   # enabled + scoped to SystemUI
./tools/duo-verify.ps1 -Stage rive                                   # build, install, stage, restart, verdict
```
