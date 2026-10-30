# Provisioning a box

KevBox Home takes its setup from one file, `kevbox-home.json`. You push it to the box, and the next
time Home starts or comes back to the front it copies the file's keys into its own `config.json`.
`kevbox/provision/provision.sh` does the push and the rest of a new box's setup in one run.

## Where the file goes

| Build | Folder |
|---|---|
| Release, `tv.kevbox.home` | `/sdcard/Android/data/tv.kevbox.home/files/` |
| Debug, `tv.kevbox.home.dev` | `/sdcard/Android/data/tv.kevbox.home.dev/files/` |

The file is `kevbox-home.json` in that folder. `config.json` sits beside it, and so does
`wallpaper.jpg` when the wallpaper is `custom`.

## What happens to the file

Home checks for `kevbox-home.json` on every load. It ignores `kevbox-home.json.tmp` and any other
`.tmp` file, since those are pushes still in flight.

- The text is a JSON object. Home applies it, saves `config.json`, then renames the file
  `kevbox-home.applied.json`, replacing an earlier one.
- The text is empty, unreadable, not JSON, or JSON that is not an object (an array, a bare string).
  Home leaves `config.json` exactly as it was and renames the file `kevbox-home.rejected.json`,
  replacing an earlier one.
- Saving `config.json` fails, or the saved file does not hold the new values (some TVs' storage
  refuses the rename that saving relies on). Home leaves `kevbox-home.json` in place and tries again
  on the next load.

After a rename the file is gone, so later loads leave `config.json` alone. A change a family member
makes on the box, such as moving an app from the hold-OK menu or changing a setting in Customize,
lasts until you push a new file.

Each outcome writes one log line under the tag `KevBoxHome`:

```
applied kevbox-home.json: apps,drawer,keys
rejected kevbox-home.json: <the JSON parser's message>
save failed kevbox-home.json: config.json kept the old apps
```

Read them with `adb logcat -s KevBoxHome`. The `applied` line lists the keys that took effect,
sorted. A key missing from that list was absent or had the wrong type.

## The keys

Every key is optional. A key in the file replaces the saved value. A missing key keeps the saved
value, and so does a key with the wrong type: Home skips that one key and still applies the others.
Home ignores unknown keys.

| Key | Type | Allowed values | Wrong type |
|---|---|---|---|
| `apps` | array of strings | Package names for the left column, in order. Blanks and repeats are dropped. | Not an array, or any element not a string: keeps the saved column. |
| `drawer` | array of strings | Package names shown first in the drawer, in order. Blanks and repeats are dropped. | Same as `apps`. |
| `hidden` | array of strings | Package names never shown in the left column or the drawer, even when `apps` lists them. | Same as `apps`. |
| `buttons` | array of strings | Top-right buttons in order, from `wifi`, `vpn`, `bluetooth`, `speedtest`, `settings`. Other values and repeats are dropped. An empty array means the default: `wifi`, `vpn`, `speedtest`, `settings` (no Bluetooth since 0.9.0.3; name `bluetooth` to show it). A list without `settings` gets it at the end, so Customize stays reachable. | Not an array, or any element not a string: keeps the saved buttons. |
| `speedtestApp` | string | The package the Speed test button opens. Default `com.rma.netpulsetv`. | Not a string, or blank: keeps the saved app. |
| `keys` | array of objects | The remote map, described below. An empty array turns every mapping off. | Not an array: keeps the saved map. |
| `wallpaper` | string | `daily` (Bing photo of the day), `reddit`, `gradient` or `custom`. | Any other value or type: keeps the saved wallpaper. |

### `keys` entries

Each entry maps one remote button:

```json
{"keycode": 193, "scancode": 416, "package": "tv.kevbox"}
{"keycode": 284, "scancode": 216, "action": "drawer"}
```

An entry needs a `keycode`, a `scancode` or both, as positive whole numbers. It also needs exactly
one of `package` (the app to open) or `action` (`home` shows KevBox Home, `drawer` opens the
drawer). Home skips any other entry on its own and keeps the rest of the array. A key press matches
the first entry with the same keycode. Home compares scancodes only when the press has keycode 0
(`KEYCODE_UNKNOWN`).

Until a file sets `keys`, the default map is the one in the example file below.

### `custom` wallpaper

`custom` shows `wallpaper.jpg` from the same folder as the provision file. Neither the script nor
Home puts it there, so push it by hand:

```sh
adb push wallpaper.jpg /sdcard/Android/data/tv.kevbox.home/files/wallpaper.jpg
```

Without that file, Home shows the daily photo.

## Full example

All seven keys:

