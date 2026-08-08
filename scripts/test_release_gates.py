#!/usr/bin/env python3
import contextlib
import io
import re
import subprocess
import sys
import unittest
from unittest import mock
from pathlib import Path

try:
    from scripts.check_release_contract import scan_history_batch
    from scripts.verify_apk_metadata import parse_package_line
    from scripts.verify_apk_signature import check_certificate, resolve_expected_sha256
except ModuleNotFoundError:
    from check_release_contract import scan_history_batch
    from verify_apk_metadata import parse_package_line
    from verify_apk_signature import check_certificate, resolve_expected_sha256


EXPECTED = "0123456789abcdef" * 4
APKSIGNER_OUTPUT = f"V2 Signer: certificate SHA-256 digest: {EXPECTED}"
REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
CANONICAL_GRADLE_VERSION = re.compile(r"^\d+\.\d+\.\d{3}:\d+$")


def extract_canonical_gradle_version(output: str) -> str:
    matches = [line for line in output.splitlines() if CANONICAL_GRADLE_VERSION.fullmatch(line)]
    if len(matches) != 1:
        raise ValueError("printVersion output must contain exactly one canonical version line")
    return matches[0]


class CertificateGateTests(unittest.TestCase):
    def test_exact_trusted_certificate_passes(self) -> None:
        self.assertEqual(check_certificate(APKSIGNER_OUTPUT, EXPECTED), EXPECTED)

    def test_wrong_certificate_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "does not match"):
            check_certificate(APKSIGNER_OUTPUT, "f" * 64)

    def test_malformed_trusted_certificate_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "exactly 64"):
            check_certificate(APKSIGNER_OUTPUT, "not-a-fingerprint")

    def test_missing_beta_certificate_secret_is_not_accepted(self) -> None:
        with mock.patch.dict("os.environ", {}, clear=True):
            self.assertIsNone(resolve_expected_sha256("beta"))

    def test_beta_certificate_secret_is_selected_without_printing_or_rewriting_it(self) -> None:
        with mock.patch.dict("os.environ", {"FT8CN_BETA_CERT_SHA256": EXPECTED}, clear=True):
            self.assertEqual(resolve_expected_sha256("beta"), EXPECTED)
        self.assertIsNone(resolve_expected_sha256("debug"))

    def test_package_version_metadata_parser_requires_exact_one_line(self) -> None:
        output = "package: name='com.bg7yoz.ft8cn.beta' versionCode='93007' versionName='0.93.005-beta.7'"
        self.assertEqual(
            parse_package_line(output),
            ("com.bg7yoz.ft8cn.beta", "93007", "0.93.005-beta.7"),
        )
        with self.assertRaisesRegex(ValueError, "exactly one"):
            parse_package_line(output + "\n" + output)

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
    def assert_wrapper_is_executable_before_first_gradle_call(self, workflow_name: str) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / workflow_name).read_text(encoding="utf-8")
        grant_line = "chmod +x ./ft8cn/gradlew"
        grant_offset = workflow.index(grant_line)
        first_gradle_call_offset = next(
            workflow.index(line)
            for line in workflow.splitlines()
            if "./gradlew" in line and "chmod +x" not in line
        )
        self.assertLess(grant_offset, first_gradle_call_offset, workflow_name)

    def assert_full_history_and_remote_tag_target_contract(self, workflow_name: str) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / workflow_name).read_text(encoding="utf-8")

        self.assertIn("fetch-depth: 0", workflow)
        self.assertIn("fetch-tags: true", workflow)
        self.assertIn('head_sha="$(git rev-parse HEAD)"', workflow)
        self.assertIn('git ls-remote --refs origin "refs/tags/${GITHUB_REF_NAME}"', workflow)
        self.assertIn('git ls-remote origin "refs/tags/${GITHUB_REF_NAME}^{}"', workflow)
        self.assertIn('remote_tag_target="${remote_tag_peeled:-$remote_tag_object}"', workflow)
        self.assertIn('"${remote_tag_target}" != "${head_sha}"', workflow)
        self.assertNotIn('git rev-parse "${GITHUB_REF_NAME}"', workflow)
        self.assertIn("./ft8cn/gradlew -p ./ft8cn -q :app:printVersion", workflow)
        self.assertIn("python scripts/check_release_contract.py --history", workflow)

    def assert_version_output_is_filtered(self, workflow_name: str) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / workflow_name).read_text(encoding="utf-8")
        if workflow_name == "android-prerelease.yml":
            version_pattern = "'^[0-9]+\\.[0-9]+\\.[0-9]{3}-beta\\.[0-9]+:[0-9]+$'"
        else:
            version_pattern = "'^[0-9]+\\.[0-9]+\\.[0-9]{3}:[0-9]+$'"

        self.assertIn("./ft8cn/gradlew -p ./ft8cn -q :app:printVersion", workflow)
        self.assertIn(f"grep -E {version_pattern} | tail -n 1 || true", workflow)
        self.assertIn(f"grep -Ec {version_pattern} || true", workflow)
        self.assertIn('"${gradle_version_count}" != "1"', workflow)

    def test_noisy_gradle_stdout_yields_only_the_canonical_version(self) -> None:
        noisy_output = "Downloading https://services.gradle.org/distributions/gradle.zip\n45%\n0.93.005:93005\n"
        self.assertEqual(extract_canonical_gradle_version(noisy_output), "0.93.005:93005")
        with self.assertRaises(ValueError):
            extract_canonical_gradle_version("Downloading\n")
        with self.assertRaises(ValueError):
            extract_canonical_gradle_version("0.93.005:93005\n0.93.005:93005\n")

    def test_formal_tags_include_and_prerelease_tags_exclude(self) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "android-release.yml").read_text(
            encoding="utf-8"
        )
        formal_pattern = '- "v*.*.*"'
        prerelease_exclusion = '- "!v*.*.*-*"'

        self.assertIn(formal_pattern, workflow)
        self.assertIn(prerelease_exclusion, workflow)
        self.assertLess(workflow.index(formal_pattern), workflow.index(prerelease_exclusion))
        self.assert_wrapper_is_executable_before_first_gradle_call("android-release.yml")
        self.assert_full_history_and_remote_tag_target_contract("android-release.yml")
        self.assert_version_output_is_filtered("android-release.yml")
        self.assertIn('refs/heads/release', workflow)
        self.assertIn('"${remote_release_sha}" != "${head_sha}"', workflow)
        self.assertIn(
            'gh release create "${TAG_NAME}" --verify-tag --title "FT8CN ${TAG_NAME}" '
            '--notes-file "${NOTES_FILE}" "${APK_PATH}"',
            workflow,
        )
        self.assertNotIn("gh release upload", workflow)
        self.assertNotIn("--clobber", workflow)

    def test_beta_prerelease_workflow_isolated_and_publishable(self) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "android-prerelease.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn('- "v*.*.*-beta.*"', workflow)
        self.assertIn("^v([0-9]+\\.[0-9]+\\.[0-9]{3})-beta\\.([0-9]+)$", workflow)
        self.assertIn("python scripts/check_release_contract.py --history", workflow)
        self.assertIn("./gradlew :app:testDebugUnitTest --rerun-tasks", workflow)
        self.assertIn("./gradlew :app:packageTestApk --rerun-tasks", workflow)
        self.assertIn("--expect beta", workflow)
        for name in (
            "FT8CN_BETA_KEYSTORE_B64",
            "FT8CN_BETA_STORE_FILE",
            "FT8CN_BETA_STORE_PASSWORD",
            "FT8CN_BETA_KEY_ALIAS",
            "FT8CN_BETA_KEY_PASSWORD",
            "FT8CN_BETA_CERT_SHA256",
        ):
            self.assertIn(name, workflow)
        self.assertIn("0.93.005-beta.7", workflow)
        self.assertIn("93007", workflow)
        self.assertIn("com.bg7yoz.ft8cn.beta", workflow)
        self.assertIn('gh release create "${TAG_NAME}" --prerelease', workflow)
        self.assertIn('notes_file="doc/release-notes/${GITHUB_REF_NAME}.md"', workflow)
        self.assertIn('test -s "${notes_file}"', workflow)
        self.assertNotIn('notes_file="doc/release-notes/v${base_version}.md"', workflow)
        self.assertNotIn("FT8CN_RELEASE_", workflow)
        self.assert_wrapper_is_executable_before_first_gradle_call("android-prerelease.yml")
        self.assert_full_history_and_remote_tag_target_contract("android-prerelease.yml")
        self.assert_version_output_is_filtered("android-prerelease.yml")
        self.assertIn('refs/heads/codex/v0.93.005-integration', workflow)
        self.assertIn('"${remote_branch_sha}" != "${head_sha}"', workflow)
        self.assertIn('GITHUB_SHA: ${{ steps.version.outputs.head_sha }}', workflow)
        self.assertNotIn("GITHUB_SHA:0:7", workflow)

        beta7_notes = REPOSITORY_ROOT / "doc" / "release-notes" / "v0.93.005-beta.7.md"
        self.assertTrue(beta7_notes.is_file())
        beta7_text = beta7_notes.read_text(encoding="utf-8")
        self.assertIn("# FT8CN v0.93.005-beta.7 Pre-release Notes", beta7_text)
        self.assertIn("0.93.005-beta.7", beta7_text)
        self.assertIn("93007", beta7_text)
        self.assertIn("com.bg7yoz.ft8cn.beta", beta7_text)
        self.assertIn("beta-only", beta7_text)
        self.assertIn("卸载", beta7_text)
        self.assertIn("HIL", beta7_text)

        for tag_name in ("v0.93.005-beta.1", "v0.93.005-beta.2", "v0.93.005-beta.3", "v0.93.005-beta.4", "v0.93.005-beta.5"):
            notes = REPOSITORY_ROOT / "doc" / "release-notes" / f"{tag_name}.md"
            self.assertTrue(notes.is_file())
            notes_text = notes.read_text(encoding="utf-8")
            self.assertIn(f"# FT8CN {tag_name} Pre-release Notes", notes_text)
            self.assertIn("com.bg7yoz.ft8cn.beta", notes_text)
            self.assertIn("Android Debug", notes_text)
            self.assertIn("不是正式版", notes_text)
            self.assertIn("不代表真实电台/HIL 已通过", notes_text)

    def test_beta6_notes_are_required_before_local_release_gates_pass(self) -> None:
        notes = REPOSITORY_ROOT / "doc" / "release-notes" / "v0.93.005-beta.6.md"
        self.assertTrue(notes.is_file())
        self.assertGreater(notes.stat().st_size, 0)
        notes_text = notes.read_text(encoding="utf-8")
        self.assertIn("# FT8CN v0.93.005-beta.6 Pre-release Notes", notes_text)
        self.assertIn("com.bg7yoz.ft8cn.beta", notes_text)
        self.assertIn("Android Debug", notes_text)
        self.assertIn("不是正式版", notes_text)
        self.assertIn("不代表真实电台/HIL 已通过", notes_text)

    def test_ci_version_output_is_filtered(self) -> None:
        self.assert_version_output_is_filtered("android.yml")

    def test_formal_workflow_remains_beta_secret_independent(self) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "android-release.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("FT8CN_FORMAL_RELEASE_APPROVED", workflow)
        self.assertIn("FT8CN_RELEASE_CERT_SHA256", workflow)
        self.assertNotIn("FT8CN_BETA_", workflow)


if __name__ == "__main__":
    unittest.main()
