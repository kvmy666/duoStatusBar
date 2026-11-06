# Live-event architecture — Duo Status Bar

Status: **alarm and flashlight implemented in Rive + Canvas; screenshot/mic/location pending; device
verification pending.** This file exists so the interactive-icon work is built as one system, not as
ad-hoc toggles. It records the owner's classification, the requirements it must satisfy, the unresolved
questions, and the proposed workflow/test path.

Project-memory key: `event_interactive_icons_architecture`.

## 1. Owner classification

| Class | Icons / states | Behaviour |
|---|---|---|
| **Fixed / stateful** | Wi-Fi, 5G/4G, airplane, clock, battery percentage | Occupies the middle slot while its state is true; changes only on a state change. |
| **Live event** | alarm, screenshot, microphone, location, volume, unlock (P1), charging (already exists) | Event fires -> current middle occupant fades -> event icon appears and plays its Rive motion -> icon fades -> the previous middle occupant returns. |
| **Per-icon user choice** | every icon above, plus stock icons | The user can show or hide each one; no forced hiding. |

The distinction is important: a fixed icon is a **state occupant**; a live icon is an **event
presentation** with a start, an animation, an end, and a guaranteed return.

## 2. Requirements this must satisfy

- **LIVE-001**: alarm, unlock, charging, screenshot, volume, location, microphone.
- **LIVE-002**: P0 = charging, alarm, screenshot, mic, location; P1 = volume, unlock.
- **LIVE-003**: event fires -> current middle occupant fades -> event icon + Rive animation; Canvas
  clone matches.
- **LIVE-004**: per-icon motion (alarm bounce/shake, screenshot flash, volume pulse, etc.).
- **LIVE-005**: SystemUI hooks first; UsageStats/NotificationListener only with explicit consent;
  Accessibility only if no alternative.
- **LIVE-006**: missing permission/source -> normal Duo state + reason log; never crash.
- **LIVE-007**: event-driven only; no polling; renderer paused while idle.
- **RIV-004/RIV-005/RIV-008**: defined motion, Canvas parity, headless + device evidence.
- **CUS-006**: per-icon show/hide remains a user choice.
- **TST-005/TST-009**: structured logs and no PII (no notification text, location, SSID, etc.).

## 3. Current facts (verified in the repository; not assumptions)

- `DuoVisual` currently has a single middle-slot state: `middleMode` 0..4 and `networkText` for the
  cellular generation. It has no event type, event phase, or event icon.
- The Rive scene has a `MiddleSlot` layer with Wi-Fi/airplane/DND/network states. It has **no alarm,
  screenshot, microphone, location, or volume art/state**.
- `DuoCanvasView` draws the same middle occupants from `DuoVisual`; there is no event overlay.
- `DuoStateMonitor` already handles charging, battery, DND, airplane, Wi-Fi/cellular, screen on/off,
  download progress, and bar tint. Charging is a boolean, not a generic event.
- The app can already choose the middle occupant (`middleSlot`) and hide the Wi-Fi glyph/cellular dots.
- **There is no verified alarm-ringing source, screenshot source, mic/location source, or volume
  source wired into the module.** This is the first place where guessing would be dangerous.

## 3.1 Owner decisions (2026-10-28)

These are now requirements, not open choices:

1. **Alarm** appears both when it is **set** and when it **rings**. Use the Android/SystemUI source,
   not a per-app alarm package (alarm apps differ across UIs; there is no single alarm package).
   The owner explicitly authorized an actual on-device measurement of the ringing/end source.
   **Final owner decision (2026-10-28): do not tie the event to notification/ring end.** The alarm
   event is a one-shot animation until its own end, then fades and restores the middle state.
