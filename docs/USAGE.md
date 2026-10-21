# Companion app user guide

[Documentation index](README.md) · [Installation](INSTALLATION.md) · [Troubleshooting](TROUBLESHOOTING.md)

## Select an app

Open **Device ID Changer** as owner user 0 and grant root permission. Search by app name or package, then select an app.

The list contains enabled launcher apps and filters out the companion, system apps, and shared-UID apps. Recognized 32-bit apps are marked unsupported. Unknown ABI does not prove support. See [compatibility](COMPATIBILITY.md).

The identity screen shows the package, UID, and saved override state. A saved override is configuration, not a live measurement of what the app reads. The manager's own Android ID cannot verify another app's value.

## Apply an override

1. Enter exactly 16 hexadecimal characters (`0–9`, `a–f`; uppercase accepted), or choose **Generate random ID**.
2. Finish any unsaved work in the target app: applying closes and reopens it.
3. Choose **Apply and restart app** and wait for the result.

Input is trimmed and normalized to lowercase. The helper stores the ID with a new revision, then attempts a force-stop and launcher start for owner user 0. Profiles persist across reboots; each new eligible process loads a snapshot.

Profile changes need an app restart. Installing or updating the module needs a phone reboot.

## Understand the result

| Result | Meaning / next action |
| --- | --- |
| **Saved · app restarted** | Profile committed and launch confirmed; runtime reads and app health remain unverified. |
| **Change rolled back** | Apply could not confirm restart, so the previous profile was restored or the new one removed. The message gives the recovery launch result. |
| **Rollback failed** | The attempted change may remain saved. Refresh to inspect it, then use Restore if needed. |
| **Saved · restart needs attention** | A change may be saved without a confirmed restart. Refresh before retrying. |
| **Another operation is running** | Wait, then retry. Entered ID is retained. |
| **Change not confirmed** | Read the error, refresh, and check root / module status. |

After an unsuccessful operation, the entered ID remains available for editing. A client timeout or disconnected root request can leave state uncertain; refresh before another action.

## Restore normal behavior

Choose **Restore and restart app**. This removes the override and restarts without that profile. It does not reset data, other identifiers, or reconstruct an Android ID from an earlier installation.

If reopening fails, the profile stays removed. Open the app manually. This differs from Apply's automatic rollback.

Remove overrides before uninstalling or replacing target apps: identity checks use only package and UID.

## Refresh and diagnose

Use toolbar refresh or **Check again** to check status. Module status checks files and markers; **runtime unverified** is expected. For actual hook behavior, see [troubleshooting](TROUBLESHOOTING.md) and [testing](TESTING.md).
