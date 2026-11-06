# Multi-UI Update: Brainstorm / Discovery Notes

Date: 2026-10-26
Goal: Define the next major update precisely before writing requirements, plan, or tests.
Status: Discovery open. No PLAN-v2, REQUIREMENTS, or TEST-PLAN will be written until the owner confirms the scope.
Note: The owner dictated the brief in Arabic; this file is the English working translation so the CLI and agents can process it safely.

## Summary / key decisions

- The update is **not defined yet**. The owner explicitly stopped plan/requirements writing until discovery is done.
- Known inputs from the owner:
  - 8 vendor UIs are in scope, including OnePlus.
  - Work must be parallelizable per UI.
  - Each UI has different hooks/methods; nothing may be guessed.
  - Vendor UI artifacts are for local analysis only; proprietary APKs must never enter the GPL repo.
  - Test targets: a rooted emulator (AVDs exist) plus the owner's phone.
  - Quality bar: clean one-shot code, performance, smoothness, battery.
  - Use cheap agents for cheap tasks; strong model only for architecture/code/debug.
  - Requirements must be classified (feature / bug / refactor / performance / security / test / docs).
  - Each phase gets its own branch.
  - All files and file contents must be in English.

## Owner brief (2026-10-26) — English working translation

### 1. Duo in the shade and Quick Settings (first feature)

- Today the shade and Quick Settings show the normal stock battery.
- Duo must appear and behave the same in all places, including the lock screen.
- This phase must contain zero guessing.
- Do not patch or modify system code in a way that breaks or forces behaviour.
- Acceptance is same behaviour everywhere, not a special case per surface.

### 2. Testing layer (a full layer, not a small phase)

- Write tests for code that currently has no tests.
- Tests must pass on every supported UI with the same result.
- All new code must ship with unit tests and path tests.
- Every function must log and surface failure, so a user report can identify the failing class/function/hook.
- An error-reporting platform is planned: probably a Telegram group, possibly in addition to the current channel.
- Logs are critical. If possible, read the Windows desktop folder named "Duo logs" (Arabic name) from Ubuntu and copy it; it contains user-device logs that will improve the code and parallel behaviour.

### 3. App UI redesign + full Duo customization

- First: full customization of the Duo icon, plus new features.
- Two engines: Rive and Canvas. The user chooses; there is also Auto/Recommended.
- If a device fails at Rive, it must switch automatically to Canvas.
- After choosing the engine, the user can customize the full design:
  - Control what appears in the middle, not only Wi-Fi/5G. It can be the clock or the charging number.
  - Cell dots can take the battery position: four dots right and four left for dual SIM, with the battery as a bar where the cell dots used to be.
  - Wi-Fi can be placed where the charging number was, etc.
  - These are initial details; the customization feature will be much larger and needs deeper discovery.
- Duo is not perfect for everyone today:
  - Sometimes Duo is present but the stock battery is also visible.
  - Sometimes a user wants the silent icon visible, not hidden.
  - Therefore there must be a control section; do not force-hide every icon.
- Live interaction with the Wi-Fi circle:
  - Example: an alarm is set and rings; Wi-Fi fades out, the alarm icon appears, with a Rive animation.
  - Live icons to support: alarm, unlock, charging (already exists), screenshot, volume up/down, location use, microphone use (already exists in the system sometimes as a green button).
  - Each icon needs its own logic and Rive animation, e.g. alarm bounces/shakes.
- Customization can change the shape of any icon.
- Pull status-bar icons and the other listed icons from every UI; let the user choose, e.g. current Wi-Fi icon = Apple icon, OnePlus Wi-Fi icon, etc.
- Do not redesign the icons; extract/pull them.
- The engine itself needs a simulation:
  - Every toggle has a small static GIF-like preview next to it showing what it does.
  - A question mark next to the toggle plays a simulation of the toggle.
  - In customization, a box containing Duo updates live when the Wi-Fi shape/behaviour changes, so the user can compare.
- Assume the user is easily confused ("treat the user as an asshole") and make customization extremely easy.
- Offer 2-3 ready-made designs plus a "plus customization" path.

### 4. Cell accuracy

- Mapping exists between phone bars and our design (phone 4-5 bars, ours 3-4).
- Problem: phone shows full bars, SIM1 is selected, but our design shows 2-3 dots instead of full.
- The shade shows full. Improve accuracy and test it.

### 5. Liquid glass + three-page app

