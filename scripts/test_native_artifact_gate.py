#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

try:
    from scripts import verify_native_artifact as native_gate
except ImportError:
    import verify_native_artifact as native_gate


DEFAULT_ABIS = native_gate.DEFAULT_ABIS
GateError = native_gate.GateError
NativeEntry = native_gate.NativeEntry
find_zipalign = native_gate.find_zipalign
load_contract = native_gate.load_contract
read_native_entries = native_gate.read_native_entries
run = native_gate.run
verify_entries = native_gate.verify_entries
verify_zip_alignment = native_gate.verify_zip_alignment


JNI_EXPORT = "Java_com_bg7yoz_ft8cn_fixture_Native_ping"
LEGACY_EXPORT = "Java_com_bg7yoz_ft8cn_fixture_Native_legacy"
REPOSITORY_ROOT = Path(__file__).resolve().parent.parent


def make_elf(
    elf_class: int,
    machine: int,
    alignment: int = 0x4000,
    symbols: tuple[str, ...] = (JNI_EXPORT,),
    *,
    byte_order: str = "little",
    segment_offset: int = 0,
    virtual_address: int = 0,
    segment_file_size: int | None = None,
    segment_memory_size: int | None = None,
    symbol_binding: int = 1,
    symbol_visibility: int = 0,
    symbol_section_index: int = 1,
) -> bytes:
    """Create a deterministic minimal ELF32/ELF64 fixture with .dynstr/.dynsym."""
    if elf_class not in (1, 2) or byte_order not in ("little", "big"):
        raise ValueError("unsupported ELF fixture identity")
    endian = "<" if byte_order == "little" else ">"
    encoding = 1 if byte_order == "little" else 2
    ehdr_size = 64 if elf_class == 2 else 52
    phdr_size = 56 if elf_class == 2 else 32
    shdr_size = 64 if elf_class == 2 else 40
    sym_size = 24 if elf_class == 2 else 16
    word_alignment = 8 if elf_class == 2 else 4
    dynstr_offset = 0x80 if elf_class == 2 else 0x60
    names = bytearray(b"\0")
    name_offsets: list[int] = []
    for symbol in symbols:
        name_offsets.append(len(names))
        names.extend(symbol.encode("ascii") + b"\0")
    dynsym_offset = (dynstr_offset + len(names) + word_alignment - 1) & ~(word_alignment - 1)
    dynsym = bytearray(b"\0" * sym_size)
    dynsym.extend(b"\0" * (len(name_offsets) * sym_size))
    section_offset = (dynsym_offset + len(dynsym) + word_alignment - 1) & ~(word_alignment - 1)
    total_size = section_offset + 3 * shdr_size
    file_size = total_size if segment_file_size is None else segment_file_size
    memory_size = total_size if segment_memory_size is None else segment_memory_size
    data = bytearray(total_size)
    ident = b"\x7fELF" + bytes((elf_class, encoding, 1, 0)) + b"\0" * 8
    if elf_class == 2:
        struct.pack_into(
            endian + "16sHHIQQQIHHHHHH",
            data,
            0,
            ident,
            3,
            machine,
            1,
            0,
            ehdr_size,
            section_offset,
            0,
            ehdr_size,
            phdr_size,
            1,
            shdr_size,
            3,
            0,
        )
        struct.pack_into(
            endian + "IIQQQQQQ",
            data,
            ehdr_size,
            1,
            5,
            segment_offset,
            virtual_address,
            virtual_address,
            file_size,
            memory_size,
            alignment,
        )
    else:
        struct.pack_into(
            endian + "16sHHIIIIIHHHHHH",
            data,
            0,
            ident,
            3,
            machine,
            1,
            0,
            ehdr_size,
            section_offset,
            0,
            ehdr_size,
            phdr_size,
            1,
            shdr_size,
            3,
            0,
        )
        struct.pack_into(
            endian + "IIIIIIII",
            data,
            ehdr_size,
            1,
            segment_offset,
            virtual_address,
            virtual_address,
            file_size,
            memory_size,
            5,
            alignment,
        )
    data[dynstr_offset : dynstr_offset + len(names)] = names
    info = (symbol_binding << 4) | 2
    for index, name_offset in enumerate(name_offsets, start=1):
        symbol_offset = index * sym_size
        if elf_class == 2:
            struct.pack_into(
                endian + "IBBHQQ",
                dynsym,
                symbol_offset,
                name_offset,
                info,
                symbol_visibility,
                symbol_section_index,
                0,
                0,
            )
        else:
            struct.pack_into(
                endian + "IIIBBH",
                dynsym,
                symbol_offset,
                name_offset,
                0,
                0,
                info,
                symbol_visibility,
                symbol_section_index,
            )
    data[dynsym_offset : dynsym_offset + len(dynsym)] = dynsym
    sh_fmt = endian + ("IIQQQQIIQQ" if elf_class == 2 else "IIIIIIIIII")
    struct.pack_into(
        sh_fmt,
        data,
        section_offset + shdr_size,
        0,
        3,
        0,
        0,
        dynstr_offset,
        len(names),
        0,
        0,
        1,
        0,
    )
    struct.pack_into(
        sh_fmt,
        data,
        section_offset + 2 * shdr_size,
        0,
        11,
        0,
        0,
        dynsym_offset,
        len(dynsym),
        1,
        1,
        word_alignment,
        sym_size,
    )
    return bytes(data)


