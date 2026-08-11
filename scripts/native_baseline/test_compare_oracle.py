import copy
import unittest

from scripts.native_baseline.compare_oracle import SCHEMA, compare_snapshots


def snapshot():
    return {
        "schema": SCHEMA,
        "metadata": {
            "native_abi": "x86_64",
            "oracle_inputs": "synthetic-public-test-v1",
            "build_fingerprint": "ignored",
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
    def test_metadata_and_small_float_differences_are_tolerated(self):
        expected = snapshot()
        actual = copy.deepcopy(expected)
        actual["metadata"]["build_fingerprint"] = "candidate"
        actual["pack_encode"]["signal_profile"]["raw_float32_le_sha256"] = "b"
        actual["pack_encode"]["signal_profile"]["rms"] += 1e-6

        errors, notices = compare_snapshots(expected, actual)

        self.assertEqual([], errors)
        self.assertEqual(1, len(notices))

    def test_integer_and_large_float_changes_fail(self):
        expected = snapshot()
        actual = copy.deepcopy(expected)
        actual["hashes"]["K1ABC"]["hash10"] = 2
        actual["pack_encode"]["signal_profile"]["rms"] = 0.5

        errors, _ = compare_snapshots(expected, actual)

        self.assertEqual(2, len(errors))

    def test_abi_mismatch_fails(self):
        expected = snapshot()
        actual = copy.deepcopy(expected)
        actual["metadata"]["native_abi"] = "arm64-v8a"

        errors, _ = compare_snapshots(expected, actual)

        self.assertTrue(any("native_abi" in error for error in errors))

    def test_fft_integer_has_narrow_tolerance_but_hashes_remain_exact(self):
        expected = snapshot()
        actual = copy.deepcopy(expected)
        actual["spectrum"]["fft"][0] += 1

        errors, _ = compare_snapshots(expected, actual)

        self.assertEqual([], errors)
        actual["spectrum"]["fft"][0] += 1
        errors, _ = compare_snapshots(expected, actual)
        self.assertEqual(1, len(errors))

    def test_strict_float_digest_fails(self):
        expected = snapshot()
        actual = copy.deepcopy(expected)
        actual["pack_encode"]["signal_profile"]["raw_float32_le_sha256"] = "b"

        errors, _ = compare_snapshots(expected, actual, strict_float_digests=True)

        self.assertEqual(1, len(errors))


if __name__ == "__main__":
    unittest.main()
