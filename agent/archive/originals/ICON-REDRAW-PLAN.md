# Owned icon redraw + animation plan

Status: planning approved by the owner's request (2026-10-31). No OEM vector is packaged. The extracted
packs under `reverse/icon-packs/` are measurement/reference only.

## 1. Why redraw

- Vendor status icons are proprietary; shipping them is not acceptable.
- Android VectorDrawable XML cannot be used directly by Rive.
- The current `scene.rml` only has alarm, flashlight and screenshot art; mic/location/volume/unlock
  exist as enum values and switches but have no art, state, source or Canvas clone.
- One owned drawing per event keeps the animation and the Canvas fallback identical.

## 2. Visual system

| Property | Value |
|---|---|
| canvas | 24 × 24 dp inside the event slot |
| stroke | 2.4 dp, round cap/join |
| fill | none; light accents may use one tinted fill |
| light | top-left; the lit edge is white, the opposite edge 16% white |
| accent | one soft blue/white halo only for emitting events (flashlight, screenshot flash) |
| motion | one enter envelope + per-event body + one exit; no idle loop |
| scale | event art scales with the ring; clamp inside ring bounds |

## 3. Per-icon logic and decisions

These are the questions the owner asked; the answer on the right is the implementation default unless the
owner changes it.

### Flashlight
- **Question:** does the icon include a button?
  **Answer:** yes. A small round button at the tail is the pressed control; the barrel is the body.
- **Question:** what does a flashlight emit?
  **Answer:** a soft white/warm halo around the lens, not a long beam (the slot is too small for a beam
  that reads). The halo is part of the icon and fades with the event.
- **Trigger:** torch on (while active). Pressed state stays; off triggers `EventFade`.
- **Motion:** button depresses 6 %, body pops 1.0→1.06→1.0, halo fades in 120 ms; hold; fade 200 ms on off.
- **Canvas:** same body path, button, two halo circles.

### Alarm
- **Question:** is it a bell or a clock?
  **Answer:** owned alarm-clock silhouette, with two ring waves, because the system source is an alarm
  clock and the waves show "ringing" without a bell's religious/cultural baggage.
- **Trigger:** next-alarm transition (one-shot); duration 1200 ms.
- **Motion:** 2° shake + 1.0→1.08 pop, two waves expand/fade, then exit.
- **Canvas:** same silhouette + waves.

### Screenshot
- **Question:** camera or shutter?
  **Answer:** camera outline plus a white shutter flash inside the lens; the flash is the "light".
- **Trigger:** screenshot executor; 1000 ms.
- **Motion:** pop 0.94→1.04, shutter flash 0→0.9→0, hold, fade.
- **Canvas:** same outline/lens/flash.

### Microphone
- **Trigger:** privacy/recording active (source to be measured; do not invent a package hook).
- **Motion:** mic capsule pulse + one expanding ring; hold while active; fade 180 ms.
- **Canvas:** capsule, stand, ring.

### Location
- **Trigger:** location-in-use (privacy source to be measured).
- **Motion:** pin drop 8 px with a settle bounce; two soft ripples; hold while active.
- **Canvas:** pin and ripples.

### Volume
- **Trigger:** volume change (measured SystemUI/AudioManager source).
- **Motion:** speaker body + three arcs lighting left→right, 1200 ms.
- **Canvas:** same.

### Unlock
- **Trigger:** keyguard unlock/screen present (measured callback).
- **Motion:** shackle lifts and rotates open; 1000 ms; then fade.
- **Canvas:** same lock body and shackle.

### Charging
- Keep the existing bolt journey + `ChargeSettle` pulse; no icon change in this phase.

## 4. Implementation order (one vertical slice at a time)

1. **Art pass** — flashlight halo/button in Rive + Canvas. Build, headless screenshot, device.
2. **Art pass** — alarm waves; screenshot shutter flash.
3. **Art pass** — mic, location, volume, unlock; one node each.
4. **States** — add one state per type with measured enter/exit; reuse the existing eventGroup; do not
   duplicate the whole machine.
5. **Simulation** — add one button per event; preview drives the same state, never a separate animation.
6. **Sources** — measure SystemUI privacy callbacks, keyguard unlock and volume; only then wire.
7. **Evidence** — headless poses for every state, Canvas/Rive side-by-side, device screenshots.
8. **Gate** — no OEM assets in `app/`, no idle loop, no new `Log`, Rive `.riv` hash matches.

## 5. Rive draw-order rule (learned the hard way)

The Rive renderer here drew the **earlier** XML sibling above the later one. The first toggle/head-line
pass put the body before the details, so the white body covered every dark detail; the screenshot showed
only a blank torch. The working order for this project is:

1. details that must be on top (lens, head line, toggle beads) first;
2. the toggle track next;
3. the opaque body **last**.

Do not "fix" this by changing the body fill or adding another layer; reorder the nodes and verify with a
headless frame. The Canvas clone keeps normal painter order and is unaffected.

## 6. Questions still open (owner may answer later)

- Flashlight: warm halo (`#FFD37A`) or cool blue (`#9CC7FF`)? Default: warm white.
- Alarm: waves (default) or a small bell?
- Mic/location: show a privacy dot with the icon, or only the icon?
- Unlock: should it also play on lock, or only unlock? Default: unlock only.
- Volume: three arcs (default) or a numeric level?

## 7. Error avoidance

- Add one node at a time; run `rive . --verify` and `rive inspect . --summary` after each edit.
- Do not bind event visibility to `eventType` arithmetic; use a state per type.
- Do not use `eventGroup` opacity for more than the enter/exit envelope; each art owns its own opacity.
- Keep the Canvas fallback in the same commit as the Rive art; never ship one-sided.
- No vendor paths, no traced OEM geometry; only measurements (size, stroke, light direction) inform the
  owned drawing.
