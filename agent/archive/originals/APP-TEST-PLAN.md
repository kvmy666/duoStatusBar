# Full app test plan — beta.40

Date: 2026-10-31. Node: API 36 emulator + CPH2747 logs. This plan covers the whole app, not just the
lock event.

## Scope

1. **Shell/navigation:** Home → Engine → Settings → back, three-page state, no crash.
2. **Liquid glass:** selector circle at rest, capsule on drag, snap back; pill blur; switches/sliders;
   scroll edges; light-mode readability.
3. **Settings:** every section reachable, toggles change persisted state, sliders move, event toggles
   (Screenshot, Calls, Lock, Alarm) respond.
4. **Simulation:** Alarm, Flashlight, Screenshot cards; no stuck event.
5. **Events:** screenshot file source, keyguard→home, incoming call, alarm, flashlight.
6. **Resilience:** rapid nav, repeated toggles, 50-event stress, process alive, crash buffer empty.
7. **Observability:** `DuoSB` logs, pid, `dumpsys gfxinfo`, screenshots for each screen.

## Cases

| ID | Case | Method | Pass |
|---|---|---|---|
| APP-01 | Home renders | launch, screenshot | bottom bar + Home content visible |
| APP-02 | Engine tab | tap Engine, screenshot | Engine content, selector moves |
| APP-03 | Settings tab | tap Settings, screenshot | sections visible |
| APP-04 | Selector | drag between tabs | capsule during drag, circle at rest |
| APP-05 | Toggle | tap a setting switch | value changes, no crash |
| APP-06 | Slider | drag a slider | value changes, no crash |
| APP-07 | Scroll | 5 swipes | content moves, edge glass, no ANR |
| APP-08 | Calls toggle | tap Calls | persisted value flips |
| APP-09 | Simulation | Alarm/Flashlight/Screenshot | preview changes, returns |
| APP-10 | Screenshot | KEYCODE_SYSRQ or file watcher | `live event -> SCREENSHOT` |
| APP-11 | Lock→home | PIN unlock | `live event -> UNLOCK` |
| APP-12 | Call | `emu gsm call` | `live event -> CALL` |
| APP-13 | Stress | 50 rapid nav/toggle/swipe | pid alive, crash empty |
| APP-14 | What-if | call during unlock; screenshot during call | newest event wins, no crash |

## Evidence

- Screenshots: `docs/evidence/full-app/<case>.png`
- Logs: `docs/evidence/full-app/logcat.txt`
- Report: `agent/FULL-APP-TEST-REPORT.md`

## Out of scope

- Stable release approval.
- Seven vendor ROM runtime (only the emulator + CPH2747 logs).
- Visual pixel match for every icon.
