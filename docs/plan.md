# Duo Status Bar — build plan

Small phases, executed incrementally; each phase ends with a commit, and a phase only counts as
successful when its own tests pass **and** the stock status bar still works with the module disabled.
Requirement checkmarks live in [`REQUIREMENTS.md`](REQUIREMENTS.md).

## Phase 0 — Ground truth ✅ in progress

**Goal:** replace every assumption with evidence from the device, and get a building module.

* `[x]` Git repo, module skeleton, Gradle 8.13 + AGP 8.13.2 + JBR 21 (same toolchain as Auto Expand)
* `[x]` Tooling: adb (TCP `192.168.100.245:6666`), jadx 1.5.6, **Rive CLI 1.1.0** (local RML docs)
* `[x]` Device SystemUI pulled (`reverse/SystemUI-device.apk`, MD5 `A6CD5483…`, 108 MB)
* `[x]` Evidence collector written: `ProbeHook`, `ViewProbe`, `ProbeReport` (P-01…P-05)
* `[x]` Docs: `DESIGN-duo.md`, `REQUIREMENTS.md`, `dependencies.md`, `architecture.drawio`
* `[ ]` Debug APK built → installed → module enabled in LSPosed → reboot → probe report captured
* `[ ]` `docs/devicereport-oos16.md` written from real logs, class/method names confirmed

**Exit criteria:** `adb logcat -s DuoSB` shows the P-01 inventory, the P-02 hierarchy, the P-03
Rive verdict and P-04 events; SystemUI runs normally. Commit.

## Phase 1 — Design lock & cloning references

* Clone/study `StatusBarLyric` (view injection into the status bar), `customiuizer`/`XMiTools`
  (real icon hiding), `lingyired/status-trio` (Duo geometry cross-check).
* Measure the status-bar slot in portrait/landscape from the P-02 dump; fill the "still to measure"
  table in `DESIGN-duo.md`.
* `docs/test-cases.md` reviewed by you. Commit.

## Phase 2 — Rive asset pipeline (FR-04/24/25)

* `rive create duo` → RML artboard with the exact geometry from `DESIGN-duo.md`.
* State machine inputs: battery, charging, saver, wifiLevel 0-3, cellLevel 0-4, airplane, dnd,
  showPercent, middleSlotChoice; reveal timeline (the ≤ 0.5 s sequence); airplane morph.
* Per-setting looping previews for the app (FR-09). `--verify` + `--screenshot` + Luau tests in CI. Commit.

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
