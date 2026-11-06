# Phase 1–5 walkthrough, review and gap-closure plan

Status: active plan. Owner: Abdulkarim. Written 2026-10-29.
Branch: `phase/5-liquid-glass` (from `phase/4-cell-accuracy`).

This file is the route from "the code builds" to "the five phases can be reviewed and signed off".
It is deliberately ordered: **evidence first, then fix, then re-measure**. No item is marked done from
reading code alone.

## 0. Ground rules

1. A phase is only "closed" when its requirement, its test case and its evidence row below all pass.
2. A device result is named with the build hash. An emulator result is named `emulator`.
3. Anything without a device/emulator capture remains `unverified`; do not promote it to "known".
4. Do not change a working ROM's dump except for the intended field. A changed dump is a regression.
5. The SystemUI module and the app are separate products: glass changes are app-only.
6. Never use a visual guess where a measurement exists (tokens, lens values, dp/px, tab geometry).

## 1. Evidence sources

| Source | Use |
|---|---|
| `agent/AGENT.md` | current branch, fatal rules, model routing |
| `agent/REQUIREMENTS.md`, `agent/TEST-PLAN.md`, `agent/PLAN-v2.md` | the phase contract |
| `agent/REPAIR-CATALOG.md` | symptom → cause → fix → verify |
| `docs/rom-contract.md` | frozen SystemUI surface |
| `agent/PHASE-4-REPORT.md` | measured cellular evidence |
| `agent/PHASE-5-PLAN.md` | glass dependency and ColorDuo measurements |
| `corpus/io.github.fengwenhua.colorduo/base.apk` | visual reference only (never shipped) |
| latest beta APK + `adb`/`uiautomator`/`dumpsys` | the only proof of device behaviour |

## 2. Phase walkthrough (in this order)

### Phase 1 — shade/QS ownership and restore

- [ ] Read the last shade dump and the LSPosed `DuoSB` lines; record device, ROM, stage, slot and size.
- [ ] Open shade and QS separately. Confirm exactly one Duo per surface and one stock icon set hidden.
- [ ] Toggle the master switch off; confirm the stock bar returns and no fresh Duo is injected.
- [ ] Rotate portrait → landscape → portrait; confirm no duplicate and no 0×0 slot.
- [ ] Capture `uiautomator dump` and `dumpsys window` for: shade, QS, keyguard.
- [ ] Re-run the safe-mode fix branch check only if the device shows LSPosed safe mode.
- **Gate:** one owner per window, stock restore verified, no forced remove/reparent in the path.
- **Known gap:** `fix/landscape-safe-mode` (`d24b448`) is not merged/verified.

### Phase 2 — renderer, Canvas parity, colour binding

- [ ] Force `engineMode=canvas`; verify Wi-Fi, cell, DND, airplane, clock, percent, charging bolt.
- [ ] Force `engineMode=rive`; repeat the same matrix.
- [ ] Change system light/dark and wallpaper; verify every foreground shape follows the bar colour.
- [ ] Verify the Canvas ring uses the same bound `leftArc`/`rightArc` as Rive.
- [ ] Capture `dumpsys gfxinfo` for one scroll and one reveal on both paths.
- **Gate:** every Canvas occupant exists in Rive and vice versa; no hard-coded track circle.
- **Known gap:** some middle-slot/event combinations are unit-tested but not device-shot.

### Phase 3 — events, Rive state, and app controls

- [ ] Alarm: set an alarm 1 minute ahead; capture scheduled state, ringing state, dismissal.
- [ ] Flashlight: toggle on, toggle off; verify icon shows only while the torch is on and fades after off.
- [ ] Screenshot: take one; verify the one-second transient and return to the previous middle state.
- [ ] Concurrent events: fire flashlight + screenshot; verify newest wins and no frozen stack.
- [ ] Check the event toggles in Settings are independent and survive process death.
- [ ] Verify event icon size follows ring size and stays inside ring bounds.
- **Gate:** event timeline, fade and middle-return match `agent/EVENT-ARCHITECTURE.md`.
- **Known gap:** OEM icon art is prototype-only; release needs owned art.

### Phase 4 — cellular accuracy

- [ ] Read `cell raw level=… levels=[…] generation=… max=… subs=[…]`.
- [ ] Compare dots to the stock status bar on SIM1, SIM2 and Auto.
- [ ] Exercise 5G/4G/3G/2G and airplane; verify no dot when airplane.
- [ ] Rotate and confirm the selected subscription still maps to the same slot.
- [ ] Run `tools/check-cell-report.py` on the captured report.
- **Gate:** measured `max/raw/levels` agree with dots on the device, both SIM slots.
- **Known gap:** per-ROM `maxSignalLevels` still falls back to 5; only one measured ROM.

