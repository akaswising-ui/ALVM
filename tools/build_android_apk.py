#!/usr/bin/env python3
"""Compile one AL source file and build a real standalone Android debug APK.

Requirements: CMake, a C++17 compiler, Python 3.9+, Rust/Cargo, Android SDK
Platform 35, Android NDK 27.2.12479018, CMake 3.22.1 and Gradle 8.9.
Run from any directory; paths are resolved relative to this repository unless
an absolute path is supplied.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "app" / "src" / "main" / "assets" / "android-export-template"
STUDIO_APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_ROOM_BYTES = 64 * 1024 * 1024
SUPPORTED_ABIS = ("arm64-v8a", "armeabi-v7a")
ELF_MACHINE = {"arm64-v8a": 183, "armeabi-v7a": 40}


class BuildError(RuntimeError):
    pass


def run(command: list[str], cwd: Path) -> None:
    print("+ " + " ".join(command), flush=True)
    try:
        subprocess.run(command, cwd=str(cwd), check=True)
    except FileNotFoundError as error:
        raise BuildError(
            "Required command not found: " + command[0]
            + ". Install the Android build prerequisites and retry."
        ) from error
    except subprocess.CalledProcessError as error:
        raise BuildError(
            "Build command failed with exit code " + str(error.returncode)
            + ": " + " ".join(command)
        ) from error


def resolve_source(value: str, source_root: str | None) -> Path:
    candidate = Path(value).expanduser()
    if not candidate.is_absolute():
        candidate = (ROOT / candidate).resolve()
    else:
        candidate = candidate.resolve()
    if source_root:
        allowed_root = Path(source_root).expanduser()
        if not allowed_root.is_absolute():
            allowed_root = (ROOT / allowed_root).resolve()
        else:
            allowed_root = allowed_root.resolve()
        try:
            candidate.relative_to(allowed_root)
        except ValueError as error:
            raise BuildError("AL source must be inside --source-root: " + str(allowed_root)) from error
    if not candidate.is_file():
        raise BuildError("AL source file does not exist: " + str(candidate))
    if candidate.suffix.lower() != ".al":
        raise BuildError("Input must be an AL source file ending in .al")
    size = candidate.stat().st_size
    if size <= 0:
        raise BuildError("AL source file is empty.")
    if size > MAX_SOURCE_BYTES:
        raise BuildError("AL source exceeds the 4 MiB Android export limit.")
    return candidate


def safe_output_name(name: str) -> str:
    value = re.sub(r"[^A-Za-z0-9._-]+", "_", name.strip()).strip("._-")
    if not value or value in (".", ".."):
        return "ALApp"
    return value[:64]


def default_application_id(name: str) -> str:
    suffix = re.sub(r"[^a-z0-9_]+", "_", name.lower()).strip("_")
    if not suffix or not suffix[0].isalpha():
        suffix = "app_" + suffix
    return "com.alvm.generated." + suffix[:40]


def validate_application_id(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+", value):
        raise BuildError(
            "Invalid Android application ID. Use dot-separated Java identifiers, "
            "for example com.example.myapp."
        )
    if len(value) > 180:
        raise BuildError("Android application ID is too long.")
    return value


def verify_room(path: Path) -> None:
    if not path.is_file():
        raise BuildError("AL compiler did not create main.room.")
    size = path.stat().st_size
    if size < 8 or size > MAX_ROOM_BYTES:
        raise BuildError("Compiler output has an invalid .room file size.")
    with path.open("rb") as stream:
        header = stream.read(8)
    if header[:4] != b"ALRM":
        raise BuildError("Compiler output does not have the ALRM bytecode header.")
    version = struct.unpack("<I", header[4:8])[0]
    if version != 10:
        raise BuildError("Expected .room v10 bytecode, compiler created v" + str(version) + ".")


def copy_application_assets(source_value: str, destination: Path) -> int:
    """Copy a bounded, symlink-free asset directory into the generated APK assets."""
    source = Path(source_value).expanduser().resolve()
    if not source.is_dir():
        raise BuildError("--assets-dir must point to an existing directory: " + str(source))
    copied_bytes = 0
    copied_files = 0
    for item in sorted(source.rglob("*")):
        if item.is_symlink():
            raise BuildError("Symlinks are not allowed in --assets-dir: " + str(item))
        if not item.is_file():
            continue
        relative = item.relative_to(source)
        parts = relative.parts
        if not parts or any(part in ("", ".", "..") for part in parts):
            raise BuildError("Unsafe asset path: " + str(relative))
        if len(relative.as_posix()) > 240:
            raise BuildError("Asset path is too long: " + str(relative))
        if any(not re.fullmatch(r"[A-Za-z0-9_.-]+", part) for part in parts):
            raise BuildError("Asset path contains unsupported characters: " + str(relative))
        if relative.as_posix() == "main.room":
            raise BuildError("The asset directory cannot replace the generated main.room.")
        copied_bytes += item.stat().st_size
        copied_files += 1
        if copied_bytes > 64 * 1024 * 1024 or copied_files > 2048:
            raise BuildError("--assets-dir exceeds the 64 MiB / 2048 file export limit.")
        target = destination.joinpath(*parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(item, target)
    return copied_files


def require_elf_arch(data: bytes, abi: str) -> None:
    if len(data) < 20 or data[:4] != b"\x7fELF":
        raise BuildError("Extracted libalvm.so is not a valid ELF shared library.")
    machine = struct.unpack("<H", data[18:20])[0]
    expected = ELF_MACHINE[abi]
    if machine != expected:
        raise BuildError(
            "libalvm.so architecture mismatch for " + abi
            + ": ELF machine " + str(machine) + ", expected " + str(expected) + "."
        )


def extract_native_library(apk_path: Path, abi: str, destination: Path) -> None:
    if not apk_path.is_file():
        raise BuildError("ALVM Studio APK is missing: " + str(apk_path))
    entry = "lib/" + abi + "/libalvm.so"
    try:
        with zipfile.ZipFile(apk_path, "r") as archive:
            data = archive.read(entry)
    except (KeyError, zipfile.BadZipFile) as error:
        raise BuildError(
            "Could not extract " + entry + " from ALVM Studio APK. "
            "Confirm the app was built with this ABI."
        ) from error
    require_elf_arch(data, abi)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)


def verify_player_apk(apk_path: Path, abi: str) -> None:
    if not zipfile.is_zipfile(apk_path):
        raise BuildError("Gradle output is not a valid APK/ZIP archive.")
    try:
        with zipfile.ZipFile(apk_path, "r") as archive:
            names = set(archive.namelist())
            required = {
                "AndroidManifest.xml",
                "classes.dex",
                "assets/main.room",
                "lib/" + abi + "/libalvm.so",
            }
            missing = sorted(required - names)
            if missing:
                raise BuildError("Generated APK is missing required entries: " + ", ".join(missing))
            room = archive.read("assets/main.room")
            if len(room) < 8 or room[:4] != b"ALRM":
                raise BuildError("Generated APK contains invalid .room bytecode.")
            version = struct.unpack("<I", room[4:8])[0]
            if version != 10:
                raise BuildError("Generated APK does not contain .room v10 bytecode.")
            require_elf_arch(archive.read("lib/" + abi + "/libalvm.so"), abi)
            if len(archive.read("classes.dex")) < 8:
                raise BuildError("Generated APK contains an empty or truncated classes.dex.")
    except zipfile.BadZipFile as error:
        raise BuildError("Gradle output is not a readable APK archive.") from error


def replace_template_markers(
    project_dir: Path, project_name: str, display_name: str, application_id: str, abi: str
) -> None:
    replacements = {
        "{{APP_NAME}}": project_name,
        "{{APP_NAME_XML}}": html.escape(display_name),
        "{{APP_ID}}": application_id,
        "{{ABI}}": abi,
    }
    text_files = (
        project_dir / "settings.gradle",
        project_dir / "app" / "build.gradle",
        project_dir / "app" / "src" / "main" / "res" / "values" / "strings.xml",
        project_dir / "README.md",
    )
    for path in text_files:
        if not path.is_file():
            raise BuildError("Android Player template file is missing: " + str(path))
        text = path.read_text(encoding="utf-8")
        for marker, value in replacements.items():
            text = text.replace(marker, value)
        if "{{" in text or "}}" in text:
            raise BuildError("Unresolved template marker in " + str(path))
        path.write_text(text, encoding="utf-8")


def build(args: argparse.Namespace) -> Path:
    source = resolve_source(args.source, args.source_root)
    display_name = args.name.strip() if args.name else source.stem
    display_name = "".join(character for character in display_name if character.isprintable())[:80]
    if not display_name:
        display_name = "ALApp"
    output_name = safe_output_name(display_name)
    application_id = validate_application_id(
        args.application_id or default_application_id(output_name)
    )
    abi = args.abi
    if abi not in SUPPORTED_ABIS:
        raise BuildError("Unsupported ABI: " + abi)

    if not TEMPLATE.is_dir():
        raise BuildError("Android Player template folder is missing: " + str(TEMPLATE))

    compiler_build = ROOT / "build" / "compiler"
    compiler = compiler_build / ("alvm.exe" if os.name == "nt" else "alvm")
    if not compiler.is_file():
        run(["cmake", "-S", str(ROOT), "-B", str(compiler_build), "-DCMAKE_BUILD_TYPE=Release"], ROOT)
        run(["cmake", "--build", str(compiler_build), "--parallel"], ROOT)
    if not compiler.is_file():
        raise BuildError("CMake finished but the ALVM compiler executable is missing.")

    work_root = (ROOT / args.work_dir).resolve() if not Path(args.work_dir).is_absolute() else Path(args.work_dir).resolve()
    project_dir = work_root / (output_name + "-android-project")
    if project_dir.exists():
        shutil.rmtree(project_dir)
    project_dir.mkdir(parents=True, exist_ok=True)

    room_file = work_root / (output_name + ".room")
    room_file.parent.mkdir(parents=True, exist_ok=True)
    run([str(compiler), "compile", str(source), "-o", str(room_file)], ROOT)
    verify_room(room_file)

    gradle_command = args.gradle.split()
    if not gradle_command:
        raise BuildError("--gradle command cannot be empty.")
    run(gradle_command + ["--no-daemon", "--stacktrace", ":app:assembleDebug"], ROOT)
    native_library = project_dir / "app" / "src" / "main" / "jniLibs" / abi / "libalvm.so"
    extract_native_library(STUDIO_APK, abi, native_library)

    shutil.copytree(
        TEMPLATE, project_dir, dirs_exist_ok=True,
        ignore=shutil.ignore_patterns("workflow-template"),
    )
    replace_template_markers(project_dir, output_name, display_name, application_id, abi)

    workflow_source = TEMPLATE / "workflow-template" / "build-apk.yml"
    workflow_destination = project_dir / ".github" / "workflows" / "build-apk.yml"
    if not workflow_source.is_file():
        raise BuildError("Exported Android Player APK workflow template is missing.")
    workflow_destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(workflow_source, workflow_destination)

    assets_dir = project_dir / "app" / "src" / "main" / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(room_file, assets_dir / "main.room")
    if args.assets_dir:
        copied = copy_application_assets(args.assets_dir, assets_dir)
        print("Packaged application assets: " + str(copied))
    source_dir = project_dir / "AL_Source"
    source_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, source_dir / (safe_output_name(source.name)))

    run(
        gradle_command + [
            "--no-daemon", "--stacktrace", "-p", str(project_dir), ":app:assembleDebug"
        ],
        ROOT,
    )
    built_apk = project_dir / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    if not built_apk.is_file() or built_apk.stat().st_size < 1024:
        raise BuildError("Gradle reported success but no valid Player APK was produced.")
    verify_player_apk(built_apk, abi)

    output_path = Path(args.output).expanduser() if args.output else (
        ROOT / "dist" / (output_name + ".apk")
    )
    if not output_path.is_absolute():
        output_path = (ROOT / output_path).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(built_apk, output_path)
    digest = hashlib.sha256(output_path.read_bytes()).hexdigest()
    print("")
    print("APK generated: " + str(output_path))
    print("Package ID: " + application_id)
    print("ABI: " + abi)
    print("Bytecode: .room v10")
    print("Build type: debug-signed (installable; not a Play Store release)")
    print("Project: " + str(project_dir))
    print("SHA-256: " + digest)
    return output_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compile one .al program and build a standalone Android debug APK."
    )
    parser.add_argument("source", help="Path to the AL .al source file")
    parser.add_argument("--name", default=None, help="Android app display name")
    parser.add_argument("--application-id", default=None, help="Android package ID")
    parser.add_argument("--abi", choices=SUPPORTED_ABIS, default="arm64-v8a")
    parser.add_argument("--source-root", default=None, help="Restrict the source file to this directory")
    parser.add_argument("--assets-dir", default=None, help="Optional directory copied into the generated APK assets/")
    parser.add_argument("--output", default=None, help="APK output path (default: dist/<app-name>.apk)")
    parser.add_argument("--work-dir", default="build/android-apk", help="Temporary build directory")
    parser.add_argument("--gradle", default=os.environ.get("GRADLE", "gradle"),
                        help="Gradle executable or command")
    return parser.parse_args()


def main() -> int:
    try:
        build(parse_args())
        return 0
    except BuildError as error:
        print("ALVM Android APK build failed: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
