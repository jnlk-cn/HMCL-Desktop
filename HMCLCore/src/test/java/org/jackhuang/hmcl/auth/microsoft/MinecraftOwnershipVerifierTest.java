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

import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// Tests the production entitlement/profile parsing with controlled transport responses.
@NotNullByDefault
final class MinecraftOwnershipVerifierTest {
    /// A synthetic profile without any production account identifiers.
    private static final UUID PROFILE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    /// A successful Java Edition entitlement response used only inside these tests.
    private static final String LICENSE = "{\"items\":[{\"name\":\"game_minecraft\",\"signature\":\"fixture\"}]}";
    /// A complete profile response using the same synthetic identity as the expected account.
    private static final String PROFILE_JSON = "{\"id\":\"10000000000000000000000000000001\",\"name\":\"Fixture\",\"skins\":[],\"capes\":[]}";

    /// Both requests use the same bearer credentials and fixed official URLs, in entitlement-first order.
    @Test
    void validatesLicenseAndMatchingProfile() throws Exception {
        var requests = new ArrayList<String>();
        var verifier = new MinecraftOwnershipVerifier((url, type, token) -> {
            assertEquals("Bearer", type);
            assertEquals("synthetic-token", token);
            requests.add(url);
            return url.contains("/entitlements/license?") ? LICENSE : PROFILE_JSON;
        });
        assertEquals(PROFILE, verifier.verify("Bearer", "synthetic-token", PROFILE).id);
        assertEquals(2, requests.size());
        assertTrue(requests.get(0).startsWith("https://api.minecraftservices.com/entitlements/license?requestId="));
        assertEquals("https://api.minecraftservices.com/minecraft/profile", requests.get(1));
    }

    /// HTTP 200 alone is insufficient; missing or non-Java entitlements never reach the profile request.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"items\":[]}", "{\"items\":[{\"name\":\"product_minecraft\"}]}",
            "{\"items\":[{\"name\":\"game_minecraft_bedrock\"}]}", "{\"items\":[null]}"})
    void rejectsUnownedAccountsBeforeProfileLookup(String license) {
        var verifier = new MinecraftOwnershipVerifier((url, type, token) -> {
            assertTrue(url.contains("/entitlements/license?"));
            return license;
        });
        assertThrows(MicrosoftService.MinecraftJavaEditionLicenseNotFoundException.class,
                () -> verifier.verify("Bearer", "synthetic-token", PROFILE));
    }

    /// Authentication refusal, rate limiting and service failures deny access at either request.
    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void rejectsHttpFailuresAtEitherStep(int status) {
        for (boolean failProfile : new boolean[]{false, true}) {
            var verifier = new MinecraftOwnershipVerifier((url, type, token) -> {
                if (failProfile && url.contains("/entitlements/license?")) return LICENSE;
                throw new ResponseCodeException(url, status);
            });
            var failure = assertThrows(ServerDisconnectException.class,
                    () -> verifier.verify("Bearer", "synthetic-token", PROFILE));
            assertEquals(status, ((ResponseCodeException) failure.getCause()).getResponseCode());
        }
    }

    /// A transport timeout cannot be converted to a successful cached ownership result.
    @Test
    void rejectsNetworkOutage() {
        var verifier = new MinecraftOwnershipVerifier((url, type, token) -> { throw new IOException("Synthetic timeout"); });
        assertThrows(ServerDisconnectException.class, () -> verifier.verify("Bearer", "synthetic-token", PROFILE));
    }

    /// An owned license with an absent Minecraft profile remains unusable until the profile is created.
    @Test
    void rejectsMissingProfile() {
        var verifier = new MinecraftOwnershipVerifier((url, type, token) -> {
            if (url.contains("/entitlements/license?")) return LICENSE;
            throw new ResponseCodeException(url, 404);
        });
        assertThrows(MicrosoftService.MinecraftJavaEditionProfileNotFoundException.class,
                () -> verifier.verify("Bearer", "synthetic-token", PROFILE));
    }

    /// A malformed or incomplete response is never treated as evidence of ownership.
    @ParameterizedTest
    @ValueSource(strings = {"null", "not-json", "[]", "{\"items\":{}}"})
    void rejectsMalformedLicense(String response) {
        var verifier = new MinecraftOwnershipVerifier((url, type, token) -> response);
        assertThrows(ServerResponseMalformedException.class, () -> verifier.verify("Bearer", "synthetic-token", PROFILE));
    }

    /// Missing profile identity and a profile belonging to another account both deny launch.
    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"error\":\"FORBIDDEN\"}",
            "{\"id\":\"20000000000000000000000000000002\",\"name\":\"Other\",\"skins\":[],\"capes\":[]}",
            "{\"id\":\"10000000000000000000000000000001\",\"name\":\" \",\"skins\":[],\"capes\":[]}"})
    void rejectsMalformedOrMismatchedProfile(String response) {
        var verifier = new MinecraftOwnershipVerifier((url, type, token) -> url.contains("/entitlements/license?") ? LICENSE : response);
        assertThrows(ServerResponseMalformedException.class, () -> verifier.verify("Bearer", "synthetic-token", PROFILE));
    }
}
