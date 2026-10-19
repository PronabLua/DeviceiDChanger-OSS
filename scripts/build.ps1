param(
    [string]$SdkRoot = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$Repository = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot)
{
    $SdkRoot = $env:ANDROID_SDK_ROOT
}
if (-not $SdkRoot -and (Test-Path -LiteralPath 'C:\Android\Sdk'))
{
    $SdkRoot = 'C:\Android\Sdk'
}
if (-not $SdkRoot -or -not (Test-Path -LiteralPath $SdkRoot))
{
    throw 'Pass -SdkRoot with the installed Android SDK directory.'
}

$SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).Path
$Ndk = Join-Path $SdkRoot 'ndk/30.0.16248370'
$Cmake = Join-Path $SdkRoot 'cmake/3.31.6/bin/cmake.exe'
$Ninja = Join-Path $SdkRoot 'cmake/3.31.6/bin/ninja.exe'
$Strip = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
foreach ($Required in @($Cmake, $Ninja, $Strip, "$Ndk/build/cmake/android.toolchain.cmake", "$SdkRoot/platforms/android-37.0/android.jar", "$SdkRoot/build-tools/37.0.0/d8.bat"))
{
    if (-not (Test-Path -LiteralPath $Required))
    {
        throw "Missing build tool: $Required. Install SDK platform 37.0, build-tools 37.0.0, NDK 30.0.16248370 and CMake 3.31.6. No system image is required."
    }
}
Get-Command java, javac, python, git -ErrorAction Stop | Out-Null
$PreviousSdk = $env:ANDROID_HOME
$env:ANDROID_HOME = $SdkRoot
Push-Location $Repository
try
{
    & .\gradlew.bat :manager:assembleDebug :manager:testDebugUnitTest :manager:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Manager build or unit tests failed.' }
    & "$Repository/hook/tests/RunTests.ps1"
    & "$Repository/hook/Build.ps1" -SdkPath $SdkRoot -OutputDirectory "$Repository/hook/build" -Platform 'android-37.0' -BuildTools '37.0.0'
    & $Cmake -S native -B native/build -G Ninja "-DCMAKE_MAKE_PROGRAM=$Ninja" "-DCMAKE_TOOLCHAIN_FILE=$Ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=none -DCMAKE_BUILD_TYPE=Release
    if ($LASTEXITCODE -ne 0) { throw 'Native configuration failed.' }
    & $Cmake --build native/build --target deviceidchanger --parallel 4
    if ($LASTEXITCODE -ne 0) { throw 'Native compilation failed.' }
    & python scripts/package.py --strip-tool $Strip
    if ($LASTEXITCODE -ne 0) { throw 'Module packaging failed.' }
}
finally
{
    Pop-Location
    $env:ANDROID_HOME = $PreviousSdk
}
