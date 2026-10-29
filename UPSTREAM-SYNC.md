# Upstream sync

This table lists every file that exists in Couch at the fork point and differs here, as printed by
`git diff --name-only --no-renames --diff-filter=MD "$(git merge-base upstream/main HEAD)"`, with
what this fork changed in it and why (AC ids are from
`docs/superpowers/specs/2026-10-28-0.1-kevbox-home-design.md`). Files only this fork has get no
row: the `kevbox/` source, test and script folders, the `ic_kb_*` drawables in
`app/src/main/res/drawable/`, `docs/kevbox/`, `docs/superpowers/`, `AGENTS.md` and this file.

## Changed

| Path | What changed | Why |
|---|---|---|
| `app/build.gradle.kts` | `applicationId` is `tv.kevbox.home`. The version is the highest `vX.Y.Z[.N]` tag from `git tag --merged HEAD` instead of `git describe`, with a fourth number, and `versionCode` = X·1,000,000 + Y·10,000 + Z·100 + N. Adds `androidResources.localeFilters += "en"`, and a `lint` block that disables `ExtraTranslation`, `MissingTranslation` and `MissingDefaultResource`. The debug comment says KevBox Home. | AC-1 (identity), AC-39 (four-part version), AC-38 (English only while `values-fr` stays untouched) |
| `app/src/main/AndroidManifest.xml` | Adds `BLUETOOTH` (`maxSdkVersion` 30), `BLUETOOTH_CONNECT`, `ACCESS_WIFI_STATE` and `REQUEST_DELETE_PACKAGES`. `.settings.IconPickerActivity` becomes `.kevbox.drawer.DrawerActivity` (single top, predictive back off). Removes `.godmode.SleepReceiver`. The `QUERY_ALL_PACKAGES` comment now names Restart app, the remote keys and the hold-OK menu instead of the debloat scan. | AC-17, AC-15, AC-37, AC-21, AC-4, AC-5 (the icon picker served HDMI inputs) |
| `app/src/main/java/cloud/samlo/couch/BootReceiver.kt` | Drops the `Sleep.forget` call and its Godmode import. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/HomeAccessibilityService.kt` | `onServiceConnected` builds a `kevbox.keys.KeyFilter` on `KeyActions`, and the new `onKeyEvent` passes every key to it. `onKeyEvent` then shows a `kevbox.keys.VolumeRepeat` every key the filter did not consume, plus every key-up, so a mapped volume key never ramps and a ramp always sees its up; it ramps a held Volume up or down through `AudioManager.adjustSuggestedStreamVolume` on a main-thread `Handler` (timing from `ViewConfiguration`) and never consumes the event; `onInterrupt` and `onDestroy` stop it. `openCouch` calls `KevBox.goHome`. New companion `restart(pkg)` runs `kevbox.restart.ForceStop` inside the service; `onDestroy` cancels a running one. `isStockHome` returns false for `com.android.tv.settings` and `com.android.settings`. `globalAction` is gone. The class KDoc describes the three jobs, and the comments say KevBox Home where they said Couch. | AC-27, AC-28 (key filter; a key-filtering service breaks Android's held-key repeat, so KevBox Home makes the volume ramp), AC-30 (Home through the HOME intent), AC-26 (Restart app), AC-4 (`globalAction` only served Godmode) |
| `app/src/main/java/cloud/samlo/couch/MainActivity.kt` | `openDrawerIfAsked` starts `kevbox.drawer.DrawerActivity` when the intent carries `KevBox.EXTRA_OPEN_DRAWER`. `onNewIntent` always calls it; `onCreate` calls it only on a fresh start (`savedInstanceState == null`), so a rebuild after process death does not replay the drawer key. `onActivityResult` and its `onSettingsResult` call are gone. Removes `Keepalive.maybe`, `Sync.start` and the `EXTRA_BENCHMARK` route. | AC-22 (drawer key from any app), AC-33 (no Rearrange apps, so Settings has no result to send back), AC-4 |
| `app/src/main/java/cloud/samlo/couch/data/Config.kt` | Removes `ambientMinutes`, `godmode`, `syncEnabled`, `syncAllTvs`, `preReleases`, `hud` and the `HUD_*` constants. Adds `apps`, `drawer`, `hidden`, `buttons`, `speedtestApp` and `keys`, with defaults from `KevBoxConfig` and `KeyMap.DEFAULT`. `FILE` is public, so `KeyActions` can watch `config.json`. `DEFAULT_HIDDEN` is empty instead of nine Sony and Projectivy packages, since nothing in KevBox Home can un-hide an app. `save` writes `config.json.tmp` and renames it inside a shared lock, so Home, the drawer and the provision loader never write the temp file at once. KDoc for the class, `layout` and `checkUpdates` updated. | AC-4, AC-7 (provision keys; only the provision file hides apps), AC-27, AC-5, AC-31 |
| `app/src/main/java/cloud/samlo/couch/data/ConfigJson.kt` | Couch's code moves into private `couch` functions; `encode` wraps it with `KevBoxConfig.encode` and `decode` ends with `KevBoxConfig.decode`. `layout` always loads as `index`. `wallpaper` is read by `KevBoxConfig.decode` instead. The removed features' keys are no longer read or written. | AC-7 (one parser for config and provision file), AC-5, AC-4 |
| `app/src/main/java/cloud/samlo/couch/data/ProgramTile.kt` | `from` fills the tile's `watch` with a `kevbox.home.Watch` (title, season, episode, position, duration). | AC-11 (status line) |
| `app/src/main/java/cloud/samlo/couch/data/Programs.kt` | `allChannels` sorts `config.apps` packages first, in column order. `channels` applies `MAX_CHANNELS` only to apps outside `config.apps`. | AC-10, AC-13: the column's apps always keep their channel row |
| `app/src/main/java/cloud/samlo/couch/data/Tile.kt` | New `watch: Watch?` field, part of `signature`. | AC-11 |
| `app/src/main/java/cloud/samlo/couch/home/CardMenus.kt` | Removes the Change icon item and `pickIcon`. `appInfo` adds `FLAG_ACTIVITY_CLEAR_TASK` next to `FLAG_ACTIVITY_NEW_TASK`. | AC-5: the icon picker went with HDMI inputs; the 2026-10-29 TV check (a fresh details page, so Back returns to Home and the Settings button never reopens it) |
| `app/src/main/java/cloud/samlo/couch/home/HomeLayout.kt` | No `Ambient` overlay. `init` paints the window `Tokens.background` once and calls `showIndex(true)`; `showIndex` no longer swaps the window background. KDoc says the Rows views stay, hidden. | AC-4 (ambient), AC-5 (Index only), AC-13 (backdrop over a flat colour) |
| `app/src/main/java/cloud/samlo/couch/home/HomeLoader.kt` | `build` calls `Provisioner.applyIfPresent(files.dir)` before `Config.load`, and always builds `IndexData`; `SectionBuilder` is no longer called. `HomeData` loses `pictures` and `sections`; `index` is non-null. | AC-6 (provision on start and resume), AC-5, AC-4 |
| `app/src/main/java/cloud/samlo/couch/home/HomeScreen.kt` | A left-column app's long press opens `kevbox.menu.AppMenu`, whose Move calls `IndexScreen.startMove`. `bind` calls `index.topBar.applyLook` and `index.showBackdrop` and always shows Index. Removes ambient mode, the Konami code, the frame HUD, the benchmark, the input offer and `SectionBinder`. `onSettingsResult` is gone, and `openSettings` uses a plain `startActivity` instead of `startActivityForResult`. | AC-23, AC-25, AC-13, AC-14, AC-33, AC-5, AC-4 |
| `app/src/main/java/cloud/samlo/couch/home/SystemWatchers.kt` | The constructor is `(activity, onChanged)`; the TV input callback and its state map are gone, so only package broadcasts remain. | AC-5: no HDMI inputs |
| `app/src/main/java/cloud/samlo/couch/home/TopBar.kt` | Removes the `GodBadge`. | AC-4 |
| `app/src/main/java/cloud/samlo/couch/index/AppList.kt` | Adds a divider and `kevbox.home.MoreAppsRow` (`moreApps`) under the app rows, gliding with them, plus `selectMore`, `rowY` and `placeFooter`. `reveal` counts More apps as the last row. When the rows, divider and More apps all fit, `reveal` rests the track with them centred on the 540dp screen (`centredY`), and `bind` re-centres after the count changes (`chosenIndex`); otherwise the list glides as before. `rowY` takes an optional row count. The bottom fade shows only when rows reach it and the column is not centred. | AC-12 (More apps), AC-13 (fade over the backdrop), Kevin's 2026-10-29 ask: the apps in the middle of the left column |
| `app/src/main/java/cloud/samlo/couch/index/AppRow.kt` | The screen-time figure on the right becomes one ellipsised status line under the name, from `kevbox.home.StatusLine.of`; gone when there is none. The content description adds the line. | AC-11 |
| `app/src/main/java/cloud/samlo/couch/index/Formats.kt` | Dates and weekdays use `Locale.ENGLISH` instead of the TV's locale. | AC-38 |
| `app/src/main/java/cloud/samlo/couch/index/IndexData.kt` | The column is `kevbox.home.AppLists.column(config.apps, config.hidden, …)` instead of a usage sort. `IndexEntry` gains `clock24h`; its signature adds the clock setting and the build hour. Removes `IndexData.inputs` and the `Sync.withOtherTvs` merge. | AC-10, AC-11, AC-5, AC-4 |
| `app/src/main/java/cloud/samlo/couch/index/IndexScreen.kt` | Adds `kevbox.home.Backdrop` as the bottom layer, `kevbox.home.MoveMode` (`startMove`, keys first in `consumesKey`) and `kevbox.home.TopFocus` for Up and Down between the column, the cards and the buttons. More apps opens `kevbox.drawer.DrawerActivity`; `onWindowFocusChanged` places a moving app and puts focus on More apps after `DrawerActivity.takeClosedByBack`. `clearColumn` empties the right side when no app is left. The top bar spans the width between `LIST_X` margins instead of sitting top right. Removes the Inputs pill and `InputMenu`. | AC-12, AC-13, AC-20, AC-21, AC-22, AC-25, AC-5, Kevin's 2026-10-29 ask: the clock and weather top left |
| `app/src/main/java/cloud/samlo/couch/index/IndexTokens.kt` | Adds `SCREEN_H` (540). The list starts lower and is shorter (`LIST_Y` 64, `LIST_H` 423), so it clears the top bar's clock; its fade starts at 340 and `ROWS_SHOWN` is 7, so the fade stays where it was on screen and a gliding list's last row stops at its top edge. Adds `PLACE_MAX_W` (120), the widest the top bar's weather place text gets. | Kevin's 2026-10-29 ask: the clock and weather top left, the apps centred; a long place name must not crowd the top-right buttons |
| `app/src/main/java/cloud/samlo/couch/index/IndexTopBar.kt` | The Inputs and Settings pills, the input offer and the `GodBadge` give way to a private `kevbox.buttons.ButtonRow`, which takes `onSettings`, and a `TextClock`. The clock and weather sit first, on the left, and a weighted `Space` pushes now playing, the update chip and the buttons to the right. The weather's place text is capped at `IndexTokens.PLACE_MAX_W`, and `showWeather` ellipsizes a long place name on its own line, so the buttons always fit. The public `inputs`, `settings`, `inputOffer` and `god` properties and `setHasInputs` are gone. `applyLook(config)` shows `KevBoxConfig.buttonsOf(config)` and sets the clock format; `firstButton()` is new; attach and detach start and stop the row's watchers. The constructor loses `onInputs`. | AC-14 to AC-20, AC-5, AC-4, Kevin's 2026-10-29 ask: the clock and weather top left |
| `app/src/main/java/cloud/samlo/couch/index/SystemLine.kt` | Free storage comes from a local `size()` with English unit names instead of `Formatter.formatShortFileSize`. The memory and storage percentages and the uptime go through a new `english()` helper, and `rate` uses `Locale.ENGLISH`, so every number shows in English digits whatever the TV's language. | AC-38 |
| `app/src/main/java/cloud/samlo/couch/settings/MainPage.kt` | Removes Layout, Card size, Names under cards, Descriptions, Date, Rows, Inputs, Show or hide apps, Rearrange apps, City, Ambient mode, Android settings, Other TVs, the Godmode row and About's seven-press unlock. Permissions always come first, Dim always shows, and App channels opens `PAGE_CHANNELS` itself. | AC-33, AC-4, AC-5 |
| `app/src/main/java/cloud/samlo/couch/settings/SettingsActivity.kt` | Only the main and channels pages. Removes `PAGE_ROWS`, `PAGE_INPUTS`, `PAGE_TVS`, `RESULT_ORGANIZE` and the Godmode accent reset in `onDestroy`. | AC-33, AC-4, AC-5 |
| `app/src/main/java/cloud/samlo/couch/update/Installer.kt` | `verify` checks the APK with `kevbox.update.VersionJson.matches(sha256(apk), release.sha256)` instead of fetching a checksum file. The cache file is `kevbox-home.apk`. | AC-31, AC-32 |
| `app/src/main/java/cloud/samlo/couch/update/Release.kt` | Holds `versionCode`, `sha256` and `notes` and stores version.json's own keys; `deserialize` goes through `VersionJson.read`. Removes `fromGitHub`, `newestOf`, `parse` and `APK`. | AC-31: updates from tv.kevbox.dev, no GitHub |
| `app/src/main/java/cloud/samlo/couch/update/UpdateCheck.kt` | Fetches `VersionJson.URL`, parses with `VersionJson.parse(text, installedCode)` and compares `versionCode`. An answer that is not newer clears the stored one. Removes the GitHub URLs and the pre-release channel. | AC-31, AC-4 (pre-releases were a Godmode setting) |
| `app/src/main/java/cloud/samlo/couch/update/Versions.kt` | Adds `installedCode` (`longVersionCode`). | AC-31, AC-39: a four-part name does not parse as `X.Y.Z` |
| `app/src/main/res/values/strings.xml` | "Couch" becomes "KevBox Home" in `app_name`, the accessibility label and description, the setup help (with `tv.kevbox.home` in its commands), About and the update texts. Adds the 19 `kevbox_*` strings. Removes 326 strings and plurals of Godmode, debloat, Other TVs, the HUD, the benchmark, ambient mode, layouts, inputs, rows and City. | AC-1, AC-4, AC-5, AC-33, AC-11 to AC-26 (new copy) |
| `app/src/main/res/xml/home_accessibility.xml` | Adds `flagRequestFilterKeyEvents`, `flagRetrieveInteractiveWindows` (Restart app reads the settings window from the window list, since the active-window root came back unusable on Google TV) and `canRequestFilterKeyEvents="true"`, sets `canRetrieveWindowContent="true"`, and adds `com.android.tv.settings` and `com.android.settings` to `packageNames`. The comment says why. | AC-27 (key filter), AC-26 (Force stop on the settings page) |
| `app/src/test/java/cloud/samlo/couch/ConfigTest.kt` | Drops the ambient default, the `rows` round trip and the sync test. `defaultsAreGeneric` and the partial-file test expect no hidden apps instead of `DEFAULT_HIDDEN`'s Sony packages. Adds `rowsLayoutLoadsAsIndex`, `missingLayoutIsIndex`, `configWithRemovedKeysLoadsAndSavesWithoutThem` and `savesFromTwoThreadsNeverLeaveABrokenFile`. | AC-5, AC-4, AC-7 (empty default hidden set) |
| `app/src/test/java/cloud/samlo/couch/HomeCaptureTest.kt` | Adds `settingsAppIsNeverStockHome`. | AC-26: opening settings for Restart app must not bounce to Home |
| `app/src/test/java/cloud/samlo/couch/UpdateTest.kt` | Removes the GitHub release tests and their `github` helper. | AC-31 (now in `kevbox/VersionJsonTest`) |
| `docs/architecture.md` | The package tree drops `godmode`, `debloat`, `adb`, `measure`, `sync`, ambient and the icon picker and adds `kevbox`. Index is the only layout; the "Godmode and Other TVs" section and the `InputMenu` paragraph are gone. HOME takeover covers key filtering and Restart app. Tests lists the `kevbox` classes and the provision script test. | AC-4, AC-5, AC-27, AC-26 (task 13: the page describes what the code does now) |

## Deleted

| Path | What changed | Why |
|---|---|---|
| `app/src/main/assets/debloat/lists.txt` | Deleted: the per-brand debloat lists. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/adb/AdbKey.kt` | Deleted: the RSA key the TV's "Allow debugging?" prompt authorised. | AC-4: built-in ADB client removed |
| `app/src/main/java/cloud/samlo/couch/adb/AdbProtocol.kt` | Deleted: adb's framing to the TV's own adbd. | AC-4: built-in ADB client removed |
| `app/src/main/java/cloud/samlo/couch/adb/AdbShell.kt` | Deleted: one shell command over the TV's own adbd. | AC-4: built-in ADB client removed |
| `app/src/main/java/cloud/samlo/couch/debloat/AppGrid.kt` | Deleted: the debloat grid's tile data. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/AppIcons.kt` | Deleted: icon decoding for the debloat grid. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/AppTile.kt` | Deleted: one app in the debloat grid. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/BatchRows.kt` | Deleted: removal batches and their check. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/Catalog.kt` | Deleted: reader for `assets/debloat/lists.txt`. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/CommandRows.kt` | Deleted: the computer route, commands to type. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/DebloatList.kt` | Deleted: what Couch knew about the TV's own apps. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/DebloatPage.kt` | Deleted: the debloat screens. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/DebloatStore.kt` | Deleted: the `debloat.json` ledger. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/Engine.kt` | Deleted: the debloat shell commands. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/Impact.kt` | Deleted: what removing an app would free. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/Keepalive.kt` | Deleted: the daily reconnect that kept "Always allow" alive. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/Packages.kt` | Deleted: package checks without the shell. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/ReportRows.kt` | Deleted: the before and after report. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/ScanRows.kt` | Deleted: the scan by what removal would buy. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/debloat/UndoRows.kt` | Deleted: the undo list. | AC-4: debloat removed |
| `app/src/main/java/cloud/samlo/couch/godmode/AdbCard.kt` | Deleted: the one-command grant card. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/FreeCache.kt` | Deleted: `pm trim-caches` over the shell. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/GodmodePage.kt` | Deleted: the hidden Godmode page. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/Konami.kt` | Deleted: the Konami code into Godmode. | AC-4: no key sequence leads to Godmode |
| `app/src/main/java/cloud/samlo/couch/godmode/MeasureRows.kt` | Deleted: the HUD and benchmark rows. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/Readouts.kt` | Deleted: live readings under Godmode's help pane. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/RealHome.kt` | Deleted: setting Couch as Android's preferred home. | AC-4: Godmode removed (the provision script sets Home instead, AC-8) |
| `app/src/main/java/cloud/samlo/couch/godmode/Sleep.kt` | Deleted: the sleep timer and screenshot. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/ToolRows.kt` | Deleted: sleep, screenshot and real-home rows. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/TvRows.kt` | Deleted: TV-wide settings rows (animations, private DNS, background apps). | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/TvSettings.kt` | Deleted: the `Settings.Global` writes and their originals. | AC-4: Godmode removed |
| `app/src/main/java/cloud/samlo/couch/godmode/Unlock.kt` | Deleted: the seven-OK counter on About. | AC-4: no key sequence leads to Godmode |
| `app/src/main/java/cloud/samlo/couch/home/AmbientMode.kt` | Deleted: the idle timer for ambient mode. | AC-4: ambient mode removed |
| `app/src/main/java/cloud/samlo/couch/index/InputMenu.kt` | Deleted: the Inputs pill's panel. | AC-5: no Inputs pill |
| `app/src/main/java/cloud/samlo/couch/index/InputMenuRow.kt` | Deleted: one input in that panel. | AC-5: no Inputs pill |
| `app/src/main/java/cloud/samlo/couch/measure/Benchmark.kt` | Deleted: the D-pad benchmark. | AC-4: benchmark removed |
| `app/src/main/java/cloud/samlo/couch/measure/BenchmarkCard.kt` | Deleted: the benchmark's result card. | AC-4: benchmark removed |
| `app/src/main/java/cloud/samlo/couch/measure/FrameHud.kt` | Deleted: the frame HUD. | AC-4: frame HUD removed |
| `app/src/main/java/cloud/samlo/couch/measure/FrameMeter.kt` | Deleted: per-frame timing from Android. | AC-4: frame HUD removed |
| `app/src/main/java/cloud/samlo/couch/measure/Frames.kt` | Deleted: which frame stage took longest. | AC-4: frame HUD removed |
| `app/src/main/java/cloud/samlo/couch/settings/IconPickerActivity.kt` | Deleted: the input icon picker. | AC-5: no HDMI inputs, so no icons to pick |
| `app/src/main/java/cloud/samlo/couch/settings/InputsPage.kt` | Deleted: the Inputs settings page. | AC-5: the Inputs page never appears |
| `app/src/main/java/cloud/samlo/couch/settings/OtherTvsPage.kt` | Deleted: the Other TVs page. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/settings/RowMover.kt` | Deleted: Move mode on the Rows page. | AC-5, AC-33: no Rows layout or Rows entry |
| `app/src/main/java/cloud/samlo/couch/settings/RowsPage.kt` | Deleted: which home rows show, and their order. | AC-5, AC-33: no Rows layout or Rows entry |
| `app/src/main/java/cloud/samlo/couch/sync/Client.kt` | Deleted: the asking side of Other TVs. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Crypto.kt` | Deleted: pairing key agreement and snapshot encryption. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Discovery.kt` | Deleted: mDNS discovery of other TVs. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Labels.kt` | Deleted: app labels in every language, for matching across TVs. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Merge.kt` | Deleted: screen time added up across TVs. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Peers.kt` | Deleted: the paired TVs. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Server.kt` | Deleted: the socket server paired TVs ask. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Snapshot.kt` | Deleted: one TV's week of screen time, as shared. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Sync.kt` | Deleted: the sync entry point. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/sync/Wire.kt` | Deleted: the framed JSON messages. | AC-4: Other TVs removed |
| `app/src/main/java/cloud/samlo/couch/ui/Ambient.kt` | Deleted: the ambient mode overlay. | AC-4: ambient mode removed |
| `app/src/main/java/cloud/samlo/couch/ui/GodBadge.kt` | Deleted: the `GOD` tag by the clock. | AC-4: Godmode removed |
| `app/src/test/java/cloud/samlo/couch/DebloatListTest.kt` | Deleted with the code it tested. | AC-4: debloat removed |
| `app/src/test/java/cloud/samlo/couch/DebloatStoreTest.kt` | Deleted with the code it tested. | AC-4: debloat removed |
| `app/src/test/java/cloud/samlo/couch/FramesTest.kt` | Deleted with the code it tested. | AC-4: frame HUD removed |
| `app/src/test/java/cloud/samlo/couch/ImpactTest.kt` | Deleted with the code it tested. | AC-4: debloat removed |
| `app/src/test/java/cloud/samlo/couch/KonamiTest.kt` | Deleted with the code it tested. | AC-4: Godmode removed |
| `app/src/test/java/cloud/samlo/couch/SyncProtocolTest.kt` | Deleted with the code it tested. | AC-4: Other TVs removed |
| `app/src/test/java/cloud/samlo/couch/SyncTest.kt` | Deleted with the code it tested. | AC-4: Other TVs removed |
| `app/src/test/java/cloud/samlo/couch/UnlockTest.kt` | Deleted with the code it tested. | AC-4: Godmode removed |
