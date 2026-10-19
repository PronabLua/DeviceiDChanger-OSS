#!/system/bin/sh

PATH=/system/bin:/system/xbin:/vendor/bin:/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin
export PATH
LC_ALL=C
export LC_ALL
umask 077
StateDir=/data/adb/deviceidchanger
ManagerPackage=io.github.pronablua.deviceidchanger
Saved=false
Restarted=false
Stopped=false
ConfigRevision=
OwnLock=false
PendingFile=
LockBinary=/system/bin/toybox
TraceAction=unknown
TraceStarted=0
PreviousFile=
PreviousRevision=
PreviousId=
HadPrevious=false
KeepPrevious=false
RolledBack=false
RestoredLaunch=false
RestartError=
RestartMessage=
LaunchState=unknown
LaunchTotalTime=-1
LaunchWaitTime=-1
LaunchActivityMatch=false

Trace()
{
    [ -d "$StateDir" ] && [ ! -L "$StateDir" ] && [ -x "$LockBinary" ] || return 0
    [ "$(stat -c '%u:%a' "$StateDir" 2>/dev/null)" = 0:700 ] || return 0
    case "$1" in
        ''|*[!a-z0-9_]*) return 0 ;;
    esac
    (
        for TraceFile in debug.lock debug.log debug.log.1; do
            if [ -L "$StateDir/$TraceFile" ] || { [ -e "$StateDir/$TraceFile" ] && [ ! -f "$StateDir/$TraceFile" ]; }; then
                exit 0
            fi
        done
        exec 7> "$StateDir/debug.lock" || exit 0
        "$LockBinary" flock -n 7 7>&7 2>/dev/null || exit 0
        if [ -f "$StateDir/debug.log" ]; then
            TraceSize=$(wc -c < "$StateDir/debug.log" | tr -d '[:space:]')
            if [ "$TraceSize" -ge 65024 ]; then
                tail -c 65024 "$StateDir/debug.log" > "$StateDir/debug.log.1" || exit 0
                chmod 0600 "$StateDir/debug.log.1" || exit 0
                : > "$StateDir/debug.log" || exit 0
            fi
        fi
        TraceNow=$(date +%s)
        TraceDuration=$((TraceNow - TraceStarted))
        [ "$TraceDuration" -ge 0 ] || TraceDuration=0
        TraceRevision=-
        case "$ConfigRevision" in
            ''|*[!0-9a-f]*) ;;
            *) [ "${#ConfigRevision}" -ne 16 ] || TraceRevision=$ConfigRevision ;;
        esac
        printf '%s action=%s phase=%s pid=%s duration_s=%s exit=%s revision=%s launch_state=%s total_time_ms=%s wait_time_ms=%s activity_match=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$TraceAction" "$1" "$$" "$TraceDuration" "${2:-0}" "$TraceRevision" "$LaunchState" "$LaunchTotalTime" "$LaunchWaitTime" "$LaunchActivityMatch" >> "$StateDir/debug.log"
        chmod 0600 "$StateDir/debug.log" "$StateDir/debug.lock"
    ) 2>/dev/null
    return 0
}

BeginTrace()
{
    case "$1" in
        status|get|apply|restore|repair|service|module_action|uninstall) TraceAction=$1 ;;
        *) TraceAction=invalid ;;
    esac
    TraceStarted=$(date +%s)
    trap 'Finish $?' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM HUP
    Trace request_start 0
}

Finish()
{
    FinalStatus=$1
    if [ "$FinalStatus" -ne 0 ] && [ "$Saved" = true ]; then
        KeepPrevious=true
    fi
    Cleanup
    Trace request_end "$FinalStatus"
}

ReplyError()
{
    Trace "error_$1" 1
    printf 'STATUS=error\nERROR_CODE=%s\nMESSAGE=%s\nSAVED=%s\nSTOPPED=%s\nRESTARTED=%s\nROLLED_BACK=%s\nRESTORED_LAUNCH=%s\nCONFIG_REVISION=%s\nVERIFICATION=not_verified\n' "$1" "$2" "$Saved" "$Stopped" "$Restarted" "$RolledBack" "$RestoredLaunch" "$ConfigRevision"
    exit 1
}

