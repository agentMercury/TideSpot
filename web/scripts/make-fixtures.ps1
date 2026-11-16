# Regenerate web/test/fixtures/domain.json from the Android domain sources.
# Read-only: this script never writes inside tidespot-android/.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\make-fixtures.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\make-fixtures.ps1 -KotlinLib "C:\path\to\kotlin\lib"

param(
    [string]$KotlinLib = ''
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$webRoot = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $webRoot
$androidDomain = Join-Path $repoRoot 'tidespot-android\core\domain\src\main\kotlin'

if ([string]::IsNullOrWhiteSpace($KotlinLib)) {
    $KotlinLib = Join-Path $repoRoot '.tools\gradle-8.9\lib'
}

$compiler = Join-Path $KotlinLib 'kotlin-compiler-embeddable-1.9.23.jar'
$stdlib = Join-Path $KotlinLib 'kotlin-stdlib-1.9.23.jar'
if (-not (Test-Path $compiler)) {
    throw "Kotlin compiler not found: $compiler (pass -KotlinLib)"
}
if (-not (Test-Path $stdlib)) {
    throw "Kotlin stdlib not found: $stdlib (pass -KotlinLib)"
}

$tmp = Join-Path $webRoot '.tmp'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$domainJar = Join-Path $tmp 'domain.jar'
$dumpJar = Join-Path $tmp 'dump.jar'
$dumpSrc = Join-Path $scriptDir 'fixture\Dump.kt'
$outFile = Join-Path $webRoot 'test\fixtures\domain.json'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outFile) | Out-Null

$classpathAll = Join-Path $KotlinLib '*'

Write-Host '[1/3] compile domain (read-only)'
$sources = Get-ChildItem -Recurse -Filter '*.kt' -Path $androidDomain | ForEach-Object { $_.FullName }
$argsDomain = @('-no-stdlib', '-no-reflect', '-classpath', $stdlib, '-d', $domainJar) + $sources
& java -cp $classpathAll org.jetbrains.kotlin.cli.jvm.K2JVMCompiler $argsDomain
if ($LASTEXITCODE -ne 0) { throw 'domain compilation failed' }

Write-Host '[2/3] compile fixture dumper'
$argsDump = @('-no-stdlib', '-no-reflect', '-classpath', "$stdlib;$domainJar", '-d', $dumpJar, $dumpSrc)
& java -cp $classpathAll org.jetbrains.kotlin.cli.jvm.K2JVMCompiler $argsDump
if ($LASTEXITCODE -ne 0) { throw 'dumper compilation failed' }

Write-Host '[3/3] run dumper'
$json = & java -cp "$dumpJar;$domainJar;$stdlib" fixture.DumpKt
if ($LASTEXITCODE -ne 0) { throw 'dumper execution failed' }
$text = ($json -join '')
[System.IO.File]::WriteAllText($outFile, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("fixture written: {0} chars -> {1}" -f $text.Length, (Split-Path -Parent $outFile))