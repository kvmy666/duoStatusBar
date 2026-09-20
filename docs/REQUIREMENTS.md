# Duo Status Bar — requirements & traceability

Status: `[ ]` todo · `[~]` in progress · `[x]` done & verified on device · `[!]` blocked
Target device (Phase 0 baseline): **OnePlus 15 · CPH2747 · Android 16 · OxygenOS 16.0.9.400(EX01) ·
KernelSU + LSPosed v2.2.0 (7854)**. Design spec: [`DESIGN-duo.md`](DESIGN-duo.md).

## Functional requirements (your FR list)

| ID | Requirement (condensed) | Phase | Status |
|---|---|---|---|
| FR-01 | Follow LSPosed / Xposed-Modules-Repo publishing standards (module meta-data, scope array, release mirror) | 0, 9 | `[~]` |
| FR-02 | Run on multiple custom ROMs, not one vendor only | 7 | `[ ]` |
| FR-03 | Everything the user can customise (size, shape, on/off) is exposed in the app settings | 5 | `[ ]` |
| FR-03b | Identical behaviour on home screen, inside apps, lock screen, portrait **and** landscape | 3 | `[ ]` |
| FR-04 | Animations powered by Rive | 2, 4 | `[~]` |
| FR-05 | Integration with Auto Expand (my other module): tap/double-tap/… actions from the status bar | 6 | `[ ]` |
| FR-06 | States: Wi-Fi, 4G/5G, DND, airplane mode, charging | 3 | `[ ]` |
| FR-07 | No conflict with live notifications — user chooses Duo, notifications, or both | 8 | `[ ]` |
| FR-08 | Stock battery / signal / Wi-Fi are **really** removed (not covered); silent mode and the rest hidden too | 3 | `[ ]` |
| FR-09 | Every setting in the app has an **infinite looping animation** explaining on/off behaviour | 5 | `[ ]` |
| FR-10 | Premium Material design, built on established frameworks (no hand-rolled UI kit) | 5 | `[ ]` |
| FR-11 | "Red Wine" primary colour with derived secondaries | 0, 5 | `[~]` |
| FR-12 | Best/easiest tech, prefer ready-made solutions over building from scratch | 0 | `[x]` |
| FR-13 | Derive the Duo element from existing open-source work (clone → understand → build) | 0, 1 | `[x]` |
| FR-14 | Research Apple's behaviour (blogs + Apple docs), e.g. exactly what happens when charging | 1 | `[x]` |
| FR-15 | State colours: saver = its colour, `<20%` = red, charging = green | 3 | `[ ]` |
| FR-16 | Optional charging/battery percentage in the middle, splitting the indicator in two halves | 3, 5 | `[ ]` |
| FR-17 | Live dragging so the user controls the position inside the status bar | 5 | `[ ]` |
| FR-18 | Tapping it triggers user actions **without** conflicting with Auto Expand; clear separation when both exist | 6 | `[ ]` |
| FR-19 | `plan` + requirements `.md` with checkmarks · `draw.io` architecture file · function-dependency file | 0, 1 | `[x]` |
| FR-20 | Use-case tests per feature, tested in many conditions; test features individually, not full regression | all | `[ ]` |
| FR-21 | Never break SystemUI: try/catch everywhere, fail silently | all | `[x]` |
| FR-22 | Plan split into small phases, executed incrementally / agile | all | `[x]` |
| FR-23 | Ambiguity is returned to you instead of being decided silently | all | `[x]` |
| FR-24 | Load the skills/plugins/MCPs needed, maximum freedom, minimum token cost | 0 | `[x]` |
| FR-25 | The unlock/lock animation set (scale-up → fill from 0 → shrink → bounce, ≤0.5 s, synced parts; Wi-Fi layers; spheres; airplane merge/spawn; middle-slot choice; shade header icons) | 4, 8 | `[ ]` |
| FR-26 | Never guess hook names — pull logcat / read the device | all | `[x]` |
| FR-27 | Read only the status-bar-relevant parts of the Auto Expand project | 0, 6 | `[x]` |
| FR-28 | Git + a commit per successful phase; simple README with images/GIFs, download count, bug report (issues + Telegram @kvmy1), Buy Me a Coffee via paypal.me/kroomfahd; in-app PayPal button | all, 9 | `[~]` |

## Non-functional requirements

| ID | Requirement | Status |
|---|---|---|
| NFR-1 | No measurable jank added to SystemUI: animations complete in ≤ 500 ms and run on the UI thread only | `[ ]` |
| NFR-2 | Battery impact: event-driven updates, no polling loops | `[ ]` |
| NFR-3 | Memory: the Duo view must stay a few KB; no bitmap allocation per frame | `[ ]` |
| NFR-4 | If the module fails, the stock status bar must remain usable (fail-silent + auto-disable after N strikes) | `[ ]` |
| NFR-5 | All prefs survive reboot and are readable inside the SystemUI process | `[ ]` |

## Decisions already locked (your approvals)

1. Status bar scope: clock stays, notification icons stay (toggleable); battery/Wi-Fi/cellular/silent/DND/BT hidden for real.
2. Dragging lives in an explicit **Edit layout** mode plus an in-app preview, so taps stay clean for Auto Expand.
3. FR-07 is implemented as Duo only / notifications only / both.
4. Setting previews are infinite **Rive** loops (always identical to the real animation); real `.gif` export on request.
5. Charging below 20 % shows **green** (iOS behaviour), not red.
6. Bold rounded font is bundled (not Apple's SF, for licensing reasons) with a "use system font" option.
7. Package `io.github.kvmy666.duostatusbar`, repo `duoStatusBar`, app name "Duo Status Bar".
8. Licence **GPL-3.0**.
9. ROM priority: OxygenOS 16 → AOSP/GSI emulator → HyperOS when hardware is available.

## Evidence log (Phase 0, device: CPH2747 / OOS 16.0.9.400)

Raw log: [`evidence/phase0-oos16.txt`](evidence/phase0-oos16.txt) · full analysis:
[`devicereport-oos16.md`](devicereport-oos16.md).

| Probe | Question | Result |
|---|---|---|
| P-00 | Module loads into `com.android.systemui` | ✅ (SystemUI uid = 10266, not 1000 — noted) |
| P-01 | Which required classes/methods exist on this ROM | ✅ 18/24 found with real methods; 6 names confirmed absent |
| P-02 | Real status-bar view hierarchy + ids | ✅ full tree; icon strip = `id=system_icons` → `statusIcons` + `id=battery` |
| P-03 | Can SystemUI load our native `librive.so` | ✅ **3/3 libraries loaded → Rive renders inside SystemUI** |
| P-04 | Screen-on / unlock / rotation triggers | ✅ all four broadcasts received |
| P-05 | Status bar height, density, orientation | ✅ 141 px window, 90 px icon row, 61 px icons, density 3.025 |
