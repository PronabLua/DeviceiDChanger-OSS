# Build from source

[Documentation index](README.md) · [Testing](TESTING.md) · [Releasing](RELEASING.md)

## Toolchain

The checked-in build script targets Windows PowerShell and Windows Android SDK executables. A full Linux / macOS build entry point is not supplied.

| Tool | Version / requirement |
| --- | --- |
| JDK | 17; `java` and `javac` on `PATH` |
| Python | 3.11+; `python` on `PATH` (packaging uses `hashlib.file_digest`) |
| Git | On `PATH`; needed by native dependency setup |
| Android SDK platform | `platforms;android-37.0` |
| Android build tools | `build-tools;37.0.0` |
| Android NDK | `ndk;30.0.16248370` |
| SDK CMake / Ninja | `cmake;3.31.6` |
| Gradle | 9.3.1 via checked-in wrapper; no global Gradle required |
| Android Gradle Plugin | 9.1.1, pinned in root build file |
| Compose compiler plugin | 2.2.10, pinned in root build file |

No emulator system image or full AOSP checkout is required. Initial builds need network access to Gradle distribution / plugin repositories, Google Maven, Maven Central, and the Git dependency repositories.

## Prepare a checkout

```powershell
git clone https://github.com/PronabLua/DeviceiDChanger-OSS.git
Set-Location DeviceiDChanger-OSS
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17'
$env:ANDROID_HOME = 'C:\Android\Sdk'
```

Replace the example JDK and SDK paths with your installed locations. Ensure the JDK's `bin` directory is on `PATH`.

If Android SDK Command-Line Tools are installed, an example package installation is:

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;30.0.16248370' 'cmake;3.31.6'
```

Accept SDK licenses through your installed SDK tooling as required.

## Run the complete development build

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -SdkRoot C:\Android\Sdk
```

SDK discovery uses `-SdkRoot` / `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then an existing `C:\Android\Sdk`. An explicit path is easiest to diagnose.

The script performs these stages:

1. Validate required SDK executables, platform JAR, and host commands.
2. Run `:manager:assembleDebug`, `:manager:testDebugUnitTest`, and `:manager:lintDebug`.
3. Run Java bridge publication and diagnostics tests.
4. Compile the Java bridge with Java 8 bytecode and produce `hook.dex` using D8 with minimum API 29.
5. Configure and build the native library for `arm64-v8a`, Android platform 29, Release, C++20, and `ANDROID_STL=none` with the pinned standalone C++ runtime.
6. Strip the library, validate bundled license checksums, and create development artifacts.

It downloads build dependencies and writes local outputs. It does not install software on a device or run the device probes / native harness.

## Outputs

| Path | Contents |
| --- | --- |
| `manager/build/outputs/apk/debug/manager-debug.apk` | Companion debug APK |
| `hook/build/hook.dex` | Java hook bridge DEX |
| `native/build/libdeviceidchanger.so` | Native library before packaging strip |
| `build/release/libdeviceidchanger.so` | Stripped packaging copy |
| `dist/deviceidchanger-0.1.0-prototype-debug.zip` | Native installation ZIP |
| `dist/deviceidchanger-0.1.0-prototype-debug.apk` | Copy of companion debug APK |
| `dist/SHA256SUMS` | Hashes of the two development artifacts |

The module archive contains root module scripts and metadata, `manager.apk`, `hook.dex`, `zygisk/arm64-v8a.so`, the project license, third-party notices, and indexed license files. Test fixtures are not included.

## Native dependencies

[Dependencies.cmake](../native/cmake/Dependencies.cmake) pins Git revisions for LSPlant, Dobby, xDL, and the Zygisk API. LSPlant initializes its DexBuilder and Parallel Hashmap submodules. The libcxx-full AAR is verified against a pinned SHA-256.

Dependency checkouts live under `native/dependencies/`. Configuration rejects a checkout whose HEAD differs from its pinned revision. Source edits with HEAD still at the pin are accepted. CMake applies [DobbyAndroid.patch](../native/patches/DobbyAndroid.patch) if the patch is not already present.

Use [THIRD_PARTY_NOTICES.txt](../THIRD_PARTY_NOTICES.txt) and [licenses/index.json](../licenses/index.json) for complete revisions, provenance, and notices. A source release provides audit copies separately; see [relinking instructions](RELEASING.md).

## Individual development commands

With SDK discovery already configured:

```powershell
.\gradlew.bat :manager:assembleDebug :manager:testDebugUnitTest :manager:lintDebug --console=plain
powershell -ExecutionPolicy Bypass -File hook/tests/RunTests.ps1
powershell -ExecutionPolicy Bypass -File hook/Build.ps1 -SdkPath C:\Android\Sdk -OutputDirectory hook/build -Platform android-37.0 -BuildTools 37.0.0
```

The explicit bridge platform arguments match the main build script; `hook/Build.ps1` has older standalone defaults. For repeatable native configuration, use the complete build entry point and its pinned arguments.

Node.js / npm is needed only for [legacy WebUI development](LEGACY_WEBUI.md). It is not required for the native build.

## Build failures

- **Missing tools:** install the exact SDK packages; a nearby version has a different directory name.
- **Wrong Java:** check both `java -version` and `javac -version`, plus `JAVA_HOME`.
- **Network / TLS error:** identify the failing dependency host before retrying; preserve the wrapper hash and TLS checks.
- **Dependency revision mismatch:** inspect that dependency's Git status and preserve local work before restoring the expected revision.
- **License checksum mismatch:** compare the indexed notice with its source; do not bypass packaging validation.
- **APK update rejected:** local debug and published release certificates differ. Follow [upgrade guidance](INSTALLATION.md).
