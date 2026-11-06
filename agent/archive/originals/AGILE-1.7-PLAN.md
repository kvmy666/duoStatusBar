# Duo Status Bar — Agile 1.7 Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.7 |
| Date | 2026-11-03 |
| Requirements | `AGILE-1.7-REQUIREMENTS.md` |
| Tests | `AGILE-1.7-TEST-PLAN.md` |
| Baseline | 1.6.0 code 76 installed; fatal fixed |
| Status | **Plan only. No 1.7 code until the five open questions are answered and the requirements/test/plan traceability is approved.** |

## 1. What changed in the owner brief

- Fatal fix worked; behaviour improved but not battery-like.
- Heads-up/QS/shade transitions still snap; Duo disappears.
- New 8-dot ring is the right direction, but its battery bar is static and PHDP still uses the old design.
- Old ring design must return; user chooses old 4-dot / new 8-dot / camera / custom.
- Battery-like host/lifecycle is the proposed architecture.
- Home becomes the state/switcher/simulator; size and position bugs.
- Size mapping changes (new 180% ≈ old 130%, old 50% = new 0%, physical minimum >0).
- Position disappears after ~20 dp; requires reset.
- Per-icon status-bar hiding + All toggle; remove hide-all.
- Use supplied icons; rename Customization → Custom; Settings returns to pill.
- ColorDuo glass/touch mimicry, exact values via REA.
- Wi-Fi mapping and 4/8-dot cell accuracy from logs.
- Report blur; live preview charge/saver/8-dot/inversion; event GIFs.

## 2. Evidence baseline

- ColorDuo: `io.github.fengwenhua.colorduo` v1.1.7 (10107), Compose + libxposed, target 36; package pulled at `/tmp/colorduo-pull/…/base.apk`; screen at `/tmp/colorduo-screen/screen.png`.
- `NavigationDragState`: velocity, settle, travel, pressed, finger, crossing; `NavigationDragKt`; use same Kyant Backdrop API.
- Current Duo: 1.6.0 code 76, 8-dot static ring, battery line, liquid selector, 3 tabs; `docs/evidence/final-*.png`.
- 1.6 fatal evidence remains `reverse/fatal-logs/`.
- Current cell mapping: `SignalMapping.sphereOpacities` and `cellOpacities`; owner reports full→1/4/3 inconsistency.

## 2.1 A0 audit result — status icons / cell mapping

Read-only audit complete. Key facts:
- `hideOtherIcons` is a single boolean; it either hides all strip children (`hideAllExcept`) or only battery/Wi-Fi/mobile (`hideReplaced`).
- QS path `hideSlotStock` hides the whole `statusIcons` container regardless of the boolean; must be split before per-icon controls.
- `isReplaced` uses slot → id → class; class fallback can be broad. A dynamic keyed inventory is required.
- Battery remains `INVISIBLE` as the anchor; never GONE. Clock is outside `system_icons` on OOS (`StatClock`).
- Known OOS slots/ids: battery, wifi_combo, mobile_combo ×2, mobile_signal/mobile_volte/wifi_signal inner parts, status_bar_dot. One UI `CombinedStatusView` is a composite.
- Cell mapping uses `signalStrength.level` and `maxSignalLevels` with `ceil`; diagnostics mislabel mapped values as raw. Add raw/max/dots/source logs and a stock-level comparison before changing math.
- Wi-Fi uses RSSI→5 levels→3 display bars; no VoWiFi reader exists yet.
- Dynamic list keys: slot → id → class + occurrence; labels localized; unknown remains visible; hide/restore must call `restore()` then reapply.

Implementation split: A4a classifier/storage/hiding; A4b dynamic UI + All; A4c cell/Wi-Fi diagnostics and mapping correction.

## 3. Phases

### A0 — Requirements answers + measurements (blocking)

**Requirements:** BAT-01/05, RING2-05, GLASS2-04, HOME2-02, ICON2-03.  
**Weapons:** REA/JADX (`inspect_android_class`, `decompile`), `adb logcat`, `dumpsys`, `screencap`, `screenrecord`, ColorDuo APK, image measurement (ImageMagick).  
**Exit:** five answers; cell-level logs for 0–4; ColorDuo alpha/blur/rim/reflection values; battery view hierarchy; size formula.

### A1 — Battery-like lifecycle and transitions

**Requirements:** BAT-01…05, QA2-02.  
**Weapons:** REA, `adb logcat`, `dumpsys window`, 60 fps screenrecord, `DuoIconHost`/`DuoHook`, 1.6 P0 guards, `T-BAT-*`.  
**Tasks:**
1. Read the stock battery host and transition logs; document parent/visibility changes.
2. Choose safe host/override strategy; keep one cached view and no reparent storm.
3. Implement animation/visibility parity; remove early hide.
4. Test six directions ×20; A/B overlay vs host.
5. Keep P0 crash gates.

