# Testing and validation

[Documentation index](README.md) · [Building](BUILDING.md)

Tests cover different layers. A passing JVM or shell fixture does not establish Zygisk injection, ART compatibility, an app's observed ID, or device safety. Record which checks ran and which need a device.

## Host checks

With JDK 17 and the Android SDK configured, run on Windows:

```powershell
.\gradlew.bat :manager:testDebugUnitTest :manager:lintDebug --console=plain
powershell -ExecutionPolicy Bypass -File hook/tests/RunTests.ps1
```

Manager unit tests cover identity validation, command construction / execution, diagnostics, manager state, and operation notices. Java bridge tests compile with Android stubs and exercise publication / diagnostics behavior. The full [build script](BUILDING.md) runs these checks along with compilation and packaging.

In a Linux / WSL environment with Bash and standard GNU utilities, run the shell fixtures from the repository root:

```sh
bash module-native/tests/launch_test.sh
bash module-native/tests/helper_test.sh
```

The helper fixture uses temporary module / state directories and mocked Android commands. It tests helper / boot-service behavior without installing anything on a device. It requires tools such as `awk`, `sed`, `grep`, `stat`, `mktemp`, and `flock`. Launch tests check accepted and rejected `am start -W` evidence. These fixtures are not run by `scripts/build.ps1`.

## Target and control probe apps

The `probe` Gradle project provides two product flavors:

| Flavor | Debug package | Purpose |
| --- | --- | --- |
| `target` | `io.github.pronablua.deviceidchanger.probe` | App to receive a test override |
| `control` | `io.github.pronablua.deviceidchanger.probe.control` | Unconfigured comparison app |

Build debug probes on Windows:

```powershell
.\gradlew.bat :probe:assembleTargetDebug :probe:assembleControlDebug --console=plain
```

APKs are under `probe/build/outputs/apk/target/debug/` and `probe/build/outputs/apk/control/debug/`. A `compatibility` build type is also defined: it is non-debuggable, uses the debug signing config, and adds `.compat` to the application ID. It is a test variant, not a production-signing pipeline.

Install / run probes only on a device you intend to test. A useful manual sequence is:

1. Install the native module and reboot, then install both probes.
2. Open target and control; record each app's own baseline. Their baseline IDs need not match.
3. Apply a test ID to **target only** through the companion.
4. Confirm a new target process and check the value shown by its `Settings.Secure.getString` call.
5. Use **Reread settings** and **Open worker process** to inspect repeat reads / another process.
6. Reopen control and compare with its own baseline. Check the unrelated secure-setting read as well.
7. Restore target and compare with its own baseline; inspect matching package / revision logs.

The probes append `observations-main.jsonl` and `observations-worker.jsonl` in their private `files/` directories. Records include Android IDs, process / UID / PID, event, timestamps, and an unrelated secure-setting result. Treat these records as private; redact them before sharing.

## Native hook-chain harness

[native/tests/hook-chain/README.md](../native/tests/hook-chain/README.md) contains exact build commands, case selection, assertions, and interpretation. It requires a normal native build, a separate pinned comparison Dobby checkout, and ARM64 execution.

The harness hooks its own executable mapping, runs cases in child processes, and checks chain execution / restoration across two independently linked engines. Cases 1–6 and 8–10 are candidate acceptance checks. Case 0 is a baseline diagnostic; case 7 separately diagnoses the comparison engine's first hook.

Follow the harness's device-execution instructions in an intended test environment. Passing its cases establishes only those chain mechanics, not universal ART or application compatibility.

## Device validation checklist

- Record device, API level, root / Zygisk versions, module / companion origin and signing variant.
- Confirm installation, boot-time companion setup, root grant, and module state.
- Exercise Apply, a second Apply, Restore, and saved-profile persistence across reboot.
- Test failure messages and rollback using controlled test apps / fixtures.
- Check target and control observations, worker processes, and unrelated setting reads.
- Confirm no target app-data clearing or native system-SSAID-file edits.
- Check large text, TalkBack, insets, and available screen sizes when changing UI.
- Check debug-to-release migration and companion repair when changing packaging.

Report measured behavior separately from code-level support. See [compatibility](COMPATIBILITY.md) for the currently reported environment.

## Documentation-only changes

Check relative links and heading anchors, command / filename accuracy, Markdown formatting, and the final changed-file list. Application builds and device execution are unnecessary when only prose / contribution templates change. Do not claim tests were run without their results.
