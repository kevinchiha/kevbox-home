# Checking KevBox Home on a TV

The checks Kevin runs by hand on the TCL test TV and on his own Xiaomi TV Box S, one section per
acceptance criterion of the 0.1-kevbox-home spec. Tick each line on both devices unless it names
one. Run the commands from the repo root on a computer with `adb` connected to the device; with
both devices connected, prefix each command with `ANDROID_SERIAL=<tv ip>:5555`.

AC-10 to AC-13 check the tile home from the tile-home plan
(`docs/superpowers/quick/2026-10-30-tile-home.md`), which replaced the spec's left column of apps.

Presses are the remote's buttons: OK, hold OK (keep it down for about a second), Back, Home, Up,
Down, Left, Right, Netflix, Prime Video, YouTube, All apps. Text in quotes is exactly what the
screen should show.

Home's layout, as the checks below name it: the app tiles run along the bottom (icons only, More
apps last), two rows of cards sit above them on the right, each under a small label, and the text
block on the left describes the app or card in focus. Up from More apps goes straight to the top
buttons; Up from an app tile goes through that app's rows first.

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
- [ ] The Xiaomi box is Kevin's own box, never a member's. Run these checks on test devices only.

[Provisioning a box](provisioning.md) explains the file and every line the script can print.

## AC-1: identity

- [ ] `adb shell pm list packages tv.kevbox.home` prints `package:tv.kevbox.home`.
- [ ] `adb shell dumpsys package tv.kevbox.home | grep -E 'versionCode|versionName'` shows
  `versionName=0.9.0.0` and `versionCode=90000` for an untagged build. A release build tagged
  `v0.9.0.N` shows `0.9.0.N` and 90000 + N, so `v0.9.0.1` gives `90001`.
- [ ] On the TV, Settings > Apps lists the app as "KevBox Home".
- [ ] Android's accessibility settings list "KevBox Home", switched
  on by the script.
- [ ] Nowhere in KevBox Home's own screens does the word "Couch" appear, except in the licence line
  under Settings > About, which names Couch on purpose (GPL v3 asks for it).
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

## AC-10: the app tiles

- [ ] Home has no list of app names on the left. Along the bottom sit the tiles of KevBox Live,
  KevBox TV and SmartTube, in that order, then More apps (a mark of four small squares). Each app
  tile shows the app's icon alone, with no name on or under it. No other app has a tile.
- [ ] Focus is on the KevBox Live tile when Home first opens: it is a little larger than the others
  and ringed.
- [ ] Press Right twice, then press Home: focus is back on the KevBox Live tile.
- [ ] Left on the KevBox Live tile does nothing. Right from the SmartTube tile lands on More apps;
  Right on More apps does nothing. Down on any tile does nothing.
- [ ] OK on the KevBox TV tile opens KevBox TV. Return with Home.
- [ ] Open SmartTube for a while, return with Home: the order has not changed.
- [ ] Push `{"hidden": ["org.smarttube.stable"]}` as in AC-6 and press Home: SmartTube's tile is
  gone, and SmartTube is not in the drawer (All apps) either. Push `{"hidden": []}` to bring it
  back.
- [ ] Push `{"apps": []}` and press Home: More apps is the only tile and has focus. Run
  `provision.sh` with the example file again to restore it.

## AC-11: two rows per app

- [ ] Focus the KevBox Live tile: above the tiles, on the right, two rows of cards show. The upper
  row's label is KevBox Live's recent channels row as it names it (its name holds "récent" or
  "recent", for example "Chaînes récentes"), the lower row's its favourites ("Favoris" or
  similar). Each label is small grey text above its row.
- [ ] Right to KevBox TV: one row, labelled "Continue Watching", and nothing in the lower place.
  Once KevBox TV publishes its Library row, a second row labelled "Library" (or "Bibliothèque")
  shows under it.
- [ ] Right to SmartTube: the upper row's label holds "Recommended", the lower row's "History".
- [ ] Cards show their art only, with no title under them.
- [ ] Hold Right from KevBox Live to More apps and back with Left: each tile swaps the text block,
  both labels and both rows to its own app. No card of one app ever shows under another app's
  name. While you keep moving the rows stay empty; they fill a moment after you stop.
