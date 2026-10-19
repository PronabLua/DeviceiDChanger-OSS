#!/usr/bin/env bash

set -Eeu
trap 'printf "FAIL: helper test line %s\n" "$LINENO" >&2' ERR
Root=$(cd "$(dirname "$0")/.." && pwd)
Fixture=$(mktemp -d "$Root/.helper-tests.XXXXXX")
case "$Fixture" in
    "$Root"/.helper-tests.*) ;;
    *) exit 1 ;;
esac
trap 'rm -rf -- "$Fixture"' EXIT
export TestPath="$PATH"
export TestState="$Fixture/state"
export TestApk="$Fixture/module/manager.apk"
export TestToybox="$Fixture/toybox"
export TestCase=normal
export BASH_ENV="$Fixture/mock.sh"
Checks=0
mkdir -p "$Fixture/module/zygisk" "$TestState"
cp "$Root/control.sh" "$Root/service.sh" "$Root/module.prop" "$Fixture/module/"
sed -e 's|^PATH=.*|PATH=$TestPath|' -e 's|^StateDir=.*|StateDir=$TestState|' -e 's|^LockBinary=.*|LockBinary=$TestToybox|' -e 's|"\$LockBinary" flock|flock|g' "$Root/common.sh" > "$Fixture/module/common.sh"
printf '#!/usr/bin/env bash\n[ "$1" = flock ] || exit 127\nshift\nflock "$@"\n' > "$TestToybox"
chmod 0755 "$TestToybox"
printf 'test apk\n' > "$Fixture/module/manager.apk"
printf 'test dex\n' > "$Fixture/module/hook.dex"
printf 'test native library\n' > "$Fixture/module/zygisk/arm64-v8a.so"
cat > "$BASH_ENV" <<'MOCK'
id()
{
    if [ "$TestCase" = no_root ]; then printf '2000\n'; else printf '0\n'; fi
}
chown() { :; }
mv()
{
    if [ "$TestCase" = rollback_write_failed ]; then
        case "${2:-}" in
            */.previous.*) return 1 ;;
        esac
    fi
    command mv "$@"
}
rm()
{
    if [ "$TestCase" = rollback_delete_failed ]; then
        case "$*" in
            *'/com.example.target.conf') return 1 ;;
        esac
    fi
    command rm "$@"
}
date()
{
    if [ "$1" = +%s ]; then printf '1792300800\n'; else printf '2026-10-18T08:00:00Z\n'; fi
}
stat()
{
    if [ "$1" = -c ] && [ "$2" = '%u:%a' ]; then printf '0:700\n'; else command stat "$@"; fi
}
flock()
{
    if [ "$1" = -n ] && [ "$2" = 9 ]; then
        case "$TestCase" in
            busy) return 1 ;;
            lock_failed) printf 'flock: Bad file descriptor\n' >&2; return 1 ;;
            lock_unsupported) printf 'flock: unknown command\n' >&2; return 127 ;;
        esac
    fi
    return 0
}
sleep() { :; }
timeout()
{
    [ "$1" = -k ] && [ "$2" = 5 ] || return 98
    shift 2
    [ "$1" -gt 0 ] || return 98
    shift
    "$@"
}
getprop() { printf '1\n'; }
pm()
{
    case "$1" in
        list)
            case " $* " in
                *' io.github.pronablua.deviceidchanger '*)
                    case "$TestCase" in
                        pm_timeout) return 124 ;;
                        pm_failed) printf 'Error: package manager unavailable\n' >&2; return 1 ;;
                        pm_bad_output) printf 'Error: package manager unavailable\n'; return 0 ;;
                        pm_after_install) if [ -f "$TestState/post-install-error" ]; then return 1; fi ;;
                    esac
                    if [ -f "$TestState/manager-present" ]; then printf 'package:io.github.pronablua.deviceidchanger\n'; fi
                    return 0
                    ;;
            esac
            case " $* " in
                *' -s '*)
                    if [ "$TestCase" = system ]; then printf 'package:com.example.target\n'; fi
                    return 0
                    ;;
            esac
            case "$TestCase" in
                secondary) printf 'package:com.example.target uid:110123\n' ;;
                sandbox) printf 'package:com.example.target uid:20123\n' ;;
                isolated) printf 'package:com.example.target uid:90123\n' ;;
                shared) printf 'package:com.example.target uid:10123\npackage:com.example.other uid:10123\n' ;;
                missing) printf 'package:com.example.other uid:10124\n' ;;
                *) printf 'package:com.example.target uid:10123\npackage:com.example.other uid:10124\n' ;;
            esac
            ;;
        path)
            case " $* " in
                *' android '*) printf 'package:/system/framework/framework-res.apk\n' ;;
                *) if [ -f "$TestState/manager-present" ]; then printf 'package:/data/app/manager/base.apk\n'; fi ;;
            esac
            ;;
        install)
            printf '%s\n' "$*" >> "$TestState/install-calls"
            [ "$#" -eq 8 ] && [ "$2" = -r ] && [ "$3" = --user ] && [ "$4" = 0 ] && [ "$5" = -S ] && [ "$7" = -- ] && [ "$8" = - ] || return 97
            cat > "$TestState/streamed.apk"
            [ "$(wc -c < "$TestState/streamed.apk" | tr -d '[:space:]')" = "$6" ] || return 97
            cmp "$TestState/streamed.apk" "$TestApk" || return 97
            case "$TestCase" in
                signature_conflict) printf 'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\n'; return 1 ;;
                install_failed) printf 'Failure [INSTALL_FAILED_INTERNAL_ERROR]\n'; return 1 ;;
            esac
            touch "$TestState/manager-present"
            if [ "$TestCase" = pm_after_install ]; then touch "$TestState/post-install-error"; fi
            printf 'Success\n'
            ;;
        *) return 1 ;;
    esac
}
am()
{
    printf '%s\n' "$*" >> "$TestState/activity-calls"
    case "$1" in
        force-stop)
            if [ "$TestCase" = stop_failed ]; then printf 'Error: denied\n'; return 1; fi
            ;;
        start)
            if [ "$TestCase" = start_failed ]; then printf 'Error: not found\n'; return 1; fi
            StartCount=$(cat "$TestState/start-count" 2>/dev/null || printf 0)
            StartCount=$((StartCount + 1))
            printf '%s\n' "$StartCount" > "$TestState/start-count"
            case "$TestCase" in
                launch_unknown|rollback_write_failed|rollback_delete_failed)
                    printf 'Status: ok\nLaunchState: UNKNOWN (0)\nActivity: com.example.target/.MainActivity\nWaitTime: 13800\nComplete\n'
                    return 0
                    ;;
                unknown_once)
                    if [ "$StartCount" -eq 1 ]; then
                        printf 'Status: ok\nLaunchState: UNKNOWN (0)\nActivity: com.example.target/.MainActivity\nWaitTime: 13800\nComplete\n'
                        return 0
                    fi
                    ;;
                alias_launch)
                    printf 'Status: ok\nLaunchState: COLD\nActivity: com.example.target/com.example.target.GameActivity\nTotalTime: 200\nWaitTime: 220\nComplete\n'
                    return 0
                    ;;
                slow_launch)
                    printf 'Status: ok\nLaunchState: COLD\nActivity: com.example.target/.MainActivity\nTotalTime: 13800\nWaitTime: 13820\nComplete\n'
                    return 0
                    ;;
            esac
            printf 'Status: ok\nLaunchState: COLD\nActivity: com.example.target/.MainActivity\nTotalTime: 200\nWaitTime: 220\nComplete\n'
            ;;
        *) return 1 ;;
    esac
}
cmd()
{
    if [ "$TestCase" = no_launcher ]; then printf 'No activity found\n'; else printf 'com.example.target/.MainActivity\n'; fi
}
MOCK

