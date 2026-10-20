# DeviceID Changer

A Zygisk module with a Kotlin and Jetpack Compose Material 3 companion app for
per-app Android ID overrides. This fork is under development and currently
builds for ARM64 only.

The native module lives in `module-native/`. The original WebUI module is
retained in `module/`, with its documentation and credits below.

## Usage

Install the native module ZIP through your root manager with Zygisk enabled,
then reboot. The module installs the companion app for the device's owner user
after boot completes.

1. Open the companion app and select a target app.
2. Enter or generate a 16-digit hexadecimal Android ID.
3. Choose **Apply and restart app**.
4. To remove the override, choose **Restore and restart app**.

ID changes require only an app restart. Installing the module or updating its
native library requires a device reboot. Target app data and the system SSAID
settings file are left unchanged.

If the helper cannot confirm the app launched, it restores the previous profile
and attempts one recovery launch. The companion reports **Change rolled back**
and the recovery result.

## Compatibility and limitations

The project targets Android 10–17 (API 29–37). Runtime testing is currently
limited to a Pixel 8a running Android 17 with KernelSU Next and Zygisk Next.
Other Android versions and devices remain unverified.

- Requires root and Zygisk.
- Supports ARM64 apps in owner user 0. Work profiles, secondary users, system
  apps, shared UIDs and 32-bit processes are excluded.
- Apps installed on adopted storage are currently skipped.
- Overrides the Android ID returned through the hooked `Settings.Secure` getter
  path. Direct SettingsProvider/Binder reads and IDs cached by apps are outside
  its scope.
- Profiles identify apps by package and UID. Remove an override before
  uninstalling or replacing an app; signing and reinstall identity checks are
  not implemented.
- A successful restart confirms the app launched, not that it read the override.
- TalkBack, large fonts and foldable layouts still need testing.

## Build on Windows

Install JDK 17, Python 3.11+, Git and these Android SDK packages:

- `platforms;android-37.0`
- `build-tools;37.0.0`
- `ndk;30.0.16248370`
- `cmake;3.31.6`

From the repository root, run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -SdkRoot C:\Android\Sdk
```

The script runs the manager unit tests, lint and hook bridge tests, builds the
ARM64 module, and writes the companion APK, module ZIP and `SHA256SUMS` to
`dist/`. The APK uses the local Android debug key; these are development builds.
The script downloads dependencies but does not install anything on a device.

Gradle is pinned by the wrapper. Native dependencies and the Dobby patch are
configured in `native/cmake/Dependencies.cmake`. Project-owned native code uses
C++20.

Prereleases include a source archive with pinned native dependencies and
rebuild/relink instructions. See [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt)
for licensing and source requirements.

## Diagnostics

Logcat tags are `DeviceIdChangerUI` for companion actions and `DeviceIdChanger`
for native hook events. The root helper writes
`/data/adb/deviceidchanger/debug.log`. Logs stay on the device and omit Android
ID values.

Native events distinguish engine initialization (`engine_ready`), hook
installation (`hook_ready`) and a getter returning the override
(`android_id_read`).

Additional tests are in `module-native/tests/` for helper and installer behavior,
`hook/tests/` for the Java bridge, and `native/tests/hook-chain/` for native hook
chaining.

## Legacy WebUI

The documentation below applies to the original upstream WebUI module.

---

![WebUI Preview](https://raw.githubusercontent.com/sidex15/deviceidchanger/refs/heads/main/preview.png)
# DeviceID/SSAID Changer WebUI Module

A simple WebUI Module to change SSAID/DeviceID on Rooted Android Devices with Apatch, KSU (And its forks), or Magisk.

## Purpose of the module
Some banking apps are using your DeviceID/SSAID (`Settings.Secure.ANDROID_ID`) to ban you from using the app once root is detected in your device.

The problem is that some of the DeviceID Changer apps are paid or free, but with ads. Also, those apps might be flagged by some banking apps as malicious, and it could cause root/malicious app detection in your banking app.

This Module is designed to install as a module and use the WebUI for changing the DeviceID/SSAID. And since it's a module, you wouldn't worry about installing those apps that might cause detection by your banking app.

<b>And it's FOSS!</b>

## For Magisk Users
Use [KSU WebUI Standalone app by 5ec1cff](https://github.com/5ec1cff/KsuWebUIStandalone) or [MMRL](https://github.com/MMRLApp/MMRL)

## WebUI Not working?
[Update your Android System WebView To latest version](https://play.google.com/store/apps/details?id=com.google.android.webview). if it's still not working, try to submit a github issue

## TempRoot/Exploit-based Root Notice
Since I don't have a device that's currently using TempRoot/Exploit-based Root (I'm still currently using LG V50 and LG V50s), It's uncertain if this module fully works on that kind of environment.
If you insist on testing or using this module, I am not responsible for the consequences you cause.

## Credits
* [7.css by khang-nd](https://khang-nd.github.io/7.css)
* [Frutiger Aero Wallpaper by /u/Jackaboyishere1](https://www.reddit.com/r/FrutigerAero/comments/1ayi7qp/frutiger_aero_wallpapers/)

## Buy me some coffee ☕
PayPal: `sidex15.official@gmail.com`
<br>BTC: `1AVaRmoEivK94XRooqbbAgvzdTMA5cwGdc`
<br>ETH (ERC20): `0xa52151ebd2718a00af9e1dfcacfb30e1d3a94860`
<br>USDT (TRC20): `TAUbxzug7cygExFunhFBiG6ntmbJwz7Skn`
<br>USDT (ERC20): `0xa52151ebd2718a00af9e1dfcacfb30e1d3a94860`

