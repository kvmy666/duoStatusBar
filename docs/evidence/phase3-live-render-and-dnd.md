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

### Fix

* `DuoRiveView` now starts the machine explicitly once the instance binds:
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

## Open: visual confirmation of live updates

The wiring is proven - writes land (`readback`), binds resolve (`inspect`), mapping is unit-tested, and the
machine now plays - but the on-device screenshots could not settle whether a live change repaints, because
the phone kept falling back to the keyguard status bar (a separate view the module does not target, Phase 3
open item) between captures. A human look at the home screen is the remaining check:

* plug in the charger - the percentage should become the bolt;
* toggle DND - the middle should become the moon;
* unlock - the 500 ms reveal bounce.
