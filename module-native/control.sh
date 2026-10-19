#!/system/bin/sh

ModuleDir=${0%/*}
. "$ModuleDir/common.sh"
CheckRoot
BeginTrace "${1:-invalid}"

case "$1" in
    status)
        [ "$#" -eq 1 ] || ReplyError invalid_arguments 'Status takes no arguments.'
        ModuleState=enabled
        Runtime=unverified
        for RequiredFile in module.prop manager.apk hook.dex zygisk/arm64-v8a.so; do
            if [ ! -f "$ModuleDir/$RequiredFile" ] || [ -L "$ModuleDir/$RequiredFile" ] || [ ! -s "$ModuleDir/$RequiredFile" ]; then
                ModuleState=incomplete
                Runtime=unavailable
            fi
        done
        if [ -e "$ModuleDir/disable" ]; then
            ModuleState=disabled
            Runtime=unavailable
        fi
        if [ -e "$ModuleDir/remove" ]; then
            ModuleState=removed
            Runtime=unavailable
        fi
        InstallState=$(cat "$StateDir/companion.status" 2>/dev/null)
        case "$InstallState" in
            installed|user_removed|pending|failed) ;;
            *) InstallState=unknown ;;
        esac
        InstallError=$(cat "$StateDir/companion.error" 2>/dev/null)
        case "$InstallError" in
            package_manager_failed|boot_timeout) ;;
            *) InstallError= ;;
        esac
        printf 'STATUS=ok\nPROTOCOL_VERSION=1\nMODULE_VERSION=1\nMODULE_STATE=%s\nRUNTIME=%s\nUSER_ID=0\nCOMPANION_INSTALL=%s\nCOMPANION_ERROR=%s\nIDENTITY_GUARD=uid_only_reinstall_not_verified\nMESSAGE=Module files checked. Zygisk injection and the target app observed ID are not verified.\n' "$ModuleState" "$Runtime" "$InstallState" "$InstallError"
        ;;
    get)
        [ "$#" -eq 2 ] || ReplyError invalid_arguments 'Get requires one app package.'
        CheckPackage "$2"
        ReadProfile
        printf 'STATUS=ok\nPACKAGE=%s\nUID=%s\nUSER_ID=0\nENABLED=%s\nCONFIGURED_ID=%s\nCONFIG_REVISION=%s\nOBSERVED_ID=\nOBSERVED_REVISION=\nOBSERVED_AT=\nVERIFICATION=not_verified\nIDENTITY_GUARD=uid_only_reinstall_not_verified\n' "$Package" "$TargetUid" "$Enabled" "$ConfiguredId" "$ConfigRevision"
        ;;
    apply)
        [ "$#" -eq 3 ] || ReplyError invalid_arguments 'Apply requires an app package and a 16-digit hexadecimal Android ID.'
        if ! printf '%s\n' "$3" | grep -Eq '^[0-9A-Fa-f]{16}$'; then
            ReplyError invalid_id 'Android ID must contain exactly 16 hexadecimal digits.'
        fi
        CheckModule
        PrepareState
        AcquireLock
        CheckPackage "$2"
        mkdir -p "$ProfileDir" || ReplyError storage_failed 'Cannot create this app configuration directory.'
        chown 0:0 "$ProfileDir" || ReplyError storage_failed 'Cannot secure this app configuration directory.'
        chmod 0700 "$ProfileDir" || ReplyError storage_failed 'Cannot secure this app configuration directory.'
        SavePreviousProfile
        ConfiguredId=$(printf '%s' "$3" | tr A-F a-f)
        NewRevision
        PendingFile=$(mktemp "$ProfileDir/.$Package.XXXXXX") || ReplyError storage_failed 'Cannot create this app configuration.'
        printf '%s\n%s\n' "$ConfiguredId" "$ConfigRevision" > "$PendingFile" || ReplyError storage_failed 'Cannot write this app configuration.'
        chmod 0600 "$PendingFile" || ReplyError storage_failed 'Cannot secure this app configuration.'
        mv -f "$PendingFile" "$ProfileFile" || ReplyError storage_failed 'Cannot commit this app configuration.'
        PendingFile=
        Saved=true
        Trace profile_commit 0
        if ! RestartApp; then
            RollbackApply
        fi
        printf 'STATUS=ok\nPACKAGE=%s\nUID=%s\nSAVED=true\nSTOPPED=true\nRESTARTED=true\nROLLED_BACK=false\nRESTORED_LAUNCH=false\nCONFIGURED_ID=%s\nCONFIG_REVISION=%s\nVERIFICATION=not_verified\nMESSAGE=Configuration saved and app launch confirmed. App health and the ID observed by the app are not verified.\n' "$Package" "$TargetUid" "$ConfiguredId" "$ConfigRevision"
        ;;
    restore)
        [ "$#" -eq 2 ] || ReplyError invalid_arguments 'Restore requires one app package.'
        CheckModule
        PrepareState
        AcquireLock
        CheckPackage "$2"
        rm -f "$ProfileFile" || ReplyError storage_failed 'Cannot remove this app override.'
        Saved=true
        Trace profile_restore 0
        if ! RestartApp; then
            ReplyError "$RestartError" "Override removed. $RestartMessage Open the app manually; the override remains removed."
        fi
        printf 'STATUS=ok\nPACKAGE=%s\nUID=%s\nSAVED=true\nSTOPPED=true\nRESTARTED=true\nROLLED_BACK=false\nRESTORED_LAUNCH=false\nCONFIGURED_ID=\nCONFIG_REVISION=\nVERIFICATION=not_verified\nMESSAGE=Override removed and app launch confirmed. App health and the ID observed by the app are not verified.\n' "$Package" "$TargetUid"
        ;;
    repair)
        [ "$#" -eq 1 ] || ReplyError invalid_arguments 'Repair takes no arguments.'
        CheckModule
        PrepareState
        AcquireLock
        InstallCompanion manual
        ;;
    *) ReplyError unknown_command 'Use status, get PACKAGE, apply PACKAGE ID, restore PACKAGE, or repair.' ;;
esac
