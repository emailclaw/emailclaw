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
 * Application path configuration object.
 *
 * <p>Centrally manages all configuration, data, and workspace paths derived from the root directory to avoid scattered hardcoding.
 */
public final class AppPaths {
    public final Path root;
    public final Path configDir;
    public final Path secretDir;
    public final Path providersFile;
    public final Path agentsFile;

    /** Global config file (current Agent / country / language). */
    public final Path globalConfigFile;

    public final Path sessionsMetaFile;
    public final Path tokenUsageFile;
    public final Path agentStatsFile;
    public final Path toolConfigFile;
    public final Path channelsFile;
    public final Path cronJobsFile;
    public final Path projectsFile;
    public final Path tasksFile;
    public final Path mcpClientsFile;
    public final Path acpAgentsFile;
    public final Path envsFile;
    public final Path securityRulesFile;
    public final Path securityConfigFile;
    public final Path backupsDir;
    public final Path voiceTranscriptionFile;
    public final Path workspaceRoot;
    public final Path skillsPoolRoot;
    public final Path logsDir;
    public final Path pluginsDir;
    public final Path projectsRoot;

    public AppPaths(Path root) {
        this.root = root;
        this.configDir = root.resolve(AppHomeConstants.CONFIG_DIR);
        this.secretDir = root.resolve(AppHomeConstants.SECRET_DIR);
        this.providersFile = configDir.resolve("providers.json");
        this.agentsFile = configDir.resolve("agents.json");
        this.globalConfigFile = configDir.resolve("global-config.json");
        this.sessionsMetaFile = configDir.resolve("sessions.json");
        this.tokenUsageFile = configDir.resolve("token-usage.json");
        this.agentStatsFile = configDir.resolve("agent-stats.json");
        this.toolConfigFile = configDir.resolve("tools.json");
        this.channelsFile = configDir.resolve("channels.json");
        this.cronJobsFile = configDir.resolve("cron-jobs.json");
        this.projectsFile = configDir.resolve("projects.json");
        this.tasksFile = configDir.resolve("tasks.json");
        this.mcpClientsFile = configDir.resolve("mcp-clients.json");
        this.acpAgentsFile = configDir.resolve("acp-agents.json");
        this.envsFile = secretDir.resolve("envs.json");
        this.securityRulesFile = configDir.resolve("security-rules.json");
        this.securityConfigFile = configDir.resolve("security-config.json");
        this.backupsDir = root.resolve(AppHomeConstants.BACKUPS_DIR);
        this.voiceTranscriptionFile = configDir.resolve("voice-transcription.json");
        this.workspaceRoot = root.resolve(AppHomeConstants.AGENT_WORKSPACE_DIR);
        this.skillsPoolRoot = root.resolve(AppHomeConstants.SKILL_POOL_DIR);
        this.logsDir = root.resolve(AppHomeConstants.LOGS_DIR);
        this.pluginsDir = root.resolve(AppHomeConstants.PLUGINS_DIR);
        this.projectsRoot = root.resolve(AppHomeConstants.PROJECTS_DIR);
    }
}
