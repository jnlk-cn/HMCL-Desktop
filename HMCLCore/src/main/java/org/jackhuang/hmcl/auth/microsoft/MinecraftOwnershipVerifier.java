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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/// Checks official Java Edition licensing and the profile belonging to the same bearer token.
@NotNullByDefault
final class MinecraftOwnershipVerifier {
    /// The request boundary; production uses fixed official HTTPS endpoints with normal TLS verification.
    private final JsonRequest request;

    /// Creates the production verifier without accepting endpoint overrides or stored success flags.
    MinecraftOwnershipVerifier() {
        this((url, type, token) -> HttpRequest.GET(url).authorization(type, token)
                .accept("application/json").retry(1).getString());
    }

    /// Allows deterministic transport responses within this package's tests.
    MinecraftOwnershipVerifier(JsonRequest request) {
        this.request = Objects.requireNonNull(request);
    }

    /// Retrieves the current entitlement and profile; every unsuccessful or incomplete response fails closed.
    MicrosoftService.MinecraftProfileResponse verify(String type, String token, @Nullable UUID expectedProfile)
            throws AuthenticationException {
        if (!"Bearer".equalsIgnoreCase(type) || token.isBlank()) {
            throw new ServerResponseMalformedException("Missing Minecraft bearer credentials.");
        }
        try {
            var license = JsonUtils.fromNonNullJson(request.get(
                    "https://api.minecraftservices.com/entitlements/license?requestId=" + UUID.randomUUID(),
                    type, token), MicrosoftService.MinecraftLicense.class);
            if (!MicrosoftService.hasJavaEditionLicense(license)) {
                throw new MicrosoftService.MinecraftJavaEditionLicenseNotFoundException();
            }

            final String profileJson;
            try {
                profileJson = request.get("https://api.minecraftservices.com/minecraft/profile", type, token);
            } catch (ResponseCodeException e) {
                if (e.getResponseCode() == 404) {
                    throw new MicrosoftService.MinecraftJavaEditionProfileNotFoundException();
                }
                throw e;
            }
            var profile = JsonUtils.fromNonNullJson(profileJson, MicrosoftService.MinecraftProfileResponse.class);
            if (profile.id == null || profile.name == null || profile.name.isBlank()
                    || profile.error != null || profile.errorType != null) {
                throw new ServerResponseMalformedException("Minecraft profile is missing its authenticated identity.");
            }
            if (expectedProfile != null && !expectedProfile.equals(profile.id)) {
                throw new ServerResponseMalformedException("Verified Minecraft profile does not match the selected Microsoft account.");
            }
            return profile;
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    /// Supplies JSON or an explicit transport failure without serializing credentials.
    @NotNullByDefault
    @FunctionalInterface
    interface JsonRequest {
        /// Performs one authenticated GET; error status codes must be reported as exceptions.
        String get(String url, String type, String token) throws IOException;
    }
}
