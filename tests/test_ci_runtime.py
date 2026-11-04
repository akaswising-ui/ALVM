import hashlib
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "python"))

import ci_runtime_engine as runtime
import ci_runtime_manager as manager


def u32(value):
    return struct.pack("<I", value & 0xFFFFFFFF)


def i32(value):
    return struct.pack("<i", value)


def i64(value):
    return struct.pack("<q", value)


def string(value):
    data = value.encode("utf-8")
    return u32(len(data)) + data


def instruction(op, arg=0, text=""):
    return struct.pack("<Bq", op, arg) + string(text)


def room_file(code, local_count=0, version=9):
    data = b"ALRM" + u32(version) + u32(0) + u32(1)
    data += string("main") + i32(-1) + u32(6) + u32(0) + u32(local_count)
    data += u32(len(code))
    for item in code:
        data += item
    return data


class CiRuntimeTests(unittest.TestCase):
    def run_bytes(self, payload):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "sample.room"
            path.write_bytes(payload)
            return runtime.run_room(str(path))

    def test_self_test(self):
        self.assertTrue(runtime.self_test())

    def test_prints_string(self):
        result = self.run_bytes(room_file([
            instruction(2, 0, "Hello from Python CI Runtime"),
            instruction(23),
            instruction(25),
        ]))
        self.assertTrue(result["ok"], result)
        self.assertEqual(result["output"], "Hello from Python CI Runtime")

    def test_arithmetic(self):
        result = self.run_bytes(room_file([
            instruction(1, 40),
            instruction(1, 2),
            instruction(6),
            instruction(23),
            instruction(25),
        ]))
        self.assertTrue(result["ok"], result)
        self.assertEqual(result["output"], "42")

    def test_array_literal_and_index(self):
        result = self.run_bytes(room_file([
            instruction(1, 12),
            instruction(1, 30),
            instruction(26, 2),
            instruction(5, 0),
            instruction(4, 0),
            instruction(1, 1),
            instruction(27),
            instruction(23),
            instruction(25),
        ], local_count=1))
        self.assertTrue(result["ok"], result)
        self.assertEqual(result["output"], "30")

    def test_native_ui_payload(self):
        result = self.run_bytes(room_file([
            instruction(2, 0, "CI Runtime test"),
            instruction(37, 1, "screen"),
            instruction(2, 0, "result"),
            instruction(2, 0, "Python executed the .room file"),
            instruction(37, 2, "text"),
            instruction(25),
        ]))
        self.assertTrue(result["ok"], result)
        self.assertEqual(result["ui"]["title"], "CI Runtime test")
        self.assertEqual(result["ui"]["elements"][0]["id"], "result")
        self.assertEqual(
            result["ui"]["elements"][0]["text"],
            "Python executed the .room file"
        )


    def test_runtime_update_and_rollback(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / "runtime-root"
            package = Path(folder) / "ci-runtime-1.0.1.ciruntime"
            runtime_source_path = (
                Path(__file__).resolve().parents[1]
                / "app" / "src" / "main" / "python" / "ci_runtime_engine.py"
            )
            runtime_source = runtime_source_path.read_text(encoding="utf-8")
            runtime_source = runtime_source.replace(
                'RUNTIME_VERSION = "1.0.0"',
                'RUNTIME_VERSION = "1.0.1"',
                1,
            )
            runtime_bytes = runtime_source.encode("utf-8")
            manifest = {
                "name": "CI Runtime",
                "version": "1.0.1",
                "runtime_api_version": 1,
                "room_format_min": 9,
                "room_format_max": 9,
                "sha256": hashlib.sha256(runtime_bytes).hexdigest(),
            }
            with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("manifest.json", json.dumps(manifest))
                archive.writestr("runtime.py", runtime_bytes)

            installed = json.loads(manager.install_update(str(package), str(root)))
            self.assertTrue(installed["ok"], installed)
            self.assertEqual(installed["active_version"], "1.0.1")

            room = Path(folder) / "hello.room"
            room.write_bytes(room_file([
                instruction(2, 0, "Versioned runtime"),
                instruction(23),
                instruction(25),
            ]))
            result = json.loads(manager.run_room_json(str(room), str(root)))
            self.assertTrue(result["ok"], result)
            self.assertEqual(result["runtimeVersion"], "1.0.1")
            self.assertEqual(result["output"], "Versioned runtime")

            rolled_back = json.loads(manager.rollback(str(root)))
            self.assertTrue(rolled_back["ok"], rolled_back)
            self.assertEqual(rolled_back["active_version"], "bundled")

            fallback = json.loads(manager.run_room_json(str(room), str(root)))
            self.assertTrue(fallback["ok"], fallback)
            self.assertEqual(fallback["runtimeVersion"], "1.0.0")

    def test_rejects_wrong_room_version(self):
        result = self.run_bytes(room_file([instruction(25)], version=8))
        self.assertFalse(result["ok"])
        self.assertIn("Unsupported .room version", result["error"])

    def test_rejects_truncated_file(self):
        result = self.run_bytes(b"ALRM" + u32(9))
        self.assertFalse(result["ok"])
        self.assertIn("Truncated", result["error"])


if __name__ == "__main__":
    unittest.main()
