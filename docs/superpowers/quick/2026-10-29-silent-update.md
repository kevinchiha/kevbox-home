# Silent self-update

## Goal
On Android 12 and later, KevBox Home installs a new release by itself, with no screen, as soon as
the release allows it and nothing is playing; a release script exists to publish it, but is never
run here.

## Scope
`update/` (Release, UpdateCheck, Installer, InstallResult), `data/Fetcher.kt`,
`kevbox/update/` (VersionJson and new Silent* files), `HomeAccessibilityService.kt`, the three
activities, `settings/MainPage.kt`, `home/HomeScreen.kt`, the manifest, `app/build.gradle.kts`
(debug-only test feed), `kevbox/provision/`, new `release.sh` and `kevbox/release/`,
UPSTREAM-SYNC.md, tests.

**Gate:** `export ANDROID_HOME=$HOME/Android/Sdk && ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease && test $(stat -c %s app/build/outputs/apk/release/app-release.apk) -le 1048576 && bash kevbox/provision/provision-test.sh && python3 -m unittest discover -s kevbox/release -p 'test_*.py'`
(the last command has nothing to find until Task 4; at baseline it is logged, not fixed)

## Acceptance criteria
- **AC-1** version.json's `silent` block is used only when `after` is a positive JSON integer and
  `earlyUsers` is missing, null or an array of strings; anything else means no silent install.
  The stored release (`update:latest`) keeps the block through `Release.serialize`/`deserialize`.
- **AC-2** "Now" for every silent rule is the update server's HTTP `Date` header from the same
  response as version.json; the box clock only when that header is missing or unreadable.
  `after` is stored moved onto the box clock (`after + boxNow - serverNow`), so later checks need
  no network.
- **AC-3** Silent install is allowed only when: SDK ≥ 31, the release has a silent block, now ≥
  `after`, `canRequestPackageInstalls()` is true, that versionCode is not marked refused, and it has
  had fewer than 3 attempts. A new versionCode starts with 0 attempts and not refused.
- **AC-4** "Not in use" (Kevin, 2026-10-29: also for out of sight): nothing is playing
  (`AudioManager.isMusicActive()` false; an exception counts as playing) AND either every KevBox
  Home activity has been out of sight for 15 min, or one is on screen and no remote key was pressed
  for 3 h. Unknown visibility counts as on screen. Checked before the download and again right
  before the commit.
- **AC-5** Before installing, the downloaded APK must match version.json's SHA-256, carry this
  app's package name, a versionCode above the installed one, and the installed app's signing
  certificate; otherwise it is deleted, nothing is installed, and the run counts as a failed attempt.
