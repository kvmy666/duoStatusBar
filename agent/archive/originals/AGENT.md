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
