# Reference projects — what we reuse, what we do not (FR-13, FR-12)

Reviewed before writing anything of our own. Licences matter: everything reused here is permissive,
and the Apple-derived geometry is public design information (icons/simple geometric shapes are not
copyrightable in the way artwork is), reproduced as our own vector data.

| Project | What it is | What we take | What we do not |
|---|---|---|---|
| **`lingyired/status-trio`** (Apache-2.0) | macOS app that combines Wi-Fi + battery + volume into one status icon, explicitly *"inspired by the iPhone Duo combined status bar"* | **The numeric geometry basis** of `DESIGN-duo.md`: ring radius 51.5 / stroke 8, the two-segment ring split by a top gap for the value, 3 Wi-Fi arcs + dot, 4 cellular spheres, green `#34C759` / yellow `#F2B900` / 22 % track. Cross-checked against the user's iPhone screenshots, which matched. | Its Swift implementation, its `--battery-top-gap` dash-offset trick (we animate a real `TrimPath` instead), its volume indicator |
| **`LinYi-7/Duo-Status`** (MIT, Kotlin) | **Android *overlay* widget** — its README states plainly that a normal APK cannot write into the real status bar, so it floats a draggable window; no root | Its signal mapping tables and UX defaults once reviewed: Wi-Fi 3 levels, 4 cellular dots, grey for inactive parts, size range 52 %–120 %, and the black/white/green colour options — all of which match our own measurements | The whole approach. An overlay cannot satisfy FR-08 (the stock icons must be *really* removed), FR-03 (must behave identically on the lock screen and in every app), FR-05 (gestures inside the status bar) or FR-17 (drag inside the status bar). It is also not animated (FR-04/FR-25) |
| **`Block-Network/StatusBarLyric`** (GPL-3.0) | LSPosed module that injects its own view into the status bar and claims broad ROM support | *Idea/technique* only: add a custom view to the status bar instead of overlaying it | Code (GPL-3.0 would relicense our project; we keep our own implementation) |
| **`MonwF/customiuizer`, `tianma8023/XMiTools`** | SystemUI tuning modules for MIUI | *Idea* only: hide icons by acting on the icon controller rather than by covering them | Code (MIUI-specific) |
| **Rive CLI samples** (`Montserrat.ttf`, OFL) | Font shipped with the Rive CLI | The placeholder font for the digits until a rounded face is chosen | — |

## Conclusion

**No existing project can be adopted wholesale**, and that is not a style preference: the only way to
meet FR-08 (real removal of the stock icons), FR-03 (identical behaviour on home screen, in apps, on
the lock screen, in both orientations), FR-05/FR-18 (gestures that coexist with Auto Expand) and
FR-04/FR-25 (Rive animation of a live element) is to run *inside* `com.android.systemui` as an LSPosed
module — which is exactly what Phase 0 proved possible on this device, including loading the Rive
runtime there.

What we did reuse is the expensive part nobody should redo: **the measured geometry and colour
endpoints** (from `status-trio`, validated against the user's screenshots) and the **state mapping
conventions**. Everything else is authored here, in text, and verified with `rive --verify` plus
screenshots — cheaper and more auditable than clicking in an editor.
