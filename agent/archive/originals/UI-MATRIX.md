# Duo UI Matrix (8 vendor UIs + AOSP)

Status: reference + measurement plan. This is not a claim of support. A UI is `supported` only after a measured dump, screenshot, and log exist for it.
Last updated: 2026-10-27 (Phase 1 visibility-hook audit added).

## Evidence tiers

| Tier | Meaning | May be claimed? |
|---|---|---|
| `measured` | Captured on a named device/build with the current code | Yes |
| `public-source` | AOSP/LineageOS source or a public module, not our device | Only as a hypothesis |
| `unverified` | No device evidence | No; this is a lead |

## Matrix

| # | Vendor / UI | Android base | SystemUI package / quirk | Status-bar / battery host | Tier | Next measurement |
|---|---|---|---|---|---|---|
| 0 | AOSP / GSI / LineageOS | 13-17 | `com.android.systemui` | `status_bar` -> `PhoneStatusBarView`; ids `clock`, `notification_icon_area`, `status_bar_end_side_content`; `system_icons` -> `LinearLayout`; `statusIcons` -> `StatusIconContainer`; `battery` -> `BatteryMeterView` | public-source | Boot `ae_api36_root`; dump the tree; confirm ids/classes |
| 1 | OnePlus / OxygenOS 16 | Android 16 / SDK 36 | `com.android.systemui`; `/system_ext/priv-app/SystemUI/SystemUI.apk` | Status: `status_bar_end_side_content` -> `com.oplus.systemui.statusbar.widget.EndSideContentLayout`; `system_icons` -> `android.widget.LinearLayout`; `battery` -> `...pipeline.battery.ui.view.StatBatteryMeterView`; `statusIcons` -> AOSP `StatusIconContainer`. Shade/QS: `quick_qs_status_icons` -> `icons` -> `statusIcons` (plain `LinearLayout`) + `batteryRemainingIcon` -> `battery_icon_view` | measured | Phase 1: hook the shade/QS battery host; capture before/after |
| 2 | OPPO / realme ColorOS | ColorOS 16 / Android 16 | `com.android.systemui`; lockscreen may run in `:ui` subprocess | Container **unverified**; do not reuse OnePlus classes without a dump | public-source (package/process), unverified (container) | Pull ColorOS SystemUI; dump shade/QS; measure battery host |
| 3 | Xiaomi / HyperOS / MIUI | HyperOS 3 / Android 17 (24129PN74G); Android 16 (24129PN74C) | `com.android.systemui`; `MiuiSystemUI.apk`; process may report `packageName="system"` | `system_icons` -> `com.android.systemui.statusbar.views.MiuiStatusBatteryContainer`; `statusIcons` -> `...views.MiuiStatusIconContainer`; `battery` -> `...views.MiuiBatteryMeterView`; `status_bar` -> `...phone.MiuiPhoneStatusBarView`; `clock` -> `...views.MiuiClock` | measured | Verify HyperOS shade/QS tree; add a second build |
| 4 | Samsung / One UI 8 | Android 16 | `com.android.systemui` | Container **unverified**; public hook `...statusbar.phone.ui.StatusBarIconControllerImpl`, `hideBySimplification(IconManager, String)`, `config_status_bar_system_icon_allowlist` | public-source (hook), unverified (container) | Pull One UI SystemUI; dump shade/QS; measure battery host |
| 5 | vivo / OriginOS / Funtouch | OriginOS 16 / Android 16 | Main SystemUI **unverified**; `com.vivo.systemuiplugin` is a separate scope for Smart Island | Container **unverified** | public-source (plugin), unverified (container) | Pull vivo SystemUI/plugin; dump; measure |
| 6 | Honor / MagicOS | MagicOS 10; Android base unverified | `com.android.systemui` | Container **unverified** | public-source (package), unverified (container) | Pull Honor SystemUI; dump; measure |
| 7 | Motorola / MyOS / Hello UI | Android 16 / API 36 | `com.android.systemui` | Container **unverified**; public ordering keys `user_switcher,wifi,mobile,battery` | public-source (package/order), unverified (container) | Pull Motorola SystemUI; dump; measure |
| 8 | Nothing / Nothing OS 4.0 | Android 16 | `com.android.systemui` | Container **unverified** | public-source (package), unverified (container) | Pull Nothing SystemUI; dump; measure |