- **AC-6** One install at a time across silent and member-started paths (`Installer.running`). A
  silent attempt is counted before `session.commit`. The manifest declares
  `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; every session on SDK ≥ 31 asks for
  `USER_ACTION_NOT_REQUIRED`, member-started ones included (Kevin, 2026-10-29).
- **AC-7** Silent results: SUCCESS needs nothing; `STATUS_PENDING_USER_ACTION` shows no screen,
  abandons the session and marks that versionCode refused; any other status is logged (the attempt
  is already counted). Member-started results behave as today.
- **AC-8** A silent install sets the "reopen after update" mark only when a KevBox Home activity is
  on screen at commit, so it never pulls someone out of another app. Member-started installs keep
  today's reopen.
- **AC-9** JobScheduler runs the silent check every 6 h with a network (persisted, not re-created
  when already scheduled) and once 15 min after the last KevBox Home activity stops (a newer stop
  replaces the pending one). Only one run at a time.
- **AC-10** On SDK ≥ 31 the Update chip stays hidden while silent install can still handle that
  version. It shows when the version is refused, has used 3 attempts, has no silent block, lacks
  the install permission, or 72 h have passed since `after`. Below SDK 31 the chip behaves as today.
- **AC-11** Settings › About checks tv.kevbox.dev now (not the stored daily result) and offers the
  install whatever AC-10 says.
- **AC-12** Every silent decision writes one log line under tag `KevBoxSilentUpdate`: trigger,
  versionCode, outcome. No URLs with tokens, no account data.
- **AC-13** `kevbox/provision/provision.sh` grants KevBox Home `REQUEST_INSTALL_PACKAGES` through
  appops; `provision-test.sh` covers it.
- **AC-14** `release.sh <A.B.C.K> [notes]` (never run in this change): refuses before anything
  changes on a dirty tree, a bad version, a version not above the published one, any `--` option
  other than `--no-silent` as the first argument. It tags `v<A.B.C.K>` locally (never pushed),
  builds with the KevBox TV key (`~/secrets/kevbox-keys/release.env`), checks versionName and code,
  the certificate SHA-256 against the KevBox TV key's, and the 1 MiB limit, uploads to persovps,
  takes the SHA-256 on the host, and writes `home/version.json` with
  `"silent": {"after": <publish time ms>, "earlyUsers": []}` (no block with `--no-silent`).
- **AC-15** Emulator (`kevbox_tv`, always `adb -s emulator-5554`, never uninstall anything): a
  test build offered a newer build with a past `after` updates itself with no installer screen
  after Home leaves the screen for 15 min with nothing playing; offered one with no silent block, it
  stays put and the Update chip shows.

## Non-goals
- No 24 h wait and no early-user list: Home has no sign-in (Kevin's brief). The parser still
  accepts `earlyUsers` so the shape matches TV and Live.
- No release, upload or tag push, and nothing on a member's box: AGENTS.md "Licence first".
- No `.dm` compile profile: Home ships only classes.dex.
- kevbox-setup's `setup.example.json` (another repo) is not edited; noted for Kevin.
- No WorkManager or other AndroidX: Home has none, so JobScheduler and SharedPreferences.

## Decisions
- Wait for nothing playing even when Home is out of sight, over TV's "out of sight 15 min" alone,
  because a launcher is out of sight exactly while someone watches (Kevin's call).
- Member-started installs go silent too, over forcing the confirm screen back, because the member
  already pressed Update (Kevin's call).
- Store `after` on the box clock, as KevBox Live does, over re-reading the server for the 72 h rule.
- Count an attempt before commit, as KevBox TV does (Kevin's rule 6), over Live's count-after.
- Check package, versionCode and signer of the download, as KevBox Live does.
- Freeze during the install: Android shows a black screen for about 0.5-3 s if Home is pressed then
  (AOSP: frozen packages are not skipped by home resolution). Accepted: the APK is 385 KB with no
  dexopt profile, installs run only when nothing plays, and AC-8 keeps the reopen mark for when
  Home was on screen.
- Sign with the KevBox TV key (`release.env`, `NUVIO_RELEASE_*`), as KevBox Live does, over the
  KevBox Home key made on 2026-10-29 (`kevbox-home.env`), because Kevin asked for "the same way as
  KevBox Live and KevBox TV". No box runs a Home release yet, so nothing has to switch keys.
- The test feed is a debug-only Gradle property (`-Pkevbox.updateFeed=<url>`, plus
  `-Pkevbox.versionCode=<n>`), over a runtime switch, so a release build can only read
  tv.kevbox.dev.
- release.sh writes version.json through a small Python script with unit tests
  (`kevbox/release/release_manifest.py`), copying KevBox TV's `scripts/release_manifest.py`.

## Tasks

### Task 1: Silent rules and the release data they need
**Delivers:** AC-1, AC-2, AC-3, AC-10 (rule only)
**Files:** `kevbox/update/VersionJson.kt`, `update/Release.kt`, `data/Fetcher.kt`,
`update/UpdateCheck.kt`, new `kevbox/update/SilentRules.kt`, new `kevbox/update/SilentState.kt`,
UPSTREAM-SYNC.md (rows for every upstream file touched, in the same commit), tests under
`app/src/test/java/cloud/samlo/couch/kevbox/`
**Interfaces:**
- `class SilentBlock(val afterMs: Long, val earlyUsers: List<String>)`; `Release` gains
  `val silent: SilentBlock?` and `val silentAfterBoxMs: Long?` (both default null).
- `Fetcher.fetch(url: String): Fetched?` with `class Fetched(val text: String, val dateMs: Long?)`;
  `text(url)` stays.
- `UpdateCheck.refresh(config, force: Boolean = false)`: `force` skips the once-a-day limit
  (the 6 h job and About pass true; Home's onResume keeps the default).
- `object SilentRules` with `fun serverNow(dateMs: Long?, boxNowMs: Long): Long`,
  `fun eligible(sdk: Int, release: Release, boxNowMs: Long, canInstall: Boolean, state: SilentState.Snapshot): SilentReason`,
  `fun quiet(playing: Boolean, onScreen: Boolean?, sinceHiddenMs: Long?, sinceKeyMs: Long?): SilentReason`,
  `fun holdsPrompt(sdk: Int, release: Release, boxNowMs: Long, canInstall: Boolean, state: SilentState.Snapshot): Boolean`;
  `enum class SilentReason` (ELIGIBLE, QUIET, OLD_ANDROID, NO_BLOCK, NOT_YET, NO_PERMISSION,
  REFUSED, OUT_OF_ATTEMPTS, PLAYING, IN_USE).
- `object SilentState` (SharedPreferences through `Prefs.of`) with
  `fun snapshot(context, versionCode: Long): Snapshot` (`class Snapshot(val attempts: Int, val refused: Boolean)`),
  `fun recordAttempt(context, versionCode: Long)`, `fun markRefused(context, versionCode: Long)`.
**Does:**
- VersionJson reads the `silent` block by AC-1's rule; Release round-trips it.
- `UpdateCheck.refresh` uses `Fetcher.fetch` and stores `silentAfterBoxMs` per AC-2.
- `SilentRules` implements AC-3, AC-4's arithmetic (15 min, 3 h, unknown = on screen) and AC-10's
  hold (72 h after `after`).
**Tests:** `SilentBlockTest` (good and bad blocks, round trip); `SilentRulesTest` (each reason,
the 15 min / 3 h / 72 h edges, server vs box clock).
**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests 'cloud.samlo.couch.kevbox.*' :app:compileDebugKotlin`

