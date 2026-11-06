# Agile 1.7 - cell/Wi-Fi level evidence and fix-ready mapping plan

| Field | Value |
|---|---|
| Date | 2026-11-03 |
| Scope | Read-only evidence audit; no code, build, install or device use |
| Sources used | `reverse/fatal-logs/`, `docs/evidence/full-app/logcat.txt`, `docs/evidence/phase0-oos16.txt`, `agent/PHASE-4-REPORT.md`, `agent/PHASE-1-5-WALKTHROUGH.md`, `CellReader.kt`, `WifiReader.kt`, `SignalMapping.kt`, `DuoStateMonitor.kt` |
| Related plan | `agent/AGILE-1.7-PLAN.md` A0/A2, `AGILE-1.7-REQUIREMENTS.md` RING2-05, `AGILE-1.7-TEST-PLAN.md` T-RING2-05 |
| Status | Evidence audit complete; mapping change is gated on the one-time capture in section 8 |

## 0. Short answer

1. The line named `cell raw` is not raw. `level` is the **selected, already-mapped four-dot value**, and
   `levels` is a list of **already-mapped four-dot values**. The stock `SignalStrength.level` is never
   logged; `max` logs the level count, not a raw value. Proof: `CellReader.kt:22-28`, `CellReader.kt:39-40`,
   `CellReader.kt:67-79`, `CellReader.kt:111-118`, `DuoStateMonitor.kt:566-576`,
   `SignalMapping.kt:8-9`.
2. On the measured 5-level OOS source (`max=5`, raw 0..4 inferred from `PHASE-4-PLAN.md:30`),
   `cellDots` is effectively identity:
   0->0, 1->1, 2->2, 3->3, 4->4. A full raw cannot become 1 or 3 on that configuration. The owner's
   `full -> 1/3/4` report is therefore **not explained by the arithmetic on the measured source**; it
   needs a source/selection/staleness cause, or a 4-level `count` treated as a max index.
3. The one real mapping defect is semantic: `cellDots(raw, stockLevels)` uses `stockLevels` as if it
   were the highest legal raw, but `maxSignalLevels()` returns a **count**. On a 4-level source whose
   full index is 3, `cellDots(3, 4) = 3`; full becomes 3 dots. On the measured 5-level source the
   off-by-one is invisible because `ceil(4 * 4 / 5) = 4`.
4. Existing logs cannot identify which subscription/slot produced `cellLevel`. The report has
   `subs=[2, 1]` (two active SIMs) and only one selected `cellLevel`, with no `pickedSub`/`pickedSlot`.
   `refresh()` also reads the default line and the per-sub list in two separate calls
   (`DuoStateMonitor.kt:566-568`), so the two can disagree within one frame.
5. New diagnostics must log, per subscription: `raw`, `count`, `maxIndex`, `rat`, `dbm`, `dots` and
   `src`; then a separate selection line with `sim`, `slot`, `sub`, `dots`, `src` and `fallback`. Wi-Fi
   must log `rssi`, the calculated stock bars and the three-arc result. Keep the existing line unchanged
   for the frozen-surface contract (`docs/rom-contract.md:10-11`) and add the new line inside the
   existing readers section.

## 1. Evidence inventory

Timestamps are device-local from the capture. `VectorLegacyBridge` duplicates each `DuoSB` line; the
tables count each `DuoSB` line once and note duplicates. `reverse/fatal-logs/logcat/main_tail30000.txt`
has no `DuoSB` lines at all; its signal lines are the stock OOS SystemUI/telephony lines.

### 1.1 Module cellular lines - Vector capture, 2026-10-31 20:49

Capture context: `VectorLegacyBridge` / `DuoSB`, PID 1103, stage 2, `renderer=Canvas`,
`provider=unreachable`, single subscription. The diagnostic dump is at
`docs/evidence/full-app/logcat.txt:757-777`.

| # | Time | Line (verbatim, `DuoSB` only) | Logged "raw" (actually dots) | True raw | `max` | `levels` (dots) | Dots drawn | Source / subId | Log line |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 20:49:32.094 | `cellLevel=3 generation=5G mobileData=true cellLevels=[3]` | 3 | not printed | not in snapshot | `[3]` | 3 | default line; `subs=[1]` in the next raw line | `full-app/logcat.txt:761` |
| 2 | 20:49:32.128 | `cell raw level=3 levels=[3] generation=5G max=5 subs=[1] airplane=false sim=auto` | 3 | 3 (inferred preimage) | 5 | `[3]` | 3 | `sim=auto`; `subs=[1]` | `:781` |
| 3 | 20:49:32.131 | same | 3 | 3 (inferred preimage) | 5 | `[3]` | 3 | same | `:783` |
| 4 | 20:49:32.221 | same | 3 | 3 (inferred preimage) | 5 | `[3]` | 3 | same | `:789` |
| 5 | 20:49:34.240 | same | 3 | 3 (inferred preimage) | 5 | `[3]` | 3 | same | `:797` |
| 6 | 20:49:34.245 | same | 3 | 3 (inferred preimage) | 5 | `[3]` | 3 | same | `:799` |
| 7 | 20:49:35.179 | same | 3 | 3 (inferred preimage) | 5 | `[3]` | 3 | same | `:801` |

Duplicates: lines 782, 784, 790, 798, 800, 802 are `VectorLegacyBridge: DuoSB | ...` copies. The six
`cell raw` timestamps are 32.128, 32.131, 32.221, 34.240, 34.245 and 35.179: six refreshes in four
bursts (32.128/32.131 and 34.240/34.245 are 3-5 ms pairs), which is the duplicate-refresh noise
described in section 3.

What can be inferred: because the logged value is the output of `cellDots(raw, 5)` and the result is 3,
the only integer preimage is `raw = 3` (`cellDots(3, 5) = 3`; `cellDots(4, 5) = 4`). That is an
inference from the current formula, not an observation of the raw value.

### 1.2 Module cellular lines - CPH2747 report, 2026-10-29

`agent/PHASE-4-REPORT.md` has no logcat millisecond timestamps; the report labels are the only time
evidence. The report itself is the capture, so these rows are `report-time`, not `logcat-time`.

