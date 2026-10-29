# Checking KevBox Home on a TV

The checks Kevin runs by hand on the TCL test TV and on his own Xiaomi TV Box S, one section per
acceptance criterion of the 0.1-kevbox-home spec. Tick each line on both devices unless it names
one. Run the commands from the repo root on a computer with `adb` connected to the device; with
both devices connected, prefix each command with `ANDROID_SERIAL=<tv ip>:5555`.

Presses are the remote's buttons: OK, hold OK (keep it down for about a second), Back, Home, Up,
Down, Left, Right, Netflix, Prime Video, YouTube, All apps. Text in quotes is exactly what the
screen should show.

## Before you start

Do these on both devices, in this order, before any key or Home check.

- [ ] Switch off the accessibility services of Projectivy (`com.spocky.projengmenu`) and
  tvQuickActions (`dev.vodik7.tvquickactions`). They filter the same remote buttons as KevBox Home,
  so with them on, a failed check proves nothing. Print the list:

  ```sh
  adb shell settings get secure enabled_accessibility_services
  ```

  Write it back without every entry that starts with `com.spocky.projengmenu/` or
  `dev.vodik7.tvquickactions/`, keeping the others joined with `:`:

  ```sh
  adb shell "settings put secure enabled_accessibility_services '<the remaining entries, joined with :>'"
  ```

  Run the `get` line again: neither package may be left in it.
- [ ] Build with the gate (`./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`)
  and install the release APK by hand, so the AC-1 check runs on the TV:

  ```sh
  adb install -r app/build/outputs/apk/release/app-release.apk
  ```

  Without a release key the build is signed with the debug key and still installs as
  `tv.kevbox.home`. A later build signed with the real key then needs
  `adb uninstall tv.kevbox.home` first.
- [ ] Open KevBox Home once from the TV's app list, so its files folder exists, then provision it
  with the example file:

  ```sh
  kevbox/provision/provision.sh kevbox/provision/kevbox-home.example.json
  ```

  A debug build installed beside the release one is provisioned with
  `KEVBOX_HOME_PACKAGE=tv.kevbox.home.dev` in front of that line, and its checks use
  `tv.kevbox.home.dev` in place of `tv.kevbox.home` in every path and command below.
- [ ] The Xiaomi box is Kevin's own box, never a member's. Nothing here goes on a member's box until
  Couch carries a licence.

[Provisioning a box](provisioning.md) explains the file and every line the script can print.

## AC-1: identity

- [ ] `adb shell pm list packages tv.kevbox.home` prints `package:tv.kevbox.home`.
- [ ] `adb shell dumpsys package tv.kevbox.home | grep -E 'versionCode|versionName'` shows
  `versionName=0.8.1.0` and `versionCode=80100` (until a `v0.8.1.N` tag exists).
- [ ] On the TV, Settings > Apps lists the app as "KevBox Home".
- [ ] Android's accessibility settings list "KevBox Home", switched
  on by the script.
- [ ] Nowhere in KevBox Home's own screens does the word "Couch" appear.
- [ ] Optional, with a debug build installed too: `adb shell pm list packages tv.kevbox.home`
  prints both `package:tv.kevbox.home` and `package:tv.kevbox.home.dev`.

## AC-6: the provision file is applied on start and resume

Write two files on the computer: `good.json` holding `{"hidden": []}`, and `bad.json` holding
the text `not json`.

- [ ] Push the good one and press Home:

  ```sh
  adb push good.json /sdcard/Android/data/tv.kevbox.home/files/kevbox-home.json
  ```

  Then `adb shell ls /sdcard/Android/data/tv.kevbox.home/files/` lists
  `kevbox-home.applied.json` and no `kevbox-home.json`, and
  `adb logcat -d -s KevBoxHome` has a line `applied kevbox-home.json: hidden`.
- [ ] Note what `adb shell cat /sdcard/Android/data/tv.kevbox.home/files/config.json` prints. Push
  the bad one and press Home:

  ```sh
  adb push bad.json /sdcard/Android/data/tv.kevbox.home/files/kevbox-home.json
  ```

  Then the folder lists `kevbox-home.rejected.json` and no `kevbox-home.json`, `config.json` prints
  exactly as before, and `adb logcat -d -s KevBoxHome` has a line starting
  `rejected kevbox-home.json:` with the parser's reason.
- [ ] Push the good one again while another app is open, then press Back or Home to return:
  `kevbox-home.applied.json` is replaced and `kevbox-home.json` is gone, so a resume also applies it.

