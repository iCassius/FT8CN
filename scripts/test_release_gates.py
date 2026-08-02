#!/usr/bin/env python3
import contextlib
import io
import subprocess
import sys
import unittest
from pathlib import Path

try:
    from scripts.check_release_contract import scan_history_batch
    from scripts.verify_apk_signature import check_certificate
except ModuleNotFoundError:
    from check_release_contract import scan_history_batch
    from verify_apk_signature import check_certificate


EXPECTED = "0123456789abcdef" * 4
APKSIGNER_OUTPUT = f"V2 Signer: certificate SHA-256 digest: {EXPECTED}"
REPOSITORY_ROOT = Path(__file__).resolve().parent.parent


class CertificateGateTests(unittest.TestCase):
    def test_exact_trusted_certificate_passes(self) -> None:
        self.assertEqual(check_certificate(APKSIGNER_OUTPUT, EXPECTED), EXPECTED)

    def test_wrong_certificate_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "does not match"):
            check_certificate(APKSIGNER_OUTPUT, "f" * 64)

    def test_malformed_trusted_certificate_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "exactly 64"):
            check_certificate(APKSIGNER_OUTPUT, "not-a-fingerprint")

    def test_history_scan_consumes_commit_tree_and_tag_before_blob(self) -> None:
        object_ids = ["a" * 40, "b" * 40, "c" * 40, "d" * 40]

        def record(object_id: str, object_type: str, body: bytes) -> bytes:
            return f"{object_id} {object_type} {len(body)}\n".encode() + body + b"\n"

        output = b"".join((
            record(object_ids[0], "commit", b"tree deadbeef\nauthor Test <test@example.com> 0 +0000\n"),
            record(object_ids[1], "tree", b"100644 blob deadbeef\ttracked.txt\n"),
            record(object_ids[2], "tag", b"object deadbeef\ntype commit\ntag v0.0.001\n"),
            record(object_ids[3], "blob", b"token=" + ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "abcdef").encode() + b"\n"),
        ))

        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr), self.assertRaises(SystemExit):
            scan_history_batch(object_ids, output)
        self.assertIn("possible secret material", stderr.getvalue())

    @unittest.skipIf(__name__ == "__main__", "the direct child must not recursively spawn itself")
    def test_script_can_run_directly(self) -> None:
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve())],
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


class ReleaseWorkflowTagFilterTests(unittest.TestCase):
    def test_formal_tags_include_and_prerelease_tags_exclude(self) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "android-release.yml").read_text(
            encoding="utf-8"
        )
        formal_pattern = '- "v*.*.*"'
        prerelease_exclusion = '- "!v*.*.*-*"'

        self.assertIn(formal_pattern, workflow)
        self.assertIn(prerelease_exclusion, workflow)
        self.assertLess(workflow.index(formal_pattern), workflow.index(prerelease_exclusion))

    def test_beta_prerelease_workflow_isolated_and_publishable(self) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "android-prerelease.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn('- "v*.*.*-beta.*"', workflow)
        self.assertIn("^v([0-9]+\\.[0-9]+\\.[0-9]{3})-beta\\.([0-9]+)$", workflow)
        self.assertIn("python scripts/check_release_contract.py --history", workflow)
        self.assertIn("./gradlew :app:testDebugUnitTest --rerun-tasks", workflow)
        self.assertIn("./gradlew :app:packageTestApk --rerun-tasks", workflow)
        self.assertIn("--expect debug", workflow)
        self.assertIn('gh release create "${TAG_NAME}" --prerelease', workflow)
        self.assertIn('notes_file="doc/release-notes/v${base_version}.md"', workflow)
        self.assertNotIn("FT8CN_RELEASE_", workflow)


if __name__ == "__main__":
    unittest.main()