def make_elf_for_abi(
    abi: str,
    alignment: int = 0x4000,
    symbols: tuple[str, ...] = (JNI_EXPORT,),
    **kwargs: object,
) -> bytes:
    elf_class, machine = native_gate.ABI_ELF_IDENTITIES[abi]
    return make_elf(elf_class, machine, alignment, symbols, **kwargs)


def entries(
    alignment: int = 0x4000,
    symbols: tuple[str, ...] = (JNI_EXPORT,),
    **kwargs: object,
) -> list[NativeEntry]:
    return [
        NativeEntry(
            f"lib/{abi}/libft8cn.so",
            abi,
            "libft8cn.so",
            make_elf_for_abi(abi, alignment, symbols, **kwargs),
        )
        for abi in DEFAULT_ABIS
    ]


def contract() -> dict[str, native_gate.JniContract]:
    return {
        "libft8cn.so": native_gate.JniContract(
            required_exports=frozenset((JNI_EXPORT,)),
            legacy_optional_exports=frozenset((LEGACY_EXPORT,)),
        )
    }


class NativeArtifactGateTests(unittest.TestCase):
    def test_16kb_all_abis_and_required_jni_contract_pass(self) -> None:
        summaries = verify_entries(entries(), DEFAULT_ABIS, contract())
        self.assertEqual(len(summaries), 4)
        self.assertTrue(all("load_align=0x4000" in line for line in summaries))

    def test_any_4kb_load_segment_fails(self) -> None:
        fixture = entries()
        fixture[2] = NativeEntry(
            fixture[2].display_path,
            fixture[2].abi,
            fixture[2].library,
            make_elf_for_abi(fixture[2].abi, 0x1000),
        )
        with self.assertRaisesRegex(GateError, "PT_LOAD 0 alignment below 0x4000: 0x1000"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_elf_identity_must_match_path_abi(self) -> None:
        fixture = entries()
        fixture[0] = NativeEntry(
            fixture[0].display_path,
            fixture[0].abi,
            fixture[0].library,
            make_elf(2, 62),
        )
        with self.assertRaisesRegex(GateError, "ELF identity does not match arm64-v8a"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_elf32_and_elf64_both_byte_orders_parse(self) -> None:
        for elf_class, machine in ((1, 3), (2, 62)):
            for byte_order in ("little", "big"):
                facts = native_gate.parse_elf(make_elf(elf_class, machine, byte_order=byte_order))
                self.assertEqual(facts.elf_class, elf_class)
                self.assertEqual(facts.machine, machine)
                self.assertEqual(facts.load_alignments, (0x4000,))

    def test_non_power_of_two_alignment_fails(self) -> None:
        fixture = entries()
        fixture[0] = NativeEntry(
            fixture[0].display_path,
            fixture[0].abi,
            fixture[0].library,
            make_elf_for_abi(fixture[0].abi, 0x5000),
        )
        with self.assertRaisesRegex(GateError, "alignment is not a power of two: 0x5000"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_non_congruent_load_segment_fails(self) -> None:
        fixture = entries()
        fixture[0] = NativeEntry(
            fixture[0].display_path,
            fixture[0].abi,
            fixture[0].library,
            make_elf_for_abi(fixture[0].abi, segment_offset=1, virtual_address=0),
        )
        with self.assertRaisesRegex(GateError, "offset/vaddr are not congruent"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_load_segment_file_range_must_fit_and_file_size_must_not_exceed_memory(self) -> None:
        fixture = entries()
        fixture[0] = NativeEntry(
            fixture[0].display_path,
            fixture[0].abi,
            fixture[0].library,
            make_elf_for_abi(
                fixture[0].abi,
                segment_offset=0x4000,
                virtual_address=0x4000,
            ),
        )
        with self.assertRaisesRegex(GateError, "PT_LOAD 0 file range extends beyond ELF file"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

        fixture = entries(segment_file_size=2, segment_memory_size=1)
        with self.assertRaisesRegex(GateError, "file size exceeds memory size"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_declared_program_section_and_dynsym_ranges_must_fit(self) -> None:
        valid = make_elf(2, 62)
        with self.assertRaisesRegex(GateError, "program header table extends beyond ELF file"):
            native_gate.parse_elf(valid[: 64 + 55])
        with self.assertRaisesRegex(GateError, "section header table extends beyond ELF file"):
            native_gate.parse_elf(valid[:-1])

        invalid_dynsym = bytearray(valid)
        section_offset = struct.unpack_from("<Q", invalid_dynsym, 40)[0]
        dynsym_header = section_offset + 2 * 64
        struct.pack_into("<Q", invalid_dynsym, dynsym_header + 32, 0x1008)
        with self.assertRaisesRegex(GateError, "dynamic symbol section 2 extends beyond ELF file"):
            native_gate.parse_elf(bytes(invalid_dynsym))

    def test_truncated_and_non_utf8_elf_fail_cleanly(self) -> None:
        with self.assertRaisesRegex(GateError, "extends beyond ELF file"):
            native_gate.parse_elf(b"\x7fELF" + bytes((2, 1)) + b"\0" * 10)
        invalid_text = bytearray(make_elf(2, 62))
        invalid_text[0x81] = 0xFF
        with self.assertRaisesRegex(GateError, "string is not valid UTF-8"):
            native_gate.parse_elf(bytes(invalid_text))

    def test_missing_abi_fails_before_release(self) -> None:
        with self.assertRaisesRegex(GateError, "ABI set mismatch.*x86_64"):
            verify_entries(entries()[:-1], DEFAULT_ABIS, contract())

    def test_missing_jni_export_fails(self) -> None:
        fixture = entries(symbols=("not_jni",))
        with self.assertRaisesRegex(GateError, "JNI export contract mismatch.*Native_ping"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_uncontracted_extra_jni_export_fails(self) -> None:
        fixture = entries(symbols=(JNI_EXPORT, "Java_com_bg7yoz_ft8cn_fixture_Native_extra"))
        with self.assertRaisesRegex(GateError, "JNI export contract mismatch.*Native_extra"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_required_jni_must_be_defined_globally_and_visible(self) -> None:
        cases = (
            ("undefined", {"symbol_section_index": 0}),
            ("local", {"symbol_binding": 0}),
            ("hidden", {"symbol_visibility": 2}),
        )
        for label, kwargs in cases:
            with self.subTest(label=label), self.assertRaisesRegex(
                GateError, "JNI export contract mismatch.*Native_ping"
            ):
                verify_entries(entries(**kwargs), DEFAULT_ABIS, contract())

    def test_weak_protected_jni_is_an_externally_visible_export(self) -> None:
        summaries = verify_entries(
            entries(symbol_binding=2, symbol_visibility=3), DEFAULT_ABIS, contract()
        )
        self.assertEqual(len(summaries), 4)

    def test_legacy_optional_jni_export_may_be_present_or_absent(self) -> None:
        self.assertEqual(len(verify_entries(entries(), DEFAULT_ABIS, contract())), 4)
        self.assertEqual(
            len(verify_entries(entries(symbols=(JNI_EXPORT, LEGACY_EXPORT)), DEFAULT_ABIS, contract())),
            4,
        )

    def test_apk_and_aab_scan_every_abi(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for suffix, prefix in (("apk", "lib"), ("aab", "base/lib")):
                artifact = root / f"fixture.{suffix}"
                with zipfile.ZipFile(artifact, "w") as archive:
                    for abi in DEFAULT_ABIS:
                        archive.writestr(f"{prefix}/{abi}/libft8cn.so", make_elf_for_abi(abi))
                kind, scanned = read_native_entries(artifact)
                self.assertEqual(kind, suffix)
                self.assertEqual({entry.abi for entry in scanned}, set(DEFAULT_ABIS))
                self.assertEqual(len(scanned), 4)

    def test_directory_scan_supports_raw_jnilibs_layout(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for abi in DEFAULT_ABIS:
                abi_dir = root / abi
                abi_dir.mkdir()
                (abi_dir / "libft8cn.so").write_bytes(make_elf_for_abi(abi))
            kind, scanned = read_native_entries(root)
            self.assertEqual(kind, "directory")
            self.assertEqual(len(scanned), 4)

    def test_duplicate_zip_entry_fails_instead_of_reading_last_entry_twice(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            artifact = Path(temp) / "duplicate.apk"
            with zipfile.ZipFile(artifact, "w") as archive:
                for abi in DEFAULT_ABIS:
                    name = f"lib/{abi}/libft8cn.so"
                    archive.writestr(name, make_elf_for_abi(abi, 0x1000))
                    with mock.patch("warnings.warn"):
                        archive.writestr(name, make_elf_for_abi(abi, 0x4000))
            with self.assertRaisesRegex(GateError, "duplicate native ZIP entry"):
                read_native_entries(artifact)

    def test_duplicate_abi_library_at_different_paths_fails(self) -> None:
        fixture = entries()
        fixture.append(
            NativeEntry(
                "other/arm64-v8a/libft8cn.so",
                "arm64-v8a",
                "libft8cn.so",
                make_elf_for_abi("arm64-v8a"),
            )
        )
        with self.assertRaisesRegex(GateError, "duplicate native library for ABI arm64-v8a"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

    def test_zipalign_command_uses_16kb_page_alignment(self) -> None:
        completed = mock.Mock(returncode=0, stdout="Verification successful", stderr="")
        with tempfile.TemporaryDirectory() as temp, mock.patch.object(
            native_gate.subprocess, "run", return_value=completed
        ) as runner:
            root = Path(temp)
            apk = root / "app.apk"
            tool = root / ("zipalign.exe" if os.name == "nt" else "zipalign")
            apk.write_bytes(b"fixture")
            tool.write_bytes(b"fixture")
            verify_zip_alignment(apk, tool)
        command = runner.call_args.args[0]
        self.assertEqual(command[1:6], ["-c", "-P", "16", "-v", "4"])

    def test_zipalign_unavailable_fails_explicitly(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(
            native_gate.shutil, "which", return_value=None
        ):
            with self.assertRaisesRegex(GateError, "zipalign unavailable"):
                find_zipalign()

    def test_zipalign_sdk_discovery_uses_numeric_version_and_stable_preference(self) -> None:
        self.assertGreater(
            native_gate._android_tool_version_key("35.0.0"),
            native_gate._android_tool_version_key("9.0.0"),
        )
        self.assertGreater(
            native_gate._android_tool_version_key("36.0.0-rc10"),
            native_gate._android_tool_version_key("36.0.0-rc2"),
        )
        with tempfile.TemporaryDirectory() as temp:
            sdk = Path(temp)
            executable = "zipalign.exe" if os.name == "nt" else "zipalign"
            for version in ("9.0.0", "35.0.0", "36.0.0-rc1", "36.0.0"):
                tool = sdk / "build-tools" / version / executable
                tool.parent.mkdir(parents=True)
                tool.write_bytes(b"fixture")
            with mock.patch.dict(os.environ, {"ANDROID_HOME": str(sdk)}, clear=True), mock.patch.object(
                native_gate.shutil, "which", return_value=None
            ):
                self.assertEqual(find_zipalign().parent.name, "36.0.0")

    def test_required_zipalign_rejects_non_apk_even_when_elf_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            contract_file = root / "contract.json"
            contract_file.write_text(
                '{"schema":2,"libraries":{"libft8cn.so":{"required_exports":['
                f'"{JNI_EXPORT}"'
                '],"legacy_optional_exports":[]}}}',
                encoding="utf-8",
            )
            for abi in DEFAULT_ABIS:
                abi_dir = root / "native" / abi
                abi_dir.mkdir(parents=True)
                (abi_dir / "libft8cn.so").write_bytes(make_elf_for_abi(abi))
            args = argparse.Namespace(
                artifact=root / "native",
                jni_contract=contract_file,
                expected_abis=DEFAULT_ABIS,
                zipalign_mode="required",
                zipalign=None,
            )
            with self.assertRaisesRegex(GateError, "accepts only APK"):
                run(args)

    def test_non_apk_auto_mode_reports_elf_only_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            contract_file = root / "contract.json"
            contract_file.write_text(
                '{"schema":2,"libraries":{"libft8cn.so":{"required_exports":['
                f'"{JNI_EXPORT}"'
                '],"legacy_optional_exports":[]}}}',
                encoding="utf-8",
            )
            for abi in DEFAULT_ABIS:
                abi_dir = root / "native" / abi
                abi_dir.mkdir(parents=True)
                (abi_dir / "libft8cn.so").write_bytes(make_elf_for_abi(abi))
            bundle = root / "native.aab"
            with zipfile.ZipFile(bundle, "w") as archive:
                for abi in DEFAULT_ABIS:
                    archive.writestr(f"base/lib/{abi}/libft8cn.so", make_elf_for_abi(abi))
            for artifact in (root / "native", bundle):
                result = run(
                    argparse.Namespace(
                        artifact=artifact,
                        jni_contract=contract_file,
                        expected_abis=DEFAULT_ABIS,
                        zipalign_mode="auto",
                        zipalign=None,
                    )
                )
                self.assertEqual(result.outcome, "ELF_ONLY_PASS")

    def test_repository_contract_is_31_required_plus_2_legacy_optional(self) -> None:
        path = Path(__file__).with_name("native_jni_contract.json")
        loaded = load_contract(path)
        library = loaded["libft8cn.so"]
        self.assertEqual(len(library.required_exports), 31)
        self.assertEqual(len(library.legacy_optional_exports), 2)
        self.assertEqual(len(library.allowed_exports), 33)
        native_gate.verify_contract_against_java_sources(
            loaded, REPOSITORY_ROOT / "ft8cn" / "app" / "src" / "main" / "java"
        )


class NativeWorkflowActivationTests(unittest.TestCase):
    def test_all_workflows_gate_the_final_apk_after_build_and_before_upload(self) -> None:
        contracts = (
            (
                "android.yml",
                '--artifact "ft8cn/app/build/outputs/apk/debug/app-debug.apk"',
                "./gradlew :app:assembleDebug",
                "python scripts/verify_apk_signature.py",
                None,
                False,
            ),
            (
                "android-prerelease.yml",
                '--artifact "${{ steps.apk.outputs.apk_path }}"',
                "./gradlew :app:packageTestApk",
                "python scripts/verify_apk_signature.py",
                "python scripts/verify_apk_metadata.py",
                True,
            ),
            (
                "android-release.yml",
                '--artifact "${{ steps.apk.outputs.apk_path }}"',
                "./gradlew assembleRelease",
                "python scripts/verify_apk_signature.py",
                "python scripts/verify_apk_metadata.py",
                True,
            ),
        )
        common = (
            "python scripts/verify_native_artifact.py",
            "--jni-contract scripts/native_jni_contract.json",
            "--expected-abis arm64-v8a,armeabi-v7a,x86,x86_64",
            "--zipalign-mode required",
        )
        for workflow_name, artifact, build, signature, metadata, creates_release in contracts:
            workflow = (REPOSITORY_ROOT / ".github" / "workflows" / workflow_name).read_text(
                encoding="utf-8"
            )
            for fragment in (*common, artifact):
                self.assertEqual(workflow.count(fragment), 1, f"{workflow_name}: {fragment}")
            gate_offset = workflow.index("python scripts/verify_native_artifact.py")
            self.assertLess(workflow.index(build), gate_offset, workflow_name)
            self.assertLess(workflow.index(signature), gate_offset, workflow_name)
            if metadata is not None:
                self.assertLess(workflow.index(metadata), gate_offset, workflow_name)
            self.assertLess(gate_offset, workflow.index("uses: actions/upload-artifact@v4"), workflow_name)
            if creates_release:
                self.assertLess(gate_offset, workflow.index("gh release create"), workflow_name)

    def test_formal_metadata_is_derived_from_the_canonical_gradle_version(self) -> None:
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "android-release.yml").read_text(
            encoding="utf-8"
        )
        for fragment in (
            'echo "version_code=${gradle_version#*:}"',
            "--package com.bg7yoz.ft8cn",
            '--version-name "${{ steps.version.outputs.version }}"',
            '--version-code "${{ steps.version.outputs.version_code }}"',
            "--require-zipalign",
        ):
            self.assertEqual(workflow.count(fragment), 1, fragment)


if __name__ == "__main__":
    unittest.main()
