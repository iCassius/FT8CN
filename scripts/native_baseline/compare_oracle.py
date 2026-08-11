#!/usr/bin/env python3
"""Compare two FT8CN native behavior snapshots produced by instrumentation."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import sys
from typing import Any


SCHEMA = "ft8cn-native-behavior-oracle-v2"
FLOAT_DIGEST_KEY = "raw_float32_le_sha256"
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
GIT_COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SUPPORTED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def validate_metadata(snapshot: dict[str, Any], name: str, errors: list[str]) -> dict[str, Any] | None:
    metadata = snapshot.get("metadata")
    if not isinstance(metadata, dict):
        errors.append(f"{name}.metadata: expected object")
        return None

    scalar_types = {
        "application_id": str,
        "version_name": str,
        "version_code": int,
        "oracle_inputs": str,
    }
    for key, expected_type in scalar_types.items():
        value = metadata.get(key)
        if not isinstance(value, expected_type) or isinstance(value, bool):
            errors.append(f"{name}.metadata.{key}: expected {expected_type.__name__}")

    environment = metadata.get("environment")
    if not isinstance(environment, dict):
        errors.append(f"{name}.metadata.environment: expected object")
    else:
        native_abi = environment.get("native_abi")
        if native_abi not in SUPPORTED_ABIS:
            errors.append(
                f"{name}.metadata.environment.native_abi: unsupported ABI {native_abi!r}"
            )
        page_size = environment.get("page_size")
        if not isinstance(page_size, int) or isinstance(page_size, bool) or page_size <= 0:
            errors.append(f"{name}.metadata.environment.page_size: expected positive integer")

    source = metadata.get("source")
    if not isinstance(source, dict):
        errors.append(f"{name}.metadata.source: expected object")
    else:
        git_commit = source.get("git_commit")
        if not isinstance(git_commit, str) or not GIT_COMMIT_PATTERN.fullmatch(git_commit):
            errors.append(f"{name}.metadata.source.git_commit: expected 40 lowercase hex characters")
        if not isinstance(source.get("git_dirty"), bool):
            errors.append(f"{name}.metadata.source.git_dirty: expected boolean")
        if not isinstance(source.get("build_variant"), str) or not source.get("build_variant"):
            errors.append(f"{name}.metadata.source.build_variant: expected non-empty string")

    artifacts = metadata.get("artifacts")
    if not isinstance(artifacts, dict):
        errors.append(f"{name}.metadata.artifacts: expected object")
    else:
        for key in ("target_apk_sha256", "test_apk_sha256", "native_library_sha256"):
            value = artifacts.get(key)
            if not isinstance(value, str) or not SHA256_PATTERN.fullmatch(value):
                errors.append(f"{name}.metadata.artifacts.{key}: expected 64 lowercase hex characters")

    if not isinstance(metadata.get("non_authoritative_environment"), dict):
        errors.append(f"{name}.metadata.non_authoritative_environment: expected object")
    return metadata


def compare_values(
    expected: Any,
    actual: Any,
    path: str,
    errors: list[str],
    notices: list[str],
    *,
    absolute_tolerance: float,
    relative_tolerance: float,
    spectrum_integer_tolerance: int,
    strict_float_digests: bool,
) -> None:
    if isinstance(expected, bool) or isinstance(actual, bool):
        if expected is not actual:
            errors.append(f"{path}: expected {expected!r}, got {actual!r}")
        return

    if isinstance(expected, dict) and isinstance(actual, dict):
        expected_keys = set(expected)
        actual_keys = set(actual)
        for key in sorted(expected_keys - actual_keys):
            errors.append(f"{path}.{key}: missing from candidate")
        for key in sorted(actual_keys - expected_keys):
            errors.append(f"{path}.{key}: unexpected candidate field")
        for key in sorted(expected_keys & actual_keys):
            child_path = f"{path}.{key}"
            if key == FLOAT_DIGEST_KEY and not strict_float_digests:
                if expected[key] != actual[key]:
                    notices.append(
                        f"{child_path}: raw floating-point digest changed; numeric tolerance checks remain authoritative"
                    )
                continue
            compare_values(
                expected[key],
                actual[key],
                child_path,
                errors,
                notices,
                absolute_tolerance=absolute_tolerance,
                relative_tolerance=relative_tolerance,
                spectrum_integer_tolerance=spectrum_integer_tolerance,
                strict_float_digests=strict_float_digests,
            )
        return

    if isinstance(expected, list) and isinstance(actual, list):
        if len(expected) != len(actual):
            errors.append(f"{path}: expected length {len(expected)}, got {len(actual)}")
            return
        for index, (expected_item, actual_item) in enumerate(zip(expected, actual)):
            compare_values(
                expected_item,
                actual_item,
                f"{path}[{index}]",
                errors,
                notices,
                absolute_tolerance=absolute_tolerance,
                relative_tolerance=relative_tolerance,
                spectrum_integer_tolerance=spectrum_integer_tolerance,
                strict_float_digests=strict_float_digests,
            )
        return

    if isinstance(expected, int) and isinstance(actual, int):
        tolerance = spectrum_integer_tolerance if path.startswith("$.spectrum.") else 0
        if abs(expected - actual) > tolerance:
            errors.append(
                f"{path}: expected integer {expected}, got {actual} "
                f"(integer tolerance={tolerance})"
            )
        return

    if (
        isinstance(expected, (int, float))
        and not isinstance(expected, bool)
        and isinstance(actual, (int, float))
        and not isinstance(actual, bool)
    ):
        if not math.isfinite(float(expected)) or not math.isfinite(float(actual)):
            errors.append(f"{path}: non-finite numeric value")
        elif not math.isclose(
            float(expected),
            float(actual),
            abs_tol=absolute_tolerance,
            rel_tol=relative_tolerance,
        ):
            errors.append(
                f"{path}: expected {expected!r}, got {actual!r} "
                f"(atol={absolute_tolerance}, rtol={relative_tolerance})"
            )
        return

    if type(expected) is not type(actual):
        errors.append(
            f"{path}: expected type {type(expected).__name__}, got {type(actual).__name__}"
        )
    elif expected != actual:
        errors.append(f"{path}: expected {expected!r}, got {actual!r}")


def compare_snapshots(
    expected: dict[str, Any],
    actual: dict[str, Any],
    *,
    absolute_tolerance: float = 1e-5,
    relative_tolerance: float = 1e-6,
    spectrum_integer_tolerance: int = 1,
    strict_float_digests: bool = False,
    require_distinct_builds: bool = True,
    require_16kb: bool = True,
) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    notices: list[str] = []
    for name, tolerance in (
        ("absolute_tolerance", absolute_tolerance),
        ("relative_tolerance", relative_tolerance),
    ):
        if (
            not isinstance(tolerance, (int, float))
            or isinstance(tolerance, bool)
            or not math.isfinite(float(tolerance))
            or tolerance < 0
        ):
            errors.append(f"{name}: expected a finite non-negative number")
    if (
        not isinstance(spectrum_integer_tolerance, int)
        or isinstance(spectrum_integer_tolerance, bool)
        or spectrum_integer_tolerance < 0
    ):
        errors.append("spectrum_integer_tolerance: expected a non-negative integer")
    if errors:
        return errors, notices

    for name, snapshot in (("oracle", expected), ("candidate", actual)):
        if snapshot.get("schema") != SCHEMA:
            errors.append(f"{name}.schema: expected {SCHEMA!r}, got {snapshot.get('schema')!r}")
    if errors:
        return errors, notices

    expected_metadata = validate_metadata(expected, "oracle", errors)
    actual_metadata = validate_metadata(actual, "candidate", errors)
    if errors or expected_metadata is None or actual_metadata is None:
        return errors, notices

    expected_environment = expected_metadata["environment"]
    actual_environment = actual_metadata["environment"]
    expected_source = expected_metadata["source"]
    actual_source = actual_metadata["source"]
    expected_artifacts = expected_metadata["artifacts"]
    actual_artifacts = actual_metadata["artifacts"]

    if expected_environment["native_abi"] != actual_environment["native_abi"]:
        errors.append(
            "metadata.environment.native_abi: snapshots must use the same process ABI; "
            f"oracle={expected_environment['native_abi']!r}, "
            f"candidate={actual_environment['native_abi']!r}"
        )
    if expected_environment["page_size"] != actual_environment["page_size"]:
        errors.append(
            "metadata.environment.page_size: snapshots must use the same page size; "
            f"oracle={expected_environment['page_size']!r}, "
            f"candidate={actual_environment['page_size']!r}"
        )
    if require_16kb and (
        expected_environment["page_size"] != 16384
        or actual_environment["page_size"] != 16384
    ):
        errors.append("metadata.environment.page_size: the native 16 KB gate requires 16384")
    for key in ("application_id", "version_name", "version_code", "oracle_inputs"):
        if expected_metadata[key] != actual_metadata[key]:
            errors.append(
                f"metadata.{key}: oracle={expected_metadata[key]!r}, "
                f"candidate={actual_metadata[key]!r}"
            )
    if expected_source["build_variant"] != actual_source["build_variant"]:
        errors.append(
            "metadata.source.build_variant: snapshots must use the same build variant; "
            f"oracle={expected_source['build_variant']!r}, "
            f"candidate={actual_source['build_variant']!r}"
        )
    if expected_source["git_dirty"] or actual_source["git_dirty"]:
        errors.append("metadata.source.git_dirty: oracle and candidate captures must be clean")
    if require_distinct_builds:
        if expected_source["git_commit"] == actual_source["git_commit"]:
            errors.append("metadata.source.git_commit: candidate must come from a distinct commit")
        if (
            expected_artifacts["native_library_sha256"]
            == actual_artifacts["native_library_sha256"]
        ):
            errors.append(
                "metadata.artifacts.native_library_sha256: candidate still contains the oracle library"
            )

    expected_behavior = {key: value for key, value in expected.items() if key not in ("schema", "metadata")}
    actual_behavior = {key: value for key, value in actual.items() if key not in ("schema", "metadata")}
    compare_values(
        expected_behavior,
        actual_behavior,
        "$",
        errors,
        notices,
        absolute_tolerance=absolute_tolerance,
        relative_tolerance=relative_tolerance,
        spectrum_integer_tolerance=spectrum_integer_tolerance,
        strict_float_digests=strict_float_digests,
    )
    return errors, notices


def load_snapshot(path: Path) -> dict[str, Any]:
    def reject_non_finite(value: str) -> None:
        raise ValueError(f"non-finite JSON number is not allowed: {value}")

    value = json.loads(
        path.read_text(encoding="utf-8"),
        parse_constant=reject_non_finite,
    )
    if not isinstance(value, dict):
        raise ValueError(f"snapshot root must be an object: {path}")
    return value


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("oracle", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--float-atol", type=float, default=1e-5)
    parser.add_argument("--float-rtol", type=float, default=1e-6)
    parser.add_argument(
        "--spectrum-int-atol",
        type=int,
        default=1,
        help="absolute tolerance for integer FFT bins; all other integer outputs remain exact",
    )
    parser.add_argument(
        "--strict-float-digests",
        action="store_true",
        help="require bit-identical synthesized float arrays in addition to tolerance-based profiles",
    )
    parser.add_argument(
        "--allow-same-build",
        action="store_true",
        help="allow oracle self-checks; never use this for candidate acceptance",
    )
    parser.add_argument(
        "--allow-non-16kb",
        action="store_true",
        help="allow exploratory 4 KB comparisons; never use this for the native 16 KB gate",
    )
    args = parser.parse_args()
    if (
        not math.isfinite(args.float_atol)
        or not math.isfinite(args.float_rtol)
        or args.float_atol < 0
        or args.float_rtol < 0
        or args.spectrum_int_atol < 0
    ):
        parser.error("numeric tolerances must be finite and non-negative")
    try:
        expected = load_snapshot(args.oracle)
        actual = load_snapshot(args.candidate)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    errors, notices = compare_snapshots(
        expected,
        actual,
        absolute_tolerance=args.float_atol,
        relative_tolerance=args.float_rtol,
        spectrum_integer_tolerance=args.spectrum_int_atol,
        strict_float_digests=args.strict_float_digests,
        require_distinct_builds=not args.allow_same_build,
        require_16kb=not args.allow_non_16kb,
    )
    for notice in notices:
        print("NOTICE: " + notice)
    if errors:
        for error in errors:
            print("ERROR: " + error, file=sys.stderr)
        print(f"native oracle mismatch: {len(errors)} difference(s)", file=sys.stderr)
        return 1
    print(
        "native oracle match: "
        f"{expected['metadata']['environment']['native_abi']} "
        f"oracle={expected['metadata']['artifacts']['native_library_sha256'][:12]} "
        f"candidate={actual['metadata']['artifacts']['native_library_sha256'][:12]} "
        f"(atol={args.float_atol}, rtol={args.float_rtol}, "
        f"spectrum_int_atol={args.spectrum_int_atol}, "
        f"strict_float_digests={args.strict_float_digests})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
