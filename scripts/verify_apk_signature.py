#!/usr/bin/env python3
"""Verify an APK with the Android SDK apksigner and classify its certificate."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path


def find_apksigner() -> Path:
    configured = os.environ.get("APKSIGNER")
    if configured:
        path = Path(configured)
        if path.is_file():
            return path
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise FileNotFoundError("Set APKSIGNER, ANDROID_HOME, or ANDROID_SDK_ROOT")
    build_tools = Path(sdk) / "build-tools"
    candidates = sorted(
        [p / ("apksigner.bat" if os.name == "nt" else "apksigner") for p in build_tools.iterdir()]
        if build_tools.is_dir()
        else [],
        key=lambda p: p.parent.name,
        reverse=True,
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(f"No apksigner found below {build_tools}")


def normalize_sha256(value: str) -> str:
    normalized = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", normalized):
        raise ValueError("certificate SHA-256 must be exactly 64 hexadecimal characters")
    return normalized


def check_certificate(output: str, expected_sha256: str | None) -> str:
    matches = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)", output)
    if not matches:
        raise ValueError("apksigner output did not include a certificate SHA-256 digest")
    normalized = {normalize_sha256(match) for match in matches}
    if len(normalized) != 1:
        raise ValueError("APK contains more than one certificate SHA-256 digest")
    actual = normalized.pop()
    if expected_sha256 is not None and actual != normalize_sha256(expected_sha256):
        raise ValueError("APK certificate does not match the expected trusted certificate")
    return actual


def resolve_expected_sha256(mode: str, explicit: str | None = None) -> str | None:
    if explicit is not None:
        return explicit
    if mode == "beta":
        return os.environ.get("FT8CN_BETA_CERT_SHA256")
    if mode == "release":
        return os.environ.get("FT8CN_RELEASE_CERT_SHA256")
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--expect", choices=("debug", "beta", "release"), required=True)
    parser.add_argument(
        "--expected-sha256",
        help="Trusted certificate SHA-256; required for --expect beta/release.",
    )
    args = parser.parse_args()
    if not args.apk.is_file():
        raise SystemExit(f"APK does not exist: {args.apk}")
    try:
        apksigner = find_apksigner()
    except FileNotFoundError as exc:
        raise SystemExit(str(exc)) from exc
    command = [str(apksigner), "verify", "--verbose", "--print-certs", str(args.apk)]
    result = subprocess.run(command, capture_output=True, text=True, shell=os.name == "nt")
    output = f"{result.stdout}\n{result.stderr}"
    if result.returncode != 0:
        print(output, file=sys.stderr)
        raise SystemExit(f"apksigner rejected {args.apk}")
    is_debug = "CN=Android Debug" in output
    if args.expect == "debug" and not is_debug:
        raise SystemExit("TEST/BETA APK is not signed by the Android debug certificate")
    if args.expect in {"beta", "release"} and is_debug:
        label = "beta prerelease" if args.expect == "beta" else "formal release"
        raise SystemExit(f"{label} APK is signed by the Android debug certificate")
    if args.expected_sha256 is None:
        args.expected_sha256 = resolve_expected_sha256(args.expect)
    if args.expect in {"beta", "release"} and not args.expected_sha256:
        environment_name = "FT8CN_BETA_CERT_SHA256" if args.expect == "beta" else "FT8CN_RELEASE_CERT_SHA256"
        raise SystemExit(f"{args.expect} verification requires {environment_name} or --expected-sha256")
    try:
        actual_sha256 = check_certificate(output, args.expected_sha256)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc
    print(f"certificate_sha256_verified=true mode={args.expect}")


if __name__ == "__main__":
    main()
