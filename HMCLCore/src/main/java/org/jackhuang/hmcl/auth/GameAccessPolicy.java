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

import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/// Requires a freshly verified Microsoft entitlement before authenticating a game identity.
@NotNullByDefault
public final class GameAccessPolicy {
    /// Prevents instances of this stateless policy.
    private GameAccessPolicy() {
    }

    /// Rejects identities that cannot be used for authenticated gameplay.
    public static void requireSupported(Account selected) throws AuthenticationException {
        Objects.requireNonNull(selected);
        if (!(selected instanceof MicrosoftAccount)) {
            throw new UnsupportedGameAccountException();
        }
    }

    /// Authenticates the selected Microsoft account with fresh ownership verification.
    public static AuthorizedAccount authenticate(Account selected, @Nullable MicrosoftAccount owner)
            throws AuthenticationException {
        return authenticate(selected, owner, System::nanoTime);
    }

    /// Uses an explicit monotonic clock for deterministic lifetime checks.
    static AuthorizedAccount authenticate(Account selected, @Nullable MicrosoftAccount owner, LongSupplier clock)
            throws AuthenticationException {
        requireSupported(selected);
        MicrosoftAccount verifier = (MicrosoftAccount) selected;
        if (verifier == null) {
            throw new MicrosoftAccountRequiredException();
        }

        // MicrosoftAccount.logIn performs a fresh entitlement and matching-profile request on every call.
        AuthInfo microsoftAuth = logIn(verifier);
        long verifiedAt = clock.getAsLong();
        AuthInfo gameAuth = microsoftAuth;
        return new AuthorizedAccount(gameAuth, verifier.getProfileID(),
                verifier.getAccessTokenExpirationTime(), verifiedAt, clock);
    }

    /// Preserves the identity whose expired credentials require interactive authentication.
    private static AuthInfo logIn(Account account) throws AuthenticationException {
        try {
            return account.logIn();
        } catch (CredentialExpiredException e) {
            throw new AccountCredentialsExpiredException(account, e);
        }
    }

    /// An in-memory, short-lived launch authorization that is never serialized or exported.
    @NotNullByDefault
    public static final class AuthorizedAccount {
        /// Authentication data for the selected game identity, with its verified Microsoft profile.
        private final AuthInfo authInfo;
        /// Microsoft-owned Minecraft profile that authorized this launch.
        private final UUID ownerProfileID;
        /// Expiration of the Microsoft Minecraft access token.
        private final long tokenExpirationTime;
        /// Monotonic time at which the checks completed.
        private final long issuedAt;
        /// Clock used to prevent a saved success flag from becoming a launch credential.
        private final LongSupplier clock;
        /// Ensures one successful account verification cannot start more than one game process.
        private final AtomicBoolean used = new AtomicBoolean();

        /// Issues an authorization only after both required account authentications succeeded.
        private AuthorizedAccount(AuthInfo authInfo, UUID ownerProfileID, long tokenExpirationTime, long verifiedAt, LongSupplier clock) {
            this.authInfo = Objects.requireNonNull(authInfo);
            this.ownerProfileID = Objects.requireNonNull(ownerProfileID);
            this.tokenExpirationTime = tokenExpirationTime;
            this.clock = Objects.requireNonNull(clock);
            this.issuedAt = verifiedAt;
        }

        /// Returns the unchanged selected game identity and its provider-specific arguments.
        public AuthInfo getAuthInfo() {
            requireValid();
            return authInfo;
        }

        /// Returns the separately verified Microsoft profile without exposing its access token.
        public UUID getOwnerProfileID() {
            return ownerProfileID;
        }

        /// Stops execution when preparation took too long or the Microsoft token expired.
        public void requireValid() {
            long elapsed = clock.getAsLong() - issuedAt;
            if (elapsed < 0 || elapsed >= TimeUnit.MINUTES.toNanos(2)
                    || System.currentTimeMillis() >= tokenExpirationTime) {
                throw new IllegalStateException("Account verification has expired. Launch the game again through HMCL.");
            }
        }

        /// Consumes the proof at the process boundary so repeated launch calls need another verification.
        public void consume() {
            requireValid();
            if (!used.compareAndSet(false, true)) {
                throw new IllegalStateException("Account verification has already been used. Launch the game again through HMCL.");
            }
        }
    }

    /// Indicates that a Microsoft account is required.
    @NotNullByDefault
    public static final class MicrosoftAccountRequiredException extends AuthenticationException {
        /// Creates the missing-account failure without allowing an offline fallback.
        public MicrosoftAccountRequiredException() {
            super("Add a Microsoft account with a valid Minecraft: Java Edition entitlement before launching the game.");
        }
    }

    /// Rejects legacy offline identities independently of region, JVM flags, or stored preferences.
    @NotNullByDefault
    public static final class UnsupportedGameAccountException extends AuthenticationException {
        /// Creates the unsupported-account failure.
        public UnsupportedGameAccountException() {
            super("Use a Microsoft account with a valid Minecraft: Java Edition entitlement.");
        }
    }

    /// Carries the account that needs its existing authentication flow to run again.
    @NotNullByDefault
    public static final class AccountCredentialsExpiredException extends AuthenticationException {
        /// Identity that needs to authenticate again.
        private final Account account;

        /// Preserves the original credential failure without storing or displaying credentials.
        private AccountCredentialsExpiredException(Account account, CredentialExpiredException cause) {
            super("Account authentication must be renewed.", cause);
            this.account = account;
        }

        /// Returns the identity to pass to the normal interactive login dialog.
        public Account getAccount() {
            return account;
        }
    }
}