| # | Report time / build | Line (verbatim) | Logged "raw" (actually dots) | True raw | `max` | `levels` (dots) | Dots | Source / subId | Report line |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-29 00:00, 1.4.4-beta.1 | `cellLevel=4 generation=5G mobileData=true cellLevels=[4, 4]` | 4 | not printed | not in snapshot | `[4, 4]` | 4 | auto; `subs=[2, 1]` in the next line | `PHASE-4-REPORT.md:17` |
| 2 | 2026-10-29 00:00, 1.4.4-beta.1 | `cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] airplane=false sim=auto` | 4 | `{4, 5}` (not disambiguated) | 5 | `[4, 4]` | 4 | auto; subId 2/slot 0 and subId 1/slot 1 inferred from the 11-03 OOS log | `:18` |
| 3 | 2026-10-29 00:07, 1.4.4-beta.2 | `cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] airplane=false sim=auto` | 4 | `{4, 5}` (not disambiguated) | 5 | `[4, 4]` | 4 | auto | `:24` |
| 4 | 2026-10-29 00:07, 1.4.4-beta.2 | `cell raw ... sim=sim2` (truncated in the report) | not printed | not printed | not printed | not printed | not printed | sim2 selection exercised | `:25` |
| 5 | 2026-10-29 00:07, 1.4.4-beta.2 | `cell raw ... sim=sim1` (truncated) | not printed | not printed | not printed | not printed | not printed | sim1 selection exercised | `:26` |
| 6 | 2026-10-29 00:07, 1.4.4-beta.2 | `cell raw ... sim=auto` (truncated) | not printed | not printed | not printed | not printed | not printed | auto selection exercised | `:27` |

`levels=[4, 4]` is in SIM-slot order because `cellSignals()` sorts by `simSlotIndex`
(`CellReader.kt:51-52`). With `subs=[2, 1]`, the list is [slot 0 = subId 2, slot 1 = subId 1] **if**
the slot assignment matches the 2026-11-03 OOS log (`subId:2 ... slotId:0`, `subId:1 ... slotId:1`).
The 2026-10-29 report itself does not print the slot pair, so that assignment is inferred across
captures. Both levels were 4, so this sample cannot prove a cross-SIM difference.

The report's "raw level 4" (`PHASE-4-REPORT.md:30`, `:60`) is not proven: under `cellDots(raw, 5)`,
both raw 4 and raw 5 map to 4. The logged 4 is the mapped dot count, and the raw is in `{4, 5}`.

### 1.3 Stock OOS cellular lines - 2026-11-03 00:10

Source: `reverse/fatal-logs/logcat/main_tail30000.txt`. OOS telephony prints per-RAT
`CellSignalStrength*` levels, not the aggregate `SignalStrength.getLevel()`. `primary=CellSignalStrengthLte`
means the aggregate level is the LTE level; the derived raw below is therefore `mLte.level`, marked
`derived`. The `OplusTelephonyController` line carries no `subId`; the adjacent
`MobileConnectionRepositoryImpl` line does.

| # | Time | `mLte.level` | LTE metrics | `mNr.level` | NR metrics | `primary` | Adjacent sub/slot | Derived raw for module | Dots at `max=5` | Lines |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 00:10:09.212 | 3 | rssi=-71, rsrp=-99, rsrq=-10, rssnr=14 | 0 | all invalid | Lte | subId 2, slot 0 at 00:10:09.213 | 3 | 3 | `:591`, `:594` |
| 2 | 00:10:11.241 | 2 | rssi invalid, rsrp=-99 | 1 | ssRsrp=-105 | Lte | subId 2, slot 0 at 00:10:11.243 | 2 | 2 | `:1112`, `:1114` |
| 3 | 00:10:21.360 | 3 | rssi=-67, rsrp=-99, rsrq=-13, rssnr=13 | 0 | all invalid | Lte | subId 2, slot 0 at 00:10:21.362 | 3 | 3 | `:4133`, `:4148` |
| 4 | 00:10:23.095 | 2 | rssi invalid, rsrp=-99 | 1 | ssRsrp=-105 | Lte | subId 2, slot 0 at 00:10:23.098 | 2 | 2 | `:4522`, `:4529` |
| 5 | 00:10:28.805 | 4 | rssi=-65, rsrp=-97, rsrq=-13, rssnr=14 | 1 | ssRsrp=-102, ssRsrq=-11, ssSinr=18 | Lte | subId 2, slot 0 at 00:10:28.806 | 4 | 4 | `:5099`, `:5102` |

Additional dual-SIM context, with no level line:

| Time | Line | Meaning | Log line |
|---|---|---|---|
| 00:10:28.078 | `MobileConnectionRepositoryImpl-->subId:1 isSoftSimCard:false slotId:1` | slot 1 / subId 1 is present and updating | `:4971` |
| 00:10:28.563 | `MobileConnectionRepositoryImpl-->subId:1 isSoftSimCard:false slotId:1` | second update for slot 1 | `:4986` |

Limits of this evidence:

- The stock line does not print `MobileSignalController.getNumLevels()` or the icon's bar count. The
  `max=5` measurement comes from the module on a different run (`PHASE-4-REPORT.md:18`, `:24`;
  `full-app/logcat.txt:781`).
- The stock line does not print the aggregate `SignalStrength.getLevel()`. The derived raw assumes the
  aggregate is the `primary` LTE level; that is consistent with `primary=CellSignalStrengthLte` but not
  directly observed.
- The association of a stock line to subId 2/slot 0 is temporal (0.001-0.002 s later), not an explicit
  field. The subId 1 updates at 00:10:28.078/563 have no corresponding per-RAT line.
- There is no stock line for raw 0 or 1. The lowest observed LTE level is 2; the highest is 4.

### 1.4 Module Wi-Fi line - Vector capture

| Time | Line | Mapped `wifiLevel` | Stock RSSI | Stock bars | Dots / arcs | Source | Log line |
|---|---|---|---|---|---|---|---|
| 20:49:32.094 | `wifiEnabled=true wifiActive=false wifiValidated=false wifiLevel=0` | 0 | not logged | not logged | 0 arcs; `wifiAsSpheres(0)=0` spheres | active network unavailable; fallback path not named | `full-app/logcat.txt:759` |

This is the only module Wi-Fi observation in the allowed sources. It proves the snapshot prints the
mapped 0..3 value, not RSSI and not the stock bar count.

### 1.5 Stock OOS Wi-Fi lines - 2026-11-03 00:10

