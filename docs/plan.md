# Duo Status Bar — build plan

Small phases, executed incrementally; each phase ends with a commit, and a phase only counts as
successful when its own tests pass **and** the stock status bar still works with the module disabled.
Requirement checkmarks live in [`REQUIREMENTS.md`](REQUIREMENTS.md).

## Phase 0 — Ground truth ✅ DONE

**Goal:** replace every assumption with evidence from the device, and get a building module.

* `[x]` Git repo, module skeleton, Gradle 8.13 + AGP 8.13.2 + JBR 21 (same toolchain as Auto Expand)
* `[x]` Tooling: adb (TCP `192.168.100.245:6666`), jadx 1.5.6, **Rive CLI 1.1.0** (local RML docs)
* `[x]` Device SystemUI pulled (`reverse/SystemUI-device.apk`, MD5 `A6CD5483…`, 108 MB)
* `[x]` Evidence collector: `ProbeHook`, `ViewProbe`, `ProbeReport` (P-00 … P-05)
* `[x]` Docs: `DESIGN-duo.md`, `REQUIREMENTS.md`, `dependencies.md`, `architecture.drawio`, `install.md`
* `[x]` Debug APK built → installed → module enabled → probes captured
* `[x]` `docs/devicereport-oos16.md` written from real logs (incl. the full status-bar tree)

**Headline results:** Rive **can** render inside SystemUI (3/3 native libraries loaded); the real icon strip
is `id=system_icons` → `statusIcons` (Wi-Fi `wifi_combo`, mobile `mobile_combo` ×2) + `StatBatteryMeterView
id=battery`; 18/24 candidate classes exist with their real method names; 6 AOSP names confirmed absent.

## Phase 1 — Design lock & cloning references ✅ DONE

* `[x]` References studied: `lingyired/status-trio` (Duo geometry), `StatusBarLyric` (view injection),
  `customiuizer`/`XMiTools` (icon hiding patterns)
* `[x]` Geometry measured from your 2 screenshots **and** the device (px + dp tables in `DESIGN-duo.md` §6)
* `[x]` `docs/test-cases.md` written (groups A…G, per-feature, with linked-test rules)

## Phase 2 — Rive asset pipeline (FR-04/24/25) 🚧 nearly done

* `[x]` `rive/duo/` project driven entirely from text by the Rive CLI (verify / inspect / screenshot)
* `[x]` Rive's trim semantics calibrated by measurement (`rive/_calib`) — `docs/rive-pipeline.md`
* `[x]` **Geometry complete**: ring (fill + 22 % track), top gap for the value, 2 Wi-Fi arc layers + dot,
  4 cellular spheres, charging bolt, airplane glyph, percentage digits (bold, optically centred)
* `[x]` **View model with 17 properties**, all bound: ring trims, tint + foreground colours, track
  opacity, percentage text / opacity / font size, bolt / airplane / Wi-Fi / cell opacities
* `[x]` **State machine with 2 layers and 2 inputs**: `reveal` (trigger, re-fires the 500 ms bounce)
  and `airplane` (bool → the morph where arcs slide to 6 o'clock, shrink to nothing and the plane
  scales up out of the same point)
* `[x]` `duo.riv` (397 KB) rendered **live on the device** in the app preview — asset pipeline proven
  end-to-end, including the Rive runtime init that the library does not do for you
* `[x]` In-app controls to fire `reveal` / toggle `airplane` so the morph can be eyeballed on the phone
  (`DuoPreview`: battery, charging, saver, airplane, Wi-Fi, cell, replay-reveal — driving the same
  `DuoMapping` and the same `DuoBinder` the status bar uses, on the same `Canvas` renderer)
* `[x]` Per-setting looping previews (FR-09, landed in Phase 5) and a CI step running `rive --verify` on
  every push (`.github/workflows/ci.yml`, which also diffs `rive/duo/build/duo.riv` against the committed
  `app/src/main/res/raw/duo.riv`)

## Phase 3 — SystemUI core (FR-03/06/08/21)

* `[x]` `DuoIconHost`: injects into `id=system_icons` and hides the stock views for real (GONE + 0×0,
  re-applied on every layout pass) — container resolution confirmed on the device
  (`container system_icons -> LinearLayout`).