Run()
{
    rm -f "$TestState/start-count"
    ExitCode=0
    Output=$(bash "$Fixture/module/control.sh" "$@") || ExitCode=$?
}

Expect()
{
    Checks=$((Checks + 1))
    if ! printf '%s\n' "$Output" | grep -Fxq "$1"; then
        printf 'Missing expected result: %s\n%s\n' "$1" "$Output" >&2
        exit 1
    fi
}

Reject()
{
    TestCase=$1
    Run apply com.example.target 0123456789abcdef
    [ "$ExitCode" -ne 0 ]
    Expect "ERROR_CODE=$2"
    Expect SAVED=false
}

Run status
Expect STATUS=ok
Expect RUNTIME=unverified
Expect USER_ID=0
Run get com.example.target
Expect CONFIGURED_ID=
Expect ENABLED=false
Run apply com.example.target xyz
Expect ERROR_CODE=invalid_id
Run apply 'com.example.target;touch injected' 0123456789abcdef
Expect ERROR_CODE=invalid_package
Reject no_root root_required
Reject shared shared_uid
Reject system system_app
Reject secondary unsupported_uid
Reject sandbox unsupported_uid
Reject isolated unsupported_uid
Reject missing package_missing
Reject busy busy
Reject lock_failed lock_failed
Reject lock_unsupported lock_unsupported
grep -q 'action=apply phase=error_lock_failed pid=' "$TestState/debug.log"

