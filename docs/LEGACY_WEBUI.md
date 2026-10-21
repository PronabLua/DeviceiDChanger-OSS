# Legacy WebUI

[Documentation index](README.md) · [Native project README](../README.md)

## Scope and upstream

The retained WebUI originates from [sidex15/deviceidchanger](https://github.com/sidex15/deviceidchanger). Its module metadata identifies version `v1.2.1` and module ID `deviceidchanger`. The native prototype uses ID `deviceidchanger_native` and a separate companion app.

The original project describes WebUI use with APatch, KernelSU / forks, and Magisk through a compatible WebUI host. Those upstream descriptions are not a device test matrix for this fork. For Magisk, the original README referenced [KSU WebUI Standalone](https://github.com/5ec1cff/KsuWebUIStandalone) and [MMRL](https://github.com/MMRLApp/MMRL).

## How it differs

| Area | Native prototype | Legacy WebUI |
| --- | --- | --- |
| UI | Compose companion APK | Root-manager WebUI |
| Module directory | `/data/adb/modules/deviceidchanger_native` | `/data/adb/modules/deviceidchanger` |
| Operation | Process-local getter override | Direct system SSAID file editing |
| Storage | Root-private per-package / UID profiles | `/data/system/users/<user-id>/settings_ssaid.xml` |
| User selection | Owner user 0 only | Discovers users with `cmd user list` |
| Change lifecycle | Restart target app | Prompts for phone reboot |
| Restore | Remove saved override | Select stored `defaultValue`, then apply |
| Backup | Apply rollback copy inside private state | Explicit backup to `/sdcard/settings_ssaid.xml` |

Native Restore does not undo earlier legacy file edits. Legacy defaults are the file's stored `defaultValue`, not a guarantee of the ID from a previous app installation. Combined use is not a validated configuration.

## Retained behavior

The WebUI selects a user and package from the SSAID settings file. It reads text XML directly or invokes `abx2xml` / `xml2abx` for Android Binary XML. Applying accepts a 16-character hexadecimal value and modifies the settings file. The success dialog offers a reboot.

Backup copies the selected user's file to the fixed path `/sdcard/settings_ssaid.xml`; a later backup, including from another user, can overwrite it. This file contains identifiers and is outside native private profile storage. Keep it private. The source also logs settings and selected IDs to the WebUI console; native diagnostic privacy behavior does not apply to those logs.

The retained WebUI source assumes its module directory and required Android commands exist. A desktop web preview cannot execute the root-manager bridge or exercise Android settings changes.

## Develop the WebUI

Source is in `src/`, `index.html`, and `public/`. Prebuilt upstream assets are retained in `module/webroot/`.

Use a Node.js version satisfying the locked Vite requirement (`^20.19.0 || >=22.12.0`) and npm:

```sh
npm ci
npm run dev
npm run build
npm run preview
```

`dev` starts Vite, `build` writes web output to `dist/`, and `preview` serves the built output. The Vite config does not copy builds into `module/webroot/` or produce a module ZIP. The native build also uses `dist/` for artifacts; run these build paths with that shared output location in mind.

## Troubleshooting and credits

For an empty or broken WebUI, check the Android System WebView, the WebUI host, root authorization, and availability of the expected module files / conversion utilities. Temporary / exploit-based root compatibility remains unverified.

Upstream interface credits include [7.css by khang-nd](https://khang-nd.github.io/7.css/) and [Frutiger Aero wallpapers by u/Jackaboyishere1](https://www.reddit.com/r/FrutigerAero/comments/1ayi7qp/frutiger_aero_wallpapers/). Additional attribution is in [CREDITS.md](../CREDITS.md); the retained history is in [module/CHANGELOG.md](../module/CHANGELOG.md).
