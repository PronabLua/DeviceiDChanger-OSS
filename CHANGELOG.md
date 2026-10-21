# Changelog

This file tracks the native fork. Original WebUI release notes are preserved separately in [module/CHANGELOG.md](module/CHANGELOG.md).

## Unreleased

### Documentation

- Reorganize the README and add installation, usage, compatibility, troubleshooting, build, architecture, helper, testing, release, and legacy guides.
- Add contribution, support, security, conduct, attribution, issue, and pull request documentation.
- Distinguish published release signing from the checked-in debug build script.

## 0.1.0-prototype — 2026-10-21

[Published prerelease](https://github.com/PronabLua/DeviceiDChanger-OSS/releases/tag/v0.1.0-prototype) · Source commit: `38c3966c0fd79ad402301e0e122db18927ebfff1`

- Native ARM64 Zygisk module and Jetpack Compose companion for per-app Android ID overrides.
- Owner-user profiles, app restart operations, and automatic Apply rollback when launch is unconfirmed.
- Module ZIP bundles the same companion APK offered separately.
- Release notes describe a non-debuggable APK signed with a dedicated release certificate and explain migration from the earlier debug companion.
- Separate corresponding-source attachment and checksums.
- Android 10–17 compatibility target; reported runtime testing limited to a Pixel 8a on Android 17.

This entry summarizes the published prototype and is not a claim of universal compatibility. See [limitations](docs/COMPATIBILITY.md).
