# Duo Status Bar 1.5.0 — beta.20

This is a pre-release for testing, not a store release.

## Highlights

- **Circle/slime selector.** The floating navigation selector is a circle at rest, stretches into a
  capsule while dragged, and springs back to a circle on the nearest tab.
- **Liquid-glass controls.** Every settings switch and slider is glass, with a wine selected state and
  a glass thumb. The settings list has masked glass edges instead of a hard cut.
- **Charging motion.** The bolt gets one arrival pulse at the ring gap; crossing into 20 % while
  charging triggers a short 200 ms shake. Both renderers share the same timing.
- **ROM maps.** Nine profiles ship: OxygenOS and HyperOS are measured; AOSP, ColorOS, One UI,
  OriginOS, MagicOS, MyOS and Nothing OS are static-only and stay `UNVERIFIED`.
- **Performance tooling.** `tools/check-perf.py` validates a gfxinfo capture against the frame budget
  and fails on empty input.

## Verification

- 322 unit tests pass.
- Release build succeeds.
- `duo.riv` is byte-identical to the fresh scene build
  (`636e8c9a9a721cb982f5c451bdddc784004e890bc16201b2b42b15282b410e9b`).
- APK SHA-256:
  `6541582bb19e67397d5658ae8b10a2e12a54aafd024fc829fcb74b68201e548d`.

## Known limitations

- The API 36 emulator does **not** meet the frame budget after a Settings scroll (p95 216.88 ms,
  budget 32 ms). CPH2747 has not been measured yet. Do not treat the emulator as a performance pass.
- On the emulator the app uses a static glass preview; phones keep the live lens path.
- Microphone/location/volume event sources are not implemented.
- Prototype OEM event icons are not packaged; release art is still pending.
- Android 13+ (minSdk 33), LSPosed, root.

## Install

1. Install the APK.
2. Enable **Duo Status Bar** in LSPosed and tick **System UI**.
3. Restart System UI, then enable the module in the app.
4. If anything goes wrong, set `duo_statusbar_stage` to `0` or use the app's kill switch.
