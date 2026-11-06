# Model and Agent Routing

## Hard rules

- **Never use GLM.**
- **BEAM never writes or edits code.** It is limited to search, inventory, log/docs extraction, and mechanical build/install/test commands with no source edits.
- **DeepSeek** (`opencode-go/deepseek-v4.1-flash`) is the only coding model (or the main agent). It is also the ceiling for architecture, debugging, and critical review.
- Never use expensive models (kimi-k3, gpt-6-luna, claude-opus, etc.).
- No `max`/`high` variant unless the owner asks.

## Routing table

| Task | Agent | Model / variant |
|---|---|---|
| Search, inventory, log reading, docs extraction | `explore-fast` | BEAM (default) |
| Mechanical edit/build/install with no architecture decision | `builder` | BEAM (default) |
| Architecture, multi-file code, debugging, critical review | `builder` / `planner` / `review-strict` | `deepseek-v4.1-flash` low/medium |
| Plan decomposition only | `planner` | `deepseek-v4.1-flash` |
| Adversarial review | `review-strict` | `deepseek-v4.1-flash` |

## Tool-limit workaround

`general` with BEAM fails when the tool schema exceeds 128 items (the REA tool set). Use:

- `explore-fast` (BEAM, `rea_*` denied) for read-only recon;
- `builder` (BEAM, `rea_*` denied) for mechanical work;
- do **not** load REA into a BEAM agent.

## Context and cost

- Grep/glob first; read only the needed ranges.
- Cap shell output with `head`/`tail`.
- Batch independent calls.
- Do not dump full build logs or full `uiautomator` XML into context; parse and summarize.
- After two failed attempts at the same problem, stop and report with the evidence.
- Use separate worktrees/branches for parallel agents; never two agents on the same files.

## Examples

- "Where is `findShadeIconsArea`?" -> `explore-fast`, BEAM.
- "Add a resolver test" -> `builder`, DeepSeek.
- "Why does the shade repopulate icons?" -> `builder`, DeepSeek (needs code reasoning).
- "Install Rive CLI" -> `builder`, BEAM (mechanical).
- "Review this diff for regressions" -> `review-strict`, DeepSeek.
