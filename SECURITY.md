# Security reporting

## Project status

DeviceID Changer is an experimental root / Zygisk project. There is no published long-term security-support schedule or guaranteed response time. Reports should identify the affected commit or release, Android environment, and whether native or legacy behavior is involved.

## Report a vulnerability

If the repository's [Security page](https://github.com/PronabLua/DeviceiDChanger-OSS/security) offers **Report a vulnerability**, use that private reporting channel. Its availability depends on repository settings.

If private reporting is unavailable, open a minimal issue asking the maintainer for a private reporting channel. Do not include an exploit, sensitive logs, or detailed reproduction in that public request. This document does not designate an unverified email address as a security contact.

For an ordinary bug with no security impact, use the [issue tracker](https://github.com/PronabLua/DeviceiDChanger-OSS/issues) and [support guidance](SUPPORT.md).

## Helpful private report contents

- Affected release / commit and environment.
- Expected versus actual security boundary.
- Impact, prerequisites, and minimal reproduction using test data.
- Relevant redacted evidence and a suggested fix, if available.

Coordinate disclosure with the maintainer through the available private channel. Never include signing keys, account credentials, device identifiers, or another person's data.

## Current boundaries

The companion invokes a root helper; profiles contain configured IDs behind root-owned filesystem permissions. These files are not encrypted. Package / UID checks do not verify signing certificates or reinstall identity. Root-capable software can modify module state. The native hook covers a specific getter path and does not provide device anonymity or app-acceptance guarantees. See [architecture](docs/ARCHITECTURE.md) and [compatibility](docs/COMPATIBILITY.md).

Legacy WebUI changes system settings and may expose IDs in console output / shared-storage backups. Its behavior differs from the native implementation. See [Legacy WebUI](docs/LEGACY_WEBUI.md).
