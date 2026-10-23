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

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies HTTP retry behavior against a real loopback server without account credentials.
@NotNullByDefault
final class HttpRequestTest {

    /// A rate-limited GET must stop after the first response even when retries were requested.
    @Test
    void rateLimitedGetIsNotRetried() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(requests, count -> 429);
        try {
            ResponseCodeException failure = assertThrows(ResponseCodeException.class,
                    () -> HttpRequest.GET(endpoint(server)).retry(5).getString());
            assertEquals(429, failure.getResponseCode());
            assertEquals("{}", failure.getData());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    /// A token-exchange-shaped POST must not multiply a rate-limited login attempt.
    @Test
    void rateLimitedPostIsNotRetried() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(requests, count -> 429);
        try {
            ResponseCodeException failure = assertThrows(ResponseCodeException.class,
                    () -> HttpRequest.POST(endpoint(server))
                            .json(Map.of("identityToken", "test-only"))
                            .retry(5).getString());
            assertEquals(429, failure.getResponseCode());
            assertEquals("{}", failure.getData());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    /// Existing retries still recover from a transient server failure.
    @Test
    void transientServerFailureStillRetries() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(requests, count -> count == 1 ? 503 : 200);
        try {
            assertEquals("{}", HttpRequest.GET(endpoint(server)).retry(5).getString());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    /// Starts an isolated server that counts requests and supplies the selected response status.
    private static HttpServer startServer(AtomicInteger requests, IntUnaryOperator status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/authentication/login_with_xbox", exchange -> {
            exchange.getRequestBody().close();
            byte @Unmodifiable [] body = "{}".getBytes(UTF_8);
            exchange.sendResponseHeaders(status.applyAsInt(requests.incrementAndGet()), body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return server;
    }

    /// Returns the loopback endpoint without contacting Microsoft or Mojang.
    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/authentication/login_with_xbox";
    }
}
