#!/usr/bin/env python3
"""The checks and the version.json behind release.sh, kept apart so each runs alone, with no ssh,
no Gradle and no network:

    python3 kevbox/release/release_manifest.py check-args <release.sh arguments...>
    python3 kevbox/release/release_manifest.py check-version <A.B.C.K> \
        [--published-json PATH] [--tags PATH]
    python3 kevbox/release/release_manifest.py render --version-code N --version-name V --url U \
        --sha256 S --notes TEXT [--no-silent] [--now-ms MS]

check-args refuses a misplaced --no-silent or an unknown option (see check_release_args).
check-version prints the version's versionCode, or refuses a malformed version or one that is not
above both the published version.json and the highest v* tag already in HEAD.
render prints version.json. Copied from KevBox TV's scripts/release_manifest.py, minus the
compile profiles and the early-user list: Home has neither, so its silent block opens at publish
time with "earlyUsers": [].
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
import unicodedata

# A.B.C.K: A, B and C in 0..99, K in 1..99, no leading zeros, so each tag has one spelling and
# K = 0 never collides with Couch's own vA.B.C tag (AGENTS.md: after Couch X.Y.Z comes X.Y.Z.1).
_VERSION = re.compile(r"^(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)\.([1-9][0-9]?)$")
_HEX64 = re.compile(r"^[0-9a-fA-F]{64}$")


def code_of(parts: list[int]) -> int:
    """versionCode for [A, B, C, K], the formula app/build.gradle.kts uses."""
    a, b, c, k = parts
    return a * 1_000_000 + b * 10_000 + c * 100 + k


def version_code(version: str) -> int:
    """The versionCode of a release version A.B.C.K; ValueError when it is not one."""
    match = _VERSION.match(version)
    if not match:
        raise ValueError(f"'{version}' is not a version A.B.C.K (A, B, C 0-99, K 1-99, no leading zeros)")
    return code_of([int(group) for group in match.groups()])


def highest_tag_code(tags: list[str]) -> int | None:
    """The versionCode app/build.gradle.kts builds from these `git tag --merged HEAD` lines.

    Same rule: vX.Y.Z or vX.Y.Z.N, a three-part tag counts as .0, anything else is skipped, and the
    highest four numbers win. None when no tag parses.
    """
    best: list[int] | None = None
    for line in tags:
        tag = line.strip()
        if not tag.startswith("v"):
            continue
        parts = tag[1:].split(".")
        if len(parts) > 4 or not all(part.isascii() and part.isdigit() for part in parts):
            continue
        numbers = [int(part) for part in parts] + [0] * (4 - len(parts))
        if best is None or numbers > best:
            best = numbers
    return None if best is None else code_of(best)


def published_code(text: str) -> int:
    """versionCode of the version.json tv.kevbox.dev serves now; ValueError when unreadable."""
    try:
        code = json.loads(text).get("versionCode")
    except (json.JSONDecodeError, AttributeError) as error:
        raise ValueError(f"the published version.json is not a JSON object ({error})") from None
    if type(code) is not int or code <= 0:
        raise ValueError("the published version.json has no positive whole versionCode")
    return code


def check_version(version: str, published: int | None, tag_code: int | None) -> int:
    """version's versionCode, when it is above the published one and the highest tag in HEAD.

    published None means nothing is published yet (version.json answered 404). The tag rule keeps
    the new tag the highest in HEAD, or Gradle would build an older or newer number than this one.
    """
    code = version_code(version)
    if published is not None and code <= published:
        raise ValueError(f"{version} (code {code}) is not above the published versionCode {published}")
    if tag_code is not None and code <= tag_code:
        raise ValueError(f"{version} (code {code}) is not above the highest v* tag in HEAD (code {tag_code})")
    return code


def check_release_args(args: list[str]) -> None:
    """Refuse release.sh arguments that would publish something other than what was meant.

    `--no-silent` counts only as the first argument. Anywhere else, any other argument starting
    with `--`, or a version starting with `-` raises ValueError naming the argument's 1-based
    position: silent install is the default, so a misplaced flag would publish a release that
    installs by itself.
    """
    skip = 1 if args[:1] == ["--no-silent"] else 0
    for position, arg in enumerate(args[skip:], start=skip + 1):
        if arg == "--no-silent":
            raise ValueError(f"Argument {position}: --no-silent must come first, before <A.B.C.K>")
        if arg.startswith("--") or (position == skip + 1 and arg.startswith("-")):
            raise ValueError(f"Argument {position}: unknown option '{arg}'")


def silent_block(now_ms: int) -> dict:
    """Home's silent block: open from publish time, no early users (Home has no sign-in)."""
    return {"after": now_ms, "earlyUsers": []}


def _one_line(text: str) -> str:
    # Any control character (newline, tab, CR, ...) becomes a space.
    return "".join(" " if unicodedata.category(ch) == "Cc" else ch for ch in text)


def render_manifest(*, version_code: int, version_name: str, url: str, sha256: str, notes: str,
                    silent: dict | None) -> str:
    """version.json as published, keys in this order; ValueError on a bad code, name or sha256."""
    if type(version_code) is not int or version_code <= 0:
        raise ValueError("versionCode must be a positive whole number")
    if not version_name.strip():
        raise ValueError("versionName must not be blank")
    if not _HEX64.match(sha256):
        raise ValueError("sha256 must be 64 hex characters")
    manifest = {
        "versionCode": version_code,
        "versionName": version_name,
        "url": url,
        "sha256": sha256.lower(),
        "notes": _one_line(notes),
    }
    if silent is not None:
        manifest["silent"] = silent
    return json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"


def now_ms() -> int:
    return time.time_ns() // 1_000_000


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Checks and version.json for KevBox Home's release.sh.")
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("check-version", help="print the versionCode of A.B.C.K, or refuse it")
    check.add_argument("version")
    check.add_argument("--published-json", help="the version.json tv.kevbox.dev serves now; omit when it is 404")
    check.add_argument("--tags", help="file with `git tag --merged HEAD --list 'v*'` output")
    render = commands.add_parser("render", help="print version.json to stdout")
    render.add_argument("--version-code", type=int, required=True)
    render.add_argument("--version-name", required=True)
    render.add_argument("--url", required=True)
    render.add_argument("--sha256", required=True)
    render.add_argument("--notes", required=True)
    render.add_argument("--no-silent", action="store_true")
    render.add_argument("--now-ms", type=int)
    return parser


def _read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def main(argv: list[str] | None = None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    # Takes release.sh's own arguments verbatim, flags included, so argparse stays out of it.
    if argv[:1] == ["check-args"]:
        try:
            check_release_args(argv[1:])
        except ValueError as error:
            print(error, file=sys.stderr)
            return 1
        return 0

    args = _parser().parse_args(argv)
    try:
        if args.command == "check-version":
            published = published_code(_read(args.published_json)) if args.published_json else None
            tag_code = highest_tag_code(_read(args.tags).splitlines()) if args.tags else None
            print(check_version(args.version, published, tag_code))
            return 0

        silent = None if args.no_silent else silent_block(args.now_ms if args.now_ms is not None else now_ms())
        sys.stdout.write(render_manifest(
            version_code=args.version_code,
            version_name=args.version_name,
            url=args.url,
            sha256=args.sha256,
            notes=args.notes,
            silent=silent,
        ))
        return 0
    except (ValueError, OSError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
