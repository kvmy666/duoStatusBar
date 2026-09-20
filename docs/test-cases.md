# Use-case test cases (FR-20)

Rule: **test the feature you just touched, individually.** Only re-test the connected ones listed
under "Linked tests". Every case is run on the target device and its result is recorded here.

Legend: `PASS` / `FAIL` / `SKIP`. "Stock bar OK?" means: with the module disabled in LSPosed, the
original status bar is untouched.

## A. Phase 0 — evidence

| ID | Case | Steps | Expected |
|---|---|---|---|
| A-1 | Probe loads into SystemUI | install APK, enable module, scope System UI, reboot, `adb logcat -s DuoSB` | `P-00 uid=1000` + `MainHook loaded into com.android.systemui` |
| A-2 | Class inventory | same log | `P-01 inventory: n/18` with a FOUND/MISSING line per class |
| A-3 | Status-bar hierarchy | same log | `P-02 STATUS_BAR window view: …` + a ≥5-level tree with ids and sizes |
| A-4 | Rive verdict | same log | `P-03 VERDICT: …` (either SUCCEEDED, or missing → documented fallback) |
| A-5 | Triggers | screen off/on, unlock, rotate | one `P-04 EVENT …` line each |
| A-6 | No SysUI damage | use the phone normally for 10 min | no crash dialog, no blank status bar, no `FATAL EXCEPTION` in logcat |
| A-7 | Rescue path | disable module in LSPosed, reboot | stock status bar returns exactly as before |

## B. Icon hiding (FR-08) — Phase 3

| ID | Case | Conditions | Expected |
|---|---|---|---|
| B-1 | Battery gone | home screen | no stock battery, Duo element present |
| B-2 | Wi-Fi / cell gone | Wi-Fi on, mobile data on | stock icons absent, Duo shows both |
| B-3 | Other icons gone | silent mode, BT on, alarm set, DND | those icons absent too |
| B-4 | Inside an app | open a full-screen app | identical to B-1 |
| B-5 | Lock screen | lock the phone | identical, no leftover stock icons |
| B-6 | Landscape | rotate in an app | Duo repositioned correctly, stock icons still hidden |
| B-7 | Notification icons | a notification arrives | notification icon still visible (clock + notifications kept, per locked decision 1) |
| B-8 | Truly removed | tap where the battery used to be | nothing happens (not an invisible overlay) |

## C. State & colours (FR-06/15/16) — Phase 3

| ID | Case | Conditions | Expected |
|---|---|---|---|
| C-1 | Percentage on | setting ON | two digits in the ring gap, ring split in two halves |
| C-2 | Percentage off | setting OFF | continuous ring, no gap (matches reference image 1) |
| C-3 | Charging | plug in | green fill, bolt in the narrower gap |
| C-4 | Charging + saver | saver ON while charging | green wins (locked decision 5) |
| C-5 | Saver | saver ON | yellow fill |
| C-6 | Critical | battery `< 20 %`, saver off | red fill |
| C-7 | Wi-Fi levels | move far/near the AP | arcs fill bottom→up 1→3, layer by layer |
| C-8 | Cellular levels | weak/strong signal | spheres light 1→4 |
| C-9 | DND | enable DND | configured middle-slot / badge behaviour |
| C-10 | 4G vs 5G | toggle preferred network | value/text reflects the real network type |

## D. Animation (FR-25) — Phase 4

| ID | Case | Expected |
|---|---|---|
| D-1 | Unlock animation | scale 1→1.12 (~100 ms) → fill 0→N (~200 ms) → 1.05 (100 ms) → spring bounce to 1.0; total ≤ 500 ms |
| D-2 | Screen-on without unlock | same animation |
| D-3 | Lock screen appearance | same animation, not doubled |
| D-4 | Parts synced | ring, Wi-Fi arcs, spheres, digits start and end together |
| D-5 | Airplane on | Wi-Fi arcs merge into the dot, plane scales from 0 inside and settles |
| D-6 | Airplane off | reverse morph |
| D-7 | Middle-slot choice | with Wi-Fi + cell + airplane all active, the chosen item occupies the slot |
| D-8 | No jank | no visible stutter; `dumpsys gfxinfo` shows no repeated > 16 ms frames during the animation |

## E. Settings app (FR-03/09/10/11/16/17) — Phase 5

| ID | Case | Expected |
|---|---|---|
| E-1 | Every toggle has a preview | each setting row plays an infinite animation matching the real behaviour |
| E-2 | Round trip | change a setting, reboot → the status bar reflects it |
| E-3 | Drag editor | drag position → persists after reboot, no overlap with the clock |
| E-4 | Reset / defaults | one action restores every default |
| E-5 | Diagnostics | shows hook health + last errors; log export works |
| E-6 | Donate | PayPal button opens `paypal.me/kroomfahd` |
| E-7 | Red Wine theme | primary + derived secondaries applied consistently, light & dark |

## F. Auto Expand integration (FR-05/18) — Phase 6

| ID | Case | Expected |
|---|---|---|
| F-1 | Duo tap → action | configured action fires once |
| F-2 | Auto Expand zones unaffected | its single/double/triple/long gestures still fire on the cutout zones |
| F-3 | No double dispatch | one tap never triggers two actions |
| F-4 | Duo disabled | Auto Expand behaviour identical to before Duo existed |
| F-5 | Auto Expand disabled | Duo gestures still work |

## G. Robustness (NFR-1…5, FR-21)

| ID | Case | Expected |
|---|---|---|
| G-1 | Kill the module's own hook paths | SystemUI keeps running; log shows the guard message |
| G-2 | Auto-disable | after the configured number of failures the module stops and stays off after reboot |
| G-3 | ROM update simulation | rename-based test on a copied build → `P-01 MISSING` instead of a crash |
| G-4 | Battery impact | idle 8 h → no measurable extra drain beyond the stock bar |
| G-5 | Memory | the Duo view stays a few KB, no per-frame allocation |

## Linked tests (re-run together)

* **B ↔ C ↔ D**: hiding, state and animation share the same view → re-run B-1, C-1, D-1 together.
* **F ↔ B**: gesture handling and hit-testing share the Duo view's bounds.
* Everything else is independent: a change in E (settings UI) never requires re-running C or D.
