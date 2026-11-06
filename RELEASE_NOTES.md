# Duo Status Bar 2.0.0 — stable

Duo Status Bar 2.0 is the first stable release of the 1.8 line. It keeps the single animated indicator for
battery, Wi-Fi and mobile signal, with the review fixes and security hardening from the 1.8 cycle included.

## Highlights

- **Review fixes and security**: the 1.8 review findings are fixed, including the hardened report and log
  paths and the guarded status-bar attach paths.
- **Glass navigation**: the Liquid Glass navigation selector, with correct blur ownership, edge highlights
  and the Canvas fallback.
- **Designs**: Default and Two SIM, each with the invert-center variant. Changing the size needs a System UI
  restart; the app has a button for it.
- **PHDP integration**: download progress can be shown on the ring.

After installing, keep the module scoped to System UI and restart System UI (or reboot). The module stays
off until the main switch in the app is turned on.
