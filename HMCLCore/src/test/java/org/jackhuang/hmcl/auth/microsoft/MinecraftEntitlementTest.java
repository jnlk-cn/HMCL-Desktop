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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Checks the exact Java Edition entitlement without accepting product names or other games.
@NotNullByDefault
final class MinecraftEntitlementTest {
    /// Recognizes the game entitlement returned by the official license endpoint.
    @Test void acceptsJavaGameLicense() {
        assertTrue(MicrosoftService.hasJavaEditionLicense(new MicrosoftService.MinecraftLicense(
                List.of(new MicrosoftService.MinecraftLicenseItem("game_minecraft", "fixture")), "fixture", "1")));
    }

    /// Other products, partial data and lookalike entitlement names never grant gameplay.
    @ParameterizedTest
    @ValueSource(strings = {"game_minecraft_bedrock", "product_minecraft", "game_dungeons", "game_minecraft_trial", "GAME_MINECRAFT", ""})
    void rejectsOtherEntitlements(String name) {
        assertFalse(MicrosoftService.hasJavaEditionLicense(new MicrosoftService.MinecraftLicense(
                List.of(new MicrosoftService.MinecraftLicenseItem(name, "fixture")), "fixture", "1")));
    }

    /// Missing or empty ownership responses fail closed.
    @Test void rejectsMissingItems() {
        assertFalse(MicrosoftService.hasJavaEditionLicense(null));
        assertFalse(MicrosoftService.hasJavaEditionLicense(new MicrosoftService.MinecraftLicense(null, "", "")));
        assertFalse(MicrosoftService.hasJavaEditionLicense(new MicrosoftService.MinecraftLicense(List.of(), "", "")));
    }
}
