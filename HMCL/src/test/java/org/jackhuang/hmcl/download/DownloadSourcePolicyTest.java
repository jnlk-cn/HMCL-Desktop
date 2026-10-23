/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies official-only Mojang content and independent third-party mirror routing.
@NotNullByDefault
final class DownloadSourcePolicyTest {
    /// Community source used only for third-party components.
    private static final String ROOT = "https://bmclapi2.bangbang93.com";

    /// Returns the candidate URLs in the order in which downloads would be attempted.
    private static @Unmodifiable List<String> urls(DownloadCandidates candidates) {
        return candidates.getCandidates().stream().map(DownloadCandidate::displayUrl).toList();
    }

    /// Region and stored mirror settings cannot redirect the vanilla manifest or assets.
    @ParameterizedTest
    @ValueSource(strings = {"DEFAULT", "MIRROR", "OFFICIAL"})
    void vanillaSourcesHaveNoMirrorFallback(String mode) {
        var provider = new HMCLDownloadProvider(ROOT);
        provider.setVersionListSource(DownloadSource.valueOf(mode));
        provider.setFileSource(DownloadSource.valueOf(mode));
        assertEquals(List.of("https://piston-meta.mojang.com/mc/game/version_manifest.json"), urls(provider.getGameVersionListCandidates()));
        String hash = "0123456789012345678901234567890123456789";
        assertEquals(List.of("https://resources.download.minecraft.net/01/" + hash),
                urls(provider.getAssetObjectCandidates(new AssetObject(hash, 1))));
    }

    /// Official Mojang files remain official for every generic download route as well.
    @ParameterizedTest
    @ValueSource(strings = {
            "https://piston-data.mojang.com/v1/objects/example/client.jar",
            "https://piston-meta.mojang.com/v1/packages/example.json",
            "https://libraries.minecraft.net/example.jar",
            "https://resources.download.minecraft.net/ab/asset",
            "https://launcher.mojang.com/example.jar"
    })
    void officialFilesNeverReceiveMirrorCandidates(String url) {
        var provider = new HMCLDownloadProvider("https://custom.example/bmcl");
        assertEquals(List.of(url), urls(provider.getGameFileCandidates(url)));
        assertEquals(List.of(url), urls(provider.getDownloadCandidates(url)));
        assertEquals(List.of(url), urls(provider.getVersionListCandidates(url)));
    }

    /// Imported vanilla manifests cannot introduce mirror URLs or lookalike official domains.
    @ParameterizedTest
    @ValueSource(strings = {
            "https://bmclapi2.bangbang93.com/version/client.jar",
            "https://mirror.example/client.jar",
            "https://piston-data.mojang.com.evil.example/client.jar",
            "https://piston-data.mojang.com@evil.example/client.jar",
            "file:///tmp/client.jar",
            "https://s3.amazonaws.com/UnrelatedBucket/client.jar"
    })
    void rejectsUntrustedMinecraftContentSources(String url) {
        var provider = new HMCLDownloadProvider(ROOT);
        assertThrows(IllegalArgumentException.class, () -> provider.getGameFileCandidates(url));
    }

    /// Legacy official HTTP links are upgraded to HTTPS rather than sent unencrypted.
    @Test
    void upgradesOfficialLegacyLinks() {
        var provider = new HMCLDownloadProvider(ROOT);
        assertEquals(List.of("https://launcher.mojang.com/client.jar"),
                urls(provider.getGameFileCandidates("http://launcher.mojang.com/client.jar")));
    }

    /// Loader metadata and addon metadata remain independently configurable.
    @Test
    void preservesThirdPartySources() {
        var provider = new HMCLDownloadProvider(ROOT);
        assertEquals(List.of(ROOT + "/maven/net/fabricmc/loader.jar", "https://maven.fabricmc.net/net/fabricmc/loader.jar"),
                urls(provider.getDownloadCandidates("https://maven.fabricmc.net/net/fabricmc/loader.jar")));
        assertEquals(List.of("https://mod.mcimirror.top/modrinth/v2/search", "https://api.modrinth.com/v2/search"),
                urls(provider.getVersionListCandidates("https://api.modrinth.com/v2/search")));
    }

    /// Official Microsoft and Mojang authentication URLs retain their origin through download routing.
    @ParameterizedTest
    @ValueSource(strings = {
            "https://login.live.com/oauth20_token.srf",
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            "https://sessionserver.mojang.com/session/minecraft/join"
    })
    void preservesAuthenticationEndpoints(String url) {
        assertEquals(List.of(url), urls(new HMCLDownloadProvider(ROOT).getDownloadCandidates(url)));
    }
}
