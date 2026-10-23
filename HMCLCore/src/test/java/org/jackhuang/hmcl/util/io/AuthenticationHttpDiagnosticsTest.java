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
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies that diagnostic output is useful without capturing account credentials.
@NotNullByDefault
final class AuthenticationHttpDiagnosticsTest {

    /// Error status, retry delay and request ID survive while cookies and URL credentials do not.
    @Test
    void limitsOutputToSafeMetadata() throws MalformedURLException {
        var connection = new StubConnection(
                "https://private-user:private-password@api.minecraftservices.com/authentication/login_with_xbox?code=private-code",
                Map.of("Retry-After", "120", "x-minecraft-request-id", "test-request",
                        "Set-Cookie", "private-cookie", "Authorization", "Bearer private-token"));
        String message = requireMessage(connection, 429);
        assertTrue(message.contains("stage=MINECRAFT_TOKEN"));
        assertTrue(message.contains("status=429"));
        assertTrue(message.contains("Retry-After=120"));
        assertTrue(message.contains("x-minecraft-request-id=test-request"));
        assertFalse(message.contains("private-"));
        assertFalse(message.contains("Set-Cookie"));
        assertFalse(message.contains("Authorization"));
    }

    /// A response header cannot inject log lines or create unbounded diagnostic output.
    @Test
    void boundsAndSanitizesHeaderValues() throws MalformedURLException {
        var connection = new StubConnection("https://xsts.auth.xboxlive.com/xsts/authorize",
                Map.of("ms-cv", "first\r\n" + "x".repeat(1000)));
        String message = requireMessage(connection, 200);
        assertTrue(message.contains("ms-cv=first??"));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
        assertTrue(message.length() < 450);
    }

    /// Unknown hosts, user-specific paths and insecure HTTP connections are never logged.
    @Test
    void rejectsUnlistedEndpoints() throws MalformedURLException {
        for (String url : new String @Unmodifiable []{
                "https://example.com/authentication/login_with_xbox",
                "https://api.minecraftservices.com/minecraft/profile/private-player",
                "http://api.minecraftservices.com/authentication/login_with_xbox",
                "https://api.minecraftservices.com:8443/authentication/login_with_xbox"}) {
            assertNull(AuthenticationHttpDiagnostics.formatResponse(new StubConnection(url, Map.of()), 429));
        }
    }

    /// Requires a message for a known endpoint without any network requests.
    private static String requireMessage(StubConnection connection, int status) {
        @Nullable String message = AuthenticationHttpDiagnostics.formatResponse(connection, status);
        assertNotNull(message);
        return message;
    }

    /// Supplies response headers only; attempts to establish a connection fail the test.
    @NotNullByDefault
    private static final class StubConnection extends HttpURLConnection {

        /// Immutable synthetic response headers containing no real account data.
        private final @Unmodifiable Map<String, String> responseHeaders;

        /// Creates a non-networking connection for a synthetic URL and header set.
        private StubConnection(String url, Map<String, String> responseHeaders) throws MalformedURLException {
            super(new URL(url));
            this.responseHeaders = Map.copyOf(responseHeaders);
        }

        /// Returns only synthetic response metadata.
        @Override
        public @Nullable String getHeaderField(String name) {
            return responseHeaders.get(name);
        }

        /// No socket is opened by this stub.
        @Override
        public void disconnect() {
        }

        /// This value describes only the JDK connection, not transparent network routing.
        @Override
        public boolean usingProxy() {
            return false;
        }

        /// Fails if formatting diagnostics ever attempts to open a network connection.
        @Override
        public void connect() {
            fail("Diagnostics must not open connections");
        }
    }
}
