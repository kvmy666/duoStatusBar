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
