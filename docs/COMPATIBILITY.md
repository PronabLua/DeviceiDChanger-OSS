# Compatibility and limitations

[Documentation index](README.md) · [Installation](INSTALLATION.md)

## Required environment

- Rooted ARM64 Android device with a working Zygisk implementation.
- API 29–37, the range accepted by the installer and native runtime.
- Installation through a root manager while Android is running.
- Companion and target app running as owner user 0.
- Root permission for companion helper requests.
- Android shell utilities including Toybox `flock` and timeout support.

The project's reported runtime test environment is a Pixel 8a running Android 17 with KernelSU Next and Zygisk Next. Android 10–17 is the compatibility target; other versions, devices, ROMs, root managers, and Zygisk combinations remain unverified. The accepted API range is not a tested-device matrix.

## App and process eligibility

| Case | Behavior |
| --- | --- |
| Ordinary owner-user apps, UID 10000–19999 | Eligible when all other checks pass |
| Enabled apps with launcher activities | Listed by the companion |
| System / updated system apps | Excluded from manager; helper rejects system packages |
| Shared Android UID | Excluded / rejected |
| Companion itself | Excluded / rejected |
| Work profiles and secondary users | Unsupported |
| Isolated / sandbox processes and child zygotes | Excluded |
| 32-bit processes | Unsupported; only an ARM64 Zygisk library is packaged |
| Unknown ABI in catalog | Can be selected; this does not verify runtime architecture |
| Adopted-storage app paths | Skipped by native app-data path matching |
| No single resolvable launcher activity | Restart cannot be confirmed; Apply rolls back |

Native package matching accepts owner-user paths under `/data/user/0/`, `/data/data/`, or `/data/user_de/0/`. Other app-data roots are skipped.

## What is overridden

The runtime hooks `Settings.NameValueCache.getStringForUser(ContentResolver, String, int)`. The Java bridge substitutes a value only when:

1. The receiver is the `Settings.Secure` cache instance.
2. The setting name is `android_id`.
3. The requested user is the process's own numeric Android user ID.

Other settings, receivers, and user arguments go to the original method. Symbolic user constants such as `USER_CURRENT` do not match the bridge's numeric-user check.

Direct SettingsProvider / Binder reads, values cached elsewhere by an app, and other identifiers are outside scope. IMEI, serial number, advertising ID, account state, and app data are not managed by the native prototype.

## Verification and identity limits

- A saved profile confirms configuration storage.
- A confirmed restart checks Android's activity-launch response, not continuing app health.
- `hook_ready` records hook installation in a process.
- `android_id_read` records the bridge returning an override for a qualifying read.
- The manager does not collect target-process observations; replies deliberately use `VERIFICATION=not_verified`.

Profiles are keyed by package and UID. Signing certificates and reinstall identity are not verified. Remove an override **before** uninstalling or replacing the app. A retained profile may become relevant again if the same package / UID combination returns.

## Other modules and interface testing

The native hook-chain regression harness does not establish compatibility with arbitrary engines, installation orders, or apps. See [testing](TESTING.md).

TalkBack, large fonts, and foldable layouts still need device testing. Temporary / exploit-based root environments have no verified compatibility claim here.
