# ROM contract — what the refactor must not change

Duo is a status-bar module that has to attach, hide icons and draw on ROMs we cannot test. Every
ROM-specific fix so far came from a user's **diagnostic dump**, so the probes, the hide logic and the log
strings are the product's contract. Refactors are **behavior-freezing**: they may move code, name things
better and add tests, but they must not change any of the following.

## Frozen surface

1. **Log strings and the `DuoSB` tag.** The diagnostic dump and the compact status line are what we fix
   ROMs from. No rewording, reordering, merging or removing. New lines may be added only inside the
   existing sections, and never on the attach/hide path.
2. **Container probe order.** `RomAdapter.containerIds` order (a measured profile from
   `assets/rom-profiles.json` is accepted first, but only when `measured`), then `stripAroundAnchors`
   (`batteryId`, `statusIcons`, `status_icons`, `system_icons`), then the additive `roleScan`
   (lowest common ancestor of the battery view and the `StatusIconContainer`), then
   `chooseElementParent`, `findComposeIconView`, `findBar`, `findShadeIconsArea`. `roleScan` runs
   only after the id and anchor paths fail, so a ROM that resolved before is unchanged.
3. **Reflection targets and their exact names/values.** `ActivityThread.mInitialApplication`,
   `mBoundApplication.appContext` (and **never** `mSystemContext` — its package is `android`, which the
   provider rejects), `WindowManagerGlobal.mViews` / `mView`, window types `2000`/`2040`,
   `TelephonyManager.createForSubscriptionId`, `StatusBarIconView.getSlot`/`getSlotTag`.
4. **Hooked class names.** `android.app.Application`, `com.android.systemui.SystemUIApplication`,
   `com.android.systemui.MiuiSystemUIApplication`, `android.view.WindowManagerImpl.addView`,
   `com.android.systemui.statusbar.phone.StatusIconContainer`,
   `com.android.systemui.statusbar.views.MiuiStatusIconContainer`, `ShadeHeaderController`, `ViewStub`,
   `StatusBarIconView.onDarkChanged` / `setIconColor`.
5. **Hiding semantics.** `GONE` + 0×0 for replaced icons; `INVISIBLE` (keep layout) for the measured
   anchor; never hide a strip the element is not drawing in; `restore()` puts everything back exactly.
6. **Stage precedence.** `duo_statusbar_stage` (adb override) → app settings → off. Keys unchanged.
7. **Settings provider contract.** Authority `io.github.kvmy666.duostatusbar.settings`, and the column set
   and order in `DuoPrefs.COLUMNS` / `DuoSettingsProvider.rowFor`. `COLUMNS` is now
   `PORTRAIT_COLUMNS + LANDSCAPE_COLUMNS` (landscape repeats portrait under the `land_` prefix, except
   `COL_REVISION`, which is shared). The app and module ship in one APK, so the order only changes when
   both sides change together; an older module ignores columns it does not know, and the old
   `COL_NETWORK_ONLY` key is still written for backward compatibility. Portrait column order may not be
   reordered without a matching migration.
8. **Rive.** `DuoBinder` property names and `PROPERTY_COUNT`, `scene.rml` binds, and
   `app/src/main/res/raw/duo.riv` must equal the scene build (CI `cmp`). The baseline after PR #11 adds
   the split groups (`ringGroup`, `indicatorsGroup`, `cellGroup`), the raised-percentage bind
   (`percentY`), the DND-dot moons (`moon1..4Opacity/Scale`, `centerMoonOpacity/Scale`), and the
   download wash (`downloadLeftEnd`, `downloadLeftOffset`, `downloadRightEnd`, `downloadRightOffset`,
   `downloadOpacity`, `downloadColor` — opacity 0 is the idle ring). Its optional linear-gradient
   variant adds `downloadGradientOpacity`, five colour/stop pairs, and four start/end coordinates;
   Kotlin keeps the solid and gradient opacities mutually exclusive. While a download asks for its
   percentage label, `percentText` is that progress and the gap opens even if the battery percentage
   is off. The
   `DuoPart` enum (`ALL` / `RING` / `INDICATORS`) selects which groups a surface draws. A refactor must
   not rename these binds.
