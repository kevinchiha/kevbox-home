# AGENTS.md

Instructions for coding agents (Claude Code, Codex and the rest) working in this repository.

## What this repo is

A fork of [samuelloranger/couch](https://github.com/samuelloranger/couch), the Android TV launcher,
that builds KevBox Home: the launcher for KevBox members' Xiaomi TV Box S and Kevin's TCL test TV.
The release build installs as `tv.kevbox.home`, the debug build as `tv.kevbox.home.dev`. The Kotlin
package and the Gradle `namespace` stay `cloud.samlo.couch`, so Couch's files keep their paths and
upstream merges stay small. What it changes, and why, is in
`docs/superpowers/specs/2026-10-28-0.1-kevbox-home-design.md`.

`README.md`, `docs/godmode.md`, `docs/sync.md`, `docs/configuration.md` and `docs/.vitepress/` are
Couch's own pages and describe Couch, including features this fork removed (Godmode, Other TVs, the
Rows layout, HDMI inputs). Leave them as they are: editing them only adds merge conflicts.

## Licence first

Couch has no licence yet. Kevin asked for one in samuelloranger/couch#17. Until Couch carries a
licence, nothing from this repo is pushed, published, released or installed on a member's box.
Kevin's own TCL test TV and his own Xiaomi box are the only devices it runs on. If the author
refuses, the repo is deleted.

## How Couch updates come in

Couch changes arrive by merge, never by rebase and never with a force push:

```sh
git fetch upstream
git merge upstream/main
```

Resolve conflicts with `UPSTREAM-SYNC.md` open: each row says what this fork changed in that file
and why, so you can keep both sides. After the merge, run the gate, then update the rows of every
file the merge touched.

After merging Couch X.Y.Z, the next release tag is `vX.Y.Z.1` (spec AC-39). The build takes the
highest `v*` tag reachable from HEAD, counting Couch's three-part tags as `.0`, and sets
`versionCode` = X·1,000,000 + Y·10,000 + Z·100 + N. Today that is `0.8.1.0`, code 80100.

- The `versionCode` in tv.kevbox.dev's `home/version.json` must be the APK's own AC-39 code, or
  boxes never see the update as newer. KevBox Home ignores that file's `profiles`.
- Couch's `.github/workflows/release.yml` publishes a signed GitHub release for any pushed `v*` tag,
  and `ci.yml` still gates the APK at Couch's 614400 bytes (AC-35's limit is 1048576). Both run only
  on a push, so no tag is pushed while the licence rule stands. Leave the workflows unedited.

The fork point is upstream commit `76be0dc` (Couch v0.8.1). Upstream has moved on since, to
`6d5638c` ("Let the remote's Input and Settings buttons open Couch's panels"), which is not merged.
That commit touches `HomeAccessibilityService.kt`, `AndroidManifest.xml`, `build.gradle.kts`,
`Config.kt`, `ConfigJson.kt`, `MainPage.kt`, `SettingsActivity.kt`, `strings.xml` and
`adb/AdbShell.kt` (deleted here), so expect conflicts there. Merge it only when Kevin asks.

## Commands

Run from the repo root.

```sh
# The gate: unit tests, lint (zero errors) and the release APK
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease

# Size check: must print 1048576 or less (AC-35)
stat -c %s app/build/outputs/apk/release/app-release.apk

# One test class
./gradlew --no-daemon :app:testDebugUnitTest --tests '<class>'

# The provisioning script's own test, against a fake adb
kevbox/provision/provision-test.sh

# Provision a box (one device attached)
kevbox/provision/provision.sh kevbox/provision/kevbox-home.example.json

# More than one device attached: name the box
ANDROID_SERIAL=<serial> kevbox/provision/provision.sh kevbox/provision/kevbox-home.example.json

# The debug build instead of the release one
KEVBOX_HOME_PACKAGE=tv.kevbox.home.dev kevbox/provision/provision.sh kevbox/provision/kevbox-home.example.json
```

[docs/kevbox/provisioning.md](docs/kevbox/provisioning.md) describes the provision file and every
script failure line. [docs/kevbox/test-on-tv.md](docs/kevbox/test-on-tv.md) is the checklist Kevin
runs on the TCL test TV and the Xiaomi box. Provisioning and the checklist touch a real device, so
only Kevin runs them; the licence rule above still applies.

## Where new code goes

- Kotlin: `app/src/main/java/cloud/samlo/couch/kevbox/`, one subpackage per feature (`buttons`,
  `config`, `drawer`, `home`, `keys`, `menu`, `provision`, `restart`, `update`).
- Its tests: `app/src/test/java/cloud/samlo/couch/kevbox/`.
- Scripts and the example provision file: `kevbox/provision/`.
- Docs: `docs/kevbox/`. Pages under `docs/` name `AGENTS.md`, `UPSTREAM-SYNC.md` and
  `kevbox/provision/` in backticks, never as links: VitePress builds `docs/` as a site and fails on
  a link that leaves it.

A Couch file gets the smallest edit that calls into `kevbox/`. Never move Couch code into `kevbox/`
or `kevbox/` code into Couch's packages. New user-facing text goes in
`app/src/main/res/values/strings.xml`; `values-fr/` stays untouched (AC-38).

## Editing a Couch file

Any edit to a file that exists on `upstream/main` adds or updates its row in `UPSTREAM-SYNC.md`, in
the same commit. Files only this fork has get no row.

Never create a `CLAUDE.md`; agent instructions live in this file.
