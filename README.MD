# DeviceID Changer — native prototype

This fork is developing a native Kotlin/Jetpack Compose Material 3 companion and
an ARM64 Zygisk module for per-app Android ID overrides. Upstream source and
attribution are retained below. The new package is built from `module-native/`;
the original `module/` WebUI package is historical upstream code.

## Build locally on Windows

Install JDK 17, Python 3.11+, Git and these Android SDK build packages:

- `platforms;android-37.0`
- `build-tools;37.0.0`
- `ndk;30.0.16248370`
- `cmake;3.31.6`

Run `powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -SdkRoot C:\Android\Sdk`.
Gradle 9.3.1 is checksum-pinned by the wrapper. The manager uses AGP 9.1.1 and
its built-in Kotlin 2.2.10 support. Native dependencies are pinned in
`native/cmake/Dependencies.cmake`; the tracked Dobby patch is applied during
configuration. The dependency build can use newer language features while all
project-owned C++ is compiled as C++20 with strict warnings, no exceptions and
no RTTI. Only ARM64 is built.

This process downloads build tools and library sources/dependencies. It does not
download ROMs, emulator images or the full AOSP source tree. It does not install
anything on a phone.

The build writes a debug-signed companion APK, prototype module ZIP and
`SHA256SUMS` to `dist/`. Debug builds use the local Android debug key. They are
development artifacts; a stable release signing key and broader runtime qualification
are required before a stable production release. Native debug symbols are removed
from the packaged library; the local original is retained for debugging.
See `THIRD_PARTY_NOTICES.txt` for
third-party source and relinking requirements.

GitHub prereleases include the APK, module ZIP, checksums and a corresponding
source archive containing the application and pinned native library sources.
The source archive includes rebuild/relink instructions. It is not an offline
SDK/NDK distribution and does not include local configuration or device logs.

## Prototype behavior

The module ZIP bundles the companion APK and installs it for Android owner user
0 after the first boot completes. Later module upgrades preserve app data;
deliberately removing the companion is respected until the module action repairs
it. The installer never removes a conflicting APK to work around signing errors.

Select an ordinary launchable app, enter or generate a 16-digit hexadecimal ID,
and choose **Apply and restart app**. The root helper commits the configuration
atomically, stops the selected app and starts its launcher activity. A new app
process reads an immutable configuration snapshot. **Restore and restart app**
removes the override. Target app data and the system SSAID settings file are not
modified. Initial module installation/native upgrades require a reboot.

Apply requires a confirmed launcher result, including a recognized launch state
and timing. If launch confirmation fails, the helper restores the previous
profile and tries one recovery launch. The manager reports **Change rolled
back** and whether recovery reopened the app. Saving a profile alone does not
establish that an app observed the new ID.

The prototype hooks the instance `Settings.NameValueCache.getStringForUser`
method through LSPlant. It returns an override only for the exact cache object
held by `Settings.Secure.sNameValueCache`, the `android_id` key and the current
owner user. Other cache objects, settings and users delegate to the original
instance method. Secure wrapper methods are not deoptimized, preserving other
frameworks' wrapper hooks. Zygisk API 4 is used. Runtime diagnostics use
the `DeviceIdChanger` log tag and do not print the ID value. The manager reports
saved/restarted state separately from runtime observations; it does not read its
own Android ID and label it as the selected app's ID.

## Validation scope

The intended OS range is Android 10–17 (API 29–37). Compilation and source review
do not establish that entire range as runtime-tested. On 2026-10-18, a Pixel 8a
running Android 17 with KernelSU Next and Zygisk Next 1.5.0 passed diagnostic
tests for two ID changes, app-only restarts, a second target process, an unchanged
control app, and restoration of the original ID. The phone boot ID and Zygote PID
remained unchanged throughout the changes and restoration after the initial
module activation reboot. This validates the tested Settings.Secure getter path
on that device, not every app or Android release.

No ROMs or system images are included. Unit tests cover validation, protocol
handling, safe debug logging and UI state boundaries;
`module-native/tests/helper_test.sh` exercises helper/installer behavior with
mock package-manager commands on a host shell.

Current limitations requiring device testing or further work:

