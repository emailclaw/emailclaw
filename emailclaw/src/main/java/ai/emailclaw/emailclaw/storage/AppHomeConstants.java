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
public final class AppHomeConstants {
    public static final String USER_HOME_VALUE = System.getProperty("user.home");
    public static final String SYS_PROP_HOME = "emailclaw.home";
    public static final String ENV_HOME = "EMAILCLAW_HOME";
    public static final String DEFAULT_HOME_DIR_NAME = "emailclaw";
    public static final Path HOME_RESOLVED = resolveHome();

    public static final String BACKUPS_DIR = ".backups";
    public static final String CHAT_HISTORY_DIR = ".chat-history";
    public static final String CONFIG_DIR = ".config";
    public static final String SECRET_DIR = ".secret";
    public static final String SECURITY_APPROVALS_DIR = ".security/approvals";
    public static final String LOGS_DIR = "logs";
    public static final String PLUGINS_DIR = "plugins";
    public static final String SKILL_POOL_DIR = "skill-pool";
    public static final String AGENT_WORKSPACE_DIR = "agent-workspace";
    public static final String PROJECTS_DIR = "projects";
    public static final Path BROWSER_DATA_PATH =
            AppHomeConstants.HOME_RESOLVED.resolve(".browser-data");

    private AppHomeConstants() {}

    private static Path resolveHome() {
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