## AC-8: the provisioning script

- [ ] With the device disconnected (`adb disconnect`), the script prints one red line,
  `provision: no device: adb devices lists none in state "device"`, and `echo $?` prints a
  non-zero code. Nothing changed on the device.
- [ ] With both devices connected and no `ANDROID_SERIAL`, it prints the red line
  `provision: more than one device: set ANDROID_SERIAL to one of them` and exits non-zero.
- [ ] With one device, it ends with `provision: done on <serial>` and exits 0. Home then shows
  KevBox Home.
- [ ] `adb shell settings get secure enabled_accessibility_services` holds
  `tv.kevbox.home/cloud.samlo.couch.HomeAccessibilityService` and every service that was there
  before, and `adb shell settings get secure accessibility_enabled` prints `1`.
- [ ] `adb shell ls /sdcard/Android/data/tv.kevbox.home/files/` lists
  `kevbox-home.applied.json` and no `kevbox-home.json`. That is how you see the app consumed the
  pushed file, since the script does not check it.

## AC-9: the example file matches the boxes

- [ ] Each package in `kevbox/provision/kevbox-home.example.json` is the app it claims to be.
  `adb shell pm list packages` lists `tv.kevbox.live` (KevBox Live), `tv.kevbox` (KevBox TV),
  `org.smarttube.stable` (SmartTube), `com.surfshark.vpnclient.android` (Surfshark),
  `com.rma.netpulsetv` (Speedtest), `com.android.vending` (Play Store), `nextapp.fx` (FX),
  `com.esaba.downloader` (Downloader) and `org.videolan.vlc` (VLC). Note any the device lacks.
- [ ] Its `keys` are the AC-28 map: the remote checks below pass with it applied.

## AC-10: the left column

- [ ] From Home, the column shows KevBox Live, KevBox TV and SmartTube, in that order, then
  "More apps". No other app is in it.
- [ ] Open SmartTube for a while, return with Home: the order has not changed.
- [ ] Push `{"hidden": ["org.smarttube.stable"]}` as in AC-6 and press Home: SmartTube is gone from
  the column, and from the drawer (All apps). Push `{"hidden": []}` to bring it back.
- [ ] Push `{"apps": []}` and press Home: the column holds only "More apps". Run
  `provision.sh` with the example file again to restore it.

## AC-11: status lines

- [ ] In KevBox TV, play an episode part way and stop with time left. Back on Home, KevBox TV's
  row reads `<title> · S<season> E<episode> · <time left>`, for example
  "Severance · S2 E4 · 31 min left", or "1 h 05 left" from an hour up. An episode without a
  season shows "E4" alone.
- [ ] SmartTube's row reads `Opened today at <time>` (its channel has no watch position). After a
  day it reads `Opened yesterday at <time>`, then the weekday ("Opened Wednesday at 22:37").
- [ ] Customize > 24-hour clock off: the time in the line switches to the 12-hour clock.
- [ ] KevBox Live's row reads "Opened …" too (the Recent channels row is the next phase).
- [ ] An app in the column not opened in the last seven days has its name alone, centred, with no
  status line. A long line ends in "…" on one line. No row shows a screen-time figure.

## AC-12: More apps

- [ ] From the first app, press Down until the last app, then Down once more: focus lands on
  "More apps", under a thin divider.
- [ ] OK on "More apps" opens the drawer.

## AC-13: the right side and the backdrop

- [ ] With an app focused in the column, the right side shows its spotlight text, its channel row
  and the week's screen-time chart, as in Couch.
- [ ] The whole screen behind shows the first card's art. Press Right into the channel row and move
  along it: the art follows the focused card.
- [ ] Focus "More apps": the backdrop shows the wallpaper.
- [ ] Customize > Dim set to Light, Medium, then Strong: the backdrop gets darker each step.

## AC-14: the top right

- [ ] Top right shows, in order: Wi-Fi, VPN (only while a VPN is on), Bluetooth, Speed test,
  Settings, then the clock and the weather.
- [ ] Push `{"buttons": ["bluetooth", "wifi"]}` and press Home: the row is Bluetooth, Wi-Fi, then
  Settings (always added). Push `{"buttons": []}`: the default order is back.
- [ ] While an update waits (a newer `version.json` on tv.kevbox.dev), `Update <versionName>` shows
  left of the buttons.

## AC-15: the Wi-Fi button

- [ ] On Wi-Fi: the icon shows the signal level, with a green dot while the internet works.
- [ ] On Ethernet (the TCL TV when plugged in): the icon is the Ethernet one, with the dot.
- [ ] Unplug the network, or switch Wi-Fi off in Android's settings: the no-network icon, no dot.
- [ ] OK on the button opens Android's network settings.

