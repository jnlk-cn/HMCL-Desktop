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

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.cleanroom.CleanroomRemoteVersion;
import org.jackhuang.hmcl.download.fabric.FabricRemoteVersion;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.neoforge.NeoForgeRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/// Keeps Mojang game content on official sources while supporting mirrors for third-party components.
@NotNullByDefault
public final class HMCLDownloadProvider extends DownloadProvider {
    /// Preferred source for component and addon metadata.
    private volatile DownloadSource versionListSource = DownloadSource.MIRROR;
    /// Preferred source for game files, libraries, and assets.
    private volatile DownloadSource fileSource = DownloadSource.MIRROR;

    /// Orders a known mirror and its official fallback without querying the user's region.
    private static DownloadCandidates getCandidates(
            DownloadSource source,
            WebURL defaultUrl, WebURL bmclapiUrl
    ) {
        return source == DownloadSource.OFFICIAL
                ? DownloadCandidates.of(defaultUrl, bmclapiUrl)
                : DownloadCandidates.of(bmclapiUrl, defaultUrl);
    }

    /// Rewrites supported service roots and orders candidates according to the user's selection.
    private static DownloadCandidates getCandidates(DownloadSource source, List<MirrorRule> rules, List<String> urls) {
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("urls cannot be empty");
        }

        boolean mirrorFirst = source != DownloadSource.OFFICIAL;

        var candidates = new ArrayList<DownloadCandidate>(urls.size() * 2);
        for (String url : urls) {
            if (url.startsWith("http://") && isOfficialGameSource("https://" + url.substring(7))) {
                url = "https://" + url.substring(7);
            }
            DownloadCandidate candidate = DownloadCandidate.of(url);
            if (candidate.url() == null) {
                // Invalid URL, just add it to the list and let the download task handle it.
                candidates.add(candidate);
                continue;
            }

            // Mojang game files never fall back to a community mirror, regardless of saved preferences.
            if (isOfficialGameSource(url)) {
                candidates.add(candidate);
                continue;
            }

            @Nullable DownloadCandidate mirrorCandidate = null;
            for (MirrorRule rule : rules) {
                if (rule.matches(url)) {
                    mirrorCandidate = DownloadCandidate.of(rule.target + url.substring(rule.source.length()));
                    break;
                }
            }

            if (mirrorCandidate == null) {
                candidates.add(candidate);
            } else if (mirrorCandidate.equals(candidate)) {
                candidates.add(candidate);
            } else if (mirrorFirst) {
                candidates.add(mirrorCandidate);
                candidates.add(candidate);
            } else {
                candidates.add(candidate);
                candidates.add(mirrorCandidate);
            }
        }

