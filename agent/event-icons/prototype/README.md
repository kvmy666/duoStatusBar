# Prototype SystemUI event icons (PROTOTYPE ONLY)

Source APK: `corpus/SystemUI-device.apk` (decoded at `/tmp/duo-systemui-res`)
SHA-256: `dbe67fd3688a5bb5ebe7200190aaaac0bfbf88068a1534b6aebd62cd3f588ff4`

**PROTOTYPE ONLY / OEM licensing.** These are proprietary OEM (OnePlus) resources extracted
for prototype analysis and engine testing. They are NOT for release. See requirement UI-013:
release builds must not rely on OEM art; final artwork must be owned, redrawn, or approved/licensed.
Do not copy these into `app/src/main/res` or any release asset set.

## Files

| File | Source path | SHA-256 | Dimensions / comment |
|---|---|---|---|
| `stat_sys_alarm.xml` | `res/drawable/stat_sys_alarm.xml` | `4958dd9973a0b88e97d6ef8b09fe0cde7b27d40953830bff5d57af444ae1c569` | 19.0×16.0 dp, viewport 19×16 |
| `ic_alarm.xml` | `res/drawable/ic_alarm.xml` | `85a981ea122aafda6ec1a9e42450f20d198f5b3de1d11545a91a11b3f2d7a627` | 17.0×17.0 dp, viewport 24×24 |
| `ic_flashlight_off.xml` | `res/drawable/ic_flashlight_off.xml` | `9e22d8b9dc57d240f14f0bb253ddccca209834ee3957e8a6636aec58e6daecac` | 24.0×24.0 dp, viewport 24×24 |
| `status_bar_qs_flashlight_active.xml` | `res/drawable/status_bar_qs_flashlight_active.xml` | `30699ce189e86362cff45a5391e80bafebbd0a8bdd5929fb7912fb11b8209834` | 24.0×24.0 dp, viewport 24×24 |
| `privacy_icon_camera.xml` | `res/drawable/privacy_icon_camera.xml` | `abe925bc58c6da91b5aa2af9787711369a572398f9d7181bf1aa6b3910eec2ec` | 16.0×16.0 dp, viewport 12×12 |
| `privacy_icon_microphone.xml` | `res/drawable/privacy_icon_microphone.xml` | `543e334ffca8ac80c646cf8a9a48c96da5cf4d2cc92548f95493abf70d06b43b` | 16.0×16.0 dp, viewport 12×12 |
| `privcay_icon_location.xml` | `res/drawable/privcay_icon_location.xml` | `b44caaff07928468413993c9b6c97b519fb3376da2f64bf8458a6707516e8c08` | 12.0×12.0 dp, viewport 12×12 |

## Mapping

- Alarm icons (`stat_sys_alarm.xml`, `ic_alarm.xml`) -> **ALARM** animation/event type.
- Flashlight icons (`ic_flashlight_off.xml`, `status_bar_qs_flashlight_active.xml`) -> **FLASHLIGHT** while-active (stateful event).
- Camera/mic/location privacy icons (`privacy_icon_camera.xml`, `privacy_icon_microphone.xml`, `privcay_icon_location.xml`) -> their respective event types (camera/privacy, microphone, location).
