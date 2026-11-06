# AGILE 1.8 — 1.8.0 alpha notes

## Alpha.8 status

- **1.8.0-alpha.8 installed on CPH2747 at 13:50:03.**
- The APK was delivered to the owner through Telegram (message 45).
- **Owner device verification is pending.** No agent device visual test was run.
- **System UI / phone restart is required after the module update** before testing.

## Version

- `versionCode = 97`, `versionName = "1.8.0-alpha.8"` (`app/build.gradle.kts:47-48`).
- APK: `app/build/outputs/apk/release/app-release.apk`.
- Size: **7,511,605 bytes**.
- SHA-256: `28b19f230d705baed4b55faac6616e15319bc1c53578034706f29999a8a1c833`.

## Changes since alpha.7

- **Idle layering:** while idle the tab row stays above the selector; the selector keeps the frosted
  surface with no blur, so icons and labels remain sharp.
- **Moving layering:** while moving the selector is drawn above the tab row and samples
  `selectorMovingBackdrop = page + pill + row`, restoring the lens and chromatic aberration during
  the drag.
- **No blur added:** the selector still has no blur in either state.
- **Shade seat:** remains the v89 battery anchor from alpha.7 (owner confirmed fixed).

## Gates

- **482 unit tests**, 0 failures, 0 errors, 0 skipped.
- Release build passes.
- Route-module log check clean (`route-module-logs.py --check`: 0 files to fix,
  0 log calls left for review).
- Both Rive scenes report **0 inert** easing keyframes (`check-rive-eases.py`:
  158/158 keyframes carry a curve).
- `app/src/main/res/raw/duo.riv` SHA-256 (new design):
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`.
- `app/src/main/res/raw/duo_old.riv` SHA-256 (old design):
  `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

## Remaining unverified

- **Movement lens:** confirm the lens and chromatic aberration are visible while dragging, with the
  selector drawn above the row.
- **Stash recovery:** `stash@{0}` is retained and must not be dropped.
- Other ROMs remain unverified.

## Alpha.7 (historical)

- `versionCode = 96`, `versionName = "1.8.0-alpha.7"`; installed on CPH2747 at 13:34:13 and
  delivered through Telegram message 44.
- APK size: 7,510,533 bytes; SHA-256:
  `96892fcced3aad0e4066f5b7ac86ce3c529a8d4224745ace4bdd53538585c068`.
- Pill owns all blur; selector below the row with no blur; frosted surface white 0.12 (dark) /
  black 0.06 (light) fading with press; inner/drop shadow and always-on highlight; blue
  selected/hovered icon; shade seat restored to the v89 battery anchor; `H:mm`/`h:mm` clock.
- 482 unit tests; Rive 158/158; `duo.riv` SHA-256:
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`; `duo_old.riv`
  SHA-256: `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

## Alpha.5 status (historical)

### Version

- `versionCode = 94`, `versionName = "1.8.0-alpha.5"` (`app/build.gradle.kts:47-48`).
- APK: `app/build/outputs/apk/release/app-release.apk`.
- Size: **7,510,057 bytes**.
- SHA-256: `1bc96ca2d646caa5f36ad3248ab5dea464a02e694124f64156bfe510d0c5af86`.
- The APK was delivered to the owner through Telegram (message 43); direct install was pending
  because the phone was offline.

### Changes since alpha.4

- **Shade header seat:** the shade header returns to the pre-Phase-1 `place()` seat
  (`translationY = ringAnchorShiftY`, X = flow nudge), with no battery anchor and no
  shade-only clamp. QS and keyguard placement are unchanged.
- **Custom page:** the Default/Compact/Custom preset chips and the Compact resize shortcut are
  removed; the current settings become the Custom baseline.
- **Selector colours:** the selected/hovered tab icon is blue (`DuoColors.NavAccent`); the other
  tabs are white. The idle selector has no tint — the static rim only — and the lens/highlight
  remain movement-only (`dragging || press > 0.01`).

### Gates

- **482 unit tests**, 0 failures, 0 errors, 0 skipped.
- Release build passes.
- Route-module log check clean (`route-module-logs.py --check`: 0 files to fix,
  0 log calls left for review).
- Both Rive scenes report **0 inert** easing keyframes (`check-rive-eases.py`:
  158/158 keyframes carry a curve).