* `[x]` **Lock screen (FR-03b).** The status bar is not one bar — the device's SystemUI has three, each
  with its own icon strip (`status_bar`, `keyguard_status_bar`, `combined_qs_header`), and only the first
  was handled, so the lock screen showed the element *and* the keyguard's own stock icons. The keyguard bar
  is now a slot of its own (its own element, its own hiding pass) and is verified on the device. Two bugs
  fell out of it: the element was being centred on the whole shade *window* (the entire screen) rather than
  its bar, and the Rive breaker counted *creations* rather than deaths, so three bars tripped it and every
  bar silently fell back to Canvas.
* `[ ]` **Shade header (FR-03b), parked.** The pulled-down shade's header is `combined_qs_header`, inflated
  from a `qs_header_stub` ViewStub; its icon area is `AlphaOptimizedLinearLayout #icons` →
  `StatusIconContainer #statusIcons`. The element is injected there, but the ROM repopulates the container
  after every hiding pass, so the icons come back. Hooking `StatusIconContainer.addView` to hide them as
  they arrive does not fire, so the insertion route is still unknown — one diagnostic line per icon class
  is in place to name it. Its carrier group also has `mobile_combo` views outside `#icons`.
* `[ ]` Landscape: the launcher is portrait-locked, so this needs the user to allow rotation first.
* `[x]` `DuoStateMonitor` + `SystemReaders`: battery/charging/saver, Wi-Fi level, cell level, airplane,
  **DND/silent** — all broadcast-driven (no polling); the mapping is covered by 29 unit tests.
  The DND crescent is the middle slot's second occupant (FR-06): it is **extracted from the device's own
  `stat_sys_dnd`** (not redrawn) by `tools/dnd-moon-to-rive.py`, which resamples its own output against the
  source and refuses a mismatch. Verified on the device (`docs/evidence/phase3-live-render-and-dnd.md`).
* `[x]` **Live rendering fixed and verified on the device.** The element used to freeze after its first
  frame. The root cause was in the `.riv`: `LinearAnimation.loopValue` defaults to `oneShot`, so the idle
  animations finished immediately, the state machine stopped advancing, and both the binds and the
  renderer's loop stopped - every later change was written but never drawn. The idle animations now
  `loopValue="loop"`, and `DuoRiveView` plays the machine and restarts the renderer's loop after each
  snapshot. Confirmed live: charging shows the bolt + green ring, DND swaps Wi-Fi for the crescent, both
  revert on release. Two dead ends are recorded: calling `draw()` directly raced the loop and killed
  SystemUI (the breaker then refused Rive), and a layout feedback loop was rendering at frame rate.
* `[x]` `DuoGuard`: stage gate + death counter in `Settings.Global`, refuses Rive after two deaths.
* `[x]` Stage 1 was reached on the device: `Duo attached on attempt 0` + `renderer=Canvas` + `attached=true`
  (LSPosed log, 2026-09-21 13:39). The icon-hiding line and the visual check come from the next run, once
  the logging sink fix below is on the device.
* `[x]` On-device verification of stage 2 (**Rive is live**): `duo-verify.ps1 -Stage rive` is 9/9 PASS and
  the element is the real Rive drawing (`renderer=Rive`, `Duo view ready`, screenshot
  `docs/evidence/verify-rive.png`); `-Stage off` restores the stock icons (`verify-off.png`). Getting there
  took three device-found faults, each now fixed and documented:
  * the first attempt died natively: `Rive.init` throws on its ReLinker step inside SystemUI, so
    `defaultRendererType` was left **null** and native renderer creation segfaulted (nothing catchable
    in-process — see `docs/evidence/phase3-attempt1-crash.txt`). `RiveInit` now performs those three steps
    itself with the type pinned to `Canvas`, `DuoSbFacts` measures hardware acceleration before any Rive
    object exists, and `DuoGuard` breaks any crash loop.
  * every diagnostic line went through `android.util.Log`, which OxygenOS **drops from SystemUI**, so all the
    reasons were invisible and every check reported FAIL. `L` is now the only logger
    (`tools/route-module-logs.py` enforces it) and `tools/duo-verify.ps1` reads LSPosed's log file, the sink
    that survives.
  * the element still came back as Canvas: the view model instance was read **synchronously** in
    `DuoRiveView.start()`, but Rive binds it only *after* the view is attached to a window. Readiness is now
    an event (`onReady`/`onFailed` with a 25 × 100 ms poll, mirroring the app preview), the host hides the
    stock icons and fires the reveal from there, and an element that never binds is swapped for Canvas rather
    than left as a hole.
  See `docs/evidence/phase3-log-sinks-and-fallback.md` and `docs/evidence/phase3-rive-live.md`.

