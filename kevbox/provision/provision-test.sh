#!/bin/sh
# Runs provision.sh against a fake adb on PATH and checks each case; never touches a real box.
# Exit 0 when every case passes.
#
#   kevbox/provision/provision-test.sh
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
SCRIPT=$HERE/provision.sh
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# Only the tools provision.sh uses, so the real adb in /usr/bin stays out of reach.
TOOLS=$WORK/tools
mkdir "$TOOLS"
for tool in tr awk grep; do
    ln -s "$(command -v "$tool")" "$TOOLS/$tool"
done
# The resumed-activity retries would wait four seconds for nothing.
printf '#!/bin/sh\nexit 0\n' >"$TOOLS/sleep"
chmod +x "$TOOLS/sleep"

FAKE=$WORK/fake
mkdir "$FAKE"
cat >"$FAKE/adb" <<'EOF'
#!/bin/sh
# Logs every call, then answers from the FAKE_* variables with CRLF line ends like an old box.
printf '%s\n' "$*" >>"$FAKE_LOG"
printf '%s\n' "${ANDROID_SERIAL:-}" >>"$FAKE_SERIALS"
case $1 in
    devices) printf 'List of devices attached\r\n%b' "$FAKE_DEVICES" ;;
    push) ;;
    shell)
        shift
        case $* in
            'getprop ro.build.version.sdk') printf '%s\r\n' "$FAKE_SDK" ;;
            'settings get secure enabled_accessibility_services') printf '%s\r\n' "$FAKE_A11Y" ;;
            'cmd package set-home-activity'*) printf 'Success\r\n' ;;
            'am start'*) printf 'Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.HOME] }\r\n' ;;
            'dumpsys activity activities') printf '%b' "$FAKE_DUMPSYS" ;;
        esac
        # FAKE_FAIL names one shell command that exits 1, for the failure-line cases.
        [ -n "${FAKE_FAIL:-}" ] && [ "$*" = "$FAKE_FAIL" ] && exit 1
        ;;
esac
exit 0
EOF
chmod +x "$FAKE/adb"

JSON=$WORK/kevbox-home.json
printf '{"apps":["tv.kevbox"]}\n' >"$JSON"
D=/sdcard/Android/data/tv.kevbox.home/files
RESUMED='  mResumedActivity: ActivityRecord{1 u0 tv.kevbox.home/cloud.samlo.couch.MainActivity t5}\r\n'
RED=$(printf '\033[31m')
RESET=$(printf '\033[0m')

failures=0
check() { # <name> <condition result>
    if [ "$2" -eq 0 ]; then
        printf 'ok   %s\n' "$1"
    else
        printf 'FAIL %s\n' "$1"
        failures=$((failures + 1))
    fi
}

# run <with-adb: yes|no> [args...]; sets $code, $out (stdout), $err (stderr), $log (adb calls).
run() {
    path=$TOOLS
    [ "$1" = yes ] && path=$FAKE:$TOOLS
    shift
    : >"$WORK/log"
    : >"$WORK/serials"
    FAKE_LOG=$WORK/log FAKE_SERIALS=$WORK/serials PATH=$path \
        "$SCRIPT" "$@" >"$WORK/out" 2>"$WORK/err"
    code=$?
    out=$(cat "$WORK/out")
    err=$(cat "$WORK/err")
    log=$(cat "$WORK/log")
}

reset() {
    unset ANDROID_SERIAL KEVBOX_HOME_PACKAGE FAKE_FAIL
    export FAKE_DEVICES='box1\tdevice\r\n\r\n' FAKE_SDK=31 FAKE_A11Y='com.a/.A11y'
    export FAKE_DUMPSYS="$RESUMED"
}

red() { [ "$err" = "${RED}provision: $1${RESET}" ]; }

# --- no box touched without one device ---
reset
run yes
check "no argument: exit 2, usage, no adb call" "$([ $code -eq 2 ] && red 'usage: provision.sh <kevbox-home.json>' && [ -z "$log" ]; echo $?)"
run yes "$WORK/missing.json"
check "unreadable file: exit 2, usage, no adb call" "$([ $code -eq 2 ] && red 'usage: provision.sh <kevbox-home.json>' && [ -z "$log" ]; echo $?)"
run no "$JSON"
check "adb not on PATH" "$([ $code -eq 1 ] && red 'adb not found on PATH'; echo $?)"

FAKE_DEVICES='box1\tunauthorized\r\n\r\n'
run yes "$JSON"
check "zero devices: exit 1, only adb devices" "$([ $code -eq 1 ] && red 'no device: adb devices lists none in state "device"' && [ "$log" = devices ]; echo $?)"

FAKE_DEVICES='box1\tdevice\r\nbox2\tdevice\r\n\r\n'
run yes "$JSON"
check "two devices: exit 1, only adb devices" "$([ $code -eq 1 ] && red 'more than one device: set ANDROID_SERIAL to one of them' && [ "$log" = devices ]; echo $?)"

