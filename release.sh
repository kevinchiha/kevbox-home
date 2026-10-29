#!/usr/bin/env bash
# KevBox Home fork only: this file does not exist upstream (samuelloranger/couch).
#
# Builds, checks and publishes one KevBox Home release to https://tv.kevbox.dev/home/ (AC-14).
# Kevin runs it by hand, or asks for it in the moment. docs/kevbox/releasing.md explains each step.
#
#   ./release.sh [--no-silent] <A.B.C.K> [release notes...]
#
# By default version.json carries "silent": {"after": <publish time, epoch ms>, "earlyUsers": []},
# so Android 12+ boxes install the release by themselves once nobody is watching
# (kevbox/update/SilentRules.kt). Home has no sign-in, so there are no early users and no 24 h wait.
# --no-silent leaves the block out: every box shows the Update chip instead.
#
# Every check runs before anything changes, and a failed check stops the script with exit 1:
#   - the arguments (release_manifest.py check-args): --no-silent only as the first argument, no
#     other --option, a version not starting with -
#   - a clean working tree, so the release is exactly HEAD
#   - the version: A.B.C.K (A, B, C 0-99, K 1-99), versionCode A*1000000 + B*10000 + C*100 + K
#   - the tag v<A.B.C.K> does not exist yet
#   - HEAD is on origin/main (pushing is Kevin's step; this script never pushes)
#   - the versionCode is above the one tv.kevbox.dev/home/version.json publishes (404: first
#     release) and above the highest v* tag already in HEAD
#   - ~/secrets/kevbox-keys/release.env sources cleanly and names an existing keystore, its alias
#     and both passwords (NUVIO_RELEASE_*: the KevBox TV key); sourced in subshells, never printed
#   - apksigner, aapt2, curl, ssh and scp are installed
#   - the rest of the gate: kevbox/provision/provision-test.sh and kevbox/release's tests
#
# Then it creates the LOCAL tag v<A.B.C.K> at HEAD (Gradle takes the version from it; the tag is
# never pushed, and is deleted again if anything fails before the upload; after the upload a
# failure keeps it and says what is on the host), runs the Gradle gate with the KevBox TV key, and
# checks the APK: package tv.kevbox.home, versionName and versionCode, the signing certificate's
# SHA-256 against the KevBox TV key's, at most 1048576 bytes. Only then it uploads the APK to
# persovps as kevbox-home-<version>.apk, takes the SHA-256 on the host and compares it with the
# built one, writes version.json, and last points kevbox-home-latest.apk at the new APK.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

manifest_py="kevbox/release/release_manifest.py"
signing_env="$HOME/secrets/kevbox-keys/release.env"
# The KevBox TV key's certificate SHA-256, the constant in KevBox Live's release.sh.
expected_sha256="9A:E0:71:8C:CC:F9:F6:83:90:93:49:31:D3:FE:0E:52:F4:57:EF:B5:81:D5:AC:C1:14:16:F1:51:2D:BB:1D:6F"
package="tv.kevbox.home"
# VersionJson.RELEASE_URL: boxes trust APKs on https://tv.kevbox.dev only, so these are fixed.
base_url="https://tv.kevbox.dev/home"
release_url="$base_url/version.json"
max_bytes=1048576
built_dir="app/build/outputs/apk/release"
built_apk="$built_dir/app-release.apk"
ssh_alias="${KEVBOX_SSH_ALIAS:-persovps}"
# The port comes from ~/.ssh/config for the alias; KEVBOX_SSH_PORT overrides it.
ssh_port_args=(); scp_port_args=()
if [[ -n "${KEVBOX_SSH_PORT:-}" ]]; then ssh_port_args=(-p "$KEVBOX_SSH_PORT"); scp_port_args=(-P "$KEVBOX_SSH_PORT"); fi
web_root="${KEVBOX_HOME_WEB_ROOT:-/var/www/kevbox-tv/home}"
usage="usage: ./release.sh [--no-silent] <A.B.C.K> [release notes...]"

die() {
  echo "release: $*" >&2
  exit 1
}

note() {
  echo "release: $*"
}

# Sources <env file> into the calling shell; call it in a subshell. Its output is dropped, since
# bash prints the offending line, value included, on a syntax error. Copied from KevBox Live's
# tools/kevbox/release-checks.sh.
source_env_quietly() {
  local env_file="${1:-}"
  # shellcheck source=/dev/null
  if ! source "$env_file" >/dev/null 2>&1; then
    echo "release: $env_file could not be sourced (unreadable, or a line bash cannot run; check its quoting)" >&2
    return 1
  fi
}

work="$(mktemp -d)"
tag=""
tag_created=0
# Set once the APK has reached the host: from then on a failure keeps the tag and says what is there.
uploaded=0
published=0
cleanup() {
  local status=$?
  rm -rf -- "$work"
  if (( tag_created == 1 && published == 0 )); then
    if (( uploaded == 0 )); then
      git tag -d "$tag" >/dev/null 2>&1 && echo "release: deleted the local tag $tag again; nothing was published" >&2
    else
      echo "release: kevbox-home-$version.apk may be on $ssh_alias in $web_root, but version.json may not name it, so boxes may not be offered it." >&2
      echo "release: kept the local tag $tag. Open $release_url: if it names $version, the release is out and only kevbox-home-latest.apk still needs repointing by hand; if not, run 'git tag -d $tag' and rerun ./release.sh with the same version." >&2
    fi
  fi
  exit "$status"
}
trap cleanup EXIT