## AC-16: the VPN indicator

- [ ] With no VPN, no VPN item shows.
- [ ] Connect Surfshark (from the drawer): within 2 s a shield and "VPN" appear between Wi-Fi and
  Bluetooth.
- [ ] Left and Right along the buttons skip it; it never takes focus.
- [ ] Disconnect Surfshark: the indicator is gone within 2 s.

## AC-17: the Bluetooth button

- [ ] With Bluetooth off: the off icon. On with nothing connected: the on icon. With devices
  connected (the Xiaomi box's own remote counts): the icon with their number.
- [ ] OK on the button opens Android's Bluetooth settings.

## AC-18: the Speed test button

- [ ] OK on Speed test opens Speedtest (`com.rma.netpulsetv`).
- [ ] Push `{"speedtestApp": "com.example.missing"}` and press Home: the Speed test button is gone.
  Push `{"speedtestApp": "com.rma.netpulsetv"}` to bring it back.

## AC-19: the Settings button

- [ ] It always shows a cog and "Settings", focused or not.
- [ ] OK opens Android's settings.
- [ ] Hold OK opens KevBox Home's "Customize" page.

## AC-20: button text and focus

- [ ] From the first app in the column, press Up: focus lands on the first button (Wi-Fi). Focused,
  it widens to its text: "Wi-Fi · strong" (level 3 or 4), "Wi-Fi · fair" (2), "Wi-Fi · weak"
  (0 or 1), "Ethernet" or "No network".
- [ ] Right to Bluetooth: "Bluetooth · off", "Bluetooth · on" or "Bluetooth · 2 connected" (with the
  real count). Right to Speed test: "Speed test". Unfocused, each goes back to its icon.
- [ ] Press Down from the buttons: focus returns to the app row it left.
- [ ] Press Right into the channel row, then Up from any card: focus lands on the first button.
  Down returns to that same card.

## AC-21: the drawer

- [ ] Open it with "More apps". It fills the screen, 5 columns wide.
- [ ] The first apps are Surfshark, Speedtest, Play Store, FX, Downloader and VLC, in that order
  (those installed). The rest follow alphabetically by name.
- [ ] KevBox Home, KevBox Live, KevBox TV and SmartTube are not in it.
- [ ] OK on an app opens it.
- [ ] Back closes the drawer, and focus is on "More apps".

## AC-22: All apps opens the drawer (service on)

KevBox Home's accessibility service must be on: Android hands All apps to the system launcher
before any app sees it.

- [ ] On Home, press All apps: the drawer opens. Back lands on Home with focus on "More apps".
- [ ] Inside another app (VLC, say), press All apps: the drawer opens over Home. Back lands on
  Home, focus on "More apps", not back in VLC.
- [ ] A remote without an All apps button: write "no All apps button" for that device and skip.

## AC-23: the hold-OK menu

- [ ] Hold OK on an app in the column: the menu is "App info", "Move", "Restart app", in that
  order, and nothing else. No "Uninstall".
- [ ] Hold OK on VLC in the drawer: "App info", "Move", "Restart app", "Uninstall".
- [ ] Hold OK on a system app in the drawer that was never updated: no "Uninstall". Find one with
  `adb shell dumpsys package <package> | grep pkgFlags`: it shows `SYSTEM` and no
  `UPDATED_SYSTEM_APP`.

## AC-24: App info

- [ ] "App info" opens Android's app details page for that app.

## AC-25: Move

- [ ] Hold OK on KevBox TV in the column, pick "Move": the spotlight says "Use the arrows to move
  it, then press OK". Up and Down carry it through the column. OK places it.
- [ ] Repeat, and place it with Back instead.
- [ ] In the drawer, hold OK on VLC, pick "Move": the heading says "Use the arrows to move it, then
  press OK". Left and Right move it by one place, Up and Down by one row. OK places it.
- [ ] Reboot the device (`adb reboot`). Both new orders are still there.
- [ ] Run `provision.sh` with the example file: the column and the drawer's first apps are back in
  the file's order.

## AC-26: Restart app

Run this section twice: with the TV's language set to English, then to French (Settings > Device
Preferences > Language > Français).

- [ ] Open KevBox TV, return with Home, hold OK on KevBox TV, pick "Restart app". The app details
  page shows for about a second, Force stop and its confirmation are pressed for you, then KevBox
  TV opens again from its start.
