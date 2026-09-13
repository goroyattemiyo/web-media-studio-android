[CmdletBinding()]
param(
    [string]$OutputDirectory,
    [switch]$Install,
    [string]$Serial
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repositoryRoot "gradlew.bat"
$apk = Join-Path $repositoryRoot "app\build\outputs\apk\debug\app-debug.apk"
$metadataPath = Join-Path $repositoryRoot "app\build\outputs\apk\debug\output-metadata.json"
$packageName = "com.goroyattemiyo.wms"

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repositoryRoot "dist"
} elseif (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot $OutputDirectory
}

$localJdkRoot = Join-Path $repositoryRoot ".toolchains\jdk-17"
$localJava = Get-ChildItem -Path $localJdkRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Directory.Name -eq "bin" } |
    Select-Object -First 1

if ($localJava) {
    $env:JAVA_HOME = Split-Path -Parent $localJava.Directory.FullName
} elseif (-not $env:JAVA_HOME) {
    $androidStudioJdk = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $androidStudioJdk) {
        $env:JAVA_HOME = $androidStudioJdk
    }
}

if (-not $env:ANDROID_HOME) {
    $defaultAndroidSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultAndroidSdk) {
        $env:ANDROID_HOME = $defaultAndroidSdk
    }
}

if (-not (Test-Path $wrapper)) {
    throw "Gradle Wrapper not found: $wrapper"
}

& $wrapper :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (-not (Test-Path $apk)) {
    throw "Expected debug APK was not produced: $apk"
}
if (-not (Test-Path $metadataPath)) {
    throw "Expected APK metadata was not produced: $metadataPath"
}

$metadata = Get-Content -Raw $metadataPath | ConvertFrom-Json
$element = $metadata.elements | Select-Object -First 1
if (-not $element.versionName -or -not $element.versionCode) {
    throw "APK version metadata is incomplete: $metadataPath"
}

$gitSha = (& git -C $repositoryRoot rev-parse --short=12 HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or -not $gitSha) {
    throw "Unable to resolve the Git checkpoint for the artifact name."
}
$dirtyPaths = @(& git -C $repositoryRoot status --porcelain)
$gitLabel = if ($dirtyPaths.Count -gt 0) { "$gitSha-dirty" } else { $gitSha }

$safeVersionName = $element.versionName -replace '[^A-Za-z0-9._-]', '-'
$artifactFileName = "wms-android-$safeVersionName-v$($element.versionCode)-$gitLabel-debug.apk"
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$artifactPath = Join-Path $OutputDirectory $artifactFileName
Copy-Item -LiteralPath $apk -Destination $artifactPath -Force

$hash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash
$checksumPath = "$artifactPath.sha256"
Set-Content -LiteralPath $checksumPath -Value "$hash  $artifactFileName" -Encoding ASCII

Write-Host "Local verification PASS"
Write-Host "Version: $($element.versionName) ($($element.versionCode))"
Write-Host "Git checkpoint: $gitLabel"
Write-Host "APK: $artifactPath"
Write-Host "SHA-256: $hash"
Write-Host "Checksum: $checksumPath"

if ($Install) {
    $adb = $null
    if ($env:ANDROID_HOME) {
        $sdkAdb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
        if (Test-Path $sdkAdb) {
            $adb = $sdkAdb
        }
    }
    if (-not $adb) {
        $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
        if ($adbCommand) {
            $adb = $adbCommand.Source
        }
    }
    if (-not $adb) {
        throw "adb was not found. Set ANDROID_HOME or add adb to PATH."
    }

    $adbTarget = @()
    if ($Serial) {
        $adbTarget += "-s"
        $adbTarget += $Serial
    }

    & $adb @adbTarget install -r $artifactPath
    if ($LASTEXITCODE -ne 0) {
        throw "APK update installation failed."
    }

    $packageDump = (& $adb @adbTarget shell dumpsys package $packageName) -join "`n"
    if ($packageDump -notmatch "versionCode=$($element.versionCode)\b" -or
        $packageDump -notmatch "versionName=$([regex]::Escape($element.versionName))\b") {
        throw "Installed package version does not match the built APK."
    }
    Write-Host "Install/update verification PASS: $packageName"
}