- Use the library from developer kyant0 named `androidliquidglass` on GitHub to make the app liquid glass.
- Bottom navigation: Home, Engine, Settings — three pages instead of one long page.

### 6. Rive animation phase

- The owner does not have Rive on Ubuntu yet; will install it. Believes animations are CLI-based, not GUI.
- We build the animation and improve the animation engine.
- Charging example: a very subtle, almost invisible pulse travels from 0 to the current level, like a transparent colour over the green; when it reaches the level, it pulses.
- At 20% charging, the Duo icon shakes.
- Rive work needs human/logical/emotional thinking, not robotic programming. Search for animations that create joy and add luxury; ask several questions when building an animation.
- Current animations are satisfactory; the new ones must improve them. Anything less is rejected.

### 7. Parallelism

- Duo must work on all devices with the same efficiency. Parallel, parallel, parallel.

### 8. Process

- Brainstorm in batches of five questions.
- Each question gets one suggested answer.
- No multiple-choice UI; open answers only.
- After brainstorming, convert everything into requirements with a checklist; add new requirements as they are observed.
- After requirements, convert to an implementation plan.
- Then write the functions to add, how they work, and how to test them.
- Acceptance is when they work on all UIs.
- Always use the tools; never guess; always produce evidence.
- Every function must log failures with the failing class/function/hook.
- The reporting phase exists and is decent, but it will be improved.

## Code evidence — 2026-10-26 (shade / QS / keyguard)

Source: read-only `explore-fast` pass; step cap reached, so the parked Phase 8 note in `docs/plan.md` was not re-read.

| Surface | Injects today? | Code evidence | Gap |
|---|---|---|---|
| Status bar (home/in-app) | Yes | `DuoIconHost.attach(statusBarRoot)`; `ContainerFinder.findStatusIconsHost`; `DuoIconHost.kt` ~1104-1196; `ContainerFinder.kt` 48-85, 197-216 | Standard path; overlay/combined-status special cases exist |
| Keyguard status bar | Yes, extra bar | `DuoHook.kt` 603-616; `DuoIconHost.kt` 267-295; uses id `system_icons` in `TYPE_NOTIFICATION_SHADE` | Only after the main bar attaches; depends on shade window capture |
| Shade header (pulled-down QS header) | Yes, handed over by controller | `DuoHook.hookShadeHeader` at `DuoHook.kt` 695-730; `DuoIconHost.attachShadeHeader` at `DuoIconHost.kt` 304-332; `ContainerFinder.findShadeIconsArea` at `ContainerFinder.kt` 312-319 | Header can be 0x0 at attach; retried after layout; `combined_qs_header` parked issue still NOT VERIFIED |
| QS panel / tiles | No separate injection found | Only header area handled | QS panel/grid not handled |

Existing tests (names only; exact coverage not fully verified):
`ContainerFinderTest`, `StockIconHiderTest`, `ShadeRestorationTest`, `LockFrameTest`, `DuoGuardTest`, `BarTintTest`, `RomDetectionTest`, `RomProfilesTest`.

Likely gaps: no direct tests for `DuoHook` window capture, keyguard/shade attach orchestration, `ShadeHeaderController` handover, or QS panel injection (none exists in code).

## Environment evidence — Windows Duo logs

- Windows OS partition: `/dev/nvme0n1p3`, NTFS, label `OS`, UUID `163250D83250BF05`, ~843 GiB.
- It is **not mounted**. `/mnt/c` exists but is empty; `/media/abdulkarim` is empty.
- `ntfs-3g` 2022.10.3 is installed. No BitLocker indicator was found in available metadata (not fully verified).
- Read-only mount command (owner or root):
  `sudo ntfs-3g -o ro /dev/nvme0n1p3 /mnt/c`
  or `sudo mount -t ntfs3 -o ro /dev/nvme0n1p3 /mnt/c`.
- After mounting, search:
  `find /mnt/c -maxdepth 4 -type d \( -iname 'Duo*Logs*' -o -iname 'duo*log*' -o -iname '*لوقز*' \) -print`
- Non-interactive sudo is unavailable (`sudo -n true` -> password required). The mount was **not** attempted and no files were read.
- Owner action: run one of these in a terminal, then tell the agent it is mounted:
  `sudo ntfs-3g -o ro /dev/nvme0n1p3 /mnt/c`
  or `sudo mount -t ntfs3 -o ro /dev/nvme0n1p3 /mnt/c`.
