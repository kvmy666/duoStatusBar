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
