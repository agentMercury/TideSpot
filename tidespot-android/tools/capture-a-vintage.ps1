# A · 复古户外 Android 截图基线采集
#
# 用法（设备/模拟器已连接）：
#   .\tools\capture-a-vintage.ps1
#   .\tools\capture-a-vintage.ps1 -OutDir .\screenshots\a-vintage-2026-11-16
#
# 只做「安装 + 打开 + 截图」，不改数据；输出可直接与 screenshots/a-vintage 对比。
param(
    [string]$Apk = "app\build\outputs\apk\debug\潮点TideSpot-v1.21.1-debug.apk",
    [string]$OutDir = "screenshots\a-vintage",
    [string]$Package = "com.tidespot.app"
)

$ErrorActionPreference = "Stop"

function Get-Adb {
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "adb"
    )
    foreach ($c in $candidates) {
        if ($c -eq "adb" -or (Test-Path $c)) { return $c }
    }
    throw "adb not found. Set ANDROID_HOME or install platform-tools."
}

$adb = Get-Adb
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

& $adb install -r $Apk | Out-Null
& $adb shell pm grant $Package android.permission.ACCESS_FINE_LOCATION 2>$null
& $adb shell pm grant $Package android.permission.ACCESS_COARSE_LOCATION 2>$null
& $adb shell am force-stop $Package
& $adb shell monkey -p $Package -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 4

function Save-Shot([string]$Name) {
    $remote = "/sdcard/$Name.png"
    & $adb shell screencap -p $remote
    & $adb pull $remote (Join-Path $OutDir "$Name.png") | Out-Null
    & $adb shell rm $remote
    Write-Host "saved $Name.png"
}

# 用 uiautomator 的 content-desc 找按钮，避免写死分辨率坐标。
function Tap-Desc([string]$Desc) {
    & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    [xml]$doc = (& $adb shell cat /sdcard/ui.xml)
    $node = $doc.SelectNodes("//node") | Where-Object { $_.GetAttribute("content-desc") -eq $Desc } | Select-Object -First 1
    if (-not $node) { Write-Warning "node not found: $Desc"; return $false }
    if ($node.GetAttribute("bounds") -match "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        $x = ([int]$matches[1] + [int]$matches[3]) / 2
        $y = ([int]$matches[2] + [int]$matches[4]) / 2
        & $adb shell input tap $x $y
        Start-Sleep -Milliseconds 800
        return $true
    }
    return $false
}

Save-Shot "01-map-light"

if (Tap-Desc "Settings") {
    Start-Sleep -Seconds 1
    Save-Shot "04-settings-light"
    & $adb shell input keyevent 4
    Start-Sleep -Milliseconds 600
}

if (Tap-Desc "Favorites") {
    Start-Sleep -Seconds 1
    Save-Shot "03-favorites-light"
    & $adb shell input keyevent 4
    Start-Sleep -Milliseconds 600
}

Write-Host "Done. Detail 页需要从收藏卡进入后单独截图（数据依赖网络/缓存）。"