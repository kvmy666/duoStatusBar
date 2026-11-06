# Phase 3 Report — App customization (shell, engine, presets, backup)

Status: **partial: shell/engine/backup/presets/preview, middle-slot choices, per-icon Wi-Fi/cellular
toggles, alarm/flashlight/screenshot live-event rendering and the event simulation implemented;
microphone/location events, layouts, icon packs and device verification pending.**

Branch: `phase/3-app-customization`, merged to `main` as the approved prototype at `10f9ac7`.
Phase 1 merged to `main` at `5485ebf`; Phase 2 committed at `1ad00b8`; Phase 3 commits `3b8a348`,
`e5b9fea`, `5d0ed88` (middle-slot choices), `6649a56` (per-icon Wi-Fi/cellular visibility), and the
event/simulation prototype series ending at `163b3b8`.

## 1. Three-page shell

- `io.github.kvmy666.duostatusbar.ui.DuoShell` provides bottom navigation with three pages:
  Home, Engine, Settings.
- Home shows module status, a live preview card (`DuoPreview`), and a presets card.
- Tested on CPH2747 (OxygenOS 16) without fatal.

## 2. Engine selector

- Additive `engineMode` with values `auto` / `rive` / `canvas`; no change to existing default
  behaviour when unset.
- `DuoGuard` remains the single owner of Rive allow/block decisions; forcing Rive/Canvas takes
  effect on the next status-bar start.
- Provider column and fallback reason surfaced in the Engine page.
- Current selector dropdown tested on device (Canvas -> Auto) with no fatal.

## 3. Presets

- `io.github.kvmy666.duostatusbar.settings.DuoPresets`: Classic, Compact, Bold.
- Each preset is a pure transform of `DuoSettings` that changes only its named fields and preserves
  the rest (enabled, engine mode, actions, surfaces, sim choice, ...).
- Presets are data-driven and applied from the Home page with a broadcast + settings push.
- Tested Compact -> Classic on device.
- Unit tests: `DuoPresetsTest`.

## 4. Backup / import

- Format `duo-settings/1`: one plain versioned JSON file with settings, preferences and
  customization; no encryption, no secrets.
- Allowlist/exclusions applied; import validates and shows a confirmation dialog before overwrite.
- Storage Access Framework (SAF) entry points for export/import.
- Unit tests only: `BackupCodecTest`, `DuoPrefsBackupTest`, `SettingsChannelTest`.
- No device test yet for SAF flow or landscape orientation.

## 5. Live preview

- Home page embeds `DuoPreview` (180 px, non-looping, 1000 ms reveal) inside a card.
- Preview is static/visual only; it does not drive the status bar.

## 6. Middle slot

- `middleSlot` choices: `auto` / `wifi` / `cell` / `percent` / `clock` / `charging`
  (`DuoPrefs.MIDDLE_SLOT_*`, normalized in `DuoPrefs.normalizeMiddleSlot`).
- Wi-Fi and cell reuse the existing occupant logic; percent/clock/charging reuse the
  existing middle text seat (network label), so no new geometry is introduced.
- Clock updates on `ACTION_TIME_TICK` (registered in `DuoStateMonitor`); the charging
  choice only renders while charging.
- Provider column `middle_slot` (`DuoPrefs.COL_MIDDLE_SLOT`), persisted in settings and
  pushed over the settings channel.
- Tests: mapping/channel coverage (`DuoMappingTest`, `SettingsChannelTest`).
- No device test yet; Rive/Canvas visual parity for the middle seat needs verification
  on device.

## 6a. Per-icon visibility (Wi-Fi glyph / cell dots)

- `showWifiGlyph` and `showCellDots` settings (DuoPrefs `COL_SHOW_WIFI_GLYPH`,
  `COL_SHOW_CELL_DOTS`) gate the Wi-Fi glyph and the four cellular dots inside the
  ring; both default to true, so existing behaviour is unchanged.
- Plumbed through `DuoMapping`/`DuoVisual`, the settings provider column and the
  settings channel; toggles exposed in the settings UI.
- Unit tests: `DuoMappingTest`, `SettingsChannelTest`.
- No device/UI test yet.

## 7. Tests and build

- Unit tests: 317 (last run, 0 failures).
- Latest code commit: `7436517` plus the flashlight-event slice on
  `phase/3-app-customization`.
- Version: `1.4.3-beta.4` (code 27).
- APK size/SHA: `6,902,776` bytes /
  `e4effec1fbc1396a4a74056601e949cf03eb2305aa75d5eb985ba241b0431a3a`
  (`app/build/outputs/apk/release/app-release.apk`).
- `duo.riv` SHA-256 `540bb0884324b1f4303b22debdfb943cb4ad9bc04ce3fba0cacaa3896269e201`;
  packaged as `res/zy.riv` by resource shrinking, byte-identical when extracted.
- Static gates: Rive verify clean, 132/132 eased keyframes, 0 inert; log-route check clean.
- Not installed/tested on phone in this pass (device disconnected); flashlight/alarm device
  verification remains pending.

## 8. Remaining work

- 4+4 cell-dot layouts with battery as a bar; Wi-Fi in the charging-number slot; battery % in the middle.
- Battery bar rendering.
- Wi-Fi charge slot.
- Icon packs (shape switching; OEM assets remain prototype-only).
- Per-icon show/hide matrix: Wi-Fi glyph and cell dots implemented (6649a56); broader stock-icon matrix remains partial.
- Question-mark toggle simulations and per-toggle static previews.
- Live events: alarm + flashlight + screenshot rendering implemented (Rive state transition, Canvas
  clone, per-event switch). Microphone/location sources and artwork remain.
- Device verification of flashlight on -> off and alarm set/ring on all three surfaces.
- Rive/Canvas middle-slot parity.
- Backup SAF flow and landscape orientation on device.
- Device smoke for backup import/export and landscape.
- Rive visual verification of the middle seat on device (no device test yet).

## 9. Safe-mode evidence

- Latest SystemUI tombstone in `from users/safe-mode-20261028/` was LuckyTool/DexKit, not Duo.
- The landscape safe-mode fix lives on a separate branch `fix/landscape-safe-mode` at commit
  `d24b448`; this report does not include that fix.
