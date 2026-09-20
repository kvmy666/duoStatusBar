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
4. **Reboot** (module changes are picked up at boot).

## 5. Collect the Phase-0 evidence

```powershell
adb logcat -d -s DuoSB > docs\evidence\phase0-oos16.txt
adb logcat -c        # clear before a fresh run
```

Useful single lines:

| Tag/content | Meaning |
|---|---|
| `P-00 uid=1000` | the module really loaded inside the SystemUI process |
| `P-01 FOUND/MISSING …` | which hook targets exist on this ROM |
| `P-02 … id=… vis=…` | the real status-bar hierarchy with ids and sizes |
| `P-03 VERDICT: …` | whether Rive can render inside SystemUI |
| `P-04 EVENT …` | screen on/off, unlock, rotation triggers |

## 6. Rescue path (if anything looks wrong)

LSPosed → Modules → **Duo Status Bar** → **off** → reboot.
The stock status bar always comes back; no system files are ever modified by this module.

## 7. Disable without the UI

```powershell
adb shell su -c 'ls /data/adb/lspd/config/'   # modules_config.db holds the enabled flags
```

The database is owned by the LSPosed daemon — change it through the manager UI, not by hand.
