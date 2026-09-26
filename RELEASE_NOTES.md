## What's new in v1.2.0

- **The element injects on more ROMs.** On some Android 16 builds (e.g. HyperOS) the System UI process
  reports its package as `system`, so the module loaded but hooked nothing and the app said "not loaded".
  The module now matches the process name too, so it binds everywhere. (Issue #5)
- **Fix a real battery drain.** The Rive renderer was drawing at frame rate for the life of the System UI
  process, including while the element was off screen — the ~130 mAh drain. It now pauses when the element
  is hidden and after a few quiet seconds, and wakes on the next change.
- **The battery level is right on the first frame.** The element used to start at a default 100 % and drew
  a full ring until the next battery broadcast. It now reads the real level immediately.
- **The whole icon follows light/dark, not just the ring.** The Wi-Fi, cellular, DND, airplane and network
  icons were fixed white; they all follow the status bar colour now, so the element is fully black on a
  light bar and white on a dark one.
- **Choose what happens to the other icons.** A **Hide other status icons** switch: on = only the ring
  (the classic look), off = keep your silent/vibrate/alarm icons beside it.
- **Optional Shizuku support.** If the stock Wi-Fi/cellular/battery icons survive the module's own hiding,
  the app can hide them at the system level with [Shizuku](https://shizuku.rikka.app) (off by default; no
  Shizuku, no change). (Issue #4)
- **A fallback alert that gets bugs fixed.** If the animated element can't start, the app says so and
  offers **Send the log on Telegram** or **Report it on GitHub** in one press.
- **Update checks.** A **Check for updates** toggle posts a notification when a new release is out, and a
  **Check for update** button tells you if you're up to date.

**Tip:** changing the size still needs a restart — the **Restart System UI** button is in the app.
