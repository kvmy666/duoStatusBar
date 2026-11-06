# ROM profile asset audit — static maps vs decoded APKs

Date: 2026-10-31. Tool: `tools/check-rom-profile-assets.py`.

This check asks one narrow question: **does a container/battery/clock id in `rom-profiles.json`
exist anywhere in the decoded resource tree?** It is static only; it does not prove runtime attach,
ordering, visibility, or that the id belongs to the battery cluster.

## Result

| Profile | Tier | Ids found | Missing |
|---|---|---:|---|
| oxygenos | measured | 8/8 | — |
| hyperos | measured | 7/7 | — |
| aosp | static | n/a | no decoded AOSP tree |
| coloros | static | 6/6 | — |
| oneui | static | 7/7 | — |
| originos | static | 7/7 | — |
| magicos | static | 5/5 | — |
| myos | static | 7/7 | — |
| nothingos | static | 7/7 | — |

## Corrections made

- **ColorOS:** removed `status_bar_end_side_content` and `quick_status_bar_header_system_icons` from
  the static map. They are absent from the decoded 3.0.4 tree; keeping them would be a guessed map.
- **MagicOS:** removed `hw_system_icons`. It is absent from the decoded Honor 11 tree; the remaining
  `system_icon_area`, `system_icons`, `statusIcons` are present.
- **AOSP:** no decoded tree is mapped, so the tool reports `no decoded tree mapped`. It remains
  public-source/static and cannot be promoted.

All edits preserve `measured=false` for the seven static entries. `RomDetection` still ignores them at
runtime; the unit test pins that only OxygenOS/HyperOS are measured.

## Limits

- A name can be present in a layout but unused on a particular device; this is not runtime proof.
- The check does not validate nested paths, visibility, parent ownership, or QS-vs-shade separation.
- The decoded vivo/OPPO/Honor trees came from APKs where `apktool` printed a warning and exited 1;
  their `res/` trees are usable but the decode is marked partial in the extraction report.
- Promote a profile to `measured` only after a device dump shows the path and bounds.
