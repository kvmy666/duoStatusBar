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
