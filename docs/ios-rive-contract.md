# `ios.riv` binding contract — artboards and state machine `Ios`

> **RETIRED (2.1.2).** No code implements this contract. `ios.riv` is no longer packaged and there is no
> `IosBinder`/`IosRiveView`; the bar draws via `IosDrawables`/`IosGlyphRenderer`. `rive/ios/scene.rml` is
> kept as design evidence for a future Rive port. The property table below is historical design.

This is the property contract for the iOS status-bar scene (`rive/ios/scene.rml` →
`app/src/main/res/raw/ios.riv`).

The scene has three artboards, one per glyph, so each stock slot gets a tight `Fit.CONTAIN`:
`Ios` (battery, the primary artboard), `IosWifi` and `IosCell`. **Every artboard runs a state machine
named `Ios`** and exposes the same 12 property names in its view model. `IosRiveView` selects the
artboard by icon kind; `IosBinder` only names properties.

## Isolation from Duo

`DuoBinder`, its 59 properties (`PROPERTY_COUNT`), `rive/duo/scene.rml`, `duo.riv` and the `Duo` artboard
are unchanged by this contract. The iOS scene is a separate file, a separate binder and a separate
property count; an iOS property can be renamed without touching Duo, and vice versa.

## Properties

`IOS_PROPERTY_COUNT` is `12` and must equal the size of `IosBinder.PROPERTY_NAMES`. A unit test
(`IosBinderTest`) asserts the count, the exact names and their order.

| # | Property | Type | Range / meaning |
|---:|---|---|---|
| 1 | `part` | number | `IosPart` ordinal: `0` BATTERY, `1` WIFI, `2` CELL |
| 2 | `visible` | boolean | Whether this glyph is drawn at all |
| 3 | `tint` | color | The dynamic bar tint, for every foreground shape (M5/M17) |
| 4 | `batteryLevel` | number | Charge, `0.0`–`1.0` |
| 5 | `batteryState` | number | `IosBatteryState` ordinal (below) |
| 6 | `batteryFillColor` | color | Resolved fill: dynamic tint for NORMAL, a fixed state colour otherwise |
| 7 | `boltOpacity` | number | `0.0`–`1.0`; `1` while charging, charging-saver or plugged-not-charging |
| 8 | `wifiLevel` | number | Wi-Fi arcs, `0`–`3` |
| 9 | `cellMode` | number | `IosCellMode` ordinal: `0` SINGLE, `1` DUAL |
| 10 | `cellRole` | number | `IosCellRole` ordinal: `0` DATA, `1` OTHER, `2` BOTH |
| 11 | `cellBars` | number | Data SIM bars, `0`–`4` (SINGLE also uses this row) |
| 12 | `cellDots` | number | Other SIM dots in DUAL, `0`–`4`; `0` in SINGLE |

## Enum values

`IosBatteryState` (property `batteryState`):

| Ordinal | Value | Meaning |
|---:|---|---|
| 0 | `NORMAL` | Unplugged, saver off, above the low threshold |
| 1 | `LOW` | Unplugged, saver off, `level <= lowThreshold` |
| 2 | `SAVER` | Unplugged, battery saver on (overrides LOW) |
| 3 | `CHARGING` | Plugged and charging |
| 4 | `CHARGING_SAVER` | Plugged and charging with saver on (yellow + bolt) |
| 5 | `PLUGGED_NOT_CHARGING` | Plugged with `BATTERY_STATUS_NOT_CHARGING` (charge limit/hold); outranks saver, grey + bolt |

`IosCellMode` (property `cellMode`): `0` `SINGLE`, `1` `DUAL`.
`IosCellRole` (property `cellRole`): `0` `DATA`, `1` `OTHER`, `2` `BOTH`.

## Behaviour the scene must respect

- Idle animations loop; the renderer is paused when hidden or idle (C8).
- Bind every foreground shape to `tint` except the battery body/fill, which use `batteryFillColor` (M5).
  `IosMapping` already resolves that colour to the dynamic tint for `NORMAL` and to Apple's fixed
  `#FF3B30` / `#FFCC00` / `#34C759` / `#8E8E93` for the other states.
- The battery fill is the body clipped to `batteryLevel`; the remainder is the same colour at 50%
  alpha. There is no outline and no percentage inside (owner decision, revision 2).
- The bolt is a separate path whose opacity is `boltOpacity`, drawn in `tint`.
- Wi-Fi is its own measured glyph (fitted to the light reference); its arcs are driven by
  `wifiLevel`, and the dot stays at full tint.
- Cellular is four bars in `SINGLE`. In `DUAL` the host spans both stock slots with one view and
  `BOTH` draws the stacked block (DATA bars on top, OTHER dots below). `DATA` and `OTHER` remain
  supported artboard values for a single row; both Android mobile slots draw something in every state
  the host accepts (rule 5).
- A `canDraw == false` element must never cause a stock icon to be hidden; the real views only report
  true once their runtime/asset started, and a post-attach Rive bind failure falls back internally to
  the Canvas glyph.
