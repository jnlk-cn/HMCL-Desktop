# HMCL Desktop

An independently maintained desktop launcher distribution derived from [HMCL](https://github.com/HMCL-dev/HMCL), maintained by **XDRi.Club**. This repository represents this distribution only, not the upstream HMCL maintainers.

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

[Project website](https://jnlk-cn.github.io/HMCL-Website/) · [中文说明](README-CN.md) · [Account access](docs/ACCOUNT-ACCESS.md) · [Privacy](PRIVACY.md)

## Accounts and game access

Every game launch requires a current, online Microsoft authentication session, a Java Edition game entitlement returned by Minecraft Services, and the matching Minecraft profile. A previously successful login or a cached profile does not grant offline access. A missing license, expired session, refused API request, malformed response or network outage blocks launch.

Microsoft accounts are the sole authentication provider in this distribution. Game access always requires fresh ownership verification.

Microsoft, Xbox and Minecraft authentication uses official endpoints. Availability also depends on Microsoft's and Mojang's permissions for the application's public client ID. A functioning OAuth page or device code does not establish Minecraft Services access.

## Downloads and distribution

- Minecraft manifests, game binaries, asset indexes and assets use official Mojang HTTPS sources without a community mirror fallback.
- Third-party loader and addon metadata can use BMCLAPI/MCIM and their original sources. These services do not receive Microsoft credentials.
- Release packages contain the launcher, launcher dependencies, corresponding source and checksums. They contain no Minecraft game binaries, game resource packs, accounts or personal game configuration.
- Launcher telemetry, public-IP geolocation and launcher self-updates are disabled. Game installation and user-requested game updates remain available.
- Exporting reusable credential-bearing game launch scripts is disabled; launch through HMCL to run account checks.

The launcher keeps HMCL's interface layout and displays version **3.17.0.362**. Its software name is **HMCL Desktop** to distinguish this modified distribution under the upstream license terms. Independent builds are not signed or marked as upstream official releases. [Source](https://github.com/jnlk-cn/HMCL-Desktop) and [release downloads](https://github.com/jnlk-cn/HMCL-Desktop/releases/latest) are publicly available.

## Build

Use JDK 21 or newer and Python 3.10 or newer. On Windows:

```powershell
.\Build.ps1 -JavaHome 'C:\Path\To\jdk-21'
```

The script compiles, runs the launcher/core/boot tests and applicable style checks, builds JAR/EXE/shell artifacts, and packages a launcher-only Windows archive with verified JavaFX dependencies. Java itself is not bundled. Output: `HMCL/build/libs` and `dist`.

Source and artifact checks enforce the supported Microsoft account surface. Portable archives include only explicitly generated launcher files, dependency files and project documentation; existing local files and internal test reports are excluded.

For Gradle-only builds, set `VERSION_ROOT=3.17.0`, `BUILD_NUMBER=362` and `VERSION_TYPE=unofficial`, then run `./gradlew :HMCL:makeExecutables :HMCL:test :HMCLCore:test :HMCLBoot:test`. The public OAuth application ID is configured in `config/project.properties`; a desktop public client has no embedded client secret.

Local credentials, account files and private keys are excluded by `.gitignore`. Pushes and pull requests scan the fetched Git history with Gitleaks and redact scanner output. The scanner configuration makes narrow exceptions for this application's public client ID and retained public upstream QQ invitation links.

## Provenance and license

This distribution, including its independently maintained modifications, is licensed under the **GNU General Public License, version 3 or (at your option) any later version**, together with the [upstream additional terms under GPLv3 Section 7](ADDITIONAL-TERMS.md). Third-party components retain their respective licenses. The complete GPL text is in [LICENSE](LICENSE).

Redistribution must preserve copyright and legal notices, identify modified versions as required by the upstream terms, and provide the corresponding source under the applicable GPL terms when distributing binaries. Each release includes matching source, license notices and SHA-256 checksums.

See [source provenance](UPSTREAM.md), [upstream metadata](config/upstream.json) and [distribution changes](CHANGES.md). Original copyright notices and upstream license terms are retained. XDRi.Club's distribution is maintained independently and makes no claim of endorsement by upstream HMCL, Mojang Studios or Microsoft.
