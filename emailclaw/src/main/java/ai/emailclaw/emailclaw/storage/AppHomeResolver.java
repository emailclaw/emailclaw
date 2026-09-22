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
package ai.emailclaw.emailclaw.storage;

import java.nio.file.Path;

/**
 * Application home directory resolution related system properties, environment variables, and default directory name constants. The exact location is combined by AppPaths.resolveHome() and the following constants.
 */
public final class AppHomeResolver {
    public static final String USER_HOME_VALUE = System.getProperty("user.home");
    public static final String SYS_PROP_HOME = "emailclaw.home";
    public static final String ENV_HOME = "EMAILCLAW_HOME";
    public static final String DEFAULT_HOME_DIR_NAME = "emailclaw";
    public static final Path APP_HOME_RESOLVED = resolveAppHome();

    private AppHomeResolver() {}

    /**
     * Resolves the application root home directory from system properties, environment variables,
     * or default user home directory.
     *
     * @return the resolved application root home directory
     */
    public static Path resolveAppHome() {
        String sysProp = System.getProperty(SYS_PROP_HOME);
        if (sysProp != null && !sysProp.isBlank()) {
            return Path.of(sysProp);
        }
        String envVar = System.getenv(ENV_HOME);
        if (envVar != null && !envVar.isBlank()) {
            return Path.of(envVar);
        }
        return Path.of(USER_HOME_VALUE, DEFAULT_HOME_DIR_NAME);
    }
}