# --- Checks: nothing changes until all of them pass. ---

# Silent install is the default, so a misplaced --no-silent (or a typo such as --no-silnet) would
# publish a release that installs by itself. Stop before anything else.
python3 "$manifest_py" check-args "$@" || die "$usage   (--no-silent goes first; no other options)"
no_silent=0
if [[ "${1:-}" == "--no-silent" ]]; then
  no_silent=1
  shift
fi
[[ $# -ge 1 && -n "$1" ]] || die "$usage"
version="$1"
shift
notes="$*"
[[ -n "$notes" ]] || notes="KevBox Home $version"
tag="v$version"

status="$(git status --porcelain)" || die "git status failed"
if [[ -n "$status" ]]; then
  echo "$status" >&2
  die "the working tree has uncommitted or untracked files; commit or remove them first"
fi

# The format alone first: offline, so a typo stops before any network call.
python3 "$manifest_py" check-version "$version" >/dev/null || die "stopped: bad version $version"

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  die "the tag $tag already exists; pick the next version"
fi

git fetch --quiet origin main || die "could not fetch origin main"
git merge-base --is-ancestor HEAD origin/main \
  || die "HEAD ($(git rev-parse --short HEAD)) is not on origin/main; push main first (git push origin main), then rerun"

published_args=()
http_code="$(curl -sS -L --max-time 30 -o "$work/published.json" -w '%{http_code}' "$release_url")" \
  || die "could not reach $release_url; nothing changed"
case "$http_code" in
  200) published_args=(--published-json "$work/published.json") ;;
  404) note "no version.json at $release_url yet: this is the first release" ;;
  *) die "$release_url answered HTTP $http_code; check the server, nothing changed" ;;
esac
git tag --merged HEAD --list 'v*' >"$work/tags"
version_code="$(python3 "$manifest_py" check-version "$version" \
  "${published_args[@]+"${published_args[@]}"}" --tags "$work/tags")" \
  || die "stopped: $version is not above what is published or already tagged"

[[ -f "$signing_env" ]] || die "missing $signing_env (the NUVIO_RELEASE_* signing values of the KevBox TV key)"
# `|| exit 1` because errexit is off left of `||`.
(
  source_env_quietly "$signing_env" || exit 1
  [[ -n "${NUVIO_RELEASE_STORE_FILE:-}" && -f "${NUVIO_RELEASE_STORE_FILE}" ]] || exit 1
  [[ -n "${NUVIO_RELEASE_STORE_PASSWORD:-}" && -n "${NUVIO_RELEASE_KEY_ALIAS:-}" && -n "${NUVIO_RELEASE_KEY_PASSWORD:-}" ]] || exit 1
) || die "$signing_env must source cleanly and set NUVIO_RELEASE_STORE_FILE (an existing keystore), NUVIO_RELEASE_STORE_PASSWORD, NUVIO_RELEASE_KEY_ALIAS and NUVIO_RELEASE_KEY_PASSWORD"

android_home="${ANDROID_HOME:-$HOME/Android/Sdk}"
apksigner="$(ls -d "$android_home"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
[[ -n "$apksigner" && -x "$apksigner" ]] || die "no apksigner under $android_home/build-tools"
aapt2="$(ls -d "$android_home"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1 || true)"
[[ -n "$aapt2" && -x "$aapt2" ]] || die "no aapt2 under $android_home/build-tools"
for tool in curl ssh scp sha256sum; do
  command -v "$tool" >/dev/null || die "$tool is not installed"
done

bash kevbox/provision/provision-test.sh || die "kevbox/provision/provision-test.sh failed; nothing changed"
python3 -m unittest discover -s kevbox/release -p 'test_*.py' \
  || die "kevbox/release's tests failed (python3 -m unittest discover -s kevbox/release -p 'test_*.py'); nothing changed"

# --- Tag and build. ---

note "tagging $tag at $(git rev-parse --short HEAD) (local only, never pushed)"
git tag -a "$tag" -m "KevBox Home $version"
tag_created=1

