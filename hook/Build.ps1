param(
    [Parameter(Mandatory = $true)][string]$SdkPath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$Platform = 'android-36',
    [string]$BuildTools = '36.0.0'
)

$ErrorActionPreference = 'Stop'
$Classes = Join-Path $OutputDirectory 'classes'
$Dex = Join-Path $OutputDirectory 'dex'
New-Item -ItemType Directory -Force -Path $Classes, $Dex | Out-Null
$AndroidJar = Join-Path $SdkPath "platforms/$Platform/android.jar"
$Source = Join-Path $PSScriptRoot 'src/main/java/io/github/deviceidchanger/hook/Bridge.java'
& javac --release 8 -classpath $AndroidJar -d $Classes $Source
if ($LASTEXITCODE -ne 0) { throw 'Hook Java compilation failed' }
$ClassFiles = @(Get-ChildItem -LiteralPath $Classes -Recurse -Filter '*.class' | ForEach-Object FullName)
& (Join-Path $SdkPath "build-tools/$BuildTools/d8.bat") --release --min-api 29 --lib $AndroidJar --output $Dex @ClassFiles
if ($LASTEXITCODE -ne 0) { throw 'Hook DEX compilation failed' }
Copy-Item -LiteralPath (Join-Path $Dex 'classes.dex') -Destination (Join-Path $OutputDirectory 'hook.dex') -Force
