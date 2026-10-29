from __future__ import annotations

import email.utils
import hashlib
import json
import os
import tempfile
import threading
import time
import unittest
import urllib.parse
import urllib.request
from unittest import mock

from updater_test_server import HOUR_MS, _Handler, apk_name, build_manifest, feed_url, make_server, prepare_dir

APK_BYTES = b"not really an apk"


class ManifestTests(unittest.TestCase):
    def setUp(self) -> None:
        handle, self.apk = tempfile.mkstemp(suffix=".apk")
        with os.fdopen(handle, "wb") as out:
            out.write(APK_BYTES)
        self.addCleanup(os.remove, self.apk)

    def test_the_silent_block_opens_the_given_hours_from_now(self) -> None:
        for hours, after in ((-1, 1_000_000 - HOUR_MS), (0, 1_000_000), (24, 1_000_000 + 24 * HOUR_MS)):
            with self.subTest(hours=hours):
                text = json.loads(build_manifest(self.apk, 90000002, hours, 8765, 1_000_000))
                self.assertEqual(text["silent"], {"after": after, "earlyUsers": []})

    def test_no_silent_leaves_the_block_out(self) -> None:
        self.assertNotIn("silent", json.loads(build_manifest(self.apk, 90000002, None, 8765, 1_000_000)))

    def test_the_apk_url_is_on_the_feed_host_and_the_sha256_matches(self) -> None:
        text = json.loads(build_manifest(self.apk, 90000002, -1, 9000, 1_000_000))
        url = urllib.parse.urlsplit(text["url"])
        feed = urllib.parse.urlsplit(feed_url(9000))
        self.assertEqual((url.scheme, url.hostname, url.port), (feed.scheme, feed.hostname, feed.port))
        self.assertEqual(feed_url(9000), "http://10.0.2.2:9000/home/version.json")
        self.assertEqual(url.path, "/home/" + apk_name(90000002))
        self.assertEqual(text["sha256"], hashlib.sha256(APK_BYTES).hexdigest())
        self.assertEqual(text["versionCode"], 90000002)


class ServerTests(unittest.TestCase):
    def test_serves_version_json_and_the_apk_with_a_date_header(self) -> None:
        handle, apk = tempfile.mkstemp(suffix=".apk")
        with os.fdopen(handle, "wb") as out:
            out.write(APK_BYTES)
        self.addCleanup(os.remove, apk)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        text = prepare_dir(directory.name, apk, 90000002, -1, 8765, 1_000_000)

        quiet = mock.patch.object(_Handler, "log_message")
        quiet.start()
        self.addCleanup(quiet.stop)
        server = make_server(directory.name, 0, "127.0.0.1")
        self.addCleanup(server.server_close)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.shutdown)
        base = f"http://127.0.0.1:{server.server_address[1]}"

        with urllib.request.urlopen(f"{base}/home/version.json", timeout=5) as response:
            self.assertEqual(response.read().decode("utf-8"), text)
            served = email.utils.parsedate_to_datetime(response.headers["Date"]).timestamp()
            self.assertLess(abs(served - time.time()), 5)
            self.assertEqual(response.headers["Cache-Control"], "no-store")
        with urllib.request.urlopen(f"{base}/home/{apk_name(90000002)}", timeout=5) as response:
            self.assertEqual(response.read(), APK_BYTES)


if __name__ == "__main__":
    unittest.main()
