# Device report — OxygenOS 16 (Phase 0 evidence)

Everything below was **read from the device** (`adb logcat -s DuoSB`), never guessed (FR-26).
Raw capture: [`evidence/phase0-oos16.txt`](evidence/phase0-oos16.txt). Reproduce with
[`install.md`](install.md) §5.

| | |
|---|---|
| Device | OnePlus 15 · **CPH2747** |
| Build | `CPH2747_16.0.9.400(EX01)` · Android **16** (SDK 36) · OPlus `V16.1.0` |
| Display | 1272 × 2772 px · physical 560 dpi · **override 484 dpi** (`density=3.025`) |
| Root / framework | KernelSU (susfs) + **LSPosed 2.2.0 (7854)**, manager `org.lsposed.manager` |
| Module | `io.github.kvmy666.duostatusbar` loaded into `com.android.systemui` |
| **SystemUI uid** | **10266** — *not* 1000: SystemUI on this ROM has no `android.uid.system` shared uid, so the module runs with app-level privileges |

## Probe results

| Probe | Question | Result |
|---|---|---|
| P-00 | Does the module load into SystemUI? | ✅ `MainHook loaded into com.android.systemui`, classloader = `PathClassLoader[DexPathList[[zip file "/system_ext/priv-app/SystemUI/SystemUI.apk"]]]` |
| P-01 | Which hook targets exist? | ✅ **18 / 24** candidates found, with real method names (below) |
| P-02 | Real status-bar hierarchy | ✅ full tree with ids, classes and px geometry (below) |
| P-03 | Can SysUI load our native Rive library? | ✅ **VERDICT: Rive can render INSIDE SystemUI (3/3 libraries loaded)** — `libc++_shared.so`, `librive-android.so`, `libandroidx.graphics.path.so` from `/data/app/…/lib/arm64` |
| P-04 | Triggers | ✅ `ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, `ACTION_USER_PRESENT`, `ACTION_CONFIGURATION_CHANGED` all delivered to a receiver registered on the SystemUI `Application` |
| P-05 | Metrics | ✅ `status_bar_height=122px` (resource), window **141px** tall, icon row **90px** at y=38 |

Probe bugs found and fixed by this evidence: `getBoundsOnScreen` is hidden (now `getLocationOnScreen`);
Rive 10 ships `librive-android.so` (needs `libc++_shared.so` first); a depth cap of 6 hid the whole icon
strip; `WindowManagerImpl#addView(View,ViewGroup.LayoutParams,Display)` does not exist on this ROM (only
the 2-argument overload does); a 2 s timer-based report was silently lost, so P-01 is now synchronous.

## Status-bar view tree (measured, portrait, 1272 px wide)

```
StatusBarWindowView                                        1272x141 @0,0
  id=status_bar_container
    com.android.systemui.statusbar.phone.PhoneStatusBarView  id=status_bar   1272x141
      com.oplus.systemui.statusbar.seeding.CapsulePluginContainer id=seeding_card_container
        …CapsuleContainerRoot (OPPO's own "Capsule" island — must not be fought)      vis=8
      android.widget.LinearLayout id=status_bar_contents      1272x141
        id=status_bar_start_side_container                    585x90  @0,38
          id=status_bar_start_side_content                    128x90  @74,38
            HeadsUpStatusBarView id=heads_up_status_bar_view
            StartSideExceptHeadsUpLayout id=status_bar_start_side_except_heads_up   128x90
              FrameLayout id=clock_for_fake
                StatClock id=clock                            128x90  @74,38   <- the clock (kept)
              id=status_bar_start_side_content_for_fake       0x90    @202,38
                prompt_view_group / ongoing_activity_chip_primary  (Android 16 activity chips)
                AlphaOptimizedFrameLayout id=notification_icon_area
                  NotificationIconContainer id=notificationIcons      <- notification icons (LEFT side here)
        Space id=cutout_space_view                            102x141 @585,38   <- punch-hole cutout
        id=status_bar_end_side_container                      585x90  @687,38
          id=status_bar_end_side_container_for_fake           329x90  @859,38
            com.oplus.systemui.statusbar.widget.EndSideContentLayout
              id=status_bar_end_side_content                  329x90  @859,38
              android.widget.LinearLayout id=system_icons      329x61  @859,52   <- * ICON STRIP *
                StatusIconContainer id=statusIcons             246x61  @859,52
                  OplusModernStatusBarWifiView id=wifi_combo   70x61  @913,52   <- Wi-Fi
                  OplusModernStatusBarMobileView id=mobile_combo 61x61 @983,52  <- SIM 1
                  OplusModernStatusBarMobileView id=mobile_combo 61x61 @1044,52 <- SIM 2
                  (slot dots: StatIconView / SingleBindableStatusBarIconView / StatusBarIconView id=status_bar_dot)
                com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView
                  id=battery                                   83x61  @1105,52   <- * BATTERY *
                    ImageView id=battery_icon_view             79x48  @1108,58
                    TextView id=battery_percentage_view · id=battery_charge_icon · id=battery_text
                    ProgressBar id=stat_battery_view
      android.widget.LinearLayout id=dynamic_icon_group        9x141  @1200,0
        DynamicContentLayout id=dynamic_icon_content · AutoMarqueeTextView id=dynamic_icon_text
        id=privacy_chip_window > OplusOngoingPrivacyChip id=privacy_chip
```