TestCase=normal
Run apply com.example.target ABCDEF0123456789
[ "$ExitCode" -eq 0 ]
Expect SAVED=true
Expect RESTARTED=true
Expect CONFIGURED_ID=abcdef0123456789
Expect VERIFICATION=not_verified
grep -q 'action=apply phase=profile_commit pid=' "$TestState/debug.log"
Profile="$TestState/profiles/10123/com.example.target.conf"
[ "$(wc -l < "$Profile" | tr -d ' ')" = 2 ]
grep -Fxq 'force-stop --user 0 com.example.target' "$TestState/activity-calls"
grep -Fxq 'start --user 0 -W -n com.example.target/.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER' "$TestState/activity-calls"
Run get com.example.target
Expect CONFIGURED_ID=abcdef0123456789
Expect OBSERVED_ID=
cp "$Profile" "$Fixture/prior-profile"
for Failure in no_launcher stop_failed start_failed; do
    TestCase=$Failure
    Run apply com.example.target 1111111111111111
    [ "$ExitCode" -ne 0 ]
    Expect "ERROR_CODE=$Failure"
    Expect SAVED=false
    Expect ROLLED_BACK=true
    Expect RESTORED_LAUNCH=false
    Expect RESTARTED=false
    cmp "$Profile" "$Fixture/prior-profile"
done
TestCase=launch_unknown
Run apply com.example.target 1111111111111111
Expect ERROR_CODE=launch_unconfirmed
Expect SAVED=false
Expect ROLLED_BACK=true
Expect RESTORED_LAUNCH=false
Expect RESTARTED=false
cmp "$Profile" "$Fixture/prior-profile"
[ "$(cat "$TestState/start-count")" = 2 ]
TestCase=unknown_once
Run apply com.example.target 1111111111111111
Expect ERROR_CODE=launch_unconfirmed
Expect SAVED=false
Expect ROLLED_BACK=true
Expect RESTORED_LAUNCH=true
Expect RESTARTED=false
cmp "$Profile" "$Fixture/prior-profile"
[ "$(cat "$TestState/start-count")" = 2 ]
TestCase=rollback_write_failed
Run apply com.example.target 1111111111111111
Expect ERROR_CODE=rollback_failed
Expect SAVED=true
Expect ROLLED_BACK=false
Expect RESTORED_LAUNCH=false
grep -Fxq 1111111111111111 "$Profile"
Backup=$(find "$TestState/profiles/10123" -maxdepth 1 -name '.previous.*' -type f -print -quit)
[ -n "$Backup" ]
cmp "$Backup" "$Fixture/prior-profile"
rm "$Backup"
cp "$Fixture/prior-profile" "$Profile"
[ "$(cat "$TestState/start-count")" = 1 ]
TestCase=normal
Run restore com.example.target
Expect SAVED=true
Expect RESTARTED=true
[ ! -e "$Profile" ]
TestCase=unknown_once
Run apply com.example.target 1111111111111111
Expect ERROR_CODE=launch_unconfirmed
Expect SAVED=false
Expect ROLLED_BACK=true
Expect RESTORED_LAUNCH=true
[ ! -e "$Profile" ]
[ "$(cat "$TestState/start-count")" = 2 ]
TestCase=rollback_delete_failed
Run apply com.example.target 1111111111111111
Expect ERROR_CODE=rollback_failed
Expect SAVED=true
Expect ROLLED_BACK=false
Expect RESTORED_LAUNCH=false
[ -f "$Profile" ]
[ "$(cat "$TestState/start-count")" = 1 ]
TestCase=launch_unknown
Run restore com.example.target
Expect ERROR_CODE=launch_unconfirmed
Expect SAVED=true
Expect ROLLED_BACK=false
Expect RESTARTED=false
[ ! -e "$Profile" ]
[ "$(cat "$TestState/start-count")" = 1 ]
for Success in alias_launch slow_launch; do
    TestCase=$Success
    Run apply com.example.target abcdef0123456789
    Expect STATUS=ok
    Expect SAVED=true
    Expect RESTARTED=true
    Expect ROLLED_BACK=false