## Phase 1 visibility-hook compatibility (exact-token audit)

This is a **static APK token audit**, not runtime support. It answers whether the new exact hooks can
install, not whether the UI behaves correctly.

| UI | `NotificationShadeWindowControllerImpl.onShadeOrQsExpanded(Boolean)` | `ShadeExpansionStateManager.onPanelExpansionChanged(..., PanelType)` | Exact `PanelType` | Phase 1 surface owner |
|---|---|---|---|---|
| OnePlus OxygenOS 16 / CPH2747 | present, measured | present, measured | `NOTIFICATION_PANEL`, `QS_PANEL`, `SHADE_PANEL` | supported, measured |
| Samsung One UI 16 | present | no 5-arg/`PanelType`; listener path only | absent | hook 1 only; unverified |
| Xiaomi HyperOS 17 | present | 3-arg `(FZZ)` only | absent | hook 1 only; unverified |
| vivo OriginOS 6 | present | `(ZFZ)` only | absent | hook 1 only; unverified |
| Motorola 15 | present | no declared 5-arg method | absent | hook 1 only; unverified |
| Nothing 15 | present | 3-arg `(FZZ)` only | absent | hook 1 only; unverified |
| OPPO 3.0.4 (old) | absent | absent | absent | structural only |
| Honor 11 (old) | absent | absent | absent | structural only |

Consequences:

- `onShadeOrQsExpanded` is the generic main-bar cover hook on the five modern non-OnePlus builds,
  subject to runtime verification.
- The QS-vs-shade owner hook only installs on the measured OnePlus signature. On the other modern
  builds `panelHookInstalled` stays false and the module uses attach-time ownership; do not claim
  their QS/shade surfaces as supported.
- Phase 2 alias targets: Samsung/Motorola `ShadeExpansionListener`; Xiaomi/Nothing
  `onPanelExpansionChanged(FZZ)`; vivo `(ZFZ)`. Each needs a per-UI QS/shade classifier and a
  runtime dump before it can be a matrix capability.
- The matrix (`RomDetection`/`RomAdapter`) selects status/shade IDs, but the visibility hooks are
  currently SystemUI-level and not yet gated per UI. Add a capability flag in Phase 2; do not infer
  support from the package name alone.

## What to measure next (per UI)

1. Build identity: `ro.build.fingerprint`, `ro.build.display.id`, `ro.build.version.sdk`, `ro.product.model`.
2. SystemUI package/version/sourceDir: `dumpsys package com.android.systemui`, `pm path com.android.systemui`.
3. Status bar tree: module `Diag` dump + `uiautomator dump` + `dumpsys window windows`.
4. Shade/QS tree: open the shade, dump, record the battery/icon host ids/classes/bounds, and the insertion route.
5. Keyguard tree: lock the device, dump.
6. Save screenshot + log + dump with a manifest entry.

## Acquisition protocol

### Allowed sources (safest -> riskiest)

1. AOSP / LineageOS / crDroid / PixelExperience source: open licences; can be stored and committed with notices.
2. `SystemUI.apk` pulled from a device the owner physically has: local analysis only; never commit.
3. User-submitted diagnostic dumps (`diagnose from users/`): consented; redact PII; never commit.
4. OEM firmware images from official sources: local only; never commit.
5. APK mirror sites: last resort; verify signer + SHA-256; quarantine; never commit.

### Pull commands (device)

```bash
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.display.id
adb shell dumpsys package com.android.systemui | grep -E 'versionName|versionCode|codePath|resourcePath'
adb shell pm path com.android.systemui
```