### Task 2: The silent install path
**Delivers:** AC-5, AC-6, AC-7, AC-8
**Files:** `update/Installer.kt`, `update/InstallResult.kt`, `AndroidManifest.xml`,
`HomeAccessibilityService.kt` (reopen mark only), new `kevbox/update/SilentInstall.kt`,
UPSTREAM-SYNC.md (rows for every upstream file touched, in the same commit), tests
**Interfaces:** `object SilentInstall` with
`fun run(context: Context, trigger: String, quiet: () -> SilentReason, homeOnScreen: () -> Boolean): SilentReason`
(reads the stored release, applies `SilentRules.eligible`, asks `quiet()`, downloads, checks,
re-asks `quiet()`, counts the attempt, sets the reopen mark only if `homeOnScreen()`, commits); a pure `fun checkApk(expectedPkg: String, installedCode: Long, installedCerts: Set<String>, pkg: String?, code: Long?, certs: Set<String>?): Boolean`.
**Does:**
- Shares Installer's download, SHA-256 check and process-wide busy flag; adds AC-5's package,
  versionCode and certificate check for silent runs.
- Silent sessions carry an extra marking them silent and their versionCode; InstallResult handles
  them by AC-7 and leaves member-started handling as today.
- The manifest gets `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; every session on SDK ≥ 31 asks for
  `USER_ACTION_NOT_REQUIRED`.
- The reopen mark follows AC-8 through `homeOnScreen()`; Task 3 passes `Visibility.onScreen(context) == true`.
**Tests:** `SilentInstallTest` on `checkApk` and on the result mapping (status → action).
**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests 'cloud.samlo.couch.kevbox.*' :app:compileDebugKotlin`

### Task 3: When it runs, and what the member sees
**Delivers:** AC-4, AC-9, AC-10, AC-11, AC-12, AC-13
**Files:** new `kevbox/update/Visibility.kt`, new `kevbox/update/SilentJob.kt` (JobService),
`MainActivity.kt`, `settings/SettingsActivity.kt`, `kevbox/drawer/DrawerActivity.kt`,
`HomeAccessibilityService.kt` (key time), `home/HomeScreen.kt` (chip hold),
`settings/MainPage.kt` (check now), `AndroidManifest.xml`, `app/build.gradle.kts` (debug-only
`kevbox.updateFeed` / `kevbox.versionCode`), `kevbox/update/VersionJson.kt` (feed URL and trusted
host from the debug property), `kevbox/provision/provision.sh`, `kevbox/provision/provision-test.sh`,
UPSTREAM-SYNC.md, tests
**Design skills:** `~/.agents/skills/ui-ux-pro-max/SKILL.md` (only for the About row text; Couch's
own Options rows win)
**Interfaces:** `object Visibility` with `fun started(activity)`, `fun stopped(activity)`,
`fun keyPressed()`, `fun onScreen(context): Boolean?`, `fun sinceHiddenMs(context): Long?`,
`fun sinceKeyMs(): Long?`. The count of started activities and the key time live in memory; the
elapsedRealtime of the last start and last stop are also kept in SharedPreferences, so a job in a
fresh process knows Home has been hidden since the last stop (a stored time above now means a
reboot happened and counts as hidden since boot). `SilentRules.quiet` treats a null time as "just now", so `sinceKeyMs()` returns the time since the activity started when no key was pressed in this process, and `sinceHiddenMs(context)` returns the time since boot when no stop is stored. `SilentJob.schedule(context)` and
`SilentJob.afterLeaving(context)`.
**Does:**
- Each activity reports start/stop and key-downs to `Visibility`; the accessibility service
  reports every key-down too. Playing comes from `isMusicActive()`.