`WifiSignalController-->handleStatusUpdated` prints `rssi`, `level`, and the default-network flags. It
does not print the Wi-Fi bar maximum. Every unique timestamp appears three times (same PID/TID, same
values); the table lists the unique observation and all line numbers.

| # | Time | `rssi` | `level` | `state` | `connected` | `isDefaultNetwork` | `wifiBars(level)` (derived) | Lines |
|---|---|---|---|---|---|---|---|---|
| 1 | 00:10:07.541 | -52 | 4 | 3 | true | true | 3 | `main_tail30000.txt:201-203` |
| 2 | 00:10:10.548-549 | -53 | 4 | 3 | true | true | 3 | `:905`, `:907`, `:908` |
| 3 | 00:10:13.564-565 | -50 | 4 | 3 | true | true | 3 | `:1925`, `:1929`, `:1930` |
| 4 | 00:10:16.575-577 | -52 | 4 | 3 | true | true | 3 | `:2576`, `:2581`, `:2582` |
| 5 | 00:10:19.588-589 | -52 | 4 | 3 | true | true | 3 | `:3386`, `:3389`, `:3391` |
| 6 | 00:10:22.596 | -53 | 4 | 3 | true | true | 3 | `:4387`, `:4389`, `:4390` |
| 7 | 00:10:25.611 | -51 | 4 | 3 | true | true | 3 | `:4727`, `:4731`, `:4732` |
| 8 | 00:10:28.639-640 | -50 | 4 | 3 | true | true | 3 | `:5023`, `:5025`, `:5026` |
| 9 | 00:10:31.659 | -50 | 4 | 3 | true | true | 3 | `:5311`, `:5313`, `:5314` |

`state=3`, `connected=true`, `isDefaultNetwork=true`, `statusLabel=null`, `isDirty=false` on every
line. The stock `level=4` is the top value on this capture; the maximum bar count is not in the line.
The module's Wi-Fi profile is 4 stock bars (`WifiReader.kt:35-39`), which is consistent but not proven
by these lines.

### 1.6 Evidence that is not a level observation

| Source | What it proves | What it does not prove | Lines |
|---|---|---|---|
| `docs/evidence/phase0-oos16.txt` | OOS has `WifiSignalController` and `MobileSignalController` with `getNumLevels()` | no level values, no max count | `:16-17` |
| `docs/evidence/phase0-oos16.txt` | two `OplusModernStatusBarMobileView id=mobile_combo` at x=983 and x=1044 (two SIMs), one `wifi_combo` | no per-view level | `:102`, `:116`, `:130` |
| `agent/PHASE-1-5-WALKTHROUGH.md` | the planned verification reads `cell raw level=...` | it cannot verify raw because the line is mapped | `:68`; known gap at `:74` |
| `reverse/fatal-logs/logcat/system_tail30000.txt`, `events_tail30000.txt` | no module signal lines in those buffers | absence is not proof of no reads | whole files |
| `reverse/fatal-logs/logcat/main_tail30000.txt` | no `DuoSB` module lines | the module log for this capture is not in the pulled file | whole file |

## 2. Where the current diagnostics mislabel mapped vs raw

### 2.1 Code path

`CellReader.cellLevel()` reads `TelephonyManager.signalStrength.level` and immediately returns
`SignalMapping.cellDots(raw, maxSignalLevels())` (`CellReader.kt:22-28`). `levelForSubscription()` does
the same (`CellReader.kt:67-79`). `cellLevels()` maps `cellSignals()` to `it.level`, and `SimSignal.level`
is documented as "already-mapped four-dot level" (`CellReader.kt:39-40`, `SignalMapping.kt:8-9`).

`DuoStateMonitor.refresh()` then passes the selected `cellLevel` and the mapped `levels` into
`CellReader.diagnostic(...)` (`DuoStateMonitor.kt:566-576`). The formatter prints them as
`cell raw level=$level levels=$levels ... max=${maxSignalLevels()}` (`CellReader.kt:111-118`).

So the fields mean:

| Printed field | What the code passes | Actual meaning |
|---|---|---|
| `cell raw level=` | `cellLevel` | selected four-dot value after `cellDots` and `selectedCellLevel` |
| `levels=` | `signals.map { it.level }` | four-dot values per active subscription, in slot order |
| `max=` | `maxSignalLevels()` | stock level **count** (5 on the measured source), not a raw value and not the maximum raw index |
| `subs=` | `subscriptionIds()` | active subscription ids, sorted by slot; not the chosen subscription |
| `sim=` | `host.simChoice` | user setting, not proof of the source used |

The KDoc at `CellReader.kt:106-109` says the line "names the raw level, the per-subscription levels, the
stock level count..." That promise is false: it names mapped values for the first two, and a count for
the third.

### 2.2 The report and the checker repeat the same error

| Location | Claim | Reality | Proof |
|---|---|---|---|
| `tools/check-cell-report.py:72` | `expected = [dots(x, stock) for x in levels]` | `levels` are already dots; this maps them a second time | `CellReader.kt:39-40` |
| `tools/check-cell-report.py:85` | compares `observed` with `dots(level, stock)` | both `observed` and `level` are already mapped | `DuoStateMonitor.kt:566-576` |
| `agent/PHASE-4-REPORT.md:30` | "raw level 4 maps to four Duo dots" | 4 is the dot count; raw is `{4, 5}` under `max=5` | `SignalMapping.kt:45-50` |
| `agent/PHASE-4-REPORT.md:60` | TP-050 evidence: `max=5`, `raw=4`, `dots=[4,4]` | `raw=4` is not observed; it is back-read from the dot value | same |
| `agent/PHASE-1-5-WALKTHROUGH.md:68` | "Read `cell raw level=...`" | the line cannot answer the raw question | same |
| `agent/AGILE-1.7-PLAN.md:46` | "diagnostics mislabel mapped values as raw" | correct, and already known | same |

The checker cannot catch this on the measured source because `dots()` is the same function and is
idempotent over 0..4 for stock 4 and 5:

- `dots(3, 5) = 3`, `dots(4, 5) = 4`;
- `dots(3, 4) = 3`, `dots(4, 4) = 4`.

A line with `level=3, max=5` and a snapshot `cellLevel=3` therefore passes even though the line never
contained a raw value. It is a self-consistency check, not a raw check.

### 2.3 Why this matters for the owner report

The visible value is `cellLevel`, which is the selected dot count. The diagnostics cannot answer:

- which subscription/slot produced it;
- what the raw `SignalStrength.level` was;
- whether `maxSignalLevels()` was 4, 5, 6 or 8;
- whether the value is fresh or a failed-read fallback.

That is why `full -> 1/3/4` cannot be attributed from the existing logs. The next section separates what
is provable from what is only a hypothesis.

## 3. Proposed diagnostics

### 3.1 Frozen-surface constraint

`docs/rom-contract.md:10-11` freezes existing `DuoSB` log strings: no rewording, reordering, merging or
removing. New lines may be added inside the existing sections, never on the attach/hide path. Therefore:

- keep the existing `cell raw level=...` line exactly as it is for compatibility;
- add a new `cell sample ...`, `cell pick ...` and `wifi sample ...` line after it;
- update `tools/check-cell-report.py` to require the new lines and to treat the legacy line as
  `legacy-only` (not a pass).

### 3.2 New cell line, one per active subscription

```text
cell sample sub=<subId> slot=<slotIndex> raw=<SignalStrength.level> count=<getNumSignalStrengthLevels>
            maxIndex=<highest legal raw> rat=<primary RAT simple name> dbm=<int> metric=<rsrp|rssi>
            dots=<0..4> src=<per-sub|default|stale|unavailable> airplane=<true|false>
```

Field dictionary:

| Field | Type | Meaning | Source |
|---|---|---|---|
| `sub` | int, -1 unknown | subscription id | `SubscriptionInfo.getSubscriptionId()` |
| `slot` | int, -1 unknown | SIM slot | `SubscriptionInfo.getSimSlotIndex()` |
| `raw` | int, -1 unknown | `SignalStrength.level` before any Duo mapping | `TelephonyManager.getSignalStrength().getLevel()` |
| `count` | int, -1 unknown | number of stock levels | `CellSignalStrength.getNumSignalStrengthLevels()` |
| `maxIndex` | int, -1 unknown | highest legal `raw`; either `count` or `count - 1`, measured once per ROM | new profile/capture |
| `rat` | string | `Lte`, `Nr`, `Wcdma`, `Gsm`, ... | `SignalStrength` primary object |
| `dbm` | int, -999 unknown | primary RAT's dBm | `CellSignalStrengthLte.getRsrp()` / `getRssi()` |
| `metric` | string | `rsrp` or `rssi` | same |
| `dots` | int 0..4 | Duo output | pure mapper |
| `src` | enum | `per-sub`, `default`, `stale`, `unavailable` | reader path |
| `airplane` | bool | airplane mode | `DeviceStateReader` |

One line per active subscription, not one line per refresh with a list. This makes the source explicit
and keeps the line bounded (two lines on a dual-SIM phone).

### 3.3 New selection line, one per refresh

```text
cell pick sim=<auto|sim1|sim2> slot=<slotIndex|-1> sub=<subId|-1> dots=<0..4>
          src=<per-sub|default|stale|fallback> fallback=<true|false> defaultSub=<subId|-1>
```

`fallback=true` means the selected slot was not in the active list and `selectedCellLevel` used
`defaultLevel` (`SignalMapping.kt:58-63`). `defaultSub` is the subscription the default
`TelephonyManager` is actually using; read it once per refresh, not inferred from the list order.

Example, OOS dual SIM, slot 0/subId 2 full and slot 1/subId 1 weak, user selects `sim2`:

```text
cell sample sub=2 slot=0 raw=4 count=5 maxIndex=4 rat=Lte dbm=-97 metric=rsrp dots=4 src=per-sub airplane=false
cell sample sub=1 slot=1 raw=1 count=5 maxIndex=4 rat=Lte dbm=-111 metric=rsrp dots=1 src=per-sub airplane=false
cell pick sim=sim2 slot=1 sub=1 dots=1 src=per-sub fallback=false defaultSub=2
```

Missing selected slot:

```text
cell pick sim=sim2 slot=-1 sub=-1 dots=4 src=fallback fallback=true defaultSub=2
```

Failed per-sub read (must not silently become 0 unless the UI policy says so):

```text
cell sample sub=2 slot=0 raw=-1 count=5 maxIndex=4 rat=- dbm=-999 metric=- dots=4 src=stale airplane=false
```

### 3.4 New Wi-Fi line

```text
wifi sample rssi=<dBm|-127> calc5=<WifiManager.calculateSignalLevel(rssi,5)>
            stockBars=<0..4> dots=<0..3> on=<true|false> active=<true|false>
            validated=<true|false> src=<active|connectionInfo|stale|off>
```

Example from the measured stock line (`rssi=-52`, stock `level=4`):

```text
wifi sample rssi=-52 calc5=4 stockBars=4 dots=3 on=true active=true validated=true src=active
```

`stockBars` must be the value the code actually feeds to `wifiBars`, not a second independent
calculation. `WifiReader` currently does `(calculateSignalLevel(rssi, 5) + 1).coerceIn(0, 4)`
(`WifiReader.kt:35-39`); log both the `calc5` and the post-shift `stockBars` so the shift is visible.
`dots` is the three-level Wi-Fi value (0..3); the four spheres are `wifiAsSpheres` (0->0, 1->2, 2->3,
3->4), not a second Wi-Fi mapping.

### 3.5 Snapshot additions

`Diag.appendReaders` currently prints `cellLevel` and `cellLevels` (`Diag.kt:271-274`). Keep those
labels and add:

```text
cellRaw=4 cellCount=5 cellMaxIndex=4 cellSlots=[0,1] cellSubs=[2,1] cellPickedSub=2 cellPickedSlot=0 cellSource=per-sub
wifiRssi=-52 wifiCalc5=4 wifiStockBars=4 wifiDots=3 wifiSource=active
```

The snapshot is a point-in-time read; it must not be used to prove that the live value was fresh. Add a
`cellSampleAgeMs` or a `src=stale` marker so a stale snapshot is distinguishable.

### 3.6 Logging rules

- Log the new lines on a **value change** only: raw, count, maxIndex, dots, sub, slot, source, sim.
  The Vector capture has three refreshes within 3 ms with identical values (`:781-784`, `:797-800`);
  that is noise and it hides real transitions.
- Log `maxSignalLevels()` once per process at startup, with the reflection result and the fallback
  reason; do not re-reflect on every refresh.
- Log the selection line every refresh that changes `dots` or `src`; do not log it for every UI
  recomposition.