```bash
D=corpus/apks/<vendor>/<ui>/<build>
mkdir -p "$D"
adb shell pm path com.android.systemui | sed 's/^package://' | while read -r p; do
  adb pull "$p" "$D/$(basename "$p")"
done
sha256sum "$D"/*.apk
```

If `adb pull` is denied:

```bash
adb shell su -c "cp '/system_ext/priv-app/SystemUI/SystemUI.apk' /sdcard/Download/SystemUI.apk"
adb pull /sdcard/Download/SystemUI.apk "$D/SystemUI.apk"
```

### Host-side inventory

```bash
aapt2 dump badging "$D/SystemUI.apk"
aapt2 dump xmltree --file res/layout/status_bar.xml "$D/SystemUI.apk"
apktool d -f -o "$D/apktool" "$D/SystemUI.apk"
jadx -d "$D/jadx" --no-res "$D/SystemUI.apk"
apksigner verify --print-certs "$D/SystemUI.apk"
```

### Live tree

```bash
adb shell cmd statusbar expand-notifications
adb shell uiautomator dump /sdcard/duo-ui.xml
adb pull /sdcard/duo-ui.xml "$D/ui.xml"
adb shell dumpsys window windows > "$D/window.txt"
adb shell dumpsys gfxinfo com.android.systemui framestats > "$D/gfxinfo.txt"
adb shell cmd statusbar collapse
```

`uiautomator` may omit SystemUI windows; the module `Diag` tree is authoritative for our attach path.

## Per-UI acquisition checklist

| UI | Artifact to obtain | Evidence required | Status |
|---|---|---|---|
| OnePlus OxygenOS 16 | `SystemUI.apk` (already at `corpus/SystemUI-device.apk`) | status + keyguard + shade/QS dump, screenshots, LSPosed log | measured |
| Xiaomi HyperOS 3 | SystemUI APK (optional; dump already in `diagnose from users/`) | same, on 24129PN74G/24129PN74C | measured (dump) |
| OPPO/realme ColorOS | base + split `SystemUI.apk` | build fingerprint, display id, shade/QS tree, screenshots | APK acquired; runtime dump missing |
| Samsung One UI | base + split `SystemUI.apk` | same; verify `StatusBarIconControllerImpl` path | APK acquired; runtime dump missing |
| vivo OriginOS | `SystemUI.apk` + `com.vivo.systemuiplugin` if present | same; plugin/main-process split | APK acquired; runtime dump missing |
| Honor MagicOS | `SystemUI.apk` | same | APK acquired; runtime dump missing |
| Motorola MyOS | `SystemUI.apk` | same | APK acquired; runtime dump missing |
| Nothing OS | `SystemUI.apk` | same | APK acquired; runtime dump missing |
| AOSP/GSI | AOSP/LineageOS source | baseline ids/classes | public-source |

Owner action for each missing UI: provide the SystemUI APK/splits (and a diagnostic dump if possible), or approve an official firmware download. Until then the UI stays `unverified`.

## Local corpus layout (gitignored)

```text
corpus/
  index.json
  apks/<vendor>/<ui>/<build>/
  dumps/<vendor>/<ui>/<build>/
  decompiled/<vendor>/<ui>/<build>/
  screenshots/<vendor>/<ui>/
  quarantine/
```

Add `corpus/` to `.gitignore` before pulling anything.

## Manifest schema (`duo-corpus/1`)