# A stale APK from an earlier build must never ship under the new tag.
rm -f -- "$built_dir"/*.apk

note "building KevBox Home $version (versionCode $version_code) with the KevBox TV key"
(
  source_env_quietly "$signing_env" || exit 1
  # build.gradle.kts reads the COUCH_* names.
  export COUCH_KEYSTORE="$NUVIO_RELEASE_STORE_FILE"
  export COUCH_KEYSTORE_PASSWORD="$NUVIO_RELEASE_STORE_PASSWORD"
  export COUCH_KEY_ALIAS="$NUVIO_RELEASE_KEY_ALIAS"
  export COUCH_KEY_PASSWORD="$NUVIO_RELEASE_KEY_PASSWORD"
  export ANDROID_HOME="$android_home"
  ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
) || die "the Gradle build failed; nothing was published"

# --- Check the APK. ---

[[ -f "$built_apk" ]] || die "no signed APK at $built_apk (an -unsigned APK means signing was not set up); nothing was published"

size="$(stat -c %s "$built_apk")"
(( size <= max_bytes )) || die "$built_apk is $size bytes, over the $max_bytes limit (AC-35); nothing was published"

badging="$("$aapt2" dump badging "$built_apk")" || die "aapt2 could not read $built_apk; nothing was published"
built_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")"
built_code="$(sed -n "s/^package: .* versionCode='\([^']*\)'.*/\1/p" <<<"$badging")"
built_name="$(sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" <<<"$badging")"
[[ "$built_package" == "$package" ]] || die "$built_apk is package '$built_package', not $package; nothing was published"
[[ "$built_name" == "$version" ]] || die "$built_apk has versionName '$built_name', not $version; nothing was published"
[[ "$built_code" == "$version_code" ]] || die "$built_apk has versionCode '$built_code', not $version_code; nothing was published"

normalize() { tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'; }
want="$(printf '%s' "$expected_sha256" | normalize)"
digests="$("$apksigner" verify --print-certs "$built_apk" | sed -n 's/.*certificate SHA-256 digest: *//p')" \
  || die "apksigner rejected $built_apk; nothing was published"
[[ -n "$digests" ]] || die "apksigner printed no SHA-256 digest for $built_apk; nothing was published"
while IFS= read -r digest; do
  [[ "$(printf '%s' "$digest" | normalize)" == "$want" ]] \
    || die "$built_apk is signed with SHA-256 $digest, not the KevBox TV key $expected_sha256; nothing was published"
done <<<"$digests"
note "$built_apk: $package $version ($version_code), $size bytes, signed with the KevBox TV key"

local_sha="$(sha256sum "$built_apk" | cut -d' ' -f1)"

# --- Upload. ---

remote_apk="kevbox-home-$version.apk"
remote_root="$(printf '%q' "$web_root")"
note "uploading $remote_apk to $ssh_alias:$web_root"
scp "${scp_port_args[@]}" "$built_apk" "$ssh_alias:/tmp/$remote_apk" || die "scp failed; version.json was not written"
# From here the APK can be in the web root, so the EXIT trap keeps the tag.
uploaded=1
# Install it and print its SHA-256 computed ON THE HOST. kevbox-home-latest.apk moves only after
# version.json is written.
host_sha="$(ssh "${ssh_port_args[@]}" "$ssh_alias" "set -e; sudo install -D -m 644 /tmp/$remote_apk $remote_root/$remote_apk; rm -f /tmp/$remote_apk; cd $remote_root && sha256sum $remote_apk" \
  | awk -v f="$remote_apk" '$2 == f { print $1 }')" || die "installing $remote_apk on $ssh_alias failed; version.json was not written"
[[ -n "$host_sha" ]] || die "no SHA-256 for $remote_apk from $ssh_alias; version.json was not written"
[[ "$host_sha" == "$local_sha" ]] \
  || die "$remote_apk on $ssh_alias has SHA-256 $host_sha, not the built $local_sha; version.json was not written"

# --- version.json. ---

# "after" is taken here, after the upload, when version.json is rendered.
render_args=(--version-code "$version_code" --version-name "$version" --url "$base_url/$remote_apk"
  --sha256 "$host_sha" --notes="$notes")
if (( no_silent == 1 )); then
  render_args+=(--no-silent)
fi
manifest="$(python3 "$manifest_py" render "${render_args[@]}")" || die "could not render version.json"
# Written beside it, then moved, so a box never reads half a file. `$(...)` dropped the final newline.
printf '%s\n' "$manifest" | ssh "${ssh_port_args[@]}" "$ssh_alias" \
  "sudo tee $remote_root/version.json.new >/dev/null && sudo chmod 644 $remote_root/version.json.new && sudo mv -f $remote_root/version.json.new $remote_root/version.json" \
  || die "writing version.json on $ssh_alias failed"
published=1

# --- The stable link, last: it names a release only once version.json does. ---

link_cmd="sudo ln -sfn $remote_apk $remote_root/kevbox-home-latest.apk"
ssh "${ssh_port_args[@]}" "$ssh_alias" "$link_cmd" \
  || die "version.json is written, so the release is out, but kevbox-home-latest.apk was not repointed; run on $ssh_alias: $link_cmd"

if (( no_silent == 1 )); then
  note "--no-silent: no silent block, every box shows the Update chip"
else
  note "silent install open from now on Android 12+ boxes, once nobody is watching"
fi
if ! curl -fsS --max-time 30 "$release_url" | cmp -s - <(printf '%s\n' "$manifest"); then
  note "warning: $release_url does not serve the new version.json yet; check it by hand"
fi
note "published KevBox Home $version (versionCode $version_code, sha256 ${host_sha:0:16}...) at $base_url/$remote_apk"
note "the tag $tag is local; never push it (Couch's release workflow would publish it on GitHub)"
