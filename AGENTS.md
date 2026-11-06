# AGENTS.md — how we work on Duo Status Bar

> **Session anchor (read first):** [`agent/AGENTS-V2.md`](agent/AGENTS-V2.md) · the old agent docs are archived under `agent/archive/` · the tool catalog lives in `weapons/`.

This file records the mistakes this project has already made, so they are not repeated, and the working
rules that keep them from coming back. Read it before changing the module, the app or the Rive scene.

The short version: **evidence over guessing, reproduce before fixing, never be certain without proof,
and never let a mistake reach the user's System UI.**

---

## Working agreement (the approach everyone follows)

1. **Never guess.** Hook names, resource ids and class names are read off a device or out of the
   SystemUI APK, never invented. `docs/rom-contract.md` holds the measured/frozen surface. A profile or
   adapter that was not measured is marked `unverified` in its `notes`, and that word is a promise, not
   decoration.
2. **Reproduce before fixing.** A bug report needs the diagnostic dump (or a logcat capture) that shows
   the failure. If the report is empty, fix the reporting first — an invisible failure is not fixable.
3. **No blind fixes.** Change one thing, then prove it: run the tests, install on the device, and
   **diff the diagnostic dump** before and after (`docs/rom-contract.md`). A changed dump on a working
   ROM means the "fix" changed behaviour, and that is a regression.
4. **No overconfidence, and no always being right.** Write down what is still unknown ("unverified",
   "not confirmed on device", "needs a report"). When two explanations fit, say so and test which one.
   A wrong belief that sounds confident costs the users a broken status bar.
5. **Fail silent, keep the bar alive.** Every hook is guarded; a failure leaves the stock bar working
   and the module disabled, never half-attached. Native faults cannot be caught in-process — the Rive
   breaker and the kill switch exist for those.
6. **Defaults must not change.** A new feature is off, or maps to exactly the old pixels. Prove it with
   a test or a measured number (see the percentage-seat guard).
7. **One property, one owner.** Nothing keyed by two simultaneously-active animations; geometry in
   Kotlin, easing in Rive.
8. **Test the feature you touched**, individually, then the linked ones (`docs/test-cases.md`), not the
   whole world.
9. **No secrets in the APK.** The bot token never ships; uploads go through the relay. Never commit
   `.env`, keystores or tokens.
10. **Verify the binary.** `app/src/main/res/raw/duo.riv` must be byte-identical to a fresh build of
    `rive/duo/scene.rml` (`rive rive/duo --once` + hash compare). A drawing nobody can read is a
    supply-chain risk.

If a change cannot be reconciled with this file, stop and ask; do not "just make it work".

---

## Mistakes registry

### Critical — can break System UI, lose icons, or mislead every user

| # | Mistake | What happened | The rule it produced |
|---|---|---|---|
| C1 | `Rive.init` left `defaultRendererType` null | ReLinker looked in SystemUI's package, threw, and the static stayed null; the null reached native `Renderer.make()` and SIGSEGV'd SystemUI (Phase 3). | `RiveInit` loads the `.so` by absolute path, pins the type, and initialises the C++ env itself. Never call the library's initialiser here. |
| C2 | Resizing the Rive view while System UI ran | Re-assigning `layoutParams`/resizing the `TextureView` took System UI down. | Size is captured once, on attach. Re-assign bounds only when they actually changed; size changes need a restart. |
| C3 | Rive breaker counted creations, not deaths | Three status bars tripped it at two; and unrelated restarts latched it forever, so every bar fell to Canvas with no way back. | Count once per process, before the risky creation; a stale count re-arms. `DuoGuard`. |
| C4 | `android.util.Log` from inside SystemUI | The ROM drops it; a working module looked completely dead and every verification reported FAIL. | Use `L` (logcat + `XposedBridge`) everywhere; `tools/route-module-logs.py --check` enforces it. |
| C5 | Bootstrapping from `ActivityThread.mSystemContext` | Its package is `android` while the uid is SystemUI, so the settings provider rejected every call and the module sat at stage 0. | Accept only the app's package or SystemUI's own window context. |
| C6 | Injecting into a `GONE` strip | Android 17 AOSP leaves a dead `system_icons`; the element measured 0×0 and the icons were hidden anyway. | Skip a `GONE` container; use the Compose icon view. |
| C7 | Injecting into `MiuiStatusBatteryContainer` | HyperOS lays out only its own children; the injected view was 0×0 — icons hidden, ring missing. | Inject into a plain `FrameLayout` and anchor over the battery. |
| C8 | Looping Rive idle animation ran forever | The renderer drew at frame rate for the process lifetime: 130 mAh vs ~15 mAh. | Pause the renderer when hidden/idle; restart it on a real change. |
| C9 | Canvas fallback drew no Wi-Fi | At stage 1 the stock Wi-Fi was hidden and the fallback drew nothing — "wifi icon not show". | Every occupant the Rive path draws must exist in the fallback, from the same geometry. |
| C10 | A leftover `duo_statusbar_stage` override | `useRive=true` yet stage 1 forever, with no visible reason. | Surface the override in the app, explain it, and make it clearable. It is a developer switch; never hide it. |
| C11 | "Provider unreachable" treated as "user switched it off" | First read landed before the app process was ready, the module gave up at stage 0 and never retried (ColorOS/realme). | Distinguish unreachable from off; retry; re-read on every change. |

### Moderate — broken behaviour, misleading reports, or wasted battery

