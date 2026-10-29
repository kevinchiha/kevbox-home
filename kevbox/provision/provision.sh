#!/bin/sh
# Provisions one TV box for KevBox Home: pushes a kevbox-home.json, grants the permissions the
# app needs, switches on its accessibility service, makes it Home and checks it is in front.
#
#   kevbox/provision/provision.sh <kevbox-home.json>
#
# ANDROID_SERIAL picks the box when more than one is connected.
# KEVBOX_HOME_PACKAGE=tv.kevbox.home.dev points every step at the debug build instead.
# Exit 0 only when every step passed; a failure prints one red line naming the step.
# The file format and the failure lines are described in docs/kevbox/provisioning.md.
set -u

PKG=${KEVBOX_HOME_PACKAGE:-tv.kevbox.home}
DIR=/sdcard/Android/data/$PKG/files
ACTIVITY=$PKG/cloud.samlo.couch.MainActivity
SERVICE=$PKG/cloud.samlo.couch.HomeAccessibilityService

fail() {
    printf '\033[31m%s\033[0m\n' "provision: $1" >&2
    exit "${2:-1}"
}

# Runs `adb shell` and prints its reply without the \r older boxes add; fails when adb does.
shout() {
    reply=$(adb shell "$@") || return 1
    printf '%s\n' "$reply" | tr -d '\r'
}

if [ $# -ne 1 ] || [ ! -f "$1" ] || [ ! -r "$1" ]; then
    fail "usage: provision.sh <kevbox-home.json>" 2
fi
FILE=$1

command -v adb >/dev/null 2>&1 || fail "adb not found on PATH"

# Serials in state "device"; "unauthorized" and "offline" boxes cannot be provisioned.
devices=$(adb devices 2>/dev/null | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ -n "${ANDROID_SERIAL:-}" ]; then
    found=
    for serial in $devices; do
        [ "$serial" = "$ANDROID_SERIAL" ] && found=1
    done
    [ -n "$found" ] || fail "ANDROID_SERIAL=$ANDROID_SERIAL is not a connected device"
else
    set -- $devices
    [ $# -eq 0 ] && fail 'no device: adb devices lists none in state "device"'
    [ $# -gt 1 ] && fail "more than one device: set ANDROID_SERIAL to one of them"
    ANDROID_SERIAL=$1
fi
export ANDROID_SERIAL

# Pushed under a .tmp name and moved, so a Home load mid-push never reads half a file.
adb push "$FILE" "$DIR/kevbox-home.json.tmp" >/dev/null &&
    adb shell mv "$DIR/kevbox-home.json.tmp" "$DIR/kevbox-home.json" >/dev/null ||
    fail "push failed"

sdk=$(shout getprop ro.build.version.sdk) || fail "reading the SDK level failed"
case $sdk in
    '' | *[!0-9]*) fail "reading the SDK level failed" ;;
esac

grant() {
    adb shell pm grant "$PKG" "android.permission.$1" >/dev/null || fail "grant $1 failed"
}
grant READ_TV_LISTINGS
grant WRITE_SECURE_SETTINGS
# BLUETOOTH_CONNECT exists from Android 12 (SDK 31); older boxes refuse an unknown permission.
[ "$sdk" -ge 31 ] && grant BLUETOOTH_CONNECT

adb shell appops set "$PKG" GET_USAGE_STATS allow >/dev/null || fail "usage access failed"
# "Install unknown apps", so the self-update needs no member to allow it. With it, Android 12 and
# later apply an update KevBox Home installs on itself with no confirm screen: an app updating
# itself needs no installer of record (AOSP PackageInstallerSession, isSelfUpdate).
adb shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow >/dev/null || fail "install permission failed"

services=$(shout settings get secure enabled_accessibility_services) ||
    fail "accessibility service failed"
case $services in
    '' | null) services=$SERVICE ;;
    *)
        case ":$services:" in
            *":$SERVICE:"*) ;;
            *) services=$services:$SERVICE ;;
        esac
        ;;
esac
# Single quotes on the remote side: a component name may hold a $.
adb shell "settings put secure enabled_accessibility_services '$services'" >/dev/null &&
    adb shell settings put secure accessibility_enabled 1 >/dev/null ||
    fail "accessibility service failed"

reply=$(shout cmd package set-home-activity "$ACTIVITY") || fail "set Home failed"
case $reply in
    *Success*) ;;
    *) fail "set Home failed" ;;
esac

# Started as Home, not with -n: an explicit component builds a standard launch, which puts a
# second MainActivity next to the Home one (KevBox.kt says why).
reply=$(shout am start -a android.intent.action.MAIN -c android.intent.category.HOME -p "$PKG") ||
    fail "start failed"
case $reply in
    *Error*) fail "start failed" ;;
esac

adb shell input keyevent KEYCODE_HOME >/dev/null || fail "HOME key failed"

# The slash after $PKG keeps tv.kevbox.home from matching tv.kevbox.home.dev.
try=1
while :; do
    if reply=$(shout dumpsys activity activities) &&
        printf '%s\n' "$reply" | grep ResumedActivity | grep -qF "$ACTIVITY"; then
        break
    fi
    [ $try -ge 5 ] && fail "KevBox Home is not the resumed activity"
    try=$((try + 1))
    sleep 1
done

printf 'provision: done on %s\n' "$ANDROID_SERIAL"
