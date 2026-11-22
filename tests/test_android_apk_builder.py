from __future__ import annotations

import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile


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


    def test_copies_safe_sprite_assets_into_player_assets_folder(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            output = root / "apk-assets"
            (source / "sprites").mkdir(parents=True)
            (source / "sprites" / "player.png").write_bytes(b"png-data")
            copied = BUILDER.copy_application_assets(str(source), output)
            self.assertEqual(copied, 1)
            self.assertEqual((output / "sprites" / "player.png").read_bytes(), b"png-data")

    def test_rejects_unsafe_asset_path_characters(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            (source / "bad name.png").write_bytes(b"asset")
            with self.assertRaises(BUILDER.BuildError):
                BUILDER.copy_application_assets(str(source), root / "output")

    def test_checks_standalone_player_apk_contents(self) -> None:
        elf = bytearray(20)
        elf[:4] = b"\x7fELF"
        elf[18:20] = struct.pack("<H", 183)
        room = b"ALRM" + struct.pack("<I", 10)
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "player.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
                archive.writestr("classes.dex", b"dex-content")
                archive.writestr("assets/main.room", room)
                archive.writestr("lib/arm64-v8a/libalvm.so", bytes(elf))
            BUILDER.verify_player_apk(apk, "arm64-v8a")
            with self.assertRaises(BUILDER.BuildError):
                BUILDER.verify_player_apk(apk, "armeabi-v7a")


    def test_generated_player_displays_al_language_identity(self) -> None:
        template = BUILDER.TEMPLATE
        strings = (template / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
        player = (template / "app/src/main/java/com/alvmplayer/PlayerActivity.java").read_text(encoding="utf-8")
        manifest = (template / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        metadata = (template / "app/src/main/assets/al-language.json").read_text(encoding="utf-8")

        self.assertIn('name="made_with_al">MADE WITH AL</string>', strings)
        self.assertIn("R.string.made_with_al", player)
        self.assertIn("root.addView(languageBrand", player)
        self.assertIn('android:name="com.alvm.language"', manifest)
        self.assertIn('"applicationLanguage": "AL"', metadata)
        self.assertIn('"sourceExtension": ".al"', metadata)
        self.assertIn('"bytecodeVersion": 10', metadata)


if __name__ == "__main__":
    unittest.main()
