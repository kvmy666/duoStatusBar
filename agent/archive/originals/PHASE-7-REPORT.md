# Phase 7 report — multi-ROM maps, adaptive glass and performance

Status: **prototype complete with one open performance blocker on the emulator**. Branch
`phase/7-multi-ui-perf`, from `phase/6-rive`.

## What changed

### ROM profile schema v2 + static maps
- `app/src/main/assets/rom-profiles.json` now carries 9 entries: the two measured maps (OxygenOS,
  HyperOS) and seven explicit **static-only** maps (AOSP, ColorOS, One UI, OriginOS, MagicOS, MyOS,
  Nothing OS).
- Static entries have `"measured": false` / `"shadeMeasured": false`; `RomDetection.forThisRom`
  continues to ignore them for runtime override. They are documentation and test fixtures, not a
  claim of support.
- Each static entry records its container ids, battery/clock ids, AOSP-style shade ids, panel kind
  and the missing measurement in `notes`.
- `RomProfilesTest` now pins: 9 profiles, only `oxygenos`/`hyperos` measured, unknown Samsung still
  falls back to the built-in `samsung` profile.

### Performance capture tool
- Added `tools/check-perf.py`. It resolves `IntendedVsync` / `FrameCompleted` from the gfxinfo header
  (API 36 added `FrameTimelineVsyncId` and shifted the indices, which the first script got wrong),
  skips incomplete rows, and fails closed on an empty capture.
- Budget flags: `--p95` (default 16.7 ms), `--max` (default 50 ms).
- The script was validated against a synthetic fixture and then against the emulator capture.

### Adaptive glass quality
- `GlassQuality` detects the emulator (`FINGERPRINT`/`HARDWARE` ranchu/goldfish) and switches to a
  static preview for controls, scroll edges and surfaces; phones keep the live path and Apple values.
- Reason: the all-live build caused an **ANR at launch** on the API 36 emulator (JIT/RenderThread
  saturated). The static preview removes the ANR; the palette and depth shadow remain.
- `GlassCard` is now a translucent frosted Material card, not a live backdrop. Ten scrolling
  `LayerBackdrop` cards are not affordable, and Apple puts liquid glass on the navigation/control
  layer, not every content surface.

### Static-map resource check
- Added `tools/check-rom-profile-assets.py`; it checks every profile id against the decoded resource
  tree (layouts/public ids), not against runtime behaviour.
- OxygenOS 8/8 and HyperOS 7/7 pass. Six static profiles now pass after removing two guessed ColorOS
  ids and one guessed MagicOS id; AOSP has no decoded tree and stays an explicit error.
- Full result: `agent/ROM-PROFILE-ASSET-AUDIT.md`.

## Measurements

Emulator: `sdk_gphone64_x86_64`, Android 16 (API 36), guest arm64 under x86_64, SwiftShader
`swiftshader_indirect`, no snapshot. Build `1.5.0-beta.20` (code 50).

| Run | Result |
|---|---|
| beta.16–17, all-live cards/controls | ANR at launch; app process alive but no window |
| beta.18, static cards + half blur / no lens | no ANR; 120 frames, p95 **200.14 ms**, max 267.97 ms |
| beta.19, static controls | install blocked by debug/release signature; debug app crashed in ART JIT (emulator issue) |
| beta.20, static controls/surfaces/scroll | launch OK, Settings reached, **119 frames p95 216.88 ms, max 308.39 ms** |
| Android Settings baseline, same emulator/run | **120 frames p95 116.87 ms, max 266.84 ms** |
| Budget | **FAIL** (`p95 <= 32 ms`, `max <= 120 ms`) |

The baseline is the key result: the emulator cannot hit 32 ms even for `com.android.settings`. Duo is
about **1.86× the platform baseline**, not an order of magnitude worse. The budget failure therefore
does not prove the phone UI is slow, but it also cannot prove it is fast; only CPH2747 can.

Evidence:
- `docs/evidence/phase7/beta18-settings.png`, `beta18-settings-scrolled.png`
- `docs/evidence/phase7/beta20-settings.png`, `beta20-settings-scrolled.png`
- `docs/evidence/phase7/duo-beta20-gfx.txt`, `docs/evidence/phase7/android-settings-gfx.txt`
- `docs/evidence/phase7/beta19b-crash.txt` (debug ART JIT SIGSEGV — emulator-only)
- `docs/evidence/phase7/beta20-crash.txt`

The emulator is not a valid proxy for CPH2747: it renders arm64 guest code under x86_64 translation
with a software GPU. The budget failure still means **the device run is required** before sign-off;
it is not waived.

## Performance decision

- Do not add live glass back to cards; it was the ANR trigger.
- Keep exactly one live navigation surface on phones and zero on emulators.
- The next measurement must be on CPH2747 with `gfxinfo` after a deliberate Settings scroll; use
  `tools/check-perf.py` and record the hash.

## Open

- Device p95/max and `meminfo` (the emulator cannot establish the budget).
- Static profiles are not support claims; each needs its own runtime dump before `measured: true`.
- Light/dark contrast was fixed by the dark-first shell but needs a light-system screenshot.
- No mic/location/volume sources (Phase 6 gap).