### Phase 5 — liquid glass shell

- [ ] Open Home, Engine, Settings in one session; back stack returns correctly.
- [ ] Confirm the stationary selector is a **circle**, and dragging turns it into a **stretching capsule**.
- [ ] Confirm the pill is blurred at rest (12 dp) and stronger while held (16 dp).
- [ ] Confirm the selector is clear/blur 0, refracts page + pill + logos, and can overflow the pill.
- [ ] Confirm every switch and slider is glass (no Material Switch/Slider left in Settings).
- [ ] Scroll Settings to both ends; verify the progressive glass edges, no hard blur rectangle.
- [ ] Measure frame time with `dumpsys gfxinfo` while scrolling and dragging.
- [ ] Verify API 33 floor: no crash on a device without the shader path (lens is API 33+).
- **Gate:** visual sign-off, no RC-017 crash, `gfxinfo` within budget, strings English.

## 3. Review matrix

| Phase | Requirement / test | Evidence | State |
|---|---|---|---|
| 1 | one owner + restore (TP-0xx) | shade/QS dump + screenshot | device pending |
| 2 | Rive/Canvas parity | two-engine screenshot + gfxinfo | partial |
| 3 | alarm/flashlight/screenshot | live capture + logs | prototype merged |
| 4 | cell dots per SIM | `cell raw` + report check | measured, one ROM |
| 5 | glass shell, 3 pages | screenshot + gfxinfo | active |

## 4. Enhancement backlog (ordered by user value)

1. **Selector physics:** circle at rest, slime capsule while dragging, springy snap; keep the halo.
2. **Glass controls:** glass switch, glass slider, glass dropdown/menus if they remain Material.
3. **Scroll edge:** progressive mask at top/bottom; keep the settings list under it.
4. **Tokens:** one `GlassTokens` object; no literal `16f`, `40f` or random alpha at call sites.
5. **Readability:** verify light mode and low-contrast wallpapers; glass must not wash out labels.
6. **Performance:** cap card blur; no animated blur over a scrolling list; measure.
7. **Event art:** replace prototype OEM icons with owned art before release.
8. **Cell fallback:** read the device's real level count per SIM; no fixed 5.
9. **Report quality:** severity-sorted repair list (critical / moderate / minor) in every release note.

## 5. Gap-closure waves

### Wave A — build and visual baseline
- Bump to the next beta, run unit tests + release assembly, install on CPH2747 when ADB returns.
- Capture Home/Engine/Settings and a drag sequence (idle → drag → release).

### Wave B — selector and glass controls
- Fix selector resting shape and drag stretch from the captures.
- Replace remaining Material controls; tune tint to readable values.
- Re-capture; do not tune by memory.

### Wave C — scroll and performance
- Validate the masked scroll edge, then `gfxinfo`; lower card blur if frame time regresses.
- Add a simple budget test/checklist to `PHASE-5-PLAN.md`.

### Wave D — phase 1–4 evidence
- Re-run phase 1 shade/QS, phase 2 engine matrix, phase 3 events, phase 4 dual-SIM.
- File any failure in `REPAIR-CATALOG.md` with the severity bucket.

### Wave E — release gate
- String audit, `.riv` hash compare, log-route check, authority grep, APK hash.
- Owner visual approval; no merge to `main` before that.

## 6. Fix severity policy for reports

Every report must list fixes in this order, not chronologically:

- **Critical** — can crash SystemUI/app, hide all icons, lose settings, enter LSPosed safe mode, or
  mislead every user. Needs a rollback build and a device test.
- **Moderate** — visible wrong behaviour, wrong mapping, broken interaction, or a misleading report.
  Needs a targeted test and a screenshot/dump.
- **Minor** — duplication, stale docs, cosmetic spacing, unused imports, log noise. Batch these; never
  let them hide a critical item.

## 7. Current known holes (do not forget)

- Selector was a tab-width pill at rest; now changed to a circle + drag capsule, **not yet device-shown**.
- Glass cards previously fell back to Material to avoid RC-017; the new wallpaper/page split gives them
  real glass. The device must confirm no RenderThread crash and acceptable frame time.
- `ScrollEdgeGlass` uses a `DstIn` gradient; it is implemented, not visually verified.
- Light-mode text on the dark wallpaper needs a screenshot check.
- API 33 lens path is the only shipped path; no API 31 fallback because `minSdk=33`.
- Phase 4 is a prototype branch and is **not merged**; phase 5 contains it.
- Phase 1 safe-mode fix is separate and unverified.
- Owner approval is still required before any merge to `main`.

## 8. Exit definition

All five phases have a named build hash, a device/emulator capture, and a pass on their gate; the
severity-sorted report has no open critical item; and the owner has approved the visual result.
