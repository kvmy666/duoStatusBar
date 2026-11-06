# Event icon extraction — phone + eight UIs

Date: 2026-10-31. This is an extraction/inventory report, not a packaging change.

## Sources

- Phone: OnePlus CPH2747 over Wi-Fi ADB; `com.android.systemui` full split set pulled to `reverse/device-systemui/com.android.systemui/` (108 MB). SystemUI base SHA-256 `dbe67fd3688a5bb5ebe7200190aaaac0bfbf88068a1534b6aebd62cd3f588ff4`.
- Eight corpus APKs decoded with `apktool d -s --no-src`: Samsung 16-11, Xiaomi 17-03, vivo OriginOS 16.0.7.194, Motorola 15.7, Nothing 15.0.176, OnePlus 16.00.14-2, OPPO 3.0.4, Honor 11.0.0.1.
- Script: `tools/extract-status-icons.py`; copies drawable/mipmap/raw candidates and hashes them.
- Output: `reverse/icon-packs/` (gitignored), 5,353 files, 24 MB; manifest `reverse/icon-packs/manifest.json`.

No APK was installed, launched or tested on the phone. ADB was restored over Wi-Fi only for pulling.

## Counts per UI

| UI | wifi | signal | battery | airplane | dnd | alarm | flashlight | volume | location | mic | screenshot | total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| oneplus-phone | 87 | 146 | 42 | 24 | 22 | 8 | 60 | 97 | 33 | 47 | 13 | 579 |
| oneplus | 87 | 146 | 45 | 24 | 22 | 8 | 56 | 94 | 33 | 47 | 13 | 575 |
| samsung | 117 | 53 | 24 | 40 | 19 | 9 | 84 | 133 | 46 | 52 | 6 | 583 |
| xiaomi | 81 | 106 | 167 | 29 | 20 | 13 | 102 | 75 | 13 | 32 | 7 | 645 |
| vivo | 36 | 22 | 447 | 22 | 13 | 10 | 12 | 85 | 32 | 48 | 8 | 735 |
| motorola | 30 | 53 | 6 | 33 | 13 | 12 | 13 | 141 | 29 | 62 | 20 | 412 |
| nothing | 14 | 2 | 10 | 22 | 13 | 8 | 20 | 82 | 28 | 54 | 14 | 267 |
| oppo | 79 | 552 | 77 | 3 | 29 | 10 | 112 | 64 | 21 | 4 | 15 | 966 |
| honor | 72 | 122 | 92 | 5 | 5 | 6 | 39 | 55 | 176 | 6 | 13 | 591 |

## Notes

- `oneplus-phone` is the actual CPH2747 SystemUI, separate from the corpus OnePlus 16.00.14-2 pack; both are kept for comparison.
- Phone event candidates include `alarm/stat_sys_alarm.xml`, `flashlight/$$qs_flashlight_icon_on__0__0.xml`, `screenshot/status_bar_qs_partial_screenshot_active.xml`, `mic/$$qs_mic_access_off__*`, `location/$$qs_location_icon_on__*`, `dnd/ic_qs_dnd_on.xml`. They are vector XML with tint/state paths; this is why the pack keeps them as resources rather than flattening to PNG.
- Many status icons are Android vector XML, not PNG. The pack keeps vectors intact; do not rasterize blindly because tint/state paths are meaningful.
- `apktool` returned 1 for vivo/OPPO/Honor but still produced usable `res/` trees; the pack is marked with that limitation, not treated as a full decode.
- These are proprietary vendor assets. They stay under `reverse/` and are not packaged into `app/`; the prototype icons remain our own drawings. A license/owner decision is required before shipping any extracted art.
- Candidate event sets now available: flashlight (12–112 per UI), screenshot (6–20), alarm (6–13), DND (5–29), mic (4–62), location (13–176), airplane (3–40).
