# Playbooks

Eight recipes. Each ends with **evidence to record** and **what would make
this invalid**. Paths assume this repo root and the local-only `../reverse/`
corpus (gitignored).

## a. Find a hook target in a vendor SystemUI APK

1. Load the REA skill; open the window for jadx-ai-mcp
   ([MCP.md](MCP.md)).
2. Register the APK with jadx (e.g. `../reverse/SystemUI-device.apk`).
3. Search the candidate class (`StatusBarIconView`, `DarkIconDispatcher`,
   `onDarkChanged`, `addView`) and resource ids in parallel.
4. Confirm every hit with cross-references to real call sites — not a search
   listing alone. Note the dex and dispatch path.
5. Cross-check the frozen surface in `../docs/rom-contract.md`; anything new
   does not ship unverified — carry it as `unverified` until measured.

**evidence to record:** class+method signatures, xrefs (file/line), resource
id values, ROM/build of the APK, and whether the id matched the frozen
contract.

**what would make this invalid:** a matched name with no xref (name exists but
caller never runs on that ROM), or a search done against the wrong APK build.
Mistake M4: package-only matching fails on HyperOS (`packageName="system"`);
match the process name too.

## b. Verify a resource id / class on a live device

1. `adb shell dumpsys package <rom-systemui-package> | grep -i <id-name-of-interest>` — note the resource table.
2. `adb exec-out uiautomator dump /dev/tty` and inspect the tree for the
   live element in the bar.
3. If the module is running, reproduce the module's own Diag tree dump
   (stage, renderer, found ids) and compare to the manifest values.
4. Match process identity with the module's own check (`SystemUiProcess.isTarget`) — never package only (M4).

**evidence to record:** the dumpsys frame for the id, the ui dump snippet, and
the module diag fingerprint; capture timestamp and device/build.

**what would make this invalid:** a stale dumpsys pair (device state changed
between commands) or verifying on a different ROM build than the report.

## c. Capture a live view tree without root

1. Connect a device without root and confirm `adb devices` shows it.
2. `adb exec-out uiautomator dump /dev/tty > /tmp/kilo/ui.xml` (uiautomator
   works over adb without root).
3. Pull the ui dump, plus:
   - `adb shell dumpsys window windows > /tmp/kilo/windows.txt`
   - `adb shell dumpsys activity top > /tmp/kilo/activity-top.txt`
4. If the injected strip exists but is 0×0, note it — that is exactly mistake
   C6/C7's case and must be reported.
5. Never screenshot a screen containing user data as-is — see the redaction
   rule in [LOGS.md](LOGS.md).

**evidence to record:** the captured files (redacted), the element's class,
and measured bounds from the ui.xml.

**what would make this invalid:** a manual re-layout after capture, a capture
taken after the module already fixed the size, or a capture with no
timestamp/state of the bar (locked? shade open?).

## d. Trace `StatusBarIconView` / `addView` / `onDarkChanged` live

Prereq: emulator or your test device; frida-server running; Kahlo MCP
([MCP.md](MCP.md)).

1. Start the emulator or attach to the test device.
2. Attach to the `com.android.systemui` process (`<spawning?>` no; attach
   only). Prefer observe-only hooks first.
3. Hook entry points with entries like:
   - `StatusBarIconView#updateFromExternalState` / `#isIconBlocked` (trace)
   - `ViewGroup#addView` filtered to `status_bar` children (trace)
   - `DarkIconDispatcherImpl#onDarkChanged` (trace, arg dump)
4. Trigger the change that matters (fold/unfold, DND on/off → reader decision
   M12, dark mode toggle).
5. Stop: detach cleanly; never leave a Java hook attached.

**evidence to record:** raw script + traced log lines with timestamps, plus
what triggered them (which change or test id).

**what would make this invalid:** a crash→restart cycle between trace and
analysis, a hook that matches too broadly (stack noise), or analysis from
memory instead of a saved log. Kahlo runs JS in SystemUI and can kill it —
record the fact if it happens.

## e. Before/after diagnostic dump diff (freeze rule)

