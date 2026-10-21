# Installation, upgrades, and removal

[Documentation index](README.md) · [Compatibility](COMPATIBILITY.md) · [Usage](USAGE.md)

## Choose the right download

Open [Releases](https://github.com/PronabLua/DeviceiDChanger-OSS/releases) and read the selected release notes. The `v0.1.0-prototype` prerelease provides:

| Asset | Purpose |
| --- | --- |
| `deviceidchanger-0.1.0-prototype-release.zip` | Installable native module; includes companion APK |
| `deviceidchanger-0.1.0-prototype-release.apk` | Standalone companion; requires installed module |
| `deviceidchanger-0.1.0-prototype-source.zip` | Application and dependency sources, patches, release rebuild instructions |
| `SHA256SUMS` | Checksums for those three assets |

GitHub's automatic source archives are not installable modules and lack the separately packaged dependency sources. Local builds use `-debug` names; see [building](BUILDING.md).

## Verify a download

Download the asset and checksum list from the same release. On Windows PowerShell, compare the computed hash with the exact filename's entry:

```powershell
Get-FileHash .\deviceidchanger-0.1.0-prototype-release.zip -Algorithm SHA256
Get-Content .\SHA256SUMS
```

On a host with `sha256sum`, place all files named by the list in the same directory:

```sh
sha256sum -c SHA256SUMS
```

Do not install an asset whose hash differs. A checksum compares bytes with the published list; it does not replace checking the download source.

## Install the native module

1. Confirm the [requirements](COMPATIBILITY.md).
2. Open your root manager while Android is running and install the native module ZIP.
3. Enable your device's Zygisk implementation and reboot once.
4. Wait for Android and Package Manager to start. The boot service installs the bundled companion for owner user 0.
5. Open **Device ID Changer** and grant root access.
6. Check module status, then follow the [user guide](USAGE.md).

The installer rejects recovery installation, non-ARM64 devices, API levels outside 29–37, and incomplete archives. Installing only the APK leaves the module missing.

## Repair the companion

Use the module's action button in the root manager. If the companion is absent, the action runs manual repair and then opens it.

Advanced users can run this in a root Android shell:

```sh
/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh repair
```

Repair uses `pm install -r --user 0`, preserving existing companion data when Android accepts the update. Read `/data/adb/deviceidchanger/install.log` if Android rejects it.

After a successful installation receipt exists, the boot service respects a manually uninstalled companion and skips automatic reinstallation. Use the module action or manual repair to install it again.

## Upgrade and signing changes

Install a replacement native ZIP through the root manager, then reboot. Profiles live outside the module directory, so an in-place module update retains them.

The published `v0.1.0-prototype` notes describe a dedicated release certificate. The checked-in build script uses the builder's local debug key. Android cannot update an installed APK with a different certificate in place. Separately built debug APKs may also have different keys.

For migration from the earlier debug companion, the release notes instruct users to uninstall **only the companion app**, then install the release APK or use the module action. Keep the module installed to retain profiles, and grant root again if prompted. Uninstalling the companion removes its own app data. Review the selected release instructions before switching build types.

## Remove an override

Select the target app and choose **Restore and restart app** before uninstalling or replacing it. Restore deletes its profile without clearing target data or rewriting the system SSAID file.

If reopening fails after removal, open the app manually. The override remains removed.

## Disable or uninstall the module

Use the root manager to disable or remove the module, then reboot so future processes no longer load its native library.

Disabling preserves profiles. The uninstall script removes profiles but retains the companion, its installation receipt, and other state / logs under `/data/adb/deviceidchanger`. Uninstall the companion separately if desired. Removing only the APK does not remove the module or profiles.

The legacy module has a different ID, `deviceidchanger`, and directly edits system settings. These instructions describe `deviceidchanger_native`; see [Legacy WebUI](LEGACY_WEBUI.md).
