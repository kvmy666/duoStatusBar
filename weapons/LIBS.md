# Libraries

| Library | Version | License | Runs in | Status |
|---|---|---|---|---|
| DexKit | 2.3.0 | Apache-2.0; `Core/` is LGPL-3.0 | SystemUI process (native + Java API) | Used in doc; integration TBD |
| YukiHook | 1.5.x (DataChannel) | Apache-2.0 | app + host (SystemUI) | Document-only |
| Rive runtime | bundled with rive-android | MIT | SystemUI process | In use |
| libxposed API | TBD | GPL-compatible per project; see docs | SystemUI (via LSPosed) | In use |
| LSPosed | UNKNOWN (per device) | GPL-3.0 (fork of Riru/Zygisk lineage) | Zygisk, system-wide | Delivery mechanism |

## DexKit 2.3.0

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

## YukiHook 1.5.x — DataChannel

- **Solves:** typed key-value or content flow bridging app ↔ hooked host
  process.
- **Where it runs:** both sides.
- **Risk:** IPC surface in vendor SystemUI contexts.
- **Decision: document-only.** The project keeps its existing settings
  provider (replace `DataChannel` Merrill with Yuki's DataChannel once the
  provider path is blocked or impedance mismatch appears). The mistake C5/C11
  lessons require a channel that answers "unreachable vs off" — verify that
  before adopting.

## Rive runtime

- **Solves:** the animated status element (vector/animation runtime, encoder
  + player). Scene source `../rive/`, shipped as `res/raw/duo.riv`.
- **Where it runs:** C++/Skia renderer inside SystemUI (native), Kotlin
  bindings.
- **Risks:** fully enumerated in the mistakes registry — C1 (native
  init/pinned renderer type), C2 (resize), C3 (breaker), M1 (looping state
  machine), M8 (resource resolution, never path), M15 (one size owner).
- **Decision:** in use, with `DuoGuard` breaker and Canvas fallback from the
  same geometry (C9/M13).

## libxposed API

- **Solves:** the hooking API surface the module is written against (replaces
  the legacy `XposedBridge` calls).
- **Where it runs:** inside SystemUI via LSPosed.
- **Risk:** API-vs-LSPosed version skew; guard every hook, fail silent.
- **Decision:** in use; exact version TBD.

## LSPosed

- **Solves:** delivery: module load, scope, enable gating, module logs at
  `/data/adb/lspd/log/modules_<boot>.log` — see [LOGS.md](LOGS.md).
- **Where it runs:** Zygisk/system-wide, must be installed per ROM.
- **Risk:** installation and scope errors mask a working module (mistake M11:
  register the restart receiver before the enable gate).
- **Decision:** primary delivery; root-kill-switch restart flows documented in
  the app; never release beyond betas without owner approval.