### What this dictates

* **Insertion target:** `LinearLayout id=system_icons` (329 × 61 px inside `EndSideContentLayout`).
  Default slot for the Duo element = the **battery position** (`x 1105…1188`), an 83 px wide column, so an
  83 × 83 px square fits the reference element exactly.
* **Hide targets (FR-08):** `id=wifi_combo`, `id=mobile_combo` (×2), `id=battery`
  (`StatBatteryMeterView`), the `StatIconView` / `StatusBarIconView id=status_bar_dot` slot views and any
  other slot inside `id=statusIcons`. `EndSideContentLayout` and `StartSideExceptHeadsUpLayout` both expose
  `setHiddenIconListChanged(boolean, ArrayList<Rect>)` — the ROM's own hide mechanism.
* **Clock:** `StatClock id=clock` at `@74,38` on the left → stays (locked decision 1).
* **Notifications:** `NotificationIconContainer id=notificationIcons` sits on the **left** beside the clock
  on this ROM → FR-07 is a left-side question, not a right-side one.
* **The OPPO Capsule** (`CapsuleContainerRoot`) is a first-party island system. Duo never hooks or hides it;
  it simply must not collide (own container, own sizes).

## Hook targets confirmed by name (P-01)

| Purpose | Class | Real methods worth hooking |
|---|---|---|
| Icon visibility / animation | `com.android.systemui.statusbar.StatusBarIconView` | `setVisibleState(int)`, `setVisibleState(int,boolean)`, `isIconVisible()`, `getSlot()`, `setIconAppearAmount(float)`, `getIconAppearAmount()`, `onDarkChanged(ArrayList,float,int)`, `getIconScale()` |
| OEM icon plumbing | `com.oplus.systemui.statusbar.phone.StatusBarIconControllerExImpl` | `setIcon(String,int,int,CharSequence,StatusBarIconList)`, `onCreateStatusBarIconView(String,boolean)`, `updateStatusBarIconDrawable(...)` |
| OEM icon holders | `com.oplus.systemui.statusbar.phone.OplusIconController` | `onSetIconHolder(int, StatusBarIconHolder, ViewGroup)`, `addHolder(...)`, `setOplusIcon(String, DynamicIcon)`, `setNetWorkSpeedIcon(...)` |
| Battery state | `com.android.systemui.battery.BatteryMeterView`, `…policy.BatteryControllerImpl` | `getUnifiedBatteryState()`, `setBatteryDrawableState(BatteryDrawableState)`, `setUnifiedBatteryColors(BatteryColors)`, `getBatteryPercentViewText()`, `fireBatteryLevelChanged()`, `isBatteryDefender()` |
| Wi-Fi / mobile / airplane | `…connectivity.NetworkControllerImpl`, `…WifiSignalController`, `…MobileSignalController` | `setWifiEnabled(boolean)`, `updateAirplaneMode(boolean)`, `pushConnectivityToSignals()`, `hasWifiInternet()`, `isMobileDataNetworkInService()`, `getMobileDataNetworkName()`, `MobileSignalController.setAirplaneMode(boolean)`, `getCurrentIconId()`, `getNumLevels()` |
| Lock screen | `com.android.systemui.statusbar.phone.KeyguardStatusBarView` | `setVisibility(int)` |
| Dark/light tint | `…statusbar.StatusBarStateControllerImpl` | `createDarkAnimator()` |
| Shade | `…qs.QSPanel`, `…qs.QuickQSPanel`, `…shade.NotificationShadeWindowView` | `setVisibility(int)`, `setAlpha(float)`, `setTransitionAlpha(float)` |

**Confirmed absent on this ROM** (Phase 3 must not reference them): `CollapsedStatusBarFragment`,
`StatusBarIconControllerImpl` (both packages), `StatusBarIconList`, `DarkIconDispatcherImpl`,
`NotificationPanelViewController`.

## Axis conversions (density 3.025)

| px | dp | |
|---|---|---|
| 141 | 46.6 | window height |
| 90 | 29.8 | icon row |
| 61 | 20.2 | icon height |
| 329 | 108.8 | end-side cluster |
| 246 | 81.3 | `statusIcons` |
| 83 | 27.4 | battery slot |
| 70 / 61 | 23.1 / 20.2 | Wi-Fi / mobile |

## Consequences for the plan

1. **FR-04 is safe.** Rive renders inside SystemUI with real native code — animations are authored in Rive
   and drawn by the real runtime, no Canvas fallback needed (fallback stays documented in
   `dependencies.md` in case another ROM blocks native loading).
2. **FR-08 is achievable for real:** we hide the actual views/slots, not an overlay.
3. **FR-05 gets easier:** `NetworkControllerImpl.setWifiEnabled()` / `updateAirplaneMode()` and
   `MobileSignalController.setAirplaneMode()` give in-process toggles — actions can run without shelling out.
4. **SystemUI's uid (10266)** means no system-only APIs may be assumed; anything privileged goes through the
   same receiver route the sibling Auto Expand module already uses.
5. **Rotation:** the tree above is portrait; landscape gets its own dump in Phase 3 (same probe).

