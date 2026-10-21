# Credits and attribution

## Project lineage

- [PronabLua/DeviceiDChanger-OSS](https://github.com/PronabLua/DeviceiDChanger-OSS): native Zygisk prototype and companion app fork.
- [sidex15/deviceidchanger](https://github.com/sidex15/deviceidchanger): original DeviceID / SSAID Changer WebUI project. Its attribution, module metadata, and release history remain in the repository.
- The retained upstream [changelog](module/CHANGELOG.md) credits `antoniu200` for multi-user support.

## Native components

| Component | Role | Recorded license |
| --- | --- | --- |
| [LSPlant](https://github.com/LSPosed/LSPlant) | ART hook implementation | LGPL-3.0 |
| [DexBuilder](https://github.com/LSPosed/DexBuilder) | Runtime DEX generation dependency | LGPL-3.0 |
| [Parallel Hashmap](https://github.com/greg7mdp/parallel-hashmap) | DexBuilder dependency | Apache-2.0 |
| [Dobby](https://github.com/jmpews/Dobby) | Native inline hooking, with project patch | Apache-2.0 |
| [xDL](https://github.com/hexhacking/xDL) | ELF / ART symbol lookup | MIT |
| [Zygisk API](https://github.com/topjohnwu/zygisk-module-sample) | Zygisk module interface | Header's permissive grant |
| [libcxx-full](https://github.com/LSPosed/prefab-libcxx) | Standalone C++ runtime | LLVM runtime notices / terms |

The companion uses AndroidX / Jetpack Compose, Kotlin, and kotlinx.coroutines. Full native revisions, license provenance, notices, and the Dobby modification are recorded in [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) and [licenses/index.json](licenses/index.json). This table does not replace those notices or license texts.

## Legacy WebUI

- [7.css by khang-nd](https://khang-nd.github.io/7.css/).
- [Frutiger Aero wallpapers by u/Jackaboyishere1](https://www.reddit.com/r/FrutigerAero/comments/1ayi7qp/frutiger_aero_wallpapers/).
- [KernelSU JavaScript bridge](https://github.com/tiann/KernelSU), Vite, and Tailwind CSS as referenced by the retained package manifests.

## Development reference

[hamen/material-3-skill](https://github.com/hamen/material-3-skill) is recorded in the third-party notices as development UI guidance, not an app runtime dependency.

The repository retains [LICENSE](LICENSE). Individual dependencies retain their own license terms.