| # | Mistake | What happened | The rule it produced |
|---|---|---|---|
| M1 | Rive animations defaulted to `oneShot` | The state machine stopped after the first frame; later changes were written but never drawn. | Idle animations `loopValue="loop"`; start the machine explicitly. |
| M2 | Cubic keyframes with no interpolator | 27 keyframes ran linear while looking finished; the easing set was inert. | `tools/check-rive-eases.py` in CI. |
| M3 | Window hook installed too late | HyperOS / One UI / Vector had System UI already running, so `onCreate` never fired and nothing attached. | Install the window hook first; adopt an existing window. |
| M4 | Matching the process by package only | HyperOS reports `packageName = "system"`, so every hook was skipped (issue #5). | Match the process name too (`SystemUiProcess.isTarget`). |
| M5 | Only ring + percentage followed the bar colour | Wi-Fi, cells, DND, airplane stayed white; in light mode the element was half-invisible. | Bind every foreground shape to the same colour property. |
| M6 | Fixed Wi-Fi thresholds | A strong link showed as 2/3 bars. | Read the active network's own `WifiInfo` and the platform mapping; Wi-Fi only when validated. |
| M7 | Battery monitor started at 100 | The first frame drew a full ring until the next sticky broadcast. | Read the sticky `ACTION_BATTERY_CHANGED` at start. |
| M8 | Loaded `duo.riv` by path | R8 renames the resource; the path silently failed and the element fell back to Canvas. | Resolve by resource id (`openRawResource`), with fallbacks. |
| M9 | Root capture only tried `su` | Locked-bootloader root has no `su` on the app's PATH; every report came back empty. | Discover root at known paths; always emit non-root device facts. |
| M10 | Reading two process streams in sequence, no timeout | A large log could deadlock; a stalled root prompt could hang the app. | Merge stderr, read once, bound the wait. |
| M11 | Registering the restart receiver after the enable gate | The button was dead on any ROM where the module read itself as off. | Register before the gate; then fall back to root / Shizuku. |
| M12 | A silenced ringer counted as Do Not Disturb | `isDndOn` OR-ed `RINGER_MODE_SILENT` in, so a phone merely put on silent drew the DND crescent with DND off (owner-reported). | DND is the interruption filter only; silent is separate. Unit-test the boundary (`DeviceStateReaderTest`). |
| M13 | Canvas fallback drew the ring track as a full circle | Rive's track reads the bound `leftArc`/`rightArc` (so it gaps or closes), but the fallback hard-coded `drawCircle`, putting a closed track under the digits (reported). | The fallback draws from the same bound geometry as Rive — extends C9 to the track. |
| M14 | The Rive breaker blocked the sibling bars in the same process | `noteRiveAttempt` counts once per process, but `riveAllowed` still consulted the (now full) counter, so after the main bar's attempt the keyguard bar and the shade header fell to Canvas — `renderer=Canvas, riveAttempts=2` on OxygenOS 16. | Once a Rive view has been created this process and the process is alive, allow Rive for the rest of the process (the risk was already taken). Unit-test `shouldAllowRive`. |
| M15 | Lock screen / shade drew the element at a different size | Each extra bar captured its *own* slot width, so the keyguard bar (56 px) drew the ring larger than the home bar (49 px) — reported: "the icon is bigger on the lock screen". | One size owner: every bar reuses the width pinned for the main bar at attach; only this bar's height caps it. |
| M16 | Measured ROM profiles never loaded | `RomProfiles.load` read `assets/rom-profiles.json` from the *host* context, but the module runs inside SystemUI, which has no such asset: every report logged `FileNotFoundException: rom-profiles.json`. | Read module assets through the module package's context (`createPackageContext`), the same route as `duo.riv`. |
| M17 | `BarTint` read a `darkIntensity` int as an icon colour | `onDarkChanged` carries a float intensity and an int tint; HyperOS 3 passes the intensity as an int, and the "first int" rule read 0..255 as a colour, so the element flipped black/white several times a second. | Only accept an int with a non-zero alpha byte (a resolved ARGB colour); unit-test `BarTint.pickTint`. |

### Minor — noise, duplication, cosmetic, docs

| # | Mistake | Rule |
|---|---|---|
| m1 | Duplicated resource-id lookups | Route through `RomResources.id`. |
| m2 | Repeated detector re-creation / duplicate log lines | Log once; keep the log worth reading. |
| m3 | Duplicate entries in `Diag.CANDIDATE_IDS` | Keep the probe list clean. |
| m4 | Hard-coded `"com.android.systemui"` | Use `RomAdapter.systemUiPackage`. |
| m5 | `restartSystemUi` always returned `true` | Report the real outcome, including a timeout. |
| m6 | README emoji / marketing tone | User docs are formal, short and plain. |
| m7 | Previews white-on-light, effectively invisible | Previews must show the real thing on a readable background. |

---

## Current state (so the next agent is not lost)

- **Measured ROMs:** OxygenOS (CPH2747), HyperOS 3 (24129PN74G). Everything else is `unverified`.
- **Frozen surface:** `docs/rom-contract.md`. Refactors are behaviour-freezing; diff the dump.
- **Design:** FR-01 role locator in `docs/rom-support-design.md`; FR-03 root/SELinux in
  `docs/rive-selinux-root.md`; review findings in `docs/code-review.md`.
- **Tests:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`, plus `tools/route-module-logs.py
  --check`, `tools/check-rive-eases.py`, the authority grep, and the `.riv` hash compare.
- **Build JDK:** AGP needs Java 17+. On this machine use
  `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
- **Never** release or merge to `main` without the owner's approval; betas are pre-releases and never go
  to the LSPosed store.
