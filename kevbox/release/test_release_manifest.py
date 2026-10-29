from __future__ import annotations

import contextlib
import io
import json
import os
import tempfile
import time
import unittest

from release_manifest import (
    check_release_args,
    check_version,
    highest_tag_code,
    main,
    published_code,
    render_manifest,
    silent_block,
    version_code,
)

SHA = "ab" * 32

RENDER_ARGS = [
    "render",
    "--version-code", "80101",
    "--version-name", "0.8.1.1",
    "--url", "https://tv.kevbox.dev/home/kevbox-home-0.8.1.1.apk",
    "--sha256", SHA,
    "--notes", "Test",
]


def run_cli(argv: list[str]) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = main(argv)
    return code, out.getvalue(), err.getvalue()


def manifest(silent: dict | None, notes: str = "Test") -> dict:
    return json.loads(render_manifest(
        version_code=80101,
        version_name="0.8.1.1",
        url="https://tv.kevbox.dev/home/x.apk",
        sha256=SHA,
        notes=notes,
        silent=silent,
    ))


class SilentBlockTests(unittest.TestCase):
    def test_the_block_opens_at_publish_time_with_no_early_users(self) -> None:
        self.assertEqual(silent_block(1000), {"after": 1000, "earlyUsers": []})

        code, out, _ = run_cli(RENDER_ARGS + ["--now-ms", "1000"])
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(out)["silent"], {"after": 1000, "earlyUsers": []})

    def test_after_is_now_when_no_time_is_given(self) -> None:
        before = time.time_ns() // 1_000_000
        code, out, _ = run_cli(RENDER_ARGS)
        after = time.time_ns() // 1_000_000
        self.assertEqual(code, 0)
        published = json.loads(out)["silent"]["after"]
        self.assertIsInstance(published, int)
        self.assertGreaterEqual(published, before)
        self.assertLessEqual(published, after)

    def test_no_silent_writes_no_silent_key(self) -> None:
        self.assertNotIn("silent", manifest(None))

        code, out, _ = run_cli(RENDER_ARGS + ["--no-silent", "--now-ms", "1000"])
        self.assertEqual(code, 0)
        self.assertNotIn("silent", json.loads(out))


class RenderTests(unittest.TestCase):
    def test_keys_come_in_the_published_order(self) -> None:
        self.assertEqual(list(manifest(silent_block(0)).keys()),
                         ["versionCode", "versionName", "url", "sha256", "notes", "silent"])

    def test_notes_with_newlines_and_tabs_render_as_one_line(self) -> None:
        notes = manifest(None, notes='Line one\nLine\ttwo\r\n"quoted" \\ $HOME `x`')["notes"]
        self.assertEqual(notes, 'Line one Line two  "quoted" \\ $HOME `x`')

    def test_output_is_pretty_printed_and_ends_with_a_newline(self) -> None:
        text = render_manifest(version_code=1, version_name="1", url="u", sha256=SHA, notes="é", silent=None)
        self.assertTrue(text.endswith("}\n"))
        self.assertIn('  "versionCode": 1,', text)
        self.assertIn('"notes": "é"', text)

    def test_the_sha256_is_written_in_lower_case(self) -> None:
        self.assertEqual(json.loads(render_manifest(
            version_code=1, version_name="1", url="u", sha256="AB" * 32, notes="", silent=None))["sha256"], SHA)

    def test_a_broken_sha256_code_or_name_renders_nothing(self) -> None:
        for args in (["--sha256", "abc"], ["--sha256", ""], ["--version-code", "0"],
                     ["--version-code", "-5"], ["--version-name", " "]):
            with self.subTest(args=args):
                argv = list(RENDER_ARGS)
                index = argv.index(args[0])
                argv[index + 1] = args[1]
                code, out, err = run_cli(argv)
                self.assertEqual((code, out), (1, ""))
                self.assertTrue(err)


