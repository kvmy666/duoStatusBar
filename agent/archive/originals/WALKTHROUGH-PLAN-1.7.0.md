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
