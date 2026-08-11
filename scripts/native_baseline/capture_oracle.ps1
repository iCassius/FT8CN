[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-[0-9]+$')]
    [string]$Serial,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [switch]$SkipBuild,
    [switch]$Allow4Kb,

    [ValidatePattern('(?i)^[0-9a-f]{64}$')]
    [string]$ExpectedTargetApkSha256,

    [ValidatePattern('(?i)^[0-9a-f]{64}$')]
    [string]$ExpectedTestApkSha256,

    [ValidatePattern('(?i)^[0-9a-f]{64}$')]
    [string]$ExpectedNativeLibrarySha256
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$androidProject = Join-Path $repoRoot 'ft8cn'

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ApkNativeLibrarySha256([string]$ApkPath, [string]$Abi) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entry = $archive.GetEntry("lib/$Abi/libft8cn.so")
        if ($null -eq $entry) {
            throw "APK has no lib/$Abi/libft8cn.so: $ApkPath"
        }
        $stream = $entry.Open()
        try {
            $sha = [System.Security.Cryptography.SHA256]::Create()
            try {
                return ([Convert]::ToHexString($sha.ComputeHash($stream))).ToLowerInvariant()
            }
            finally {
                $sha.Dispose()
            }
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        $archive.Dispose()
    }
}

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

$gitCommit = (& git -C $repoRoot rev-parse HEAD 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $gitCommit -notmatch '(?i)^[0-9a-f]{40}$') {
    throw "Could not resolve the source Git commit: $gitCommit"
}
$gitStatus = (& git -C $repoRoot status --porcelain --untracked-files=normal 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Could not determine the source Git status: $gitStatus"
}
$gitDirty = if ([string]::IsNullOrWhiteSpace($gitStatus)) { 'false' } else { 'true' }

if ($SkipBuild) {
    $missingExpectedHashes = @()
    if ([string]::IsNullOrWhiteSpace($ExpectedTargetApkSha256)) { $missingExpectedHashes += 'ExpectedTargetApkSha256' }
    if ([string]::IsNullOrWhiteSpace($ExpectedTestApkSha256)) { $missingExpectedHashes += 'ExpectedTestApkSha256' }
    if ([string]::IsNullOrWhiteSpace($ExpectedNativeLibrarySha256)) { $missingExpectedHashes += 'ExpectedNativeLibrarySha256' }
    if ($missingExpectedHashes.Count -gt 0) {
        throw "-SkipBuild requires explicit expected hashes: $($missingExpectedHashes -join ', ')"
    }
}

if (-not $SkipBuild) {
    Push-Location $androidProject
    try {
        & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest `
            "-Pft8cn.oracleGitCommit=$gitCommit" `
            "-Pft8cn.oracleGitDirty=$gitDirty" `
            --no-daemon --stacktrace
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
$targetApkSha256 = Get-Sha256 $targetApk
$testApkSha256 = Get-Sha256 $testApk
if ($SkipBuild) {
    if ($targetApkSha256 -ne $ExpectedTargetApkSha256.ToLowerInvariant()) {
        throw "Target APK SHA-256 does not match -ExpectedTargetApkSha256"
    }
    if ($testApkSha256 -ne $ExpectedTestApkSha256.ToLowerInvariant()) {
        throw "Test APK SHA-256 does not match -ExpectedTestApkSha256"
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

$testClass = 'com.bg7yoz.ft8cn.nativebaseline.NativeOracleInstrumentationTest#captureProductionNativeOracle'
$instrumentation = 'com.bg7yoz.ft8cn.beta.test/androidx.test.runner.AndroidJUnitRunner'
$instrumentationOutput = (& $adb -s $Serial shell am instrument -w -r -e class $testClass $instrumentation 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $instrumentationOutput -notmatch 'OK \(1 test\)') {
    throw "Native oracle instrumentation failed:`n$instrumentationOutput"
}

$json = (& $adb -s $Serial exec-out run-as com.bg7yoz.ft8cn.beta cat files/native-behavior-oracle-v2.json 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "Could not extract the native oracle from the selected emulator:`n$json"
}
try {
    $parsed = $json | ConvertFrom-Json -Depth 100
}
catch {
    throw "Extracted native oracle is not valid JSON: $($_.Exception.Message)"
}
if ($parsed.schema -ne 'ft8cn-native-behavior-oracle-v2') {
    throw "Unexpected native oracle schema: $($parsed.schema)"
}
$metadata = $parsed.metadata
if ($metadata.environment.page_size -ne [long]$pageSize) {
    throw "Captured page size does not match adb: JSON=$($metadata.environment.page_size), adb=$pageSize"
}
if ($metadata.source.git_commit -ne $gitCommit) {
    throw "Captured Git commit does not match the current checkout"
}
if ($metadata.source.git_dirty.ToString().ToLowerInvariant() -ne $gitDirty) {
    throw "Captured Git dirty state does not match the current checkout"
}
if ($metadata.source.build_variant -ne 'debug') {
    throw "Unexpected captured build variant: $($metadata.source.build_variant)"
}
if ($metadata.artifacts.target_apk_sha256 -ne $targetApkSha256) {
    throw "Installed target APK SHA-256 does not match the APK built by this script"
}
if ($metadata.artifacts.test_apk_sha256 -ne $testApkSha256) {
    throw "Installed test APK SHA-256 does not match the APK built by this script"
}
$nativeLibrarySha256 = Get-ApkNativeLibrarySha256 $targetApk $metadata.environment.native_abi
if ($metadata.artifacts.native_library_sha256 -ne $nativeLibrarySha256) {
    throw "Installed native library SHA-256 does not match the selected APK entry"
}
if ($SkipBuild -and $nativeLibrarySha256 -ne $ExpectedNativeLibrarySha256.ToLowerInvariant()) {
    throw "Native library SHA-256 does not match -ExpectedNativeLibrarySha256"
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

Write-Output "Native oracle captured from $Serial (PAGE_SIZE=$pageSize, ABI=$($metadata.environment.native_abi), lib=$nativeLibrarySha256): $resolvedOutput"
