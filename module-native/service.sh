#!/system/bin/sh

ModuleDir=${0%/*}
. "$ModuleDir/common.sh"
CheckRoot
BeginTrace service
CheckModule
PrepareState

BootAttempts=0
while [ "$BootAttempts" -lt 120 ]; do
    if [ "$(getprop sys.boot_completed)" = 1 ] && timeout -k 5 10 pm path android 2>/dev/null | grep -q '^package:'; then
        Trace package_manager_ready 0
        AcquireLock
        InstallCompanion automatic
        exit $?
    fi
    BootAttempts=$((BootAttempts + 1))
    sleep 2
done

WriteState companion.error boot_timeout
ReplyError boot_timeout 'Android package manager did not become ready. Open the root manager module action to repair the companion installation.'