        return DownloadCandidates.of(List.copyOf(candidates));
    }

    /// Recognizes official Mojang game-content roots using the same host-boundary rules as mirror routing.
    private static boolean isOfficialGameSource(String url) {
        return OFFICIAL_GAME_ROOTS.stream().anyMatch(root -> new MirrorRule(root, root).matches(url));
    }

    /// Official roots whose files and metadata must retain their original HTTPS source.
    private static final @Unmodifiable List<String> OFFICIAL_GAME_ROOTS = List.of(
            "https://launchermeta.mojang.com", "https://piston-meta.mojang.com",
            "https://piston-data.mojang.com", "https://launcher.mojang.com",
            "https://libraries.minecraft.net", "https://resources.download.minecraft.net",
            "https://s3.amazonaws.com/Minecraft.Download", "https://s3.amazonaws.com/MinecraftResources"
    );

    /// BMCLAPI service root, including any explicitly configured private mirror prefix.
    private final String bmclRoot;
    /// Supported mirror rewrites; authentication services are deliberately absent.
    private final @Unmodifiable List<MirrorRule> rules;

    /// Creates a provider using the supplied BMCLAPI root.
    public HMCLDownloadProvider(String bmclRoot) {
        this.bmclRoot = bmclRoot;
        this.rules = List.of(
                new MirrorRule("https://bmclapi2.bangbang93.com", bmclRoot),
                new MirrorRule("http://files.minecraftforge.net/maven", bmclRoot + "/maven"),
                new MirrorRule("https://files.minecraftforge.net/maven", bmclRoot + "/maven"),
                new MirrorRule("https://maven.minecraftforge.net", bmclRoot + "/maven"),
                new MirrorRule("https://maven.neoforged.net/releases/", bmclRoot + "/maven/"),
                new MirrorRule("http://dl.liteloader.com/versions", bmclRoot + "/maven"),
                new MirrorRule("https://dl.liteloader.com/versions", bmclRoot + "/maven"),
                new MirrorRule("https://meta.fabricmc.net", bmclRoot + "/fabric-meta"),
                new MirrorRule("https://maven.fabricmc.net", bmclRoot + "/maven"),
                new MirrorRule("https://repo1.maven.org/maven2", "https://maven.aliyun.com/repository/central"),
                new MirrorRule("https://repo.maven.apache.org/maven2", "https://maven.aliyun.com/repository/central"),
                new MirrorRule("https://hmcl.glavo.site/metadata/cleanroom", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/cleanroom"),
                new MirrorRule("https://hmcl.glavo.site/metadata/fmllibs", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/fmllibs"),

                // https://github.com/mcmod-info-mirror/mcim-rust-api
                // MCIM currently mirrors metadata only; file CDN URLs must remain usable.
                new MirrorRule("https://api.modrinth.com", "https://mod.mcimirror.top/modrinth"),
                new MirrorRule("https://api.curseforge.com", "https://mod.mcimirror.top/curseforge")
        );
    }

    /// Fetches Forge, NeoForge, and OptiFine metadata through BMCLAPI where supported.
    @Override
    protected Task<? extends ComponentRemoteVersionList<?>> fetchVersionsAsync(GameComponentType type, @Nullable GameVersionNumber gameVersion) {
        assert (gameVersion == null) == (type == GameComponentType.GAME);
        switch (type) {
            case FORGE -> {
                Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchOfficial = ForgeRemoteVersion.fetchAsync(this, gameVersion);

                DownloadSource source = versionListSource;
                if (source == DownloadSource.OFFICIAL) {
                    return fetchOfficial;
                }

                Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchBMCL = ForgeRemoteVersion.fetchBMCLAsync(this, bmclRoot, gameVersion);
                return new FallbackTask<>(fetchBMCL, fetchOfficial);
            }
            case NEO_FORGE -> {
                Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchOfficial = NeoForgeRemoteVersion.fetchAsync(this, gameVersion);

                DownloadSource source = versionListSource;
                if (source == DownloadSource.OFFICIAL) {
                    return fetchOfficial;
                }
                Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchBMCL = NeoForgeRemoteVersion.fetchBMCLAsync(bmclRoot, gameVersion);
                return new FallbackTask<>(fetchBMCL, fetchOfficial);
            }
            case OPTIFINE -> {
                return OptiFineRemoteVersion.fetchBMCLAsync(this, bmclRoot, gameVersion);
            }
        }

        return super.fetchVersionsAsync(type, gameVersion);
    }

    /// Changes file download preference for subsequent requests.
    public void setFileSource(DownloadSource fileSource) {
        this.fileSource = Objects.requireNonNull(fileSource);
    }

    /// Changes version and addon metadata preference for subsequent requests.
    public void setVersionListSource(DownloadSource versionListSource) {
        this.versionListSource = Objects.requireNonNull(versionListSource);
    }

    /// Returns the launcher's existing cache repository.
    @Override
    public HMCLCacheRepository getCacheRepository() {
        return HMCLCacheRepository.REPOSITORY;
    }

    /// Returns the official Minecraft version manifest without a mirror fallback.
    @Override
    public DownloadCandidates getGameVersionListCandidates() {
        return DownloadCandidates.of(GameRemoteVersion.VERSION_MANIFEST_URL);
    }

    /// Returns Fabric loader and game metadata candidates.
    @Override
    public FabricLikeVersionListCandidates getFabricVersionListCandidates() {
        DownloadSource source = versionListSource;
        return new FabricLikeVersionListCandidates(
                getCandidates(source, FabricRemoteVersion.LOADER_META_URL, WebURL.parse(bmclRoot + "/fabric-meta/v2/versions/loader")),
                getCandidates(source, FabricRemoteVersion.GAME_META_URL, WebURL.parse(bmclRoot + "/fabric-meta/v2/versions/game"))
        );
    }

    /// Returns the existing Cleanroom metadata mirror and official fallback.
    @Override
    public DownloadCandidates getCleanroomVersionListCandidates() {
        return getCandidates(versionListSource, CleanroomRemoteVersion.LOADER_LIST_URL,
                WebURL.parse("https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/cleanroom/index.json"));
    }

    /// Rejects non-Mojang Minecraft game-file URLs, including imported community mirror URLs.
    @Override
    public DownloadCandidates getGameFileCandidates(List<String> urls) {
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("Minecraft game-file URLs are missing.");
        }
        @Unmodifiable List<String> official = urls.stream().map(url -> {
            String secure = url.startsWith("http://") ? "https://" + url.substring(7) : url;
            if (!isOfficialGameSource(secure)) {
                throw new IllegalArgumentException("Minecraft game files require an official Mojang HTTPS source.");
            }
            return secure;
        }).toList();
        return DownloadCandidates.of(official.stream().map(DownloadCandidate::of).toList());
    }

    /// Applies file mirror rules to the supplied original URLs.
    @Override
    public DownloadCandidates getDownloadCandidates(List<String> urls) {
        return getCandidates(fileSource, rules, urls);
    }

    /// Applies metadata mirror rules to the supplied original URLs.
    @Override
    public DownloadCandidates getVersionListCandidates(List<String> urls) {
        return getCandidates(versionListSource, rules, urls);
    }

    /// Fetches Mojang asset objects directly without a mirror fallback.
    @Override
    public DownloadCandidates getAssetObjectCandidates(AssetObject assetObject) {
        return DownloadCandidates.of(WebURL.parse("https://resources.download.minecraft.net/" + assetObject.getLocation()));
    }

    /// Runs the fallback task only if the preferred metadata request fails.
    @NotNullByDefault
    private static final class FallbackTask<T> extends Task<T> {

        /// Preferred metadata task.
        private final Task<? extends T> task;
        /// Metadata task used after a failed preferred request.
        private final Task<? extends T> fallback;

        /// Deferred tasks scheduled after the preferred request finishes.
        private @Unmodifiable List<Task<?>> dependencies = List.of();

        /// Connects the preferred and fallback requests.
        public FallbackTask(Task<? extends T> task, Task<? extends T> fallback) {
            this.task = task;
            this.fallback = fallback;
        }

        /// Runs the preferred request before this task.
        @Override
        public Collection<? extends Task<?>> getDependents() {
            return List.of(task);
        }

        /// Allows this task to recover from the preferred request's failure.
        @Override
        public boolean isRelyingOnDependents() {
            return false;
        }

        /// Publishes the preferred result or schedules the fallback.
        @Override
        public void execute() throws Exception {
            if (isDependentsSucceeded()) {
                setResult(task.getResult());
            } else {
                dependencies = List.of(fallback);
                fallback.storeTo(this::setResult);
            }
        }

        /// Returns fallback work selected by [#execute].
        @Override
        public @Unmodifiable List<Task<?>> getDependencies() {
            return dependencies;
        }
    }

    /// A service-root rewrite that cannot match a different host with a similar prefix.
    /// @param source official service root
    /// @param target mirror service root
    private record MirrorRule(String source, String target) {
        /// Tests the root boundary before retaining an original URL's path and query.
        public boolean matches(String url) {
            return url.startsWith(source) && (source.endsWith("/") || url.length() == source.length()
                    || "/?#".indexOf(url.charAt(source.length())) >= 0);
        }
    }
}

