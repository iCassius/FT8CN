#!/usr/bin/env python3
"""Verify APK package/version metadata and zip alignment with Android SDK tools."""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
from pathlib import Path


def find_sdk_tool(name: str) -> Path:
    configured = os.environ.get(name.upper())
    if configured:
        path = Path(configured)
        if path.is_file():
            return path
    configured_path = shutil.which(name)
    if configured_path:
        return Path(configured_path)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise FileNotFoundError(f"Set {name.upper()}, ANDROID_HOME, or ANDROID_SDK_ROOT")
    build_tools = Path(sdk) / "build-tools"
    candidates = []
    if build_tools.is_dir():
        for version_dir in build_tools.iterdir():
            if os.name == "nt":
                suffix = ".bat" if name == "apksigner" else ".exe"
            else:
                suffix = ""
            candidate = version_dir / f"{name}{suffix}"
            if candidate.is_file():
                candidates.append(candidate)
    if not candidates:
        raise FileNotFoundError(f"No {name} found below {build_tools}")
    return sorted(candidates, key=lambda path: path.parent.name, reverse=True)[0]


def dump_badging(apk: Path, aapt2: Path) -> str:
    result = subprocess.run(
        [str(aapt2), "dump", "badging", str(apk)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        shell=False,
    )
    output = f"{result.stdout}\n{result.stderr}"
    if result.returncode != 0:
        raise ValueError(f"aapt2 rejected {apk}")
    return output


def parse_package_line(output: str) -> tuple[str, str, str]:
    matches = re.findall(
        r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'",
        output,
        re.MULTILINE,
    )
    if len(matches) != 1:
        raise ValueError("aapt2 output must contain exactly one package metadata line")
    return matches[0]


def verify_zipalign(apk: Path, zipalign: Path) -> None:
    result = subprocess.run(
        [str(zipalign), "-c", "-v", "4", str(apk)],
        capture_output=True,
        text=True,
        shell=False,
    )
    if result.returncode != 0:
        raise ValueError(f"zipalign rejected {apk}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--require-zipalign", action="store_true")
    args = parser.parse_args()
    if not args.apk.is_file():
        raise SystemExit(f"APK does not exist: {args.apk}")
    try:
        metadata = parse_package_line(dump_badging(args.apk, find_sdk_tool("aapt2")))
        expected = (args.package, args.version_code, args.version_name)
        if metadata != expected:
            raise ValueError(f"APK metadata {metadata!r} does not match expected {expected!r}")
        if args.require_zipalign:
            verify_zipalign(args.apk, find_sdk_tool("zipalign"))
    except (FileNotFoundError, ValueError) as exc:
        raise SystemExit(str(exc)) from exc
    print(
        f"apk_metadata_verified=true package={metadata[0]} "
        f"versionName={metadata[2]} versionCode={metadata[1]} zipalign={args.require_zipalign}"
    )


if __name__ == "__main__":
    main()