- [ ] Focus the KevBox Live tile, press Up: focus is on the first card of the lower row. Up again:
  the first card of the upper row. Up again: Wi-Fi.
- [ ] Down from Wi-Fi: back on that same upper card. Down: the lower row. Down: the KevBox Live
  tile.
- [ ] In the lower row press Right twice, then Up and Down: focus is back on the third card.
- [ ] Focus the KevBox TV tile (one row), press Up: its first card. Up again: Wi-Fi. Down twice:
  the card, then the KevBox TV tile.
- [ ] Focus the KevBox Live tile, then press Right and Up straight after, without waiting for the
  rows to fill: focus is on the first card of KevBox TV's row, which fills at once.
- [ ] From a card in either row, press Back: focus is on the tile of the app the rows belong to.
  Go up to Wi-Fi and press Back: the same.
- [ ] OK on a card of KevBox TV's Continue Watching row opens that program in KevBox TV. Return
  with Home.
- [ ] Focus More apps, press Up, Right to Settings, and hold OK. Open "App channels" and switch
  off SmartTube's History channel. Back on Home, SmartTube shows its Recommended row alone, no
  other channel in History's place. Switch History back on.
- [ ] In "App channels", switch off every SmartTube channel. Back on Home, focus the SmartTube
  tile: the text block shows "SmartTube" alone, with no rows and no labels. Up goes to Wi-Fi, and
  Down from Wi-Fi returns to the SmartTube tile. Switch the channels back on.

## AC-12: More apps

- [ ] Focus More apps: the text block reads "More apps", no rows and no labels show, and the
  wallpaper is behind the screen.
- [ ] OK on More apps opens the drawer. Back closes it, and focus is on More apps.
- [ ] Hold OK on More apps: no menu opens.
- [ ] From More apps, press Up: focus is on Wi-Fi. Down: back on More apps. Up again, then Back:
  focus is on More apps.

## AC-13: the text block and the backdrop

- [ ] Focus the KevBox TV tile with an episode part watched. The text block on the left shows,
  top to bottom: "KevBox TV" alone on a small line in the accent colour (nothing after it, no
  ", Continue Watching"), then the first card's title, its description and a progress bar.
- [ ] Press Up into the row and move along it: the title, description and progress follow the
  focused card, and the small line still reads "KevBox TV".
- [ ] With a tile focused, the whole screen behind shows the art of its upper row's first card.
  Move along a row: the art follows the focused card.
- [ ] Focus More apps, or an app with no rows: the backdrop shows the wallpaper.
- [ ] Home shows no week's screen-time chart and no status line under or beside any app ("Opened
  …", time left and the like). The line of TV readings stays along the very bottom.
- [ ] Focus More apps, press Up to the buttons, Right to Settings, and hold OK. Press Down to
  "Dim", OK, and pick Light, then Back to Home. Repeat with Medium, then Strong: the backdrop gets
  darker each step.

## AC-14: the top right

- [ ] Top right shows, in order: Wi-Fi, VPN (only while a VPN is on), Speed test, Settings, then
  the clock and the weather. No Bluetooth button by default.
- [ ] Push `{"buttons": ["bluetooth", "wifi"]}` and press Home: the row is Bluetooth, Wi-Fi, then
  Settings (always added). Push `{"buttons": []}`: the default order is back.
- [ ] While an update waits (a newer `version.json` on tv.kevbox.dev), `Update <versionName>` shows
  left of the buttons.

## AC-15: the Wi-Fi button

- [ ] On Wi-Fi: the icon shows the signal level, with a green dot while the internet works.
- [ ] On Ethernet (the TCL TV when plugged in): the icon is the Ethernet one, with the dot.
- [ ] Unplug the network, or switch Wi-Fi off in Android's settings: the no-network icon, no dot.
- [ ] Focus More apps and press Up: focus is on Wi-Fi. OK opens Android's network settings.

## AC-16: the VPN indicator

- [ ] With no VPN, no VPN item shows.
- [ ] Connect Surfshark (from the drawer): within 2 s a shield and "VPN" appear between Wi-Fi and
  Speed test.
