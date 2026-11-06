# Phase 8 report — release readiness

Status: **code and gates complete; release is blocked on device/owner approval.** Branch
`phase/8-release`, from `phase/7-multi-ui-perf`.

Build: `1.5.0-beta.20`, versionCode 50.
APK: `7,095,720` bytes, SHA-256
`6541582bb19e67397d5658ae8b10a2e12a54aafd024fc829fcb74b68201e548d`.
Rive: `636e8c9a9a721cb982f5c451bdddc784004e890bc16201b2b42b15282b410e9b` (packaged == build).
Relay ZIP: SHA-256 `b1a601f28f65d30da893b0d0da4f70cc0f1ef327dae2429fd55d19d5b3479583`.

## Gates

| Gate | Result |
|---|---|
| Unit tests | **322 pass**, 0 failures, 0 errors, 0 skipped |
| Release assembly | **success** |
| Log routing | 0 files to fix, 0 `android.util.Log` calls outside `L` |
| Rive easing | 142/142 keyframes eased, 0 inert |
| `.riv` identity | build and packaged hashes match |
| Emulator launch (API 36) | success, no app crash; Settings reached |
| Emulator frame budget | **FAIL** — 119 frames, p95 216.88 ms, max 308.39 ms (budget 32/120); Android Settings baseline on the same emulator is p95 116.87 ms |
| Device (CPH2747) install/visual | **not done** (device offline) |
| Owner approval | **not given** |
| OEM asset removal | **not done** (prototype icons remain in `agent/event-icons/prototype/` only) |

## Release classification

- `1.5.0-beta.20` is a **pre-release**. Do not publish to the LSPosed store.
- Do not merge to `main` before the owner approves the visual result and the device run.
- The APK is safe to side-load for testing: the module remains off until the owner enables it, and the
  kill switch / safe mode paths are unchanged.
- The emulator performance failure is a gate failure, not a cosmetic warning. It is not waived.

## Open blockers, in order

1. **CPH2747 run:** install beta.20, enable the module, capture Home/Engine/Settings, selector idle →
   drag → release, both scroll ends, and `gfxinfo` after a deliberate Settings scroll.
2. **Frame budget:** if the device also exceeds p95 32 ms, keep the static control material and lower
   navigation blur/lens before release; do not ship a slow glass layer.
3. **Visual approval:** the selector must be a circle at rest and a capsule only while dragged; the
   owner has not signed this build off.
4. **Phase 6 sources:** mic/location/volume still have no measured source; do not advertise them.
5. **OEM icons:** prototype art must be replaced or explicitly accepted before a stable release.
6. **Phase 1 safe mode:** `fix/landscape-safe-mode` remains unmerged/unverified; do not claim a fix.

## Recommendation

Ship nothing stable yet. The beta is a review build. When the owner is awake, run the device gate,
attach the dump/screenshots to this report, and only then decide between `1.5.0-rc.1` and another beta.
