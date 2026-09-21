# Installing & enabling (device notes)

Target device used for development: **OnePlus 15 (CPH2747), Android 16 / OxygenOS 16.0.9.400(EX01)**,
KernelSU + LSPosed 2.2.0. Everything here is repeatable on any rooted ROM with LSPosed.

## 1. adb over TCP

```powershell
C:\Users\krom3\Desktop\platform-tools-latest-windows\platform-tools\adb.exe connect 192.168.100.245:6666
```

## 2. Build & install the module

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'   # Gradle needs JDK 17+; the system JDK is 8
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 3. LSPosed Manager

The manager is **not** a normal installed app on this device; it ships inside the KernelSU module at
`/data/adb/modules/zygisk_lsposed/manager.apk` and is installed on demand. Two ways to get it:

* Broadcast the hidden secret code (exactly what the module's `action.sh` does):
  ```powershell
  adb shell su -c 'am broadcast -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733 android'
  ```
* Or install the APK directly (done during Phase 0):
  ```powershell
  adb shell su -c 'cp /data/adb/modules/zygisk_lsposed/manager.apk /data/local/tmp/lsm.apk'
  adb pull /data/local/tmp/lsm.apk reverse/lsm.apk
  adb install -r reverse/lsm.apk      # package org.lsposed.manager, label "LSPosed"
  ```

## 4. Enable the module (human step)

1. Open **LSPosed**.
2. **Modules → Duo Status Bar** → turn it **on**.
3. Make sure the scope **System UI** (`com.android.systemui`) is ticked — that is the only scope this
   module declares, and the only one it may ever need.
4. Restart the target process, or reboot (the module is injected when the process starts).

## 5. The gate: staged enable (how the module is switched on)

The module is **gated OFF by default**: at stage 0 it hooks nothing at all inside System UI, so installing it
cannot change — or destabilise — the status bar. Staging exists because a Rive *native* fault kills the host
process before any `try`/`catch` can run, which means the safety net has to live outside the process
(full evidence: `docs/evidence/phase3-attempt1-crash.txt`).

```powershell
adb shell settings put global duo_statusbar_stage 1   # icons only: Canvas drawing, no native code
adb shell settings put global duo_statusbar_stage 2   # icons + Rive (the real element)
adb shell settings put global duo_statusbar_stage 0   # off (default)
adb shell settings put global duo_statusbar_rive_attempts 0   # clear the crash breaker
adb shell su -c 'pkill -f com.android.systemui'       # the stage is read per process, so restart it
```

**The crash breaker.** The module writes `duo_statusbar_rive_attempts` *before* it creates a Rive view and
clears it once the drawing has survived 4 s. Two attempts that never got cleared mean Rive died twice, and
stage 2 is then refused (Canvas is used instead) until the counter is reset — a bad build cannot crash-loop.

## 6. Verifying a run

```powershell
adb logcat -c; adb shell su -c 'pkill -f com.android.systemui'; Start-Sleep 20; adb logcat -d -s DuoSB
```

Lines to look for, in order:

| Line | Meaning |
|---|---|
| `gated off - nothing hooked. Enable with: …` | stage is 0 — the module deliberately did nothing |
| `application ready: com.android.systemui (stage 1)` | the gate let it through, and at which stage |
| `status bar window found: …` | the status-bar window was identified by `TYPE_STATUS_BAR` |
| `container system_icons -> LinearLayout` | the icon strip was found on this ROM |
| `--- window facts ---` … `verdict: …` | hardware acceleration: whether Rive *can* work here at all |
| `element: Canvas (stage 1)` / `Rive runtime ready: defaultRendererType=…` | which renderer is in use |
| `Duo injected into … (83px wide)` | the element is in the status bar |
| `stock status-bar views removed (GONE + 0x0)` | stock icons hidden for real, not overlaid (FR-08) |

## 7. Rescue path (if anything looks wrong)

1. Kill switch: `adb shell settings put global duo_statusbar_stage 0`, then restart System UI. The module
   hooks nothing again on the next start — no reboot, no uninstall, no root shell needed.
2. If System UI will not settle: LSPosed → Modules → **Duo Status Bar** → **off** → reboot.
3. Last resort: LSPosed safe mode, or uninstall the module. The stock status bar returns in every case.

No system file is ever modified by this module, and nothing is patched on disk: the module only draws.

