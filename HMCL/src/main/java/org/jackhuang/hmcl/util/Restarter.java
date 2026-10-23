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

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Restarts this same local launcher without an update or replacement mechanism.
@NotNullByDefault
public final class Restarter {

    /// Restart the current application.
    public static void restartSelf() throws IOException {
        LOG.info("Restarting HMCL");

        @Nullable Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null) {
            throw new IOException("Failed to find current HMCL location");
        }

        var command = new ArrayList<String>();
        command.add(JavaRuntime.getDefault().getBinary().toString());
        try {
            for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (argument.startsWith("-D") || argument.startsWith("-X")) {
                    command.add(argument);
                }
            }
        } catch (Throwable ignored) {
            // Some custom runtimes omit java.management; preserve launcher paths in that case.
            System.getProperties().forEach((key, value) -> {
                if (key instanceof String property && property.startsWith("hmcl.")) {
                    command.add("-D" + property + "=" + value);
                }
            });
        }
        command.add("-jar");
        command.add(thisJar.toAbsolutePath().toString());
        // Do not log the command line: inherited properties may contain credentials.
        new ProcessBuilder(command).directory(Path.of("").toAbsolutePath().toFile()).inheritIO().start();
    }

    /// Prevents instantiation.
    private Restarter() {
    }
}
