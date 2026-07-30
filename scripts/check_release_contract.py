#!/usr/bin/env python3
"""Check version, release workflow, and tracked-artifact hygiene before a build."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def tracked_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True
    )
    return [ROOT / item for item in result.stdout.decode().split("\0") if item]


def main() -> None:
    properties_path = ROOT / "ft8cn" / "gradle.properties"
    values = read_properties(properties_path)
    version = values.get("ft8cn.versionName", "")
    code = values.get("ft8cn.versionCode", "")
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d{3})", version)
    if not match:
        fail(f"ft8cn.versionName must be MAJOR.MINOR.BUILD with three build digits: {version!r}")
    expected_code = int(match.group(1)) * 10000 + int(match.group(2)) * 1000 + int(match.group(3))
    if code != str(expected_code):
        fail(f"ft8cn.versionCode={code!r} does not match {version!r}; expected {expected_code}")

    app_gradle = (ROOT / "ft8cn" / "app" / "build.gradle").read_text(encoding="utf-8")
    if "versionName versionNameValue" not in app_gradle or "versionCode versionCodeValue.toInteger()" not in app_gradle:
        fail("app/build.gradle is not consuming the canonical gradle.properties version")
    if re.search(r"release\s*\{[^}]*signingConfig\s+signingConfigs\.debug", app_gradle, re.S):
        fail("release build type may not use the debug signing config")

    ci = (ROOT / ".github" / "workflows" / "android.yml").read_text(encoding="utf-8")
    release_ci = (ROOT / ".github" / "workflows" / "android-release.yml").read_text(encoding="utf-8")
    if "java-version: '17'" not in ci and 'java-version: "17"' not in ci:
        fail("ordinary CI must use JDK 17 for AGP 9")
    if "java-version: '17'" not in release_ci and 'java-version: "17"' not in release_ci:
        fail("tag release workflow must use JDK 17 for AGP 9")
    if "doc/RELEASES.md" not in release_ci or "--notes-file RELEASES.md" in release_ci:
        fail("release workflow must use the repository's actual doc/RELEASES.md notes file")

    required_docs = [ROOT / "doc" / "RELEASES.md", ROOT / "doc" / "RELEASE_SIGNING.md"]
    for path in required_docs:
        if not path.is_file():
            fail(f"required release document is missing: {path.relative_to(ROOT)}")

    forbidden_suffixes = {".apk", ".jks", ".keystore"}
    for path in tracked_files():
        if path.resolve() == Path(__file__).resolve():
            continue
        if path.suffix.lower() in forbidden_suffixes or path.name in {"keystore.properties"}:
            fail(f"generated APK or signing material is tracked: {path.relative_to(ROOT)}")
        if path.is_file() and path.stat().st_size < 2_000_000:
            text = path.read_text(encoding="utf-8", errors="ignore")
            if "-----BEGIN PRIVATE KEY-----" in text or "-----BEGIN RSA PRIVATE KEY-----" in text:
                fail(f"private key material is present in tracked file: {path.relative_to(ROOT)}")
            for line in text.splitlines():
                if re.search(r"(?i)^(storePassword|keyPassword)\s*=", line) and not re.search(
                    r"(?i)=(replace-with|your[-_])", line
                ):
                    fail(f"possible real signing password in tracked file: {path.relative_to(ROOT)}")

    print(f"release contract OK: version={version}, versionCode={code}")


if __name__ == "__main__":
    main()
