# Weapons / tools catalog

This folder is the catalog of tools, MCP servers, skills, libraries, and
playbooks used to reverse-engineer and verify vendor SystemUI builds for the
Duo Status Bar module. It documents what exists, how to run it, and when to
reach for it. It does not install anything and does not contain vendor APK
contents.

## The rule: one job → one weapon

Each class of task has exactly one owned tool. Do not run two tools for the
same job; overlapping tools are documented as document-only. Pick from the
decision table below before doing anything else.

## Decision table

| Job | Weapon | Doc |
|---|---|---|
| Find a class/method/resource in a vendor SystemUI APK | jadx-ai-mcp (+ REA `decode_android_resources`) | [MCP servers](#mcp-servers) |
| Trace a live icon hide/show inside running SystemUI | Kahlo MCP (Frida) + REA Frida tools | [MCP servers](#mcp-servers), [Playbooks](#playbooks) |
| Device screenshot / logcat / dumpsys | REA adb tools | [MCP servers](#mcp-servers), [Log sources](#log-sources) |
| Patch a manifest / resource (document-only) | apktool-mcp-server | [MCP servers](#mcp-servers) |
| Runtime target discovery that survives ROM renames | DexKit | [Libraries](#libraries) |
| App ↔ SystemUI settings channel | Existing settings provider (YukiHook DataChannel documented only) | [Libraries](#libraries) |
| UI automation on device | Android MCP (document-only; overlaps REA) | [MCP servers](#mcp-servers) |
| Session planning / grilling / config | skills `grill-me`, `reverse-engineer-anything`, `kilo-config` | [Skills and agent types](#skills-and-agent-types) |
| Step-by-step recipe for any of the above | playbooks | [Playbooks](#playbooks) |
| Where evidence lives and what counts as proof | log sources | [Log sources](#log-sources) |
| Installing any of this on a new machine | setup | [Setup](#setup) |

Agent types and the model-routing rule are in [Skills and agent types](#skills-and-agent-types) and
linked from `../agent/archive/AGENT-DOCS-DEPRECATED.md` (block `## FILE: MODEL-ROUTING.md`); agent working rules are in
`../agent/AGENTS-V2.md` (see also the repo-root `AGENTS.md` mistakes registry).

## Hard rules that apply to every weapon

- Never guess names: hook targets come out of the APK or off a device, and
  surface entries are frozen in `../docs/rom-contract.md`.
- `reverse/` is a local-only, gitignored corpus. Vendor APK contents never
  enter git.
- A case is not confirmed unless it is backed by LSPosed/logs evidence in
  [Log sources](#log-sources).
- Never ship a patched vendor APK. apktool work is analysis-only.

## Inventory

**Date:** 2026-10-27T00:15Z · **Host:** Ubuntu 26.04 (linux x86_64), node v22.23.4, python 3.14.4, openjdk 25.0.4.1
**Disk before:** 9.5G free / 107G · **Disk after:** 8.5G free / 107G · **RAM:** 15Gi (7.9Gi avail before / 6.9Gi after)

### Tools

| Tool | Version | Path | Status | License | Invocation | Verified output |
|---|---|---|---|---|---|---|
| uv | 0.13.5 | [redacted]/.local/bin/uv | installed | MIT/Apache-2.0 | `uv` | `uv 0.13.5 (x86_64-unknown-linux-gnu)` |
| JADX CLI | 1.5.6 | [redacted]/.local/share/duo-re-tools/jadx (symlink ~/.local/bin/jadx) | installed | Apache-2.0 | `jadx <apk>` | `1.5.6` |
| Apktool | 3.0.3 | [redacted]/.local/share/duo-re-tools/apktool (symlink ~/.local/bin/apktool; runs jbr-21.0.11 java) | installed | Apache-2.0 | `apktool d <apk>` | `3.0.3` |
| Rive CLI | 1.8.0 | [redacted]/.rive/bin/rive (NOT on PATH) | installed | see Rive CLI installer/license | `[redacted]/.rive/bin/rive --help` | `rive 1.8.0` |
| Frida tools | 17.24.3 | ~/.local/bin/frida (uv tool) | installed | MIT/wax (tooling) | `frida -U <proc>` | `17.24.3` |
| frida-server | 17.24.3 | [redacted]/.local/share/duo-re-tools/frida-server/ | installed | AGPL-3.0 | push to device, run as root, `frida -U` | SHA-256 recorded (below) |
| jadx-ai-mcp | plugin jar v6.5.0 | duo-re-tools/mcp/jadx-ai-mcp | installed | see repo `LICENSE` | loads inside JADX GUI (**GUI not run here**) | jar downloaded: `jadx-ai-mcp-6.5.0.jar` |
| jadx-mcp-server | repo clone | duo-re-tools/mcp/jadx-mcp-server (uv venv) | installed | see repo `LICENSE` | `uv run jadx_mcp_server.py` | `--help` shows `--jadx-host ... port (default:8650)` |
| kahlo-mcp | repo clone | duo-re-tools/mcp/kahlo-mcp/kahlo-mcp (dist built) | installed | Commons Clause (Draft on Apache-2.0; **no paid-product resale/redistribution**) | `node dist/index.js` | `dist/index.js` exists |
| REA | 6.7.0 | not installed globally (npx only) | installed (runtime via npx) | see npm package | `npx -y rea-agents@6.7.0` | `6.7.0` |
| Android MCP | – | – | document-only | – | – | Not requested: no install was part of scope |
| apktool-mcp-server | – | – | document-only | – | – | Same reason |
| DexKit | – | – | document-only | – | – | Same reason |

### Frida server files

- `frida-server-17.24.3-android-arm64` — SHA-256 `e187642b952189a9d5b69d2e787ee4dbe39df8dc15e7c42be09fa517cfd79e16` — arm64 devices (e.g. physical phones)
- `frida-server-17.24.3-android-x86_64` — SHA-256 `1c32715d6c00fb16a80cc5578625c0c194dbed12dd9d9623018e74c7764733c4` — x86_64 emulators
- Version `17.24.3` matches installed `frida` CLI exactly; no version mismatch.

### MCP config snippets

jadx-ai-mcp (pair with jadx-mcp-server, run AFTER JADX GUI with plugin is loaded separately — GUI was not started in this install):

```json
{
  "mcpServers": {
    "jadx-mcp-server": {
      "command": "[redacted]/.local/bin/uv",
      "args": ["run", "--directory", "[redacted]/.local/share/duo-re-tools/mcp/jadx-mcp-server", "jadx_mcp_server.py", "--jadx-host", "127.0.0.1", "--jadx-port", "8650"]
    }
  }
}
```

kahlo-mcp (config.json already points `adbPath` at `/usr/bin/adb`):

```json
{
  "mcpServers": {
    "kahlo-mcp": {
      "command": "node",
      "args": ["[redacted]/.local/share/duo-re-tools/mcp/kahlo-mcp/kahlo-mcp/dist/index.js"]
    }
  }
}
```

### Failures / unverified

- Nothing failed. One hiccup: initial grep-based URL extraction for the jadx zip produced malformed URL; re-downloaded explicitly, installed fine.
- `uv run jadx_mcp_server.py --help` exits cleanly (argparse banner only); server NOT actually launched as a persistent process.
- jadx-ai-mcp plugin jar downloaded but not loaded into a running JADX GUI (out of scope).
- kahlo-mcp not launched.

### How to use

- **jadx**: `jadx -d out/ file.apk` to decompile; `jadx --help` for flags.
- **apktool**: `apktool d file.apk -o out/` to decode resources; `apktool b out/` to rebuild.
- **frida**: `frida -U -l script.js <app>` once frida-server is running on device (`adb push frida-server-17.24.3-android-<abi> /data/local/tmp/` then run as root in background).
- **jadx-ai-mcp / jadx-mcp-server**: bring up micromamba opener; load plugin into JADX GUI and start the MCP server, then launch the Python `jadx_mcp_server` via `uv run` to bridge LLM commands to the plugin. Do not expose `--jadx-host` beyond 127.0.0.1.
- **kahlo-mcp**: `node dist/index.js` (stdio transport), reads dataDir `./data`, uses `/usr/bin/adb`.
- **REA**: ephemeral via `npx -y rea-agents@6.7.0 --version` — keep global install absent to avoid toolchain duplication.

## Libraries

| Library | Version | License | Runs in | Status |
|---|---|---|---|---|
| DexKit | 2.3.0 | Apache-2.0; `Core/` is LGPL-3.0 | SystemUI process (native + Java API) | Used in doc; integration TBD |
| YukiHook | 1.5.x (DataChannel) | Apache-2.0 | app + host (SystemUI) | Document-only |
| Rive runtime | bundled with rive-android | MIT | SystemUI process | In use |
| libxposed API | TBD | GPL-compatible per project; see docs | SystemUI (via LSPosed) | In use |
| LSPosed | UNKNOWN (per device) | GPL-3.0 (fork of Riru/Zygisk lineage) | Zygisk, system-wide | Delivery mechanism |

### DexKit 2.3.0

- **Solves:** finding obfuscated/renamed classes, fields, methods, and
  resources at runtime by structure/query instead of a hard-coded name — the
  way a target discovery survives ROM renames (HyperOS rewrites, OxygenOS
  refactors).
- **Where it runs:** inside SystemUI, through the module hook; includes a
  native component.
- **Cost/risk:** native `.so` loads inside SystemUI — a crash there follows
  mistake C1's lesson (no untracked native loader, no half-attached module).
  Multi-dex scanning costs CPU once, then caches its dex snapshot.
- **Decision:** use for targets that change names across ROMs; keep the
  frozen `../docs/rom-contract.md` ids as the fast path when they are known.

### YukiHook 1.5.x — DataChannel

- **Solves:** typed key-value or content flow bridging app ↔ hooked host
  process.
- **Where it runs:** both sides.
- **Risk:** IPC surface in vendor SystemUI contexts.
- **Decision: document-only.** The project keeps its existing settings
  provider (replace `DataChannel` Merrill with Yuki's DataChannel once the
  provider path is blocked or impedance mismatch appears). The mistake C5/C11
  lessons require a channel that answers "unreachable vs off" — verify that
  before adopting.

### Rive runtime

- **Solves:** the animated status element (vector/animation runtime, encoder
  + player). Scene source `../rive/`, shipped as `res/raw/duo.riv`.
- **Where it runs:** C++/Skia renderer inside SystemUI (native), Kotlin
  bindings.
- **Risks:** fully enumerated in the mistakes registry — C1 (native
  init/pinned renderer type), C2 (resize), C3 (breaker), M1 (looping state
  machine), M8 (resource resolution, never path), M15 (one size owner).
- **Decision:** in use, with `DuoGuard` breaker and Canvas fallback from the
  same geometry (C9/M13).

### libxposed API

- **Solves:** the hooking API surface the module is written against (replaces
  the legacy `XposedBridge` calls).
- **Where it runs:** inside SystemUI via LSPosed.
- **Risk:** API-vs-LSPosed version skew; guard every hook, fail silent.
- **Decision:** in use; exact version TBD.

### LSPosed

- **Solves:** delivery: module load, scope, enable gating, module logs at
  `/data/adb/lspd/log/modules_<boot>.log` — see [Log sources](#log-sources).
- **Where it runs:** Zygisk/system-wide, must be installed per ROM.
- **Risk:** installation and scope errors mask a working module (mistake M11:
  register the restart receiver before the enable gate).
- **Decision:** primary delivery; root-kill-switch restart flows documented in
  the app; never release beyond betas without owner approval.

## Log sources

What counts as proof for this module, and how to get it exactly.

### Device log sources

| Source | Command / path | Notes |
|---|---|---|
| LSPosed module log | `/data/adb/lspd/log/modules_<boot>.log` | authoritative record of hook install, scope errors, module crash ground truth; needs root. **A case not confirmed from this log is not confirmed.** |
| Module runtime log | `adb logcat -s DuoSB` | filter the module's own tag; works without root |
| Window tree | `adb shell dumpsys window windows` | which window is carrying the injected view; proves top Activity & flags |
| Activity stack | `adb shell dumpsys activity top` | who owns the view hierarchy right now |
| View tree (no root) | `adb shell uiautomator dump` | pulls the UI XML; see [Playbooks](#playbooks) c |
| Frame stats | `adb shell dumpsys gfxinfo com.android.systemui framestats` | jank / dropped frames for the Rive view |
| Memory | `adb shell dumpsys meminfo com.android.systemui` | module's footprint inside SystemUI's process |
| Battery | `adb shell dumpsys batterystats --reset` then read | mistake C8 baseline; see Playbooks f |
| Package identity | `adb shell dumpsys package <pkg>` | versionName/path of the target SystemUI; Playbooks h |
| Properties | `adb shell getprop` | ROM fingerprint (ro.product, ro.build); include with every report |

### Platform rule: `android.util.Log` is dropped on OxygenOS

On OxygenOS (and possibly other vendors) the ROM discards `android.util.Log`
calls made from inside the SystemUI process. A module working perfectly looks
completely dead and every verification reads as FAIL — this is mistake C4 in
`../AGENTS.md`.

Therefore:

- The module logs exclusively through `L` (which also mirrors to
  `XposedBridge`, landing in LSPosed's module log).
- `tools/route-module-logs.py --check` enforces this in CI; run it before
  trusting any "no logs" conclusion.
- If you see nothing in `adb logcat -s DuoSB` on OxygenOS, that is expected;
  read LSPosed's log before concluding the module is off.

### Reading LSPosed's log

```sh
adb shell su -c "ls /data/adb/lspd/log/"
adb shell su -c "cat /data/adb/lspd/log/modules_<boot>.log"
```

`<boot>` matches the ID of the boot you are investigating (pick the latest
one; earlier boots have separate files). Use `grep DuoSB` to focus on this
module's lines.

### Privacy / redaction rule

Evidence may leave the machine in reports or issues — redact before sharing.

- Never commit raw vendor APK contents or decompiled trees (they belong under
  `../reverse/`, gitignored).
- Never commit real phone numbers, IMEIs, SIM identifiers, or account names
  from uiautomator dumps or screenshots; crop/replace them.
- Never commit `.env`, keystores, or the bot token (repo rule; also applies to
  any log excerpt from the app's process, which may carry config).
- Log snippets must be redacted of serials (the device serial in `adb -s`)
  only when you cannot strip it with a placeholder instead.

### Where evidence lands per job

| Evidence | Destination |
|---|---|
| Diagnostic dumps before/after a change | `/tmp/kilo/`, summarized into `../docs/rom-contract.md` notes |
| ROM profile measurements | `../docs/rom-contract.md` |
| Hook-target facts | same, marked measured vs unverified |
| Battery baseline numbers | change/feature notes; measured numbers beat prose |

## MCP servers

Configured in `~/.config/kilo/kilo.jsonc`. Only REA is registered today;
everything else here is documented ahead of registration. Versions that have
not been verified on this machine are marked UNKNOWN.

Install root for local tooling: `[redacted]/.local/share/duo-re-tools`.

| Server | Purpose | Status |
|---|---|---|
| REA | reverse-engineering toolset over adb/decode/scan | Registered |
| jadx-ai-mcp | decompile + search vendor APKs | Documented, not registered |
| Kahlo MCP | Frida script injection into live processes | Documented, not registered |
| apktool-mcp-server | decode/patch manifest & resources | Document-only, do not register |
| Android MCP | device UI automation | Document-only, do not register |

### REA (`rea-agents@6.7.0`)

- **Purpose:** the default reverse-engineering Swiss knife: adb tools
  (screenshot, logcat, dumpsys, shell), Android resource decode, binary
  analysis, scan. Covers the "device evidence" job end to end.
- **Install path:** no local install; run via npx from npm.
- **Invocation:** registered MCP server; tools are prefixed `rea_*` inside
  agent sessions.
- **Config (existing entry, for reference):**

```jsonc
{
  "mcp": {
    "rea": {
      "command": "npx",
      "args": ["-y", "rea-agents@6.7.0", "mcp"]
    }
  }
}
```

- **Security:** adb tools can read device state; outputs may contain package
  lists and logs — apply the redaction rule in [Log sources](#log-sources) before
  committing anything.
- **Use when:** anything on-device or resource decode.
- **Do not use when:** deep Java decompiled-code work — that is jadx-ai-mcp's job.
- **License:** UNKNOWN (see REA package on npm).

### jadx-ai-mcp

- **Purpose:** decompile a vendor SystemUI APK and search it (classes,
  methods, strings, resource ids) with xref confirmation. This is the "find a
  hook target" weapon.
- **Local jar:** `jadx-headless-mcp-0.7.1-all.jar` already present at
  `[redacted]/.local/share/rea/android/jadx-headless-mcp-0.7.1-all.jar`
  (this is the headless MCP variant shipped with REA).
- **Standalone install path:** `[redacted]/.local/share/duo-re-tools/jadx/`
  (when registered separately; download version UNKNOWN until verified).
- **Invocation:** jar launches an HTTP MCP server on localhost;
  `java -jar [redacted]/.local/share/rea/android/jadx-headless-mcp-0.7.1-all.jar`
  (exact flags TBD until first run).
- **Config (placeholder):**

```jsonc
{
  "mcp": {
    "jadx": {
      "url": "http://127.0.0.1:<PORT>" // HTTP only on localhost
    }
  }
}
```

- **Security:** the HTTP server must bind to `127.0.0.1` only — never expose
  it on the LAN. Decompiled vendor code stays in `../reverse/`
  (gitignored); never commit decompiled output.
- **Use when:** locating a class/method/resource id in a vendor APK, confirming
  with cross-references before writing a hook.
- **Do not use when:** you need runtime behaviour — use Kahlo/Frida.
- **License:** UNKNOWN.

### Kahlo MCP (Frida bridge)

- **Purpose:** inject Frida scripts into a live SystemUI process to trace
  `StatusBarIconView`, `addView`, `onDarkChanged`, etc., with hooks in Java.
- **Install path:** `[redacted]/.local/share/duo-re-tools/kahlo/`
  (version UNKNOWN until installed — see [Setup](#setup)). Requires
  `frida-server` running on the device/emulator.
- **Invocation:** MCP server over stdio (exact command TBD).
- **Config (placeholder):**

```jsonc
{
  "mcp": {
    "kahlo": {
      "command": "<TBD>",
      "args": ["<TBD>"]
    }
  }
}
```

- **Security:** scripts execute inside the live SystemUI process. A bad hook
  can crash System UI. Prefer observe-only hooks first; guard everything.
- **Use when:** static analysis cannot answer how an icon is hidden/shown or a
  colour flows at runtime.
- **Do not use when:** the target behaviour can be proven from a diagnostic
  dump or LSPosed log; also avoid on a user's phone — emulator/your own test
  device first.
- **License:** UNKNOWN.

### apktool-mcp-server (document-only)

- **Purpose:** decode an APK's manifest and resources to smali/raw XML for
  reference; optionally rebuild. **Capped at decode/analysis.**
- **Rule:** never ship a patched vendor APK, and never rebuild-and-install one.
  Patches in this project live in the module, not in vendor APKs.
- **Install path:** `[redacted]/.local/share/duo-re-tools/apktool/`
  (see [Setup](#setup); apktool is a CLI, the MCP wrapper version TBD).
- **License:** apktool is Apache-2.0; MCP wrapper UNKNOWN.

### Android MCP (document-only)

- **Purpose:** UI automation on a device (tap, dump UI tree, screenshots).
- **Overlaps REA's adb tools; keep it unregistered until a concrete need
  appears.** Do not run both for the same job.
- **Install/version/license:** UNKNOWN.

### Shared security rules

- jadx HTTP server: `127.0.0.1` only, always.
- Kahlo runs JS inside SystemUI and can kill the bar; emulator first.
- Vendor APKs and decompiled trees live under `../reverse/` (gitignored) and
  are never committed.
- No secrets in any MCP config (the bot token never appears here).

## Playbooks

Eight recipes. Each ends with **evidence to record** and **what would make
this invalid**. Paths assume this repo root and the local-only `../reverse/`
corpus (gitignored).

### a. Find a hook target in a vendor SystemUI APK

1. Load the REA skill; open the window for jadx-ai-mcp
   ([MCP servers](#mcp-servers)).
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

### b. Verify a resource id / class on a live device

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

### c. Capture a live view tree without root

1. Connect a device without root and confirm `adb devices` shows it.
2. `adb exec-out uiautomator dump /dev/tty > /tmp/kilo/ui.xml` (uiautomator
   works over adb without root).
3. Pull the ui dump, plus:
   - `adb shell dumpsys window windows > /tmp/kilo/windows.txt`
   - `adb shell dumpsys activity top > /tmp/kilo/activity-top.txt`
4. If the injected strip exists but is 0×0, note it — that is exactly mistake
   C6/C7's case and must be reported.
5. Never screenshot a screen containing user data as-is — see the redaction
   rule in [Log sources](#log-sources).

**evidence to record:** the captured files (redacted), the element's class,
and measured bounds from the ui.xml.

**what would make this invalid:** a manual re-layout after capture, a capture
taken after the module already fixed the size, or a capture with no
timestamp/state of the bar (locked? shade open?).

### d. Trace `StatusBarIconView` / `addView` / `onDarkChanged` live

Prereq: emulator or your test device; frida-server running; Kahlo MCP
([MCP servers](#mcp-servers)).

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

### e. Before/after diagnostic dump diff (freeze rule)

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

### f. Battery / performance measurement

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

### g. Boot the rooted AOSP emulator and prove `adb root`

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

### h. Pull / inventory an OEM SystemUI APK into the local-only corpus

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

## Setup

Reproducible install for the weapons catalog. Nothing here needs root on the
dev machine. Paths:

- Tools root: `[redacted]/.local/share/duo-re-tools`
- Emulator: `[redacted]/Android/Sdk/emulator/emulator`
- adb: `/usr/bin/adb`

### Java / runtime facts (this machine)

- Java 25 is on `PATH`. AGP needs 17+.
- JBR 21 lives at `[redacted]/.jdks/jbr-21.0.11`; **apktool runs with
  it**, e.g.:
  `JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 <apktool-cmd> d ...`
- Node 22, Python 3.14 are available.

### uv (Python)

Used to run Python tooling (`tools/*`) reproducibly. Not on `PATH` today.

```sh
curl -LsSf https://astral.sh/uv/install.sh | sh   # installs into ~/.local/bin
uv --version
```

### JADX

For manual use; the headless MCP jar already exists at
`[redacted]/.local/share/rea/android/jadx-headless-mcp-0.7.1-all.jar`.
Version to install: TBD (see [Inventory](#inventory) for the record of what is
present).

```sh
mkdir -p ~/.local/share/duo-re-tools/jadx
# download latest jadx release zip from https://github.com/skylot/jadx/releases
unzip jadx-<ver>.zip -d ~/.local/share/duo-re-tools/jadx
export PATH="$HOME/.local/share/duo-re-tools/jadx/bin:$PATH"
jadx --version
JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 jadx --version
```

### Apktool

Version TBD until installed.

```sh
mkdir -p ~/.local/share/duo-re-tools/apktool
# grab apktool_<ver>.jar per https://apktool.org docs
JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 java -jar ~/.local/share/duo-re-tools/apktool/apktool_<ver>.jar --version
```

### Frida + frida-server

```sh
pip install frida-tools          # versions TBD; pin when confirmed
adb shell uname -m               # device arch for the frida-server binary
# download matching frida-server from https://github.com/frida/frida/releases
adb push frida-server-<ver>-<arch> /data/local/tmp/frida-server
adb shell su -c "chmod 755 /data/local/tmp/frida-server && /data/local/tmp/frida-server &"
frida-ps -U                      # sanity check
```

### jadx-ai-mcp / Kahlo MCP / Android MCP / apktool-mcp-server

Install under `[redacted]/.local/share/duo-re-tools/<name>/`; exact
fetch commands are TBD until first registration. Once installed, register
each in `~/.config/kilo/kilo.jsonc` using the placeholder JSON snippets in
[MCP servers](#mcp-servers).

Current status of what is actually installed/configured: **see
[Inventory](#inventory)** — that section records present-vs-missing; this
Setup section only records how to reproduce.

## Skills and agent types

### Built-in skills

These load from `~/.config/kilo/skills/` or builtin. They are not installed;
they arrive with the Kilo config.

| Skill | What it does | Load when |
|---|---|---|
| `reverse-engineer-anything` (REA) | structured RE workflow for native, managed, packaged, firmware, browser targets; decode, compare versions, explain features | any RE task on a vendor APK or the `.riv` / native side; skip for plain source-repo analysis |
| `grill-me` | interview-driven session that stress-tests a plan/design into a capture file on disk, checkpointing after every answer | planning a feature, contract change, or reverse-engineering campaign; "grill me" |
| `kilo-config` | authoritative guidance on Kilo config paths, `kilo.jsonc` fields, MCPs, agents, commands, permissions | anything about agent/mcp/permission configuration |

### Agent types

| Agent | Role |
|---|---|
| `explore-fast` | cheap read-only reconnaissance (search, inventory, log reading, docs extraction) |
| `explore` | deeper read-only exploration and explanation |
| `general` | general-purpose work that must sometimes edit |
| `builder` | implements a specified change |
| `planner` | produces a plan for a defined change |
| `review-strict` | critical code review with the mistakes registry in mind |

### Model-routing rule

Mirror of `../agent/archive/AGENT-DOCS-DEPRECATED.md` (`## FILE: MODEL-ROUTING.md`):

- **Cheap: `opencode-go/beam-free`** — search, inventory, log reading,
  docs extraction, and mechanical build/install/test commands. **BEAM never
  writes or edits code.** Use `explore-fast` or `builder` (both deny REA)
  because a `general` agent with the full REA tool schema exceeds BEAM's
  128-tool limit.
- **Deep: `deepseek-v4.1-flash`** — all code writing/editing, architecture,
  debugging, critical review.
- **Never use GLM.** deepseek is the ceiling. Never route to expensive models
  (e.g. large frontier models). If deepseek cannot do it, a subagent is the
  wrong tool.

### Test wiring notes

- `:app:testDebugUnitTest` plus `assembleDebug` is the standard proof command;
  skills do not replace it.
- The `.riv` hash compare and `tools/route-module-logs.py --check` stay in the
  verification loop regardless of which agent wrote the code.