## Phase 4 — Animation (FR-25) — design pass done, verified against the reference ✅ DONE

Design/animation pass driven by the user's review of the live element (2026-09-21). Everything below was
measured, not eyeballed, using the CLI's headless renderer:

* `[x]` **Ring closes when the percentage is off.** The top gap was a fixed 71.3° hole; it is now two bound
  properties (71.3° digits / 55.6° bolt / 0° closed), so the ring is continuous without the number.
* `[x]` **Reveal raised to 4 s** (user asked for 4× the 1 s cut); **animated fill** over 2.4 s, from 0 on
  first attach.
* `[x]` **Wi-Fi re-authored to match the Apple glyph.** Two real bugs found by measuring:
  * `WifiIdle`/`PlaneMorph` *key the arcs' TrimPath* (±58°) and `WifiIdle` loops forever, so the authored
    trim was overridden at runtime and every earlier Wi-Fi edit was a no-op on screen. The keyframes now
    carry the measured values.
  * the Ellipse radius had been set from Apple's outer *edge* instead of its *centreline*, putting both arcs
    a full stroke too far out.
  Verified by overlaying my render on a high-res Apple reference: Wi-Fi-region IoU 0.52 → **0.958**. The dot
  is traced from the reference (`tools/extract-wifi-dot.py`), because a triangle-plus-corner-radius never
  matched.
* `[x]` **Ring halves no longer overlap.** Each half was a window from one gap edge to the other, so with the
  gap closed their round caps met at 12 o'clock — the left body painted over the right, and the track's two
  0.22 caps stacked to 0.46 as a bright blob. Each half is now one window anchored with `offset` and sized by
  `end` (an arc length); the left half grows past 12 o'clock and the right goes to zero, so there is no
  junction to overlap at.
* `[x]` **New DND crescent and airplane**, traced from the supplied art
  (`tools/trace-shape-to-rive.py`), both scaled to the Wi-Fi's measured 49.6 × 36.9 footprint and centred on
  the same point.
* `[x]` **Middle-slot crossfade**: the slot used to cut (the Wi-Fi's opacities and the plane's were written
  in the same frame). `DuoVisual.visual` takes a `middleBlend` and `DuoStateMonitor` tweens it over 200 ms,
  matching the PlaneMorph.
* `[x]` State machine bound and driven from the view model (`revealRequest`, `airplaneState`) — the runtime's
  own input setters are Kotlin `internal`, so the view model is the only public route.
* `[x]` Airplane merge/spawn: the Wi-Fi arcs collapse (7 frames) then the plane grows (11 frames) =
  **200 ms**, with 80 ms layer mixes. Units confirmed with `rive schema` (animations: frames; transitions: ms).
* `[x]` Visual pass on real hardware — and the CLI's `--screenshot` is **not** broken: it renders headlessly
  with `--advance`/`--viewport`/`--data`, which is now the main design loop (`docs/rive-pipeline.md`).
* `[ ]` Middle-slot choice: the element takes the battery slot's column (83 px, measured) — whether the design
  means a different "middle" still needs re-checking against the two reference screenshots.
* `[ ]` A flashlight glyph: mentioned as a possible middle-slot occupant, needs the user to say what it is.

## Phase 4b — Motion system rebuild (FR-25) 🚧 in progress

The four animations above were tweens, not a system. This rebuild makes one: every part on one clock, every
motion caused by something, and a reason attached to each number. Rive-authored, view-model driven, verified
headlessly.

**Philosophy** (each decision below follows from these): one body one beat · motion is a receipt, never an
idle · additive layers, never combinatorial states · one property one owner · ease with intent · geometry in
Kotlin, easing in Rive.

