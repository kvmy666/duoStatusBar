# Phase 2 Report — Testing, logging, repair

Status: **static gate complete; device/per-UI evidence pending.**

Branch: `phase/2-multi-ui`. Phase 1 merged to `main` at `5485ebf`.

## 1. Structured logging (`DuoLog`)

- `io.github.kvmy666.duostatusbar.hook.DuoLog` emits one stable single-line record through `L`:
  `ui= surface= stage= class= id= hook= result= reason= detail= build=`.
- Values are flattened (no newlines/CR/control chars), length-capped (240 chars for `detail`,
  160 for other fields) and redacted (`ssid`, `imei`, `serial`, `token`, `password`, `secret`,
  `account`, `email`, plus email addresses).
- Stable reason codes are documented in `agent/REPAIR-CATALOG.md` (DuoLog reason codes table).

## 2. 2 MB ring log and redaction

- `L` keeps the last 600 lines or 2 MB (`MAX_RECENT_BYTES`), whichever hits first, under a lock.
- The ring is handed to the app through the settings channel without root, so a report is never empty
  just because `su` is unavailable.
- The same redaction rules apply at the source (`DuoLog.sanitize`); the ring itself stores the
  already-redacted line.

## 3. Report ZIP

- `DuoReport.writeZip` / `LogReporter.writeReportZip` produce a self-contained ZIP:
  `manifest.json`, `diagnostics.txt`, `module-log.txt`, `logcat.txt`, optional `screenshot.png`.
- Manifest carries schema `duo-report/1`, timestamp, app version/build type, device and adapter
  (ROM) maps, and the entry list.
- Pruning keeps the newest 10 reports and 10 text dumps (`DuoReport.prune`).
- Optional screenshot is included only when supplied; otherwise omitted from both disk and manifest.

## 4. PII filter on the LSPosed tail and 2 MB dump cap

- `RootLogs` captures only filtered evidence: logcat `-s DuoSB` and the last 400 `DuoSB`/
  `duostatusbar` lines from the newest LSPosed module log; a full logcat is never captured.
- Device facts are always appended, so a report is never empty.
- The module log handed to the ZIP is the 2 MB ring (`L.recentText()`).

## 5. Capability fields and Xiaomi (FZZ) evidence

- `RomAdapter` carries `coverHook`, `panelExpansionKind`, `panelHookMeasured`,
  `panelOwnerAvailable` (plus the shade fields).
- `panelOwnerAvailable` is `true` only for measured OnePlus/OxygenOS; `false` for AOSP and every
  other UI.
- Xiaomi HyperOS uses `panelExpansionKind = "fzz_3arg"` with `panelOwnerAvailable = false`:
  the HyperOS 17 callback is `onPanelExpansionChanged(float, boolean, boolean)` and is a
  cover/expansion signal only, never a QS-vs-shade owner. See `RC-014`.
- `LogReporter.writeReportZip` exports all of these fields (`coverHook`, `panelExpansionKind`,
  `panelHookMeasured`, `panelOwnerAvailable`) in the adapter block of the report.

## 6. Tests

- Unit tests: 261 (Robolectric + JVM), including `DuoLogTest`, `DuoReportTest`,
  `LogReporterZipTest`, `LRetentionTest`, `ShadeExpansionClassifierTest`, `DuoPrefsDumpTest`,
  `RomDetectionTest`, `RomProfilesTest`, `RootLogsTest`.
- Static gates: `tools/route-module-logs.py --check`, `tools/check-rive-eases.py`,
  Rive verify/build/hash (best effort), authority grep.

## 7. Remaining tasks

- Coverage gate: 80% lines/branches on changed code (unit + Robolectric).
- Instrumentation tests for resolver/attach/hide.
- Device TP-070..TP-079 (report round-trip, no PII, catalog coverage).
- Per-UI panel aliases verification (currently unverified for non-OnePlus UIs).
- Per-UI runtime dumps for six UIs still missing.

## 8. Build result (this run)

- Command: `JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 PATH=... ./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain --offline`
- Result: BUILD SUCCESSFUL (tests 32 executed; release 22 executed, 62 up-to-date on first pass).
- Unit tests: 261, 0 failures (30 test classes).
- Release APK: `app/build/outputs/apk/release/app-release.apk`
  - size: 6,767,500 bytes
  - SHA-256: `01a6b812f6c6ed05b9e4d4b7f28218f639a963daefc32e032d568b208f5ea500`
  - versionCode 24, versionName 1.4.3-beta.1
- Static gates: not re-run in this pass (route-module-logs / rive-eases unchanged).
