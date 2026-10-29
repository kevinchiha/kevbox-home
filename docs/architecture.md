# Architecture

KevBox Home is Couch reshaped: one Gradle module of plain Kotlin on framework Views. Seven
packages, each with one job; KevBox Home's own code sits in `kevbox/`, and Couch's files get only
the small calls into it, so upstream merges stay clean (`UPSTREAM-SYNC.md` at the repo root lists
every one).

```
cloud.samlo.couch
├── MainActivity, HomeAccessibilityService, MediaListener, BootReceiver
├── home/      the home screen: wiring, loading, binding, top bar, menus, reorder
├── ui/        reusable views: Card, FocusRow, Rows, Hero, NowPlaying, tokens
├── data/      config, apps, inputs, channels, weather, wallpapers, image pipeline
├── update/    daily release check, download, checksum, Android's package installer
├── settings/  Customize: activity shell, option rows, the main and channels pages
├── index/     the Index layout: app list, program text, channel row, the week, system line
└── kevbox/    KevBox Home's own code
    ├── KevBox     the log tag (KevBoxHome) and the way back to Home
    ├── config/    the KevBox keys of config.json and of the provision file, one parser
    ├── provision/ applies the pushed kevbox-home.json on every Home load
    ├── home/      left column order, status lines, More apps, the backdrop, Move in the column
    ├── buttons/   the top-right Wi-Fi, VPN, Bluetooth, Speed test and Settings buttons
    ├── drawer/    the full-screen grid of every other TV app, with Move
    ├── menu/      the hold-OK menu: App info, Move, Restart app, Uninstall
    ├── keys/      the remote key map and the filter the accessibility service runs
    ├── restart/   Restart app: the details page, then Force stop pressed by the service
    └── update/    tv.kevbox.dev's home/version.json
```

The four classes at the root are referenced by name from the TV's settings (the launcher entry,
the accessibility service, the notification listener, the boot receiver), so they keep their name
and package. `MainActivity` only forwards lifecycle calls and keys to `home.HomeScreen`, and starts
the drawer when a drawer key brought Home to the front.

## Data flow

```text
loader thread                                         main thread
─────────────                                         ───────────
kevbox-home.json ─▶ Provisioner ─▶ config.json
config.json ─▶ HomeLoader
                ├─ AppSource ───┐
                ├─ Programs ────┼─▶ IndexBuilder ─IndexData─▶ IndexScreen
                ├─ Usage ───────┘                                 │
                └─ Weather, Wallpapers                            ▼
                                                  AppList / FocusRow / Card ─focus─▶ Spotlight, Backdrop
settings/*, Move ─▶ config.json
```

1. `HomeLoader` runs on a single background thread. It first lets `Provisioner` apply a pushed
   `kevbox-home.json`, then reads `config.json`, lists apps, queries preview channels, and fetches
   weather and the wallpaper. Banners are rendered there, at their final pixel size.
2. `IndexBuilder` turns that into `IndexData`: the left column's apps in `config.apps` order (minus
   `hidden`, through `kevbox/home/AppLists`), each with its channel, its week of usage and a
   signature of everything that affects how it looks.
3. `IndexScreen` applies it on the main thread. Rows whose signature didn't change are left alone,
   and the focused view is never detached before focus has somewhere to go, so reloads don't move
   you.

Loads happen on every return to the home screen and when apps change (`SystemWatchers`). Because
unchanged rows aren't touched, a reload is invisible.

## Views and motion

- **`Card`** animates only scale and the ring's alpha on focus. Its content (`CardContent`) is a
  pre-rounded bitmap, remote art loaded at its exact size, or an icon with a label.
- **`FocusRow`** never scrolls: it translates its strip so the focused card sits at a fixed
  anchor, retargeting one animation per move. It draws the focused card last and keeps left/right
  inside the row. Coming back from another row returns to the card you left.
- **`Rows`** does the same vertically: the focused row glides to the top of the rows area.
- **`Hero`** updates its text on every move (fixed-size views, so no layout pass) and only loads
  the backdrop once focus has rested, crossfading between two stacked image views.
- **`Tokens`** holds every colour, size and timing. Everything shares one expo-out curve.

`Rows`, `Hero` and the Rows layout's `SectionBuilder` and `SectionBinder` stay in the source,
unused and hidden, so Couch's changes to them merge without conflicts.

## Index layout

`IndexScreen` is the only layout. Everything sits at fixed positions from `IndexTokens` (dp on the
960x540 canvas), so focus moves never lay out.

- **`AppList`** glides its column by translation, like `Rows`; `AppRow` only swaps colours when
  selected. Each row shows the app's icon, its name and one status line (`kevbox/home/StatusLine`):
  where the show stands, else when the app was last opened. Under the last app, past a divider,
  `MoreAppsRow` opens the drawer. `IndexScreen.focusSearch` routes the D-pad between the list, the
  channel row (a `FocusRow` of `Card`s with a thin ring) and the top buttons; `TopFocus` remembers
  where Up came from, so Down goes back there.
- **`Backdrop`** (`kevbox/home`) is the one full-screen layer: the focused program's art, else the
  wallpaper, darkened by Customize's Dim. It crossfades like `Hero`, of which it is a copy.
- **`Spotlight`** is text only: the focused program's source, title, synopsis and progress, or the
  app's name. The pictures are the channel's cards, so moving along them decodes no larger copy of
  their art; an app without a channel shows its banner, rendered at card size, in the row's place.
- **`StatsBar`** shows the week's total, when the app was last opened, and a bar per day.
- **`IndexTopBar`** holds what's playing, the Update chip, `kevbox/buttons/ButtonRow` and the clock,
  then the weather. The Wi-Fi, Bluetooth and Speed test buttons are icons that widen to their text
  when focused; the VPN indicator shows only while a VPN network is up and takes no focus.