done
TestCase=normal
Run restore com.example.target
Expect STATUS=ok
printf '0123456789abcdef\ndeadbeefdeadbeef\nextra\n' > "$Profile"
Run get com.example.target
Expect ERROR_CODE=profile_invalid
Run restore com.example.target
Expect STATUS=ok
mkdir "$Profile"
for Operation in get restore; do
    Run "$Operation" com.example.target
    Expect ERROR_CODE=unsafe_state
    Expect SAVED=false
done
Run apply com.example.target 0123456789abcdef
Expect ERROR_CODE=unsafe_state
Expect SAVED=false
[ -z "$(find "$Profile" -mindepth 1 -print -quit)" ]
rmdir "$Profile"
printf 'PASS: launch rollback transactions and exact profile recovery.\n'

Output=$(bash "$Fixture/module/service.sh")
Expect COMPANION_INSTALL=installed
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 1 ]
Output=$(bash "$Fixture/module/service.sh")
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 1 ]
Receipt=$(cat "$TestState/companion.sha256")
for Failure in pm_timeout pm_failed pm_bad_output; do
    TestCase=$Failure
    ExitCode=0
    Output=$(bash "$Fixture/module/service.sh") || ExitCode=$?
    [ "$ExitCode" -ne 0 ]
    Expect ERROR_CODE=package_manager_failed
    [ "$(cat "$TestState/companion.status")" = installed ]
    [ "$Receipt" = "$(cat "$TestState/companion.sha256")" ]
    [ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 1 ]
done
TestCase=normal
rm "$TestState/manager-present"
Output=$(bash "$Fixture/module/service.sh")
Expect COMPANION_INSTALL=user_removed
TestCase=pm_timeout
ExitCode=0
Output=$(bash "$Fixture/module/service.sh") || ExitCode=$?
[ "$ExitCode" -ne 0 ]
Expect ERROR_CODE=package_manager_failed
[ "$(cat "$TestState/companion.status")" = user_removed ]
[ "$Receipt" = "$(cat "$TestState/companion.sha256")" ]
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 1 ]
TestCase=normal
printf 'upgraded test apk\n' > "$Fixture/module/manager.apk"
Output=$(bash "$Fixture/module/service.sh")
Expect COMPANION_INSTALL=user_removed
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 1 ]
Run repair
Expect COMPANION_INSTALL=installed
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 2 ]
Receipt=$(cat "$TestState/companion.sha256")
printf 'conflicting test apk\n' > "$Fixture/module/manager.apk"
TestCase=signature_conflict
Run repair
Expect ERROR_CODE=companion_install_failed
[ -f "$TestState/manager-present" ]
[ "$Receipt" = "$(cat "$TestState/companion.sha256")" ]
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 3 ]
TestCase=install_failed
Run repair
Expect ERROR_CODE=companion_install_failed
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 6 ]
TestCase=pm_after_install
Run repair
Expect ERROR_CODE=package_manager_failed
[ -f "$TestState/manager-present" ]
[ "$Receipt" = "$(cat "$TestState/companion.sha256")" ]
[ "$(wc -l < "$TestState/install-calls" | tr -d ' ')" = 7 ]
grep -q 'duration_s=' "$TestState/debug.log"
grep -q 'launch_state=' "$TestState/debug.log"
if grep -Eq 'abcdef0123456789|0123456789abcdef|1111111111111111|pm install|com.example.target' "$TestState/debug.log"; then exit 1; fi
head -c 70000 /dev/zero | tr '\0' x >> "$TestState/debug.log"
TestCase=normal
Run status
Expect STATUS=ok
[ "$(wc -c < "$TestState/debug.log")" -lt 65536 ]
[ "$(wc -c < "$TestState/debug.log.1")" -lt 65536 ]
printf 'PASS: %s protocol assertions plus profile, stream, receipt, and retry checks.\n' "$Checks"
