# Keyguard unlock hook — cross-UI class audit

Date: 2026-10-31. Static smali check of the eight corpus SystemUI APKs.

All eight contain the same class and both measured methods:

| UI | `StatusBarKeyguardViewManager` | `notifyKeyguardAuthenticated` | `onKeyguardFadedAway` |
|---|---|---|---|
| OnePlus 16.00.14-2 | yes | yes | yes |
| Samsung 16-11 | yes | yes | yes |
| Xiaomi 17-03 | yes | yes | yes |
| vivo OriginOS 16.0.7.194 | yes | yes | yes |
| Motorola 15.7 | yes | yes | yes |
| Nothing 15.0.176 | yes | yes | yes |
| OPPO 3.0.4 | yes | yes | yes |
| Honor 11.0.0.1 | yes | yes | yes |

This is why the same LSPosed hook can be applied without per-vendor class names. It does not prove the
runtime transition on each vendor ROM; that still needs a device/emulator run.

## Runtime proof

AOSP/API 36 emulator, real PIN:

```
keyguard unlock hook installed
keyguard faded away -> unlock fallback
unlock received enabled=true active=null
live event -> UNLOCK
```

Call source is system-wide `TelephonyManager.ACTION_PHONE_STATE_CHANGED`; emulator `gsm call` produced:

```
phone state=RINGING callEnabled=true
live event -> CALL
```

Screenshot is the shared `ScreenshotWatcher` file source; phone produced:

```
live event -> SCREENSHOT source=file:/storage/emulated/0/Pictures/Screenshots/...
```
