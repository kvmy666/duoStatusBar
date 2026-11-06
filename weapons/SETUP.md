# Setup

Reproducible install for the weapons catalog. Nothing here needs root on the
dev machine. Paths:

- Tools root: `[redacted]/.local/share/duo-re-tools`
- Emulator: `[redacted]/Android/Sdk/emulator/emulator`
- adb: `/usr/bin/adb`

## Java / runtime facts (this machine)

- Java 25 is on `PATH`. AGP needs 17+.
- JBR 21 lives at `[redacted]/.jdks/jbr-21.0.11`; **apktool runs with
  it**, e.g.:
  `JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 <apktool-cmd> d ...`
- Node 22, Python 3.14 are available.

## uv (Python)

Used to run Python tooling (`tools/*`) reproducibly. Not on `PATH` today.

```sh
curl -LsSf https://astral.sh/uv/install.sh | sh   # installs into ~/.local/bin
uv --version
```

## JADX

For manual use; the headless MCP jar already exists at
`[redacted]/.local/share/rea/android/jadx-headless-mcp-0.7.1-all.jar`.
Version to install: TBD (see `INVENTORY.md` for the record of what is
present).

```sh
mkdir -p ~/.local/share/duo-re-tools/jadx
# download latest jadx release zip from https://github.com/skylot/jadx/releases
unzip jadx-<ver>.zip -d ~/.local/share/duo-re-tools/jadx
export PATH="$HOME/.local/share/duo-re-tools/jadx/bin:$PATH"
jadx --version
JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 jadx --version
```

## Apktool

Version TBD until installed.

```sh
mkdir -p ~/.local/share/duo-re-tools/apktool
# grab apktool_<ver>.jar per https://apktool.org docs
JAVA_HOME=[redacted]/.jdks/jbr-21.0.11 java -jar ~/.local/share/duo-re-tools/apktool/apktool_<ver>.jar --version
```

## Frida + frida-server

```sh
pip install frida-tools          # versions TBD; pin when confirmed
adb shell uname -m               # device arch for the frida-server binary
# download matching frida-server from https://github.com/frida/frida/releases
adb push frida-server-<ver>-<arch> /data/local/tmp/frida-server
adb shell su -c "chmod 755 /data/local/tmp/frida-server && /data/local/tmp/frida-server &"
frida-ps -U                      # sanity check
```

## jadx-ai-mcp / Kahlo MCP / Android MCP / apktool-mcp-server

Install under `[redacted]/.local/share/duo-re-tools/<name>/`; exact
fetch commands are TBD until first registration. Once installed, register
each in `~/.config/kilo/kilo.jsonc` using the placeholder JSON snippets in
[MCP.md](MCP.md).

Current status of what is actually installed/configured: **see
`.../INVENTORY.md` at the repo root** — that file, owned by another agent,
records present-vs-missing; this SETUP only records how to reproduce.
