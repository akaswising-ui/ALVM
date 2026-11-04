"""CI Runtime loader and local version/rollback manager.

Updates are offline .ciruntime ZIP packages selected by the user. Only trusted
runtime packages should be installed: runtime.py is executable Python code.
"""
from __future__ import annotations

import hashlib
import importlib
import importlib.util
import json
import os
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path
from types import ModuleType
from typing import Any

RUNTIME_API_VERSION = 1
BUNDLED_VERSION = "1.0.0"
ROOM_FORMAT_VERSION = 9
_VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")
_MODULE_CACHE: dict[str, ModuleType] = {}


def _root(runtime_root: str) -> Path:
    root = Path(runtime_root).resolve()
    (root / "versions").mkdir(parents=True, exist_ok=True)
    return root


def _state_path(root: Path) -> Path:
    return root / "active.json"


def _read_state(root: Path) -> dict[str, Any]:
    path = _state_path(root)
    if not path.exists():
        return {"active_version": "bundled", "previous_versions": []}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        active = data.get("active_version", "bundled")
        previous = data.get("previous_versions", [])
        if not isinstance(active, str) or not isinstance(previous, list):
            raise ValueError("invalid runtime state")
        previous = [item for item in previous if isinstance(item, str) and (
            item == "bundled" or _VERSION_RE.fullmatch(item)
        )]
        return {"active_version": active, "previous_versions": previous[:5]}
    except Exception:
        # A damaged state file must never prevent the bundled runtime from starting.
        return {"active_version": "bundled", "previous_versions": []}


def _write_state(root: Path, state: dict[str, Any]) -> None:
    destination = _state_path(root)
    temp = root / "active.json.tmp"
    temp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(str(temp), str(destination))


def _module_from_file(path: Path, cache_key: str) -> ModuleType:
    cached = _MODULE_CACHE.get(cache_key)
    if cached is not None:
        return cached
    module_name = "_ci_runtime_" + re.sub(r"[^A-Za-z0-9_]", "_", cache_key)
    spec = importlib.util.spec_from_file_location(module_name, str(path))
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load CI Runtime module")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    try:
        spec.loader.exec_module(module)
    except Exception:
        sys.modules.pop(module_name, None)
        raise
    _validate_runtime_module(module)
    _MODULE_CACHE[cache_key] = module
    return module


def _validate_runtime_module(module: ModuleType) -> None:
    if getattr(module, "RUNTIME_API_VERSION", None) != RUNTIME_API_VERSION:
        raise RuntimeError("Runtime API version mismatch")
    if not callable(getattr(module, "run_room", None)):
        raise RuntimeError("Runtime package is missing run_room()")
    test = getattr(module, "self_test", None)
    if not callable(test) or test() is not True:
        raise RuntimeError("CI Runtime self-test failed")


def _read_manifest(version_dir: Path) -> dict[str, Any]:
    manifest_path = version_dir / "manifest.json"
    runtime_path = version_dir / "runtime.py"
    if not manifest_path.is_file() or not runtime_path.is_file():
        raise RuntimeError("Runtime version folder is incomplete")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("name") != "CI Runtime":
        raise RuntimeError("Invalid runtime package name")
    if manifest.get("runtime_api_version") != RUNTIME_API_VERSION:
        raise RuntimeError("Runtime API version mismatch")
    if manifest.get("version") != version_dir.name:
        raise RuntimeError("Runtime version folder does not match manifest")
    if not _VERSION_RE.fullmatch(version_dir.name):
        raise RuntimeError("Invalid runtime version")
    if not (manifest.get("room_format_min", 0) <= ROOM_FORMAT_VERSION
            <= manifest.get("room_format_max", -1)):
        raise RuntimeError("This runtime does not support the installed .room format")
    digest = hashlib.sha256(runtime_path.read_bytes()).hexdigest()
    if digest.lower() != str(manifest.get("sha256", "")).lower():
        raise RuntimeError("Runtime package checksum mismatch")
    return manifest


def _load_engine(root: Path) -> tuple[ModuleType, str]:
    state = _read_state(root)
    active = state["active_version"]
    if active == "bundled":
        return importlib.import_module("ci_runtime_engine"), BUNDLED_VERSION

    version_dir = root / "versions" / active
    try:
        _read_manifest(version_dir)
        engine = _module_from_file(version_dir / "runtime.py", active)
        return engine, active
    except Exception:
        # Broken/incompatible active updates are switched back to the bundled engine.
        previous = state["previous_versions"]
        fallback = previous[0] if previous else "bundled"
        if fallback != "bundled":
            try:
                candidate_dir = root / "versions" / fallback
                _read_manifest(candidate_dir)
                _module_from_file(candidate_dir / "runtime.py", fallback)
            except Exception:
                fallback = "bundled"
        remaining = [item for item in previous if item != fallback]
        _write_state(root, {
            "active_version": fallback,
            "previous_versions": [active] + remaining
        })
        if fallback == "bundled":
            return importlib.import_module("ci_runtime_engine"), BUNDLED_VERSION
        engine = _module_from_file(root / "versions" / fallback / "runtime.py", fallback)
        return engine, fallback


