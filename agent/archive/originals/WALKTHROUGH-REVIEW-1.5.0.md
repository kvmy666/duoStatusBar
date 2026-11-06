# Duo Status Bar 1.5.0 — walkthrough and code review

Date: 2026-10-31. Branch chain: `phase/5-liquid-glass` → `phase/6-rive` → `phase/7-multi-ui-perf` →
`phase/8-release`.

This is the walkthrough/review report requested after Phases 5–8. It is deliberately split from the
release report: `PHASE-8-REPORT.md` says whether the binary can ship; this file says what the code does,
what was checked, and what is still wrong. Fixes are sorted **critical → moderate → minor**.

## 1. Walkthrough

### Phase 5 — liquid glass shell (locked prototype)
- Start: Home/Engine/Settings use one floating bar with a three-tab selector.
- Tap a tab: center animates to the tab station; the selector is a circle there.
- Drag the selector: width grows to +42 dp, height gives 6 dp, shape stays a capsule; distance from the
  nearest station drives the stretch, so it grows and shrinks while crossing tabs.
- Release: spring to the nearest station; stretch springs to 0; the shape is a circle again.
- Glass: `GlassBackdrop` records `wallpaper` and `page` separately. Cards/controls sample wallpaper;
  the bar samples `rememberCombinedBackdrop(wallpaper, page)`; the selector combines that with the tab
  layer, so it refracts the page, pill and logos without sampling itself.
- Controls: every `SettingSwitch` and `LabelledSlider` routes through `GlassSwitch`/`GlassSlider`.
- Scroll: `ScrollEdgeGlass` masks a blurred copy with a `DstIn` gradient at the top 72 dp and bottom
  92 dp. On emulator/low quality it falls back to a static shade.
- Apple values: `GlassTokens` (40/24/12 dp blur, 1.5 saturation, 0.04 brightness, 1.0 contrast, 16/32
  lens, 28% highlight, 0/8/28 shadow at 14%) and `GlassQuality` for the emulator preview.

### Phase 6 — Rive animation system
- `ChargeIn` still flies the bolt from the middle to the ring gap.
- New `ChargeSettle` state (`0:261`) plays a 14-frame scale pulse (1 → 1.08 → 1) and then `ChargeBolt`.
  If the charger is removed mid-pulse, a 0-duration transition goes to `ChargeBolt`; its normal
  transitions then hide the bolt.
- `ChargeMotion` is the single source for the 1.6 s/1.833 s settle window, the 200 ms shake and the
  20 % crossing rule. Canvas uses it for the matching pulse; `DuoIconHost.shakeAll()` uses it for both
  renderers.
- `ChargeMotionTest` covers 19 → 20, 19 → 21, 20 → 20, 20 → 21, not charging, and a 19 → 40 jump.
- Headless evidence: `duo.png`, `charge-96.png`, `charge-110.png`.

### Phase 7 — multi-ROM and performance
- `rom-profiles.json` has 9 maps. `measured=true` only for OxygenOS and HyperOS; the other seven are
  static-only and cannot override the built-in detection.
- `RomProfilesTest` pins the count, the measured set, the unknown-Samsung fallback, and the OxygenOS
  shade map.
- `tools/check-perf.py` resolves gfxinfo columns by header, rejects empty captures, and applies
  p95/max budgets.
- Emulator run: full live glass caused an ANR; static controls/surfaces removed the ANR but still
  failed the frame budget. Details in `PHASE-7-REPORT.md`.

### Phase 8 — release
- `RELEASE-NOTES-1.5.0.md` written.
- `1.5.0-beta.20` built and relayed.
- The stable gate is **not** met: device run, performance and owner approval are open.

## 2. Code review findings

### Critical

1. **All-live glass ANR (fixed, needs device proof).**
   The emulator showed an ANR with live cards, live scroll edges and live controls. `GlassCard` now uses
   a translucent frost, `GlassQuality` turns controls/scroll/surfaces static on emulators, and the bar
   remains the one live surface on phones. The change is in `GlassSurface.kt`/`GlassControls.kt`; the
   device still must prove the phone path is fast enough.
2. **RC-017 recursive backdrop (fixed).**
   The wallpaper/page split is the structural fix. `GlassCard` no longer draws from the page layer, and
   controls inside the page sample only wallpaper. Do not "simplify" this back to one recorded layer.
3. **Emulator-only ART JIT crash (not a product crash).**
   The debug APK crashed in `art::JitCompileTask::Finalize` under the translated guest. Release beta.20
   launches and reaches Settings; this is an emulator/toolchain issue, not shipped code.

