# BRAINSTORM — Multi-UI Update Discovery Summary

Full capture: `brainstorms/2026-10-26-multi-ui-update-discovery.md` (English translation; no Arabic in files).

## Goal

Define the next major update before writing requirements, test plan, or code. The owner dictated the brief; the agent asked questions in batches of five with one suggested answer each.

## Locked decisions

1. **Surfaces:** Duo replaces the battery/status-icon cluster in the status bar, keyguard, shade header, and QS header. QS and shade are one verification surface.
2. **Host model:** hook the existing battery host; draw Duo in its bounds; restore exactly. No force/removal, no separate window overlay.
3. **Measured OnePlus host:** `quick_qs_status_icons` -> `batteryRemainingIcon` -> `battery_icon_view`; `statusIcons` is a plain `LinearLayout`. The old `shade_header_system_icons` / `StatusIconContainer` assumption was wrong for this build.
4. **UIs:** OnePlus OxygenOS, OPPO/realme ColorOS, Xiaomi HyperOS, Samsung One UI, vivo OriginOS, Honor MagicOS, Motorola MyOS, Nothing OS, AOSP baseline.
5. **Engines:** Rive + Canvas, user choice, Auto/Recommended; auto fallback to Canvas on failure; every Rive design has a Canvas clone.
6. **Customization:** middle slot (Wi-Fi/cell/battery %/clock/charging); 4+4 dots vs battery bar; Wi-Fi in the charge slot; icon packs; per-icon show/hide; 3 presets + advanced; live preview.
7. **UX:** assume the user is easily confused; minimal text, visual-first, logical; 3 pages (Home/Engine/Settings).
8. **Backup:** plain versioned JSON, no encryption, no secrets; export/import everything.
9. **Live events:** charging, alarm, screenshot, mic, location (P0); volume, unlock (P1); SystemUI hooks first; consent for UsageStats/NotificationListener.
10. **Cell accuracy:** four dots mapped to the system bar count; dual-SIM and 5G NSA.
11. **Language:** app and support channel English only.
12. **Stability:** no new core architecture; follow the old code paths.
13. **Process:** no code before Requirements + Test Plan + Plan v2 are approved; one branch per phase.
14. **Tools:** BEAM for non-sensitive work; DeepSeek for architecture/code/debug; never GLM.
15. **OEM assets:** locally extracted for prototype/engine testing; owner removes them at the official launch; no redistribution commitment.

## Measured evidence

- CPH2747 / Android 16 / OxygenOS 16.0.9.400; module 1.4.3-beta.1 (24); `duo_statusbar_stage=null`.
- Shade/QS tree measured; `batteryRemainingIcon` bounds `[1116,153][1202,254]`; `battery_icon_view` `[1119,178][1201,229]`.
- LSPosed: module active in SystemUI (`DuoSB` lines, pid 10266); shade header still stock.
- Windows NTFS `OS` partition not mounted; owner action.

## Open flags

- Phase 1 safe code is built (`phase/1-shade-qs`, 221 tests, safe release); **device verification is pending**.
- LSPosed safe mode occurred on the previous duplicate-fix build (forced remove/reparent); that path is removed. Rollback + safe APKs are in `build/`.
- 8-UI shade schema + static maps + candidate resolver probes are built; runtime dumps are still missing for six UIs.
- OnePlus vendor hooks (`OplusQSSimpleHeader`, `OplusSplitShadeHeaderControllerExImpl`) are static-only.
- 8 vendor APKs are acquired; 6 UIs still lack runtime dumps -> `unverified`.
- Windows logs not mounted.
- EOL is mixed CRLF/LF; normalize before commit.
- PERF-005 active-use +2% proposed.
- Support channel later.
- OEM icon removal timing is the owner's responsibility.