```json
{
  "schema": "duo-corpus/1",
  "entry": {
    "id": "oneplus-oxygenos-16-cph2747",
    "vendor": "OnePlus",
    "ui": "OxygenOS 16",
    "android": { "release": "16", "sdk": 36 },
    "device": { "model": "CPH2747", "fingerprint": "<ro.build.fingerprint>", "display": "<ro.build.display.id>" },
    "systemui": { "package": "com.android.systemui", "versionName": "<dumpsys>", "sourceDir": "<pm path>" },
    "apks": [{ "name": "SystemUI.apk", "sha256": "<sha>", "size": 0, "signer_sha256": "<apksigner>" }],
    "source": { "kind": "owner-device", "url": null, "acquired_at": "2026-10-26", "method": "adb pull", "consent": "owner" },
    "evidence": { "status": "measured", "dump": "<path>", "dump_sha256": "<sha>", "public_urls": [] },
    "container": { "id": "quick_qs_status_icons", "class": "android.view.ViewGroup", "tier": "measured" },
    "license": { "redistribution": "prohibited", "commit": false },
    "redactions": ["ssid", "imei", "serial", "accounts"],
    "notes": "Shade/QS host measured 2026-10-26"
  }
}
```

For a public-source-only row: empty `apks`, `source.kind = "public-source"`, `evidence.status = "public-source"`, `container.tier = "unverified"`.

## Commit rules

| Item | Local corpus | Commit to GPL repo |
|---|---|---|
| Vendor APK / splits / oat / vdex | Yes | **Never** |
| Decompiled smali/JADX/OEM layouts/drawables/fonts | Yes | **Never** |
| AOSP/LineageOS source | Yes | Yes, with notices |
| User diagnostics/logcat/screenshots | Yes, redacted | **No** |
| Class/id/package facts with provenance | Yes | Yes, as documentation |
| SHA-256/fingerprint/source URL | Yes | Yes, metadata only |
| OEM icons | Local/prototype only | Owner removes before official launch |

## Per-UI header map (static, aapt2, 2026-10-27)

Source: downloaded SystemUI APKs in `corpus/`; xmltrees in `corpus/analysis/<vendor>-<layout>.xml.txt`. **Static evidence only** — runtime still needs a device dump.