def status_json(runtime_root: str) -> str:
    root = _root(runtime_root)
    state = _read_state(root)
    active = state["active_version"]
    if active != "bundled":
        try:
            _read_manifest(root / "versions" / active)
        except Exception:
            active = "bundled"
    return json.dumps({
        "ok": True,
        "active_version": active,
        "bundled_version": BUNDLED_VERSION,
        "previous_versions": state["previous_versions"],
        "room_format": ROOM_FORMAT_VERSION,
        "runtime_api_version": RUNTIME_API_VERSION,
    }, ensure_ascii=False)


def run_room_json(room_path: str, runtime_root: str,
                  entry_name: str = "main", event_value: str = "") -> str:
    root = _root(runtime_root)
    try:
        engine, version = _load_engine(root)
        result = engine.run_room(room_path, entry_name, event_value)
        result["runtimeVersion"] = version
        return json.dumps(result, ensure_ascii=False)
    except Exception as exc:
        return json.dumps({
            "ok": False,
            "output": "",
            "error": "CI Runtime loader error: " + str(exc),
            "ui": {"title": "AL Application", "elements": []},
            "runtimeVersion": BUNDLED_VERSION,
        }, ensure_ascii=False)


def install_update(package_path: str, runtime_root: str) -> str:
    root = _root(runtime_root)
    try:
        archive_path = Path(package_path)
        if not archive_path.is_file():
            raise RuntimeError("Runtime update file was not found")
        if archive_path.stat().st_size > 4 * 1024 * 1024:
            raise RuntimeError("Runtime update package exceeds the 4 MiB limit")

        with zipfile.ZipFile(archive_path, "r") as archive:
            names = archive.namelist()
            if sorted(names) != ["manifest.json", "runtime.py"]:
                raise RuntimeError("Package must contain only manifest.json and runtime.py")
            manifest_bytes = archive.read("manifest.json")
            runtime_bytes = archive.read("runtime.py")

        if len(manifest_bytes) > 64 * 1024 or len(runtime_bytes) > 2 * 1024 * 1024:
            raise RuntimeError("Runtime update contains an oversized file")
        manifest = json.loads(manifest_bytes.decode("utf-8"))
        version = str(manifest.get("version", ""))
        if manifest.get("name") != "CI Runtime":
            raise RuntimeError("Invalid runtime package name")
        if not _VERSION_RE.fullmatch(version):
            raise RuntimeError("Runtime version must use numeric major.minor.patch format")
        if manifest.get("runtime_api_version") != RUNTIME_API_VERSION:
            raise RuntimeError("Runtime API version is not supported")
        minimum = manifest.get("room_format_min")
        maximum = manifest.get("room_format_max")
        if not isinstance(minimum, int) or not isinstance(maximum, int):
            raise RuntimeError("Runtime package is missing .room compatibility information")
        if not (minimum <= ROOM_FORMAT_VERSION <= maximum):
            raise RuntimeError("Runtime package does not support .room version 9")
        digest = hashlib.sha256(runtime_bytes).hexdigest()
        if digest.lower() != str(manifest.get("sha256", "")).lower():
            raise RuntimeError("Runtime package checksum verification failed")

        source = runtime_bytes.decode("utf-8")
        compile(source, "runtime.py", "exec")
        versions_dir = root / "versions"
        destination = versions_dir / version
        if destination.exists():
            raise RuntimeError("This runtime version is already installed")

        with tempfile.TemporaryDirectory(prefix="ci-runtime-", dir=str(versions_dir)) as staging:
            stage = Path(staging)
            (stage / "runtime.py").write_bytes(runtime_bytes)
            normalized_manifest = dict(manifest)
            normalized_manifest["sha256"] = digest
            (stage / "manifest.json").write_text(
                json.dumps(normalized_manifest, ensure_ascii=False, indent=2),
                encoding="utf-8"
            )
            # Verify the module interface and self-test before activating it.
            candidate = _module_from_file(stage / "runtime.py", "staged_" + version)
            _validate_runtime_module(candidate)
            os.replace(str(stage), str(destination))

        state = _read_state(root)
        old_active = state["active_version"]
        previous = [old_active] + [
            item for item in state["previous_versions"] if item != old_active and item != version
        ]
        _write_state(root, {
            "active_version": version,
            "previous_versions": previous[:5],
        })
        return json.dumps({
            "ok": True,
            "message": "CI Runtime update installed and activated",
            "active_version": version,
            "previous_versions": previous[:5],
            "room_format": ROOM_FORMAT_VERSION,
        }, ensure_ascii=False)
    except Exception as exc:
        return json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False)


def rollback(runtime_root: str) -> str:
    root = _root(runtime_root)
    state = _read_state(root)
    old_active = state["active_version"]
    candidates = list(state["previous_versions"])
    selected = "bundled"
    for candidate in candidates:
        if candidate == "bundled":
            selected = "bundled"
            break
        try:
            version_dir = root / "versions" / candidate
            _read_manifest(version_dir)
            _module_from_file(version_dir / "runtime.py", candidate)
            selected = candidate
            break
        except Exception:
            continue

    if selected == old_active:
        return json.dumps({"ok": False, "error": "No earlier working runtime is available"})
    remaining = [item for item in candidates if item != selected]
    remaining.insert(0, old_active)
    _write_state(root, {
        "active_version": selected,
        "previous_versions": remaining[:5],
    })
    return json.dumps({
        "ok": True,
        "message": "CI Runtime rollback completed",
        "active_version": selected,
        "previous_versions": remaining[:5],
    }, ensure_ascii=False)