### A2 — Ring designs, battery, PHDP, cells

**Requirements:** RING2-01…08.  
**Weapons:** Rive CLI, `scene.rml`, `DuoMapping`, `SignalMapping`, Canvas, PHDP, logs, headless screenshots.  
**Tasks:**
1. Restore old 4-dot art; add new 8-dot art; selectable via Home state.
2. Bind battery line to level; PHDP uses selected design.
3. Fix circular battery line.
4. Re-map cell levels from logs; per-SIM 4/8 dots; tests.
5. Camera state only on main bar.
6. Canvas parity/hash/ease.

### A3 — Home states, size, position, live simulation

**Requirements:** HOME2-01…06, UX2-02.  
**Weapons:** Compose, `DuoPresetStore`, `DuoPrefs`, `DuoMapping`, `DuoRiveStill`, screenshot/measurement.  
**Tasks:**
1. Four Home states: Default(old), 2SIM(new), Camera, Custom; persist; preview.
2. Re-base size formula; unit table; actual minimum >0.
3. Fix position drag range/pointer capture; no reset.
4. Add preview controls: charging, saver, 8-dot custom, centre inversion.
5. Event previews; no continuous idle loop.

### A4 — Navigation, icons, status-bar per-icon controls

**Requirements:** NAV2-01/02, ICON2-01…04.  
**Weapons:** Compose, `StockIconHider`, status-bar slot inventory, `Settings.Secure`/`Global`, adb, vendor APKs via REA/JADX.  
**Tasks:**
1. Rename Custom; add Settings to pill.
2. Use supplied icon assets.
3. Enumerate stock icon slots; per-icon hide; All toggle next to header.
4. Fix Wi-Fi outside ring; ensure Duo art not hidden.
5. CPH2747 test; other UIs unverified.

### A5 — ColorDuo glass and touch

**Requirements:** GLASS2-01…04.  
**Weapons:** REA/JADX on ColorDuo, runtime screenshot sampling, Kyant Backdrop docs, Compose, screenrecord.  
**Tasks:**
1. Extract exact alpha/blur/rim/reflection/shadow values.
2. Match selector effects and dimensions.
3. Match press/drag/velocity/settle/haptics.
4. Side-by-side proof; no label blur.

### A6 — Report, event GIFs, polish

**Requirements:** UX2-01…03.  
**Weapons:** Compose, ImageMagick/Rive GIF, screenshot.  
**Tasks:**
1. Move report title below blur; reduce overlap.
2. Add four event previews.
3. Verify no duplicate settings.

### A7 — Test/release

**Requirements:** QA2-01…04.  
**Weapons:** full unit/Compose/Rive/device suite, 1.6 P0 suite, screenrecord, emulator, phone install, `review-strict`.  
**Exit:** all P0/P1 tests pass, no fatal, owner approves, final APK installed on CPH2747, rollback retained.

## 4. Dependencies / waves

- A0 blocks all.
- A1 and A2 overlap `DuoIconHost`/Rive; serial by file.
- A3 can start after A0/A2 design IDs; uses Home files.
- A4 is separate UI/hook files but needs A3 navigation.
- A5 is independent after A0; can parallel with A4.
- A6 waits for A3/A5.
- A7 last.

## 5. Risks

| Risk | Mitigation |
|---|---|
| Battery host override destabilizes SystemUI | A0 evidence; A/B; one cached view; P0 guards; rollback |
| Transition fix changes stock battery | Only wrap/observe; never mutate stock layout unless measured; screenshots |
| Old+new Rive art grows .riv | Shared geometry; verify size/ease/hash |
| Size formula wrong | Owner answer; unit table; measured screenshots |
| Per-icon slots differ by vendor | Slot inventory; fail-silent; unverified others |
| ColorDuo copying/licensing | Reimplement values with own code; no copied assets |
| Cell mapping wrong | Log-driven table + per-phone tests |
| Position pointer loss | Compose pointer capture; device test |
| Nav 4th item crowds | Responsive labels; owner decision |

## 6. Decisions locked 2026-11-03

1. Size: logical 0…100, `physical = 50 + 1.7 × logical`; 0=50, 100=220; old/new icons optically normalized.
2. Battery hide: hiding the stock battery hides only the stock icon; Duo's battery and invisible anchor stay.
3. Per-icon list is dynamic from actual SystemUI slots.
4. Custom starts blank; ready presets are editable templates. Whole centre icon moves; Wi-Fi can be detached and pinned; charge number can move; size/rotation editable.
5. Camera is a fixed toggle: ring around cutout, Wi-Fi outside visible, size/rotation editable; QS/shade/keyguard normal.
6. Settings is the fourth pill tab.

No unresolved owner question remains. A0 evidence still measures ColorDuo values, transitions, and cell mapping.

## 7. Approval

No 1.7 code starts before answers + requirements/test/plan review. The 1.6 installed build remains the rollback baseline.
