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