| UI | QS/shade header layout | Icon cluster | Battery host | Notes |
|---|---|---|---|---|
| OnePlus OxygenOS 16 | `quick_status_bar_expanded_header` (`OplusQuickStatusBarHeader`) + `combined_qs_header` | `quick_qs_status_icons` (`ConstraintLayout`) -> `icons` (`AlphaOptimizedLinearLayout`) -> `statusIcons` (`StatusIconContainer`); also `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons` | `batteryRemainingIcon`; `battery_icon_view` only here | The visible QS cluster is `quick_qs_status_icons`; correct hooks: `OplusQSSimpleHeader.onInit` (simple QS) and `OplusSplitShadeHeaderControllerExImpl.init` (split shade). `ShadeHeaderController` alone gives the GONE `split_shade_status_bar` |
| Samsung One UI 16 | `sec_quick_status_bar_expanded_header` (`SecQuickStatusBarHeader`, no cluster) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons` (`StatusIconContainer`); `system_icons` -> `statusIcons` | `batteryRemainingIcon` (`BatteryMeterView`); `battery` in `system_icons` | No `quick_qs_status_icons`; use the combined header cluster or the status-bar cluster |
| Xiaomi HyperOS 17 | `miui_ns_qs_header_view` (`MiuiQSHeaderView`, nested `system_icon_area`) + `combined_qs_header` | `system_icons` (`MiuiStatusBatteryContainer`) -> `MiuiStatusIconContainer` `statusIcons`; `combined_qs_header` -> `shade_header_system_icons` -> `statusIcons` | `MiuiBatteryMeterView` `battery`; `batteryRemainingIcon` absent | No `quick_qs_status_icons`; the measured HyperOS 3 status-bar path is `system_icons` |
| vivo OriginOS 6 | `quick_status_bar_expanded_header` (`QuickStatusBarHeader`, no cluster) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons`; `system_icons` -> `statusIcons` | `VivoBatteryMeterView` `batteryRemainingIcon`; `battery` in `system_icons` | No `quick_qs_status_icons` |
| Motorola MyOS 15 | `modern_qs_header` (clock only) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons`; `system_icons` -> `statusIcons` | `BatteryMeterView` `batteryRemainingIcon`; `battery` in `system_icons` | No `quick_qs_status_icons` |
| Nothing OS 15 | `quick_status_bar_expanded_header` (`QuickStatusBarHeader`, no cluster) + `combined_qs_header` | `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons`; `system_icons` -> `statusIcons` | `BatteryMeterView` `batteryRemainingIcon`; `battery` in `system_icons` | No `quick_qs_status_icons` |
| OPPO ColorOS (3.0.4, old) | `quick_status_bar_expanded_header` + `quick_qs_status_icons` | `quick_qs_status_icons` (`LinearLayout`) -> `date` + `statusIcons`; `system_icons` -> `statusIcons` (`AlphaOptimizedLinearLayout`) | `quick_status_bar_system_icons` -> `battery`; `system_icons` -> `BatteryMeterView` `battery` (`oppo_battery_view`) | Old build; ColorOS 16 needs a device dump before claiming |
| Honor MagicOS (11.0.0.1, old) | `hw_notch_qs_header_status_bar` (`HwNotchStatusBar`) + `quick_status_bar_expanded_header` (`HwQuickStatusBarHeader`) | `HwStatusbarContents` -> `HwSystemIcons` `system_icon_area_fixed`, `HwSystemIconParentView` -> `HwSystemIconsMore` `statusIcons_more`, `HwSystemIcons` `system_icon_area`; `hw_system_icons` -> `statusIcons` | `hw_system_icons` -> include `battery` | Old build; MagicOS 10 needs a device dump |

**Common pattern:** OnePlus/Samsung/Xiaomi/vivo/Motorola/Nothing share `combined_qs_header` -> `shade_header_system_icons` -> `hover_system_icons_container` -> `statusIcons` + `batteryRemainingIcon`. OnePlus/OPPO additionally expose a visible `quick_qs_status_icons` cluster. Honor uses `hw_system_icons`/`system_icon_area`. The code must resolve the visible cluster per UI; do not hardcode OnePlus ids.

**Profile schema implication:** `RomAdapter` currently carries only status-bar `containerIds`/`batteryId`. Add optional `shadeContainerIds`/`shadeBatteryId` (default empty) and parse them in `RomProfiles`; only then can each UI carry its own shade map without breaking the old status-bar path.

## Resolver wiring status (2026-10-27)

- `RomAdapter` now carries `shadeContainerIds`, `shadeBatteryId`, `shadeStatusIconIds`, `shadeMeasured`.
- `ContainerFinder.findShadeIconsArea` / `findVisibleShadeArea` / `findShadeBattery` consume the first two as candidate probes, gated on `width>0 && isShown && isAttachedToWindow`.
- OnePlus is `shadeMeasured=true`; Samsung, ColorOS, vivo, Motorola, Nothing, Honor and HyperOS shade maps are static/unverified.
- `shadeStatusIconIds` is not consumed yet.
- Runtime dumps are still missing for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS; do not claim support.

## Static resource matrix (aapt2, 2026-10-27)

Exact-name presence in the downloaded vendor SystemUI APKs. `absent` means the exact AOSP/OnePlus name was not found; it may be renamed, so it is **not** proof that the function is missing.

| UI | `quick_qs_status_icons` | `header_container` | `batteryRemainingIcon` | `battery_icon_view` |
|---|---|---|---|---|
| OnePlus OxygenOS 16 | present | present | present | present |
| Samsung One UI 16 | absent | absent | present | not checked |
| Xiaomi SystemUI 17 | absent | absent | absent | not checked |
| vivo OriginOS 6 | absent | absent | present | not checked |
| Motorola System UI 15 | absent | absent | present | not checked |
| Nothing OS 15 | absent | absent | present | not checked |
| OPPO System UI 3.0.4 (old) | present | absent | absent | not checked |
| Honor System UI 11 (old) | absent | absent | absent | not checked |

**Conclusion:** the OnePlus shade fix is not generic. The 8-UI code needs per-UI host resolution (resource name/class/role), with the OnePlus ids as one profile, not a global assumption.

## Candidate header layouts (static, aapt2, 2026-10-27)

Observed across the 8 downloaded APKs (vendor mapping is in `corpus/analysis/resources-<vendor>.txt`; do not assign a name to a UI without that file):

`combined_qs_header`, `quick_qs_status_icons`, `quick_status_bar_expanded_header`, `quick_status_bar_header_system_icons`, `system_icons`, `hw_system_icons`, `modern_qs_header`, `normal_shade_header`, `shade_header_container`, `large_screen_shade_header`, `sec_quick_status_bar_expanded_header`, `miui_ns_qs_header_view`, `miui_quick_status_bar_expanded_header`, `hw_notch_qs_header_status_bar`.

This confirms the 8-UI code needs per-UI layout/profile resolution. A single OnePlus id set is not portable.

## Current status

- Measured: OnePlus CPH2747 (OxygenOS 16), Xiaomi 24129PN74G / 24129PN74C (HyperOS).
- Acquired locally: all eight vendor SystemUI APKs in `corpus/` (versions/hashes in `corpus/index.json`), plus HyperOS dumps (`diagnose from users/`) and AOSP source references.
- Public-source: AOSP baseline.
- **Runtime dumps are still missing** for ColorOS, One UI, OriginOS, MagicOS, MyOS, Nothing OS. Their APK maps are static only (`public-source`), so those UIs remain `unverified`; do not claim support.
- OPPO (3.0.4) and Honor (11.0.0.1) APKs are old builds; they are useful for structure, not for current ColorOS/MagicOS claims.
- Disk budget: ~8.5 GB free.
- No QS panel tree captured beyond the header.
- No vendor APK committed.

## Sources

- AOSP `status_bar.xml`, `system_icons.xml`, `StatusIconContainer`, `BatteryMeterView`: `https://android.googlesource.com/platform/frameworks/base/`
- OnePlus/HyperOS measured dumps: `diagnose from users/`, `app/src/main/assets/rom-profiles.json`
- Public module references for unverified rows: LSPosed module pages, XMiTools, HyperDuo, HonorDuo, Motorola-Tweak-Mod, NothingTweaks.

