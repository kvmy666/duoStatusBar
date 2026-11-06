# Screenshot source audit — 8 vendor SystemUI APKs + CPH2747

Date: 2026-10-31. Method: apktool smali decode (`-r`), class inventory, plus a live ADB trigger on
CPH2747. This is a source audit, not a screenshot fix.

## Shared source

`com.android.systemui.screenshot.TakeScreenshotExecutorImpl` and
`com.android.systemui.screenshot.ScreenshotController` are present in **six of the eight** vendor
SystemUI APKs:

| UI | AOSP TakeScreenshotExecutorImpl | Notes |
|---|---|---|
| OnePlus 16.00.14-2 | yes | same AOSP package/class |
| Samsung 16-11 | yes | same class, different dex split |
| Xiaomi 17-03 | yes | **plus** `com.miui.systemui.screenshot.ScreenshotHelper` |
| vivo OriginOS 16.0.7.194 | yes | same class |
| Motorola 15.7 | yes | same class |
| Nothing 15.0.176 | yes | same class |
| OPPO 3.0.4 | **no** | old APK, different screenshot stack |
| Honor 11.0.0.1 | **no** | old APK, different screenshot stack |

That means **one AOSP hook can cover six interfaces**, not eight. Xiaomi may bypass it through its
`ScreenshotHelper`; that path must be verified separately.

## CPH2747 live result

`adb shell input keyevent 120` produced:

```
com.oplus.screenshot/LongshotCapture
```

No `DuoSB ... live event -> SCREENSHOT` line appeared. The AOSP executor class is present in the
OnePlus APK, but this key path is owned by the external `com.oplus.screenshot` process/activity.
So the existing `TakeScreenshotExecutorImpl` hook is **not the source for this OxygenOS gesture**.

## What this means

1. The shared hook is real for the six AOSP-derived UIs; use it as the primary source.
2. OxygenOS needs a second bridge for the Oplus screenshot session. Do not call the AOSP hook
   "working on OxygenOS" until `live event -> SCREENSHOT` is observed there.
3. OPPO 3.0.4 and Honor 11 are ancient maps; they are not evidence for current ColorOS/MagicOS.
4. Xiaomi's `ScreenshotHelper` must be checked before claiming Xiaomi support.
5. The Canvas branch was a separate bug; fixing it did not make the source fire.

## Recommended next step

- Keep the AOSP executor hook for six UIs.
- Add an Oplus screenshot bridge only after its class/method is measured (the external process is
  outside SystemUI; a naive hook there is not safe).
- Verify with the exact log line on CPH2747, not by taking a screenshot and guessing.
- No APK build from this audit.

## Evidence location

- `reverse/smali/<ui>/.../TakeScreenshotExecutorImpl.smali` (gitignored)
- `reverse/smali/xiaomi/.../ScreenshotHelper.smali`
- CPH2747 logcat line: `com.oplus.screenshot/LongshotCapture`
