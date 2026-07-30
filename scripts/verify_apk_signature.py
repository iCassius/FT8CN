#!/usr/bin/env python3
"""Verify an APK with the Android SDK apksigner and classify its certificate."""

from __future__ import annotations

import argparse
import os
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--expect", choices=("debug", "release"), required=True)
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
    if args.expect == "release" and is_debug:
        raise SystemExit("formal release APK is signed by the Android debug certificate")
    if "certificate SHA-256 digest:" not in output:
        raise SystemExit("apksigner output did not include a certificate digest")
    print(output.strip())


if __name__ == "__main__":
    main()