The freeze rule from the working agreement: prove the default bar still draws
the same pixels.

1. Make one change to the module.
2. Capture the diag dump before (`../docs/rom-contract.md` data or a fresh
   diagnostic from the build) and after.
3. `diff /tmp/kilo/before.txt /tmp/kilo/after.txt > /tmp/kilo/diff.txt`.
4. Expect an empty diff on a working ROM for a default-preserving change; a
   non-empty diff needs an explanation in the change note or a revert.
5. Also run `./gradlew :app:testDebugUnitTest :app:assembleDebug` + the tools
   (logs check, rive ease check, `.riv` hash).

**evidence to record:** diff file, test command output, what the dump covered.

**what would make this invalid:** comparing against an old dump from a
different ROM build, or a diff made after configuring features that change
behaviour.

## f. Battery / performance measurement

1. Reset counters:
   - `adb shell dumpsys batterystats --reset <package>`
2. Let the bar idle: 8h (or at least the fixture's overnight run) per mistake
   C8's baseline (~130 mAh against ~15 mAh).
3. `adb shell dumpsys batterystats com.android.systemui > /tmp/kilo/bstats.txt`
   — filter the module's share; separate Rive active vs paused/idle runs.
4. CPU/frames:
   - `adb shell dumpsys meminfo com.android.systemui > /tmp/kilo/mem.txt`
   - `adb shell dumpsys gfxinfo com.android.systemui framestats > /tmp/kilo/gfx.txt`
5. Repeat with renderer forced to Canvas for parity (expect lower), and with
   the animator idle pause disabled on purpose for the expensive case (C8).

**evidence to record:** bstats deltas (µAh) per config, meminfo stability, gfx
framestats drop counts; the exact idle recipe (screen on/off, condition).

**what would make this invalid:** tracker on a differently-set-up device,
no app-idle/Doze control, or measuring with the bot relay chattering in background.

## g. Boot the rooted AOSP emulator and prove `adb root`

Do **not** run this now; document for fresh setup.

1. `emulator -avd testRootEmu -writable-system &` (emulator binary at
   `[redacted]/Android/Sdk/emulator/emulator`, adb at `/usr/bin/adb`).
2. `adb wait-for-device && adb root` — expect: `adb: rebooting into root mode...`
   (Google Play images refuse; use a non-Play "AOSP" image).
3. `adb wait-for-device && adb unroot && adb root` cycle once.
4. Prove:
   - `adb shell id` shows `uid=0(root)`
   - `adb push <file> /data/local/tmp/ && adb shell cat /data/local/tmp/<file>`
5. With root: read `/data/adb/lspd/log/modules_<boot>.log`.
6. Stop the emulator (`adb emu kill` or Ctrl-C).

**evidence to record:** `adb root` exit, `adb shell id` output as uid=0,
rooted file read succeeding.

**what would make this invalid:** shell id still `2000(shell)` — the image
lacks root; or all these commands "worked" but you cannot actually read the
LSPosed log (that means no root, no LSPosed, or wrong user path).

## h. Pull / inventory an OEM SystemUI APK into the local-only corpus

1. On a root/adb device with frida or similar not needed:
   - `adb shell dumpsys package <rom-systemui-package> | grep -e versionName -e path`
2. `adb pull <apk-path> [redacted]/duoStatusBar/reverse/` (root:
   `adb shell su -c "cp <apk-path> /data/local/tmp/Sys.apk && chmod 644 /data/local/tmp/Sys.apk"` then pull).
3. Decode the extracted APK with apktool-mcp-server **decode-only** (see the
   rule: never ship a patched vendor APK) or point
   jadx-ai-mcp at the file directly.
4. Record into the device table in `../docs/rom-contract.md` only measured
   ids/names; everything else stays `unverified`. The corpus stays under
   `reverse/` (gitignored), never committed.

**evidence to record:** apk path/size/sha256, ROM build (versionName from
step 1), device model.

**what would make this invalid:** an APK from a different device on the same
OEM (rename-triggering), a half-pulled APK, or any file that leaked outside
`reverse/`.