- Candidate folder: UNKNOWN until the partition is mounted.

## Research evidence — libraries and tools

### Liquid glass
- Repo: `https://github.com/kyant0/androidliquidglass` (branch `kmp`), Apache-2.0.
- Latest observed: 2.0.1 (release date shown as 26 Aug; year not explicit), 4.2k stars, 341 forks, 41 open issues.
- Requires Jetpack Compose / Compose Multiplatform; Compose 1.12.0; minSdk 21; Kotlin stdlib 2.4.10; depends on `io.github.kyant0:shapes` 1.2.1.
- Gradle: `implementation("io.github.kyant0:backdrop:2.0.1")` from Maven Central.
- API: `Modifier.drawBackdrop` / `Modifier.drawPlainBackdrop`, `Backdrop`, `BackdropEffectScope`; no high-level components; examples are LiquidButton/Toggle/Slider/BottomTabs.
- Limits: `RenderEffect` blur only on Android 12+ (API 31+); lens/runtime shader on Android 13+ (API 33+); unsupported effects are skipped. Blur radius >~150 px is heavy.
- Alternative: Haze (`dev.chrisbanes.haze:haze`, Apache-2.0) or platform RenderEffect blur.
- Status: no explicit stable/beta/experimental label in README.

### Rive
- CLI installed: 1.8.0 at `[redacted]/.rive/bin/rive` (not on PATH; add `~/.rive/bin`).
- CLI can author scenes as text via Rive Markup Language (RML) and compile to `.riv`; it is not the graphical Editor. Docs: `https://rive.app/docs/runtimes/advanced-topic/rml`.
- Headless commands/flags: `rive create`, `rive <dir>`, `--verify`, `--once`, `--test`, `inspect`, `--screenshot`, `--viewport=WxH`, `--bench=N`, `--data=`, `--advance=`, `--data-dump`, `--format=json|human`, `--optimize`.
- Rive Android runtime: `app.rive:rive-android:11.15.0`, MIT, minSdk 21, ABIs `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`; approx. 2.4 MB download / 7 MB install per arm64 slice; multi-arch AAR ~12 MB.
- Docs: state machines `https://rive.app/docs/runtimes/android/state-machines`; data binding `https://rive.app/docs/runtimes/android/data-binding`.

## Shade header failure — prior evidence (read-only)

Reconstructed from git and docs; no fixes proposed.

Commits:
- `65acc1c` (2026-09-21): "Phase 3: decode the shade header instead of guessing at it". Decoded `OplusQSModuleEx.providesShadeHeaderView`: `ViewStub qs_header_stub` -> `R.layout.combined_qs_header`. Injection/hiding target looked right, but the ROM repopulates `statusIcons` after the hide pass; the element was sized against a 0x0 container (164 px fallback). Kept both hand-over paths (`ShadeHeaderController` constructor + `ViewStub` hook) and a tree dump.
- `7c79f44` (2026-09-21): "Phase 3: the element on the lock screen". Three bars: `status_bar/system_icons`, `keyguard_status_bar/system_icons`, `combined_qs_header/shade_header_system_icons`. Shade header "injected; icons remain"; it was not in the shade window tree at all; taken from the `ShadeHeaderController` constructor and injected into the icon area; stock icons still showing, so the hidden container was not the one drawing them.
- `3ed8f97` (2026-09-21): "hook the icon container as icons arrive (does not fire - recorded)". Hooked `StatusIconContainer.addView`; it does not fire (no icon added through that method). Carrier group's `mobile_combo` views sit outside `#icons`, not covered.
- `0d038f0` (2026-10-10): "restore extra-bar stock icons independently". Each extra bar owns `StockIconHider`; `attachShadeHeader` re-resolves the icon area and releases the old slot on reinflation.
- `07494e2` (2026-10-10): multi-bar consistency; shade header path wrapped so failures cannot surface in SystemUI.

Code surface:
- `DuoHook.kt`: `hookShadeHeader()` hooks both the `ShadeHeaderController` constructor and `ViewStub.inflate`; `hookStatusIconContainer()` hooks `StatusIconContainer`/`MiuiStatusIconContainer.addView`; `attachExtraBars()` calls `host.attachShadeHeader(...)`.
- `DuoIconHost.kt`: `attachShadeHeader()` -> `finder.findShadeIconsArea(header)`; re-resolves/releases the prior slot; `attachExtraView(...)` -> `attachInto(...)` injects the element and hides stock on ready.
- `ContainerFinder.kt`: `findShadeIconsArea(header)` tries `shade_header_system_icons`, else the parent of `statusIcons`.
- `Diag.kt`: probes `shade_header_system_icons`.