- Keep every line bounded; no per-RAT dump of all `CellSignalStrength` fields.

## 4. Mapping table

### 4.1 Three quantities the code currently conflates

- `raw`: `SignalStrength.level`, an integer. The plan's measured AOSP range is 0..4
  (`PHASE-4-PLAN.md:30`); the measured module line has `max=5` (`full-app/logcat.txt:781`,
  `PHASE-4-REPORT.md:18`).
- `count`: `CellSignalStrength.getNumSignalStrengthLevels()`, 5 on the measured SystemUI
  (`PHASE-4-PLAN.md:26-28`; module `max=5`). This is a count, not a raw value.
- `maxIndex`: the highest legal `raw`. If the platform returns levels 0..4, `maxIndex=4` and
  `count=5`; if it returns a 4-level index 0..3, `maxIndex=3` and `count=4`. The existing code never
  distinguishes these.

`SignalMapping.cellDots(rawLevel, stockLevels)` does:

```text
stock = max(1, stockLevels)
raw = clamp(rawLevel, 0, stock)
if raw == 0 -> 0
else -> clamp(ceil(raw * 4 / stock), 1, 4)
```

(`SignalMapping.kt:45-50`). It treats `stockLevels` as the maximum raw value, but callers pass the
**count**. That is the semantic defect.

### 4.2 Current behavior (for the code as written)

| `raw` | `count=4` | `count=5` | `count=6` | `count=8` |
|---|---|---|---|---|
| 0 | 0 | 0 | 0 | 0 |
| 1 | 1 | 1 | 1 | 1 |
| 2 | 2 | 2 | 2 | 1 |
| 3 | 3 | 3 | 2 | 2 |
| 4 | 4 | 4 | 3 | 2 |
| 5 | 4 (clamped) | 4 | 4 | 3 |
| 6 | 4 | 4 | 4 | 3 |
| 7 | 4 | 4 | 4 | 4 |
| 8 | 4 | 4 | 4 | 4 |

Key rows:

- A full 4-level **index** source (`raw=3, count=4`) becomes **3**, not 4. This is a mapping bug if
  the 4-level source really has max index 3; no 4-level capture exists yet.
- A full 5-level **index** source (`raw=4, count=5`) becomes **4**. The measured OOS case is correct
  for full.
- A full 6- or 8-level source (`raw=5, count=6`; `raw=7, count=8`) becomes **4**; the current
  formula recovers full at the top. It only fails when the feed is a 4/5-level value and the count is
  over-reported.
- A 4-level value with `count=8` (`raw=4`) becomes **2**, and `raw=3` becomes **2**; a 4-level value
  with `count=6` (`raw=4`) becomes **3**. This is the count-overreport hypothesis for full -> 3.

### 4.3 Proposed normative table (`maxIndex` + round)

The proposal is to log and use `maxIndex` in a new function (working name `cellDotsIndexed`), keeping
the existing `cellDots(raw, stockLevels)` for compatibility until the capture confirms the scale:

```text
dots = 0                              if raw <= 0
dots = round(4 * raw / maxIndex)      otherwise, clamped to 1..4
```

with integer rounding `(4 * raw + maxIndex / 2) / maxIndex`. This maps the top raw to 4 for every
`maxIndex >= 1`, and it keeps the measured OOS 5-level table unchanged.

| `raw` | 5-level OOS, `maxIndex=4` (count measured; index inferred) | 4-level AOSP, `maxIndex=3` (candidate) | 5-bar OOS, `maxIndex=5` (if raw is bar count) | 4-bar AOSP, `maxIndex=4` (if raw is bar count) |
|---|---|---|---|---|
| 0 | 0 | 0 | 0 | 0 |
| 1 | 1 | 1 | 1 | 1 |
| 2 | 2 | 3 | 2 | 2 |
| 3 | 3 | 4 | 2 | 3 |
| 4 | 4 | - | 3 | 4 |
| 5 | - | - | 4 | - |

Reading the table:

- **5-level OOS, raw 0..4**: 0,1,2,3,4. This is the normative CPH2747 table and matches the current
  output; no visible OOS change is expected.
- **4-level AOSP, raw 0..3**: 0,1,3,4. The important boundary is full (3) -> 4. The middle choice
  (2 -> 3) is a design choice; the binding rule is "full must be 4" and "no full -> 1".
- **If the platform's raw is a bar count 1..5**: 4/5 -> 3, 5/5 -> 4. This avoids the current
  premature full at 4/5.
- The alternative current table is identity for 5-level and 4-level raw 0..4; it is safe only if the
  capture proves the raw maximum equals the count, not `count - 1`.

### 4.4 Dual-SIM per-sub source table

| User setting | Code path | Today's log evidence | Failure mode | Required new source value |
|---|---|---|---|---|
| `auto` | `defaultLevel` from the default `TelephonyManager.signalStrength` (`CellReader.kt:24-27`; `SignalMapping.kt:62`) | `sim=auto`, `subs=[2,1]`, one `cellLevel` | default voice/data sub may not be the stock icon the user compares | `src=default`, `defaultSub=<id>` |
| `sim1` | `signals.firstOrNull { slot == 0 }?.level` (`SignalMapping.kt:60`) | `sim=sim1`, `levels` in slot order | slot 0 missing -> `defaultLevel` (other SIM) | `src=per-sub`, `slot=0`, `sub=<id>`; `fallback=true` when missing |
| `sim2` | `signals.firstOrNull { slot == 1 }?.level` (`SignalMapping.kt:61`) | `sim=sim2`, `levels` in slot order | slot 1 missing -> `defaultLevel` | same, `slot=1` |
| per-sub read | `createForSubscriptionId(subId).signalStrength` (`CellReader.kt:69-73`) | not named per line | exception -> `0` (`CellReader.kt:76-79`) | `src=per-sub` or `src=unavailable`, never a silent 0 |
| list read | `activeSubscriptionInfoList.sortedBy(simSlotIndex)` (`CellReader.kt:50-58`) | `subs=[...]` | exception -> empty list (`CellReader.kt:61-64`), then all selections fall back | `src=unavailable`, `fallback=true` |

The measured OOS log has both subscriptions (`subs=[2,1]`) and both levels 4, so it cannot show
cross-talk. The dual-SIM UI itself is proven: two `mobile_combo` views at x=983 and x=1044
(`phase0-oos16.txt:116`, `:130`) and `subId:1/slotId:1` updates at 00:10:28.078/563.

