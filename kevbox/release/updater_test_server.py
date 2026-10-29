#!/usr/bin/env python3
r"""Serve a test version.json and APK to the kevbox_tv emulator for the silent-update check (AC-15).

    python3 kevbox/release/updater_test_server.py --apk PATH --version-code N \
        (--after-hours H | --no-silent) [--port 8765] [--bind 0.0.0.0]

The files sit under /home/, like tv.kevbox.dev/home/: the emulator reads
http://10.0.2.2:<port>/home/version.json, which is the whole URL a debug build takes as
-Pkevbox.updateFeed. The APK url in version.json is on that same host, as VersionJson.trusted
requires of a test feed. version.json comes from release_manifest.render_manifest, so it has the
shape release.sh publishes; its silent block opens H hours from now (negative: already open), or
is left out with --no-silent. Every response carries the standard Date header, which the app
takes as the server's clock (AC-2).

It never calls adb, ssh or Gradle. The emulator steps are in docs/kevbox/releasing.md.
"""
from __future__ import annotations

import argparse
import functools
import hashlib
import http.server
import os
import shutil
import signal
import sys
import tempfile

from release_manifest import now_ms, render_manifest

DEFAULT_BIND = "0.0.0.0"
# The emulator reaches the host's loopback as 10.0.2.2.
EMULATOR_HOST = "10.0.2.2"
DEFAULT_PORT = 8765
FOLDER = "home"
HOUR_MS = 3_600_000


def apk_name(version_code: int) -> str:
    return f"kevbox-home-test-{version_code}.apk"


def feed_url(port: int) -> str:
    """The -Pkevbox.updateFeed value for a debug build that reads this server."""
    return f"http://{EMULATOR_HOST}:{port}/{FOLDER}/version.json"


def _sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_manifest(apk_path: str, version_code: int, after_hours: float | None, port: int, now: int) -> str:
    """version.json for the APK at apk_path; no silent block when after_hours is None."""
    silent = None
    if after_hours is not None:
        # Built here rather than with silent_block, which opens at publish time.
        silent = {"after": now + round(after_hours * HOUR_MS), "earlyUsers": []}
    return render_manifest(
        version_code=version_code,
        version_name=f"test-{version_code}",
        url=f"http://{EMULATOR_HOST}:{port}/{FOLDER}/{apk_name(version_code)}",
        sha256=_sha256(apk_path),
        notes=f"Emulator test build {version_code}",
        silent=silent,
    )


def prepare_dir(directory: str, apk_path: str, version_code: int, after_hours: float | None,
                port: int, now: int) -> str:
    """Place the APK and version.json under directory/home/. Returns the version.json text."""
    folder = os.path.join(directory, FOLDER)
    os.makedirs(folder, exist_ok=True)
    target = os.path.join(folder, apk_name(version_code))
    try:
        os.link(apk_path, target)
    except OSError:
        shutil.copyfile(apk_path, target)
    text = build_manifest(target, version_code, after_hours, port, now)
    with open(os.path.join(folder, "version.json"), "w", encoding="utf-8") as handle:
        handle.write(text)
    return text


class _Handler(http.server.SimpleHTTPRequestHandler):
    # send_response already adds Date (RFC 7231, GMT). no-store keeps every check a fresh read.
    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


def make_server(directory: str, port: int, bind: str = DEFAULT_BIND) -> http.server.ThreadingHTTPServer:
    handler = functools.partial(_Handler, directory=directory)
    return http.server.ThreadingHTTPServer((bind, port), handler)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Serve a test version.json and APK for the kevbox_tv emulator's silent-update check.",
        epilog="Emulator steps: docs/kevbox/releasing.md.",
    )
    parser.add_argument("--apk", required=True, help="APK to serve")
    parser.add_argument("--version-code", type=int, required=True, help="versionCode of that APK")
    silent = parser.add_mutually_exclusive_group(required=True)
    silent.add_argument("--after-hours", type=float,
                        help="silent.after is now plus this many hours; negative means already open")
    silent.add_argument("--no-silent", action="store_true", help="publish no silent block")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"default {DEFAULT_PORT}")
    parser.add_argument("--bind", default=DEFAULT_BIND, help=f"address to listen on, default {DEFAULT_BIND}")
    return parser


def _stop_on_sigterm(_signum, _frame) -> None:
    # `kill <pid>` sends SIGTERM; turn it into a normal exit so the temp dir gets removed.
    raise SystemExit(0)


def main(argv: list[str] | None = None) -> int:
    parser = _parser()
    args = parser.parse_args(argv)
    if not os.path.isfile(args.apk):
        parser.error(f"--apk {args.apk} is not a file")
    if args.version_code <= 0:
        parser.error("--version-code must be a positive whole number")

    directory = tempfile.mkdtemp(prefix="kevbox-home-updater-test-")
    try:
        after_hours = None if args.no_silent else args.after_hours
        text = prepare_dir(directory, args.apk, args.version_code, after_hours, args.port, now_ms())
        server = make_server(directory, args.port, args.bind)
        signal.signal(signal.SIGTERM, _stop_on_sigterm)
        print(text, end="", flush=True)
        print(f"Serving {directory} on http://{args.bind}:{args.port}/", flush=True)
        print(f"Feed URL for the debug build: -Pkevbox.updateFeed={feed_url(args.port)}", flush=True)
        print("Ctrl-C to stop.", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            server.server_close()
    finally:
        shutil.rmtree(directory, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
