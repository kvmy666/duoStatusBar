# Function dependency map (FR-19)

The point of this file: when something breaks, know **what else is wired to it** so the fix is cheap.
Read it top-down for design, bottom-up for debugging.

## 1. Runtime data flow inside the SystemUI process

```
BatteryManager / BroadcastReceiver(ACTION_BATTERY_CHANGED)
      └─> DuoStateBinder.battery() ──┐
WifiManager (WIFI_STATE / RSSI)       │
      └─> DuoStateBinder.wifi()    ──┤
TelephonyManager (signal / 4G-5G)     ├─> DuoState (immutable snapshot)
      └─> DuoStateBinder.cellular()─┤        │
Settings.Global.airplane_mode_on      │        ├─> DuoRiveView.inputs()   (Rive state machine)
      └─> DuoStateBinder.airplane()─┤        ├─> DuoRiveView.palette()   (green/yellow/red)
NotificationManager (DND)             │        └─> DuoGestureBridge.actions()
      └─> DuoStateBinder.dnd()    ──┘
```

## 2. Component → depends on → breaks what if it fails

| Component | Depends on | If it fails, what stops working | Fail behaviour required |
|---|---|---|---|
| `ProbeHook` / `ProbeReport` (Phase 0) | SystemUI classloader, `Application.onCreate` | Evidence collection only | log + continue (never throws) |
| `DuoIconHider` (FR-08) | `StatusBarIconControllerImpl` (real method names from P-01) | Stock icons stay visible → the Duo element overlaps them | do **not** draw the Duo element either; log; auto-disable after N strikes |
| `DuoInjector` (FR-03) | status-bar view found via `WindowManagerImpl.addView` (P-02), plus `KeyguardStatusBarView` for the lock screen | nothing on screen | log; keep stock status bar intact |
| `DuoRiveView` (FR-04) | `librive.so` loadable in SysUI (P-03) + `.riv` asset readable from the module APK | animation missing | fall back to a static vector draw (or nothing), never crash |
| `DuoStateBinder` (FR-06/15) | the four system services above; the receivers must be cancelled on disable | wrong/blank values | last known value, no throw |
| `DuoGestureBridge` (FR-05/18) | `GestureDetector` on the Duo view + Auto Expand coexistence flag | taps do nothing | log; Auto Expand keeps working untouched |
| `PrefsBridge` (NFR-5) | prefs file written by the app + readable inside SysUI | settings appear ignored | ship compiled-in defaults |
| `SafetyGuard` (FR-21/NFR-4) | every hook registers here | a fault becomes a bootloop | hard rule: all hooks go through `L.guard{}` |

## 3. Cross-module contracts

| Contract | With | Rule |
|---|---|---|
| Status-bar tap zones | Auto Expand (`io.github.kvmy666.autoexpand`) | Duo owns taps **on the Duo element only**; Auto Expand keeps its cutout zones. Both read the same "coexistence" flag; neither consumes an event the other dispatched. |
| Prefs storage | Auto Expand | Different file (`duo_prefs.json`) — never share `tweaks_prefs.json`, so a bad write cannot corrupt the sibling module. |
| LSPosed scope | LSPosed | `com.android.systemui` only. Adding `android` (system_server) is forbidden: it is what breaks the screenshot chord in the sibling module. |
| Rive assets | Rive CLI project | The `.riv` used in the status bar is the same file the app previews (FR-09) — no second source of truth. |

## 4. Ordered debug playbook (cheapest → most expensive)

1. Read the module log — LSPosed's own file, `/data/adb/lspd/log/modules_<boot>.log` (lines prefixed
   `DuoSB |`), or `./tools/duo-verify.ps1`. `adb logcat -s DuoSB` is **not** enough on OxygenOS: logd there
   drops `android.util.Log` output from SystemUI entirely. Every guard logs
   `<Component> FAILED -> <exception>`; the first line is almost always the cause.
2. Re-run the Phase-0 probes (P-01 inventory) — after a ROM update the class/method names are the
   first thing to change; a `P-01 MISSING` line explains a whole dead feature.
3. `P-02` hierarchy dump — if the Duo element is not visible, this shows whether the container existed,
   its size (0 px = wrong container) and which sibling holds the stock icons.
4. `P-03` — only relevant to animation: if `librive.so` failed to load, everything else still works.
5. Disable the module in LSPosed Manager → the stock status bar must return instantly; this is the
   guaranteed rescue path and is verified in every phase.