## 5. Why `full -> 1 / 3 / 4` is possible

### 5.1 What is provable

1. **Full -> 4 is the normal result on the measured 5-level source.** `cellDots(4,5)=4`; the
   CPH2747 report shows 4 dots with `max=5` (`PHASE-4-REPORT.md:18`, `:24`). The Vector capture shows
   3 dots with `max=5` and `level=3` (`full-app/logcat.txt:781`), which is consistent with raw 3, not
   with a full bar.
2. **Full -> 3 is possible only if the raw fed is below the top, or the scale is over-reported.**
   For `raw = maxIndex` (the actual top), the current formula returns 4 for `count=5/maxIndex=4`,
   `count=6/maxIndex=5` and `count=8/maxIndex=7`; it returns **3** for the 4-level index case
   (`count=4`, `maxIndex=3`, `raw=3`). The other full -> 3 paths are:
   - a 4/5-level raw value with `count=6` (`raw=4` -> 3) or `count=8` (`raw=4` -> 2, `raw=5` -> 3),
     which requires `getNumSignalStrengthLevels()` to return 6 or 8; no measured evidence;
   - a feed that is one step below the top while the user calls the stock icon full, e.g. raw 3 on a
     5-level source (`cellDots(3,5)=3`).
3. **Full -> 1 is impossible from the current formula when `raw = maxIndex` for `maxIndex` 1..8.**
   The top raw returns 3 only in the 4-level index case; every other top returns 4. `dots=1` requires
   `raw=1` (count 4/5/6) or `raw=2` (count 8). A full bar cannot produce it unless the value fed is not
   the full bar.
4. **The code can feed a different bar than the user is looking at.** `cellLevel` is one value; the
   stock bar has two `mobile_combo` icons. `simChoice` selects by slot, but a missing slot falls back
   to the default line, and `auto` uses the default `TelephonyManager` read. The report never logs the
   chosen sub/slot (`CellReader.kt:111-118`, `DuoStateMonitor.kt:566-576`). This is the leading
   explanation for `full -> 1` or `full -> 3` on a dual-SIM phone: the user compares Duo with SIM A,
   while Duo is showing SIM B or the default line.

### 5.2 Evidence vs hypothesis

| Candidate | Evidence | Status | What would confirm it |
|---|---|---|---|
| Wrong SIM / default-line cross-read | `subs=[2,1]` (`PHASE-4-REPORT.md:18`), two `mobile_combo` views (`phase0-oos16.txt:116`, `:130`), separate `cellSignals()` + `cellLevel()` reads (`DuoStateMonitor.kt:566-568`), fallback to `defaultLevel` (`SignalMapping.kt:58-63`) | **Proven possible; occurrence unproven** | new `cell pick` line with `sub`/`slot`/`src` and the stock icon screenshot at the same time |
| Stale value after a failed read | `cellLevel` returns `current` on failure (`CellReader.kt:29-31`); `cellLevel` starts at 4 (`DuoStateMonitor.kt:50`) | **Proven possible; no sample** | `src=stale` and a timestamp/age in the log |
| Per-sub read failure becomes 0 | `levelForSubscription` returns 0 on exception (`CellReader.kt:76-79`) | **Proven code path; would show 0, not 1/3** | `src=unavailable`, `raw=-1` |
| 4-level index full -> 3 | `cellDots(3,4)=3`; no 4-level capture | **Hypothesis; mapping bug if true** | a 4-level ROM capture with `maxIndex=3`, `raw=3` and stock full |
| Count over-reported to 6/8 | `maxSignalLevels` allows 1..8 (`CellReader.kt:85-94`); test covers 6 (`DuoMappingTest.kt:205`) | **Hypothesis; no measured 6/8** | `count=6/8` from `getNumSignalStrengthLevels()` on the device |
| Primary RAT mismatch (LTE vs NR) | OOS `mLte.level=4`, `mNr.level=1`, `primary=Lte` at 00:10:28.805 (`:5099`); stock 5G icon may use NR | **Hypothesis; would explain a one-step difference, not full -> 1 alone** | log `rat` plus the stock 5G icon's level; capture both NR and LTE |
| Comparing the wrong stock icon | two `mobile_combo` views; one Duo row | **Hypothesis; user-facing, not code** | screenshot with both stock icons and Duo labelled |
| 8-dot dual-SIM design re-maps 4 dots | no 8-dot mapping in the allowed code; plan only (`AGILE-1.7-PLAN.md:36`, `:58`) | **Unverified; out of this code path** | the 8-dot implementation and its tests |

### 5.3 What cannot explain it

- `SignalMapping.sphereOpacities` / `cellOpacities` are a direct 0..4 -> four-sphere mapping
  (`SignalMapping.kt:66-69`, `:81-91`); they cannot turn 4 into 1 or 3.
- `selectedCellLevel` returns a value from the list or `defaultLevel`; it does not transform it
  (`SignalMapping.kt:58-63`).
- The current `cellDots` cannot map `raw = maxIndex` to 1 for any maxIndex 1..8, as shown in section
  5.1. A report of full -> 1 is therefore a **source** bug until the new log proves otherwise.

## 6. Unit tests

### 6.1 Pure mapping tests (extend `DuoMappingTest.kt:191-206`)

1. `stock 5-level OOS maps full to four dots`:
   `cellDotsIndexed(0,4)=0`, `1->1`, `2->2`, `3->3`, `4->4` for `maxIndex=4`.
2. `stock 4-level index maps full to four dots`:
   `cellDotsIndexed(0,3)=0`, `1->1`, `2->3`, `3->4` (the proposed `maxIndex` + round table). This is
   the regression test for the only full -> 3 mapping path.
3. `count is not maxIndex`:
   `cellDotsIndexed(4,4)=4` (maxIndex 4) vs `cellDotsIndexed(4,5)=3` (maxIndex 5). This test forces
   the API to name which one it takes.
4. `full is four for every tested maxIndex`:
   for `m` in 1..8, `cellDotsIndexed(m,m)=4`; for `m=0`, define and test the fallback.
5. `clamping`: `cellDotsIndexed(-1,4)=0`, `cellDotsIndexed(99,4)=4`.
6. `stock 4-bar / 5-bar Wi-Fi table`: `wifiBars(0)=0`, `1->1`, `2->1`, `3->2`, `4->3`;
   `wifiAsSpheres(0)=0`, `1->2`, `2->3`, `3->4`.
