#!/usr/bin/env python3
"""Check release identity, signing gates, and tracked/staged secret hygiene."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

try:
    from scripts.verify_native_artifact import (
        GateError as NativeGateError,
        load_contract as load_native_contract,
        verify_contract_against_java_sources,
    )
except ImportError:
    from verify_native_artifact import (
        GateError as NativeGateError,
        load_contract as load_native_contract,
        verify_contract_against_java_sources,
    )


ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__).resolve()
SECRET_FILE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"}
SECRET_FILE_NAMES = {
    ".env",
    "credentials.json",
    "service-account.json",
    "keystore.properties",
}
PLACEHOLDER_RE = re.compile(r"(?i)(replace-with|your[-_]|<secret>|<key_|example|changeme)")
BETA_TAG = "v0.93.005-beta.8"
BETA_VERSION_NAME = "0.93.005-beta.8"
BETA_VERSION_CODE = "93008"
BETA_SECRET_NAMES = (
    "FT8CN_BETA_KEYSTORE_B64",
    "FT8CN_BETA_STORE_FILE",
    "FT8CN_BETA_STORE_PASSWORD",
    "FT8CN_BETA_KEY_ALIAS",
    "FT8CN_BETA_KEY_PASSWORD",
    "FT8CN_BETA_CERT_SHA256",
)
SECRET_LINE_PATTERNS = (
    re.compile(r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----"),
    re.compile(r"(?i)\b(?:ghp|github_pat|xox[baprs])-[-_a-z0-9]{20,}\b"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"(?i)\b(?:bearer|authorization)\s*[:=]\s*(?!\$\{\{)[^\s]+"),
    re.compile(
        r"(?i)\b(?:password|passwd|secret|token|private[_-]?key|credential)\b\s*[:=]\s*([A-Za-z0-9+/]{32,}={0,2})"
    ),
)


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def run_git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True)
    return result.stdout.decode("utf-8", errors="replace")


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
    return [ROOT / item for item in run_git("ls-files", "-z").split("\0") if item]


def scan_text(text: str, label: str) -> None:
    for line in text.splitlines():
        if PLACEHOLDER_RE.search(line):
            continue
        for pattern in SECRET_LINE_PATTERNS:
            if pattern.search(line):
                fail(f"possible secret material in {label}; value was not printed")


def scan_tracked_files() -> None:
    for path in tracked_files():
        if path.resolve() == SELF:
            continue
        relative = path.relative_to(ROOT)
        if path.suffix.lower() in SECRET_FILE_SUFFIXES or path.name in SECRET_FILE_NAMES:
            fail(f"signing/credential file is tracked: {relative}")
        if path.is_file() and path.stat().st_size < 2_000_000:
            scan_text(path.read_text(encoding="utf-8", errors="ignore"), str(relative))


def scan_staged_diff() -> None:
    diff = run_git("diff", "--cached", "--unified=0", "--no-ext-diff")
    current_path: str | None = None
    added: list[str] = []
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            current_path = line[6:]
            added = []
        elif line.startswith("+") and not line.startswith("+++") and current_path:
            added.append(line[1:])
        elif line.startswith("@@") and current_path and added:
            if current_path not in {str(SELF.relative_to(ROOT)).replace("\\", "/")}:
                scan_text("\n".join(added), f"staged diff {current_path}")
            added = []
    if current_path and added and current_path != str(SELF.relative_to(ROOT)).replace("\\", "/"):
        scan_text("\n".join(added), f"staged diff {current_path}")


def scan_history_batch(object_ids: list[str], output: bytes) -> None:
    """Consume every ``git cat-file --batch`` response before inspecting blobs.

    ``rev-list --objects`` starts with commit objects.  Each batch object, not
    just blobs, has a body after its header; failing to consume a commit/tree/tag
    body makes the next body look like a header and silently skips history.
    """
    cursor = 0
    for object_id in object_ids:
        header_end = output.find(b"\n", cursor)
        if header_end < 0:
            fail("git cat-file --batch ended before all requested history objects were read")
        header = output[cursor:header_end].split()
        cursor = header_end + 1
        if len(header) != 3:
            fail(f"git cat-file --batch returned an unreadable history object: {object_id[:12]}")
        try:
            size = int(header[2])
        except ValueError:
            fail(f"git cat-file --batch returned an invalid object size: {object_id[:12]}")
        if size < 0 or cursor + size >= len(output):
            fail(f"git cat-file --batch returned a truncated history object: {object_id[:12]}")
        body = output[cursor:cursor + size]
        cursor += size
        if output[cursor:cursor + 1] != b"\n":
            fail(f"git cat-file --batch returned an unterminated history object: {object_id[:12]}")
        cursor += 1
        if header[1] == b"blob" and b"\x00" not in body and len(body) < 2_000_000:
            scan_text(body.decode("utf-8", errors="ignore"), f"history blob {object_id[:12]}")
    if cursor != len(output):
        fail("git cat-file --batch returned unexpected trailing history data")


def scan_history() -> None:
    """Scan reachable historical text blobs when explicitly requested."""
    entries = [line.partition(" ") for line in run_git("rev-list", "--objects", "--all").splitlines()]
    object_ids = [object_id for object_id, _, path_hint in entries if Path(path_hint).name not in {"check_release_contract.py", "verify_apk_signature.py"}]
    if not object_ids:
        return
    process = subprocess.Popen(
        ["git", "cat-file", "--batch"], cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE
    )
    output, _ = process.communicate(("\n".join(object_ids) + "\n").encode())
    scan_history_batch(object_ids, output)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--history",
        action="store_true",
        help="Also scan reachable historical text blobs; slower and intended for pre-release review.",
    )
    args = parser.parse_args()

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
    if "FT8CN_RELEASE_CERT_SHA256" not in app_gradle or "releaseCertSha256" not in app_gradle:
        fail("formal release signing must require the trusted certificate SHA-256")
    build_types = app_gradle.split("buildTypes {", 1)[-1]
    beta_block = re.search(r"\bbeta\s*\{(?P<body>.*?)\n\s*}\s*release\s*\{", build_types, re.S)
    if not beta_block:
        fail("app/build.gradle must define a dedicated beta build type")
    if "signingConfig signingConfigs.beta" not in beta_block.group("body"):
        fail("beta build type must select the beta-only signing config")
    if "signingConfig signingConfigs.debug" in beta_block.group("body"):
        fail("beta build type may not use the Android Debug signing config")
    if "applicationIdSuffix '.beta'" not in beta_block.group("body"):
        fail("beta build type must use the isolated beta package suffix")
    if "dependsOn(\"assembleBeta\")" not in app_gradle or "outputs/apk/beta/app-beta.apk" not in app_gradle:
        fail("packageTestApk must package the dedicated beta variant")

    ci = (ROOT / ".github" / "workflows" / "android.yml").read_text(encoding="utf-8")
    beta_ci = (ROOT / ".github" / "workflows" / "android-prerelease.yml").read_text(encoding="utf-8")
    release_ci = (ROOT / ".github" / "workflows" / "android-release.yml").read_text(encoding="utf-8")
    try:
        verify_contract_against_java_sources(
            load_native_contract(ROOT / "scripts" / "native_jni_contract.json"),
            ROOT / "ft8cn" / "app" / "src" / "main" / "java",
        )
    except NativeGateError as exc:
        fail(str(exc))
    root_ignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    for pattern in ("**/*.jks", "**/*.keystore", "**/*.p12", "**/*.pfx", "**/*.apk"):
        if pattern not in root_ignore:
            fail(f"root .gitignore must recursively ignore {pattern}")
    if "java-version: '17'" not in ci and 'java-version: "17"' not in ci:
        fail("ordinary CI must use JDK 17 for AGP 9")
    if "java-version: '17'" not in release_ci and 'java-version: "17"' not in release_ci:
        fail("tag release workflow must use JDK 17 for AGP 9")
    if "doc/release-notes/v${tag_version}.md" not in release_ci or "--notes-file RELEASES.md" in release_ci:
        fail("release workflow must use the version-specific notes file")
    for required in (
        ROOT / "doc" / "RELEASES.md",
        ROOT / "doc" / "RELEASE_SIGNING.md",
        ROOT / "doc" / "release-notes" / f"v{version}.md",
        ROOT / "doc" / "release-notes" / f"{BETA_TAG}.md",
    ):
        if not required.is_file():
            fail(f"required release document is missing: {required.relative_to(ROOT)}")
    notes = (ROOT / "doc" / "release-notes" / f"v{version}.md").read_text(encoding="utf-8")
    if not re.search(rf"^# FT8CN v{re.escape(version)} Release Notes$", notes, re.M):
        fail(f"version-specific notes do not have the exact v{version} heading")
    beta_notes = (ROOT / "doc" / "release-notes" / f"{BETA_TAG}.md").read_text(encoding="utf-8")
    if not re.search(rf"^# FT8CN {re.escape(BETA_TAG)} Pre-release Notes$", beta_notes, re.M):
        fail(f"beta notes do not have the exact {BETA_TAG} heading")
    for marker in (BETA_VERSION_NAME, BETA_VERSION_CODE, "com.bg7yoz.ft8cn.beta", "beta-only", "HIL", "卸载"):
        if marker not in beta_notes:
            fail(f"beta notes are missing required boundary/version marker: {marker}")
    if "FT8CN_FORMAL_RELEASE_APPROVED" not in release_ci:
        fail("formal release workflow must remain blocked until explicit approval")
    if "FT8CN_RELEASE_CERT_SHA256" not in release_ci:
        fail("formal release workflow must pass the trusted certificate fingerprint")
    if "if: ${{ always() }}" not in release_ci or "ft8cn-release-signing" not in release_ci:
        fail("formal keystore cleanup must be an always-run step in a fixed temp directory")
    if "git ls-remote" not in release_ci or "gh release view" not in release_ci:
        fail("release workflow must check immutable remote tags and existing Releases")
    if "packageTestApk" in ci:
        fail("ordinary CI may not require beta-only signing secrets")
    for name in BETA_SECRET_NAMES:
        if f"secrets.{name}" not in beta_ci or f"Missing secret {name}" not in beta_ci:
            fail(f"beta workflow must require the {name} secret without printing its value")
    for required in (
        '"v*.*.*-beta.*"',
        "^v([0-9]+\\.[0-9]+\\.[0-9]{3})-beta\\.([0-9]+)$",
        "-Pft8cn.versionName",
        "-Pft8cn.versionCode",
        "keytool",
        "apksigner",
        "ft8cn-beta-signing",
        "if: ${{ always() }}",
        "--expect beta",
        "com.bg7yoz.ft8cn.beta",
        BETA_VERSION_NAME,
        BETA_VERSION_CODE,
    ):
        if required not in beta_ci:
            fail(f"beta workflow is missing required signing/version/package contract: {required}")
    if "FT8CN_RELEASE_" in beta_ci:
        fail("beta workflow must not consume formal release signing secrets")
    if "FT8CN_BETA_" in release_ci:
        fail("formal release workflow must remain independent of beta signing secrets")

    native_gate_common_fragments = (
        "python scripts/verify_native_artifact.py",
        "--jni-contract scripts/native_jni_contract.json",
        "--expected-abis arm64-v8a,armeabi-v7a,x86,x86_64",
        "--zipalign-mode required",
    )
    workflow_gate_contracts = (
        (
            "android.yml",
            ci,
            '--artifact "ft8cn/app/build/outputs/apk/debug/app-debug.apk"',
            "./gradlew :app:assembleDebug",
            "python scripts/verify_apk_signature.py",
            None,
            False,
        ),
        (
            "android-prerelease.yml",
            beta_ci,
            '--artifact "${{ steps.apk.outputs.apk_path }}"',
            "./gradlew :app:packageTestApk",
            "python scripts/verify_apk_signature.py",
            "python scripts/verify_apk_metadata.py",
            True,
        ),
        (
            "android-release.yml",
            release_ci,
            '--artifact "${{ steps.apk.outputs.apk_path }}"',
            "./gradlew assembleRelease",
            "python scripts/verify_apk_signature.py",
            "python scripts/verify_apk_metadata.py",
            True,
        ),
    )
    for workflow_name, workflow, artifact, build, signature, metadata, creates_release in workflow_gate_contracts:
        for fragment in (*native_gate_common_fragments, artifact):
            if workflow.count(fragment) != 1:
                fail(f"{workflow_name} must contain exactly one native artifact gate fragment: {fragment}")
        gate_offset = workflow.index("python scripts/verify_native_artifact.py")
        upload_offset = workflow.index("uses: actions/upload-artifact@v4")
        if workflow.index(build) > gate_offset:
            fail(f"{workflow_name} must build its final APK before the native artifact gate")
        if workflow.index(signature) > gate_offset:
            fail(f"{workflow_name} must verify the APK signature before the native artifact gate")
        if metadata is not None and workflow.index(metadata) > gate_offset:
            fail(f"{workflow_name} must verify APK metadata before the native artifact gate")
        if gate_offset > upload_offset:
            fail(f"{workflow_name} must run the native artifact gate before artifact upload")
        if creates_release and gate_offset > workflow.index("gh release create"):
            fail(f"{workflow_name} must run the native artifact gate before GitHub Release creation")

    for fragment in (
        'echo "version_code=${gradle_version#*:}"',
        "--package com.bg7yoz.ft8cn",
        '--version-name "${{ steps.version.outputs.version }}"',
        '--version-code "${{ steps.version.outputs.version_code }}"',
        "--require-zipalign",
    ):
        if release_ci.count(fragment) != 1:
            fail(f"android-release.yml must contain exactly one formal metadata fragment: {fragment}")

    scan_tracked_files()
    scan_staged_diff()
    if args.history:
        scan_history()
    print(f"release contract OK: version={version}, versionCode={code}, notes=v{version}")


if __name__ == "__main__":
    main()
