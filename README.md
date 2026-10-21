# DeviceID Changer

Per-app Android ID overrides for rooted Android devices, using a native Zygisk module and a Kotlin / Jetpack Compose companion app.

[Download](https://github.com/PronabLua/DeviceiDChanger-OSS/releases) · [Installation](docs/INSTALLATION.md) · [User guide](docs/USAGE.md) · [Build from source](docs/BUILDING.md) · [Troubleshooting](docs/TROUBLESHOOTING.md)

> **Prototype:** the native module builds for ARM64 and owner user 0. Android 10–17 is the compatibility target; reported device testing is limited to a Pixel 8a on Android 17 with KernelSU Next and Zygisk Next. See the [compatibility limits](docs/COMPATIBILITY.md).

## Overview

Choose an app, enter or generate a 16-character hexadecimal Android ID, and apply the override by restarting that app. The native module changes the value returned through its hooked `Settings.Secure` getter path inside eligible app processes. It leaves target app data and the system SSAID settings file unchanged.

- Apply and remove individual app overrides from the companion app.
- Generate IDs with `SecureRandom`.
- Keep saved overrides across device reboots.
- Automatically restore the previous profile if Apply cannot confirm an app launch, then attempt one recovery launch.
- Inspect local helper and hook diagnostics without logging configured Android ID values.

The original upstream WebUI is retained in this repository. It edits the system SSAID file directly and has different behavior. See [Legacy WebUI](docs/LEGACY_WEBUI.md).

## Quick start

1. Check the [requirements](docs/COMPATIBILITY.md) and open [Releases](https://github.com/PronabLua/DeviceiDChanger-OSS/releases).
2. Download the **native module ZIP** and its `SHA256SUMS`. The companion APK alone cannot provide overrides.
3. Install the ZIP through your root manager while Android is running, enable a working Zygisk implementation, and reboot.
4. Wait for Android to finish booting. The module installs the bundled companion for owner user 0.
5. Open **Device ID Changer**, grant root access, and select an eligible app.
6. Enter 16 hexadecimal characters or choose **Generate random ID**, then **Apply and restart app**.
7. Use **Restore and restart app** to remove that app's override.

Installing or updating the native module requires a phone reboot. Later ID changes require an app restart. A successful restart confirms an activity launch; it does not prove that the app read the override.

For checksums, companion repair, upgrades, and removal, follow the [installation guide](docs/INSTALLATION.md).

## Compatibility at a glance

| Area | Native prototype |
| --- | --- |
| Android | API 29–37 accepted by installer and runtime; other than the reported Android 17 environment, unverified |
| Architecture | ARM64 device and app processes; no 32-bit module library |
| User | Owner user 0 only |
| Apps | Ordinary apps; manager lists enabled launcher apps and excludes system and shared-UID apps |
| Read path | Secure settings cache getter for `android_id` and the process's own numeric user ID |
| Outside scope | Work profiles, secondary users, isolated / sandbox processes, adopted-storage app paths, direct provider / Binder reads, and app-cached IDs |
| Identity checks | Package and UID only; signing certificate and reinstall identity are not verified |

Remove an override before uninstalling or replacing its target app. The project provides no guarantee of an app accepting a device or account.

## Build from source

The maintained build entry point is Windows PowerShell. Install JDK 17, Python 3.11+, Git, and the Android SDK packages in the [build guide](docs/BUILDING.md), then run from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -SdkRoot C:\Android\Sdk
```

The script runs manager unit tests, Android lint, and Java bridge tests, builds the companion, bridge DEX, and ARM64 library, and writes:

```text
dist/
  deviceidchanger-0.1.0-prototype-debug.apk
  deviceidchanger-0.1.0-prototype-debug.zip
  SHA256SUMS
```

Local scripted builds use the builder's Android debug key. The published `v0.1.0-prototype` prerelease is described in its release notes as using a dedicated release certificate. See [release and source packaging](docs/RELEASING.md) for that distinction and the corresponding-source archive.

## Documentation

| Guide | Contents |
| --- | --- |
| [Documentation index](docs/README.md) | Reading paths for users and contributors |
| [Installation](docs/INSTALLATION.md) | Downloads, installation, upgrades, repair, removal |
| [Usage](docs/USAGE.md) | Selection, ID validation, Apply, Restore, result messages |
| [Compatibility](docs/COMPATIBILITY.md) | Requirements, exclusions, verification limits |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Common failures and diagnostics |
| [Building](docs/BUILDING.md) | Toolchain, build steps, dependencies, outputs |
| [Architecture](docs/ARCHITECTURE.md) | Manager, helper, runtime, bridge, storage |
| [Helper reference](docs/HELPER_REFERENCE.md) | Root commands and response fields |
| [Testing](docs/TESTING.md) | Host checks, probes, native harness, device validation |
| [Releasing](docs/RELEASING.md) | Binary and source packaging, signing, release notes |
| [Legacy WebUI](docs/LEGACY_WEBUI.md) | Original module and web development |

## Contributing and support

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. For questions and reproducible bugs, see [SUPPORT.md](SUPPORT.md) and the [issue tracker](https://github.com/PronabLua/DeviceiDChanger-OSS/issues). Follow the [Code of Conduct](CODE_OF_CONDUCT.md). Report vulnerabilities according to [SECURITY.md](SECURITY.md).

## License and credits

The repository retains the GNU Affero General Public License v3 text in [LICENSE](LICENSE). Third-party components have their own terms; see [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt), the [license index](licenses/index.json), and [credits](CREDITS.md).

The native fork is maintained under [PronabLua/DeviceiDChanger-OSS](https://github.com/PronabLua/DeviceiDChanger-OSS) and builds on [sidex15/deviceidchanger](https://github.com/sidex15/deviceidchanger). The [changelog](CHANGELOG.md) separates the native prerelease from upstream history.
