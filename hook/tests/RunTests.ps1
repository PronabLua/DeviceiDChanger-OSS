$ErrorActionPreference = 'Stop'
$HookRoot = Split-Path -Parent $PSScriptRoot
$TestOutput = Join-Path $HookRoot 'build/publication-tests'
New-Item -ItemType Directory -Force -Path $TestOutput | Out-Null
$TestSources = @(
    (Join-Path $HookRoot 'src/main/java/io/github/deviceidchanger/hook/Bridge.java'),
    (Join-Path $PSScriptRoot 'stubs/android/os/Process.java'),
    (Join-Path $PSScriptRoot 'stubs/android/util/Log.java'),
    (Join-Path $PSScriptRoot 'BridgePublicationTest.java'),
    (Join-Path $PSScriptRoot 'BridgeDiagnosticsTest.java')
)
& javac --release 17 -Xlint:all -Werror -d $TestOutput @TestSources
if ($LASTEXITCODE -ne 0) { throw 'Bridge publication tests did not compile' }
& java -cp $TestOutput io.github.deviceidchanger.hook.BridgePublicationTest
if ($LASTEXITCODE -ne 0) { throw 'Bridge publication tests failed' }
& java -cp $TestOutput io.github.deviceidchanger.hook.BridgeDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Bridge diagnostic tests failed' }
