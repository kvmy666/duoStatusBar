# Phase 1 Report — Shade / QS / Header Surfaces

Status: **closed and owner-approved on the measured OnePlus CPH2747 baseline** (2026-10-27).
This is not an 8-UI support claim.
Branch: `phase/1-shade-qs`; merged to main after owner approval.
Build reviewed: fix10, package `1.4.3-beta.1` (code 24).
Last updated: 2026-10-27.

## 1. Scope and result

Phase 1 fixed the three-surface Duo presentation on OxygenOS 16 / CPH2747:

- status-bar / header Duo;
- Quick Settings Duo;
- full notification-shade Duo.

The surface owner is no longer inferred from container attach order. It now follows SystemUI's own
panel state, and exactly one copy is visible at a time. The two optional surfaces can be disabled
independently without removing a live SystemUI view.

## 2. Problems and fixes

| # | Symptom | Root cause | Fix | Evidence |
|---|---|---|---|---|
| 1 | QS ring stayed 9 px / squashed | The vendor QS cluster was 9x63 inside the 63 px strip | Draw in the nearest taller QS ancestor; keep the main-bar size (114 px at 133%) | `qs header layout ... view=114x129`, device dump |
| 2 | Two Duo copies during QS entry/exit | Visibility was derived from whichever container attached last; QS overlay slots were not found by `liveSlotIn(stockTarget)` | `onShadeOrQsExpanded` + `ShadeExpansionStateManager` panel state; per-panel owner; one visible copy | fix8 owner/covered log |
| 3 | Shade copy disappeared (`alpha=0`) | Attach happened after the last layout; no later settle restored the copy | Show the owner on attach; panel callback is authoritative; no owner lock to null | fix8 `panel owner -> shade header` |
| 4 | Ring moved while 5G/indicators stayed fixed | `reapplyExtraHiding` called `applyExtraLayout` on every layout tick | Removed the per-layout re-anchor; the ring keeps its attach box; transition is hidden by the panel state | Phase 1 log, RC-013 |
| 5 | Hard pop/dropped frame returning to the header | Main Duo alpha was set to 1 in one frame while the ROM bar was still settling | Match the ROM's status-bar reveal: 320 ms, 50 ms delay, vendor `ALPHA_IN` cubic `(0.4, 0, 1, 1)` | `CollapsedStatusBarFragmentExImpl:472-473,611`; `Interpolators.java:96` |
| 6 | Safe-mode incident | A previous build forced `removeView`/`dropSlot` when a shade target changed | No live removal/reparent; detached-only drops; visibility uses alpha/stock restore; guarded hooks | `DuoIconHost` live-slot rules; no safe mode in later logs |
| 7 | QS 5G offset in the measured tree | Indicator view was posted before the final ring anchor | One delayed alignment pass after the attach (not a per-frame tracker) | 31 px offset in the QS tree; delayed alignment |
| 8 | Off/on or missing hook could leave a surface locked hidden | `panelStateKnown`/`panelOwner` survived teardown; a Boolean-hook-only fallback set owner to null | Reset panel state in teardown; `panelHookInstalled` gate; attach fallback when the per-panel hook is absent | Final review pass |
| 9 | QS/shade drew 175x198 while the header was 141x159; shade moved down | The extra used the tall overlay/window cap; a 1 px header shortfall selected the 688 px QS container | `SlotGeometry.extraSidePx` clamps every extra to the main bar side; a 10% shortfall (not 1 px) is required before moving out of the stock header | Device dump: header 141x159, QS/shade 175x198, shade parent 1170x688 |

## 3. SystemUI evidence

Measured on `corpus/SystemUI-device.apk` (CPH2747, sha256 `dbe67fd3…`):

- `com.android.systemui.shade.NotificationShadeWindowControllerImpl.onShadeOrQsExpanded(java.lang.Boolean)`:
  `true` at the start of QS/shade expansion, `false` when both close.
- `com.android.systemui.shade.ShadeExpansionStateManager.onPanelExpansionChanged(float, boolean, boolean, String, PanelType)`:
  `QS_PANEL` and `NOTIFICATION_PANEL` are separate; `SHADE_PANEL` is the aggregate.
- `PanelType` names: `NOTIFICATION_PANEL` (0), `QS_PANEL` (1), `SHADE_PANEL` (2).
- Live `dumpsys activity service com.android.systemui/.SystemUIService`: QS closed
  `PhoneStatusBarView alpha=1.0`; QS open `PhoneStatusBarView alpha=0.0`.
- The ROM's status-bar reveal: `CollapsedStatusBarFragmentExImpl` `ObjectAnimator/ValueAnimator` on
  `View.ALPHA`, **320 ms**, `Interpolators.ALPHA_IN`, **50 ms** start delay.
- `Agent/REPAIR-CATALOG.md` **RC-013** records the full method, hook and failure model.

## 4. Device verification

- fix8 hooks installed on the device:
  `shade/QS coverage hook installed`, `shade panel expansion hook installed`.
