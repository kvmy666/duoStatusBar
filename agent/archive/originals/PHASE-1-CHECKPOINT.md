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
