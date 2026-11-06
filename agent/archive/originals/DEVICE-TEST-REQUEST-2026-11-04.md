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

