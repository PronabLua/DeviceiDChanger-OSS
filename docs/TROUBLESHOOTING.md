# Troubleshooting

[Documentation index](README.md) · [Installation](INSTALLATION.md) · [Helper reference](HELPER_REFERENCE.md)

## Common problems

| Symptom | Check / recovery |
| --- | --- |
| Module missing | Install the native module ZIP, then reboot. The standalone APK is insufficient. |
| Phone restart required | A module update is staged. Reboot once before changing profiles. |
| Module disabled / incomplete | Enable it or reinstall a complete archive through the root manager, then reboot. |
| Root request unavailable | Grant root to the companion; check the root manager's authorization state. |
| Companion absent after boot | Wait for Package Manager readiness, then use the module action. Inspect installation logs if repair fails. |
| Companion stays absent on later boots | A recorded installation followed by user removal is respected. Use manual repair or the module action. |
| APK update rejected | Compare signing origin and version. Read installation log; follow [signing migration](INSTALLATION.md). |
| App missing from list | Check owner user, enabled launcher activity, system / shared UID exclusions, and refresh the catalog. |
| 32-bit app unsupported | Only an ARM64 Zygisk library is provided. |
| Another operation running | Wait and retry; do not delete lock files while an operation is active. |
| Lock unsupported / failed | This is a utility failure, not contention. Capture error and device details. |
| Invalid saved profile | Use Restore to remove it; do not repair files by guessing their contents. |
| Change rolled back | The requested restart was not confirmed. Previous configuration was restored; read the recovery launch result. |
| Rollback failed | Refresh saved state. The attempted profile may remain active; use Restore if appropriate. |
| Restore removed override but restart failed | Open the target manually; the profile remains removed. |
| Client timeout / connection lost | Refresh saved profile and module state before retrying; completion may be uncertain. |
| Runtime unverified | Expected status: the manager has no target-process observation channel. |
| App still reports another ID | Check eligible process / path, actual hook events, app caching, and read-method limits below. |

## Collect focused diagnostics

On a host with ADB and USB debugging authorized, the following streams the two relevant logcat tags:

```sh
adb logcat -v threadtime -s DeviceIdChanger:I DeviceIdChangerUI:D
```

Reproduce one operation and stop capture. Companion action traces use `DeviceIdChangerUI` in debug builds. Native / bridge traces use `DeviceIdChanger`; published release UI builds may not include debug action traces.

Inside a root Android shell, inspect helper status and private logs:

```sh
su
/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh status
cat /data/adb/deviceidchanger/debug.log
cat /data/adb/deviceidchanger/debug.log.1
cat /data/adb/deviceidchanger/install.log
```

Some logs do not exist until the relevant action occurs. Helper traces rotate around 65 KB, retaining a previous tail in `debug.log.1`. Records include action, phase, duration, exit, revision, and launch evidence. Installation output is stored in `install.log`.

Helper / hook diagnostics omit configured Android ID values, but may include package names, UIDs, revisions, process IDs, activity evidence, and sampled caller class / method names. Raw `get` output, profile files, legacy WebUI console output, and probe records **can contain Android IDs**. Review and redact before sharing. See [support](../SUPPORT.md).

## Interpret hook events

| Event | What it establishes |
| --- | --- |
| `snapshot_failed` | Profile / DEX snapshot could not be accepted; inspect status code and installed files |
| `runtime_failed step=...` | A named ART / bridge initialization step failed |
| `engine_ready` | LSPlant initialization completed using the configured engine |
| `bridge_created` | Java bridge instance was constructed |
| `hook_path` | Selected cache getter / receiver path |
| `hook_ready` | Hook installation completed for a package / revision |
| `hook_failed` | Hook setup failed |
| `callback_seen`, `callback_summary` | Hook callback activity; this alone does not establish an eligible ID read |
| `android_id_attempt` | An ID read reached the callback; `eligible` and `user_kind` explain matching |
| `android_id_read` | Bridge returned the override on a qualifying read for this revision |

Compare package, process, and revision for the operation being investigated. The bridge reports the first qualifying ID read and limits diagnostic samples; absence of repeated log lines is expected.

## Launch confirmed, but no override observed

Confirm that the module was installed / updated and the phone rebooted, Zygisk injects into the target, the app runs an ARM64 owner-user process, and a saved profile exists. The native module skips adopted-storage paths and excluded UIDs / processes.

Even a working hook does not cover direct provider / Binder reads or an ID cached elsewhere by the app. `hook_ready` does not establish a getter read, and `android_id_read` establishes only that specific callback behavior. Use the [target / control probes](TESTING.md) for a focused getter test; do not use the manager's own ID or a shell settings query as proof of another app's observed ID.

## Report a reproducible problem

Include module / companion version and origin, Android version / API, device model, root / Zygisk versions, target architecture, exact steps, expected / actual behavior, and a small redacted log excerpt. State whether the problem affects native or legacy behavior. Use the [bug report template](../.github/ISSUE_TEMPLATE/bug_report.md); suspected vulnerabilities belong in the [security reporting process](../SECURITY.md).
