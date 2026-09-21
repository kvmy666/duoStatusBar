# Phase 3 evidence: live rendering, and the Do Not Disturb moon

Date: 2026-09-21. Device: OnePlus 15 / OxygenOS 16 (`192.168.100.245`).

Two things were fixed/added together because they share one root cause. All lines below are from LSPosed's
module log on the device.

## Finding 6: the Rive state machine was never running, so nothing ever repainted

The element drew **one** frame - the bind-time frame - and then never changed again. Battery percentage,
charging bolt and DND all appeared frozen until SystemUI restarted. The writes were fine; the drawing was
not:

```
readback bolt=1.0 dnd=1.0 playing=false machines=0
```

* `bolt=1.0 dnd=1.0` - the snapshot had been written into the view model.
* `machines=0` - the state machine was **not playing**.
* `playing=false` - the renderer's loop was **stopped**.

Two Rive rules combine to make that fatal: a data bind only applies *while a state machine is running*, and
the renderer only draws while it is playing. With the machine idle the loop stops after the first frame, so
every later write landed in the view model and was never drawn.

### Fix (three parts)

* **The real cause was in the `.riv`.** `LinearAnimation.loopValue` defaults to `oneShot`, so the 1-frame
  `Idle` and `WifiIdle` animations played once and finished. A state machine only keeps advancing while it
  has a playing animation, so the machine went idle, `advance` reported no change, the controller dropped
  it from `playingStateMachines`, and the renderer's loop stopped. Both idle animations are now
  `loopValue="loop"`, which keeps the machine - and therefore the binds - alive. This is the fix that
  actually made live updates repaint.
* `DuoRiveView` starts the machine explicitly once the instance binds:
  `rive.play(STATE_MACHINE, Loop.LOOP, Direction.AUTO, true, true)` - `autoplay` alone left it idle
  (`playingStateMachines=0`).
* After each snapshot it asks the renderer to run: `artboardRenderer.start()`. `start()` is idempotent
  (`if (isPlaying) return`) and sets the loop going, which is what `scheduleFrame()` cannot do - `doFrame`
  returns immediately unless `isPlaying`, so scheduling a frame on a stopped renderer is a no-op.

### Two wrong turns, recorded so they are not repeated

* **Calling `artboardRenderer.draw()` directly killed SystemUI.** It raced the renderer's own loop; the
  process died twice and `DuoGuard`'s breaker then refused Rive entirely (`Rive refused: 2 failed attempts
  recorded`). `start()` is the supported entry point; `draw()` is not.
* **A layout feedback loop made it worse.** `hideRemoving` re-applied `layoutParams` on every pass, which
  requested another layout, which fired the layout listener, which called back in - render was being invoked
  at frame rate (thousands of lines a second). `hideRemoving` now no-ops when the view is already
  GONE + 0x0, which ends the loop.

## Finding 7: the DND moon, extracted from the device's own icon

FR-06 wanted DND/silent shown; the design table left it "badge or middle-slot". Per FR-23 this was returned
to the user, who chose the **middle slot** and specified that the crescent must be **extracted from the DND
icon itself**, not redrawn.

* `tools/dnd-moon-to-rive.py` holds the `pathData` of `drawable/stat_sys_dnd`, read from the device's
  SystemUI with `aapt2 dump xmltree`. It parses the SVG cubics, converts them to Rive's polar vertex
  handles (rotation is **radians** - `rive docs format`), bakes in a scale/centre, and **refuses to emit a
  mismatch**: `--check` resamples the rebuilt curve against the source and currently reports
  `max_resample_error=3.97e-15` view units. The same geometry is emitted for the Canvas fallback with
  `--android`.
* `scene.rml` gained a `dndMoon` shape (the extracted vertices) bound to a new `dndOpacity` property
  (0:87). `rive inspect --json` resolves the bind; `--verify` is clean.
* `DuoMapping.visual` treats the middle slot as single-occupancy: airplane wins, then DND, else Wi-Fi.
  `dndOpacity` is 1 only while DND is on and airplane is off. Covered by three new unit tests (29 total).
* `SystemReaders.isDndOn` reads the notification policy's interruption filter, plus a truly silenced ringer.
  Vibrate is deliberately excluded: the moon means "this will not make a sound".
* `DuoStateMonitor` listens for `ACTION_INTERRUPTION_FILTER_CHANGED` and `RINGER_MODE_CHANGED_ACTION`
  (event-driven, no polling) and logs each transition.

Device evidence:

```
state monitor up: ... dnd=true
dnd -> false (middle slot now shows the moon)
dnd -> true (middle slot now shows the moon)
```

`docs/evidence/verify-dnd.png` (DND on) shows the crescent in the ring's middle; `verify-dnd-off.png` shows
the Wi-Fi back.

## Verified live on the device

With the idle animations looping, every state change now repaints without a restart - confirmed from
screenshots of the status bar taken seconds after each change:

* `docs/evidence/bolt-on-zoom.png` / `bolt-off-zoom.png` - charging shows the bolt and the ring turns
  **green** (FR-15), and unplugging brings the percentage and white ring straight back;
* `docs/evidence/dnd-live-on-zoom.png` / `dnd-live-off-zoom.png` - toggling DND swaps the Wi-Fi for the
  crescent and back (FR-06);
* `Duo view ready (machines=1, playing=1)` and `duo-verify.ps1 -Stage rive` 9/9 PASS.

The earlier captures only ever showed the bind-time frame; the "40" and "43" that appeared to update were
re-instantiated elements, not live binds, which is exactly the trap this finding records.

Still open in Phase 3: the **keyguard status bar** (a separate view the module does not attach to, so the
lock screen keeps its stock icons) and landscape.