Symptoms:
- The shade header id exists, but "view not in this window" at probe time.
- Icons return after the hide pass because the ROM repopulates `statusIcons`.
- `addView` hook does not fire.
- Element sized against a 0x0 container (164 px fallback).
- Carrier-group `mobile_combo` views outside `#icons` are not covered.

Evidence paths:
- `docs/plan.md` Phase 8 (parked; needs a measured shade tree).
- `docs/evidence/phase0-oos16.txt`, `docs/rom-contract.md`, `docs/shade-restoration-fix.md`.
- `diagnose from users/duo-diagnostics-20260927-024722.txt`, `...-181934.txt` (header id exists, view not in window).

Unknowns:
- Exact route by which the ROM adds shade-header icons (not `StatusIconContainer.addView`).
- Correct container to hide (likely not the one currently hidden; carrier group outside `#icons`).
- Whether inflation timing/reinflation is fully handled.
- Exact 0x0 sizing fix beyond the fallback width.
- No measured QS header view tree captured beyond class names.

## Q&A log

### Q1 - What is the primary goal of the big update?
- Asked: Choose the closest goal.
- Captured (owner): will provide requirements first, then brainstorm.
- Meaning: do not infer scope or write PLAN-v2 / REQUIREMENTS / TEST-PLAN before that.

### Q2 - Duo in the shade / QS: behaviour and boundary
- Captured (owner): Duo should behave like the battery and the status-bar icons. Replace the battery wherever the status bar of the shade and QS shows it; wherever the battery appears, Duo appears instead.
- The owner does **not** want a forced removal or a behaviour change (e.g. forcibly removing the battery). The owner wants a hook on the battery view and a change of its shape/appearance.
- Implication: this is not the current "hide stock + inject a separate Duo view" model. Phase 1 must decide how to hook the existing battery host and draw Duo in its place, then restore exactly.

### Q3 - Supported UIs and acceptance
- Captured (owner): yes, identical behaviour. When the update ships, testers test it. Logging is very important so we know which UI failed and what failed, in order to fix it.

### Q4 - Customization v1
- Captured (owner): the suggested initial scope is good. Build it as a prototype: flexible and improvable.

### Q5 - OEM icons
- Captured (owner): extract the OEM icons as-is so we can see how the engine handles them and run the engine on them; at the official launch, remove them.
- Implication: local/prototype use only; release builds must not ship OEM assets. The exact beta-vs-release boundary is still open.

### Q6 - Shade header history
- Captured (owner): the shade header was attempted before and failed; we will try again now.
- Action: reconstruct the old failure from git/docs/logs before proposing a new approach; do not repeat the same hook blindly.

### Architectural tension to resolve before Phase 1
- Current code: injects a Duo view into the strip and hides the stock icons.
- New direction: hook the existing battery view and change its shape, without forcing/removing it.
- This must be resolved with evidence per UI (what the battery host actually is, which draw method can be intercepted, and how to restore it).

### Q7 - Battery host hook: accepted
- Captured (owner): yes. Keep the original battery view, intercept its drawing, draw Duo inside its bounds, restore on disable. No removal and no separate overlay.

### Q8 - Surfaces: accepted
- Captured (owner): yes. Status bar, keyguard, shade header, QS header; QS panel/tile only if it hosts the battery separately. AOD and the Settings app are out of scope.

### Q9 - Size and position
- Captured (owner): Duo's size is flexible right/left/top/bottom within the status bar, with zoom in/out.
- Implication: this is more than "match the host height". The user wants free position and scale inside the host bounds. Per-surface bounds still apply.

### Q10 - Unknown battery host fallback
- Captured (owner): leave the stock battery as-is; write a log explaining why the host was not recognized and how the code tried to recognize it, so it can be fixed.
- Implication: the resolver needs a structured trace (candidate ids/classes, tree path, bounds, hook attempts, failure reason), not just a generic "not found".

### Q11 - Shade header failure memory
- Captured (owner): does not remember details; all attempts failed. Believes the new tools make it fixable.
- Evidence already reconstructed above; the retry must start with a diagnostic probe, not the old hook.

