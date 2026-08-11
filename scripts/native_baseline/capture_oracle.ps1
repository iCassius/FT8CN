[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-[0-9]+$')]
    [string]$Serial,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [switch]$SkipBuild,
    [switch]$Allow4Kb
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$androidProject = Join-Path $repoRoot 'ft8cn'

$androidSdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($androidSdk)) {
    $androidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$adb = Join-Path $androidSdk 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) {
    throw "adb.exe not found under Android SDK: $adb"
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $studioJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (-not (Test-Path -LiteralPath (Join-Path $studioJbr 'bin\java.exe') -PathType Leaf)) {
        throw 'JAVA_HOME is not set and the Android Studio JBR was not found.'
    }
    $env:JAVA_HOME = $studioJbr
}
$env:ANDROID_HOME = $androidSdk

$deviceState = (& $adb -s $Serial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
    throw "Selected emulator is not ready: $Serial ($deviceState)"
}
$pageSize = (& $adb -s $Serial shell getconf PAGE_SIZE 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $pageSize -notmatch '^[0-9]+$') {
    throw "Could not determine page size on ${Serial}: $pageSize"
}
if (-not $Allow4Kb -and $pageSize -ne '16384') {
    throw "Oracle capture requires a 16 KB emulator by default; $Serial reports PAGE_SIZE=$pageSize. Use -Allow4Kb only for a separately labelled 4 KB baseline."
}

if (-not $SkipBuild) {
    Push-Location $androidProject
    try {
        & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle oracle build failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

$targetApk = Join-Path $androidProject 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $androidProject 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
foreach ($apk in @($targetApk, $testApk)) {
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "Required oracle APK is missing: $apk"
    }
}

& $adb -s $Serial install -r -t $targetApk
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to install the target debug APK on the selected emulator.'
}
& $adb -s $Serial install -r -t $testApk
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to install the instrumentation APK on the selected emulator.'
}

$testClass = 'com.bg7yoz.ft8cn.nativebaseline.NativeOracleInstrumentationTest'
$instrumentation = 'com.bg7yoz.ft8cn.beta.test/androidx.test.runner.AndroidJUnitRunner'
$instrumentationOutput = (& $adb -s $Serial shell am instrument -w -r -e class $testClass $instrumentation 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $instrumentationOutput -notmatch 'OK \(1 test\)') {
    throw "Native oracle instrumentation failed:`n$instrumentationOutput"
}

$json = (& $adb -s $Serial exec-out run-as com.bg7yoz.ft8cn.beta cat files/native-behavior-oracle-v1.json 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "Could not extract the native oracle from the selected emulator:`n$json"
}
try {
    $parsed = $json | ConvertFrom-Json -Depth 100
}
catch {
    throw "Extracted native oracle is not valid JSON: $($_.Exception.Message)"
}
if ($parsed.schema -ne 'ft8cn-native-behavior-oracle-v1') {
    throw "Unexpected native oracle schema: $($parsed.schema)"
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath, (Get-Location).Path)
$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
[System.IO.File]::WriteAllText(
    $resolvedOutput,
    $json.TrimEnd() + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output "Native oracle captured from $Serial (PAGE_SIZE=$pageSize): $resolvedOutput"
