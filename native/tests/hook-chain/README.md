# ARM64 hook-chain regression harness

This standalone executable hooks only its own anonymous executable mapping.
It does not use ART, read app data, change module configuration or touch another
process. Each case runs in a child with a three-second alarm. Fault handlers
convert child faults into exit codes; a failing case cannot hang the parent.

The first shared library imports this project's current Dobby static archive.
The second independently links the exact Dobby revision pinned by Vector
`ddeed8ca1ffedfaba7f4341b911089906862d5c4`:

- Repository: `https://github.com/JingMatrix/Dobby`
- Commit: `05a09ac6807a6bb1726350e40ea4b127c1c79809`

The libraries load with `RTLD_LOCAL`; their Dobby symbols are hidden. This gives
two independent hook registries and relocation implementations. The test forces
the target mapping more than 4 GiB from both replacements so a normal ARM64
hook uses an absolute literal veneer. The ordinary function executes four ADD
instructions across the first 16 bytes and returns at offset 16. It returns
its input plus seven. Every stage checks inputs 0, 5 and -3 to verify argument
preservation and execution of the relocated instructions. For input zero the
chain must produce `7 -> 107 -> 307`, then restore through `107 -> 7`.
Reverse-order tests use `7 -> 207 -> 307 -> 207 -> 7`. Restoration compares all
original bytes. Successful near hooks must contain a B at entry and leave
bytes 4 through 31 unchanged from the immediate pre-install snapshot.

Build on Windows after the normal native build:

```powershell
git clone --no-checkout https://github.com/JingMatrix/Dobby.git build/research/override-dobby-vector
git -C build/research/override-dobby-vector checkout 05a09ac6807a6bb1726350e40ea4b127c1c79809
& C:/Android/Sdk/cmake/3.31.6/bin/cmake.exe -S native/tests/hook-chain -B build/research/hook-chain-build -G Ninja -DCMAKE_MAKE_PROGRAM=C:/Android/Sdk/cmake/3.31.6/bin/ninja.exe -DCMAKE_TOOLCHAIN_FILE=C:/Android/Sdk/ndk/30.0.16248370/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=none -DCMAKE_BUILD_TYPE=Release
& C:/Android/Sdk/cmake/3.31.6/bin/cmake.exe --build build/research/hook-chain-build --target hook_chain_test chain_first chain_second --parallel 6
```

An existing checkout can be reused. `VectorDobbySource` and `FirstDobbyLibrary`
are CMake cache options for alternate local paths. The latter defaults to
`native/build/dependencies/dobby-build/libdobby.a`. Rebuild the harness after
changing that archive. Keep a separate copy of baseline binaries before testing
a candidate.

After explicitly authorizing device execution, place `hook_chain_test`,
`libchain_first.so` and `libchain_second.so` together in a dedicated test folder,
make the executable runnable, and invoke:

```sh
/data/local/tmp/didc-hook-chain/hook_chain_test /data/local/tmp/didc-hook-chain 1
```

Replace the final argument to select a case:

| Case | Coverage |
| --- | --- |
| 0 | Normal first-engine veneer followed by Vector's normal veneer; baseline reproducer. |
| 1 | Required near branch followed by Vector's wider patch; full chain and restoration. |
| 2 | Forced near-allocation failure; candidate must refuse before changing target bytes. |
| 3 | Defensive post-install branch check; original bytes must remain or be restored. |
| 4 | Vector installs first, then our near hook; full reverse chain and restoration. |
| 5 | Out-of-order removal of our hook must fail without changing the active chain. |
| 6 | Original code is an LDR/BR literal thunk; our hook installs first. |
| 7 | Original literal thunk with Vector installing first; diagnostic of Vector alone before our installation. |
| 8 | Branch into the first 16 bytes must be rejected without changing bytes or the callable original. |
| 9 | ADR targeting the first 16 bytes must be rejected without changing bytes or the callable original. |
| 10 | Ordinary overlapping LDR literal must be rejected without changing bytes or the callable original. |

Exit zero means the selected assertions passed. Parent exit one means failure;
the printed child exit code distinguishes assertion failures from signals.
Examples: 139 is SIGSEGV, 132 is SIGILL and 142 is the alarm timeout. Baseline
cases 0, 1 and 2 are intentionally capable of failing. Candidate cases 1 through
6 and 8 through 10 must pass; case 0 remains a diagnostic for the unsafe
normal-veneer path.
Case 7 checks Vector's first hook before installing our hook, so a failure at
that initial step describes the unchanged Vector engine rather than our
candidate. It is a separate diagnostic and is not a candidate acceptance gate.

This isolates hook-chain mechanics. Passing it does not establish ART, Vector
or any application's complete runtime compatibility. It does not change other
modules or claim support for arbitrary hook engines or installation orders.
