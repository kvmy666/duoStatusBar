# Rive, SELinux and locked-bootloader root (FR-03)

Status: investigation complete. App-side fallback ships; no risky root module is required.

## The question

Users rooted **without unlocking the bootloader** (common on Samsung: Knox-safe / exploit-based
root) reported that the element draws but "animations don't run — it is static". The suspicion was
that Rive animations need an SELinux layer that jailbreak/root-hiding breaks, and whether
[Mountify](https://github.com/backslashxx/mountify) fixes it.

## What the logs actually show

On the affected devices the module log contains, in order:

```
native loaded: libc++_shared.so
native loaded: librive-android.so
Rive runtime ready: defaultRendererType=Canvas
Duo view ready (machines=1, playing=1, inputs=[])
reveal fired (1000ms)
...
Rive renderer paused (idle)
```

So on those devices Rive **does** start and the state machine **is** playing; the arrival animation
fires. What stops is the *idle* motion, about 3 seconds after the last change, by design:

- `DuoRiveView.idleStop` pauses the renderer so a looping idle animation cannot keep drawing at frame
  rate (the fix for the reported 130 mAh vs ~15 mAh drain — see `AGENTS.md` C8).
- Every real change (`render`, `reveal`) restarts it, so charging, the reveal and state changes animate;
  only ambient idle motion is stopped.

That is a battery policy, not SELinux. A genuinely static element (no reveal at all) would show
`renderer=Rive ready=false` and no `Duo view ready`, or no `Duo view ready (playing=1)` line.

## Mountify — verdict

Mountify is a **root-side OverlayFS metamodule** for KernelSU / APatch / Magisk. It mounts module
directories globally and mirrors each file's SELinux context, so module files look like OEM system
files. It requires `CONFIG_OVERLAY_FS` and ships with no warranty ("I am handing you a sharp knife").

- It could help **only** if the failure were `librive-android.so` being unreachable or SELinux-denied
  to SystemUI under root hiding.
- Our evidence shows the native libraries load (the log lines above), so Mountify would **not** change
  the observed "static" behaviour. It is not Rive-specific.
- Comparable alternatives: Zygisk Next / ReZygisk / Zygisk Assistant / Shamiko unmount or deny
  policies, and KernelSU/APatch magic-mount metamodules. None is a guaranteed fix and all are
  root-side, so none is shipped or required by Duo.

## App-side fix (what we do)

1. **Rive is optional and never the only path.** `RiveInit` loads the module's own `.so` by absolute
   path; if that fails (a real SELinux/unmount denial), the host falls back to `DuoCanvasView`, which
   needs no native code, so System UI never breaks (`FR-21`).
2. **The evidence is now captured.** `RootLogs` discovers the root shell at known paths
   (`/system/bin/su`, `/data/adb/ksu/bin/su`, `/data/adb/ap/bin/su`, `/data/adb/magisk/su`) and falls
   back to non-root device facts, so the locked-root reports that arrived empty now identify the device.
3. **If a true native-load denial is reported**, capture the SELinux denial (`dmesg`, `logcat -b
   events`, `audit`) and the exact `native … : <Throwable>` line — that is the evidence needed before
   recommending any root module.

## Decision

Keep the battery-safe idle pause as the default and do not require Mountify or any hiding module. Add a
user-facing choice only if the owner wants idle motion back, with the battery trade-off stated. This
matches FR-21 (never break System UI) and FR-12 (prefer a self-contained solution over a dangerous
external one).
