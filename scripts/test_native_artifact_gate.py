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
REPOSITORY_ROOT = Path(__file__).resolve().parent.parent


def make_elf64(alignment: int, symbols: tuple[str, ...] = (JNI_EXPORT,)) -> bytes:
    """Create a deterministic minimal ELF64 fixture with .dynstr/.dynsym."""
    ehdr_size = 64
    phdr_size = 56
    dynstr_offset = 0x80
    names = bytearray(b"\0")
    name_offsets: list[int] = []
    for symbol in symbols:
        name_offsets.append(len(names))
        names.extend(symbol.encode("ascii") + b"\0")
    dynsym_offset = (dynstr_offset + len(names) + 7) & ~7
    dynsym = bytearray(b"\0" * 24)
    for name_offset in name_offsets:
        dynsym.extend(struct.pack("<IBBHQQ", name_offset, 0x12, 0, 1, 0, 0))
    section_offset = (dynsym_offset + len(dynsym) + 7) & ~7
    shdr_size = 64
    total_size = section_offset + 3 * shdr_size
    data = bytearray(total_size)
    ident = b"\x7fELF" + bytes((2, 1, 1, 0)) + b"\0" * 8
    struct.pack_into(
        "<16sHHIQQQIHHHHHH",
        data,
        0,
        ident,
        3,
        62,
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
    struct.pack_into("<IIQQQQQQ", data, ehdr_size, 1, 5, 0, 0, 0, total_size, total_size, alignment)
    data[dynstr_offset : dynstr_offset + len(names)] = names
    data[dynsym_offset : dynsym_offset + len(dynsym)] = dynsym
    struct.pack_into(
        "<IIQQQQIIQQ",
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
        "<IIQQQQIIQQ",
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
        8,
        24,
    )
    return bytes(data)


def entries(alignment: int = 0x4000, symbols: tuple[str, ...] = (JNI_EXPORT,)) -> list[NativeEntry]:
    return [
        NativeEntry(f"lib/{abi}/libft8cn.so", abi, "libft8cn.so", make_elf64(alignment, symbols))
        for abi in DEFAULT_ABIS
    ]


def contract() -> dict[str, frozenset[str]]:
    return {"libft8cn.so": frozenset((JNI_EXPORT,))}


class NativeArtifactGateTests(unittest.TestCase):
    def test_16kb_all_abis_and_exact_jni_contract_pass(self) -> None:
        summaries = verify_entries(entries(), DEFAULT_ABIS, contract())
        self.assertEqual(len(summaries), 4)
        self.assertTrue(all("load_align=0x4000" in line for line in summaries))

    def test_any_4kb_load_segment_fails(self) -> None:
        fixture = entries()
        fixture[2] = NativeEntry(
            fixture[2].display_path,
            fixture[2].abi,
            fixture[2].library,
            make_elf64(0x1000),
        )
        with self.assertRaisesRegex(GateError, "PT_LOAD alignment below 0x4000: 0x1000"):
            verify_entries(fixture, DEFAULT_ABIS, contract())

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

    def test_apk_and_aab_scan_every_abi(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for suffix, prefix in (("apk", "lib"), ("aab", "base/lib")):
                artifact = root / f"fixture.{suffix}"
                with zipfile.ZipFile(artifact, "w") as archive:
                    for abi in DEFAULT_ABIS:
                        archive.writestr(f"{prefix}/{abi}/libft8cn.so", make_elf64(0x4000))
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
                (abi_dir / "libft8cn.so").write_bytes(make_elf64(0x4000))
            kind, scanned = read_native_entries(root)
            self.assertEqual(kind, "directory")
            self.assertEqual(len(scanned), 4)

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

    def test_required_zipalign_rejects_non_apk_even_when_elf_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            contract_file = root / "contract.json"
            contract_file.write_text(
                '{"schema":1,"libraries":{"libft8cn.so":{"jni_exports":['
                f'"{JNI_EXPORT}"'
                "]}}}",
                encoding="utf-8",
            )
            for abi in DEFAULT_ABIS:
                abi_dir = root / "native" / abi
                abi_dir.mkdir(parents=True)
                (abi_dir / "libft8cn.so").write_bytes(make_elf64(0x4000))
            args = argparse.Namespace(
                artifact=root / "native",
                jni_contract=contract_file,
                expected_abis=DEFAULT_ABIS,
                zipalign_mode="required",
                zipalign=None,
            )
            with self.assertRaisesRegex(GateError, "accepts only APK"):
                run(args)

    def test_repository_contract_is_well_formed_and_has_current_33_exports(self) -> None:
        path = Path(__file__).with_name("native_jni_contract.json")
        loaded = load_contract(path)
        self.assertEqual(len(loaded["libft8cn.so"]), 33)


class NativeReleaseWorkflowActivationTests(unittest.TestCase):
    def test_beta_and_formal_gate_the_final_apk_before_any_upload_or_release(self) -> None:
        required_fragments = (
            "python scripts/verify_native_artifact.py",
            '--artifact "${{ steps.apk.outputs.apk_path }}"',
            "--jni-contract scripts/native_jni_contract.json",
            "--expected-abis arm64-v8a,armeabi-v7a,x86,x86_64",
            "--zipalign-mode required",
        )
        for workflow_name in ("android-prerelease.yml", "android-release.yml"):
            workflow = (REPOSITORY_ROOT / ".github" / "workflows" / workflow_name).read_text(
                encoding="utf-8"
            )
            for fragment in required_fragments:
                self.assertEqual(workflow.count(fragment), 1, f"{workflow_name}: {fragment}")
            gate_offset = workflow.index("python scripts/verify_native_artifact.py")
            artifact_upload_offset = workflow.index("uses: actions/upload-artifact@v4")
            release_offset = workflow.index("gh release create")
            self.assertLess(gate_offset, artifact_upload_offset, workflow_name)
            self.assertLess(gate_offset, release_offset, workflow_name)


if __name__ == "__main__":
    unittest.main()
