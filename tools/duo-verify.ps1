<#
    One-command verification of the Duo Status Bar integration on the device.

    Runs the phased test plan in order and prints only what matters:

      1. build and install the module APK
      2. set the stage through the adb override (off | icons | rive)
      3. restart System UI and wait for it to come back
      4. read back the log lines that prove each step happened, and say PASS/FAIL per claim
      5. keep a screenshot as the visual record

    Usage:
      ./tools/duo-verify.ps1                 # default: icons stage (Canvas, no native code)
      ./tools/duo-verify.ps1 -Stage rive     # the real element
      ./tools/duo-verify.ps1 -Stage off      # back to stock; checks the stock icons return
      ./tools/duo-verify.ps1 -SkipInstall    # re-run the checks only

    Every verdict below comes from a log line or a file — nothing is inferred from "it looked fine".
#>
param(
    [string]$Adb = 'C:\Users\krom3\Desktop\platform-tools-latest-windows\platform-tools\adb.exe',
    [string]$Device = '192.168.100.245:6666',
    [ValidateSet('off', 'icons', 'rive')][string]$Stage = 'icons',
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$stageValue = @{ off = 0; icons = 1; rive = 2 }[$Stage]

function Say($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Detail($text) { Write-Host "    $text" -ForegroundColor Gray }

<#
    Reads the module's own log lines back out of LSPosed's log file.

    Why not just logcat: on the target ROM (OxygenOS 16) `android.util.Log` output from SystemUI never
    reaches logcat - logd is filtered and the tag is simply absent, even though the code ran. LSPosed
    writes what the module sends through `XposedBridge.log` into
    /data/adb/lspd/log/modules_<boot>.log instead, so that file is the record that can be trusted here.
#>
function Get-ModuleLines {
    $remote = '/sdcard/Download/duo-lsposed.log'
    # LSPosed starts a new modules_*.log per boot, so pick the newest: `cp a b dest` would fail and the
    # checks would then fail with no lines at all, which is the exact confusion this script exists to avoid.
    $newest = (& $Adb shell su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1' 2>&1 | Select-Object -First 1)
    if ($newest) { $newest = $newest.Trim() }
    if (-not $newest -or $newest -notmatch 'modules_.*\.log$') { return @() }
    # Remember which file this came from: after a restart LSPosed may roll to a new modules_*.log, and a
    # line count from the *old* file then slices the new one from the wrong place - silently hiding the
    # very lines the checks look for. The caller compares file names, not just counts.
    $script:lastLogFile = $newest
    & $Adb shell su -c "cp '$newest' $remote; chmod 666 $remote" 2>&1 | Out-Null
    $local = Join-Path $root 'docs\evidence\lsposed-modules.log'
    & $Adb pull $remote $local 2>&1 | Out-Null
    & $Adb shell rm $remote 2>&1 | Out-Null
    if (-not (Test-Path $local)) { return @() }
    return @(Get-Content $local |
        Where-Object { $_ -match 'DuoSB \| ' } |
        ForEach-Object { ($_ -split 'DuoSB \| ', 2)[1] })
}

Say "adb connect $Device"
$connectOutput = (& $Adb connect $Device 2>&1) -join ' '
Detail $connectOutput
$derived = (& $Adb devices 2>&1) | Where-Object { $_ -match ("^" + [regex]::Escape($Device) + "\s+") } | Select-Object -First 1
$state = if ($derived -and $derived -match '\s+(\S+)\s*$') { $Matches[1] } else { 'not listed' }
if ($state -ne 'device') {
    Write-Host "device state: '$state' - the phone is not reachable from here." -ForegroundColor Red
    Write-Host "  wake the phone, keep it on the same Wi-Fi, then re-enable 'Wireless debugging' in" -ForegroundColor Yellow
    Write-Host "  Developer options (its port changes on every toggle - pass -Device <ip:port> if so)." -ForegroundColor Yellow
    exit 1
}
Detail "device online: $Device"

if (-not $SkipInstall) {
    Say "build"
    # Gradle needs JDK 17+; the system JDK on this machine is 8, and a fresh process has no JAVA_HOME.
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    $built = & (Join-Path $root 'gradlew.bat') ':app:assembleDebug' '--console=plain' 2>&1
    $built | Select-String 'BUILD SUCCESSFUL|BUILD FAILED' | ForEach-Object { Detail $_.Line.Trim() }
    if (-not ($built | Select-String 'BUILD SUCCESSFUL')) {
        Write-Host "build failed - not installing a stale APK" -ForegroundColor Red
        $built | Select-Object -Last 12 | ForEach-Object { Detail $_ }
        exit 1
    }
    if (-not (Test-Path $apk)) {
        Write-Host "build produced no APK" -ForegroundColor Red
        exit 1
    }
    Say "install"
    & $Adb install -r $apk | Select-String 'Success|Failure' | ForEach-Object { Detail $_.Line }
}

Say "stage -> $Stage ($stageValue)"
& $Adb shell settings put global duo_statusbar_stage $stageValue | Out-Null
Detail "stage is now $(& $Adb shell settings get global duo_statusbar_stage)"

Say "restart System UI"
$beforeLines = @(Get-ModuleLines)
$logFileBefore = $script:lastLogFile
$moduleLinesBefore = $beforeLines.Count
& $Adb logcat -c -b all | Out-Null
& $Adb shell su -c 'pkill -f com.android.systemui' | Out-Null
Start-Sleep -Seconds 22

Say "module log"
# Two sinks, because neither is enough on its own: LSPosed's own log is the one that survives
# OxygenOS's filtered logd (android.util.Log from SystemUI never reaches logcat there - see L.kt),
# while logcat covers every other ROM. The module writes to both, so the claims below hold either way.
$moduleLines = @(Get-ModuleLines)
$newLines = if ($script:lastLogFile -ne $logFileBefore) {
    # A new file is a new record: every line in it belongs to this restart, so none may be sliced away.
    $moduleLines
} elseif ($moduleLines.Count -gt $moduleLinesBefore) {
    $moduleLines[$moduleLinesBefore..($moduleLines.Count - 1)]
} else {
    $moduleLines
}
$logcatLines = @((& $Adb logcat -d -s DuoSB) | ForEach-Object { $_ -replace '^.*DuoSB\s+:\s*', '' })
$log = @($newLines) + @($logcatLines) | Where-Object { $_ }
$log | ForEach-Object { Detail $_ }

$joined = $log -join "`n"
$checks = [ordered]@{
    'the gate resolved'          = 'stage \d \((adb override|app settings)'
}
# `off` is the kill switch: the correct outcome is that nothing was hooked at all, so the injection
# checks below would be false failures there - and demanding them would hide the one claim that matters.
if ($Stage -eq 'off') {
    $checks['nothing was hooked'] = 'gated off|nothing hooked'
} else {
    $checks['the status bar was found']    = 'status bar window found'
    $checks['the icon strip was found']    = 'container system_icons'
    $checks['hardware acceleration known'] = 'verdict:'
    $checks['the element was injected']    = 'Duo injected into'
}
if ($Stage -eq 'icons') { $checks['the Canvas element was used'] = 'element: Canvas' }
if ($Stage -eq 'rive') {
    $checks['the Rive element was chosen'] = 'element: Rive'
    $checks['the Rive runtime came up'] = 'Rive runtime ready: defaultRendererType='
    $checks['the Rive view is live'] = 'Duo view ready'
    $checks['the app sees the Rive renderer'] = 'renderer=Rive'
}

Say "verdict"
$failed = 0
foreach ($claim in $checks.Keys) {
    if ($joined -match $checks[$claim]) {
        Write-Host ("    PASS  {0}" -f $claim) -ForegroundColor Green
    } else {
        Write-Host ("    FAIL  {0}  (no line matching /{1}/)" -f $claim, $checks[$claim]) -ForegroundColor Red
        $failed++
    }
}

$shot = Join-Path $root "docs\evidence\verify-$Stage.png"
& $Adb shell screencap -p /sdcard/duo-verify.png | Out-Null
& $Adb pull /sdcard/duo-verify.png $shot 2>&1 | Out-Null
& $Adb shell rm /sdcard/duo-verify.png | Out-Null
Say "screenshot: $shot"

if ($failed -gt 0) {
    Write-Host "`n$failed claim(s) failed - read the log above; the status bar is never left broken by design (it falls back or stays stock)." -ForegroundColor Yellow
    exit 1
}
Write-Host "`nall claims verified at stage '$Stage'." -ForegroundColor Green