9. **Fallback order.** Rive → Canvas; attach retry count/timings; overlay-vs-strip placement. A split
   layout (`splitIndicators`, default **off**) creates a second surface for the icon cluster; each
   surface falls back to Canvas independently, and the ring's surface is never rebuilt just because the
   cluster is added. The element view is `RingGeometry.elementHeightPx(side)` tall (not square) so the
   percentage can rise: `ringAnchorShiftY` keeps the ring at its original position, and the default
   `percentHeight = 100` maps to the same absolute text seat as the pre-PR square view
   (`77.5 - 76 = 61.5 - 60`). Moving either constant moves what every user sees.
10. **iOS mode (IOS-B1).** The iOS mode is additive and leaves the Duo surface untouched:
    `DuoBinder`, `PROPERTY_COUNT` (59), `rive/duo/scene.rml`, `rive/duo/scene_old.rml`,
    `app/src/main/res/raw/duo.riv`, the `Duo` artboard/state machine and Duo's attach/hide paths are
    unchanged. The iOS surface owns a separate `ios.riv` (artboards `Ios` battery, `IosWifi`, `IosCell`;
    state machine `Ios` in each), and `IosBinder` with `IOS_PROPERTY_COUNT = 12`; the frozen property
    table is `docs/ios-rive-contract.md`.
    One settings column is appended after `design_style`, in portrait and landscape: `ios_mode`
    (bool, default `0`). An older app omits it and the module falls back to that default; an older
    module ignores it. `rowFor` / `encoded()` stay positional, so the column is appended, never inserted.
    Gate: with `ios_mode = 1` and no adb override, `DuoGuard.stageFor(...)` resolves `OFF`, so the Duo
    element never draws. An explicit `duo_statusbar_stage` override (`0`/`1`/`2`) always wins (C10):
    override `2` means Duo draws and iOS stays suppressed; `duo_statusbar_stage 0` kills both. The iOS
    breaker owns separate keys (`duo_statusbar_ios_rive_attempts`, `duo_statusbar_ios_rive_attempt_at`)
    and never shares Duo's counters.
    `IosRenderers.DRAWING_READY == true`; `IosRiveView` (real Rive, internal Canvas fallback) and
    `IosCanvasView` draw the glyphs. The flag was flipped only after `ios.riv` was reproducible and
    covered by `tools/check-rive-shipped.sh` (rule 10); the Rive view implements C1/C2/C8/M1 and the
    host uses Canvas when the attached bar is not hardware accelerated. Suppression
    families and strategies are frozen by the measured device dumps (see docs/ios-geometry.md) §2.5;
    unknown or opaque families are skipped and the stock icon stays (fail silent). `Diag` adds one line to
    the `module:` section, `iosMode=... iosAttempts=...`; no existing line changes.

## Safety procedure for every refactor phase

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — green.
2. CI guards green: `route-module-logs.py --check`, `check-rive-eases.py`, authority grep, Rive `cmp`.
3. Install on a device, restart System UI, and **diff the diagnostic dump** against the baseline captured
   before the refactor. Expect an empty diff (ignoring timestamps and pids).
4. The dump is the evidence. A refactor that changes the dump is not a refactor.

### Capturing and diffing the dump

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$log = (& $adb shell "su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'").Trim()
& $adb shell su 0 cat $log | Set-Content .\modules.log
(Get-Content .\modules.log | Where-Object { $_ -match 'DuoSB \|' } |
    ForEach-Object { ($_ -replace '.*DuoSB \| ', '') }) |
    Set-Content .\diag-after.txt
Compare-Object (Get-Content .\diag-baseline.txt) (Get-Content .\diag-after.txt)
```

Timestamps and pids differ every run; filter them out before comparing (the dump body — build, adapter,
probes, view tree, readers — should be identical).
