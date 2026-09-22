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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application path domain model and value object.
 *
 * <p>Centrally manages all configuration, database, data, and workspace paths derived from the
 * application root directory, completely adhering to the Pure DI pattern without ambient global state.
 */
public final class AppPaths {
    private static final Logger LOGGER = Logger.getLogger(AppPaths.class.getName());

    // Relative directories
    public static final String AGENT_WORKSPACE_DIR = "agent-workspace";
    public static final String BACKUPS_DIR = ".backups";
    public static final String BROWSER_DATA_DIR = ".browser-data";
    public static final String CONFIG_DIR = ".config";
    public static final String DATABASE_DIR = ".database";
    public static final String LOGS_DIR = "logs";
    public static final String OFFLOADS_DIR = ".offloads";
    public static final String PLUGINS_DIR = "plugins";
    public static final String PROJECTS_DIR = "projects";
    public static final String SKILL_POOL_DIR = "skill-pool";
    public static final String SECRET_DIR = ".secret";
    public static final String SECURITY_APPROVALS_DIR = ".security/approvals";
    public static final String WEBVIEW_DIR = ".webview";

    // Absolute / resolved instance paths
    public final Path root;
    public final Path workspaceRoot;
    public final Path backupsDir;
    public final Path browserDataDir;
    public final Path configDir;
    public final Path databaseDir;
    public final Path logsDir;
    public final Path offloadsDir;
    public final Path pluginsDir;
    public final Path projectsRoot;
    public final Path securityApprovalsDir;
    public final Path secretDir;
    public final Path skillsPoolRoot;
    public final Path webviewDir;

    /** Global config file (current Agent / country / language). */
    public final Path globalConfigFile;

    public final Path providersFile;
    public final Path agentsFile;
    public final Path databaseFile;
    public final Path toolConfigFile;
    public final Path channelsFile;
    public final Path cronJobsFile;
    public final Path projectsFile;
    public final Path mcpClientsFile;
    public final Path acpAgentsFile;
    public final Path envsFile;
    public final Path securityRulesFile;
    public final Path securityConfigFile;
    public final Path voiceTranscriptionFile;

    /**
     * Constructs an {@code AppPaths} value object rooted at the specified base directory.
     *
     * @param root the base application root directory, must not be null
     */
    public AppPaths(Path root) {
        this.root = Objects.requireNonNull(root, "root");
        this.workspaceRoot = root.resolve(AGENT_WORKSPACE_DIR);
        this.browserDataDir = root.resolve(BROWSER_DATA_DIR);
        this.backupsDir = root.resolve(BACKUPS_DIR);
        this.configDir = root.resolve(CONFIG_DIR);
        this.databaseDir = root.resolve(DATABASE_DIR);
        this.logsDir = root.resolve(LOGS_DIR);
        this.offloadsDir = root.resolve(OFFLOADS_DIR);
        this.projectsRoot = root.resolve(PROJECTS_DIR);
        this.pluginsDir = root.resolve(PLUGINS_DIR);
        this.securityApprovalsDir = root.resolve(SECURITY_APPROVALS_DIR);
        this.secretDir = root.resolve(SECRET_DIR);
        this.skillsPoolRoot = root.resolve(SKILL_POOL_DIR);
        this.webviewDir = root.resolve(WEBVIEW_DIR);

        this.databaseFile = databaseDir.resolve("emailclaw.db");
        this.providersFile = configDir.resolve("providers.json");
        this.agentsFile = configDir.resolve("agents.json");
        this.globalConfigFile = configDir.resolve("global-config.json");
        this.toolConfigFile = configDir.resolve("tools.json");
        this.channelsFile = configDir.resolve("channels.json");
        this.cronJobsFile = configDir.resolve("cron-jobs.json");
        this.projectsFile = configDir.resolve("projects.json");
        this.mcpClientsFile = configDir.resolve("mcp-clients.json");
        this.acpAgentsFile = configDir.resolve("acp-agents.json");
        this.envsFile = secretDir.resolve("envs.json");
        this.securityRulesFile = configDir.resolve("security-rules.json");
        this.securityConfigFile = configDir.resolve("security-config.json");
        this.voiceTranscriptionFile = configDir.resolve("voice-transcription.json");
    }

    /*
     * Ensure directories exist before creating services, preventing WatchService registration failure
     */
    public void ensureStructure() {
        try {
            LOGGER.log(Level.INFO, "Initialize working directory structure: {0}", this.root);
            Files.createDirectories(this.root);
            Files.createDirectories(this.workspaceRoot);
            Files.createDirectories(this.backupsDir);
            Files.createDirectories(this.browserDataDir);
            Files.createDirectories(this.configDir);
            Files.createDirectories(this.databaseDir);
            Files.createDirectories(this.logsDir);
            Files.createDirectories(this.offloadsDir);
            Files.createDirectories(this.pluginsDir);
            Files.createDirectories(this.projectsRoot);
            Files.createDirectories(this.securityApprovalsDir);
            Files.createDirectories(this.secretDir);
            Files.createDirectories(this.skillsPoolRoot);
            Files.createDirectories(this.webviewDir);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize working directory", e);
            throw new RuntimeException("Failed to initialize workspace", e);
        }
    }

    /**
     * Factory method creating an {@code AppPaths} instance for a specific root.
     *
     * @param root the application root directory
     * @return a new {@code AppPaths} instance
     */
    public static AppPaths of(Path root) {
        return new AppPaths(root);
    }

    /**
     * Factory method creating an {@code AppPaths} instance using the default resolved home directory.
     * Useful for standalone fallback and backward compatibility.
     *
     * @return an {@code AppPaths} instance rooted at {@link AppHomeResolver#APP_HOME_RESOLVED}
     */
    public static AppPaths fromDefault() {
        return new AppPaths(AppHomeResolver.APP_HOME_RESOLVED);
    }
}
