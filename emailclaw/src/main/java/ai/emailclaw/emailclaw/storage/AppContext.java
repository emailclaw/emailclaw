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

import ai.emailclaw.emailclaw.model.AcpAgentInfo;
import ai.emailclaw.emailclaw.model.AgentConfiguration;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentStatRecord;
import ai.emailclaw.emailclaw.model.BackupInfo;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.EnvVariable;
import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.model.HeartbeatConfig;
import ai.emailclaw.emailclaw.model.McpClientInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.SecurityRule;
import ai.emailclaw.emailclaw.model.TokenUsageRecord;
import ai.emailclaw.emailclaw.model.ToolInfo;
import ai.emailclaw.emailclaw.model.VoiceTranscriptionConfig;
import ai.emailclaw.emailclaw.util.UuidUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application runtime context.
 *
 * <p>Responsibilities:
 * <br>1) Aggregate path objects;
 * <br>2) Expose unified configuration read/write entry (delegated to ConfigManager);
 * <br>3) Provide workspace path and session metadata helper methods.
 */
public class AppContext implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(AppContext.class.getName());

    private final AppPaths paths;
    private final ConfigManager configManager;

    public AppContext(AppPaths paths) {
        this.paths = paths;
        this.configManager = new ConfigManager(paths);
    }

    public AppPaths paths() {
        return paths;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public void ensureStructure() {
        try {
            LOGGER.log(Level.INFO, "Initialize working directory structure: {0}", paths.root);
            Files.createDirectories(paths.root);
            Files.createDirectories(paths.configDir);
            Files.createDirectories(paths.secretDir);
            Files.createDirectories(paths.workspaceRoot);
            Files.createDirectories(paths.skillsPoolRoot);
            //            Files.createDirectories(paths.sessionsRoot);
            Files.createDirectories(paths.chatHistoryRoot);
            Files.createDirectories(paths.backupsDir);
            Files.createDirectories(paths.logsDir);
            Files.createDirectories(paths.pluginsDir);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize working directory", e);
            throw new RuntimeException("Failed to initialize workspace", e);
        }
    }

    // --- Providers ---
    public List<ProviderInfo> loadProviders() {
        return configManager.getProviders();
    }

    public void saveProviders(List<ProviderInfo> providers) {
        configManager.saveProviders(providers);
    }

    // --- Agents ---
    public List<AgentInfo> loadAgents() {
        return configManager.getAgents();
    }

    public void saveAgents(List<AgentInfo> agents) {
        configManager.saveAgents(agents);
    }

    // --- Global Config ---
    public GlobalConfig loadGlobalConfig() {
        return configManager.getGlobalConfig();
    }

    public void saveGlobalConfig(GlobalConfig config) {
        configManager.saveGlobalConfig(config);
    }

    // --- Sessions ---
    public List<ChatSessionInfo> loadSessions() {
        return configManager.getSessions();
    }

    public void saveSessions(List<ChatSessionInfo> sessions) {
        configManager.saveSessions(sessions);
    }

    // --- Tools ---
    public List<ToolInfo> loadTools(List<ToolInfo> defaults) {
        return configManager.getTools(defaults);
    }

    public void saveTools(List<ToolInfo> tools) {
        configManager.saveTools(tools);
    }

    // --- Token Usage ---
    public List<TokenUsageRecord> loadTokenUsage() {
        return configManager.getTokenUsageRecords();
    }

    public void saveTokenUsage(List<TokenUsageRecord> records) {
        configManager.saveTokenUsageRecords(records);
    }

    // --- Agent Stats ---
    public List<AgentStatRecord> loadAgentStats() {
        return configManager.getAgentStats();
    }

    public void saveAgentStats(List<AgentStatRecord> records) {
        configManager.saveAgentStats(records);
    }

    // --- Channels ---
    public List<ChannelInfo> loadChannels() {
        return configManager.getChannels();
    }

    public void saveChannels(List<ChannelInfo> channels) {
        configManager.saveChannels(channels);
    }

    // --- Heartbeat (per agent) ---
    public HeartbeatConfig loadHeartbeat(String agentId) {
        LOGGER.log(Level.INFO, "Heartbeat call start: read heartbeat config, agent={0}", agentId);
        return configManager.getHeartbeat(agentId);
    }

    public void saveHeartbeat(String agentId, HeartbeatConfig config) {
        LOGGER.log(Level.INFO, "Heartbeat call start: save heartbeat config, agent={0}", agentId);
        configManager.saveHeartbeat(agentId, config);
    }

    // --- MCP Clients ---
    public List<McpClientInfo> loadMcpClients() {
        return configManager.getMcpClients();
    }

    public void saveMcpClients(List<McpClientInfo> clients) {
        configManager.saveMcpClients(clients);
    }

    // --- ACP Agents ---
    public List<AcpAgentInfo> loadAcpAgents() {
        return configManager.getAcpAgents();
    }

    public void saveAcpAgents(List<AcpAgentInfo> agents) {
        configManager.saveAcpAgents(agents);
    }

    // --- Agent Configuration (per agent) ---
    public AgentConfiguration loadAgentConfig(String agentId) {
        return configManager.getAgentConfiguration(agentId);
    }

    public void saveAgentConfig(String agentId, AgentConfiguration config) {
        configManager.saveAgentConfiguration(agentId, config);
    }

    // --- Environments ---
    public List<EnvVariable> loadEnvVariables() {
        return configManager.getEnvVariables();
    }

    public void saveEnvVariables(List<EnvVariable> vars) {
        configManager.saveEnvVariables(vars);
    }

    // --- Security Rules ---
    public List<SecurityRule> loadSecurityRules() {
        return configManager.getSecurityRules();
    }

    public void saveSecurityRules(List<SecurityRule> rules) {
        configManager.saveSecurityRules(rules);
    }

    // --- Backups ---
    public List<BackupInfo> loadBackups() {
        return configManager.getBackups();
    }

    public void saveBackups(List<BackupInfo> backups) {
        configManager.saveBackups(backups);
    }

    // --- Voice Transcription ---
    public VoiceTranscriptionConfig loadVoiceTranscription() {
        return configManager.getVoiceTranscription();
    }

    public void saveVoiceTranscription(VoiceTranscriptionConfig config) {
        configManager.saveVoiceTranscription(config);
    }

    // --- Paths helpers ---
    public Path workspaceFor(String agentId) {
        return paths.workspaceRoot.resolve(agentId);
    }

    public Path skillsForWorkspace(String agentId) {
        return workspaceFor(agentId).resolve(WorkspacePaths.SKILLS_DIR);
    }

    public ChatSessionInfo createSession(String agentId) {
        LOGGER.log(Level.FINE, "Create session metadata: agent={0}", agentId);
        ChatSessionInfo info = new ChatSessionInfo();
        info.setId(UuidUtils.randomUUIDv7().toString());
        info.setAgentId(agentId);
        info.setName("New Chat");
        info.setChannel("console");
        String now = LocalDateTime.now().toString();
        info.setCreatedAt(now);
        info.setUpdatedAt(now);
        return info;
    }

    @Override
    public void close() {
        configManager.close();
    }
}
