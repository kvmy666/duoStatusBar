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
* `[ ]` Per-setting looping previews (FR-09) and a CI step running `rive --verify` on every push

## Phase 3 — SystemUI core (FR-03/06/08/21)

* `[x]` `DuoIconHost`: injects into `id=system_icons` and hides the stock views for real (GONE + 0×0,
  re-applied on every layout pass) — container resolution confirmed on the device
  (`container system_icons -> LinearLayout`).
* `[ ]` Keyguard status bar and landscape: the attach is layout-driven, so it follows a re-inflate, but
  neither has been verified on the device yet.
* `[x]` `DuoStateMonitor` + `SystemReaders`: battery/charging/saver, Wi-Fi level, cell level, airplane,
  broadcast-driven (no polling); the mapping is covered by 16 unit tests. DND still to add.
* `[x]` `DuoGuard`: stage gate + death counter in `Settings.Global`, refuses Rive after two deaths.
* `[ ]` On-device verification of stage 1 (Canvas only, no native code) — ready, phone pending.
* `[ ]` On-device verification of stage 2 (Rive). First attempt died natively: `Rive.init` throws on its
  ReLinker step inside SystemUI, so `defaultRendererType` was left **null** and native renderer creation
  segfaulted (nothing catchable in-process — see `docs/evidence/phase3-attempt1-crash.txt`). Fix in:
  `RiveInit` performs those three steps itself with the type pinned to `Canvas`, `DuoSbFacts` measures
  hardware acceleration before any Rive object exists, `DuoGuard` breaks any crash loop.

## Phase 4 — Animation (FR-25)

* `[x]` State machine bound and driven from the view model (`revealRequest`, `airplaneState`) — the runtime's
  own input setters are Kotlin `internal`, so the view model is the only public route.
* `[x]` Reveal timeline: **30 frames at 60 fps = 500 ms** (scale 1 → 1.12 at 100 ms → 1.05 at 300 ms → 1),
  one shot, re-firable on the false→true edge; the state leaves only on the animation's 100 % exit time, so
  FR-25's budget is structural rather than a timing hope. The whole element scales because a single `Node`
  parents everything — that is the "synced group".
* `[x]` Airplane merge/spawn: the wifi arcs collapse (7 frames) then the plane grows (11 frames) =
  **200 ms**, with 80 ms layer mixes. Units confirmed with `rive schema` (animations: frames; transitions: ms).
* `[ ]` Middle-slot choice: the element takes the battery slot's column (83 px, measured) — whether the design
  means a different "middle" still needs re-checking against the two reference screenshots.
* `[ ]` Visual pass on real hardware (reveal bounce + morph). The CLI's `--screenshot` is currently broken
  (CLI 1.1.0, see docs/rive-pipeline.md), so the in-app preview is the only visual surface for now.
* Commit.

## Phase 5 — Settings app (FR-03/09/10/11/16/17/28)

* Compose Material 3, full Red Wine tonal palette, search, every toggle with an infinite preview,
  layout editor (drag), percentage toggle, donate button, diagnostics + log export. Commit.

## Phase 6 — Auto Expand integration (FR-05/18/27)

* Shared gesture contract, coexistence flag, per-side ownership, conflict matrix documented. Commit.

## Phase 7 — Multi-ROM hardening (FR-01/02)

* ROM adapter layer with capability detection (AOSP / ColorOS-OOS / HyperOS), safe-mode. Commit.

## Phase 8 — Shade header (FR-07/25)

* Optional replacement of QS / notification-header icons; Duo / notifications / both. Commit.

## Phase 9 — Release (FR-01/28)

* README (badges, your GIFs, download count, bug report via Issues + Telegram @kvmy1,
  Buy Me a Coffee → paypal.me/kroomfahd), CI workflow → GitHub Release + Xposed-Modules-Repo. Commit + tag.

## Human-in-the-loop steps (you)

| Phase | What only you can do |
|---|---|
| 0 | Install the APK, enable the module in LSPosed (scope **System UI**), reboot, keep the phone connected |
| 3-4 | Look at the screen and tell me what looks wrong; record short clips |
| 5 | Review the settings UI on the phone |
| 6 | Have Auto Expand installed while testing taps |
| 9 | Record the final GIFs/screenshots for the README |
