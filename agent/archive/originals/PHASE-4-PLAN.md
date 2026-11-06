# Phase 4 — Cell accuracy (plan, 2026-10-28)

Branch: `phase/4-cell-accuracy` (created from `main` after the Phase 3 prototype merge `10f9ac7`).

## 1. Scope and requirements

Primary requirement: **UI-012 / CUS-004**, four cellular dots must match the phone's own bar count;
full phone bars must show four full Duo dots. Cover dual-SIM and 5G NSA.

Inputs:

- `agent/REQUIREMENTS.md` UI-012, CUS-004.
- `agent/PLAN-v2.md` §9.
- `agent/TEST-PLAN.md` §6.5, TP-050..TP-054.
- `agent/REPAIR-CATALOG.md` RC-007 and RC-016.

Out of scope for this phase: new event types, new icon packs, layouts, and multi-UI profiles.

## 2. Measured state before changes

Evidence from the 2026-10-28 22:09 report (`duo-report-20261028-220953.zip`):

- `cellLevel=4 generation=5G mobileData=true cellLevels=[4, 4]`.
- The phone was on the default data line, two subscriptions were visible, and the module saw four
  levels for both.
- SystemUI `MobileSignalController.getNumLevels()` is
  `CellSignalStrength.getNumSignalStrengthLevels()` (5 on this SystemUI; `+1` only when strengths
  are inflated), while Duo draws four dots. This is the likely source of a “full phone bar = fewer
  dots” mismatch on a 5-level stock bar; it must be confirmed with raw values from the device.
- Current `CellReader` reads `TelephonyManager.signalStrength.level` (0..4) and clamps to 0..4. It
  does not log the raw level/admin max/network type, so a mismatch cannot be proven from a report.

## 3. Decisions

1. **Measure first.** Add a bounded structured log line per cellular update with raw level, levels per
   active subscription, stock max levels, data generation and the active subscription ids.
2. **One pure mapper.** Put the 5-level-to-4-dot mapping in `SignalMapping` so it is unit-tested and
   shared by the single-SIM and dual-SIM paths. No UI or Rive change is needed for cell dots.
3. **No per-UI override without measurement.** The mapper takes a stock level count; a profile override
   is only added if a measured UI disagrees.
4. **Fail closed to the last value.** A failed telephony read keeps the previous level and logs the
   reason, never throws (FR-21).
5. **Dual-SIM order is SIM-slot order.** `sim1`/`sim2` select by slot; `auto` follows the default data
   subscription. The log records both ids so a mismatch is attributable.

## 4. Work breakdown

| # | Task | Files | Verification |
|---|---|---|---|
| 4.1 | Add pure `cellDots(rawLevel, stockLevels)` mapper and tests for 4/5/6 levels, 0 and clamping | `SignalMapping.kt`, `DuoMappingTest.kt` | unit |
| 4.2 | Use the mapper in `CellReader` single- and dual-SIM reads | `CellReader.kt` | unit + log |
| 4.3 | Log one structured `cell raw ...` line per refresh: level, levels, generation, max, subs, simChoice | `DuoStateMonitor.kt`, `CellReader.kt` | report contains line |
| 4.4 | Add dual-SIM selection and 5G-NSA pure tests | `DuoMappingTest.kt`, `CellReaderTest.kt` | unit |
| 4.5 | Device evidence: full/3/4/weak bars, dual-SIM, 5G NSA, transitions | CPH2747 report | TP-050..TP-054 |
| 4.6 | If and only if measured mapping differs, add a profile/UI override with a test | `RomAdapter`/`CellReader` | measured diff |

## 5. Test plan mapping

| Test | Method | Pass criterion |
|---|---|---|
| TP-050 | Compare stock bars and Duo dots at each level | full=4, 3/4=3, 2/4=2, 1/4=1, none=0 |
| TP-051 | Dual-SIM: select sim1/sim2 and auto | selected subscription's level, no cross-talk |
| TP-052 | 5G NSA: LTE data type + NR connected | label 5G, not 4G |
| TP-053 | Weak/strong transition | monotonic, no flicker or stuck value |
| TP-054 | Per-UI measurement | mapper matches that UI's bar count or is `UNVERIFIED` |

## 6. Exit gate

- 4-dot mapping unit tests green.
- One report shows the raw cellular line and the expected dots on CPH2747.
- Dual-SIM/5G cases recorded.
- No change to the default bar when telephony is unreadable.

## 7. Current status

- Branch created from the Phase 3 prototype merge (`10f9ac7`).
- 4.1-4.3 implemented:
  - `SignalMapping.cellDots(rawLevel, stockLevels)` scales a 5/6-level stock source into four
    dots; 4-level sources are unchanged. Unit tests cover 0..4, 4/5/6-level sources and clamping.
  - `CellReader` uses the mapper for the single-SIM and per-subscription paths.
  - Every refresh logs
    `cell raw level=.. levels=[..] generation=.. max=.. subs=[..] airplane=.. sim=..`.
- 4.4 implemented: `SimSignal(slot, subscriptionId, level)` and
  `SignalMapping.selectedCellLevel(choice, signals, default)` select by SIM slot, not active-list
  position. A slot that is absent keeps the platform default rather than borrowing the other SIM.
- 319 tests, 0 failures.
- `tools/check-cell-report.py` verifies the raw line against the snapshot and four-dot mapping;
  the 2026-10-29 report passes (`raw lines: 15`, `dots=[4, 4]`, `max=5`).
- APK `5234556acfcc9d6b3bed9acada14759c712b0638c0ff6e6b39b9d6d247c4fd1f` (1.4.4-beta.3, code 30).
- Settings > Simulation now has a **Cellular dots** card with levels 0..4. It uses the real Rive
  scene, so TP-053's weak/mid/full visual path can be inspected without waiting for a radio change.
- Device evidence from 2026-10-29 00:00 report (1.4.4-beta.1):
  - `cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] airplane=false sim=auto`.
  - Two active subscriptions; the stock max is 5; raw level 4 maps to four Duo dots.
  - TP-050 full-level case passes; TP-052 5G NSA passes at that moment.
  - TP-051 exercised on 1.4.4-beta.2: logs show `sim=sim2`, then `sim=sim1`, then `sim=auto`;
    both subscriptions read 4 at the time, so cross-talk is not distinguishable in that sample.
  - TP-053 visual path is unit-tested for 1/2/3/4/0 dots; a real weak-signal transition still
    needs one device sample (all recorded samples were raw 4 / max 5).