### Moderate

4. **Performance gate fails on the emulator — but the baseline fails too.**
   beta.20: 119 frames, p95 216.88 ms, max 308.39 ms. The Android Settings baseline on the same
   emulator is p95 116.87 ms, so Duo is ~1.86× the platform baseline; the emulator cannot prove the
   phone budget either way. Do not reinterpret this as a pass; CPH2747 remains the deciding run.
5. **Static ROM entries are not support.**
   Seven new maps are `measured=false`. `RomDetection` ignores them, and tests enforce that. Adding a
   device dump is the only way to promote one.
6. **Phase 6 still lacks mic/location/volume.**
   The charging and alarm/flashlight/screenshot motions exist. The other event sources and art do not;
   the report and UI must not imply they do.
7. **The selector has not been visually signed off.**
   The geometry is correct in code and the emulator screenshot shows a resting circle, but the owner
   has not dragged beta.20 on CPH2747. Treat the look as unverified.
8. **Phase 1 safe mode is still an open branch.**
   `fix/landscape-safe-mode` (`d24b448`) is unmerged and unverified. Do not include it in a release
   claim.
9. **OEM event icons remain prototypes.**
   They are not packaged, but a stable release needs owned art or an explicit owner exception.

### Minor

10. `GlassSurface` has two material paths (live and static) with duplicated tint/shadow code. They are
    intentionally kept separate for profiling; extract only after the device measurement.
11. `GlassControls` fallback switches use the Material control when no wallpaper is provided
    (previews/tests). That is intentional, but a screenshot test should pin both paths.
12. `check-perf.py` uses `FrameCompleted - IntendedVsync`, which includes idle gaps if the capture is
    taken without a deliberate scroll. Always scroll first; an idle capture is not a budget test.
13. `ChargeSettle` is an arrival pulse on the bolt, not a sweep over the green arc. The report says so;
    do not silently call it the full RIV-002 pulse.
14. The repo still contains literal `com.android.systemui` constants in AOSP fallbacks and
    `AutoExpand`; they are package identity, not adapter ids. The authority grep is clean.

## 3. Code quality observations

- **One owner:** `GlassTokens`/`GlassQuality` own material numbers; `ChargeMotion` owns charging
  timing; `RomProfiles` owns profile parsing. No duplicated magic numbers in the new code.
- **Fail-closed:** `check-perf.py` rejects empty input; `GlassCard` has a Material fallback; the
  emulator path does not throw.
- **Tests:** 322 unit tests; new tests cover the charge crossing and the shipped profile set. There is
  no UI screenshot test and no instrumented test for the selector.
- **Binary identity:** `rive/duo/build/duo.riv` and `app/src/main/res/raw/duo.riv` are byte-identical
  (`636e8c9a…`); 142/142 keyframes carry easing.
- **Logging:** route checker is clean; no `android.util.Log` outside `L.kt`.
- **Doc truth:** Phase 5 locked at beta.14; Phases 6–8 are prototypes with explicit open gates, not a
  stable release.

## 4. Evidence index

| Item | Path |
|---|---|
| Phase 5 report | `agent/PHASE-5-REPORT.md` |
| Phase 6 report | `agent/PHASE-6-REPORT.md` |
| Phase 6 headless poses | `rive/duo/build/duo.png`, `charge-96.png`, `charge-110.png` |
| Phase 7 report | `agent/PHASE-7-REPORT.md` |
| Emulator screenshots | `docs/evidence/phase7/beta18-*`, `beta20-*` |
| Emulator crash evidence | `docs/evidence/phase7/beta19b-crash.txt` |
| Phase 8 / release gate | `agent/PHASE-8-REPORT.md` |
| Release notes | `RELEASE-NOTES-1.5.0.md` |
| Walkthrough plan | `agent/PHASE-1-5-WALKTHROUGH.md` |

## 5. Next actions (ordered)

1. Install beta.20 on CPH2747; run the Phase 5/6/7 walkthrough on the device.
2. Capture a real `gfxinfo` after a deliberate Settings scroll and run `check-perf.py`.
3. If the budget fails on device, lower navigation blur/lens or keep static surfaces before release.
4. Promote a static ROM map only after its runtime dump; otherwise leave it `UNVERIFIED`.
5. Decide on OEM art and the Phase 1 safe-mode branch before any stable release.
6. Owner approval, then `1.5.0-rc.1`—not a store release.
