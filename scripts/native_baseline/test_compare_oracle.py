import copy
import math
from pathlib import Path
import tempfile
import unittest

from scripts.native_baseline.compare_oracle import SCHEMA, compare_snapshots, load_snapshot


def snapshot():
    return {
        "schema": SCHEMA,
        "metadata": {
            "application_id": "com.bg7yoz.ft8cn.beta",
            "version_name": "0.93.005-beta",
            "version_code": 93005,
            "oracle_inputs": "synthetic-public-test-v1",
            "environment": {"native_abi": "x86_64", "page_size": 16384},
            "source": {
                "git_commit": "1" * 40,
                "git_dirty": False,
                "build_variant": "debug",
                "native_candidate": True,
            },
            "artifacts": {
                "target_apk_sha256": "2" * 64,
                "test_apk_sha256": "3" * 64,
                "native_library_sha256": "4" * 64,
            },
            "non_authoritative_environment": {"build_fingerprint": "ignored"},
        },
        "hashes": {"K1ABC": {"hash10": 1}},
        "pack_encode": {
            "signal_profile": {
                "raw_float32_le_sha256": "a",
                "rms": 0.25,
                "probes": [{"index": 0, "value": 0.125}],
            }
        },
        "spectrum": {"fft": [10]},
    }


class CompareOracleTest(unittest.TestCase):
    def candidate(self):
        actual = copy.deepcopy(snapshot())
        actual["metadata"]["source"]["git_commit"] = "5" * 40
        actual["metadata"]["artifacts"]["native_library_sha256"] = "6" * 64
        return actual

    def test_metadata_and_small_float_differences_are_tolerated(self):
        expected = snapshot()
        actual = self.candidate()
        actual["metadata"]["non_authoritative_environment"]["build_fingerprint"] = "candidate"
        actual["pack_encode"]["signal_profile"]["raw_float32_le_sha256"] = "b"
        actual["pack_encode"]["signal_profile"]["rms"] += 1e-6

        errors, notices = compare_snapshots(expected, actual)

        self.assertEqual([], errors)
        self.assertEqual(1, len(notices))

    def test_integer_and_large_float_changes_fail(self):
        expected = snapshot()
        actual = self.candidate()
        actual["hashes"]["K1ABC"]["hash10"] = 2
        actual["pack_encode"]["signal_profile"]["rms"] = 0.5

        errors, _ = compare_snapshots(expected, actual)

        self.assertEqual(2, len(errors))

    def test_abi_mismatch_fails(self):
        expected = snapshot()
        actual = self.candidate()
        actual["metadata"]["environment"]["native_abi"] = "arm64-v8a"

        errors, _ = compare_snapshots(expected, actual)

        self.assertTrue(any("native_abi" in error for error in errors))

    def test_fft_integer_has_narrow_tolerance_but_hashes_remain_exact(self):
        expected = snapshot()
        actual = self.candidate()
        actual["spectrum"]["fft"][0] += 1

        errors, _ = compare_snapshots(expected, actual)

        self.assertEqual([], errors)
        actual["spectrum"]["fft"][0] += 1
        errors, _ = compare_snapshots(expected, actual)
        self.assertEqual(1, len(errors))

    def test_strict_float_digest_fails(self):
        expected = snapshot()
        actual = self.candidate()
        actual["pack_encode"]["signal_profile"]["raw_float32_le_sha256"] = "b"

        errors, _ = compare_snapshots(expected, actual, strict_float_digests=True)

        self.assertEqual(1, len(errors))

    def test_same_native_library_and_commit_fail_by_default(self):
        errors, _ = compare_snapshots(snapshot(), copy.deepcopy(snapshot()))

        self.assertTrue(any("git_commit" in error for error in errors))
        self.assertTrue(any("native_library_sha256" in error for error in errors))

    def test_candidate_provenance_is_required(self):
        actual = self.candidate()
        del actual["metadata"]["source"]["native_candidate"]

        errors, _ = compare_snapshots(snapshot(), actual)

        self.assertTrue(any("native_candidate" in error for error in errors))

    def test_non_finite_tolerances_fail(self):
        expected = snapshot()
        actual = self.candidate()

        for value in (math.inf, -math.inf, math.nan):
            errors, _ = compare_snapshots(expected, actual, absolute_tolerance=value)
            self.assertTrue(any("absolute_tolerance" in error for error in errors))

    def test_non_finite_json_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text('{"value": NaN}', encoding="utf-8")
            with self.assertRaises(ValueError):
                load_snapshot(path)

    def test_metadata_type_and_missing_fields_fail_without_crashing(self):
        expected = snapshot()
        actual = self.candidate()
        actual["metadata"] = []
        errors, _ = compare_snapshots(expected, actual)
        self.assertTrue(any("candidate.metadata" in error for error in errors))

        actual = self.candidate()
        del actual["metadata"]["artifacts"]["test_apk_sha256"]
        errors, _ = compare_snapshots(expected, actual)
        self.assertTrue(any("test_apk_sha256" in error for error in errors))

    def test_array_length_mismatch_fails(self):
        expected = snapshot()
        actual = self.candidate()
        actual["pack_encode"]["signal_profile"]["probes"].append(
            {"index": 1, "value": 0.25}
        )
        errors, _ = compare_snapshots(expected, actual)
        self.assertTrue(any("expected length" in error for error in errors))

    def test_page_size_and_dirty_source_fail(self):
        expected = snapshot()
        actual = self.candidate()
        actual["metadata"]["environment"]["page_size"] = 4096
        actual["metadata"]["source"]["git_dirty"] = True
        errors, _ = compare_snapshots(expected, actual)
        self.assertTrue(any("page_size" in error for error in errors))
        self.assertTrue(any("git_dirty" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
