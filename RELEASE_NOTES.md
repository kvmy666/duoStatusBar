# Duo Status Bar 2.0.2

A maintenance release on top of 2.0.1.

## Fixes

- **Restart / QS / shade**: the element is not hidden or reported attached until it has a real laid-out box. A 0x0 element falls back to the simple Canvas drawing; if that also fails, the stock icons are restored instead of leaving a gap. Panel reveal/handoff retries for a bounded window and no longer treats alpha 0 as "no view".
- **ROM profiles**: measured OxygenOS and HyperOS profiles now require their measured SDK (OxygenOS 16 / SDK 36, HyperOS 3 / SDK 37). OxygenOS 13 is unverified and uses the generic ids.
- **Classic fallback**: the Canvas fallback honors Classic (one SIM row) instead of always drawing the dual-SIM row.
- **DND**: with middle=percent, Do Not Disturb keeps its normal middle seat instead of the inverted low/scaled seat.
- **Report upload**: 2.0.1 and 2.0.2 builds carry the relay key; full-log upload is configured.

## Known limitations

- ColorOS 17 "both bars hidden" and the Xiaomi Fold lag/restart reports still need the user's ZIP or tombstone to prove the cause.
- Android 13 OxygenOS may still fall back to Canvas if Rive cannot load its asset.
