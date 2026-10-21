# Release and source packaging

[Documentation index](README.md) · [Building](BUILDING.md) · [Changelog](../CHANGELOG.md)

## Development builds and published assets

The checked-in `scripts/build.ps1` assembles the **debug** companion and `scripts/package.py` packages that APK into `-debug` artifacts. Neither script configures a production signing key.

The published [v0.1.0-prototype release notes](https://github.com/PronabLua/DeviceiDChanger-OSS/releases/tag/v0.1.0-prototype) describe a non-debuggable Gradle release APK signed with a dedicated release certificate, with the same APK bundled into its module ZIP. They also provide a source attachment with release rebuild instructions.

Do not label an ordinary scripted debug build as a reproduction of the published release binary. Use the selected release attachment's instructions for its release-specific process. Signing secrets are not supplied by this repository; byte-for-byte reproduction is not established here.

## Binary packaging behavior

After a successful development build, `scripts/package.py` expects:

- `manager/build/outputs/apk/debug/manager-debug.apk`
- `native/build/libdeviceidchanger.so`
- `hook/build/hook.dex`

The script requires `--strip-tool`, strips a copy of the library, collects native module `.sh` / `.prop` files, and includes the APK, DEX, ARM64 library, project license, notices, and checksum-indexed third-party licenses. Shell files must have LF line endings. ZIP entries get deterministic timestamps and executable modes for scripts; this does not establish reproducibility of the underlying compiled artifacts.

The standalone APK and binary ZIP receive hashes in `dist/SHA256SUMS`. This script does not create / hash a source archive or upload a GitHub release.

## Package corresponding source

Run the normal build's dependency setup first. The source packager reads existing Git checkouts and does not download missing dependencies.

```powershell
python scripts/source_release.py --revision HEAD --output dist/deviceidchanger-source.zip
```

For a published release, replace `HEAD` with the exact intended release commit. The script archives that commit, not uncommitted working-tree edits. It expects the pinned native dependency commits to exist in their local checkouts, including nested DexBuilder and Parallel Hashmap repositories.

The archive contains:

- The selected application Git tree at the archive root.
- Pristine pinned library sources under `third-party-sources/`.
- The separately tracked Dobby patch and project license / notice files.
- `SOURCE_RELEASE_MANIFEST.json` with application revision / tree and dependency revisions.
- `SOURCE_RELEASE_README.txt` with rebuild / relink instructions and limitations.

The normal dependency downloads, SDK, NDK, JDK, Gradle artifacts, and libcxx-full binary AAR are still needed. This is not an offline build environment. The packager excludes or rejects caches, local configuration, Git metadata, signing-key files, and detected private-key material.

Inspect the manifest's `application_source_complete` value and any warnings. An incomplete structural test archive is not suitable for publication as corresponding source. The source check is structural; review release contents as well.

## Rebuild with modified libraries

The source archive's `third-party-sources/` trees are audit / source copies without Git metadata. Do not copy entire trees directly into `native/dependencies/`, where CMake expects real Git checkouts.

Run normal dependency setup, keep the initialized checkouts at the pinned HEADs, then modify their source files in place or copy individual modified files from the source attachment. Rebuild to relink the native library. Preserve the Dobby patch and license notices. See [THIRD_PARTY_NOTICES.txt](../THIRD_PARTY_NOTICES.txt) for recorded licensing / source information.

## Maintainer release checks

1. Select the release commit; record exact toolchain, dependency pins, patch, and signing variant.
2. Run applicable [host and device checks](TESTING.md). State untested configurations.
3. Inspect module contents, executable modes, LF script endings, and companion identity / certificate. Verify the standalone APK matches the bundled APK.
4. Prepare the matching corresponding-source attachment and inspect its manifest / instructions.
5. Generate a checksum list covering every published APK, module ZIP, and source ZIP.
6. Update the changelog and write release notes describing installation, signing migration, source commit, assets, compatibility limits, and verification evidence.
7. Keep prototype releases marked as prereleases while that status applies.

These are manual release steps. The repository has no active checked-in build / release workflow under `.github/workflows/`. A retained upstream dispatch file is located under `.github/workflow/sync.yml` (singular directory), so it is not an active GitHub Actions workflow. It is not a native build or signing pipeline.