```json
{
  "apps": ["tv.kevbox.live", "tv.kevbox", "org.smarttube.stable"],
  "drawer": ["com.surfshark.vpnclient.android", "com.rma.netpulsetv", "com.android.vending",
             "nextapp.fx", "com.esaba.downloader", "org.videolan.vlc"],
  "hidden": ["com.sony.dtv.promos"],
  "buttons": ["wifi", "vpn", "bluetooth", "speedtest", "settings"],
  "speedtestApp": "com.rma.netpulsetv",
  "keys": [
    {"keycode": 193, "scancode": 416, "package": "tv.kevbox"},
    {"keycode": 194, "scancode": 207, "package": "tv.kevbox.live"},
    {"keycode": 190, "scancode": 425, "package": "org.smarttube.stable"},
    {"keycode": 3, "scancode": 172, "action": "home"},
    {"keycode": 284, "scancode": 216, "action": "drawer"}
  ],
  "wallpaper": "daily"
}
```

`kevbox/provision/kevbox-home.example.json` is the file for the current boxes. It sets only `apps`,
`drawer` and `keys`, so a push keeps whatever buttons and wallpaper the box already shows.

## Running the script

```sh
kevbox/provision/provision.sh kevbox/provision/kevbox-home.example.json
```

Install KevBox Home on the box first; the script does not install anything. With one box connected
over adb, the script uses it. With more than one, name the box:

```sh
ANDROID_SERIAL=192.168.1.50:5555 kevbox/provision/provision.sh my-box.json
```

To set up the debug build instead of the release one, for example on a test TV that has both:

```sh
KEVBOX_HOME_PACKAGE=tv.kevbox.home.dev kevbox/provision/provision.sh my-box.json
```

The script runs these steps in order and stops at the first one that fails:

1. Pushes the file as `kevbox-home.json.tmp`, then moves it to `kevbox-home.json` with
   `adb shell mv`, so Home never reads a half-written file.
2. Reads the Android SDK level.
3. Grants `READ_TV_LISTINGS` and `WRITE_SECURE_SETTINGS`, plus `BLUETOOTH_CONNECT` on SDK 31
   (Android 12) and later.
4. Allows usage access (`appops set <package> GET_USAGE_STATS allow`), then "Install unknown apps"
   (`appops set <package> REQUEST_INSTALL_PACKAGES allow`), so KevBox Home can update itself with
   no confirm screen on Android 12 and later.
5. Adds KevBox Home's accessibility service to `enabled_accessibility_services`, keeping every
   service already there, and sets `accessibility_enabled` to 1.
6. Makes KevBox Home the Home app (`cmd package set-home-activity`).
7. Starts it as Home (`am start -a android.intent.action.MAIN -c android.intent.category.HOME -p
   <package>`), then sends the Home key. It never starts `MainActivity` by name with `-n`: that
   makes an ordinary launch, which puts a second KevBox Home next to the real one.
8. Checks up to five times, a second apart, that `dumpsys activity activities` shows KevBox Home
   as the resumed activity.

It prints `provision: done on <serial>` and exits 0 when every step passed.

The script does not check the JSON (Home rejects a bad file and logs why), does not wait for
`kevbox-home.applied.json`, and does not push `wallpaper.jpg`. To confirm the file applied, list the
folder or read the log:

```sh
adb shell ls /sdcard/Android/data/tv.kevbox.home/files/
adb logcat -s KevBoxHome
```

`kevbox/provision/provision-test.sh` runs the script against a fake `adb` and never touches a box.

## Failure lines

Each failure prints one red line on stderr. A usage error exits 2; every other failure exits 1.
The first five come before the script changes anything on the box.

| Line | Meaning |
|---|---|
| `provision: usage: provision.sh <kevbox-home.json>` | No file given, or the file cannot be read. |
| `provision: adb not found on PATH` | Install the Android platform tools or fix `PATH`. |
| `provision: no device: adb devices lists none in state "device"` | No box connected, or the box shows `unauthorized` or `offline`. Accept the prompt on the TV and retry. |
| `provision: more than one device: set ANDROID_SERIAL to one of them` | Pick the box with `ANDROID_SERIAL`. |
| `provision: ANDROID_SERIAL=<serial> is not a connected device` | That serial is not in `adb devices`, or not in state `device`. |
| `provision: push failed` | The push or the move failed. Check that the app is installed and has opened once, so its folder exists. |
| `provision: reading the SDK level failed` | `getprop ro.build.version.sdk` failed or did not return a number. |
| `provision: grant READ_TV_LISTINGS failed` | `pm grant` refused. The installed build may not declare the permission. |
| `provision: grant WRITE_SECURE_SETTINGS failed` | Same, for `WRITE_SECURE_SETTINGS`. |
| `provision: grant BLUETOOTH_CONNECT failed` | Same, for `BLUETOOTH_CONNECT` (SDK 31 and later only). |
| `provision: usage access failed` | `appops set` refused. |
| `provision: install permission failed` | `appops set` refused `REQUEST_INSTALL_PACKAGES`. |
| `provision: accessibility service failed` | Reading or writing the secure accessibility settings failed. |
| `provision: set Home failed` | `cmd package set-home-activity` failed or did not print `Success`. |
| `provision: start failed` | `am start` failed or printed `Error`. |
| `provision: HOME key failed` | `input keyevent KEYCODE_HOME` failed. |
| `provision: KevBox Home is not the resumed activity` | After five tries another app is still in front. Press Home on the remote and check which launcher opens. |
