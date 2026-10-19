#!/system/bin/sh

ModuleDir=${0%/*}
. "$ModuleDir/common.sh"
CheckRoot
BeginTrace uninstall

if [ -d "$StateDir" ] && [ ! -L "$StateDir" ]; then
    if [ -d "$StateDir/profiles" ] && [ ! -L "$StateDir/profiles" ]; then
        rm -rf "$StateDir/profiles" || ReplyError storage_failed 'Could not remove saved app overrides.'
        Trace profiles_removed 0
    fi
    printf '%s\n' 'Module removed. The companion app and its install receipt were retained.' > "$StateDir/uninstall.log"
    chmod 0600 "$StateDir/uninstall.log"
fi