CheckRoot()
{
    if [ "$(id -u)" != 0 ]; then
        ReplyError root_required 'Grant root access to use this module.'
    fi
}

PrepareState()
{
    if [ -L "$StateDir" ] || [ -L "$StateDir/profiles" ]; then
        ReplyError unsafe_state 'The module configuration directory is a symbolic link.'
    fi
    mkdir -p "$StateDir/profiles" || ReplyError storage_failed 'Cannot create the module configuration directory.'
    chown 0:0 "$StateDir" "$StateDir/profiles" || ReplyError storage_failed 'Cannot secure the module configuration directory.'
    chmod 0700 "$StateDir" "$StateDir/profiles" || ReplyError storage_failed 'Cannot secure the module configuration directory.'
    Trace state_ready 0
}

Cleanup()
{
    if [ -n "$PendingFile" ]; then
        rm -f "$PendingFile"
    fi
    if [ -n "$PreviousFile" ] && [ "$KeepPrevious" != true ]; then
        rm -f "$PreviousFile"
        Trace backup_cleanup "$?"
        PreviousFile=
    fi
    if [ "$OwnLock" = true ]; then
        "$LockBinary" flock -u 9 9>&9 2>/dev/null
        ReleaseStatus=$?
        exec 9>&-
        OwnLock=false
        Trace lock_release "$ReleaseStatus"
    fi
}

AcquireLock()
{
    Trace lock_request 0
    if [ ! -x "$LockBinary" ]; then
        ReplyError lock_unsupported 'Android does not provide the required Toybox locking utility.'
    fi
    if [ -L "$StateDir/command.lock" ] || { [ -e "$StateDir/command.lock" ] && [ ! -f "$StateDir/command.lock" ]; }; then
        ReplyError unsafe_state 'The module command lock is not a regular file.'
    fi
    exec 9> "$StateDir/command.lock" || ReplyError storage_failed 'Cannot open the module operation lock.'
    LockDetails=$("$LockBinary" flock -n 9 9>&9 2>&1)
    LockStatus=$?
    if [ "$LockStatus" -ne 0 ]; then
        exec 9>&-
        Trace lock_rejected "$LockStatus"
        if [ "$LockStatus" -eq 1 ] && [ -z "$LockDetails" ]; then
            ReplyError busy 'Another module operation is running. Try again shortly.'
        fi
        case "$LockDetails" in
            *'Unknown command'*|*'Unknown option'*|*'unknown command'*|*'unknown option'*|*'not found'*|*'usage:'*) ReplyError lock_unsupported 'The Android locking utility does not support the required operation.' ;;
        esac
        ReplyError lock_failed 'Android could not acquire the operation lock. This is a locking error, not another running operation. Check the module debug log.'
    fi
    OwnLock=true
    Trace lock_acquired 0
}

WriteState()
{
    if [ -L "$StateDir/$1" ] || { [ -e "$StateDir/$1" ] && [ ! -f "$StateDir/$1" ]; }; then
        ReplyError unsafe_state 'The module state destination is not a regular file.'
    fi
    PendingFile=$(mktemp "$StateDir/.$1.XXXXXX") || ReplyError storage_failed 'Cannot create a module state file.'
    printf '%s\n' "$2" > "$PendingFile" || ReplyError storage_failed 'Cannot save module state.'
    chmod 0600 "$PendingFile" || ReplyError storage_failed 'Cannot secure module state.'
    mv -f "$PendingFile" "$StateDir/$1" || ReplyError storage_failed 'Cannot commit module state.'
    PendingFile=
}

CheckModule()
{
    if [ -e "$ModuleDir/remove" ]; then
        ReplyError module_removed 'The module is marked for removal.'
    fi
    if [ -e "$ModuleDir/disable" ]; then
        ReplyError module_disabled 'Enable the module in your root manager and reboot.'
    fi
    for RequiredFile in module.prop manager.apk hook.dex zygisk/arm64-v8a.so; do
        if [ ! -f "$ModuleDir/$RequiredFile" ] || [ -L "$ModuleDir/$RequiredFile" ] || [ ! -s "$ModuleDir/$RequiredFile" ]; then
            ReplyError module_incomplete 'Reinstall a complete module archive.'
        fi
    done
}

