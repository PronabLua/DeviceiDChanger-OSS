#!/system/bin/sh

ModuleDir=${0%/*}
. "$ModuleDir/common.sh"
CheckRoot
BeginTrace module_action
CheckModule

GetManagerPresence
if [ "$ManagerPresence" = error ]; then
    ReplyError package_manager_failed 'Android package manager is unavailable. Try the module action again when Android is ready.'
fi
if [ "$ManagerPresence" = absent ]; then
    printf '%s\n' 'The companion app is missing. Installing the bundled signed APK.'
    /system/bin/sh "$ModuleDir/control.sh" repair || exit 1
fi

timeout -k 5 30 am start --user 0 -W -n io.github.pronablua.deviceidchanger/.MainActivity
