# Phase 6 report — Rive animation system (prototype)

Status: **prototype complete; device/emulator execution pending**. Branch `phase/6-rive`, from the
locked Phase 5 prototype. The Rive file and both renderers are updated together.

## What changed

### Charging arrival pulse (RIV-002)
- New `ChargeSettle` animation (14 frames / 233 ms) on the bolt: scale 1 → 1.08 → 1 with cubic
  easing, no loop.
- New `ChargeSettle` state (`0:261`) between `ChargeIn` (`0:147`) and `ChargeBolt` (`0:148`).
  - `ChargeIn` → `ChargeSettle` on its 100 % exit time.
  - `ChargeSettle` → `ChargeBolt` after the pulse, or immediately to `ChargeBolt` if the charger was
    removed mid-pulse.
- Same timeline in the Canvas clone: `ChargeMotion.SETTLE_START_MS = 1600`,
  `SETTLE_END_MS = 1833`, `SETTLE_SCALE = 0.08`. Canvas invalidates during that window so the pulse
  is drawn, not skipped by idle pausing.
- Interpretation note: the existing `ChargeIn` bolt flight is the "0 → level" travel in this
  prototype; the new settle pulse is the "arrived at level" beat. A separate sweep *over the green
  arc* is not authored — do not claim it as done.

### 20 % shake (RIV-003)
- Pure rule `ChargeMotion.shouldShake(previous, target, charging)`: one shake when charging crosses
  from below 20 to 20–24; no shake on reboot/plug when already ≥20.
- `DuoIconHost.shakeAll()` applies a 200 ms, 3.5 dp damped horizontal shake to every element (main,
  split indicators, keyguard, shade copies). It is renderer-neutral, so Rive and Canvas shake by the
  same amount; the per-state motion remains Rive's.
- Canvas and Rive share `ChargeMotion` constants; `ChargeMotionTest` pins the crossing rule and the
  timeline order.

### Existing event motions
- Alarm bounce/shake (`EventAlarm`, 72 frames / 1200 ms).
- Flashlight press/hold/release (`EventFlashlight` 12 frames + `EventFade`).
- Screenshot pop/hold/fade (`EventScreenshot`, 60 frames / 1000 ms).
- Each has a Canvas clone in `DuoCanvasView`.
- Microphone, location and volume sources/motions are **not** implemented; the measured sources are
  still missing (see `EVENT-ARCHITECTURE.md` §5).

## Evidence

| Check | Result |
|---|---|
| `rive . --verify` | success, 0 errors, 0 warnings |
| `rive . --once` | `493,329` bytes, 0 errors, 0 warnings |
| `duo.riv` SHA-256 | `636e8c9a9a721cb982f5c451bdddc784004e890bc16201b2b42b15282b410e9b` |
| `app/src/main/res/raw/duo.riv` | byte-identical to `rive/duo/build/duo.riv` |
| `check-rive-eases.py` | 142 keyframes, 142 curves, 0 inert |
| Headless idle | `rive/duo/build/duo.png` (120×136) |
| Headless charge @ 96f | `rive/duo/build/charge-96.png` — bolt in the ring gap |
| Headless charge @ 110f | `rive/duo/build/charge-110.png` — settle frame |
| Unit tests | 321 (2 new), 0 failures |
| App build | pending final Phase 6 release build |

## Remaining

- Emulator/device: charging on/off, 20 % crossing, both engines, all three surfaces; `gfxinfo` idle
  and active.
- Mic/location/volume: source adapters need measured SystemUI/AudioManager evidence; do not invent
  hooks or art.
- Headless screenshots capture the state-machine start and the two charge frames; no automated
  pixel-diff is in CI yet.
