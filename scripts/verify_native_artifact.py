#!/usr/bin/env python3
"""Fail-closed native artifact gate for Android APK/AAB/directory inputs.

The gate validates every packaged ``.so`` without extracting untrusted ZIP
paths.  It checks ELF PT_LOAD alignment, the exact ABI set, per-ABI library
parity, and the JNI exports declared in a versioned JSON contract.  APK inputs
also run Android SDK ``zipalign -c -P 16 -v 4``.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import struct
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable


PT_LOAD = 1
SHT_DYNSYM = 11
MIN_PAGE_ALIGNMENT = 0x4000
DEFAULT_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


class GateError(ValueError):
    """Expected, user-facing verification failure."""


@dataclass(frozen=True)
class NativeEntry:
    display_path: str
    abi: str
    library: str
    data: bytes


@dataclass(frozen=True)
class ElfFacts:
    elf_class: int
    load_alignments: tuple[int, ...]
    dynamic_symbols: frozenset[str]


def _unpack_from(fmt: str, data: bytes, offset: int, context: str) -> tuple[object, ...]:
    size = struct.calcsize(fmt)
    if offset < 0 or offset + size > len(data):
        raise GateError(f"{context} extends beyond ELF file ({offset}+{size}>{len(data)})")
    return struct.unpack_from(fmt, data, offset)


def _bounded_slice(data: bytes, offset: int, size: int, context: str) -> bytes:
    if offset < 0 or size < 0 or offset + size > len(data):
        raise GateError(f"{context} extends beyond ELF file ({offset}+{size}>{len(data)})")
    return data[offset : offset + size]


def _cstring(data: bytes, offset: int, context: str) -> str:
    if offset < 0 or offset >= len(data):
        raise GateError(f"{context} string offset {offset} is outside its string table")
    end = data.find(b"\0", offset)
    if end < 0:
        raise GateError(f"{context} string is not NUL terminated")
    return data[offset:end].decode("utf-8", errors="strict")


def parse_elf(data: bytes, display_path: str = "<memory>") -> ElfFacts:
    if len(data) < 16 or data[:4] != b"\x7fELF":
        raise GateError(f"{display_path}: not an ELF file")
    elf_class = data[4]
    data_encoding = data[5]
    if elf_class not in (1, 2):
        raise GateError(f"{display_path}: unsupported ELF class {elf_class}")
    if data_encoding not in (1, 2):
        raise GateError(f"{display_path}: unsupported ELF byte order {data_encoding}")
    endian = "<" if data_encoding == 1 else ">"

    if elf_class == 2:
        header_fmt = endian + "16sHHIQQQIHHHHHH"
        header = _unpack_from(header_fmt, data, 0, f"{display_path}: ELF64 header")
        phoff, shoff = int(header[5]), int(header[6])
        phentsize, phnum = int(header[9]), int(header[10])
        shentsize, shnum = int(header[11]), int(header[12])
        ph_fmt = endian + "IIQQQQQQ"
        sh_fmt = endian + "IIQQQQIIQQ"
        sym_fmt = endian + "IBBHQQ"
        ph_align_index = 7
    else:
        header_fmt = endian + "16sHHIIIIIHHHHHH"
        header = _unpack_from(header_fmt, data, 0, f"{display_path}: ELF32 header")
        phoff, shoff = int(header[5]), int(header[6])
        phentsize, phnum = int(header[9]), int(header[10])
        shentsize, shnum = int(header[11]), int(header[12])
        ph_fmt = endian + "IIIIIIII"
        sh_fmt = endian + "IIIIIIIIII"
        sym_fmt = endian + "IIIBBH"
        ph_align_index = 7

    required_ph_size = struct.calcsize(ph_fmt)
    if phnum <= 0:
        raise GateError(f"{display_path}: ELF has no program headers")
    if phentsize < required_ph_size:
        raise GateError(
            f"{display_path}: program header entry size {phentsize} is smaller than {required_ph_size}"
        )
    load_alignments: list[int] = []
    for index in range(phnum):
        fields = _unpack_from(
            ph_fmt,
            data,
            phoff + index * phentsize,
            f"{display_path}: program header {index}",
        )
        if int(fields[0]) == PT_LOAD:
            load_alignments.append(int(fields[ph_align_index]))
    if not load_alignments:
        raise GateError(f"{display_path}: ELF has no PT_LOAD segments")

    required_sh_size = struct.calcsize(sh_fmt)
    if shnum <= 0 or shoff <= 0:
        raise GateError(f"{display_path}: ELF has no section table; cannot verify JNI exports")
    if shentsize < required_sh_size:
        raise GateError(
            f"{display_path}: section header entry size {shentsize} is smaller than {required_sh_size}"
        )
    sections: list[tuple[int, ...]] = []
    for index in range(shnum):
        fields = _unpack_from(
            sh_fmt,
            data,
            shoff + index * shentsize,
            f"{display_path}: section header {index}",
        )
        sections.append(tuple(int(value) for value in fields))

    symbols: set[str] = set()
    required_sym_size = struct.calcsize(sym_fmt)
    for section_index, section in enumerate(sections):
        section_type = section[1]
        if section_type != SHT_DYNSYM:
            continue
        section_offset, section_size = section[4], section[5]
        string_table_index = section[6]
        entry_size = section[9]
        if string_table_index >= len(sections):
            raise GateError(
                f"{display_path}: dynamic symbol section {section_index} has invalid string table index"
            )
        if entry_size < required_sym_size or section_size % entry_size != 0:
            raise GateError(f"{display_path}: malformed dynamic symbol section {section_index}")
        string_section = sections[string_table_index]
        string_table = _bounded_slice(
            data,
            string_section[4],
            string_section[5],
            f"{display_path}: dynamic string table",
        )
        for symbol_index in range(section_size // entry_size):
            symbol = _unpack_from(
                sym_fmt,
                data,
                section_offset + symbol_index * entry_size,
                f"{display_path}: dynamic symbol {symbol_index}",
            )
            name_offset = int(symbol[0])
            if name_offset:
                symbols.add(_cstring(string_table, name_offset, f"{display_path}: dynamic symbol"))
    if not symbols:
        raise GateError(f"{display_path}: ELF has no readable dynamic symbols")
    return ElfFacts(elf_class, tuple(load_alignments), frozenset(symbols))


def _abi_from_parts(parts: tuple[str, ...], display_path: str) -> str:
    matches = [part for part in parts[:-1] if part in DEFAULT_ABIS]
    if len(matches) != 1:
        raise GateError(
            f"{display_path}: cannot identify exactly one supported ABI path component; "
            f"expected one of {', '.join(DEFAULT_ABIS)}"
        )
    return matches[0]


def read_native_entries(artifact: Path) -> tuple[str, list[NativeEntry]]:
    artifact = artifact.resolve()
    if artifact.is_dir():
        entries: list[NativeEntry] = []
        for path in sorted(artifact.rglob("*.so")):
            relative = path.relative_to(artifact)
            parts = relative.parts
            display = relative.as_posix()
            entries.append(NativeEntry(display, _abi_from_parts(parts, display), path.name, path.read_bytes()))
        kind = "directory"
    elif artifact.is_file() and artifact.suffix.lower() in (".apk", ".aab"):
        kind = artifact.suffix.lower()[1:]
        entries = []
        try:
            with zipfile.ZipFile(artifact) as archive:
                names = sorted(
                    info.filename
                    for info in archive.infolist()
                    if not info.is_dir() and info.filename.lower().endswith(".so")
                )
                for name in names:
                    posix = PurePosixPath(name)
                    entries.append(
                        NativeEntry(name, _abi_from_parts(posix.parts, name), posix.name, archive.read(name))
                    )
        except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
            raise GateError(f"Cannot read {artifact}: {exc}") from exc
    else:
        raise GateError("Artifact must be an existing .apk, .aab, or directory")
    if not entries:
        raise GateError(f"{artifact}: no .so files found")
    return kind, entries


def load_contract(path: Path) -> dict[str, frozenset[str]]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise GateError(f"Cannot read JNI contract {path}: {exc}") from exc
    if not isinstance(document, dict) or document.get("schema") != 1:
        raise GateError("JNI contract must be an object with schema=1")
    libraries = document.get("libraries")
    if not isinstance(libraries, dict) or not libraries:
        raise GateError("JNI contract must contain a non-empty libraries object")
    result: dict[str, frozenset[str]] = {}
    for library, entry in libraries.items():
        if not isinstance(library, str) or not library.endswith(".so") or not isinstance(entry, dict):
            raise GateError("JNI contract library entries must be .so-name objects")
        exports = entry.get("jni_exports")
        if (
            not isinstance(exports, list)
            or not exports
            or any(not isinstance(value, str) or not value.startswith("Java_") for value in exports)
            or len(exports) != len(set(exports))
        ):
            raise GateError(f"JNI contract {library} must contain unique Java_* jni_exports")
        result[library] = frozenset(exports)
    return result


def verify_entries(
    entries: Iterable[NativeEntry],
    expected_abis: Iterable[str],
    contract: dict[str, frozenset[str]],
    min_alignment: int = MIN_PAGE_ALIGNMENT,
) -> list[str]:
    native_entries = list(entries)
    expected = frozenset(expected_abis)
    if not expected or not expected.issubset(DEFAULT_ABIS):
        raise GateError(f"Expected ABIs must be selected from {', '.join(DEFAULT_ABIS)}")
    actual = frozenset(entry.abi for entry in native_entries)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        raise GateError(f"ABI set mismatch: missing={missing or 'none'} extra={extra or 'none'}")

    libraries_by_abi = {
        abi: frozenset(entry.library for entry in native_entries if entry.abi == abi) for abi in expected
    }
    reference_abi = sorted(expected)[0]
    reference_libraries = libraries_by_abi[reference_abi]
    for abi, libraries in sorted(libraries_by_abi.items()):
        if libraries != reference_libraries:
            raise GateError(
                f"Native library set differs across ABIs: {reference_abi}={sorted(reference_libraries)} "
                f"{abi}={sorted(libraries)}"
            )
    missing_contract_libraries = set(contract) - reference_libraries
    if missing_contract_libraries:
        raise GateError(f"JNI contract libraries are absent: {sorted(missing_contract_libraries)}")

    summaries: list[str] = []
    for entry in sorted(native_entries, key=lambda value: value.display_path):
        facts = parse_elf(entry.data, entry.display_path)
        bad_alignments = [alignment for alignment in facts.load_alignments if alignment < min_alignment]
        if bad_alignments:
            formatted = ", ".join(f"0x{alignment:x}" for alignment in bad_alignments)
            raise GateError(
                f"{entry.display_path}: PT_LOAD alignment below 0x{min_alignment:x}: {formatted}"
            )
        actual_jni = frozenset(name for name in facts.dynamic_symbols if name.startswith("Java_"))
        if entry.library in contract:
            required_jni = contract[entry.library]
            missing = sorted(required_jni - actual_jni)
            extra = sorted(actual_jni - required_jni)
            if missing or extra:
                raise GateError(
                    f"{entry.display_path}: JNI export contract mismatch: "
                    f"missing={missing or 'none'} extra={extra or 'none'}"
                )
        elif actual_jni:
            raise GateError(
                f"{entry.display_path}: exposes JNI symbols but has no contract entry: {sorted(actual_jni)}"
            )
        summaries.append(
            f"{entry.display_path}: elf={facts.elf_class * 32}-bit "
            f"load_align={','.join(f'0x{value:x}' for value in facts.load_alignments)} "
            f"jni_exports={len(actual_jni)}"
        )
    return summaries


def find_zipalign(configured: Path | None = None) -> Path:
    if configured is not None:
        if configured.is_file():
            return configured.resolve()
        raise GateError(f"Configured zipalign does not exist: {configured}")
    env_tool = os.environ.get("ZIPALIGN")
    if env_tool:
        candidate = Path(env_tool)
        if candidate.is_file():
            return candidate.resolve()
        raise GateError(f"ZIPALIGN does not point to a file: {candidate}")
    on_path = shutil.which("zipalign")
    if on_path:
        return Path(on_path).resolve()
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise GateError("zipalign unavailable: set --zipalign, ZIPALIGN, ANDROID_HOME, or ANDROID_SDK_ROOT")
    build_tools = Path(sdk) / "build-tools"
    executable = "zipalign.exe" if os.name == "nt" else "zipalign"
    candidates = [path for path in build_tools.glob(f"*/{executable}") if path.is_file()]
    if not candidates:
        raise GateError(f"zipalign unavailable below {build_tools}")
    return sorted(candidates, key=lambda path: path.parent.name, reverse=True)[0].resolve()


def verify_zip_alignment(apk: Path, zipalign: Path) -> None:
    try:
        result = subprocess.run(
            [str(zipalign), "-c", "-P", "16", "-v", "4", str(apk.resolve())],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            shell=False,
        )
    except OSError as exc:
        raise GateError(f"Could not execute zipalign {zipalign}: {exc}") from exc
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise GateError(f"zipalign -c -P 16 rejected {apk}: {detail or 'no diagnostic output'}")


def parse_abis(value: str) -> tuple[str, ...]:
    values = tuple(part.strip() for part in value.split(",") if part.strip())
    if len(values) != len(set(values)):
        raise argparse.ArgumentTypeError("ABIs must not contain duplicates")
    unknown = set(values) - set(DEFAULT_ABIS)
    if not values or unknown:
        raise argparse.ArgumentTypeError(f"ABIs must be selected from {', '.join(DEFAULT_ABIS)}")
    return values


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", required=True, type=Path, help="APK, AAB, or extracted directory")
    parser.add_argument("--jni-contract", required=True, type=Path)
    parser.add_argument("--expected-abis", type=parse_abis, default=DEFAULT_ABIS)
    parser.add_argument(
        "--zipalign-mode",
        choices=("auto", "required", "skip"),
        default="auto",
        help="auto checks APKs; required rejects AAB/directories; skip is diagnostic-only",
    )
    parser.add_argument("--zipalign", type=Path, help="explicit Android SDK zipalign executable")
    return parser


def run(args: argparse.Namespace) -> list[str]:
    kind, entries = read_native_entries(args.artifact)
    summaries = verify_entries(entries, args.expected_abis, load_contract(args.jni_contract))
    if args.zipalign_mode == "required" and kind != "apk":
        raise GateError("zipalign-mode=required accepts only APK input; AAB/directory is not an installable APK")
    zipalign_required = kind == "apk" and args.zipalign_mode != "skip"
    if zipalign_required:
        verify_zip_alignment(args.artifact, find_zipalign(args.zipalign))
        summaries.append("zipalign: PASS (-c -P 16 -v 4)")
    elif args.zipalign_mode == "skip":
        summaries.append("zipalign: SKIPPED (diagnostic mode; not release-safe)")
    else:
        summaries.append(f"zipalign: NOT_APPLICABLE ({kind} input; verify the final APK separately)")
    return summaries


def main() -> None:
    try:
        lines = run(build_parser().parse_args())
    except GateError as exc:
        print(f"native_artifact_gate=FAIL {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
    for line in lines:
        print(line)
    print("native_artifact_gate=PASS")


if __name__ == "__main__":
    main()
