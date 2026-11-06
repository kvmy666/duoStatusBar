# Emulator call test — PASS

Date: 2026-10-31. AVD `ae_api36_root`, LSPosed module enabled, `duo_statusbar_stage=2`.

## Incoming call

Injected through the emulator console:

```
adb -s emulator-5554 emu gsm call 12345
```

Telephony/Telecom reached `RINGING`, then the module log showed:

```
DuoSB: phone state=RINGING callEnabled=true
DuoSB: live event -> CALL
```

After the test:

```
adb -s emulator-5554 emu gsm cancel 12345
```

## Meaning

- The `TelephonyManager.ACTION_PHONE_STATE_CHANGED` receiver works.
- The call event is enabled (`callEnabled=true`).
- `EventArbiter` accepted `CALL` and the renderer received `live event -> CALL`.
- The event is a real incoming call, not an API/state shortcut.

## Limits

- This proves the event path on the AOSP-derived emulator; each vendor's Telephony/SystemUI must still
  be checked before claiming all eight UIs.
- The visual shake/art was not pixel-compared; the event reached the renderer.
