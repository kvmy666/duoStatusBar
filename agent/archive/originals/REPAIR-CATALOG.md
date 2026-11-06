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
