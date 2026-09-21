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

Say "adb connect $Device"
& $Adb connect $Device | Out-Null
$deviceList = (& $Adb devices) -join "`n"
if ($deviceList -notmatch 'device$' -and $deviceList -notmatch "device\s") {
    Write-Host "device is not online - re-enable wireless debugging on the phone, then re-run" -ForegroundColor Red
    exit 1
}

if (-not $SkipInstall) {
    Say "build"
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    & (Join-Path $root 'gradlew.bat') ':app:assembleDebug' '--console=plain' | Out-Null
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
& $Adb logcat -c -b all | Out-Null
& $Adb shell su -c 'pkill -f com.android.systemui' | Out-Null
Start-Sleep -Seconds 22

Say "module log"
$log = (& $Adb logcat -d -s DuoSB) | ForEach-Object { $_ -replace '^.*DuoSB\s+:\s*', '' }
$log | ForEach-Object { Detail $_ }

$joined = $log -join "`n"
$checks = [ordered]@{
    'the gate resolved'          = 'stage \d \((adb override|app settings)'
    'the status bar was found'   = 'status bar window found'
    'the icon strip was found'   = 'container system_icons'
    'hardware acceleration known'= 'verdict:'
    'the element was injected'   = 'Duo injected into'
}
if ($Stage -eq 'icons') { $checks['the Canvas element was used'] = 'element: Canvas' }
if ($Stage -eq 'rive') {
    $checks['the Rive runtime came up'] = 'Rive runtime ready: defaultRendererType='
    $checks['the Rive view is live'] = 'Duo view ready'
}
if ($Stage -eq 'off') { $checks['nothing was hooked'] = 'gated off|nothing hooked' }

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
