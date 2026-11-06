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
| Find a class/method/resource in a vendor SystemUI APK | jadx-ai-mcp (+ REA `decode_android_resources`) | [MCP.md](MCP.md) |
| Trace a live icon hide/show inside running SystemUI | Kahlo MCP (Frida) + REA Frida tools | [MCP.md](MCP.md), [PLAYBOOKS.md](PLAYBOOKS.md) |
| Device screenshot / logcat / dumpsys | REA adb tools | [MCP.md](MCP.md), [LOGS.md](LOGS.md) |
| Patch a manifest / resource (document-only) | apktool-mcp-server | [MCP.md](MCP.md) |
| Runtime target discovery that survives ROM renames | DexKit | [LIBS.md](LIBS.md) |
| App ↔ SystemUI settings channel | Existing settings provider (YukiHook DataChannel documented only) | [LIBS.md](LIBS.md) |
| UI automation on device | Android MCP (document-only; overlaps REA) | [MCP.md](MCP.md) |
| Session planning / grilling / config | skills `grill-me`, `reverse-engineer-anything`, `kilo-config` | [SKILLS.md](SKILLS.md) |
| Step-by-step recipe for any of the above | playbooks | [PLAYBOOKS.md](PLAYBOOKS.md) |
| Where evidence lives and what counts as proof | log sources | [LOGS.md](LOGS.md) |
| Installing any of this on a new machine | setup | [SETUP.md](SETUP.md) |

Agent types and the model-routing rule are in [SKILLS.md](SKILLS.md) and
linked from `../agent/archive/originals/MODEL-ROUTING.md`; agent working rules are in
`../agent/AGENTS-V2.md` (see also the repo-root `AGENTS.md` mistakes registry).

## Hard rules that apply to every weapon

- Never guess names: hook targets come out of the APK or off a device, and
  surface entries are frozen in `../docs/rom-contract.md`.
- `reverse/` is a local-only, gitignored corpus. Vendor APK contents never
  enter git.
- A case is not confirmed unless it is backed by LSPosed/logs evidence in
  [LOGS.md](LOGS.md).
- Never ship a patched vendor APK. apktool work is analysis-only.
