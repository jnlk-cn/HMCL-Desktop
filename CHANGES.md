# Independently maintained changes

Snapshot date: **October 23, 2026**. Maintainer: **XDRi.Club**.

This source snapshot modifies upstream HMCL at the baseline recorded in [UPSTREAM.md](UPSTREAM.md). The principal maintained changes are:

- Microsoft accounts are the only supported account type. Every game launch requires online authentication, verified Java Edition ownership and the matching official Minecraft profile.
- Failed authentication, malformed responses, expired sessions, missing ownership or unavailable services stop launch. Launch authorization is short-lived, single-use and held in memory.
- Minecraft manifests, game files and assets use official Mojang HTTPS sources. Third-party loader and addon metadata can use BMCLAPI/MCIM and their original sources.
- Launcher telemetry, public-IP geolocation and launcher self-updates are disabled. User-requested game installation and game updates remain available.
- Reusable credential-bearing game launch scripts are not exported.
- Build and packaging checks enforce the supported account surface and exclude local account state, private configuration and arbitrary staging files.
- The software name is HMCL Desktop, with the numeric version retained from the recorded upstream reference. Original copyright declarations and credits are preserved.

These modifications are distributed under the [GPL](LICENSE) and the [upstream additional terms](ADDITIONAL-TERMS.md). Availability of Microsoft and Minecraft Services depends on permissions granted by their operators; source publication does not establish that such access has been granted.