- [ ] Left and Right along the buttons skip it; it never takes focus.
- [ ] Disconnect Surfshark: the indicator is gone within 2 s.

## AC-17: the Bluetooth button

Off by default. Push `{"buttons": ["wifi", "bluetooth", "speedtest"]}` first, and
`{"buttons": []}` after.

- [ ] With Bluetooth off: the off icon. On with nothing connected: the on icon. With devices
  connected (the Xiaomi box's own remote counts): the icon with their number.
- [ ] Focus More apps, press Up, then Right once: focus is on Bluetooth (the VPN indicator never
  takes focus). OK opens Android's Bluetooth settings.

## AC-18: the Speed test button

- [ ] Focus More apps, press Up, then Right once: focus is on Speed test. OK opens Speedtest
  (`com.rma.netpulsetv`).
- [ ] Push `{"speedtestApp": "com.example.missing"}` and press Home: the Speed test button is gone.
  Push `{"speedtestApp": "com.rma.netpulsetv"}` to bring it back.

## AC-19: the Settings button

- [ ] It always shows a cog and "Settings", focused or not.
- [ ] OK opens Android's settings.
- [ ] Hold OK opens KevBox Home's "Customize" page.

## AC-20: button text and focus

- [ ] Focus More apps and press Up: focus lands on the first button (Wi-Fi). Focused, it widens
  to its text: "Wi-Fi · strong" (level 3 or 4), "Wi-Fi · fair" (2), "Wi-Fi · weak" (0 or 1),
  "Ethernet" or "No network".
- [ ] Right to Speed test: "Speed test". With Bluetooth pushed (AC-17): "Bluetooth · off",
  "Bluetooth · on" or "Bluetooth · 2 connected" (with the real count). Unfocused, each goes back to its icon.
- [ ] Press Down from the buttons: focus returns to More apps, the tile it left.
- [ ] Focus the KevBox Live tile and press Up twice, into its upper row. Press Right, then Up:
  focus lands on the first button. Down returns to that same card.

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

- [ ] Hold OK on an app tile: the menu is "App info", "Move", "Restart app", in that order, and
  nothing else. No "Uninstall".
- [ ] Hold OK on VLC in the drawer: "App info", "Move", "Restart app", "Uninstall".
- [ ] Hold OK on a system app in the drawer that was never updated: no "Uninstall". Find one with
  `adb shell dumpsys package <package> | grep pkgFlags`: it shows `SYSTEM` and no
  `UPDATED_SYSTEM_APP`.

## AC-24: App info

- [ ] In the drawer, hold OK on VLC. "App info" is the menu's first item and has focus; press OK:
  Android's app details page for VLC opens. Back, then do the same on KevBox TV's tile.

## AC-25: Move

- [ ] Hold OK on KevBox TV's tile, pick "Move": the KevBox TV tile keeps focus, and the text block
  shows "KevBox TV" with "Use Left and Right to move it, then press OK" under it.
- [ ] Press Right: the tile swaps places with SmartTube. Right again does nothing; the tile never
  passes More apps. Up and Down do nothing either. Press Left twice: KevBox TV is first. OK places
  it, and the text block shows KevBox TV's first card again.
- [ ] `adb shell cat /sdcard/Android/data/tv.kevbox.home/files/config.json`: its `apps` list starts
  with `tv.kevbox`, then `tv.kevbox.live`.
- [ ] Repeat, move it one place, and place it with Back instead: the new order stays.
- [ ] In the drawer, hold OK on VLC, pick "Move": the heading says "Use the arrows to move it, then
  press OK". Left and Right move it by one place, Up and Down by one row. OK places it.
- [ ] Reboot the device (`adb reboot`). Both new orders are still there.
- [ ] Run `provision.sh` with the example file: the tiles and the drawer's first apps are back in
  the file's order.

## AC-26: Restart app

Run this section twice: with the TV's language set to English, then to French. On Google TV the
language is in Settings > System > Language > Français; on older Android TV it is in Settings >
Device Preferences > Language > Français.

- [ ] Open KevBox TV, return with Home, hold OK on KevBox TV's tile, pick "Restart app". The app
  details page shows for about a second, Force stop and its confirmation are pressed for you, then
  KevBox TV opens again from its start.
- [ ] Force-stop SmartTube first (`adb shell am force-stop org.smarttube.stable`), then "Restart
  app" on it: Force stop is greyed out, so it goes straight to opening SmartTube.
- [ ] Service off. KevBox Home turns its own accessibility service back on every time Home opens,
  as long as it holds `WRITE_SECURE_SETTINGS`, so take that grant away first:

  ```sh
  adb shell pm revoke tv.kevbox.home android.permission.WRITE_SECURE_SETTINGS
  ```

  Then switch KevBox Home's accessibility service off in Android's accessibility settings, press
  Home, hold OK on KevBox TV's tile and pick "Restart app": the details page stays open and the
  toast reads "Press Force stop, then reopen the app".
- [ ] Switch the service back on in Android's accessibility settings, then give the grant back
  before the AC-27 to AC-30 checks:

  ```sh
  kevbox/provision/provision.sh kevbox/provision/kevbox-home.example.json
  ```

## AC-27: the key filter

- [ ] Press Netflix: KevBox TV opens, and `adb logcat -d -s KevBoxHome` has a line like
  `key keycode=193 scancode=416 -> tv.kevbox`. That line repeats the numbers of the key map entry
  that matched, not the numbers the remote sent.
- [ ] Hold Volume up inside an app: the volume keeps ramping while held.
- [ ] Hold Netflix for two seconds: KevBox TV opens once, on release.

## AC-28: the default map

- [ ] Netflix opens KevBox TV.
- [ ] Prime Video opens KevBox Live.
- [ ] YouTube opens SmartTube.
- [ ] Home, from inside an app, shows KevBox Home.
- [ ] All apps opens the drawer (AC-22).
- [ ] Note which buttons this remote has, per device. The log line only repeats the key map's
  numbers, so to see what the remote itself sends, run `adb shell getevent -l`, press each button
  and note the code it prints (Ctrl+C to stop).

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

- [ ] Weather shows top right (temperature and sky), and the wallpaper works as in Couch. The
  screen-time chart is gone on purpose (AC-13).
- [ ] Focus More apps, press Up, Right to Settings, and hold OK. "Customize" lists exactly: under
  "Permissions", "HOME button", "Notification access", "Usage access" and "TV listings"; under
  "Appearance", "Accent colour"; under "Wallpaper", "Source", "Subreddit", "Change image" and
  "Dim"; under "Home", "App channels"; under "Clock and weather", "24-hour clock", "Weather" and
  "Units"; under "System", "Check for updates" and "About".
- [ ] It has no Layout, Card size, Names under cards, Descriptions, Date, Rows, Inputs, Show or hide
  apps, Rearrange apps, City, Ambient mode, Other TVs, Android settings, Godmode or pre-release row.
- [ ] Change Accent colour, Source and Units, return with Back: Home shows each change.
- [ ] Switch "24-hour clock" off, return with Back: the clock top left shows the 12-hour time.
  Switch it back on.
- [ ] Press OK seven times on About: nothing opens.

## AC-37: Uninstall

- [ ] Hold OK on VLC in the drawer, pick "Uninstall": Android's own uninstall question appears.
  Pick Cancel: VLC is still installed.
- [ ] Again, and confirm: within 2 s VLC leaves the drawer, the drawer stays open, and Home needs no
  restart. Reinstall VLC from Play Store afterwards.
- [ ] Hold OK on an app tile: no "Uninstall" (AC-23).

## AC-38: English whatever the language

Set the TV's language to French (Google TV: Settings > System > Language > Français; older
Android TV: Settings > Device Preferences > Language > Français), then:

- [ ] With More apps focused, the text block reads "More apps". The buttons read "Wi-Fi · strong"
  (or their state) and "Settings".
- [ ] Row labels show each channel's name as its app publishes it, so KevBox Live's may stay in
  French; that is the app's text, not KevBox Home's.
- [ ] The bottom line's numbers use a decimal point with English units ("storage, 12 GB free",
  never "Go").
- [ ] Customize, the hold-OK menu and the drawer are all in English.
- [ ] Set the language back to English when done.
