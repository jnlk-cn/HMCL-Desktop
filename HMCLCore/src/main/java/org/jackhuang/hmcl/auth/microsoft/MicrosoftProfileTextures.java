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
package org.jackhuang.hmcl.auth.microsoft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.gson.ValidationTypeAdapterFactory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Decodes texture metadata from profiles obtained through official Minecraft services.
@NotNullByDefault
public final class MicrosoftProfileTextures {
    /// Parser validating the official texture payload format.
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE).create();

    /// Prevents creating a stateless decoder.
    private MicrosoftProfileTextures() { }

    /// Returns texture metadata without creating an authentication service or accepting an API URL.
    public static Optional<@Unmodifiable Map<TextureType, Texture>> getTextures(CompleteGameProfile profile)
            throws ServerResponseMalformedException {
        @Nullable String encoded = Objects.requireNonNull(profile).getProperties().get("textures");
        if (encoded == null) return Optional.empty();
        try {
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            @Nullable TexturePayload payload = GSON.fromJson(json, TexturePayload.class);
            if (payload == null) throw new JsonParseException("Missing Minecraft texture payload");
            return Optional.ofNullable(payload.textures).map(Map::copyOf);
        } catch (IllegalArgumentException | JsonParseException failure) {
            throw new ServerResponseMalformedException(failure);
        }
    }

    /// Deserialization target for the official profile texture property.
    @NotNullByDefault
    private static final class TexturePayload {
        /// Optional texture map supplied by the official profile service.
        private @Nullable @Unmodifiable Map<TextureType, Texture> textures;
    }
}