### Q12 - The 8 UI list: confirmed
- Captured (owner): yes. OnePlus OxygenOS, OPPO/realme ColorOS, Xiaomi HyperOS, Samsung One UI, vivo OriginOS, Honor MagicOS, Motorola MyOS, Nothing OS, with AOSP as the baseline. A new UI stays `unverified` until measured.

### Q13 - Size/position scope: confirmed
- Captured (owner): yes. The same drag/zoom model applies to every surface, clipped to that surface's bounds; the app preview shows each surface; a per-surface override only when the host is smaller.

### Q14 - Failure trace + repair catalog
- Captured (owner): yes, with the trace. The owner wants a repair-code catalog, like an automotive repair manual: the log tells the agent the failure cause, how to fix it, and which tools to use.
- Deliverable: `agent/REPAIR-CATALOG.md` (English, living document). Every new log adds an entry. The catalog must map log signature -> cause -> evidence -> tool -> fix -> verification, and never guess.
- The catalog must also teach the agent how to read the diagnostic dump and how to confirm the exact log string in the code before using a signature.

### Q15 - Test target order
- Captured (owner): the owner's phone only for now. Write the code for all devices, then verify on the emulator later.
- Implication: CPH2747 is the first real target; the emulator is a later verification lane, not a blocker for the first code.

### Still open
- OEM icon build boundary (local debug only vs internal beta vs public beta) -> owner
- Phone connection method/port -> owner
- Live-event icon priority for v1 -> owner
- Log line schema/fields -> owner/agent

### Q16 - OEM icon removal boundary
- Captured (owner): the owner will remove the OEM icons at the right time; no fixed date; it is his responsibility and he will not break the law.
- Implication: document the risk and keep the assets local/prototype; do not ship them in an official release. No build-time ban was requested.

### Q17 - Phone connection
- Captured (owner): wireless debugging on port 6666; the address ends with 245; derive it from the connected addresses. Start with the shade fix.

### Q18 - Log schema
- Captured (owner): yes, the proposed structured line (`ui/surface/stage/class/id/hook/result/reason/detail/build`) is accepted.

### Q19 - Live event icons
- Captured (owner): yes, the suggested priority is accepted: P0 = charging (exists), alarm, screenshot, mic, location; P1 = volume, unlock.

### Q20 - First code target
- Captured (owner): start by fixing the shade problem.

## Device baseline — CPH2747 (2026-10-26)

- Connection: `adb connect [redacted]` succeeded; serial `[redacted]`.
- Device: OnePlus CPH2747, Android 16, SDK 36.
- Fingerprint: `OnePlus/CPH2747/OP611FL1:16/BP2A.250605.015/B.R4T3.16cae21_dc209e_dc22f5:user/release-keys`.
- Build display: `CPH2747_16.0.9.400(EX01)`.
- Module: `io.github.kvmy666.duostatusbar`, versionName `1.4.3-beta.1`, versionCode `24`.
- `duo_statusbar_stage` = `null` (unset; not a confirmed stage).
- Shade expanded; `uiautomator dump` pulled to `/tmp/duo-shade.xml`.
- The first 40 matching nodes were notification rows; no battery/QS/clock nodes visible there. The XML has one huge line; grep fails on line length. Parse with Python.
- Still missing: screenshot, LSPosed log, full shade/QS tree, shade collapse.

## Measured QS header tree — CPH2747 (2026-10-26)

Source: `uiautomator dump` of the expanded shade (`/tmp/duo-shade.xml`), screenshot `/tmp/duo-shade.png` (1272x2772). Read-only; shade collapsed after capture.

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

Other ids present: `mobile_combo`, `mobile_combo_real`, `mobile_group`, `mobile_signal`, `wifi_combo`, `wifi_group`, `wifi_inout`, `wifi_left`, `wifi_signal`.

**Why the old hook failed:** the real QS icon cluster is `quick_qs_status_icons`; the code looked for `shade_header_system_icons`, and `statusIcons` here is a plain `LinearLayout`, not `StatusIconContainer`. The old `StatusIconContainer.addView` hook therefore had no target. The real battery host is `batteryRemainingIcon` / `battery_icon_view`.

**Correction (2026-10-27):** the module is active in SystemUI — `DuoSB` lines appear under `(com.android.systemui)` pid 10266 (`reveal fired`, `Rive renderer paused`). The `out of scope` line was the app process only. The shade header still shows the stock battery (screenshot 2026-10-27 01:53), so the missing piece is the header attach/resolve path, not scope. `duo_statusbar_stage` is `null` (no adb override).

