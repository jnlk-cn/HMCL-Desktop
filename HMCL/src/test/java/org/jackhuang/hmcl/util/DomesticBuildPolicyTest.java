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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.Metadata;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies that compiled launcher code cannot load removed reporting and self-update components.
@NotNullByDefault
final class DomesticBuildPolicyTest {
    /// The modified distribution carries a distinct name under the upstream license terms.
    @Test
    void distinguishesModifiedDistribution() {
        assertEquals("HMCL Desktop", Metadata.NAME);
        assertEquals("HMCL Desktop", Metadata.FULL_NAME);
    }

    /// Removed components must be absent even when an old configuration requests updates.
    @ParameterizedTest
    @ValueSource(strings = {
            "org.jackhuang.hmcl.upgrade.UpdateChecker",
            "org.jackhuang.hmcl.upgrade.UpdateHandler",
            "org.jackhuang.hmcl.upgrade.HMCLDownloadTask",
            "org.jackhuang.hmcl.upgrade.RemoteVersion",
            "org.jackhuang.hmcl.ui.UpgradeDialog",
            "org.jackhuang.hmcl.countly.CrashReport"
    })
    void updaterAndCountlyClassesAreAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    /// No network geolocation API remains available to startup or download pages.
    @Test
    void regionHelperCannotFetchNetworkLocation() {
        assertThrows(NoSuchMethodException.class, () -> RegionUtils.class.getDeclaredMethod("isIsChinaMainlandNetwork"));
        assertThrows(NoSuchMethodException.class, () -> RegionUtils.class.getDeclaredMethod("init"));
    }
}
