"""Verify built launcher policy, local update rejection, and optional official-source downloads."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
BMCL = "https://bmclapi2.bangbang93.com"


def json_request(url: str) -> object:
    request = urllib.request.Request(url, headers={"User-Agent": "HMCL-Verification/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def verified_download(url: str, expected: str, destination: Path, fallback_url: str | None = None) -> dict:
    failures = []
    for candidate in ([url, fallback_url] if fallback_url else [url]):
        try:
            result = download_candidate(candidate, expected, destination)
            result.update({"requestedUrl": url, "usedFallback": candidate != url, "failedCandidates": failures})
            return result
        except Exception as error:
            failures.append({"url": candidate, "error": str(error)})
    raise RuntimeError(f"All file candidates failed: {failures}")


def download_candidate(url: str, expected: str, destination: Path) -> dict:
    hasher = hashlib.sha1()
    started = time.monotonic()
    with urllib.request.urlopen(url, timeout=45) as response, destination.open("wb") as output:
        final_host = urllib.parse.urlsplit(response.url).hostname
        total = 0
        for chunk in iter(lambda: response.read(1024 * 1024), b""):
            total += len(chunk)
            if total > 100 * 1024 * 1024:
                raise ValueError("Probe file exceeded its download limit")
            hasher.update(chunk)
            output.write(chunk)
    actual = hasher.hexdigest()
    if actual != expected:
        raise ValueError(f"SHA-1 mismatch for {destination.name}: {actual} != {expected}")
    return {"url": url, "file": destination.name, "bytes": total, "sha1": actual,
            "verified": True, "finalHost": final_host, "seconds": round(time.monotonic() - started, 2)}


def live_probe(qa: Path) -> dict:
    manifest = json_request("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    entry = next(item for item in manifest["versions"] if item["id"] == "1.20.1")
    version_url = entry["url"]
    version_file = qa / "minecraft-1.20.1-version.json"
    version_result = verified_download(version_url, entry["sha1"], version_file)
    version = json.loads(version_file.read_text(encoding="utf-8"))
    index = version["assetIndex"]
    index_file = qa / "minecraft-1.20.1-assets.json"
    index_result = verified_download(index["url"], index["sha1"], index_file)
    objects = json.loads(index_file.read_text(encoding="utf-8"))["objects"]
    asset_name, asset = min(objects.items(), key=lambda item: item[1]["size"])
    asset_result = verified_download("https://resources.download.minecraft.net/" + asset["hash"][:2] + "/" + asset["hash"],
                                     asset["hash"], qa / "minecraft-asset.bin")
    asset_result["assetName"] = asset_name
    library = min((item["downloads"]["artifact"] for item in version["libraries"]
                   if "artifact" in item.get("downloads", {})), key=lambda item: item["size"])
    library_result = verified_download(library["url"], library["sha1"], qa / "minecraft-library.jar")
    client = version["downloads"]["client"]
    client_result = verified_download(client["url"], client["sha1"], qa / "minecraft-1.20.1-client.jar")
    endpoints = [
        BMCL + "/forge/minecraft/1.20.1", BMCL + "/neoforge/list/1.21.1",
        BMCL + "/fabric-meta/v2/versions/loader/1.20.1",
        "https://mod.mcimirror.top/modrinth/v2/search?query=sodium&limit=1",
        "https://mod.mcimirror.top/curseforge/v1/mods/search?gameId=432&searchFilter=jei&pageSize=1",
        "https://api.modrinth.com/v2/search?query=sodium&limit=1",
    ]

    def check(url: str) -> dict:
        try:
            value = json_request(url)
            return {"url": url, "status": 200, "jsonType": type(value).__name__, "items": len(value)}
        except urllib.error.HTTPError as error:
            return {"url": url, "status": error.code}
        except Exception as error:
            return {"url": url, "error": str(error)}

    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(check, endpoints))
    for result in results[:3]:
        if result.get("status") != 200 or not result.get("items"):
            raise RuntimeError(f"BMCLAPI metadata probe failed: {result}")
    return {"minecraftVersion": "1.20.1", "manifestVersions": len(manifest["versions"]),
            "verifiedFiles": [version_result, index_result, asset_result, library_result, client_result],
            "apiResults": results,
            "scope": "Live file/API probe; not a complete game installation or account login test"}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", default="java")
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--microsoft-auth-id", help="Expected public OAuth client ID; defaults to the project configuration")
    args = parser.parse_args()
    qa = ROOT / "qa"
    qa.mkdir(exist_ok=True)
    properties = dict(line.split("=", 1) for line in (ROOT / "HMCL/build/hmcl.properties").read_text().splitlines()
                      if "=" in line)
    version = properties["hmcl.version"]
    project_properties = dict(line.split("=", 1) for line in (ROOT / "config/project.properties").read_text().splitlines()
                              if line and not line.startswith("#") and "=" in line)
    expected_auth_id = args.microsoft_auth_id or project_properties.get("microsoftAuthId", "")
    if expected_auth_id:
        uuid.UUID(expected_auth_id)
    jar = ROOT / f"HMCL/build/libs/HMCL-{version}.jar"
    policy = []
    for suffix in ("jar", "exe"):
        with zipfile.ZipFile(jar.with_suffix("." + suffix)) as archive:
            embedded_properties = dict(line.split("=", 1) for line in archive.read("assets/hmcl.properties").decode("utf-8").splitlines()
                                       if line and not line.startswith("#") and "=" in line)
            if expected_auth_id and embedded_properties.get("hmcl.microsoft.auth.id") != expected_auth_id:
                raise RuntimeError(f"Wrong Microsoft OAuth client ID embedded in {suffix}")
            if embedded_properties.get("hmcl.version") != "3.17.0.362" or embedded_properties.get("hmcl.version.type") != "unofficial":
                raise RuntimeError("Wrong displayed version or upstream official build status")
            if b"HMCL Desktop" not in archive.read("org/jackhuang/hmcl/Metadata.class"):
                raise RuntimeError("Modified distribution is missing its distinguishing software name")
            for notice in ("LICENSE", "ADDITIONAL-TERMS.md", "UPSTREAM.md", "CHANGES.md"):
                if archive.read("assets/licenses/hmcl/" + notice) != (ROOT / notice).read_bytes():
                    raise RuntimeError("Packaged license or provenance notice differs from source: " + notice)
            names = archive.namelist()
            required = {"org/jackhuang/hmcl/auth/GameAccessPolicy.class",
                        "org/jackhuang/hmcl/auth/microsoft/MinecraftOwnershipVerifier.class"}
            if not required.issubset(set(names)):
                raise RuntimeError("Game ownership policy missing from artifact")
            forbidden = [name for name in names if "/countly/" in name
                         or "/auth/offline/" in name or "/auth/authlibinjector/" in name
                         or "authlib-injector" in name.lower()
                         or name.endswith((
                "/ClassicAccount.class", "/YggdrasilAccount.class", "/YggdrasilService.class",
                "/YggdrasilProvider.class", "/YggdrasilSession.class", "/CreateAccountPane.class",
                "/AddAuthlibInjectorServerPane.class", "/OfflineAccountSkinPane.class",
                "/UpdateChecker.class", "/UpdateHandler.class", "/HMCLDownloadTask.class", "/UpgradeDialog.class",
                "/ExecutableHeaderHelper.class"))]
            bad_constants = []
            for name in names:
                if name.endswith(".class"):
                    data = archive.read(name)
                    for value in (b"ipwho.is", b"/api/update_link", b"countly.hmcl", b"count.ly", b"CN Edition", b"cn.edition"):
                        if value in data:
                            bad_constants.append([name, value.decode()])
                    if name.endswith("/ui/main/MainPage.class") and any(value in data for value in (
                            b"update.channel.nightly", b"update.channel.dev", b"announcement-close-button")):
                        bad_constants.append([name, "development channel announcement"])
            if forbidden or bad_constants:
                raise RuntimeError({"classes": forbidden, "constants": bad_constants})
            policy.append({"artifact": suffix, "entries": len(names), "forbiddenClasses": [], "forbiddenEndpointsOrUiText": []})
    with tempfile.TemporaryDirectory(prefix="hmcl-update-rejection-") as temporary:
        target = Path(temporary) / "unchanged.jar"
        sentinel = b"HMCL launcher update rejection sentinel"
        target.write_bytes(sentinel)
        result = subprocess.run([args.java, "-jar", str(jar), "--apply-to", str(target)],
                                capture_output=True, text=True, timeout=15)
        if result.returncode != 1 or target.read_bytes() != sentinel:
            raise RuntimeError("Legacy update rejection test failed")
        rejection = {"exitCode": result.returncode, "targetUnchanged": True, "stderr": result.stderr.strip()}
    summary = {}
    for module in ("HMCL", "HMCLCore", "HMCLBoot"):
        counts = dict(tests=0, failures=0, errors=0, skipped=0)
        for path in (ROOT / module / "build/test-results/test").glob("TEST-*.xml"):
            suite = ET.parse(path).getroot()
            for key in counts:
                counts[key] += int(suite.get(key, 0))
        if not counts["tests"] or counts["failures"] or counts["errors"]:
            raise RuntimeError({module: counts})
        summary[module] = counts
    report = {"version": version, "sourceCommit": properties.get("hmcl.version.hash"),
              "distributionName": "HMCL Desktop",
              "license": {"name": "GPL-3.0-or-later with upstream Section 7 additional terms",
                          "noticesEmbeddedAndMatched": True},
              "binaryPolicy": policy, "legacyUpdateRejection": rejection, "testSummary": summary}
    if expected_auth_id:
        report["microsoftOAuth"] = {"clientId": expected_auth_id, "embeddedInJarAndExe": True,
                                    "clientType": "public desktop", "realAccountLoginVerified": False}
    if args.live:
        report["live"] = live_probe(qa)
    (qa / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
