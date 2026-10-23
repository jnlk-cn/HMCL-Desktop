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
package org.jackhuang.hmcl.auth;

import org.jackhuang.hmcl.auth.microsoft.*;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/// Exercises fresh ownership verification and the process authorization using controlled Microsoft services.
@NotNullByDefault
final class GameAccessPolicyTest {
    /// Synthetic owned profile used without production credentials.
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    /// Constructs an account through the Microsoft factory.
    private static MicrosoftAccount owner(ControlledService service) throws AuthenticationException {
        return new MicrosoftAccountFactory(service).create((provider, profiles) -> profiles.get(0),
                "", "", stage -> { }, OAuth.GrantFlow.DEVICE);
    }

    /// A cached token cannot replace the launch-time entitlement check.
    @Test void verifiesEveryLaunchRatherThanTrustingCachedAuthentication() throws Exception {
        var service = new ControlledService();
        var account = owner(service);
        var first = GameAccessPolicy.authenticate(account, null);
        var second = GameAccessPolicy.authenticate(account, null);
        assertEquals(2, service.verifications);
        assertEquals(OWNER, first.getAuthInfo().getUUID());
        assertEquals(OWNER, second.getOwnerProfileID());
        assertThrows(AuthenticationException.class, account::playOffline);
    }

    /// A revoked license prevents the next launch after an earlier successful check.
    @Test void rejectsRevokedEntitlement() throws Exception {
        var service = new ControlledService();
        var account = owner(service);
        GameAccessPolicy.authenticate(account, null);
        service.failure = new MicrosoftService.MinecraftJavaEditionLicenseNotFoundException();
        assertThrows(MicrosoftService.MinecraftJavaEditionLicenseNotFoundException.class,
                () -> GameAccessPolicy.authenticate(account, null));
        assertEquals(2, service.verifications);
    }

    /// A network failure never creates a cached or local launch identity.
    @Test void rejectsUnavailableOwnershipService() throws Exception {
        var service = new ControlledService();
        service.failure = new ServerDisconnectException(new IOException("Controlled unavailable service"));
        assertThrows(ServerDisconnectException.class, () -> GameAccessPolicy.authenticate(owner(service), null));
    }

    /// An unrelated Account implementation remains unsupported even if a Microsoft owner is supplied.
    @Test void rejectsEveryNonMicrosoftAccount() throws Exception {
        var unsupported = new UnsupportedAccount();
        assertThrows(GameAccessPolicy.UnsupportedGameAccountException.class,
                () -> GameAccessPolicy.authenticate(unsupported, owner(new ControlledService())));
        assertEquals(0, unsupported.logins);
    }

    /// The process proof expires after preparation stalls.
    @Test void authorizationExpiresUsingMonotonicTime() throws Exception {
        var clock = new AtomicLong(10);
        var authorized = GameAccessPolicy.authenticate(owner(new ControlledService()), null, clock::get);
        clock.addAndGet(TimeUnit.MINUTES.toNanos(2));
        assertThrows(IllegalStateException.class, authorized::getAuthInfo);
    }

    /// A successful verification cannot authorize two game processes.
    @Test void consumesProofOnlyOnce() throws Exception {
        var authorized = GameAccessPolicy.authenticate(owner(new ControlledService()), null);
        authorized.consume();
        assertThrows(IllegalStateException.class, authorized::consume);
    }

    /// Fixture representing any account type outside the sole supported provider.
    @NotNullByDefault
    private static final class UnsupportedAccount extends Account {
        /// Counts attempts to authenticate an unsupported identity.
        private int logins;

        /// Creates a synthetic identifier without loading any provider.
        private UnsupportedAccount() { super(AccountID.generate()); }

        /// Returns a synthetic public profile name.
        @Override public String getProfileName() { return "UnsupportedFixture"; }

        /// Returns the fixture profile ID.
        @Override public UUID getProfileID() { return OWNER; }

        /// Fails if policy validation accidentally invokes an unsupported login.
        @Override public AuthInfo logIn() { logins++; throw new AssertionError("Unexpected login"); }

        /// Disallows local authentication in the fixture.
        @Override public AuthInfo playOffline() throws AuthenticationException { throw new AuthenticationException("Unsupported"); }
    }

    /// Synthetic OAuth callback that fails if a test unexpectedly opens a real login flow.
    @NotNullByDefault
    private static final class NoInteractiveLogin implements OAuth.Callback {
        /// Disallows a real callback server in these isolated tests.
        @Override public OAuth.Session startServer() { throw new AssertionError("Unexpected interactive login"); }

        /// Disallows production device codes.
        @Override public void grantDeviceCode(String code, String uri) { throw new AssertionError("Unexpected device code"); }

        /// Rejects an unexpected device completion callback.
        @Override public void loginCompletedDeviceCode() { throw new AssertionError("Unexpected device completion"); }

        /// Prevents tests from navigating any real account browser.
        @Override public void openBrowser(OAuth.GrantFlow flow, String url) { throw new AssertionError("Unexpected browser"); }

        /// Identifies the fixture without using a registered application.
        @Override public String getClientId() { return "test-fixture"; }
    }

    /// Controls only the remote boundary while retaining real MicrosoftAccount and GameAccessPolicy behavior.
    @NotNullByDefault
    private static final class ControlledService extends MicrosoftService {
        /// Counts the fresh checks issued by the account implementation.
        private int verifications;

        /// Optional failure returned by the controlled entitlement/profile service.
        private @Nullable AuthenticationException failure;

        /// Creates a service with no browser access.
        private ControlledService() { super(new NoInteractiveLogin()); }

        /// Supplies a synthetic session without exchanging any production credentials.
        @Override public MicrosoftSession authenticate(OAuth.GrantFlow flow) {
            return new MicrosoftSession("Bearer", "fixture-access", System.currentTimeMillis() + 3600000,
                    "fixture-refresh", new MicrosoftSession.User("fixture-user"),
                    new MicrosoftSession.GameProfile(OWNER, "OwnedFixture"));
        }

        /// Records a fresh entitlement request and can simulate denial, revocation or an outage.
        @Override public void verifyGameOwnership(String type, String token, UUID expected) throws AuthenticationException {
            verifications++;
            assertEquals(OWNER, expected);
            if (failure != null) throw failure;
        }

        /// Prevents asynchronous texture loading from contacting any production service.
        @Override public Optional<CompleteGameProfile> getCompleteGameProfile(UUID uuid) { return Optional.empty(); }
    }

}
