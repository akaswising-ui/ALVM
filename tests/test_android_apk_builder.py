from __future__ import annotations

import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest


MODULE_PATH = Path(__file__).resolve().parents[1] / "tools" / "build_android_apk.py"
SPEC = importlib.util.spec_from_file_location("alvm_android_apk_builder", MODULE_PATH)
BUILDER = importlib.util.module_from_spec(SPEC)
assert SPEC is not None and SPEC.loader is not None
SPEC.loader.exec_module(BUILDER)


class AndroidApkBuilderTests(unittest.TestCase):
    def test_default_package_id_is_valid_and_deterministic(self) -> None:
        package_id = BUILDER.default_application_id("AL Calculator")
        self.assertEqual(package_id, "com.alvm.generated.al_calculator")
        self.assertEqual(BUILDER.validate_application_id(package_id), package_id)

    def test_rejects_invalid_application_ids(self) -> None:
        for invalid in ("com", "com.example.", "com.example.my-app", "1com.example.app"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(BUILDER.BuildError):
                    BUILDER.validate_application_id(invalid)

    def test_safe_output_name_removes_path_and_shell_characters(self) -> None:
        self.assertEqual(BUILDER.safe_output_name("../../My App.apk"), "My_App.apk")
        self.assertEqual(BUILDER.safe_output_name("..."), "ALApp")

    def test_accepts_room_v10_header(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            room = Path(directory) / "main.room"
            room.write_bytes(b"ALRM" + struct.pack("<I", 10))
            BUILDER.verify_room(room)

    def test_rejects_truncated_bad_magic_and_wrong_version(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            room = Path(directory) / "main.room"
            for payload in (b"ALRM", b"NOPE" + struct.pack("<I", 10),
                            b"ALRM" + struct.pack("<I", 9)):
                room.write_bytes(payload)
                with self.subTest(payload=payload):
                    with self.assertRaises(BUILDER.BuildError):
                        BUILDER.verify_room(room)

    def test_checks_native_elf_architecture(self) -> None:
        valid = bytearray(20)
        valid[:4] = b"\x7fELF"
        valid[18:20] = struct.pack("<H", 183)
        BUILDER.require_elf_arch(bytes(valid), "arm64-v8a")
        with self.assertRaises(BUILDER.BuildError):
            BUILDER.require_elf_arch(bytes(valid), "armeabi-v7a")


if __name__ == "__main__":
    unittest.main()