## Visibility-hook capability (machine-readable, T2)

Fields live on `RomAdapter` (`coverHook`, `panelExpansionKind`, `panelHookMeasured`) and are parsed from
`app/src/main/assets/rom-profiles.json`. They describe the **static exact-token audit** only; they are not
a claim of runtime support. A UI is runtime-supported only after a measured dump exists (see Evidence tiers).

| UI | coverHook | panelExpansionKind | panelHookMeasured | panelOwnerAvailable | Status |
|---|---|---|---|---|---|
| OnePlus OxygenOS 16 / CPH2747 | true | panel_type_5arg | true | true | measured |
| Samsung One UI 16 | true | listener | false | false | unverified |
| Xiaomi HyperOS 17 | true | fzz_3arg | false | false | unverified |
| vivo OriginOS 6 | true | zfz_3arg | false | false | unverified |
| Motorola MyOS 15 | true | listener | false | false | unverified |
| Nothing OS 15 | true | fzz_3arg | false | false | unverified |
| OPPO / ColorOS | false | none | false | false | unverified |
| Honor / MagicOS | false | none | false | false | unverified |
| AOSP baseline | true | panel_type_5arg | false | false | public-source |

Runtime support is **not** claimed by these fields; they only state which exact hooks the static audit
found. Each non-measured row still needs a per-UI QS/shade classifier and a runtime dump before its
panel hook can install.

`panelOwnerAvailable` is `true` only for OnePlus/OxygenOS (measured 5-arg `onPanelExpansionChanged`
with a `PanelType`); it is `false` for AOSP and every other UI. A `false` value means the UI may
still report cover/expansion via `panelExpansionKind`, but never QS-vs-shade ownership.