CheckPackage()
{
    Trace package_check_start 0
    Package=$1
    if [ "${#Package}" -gt 255 ] || ! printf '%s\n' "$Package" | grep -Eq '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$'; then
        ReplyError invalid_package 'Choose a valid installed app package.'
    fi
    if [ "$Package" = "$ManagerPackage" ]; then
        ReplyError unsupported_package 'The companion manager cannot be selected.'
    fi
    PackageRows=$(timeout -k 5 20 pm list packages -U --user 0 2>/dev/null) || ReplyError package_manager_failed 'Android package information is unavailable.'
    TargetUid=$(printf '%s\n' "$PackageRows" | awk -v Target="package:$Package" '$1 == Target && $2 ~ /^uid:[0-9]+$/ { sub(/^uid:/, "", $2); print $2 }')
    case "$TargetUid" in
        ''|*[!0-9]*) ReplyError package_missing 'The app is not installed for owner user 0.' ;;
    esac
    if [ "$TargetUid" -lt 10000 ] || [ "$TargetUid" -ge 20000 ]; then
        ReplyError unsupported_uid 'Only ordinary owner-user apps are supported. Isolated and sandbox processes are excluded.'
    fi
    UidCount=$(printf '%s\n' "$PackageRows" | awk -v Target="uid:$TargetUid" '$2 == Target { Count++ } END { print Count + 0 }')
    if [ "$UidCount" -ne 1 ]; then
        ReplyError shared_uid 'Apps sharing an Android UID are not supported.'
    fi
    SystemRows=$(timeout -k 5 20 pm list packages -s --user 0 "$Package" 2>/dev/null) || ReplyError package_manager_failed 'Cannot determine whether this is a system app.'
    if printf '%s\n' "$SystemRows" | grep -Fxq "package:$Package"; then
        ReplyError system_app 'System apps are not supported by this prototype.'
    fi
    ProfileDir="$StateDir/profiles/$TargetUid"
    ProfileFile="$ProfileDir/$Package.conf"
    if [ -L "$ProfileDir" ] || [ -L "$ProfileFile" ]; then
        ReplyError unsafe_state 'The selected app configuration contains a symbolic link.'
    fi
    if { [ -e "$ProfileDir" ] && [ ! -d "$ProfileDir" ]; } || { [ -e "$ProfileFile" ] && [ ! -f "$ProfileFile" ]; }; then
        ReplyError unsafe_state 'The selected app configuration path contains an unexpected file type.'
    fi
    Trace package_check_end 0
}

NewRevision()
{
    ConfigRevision=$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')
    if ! printf '%s\n' "$ConfigRevision" | grep -Eq '^[0-9a-f]{16}$'; then
        ReplyError entropy_failed 'Cannot create a configuration revision.'
    fi
}

ReadProfile()
{
    Trace profile_read_start 0
    ConfiguredId=
    ConfigRevision=
    Enabled=false
    if [ -f "$ProfileFile" ]; then
        exec 8< "$ProfileFile" || ReplyError profile_read_failed 'Cannot read the selected app configuration.'
        IFS= read -r ConfiguredId <&8 || ReplyError profile_invalid 'The saved Android ID is incomplete. Restore this app to remove it.'
        IFS= read -r ConfigRevision <&8 || ReplyError profile_invalid 'The saved revision is incomplete. Restore this app to remove it.'
        if IFS= read -r ExtraLine <&8 || [ -n "$ExtraLine" ]; then
            ReplyError profile_invalid 'The saved app configuration has extra content. Restore this app to remove it.'
        fi
        exec 8<&-
        if ! printf '%s\n' "$ConfiguredId" | grep -Eq '^[0-9a-f]{16}$' || ! printf '%s\n' "$ConfigRevision" | grep -Eq '^[0-9a-f]{16}$'; then
            ReplyError profile_invalid 'The saved app configuration is invalid. Restore this app to remove it.'
        fi
        Enabled=true
    fi
    Trace profile_read_end 0
}

