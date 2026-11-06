# Duo Status Bar 1.7.0 — stable

Stable release after the 1.7 alpha series. Owner accepted the alpha.12 behaviour
(including the one-frame transition fixes) as the 1.7 acceptance.

## Features

- **Fatal fixes.** The 1.6 crash paths stay closed: no id-less injected child, one
  Rive surface/import, no reparent storm, guarded hooks, and fail-silent fallback to
  the stock bar.
- **Transitions.** Duo crossfades across heads-up, Quick Settings and the shade. The
  outgoing copy is locked during the handoff and the incoming copy is only revealed
  when it is drawable, so there is no one-frame gap, dim frame, size flash or blackout.
- **Classic / 2 SIM.** Both ring designs are selectable and persistent: the old
  four-dot single-SIM ring and the new eight-dot dual-SIM ring with a live battery line.
- **Camera.** The selected design can move onto the camera cutout and bind to the
  camera-ring size; the normal bar, QS, shade and keyguard stay unchanged.
- **Size normalization.** One shared 0…100 logical scale for both designs
  (`physical = 50 + 1.7 × logical`); icon artwork is normalized so the same logical
  size looks identical, and switching designs does not change the size.
- **Per-icon status-bar UI.** Dynamic per-icon hiding built from the slots that exist
  on the device, with an **All** master switch. Hiding the stock battery keeps Duo's
  invisible anchor and does not hide Duo's own art.
- **ColorDuo glass.** Navigation glass and touch behaviour matched to ColorDuo 1.1.7
  from measured values: transparency, blur, edge refraction, rim, shadow, press/drag
  and settle.
- **Cell mapping.** Cellular dots follow the stock level per SIM (4 or 8 dots), with
  raw/max/dots diagnostics; Wi-Fi and VoWiFi mapping corrected.
- **Live preview lab.** Home is the design/customization surface with a full layout
  simulator: charge level, low-power mode, per-dot 4/8 simulation, centre inversion,
  size, position and rotation.
- **Event previews.** Animated Alarm, Flashlight, Screenshot and Unlock previews
  replace the old static rows.
- **Download and burn-in.** PHDP download progress renders in the selected ring
  design, and burn-in protection remains available.

## Known residuals

- **ART JIT platform fault.** A `JitCompileTask::Finalize` SIGSEGV in the app process
  was triggered by Rive preview detach during a window resize. There is no recurrence
  on the current build; treated as platform/mitigated, not an app-side fix.
- **60 fps verification not achieved.** The device encoder caps captures at ~62.8 fps
  VFR, and one capture ran at 28.6 fps. The one-frame transition fixes were accepted
  by the owner from those captures.
- **Other vendor UIs remain unverified.** Only OxygenOS (CPH2747) and HyperOS 3 are
  measured; every other ROM stays `unverified`.

## Verification

- `versionCode = 89`, `versionName = 1.7.0`.
- 449 unit tests across 51 suites, 0 failures, 0 errors, 0 skipped.
- Release build succeeds (`:app:testDebugUnitTest :app:assembleRelease`, lint-vital tasks
  excluded), signed with the release certificate.
- `tools/route-module-logs.py --check`: 0 files to fix, 0 calls left for review.
- `tools/check-rive-eases.py`: 158 easing keyframes, 158 carry a curve, 0 inert.
- APK size: 7,476,736 bytes.
- APK SHA-256:
  `3e15a8a92502f73773c459baffe9c7fae80e19a93417ea4d041126192519a2a6`.