7. `Wi-Fi stock bar shift` is pinned: for the measured RSSI values -52/-53/-50, the pure helper must
   return `stockBars=4`, `dots=3`; if `calculateSignalLevel` changes, the test fails.

### 6.2 Selection tests (extend `DuoMappingTest.kt:174-189`)

1. slot 2 only, `sim2` -> 4; `sim1` -> default 2; `auto` -> default 2 (existing).
2. both slots, `[slot0=3, slot1=4]`: `sim1=3`, `sim2=4`, `auto=default 1` (existing).
3. **new**: selected slot missing returns `(dots=default, src=fallback, slot=-1, sub=-1)`; it must not
   silently report `src=per-sub`.
4. **new**: both slots active with different levels; each `sim` choice reports the matching slot and
   subId; no cross-talk.

### 6.3 Diagnostic-format tests (new `CellDiagnosticTest.kt`)

1. `cell sample` with `raw=4, count=5, maxIndex=4, dots=4` parses and preserves raw != dots semantics.
2. `cell sample` with `raw=4, count=6, maxIndex=5, dots=3` proves the fields are not aliases.
3. `cell pick` with `fallback=true` and `slot=-1` parses; the parser must reject a missing `src`.
4. `wifi sample` with `rssi=-52, calc5=4, stockBars=4, dots=3` parses.
5. `legacy-only` line is reported as `legacy-only`, not `PASS`.

### 6.4 Checker tests (`tools/check-cell-report.py`)

1. Synthetic report with `cell raw level=4 ... max=5` and snapshot `cellLevel=1` -> FAIL
   (`dots(4,5)=4` vs 1).
2. Synthetic new line `raw=4, count=5, maxIndex=4, dots=1` -> FAIL.
3. Synthetic new line `raw=3, count=5, maxIndex=4, dots=3` -> PASS.
4. Synthetic 4-level line `raw=3, count=4, maxIndex=3, dots=4` -> PASS only with the new table.
5. `--self-test` should run these fixtures without a device.

## 7. Minimal code change surface (no edits in this task)

| File | Lines to touch | Change | Risk |
|---|---|---|---|
| `app/src/main/java/.../hook/SignalMapping.kt` | 8-9, 45-50, 58-63 | Add `CellSample`/`Selection` data; add `cellDots(raw, maxIndex)` using round; keep the old `cellDots` until the capture confirms `maxIndex`; make selection return `dots + src + slot + subId` | Low, pure; unit tests catch |
| `.../hook/CellReader.kt` | 22-32, 39-40, 47-79, 85-94, 111-118 | Return raw + count + maxIndex + dots per sub; cache `maxSignalLevels` and log its source once; keep current on single read, mark per-sub failure instead of silent 0; add new diagnostic line | Medium; Android APIs, permission; no UI change |
| `.../hook/DuoStateMonitor.kt` | 558-581 | Read `cellSignals` once and derive the default from the same snapshot; pass selection source to the diagnostic; log on change only | Medium; avoid duplicate refresh noise |
| `.../hook/Diag.kt` | 264-280 | Add raw/count/maxIndex/slots/picked sub/source to the readers section; keep existing labels | Low; log-only |
| `.../hook/WifiReader.kt` | 25-46, 49-55 | Add `WifiSample` (rssi, calc5, stockBars, dots, source); log the new `wifi sample` line | Low; no visual change |
| `tools/check-cell-report.py` | 23-35, 72, 85 | Parse new lines; reject legacy-only; compare `dots` to the `maxIndex` table; add self-test | Low; tool-only |
| `app/src/test/.../DuoMappingTest.kt` | 153-206 | Add the tables and selection-source assertions | Low |
| `app/src/test/.../CellDiagnosticTest.kt` | new file | Format/parser tests; no Android runtime | Low |

Deliberately **not** changed: `DuoBinder`, `DuoVisual`, `DuoCanvasView`, the Rive scene, and the
`cellLevel` 0..4 contract. The display still receives four dots; only the value and evidence change.

The `cellDots` change is gated:

- If the capture proves `maxIndex = count` (5-level OOS, raw 0..4), the current formula is already
  correct for full; keep it and only fix the diagnostics/selection.
- If the capture proves `maxIndex = count - 1` (4-level index), switch to the round/maxIndex table.
- If the capture proves raw is a 5-bar count (0..5), use `maxIndex=5`; do not use `count=5` with raw 5
  and call it malformed.

## 8. Missing evidence and the one-time capture (later, not now)

### 8.1 Missing evidence

1. No module line with both `raw` and `dots`; every existing `cell raw` line is mapped.
2. No `pickedSub`/`pickedSlot`/`source`; `subs=[...]` is the active list, not the chosen line.
3. No `maxIndex`; `max=5` is a count, and no line says whether raw 0..4 or 0..5 is legal.
4. No raw for subId 1/slot 1; only its presence at 00:10:28.078/563.
5. No weak 0/1 sample; the lowest observed OOS LTE level is 2.
6. No 4-level ROM sample; the only measured `max` is 5 (Vector and CPH2747).
7. No aggregate `SignalStrength.getLevel()` in the OOS stock line; only per-RAT levels.
8. No `MobileSignalController` `level`/`numLevels` line; `getNumLevels()` exists but is not logged.
9. No stock Wi-Fi bar maximum; `level=4` is logged without `maxBars`.
10. No screenshot paired with each level line, so the "full" the owner compares is not identified as
    SIM1, SIM2 or the combined icon.
11. No airplane/off or no-service module sample; full-app has `airplane=false`.

### 8.2 Exact capture to run once the phone is allowed

Use a debug build that contains the new `cell sample`/`cell pick`/`wifi sample` lines from section 3.
Do not change the visual mapping before this capture.

1. `adb logcat -c`
2. Start a bounded capture:
   `adb logcat -b main -v threadtime > cell-wifi-$(date +%Y%m%d-%H%M%S).log`
3. On the phone, for each state below, wait 3-5 seconds, take one screenshot of the stock bar plus Duo,
   and note the state in the capture file:
   - SIM1: full, 3/4, 2/4, 1/4, 0 (or as close as the radio allows);
   - SIM2: the same sequence;
   - `sim=auto` with both SIMs active;
   - airplane on/off;
   - Wi-Fi off, on but not connected, connected at -50, -65, -75, -85 dBm (or as close as the AP allows).
