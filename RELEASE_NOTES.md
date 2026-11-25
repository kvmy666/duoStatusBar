# Duo Status Bar 2.1.1

This release adds an iOS 27 status bar mode alongside the existing Duo ring mode. It replaces 2.1.0, which was withdrawn.

## New

- iOS 27 status bar mode with stacked dual-SIM icons and the iPhone icon order.
- 5G placement options for the cellular indicators.
- A toggle to hide extra icons from the status bar.
- Icon sizes follow the host clock size by default.
- Per-icon size and offset sliders for finer adjustment.
- Charging colours for the battery indicator.

## Fixed

- Changing the Duo design or size no longer restarts System UI, so it can no longer put LSPosed into safe mode.
- Only one mode owns the status bar at a time (Off, Duo or iOS); switching fully removes the other mode.
- Turning iOS mode off turns Duo on, and the settings follow the active mode.
- Fixed a System UI crash in iOS mode when other status icons changed.
- Stock icons stay hidden next to Duo after locking and unlocking the phone.
- The manual System UI restart now warns that LSPosed enters safe mode after four restarts per boot.

## Improved

- Diagnostic output is more detailed, so reports can identify the cause faster.
- Removed the old iOS overlay code.