- [ ] Force-stop SmartTube first (`adb shell am force-stop org.smarttube.stable`), then "Restart
  app" on it: Force stop is greyed out, so it goes straight to opening SmartTube.
- [ ] Service off. KevBox Home turns its own accessibility service back on every time Home opens,
  as long as it holds `WRITE_SECURE_SETTINGS`, so take that grant away first:

  ```sh
  adb shell pm revoke tv.kevbox.home android.permission.WRITE_SECURE_SETTINGS
  ```

  Then switch KevBox Home's accessibility service off in Android's accessibility settings, press
  Home, hold OK on KevBox TV and pick "Restart app": the details page stays open and the toast
  reads "Press Force stop, then reopen the app".
- [ ] Switch the service back on in Android's accessibility settings, then give the grant back
  before the AC-27 to AC-30 checks:

  ```sh
  kevbox/provision/provision.sh kevbox/provision/kevbox-home.example.json
  ```

## AC-27: the key filter

- [ ] Press Netflix: KevBox TV opens, and `adb logcat -d -s KevBoxHome` has a line like
  `key keycode=193 scancode=416 -> tv.kevbox`.
- [ ] Hold Volume up inside an app: the volume keeps ramping while held.
- [ ] Hold Netflix for two seconds: KevBox TV opens once, on release.

## AC-28: the default map

- [ ] Netflix opens KevBox TV.
- [ ] Prime Video opens KevBox Live.
- [ ] YouTube opens SmartTube.
- [ ] Home, from inside an app, shows KevBox Home.
- [ ] All apps opens the drawer (AC-22).
- [ ] Note which buttons this remote has, and the keycode and scancode the log line printed for
  each, per device.

## AC-29: speed and missing targets

- [ ] From inside VLC, each app button opens its app within half a second of letting go, with
  Google's launcher still installed (do not disable it).
- [ ] Push `{"keys": [{"keycode": 193, "scancode": 416, "package": "com.example.missing"}]}` and
  press Home. Netflix now does what it does without KevBox Home (on the Xiaomi box, the Netflix
  app or its install page). Run `provision.sh` with the example file to restore the map.

## AC-30: Home

- [ ] From inside KevBox TV, VLC and Play Store, press Home: KevBox Home shows each time, and the
  stock launcher never comes to the front, not even for a flash.
- [ ] Find the stock launcher's package with
  `adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`
  (Google TV's is `com.google.android.apps.tv.launcherx`), then open it directly:

  ```sh
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -p com.google.android.apps.tv.launcherx
  ```

  KevBox Home opens over it.

## AC-33: kept features and Customize

- [ ] Weather shows top right (temperature and sky), and the wallpaper and the screen-time chart
  work as in Couch.
- [ ] Hold OK on Settings. "Customize" lists exactly: under "Permissions", "HOME button",
  "Notification access", "Usage access" and "TV listings"; under "Appearance", "Accent colour";
  under "Wallpaper", "Source", "Subreddit", "Change image" and "Dim"; under "Home", "App
  channels"; under "Clock and weather", "24-hour clock", "Weather" and "Units"; under "System",
  "Check for updates" and "About".
- [ ] It has no Layout, Card size, Names under cards, Descriptions, Date, Rows, Inputs, Show or hide
  apps, Rearrange apps, City, Ambient mode, Other TVs, Android settings, Godmode or pre-release row.
- [ ] Change Accent colour, Source and Units, return with Back: Home shows each change.
- [ ] Press OK seven times on About: nothing opens.

## AC-37: Uninstall

- [ ] Hold OK on VLC in the drawer, pick "Uninstall": Android's own uninstall question appears.
  Pick Cancel: VLC is still installed.
- [ ] Again, and confirm: within 2 s VLC leaves the drawer, the drawer stays open, and Home needs no
  restart. Reinstall VLC from Play Store afterwards.
- [ ] Hold OK on a column app: no "Uninstall" (AC-23).

## AC-38: English whatever the language

Set the TV's language to French (Settings > Device Preferences > Language > Français), then:

- [ ] Home reads "More apps", the buttons "Wi-Fi · strong" (or their state) and "Settings", the
  status lines "Opened …" with English weekdays ("Opened Wednesday at 22:37", never "mercredi").
- [ ] The screen-time chart's day letters are English (M, T, W and so on), and the bottom line's numbers
  use a decimal point with English units ("storage, 12 GB free", never "Go").
- [ ] Customize, the hold-OK menu and the drawer are all in English.
- [ ] Set the language back to English when done.