SavePreviousProfile()
{
    ReadProfile
    PreviousRevision=$ConfigRevision
    PreviousId=$ConfiguredId
    HadPrevious=$Enabled
    if [ "$HadPrevious" = true ]; then
        PreviousFile=$(mktemp "$ProfileDir/.previous.XXXXXX") || ReplyError storage_failed 'Cannot create a rollback copy of the existing configuration.'
        cp "$ProfileFile" "$PreviousFile" || ReplyError storage_failed 'Cannot preserve the existing configuration before changing it.'
        chmod 0600 "$PreviousFile" || ReplyError storage_failed 'Cannot secure the rollback copy.'
    fi
    Trace previous_profile_saved 0
}

ReadLaunchEvidence()
{
    LaunchState=unknown
    LaunchTotalTime=-1
    LaunchWaitTime=-1
    LaunchActivityMatch=false
    LaunchEvidence=$(printf '%s\n' "$StartOutput" | awk -v Target="$Package" '
        BEGIN { State = "unknown"; Total = "-1"; Wait = "-1"; Matched = "false" }
        /^Status:/ { StatusCount++; if ($0 ~ /^Status: ok[[:space:]]*$/) StatusOk = 1 }
        /^LaunchState:/ {
            StateCount++
            Value = $0
            sub(/^LaunchState:[[:space:]]*/, "", Value)
            sub(/[[:space:]]*$/, "", Value)
            if (Value ~ /^(COLD|WARM|HOT|RELAUNCH)$/) State = Value
        }
        /^TotalTime:/ {
            TotalCount++
            Value = $0
            sub(/^TotalTime:[[:space:]]*/, "", Value)
            sub(/[[:space:]]*$/, "", Value)
            if (Value ~ /^[0-9]+$/ && length(Value) <= 10) Total = Value
        }
        /^WaitTime:/ {
            WaitCount++
            Value = $0
            sub(/^WaitTime:[[:space:]]*/, "", Value)
            sub(/[[:space:]]*$/, "", Value)
            if (Value ~ /^[0-9]+$/ && length(Value) <= 10) Wait = Value
        }
        /^Activity:/ {
            ActivityCount++
            Value = $0
            sub(/^Activity:[[:space:]]*/, "", Value)
            sub(/[[:space:]]*$/, "", Value)
            if (split(Value, Parts, "/") == 2 && Parts[1] == Target && Parts[2] ~ /^[A-Za-z_.$][A-Za-z0-9_.$]*$/) Matched = "true"
        }
        /^Complete[[:space:]]*$/ { CompleteCount++ }
        /[^[:space:]]/ { LastLine = $0 }
        END {
            Confirmed = StatusCount == 1 && StatusOk && StateCount == 1 && State != "unknown" && TotalCount == 1 && Total != "-1" && ActivityCount == 1 && Matched == "true" && CompleteCount == 1 && LastLine ~ /^Complete[[:space:]]*$/ && WaitCount <= 1
            printf "%s %s %s %s %s\n", State, Total, Wait, Matched, Confirmed ? "true" : "false"
        }
    ')
    set -- $LaunchEvidence
    [ "$#" -eq 5 ] || return 1
    LaunchState=$1
    LaunchTotalTime=$2
    LaunchWaitTime=$3
    LaunchActivityMatch=$4
    [ "$5" = true ]
}

RestartApp()
{
    Restarted=false
    Stopped=false
    RestartError=
    RestartMessage=
    LaunchState=unknown
    LaunchTotalTime=-1
    LaunchWaitTime=-1
    LaunchActivityMatch=false
    Trace app_stop_start 0
    StopOutput=$(timeout -k 5 20 am force-stop --user 0 "$Package" 2>&1)
    StopCode=$?
    Trace app_stop_end "$StopCode"
    if [ "$StopCode" -ne 0 ] || printf '%s\n' "$StopOutput" | grep -Eiq 'exception|error:'; then
        RestartError=stop_failed
        RestartMessage='Android could not confirm that the app stopped.'
        return 1
    fi
    Stopped=true
    Trace activity_lookup_start 0
    ActivityOutput=$(timeout -k 5 20 cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$Package" 2>/dev/null)
    ActivityStatus=$?
    Trace activity_lookup_end "$ActivityStatus"
    if [ "$ActivityStatus" -ne 0 ]; then
        RestartError=activity_lookup_failed
        RestartMessage='The app stopped, but its launcher activity could not be resolved.'
        return 1
    fi
    Activity=$(printf '%s\n' "$ActivityOutput" | awk -F/ -v Target="$Package" '$1 == Target && NF == 2 && $2 ~ /^[A-Za-z_.$][A-Za-z0-9_.$]*$/ { print $0 }')
    if [ -z "$Activity" ] || [ "$(printf '%s\n' "$Activity" | wc -l | tr -d ' ')" != 1 ]; then
        RestartError=no_launcher
        RestartMessage='The app stopped, but it has no single launcher activity.'
        return 1
    fi
    Trace app_start_start 0
    StartOutput=$(timeout -k 5 30 am start --user 0 -W -n "$Activity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>&1)
    StartCode=$?
    LaunchConfirmed=false
    if ReadLaunchEvidence; then
        LaunchConfirmed=true
    fi
    Trace app_start_end "$StartCode"
    if [ "$StartCode" -ne 0 ] || printf '%s\n' "$StartOutput" | grep -Eiq '^Error:|exception'; then
        RestartError=start_failed
        RestartMessage='Android reported an error while launching the app.'
        return 1
    fi
    if [ "$LaunchConfirmed" != true ]; then
        RestartError=launch_unconfirmed
        RestartMessage='Android did not confirm a complete activity launch.'
        return 1
    fi
    Restarted=true
    return 0
}

RollbackApply()
{
    AttemptError=$RestartError
    AttemptMessage=$RestartMessage
    AttemptStopped=$Stopped
    Trace rollback_start 0
    if [ -L "$ProfileDir" ] || [ ! -d "$ProfileDir" ] || [ -L "$ProfileFile" ] || { [ -e "$ProfileFile" ] && [ ! -f "$ProfileFile" ]; }; then
        KeepPrevious=true
        ReplyError rollback_failed 'Automatic rollback could not safely access the configuration. The new override may remain active; use Restore and check the module debug log.'
    fi
    if [ "$HadPrevious" = true ]; then
        if [ -z "$PreviousFile" ] || [ ! -f "$PreviousFile" ] || [ -L "$PreviousFile" ] || ! mv -f "$PreviousFile" "$ProfileFile"; then
            KeepPrevious=true
            ReplyError rollback_failed 'Automatic rollback failed. The new override may remain active and the previous profile backup was retained. Use Restore and check the module debug log.'
        fi
        PreviousFile=
    else
        if ! rm -f "$ProfileFile"; then
            ReplyError rollback_failed 'Automatic rollback failed to remove the new override. It may remain active; use Restore and check the module debug log.'
        fi
    fi
    Saved=false
    RolledBack=true
    ConfigRevision=$PreviousRevision
    ConfiguredId=$PreviousId
    Trace rollback_complete 0
    if RestartApp; then
        RestoredLaunch=true
    else
        RestoredLaunch=false
    fi
    Restarted=false
    Stopped=$AttemptStopped
    if [ "$RestoredLaunch" = true ]; then
        ReplyError "$AttemptError" "$AttemptMessage The change was rolled back to the previous configuration, and Android confirmed its app launch. App health and Android ID remain unverified."
    fi
    ReplyError "$AttemptError" "$AttemptMessage The change was rolled back to the previous configuration, but its app launch could not be confirmed. Open the app manually."
}

GetManagerPresence()
{
    Trace companion_query_start 0
    ManagerPresence=error
    ManagerRows=$(timeout -k 5 10 pm list packages --user 0 "$ManagerPackage" 2>&1)
    ManagerCode=$?
    Trace companion_query_end "$ManagerCode"
    if [ "$ManagerCode" -ne 0 ]; then
        return 0
    fi
    if [ -n "$ManagerRows" ] && printf '%s\n' "$ManagerRows" | grep -Evq '^package:[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$'; then
        return 0
    fi
    if printf '%s\n' "$ManagerRows" | grep -Fxq "package:$ManagerPackage"; then
        ManagerPresence=installed
    else
        ManagerPresence=absent
    fi
}

ManagerQueryFailed()
{
    WriteState companion.error package_manager_failed
    ReplyError package_manager_failed 'Android could not confirm whether the companion app is installed. The previous install receipt was retained; try Repair again when Android is ready.'
}

InstallCompanion()
{
    ApkSize=$(wc -c < "$ModuleDir/manager.apk" | tr -d '[:space:]')
    case "$ApkSize" in
        ''|*[!0-9]*) ReplyError apk_invalid 'Cannot determine the bundled companion APK size.' ;;
    esac
    if [ "${#ApkSize}" -gt 10 ] || [ "$ApkSize" -le 0 ] || [ "$ApkSize" -gt 2147483647 ]; then
        ReplyError apk_invalid 'The bundled companion APK size is invalid.'
    fi
    ApkHash=$(sha256sum "$ModuleDir/manager.apk" | awk '{ print $1 }')
    if ! printf '%s\n' "$ApkHash" | grep -Eq '^[0-9a-f]{64}$'; then
        ReplyError apk_invalid 'Cannot verify the bundled companion APK checksum.'
    fi
    PreviousHash=$(cat "$StateDir/companion.sha256" 2>/dev/null)
    GetManagerPresence
    if [ "$ManagerPresence" = error ]; then
        ManagerQueryFailed
    fi
    if [ "$1" = automatic ] && [ -n "$PreviousHash" ]; then
        if [ "$ManagerPresence" = absent ]; then
            WriteState companion.status user_removed
            WriteState companion.error ''
            printf 'STATUS=ok\nCOMPANION_INSTALL=user_removed\nMESSAGE=Companion app was removed; automatic reinstall skipped.\n'
            return 0
        fi
        if [ "$PreviousHash" = "$ApkHash" ]; then
            WriteState companion.status installed
            WriteState companion.error ''
            printf 'STATUS=ok\nCOMPANION_INSTALL=installed\nMESSAGE=Companion app is already installed.\n'
            return 0
        fi
    fi
    WriteState companion.status pending
    WriteState companion.error ''
    Attempt=0
    while [ "$Attempt" -lt 3 ]; do
        Attempt=$((Attempt + 1))
        Trace "companion_install_${Attempt}_start" 0
        InstallOutput=$(timeout -k 5 45 pm install -r --user 0 -S "$ApkSize" -- - < "$ModuleDir/manager.apk" 2>&1)
        InstallCode=$?
        Trace "companion_install_${Attempt}_end" "$InstallCode"
        printf '%s\n' "$InstallOutput" > "$StateDir/install.log"
        chmod 0600 "$StateDir/install.log"
        if [ "$InstallCode" -eq 0 ] && printf '%s\n' "$InstallOutput" | grep -Fxq Success; then
            GetManagerPresence
            if [ "$ManagerPresence" = error ]; then
                ManagerQueryFailed
            fi
            if [ "$ManagerPresence" != installed ]; then
                WriteState companion.status failed
                ReplyError companion_install_failed 'Android reported installation success, but the companion is absent for owner user 0. The previous receipt was retained.'
            fi
            WriteState companion.sha256 "$ApkHash"
            WriteState companion.status installed
            WriteState companion.error ''
            printf 'STATUS=ok\nCOMPANION_INSTALL=installed\nMESSAGE=Companion app installed. Existing app data was preserved.\n'
            return 0
        fi
        if printf '%s\n' "$InstallOutput" | grep -Eq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE|INSTALL_FAILED_VERSION_DOWNGRADE|INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES'; then
            break
        fi
        if [ "$Attempt" -lt 3 ]; then
            sleep 5
        fi
    done
    WriteState companion.status failed
    ReplyError companion_install_failed 'Android rejected or timed out installing the companion app. Existing data was kept. See the root-private install log and use Repair.'
}
