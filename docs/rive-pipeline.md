# Rive pipeline — how the animations are built and verified

Rive is used two ways in this project, and both come from **one** source of truth:

| Where | What | Why |
|---|---|---|
| Inside `com.android.systemui` | the live status-bar element, drawn by the real Rive runtime (proved loadable in Phase 0: 3/3 native libraries) | FR-04 |
| Inside the settings app | the per-setting preview loops | FR-09, FR-24 |

## The project

```
rive/duo/          the real project           -> rive/duo/build/duo.riv -> app assets
rive/_calib/       sacrificial calibration    (kept because it documents Rive's trim semantics)
```

Authoring is text (RML), not a GUI: the CLI compiles, inspects and screenshots, so the animations
can be built and checked without opening the Rive editor.

```powershell
$rive = "$env:USERPROFILE\.rive\bin\rive.exe"
& $rive rive\duo --verify                 # does it compile? (exit 1 on errors)
& $rive rive\duo --once                   # build rive/duo/build/duo.riv
& $rive rive\duo --screenshot --advance=1 # render one frame to build/duo.png and LOOK at it
& $rive rive\duo --test                   # Luau tests in the project
& $rive inspect rive\duo --summary        # what actually got built (counts + problems)
& $rive docs --list                       # the local docs; `rive schema <Type>` for any property
& $rive rive\duo                          # live preview window, rebuilding on save
```

Rules taken from Rive's own agent guide (`rive/duo/AGENTS.md`): look up every type and property with
`rive schema` instead of guessing, and never treat "it compiled" as "it is correct" — a clean build
still hides a bind pointing at a property that does not exist.

## Calibrated Rive semantics (measured on this machine, 2026-09-20)

`rive/_calib` renders three concentric quarters to answer questions the docs do not answer directly:

| Question | Measured answer |
|---|---|
| Where does a closed `Ellipse` path start? | **at 12 o'clock (top)** |
| Which way does it run? | **clockwise** (top → right → bottom → left) |
| Units of `TrimPath.start/end/offset` | fractions of path length; fraction `f` == `f` · 360° |
| Does a trim wrap when `start > end`? | **yes** — `0.75 → 0.10` draws across the top |
| What does `TrimPath.offset` do? | rotates the trimmed window (`start=0 end=0.3222 offset=0.8389` centres a 116° arc on top) |
| Where do stroke effects live? | **inside the `Stroke`**, not beside the path — a `TrimPath` next to `<Ellipse>` makes the build fail with "the built riv could not be re-imported" |
| Paint order | between sibling shapes the first declared paints on top; within one shape the last declared paint paints on top |

**Other traps found while building the first scene**

* Every authored id must be unique across the whole project (two `0:12`s = build error).
* `defaultStateMachineId` must match an id that exists in the file.
* A `TrimPath` declared after the paint it should affect simply does not apply — order matters.
* An empty `<LinearAnimation/>` with no keyframes is not worth keeping; give the state machine a real
  animation or leave the state machine out while iterating on geometry.

## Scene structure (`rive/duo/scene.rml`)

One `Node` (the group) sits at the ring's centre `(60, 61.5)` so the whole element can be scaled as a
body — the reveal animation scales that single node. Children are written relative to it:

| Object | Geometry | Notes |
|---|---|---|
| `ringTrackL` / `ringTrackR` | `Ellipse` 103×103, stroke 8, shape `opacity="0.22"` | the unfilled halves |
| `ringFillL` | trim `0.6631 → 0.9010` | battery 0 → 50 % |
| `ringFillR` | trim `0.0990 → 0.0990` | battery 50 → 100 %, animated |
| `wifiLayer1` / `wifiLayer2` | `Ellipse` 62 / 37, stroke 7, trim `0 → 0.3222`, `offset 0.8389` | signal layers, appear bottom-up |
| `wifiDot` | `Ellipse` 14×12 | the Wi-Fi centre dot |
| `cell1…cell4` | `Ellipse` 11×11 at the measured positions | cellular spheres |

Still to add (same phase): percentage text in the top gap, the charging bolt, the airplane morph, the
view model + data binds, and the reveal timeline. Each addition is verified with `--verify` plus a
screenshot before the next one starts.
