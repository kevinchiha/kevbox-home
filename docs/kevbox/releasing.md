# Releasing KevBox Home

Couch still has no licence (samuelloranger/couch#17); on 2026-10-29 Kevin chose to release KevBox
Home anyway and accepted that risk. Tags never leave this computer: Couch's release workflow would
publish any pushed `v*` tag on GitHub. This page describes the release and the emulator check (AC-15
of `docs/superpowers/quick/2026-10-29-silent-update.md`) that proves the silent update works.

## What Kevin runs

```sh
./release.sh 0.8.1.1 "What changed, in one line"
./release.sh --no-silent 0.8.1.2 "A release every box asks about first"
```

The version is `A.B.C.K`: `A.B.C` is the Couch release this build is on (the newest `vX.Y.Z` tag
in `main`), `K` counts KevBox Home's releases on it from 1. The versionCode is
A·1,000,000 + B·10,000 + C·100 + K, so `0.8.1.1` is 80101. Notes are optional and default to
"KevBox Home <version>".

Run it from a clean `main` that is already pushed: the script never pushes anything.

## What the script checks first

Each check runs before anything changes. A failed one prints why and exits with status 1.

| Check | Why |
|---|---|
| `--no-silent` comes first if at all; no other `--option`; the version does not start with `-` | Silent install is the default, so a misplaced or misspelled flag would publish a release that installs by itself. |
| No uncommitted or untracked files | The release is exactly HEAD. |
| The version is `A.B.C.K`, A, B and C 0-99, K 1-99, no leading zeros | Each tag has one spelling, and K = 0 would match Couch's own `vA.B.C` tag. |
| The tag `vA.B.C.K` does not exist | A tag names one build. |
| HEAD is on `origin/main` (after a `git fetch`) | The release points at a pushed commit. |
| The versionCode is above the one `https://tv.kevbox.dev/home/version.json` publishes, and above the highest `v*` tag already in HEAD | Boxes only install a higher versionCode, and Gradle builds the highest tag in HEAD. A 404 means nothing is published yet: the first release. |
| `~/secrets/kevbox-keys/release.env` sources cleanly and sets `NUVIO_RELEASE_STORE_FILE` (an existing file), `NUVIO_RELEASE_STORE_PASSWORD`, `NUVIO_RELEASE_KEY_ALIAS`, `NUVIO_RELEASE_KEY_PASSWORD` | KevBox Home is signed with the KevBox TV key, as KevBox Live is. The file is read in subshells and never printed. |
| `apksigner` and `aapt2` under `$ANDROID_HOME/build-tools` (default `~/Android/Sdk`); `curl`, `ssh`, `scp`, `sha256sum` | The checks after the build need them. |
| `kevbox/provision/provision-test.sh` and `kevbox/release`'s Python tests pass | The parts of the gate Gradle does not run. |

## What it does next

1. Creates the local tag `vA.B.C.K` at HEAD. `app/build.gradle.kts` takes the version from it.
   The tag is never pushed: Couch's `.github/workflows/release.yml` would publish any pushed `v*`
   tag on GitHub. If anything fails before the upload, the script deletes the tag again. If
   something fails after it, the APK may already be on the host, so the script keeps the tag and
   says what to check: when `version.json` already names the version, the release is out and only
   `kevbox-home-latest.apk` needs repointing by hand (the script prints the command); when it
   does not, delete the tag (`git tag -d vA.B.C.K`) and rerun with the same version.
2. Runs the Gradle gate (`testReleaseUnitTest lintRelease assembleRelease`) with the KevBox TV key,
   passed to Gradle as `COUCH_KEYSTORE`, `COUCH_KEYSTORE_PASSWORD`, `COUCH_KEY_ALIAS` and
   `COUCH_KEY_PASSWORD`.
3. Checks the APK: package `tv.kevbox.home`, versionName and versionCode equal to the version's
   (`aapt2 dump badging`), the signing certificate's SHA-256 equal to the KevBox TV key's
   (`apksigner verify --print-certs`), and 1048576 bytes or less (AC-35).
4. Copies the APK to persovps, installs it as `kevbox-home-<version>.apk` in the web root, and
   takes its SHA-256 on the host. A SHA-256 that differs from the built APK's stops the script
   before version.json changes.
5. Writes `version.json` (rendered by `kevbox/release/release_manifest.py`) beside the old one and
   moves it into place, so a box never reads half a file:

   ```json
   {
     "versionCode": 80101,
     "versionName": "0.8.1.1",
     "url": "https://tv.kevbox.dev/home/kevbox-home-0.8.1.1.apk",
     "sha256": "<64 hex characters, from the host>",
     "notes": "What changed, in one line",
     "silent": {"after": 1793000000000, "earlyUsers": []}
   }
   ```

   `after` is the time version.json was written, in epoch milliseconds, so Android 12+ boxes may
   install the release from then on, once nobody is watching. Home has no sign-in, so there is no
   early-user list and no 24 h wait. With `--no-silent` the `silent` block is left out and every box
   shows the Update chip instead.
6. Points `kevbox-home-latest.apk` at the new APK, last, so the link never names a release that
   `version.json` does not offer yet.

Where it uploads, overridable through the environment:

| Variable | Default |
|---|---|
| `KEVBOX_SSH_ALIAS` | `persovps` |
| `KEVBOX_SSH_PORT` | `1788` |
| `KEVBOX_HOME_WEB_ROOT` | `/var/www/kevbox-tv/home` (served as `https://tv.kevbox.dev/home/`) |

The base URL is fixed at `https://tv.kevbox.dev/home`: boxes only trust APKs from
`https://tv.kevbox.dev`.

## Emulator check (AC-15)

Kevin or the orchestrator runs this on the local `kevbox_tv` emulator, never on a TV or a box. A
real TV may be connected over network adb, so every command names the emulator. Never uninstall
anything: each build installs over the last one.

```sh
E=emulator-5554
adb -s $E emu avd name                  # must print kevbox_tv; stop if it prints anything else
PORT=8765
FEED=http://10.0.2.2:$PORT/home/version.json
adb -s $E shell dumpsys package tv.kevbox.home.dev | grep versionCode
```

The test builds below use versionCodes 90000001 to 90000003. If the last command shows a higher
versionCode, pick three higher numbers instead.

### Build three test APKs

The debug build (`tv.kevbox.home.dev`) reads the feed given at build time, and all three must
carry the same one:

```sh
export ANDROID_HOME=$HOME/Android/Sdk
for n in 90000001 90000002 90000003; do
  ./gradlew --no-daemon :app:assembleDebug -Pkevbox.updateFeed=$FEED -Pkevbox.versionCode=$n
  cp app/build/outputs/apk/debug/app-debug.apk /tmp/kevbox-home-$n.apk
done
```

### Run 1: a silent block that is already open

```sh
adb -s $E install -r /tmp/kevbox-home-90000001.apk
adb -s $E shell appops set tv.kevbox.home.dev REQUEST_INSTALL_PACKAGES allow
python3 kevbox/release/updater_test_server.py --apk /tmp/kevbox-home-90000002.apk \
  --version-code 90000002 --after-hours -1 --port $PORT
```

The server prints the version.json it serves and logs each request. In a second terminal:

```sh
adb -s $E logcat -c
adb -s $E shell am start -n tv.kevbox.home.dev/cloud.samlo.couch.MainActivity
```

1. In Home, open Settings and make sure "Check for updates" is on, then open About. It asks the
   feed at once; the server logs `GET /home/version.json`. Back out to Home: the Update chip stays
   hidden, because the silent update holds it (AC-10).
2. Leave Home's screen. Pressing Home works if another launcher is the default. If KevBox Home dev
   is the default launcher (check with
   `adb -s $E shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`),
   open another full-screen app instead (for example `adb -s $E shell monkey -p tv.kevbox.live 1`).
   TV Settings does not count: it opens as a side panel, Home stays visible behind it and never
   stops. Check with `adb -s $E shell dumpsys jobscheduler | grep 'tv.kevbox.home.dev'`: a second
   job, `/1263011330`, appears once Home has really left the screen.
3. Wait at least 16 minutes. Play nothing and press nothing.
4. Check:

   ```sh
   adb -s $E shell dumpsys package tv.kevbox.home.dev | grep versionCode    # versionCode=90000002
   adb -s $E logcat -d -s KevBoxSilentUpdate
   adb -s $E shell dumpsys activity activities | grep -ci packageinstaller  # 0: no installer screen
   ```

   The log shows `trigger=left versionCode=90000002 outcome=COMMITTED`, and
   `trigger=result versionCode=90000002 outcome=INSTALLED (status 0 ...)` when the result arrived
   before Android stopped the old process. The server logs the APK download.

If no `trigger=left` line appears after 16 minutes, JobScheduler is running late. Push the job now
(it still checks the 15 minutes itself):

```sh
adb -s $E shell cmd jobscheduler run -f tv.kevbox.home.dev 1263011330
```

Stop the server with Ctrl-C.

### Run 2: no silent block

90000002 is installed after run 1.

```sh
python3 kevbox/release/updater_test_server.py --apk /tmp/kevbox-home-90000003.apk \
  --version-code 90000003 --no-silent --port $PORT
adb -s $E logcat -c
adb -s $E shell am start -n tv.kevbox.home.dev/cloud.samlo.couch.MainActivity
```

1. Open Settings › About and wait for `GET /home/version.json` in the server log. About offers
   test-90000003; do not press it. Back out to Home: the Update chip shows test-90000003.
2. Leave Home's screen as in run 1 and wait at least 16 minutes, nothing playing.
3. Check: `versionCode=90000002` still, and `adb -s $E logcat -d -s KevBoxSilentUpdate` shows
   `trigger=left versionCode=90000003 outcome=NO_BLOCK`. Nothing installed and no installer screen
   appeared.

Stop the server with Ctrl-C.

### Back to the normal debug build

Debug builds are debuggable, so `-d` installs the lower versionCode over the test build and keeps
its data:

```sh
./gradlew --no-daemon :app:assembleDebug
adb -s $E install -r -d app/build/outputs/apk/debug/app-debug.apk
```
