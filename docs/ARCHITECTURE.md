# Architecture

[Documentation index](README.md) · [Helper reference](HELPER_REFERENCE.md)

## Repository map

| Path | Responsibility |
| --- | --- |
| `manager/` | Kotlin / Jetpack Compose companion, app catalog, root client, UI and unit tests |
| `module-native/` | Native module metadata, installer, boot service, action, uninstall, root helper, shell tests |
| `native/` | Zygisk entry point, profile transport, ART hook runtime, dependency pins and patch |
| `hook/` | Java bridge compiled to a standalone DEX, plus host tests |
| `probe/` | Target / control Android test apps with main and worker processes |
| `scripts/` | Windows build, binary packaging, source-release packaging |
| `licenses/` | Third-party license texts and checksum index |
| `src/`, `public/`, `index.html` | Retained upstream WebUI source and assets |
| `module/` | Retained upstream WebUI module metadata and built web assets |

## Configuration path

```text
Companion UI
    -> RootHelper -> su -c -> control.sh
    -> validate module, package, UID and ID
    -> lock -> atomic profile update
    -> force-stop -> resolve launcher -> launch with am -W
    -> structured reply -> refresh saved profile -> UI result
```

`AppCatalog` lists enabled launcher applications, filters unsupported identities, and labels recognized ABIs. `ManagerViewModel` handles selection, requests, drafts, and result notices. `Identity` validates packages / IDs and generates random IDs. `CommandProcess` bounds command runtime and output; `RootHelper` serializes requests with a coroutine mutex.

`HelperCommand` checks staged module installation before calling the installed helper. A pending module requires reboot; a missing module produces installation guidance.

The shell helper separately validates root, arguments, module files, user-0 package UID, system-app status, shared UID, and storage paths. Mutations use a root-owned lock and temporary files followed by rename. Apply preserves the previous profile and rolls back if a complete launcher start cannot be confirmed. Restore leaves the override removed if launch fails.

## Runtime path

```text
ARM64 app specialization
    -> native UID and owner-user app-data-path checks
    -> Zygisk root companion reads profile and bridge DEX
    -> profile snapshot sent to the specializing process
    -> LSPlant / Dobby initialize ART hooks
    -> in-memory DEX loader loads Bridge
    -> hook secure-cache getter
    -> eligible android_id read returns snapshot ID
```

[Module.cpp](../native/Source/Module.cpp) skips excluded processes and requests configuration before specialization completes. [Profile.cpp](../native/Source/Profile.cpp) validates root-owned directories / files, refuses symlinks, validates profile contents, and bounds the bridge DEX to 112 bytes–1 MiB. Socket reads / writes use timeouts and a protocol magic value.

[Runtime.cpp](../native/Source/Runtime.cpp) resolves ART symbols through xDL, initializes LSPlant with Dobby hooks, and loads the bridge with `InMemoryDexClassLoader`. Callback publication is synchronized while the original method reference is installed.

[Bridge.java](../hook/src/main/java/io/github/deviceidchanger/hook/Bridge.java) substitutes the snapshot ID only for the Secure cache receiver, `android_id`, and the process's own numeric user ID. Other calls invoke the original method. The bridge records a first qualifying read and bounded diagnostic samples without logging the ID value.

The root Zygisk companion is a native service endpoint; it is distinct from the Android companion UI app.

## Persistent state

| Location | Contents |
| --- | --- |
| `/data/adb/modules/deviceidchanger_native/` | Installed module, APK, DEX, Zygisk library |
| `/data/adb/modules_update/deviceidchanger_native/` | Staged module update, when present |
| `/data/adb/deviceidchanger/profiles/<uid>/<package>.conf` | Android ID and configuration revision |
| `/data/adb/deviceidchanger/companion.sha256` | Last successfully installed bundled APK hash |
| `/data/adb/deviceidchanger/companion.status` | Installation state |
| `/data/adb/deviceidchanger/companion.error` | Selected installation readiness errors |
| `/data/adb/deviceidchanger/install.log` | Latest Package Manager installation output |
| `/data/adb/deviceidchanger/debug.log`, `debug.log.1` | Bounded helper traces |

State / profile directories use root ownership and mode `0700`; profiles and private logs use `0600`. A normal profile has exactly two lowercase hexadecimal lines, each 16 characters, with newline separators:

```text
0123456789abcdef
fedcba9876543210
```

The first line is the configured ID; the second is an independently generated revision used for diagnostics. Profile storage contains the ID in plaintext behind filesystem permissions. Use helper commands rather than editing files by hand.

## Lifecycle and trust limits

The boot service waits for boot completion / Package Manager readiness and installs or updates the bundled APK. It tracks successful installation and respects later user removal. Module action repairs an absent companion and opens it. Uninstall removes profiles while retaining the companion and receipt.

Each target process receives a snapshot; profile changes require process restart. The prototype does not implement signing / reinstall identity checks or a target-observation channel back to the manager. See [compatibility](COMPATIBILITY.md) and [diagnostics](TROUBLESHOOTING.md).
