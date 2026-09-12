$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repositoryRoot "gradlew.bat"
$apk = Join-Path $repositoryRoot "app\build\outputs\apk\debug\app-debug.apk"

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

Write-Host "Local verification PASS"
Write-Host "APK: $apk"
