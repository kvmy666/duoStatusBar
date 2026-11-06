# MCP servers

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

## REA (`rea-agents@6.7.0`)

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
  lists and logs — apply the redaction rule in [LOGS.md](LOGS.md) before
  committing anything.
- **Use when:** anything on-device or resource decode.
- **Do not use when:** deep Java decompiled-code work — that is jadx-ai-mcp's job.
- **License:** UNKNOWN (see REA package on npm).

## jadx-ai-mcp

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

## Kahlo MCP (Frida bridge)

- **Purpose:** inject Frida scripts into a live SystemUI process to trace
  `StatusBarIconView`, `addView`, `onDarkChanged`, etc., with hooks in Java.
- **Install path:** `[redacted]/.local/share/duo-re-tools/kahlo/`
  (version UNKNOWN until installed — see [SETUP.md](SETUP.md)). Requires
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

## apktool-mcp-server (document-only)

- **Purpose:** decode an APK's manifest and resources to smali/raw XML for
  reference; optionally rebuild. **Capped at decode/analysis.**
- **Rule:** never ship a patched vendor APK, and never rebuild-and-install one.
  Patches in this project live in the module, not in vendor APKs.
- **Install path:** `[redacted]/.local/share/duo-re-tools/apktool/`
  (see [SETUP.md](SETUP.md); apktool is a CLI, the MCP wrapper version TBD).
- **License:** apktool is Apache-2.0; MCP wrapper UNKNOWN.

## Android MCP (document-only)

- **Purpose:** UI automation on a device (tap, dump UI tree, screenshots).
- **Overlaps REA's adb tools; keep it unregistered until a concrete need
  appears.** Do not run both for the same job.
- **Install/version/license:** UNKNOWN.

## Shared security rules

- jadx HTTP server: `127.0.0.1` only, always.
- Kahlo runs JS inside SystemUI and can kill the bar; emulator first.
- Vendor APKs and decompiled trees live under `../reverse/` (gitignored) and
  are never committed.
- No secrets in any MCP config (the bot token never appears here).
