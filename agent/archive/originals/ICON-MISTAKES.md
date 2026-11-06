# Icon drawing and animation — mistakes ledger

This is the list of mistakes made while building the event icons, so the next icon does not repeat them.
All of them were visible in this session; none is theoretical.

## Drawing / Rive

1. **Used the old prototype silhouette instead of the Quick Settings affordance.**
   The horizontal torch was never a vendor icon; it was our prototype. The QS source is
   `affordance_flashlight_icon.xml` pulled from CPH2747. Always pull the QS icon first.
2. **Did not check the actual reference proportions.**
   The QS flashlight is vertical: a wide cap bar, shoulders, a narrow handle, and a small switch hole.
   The first redraw used a funnel silhouette with a circular lens — wrong.
3. **Rive draws earlier siblings above later siblings in this project.**
   The first toggle/head-line pass put the white body before the details, so the body covered every
   dark detail. Working order: details first, track next, **opaque body last**.
4. **Same error with the toggle dots.**
   The track was before the dots in XML; because of reverse draw order it covered them. Put the track
   after the dots so the dots render on top.
5. **Used a circular halo for a flashlight.**
   The owner wants a cone: narrow at the mouth, widening away from it, with light inside. A circular
   halo is only correct for a glowing object, not a directional torch.
6. **Added two beam edge lines, then had to remove them.**
   The final design is glow-only: no stroke on the cone.
7. **Added a circular lens that the QS icon does not have.**
   The cap bar is the lens. Do not invent a lens circle.
8. **Used `CubicVertex` directly in `scene.rml`.**
   It is abstract; verification failed (`not-null` error). Use `StraightVertex` or a concrete
   `Cubic*Vertex` type (`CubicDetachedVertex`, etc.).
9. **Used a six-digit color (`FFB86B`).**
   Rive expects ARGB; the missing alpha made the bead invisible. Always use eight digits
   (`FFFFB86B`).
10. **Scaled the whole icon when trying to enlarge it, including the beam.**
    The glow overflowed the ring and later the numbers. Scale the body/toggle deliberately and keep
    the light path inside the ring.
11. **Changed the icon scale several times without measuring the ring.**
    Sizes tried: 1.35×, 2.5×, 3.1×, 3.77×, then 2.64×, then 2.11×. The target is roughly 80% of the
    ring with margin; do the percentage math before editing, not after three APK builds.
12. **Shifted the icon up/down without centering it.**
    One attempt left a 6 px bottom margin and a 38 px top margin. Measure top and bottom before shipping.

## Animation / state machine

13. **Assumed `EventFade` would animate the toggle.**
    It only faded the group/icon. The switch and light stayed as they were. `EventFade` now has explicit
    keyframes: 300 ms toggle/light off, then a 200 ms icon fade, then Wi-Fi returns.
14. **On/off timing was shared accidentally.**
    The first pulse was 200 ms, then 300 ms, then 500 ms. Write the intended duration in the plan before
    authoring keyframes.
15. **Treated the charging settle pulse as the full 0→level pulse.**
    It is only the arrival beat; the green-arc sweep is still missing. Do not call it RIV-002 complete.
16. **Used headless `--advance=0/9/18` and got the idle state.**
    The CLI did not always enter the event state. Use a known working frame (4) or inspect the state
    machine; do not conclude the art is missing from an idle screenshot.
17. **Did not verify the event source before drawing.**
    Mic/location/volume/unlock still have no measured source. Art without a source produces a hidden
    icon and a false "done" claim.

## Process / build

18. **Installed over a debug APK without uninstalling.**
    Release install failed with a signature mismatch and the phone still ran the old build. Uninstall
    or check `INSTALL=0` and the versionName every time.
19. **Built APKs before the final Rive copy in some iterations.**
    The APK contained the previous animation. Always `rive --once`, copy `duo.riv`, then assemble and
    check the packaged `res/zy.riv` hash.
20. **Believed an APK was current because it was installed.**
    Check `dumpsys package` versionCode/versionName and the packaged Rive hash, not just "Success".
21. **Used a debug build on the emulator and hit an ART JIT crash.**
    The debug build crashed in `JitCompileTask::Finalize`; release did not. Use release for UI/perf
    validation.
22. **Tried to compare against vendor icons by eye only.**
    The reference images are QS On/Off; the app icon is an in-ring event. Pull the QS vector, measure
    its cap/handle/switch, and keep a comparison image.
23. **Forgot the owner’s 30% / 20% size steps.**
    Current target is 2.11× after 3.77 → 2.64 → 2.11; check the last instruction before rebuilding.
24. **Rive screenshot did not show dark details until the body was moved last.**
    A blank white silhouette is a draw-order symptom, not a color/alpha symptom.

## Rules for the next icon

- Pull and inspect the QS vector first; redraw owned geometry, never copy the path.
- Write the shape order (details → track → body) before editing.
- Use `StraightVertex` unless a concrete cubic type is known.
- Use eight-digit ARGB.
- One vertical slice: art → states → Canvas → simulation → source → evidence.
- Measure the ring/icon ratio before scaling.
- Build with release, copy the `.riv`, verify the packaged hash, install, check versionName.
- Do not mark an event complete without a measured source and a device screenshot.
