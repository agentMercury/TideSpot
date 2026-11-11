# 潮点 TideSpot 本地一键验证脚本（HANDOVER §6 A-3）
# 作用：工程根目录校验 + JAVA_HOME + 八模块单测 + 红线扫描 + assembleRelease
#       （OPT-05 CI 门禁的本地形态；命令与 HANDOVER §2 验证命令一致）
# 用法：powershell -ExecutionPolicy Bypass -File verify.ps1 [-SkipAssemble]
param(
    [switch]$SkipAssemble
)

$ErrorActionPreference = 'Stop'
$expectedRoot = 'D:\workspace\weatherfishcheck\tidespot-android'
$root = $PSCommandPath | Split-Path -Parent
$fail = 0

function Step($ok, $msg) {
    if ($ok) { Write-Host "[PASS] $msg" } else { Write-Host "[FAIL] $msg"; $script:fail++ }
}

Write-Host "=== TideSpot verify.ps1 $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ==="

# 1) JAVA_HOME（HANDOVER §4.1）
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
Step (Test-Path "$env:JAVA_HOME\bin\java.exe") "JDK 17 就位（$env:JAVA_HOME）"

# 2) 工程根目录校验（R-09 已迁移至全英文路径 weatherfishcheck，无需 subst 映射）
if ($root -ne $expectedRoot) {
    Step $false "请在工程根目录运行（预期 $expectedRoot，实际 $root）"
    exit 1
}

# 3) 红线扫描（SRS §12：main 源码零域名命中——口径：域名出现于 URL/网络调用形态；
#    KDoc 中"不依赖/不接入 Overpass"等否定性文字提及不算，M6 基线同口径）
$mainKt = Get-ChildItem $root -Recurse -Filter '*.kt' |
    Where-Object { $_.FullName -match '\\src\\main\\' }
$redHits = $mainKt | Select-String -Pattern 'https?://[^\s"''`]*(?:cnemc|nmefc|overpass)[^\s"''`]*'
Step ($null -eq $redHits) "红线扫描 main 源码零域名命中（$($mainKt.Count) 个 .kt 文件）"
if ($redHits) { $redHits | ForEach-Object { Write-Host "       命中: $($_.Path):$($_.LineNumber)" } }

# 4) 八模块单测（+ 可选 assembleRelease）；-p 工程真实路径
# gradle 直跑发行包在工程根 .tools（HANDOVER §2 路径；tidespot-android 下无 .tools）
$gradle = Join-Path (Split-Path $root -Parent) '.tools\gradle-8.9\bin\gradle.bat'
Step (Test-Path $gradle) "Gradle 8.9 就位（$gradle）"

# 本机系统代理（若有）注入 JVM，保证新增依赖可达（否则 gradle 直连 maven 可能失败）
$ie = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
if ($ie.ProxyEnable -eq 1 -and $ie.ProxyServer) {
    $host_, $port = ($ie.ProxyServer -replace '^http://', '') -split ':'
    $env:GRADLE_OPTS = "-Dhttp.proxyHost=$host_ -Dhttp.proxyPort=$port -Dhttps.proxyHost=$host_ -Dhttps.proxyPort=$port"
    Write-Host "[INFO] 系统代理 $($ie.ProxyServer) 已注入 GRADLE_OPTS"
}
$tasks = @(
    ':app:testDebugUnitTest', ':core:domain:test', ':core:data:test', ':core:ui:testDebugUnitTest',
    ':feature:detail:testDebugUnitTest', ':feature:favorites:testDebugUnitTest',
    ':feature:map:testDebugUnitTest', ':feature:settings:testDebugUnitTest'
)
if (-not $SkipAssemble) { $tasks += ':app:assembleRelease' }
$tasks += '--console=plain'

Write-Host '=== Gradle 全量验证（八模块测试 + assembleRelease）==='
& $gradle @tasks -p $root
Step ($LASTEXITCODE -eq 0) "Gradle 八模块测试$(if (-not $SkipAssemble) { ' + assembleRelease' }) 全绿（退出码 $LASTEXITCODE）"

# 5) 结果汇总
Write-Host '=== 汇总 ==='
if ($fail -eq 0) {
    Write-Host 'VERIFY PASS（全部步骤通过）'
    exit 0
} else {
    Write-Host "VERIFY FAIL（$fail 项失败）"
    exit 1
}