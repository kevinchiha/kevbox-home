# Setup

Couch is sideloaded: it is not on the Play Store. You need ADB access to the TV once, for the
install and the permissions an app can't request from the remote.

## 1. Enable ADB on the TV

Settings → System → About → press OK on *Build* seven times to unlock developer options, then
Settings → System → Developer options → **Network debugging** (or USB debugging).

From a computer on the same network:

```sh
adb connect <tv-address>:5555   # accept the prompt on the TV the first time
```

## 2. Install

```sh
adb install couch-vX.Y.Z.apk
adb shell cmd package compile -m speed -f cloud.samlo.couch   # optional: full ahead-of-time compile
```

The compile step makes the very first launch as smooth as later ones.

## 3. Permissions

| Permission | What it unlocks | Command |
|---|---|---|
| TV listings | App channel rows (YouTube, Jellyfin…) | `adb shell pm grant cloud.samlo.couch android.permission.READ_TV_LISTINGS` |
| Notification access | The now-playing pill | `adb shell cmd notification allow_listener cloud.samlo.couch/cloud.samlo.couch.MediaListener` |
| Secure settings | Repairing the HOME takeover by itself | `adb shell pm grant cloud.samlo.couch android.permission.WRITE_SECURE_SETTINGS` |

Couch asks for TV listings itself on first launch; the other two are only possible over ADB.
Everything works without them, minus the feature in that row.

## 4. Make HOME open Couch

Most Android TV devices let you pick a default home app. Many TVs (Sony Bravia among them) don't:
HOME always opens the stock launcher. For those, Couch includes an accessibility service that
replaces the stock launcher as soon as it appears:

```sh
adb shell settings put secure enabled_accessibility_services \
  cloud.samlo.couch/cloud.samlo.couch.HomeAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

The service only receives window events from the stock launcher. It never sees or changes key
presses, so volume and every other button behave exactly as before. The trade-off is that the stock
launcher can show for a moment (up to a few seconds right after a HOME press) before Couch
replaces it. See [decisions](decisions.md#home-button) for why.

**Android drops accessibility services when their app is force-stopped** (some TVs do this to free
memory). With the secure-settings permission above, Couch turns its service back on whenever it
opens and at boot. Without it, re-run the two commands.

On Fire TV, use a home-redirect app pointed at Couch instead; the accessibility service isn't
needed.

## Updating

Install the new APK over the old one (`adb install -r`). Settings are kept: they live in the app's
storage and survive updates, not uninstalls. To keep them across an uninstall, copy
`/sdcard/Android/data/cloud.samlo.couch/files/` off the TV first.
