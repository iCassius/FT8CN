#!/usr/bin/env python3
import unittest

from scripts.verify_apk_signature import check_certificate


EXPECTED = "0123456789abcdef" * 4
APKSIGNER_OUTPUT = f"V2 Signer: certificate SHA-256 digest: {EXPECTED}"


class CertificateGateTests(unittest.TestCase):
    def test_exact_trusted_certificate_passes(self) -> None:
        self.assertEqual(check_certificate(APKSIGNER_OUTPUT, EXPECTED), EXPECTED)

    def test_wrong_certificate_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "does not match"):
            check_certificate(APKSIGNER_OUTPUT, "f" * 64)

    def test_malformed_trusted_certificate_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "exactly 64"):
            check_certificate(APKSIGNER_OUTPUT, "not-a-fingerprint")


if __name__ == "__main__":
    unittest.main()
