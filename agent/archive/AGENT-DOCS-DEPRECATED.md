# AGENT-DOCS-DEPRECATED.md — archived agent documents

**Status:** deprecated as of 2026-11-06. Not current instructions.

**Source of truth:** [`../AGENTS-V2.md`](../AGENTS-V2.md).

**Why archived:** the `agent/` folder accumulated overlapping plans, requirements, test plans, phase reports, review reports and evidence notes from Plan v2 through 1.8.0-alpha.8. Several describe states that no longer exist. They are preserved here and under `archive/originals/` as history and evidence only.

---

## Table of contents

| File | One-line purpose (first heading) |
|---|---|
| `AGENT.md` | AGENT.md — Session Anchor |
| `AGILE-1.7-CELL-MAP.md` | Agile 1.7 - cell/Wi-Fi level evidence and fix-ready mapping plan |
| `AGILE-1.7-COLORDUO.md` | ColorDuo 1.1.7 - navigation/selector glass values |
| `AGILE-1.7-PLAN.md` | Duo Status Bar — Agile 1.7 Plan |
| `AGILE-1.7-REQUIREMENTS.md` | Duo Status Bar — Agile 1.7 Requirements |
| `AGILE-1.7-TEST-PLAN.md` | Duo Status Bar — Agile 1.7 Test Plan |
| `AGILE-1.7-TRANSITIONS-AFTER.md` | Agile 1.7 — transitions after crossfade fix (de48022) |
| `AGILE-1.7-TRANSITIONS.md` | Agile 1.7 — HUD / QS / shade transition measurement |
| `AGILE-1.8-ALPHA11.md` | AGILE 1.8 — alpha.11 on-device verification attempt |
| `AGILE-1.8-NOTES.md` | AGILE 1.8 — 1.8.0 alpha notes |
| `AGILE-1.8-PLAN.md` | Duo Status Bar — Agile 1.8 Plan |
| `AGILE-1.8-REQUIREMENTS.md` | Duo Status Bar — Agile 1.8 Requirements |
| `AGILE-1.8-TEST-PLAN.md` | Duo Status Bar — Agile 1.8 Test Plan |
| `APP-TEST-PLAN.md` | Full app test plan — beta.40 |
| `BRAINSTORM-2026-11-02.md` | Brainstorm — Duo 1.6 Customization, Ring Decomposition, Downloads, Stability |
| `BRAINSTORM.md` | BRAINSTORM — Multi-UI Update Discovery Summary |
| `CODE-REVIEW-PLAN-1.7.0.md` | Duo Status Bar — Code Review Plan (1.7.0 stable) |
| `CODE-REVIEW-PLAN-1.8.0.md` | Duo Status Bar — Code Review Plan (1.8.0) |
| `CODE-REVIEW-REPORT-1.7.0.md` | Duo Status Bar — Code Review Report (1.7.0 stable) |
| `DECISIONS.md` | Decisions (ADR Log) |
| `DEVICE-TEST-REQUEST-1.8.0.md` | Device test request — 1.8.0-alpha.8 |
| `DEVICE-TEST-REQUEST-2026-11-04.md` | Device Test Request — 2026-11-04 (CPH2747) |
| `EMULATOR-CALL-TEST.md` | Emulator call test — PASS |
| `EMULATOR-LOCK-TEST.md` | Emulator lock → home test — PASS |
| `EMULATOR-TEST-MATRIX.md` | Emulator + cross-UI test matrix |
| `EVENT-ARCHITECTURE.md` | Live-event architecture — Duo Status Bar |
| `EVENT-ICON-CANDIDATES.md` | Event icon candidates — phone + eight UIs |
| `EVENT-ICONS-PULL-REPORT.md` | Event icon extraction — phone + eight UIs |
| `EVIDENCE.md` | Evidence Protocol |
| `ICON-MISTAKES.md` | Icon drawing and animation — mistakes ledger |
| `ICON-REDRAW-PLAN.md` | Owned icon redraw + animation plan |
| `KEYGUARD-CROSS-UI-AUDIT.md` | Keyguard unlock hook — cross-UI class audit |
| `LIST-THE-PLAN.md` | List the Plan — Pre-Plan Evidence and Change Inventory |
| `MODEL-ROUTING.md` | Model and Agent Routing |
| `PHASE-1-5-WALKTHROUGH.md` | Phase 1–5 walkthrough, review and gap-closure plan |
| `PHASE-1-CHECKPOINT.md` | Phase 1 Checkpoint — Shade/QS Repair |
| `PHASE-1-REPORT.md` | Phase 1 Report — Shade / QS / Header Surfaces |
| `PHASE-1-TEST.md` | Phase 1 — Shade/QS Test Sheet |
| `PHASE-2-REPORT.md` | Phase 2 Report — Testing, logging, repair |
| `PHASE-3-REPORT.md` | Phase 3 Report — App customization (shell, engine, presets, backup) |
| `PHASE-4-PLAN.md` | Phase 4 — Cell accuracy (plan, 2026-10-28) |
| `PHASE-4-REPORT.md` | Phase 4 — Cell accuracy report (prototype) |
| `PHASE-5-PLAN.md` | Phase 5 — Liquid glass + three-page shell (plan) |
| `PHASE-5-REPORT.md` | Phase 5 report — beta.14 (2026-10-29) |
| `PHASE-6-REPORT.md` | Phase 6 report — Rive animation system (prototype) |
| `PHASE-7-REPORT.md` | Phase 7 report — multi-ROM maps, adaptive glass and performance |
| `PHASE-8-REPORT.md` | Phase 8 report — release readiness |
| `PLAN-1.6.md` | Duo Status Bar — Implementation Plan 1.6 |
| `PLAN-v2.md` | Duo Status Bar — Implementation Plan v2 (Multi-UI Update) |
| `RELEASE-NOTES-1.7.0.md` | Duo Status Bar 1.7.0 — stable |
| `REPAIR-CATALOG.md` | Duo Repair Catalog |
| `REQUIREMENTS-1.6.md` | Duo Status Bar — Requirements 1.6 |
| `REQUIREMENTS-GAP-AUDIT.md` | Requirements gap audit — after phone + 8-UI icon extraction |
| `REQUIREMENTS.md` | Duo Status Bar — Requirements v2 (Multi-UI Update) |
| `ROM-PROFILE-ASSET-AUDIT.md` | ROM profile asset audit — static maps vs decoded APKs |
| `SCREENSHOT-SOURCE-AUDIT.md` | Screenshot source audit — 8 vendor SystemUI APKs + CPH2747 |
| `TEST-PLAN-1.6.md` | Duo Status Bar — Test Plan 1.6 |
| `TEST-PLAN.md` | Duo Status Bar — Test Plan v2 (Multi-UI Update) |
| `UI-MATRIX.md` | Duo UI Matrix (8 vendor UIs + AOSP) |
| `WALKTHROUGH-PLAN-1.7.0.md` | Duo Status Bar — Walkthrough Plan (1.7.0 stable) |
| `WALKTHROUGH-PLAN-1.8.0.md` | Duo Status Bar — Walkthrough Plan (1.8.0) |
| `WALKTHROUGH-REVIEW-1.5.0.md` | Duo Status Bar 1.5.0 — walkthrough and code review |
| `WALKTHROUGH-REVIEW-PHASES-1-8.md` | Walkthrough & review — Phases 1–8 |

---

## FILE: AGENT.md

# AGENT.md — Session Anchor

**Read this file first in every session.** It is the 30-second orientation, the non-negotiable rules, and the current state.
Last updated: 2026-10-28. Owner: Abdulkarim.

## 1. What this project is

Duo Status Bar is an LSPosed module that replaces the stock battery/status icons with one animated Duo element. It runs inside SystemUI, must never break the stock bar, and currently supports measured OnePlus OxygenOS 16 (CPH2747) and Xiaomi HyperOS 3 (24129PN74G).

Phase 1 merged to `main` at `5485ebf`; Phase 2 merged/committed at `1ad00b8`. Active branch: `phase/3-app-customization` (shell, engine selector, presets, backup, middle-slot).

**Interactive-icon note (owner):** fixed states (Wi-Fi, 5G/4G, airplane, clock, battery %) are different from live events (alarm, screenshot, mic, location, volume, unlock). Live events need an Event Layer: icon appears, Rive animation plays, then it fades and the previous middle occupant returns. **Animation is per event type, not per icon**: multiple icon variants share one event animation (e.g. four alarm icons -> one alarm animation; 16 icons and 4 animations = 4 animations + an icon→animation map, not 64 animations). Architecture, workflow and test matrix are in `agent/EVENT-ARCHITECTURE.md`; project memory key `event_interactive_icons_architecture`. Do not implement as a static toggle or placeholder text. Answer the open questions in that file before building the Rive event layer.

## 2. Current state (2026-10-28, Phase 3 WIP)

- **Phase 1** merged to `main` at `5485ebf`. **Phase 2** committed at `1ad00b8`. **Phase 3** at `6649a56` (per-icon Wi-Fi/cellular visibility) + current work on `phase/3-app-customization`.
- **Phase 3 partial (device-smoke-tested on CPH2747):** three-page shell (Home/Engine/Settings), Home live preview, engine selector (Auto/Rive/Canvas) with DuoGuard, Classic/Compact/Bold presets, backup/import core (duo-settings/1, allowlist/exclusions, confirmation, SAF) unit-tested only, middle-slot choices (auto/wifi/cell/percent/clock/charging) implemented (unit-tested; Rive visual unverified), per-icon Wi-Fi/cellular toggles (6649a56).
- **Event layer (WIP on phase/3-app-customization):** contract/arbit committed `377bf7a` (LiveEvent/EventArbiter, newest-wins); per-event toggles `f83b931`; flashlight source confirmed firing (`f8995e5`, measured 2026-10-28: hook loaded in 4 SystemUI processes, `live event -> FLASHLIGHT` fired 6 times each followed by `none`). Alarm one-shot live event committed `fa939d4`: 1200 ms one-shot, no notification/ring-end source, NextAlarm is the only start source. Event icon shape seam defaults to one, extensible to four; per-event switches. No visual/Rive event layer yet.
- **Tests:** 315 unit tests (last run, 0 failures); installed build 1.4.3-beta.1 (versionCode 24). Bridge skew bug (73/77 vs 77/79) fixed in `5593601`; APK `fbc197f3af7e55311906e4fb5a17bdf086bc5291c02ec973136ca25583906c40`. Flashlight source confirmed firing (measured 2026-10-28).
- **Branch:** `phase/3-app-customization`; safe-mode fix branch separate at `d24b448` (`fix/landscape-safe-mode`).
- **Safe-mode evidence:** latest SystemUI tombstone was LuckyTool/DexKit, not Duo; see `from users/safe-mode-20261028/`. No device test for middle-slot; Rive visual needs verification.
- **Remaining:** 4+4 layouts, battery bar, Wi-Fi charge slot, icon packs, per-icon matrix, question-mark simulations, live events, Rive/Canvas middle-slot parity; no device test for backup SAF/landscape.
- **EOL caveat:** working tree has mixed CRLF/LF; normalize before a real commit, do not mass-rewrite now.

## 3. Fatal rules

1. **Never guess** a class, id, hook, resource, or log string. Read it from the device/APK.
2. **No code before** Requirements + Test Plan + Plan v2 are complete and approved.
3. **Never force/remove** the stock battery or patch vendor code. Hook the existing host, draw Duo, restore exactly.
4. **Fail silent:** a failure leaves the stock bar working; disable the surface and log the reason.
5. **No new core architecture or hook framework.** Follow the existing `DuoIconHost` / `ContainerFinder` / `RomAdapter` patterns.
6. **No commits to `main`.** One branch per phase; merge after the gate.
7. **Every Rive design has a Canvas clone.**
8. **Every function that can fail logs** UI/surface/class/hook/reason; no invisible failures.
9. **No PII** in logs/reports: no SSID, IMEI, serial, accounts, location, notification text.
10. **All file names and file contents are English.**
11. **Use the tools, not memory.** The repair catalog and the dump are the source of truth.
12. **Model rule:** **BEAM never writes or edits code.** BEAM is only for search, inventory, log/docs extraction, and mechanical build/install/test commands. Code edits are only `deepseek-v4.1-flash` (or the main agent). **Never use GLM.**

## 4. Read order

1. `agent/AGENT.md` (this file)
2. `agent/REQUIREMENTS.md`
3. `agent/TEST-PLAN.md`
4. `agent/PLAN-v2.md`
5. `agent/UI-MATRIX.md`
6. `agent/REPAIR-CATALOG.md`
7. `weapons/INVENTORY.md`
8. The current diagnostic dump / LSPosed log

## 5. File map

```text
agent/
  AGENT.md              session anchor (this file)
  README.md             folder index
  REQUIREMENTS.md       v2 requirements + checklist
  TEST-PLAN.md          v2 test plan + matrices
  PLAN-v2.md            phased implementation plan
  REPAIR-CATALOG.md     log -> cause -> tool -> fix -> verify
  UI-MATRIX.md          8 UIs + AOSP, evidence tiers
  BRAINSTORM.md         discovery summary
  MODEL-ROUTING.md      cheap/strong agent routing
  EVIDENCE.md           evidence protocol
weapons/
  README.md             tool catalog
  INVENTORY.md          installed versions/paths
  MCP.md                REA, jadx-ai-mcp, Kahlo, etc.
  SKILLS.md             skills + agent types
  LIBS.md               DexKit, YukiHook, Rive, libxposed
  PLAYBOOKS.md          concrete recipes
  LOGS.md               log sources
  SETUP.md              install commands
brainstorms/
  2026-10-26-multi-ui-update-discovery.md
docs/                   existing project docs (ROM contract, design, evidence)
reverse/                local-only vendor APKs (gitignored)
corpus/                 local-only OEM corpus (gitignored; add before use)
diagnose from users/    private user dumps (gitignored)
```

## 6. Current next action

1. Boot `ae_api36_root` (rooted AOSP) and verify `adb root`.
2. Install `build/duo-shade-test-debug.apk`; if LSPosed is present, enable the module for **System UI**, restart SystemUI. Exact commands: `agent/PHASE-1-TEST.md`.
3. Open the shade; capture screenshot + LSPosed `DuoSB` log. Expected generic path: Duo replaces the cluster; stock battery hidden; restored on disable.
4. If it fails, add the exact log/screenshot to `agent/REPAIR-CATALOG.md`; do not guess. The OnePlus vendor hooks still need the phone (deferred).
5. After the Phase 1 device gate, implement Phase 7 per-UI profiles (static maps are ready; runtime dumps are missing for six UIs).

## 7. Open flags

- Device verification of Phase 1 is pending; the phone is off-limits and the emulator is not booted.
- OnePlus vendor hooks (`OplusQSSimpleHeader`, `OplusSplitShadeHeaderControllerExImpl`) are static-only; no device run yet.
- Runtime dumps for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS are missing -> `unverified`.
- Windows "Duo logs" not mounted.
- Working tree EOL is mixed; normalize before commit.
- PERF-005 active-use +2% is proposed.
- Support channel (Telegram) is later.
- OEM icon removal timing is the owner's responsibility.

## 8. Do not do

- Do not start coding because "it looks easy".
- Do not add a new hook framework, DI, database, or architecture.
- Do not hide every icon by default.
- Do not use `android.util.Log` inside SystemUI; use `L`.
- Do not run a full regression for a one-feature change; run the linked tests.
- Do not commit vendor APKs, OEM assets, user logs, keys, or secrets.
- Do not claim a UI is supported without a measured dump + screenshot + log.
- Do not use GLM or expensive models.


## FILE: AGILE-1.7-CELL-MAP.md

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


## FILE: AGILE-1.7-COLORDUO.md

# ColorDuo 1.1.7 - navigation/selector glass values

Read-only evidence report for AGILE 1.7 (GLASS2 / A5). No app code, build, install, or device setting was changed.

## 0. Artifact and method

| Item | Value |
|---|---|
| APK | `/tmp/colorduo-pull/io.github.fengwenhua.colorduo/base.apk` |
| APK SHA-256 | `d7441541b5ac1c4802ca0312ba1a1debf873580efa534cc16c1e69c2c45e8cd2` |
| Package | `io.github.fengwenhua.colorduo` |
| Version | 1.1.7 (versionCode 10107) |
| Screenshot | `/tmp/colorduo-screen/screen.png` (1272 x 2772, RGBA) |
| Tools | REA 0.7.1 JADX headless (`inspect_android_class`, `inspect_android_method`), local JADX source output, apktool smali |
| Runtime | APK was never executed; no phone used |

Important correction to the request: `LiquidNavigationBar`, `NavigationPill_N85Pf5M`, and `NavigationTabRow_FU0evQE` are **methods**, not static fields. The class has exactly two fields:

- `private static final float BarBlurRadius = 40.0f`
- `public static final String PROGRESSIVE_BLUR_ALPHA_MASK_SHADER = "\nuniform shader content;\nuniform float2 size;\nhalf4 main(float2 coord) {\n    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);\n    return content.eval(coord) * blurAlpha;\n}\n"`

DEX method names and descriptors:

- `LiquidNavigationBar(IIZLii0;Lm70;Lgq;I)V`
- `m20NavigationPillN85Pf5M(IIZLii0;Lzg1;JFFFFZZLm70;Lgq;III)V` (`NavigationPill_N85Pf5M` in JADX naming)
- `m21NavigationTabRowFU0evQE(IIJLes0;Lb70;Lm70;Lgq;II)V` (`NavigationTabRow_FU0evQE` in JADX naming)

The `NavigationPill_N85Pf5M$...` names are synthetic lambdas. The three navigation bar composables are variants of the same pill/tab system, selected by `SettingsNavigationKt` from `Prefs` (`DockedNavigationBar`, `FloatingNavigationBar`, `LiquidNavigationBar`).

The screenshot is the three-tab liquid layout in dark theme (`SettingsTab` entries: `Drawing`, `Parts`, `About`, displayed as Home, Components, About). For that layout the width formula below gives `0.54805998` (54.806% of available width); the dark-theme tint is `#121212` at 0.40 and the static highlight alpha is 0.78.

## 1. Exact values by navigation style

All dp values are Compose `Dp` literals multiplied by density at the draw site. All colors are Compose `Color` values; `copy(alpha)` is applied at the call site.

| Value | Liquid | Floating | Docked |
|---|---:|---:|---:|
| Pill height | **64.0 dp** | **56.0 dp** | no pill; full-width bar, 64.0 dp tab item |
| Pill width fraction | **0.47805998 + 0.07 * (tabs - 2)** | **0.62 + 0.07 * (tabs - 2)** | full width (`fillMaxWidth(1.0f)`) |
| Pill width, 3 tabs | **0.54805998 (54.806%)** | **0.69 (69%)** | 100% |
| Pill width, 4 tabs | **0.61805998 (61.806%)** | **0.76 (76%)** | 100% |
| Pill shape | **RoundedCornerShape(50%)** | **RoundedCornerShape(50%)** | RectangleShape |
| Effective pill radius | **32.0 dp** (50% of 64) | **28.0 dp** (50% of 56) | 0 dp |
| Tint, dark | **`#121212` at alpha 0.40** | **black at alpha 0.36** | **black at alpha 0.58** |
| Tint, light | **`#FAFAFA` at alpha 0.40** | **white at alpha 0.38** | **white at alpha 0.62** |
| Glass blur | **12.0 dp** | **12.0 dp** | **12.0 dp** |
| Lens refraction height | **24.0 dp** | **16.0 dp** | none |
| Lens refraction amount | **24.0 dp** | **32.0 dp** | none |
| Lens depth effect | true | true | n/a |
| Lens chromatic aberration | **true** | false | n/a |
| Moving selector height | none (rim/highlight follows the tab) | **48.0 dp** (56 - 8) | none |
| Moving selector width | none | **`(availableWidthDp - 8.0) / tabs`** | none |
| Moving selector background | none; full-width tint at 0.16 + highlight | **white 0.18 (dark) / black 0.08 (light)** | none |
| Liquid surface tint overlay | **tint color at 0.16** | none | none |

Exact source literals:

- Liquid: `m20NavigationPillN85Pf5M(..., new la1(f31(50.0f) x4), z ? tl.b(gf0.I(4279374354L), 0.4f) : tl.b(gf0.I(4294638330L), 0.4f), 0.47805998f, 64.0f, 24.0f, 24.0f, true, true, ...)`
- Floating: `m20NavigationPillN85Pf5M(..., new la1(f31(50.0f) x4), z ? tl.b(tl.b, 0.36f) : tl.b(tl.d, 0.38f), 0.62f, 56.0f, 16.0f, 32.0f, false, false, ...)`
- Docked: `z ? tl.b(tl.b, 0.58f) : tl.b(tl.d, 0.62f)`; `fillMaxWidth(1.0f)`; no `m20NavigationPillN85Pf5M` call.

`4279374354` = `0xFF121212`; `4294638330` = `0xFFFAFAFA`; `tl.b` = `0xFF000000`; `tl.d` = `0xFFFFFFFF`.

## 2. Selector geometry and movement

From `NavigationPill_N85Pf5M$lambda$12$0`:

```java
float fB = (jhVar.b() - 8.0f) / i;          // selector width, dp
float fZ = jvVar.z(fB);                     // same in px
...
gu1.w(xh1.c(xh1.h(es0VarG2, f4), f2 - 8.0f), zg1Var2);
```

- `jhVar.b()` is the available width in dp; `i` is the tab count.
- `xh1.h` is `Modifier.width`; `xh1.c` is `Modifier.height`.
- Selector width = `(availableWidthDp - 8.0) / tabs`; this is narrower than a tab slot by `8/tabs` dp.
- Selector height = `pillHeight - 8.0` for the non-liquid selector path: 48 dp floating. The same expression would give 56 dp for a 64 dp pill, but the liquid flag skips this branch; liquid selection is the rim/highlight, not a filled selector.
- Selector shape = `zt.u` = **`RectangleShape` (0 dp corner)**. It is clipped by the pill shape with `gu1.w(...)`, so the visible ends take the pill's 50% radius.
- Selector offset X = `round((position - (tabs - 1) / 2.0f) * direction * selectorWidthPx)`. `direction` is `-1`/`+1`; no extra 4 dp offset.
- Selector background:
  - dark theme: white (`tl.d`) at **0.18**;
  - light theme: black (`tl.b`) at **0.08**;
  - drawn with `gu1.r(..., zt.u)`.
- Liquid mode does not draw this per-tab selector. It draws the tint color at **0.16** over the full row (`tl.b(j2, 0.16f)`) and indicates selection with the highlight/rim below.

For the non-liquid selector path, 3 tabs: width = `(availableWidthDp - 8) / 3`; 4 tabs: `(availableWidthDp - 8) / 4`. Liquid has no filled selector; its selection indicator is the rim/highlight.

## 3. Glass pipeline (vibrancy -> blur -> lens)

The liquid/floating pill is built by the custom `ff0.u` glass surface with the same Kyant-style order used by Duo: colour filter, then blur, then lens. The exact callbacks are in `SettingsNavigationKt`:

1. **Vibrancy/saturation** - `vl.a(nd)` applies an Android `ColorMatrixColorFilter` (API 31+ as a `RenderEffect.createColorFilterEffect`). The literal matrix is:

```text
1.3935  -0.3575  -0.036   0   0
-0.1065  1.1425  -0.036   0   0
-0.1065 -0.3575   1.464   0   0
0        0        0       1   0
```

This is a saturation matrix. Derived from the coefficients (not a separate literal), it is **saturation = 1.5** with Rec.709 luminance weights (0.213, 0.715, 0.072). Duo's `GlassTokens.SATURATION = 1.5f` already matches this exactly.

2. **Blur** - `zt.g(nd, uy.e * 12.0f)`: a RenderEffect blur with **radius 12.0 dp** (converted to px with density). `zt.g` is a blur modifier, not a colour filter. It also records the max blur radius in `uy.i` for layer bleed.

3. **Lens/refraction** - `ws.F(nd, f * density, density * f2, true, chromatic)` sets the runtime shader uniforms:
   - `refractionHeight` = first arg (dp -> px);
   - `refractionAmount` = second arg (dp -> px), passed to the shader as `-f2`;
   - `depthEffect` = `true` -> 1.0;
   - `chromaticAberration` = `true` -> 1.0.
   - Liquid: **24.0 dp height / 24.0 dp amount**, depth true, chromatic true.
   - Floating: **16.0 dp height / 32.0 dp amount**, depth true, chromatic false.
   - Docked: no lens, only vibrancy + 12 dp blur.

The shader source (`ws.java`) confirms the uniform names and that chromatic mode uses a seven-tap dispersion (`red, orange, yellow, green, cyan, blue, purple`). `refractionAmount` is negated at upload; report the positive parameter values above.

### `BarBlurRadius` warning

`BarBlurRadius = 40.0f` is **not** the glass blur radius. R8 constant-folded the field; the only use is:

```java
new da0((navigationDragState.getVelocity() * BarBlurRadius) + 45.0f, 11)
```

That is the **highlight angle** sweep: `45 deg + 40 * velocity`. The actual glass blur is 12 dp. Do not port `40.0f` as a blur radius.

### `PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` warning

The exact public shader string is present in the field, but there is no smali reference to `SettingsNavigationKt;->PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` anywhere in the APK. It is defined but unused by this class; do not infer a progressive-blur implementation from its presence.

## 4. Tint, highlight/rim, border, shadow, reflection

### Tint

- Liquid: `Color(0xFF121212).copy(alpha = 0.40f)` in dark; `Color(0xFFFAFAFA).copy(alpha = 0.40f)` in light.
- Floating: black at 0.36 dark; white at 0.38 light.
- Docked: black at 0.58 dark; white at 0.62 light.
- Liquid full-row surface tint: the same tint color at **0.16** (`tl.b(j2, 0.16f)`).
- Floating selector: white 0.18 dark; black 0.08 light.

### Highlight / rim (this is the closest thing to a border)

The nav path has **no `BorderStroke` and no border draw**. It draws a highlight/rim via the custom `y90` highlight and `aa0` modifier:

- Static highlight (`NavigationPill_N85Pf5M$lambda$7$0`):
  - `y90.a(y90.e, 0.75f, z ? 0.78f : 0.96f, null, 8)`
  - width = **0.75 dp**
  - blur radius = **0.4 dp** (the `i = 8` mask leaves the blur at the factory's 0.4 default)
  - alpha = **0.78 dark / 0.96 light**
  - style = default `ca0.b` = `da0(45 deg, mask 15)` -> **white at 0.5 alpha**, angle 45 deg, falloff 1.0, blend mode id 12 (`Plus` in Compose's public ordering).
- Pressed/dynamic highlight (`NavigationPill_N85Pf5M$lambda$12$0$12$0`):
  - `y90.a(y90.e, 0.8f, clamp(press, 0, 1) * 0.65f + 0.25f, new da0(velocity * 40.0f + 45.0f, 11), 2)`
  - width = **0.8 dp**
  - blur radius = **0.25 dp**
  - alpha = **0.25 + 0.65 * press** (range 0.25 to 0.90)
  - style = white 0.5 shader, angle **45 deg + 40 * velocity**, falloff 1.0.
- `aa0` draws the highlight as a stroked rounded rect: `paint.strokeWidth = ceil(widthPx) * 2.0f`. The nominal stroke is **2 x width = 1.5 dp static / 1.6 dp dynamic**, but the `ceil` is applied in pixels, so the actual stroke quantises up by up to 2 px depending on density (for example 0.75 dp at 2.75x is 2.06 px -> 3 px -> 6 px = 2.18 dp). The layer alpha is `y90.c`; the shader colour is white 0.5.

If the requirement's "border alpha" means a solid border, the answer is **none**. If it means the rim, use the highlight values above.

### Shadow

There is **no drop shadow/elevation** in the ColorDuo navigation path. `vy.D` is not a shadow: it is the layer bleed derived from the maximum blur radius (`uy.i`), and for the measured lens/blur pairs it becomes 0 (blur 12 - lens height 24 or 16). Duo's `GlassSurface` shadow (18 dp, 14%) is extra and must not be attributed to ColorDuo.

### Reflection

`NavigationPill_N85Pf5M$lambda$10$0` draws a press-only specular reflection in the liquid branch:

```java
if (liquid && press > 0.0f) {
    brush = linearGradient(
        white.copy(alpha = clamp(press, 0f, 1f) * 0.16f),
        transparent
    )
    centerX = ((widthPx - 8.dp.toPx()) *
               (position - (tabs - 1) / 2.0f) * direction) / tabs
              + widthPx / 2.0f
    centerY = heightPx / 2.0f
    height = heightPx * 1.5f
}
```

- Colour: **white at 0.16 * press**, fading to transparent (`tl.g`).
- Height: **1.5x** the layer height.
- Horizontal inset: **8.0 dp** in the centre calculation.
- Only when liquid mode is active and press > 0; it is not a permanent reflection.

## 5. Motion, drag, and haptics

### Animation specs

| Property | Exact spec |
|---|---|
| Press progress (`navigationPress`) | `spring(dampingRatio = 0.7f, stiffness = 500.0f)` |
| Stretch (`navigationStretch`) | `spring(dampingRatio = 0.8f, stiffness = 350.0f)` |
| Panel shift (`navigationPanelShift`) | `spring(dampingRatio = 0.75f, stiffness = 300.0f)` |
| Drag settle | `spring(dampingRatio = 0.78f, stiffness = 420.0f, visibilityThreshold = 0.01f)` |
| Tap settle | `tween(durationMillis = 240, delayMillis = 90, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))` |
| Resting animatable | visibility threshold `0.01f` |

### Scale and shift values

`zt.V(240, 4, g10.a)` maps to `tween(durationMillis = 240, delayMillis = 90, easing = CubicBezierEasing(0.4, 0.0, 0.2, 1.0))`: mask bit 2 is not set, so `zt.V` supplies its default 90 ms delay.

- Press scale of a tab: selected `1.0 + 0.025 * press`; unselected `1.0 - 0.03 * press`.
- Stretch scale: X `1.0 + 0.16 * stretch`; Y `1.0 - 0.08 * stretch`; both multiplied by `1.0 + 0.35 * press`.
- Panel shift: `4.0 dp * clamp(travel, -1.0f, 1.0f)` (the `navigationPanelShift` offset; `travel` is the normalised drag travel).
- These are float multipliers; no dp values except the panel shift.

### Drag model (`NavigationDragState`)

- `tabWidth = availableWidth / tabCount`; position is a float tab index in `0 .. tabCount-1`.
- `move(dx, width, ...)`: `position += (dx / tabWidth) * direction`, clamped to `[0, tabCount-1]`.
- Crossing/haptic: when `round(position)` changes, `onCrossing` fires; it calls `e41.a(9)` -> `View.performHapticFeedback(9)` on API >= 30.
- `travel += dx / width` (not dx/tabWidth).
- `velocity = clamp(((dx / tabWidth) * 250.0f) / dt, -1.0f, 1.0f)`; `dt` is at least 1 ms.
- On release:
  - no velocity or non-positive width: target = `round(position)`;
  - otherwise target = `round((isRtl ? width - x : x) / width * tabCount)`;
  - clamp to `[0, tabCount - 1]`;
  - `settleTo(target, pressed && velocity == null, velocity != null)`, then `onSelect(target)`.
- There is no explicit drag slop/long-press threshold in this state machine; the pointer input starts the drag and the crossing is defined by the rounded tab index. The tab row itself uses `jv0` and is not the drag surface.
- Haptic type is numeric **9**; the platform name for 9 on older API levels is not asserted (the `e41` mapper changes it below API 30).

## 6. Tab row typography and sizing

From `m21NavigationTabRowFU0evQE`:

| Value | Exact |
|---|---|
| Icon size | **23.0 dp** (`xh1.e(es0, 23.0f)` = `Modifier.size(23.dp)`) |
| Label with icon | **12.3 sp** (`lm1.f(0x100000000L, 12.3f)`) |
| Label without icon | **14 sp** (`lm1.d(14)`) |
| Font weight | **500** (`u60.h` = `FontWeight(500)`) |
| Text vertical padding | **1.6 dp** (`gf0.b0(..., 0.0f, 1.6f, 0.0f, 0.0f, 13)`) |
| Tab row horizontal padding | **4.0 dp** (`gf0.a0(..., 4.0f, 0.0f, 2)`) |
| Docked tab item height | **64.0 dp** (`xh1.c(..., 64.0f)` in `jv0.b`) |

The 23 dp is the icon size (`Modifier.size(23.dp)`), not padding. The tab row itself is drawn after the glass layer so labels are not blurred.

## 7. Obfuscated helper map (actual constants vs helpers)

| Obfuscated call | What it actually is | Consequence |
|---|---|---|
| `f31(50.0f)` | `CornerSize(percent = 50)` | **50%**, not 50 dp |
| `la1(...)` / `ma1.a` | `RoundedCornerShape` / cached 50% shape | pill shape |
| `zt.u` (`ra0(2)`) | `RectangleShape` | selector intrinsic radius 0, clipped by pill |
| `zt.P(a, b, null, 4)` | `spring(dampingRatio = a, stiffness = b)` | motion values above |
| `zt.g(nd, px)` | RenderEffect blur modifier | **12 dp** actual blur |
| `ws.F(nd, a, b, true, z)` | custom lens shader | `refractionHeight = a`, `refractionAmount = b`, depth = true, chromatic = z |
| `vl.a(nd)` | saturation `ColorMatrixColorFilter` | matrix above; saturation 1.5 |
| `y90` / `da0` | custom highlight / highlight style | width, blur, alpha, angle, falloff |
| `tl.b(color, alpha)` | `Color.copy(alpha)` | tint/selector/reflection alpha |
| `gf0.I(long)` | `Color(long)` | `0xFF121212` / `0xFFFAFAFA` |
| `bq0.R(float)` | `roundToInt` | selector/velocity rounding |
| `i9.b` | `animateFloatAsState` | press/stretch/panel |
| `a12.a(i, 0.01f)` | `Animatable(initial, visibilityThreshold = 0.01)` | resting position |
| `g10.a` / `vt(0.4f, 0.2f)` | `CubicBezierEasing(0.4, 0.0, 0.2, 1.0)` | tap settle easing |
| `u60.h` | `FontWeight(500)` | label weight |
| `lm1.f(0x100000000L, 12.3f)` | `12.3.sp` | label with icon |
| `lm1.d(14)` | `14.sp` | label-only |
| `xh1.b` / `xh1.c` / `xh1.h` | `fillMaxWidth` / `height` / `width` | pill and selector geometry |
| `xh1.e` / `gf0.a0` / `gf0.b0` | `Modifier.size` / padding modifiers | 23 dp icon size, 4 dp row, 1.6 dp text |
| `e41.a(9)` | haptic feedback type 9 | crossing haptic |

## 8. What cannot be extracted statically

- **Rendered appearance after compositing.** The literal tint alphas, blur and lens parameters are exact, but the final pixels depend on the wallpaper, theme, OEM GPU, and API level. The screenshot is a visual reference, not a pixel-value source.
- **Runtime shader availability.** The lens requires API 33+ and blur requires API 31+ in this code. Behaviour on older API levels is a code path, not a measured render.
- **`BarBlurRadius` intent.** The field is named "blur radius" but is constant-folded into the highlight angle. There is no static evidence that 40.0f was ever intended as a blur radius; the actual blur is 12 dp.
- **`PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` consumer.** The string is defined but has no smali reference in the APK. It may be dead code or consumed by a path not present in this build; do not guess.
- **Border and shadow.** No `BorderStroke`, `drawBorder`, elevation, or shadow call exists in the ColorDuo navigation path. Any border/shadow seen in a screenshot comes from the highlight/rim or the system, not a measured nav token.
- **Haptic platform name.** Type 9 is passed to `View.performHapticFeedback` on API >= 30; the symbolic Android constant name for 9 is not asserted here.
- **Docked selected-tab styling.** `DockedNavigationBar` uses `jv0.b` for the tab items; its selected-state internals were not decompiled to a token list because it has no pill/selector glass. The bar tint and blur are measured; item-level selected colours are not.
- **Exact `SettingsTab` count at runtime** is not dynamic: the enum has three entries in 1.1.7. The width formula is written for any count so Duo's planned four tabs can use it.

## 9. How to port to Duo (mapping only, no code changes)

Existing Duo tokens live in `app/src/main/java/io/github/kvmy666/duostatusbar/ui/GlassSurface.kt` (`GlassTokens`), `DuoShell.kt` (`LensNavigationBar`), and `DuoDesign.kt` (`DuoRadius`).

| Duo token / value | ColorDuo 1.1.7 value | Mapping note |
|---|---|---|
| `GlassTokens.SATURATION = 1.5f` | saturation 1.5 (matrix above) | **Already exact**; no change. |
| `GlassTokens.NavigationBlur = 36.dp` | **12.0 dp** | ColorDuo's nav glass is much thinner. Exact match means a 12 dp nav blur; keeping 36 dp is a deliberate Duo look, not a ColorDuo match. |
| `GlassTokens.LensHeight = 14.dp`, `LensAmount = 26.dp` | liquid **24 / 24 dp**; floating **16 / 32 dp** | Add separate navigation lens tokens; do not reuse the card values. Duo's selector already uses 16/32, which matches ColorDuo floating. |
| `GlassTokens.NavigationTint = DuoColors.Background.copy(alpha = 0.72f)` | **`#121212` @ 0.40 dark / `#FAFAFA` @ 0.40 light** | Use the measured colours/alphas for an exact match; 0.72 is Duo-specific. |
| `GlassSurface(shape = RoundedCornerShape(50))` | **50%** | Already exact. |
| `LensNavigationBar.pillHeight = 66.dp` | **64.0 dp** | Set 64 dp for liquid. |
| `restHeight = 57.dp` | **56.0 dp** (pill - 8) | Set 56 dp. |
| `restWidth = 71.dp` / `heldWidth = 94.dp` | **`(availableWidth - 8.dp) / tabs`** | ColorDuo does not expand the selector to 94 dp; width is tab-slot based. For Duo's 4 tabs, use `(maxWidth - 8.dp) / 4`. |
| `GlassTokens.NavigationBorder` gradient | **no border** | Exact ColorDuo match sets `borderBrush = null`; the rim is the highlight. Keeping the gradient is a Duo identity choice. |
| `GlassSurface` 18 dp / 14% shadow | **no shadow** | Exact match removes the shadow; Duo's shadow is extra. |
| `spring(0.76f, 420f)` settle | **0.78 / 420 / 0.01** | Close; add visibility threshold 0.01. |
| `spring(0.66f, 420f)` tap | **240 ms tween + 90 ms delay, `CubicBezier(0.4, 0, 0.2, 1)`** | ColorDuo uses a delayed tween, not a spring, for tap settle. |
| press / stretch / panel springs | **0.7/500, 0.8/350, 0.75/300** | Add if matching ColorDuo's press/stretch/panel feel. |
| no crossing haptic in Duo | **type 9 on rounded-index crossing** | Add `View.performHapticFeedback(9)` on crossing. |
| Duo selector separate lens (4 dp blur, 16/32) | ColorDuo liquid uses **12 dp glass + 16% tint + rim**; floating uses **0.18/0.08 selector** | Choose one: keep Duo's selector lens (closer to floating) or add ColorDuo's tint + highlight for liquid. |
| Duo icon 18 dp, `labelSmall` | **23 dp icon size, 12.3 sp with icon / 14 sp label-only, FontWeight 500** | Adjust icon/label tokens; text vertical padding 1.6 dp, row horizontal 4 dp. |
| no reflection | **white 0.16 * press gradient, 1.5x height, 8 dp inset** | Add only in liquid mode. |

Do **not** port `BarBlurRadius = 40.0f` as a blur value. Do **not** port `PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` as a live shader unless a consumer is found.

## 10. Evidence index

Primary smali (apktool output):

- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/SettingsNavigationKt.smali:15` - `BarBlurRadius` field = 40.0f.
- `...SettingsNavigationKt.smali:17` - `PROGRESSIVE_BLUR_ALPHA_MASK_SHADER` exact string.
- `...SettingsNavigationKt.smali:2780-2908` - `LiquidNavigationBar`: 50%, `0xFF121212`/`0xFFFAFAFA` @ 0.4, `0.47805998`, 64/24/24, true/true.
- `...SettingsNavigationKt.smali:2105-2245` - `FloatingNavigationBar`: 0.36/0.38, `0.62`, 56/16/32, false/false.
- `...SettingsNavigationKt.smali:350-450` - `DockedNavigationBar`: 0.58/0.62, full width.
- `...SettingsNavigationKt.smali:3857` - `0.07f` width-fraction increment.
- `...SettingsNavigationKt.smali:6292-6310` - selector `(jh.b() - 8)/tabs`, `height - 8`, white 0.18 / black 0.08.
- `...SettingsNavigationKt.smali:6514-6560` - selector width/height and alpha.
- `...SettingsNavigationKt.smali:7193-7301` - liquid full-width tint at 0.16.
- `...SettingsNavigationKt.smali:7608-7706` - dynamic highlight 0.8/0.25/0.65/0.25/40/45.
- `...SettingsNavigationKt.smali:8666-8705` - static highlight 0.75/0.78/0.96.
- `...SettingsNavigationKt.smali:8604-8625` - vibrancy call, 12 dp blur, lens `ws.F`.
- `...SettingsNavigationKt.smali:8072-8100` - selector offset formula.
- `...SettingsNavigationKt.smali:10150`, `10317`, `10339`, `10393`, `10402` - 23 dp icon, 12.3 sp, 14 sp, 1.6 dp, 4 dp.
- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/NavigationDragKt.smali:627-635` - haptic `0x9`.
- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/NavigationDragState$settleTo$1.smali:384-423` - 240 ms and spring 0.78/420/0.01.
- `/tmp/colorduo-apk/smali/io/github/fengwenhua/colorduo/ui/NavigationDragState.smali:152` - resting threshold 0.01.

JADX source:

- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/SettingsNavigationKt.java:13,15` - fields.
- `...SettingsNavigationKt.java:281-310` - liquid literals.
- `...SettingsNavigationKt.java:1040-1120` - selector width/height/alpha and offset.
- `...SettingsNavigationKt.java:1120-1200` - liquid glass wiring.
- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/NavigationDragState.java:166,250-330` - resting/finish/move.
- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/NavigationDragKt.java:183` - haptic call.
- `/tmp/colorduo-jadx/sources/defpackage/vl.java:8-9` - vibrancy matrix.
- `/tmp/colorduo-jadx/sources/defpackage/y90.java:8-11,30-40` - highlight defaults/factory.
- `/tmp/colorduo-jadx/sources/defpackage/da0.java:9-15` - white 0.5 shader, angle/falloff.
- `/tmp/colorduo-jadx/sources/defpackage/aa0.java:29-60` - stroke width/alpha/blur.
- `/tmp/colorduo-jadx/sources/defpackage/ws.java:44-100` - lens uniforms and dispersion shader.
- `/tmp/colorduo-jadx/sources/defpackage/zt.java:1665-1678` - blur modifier.
- `/tmp/colorduo-jadx/sources/defpackage/zt.java:615-624` - `tween` factory; `V(240, 4, g10.a)` yields 240 ms + 90 ms delay.
- `/tmp/colorduo-jadx/sources/defpackage/xh1.java:47-49` - `xh1.e` = `Modifier.size` (23 dp icon).
- `/tmp/colorduo-jadx/sources/defpackage/f31.java` - percent corner size.
- `/tmp/colorduo-jadx/sources/defpackage/ma1.java` - cached 50% shape.
- `/tmp/colorduo-jadx/sources/defpackage/vt.java` - CubicBezierEasing(0.4, 0.0, 0.2, 1.0).
- `/tmp/colorduo-jadx/sources/defpackage/u60.java` - FontWeight(500).
- `/tmp/colorduo-jadx/sources/defpackage/lm1.java:33-80` - sp text units.
- `/tmp/colorduo-jadx/sources/defpackage/jv0.java:256` - docked tab item 64 dp.
- `/tmp/colorduo-jadx/sources/io/github/fengwenhua/colorduo/ui/SettingsTab.java` - three tabs.

REA evidence IDs retained in this session (JADX headless, APK digest above):

- `ev_4e3e430d6aac2603dfc076e6f06a00bc2dcfcd2b33a65fe5462d3770b60ae535` - class inventory (fields/methods).
- `ev_92741a71cbd17bfb77836743b11123f06d38fa567f583cd97e02f915e6c6c418` - `LiquidNavigationBar` body.
- `ev_284457fd8222450bf509521f44521701410619822bcbb75ebc87042109062378` - `FloatingNavigationBar` body.
- `ev_e9a38419016815e42fa2ccb470444af81d522b0554ca00c5ff686e36273ea571` - `DockedNavigationBar` body.
- `ev_918f2b4634acebd43c64de3d7b9ee61ab8acfa3d30ebf5a8f856bcac7925632b` - `lambda$6` (vibrancy/blur/lens).
- `ev_bb0d62bb13195f9af49386b48c12e4efc12f739f093e19e3e12911d1f28ae853` - `lambda$7` (static highlight).
- `ev_9fb5e4db5672818f85b5a7fe430992eaf925261ef1777dd12d850a9e78580626` - `lambda$12$0$12` (dynamic highlight).
- `ev_805bf0e3f48906f308fe932ea273d821615ec555f5968cbc2efa840f27c1af81` - `lambda$10` (reflection).
- `ev_9efe3497af1943f518b841412e6758cc1113fe2adc000b874e935e7ee100e253` - stretch X.
- `ev_ea259cbced22441dd1e700bd1667a34e1879640cb38895ec613125049e8ac091` - stretch Y.
- `ev_3497dcce4afff51318ca25db0a9732dac23912f6c02ba54bde1131ec473d22ed` - press scale.
- `ev_d6d0c08408aa196edaa27887dafec45a9c452bb09dead54e1c65080e5a20287f` - selector offset.
- `ev_6b7b8df852747bcedee3d5cf7cba9c1ddfe5a5a6c9214a462c48f2a2ada8f714` - tab row lambda.

Screenshot: `/tmp/colorduo-screen/screen.png` - 1272 x 2772, three-tab liquid layout; used only as a visual reference.


## FILE: AGILE-1.7-PLAN.md

# Duo Status Bar — Agile 1.7 Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.7 |
| Date | 2026-11-03 |
| Requirements | `AGILE-1.7-REQUIREMENTS.md` |
| Tests | `AGILE-1.7-TEST-PLAN.md` |
| Baseline | 1.6.0 code 76 installed; fatal fixed |
| Status | **Plan only. No 1.7 code until the five open questions are answered and the requirements/test/plan traceability is approved.** |

## 1. What changed in the owner brief

- Fatal fix worked; behaviour improved but not battery-like.
- Heads-up/QS/shade transitions still snap; Duo disappears.
- New 8-dot ring is the right direction, but its battery bar is static and PHDP still uses the old design.
- Old ring design must return; user chooses old 4-dot / new 8-dot / camera / custom.
- Battery-like host/lifecycle is the proposed architecture.
- Home becomes the state/switcher/simulator; size and position bugs.
- Size mapping changes (new 180% ≈ old 130%, old 50% = new 0%, physical minimum >0).
- Position disappears after ~20 dp; requires reset.
- Per-icon status-bar hiding + All toggle; remove hide-all.
- Use supplied icons; rename Customization → Custom; Settings returns to pill.
- ColorDuo glass/touch mimicry, exact values via REA.
- Wi-Fi mapping and 4/8-dot cell accuracy from logs.
- Report blur; live preview charge/saver/8-dot/inversion; event GIFs.

## 2. Evidence baseline

- ColorDuo: `io.github.fengwenhua.colorduo` v1.1.7 (10107), Compose + libxposed, target 36; package pulled at `/tmp/colorduo-pull/…/base.apk`; screen at `/tmp/colorduo-screen/screen.png`.
- `NavigationDragState`: velocity, settle, travel, pressed, finger, crossing; `NavigationDragKt`; use same Kyant Backdrop API.
- Current Duo: 1.6.0 code 76, 8-dot static ring, battery line, liquid selector, 3 tabs; `docs/evidence/final-*.png`.
- 1.6 fatal evidence remains `reverse/fatal-logs/`.
- Current cell mapping: `SignalMapping.sphereOpacities` and `cellOpacities`; owner reports full→1/4/3 inconsistency.

## 2.1 A0 audit result — status icons / cell mapping

Read-only audit complete. Key facts:
- `hideOtherIcons` is a single boolean; it either hides all strip children (`hideAllExcept`) or only battery/Wi-Fi/mobile (`hideReplaced`).
- QS path `hideSlotStock` hides the whole `statusIcons` container regardless of the boolean; must be split before per-icon controls.
- `isReplaced` uses slot → id → class; class fallback can be broad. A dynamic keyed inventory is required.
- Battery remains `INVISIBLE` as the anchor; never GONE. Clock is outside `system_icons` on OOS (`StatClock`).
- Known OOS slots/ids: battery, wifi_combo, mobile_combo ×2, mobile_signal/mobile_volte/wifi_signal inner parts, status_bar_dot. One UI `CombinedStatusView` is a composite.
- Cell mapping uses `signalStrength.level` and `maxSignalLevels` with `ceil`; diagnostics mislabel mapped values as raw. Add raw/max/dots/source logs and a stock-level comparison before changing math.
- Wi-Fi uses RSSI→5 levels→3 display bars; no VoWiFi reader exists yet.
- Dynamic list keys: slot → id → class + occurrence; labels localized; unknown remains visible; hide/restore must call `restore()` then reapply.

Implementation split: A4a classifier/storage/hiding; A4b dynamic UI + All; A4c cell/Wi-Fi diagnostics and mapping correction.

## 3. Phases

### A0 — Requirements answers + measurements (blocking)

**Requirements:** BAT-01/05, RING2-05, GLASS2-04, HOME2-02, ICON2-03.  
**Weapons:** REA/JADX (`inspect_android_class`, `decompile`), `adb logcat`, `dumpsys`, `screencap`, `screenrecord`, ColorDuo APK, image measurement (ImageMagick).  
**Exit:** five answers; cell-level logs for 0–4; ColorDuo alpha/blur/rim/reflection values; battery view hierarchy; size formula.

### A1 — Battery-like lifecycle and transitions

**Requirements:** BAT-01…05, QA2-02.  
**Weapons:** REA, `adb logcat`, `dumpsys window`, 60 fps screenrecord, `DuoIconHost`/`DuoHook`, 1.6 P0 guards, `T-BAT-*`.  
**Tasks:**
1. Read the stock battery host and transition logs; document parent/visibility changes.
2. Choose safe host/override strategy; keep one cached view and no reparent storm.
3. Implement animation/visibility parity; remove early hide.
4. Test six directions ×20; A/B overlay vs host.
5. Keep P0 crash gates.

### A2 — Ring designs, battery, PHDP, cells

**Requirements:** RING2-01…08.  
**Weapons:** Rive CLI, `scene.rml`, `DuoMapping`, `SignalMapping`, Canvas, PHDP, logs, headless screenshots.  
**Tasks:**
1. Restore old 4-dot art; add new 8-dot art; selectable via Home state.
2. Bind battery line to level; PHDP uses selected design.
3. Fix circular battery line.
4. Re-map cell levels from logs; per-SIM 4/8 dots; tests.
5. Camera state only on main bar.
6. Canvas parity/hash/ease.

### A3 — Home states, size, position, live simulation

**Requirements:** HOME2-01…06, UX2-02.  
**Weapons:** Compose, `DuoPresetStore`, `DuoPrefs`, `DuoMapping`, `DuoRiveStill`, screenshot/measurement.  
**Tasks:**
1. Four Home states: Default(old), 2SIM(new), Camera, Custom; persist; preview.
2. Re-base size formula; unit table; actual minimum >0.
3. Fix position drag range/pointer capture; no reset.
4. Add preview controls: charging, saver, 8-dot custom, centre inversion.
5. Event previews; no continuous idle loop.

### A4 — Navigation, icons, status-bar per-icon controls

**Requirements:** NAV2-01/02, ICON2-01…04.  
**Weapons:** Compose, `StockIconHider`, status-bar slot inventory, `Settings.Secure`/`Global`, adb, vendor APKs via REA/JADX.  
**Tasks:**
1. Rename Custom; add Settings to pill.
2. Use supplied icon assets.
3. Enumerate stock icon slots; per-icon hide; All toggle next to header.
4. Fix Wi-Fi outside ring; ensure Duo art not hidden.
5. CPH2747 test; other UIs unverified.

### A5 — ColorDuo glass and touch

**Requirements:** GLASS2-01…04.  
**Weapons:** REA/JADX on ColorDuo, runtime screenshot sampling, Kyant Backdrop docs, Compose, screenrecord.  
**Tasks:**
1. Extract exact alpha/blur/rim/reflection/shadow values.
2. Match selector effects and dimensions.
3. Match press/drag/velocity/settle/haptics.
4. Side-by-side proof; no label blur.

### A6 — Report, event GIFs, polish

**Requirements:** UX2-01…03.  
**Weapons:** Compose, ImageMagick/Rive GIF, screenshot.  
**Tasks:**
1. Move report title below blur; reduce overlap.
2. Add four event previews.
3. Verify no duplicate settings.

### A7 — Test/release

**Requirements:** QA2-01…04.  
**Weapons:** full unit/Compose/Rive/device suite, 1.6 P0 suite, screenrecord, emulator, phone install, `review-strict`.  
**Exit:** all P0/P1 tests pass, no fatal, owner approves, final APK installed on CPH2747, rollback retained.

## 4. Dependencies / waves

- A0 blocks all.
- A1 and A2 overlap `DuoIconHost`/Rive; serial by file.
- A3 can start after A0/A2 design IDs; uses Home files.
- A4 is separate UI/hook files but needs A3 navigation.
- A5 is independent after A0; can parallel with A4.
- A6 waits for A3/A5.
- A7 last.

## 5. Risks

| Risk | Mitigation |
|---|---|
| Battery host override destabilizes SystemUI | A0 evidence; A/B; one cached view; P0 guards; rollback |
| Transition fix changes stock battery | Only wrap/observe; never mutate stock layout unless measured; screenshots |
| Old+new Rive art grows .riv | Shared geometry; verify size/ease/hash |
| Size formula wrong | Owner answer; unit table; measured screenshots |
| Per-icon slots differ by vendor | Slot inventory; fail-silent; unverified others |
| ColorDuo copying/licensing | Reimplement values with own code; no copied assets |
| Cell mapping wrong | Log-driven table + per-phone tests |
| Position pointer loss | Compose pointer capture; device test |
| Nav 4th item crowds | Responsive labels; owner decision |

## 6. Decisions locked 2026-11-03

1. Size: logical 0…100, `physical = 50 + 1.7 × logical`; 0=50, 100=220; old/new icons optically normalized.
2. Battery hide: hiding the stock battery hides only the stock icon; Duo's battery and invisible anchor stay.
3. Per-icon list is dynamic from actual SystemUI slots.
4. Custom starts blank; ready presets are editable templates. Whole centre icon moves; Wi-Fi can be detached and pinned; charge number can move; size/rotation editable.
5. Camera is a fixed toggle: ring around cutout, Wi-Fi outside visible, size/rotation editable; QS/shade/keyguard normal.
6. Settings is the fourth pill tab.

No unresolved owner question remains. A0 evidence still measures ColorDuo values, transitions, and cell mapping.

## 7. Approval

No 1.7 code starts before answers + requirements/test/plan review. The 1.6 installed build remains the rollback baseline.


## FILE: AGILE-1.7-REQUIREMENTS.md

# Duo Status Bar — Agile 1.7 Requirements

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Agile 1.7 (after 1.6.0 installed on CPH2747) |
| Date | 2026-11-03 |
| Owner | Abdulkarim |
| Status | **Draft for owner answers before implementation.** |
| Evidence | Owner notes 2026-11-03; 1.6 code + screenshots; `reverse/fatal-logs/`; ColorDuo 1.1.7 APK `/tmp/colorduo-pull/.../base.apk`; CPH2747 screenshot `/tmp/colorduo-screen/screen.png` |
| Baseline | 1.6.0 code 76, fatal paths fixed, 8-dot ring/battery line, liquid selector, 3 tabs |

This phase is additive. It does not reopen the 1.6 fatal fix. Any requirement that conflicts with 1.6 safety follows `AGENTS.md` and `REQUIREMENTS-1.6.md`.

---

## 1. Owner intent

Make Duo behave like the stock battery, not a separate overlay that disappears or jumps between heads-up, Quick Settings, and the shade. The visual design stays, but the user can choose the old single-SIM four-dot ring or the new dual-SIM eight-dot ring, and the new ring must be live, not a static 100% drawing.

The Home page becomes the design/customization switcher and live simulator. Settings returns to the bottom pill (short label "Custom"), the supplied icons are used, and the status-bar icons gain per-icon hide controls plus a master "all" toggle. The glass selector must match ColorDuo (same Kyant Backdrop library) in transparency, blur, edge refraction, and touch behaviour.

---

## 1.1 Clarifications received 2026-11-03

### Design states (authoritative)

| State | Definition |
|---|---|
| **Default** | Old design: battery is almost a full ring; the charge number cuts the ring; Wi-Fi sits inside; four cellular dots at the bottom (single-SIM style). |
| **2 SIM** | New design: eight dots (four per side), centre icon, battery line at the bottom, charge number above. |
| **Camera** | Existing toggle that moves the currently selected design onto the camera cutout and binds it to the camera-ring size; not an independent art style. |
| **Custom** | Constrained editor: switch old/new (one/two SIM), swap Wi-Fi/charge number, resize and move the centre icon up/down/left/right. Not arbitrary geometry. |

### Clarified behaviour

- Both Default and 2 SIM use **one shared size scale 0…100**. 0 maps to today's 50%; 100 maps to a new physical maximum of **220** (current 200 + 10%). The element stays visible at 0. Switching designs must not change size.
- Icon artwork is normalized between designs: a Wi-Fi icon drawn at 10 in the old art must render at the same optical size as the new art (currently ~8). One shared resize control for both.
- The overlay is rated 90% good; the remaining defect is the harsh HUD/QS/shade transition, not the overlay concept. Study inheritance/stock-battery host as an option, but keep the overlay if it is safer.
- Camera is the existing toggle that places the current design on the camera cutout; it is not a new art style. QS/shade/keyguard stay normal.
- Settings is a **fourth tab** inside the pill.
- ColorDuo must be matched in full: same library, same transparency/blur/refraction/selector size/touch values.
- Per-icon status-bar hiding includes the clock and battery. Hiding the stock battery must **not** hide Duo's own battery; the invisible anchor must survive. The “light icon” clarification was a typo: it meant the Duo battery itself.
- The per-icon list is built **dynamically** from the status-bar slots that actually exist on the device.
- Custom is a **blank, fully editable layout**: move the whole centre icon, detach Wi-Fi and pin it separately, move the charge number, resize, and rotate left/right. It starts without preset edits. The ready presets are editable templates; editing them behaves like editing a template.
- Camera is a fixed option/toggle: it puts the ring around the cutout, keeps Wi-Fi outside and visible, and allows size/rotation edits in that mode. Custom cannot move the physical camera; the Camera option owns the cutout placement.

---

## 2. Requirements

### 2.1 Battery behaviour and surface transitions (BAT)

| ID | Requirement | Acceptance |
|---|---|---|
| BAT-01 | Study how the stock battery view is hosted/lifecycled on CPH2747 and, where safe, make Duo inherit/attach to the same battery host instead of a separate overlay. If inheritance/override is unsafe, document why and reproduce the stock lifecycle with one cached view. | Evidence shows the chosen host, no new fatal, and the ring follows the battery's visibility. |
| BAT-02 | Heads-up ↔ Quick Settings ↔ shade transitions must be smooth: no snap, no size jump, no flash, no empty frame. Cover all six directions. | Screen recording at 60 fps; no jump >2 px, no blank frame, no size change beyond the measured animation. |
| BAT-03 | Duo must not disappear during a surface transition. If the stock battery is transitioning, Duo follows the same interpolation rather than hiding early. | 20 transitions each direction; Duo present on every frame where the stock battery is present. |
| BAT-04 | The battery-like implementation must remain fail-silent and keep the 1.6 crash guards (no id-less child, one Rive surface/import, no reparent storm). | P0 tests still pass; no `ConstraintSet.clone`/JNI/SIGABRT. |
| BAT-05 | Read and classify the current transition logs before changing the lifecycle; record the exact views, timestamps, and parent changes. | A short transition report with log excerpts and the measured jump source. |

### 2.2 Ring designs, battery, PHDP, and cells (RING2)

| ID | Requirement | Acceptance |
|---|---|---|
| RING2-01 | Restore the old four-dot single-SIM design and keep the new eight-dot dual-SIM design. Home lets the user choose **Default (old)** / **2 SIM (new)** / **Custom**, with a separate **Camera** toggle that applies to the current design. | Both designs render; switching is persistent; no deleted art. |
| RING2-02 | The new design's bottom battery bar is live: it must decrease/increase with the real battery level, not stay at 100. | 100/80/49/19 renders; bar length/trim follows level on device. |
| RING2-03 | PHDP download progress uses the selected ring design (old design → old progress style; 2SIM → new battery-line style). No stale old progress geometry. | PHDP/test download renders in both designs; no mixed geometry. |
| RING2-04 | In the new 8-dot design, the battery bar and dots form a clean circular ring; no flattened/straight bar or gaps that break the silhouette. | Headless/reference compare; ring curvature continuous; screenshots. |
| RING2-05 | Cellular dot mapping must match the stock icon on each device: 4 dots (single SIM) / 8 dots (dual SIM), with full/3/1-dot cases accurate. No “full signal → one dot”. | Read cell logs and level changes; matrix of stock level → dots; CPH2747 + at least one other ROM or static evidence. |
| RING2-06 | Camera is a toggle that moves the **currently selected design** into the camera cutout and binds it to the camera-ring size; QS/shade/keyguard stay normal. | Camera only on main bar; current design transferred; transitions tested. |
| RING2-07 | Custom is unrestricted: move any element, change Wi-Fi/charge-number placement, mix old/new choices, and set the eight dots individually. Home can simulate 4 full, 4 right + 2 left, or fully custom per dot. | Custom editor exposes per-element position/visibility; preview updates; no module write required for simulation. |
| RING2-08 | Battery-bar color follows the existing level/charging/saver mapping; 100% while plugged returns to normal (1.6 behavior preserved). | Level/charging/saver cases pass. |

### 2.3 Home, size, position, and preview (HOME2)

| ID | Requirement | Acceptance |
|---|---|---|
| HOME2-01 | Home is the design/customization surface: Default/2 SIM/Custom plus a Camera toggle, static/GIF preview, size/position controls, and live preview that updates on every change. Ready presets are editable templates; Custom starts blank. | All states/toggle; template edits persist; Custom blank; preview updates. |
| HOME2-02 | Both designs share one logical **0…100** scale: `physical = 50 + 1.7 × logical`; 0 → 50, 100 → 220. The element remains visible at 0. Icon art is normalized across designs so the same logical size looks identical. | Unit table + screenshots at 0/50/100; old/new optical comparison. |
| HOME2-03 | Position drag works through its full range; the element must not disappear after ~20 dp or require Reset to grab it again. The drag cursor must remain attached to the finger. | Drag from 0 to max and back, 10×; visible selection, no lost pointer, no reset. |
| HOME2-04 | Live preview supports: charging level slider, low-power mode, 8-dot simulation, and moving the centre icons (Wi-Fi/5G/4G/flashlight) to the top seat with a smaller scale while the number takes the centre. | Each control changes the preview; inverted layout matches the real mapping. |
| HOME2-05 | Live-event rows have animated previews (GIF/Rive) for Alarm, Flashlight, Screenshot, Unlock. | Four previews loop/play; no separate Simulation section. |
| HOME2-06 | Home shows the supplied design previews as still/GIF references; Default and 2SIM are visibly different. | Screenshots match assets/ring. |

### 2.4 Navigation, icons, and settings (NAV2/ICON2)

| ID | Requirement | Acceptance |
|---|---|---|
| NAV2-01 | Rename "Customization" to **"Custom"** (short label) in the pill and page title. | Visible label; no clipped text. |
| NAV2-02 | Put **Settings** as the **fourth tab** inside the pill. The page remains the health/backup/update/about surface, not duplicated customization. | Four tabs; one-tap Settings; no duplicate controls; back works. |
| ICON2-01 | Use the owner-supplied Home/sliders/report icons; do not redraw replacements. | APK contains the supplied assets; screenshots match. |
| ICON2-02 | Replace “hide all other icons” with per-icon controls. Add an **All** switch next to the “Status bar icons” header that enables/disables every per-icon switch. | Each switch hides only its icon; All toggles all; state persists. |
| ICON2-03 | Individual controls cover all discovered status-bar icons, including clock and battery, plus sound/general, silent, vibrate, Wi-Fi speed, VoWiFi, Wi-Fi/cellular replaced icons. Battery hiding must keep the invisible anchor and must not hide the “light” icon (owner term to confirm). | Each tested on CPH2747; missing vendors marked unverified; battery anchor survives. |
| ICON2-04 | Moving Wi-Fi outside the ring must keep the Wi-Fi glyph visible. Hidden-icon handling must not hide Duo's own Wi-Fi art. | Wi-Fi outside + hidden stock Wi-Fi; Duo glyph remains. |

### 2.5 ColorDuo glass and touch (GLASS2)

| ID | Requirement | Acceptance |
|---|---|---|
| GLASS2-01 | Match ColorDuo 1.1.7's navigation glass: transparency, blur radius, edge refraction, highlight/rim, shadow, and selector reflection. Use the same Kyant Backdrop API and measured values, not guesses. | Side-by-side screenshots; measured alpha/blur/rim; pixel diff justification. |
| GLASS2-02 | Match ColorDuo's selector touch model: press feedback, drag start, finger tracking, velocity/settle, crossing, and haptics. | Side-by-side recording; no label blur; drag from anywhere. |
| GLASS2-03 | Keep Duo's active-icon blue and the null wine fill. | Screenshot. |
| GLASS2-04 | Reverse ColorDuo with REA: package `io.github.fengwenhua.colorduo`, v1.1.7 (10107), Compose + libxposed, `NavigationDragState` exposes velocity/settling/travel. Record exact source/asset values in the phase notes. | Evidence file with class/method/strings and screenshots. |

### 2.6 Report, live preview, and event GIFs (UX2)

| ID | Requirement | Acceptance |
|---|---|---|
| UX2-01 | Report page top: reduce the blur overlap and move “Report a problem” down so it is not clipped/behind the glass. | Screenshot at 1272×2772. |
| UX2-02 | The Home live preview is interactive: charging slider, saver toggle, 8-dot simulation, centre inversion. | Each updates within 100 ms; no continuous loop when idle. |
| UX2-03 | Live-event GIFs/previews replace the old static rows and Simulation section. | Four event previews; no Play/level simulator. |

### 2.7 Quality and all-UI (QA2)

| ID | Requirement | Acceptance |
|---|---|---|
| QA2-01 | CPH2747 is the measured device; every other ROM/AOSP stays `unverified` until evidence exists. | Matrix. |
| QA2-02 | No fatal/safe mode in transition, camera, rotation, or per-icon toggles. | 1.6 P0 tests + new transition matrix. |
| QA2-03 | Test the requirements themselves before code: a requirement-to-test traceability table is approved. | This file ↔ test plan ↔ plan. |
| QA2-04 | No PII in logs; no stock/raster asset without owner permission; supplied assets are allowed only for this app. | Evidence. |

---

## 3. Open questions (blocking)

1. **Size 100% anchor:** with 0% = old 50%, what is 100%? Options: 100% = old 130% (normal, max 130), 100% = old 200% (full old range), or 100% = old 180%. Should sizes above 130 old be reachable?
2. **Settings placement:** fourth item in the pill labelled Settings, or a gear inside the existing three-item pill? The owner said “inside the pill”; the exact form is still open.
3. **Status-bar icon list:** confirm the definitive list. Is it all of: general/sound, silent, vibrate, Wi-Fi speed, VoWiFi, clock, battery, alarm, Bluetooth, DND, location, microphone, hotspot, rotation lock? Hiding the battery is special because Duo anchors to it.
4. **Custom editor scope:** “unrestricted” can mean (a) per-element position/visibility/scale with snap, or (b) a freeform canvas with arbitrary drag. Which one? Does Custom start from a copy of Default/2SIM, or from the last custom state?
5. **Camera ring design source:** when Camera is selected, does it always transfer the **current** Home design (Default/2SIM/Custom), or a fixed camera-specific layout? (The brief says current design; confirm.)

**Answered:** battery overlay is 90% good; study inheritance but prioritize the transition fix. Home states are Default/2SIM/Camera/Custom. Camera is current design in the camera ring. ColorDuo values must be matched exactly.

---

## 4. Out of scope for this phase

- Reopening the 1.6 fatal fix (must stay green).
- New onboarding, cloud accounts, or relay changes.
- Other vendors' live testing without a device; they remain `unverified`.


## FILE: AGILE-1.7-TEST-PLAN.md

# Duo Status Bar — Agile 1.7 Test Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.7 |
| Date | 2026-11-03 |
| Status | Draft; mirrors `AGILE-1.7-REQUIREMENTS.md` |
| Baseline | 1.6.0 code 76 on CPH2747; fatal paths fixed |
| Pass rule | PASS only with exact evidence; otherwise `unverified`/FAIL |

## 1. Gates

| Gate | Evidence |
|---|---|
| Requirements audit | Every requirement has a test here and a phase in `AGILE-1.7-PLAN.md` |
| Compile/unit | `:app:testDebugUnitTest :app:assembleDebug --offline` |
| Release | `:app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline` |
| Rive | `rive . --verify`, `inspect --summary`, ease/hash gates |
| Transition | 6 directions × 20; 60 fps recording; logcat |
| Fatal | 1.6 P0 suite remains green |
| All-UI | CPH2747 live; others `unverified` |

## 2. Cases

### BAT

| ID | Steps | Pass |
|---|---|---|
| T-BAT-01 | Read stock battery view hierarchy/lifecycle on CPH2747; pull logs during heads-up/QS/shade; record parent/visibility/geometry | Host and transition source documented |
| T-BAT-02 | Record 20 transitions in each direction (HUD↔QS, QS↔shade, shade→HUD) at 60 fps; inspect every frame for jump/blank/size flash | No jump >2 px, no blank, no size flash |
| T-BAT-03 | Toggle camera ring off, repeat transitions; check Duo remains with stock battery | Present on all frames where stock is present |
| T-BAT-04 | Run 1.6 P0 tests: QS rotation, shade stress, camera/lock, 100% charging | STB-01…08 pass, no fatal |

### RING2

| ID | Steps | Pass |
|---|---|---|
| T-RING2-01 | Home: switch Default/2SIM/Camera/Custom; kill/relaunch | All render, persist, no deleted art |
| T-RING2-02 | Set battery to 100/80/49/19 (or headless visuals) in new design | Battery bar follows level, not static 100 |
| T-RING2-03 | Trigger PHDP test/real download in old and 2SIM designs | Progress uses selected geometry |
| T-RING2-04 | Headless render new ring; inspect curvature | Continuous circular battery line, no straight segment |
| T-RING2-05 | Read cell logs at 0…4 bars; compare stock icon to dots; repeat on second SIM and second ROM if available | Exact mapping; no full→1 dot |
| T-RING2-06 | Camera toggle with current Default/2SIM/Custom; open QS/shade/keyguard | Current design scaled into camera cutout; Wi-Fi outside visible; normal on other surfaces |
| T-RING2-07 | Home simulation 4 full / 4+2 / custom; check preview | Preview matches, no module write |
| T-RING2-08 | 100% plugged, 99%, unplugged, saver, charging | Color/state mapping correct |

### HOME2 / size / position

| ID | Steps | Pass |
|---|---|---|
| T-HOME2-01 | Home four states; change each; live preview | Updates within 100 ms, persists |
| T-HOME2-02 | Set size 0/100; measure rendered ring; compare to physical 50/220; check old/new optical equality at the same logical size | Mapping matches `50+1.7×logical`; no zero pixels |
| T-HOME2-03 | Drag position 0→max→0 ten times; release mid-range; re-grab | No disappearance after 20 dp, no reset, cursor follows |
| T-HOME2-04 | Move centre icons to top; number to centre; shrink icons; live preview | Positions/scale correct |
| T-HOME2-05 | Preview charge slider, saver, 8-dot custom | All update, no continuous idle loop |
| T-HOME2-06 | Four event previews | Animated, no static placeholder |

### NAV2 / icons

| ID | Steps | Pass |
|---|---|---|
| T-NAV2-01 | Check "Custom" label and fourth Settings tab; screenshots | Short label, four tabs, Settings one tap, no duplicate customization |
| T-ICON2-01 | Inspect APK assets and screenshots against supplied icons | Supplied assets used, no redraw |
| T-ICON2-02 | Toggle each dynamic per-slot switch; All on/off | Only target icon changes; All toggles all; persists |
| T-ICON2-03 | Move Wi-Fi out; hide stock Wi-Fi | Duo Wi-Fi remains visible |
| T-ICON2-04 | Hide battery; check Duo battery and anchor | Duo battery remains, anchor survives, no black slot |
| T-ICON2-05 | Missing ROMs | Marked unverified |

### GLASS2

| ID | Steps | Pass |
|---|---|---|
| T-GLASS2-01 | Screenshot ColorDuo and Duo nav; sample alpha/blur/rim/reflection; compare | Values documented, close visual match |
| T-GLASS2-02 | Record press/drag/settle in both; compare | Same start/follow/settle/haptics, no label blur |
| T-GLASS2-03 | Inspect active blue/no wine | Pass |
| T-GLASS2-04 | Document ColorDuo REA evidence: package/version/classes | Evidence file complete |

### UX2 / QA2

| ID | Steps | Pass |
|---|---|---|
| T-UX2-01 | Report page screenshot; check title below blur | No overlap/clipping |
| T-UX2-02 | Live preview interactions | See HOME2 |
| T-UX2-03 | Event previews | Four animated |
| T-QA2-01 | All-UI matrix | CPH2747 measured; others unverified |
| T-QA2-02 | Fatal/transition matrix | No fatal, no safe mode |
| T-QA2-03 | Requirements audit | 1:1 traceability |
| T-QA2-04 | Log/report privacy | No PII; assets licensed by owner |

## 3. Evidence layout

- `docs/evidence/agile-1.7/transitions/` — videos/frames.
- `docs/evidence/agile-1.7/ring/` — headless/device.
- `docs/evidence/agile-1.7/home/` — four states.
- `docs/evidence/agile-1.7/icons/` — per-icon toggles.
- `docs/evidence/agile-1.7/glass/` — ColorDuo vs Duo.
- `docs/evidence/agile-1.7/report/`.
- Logs and dumps alongside.

## 4. Traceability

| Requirement | Cases |
|---|---|
| BAT-01…05 | T-BAT-01…04 |
| RING2-01…08 | T-RING2-01…08 |
| HOME2-01…06 | T-HOME2-01…06 |
| NAV2-01/02 | T-NAV2-01 |
| ICON2-01…04 | T-ICON2-01…05 |
| GLASS2-01…04 | T-GLASS2-01…04 |
| UX2-01…03 | T-UX2-01…03 |
| QA2-01…04 | T-QA2-01…04 |

## 5. Exit

- Requirements answers recorded.
- All P0/P1 tests pass on CPH2747; other UIs truthful.
- No fatal in transition/camera/rotation.
- Owner approves visual match and Home logic.


## FILE: AGILE-1.7-TRANSITIONS-AFTER.md

# Agile 1.7 — transitions after crossfade fix (de48022)

**Phone:** CPH2747 `[redacted]` · Duo release 1.6.0 code 76 · 1272×2772.
**Baseline:** `agent/AGILE-1.7-TRANSITIONS.md` (267–283 ms Duo-absent gaps on QS/shade → collapsed).
**Fix:** HEAD `de48022655290aa7d777b00a881efbb45ba3054e` — `fix: crossfade Duo across HUD QS shade`.
**Result:** the 267–283 ms blackout is gone. QS→collapsed and shade→collapsed have **no all-zero Duo frame**, and the main copy is opaque on the owner-null frame. A smaller **horizontal position snap (~36–38 px)** remains.

## Build / install

| Item | Result |
|---|---|
| HEAD | `de48022655290aa7d777b00a881efbb45ba3054e` |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk-amd64`) |
| Build | `:app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline` → **BUILD SUCCESSFUL** (4m 24s) |
| APK | `app/build/outputs/apk/release/app-release.apk` (6.9 MB) |
| SHA-256 | `f9da475585d7944cae8086fa53f8b88c5db353029e96c71398a15ed244b8d8e5` |
| Install | `adb install -r` → **Success**; versionName=1.6.0, versionCode=76, lastUpdateTime=2026-11-03 20:25:40 (data preserved) |
| SystemUI | killed for reload; **PID 19271** after restart |
| Tests | `:app:testDebugUnitTest --tests '*PanelHandoffTest*'` → **BUILD SUCCESSFUL** |

Device raw identity: `docs/evidence/agile-1.7/transitions-after/build-after.txt`.

## Recording

Command: root `screenrecord --size 1272x2772 --bit-rate 30000000 --time-limit 60 /data/local/tmp/duo-transition-after.mp4`.

- File: `docs/evidence/agile-1.7/transitions-after/duo-transition-after.mp4`
- **Actual rate: 1,070 frames / 59.998 s = 17.83 fps** (`19260000/1079963`). Stock `screenrecord` did not deliver 60 fps on this run; full-size 24 Mbps was ~33 fps in the previous take and 720p/12 Mbps was also ~33 fps. This is a tool/device limit, reported exactly, not a dropped transition.
- Logs: `docs/evidence/agile-1.7/transitions-after/logcat-after.txt`; the run includes two QS/shade/collapse cycles. The second cycle’s final `collapse` occurred at 20:29:14, **after the 60 s video ended at 20:29:13**; it is not in the video (the first cycle’s six commands are complete).
- SeedlingCard HUD was **not available** in this run: `dumpsys window` and the log contain no `SeedlingCard`; `input keyevent 120` saved screenshots but no HUD card. HUD→QS was therefore not measured. `cmd notification post` remains importance 3 and does not heads-up.

## Before / after gap

| Hand-off | Baseline (pre-fix) | After de48022 | Frames |
|---|---|---|---|
| QS → collapsed | 283 ms Duo absent; main faded in over ~150 ms | **no all-zero Duo frame**; main bbox present at 44.3 s, 45.0 s, 45.1 s | no absent frame in 43.8–45.4 s |
| shade → collapsed | 267 ms Duo absent; main faded in over ~150 ms | **no all-zero Duo frame**; main bbox present at 48.4 s, 49.0 s, 49.2 s | no absent frame in 48.0–49.4 s |
| shade → QS | 1-frame owner gap | 9 ms owner gap; no blank; QS present at 42.5 s | no absent frame |
| QS → shade | 1-frame gap | visible QS→shade at 40.3 s; no absent frame in the sampled window | no absent frame |

`PanelHandoff.main` now does `immediateReveal → Set(1f)`; `DuoHook` calls `setMainBarVisible(true, immediateReveal = true)` on leaving QS/shade, and `setPanelOwner(null)` also forces the immediate reveal. Owner-null log lines:

- QS: `20:28:58.019  panel owner -> collapsed` → main bbox `(1080,16)-(1173,123)`, 2,287 bright pixels at 45.0 s.
- Shade: `20:29:02.175  panel owner -> collapsed` → main bbox `(1080,16)-(1173,123)`, 2,290 bright pixels at 49.2 s.

Stable main is 2,290 bright pixels; the owner-null frame is therefore **opaque**, not a faded copy.

## Position after fix

The after run had battery **67%**, so the Duo ring is white and the old red-arc mask is not applicable. Measurements below use the white ring; baseline numbers in parentheses used the red+white mask at 17%.

| State | After bbox / centre | Baseline centre | Delta |
|---|---|---|---|
| Main | `(1080,16)-(1173,123)` / `(1126.5,69.5)` | `(1127.5,69.5)` | −1 px x, 0 px y |
| QS | `(1044,115)-(1137,223)` / `(1090.5,169.0)` | `(1082.5,169.0)` | +8 px x, 0 px y |
| Shade | `(1118,138)-(1211,244)` / `(1164.5,191.0)` | `(1155.5,173.5)` | +9 px x, +17.5 px y |
| QS at hand-off | `(1044,28)-(1137,135)` / `(1090.5,81.5)` at 44.2 s | — | ring already moved up with the panel |
| Shade at hand-off | `(1118,20)-(1211,127)` / `(1164.5,73.5)` at 48.3 s | — | ring already moved up with the panel |
| QS → main next frame | `(1080,16)-(1173,123)` at 44.3 s | — | **+36 px x, −12 px y**, no blank |
| Shade → main next frame | `(1080,16)-(1173,123)` at 48.4 s | — | **−38 px x, −4 px y**, no blank |

The **blackout is fixed**; the main copy is immediately visible and opaque. The remaining movement is the existing **host/anchor snap**: the QS/shade copy travels up with the panel, then the main copy appears at the status-bar anchor. The residual snap is ~36–38 px horizontally (and 4–12 px vertically at the sampled hand-off), versus the pre-fix 45–104 px jump that was hidden behind the 267–283 ms gap. The shade stable centre is 17.5 px lower than baseline; that may be header settle/content at this capture and needs a follow-up if visible on device. No size regression was observed: the white ring is 94×109 px in QS/shade and 94×108 px in main; the baseline red+white bbox was 108×124 because it included the red battery arc.

## Disabled surfaces

`PanelHandoff.extra` hard-sets `0f` when `enabled == false`; `showSurfaceOwner` cancels any running fade and hard-zeroes the slot, so a disabled QS/shade surface cannot fade back or leave a hidden-stock hole. `PanelHandoffTest` passes. No on-device disabled-surface toggle was performed, to preserve settings.

## Remaining issue

1. **60 fps not achieved by stock `screenrecord`** on this ROM: 17.83 fps full-size/30 Mbps, ~33 fps full-size/24 Mbps and 720p/12 Mbps. The transition absence is verified at the source frame interval (56 ms) and by owner-null logs; a future high-speed capture needs the OEM recorder or a different tool.
2. **Position snap remains** (~36–38 px x) at the QS/shade → main hand-off, even though there is no blank frame. A follow-up should animate the main copy from the last extra position or keep one view across the hand-off.
3. **HUD→QS unverified** because no SeedlingCard/heads-up was available in this run.
4. **Shade centre +17.5 px y** versus baseline; confirm whether this is panel settle or a real layout shift.
5. The second cycle’s final shade→collapsed is after the video end; not captured.

## Evidence

- After video: `docs/evidence/agile-1.7/transitions-after/duo-transition-after.mp4`
- After logs: `docs/evidence/agile-1.7/transitions-after/logcat-after.txt`
- Build/install: `docs/evidence/agile-1.7/transitions-after/build-after.txt`
- Measurements: `measurements-after.csv`, `timeline-after.csv`
- Selected frames: `docs/evidence/agile-1.7/transitions-after/frames/selected/`
  - `stable_qs_after.png`, `stable_shade_after.png`, `shade_to_qs_42.5.png`
  - `qs_to_collapsed_before_44.2.png`, `qs_to_collapsed_after_44.3.png`, `qs_to_collapsed_owner_null_45.0.png`
  - `shade_to_collapsed_before_48.3.png`, `shade_to_collapsed_after_48.4.png`, `shade_to_collapsed_owner_null_49.2.png`
- Contact sheet: `frames/after-collapse-contact.png`
- Baseline: `agent/AGILE-1.7-TRANSITIONS.md`, `docs/evidence/agile-1.7/transitions/`

## Device state

Last confirmed before the link dropped: SystemUI **PID 19271**, focus `NotificationShade` after the final recording. A cleanup command (`cmd statusbar collapse; input keyevent HOME`) was issued but the shell hung, then `adb` lost the route to `[redacted]` (`No route to host`; ping 100% loss). **Home/portrait restoration is unconfirmed** because the phone went offline; no reboot, install, or setting change was attempted after that. Recorder is stopped.


## FILE: AGILE-1.7-TRANSITIONS.md

# Agile 1.7 — HUD / QS / shade transition measurement

**Device:** CPH2747 (`[redacted]`), OxygenOS/ColorOS 16, 1272×2772, density override 506.
**Build measured:** Duo 1.6.0 code 76 (module inside SystemUI; no code/settings changed).
**Date:** 2026-11-03, device timezone +03 (log times below are device time).
**Capture:** root `screenrecord` (native resolution, ~79 fps average; 7,663 frames, 97.05 s) + full `logcat -b all -v threadtime`. Shell `screenrecord` itself failed with `Permission denied`; `su -c screenrecord` succeeded. Shell `cmd notification post` succeeds only on the default-importance `shell_cmd` channel (importance 3), so it does not produce a heads-up.
**Verdict:** the harsh transition is real and reproducible. Duo keeps exactly the same size, but its **host/anchor moves** and it is **absent for 267–283 ms** on every QS/shade → collapsed hand-off. The first cycle also caught an OEM screen-recorder `SeedlingCard` HUD that coexisted with QS and ended in a QS → HUD hand-off.

## What was measured

The Duo glyph is the bright ring around the battery at the top-right. Per frame the measurement is the bounding box of the white ring + Wi-Fi dots + red battery arc (the red arc alone is 82–83 px wide). The full glyph is **108 px wide × 124–125 px high in every state**, so there is **no size change** in this sample; only position and visibility change.

| State | Duo bright bbox (x0,y0)-(x1,y1) | Centre | Parent / anchor |
|---|---|---|---|
| Collapsed / HUD present | `(1074,8)-(1181,131)` | `(1127.5,69.5)` | `StatusBar` window `[0,0][1272,141]`; main bar copy |
| QS | `(1029,107)-(1136,231)` | `(1082.5,169.0)` | `quick_qs_status_icons` `[1137,143][1146,199]` (9×56) inside `qs_status_bar_container_layout`; Duo is in a taller ancestor and aligned to the battery end |
| Shade | `(1102,112)-(1209,235)` | `(1155.5,173.5)` | `quick_qs_status_icons` `[1193,140][1202,183]` (9×43) inside `header_container` `[51,140][1221,267]`; Duo is centred on the battery |
| HUD card (`SeedlingCard`) | Duo stays at the collapsed bbox above | — | HUD is an OEM `SeedlingCard` overlay; Duo is **not** hosted in it |

The `uiautomator` dump is the evidence for the parent bounds: `qs-uiautomator.xml`, `shade-uiautomator.xml`. The 9 px stock cluster is why the module deliberately moves the Duo out to a taller ancestor (`DuoIconHost.kt:989`); inheriting that 9 px cluster would squeeze the ring.

## Timeline — cycle 1 (video PTS 0 = 18:42:26.389)

`t_video` is seconds into `duo-transitions-2026-11-03T1542Z.mp4`; `t_device` is the device clock. Bounds are the bright Duo bbox.

| t_device | t_video | Command / ROM event | Duo bounds | Visibility | Parent / source | Jump / blank |
|---|---:|---|---|---|---|---|
| 18:42:57.030 | 30.641 | OEM screen recorder stops; `Saved to "Photos"` SeedlingCard posted | `(1074,8)-(1181,131)` | visible | StatusBar / main | HUD card appears below; Duo unaffected |
| 18:42:58.658 | 32.269 | `cmd statusbar expand-settings` | before `(1074,8)-(1181,131)` | visible | StatusBar → QS header | HUD → QS; no blank; QS copy at 32.317 |
| 18:42:58.689 | 32.300 | `panel owner -> qs header` | QS starts `(1029,19)-(1136,142)`, settles lower | visible | QS header (`attach=qs header`) | x jump −45 px; ring then travels down with QS |
| 18:42:59.432 | 33.043 | ROM reports QS/notification covered = false; owner collapsed while the SeedlingCard HUD is still present | QS copy disappears; main fades in | **absent 32.983–33.233 (16 frames, 267 ms), then fade to full by 33.383** | QS header → StatusBar / main | **QS → HUD blackout**; main bbox `(1074,8)-(1181,131)` |
| 18:43:00.833 | 34.444 | `cmd statusbar expand-notifications` | before `(1074,8)-(1181,131)` | visible | StatusBar → shade header | HUD → shade; 1 blank frame at 34.417 |
| 18:43:00.867 | 34.478 | `panel owner -> shade header`; 18:43:00.922 `shade surface owner -> shade header (attach=qs header, panelState=true)` | shade starts `(1102,97)-(1209,221)`, settles `(1102,112)-(1209,235)` | visible | shade header / `header_container` | x jump +73 px vs QS; no animation between hosts |
| 18:43:03.026 | 36.637 | `cmd statusbar expand-settings` | shade `(1102,112)-(1209,235)` | visible | shade header | shade → QS |
| 18:43:03.103 | 36.714 | `shade/QS covered -> false`; 18:43:03.104 owner collapsed; 18:43:03.114 owner `qs header` | QS `(1029,107)-(1136,231)` | 11 ms owner gap; no blank frame detected | shade → collapsed → QS header | hard x jump −73 px; no visible blank |
| 18:43:05.113 | 38.724 | `cmd statusbar collapse` | before `(1029,19)-(1136,142)` (ring already travelling up with QS) | visible | QS header | QS → collapsed |
| 18:43:05.650 | 39.261 | `panel owner -> collapsed` | QS copy disappears; main fades in | **absent 39.217–39.483 (17 frames, 283 ms), fade to full by 39.633** | QS header → StatusBar / main | **QS → collapsed blackout**; after `(1074,8)-(1181,131)` |
| 18:43:07.214 | 40.825 | `cmd statusbar expand-notifications` | before `(1074,8)-(1181,131)` | visible | StatusBar → shade header | collapsed → shade; 1 blank frame near 40.783 |
| 18:43:07.247 | 40.858 | `panel owner -> shade header` | shade `(1102,112)-(1209,235)` | visible | shade header | x jump +28 px vs main, +73 px vs QS |
| 18:43:09.277 | 42.888 | `cmd statusbar collapse` | shade ring travels up to `(1102,0)-(1209,116)` | visible until 43.350 | shade header | shade → collapsed |
| 18:43:09.810 | 43.421 | covered false; owner collapsed; owner shade at 43.423; owner collapsed at 43.455 | shade copy disappears; main fades in | **absent 43.367–43.617 (16 frames, 267 ms), fade to full by 43.767**; 32 ms shade re-show flicker at 43.423–43.455 | shade → StatusBar / main | **shade → collapsed blackout + flicker** |

Cycles 2 and 3 repeated the same owner sequence and the same stable bboxes (`QS 1029,107–1136,231`; `shade 1102,111–1209,235`; `main 1074,7–1181,131`). There was no HUD in cycles 2–3: the three `cmd notification post` probes were importance 3 (default), so they stayed in the shade and did not heads-up. The only HUD is the OEM `SeedlingCard` in cycle 1.

## Per-direction result

| Direction | Δ position (stable bboxes) | Visibility result |
|---|---|---|
| HUD → QS | main `(1074,8)` → QS `(1029,107)`: **−45 px x, +99 px y** | no blank frame; QS copy is present at 32.317, then travels down with the QS header |
| QS → HUD | QS `(1029,107)` → main `(1074,8)`: **+45 px x, −99 px y** | **267 ms absent + ~150 ms fade** (QS copy hidden, main alpha-in delayed) |
| HUD → shade | main `(1074,8)` → shade `(1102,112)`: **+28 px x, +104 px y** | 1 blank frame |
| shade → QS | shade `(1102,112)` → QS `(1029,107)`: **−73 px x, −5 px y** | 11 ms owner gap; no blank frame detected at 36.720; hard x jump |
| QS → collapsed | QS `(1029,107)` → main `(1074,8)`: **+45 px x, −99 px y** | **283 ms absent + ~150 ms fade** |
| shade → collapsed | shade `(1102,112)` → main `(1074,8)`: **−28 px x, −104 px y** | **267 ms absent + ~150 ms fade**; 32 ms shade re-show before the fade |

In the blank frames the **stock battery is also not visible** (see `frames/selected/gap_05_qs_to_collapsed.png`, `gap_06_shade_to_collapsed.png`). The module has hidden the stock battery with `INVISIBLE`, and the Duo copy is already at alpha 0 while the main-bar alpha-in is still delayed; that is a complete battery-indicator gap, not just a Duo gap.

## Exact cause candidates

1. **Primary — outgoing copy is hidden immediately, incoming main bar is alpha-only and delayed.**
   - `setPanelOwner` calls `showSurfaceOwner`, which sets the QS/shade extra alpha to 0/1 instantly (`app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoIconHost.kt:685`, `:738`, `:770`).
   - `setMainBarVisible(false)` sets alpha 0 immediately; `setMainBarVisible(true)` uses `MAIN_ALPHA_IN_MS = 320` and `MAIN_ALPHA_IN_DELAY_MS = 50` (`DuoIconHost.kt:706`, constants `:2951-2952`).
   - The panel callback order is `setMainBarVisible(false)` on expansion and `setPanelOwner(null)` on collapse (`DuoHook.kt:682`, `:689`, `:766`). There is no cross-fade between the outgoing extra and the incoming main bar, so the 267–283 ms absent window is the module's own policy, not a dropped video frame.

2. **Position jump — the three surfaces use different hosts and different anchor modes.**
   - QS is attached to/anchored against the QS battery with `alignEnd`; the shade slot is centred; the main bar is fixed in the status-bar window. `anchorOnBattery` (`DuoIconHost.kt:2722`) computes `translationX/Y` from the battery's `getLocationInWindow`; QS passes `alignEnd`, shade does not. The 73 px QS↔shade jump and the ~100 px vertical main↔header jump follow directly from those different battery locations.
   - The QS attach can move the element out of the 9 px stock cluster to a taller ancestor (`DuoIconHost.kt:965`, `:989`). The shade route is selected by `slotName` from `isShadeSurface` (`DuoIconHost.kt:793`), and the log shows the ROM handing the QS cluster to the shade path: `shade surface owner -> shade header (attach=qs header, panelState=true)`.
   - `showSurfaceOwner` only changes alpha; it never animates `translationX/Y`, so the host change is a hard teleport.

3. **HUD/QS hand-off — the module has no HUD host.**
   - The `Saved to "Photos"` card is an OEM `SeedlingCard` (`Window #8 SeedlingCard` in `dumpsys-window-qs.txt`), posted when the pre-existing Oplus screen recorder stopped at 18:42:57.033. Duo stayed in the `StatusBar` at `(1074,8)-(1181,131)`; it is not inside the card.
   - QS was opened at 18:42:58.689, then the ROM reported covered=false / owner collapsed at 18:42:59.432 while the card remained. The module dropped the QS copy and started the delayed main-bar fade. `OplusHeadsUpNotificationViewBinder/isVisibility` moved true→false at the same 18:42:59.432 boundary; that view is part of the panel/heads-up bridge, not a Duo host. The observed result is a QS → HUD hand-off with the same ~267 ms Duo gap.

4. **Transient collapsed owner during shade → QS.**
   - `onShadeOrQsExpanded(false)` at 18:43:03.103 sets owner null; the QS panel callback arrives 11 ms later at 18:43:03.114. The module therefore hides both extras for 11 ms, but the 60 fps capture shows no blank frame (the QS copy is already present at 36.720). On shade collapse the reverse flicker is 32 ms (owner shade at 18:43:09.812, owner collapsed at 18:43:09.844). Code: `DuoHook.kt:682-689`, `DuoHook.kt:766`.

5. **Stock-battery fallback is not used during the hand-off.**
   - `hideSlotStock` hides `batteryRemainingIcon` with `INVISIBLE` (`DuoIconHost.kt:1337`); `anchorBattery` is also forced `INVISIBLE` (`DuoIconHost.kt:2710`). The stock cluster is only 9 px wide (QS 9×56, shade 9×43), so simply inheriting that host is not viable, but the module currently offers no visible fallback while Duo fades.

## Smallest safe experiment (do not change behaviour first)

1. **ADB-only, already valid:** with QS and shade open, `uiautomator dump` shows the stock `quick_qs_status_icons` is 9×56 (QS) and 9×43 (shade), while `header` is 1020×63 / `header_container` is 1170×127. This rules out using that 9 px cluster as the Duo host unchanged. The logs and frames in this report are the baseline.
2. **Next, one debug-only log (no layout/visibility change):** in `anchorOnBattery`, print the slot name, `battery` class and parent class, `battery.getLocationInWindow(...)`, `battery.width/height`, and the computed `seat` / `translationY` for collapsed, QS and shade. Repeat the same `cmd statusbar` sequence and compare. This proves whether the battery's own frame is stable enough to be the single anchor and why QS ends at x=1136 while shade ends at x=1209.
3. Only if that log confirms a stable battery frame, test in a throwaway build: keep the element in its **current overlay parent** for all three states (not the 9 px stock cluster) and drive only `translationX/Y` from the battery's window location; do not reparent a live view, do not resize the Rive view, and keep the stock battery `INVISIBLE` as the anchor. That is the smallest behavioural experiment that can test inheritance without repeating C1/C2/C7.

## Evidence

- Report: `agent/AGILE-1.7-TRANSITIONS.md`
- Video: `docs/evidence/agile-1.7/transitions/duo-transitions-2026-11-03T1542Z.mp4`
- Probe: `docs/evidence/agile-1.7/transitions/video-probe.txt` (1272×2772, 7,663 frames, 97.05 s, avg 229890000/2911567 ≈ 78.96 fps)
- Full log: `docs/evidence/agile-1.7/transitions/logcat-live.txt`
- Filtered log: `docs/evidence/agile-1.7/transitions/logcat-duo-filtered.txt`
- Measurements: `docs/evidence/agile-1.7/transitions/measurements.csv`
- Timeline CSV: `docs/evidence/agile-1.7/transitions/timeline-cycle1.csv`
- Host bounds: `docs/evidence/agile-1.7/transitions/host-bounds.txt`
- Window dumps: `dumpsys-window-collapsed-before.txt`, `dumpsys-window-qs.txt`, `dumpsys-window-shade.txt`, `surfaceflinger-list-before.txt`
- Host bounds: `qs-uiautomator.xml`, `shade-uiautomator.xml`
- Selected before/after/gap frames: `docs/evidence/agile-1.7/transitions/frames/selected/`
  - `before_01_hud_to_qs.png` / `during_01_hud_to_qs_32.317.png` / `after_01_hud_to_qs.png`
  - `before_02_qs_to_hud.png` / `gap_02_qs_to_hud.png` / `after_02_qs_to_hud.png`
  - `before_03_hud_to_shade.png` / `gap_03_hud_to_shade.png` / `after_03_hud_to_shade.png`
  - `before_04_shade_to_qs.png` / `during_04_shade_to_qs_36.720.png` / `after_04_shade_to_qs.png`
  - `before_05_qs_to_collapsed.png` / `gap_05_qs_to_collapsed.png` / `after_05_qs_to_collapsed.png`
  - `before_06_shade_to_collapsed.png` / `gap_06_shade_to_collapsed.png` / `after_06_shade_to_collapsed.png`
  - stable: `stable_collapsed.png`, `stable_hud.png`, `stable_qs.png`, `stable_shade.png`, `qs-screenshot-now.png`
  - cycle 3 verification: `cycle3_60.5.png` … `cycle3_70.9.png`
- Overview: `frames/contact-sheet-5s.png`, `frames/state-grid-cycle1.png`, `frames/right-top/contact-31.5-45.5.png`

## Device state after measurement

- Home (`com.android.launcher`), orientation 0 / portrait, keyguard not showing.
- QS/shade collapsed; no `screenrecord` or OEM recorder process left running.
- `ColorDuo` and the Duo settings app were force-stopped; `AutoExpand` and `Duolingo` were force-stopped during cleanup.
- No Duo setting, build, install, reboot or system setting was changed. The only notification-state probe was a 5 s Telegram snooze; it expired, the immediate unsnooze call was rejected, and the Telegram notification is present again.


## FILE: AGILE-1.8-ALPHA11.md

# AGILE 1.8 — alpha.11 on-device verification attempt

**Date:** 2026-11-04 (UTC)
**Target:** CPH2747, OxygenOS 16, 1272×2772
**Build under test:** `1.7.0-alpha.11`, versionCode `87`
**Result: PARTIALLY VERIFIED — the host could not reach the device, but an
owner-supplied alpha.11 video and diagnostic ZIP were analysed.** No install, build,
or direct device access was performed; install state, SystemUI PID stability and the
crash buffer remain unverified from the host.

The connectivity attempt is retained below. The new measured results are in
**“Owner-supplied alpha.11 capture”**.

**Headline:** real capture **62.855 fps, not 60**; **zero fully blank Duo frames**;
**8 frames (128.5 ms) with no Duo in the recorded strip** during the HUD→Shade panel
travel (element below the crop, not proven blank); final x residual **0** on all
HUD handoffs; no `panel cover` / `panel reveal` lines in the supplied log window.

## Build identity (static, verified here)

| Item | Value |
|---|---|
| APK | `app/build/outputs/apk/release/app-release.apk` |
| Size | 7,474,080 bytes |
| SHA-256 | `665d07f621a8c389a8c6c89f37e6886ee2255617a62c93c921f12325ecd2a301` |
| versionCode | `87` |
| versionName | `1.7.0-alpha.11` |
| package | `io.github.kvmy666.duostatusbar` |
| minSdk / targetSdk | 33 / 36 |

The APK matches the requested code and hash exactly. Identity was read with
`aapt dump badging`; see `05-apk-identity.txt`.

## Owner-supplied alpha.11 capture (measured 2026-11-04)

Source: owner video `efddc51c-….mp4` (3,504,585 bytes) and report ZIP
`1b0f985a-….zip`. Read-only analysis with Bash/ffmpeg/Python; no phone, build or
install was used. Evidence: `docs/evidence/agile-1.7/transitions-alpha11-owner/`
(git-ignored, kept on disk).

### Video

| Item | Measured |
|---|---|
| Resolution / codec | 696 × 132 h264 (cropped strip, ≈ top 241 device px at 1272 wide) |
| Frames / duration | 500 / 7.954767 s (PTS span 7.9384 s) |
| Avg fps | **62.855** (`15000000/238643`; 500/7.954767) |
| Cadence | VFR: dt median 15.867 ms, min 3.411 ms, max 28.467 ms |
| 60 fps? | **No — not a constant 60.000 fps.** `r_frame_rate=90000/1` is a container artifact. |

### Duo bounds (video px; bright mask x ≥ 575, threshold 170)

| State | bbox | size | device-equiv (×1.8276) |
|---|---|---|---|
| HUD | 589–643, 2–72 | 55 × 71 | ≈100 × 130 |
| QS rest | 576–629, 58–128 | 54 × 71 | ≈99 × 130 |
| Shade rest | 608–662, 51–122 | 55 × 72 | ≈101 × 132 |

No size flash: every unclipped frame is 54–55 × 69–72 video px.

### Absent / weak frames

- **No fully blank (all-zero) Duo frame observed.**
- **8 visually absent frames: f120–f127**, t = 1.8961 → 2.0246 s, **128.5 ms**. The
  element is partly at the bottom edge at f119 (y 126–131) and re-enters at f128
  (y 130–131), so it travelled **below the 132-px crop**. A true blank cannot be
  distinguished from off-crop travel in this crop.
- **One crossfade-dip frame: f86** (t = 1.3522 s): only the incoming shade copy at
  ~60 % brightness, no outgoing copy. It is dim, not blank.

### main / extra ordering (threshold-100 union width > 60 px = two copies)

| Transition | Double-image window | Ordering | Blank frames |
|---|---|---|---|
| HUD→Shade #1 | f85; f86 dip (incoming only); f87–f89 double (31.6 ms) | incoming shade over outgoing HUD | 0 |
| Shade→HUD #1 | none | **inversion:** main at HUD x (f164–165) replaced by extra at shade x (f166), then extra slides to HUD x by f174 | 0 |
| HUD→QS | f210–f211 (15.1 ms, identical frames) | incoming QS over outgoing HUD | 0 |
| QS→HUD | none | single copy slides | 0 |
| HUD→Shade #2 | f328–f329 (15.9 ms, identical frames) | incoming shade over outgoing HUD | 0 |
| Shade→HUD #2 | none | single copy slides | 0 |

### x handoff, y, residual

| Transition | Handoff | Final residual |
|---|---|---|
| HUD→QS | incoming x0 576 vs HUD 589: **−13 video / −24 device px** | QS→HUD settles at 589, **residual 0** (f296, 80 ms) |
| HUD→Shade | incoming x0 608 vs HUD 589: **+19 video / +35 device px** | Shade→HUD settles at 589, **residual 0** |
| Shade→HUD #1 | one-frame backwards jump to shade x at f166 (+35 device px) for ~32 ms, then slide to HUD x by f172–f174 | **0** |
| Shade→HUD #2 | slide 608→589 over f379–f386 (**126.7 ms**) | **0** |

y: HUD rest y0 = 2; QS rest y0 = 58; Shade rest y0 = 51. Vertical travel finishes
before the horizontal handoff (y0 = 2 at f164/f291/f378), so the handoff is
horizontal-only.

**HUD→QS one-frame jump:** not reproduced. HUD→QS is a 2-frame double image
(f210–f211, duplicated frames) with the incoming copy already at QS x = 576, then
descent; the x change completes across f208–f212 (~80 ms) with no blank frame.

### Log ZIP

| Item | Value |
|---|---|
| App / code | `1.7.0-alpha.11 (code 87)`, release |
| Stage / renderer / attached | `stage=2 · renderer=Rive · attached=true · riveAttempts=1` |
| Window | `attached=true size=1272x141`, density 3.1625, slot `LinearLayout 288x63 width=134` |
| SystemUI | `16.99.12 (169912)`, PID 13410, uid 10266 |
| Settings | `sizePercent=169, offsetX=0, percentHeight=23, split=false, lock=false, indicatorsOffsetX=-46, revealMs=1000, designStyle=old, showInQs=true, showInShade=true, hideOtherIcons=true, wifiDots=false, showWifiGlyph=false, showCellDots=true, eventMask=255` |
| hiddenStockIcons | **empty** |
| Cell | level 4 / 5, levels `[4, 4]`, 5G, mobileData true, subs `[2, 1]` (picked 2), RAT LTE, dbm −85, dots = 4 |
| Wi-Fi | enabled true, active false, validated false, level 0, RSSI −127, calc5 0, stockBars 0, dots 0 |
| FATAL / crash | **none**; no tombstone/ANR/FATAL lines |
| Other | handled `NoSuchFieldException` for `ActivityThread$AppBindData` (twice); one snapshot says `renderer=Rive ready=false`, the later status says `attached=true` |

- `panel owner -> collapsed / shade header / collapsed` and `shade/QS covered -> false`
  are present.
- **`panel cover` / `panel reveal` lines are absent** from this ZIP. The module log
  window covers 18:57:09–18:57:12 only and does not cover the video's transitions
  (first handoff at t = 1.34 s of the clip), so this is **not** proof that the
  `PanelHandoff` guard failed — the lines were simply outside the captured window.
- **Per-icon inventory:** `module stock icon inventory stored (633 chars)` at
  `1793807582532` **did appear**, with `hiddenStockIcons=` empty. The 633-char
  payload itself is not included in the dump, so the per-icon list content cannot be
  verified from this ZIP.

### Comparison with `AGILE-1.8-NOTES.md`

- The alpha.10 note said “one ~35 ms Duo-absent frame at the first QS→HUD; HUD→QS is
  still a one-frame jump”. **Neither is observed here:** QS→HUD has 0 blank frames,
  and HUD→QS is a 2-frame double image, not a one-frame jump.
- The new residual is **HUD→Shade #1**: one dim single-copy frame (f86) and a
  3-frame double image; and **Shade→HUD #1**: a ~32 ms ordering inversion where the
  extra copy redraws at shade x after the main copy reached HUD x.
- The capture is 62.855 fps VFR, **not 60 fps**; do not record it as a 60 fps capture.
- Finding 2 is consistent: `169 / −46` is the portrait set and is the only set in this
  report.

## Connectivity attempt

Host: Linux 7.0.0-42-generic, adb 1.0.41 (34.0.5-debian), local address
`[redacted]/24` on `wlo1`.

| Step | Command | Observed |
|---|---|---|
| Attached devices | `adb devices -l` | empty list |
| mDNS | `adb mdns services` | `List of discovered mdns services` — none |
| Endpoint 1 | `adb connect [redacted]` | `failed to connect ...: No route to host` |
| Endpoint 2 | `adb connect [redacted]` | `failed to connect ...: No route to host` |
| Devices after | `adb devices -l` | still empty |

`adb connect` exits 0 even when the connection fails; the printed message is the verdict.

## Why this is a network absence, not an adb fault

- `ping [redacted]` and `ping [redacted]` → **100% packet loss** (2/2 lost).
- The gateway `[redacted]` answers in 1.7–3.7 ms, and other LAN hosts are present in
  the neighbour table, so the local network is healthy.
- `ip neigh` shows `[redacted] dev wlo1 FAILED` and
  `[redacted] dev wlo1 FAILED`: ARP never resolved, i.e. no host is answering at
  layer 2.
- A TCP/6666 sweep of all 254 addresses in `[redacted]/24` found **no** listening
  host, so the phone is not merely on a new DHCP address.

Conclusion: CPH2747 is powered off, off Wi-Fi, or has wireless debugging disabled. The
host side cannot proceed through any route.

## Not measured from the host (still unverified)

The owner capture covers the transition geometry, but the following remain unverified
from the host:

- install (`adb install -r`) and post-install `versionName`/`versionCode`/`lastUpdateTime`
- SystemUI PID stability across the recorded transitions and the crash buffer
- a true 60 fps capture (this clip is 62.855 fps VFR)
- the 633-char per-icon inventory payload (only the “stored” log line exists)
- `panel cover` / `panel reveal` lines during the recorded transitions (outside the log window)

Measured from the owner capture (do not re-list as missing): real fps, Duo bounds per
state, absent/weak frames, main/extra ordering, x/y/size, and the HUD→QS jump status.

## What the pass was meant to verify

Fix commit `652024d` ("fix: remove one-frame surface handoff gaps") adds
`PanelHandoff.swapOrder(incomingDrawable, waitedFrames)`:

- `SWAP` — the incoming copy is drawable, so the outgoing copy may hide this frame.
- `HOLD_ONE_FRAME` — hold the outgoing copy for exactly one frame.
- `KEEP_OUTGOING` — the guard expired with no drawable copy; keep the outgoing copy
  (fail silent, never blank).

When a device is available, the checks are:

1. QS → HUD: `panel cover: main hidden after the incoming copy drew` must appear after
   the incoming copy is drawable, with no all-zero Duo frame.
2. HUD → QS/Shade: `panel reveal: keeping the outgoing extra (main copy not drawable)`
   may appear for at most one frame; the outgoing copy must stay on screen.
3. No `panel cover: keeping the main copy (incoming copy not drawable)` unless the
   incoming copy genuinely failed to render.
4. SystemUI PID unchanged across the transitions, empty crash buffer.

## Retry

Bring CPH2747 back on the `[redacted]/24` network with wireless debugging enabled,
confirm its address with `adb mdns services`, then:

```
adb connect <phone-ip>:6666
adb devices -l
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell dumpsys package io.github.kvmy666.duostatusbar | grep -E 'versionCode|versionName'
```

Record with scrcpy at 60 fps if installed, otherwise root `screenrecord`, and report the
real measured fps rather than the nominal one.

## Evidence index

All under `docs/evidence/agile-1.7/transitions-alpha11/`:

| File | Contents |
|---|---|
| `00-host-env.txt` | host kernel, adb version, local address, default route |
| `01-adb-devices-mdns.txt` | `adb devices -l` and `adb mdns services` |
| `02-adb-connect.txt` | both connect attempts and the device list afterwards |
| `03-network-reachability.txt` | pings to `.245`, `.102`, gateway; `ip neigh` |
| `04-lan-6666-sweep.txt` | TCP/6666 sweep of [redacted]/24 |
| `05-apk-identity.txt` | APK size, SHA-256 and `aapt` version fields |

Owner capture (git-ignored, under `docs/evidence/agile-1.7/transitions-alpha11-owner/`):

| File | Contents |
|---|---|
| `video-probe.txt` | ffprobe stream/format, 500 frames, 62.855 fps, VFR cadence |
| `measurements.csv` | per-frame visible-pixel count and bbox, all 500 frames |
| `report.md` | transition table, absent/weak frames, ordering, x/y/size |
| `frames/` | 19 selected before/after/double/absent frames |

## Repo state

Documentation and evidence only. No application code, build file or binary was changed,
and no APK was rebuilt; the tested artifact is the existing `5805a69` release build.


## FILE: AGILE-1.8-NOTES.md

# AGILE 1.8 — 1.8.0 alpha notes

## Alpha.8 status

- **1.8.0-alpha.8 installed on CPH2747 at 13:50:03.**
- The APK was delivered to the owner through Telegram (message 45).
- **Owner device verification is pending.** No agent device visual test was run.
- **System UI / phone restart is required after the module update** before testing.

## Version

- `versionCode = 97`, `versionName = "1.8.0-alpha.8"` (`app/build.gradle.kts:47-48`).
- APK: `app/build/outputs/apk/release/app-release.apk`.
- Size: **7,511,605 bytes**.
- SHA-256: `28b19f230d705baed4b55faac6616e15319bc1c53578034706f29999a8a1c833`.

## Changes since alpha.7

- **Idle layering:** while idle the tab row stays above the selector; the selector keeps the frosted
  surface with no blur, so icons and labels remain sharp.
- **Moving layering:** while moving the selector is drawn above the tab row and samples
  `selectorMovingBackdrop = page + pill + row`, restoring the lens and chromatic aberration during
  the drag.
- **No blur added:** the selector still has no blur in either state.
- **Shade seat:** remains the v89 battery anchor from alpha.7 (owner confirmed fixed).

## Gates

- **482 unit tests**, 0 failures, 0 errors, 0 skipped.
- Release build passes.
- Route-module log check clean (`route-module-logs.py --check`: 0 files to fix,
  0 log calls left for review).
- Both Rive scenes report **0 inert** easing keyframes (`check-rive-eases.py`:
  158/158 keyframes carry a curve).
- `app/src/main/res/raw/duo.riv` SHA-256 (new design):
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`.
- `app/src/main/res/raw/duo_old.riv` SHA-256 (old design):
  `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

## Remaining unverified

- **Movement lens:** confirm the lens and chromatic aberration are visible while dragging, with the
  selector drawn above the row.
- **Stash recovery:** `stash@{0}` is retained and must not be dropped.
- Other ROMs remain unverified.

## Alpha.7 (historical)

- `versionCode = 96`, `versionName = "1.8.0-alpha.7"`; installed on CPH2747 at 13:34:13 and
  delivered through Telegram message 44.
- APK size: 7,510,533 bytes; SHA-256:
  `96892fcced3aad0e4066f5b7ac86ce3c529a8d4224745ace4bdd53538585c068`.
- Pill owns all blur; selector below the row with no blur; frosted surface white 0.12 (dark) /
  black 0.06 (light) fading with press; inner/drop shadow and always-on highlight; blue
  selected/hovered icon; shade seat restored to the v89 battery anchor; `H:mm`/`h:mm` clock.
- 482 unit tests; Rive 158/158; `duo.riv` SHA-256:
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`; `duo_old.riv`
  SHA-256: `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

## Alpha.5 status (historical)

### Version

- `versionCode = 94`, `versionName = "1.8.0-alpha.5"` (`app/build.gradle.kts:47-48`).
- APK: `app/build/outputs/apk/release/app-release.apk`.
- Size: **7,510,057 bytes**.
- SHA-256: `1bc96ca2d646caa5f36ad3248ab5dea464a02e694124f64156bfe510d0c5af86`.
- The APK was delivered to the owner through Telegram (message 43); direct install was pending
  because the phone was offline.

### Changes since alpha.4

- **Shade header seat:** the shade header returns to the pre-Phase-1 `place()` seat
  (`translationY = ringAnchorShiftY`, X = flow nudge), with no battery anchor and no
  shade-only clamp. QS and keyguard placement are unchanged.
- **Custom page:** the Default/Compact/Custom preset chips and the Compact resize shortcut are
  removed; the current settings become the Custom baseline.
- **Selector colours:** the selected/hovered tab icon is blue (`DuoColors.NavAccent`); the other
  tabs are white. The idle selector has no tint — the static rim only — and the lens/highlight
  remain movement-only (`dragging || press > 0.01`).

### Gates

- **482 unit tests**, 0 failures, 0 errors, 0 skipped.
- Release build passes.
- Route-module log check clean (`route-module-logs.py --check`: 0 files to fix,
  0 log calls left for review).
- Both Rive scenes report **0 inert** easing keyframes (`check-rive-eases.py`:
  158/158 keyframes carry a curve).
- `app/src/main/res/raw/duo.riv` SHA-256 (new design):
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`.
- `app/src/main/res/raw/duo_old.riv` SHA-256 (old design):
  `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

### Remaining unverified

- **Device install:** the phone was offline (`No route to host`), so alpha.5 was not installed
  directly; installation and the visual pass remain the owner's.
- **Shade seat:** confirm the pre-Phase-1 `place()` seat on the home bar, then in the shade/QS
  header, with no battery anchor and no clamp.
- **Selector:** confirm the selected/hovered icon is blue and the other tabs white; while idle,
  confirm the static rim only (no tint), and that the lens/highlight appear only while
  dragging/pressing.
- **Custom page:** confirm the preset chips and the Compact resize shortcut are gone, and that
  the current settings are used as the Custom baseline.
- **Stash recovery:** the working tree was recovered from `stash@{0}` without `git reset` or
  `git stash pop`; **`stash@{0}` is retained until device verification** and must not be dropped
  yet.
- Other ROMs remain unverified.

## Alpha.4 (historical)

- `versionCode = 93`, `versionName = "1.8.0-alpha.4"`; delivered through Telegram message 42.
- APK size: 7,519,569 bytes; SHA-256:
  `6b3dc094695ed3f28bddcdf4daf0d4407cd5e5d4d1f0f64a10ac97a259a8e9cf`.
- v89 extras placement; static selector rim with movement-only lens/highlight; invert charging
  bolt at ring centre in both designs and the Canvas fallback; hour-only clock; unsnapped
  Wi-Fi/cell/5G sliders; Classic card background `#101014`.
- 482 unit tests; Rive 158/158; `duo.riv` SHA-256:
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`; `duo_old.riv`
  SHA-256: `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

## Alpha.3 (historical)

- `versionCode = 92`, `versionName = "1.8.0-alpha.3"`; delivered through Telegram message 41.
- APK size: 7,518,853 bytes; SHA-256:
  `6c1c3899cfd4d8d93cd318fc3793ba2cb1b3711ed232cf2696c4511ab11ee591`.
- Navigation icons packaged and tinted (active 24 dp / inactive 17 dp); selector lens/highlight
  movement-only; 5-bar cellular mapping 1,1,2,3,4; explicit `rightSeat` shade/home seat; gray
  22% 2-SIM battery track; static Classic card; setting previews follow the selected design;
  Custom Live Preview removed.
- 483 unit tests; Rive 158/158; `duo.riv` SHA-256:
  `70c4624c137dd374f4826e3d653504cb914f4e0b01d432ddc8ad2a2bf748c13e`; `duo_old.riv`
  SHA-256: `0ac9618c428e12b493058d17596e5b136fa6512f7ea352ee17b73b03d46f314f`.

## Alpha.2 (historical)

- `versionCode = 91`, `versionName = "1.8.0-alpha.2"`.
- APK size: 7,583,516 bytes; SHA-256:
  `5d0ddf42bed19fe09146295df7af81b46961eb286871608475c4064d5ffca2f9`.
- `adb install -r` on CPH2747 returned Success at 19:31:22; app data was kept.
- Design-switch fatal fixed: two persistent Rive panes in the app preview, no destroy/recreate;
  design cards moved to still images (`classic_demo.gif`, `design_dual.png`).
- Battery line direction driven by `batteryLineStart`; live event seat moved to `eventSlot` with
  `INVERTED_MIDDLE_Y=-43.35`; selector moved under the tabs with a 0.04 tint and no
  blur/vibrancy; shade/header patch restored.
- 482 unit tests; Rive 158/158 keyframes with a curve; `duo.riv` SHA-256:
  `39910296c24d55fee6efa8e58d4daefe242b01cdf4ebeb2728bbf19acd22db49`.

## Alpha.1 (historical)

- `versionCode = 90`, `versionName = "1.8.0-alpha.1"`.
- Size: 7,479,360 bytes; SHA-256:
  `fb3d1d613a2faba6dccdd3861025e8dccc2975e7a0978850efa60f113d2964e8`.
- Installed on CPH2747 at 18:14:24 with data kept.


## FILE: AGILE-1.8-PLAN.md

# Duo Status Bar — Agile 1.8 Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.8 |
| Date | 2026-11-05 |
| Requirements | `AGILE-1.8-REQUIREMENTS.md` |
| Tests | `AGILE-1.8-TEST-PLAN.md` |
| Review | `CODE-REVIEW-PLAN-1.8.0.md` |
| Walkthrough | `WALKTHROUGH-PLAN-1.8.0.md` |
| Baseline | 1.7.0 stable (versionCode 89), 449 unit tests, CPH2747 |
| Branch | `feat/1.8-selector-live-preview` at `eafdcb8` |
| Preserved branch | `fix/1.7.0-review-fixes` at `eafdcb8`; commits `745fc5f`, `eafdcb8` |
| Status | **Plan only. No 1.8 product code until requirements, plan and test plan are approved.** |

## 1. What changes

- The bottom selector adopts the measured Backdrop 1.0.1 highlight and radial press glow, with one rim owner on the live path and no unintended 24 dp shadow.
- Hover turns the tab blue; release makes the selected tab a large white icon with a small label, inside the existing four-tab pill.
- The 2 SIM battery line becomes live in the real icon and the remaining live preview; the Classic scene is frozen.
- Invert center becomes a real, persisted status-bar behaviour stored in the existing `middle_slot=percent` setting.
- The static preview card and the whole layout simulator are removed; one Live Preview Controls card remains.
- Position becomes right-anchored in normal mode; camera/compact centered mode stays explicit.
- Classic/2 SIM design cards become fixed runtime Rive demos; event previews follow the selected design.
- All user-facing copy is rewritten in short, plain English and moved to string resources where hardcoded.
- Both Rive scenes gain the same property contract; both `.riv` files are rebuilt and hash-checked.

This plan does not reopen 1.6/1.7 safety work and does not add architecture.

## 2. Evidence baseline

| Fact | Evidence |
|---|---|
| 1.7.0 stable is the rollback baseline | `agent/RELEASE-NOTES-1.7.0.md`; versionCode 89 |
| 449 unit tests pass on the baseline | `agent/CODE-REVIEW-REPORT-1.7.0.md` |
| ColorDuo 1.1.7 measured nav values | `agent/AGILE-1.7-COLORDUO.md` |
| Backdrop dependency is 1.0.1, minSdk 33 | `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| Current selector has a manual double rim and a vertical press gradient | `ui/GlassSurface.kt`, `ui/DuoShell.kt` |
| Current Home has a static preview card plus a Layout simulation card | `ui/DuoHomeScreen.kt`, `ui/PreviewSimulator.kt` |
| Current Position editor is centred in every mode | `ui/DuoSettingsComponents.kt` (`positionPreviewOffset`, `positionOffsetLimit`) |
| Current `lockRing` centres natively but the editor only exposes centered mode when `splitIndicators` is also on | `hook/DuoIconHost.kt`, `ui/DuoSettingsSections.kt`, `settings/DuoPrefs.kt` |
| Both scenes currently declare the same 55 view-model properties | `rive/duo/scene.rml`, `rive/duo/scene_old.rml` |
| No literal design-option GIF exists in git history | verified in session exploration 2026-11-05 |
| `.kilo/` is untracked; `docs/evidence/` is git-ignored | `git status`, `.gitignore` |

A0 records the exact Backdrop 1.0.1 highlight/shadow API symbols. No API name is guessed in this plan.

## 3. Phases

Every requirement has exactly one phase. Phase details name the primary test IDs from the test plan.

| Phase | Name | Requirements | Main deliverables | Primary tests |
|---|---|---|---|---|
| A0 | Evidence and contract freeze | QA3-01, QA3-04 | Backdrop API note, current-code inventory, Rive property contract, branch/process record, closed traceability table. No product code. | T-QA3-01, T-QA3-04 |
| A1 | Selector glass and navigation | GLASS3-01, GLASS3-02, NAV3-01, NAV3-02 | One-owner live highlight, no 24 dp shadow, radial press glow, hover index, selected white icon/label, replaceable icon layer, four tabs. | T-GLASS3-01, T-GLASS3-02, T-NAV3-01, T-NAV3-02 |
| A2 | Battery line and invert visual | BATT3-01, BATT3-02, INV3-01 | Live 2 SIM battery line in Rive, Canvas and preview; invert-center visual mapping in both designs; both scenes property-parity and rebuilt `.riv`. | T-BATT3-01, T-BATT3-02, T-INV3-01 |
| A3 | Home cleanup, invert toggle, position | INV3-02, HOME3-01, HOME3-02, POS3-01 | Delete static preview and `PreviewSimulator.kt`; one Live Preview Controls card; real-drivable controls only; Invert center persisted as `middle_slot=percent`; right-anchored Position and unified `lockRing` mode. | T-INV3-02, T-HOME3-01, T-HOME3-02, T-POS3-01 |
| A4 | Design demos and copy | DEMO3-01, DEMO3-02, COPY3-01 | Fixed Classic/2 SIM runtime demos, design-following event previews, selector semantics, full short-English copy pass, hardcoded report/preview text moved to `strings.xml`. | T-DEMO3-01, T-DEMO3-02, T-COPY3-01 |
| A5 | Gates and review handoff | QA3-02, QA3-03 | Full unit/release/route/ease/hash gates, both-scene property parity, device handoff, review and walkthrough evidence. | T-QA3-02, T-QA3-03 |

### A0 — Evidence and contract freeze (blocking)

1. Measure the Backdrop 1.0.1 highlight API: parameter names for width, blur, alpha, intensity, angle, falloff, blend mode, and shadow. Record the exact symbol that disables the default 24 dp shadow. Do not invent a call.
2. Record the current selector rim and press-reflection sites, hover/drag state, design selectors, `PreviewSimulator` control inventory, position mapping, `lockRing` predicate, and the old `middle_slot=percent` behaviour.
3. Parse `DuoBinder` property names and both scene property lists; freeze the current 55-name baseline. Any later addition must update both scene lists and `PROPERTY_COUNT`.
4. Confirm the no-GIF finding with the repository history and record it. Do not claim deleted assets.
5. Record branch heads `eafdcb8` for both `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`, and the preserved commits `745fc5f`, `eafdcb8`.
6. Write the traceability audit and evidence placeholders. No app code, Rive file, string resource or commit is changed in A0.

**Exit:** Backdrop API note, current-code inventory, property contract, process record, traceability audit. A0 evidence is under `docs/evidence/agile-1.8/`.

### A1 — Selector glass and navigation

1. Replace the manual live-path double rim with the Backdrop highlight as the single owner; keep one manual rim only in the non-live fallback.
2. Set the measured highlight tokens: 0.75 dp width, 0.4 dp blur, 0.78/0.96 alpha, intensity 0.5, 45°, falloff 1.0, Plus. Disable the 24 dp default shadow.
3. Replace the vertical press reflection with the radial white `0.16 * press` glow, radius `1.5 * height`, 8 dp inset.
4. Add a pure `hoverIndex` function for the drag position; use it to tint the hovered icon and label #3C88F8.
5. On release, render the selected icon about 24 dp, pure white, centred in the selector, with a small label below; keep four tabs.
6. Add one replaceable icon source/map. Do not edit the prototype vector paths.
7. Add `selectableGroup`/selected semantics where the nav is selectable; keep labels out of the blurred backdrop layer.

**Files:** `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt`, `ui/DuoNavIcons.kt`.

**Do not touch:** Rive scenes, `DuoMapping`, `DuoBinder`, app settings, user-facing copy. A1 may add an accessibility string key; A4 owns the final copy pass.

### A2 — Battery line and invert visual

1. Derive one clamped level value 0–100 for the 2 SIM bottom arc. Bind the new scene's `batteryLine` to a level-derived trim property; if a new property is required, declare it in **both** `scene.rml` and `scene_old.rml`, add it to `DuoBinder`, and update `PROPERTY_COUNT`.
2. Keep the Classic ring-fill path exactly as the 1.7 baseline.
3. Apply the invert-center visual mapping to both designs: percentage centre, normal middle occupant above and smaller, one number only. Use a measured geometry value; do not guess the centre seat.
4. If invert needs a new draw property or state-machine input, add it to both scenes and the binder; the property-parity test must stay green.
5. Rebuild `app/src/main/res/raw/duo.riv` and `duo_old.riv` from `rive/duo --once`; hash-compare both.
6. Keep the Canvas fallback and the in-app preview on the same mapping; no second source of truth.

**Files:** `rive/duo/scene.rml`, `rive/duo/scene_old.rml`, `app/src/main/res/raw/duo.riv`, `app/src/main/res/raw/duo_old.riv`, `hook/DuoBinder.kt`, `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, `hook/DuoCanvasView.kt`.

**Do not touch:** nav glass, position code, user-facing copy, or preview/home structure. A2 exposes the mapping; A3 integrates it into the preview.

### A3 — Home cleanup, invert toggle, position

1. Remove the static **Live preview** card and the **Layout simulation** card from Home.
2. Delete `ui/PreviewSimulator.kt` and every reference; delete per-dot/custom-distribution controls and strings with it.
3. Keep one **Live Preview Controls** card with the interactive preview. Move battery level, charging, battery saver, Invert center, Wi-Fi/5G/4G/flashlight and other real-model controls.
4. Invert center writes/reads `middle_slot=percent`; no new column/schema. Explain the migration from the old percent-in-network behaviour in the UI copy and docs.
5. Normal Position becomes right-anchored: `0 = right edge`, range `-limit … 0`, negative moves left. Camera/Compact/`lockRing` centered mode uses `-limit … +limit` and 0 = centre.
6. Use one predicate for UI, preference clamp and native placement. Fix the current mismatch where `lockRing` centres natively but the editor exposes centered mode only for `splitIndicators`.
7. Document RTL: the normal field is physical right/left, not start/end; 0 is the right edge in both LTR and RTL. Test the sign convention.
8. Preserve the pointer-capture behaviour: no lost drag, no disappearing element, no reset-to-grab.

**Files:** `ui/DuoHomeScreen.kt`, `ui/PreviewSimulator.kt` (delete), `ui/DuoPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsComponents.kt`, `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt`.

**Starts after A2** because the invert visual contract and the preview must match the final mapping.

### A4 — Design demos and copy

1. Classic design card always plays the Classic scene; 2 SIM card always plays the new scene. These are fixed demos, independent of the selected design.
2. Live Events previews follow the selected design; in split mode use the ring part when supported. Do not fake a second view.
3. Add `selectableGroup`, exactly-one-selected normalization, and a clear disabled state to the ring design selector. After `PreviewSimulator.kt` is deleted, there must be no second always-enabled design selector.
4. Rewrite all user-facing copy: labels/headings one line at 360 dp, helper text no more than 1.5 short lines (at most two lines, second short), no developer terms, placeholders preserved.
5. Move hardcoded report/preview text into `strings.xml`; remove dead simulator strings.

**Files:** `ui/DuoHomeScreen.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsScreen.kt`, `ui/DuoReportScreen.kt`, `ui/LogReporter.kt`, `res/values/strings.xml`, remaining UI files with hardcoded user text.

**Starts after A3** because it touches the same Home/preview/settings files. One writer only.

### A5 — Gates and review handoff

1. Run the full unit suite and the release build; keep the existing 449 tests green and add the 1.8 tests.
2. Run the route-log gate, Rive ease gate (both scenes if the script only covers the new scene), and both `.riv` hash comparisons.
3. Verify property parity for both scenes and `DuoBinder.PROPERTY_COUNT`.
4. Hand the device walkthrough to the owner; mark CPH2747-only truth and all other ROMs `unverified`.
5. Write the 1.8.0 review and walkthrough reports from evidence. No release, no main merge, no push, no store.

**Files:** tests, `tools/check-rive-eases.py` only if the old scene is not covered, review/walkthrough documents. Product code changes only for confirmed findings.

## 4. Parallel subagent waves and file serialization

Implementers must be code-capable agents (the main agent or `deepseek-v4.1-flash`, per `AGENT.md`); BEAM never edits code. Read-only verifiers may run in parallel. The main agent owns integration and the final diff.

| Wave | Runs in parallel | Files | Rule |
|---|---|---|---|
| 0 | A0 only | evidence/docs | Blocks all waves. No product code. |
| 1 | A1 selector/nav + A2 Rive/mapping | A1: `GlassSurface.kt`, `GlassControls.kt`, `DuoShell.kt`, `DuoNavIcons.kt`; A2: both `.rml`, both `.riv`, `DuoBinder.kt`, `DuoMapping.kt`, `RingGeometry.kt`, `DuoCanvasView.kt` | Disjoint file sets. No shared file. Both rebuild their own area. |
| 2 | A3 only | Home/preview/settings/native position files listed in A3 | Starts after A2 freezes the invert visual contract. One writer for `DuoHomeScreen.kt`, `DuoSettingsSections.kt`, `DuoPrefs.kt`, `DuoIconHost.kt`. |
| 3 | A4 only | design demos, event previews, strings/copy files | Starts after A3. Strings/copy is a single lane across all UI files. |
| 4 | Test authors + read-only verifiers | test files by area; no product files | Parallel only where test files differ. The main agent runs integration and fixes. |

Serialization rules:

- No two agents edit the same file in the same wave. If a file must change in two phases, the later phase starts after the earlier phase's handoff.
- `DuoHomeScreen.kt`: A3 cleanup first, A4 demos/copy second.
- `DuoSettingsSections.kt`: A3 position/invert UI first, A4 copy second.
- `DuoPrefs.kt`: A3 only (position clamp/invert read-back); A4 does not change storage.
- `DuoBinder.kt`: A2 only; A4 copy never changes property names.
- `strings.xml`: A4 only after all UI structure is frozen; no concurrent string edits.
- `DuoIconHost.kt`/`SlotGeometry.kt`: A3 only; no glass/nav or Rive changes in the same wave.
- Test files: one owner per existing test file; new test files may be created in parallel.
- The main agent merges by reading the final diff and running the gates; no rebase, amend, force-push or branch deletion.

## 5. Process guardrails

1. Work only on `feat/1.8-selector-live-preview`. Do not commit to `main`.
2. Preserve `fix/1.7.0-review-fixes` at `eafdcb8`, including `745fc5f` and `eafdcb8`. Do not rebase, amend, delete or rewrite them.
3. Never stage or commit `.kilo/` or `docs/evidence/`. Evidence stays on disk and git-ignored.
4. No release, tag, push, main merge, or LSPosed store action. A release build is a build artifact only.
5. No secrets, tokens, keystores, vendor APKs or owner assets in the repository.
6. Every code change cites a requirement ID. No opportunistic refactor.
7. If a fix cannot be reconciled with `AGENTS.md`, stop and ask the owner.

## 6. Open decisions locked

The canonical list is `AGILE-1.8-REQUIREMENTS.md` §3. The implementation must not reinterpret these:

- Four tabs; "middle icon" means the selected icon centred in the selector.
- Hover #3C88F8; selected pure white ~24 dp with a small label below.
- Backdrop 1.0.1; measured highlight tokens; no custom AGSL; no 24 dp shadow; one live rim owner.
- Radial white `0.16 * press` glow, `1.5 * height`, 8 dp inset.
- 2 SIM battery arc follows 0–100; Classic is frozen.
- Invert center is `middle_slot=percent`; no new column; old percent-in-network migrates to it; no duplicate number; normal middle occupant moves above.
- Normal Position `0 = right edge`, range `-limit … 0`; `lockRing` centered mode is separate; RTL uses physical left/right.
- No GIF exists; Classic/2 SIM cards are fixed runtime Rive demos; event previews follow the selected design.
- Exactly one design option selected; `selectableGroup`; disabled state clear; no second selector.
- Full short-English copy pass; placeholders preserved; report/preview text in `strings.xml`.
- Keep prototype icons; structure for drop-in owner delivery; do not redraw.
- CPH2747 is the only measured device truth; other ROMs `unverified`.

## 7. Risks

| Risk | Mitigation |
|---|---|
| Backdrop 1.0.1 API differs from the measured names | A0 reads the exact symbol; no guessed calls; screenshot proof |
| Default 24 dp shadow remains or a second rim is drawn | Code grep for shadow/rim; one live path; fallback-only manual rim |
| Press glow costs frames | Press-only, no idle loop; profile on CPH2747 |
| Hover updates recompose the whole bar | Pure `hoverIndex`; only icon/label state changes; no per-frame writes |
| Selected 24 dp icon plus label overflows a slot | Measure at 360 dp; one-line label; four-tab layout stays |
| Battery line property exists in only one scene | Property-parity test; rebuild both `.riv`; hash compare |
| Invert center geometry guessed | A0/A2 measured centre/upper seats; no-duplicate-number test |
| `middle_slot=percent` migration surprises old users | Read old value as Invert center; document; no new column |
| UI/native position disagreement around `lockRing` | One predicate and one range in UI, prefs and native; RTL test |
| Deleting the simulator breaks references/strings | Compile + grep; delete controls and strings together |
| Copy rewrite breaks placeholders | Placeholder parity test; forbidden-term scan |
| Evidence or `.kilo/` accidentally staged | Process guard; `git status` before every commit |
| Device test claimed without the phone | Owner-run walkthrough; everything else `unverified` |

## 8. Gates

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline
./gradlew :app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline
python3 tools/route-module-logs.py --check
python3 tools/check-rive-eases.py
rive rive/duo --once
sha256sum app/src/main/res/raw/duo.riv app/src/main/res/raw/duo_old.riv
```

The fresh-build hashes must equal the checked-in hashes for **both** files. A5 runs the full test plan; device steps are owner-run.

## 9. Approval and exit

No 1.8 product code starts before owner approval of requirements, plan and test plan. The phase exits only when every requirement row has a verdict, the 449 baseline tests and the new tests pass, both Rive scenes satisfy the property contract, and the owner accepts the CPH2747 walkthrough. No release, no main merge, no push, no LSPosed store.


## FILE: AGILE-1.8-REQUIREMENTS.md

# Duo Status Bar — Agile 1.8 Requirements

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Agile 1.8 (selector effects, live preview, 1.8.0 review baseline) |
| Date | 2026-11-05 |
| Owner | Abdulkarim |
| Status | **Requirements draft for owner review. No 1.8 product code before this file, the plan and the test plan are approved.** |
| Evidence | Owner brief 2026-11-05; session exploration 2026-11-05; ColorDuo 1.1.7 APK (`io.github.fengwenhua.colorduo`, versionCode 10107); Kyant Backdrop 1.0.1 API; current code at `feat/1.8-selector-live-preview` (`eafdcb8`); `agent/AGILE-1.7-COLORDUO.md`; 449 unit tests on 1.7.0 |
| Baseline | 1.7.0 stable (versionCode 89, 449 unit tests), CPH2747 measured device; `fix/1.7.0-review-fixes` at `eafdcb8` is isolated and preserved |

This phase is additive. It does not reopen the 1.6 fatal fix, does not change the 1.7 transition model, and does not add a new settings schema. Anything that conflicts with `AGENTS.md` stops and returns to the owner.

---

## 1. Owner intent

Finish the bottom navigation as a liquid-glass selector with the measured ColorDuo/Backdrop light behaviour, make hover and selection legible with four tabs, make the 2 SIM battery line truly live, and make Invert center a real, persisted status-bar behaviour. Then remove the duplicate static preview and the whole layout simulator, keep one Live Preview Controls card, correct the right-anchored Position model, add honest runtime design demos, and rewrite the user-facing copy into short, plain English.

The 1.8.0 review must be able to prove every statement with a requirement ID, a test ID, and an evidence path. Device truth stays with the owner on CPH2747.

---

## 2. Requirements

### 2.1 Bottom selector liquid glass (GLASS3)

| ID | Requirement | Acceptance |
|---|---|---|
| GLASS3-01 | Use Kyant Backdrop 1.0.1's supported highlight/refraction surface for the live bottom selector. No app-authored AGSL. The live path uses the measured directional Highlight: width **0.75 dp**, blur **0.4 dp**, alpha **0.78 dark / 0.96 light**, intensity **0.5**, angle **45°**, falloff **1.0**, blend **Plus**. Disable Backdrop's unintended default **24 dp shadow**. The live path has exactly **one rim owner** (the Backdrop highlight), replacing Duo's manual double rim; the manual rim remains only on the non-live fallback. minSdk stays 33. | Code and measurements match the token table; no app-authored `RuntimeShader`/AGSL; no 24 dp shadow; one live rim draw path; fallback draws one manual rim; dark/light screenshots; Backdrop dependency is still 1.0.1. |
| GLASS3-02 | Replace the current vertical press reflection with ColorDuo's radial **white 0.16 × press** glow: radius **1.5 × selector height**, **8 dp inset**, only while pressed. | Code uses a radial brush centred on the selector; alpha = `0.16 * press`; radius and inset match; the old vertical-gradient press reflection is absent; still/video evidence. |

### 2.2 Selector hover and icons (NAV3)

| ID | Requirement | Acceptance |
|---|---|---|
| NAV3-01 | While the selector is dragged over a tab, that tab's icon and label turn blue **#3C88F8**. On release, the selected tab becomes the large **pure-white** icon (about **24 dp**) centred in the selector, with a small label below. The tab count stays **four**: Home, Custom, Report, Settings. | Hover index is pure logic and tested; hovered tab is #3C88F8; selected icon is white, about 24 dp, centred in the selector; label is below and one line; four tabs unchanged; screenshots. |
| NAV3-02 | Keep the current prototype vectors. Do not redraw owner art. Route all four icons through one replaceable icon source so the owner's later icons drop in without layout changes. | No vector path edits in this phase; one icon source/map; the selected/hover layout consumes a 24 dp icon slot independent of the vector; a test asset swap proves the layer is replaceable. |

### 2.3 2 SIM / 8-dot battery line (BATT3)

| ID | Requirement | Acceptance |
|---|---|---|
| BATT3-01 | The small bottom battery arc in the 2 SIM design must follow real battery level 0–100 in the real status-bar icon and in the remaining live preview. It must not stay full at 100 while only the number changes. Classic scene behaviour is unchanged. | At 0/19/49/80/100 the arc is monotonic and clamped; real icon and live preview agree; Classic renders exactly its 1.7 baseline; screenshots/measurements. |
| BATT3-02 | Fix the mapping once for the new Rive scene and for the Kotlin/Canvas/preview path. Both surfaces read the same level-derived value; no second source of truth. | Unit mapping table passes; Canvas fallback and preview show the same arc as Rive at the same level; Classic path unchanged. |

### 2.4 Invert center (INV3)

| ID | Requirement | Acceptance |
|---|---|---|
| INV3-01 | Apply the simulator's existing invert behaviour to the real status-bar icon in **both** designs: the percentage moves to the centre, the normal middle icon moves above at a smaller size, and there is no duplicate number. | Both designs show one percentage, centred; the middle occupant is above at the smaller size; real icon and live preview match; no duplicate number in any frame; screenshots. |
| INV3-02 | Persist the behaviour using the existing `middle_slot=percent` setting. Do not add a new settings column or schema migration. Expose a clear **Invert center** toggle in Live Preview Controls. Document the decision and the migration from the old broken percent-in-network behaviour. | Toggle writes and reads `middle_slot=percent`; an existing percent-in-network value becomes Invert center; no new provider column/version bump; toggle label/detail are clear; the decision and migration are recorded in the plan and copy. |

### 2.5 Live preview cleanup (HOME3)

| ID | Requirement | Acceptance |
|---|---|---|
| HOME3-01 | Remove the separate static **Live preview** card from Home. Remove the entire **Layout simulation** card and delete `ui/PreviewSimulator.kt`. Keep exactly one **Live Preview Controls** card with the interactive preview. | Home contains one preview card; no static preview card; no Layout simulation card; `PreviewSimulator.kt` is absent and unreferenced; controls update the preview; dead strings removed. |
| HOME3-02 | Move only controls that can truly drive the real element: battery level, charging, battery saver, Invert center, Wi-Fi/5G/4G/flashlight, and any other control the real model supports. Do not move per-dot or custom-distribution controls; delete them with the simulator because the real model has no independent left/right dots. | Control inventory matches the real model; per-dot/custom distribution controls and strings are gone; Invert center persists; the other simulation controls are explicitly preview-only; no fake independent-dot state remains. |

### 2.6 Position (POS3)

| ID | Requirement | Acceptance |
|---|---|---|
| POS3-01 | In normal mode (not camera/locked), Position **0 dp means the right edge**, not the centre. The editor preview is right-anchored and negative values move left. Camera/Compact centered mode remains a separate explicit mode and may use 0 = centre. Do not use 0 = centre for the normal field. Fix the UI/native mismatch around `lockRing` and document RTL. | Normal range is `-limit … 0`; 0 renders at the right edge; negative moves left; preview and native agree; `lockRing` switches both UI and native to centered `-limit … +limit`; RTL keeps the same physical rule; tests and screenshots. |

### 2.7 Design demos and media (DEMO3)

| ID | Requirement | Acceptance |
|---|---|---|
| DEMO3-01 | No literal design-option GIF exists in git history (verified in this phase's exploration). Do not claim deleted assets. Implement animated **runtime Rive** demos: the Classic design card always demonstrates Classic, the 2 SIM card always demonstrates 2 SIM, and Live Events previews follow the selected design (and split mode when supported). | Classic card plays the old scene regardless of selection; 2 SIM card plays the new scene regardless of selection; event previews use the selected design's raw resource; split mode uses the ring part; no GIF-deletion claim; previews animate and pause when idle/off-screen. |
| DEMO3-02 | The ring design selector must have exactly one selected option, a clear disabled state, `selectableGroup` semantics, and no second always-enabled design selector after the simulator is deleted. | Normalized invalid value selects exactly one; disabled options are visibly disabled and cannot be selected; `selectableGroup` is present; only one design selector remains on Home/Settings; tests. |

### 2.8 Copy (COPY3)

| ID | Requirement | Acceptance |
|---|---|---|
| COPY3-01 | Rewrite all user-facing UI copy in short, simple English: at most one rendered line for labels/headings, and no more than **1.5 short lines** for helper text (at most two lines, with the second line short). Avoid developer terms: System UI, LSPosed/scope, adb/stage, PHDP, JSON, QS/shade/keyguard, px, animation internals. Keep the meaning and every format placeholder. Move hardcoded report/preview text into `strings.xml`. This is a full pass, not only Home. | Copy audit covers Home, Custom, Settings, Report, preview, dialogs/toasts, update/backup; labels fit one line at 360 dp; helper text is no more than 1.5 short lines; forbidden-term grep is zero in user strings; placeholders are byte-identical; no hardcoded report/preview text remains. |

### 2.9 Process and quality (QA3)

| ID | Requirement | Acceptance |
|---|---|---|
| QA3-01 | Requirements IDs, acceptance criteria, phase IDs, test IDs and evidence paths form a closed 1:1 chain: every requirement has one phase and one primary test with one evidence path, and the reverse map has no orphans. | Traceability audit passes both directions; no requirement without a phase/test/evidence; no orphan test. |
| QA3-02 | Verification must include: battery 0/19/49/80/100 monotonic/clamped; invert mapping in both designs with no duplicate number; position 0 = right in preview and native, including RTL; nav hover index as pure logic; design selection exactly one/normalization; Rive property parity in both scenes; copy length/placeholder checks; the existing **449 unit tests**; a release build; the route-log and Rive ease/hash gates. Device verification is owner-run, CPH2747-only truth; other ROMs remain `unverified`. | All non-device gates pass; device-only checks are pass or `unverified` with the missing evidence; the 449 existing tests stay green; release build is produced but not published; owner-run device summary exists; no other ROM is claimed. |
| QA3-03 | Both `rive/duo/scene.rml` and `rive/duo/scene_old.rml` must declare every property `DuoBinder` writes. Rebuild both `app/src/main/res/raw/duo.riv` and `duo_old.riv` and hash-compare them, because CI checks only the new scene. | Property-name set equality for both scenes against the binder; `PROPERTY_COUNT` matches; both fresh `.riv` hashes equal the checked-in files; the ease gate covers both scenes. |
| QA3-04 | Preserve the isolated `fix/1.7.0-review-fixes` commits (`745fc5f`, `eafdcb8`) and the current `feat/1.8-selector-live-preview` branch. Never commit `.kilo/` or evidence. No release, no merge to `main`, no push, no LSPosed store. | Branch heads and required commit hashes unchanged; no `.kilo/` or `docs/evidence/` staged; no tag/release/push; no main merge; owner approval before any release action. |

---

## 3. Open decisions locked

These interpretations are final for 1.8 unless the owner explicitly reopens them. They exist because several phrases in the brief are ambiguous; locking them prevents incompatible implementations.

| # | Topic | Locked interpretation |
|---|---|---|
| 1 | "Middle icon" with four tabs | Four tabs have no single centre tab. "Middle icon" means the **selected icon centred inside the selector**, not a fifth/centre tab. The tab count stays four: Home, Custom, Report, Settings. |
| 2 | Hover and selected appearance | Hovered tab icon and label are **#3C88F8** during the drag. On release the selected tab becomes a **pure-white ~24 dp icon centred in the selector**, with a **small label below**; the selector remains the moving liquid capsule. |
| 3 | Glass library and effects | Use **Backdrop 1.0.1** only. Use the measured directional Highlight tokens (0.75 dp / 0.4 dp, 0.78 dark / 0.96 light, intensity 0.5, 45°, falloff 1.0, Plus). Do not author AGSL. Disable the unintended default **24 dp shadow**. minSdk stays **33**. |
| 4 | Rim ownership | The live path has **one rim owner** (Backdrop highlight); remove Duo's manual double rim from the live path. Keep **one manual rim** only for the non-live fallback so the selector still has an edge when the live path is unavailable. |
| 5 | Press reflection | Replace the vertical press gradient with ColorDuo's **radial white 0.16 × press** glow, radius **1.5 × selector height**, **8 dp inset**, press-only. |
| 6 | Battery line | The 2 SIM bottom arc follows **0–100** in the real icon and in the remaining live preview. Classic scene behaviour is frozen. One level-derived value feeds Rive and the Kotlin/Canvas/preview path. |
| 7 | Invert center persistence | Invert center is stored as **`middle_slot=percent`**. No new settings column and no schema version bump. The old percent-in-network meaning is **migrated** to Invert center, not preserved as a second mode. The old `networkText = level` render is removed; it was the duplicate. The existing Settings item for the same value is renamed **Invert center**; it is the same setting, not a second mode. |
| 8 | Invert center geometry | The percentage sits in the centre; the **normal middle occupant** (the one Auto would choose: airplane > DND > Wi-Fi > network) moves above at a smaller size. There is **no duplicate number**, and no second icon-choice setting is added. The Live Preview Controls may simulate Wi-Fi/5G/4G/flashlight, but only the invert state persists. |
| 9 | Position origin | Normal mode: **0 dp = physical right edge**, range `-limit … 0`; negative moves left. Camera/Compact centered mode is a separate mode using `-limit … +limit` with 0 = centre. RTL keeps the same physical rule: 0 is the right edge, negative is left; do not reinterpret the number as start/end. |
| 10 | `lockRing` ownership | `lockRing` is the centered/camera mode. The UI, preference clamp and native placement must all use the **same predicate** and the same range; no UI showing a normal field while native centres, or the reverse. |
| 11 | Design demos | There was **no literal design-option GIF** in git history; no deleted asset is claimed. Use **runtime Rive demos**. Classic and 2 SIM cards are **fixed demos**; Live Events previews follow the selected design and split mode when supported. |
| 12 | Design selector | Exactly **one** selected option at all times. Invalid values normalize to one option. Disabled options are visibly disabled and cannot be selected. Use `selectableGroup` semantics. After `PreviewSimulator.kt` is deleted there must be **no second always-enabled design selector**. |
| 13 | Copy | "1 line or 1.5 short lines" means: labels/headings fit one line at 360 dp; helper text is no more than 1.5 short lines (at most two lines, second line short); no ellipsis at the target width. Developer terms are forbidden in user-facing strings. Placeholders are preserved exactly. Report/preview hardcoded text moves to `strings.xml`. |
| 14 | Icons | Current vectors are prototypes. Do not redraw them and do not use icon replacement as a 1.8.0 acceptance item. Structure the icon layer for a later drop-in owner delivery. |
| 15 | Evidence and process | `docs/evidence/` is git-ignored and never committed. `.kilo/` is never committed. Preserve `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`. No release, no main merge, no push, no LSPosed store. |
| 16 | Device truth | The owner runs device verification. CPH2747 is the only measured truth for this phase. Every other ROM/device is `unverified` until it has its own evidence. |

---

## 4. Traceability contract

This table is the 1:1 chain. The plan uses the phase ID; the test plan uses the test ID; evidence lives under the named path. Every test covers exactly one requirement.

| Requirement | Phase | Primary test | Evidence path |
|---|---|---|---|
| GLASS3-01 | A1 | T-GLASS3-01 | `docs/evidence/agile-1.8/glass/highlight-shadow.txt` |
| GLASS3-02 | A1 | T-GLASS3-02 | `docs/evidence/agile-1.8/glass/press-glow.txt` |
| NAV3-01 | A1 | T-NAV3-01 | `docs/evidence/agile-1.8/nav/hover-selected.txt` |
| NAV3-02 | A1 | T-NAV3-02 | `docs/evidence/agile-1.8/nav/icon-layer.txt` |
| BATT3-01 | A2 | T-BATT3-01 | `docs/evidence/agile-1.8/battery/levels.txt` |
| BATT3-02 | A2 | T-BATT3-02 | `docs/evidence/agile-1.8/battery/parity.txt` |
| INV3-01 | A2 | T-INV3-01 | `docs/evidence/agile-1.8/invert/designs.txt` |
| INV3-02 | A3 | T-INV3-02 | `docs/evidence/agile-1.8/invert/persistence.txt` |
| HOME3-01 | A3 | T-HOME3-01 | `docs/evidence/agile-1.8/home/cleanup.txt` |
| HOME3-02 | A3 | T-HOME3-02 | `docs/evidence/agile-1.8/home/controls.txt` |
| POS3-01 | A3 | T-POS3-01 | `docs/evidence/agile-1.8/position/right-anchor.txt` |
| DEMO3-01 | A4 | T-DEMO3-01 | `docs/evidence/agile-1.8/demos/design-events.txt` |
| DEMO3-02 | A4 | T-DEMO3-02 | `docs/evidence/agile-1.8/demos/selector-state.txt` |
| COPY3-01 | A4 | T-COPY3-01 | `docs/evidence/agile-1.8/copy/audit.txt` |
| QA3-01 | A0 | T-QA3-01 | `docs/evidence/agile-1.8/gates/traceability.txt` |
| QA3-02 | A5 | T-QA3-02 | `docs/evidence/agile-1.8/gates/suite.txt` (contains the CPH2747 summary link) |
| QA3-03 | A5 | T-QA3-03 | `docs/evidence/agile-1.8/rive/parity.txt` |
| QA3-04 | A0 | T-QA3-04 | `docs/evidence/agile-1.8/gates/process.txt` |

---

## 5. Out of scope

- Changing the tab count (four tabs stay) or adding a centre tab.
- A new settings column, schema, or backup format version. The `middle_slot=percent` behaviour migration is allowed and required; it is not a schema change.
- Per-dot independent left/right masks or custom distribution in the real model; those controls are deleted with the simulator.
- SystemUI glass, `RenderEffect`, AGSL, or applying Backdrop inside the status bar. Backdrop stays app-side only.
- Vendor assets, copied ColorDuo assets, owner icon redraws, or new icon packs.
- Reopening the 1.6 fatal fix, the 1.7 transition fix, per-icon hiding, or other 1.7 surfaces.
- Unrelated refactors, new hook frameworks, new architecture, or a whole-app performance re-audit.
- Literal GIF recreation, deleted-asset claims, or marketing copy.
- Agent-run device testing, release, merge to `main`, push, tags, or LSPosed store publication.

---

## 6. Approval

No 1.8 product code starts before the owner approves these requirements, `AGILE-1.8-PLAN.md` and `AGILE-1.8-TEST-PLAN.md`. The 1.7.0 stable build remains the rollback baseline. Only documentation was produced in this step; no app code, Rive file, string resource, or git history was changed.


## FILE: AGILE-1.8-TEST-PLAN.md

# Duo Status Bar — Agile 1.8 Test Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.8 |
| Date | 2026-11-05 |
| Status | Draft; mirrors `AGILE-1.8-REQUIREMENTS.md` |
| Baseline | 1.7.0 stable (versionCode 89), 449 unit tests, CPH2747 |
| Branch | `feat/1.8-selector-live-preview` |
| Pass rule | PASS only with the named evidence and exact acceptance value; otherwise FAIL or `unverified` |

## 1. Gates

| Gate | Evidence |
|---|---|
| Requirements audit | Every requirement has one phase and one primary test here and in `AGILE-1.8-PLAN.md`; every test maps back to one requirement |
| Compile/unit | `:app:testDebugUnitTest :app:assembleDebug --offline`; the existing **449 tests** stay green and the new tests pass |
| Release build | `:app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline`; build artifact only, no publication |
| Route logs | `python3 tools/route-module-logs.py --check` |
| Rive ease | `python3 tools/check-rive-eases.py` covers both scenes, or the script is extended to do so |
| Rive hashes | fresh `rive rive/duo --once` output matches **both** `duo.riv` and `duo_old.riv` by SHA-256 |
| Property parity | both `.rml` property sets equal the `DuoBinder` write set; `PROPERTY_COUNT` matches |
| Device | Owner-run CPH2747 summary; all other ROMs `unverified` |
| Process | branch/commit hashes preserved; no `.kilo/` or evidence committed; no release/push/main merge |

## 2. Cases

Each case covers exactly one requirement. Sub-cases inside a case are evidence steps, not separate requirements.

### Glass and navigation

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-GLASS3-01 | GLASS3-01 | Read the selector implementation. Confirm Backdrop 1.0.1 is the only glass library, the highlight API is called with 0.75 dp width, 0.4 dp blur, 0.78/0.96 alpha, intensity 0.5, 45°, falloff 1.0 and Plus. Confirm the default 24 dp shadow is disabled. Grep for `RuntimeShader`/AGSL in the selector. Count the rim draw paths on the live path and on the fallback. Capture dark and light screenshots. | Token table matches exactly; no app-authored AGSL; no 24 dp shadow; one live rim owner; one manual rim only on the fallback. | `docs/evidence/agile-1.8/glass/highlight-shadow.txt` |
| T-GLASS3-02 | GLASS3-02 | Trigger press from 0 to 1 and inspect the reflection. Confirm a radial white brush with alpha `0.16 * press`, radius `1.5 * selector height`, 8 dp inset, press-only. Confirm the vertical press gradient is gone. Capture still/video. | Radial glow matches all values; no vertical press reflection; no glow when press = 0. | `docs/evidence/agile-1.8/glass/press-glow.txt` |
| T-NAV3-01 | NAV3-01 | Unit-test the pure hover index at slot boundaries and all four slots. Drag across Home → Custom → Report → Settings; capture hover colour. Release on each tab; capture selected icon size/colour and label position. Count tabs. | Hover index maps to the correct tab and clamps; hovered icon/label is `#3C88F8`; selected icon is pure white, about 24 dp, centred in the selector; label is below and one line; four tabs remain. | `docs/evidence/agile-1.8/nav/hover-selected.txt` |
| T-NAV3-02 | NAV3-02 | Inspect the icon layer and vector diff. Confirm no prototype vector paths changed. Confirm one icon source/map feeds all four tabs. Swap a test-only vector through the source and confirm the layout still uses the 24 dp slot. | No owner art redrawn; one source/map; test swap preserves layout and alignment. | `docs/evidence/agile-1.8/nav/icon-layer.txt` |

### Battery and invert

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-BATT3-01 | BATT3-01 | Drive the 2 SIM design at 0, 19, 49, 80 and 100. Measure the bottom arc in the real status-bar icon and in the remaining live preview. Repeat with out-of-range inputs. Render the Classic scene at the same levels. | Real and preview arcs are monotonic and clamped 0–100; 0 is empty and 100 is full; Classic matches the 1.7 baseline; no static full arc while the number changes. | `docs/evidence/agile-1.8/battery/levels.txt` |
| T-BATT3-02 | BATT3-02 | Compare the Kotlin level mapping, Rive battery-line property, Canvas fallback and preview at the same levels. Confirm one derived value feeds all surfaces. Confirm no duplicate battery geometry. | All surfaces agree at every level; one mapping source; Classic unchanged; no second battery line. | `docs/evidence/agile-1.8/battery/parity.txt` |
| T-INV3-01 | INV3-01 | Toggle Invert center in Classic and 2 SIM. Capture the real icon and the live preview. Confirm the percentage is centred, the normal middle occupant is above at the smaller size, and exactly one percentage is visible. Step through charging/saver states. | Both designs match the simulator behaviour; one number; centre number; upper smaller icon; no duplicate or stale number. | `docs/evidence/agile-1.8/invert/designs.txt` |

### Home, invert persistence, position

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-INV3-02 | INV3-02 | Toggle Invert center from Live Preview Controls. Read `middle_slot` from settings; confirm `percent`. Restart the app/SystemUI; confirm it persists. Seed an old percent-in-network value and confirm migration to Invert center. Inspect the provider schema and backup format for a new column/version. | Writes/reads `middle_slot=percent`; persists; old value migrates; no new column/schema version; toggle copy is clear. | `docs/evidence/agile-1.8/invert/persistence.txt` |
| T-HOME3-01 | HOME3-01 | Open Home. Confirm one Live Preview Controls card, no static **Live preview** card and no **Layout simulation** card. Confirm `ui/PreviewSimulator.kt` is absent and unreferenced. Search for dead card/string references. Exercise the remaining preview. | Exactly one preview card; static and layout cards gone; file gone and unreferenced; preview updates; no dead strings. | `docs/evidence/agile-1.8/home/cleanup.txt` |
| T-HOME3-02 | HOME3-02 | Inventory every control in Live Preview Controls. Confirm battery, charging, battery saver, Invert center, Wi-Fi/5G/4G/flashlight and other real-model controls are present. Confirm per-dot/custom-distribution controls are absent. Toggle each; confirm Invert persists and the rest are preview-only. | Inventory matches the real model; no per-dot/custom controls or strings; Invert persists; other controls are clearly preview-only. | `docs/evidence/agile-1.8/home/controls.txt` |
| T-POS3-01 | POS3-01 | Unit-test the right-anchored preview mapping: offset 0 → right edge, `-limit` → left edge, positive clamps to right edge. Repeat in centered mode: 0 → centre, ±limit → both edges. Verify native `place` 0 = right edge and native `placeLocked` 0 = centre. Toggle `lockRing` and confirm UI, prefs and native all switch mode together. Repeat in RTL. Drag 0 → `-limit` → 0 and release mid-range. | Normal preview/native agree (0 = right, negative = left, positive clamps); centered mode is explicit for `lockRing`; no UI/native mismatch; RTL keeps the physical rule; pointer stays attached; no disappearance. | `docs/evidence/agile-1.8/position/right-anchor.txt` |

### Demos and copy

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-DEMO3-01 | DEMO3-01 | Select Classic, then 2 SIM. Confirm the Classic card always plays the old scene and the 2 SIM card always plays the new scene. Confirm both are animated runtime Rive, not GIFs/static. Select each design and exercise the four event previews; confirm they use the selected design and the ring part in split mode. Confirm no deleted-GIF claim exists. | Fixed card demos are correct and animated; event previews follow the selected design; split mode uses the ring part; no GIF/deleted-asset claim. | `docs/evidence/agile-1.8/demos/design-events.txt` |
| T-DEMO3-02 | DEMO3-02 | Unit-test design normalization for valid, invalid and empty values. Confirm exactly one option is selected. Toggle module-disabled state and confirm the option is visibly disabled and cannot be selected. Inspect semantics for `selectableGroup`. Search Home and Settings for design selectors. | Exactly one selected; invalid normalizes; disabled state clear and blocked; `selectableGroup` present; only one design selector remains. | `docs/evidence/agile-1.8/demos/selector-state.txt` |
| T-COPY3-01 | COPY3-01 | Scan every user-facing string and hardcoded UI text. Check label/heading one-line fit at 360 dp and helper text no more than 1.5 short lines (at most two lines, second short). Grep for forbidden terms: System UI, LSPosed/scope, adb/stage, PHDP, JSON, QS/shade/keyguard, px, animation internals. Compare every format placeholder before/after. Confirm report/preview hardcoded text is in `strings.xml`. | Limits pass; forbidden-term count is zero in user strings; placeholders are byte-identical; no hardcoded report/preview text; full screen coverage. | `docs/evidence/agile-1.8/copy/audit.txt` |

### Process and gates

| ID | Requirement | Steps | Pass | Evidence |
|---|---|---|---|---|
| T-QA3-01 | QA3-01 | Read `AGILE-1.8-REQUIREMENTS.md` §4, `AGILE-1.8-PLAN.md` §3 and this file. For each requirement confirm one phase, one test and one evidence path. Reverse every test to exactly one requirement. | 1:1 both directions; no orphans; no duplicated ownership. | `docs/evidence/agile-1.8/gates/traceability.txt` |
| T-QA3-02 | QA3-02 | Aggregate gate: confirm every case in §2 has a verdict, including T-BATT3-01, T-INV3-01, T-POS3-01, T-NAV3-01, T-DEMO3-02, T-QA3-03 and T-COPY3-01. Run the existing 449 tests plus the new tests. Build debug and release. Run the route-log gate, Rive ease gate and both hash checks. Collect the owner-run CPH2747 summary. Mark other ROMs `unverified`. | Every case has a verdict; the named cases pass; 449 baseline tests stay green; release build produced but not published; route/ease/hash gates clean; owner summary present; other ROMs `unverified`. | `docs/evidence/agile-1.8/gates/suite.txt` (contains the CPH2747 summary link) |
| T-QA3-03 | QA3-03 | Parse the `ViewModelProperty` names from `scene.rml` and `scene_old.rml`; parse every property name `DuoBinder.write` uses. Compare set equality for both. Confirm `PROPERTY_COUNT`. Run `rive rive/duo --once`; hash both `duo.riv` and `duo_old.riv` against the checked-in files. Confirm the ease gate covers both scenes. | Both scene sets equal the binder set; count matches; both hashes equal; ease gate covers both. | `docs/evidence/agile-1.8/rive/parity.txt` |
| T-QA3-04 | QA3-04 | Record `git rev-parse` for `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`; confirm `745fc5f` and `eafdcb8` are present. Check `git status` for `.kilo/` and `docs/evidence/`. Check there is no release/tag/push/main merge. | Branch/commits preserved; no `.kilo/`/evidence staged; no release/tag/push/main merge/store. | `docs/evidence/agile-1.8/gates/process.txt` |

## 3. Required sub-checks

The following checks are mandatory inside the named case; they cannot be replaced by a code read alone.

| Check | Case |
|---|---|
| Battery 0/19/49/80/100 monotonic and clamped | T-BATT3-01 |
| Invert mapping in both designs, no duplicate number | T-INV3-01 |
| Position 0 = right in preview and native, including RTL | T-POS3-01 |
| Nav hover index pure logic | T-NAV3-01 |
| Design selection exactly one and normalization | T-DEMO3-02 |
| Rive property parity in both scenes | T-QA3-03 |
| Copy length and placeholder checks | T-COPY3-01 |
| Existing 449 unit tests | T-QA3-02 |
| Release build | T-QA3-02 |
| Route-log gate | T-QA3-02 |
| Rive ease and both-file hash gates | T-QA3-02, T-QA3-03 |

## 4. Evidence layout

- `docs/evidence/agile-1.8/glass/` — highlight/shadow, press glow.
- `docs/evidence/agile-1.8/nav/` — hover/selected, icon layer.
- `docs/evidence/agile-1.8/battery/` — level table, parity.
- `docs/evidence/agile-1.8/invert/` — both designs, persistence.
- `docs/evidence/agile-1.8/home/` — cleanup, controls.
- `docs/evidence/agile-1.8/position/` — right anchor, RTL, lockRing.
- `docs/evidence/agile-1.8/demos/` — design/event demos, selector state.
- `docs/evidence/agile-1.8/copy/` — audit.
- `docs/evidence/agile-1.8/rive/` — property parity, hashes.
- `docs/evidence/agile-1.8/gates/` — traceability, suite, process.
- `docs/evidence/agile-1.8/device/` — owner-run CPH2747 summary.

`docs/evidence/` is git-ignored. Never commit it. Never commit `.kilo/`.

## 5. Traceability

| Requirement | Phase | Primary test |
|---|---|---|
| GLASS3-01 | A1 | T-GLASS3-01 |
| GLASS3-02 | A1 | T-GLASS3-02 |
| NAV3-01 | A1 | T-NAV3-01 |
| NAV3-02 | A1 | T-NAV3-02 |
| BATT3-01 | A2 | T-BATT3-01 |
| BATT3-02 | A2 | T-BATT3-02 |
| INV3-01 | A2 | T-INV3-01 |
| INV3-02 | A3 | T-INV3-02 |
| HOME3-01 | A3 | T-HOME3-01 |
| HOME3-02 | A3 | T-HOME3-02 |
| POS3-01 | A3 | T-POS3-01 |
| DEMO3-01 | A4 | T-DEMO3-01 |
| DEMO3-02 | A4 | T-DEMO3-02 |
| COPY3-01 | A4 | T-COPY3-01 |
| QA3-01 | A0 | T-QA3-01 |
| QA3-02 | A5 | T-QA3-02 |
| QA3-03 | A5 | T-QA3-03 |
| QA3-04 | A0 | T-QA3-04 |

## 6. Pass/fail rules

- PASS only with the named evidence and the exact acceptance value.
- FAIL when evidence contradicts the requirement.
- `unverified` when the device, recording, build or measurement is missing; do not claim it.
- Device verification is owner-run and CPH2747-only truth. Other ROMs stay `unverified`.
- A visual change without a requirement ID is a regression, not a pass.

## 7. Exit

- All 18 requirements have a verdict with evidence.
- Existing 449 tests and the new tests pass; debug and release builds succeed.
- Both Rive scenes satisfy the property contract; both `.riv` hashes match.
- Owner accepts the CPH2747 walkthrough.
- No release, no main merge, no push, no LSPosed store; branch and review-fix commits preserved.


## FILE: APP-TEST-PLAN.md

# Full app test plan — beta.40

Date: 2026-10-31. Node: API 36 emulator + CPH2747 logs. This plan covers the whole app, not just the
lock event.

## Scope

1. **Shell/navigation:** Home → Engine → Settings → back, three-page state, no crash.
2. **Liquid glass:** selector circle at rest, capsule on drag, snap back; pill blur; switches/sliders;
   scroll edges; light-mode readability.
3. **Settings:** every section reachable, toggles change persisted state, sliders move, event toggles
   (Screenshot, Calls, Lock, Alarm) respond.
4. **Simulation:** Alarm, Flashlight, Screenshot cards; no stuck event.
5. **Events:** screenshot file source, keyguard→home, incoming call, alarm, flashlight.
6. **Resilience:** rapid nav, repeated toggles, 50-event stress, process alive, crash buffer empty.
7. **Observability:** `DuoSB` logs, pid, `dumpsys gfxinfo`, screenshots for each screen.

## Cases

| ID | Case | Method | Pass |
|---|---|---|---|
| APP-01 | Home renders | launch, screenshot | bottom bar + Home content visible |
| APP-02 | Engine tab | tap Engine, screenshot | Engine content, selector moves |
| APP-03 | Settings tab | tap Settings, screenshot | sections visible |
| APP-04 | Selector | drag between tabs | capsule during drag, circle at rest |
| APP-05 | Toggle | tap a setting switch | value changes, no crash |
| APP-06 | Slider | drag a slider | value changes, no crash |
| APP-07 | Scroll | 5 swipes | content moves, edge glass, no ANR |
| APP-08 | Calls toggle | tap Calls | persisted value flips |
| APP-09 | Simulation | Alarm/Flashlight/Screenshot | preview changes, returns |
| APP-10 | Screenshot | KEYCODE_SYSRQ or file watcher | `live event -> SCREENSHOT` |
| APP-11 | Lock→home | PIN unlock | `live event -> UNLOCK` |
| APP-12 | Call | `emu gsm call` | `live event -> CALL` |
| APP-13 | Stress | 50 rapid nav/toggle/swipe | pid alive, crash empty |
| APP-14 | What-if | call during unlock; screenshot during call | newest event wins, no crash |

## Evidence

- Screenshots: `docs/evidence/full-app/<case>.png`
- Logs: `docs/evidence/full-app/logcat.txt`
- Report: `agent/FULL-APP-TEST-REPORT.md`

## Out of scope

- Stable release approval.
- Seven vendor ROM runtime (only the emulator + CPH2747 logs).
- Visual pixel match for every icon.


## FILE: BRAINSTORM-2026-11-02.md

# Brainstorm — Duo 1.6 Customization, Ring Decomposition, Downloads, Stability

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Brainstorm / discovery for the 2026-11-02 revision |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Open. Five owner answers are required before Requirements/Test Plan/final Plan.** |
| Inputs | Owner brief 2026-11-02; `agent/LIST-THE-PLAN.md`; current source; PHDP v2.11.0; existing device logs |
| Output after answers | Requirements additions → Test Plan additions → phased Plan with weapons → approval → code |

The owner explicitly forbids guessing. This brainstorm separates **evidence**, **decisions the owner must make**, **options with tradeoffs**, and **the draft phase shape**. It is not a commitment to any implementation.

---

## 1. Objective

Deliver a coherent 1.6-era update that:

1. Fixes the SystemUI FATAL/safe-mode risk first.
2. Rebuilds the app navigation/pill so the selector is transparent, the icon changes state, and the selector can be dragged from anywhere on the pill.
3. Reorganizes customization into a real Customization page with a live, on-change preview and persistent Default/Compact/Custom presets.
4. Decomposes the ring into independently positioned Rive objects: centre occupant, charge percentage, split battery + SIM pair, clock.
5. Corrects download-vs-charging behaviour and integrates PHDP honestly.
6. Replaces the simulation/data sections with generated GIF previews and removes unfinished surfaces.
7. Preserves parity across all vendor UIs and the Canvas fallback.

---

## 2. Evidence that constrains the design

### 2.1 App shell

- Current pages: Home, Graphics, Report, Settings (`DuoShell.kt:112`).
- Graphics is only the engine switch; Home has a continuously looping preview and Classic/Compact/Bold presets; Settings has all customization.
- The selector is a wine glass capsule with its own drag handler; the tabs are separately selectable.
- Preset choice is transient (`rememberSaveable`), not persisted.
- Live preview loops every 5.2s instead of responding only to changes.

### 2.2 Ring / Rive

- One 120×136 artboard; ring 103 diameter, stroke 8; no constraints/rig.
- Wi-Fi and live events are separate sibling groups at different seats.
- Charge % is a Text inside `ringGroup` at the top gap.
- Four cellular dots are fixed seats; `splitIndicators` is a **view split**, not a battery split.
- Camera ring is not implemented; `lockRing` only centres the element.
- Clock is `HH:mm`, shared with the network label, and too wide for its 56-unit box.
- Canvas is not a matching clone: ring scale, DND/bolt seats, event sizes, unlock/call art, and event motion differ.
- The `.riv` hash in docs is stale; the easing check currently reports 6 inert keyframes.

### 2.3 Download / PHDP

- Duo already receives PHDP’s private same-UID broadcast.
- PHDP v2.11.0 is the latest stable; no public test API; test uses the real publisher.
- Duo app cannot send the broadcast because the receiver is non-exported and the app has a different UID.
- PHDP’s default `showLabel=false`; the owner wants a number.
- Current charging precedence hides download digits.
- Only the ring has the download tap override; the split indicator view does not.
- No Download Progress UI exists.

### 2.4 Stability

- The owner’s current FATAL bundle is missing; the only owner crash capture is 2026-10-28.
- That capture shows QS/shade surface churn, a counted SystemUI crash 4.5s after collapse, and a separate LuckyTool/DexKit SIGABRT 15 minutes later.
- No rotation line in the capture. The current rotation and camera crashes are not yet classified.
- Live Rive resize and live reparent are still in HEAD; the `fix/landscape-safe-mode` guard is unmerged and untested.
- Any cosmetic change before classification would be reckless.

### 2.5 Images (now retrieved)

The six files are in `/storage/emulated/0/kilo images`, pulled to `/tmp/duo-kilo-images/`, and archived at `reverse/kilo-images/` (gitignored).

- They are **icon and selector references plus one current-preview crop**, not full app screens.
- Home reference: filled navy house icon.
- Customization reference: white three-row sliders icon.
- Report reference: two-document + trend-arrow outline icon (Icons8 stock asset; licensing must be checked before packaging).
- `new duo customisition icon.jpg` is the **New Duo Customization** target: number above, Wi-Fi centre, **eight SIM dots (four per side)**, and a colored battery line at the bottom. It is not the old rejected screen.
- Selector resting: 225×179 px inside a 207 px-high pill; held: 296×231 px, overflows; ratio ≈1.30×; active Home icon also grows ≈1.25–1.41×.
- Owner answer: final tabs are **Home / Customization / Report**; Settings opens from a gear on Home. The mockup's Home/Components/About labels are not final.
- The images do not define the camera cutout, the split-transition frames, full page layouts, or status-bar-scale placements.

---

## 3. Options and proposed direction

### 3.1 Navigation and selector

**Goal:** transparent selector, icon under it becomes active, long-press anywhere starts a drag, selector jumps to the finger, and the selector size matches the reference.

Options:

A. **Overlay selector with gesture ownership on the whole pill** (recommended)
- Keep the selector as a separate overlay, but remove its wine fill; use only a soft glass lens/outline.
- Move `pointerInput` from the selector box to the pill container. On long-press (or immediate drag after a threshold), capture the pointer, set selector centre to the finger, and follow.
- Tabs remain `selectable` for accessibility and tap navigation; drag is a separate gesture that suppresses the click.
- Active icon uses a tinted/duotone state; inactive icons use a muted foreground.
- At rest, selector is a transparent glass ring/capsule; when held, it expands to the reference size.
- Use `awaitEachGesture`/`detectDragGesturesAfterLongPress` and `change.consume()` to avoid page scroll conflicts.
- On drag end, snap to nearest station and select that page.

B. **Make the selector itself the only drag handle**
- Simpler, but contradicts “hold the pill anywhere”.

C. **Use a single custom nav bar that draws icons and selector together**
- More control, but a larger rewrite and risk of losing accessibility.

**Recommendation:** A. The existing selector can stay; its wine fill is removed, it becomes a large translucent ellipse around the active item, and its gesture is promoted to the parent. Measured targets: rest 225×179 px inside a 207 px pill; held 296×231 px; effective scale ≈1.30×. Translate to dp at run time, not hard-coded pixels.

**Resolved by images:** the selector is an ellipse/capsule around the active item, not a narrow capsule around the text; the active icon/label grow with it. The held frame is mid-motion, so the settled held size still needs owner confirmation.

### 3.2 App information architecture

The owner’s references show Home, Customization, Report. Current app has Home, Graphics, Report, Settings. Possible structures:

A. **Three top-level pages + Settings from a gear/overlay** (recommended if the references show three nav items)
- Home: status + live preview + preset selector.
- Customization: the full ring/page/engine/preview surface.
- Report: report form + contact developer.
- Settings: module health, diagnostics, backup, updates; reached from Home header or a gear, not the bottom pill.

B. **Four bottom items: Home, Customization, Report, Settings**
- Closest to current code; only rename Graphics→Customization and move sections.
- Keeps one-tap access to diagnostics/backup.

C. **Three items exactly; move backup/update/health into Report or Home**
- Cleanest visual, but diagnostics/backup are not “report” content and could be hard to find.

**Recommendation:** Decide from the Home/Customization/Report images. Do not delete Settings functionality before deciding; move it, do not throw it away. “About/Report in Settings is not needed” means the duplicate report block, not module health/backup/update.

### 3.3 Preset model

Current: three transforms, no persisted selection.

Proposed model:

```
DuoPresetState {
  selected: DEFAULT | COMPACT | CUSTOM
  lastCustom: DuoSettings
  default: DuoSettings       // fixed snapshot
  compact: DuoSettings       // fixed snapshot
}
```

Rules:

- Applying Default or Compact saves the *current* settings to `lastCustom` only if the user was in Custom and had unsaved custom edits? Safer: always keep the last explicit Custom snapshot, updated on any manual edit while Custom is selected.
- Entering Custom restores `lastCustom`.
- Manual edit in Default/Compact switches selection to Custom and writes the edited settings to `lastCustom`.
- Applying a preset must not destroy the other preset’s stored values.
- Old Classic/Compact/Bold migration: map old `classic`→Default, old `compact`→Compact? But the new Compact is a camera ring, not the old split layout. This is a breaking semantic change; require explicit owner decision. Old Bold has no successor.
- Persist `selectedPreset` in the settings store, not `rememberSaveable`.
- Backup must include the preset state and version it.

**Risk:** a camera-ring Compact cannot be represented by the current `DuoSettings` fields; a new `ringPlacement`/camera mode field is required, with an explicit single-surface rule.

### 3.4 Sliders and position editor

Current: tap/drag anywhere in the 48dp slider; position drags anywhere in the 40dp editor.

Proposed:

- Keep the visual track; attach tap/drag only to the 28–32dp thumb, with a 48dp hit target (invisible padding).
- Track taps do nothing except accessibility `setProgress`; vertical scroll over the track must not be consumed.
- Position editor: only the icon/knob starts the drag. Tapping the rail left/right can remain an optional accessibility action, but no drag from the rail.
- Test with a vertical swipe over each slider at several scroll positions; verify the settings page scrolls, not the value.

**Unknown:** Should tapping the track still jump the value for accessibility? The owner says “only the circle”; recommendation is no pointer-track control, but keep TalkBack `setProgress`.

### 3.5 Ring as independent objects

The current Rive artboard is flat. The requested object model is best implemented with **parent slot Nodes** and **rail rotations**, not duplicated art:

```text
duoGroup
├── percentSlot (Node at ring centre)
│   └── percent Text
├── ringGroup
│   ├── download wash
│   ├── batteryHalfL / batteryHalfR (separate trimmed arcs)
│   └── bolt
├── centerSlot (Node)
│   ├── eventGroup
│   └── middleFadeGroup (Wi-Fi, airplane, DND, network/clock)
└── simGroup
    ├── simRail1..4 (rotation angle)
    └── simDot1..4 at (0,-R)
```

Principles:

- `centerSlot` owns the transform; Wi-Fi and event art are children, so moving the slot moves both.
- `percentSlot` owns the number; it has two seats (above/centre) and can cross-fade/move as one object.
- Battery halves are separate arc shapes; a single trimmed ellipse cannot move independently.
- SIM dots use rail rotations; `x=R·sinθ`, `y=-R·cosθ`, or a child at `(0,-R)` with parent rotation θ. Exact, cheap, no unverified follow-path constraint.
- Keep live data in Kotlin; Rive owns art, opacity, trim, and short transitions.

**Hard constraints found:**

- The Wi-Fi glyph at authored size does not fit the top-gap percentage seat. It needs a scaled/alternate top-gap pose.
- `splitIndicators` puts the middle icons in a separate view. A Wi-Fi↔percentage swap inside the ring cannot work in split mode. Recommendation: force `splitIndicators=false` for the charge-centre and camera presets, or hide/disable the option with an explanation.
- Two battery halves (171.3°) do not fit the 117.4° bottom gap. The “battery moves to the old four-dot position” phrase needs the image and an explicit answer.
- Charging bolt currently owns the top gap. If charge % is centre and Wi-Fi is top, the bolt collides; a priority rule is required.

### 3.6 Camera ring / Compact

What is proven:

- No camera cutout geometry in the repo.
- `lockRing` centres the ring on the status bar; it does not detect a camera.
- No “Resize” action.
- Moving/resizing the live Rive view is the C2 risk.

Proposed safe shape:

- Camera mode only on the main status bar. Keyguard/QS/shade use the Default placement (owner explicitly asked for normal QS/shade placement).
- Use a **single Rive view** with a fixed outer size; move the artboard/ring inside it via bound translation. Do not resize/reparent the view live.
- The camera cutout must be measured per device/build; do not guess a centre/radius.
- “Resize” should be a tap affordance that opens a size control, not a live resize of the TextureView. The exact wording/timeout/placement is unknown.
- If the camera ring cannot be safe, it must be disabled with a logged reason rather than risk SystemUI.

### 3.7 Clock

- Use `DateFormat.getTimeFormat(context)` or `DateFormat.is24HourFormat(context)` to honour system 12/24h, locale, and AM/PM.
- Watch `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `ACTION_LOCALE_CHANGED`, and configuration changes.
- Add a dedicated clock Text object; do not reuse the network `5G/4G` node.
- Set `noWrap`, auto-width or fit-font, origin centre, and a max width that leaves ≥4 units to the inner ring.
- If AM/PM makes the string too wide, use a smaller font or a compact format; decide from the image.

### 3.8 Download / PHDP

Two separate problems:

1. **Charging precedence** (fixable in Duo): when a download is active, ignore the charging visual, show the download number/bar, and let a ring/icon tap return to charging. At completion (`active=false`, `progress=0`), return to normal; at 100% while plugged, return to normal charging or non-charging according to the final decision.
2. **Simulation** (blocked by PHDP API): PHDP has no public trigger and its test uses the real publisher. A Duo button cannot start PHDP’s test. Options:
   - A. Simulate only in the app preview; “Test in status bar” opens PHDP so the user presses its test. (honest, recommended)
   - B. Add a Duo-owned test injection to its own module via a guarded one-shot provider/broadcast, independent of PHDP. This expands attack surface and may violate “real simulation requires PHDP”.
   - C. Ask PHDP upstream for a `simulated` flag and a public test action; ship after it lands.

- Number visibility: honour PHDP `showLabel` (default false) or add a Duo “Show download percentage” switch. Recommendation: Duo switch default ON, with a note that it overrides PHDP’s label preference.
- Toggle-off warning: turning Duo’s download toggle off while PHDP Duo mode is on removes PHDP’s ring too; surface a warning and an “Open PHDP” action.
- Latest link: `https://github.com/hxreborn/punch-hole-download-progress/releases/latest`; direct APK is versioned, so resolve via the release page/API rather than a fake stable URL.

### 3.9 Stability / FATAL — evidence now identifies two bugs

The owner-authorized pull is in `reverse/fatal-logs/` (92 files). The Nov 2 incidents are **two independent Duo bugs**, not LuckyTool:

1. **Java crash on QS rotation** (`system_app_crash@1793641719781.txt`): `ConstraintSet.clone` throws because Duo adds an id-less `DuoRiveView` to the OnePlus `OplusQSRootViewComponent` `ConstraintLayout`. The log shows `id=-`, the target `ConstraintLayout[1272x2772]`, injection 468 ms before the FATAL. Fix direction: assign a stable ID before `addView`, or inject only into a host that does not require IDs; verify with QS rotation.
2. **Native JNI overflow** (`tombstone_03`): `File::import` leaks ~110 `Paint` global refs per import; 444 imports produced 48,771 Paint refs and hit the 51,200 limit. Duo re-created Rive views 8–12×/s during shade layout when `lock_ring=true`; the storm repeated on restarts. Fix direction: one Rive view/import per surface, reuse it; never re-import on layout; add import/global-ref counters and a hard guard.

LuckyTool `libdexkit.so` SIGABRT at 2026-10-31 02:34 is a separate boot crash (different PID/time). It must not be used to explain Nov 2.

Safety constraints:

- No live resize/reparent of a Rive TextureView across rotation.
- No per-layout reattach or `.riv` import.
- Camera mode must not require moving a live Rive view; QS/shade use the normal placement.
- Keep the breaker/kill switch, add both signatures to the repair catalog.
- Prefer one Rive surface for the camera layout and a stable ID for every injected child.

### 3.10 Burn-in

- First measure whether OxygenOS already shifts the status bar. If yes, do nothing and document it.
- If not, the safest implementation is a small periodic translation of the whole element inside its fixed view, not a view move/resize.
- 1 px every hour, hold, return after one hour; use a low-frequency scheduler, no frame loop, no wake lock. Measure battery over 8h idle.
- Needs an owner decision: exact cadence (1px left for one hour then back, repeat) and whether it applies to all surfaces or only the main bar.

### 3.11 All-UI parity

- The ring objects are inside the artboard, so they scale with the host view.
- Any mode that requires split views, camera cutout, or per-surface placement must be gated to the main bar and marked `unverified` elsewhere.
- The Canvas clone must be updated from a shared geometry table (ring radius, stroke, seats, event scale, clock box, SIM angles).
- Per-UI test cells remain `UNVERIFIED` until a dump/screenshot/log exists.

---

## 4. Draft phase shape (not final)

The final phases will be written only after the five answers. The likely sequence:

| Phase | Goal | Main weapons |
|---|---|---|
| P0 | Evidence + crash classification + safe rollback | REA adb, logcat, LSPosed logs, tombstones, dumpsys, `DuoGuard`, settings-only A/B |
| P1 | App shell + selector + slider interaction + preset state model | Compose, Robolectric/Compose tests, screenshots, adb |
| P2 | Settings IA cleanup, GIF previews, report move, removals | Compose, ImageDecoder/AnimatedImageDrawable, Rive screenshot/GIF generator, tests |
| P3 | Rive ring decomposition + clock + camera mode + Canvas parity | Rive CLI 1.8.0 (`verify`, `screenshot`, `inspect`, `schema`), `scene.rml`, headless pixel measurement, Kotlin geometry tests |
| P4 | Download/PHDP UX + precedence state machine + simulation decision | PHDP contract, Duo mapping tests, module logs, real download capture |
| P5 | Burn-in + all-UI verification + performance/battery | adb/dumpsys/gfxinfo/batterystats, per-UI matrix, REA/JADX |
| P6 | Full regression, docs, release candidate | Gradle, log/easing/hash gates, review-strict, owner approval |

No phase starts before the previous gate; the crash gate is first.

---

## 5. Draft test shape (not final)

- Unit: selector state machine, preset state, knob-only slider value mapping, download-over-charging arbitration, clock formatting, geometry seats, SIM angles, burn-in cadence.
- Robolectric/Compose: long-press drag, icon tint, page snap, scroll-vs-slider gesture, preview invalidation.
- Rive headless: default/compact/custom poses; percent-centre + Wi-Fi-top; split battery/SIM; camera ring; clock 12/24h; each event GIF source frame.
- Canvas parity: same geometry table, side-by-side screenshots.
- Device: fatal A/B matrix; all surfaces; rotation; camera; download real/PHDP test; 100% charge; 8h idle battery; per-UI evidence.
- Report: no PII; new crash signatures added to `agent/REPAIR-CATALOG.md`.

---

## 6. Five questions for the owner

These are the blockers that cannot be solved from the code or the current images.

### Q1 — Navigation structure and tab names

The images are now available, but they conflict: the selector mockup shows **Home / Components / About**, while the owner named **Home / Customization / Report**, and the current app is **Home / Graphics / Report / Settings**. The icon references are a filled house, a sliders icon, and a documents+trend icon.

**Question:** What is the final bottom navigation?
- A. Three tabs: Home / Customization / Report; Settings opens from a gear on Home; use the house/sliders/report icons. (recommended, matches the three references)
- B. Four tabs: Home / Customization / Report / Settings; use the house/sliders/report/gear icons.
- C. Three tabs: Home / Components / About (literal mockup labels); Report is a page action inside About.
- D. Other (describe).

Recommended: A if the three reference icons are the complete set; B if Settings must remain one tap away. Do not guess the tab count from the mockup alone.

### Q2 — FATAL evidence

There is no owner capture for the latest safe-mode incidents. The only tombstone in the repo is a separate LuckyTool/DexKit abort.

**Question:** Will you provide the LSPosed log + tombstone from the last safe-mode incident, or authorize one controlled reproduction per boot (streaming logcat + tombstone pull + settings A/B)?

Options:
- A. I will send the existing logs/tombstone. (recommended)
- B. Run the controlled capture; one action per boot.
- C. Try to fix without reproduction (not allowed by the project's evidence rule).

Recommended: A, then B only if A is incomplete.

### Q3 — Split battery / SIM geometry

The current ring has no split battery. The bottom gap is 117.4°, two 85.6° halves do not fit, and “four dots” could mean two per SIM. **The split-battery design image is not among the six supplied files; the owner said another image would be provided.**

**Question:** Please provide the split-battery image, then confirm which is correct:
- A. Both battery halves move to the old four-dot area; four dots = 2 per SIM, SIM1 right, SIM2 left, all on the same curvature.
- B. One battery half moves; the other stays; four dots total.
- C. The full battery (ring) moves; four dots split 2 right/2 left.
- D. Other (describe with the image).

Recommended: do not implement until the split-battery image exists. A only if the image shows both halves moving.

### Q4 — Camera ring, “Resize”, and surfaces

The camera ring is not implemented; “Resize” has no current action; live resizing is a known SystemUI crash risk.

**Question:** What should the camera/Compact mode do?
- A. Main status bar only; QS/shade/keyguard use Default; tap opens a size control labelled “Resize”; no live view resize. (recommended)
- B. All surfaces use the camera ring; tap shows a transient “Resize” label and opens a sheet.
- C. Camera ring is a static placement only; no resize control.
- D. Different (describe with the image).

Recommended: A, because it matches the owner’s “QS/shade normal” sentence and the C2 risk.

### Q5 — Contradictory removals

The brief contains conflicts that change the requirements.

**Question:** Which statements are correct? (select all that apply)
- A. Remove the **Charging animation section** only; keep “Charging %” as a Middle-of-ring option.
- B. Remove “Charging %” from Middle of ring too.
- C. Remove **Icon Colour Match**; add its white↔black GIF only as a preview, not a control.
- D. Keep Icon Colour Match and add the GIF to it.
- E. Remove the **Calls toggle** from the UI and keep the call event permanently off; keep the code/art for a future version.
- F. Remove Calls from UI **and code/art/tests**.
- G. Remove **Smooth Graphics** and always use Rive, while keeping a Canvas fallback selector in Customization.
- H. Remove Smooth Graphics and also remove the Rive/Canvas selector; only Rive.

Recommended: A, C, E, G. These preserve the owner’s intent while keeping the Canvas fallback available.

### 6.1 Owner answers — 2026-11-02

| Question | Answer | Planning consequence |
|---|---|---|
| Q1 navigation | **Three tabs: Home / Customization / Report** | Rename Graphics to Customization and use the house/sliders/report icons; Settings reached by a gear on Home. No fourth bottom tab. |
| Q2 fatal evidence | **Pull the last two days of LSPosed + LuckyTool logs from the phone** | A read-only forensic pull is running now. P0 remains evidence-first; no crash fix before timestamp/pid correlation. |
| Q3 split battery | The image is **`new duo customization`**: eight SIM dots, battery line at the bottom, Wi-Fi centre, number above. “You must see the image.” | Use the image as the static target. It shows 8 dots (4 per side), not 4 total. The split transition frames still need authoring/owner review. |
| Q4 camera | **Main status bar + size sheet; QS/shade/keyguard normal** | Camera placement is main-surface only; no live Rive view resize; “Resize” opens a sheet/control. |
| Q5 contradictions | **Remove Charging option too; keep Icon Colour + add white↔black GIF; Calls UI off but code kept; Rive always with Canvas kept; PHDP opens its test** | Requirements: remove Animations “Charging” and Middle “Charging %”; keep Icon Colour with GIF; Calls off/UI removed; keep Canvas fallback; PHDP test opens PHDP. |

**Correction:** the earlier mapping of `new duo customisition icon.jpg` as “current rejected screen” was wrong. The owner names it “new duo customization”; it is the **target** ring layout. The current rejected screen was not among the six files or was not needed.

---

## 7. After the answers

1. Update this file with the answers and decisions.
2. Write `agent/REQUIREMENTS.md` additions with stable new IDs (UI/CUS/RING/DL/STAB/BURN/QA).
3. Update `agent/TEST-PLAN.md` with traceable cases and gates.
4. Write the final phased plan with per-phase files, weapons, dependencies, rollback, and exit gates.
5. Review Requirements ↔ Test Plan ↔ Plan for 1:1 traceability.
6. Only then start P0 (crash evidence) and no cosmetic code before the crash gate.

---

## 8. Open decision log

| Decision | Status | Source |
|---|---|---|
| Selector transparent, icon active tint | Proposed | Owner |
| Drag from anywhere on pill after hold | Proposed | Owner |
| Resting/held selector dimensions | Measured; settled held size needs confirmation | Images |
| Home/Customization/Report/Settings structure | Blocked | Images + Q1 |
| Preset model with last-custom snapshot | Proposed | Owner |
| Slider knob-only drag | Proposed | Owner |
| Remove Smooth Graphics / clock font / icon colour | Contradictory | Q5 |
| Remove Mic/Location/Volume | Confirmed | Owner |
| Calls off, UI removed | Confirmed | Owner |
| Simulation GIFs replace section | Confirmed | Owner |
| Contact Developer moves to Report | Confirmed | Owner |
| Center = Wi-Fi + event object | Confirmed | Owner |
| Percent two seats | Confirmed | Owner |
| Split battery/SIM geometry | Blocked | Q3 + image |
| Camera ring/Resize | Blocked | Q4 + image |
| Clock system format, one line | Confirmed | Owner |
| Download over charging | Confirmed | Owner |
| PHDP simulation trigger | Blocked by PHDP API | Q2/PHDP |
| Fatal fix | Blocked by owner logs | Q2 |
| Burn-in toggle | Proposed | Owner + measurement |
| All-UI parity | Required | Owner/AGENT |


## FILE: BRAINSTORM.md

# BRAINSTORM — Multi-UI Update Discovery Summary

Full capture: `brainstorms/2026-10-26-multi-ui-update-discovery.md` (English translation; no Arabic in files).

## Goal

Define the next major update before writing requirements, test plan, or code. The owner dictated the brief; the agent asked questions in batches of five with one suggested answer each.

## Locked decisions

1. **Surfaces:** Duo replaces the battery/status-icon cluster in the status bar, keyguard, shade header, and QS header. QS and shade are one verification surface.
2. **Host model:** hook the existing battery host; draw Duo in its bounds; restore exactly. No force/removal, no separate window overlay.
3. **Measured OnePlus host:** `quick_qs_status_icons` -> `batteryRemainingIcon` -> `battery_icon_view`; `statusIcons` is a plain `LinearLayout`. The old `shade_header_system_icons` / `StatusIconContainer` assumption was wrong for this build.
4. **UIs:** OnePlus OxygenOS, OPPO/realme ColorOS, Xiaomi HyperOS, Samsung One UI, vivo OriginOS, Honor MagicOS, Motorola MyOS, Nothing OS, AOSP baseline.
5. **Engines:** Rive + Canvas, user choice, Auto/Recommended; auto fallback to Canvas on failure; every Rive design has a Canvas clone.
6. **Customization:** middle slot (Wi-Fi/cell/battery %/clock/charging); 4+4 dots vs battery bar; Wi-Fi in the charge slot; icon packs; per-icon show/hide; 3 presets + advanced; live preview.
7. **UX:** assume the user is easily confused; minimal text, visual-first, logical; 3 pages (Home/Engine/Settings).
8. **Backup:** plain versioned JSON, no encryption, no secrets; export/import everything.
9. **Live events:** charging, alarm, screenshot, mic, location (P0); volume, unlock (P1); SystemUI hooks first; consent for UsageStats/NotificationListener.
10. **Cell accuracy:** four dots mapped to the system bar count; dual-SIM and 5G NSA.
11. **Language:** app and support channel English only.
12. **Stability:** no new core architecture; follow the old code paths.
13. **Process:** no code before Requirements + Test Plan + Plan v2 are approved; one branch per phase.
14. **Tools:** BEAM for non-sensitive work; DeepSeek for architecture/code/debug; never GLM.
15. **OEM assets:** locally extracted for prototype/engine testing; owner removes them at the official launch; no redistribution commitment.

## Measured evidence

- CPH2747 / Android 16 / OxygenOS 16.0.9.400; module 1.4.3-beta.1 (24); `duo_statusbar_stage=null`.
- Shade/QS tree measured; `batteryRemainingIcon` bounds `[1116,153][1202,254]`; `battery_icon_view` `[1119,178][1201,229]`.
- LSPosed: module active in SystemUI (`DuoSB` lines, pid 10266); shade header still stock.
- Windows NTFS `OS` partition not mounted; owner action.

## Open flags

- Phase 1 safe code is built (`phase/1-shade-qs`, 221 tests, safe release); **device verification is pending**.
- LSPosed safe mode occurred on the previous duplicate-fix build (forced remove/reparent); that path is removed. Rollback + safe APKs are in `build/`.
- 8-UI shade schema + static maps + candidate resolver probes are built; runtime dumps are still missing for six UIs.
- OnePlus vendor hooks (`OplusQSSimpleHeader`, `OplusSplitShadeHeaderControllerExImpl`) are static-only.
- 8 vendor APKs are acquired; 6 UIs still lack runtime dumps -> `unverified`.
- Windows logs not mounted.
- EOL is mixed CRLF/LF; normalize before commit.
- PERF-005 active-use +2% proposed.
- Support channel later.
- OEM icon removal timing is the owner's responsibility.


## FILE: CODE-REVIEW-PLAN-1.7.0.md

# Duo Status Bar — Code Review Plan (1.7.0 stable)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Post-1.7.0 stable review (branch `fix/1.6-stability`, HEAD `7361c73`) |
| Date | 2026-11-04 |
| Owner | Abdulkarim |
| Requirements | `agent/AGILE-1.7-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.7-PLAN.md` |
| Tests | `agent/AGILE-1.7-TEST-PLAN.md` |
| Release evidence | `agent/RELEASE-NOTES-1.7.0.md`, `agent/AGILE-1.8-NOTES.md` |
| Rules | `AGENTS.md`, `agent/AGENT.md` |
| Status | Review plan approved for execution; no code change before findings are reviewed |

## 1. Purpose

Validate the shipped 1.7.0 code against the approved requirements, plan and test plan, then find
and fix performance and battery leaks **without changing behaviour that already meets the
requirements**. A behaviour change is allowed only when the code contradicts a requirement or
violates a safety rule from `AGENTS.md` / `agent/AGENT.md`.

This review does not reopen the 1.6 fatal fix, does not add architecture, and does not replace the
Rive/Canvas split.

## 2. Scope

In scope:

- Runtime path inside SystemUI: `hook/**` (hook, host, monitor, readers, drawing, transitions).
- App path: `ui/**`, `DuoPreview.kt`, `MainActivity.kt`, `DuoApp.kt`, settings, update, reporting.
- Requirement traceability for BAT, RING2, HOME2, NAV2, ICON2, GLASS2, UX2, QA2.
- Unit tests and the project gates that protect the frozen surface.

Out of scope:

- Style, naming, formatting and generic refactors.
- New features or new hook frameworks.
- Re-testing vendor ROMs that remain `unverified`.
- The platform ART JIT fault recorded as `mitigated/platform` in `AGILE-1.8-NOTES.md`.

## 3. Non-negotiable rules for this review

1. Evidence over guessing: every finding cites a changed or reviewed file and line.
2. One property, one owner: geometry in Kotlin, easing in Rive.
3. Fail silent: any failure leaves the stock status bar working.
4. Defaults must not change: a fix maps to the same visible pixels unless a requirement is missed.
5. No secrets in the APK or in reports.
6. No `android.util.Log` inside SystemUI; use `L`.
7. No behaviour change without a requirement ID or a measured defect.
8. No fix that grows the Rive asset, adds a second renderer, or reparents views per frame.

## 4. Requirement traceability matrix

The matrix is the review checklist. `code` names the primary owner file(s); `test` names the
existing unit/device case; `status` is the review verdict after evidence.

### 4.1 Battery behaviour and transitions (BAT)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| BAT-01 | Stock battery host/lifecycle study, or documented safe overlay | `hook/DuoIconHost.kt`, `hook/DuoHook.kt`, `docs/rom-contract.md` | device walkthrough | pending |
| BAT-02 | Smooth HUD/QS/shade transitions, no snap/size jump/flash/blank | `hook/PanelHandoff.kt`, `hook/DuoIconHost.kt` | `PanelHandoffTest`, transition capture | pending |
| BAT-03 | Duo present whenever stock battery is present | `hook/DuoIconHost.kt` | transition capture | pending |
| BAT-04 | 1.6 crash guards remain | `hook/DuoGuard.kt`, `hook/DuoIconHost.kt`, `hook/DuoRiveView.kt` | P0 unit suite | pending |
| BAT-05 | Transition logs classified before lifecycle change | `docs/evidence/agile-1.7/**`, `AGILE-1.8-NOTES.md` | evidence review | pending |

### 4.2 Ring designs, battery, PHDP, cells (RING2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| RING2-01 | Classic 4-dot and 2 SIM 8-dot selectable and persistent | `hook/DuoMapping.kt`, `hook/DuoRiveView.kt`, `hook/DuoCanvasView.kt`, `settings/DuoPrefs.kt` | `DuoPrefsTest`, `DuoMappingTest` | pending |
| RING2-02 | New battery bar is live, not static 100% | `hook/DuoMapping.kt`, `hook/DuoStateMonitor.kt` | `DuoMappingTest` | pending |
| RING2-03 | PHDP progress uses selected design | `hook/DownloadWash.kt`, `hook/DuoMapping.kt` | `DownloadWashTest` | pending |
| RING2-04 | 8-dot battery bar is a continuous circular ring | `hook/RingGeometry.kt`, Rive scene | headless/device compare | pending |
| RING2-05 | Cell mapping matches stock per SIM (4/8 dots) | `hook/SignalMapping.kt`, `hook/CellReader.kt` | `SignalMappingTest` | pending |
| RING2-06 | Camera moves current design to cutout only on main bar | `hook/DuoIconHost.kt`, `hook/rom/RomAdapter.kt` | device walkthrough | pending |
| RING2-07 | Custom editor + per-dot simulation (4 full, 4+2, custom) | `ui/PreviewSimulator.kt`, `ui/DuoHomeScreen.kt`, `settings/DuoPrefs.kt` | `PreviewSimulatorTest` | pending |
| RING2-08 | Battery colour mapping preserved (100% plugged = normal) | `hook/DuoMapping.kt`, `hook/DuoStateMonitor.kt` | `DuoMappingTest` | pending |

### 4.3 Home, size, position, preview (HOME2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| HOME2-01 | Home is the design/customisation surface with live preview | `ui/DuoHomeScreen.kt`, `ui/DuoShell.kt` | device/Compose walkthrough | pending |
| HOME2-02 | One logical 0..100 scale, `physical = 50 + 1.7 * logical` | `settings/DuoPrefs.kt` | `DuoPrefsTest` | pending |
| HOME2-03 | Position drag full range, pointer stays attached | `ui/DuoSettingsComponents.kt`, `ui/GlassControls.kt` | device walkthrough | pending |
| HOME2-04 | Preview: charge slider, saver, 8-dot, centre inversion | `ui/PreviewSimulator.kt`, `ui/DuoHomeScreen.kt` | `PreviewSimulatorTest` | pending |
| HOME2-05 | Animated previews for Alarm/Flashlight/Screenshot/Unlock | `ui/DuoSettingPreview.kt`, `DuoPreview.kt` | device walkthrough | pending |
| HOME2-06 | Supplied design previews, Classic vs 2 SIM visibly different | `ui/**`, `res/drawable/**` | asset check | pending |

### 4.4 Navigation, icons, settings (NAV2/ICON2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| NAV2-01 | "Custom" short label | `ui/DuoShell.kt`, `ui/DuoNavIcons.kt` | device walkthrough | pending |
| NAV2-02 | Settings is the fourth pill tab | `ui/DuoShell.kt` | device walkthrough | pending |
| ICON2-01 | Owner-supplied icons used unchanged | `res/drawable/**` | asset check | pending |
| ICON2-02 | Per-icon hiding + All master switch | `settings/StockIconHider.kt`, `settings/StockIconInventory.kt`, `hook/StockIconHider.kt` | `StockIconHiderTest` | pending |
| ICON2-03 | All discovered slots incl. clock/battery; battery anchor survives | `hook/StockIconHider.kt`, `hook/DuoIconHost.kt` | device walkthrough | pending |
| ICON2-04 | Wi-Fi outside ring keeps Duo Wi-Fi glyph | `hook/DuoIconHost.kt`, `hook/DuoMapping.kt` | device walkthrough | pending |

### 4.5 ColorDuo glass and touch (GLASS2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| GLASS2-01 | Measured ColorDuo transparency/blur/refraction/rim/shadow | `ui/GlassSurface.kt`, `ui/GlassControls.kt` | pixel compare | pending |
| GLASS2-02 | Measured selector touch model | `ui/GlassControls.kt`, `ui/DuoShell.kt` | device recording | pending |
| GLASS2-03 | Active blue and null wine fill preserved | `ui/DuoTheme.kt`, `ui/GlassSurface.kt` | screenshot | pending |
| GLASS2-04 | ColorDuo REA evidence recorded | `agent/AGILE-1.7-*` | evidence review | pending |

### 4.6 Report, preview, events (UX2) and quality (QA2)

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| UX2-01 | Report title below blur, not clipped | `ui/DuoReportScreen.kt` | device screenshot | pending |
| UX2-02 | Live preview interactive, updates <100 ms, no idle loop | `ui/PreviewSimulator.kt`, `DuoPreview.kt` | device + logs | pending |
| UX2-03 | Four animated event previews, no old Simulation section | `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt` | device walkthrough | pending |
| QA2-01 | Only measured ROMs claimed supported | `hook/rom/RomProfiles.kt`, docs | matrix review | pending |
| QA2-02 | No fatal in transition/camera/rotation/per-icon | `hook/**` | P0 suite + walkthrough | pending |
| QA2-03 | Requirement → test → plan traceability | this file | audit | pending |
| QA2-04 | No PII, assets allowed | `L.kt`, `ui/LogReporter.kt`, `settings/TelegramLog.kt` | grep/audit | pending |

## 5. Performance and battery review tracks

Each hypothesis is a candidate, not a finding. A finding needs code evidence and a measured or
provable cost.

### 5.1 SystemUI runtime (highest risk)

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P1 | Renderer keeps advancing while the element is hidden | `DuoRiveView.kt`, `DuoIconHost.kt` | `setRenderActive(false)` on every hidden path; idle-stop logs |
| P2 | Repeated identical snapshots re-start the renderer | `DuoRiveView.render`, `DuoBinder` | equality skip present; verify every caller |
| P3 | A retry/survival timer keeps firing after detach | `DuoIconHost.kt` (`SURVIVAL_MS`, shade retries) | `removeCallbacks` on teardown; no orphan `postDelayed` |
| P4 | Receivers/content observers leak per attach | `DuoHook.kt`, `DuoStateMonitor.kt` | one register, one unregister; count on re-attach |
| P5 | High-frequency broadcasts trigger expensive reads/logging | `DuoStateMonitor.refresh`, `CellReader`, `WifiReader` | RSSI/connectivity burst; reflection count; log volume |
| P6 | Reflection is repeated on every refresh | `CellReader.kt` (`createForSubscriptionId`, signal getters) | cache `Method` handles or equivalent |
| P7 | Heartbeat write is too frequent / keeps the process awake | `DuoStateMonitor.heartbeatRunnable`, `DuoGuard` | settings write count; doze behaviour |
| P8 | FileObserver or screenshot watcher runs while disabled | `ScreenshotWatcher.kt` | start/stop pairing with module state |
| P9 | Download/charge/moon animations continue when idle | `DownloadWash.kt`, `ChargeMotion.kt`, `DotMoonMotion.kt`, `DuoRiveView` | animation end conditions; no frame loop after settle |
| P10 | Canvas fallback redraws continuously | `DuoCanvasView.kt`, `DuoElement` | invalidate only on change; no `postInvalidateOnAnimation` loop |

### 5.2 App and preview path

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P11 | Looping previews drive a frame callback for the whole screen life | `DuoPreview.kt` (`DuoRivePreview`), `DuoSettingPreview.kt` | `withFrameNanos` loop; lifecycle pause |
| P12 | Previews keep Rive renderers running while off-screen/backgrounded | `DuoPreview.kt`, `ui/DuoHomeScreen.kt`, `ui/DuoSettingsScreen.kt` | idle stop + lifecycle; renderer logs |
| P13 | Compose recomposes on every frame during holds | `DuoPreview.kt`, `PreviewSimulator.kt` | phase updates only when needed |
| P14 | Handlers/coroutines outlive composition | `DuoPreview.kt`, `ui/**` | `DisposableEffect` coverage |
| P15 | Update/download workers poll or retry too aggressively | `UpdateWorker.kt`, `UpdateChecker.kt`, `UpdateDownloadWorker.kt` | WorkManager constraints; backoff |
| P16 | Log collection or reporting holds a wake lock/thread | `RootLogs.kt`, `settings/TelegramLog.kt`, `ui/LogReporter.kt` | thread lifetime; timeout |

### 5.3 Cross-cutting

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P17 | Allocation churn on every status tick | `DuoMapping.kt`, `DuoStateMonitor.render`, `DuoIconHost` | object creation per tick; strings in hot path |
| P18 | Logging in the hot path floods logcat/XposedBridge | `L.kt`, `DuoStateMonitor`, `DuoIconHost` | lines per second during RSSI/battery bursts |
| P19 | Duplicate geometry/state owners cause extra work | `RingGeometry.kt`, `SlotGeometry.kt`, `DuoMapping.kt` | one owner per property |
| P20 | Dead code left by 1.7 paths (old simulation, old hide-all) | `ui/**`, `hook/**` | only code made obsolete by 1.7 |

## 6. Sub-agent assignments

Sub-agents are read-only. They return findings in the exact shape below; the main agent verifies
each line against the code and applies only approved fixes.

| Agent | Track | Scope |
|---|---|---|
| A | Business logic (RING2/HOME2) | ring designs, battery bar, PHDP, cells, camera, custom, size, position, previews |
| B | Business logic (BAT/NAV2/ICON2/GLASS2/UX2) | transitions, host lifecycle, navigation, per-icon hiding, glass, report |
| C | Performance/battery (SystemUI) | P1–P10, P17–P20 in `hook/**` |
| D | Performance/battery (app) | P11–P16 in `ui/**`, previews, workers, logging |
| E | Tests and deploy safety | requirement coverage, P0 guards, gates, frozen-surface diff |

Finding shape:

- `path`
- `line` (exact current line)
- `requirement` (ID or `PERF`/`BATTERY`)
- `confidence` (`high` only)
- `why` (1–2 sentences with evidence)
- `finding` (specific)
- `suggestion` (one safe fix direction, or `none` if investigation is needed)

A track with no solid issue returns `NO_FINDINGS`.

## 7. Severity

- **CRITICAL**: fatal/SystemUI crash, data loss, security exposure, requirement violated in a way
  that breaks the product.
- **WARNING**: measurable battery/perf regression, logic error, unhandled failure, drift risk.
- **SUGGESTION**: non-blocking improvement tied to a requirement or measured cost.

## 8. Fix policy

1. Fix only findings confirmed by the main agent.
2. Performance fixes must not change visible output: compare bound properties and pixels.
3. Requirement fixes must cite the requirement ID and update the traceability table.
4. One logical change per commit-sized edit; no opportunistic refactors.
5. After fixes: `./gradlew :app:testDebugUnitTest :app:assembleDebug` and the project gates.
6. If a fix needs a device measurement that is unavailable, record `unverified`; do not guess.

## 9. Verification commands

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 tools/route-module-logs.py --check
python3 tools/check-rive-eases.py
sha256sum app/src/main/res/raw/duo.riv   # compare with a fresh Rive build when the scene changes
```

Device walkthrough evidence follows `agent/WALKTHROUGH-PLAN-1.7.0.md`.

## 10. Exit criteria

- Every requirement row has a verdict: pass, fail, or `unverified` with the missing evidence.
- Every confirmed performance/battery finding is fixed or explicitly deferred with a reason.
- No new fatal, no new permission, no new secret, no new architecture.
- Unit suite and debug build green; gates clean.
- Review report and walkthrough report written under `agent/`.


## FILE: CODE-REVIEW-PLAN-1.8.0.md

# Duo Status Bar — Code Review Plan (1.8.0)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Post-1.8.0 implementation review |
| Date | 2026-11-05 |
| Owner | Abdulkarim |
| Requirements | `agent/AGILE-1.8-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.8-PLAN.md` |
| Tests | `agent/AGILE-1.8-TEST-PLAN.md` |
| Walkthrough | `agent/WALKTHROUGH-PLAN-1.8.0.md` |
| Branch | `feat/1.8-selector-live-preview` |
| Baseline | 1.7.0 stable (versionCode 89), 449 unit tests, CPH2747 |
| Preserved | `fix/1.7.0-review-fixes` at `eafdcb8`; commits `745fc5f`, `eafdcb8` |
| Rules | `AGENTS.md`, `agent/AGENT.md` |
| Status | Review plan only; no code change before findings are reviewed |

## 1. Purpose

Validate the 1.8.0 implementation against the approved requirements, plan and test plan. Find correctness, accessibility, performance and battery defects in the changed code, then fix only confirmed findings. Do not change behaviour that already satisfies a requirement. Do not reopen the 1.6 fatal fix, the 1.7 transition model, or the per-icon hiding surface.

The review is evidence-first. Every finding cites a current file and line, a requirement ID or a measured defect, and a proof. A hypothesis is not a finding.

## 2. Scope

In scope:

- App navigation and glass: `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt`, `ui/DuoNavIcons.kt`.
- Home, previews, demos and copy: `ui/DuoHomeScreen.kt`, `ui/DuoPreview.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsScreen.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsComponents.kt`, `ui/DuoReportScreen.kt`, `ui/LogReporter.kt`, `res/values/strings.xml`.
- Deleted simulator: `ui/PreviewSimulator.kt` absent and unreferenced.
- Position and persistence: `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt`.
- Battery/invert visual mapping: `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, `hook/DuoBinder.kt`, `hook/DuoCanvasView.kt`, `rive/duo/scene.rml`, `rive/duo/scene_old.rml`, `app/src/main/res/raw/duo.riv`, `duo_old.riv`.
- Tests and gates for the 18 requirements.

Out of scope:

- Style, naming, formatting and generic refactors.
- New features, new hook frameworks or new architecture.
- Re-testing vendor ROMs that remain `unverified`.
- A whole-app performance re-audit beyond the changed paths.
- The platform ART JIT fault already recorded in `AGILE-1.8-NOTES.md`.
- Release, tag, push, main merge or LSPosed store action.

## 3. Non-negotiable review rules

1. Evidence over guessing: every finding names the current file, line and proof.
2. One property, one owner: geometry in Kotlin, easing in Rive; one live rim owner; one battery-level source.
3. Fail silent: any failure leaves the stock status bar working.
4. Defaults must not change: Classic scene, untouched settings and untouched surfaces map to the 1.7 pixels.
5. No app-authored AGSL; Backdrop 1.0.1 is the only glass library; minSdk stays 33.
6. No unintended 24 dp Backdrop shadow; no manual double rim on the live path.
7. Both Rive scenes must declare every `DuoBinder` property.
8. No secrets, vendor assets or evidence committed; `.kilo/` never committed.
9. No behaviour change without a requirement ID or a measured defect.
10. Preserve `fix/1.7.0-review-fixes` and `feat/1.8-selector-live-preview`; no rebase/amend/delete.
11. No release, no main merge, no push, no LSPosed store.

## 4. Requirement review matrix

`code` names the primary owner files; `test` names the primary case. `status` is the verdict after evidence.

### 4.1 Glass and navigation

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| GLASS3-01 | Backdrop 1.0.1 highlight, no AGSL, no 24 dp shadow, one live rim owner | `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt` | T-GLASS3-01 | pending |
| GLASS3-02 | Radial white 0.16×press glow, 1.5×height, 8 dp inset | `ui/GlassControls.kt`, `ui/GlassSurface.kt` | T-GLASS3-02 | pending |
| NAV3-01 | Hover #3C88F8; selected white ~24 dp icon/label; four tabs | `ui/DuoShell.kt`, `ui/DuoNavIcons.kt` | T-NAV3-01 | pending |
| NAV3-02 | Replaceable icon layer, no owner-art redraw | `ui/DuoNavIcons.kt`, `ui/DuoShell.kt` | T-NAV3-02 | pending |

### 4.2 Battery and invert

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| BATT3-01 | 2 SIM arc follows 0–100 in real icon and preview; Classic frozen | `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, Rive scenes, `ui/DuoPreview.kt` | T-BATT3-01 | pending |
| BATT3-02 | One level mapping for Rive/Canvas/preview | `hook/DuoBinder.kt`, `hook/DuoMapping.kt`, `hook/DuoCanvasView.kt`, both scenes | T-BATT3-02 | pending |
| INV3-01 | Invert center in both designs; no duplicate number | `hook/DuoMapping.kt`, `hook/RingGeometry.kt`, both scenes, `ui/DuoPreview.kt` | T-INV3-01 | pending |

### 4.3 Home, invert persistence, position

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| INV3-02 | `middle_slot=percent`; no new column; Invert center toggle; migration | `ui/DuoHomeScreen.kt`, `settings/DuoPrefs.kt`, docs | T-INV3-02 | pending |
| HOME3-01 | Remove static card + Layout simulation + `PreviewSimulator.kt`; one card | `ui/DuoHomeScreen.kt`, `ui/PreviewSimulator.kt` (deleted), `res/values/strings.xml` | T-HOME3-01 | pending |
| HOME3-02 | Move real-drivable controls; delete per-dot/custom controls | `ui/DuoHomeScreen.kt`, `ui/DuoPreview.kt`, `res/values/strings.xml` | T-HOME3-02 | pending |
| POS3-01 | 0 = right edge; negative left; centered `lockRing`; RTL; UI/native parity | `ui/DuoSettingsComponents.kt`, `ui/DuoSettingsSections.kt`, `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt` | T-POS3-01 | pending |

### 4.4 Demos and copy

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| DEMO3-01 | Fixed Classic/2 SIM demos; events follow design; no GIF claim | `ui/DuoHomeScreen.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsScreen.kt` | T-DEMO3-01 | pending |
| DEMO3-02 | Exactly one design selected; disabled; `selectableGroup`; no second selector | `ui/DuoHomeScreen.kt`, `ui/DuoSettingsScreen.kt` | T-DEMO3-02 | pending |
| COPY3-01 | Short English, no dev terms, placeholders, strings.xml | `res/values/strings.xml`, all UI files above | T-COPY3-01 | pending |

### 4.5 Process and quality

| ID | Requirement | Code owner | Test | Status |
|---|---|---|---|---|
| QA3-01 | Closed 1:1 traceability | this plan, requirements, test plan | T-QA3-01 | pending |
| QA3-02 | Full gate set, 449 baseline, release build, owner device truth | tests, `tools/`, `WALKTHROUGH-PLAN-1.8.0.md` | T-QA3-02 | pending |
| QA3-03 | Both scenes declare binder properties; rebuild/hash both `.riv` | both `.rml`, `DuoBinder.kt`, both `.riv`, `tools/check-rive-eases.py` | T-QA3-03 | pending |
| QA3-04 | Preserve branches/commits; no `.kilo/`/evidence; no release/merge/push/store | git state, process evidence | T-QA3-04 | pending |

## 5. Review tracks

Each track returns findings or `NO_FINDINGS`. A finding must include the shape in §7.

### C1 — Glass and selector correctness

1. Confirm Backdrop 1.0.1 is the only selector glass; no app-authored AGSL or `RuntimeShader`.
2. Confirm the measured highlight tokens and the disabled 24 dp default shadow.
3. Confirm exactly one live rim owner and one fallback manual rim; no double rim remains.
4. Confirm the radial press glow values and that it is press-only.
5. Confirm hover index pure logic, correct boundary/clamping, #3C88F8, selected white ~24 dp icon and small label below, four tabs.
6. Confirm the icon source is replaceable without editing prototype vectors.
7. Check accessibility: tab semantics, selected state, disabled state, label announcement, touch targets.
8. Check RTL and font-scale behaviour of the four-tab pill.

### C2 — Battery and invert mapping

1. Confirm one clamped level-derived value feeds the 2 SIM line in Rive, Canvas and preview.
2. Confirm the Classic scene is pixel-mapped to the 1.7 baseline and no classic property changed meaning.
3. Confirm invert center in both designs: centre number, upper smaller normal occupant, one number.
4. Confirm `middle_slot=percent` is the only persistence; no new column/schema.
5. Confirm both scenes declare every property the binder writes; `PROPERTY_COUNT` matches.
6. Confirm both `.riv` files are byte-identical to fresh builds; do not trust only the new scene.
7. Check that a missing property degrades to a warning and does not blank the element (existing binder contract).

### C3 — Home, preview and position

1. Confirm the static **Live preview** card and the **Layout simulation** card are gone; `PreviewSimulator.kt` absent and unreferenced.
2. Confirm one Live Preview Controls card and the required control inventory; per-dot/custom controls deleted.
3. Confirm Invert center is a persisted toggle, while battery/charging/saver/icon simulations are clearly preview-only.
4. Confirm right-anchored normal Position (`0 = right`, `-limit … 0`), explicit centered `lockRing` mode (`-limit … +limit`), and one predicate in UI/prefs/native.
5. Confirm RTL uses physical right/left and does not mirror the numeric field.
6. Confirm pointer capture, no disappearance, no reset-to-grab, and no out-of-range write.
7. Confirm no stale strings, dead composables or unreachable simulator code.

### C4 — Demos and copy

1. Confirm Classic card always plays the old scene; 2 SIM card always plays the new scene; both are animated runtime Rive.
2. Confirm event previews follow the selected design and use the ring part in split mode when supported.
3. Confirm no deleted-GIF claim and no literal GIF dependency.
4. Confirm exactly one design selected, normalization, disabled state, `selectableGroup`, and no second selector.
5. Confirm full copy pass: one-line labels, helper text no more than 1.5 short lines, forbidden terms absent, placeholders unchanged.
6. Confirm report/preview hardcoded text moved to `strings.xml`; dead strings removed.
7. Check all screens at 360 dp and with large font/accessibility scaling.

### C5 — Performance and battery (changed paths only)

| # | Hypothesis | Primary files | What proves it |
|---|---|---|---|
| P18-1 | The selector keeps a live Backdrop/highlight loop when the app is idle | `GlassSurface.kt`, `GlassControls.kt`, `DuoShell.kt` | No frame callback while not pressed/dragging; idle sample |
| P18-2 | Hover recomposes the whole bar or writes state per frame | `DuoShell.kt` | Pure hover index; only icon/label state changes |
| P18-3 | Fixed design demos keep two Rive renderers alive off-screen | `DuoHomeScreen.kt`, `DuoSettingPreview.kt` | Lifecycle pause/idle stop |
| P18-4 | Event previews loop forever when disabled/backgrounded | `DuoSettingPreview.kt` | `playKey` stops; coroutine cancels |
| P18-5 | Invert/battery changes restart the whole Rive machine unnecessarily | `DuoMapping.kt`, `DuoBinder.kt` | Identical-snapshot skip; no extra surfaces |
| P18-6 | Position/`lockRing` writes trigger repeated layout passes | `DuoIconHost.kt`, `SlotGeometry.kt`, `DuoPrefs.kt` | One write per change; no per-frame re-anchor |
| P18-7 | Two battery-level owners cause duplicate work or disagreement | `DuoMapping.kt`, `RingGeometry.kt`, previews | One value path; mapping tests |
| P18-8 | Copy/structure changes add allocations or strings in a hot path | `DuoHomeScreen.kt`, `strings.xml` | No hot-path string formatting; no per-tick allocations |

A track with no solid issue returns `NO_FINDINGS`.

### C6 — Tests and deploy safety

1. Confirm all 18 requirements have a test and evidence path; reverse map has no orphan.
2. Confirm the existing 449 tests stay green and the new tests cover the required sub-checks.
3. Confirm both `.riv` hashes, property parity, route-log gate and ease gate.
4. Confirm no `.kilo/` or `docs/evidence/` staged; branch heads and preserved commits unchanged.
5. Confirm no release/tag/push/main merge/store action.

## 6. Sub-agent assignments

Sub-agents are read-only reviewers. They return findings in the shape below; the main agent verifies each line and applies only approved fixes.

| Agent | Track | Scope |
|---|---|---|
| A | C1 | glass, highlight/shadow, press glow, hover/selected, icon layer, accessibility |
| B | C2 | battery line, invert mapping, both scenes, binder parity, both `.riv` |
| C | C3 | Home cleanup, preview controls, position, `lockRing`, RTL |
| D | C4 | design demos, event previews, selector semantics, full copy |
| E | C5/C6 | performance of changed paths, tests, gates, process/branch safety |

Finding shape:

- `path`
- `line` (exact current line)
- `requirement` (ID or `PERF`/`BATTERY`)
- `confidence` (`high` only)
- `why` (1–2 sentences with evidence)
- `finding` (specific)
- `suggestion` (one safe fix direction, or `none` if investigation is needed)

## 7. Severity

- **CRITICAL**: fatal/SystemUI crash, data loss, security exposure, broken stock bar, or a requirement violated in a way that breaks the product.
- **WARNING**: measurable battery/perf regression, logic error, unhandled failure, accessibility blocker, drift risk.
- **SUGGESTION**: non-blocking improvement tied to a requirement or measured cost.

## 8. Fix policy

1. Fix only findings confirmed by the main agent against the current code.
2. A fix cites the requirement ID or measured defect; no opportunistic refactors.
3. Performance fixes must not change visible output; compare bound properties and pixels.
4. One logical change per commit-sized edit; no concurrent edits to the same file.
5. After fixes: run the full test plan and both Rive gates. If a device measurement is unavailable, record `unverified`.
6. No release, no main merge, no push, no LSPosed store. Preserve the review-fix branch and commits.

## 9. Verification commands

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline
./gradlew :app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline
python3 tools/route-module-logs.py --check
python3 tools/check-rive-eases.py
rive rive/duo --once
sha256sum app/src/main/res/raw/duo.riv app/src/main/res/raw/duo_old.riv
```

Device evidence follows `WALKTHROUGH-PLAN-1.8.0.md`; it is owner-run and CPH2747-only truth.

## 10. Exit criteria

- Every requirement row has a verdict: pass, fail, or `unverified` with the missing evidence.
- Every confirmed finding is fixed or explicitly deferred with a reason.
- No new fatal, no new permission, no new secret, no new architecture, no new settings column.
- 449 baseline tests plus new tests pass; debug and release builds succeed; route/ease/hash gates clean.
- Review report and owner walkthrough summary written under `agent/`.
- No release, no main merge, no push, no LSPosed store; branch and review-fix commits preserved.


## FILE: CODE-REVIEW-REPORT-1.7.0.md

# Duo Status Bar — Code Review Report (1.7.0 stable)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Reviewed state | Branch `fix/1.6-stability`, HEAD `7361c73` (`1.7.0`, versionCode 89) plus the review fixes below |
| Date | 2026-11-04 |
| Requirements | `agent/AGILE-1.7-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.7-PLAN.md` |
| Test plan | `agent/AGILE-1.7-TEST-PLAN.md` |
| Review plan | `agent/CODE-REVIEW-PLAN-1.7.0.md` |
| Walkthrough plan | `agent/WALKTHROUGH-PLAN-1.7.0.md` |
| Scope | Full 1.7.0 runtime and app code, not a diff-only review |
| Device state | CPH2747 not attached during this pass; device-only acceptance stays `unverified` |

## 1. Method

- Read the requirements, plan, test plan, 1.7 release notes and 1.8 notes.
- Ran five read-only sub-agents in parallel: RING2/HOME2, BAT/NAV2/ICON2/GLASS2/UX2, SystemUI
  performance/battery, app performance/battery, tests/dead-code/deploy.
- Verified every applied fix against the current source; no fix was applied from a sub-agent claim
  alone.
- Ran `:app:testDebugUnitTest :app:assembleDebug` and the two project gates.

## 2. Performance and battery fixes applied

These fixes preserve the visible output. Each one removes work that had no on-screen effect.

| # | File | Change | Why it matters |
|---|---|---|---|
| 1 | `hook/DuoHook.kt:137` | Stop `DuoStateMonitor` on the live-disable path before `host.teardown()` | The receiver, ContentObserver, screenshot FileObservers and 20 s heartbeat stayed alive with no element attached. |
| 2 | `hook/DuoHook.kt:651` | Keep one status-bar layout listener per root; remove the old one when the root changes | Disable/re-enable stacked a second listener, so every layout ran the hide pass twice. |
| 3 | `hook/DuoIconHost.kt:1015` | Stop the main copy's renderer when the main bar is hidden; restart on reveal | Hidden main copy kept restarting a looping Rive renderer on every status change. |
| 4 | `hook/DuoIconHost.kt:1291` | Tie each QS/shade copy's renderer to its alpha target; stop it at zero alpha and on latch | Hidden extras re-armed the Rive renderer on every download/RSSI update. |
| 5 | `hook/DuoCanvasView.kt:120` | Add `setRenderActive` to the Canvas fallback: store snapshots but skip invalidate/tick while hidden | Hidden Canvas copies no longer redraw on every state change. |
| 6 | `hook/CellReader.kt:31` | Cache reflective `Method` handles for `createForSubscriptionId`, per-RAT getters and `ServiceState.getNrState` | Reflection ran on SystemUI's main thread on every connectivity/RSSI refresh. |
| 7 | `hook/WifiReader.kt:55` | One active-network/capability snapshot per sample; read `connectionInfo` only as fallback | Three duplicate binder queries plus an unconditional `connectionInfo` read per refresh. |
| 8 | `DuoPreview.kt:70` | Add the 2 s idle renderer stop to the Home live preview and stop it on dispose | The Home lab had the same continuous idle Rive loop that `DuoRiveStill` already guards against. |
| 9 | `DuoPreview.kt:466` | Add `rememberResumed()` and gate preview frame loops/reveal loops | Preview loops kept running after the app went to the background. |
| 10 | `ui/DuoSettingPreview.kt:117` | Gate the event-row replay loop on the resumed lifecycle | Four event previews kept replaying while the tab was backgrounded. |
| 11 | `ui/DuoSettingsScreen.kt:213` | Pause the 1.5 s status re-read while the tab is not resumed | The loop woke SharedPreferences, Settings.Global, PackageManager and Shizuku every 1.5 s off-screen. |
| 12 | `RootLogs.kt:295` | Destroy the `su` process and use a daemon worker on timeout | Every timed-out root capture leaked a worker thread and a child process. |
| 13 | `hook/DuoRiveView.kt:187` | Stop the view-model poll after `teardown()` | A disposed Rive view could keep polling and bind late. |

## 3. Transition and requirement fixes applied

| # | File | Change | Requirement |
|---|---|---|---|
| 14 | `hook/DuoIconHost.kt:757` | Do not cancel an in-flight collapse hand-off when `setPanelOwner(null)` follows `onPanelCollapsed()`; start it only when none is running | BAT-02: the slide was cancelled before it could draw. |
| 15 | `hook/DuoIconHost.kt:1233` | Apply the incoming QS/shade copy first, then the outgoing one | BAT-02: `anotherCopyVisible()` saw the outgoing alpha already at 0, so QS→shade was a hard cut while shade→QS faded. |
| 16 | `ui/DuoHomeScreen.kt:212` | Pass the selected `rawRes` to the Home live preview | HOME2-01/HOME2-06: the live preview always showed the 2 SIM drawing. |

## 4. Requirement validation

Status values: **PASS** = code and existing tests satisfy the requirement; **PARTIAL** = implemented
but a required sub-case is missing; **FAIL** = code contradicts the requirement; **UNVERIFIED** =
needs the device walkthrough or a measurement that is not available in this pass.

### BAT

| ID | Status | Evidence |
|---|---|---|
| BAT-01 | PASS (documented) | Overlay/host decision and measured ROM contract are in `AGENTS.md`, `docs/rom-contract.md`, `AGILE-1.8-NOTES.md`. |
| BAT-02 | PARTIAL | Hand-off cancel and QS/shade cross-fade order fixed. Device log shows normal 26–40 px/120 ms slides; 3 `too-far` skips occurred while `lockRing`/Camera was enabled. Six-direction capture is still required. |
| BAT-03 | PARTIAL | `mainCopyReady`/`coverOrder` hold the outgoing copy; device frame capture still required. |
| BAT-04 | PASS (unit) | 449 unit tests, P0 guards present; device P0 run still required. |
| BAT-05 | PASS | Transition measurements and residuals recorded in `AGILE-1.8-NOTES.md`. |

### RING2

| ID | Status | Evidence |
|---|---|---|
| RING2-01 | PARTIAL | Rive selects `duo`/`duo_old`; `DuoCanvasView` always draws the 2 SIM line/eight dots, so the Canvas fallback cannot render Classic. |
| RING2-02 | **FAIL** | `RingGeometry.kt` fixes `BATTERY_LINE_START/END` and `scene.rml:181` fixes the trim; the code comment says the reference draws the full arc at 80/49/19. The bar does not follow the level. |
| RING2-03 | **FAIL** | `DownloadWash.of` has no design input and always uses the old ring trims; `DownloadProgress.toWash` calls it without a design. |
| RING2-04 | UNVERIFIED | Needs headless/device curvature comparison. |
| RING2-05 | **FAIL** | `DuoVisual` carries one `cellLevel`; `scene.rml:991-1009` binds `cell5..cell8` to the same properties as `cell1..cell4`. Both sides mirror one SIM. |
| RING2-06 | PARTIAL | `lockRing` centers the ring; there is no measured camera-cutout binding and no enforced “Wi-Fi outside visible” mode. |
| RING2-07 | **FAIL** | `DuoSettings` has `offsetX`, `indicatorsOffsetX` and `percentHeight` only; no rotation, Wi-Fi detach/pin, 2D centre move or per-dot module layout. The Home simulator does support 4 full/4+2/custom masks. |
| RING2-08 | PASS | Level/charging/saver mapping and the 100% plugged rule are covered by `DuoMappingTest`/`shouldShowCharging`. |

### HOME2

| ID | Status | Evidence |
|---|---|---|
| HOME2-01 | PARTIAL | Home has design cards and the live lab; the full Custom editor is on the Custom tab. `DuoPresetStore.apply("custom")` falls back to current settings when no snapshot exists, so Custom is not blank. |
| HOME2-02 | PARTIAL | `DuoPrefs.logicalToPhysical` is `50 + 1.7 × logical` and clamped 50..220; optical equality uses one whole-view `OLD_OPTICAL_SCALE`, not per-art normalisation. |
| HOME2-03 | UNVERIFIED | Drag code exists; the full-range/pointer test is device-only. |
| HOME2-04 | PASS (simulator) | `PreviewSimulator` exposes charge, saver, design, 4-full/4+2/custom dot masks, centre inversion and middle-slot choice. |
| HOME2-05 | PASS | `DuoEventRowPreview`/`DuoEventSimulationPreview` animate Alarm, Flashlight, Screenshot and Unlock. |
| HOME2-06 | PARTIAL | The Home live preview now uses the selected drawing; the supplied still/GIF design references still need an asset check. |

### NAV2 / ICON2

| ID | Status | Evidence |
|---|---|---|
| NAV2-01 | PASS | `nav_engine` is “Custom” and the pill label is not clipped by the responsive selector width. |
| NAV2-02 | PASS | Four items: Home, Custom, Report, Settings; Settings is the fourth. |
| ICON2-01 | **FAIL** | `DuoNavIcons.kt:10-13` says the Home/Custom/Report icons are redrawn Compose vectors; no owner-supplied asset is used. |
| ICON2-02 | **FAIL** | The UI still exposes the legacy “Hide other status icons” switch (`DuoSettingsSections.kt:606`); `StockIconSelection.withAll` has no production caller, so the required All master switch is absent. |
| ICON2-03 | PARTIAL | The inventory walks the `system_icons` strip; the clock and the sound/vibrate group live outside it on CPH2747, so they cannot be listed or hidden. The battery anchor is preserved (`StockIconHider` keeps it INVISIBLE). |
| ICON2-04 | PARTIAL | Split indicators exist; the “Wi-Fi outside + stock Wi-Fi hidden” case needs the device walkthrough. |

### GLASS2 / UX2 / QA2

| ID | Status | Evidence |
|---|---|---|
| GLASS2-01 | UNVERIFIED | The measured tokens are used, but the navigation surface also applies brightness/contrast; side-by-side pixel comparison is still required. |
| GLASS2-02 | PARTIAL | `DuoShell` uses `detectDragGesturesAfterLongPress`, snaps the selector to the finger and has no velocity/haptic crossing; ColorDuo starts on press and settles by velocity. |
| GLASS2-03 | PASS | Active blue and null wine fill are in the theme. |
| GLASS2-04 | PASS | ColorDuo evidence is recorded in the phase notes. |
| UX2-01 | UNVERIFIED | Needs a 1272×2772 screenshot of the report page. |
| UX2-02 | PARTIAL | Idle renderer stop and lifecycle gating added; the <100 ms update measurement is device-only. |
| UX2-03 | PASS | The four event previews replace the old static rows. |
| QA2-01 | PASS | Only CPH2747 and HyperOS 3 are marked measured; other ROMs remain `unverified`. |
| QA2-02 | PARTIAL | 449 unit tests pass; the device P0/transition matrix is still required. |
| QA2-03 | PASS | The requirement-to-test traceability is in the review plan. |
| QA2-04 | PARTIAL | No PII audit was run; the deploy findings below still apply. |

## 5. Additional review findings (not fixed)

### Deploy safety

- **Direct Telegram bot token can be compiled into the APK.** `app/build.gradle.kts:59` injects
  `TELEGRAM_BOT_TOKEN` from `.env`; `TelegramLog.kt` uses it as a fallback. The current `.env` value
  is a placeholder and the shipped 1.7.0 APK has no token pattern, but a local release with a real
  token would violate `AGENTS.md` rule 9. Keep relay-only delivery, or restrict the token to a
  non-publishable build type.
- **Release tag is not checked against Gradle’s version.** `.github/workflows/build-release.yml`
  derives the release name from the tag but does not compare it with `versionCode`/`versionName`;
  a tag such as `90-1.7.1` would publish an APK that still reports 89/1.7.0.

### Dead code and duplication (suggestions)

- `DuoCellSimulationPreview` has no caller after the old Simulation section was removed.
- `StockIconHider.isReplaced` has no production caller; only its test uses it.
- `PreviewSimulator` duplicates the Wi-Fi glyph/flashlight path from `DuoCanvasView`; a geometry
  change in one copy can make the preview lie.
- `RingGeometry.CELL_DOT_POSITIONS`/`BATTERY_LINE_*` are duplicated as literals in
  `rive/duo/scene.rml`; there is no parser test that keeps the two tables equal.
- The Customization page uses a scrolling `Column`, so all preview rows stay composed; the resumed
  gate stops background work, but off-screen rows still hold their Compose state. A `LazyColumn`
  would remove that residual.

### App preview residual

- `DuoRivePreview` still requests a frame every vsync while the page is resumed, including the hold
  phases. The renderer now idles, so the remaining cost is the frame callback, not drawing. Moving
  the hold windows to `delay` would reduce it further; this is a suggestion, not a leak.

## 6. Verification

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 449 tests, 0 failures, 0 errors, 0 skipped |
| `:app:assembleDebug` | Success |
| CPH2747 device log | 6,216 `DuoSB` lines; 0 FATAL/SIGSEGV/SIGABRT; 248 idle pauses; 85 normal hand-off slides; 3 `too-far` skips in Camera mode |
| CPH2747 test log 22:16–22:41 | 3,573 `DuoSB` lines; 0 FATAL; Classic shade copy stayed in the 127 px strip; restart request did not restart SystemUI |
| Shade behavior | Owner selected build 1 (`a534…`) as the reference; the later shade-parent change and its 6 tests were reverted; 449 tests pass |
| `tools/route-module-logs.py --check` | 0 files to fix, 0 log calls left for review |
| `tools/check-rive-eases.py` | 158 easing keyframes, 158 carry a curve, 0 inert |
| Rive asset | Unchanged; no `duo.riv` rebuild was required |
| Java | Build used `/usr/lib/jvm/java-21-openjdk-amd64`; the default Java 25 is not supported by this AGP |

## 6.1 Device verification (CPH2747, 2026-11-04)

- Device: CPH2747, Android 16 / SDK 36, build `CPH2747_16.0.9.400(EX01)`, KernelSU root.
- LSPosed: module `io.github.kvmy666.duostatusbar` enabled, scope `com.android.systemui`.
- Installed APK SHA-256 `a534437544a9ee72b2b9b0b489c93271da9c27705139646d6650a1704db01788`,
  identical to the review build.
- Runtime: `stage=2`, `renderer=Rive`, `attached=true`, size 169 %, design `new`, `lock=false`,
  `split=false`.
- Log evidence: 6,216 filtered `DuoSB` lines; 0 `FATAL`/`SIGSEGV`/`SIGABRT`; 248
  `Rive renderer paused (idle)` lines; 85 normal hand-off slides of 26–40 px over 120 ms.
- Residual found on device: three `panel handoff: no x slide (too-far)` events at
  21:56:27–21:56:31 while `lockRing` (Camera/Compact) was enabled. The hand-off is deliberately
  skipped above `HANDOFF_MAX_DP = 24`; in Camera mode the centred main copy and the QS/shade copy
  are farther apart, so the collapse does not slide. This needs either a measured Camera-specific
  bound or a crossfade; it is not fixed without a second device pass.
- The phone dropped off Wi-Fi during the transition test (AC powered, level 20 %), so the
  six-direction capture, per-icon matrix, size/design screenshots and PHDP check remain
  incomplete. The filtered log and summary were sent to Telegram as messages 36 and 37.

## 6.2 Fatal / safe-mode evidence (pending)

- `2026-11-04T21:56:37.000 E/LSPosedService-SafeMode: System UI crashed too many times, stop all
  modules and enter safe mode ...` happened 0.4 s after
  `21:56:36.584 DuoSB | restart requested by the app - restarting System UI`.
- The pulled logcat and `crash` buffer contain no SystemUI `FATAL`/`SIGSEGV`/`SIGABRT` stack; the
  only fatal entries are unrelated `app.source.getcontact` Keystore crashes at 03:02–03:52 and
  22:31–22:33.
- The fresh 22:16–22:41 log has no fatal or safe-mode line. It shows
  `22:39:50.587 restart requested by the app - restarting System UI`; the next module line at
  22:40:43 still reports PID `10266` but a different LSPosed token, so the log does not confirm
  whether the process restarted. Do not claim success or failure without a process-start line.
- The 21:56 SystemUI crash stack/tombstone is still missing. Do not change the Rive startup path
  until it is pulled. The silent restart failure and the repeated-restart safe-mode risk need their
  own check; the owner-run checklist is in `agent/DEVICE-TEST-REQUEST-2026-11-04.md`.

## 6.3 Shade anchor position

- The legacy fix commit `0ffeb3f` ("restore legacy shade anchor position") removed a
  `if (view.translationY < 0f) view.translationY = 0f` clamp. Current `applyExtraLayout` still uses
  the same legacy path: `anchorOnBattery(..., alignEnd = true)` for the QS cluster and
  `place(..., slot.center)` otherwise, with no clamp. It is also identical in 1.6.0.
- The old dump (new design, 141 px) had the shade copy at `@1044,-18` inside the 438 px header,
  matching the documented `(1102,112)-(1209,235)`.
- The 22:40 device log found the regression cause: with Classic the optically scaled view is
  134x151 and `shade header layout target=android.widget.LinearLayout[1170x127]`; the old
  `target.height * 10 < desired * 9` rule (127/134 = 94.8 %) kept the copy inside the 127 px stock
  strip, so it moved down. The new design at 141/142 px moved to the 438 px parent. The owner's
  shade screenshot confirms the lower Classic ring.
- The owner reviewed build 1 (`a5344375…01788`) and chose its shade/QS/HUD behavior as the
  reference. The later `needsTallerParent`/`shadeHeader` change was therefore reverted, along with
  its `SlotGeometryTest`; `DuoIconHost.attachExtraView` is back to the build-1 shortfall rule. The
  Classic shade copy remains in the stock 127 px strip, which is the owner-accepted build-1
  position. 449 unit tests pass after the revert; the build-2 APK (`c185…`, Telegram 38) is
  superseded and must not be used.

## 7. Recommendation

**NEEDS CHANGES before claiming 1.7.0 meets the full requirements.** The performance/battery leaks
found in the SystemUI and app paths are fixed and the suite is green, but the device pass found a
SystemUI safe-mode event whose crash stack is still missing, and the requirement audit found real
product gaps: the 2 SIM battery line is static (RING2-02), PHDP ignores the selected design
(RING2-03), the eight dots mirror one SIM (RING2-05), the Custom editor is narrower than the plan
(RING2-07), the All icon switch and the owner-supplied nav icons are missing (ICON2-01/02), and the
ColorDuo touch model is not matched (GLASS2-02). The shade anchor code matches the legacy position;
no position change was made without a measured delta. These are feature/safety work, not performance
fixes, and need the owner-run tests in `agent/DEVICE-TEST-REQUEST-2026-11-04.md` plus the device
walkthrough in `agent/WALKTHROUGH-PLAN-1.7.0.md`.


## FILE: DECISIONS.md

# Decisions (ADR Log)

Status: living. Each decision has context, the choice, and the consequence.

## ADR-001 — Hook the existing battery host
- Context: the old model hid stock icons and injected a separate Duo view; the owner wants Duo where the battery is, without forcing/removing it.
- Decision: keep the stock battery view, intercept its drawing, draw Duo in its bounds, restore exactly on disable. No removal, no separate window overlay.
- Consequence: the host resolver must find the real draw point per UI; if it cannot, leave stock and log.

## ADR-002 — QS and shade are one surface
- Context: the QS lives in the shade; the owner expects one verification to cover both.
- Decision: treat the shade header and QS header as one surface for the fix; capture both as evidence.
- Consequence: one host binding, two screenshots/dumps.

## ADR-003 — Measured OnePlus tree is authoritative
- Context: CPH2747 measured `quick_qs_status_icons` -> `batteryRemainingIcon` -> `battery_icon_view`; the old code looked for `shade_header_system_icons` / `StatusIconContainer`.
- Decision: use the measured ids/classes for OxygenOS 16; keep the old paths as fallbacks only.
- Consequence: no guessed OnePlus ids; other UIs still need their own measurement.

## ADR-004 — Additive code, no new core architecture
- Context: the app is stable; the owner asked not to introduce new things.
- Decision: extend `DuoIconHost` / `ContainerFinder` / `RomAdapter` in their existing style. App-only UI libraries are allowed; a new core dependency needs a written reason and approval.
- Consequence: slower, smaller changes; no framework rewrite.

## ADR-005 — Every Rive design has a Canvas clone
- Context: not every device can run Rive; the owner requires Canvas parity.
- Decision: every Rive design/state must have a Canvas implementation with the same geometry/state logic.
- Consequence: each animation is designed twice; tests compare both.

## ADR-006 — Auto fallback Rive -> Canvas
- Context: Rive can fail natively; the current behaviour already falls back.
- Decision: Auto tries Rive; on capability failure, bind timeout, or native failure, switch to Canvas for the process; user can force either; log the reason.
- Consequence: no half-attached Rive; Canvas must be complete.

## ADR-007 — English only
- Context: the owner wants English app and support; the CLI has problems with Arabic.
- Decision: all file names and file contents, app UI, and support channel are English.
- Consequence: no Arabic localization in this release.

## ADR-008 — Plain JSON backup
- Context: the owner wants backup/import of everything, with no sensitive data.
- Decision: one versioned JSON, no encryption, no passwords; import validates and confirms before overwrite.
- Consequence: schema versioning and migration tests are required.

## ADR-009 — 8 vendor UIs + AOSP; unverified is explicit
- Context: only OnePlus and HyperOS are measured.
- Decision: support 8 vendor UIs + AOSP; a UI without a measured dump is `unverified` and not claimed.
- Consequence: per-UI evidence is the acceptance gate.

## ADR-010 — Branch per phase
- Context: the update is large and parallelizable.
- Decision: `phase/N-name`, UI sub-branches `phase/N/<ui>`; no `main` commits; merge after the phase gate.
- Consequence: parallel agents must use separate worktrees/files.

## ADR-011 — Model routing
- Context: cost and quality.
- Decision: BEAM for non-sensitive tasks; DeepSeek for architecture/code/debug/critical review; never GLM; DeepSeek is the ceiling.
- Consequence: `general` + BEAM is avoided when the REA tool schema exceeds 128 tools; use `explore-fast`/`builder` (REA denied).

## ADR-012 — Host rendering spike (pending)
- Context: `battery_icon_view` is an `ImageView`; Canvas can use a `Drawable`, but Rive may need a child view inside `batteryRemainingIcon`.
- Decision: spike both options on CPH2747 before choosing. Canvas drawable first; Rive child view only if it stays inside the host and does not create a window overlay.
- Status: proposed; decision after the spike. If Rive cannot be hosted safely, Phase 1 uses Canvas for this host and logs the limitation.

## ADR-013 — OEM assets are local/prototype
- Context: OEM icons are copyrighted; the owner will remove them at the official launch.
- Decision: extract locally for analysis/engine testing; do not commit; release builds do not depend on them.
- Consequence: the owner controls removal timing; no redistribution claim.

## ADR-014 — Support channel later
- Context: user communication is difficult; the owner wants a technical support channel in English.
- Decision: Telegram, created later; no implementation in the current phases.
- Consequence: the in-app report zip is the interim path.


## FILE: DEVICE-TEST-REQUEST-1.8.0.md

# Device test request — 1.8.0-alpha.8

Build: `1.8.0-alpha.8` (code 97), delivered to the owner through Telegram (message 45) and
installed on CPH2747 at 13:50:03 with data kept.

**Restart the phone / System UI first.** The module update does not take effect until System UI
restarts.

## Checklist

1. **Install:** confirm Settings shows `1.8.0-alpha.8` (code 97). Restart the phone or System UI
   before judging the bar.
2. **Idle layering:** while idle, the tab row must stay above the selector. The selector must keep
   the frosted surface with no blur, and icons/labels must stay sharp.
3. **Moving layering:** drag the selector. It must rise above the tab row and bring back the lens
   and chromatic aberration (`selectorMovingBackdrop = page + pill + row`) while dragging. No blur
   may be added to the selector.
4. **Selector frost:** in the dark theme the frosted surface is white at 0.12; in the light theme
   it is black at 0.06. Press a tab and confirm the frost fades with the press.
5. **Selector depth:** confirm the inner and drop shadow, and the always-on highlight (idle at
   0.45x). The lens and chromatic aberration must appear only while moving.
6. **Selector colours:** select a tab; its icon must be blue (`DuoColors.NavAccent`). Hover another
   tab and confirm it is blue too. Every other tab must stay white.
7. **Shade seat:** open the shade/QS panel. The element must keep the v89 battery anchor (X right,
   Y ~-18) with no clamp, and must not overlap the bell icon.
8. **Clock:** set the middle slot to clock. Confirm the format is `H:mm`/`h:mm` without AM/PM and
   that the minutes fit inside the ring.
9. **Regression:** charge the phone with Invert center on (bolt at ring centre in both designs);
   drag/tap the Wi-Fi/cell/5G sliders and confirm the value persists. Confirm System UI stays alive
   and the status bar keeps rendering.

## If it fails or System UI restarts

**Do not clear the log and do not reboot before pulling the log.** A reboot can wipe the System
UI log and tombstone. Pull logcat immediately, plus any tombstone written around the failure.
LuckyTool may name another process; search for `Duo`, `Rive` and `SystemUI` in the capture.
Attach a screenshot and the failure-window log.


## FILE: DEVICE-TEST-REQUEST-2026-11-04.md

# Device Test Request — 2026-11-04 (CPH2747)

The owner runs these tests; the agent does not touch the phone. After the run, tell the agent the
approximate time and ask for the log pull.

## Evidence already collected

- Review APK SHA-256 `a534437544a9ee72b2b9b0b489c93271da9c27705139646d6650a1704db01788`
  is installed on CPH2747.
- LSPosed: module enabled, scope `com.android.systemui`.
- `2026-11-04T21:56:37.000 E/LSPosedService-SafeMode: System UI crashed too many times, stop all
  modules and enter safe mode ...` occurred 0.4 s after
  `21:56:36.584 DuoSB | restart requested by the app - restarting System UI`.
- No SystemUI `FATAL`/`SIGSEGV`/`SIGABRT` stack is in the pulled logcat or crash buffer. The crash
  stack/tombstone from the restart loop is still missing.
- The diagnostic dump (`last_dump`, captured 21:56:20) shows the shade-header copy at
  `DuoRiveView id=0x5 141x159 @1044,-18` inside the stock header; with the header's screen offset
  this is the documented legacy shade bbox `(1102,112)-(1209,235)`. The main copy is
  `DuoRiveView id=0xf 141x159 @1044,-6` (documented `(1074,8)-(1181,131)`).
- The shade anchor code is identical to the legacy fix commit `0ffeb3f` (`restore legacy shade
  anchor position`) and to 1.6.0: `applyExtraLayout` → `anchorOnBattery(..., alignEnd = true)` with
  no `translationY` clamp. No code regression was found in that path.

## Before testing

1. Keep the phone awake and connected to Wi-Fi ADB `[redacted]`.
2. Confirm LSPosed has the module enabled for **System UI**. If safe mode disabled it, enable it
   once and restart System UI once; do not tap Restart repeatedly.
3. Do not clear logcat or the LSPosed logs.

## Test sequence

Run the steps in this order and write down the time of each step.

| # | Step | Evidence to capture |
|---|---|---|
| 1 | Home screen, current settings | Screenshot |
| 2 | Open the shade (first swipe) | Screenshot; if the Duo ring looks moved, leave it open |
| 3 | Open QS (second swipe) | Screenshot |
| 4 | Close the panel | Screenshot |
| 5 | Home → switch Ring design (Classic ↔ 2 SIM) once | Note the time |
| 6 | Tap **Restart System UI** once; wait 15 s; do not tap again | Note the time; screenshot after SystemUI returns |
| 7 | If a fatal/safe-mode screen appears, stop and note the time | Do not reboot repeatedly |
| 8 | Optional: Camera toggle, then open/close QS and shade | Screenshot + time |
| 9 | Optional: Size 0 and 100 | Screenshot + time |
| 10 | Optional: hide Battery, then restore it | Screenshot + time |

## After the run

Tell the agent:

- the time of the design switch/restart and any fatal/safe-mode event;
- whether the shade icon moved, and at which step;
- “pull the log”.

The agent will pull, without running new tests:

- `/data/adb/lspd/log` latest `modules_*` and `verbose_*`;
- `logcat -b crash -d` and `logcat -d` around the reported time;
- `/data/tombstones` entries for `systemui`;
- `/data/system/dropbox` `system_app_crash` / `system_app_anr` around the time;
- the app prefs `last_dump` and the current screenshots.

## Fix policy after the pull

- Fatal/safe mode: fix only from the crash stack/tombstone; do not guess.
- Shade position: compare the new `DuoRiveView` bounds with the legacy bbox above; change the anchor
  only when the measured delta is known.

## Build 2 — reverted

- Build 2 (`c1858418…b025`, Telegram 38) is **superseded**. The owner selected build 1
  (`a5344375…01788`) as the shade/QS/HUD reference; the later shade-parent change and its
  `SlotGeometryTest` were reverted.
- Use the build-1 APK already installed on CPH2747 for the shade/QS/HUD behavior. The code in the
  worktree now matches build 1; 449 unit tests pass.
- The earlier test request below remains valid for the fatal/safe-mode reproduction; do not install
  build 2.



## FILE: EMULATOR-CALL-TEST.md

# Emulator call test — PASS

Date: 2026-10-31. AVD `ae_api36_root`, LSPosed module enabled, `duo_statusbar_stage=2`.

## Incoming call

Injected through the emulator console:

```
adb -s emulator-5554 emu gsm call 12345
```

Telephony/Telecom reached `RINGING`, then the module log showed:

```
DuoSB: phone state=RINGING callEnabled=true
DuoSB: live event -> CALL
```

After the test:

```
adb -s emulator-5554 emu gsm cancel 12345
```

## Meaning

- The `TelephonyManager.ACTION_PHONE_STATE_CHANGED` receiver works.
- The call event is enabled (`callEnabled=true`).
- `EventArbiter` accepted `CALL` and the renderer received `live event -> CALL`.
- The event is a real incoming call, not an API/state shortcut.

## Limits

- This proves the event path on the AOSP-derived emulator; each vendor's Telephony/SystemUI must still
  be checked before claiming all eight UIs.
- The visual shake/art was not pixel-compared; the event reached the renderer.


## FILE: EMULATOR-LOCK-TEST.md

# Emulator lock → home test — PASS

Date: 2026-10-31. AVD `ae_api36_root` (API 36), LSPosed, SystemUI stage 2.

## Setup

- LSPosed module `io.github.kvmy666.duostatusbar` (`mid=45`) was disabled.
- Corrected:
  - `enabled=1`
  - `apk_path` refreshed to the installed APK
  - scope `45 | com.android.systemui | 0`
  - `duo_statusbar_stage=2`
- SystemUI restarted.

## Hooks installed

```
DuoSB: shade/QS coverage hook installed
DuoSB: bar appearance hook installed (StatusBarIconView) - FR-15b
DuoSB: flashlight event hook installed
DuoSB: screenshot event hook installed
DuoSB: keyguard unlock hook installed
```

## Transition test

- Set a real PIN (`locksettings set-pin 1234`).
- Locked/unlocked with the PIN.
- Log:

```
keyguard unlock hook installed
keyguard faded away -> unlock fallback
unlock received enabled=true active=null
live event -> UNLOCK
```

This is the lock screen → home transition, not an unlock API shortcut. The keyguard hook fires and the
UNLOCK event reaches the renderer.

## Scope

- The hook is `StatusBarKeyguardViewManager.onKeyguardFadedAway` with
  `notifyKeyguardAuthenticated(true)` as the first path. This is the AOSP-derived class present in six of
  the eight UI APKs; the other UIs still need their own runtime check before claiming support.
- Call/ringing remains unverified.
- Screenshot is separately verified through the shared `ScreenshotWatcher` file source.


## FILE: EMULATOR-TEST-MATRIX.md

# Emulator + cross-UI test matrix

Date: 2026-10-31. This is the current evidence matrix, not a claim for every vendor runtime.

## Runtime (emulator / phone)

| Scenario | Environment | Result | Log |
|---|---|---|---|
| Screenshot | CPH2747 | **PASS** | `live event -> SCREENSHOT source=file:/storage/emulated/0/Pictures/Screenshots/...` |
| Lock screen → home | API 36 emulator, PIN 1234 | **PASS** | `keyguard faded away -> unlock fallback` / `live event -> UNLOCK` |
| Incoming call | API 36 emulator, `gsm call 12345` | **PASS** | `phone state=RINGING callEnabled=true` / `live event -> CALL` |
| Flashlight | CPH2747 | event path works; art still evolving | `EventFlashlight` |
| Alarm | CPH2747 | event path works; art redrawn | `EventAlarm` |

## Cross-UI static matrix

| UI | Keyguard class + 2 methods | AOSP screenshot executor | Screenshot file watcher |
|---|---|---|---|
| OnePlus | yes | yes | yes (shared) |
| Samsung | yes | yes | yes (shared) |
| Xiaomi | yes | yes (+ MIUI helper) | yes (shared) |
| vivo | yes | yes | yes (shared) |
| Motorola | yes | yes | yes (shared) |
| Nothing | yes | yes | yes (shared) |
| OPPO 3.0.4 | yes | no (old) | yes (shared) |
| Honor 11 | yes | no (old) | yes (shared) |

Static presence is not runtime proof. The keyguard and file-watcher paths are vendor-neutral; the AOSP
screenshot executor is a bonus source for six UIs.

## Remaining gate

- Runtime lock/call on the other seven vendor ROMs.
- Mic/location/volume sources.
- True gradient/blur flashlight glow.
- Device performance (`p95`), visual owner approval, stable release.


## FILE: EVENT-ARCHITECTURE.md

# Live-event architecture — Duo Status Bar

Status: **alarm and flashlight implemented in Rive + Canvas; screenshot/mic/location pending; device
verification pending.** This file exists so the interactive-icon work is built as one system, not as
ad-hoc toggles. It records the owner's classification, the requirements it must satisfy, the unresolved
questions, and the proposed workflow/test path.

Project-memory key: `event_interactive_icons_architecture`.

## 1. Owner classification

| Class | Icons / states | Behaviour |
|---|---|---|
| **Fixed / stateful** | Wi-Fi, 5G/4G, airplane, clock, battery percentage | Occupies the middle slot while its state is true; changes only on a state change. |
| **Live event** | alarm, screenshot, microphone, location, volume, unlock (P1), charging (already exists) | Event fires -> current middle occupant fades -> event icon appears and plays its Rive motion -> icon fades -> the previous middle occupant returns. |
| **Per-icon user choice** | every icon above, plus stock icons | The user can show or hide each one; no forced hiding. |

The distinction is important: a fixed icon is a **state occupant**; a live icon is an **event
presentation** with a start, an animation, an end, and a guaranteed return.

## 2. Requirements this must satisfy

- **LIVE-001**: alarm, unlock, charging, screenshot, volume, location, microphone.
- **LIVE-002**: P0 = charging, alarm, screenshot, mic, location; P1 = volume, unlock.
- **LIVE-003**: event fires -> current middle occupant fades -> event icon + Rive animation; Canvas
  clone matches.
- **LIVE-004**: per-icon motion (alarm bounce/shake, screenshot flash, volume pulse, etc.).
- **LIVE-005**: SystemUI hooks first; UsageStats/NotificationListener only with explicit consent;
  Accessibility only if no alternative.
- **LIVE-006**: missing permission/source -> normal Duo state + reason log; never crash.
- **LIVE-007**: event-driven only; no polling; renderer paused while idle.
- **RIV-004/RIV-005/RIV-008**: defined motion, Canvas parity, headless + device evidence.
- **CUS-006**: per-icon show/hide remains a user choice.
- **TST-005/TST-009**: structured logs and no PII (no notification text, location, SSID, etc.).

## 3. Current facts (verified in the repository; not assumptions)

- `DuoVisual` currently has a single middle-slot state: `middleMode` 0..4 and `networkText` for the
  cellular generation. It has no event type, event phase, or event icon.
- The Rive scene has a `MiddleSlot` layer with Wi-Fi/airplane/DND/network states. It has **no alarm,
  screenshot, microphone, location, or volume art/state**.
- `DuoCanvasView` draws the same middle occupants from `DuoVisual`; there is no event overlay.
- `DuoStateMonitor` already handles charging, battery, DND, airplane, Wi-Fi/cellular, screen on/off,
  download progress, and bar tint. Charging is a boolean, not a generic event.
- The app can already choose the middle occupant (`middleSlot`) and hide the Wi-Fi glyph/cellular dots.
- **There is no verified alarm-ringing source, screenshot source, mic/location source, or volume
  source wired into the module.** This is the first place where guessing would be dangerous.

## 3.1 Owner decisions (2026-10-28)

These are now requirements, not open choices:

1. **Alarm** appears both when it is **set** and when it **rings**. Use the Android/SystemUI source,
   not a per-app alarm package (alarm apps differ across UIs; there is no single alarm package).
   The owner explicitly authorized an actual on-device measurement of the ringing/end source.
   **Final owner decision (2026-10-28): do not tie the event to notification/ring end.** The alarm
   event is a one-shot animation until its own end, then fades and restores the middle state.
2. **Newest event wins**: a new event cancels the current event and takes its place immediately.
3. **Duration is per event type**:
   - Alarm: one-shot animation duration (prototype ~1.2 s), **not** while-ringing and **not** tied
     to notification removal.
   - Flashlight: while the torch is active; hide when it turns off.
   - Transient events (screenshot): a short fixed lifetime (~1 s or the source's active window).
   - Rotating/state icons (Wi-Fi, 5G/4G, airplane, clock, battery %) are not events.
4. **Animation is per event type, not per icon.** Multiple icon variants share the same event
   animation (`icon -> animation` map). 16 icons and 4 animations are 4 animations plus a mapping/testing
   matrix, not 64 authored animations.
5. **Same behaviour on all three surfaces:** status bar, keyguard and shade/QS copies must show the
   same event state/animation/return; no surface-specific event logic.
6. **Return to the current middle state** at event end, not the state captured at event start.
7. **Prototype scope:** alarm + flashlight + screenshot, each with its own animation; volume/unlock
   wait until the P0 event layer is stable.
8. **One icon shape for now, extensible to four:** the event model must accept an icon variant/slot
   list later without changing animations or event logic. Start with one default shape per event.
9. **Per-event toggle:** each event has its own user switch; one global switch is not enough.
10. **Render sizing:** the event icon scales with the ring's current size, and stays inside the ring
   bounds. Alarm motion is a jump/shake inside the ring; flashlight is a pressed button that remains
   pressed while torch is on, then fades after the press-off.
9. **Use system/SystemUI sources first.** Do not integrate every third-party alarm/clock app; v1 stays
   hook/SystemUI-only and degrades when a source is unavailable.
10. **Icons come from the device/SystemUI resources** for the prototype. OEM assets are prototype-only
    (UI-013); release must use owned/redrawn art or an approved licensed set.

## 3.2 Measured system-source evidence (OnePlus SystemUI APK)

APK: `corpus/SystemUI-device.apk`, SHA-256 `dbe67fd3688a5bb5ebe7200190aaaac0bfbf88068a1534b6aebd62cd3f588ff4`.
Static evidence only; runtime behavior must still be proven on device.

### Next alarm / alarm icon

`com.android.systemui.statusbar.policy.NextAlarmControllerImpl`:

- Constructor receives `android.app.AlarmManager` and `BroadcastDispatcher`.
- Field `mNextAlarm: AlarmManager.AlarmClockInfo`.
- `onReceive(Context, Intent)` handles the exact action string
  `android.app.action.NEXT_ALARM_CLOCK_CHANGED` and calls `updateNextAlarm()`.
- `updateNextAlarm()` calls `mAlarmManager.getNextAlarmClock(userId)` and notifies
  `NextAlarmController.NextAlarmChangeCallback.onNextAlarmChanged(mNextAlarm)`.

Consequence: this is the system-wide alarm source, independent of the alarm app package. It tells us
the **next alarm** (set state). It does **not** by itself prove a separate "currently ringing" signal;
that exact end-of-ring transition still needs a measured source (status-bar alarm icon state, alarm
broadcast, or a SystemUI callback). Do not invent one.

**Measured on CPH2747, 2026-10-28 15:09 +03 (12:09Z):**

- Before: next alarm `15:09:00`, `next_alarm_formatted=Wed 3:09 PM`.
- At `15:09:00.020` the system delivered `android.app.action.NEXT_ALARM_CLOCK_CHANGED` with the old
  alarm cleared (`nextUserAlarmTime=0`), then at `15:09:00.103` delivered it again with the next alarm
  `17:33`; `dumpsys alarm` confirmed the next alarm moved to `17:33`.
- The alarm app's foreground service (`AlarmService`, code `ALARM_MANAGER_ALARM_CLOCK`) started,
  vibration with `USAGE=ALARM` was observed, and the heads-up dismissed around `15:09:05.736`.
- **No** `PhoneStatusBarPolicy`/status-bar alarm-icon line appeared, and no system end-of-ring
  broadcast was observed.
- Conclusion: `NextAlarmControllerImpl` covers the **set state and ring-start transition**, not a
  distinct ringing/end signal. Ringing/end must be sourced from another measured system signal; the
  candidates are SystemUI notification entry (`CATEGORY_ALARM`/alarm FGS), `AudioManager`
  playback callback (`USAGE_ALARM`), or a status-bar icon observation. Do not implement the end on
  the NextAlarm callback alone.

**Measured generic notification evidence (same ring):**

- `NotificationService PostNotification ... prayer_alerts` at `15:09:00.100–00.314`.
- SystemUI `NotificationPipeline` processed the post; heads-up and `removeLiveAlert` appeared.
- At `15:09:05.109–05.120` the FGS stopped and the notification was removed/dismissed
  (`NotifDismissalHandler`, `NotificationPipeline` dismissal, `notificationRemoved`), followed by
  vibration cancel at `15:09:06.122`.
- **Correction from the same logs:** the posted prayer-alarm notification did **not** carry
  `Notification.CATEGORY_ALARM`; Oplus tracker recorded `notification_type=others`, the FGS had no
  type, and the SystemUI `alarm_clock` slot has `notification=null` (it is driven by
  `NextAlarmController`/AlarmManager). Therefore `CATEGORY_ALARM` is not a reliable end key on this
  alarm app. The only generic signals observed were the FGS code `ALARM_MANAGER_ALARM_CLOCK` and the
  notification removal of that FGS notification; both need a generic, non-package SystemUI accessor
  before they can be used. Do not implement per-app package matching.

**Static SystemUI hooks for the notification source:**

- `com.android.systemui.statusbar.notification.collection.NotifCollection`:
  `postNotification(StatusBarNotification, Ranking)` and
  `m6178$$Nest$monNotificationRemoved(StatusBarNotification, RankingMap, int)`.
- `NotifPipeline.addCollectionListener(NotifCollectionListener)` / `removeCollectionListener`.
- The adapter must filter by `Notification.CATEGORY_ALARM` and/or the SystemUI alarm FGS marker;
  record the exact category in the prototype measurement. Do not filter by the alarm app package.

- Evidence dump: `from users/alarm-20261028/` (CPH2747, 2026-10-28 15:09 +03):
  `duo-alarm-after-screen.png`, `duo-alarm-after.txt`, `duo-alarm-before.txt`,
  `duo-alarm-logcat-after.txt`, `duo-alarm-logcat.txt`, `duo-alarm-ring.txt`,
  `duo-alarm-screen.png`, `duo-alarm-systemui.txt`, `duo-alarm-wait-1.png`.

### Flashlight / torch

`com.android.systemui.statusbar.policy.FlashlightControllerImpl`:

- Constructor uses `android.hardware.camera2.CameraManager`.
- Tracks `mFlashlightEnabled`, `mTorchCallback`, `mListeners`, `mHasFlashlight`.
- Exposes `addCallback`/`removeCallback`, `isEnabled()`, `isAvailable()`, `setFlashlight(...)`, and
  `dispatchListeners(int, boolean)`.
- A camera torch callback updates the enabled state and dispatches listeners.

Consequence: flashlight is a **stateful event** (while active), not a one-shot; the verified source
is the SystemUI flashlight controller/camera torch callback, not a third-party app.

**Measured on CPH2747, 2026-10-28 (flashlight toggles):**

- The Duo hook (`FlashlightControllerImpl` / `dispatchListeners`) loaded in **4 SystemUI processes**;
  the latest hook line is at `16:05:31.582` (pid `20976`).
- `live event -> FLASHLIGHT` fired **6 times**, each followed by `live event -> none` when the torch
  was turned off (while-active semantics confirmed: on -> off -> fade/restore).
- The alarm hook (`AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED`) was installed in the same window but
  **no alarm broadcast was delivered**; the next alarm was in the future, so no `live event -> ALARM`
  fired. This confirms the flashlight source fires independently of the alarm source and does not
  depend on an alarm broadcast.

### Prototype icon candidates (decoded resources)

Paths from `/tmp/duo-systemui-res` (proprietary OEM resources; prototype-only):

| Event | Candidate | Type | Dimensions |
|---|---|---|---|
| Alarm (status) | `res/drawable/stat_sys_alarm.xml` | Vector | 19×16 dp |
| Alarm (AOD) | `res/drawable/ic_alarm.xml` | Vector | 17×17 dp |
| Alarm (big) | `res/drawable/ic_access_alarms_big.xml` | Vector | 18×18 dp |
| Flashlight | `res/drawable/ic_flashlight_off.xml` | Vector | 24×24 dp |
| Flashlight active | `res/drawable/status_bar_qs_flashlight_active.xml` | Vector | 24×24 dp |
| Flashlight inactive | `res/drawable/status_bar_qs_flashlight_inactive.xml` | Vector | 24×24 dp |
| Camera/privacy | `res/drawable/privacy_icon_camera.xml` | Vector | 16×16 dp |
| Camera/privacy | `res/drawable/icon_camera_in_use.xml` | Vector | 24×24 dp |

Import these into a prototype-only asset folder, not the release resource set. Measure the actual
rendered pixels before choosing the final artwork.

## 4. Proposed components and ownership

```
event source adapter
      |
      v
LiveEventMonitor  (dedupe, rate limit, consent, source -> event)
      |
      v
EventArbiter      (priority, single active event, queue/coalesce, duration)
      |
      v
EventPresenter    (event -> DuoVisual + Rive/Canvas contract)
      |
      +--> Rive Event layer  (one-shot icon motion, returns to state)
      |
      +--> Canvas event clone (same geometry/opacities/timing)
      |
      v
DuoStateMonitor -> host.render -> status bar / keyguard / shade
```

### 4.1 Data contract

```kotlin
enum class LiveEventType { NONE, ALARM, SCREENSHOT, MIC, LOCATION, VOLUME, UNLOCK }

data class LiveEvent(
    val type: LiveEventType,
    val priority: Int,          // resolved by the arbiter, not by the source
    val source: String,         // structured reason, never PII
    val startedAtElapsed: Long,
    val durationMs: Long?       // null = while the source is active
)
```

`DuoVisual` should gain only presentation fields, not event logic:

```kotlin
val eventType: Int = 0        // 0 none; Rive/Canvas mapping table owns the rest
val eventPhase: Float = 0f    // 0..1 within the event, for Canvas; Rive owns its easing
val eventVisible: Boolean = false
```

The event **duration** and **priority** stay in Kotlin. Rive owns the motion/easing of the icon; one
property, one owner. The presentation returns to the previous middle state when the arbiter says the
event ended. No two active animations own the same property.

### 4.2 Event arbiter

- One active presentation at a time.
- Fixed middle occupant is captured as `previousMiddle`; it is restored on end.
- P0 event may preempt P1; P0 vs P0 needs an explicit policy (queue vs latest-wins).
- Repeated identical events within a debounce window are coalesced.
- A maximum event duration prevents a lost end-broadcast from pinning an icon forever.
- Screen off/teardown clears the active event and restores the state on next attach.

### 4.3 Icon variants vs animation ownership (owner clarification)

The **animation belongs to the event type, not to an individual icon**. Multiple icon variants (for
example four alarm shapes in an icon pack) share the same alarm animation.

- `EventType` owns the animation (alarm bounce/shake, screenshot flash, volume pulse, ...).
- `EventIcon` is a replaceable artwork slot. An icon pack maps each icon to an `EventType`.
- A Rive event plays transform/alpha/motion around the icon slot; it does **not** require a separate
  animation per icon.
- 16 icons and 4 animations do not mean 64 animations. It means 4 animations and an
  `icon -> animation` map. The combination matrix is a **test** concern (each icon must play its
  assigned animation and return correctly), not an authoring multiplication.
- If an icon has no assigned animation, use the event type's default; never duplicate an animation
  per icon.

### 4.4 Rive contract (to be authored, not guessed)

A new `Event` layer (or a dedicated state machine) with:

- `eventType` number property: 0 none, 1 alarm, 2 screenshot, 3 mic, 4 location, 5 volume, 6 unlock.
- An `eventIcon`/artwork selector boundary: icon swap is independent of the event animation.
- A trigger/phase input; the transition itself is one-shot.
- Return transition to the current `MiddleSlot` state (Wi-Fi / network / airplane / DND / clock /
  percent / charging).
- No idle loop; pause when hidden or idle.

The alarm shape/icon must come from an owned asset or a drawn vector. Do not copy OEM art into a
release build (UI-013); the corpus is prototyping only.

### 4.5 Canvas contract

- Draw the selected icon geometry at the same seat and scale.
- Use the event-type timeline (not a per-icon timeline); an icon swap only changes the drawing, never
  the motion contract.
- Same fade-in/flash/bounce/pulse timing as the selected event.
- Same return to the previous middle state.
- A pure `EventGeometry`/timeline helper so unit tests can assert phases without a bitmap.

### 4.6 Icon-pack assignment table (to be filled from the owner's packs)

| Icon variant | Event type | Animation | Evidence |
|---|---|---|---|
| Alarm A/B/C/D | ALARM | alarm bounce/shake | icon asset + headless capture |
| Screenshot A/B/... | SCREENSHOT | screenshot flash | icon asset + headless capture |
| Mic A/B/... | MIC | mic pulse | icon asset + headless capture |
| Location A/B/... | LOCATION | location pulse | icon asset + headless capture |
| Volume A/B/... | VOLUME | volume pulse | icon asset + headless capture |

The table is the single source of truth for the icon → animation mapping; adding an icon does not
add an animation, only a row.

## 5. Event source strategy (evidence first)

| Event | Candidate source | Status / why it is not wired |
|---|---|---|
| Charging | Existing battery sticky broadcast | Works; keep as baseline. |
| Alarm | `StatusBarIconView` alarm icon, `AlarmManager` next-alarm state, or alarm app notification | **Unknown**: next-alarm icon is not the same as ringing; NotificationListener needs consent. Must be measured. |
| Screenshot | SystemUI screenshot controller / screenshot UI hook | **Unknown**: version/class differs; needs decompilation of CPUI/SystemUI. |
| Microphone | Privacy indicator / status icon / AppOps | **Unknown**: permission/privacy surface; must not log app names or audio. |
| Location | Privacy indicator / status icon / AppOps | **Unknown**: same privacy rule; no coordinates. |
| Volume | `AudioManager` volume-change broadcast/stream | Feasible, but needs a decision on which streams and debounce. |
| Unlock | Existing `ACTION_USER_PRESENT` | P1; already observed for reveal, needs event semantics. |

No source may be guessed from a class name. Each source gets an adapter, a measured evidence row, and
a fallback row in the test matrix.

## 6. Return / priority / duration decisions still needed

Proposed defaults (not approved):

- Alarm: while ringing, plus a bounded tail; motion = bounce/shake.
- Screenshot: ~800 ms flash.
- Mic/Location: while the privacy indicator is active; bounded maximum.
- Volume: ~1.2 s pulse.
- Unlock: ~1 s, P1.
- Charging: existing animation; not an event takeover.
- New P0 while another P0 is active: latest-wins with a short cross-fade, unless the owner chooses a
  queue.
- New P1 while P0 active: drop/coalesce; P1 does not interrupt P0.
- Return: always to the **current** middle state (not the state at event start if Wi-Fi/airplane/
  clock changed during the event).

## 7. Test path (must exist before implementation is called done)

### Unit / pure
- Arbiter: priority, preemption, queue/coalesce, debounce, max duration, clear on teardown.
- Mapping: event -> visual fields; return to prior middle; no event = byte-identical old output.
- Timeline/geometry: phases for alarm bounce, screenshot flash, volume pulse.
- Consent: missing/denied source -> no event + reason, no crash.
- Redaction: no SSID, IMEI, serial, notification text, location, app name in event logs.

### Robolectric / instrumentation
- Broadcast/receiver lifecycle: start/stop, duplicate broadcasts, screen off/on, rotation.
- Attach/hide/restore with an active event.
- App settings: per-event toggle, duration, priority display; backup round-trip includes them.

### Rive headless
- `rive --verify` after every scene change.
- `--screenshot` for each event at start/mid/end and each return state.
- Hash compare `duo.riv` with a fresh build of `scene.rml`.

### Device (emulator first; phone only when the owner says so)
- TP matrix: every event x status bar/keyguard/shade x Rive/Canvas x light/dark.
- Trigger each event via a debug-only source; capture screenshots/video and logs.
- Rapid events, event during charging/DND/airplane/clock, rotation, screen off, QS/shade open.
- Performance: no idle loop, p95 frame <=16.7 ms, memory/battery within budgets.
- Failure: source unavailable, permission denied, binding timeout, renderer fallback.

## 8. Requirement-to-test map

| Requirement | Test evidence |
|---|---|
| LIVE-001/LIVE-002 | Event source matrix + priority unit tests |
| LIVE-003 | Rive headless + Canvas timeline + device capture |
| LIVE-004 | Per-event headless screenshots + Canvas phase tests |
| LIVE-005 | Consent/failure tests; hook evidence in repair catalog |
| LIVE-006 | Missing-source/permission tests; no crash |
| LIVE-007 | Receiver-only code audit + gfxinfo/battery |
| CUS-006 | Per-event/per-icon toggles + preview |
| TST-005/TST-009 | Structured log + redaction tests |
| RIV-004/RIV-005/RIV-008 | Headless + Canvas parity + device screenshots |
| Icon variants | Each icon plays its assigned event animation; no per-icon animation duplication |

## 9. Implementation sequence (after questions are answered)

1. Write the event contract (`LiveEventType`, `LiveEvent`, `EventArbiter`) and pure tests; no hooks.
2. Add `DuoVisual` fields and Canvas event clone; keep default output identical.
3. Add one source adapter at a time (volume/unlock are easiest; alarm/mic/location need measured
   SystemUI evidence).
4. Author the Rive `Event` layer and rebuild `duo.riv`; verify hash; add headless screenshots.
5. Wire the app settings (per-event toggle, preview, debug trigger) and backup schema.
6. Run unit, Robolectric, headless, emulator; phone only with owner approval.
7. Add repair-catalog entries and evidence paths.

## 10. Questions — resolved and still open

**Resolved by the owner:**

- Alarm: set **and** ringing; use Android/SystemUI, not per-app packages.
- Preemption: newest event cancels the previous one and takes its place.
- Durations: alarm while ringing + ~1 s intro; flashlight while active; transient events per source.
- Animation: per event type, not per icon; icons map to an animation.
- Icons: take them from the phone/SystemUI for the prototype; OEM assets are prototype-only.
- Clock: treated as a fixed middle occupant (not an event).

**Resolved after the architecture review:**

- Three surfaces: status bar, keyguard and shade/QS, same behavior.
- Return to the current middle state.
- Prototype: alarm + flashlight + screenshot.
- Event icon scales with the ring; alarm jumps/shakes within the ring bounds; flashlight is a pressed
  button while torch is on, then fades after press-off.
- v1 is SystemUI/hook-only with graceful degradation.

**Final alarm-end decision (owner, 2026-10-28):** do not use a notification/ring-end source. Alarm is
a one-shot event with a fixed animation duration (prototype `1200 ms`), then it fades and restores the
current middle state. The measured notification evidence above is retained as history, not as a
dependency. No `NotifCollection` probe is installed.

**Still open (not blocking the animation prototype):**

1. **Icon inventory:** which icon variants exist in the first pack (for example the four alarm
   shapes), and the exact `icon -> event type` mapping. The animation is per event type; the icon
   list/assets are still needed. Start with one shape (`iconKey="default"`).
2. **Screenshot source:** the exact SystemUI screenshot controller/UI hook on CPH2747 is not yet
   measured; do not invent it.
3. **Location/mic source:** privacy-indicator/status-icon source is not yet measured; keep for after
   the three-event prototype.
4. **Per-event switch UI** is implemented; previews/question marks are still Phase 3 UI work.

## 10.1 First implementation slice (status: alarm + flashlight implemented, device pending)

Runtime log check (2026-10-28 20:13 report) diagnosed the earlier build: the module was on Rive and the
flashlight source fired, but the Event layer only had an ALARM transition (`eventType == 1`), so
`eventType == 7` could not render. That gap is now closed.

Completed:

- `LiveEvent`/`EventArbiter` contract + unit tests, no hooks (`377bf7a`, newest-wins).
- Per-event live toggles (`f83b931`).
- Flashlight adapter, verified `FlashlightControllerImpl` source, while-active semantics (`f8995e5`).
- Alarm one-shot adapter via `AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED`; `1200 ms` transient,
  no notification/ring-end dependency; log-only.
- Rive `EventFlashlight` state + `EventFlashlight`/`EventFade` animations; `EventAlarm` now also
  clears the flashlight artwork and shows the alarm artwork, so the two event shapes never overlap.
  Flashlight press is 12 frames (200 ms), then holds for as long as the torch is on; off fades in
  12 frames and returns the current middle occupant.
- Canvas event clone draws the same alarm/flashlight silhouettes in the Rive `eventGroup` seat,
  hides the normal middle occupant while the event is visible, and leaves ring/signal dots intact.
- Owner feedback (CPH2747, 1.4.3-beta.2): event icons were still too small and sat low; Wi-Fi/5G
  size and center were right. The event slot moved to y=1 (the 5G text origin, not the Wi-Fi arc
  origin at 17), and the artwork was scaled to 2.0x (alarm ~40 units high, flashlight ~42 x 28) in
  both Rive and Canvas. Version bumped to 1.4.3-beta.4 (code 27).
- Flashlight false-positive fixed: `dispatchListeners(int, boolean)` is multiplexed; only `what == 1`
  is the torch state. The hook now ignores availability/night-light/error callbacks (RC-015).
- Screenshot event added: `TakeScreenshotExecutorImpl.executeScreenshotsAsync` is the shared request
  entry; one 1000 ms transient, an owned outline-camera Rive state (`EventScreenshot`, 60 frames) and
  the same Canvas clone. This is static/REA evidence; on-device verification is pending (RC-016).
- Simulation section added to Settings: pick Alarm or Flashlight, then play the real Duo Rive event
  animation. The icon row is the selected event prototype; OEM reference vectors stay archived in
  `agent/event-icons/prototype/` and are not packaged (UI-013).
- Unit test: `DuoMappingTest.event fields pass through without changing the middle occupant`.
- Rive headless evidence: idle Wi-Fi, alarm at 400 ms, flashlight at 1 s; `--verify` clean;
  `tools/check-rive-eases.py rive/duo/scene.rml` 132/132 eased, 0 inert; log-route check clean.
- `duo.riv` SHA-256 `540bb0884324b1f4303b22debdfb943cb4ad9bc04ce3fba0cacaa3896269e201`
  (build identical to `app/src/main/res/raw/duo.riv`).
- 317 tests, 0 failures; APK `e4effec1fbc1396a4a74056601e949cf03eb2305aa75d5eb985ba241b0431a3a`.

Pending:

- Microphone and location sources + their event artwork/animations (screenshot is now implemented).
- Device evidence: alarm set/ring -> animation end; flashlight on -> off; newest-wins; return to
  current middle state on status bar, keyguard and shade.
- Owned release art (OEM prototype assets remain UI-013-only).

Event icon shape seam defaults to one variant, extensible to four, without changing animations or
event logic; per-event switches remain independent.

## 11. Final decision (2026-10-28)

- No notification/ring-end source; alarm is one-shot 1200 ms; flashlight while active.
- Alarm probe removed; NextAlarm is the only start source.
- Rive and Canvas both render alarm + flashlight; screenshot/mic/location remain next.
- 317 tests; APK `e4effec1fbc1396a4a74056601e949cf03eb2305aa75d5eb985ba241b0431a3a`.



## FILE: EVENT-ICON-CANDIDATES.md

# Event icon candidates — phone + eight UIs

Generated from `reverse/icon-packs/manifest.json`; assets remain gitignored. Each cell is the best 1–2 candidate filenames, not a final selection.

## wifi

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/wifi/ic_wifi_signal_0.xml<br>oneplus-phone/wifi/ic_wifi_signal_1.xml |
| OnePlus 16.00.14-2 | oneplus/wifi/ic_wifi_signal_0.xml<br>oneplus/wifi/ic_wifi_signal_1.xml |
| Samsung 16-11 | samsung/wifi/stat_sys_wifi_signal_0.xml<br>samsung/wifi/stat_sys_wifi_signal_1.xml |
| Xiaomi 17-03 | xiaomi/wifi/stat_sys_vowifi.xml<br>xiaomi/wifi/stat_sys_wifi_in.xml |
| vivo 16.0.7.194 | vivo/wifi/ic_signal_wifi_off.xml<br>vivo/wifi/ic_show_x_wifi_signal_0.xml |
| Motorola 15.7 | motorola/wifi/stat_sys_vowifi.xml<br>motorola/wifi/stat_sys_vowifi_not_ready.xml |
| Nothing 15.0.176 | nothing/wifi/stat_sys_wifi_4_hotspot.xml<br>nothing/wifi/stat_sys_wifi_5_hotspot.xml |
| OPPO 3.0.4 | oppo/wifi/stat_sys_wifi_in.xml<br>oppo/wifi/stat_sys_wifi_out.xml |
| Honor 11.0.0.1 | honor/wifi/stat_sys_wifi_in.xml<br>honor/wifi/stat_sys_wifi_out.xml |

## signal

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/signal/stat_sys_signal_0.xml<br>oneplus-phone/signal/stat_sys_signal_1.xml |
| OnePlus 16.00.14-2 | oneplus/signal/stat_sys_signal_0.xml<br>oneplus/signal/stat_sys_signal_1.xml |
| Samsung 16-11 | samsung/signal/stat_sys_signal_0.xml<br>samsung/signal/stat_sys_signal_1.xml |
| Xiaomi 17-03 | xiaomi/signal/stat_sys_signal_0.xml<br>xiaomi/signal/stat_sys_signal_1.xml |
| vivo 16.0.7.194 | vivo/signal/ic_signal_strength_zero_bar_no_internet.xml<br>vivo/signal/vivo_no_signal.xml |
| Motorola 15.7 | motorola/signal/ic_signal_strength_zero_bar_no_internet.xml<br>motorola/signal/zz_moto_ic_qs_signal_in.xml |
| Nothing 15.0.176 | nothing/signal/ic_roaming_signal.xml<br>nothing/signal/ic_signal_strength_zero_bar_no_internet.xml |
| OPPO 3.0.4 | oppo/signal/stat_sys_signal_in.xml<br>oppo/signal/stat_sys_signal_out.xml |
| Honor 11.0.0.1 | honor/signal/stat_sys_signal_0.xml<br>honor/signal/stat_sys_signal_1.xml |

## battery

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/battery/ic_battery_unknown.xml<br>oneplus-phone/battery/status_bar_qs_battery_active.xml |
| OnePlus 16.00.14-2 | oneplus/battery/ic_battery_unknown.xml<br>oneplus/battery/status_bar_qs_battery_active.xml |
| Samsung 16-11 | samsung/battery/stat_sys_battery_over_95.xml<br>samsung/battery/stat_sys_battery_warning.xml |
| Xiaomi 17-03 | xiaomi/battery/stat_sys_battery.webp<br>xiaomi/battery/stat_sys_battery.webp |
| vivo 16.0.7.194 | vivo/battery/ic_battery_unknown.xml<br>vivo/battery/vivo_battery.xml |
| Motorola 15.7 | motorola/battery/ic_battery_unknown.xml<br>motorola/battery/ic_setting_battery.xml |
| Nothing 15.0.176 | nothing/battery/ic_battery_alert.xml<br>nothing/battery/ic_battery_pause.xml |
| OPPO 3.0.4 | oppo/battery/battery.png<br>oppo/battery/battery.png |
| Honor 11.0.0.1 | honor/battery/stat_sys_battery_new_svg.xml<br>honor/battery/stat_sys_battery_new_save_svg.xml |

## airplane

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/airplane/stat_sys_airplane_mode.xml<br>oneplus-phone/airplane/status_bar_qs_airplane_active.xml |
| OnePlus 16.00.14-2 | oneplus/airplane/stat_sys_airplane_mode.xml<br>oneplus/airplane/status_bar_qs_airplane_active.xml |
| Samsung 16-11 | samsung/airplane/stat_sys_airplane_mode.xml<br>samsung/airplane/qs_airplane_icon_on.xml |
| Xiaomi 17-03 | xiaomi/airplane/stat_sys_airplane_mode.xml<br>xiaomi/airplane/ic_qs_lite_airplane.xml |
| vivo 16.0.7.194 | vivo/airplane/stat_sys_airplane_mode.xml<br>vivo/airplane/qs_airplane_icon_on.xml |
| Motorola 15.7 | motorola/airplane/stat_sys_airplane_mode.xml<br>motorola/airplane/ic_setting_airplane.xml |
| Nothing 15.0.176 | nothing/airplane/stat_sys_airplane.xml<br>nothing/airplane/stat_sys_airplane_mode.xml |
| OPPO 3.0.4 | oppo/airplane/stat_sys_airplane_mode.xml<br>oppo/airplane/oppo_ic_sysbar_airplane_on.png |
| Honor 11.0.0.1 | honor/airplane/stat_sys_airplane_mode.xml<br>honor/airplane/ic_airplanemode_tile_on.xml |

## dnd

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/dnd/stat_sys_dnd.xml<br>oneplus-phone/dnd/ic_qs_dnd_on.xml |
| OnePlus 16.00.14-2 | oneplus/dnd/stat_sys_dnd.xml<br>oneplus/dnd/ic_qs_dnd_on.xml |
| Samsung 16-11 | samsung/dnd/stat_sys_dnd.xml<br>samsung/dnd/stat_sys_do_not_disturb_mode.xml |
| Xiaomi 17-03 | xiaomi/dnd/stat_sys_dnd.xml<br>xiaomi/dnd/ic_qs_dnd_on.xml |
| vivo 16.0.7.194 | vivo/dnd/stat_sys_dnd.xml<br>vivo/dnd/ic_qs_dnd_on.xml |
| Motorola 15.7 | motorola/dnd/stat_sys_dnd.xml<br>motorola/dnd/ic_qs_dnd_on.xml |
| Nothing 15.0.176 | nothing/dnd/stat_sys_dnd.xml<br>nothing/dnd/ic_qs_dnd_on.xml |
| OPPO 3.0.4 | oppo/dnd/stat_sys_dnd.xml<br>oppo/dnd/ic_dnd.xml |
| Honor 11.0.0.1 | honor/dnd/stat_sys_dnd.xml<br>honor/dnd/stat_sys_dnd_total_silence.xml |

## alarm

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/alarm/stat_sys_alarm.xml<br>oneplus-phone/alarm/ic_alarm.xml |
| OnePlus 16.00.14-2 | oneplus/alarm/stat_sys_alarm.xml<br>oneplus/alarm/ic_alarm.xml |
| Samsung 16-11 | samsung/alarm/stat_sys_alarm.xml<br>samsung/alarm/stat_sys_alarm_dim.xml |
| Xiaomi 17-03 | xiaomi/alarm/stat_sys_alarm.xml<br>xiaomi/alarm/stat_sys_alarm_dim.xml |
| vivo 16.0.7.194 | vivo/alarm/stat_sys_alarm.xml<br>vivo/alarm/stat_sys_alarm_dim.xml |
| Motorola 15.7 | motorola/alarm/stat_sys_alarm.xml<br>motorola/alarm/stat_sys_alarm_dim.xml |
| Nothing 15.0.176 | nothing/alarm/stat_sys_alarm.xml<br>nothing/alarm/stat_sys_alarm_dim.xml |
| OPPO 3.0.4 | oppo/alarm/stat_sys_alarm.xml<br>oppo/alarm/stat_sys_alarm_dim.xml |
| Honor 11.0.0.1 | honor/alarm/stat_sys_alarm.xml<br>honor/alarm/stat_sys_alarm_dim.xml |

## flashlight

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/flashlight/ic_flashlight_off.xml<br>oneplus-phone/flashlight/status_bar_qs_flashlight_open.webp |
| OnePlus 16.00.14-2 | oneplus/flashlight/ic_flashlight_off.xml<br>oneplus/flashlight/status_bar_qs_flashlight_open.webp |
| Samsung 16-11 | samsung/flashlight/ic_flashlight_on.xml<br>samsung/flashlight/ic_flashlight_off.xml |
| Xiaomi 17-03 | xiaomi/flashlight/ic_flashlight_off.xml<br>xiaomi/flashlight/ic_qs_lite_flashlight.xml |
| vivo 16.0.7.194 | vivo/flashlight/ic_flashlight_on.xml<br>vivo/flashlight/ic_flashlight_off.xml |
| Motorola 15.7 | motorola/flashlight/ic_flashlight_off.xml<br>motorola/flashlight/qs_flashlight_icon_on.xml |
| Nothing 15.0.176 | nothing/flashlight/ic_flashlight_on.xml<br>nothing/flashlight/ic_flashlight_off.xml |
| OPPO 3.0.4 | oppo/flashlight/qs_tile_flashlight_anim.xml<br>oppo/flashlight/online_status_bar_flashlight.png |
| Honor 11.0.0.1 | honor/flashlight/ic_icon_flashlight.png<br>honor/flashlight/ic_flashlight_off2on.xml |

## volume

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/volume/ic_volume_off.xml<br>oneplus-phone/volume/ic_ring_volume.xml |
| OnePlus 16.00.14-2 | oneplus/volume/ic_volume_off.xml<br>oneplus/volume/ic_ring_volume.xml |
| Samsung 16-11 | samsung/volume/ic_volume_off.xml<br>samsung/volume/ic_ring_volume.xml |
| Xiaomi 17-03 | xiaomi/volume/ic_volume_off.xml<br>xiaomi/volume/ic_ring_volume.xml |
| vivo 16.0.7.194 | vivo/volume/ic_volume_off.xml<br>vivo/volume/ic_ring_volume.xml |
| Motorola 15.7 | motorola/volume/ic_volume_off.xml<br>motorola/volume/ic_ring_volume.xml |
| Nothing 15.0.176 | nothing/volume/ic_volume_off.xml<br>nothing/volume/ic_ring_volume.xml |
| OPPO 3.0.4 | oppo/volume/ic_volume_more.png<br>oppo/volume/ic_volume_more.png |
| Honor 11.0.0.1 | honor/volume/ic_volume_media.xml<br>honor/volume/ic_volume_voice.xml |

## location

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/location/stat_sys_location.xml<br>oneplus-phone/location/ic_location.xml |
| OnePlus 16.00.14-2 | oneplus/location/stat_sys_location.xml<br>oneplus/location/ic_location.xml |
| Samsung 16-11 | samsung/location/stat_sys_location.xml<br>samsung/location/stat_sys_privacy_location.xml |
| Xiaomi 17-03 | xiaomi/location/ic_location.xml<br>xiaomi/location/ic_my_location.xml |
| vivo 16.0.7.194 | vivo/location/ic_location.xml<br>vivo/location/qs_location_icon_on.xml |
| Motorola 15.7 | motorola/location/ic_location.xml<br>motorola/location/qs_location_icon_on.xml |
| Nothing 15.0.176 | nothing/location/stat_sys_location.xml<br>nothing/location/ic_location.xml |
| OPPO 3.0.4 | oppo/location/stat_sys_location.xml<br>oppo/location/qs_tile_location_anim.xml |
| Honor 11.0.0.1 | honor/location/stat_sys_location.xml<br>honor/location/ic_location.xml |

## mic

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/mic/stat_sys_mic_none.xml<br>oneplus-phone/mic/privacy_icon_microphone.xml |
| OnePlus 16.00.14-2 | oneplus/mic/stat_sys_mic_none.xml<br>oneplus/mic/privacy_icon_microphone.xml |
| Samsung 16-11 | samsung/mic/stat_sys_headset_mic.xml<br>samsung/mic/ic_mic_off.xml |
| Xiaomi 17-03 | xiaomi/mic/stat_sys_micphone.xml<br>xiaomi/mic/stat_sys_micphone_tint.xml |
| vivo 16.0.7.194 | vivo/mic/stat_sys_headset_mic.xml<br>vivo/mic/ic_mic_off.xml |
| Motorola 15.7 | motorola/mic/stat_sys_headset_mic.xml<br>motorola/mic/ic_mic_26dp.xml |
| Nothing 15.0.176 | nothing/mic/stat_sys_headset_mic.xml<br>nothing/mic/ic_mic_off.xml |
| OPPO 3.0.4 | oppo/mic/ic_mic_26dp.xml<br>oppo/mic/ic_headset_mic.xml |
| Honor 11.0.0.1 | honor/mic/stat_sys_mic_none.xml<br>honor/mic/ic_mic_26dp.xml |

## screenshot

| UI | candidates |
|---|---|
| CPH2747 (actual) | oneplus-phone/screenshot/ic_screenshot_edit.xml<br>oneplus-phone/screenshot/ic_screenshot_share.xml |
| OnePlus 16.00.14-2 | oneplus/screenshot/ic_screenshot_edit.xml<br>oneplus/screenshot/ic_screenshot_share.xml |
| Samsung 16-11 | samsung/screenshot/ic_screenshot_edit.xml<br>samsung/screenshot/ic_screenshot_share.xml |
| Xiaomi 17-03 | xiaomi/screenshot/ic_qs_screenshot.xml<br>xiaomi/screenshot/ic_screenshot_edit.xml |
| vivo 16.0.7.194 | vivo/screenshot/ic_screenshot_edit.xml<br>vivo/screenshot/ic_screenshot_share.xml |
| Motorola 15.7 | motorola/screenshot/ic_screenshot_edit.xml<br>motorola/screenshot/ic_screenshot_share.xml |
| Nothing 15.0.176 | nothing/screenshot/ic_screenshot_edit.xml<br>nothing/screenshot/ic_screenshot_share.xml |
| OPPO 3.0.4 | oppo/screenshot/ic_screenshot_edit.xml<br>oppo/screenshot/ic_screenshot_share.xml |
| Honor 11.0.0.1 | honor/screenshot/ic_screenshot_edit.xml<br>honor/screenshot/ic_screenshot_share.xml |

## Reading the names

- `stat_sys_*` is the closest match to a status-bar icon; `privacy_icon_*` is the privacy dot/mic/location glyph.
- `$$`/`$` names are vendor resource-obfuscation variants; do not treat them as design names.
- Vector XML is kept as-is; tint/state paths matter. Do not ship vendor assets without owner/licence approval.


## FILE: EVENT-ICONS-PULL-REPORT.md

# Event icon extraction — phone + eight UIs

Date: 2026-10-31. This is an extraction/inventory report, not a packaging change.

## Sources

- Phone: OnePlus CPH2747 over Wi-Fi ADB; `com.android.systemui` full split set pulled to `reverse/device-systemui/com.android.systemui/` (108 MB). SystemUI base SHA-256 `dbe67fd3688a5bb5ebe7200190aaaac0bfbf88068a1534b6aebd62cd3f588ff4`.
- Eight corpus APKs decoded with `apktool d -s --no-src`: Samsung 16-11, Xiaomi 17-03, vivo OriginOS 16.0.7.194, Motorola 15.7, Nothing 15.0.176, OnePlus 16.00.14-2, OPPO 3.0.4, Honor 11.0.0.1.
- Script: `tools/extract-status-icons.py`; copies drawable/mipmap/raw candidates and hashes them.
- Output: `reverse/icon-packs/` (gitignored), 5,353 files, 24 MB; manifest `reverse/icon-packs/manifest.json`.

No APK was installed, launched or tested on the phone. ADB was restored over Wi-Fi only for pulling.

## Counts per UI

| UI | wifi | signal | battery | airplane | dnd | alarm | flashlight | volume | location | mic | screenshot | total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| oneplus-phone | 87 | 146 | 42 | 24 | 22 | 8 | 60 | 97 | 33 | 47 | 13 | 579 |
| oneplus | 87 | 146 | 45 | 24 | 22 | 8 | 56 | 94 | 33 | 47 | 13 | 575 |
| samsung | 117 | 53 | 24 | 40 | 19 | 9 | 84 | 133 | 46 | 52 | 6 | 583 |
| xiaomi | 81 | 106 | 167 | 29 | 20 | 13 | 102 | 75 | 13 | 32 | 7 | 645 |
| vivo | 36 | 22 | 447 | 22 | 13 | 10 | 12 | 85 | 32 | 48 | 8 | 735 |
| motorola | 30 | 53 | 6 | 33 | 13 | 12 | 13 | 141 | 29 | 62 | 20 | 412 |
| nothing | 14 | 2 | 10 | 22 | 13 | 8 | 20 | 82 | 28 | 54 | 14 | 267 |
| oppo | 79 | 552 | 77 | 3 | 29 | 10 | 112 | 64 | 21 | 4 | 15 | 966 |
| honor | 72 | 122 | 92 | 5 | 5 | 6 | 39 | 55 | 176 | 6 | 13 | 591 |

## Notes

- `oneplus-phone` is the actual CPH2747 SystemUI, separate from the corpus OnePlus 16.00.14-2 pack; both are kept for comparison.
- Phone event candidates include `alarm/stat_sys_alarm.xml`, `flashlight/$$qs_flashlight_icon_on__0__0.xml`, `screenshot/status_bar_qs_partial_screenshot_active.xml`, `mic/$$qs_mic_access_off__*`, `location/$$qs_location_icon_on__*`, `dnd/ic_qs_dnd_on.xml`. They are vector XML with tint/state paths; this is why the pack keeps them as resources rather than flattening to PNG.
- Many status icons are Android vector XML, not PNG. The pack keeps vectors intact; do not rasterize blindly because tint/state paths are meaningful.
- `apktool` returned 1 for vivo/OPPO/Honor but still produced usable `res/` trees; the pack is marked with that limitation, not treated as a full decode.
- These are proprietary vendor assets. They stay under `reverse/` and are not packaged into `app/`; the prototype icons remain our own drawings. A license/owner decision is required before shipping any extracted art.
- Candidate event sets now available: flashlight (12–112 per UI), screenshot (6–20), alarm (6–13), DND (5–29), mic (4–62), location (13–176), airplane (3–40).


## FILE: EVIDENCE.md

# Evidence Protocol

## Tiers

| Tier | Meaning | Claim? |
|---|---|---|
| measured | Captured on a named device/build with the current code | Yes |
| log-confirmed | Proven by a dump/LSPosed log, not re-run after a change | Yes, with build named |
| public-source | AOSP/LineageOS source or public module | Only as a hypothesis |
| unverified | No device evidence | No |

## Sources and commands

| Need | Source | Command |
|---|---|---|
| Module log | LSPosed file | `adb shell su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'` |
| Module log lines | LSPosed | `adb shell su -c 'cat <log>' | grep 'DuoSB'` |
| logcat | adb | `adb logcat -s DuoSB` (may be empty from SystemUI on OxygenOS) |
| View tree | adb | `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` |
| Window focus/bounds | adb | `adb shell dumpsys window windows` |
| Frame timing | adb | `adb shell dumpsys gfxinfo com.android.systemui framestats` |
| Memory | adb | `adb shell dumpsys meminfo com.android.systemui` |
| Battery | adb | `adb shell dumpsys batterystats` |
| Screenshot | adb | `adb exec-out screencap -p > shot.png` |
| Static APK facts | jadx / REA | `jadx -d out app.apk`; `rea decode_android_resources` |
| Live hooks | Kahlo / Frida | `frida -U -l script.js com.android.systemui` |

## Rules

1. A case that cannot be confirmed from the LSPosed log is **not confirmed**. On OxygenOS, `android.util.Log` from SystemUI is dropped; use `L`.
2. Reproduce before fixing. Capture the failing dump/log first.
3. Change one thing. Re-run the same case. Diff the dump.
4. A dump diff on a working ROM (except timestamps/pids) is a regression.
5. If a surface cannot be resolved, leave the stock bar and log the candidate trace; never half-attach.
6. Record the exact command and the output path with every claim.
7. Redact PII: SSID, IMEI, serial, accounts, location, notification text.
8. Every new failure adds/updates an entry in `agent/REPAIR-CATALOG.md`.
9. A UI without a measured dump is `unverified`; do not claim support.
10. The `.riv` used in production must be byte-identical to a fresh build of `rive/duo/scene.rml`.

## Evidence paths

- Phase 1: `docs/evidence/v2/phase1/`
- Per-UI: `docs/evidence/v2/<ui>/`
- Performance: `docs/evidence/v2/perf/`
- Reports: in-app zip + Telegram later


## FILE: ICON-MISTAKES.md

# Icon drawing and animation — mistakes ledger

This is the list of mistakes made while building the event icons, so the next icon does not repeat them.
All of them were visible in this session; none is theoretical.

## Drawing / Rive

1. **Used the old prototype silhouette instead of the Quick Settings affordance.**
   The horizontal torch was never a vendor icon; it was our prototype. The QS source is
   `affordance_flashlight_icon.xml` pulled from CPH2747. Always pull the QS icon first.
2. **Did not check the actual reference proportions.**
   The QS flashlight is vertical: a wide cap bar, shoulders, a narrow handle, and a small switch hole.
   The first redraw used a funnel silhouette with a circular lens — wrong.
3. **Rive draws earlier siblings above later siblings in this project.**
   The first toggle/head-line pass put the white body before the details, so the body covered every
   dark detail. Working order: details first, track next, **opaque body last**.
4. **Same error with the toggle dots.**
   The track was before the dots in XML; because of reverse draw order it covered them. Put the track
   after the dots so the dots render on top.
5. **Used a circular halo for a flashlight.**
   The owner wants a cone: narrow at the mouth, widening away from it, with light inside. A circular
   halo is only correct for a glowing object, not a directional torch.
6. **Added two beam edge lines, then had to remove them.**
   The final design is glow-only: no stroke on the cone.
7. **Added a circular lens that the QS icon does not have.**
   The cap bar is the lens. Do not invent a lens circle.
8. **Used `CubicVertex` directly in `scene.rml`.**
   It is abstract; verification failed (`not-null` error). Use `StraightVertex` or a concrete
   `Cubic*Vertex` type (`CubicDetachedVertex`, etc.).
9. **Used a six-digit color (`FFB86B`).**
   Rive expects ARGB; the missing alpha made the bead invisible. Always use eight digits
   (`FFFFB86B`).
10. **Scaled the whole icon when trying to enlarge it, including the beam.**
    The glow overflowed the ring and later the numbers. Scale the body/toggle deliberately and keep
    the light path inside the ring.
11. **Changed the icon scale several times without measuring the ring.**
    Sizes tried: 1.35×, 2.5×, 3.1×, 3.77×, then 2.64×, then 2.11×. The target is roughly 80% of the
    ring with margin; do the percentage math before editing, not after three APK builds.
12. **Shifted the icon up/down without centering it.**
    One attempt left a 6 px bottom margin and a 38 px top margin. Measure top and bottom before shipping.

## Animation / state machine

13. **Assumed `EventFade` would animate the toggle.**
    It only faded the group/icon. The switch and light stayed as they were. `EventFade` now has explicit
    keyframes: 300 ms toggle/light off, then a 200 ms icon fade, then Wi-Fi returns.
14. **On/off timing was shared accidentally.**
    The first pulse was 200 ms, then 300 ms, then 500 ms. Write the intended duration in the plan before
    authoring keyframes.
15. **Treated the charging settle pulse as the full 0→level pulse.**
    It is only the arrival beat; the green-arc sweep is still missing. Do not call it RIV-002 complete.
16. **Used headless `--advance=0/9/18` and got the idle state.**
    The CLI did not always enter the event state. Use a known working frame (4) or inspect the state
    machine; do not conclude the art is missing from an idle screenshot.
17. **Did not verify the event source before drawing.**
    Mic/location/volume/unlock still have no measured source. Art without a source produces a hidden
    icon and a false "done" claim.

## Process / build

18. **Installed over a debug APK without uninstalling.**
    Release install failed with a signature mismatch and the phone still ran the old build. Uninstall
    or check `INSTALL=0` and the versionName every time.
19. **Built APKs before the final Rive copy in some iterations.**
    The APK contained the previous animation. Always `rive --once`, copy `duo.riv`, then assemble and
    check the packaged `res/zy.riv` hash.
20. **Believed an APK was current because it was installed.**
    Check `dumpsys package` versionCode/versionName and the packaged Rive hash, not just "Success".
21. **Used a debug build on the emulator and hit an ART JIT crash.**
    The debug build crashed in `JitCompileTask::Finalize`; release did not. Use release for UI/perf
    validation.
22. **Tried to compare against vendor icons by eye only.**
    The reference images are QS On/Off; the app icon is an in-ring event. Pull the QS vector, measure
    its cap/handle/switch, and keep a comparison image.
23. **Forgot the owner’s 30% / 20% size steps.**
    Current target is 2.11× after 3.77 → 2.64 → 2.11; check the last instruction before rebuilding.
24. **Rive screenshot did not show dark details until the body was moved last.**
    A blank white silhouette is a draw-order symptom, not a color/alpha symptom.

## Rules for the next icon

- Pull and inspect the QS vector first; redraw owned geometry, never copy the path.
- Write the shape order (details → track → body) before editing.
- Use `StraightVertex` unless a concrete cubic type is known.
- Use eight-digit ARGB.
- One vertical slice: art → states → Canvas → simulation → source → evidence.
- Measure the ring/icon ratio before scaling.
- Build with release, copy the `.riv`, verify the packaged hash, install, check versionName.
- Do not mark an event complete without a measured source and a device screenshot.


## FILE: ICON-REDRAW-PLAN.md

# Owned icon redraw + animation plan

Status: planning approved by the owner's request (2026-10-31). No OEM vector is packaged. The extracted
packs under `reverse/icon-packs/` are measurement/reference only.

## 1. Why redraw

- Vendor status icons are proprietary; shipping them is not acceptable.
- Android VectorDrawable XML cannot be used directly by Rive.
- The current `scene.rml` only has alarm, flashlight and screenshot art; mic/location/volume/unlock
  exist as enum values and switches but have no art, state, source or Canvas clone.
- One owned drawing per event keeps the animation and the Canvas fallback identical.

## 2. Visual system

| Property | Value |
|---|---|
| canvas | 24 × 24 dp inside the event slot |
| stroke | 2.4 dp, round cap/join |
| fill | none; light accents may use one tinted fill |
| light | top-left; the lit edge is white, the opposite edge 16% white |
| accent | one soft blue/white halo only for emitting events (flashlight, screenshot flash) |
| motion | one enter envelope + per-event body + one exit; no idle loop |
| scale | event art scales with the ring; clamp inside ring bounds |

## 3. Per-icon logic and decisions

These are the questions the owner asked; the answer on the right is the implementation default unless the
owner changes it.

### Flashlight
- **Question:** does the icon include a button?
  **Answer:** yes. A small round button at the tail is the pressed control; the barrel is the body.
- **Question:** what does a flashlight emit?
  **Answer:** a soft white/warm halo around the lens, not a long beam (the slot is too small for a beam
  that reads). The halo is part of the icon and fades with the event.
- **Trigger:** torch on (while active). Pressed state stays; off triggers `EventFade`.
- **Motion:** button depresses 6 %, body pops 1.0→1.06→1.0, halo fades in 120 ms; hold; fade 200 ms on off.
- **Canvas:** same body path, button, two halo circles.

### Alarm
- **Question:** is it a bell or a clock?
  **Answer:** owned alarm-clock silhouette, with two ring waves, because the system source is an alarm
  clock and the waves show "ringing" without a bell's religious/cultural baggage.
- **Trigger:** next-alarm transition (one-shot); duration 1200 ms.
- **Motion:** 2° shake + 1.0→1.08 pop, two waves expand/fade, then exit.
- **Canvas:** same silhouette + waves.

### Screenshot
- **Question:** camera or shutter?
  **Answer:** camera outline plus a white shutter flash inside the lens; the flash is the "light".
- **Trigger:** screenshot executor; 1000 ms.
- **Motion:** pop 0.94→1.04, shutter flash 0→0.9→0, hold, fade.
- **Canvas:** same outline/lens/flash.

### Microphone
- **Trigger:** privacy/recording active (source to be measured; do not invent a package hook).
- **Motion:** mic capsule pulse + one expanding ring; hold while active; fade 180 ms.
- **Canvas:** capsule, stand, ring.

### Location
- **Trigger:** location-in-use (privacy source to be measured).
- **Motion:** pin drop 8 px with a settle bounce; two soft ripples; hold while active.
- **Canvas:** pin and ripples.

### Volume
- **Trigger:** volume change (measured SystemUI/AudioManager source).
- **Motion:** speaker body + three arcs lighting left→right, 1200 ms.
- **Canvas:** same.

### Unlock
- **Trigger:** keyguard unlock/screen present (measured callback).
- **Motion:** shackle lifts and rotates open; 1000 ms; then fade.
- **Canvas:** same lock body and shackle.

### Charging
- Keep the existing bolt journey + `ChargeSettle` pulse; no icon change in this phase.

## 4. Implementation order (one vertical slice at a time)

1. **Art pass** — flashlight halo/button in Rive + Canvas. Build, headless screenshot, device.
2. **Art pass** — alarm waves; screenshot shutter flash.
3. **Art pass** — mic, location, volume, unlock; one node each.
4. **States** — add one state per type with measured enter/exit; reuse the existing eventGroup; do not
   duplicate the whole machine.
5. **Simulation** — add one button per event; preview drives the same state, never a separate animation.
6. **Sources** — measure SystemUI privacy callbacks, keyguard unlock and volume; only then wire.
7. **Evidence** — headless poses for every state, Canvas/Rive side-by-side, device screenshots.
8. **Gate** — no OEM assets in `app/`, no idle loop, no new `Log`, Rive `.riv` hash matches.

## 5. Rive draw-order rule (learned the hard way)

The Rive renderer here drew the **earlier** XML sibling above the later one. The first toggle/head-line
pass put the body before the details, so the white body covered every dark detail; the screenshot showed
only a blank torch. The working order for this project is:

1. details that must be on top (lens, head line, toggle beads) first;
2. the toggle track next;
3. the opaque body **last**.

Do not "fix" this by changing the body fill or adding another layer; reorder the nodes and verify with a
headless frame. The Canvas clone keeps normal painter order and is unaffected.

## 6. Questions still open (owner may answer later)

- Flashlight: warm halo (`#FFD37A`) or cool blue (`#9CC7FF`)? Default: warm white.
- Alarm: waves (default) or a small bell?
- Mic/location: show a privacy dot with the icon, or only the icon?
- Unlock: should it also play on lock, or only unlock? Default: unlock only.
- Volume: three arcs (default) or a numeric level?

## 7. Error avoidance

- Add one node at a time; run `rive . --verify` and `rive inspect . --summary` after each edit.
- Do not bind event visibility to `eventType` arithmetic; use a state per type.
- Do not use `eventGroup` opacity for more than the enter/exit envelope; each art owns its own opacity.
- Keep the Canvas fallback in the same commit as the Rive art; never ship one-sided.
- No vendor paths, no traced OEM geometry; only measurements (size, stroke, light direction) inform the
  owned drawing.


## FILE: KEYGUARD-CROSS-UI-AUDIT.md

# Keyguard unlock hook — cross-UI class audit

Date: 2026-10-31. Static smali check of the eight corpus SystemUI APKs.

All eight contain the same class and both measured methods:

| UI | `StatusBarKeyguardViewManager` | `notifyKeyguardAuthenticated` | `onKeyguardFadedAway` |
|---|---|---|---|
| OnePlus 16.00.14-2 | yes | yes | yes |
| Samsung 16-11 | yes | yes | yes |
| Xiaomi 17-03 | yes | yes | yes |
| vivo OriginOS 16.0.7.194 | yes | yes | yes |
| Motorola 15.7 | yes | yes | yes |
| Nothing 15.0.176 | yes | yes | yes |
| OPPO 3.0.4 | yes | yes | yes |
| Honor 11.0.0.1 | yes | yes | yes |

This is why the same LSPosed hook can be applied without per-vendor class names. It does not prove the
runtime transition on each vendor ROM; that still needs a device/emulator run.

## Runtime proof

AOSP/API 36 emulator, real PIN:

```
keyguard unlock hook installed
keyguard faded away -> unlock fallback
unlock received enabled=true active=null
live event -> UNLOCK
```

Call source is system-wide `TelephonyManager.ACTION_PHONE_STATE_CHANGED`; emulator `gsm call` produced:

```
phone state=RINGING callEnabled=true
live event -> CALL
```

Screenshot is the shared `ScreenshotWatcher` file source; phone produced:

```
live event -> SCREENSHOT source=file:/storage/emulated/0/Pictures/Screenshots/...
```


## FILE: LIST-THE-PLAN.md

# List the Plan — Pre-Plan Evidence and Change Inventory

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Pre-plan notes / change inventory ("list the plan") |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Draft. No code until brainstorming answers, Requirements, Test Plan, and final phased Plan are approved.** |
| Scope | OnePlus CPH2747 first; every change must be proven or marked `unverified` on the other 7 vendor UIs + AOSP |
| Sources | Owner brief 2026-11-02; `agent/AGENT.md`; `agent/REQUIREMENTS.md`; `agent/TEST-PLAN.md`; `agent/PLAN-v2.md`; `agent/UI-MATRIX.md`; current app/Rive/hook source; PHDP v2.11.0 public source; device logs already in the repo |
| Related files | `agent/BRAINSTORM-2026-11-02.md`, later `agent/REQUIREMENTS.md`, `agent/TEST-PLAN.md`, `agent/PLAN-v2.md` |

This file is **not** the final plan. It records what exists, what the owner asked for, what is proven, and what is still unknown. The final plan must be traceable to a requirement and a test.

---

## 1. Non-negotiable rules for this round

1. No guessing. Read the device/APK/log/source before naming a class, id, hook, resource, coordinate, or API.
2. No code before Brainstorm answers → Requirements → Test Plan → phased Plan are approved.
3. The FATAL/safe-mode fix is P0. No cosmetic work may hide or postpone it.
4. Every change must be evaluated on all supported surfaces: status bar, keyguard, shade header, QS header, app preview, landscape. A UI without evidence stays `unverified`.
5. Every Rive design needs a Canvas clone from the same geometry; the current Canvas is **not** yet a matching clone.
6. Defaults must preserve today’s pixels unless the owner explicitly changes them.
7. One property, one owner. Geometry in Kotlin; easing/art in Rive. Never animate a bound property in two places.
8. Fail silent: a bad new object/config leaves the stock bar working and logs the reason.
9. All repository files and file contents stay English.
10. The six reference images are on the owner’s phone in the folder `kilo images`. They are **not** local; local lookalikes must not be used.

---

## 2. Owner change inventory

This is the literal translated inventory, grouped for planning. It is not yet the requirements checklist.

### A. App shell, pill, selector, and motion

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| A1 | Current pill shape/icons are rejected; three references define Home, Customization, Report icons. | `DuoScreen` is Home / Graphics (`ENGINE`) / Report / Settings (`DuoShell.kt:112,132-137`). Current icons are Material `Home`, `Palette`, `Email`, `Settings`. References are a filled house, sliders, and documents+trend icon, not full page layouts. Mockup labels are Home/Components/About. | **PARTIAL; tab set unresolved** |
| A2 | Selector must be transparent; the icon under it changes colour instead of the selector turning red/blue. | Selector is a wine-tinted glass capsule (`DuoShell.kt:269-298`); tab tint also changes (`:187-194`). Reference: dark translucent ellipse, active Home blue, no wine. | Design change |
| A3 | Press-and-hold the pill, then drag the selector across pages; if the selector is on Home but the user holds Report, the selector jumps to the finger and follows it. | Drag is registered only on the selector itself (`DuoShell.kt:299-357`); tabs use `selectable` (`:200-206`). | Interaction change |
| A4 | Match pill/selector size to the two reference images: resting and held/enlarged. | Current selector: capsule height 44dp, width `itemWidth-32dp` min 72dp, stretch 26dp (`DuoShell.kt:145-149,234-237`). Reference: 225×179 px at rest in a 207 px pill; 296×231 px held; ≈1.30× scale; large ellipse around the active item. | Measured; settled held size unconfirmed |
| A5 | Replace the old Duo on/off GIF copied from Shutterstock with a new one: Duo icon appears, then disappears, on a black background, in the same style as previous GIFs. | Existing animated GIF path exists (`DuoSettingsComponents.kt:600-630`); only `guide_root_superuser.gif` is packaged. No Duo on/off GIF asset found. | New asset |
| A6 | Live preview must not animate continuously; it changes only when the user changes toggles/sliders/shapes. | Home preview loops every 5200 ms (`DuoHomeScreen.kt:202-220`); setting previews use phases. | Behaviour change |

### B. Settings / customization information architecture

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| B1 | Move all customization from Home/Settings into a Customization section; tapping the Customization ring opens its toggles/sliders/shape controls. | Home has live preview + presets; Settings holds sections (`DuoSettingsScreen.kt:534-593`). Target page count/name is not yet confirmed. | Needs IA decision |
| B2 | Move the engine selector into Customization; show Rive and Canvas; note Canvas is the automatic fallback when Rive fails. | Separate Graphics page with Auto/Rive/Canvas (`DuoShell.kt:362-416`). | Move + simplify |
| B3 | Remove from the UI only: Smooth Graphics (force ON always), System-Font Clock, Icon Colour Match. Separately add a white↔black GIF for icon colour matching. | Smooth Graphics is actually the Rive/Canvas switch (`DuoSettingsSections.kt:362-370`); System-Font Clock is `systemClockFont` (`:371-376`); Icon Colour is an Auto/Black/White picker (`:377-386`). The GIF instruction conflicts with removal. | **CONTRADICTION** |
| B4 | Remove the “Charging” item. | “Charging” appears both as an Animations switch (`DuoSettingsSections.kt:320-328`) and as a Middle-of-ring option “Charging %” (`:399`). Owner sentence is ambiguous. | **CONTRADICTION** |
| B5 | Fix the Wi-Fi glyph toggle; it does not work (may need a UI restart; untested). | `showWifiGlyph` exists (`DuoPrefs.kt:157`), read by host (`DuoIconHost.kt:1168-1169`), mapped to zero opacities (`DuoMapping.kt:182-184`), and exposed in the Icons section (`DuoSettingsSections.kt:554-559`). Needs reproduction. | Bug |
| B6 | Delete microphone, location, volume toggles from UI and code. | Bits and switches exist (`DuoPrefs.kt:295-297`; `DuoSettingsSections.kt:928-951`). | Remove |
| B7 | Calls: keep the event off and delete it from the UI. | Switch exists (`DuoSettingsSections.kt:960-967`); bit `EVENT_CALL` exists (`DuoPrefs.kt:299`). | UI remove; code retention TBD |
| B8 | Make simulations (flash, lock, screenshot, etc.) animated GIF previews like the others; delete the Simulation section and the Data/Cell-simulation section. | Simulation section is a live Rive control (`DuoSettingsSections.kt:972-1047`); Cell simulation is separate (`:1049-1089`). | Replace with GIFs |
| B9 | About/Report in Settings is no longer needed because Report is its own section. | Settings ends with AboutSection (`DuoSettingsScreen.kt:592-663`); Report page already exists (`DuoShell.kt:105`). | Move/remove carefully |
| B10 | Move Contact Developer to Report. | Contact button lives in About (`DuoSettingsSections.kt:733-743`). | Move |
| B11 | Sliders must respond only on the knob/circle, never the whole track; Position must move only from its icon/knob. | GlassSlider registers tap and drag on the whole 48dp box (`GlassControls.kt:228-240`); PositionEditor registers tap and drag across the full editor (`DuoSettingPreview.kt`/`DuoSettingsComponents.kt:269-291). | Interaction change |
| B12 | Presets: Default = Wi-Fi centre + auto 5G/4G + charge % above; Compact = camera ring with “Resize” on tap; Custom = last custom settings. Settings apply live, persist, and return when switching back to Custom. | Current presets are Classic/Compact/Bold (`DuoPresets.kt:15-65`); selected preset is transient UI state (`DuoHomeScreen.kt:66`); no Custom snapshot; “Compact” is not a camera ring. | Rework |
| B13 | When a preset is applied, change all its toggles; modifications are saved under Custom; returning to Default changes to Default, returning to Custom restores the last Custom state. | Preset application is a pure field transform, not a stored complete state (`DuoPresets.kt:4-13`). | New state model |

### C. Ring contents and object decomposition

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| C1 | Centre is one object: Wi-Fi and live-event icon; if Wi-Fi moves, the live icon moves with it. | Wi-Fi is in `middleFadeGroup`; live events are a sibling `eventGroup` at a different seat (`scene.rml:253,573-777`). | Rive refactor |
| C2 | Charge percentage is a separate object with two seats: original above, or centre. If it is centre, Wi-Fi takes its old above place; the number stays functional. | Percent is inside `ringGroup` at `y=-76` (`scene.rml:33-55`); Wi-Fi seat is `y=17`; no seat swap. | Rive + geometry |
| C3 | Split battery half-state: eight SIM dots (four per side), SIM1 right and SIM2 left, battery line at the bottom of the ring; Wi-Fi centre, number above. | Owner confirms `new duo customisition icon.jpg` is the design source. `splitIndicators` currently means separate views, not a split battery (`DuoPrefs.kt:44-55`); four fixed dots (`scene.rml:786-805`); no eight-dot/SIM-pair split state exists. | Image available; transition still unverified |
| C4 | Middle-of-ring “Charging”: Wi-Fi takes the charging-number place and size; it is functional, not a preview. | `middleSlot=charging` puts battery level in the network Text, Wi-Fi disappears (`DuoMapping.kt:166-177`). | Rework |
| C5 | Clock must use system time (not hard-coded 24h), one line, not touch ring edges. | Clock uses `SimpleDateFormat("HH:mm")` (`DuoStateMonitor.kt:295`); shared `network` Text width 56, wrap (`scene.rml:762-776`); measured 5-char times need 72-77 units at font 30. | Bug + new text object |
| C6 | Move Contact Developer to Report. | See B10. | UI move |
| C7 | Camera/Compact ring: tap shows “Resize”; ring in head-up/status bar only, QS/shade keep normal position. | No camera cutout geometry; `lockRing` only centres the ring (`DuoPrefs.kt:52-55`); no “Resize” action. | **BLOCKED on image + Q** |
| C8 | FATAL/restarts happened around ring centre in status bar, rotation, and camera ring; shade/QS is the suspected trigger. | Owner report; no current owner capture. Old capture shows QS/shade surface churn and a separate LuckyTool tombstone. See section 6. | P0 evidence |
| C9 | At 100% charging, return to normal non-charging behaviour. | Charging remains charging while plugged; no 100% special case. | New state rule |
| C10 | Add burn-in toggle: shift the whole Duo icon 1 px left, return after one hour, repeat, without battery drain; skip if SystemUI already does it. | No burn-in setting or motion found. SystemUI capability not measured. | New feature + evidence |

### D. Download progress / PHDP

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| D1 | When a download starts, ignore charging; show download number + bar. Tap the icon to return to charging. Before this, download is overwritten by the charging bolt. | `chargingVisual` suppresses download digits (`DuoMapping.kt:194-200`); test asserts the bolt wins (`DuoMappingTest.kt:309-317`). | Fix state machine |
| D2 | Add a Download Progress toggle, latest-release direct link, Simulate button; if PHDP is absent show the toggle GIF; real status-bar simulation requires PHDP. | No download preference, no PHDP detection, no link, no test button, no GIF. PHDP has no public test trigger. | New feature + Q |
| D3 | Only PHDP provides real progress; Duo consumes PHDP rather than duplicating notification hooks. | Duo-PHDP broadcast contract exists (`DownloadProgress.kt:44-65`; `DuoStateMonitor.kt:156-185`); PHDP v2.11.0 is authoritative. | Contract exists |
| D4 | Download state should override charging from 0 to 100; at completion return to normal, not remain in download. | PHDP completion holds 100 then sends `active=false`; Duo has no selection state. | New state machine |

### E. Stability, all-UI parity, documentation

| # | Owner request | Current evidence | Status |
|---|---|---|---|
| E1 | Fix the FATAL/safe-mode loop first. | See section 6. | P0 |
| E2 | Keep the ring normal in QS/shade when the camera-ring mode is used. | No camera ring; QS/shade attach is separate and measured. | Design rule |
| E3 | Every change must match all vendor UIs. | 2 measured UIs, 6 static-only; `agent/UI-MATRIX.md`. | Test gate |
| E4 | Read Rive system/animation docs before authoring. | `docs/rive-pipeline.md`, `rive/duo/AGENTS.md`, Rive CLI docs. | Mandatory |
| E5 | Write notes, brainstorm, ask 5 questions, then Requirements/Test Plan/Plan. | This file + `agent/BRAINSTORM-2026-11-02.md`. | This round |

---

## 3. Current app baseline (evidence)

### 3.1 Pages and navigation

- `DuoShell.kt:112`: `enum class DuoPage { HOME, ENGINE, REPORT, SETTINGS }`.
- Bottom nav items: Home, Graphics, Report, Settings (`DuoShell.kt:132-137`; strings `nav_engine=Graphics`, `nav_report=Report`).
- Home: live preview, “Customize ring” button to Settings, presets Classic/Compact/Bold (`DuoHomeScreen.kt:61-178`).
- Graphics: only engine selector Auto/Rive/Canvas (`DuoShell.kt:362-416`).
- Report: independent page (`DuoReportScreen.kt`).
- Settings: status/health, Customize, Animations, Appearance, Surfaces, Icons, Actions, Live Events, Simulation, Cell Simulation, Backup, About (`DuoSettingsScreen.kt:534-663`).
- Selected preset is `rememberSaveable` only; not persisted (`DuoHomeScreen.kt:66`).

### 3.2 Navigation pill/selector

- Wine glass selector: 44dp high, width `itemWidth-32dp` min 72dp, 26dp stretch, 4dp squash; wine tint alpha 0.10/0.16 (`DuoShell.kt:145-149,234-298`).
- Only the selector has `pointerInput`/`detectDragGestures`; tabs are individually `selectable` (`DuoShell.kt:200-206,299-357`).
- The selector already refracts the page and the pill via a separate layer backdrop (`:169-180,269-298`).
- Owner wants the selector transparent, icon colour change, and drag from anywhere on the pill.

### 3.3 Sliders and position editor

- `GlassSlider` has one `Semantics` box, tap on the whole 48dp height and drag on the whole width (`GlassControls.kt:214-240`).
- The thumb is 28dp; track height 10dp (`:197-200,242-312`).
- `PositionEditor` has tap left/right and drag across the whole 40dp editor (`DuoSettingsComponents.kt:237-291`). Owner wants only the icon/knob.

### 3.4 Presets

- `DUO_PRESETS` = Classic / Compact / Bold (`DuoPresets.kt:15-65`).
- A preset is a `DuoSettings` field transform; it does not store a complete snapshot (`:4-13`).
- Current Compact sets `size=100`, `splitIndicators=true`, `lockRing=true`, `hideOtherIcons=true`, `edgePadding=0` (`:33-49`) — not a camera ring.
- No `Default`/`Custom` persisted IDs or last-custom snapshot.

### 3.5 Clock

- `DuoStateMonitor.kt:295`: `SimpleDateFormat("HH:mm", Locale.getDefault())` → always 24-hour.
- Only `ACTION_TIME_TICK` updates; manual time/time-zone/locale changes are not in the filter (`:306-320`).
- Clock is rendered through the shared `network` Text (56×34, font 30, wrap) (`scene.rml:762-776`); Canvas uses the same 30-unit font (`DuoCanvasView.kt:318-327`).
- Measured text widths at font 30: 12:34 = 72, 23:59 = 77, 12:34 PM = 125 design units. The current 56-unit box cannot keep one line.

### 3.6 Rive / Canvas / ring

- Artboard 120×136, ring ellipse 103×103, stroke 8 (`scene.rml:17,164-169`).
- No constraints/rig; all seats are authored constants or bound numbers.
- `splitIndicators` splits Rive views, not the battery (`DuoPrefs.kt:44-55`; `DuoPart.kt`).
- Camera ring is not implemented; `lockRing` centres the ring.
- Canvas parity defects exist: different ring scale, DND seat, bolt seat, event scales, missing unlock/call art, no event motion (`DuoCanvasView.kt:167-171,477-533`).
- Current `.riv` SHA-256 `e4c4f4ec09dc0ccb47451b34dcfd0bf3085c42a0231566b474969798bc29716d`; docs record older hashes.
- `tools/check-rive-eases.py` currently reports 6 inert keyframes at `scene.rml:3492,3549-3552`.

### 3.7 Download / PHDP

- Duo consumes PHDP broadcasts: `io.github.kvmy666.duostatusbar.action.DOWNLOAD` and `DOWNLOAD_QUERY`.
- PHDP v2.11.0 latest (2026-10-21); direct asset `phdp-v2.11.0-release.apk`; no stable latest-APK URL.
- PHDP has no public test trigger. Its Duo test uses the same publisher as a real download; no `simulated` flag.
- Duo app cannot broadcast into the SystemUI-located receiver: receiver is `RECEIVER_NOT_EXPORTED`, different UID.
- PHDP default `showLabel=false`, so Duo must decide to honour it or add its own percentage switch.
- Charging currently wins over download digits (`DuoMapping.kt:194-200`).
- Only the ring has the download tap override; the split indicator view does not (`DuoIconHost.kt:323-324,1593-1633`).

---

## 4. Current settings inventory (for movement/deletion)

Order in `DuoSettingsScreen.kt:534-663`:

1. Customize
2. Animations
3. Appearance
4. Surfaces
5. Icons
6. Actions
7. Live Events
8. Simulation
9. Cell Simulation
10. Backup
11. About

Relevant rows:

- Appearance: Smooth Graphics (`useRive`), System-Font Clock, Icon Colour, Middle of ring (Auto/Wi-Fi/Cellular/Percent/Clock/Charging %), Airplane, DND mode, Wi-Fi dots, SIM choice.
- Animations: master, speed, arrival, departure, charging.
- Icons: hide other icons, Wi-Fi glyph, cellular dots, Shizuku hiding.
- Actions: tap/double/long press, indicator actions, download note.
- Live Events: Alarm, Flashlight, Screenshot, Microphone, Location, Volume, Unlock, Calls.
- Simulation: alarm/flashlight/screenshot live preview + Play.
- Cell Simulation: 0–4 levels.
- About: module status, problem text, send log, Contact Developer, root, export, update, donate, version.

---

## 5. Image evidence (retrieved 2026-11-02)

The owner connected the phone. The folder is `/storage/emulated/0/kilo images` on CPH2747. It contains exactly six files; they were pulled to `/tmp/duo-kilo-images/` and copied to the gitignored `reverse/kilo-images/` with matching SHA-256.

| Slot | File | Dimensions | SHA-256 | Content |
|---|---|---:|---|---|
| New Duo Customization reference | `new duo customisition icon.jpg` | 1122×872 | `e563548678d5cc88cb0f23fb11512027548d2c4af59ceb870e2212fbf76849ef` | Target ring crop: three rings (80 green / 49 yellow / 19 red); number above, black Wi-Fi centre, **eight SIM dots (four per side)**, colored battery line at the bottom; no app chrome |
| Home reference | `simple-rounded-house-icon-symbol-homepage-building-navigation-element-vector.jpg` | 980×980 | `757430018cd2c9f6f4e1ff38558d0b977cdf70f6d56638effdcf1629aed6d7ed` | Filled dark-navy house silhouette with arched door, 664×664 icon |
| Customization reference | `pill customasition icon.png` | 2464×1642 | `a5b7e15ddf8f99170758b49a5ade54a1d88fae1c71d146f3f28f8eda9596fa13` | White three-row sliders/tune icon, 83-unit rounded bars, 85-unit gaps, dark grid canvas |
| Report reference | `graph-report.jpg` | 1200×1200 | `60b1c668fd312a8d93c54fbced406d5a04bbee8f1fcac3c6fc4366f85bfb9ab9` | Two overlapping outline documents + rising trend arrow; Icons8 “Graph Report” stock icon |
| Selector resting | `static selector.jpg` | 756×238 | `5a98129d39927f5b6a32de5729fad8c72b33c25305cb6cc770a17c3c17635578` | Nav pill crop, active Home in blue; selector 225×179 px inside a 207 px-high pill |
| Selector held | `on movment.jpg` | 791×273 | `ccc59b7720259bb51dbbfb52b4c00b00b22074ff97aa0663c9d7613cf148de00` | Mid-motion crop; selector 296×231 px, overflows pill; active Home icon 76×90, label ~136×39 |

### 5.1 Measured selector/pill geometry

- Pill height: 207 px at rest, 209 px held; effectively unchanged (1.01×).
- Selector at rest: 225×179 px, centre inside the pill, 17 px below top and 11 px above bottom.
- Selector held: 296×231 px, 9 px above and 13 px below the pill; overlaps the next label.
- Held/resting: width 1.316×, height 1.290×, area ~1.60–1.70×; effective scale 1.26–1.30×.
- Resting fill: frosted charcoal `#232425`, rim `#323233`; held fill lighter `#343536`, rim with chromatic fringe.
- Active Home: blue `#3c88f8` at rest; label 98×29; icon 61×64. Held: icon 76×90 (width 1.246×, height 1.406×), label ~136×39.
- The active item grows with the selector; inactive items keep their resting size.
- The held image is a transient mid-motion frame; exact settled enlarged size still needs confirmation.

### 5.2 What these references are and are not

They are **icon/selector references plus one ring-design crop**, not full Home/Customization/Report screen layouts. They do not show:
- the final app page structure, cards, sections, or report form;
- the camera cutout or “Resize” affordance;
- the split-battery transition frames (only the target static 8-dot ring is shown);
- the ring at status-bar scale in all surfaces.

Owner answer 2026-11-02: **three tabs: Home / Customization / Report**, with Settings opened from a gear on Home. The selector mockup's Home/Components/About labels are not the final tab names; use the house/sliders/report icons.

Owner answer on the ring: the `new duo customization` image is the design source: eight SIM dots, battery line at the bottom, Wi-Fi centre, number above. The earlier "current rejected screen" label was wrong and is corrected here.

Local substitute images were not used. The originals are archived at `reverse/kilo-images/` and ignored by git (`.gitignore:30: reverse/`).

---

## 6. FATAL / safe-mode evidence

### 6.1 Owner report

- FATAL appeared after the shade/QS feature.
- Occurs on portrait→landscape rotation.
- Occurs when the ring is placed in the camera position.
- LSPosed reportedly blamed LuckyTool; LuckyTool’s log reportedly blamed the status bar/module.
- Highest priority.

### 6.2 Captured evidence (2026-11-02/03, CPH2747)

Owner authorized a read-only pull. Evidence is in `reverse/fatal-logs/` (gitignored): 92 files, 31 MB, with `MANIFEST.tsv`.

- LSPosed coverage: 2026-11-02 20:46:06 → 22:17:02, 22:17:02 → 23:09:40, 23:10:00 → 00:08:40 (+03). Oct 31 LSPosed logs have rotated away.
- Tombstones: 11 text files. Dropbox: 59 entries. Logcat: crash/main/system/events tails (30k each).
- Current SystemUI at capture: pid 7530, beta.45, 3 Rive attaches since 23:10, stable.
- Current Duo settings (rev 3864): `stage=2`, `engine_mode=auto`, `use_rive=1`, `show_in_qs=1`, `show_in_shade=1`, `live_apply=1`, `size=130`, `show_percent=1`, `percent_height=56`, `offset_x=0`, `edge_padding=100`, `split=0`, `lock_ring=0`, `middle_slot=auto`, `icon_color=auto`, `event_mask=255`.

### 6.3 Crash A — Java FATAL in QS `ConstraintLayout` (rotation)

Exact signature, `dropbox/system_app_crash@1793641719781.txt:11-26`:

```text
java.lang.RuntimeException: All children of ConstraintLayout must have ids to use ConstraintSet
  at androidx.constraintlayout.widget.ConstraintSet.clone(...)
  at com.oplus.systemui.plugins.qs.OplusQSRootViewComponent.updateResource(...:955)
  at ...cellCalculatorListener$1.onGridInfoChange(...:6)
  at ...CellCalculatorManager.setNoPersonalRowCountLandRight(...:26)
```

The same signature is in `system_app_crash@1793641993087.txt:11-20`, called from `OplusQSRootViewComponent$onViewAttached...emit(...:645)`.

Causal chain in `lsposed/log.old/verbose_2026-11-02T20:46:06.552425.log`:

```text
20:48:39.074  panel owner -> qs header
20:48:39.119  shade route slot=qs header cluster=391x63 target=RelativeLayout[1272x63]
20:48:39.130  element: Rive (stage 2, ALL)
20:48:39.301  qs header layout target=androidx.constraintlayout.widget.ConstraintLayout[1272x2772] ... parent=ConstraintLayout
20:48:39.302  Duo injected into qs header (RelativeLayout, 124px)
20:48:39.770  FATAL (landscape grid: setNoPersonalRowCountLandRight)
20:48:43.578  orientation is now portrait
```

The injected `DuoRiveView` has `id=-` (NO_ID), and `DuoIconHost.attachInto` calls `target.addView(candidate.ui)` without assigning an id (`DuoIconHost.kt:972`); the target is a `ConstraintLayout` (`DuoIconHost.kt:984-987`). Beta.45 still logs the same `ConstraintLayout` target at `23:13:44.660`. No QS rotation has happened since the 23:10 boot, so beta.45 has not re-observed it.

This is **not LuckyTool**: the Java stack is OnePlus QS + Duo's id-less child. It is the rotation crash.

### 6.4 Crash B — JNI global-reference overflow (Rive re-import storm)

Exact tombstone, `tombstones/tombstone_03:15-35`:

```text
Abort message: 'JNI ERROR (app bug): global reference table overflow (max=51200)'
48771 of android.graphics.Paint (48771 unique instances)
  444 of app.rive.runtime.kotlin.core.FallbackAssetLoader
   33 of app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
```

Backtrace, `tombstone_03:233-261`:

```text
art::JNI<false>::NewGlobalRef
  ← rive_android::CanvasRenderPaint::CanvasRenderPaint()
  ← rive_android::AndroidCanvasFactory::makeRenderPaint()
  ← rive::File::import()
  ← Java_app_rive_runtime_kotlin_core_File_import
  ← RiveAnimationView.loadFileFromResource
  ← RiveAnimationView.onAttachedToWindow
  ← DuoIconHost.ensureCluster
  ← DuoIconHost.syncExtraIndicators
  ← DuoIconHost.attachInto
  ← DuoIconHost.attachExtra
  ← DuoHook.attachExtraBars
  ← DuoHook.addShadeLayoutListener$lambda$0
```

Quantitative link: **48,771 Paint refs / 444 FallbackAssetLoader refs (≈444 imports inferred) = ~109.8 new Paint global refs per import.**

Immediate trigger in the LSPosed log:

```text
22:15:31.470  settings rev 3356: size 130%, percent=true, split=false, lock=false, edge=100%
22:15:32.167  settings rev 3357: size 100%, percent=false, split=true, lock=true, edge=0%
22:15:32.169  ring locked to PhoneStatusBarView 1272x141
22:15:32.180  element: Rive (stage 2, INDICATORS)
...           8-12 "Duo view ready" per second
22:16:01.573  SIGABRT (global ref table full)
```

The storm repeated on each restart: pid 1184 **269** Rive views in 32 s; pid 7975 **188** in 25 s + 64 in 9 s; pid 10588 **291** in 33 s. This is the camera/lock-ring crash path; `lock_ring` is the module's camera-cutout lock (`DuoPrefs.kt:282`).

### 6.5 Crash C — LuckyTool only at boot (not the Nov 2 cause)

`tombstones/tombstone_01:7-38` and `tombstone_28` show SIGABRT at 2026-10-31 02:34:14, pid 7620, uptime 4 s, 12 frames in `com.luckyzyx.luckytool/base.apk!libdexkit.so` (frame `0x1e948` repeated 3×). No `Abort message`. These are **different timestamps and PIDs** from every Nov 2 Duo crash. LuckyTool is scoped in SystemUI and can crash at boot, but it did not cause the Nov 2 QS/JNI crashes.

### 6.6 Ranked conclusions

| Rank | Conclusion | Confidence | Evidence |
|---|---|---|---|
| 1 | Crash A: id-less `DuoRiveView` in OnePlus `ConstraintLayout`; rotation/grid update calls `ConstraintSet.clone` | **Very high** | Java stack + `id=-` + `addView` path + 468 ms timing |
| 2 | Crash B: re-creating/importing Rive views on every shade layout pass while lock/camera mode is on; each import leaks ~110 global Paint refs until ART aborts | **Very high** | 444 FallbackAssetLoader refs (≈444 imports) → 48,771 Paints; 8–12 attaches/s; 29 s to abort; pure Duo/Rive stack |
| 3 | LuckyTool boot aborts are real but separate | High for Oct 31 | 12/12 frames in `libdexkit.so`; different time/PID |
| 4 | Generic Rive runtime leak independent of Duo's attach rate | Low | The leak exists, but only 444 imports in ~30 s reached the 51,200 limit |

The exact sub-setting that started rev 3357 is not isolated because five values changed together; the fix must remove the re-attach/re-import path, not merely reverse one setting.

### 6.7 Minimum verification after the fix

- QS open, rotate portrait→landscape, pull logcat + LSPosed + dropbox; expect **zero** `ConstraintSet.clone` FATALs and no new SystemUI pid.
- Hold the shade open and toggle size/split/lock for 60 s; count `File::import` and `Duo view ready`; assert imports do not grow per layout and `global refs << 51200`.
- Camera/lock mode: one Rive surface, no reattach storm; 60 s stress; no JNI abort.
- Keep a settings-only A/B (`stage=1` Canvas vs `stage=2` Rive) to separate the two mechanisms.

---

## 7. Contradictions and unknowns

### Contradictions resolved by owner answers (2026-11-02)

1. **Smooth Graphics:** remove the UI switch; Rive is the default/primary renderer; keep Canvas as the explicit fallback selector in Customization.
2. **Icon Colour Match:** keep the control; add a white↔black GIF preview.
3. **Charging:** remove the Charging animation section and the “Charging %” Middle-of-ring option. Charging state/bolt behavior remains unless a later requirement removes it.
4. **Calls:** remove the toggle from the UI; keep the event permanently off; retain code/art for a later version.
5. **App structure:** three tabs Home / Customization / Report; Settings opens from a gear on Home.
6. **Simulate:** PHDP has no public test trigger; the button will open the PHDP app so the user can run its own test. Without PHDP, show the GIF preview only.
7. **Split battery:** the `new duo customization` image is authoritative: eight SIM dots (four per side), battery line at the bottom, Wi-Fi centre, number above. The split transition still needs an explicit storyboard before implementation.
8. **Live preview:** always present in Customization; updates only when a setting changes; persists; no continuous loop.

### Existing contradictions still requiring a design decision

- The 8-dot image shows the static target, not how the battery line “moves to the old four-dot position” during the split transition.
- “Battery line at bottom” plus “battery moves to the old four-dot position” could mean a reflow of the arc halves; no transition frames yet.
- The ring number is above and Wi-Fi centre in the image; when charge % is centre, Wi-Fi must move above. The image does not show that swapped state.

### Unknowns requiring evidence

- Camera cutout geometry and “Resize” interaction on the final Compact design.
- Whether the Wi-Fi glyph fits the old percentage seat at full size; current geometry says it does not (top-gap inner width ≈55.4 units; Wi-Fi bbox ≈49.6×50; its centre would sit outside the ring). A scaled pose or a different top-gap variant is required.
- Whether `splitIndicators` must be disabled for the charge-centre and camera modes (currently split moves the inner icons to a different view, so an in-ring swap is impossible there).
- Event vs center vs charging priority.
- Whether SystemUI already applies a burn-in shift on CPH2747.
- Exact transition frames for the eighth-dot battery split; the static design is clear, the motion is not.
- PHDP simulation: the owner accepted opening PHDP’s own test; direct injection is not approved.

---

## 8. Preliminary risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Id-less child in OnePlus `ConstraintLayout` | Rotation FATAL / safe mode | P0: assign a stable ID before `addView` or inject into a compatible host; QS rotation test |
| Rive re-import storm under shade layout | JNI global-ref overflow / safe mode | P0: one view/import per surface; reuse; no per-layout reattach; import/global-ref counters; stress test |
| LuckyTool boot abort | Separate safe-mode cause | Keep separate; do not blame for Nov 2; test with LuckyTool off if needed |
| Preset snapshots corrupt settings | User loses custom ring | Versioned `DuoPresetState`; backup round-trip; no silent migration |
| Ring object decomposition breaks Canvas | Fallback differs | Shared geometry table + parity tests before Rive art |
| Wi-Fi top-gap swap does not fit | Overlap/clipping | Measure with image; author scaled pose; verify headless |
| Split battery transition wrong | Wasted Rive work / visual mismatch | Image-derived static layout; owner review of transition storyboard |
| Clock string too wide | Wrap/touch ring | Dedicated text object, no-wrap/fit, system format |
| PHDP simulation impossible | Requirement gap | Open PHDP test; GIF fallback; no unauthorized injection |
| Slider knob-only hurts accessibility | Lower usability | Keep semantics `setProgress`; knob hit target ≥48dp; test |
| Burn-in hourly timer drains battery | Battery regression | Use existing SystemUI capability if present; otherwise measure scheduler/8h idle |
| All-UI claim without evidence | False support | Keep `unverified`; per-UI matrix gate |

---

## 9. Immediate next actions (no code)

1. ~~Pull/read the six `kilo images`~~ — done; 8-dot ring reference and selector measurements captured.
2. ~~Capture FATAL evidence~~ — done; two root causes identified.
3. ~~Answer the five brainstorm questions~~ — done.
4. ~~Write `agent/REQUIREMENTS-1.6.md` with stable IDs.~~ — done.
5. ~~Write `agent/TEST-PLAN-1.6.md` with traceable cases and gates.~~ — done.
6. ~~Write `agent/PLAN-1.6.md` with phases, weapons, and traceability.~~ — done.
7. Review Requirements ↔ Test Plan ↔ Plan; then implement P0 only after owner approval.
8. Preserve the dirty beta.45 worktree per `PLAN-1.6.md §2.0` before P0.

---

## 10. Change log

- 2026-11-02: Initial pre-plan evidence and change inventory. No code, build, or install performed.


## FILE: MODEL-ROUTING.md

# Model and Agent Routing

## Hard rules

- **Never use GLM.**
- **BEAM never writes or edits code.** It is limited to search, inventory, log/docs extraction, and mechanical build/install/test commands with no source edits.
- **DeepSeek** (`opencode-go/deepseek-v4.1-flash`) is the only coding model (or the main agent). It is also the ceiling for architecture, debugging, and critical review.
- Never use expensive models (kimi-k3, gpt-6-luna, claude-opus, etc.).
- No `max`/`high` variant unless the owner asks.

## Routing table

| Task | Agent | Model / variant |
|---|---|---|
| Search, inventory, log reading, docs extraction | `explore-fast` | BEAM (default) |
| Mechanical edit/build/install with no architecture decision | `builder` | BEAM (default) |
| Architecture, multi-file code, debugging, critical review | `builder` / `planner` / `review-strict` | `deepseek-v4.1-flash` low/medium |
| Plan decomposition only | `planner` | `deepseek-v4.1-flash` |
| Adversarial review | `review-strict` | `deepseek-v4.1-flash` |

## Tool-limit workaround

`general` with BEAM fails when the tool schema exceeds 128 items (the REA tool set). Use:

- `explore-fast` (BEAM, `rea_*` denied) for read-only recon;
- `builder` (BEAM, `rea_*` denied) for mechanical work;
- do **not** load REA into a BEAM agent.

## Context and cost

- Grep/glob first; read only the needed ranges.
- Cap shell output with `head`/`tail`.
- Batch independent calls.
- Do not dump full build logs or full `uiautomator` XML into context; parse and summarize.
- After two failed attempts at the same problem, stop and report with the evidence.
- Use separate worktrees/branches for parallel agents; never two agents on the same files.

## Examples

- "Where is `findShadeIconsArea`?" -> `explore-fast`, BEAM.
- "Add a resolver test" -> `builder`, DeepSeek.
- "Why does the shade repopulate icons?" -> `builder`, DeepSeek (needs code reasoning).
- "Install Rive CLI" -> `builder`, BEAM (mechanical).
- "Review this diff for regressions" -> `review-strict`, DeepSeek.


## FILE: PHASE-1-5-WALKTHROUGH.md

# Phase 1–5 walkthrough, review and gap-closure plan

Status: active plan. Owner: Abdulkarim. Written 2026-10-29.
Branch: `phase/5-liquid-glass` (from `phase/4-cell-accuracy`).

This file is the route from "the code builds" to "the five phases can be reviewed and signed off".
It is deliberately ordered: **evidence first, then fix, then re-measure**. No item is marked done from
reading code alone.

## 0. Ground rules

1. A phase is only "closed" when its requirement, its test case and its evidence row below all pass.
2. A device result is named with the build hash. An emulator result is named `emulator`.
3. Anything without a device/emulator capture remains `unverified`; do not promote it to "known".
4. Do not change a working ROM's dump except for the intended field. A changed dump is a regression.
5. The SystemUI module and the app are separate products: glass changes are app-only.
6. Never use a visual guess where a measurement exists (tokens, lens values, dp/px, tab geometry).

## 1. Evidence sources

| Source | Use |
|---|---|
| `agent/AGENT.md` | current branch, fatal rules, model routing |
| `agent/REQUIREMENTS.md`, `agent/TEST-PLAN.md`, `agent/PLAN-v2.md` | the phase contract |
| `agent/REPAIR-CATALOG.md` | symptom → cause → fix → verify |
| `docs/rom-contract.md` | frozen SystemUI surface |
| `agent/PHASE-4-REPORT.md` | measured cellular evidence |
| `agent/PHASE-5-PLAN.md` | glass dependency and ColorDuo measurements |
| `corpus/io.github.fengwenhua.colorduo/base.apk` | visual reference only (never shipped) |
| latest beta APK + `adb`/`uiautomator`/`dumpsys` | the only proof of device behaviour |

## 2. Phase walkthrough (in this order)

### Phase 1 — shade/QS ownership and restore

- [ ] Read the last shade dump and the LSPosed `DuoSB` lines; record device, ROM, stage, slot and size.
- [ ] Open shade and QS separately. Confirm exactly one Duo per surface and one stock icon set hidden.
- [ ] Toggle the master switch off; confirm the stock bar returns and no fresh Duo is injected.
- [ ] Rotate portrait → landscape → portrait; confirm no duplicate and no 0×0 slot.
- [ ] Capture `uiautomator dump` and `dumpsys window` for: shade, QS, keyguard.
- [ ] Re-run the safe-mode fix branch check only if the device shows LSPosed safe mode.
- **Gate:** one owner per window, stock restore verified, no forced remove/reparent in the path.
- **Known gap:** `fix/landscape-safe-mode` (`d24b448`) is not merged/verified.

### Phase 2 — renderer, Canvas parity, colour binding

- [ ] Force `engineMode=canvas`; verify Wi-Fi, cell, DND, airplane, clock, percent, charging bolt.
- [ ] Force `engineMode=rive`; repeat the same matrix.
- [ ] Change system light/dark and wallpaper; verify every foreground shape follows the bar colour.
- [ ] Verify the Canvas ring uses the same bound `leftArc`/`rightArc` as Rive.
- [ ] Capture `dumpsys gfxinfo` for one scroll and one reveal on both paths.
- **Gate:** every Canvas occupant exists in Rive and vice versa; no hard-coded track circle.
- **Known gap:** some middle-slot/event combinations are unit-tested but not device-shot.

### Phase 3 — events, Rive state, and app controls

- [ ] Alarm: set an alarm 1 minute ahead; capture scheduled state, ringing state, dismissal.
- [ ] Flashlight: toggle on, toggle off; verify icon shows only while the torch is on and fades after off.
- [ ] Screenshot: take one; verify the one-second transient and return to the previous middle state.
- [ ] Concurrent events: fire flashlight + screenshot; verify newest wins and no frozen stack.
- [ ] Check the event toggles in Settings are independent and survive process death.
- [ ] Verify event icon size follows ring size and stays inside ring bounds.
- **Gate:** event timeline, fade and middle-return match `agent/EVENT-ARCHITECTURE.md`.
- **Known gap:** OEM icon art is prototype-only; release needs owned art.

### Phase 4 — cellular accuracy

- [ ] Read `cell raw level=… levels=[…] generation=… max=… subs=[…]`.
- [ ] Compare dots to the stock status bar on SIM1, SIM2 and Auto.
- [ ] Exercise 5G/4G/3G/2G and airplane; verify no dot when airplane.
- [ ] Rotate and confirm the selected subscription still maps to the same slot.
- [ ] Run `tools/check-cell-report.py` on the captured report.
- **Gate:** measured `max/raw/levels` agree with dots on the device, both SIM slots.
- **Known gap:** per-ROM `maxSignalLevels` still falls back to 5; only one measured ROM.

### Phase 5 — liquid glass shell

- [ ] Open Home, Engine, Settings in one session; back stack returns correctly.
- [ ] Confirm the stationary selector is a **circle**, and dragging turns it into a **stretching capsule**.
- [ ] Confirm the pill is blurred at rest (12 dp) and stronger while held (16 dp).
- [ ] Confirm the selector is clear/blur 0, refracts page + pill + logos, and can overflow the pill.
- [ ] Confirm every switch and slider is glass (no Material Switch/Slider left in Settings).
- [ ] Scroll Settings to both ends; verify the progressive glass edges, no hard blur rectangle.
- [ ] Measure frame time with `dumpsys gfxinfo` while scrolling and dragging.
- [ ] Verify API 33 floor: no crash on a device without the shader path (lens is API 33+).
- **Gate:** visual sign-off, no RC-017 crash, `gfxinfo` within budget, strings English.

## 3. Review matrix

| Phase | Requirement / test | Evidence | State |
|---|---|---|---|
| 1 | one owner + restore (TP-0xx) | shade/QS dump + screenshot | device pending |
| 2 | Rive/Canvas parity | two-engine screenshot + gfxinfo | partial |
| 3 | alarm/flashlight/screenshot | live capture + logs | prototype merged |
| 4 | cell dots per SIM | `cell raw` + report check | measured, one ROM |
| 5 | glass shell, 3 pages | screenshot + gfxinfo | active |

## 4. Enhancement backlog (ordered by user value)

1. **Selector physics:** circle at rest, slime capsule while dragging, springy snap; keep the halo.
2. **Glass controls:** glass switch, glass slider, glass dropdown/menus if they remain Material.
3. **Scroll edge:** progressive mask at top/bottom; keep the settings list under it.
4. **Tokens:** one `GlassTokens` object; no literal `16f`, `40f` or random alpha at call sites.
5. **Readability:** verify light mode and low-contrast wallpapers; glass must not wash out labels.
6. **Performance:** cap card blur; no animated blur over a scrolling list; measure.
7. **Event art:** replace prototype OEM icons with owned art before release.
8. **Cell fallback:** read the device's real level count per SIM; no fixed 5.
9. **Report quality:** severity-sorted repair list (critical / moderate / minor) in every release note.

## 5. Gap-closure waves

### Wave A — build and visual baseline
- Bump to the next beta, run unit tests + release assembly, install on CPH2747 when ADB returns.
- Capture Home/Engine/Settings and a drag sequence (idle → drag → release).

### Wave B — selector and glass controls
- Fix selector resting shape and drag stretch from the captures.
- Replace remaining Material controls; tune tint to readable values.
- Re-capture; do not tune by memory.

### Wave C — scroll and performance
- Validate the masked scroll edge, then `gfxinfo`; lower card blur if frame time regresses.
- Add a simple budget test/checklist to `PHASE-5-PLAN.md`.

### Wave D — phase 1–4 evidence
- Re-run phase 1 shade/QS, phase 2 engine matrix, phase 3 events, phase 4 dual-SIM.
- File any failure in `REPAIR-CATALOG.md` with the severity bucket.

### Wave E — release gate
- String audit, `.riv` hash compare, log-route check, authority grep, APK hash.
- Owner visual approval; no merge to `main` before that.

## 6. Fix severity policy for reports

Every report must list fixes in this order, not chronologically:

- **Critical** — can crash SystemUI/app, hide all icons, lose settings, enter LSPosed safe mode, or
  mislead every user. Needs a rollback build and a device test.
- **Moderate** — visible wrong behaviour, wrong mapping, broken interaction, or a misleading report.
  Needs a targeted test and a screenshot/dump.
- **Minor** — duplication, stale docs, cosmetic spacing, unused imports, log noise. Batch these; never
  let them hide a critical item.

## 7. Current known holes (do not forget)

- Selector was a tab-width pill at rest; now changed to a circle + drag capsule, **not yet device-shown**.
- Glass cards previously fell back to Material to avoid RC-017; the new wallpaper/page split gives them
  real glass. The device must confirm no RenderThread crash and acceptable frame time.
- `ScrollEdgeGlass` uses a `DstIn` gradient; it is implemented, not visually verified.
- Light-mode text on the dark wallpaper needs a screenshot check.
- API 33 lens path is the only shipped path; no API 31 fallback because `minSdk=33`.
- Phase 4 is a prototype branch and is **not merged**; phase 5 contains it.
- Phase 1 safe-mode fix is separate and unverified.
- Owner approval is still required before any merge to `main`.

## 8. Exit definition

All five phases have a named build hash, a device/emulator capture, and a pass on their gate; the
severity-sorted report has no open critical item; and the owner has approved the visual result.


## FILE: PHASE-1-CHECKPOINT.md

# Phase 1 Checkpoint — Shade/QS Repair

Date: 2026-10-27 (after the safe-mode incident)
Branch: `phase/1-shade-qs` (no commit; mixed working tree)
Purpose: a clean handoff across the owner's compact. Read this before touching the shade path again.

## Current device state

- Phone: CPH2747 at `[redacted]`.
- Installed: **rollback** `build/duo-phone-rollback-1.4.3.apk` (6,712,200 bytes, SHA-256 `d5795cc8a1c099e6d0fcef8ba4f9189cfa72ad81b8e6b8ca4a773c5419a47110`), installed with `-r`; SystemUI pid 30104; no LSPosed safe-mode line.
- The rollback is the known-working baseline: Duo appears in the shade/QS, with the known cosmetic issues below.
- The rollback screenshots taken after install are **black** (20 KB; screen was off/locked). Visual confirmation is still needed after the owner unlocks the phone.
- Do **not** install any later working-tree build until it is built, reviewed, and tested with the debug probe.

## Known bugs on the rollback baseline

1. **QS:** `quick_qs_status_icons` is 9x63; Duo is squeezed, Wi-Fi sits outside the ring. The tree had **2 TextureView** nodes (duplicate).
2. **Shade:** `quick_qs_status_icons` is 386x101; the tree had **0 TextureView** and the stock battery/icons remained. The shade is stock.
3. **Lock screen:** intermittent closed/100/overlap state; the tree had 2 TextureViews at identical bounds (duplicate with different state).
4. **Safe mode incident:** a later duplicate-fix build forced `removeView`/`dropSlot`/`ourViews` when the shade target changed; LSPosed entered safe mode. The phone went offline before the crash log was pulled; the exact exception is **UNKNOWN**. The forced removals were removed from the source, but the resulting builds were not device-verified.

## Source state — do not trust the last changes

- The last **built/installed** experiment was the separate-slots release (6,736,408); it still showed QS 9x63 / Wi-Fi outside and the shade stock.
- The working tree has further **unbuilt** changes (ancestor-chain normalization, slot names by original cluster dimensions). They were written but the build/tests were not completed after the last edit; `local.properties` was restored to its original Windows path.
- The working tree is mixed with the owner's pre-existing changes and CRLF/LF churn. Do not commit or install from it until checkpointed.

## The single next fix (one change set, then test)

1. `ContainerFinder.usableShadeHost`: walk the **ancestor chain** until a shown/attached `ViewGroup` wider than the original cluster is found (9x63 -> 9x63 parent -> 1020x63 `header`). No battery requirement.
2. `findVisibleShadeArea`: return the **raw** chosen cluster; the host normalizes after choosing the slot name.
3. `DuoIconHost.shadeSlotName(source, originalCluster)`:
   - `originalCluster.width < originalCluster.height` -> `qs header`;
   - `source == OplusQSSimpleHeader` -> `qs header`;
   - otherwise -> `shade header`.
4. `attachShadeCluster`: compute the slot name from the **original cluster**, then normalize the target; one-owner per slot name; if the slot exists, return true; **never** remove/drop/reparent a live Duo.
5. `attachShadeHeader`: if the resolved cluster is narrow (QS 9x63), do **not** consume the shade slot; schedule the bounded retry and wait for the wide 386x101 shade cluster.
6. Tests: ancestor-chain normalization, raw `findVisibleShadeArea`, slot-name mapping, narrow QS skip.
7. Build a **debug** APK with `ShadeProbe` enabled; test QS and shade first, then lock. Do not install a release build until the debug evidence is green.

## Acceptance evidence for this fix

- QS: exactly **1** TextureView; no stock battery; Wi-Fi **inside** the ring; `quick_qs_status_icons` parent widened (not 9px).
- Shade: exactly **1** TextureView; stock battery/icons hidden; Duo visible at 386x101.
- Lock: exactly **1** TextureView per surface; no closed/100/overlap duplicate.
- No `removeView`/`dropSlot`/`ourViews`/reparent in the shade attach path.
- No safe mode after 5 minutes of opening/closing QS, shade, and lock.
- Logs show the slot names (`qs header`, `shade header`) and the chosen target bounds.
- If any check fails: reinstall the rollback, stop, and add a repair-catalog entry; do not loop.

## 8-UI status (not Phase 1)

- 8/8 vendor SystemUI APKs downloaded into `corpus/` with index/hashes.
- Static per-UI maps and `shadeContainerIds`/`shadeBatteryId` candidate probes are built; OnePlus `shadeMeasured=true`, the other seven are static/unverified.
- Runtime dumps for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS are still missing -> `unverified`.
- Do not claim 8-UI support until each has a runtime dump.

## Rules for the next session

- Read `agent/AGENT.md`, `agent/REPAIR-CATALOG.md` (RC-003-SAFEMODE/DUPLICATE), then this file.
- One change set, then build + debug test; no blind release installs.
- No forced removal/reparent of live SystemUI views; leave stock and log instead.
- A safe mode report stops the work; restore the rollback.
- The owner makes the checkpoint/compact; continue from this file, not from memory.


## FILE: PHASE-1-REPORT.md

# Phase 1 Report — Shade / QS / Header Surfaces

Status: **closed and owner-approved on the measured OnePlus CPH2747 baseline** (2026-10-27).
This is not an 8-UI support claim.
Branch: `phase/1-shade-qs`; merged to main after owner approval.
Build reviewed: fix10, package `1.4.3-beta.1` (code 24).
Last updated: 2026-10-27.

## 1. Scope and result

Phase 1 fixed the three-surface Duo presentation on OxygenOS 16 / CPH2747:

- status-bar / header Duo;
- Quick Settings Duo;
- full notification-shade Duo.

The surface owner is no longer inferred from container attach order. It now follows SystemUI's own
panel state, and exactly one copy is visible at a time. The two optional surfaces can be disabled
independently without removing a live SystemUI view.

## 2. Problems and fixes

| # | Symptom | Root cause | Fix | Evidence |
|---|---|---|---|---|
| 1 | QS ring stayed 9 px / squashed | The vendor QS cluster was 9x63 inside the 63 px strip | Draw in the nearest taller QS ancestor; keep the main-bar size (114 px at 133%) | `qs header layout ... view=114x129`, device dump |
| 2 | Two Duo copies during QS entry/exit | Visibility was derived from whichever container attached last; QS overlay slots were not found by `liveSlotIn(stockTarget)` | `onShadeOrQsExpanded` + `ShadeExpansionStateManager` panel state; per-panel owner; one visible copy | fix8 owner/covered log |
| 3 | Shade copy disappeared (`alpha=0`) | Attach happened after the last layout; no later settle restored the copy | Show the owner on attach; panel callback is authoritative; no owner lock to null | fix8 `panel owner -> shade header` |
| 4 | Ring moved while 5G/indicators stayed fixed | `reapplyExtraHiding` called `applyExtraLayout` on every layout tick | Removed the per-layout re-anchor; the ring keeps its attach box; transition is hidden by the panel state | Phase 1 log, RC-013 |
| 5 | Hard pop/dropped frame returning to the header | Main Duo alpha was set to 1 in one frame while the ROM bar was still settling | Match the ROM's status-bar reveal: 320 ms, 50 ms delay, vendor `ALPHA_IN` cubic `(0.4, 0, 1, 1)` | `CollapsedStatusBarFragmentExImpl:472-473,611`; `Interpolators.java:96` |
| 6 | Safe-mode incident | A previous build forced `removeView`/`dropSlot` when a shade target changed | No live removal/reparent; detached-only drops; visibility uses alpha/stock restore; guarded hooks | `DuoIconHost` live-slot rules; no safe mode in later logs |
| 7 | QS 5G offset in the measured tree | Indicator view was posted before the final ring anchor | One delayed alignment pass after the attach (not a per-frame tracker) | 31 px offset in the QS tree; delayed alignment |
| 8 | Off/on or missing hook could leave a surface locked hidden | `panelStateKnown`/`panelOwner` survived teardown; a Boolean-hook-only fallback set owner to null | Reset panel state in teardown; `panelHookInstalled` gate; attach fallback when the per-panel hook is absent | Final review pass |
| 9 | QS/shade drew 175x198 while the header was 141x159; shade moved down | The extra used the tall overlay/window cap; a 1 px header shortfall selected the 688 px QS container | `SlotGeometry.extraSidePx` clamps every extra to the main bar side; a 10% shortfall (not 1 px) is required before moving out of the stock header | Device dump: header 141x159, QS/shade 175x198, shade parent 1170x688 |

## 3. SystemUI evidence

Measured on `corpus/SystemUI-device.apk` (CPH2747, sha256 `dbe67fd3…`):

- `com.android.systemui.shade.NotificationShadeWindowControllerImpl.onShadeOrQsExpanded(java.lang.Boolean)`:
  `true` at the start of QS/shade expansion, `false` when both close.
- `com.android.systemui.shade.ShadeExpansionStateManager.onPanelExpansionChanged(float, boolean, boolean, String, PanelType)`:
  `QS_PANEL` and `NOTIFICATION_PANEL` are separate; `SHADE_PANEL` is the aggregate.
- `PanelType` names: `NOTIFICATION_PANEL` (0), `QS_PANEL` (1), `SHADE_PANEL` (2).
- Live `dumpsys activity service com.android.systemui/.SystemUIService`: QS closed
  `PhoneStatusBarView alpha=1.0`; QS open `PhoneStatusBarView alpha=0.0`.
- The ROM's status-bar reveal: `CollapsedStatusBarFragmentExImpl` `ObjectAnimator/ValueAnimator` on
  `View.ALPHA`, **320 ms**, `Interpolators.ALPHA_IN`, **50 ms** start delay.
- `Agent/REPAIR-CATALOG.md` **RC-013** records the full method, hook and failure model.

## 4. Device verification

- fix8 hooks installed on the device:
  `shade/QS coverage hook installed`, `shade panel expansion hook installed`.
- QS open: `panel owner -> qs header`, `covered=true (main alpha=0)`.
- QS close: `panel owner -> collapsed`, `covered=false (main alpha=1)`.
- Full shade: `panel owner -> shade header`.
- No `FATAL EXCEPTION`, no safe mode, SystemUI alive after the test.
- The final build was delivered through the Telegram/catbox link after the phone had left the LAN.
  The owner installed it and confirmed the Phase 1 result on-device (2026-10-27). No fresh
  diagnostic dump was attached to that confirmation, so the approval is owner-confirmed visual
  evidence rather than a new measured dump.

## 5. Do the visibility hooks work on every UI?

**No — not yet.** The exact-name audit of the downloaded APKs is:

| UI | `onShadeOrQsExpanded` exact | 5-arg `onPanelExpansionChanged` + `PanelType` | Oplus vendor classes | Status |
|---|---|---|---|---|
| OnePlus OxygenOS 16 / CPH2747 | yes | yes | 3/3 | measured Phase 1 base |
| Samsung One UI 16 | yes | no (different expansion API) | 0/3 | hook 1 only; unverified |
| Xiaomi HyperOS 17 | yes | no (3-arg `FZZ`) | 0/3 | hook 1 only; unverified |
| vivo OriginOS 6 | yes | no (`ZFZ`) | 0/3 | hook 1 only; unverified |
| Motorola 15 | yes | no | 0/3 | hook 1 only; unverified |
| Nothing 15 | yes | no (`QS_PANEL` string only) | 0/3 | hook 1 only; unverified |
| OPPO 3.0.4 (old) | no | no | 0/3 | structural only |
| Honor 11 (old) | no | no | 0/3 | structural only |

What this means:

- The **main-bar cover** hook (`onShadeOrQsExpanded`) is present by exact name on the five modern
  non-OnePlus builds, but runtime installation/behavior is unverified.
- The **QS-vs-shade owner** hook only installs on the measured OnePlus signature. On the other
  modern UIs the module deliberately falls back to attach-time ownership; that does not yet make
  those QS/shade surfaces supported.
- `RomDetection`/`RomAdapter` still selects status-bar and shade-container maps per UI. The new
  visibility hooks are SystemUI-level, not yet gated or aliased per matrix row. Phase 2 must add
  per-UI aliases (or a matrix capability flag), not assume the OnePlus classes exist everywhere.
- `UI-MATRIX.md` remains the source of truth: only OnePlus has a measured Phase 1 shade/QS path.
  Xiaomi has a measured status-bar dump; its QS/shade path is still unverified. The remaining UIs
  are static APK maps only.

### Phase 2 alias targets (from the exact audit)

- Samsung: `ShadeExpansionListener`/`ShadeExpansionChangeEvent` path; no `PanelType`.
- Xiaomi: `ShadeExpansionStateManager.onPanelExpansionChanged(FZZ)`; no `PanelType`.
- vivo: `ShadeExpansionStateManager.onPanelExpansionChanged(ZFZ)`.
- Motorola: `ShadeExpansionListener`; no 5-arg method.
- Nothing: `(FZZ)`; no `PanelType`.

Each needs a per-UI QS/shade classifier, a runtime dump, and a matrix capability flag. A missing
exact token is not proof the function is absent; it is proof the current alias cannot install.

## 6. New user toggles

Two optional settings are now part of the app ↔ module contract:

- `showInQs` (default `true`) — draw Duo in Quick Settings.
- `showInShade` (default `true`) — draw Duo in the full notification shade.

Behavior when off:

- the Duo copy for that surface is hidden;
- the stock header icons for that slot are restored in place (`stock.restore()`);
- no live Duo is removed or reparented;
- turning it back on re-runs the hide pass and shows the owner copy;
- the columns are optional in the provider contract, so an older settings app cannot disable a
  surface by omission.

The switches are in the **Surfaces** section of the settings app. Persistence and the provider row
are covered by `SettingsChannelTest` (including the missing-column fallback to `true`).

## 7. Validation

- `:app:testDebugUnitTest`: **228 tests, 0 failures** (24 suites; includes the surface-toggle
  persistence and missing-column tests).
- `:app:assembleRelease`: `BUILD SUCCESSFUL`; final APK `6,754,152` bytes, SHA-256
  `d3ab2d2f79ca0aab778c24351898551f25223e7358d4ce3c51946ce98d8224b3`.
- No safe mode in the fix8 device log; the later 175 px size regression did not enter safe mode.
- The final build was installed from the Telegram/catbox delivery and the owner approved it.
  The final on-device visual test is therefore owner-confirmed; static build/tests are green.
  The phone was not reconnected for a post-approval diagnostic dump.

## 8. Phase 2 entry conditions

1. Keep Phase 1 behavior frozen on OnePlus; do not change the measured hooks without a dump diff.
2. Add a per-UI capability map (matrix) for the visibility hooks and vendor routes.
3. Obtain runtime dumps for Samsung/Xiaomi QS/shade, vivo, Motorola, Nothing, ColorOS and Honor
   before claiming support. Static APK names are leads only.
4. Implement aliases per the targets above; test one UI at a time.
5. New features remain off/unmapped by default; stock icons must survive every disable path.


## FILE: PHASE-1-TEST.md

# Phase 1 — Shade/QS Test Sheet

Status: safe build prepared; **device verification pending**.
Branch: `phase/1-shade-qs`.
Last updated: 2026-10-27.

> **SAFE-MODE WARNING:** the previous duplicate-fix release caused LSPosed safe mode. Do **not** install it. If safe mode is active: exit safe mode, install `build/duo-phone-rollback-1.4.3.apk` first, verify the known state, then test `build/duo-shade-safe-release.apk` (untested on device) with logs. If safe mode recurs, stop and report; do not loop installs.

## Artifacts

| Artifact | Path | Notes |
|---|---|---|
| Rollback (known working before the duplicate fix) | `build/duo-phone-rollback-1.4.3.apk` | 6,712,200 bytes; same signature; use first if safe mode |
| Safe release (untested on device) | `build/duo-shade-safe-release.apk` | 6,736,680 bytes; no forced remove/reparent |
| Debug test (probe on) | `build/duo-shade-test-debug.apk` | 36,241,821 bytes; emulator/debug signature; not for the phone release |
| Source branch | `phase/1-shade-qs` | No commit |

## Prerequisites

- Android SDK emulator at `[redacted]/Android/Sdk/emulator/emulator`.
- AVD `ae_api36_root` (google_apis, x86_64, rootable).
- LSPosed (or an equivalent Xposed loader) inside the emulator. If it is missing, the SystemUI hook cannot be tested; only the app UI can.
- `adb` on PATH.

## Emulator boot

```bash
[redacted]/Android/Sdk/emulator/emulator -avd ae_api36_root -no-snapshot -no-audio -gpu swiftshader_indirect &
adb wait-for-device
adb root
adb shell getprop ro.build.version.sdk
```

If `adb root` is denied, the AVD is not the userdebug/google_apis image; stop and report.

## Check the loader

```bash
adb shell pm list packages | grep -iE 'lsposed|xposed|magisk|kernelsu|apatch'
adb shell su -c 'ls -d /data/adb/lspd /data/adb/modules 2>/dev/null'
```

If LSPosed is absent, install it on the rooted AVD or use the owner's phone later. Do not claim the shade hook passed without a loader.

## Install and scope

```bash
adb install -r build/duo-shade-test-debug.apk
```

1. Open LSPosed Manager.
2. Modules → Duo Status Bar → enable.
3. Scope → check **System UI**.
4. Save; restart System UI:
   ```bash
   adb shell su -c 'kill $(pidof com.android.systemui)'
   ```
5. Wait for SystemUI to come back; confirm:
   ```bash
   adb shell pidof com.android.systemui
   ```

## Test A — generic/AOSP shade path

This is what the emulator can prove. The OnePlus vendor hooks do not run on AOSP.

1. Open the shade:
   ```bash
   adb shell cmd statusbar expand-notifications
   ```
2. Wait 2 s; screenshot:
   ```bash
   adb exec-out screencap -p > /tmp/phase1-aosp-shade.png
   ```
3. Capture the log:
   ```bash
   adb shell su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'
   adb shell su -c 'cat <log>' | grep DuoSB
   ```
4. Pass criteria:
   - Duo is visible in the status bar and, if the AOSP build exposes a shade cluster, in the shade header.
   - The stock battery is not visible under Duo in the same slot.
   - Logs show the resolved source/container and `battery hidden`; no crash/ANR.
5. Disable the module, restart SystemUI, open the shade:
   - the stock battery/icons return exactly.

## Test B — OnePlus vendor path (phone only, deferred)

The owner said use the emulator, not the phone. Do not install the debug build over the release on CPH2747.
When the phone is allowed again:

1. Install the matching release build (same signature).
2. Enable LSPosed scope System UI; restart.
3. Open the shade; expect:
   - `OplusQSSimpleHeader.onInit` or `OplusSplitShadeHeaderControllerExImpl.init` logs;
   - `source=OplusQSSimpleHeader|OplusSplitShade`;
   - the visible cluster `quick_qs_status_icons` gets Duo; `statusIcons` + `batteryRemainingIcon` hidden;
   - stock restored on disable.
4. Capture screenshot + `DuoSB` log; add to `agent/REPAIR-CATALOG.md`.

## Failure capture

If a case fails, record:

- device/build: `getprop ro.build.fingerprint`, `ro.build.display.id`;
- surface: status/keyguard/shade/QS;
- screenshot path;
- LSPosed log path + the last `DuoSB` lines;
- the exact `source`, `container`, `battery`, `stage`, `reason` fields;
- whether the stock battery was visible, hidden, or restored.

Add a `RC-XXX` entry to `agent/REPAIR-CATALOG.md` **before** changing code. Do not guess.

## Known limitations

- The emulator cannot validate the OnePlus vendor hooks.
- The debug APK is debug-signed; do not install it over the phone's release build.
- `ShadeProbe` is enabled only in debug builds (`BuildConfig.DEBUG`).
- Six vendor UIs have static APK maps only; no runtime dumps.
- The working tree has mixed CRLF/LF; normalize before a real commit.


## FILE: PHASE-2-REPORT.md

# Phase 2 Report — Testing, logging, repair

Status: **static gate complete; device/per-UI evidence pending.**

Branch: `phase/2-multi-ui`. Phase 1 merged to `main` at `5485ebf`.

## 1. Structured logging (`DuoLog`)

- `io.github.kvmy666.duostatusbar.hook.DuoLog` emits one stable single-line record through `L`:
  `ui= surface= stage= class= id= hook= result= reason= detail= build=`.
- Values are flattened (no newlines/CR/control chars), length-capped (240 chars for `detail`,
  160 for other fields) and redacted (`ssid`, `imei`, `serial`, `token`, `password`, `secret`,
  `account`, `email`, plus email addresses).
- Stable reason codes are documented in `agent/REPAIR-CATALOG.md` (DuoLog reason codes table).

## 2. 2 MB ring log and redaction

- `L` keeps the last 600 lines or 2 MB (`MAX_RECENT_BYTES`), whichever hits first, under a lock.
- The ring is handed to the app through the settings channel without root, so a report is never empty
  just because `su` is unavailable.
- The same redaction rules apply at the source (`DuoLog.sanitize`); the ring itself stores the
  already-redacted line.

## 3. Report ZIP

- `DuoReport.writeZip` / `LogReporter.writeReportZip` produce a self-contained ZIP:
  `manifest.json`, `diagnostics.txt`, `module-log.txt`, `logcat.txt`, optional `screenshot.png`.
- Manifest carries schema `duo-report/1`, timestamp, app version/build type, device and adapter
  (ROM) maps, and the entry list.
- Pruning keeps the newest 10 reports and 10 text dumps (`DuoReport.prune`).
- Optional screenshot is included only when supplied; otherwise omitted from both disk and manifest.

## 4. PII filter on the LSPosed tail and 2 MB dump cap

- `RootLogs` captures only filtered evidence: logcat `-s DuoSB` and the last 400 `DuoSB`/
  `duostatusbar` lines from the newest LSPosed module log; a full logcat is never captured.
- Device facts are always appended, so a report is never empty.
- The module log handed to the ZIP is the 2 MB ring (`L.recentText()`).

## 5. Capability fields and Xiaomi (FZZ) evidence

- `RomAdapter` carries `coverHook`, `panelExpansionKind`, `panelHookMeasured`,
  `panelOwnerAvailable` (plus the shade fields).
- `panelOwnerAvailable` is `true` only for measured OnePlus/OxygenOS; `false` for AOSP and every
  other UI.
- Xiaomi HyperOS uses `panelExpansionKind = "fzz_3arg"` with `panelOwnerAvailable = false`:
  the HyperOS 17 callback is `onPanelExpansionChanged(float, boolean, boolean)` and is a
  cover/expansion signal only, never a QS-vs-shade owner. See `RC-014`.
- `LogReporter.writeReportZip` exports all of these fields (`coverHook`, `panelExpansionKind`,
  `panelHookMeasured`, `panelOwnerAvailable`) in the adapter block of the report.

## 6. Tests

- Unit tests: 261 (Robolectric + JVM), including `DuoLogTest`, `DuoReportTest`,
  `LogReporterZipTest`, `LRetentionTest`, `ShadeExpansionClassifierTest`, `DuoPrefsDumpTest`,
  `RomDetectionTest`, `RomProfilesTest`, `RootLogsTest`.
- Static gates: `tools/route-module-logs.py --check`, `tools/check-rive-eases.py`,
  Rive verify/build/hash (best effort), authority grep.

## 7. Remaining tasks

- Coverage gate: 80% lines/branches on changed code (unit + Robolectric).
- Instrumentation tests for resolver/attach/hide.
- Device TP-070..TP-079 (report round-trip, no PII, catalog coverage).
- Per-UI panel aliases verification (currently unverified for non-OnePlus UIs).
- Per-UI runtime dumps for six UIs still missing.

## 8. Build result (this run)

- Command: `JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 PATH=... ./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain --offline`
- Result: BUILD SUCCESSFUL (tests 32 executed; release 22 executed, 62 up-to-date on first pass).
- Unit tests: 261, 0 failures (30 test classes).
- Release APK: `app/build/outputs/apk/release/app-release.apk`
  - size: 6,767,500 bytes
  - SHA-256: `01a6b812f6c6ed05b9e4d4b7f28218f639a963daefc32e032d568b208f5ea500`
  - versionCode 24, versionName 1.4.3-beta.1
- Static gates: not re-run in this pass (route-module-logs / rive-eases unchanged).


## FILE: PHASE-3-REPORT.md

# Phase 3 Report — App customization (shell, engine, presets, backup)

Status: **partial: shell/engine/backup/presets/preview, middle-slot choices, per-icon Wi-Fi/cellular
toggles, alarm/flashlight/screenshot live-event rendering and the event simulation implemented;
microphone/location events, layouts, icon packs and device verification pending.**

Branch: `phase/3-app-customization`, merged to `main` as the approved prototype at `10f9ac7`.
Phase 1 merged to `main` at `5485ebf`; Phase 2 committed at `1ad00b8`; Phase 3 commits `3b8a348`,
`e5b9fea`, `5d0ed88` (middle-slot choices), `6649a56` (per-icon Wi-Fi/cellular visibility), and the
event/simulation prototype series ending at `163b3b8`.

## 1. Three-page shell

- `io.github.kvmy666.duostatusbar.ui.DuoShell` provides bottom navigation with three pages:
  Home, Engine, Settings.
- Home shows module status, a live preview card (`DuoPreview`), and a presets card.
- Tested on CPH2747 (OxygenOS 16) without fatal.

## 2. Engine selector

- Additive `engineMode` with values `auto` / `rive` / `canvas`; no change to existing default
  behaviour when unset.
- `DuoGuard` remains the single owner of Rive allow/block decisions; forcing Rive/Canvas takes
  effect on the next status-bar start.
- Provider column and fallback reason surfaced in the Engine page.
- Current selector dropdown tested on device (Canvas -> Auto) with no fatal.

## 3. Presets

- `io.github.kvmy666.duostatusbar.settings.DuoPresets`: Classic, Compact, Bold.
- Each preset is a pure transform of `DuoSettings` that changes only its named fields and preserves
  the rest (enabled, engine mode, actions, surfaces, sim choice, ...).
- Presets are data-driven and applied from the Home page with a broadcast + settings push.
- Tested Compact -> Classic on device.
- Unit tests: `DuoPresetsTest`.

## 4. Backup / import

- Format `duo-settings/1`: one plain versioned JSON file with settings, preferences and
  customization; no encryption, no secrets.
- Allowlist/exclusions applied; import validates and shows a confirmation dialog before overwrite.
- Storage Access Framework (SAF) entry points for export/import.
- Unit tests only: `BackupCodecTest`, `DuoPrefsBackupTest`, `SettingsChannelTest`.
- No device test yet for SAF flow or landscape orientation.

## 5. Live preview

- Home page embeds `DuoPreview` (180 px, non-looping, 1000 ms reveal) inside a card.
- Preview is static/visual only; it does not drive the status bar.

## 6. Middle slot

- `middleSlot` choices: `auto` / `wifi` / `cell` / `percent` / `clock` / `charging`
  (`DuoPrefs.MIDDLE_SLOT_*`, normalized in `DuoPrefs.normalizeMiddleSlot`).
- Wi-Fi and cell reuse the existing occupant logic; percent/clock/charging reuse the
  existing middle text seat (network label), so no new geometry is introduced.
- Clock updates on `ACTION_TIME_TICK` (registered in `DuoStateMonitor`); the charging
  choice only renders while charging.
- Provider column `middle_slot` (`DuoPrefs.COL_MIDDLE_SLOT`), persisted in settings and
  pushed over the settings channel.
- Tests: mapping/channel coverage (`DuoMappingTest`, `SettingsChannelTest`).
- No device test yet; Rive/Canvas visual parity for the middle seat needs verification
  on device.

## 6a. Per-icon visibility (Wi-Fi glyph / cell dots)

- `showWifiGlyph` and `showCellDots` settings (DuoPrefs `COL_SHOW_WIFI_GLYPH`,
  `COL_SHOW_CELL_DOTS`) gate the Wi-Fi glyph and the four cellular dots inside the
  ring; both default to true, so existing behaviour is unchanged.
- Plumbed through `DuoMapping`/`DuoVisual`, the settings provider column and the
  settings channel; toggles exposed in the settings UI.
- Unit tests: `DuoMappingTest`, `SettingsChannelTest`.
- No device/UI test yet.

## 7. Tests and build

- Unit tests: 317 (last run, 0 failures).
- Latest code commit: `7436517` plus the flashlight-event slice on
  `phase/3-app-customization`.
- Version: `1.4.3-beta.4` (code 27).
- APK size/SHA: `6,902,776` bytes /
  `e4effec1fbc1396a4a74056601e949cf03eb2305aa75d5eb985ba241b0431a3a`
  (`app/build/outputs/apk/release/app-release.apk`).
- `duo.riv` SHA-256 `540bb0884324b1f4303b22debdfb943cb4ad9bc04ce3fba0cacaa3896269e201`;
  packaged as `res/zy.riv` by resource shrinking, byte-identical when extracted.
- Static gates: Rive verify clean, 132/132 eased keyframes, 0 inert; log-route check clean.
- Not installed/tested on phone in this pass (device disconnected); flashlight/alarm device
  verification remains pending.

## 8. Remaining work

- 4+4 cell-dot layouts with battery as a bar; Wi-Fi in the charging-number slot; battery % in the middle.
- Battery bar rendering.
- Wi-Fi charge slot.
- Icon packs (shape switching; OEM assets remain prototype-only).
- Per-icon show/hide matrix: Wi-Fi glyph and cell dots implemented (6649a56); broader stock-icon matrix remains partial.
- Question-mark toggle simulations and per-toggle static previews.
- Live events: alarm + flashlight + screenshot rendering implemented (Rive state transition, Canvas
  clone, per-event switch). Microphone/location sources and artwork remain.
- Device verification of flashlight on -> off and alarm set/ring on all three surfaces.
- Rive/Canvas middle-slot parity.
- Backup SAF flow and landscape orientation on device.
- Device smoke for backup import/export and landscape.
- Rive visual verification of the middle seat on device (no device test yet).

## 9. Safe-mode evidence

- Latest SystemUI tombstone in `from users/safe-mode-20261028/` was LuckyTool/DexKit, not Duo.
- The landscape safe-mode fix lives on a separate branch `fix/landscape-safe-mode` at commit
  `d24b448`; this report does not include that fix.


## FILE: PHASE-4-PLAN.md

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


## FILE: PHASE-4-REPORT.md

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


## FILE: PHASE-5-PLAN.md

# Phase 5 — Liquid glass + three-page shell (plan)

Branch: `phase/5-liquid-glass`, created from `phase/4-cell-accuracy`.

## 1. Scope (requirements)

| Requirement | Meaning | Test |
|---|---|---|
| CUS-011 / TP-030 | Home, Engine and Settings are three pages with a correct back stack | manual UI |
| CUS-012 / TP-031 | Liquid glass on the app surfaces; API 31+ blur, API 33+ shader, graceful fallback below 31 | screenshots on API 31/33/older + `dumpsys gfxinfo` |
| CUS-013 / TP-032 | User-facing strings remain English | string audit |

The effect is **app-only**. It must never be applied to SystemUI, the status bar, keyguard or shade.

## 2. Dependency audit (measured 2026-10-28)

The plan named `io.github.kyant0:backdrop:2.0.1`. Maven metadata and the published POM show:

- 2.0.1: Kotlin 2.4.10, Compose Multiplatform 1.12.0, packaging `jar` (CMP module).
- 1.0.1: Kotlin 2.2.21, AndroidX Compose foundation/ui 1.9.5, packaging `aar`.
- The project currently uses Kotlin 2.1.0 and Compose BOM `2024.12.01` (Compose 1.7.6).

2.0.1 cannot be consumed by the current Kotlin/Compose stack without a Compose Multiplatform
migration. 1.0.1 still requires Kotlin 2.2.21 and Compose 1.9.5.

**Decision:** use `backdrop:1.0.1` (Apache-2.0) and upgrade Kotlin to 2.2.21 and Compose BOM to
`2025.10.01` (UI 1.9.4; the library raises UI/foundation to 1.9.5). This is a gated spike: if the
upgrade breaks Rive/tests or cannot fetch offline, revert the dependency and implement the same
surface with a small `RenderEffect` wrapper instead of shipping a broken build.

## 2.1 ColorDuo reference (measured 2026-10-29)

Owner pointed at `ColorDuo`; pulled from CPH2747 and decompiled with REA/JADX.

- Package: `io.github.fengwenhua.colorduo`, version `1.1.7` (code 10107), APK SHA-256
  `d7441541b5ac1c4802ca0312ba1a1debf873580efa534cc16c1e69c2c45e8cd2`.
- **The package names are R8-obfuscated**, so the first pass incorrectly said "no library". The
  shader/class surface (`Refraction`, `LocalDropdownBackdrop`, `drawBackdrop`, progressive alpha
  mask) matches **Kyant Backdrop** (the same library we use), bundled or forked into the app and
  renamed to `defpackage`/`ws`/`zt`. This is why the original package search was empty.
- `DockedNavigationBar` / `FloatingNavigationBar` + `NavigationPill`:
  - `BarBlurRadius = 40.0f`.
  - API 33+ progressive alpha mask:
    `uniform shader content; uniform float2 size; blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);`
  - API 33+ refraction shader (`Refraction`) with `cornerRadii`, `refractionHeight`,
    `refractionAmount`, `depthEffect`, optional `chromaticAberration`.
  - Compose `graphicsLayer`/`RenderNode` capture and `RenderEffect.createBlurEffect(...)` +
    `RenderEffect.createRuntimeShaderEffect(..., "content")`, chained with the blur.
  - Measured lens values on the settings page: `16f` height, `32f` amount, `depthEffect=true`,
    `chromaticAberration=true`.
- This is a **progressive, refractive** glass; a plain uniform blur over a gradient is not the same
  effect. Our `backdrop` surface now uses `blur(40f)` + `lens(16f, 32f, depthEffect=true,
  chromaticAberration=true)` on the navigation bar, and keeps a calmer `blur(18f)` for content cards.

## 3. Architecture

- A single `GlassSurface` composable owns shape, tint, border and shadow.
- API gates live in one place:
  - API 33+: allow the runtime-shader/lens path if supported.
  - API 31–32: blur only.
  - <31: translucent fallback without blur; no crash, no invisible surface.
- Blur radius is capped (start 16–20) and measured; no continuous animation over the blur.
- The glass layers are app cards/sheets/navigation only; the module's SystemUI views are untouched.
- Existing Compose screens keep their current data flow; this phase changes presentation, not hooks.

## 4. Work breakdown

| # | Task | Verification |
|---|---|---|
| 5.1 | Dependency spike: Kotlin 2.2.21, Compose BOM 2025.10.01, backdrop 1.0.1 | `assembleDebug`/unit tests |
| 5.2 | Add `GlassSurface` + API capability probe; no-op fallback | Robolectric/unit + manual |
| 5.3 | Apply to Home/Engine/Settings surfaces, keep three-page navigation | TP-030 |
| 5.4 | Cap radius and measure with `dumpsys gfxinfo` on CPH2747 | TP-031 |
| 5.5 | String audit: English only | TP-032 |
| 5.6 | Screenshots API 31/33/older + visual sign-off | owner |

## 5. Test plan

- TP-030: open/back through Home → Engine → Settings; each page keeps state; system back returns.
- TP-031: on API 31 and 33, the glass renders; below 31 the fallback is visible and opaque enough;
  `gfxinfo` frame times stay within the existing budget.
- TP-032: `grep` user-facing strings; no Arabic or mixed-language copy in the app resource set.
- Regression: existing unit suite (319) stays green; Rive binary untouched.

## 6. Exit gate

Three pages approved, glass visible on API 31/33, no fallback crash on older, no jank measurement
regression, and no SystemUI surface touched. No merge until owner visual approval.

## 7. Current status

- Branch created.
- Dependency spike is green:
  - Kotlin upgraded to 2.2.21; Compose BOM `2025.10.01`; `backdrop:1.0.1`.
  - `:app:compileDebugKotlin`, unit tests, and release assembly succeed.
- 5.2/5.3 first pass implemented:
  - `GlassBackdrop` records a dark gradient + two colour blobs into a `LayerBackdrop`.
  - `GlassSurface` draws the backdrop through a rounded shape with `blur(18f)`, a low tint and border.
  - Navigation bar, Home cards, Engine panel, and all settings Cards use the glass surface.
  - The SystemUI module is untouched; glass is app-only.
- 319 tests, 0 failures.
- APK `2a45a60c48332a40829d3d04ce59e8cf133ada8261314d48d4be3d258de66afa`
  (`7,071,160` bytes, `1.5.0-beta.8`, code 38). The navigation bar is a floating pill over the page
  content, with `vibrancy()` + `blur(8dp)` + `lens(24dp, 32dp, depthEffect=true,
  chromaticAberration=true)` and `onDrawSurface` tint. Page content draws behind the bar.
- Pressing/dragging now reacts on the whole pill: scale `1.02`, blur `12dp`, lens `30/42dp`, brighter
  surface. The selector scales to `1.16` while held and follows the finger, then snaps.
- Backdrop docs correction: `blur`/`lens` are **pixel** arguments, so the earlier `16f/32f` was
  ~5/10dp at this density. The current code uses `dp.toPx()` and `vibrancy()`.
- The selector is a moving **capsule the width of a tab** (not a 52dp circle), with
  `lens(10dp, 14dp, chromaticAberration=true)` and a blue/white surface. It is draggable with
  `detectDragGestures`: while held it scales/focuses its lens, follows the finger, and on release
  snaps to the nearest tab with a spring and selects that page. Taps on the bar select directly
  without a Material ripple/box.
- RC-017 fixed the app crash: cards inside the recorded `LayerBackdrop` were recursively sampling the
  same layer. The page content now sees a null local backdrop; only the floating bar samples the layer.
  Device verification: `pidof` alive after launch, crash buffer empty, activity focused.
- Still pending: visual approval/screenshots, `gfxinfo` measurement, string audit, and the API-31
  fallback (the app's minSdk is 33, so only the API 33+ path is currently shipped).
- `1.5.0-beta.4` was installed and launch-verified on CPH2747; `beta.6` was built and relayed after
  ADB went offline, so it still needs installation/visual check.

## 8. Apple-reference implementation (2026-10-29, beta.13)

The owner asked for real Apple numbers, not tuned guesses. The values below are the published iOS 26
design-kit transcriptions (light angle −45°, material opacity 60%, frost 14 px large, depth 16,
layered shadow 0/8/28 at 14%), Apple's regular/thick material ranges (140–160% saturation, 40–48 px
nav blur, 18% tint), and the Kyant Backdrop tutorial (`vibrancy()` = 1.5 saturation, lens 16/32 dp,
effect order colour filter → blur → lens). They live in one place: `GlassTokens`.

| Material | Blur | Tint | Lens | Notes |
|---|---|---|---|---|
| Navigation pill | 40 dp | white 16% | 16/32 dp, depth + chroma | picks up press to +4 dp |
| Settings card | 24 dp | white 12% | none | readable on the dark wallpaper |
| Switch track / slider track | 12 dp | white 10–14% | 6/9 dp | check/slider states |
| Slider thumb | 8 dp | white 82% | 5/7 dp | glass bead |
| Scroll edge | 24 dp | mask | none | `DstIn` gradient mask |

### 8.1 Selector = circle at rest, slime capsule while dragging

The old selector was a tab-width capsule even at rest (the owner's complaint). It is now:

- idle: a 54 dp circle with `RoundedCornerShape(50)`; width == height;
- drag: width grows to 54 + 42 dp and height gives 6 dp, so it becomes a capsule;
- the stretch is driven by distance from the nearest tab plus a small velocity term, so it visibly
  grows and shrinks as it passes tab centres;
- release: spring to the nearest station and spring the stretch back to 0 (circle again);
- the halo follows the selector centre and leads it by 10 dp while dragging.

### 8.2 Every toggle and scroll

- `SettingSwitch` → `GlassSwitch` (glass track + bright bead; Material fallback when no backdrop).
- `LabelledSlider` → `GlassSlider` (glass track, wine fill, glass thumb; semantics kept).
- `DuoPreview` toggle/slider use the same controls.
- Settings scroll gets `ScrollEdgeGlass`: top 72 dp / bottom 92 dp, blurred content masked by a
  `DstIn` gradient inside an offscreen layer, so the edge fades instead of showing a hard rectangle.

### 8.3 Backdrop separation (RC-017 hardening)

`GlassBackdrop` now records two layers:

1. `wallpaper` — gradient only, provided to content/cards/controls;
2. `page` — page content, used only by the floating bar through `rememberCombinedBackdrop`.

Cards and controls therefore sample the wallpaper, not the layer that contains themselves. This keeps
the earlier RC-017 SIGSEGV fix while allowing real glass cards again.

### 8.4 Remaining before sign-off

- Device screenshots of Home/Engine/Settings, selector idle/drag/release, and both scroll ends.
- `gfxinfo` while scrolling and dragging; lower `CardBlur` if frame time regresses.
- Light-mode text screenshot (the shell wallpaper is dark).
- Report severity buckets are now defined in `agent/PHASE-1-5-WALKTHROUGH.md` §6.

### 8.5 Beta.14 build (2026-10-29)

- Version `1.5.0-beta.14`, versionCode 44.
- `:app:testDebugUnitTest`: 319 tests, 0 failures, 0 errors, 0 skipped.
- `:app:assembleRelease`: success.
- APK `7,093,116` bytes, SHA-256
  `e5957a7e6e1fdd12aabc2cdfd134500f7ba8bc0e82edf42495fe31a541a6209a`.
- Adds the iOS 26 layered depth shadow to glass surfaces (18 dp nav, 8 dp cards, 14% ambient/spot).
- `tools/route-module-logs.py --check`: 0 files to fix, 0 log calls left.
- `tools/check-rive-eases.py`: 138 easing keyframes, 138 carry a curve, 0 inert (the tool's
  default path typo `riva/` was corrected to `rive/` in this change).
- ADB saw no device during the build, so this APK is **not installed** yet.




## FILE: PHASE-5-REPORT.md

# Phase 5 report — beta.14 (2026-10-29)

**Prototype lock:** Phase 5 is frozen at `1.5.0-beta.14` (code 44), APK
`e5957a7e6e1fdd12aabc2cdfd134500f7ba8bc0e82edf42495fe31a541a6209a`. The selector physics, glass
tokens, toggle/slider materials and scroll edges are no longer changed by Phase 6–8 work. Later builds
may contain the phase-6 charging motion, but no Phase 5 visual retune.

Build: `1.5.0-beta.14`, versionCode 44, branch `phase/5-liquid-glass`.
APK: `7,093,116` bytes, SHA-256 `e5957a7e6e1fdd12aabc2cdfd134500f7ba8bc0e82edf42495fe31a541a6209a`.

Fixes are sorted by severity, as requested: **critical**, then **moderate**, then **minor**. This is a
build-and-static review; the device has not run this APK yet (ADB was empty during the build).

## Critical fixes

### C-01 — Recursive glass capture (RC-017) hardened
- **Symptom:** opening the app killed it immediately with `SIGSEGV` in `RenderThread` (stack overflow).
- **Cause:** cards were recorded into a `LayerBackdrop` and also sampled the same layer.
- **Fix:** `GlassBackdrop` now records two separate layers: `wallpaper` (gradient only) for content,
  cards and controls, and `page` (content) for the floating bar through `rememberCombinedBackdrop`.
  No glass surface samples the layer that contains itself.
- **Why it matters:** this is the only known crash that takes the whole app down. It gets the critical
  bucket even though the immediate trigger was fixed before beta.12; the new architecture removes the
  mechanism instead of relying on "do not add a card there".
- **Verification:** compile + 319 tests + release build pass. **Device launch is still required.**

## Moderate fixes

### M-01 — Stationary selector was a pill, not a circle
- **User report:** "وقت ما يكون ثابت حقنا وضعيته بيل مو دايري" / "السيليكتور زي السلايم".
- **Change:** idle selector is now a 54 dp circle (`width == height`, 50% capsule shape). While dragging
  its width grows to +42 dp and its height gives 6 dp, so it becomes a capsule; release springs the
  stretch back to zero and the selector returns to a circle on the nearest tab.
- **Slime behaviour:** stretch is driven by distance from the nearest tab plus a small velocity term,
  so it visibly grows and shrinks while crossing tabs.
- **Not yet device-shot.**

### M-02 — Pill was too transparent at rest
- **User report:** "البيل وقت ما أكون مو ماسك السيليكتور شفاف جداً".
- **Change:** the pill is blurred at rest (`12 dp`) and stronger while held (`16 dp`); lens is 24/32 dp
  at rest and 30/42 dp interactive, with the Apple 1.5 saturation / 0.04 brightness / 1.5 contrast.
- **Scope:** `GlassTokens` + `GlassSurface`.

### M-03 — The selector did not use the pill/logos as its backdrop
- **Change:** selector uses `rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)`, is drawn after the
  pill layer, and uses `RoundedCornerShape(50)` so it can overflow the pill without clipping.
- **Effect:** moving the selector refracts the page, the pill and the tab icons, not only wallpaper.

### M-04 — Every toggle was still Material
- **Change:** `SettingSwitch` now calls `GlassSwitch`; `DuoPreview` uses the same control. The track is a
  glass capsule (`12 dp` blur, lens 6/9), the knob is a bright bead, and the checked state is a wine
  tint instead of an opaque fill. Material `Switch` remains only as a no-backdrop fallback.
- **Scope:** all settings toggles (master, percent, bolt, split, live apply, lock ring, animations,
  renderer, clock font, airplane, Wi-Fi dots, events, updates, icon hiding).

### M-05 — Every slider was still Material
- **Change:** `LabelledSlider` now calls `GlassSlider`; the preview sliders use it too. The track is
  glass with a wine fill, the thumb is a glass bead with shadow, and the semantics
  (`progressBarRangeInfo` / `setProgress`) are preserved. Steps still snap.
- **Scope:** percent height, size, edge padding, animation speed, battery/Wi-Fi/cellular previews.

### M-06 — Scrolling had no glass edge
- **Change:** `ScrollEdgeGlass` adds a top 72 dp and bottom 92 dp material over the page, blurred from
  the page backdrop and masked by a `DstIn` vertical gradient inside an offscreen layer. The content
  scrolls under it; the edge fades instead of ending as a hard rectangle.
- **Scope:** app shell only; the SystemUI shade/QS is untouched.

### M-07 — Glass numbers were ad hoc
- **Change:** one `GlassTokens` object now owns blur, saturation, brightness, lens, tint and shape.
  Values come from the published iOS 26 design-kit transcription (light −45°, opacity 60%, frost 14,
  depth 16, shadow 0/8/28 at 14%), Apple's material ranges (40–48 dp navigation, 140–160% saturation,
  18% tint) and the Kyant tutorial (1.5 saturation, lens 16/32, colour → blur → lens order).
  `dp.toPx()` is applied at the draw site; raw `16f` was the earlier flat-glass bug.
- **Scope:** `GlassSurface.kt`, `GlassControls.kt`.

### M-08 — Cards had silently fallen back to Material
- **Change:** with the wallpaper/page split, `GlassCard` uses real glass again at 24 dp with a 12%
  white tint. This is the visual improvement, but also the main performance risk; measure `gfxinfo`.
- **Rollback:** set `GlassTokens.CardBlur` down or restore the Material fallback if frame time regresses.

### M-09 — Glass lacked the iOS depth shadow
- **Change:** `GlassSurface` now applies the iOS 26 layered shadow (0/8/28 at 14%) through Compose
  `shadow` with matching ambient/spot colours; navigation uses 18 dp, cards 8 dp. The shadow is drawn
  before the glass and is not part of the sampled layer.
- **Scope:** `GlassSurface.kt`.

## Minor fixes

### m-01 — `check-rive-eases.py` default path typo
- `riva/duo/scene.rml` → `rive/duo/scene.rml`. The checker had been failing with `FileNotFoundError`
  instead of checking the scene. Now: **138 easing keyframes, 138 carry a curve, 0 inert**.

### m-02 — Documentation
- Added `agent/PHASE-1-5-WALKTHROUGH.md`: the phase-by-phase walkthrough, review matrix, enhancement
  backlog, gap-closure waves and the severity policy.
- Updated `agent/PHASE-5-PLAN.md` with the Apple-reference table, selector physics, control/scroll
  work and the beta.13 evidence.

### m-03 — Code hygiene
- Removed the selector's old `lensX`/`lensScale` path and unused animation import.
- Added `GlassControls.kt` so the switch/slider logic is not duplicated in each section.
- `L.kt` still imports `android.util.Log`; that file is the sanctioned log router, not a violation.

## Verification performed

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 319 tests, 0 failures, 0 errors, 0 skipped |
| `:app:assembleRelease` | success |
| `tools/route-module-logs.py --check` | 0 files to fix, 0 log calls left |
| `tools/check-rive-eases.py` | 138/138 eased, 0 inert |
| `duo.riv` SHA-256 | `540bb0884324b1f4303b22debdfb943cb4ad9bc04ce3fba0cacaa3896269e201` (unchanged) |
| `adb devices` | empty; no install/launch on CPH2747 |
| Telegram relay | HTTP 200, `{"ok":true}`; final ZIP SHA-256 `034d9f52900a3d9bedca8d9c733e6ba01a9209d55938fb062fd55deb100c9b56` |

## Open after this report

- Install beta.13 and capture: selector idle circle, drag capsule, release circle, both scroll ends,
  switch on/off, slider drag.
- `dumpsys gfxinfo` while scrolling/dragging; reduce card blur if needed.
- Light-mode screenshot: the shell wallpaper is still dark, so black text in light mode may be weak.
- Device launch check for RC-017 (no crash, `RenderThread` clean).
- Phase 1–4 evidence walk is still pending; see `PHASE-1-5-WALKTHROUGH.md`.


## FILE: PHASE-6-REPORT.md

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


## FILE: PHASE-7-REPORT.md

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


## FILE: PHASE-8-REPORT.md

# Phase 8 report — release readiness

Status: **code and gates complete; release is blocked on device/owner approval.** Branch
`phase/8-release`, from `phase/7-multi-ui-perf`.

Build: `1.5.0-beta.20`, versionCode 50.
APK: `7,095,720` bytes, SHA-256
`6541582bb19e67397d5658ae8b10a2e12a54aafd024fc829fcb74b68201e548d`.
Rive: `636e8c9a9a721cb982f5c451bdddc784004e890bc16201b2b42b15282b410e9b` (packaged == build).
Relay ZIP: SHA-256 `b1a601f28f65d30da893b0d0da4f70cc0f1ef327dae2429fd55d19d5b3479583`.

## Gates

| Gate | Result |
|---|---|
| Unit tests | **322 pass**, 0 failures, 0 errors, 0 skipped |
| Release assembly | **success** |
| Log routing | 0 files to fix, 0 `android.util.Log` calls outside `L` |
| Rive easing | 142/142 keyframes eased, 0 inert |
| `.riv` identity | build and packaged hashes match |
| Emulator launch (API 36) | success, no app crash; Settings reached |
| Emulator frame budget | **FAIL** — 119 frames, p95 216.88 ms, max 308.39 ms (budget 32/120); Android Settings baseline on the same emulator is p95 116.87 ms |
| Device (CPH2747) install/visual | **not done** (device offline) |
| Owner approval | **not given** |
| OEM asset removal | **not done** (prototype icons remain in `agent/event-icons/prototype/` only) |

## Release classification

- `1.5.0-beta.20` is a **pre-release**. Do not publish to the LSPosed store.
- Do not merge to `main` before the owner approves the visual result and the device run.
- The APK is safe to side-load for testing: the module remains off until the owner enables it, and the
  kill switch / safe mode paths are unchanged.
- The emulator performance failure is a gate failure, not a cosmetic warning. It is not waived.

## Open blockers, in order

1. **CPH2747 run:** install beta.20, enable the module, capture Home/Engine/Settings, selector idle →
   drag → release, both scroll ends, and `gfxinfo` after a deliberate Settings scroll.
2. **Frame budget:** if the device also exceeds p95 32 ms, keep the static control material and lower
   navigation blur/lens before release; do not ship a slow glass layer.
3. **Visual approval:** the selector must be a circle at rest and a capsule only while dragged; the
   owner has not signed this build off.
4. **Phase 6 sources:** mic/location/volume still have no measured source; do not advertise them.
5. **OEM icons:** prototype art must be replaced or explicitly accepted before a stable release.
6. **Phase 1 safe mode:** `fix/landscape-safe-mode` remains unmerged/unverified; do not claim a fix.

## Recommendation

Ship nothing stable yet. The beta is a review build. When the owner is awake, run the device gate,
attach the dump/screenshots to this report, and only then decide between `1.5.0-rc.1` and another beta.


## FILE: PLAN-1.6.md

# Duo Status Bar — Implementation Plan 1.6

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Version | 1.6 |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Plan ready for owner review. No 1.6 code started by this plan; the worktree already contains the pre-1.6 beta.45 build input (see §2.0). P0 must be approved first.** |
| Requirements | `agent/REQUIREMENTS-1.6.md` |
| Tests | `agent/TEST-PLAN-1.6.md` |
| Brainstorm | `agent/BRAINSTORM-2026-11-02.md` |
| Evidence notes | `agent/LIST-THE-PLAN.md`; `reverse/fatal-logs/`; `reverse/kilo-images/` |
| Baseline | beta.45 (code 75), `app/build/outputs/apk/release/app-release.apk`, SHA-256 `0e359a7953ca3be7f19b7dcefaa8e1da316e834646bbdeed87d60ca7dee3f1ba`; APK path is gitignored, copy it to rollback before any clean |

This plan is traceable: every task maps to requirement IDs, and every requirement has a test in `TEST-PLAN-1.6.md`. No code before P0 review/approval. No cosmetic work may land before P0's exit gate.

---

## 1. Objective

Ship a stable 1.6 that:

1. Eliminates the two proven SystemUI fatal paths.
2. Delivers the three-tab shell with the new icon set, transparent selector, hold-drag interaction, and the measured resting/held sizes.
3. Moves all customization into Customization with persistent Default/Compact/Custom snapshots and a change-driven live preview.
4. Decomposes the ring into Rive objects: one centre object (Wi-Fi + event), a two-seat **battery percentage** (not the removed Charging %), a dedicated clock, eight SIM dots, and a bottom battery line with a split transition.
5. Makes download progress override charging, returns to normal at completion/100%, and integrates PHDP honestly.
6. Replaces simulation/data sections with GIF previews and removes the approved dead controls.
7. Preserves Canvas parity and marks other UIs truthfully.

---

## 2. Ground truth

### 2.0 Current worktree baseline (must preserve before P0)

The true baseline is **HEAD `670734d` + a dirty working tree**, not HEAD alone:

- Branch `phase/8-release`; HEAD is beta.36 and does not compile against the worktree because `DuoShell.kt` imports untracked `DuoHomeScreen.kt`, `DuoReportScreen.kt`, `DuoDesign.kt`.
- 18 modified files, 20 untracked top-level entries; no stash/branch contains the worktree blobs.
- The dirty tree is the beta.45 build input; `app-release.apk` SHA-256 `0e359a…f1ba` matches the plan and embeds the worktree `duo.riv` (`e4c4f4…716d`), not HEAD.
- P0 files (`DuoIconHost.kt`, `DuoRiveView.kt`, `DuoHook.kt`, `DuoGuard.kt`) are clean.
- `AppContextResolver.kt` contains an accidental ~5.7 KB tool-list paste in a KDoc; fix in its own commit, never mix with P0.
- Pre-1.6 UI/report/relay/unlock changes are mixed into the same dirty tree; they conflict with parts of 1.6 (4 tabs, wine selector, Material icons) and must not be assumed to be 1.6 work.
- The only beta.45 APK is under gitignored `app/build/outputs/apk/release/`; copy it to a stable rollback path before any clean.

**P0.0 (preserve baseline, no feature code):**
1. Create a WIP branch/commit from HEAD with only app/rive/relay/gradle/agent work; exclude `opencode-arabic/`, `report-bot/`, `from users/`, `docs/evidence/`, `.github/`, and PII-bearing logs.
2. Copy the beta.45 APK to a stable rollback path.
3. Revert the `AppContextResolver.kt` comment corruption separately.
4. Branch `fix/1.6-stability` from the preserved WIP baseline, not HEAD.
5. Only then apply P0.

### 2.1 Crash evidence (P0)

- **A. QS rotation Java crash:** `system_app_crash@1793641719781.txt` — `ConstraintSet.clone` throws because an injected `DuoRiveView` has `id=-` inside OnePlus's `OplusQSRootViewComponent` `ConstraintLayout`. `DuoIconHost.kt:972` calls `addView`; target logged as `ConstraintLayout[1272x2772]` at `DuoIconHost.kt:984-987`.
- **B. JNI overflow:** `tombstones/tombstone_03` — 444 `FallbackAssetLoader` refs (≈444 `.riv` imports inferred) and 48,771 `Paint` global refs (~109.8 Paint/import), hitting the 51,200 limit. The LSPosed log shows the attach/import storm while `lock_ring=true`; the same storm repeated on restarts. Stack: `DuoHook.addShadeLayoutListener → attachExtraBars → DuoIconHost.attachInto → syncExtraIndicators → ensureCluster → RiveAnimationView.onAttachedToWindow → File::import`.
- **C. LuckyTool:** `tombstone_01`/`tombstone_28` — separate boot SIGABRT in `libdexkit.so` on Oct 31; different time/PID. Not the Nov 2 cause.

### 2.2 Owner decisions

- Three tabs: Home / Customization / Report; Settings from a gear on Home.
- Camera/Compact: main status bar only; QS/shade/keyguard normal; tap opens Resize sheet; no live view resize.
- Remove Charging animation + Charging % middle option.
- Keep Icon Colour + add white↔black GIF.
- Calls: UI removed, event permanently off, code kept.
- Rive primary, Canvas kept.
- PHDP test button opens the PHDP app; the user runs PHDP’s own test.
- Split battery design is `new duo customization`: number above, Wi-Fi centre, eight SIM dots, battery line bottom.

### 2.3 Design assets

- Icons: filled house, sliders, documents+trend. Redraw as original vectors; the report reference is Icons8 stock and must not be shipped as-is.
- Selector: rest 225×179 px inside 207 px pill; held 296×231 px; ≈1.30×; held overflows; active icon grows.
- Ring: 8 dots (4/side), battery line, number above, Wi-Fi centre.
- GIFs: original Duo appear/disappear on black; event previews; icon-colour white↔black.

---

## 3. Branch and safety model

- Base: current stable branch after beta.45. Create one branch per phase: `fix/1.6-stability`, `feat/1.6-shell`, `feat/1.6-ring`, `feat/1.6-downloads`, `feat/1.6-burnin`, `release/1.6`.
- P0 is the only branch allowed to touch `DuoIconHost`/`DuoRiveView`/`DuoHook` first; all other branches rebase on it.
- Keep beta.45 APK and settings export as rollback.
- Never clear logs on the owner's phone before pulling.
- A failed P0 test stops the line; do not proceed to UI.
- Any new surface/class/hook must be measured; otherwise mark `unverified`.

---

## 4. Phase overview

| Phase | Goal | Requirements | Exit gate |
|---|---|---|---|
| P0 | Stop QS rotation + JNI crashes; 100% charging; preserve beta.45 baseline | STB-01…08; CUS-16 main-bar guard | STB-01…08 pass; 20 rotations + 60 s shade/camera stress: zero fatal, stable PID, no safe mode |
| P1 | Three-tab shell, icons, selector, presets, settings cleanup, GIFs, sliders, report move | NAV-01…08; CUS-01…15, CUS-17 | Compose/device cases; no dead control; preset round-trip; storyboard drafted |
| P2 | Rive ring decomposition, clock, eight dots, split, camera, Canvas parity | RING-01…10; CUS-16 full camera/Resize | Headless storyboards; owner approves split before RING-05; hash/ease; Canvas parity |
| P3 | Download/PHDP precedence and UX | DL-01…09 | Real/simulated download cases; no charging regression |
| P4 | Burn-in protection | BURN-01…03 | 2 cycles + 8 h battery |
| P5 | All-UI verification, regression, docs, release candidate | QA-01…05 | Matrix + full suite + owner install |

---

## 5. P0 — Stop SystemUI deaths (blocker)

### P0.1 Stable ID for injected views

**Requirement:** STB-01, STB-04, STB-08  
**Files:** `app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoIconHost.kt` (attach path around 960–1000), any other `addView` path in `DuoIconHost`/`DuoHook`; tests.  
**Task:**
- Assign a stable unique ID at **every injected-view creation** before any `addView`, not only when the runtime parent happens to be a `ConstraintLayout`. Audit all sites: `DuoIconHost.kt` 972, 1414, 1439, 1485, 1722, 1797–1798, 1811/1819, 2036, 2108, 2307, 2314; `DuoRiveView.kt:154`; `ContainerFinder.kt:330` fallback parent.
- Use one constant ID per surface/instance; never reuse duplicate IDs in the same parent.
- Prefer a host container that does not require IDs if that is the existing measured route; fall back to a plain `FrameLayout` only if the contract proves it.
- Log parent class + id in every attach line; add a debug detector that fails if any injected child has `View.NO_ID` in a `ConstraintLayout`.
- Do not change view hierarchy/geometry in this task.

**Weapons:** `rg`, `logcat`, LSPosed verbose, `dumpsys`, `./gradlew test`, `T-STB-01`.

**Exit:** QS rotation 20×; no `ConstraintSet.clone`; log shows the ID.

### P0.2 One Rive view/import per surface

**Requirement:** STB-02, STB-03, STB-07  
**Files:** `DuoRiveView.kt` (load/attach path, `ensureCluster` callers), `DuoIconHost.kt` (`ensureCluster`, `syncExtraIndicators`, `attachInto`, `attachExtra`), `DuoHook.kt` (`attachExtraBars`, shade layout listener), `DuoGuard` (counters/breaker), tests.  
**Task:**
- Remove `.riv` import from any layout/attach callback that can run per pass. Cache one view per surface lifetime and reuse it.
- Never remove/re-add or reparent the cached Rive view on layout, shade, size, split, or preset changes. `onAttachedToWindow` runs **once per surface lifetime** (bar + configuration).
- A configuration change may destroy the old surface and create one new surface after the new config is known; account for native refs, and never create a new view while the old one is attached. No other detach/reattach is allowed.
- Add counters: `riveImports`, `riveAttachCount`, `liveRiveViews`, `duoViewReady`; log only on change; expose in diagnostics.
- Add a hard guard: if imports/attaches exceed a safe threshold per process, stop creating views and use Canvas; log the reason.
- Keep the Rive breaker and Canvas fallback.

**Weapons:** `T-STB-02`, LSPosed import/global-ref count, tombstone, `DuoGuard`, `review-strict`.

**Exit:** 60 s shade stress: one attach per surface lifetime, imports do not grow per layout; no JNI abort; no restart.

### P0.3 Camera/lock mode without reattach

**Requirement:** STB-03, CUS-16  
**Files:** `DuoIconHost.kt` (camera/lock placement), `DuoHook.kt` (layout listener), `DuoPrefs` (`lockRing`), Rive pose.  
**Task:**
- Camera/lock mode uses one fixed view and inner translation; no reparent/resize on shade/layout.
- QS/shade/keyguard force the Default placement; only the main bar gets camera placement.
- “Resize” opens the sheet; it does not resize the live view.
- Add a device test that toggles only `lock_ring=1` and watches attach rate.

**Weapons:** `T-STB-03`, `T-CUS-16`, phone, LSPosed logs.

**Exit:** 60 s camera mode: attach count stable; no JNI overflow.

### P0.4 Rotation lifecycle

**Requirement:** STB-04, STB-08  
**Files:** `DuoHook.kt:501-514`, `DuoIconHost.kt:1305-1328`, `DuoRiveView.kt`.  
**Task:**
- Cancel/defer pending attach on configuration change; wait for the new configuration before creating a surface.
- No live `TextureView` resize/reparent; use the current measured capture-on-attach rule.
- Test portrait↔landscape with QS closed/open and camera on/off.

**Weapons:** `T-STB-04`, rotation harness, `dumpsys`, screencap.

**Exit:** 20 rotations per mode; no fatal/ANR; ring returns.

### P0.5 100% charging

**Requirement:** STB-06  
**Files:** `DuoStateMonitor.kt` charging state, `DuoMapping.kt`, `DuoIconHost`/Rive bindings, `DuoCanvasView.kt`.  
**Task:**
- Treat 100% + plugged as non-charging for visuals; hide bolt/stop charging loop; keep battery normal.
- Test 100%, 99%, unplugged, replug.

**Weapons:** battery dumpsys/root or emulator, `T-STB-06`, screenshots.

### P0.6 Crash telemetry and repair catalog

**Requirement:** STB-05  
**Files:** `Diag.kt`, `DuoGuard`, `agent/REPAIR-CATALOG.md`, `agent/AGENT.md`.  
**Task:**
- Persist `qs_constraint`, `rive_import`, `jni_refs` markers **before** the risky attach/import so the next boot can report which path preceded a SystemUI death.
- Record both signatures and the safe recovery (Canvas, restart prompt).
- Update the mistakes registry.

**Weapons:** `T-STB-05`, report export, `review-strict`.

**P0 exit:** all STB tests pass; owner confirms no safe mode; no UI work merged.

---

## 6. P1 — Shell, settings, interactions, GIFs

### P1.1 Three-tab shell + Settings gear

**Requirements:** NAV-01, NAV-08  
**Files:** `DuoShell.kt` (enum, nav items), `DuoScreen`/`MainActivity`, `DuoHomeScreen.kt`, `DuoReportScreen.kt`, strings.  
**Task:**
- Pages: `HOME`, `CUSTOMIZATION`, `REPORT`.
- Remove bottom `SETTINGS` and `ENGINE` pages; Settings opens as a sheet/page from a gear on Home.
- Home: status/health, preview, gear; no editing controls.
- Customization: presets + live preview + controls.
- Report: form + Contact Developer.

**Weapons:** Compose tests, screenshots, `T-NAV-01/08`. Add the missing `app/src/androidTest` Compose harness for nav/selector/slider interactions (none exists today).

### P1.2 Redraw icons

**Requirements:** NAV-02  
**Files:** `DuoIcons.kt`/`ui/icons`, drawables, `strings.xml`; no stock JPG/PNG.  
**Task:**
- Redraw house, sliders, documents+trend as original vectors matching all six references.
- Licence-review every reference; the report icon is Icons8. Do not ship any stock raster or traced path data.
- Add 1×/2× screenshots and a licence note covering all six sources.

**Weapons:** VectorPath/Compose, ImageMagick, `T-NAV-02`.

### P1.3 Selector

**Requirements:** NAV-03…06  
**Files:** `DuoShell.kt` selector block, `DuoColors`, `GlassTokens`.  
**Task:**
- Remove wine fill; add a `NavAccent` token (reference blue `#3C88F8`, no blue exists today); active icon/label accent; inactive neutral.
- Rebuild selector as a large ellipse around the active item; rest/held sizes from measurements; active icon scales.
- Move gesture ownership to the pill parent; long-press starts drag, selector jumps to pointer, follows, snaps.
- Preserve tap select/accessibility; no scroll theft.

**Weapons:** Compose `pointerInput`, screenshot measurement, `T-NAV-03/04/05/06`.

### P1.4 Slider and position interaction

**Requirements:** CUS-12, CUS-13  
**Files:** `GlassControls.kt:214-240`, `DuoSettingsComponents.kt:237-291`.  
**Task:**
- Knob-only drag with a ≥48 dp hit target; track taps no-op except TalkBack `setProgress`.
- Position icon-only drag; rail no drag.
- Test vertical swipes over tracks.

**Weapons:** Compose tests, `T-CUS-12/13`.

### P1.5 Preset state

**Requirements:** CUS-02, CUS-03  
**Files:** `DuoPresets.kt`, `DuoPrefs.kt`, `DuoHomeScreen.kt`, Customization page, backup.  
**Task:**
- `DuoPresetState` with `selected`, fixed `default`, fixed `compact`, `lastCustom`; persisted **per orientation** (portrait and landscape).
- Default = Wi-Fi centre, auto 5G/4G, percent above.
- Compact = camera ring + Resize; main bar only.
- Editing while Default/Compact switches to Custom and saves; Custom restores exactly.
- Migrate old `classic` → Default; old `compact`/`bold` → Custom to preserve pixels (new Compact is a camera ring, so it must not inherit old compact). Verify with old prefs/backup.
- Version backup schema.

**Weapons:** unit tests, preference/backup round-trip, `T-CUS-02/03`.

### P1.6 Settings cleanup and GIFs

**Requirements:** CUS-04…11, CUS-14, CUS-17  
**Files:** `DuoSettingsScreen.kt:534-663`, `DuoSettingsSections.kt`, `DuoPrefs.kt`, `DuoMapping.kt`, `DuoStateMonitor.kt`, drawables/strings.  
**Task:**
- Remove Smooth Graphics, System-Font Clock, the charging animation toggle (`chargingEnabled`), and `MIDDLE_SLOT_CHARGING` only; keep the separate `MIDDLE_SLOT_PERCENT` Battery % option and the charging bolt.
- Keep Icon Colour + white/black GIF.
- Remove Mic/Location/Volume from UI/code; Calls UI off/code kept.
- Remove Simulation + Cell Simulation; the four remaining event rows get GIF previews (Alarm, Flashlight, Screenshot, Unlock); no Play/level. Duo on/off and Icon Colour GIFs are separate.
- Remove duplicate About/Report; move Contact to Report; keep health/backup/update.
- Fix Wi-Fi glyph; no restart.
- Update search index.

**Weapons:** Compose, `AnimatedImageDrawable`, headless GIF generator, `T-CUS-*`, `T-CUS-14/17`.

### P1.7 Engine and preview

**Requirements:** CUS-01, CUS-04, CUS-15  
**Files:** `DuoShell.kt` EnginePage, Customization page, `DuoRivePreview`, `DuoPrefs.engineMode`.  
**Task:**
- Move engine selector into Customization; Rive default, Canvas fallback with note.
- Live preview always visible; no continuous loop; invalidate on edits; persist.
- Tapping the ring focuses/scrolls to controls.

**Weapons:** frame stats, Compose tests, `T-CUS-15`.

### P1.8 Duo on/off GIF

**Requirement:** NAV-07  
**Files:** `res/drawable`/assets, docs media, generator.  
**Task:**
- Generate original icon-appear/disappear on black; same loop style as prior GIFs.
- Record source frames/hash; no Shutterstock frames.
- Use `AnimatedImageDrawable`; start on attach.

**Weapons:** Rive frames/ImageMagick, `T-NAV-07`.

### P1.9 Split-transition storyboard (design, no code)

**Requirement:** RING-05 (draft only)  
**Task:** turn the `new duo customization` static layout into a short frame storyboard for the battery-line split and SIM-dot/battery reflow. No code; owner reviews the frames.  
**Weapons:** ImageMagick/Inkscape, Rive storyboard, owner review.

**P1 exit:** NAV and CUS-01…15/17 tests; preset persistence; no dead controls; screenshot review; split storyboard drafted. CUS-16 full camera is P2; its main-bar guard is P0.

---

## 7. P2 — Rive ring decomposition

### P2.0 Read and freeze

**Requirement:** RING-09  
**Files:** `docs/rive-pipeline.md`, `rive/duo/AGENTS.md`, `rive/duo/scene.rml`, `tools/check-rive-eases.py`.  
**Task:** read the Duo Rive system; record artboard, layer order, states, 8-digit ARGB, `StraightVertex` rule, 0.2 s final hold, no constraints.

### P2.1 Shared geometry table

**Requirement:** RING-08  
**Files:** Kotlin geometry (`DuoMapping`/new table), Canvas, Rive bindings.  
**Task:** one source for ring radius/stroke, centre, percent/clock boxes, 8 dot angles, battery line angles, camera offset. Unit-test values. Rive constants derived from the table.

### P2.2 Centre object + event

**Requirement:** RING-01, RING-07  
**Files:** `scene.rml` `middleFadeGroup`/`eventGroup`, `DuoMapping`, `DuoCanvasView`.  
**Task:**
- Parent both Wi-Fi and event art under one `centerSlot`.
- Event takes the slot, then restores occupant; no separate seat.
- Add Rive state/transition; Canvas clone.

### P2.3 Battery % two seats

**Requirement:** RING-02, RING-06  
**Files:** `scene.rml` percent group, `DuoMapping` seat flag, `DuoPrefs` constraints.  
**Task:**
- `percentSlot` at top/centre; when centre, Wi-Fi moves top.
- Functional number; force single-view (`splitIndicators=false`) or explain disabled split.
- Add state; Canvas clone.

### P2.4 Clock

**Requirement:** RING-03  
**Files:** `DuoStateMonitor.kt:295`, time filters, `scene.rml` `clock` text, Canvas, strings/enum.  
**Task:**
- System 12/24 via `DateFormat`; update on time/timezone/locale/config.
- Dedicated text object; no wrap; fit width; never touch ring.
- Test 12:34 / 23:59 / 12:34 PM.

### P2.5 Eight SIM dots + battery line

**Requirement:** RING-04  
**Files:** `scene.rml`, `DuoMapping` SIM levels, `DuoCanvasView`.  
**Task:**
- Author 8 dots (4/side) with **SIM mapping UNVERIFIED** until measured/owner-confirmed; bottom battery line.
- Number above, Wi-Fi centre; match image.
- Canvas clone; headless screenshot compare.

### P2.6 Split battery transition

**Requirement:** RING-05  
**Files:** `scene.rml`, state machine, `DuoMapping`, Canvas.  
**Task:**
- Translate the owner's static image into a storyboard: two battery halves move to the old dot positions, keep curvature; 8 SIM dots maintain mapping.
- Owner reviews frames before code.
- Implement forward/reverse; no jump; Canvas clone.
- **Blocked until storyboard approval.**

### P2.7 Camera/Compact

**Requirement:** CUS-16, RING-06  
**Files:** `DuoPrefs` placement, `DuoIconHost`, `scene.rml`, `DuoShell` camera page/sheet.  
**Task:**
- Main bar only; QS/shade/keyguard use Default.
- Fixed outer view; inner translation; Resize sheet; no live resize.
- Use measured cutout only; otherwise `unverified`.

### P2.8 Canvas parity

**Requirement:** RING-08  
**Files:** `DuoCanvasView.kt`, geometry table.  
**Task:**
- Port every new object and transition; side-by-side diff.
- Fix current known mismatches (ring scale, DND/bolt seat, event scales, unlock/call art, event motion).

### P2.9 Assets and gates

**Requirement:** RING-09, RING-10  
**Task:** `rive verify`, ease check, hash compare, idle renderer pause, performance.

**P2 exit:** owner approves split storyboard; headless frames; Canvas parity; hash/ease; no performance regression.

---

## 8. P3 — Download progress and PHDP

**Requirements:** DL-01…09  
**Files:** `DuoStateMonitor.kt:156-185`, `DownloadProgress.kt`, `DuoMapping.kt:194-200`, `DuoIconHost` tap path, Customization UI, strings, PHDP link.

Tasks:
- Add the Download Progress section: toggle, latest release link, PHDP status, Simulate button, explanation.
- State machine: download active → download number/bar, charging hidden; tap → charging/battery; tap again → download; completion → previous state; 100% → normal.
- Only the ring owns the tap override; split view must not silently lose it.
- PHDP detection; if absent show GIF + link; Simulate opens the PHDP app for its own test; no injection.
- Use versioned release URL/API; no fake latest APK.

**Weapons:** PHDP source/APK on emulator, Duo mapping unit tests, real download capture, `T-DL-*`.

**Exit:** download at 50% charging, tap, complete; no stuck; P0 still passes.

---

## 9. P4 — Burn-in

**Requirement:** BURN-01…03  
**Files:** new `BurnIn` preference/controller, `DuoIconHost` translation, diagnostics.

Tasks:
- Detect whether OxygenOS already shifts; if yes disable/note.
- 1 px left hour, hold, return, repeat; no render loop/wake lock; stop screen off.
- Measure 8 h battery and visual movement.
- Main bar only; document other surfaces.

**Weapons:** `AlarmManager`/WorkManager, batterystats, `T-BURN-*`.

---

## 10. P5 — All-UI, regression, release

**Requirements:** QA-01…05

Tasks:
- CPH2747 full pass; every other corpus UI including AOSP with evidence or `unverified`; static checks for unmeasured ROMs.
- Full unit/Compose/Rive/Canvas/gates.
- Reports/privacy/logs; no PII.
- Update `REPAIR-CATALOG`, `AGENT`, phase report, release notes.
- Build release candidate; owner installs; keep beta.45 rollback.

**Weapons:** `REA`/JADX for vendor APKs, `adb`, Gradle, `review-strict`, owner install log.

---

## 11. Work waves and parallelization

| Wave | Tasks | Can run in parallel? |
|---|---|---|
| W0 | P0.1, P0.2, P0.6 | P0.1 and P0.2 touch different functions but same files; serial by file ownership. Only one agent edits `DuoIconHost` at a time. |
| W1 | P0.3, P0.4, P0.5 | P0.3/P0.4 share host; serial. P0.5 can parallel (state/mapping) after P0.2. |
| W2 | P1.1/P1.2, P1.4, P1.6 | UI files overlap; divide by file ownership. P1.2 assets can parallel with P1.4. |
| W3 | P1.3/P1.5/P1.7, P1.8 | Serial for `DuoShell`; GIF asset can parallel. |
| W4 | P2.1/P2.2/P2.3 | Shared `scene.rml`; one Rive owner. |
| W5 | P2.4/P2.5/P2.6 | Clock is separate; dots/battery share scene; serial. |
| W6 | P2.7/P2.8 | Camera host and Canvas separate after pose freeze. |
| W7 | P3, P4 | Separate files; parallel. |
| W8 | P5 | Serial final gate. |

Rules: no two agents edit `DuoIconHost.kt`/`scene.rml` concurrently; every branch builds before handoff; P0 must be merged/rebased before UI.

---

## 12. Requirement traceability

| Requirement | Phase | Primary files | Test |
|---|---|---|---|
| STB-01 | P0.1 | `DuoIconHost.kt` | T-STB-01 |
| STB-02 | P0.2 | `DuoRiveView.kt`, `DuoIconHost.kt`, `DuoGuard` | T-STB-02 |
| STB-03 | P0.3 | `DuoIconHost.kt`, `DuoHook.kt` | T-STB-03 |
| STB-04 | P0.4 | `DuoHook.kt`, `DuoRiveView.kt` | T-STB-04 |
| STB-05 | P0.6 | `Diag.kt`, `REPAIR-CATALOG` | T-STB-05 |
| STB-06 | P0.5 | `DuoStateMonitor.kt`, `DuoMapping.kt` | T-STB-06 |
| STB-07 | P0.2 | `DuoGuard`, Canvas | T-STB-07 |
| STB-08 | P0.1/P0.4 | `DuoIconHost` | T-STB-08 |
| NAV-01/08 | P1.1 | `DuoShell.kt`, `DuoHomeScreen.kt` | T-NAV-01/08 |
| NAV-02 | P1.2 | icons/drawables | T-NAV-02 |
| NAV-03/04 | P1.3 | `DuoShell.kt` | T-NAV-03/04 |
| NAV-05/06 | P1.3 | `DuoShell.kt` | T-NAV-05/06 |
| NAV-07 | P1.8 | drawable/assets | T-NAV-07 |
| CUS-01 | P1.7 | Customization page | T-CUS-01 |
| CUS-02/03 | P1.5 | `DuoPresets.kt`, `DuoPrefs.kt` | T-CUS-02/03 |
| CUS-04 | P1.6/P1.7 | engine/settings UI | T-CUS-04 |
| CUS-05 | P1.6 | `DuoSettingsSections.kt`, drawable | T-CUS-05 |
| CUS-06 | P1.6 | settings/clock | T-CUS-06 |
| CUS-07 | P1.6 | settings/`DuoPrefs` | T-CUS-07 |
| CUS-08/09 | P1.6 | events/prefs | T-CUS-08/09 |
| CUS-10 | P1.6 | simulation/event rows | T-CUS-10 |
| CUS-11 | P1.1/P1.6 | settings/report | T-CUS-11 |
| CUS-12/13 | P1.4 | `GlassControls.kt`, `DuoSettingsComponents.kt` | T-CUS-12/13 |
| CUS-14 | P1.6 | `DuoIconHost`, `DuoMapping` | T-CUS-14 |
| CUS-15 | P1.7 | `DuoRivePreview` | T-CUS-15 |
| CUS-16 | P0.3/P2.7 | host/scene | T-CUS-16 |
| CUS-17 | P1.6 | settings search | T-CUS-17 |
| RING-01/07 | P2.2 | `scene.rml`, mapping | T-RING-01/07 |
| RING-02/06 | P2.3 | percent/constraints | T-RING-02/06 |
| RING-03 | P2.4 | clock/monitor | T-RING-03 |
| RING-04 | P2.5 | scene/Canvas | T-RING-04 |
| RING-05 | P2.6 | scene | T-RING-05 |
| RING-08 | P2.8 | Canvas | T-RING-08 |
| RING-09/10 | P2.9 | scene/assets | T-RING-09/10 |
| DL-01…09 | P3 | monitor/mapping/UI | T-DL-01…09 |
| BURN-01…03 | P4 | new controller | T-BURN-01…03 |
| QA-01…05 | P5 | all | T-QA-01…05 |

---

## 13. Risks and rollback

| Risk | Mitigation |
|---|---|
| P0 fix makes view invisible/mis-sized | ID-only change; no geometry change; screenshots; Canvas fallback |
| Rive reuse breaks animations | Reuse controller, not static; headless state tests; fallback |
| Camera mode still unstable | Main bar only; no live resize; kill switch; stage 1 |
| Preset migration loses custom | Versioned schema; export before migration; tests |
| Icon licensing | Redraw original; no stock asset; record source |
| Split transition guessed | Storyboard approval before coding; no Rive work until then |
| PHDP test unavailable | Open PHDP; GIF fallback; no injection |
| Burn-in battery | Detect OS shift; low-frequency schedule; measure |
| Other ROMs regress | P0–P4 only CPH2747 claimed; other UIs `unverified` |

Rollback: keep beta.45 APK + settings export; revert to `stage=1` Canvas if Rive fails; P0 branch is independently revertible.

---

## 14. Owner gates

1. Approve this plan/requirements/test plan.
2. Approve split-battery storyboard before RING-05.
3. Approve icon redraws/licence.
4. Approve camera “Resize” sheet visuals.
5. Install each beta and confirm no safe mode before P5 release.

---

## 15. Immediate next action

After approval: start **P0.1 (stable ID)** and **P0.2 (Rive reuse)** on the stability branch only. Do not start UI/Rive art until P0 exit.

This plan added no code. The worktree already contains the pre-1.6 beta.45 build input; preserve it per §2.0 before P0.


## FILE: PLAN-v2.md

# Duo Status Bar — Implementation Plan v2 (Multi-UI Update)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Implementation Plan v2 |
| Version | 1.0 — draft for owner approval |
| Date | 2026-10-26 |
| Owner | Abdulkarim |
| Author | Kilo (orchestrator) |
| Status | **Draft. No code until Requirements + Test Plan + this plan are approved.** |
| Inputs | `agent/REQUIREMENTS.md` v1.0, `agent/TEST-PLAN.md` v1.0, `agent/REPAIR-CATALOG.md`, measured CPH2747 evidence |
| Change model | Additive. No new core architecture or hook framework. Follow the existing `DuoIconHost` / `ContainerFinder` / `RomAdapter` patterns. |

## 1. Objective

Ship a stable multi-UI update that:

1. puts Duo where the stock battery/status icons live, starting with the shade/QS header on CPH2747;
2. adds a full testing/logging/repair layer;
3. rebuilds the app into three pages with full Duo customization, two engines, backup/import, and live event icons;
4. fixes cell accuracy;
5. adds liquid glass and the Rive animation system;
6. runs with the same behaviour on 8 vendor UIs + AOSP, with measured evidence.

Success is **not** "it compiles". Success is: the touched feature passes its tests, the stock bar still works with the module off, and the measured evidence exists for the claimed UI.

## 2. Principles

- Evidence over guessing. No class/id/hook is invented.
- Reproduce before fixing. A bug needs a dump/log that shows it.
- One change at a time; diff the diagnostic dump.
- Fail silent: leave the stock bar working, log the reason, disable the surface.
- One property, one owner.
- Every Rive design has a Canvas clone.
- No new core architecture. Additive strategies inside the existing code paths.
- Every function that can fail logs enough to identify UI/surface/class/hook/reason.
- Performance and battery are acceptance criteria, not cleanup.
- No code before the three documents are approved.

## 3. Branch model

- One branch per phase: `phase/N-name`.
- UI sub-branches for parallel lanes: `phase/N/<ui-key>`; merge back into the phase branch only after the lane gate.
- Never commit to `main`. Merge only after the phase gate and owner approval.
- A phase branch starts from the previous phase branch (or `main` if the previous is merged).
- Keep the working tree clean per phase; do not mix unrelated changes.

## 4. Phase overview

| Phase | Branch | Goal | Parallel lanes | Exit gate | Weapons / tools |
|---|---|---|---|---|---|
| 0 | `phase/0-docs-evidence` | Docs, tools, evidence baseline | Docs / tools | G0-G2; docs complete | REA, JADX, Apktool, Rive CLI, git, Gradle |
| 1 | `phase/1-shade-qs` | Duo replaces the battery/icon cluster in shade/QS on CPH2747 | Probe / resolver / renderer | G3: dump+screenshot+log; stock restored | REA adb + JADX + Apktool, module `ShadeProbe`/`Diag`, `uiautomator`, `dumpsys`, LSPosed log, Gradle |
| 2 | `phase/2-testing-logs` | Tests for untested code; structured logs; report zip; repair catalog | Unit / Robolectric / device / logging | G4: coverage + report round-trip | Gradle test, Robolectric, instrumentation, REA adb/logcat, Python parser |
| 3 | `phase/3-app-customization` | 3-page app, engines, customization, backup/import, live events | UI / engine / backup / live | G4: app cases pass | Gradle/Compose, Rive CLI, JSON tests, Frida/Kahlo, REA adb |
| 4 | `phase/4-cell-accuracy` | 4-dot mapping matching system bars | Mapping / dual-SIM / 5G | G4: C-7/C-8/C-11 | REA adb, `dumpsys telephony.registry`, `dumpsys connectivity`, JADX |
| 5 | `phase/5-liquid-glass` | Liquid glass + final 3-page shell | UI / fallback | G4: API 31/33/older | Gradle/Compose, `gfxinfo`, screenshots |
| 6 | `phase/6-rive` | Animation system, Canvas parity | Rive / Canvas / perf | G4: headless + device | Rive CLI (`--verify/--screenshot/--bench/--data`), Canvas tests, adb |
| 7 | `phase/7-multi-ui-perf` | 8 UIs + AOSP, parallel lanes, performance/battery | Per-UI lanes | G5: per-UI evidence + budgets | REA adb per UI, JADX/Apktool, `gfxinfo`/`meminfo`/`batterystats` |
| 8 | `phase/8-release` | Release hardening/docs | Docs / release | G6: owner approval | Gradle, git, Rive hash, log checks, owner approval |

### Weapons per phase (execution map)

- **P0:** `rea_open_binary`, `rea_inspect_android_package`, `rea_decode_android_resources`; `jadx` CLI; `apktool`; `rive --verify/--screenshot`; `git`; Gradle.
- **P1:** `rea_list_adb_devices`, `rea_inspect_adb_window`, `rea_inspect_adb_package`, `rea_capture_adb_screen`; `rea_search_android_classes`, `rea_inspect_android_class`, `rea_inspect_android_method`; module `ShadeProbe`/`Diag`; `uiautomator dump`; `dumpsys window`; LSPosed log; `adb install`; Gradle.
- **P2:** `./gradlew :app:testDebugUnitTest`; Robolectric; instrumentation; `rea_read_adb_logcat`; Python log parser; `agent/REPAIR-CATALOG.md`.
- **P3:** Gradle/Compose; Rive CLI; JSON backup unit tests; Robolectric; Frida/Kahlo MCP for live events; `rea_capture_adb_screen`.
- **P4:** `rea_inspect_adb_*`; `dumpsys telephony.registry`; `dumpsys connectivity`; JADX `SignalStrength`; module logs.
- **P5:** Gradle/Compose; `dumpsys gfxinfo`; `rea_capture_adb_screen`.
- **P6:** `rive --verify`, `rive --screenshot`, `rive --bench`, `rive --data`; Canvas tests; `rea_capture_adb_screen`.
- **P7:** per-UI `rea_*` + JADX/Apktool; `dumpsys gfxinfo/meminfo/batterystats`; screenshot/dump matrix.
- **P8:** Gradle, git, Rive hash compare, log checks, owner approval.

## 5. Phase 0 — Docs, tools, evidence

**Branch:** `phase/0-docs-evidence`

Tasks:
- [x] Requirements v1.0.
- [x] Test Plan v1.0.
- [x] Repair catalog seed.
- [x] Weapons inventory and tool installs.
- [ ] `UI-MATRIX.md`.
- [ ] `AGENT.md`, `README.md`, `BRAINSTORM.md`, `MODEL-ROUTING.md`, `EVIDENCE.md`.
- [ ] Owner approval on all three core docs.

Exit: G0-G2; no code yet.

## 6. Phase 1 — Shade/QS battery host

**Branch:** `phase/1-shade-qs`

**Goal:** on CPH2747, Duo replaces the stock battery/status-icon cluster in the shade/QS header, with the same element and behaviour as the status bar. No force/remove; restore exactly.

**Status (2026-10-27):** safe build complete on `phase/1-shade-qs`; 221 unit tests pass; release builds; log check clean. The previous duplicate-fix build caused LSPosed safe mode (forced remove/reparent); that path is removed. **Device gate pending** — phone offline; test sheet in `agent/PHASE-1-TEST.md`; rollback in `build/duo-phone-rollback-1.4.3.apk`.

**Measured facts (CPH2747, 2026-10-26):**
- Real QS cluster: `quick_qs_status_icons` -> `icons` -> `statusIcons` (plain `LinearLayout`) + `batteryRemainingIcon` -> `battery_icon_view`.
- `batteryRemainingIcon` bounds `[1116,153][1202,254]`; `battery_icon_view` bounds `[1119,178][1201,229]`.
- The old path looked for `shade_header_system_icons` and `StatusIconContainer.addView`; neither matches this build.
- LSPosed log shows the module active in SystemUI (`DuoSB` lines, pid 10266); the shade header still shows the stock battery. No scope blocker.

**Tasks:**
1. [x] Confirm the module is active in SystemUI (`DuoSB` lines under `(com.android.systemui)`, pid 10266). No scope blocker.
2. [ ] Capture baseline: shade/QS screenshot, `uiautomator dump`, `dumpsys window`, LSPosed log. Save under `docs/evidence/v2/phase1/`.
3. [ ] Add a **debug-only read-only probe**: log `quick_qs_status_icons`, `batteryRemainingIcon`, `battery_icon_view`, their bounds, and the `ViewGroup.addView` route for the shade header. No behaviour change; no hiding.
4. [ ] Extend `ContainerFinder.findShadeIconsArea` additively:
   - prefer `quick_qs_status_icons`;
   - fall back to `shade_header_system_icons`;
   - fall back to the current `statusIcons` parent;
   - log every candidate and the chosen one.
5. [ ] Add a small `BatteryHostResolver` (additive helper, existing package/style) that returns the actual battery host and its draw point:
   - `batteryRemainingIcon` (container);
   - `battery_icon_view` (ImageView/drawable);
   - candidate classes/ids/tree path/bounds;
   - a structured trace on failure.
6. [ ] **Host rendering spike** (evidence before code):
   - Canvas: custom `Drawable` inside the battery ImageView; draw Duo, suppress the stock draw, restore.
   - Rive: determine whether a child view can live inside `batteryRemainingIcon` without a window overlay; if not, keep Canvas for this host and log the limitation.
   - Record the decision and the reason in `agent/DECISIONS.md`.
7. [ ] Implement the chosen host binding:
   - draw Duo in the host bounds;
   - hide only the stock draw inside that host;
   - restore exactly on disable;
   - no forced removal, no separate window overlay;
   - same element/geometry as the status bar.
8. [ ] Log: `ui=oneplus surface=shade|qs stage=resolve|hook|attach|draw|hide|restore class=<fqcn> id=<id> hook=<method> result=<ok|fail|skip> reason=<code> detail=<bounded> build=<fingerprint>`.
9. [ ] Tests:
   - `BatteryHostResolverTest` with the measured tree fixture;
   - `ContainerFinderTest` for the new fallback order;
   - Robolectric attach/hide/restore;
   - on-device TP-001..TP-010.
10. [ ] Evidence: before/after screenshot, dump diff, log; stock battery visible with the module off.
11. [ ] Do **not** touch QS tiles, AOD, or the Settings app in this phase.

**Files likely touched (confirm before editing):**
- `app/src/main/java/.../hook/DuoHook.kt`
- `app/src/main/java/.../hook/DuoIconHost.kt`
- `app/src/main/java/.../hook/ContainerFinder.kt`
- `app/src/main/java/.../hook/Diag.kt`
- `app/src/main/assets/rom-profiles.json` (OnePlus profile notes only)
- tests under `app/src/test/.../hook/`
- `agent/REPAIR-CATALOG.md`, `docs/evidence/v2/phase1/`

**Exit gate (G3):**
- Duo replaces the battery/icon cluster in shade/QS on CPH2747.
- Stock battery returns exactly when the module is disabled.
- No crash/ANR; dump diff empty except timestamps/pids.
- Screenshot + tree + log archived.
- If the host cannot be resolved: stock left untouched, trace logged, status `unverified`; no half-attach.

**Risks:**
- The battery host draws through a drawable, not `onDraw`; the spike must confirm the actual draw point.
- Rive may require a child view; no separate window overlay. If it cannot be done safely, Canvas is the Phase 1 renderer for this host, with a logged limitation.
- The ROM may repopulate the cluster; the hide must re-apply on the actual insertion route.
- The shade header attach/resolve path is still unknown; the module is active in SystemUI.

## 7. Phase 2 — Testing, logging, repair

**Branch:** `phase/2-testing-logs`

Tasks:
- [ ] Add `DuoLog` structured schema (`ui/surface/stage/class/id/hook/result/reason/detail/build`).
- [ ] Add a 2 MB ring log + session cap; redact PII.
- [ ] Add unit/path tests for untested code; 80% changed-code lines/branches.
- [ ] Add Robolectric/instrumentation for resolver/attach/hide.
- [ ] Add report zip (dump + logcat + screenshot + build) and share sheet.
- [ ] Extend `agent/REPAIR-CATALOG.md` with every new reason code.
- [ ] Add CI gates: tests, logs check, Rive check, authority grep, `.riv` hash.
- [ ] Run TP-070..TP-079.

Exit: coverage + report round-trip + no PII + catalog coverage.

## 8. Phase 3 — App, engines, customization, backup, live events

**Branch:** `phase/3-app-customization`

Tasks:
- [ ] 3-page shell: Home / Engine / Settings; bottom navigation.
- [ ] Engine selector: Rive / Canvas / Auto; Auto fallback reason logged.
- [ ] Middle slot: Wi-Fi, cell, battery %, clock, charging.
- [ ] Layouts: 4+4 dots; battery bar in the cell position; Wi-Fi in the charging slot.
- [ ] Icon packs: per-state selection; OEM packs local/prototype; fallback.
- [ ] Per-icon show/hide for stock icons.
- [ ] 3 presets + advanced + live preview box.
- [ ] Toggle previews + question-mark simulation.
- [ ] Backup/import: plain versioned JSON; all settings/preferences/customization; validate + confirm.
- [ ] Live events: alarm, screenshot, mic, location (P0); volume, unlock (P1); hooks first; consent for UsageStats/NotificationListener; fallback.
- [ ] Every Rive design gets a Canvas clone.
- [ ] English-only strings.
- [ ] Run TP-020..TP-045.

Exit: app cases pass on CPH2747; backup round-trip; no crash.

## 9. Phase 4 — Cell accuracy

**Branch:** `phase/4-cell-accuracy`

Tasks:
- [ ] 4-dot mapping to the system bar count.
- [ ] Dual-SIM: active subscription per slot.
- [ ] 5G NSA: radio NR state, not LTE data type.
- [ ] Per-UI override only if the measurement requires it (the owner asked for 4-dot mapping first).
- [ ] Log raw level/max/network type per update.
- [ ] Run TP-050..TP-054.

Exit: full bars on the phone show full dots on CPH2747; dual-SIM/5G cases pass.

**Status (2026-10-28):** `phase/4-cell-accuracy` created from the Phase 3 prototype merge
(`10f9ac7`). Raw cellular logging and the stock-level-to-4-dot mapper are implemented (318 tests);
device TP-050..TP-054 remain pending.

## 10. Phase 5 — Liquid glass + three-page shell

**Branch:** `phase/5-liquid-glass`

Tasks:
- [ ] Add `io.github.kyant0:backdrop:2.0.1` (Apache-2.0).
- [ ] Apply to the app surfaces only, never SystemUI.
- [ ] API 31+ blur; API 33+ shader; graceful fallback below 31.
- [ ] Measure jank/memory; cap blur radius.
- [ ] Finalize Home/Engine/Settings layout.
- [ ] Run TP-030..TP-032.

Exit: visual approval + no jank on CPH2747.

**Status (2026-10-30):** **Phase 5 prototype locked** at beta.14 (code 44). The slime selector,
glass toggles/sliders, scroll edges, Apple tokens and depth shadow are frozen; further changes belong to
Phase 6/7. Device visual sign-off is still pending because CPH2747 was offline.

## 11. Phase 6 — Rive animation system

**Branch:** `phase/6-rive`

Tasks:
- [ ] Author RML with Rive CLI 1.8.0; `--verify`, `--screenshot`, `--bench`.
- [ ] Charging pulse from 0 to level; pulse at level.
- [ ] 20% charge shake (~200 ms).
- [ ] Alarm bounce/shake; screenshot flash; volume pulse; location/mic state.
- [ ] Every design gets a Canvas clone.
- [ ] Pause renderer when idle/hidden; restart on change.
- [ ] Run TP-033, TP-042, TP-061, TP-066.

Exit: headless poses + on-device screenshots; no regression of existing animations.

**Status (2026-10-30):** branch `phase/6-rive`; charging settle pulse (Rive state `0:261` +
`ChargeSettle` animation), renderer-neutral 20 % shake (`ChargeMotion` + `DuoIconHost.shakeAll`),
headless idle/charge screenshots and `duo.riv` hash `636e8c9a…` recorded. Alarm/flashlight/screenshot
motions remain; mic/location/volume sources are still unmeasured. Emulator/device runs pending.

## 12. Phase 7 — Multi-UI parallel + performance

**Branch:** `phase/7-multi-ui-perf`

Parallel lanes (one per UI):
- `phase/7/aosp`
- `phase/7/oneplus`
- `phase/7/coloros`
- `phase/7/hyperos`
- `phase/7/oneui`
- `phase/7/originos`
- `phase/7/magicos`
- `phase/7/myos`
- `phase/7/nothingos`

Rules:
- **Acquisition gate:** all 8 vendor SystemUI APKs are now local in `corpus/` (hashes in `corpus/index.json`), and their static maps are in `agent/UI-MATRIX.md` (per-UI header map). Runtime dumps are still missing for 6 UIs, so those lanes stay `UNVERIFIED` until a device dump exists.
- Shared core changes land first (resolver/profile schema); lanes only add profiles/adapters/tests.
- A lane may not edit another lane's files.
- Each lane produces a measured dump + screenshot + log or `UNVERIFIED`.
- Merge lanes into the phase branch after the per-UI gate.

Tasks:
- [ ] **Profile schema v2 (shared core, first):** add optional `shadeContainerIds`, `shadeBatteryId`, `shadeStatusIconId` to `RomAdapter` (default empty); parse them in `RomProfiles`; keep the old status-bar path unchanged.
- [ ] **Per-UI static maps:** OnePlus (measured status + `quick_qs_status_icons`; `combined_qs_header` fallback), Samsung (`shade_header_system_icons` + `system_icons`), Xiaomi (`system_icons` measured + `shade_header_system_icons`), vivo (`shade_header_system_icons` + `system_icons`), Motorola/Nothing (`shade_header_system_icons` + `system_icons`), OPPO (`quick_qs_status_icons` + `system_icons`; old APK), Honor (`hw_system_icons`/`system_icon_area`; old APK). Mark all unmeasured maps `measured=false` / `UNVERIFIED`.
- [ ] `ContainerFinder.findShadeIconsArea` uses the profile-specific shade IDs first, then the common `quick_qs_status_icons` -> `shade_header_system_icons` -> `system_icons` fallbacks; log every candidate.
- [ ] Unit tests for every profile map and the fallback order.
- [ ] Per-UI battery/status host resolution.
- [ ] Per-UI cell mapping.
- [ ] Per-UI performance/battery numbers.
- [ ] Run TP-080..TP-083, TP-060..TP-067.

**Status (2026-10-27):** profile schema, 8 static maps, and candidate resolver probes are built and unit-tested; OnePlus is `shadeMeasured=true`. Runtime dumps for six UIs are still missing, so the per-UI lanes remain `UNVERIFIED`; no device claim yet.

**Status (2026-10-31):** branch `phase/7-multi-ui-perf`; 9 profiles in the shipped asset (2 measured, 7 static-only), `check-perf.py` added and header-corrected. Emulator: all-live glass ANR; static controls/surfaces remove the ANR but the API 36 emulator still measures p95 **216.88 ms** (budget 32 ms), so the Phase 7 performance gate **fails** on the emulator and is open on CPH2747. Screenshots and crash logs in `docs/evidence/phase7/`; report `agent/PHASE-7-REPORT.md`.

Exit: 8 UIs + AOSP with evidence; budgets pass; unknown UI left untouched.

## 13. Phase 8 — Release

**Branch:** `phase/8-release`

Tasks:
- [ ] README/release notes.
- [ ] Version bump.
- [ ] Full gates: unit, instrumentation, per-UI evidence, perf/battery, `.riv` hash, logs check.
- [ ] Owner approval.
- [ ] Beta pre-release only; no store until approved.
- [ ] Remove OEM assets at the owner's chosen time before official launch.

Exit: G6 owner approval.

**Status (2026-10-31):** branch `phase/8-release`; beta.20 (code 50) built and relayed, 322 unit tests
pass, `.riv`/APK hashes recorded, log and easing gates pass. Stable release is **blocked**: device
visual/perf run, frame budget and owner approval are open. See `agent/PHASE-8-REPORT.md` and
`agent/WALKTHROUGH-REVIEW-1.5.0.md`.

## 14. Function inventory (names are proposals; confirm before coding)

| Function / class | Purpose | Inputs / outputs | Logging | Tests | Phase |
|---|---|---|---|---|---|
| `ContainerFinder.findShadeIconsArea` (extend) | Resolve the shade/QS cluster | header view -> ViewGroup? | candidates + chosen | unit + Robolectric | 1 |
| `BatteryHostResolver.resolve` | Find the battery host/draw point | root + surface -> host? | candidates, tree path, bounds, reason | unit fixture | 1 |
| `BatteryHostBinding.attach` | Draw Duo in the host; hide stock draw | host + renderer -> binding | stage/result/reason | Robolectric + device | 1 |
| `BatteryHostBinding.restore` | Put the stock drawing back | binding -> void | result | device | 1 |
| `QsHeaderProbe.install` | Debug-only addView/tree probe | window -> void | class/id/parent/bounds | device | 1 |
| `DuoLog.event` | Structured log | fields -> void | schema | unit | 2 |
| `DuoReport.export` | zip dump+logcat+screenshot+build | context -> Uri | result | instrumentation | 2 |
| `EngineSelector.resolve` | Rive/Canvas/Auto | device + prefs -> engine + reason | reason | unit | 3 |
| `MiddleSlotModel` | Middle occupant | state + prefs -> slot | state changes | unit | 3 |
| `CellMapper.map` | 4-dot mapping | level/max/network -> 0..4 | raw/mapped | unit | 4 |
| `BackupCodec.export/import` | JSON backup | prefs -> JSON / JSON -> prefs | version/result | unit | 3 |
| `PresetStore.apply` | 3 presets | preset id -> prefs | result | unit | 3 |
| `LiveEventMonitor` | Event sources | hooks/consent -> event | event/reason | unit + device | 3 |
| `PerfProbe` | gfxinfo/meminfo/battery capture | adb/device -> report | metrics | device | 7 |

## 15. Parallelism rules

- Phase 1 is sequential: scope -> probe -> resolver -> binding -> tests. Do not parallelize code that edits the same hook files.
- Phase 3 can split into app-shell / engine / backup / live-events lanes, but shared `DuoPrefs`/provider changes land first.
- Phase 7 is the main parallel phase: one lane per UI, separate files; shared resolver changes land first.
- Any lane that must edit `DuoHook.kt`/`DuoIconHost.kt`/`ContainerFinder.kt` waits for the shared core to merge.
- Use separate worktrees for parallel agents; never two agents on the same files.

## 16. Human gates

| Phase | Owner action |
|---|---|
| 1 | Enable module for System UI; restart; keep the phone connected |
| 1 | Visual check of shade/QS before/after |
| 3 | Review the 3 pages/presets and backup file |
| 5 | Visual sign-off on liquid glass |
| 6 | Visual sign-off on animations |
| 7 | Provide one device/dump per UI, or accept `unverified` |
| 8 | Approve release; remove OEM assets before official launch |

## 17. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Shade header path not resolved | Stock battery remains | Probe the header; use the measured `quick_qs_status_icons` tree |
| Battery host uses a drawable, not `onDraw` | Wrong hook | Spike both draw points; log candidates |
| Rive cannot live inside the battery host without an overlay | Engine parity | Canvas for Phase 1; record limitation; revisit |
| ROM repopulates icons | Icons return | Probe the real insertion route; re-apply on that route |
| 6 UIs have no device | False "support" | `unverified` label; no claim |
| Native Rive crash | SystemUI death | Existing breaker/kill switch; guard |
| Liquid glass blur jank | Bad UX | Cap radius; fallback below API 31 |
| Disk 8.5 GB free | Build/download limits | Clean build artifacts; no large firmware |
| Windows logs not mounted | Missing user evidence | Owner mounts read-only NTFS |
| Backup schema drift | Broken import | Versioned JSON + validation + tests |

## 18. Immediate next action

1. Owner approves Requirements + Test Plan + Plan v2.
2. Capture the shade/QS baseline; the module is already active in SystemUI.
3. Start `phase/1-shade-qs`: probe, then the host binding.
4. No code before steps 1-2 are green.

*End of Implementation Plan v2.*


## FILE: RELEASE-NOTES-1.7.0.md

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


## FILE: REPAIR-CATALOG.md

# Duo Repair Catalog

Status: living document. Every new user log must add or update an entry.
Owner: Abdulkarim. Maintained by the agent. Language: English only (the CLI has problems with Arabic).

## What this is

A repair manual for Duo failures. Given a diagnostic dump, a logcat capture, or a user report, this
catalog tells the agent:

1. what the symptom most likely means,
2. which log lines prove or disprove it,
3. which tool to use to confirm it,
4. the smallest safe fix pattern,
5. how to verify the fix without breaking another surface.

It is not a list of guesses. Every entry has an evidence tier. An entry that has not been confirmed on a
device is marked **unverified** and may not be presented as a known cause.

## How to use it (order matters)

1. **Read the dump first.** Find the device block, the ROM adapter, the stage, the surfaces, the renderer,
   and the last failure line. Do not start from the user's wording.
2. **Match the symptom, not the words.** The user says "not working"; the dump says which stage failed.
3. **Confirm the exact log string in the code.** Log strings are part of the frozen surface
   (`docs/rom-contract.md`). Search the source for the tag and the message before treating a pattern as a
   signature. If the string is not in the code, it is not evidence.
4. **Pick the smallest tool that can confirm it.** `jadx`/REA for static facts, `dumpsys`/`uiautomator` for
   live trees, Kahlo/Frida for live hooks, the module's own `Diag` dump for attach/hide state.
5. **Change one thing, then prove it.** Re-run the same case, capture the same evidence, and diff the dump.
   A working ROM that changes its dump is a regression, not a fix.

## Evidence tiers

| Tier | Meaning | May be claimed? |
|---|---|---|
| `measured` | Captured on a named device/build with the current code | Yes |
| `log-confirmed` | Proven by a diagnostic dump or LSPosed log, but not re-run after a change | Yes, with the build named |
| `public-source` | AOSP/LineageOS source or a public module, not our device | Only as a hypothesis |
| `unverified` | No device evidence | No; this is a lead, not a cause |

## Inputs and where they come from

| Input | Source | Notes |
|---|---|---|
| Diagnostic dump | In-app report, `diagnose from users/`, Telegram | Primary evidence; contains device, adapter, probes, tree, readers |
| LSPosed module log | `/data/adb/lspd/log/modules_<boot>.log`, lines `DuoSB \|` | The sink that survives on OxygenOS |
| logcat | `adb logcat -s DuoSB` | May be empty from SystemUI on OxygenOS; do not use alone |
| View tree | `uiautomator dump`, module `Diag` tree | The module tree is authoritative for our attach path |
| Frame timing | `dumpsys gfxinfo com.android.systemui framestats` | For jank/perf entries |
| Memory | `dumpsys meminfo com.android.systemui` | For Rive/memory entries |
| Battery | `dumpsys batterystats` | For drain entries |
| Screenshot | `adb exec-out screencap -p` | For visual-only symptoms |

## Proposed log line contract (to confirm against the code)

Every new function that can fail should emit one structured line. Do not log per frame.

```text
DuoSB | ui=<key> surface=<status|keyguard|shade|qs|app> stage=<resolve|hook|attach|draw|hide|restore|state>
       class=<fqcn> id=<resource-id|none> hook=<method|none> result=<ok|fail|skip>
       reason=<short-code> detail=<bounded text> build=<fingerprint>
```

Rules:
- one line per state change or failure, never per frame;
- no SSIDs, IMEI, serial, accounts, location, or notification text;
- the reason code is stable and appears in this catalog;
- a missing log line is a bug: add the log before fixing the invisible failure.

## DuoLog reason codes (T1a)

Concise map of the stable constants in `io.github.kvmy666.duostatusbar.hook.DuoLog` to the surface/stage they
describe. Use these exact strings in `reason=`; do not invent new ones without adding them here.

| Constant | Surface / stage | Meaning |
|---|---|---|
| `RESOLVE_NO_CANDIDATE` | any / resolve | No candidate container or resource id resolved for the target surface. |
| `HOOK_ABSENT` | any / hook | Expected hook point or class is absent on this ROM. |
| `HOOK_INSTALL_FAILED` | any / hook | Reflection/install of a hook threw. |
| `ATTACH_ZERO_WIDTH` | any / attach | Injected element measured 0 px wide; injection not counted as success. |
| `PANEL_HOOK_ABSENT` | qs, shade / panel | Panel expansion hook unavailable on this UI. |
| `PANEL_ALIAS_UNVERIFIED` | qs, shade / panel | Per-UI panel alias not verified on a device. |
| `SURFACE_DISABLED` | qs, shade / state | Surface disabled by the user (`showInQs`/`showInShade`). |
| `RESTORE_FAILED` | any / restore | Restoring the stock drawing failed. |
| `PROFILE_ASSET_MISSING` | app / resolve | `rom-profiles.json` asset could not be read. |

## Report ZIP and PII filter (T2/T3)

- ZIP builder: `io.github.kvmy666.duostatusbar.ui.DuoReport.writeZip` / `LogReporter.writeReportZip`.
  Entries: `manifest.json`, `diagnostics.txt`, `module-log.txt`, `logcat.txt`, optional `screenshot.png`.
  Old reports/text dumps are pruned to the newest 10 each (`DuoReport.prune`).
- PII filter: `DuoLog.sanitize` redacts `ssid`, `imei`, `serial`, `token`, `password`, `secret`,
  `account`, `email` and email addresses, flattens newlines and caps `detail` at 240 chars.
- LSPosed tail filter: `RootLogs.rootScript` greps only `DuoSB`/`duostatusbar` lines and takes the
  last 400; logcat capture is `-s DuoSB`. Device facts are always appended.
- 2 MB caps: `L` keeps at most 600 lines / 2 MB (`MAX_RECENT_BYTES`); the module log handed to the
  report is that ring. Report zips are not size-capped beyond pruning.

## Triage order

1. Is the module loaded into the SystemUI process? (`stage`, process identity)
2. Is the stage gate on? (`duo_statusbar_stage` override vs app settings)
3. Did the ROM resolve a host? (`stripStrategy`, container class/id)
4. Did the battery/icon hook attach? (`surface`, `hook`, `attach`)
5. Did the renderer bind? (`Renderer=Rive|Canvas`, `onReady/onFailed`)
6. Did hiding apply, and did it restore? (`hide`, `restore`)
7. Is the visual wrong while the state is correct? (geometry, tint, mapping)
8. Only then inspect the vendor APK/static code.

## Entry template

```markdown
### RC-XXX - <short symptom>
- Evidence tier: measured | log-confirmed | public-source | unverified
- UI(s): <aosp|oneplus|coloros|hyperos|oneui|originos|magicos|myos|nothingos|all>
- Surface(s): <status|keyguard|shade|qs|app>
- Symptom: <what the user sees>
- Log signatures: `<exact or symbolic pattern>` (confirm in source)
- Likely cause: <one cause; alternatives listed separately>
- Confirm: <commands/tools and what output proves it>
- Fix pattern: <smallest safe change>
- Verify: <case IDs + dump diff + screenshot>
- Risk/rollback: <what can regress; how to restore>
```

## Entries

### RC-001 - Duo absent, stock bar looks normal
- Evidence tier: log-confirmed
- UI(s): all
- Surface(s): all
- Symptom: "Duo is not there"; stock battery/icons unchanged.
- Log signatures: `MainHook loaded into`, `out of scope`, `gated off`, `settings unreadable`, `strip resolved by`, `no container id resolved`.
- Likely cause: module not in the SystemUI process, scope missing, stage gate off, or the provider was unreachable and the module stayed off.
- Confirm: dump device block + `stage` + process identity + `strip` line; LSPosed log for the same boot.
- Fix pattern: fix scope/identity/gate first. Do not add a new hook to a module that never loaded.
- Verify: A-1/A-7, H-1/H-2/H-13; dump diff empty except timestamps/pids.
- Risk: treating "provider unreachable" as "user turned it off" (C11).

### RC-002 - Duo is present but the stock battery is also visible
- Evidence tier: measured
- UI(s): oneplus, hyperos, oneui
- Surface(s): status, keyguard, shade, qs
- Symptom: both drawings visible at once.
- Log signatures: `hide`, `stock`, `GONE`, `0x0`, `container`, `repopulate`.
- Likely cause: the hide pass targeted the wrong view/container, the ROM re-populated the container, or the element was injected into a sibling while the battery lives elsewhere.
- Confirm: module tree dump of the surface; compare the hidden node with the node that actually draws the battery; `uiautomator dump` for the live tree.
- Fix pattern: resolve the exact battery/icon host per surface; hide only the views Duo replaces; re-apply on the ROM's repopulation event; restore on disable.
- Verify: B-1..B-8, H-11; screenshot before/after; dump diff.
- Risk: hiding a container that also holds icons the user wants (M5/C7).

### RC-003 - Shade header: injected but icons return / header 0x0 / not in window
- Evidence tier: measured (prior attempt)
- UI(s): oneplus (OxygenOS 16)
- Surface(s): shade
- Symptom: Duo appears or not, stock shade icons remain; header may size to 0.
- Log signatures: `shade_header_system_icons`, `not in this window`, `combined_qs_header`, `ShadeHeaderController`, `ViewStub`, `addView`, `0x0`.
- Likely cause: the header is not in the shade window at attach time; the ROM repopulates `statusIcons` after the hide pass; `StatusIconContainer.addView` is not the insertion route; the container is 0x0.
- Confirm: capture the full shade tree **after** expansion, not at attach; hook `ViewGroup.addView` on candidate parents; measure the header after layout; record `mobile_combo` outside `#icons`.
- Fix pattern: diagnostic probe first (RC-003-PROBE). Do not repeat the old hook blindly.
- Verify: screenshot + full tree + log; one probe run on CPH2747.
- Risk: half-attached shade (icons hidden, element 0x0) — leave stock if unresolved.

#### RC-003-DUPLICATE - Two Duo drawings / Wi-Fi outside the ring (2026-10-27)

Measured after installing the release on CPH2747:

- QS tree: **2** `TextureView` nodes, both `[1137,140][1146,203]` (**9x63**), under `quick_qs_status_icons` -> `header`.
- Lock tree: **2** `TextureView` nodes, both `[1071,43][1185,128]` (**114x85**), under `system_icons` -> `system_icons_container` -> `status_icon_area`.
- LSPosed log: `Duo injected into shade header (ConstraintLayout, 114px)` repeated at 10:57:28, 10:57:29, 10:57:31, 10:57:31 (4 injections).
- Stock battery `batteryRemainingIcon`/`battery_icon_view` absent from both trees after the hide.
- Symptom: one Duo at the old battery place and one on the Wi-Fi; in QS the ring/Wi-Fi are squeezed into a 9px-wide cluster, so Wi-Fi sits outside the ring. On the lock screen the view is 114x85 and the Wi-Fi is inside; the owner also reports the ring closing/100/overlapping the cell dots sometimes (needs a charging capture).
- Likely cause: the Duo is attached to the **collapsed `quick_qs_status_icons`** (9px) instead of the parent `header` that also holds the battery; and the shade path reattaches/replaces the slot 4 times without removing the old `TextureView`.
- Fix direction: attach to the stable parent/wrapper that contains both the icon cluster and the battery (not the 9px cluster); use the main-bar side (114px); one shade owner per window; remove the prior Duo view before adding; do not reattach a healthy slot.

#### RC-003-SAFEMODE - LSPosed safe mode after the shade duplicate fix (2026-10-27)

- Owner-reported: LSPosed detected abnormal/forced behaviour in the shade and entered **safe mode**; the module was disabled.
- The installed build at that moment was the duplicate-fix release (`6,736,564` bytes), not the rollback.
- Code evidence: its `DuoIconHost.attachShadeCluster` performed `removeView`, `dropSlot`, and `ourViews().removeView` whenever the target changed (`quick_qs_status_icons` 9px ↔ parent `header`). That is a forced mutation/reparent loop on live SystemUI views.
- The phone went offline (`No route to host`) before the LSPosed/logcat log could be pulled. The exact crash/ANR line is **UNKNOWN**; do not claim a log-confirmed cause.
- Fix: every `removeView`, `dropSlot`, `ourViews`, and `replaceSlot` call was removed from `attachShadeCluster`. One-owner rule: if a live `"shade header"` slot exists, return true and do not touch it. A live slot is never removed/reparented. 221 unit tests pass; release/debug build.
- Artifacts:
  - rollback (known working before the duplicate fix): `build/duo-phone-rollback-1.4.3.apk`, 6,712,200 bytes, SHA-256 `d5795cc8a1c099e6d0fcef8ba4f9189cfa72ad81b8e6b8ca4a773c5419a47110`;
  - safe release (untested on device): `build/duo-shade-safe-release.apk`, 6,736,680 bytes, SHA-256 `3e977f9988755c81bd9a8f8cdb00203d3ebfd86debee8225951af6b6fa9dcb32`;
  - safe debug (probe on): `build/duo-shade-test-debug.apk`, 36,241,821 bytes, SHA-256 `b82e7ca958beeb16a50aac228875d3c3bd50194b4b3be69b940a074b6653aff1`.
- Rule: never force/remove/reparent a live Duo from a layout or hook callback. If a live shade slot exists, leave it and return; if it is detached, leave stock and log. Safe mode is worse than a cosmetic duplicate.
- Next: when the phone is reachable, exit LSPosed safe mode, install the rollback first, confirm the known state, then test the safe release with logs. Do not reinstall the old duplicate-fix APK.

#### RC-003-BUILD - Phase 1 build status (2026-10-27)

- Branch `phase/1-shade-qs`; no commit.
- Code: `DuoIconHost`, `OnePlusShadeHooks`, `DuoHook`, `ContainerFinder`, `AppContextResolver`, `ShadeProbe`.
- `:app:testDebugUnitTest` — all tests pass, 0 failures.
- `:app:assembleRelease` — pass.
- `tools/route-module-logs.py --check` — 0.
- APKs: release `app/build/outputs/apk/release/app-release.apk` (6,735,892 bytes); debug test `build/duo-shade-test-debug.apk` (36,239,933 bytes).
- 8-UI data layer: `shadeContainerIds`/`shadeBatteryId` parsed and probed by the resolver; OnePlus `shadeMeasured=true`, the other seven static/unverified; `shadeStatusIconIds` unused.
- **Device not run.** OnePlus vendor hooks are static-only; the emulator validates the generic/AOSP path only.

#### RC-003-MEASURED - CPH2747 QS header tree (2026-10-26)

Measured with `uiautomator dump` of the expanded shade; screenshot 1272x2772.

| Element | Class | Bounds |
|---|---|---|
| `qs_frame` | FrameLayout | [51,0][1221,2772] |
| `header_container` | LinearLayout | [51,140][1221,267] |
| `header` | LinearLayout | [70,153][1202,254] |
| `quick_qs_status_icons` | ViewGroup | [851,153][1202,254] |
| `icons` | LinearLayout | [851,153][1116,254] |
| `statusIcons` | LinearLayout | [860,153][1116,254] |
| `batteryRemainingIcon` | LinearLayout | [1116,153][1202,254] |
| `battery_icon_view` | ImageView | [1119,178][1201,229] |
| `status_bar_dot` x2 | View | [990,172][1053,235], [1053,172][1116,235] |
| `qs_clock_container` | ViewGroup | [70,153][851,254] |
| `qs_carrier_text` | TextView | [70,153][851,254] |
| `qs_footer_clock` | TextView (clickable) | [76,267][457,360] |

- The real cluster is `quick_qs_status_icons`; `shade_header_system_icons` is not the container on this build.
- `statusIcons` is a plain `LinearLayout`, not `StatusIconContainer`; the old `addView` hook had no target.
- The battery host is `batteryRemainingIcon` (LinearLayout) with child `battery_icon_view` (ImageView).
- Correction (2026-10-27): the module is active in SystemUI (`DuoSB` lines, pid 10266); the `out of scope` line was the app process. The shade header still shows the stock battery, so the missing piece is the header attach/resolve path, not scope.

#### RC-003-PROBE-RESULT - CPH2747, 2026-10-27

The probe ran on the installed beta (versionCode 24) after a SystemUI restart.

- `ShadeHeaderController` found; its constructor view is `split_shade_status_bar`, **GONE 0x0**.
- `ViewStub.inflate` fired for `split_shade_status_bar` (and unrelated stubs), **not** for the QS header.
- At attach time: `shade_header_system_icons` = none; `statusIcons` = `StatusIconContainer` **0x0**; `quick_qs_status_icons` = `ConstraintLayout` **0x0** (logged but not selected); chosen `icons` = `AlphaOptimizedLinearLayout` **0x0**.
- After the shade expands, the visible header is `quick_qs_status_icons` under `header_container` (`[816,153][1202,254]` in the current capture; earlier `[851,153][1202,254]`).
- The one-shot attach happens before the QS header is laid out; the stock battery remains. This is the root cause of the old failure.
- Follow-up (2026-10-27): there are **two** visible `quick_qs_status_icons` instances. The first (`351x56` at `[795,143][1146,199]`) is not the shade header; the real one (`386x101` at `[816,153][1202,254]`) is under `header_container`/`header`. Selection must rank by the `header_container`/`qs_frame` ancestor, not by "first visible".
- **Correct OnePlus hook points (REA static analysis, 2026-10-27):**
  - `com.oplus.systemui.separate.OplusQSSimpleHeader.onInit()` / `onFinishInflate()` sets `qsStatusIconsContainer = findViewById(R.id.quick_qs_status_icons)`. This is the visible simple QS header; hook `onInit` after the original.
  - `com.oplus.systemui.qs.impl.OplusSplitShadeHeaderControllerExImpl.init(ViewGroup)` sets `qsStatusIcon = findViewById(R.id.quick_qs_status_icons)`, `iconsStrip = findViewById(R.id.icons)`, `statusIconContainer = findViewById(R.id.statusIcons)`, `batteryIcon = findViewById(R.id.batteryRemainingIcon)`. This is the split shade header; hook `init` after the original.
  - `ShadeHeaderController` only yields `split_shade_status_bar`; hooking it alone misses the simple QS header. The old `hookShadeHeader` path is insufficient.

#### RC-003-PROBE - Shade/QS diagnostic probe (read-only)
1. Open the shade fully; capture the view tree with the module `Diag` and `uiautomator dump`.
2. Hook `ViewGroup.addView` on candidate parents and record class/id/parent/bounds for every added child.
3. Record the header/container bounds after layout, not at attach.
4. Record `mobile_combo` and any icon host outside `#icons`.
5. Capture screenshot + LSPosed log + `dumpsys window`.
Exit: one named insertion route and one named container, or `UNKNOWN` with the exact candidates tried.

### RC-004 - Keyguard bar missing or duplicated
- Evidence tier: log-confirmed
- UI(s): all
- Surface(s): keyguard
- Symptom: element absent on the lock screen, or two elements.
- Log signatures: `keyguard`, `extra bar`, `attachExtra`, `system_icons`, `TYPE_NOTIFICATION_SHADE`.
- Likely cause: the shade window was not captured, the keyguard bar attached before the main bar, or the extra bar reused the wrong slot.
- Confirm: keyguard surface in the dump; window capture order; slot release/re-resolve lines.
- Fix pattern: attach the keyguard bar only after its window exists; one slot per bar; release the old slot on reinflation.
- Verify: B-5, H-12; lock/unlock; screenshot.
- Risk: duplicate element or leftover stock icons.

### RC-005 - Renderer=Rive but the element does not animate / froze
- Evidence tier: measured
- UI(s): all
- Surface(s): all
- Symptom: first frame only; later state changes not drawn.
- Log signatures: `Renderer=Rive`, `onReady`, `onFailed`, `oneShot`, `loop`, `renderer paused (idle)`.
- Likely cause: the state machine stopped (`oneShot`), the view model bound after the first read, or the renderer was paused and never restarted.
- Confirm: Rive state/inputs in the dump; `onReady` timing; animation loopValue in the `.riv`.
- Fix pattern: loop idle animations; start the machine explicitly; restart the renderer on a real change; keep the pause-on-idle.
- Verify: D-1..D-8; headless `--data` poses; device screenshot.
- Risk: battery drain if the renderer is left running (C8).

### RC-006 - Rive fallback to Canvas (or black/crash)
- Evidence tier: measured
- UI(s): all
- Surface(s): all
- Symptom: `Renderer=Canvas` unexpectedly, or a black element, or SystemUI restarted.
- Log signatures: `Rive runtime ready`, `defaultRendererType`, `Rive refused`, `failed attempts`, `bind`, `SIGSEGV`, `breaker`.
- Likely cause: native init/relinker failure, null renderer type, native crash, or the breaker latched after deaths.
- Confirm: the stage + breaker counters + the last native line; do not blame the `.riv` before the runtime.
- Fix pattern: keep `RiveInit` absolute-path + pinned renderer; count deaths once per process; allow Rive for the rest of the process after a successful creation (M14); never call the library initializer.
- Verify: H-3..H-7; Canvas fallback renders every occupant (C9); no crash.
- Risk: uncatchable native fault; the kill switch is the rescue path.

### RC-007 - Cell dots do not match the phone bars
- Evidence tier: measured
- UI(s): oneplus, hyperos
- Surface(s): status
- Symptom: phone shows full, Duo shows 2-3; SIM1 selected.
- Log signatures: `cell`, `level`, `signal`, `subscription`, `5G`, `NSA`, `mapping`.
- Likely cause: wrong subscription/level source, bar-count mapping (4/5 vs 3/4), or NSA reported as LTE.
- Confirm: compare the phone's own `SignalStrength`/`WifiInfo` with the module reader; log raw level + max + network type.
- Fix pattern: read the active subscription's own level; map max to the design's dot count; use the radio's NR state for 5G NSA.
- Verify: C-7/C-8/C-11; dual-SIM case; screenshot.
- Risk: changing one operator's mapping breaks another.

### RC-008 - Wrong tint (black/white flip, half-invisible)
- Evidence tier: measured
- UI(s): oneplus, hyperos
- Surface(s): status, keyguard, shade, qs
- Symptom: ring black but icons white; element invisible in light mode.
- Log signatures: `BarTint`, `onDarkChanged`, `darkIntensity`, `setIconColor`, `fgColor`, `tint`.
- Likely cause: an int intensity read as ARGB; only the ring bound to colour; the fallback uses a different colour source.
- Confirm: log the raw tint (alpha byte + value) and the resolved colour; screenshot light/dark.
- Fix pattern: accept only an int with non-zero alpha; bind every foreground shape to `fgColor`; keep the bolt on `tint`.
- Verify: M17/FR-15b; light/dark; screenshot.
- Risk: flipping several times per second.

### RC-009 - Settings changes do nothing / provider unreachable
- Evidence tier: log-confirmed
- UI(s): all
- Surface(s): app, all
- Symptom: toggle changes in the app, bar unchanged; or module stays off.
- Log signatures: `settings rev`, `unreadable`, `provider`, `authority`, `revision`, `using defaults`.
- Likely cause: the module can't read the provider, the revision did not change, or the size change needs a restart.
- Confirm: provider authority + revision + read result; app-side write result; restart state.
- Fix pattern: retry on every change; distinguish unreachable from off; size changes require a System UI restart (C2).
- Verify: E-2/E-8, H-8..H-10, H-13.
- Risk: treating unreachable as off (C11).

### RC-010 - Battery host not resolved (new battery-hook architecture)
- Evidence tier: unverified (architecture not implemented)
- UI(s): all
- Surface(s): status, keyguard, shade, qs
- Symptom: Duo does not replace the battery; stock battery remains.
- Log signatures: proposed `surface=battery stage=resolve result=fail reason=<code>`; candidate classes/ids/tree path.
- Likely cause: the battery view class/id differs per UI; the draw method is not `onDraw` on the view; the view is a `ViewGroup` with a child drawable; the header is 0x0.
- Confirm: per-UI resolver trace (candidate ids/classes, tree path, bounds, hook point); static confirmation in the vendor APK.
- Fix pattern: per-UI `BatteryHostAdapter` with a resolution order (profile id/class -> role scan -> tree), a draw interception point, and a restore path; no removal/overlay.
- Verify: new surface cases for status/keyguard/shade/qs on CPH2747 first; emulator later.
- Risk: hooking the wrong draw method gives a half-attached surface; leave stock and log.

### RC-011 - QS panel/tile not handled
- Evidence tier: unverified
- UI(s): all
- Surface(s): qs
- Symptom: QS panel still shows the stock battery/percentage.
- Log signatures: none yet; add `surface=qs`.
- Likely cause: no injection path exists for the QS panel; only the header is handled.
- Confirm: capture the QS panel tree after expansion; identify the battery host; decide whether it is the same view as the header.
- Fix pattern: add the QS host only after the probe names it; do not assume it is the header.
- Verify: QS cases; screenshot; dump diff.
- Risk: touching the panel can break QS layout.

### RC-012 - OEM icon pack does not render / engine rejects it
- Evidence tier: unverified
- UI(s): all
- Surface(s): app preview, all
- Symptom: selected OEM icon is blank, wrong size, or crashes the preview.
- Log signatures: proposed `iconpack=<id> asset=<name> result=fail reason=<code>`.
- Likely cause: vector/size/density mismatch, unsupported drawable type, or a missing fallback.
- Confirm: decode the asset with `aapt2`/`jadx`; render it in isolation; log dimensions/type.
- Fix pattern: normalize to the design bounds, provide a vector fallback, never let a pack crash the preview.
- Verify: per-icon preview; engine Rive + Canvas; release build excludes OEM assets.
- Risk: licensing; OEM assets are prototype-only and removed at official launch.

### RC-013 - QS/shade/header surface ownership and the return transition (2026-10-27)
- Evidence tier: **measured** for surface ownership and both hook installations on CPH2747 (fix8);
  **decompiled/static** for the transition constants from
  `corpus/SystemUI-device.apk` via jadx 1.5.6 (`CollapsedStatusBarFragmentExImpl.java:472-473,611`
  and `com/android/app/animation/Interpolators.java:96`); **owner-confirmed** for the final visual
  test after the Telegram/catbox delivery (no post-approval diagnostic dump was attached).
- UI(s): qs, shade, status
- Surface(s): `qs header`, `shade header`, main status bar
- Symptom:
  1. entering/exiting QS showed two Duo drawings (header + QS copy);
  2. the full-shade header could stay blank (`alpha=0`) after it attached;
  3. returning from QS/shade to the header showed a hard pop/dropped frame.
- Wrong model (do not repeat): deriving the visible surface from **which container attached last**.
  On this ROM the QS slot lives in `qs_container_area_layout`, while the shade slot lives in
  `header_container`; both can be created in a different order from the panel state, and a live slot
  in an overlay parent is not found by `liveSlotIn(stockTarget)`. The result was a stale owner,
  both copies visible, and the shade copy left at alpha 0 when no later layout arrived.
- The SystemUI state that actually owns this (measured with jadx 1.5.6 on
  `corpus/SystemUI-device.apk`, sha256 `dbe67fd3…`):
  - `NotificationShadeWindowControllerImpl.onShadeOrQsExpanded(java.lang.Boolean)`:
    edge-triggered `true` at the start of QS or shade expansion, `false` when both are closed.
  - `ShadeExpansionStateManager.onPanelExpansionChanged(float, boolean, boolean, String, PanelType)`:
    `QS_PANEL` and `NOTIFICATION_PANEL` are separate; `PanelType.SHADE_PANEL` is the aggregate.
    Decompiled `PanelType.java:13-15` confirms the names `NOTIFICATION_PANEL`, `QS_PANEL`,
    `SHADE_PANEL` (enum ordinals 0, 1, 2), so `toString()` is stable enough for the after-hook.
  - The notification surface wins when both `notificationExpanded` and `qsExpanded` are true; on
    this split build QS can remain expanded while the full notification shade is visible.
  - Live `dumpsys activity service com.android.systemui/.SystemUIService` proof: QS closed has
    `PhoneStatusBarView alpha=1.0`; QS open has `PhoneStatusBarView alpha=0.0`. The QS root is
    `OplusQSRootView`/`qs_container_area_layout`; the shade header is
    `OplusQSSimpleHeader`/`header_container`.
- Fix pattern (safe, after-only hooks):
  - Hook `onShadeOrQsExpanded` after the method; read the Boolean only. `true` → hide the **main**
    status-bar Duo (`alpha=0` on `element` and main `indicators`), `false` → reveal it.
  - Hook `onPanelExpansionChanged` after the method; maintain `QS_PANEL` and
    `NOTIFICATION_PANEL` expanded flags. `QS_PANEL` expanded → `qs header`; notification expanded →
    `shade header`; neither → hide both extras. This owner is authoritative over attach order.
  - Do not modify the vendor method arguments or return value; never remove/reparent a live Duo.
  - The first frame is handled by the same edge callback, so the main header disappears at the
    start of the QS transition rather than after the slot attach.
  - If the per-panel hook is unavailable, do **not** call `setPanelOwner(null)` from the Boolean
    hook: that would lock ownership to null and hide both extras. Keep the attach-time fallback.
    The implementation uses `panelHookInstalled` for this gate.
  - Run both after-hook bodies on the main looper; vendor callbacks are not guaranteed to be
    main-thread (the existing `OnePlusShadeHooks` already posts for the same reason).
  - One size owner for extras: `SlotGeometry.extraSidePx` clamps QS/shade/keyguard to the main bar's
    final `mainSidePx`. The QS/shade overlay can be full-window height, so the old bar-height cap
    alone produced 175 px extras against a 141 px header; the extra is now exactly the main size. A
    1 px header shortfall must not select a taller container: require a 10% shortfall before moving
    out of the stock header, otherwise the shade anchor moves into the 688 px QS container.
  - Per-surface user gates: `showInQs` and `showInShade` default true. Disabling a surface hides
    only our element and calls the slot's `stock.restore()` so the ROM header is not left blank;
    enabling calls `hideSlotStock` again. No live view is removed or reparented, so the off/on path
    is the same safe path as the alpha visibility change. The columns are optional in the provider
    contract so an older settings app cannot disable a surface by omission.
- Return motion (why the pop happened): the ROM's main status bar reveal is
  `CollapsedStatusBarFragmentExImpl.access$updatePhoneStatusBarViewShownState`:
  `ObjectAnimator`/`ValueAnimator` on `View.ALPHA`, **320 ms**, `Interpolators.ALPHA_IN` =
  `PathInterpolator(0.4, 0.0, 1.0, 1.0)` (vendor `Interpolators.java:96`), **50 ms** start delay.
  The module previously set the main Duo to alpha 1 in one frame, so it popped while the ROM's bar
  was still settling. Use the same 320 ms / 50 ms / `ALPHA_IN` on the main ring and its split
  indicator when restoring. Hide immediately on expansion. The vendor definition differs from AOSP's
  `ALPHA_IN`; use the decompiled vendor value, do not substitute the AOSP curve.
- Evidence commands:
  - `jadx -d /tmp/duo-re/src /path/SystemUI.apk`
  - `adb shell dumpsys activity service com.android.systemui/.SystemUIService`
  - `adb shell su -c 'cat /data/adb/lspd/log/modules_<boot>.log' | grep -E 'shade/QS|panel owner|shade surface|Duo injected'`
- Log signatures for this case:
  - `shade/QS coverage hook installed`
  - `shade panel expansion hook installed`
  - `shade/QS covered -> true (main alpha=0)` / `false (main alpha=1)`
  - `panel owner -> qs header|shade header|collapsed`
  - `shade surface owner -> ...` (attach-time only; not authoritative)
- Verify: QS open shows one QS Duo and no main Duo; full shade shows the shade Duo; closing either
  fades the main Duo in over the measured 320 ms and never leaves both visible; screenshot before/
  after and the LSPosed owner lines agree. `228` unit tests + release build pass (final APK SHA-256
  `d3ab2d2f79ca0aab778c24351898551f25223e7358d4ce3c51946ce98d8224b3`). The fix8 device run had no
  safe mode and no `FATAL EXCEPTION`; the owner approved the final build after installing it from
  the Telegram/catbox delivery.
- Risk: the hooks are on core SystemUI state callbacks. Keep them after-only and guarded; if either
  class/method is absent, fall back to the old attach behavior and log
  `shade/QS coverage hook unavailable` rather than guessing.

### RC-014 - HyperOS 17 panel expansion is cover/expansion only (2026-10-27)

- Evidence tier: static (APK decompiled with jadx; no runtime dump attached)
- UI(s): hyperos
- Surface(s): status, shade, qs
- Symptom: none reported; static classification of the HyperOS 17 panel expansion callback.
- Log signatures: none (static finding; no runtime signature)
- Likely cause: the HyperOS 17 SystemUI APK (sha 9869f129…) exposes only
  `onPanelExpansionChanged(float, boolean, boolean)` with the exact JADX signature `(FZZ)V`; its body
  sets `fraction`/`expanded`/`tracking` and a single shade state, with no `PanelType` argument. It
  therefore cannot distinguish the QS panel from the full notification shade.
- Confirm: jadx on the HyperOS 17 SystemUI APK (sha 9869f129…); decompile the panel controller and
  verify the method signature and body set `fraction`/`expanded`/`tracking` with a single shade state
  and no panel type.
- Fix pattern: keep `panelExpansionKind = "fzz_3arg"` and `panelOwnerAvailable = false` for HyperOS;
  treat the callback as a cover/expansion signal only. Phase 2 alias targets
  `onPanelExpansionChanged(FZZ)` with a per-UI classifier. No runtime support claim.
- Verify: static audit only; a runtime dump is still required before any support claim.
- Risk: treating a cover/expansion signal as a QS-vs-shade owner would hide the wrong surface.

### RC-015 - Flashlight icon appears while the torch is off (2026-10-28)

- Evidence tier: static (JADX/REA) + owner report; unit-tested
- UI(s): oneplus (ColorOS/OxygenOS SystemUI)
- Surface(s): status, keyguard, shade
- Symptom: the flashlight event icon appears even though the torch is off.
- Log signatures: none reliable; the false event is not logged as a torch transition. The hook target is
  `FlashlightControllerImpl.dispatchListeners(int what, boolean value)`.
- Likely cause: the callback multiplexes several events. Only `what == 1` means `onFlashlightChanged`;
  `2` is availability, `6` is night-light, and `0` is an error. The first implementation passed every
  callback's boolean to the event arbiter, so an availability update (`2, true`) looked like torch on.
- Confirm: `rea_inspect_android_method` on `corpus/SystemUI-device.apk`, class
  `com.android.systemui.statusbar.policy.FlashlightControllerImpl`, method `dispatchListeners`;
  the method switches on `what` and calls `onFlashlightChanged(z)` only for 1.
- Fix pattern: `flashlightStateFromDispatch(what, value)` returns the value only for `what == 1`, and
  `null` for every other type; the hook returns without touching the arbiter on `null`.
- Verify: `EventArbiterTest.only dispatch type one is the flashlight state`; on-device test:
  open/close flashlight and switch availability without changing the torch; no event icon on off.
- Risk: a future ROM changes the integer contract; keep the type filter guarded and log the observed
  `what` only at debug level if it changes.

### RC-016 - Screenshot event source (2026-10-28)

- Evidence tier: static (REA/JADX); no device run yet
- UI(s): oneplus (ColorOS/OxygenOS SystemUI)
- Surface(s): status, keyguard, shade
- Symptom: none (feature source); the screenshot event needs one shared entry that covers hardware key,
  QS tile and assistant requests.
- Log signatures: `live event -> SCREENSHOT` after the hook; no screenshot-specific vendor callback was
  found in the measured SystemUI.
- Likely cause of the gap: screenshot requests are dispatched through
  `com.android.systemui.screenshot.TakeScreenshotExecutorImpl.executeScreenshotsAsync(
  ScreenshotRequest, Consumer<Uri>, TakeScreenshotService.RequestCallback)`, which launches the
  per-display coroutine. Hooking a single vendor tile would miss the hardware key and assistant.
- Confirm: `rea_inspect_android_class` / `rea_inspect_android_method` on `corpus/SystemUI-device.apk`
  (`TakeScreenshotExecutorImpl`), then a log line when the user takes a screenshot on CPH2747.
- Fix pattern: hook `executeScreenshotsAsync` before the method body and offer `SCREENSHOT` (1000 ms
  transient); one Rive `EventScreenshot` state (60 frames) and a Canvas outline-camera clone.
- Verify: take a screenshot via hardware key and QS; expect one event, no duplicate stacked icons;
  screenshot event preempts an older event and returns to the current middle state.
- Risk: multi-display requests may call the executor more than once; the arbiter's same-type replace
  collapses them, but verify on a multi-display setup before claiming support.

### RC-017 - Settings app SIGSEGV in RenderThread (2026-10-29)

- Evidence tier: measured (device crash tombstone) + controlled replay after fix
- UI(s): oneplus (ColorOS/OxygenOS)
- Surface(s): app Settings/Home (not SystemUI)
- Symptom: opening the app immediately killed it.
- Log signatures:
  `F libc: Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR) ... in tid ... (RenderThread)`;
  tombstone `Cause: stack pointer is not in a rw map; likely due to stack overflow`.
- Cause: `GlassBackdrop` recorded page content into a `LayerBackdrop`, and `GlassCard` / Home and
  Engine `GlassSurface` inside that same layer also called `drawBackdrop` on it. That is a recursive
  GraphicsLayer capture; RenderThread overflowed before any Compose exception could be reported.
- Fix: only the floating navigation bar receives the recorded backdrop. Page cards are tinted
  Material surfaces; `LocalGlassBackdrop` is `null` inside the captured content so `GlassCard` falls
  back to plain `Card`.
- Verify: install 1.5.0-beta.4, start `MainActivity`, `pidof` stays alive, crash buffer empty; the
  activity reaches `mCurrentFocus`.
- Risk: any future glass component added inside the recorded page layer can reintroduce the recursion.

## New entry checklist

- [ ] Symptom is observable, not a user interpretation.
- [ ] At least one exact log signature was confirmed in the source.
- [ ] The likely cause is one sentence; alternatives are separate.
- [ ] A tool and a command can confirm it.
- [ ] The fix is the smallest safe change.
- [ ] Verification names the test IDs and the dump diff.
- [ ] Evidence tier is set; `unverified` is allowed.
- [ ] No PII in the entry.

## Tool index

| Need | Tool | Command |
|---|---|---|
| Decompile an APK | jadx / REA | `jadx -d out app.apk`; `rea decode_android_resources` |
| Decode resources/manifest | apktool | `apktool d -f -o out app.apk` |
| Live view tree | adb | `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` |
| Window focus | adb | `adb shell dumpsys window windows` |
| Frame timing | adb | `adb shell dumpsys gfxinfo com.android.systemui framestats` |
| Memory | adb | `adb shell dumpsys meminfo com.android.systemui` |
| Battery | adb | `adb shell dumpsys batterystats` |
| LSPosed log | adb/root | `/data/adb/lspd/log/modules_<boot>.log` |
| Live hooks | Kahlo / Frida / REA | `frida -U -l script.js com.android.systemui` |
| Static target discovery | DexKit | runtime query in the module (guarded) |
| Screenshot | adb | `adb exec-out screencap -p > shot.png` |

## Maintenance

- Add an entry for every distinct failure that reached a user.
- When a fix lands, change the tier to `measured` and add the verification evidence path.
- When a log string changes, update the frozen-surface note and every entry that quotes it.
- Never delete an entry; mark it `superseded` and link the replacement.


## FILE: REQUIREMENTS-1.6.md

# Duo Status Bar — Requirements 1.6

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Version | 1.6 requirements (additive to `agent/REQUIREMENTS.md` v2) |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Draft for owner review. No implementation until this file, `TEST-PLAN-1.6.md`, `PLAN-1.6.md`, and the brainstorm are consistent and approved.** |
| Evidence | Owner answers 2026-11-02; `reverse/kilo-images/`; `reverse/fatal-logs/`; current source; `agent/LIST-THE-PLAN.md`; `agent/BRAINSTORM-2026-11-02.md` |
| Precedence | These requirements are additive; where they conflict with v2, the more specific 1.6 requirement wins. Safety/non-negotiables in `AGENTS.md` still win. |

Legend: **P0** = release blocker; **P1** = required for 1.6; **P2** = polish. Every row has a test ID in `agent/TEST-PLAN-1.6.md` and a phase in `agent/PLAN-1.6.md`.

---

## 1. Non-negotiables

1. One unguarded view/API call must never be able to take SystemUI down; fail silent and keep the stock bar.
2. No fix without a reproduced signature or a deterministic test. No guessing.
3. No `.riv` import, Rive view creation, or reparent on a layout pass. One view per surface/lifetime; reuse it.
4. No injected child without a unique, stable ID when the host is a `ConstraintLayout`.
5. All surfaces must be tested or explicitly marked `unverified`; a CPH2747 pass cannot claim One UI/HyperOS/ColorOS/Vector/AOSP.
6. Every Rive object has a Canvas clone driven by the same geometry table.
7. Defaults preserve current pixels except where the owner explicitly approved a redesign.
8. No stock asset is packaged without a licence; reference images are redrawn as original vectors/Rive shapes.
9. Flows must store secrets only in the relay/app, never in the APK or report card.
10. The 1.6 release is blocked until P0 and all P1 acceptance tests pass.

**Owner decision recorded (Q5, 2026-11-02):** the **Charging %** middle option is removed; the separate **Battery %** middle option remains and is the two-seat percentage object in RING-02/RING-06/RING-07. If this inference is wrong, only the Battery % centre seat is dropped; the Charging % removal stands.

---

## 2. Stability and crash requirements (P0)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| STB-01 | Every view injected into a `ConstraintLayout` must have a unique non-NO_ID before `addView`, or the host must be a container that does not run `ConstraintSet.clone`. | QS open + rotate portrait→landscape 20×; no `All children of ConstraintLayout must have ids` fatal; SystemUI PID unchanged; log proves the injected child's ID. | T-STB-01 | P0 |
| STB-02 | A Rive view and its `.riv` import must be created once per **surface lifetime** (a bar + configuration pair) and reused. Never remove/re-add or reparent the same cached view on layout, shade, size, split, or preset changes; attach count is 1 per surface, not merely 1 import. A rotation may create one new surface after destroying the old one; that is not a per-layout reattach. | 60 s shade open/close + settings changes: attach count stays 1 per surface; `File::import` does not grow per layout; `Duo view ready` does not storm; 51,200 global-ref limit never approached. | T-STB-02 | P0 |
| STB-03 | Camera/lock-ring mode must not change view identity, reparent, or re-import on every layout. It must use one fixed surface with inner art translation. | Enable lock/camera mode; hold shade open 60 s; attach count stable; no JNI overflow; no SystemUI restart. | T-STB-03 | P0 |
| STB-04 | Rotation must be graceful: cancel/defer pending attaches, do not resize/reparent a live Rive view; after the new configuration is known, destroy the old surface and create at most one new surface. | 20 rotations QS closed/open; no fatal; no ANR; ring returns; covers portrait and landscape. | T-STB-04 | P0 |
| STB-05 | Both identified signatures (ConstraintSet.clone; JNI global ref table overflow) must be added to the repair catalog and the diagnostic report. Counters/flags must be persisted before the risky attach/import so the next boot can report which path preceded a SystemUI death. | Pre-crash markers survive a SystemUI restart; report contains `qs_constraint`, `rive_import`, `jni_refs`; forced-path test proves detection. | T-STB-05 | P0 |
| STB-06 | At 100% battery while plugged, charging visuals must stop and the ring must behave like a non-charging phone. Bolt/charging animation must not loop at 100%. | Force 100% + plugged; bolt hidden, no charging animation, number normal; unplug/replug still correct. | T-STB-06 | P0 |
| STB-07 | A Rive failure must fall back to Canvas, never crash or leave an empty bar; the breaker/kill switch stays. | Force bad `.riv`/no renderer; bar draws Canvas; log has reason; no SystemUI restart. | T-STB-07 | P0 |
| STB-08 | The crash fixes must not hide the bar on any surface. QS, shade, keyguard, and main bar remain visible or stock, never blank. | Four surfaces after fix; screenshots + dumps. | T-STB-08 | P0 |

---

## 3. Navigation, pill, and selector (P1)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| NAV-01 | Bottom navigation has exactly three tabs: **Home / Customization / Report**. Settings is a gear action on Home, not a fourth tab. | Three tabs at every width; gear opens Settings; no overflow/missing tab. | T-NAV-01 | P1 |
| NAV-02 | Use original vector/Rive redraws inspired by all six references: filled house (Home), sliders (Customization), documents + trend arrow (Report). Do not package the supplied JPG/PNG stock images or trace their path data; the report icon is Icons8 and every reference needs a licence review. | Icons match silhouette/proportions; no stock raster or copied path data in APK; licence note covers all six sources. | T-NAV-02 | P1 |
| NAV-03 | The selector is transparent (no wine fill); the active icon/label changes to the active accent (reference blue `#3C88F8`), inactive icons stay neutral. | Screenshot compare; active tint only under selector; selector has no wine wash. | T-NAV-03 | P1 |
| NAV-04 | Selector geometry: at rest 225×179 px in a 207 px pill (≈71.1×56.6 dp at 506 dpi); held 296×231 px (≈93.6×73.0 dp), ≈1.30× scale, overflows the pill; active icon grows ≈1.25–1.41×; inactive items do not grow. Use dp, not pixels. The held frame is mid-motion; the settled held size must be confirmed before it is frozen. | Measured screenshot at 1272×2772; ratios within ±5%; no clipping of labels. | T-NAV-04 | P1 |
| NAV-05 | Press-and-hold anywhere on the pill starts selector drag. The selector jumps to the finger and follows it; release snaps to the nearest tab. A normal tap still selects the tab. | Drag from each tab; selector jumps; page changes only on release; no accidental scroll; TalkBack still exposes tabs. | T-NAV-05 | P1 |
| NAV-06 | Page navigation, selector animation, and haptic/press feedback remain coherent during drag; dragging on Report/Home does not open a page until release. | Repeated drags, reversal, cancel; no page flash; selector returns. | T-NAV-06 | P1 |
| NAV-07 | Replace the copied Duo on/off GIF with an original: Duo icon appears, then disappears, on a black background, matching the existing GIF style/loop. | Asset is original; black background; correct loop; no Shutterstock frame; documented source/hash. | T-NAV-07 | P1 |
| NAV-08 | Home shows health/status, a non-interactive preview and Settings gear; it does not contain editing controls. Report is a full page; Customization is a full page with the live editing preview. | Page inventory matches; one shared preview state, two independent surfaces; no editing on Home; gear reachable. | T-NAV-01/08 | P1 |

---

## 4. Customization and settings (P1)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| CUS-01 | Customization contains all ring presets, toggles, sliders, shape choices, engine choice, and a persistent live preview. Tapping the Customization ring opens/focuses its controls. | Every old customization control has a new home; no dead end; preview stays visible while scrolling. | T-CUS-01 | P1 |
| CUS-02 | Presets are persistent snapshots: **Default** (Wi-Fi centre + auto 5G/4G + battery % above), **Compact** (camera ring + Resize sheet), **Custom** (last custom settings). Switching to Custom restores its last state. Editing while in Default/Compact switches to Custom and saves there. | Kill/relaunch app; selection and all fields survive. Default→Custom→Default cycle restores exact values. | T-CUS-02 | P1 |
| CUS-03 | Migrate old presets without losing pixels: `classic` → Default; old `compact`/`bold` → Custom (not the new camera Compact). Store a schema version. | Import old backup/prefs; old compact/bold render exactly as before; no camera surprise. | T-CUS-03 | P1 |
| CUS-04 | Remove the “Smooth Graphics” UI toggle. Rive is primary; Canvas remains an explicit fallback selector in Customization with a note that it is automatic when Rive fails. | No Smooth Graphics switch; Rive selected by default; Canvas selectable; note visible. | T-CUS-04 | P1 |
| CUS-05 | Keep the Icon Colour control (Auto/Black/White) and add a white↔black animated GIF preview. | GIF loops; control changes icon tint; preview matches. | T-CUS-05 | P1 |
| CUS-06 | Remove the System-Font Clock toggle. Clock uses the system font/format without a user switch. | No toggle; clock matches system font; no 24h hard-code. | T-CUS-06 | P1 |
| CUS-07 | Remove the Charging animation toggle (`chargingEnabled`) and `MIDDLE_SLOT_CHARGING` **only**. Do not remove the separate `MIDDLE_SLOT_PERCENT` Battery % option, which is the two-seat percentage object in RING-02. Keep actual charging bolt/state behaviour. | No Charging switch; no Charging % option; Battery % still present; charging still works; no dead pref. | T-CUS-07 | P1 |
| CUS-08 | Remove Microphone, Location, and Volume event toggles from UI and code (prefs, mapping, art references, tests). | No rows; no dead enum/art/tests; event mask migration safe. | T-CUS-08 | P1 |
| CUS-09 | Remove the Calls toggle from UI; keep Calls permanently off. Retain code/art for a future version, clearly documented as off. | No Calls row; event cannot fire; code path still off by default. | T-CUS-09 | P1 |
| CUS-10 | Remove the Simulation and Cell Simulation sections. Each remaining live event row (Alarm, Flashlight, Screenshot, Unlock) gets an animated GIF preview; remove Play buttons and level simulator. This is **four event GIFs**, separate from the Duo on/off and Icon Colour GIFs. | No simulation sections; 4 event rows show GIFs; no dead strings; GIF inventory matches. | T-CUS-10 | P1 |
| CUS-11 | Remove the duplicate About/Report block from Settings. Settings keeps module health, diagnostics/root/Shizuku, backup, update, donate, version. Contact Developer moves to Report. | Settings has no report form; Report has Contact; health/backup/update still reachable. | T-CUS-11 | P1 |
| CUS-12 | Sliders respond only on the knob/circle, not the track. Track taps do nothing; vertical swipes over a track scroll the page. Keep TalkBack `setProgress`. | Drag knob works; track swipe scrolls; value unchanged; accessibility action works. | T-CUS-12 | P1 |
| CUS-13 | Position editors move only from their icon/knob, not the rail. | Drag icon works; rail drag does nothing; page scroll unaffected. | T-CUS-13 | P1 |
| CUS-14 | Fix the Wi-Fi glyph toggle. It must change the ring on every supported surface without a UI restart. | Toggle off/on; glyph appears/disappears; dump/log; no restart needed. | T-CUS-14 | P1 |
| CUS-15 | The live preview is always present in Customization but does not animate continuously. It invalidates only when a toggle/slider/shape/preset changes, then returns to a stable frame. | Idle preview uses no render loop; change updates within 100 ms; no 5.2 s loop. | T-CUS-15 | P1 |
| CUS-16 | Camera/Compact uses the **main status bar only**. QS, shade, and keyguard keep the Default placement. Tapping the camera ring opens a **Resize** sheet; no live view resize/reparent. The P0 guard is main-only; the full camera art/Resize UI is P2. | Camera on main; normal on other surfaces; Resize sheet opens; no attach storm. | T-CUS-16 | P0/P2 |
| CUS-17 | Settings search covers the new/moved rows and does not point to deleted sections. | Search each removed/added row; no dead result. | T-CUS-17 | P1 |

---

## 5. Ring object model and geometry (P2)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| RING-01 | Centre is one object containing Wi-Fi and the live-event art. Moving the centre moves both; an active event uses the same transform and restores the centre occupant when it ends. | Event while Wi-Fi centre; both stay in the same seat; no jump; restore. | T-RING-01 | P2 |
| RING-02 | **Battery percentage** (`MIDDLE_SLOT_PERCENT`, not the removed Charging %) is a separate object with two seats: above (default) or centre. In centre, Wi-Fi/selected centre occupant takes the above seat. Both stay functional. | Toggle seat; number live; Wi-Fi/occupant moves; no overlap; split off; Charging % stays absent. | T-RING-02 | P2 |
| RING-03 | Clock is a dedicated text object, uses system 12/24h, locale/AM-PM, updates on time/time-zone/locale changes, stays on one line, and never touches the ring edges. | 12:34, 23:59, 12:34 PM all one line within safe width; system changes update. | T-RING-03 | P2 |
| RING-04 | Default ring layout matches `new duo customization`: number above, Wi-Fi centre, **eight SIM dots (four per side)**, battery line at the bottom. SIM1 right/SIM2 left is the current interpretation and must be confirmed from the image/owner before freeze; dots follow the ring curvature. | Headless render compare to reference; dot count/angle/right-left assignment; battery line correct. | T-RING-04 | P2 |
| RING-05 | Split battery: the battery line/arc separates into two halves; each half moves to the old four-dot position and keeps the same curvature; the eight SIM dots keep their right/left SIM mapping. The transition must be authored in Rive from the owner's design, not guessed. | Storyboard frames approved; no jump; reverse animation; Canvas clone. | T-RING-05 | P2 |
| RING-06 | When Battery % is centre, the layout uses a single in-ring view (no split-view icons) so the swap is physically possible; if split is selected, show a clear constraint/disabled state rather than a broken layout. | Toggle split + centre percent; UI explains; no overlap/missing icons. | T-RING-06 | P2 |
| RING-07 | Event priority: live event takes the centre/its object; after the event it restores the selected occupant. Charging does not occupy the middle (Charging % removed). | Alarm/flash/screenshot/unlock in each center mode; restore; no stuck icon. | T-RING-07 | P2 |
| RING-08 | Canvas is a clone from the same geometry table: ring, stroke, centre, percent, clock, eight dots, battery line, event scale/motion. | Side-by-side screenshots; pixel positions within tolerance; no missing Wi-Fi. | T-RING-08 | P2 |
| RING-09 | The `.riv` in the APK is byte-identical to a fresh build; no inert eases; layer order follows the documented rules. | Hash compare; `check-rive-eases.py`; `rive verify`; RML review. | T-RING-09 | P2 |
| RING-10 | Idle animation pauses when hidden/static; no continuous full-rate renderer. Preview/renderer wakeups only on real changes. | Battery/frame profile; no 130 mAh regression; preview idle 0 fps. | T-RING-10 | P2 |

---

## 6. Download progress and PHDP (P3)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| DL-01 | Add a Download Progress toggle with a direct latest-release link, a Simulate action, and a note that real PHDP status-bar simulation requires PHDP. | Toggle persists; link opens latest release; note visible; no PHDP duplication. | T-DL-01 | P3 |
| DL-02 | When a download starts, download progress overrides charging: show the download number and bar; hide the charging bolt/number. | Start download while charging at 50%; ring shows download only. | T-DL-02 | P3 |
| DL-03 | While download is active, tapping the ring switches to charging/battery. Tapping again may return to download. The download itself continues. | Two-way tap; state stays consistent; no lost progress. | T-DL-03 | P3 |
| DL-04 | At download completion, return to the previous state (charging if plugged/under 100, otherwise normal). At 100% charging, return to normal non-charging. | Complete at 100/99/unplugged; correct final ring. | T-DL-04 | P3 |
| DL-05 | PHDP detection is honest: show installed/absent; if absent, show the toggle GIF and a clear PHDP link; do not claim a live simulation. | PHDP installed/not installed; GIF vs open PHDP; no false status. | T-DL-05 | P3 |
| DL-06 | “Simulate” opens the PHDP app so the user can run its own test when PHDP is installed. If PHDP exposes no verified exported test component, Duo must not claim to start the test directly. Duo does not inject into PHDP’s non-exported receiver or add an unauthorized test broadcast. | Button opens PHDP; user runs the test; no broadcast injection; no security bypass. | T-DL-06 | P3 |
| DL-07 | Duo’s download percentage visibility is explicit. If PHDP `showLabel` is false, Duo may show its own number only with a documented switch; no hidden override. | PHDP label off/on + Duo switch; documented; test. | T-DL-07 | P3 |
| DL-08 | The download bar/number works on the main bar and every visible surface; only the ring has the tap override; split indicator view must not silently lose the download state. | Main/keyguard/QS/shade; split on/off; tap; no blank. | T-DL-08 | P3 |
| DL-09 | Use the real GitHub release endpoint; no fake stable APK URL. Show version and compatibility before download. | Link resolves; APK asset/version; no 404. | T-DL-09 | P3 |

---

## 7. Burn-in protection (P4)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| BURN-01 | Add a Burn-in protection toggle. If OxygenOS/SystemUI already shifts the element, the app must detect/document it and not double-shift. | System check/log; toggle disabled with note if already active. | T-BURN-01 | P4 |
| BURN-02 | When enabled, shift the whole Duo element 1 px left, hold for one hour, return, and repeat without a render loop or wake lock. | 2 cycles measured; 1 px; no frame loop; no visible jump. | T-BURN-02 | P4 |
| BURN-03 | Burn-in must not drain battery: no continuous timer, no full-rate renderer; use a low-frequency scheduler and pause when screen off. | 8 h idle battery delta within budget; no wakeups beyond schedule. | T-BURN-03 | P4 |

---

## 8. Every-UI, quality, and release (P5)

| ID | Requirement | Acceptance criteria | Test | Phase |
|---|---|---|---|---|
| QA-01 | Every change is verified on CPH2747 and marked `unverified` on every other corpus UI, including AOSP, until screenshots/logs exist. | Matrix status including AOSP; no false "supported" claims. | T-QA-01 | P5 |
| QA-02 | Public report cards are sanitized (no username/file); the private owner copy may contain the file/username. No PII in module logs. | Report test; grep; card screenshot; private vs public. | T-QA-02 | P5 |
| QA-03 | New crash signatures, settings, and phase evidence are documented. | `REPAIR-CATALOG`, `LIST-THE-PLAN`, test report updated. | T-QA-03 | P5 |
| QA-04 | Test/quality gates: unit, Compose, Rive headless, Canvas parity, `.riv` hash, ease check, route-log check, full app tests. Lint-vital exclusions must be explicit and justified, not silent. | All commands pass; no undocumented skipped gates. | T-QA-04 | P5 |
| QA-05 | Release candidate is built and installed on the owner's phone only after P0–P4 gates; rollback is documented. | Owner install log; rollback APK; no safe mode. | T-QA-05 | P5 |

---

## 9. Requirement → phase summary

| Phase | Requirements |
|---|---|
| P0 | STB-01…STB-08; main-bar camera guard (CUS-16 partial) |
| P1 | NAV-01…NAV-08; CUS-01…CUS-15, CUS-17 |
| P2 | RING-01…RING-10; CUS-16 full camera/Resize |
| P3 | DL-01…DL-09 |
| P4 | BURN-01…BURN-03 |
| P5 | QA-01…QA-05; all-UI verification and release |

Note: P2 and P3 are ordered for implementation convenience; download precedence can start after P1 because it touches the same rendering state machine, but no Rive art changes are allowed before P2's object-model gate.

---

## 10. Open items requiring owner/design confirmation

1. Split-battery **transition frames**: the static 8-dot image is clear; the motion (“battery moves to the old four-dot position”) is not. A short storyboard is required before RING-05 is coded.
2. Camera cutout geometry (center/radius per ROM) is unmeasured; start with the main CPH2747 and keep other ROMs `unverified`.
3. Whether `splitIndicators` should be force-disabled or merely warn when centre-percent/camera is selected.
4. Whether burn-in is already provided by OxygenOS; measure before implementing BURN-02.
5. Licensing: the report icon is Icons8 and the house/sliders references may also be stock; do not ship any reference raster or traced path data without a licence review.
6. Preview ownership: Home has a non-interactive preview; Customization has the live editing preview. Both share one `DuoSettings` state; only the Customization preview invalidates on changes.

---

## 11. Change log

- 2026-11-02: Initial 1.6 requirements from owner answers and crash evidence. This document did not change code; the worktree already contains the pre-1.6 beta.45 build input.


## FILE: REQUIREMENTS-GAP-AUDIT.md

# Requirements gap audit — after phone + 8-UI icon extraction

Date: 2026-10-31. Scope: `agent/REQUIREMENTS.md`, `agent/PLAN-v2.md`, Phases 1–8.
This audit answers "did we implement everything?" with **no promotion of unverified work**.

## 1. What was added in this pass

- **Phone icons pulled (no phone test):** `com.android.systemui` split set pulled over Wi-Fi ADB from
  CPH2747 to `reverse/device-systemui/com.android.systemui/` (108 MB).
  Base `SystemUI.apk` SHA-256 `dbe67fd3688a5bb5ebe7200190aaaac0bfbf88068a1534b6aebd62cd3f588ff4`.
  Nothing was installed, launched, toggled or tested on the phone.
- **ADB over Wi-Fi restored:** connected at `[redacted]`; used only for pull/dumps.
- **Eight corpus UIs decoded** with `apktool d -s --no-src`: Samsung, Xiaomi, vivo, Motorola,
  Nothing, OnePlus, OPPO, Honor.
- **Icon inventory tool:** `tools/extract-status-icons.py`.
- **Icon packs:** 5,353 drawable candidates, 24 MB, under gitignored `reverse/icon-packs/`, with
  `manifest.json` (source path, category, bytes, SHA-256, vector/raster).
- **Report:** `agent/EVENT-ICONS-PULL-REPORT.md`.
- **Static-map check:** `tools/check-rom-profile-assets.py` verified every id in the decoded resources.
  OxygenOS 8/8 and HyperOS 7/7 pass; six static maps pass after removing two guessed ColorOS ids and
  one guessed MagicOS id. AOSP has no decoded tree and remains an explicit error. See
  `agent/ROM-PROFILE-ASSET-AUDIT.md`.

Counts (phone + 8 UIs) are in that report. The phone pack alone has `flashlight=60`,
`screenshot=13`, `alarm=8`, `dnd=22`, `mic=47`, `location=33`, `airplane=24`.

## 2. Status by requirement group

| Group | State | What is still missing |
|---|---|---|
| UI-001 multi-UI | **partial** | 2 measured maps; 7 static-only, no runtime dumps |
| UI-002..UI-010 surfaces/host | **partial** | measured OnePlus/HyperOS; other UIs not measured; QS panel not proven |
| UI-011 per-icon toggles | **partial** | full stock-icon matrix not captured |
| UI-012 cell accuracy | **partial** | mapper + logs; TP-050..054 device pending |
| **UI-013 OEM icons** | **prototype done** | assets extracted; owner must choose/remove before release |
| UI-014 Canvas clone | **partial** | charging shake/pulse yes; every Rive state still not side-by-side |
| UI-015 parallel UI lanes | **partial** | corpus/assets exist; no per-UI runtime lane finished |
| UI-016 acceptance per UI | **not met** | only 2 measured; rest `UNVERIFIED` |
| CUS-001/002 engines | **partial** | forced engines work; Auto fallback not screenshot-proven on API 36 |
| CUS-003 middle slot | **partial** | Wi-Fi/cell/percent; clock/charging visuals unverified |
| CUS-004 layout options | **not done** | 4+4/bar/Wi-Fi-in-charge-slot UI not implemented |
| CUS-005 icon packs | **not done** | assets extracted, but no pack list/preview/switch in the app |
| CUS-006 show/hide | **partial** | Duo glyphs; stock icon matrix incomplete |
| CUS-007 presets | **partial** | data-driven presets exist; not all previewed |
| CUS-008 previews/help | **not done** | question-mark simulation not implemented |
| CUS-009 confused-user UX | **not verified** | no usability review |
| CUS-010 backup | **partial** | round-trip test; schema/version doc missing |
| CUS-011 three pages | **done** | Home/Engine/Settings |
| CUS-012 liquid glass | **code done, unverified** | live path on phone, static preview on emulator; owner visual sign-off pending |
| CUS-013 English only | **partial** | string audit not complete |
| LIVE-001/002 event set | **partial** | alarm/flashlight/screenshot/charging; **mic/location/volume sources missing** |
| LIVE-003/004 event motion | **partial** | three motions; volume/mic/location motion absent |
| LIVE-005/006 sources/consent | **not done** | no permission/consent flow for UsageStats/NotificationListener |
| LIVE-007 event-driven | **partial** | no polling in current path; battery/event audit not complete |
| RIV-001 quiet motion | **partial** | charge settle added; full design review pending |
| RIV-002 0→level sweep | **partial** | bolt flight + arrival pulse; **green-arc sweep not authored** |
| RIV-003 20% shake | **done** | 200 ms, both renderers, unit-tested; device pending |
| RIV-004 per-event motion | **partial** | alarm/flashlight/screenshot; rest missing |
| RIV-005 Canvas parity | **partial** | charge shake/pulse mirrored; full state matrix not |
| RIV-006 CLI | **done** | verify/build/screenshot/data/advance used |
| RIV-007 pause idle | **done** | existing renderer pause path; not re-measured on device |
| RIV-008 headless evidence | **partial** | idle + charge 96/110 frames; no per-state contact sheet |
| PERF-001..008 | **not met** | emulator p95 216.88 ms (baseline 116.87); device run and graphics-memory/battery checks pending |
| Phase 1 safe mode | **unverified** | `fix/landscape-safe-mode` not merged |
| DOC-005/006 | **partial** | release notes exist; backup schema doc missing |

## 3. "Things we skipped" — explicit list

1. **Icon extraction was skipped and is now closed** (phone + 8 UIs, 5,353 files).
2. **Icon-pack switching UI (CUS-005)** — extraction is done, the picker is not.
3. **Green-arc 0→level charging sweep (RIV-002)** — only the bolt flight and settle pulse exist.
4. **Mic/location/volume event sources and motions (LIVE/RIV-004)** — not implemented.
5. **Permission/consent flow (LIVE-005/006)** — not implemented.
6. **Layout options (CUS-004)** and **help/preview simulation (CUS-008)** — not implemented.
7. **Per-UI runtime measurements (UI-016/PERF-008)** — seven static maps only.
8. **Phase 1 safe-mode fix** — unmerged/unconfirmed.
9. **Device visual/performance sign-off** — CPH2747 has not run beta.20.

## 4. Safety and hygiene

- Vendor assets remain outside `app/`; no extracted icon is packaged.
- `reverse/` is local-only. Do not commit APKs, decoded trees, or icon packs.
- The phone was only read: `pull`/`getprop`; no test, install, reboot or settings change.
- Wi-Fi ADB remains available for future pulls; it can be disconnected with `adb disconnect`.


## FILE: REQUIREMENTS.md

# Duo Status Bar — Requirements v2 (Multi-UI Update)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Requirements v2 (multi-UI update) |
| Version | 1.0 — draft for owner approval |
| Date | 2026-10-26 |
| Owner | Abdulkarim |
| Author | Kilo (orchestrator) |
| Status | **Draft. No code until this document, TEST-PLAN, and PLAN-v2 are complete and approved.** |
| Sources | `brainstorms/2026-10-26-multi-ui-update-discovery.md`, measured CPH2747 evidence, `docs/REQUIREMENTS.md` (FR-01..28 / NFR-1..5), `agent/REPAIR-CATALOG.md` |
| Change model | Additive v2. Existing FR/NFR IDs stay valid and are referenced, not renumbered. |

## Legend

**Type:** `Feature` · `Bug` · `Refactor` · `Performance` · `Security` · `Test` · `Docs` · `Tooling`
**Priority:** `P0` blocks the phase · `P1` required for the release · `P2` can follow
**Status:** `[ ]` todo · `[~]` in progress · `[x]` done and verified · `[!]` blocked · `[u]` unverified

## Scope

Eight vendor UIs plus AOSP as the baseline:

1. OnePlus OxygenOS
2. OPPO / realme ColorOS
3. Xiaomi HyperOS / MIUI
4. Samsung One UI
5. vivo OriginOS / Funtouch
6. Honor MagicOS
7. Motorola MyOS / Hello UI
8. Nothing OS
9. AOSP / GSI (baseline)

A UI without a measured dump stays `unverified` and may not be claimed as supported.

## Non-negotiables

- Never guess a class, id, hook, or resource. Read it from the device or the APK.
- Never force/remove the stock battery or patch vendor code. Hook the existing host, draw Duo, restore exactly.
- Fail silent: a failure leaves the stock bar working and the module disabled for that surface.
- No new core architecture or hook framework. Follow the existing `DuoIconHost` / `ContainerFinder` / `RomAdapter` code paths.
- Every function that can fail must log enough to identify the UI, surface, class/function, hook, and reason.
- Every Rive design must have a Canvas clone.
- All files and file contents are English.
- No code before Requirements + Test Plan + Plan v2 are complete and approved.

---

## A. Multi-UI and surfaces

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| UI-001 | Feature | P0 | Support the 8 vendor UIs above plus AOSP; each UI has a measured profile or is marked `unverified`. | 7 | [ ] | `rom-profiles.json` + per-UI dump; no guessed ids |
| UI-002 | Feature | P0 | Duo replaces the battery and the status-icon cluster wherever the status bar shows them: status bar, keyguard, shade header, QS header. | 1, 7 | [ ] | per-surface dump + screenshot + log |
| UI-003 | Feature | P0 | Hook the existing battery host; draw Duo inside its bounds; restore the original drawing on disable. No removal, no separate overlay, no forced behaviour. | 1 | [ ] | before/after dump; original battery returns |
| UI-004 | Feature | P0 | Use the measured OnePlus QS tree: `quick_qs_status_icons` -> `icons` -> `statusIcons` (plain `LinearLayout`) + `batteryRemainingIcon` -> `battery_icon_view`. Do not assume `shade_header_system_icons` or `StatusIconContainer`. | 1 | [ ] | measured tree in `agent/REPAIR-CATALOG.md` |
| UI-005 | Feature | P0 | QS and shade are one surface for verification: the QS lives in the shade. Capture both, but a fix that works on one must work on the other. | 1 | [ ] | one probe, two captures |
| UI-006 | Feature | P0 | Same behaviour on every surface: status bar, keyguard, shade header, QS header. QS panel/tile only if it hosts the battery separately. AOD and the Settings app are out of scope. | 1, 7 | [ ] | surface matrix |
| UI-007 | Feature | P0 | Duo size/position is flexible right/left/top/bottom within the host bounds, with zoom. The same model applies to every surface, clipped to that surface. | 3 | [ ] | preview + device screenshots |
| UI-008 | Feature | P0 | If a battery/icon host cannot be resolved, leave the stock bar untouched and log a structured resolution trace (candidate ids/classes, tree path, bounds, hook attempt, reason). | 1, 7 | [ ] | `RC-010` in the repair catalog |
| UI-009 | Feature | P0 | Keyguard/lock screen uses the same element and behaviour as the status bar. | 1 | [ ] | lock/unlock cases |
| UI-010 | Feature | P1 | QS panel/tile is handled only after the header is proven and only if it hosts the battery separately. | 7 | [ ] | QS panel probe |
| UI-011 | Feature | P0 | The user controls which stock icons are hidden or shown. Do not force-hide every icon; silent/vibrate/alarm/others are per-icon choices. | 3 | [ ] | per-icon toggles + screenshot |
| UI-012 | Bug | P0 | Cell accuracy: four dots map to the system bar count; full bars on the phone must show full dots. Cover dual-SIM and 5G NSA. | 4 | [~] | raw logging + 5→4 mapper; device TP-050..054 pending · agent/PHASE-4-PLAN.md |
| UI-013 | Feature | P1 | OEM icons are extracted as-is for prototype analysis and engine testing. The owner removes them at the official launch; release builds must not rely on them. | 3 | [x] | `agent/EVENT-ICONS-PULL-REPORT.md` + `reverse/icon-packs/manifest.json` · owner sign-off still required |
| UI-014 | Feature | P0 | Every Rive design has a Canvas clone with the same state logic and geometry. | 3, 6 | [ ] | side-by-side render |
| UI-015 | Feature | P0 | Parallel per-UI work: each UI lane can be developed and tested independently, then merged into the phase branch. | 7 | [ ] | branch/PR map |
| UI-016 | Test | P0 | Acceptance is same behaviour on every supported UI. A UI without a device/dump is `unverified`, not "supported". | 7, 8 | [ ] | matrix + evidence per UI |

## B. Engines and customization

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| CUS-001 | Feature | P0 | Two engines: Rive and Canvas. User chooses; there is Auto/Recommended. | 3 | [~] | settings + renderer log · agent/PHASE-3-REPORT.md |
| CUS-002 | Feature | P0 | Auto tries Rive; on capability failure, bind timeout, or native failure, it switches to Canvas automatically. The user can force either. | 3 | [~] | reason logged; fallback screenshot · agent/PHASE-3-REPORT.md |
| CUS-003 | Feature | P0 | Middle-slot choice: Wi-Fi, cell, battery %, clock, charging number. Not only Wi-Fi/5G. | 3 | [~] | middle-slot choices implemented (mapping/channel tests); clock/charging partial until Rive visual verified · agent/PHASE-3-REPORT.md |
| CUS-004 | Feature | P0 | Layout options: 4+4 cell dots with the battery as a bar where the dots were; Wi-Fi can take the charging-number slot; battery % can take the middle. | 3 | [ ] | preview + device |
| CUS-005 | Feature | P0 | Icon packs: the user can change the shape of any icon and switch packs (e.g. Apple Wi-Fi, OnePlus Wi-Fi). OEM assets are prototype-only. | 3 | [ ] | pack list + preview |
| CUS-006 | Feature | P0 | Per-icon show/hide for stock icons; no forced hiding. | 3 | [~] | Duo component toggles (Wi-Fi glyph, cell dots) implemented (6649a56); stock-icon matrix remains partial. |
| CUS-007 | Feature | P0 | 3 ready-made presets + advanced customization. Presets are data-driven and changeable without code. | 3 | [~] | preset JSON + preview · agent/PHASE-3-REPORT.md |
| CUS-008 | Feature | P0 | Every toggle has a small static visual preview; a question mark plays a simulation of the toggle. | 3 | [ ] | UI test |
| CUS-009 | Feature | P0 | "Assume the user is easily confused": minimal text, visual-first, logical grouping, obvious defaults. | 3 | [ ] | usability review |
| CUS-010 | Feature | P0 | Settings backup/import: one plain versioned JSON file with all settings, preferences, and customization; no encryption, no secrets. Import validates and confirms before overwrite. | 3 | [~] | round-trip test · agent/PHASE-3-REPORT.md |
| CUS-011 | Feature | P0 | Three-page app with bottom navigation: Home, Engine, Settings. Backup/import lives in Settings. | 3, 5 | [~] | navigation test · agent/PHASE-3-REPORT.md |
| CUS-012 | Feature | P1 | Liquid-glass UI using `kyant0/androidliquidglass` (`io.github.kyant0:backdrop:2.0.1`); API 31+ blur, API 33+ shader, graceful fallback. | 5 | [ ] | device screenshots |
| CUS-013 | Feature | P0 | App UI is English only. | 3 | [~] | string audit · agent/PHASE-3-REPORT.md |
| CUS-014 | Feature | P2 | New sections can be added later without breaking the three-page model. | 3 | [ ] | navigation structure |

## C. Live events

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| LIVE-001 | Feature | P0 | Live event set: alarm, unlock, charging (exists), screenshot, volume up/down, location use, microphone use (exists). | 3, 6 | [ ] | event matrix |
| LIVE-002 | Feature | P0 | P0 events: charging, alarm, screenshot, mic, location. P1: volume, unlock. | 3, 6 | [ ] | priority table |
| LIVE-003 | Feature | P0 | When an event fires, the current middle occupant fades and the event icon appears with a Rive animation; Canvas clone matches. | 6 | [ ] | live capture |
| LIVE-004 | Feature | P0 | Each event icon has its own logic (e.g. alarm bounce/shake; screenshot flash; volume pulse). | 6 | [ ] | per-icon spec |
| LIVE-005 | Feature | P0 | Detection uses SystemUI hooks first. UsageStats/NotificationListener only with explicit user consent; Accessibility only if no alternative. | 3 | [ ] | permission flow |
| LIVE-006 | Feature | P0 | Missing permission or unavailable source degrades to the normal Duo state and logs the reason; no crash. | 3 | [ ] | failure cases |
| LIVE-007 | Performance | P0 | Live events are event-driven; no polling and no renderer running while idle. | 6, 7 | [ ] | `gfxinfo` + battery |

## D. Rive and animation

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| RIV-001 | Feature | P0 | Motion language: quiet luxury, short, purposeful, no idle loops. Improve the current animations; do not regress. | 6 | [ ] | design review |
| RIV-002 | Feature | P0 | Charging: a very subtle, almost invisible pulse travels from 0 to the current level over the green; when it reaches the level, it pulses. | 6 | [ ] | headless render + device |
| RIV-003 | Feature | P0 | At 20% charging, the Duo icon shakes (~200 ms). | 6 | [ ] | headless render |
| RIV-004 | Feature | P0 | Each live event has a defined motion (alarm bounce/shake, screenshot flash, volume pulse, etc.). | 6 | [ ] | spec + render |
| RIV-005 | Feature | P0 | Every Rive design has a Canvas clone with the same states and geometry. | 3, 6 | [ ] | side-by-side |
| RIV-006 | Tooling | P1 | Rive CLI 1.8.0 RML authoring; headless `--verify`, `--screenshot`, `--bench`, `--data`, `--advance`. | 0, 6 | [x] | `weapons/INVENTORY.md` |
| RIV-007 | Performance | P0 | Pause the renderer when hidden/idle; restart on a real change. | 6, 7 | [ ] | `gfxinfo`, battery |
| RIV-008 | Test | P0 | Headless pose screenshots per state + on-device screenshots; no unverified motion. | 6 | [ ] | evidence paths |

## E. Testing, logging, reporting

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| TST-001 | Test | P0 | Write tests for code that has none; every new function gets unit + path tests. | 2 | [ ] | coverage report |
| TST-002 | Test | P0 | Path coverage gate on changed code: 80% lines and branches. | 2 | [ ] | CI report |
| TST-003 | Test | P0 | Robolectric for resolver/hook decisions; instrumentation for attach/hide; on-device smoke per UI. | 2 | [ ] | test matrix |
| TST-004 | Test | P0 | Tests must pass on every supported UI with the same result; otherwise the UI is `unverified`. | 2, 7 | [ ] | per-UI evidence |
| TST-005 | Feature | P0 | Every function that can fail logs: `ui`, `surface`, `stage`, `class`, `id`, `hook`, `result`, `reason`, `detail`, `build`. | 2 | [ ] | log schema test |
| TST-006 | Feature | P0 | Log retention: ring file capped at 2 MB, last N sessions; redact PII; verbose in debug, errors in release; one line per state change, never per frame. | 2 | [ ] | log audit |
| TST-007 | Feature | P0 | Everything useful for repair is logged, no matter how small, within the cap and redaction rules. | 2 | [ ] | repair catalog coverage |
| TST-008 | Feature | P0 | In-app report produces a zip with dump + logcat + screenshot + build; share sheet; Telegram support channel later. | 2 | [ ] | report round-trip |
| TST-009 | Security | P0 | No PII in logs/reports: no SSID, IMEI, serial, accounts, location, notification text. | 2 | [ ] | redaction test |
| TST-010 | Test | P0 | Regression/linked tests: B↔C↔D, F↔B, H↔all. | 2, 7 | [ ] | test plan |
| TST-011 | Docs | P0 | `agent/REPAIR-CATALOG.md` maps log signature -> cause -> evidence -> tool -> fix -> verification; every new log adds an entry. | 2 | [x] | catalog |
| TST-012 | Test | P0 | Test matrix: 9 UI rows × surfaces × states × renderers × light/dark. | 2, 7 | [ ] | matrix in TEST-PLAN |

## F. Performance and battery

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| PERF-001 | Performance | P0 | Idle: 0 rendered frames; renderer paused when hidden/idle. | 7 | [ ] | `gfxinfo` frame count |
| PERF-002 | Performance | P0 | Animation: p95 frame time ≤16.7 ms; no frame >50 ms. | 7 | [ ] | `gfxinfo framestats` |
| PERF-003 | Performance | P0 | Graphics memory delta ≤3 MB for the Duo views. | 7 | [ ] | `dumpsys meminfo` |
| PERF-004 | Performance | P0 | Battery: 8 h idle ≤ stock +1% and ≤5 mAh, no wake locks. | 7 | [ ] | `dumpsys batterystats` |
| PERF-005 | Performance | P0 | Active use: 1 h scripted use ≤ stock +2% under the same script (proposed; owner to confirm). | 7 | [ ] | `batterystats` delta |
| PERF-006 | Performance | P0 | No polling loops; all updates are event/broadcast driven. | 7 | [ ] | code audit |
| PERF-007 | Performance | P0 | No per-frame allocation in the Canvas path; Rive reuse. | 7 | [ ] | heap/allocation trace |
| PERF-008 | Performance | P0 | Measure every supported UI, not only OnePlus; record device/build. | 7 | [ ] | per-UI perf table |

## G. Process and quality

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| PRC-001 | Docs | P0 | Order: Brainstorm -> Requirements -> Test Plan -> Plan v2 -> owner approval -> code. | 0 | [~] | this document |
| PRC-002 | Process | P0 | One branch per phase; never commit to `main`; merge only after the phase gate. | all | [ ] | git log/PRs |
| PRC-003 | Process | P0 | No new core architecture or hook framework; follow the old code paths. App-only UI libraries allowed; a new core dependency needs a written reason and approval. | all | [ ] | code review |
| PRC-004 | Process | P0 | Evidence over guessing; use the tools; reproduce before fixing; diff the dump. | all | [x] | AGENTS.md |
| PRC-005 | Process | P0 | One-shot clean code; no rushed code; quality over speed. | all | [ ] | review |
| PRC-006 | Process | P0 | Cheapest adequate model: BEAM for non-sensitive work; DeepSeek for architecture/code/debug; no GLM. | all | [x] | memory + agents |
| PRC-007 | Docs | P0 | All files and file contents in English. | all | [x] | this document |
| PRC-008 | Process | P0 | Owner approves every release; betas are pre-releases. | 8 | [ ] | release checklist |
| PRC-009 | Process | P1 | OEM assets are local/prototype only; the owner removes them at the official launch; no redistribution commitment. | 3, 8 | [ ] | owner sign-off |
| PRC-010 | Process | P0 | Any new requirement observed during development is added here before implementation. | all | [ ] | changelog |

## H. Documentation and release

| ID | Type | P | Requirement | Phase | Status | Verification / evidence |
|---|---|---|---|---|---|---|
| DOC-001 | Docs | P0 | `agent/AGENT.md` is the session anchor and is read first. | 0 | [ ] | file |
| DOC-002 | Docs | P0 | `agent/README.md` indexes the folder and read order. | 0 | [ ] | file |
| DOC-003 | Docs | P0 | UI matrix with 8 vendor UIs + AOSP, evidence tiers, acquisition protocol. | 0, 7 | [ ] | file |
| DOC-004 | Docs | P0 | Repair catalog is maintained with every new log. | 2 | [x] | `agent/REPAIR-CATALOG.md` |
| DOC-005 | Docs | P1 | Backup JSON schema/version documented. | 3 | [ ] | schema |
| DOC-006 | Docs | P1 | Release notes + README update for the multi-UI update. | 8 | [ ] | release |

---

## Traceability to phases and weapons

No implementation without a weapon. If the weapon cannot confirm the claim, the status is `unverified`.

| Phase | Branch | Requirements | Weapons / tools |
|---|---|---|---|
| 0 — Docs, tools, evidence | `phase/0-docs-evidence` | PRC-001, PRC-007, DOC-001..004, RIV-006 | REA (`open_binary`, `inspect_android_package`, `decode_android_resources`), JADX 1.5.6, Apktool 3.0.3, Rive CLI 1.8.0, git, Gradle |
| 1 — Shade/QS battery host | `phase/1-shade-qs` | UI-002..UI-006, UI-008, UI-009 | REA adb (`list_adb_devices`, `inspect_adb_window`, `inspect_adb_package`, `capture_adb_screen`), JADX (`search_android_classes`, `inspect_android_class`, `inspect_android_method`), Apktool resources, module `Diag` + `ShadeProbe`, `uiautomator`, `dumpsys window`, `adb install`, LSPosed log |
| 2 — Testing/logging/repair | `phase/2-testing-logs` | TST-001..TST-012 | Gradle `testDebugUnitTest`, Robolectric, instrumentation, REA adb/logcat, Python log parser, `agent/REPAIR-CATALOG.md` |
| 3 — App, engines, customization, backup | `phase/3-app-customization` | CUS-001..CUS-011, CUS-013, LIVE-001..LIVE-007, PERF-004..007 | Gradle/Compose, Rive CLI, adb screenshots, JSON unit tests, Robolectric, Frida/Kahlo MCP, REA Frida/adb |
| 4 — Cell accuracy | `phase/4-cell-accuracy` | UI-012 | REA adb, `dumpsys telephony.registry`, `dumpsys connectivity`, module logs, JADX for `SignalStrength` |
| 5 — Liquid glass + 3 pages | `phase/5-liquid-glass` | CUS-011, CUS-012 | Gradle/Compose, `dumpsys gfxinfo`, adb screenshots |
| 6 — Rive animations | `phase/6-rive` | RIV-001..RIV-008 | Rive CLI (`--verify`, `--screenshot`, `--bench`, `--data`), Canvas tests, adb screenshots |
| 7 — Multi-UI parallel + performance | `phase/7-multi-ui-perf` | UI-001, UI-007, UI-010, UI-014..016, PERF-001..008 | REA adb per UI, JADX/Apktool per SystemUI, `gfxinfo`/`meminfo`/`batterystats`, screenshots |
| 8 — Release | `phase/8-release` | PRC-008, PRC-009, DOC-005, DOC-006 | Gradle, git, Rive hash compare, log check scripts, owner approval |

## Weapons per requirement group

| Requirements | Implementation | Weapons / tools |
|---|---|---|
| UI-001..UI-016 | Multi-UI host resolution, surface attach, measurement | REA adb (`list_adb_devices`, `inspect_adb_window`, `inspect_adb_package`, `capture_adb_screen`), JADX (`search_android_classes`, `inspect_android_class`, `inspect_android_method`), Apktool (`decode_android_resources`), module `Diag` + `ShadeProbe`, `uiautomator`, `dumpsys window/activity`, `adb install`, LSPosed log |
| CUS-001..CUS-014 | Engines, customization, backup, 3 pages | Gradle/Compose, Rive CLI (`--verify`, `--screenshot`, `--bench`), adb screenshots, JSON unit tests, Robolectric, `dumpsys gfxinfo` |
| LIVE-001..LIVE-007 | Live event detection and animation | Frida/Kahlo MCP, REA Frida/adb, SystemUI hooks, `dumpsys`, logcat, consent flow tests |
| RIV-001..RIV-008 | Animation authoring and parity | Rive CLI (`--verify`, `--screenshot`, `--bench`, `--data`), Canvas tests, adb screenshots |
| TST-001..TST-012 | Tests, logs, repair catalog | Gradle `testDebugUnitTest`, Robolectric, instrumentation, REA adb, Python log parser, `agent/REPAIR-CATALOG.md` |
| PERF-001..PERF-008 | Performance and battery | `dumpsys gfxinfo ... framestats`, `dumpsys meminfo`, `dumpsys batterystats`, REA adb, per-UI scripts |
| PRC-001..PRC-010 | Process and quality | git, review-strict, `agent/REPAIR-CATALOG.md`, REA/JADX evidence |
| DOC-001..DOC-006 | Docs and release | git, markdown, Rive hash compare, log check scripts |

## Locked decisions

1. Hook the existing battery host; do not force/remove or overlay.
2. QS and shade are one verification surface.
3. Measured OnePlus QS tree is authoritative for this build.
4. Every Rive design has a Canvas clone.
5. Auto fallback: Rive -> Canvas on failure; user can force either.
6. App UI and support channel are English only.
7. Backup is a plain versioned JSON, no encryption.
8. No new core architecture; follow the old code paths.
9. 8 vendor UIs + AOSP; unmeasured UI stays `unverified`.
10. No code before Requirements + Test Plan + Plan v2 are approved.

## Open flags

- Windows "Duo logs" folder is not mounted; owner must run the read-only NTFS mount.
- CPH2747 module is active in SystemUI; the shade header still shows the stock battery (measured 2026-10-27).
- Support channel is later; no platform details yet.
- Tester availability per UI is not yet confirmed; those UIs stay `unverified`.
- All eight vendor SystemUI APKs are acquired in `corpus/` (static maps in `agent/UI-MATRIX.md`); runtime dumps for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS are still missing, so those UIs remain `unverified`.
- PERF-005 active-use +2% is proposed; owner must confirm.

## Acceptance gates

- **G0** Requirements approved.
- **G1** Test Plan approved.
- **G2** Plan v2 approved.
- **G3** Phase 1 shade/QS: measured dump + screenshot + log on CPH2747; stock battery restored on disable.
- **G4** Per-UI: measured evidence for each claimed UI.
- **G5** Performance/battery budgets pass.
- **G6** Owner release approval.


## FILE: ROM-PROFILE-ASSET-AUDIT.md

# ROM profile asset audit — static maps vs decoded APKs

Date: 2026-10-31. Tool: `tools/check-rom-profile-assets.py`.

This check asks one narrow question: **does a container/battery/clock id in `rom-profiles.json`
exist anywhere in the decoded resource tree?** It is static only; it does not prove runtime attach,
ordering, visibility, or that the id belongs to the battery cluster.

## Result

| Profile | Tier | Ids found | Missing |
|---|---|---:|---|
| oxygenos | measured | 8/8 | — |
| hyperos | measured | 7/7 | — |
| aosp | static | n/a | no decoded AOSP tree |
| coloros | static | 6/6 | — |
| oneui | static | 7/7 | — |
| originos | static | 7/7 | — |
| magicos | static | 5/5 | — |
| myos | static | 7/7 | — |
| nothingos | static | 7/7 | — |

## Corrections made

- **ColorOS:** removed `status_bar_end_side_content` and `quick_status_bar_header_system_icons` from
  the static map. They are absent from the decoded 3.0.4 tree; keeping them would be a guessed map.
- **MagicOS:** removed `hw_system_icons`. It is absent from the decoded Honor 11 tree; the remaining
  `system_icon_area`, `system_icons`, `statusIcons` are present.
- **AOSP:** no decoded tree is mapped, so the tool reports `no decoded tree mapped`. It remains
  public-source/static and cannot be promoted.

All edits preserve `measured=false` for the seven static entries. `RomDetection` still ignores them at
runtime; the unit test pins that only OxygenOS/HyperOS are measured.

## Limits

- A name can be present in a layout but unused on a particular device; this is not runtime proof.
- The check does not validate nested paths, visibility, parent ownership, or QS-vs-shade separation.
- The decoded vivo/OPPO/Honor trees came from APKs where `apktool` printed a warning and exited 1;
  their `res/` trees are usable but the decode is marked partial in the extraction report.
- Promote a profile to `measured` only after a device dump shows the path and bounds.


## FILE: SCREENSHOT-SOURCE-AUDIT.md

# Screenshot source audit — 8 vendor SystemUI APKs + CPH2747

Date: 2026-10-31. Method: apktool smali decode (`-r`), class inventory, plus a live ADB trigger on
CPH2747. This is a source audit, not a screenshot fix.

## Shared source

`com.android.systemui.screenshot.TakeScreenshotExecutorImpl` and
`com.android.systemui.screenshot.ScreenshotController` are present in **six of the eight** vendor
SystemUI APKs:

| UI | AOSP TakeScreenshotExecutorImpl | Notes |
|---|---|---|
| OnePlus 16.00.14-2 | yes | same AOSP package/class |
| Samsung 16-11 | yes | same class, different dex split |
| Xiaomi 17-03 | yes | **plus** `com.miui.systemui.screenshot.ScreenshotHelper` |
| vivo OriginOS 16.0.7.194 | yes | same class |
| Motorola 15.7 | yes | same class |
| Nothing 15.0.176 | yes | same class |
| OPPO 3.0.4 | **no** | old APK, different screenshot stack |
| Honor 11.0.0.1 | **no** | old APK, different screenshot stack |

That means **one AOSP hook can cover six interfaces**, not eight. Xiaomi may bypass it through its
`ScreenshotHelper`; that path must be verified separately.

## CPH2747 live result

`adb shell input keyevent 120` produced:

```
com.oplus.screenshot/LongshotCapture
```

No `DuoSB ... live event -> SCREENSHOT` line appeared. The AOSP executor class is present in the
OnePlus APK, but this key path is owned by the external `com.oplus.screenshot` process/activity.
So the existing `TakeScreenshotExecutorImpl` hook is **not the source for this OxygenOS gesture**.

## What this means

1. The shared hook is real for the six AOSP-derived UIs; use it as the primary source.
2. OxygenOS needs a second bridge for the Oplus screenshot session. Do not call the AOSP hook
   "working on OxygenOS" until `live event -> SCREENSHOT` is observed there.
3. OPPO 3.0.4 and Honor 11 are ancient maps; they are not evidence for current ColorOS/MagicOS.
4. Xiaomi's `ScreenshotHelper` must be checked before claiming Xiaomi support.
5. The Canvas branch was a separate bug; fixing it did not make the source fire.

## Recommended next step

- Keep the AOSP executor hook for six UIs.
- Add an Oplus screenshot bridge only after its class/method is measured (the external process is
  outside SystemUI; a naive hook there is not safe).
- Verify with the exact log line on CPH2747, not by taking a screenshot and guessing.
- No APK build from this audit.

## Evidence location

- `reverse/smali/<ui>/.../TakeScreenshotExecutorImpl.smali` (gitignored)
- `reverse/smali/xiaomi/.../ScreenshotHelper.smali`
- CPH2747 logcat line: `com.oplus.screenshot/LongshotCapture`


## FILE: TEST-PLAN-1.6.md

# Duo Status Bar — Test Plan 1.6

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Version | 1.6 (additive to `agent/TEST-PLAN.md` v2) |
| Date | 2026-11-02 |
| Owner | Abdulkarim |
| Status | **Draft for owner review; mirrors `REQUIREMENTS-1.6.md`** |
| Evidence | `reverse/fatal-logs/`, `reverse/kilo-images/`, owner answers, current source |
| Pass rule | A requirement is PASS only when the exact case passes on the named environment and the evidence is stored. Otherwise `UNVERIFIED` or `FAIL`. |

Legend: device unless stated; emulator allowed only where explicitly marked. Every failure must be captured before retrying. Never clear logs before saving.

---

## 1. Quality gates

| Gate | Command / evidence | Blocks |
|---|---|---|
| Compile + unit | `./gradlew :app:testDebugUnitTest :app:assembleDebug` (JDK 21) | All phases |
| Release compile | `JAVA_HOME=... ./gradlew :app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline`; the two lint-vital exclusions are explicitly recorded as non-gates with rationale | P5 |
| Rive verify | `rive verify rive/duo/duo.riv` / `tools/check-rive-eases.py` | Any Rive phase |
| `.riv` identity | fresh `rive rive/duo --once` + SHA-256 compare | Any Rive phase |
| Log routing | `tools/route-module-logs.py --check` | All |
| Crash gate | QS rotation + shade stress + camera/lock stress, zero FATAL | P0 exit |
| Full test | `agent/TEST-PLAN.md` linked tests + full app manual pass | P5 |
| Evidence | Each case row has a log/screenshot/dump path | P5 |

---

## 2. Environments

- **Owner phone:** CPH2747, Android 16, 1272×2772, 506 dpi, OxygenOS 16.0.9.400, KernelSU Next, LSPosed, beta.45 baseline.
- **Emulator:** AOSP API 36, 1080×2400 and landscape; no vendor QS.
- **Other ROMs:** only static/asset checks until a live device is available; mark `unverified`.
- **Toggles for A/B:** `stage=1` Canvas vs `stage=2` Rive; `show_in_qs`, `show_in_shade`, `live_apply`, `lock_ring`.
- **Logs:** `adb logcat -b all -d`, LSPosed verbose, dropbox, tombstone. Pull before clearing/rebooting.

---

## 3. Stability cases (P0)

### T-STB-01 — QS rotation ConstraintLayout + all injection sites

1. Set `show_in_qs=1`, `stage=2`, `use_rive=1`, `lock_ring=0`.
2. Open QS, confirm log shows parent class and injected view ID.
3. Rotate portrait→landscape; wait 10 s; rotate back.
4. Pull logcat, LSPosed, dropbox, tombstone.
5. Repeat 20×; check SystemUI PID and `am_crash`.
6. Repeat with split cluster/extra indicators on, and with shade header; inspect every injection site from `PLAN-1.6 P0.1` for `View.NO_ID`, including `DuoIconHost` addView sites, `DuoRiveView.kt:154`, and the `ContainerFinder` fallback parent.
- PASS: no `All children of ConstraintLayout must have ids`; every injected child has a non-NO_ID; PID unchanged; ring visible.
- FAIL: any `ConstraintSet.clone`/`OplusQSRootViewComponent` fatal, or any NO_ID injected child.
- Evidence: T-STB-01 timestamp, log excerpt, PID, screenshots, addView inventory.

### T-STB-02 — Rive attach/import storm

1. Start with main bar and shade host.
2. Hold shade open; toggle size, split, lock, and preset 20× over 60 s.
3. Count `File::import`, `Duplicate attach/onAttachedToWindow`, `Duo view ready`, and global refs in LSPosed; watch for SIGABRT.
4. Repeat with `stage=1` Canvas.
- PASS: one attach per surface lifetime; a rotation may create one new surface; imports do not grow per layout; no global-ref overflow; no restart.
- FAIL: more than one attach per surface, any detach/re-attach on layout, `Duo view ready` >5/s sustained, or any JNI abort.
- Evidence: counts, log timestamps, tombstone absence, screencap.

### T-STB-03 — Camera/lock ring reattach storm

1. On main bar only, enable the camera/lock placement with `split=0`, fixed size.
2. Open/close shade 20×; wait 60 s.
3. Record attach/import counts and SystemUI PID.
- PASS: one fixed surface; attach count = 1 per surface; no JNI overflow; no restart.
- FAIL: >1 attach per surface or attach rate >5/s sustained.
- Evidence: same as T-STB-02.

### T-STB-04 — Rotation stress

1. QS closed/open, camera off; rotate 20× each.
2. QS closed/open, camera on; rotate 20× each.
3. Check for blank bar, duplicate bar, ANR, fatal.
- PASS: no fatal/ANR; bar returns; no duplicate.
- Evidence: video/screenshots per rotation, log tail.

### T-STB-05 — Pre-crash signature reporting

1. In a debug build, force the attach/import path to write a pre-crash marker, then kill/restart SystemUI (test only).
2. Export a report after the restart.
- PASS: report includes `qs_constraint`/`rive_import`/`jni_refs` and the marker survives the restart.
- FAIL: no field, no surviving marker, or wrong signature.

### T-STB-06 — 100% charging

1. Set battery to 100% with charger connected (root/dumpsys or real).
2. Observe bolt, animation, number; unplug/replug.
- PASS: no bolt/charging loop at 100%; normal ring; replug behaves.
- Evidence: screenshot + log.

### T-STB-07 — Canvas fallback

1. Force an invalid/missing `.riv` or renderer failure in a debug build.
2. Observe main/QS/shade/keyguard.
- PASS: Canvas ring draws; bar remains; log reason; no restart.
- FAIL: blank/stock missing or crash.

### T-STB-08 — Surface presence after fix

1. Main, QS, shade, keyguard; camera on/off; rotation.
2. Confirm each has Duo or stock, never blank.
- PASS: four surfaces visible; screenshots.

---

## 4. Navigation cases (P1)

### T-NAV-01/08 — Three tabs + Settings
- Verify Home/Customization/Report; gear on Home opens Settings; no fourth tab; page content matches requirement.
- PASS: 3 tabs at portrait/landscape; gear; no duplicate customization on Home.

### T-NAV-02 — Icon authenticity
- Compare redrawn vectors to all six references at 1× and 2×; inspect APK assets for stock JPG/PNG; check licence note.
- PASS: silhouette/proportion match; no stock raster or traced path data; licence review covers all six sources.
- FAIL: packaged stock file or unlicensed copy.

### T-NAV-03 — Transparent selector/tint
- Screenshot resting active for each tab; sample selector fill; check active icon/label color and inactive color.
- PASS: no wine wash; active accent; inactive neutral; no double-tint.

### T-NAV-04 — Selector geometry
- Capture at 1272×2772; measure bbox; compare to 225×179 / provisional 296×231 and ±5% ratios.
- Confirm settled held size with the owner before freezing; the held image is mid-motion.
- PASS: inside ratio; held overflows; labels not clipped; active icon grows.
- FAIL: pill resizes, selector clipped, inactive icons grow.

### T-NAV-05/06 — Hold-drag
- Long-press Home, drag to Report; hold Report from Home; drag back; tap each tab; vertical scroll over pill.
- PASS: selector jumps to finger; snap on release; tap works; scroll not stolen; cancel restores; TalkBack tabs.
- Evidence: screen recording + log.

### T-NAV-07 — Duo on/off GIF
- Inspect asset frames, black background, loop, duration, hash; compare style to docs/media GIFs.
- PASS: original, correct loop, no Shutterstock frame.

---

## 5. Customization cases (P1)

### T-CUS-01 — IA inventory
- Walk every control; map to requirement. No orphan controls; live preview visible on Customization; tapping ring focuses controls.
- PASS: requirement table complete.

### T-CUS-02/03 — Presets
1. Record Default values; edit size/color; switch Default→Custom→Default→Compact→Custom.
2. Kill/relaunch app; verify persistence.
3. Import old backup with `classic`/`compact`/`bold`.
- PASS: exact restore; old compact/bold preserve pixels; no camera surprise; schema version.
- FAIL: custom loss, wrong preset, camera starts unexpectedly.

### T-CUS-04 — Engine
- No Smooth Graphics switch; Rive default; Canvas fallback selectable; note; force Rive failure.
- PASS: no crash; Canvas used.

### T-CUS-05 — Icon colour + GIF
- Cycle Auto/Black/White; view GIF; check white↔black; screenshot.
- PASS: control + GIF both work.

### T-CUS-06 — Clock font
- No System-Font toggle; compare clock font to system; 12/24 changes.
- PASS.

### T-CUS-07 — Charging removal
- Search settings for Charging animation and Charging %; confirm absent; confirm Battery % remains; charge phone; bolt still works.
- PASS: no dead UI; Battery % present; charging behavior preserved.

### T-CUS-08/09 — Event removals
- Verify no Mic/Location/Volume rows; no prefs/enums/art refs; Calls row absent; call cannot fire.
- PASS: grep + UI + event simulation.

### T-CUS-10 — Simulation GIFs
- No Simulation/Cell Simulation sections; each remaining event row has animated GIF; no Play/level.
- PASS: 4 event GIFs play/loop; Duo on/off and Icon Colour GIFs are separate; no live simulation controls.

### T-CUS-11 — Settings/Report split
- Settings: health, diagnostics, backup, update, donate, version. Report: form + Contact. No duplicate.
- PASS.

### T-CUS-12/13 — Slider/position interaction
- Drag knob; tap track; vertical swipe over track; rail drag; icon drag.
- PASS: only knob/icon changes; page scrolls; Track tap no-op except a11y; `setProgress` works.
- Evidence: video + value logs.

### T-CUS-14 — Wi-Fi glyph
- Toggle off/on; restart-free; main/QS/shade/keyguard; dump.
- PASS: glyph toggles on every surface without restart.
- FAIL: requires restart or no change.

### T-CUS-15 — Preview invalidation
- Idle 60 s; change one control; measure frames.
- PASS: 0 fps idle, update ≤100 ms on change; no 5.2 s loop.
- Evidence: frame stats/screens.

### T-CUS-16 — Camera/Resize
- Main camera mode; QS/shade/keyguard normal; tap rings opens Resize sheet; no live view resize; 60 s.
- P0 partial: main-only guard exists; P2 full: camera art + sheet.
- PASS: surfaces; sheet; no attach storm.
- FAIL: camera on QS or view resize.

### T-CUS-17 — Search
- Search every new/removed row.
- PASS: new found; deleted no result; no stale section.

---

## 6. Ring/Rive cases (P2)

### T-RING-01 — Centre object
- Headless render Wi-Fi + each event; trigger event while centre; restore.
- PASS: same seat; no jump; event ends to occupant.

### T-RING-02 — Battery % seat
- Default above; select centre; Wi-Fi/occupant above; number live; split off; Charging % stays absent.
- PASS: no overlap/clipping; Canvas parity.

### T-RING-03 — Clock
- `12:34`, `23:59`, `12:34 PM`, locale/timezone change, 12/24 toggle.
- PASS: system format, one line, safe width, update.
- FAIL: 24h hard-code, wrap, ring contact.

### T-RING-04 — 8 dots + battery line
- Headless compare to `new duo customization`: 8 dots, 4/side, bottom line, number above, Wi-Fi center.
- SIM1 right/SIM2 left is **UNVERIFIED** until measured/owner-confirmed; test the mapping conditionally.
- PASS: geometry/dot count/curve; screenshot; mapping confirmed.
- FAIL: 4 dots, wrong side after confirmation, line not bottom.

### T-RING-05 — Split battery transition
- Play forward/reverse at 1× and 0.25×; capture frames; verify two halves move to old dot positions, curvature preserved, no jump.
- PASS: owner-approved storyboard; Canvas clone.
- BLOCKED until storyboard approved.

### T-RING-06 — Centre Battery % + split constraint
- Select centre Battery % + split; observe.
- PASS: single-view enforced or clear disabled state; no missing icons/overlap.

### T-RING-07 — Event priority
- Alarm/flash/screenshot/unlock in each center mode; restore.
- PASS: event takes priority; restores; no stuck.

### T-RING-08 — Canvas parity
- Render Rive and Canvas same state; overlay screenshot/diff.
- PASS: ring, stroke, seats, dots, battery, clock, events match tolerance.

### T-RING-09/10 — Asset/ease/performance
- `rive verify`, hash compare, ease script; 8 h idle; preview idle fps.
- PASS: byte identity, no inert eases, battery within budget.

---

## 7. Download/PHDP cases (P3)

### T-DL-01 — Toggle/link/note
- Toggle persists; link opens latest release; note visible; PHDP status.
- PASS.

### T-DL-02 — Download over charging
- Start a real/simulated PHDP download at 50% charging.
- PASS: download number/bar only; bolt hidden.

### T-DL-03 — Tap switch
- Tap ring twice; check state; download continues.
- PASS.

### T-DL-04 — Completion/100%
- Complete at 99%, 100%, unplugged; check final.
- PASS: prior state; no stuck 100.

### T-DL-05/06 — PHDP absent/open
- PHDP absent: GIF + link; installed: open PHDP app for its own test; no injection.
- PASS: no claim that Duo starts PHDP’s test directly.

### T-DL-07/08 — Label/surfaces
- PHDP label on/off; Duo switch; main/keyguard/QS/shade; split on/off.
- PASS.

### T-DL-09 — Latest link
- Resolve GitHub latest; version/APK.
- PASS.

---

## 8. Burn-in cases (P4)

### T-BURN-01 — System check
- Inspect OxygenOS for built-in shift; log/disable.
- PASS: no double-shift.

### T-BURN-02 — 1 px hourly
- Enable; capture at t0, +1h, +2h; measure x offset; no frame loop.
- PASS: 1 px left for 1h, return, repeat.

### T-BURN-03 — 8h battery
- 8h idle same brightness/network; compare batterystats to baseline.
- PASS: within budget; no wake locks.

---

## 9. All-UI, quality, release (P5)

### T-QA-01 — UI matrix
- CPH2747 live; every other corpus UI, including AOSP, static/emulator; mark `unverified`.
- PASS: matrix with evidence and AOSP column.

### T-QA-02 — Privacy
- Send a report; verify the private owner copy may have file/user; the public group card has no file/user; module logs contain no PII.
- PASS: public vs private split; no PII in logs.

### T-QA-03/04/05 — Docs/gates/release
- Update repair catalog and evidence; run all documented gates; record the two lint-vital exclusions and why; install beta; rollback.
- PASS: no undocumented skipped gate.

---

## 10. Traceability

| Requirement | Case(s) |
|---|---|
| STB-01 | T-STB-01 |
| STB-02/03 | T-STB-02/03 |
| STB-04 | T-STB-04 |
| STB-05 | T-STB-05 |
| STB-06 | T-STB-06 |
| STB-07/08 | T-STB-07/08 |
| NAV-01…08 | T-NAV-01…07 (08 shares 01) |
| CUS-01…17 | T-CUS-01…17 |
| RING-01…10 | T-RING-01…10 |
| DL-01…09 | T-DL-01…09 |
| BURN-01…03 | T-BURN-01…03 |
| QA-01…05 | T-QA-01…05 |

Every requirement has at least one case; every case names its evidence. No orphan cases are allowed.

---

## 11. Entry/exit criteria

- **P0 entry:** requirements approved; crash evidence archived; branch/rollback chosen.
- **P0 exit:** STB-01…08 pass on CPH2747; zero QS/JNI fatals in 20 rotations + 60 s shade stress; no safe mode.
- **P1 entry:** P0 exit.
- **P1 exit:** NAV and CUS-01…15/17 unit, Compose, device cases pass; no blank surfaces. DL is P3; CUS-16 full camera is P2.
- **P2 entry:** P1 exit; split-transition storyboard drafted.
- **RING-05 coding gate:** storyboard owner-approved (not a P2 entry blocker for other tasks).
- **P2 exit:** Rive/Canvas parity; hash/ease gates; storyboard approved.
- **P3/P4 exit:** download/burn-in device cases.
- **P5 exit:** all-UI matrix + full suite + owner install; release candidate.

---

## 12. Change log

- 2026-11-02: Initial 1.6 test plan, mapped one-to-one to requirements. No test executed yet; the worktree already contains the pre-1.6 beta.45 build input, not a clean HEAD.


## FILE: TEST-PLAN.md

# Duo Status Bar — Test Plan v2 (Multi-UI Update)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Document | Test Plan v2 |
| Version | 1.0 — draft for owner approval |
| Date | 2026-10-26 |
| Owner | Abdulkarim |
| Author | Kilo (orchestrator) |
| Status | **Draft. No code until Requirements + this plan + PLAN-v2 are approved.** |
| Baseline | `agent/REQUIREMENTS.md` v1.0, `docs/test-cases.md`, `docs/rom-contract.md` |
| Change model | Additive. Existing cases stay valid; v2 adds multi-UI, surfaces, customization, live events, performance. |

## 1. Scope

### In scope

- 8 vendor UIs + AOSP: OnePlus OxygenOS, OPPO/realme ColorOS, Xiaomi HyperOS, Samsung One UI, vivo OriginOS, Honor MagicOS, Motorola MyOS, Nothing OS, AOSP/GSI.
- Surfaces: status bar, keyguard, shade header, QS header, QS panel/tile (only if it hosts the battery), app preview, landscape.
- Engines: Rive, Canvas, Auto fallback.
- Customization: middle slot, 4+4 dots, battery bar, icon packs, per-icon show/hide, 3 presets, live preview, backup/import.
- Live events: charging, alarm, screenshot, mic, location (P0); volume, unlock (P1).
- Cell accuracy: 4-dot mapping, dual-SIM, 5G NSA.
- Logging, repair catalog, report zip, fail-safe.
- Performance and battery budgets.

### Out of scope

- AOD and the Settings app battery.
- iOS or non-Android targets.
- Cloud sync of settings.
- Support channel implementation (Telegram is later).

## 2. Quality gates

| Gate | When | Contents | Pass condition |
|---|---|---|---|
| **G0** | now | Requirements approved | Owner approval |
| **G1** | after this doc | Test Plan approved | Owner approval |
| **G2** | after PLAN-v2 | Plan approved | Owner approval |
| **G3** | Phase 1 | Shade/QS on CPH2747 | Dump + screenshot + log; stock restored on disable |
| **G4** | per UI | Measured evidence on that UI | Dump + screenshot + log; no guessed ids |
| **G5** | Phase 7 | Performance/battery | All budgets below |
| **G6** | Phase 8 | Release | Owner approval |

## 3. Environments

| Environment | Purpose | Notes |
|---|---|---|
| CPH2747 (OnePlus 16, OxygenOS 16.0.9.400) | First real target | Wireless debugging `...245:6666`; module active in SystemUI; shade header still stock |
| AOSP emulator (`ae_api36_root`, `ae_api34_root`) | Baseline/rooted verification | `google_apis`, `adb root` expected; later phase |
| 8 vendor devices | Per-UI acceptance | One device/dump per UI; otherwise `unverified` |
| Root modes | Diagnostics | Magisk, KernelSU, APatch, Shizuku, none |
| Windows logs | User-corpus analysis | NTFS `OS` partition; read-only mount; owner action |

## 4. Test levels

| Level | What | Tool |
|---|---|---|
| L1 Unit | Pure logic: resolver, mapping, presets, backup codec, log schema | JVM tests |
| L2 Robolectric | View/hook decisions, tree fixtures, attach/hide paths | Robolectric |
| L3 Instrumentation | Real view attach/draw/restore on device | AndroidX test |
| L4 Device smoke | Per-UI status/keyguard/shade/QS | adb + screenshots |
| L5 Visual | Before/after, light/dark, Rive vs Canvas | screenshots |
| L6 Performance | Frame time, memory, battery | `gfxinfo`, `meminfo`, `batterystats` |
| L7 Fail-safe | Missing hook, missing permission, bad backup, provider down | fault injection |
| L8 Security/privacy | Redaction, no secrets, no PII | log/report audit |

## 5. Matrices

### 5.1 UI × surface

Rows: AOSP, OnePlus, ColorOS, HyperOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS.
Columns: status, keyguard, shade header, QS header, QS panel/tile, app preview, landscape.

Each cell: `PASS` / `FAIL` / `SKIP` / `UNVERIFIED` + evidence path.

### 5.2 State matrix

Battery: 0, 10, 19, 20, 50, 100.
Charging: on/off, 20% shake.
Saver: on/off.
Wi-Fi: 0-3, off, no internet.
Cell: 0-4, 4G, 5G NSA, dual-SIM (SIM1/SIM2/both).
DND, silent, airplane, Bluetooth, alarm, screenshot, volume, location, mic, download progress.
Percentage: on/off.
Middle slot: Wi-Fi, cell, battery %, clock, charging.
Layout: 4+4 dots, battery bar, Wi-Fi in charge slot.

### 5.3 Engine × renderer

Auto → Rive; Auto → Canvas on failure; forced Rive; forced Canvas.
Every visual case must pass in both Rive and Canvas, with the same state logic and geometry.

### 5.4 Colour

Light/dark; auto/black/white tint; every foreground shape follows the tint.

### 5.5 Root matrix

Magisk, KernelSU, APatch, Shizuku, none; `adb root` on the emulator.
Diagnostics must name the route and never produce an empty report.

## 6. Test cases

### 6.1 Shade / QS (Phase 1)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-001 | UI-002, UI-003 | On CPH2747, open the shade; screenshot + tree + log | Duo replaces the battery host; no stock battery under it |
| TP-002 | UI-004 | Compare the measured tree ids/classes | `quick_qs_status_icons`, `batteryRemainingIcon`, `battery_icon_view` resolved in the log |
| TP-003 | UI-005 | Capture shade and QS header in one run | Same result; one fix covers both |
| TP-004 | UI-003 | Disable the module; restart System UI | Stock battery returns exactly; screenshot |
| TP-005 | UI-008 | Force an unknown host fixture | Stock left untouched; structured trace logged |
| TP-006 | UI-009 | Lock/unlock | Same element/behaviour on keyguard |
| TP-007 | UI-010 | Expand QS panel | Header correct; panel only if it hosts the battery |
| TP-008 | TST-005 | Read the LSPosed log | `ui/surface/stage/class/id/hook/result/reason` present |
| TP-009 | UI-006 | Rotate (if allowed) | No 0x0; no half-attach; restore on failure |
| TP-010 | UI-007 | Change position/zoom | Clipped to the host; no overlap with clock |

### 6.2 Surfaces and hiding (Phase 1/7)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-011 | UI-002 | Status bar, keyguard, shade, QS | Same Duo element; no duplicate |
| TP-012 | UI-011 | Toggle each stock icon | Only the selected icons hide; others stay |
| TP-013 | UI-002 | Notification arrives | Notification icons unaffected |
| TP-014 | UI-006 | Unknown ROM fixture | No icon hidden; stock bar intact |
| TP-015 | UI-003 | Disable module | Every surface restores exactly |
| TP-016 | UI-010 | QS panel with battery | Correct host or `UNVERIFIED` |

### 6.3 Engines and customization (Phase 3)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-020 | CUS-001 | Choose Rive/Canvas/Auto | Setting persists; log shows engine |
| TP-021 | CUS-002 | Force a Rive failure | Auto switches to Canvas; reason logged |
| TP-022 | CUS-003 | Change middle slot | Live preview and bar match |
| TP-023 | CUS-004 | 4+4 dots / battery bar / Wi-Fi in charge slot | Geometry matches; no overlap |
| TP-024 | CUS-005 | Switch icon packs | Icon changes in preview and bar; fallback on bad asset |
| TP-025 | CUS-006 | Per-icon show/hide | Correct icons only |
| TP-026 | CUS-007 | Apply each preset | Preset matches; advanced remains editable |
| TP-027 | CUS-008 | Toggle preview + question mark | Static preview + simulation play |
| TP-028 | CUS-009 | First-run usability | No long text; visual-first; obvious defaults |
| TP-029 | CUS-010 | Export -> import | Round-trip identical; bad JSON rejected |
| TP-030 | CUS-011 | Navigate Home/Engine/Settings | Three pages; back stack correct |
| TP-031 | CUS-012 | Liquid glass on API 31/33/older | Effect or graceful fallback; no jank |
| TP-032 | CUS-013 | String audit | English only |
| TP-033 | UI-014 | Rive vs Canvas side by side | Same geometry/state |

### 6.4 Live events (Phase 3/6)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-040 | LIVE-001/002 | Trigger alarm, screenshot, mic, location, volume, unlock, charging | Correct icon; no wrong event |
| TP-041 | LIVE-003 | Event fires with Wi-Fi active | Wi-Fi fades; event icon appears; returns after |
| TP-042 | LIVE-004 | Per-icon motion | Motion matches the spec; Canvas clone |
| TP-043 | LIVE-005 | Permission granted/denied | Hook path first; consent path; fallback |
| TP-044 | LIVE-006 | Missing source/permission | Normal Duo state; reason logged; no crash |
| TP-045 | LIVE-007 | Idle after event | Renderer pauses; no polling |

### 6.5 Cell accuracy (Phase 4)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-050 | UI-012 | Compare phone bars vs 4 dots | Full = 4; 3/4 = 3; etc. |
| TP-051 | UI-012 | Dual-SIM | Correct per active subscription |
| TP-052 | UI-012 | 5G NSA | Reads 5G, not 4G |
| TP-053 | UI-012 | Weak/strong transitions | No flicker; monotonic mapping |
| TP-054 | UI-012 | Per-UI measurement | Mapping matches that UI's bars |

### 6.6 Performance and battery (Phase 7)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-060 | PERF-001 | Idle 5 min, screen on | 0 new frames; renderer paused |
| TP-061 | PERF-002 | Reveal + state changes | p95 ≤16.7 ms; no frame >50 ms |
| TP-062 | PERF-003 | `dumpsys meminfo` | Graphics delta ≤3 MB |
| TP-063 | PERF-004 | 8 h idle | ≤ stock +1% and ≤5 mAh; no wake locks |
| TP-064 | PERF-005 | 1 h scripted active use | ≤ stock +2% (proposed) |
| TP-065 | PERF-006 | Code audit | No polling loops |
| TP-066 | PERF-007 | Canvas path | No per-frame allocation |
| TP-067 | PERF-008 | Each claimed UI | Per-UI numbers recorded |

### 6.7 Logging, reporting, fail-safe (Phase 2)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-070 | TST-005 | Trigger a failure | Full structured line with reason |
| TP-071 | TST-006 | Fill the log | 2 MB cap; old entries rotate |
| TP-072 | TST-009 | Inspect log/report | No SSID/IMEI/serial/accounts/notification text |
| TP-073 | TST-008 | Export report | Zip with dump+logcat+screenshot+build; opens share |
| TP-074 | TST-011 | New log | Repair catalog has a matching entry |
| TP-075 | TST-001/002 | Changed code | 80% line/branch coverage |
| TP-076 | TST-003 | Resolver/hook paths | Robolectric + instrumentation pass |
| TP-077 | TST-004 | Per-UI | Same result or `UNVERIFIED` |
| TP-078 | TST-010 | Regression | Linked tests re-run |
| TP-079 | K fail-safe | Kill the hook, provider down, bad backup | Stock bar works; module off; no crash |

### 6.8 Multi-UI (Phase 7)

| ID | Requirement | Method | Pass criterion |
|---|---|---|---|
| TP-080 | UI-001 | Each UI | Measured profile or `UNVERIFIED` |
| TP-081 | UI-015 | Parallel lane merge | No conflicts; phase gate green |
| TP-082 | UI-016 | Per-UI acceptance | Same behaviour; evidence path |
| TP-083 | PRC-002 | Branch/PR audit | One phase branch; no main commit |

## 7. Performance protocol

### Frame timing

```bash
adb shell dumpsys gfxinfo com.android.systemui reset
# run the case
adb shell dumpsys gfxinfo com.android.systemui framestats
```

Pass: p95 ≤16.7 ms; no frame >50 ms; idle: 0 new frames over 5 minutes.

### Memory

```bash
adb shell dumpsys meminfo com.android.systemui
```

Pass: graphics delta ≤3 MB vs module off; no growth after repeated reveals.

### Battery

```bash
adb shell dumpsys batterystats --reset
# 8 h idle, same brightness/network
adb shell dumpsys batterystats com.android.systemui
```

Pass: ≤ stock +1% and ≤5 mAh over 8 h; `Wake Locks` zero for Duo; no alarm/wakelock from the module.
Active 1 h: ≤ stock +2% under the same script (proposed).

### Log

Use the LSPosed log, not `android.util.Log` alone:

```bash
adb shell su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'
adb shell su -c 'cat <log>' | grep 'DuoSB'
```

## 8. Entry and exit criteria

### Entry per phase

- Requirements approved.
- Plan approved.
- Previous phase gate green.
- Device/emulator available and scope enabled.
- Baseline dump captured.

### Exit per phase

- Phase tasks complete.
- Tests for the touched feature pass.
- Linked tests pass.
- Dump diff empty except timestamps/pids on a working ROM.
- No crash/ANR; stock bar works with the module off.
- Evidence paths recorded.

## 9. Current status (2026-10-26)

| Item | State |
|---|---|
| CPH2747 | **Offline/unreachable** (No route to host) after the owner left; LSPosed entered safe mode on the duplicate-fix build. Rollback prepared. |
| LSPosed | Module active in SystemUI (`DuoSB` lines, pid 10266); installed shade header still stock |
| Shade/QS tree | Measured: `quick_qs_status_icons` -> `batteryRemainingIcon` -> `battery_icon_view`; `statusIcons` is `StatusIconContainer` at 0x0 before layout |
| AOSP emulator | AVDs exist (`ae_api36_root`, `ae_api34_root`); not booted; emulator test pending |
| Windows logs | NTFS not mounted; owner action |
| UIs measured | OnePlus, HyperOS (existing profiles) |
| UIs unverified | ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS |
| Rive CLI | 1.8.0 installed |
| Liquid glass | Library identified; not integrated |
| Phase 1 build | `phase/1-shade-qs`; 221 unit tests pass; safe release 6,736,680; log check 0; **device not run** |
| LSPosed safe mode | Duplicate-fix build forced removeView/dropSlot/ourViews; owner-reported safe mode; exact crash line UNKNOWN (phone offline); forcing removed; safe build untested |
| 8-UI resolver | `shadeContainerIds`/`shadeBatteryId` candidate probes built; OnePlus `shadeMeasured=true`; six runtime dumps missing |
| Vendor APKs | 8 downloaded into `corpus/`; static maps in `UI-MATRIX`; 6 lack runtime dumps |
| EOL | mixed CRLF/LF in the working tree; normalize before a real commit |

## 10. Manual owner gates

- Visual sign-off per UI.
- OEM icon removal at official launch.
- Support channel creation (later).
- Release approval.
- Real-device test on each claimed UI.

## 11. Defect workflow

1. Reproduce with the diagnostic dump + logcat + screenshot.
2. Find or add a `RC-XXX` entry in `agent/REPAIR-CATALOG.md`.
3. Confirm the exact log string in the source.
4. Fix one thing; re-run the same case.
5. Diff the dump; update the catalog tier to `measured`.
6. Add a regression test.

## 12. Traceability

| Requirement group | Test cases |
|---|---|
| UI-001..UI-016 | TP-001..TP-016, TP-080..TP-083 |
| CUS-001..CUS-014 | TP-020..TP-033 |
| LIVE-001..LIVE-007 | TP-040..TP-045 |
| RIV-001..RIV-008 | TP-033, TP-042, TP-061, TP-066 |
| TST-001..TST-012 | TP-008, TP-070..TP-079 |
| PERF-001..PERF-008 | TP-060..TP-067 |
| PRC-001..PRC-010 | TP-083 + gate audit |
| DOC-001..DOC-006 | file audit |


## FILE: UI-MATRIX.md

# Duo UI Matrix (8 vendor UIs + AOSP)

Status: reference + measurement plan. This is not a claim of support. A UI is `supported` only after a measured dump, screenshot, and log exist for it.
Last updated: 2026-10-27 (Phase 1 visibility-hook audit added).

## Evidence tiers

| Tier | Meaning | May be claimed? |
|---|---|---|
| `measured` | Captured on a named device/build with the current code | Yes |
| `public-source` | AOSP/LineageOS source or a public module, not our device | Only as a hypothesis |
| `unverified` | No device evidence | No; this is a lead |

## Matrix

| # | Vendor / UI | Android base | SystemUI package / quirk | Status-bar / battery host | Tier | Next measurement |
|---|---|---|---|---|---|---|
| 0 | AOSP / GSI / LineageOS | 13-17 | `com.android.systemui` | `status_bar` -> `PhoneStatusBarView`; ids `clock`, `notification_icon_area`, `status_bar_end_side_content`; `system_icons` -> `LinearLayout`; `statusIcons` -> `StatusIconContainer`; `battery` -> `BatteryMeterView` | public-source | Boot `ae_api36_root`; dump the tree; confirm ids/classes |
| 1 | OnePlus / OxygenOS 16 | Android 16 / SDK 36 | `com.android.systemui`; `/system_ext/priv-app/SystemUI/SystemUI.apk` | Status: `status_bar_end_side_content` -> `com.oplus.systemui.statusbar.widget.EndSideContentLayout`; `system_icons` -> `android.widget.LinearLayout`; `battery` -> `...pipeline.battery.ui.view.StatBatteryMeterView`; `statusIcons` -> AOSP `StatusIconContainer`. Shade/QS: `quick_qs_status_icons` -> `icons` -> `statusIcons` (plain `LinearLayout`) + `batteryRemainingIcon` -> `battery_icon_view` | measured | Phase 1: hook the shade/QS battery host; capture before/after |
| 2 | OPPO / realme ColorOS | ColorOS 16 / Android 16 | `com.android.systemui`; lockscreen may run in `:ui` subprocess | Container **unverified**; do not reuse OnePlus classes without a dump | public-source (package/process), unverified (container) | Pull ColorOS SystemUI; dump shade/QS; measure battery host |
| 3 | Xiaomi / HyperOS / MIUI | HyperOS 3 / Android 17 (24129PN74G); Android 16 (24129PN74C) | `com.android.systemui`; `MiuiSystemUI.apk`; process may report `packageName="system"` | `system_icons` -> `com.android.systemui.statusbar.views.MiuiStatusBatteryContainer`; `statusIcons` -> `...views.MiuiStatusIconContainer`; `battery` -> `...views.MiuiBatteryMeterView`; `status_bar` -> `...phone.MiuiPhoneStatusBarView`; `clock` -> `...views.MiuiClock` | measured | Verify HyperOS shade/QS tree; add a second build |
| 4 | Samsung / One UI 8 | Android 16 | `com.android.systemui` | Container **unverified**; public hook `...statusbar.phone.ui.StatusBarIconControllerImpl`, `hideBySimplification(IconManager, String)`, `config_status_bar_system_icon_allowlist` | public-source (hook), unverified (container) | Pull One UI SystemUI; dump shade/QS; measure battery host |
| 5 | vivo / OriginOS / Funtouch | OriginOS 16 / Android 16 | Main SystemUI **unverified**; `com.vivo.systemuiplugin` is a separate scope for Smart Island | Container **unverified** | public-source (plugin), unverified (container) | Pull vivo SystemUI/plugin; dump; measure |
| 6 | Honor / MagicOS | MagicOS 10; Android base unverified | `com.android.systemui` | Container **unverified** | public-source (package), unverified (container) | Pull Honor SystemUI; dump; measure |
| 7 | Motorola / MyOS / Hello UI | Android 16 / API 36 | `com.android.systemui` | Container **unverified**; public ordering keys `user_switcher,wifi,mobile,battery` | public-source (package/order), unverified (container) | Pull Motorola SystemUI; dump; measure |
| 8 | Nothing / Nothing OS 4.0 | Android 16 | `com.android.systemui` | Container **unverified** | public-source (package), unverified (container) | Pull Nothing SystemUI; dump; measure |

## Phase 1 visibility-hook compatibility (exact-token audit)

This is a **static APK token audit**, not runtime support. It answers whether the new exact hooks can
install, not whether the UI behaves correctly.

| UI | `NotificationShadeWindowControllerImpl.onShadeOrQsExpanded(Boolean)` | `ShadeExpansionStateManager.onPanelExpansionChanged(..., PanelType)` | Exact `PanelType` | Phase 1 surface owner |
|---|---|---|---|---|
| OnePlus OxygenOS 16 / CPH2747 | present, measured | present, measured | `NOTIFICATION_PANEL`, `QS_PANEL`, `SHADE_PANEL` | supported, measured |
| Samsung One UI 16 | present | no 5-arg/`PanelType`; listener path only | absent | hook 1 only; unverified |
| Xiaomi HyperOS 17 | present | 3-arg `(FZZ)` only | absent | hook 1 only; unverified |
| vivo OriginOS 6 | present | `(ZFZ)` only | absent | hook 1 only; unverified |
| Motorola 15 | present | no declared 5-arg method | absent | hook 1 only; unverified |
| Nothing 15 | present | 3-arg `(FZZ)` only | absent | hook 1 only; unverified |
| OPPO 3.0.4 (old) | absent | absent | absent | structural only |
| Honor 11 (old) | absent | absent | absent | structural only |

Consequences:

- `onShadeOrQsExpanded` is the generic main-bar cover hook on the five modern non-OnePlus builds,
  subject to runtime verification.
- The QS-vs-shade owner hook only installs on the measured OnePlus signature. On the other modern
  builds `panelHookInstalled` stays false and the module uses attach-time ownership; do not claim
  their QS/shade surfaces as supported.
- Phase 2 alias targets: Samsung/Motorola `ShadeExpansionListener`; Xiaomi/Nothing
  `onPanelExpansionChanged(FZZ)`; vivo `(ZFZ)`. Each needs a per-UI QS/shade classifier and a
  runtime dump before it can be a matrix capability.
- The matrix (`RomDetection`/`RomAdapter`) selects status/shade IDs, but the visibility hooks are
  currently SystemUI-level and not yet gated per UI. Add a capability flag in Phase 2; do not infer
  support from the package name alone.

## What to measure next (per UI)

1. Build identity: `ro.build.fingerprint`, `ro.build.display.id`, `ro.build.version.sdk`, `ro.product.model`.
2. SystemUI package/version/sourceDir: `dumpsys package com.android.systemui`, `pm path com.android.systemui`.
3. Status bar tree: module `Diag` dump + `uiautomator dump` + `dumpsys window windows`.
4. Shade/QS tree: open the shade, dump, record the battery/icon host ids/classes/bounds, and the insertion route.
5. Keyguard tree: lock the device, dump.
6. Save screenshot + log + dump with a manifest entry.

## Acquisition protocol

### Allowed sources (safest -> riskiest)

1. AOSP / LineageOS / crDroid / PixelExperience source: open licences; can be stored and committed with notices.
2. `SystemUI.apk` pulled from a device the owner physically has: local analysis only; never commit.
3. User-submitted diagnostic dumps (`diagnose from users/`): consented; redact PII; never commit.
4. OEM firmware images from official sources: local only; never commit.
5. APK mirror sites: last resort; verify signer + SHA-256; quarantine; never commit.

### Pull commands (device)

```bash
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.display.id
adb shell dumpsys package com.android.systemui | grep -E 'versionName|versionCode|codePath|resourcePath'
adb shell pm path com.android.systemui
```

```bash
D=corpus/apks/<vendor>/<ui>/<build>
mkdir -p "$D"
adb shell pm path com.android.systemui | sed 's/^package://' | while read -r p; do
  adb pull "$p" "$D/$(basename "$p")"
done
sha256sum "$D"/*.apk
```

If `adb pull` is denied:

```bash
adb shell su -c "cp '/system_ext/priv-app/SystemUI/SystemUI.apk' /sdcard/Download/SystemUI.apk"
adb pull /sdcard/Download/SystemUI.apk "$D/SystemUI.apk"
```

### Host-side inventory

```bash
aapt2 dump badging "$D/SystemUI.apk"
aapt2 dump xmltree --file res/layout/status_bar.xml "$D/SystemUI.apk"
apktool d -f -o "$D/apktool" "$D/SystemUI.apk"
jadx -d "$D/jadx" --no-res "$D/SystemUI.apk"
apksigner verify --print-certs "$D/SystemUI.apk"
```

### Live tree

```bash
adb shell cmd statusbar expand-notifications
adb shell uiautomator dump /sdcard/duo-ui.xml
adb pull /sdcard/duo-ui.xml "$D/ui.xml"
adb shell dumpsys window windows > "$D/window.txt"
adb shell dumpsys gfxinfo com.android.systemui framestats > "$D/gfxinfo.txt"
adb shell cmd statusbar collapse
```

`uiautomator` may omit SystemUI windows; the module `Diag` tree is authoritative for our attach path.

## Per-UI acquisition checklist

| UI | Artifact to obtain | Evidence required | Status |
|---|---|---|---|
| OnePlus OxygenOS 16 | `SystemUI.apk` (already at `corpus/SystemUI-device.apk`) | status + keyguard + shade/QS dump, screenshots, LSPosed log | measured |
| Xiaomi HyperOS 3 | SystemUI APK (optional; dump already in `diagnose from users/`) | same, on 24129PN74G/24129PN74C | measured (dump) |
| OPPO/realme ColorOS | base + split `SystemUI.apk` | build fingerprint, display id, shade/QS tree, screenshots | APK acquired; runtime dump missing |
| Samsung One UI | base + split `SystemUI.apk` | same; verify `StatusBarIconControllerImpl` path | APK acquired; runtime dump missing |
| vivo OriginOS | `SystemUI.apk` + `com.vivo.systemuiplugin` if present | same; plugin/main-process split | APK acquired; runtime dump missing |
| Honor MagicOS | `SystemUI.apk` | same | APK acquired; runtime dump missing |
| Motorola MyOS | `SystemUI.apk` | same | APK acquired; runtime dump missing |
| Nothing OS | `SystemUI.apk` | same | APK acquired; runtime dump missing |
| AOSP/GSI | AOSP/LineageOS source | baseline ids/classes | public-source |

Owner action for each missing UI: provide the SystemUI APK/splits (and a diagnostic dump if possible), or approve an official firmware download. Until then the UI stays `unverified`.

## Local corpus layout (gitignored)

```text
corpus/
  index.json
  apks/<vendor>/<ui>/<build>/
  dumps/<vendor>/<ui>/<build>/
  decompiled/<vendor>/<ui>/<build>/
  screenshots/<vendor>/<ui>/
  quarantine/
```

Add `corpus/` to `.gitignore` before pulling anything.

## Manifest schema (`duo-corpus/1`)

```json
{
  "schema": "duo-corpus/1",
  "entry": {
    "id": "oneplus-oxygenos-16-cph2747",
    "vendor": "OnePlus",
    "ui": "OxygenOS 16",
    "android": { "release": "16", "sdk": 36 },
    "device": { "model": "CPH2747", "fingerprint": "<ro.build.fingerprint>", "display": "<ro.build.display.id>" },
    "systemui": { "package": "com.android.systemui", "versionName": "<dumpsys>", "sourceDir": "<pm path>" },
    "apks": [{ "name": "SystemUI.apk", "sha256": "<sha>", "size": 0, "signer_sha256": "<apksigner>" }],
    "source": { "kind": "owner-device", "url": null, "acquired_at": "2026-10-26", "method": "adb pull", "consent": "owner" },
    "evidence": { "status": "measured", "dump": "<path>", "dump_sha256": "<sha>", "public_urls": [] },
    "container": { "id": "quick_qs_status_icons", "class": "android.view.ViewGroup", "tier": "measured" },
    "license": { "redistribution": "prohibited", "commit": false },
    "redactions": ["ssid", "imei", "serial", "accounts"],
    "notes": "Shade/QS host measured 2026-10-26"
  }
}
```

For a public-source-only row: empty `apks`, `source.kind = "public-source"`, `evidence.status = "public-source"`, `container.tier = "unverified"`.

## Commit rules

| Item | Local corpus | Commit to GPL repo |
|---|---|---|
| Vendor APK / splits / oat / vdex | Yes | **Never** |
| Decompiled smali/JADX/OEM layouts/drawables/fonts | Yes | **Never** |
| AOSP/LineageOS source | Yes | Yes, with notices |
| User diagnostics/logcat/screenshots | Yes, redacted | **No** |
| Class/id/package facts with provenance | Yes | Yes, as documentation |
| SHA-256/fingerprint/source URL | Yes | Yes, metadata only |
| OEM icons | Local/prototype only | Owner removes before official launch |

## Per-UI header map (static, aapt2, 2026-10-27)

Source: downloaded SystemUI APKs in `corpus/`; xmltrees in `corpus/analysis/<vendor>-<layout>.xml.txt`. **Static evidence only** — runtime still needs a device dump.

| UI | QS/shade header layout | Icon cluster | Battery host | Notes |
|---|---|---|---|---|
| OnePlus OxygenOS 16 | `quick_status_bar_expanded_header` (`OplusQuickStatusBarHeader`) + `combined_qs_header` | `quick_qs_status_icons` (`ConstraintLayout`) -> `icons` (`AlphaOptimizedLinearLayout`) -> `statusIcons` (`StatusIconContainer`); also `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons` | `batteryRemainingIcon`; `battery_icon_view` only here | The visible QS cluster is `quick_qs_status_icons`; correct hooks: `OplusQSSimpleHeader.onInit` (simple QS) and `OplusSplitShadeHeaderControllerExImpl.init` (split shade). `ShadeHeaderController` alone gives the GONE `split_shade_status_bar` |
| Samsung One UI 16 | `sec_quick_status_bar_expanded_header` (`SecQuickStatusBarHeader`, no cluster) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons` (`StatusIconContainer`); `system_icons` -> `statusIcons` | `batteryRemainingIcon` (`BatteryMeterView`); `battery` in `system_icons` | No `quick_qs_status_icons`; use the combined header cluster or the status-bar cluster |
| Xiaomi HyperOS 17 | `miui_ns_qs_header_view` (`MiuiQSHeaderView`, nested `system_icon_area`) + `combined_qs_header` | `system_icons` (`MiuiStatusBatteryContainer`) -> `MiuiStatusIconContainer` `statusIcons`; `combined_qs_header` -> `shade_header_system_icons` -> `statusIcons` | `MiuiBatteryMeterView` `battery`; `batteryRemainingIcon` absent | No `quick_qs_status_icons`; the measured HyperOS 3 status-bar path is `system_icons` |
| vivo OriginOS 6 | `quick_status_bar_expanded_header` (`QuickStatusBarHeader`, no cluster) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons`; `system_icons` -> `statusIcons` | `VivoBatteryMeterView` `batteryRemainingIcon`; `battery` in `system_icons` | No `quick_qs_status_icons` |
| Motorola MyOS 15 | `modern_qs_header` (clock only) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons`; `system_icons` -> `statusIcons` | `BatteryMeterView` `batteryRemainingIcon`; `battery` in `system_icons` | No `quick_qs_status_icons` |
| Nothing OS 15 | `quick_status_bar_expanded_header` (`QuickStatusBarHeader`, no cluster) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons`; `system_icons` -> `statusIcons` | `BatteryMeterView` `batteryRemainingIcon`; `battery` in `system_icons` | No `quick_qs_status_icons` |
| OPPO ColorOS (3.0.4, old) | `quick_status_bar_expanded_header` + `quick_qs_status_icons` | `quick_qs_status_icons` (`LinearLayout`) -> `date` + `statusIcons`; `system_icons` -> `statusIcons` (`AlphaOptimizedLinearLayout`) | `quick_status_bar_system_icons` -> `battery`; `system_icons` -> `BatteryMeterView` `battery` (`oppo_battery_view`) | Old build; ColorOS 16 needs a device dump before claiming |
| Honor MagicOS (11.0.0.1, old) | `hw_notch_qs_header_status_bar` (`HwNotchStatusBar`) + `quick_status_bar_expanded_header` (`HwQuickStatusBarHeader`) | `HwStatusbarContents` -> `HwSystemIcons` `system_icon_area_fixed`, `HwSystemIconParentView` -> `HwSystemIconsMore` `statusIcons_more`, `HwSystemIcons` `system_icon_area`; `hw_system_icons` -> `statusIcons` | `hw_system_icons` -> include `battery` | Old build; MagicOS 10 needs a device dump |

**Common pattern:** OnePlus/Samsung/Xiaomi/vivo/Motorola/Nothing share `combined_qs_header` -> `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons` + `batteryRemainingIcon`. OnePlus/OPPO additionally expose a visible `quick_qs_status_icons` cluster. Honor uses `hw_system_icons`/`system_icon_area`. The code must resolve the visible cluster per UI; do not hardcode OnePlus ids.

**Profile schema implication:** `RomAdapter` currently carries only status-bar `containerIds`/`batteryId`. Add optional `shadeContainerIds`/`shadeBatteryId` (default empty) and parse them in `RomProfiles`; only then can each UI carry its own shade map without breaking the old status-bar path.

## Resolver wiring status (2026-10-27)

- `RomAdapter` now carries `shadeContainerIds`, `shadeBatteryId`, `shadeStatusIconIds`, `shadeMeasured`.
- `ContainerFinder.findShadeIconsArea` / `findVisibleShadeArea` / `findShadeBattery` consume the first two as candidate probes, gated on `width>0 && isShown && isAttachedToWindow`.
- OnePlus is `shadeMeasured=true`; Samsung, ColorOS, vivo, Motorola, Nothing, Honor and HyperOS shade maps are static/unverified.
- `shadeStatusIconIds` is not consumed yet.
- Runtime dumps are still missing for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS; do not claim support.

## Static resource matrix (aapt2, 2026-10-27)

Exact-name presence in the downloaded vendor SystemUI APKs. `absent` means the exact AOSP/OnePlus name was not found; it may be renamed, so it is **not** proof that the function is missing.

| UI | `quick_qs_status_icons` | `header_container` | `batteryRemainingIcon` | `battery_icon_view` |
|---|---|---|---|---|
| OnePlus OxygenOS 16 | present | present | present | present |
| Samsung One UI 16 | absent | absent | present | not checked |
| Xiaomi SystemUI 17 | absent | absent | absent | not checked |
| vivo OriginOS 6 | absent | absent | present | not checked |
| Motorola System UI 15 | absent | absent | present | not checked |
| Nothing OS 15 | absent | absent | present | not checked |
| OPPO System UI 3.0.4 (old) | present | absent | absent | not checked |
| Honor System UI 11 (old) | absent | absent | absent | not checked |

**Conclusion:** the OnePlus shade fix is not generic. The 8-UI code needs per-UI host resolution (resource name/class/role), with the OnePlus ids as one profile, not a global assumption.

## Candidate header layouts (static, aapt2, 2026-10-27)

Observed across the 8 downloaded APKs (vendor mapping is in `corpus/analysis/resources-<vendor>.txt`; do not assign a name to a UI without that file):

`combined_qs_header`, `quick_qs_status_icons`, `quick_status_bar_expanded_header`, `quick_status_bar_header_system_icons`, `system_icons`, `hw_system_icons`, `modern_qs_header`, `normal_shade_header`, `shade_header_container`, `large_screen_shade_header`, `sec_quick_status_bar_expanded_header`, `miui_ns_qs_header_view`, `miui_quick_status_bar_expanded_header`, `hw_notch_qs_header_status_bar`.

This confirms the 8-UI code needs per-UI layout/profile resolution. A single OnePlus id set is not portable.

## Current status

- Measured: OnePlus CPH2747 (OxygenOS 16), Xiaomi 24129PN74G / 24129PN74C (HyperOS).
- Acquired locally: all eight vendor SystemUI APKs in `corpus/` (versions/hashes in `corpus/index.json`), plus HyperOS dumps (`diagnose from users/`) and AOSP source references.
- Public-source: AOSP baseline.
- **Runtime dumps are still missing** for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS. Their APK maps are static only (`public-source`), so those UIs remain `unverified`; do not claim support.
- OPPO (3.0.4) and Honor (11.0.0.1) APKs are old builds; they are useful for structure, not for current ColorOS/MagicOS claims.
- Disk budget: ~8.5 GB free.
- No QS panel tree captured beyond the header.
- No vendor APK committed.

## Sources

- AOSP `status_bar.xml`, `system_icons.xml`, `StatusIconContainer`, `BatteryMeterView`: `https://android.googlesource.com/platform/frameworks/base/`
- OnePlus/HyperOS measured dumps: `diagnose from users/`, `app/src/main/assets/rom-profiles.json`
- Public module references for unverified rows: LSPosed module pages, XMiTools, HyperDuo, HonorDuo, Motorola-Tweak-Mod, NothingTweaks.

## Visibility-hook capability (machine-readable, T2)

Fields live on `RomAdapter` (`coverHook`, `panelExpansionKind`, `panelHookMeasured`) and are parsed from
`app/src/main/assets/rom-profiles.json`. They describe the **static exact-token audit** only; they are not
a claim of runtime support. A UI is runtime-supported only after a measured dump exists (see Evidence tiers).

| UI | coverHook | panelExpansionKind | panelHookMeasured | panelOwnerAvailable | Status |
|---|---|---|---|---|---|
| OnePlus OxygenOS 16 / CPH2747 | true | panel_type_5arg | true | true | measured |
| Samsung One UI 16 | true | listener | false | false | unverified |
| Xiaomi HyperOS 17 | true | fzz_3arg | false | false | unverified |
| vivo OriginOS 6 | true | zfz_3arg | false | false | unverified |
| Motorola MyOS 15 | true | listener | false | false | unverified |
| Nothing OS 15 | true | fzz_3arg | false | false | unverified |
| OPPO / ColorOS | false | none | false | false | unverified |
| Honor / MagicOS | false | none | false | false | unverified |
| AOSP baseline | true | panel_type_5arg | false | false | public-source |

Runtime support is **not** claimed by these fields; they only state which exact hooks the static audit
found. Each non-measured row still needs a per-UI QS/shade classifier and a runtime dump before its
panel hook can install.

`panelOwnerAvailable` is `true` only for OnePlus/OxygenOS (measured 5-arg `onPanelExpansionChanged`
with a `PanelType`); it is `false` for AOSP and every other UI. A `false` value means the UI may
still report cover/expansion via `panelExpansionKind`, but never QS-vs-shade ownership.


## FILE: WALKTHROUGH-PLAN-1.7.0.md

# Duo Status Bar — Walkthrough Plan (1.7.0 stable)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | Post-1.7.0 stable walkthrough |
| Date | 2026-11-04 |
| Branch | `fix/1.6-stability` at `7361c73` |
| Build under test | `1.7.0` (versionCode 89) or the build produced after the review fixes |
| Requirements | `agent/AGILE-1.7-REQUIREMENTS.md` |
| Test plan | `agent/AGILE-1.7-TEST-PLAN.md` |
| Review plan | `agent/CODE-REVIEW-PLAN-1.7.0.md` |
| Measured device | OnePlus CPH2747 (OxygenOS 16, Android 16 / SDK 36) |
| Secondary evidence | Xiaomi 24129PN74G (HyperOS 3) if available; everything else `unverified` |
| Status | Ready to execute; device steps require an attached phone or booted emulator |

## 1. Purpose

Walk the 1.7.0 build through the approved requirements and the performance/battery hypotheses in
the review plan. The walkthrough is evidence-first: a step passes only with the exact log,
screenshot, recording or measurement named below. Anything without evidence is `unverified`, not
passed.

## 2. Preconditions

1. Device booted, `adb devices` shows it, and `adb shell getprop ro.build.version.sdk` returns 36
   for the measured phone.
2. LSPosed active, module enabled for **System UI**, SystemUI restarted after install.
3. Install the build under test:

   ```bash
   adb install -r build/duo-1.7.0-debug.apk
   adb shell su -c 'killall com.android.systemui'   # or the ROM's restart path
   ```

4. Clear only the app-side log buffer, never the persistent crash history:

   ```bash
   adb logcat -c
   adb logcat -s DuoSB:* > /tmp/duo-walkthrough.log
   ```

5. Record the baseline battery state:

   ```bash
   adb shell dumpsys battery
   adb shell dumpsys batterystats --reset
   ```

6. Take a baseline screenshot and save the module's diagnostic dump.

## 3. Static walkthrough (code, before touching the device)

Execute in this order; each step cites the file that owns the behaviour.

### S1 — Requirement traceability

- Open `agent/CODE-REVIEW-PLAN-1.7.0.md` §4.
- For every requirement ID, open the named code owner and confirm the implementation path exists.
- Mark `pass`, `fail`, or `unverified`; do not mark `pass` from reading alone when the acceptance
  criterion is visual or device-measured.

### S2 — Size and persistence

- `settings/DuoPrefs.kt`: `MIN_SIZE = 50`, `MAX_SIZE = 220`,
  `logicalToPhysical = 50 + 1.7 * logical`.
- `ui/DuoSettingsSections.kt`: the slider speaks logical 0..100 and stores physical.
- `hook/DuoIconHost.kt`: the stored physical size is applied without re-deriving from the design.
- Confirm switching Classic/2 SIM does not write a different size.

### S3 — Ring and battery mapping

- `hook/DuoMapping.kt`: Classic vs 2 SIM geometry, live level, charging/saver colour, PHDP wash.
- `hook/RingGeometry.kt`: battery bar follows the same arc as the dots.
- `hook/SignalMapping.kt`: per-SIM 4/8-dot mapping; no full-to-one-dot case.
- `hook/CellReader.kt`: raw level, max levels, per-subscription samples.
- `hook/DownloadWash.kt`: progress uses the selected design.

### S4 — Transitions and host lifecycle

- `hook/PanelHandoff.kt`: `swapOrder`, `coverOrder`, `OutgoingLatch`, `main`, `extra`,
  `horizontalHandoff`.
- `hook/DuoIconHost.kt`: one cached view per bar, pinned width, no reparent storm, survival timer.
- `hook/DuoHook.kt`: receiver registration, settings debounce, shade settle.
- `hook/DuoRiveView.kt`: renderer start/stop, `setRenderActive`, identical-snapshot skip,
  teardown removes callbacks.

### S5 — Icons and navigation

- `settings/StockIconInventory.kt`: dynamic slot inventory.
- `settings/StockIconHider.kt` and `hook/StockIconHider.kt`: per-icon hide, All switch, battery
  anchor stays `INVISIBLE` (never `GONE`), restore before reapply.
- `ui/DuoShell.kt`: four pill tabs, short "Custom" label, Settings one tap away.
- `ui/DuoNavIcons.kt`: supplied icons.

### S6 — Glass, report and previews

- `ui/GlassSurface.kt`, `ui/GlassControls.kt`: measured ColorDuo values, press/drag/settle.
- `ui/DuoReportScreen.kt`: title below the blur.
- `DuoPreview.kt`, `ui/DuoSettingPreview.kt`, `ui/PreviewSimulator.kt`: preview controls, idle
  pause, event previews.
- Confirm no old "hide all other icons" or old Simulation section remains.

### S7 — Performance/battery static checks

For each hypothesis P1–P20 in the review plan:

- Find the start and stop of every timer, animation, receiver, observer and renderer.
- Confirm the stop runs on the same lifecycle edge as the start (detach, disable, screen off,
  composition dispose, worker cancel).
- Confirm identical state does not restart work.
- Record the file and line that proves or contradicts the hypothesis.

## 4. Runtime walkthrough

### R1 — Boot and first attach

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R1.1 | Reboot or restart SystemUI with the module enabled | `adb logcat -s DuoSB:*` | `Duo view ready`, no FATAL |
| R1.2 | Check the stock bar is intact | screenshot | stock icons present, Duo present |
| R1.3 | Disable the module and restart SystemUI | screenshot + log | stock bar restored exactly |
| R1.4 | Re-enable | log | attaches once, no duplicate views |

### R2 — Ring designs and battery

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R2.1 | Home → Classic | screenshot | four dots, old ring |
| R2.2 | Home → 2 SIM | screenshot | eight dots, live battery line |
| R2.3 | Set battery 100/80/49/19 (or simulator) | screenshots | bar follows level |
| R2.4 | Charging, saver, 100% plugged | screenshots | colour/bolt rules preserved |
| R2.5 | Trigger PHDP download in both designs | screenshots | selected geometry |
| R2.6 | Camera toggle with each design | screenshots | current design on cutout, Wi-Fi outside, normal QS/shade/keyguard |
| R2.7 | Custom: move centre, detach Wi-Fi, move number, rotate, resize | screenshots | editor works; template edits persist; Custom blank |

### R3 — Size and position

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R3.1 | Logical size 0, 50, 100 | screenshots + measured pixels | 50 / 135 / 220 physical; visible at 0 |
| R3.2 | Switch Classic ↔ 2 SIM at each size | screenshots | optical size identical, stored size unchanged |
| R3.3 | Drag position 0 → max → 0 ten times | screen recording | no disappearance, pointer attached, no reset |
| R3.4 | Release mid-range and re-grab | recording | re-grab works |

### R4 — Transitions (BAT-02/03)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R4.1 | Record 20 HUD→QS | 60 fps capture | no jump >2 px, no blank, no size flash |
| R4.2 | Record 20 QS→HUD | capture | Duo present whenever stock battery present |
| R4.3 | Record 20 QS→shade | capture | same |
| R4.4 | Record 20 shade→QS | capture | same |
| R4.5 | Record 20 shade→HUD | capture | same |
| R4.6 | Record 20 HUD→shade | capture | same |
| R4.7 | Repeat with camera on | capture | same |

If the device encoder caps below 60 fps, record the measured rate and mark the 60 fps criterion
`unverified`; do not claim it.

### R5 — Per-icon hiding

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R5.1 | Open the per-icon list | screenshot | dynamic slots, no guessed entries |
| R5.2 | Toggle each switch | screenshots | only that icon hides; state persists |
| R5.3 | All on/off | screenshots | every switch follows; persists |
| R5.4 | Hide battery | screenshot + log | Duo battery stays, anchor survives, no black slot |
| R5.5 | Move Wi-Fi outside; hide stock Wi-Fi | screenshot | Duo Wi-Fi remains |
| R5.6 | Rotate and restart SystemUI | screenshot | hiding reapplied correctly |

### R6 — Glass, report, previews

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R6.1 | Compare navigation glass with ColorDuo 1.1.7 | side-by-side screenshots | transparency/blur/rim/shadow match measured values |
| R6.2 | Press, drag, settle, cross tabs | recording | no label blur, drag from anywhere, haptics match |
| R6.3 | Open report page | screenshot | title below blur, no clipping |
| R6.4 | Exercise live preview: charge, saver, 8-dot, inversion | recording + log | updates <100 ms; no continuous idle loop |
| R6.5 | Play four event previews | recording | Alarm/Flashlight/Screenshot/Unlock animate |

### R7 — Performance and battery

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R7.1 | Idle 10 min with screen on, Duo visible | `dumpsys batterystats`, CPU/GPU sample | no unexpected wakeups; renderer paused |
| R7.2 | Idle 60 s with the app open on Home | log | one preview ready, idle pause, no GC storm |
| R7.3 | Idle 10 min with the app backgrounded | `dumpsys batterystats` | no app-side frame loop |
| R7.4 | Screen off 30 min | `dumpsys batterystats`, log | no wake lock, no repeating writes beyond the documented heartbeat |
| R7.5 | Trigger RSSI/connectivity bursts | log volume + CPU | bounded reads; no log flood |
| R7.6 | Rotate repeatedly | log + view count | no duplicate receivers/observers/renderers |
| R7.7 | Kill/relaunch the app | log | workers and previews stop/restart cleanly |

Measurement commands:

```bash
adb shell dumpsys batterystats --charged com.android.systemui
adb shell dumpsys batterystats --charged io.github.kvmy666.duostatusbar
adb shell top -b -n 1 -o PID,USER,%CPU,RES,NAME | grep -E 'systemui|duostatusbar'
adb shell dumpsys gfxinfo com.android.systemui framestats
adb shell dumpsys gfxinfo io.github.kvmy666.duostatusbar framestats
```

### R8 — Fatal and safe-mode regression

Run the 1.6 P0 suite:

- QS rotation, shade stress, camera/lock, 100% charging.
- Toggle the module off/on 10 times.
- Rotate 20 times with the shade open and closed.
- Confirm the crash buffer is empty and no tombstone names `duostatusbar`.

```bash
adb logcat -b crash -d
adb shell dumpsys dropbox --print data_app_crash | tail -200
```

## 5. Evidence layout

Store under `docs/evidence/agile-1.7-review/`:

- `logs/` — logcat slices, diagnostic dumps.
- `screens/` — ring, size, icons, glass, report.
- `transitions/` — recordings and frame measurements.
- `perf/` — batterystats, CPU/GPU, gfxinfo.
- `summary.md` — requirement verdicts and finding IDs.

## 6. Pass/fail rules

- PASS only with the named evidence and the exact acceptance value.
- FAIL when the evidence contradicts the requirement.
- `unverified` when the device, recording rate or ROM evidence is missing.
- A performance fix is PASS only when the visible output is unchanged and the measured cost drops
  or the redundant work is provably removed.

## 7. Rollback

- Keep the installed 1.7.0 stable APK and its SHA-256.
- If a review fix regresses a transition or a fatal guard, reinstall the stable APK, restart
  SystemUI, and record the failing evidence before any second attempt.
- Never leave the device in a half-attached state: disable the module and restore the stock bar.

## 8. Exit criteria

- Every requirement row has a verdict with evidence.
- Every performance/battery finding is fixed, deferred with a reason, or marked `unverified`.
- No fatal, no safe mode, no new permission, no new secret.
- The walkthrough summary links every finding to its evidence file.


## FILE: WALKTHROUGH-PLAN-1.8.0.md

# Duo Status Bar — Walkthrough Plan (1.8.0)

## Document control

| Field | Value |
|---|---|
| Project | Duo Status Bar |
| Phase | 1.8.0 owner walkthrough |
| Date | 2026-11-05 |
| Branch | `feat/1.8-selector-live-preview` |
| Preserved | `fix/1.7.0-review-fixes` at `eafdcb8`; commits `745fc5f`, `eafdcb8` |
| Build under test | 1.8.0 candidate APK produced by the plan; not a release |
| Requirements | `agent/AGILE-1.8-REQUIREMENTS.md` |
| Plan | `agent/AGILE-1.8-PLAN.md` |
| Test plan | `agent/AGILE-1.8-TEST-PLAN.md` |
| Review plan | `agent/CODE-REVIEW-PLAN-1.8.0.md` |
| Measured device | OnePlus CPH2747 (OxygenOS 16, Android 16 / SDK 36) |
| Secondary evidence | Xiaomi 24129PN74G (HyperOS 3) if available; everything else `unverified` |
| Status | Ready for owner execution after requirements/plan/test approval; device steps are owner-run |

## 1. Purpose

Walk the 1.8.0 candidate through the approved requirements and the changed surfaces. A step passes only with the exact log, screenshot, recording or measurement named here. Anything without evidence is `unverified`, not passed. The walkthrough is owner-run; the agent does not take over the phone. CPH2747 is the only measured truth for this phase.

## 2. Preconditions

1. Device booted; `adb devices` shows it; `adb shell getprop ro.build.version.sdk` returns 36 on CPH2747.
2. LSPosed is active and the module is enabled for **System UI**; SystemUI is restarted after install.
3. Install the candidate build:

   ```bash
   adb install -r build/duo-1.8.0-debug.apk
   adb shell su -c 'killall com.android.systemui'   # or the ROM's restart path
   ```

4. Clear only the app-side log buffer, never the persistent crash history:

   ```bash
   adb logcat -c
   adb logcat -s DuoSB:* > /tmp/duo-1.8-walkthrough.log
   ```

5. Record the baseline battery and display state:

   ```bash
   adb shell dumpsys battery
   adb shell wm size
   adb shell wm density
   adb shell dumpsys batterystats --reset
   ```

6. Take a baseline screenshot and save the module diagnostic dump.
7. Confirm the branch and preserved commits before testing:

   ```bash
   git rev-parse --short fix/1.7.0-review-fixes
   git rev-parse --short feat/1.8-selector-live-preview
   git log --oneline -3 fix/1.7.0-review-fixes
   ```

## 3. Static walkthrough (code, before touching the device)

Execute in this order; each step cites the file that owns the behaviour.

### S1 — Requirements and traceability

- Open `AGILE-1.8-REQUIREMENTS.md` §4, `AGILE-1.8-PLAN.md` §3 and `AGILE-1.8-TEST-PLAN.md` §5.
- Confirm every requirement has exactly one phase, one test and one evidence path.
- Confirm no orphan test, no duplicated phase ownership and no requirement without an acceptance value.
- Mark visual or device-measured rows `unverified` until the runtime section proves them.

### S2 — Selector glass and navigation

- `ui/GlassSurface.kt`, `ui/GlassControls.kt`, `ui/DuoShell.kt`: Backdrop 1.0.1 highlight tokens, disabled 24 dp shadow, one live rim owner, one fallback manual rim.
- `ui/GlassControls.kt`: radial white `0.16 * press` glow, `1.5 * height`, 8 dp inset; no vertical press gradient.
- `ui/DuoShell.kt`: hover index pure logic, #3C88F8 hover, selected white ~24 dp icon, label below, four tabs.
- `ui/DuoNavIcons.kt`: no prototype vector edits; one replaceable icon source.
- Confirm no app-authored AGSL or `RuntimeShader`.

### S3 — Battery and invert

- `hook/DuoMapping.kt`, `hook/RingGeometry.kt`: one clamped 0–100 level source for the 2 SIM bottom arc; Classic path unchanged.
- `hook/DuoCanvasView.kt`, `ui/DuoPreview.kt`: same level source in Canvas and preview.
- Both `.rml` scenes and `DuoBinder.kt`: every written property declared; `PROPERTY_COUNT` matches; invert visual has a measured seat, not a guessed value.
- `settings/DuoPrefs.kt`, `ui/DuoHomeScreen.kt`: Invert center maps to `middle_slot=percent`, no new column.
- Confirm both `duo.riv` and `duo_old.riv` are rebuilt from the current scenes.

### S4 — Home, preview and position

- `ui/DuoHomeScreen.kt`: one Live Preview Controls card; no static Live preview card; no Layout simulation card; no `PreviewSimulator` reference.
- `ui/PreviewSimulator.kt`: absent.
- `ui/DuoPreview.kt`: battery, charging, saver, Invert center, Wi-Fi/5G/4G/flashlight and other real-model controls; no per-dot/custom distribution.
- `ui/DuoSettingsComponents.kt`, `ui/DuoSettingsSections.kt`, `settings/DuoPrefs.kt`, `hook/DuoIconHost.kt`, `hook/SlotGeometry.kt`: normal Position `0 = right edge`, `-limit … 0`; `lockRing` centered mode `-limit … +limit`; one predicate; RTL physical rule documented.

### S5 — Demos, selector and copy

- `ui/DuoHomeScreen.kt`, `ui/DuoSettingPreview.kt`, `ui/DuoSettingsSections.kt`, `ui/DuoSettingsScreen.kt`: Classic card fixed to old scene; 2 SIM card fixed to new scene; event previews follow selected design and ring part in split mode.
- Design selector: exactly one selected, normalization, disabled state, `selectableGroup`, no second selector.
- `res/values/strings.xml` and the UI files: one-line labels, helper text no more than 1.5 short lines, no developer terms, placeholders preserved, report/preview hardcoded text moved.
- Confirm no literal design-option GIF exists in history and no deleted-GIF claim is present.

### S6 — Performance of changed paths

- Selector: no Backdrop/highlight loop when idle; only press/drag work.
- Hover: only icon/label state changes; no whole-bar recomposition per frame.
- Demos: fixed cards pause off-screen; event previews stop when disabled/backgrounded.
- Battery/invert: identical snapshots do not restart the Rive machine; no extra Rive surfaces.
- Position/`lockRing`: one write per change; no per-frame re-anchor.

### S7 — Process and gates

- Confirm the 449 baseline tests and new tests pass; debug and release builds produced.
- Run `tools/route-module-logs.py --check`, `tools/check-rive-eases.py`, fresh `rive rive/duo --once`, and both hash comparisons.
- Confirm no `.kilo/` or `docs/evidence/` staged; branch heads and preserved commits unchanged.
- Confirm no release, tag, push, main merge or LSPosed store action.

## 4. Runtime walkthrough

### R1 — Boot, attach and restore

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R1.1 | Restart SystemUI with the module enabled | `adb logcat -s DuoSB:*` | `Duo view ready`, no FATAL |
| R1.2 | Check the stock bar and Duo | screenshot | stock icons intact; Duo present |
| R1.3 | Disable the module and restart SystemUI | screenshot + log | stock bar restored exactly |
| R1.4 | Re-enable | log | attaches once; no duplicate views |

### R2 — Selector glass and navigation (GLASS3-01, GLASS3-02, NAV3-01, NAV3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R2.1 | Compare the selector with ColorDuo 1.1.7 in dark and light | side-by-side screenshots | measured highlight/blur/tint match; no 24 dp shadow |
| R2.2 | Inspect the live edge | screenshot/zoom | one rim owner; no double rim |
| R2.3 | Press and hold the selector | still/video | radial white 0.16×press glow; radius 1.5×height; 8 dp inset |
| R2.4 | Drag across Home → Custom → Report → Settings | video | hovered icon/label #3C88F8; smooth tracking; no lost pointer |
| R2.5 | Release on each tab | screenshots | selected icon white, ~24 dp, centred in selector; small label below; four tabs |
| R2.6 | Inspect the icon assets | APK/screenshot | prototype vectors unchanged; drop-in layer only |
| R2.7 | Rotate and large-font check | screenshots | four tabs fit; label readable; touch targets intact |

### R3 — 2 SIM battery line (BATT3-01, BATT3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R3.1 | Drive the 2 SIM design at 100, 80, 49, 19, 0 | screenshots + measured pixels | arc decreases monotonically; 100 full; 0 empty |
| R3.2 | Repeat out-of-range values | screenshots | clamped, no wrap or overshoot |
| R3.3 | Compare the real icon and the live preview at each level | side-by-side | same level, same arc |
| R3.4 | Render Classic at the same levels | screenshots | matches the 1.7 baseline; no battery-line change |
| R3.5 | Check Canvas fallback if the engine falls back | screenshot/log | same arc; no blank battery |

### R4 — Invert center (INV3-01, INV3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R4.1 | Enable Invert center in Classic | screenshot | percentage centred; middle icon above, smaller; one number |
| R4.2 | Enable Invert center in 2 SIM | screenshot | same rules; no duplicate number |
| R4.3 | Toggle charging/saver while inverted | screenshots | one number; state colours/bolt correct |
| R4.4 | Read `middle_slot`; restart app/SystemUI | settings dump + screenshot | value is `percent`; survives restart |
| R4.5 | Seed an old percent-in-network value | settings dump + screenshot | migrates to Invert center; no second mode |
| R4.6 | Inspect provider/backup schema | code/dump | no new column or version |
| R4.7 | Disable Invert center | screenshot | returns to the normal seat; no stale duplicate |

### R5 — Live preview cleanup (HOME3-01, HOME3-02)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R5.1 | Open Home | screenshot | one Live Preview Controls card; no static Live preview card |
| R5.2 | Search Home | screenshot/code | no Layout simulation card; `PreviewSimulator.kt` absent |
| R5.3 | Exercise battery, charging, saver, invert, Wi-Fi/5G/4G/flashlight | recording | preview updates; controls match the real model |
| R5.4 | Search for per-dot/custom distribution | screenshot/code | controls and strings gone |
| R5.5 | Confirm Invert persists; other controls are preview-only | settings dump | only Invert writes a setting; no fake dot state |
| R5.6 | Idle 60 s on Home | log/CPU sample | previews pause when idle/off-screen; no frame loop |

### R6 — Position and `lockRing` (POS3-01)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R6.1 | Normal mode: set Position 0 | screenshot + measured x | element at the right edge, not centre |
| R6.2 | Move to `-limit` and back | recording | negative moves left; 0 returns to right; pointer attached |
| R6.3 | Try a positive value | UI/measurement | clamps to 0 (right edge); no off-screen element |
| R6.4 | Enable Camera/Compact/`lockRing` | screenshot + measured x | centered mode; 0 = centre; range ±limit |
| R6.5 | Toggle `lockRing` off/on | UI + native screenshot | UI, prefs and native switch together; no mismatch |
| R6.6 | Repeat R6.1–R6.4 in RTL | screenshots + measured x | 0 is still the physical right edge; negative is left |
| R6.7 | Release mid-range and re-grab | recording | no disappearance, no reset-to-grab |

### R7 — Design demos and copy (DEMO3-01, DEMO3-02, COPY3-01)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R7.1 | Select Classic | recording | Classic card plays old scene; 2 SIM card still plays new scene |
| R7.2 | Select 2 SIM | recording | 2 SIM card plays new scene; Classic card still plays old scene |
| R7.3 | Play four Live Events in each design | recording | previews follow selected design; split uses ring part |
| R7.4 | Inspect design selector | screenshot/semantics | exactly one selected; disabled state clear; `selectableGroup`; no second selector |
| R7.5 | Review every screen copy | screenshots | labels one line at 360 dp; helper text no more than 1.5 short lines; no developer terms |
| R7.6 | Check placeholders and report/preview text | code/string dump | placeholders unchanged; no hardcoded report/preview text |
| R7.7 | Confirm no GIF claim | docs/evidence | runtime Rive only; no deleted-asset claim |

### R8 — Gates, regression and process (QA3-01, QA3-02, QA3-03, QA3-04)

| Step | Action | Evidence | Pass |
|---|---|---|---|
| R8.1 | Run the 449 baseline tests plus 1.8 tests | test results | 0 failures/errors/skipped |
| R8.2 | Build debug and release | build logs/APK hashes | both succeed; release is not published |
| R8.3 | Route-log gate | command output | clean |
| R8.4 | Rive ease gate and both hash comparisons | command output | clean; both `.riv` match fresh builds |
| R8.5 | Property parity both scenes | test output | binder set equals both scene sets |
| R8.6 | 1.6 P0 regression: QS rotation, shade stress, camera/lock, 100% charging | logs/video | no fatal, no safe mode |
| R8.7 | Branch/process audit | git output | `745fc5f`/`eafdcb8` and branches preserved; no `.kilo/`/evidence staged; no release/tag/push/main merge |

## 5. Evidence layout

Store under `docs/evidence/agile-1.8/device/`:

- `cph2747.txt` — device build facts, owner summary, requirement verdicts.
- `screens/` — selector, nav, battery, invert, home, position, demos, copy.
- `recordings/` — hover/drag/press, position drag, invert transitions.
- `logs/` — `DuoSB` slices, diagnostic dumps, crash buffer.
- `gates/` — unit/release/route/ease/hash outputs and property parity.
- `summary.md` — pass/fail/unverified per requirement with evidence links.

`docs/evidence/` is git-ignored. Never commit it. Never commit `.kilo/`.

## 6. Pass/fail rules

- PASS only with the named evidence and exact acceptance value.
- FAIL when the evidence contradicts the requirement.
- `unverified` when the device, recording rate, build or measurement is missing.
- A visual change without a requirement ID is a regression.
- Other ROMs stay `unverified`; do not generalize CPH2747 results.
- A performance fix is PASS only when visible output is unchanged and the redundant work is provably removed.

## 7. Rollback

- Keep the installed 1.7.0 stable APK and its SHA-256 as the rollback baseline.
- If the candidate regresses a transition, fatal guard, position or stock restore, reinstall 1.7.0, restart SystemUI, and record the failing evidence before a second attempt.
- Never leave the device half-attached: disable the module and restore the stock bar.
- No release, no main merge, no push, no LSPosed store at any point.

## 8. Exit criteria

- Every one of the 18 requirements has a verdict with evidence.
- Existing 449 tests plus the new tests pass; debug and release builds succeed.
- Both Rive scenes satisfy the property contract; both `.riv` hashes match.
- CPH2747 owner summary is complete; other ROMs are `unverified`.
- No fatal, no safe mode, no new permission, no new secret, no new settings column.
- Branch heads and `745fc5f`/`eafdcb8` preserved; no `.kilo/`/evidence committed; no release/tag/push/main merge/store.


## FILE: WALKTHROUGH-REVIEW-1.5.0.md

# Duo Status Bar 1.5.0 — walkthrough and code review

Date: 2026-10-31. Branch chain: `phase/5-liquid-glass` → `phase/6-rive` → `phase/7-multi-ui-perf` →
`phase/8-release`.

This is the walkthrough/review report requested after Phases 5–8. It is deliberately split from the
release report: `PHASE-8-REPORT.md` says whether the binary can ship; this file says what the code does,
what was checked, and what is still wrong. Fixes are sorted **critical → moderate → minor**.

## 1. Walkthrough

### Phase 5 — liquid glass shell (locked prototype)
- Start: Home/Engine/Settings use one floating bar with a three-tab selector.
- Tap a tab: center animates to the tab station; the selector is a circle there.
- Drag the selector: width grows to +42 dp, height gives 6 dp, shape stays a capsule; distance from the
  nearest station drives the stretch, so it grows and shrinks while crossing tabs.
- Release: spring to the nearest station; stretch springs to 0; the shape is a circle again.
- Glass: `GlassBackdrop` records `wallpaper` and `page` separately. Cards/controls sample wallpaper;
  the bar samples `rememberCombinedBackdrop(wallpaper, page)`; the selector combines that with the tab
  layer, so it refracts the page, pill and logos without sampling itself.
- Controls: every `SettingSwitch` and `LabelledSlider` routes through `GlassSwitch`/`GlassSlider`.
- Scroll: `ScrollEdgeGlass` masks a blurred copy with a `DstIn` gradient at the top 72 dp and bottom
  92 dp. On emulator/low quality it falls back to a static shade.
- Apple values: `GlassTokens` (40/24/12 dp blur, 1.5 saturation, 0.04 brightness, 1.0 contrast, 16/32
  lens, 28% highlight, 0/8/28 shadow at 14%) and `GlassQuality` for the emulator preview.

### Phase 6 — Rive animation system
- `ChargeIn` still flies the bolt from the middle to the ring gap.
- New `ChargeSettle` state (`0:261`) plays a 14-frame scale pulse (1 → 1.08 → 1) and then `ChargeBolt`.
  If the charger is removed mid-pulse, a 0-duration transition goes to `ChargeBolt`; its normal
  transitions then hide the bolt.
- `ChargeMotion` is the single source for the 1.6 s/1.833 s settle window, the 200 ms shake and the
  20 % crossing rule. Canvas uses it for the matching pulse; `DuoIconHost.shakeAll()` uses it for both
  renderers.
- `ChargeMotionTest` covers 19 → 20, 19 → 21, 20 → 20, 20 → 21, not charging, and a 19 → 40 jump.
- Headless evidence: `duo.png`, `charge-96.png`, `charge-110.png`.

### Phase 7 — multi-ROM and performance
- `rom-profiles.json` has 9 maps. `measured=true` only for OxygenOS and HyperOS; the other seven are
  static-only and cannot override the built-in detection.
- `RomProfilesTest` pins the count, the measured set, the unknown-Samsung fallback, and the OxygenOS
  shade map.
- `tools/check-perf.py` resolves gfxinfo columns by header, rejects empty captures, and applies
  p95/max budgets.
- Emulator run: full live glass caused an ANR; static controls/surfaces removed the ANR but still
  failed the frame budget. Details in `PHASE-7-REPORT.md`.

### Phase 8 — release
- `RELEASE-NOTES-1.5.0.md` written.
- `1.5.0-beta.20` built and relayed.
- The stable gate is **not** met: device run, performance and owner approval are open.

## 2. Code review findings

### Critical

1. **All-live glass ANR (fixed, needs device proof).**
   The emulator showed an ANR with live cards, live scroll edges and live controls. `GlassCard` now uses
   a translucent frost, `GlassQuality` turns controls/scroll/surfaces static on emulators, and the bar
   remains the one live surface on phones. The change is in `GlassSurface.kt`/`GlassControls.kt`; the
   device still must prove the phone path is fast enough.
2. **RC-017 recursive backdrop (fixed).**
   The wallpaper/page split is the structural fix. `GlassCard` no longer draws from the page layer, and
   controls inside the page sample only wallpaper. Do not "simplify" this back to one recorded layer.
3. **Emulator-only ART JIT crash (not a product crash).**
   The debug APK crashed in `art::JitCompileTask::Finalize` under the translated guest. Release beta.20
   launches and reaches Settings; this is an emulator/toolchain issue, not shipped code.

### Moderate

4. **Performance gate fails on the emulator — but the baseline fails too.**
   beta.20: 119 frames, p95 216.88 ms, max 308.39 ms. The Android Settings baseline on the same
   emulator is p95 116.87 ms, so Duo is ~1.86× the platform baseline; the emulator cannot prove the
   phone budget either way. Do not reinterpret this as a pass; CPH2747 remains the deciding run.
5. **Static ROM entries are not support.**
   Seven new maps are `measured=false`. `RomDetection` ignores them, and tests enforce that. Adding a
   device dump is the only way to promote one.
6. **Phase 6 still lacks mic/location/volume.**
   The charging and alarm/flashlight/screenshot motions exist. The other event sources and art do not;
   the report and UI must not imply they do.
7. **The selector has not been visually signed off.**
   The geometry is correct in code and the emulator screenshot shows a resting circle, but the owner
   has not dragged beta.20 on CPH2747. Treat the look as unverified.
8. **Phase 1 safe mode is still an open branch.**
   `fix/landscape-safe-mode` (`d24b448`) is unmerged and unverified. Do not include it in a release
   claim.
9. **OEM event icons remain prototypes.**
   They are not packaged, but a stable release needs owned art or an explicit owner exception.

### Minor

10. `GlassSurface` has two material paths (live and static) with duplicated tint/shadow code. They are
    intentionally kept separate for profiling; extract only after the device measurement.
11. `GlassControls` fallback switches use the Material control when no wallpaper is provided
    (previews/tests). That is intentional, but a screenshot test should pin both paths.
12. `check-perf.py` uses `FrameCompleted - IntendedVsync`, which includes idle gaps if the capture is
    taken without a deliberate scroll. Always scroll first; an idle capture is not a budget test.
13. `ChargeSettle` is an arrival pulse on the bolt, not a sweep over the green arc. The report says so;
    do not silently call it the full RIV-002 pulse.
14. The repo still contains literal `com.android.systemui` constants in AOSP fallbacks and
    `AutoExpand`; they are package identity, not adapter ids. The authority grep is clean.

## 3. Code quality observations

- **One owner:** `GlassTokens`/`GlassQuality` own material numbers; `ChargeMotion` owns charging
  timing; `RomProfiles` owns profile parsing. No duplicated magic numbers in the new code.
- **Fail-closed:** `check-perf.py` rejects empty input; `GlassCard` has a Material fallback; the
  emulator path does not throw.
- **Tests:** 322 unit tests; new tests cover the charge crossing and the shipped profile set. There is
  no UI screenshot test and no instrumented test for the selector.
- **Binary identity:** `rive/duo/build/duo.riv` and `app/src/main/res/raw/duo.riv` are byte-identical
  (`636e8c9a…`); 142/142 keyframes carry easing.
- **Logging:** route checker is clean; no `android.util.Log` outside `L.kt`.
- **Doc truth:** Phase 5 locked at beta.14; Phases 6–8 are prototypes with explicit open gates, not a
  stable release.

## 4. Evidence index

| Item | Path |
|---|---|
| Phase 5 report | `agent/PHASE-5-REPORT.md` |
| Phase 6 report | `agent/PHASE-6-REPORT.md` |
| Phase 6 headless poses | `rive/duo/build/duo.png`, `charge-96.png`, `charge-110.png` |
| Phase 7 report | `agent/PHASE-7-REPORT.md` |
| Emulator screenshots | `docs/evidence/phase7/beta18-*`, `beta20-*` |
| Emulator crash evidence | `docs/evidence/phase7/beta19b-crash.txt` |
| Phase 8 / release gate | `agent/PHASE-8-REPORT.md` |
| Release notes | `RELEASE-NOTES-1.5.0.md` |
| Walkthrough plan | `agent/PHASE-1-5-WALKTHROUGH.md` |

## 5. Next actions (ordered)

1. Install beta.20 on CPH2747; run the Phase 5/6/7 walkthrough on the device.
2. Capture a real `gfxinfo` after a deliberate Settings scroll and run `check-perf.py`.
3. If the budget fails on device, lower navigation blur/lens or keep static surfaces before release.
4. Promote a static ROM map only after its runtime dump; otherwise leave it `UNVERIFIED`.
5. Decide on OEM art and the Phase 1 safe-mode branch before any stable release.
6. Owner approval, then `1.5.0-rc.1`—not a store release.


## FILE: WALKTHROUGH-REVIEW-PHASES-1-8.md

# Walkthrough & review — Phases 1–8

Date: 2026-10-31. This review matches the phase plan against the current branch and evidence. It does not
promote unverified vendor claims.

## Phase status

| Phase | State | Evidence / gap |
|---|---|---|
| 1 shade/QS ownership | partial | measured OnePlus/HyperOS paths; no fresh multi-ROM dump; safe-mode branch still unmerged |
| 2 renderer/Canvas parity | partial | Rive + Canvas shapes exist; full per-state side-by-side matrix not captured |
| 3 event prototype | done prototype | alarm/flashlight/screenshot; screenshot now has a working file source |
| 4 cell accuracy | prototype | mapping/logs/report; dual-SIM device run still the gate |
| 5 liquid glass | **locked** | beta.14; selector, controls, scroll, tokens; no further retune |
| 6 Rive motion | partial | charge settle + 20% shake + lock/call art and hooks; mic/location/volume missing |
| 7 multi-UI/perf | partial | 9 static profiles, perf tool; emulator p95 fails, device pending |
| 8 release | prepared | beta.36 built/installed; stable release blocked on device/perf/owner |

## New emulator evidence

### Lock → home: PASS
- Module enabled in LSPosed, scope `com.android.systemui`, `duo_statusbar_stage=2`.
- `keyguard unlock hook installed`
- `keyguard faded away -> unlock fallback`
- `unlock received enabled=true active=null`
- `live event -> UNLOCK`

This is the transition from the lock screen to home, not an unlock API call.

### Incoming call: PASS
- `adb emu gsm call 12345`
- `phone state=RINGING callEnabled=true`
- `live event -> CALL`

### Screenshot: PASS (phone)
- `KEYCODE_SYSRQ`
- `live event -> SCREENSHOT source=file:/storage/emulated/0/Pictures/Screenshots/...`

## What was fixed in this round

- Screenshot source: shared `ScreenshotWatcher` across public screenshot dirs; no vendor-only hook.
- Keyguard: `StatusBarKeyguardViewManager` hook, verified on the emulator.
- Call: `CALL` event, phone-state receiver, Rive art/state, Canvas clone, settings switch.
- Lock/call art: owned drawings, dynamic Duo color.
- Flashlight: size/glow/timing iterations; layered glow (not true gradient).
- Alarm: owned clock with bells/hands/legs.
- Screenshot art: brackets + camera + lens; Canvas duplicate branch removed.

## Requirements review

### Done / verified
- UI-013 icon extraction (phone + 8 UIs, 5,353 files) with manifest and candidate list.
- Screenshot source (file watcher), lock transition, incoming-call event on the emulator.
- RIV-003 20% shake, ChargeSettle, alarm/flashlight/screenshot art.
- `check-perf.py`, profile asset checker, log route, Rive easing.

### Partial
- UI-001/002/016: six AOSP-derived screenshot classes; only two measured ROM maps.
- UI-014: Canvas parity for new lock/call art exists, but no full state screenshot matrix.
- CUS-005 icon packs: extracted assets, no picker.
- RIV-002: bolt flight + settle pulse, not the full green-arc 0→level sweep.
- RIV-005: parity implemented, not visually compared on device.
- Font/per-UI runtime, CUS-004 layout options, CUS-008 help previews: missing.
- PERF: emulator p95 216.88 ms vs 32 ms budget; baseline also fails (116.87 ms). Device run required.
- LIVE-001/002: mic/location/volume sources still absent.
- Phase 1 safe-mode: unmerged.

### Release gate
- beta.36 installed on CPH2747; no stable release.
- Owner approval and device visual/perf run remain.

## Fix severity

### Critical
1. RC-017 recursive backdrop — structural fix (wallpaper/page split).
2. All-live glass ANR — adaptive quality; no ANR in later build.
3. Screenshot was not firing on OxygenOS — now file watcher proves the event path.

### Moderate
1. Rive draw order hid the toggle/head line — body must be last in this scene.
2. Keyguard broadcast didn't arrive — replaced with measured Keyguard hook.
3. Call source was untested — now passes with emulator GSM.
4. Emulator frame budget fails; device unknown.
5. Seven ROM maps static-only.
6. Mic/location/volume sources missing.
7. Flashlight glow is layered, not a true gradient/blur.

### Minor
1. `gfxinfo` header parsing was wrong; fixed.
2. Rive tool path typo; fixed.
3. Many size/timing iterations; logged in `ICON-MISTAKES.md`.

## Conclusion

The lock/call/screenshot event paths are now verified on the emulator/phone. The release is still not
approved: other-vendor runtime checks, mic/location/volume, true glow gradient, performance and owner
visual sign-off remain.

