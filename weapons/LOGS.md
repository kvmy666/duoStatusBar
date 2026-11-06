# Logs and evidence sources

What counts as proof for this module, and how to get it exactly.

## Device log sources

| Source | Command / path | Notes |
|---|---|---|
| LSPosed module log | `/data/adb/lspd/log/modules_<boot>.log` | authoritative record of hook install, scope errors, module crash ground truth; needs root. **A case not confirmed from this log is not confirmed.** |
| Module runtime log | `adb logcat -s DuoSB` | filter the module's own tag; works without root |
| Window tree | `adb shell dumpsys window windows` | which window is carrying the injected view; proves top Activity & flags |
| Activity stack | `adb shell dumpsys activity top` | who owns the view hierarchy right now |
| View tree (no root) | `adb shell uiautomator dump` | pulls the UI XML; see [PLAYBOOKS.md](PLAYBOOKS.md) c |
| Frame stats | `adb shell dumpsys gfxinfo com.android.systemui framestats` | jank / dropped frames for the Rive view |
| Memory | `adb shell dumpsys meminfo com.android.systemui` | module's footprint inside SystemUI's process |
| Battery | `adb shell dumpsys batterystats --reset` then read | mistake C8 baseline; see PLAYBOOKS f |
| Package identity | `adb shell dumpsys package <pkg>` | versionName/path of the target SystemUI; PLAYBOOKS h |
| Properties | `adb shell getprop` | ROM fingerprint (ro.product, ro.build); include with every report |

## Platform rule: `android.util.Log` is dropped on OxygenOS

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

## Reading LSPosed's log

```sh
adb shell su -c "ls /data/adb/lspd/log/"
adb shell su -c "cat /data/adb/lspd/log/modules_<boot>.log"
```

`<boot>` matches the ID of the boot you are investigating (pick the latest
one; earlier boots have separate files). Use `grep DuoSB` to focus on this
module's lines.

## Privacy / redaction rule

Evidence may leave the machine in reports or issues — redact before sharing.

- Never commit raw vendor APK contents or decompiled trees (they belong under
  `../reverse/`, gitignored).
- Never commit real phone numbers, IMEIs, SIM identifiers, or account names
  from uiautomator dumps or screenshots; crop/replace them.
- Never commit `.env`, keystores, or the bot token (repo rule; also applies to
  any log excerpt from the app's process, which may carry config).
- Log snippets must be redacted of serials (the device serial in `adb -s`)
  only when you cannot strip it with a placeholder instead.

## Where evidence lands per job

| Evidence | Destination |
|---|---|
| Diagnostic dumps before/after a change | `/tmp/kilo/`, summarized into `../docs/rom-contract.md` notes |
| ROM profile measurements | `../docs/rom-contract.md` |
| Hook-target facts | same, marked measured vs unverified |
| Battery baseline numbers | change/feature notes; measured numbers beat prose |