### Q21 - Document gate: confirmed
- Captured (owner): yes. Brainstorm -> REQUIREMENTS -> TEST-PLAN -> PLAN-v2 -> owner approval on each -> then code. No code before the documents are complete.

### Q22 - Stability and code style
- Captured (owner): do not introduce new things; the app is stable now; follow the old code paths.
- Implication: additive changes in the existing `DuoIconHost` / `ContainerFinder` / `RomAdapter` style. No new core architecture or hook framework. New dependencies need a written reason and approval.

### Q23 - App content and backup/import
- Captured (owner): the agent decides the content. Settings must include **backup and import**: backup exports settings, preferences, and customization; import restores them.
- Implication: versioned backup format, validation, confirmation before overwrite, no secrets.

### Q24 - Logs and support
- Captured (owner): logs arrive, but communication with the user who sent the log is sometimes difficult. He is considering a **technical support channel, English only**.
- Open: platform (Telegram group/channel vs Discord), scope (support only vs beta/reports), who answers.

### Q25 - Support matrix
- Still open: Android versions, ABIs, app UI language (English only vs English + Arabic).

### Q26 - Language
- Captured (owner): English only. App UI and support channel are English only.

### Q27 - Support channel
- Captured (owner): Telegram, to be created later.

### Q28 - Backup scope
- Captured (owner): everything can be backed up.

### Q29 - Dependency boundary
- Captured (owner): agreed. No new core architecture/hook framework; reuse the existing code paths. App-only UI libraries are allowed; a new core dependency needs a written reason and approval.

### Q30 - Content and UX
- Captured (owner): the agent evaluates the content and puts it in the right place; new sections may be added later. Treat the user as easily confused ("asshole"), make the app logical and understandable, reduce text/explanation inside the app, and rely more on visuals.

### Q31 - Path testing
- Captured (owner): yes. Unit + path coverage on changed code; on-device smoke per UI.

### Q32 - Logging
- Captured (owner): everything that helps repair, no matter how small. Keep it useful, bounded, and redacted; no PII.

### Q33 - Rive auto-fallback and Canvas parity
- Captured (owner): yes. If Rive fails, switch automatically to Canvas; this is the current behaviour. **Every Rive design must have a Canvas clone.**

### Q34 - Live event sources
- Captured (owner): yes. SystemUI hooks first; UsageStats/NotificationListener only with explicit consent; Accessibility only if no alternative.

### Q35 - Animation style
- Captured (owner): yes. Quiet luxury: short, purposeful motion, no idle loops; charging pulse 1.2 s, 20% shake 200 ms, alarm bounce; Canvas matches the same logic.

### Q36 - QS and shade
- Captured (owner): same as the status bar. The QS lives in the shade; the owner expects that verifying one verifies the other. Still capture both surfaces as evidence.

### Q37 - Customization v1
- Captured (owner): yes, as a prototype.

### Q38 - Backup format
- Captured (owner): no encryption, no passwords. A simple JSON file; it contains no sensitive information.

### Q39 - Cell mapping
- Captured (owner): accuracy is based on our four dots; map them to match the system bars. Dual-SIM and 5G NSA are in scope.

### Q40 - Performance numbers
- Captured (owner): the numbers are good. He asked whether the battery number was included.
- Added: 8h idle <= stock +1% and <=5 mAh, no wake locks; 1h active comparison to stock.

### Process correction - model routing
- Captured (owner): never use GLM. Use the free BEAM model for non-sensitive work; reserve `deepseek-v4.1-flash` for architecture/code/debug/critical review.

### Process correction - naming and language
- Captured (owner): all file and folder names in English; all file contents in English because the CLI has problems with Arabic.
- Rule: use `weapons/` and `agent/`; Arabic only in chat.

## Open flags (pending input)

- Exact surfaces covered by "shade and QS" (notification shade header vs QS header vs tiles vs AOD) -> owner
- Exact technical boundary of "no patching/forcing" -> owner
- Definition of "supported UI" and acceptance evidence per UI -> owner
- Customization v1 scope vs later phases -> owner
- OEM icon licensing/distribution approach -> owner
- Access to each OEM device/dump -> owner
- Which OEM ideas to borrow -> owner
- Root emulator choice (`ae_api34_root` vs `ae_api36_root`) -> owner/agent
- Location/mount of the Windows "Duo logs" folder -> owner/agent
- Priority of the live-event icons for v1 -> owner
