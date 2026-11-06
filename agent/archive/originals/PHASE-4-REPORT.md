# Phase 4 — Cell accuracy report (prototype)

Branch: `phase/4-cell-accuracy`, created from `main` after the Phase 3 prototype merge `10f9ac7`.

## 1. Outcome

The four cellular dots now use an explicit stock-level-to-four-dot mapping, a slot-aware SIM selection,
and a per-refresh diagnostic line. The CPH2747 full-bar and 5G cases are measured; the weak-signal
transition still needs one real-device sample. The Settings > Simulation case can show levels 0..4
without waiting for the radio.

## 2. Evidence

Report `1.4.4-beta.1` (2026-10-29 00:00):

```text
cellLevel=4 generation=5G mobileData=true cellLevels=[4, 4]
cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] airplane=false sim=auto
```

Report `1.4.4-beta.2` (2026-10-29 00:07):

```text
cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] airplane=false sim=auto
cell raw ... sim=sim2
cell raw ... sim=sim1
cell raw ... sim=auto
```

- Stock SystemUI reports five levels (`max=5`); raw level 4 maps to four Duo dots.
- Both active subscriptions reported level 4 in these samples, so they cannot prove that sim1 and
  sim2 differ; the selection path itself is logged and unit-tested.
- `generation=5G` with LTE data is the measured NSA case already covered by the existing mapper.

## 3. Implementation

| Change | Where |
|---|---|
| `cellDots(rawLevel, stockLevels)` maps 5/6-level stock sources into four dots, clamps invalid values | `SignalMapping.kt` |
| `SimSignal(slot, subscriptionId, level)` preserves SIM slot | `SignalMapping.kt` |
| `selectedCellLevel(sim1/sim2/auto, signals, default)` selects by slot, falls back to the platform default | `SignalMapping.kt` |
| `cellSignals()` reads active subscriptions and their per-subscription levels | `CellReader.kt` |
| Structured `cell raw ...` line on every refresh | `DuoStateMonitor.kt` |
| Cellular dots 0..4 simulation using the real Rive scene | `DuoSettingPreview.kt`, `DuoSettingsSections.kt` |
| Report checker | `tools/check-cell-report.py` |

## 4. Tests

- `319` unit tests, `0` failures.
- `stock cellular levels map onto four dots`: 4/5/6-level sources, clamping.
- `dual SIM selection uses the slot, not the active-list order`: slot 2 only, both slots, auto fallback.
- `cellular shows one sphere per bar`: levels 0, 1, 2, 3, 4.
- `tools/check-cell-report.py` on the measured report: `EXIT=0`, 18 raw lines, `dots=[4, 4]`.
- Existing 5G NSA tests (`networkGeneration(13, nrConnected=true) -> 5G`) remain green.

## 5. Test-plan status

| Test | Status | Evidence |
|---|---|---|
| TP-050 full bars -> four dots | **PASS** | `max=5`, `raw=4`, `dots=[4,4]` |
| TP-051 dual-SIM sim1/sim2/auto | **PARTIAL** | all three selections logged; both levels were 4, so cross-talk is not distinguishable |
| TP-052 5G NSA | **PASS** | `generation=5G` while on LTE data; existing NSA unit tests |
| TP-053 weak/strong transition | **PARTIAL** | 0..4 visual path unit-tested and simulated; no weak radio sample yet |
| TP-054 per-UI measurement | **PASS for CPH2747** | `max=5`, measured OxygenOS report |

## 6. Build

- Version `1.4.4-beta.3` (code 30)
- Commit `12d9b55`
- APK `5234556acfcc9d6b3bed9acada14759c712b0638c0ff6e6b39b9d6d247c4fd1f`
  (`6,911,252` bytes)
- `duo.riv` unchanged from the Phase 3 event build
  `540bb0884324b1f4303b22debdfb943cb4ad9bc04ce3fba0cacaa3896269e201`
- Sent through the relay with HTTP 200; not installed by the agent (device disconnected).

## 7. Remaining / next

1. One real weak-signal report: capture `cell raw` at 3/2/1/0 and verify the dots follow without
   sticking or flickering.
2. If a different UI reports a bar count other than 5, add a measured profile count; do not guess one.
3. Optionally repeat TP-051 with different levels on sim1/sim2.

No release/merge of this branch until the above is reviewed by the owner.