4. For each state, pull one snapshot:
   `adb shell dumpsys telephony.registry > telephony.txt`
   `adb shell dumpsys connectivity > connectivity.txt`
   `adb shell dumpsys wifi > wifi.txt`
   `adb shell settings get global mobile_data`
5. Stop logcat. Extract the module lines:
   `grep -E "DuoSB.*(cell sample|cell pick|wifi sample|cell raw)" cell-wifi-*.log`
6. Acceptance per state: one `cell sample` for each active sub, one `cell pick`, one `wifi sample`;
   the `sub`/`slot` in `cell pick` matches the stock icon the screenshot labels; `raw`/`count`/
   `maxIndex` are present; `dots` is 0..4.
7. Repeat step 1-6 on a 4-level AOSP/older ROM. If no second device exists, record the 4-level path as
   `unverified` and do not change the 4-level branch.

The minimum decisive capture is one full and one 1-bar state on each SIM, with the stock screenshot
labelled. That alone distinguishes:

- wrong SIM/source: `cell pick` says subId 1 while the screenshot's full icon is subId 2;
- stale: `src=stale`, `raw=-1`, old `dots`;
- 4-level mapping: `count=4`, `maxIndex=3`, `raw=3`, `dots=3` while the stock icon is full;
- count overreport: `count=6/8` with raw 4/5;
- primary RAT: `rat=Lte` with `mLte=4` but `mNr=1` while the 5G icon is full.

## 9. Exit criteria for the fix

1. The new diagnostics are present in a real report; the legacy line remains for compatibility.
2. A full stock bar on each active SIM produces `raw=maxIndex` and `dots=4`; a 1-bar state produces
   `dots=1`; no `full -> 1` or `full -> 3` in the capture.
3. `cell pick` names the exact subId/slot/source and `fallback` for every state.
4. `tools/check-cell-report.py` fails a legacy-only report and passes only when
   `dots` matches the logged `raw`/`maxIndex`.
5. The 4-level branch is either measured or explicitly `unverified`; it is not guessed.
6. `T-RING2-05` and `RING2-05` can be marked PASS with the capture, not with the current mapped-only
   line.

## Appendix A - exact excerpts used

```text
# docs/evidence/full-app/logcat.txt:759-764
10-31 20:49:32.094  1103  1103 I DuoSB   :   wifiEnabled=true wifiActive=false wifiValidated=false wifiLevel=0
10-31 20:49:32.094  1103  1103 I DuoSB   :   cellLevel=3 generation=5G mobileData=true cellLevels=[3]
10-31 20:49:32.094  1103  1103 I DuoSB   :   airplane=false saver=false dnd=false

# docs/evidence/full-app/logcat.txt:781-784
10-31 20:49:32.128  1103  1103 I DuoSB   : cell raw level=3 levels=[3] generation=5G max=5 subs=[1] airplane=false sim=auto
10-31 20:49:32.128  1103  1103 I VectorLegacyBridge: DuoSB | cell raw level=3 levels=[3] generation=5G max=5 subs=[1] airplane=false sim=auto
10-31 20:49:32.131  1103  1103 I DuoSB   : cell raw level=3 levels=[3] generation=5G max=5 subs=[1] airplane=false sim=auto
10-31 20:49:32.131  1103  1103 I VectorLegacyBridge: DuoSB | cell raw level=3 levels=[3] generation=5G max=5 subs=[1] airplane=false sim=auto

# agent/PHASE-4-REPORT.md:17-18
cellLevel=4 generation=5G mobileData=true cellLevels=[4, 4]
cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] airplane=false sim=auto

# reverse/fatal-logs/logcat/main_tail30000.txt:5099 (wrapped for readability)
11-03 00:10:28.805  7623  7623 D OplusTelephonyController: onSignalStrengthChanged:
  ... mLte=CellSignalStrengthLte: rssi=-65 rsrp=-97 rsrq=-13 rssnr=14 ... level=4 ...
  mNr=CellSignalStrengthNr: ... ssRsrp=-102 ssRsrq=-11 ssSinr=18 level=1 ...
  primary=CellSignalStrengthLte
# reverse/fatal-logs/logcat/main_tail30000.txt:5102
11-03 00:10:28.806  7530  8229 I SystemUi--Statusbar: MobileConnectionRepositoryImpl-->subId:2 isSoftSimCard:false slotId:0

# reverse/fatal-logs/logcat/main_tail30000.txt:5311-5314
11-03 00:10:31.659  7530  8151 I SystemUi--Statusbar: NetworkController.WifiSignalController-->handleStatusUpdated state=3 enabled=true connected=true isDefaultNetwork=true rssi=-50 level=4 statusLabel=null isDirty=false
```

## Appendix B - line anchors used

- `CellReader.kt:22-28`, `39-40`, `47-59`, `61-64`, `67-79`, `85-94`, `106-118`
- `SignalMapping.kt:8-9`, `19-24`, `45-50`, `58-63`, `66-69`, `81-104`
- `WifiReader.kt:25-46`, `35-39`, `49-55`, `74-82`
- `DuoStateMonitor.kt:49-50`, `558-581`, `566-576`
- `Diag.kt:264-280`
- `tools/check-cell-report.py:32-35`, `72`, `83-87`
- `app/src/test/.../DuoMappingTest.kt:153-161`, `174-189`, `191-206`
- `docs/rom-contract.md:10-11`
- `docs/evidence/full-app/logcat.txt:759-784`, `789-802`
- `docs/evidence/phase0-oos16.txt:16-17`, `102-143`
- `agent/PHASE-4-REPORT.md:17-18`, `24-27`, `30`, `60`, `64`
- `agent/PHASE-4-PLAN.md:26-31`, `78-80`, `92-99`
- `agent/PHASE-1-5-WALKTHROUGH.md:68`, `74`
- `agent/AGILE-1.7-PLAN.md:36`, `46-47`, `58`
- `reverse/fatal-logs/logcat/main_tail30000.txt:201-203`, `591`, `594`, `905-908`, `1112`, `1114`, `1925-1930`, `2576-2582`, `3386-3391`, `4133`, `4148`, `4387-4390`, `4522`, `4529`, `4727-4732`, `4971`, `4986`, `5023-5026`, `5099`, `5102`, `5311-5314`
