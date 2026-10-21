# Contributing

Contributions that improve correctness, clear documentation, compatibility evidence, and accessible UI are welcome. Read the [project overview](README.md), [compatibility limits](docs/COMPATIBILITY.md), and [Code of Conduct](CODE_OF_CONDUCT.md) first.

## Before starting

- Search [existing issues](https://github.com/PronabLua/DeviceiDChanger-OSS/issues) and pull requests for related work.
- For a substantial change, describe the problem, proposed behavior, affected components, and validation plan in an issue.
- Keep native prototype and legacy WebUI behavior distinct.
- Report suspected vulnerabilities through [SECURITY.md](SECURITY.md).

## Development workflow

1. Fork / clone the repository and create a focused branch.
2. Follow the [build guide](docs/BUILDING.md) and inspect the [architecture](docs/ARCHITECTURE.md).
3. Make the smallest coherent change and update related documentation.
4. Run the [checks](docs/TESTING.md) appropriate to the affected layer.
5. Open a pull request describing the problem, resulting behavior, validation, and remaining limitations.

Follow existing formatting. Keep Android scripts LF-terminated, preserve notices, and use the pinned Gradle / native toolchain. Explain dependency or patch changes, including their source and impact. Avoid unrelated generated assets, broad formatting changes, and lockfile churn.

Do not commit local SDK paths, signing keys, build caches, device logs, probe records, SSAID backups, or Android ID values from a real device.

## Validation expectations

| Change | Relevant checks |
| --- | --- |
| Manager logic / UI | Unit tests, lint, compilation; device / accessibility checks for changed UI |
| Root helper / lifecycle | Bash fixtures and focused intended-device checks |
| Java bridge | Bridge tests, build, target / control observations |
| Native runtime / Dobby patch | Native build, applicable hook-chain cases, focused device validation |
| Packaging / dependencies | Complete build, archive inspection, license checks, source manifest review |
| Documentation | Link / anchor checks, command accuracy, rendered structure, documentation-only diff |

List exact checks and outcomes. Distinguish host tests from device observations and say what was not run. New behavior or a regression fix should have a focused test when it can meaningfully validate the change; avoid tests that merely repeat implementation details.

## Documentation changes

Keep the README concise and put detailed procedures in `docs/`. Link relative to the current document, label host versus Android commands, and distinguish intended API support from tested devices. Document current behavior rather than promises. Preserve upstream attribution and existing license files.

## Pull request checklist

- Explain a concrete problem and resulting behavior.
- Keep scope focused and identify native versus legacy changes.
- Include validation results and relevant redacted evidence.
- Update affected usage, compatibility, or build instructions.
- Preserve private data, license notices, and dependency provenance.

Review and release timing depend on maintainer availability; opening a contribution does not guarantee inclusion in a release.