export ANDROID_SERIAL=box9
run yes "$JSON"
check "ANDROID_SERIAL not listed: exit 1, only adb devices" "$([ $code -eq 1 ] && red 'ANDROID_SERIAL=box9 is not a connected device' && [ "$log" = devices ]; echo $?)"

export ANDROID_SERIAL=box2
run yes "$JSON"
others=$(grep -vc '^box2$' "$WORK/serials")
check "two devices with ANDROID_SERIAL listed: goes ahead on that box" "$([ $code -eq 0 ] && [ "$out" = 'provision: done on box2' ] && [ "$others" -eq 0 ]; echo $?)"

# --- happy path order ---
reset
run yes "$JSON"
expected="devices
push $JSON $D/kevbox-home.json.tmp
shell mv $D/kevbox-home.json.tmp $D/kevbox-home.json
shell getprop ro.build.version.sdk
shell pm grant tv.kevbox.home android.permission.READ_TV_LISTINGS
shell pm grant tv.kevbox.home android.permission.WRITE_SECURE_SETTINGS
shell pm grant tv.kevbox.home android.permission.BLUETOOTH_CONNECT
shell appops set tv.kevbox.home GET_USAGE_STATS allow
shell appops set tv.kevbox.home REQUEST_INSTALL_PACKAGES allow
shell settings get secure enabled_accessibility_services
shell settings put secure enabled_accessibility_services 'com.a/.A11y:tv.kevbox.home/cloud.samlo.couch.HomeAccessibilityService'
shell settings put secure accessibility_enabled 1
shell cmd package set-home-activity tv.kevbox.home/cloud.samlo.couch.MainActivity
shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -p tv.kevbox.home
shell input keyevent KEYCODE_HOME
shell dumpsys activity activities"
check "SDK 31: exit 0, every call in AC-8 order" "$([ $code -eq 0 ] && [ "$log" = "$expected" ] && [ "$out" = 'provision: done on box1' ] && [ -z "$err" ]; echo $?)"

check "starts Home by HOME intent and package, never -n" "$(printf '%s\n' "$log" | grep -qx 'shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -p tv.kevbox.home' && ! printf '%s\n' "$log" | grep -q '^shell am start.* -n '; echo $?)"

FAKE_SDK=30
run yes "$JSON"
check "SDK 30: no BLUETOOTH_CONNECT grant" "$([ $code -eq 0 ] && ! printf '%s\n' "$log" | grep -q BLUETOOTH_CONNECT; echo $?)"

reset
export FAKE_FAIL='appops set tv.kevbox.home REQUEST_INSTALL_PACKAGES allow'
run yes "$JSON"
check "install permission refused: exit 1, stops before accessibility" "$([ $code -eq 1 ] && red 'install permission failed' && ! printf '%s\n' "$log" | grep -q '^shell settings'; echo $?)"

FAKE_SDK=28
unset FAKE_FAIL
run yes "$JSON"
check "SDK 28: the install permission is granted too" "$([ $code -eq 0 ] && printf '%s\n' "$log" | grep -qx 'shell appops set tv.kevbox.home REQUEST_INSTALL_PACKAGES allow'; echo $?)"

reset
FAKE_A11Y='null'
run yes "$JSON"
check "null accessibility list: only our service" "$(printf '%s\n' "$log" | grep -qx "shell settings put secure enabled_accessibility_services 'tv.kevbox.home/cloud.samlo.couch.HomeAccessibilityService'"; echo $?)"

FAKE_A11Y='tv.kevbox.home/cloud.samlo.couch.HomeAccessibilityService:com.a/.A11y'
run yes "$JSON"
check "service already listed: list written unchanged" "$(printf '%s\n' "$log" | grep -qx "shell settings put secure enabled_accessibility_services '$FAKE_A11Y'"; echo $?)"

reset
FAKE_DUMPSYS='  mResumedActivity: ActivityRecord{1 u0 tv.kevbox.home.dev/cloud.samlo.couch.MainActivity t5}\r\n'
run yes "$JSON"
tries=$(printf '%s\n' "$log" | grep -c '^shell dumpsys activity activities$')
check "activity missing from dumpsys: exit 1 after 5 tries" "$([ $code -eq 1 ] && red 'KevBox Home is not the resumed activity' && [ "$tries" -eq 5 ]; echo $?)"

reset
export KEVBOX_HOME_PACKAGE=tv.kevbox.home.dev
FAKE_DUMPSYS=$(printf '%s' "$RESUMED" | sed 's#tv.kevbox.home/#tv.kevbox.home.dev/#')
run yes "$JSON"
check "KEVBOX_HOME_PACKAGE points every step at the debug build" "$([ $code -eq 0 ] && printf '%s\n' "$log" | grep -qx "shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -p tv.kevbox.home.dev"; echo $?)"

if [ $failures -ne 0 ]; then
    printf '%s case(s) failed\n' "$failures"
    exit 1
fi
printf 'every case passed\n'
