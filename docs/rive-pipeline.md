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

## Traps found the hard way (each cost a debugging round — do not repeat them)

| Symptom | Cause | Fix |
|---|---|---|
| **Every data bind silently does nothing.** The artboard renders as authored: bound text shows its literal, bound trims stay at their authored value. No error, no warning, `--verify` clean. | The `StateMachine` and its `LinearAnimation` were written as **root elements** (siblings of `<Artboard>`) instead of **children of the artboard**. An artboard only runs a machine declared inside it, and *binds only apply while a state machine is running*. | Move `<StateMachine>` + `<LinearAnimation>` inside `<Artboard>`. Confirmed by measurement: after the move, the bound default instance (`trimRightEnd = 0.3369`) filled the ring's right half exactly, and `100` replaced the authored `50`. |
| `--data Duo/x=…` answers *"no view model is bound to this artboard"* | Same root cause as above (the CLI cannot resolve a bound instance for an artboard with no running machine). | Same fix. |
| 3-digit text wraps to a second line and the third glyph lands in the middle of the icon | `Text` with `sizingValue="fixed"` and too small a `width` wraps like any text box. | Widen the box (`width="80"`), `overflowValue="visible"`, and **bind `TextStylePaint.fontSize` (key 274)** so the app shrinks the font for 3 digits — which is also what iOS does. |
| `DataBindContext has no property "name"` | `DataBindContext` extends `DataBind`, which does not inherit `Component.name`. | Give binds no `name`; identify them by the target property + source path. |
| `interpolationType` rejects `easeOut` / `easeInOut` | Only `hold`, `linear`, `cubic`, `cubicValue`, `elastic`, `scripted` (or an int) are accepted. | Use `cubic` plus `interpolatorId` pointing at a `CubicEaseInterpolator` when a custom curve is wanted. |
| Bound value has no visible effect, and the *authored* value equals the intended bound value | Nothing is wrong — you cannot see a bind whose value matches the literal. | Set the view model's default instance to something visibly different while testing (that is how the missing state machine was caught). |
| **App crashes at startup**: `UnsatisfiedLinkError: No implementation found for long app.rive.runtime.kotlin.core.FileAssetLoader.constructor()` | rive-android 10.x removes its **own** AndroidX-Startup initializer from the merged manifest (`<meta-data android:name="…RiveInitializer" tools:node="remove"/>`), and this version has no `Rive.init()`. Nothing loads `librive-android.so`, so every JNI call fails. | Call the public initializer explicitly: `app.rive.runtime.kotlin.RiveInitializer().create(context)` before the first Rive object. In this project that is `RiveInit.ensure(context)` (reflective, dependency-free — deliberately, because the same call has to work inside SystemUI in Phase 3, where our compile classpath is irrelevant to the host process). |
| On a real device the artboard draws its **authored** literals (e.g. `50`) even though the default instance says `100` | A view model default instance is a **build-time default**: the CLI previewer applies it, a runtime does not. | The host must push every property — which is the design anyway (`DuoStateBinder` sets all of them on each state change). Treat instance values as “what the previewer shows”, never as runtime behaviour. |

## Driving the file from Android (read out of rive-android 10.2.0's own bytecode, not from memory)

The *public* API is narrower than the Rive editor implies, and it decides what the host can control:

| Want to… | Public API | Note |
|---|---|---|
| Initialise the runtime | `RiveInitializer().create(context)` | Nothing does it for you (see the trap table) |
| Set any value the file draws | `stateMachine.getViewModelInstance().getNumberProperty("x").value = …` — and `getColorProperty` / `getStringProperty` / `getBooleanProperty` / `getEnumProperty` | The reason every visual parameter in `duo.riv` is a view-model property |
| Fire the reveal | `getTriggerProperty("reveal").trigger()` | public ✅ |
| Play a one-off animation | `RiveAnimationView.play(name, Loop, Direction, …)` | public, but a plain animation does **not** run binds |
| Set a `StateMachineBool` / `StateMachineTrigger` input | **impossible** | their setters are Kotlin `internal`; bytecode shows only `SMIBoolean.setValue$kotlin_release` and `SMITrigger.fire$kotlin_release` |

**Consequence:** the state machine's transitions are driven by **view model booleans** (`revealRequest`,
`airplaneState`) instead of `StateMachineBool` inputs, using
`TransitionViewModelCondition` → `TransitionPropertyViewModelComparator` → `BindablePropertyBoolean`
(propertyKey 634) → `TransitionValueBooleanComparator`. Everything the host needs is reachable through
public API, while the animations themselves (500 ms reveal bounce, 200 ms airplane morph) still live in
the `.riv`.

### How to verify a bind actually applied (recipe)

Do not eyeball the render. Measure it:

```powershell
# render one frame, then measure the drawn geometry numerically
& $rive rive\duo --screenshot --advance=1
python -c "from PIL import Image; import numpy as np
im=Image.open('rive/duo/build/duo.png').convert('L'); m=np.asarray(im).astype(float)>120
ys,xs=np.nonzero(m)
cx,cy,r=59.5,61.5,51.5                      # ring centre + radius from DESIGN-duo.md
d=np.hypot(xs-cx,ys-cy); band=(d>r-5)&(d<r+5)
ang=(np.degrees(np.arctan2(xs[band]-cx,-(ys[band]-cy))))%360
print([i*5 for i,v in enumerate(np.histogram(ang,bins=72,range=(0,360))[0]) if v==0])"
```

The empty 5° bins are the honest answer: a full ring leaves only the two designed gaps, a 50 % ring
leaves one half empty, and a missing bind looks exactly like the authored value.

**Setup that has to hold for binds to work at all:** `Artboard.viewModelId` → the view model,
`Artboard.viewModelInstanceId` → the instance to show, `ViewModel.defaultInstanceId` → the same
instance, `DefaultInstance` marked `exports="true"`, **and** the state machine inside the artboard.


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
