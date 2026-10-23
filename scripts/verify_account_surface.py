"""Check that supported account surfaces and packaged resources match the Microsoft-only policy."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import zipfile


ROOT = Path(__file__).resolve().parents[1]
EXTERNAL_INTEGRATION = re.compile(r"authlib[-_ ]?injector|blessing[ -]?skin|littleskin|皮肤站", re.I)
REMOVED_NAMES = (
    "AuthlibInjector", "AddAuthlibInjector", "YggdrasilAccount", "YggdrasilService",
    "YggdrasilProvider", "YggdrasilSession", "OfflineAccountSkin", "ClassicAccount", "CreateAccountPane",
)


def verify_source() -> dict:
    failures = []
    checked = 0
    schema = json.loads((ROOT / "docs/schemas/accounts/1.0.0.json").read_text(encoding="utf-8"))
    account_types = schema["properties"]["accounts"]["items"]["properties"]["type"]["enum"]
    if account_types != ["microsoft"]:
        failures.append("Account schema advertises an unsupported provider")
    roots = (ROOT / "HMCL/src/main/java", ROOT / "HMCLCore/src/main/java",
             ROOT / "HMCL/src/main/resources/assets/lang", ROOT / "docs")
    for base in roots:
        for path in base.rglob("*"):
            if not path.is_file() or path.suffix not in (".java", ".properties", ".md", ".json"):
                continue
            checked += 1
            relative = path.relative_to(ROOT).as_posix()
            if EXTERNAL_INTEGRATION.search(relative) or EXTERNAL_INTEGRATION.search(path.read_text(encoding="utf-8")):
                failures.append(relative)
    catalog = (ROOT / "HMCL/src/main/resources/assets/addon/mod.csv").read_text(encoding="utf-8")
    if any(name in catalog.lower() for name in ("authlib-injector", "yggdrasil-proxy", "multiyggdrasil")):
        failures.append("Addon catalog includes an external identity-injection component")
    if failures:
        raise SystemExit("Unsupported account integration remains: " + ", ".join(failures))
    return {"sourceFilesChecked": checked, "advertisedAccountTypes": account_types}


def verify_artifact(path: Path) -> dict:
    failures = []
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if EXTERNAL_INTEGRATION.search(name) or any(part in name for part in REMOVED_NAMES):
                failures.append(name)
            if name.endswith(".properties") and EXTERNAL_INTEGRATION.search(archive.read(name).decode("utf-8")):
                failures.append(name)
        required = {"org/jackhuang/hmcl/auth/microsoft/MicrosoftAccount.class",
                    "org/jackhuang/hmcl/auth/microsoft/MinecraftOwnershipVerifier.class",
                    "org/jackhuang/hmcl/auth/microsoft/MicrosoftProfileTextures.class"}
        if not required.issubset(archive.namelist()):
            failures.append("Required Microsoft authentication or profile component is missing")
    if failures:
        raise SystemExit(path.name + ": " + ", ".join(failures))
    return {"artifact": path.name, "unsupportedIntegrations": []}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts", action="store_true")
    args = parser.parse_args()
    result = verify_source()
    if args.artifacts:
        paths = [path for path in (ROOT / "HMCL/build/libs").glob("HMCL-*")
                 if path.suffix in (".jar", ".exe", ".sh")]
        if not {".jar", ".exe", ".sh"}.issubset({path.suffix for path in paths}):
            raise SystemExit("Build all launcher executable formats before artifact verification")
        result["artifacts"] = [verify_artifact(path) for path in sorted(paths)]
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