- **`Recommendations`** turns Fire TV's recommendation notifications (read through `MediaListener`)
  into one channel per app that has no preview channel; `Programs` lists them with the others.
- **`Usage`** reads usage events (with usage access) into seven days per app; `Usage.Tally` is the
  pure part and is unit tested. **`SystemInfo`** gives `SystemLine` its readings every few seconds.
- Text uses Hanken Grotesk (`Fonts`, a Latin subset of the variable font in `assets/fonts`, OFL).
  Dates, weekdays and the bottom line's numbers are formatted in English whatever the TV's
  language, since the APK ships English copy only.

## Drawer and hold-OK menu

`kevbox/drawer/DrawerActivity` is a full-screen grid, five columns wide, of every TV app that is
neither in the left column nor hidden: the provisioned `drawer` packages first, the rest by name.
Only Home starts it, so its task sits straight above Home's and Back lands on More apps. It listens
for package removals itself, so an uninstalled app leaves while it is open.

Holding OK on an app, in the column or the drawer, opens `kevbox/menu/AppMenu`: App info, Move,
Restart app, and Uninstall for removable drawer apps only. Move saves the new order to
`config.json` (`MoveMode` and `ColumnMove` in the column, `GridMove` in the drawer). Program cards
keep Couch's `CardMenus`.

## Data

- **`Config`** is the user's setup; `ConfigJson` reads and writes it atomically. Unknown or broken
  values fall back to defaults instead of failing. The KevBox keys (`apps`, `drawer`, `hidden`,
  `buttons`, `speedtestApp`, `keys`, `wallpaper`) go through `kevbox/config/KevBoxConfig`, the same
  parser the provision file uses.
- **`AppSource` / `InputSource` / `Programs`** produce `Tile`s, the one data type every card shows.
  Inputs get their name from the TV's label or the connected CEC device, and their icon from
  `InputIcon.resolve`; KevBox Home shows no inputs, but the code stays for upstream merges.
- **`Images`** is the image pipeline: `Fetcher` downloads, `DiskCache` keeps remote art on disk
  (60 MB budget), and decoded bitmaps sit in a memory cache sized from the heap.
- **`Weather`** (Open-Meteo, with Meteocons icons by Bas Milius, MIT, as animated vectors) and **`Wallpapers`** (Bing, a subreddit through Arctic Shift, a local
  image) cache every answer in preferences, so a network hiccup keeps the last good result.
- **`Provisioner`** (`kevbox/provision`) applies `kevbox-home.json` from the app's external files
  folder, then renames it `kevbox-home.applied.json`, or `kevbox-home.rejected.json` when it is not
  a JSON object. The file format is in [Provisioning a box](kevbox/provisioning.md); the script
  that pushes it lives in `kevbox/provision/`.
- **`UpdateCheck`** reads `https://tv.kevbox.dev/home/version.json` once a day through
  `kevbox/update/VersionJson`, and offers a release only when its `versionCode` is higher than the
  installed one. `Installer` checks the APK against the file's `sha256` before Android's package
  installer sees it.

## Settings

`SettingsActivity` hosts one page at a time (main, channels), chosen by an intent extra. Pages
build their list from `OptionList` helpers (choice, toggle, text, link), each producing an
`OptionRow`. Every change is saved immediately; the home screen picks it up when it resumes.
Holding OK on the Settings button opens it; a plain OK opens Android's settings.

## HOME takeover and the remote

`HomeAccessibilityService` has three jobs.

- It filters key events. `kevbox/keys/KeyFilter` matches each key against the `keys` map
  (`KeyMap.DEFAULT` until a provision file sets one), consumes a matched key's down, repeats and
  up, and acts once on the up: open a package, show Home, or open the drawer. A key with no match
  passes through untouched, so holding volume still ramps. Home goes through the HOME intent
  (`KevBox.goHome`), never an explicit start. The stock-launcher catch below and `provision.sh`
  start it the same way, so none of KevBox's own starts builds a second Home.
- It receives window-state events from the stock launchers' packages and opens KevBox Home when one
  appears (`isStockHome`).
- It runs Force stop for Restart app. `kevbox/restart/AppRestart` opens the app's details page;
  `ForceStop`, inside the service, finds the settings app's own "Force stop" string, presses it
  and its confirmation, then relaunches the app. If a step takes more than 5 s, or the service is
  off, a toast asks the member to finish by hand.

With `WRITE_SECURE_SETTINGS`, `HomeAccessibilityService.restore` puts the service back in
Android's enabled list after a force-stop removes it; `HomeScreen` and `BootReceiver` call it.

## Tests

JVM unit tests (`app/src/test`) cover the pure logic: config defaults, round-trip and migrations,
input icon guessing, row ordering, weather conditions, subreddit image filtering, input keys,
and how usage events add up to screen time. `HomeCaptureTest` checks which windows count as the
stock home, and `UpdateTest` the version comparison and the update switch's default.

KevBox Home's tests sit in `app/src/test/java/cloud/samlo/couch/kevbox/`: `KevBoxConfigTest` and
`ProvisionerTest` (the provision file, its parsing and merging), `KeyMapTest` and `KeyFilterTest`
(the remote map and per-press handling), `AppListsTest` (what the column and the drawer hold),
`StatusLineTest` (the status-line text), `ButtonTextTest` (the buttons' focused text),
`ColumnMoveTest` and `GridMoveTest` (Move in the column and the drawer), `UninstallTest` (when
Uninstall is offered) and `VersionJsonTest` (`version.json` parsing). `kevbox/provision/provision-test.sh`
runs the provisioning script against a fake `adb`.
