## What's new in v1.1.0

- **Fix battery drain.** System UI no longer re-reads Wi-Fi/cellular state and re-renders the element on every layout pass, and the clock font no longer forces a continuous relayout loop. System UI now idles normally instead of burning ~500 mAh in the background.
- **Fix a Rive crash** (`ConcurrentModificationException`) that could restart System UI.
- **Android 14 support** — the minimum SDK is now Android 14, and Samsung One UI / ColorOS 14 / ColorOS 16 are recognised with best-effort ids (a tree-walk fallback finds the icon strip on ROMs we have not measured yet).
- **Better bug reports** — the full diagnostic dump and the "Collect full log (root)" action are now available in every build (not just debug), so a complete report is one tap away in the About section.
