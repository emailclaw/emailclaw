/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw;

import ai.emailclaw.emailclaw.cli.CliApp;
import java.awt.GraphicsEnvironment;

/**
 * Common "Launcher" bootstrap class for independent startup in GUI, CLI, or Service modes.
 *
 * <p>Only used to solve the bootstrap issue of running Java applications with Maven,
 * dispatching appropriately to {@link FxApp}, {@link CliApp}, or {@link ServiceApp}.
 */
public class Launcher {
    public static final String APP_VERSION = "v26.11.9";

    public static void main(String[] args) {
        boolean isService = false;
        boolean isCli = false;
        for (String arg : args) {
            if ("--service".equalsIgnoreCase(arg)
                    || "-s".equalsIgnoreCase(arg)
                    || "service".equalsIgnoreCase(arg)) {
                isService = true;
                break;
            }
            if ("--cli".equalsIgnoreCase(arg)
                    || "-c".equalsIgnoreCase(arg)
                    || "cli".equalsIgnoreCase(arg)
                    || "run".equalsIgnoreCase(arg)
                    || "--run".equalsIgnoreCase(arg)
                    || "-r".equalsIgnoreCase(arg)
                    || "--help".equalsIgnoreCase(arg)
                    || "-h".equalsIgnoreCase(arg)
                    || "help".equalsIgnoreCase(arg)
                    || "--version".equalsIgnoreCase(arg)
                    || "-v".equalsIgnoreCase(arg)
                    || "version".equalsIgnoreCase(arg)) {
                isCli = true;
                break;
            }
        }

        if (!isService && !isCli) {
            // Automatically fall back to CLI if running in a headless environment without display
            if (GraphicsEnvironment.isHeadless()
                    || (System.getenv("DISPLAY") == null
                            && System.getenv("WAYLAND_DISPLAY") == null
                            && System.getProperty("os.name", "").toLowerCase().contains("linux"))) {
                isCli = true;
            }
        }

        if (isService) {
            ServiceApp.main(args);
        } else if (isCli) {
            CliApp.main(args);
        } else {
            // Directly call FxApp's main method, bypassing Maven's default JavaFX bootstrap
            // mechanism
            FxApp.main(args);
        }
    }
}
