# Support

Start with [installation](docs/INSTALLATION.md), [compatibility](docs/COMPATIBILITY.md), and [troubleshooting](docs/TROUBLESHOOTING.md). For build errors, use the [build guide](docs/BUILDING.md).

## Where to ask

- **Reproducible bug:** open an [issue](https://github.com/PronabLua/DeviceiDChanger-OSS/issues) using the bug template.
- **Feature request:** explain the problem and expected behavior using the feature template.
- **Documentation correction:** open a focused issue or documentation pull request.
- **Suspected vulnerability:** follow [SECURITY.md](SECURITY.md) before publishing details.

This prototype has no documented response-time or compatibility guarantee. The [reported test environment](docs/COMPATIBILITY.md) is limited.

## Include useful context

Provide module / companion version, release or local build origin, source commit if known, device model, Android version / API, root manager and Zygisk versions, and native versus legacy implementation. For app-specific failures, include architecture and owner-user status where known.

Give the smallest reproducible steps, expected / actual result, exact UI error or helper error code, and a focused redacted log excerpt. State whether a phone reboot occurred after module installation / update.

## Keep reports private-data free

Remove real Android IDs, profile contents, personal app data, account details, serial numbers, private paths, and tokens. Review package names and caller information before sharing logs. Avoid full device logcat dumps.

Native hook / helper diagnostic traces omit configured IDs, but helper `get` output, probe observations, profile files, legacy WebUI console output, and `/sdcard/settings_ssaid.xml` backups can contain them. Use invented example values when describing a failure.