**Architecture — five layers, one machine:** `Body` (arrival/departure) · `MiddleSlot` (Wi-Fi ↔ airplane ↔
DND) · `WifiLevel` and `CellLevel` (blends) · `Charge` (the bolt's journey). Layers run simultaneously, so no
state ever needs a combination of them.

* `[x]` **Phase 1 — the eases are real.** The file had 27 `cubic` keyframes and **zero** interpolator
  children: per `rive docs gotchas` a cubic keyframe with no `CubicEaseInterpolator` child does not ease at
  all, so the entire set had been running linear while looking finished (`--verify` builds it clean). Every
  keyframe now carries its curve. Proved with a control build that had them stripped — the frames differ by
  up to 67k pixels. `tools/check-rive-eases.py` enforces it in CI.
* `[x]` **Phase 2 — the body.** `Reveal` rewritten (0.75 → 1.10 overshoot at 100 ms → 0.97 rebound → damped
  elastic settle at 500 ms), starting at 0.75 rather than 0 because at 140 px a zero-point spawn reads as a
  bubble, and opaque by 100 ms so the element is legible before it settles. `Depart` added (ease-in, 40 %
  shorter, opposite tilt). **The duration is the user's**: 500/750/1000/1250/1500 ms, one timeline at five
  speeds (`AnimationState.speed`), on a slider. `revealMs` carries both the trigger and the duration, which
  also fixes the re-fire bug (the old boolean ended at 4000 ms but cleared at 4060 ms). Departure is tied to
  the screen, not the lock — the lock screen is meant to show the element.
* `[ ]` **Phase 3 — MiddleSlot.** The four-state star (`Off`/`Wifi`/`Plane`/`Dnd`) plus `PlaneMorph`,
  `PlaneUnmorph`, `DndIn`, `DndOut`, driven by `middleMode`. Arcs always retract to the same point first so
  the slot always empties the same way; the plane gets a small back-out, the crescent deliberately gets none
  (overshoot is the language of "notable"; DND should bloom, not pop).
* `[ ]` **Phase 4 — the charge choreography** (the user's idea, 2026-09-21). Plugging in tells a story:
  1. the Wi-Fi fades out where it sits;
  2. the bolt appears **in the middle slot** — the same place, so the eye never has to search;
  3. it rises and shrinks as it travels to its home in the ring's top gap;
  4. the Wi-Fi returns to the slot behind it.
  The bolt is born where the Wi-Fi was and takes its place in the ring. Also here: the tint cross-fade to
  green, and `ChargeOut` (faster, no overshoot — unplugging is an acknowledgement of a loss, not a
  celebration).
  *Ownership:* the Wi-Fi gets a **group node** whose opacity the Charge layer keys, while the arcs keep their
  own bound/blended opacity — two nested opacities multiply, so the fade for the bolt and the signal level
  never fight over one property.
* `[ ]` **Phase 5 — the signal ramps move into Rive.** `BlendState1DViewModel` for Wi-Fi and cellular; delete
  the binds they replace. Easing one bound number makes each sphere cross its threshold in turn, which is the
  design's 40 ms cascade for free — no four extra timelines.
* `[!]` **Phase 6 — the fill into Rive: DEFERRED, fallback in place.** Built it — a `DataConverterGroup`
  [interpolator 0.2 s ease-out, range mapper 0–50 → 0.6631–0.9010 with `clampUpper`] fed by a raw
  `batteryLevel`, and the mirror for the right half. `inspect` confirms the wiring (converterId on the bind,
  both items in the group), but **the CLI's headless renderer does not apply it**: the fill measured identical
  at every level (11260 bright ring pixels at 0/25/50/75/100) and identical across the ease. Since it could not
  be verified before shipping, the fill is back on the host's tween, which is known to work — the fallback the
  user asked for. The converters are removed rather than left as dead weight.
* `[x]` **Phase 7 — docs.** DESIGN §5, the stale timing tables in `rive-pipeline.md` and
  `rive-summary-phase4.json`, FR-25, and NFR-1 (rewritten to "runs on the UI thread, arrival user-configurable
  in 500–1500 ms", because a fixed ceiling cannot hold against the arrival being a user setting).

**Not doing, and why:** no idle breathing, no low-battery heartbeat, no squash-and-stretch, no particles or
glow (fill-rate and memory on SystemUI), no per-sphere timelines, no in-Rive digit counting, no taps in Rive
(that belongs to Auto Expand), no Luau (a WASM runtime in a process that has already died natively).

**Verification:** `--verify` plus two checks `problems` does *not* do — cubic-without-interpolator must stay
0, and an ownership check (nothing keyed by two simultaneously-active layers, nothing both keyed and bound).
Then pose screenshots per state via `--data`/`--advance`/`--viewport=140x140`, `--bench` for the NFR-1 claim,
and on the device `gfxinfo`, `meminfo`, all three surfaces and both orientations.

## Phase 5 — Settings app (FR-03/09/10/11/16/17/28)

* `[x]` Red Wine tonal palette, light **and** dark, expressed in both `colors.xml` and Compose
  (`ui/DuoTheme.kt`) so XML and composables cannot drift apart.
* `[x]` The app ↔ module channel: the app owns the settings and publishes them over an exported provider,
  and the module reads them (see `settings/DuoPrefs.kt` for why a provider and not `Settings.Global` — a
  normal app cannot write global settings). Size, position and the percentage apply **live** on a broadcast;
  an explicit `duo_statusbar_stage` from adb still overrides everything, so the kill switch is untouched.
* `[x]` Settings screen: master switch, Rive/Canvas choice, percentage (FR-16), size and position, a looping
  reveal preview (FR-09, first pass), and diagnostics that show the module's own self-report plus sharing.
* `[x]` Hiding is now reversible: the stock views' original visibility and sizes are remembered, so switching
  the element off restores them instead of leaving an empty stretch of status bar.
* `[x]` Dragging the element (FR-17): a mock status bar in the app hosting the **same Canvas element** the
  phone uses, dragged to set the offset — no native code, so it cannot break the settings app.
* `[x]` **Per-setting looping previews (FR-09)**: every row that has a visible behaviour carries a small
  looping demonstration of its own off → on → off. It morphs two snapshots (`DuoVisual.lerp`) built from the
  same `DuoMapping` the status bar uses, and draws them with the Canvas element - several run at once, and
  each Rive view is a native instance. Wired to: the master switch (element absent ↔ present), the percentage
  (ring closed ↔ gap + digits), and the size slider (min ↔ max). The position row is its own demo already -
  dragging it moves the real element - and the renderer + gesture rows have no visual to demonstrate, which
  is recorded rather than faked.
* `[x]` **Settings search** (filters the sections by label/detail) and **diagnostics as a file** (FR-28):
  the report is written to the app's own `diagnostics/` directory and handed out through a `FileProvider`,
  because a bug report needs the whole log and a shared string gets truncated by chat apps. The donate
  button is in.
* `[x]` Human review of the settings UI on the phone - the demo rows render and loop (verified 2026-09-21).
* Commit.

## Phase 6 — Auto Expand integration (FR-05/18/27)

* `[x]` Shared gesture contract, taken from Auto Expand's own code rather than invented: its privileged
  broadcast (`…ZONE_PRIVILEGED_ACTION` + extra `zone_action_key`), sent with `setPackage("com.android.systemui")`
  — which is where both modules live, so the request is a same-process broadcast with the shape its own
  dispatcher uses (`hook/integration/AutoExpand.kt`).
* `[x]` Ownership split: **Duo draws, Auto Expand acts.** This module implements no second copy of any action,
  so the two can never disagree about what a tap means. The action keys and labels are mirrored in
  `settings/DuoActions.kt`, with the ones needing extra data left out on purpose.
* `[x]` Conflict is opt-in by construction: with the default `no_action` the element installs no touch
  listener, is not clickable, and does not consume a single event — Auto Expand's zones keep working untouched.
* `[x]` Rive's own pointer handling is switched off (`setTouchPassThrough(true)`) so it cannot swallow a
  gesture meant for the hand-off.
* `[ ]` On-device verification with both modules installed at once.
* Commit.

## Phase 7 — Multi-ROM hardening (FR-01/02)

* `[x]` `RomAdapter` + `RomDetection`: the ROM is picked from build identity by a **pure function** (5 unit
  tests), and each adapter carries the icon-strip ids, the battery id, and a `notes` field stating whether the
  values were **measured** or are **unverified** — the project's evidence rule, in code.
* `[x]` Every adapter probes the AOSP spelling first, so a stock-like ROM needs no special case; the runtime
  logs each id it tried and what it found, which is how the next adapter gets written from evidence instead
  of guesswork.
* `[x]` "Safe mode" is structural rather than a flag: an unrecognised ROM simply never resolves the strip, so
  nothing is hidden, the stock bar stays, and the log says exactly what was tried.
* `[ ]` Measure a HyperOS/MIUI device and replace the unverified adapter with real ids.
* Commit.

## Phase 8 — Shade header (FR-07/25)

* Optional replacement of QS / notification-header icons; Duo / notifications / both. Commit.
* Deliberately **not started**: it needs the shade's own view tree measured on the device first, and this
  project's rule is that ids are measured, never guessed. Phase 0 collected the class names; the ids and sizes
  are what a probe run has to add before this can be written.

## Phase 9 — Release (FR-01/28)

* `[x]` README: badges (downloads, release, CI), what it does, the honest status table, install, the kill
  switch, gestures, build instructions, Issues + Telegram @kvmy1, Buy Me a Coffee / paypal.me/kroomfahd.
* `[x]` CI on every push: unit tests, debug build, `.riv` consistency check, provider-authority check, and the
  Rive project checks when the CLI is available.
* `[x]` Tag-driven release workflow → GitHub Release + Xposed-Modules-Repo mirror, with `--latest` forced.
* `[ ]` First tagged release — the Phase 3 device run it was waiting on is done (Rive live), so this now
  only needs the GIFs/screenshots for the listing.

## Phase 10 — User-facing release pass 🚧

The app was written for the project, not for a person. This pass rewrites it for the person.

* `[x]` **Wording.** Every user-facing string is now plain English ("Battery icon", "Appear", "Smooth
  graphics"); the requirement ids, the "mock status bar" and the adb kill switch are gone from the
  screen. The screen is grouped into **Battery icon / Animations / Appearance / Tap actions / About**.
* `[x]` **Animations section (FR-25).** A master switch, an arrival **speed** control, and individual
  switches for **Appear**, **Disappear** and **Charging**. Off means instant, not removed: the charging
  switch is a real `animateCharge` view-model boolean, and the Charge layer takes a direct transition to
  its pose when it is false (verified headlessly — the bolt is in the ring at frame 2 instead of flying).
  Arrival, departure and the ring fill are gated host-side.
* `[x]` **Size needs a restart (FR-03/17).** Resizing the Rive view while System UI was running is what
  took it down, so the module now captures the size once, on attach, and ignores live changes. The row
  says so and carries a **Restart System UI** button; the app asks the module (the only side that can do
  it) to restart, and the module kills its own process so Android brings System UI back.
* `[x]` **Previews that show the thing (FR-09).** The row demos were white-on-light and effectively
  invisible; they now sit on a dark chip. The animation switches use a small **Rive** demo instead of the
  Canvas one, so the arrival bounce, the departure shrink and the charging journey are the real motion.
* `[x]` **Position (FR-17).** The blank strip is replaced by a status-bar-shaped preview (clock, dark
  pill) with the same element the bar uses; dragging anywhere moves it, the value is shown, and there is a
  Reset.
* `[x]` **Release prep.** `versionName` 1.0.0 (`versionCode` 2); `:app:minifyReleaseWithR8` is green, so
  the shrunk build compiles.
* `[ ]` On-device pass: install the release, walk every control, confirm the restart button and the Rive
  demos on the phone.

## Phase 11 — Issue fixes (2026-09-26)

Two open reports, both fixed.

* `[x]` **Issue #5 — "Not working" (HyperOS / Android 16, and stock A17).** The diagnostic log showed the
  module *was* loaded into the SystemUI process (`MainHook loaded into system
  (process=com.android.systemui)`), yet the app reported "never injected". The process on those ROMs
  reports `packageName = "system"`, and `MainHook` matched only `com.android.systemui`, so every hook was
  silently skipped. The identity check now also accepts the process name (`SystemUiProcess.isTarget`,
  unit-tested), which is the stable identity across OEM builds. `MainHook` also logs when a process is
  deliberately out of scope, so the next report can tell the two apart.
* `[x]` **Issue #4 — "Add Shizuku support".** Optional Shizuku path for hiding the stock status-bar icons
  when the module's own view-hiding leaves them behind. The app writes the secure `icon_blacklist` setting
  through a Shizuku `UserService` running as shell/root (`settings/StockIconHider.kt`, `ShellService.kt`,
  `IShellService.aidl`); the merge/remove rules are pure and unit-tested (`IconBlacklist`). Off by default,
  every call guarded, and turning the master switch off puts the icons back. The LSPosed path is unchanged
  and does not depend on Shizuku.
* `[ ]` On-device verification with Shizuku running (grant access, toggle, confirm the icons go and return).

## Phase 12 — Battery drain, colour and icon control (2026-09-26)

* `[x]` **Battery drain (critical, 130 mAh vs ~15 mAh).** Root cause: the Rive state machine's idle
  animations loop forever, so the renderer advanced and drew at frame rate for the life of the SystemUI
  process — including while the element was off screen. `DuoElement.setRenderActive` now stops the
  renderer whenever the element is hidden (screen off / teardown), and `DuoRiveView` pauses it after a
  3 s quiet period, restarting on the next snapshot or reveal. A static element now costs no frames.
* `[x]` **Dynamic colour (FR-15b).** `BarTint` captures the tint SystemUI applies to its own icons
  (`StatusBarIconView.onDarkChanged` / `setIconColor`, ROM-guarded) and the element matches it — black on
  a light bar, white on a dark one — with the system day/night setting as the fallback. An **Icon colour**
  picker offers Auto / Black / White. The ring's default colour follows the foreground too, so the whole
  element flips, not just the text.
* `[x]` **Hide other icons (FR-08b).** A **Hide other status icons** switch (default on = only the ring).
  Off, only the icons Duo replaces — the battery, and the Wi-Fi/cellular icons in the strip, identified by
  `StatusBarIconView.getSlot()` — are hidden, and silent/vibrate/alarm stay visible beside the ring.
* `[x]` **Battery level on first paint.** The monitor started at its default `level = 100` and only picked
  up the real level when the next `ACTION_BATTERY_CHANGED` arrived — which the receiver is not guaranteed
  to get at registration, and which on the device can be minutes apart. Until then the element drew a
  full ring reading 100 %. `DuoStateMonitor` now reads the sticky battery broadcast directly at `start()`,
  so the first frame is the real percentage.
* `[x]` **Fallback alert + reporting.** When Rive cannot draw (did not bind, disabled by the breaker, or
  could not start) the module records the reason over the provider and the app shows a red alert with
  **Send the log on Telegram** (writes the diagnostics file, hands it to Telegram, or opens the chat) and
  **Report it on GitHub**. The reason is logged and cleared at each module load, so a user on an
  unmeasured ROM can hand over the evidence in one press — which is how the next fix gets written.
* `[x]` **Every icon follows the colour, not just the ring (FR-15b).** Only the ring fill (`tint`) and the
  percentage were bound to a colour; the Wi-Fi arcs and dot, the four cellular spheres, the DND crescent,
  the airplane, the network label and the track were all authored solid white, so in light mode the ring
  went black while the rest stayed white. They are now all bound to the `fgColor` view model property
  (the bolt stays on `tint`, matching the Canvas fallback). Verified headlessly by rendering with a red
  `fgColor`: every foreground shape follows it.
* `[ ]` On-device verification of all three (battery over a full charge cycle; colour across a light and a
  dark app; the other-icons switch both ways).

## Human-in-the-loop steps (you)

| Phase | What only you can do |
|---|---|
| 0 | Install the APK, enable the module in LSPosed (scope **System UI**), reboot, keep the phone connected |
| 3-4 | Re-enable **wireless debugging** after a reboot (adbd does not come back on its own), then run `tools/duo-verify.ps1 -Stage icons` and `-Stage rive`; look at the screen and say what looks wrong |
| 5 | Review the settings UI on the phone |
| 6 | Have Auto Expand installed while testing taps |
| 9 | Record the final GIFs/screenshots for the README |
