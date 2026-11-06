# Requirements gap audit — after phone + 8-UI icon extraction

Date: 2026-10-31. Scope: `agent/REQUIREMENTS.md`, `agent/PLAN-v2.md`, Phases 1–8.
This audit answers "did we implement everything?" with **no promotion of unverified work**.

## 1. What was added in this pass

- **Phone icons pulled (no phone test):** `com.android.systemui` split set pulled over Wi-Fi ADB from
  CPH2747 to `reverse/device-systemui/com.android.systemui/` (108 MB).
  Base `SystemUI.apk` SHA-256 `dbe67fd3688a5bb5ebe7200190aaaac0bfbf88068a1534b6aebd62cd3f588ff4`.
  Nothing was installed, launched, toggled or tested on the phone.
- **ADB over Wi-Fi restored:** connected at `[redacted]`; used only for pull/dumps.
- **Eight corpus UIs decoded** with `apktool d -s --no-src`: Samsung, Xiaomi, vivo, Motorola,
  Nothing, OnePlus, OPPO, Honor.
- **Icon inventory tool:** `tools/extract-status-icons.py`.
- **Icon packs:** 5,353 drawable candidates, 24 MB, under gitignored `reverse/icon-packs/`, with
  `manifest.json` (source path, category, bytes, SHA-256, vector/raster).
- **Report:** `agent/EVENT-ICONS-PULL-REPORT.md`.
- **Static-map check:** `tools/check-rom-profile-assets.py` verified every id in the decoded resources.
  OxygenOS 8/8 and HyperOS 7/7 pass; six static maps pass after removing two guessed ColorOS ids and
  one guessed MagicOS id. AOSP has no decoded tree and remains an explicit error. See
  `agent/ROM-PROFILE-ASSET-AUDIT.md`.

Counts (phone + 8 UIs) are in that report. The phone pack alone has `flashlight=60`,
`screenshot=13`, `alarm=8`, `dnd=22`, `mic=47`, `location=33`, `airplane=24`.

## 2. Status by requirement group

| Group | State | What is still missing |
|---|---|---|
| UI-001 multi-UI | **partial** | 2 measured maps; 7 static-only, no runtime dumps |
| UI-002..UI-010 surfaces/host | **partial** | measured OnePlus/HyperOS; other UIs not measured; QS panel not proven |
| UI-011 per-icon toggles | **partial** | full stock-icon matrix not captured |
| UI-012 cell accuracy | **partial** | mapper + logs; TP-050..054 device pending |
| **UI-013 OEM icons** | **prototype done** | assets extracted; owner must choose/remove before release |
| UI-014 Canvas clone | **partial** | charging shake/pulse yes; every Rive state still not side-by-side |
| UI-015 parallel UI lanes | **partial** | corpus/assets exist; no per-UI runtime lane finished |
| UI-016 acceptance per UI | **not met** | only 2 measured; rest `UNVERIFIED` |
| CUS-001/002 engines | **partial** | forced engines work; Auto fallback not screenshot-proven on API 36 |
| CUS-003 middle slot | **partial** | Wi-Fi/cell/percent; clock/charging visuals unverified |
| CUS-004 layout options | **not done** | 4+4/bar/Wi-Fi-in-charge-slot UI not implemented |
| CUS-005 icon packs | **not done** | assets extracted, but no pack list/preview/switch in the app |
| CUS-006 show/hide | **partial** | Duo glyphs; stock icon matrix incomplete |
| CUS-007 presets | **partial** | data-driven presets exist; not all previewed |
| CUS-008 previews/help | **not done** | question-mark simulation not implemented |
| CUS-009 confused-user UX | **not verified** | no usability review |
| CUS-010 backup | **partial** | round-trip test; schema/version doc missing |
| CUS-011 three pages | **done** | Home/Engine/Settings |
| CUS-012 liquid glass | **code done, unverified** | live path on phone, static preview on emulator; owner visual sign-off pending |
| CUS-013 English only | **partial** | string audit not complete |
| LIVE-001/002 event set | **partial** | alarm/flashlight/screenshot/charging; **mic/location/volume sources missing** |
| LIVE-003/004 event motion | **partial** | three motions; volume/mic/location motion absent |
| LIVE-005/006 sources/consent | **not done** | no permission/consent flow for UsageStats/NotificationListener |
| LIVE-007 event-driven | **partial** | no polling in current path; battery/event audit not complete |
| RIV-001 quiet motion | **partial** | charge settle added; full design review pending |
| RIV-002 0→level sweep | **partial** | bolt flight + arrival pulse; **green-arc sweep not authored** |
| RIV-003 20% shake | **done** | 200 ms, both renderers, unit-tested; device pending |
| RIV-004 per-event motion | **partial** | alarm/flashlight/screenshot; rest missing |
| RIV-005 Canvas parity | **partial** | charge shake/pulse mirrored; full state matrix not |
| RIV-006 CLI | **done** | verify/build/screenshot/data/advance used |
| RIV-007 pause idle | **done** | existing renderer pause path; not re-measured on device |
| RIV-008 headless evidence | **partial** | idle + charge 96/110 frames; no per-state contact sheet |
| PERF-001..008 | **not met** | emulator p95 216.88 ms (baseline 116.87); device run and graphics-memory/battery checks pending |
| Phase 1 safe mode | **unverified** | `fix/landscape-safe-mode` not merged |
| DOC-005/006 | **partial** | release notes exist; backup schema doc missing |

## 3. "Things we skipped" — explicit list

1. **Icon extraction was skipped and is now closed** (phone + 8 UIs, 5,353 files).
2. **Icon-pack switching UI (CUS-005)** — extraction is done, the picker is not.
3. **Green-arc 0→level charging sweep (RIV-002)** — only the bolt flight and settle pulse exist.
4. **Mic/location/volume event sources and motions (LIVE/RIV-004)** — not implemented.
5. **Permission/consent flow (LIVE-005/006)** — not implemented.
6. **Layout options (CUS-004)** and **help/preview simulation (CUS-008)** — not implemented.
7. **Per-UI runtime measurements (UI-016/PERF-008)** — seven static maps only.
8. **Phase 1 safe-mode fix** — unmerged/unconfirmed.
9. **Device visual/performance sign-off** — CPH2747 has not run beta.20.

## 4. Safety and hygiene

- Vendor assets remain outside `app/`; no extracted icon is packaged.
- `reverse/` is local-only. Do not commit APKs, decoded trees, or icon packs.
- The phone was only read: `pull`/`getprop`; no test, install, reboot or settings change.
- Wi-Fi ADB remains available for future pulls; it can be disconnected with `adb disconnect`.
