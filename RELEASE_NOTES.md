# Duo Status Bar 2.1.2

Version 2.1.2 (112) improves how the module attaches to System UI, makes diagnostic reports complete, and adds optional automatic problem reports.

## Changes

- The module now installs only into the real System UI process, so full diagnostic reports are no longer overwritten by helper processes; this fixes the "module not running" and missing-report confusion.
- Every report section now states present, empty, unreadable or skipped with a reason, and reports include a persisted breadcrumb plus crash and framework logs.
- Opt-in automatic problem reports are off by default: a 5-second consent choice after launch or update, at most 3 sends a day, sent only when a problem is detected and network and battery conditions allow, through the relay with no bot token in the app; a 24-hour work pulse runs only while a problem exists, without continuous polling.
- Re-ordering iOS icons no longer changes the live icon list inside the vendor call, fixing the System UI crash loop when iOS mode is enabled while System UI runs.
- The iOS mode switch is unavailable with a clear explanation on interfaces that cannot draw it; a saved iOS-on falls back to Duo instead of showing nothing, and the health screen reports "iOS active" instead of "disabled".
- Pixel and AOSP devices hide the Compose icon cluster correctly, and the module attaches when the stock icon strip is an empty stub.
- HyperOS keyguard and control centre icons, and the vivo quick settings and shade row, now attach correctly.
- Honor and ColorOS signal clusters are hidden, and the Motorola dual-SIM composite is kept.
- Removed an unused iOS drawing asset and dead legacy code, and updated outdated documentation.

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
