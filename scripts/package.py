"""Package the launcher, dependencies and corresponding source without personal or game data."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[1]


def digest(path: Path, algorithm: str = "sha256") -> str:
    hasher = hashlib.new(algorithm)
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def main() -> None:
    properties = dict(
        line.split("=", 1)
        for line in (ROOT / "HMCL/build/hmcl.properties").read_text(encoding="utf-8").splitlines()
        if line and not line.startswith("#") and "=" in line
    )
    version = properties["hmcl.version"]
    label = f"HMCL-{version}"
    output = ROOT / "dist"
    delivery = output / label
    delivery.mkdir(parents=True, exist_ok=True)

    artifacts: list[Path] = []
    for suffix in ("exe", "jar", "sh"):
        source = ROOT / "HMCL/build/libs" / f"HMCL-{version}.{suffix}"
        if not source.is_file():
            raise SystemExit(f"Missing build artifact: {source}")
        target = delivery / source.name
        shutil.copy2(source, target)
        artifacts.append(target)

    # Only explicitly produced launcher files may enter a portable archive.
    package_files = list(artifacts)

    # Cache Windows x64 JavaFX, verifying bytes against upstream's locked hashes.
    javafx = json.loads((ROOT / "HMCL/src/main/resources/assets/openjfx-dependencies.json").read_text())
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    cache = gradle_home / "caches/modules-2/files-2.1"
    dependencies: list[dict[str, str]] = []
    for descriptor in javafx["windows-x86_64"]["classic"]:
        filename = "{artifactId}-{version}-{classifier}.jar".format(**descriptor)
        candidates = (cache / descriptor["groupId"] / descriptor["artifactId"] / descriptor["version"]).glob(f"*/{filename}")
        source = next((path for path in candidates if digest(path, "sha1") == descriptor["sha1"]), None)
        if source is None:
            raise SystemExit(f"Verified JavaFX runtime is missing from Gradle cache: {filename}")
        target = delivery / ".hmcl/dependencies/windows-x86_64/openjfx" / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        package_files.append(target)
        dependencies.append({"file": str(target.relative_to(delivery)).replace("\\", "/"),
                             "sha1": descriptor["sha1"], "sha256": digest(target)})

    script = f'''@echo off
setlocal
cd /d "%~dp0"
set "HMCL_USER_HOME=%~dp0.hmcl\\user"
set "HMCL_LOCAL_HOME=%~dp0.hmcl"
set "HMCL_DEPENDENCIES_DIR=%~dp0.hmcl\\dependencies"
start "" "%~dp0HMCL-{version}.exe"
endlocal
'''
    # The commands and paths inside the script are ASCII, independent of the script's display name.
    entry_point = delivery / "启动 HMCL.cmd"
    entry_point.write_bytes(script.replace("\n", "\r\n").encode("ascii"))
    package_files.append(entry_point)
    for source_name in ("README.md", "README-CN.md", "UPSTREAM.md", "PRIVACY.md", "LICENSE",
                        "ADDITIONAL-TERMS.md", "CHANGES.md"):
        target = delivery / source_name
        shutil.copy2(ROOT / source_name, target)
        package_files.append(target)

    upstream = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    build_info = delivery / "BUILD-INFO.json"
    build_info.write_text(json.dumps({
        "distributionName": "HMCL Desktop", "maintainer": "XDRi.Club", "displayName": "HMCL Desktop", "version": version,
        "upstreamRepository": "https://github.com/HMCL-dev/HMCL",
        "sourceCommit": upstream,
        "telemetry": "removed; local diagnostics only",
        "launcherSelfUpdate": "removed",
        "microsoftOAuthClientId": properties.get("hmcl.microsoft.auth.id", ""),
        "javaMinimum": 17, "recommendedJava": 21,
        "bundledRuntime": "JavaFX for Windows x64; Java itself is not bundled",
        "javafx": dependencies,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    package_files.append(build_info)

    # Pin the corresponding source to the same clean commit recorded in the binary.
    if subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT).strip():
        raise SystemExit("Commit all source changes before packaging a release.")
    if properties.get("hmcl.version.hash") != upstream:
        raise SystemExit("Rebuild with GITHUB_SHA set to the current source commit before packaging.")
    source_zip = output / f"{label}-source.zip"
    subprocess.run(["git", "archive", "--format=zip", f"--prefix={label}-source/",
                    "--output", str(source_zip), upstream], cwd=ROOT, check=True)

    # A local checksum manifest also covers the pre-cached JavaFX dependencies.
    checksums = []
    for path in sorted(package_files):
        checksums.append(f"{digest(path)}  {path.relative_to(delivery).as_posix()}")
    manifest = delivery / "SHA256SUMS.txt"
    manifest.write_text("\n".join(checksums) + "\n", encoding="utf-8")
    package_files.append(manifest)
    portable_zip = output / f"{label}-windows-x64-portable.zip"
    with zipfile.ZipFile(portable_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(package_files):
            archive.write(path, f"{label}/{path.relative_to(delivery).as_posix()}")

    packages = (source_zip, portable_zip)
    (output / "SHA256SUMS.txt").write_text(
        "\n".join(f"{digest(path)}  {path.name}" for path in packages) + "\n", encoding="utf-8"
    )
    for path in [*artifacts, *packages]:
        print(f"{path.name}: {path.stat().st_size:,} bytes, SHA256 {digest(path)}")


if __name__ == "__main__":
    main()
