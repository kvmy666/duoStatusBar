# Skills and agent types

## Built-in skills

These load from `~/.config/kilo/skills/` or builtin. They are not installed;
they arrive with the Kilo config.

| Skill | What it does | Load when |
|---|---|---|
| `reverse-engineer-anything` (REA) | structured RE workflow for native, managed, packaged, firmware, browser targets; decode, compare versions, explain features | any RE task on a vendor APK or the `.riv` / native side; skip for plain source-repo analysis |
| `grill-me` | interview-driven session that stress-tests a plan/design into a capture file on disk, checkpointing after every answer | planning a feature, contract change, or reverse-engineering campaign; "grill me" |
| `kilo-config` | authoritative guidance on Kilo config paths, `kilo.jsonc` fields, MCPs, agents, commands, permissions | anything about agent/mcp/permission configuration |

## Agent types

| Agent | Role |
|---|---|
| `explore-fast` | cheap read-only reconnaissance (search, inventory, log reading, docs extraction) |
| `explore` | deeper read-only exploration and explanation |
| `general` | general-purpose work that must sometimes edit |
| `builder` | implements a specified change |
| `planner` | produces a plan for a defined change |
| `review-strict` | critical code review with the mistakes registry in mind |

## Model-routing rule

Mirror of `../agent/archive/originals/MODEL-ROUTING.md`:

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

## Test wiring notes

- `:app:testDebugUnitTest` plus `assembleDebug` is the standard proof command;
  skills do not replace it.
- The `.riv` hash compare and `tools/route-module-logs.py --check` stay in the
  verification loop regardless of which agent wrote the code.
