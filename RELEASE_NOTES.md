# Duo Status Bar 2.0.1

A maintenance release on top of 2.0.0.

## Fixes

- **QS/shade hand-off**: the panel guards now retry the incoming/outgoing drawable check for a bounded
  window instead of giving up after one frame, so the main copy no longer stays visible over an expanded
  panel (or the outgoing copy is no longer kept after collapse).
- **Charge shake**: the 20 % shake no longer throws when it tags the view, so the animation runs and a
  second trigger cancels the previous one cleanly.
- **Shade fallback**: the shade copy keeps the right seat when there is no battery anchor.

The relay reporting fix is pending configuration and is not part of this build.