2. **Newest event wins**: a new event cancels the current event and takes its place immediately.
3. **Duration is per event type**:
   - Alarm: one-shot animation duration (prototype ~1.2 s), **not** while-ringing and **not** tied
     to notification removal.
   - Flashlight: while the torch is active; hide when it turns off.
   - Transient events (screenshot): a short fixed lifetime (~1 s or the source's active window).
   - Rotating/state icons (Wi-Fi, 5G/4G, airplane, clock, battery %) are not events.
4. **Animation is per event type, not per icon.** Multiple icon variants share the same event
   animation (`icon -> animation` map). 16 icons and 4 animations are 4 animations plus a mapping/testing
   matrix, not 64 authored animations.
5. **Same behaviour on all three surfaces:** status bar, keyguard and shade/QS copies must show the
   same event state/animation/return; no surface-specific event logic.
6. **Return to the current middle state** at event end, not the state captured at event start.
7. **Prototype scope:** alarm + flashlight + screenshot, each with its own animation; volume/unlock
   wait until the P0 event layer is stable.
8. **One icon shape for now, extensible to four:** the event model must accept an icon variant/slot
   list later without changing animations or event logic. Start with one default shape per event.
9. **Per-event toggle:** each event has its own user switch; one global switch is not enough.
10. **Render sizing:** the event icon scales with the ring's current size, and stays inside the ring
   bounds. Alarm motion is a jump/shake inside the ring; flashlight is a pressed button that remains
   pressed while torch is on, then fades after the press-off.
9. **Use system/SystemUI sources first.** Do not integrate every third-party alarm/clock app; v1 stays
   hook/SystemUI-only and degrades when a source is unavailable.
10. **Icons come from the device/SystemUI resources** for the prototype. OEM assets are prototype-only
    (UI-013); release must use owned/redrawn art or an approved licensed set.

## 3.2 Measured system-source evidence (OnePlus SystemUI APK)

APK: `corpus/SystemUI-device.apk`, SHA-256 `dbe67fd3688a5bb5ebe7200190aaaac0bfbf88068a1534b6aebd62cd3f588ff4`.
Static evidence only; runtime behavior must still be proven on device.

### Next alarm / alarm icon

`com.android.systemui.statusbar.policy.NextAlarmControllerImpl`:

- Constructor receives `android.app.AlarmManager` and `BroadcastDispatcher`.
- Field `mNextAlarm: AlarmManager.AlarmClockInfo`.
- `onReceive(Context, Intent)` handles the exact action string
  `android.app.action.NEXT_ALARM_CLOCK_CHANGED` and calls `updateNextAlarm()`.
- `updateNextAlarm()` calls `mAlarmManager.getNextAlarmClock(userId)` and notifies
  `NextAlarmController.NextAlarmChangeCallback.onNextAlarmChanged(mNextAlarm)`.

Consequence: this is the system-wide alarm source, independent of the alarm app package. It tells us
the **next alarm** (set state). It does **not** by itself prove a separate "currently ringing" signal;
that exact end-of-ring transition still needs a measured source (status-bar alarm icon state, alarm
broadcast, or a SystemUI callback). Do not invent one.

**Measured on CPH2747, 2026-10-28 15:09 +03 (12:09Z):**

- Before: next alarm `15:09:00`, `next_alarm_formatted=Wed 3:09 PM`.
- At `15:09:00.020` the system delivered `android.app.action.NEXT_ALARM_CLOCK_CHANGED` with the old
  alarm cleared (`nextUserAlarmTime=0`), then at `15:09:00.103` delivered it again with the next alarm
  `17:33`; `dumpsys alarm` confirmed the next alarm moved to `17:33`.
- The alarm app's foreground service (`AlarmService`, code `ALARM_MANAGER_ALARM_CLOCK`) started,
  vibration with `USAGE=ALARM` was observed, and the heads-up dismissed around `15:09:05.736`.
- **No** `PhoneStatusBarPolicy`/status-bar alarm-icon line appeared, and no system end-of-ring
  broadcast was observed.
- Conclusion: `NextAlarmControllerImpl` covers the **set state and ring-start transition**, not a
  distinct ringing/end signal. Ringing/end must be sourced from another measured system signal; the
  candidates are SystemUI notification entry (`CATEGORY_ALARM`/alarm FGS), `AudioManager`
  playback callback (`USAGE_ALARM`), or a status-bar icon observation. Do not implement the end on
  the NextAlarm callback alone.

**Measured generic notification evidence (same ring):**

- `NotificationService PostNotification ... prayer_alerts` at `15:09:00.100–00.314`.
- SystemUI `NotificationPipeline` processed the post; heads-up and `removeLiveAlert` appeared.
- At `15:09:05.109–05.120` the FGS stopped and the notification was removed/dismissed
  (`NotifDismissalHandler`, `NotificationPipeline` dismissal, `notificationRemoved`), followed by
  vibration cancel at `15:09:06.122`.
- **Correction from the same logs:** the posted prayer-alarm notification did **not** carry
  `Notification.CATEGORY_ALARM`; Oplus tracker recorded `notification_type=others`, the FGS had no
  type, and the SystemUI `alarm_clock` slot has `notification=null` (it is driven by
  `NextAlarmController`/AlarmManager). Therefore `CATEGORY_ALARM` is not a reliable end key on this
  alarm app. The only generic signals observed were the FGS code `ALARM_MANAGER_ALARM_CLOCK` and the
  notification removal of that FGS notification; both need a generic, non-package SystemUI accessor
  before they can be used. Do not implement per-app package matching.

**Static SystemUI hooks for the notification source:**

- `com.android.systemui.statusbar.notification.collection.NotifCollection`:
  `postNotification(StatusBarNotification, Ranking)` and
  `m6178$$Nest$monNotificationRemoved(StatusBarNotification, RankingMap, int)`.
- `NotifPipeline.addCollectionListener(NotifCollectionListener)` / `removeCollectionListener`.
- The adapter must filter by `Notification.CATEGORY_ALARM` and/or the SystemUI alarm FGS marker;
  record the exact category in the prototype measurement. Do not filter by the alarm app package.

- Evidence dump: `from users/alarm-20261028/` (CPH2747, 2026-10-28 15:09 +03):
  `duo-alarm-after-screen.png`, `duo-alarm-after.txt`, `duo-alarm-before.txt`,
  `duo-alarm-logcat-after.txt`, `duo-alarm-logcat.txt`, `duo-alarm-ring.txt`,
  `duo-alarm-screen.png`, `duo-alarm-systemui.txt`, `duo-alarm-wait-1.png`.

### Flashlight / torch

`com.android.systemui.statusbar.policy.FlashlightControllerImpl`:

- Constructor uses `android.hardware.camera2.CameraManager`.
- Tracks `mFlashlightEnabled`, `mTorchCallback`, `mListeners`, `mHasFlashlight`.
- Exposes `addCallback`/`removeCallback`, `isEnabled()`, `isAvailable()`, `setFlashlight(...)`, and
  `dispatchListeners(int, boolean)`.
- A camera torch callback updates the enabled state and dispatches listeners.

Consequence: flashlight is a **stateful event** (while active), not a one-shot; the verified source
is the SystemUI flashlight controller/camera torch callback, not a third-party app.

**Measured on CPH2747, 2026-10-28 (flashlight toggles):**

- The Duo hook (`FlashlightControllerImpl` / `dispatchListeners`) loaded in **4 SystemUI processes**;
  the latest hook line is at `16:05:31.582` (pid `20976`).
- `live event -> FLASHLIGHT` fired **6 times**, each followed by `live event -> none` when the torch
  was turned off (while-active semantics confirmed: on -> off -> fade/restore).
- The alarm hook (`AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED`) was installed in the same window but
  **no alarm broadcast was delivered**; the next alarm was in the future, so no `live event -> ALARM`
  fired. This confirms the flashlight source fires independently of the alarm source and does not
  depend on an alarm broadcast.

### Prototype icon candidates (decoded resources)

Paths from `/tmp/duo-systemui-res` (proprietary OEM resources; prototype-only):

| Event | Candidate | Type | Dimensions |
|---|---|---|---|
| Alarm (status) | `res/drawable/stat_sys_alarm.xml` | Vector | 19×16 dp |
| Alarm (AOD) | `res/drawable/ic_alarm.xml` | Vector | 17×17 dp |
| Alarm (big) | `res/drawable/ic_access_alarms_big.xml` | Vector | 18×18 dp |
| Flashlight | `res/drawable/ic_flashlight_off.xml` | Vector | 24×24 dp |
| Flashlight active | `res/drawable/status_bar_qs_flashlight_active.xml` | Vector | 24×24 dp |
| Flashlight inactive | `res/drawable/status_bar_qs_flashlight_inactive.xml` | Vector | 24×24 dp |
| Camera/privacy | `res/drawable/privacy_icon_camera.xml` | Vector | 16×16 dp |
| Camera/privacy | `res/drawable/icon_camera_in_use.xml` | Vector | 24×24 dp |

Import these into a prototype-only asset folder, not the release resource set. Measure the actual
rendered pixels before choosing the final artwork.

## 4. Proposed components and ownership

```
event source adapter
      |
      v
LiveEventMonitor  (dedupe, rate limit, consent, source -> event)
      |
      v
EventArbiter      (priority, single active event, queue/coalesce, duration)
      |
      v
EventPresenter    (event -> DuoVisual + Rive/Canvas contract)
      |
      +--> Rive Event layer  (one-shot icon motion, returns to state)
      |
      +--> Canvas event clone (same geometry/opacities/timing)
      |
      v
DuoStateMonitor -> host.render -> status bar / keyguard / shade
```

### 4.1 Data contract

```kotlin
enum class LiveEventType { NONE, ALARM, SCREENSHOT, MIC, LOCATION, VOLUME, UNLOCK }

data class LiveEvent(
    val type: LiveEventType,
    val priority: Int,          // resolved by the arbiter, not by the source
    val source: String,         // structured reason, never PII
    val startedAtElapsed: Long,
    val durationMs: Long?       // null = while the source is active
)
```

`DuoVisual` should gain only presentation fields, not event logic:

```kotlin
val eventType: Int = 0        // 0 none; Rive/Canvas mapping table owns the rest
val eventPhase: Float = 0f    // 0..1 within the event, for Canvas; Rive owns its easing
val eventVisible: Boolean = false
```

The event **duration** and **priority** stay in Kotlin. Rive owns the motion/easing of the icon; one
property, one owner. The presentation returns to the previous middle state when the arbiter says the
event ended. No two active animations own the same property.

### 4.2 Event arbiter

- One active presentation at a time.
- Fixed middle occupant is captured as `previousMiddle`; it is restored on end.
- P0 event may preempt P1; P0 vs P0 needs an explicit policy (queue vs latest-wins).
- Repeated identical events within a debounce window are coalesced.
- A maximum event duration prevents a lost end-broadcast from pinning an icon forever.
- Screen off/teardown clears the active event and restores the state on next attach.

### 4.3 Icon variants vs animation ownership (owner clarification)

The **animation belongs to the event type, not to an individual icon**. Multiple icon variants (for
example four alarm shapes in an icon pack) share the same alarm animation.

- `EventType` owns the animation (alarm bounce/shake, screenshot flash, volume pulse, ...).
- `EventIcon` is a replaceable artwork slot. An icon pack maps each icon to an `EventType`.
- A Rive event plays transform/alpha/motion around the icon slot; it does **not** require a separate
  animation per icon.
- 16 icons and 4 animations do not mean 64 animations. It means 4 animations and an
  `icon -> animation` map. The combination matrix is a **test** concern (each icon must play its
  assigned animation and return correctly), not an authoring multiplication.
- If an icon has no assigned animation, use the event type's default; never duplicate an animation
  per icon.

### 4.4 Rive contract (to be authored, not guessed)

A new `Event` layer (or a dedicated state machine) with:

- `eventType` number property: 0 none, 1 alarm, 2 screenshot, 3 mic, 4 location, 5 volume, 6 unlock.
- An `eventIcon`/artwork selector boundary: icon swap is independent of the event animation.
- A trigger/phase input; the transition itself is one-shot.
- Return transition to the current `MiddleSlot` state (Wi-Fi / network / airplane / DND / clock /
  percent / charging).
- No idle loop; pause when hidden or idle.

The alarm shape/icon must come from an owned asset or a drawn vector. Do not copy OEM art into a
release build (UI-013); the corpus is prototyping only.

### 4.5 Canvas contract

- Draw the selected icon geometry at the same seat and scale.
- Use the event-type timeline (not a per-icon timeline); an icon swap only changes the drawing, never
  the motion contract.
- Same fade-in/flash/bounce/pulse timing as the selected event.
- Same return to the previous middle state.
- A pure `EventGeometry`/timeline helper so unit tests can assert phases without a bitmap.

### 4.6 Icon-pack assignment table (to be filled from the owner's packs)

| Icon variant | Event type | Animation | Evidence |
|---|---|---|---|
| Alarm A/B/C/D | ALARM | alarm bounce/shake | icon asset + headless capture |
| Screenshot A/B/... | SCREENSHOT | screenshot flash | icon asset + headless capture |
| Mic A/B/... | MIC | mic pulse | icon asset + headless capture |
| Location A/B/... | LOCATION | location pulse | icon asset + headless capture |
| Volume A/B/... | VOLUME | volume pulse | icon asset + headless capture |

The table is the single source of truth for the icon → animation mapping; adding an icon does not
add an animation, only a row.

## 5. Event source strategy (evidence first)

| Event | Candidate source | Status / why it is not wired |
|---|---|---|
| Charging | Existing battery sticky broadcast | Works; keep as baseline. |
| Alarm | `StatusBarIconView` alarm icon, `AlarmManager` next-alarm state, or alarm app notification | **Unknown**: next-alarm icon is not the same as ringing; NotificationListener needs consent. Must be measured. |
| Screenshot | SystemUI screenshot controller / screenshot UI hook | **Unknown**: version/class differs; needs decompilation of CPUI/SystemUI. |
| Microphone | Privacy indicator / status icon / AppOps | **Unknown**: permission/privacy surface; must not log app names or audio. |
| Location | Privacy indicator / status icon / AppOps | **Unknown**: same privacy rule; no coordinates. |
| Volume | `AudioManager` volume-change broadcast/stream | Feasible, but needs a decision on which streams and debounce. |
| Unlock | Existing `ACTION_USER_PRESENT` | P1; already observed for reveal, needs event semantics. |

No source may be guessed from a class name. Each source gets an adapter, a measured evidence row, and
a fallback row in the test matrix.

## 6. Return / priority / duration decisions still needed

Proposed defaults (not approved):

- Alarm: while ringing, plus a bounded tail; motion = bounce/shake.
- Screenshot: ~800 ms flash.
- Mic/Location: while the privacy indicator is active; bounded maximum.
- Volume: ~1.2 s pulse.
- Unlock: ~1 s, P1.
- Charging: existing animation; not an event takeover.
- New P0 while another P0 is active: latest-wins with a short cross-fade, unless the owner chooses a
  queue.
- New P1 while P0 active: drop/coalesce; P1 does not interrupt P0.
- Return: always to the **current** middle state (not the state at event start if Wi-Fi/airplane/
  clock changed during the event).

## 7. Test path (must exist before implementation is called done)

### Unit / pure
- Arbiter: priority, preemption, queue/coalesce, debounce, max duration, clear on teardown.
- Mapping: event -> visual fields; return to prior middle; no event = byte-identical old output.
- Timeline/geometry: phases for alarm bounce, screenshot flash, volume pulse.
- Consent: missing/denied source -> no event + reason, no crash.
- Redaction: no SSID, IMEI, serial, notification text, location, app name in event logs.

### Robolectric / instrumentation
- Broadcast/receiver lifecycle: start/stop, duplicate broadcasts, screen off/on, rotation.
- Attach/hide/restore with an active event.
- App settings: per-event toggle, duration, priority display; backup round-trip includes them.

### Rive headless
- `rive --verify` after every scene change.
- `--screenshot` for each event at start/mid/end and each return state.
- Hash compare `duo.riv` with a fresh build of `scene.rml`.

### Device (emulator first; phone only when the owner says so)
- TP matrix: every event x status bar/keyguard/shade x Rive/Canvas x light/dark.
- Trigger each event via a debug-only source; capture screenshots/video and logs.
- Rapid events, event during charging/DND/airplane/clock, rotation, screen off, QS/shade open.
- Performance: no idle loop, p95 frame <=16.7 ms, memory/battery within budgets.
- Failure: source unavailable, permission denied, binding timeout, renderer fallback.

## 8. Requirement-to-test map

| Requirement | Test evidence |
|---|---|
| LIVE-001/LIVE-002 | Event source matrix + priority unit tests |
| LIVE-003 | Rive headless + Canvas timeline + device capture |
| LIVE-004 | Per-event headless screenshots + Canvas phase tests |
| LIVE-005 | Consent/failure tests; hook evidence in repair catalog |
| LIVE-006 | Missing-source/permission tests; no crash |
| LIVE-007 | Receiver-only code audit + gfxinfo/battery |
| CUS-006 | Per-event/per-icon toggles + preview |
| TST-005/TST-009 | Structured log + redaction tests |
| RIV-004/RIV-005/RIV-008 | Headless + Canvas parity + device screenshots |
| Icon variants | Each icon plays its assigned event animation; no per-icon animation duplication |

## 9. Implementation sequence (after questions are answered)

1. Write the event contract (`LiveEventType`, `LiveEvent`, `EventArbiter`) and pure tests; no hooks.
2. Add `DuoVisual` fields and Canvas event clone; keep default output identical.
3. Add one source adapter at a time (volume/unlock are easiest; alarm/mic/location need measured
   SystemUI evidence).
4. Author the Rive `Event` layer and rebuild `duo.riv`; verify hash; add headless screenshots.
5. Wire the app settings (per-event toggle, preview, debug trigger) and backup schema.
6. Run unit, Robolectric, headless, emulator; phone only with owner approval.
7. Add repair-catalog entries and evidence paths.

## 10. Questions — resolved and still open

**Resolved by the owner:**

- Alarm: set **and** ringing; use Android/SystemUI, not per-app packages.
- Preemption: newest event cancels the previous one and takes its place.
- Durations: alarm while ringing + ~1 s intro; flashlight while active; transient events per source.
- Animation: per event type, not per icon; icons map to an animation.
- Icons: take them from the phone/SystemUI for the prototype; OEM assets are prototype-only.
- Clock: treated as a fixed middle occupant (not an event).

**Resolved after the architecture review:**

- Three surfaces: status bar, keyguard and shade/QS, same behavior.
- Return to the current middle state.
- Prototype: alarm + flashlight + screenshot.
- Event icon scales with the ring; alarm jumps/shakes within the ring bounds; flashlight is a pressed
  button while torch is on, then fades after press-off.
- v1 is SystemUI/hook-only with graceful degradation.

**Final alarm-end decision (owner, 2026-10-28):** do not use a notification/ring-end source. Alarm is
a one-shot event with a fixed animation duration (prototype `1200 ms`), then it fades and restores the
current middle state. The measured notification evidence above is retained as history, not as a
dependency. No `NotifCollection` probe is installed.

**Still open (not blocking the animation prototype):**

1. **Icon inventory:** which icon variants exist in the first pack (for example the four alarm
   shapes), and the exact `icon -> event type` mapping. The animation is per event type; the icon
   list/assets are still needed. Start with one shape (`iconKey="default"`).
2. **Screenshot source:** the exact SystemUI screenshot controller/UI hook on CPH2747 is not yet
   measured; do not invent it.
3. **Location/mic source:** privacy-indicator/status-icon source is not yet measured; keep for after
   the three-event prototype.
4. **Per-event switch UI** is implemented; previews/question marks are still Phase 3 UI work.

## 10.1 First implementation slice (status: alarm + flashlight implemented, device pending)

Runtime log check (2026-10-28 20:13 report) diagnosed the earlier build: the module was on Rive and the
flashlight source fired, but the Event layer only had an ALARM transition (`eventType == 1`), so
`eventType == 7` could not render. That gap is now closed.

Completed:

- `LiveEvent`/`EventArbiter` contract + unit tests, no hooks (`377bf7a`, newest-wins).
- Per-event live toggles (`f83b931`).
- Flashlight adapter, verified `FlashlightControllerImpl` source, while-active semantics (`f8995e5`).
- Alarm one-shot adapter via `AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED`; `1200 ms` transient,
  no notification/ring-end dependency; log-only.
- Rive `EventFlashlight` state + `EventFlashlight`/`EventFade` animations; `EventAlarm` now also
  clears the flashlight artwork and shows the alarm artwork, so the two event shapes never overlap.
  Flashlight press is 12 frames (200 ms), then holds for as long as the torch is on; off fades in
  12 frames and returns the current middle occupant.
- Canvas event clone draws the same alarm/flashlight silhouettes in the Rive `eventGroup` seat,
  hides the normal middle occupant while the event is visible, and leaves ring/signal dots intact.
- Owner feedback (CPH2747, 1.4.3-beta.2): event icons were still too small and sat low; Wi-Fi/5G
  size and center were right. The event slot moved to y=1 (the 5G text origin, not the Wi-Fi arc
  origin at 17), and the artwork was scaled to 2.0x (alarm ~40 units high, flashlight ~42 x 28) in
  both Rive and Canvas. Version bumped to 1.4.3-beta.4 (code 27).
- Flashlight false-positive fixed: `dispatchListeners(int, boolean)` is multiplexed; only `what == 1`
  is the torch state. The hook now ignores availability/night-light/error callbacks (RC-015).
- Screenshot event added: `TakeScreenshotExecutorImpl.executeScreenshotsAsync` is the shared request
  entry; one 1000 ms transient, an owned outline-camera Rive state (`EventScreenshot`, 60 frames) and
  the same Canvas clone. This is static/REA evidence; on-device verification is pending (RC-016).
- Simulation section added to Settings: pick Alarm or Flashlight, then play the real Duo Rive event
  animation. The icon row is the selected event prototype; OEM reference vectors stay archived in
  `agent/event-icons/prototype/` and are not packaged (UI-013).
- Unit test: `DuoMappingTest.event fields pass through without changing the middle occupant`.
- Rive headless evidence: idle Wi-Fi, alarm at 400 ms, flashlight at 1 s; `--verify` clean;
  `tools/check-rive-eases.py rive/duo/scene.rml` 132/132 eased, 0 inert; log-route check clean.
- `duo.riv` SHA-256 `540bb0884324b1f4303b22debdfb943cb4ad9bc04ce3fba0cacaa3896269e201`
  (build identical to `app/src/main/res/raw/duo.riv`).
- 317 tests, 0 failures; APK `e4effec1fbc1396a4a74056601e949cf03eb2305aa75d5eb985ba241b0431a3a`.

Pending:

- Microphone and location sources + their event artwork/animations (screenshot is now implemented).
- Device evidence: alarm set/ring -> animation end; flashlight on -> off; newest-wins; return to
  current middle state on status bar, keyguard and shade.
- Owned release art (OEM prototype assets remain UI-013-only).

Event icon shape seam defaults to one variant, extensible to four, without changing animations or
event logic; per-event switches remain independent.

## 11. Final decision (2026-10-28)

- No notification/ring-end source; alarm is one-shot 1200 ms; flashlight while active.
- Alarm probe removed; NextAlarm is the only start source.
- Rive and Canvas both render alarm + flashlight; screenshot/mic/location remain next.
- 317 tests; APK `e4effec1fbc1396a4a74056601e949cf03eb2305aa75d5eb985ba241b0431a3a`.

