#!/usr/bin/env python3
"""Compare two FT8CN native behavior snapshots produced by instrumentation."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys
from typing import Any


SCHEMA = "ft8cn-native-behavior-oracle-v1"
FLOAT_DIGEST_KEY = "raw_float32_le_sha256"


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
) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    notices: list[str] = []
    for name, snapshot in (("oracle", expected), ("candidate", actual)):
        if snapshot.get("schema") != SCHEMA:
            errors.append(f"{name}.schema: expected {SCHEMA!r}, got {snapshot.get('schema')!r}")
    if errors:
        return errors, notices

    expected_metadata = expected.get("metadata", {})
    actual_metadata = actual.get("metadata", {})
    if expected_metadata.get("native_abi") != actual_metadata.get("native_abi"):
        errors.append(
            "metadata.native_abi: snapshots must be captured on the same ABI; "
            f"oracle={expected_metadata.get('native_abi')!r}, candidate={actual_metadata.get('native_abi')!r}"
        )
    if expected_metadata.get("oracle_inputs") != actual_metadata.get("oracle_inputs"):
        errors.append(
            "metadata.oracle_inputs: snapshot input revisions differ; "
            f"oracle={expected_metadata.get('oracle_inputs')!r}, candidate={actual_metadata.get('oracle_inputs')!r}"
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
    value = json.loads(path.read_text(encoding="utf-8"))
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
    args = parser.parse_args()
    if args.float_atol < 0 or args.float_rtol < 0 or args.spectrum_int_atol < 0:
        parser.error("numeric tolerances must be non-negative")
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
        f"{expected['metadata']['native_abi']} "
        f"(atol={args.float_atol}, rtol={args.float_rtol}, "
        f"spectrum_int_atol={args.spectrum_int_atol}, "
        f"strict_float_digests={args.strict_float_digests})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
