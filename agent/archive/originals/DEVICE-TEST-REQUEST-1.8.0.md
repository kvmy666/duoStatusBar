# Device test request — 1.8.0-alpha.8

Build: `1.8.0-alpha.8` (code 97), delivered to the owner through Telegram (message 45) and
installed on CPH2747 at 13:50:03 with data kept.

**Restart the phone / System UI first.** The module update does not take effect until System UI
restarts.

## Checklist

1. **Install:** confirm Settings shows `1.8.0-alpha.8` (code 97). Restart the phone or System UI
   before judging the bar.
2. **Idle layering:** while idle, the tab row must stay above the selector. The selector must keep
   the frosted surface with no blur, and icons/labels must stay sharp.
3. **Moving layering:** drag the selector. It must rise above the tab row and bring back the lens
   and chromatic aberration (`selectorMovingBackdrop = page + pill + row`) while dragging. No blur
   may be added to the selector.
4. **Selector frost:** in the dark theme the frosted surface is white at 0.12; in the light theme
   it is black at 0.06. Press a tab and confirm the frost fades with the press.
5. **Selector depth:** confirm the inner and drop shadow, and the always-on highlight (idle at
   0.45x). The lens and chromatic aberration must appear only while moving.
6. **Selector colours:** select a tab; its icon must be blue (`DuoColors.NavAccent`). Hover another
   tab and confirm it is blue too. Every other tab must stay white.
7. **Shade seat:** open the shade/QS panel. The element must keep the v89 battery anchor (X right,
   Y ~-18) with no clamp, and must not overlap the bell icon.
8. **Clock:** set the middle slot to clock. Confirm the format is `H:mm`/`h:mm` without AM/PM and
   that the minutes fit inside the ring.
9. **Regression:** charge the phone with Invert center on (bolt at ring centre in both designs);
   drag/tap the Wi-Fi/cell/5G sliders and confirm the value persists. Confirm System UI stays alive
   and the status bar keeps rendering.

## If it fails or System UI restarts

**Do not clear the log and do not reboot before pulling the log.** A reboot can wipe the System
UI log and tombstone. Pull logcat immediately, plus any tombstone written around the failure.
LuckyTool may name another process; search for `Duo`, `Rive` and `SystemUI` in the capture.
Attach a screenshot and the failure-window log.
