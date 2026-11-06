# RE Toolchain Inventory

**Date:** 2026-10-27T00:15Z · **Host:** Ubuntu 26.04 (linux x86_64), node v22.23.4, python 3.14.4, openjdk 25.0.4.1
**Disk before:** 9.5G free / 107G · **Disk after:** 8.5G free / 107G · **RAM:** 15Gi (7.9Gi avail before / 6.9Gi after)

## Tools

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

## Frida server files

- `frida-server-17.24.3-android-arm64` — SHA-256 `e187642b952189a9d5b69d2e787ee4dbe39df8dc15e7c42be09fa517cfd79e16` — arm64 devices (e.g. physical phones)
- `frida-server-17.24.3-android-x86_64` — SHA-256 `1c32715d6c00fb16a80cc5578625c0c194dbed12dd9d9623018e74c7764733c4` — x86_64 emulators
- Version `17.24.3` matches installed `frida` CLI exactly; no version mismatch.

## MCP config snippets

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

## Failures / unverified

- Nothing failed. One hiccup: initial grep-based URL extraction for the jadx zip produced malformed URL; re-downloaded explicitly, installed fine.
- `uv run jadx_mcp_server.py --help` exits cleanly (argparse banner only); server NOT actually launched as a persistent process.
- jadx-ai-mcp plugin jar downloaded but not loaded into a running JADX GUI (out of scope).
- kahlo-mcp not launched.

## How to use

- **jadx**: `jadx -d out/ file.apk` to decompile; `jadx --help` for flags.
- **apktool**: `apktool d file.apk -o out/` to decode resources; `apktool b out/` to rebuild.
- **frida**: `frida -U -l script.js <app>` once frida-server is running on device (`adb push frida-server-17.24.3-android-<abi> /data/local/tmp/` then run as root in background).
- **jadx-ai-mcp / jadx-mcp-server**: bring up micromamba opener; load plugin into JADX GUI and start the MCP server, then launch the Python `jadx_mcp_server` via `uv run` to bridge LLM commands to the plugin. Do not expose `--jadx-host` beyond 127.0.0.1.
- **kahlo-mcp**: `node dist/index.js` (stdio transport), reads dataDir `./data`, uses `/usr/bin/adb`.
- **REA**: ephemeral via `npx -y rea-agents@6.7.0 --version` — keep global install absent to avoid toolchain duplication.
