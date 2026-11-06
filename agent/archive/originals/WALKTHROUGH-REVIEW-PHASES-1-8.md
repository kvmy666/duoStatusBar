# Walkthrough & review — Phases 1–8

Date: 2026-10-31. This review matches the phase plan against the current branch and evidence. It does not
promote unverified vendor claims.

## Phase status

| Phase | State | Evidence / gap |
|---|---|---|
| 1 shade/QS ownership | partial | measured OnePlus/HyperOS paths; no fresh multi-ROM dump; safe-mode branch still unmerged |
| 2 renderer/Canvas parity | partial | Rive + Canvas shapes exist; full per-state side-by-side matrix not captured |
| 3 event prototype | done prototype | alarm/flashlight/screenshot; screenshot now has a working file source |
| 4 cell accuracy | prototype | mapping/logs/report; dual-SIM device run still the gate |
| 5 liquid glass | **locked** | beta.14; selector, controls, scroll, tokens; no further retune |
| 6 Rive motion | partial | charge settle + 20% shake + lock/call art and hooks; mic/location/volume missing |
| 7 multi-UI/perf | partial | 9 static profiles, perf tool; emulator p95 fails, device pending |
| 8 release | prepared | beta.36 built/installed; stable release blocked on device/perf/owner |

## New emulator evidence

### Lock → home: PASS
- Module enabled in LSPosed, scope `com.android.systemui`, `duo_statusbar_stage=2`.
- `keyguard unlock hook installed`
- `keyguard faded away -> unlock fallback`
- `unlock received enabled=true active=null`
- `live event -> UNLOCK`

This is the transition from the lock screen to home, not an unlock API call.

### Incoming call: PASS
- `adb emu gsm call 12345`
- `phone state=RINGING callEnabled=true`
- `live event -> CALL`

### Screenshot: PASS (phone)
- `KEYCODE_SYSRQ`
- `live event -> SCREENSHOT source=file:/storage/emulated/0/Pictures/Screenshots/...`

## What was fixed in this round

- Screenshot source: shared `ScreenshotWatcher` across public screenshot dirs; no vendor-only hook.
- Keyguard: `StatusBarKeyguardViewManager` hook, verified on the emulator.
- Call: `CALL` event, phone-state receiver, Rive art/state, Canvas clone, settings switch.
- Lock/call art: owned drawings, dynamic Duo color.
- Flashlight: size/glow/timing iterations; layered glow (not true gradient).
- Alarm: owned clock with bells/hands/legs.
- Screenshot art: brackets + camera + lens; Canvas duplicate branch removed.

## Requirements review

### Done / verified
- UI-013 icon extraction (phone + 8 UIs, 5,353 files) with manifest and candidate list.
- Screenshot source (file watcher), lock transition, incoming-call event on the emulator.
- RIV-003 20% shake, ChargeSettle, alarm/flashlight/screenshot art.
- `check-perf.py`, profile asset checker, log route, Rive easing.

### Partial
- UI-001/002/016: six AOSP-derived screenshot classes; only two measured ROM maps.
- UI-014: Canvas parity for new lock/call art exists, but no full state screenshot matrix.
- CUS-005 icon packs: extracted assets, no picker.
- RIV-002: bolt flight + settle pulse, not the full green-arc 0→level sweep.
- RIV-005: parity implemented, not visually compared on device.
- Font/per-UI runtime, CUS-004 layout options, CUS-008 help previews: missing.
- PERF: emulator p95 216.88 ms vs 32 ms budget; baseline also fails (116.87 ms). Device run required.
- LIVE-001/002: mic/location/volume sources still absent.
- Phase 1 safe-mode: unmerged.

### Release gate
- beta.36 installed on CPH2747; no stable release.
- Owner approval and device visual/perf run remain.

## Fix severity

### Critical
1. RC-017 recursive backdrop — structural fix (wallpaper/page split).
2. All-live glass ANR — adaptive quality; no ANR in later build.
3. Screenshot was not firing on OxygenOS — now file watcher proves the event path.

### Moderate
1. Rive draw order hid the toggle/head line — body must be last in this scene.
2. Keyguard broadcast didn't arrive — replaced with measured Keyguard hook.
3. Call source was untested — now passes with emulator GSM.
4. Emulator frame budget fails; device unknown.
5. Seven ROM maps static-only.
6. Mic/location/volume sources missing.
7. Flashlight glow is layered, not a true gradient/blur.

### Minor
1. `gfxinfo` header parsing was wrong; fixed.
2. Rive tool path typo; fixed.
3. Many size/timing iterations; logged in `ICON-MISTAKES.md`.

## Conclusion

The lock/call/screenshot event paths are now verified on the emulator/phone. The release is still not
approved: other-vendor runtime checks, mic/location/volume, true glow gradient, performance and owner
visual sign-off remain.