- QS open: `panel owner -> qs header`, `covered=true (main alpha=0)`.
- QS close: `panel owner -> collapsed`, `covered=false (main alpha=1)`.
- Full shade: `panel owner -> shade header`.
- No `FATAL EXCEPTION`, no safe mode, SystemUI alive after the test.
- The final build was delivered through the Telegram/catbox link after the phone had left the LAN.
  The owner installed it and confirmed the Phase 1 result on-device (2026-10-27). No fresh
  diagnostic dump was attached to that confirmation, so the approval is owner-confirmed visual
  evidence rather than a new measured dump.

## 5. Do the visibility hooks work on every UI?

**No — not yet.** The exact-name audit of the downloaded APKs is:

| UI | `onShadeOrQsExpanded` exact | 5-arg `onPanelExpansionChanged` + `PanelType` | Oplus vendor classes | Status |
|---|---|---|---|---|
| OnePlus OxygenOS 16 / CPH2747 | yes | yes | 3/3 | measured Phase 1 base |
| Samsung One UI 16 | yes | no (different expansion API) | 0/3 | hook 1 only; unverified |
| Xiaomi HyperOS 17 | yes | no (3-arg `FZZ`) | 0/3 | hook 1 only; unverified |
| vivo OriginOS 6 | yes | no (`ZFZ`) | 0/3 | hook 1 only; unverified |
| Motorola 15 | yes | no | 0/3 | hook 1 only; unverified |
| Nothing 15 | yes | no (`QS_PANEL` string only) | 0/3 | hook 1 only; unverified |
| OPPO 3.0.4 (old) | no | no | 0/3 | structural only |
| Honor 11 (old) | no | no | 0/3 | structural only |

What this means:

- The **main-bar cover** hook (`onShadeOrQsExpanded`) is present by exact name on the five modern
  non-OnePlus builds, but runtime installation/behavior is unverified.
- The **QS-vs-shade owner** hook only installs on the measured OnePlus signature. On the other
  modern UIs the module deliberately falls back to attach-time ownership; that does not yet make
  those QS/shade surfaces supported.
- `RomDetection`/`RomAdapter` still selects status-bar and shade-container maps per UI. The new
  visibility hooks are SystemUI-level, not yet gated or aliased per matrix row. Phase 2 must add
  per-UI aliases (or a matrix capability flag), not assume the OnePlus classes exist everywhere.
- `UI-MATRIX.md` remains the source of truth: only OnePlus has a measured Phase 1 shade/QS path.
  Xiaomi has a measured status-bar dump; its QS/shade path is still unverified. The remaining UIs
  are static APK maps only.

### Phase 2 alias targets (from the exact audit)

- Samsung: `ShadeExpansionListener`/`ShadeExpansionChangeEvent` path; no `PanelType`.
- Xiaomi: `ShadeExpansionStateManager.onPanelExpansionChanged(FZZ)`; no `PanelType`.
- vivo: `ShadeExpansionStateManager.onPanelExpansionChanged(ZFZ)`.
- Motorola: `ShadeExpansionListener`; no 5-arg method.
- Nothing: `(FZZ)`; no `PanelType`.

Each needs a per-UI QS/shade classifier, a runtime dump, and a matrix capability flag. A missing
exact token is not proof the function is absent; it is proof the current alias cannot install.

## 6. New user toggles

Two optional settings are now part of the app ↔ module contract:

- `showInQs` (default `true`) — draw Duo in Quick Settings.
- `showInShade` (default `true`) — draw Duo in the full notification shade.

Behavior when off:

- the Duo copy for that surface is hidden;
- the stock header icons for that slot are restored in place (`stock.restore()`);
- no live Duo is removed or reparented;
- turning it back on re-runs the hide pass and shows the owner copy;
- the columns are optional in the provider contract, so an older settings app cannot disable a
  surface by omission.

The switches are in the **Surfaces** section of the settings app. Persistence and the provider row
are covered by `SettingsChannelTest` (including the missing-column fallback to `true`).

## 7. Validation

- `:app:testDebugUnitTest`: **228 tests, 0 failures** (24 suites; includes the surface-toggle
  persistence and missing-column tests).
- `:app:assembleRelease`: `BUILD SUCCESSFUL`; final APK `6,754,152` bytes, SHA-256
  `d3ab2d2f79ca0aab778c24351898551f25223e7358d4ce3c51946ce98d8224b3`.
- No safe mode in the fix8 device log; the later 175 px size regression did not enter safe mode.
- The final build was installed from the Telegram/catbox delivery and the owner approved it.
  The final on-device visual test is therefore owner-confirmed; static build/tests are green.
  The phone was not reconnected for a post-approval diagnostic dump.

## 8. Phase 2 entry conditions

1. Keep Phase 1 behavior frozen on OnePlus; do not change the measured hooks without a dump diff.
2. Add a per-UI capability map (matrix) for the visibility hooks and vendor routes.
3. Obtain runtime dumps for Samsung/Xiaomi QS/shade, vivo, Motorola, Nothing, ColorOS and Honor
   before claiming support. Static APK names are leads only.
4. Implement aliases per the targets above; test one UI at a time.
5. New features remain off/unmapped by default; stock icons must survive every disable path.
