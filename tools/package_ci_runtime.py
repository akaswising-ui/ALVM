#!/usr/bin/env python3
"""Create an offline .ciruntime update package from a CI Runtime Python module."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "app" / "src" / "main" / "python" / "ci_runtime_engine.py"


def main() -> int:
    parser = argparse.ArgumentParser(description="Package a versioned CI Runtime update.")
    parser.add_argument("--version", required=True, help="Runtime version, e.g. 1.0.1")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=ROOT / "build" / "ci-runtime-update.ciruntime")
    args = parser.parse_args()

    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?", args.version):
        parser.error("--version must use numeric major.minor.patch format")

    source = args.source.read_text(encoding="utf-8")
    source, count = re.subn(
        r'^RUNTIME_VERSION\s*=\s*"[^"]+"\s*$',
        f'RUNTIME_VERSION = "{args.version}"',
        source,
        count=1,
        flags=re.MULTILINE,
    )
    if count != 1:
        raise SystemExit("Source must define RUNTIME_VERSION exactly once")
    compile(source, str(args.source), "exec")
    payload = source.encode("utf-8")
    manifest = {
        "name": "CI Runtime",
        "version": args.version,
        "runtime_api_version": 1,
        "room_format_min": 9,
        "room_format_max": 9,
        "file": "runtime.py",
        "sha256": hashlib.sha256(payload).hexdigest(),
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", json.dumps(manifest, indent=2))
        archive.writestr("runtime.py", payload)
    print(f"Created {args.output}")
    print(f"CI Runtime {args.version}; .room formats 9; SHA-256 {manifest['sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
