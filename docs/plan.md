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
* `[ ]` In-app controls to fire `reveal` / toggle `airplane` so the morph can be eyeballed on the phone
* `[ ]` Per-setting looping previews (FR-09) and a CI step running `rive --verify` on every push

## Phase 3 — SystemUI core (FR-03/06/08/21)

* `DuoIconHider`: real removal of the stock icons using the method names confirmed in Phase 0.
* `DuoInjector`: add the Duo view to the status bar and the keyguard status bar.
* `DuoStateBinder`: battery/charging/saver, Wi-Fi level, cell level, airplane, DND.
* `SafetyGuard`: strike counter + auto-disable. Commit.

## Phase 4 — Animation (FR-25)

* Bind the state machine, implement the reveal timeline, verify ≤ 500 ms and the synced group.
* Airplane merge/spawn, middle-slot choice. Commit.

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