class ReleaseArgsTests(unittest.TestCase):
    def check_args(self, *args: str) -> tuple[int, str, str]:
        return run_cli(["check-args", *args])

    def test_valid_argument_lists_pass(self) -> None:
        for args in ((), ("0.8.1.1",), ("0.8.1.1", "Fix", "clock"), ("--no-silent", "0.8.1.1", "notes"),
                     ("0.8.1.1", "-1 crash on start")):
            with self.subTest(args=args):
                self.assertEqual(self.check_args(*args), (0, "", ""))

    def test_no_silent_after_the_version_stops_the_release(self) -> None:
        code, out, err = self.check_args("0.8.1.1", "--no-silent", "notes")
        self.assertEqual((code, out), (1, ""))
        self.assertIn("Argument 2: --no-silent must come first", err)

        code, _, err = self.check_args("0.8.1.1", "notes", "--no-silent")
        self.assertEqual(code, 1)
        self.assertIn("Argument 3", err)

    def test_no_silent_twice_stops_the_release(self) -> None:
        code, _, err = self.check_args("--no-silent", "--no-silent", "0.8.1.1")
        self.assertEqual(code, 1)
        self.assertIn("Argument 2: --no-silent must come first", err)

    def test_any_other_double_dash_argument_stops_the_release(self) -> None:
        for args, position in ((("--no-silnet", "0.8.1.1"), 1), (("0.8.1.1", "--force"), 2),
                               (("--no-silent", "0.8.1.1", "--dry-run"), 3), (("--help",), 1)):
            with self.subTest(args=args):
                code, out, err = self.check_args(*args)
                self.assertEqual((code, out), (1, ""))
                self.assertIn(f"Argument {position}: unknown option", err)

    def test_a_version_starting_with_a_dash_stops_the_release(self) -> None:
        for args, position in ((("-n", "notes"), 1), (("--no-silent", "-0.8.1.1"), 2)):
            with self.subTest(args=args):
                with self.assertRaises(ValueError) as caught:
                    check_release_args(list(args))
                self.assertIn(f"Argument {position}: unknown option", str(caught.exception))


class VersionTests(unittest.TestCase):
    def test_the_code_follows_the_build_formula(self) -> None:
        self.assertEqual(version_code("0.8.1.1"), 80101)
        self.assertEqual(version_code("1.2.3.4"), 1_020_304)
        self.assertEqual(version_code("99.99.99.99"), 99_999_999)

    def test_a_malformed_version_is_refused(self) -> None:
        for bad in ("", "0.8.1", "0.8.1.0", "0.8.1.100", "0.100.1.1", "0.8.01.1", "v0.8.1.1",
                    "0.8.1.1-beta", "0.8.1.1 ", "1.2.3.4.5", "a.b.c.d", "-0.8.1.1"):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    version_code(bad)

    def test_the_highest_tag_wins_like_gradle(self) -> None:
        tags = ["v0.1.0", "v0.8.1", "v0.8.0.3", "v0.7.9.99", "vnext", "v1.2.3.4.5", "v0.8.x", ""]
        self.assertEqual(highest_tag_code(tags), 80100)
        self.assertEqual(highest_tag_code(tags + ["v0.8.1.2"]), 80102)
        self.assertIsNone(highest_tag_code([]))
        self.assertIsNone(highest_tag_code(["junk"]))

    def test_published_code_reads_version_json(self) -> None:
        self.assertEqual(published_code('{"versionCode": 80101, "versionName": "0.8.1.1"}'), 80101)
        for bad in ("", "[]", "not json", '{"versionCode": "80101"}', '{"versionCode": 0}', "{}",
                    '{"versionCode": 1.5}', '{"versionCode": true}'):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    published_code(bad)

    def test_the_version_must_be_above_published_and_tags(self) -> None:
        self.assertEqual(check_version("0.8.1.1", None, None), 80101)
        self.assertEqual(check_version("0.8.1.2", 80101, 80101), 80102)
        for published, tag in ((80101, None), (80102, None), (None, 80101), (None, 90000)):
            with self.subTest(published=published, tag=tag):
                with self.assertRaises(ValueError):
                    check_version("0.8.1.1", published, tag)

    def test_check_version_command(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            published = os.path.join(directory, "version.json")
            tags = os.path.join(directory, "tags")
            with open(published, "w", encoding="utf-8") as handle:
                handle.write('{"versionCode": 80101}')
            with open(tags, "w", encoding="utf-8") as handle:
                handle.write("v0.8.0\nv0.8.1\nv0.8.1.1\n")

            self.assertEqual(run_cli(["check-version", "0.8.1.2", "--published-json", published, "--tags", tags]),
                             (0, "80102\n", ""))
            # First release: no published file.
            self.assertEqual(run_cli(["check-version", "0.8.1.2", "--tags", tags]), (0, "80102\n", ""))

            code, out, err = run_cli(["check-version", "0.8.1.1", "--published-json", published, "--tags", tags])
            self.assertEqual((code, out), (1, ""))
            self.assertIn("not above the published versionCode 80101", err)

            code, out, err = run_cli(["check-version", "0.8.1", "--tags", tags])
            self.assertEqual((code, out), (1, ""))
            self.assertIn("is not a version A.B.C.K", err)

            code, out, _ = run_cli(["check-version", "0.8.1.2", "--published-json", os.path.join(directory, "none")])
            self.assertEqual((code, out), (1, ""))


if __name__ == "__main__":
    unittest.main()