- BGMI 4.6.0 (`com.pubg.imobile`) passed two UI Apply/restart attempts after the
  hook-chain fix on the tested phone. The second reached its in-game welcome
  screen and remained alive after 55 seconds; Restore also reopened it. Logs
  confirmed the new engine and installed getter hook, but no BGMI Android ID
  getter read was observed during capture. This verifies the startup regression
  fix, not BGMI's actual device identity or server behavior. Its override was
  removed after that test. The newer instance-cache backend was subsequently
  verified using owned diagnostic apps; BGMI's own identity has not been
  reverified with that backend.
- Owner user 0 only; work profiles and secondary users are excluded.
- System apps, shared UIDs and 32-bit processes are excluded. Unknown app ABI is
  shown explicitly; only ARM64 processes can load the module.
- Direct SettingsProvider/Binder reads and IDs retained in app storage are not
  covered by the Java getter hook.
- Runtime configuration currently binds UID and package, without signing or
  reinstall identity verification. Remove an override before uninstalling or
  replacing its target app.
- Adopted-storage app paths are currently skipped by the runtime.
- Portrait/dark UI, search, invalid input, Apply and Restore have been checked on
  the Pixel 8a. TalkBack, large-font, foldable and other-device behavior remain
  unverified.

## Temporary debug diagnostics

The debug manager emits action, duration, exit/error status and revision metadata
under the `DeviceIdChangerUI` logcat tag. Native getter events use
`DeviceIdChanger`. Neither tag logs Android ID values or full root commands.

The helper writes root-private `/data/adb/deviceidchanger/debug.log` with request,
lock, package lookup, commit, restart and installer phases. The current log and
one rotated copy are bounded below 64 KiB each and use mode 0600. Genuine lock
contention is distinguished from unsupported locking and descriptor failures.
Read these logs through root access while debugging; no logs are uploaded.

Android shell descriptor inheritance is handled explicitly for Toybox flock.
APK streaming uses an argument terminator before the stdin marker, including on
Android 17. These fixes were exercised on the connected phone and in host tests.

`native/tests/hook-chain` tests two independent ARM64 hook engines against
synthetic functions, including installation order, allocation failure and
restoration. It does not touch another app or prove application compatibility.
`hook/tests/RunTests.ps1` checks callback publication, unrelated-setting
delegation and preservation of original exceptions.

The ARM64 hook-chain fix passed nine acceptance cases on the connected Android
17 phone on 2026-10-18, including both engine orders and byte-for-byte
restoration. The previous engine crashed or looped in the baseline cases.
The callback publication tests passed five host JVM checks. After one authorized
update reboot, the probe again verified distinct IDs in its main and worker
processes, an unchanged control app and unrelated setting, and restoration.
The new native engine marker was observed. Phone boot ID, the init-owned Zygote
PID and system-server PID stayed unchanged throughout the app-only changes.

On 2026-10-19, the earlier static Secure hook failed a controlled release-probe
test when ImNotADeveloper was scoped to the same app: an early read reached our
callback, but later reads returned the original ID. This reproduced the failure
without an account app. The instance-cache backend passed that same combination,
including forced `speed` AOT compilation, two distinct IDs, main and worker
processes, repeated reads, an unchanged control and unrelated setting, and
restoration. Natural callback events were captured through the other module's
original-method chain. The temporary test scope was restored exactly, and no
phone, Zygote or system-server restart occurred during those ID changes.

The current bridge has 13 host checks covering instance receiver identity,
argument and exception preservation, publication, delegation and bounded
diagnostics. Device validation remains limited to the tested Android 17 phone.
The Secure cache field and getter descriptors were source-audited across API
29–37, including 12L; Android 10–16 have not been runtime-tested here.

The change addresses the nested-hook failure class described in the
[LSPlant maintainer discussion](https://github.com/LSPosed/LSPlant/discussions/11)
and [Vector's veneer investigation](https://github.com/JingMatrix/Vector/pull/579).
Our own source patch uses a mandatory four-byte entry branch, preserves the
original instruction span separately, and rejects unsupported cases before
patching. It does not modify Vector or the target app's binaries.

Diagnostic events distinguish `engine_ready` (ART engine initialized),
`hook_ready` (getter hook installed), and `android_id_read` (the configured
value returned through the hooked getter). Only the last event demonstrates
that getter was used by the target process; a successful launch is not an ID
verification.

The native fork is a development prototype, not a validated replacement for the
upstream module yet. The upstream documentation below describes the legacy WebUI.

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