- `app/src/main/res/raw/duo.riv` SHA-256 (new design):
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`.
- `app/src/main/res/raw/duo_old.riv` SHA-256 (old design):
  `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

### Remaining unverified

- **Device install:** the phone was offline (`No route to host`), so alpha.5 was not installed
  directly; installation and the visual pass remain the owner's.
- **Shade seat:** confirm the pre-Phase-1 `place()` seat on the home bar, then in the shade/QS
  header, with no battery anchor and no clamp.
- **Selector:** confirm the selected/hovered icon is blue and the other tabs white; while idle,
  confirm the static rim only (no tint), and that the lens/highlight appear only while
  dragging/pressing.
- **Custom page:** confirm the preset chips and the Compact resize shortcut are gone, and that
  the current settings are used as the Custom baseline.
- **Stash recovery:** the working tree was recovered from `stash@{0}` without `git reset` or
  `git stash pop`; **`stash@{0}` is retained until device verification** and must not be dropped
  yet.
- Other ROMs remain unverified.

## Alpha.4 (historical)

- `versionCode = 93`, `versionName = "1.8.0-alpha.4"`; delivered through Telegram message 42.
- APK size: 7,519,569 bytes; SHA-256:
  `6b3dc094695ed3f28bddcdf4daf0d4407cd5e5d4d1f0f64a10ac97a259a8e9cf`.
- v89 extras placement; static selector rim with movement-only lens/highlight; invert charging
  bolt at ring centre in both designs and the Canvas fallback; hour-only clock; unsnapped
  Wi-Fi/cell/5G sliders; Classic card background `#101014`.
- 482 unit tests; Rive 158/158; `duo.riv` SHA-256:
  `fa32b3f91ae1748bd8d56c6b79b7193e9ede114020d14922a3841060ae641567`; `duo_old.riv`
  SHA-256: `436d23583822671bf1700ad61d0bdec85f936ac7410487d2d43b581097a5bf09`.

## Alpha.3 (historical)

- `versionCode = 92`, `versionName = "1.8.0-alpha.3"`; delivered through Telegram message 41.
- APK size: 7,518,853 bytes; SHA-256:
  `6c1c3899cfd4d8d93cd318fc3793ba2cb1b3711ed232cf2696c4511ab11ee591`.
- Navigation icons packaged and tinted (active 24 dp / inactive 17 dp); selector lens/highlight
  movement-only; 5-bar cellular mapping 1,1,2,3,4; explicit `rightSeat` shade/home seat; gray
  22% 2-SIM battery track; static Classic card; setting previews follow the selected design;
  Custom Live Preview removed.
- 483 unit tests; Rive 158/158; `duo.riv` SHA-256:
  `70c4624c137dd374f4826e3d653504cb914f4e0b01d432ddc8ad2a2bf748c13e`; `duo_old.riv`
  SHA-256: `0ac9618c428e12b493058d17596e5b136fa6512f7ea352ee17b73b03d46f314f`.

## Alpha.2 (historical)

- `versionCode = 91`, `versionName = "1.8.0-alpha.2"`.
- APK size: 7,583,516 bytes; SHA-256:
  `5d0ddf42bed19fe09146295df7af81b46961eb286871608475c4064d5ffca2f9`.
- `adb install -r` on CPH2747 returned Success at 19:31:22; app data was kept.
- Design-switch fatal fixed: two persistent Rive panes in the app preview, no destroy/recreate;
  design cards moved to still images (`classic_demo.gif`, `design_dual.png`).
- Battery line direction driven by `batteryLineStart`; live event seat moved to `eventSlot` with
  `INVERTED_MIDDLE_Y=-43.35`; selector moved under the tabs with a 0.04 tint and no
  blur/vibrancy; shade/header patch restored.
- 482 unit tests; Rive 158/158 keyframes with a curve; `duo.riv` SHA-256:
  `39910296c24d55fee6efa8e58d4daefe242b01cdf4ebeb2728bbf19acd22db49`.

## Alpha.1 (historical)

- `versionCode = 90`, `versionName = "1.8.0-alpha.1"`.
- Size: 7,479,360 bytes; SHA-256:
  `fb3d1d613a2faba6dccdd3861025e8dccc2975e7a0978850efa60f113d2964e8`.
- Installed on CPH2747 at 18:14:24 with data kept.
