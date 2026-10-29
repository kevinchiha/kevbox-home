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
Rows layout, HDMI inputs). Leave them as they are: editing them only adds merge conflicts. The one
exception is the short KevBox Home section at the top of `README.md`, which the licence asks for.

## Licence

Couch is licensed under the GNU GPL v3 since samuelloranger/couch#25; its `LICENSE` sits at the repo
root, unedited. KevBox Home is a modified Couch, so it is GPL v3 too. That puts three duties on this
repo:

- Source for every released APK. Whoever gets a KevBox Home APK must be able to get the exact
  source it was built from. That source is the commit on `main`, in the public repository
  [kevinchiha/kevbox-home](https://github.com/kevinchiha/kevbox-home), that the release was built
  from. Release tags stay on this computer (see below), so the commit is what identifies the
  source. `release.sh` refuses a HEAD that is not already on `origin/main`, so every release's
  commit is pushed before the APK exists. Never rewrite or force-push `main`: a released APK would
  lose its source. Making the repository public is Kevin's step; agents never change its
  visibility.
- Keep the notices. Leave `LICENSE` and Couch's copyright and licence notices in place. Settings ›
  About and the top of `README.md` say that KevBox Home is a modified Couch under GPL v3, with no
  warranty, and where its source is.
- Mark the changes. `UPSTREAM-SYNC.md` says what this fork changed in each Couch file and why.
  Keep it current (see "Editing a Couch file").

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
`versionCode` = X·1,000,000 + Y·10,000 + Z·100 + N. With Couch 0.9.0 merged, an untagged build says
`0.9.0.0` (code 90000), and the next release is `0.9.0.1`, code 90001.

- The `versionCode` in tv.kevbox.dev's `home/version.json` must be the APK's own AC-39 code, or
  boxes never see the update as newer. KevBox Home ignores that file's `profiles`.
- Couch's `.github/workflows/release.yml` publishes a signed GitHub release for any pushed `v*` tag,
  and `ci.yml` still gates the APK at Couch's 614400 bytes (AC-35's limit is 1048576). Both run only
  on a push, so never push a tag: release.sh keeps its tags local. Leave the workflows unedited.

On 2026-10-29 upstream was merged up to `d3dd5ea` (Couch 0.9.0, "docs: license Couch under GPL v3
(#25)"); the fork had started from `76be0dc` (Couch v0.8.1). That merge left out Couch's remote relay
(#18: `remote/`, `bravia/`, `settings/RemotePage.kt`, `app/src/main/native/relay.c` and its build),
because it needs Couch's ADB client, which this fork removed, and an ADB port left open on every
member's box. Each of its files has a row in `UPSTREAM-SYNC.md`'s Deleted section. Whether to bring
it in is queued for Kevin's review; until he decides, keep it out when a later merge touches it.
Merge newer upstream commits only when Kevin asks.

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
only Kevin runs them.
[docs/kevbox/releasing.md](docs/kevbox/releasing.md) covers `release.sh` (Kevin runs it, or asks for
it in the moment) and the emulator check of the silent update.

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
