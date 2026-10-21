# Root helper reference

[Documentation index](README.md) · [Architecture](ARCHITECTURE.md)

## Invocation

Run these commands **inside an Android shell with root access**, after installation and reboot:

```sh
su
/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh status
```

`status` and `get` inspect state. `apply` and `restore` change the selected app's profile and close / reopen it. `repair` attempts companion installation. The manager additionally detects a staged module update before invoking this script; direct helper invocation does not perform that client-side guard.

| Command | Arguments | Action |
| --- | --- | --- |
| `status` | None | Check module files / markers and companion installation state |
| `get` | `PACKAGE` | Validate app and read its saved profile |
| `apply` | `PACKAGE ID` | Save a profile, restart app; roll back on unconfirmed restart |
| `restore` | `PACKAGE` | Remove profile and restart; removal persists if launch fails |
| `repair` | None | Manually install / update bundled companion APK |

Examples use a placeholder package; replace it with an eligible app you intend to restart:

```sh
/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh get com.example.app
/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh apply com.example.app 0123456789abcdef
/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh restore com.example.app
/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh repair
```

Package names have a maximum of 255 characters, dot-separated components beginning with an ASCII letter, and remaining ASCII letters, digits, or underscores. IDs are exactly 16 hexadecimal characters, normalized to lowercase. The helper rejects wrong argument counts and unsupported packages / UIDs.

## Response format

Replies are newline-delimited `KEY=value` text. Split at the first `=`. Parse exit status together with `STATUS`: success requires exit 0 and `STATUS=ok`. Errors normally exit 1. A response is not JSON, and human-readable `MESSAGE` text is not an API enum.

| Field | Meaning |
| --- | --- |
| `STATUS` | `ok` or `error` |
| `ERROR_CODE` | Machine-readable failure code when present |
| `MESSAGE` | Explanation / recovery guidance |
| `PROTOCOL_VERSION` | `1` in status responses |
| `MODULE_VERSION` | `1` in installed-helper status response; not a semantic release string |
| `MODULE_STATE` | `enabled`, `incomplete`, `disabled`, or `removed` from installed helper |
| `RUNTIME` | `unverified` or `unavailable`; file checks do not verify injection |
| `USER_ID` | `0` for supported operations that include this field |
| `PACKAGE`, `UID` | Validated app identity |
| `ENABLED` | Whether `get` found a valid saved profile |
| `CONFIGURED_ID`, `CONFIG_REVISION` | Saved / committed ID and revision, when included |
| `SAVED`, `STOPPED`, `RESTARTED` | Operation progress flags |
| `ROLLED_BACK`, `RESTORED_LAUNCH` | Whether Apply restored the previous configuration and confirmed recovery launch |
| `VERIFICATION` | `not_verified` |
| `OBSERVED_ID`, `OBSERVED_REVISION`, `OBSERVED_AT` | Empty placeholders in `get`; no runtime observation is collected |
| `IDENTITY_GUARD` | `uid_only_reinstall_not_verified` |
| `COMPANION_INSTALL` | `installed`, `user_removed`, `pending`, `failed`, or `unknown` in status |
| `COMPANION_ERROR` | `package_manager_failed`, `boot_timeout`, or empty in status |

Field sets differ by command. Do not assume all fields occur on every response. `get` exposes the configured ID: redact it before sharing output.

The manager's preflight can also synthesize `MODULE_STATE=pending_reboot` / `missing`, `RUNTIME=pending_reboot` / `unavailable`, and associated errors without calling the installed script.

## Failure groups

| Codes / family | Interpretation |
| --- | --- |
| `root_required`, `root_connection_failed` | Missing root or unavailable client root connection |
| `module_missing`, `module_pending_reboot` | Manager preflight: install module or reboot |
| `module_disabled`, `module_removed`, `module_incomplete` | Module cannot accept changes |
| `invalid_arguments`, `invalid_package`, `invalid_id`, `unknown_command` | Invalid command input |
| `package_missing`, `unsupported_uid`, `unsupported_package`, `shared_uid`, `system_app` | App missing or excluded |
| `package_manager_failed` | Android package information unavailable |
| `busy` | Lock held by another mutation |
| `lock_unsupported`, `lock_failed` | Required locking utility missing / failed; distinct from contention |
| `unsafe_state`, `storage_failed`, `profile_read_failed`, `profile_invalid`, `entropy_failed` | State / profile validation or storage failure |
| `stop_failed`, `activity_lookup_failed`, `no_launcher`, `start_failed`, `launch_unconfirmed` | Restart could not be confirmed |
| `rollback_failed` | Previous state could not be restored; attempted profile may remain |
| `apk_invalid`, `companion_install_failed`, `boot_timeout` | Companion payload / installation readiness failure |
| `client_timeout`, `client_output_limit`, `client_failure`, `client_cancelled` | Client handling / diagnostic failures; inspect current state |

## Timeouts and recovery

The manager request limits are 260 seconds for Apply, 150 for Restore, 210 for Repair, and 60 for other requests. Individual Android commands have separate bounds. These are maximum waits, not expected durations.

Apply preserves the previous profile, commits a new profile, and checks force-stop / launch output. If restart fails, it restores the previous profile (or removes the new one) and attempts one recovery restart. A rolled-back operation returns an error even if recovery launch succeeds.

Restore removes the profile first and does not re-create it if restart fails. If a request times out or a rollback fails, inspect `get` and diagnostics before retrying. See [troubleshooting](TROUBLESHOOTING.md).
