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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Opt-in authentication response diagnostics that never inspect bodies, queries or request headers.
@NotNullByDefault
final class AuthenticationHttpDiagnostics {

    /// Exact service paths permitted in diagnostics; arbitrary URLs and profile identifiers are excluded.
    private static final @Unmodifiable Map<String, String> STAGES = Map.of(
            "login.live.com/oauth20_token.srf", "MICROSOFT_OAUTH",
            "login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", "MICROSOFT_DEVICE_CODE",
            "login.microsoftonline.com/consumers/oauth2/v2.0/token", "MICROSOFT_OAUTH",
            "user.auth.xboxlive.com/user/authenticate", "XBOX_LIVE",
            "xsts.auth.xboxlive.com/xsts/authorize", "XSTS",
            "api.minecraftservices.com/authentication/login_with_xbox", "MINECRAFT_TOKEN");

    /// Only timing, content type and service correlation headers can enter the local log.
    private static final @Unmodifiable List<String> SAFE_HEADERS = List.of(
            "Date", "Retry-After", "Content-Type", "x-minecraft-request-id", "x-ms-request-id",
            "x-azure-ref", "x-cache", "ms-cv", "x-xblcorrelationid");

    /// Prevents construction of this stateless formatter.
    private AuthenticationHttpDiagnostics() {
    }

    /// Emits local diagnostics only when explicitly enabled by the debug launcher entry point.
    static void logResponse(HttpURLConnection connection, int status) {
        if (Boolean.getBoolean("hmcl.auth.debug")) {
            @Nullable String message = formatResponse(connection, status);
            if (message != null) {
                LOG.info(message);
            }
        }
    }

    /// Formats allowlisted metadata; a JDK proxy flag does not identify transparent VPN or TUN routes.
    static @Nullable String formatResponse(HttpURLConnection connection, int status) {
        URL url = connection.getURL();
        String endpoint = url.getHost().toLowerCase(Locale.ROOT) + url.getPath();
        @Nullable String stage = STAGES.get(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || (url.getPort() != -1 && url.getPort() != 443) || stage == null) {
            return null;
        }
        StringBuilder message = new StringBuilder("Authentication response: stage=")
                .append(stage).append(" endpoint=").append(endpoint)
                .append(" status=").append(status).append(" javaProxy=").append(connection.usingProxy());
        for (String name : SAFE_HEADERS) {
            @Nullable String value = connection.getHeaderField(name);
            if (value != null) {
                message.append(' ').append(name).append('=');
                for (int i = 0; i < Math.min(value.length(), 256); i++) {
                    char character = value.charAt(i);
                    message.append(character >= 32 && character < 127 ? character : '?');
                }
            }
        }
        return message.toString();
    }
}