- `SilentJob` runs `UpdateCheck.refresh` then `SilentInstall.run` per AC-9, logging by AC-12.
- HomeScreen hides the chip while `SilentRules.holdsPrompt` is true (AC-10); About fetches now and
  installs (AC-11).
- provision.sh runs `appops set tv.kevbox.home REQUEST_INSTALL_PACKAGES allow` (package from its
  existing variable), and makes KevBox Home its own installer of record (`pm set-installer <pkg> <pkg>`,
  or the nearest command that works from adb shell), because Android only skips the confirm screen
  for the installer of record; a box installed with plain `adb install` would refuse the first silent update.
- `SilentInstall.run` returns COMMITTED when Android has the session; every other reason means nothing
  was installed (Task 2 added NO_UPDATE, BUSY, DOWNLOAD_FAILED, BAD_APK, FAILED, COMMITTED).
**Tests:** `VisibilityTest` (unknown, on screen, hidden for 15 min, key time); provision-test case
for the appops line.
**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest :app:compileDebugKotlin && bash kevbox/provision/provision-test.sh`

### Task 4: The release script and the emulator tools
**Delivers:** AC-14, AC-15 (tools; the run itself is the orchestrator's)
**Files:** new `release.sh`, new `kevbox/release/release_manifest.py`,
new `kevbox/release/test_release_manifest.py`, new `kevbox/release/updater_test_server.py`,
new `docs/kevbox/releasing.md`, AGENTS.md (one line pointing at it)
**Does:**
- release.sh follows AC-14, reading the KevBox TV key's variable names from release.env and passing
  them to Gradle as the `COUCH_*` names build.gradle.kts reads; it reads the expected certificate
  SHA-256 from the KevBox TV keystore with keytool or from a constant, as KevBox Live does.
- release_manifest.py renders version.json (`after` = publish time, `earlyUsers: []`) and checks
  arguments (`--no-silent` first only, other `--` refused, versionName not starting with `-`).
- updater_test_server.py serves a test APK and a version.json with or without a silent block,
  copied from KevBox TV's, for AC-15; releasing.md holds the emulator steps (15 min, not 5).
**Tests:** `test_release_manifest.py` (silent block, `--no-silent`, refused options, after = now).
**Verify:** `python3 -m unittest discover -s kevbox/release -p 'test_*.py' && bash -n release.sh`

## Build log
baseline: gradle, size (385083 B) and provision-test green; the python step fails (kevbox/release does not exist until Task 4)
Task 1: started at d084f1f
Task 1: landed (extra public helpers SilentRules.afterOnBox / onBoxClock, Release.withSilentAfterBox; SilentBlock is a data class in VersionJson.kt; eligible() does not compare versionCodes, callers use UpdateCheck.pending(); Task 3 Interfaces updated for null times)
Task 2: started at a269f03
Task 2: landed (SilentReason gained NO_UPDATE, BUSY, DOWNLOAD_FAILED, BAD_APK, FAILED, COMMITTED; extra helpers SilentResult, markSilent/isSilent/resultFor/onResult; HomeAccessibilityService untouched, reopenAfterUpdate already fits; installer-of-record finding moved into Task 3)
Task 3: started at ddca11f
Task 3: landed (no installer-of-record step: pm set-installer throws SecurityException from adb, and AOSP 12-14 skip the screen for a self-update; VersionJson.URL is now a val with RELEASE_URL; buildConfig on, debug-only KEVBOX_UPDATE_FEED; extra SilentJob.holdsPrompt; outside Files: strings.xml (kevbox_about_checking), docs/kevbox/provisioning.md; Installer.kt comments still claim installer-of-record, fixed in Task 4)
