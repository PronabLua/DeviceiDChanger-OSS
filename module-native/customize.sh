#!/system/bin/sh

if [ "$BOOTMODE" != "true" ]; then
    abort "Install this module from the root manager while Android is running."
fi

if [ "$ARCH" != "arm64" ]; then
    abort "Only ARM64 Android devices are supported."
fi

if [ "$API" -lt 29 ] || [ "$API" -gt 37 ]; then
    abort "This build supports Android 10 through Android 17."
fi

for RequiredFile in manager.apk hook.dex zygisk/arm64-v8a.so common.sh control.sh service.sh; do
    if [ ! -s "$MODPATH/$RequiredFile" ]; then
        abort "The module archive is incomplete: $RequiredFile"
    fi
done

set_perm_recursive "$MODPATH" 0 0 0755 0644
for Script in common.sh control.sh service.sh action.sh uninstall.sh; do
    set_perm "$MODPATH/$Script" 0 0 0755
done

touch "$MODPATH/skip_mount"
ui_print "Native Android ID manager and Zygisk module installed."
ui_print "Enable Zygisk in your root manager and reboot once."
ui_print "The companion app will install after Android finishes booting."
ui_print "Later ID changes use Apply and restart app."
ui_print "This prototype does not yet provide runtime verification."
