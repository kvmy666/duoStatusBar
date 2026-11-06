# Decisions (ADR Log)

Status: living. Each decision has context, the choice, and the consequence.

## ADR-001 — Hook the existing battery host
- Context: the old model hid stock icons and injected a separate Duo view; the owner wants Duo where the battery is, without forcing/removing it.
- Decision: keep the stock battery view, intercept its drawing, draw Duo in its bounds, restore exactly on disable. No removal, no separate window overlay.
- Consequence: the host resolver must find the real draw point per UI; if it cannot, leave stock and log.

## ADR-002 — QS and shade are one surface
- Context: the QS lives in the shade; the owner expects one verification to cover both.
- Decision: treat the shade header and QS header as one surface for the fix; capture both as evidence.
- Consequence: one host binding, two screenshots/dumps.

## ADR-003 — Measured OnePlus tree is authoritative
- Context: CPH2747 measured `quick_qs_status_icons` -> `batteryRemainingIcon` -> `battery_icon_view`; the old code looked for `shade_header_system_icons` / `StatusIconContainer`.
- Decision: use the measured ids/classes for OxygenOS 16; keep the old paths as fallbacks only.
- Consequence: no guessed OnePlus ids; other UIs still need their own measurement.

## ADR-004 — Additive code, no new core architecture
- Context: the app is stable; the owner asked not to introduce new things.
- Decision: extend `DuoIconHost` / `ContainerFinder` / `RomAdapter` in their existing style. App-only UI libraries are allowed; a new core dependency needs a written reason and approval.
- Consequence: slower, smaller changes; no framework rewrite.

## ADR-005 — Every Rive design has a Canvas clone
- Context: not every device can run Rive; the owner requires Canvas parity.
- Decision: every Rive design/state must have a Canvas implementation with the same geometry/state logic.
- Consequence: each animation is designed twice; tests compare both.

## ADR-006 — Auto fallback Rive -> Canvas
- Context: Rive can fail natively; the current behaviour already falls back.
- Decision: Auto tries Rive; on capability failure, bind timeout, or native failure, switch to Canvas for the process; user can force either; log the reason.
- Consequence: no half-attached Rive; Canvas must be complete.

## ADR-007 — English only
- Context: the owner wants English app and support; the CLI has problems with Arabic.
- Decision: all file names and file contents, app UI, and support channel are English.
- Consequence: no Arabic localization in this release.

## ADR-008 — Plain JSON backup
- Context: the owner wants backup/import of everything, with no sensitive data.
- Decision: one versioned JSON, no encryption, no passwords; import validates and confirms before overwrite.
- Consequence: schema versioning and migration tests are required.

## ADR-009 — 8 vendor UIs + AOSP; unverified is explicit
- Context: only OnePlus and HyperOS are measured.
- Decision: support 8 vendor UIs + AOSP; a UI without a measured dump is `unverified` and not claimed.
- Consequence: per-UI evidence is the acceptance gate.

## ADR-010 — Branch per phase
- Context: the update is large and parallelizable.
- Decision: `phase/N-name`, UI sub-branches `phase/N/<ui>`; no `main` commits; merge after the phase gate.
- Consequence: parallel agents must use separate worktrees/files.

## ADR-011 — Model routing
- Context: cost and quality.
- Decision: BEAM for non-sensitive tasks; DeepSeek for architecture/code/debug/critical review; never GLM; DeepSeek is the ceiling.
- Consequence: `general` + BEAM is avoided when the REA tool schema exceeds 128 tools; use `explore-fast`/`builder` (REA denied).

## ADR-012 — Host rendering spike (pending)
- Context: `battery_icon_view` is an `ImageView`; Canvas can use a `Drawable`, but Rive may need a child view inside `batteryRemainingIcon`.
- Decision: spike both options on CPH2747 before choosing. Canvas drawable first; Rive child view only if it stays inside the host and does not create a window overlay.
- Status: proposed; decision after the spike. If Rive cannot be hosted safely, Phase 1 uses Canvas for this host and logs the limitation.

## ADR-013 — OEM assets are local/prototype
- Context: OEM icons are copyrighted; the owner will remove them at the official launch.
- Decision: extract locally for analysis/engine testing; do not commit; release builds do not depend on them.
- Consequence: the owner controls removal timing; no redistribution claim.

## ADR-014 — Support channel later
- Context: user communication is difficult; the owner wants a technical support channel in English.
- Decision: Telegram, created later; no implementation in the current phases.
- Consequence: the in-app report zip is the interim path.
