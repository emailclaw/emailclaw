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
import ai.emailclaw.emailclaw.model.AgentIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentStatRecord;
import ai.emailclaw.emailclaw.model.BackupInfo;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.CronJobModel;
import ai.emailclaw.emailclaw.model.EnvVariable;
import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.model.HeartbeatConfig;
import ai.emailclaw.emailclaw.model.McpClientInfo;
import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.SecurityRule;
import ai.emailclaw.emailclaw.model.SecuritySettings;
import ai.emailclaw.emailclaw.model.TokenUsageRecord;
import ai.emailclaw.emailclaw.model.ToolInfo;
import ai.emailclaw.emailclaw.model.VoiceTranscriptionConfig;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.service.ProviderCatalog;
import ai.emailclaw.emailclaw.tools.BuiltInToolNames;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.core.type.TypeReference;

/**
 * Application configuration unified manager.
 *
 * <p>Design goals:
 * <br>1) As a globally unique configuration source, uniformly maintains memory state;
 * <br>2) Uniformly handles JSON serialization/deserialization;
 * <br>3) Uniformly integrates config file hot-loading and publishes change events; note that data modified in memory should be saved immediately by calling save method, otherwise hot-loading might lead to loss of modified data;
 * <br>4) Intercepts self-write echo events via "last local write content fingerprint".
 */
public class ConfigManager {

    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());

    /**
     * Config keys used for external listening.
     */
    public static final String EVENT_PROVIDERS = "providers";

    public static final String EVENT_AGENTS = "agents";

    public static final String EVENT_GLOBAL_CONFIG = "global_config";

    public static final String EVENT_TOOLS = "tools";

    public static final String EVENT_SESSIONS = "sessions";

    public static final String EVENT_TOKEN_USAGE = "token_usage";

    public static final String EVENT_AGENT_STATS = "agent_stats";

    // The current channel design pattern is that the thread will continuously read the latest
    // Channel config, supporting instant effect after user modifies config in UI. Therefore
    // EVENT_CHANNELS is not needed
    public static final String EVENT_CHANNELS = "channels";

    public static final String EVENT_CRON_JOBS = "cron_jobs";

    public static final String EVENT_MCP_CLIENTS = "mcp_clients";

    public static final String EVENT_ACP_AGENTS = "acp_agents";

    public static final String EVENT_PROJECTS = "projects";

    public static final String EVENT_ENVS = "envs";

    public static final String EVENT_SECURITY_RULES = "security_rules";

    public static final String EVENT_SECURITY_CONFIG = "security_config";

    public static final String EVENT_BACKUPS = "backups";

    public static final String EVENT_VOICE_TRANSCRIPTION = "voice_transcription";

    private static final java.util.regex.Pattern BUILTIN_VARIANT_PATTERN =
            java.util.regex.Pattern.compile("^(.+)-(en|zh)$");

    private static final TypeReference<List<ProviderInfo>> PROVIDERS_REF =
            new TypeReference<List<ProviderInfo>>() {};

    private static final TypeReference<List<AgentInfo>> AGENTS_REF =
            new TypeReference<List<AgentInfo>>() {};

    private static final TypeReference<List<ToolInfo>> TOOLS_REF =
            new TypeReference<List<ToolInfo>>() {};

    private static final TypeReference<List<ChatSessionInfo>> SESSIONS_REF =
            new TypeReference<List<ChatSessionInfo>>() {};

    private static final TypeReference<List<TokenUsageRecord>> TOKEN_USAGE_REF =
            new TypeReference<List<TokenUsageRecord>>() {};

    private static final TypeReference<List<AgentStatRecord>> AGENT_STATS_REF =
            new TypeReference<List<AgentStatRecord>>() {};

    private static final TypeReference<List<ChannelInfo>> CHANNELS_REF =
            new TypeReference<List<ChannelInfo>>() {};

    private static final TypeReference<List<CronJobModel.CronJobSpec>> CRON_JOBS_REF =
            new TypeReference<List<CronJobModel.CronJobSpec>>() {};

    private static final TypeReference<List<McpClientInfo>> MCP_CLIENTS_REF =
            new TypeReference<List<McpClientInfo>>() {};

    private static final TypeReference<List<AcpAgentInfo>> ACP_AGENTS_REF =
            new TypeReference<List<AcpAgentInfo>>() {};

    private static final TypeReference<List<EnvVariable>> ENVS_REF =
            new TypeReference<List<EnvVariable>>() {};

    private static final TypeReference<List<ProjectInfo>> PROJECTS_REF =
            new TypeReference<List<ProjectInfo>>() {};

    private static final TypeReference<List<SecurityRule>> SECURITY_RULES_REF =
            new TypeReference<List<SecurityRule>>() {};

    private static final TypeReference<List<BackupInfo>> BACKUPS_REF =
            new TypeReference<List<BackupInfo>>() {};

    private final CachedState<List<ProjectInfo>> projectsState = new CachedState<>();

    private final AppPaths paths;

    private final JsonStore jsonStore = new JsonStore();

    private final Path backupsMetaFile;

    /**
     * Global filesystem watcher, used to receive external modification events of config files.
     */
    private final WatchService watchService;

    /**
     * File-level callback index: which reload functions should be executed after a specific file changes.
     */
    private final ConcurrentHashMap<Path, CopyOnWriteArrayList<Runnable>> reloadCallbacksByFile =
            new ConcurrentHashMap<>();

    /**
     * Directory -> WatchKey mapping, avoid duplicate registration of the same directory.
     */
    private final ConcurrentHashMap<Path, WatchKey> watchKeyByDirectory = new ConcurrentHashMap<>();

    /**
     * WatchKey -> directory reverse mapping, used to quickly locate absolute file path from events.
     */
    private final ConcurrentHashMap<WatchKey, Path> directoryByWatchKey = new ConcurrentHashMap<>();

    /**
     * Watcher thread running flag. Set to false when close() is called.
     */
    private volatile boolean watcherRunning = true;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Runnable>> listenersByKey =
            new ConcurrentHashMap<>();

    private final CachedState<List<ProviderInfo>> providersState = new CachedState<>();

    private final CachedState<List<AgentInfo>> agentsState = new CachedState<>();

    private final CachedState<GlobalConfig> globalConfigState = new CachedState<>();

    private final CachedState<List<ToolInfo>> toolsState = new CachedState<>();

    private final CachedState<List<ChatSessionInfo>> sessionsState = new CachedState<>();

    private final CachedState<List<TokenUsageRecord>> tokenUsageState = new CachedState<>();

    private final CachedState<List<AgentStatRecord>> agentStatsState = new CachedState<>();

    private final CachedState<List<ChannelInfo>> channelsState = new CachedState<>();

    private final CachedState<List<CronJobModel.CronJobSpec>> cronJobsState = new CachedState<>();

    private final CachedState<List<McpClientInfo>> mcpClientsState = new CachedState<>();

    private final CachedState<List<AcpAgentInfo>> acpAgentsState = new CachedState<>();

    private final CachedState<List<EnvVariable>> envVariablesState = new CachedState<>();

    private final CachedState<List<SecurityRule>> securityRulesState = new CachedState<>();

    private final CachedState<SecuritySettings> securitySettingsState = new CachedState<>();

    private final CachedState<List<BackupInfo>> backupsState = new CachedState<>();

    private final CachedState<VoiceTranscriptionConfig> voiceTranscriptionState =
            new CachedState<>();

    /**
     * Cache per-agent configs (files within workspace) by agentId.
     * This type of config might be numerous, lazy loading on demand is sufficient.
     */
    private final ConcurrentHashMap<String, CachedState<HeartbeatConfig>> heartbeatStateByAgent =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, CachedState<AgentConfiguration>>
            agentConfigStateByAgent = new ConcurrentHashMap<>();

    public ConfigManager(AppPaths paths) {
        this.paths = paths;
        this.backupsMetaFile = paths.backupsDir.resolve("backups-meta.json");
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize ConfigManager WatchService", e);
        }
        // Register watch targets first, then start thread, avoiding the race window where "thread
        // runs first, targets not attached yet".
        registerGlobalWatchers();
        Thread.ofVirtual().name("ConfigManager-Watcher").start(this::watchFileChanges);
    }

    /**
     * Register config change listener.
     *
     * <p>Listeners are triggered after "external file change hot-load successful" and "in-program save successful".
     */
    public void addChangeListener(String configKey, Runnable listener) {
        if (configKey == null || listener == null) {
            return;
        }
        listenersByKey
                .computeIfAbsent(configKey, ignored -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    // ------------------------- Providers -------------------------
    public List<ProviderInfo> getProviders() {
        boolean changed = false;
        synchronized (providersState.lock) {
            ensureProvidersLoadedLocked();
            if (providersState.value.isEmpty()) {
                providersState.value = ProviderCatalog.builtins();
                changed = true;
            } else {
                changed = mergeBuiltinProviders(providersState.value);
            }
            if (changed) {
                providersState.value.sort(Comparator.comparing(item -> item.getName()));
                providersState.lastWrittenContent =
                        writeJson(
                                paths.providersFile,
                                providersState.value,
                                providersState.lastWrittenContent);
            }
            return providersState.value;
        }
    }

    public void saveProviders(List<ProviderInfo> providers) {
        List<ProviderInfo> toSave = providers == null ? new ArrayList<>() : providers;
        toSave.sort(Comparator.comparing(item -> item.getName()));
        synchronized (providersState.lock) {
            providersState.value = toSave;
            providersState.lastWrittenContent =
                    writeJson(paths.providersFile, toSave, providersState.lastWrittenContent);
        }
        notifyChange(EVENT_PROVIDERS);
    }

    private void ensureProvidersLoadedLocked() {
        if (providersState.value != null) {
            return;
        }
        providersState.value = readList(paths.providersFile, PROVIDERS_REF, providersState);
    }

    /**
     * Compatibility strategy for incremental upgrade of built-in Provider.
     */
    private boolean mergeBuiltinProviders(List<ProviderInfo> providers) {
        boolean changed = false;
        changed |= migrateLegacyProviderIds(providers);
        List<ProviderInfo> builtins = ProviderCatalog.builtins();
        for (ProviderInfo builtin : builtins) {
            ProviderInfo existing =
                    providers.stream()
                            .filter(item -> item.getId().equals(builtin.getId()))
                            .findFirst()
                            .orElse(null);
            if (existing == null) {
                providers.add(builtin);
                changed = true;
                continue;
            }
            if (mergeBuiltinModels(existing, builtin)) {
                changed = true;
            }
            if (existing.getExtraModels() == null) {
                existing.setExtraModels(new ArrayList<>());
                changed = true;
            }
            if ((existing.getMeta() == null || existing.getMeta().isEmpty())
                    && builtin.getMeta() != null) {
                existing.setMeta(builtin.getMeta());
                changed = true;
            }
            if (builtin.isSupportsOAuth() && !existing.isSupportsOAuth()) {
                existing.setSupportsOAuth(true);
                changed = true;
            }
            if (builtin.isFreeTier() && !existing.isFreeTier()) {
                existing.setFreeTier(true);
                changed = true;
            }
            if ("dashscope".equals(existing.getId()) && existing.isFreezeUrl()) {
                existing.setFreezeUrl(false);
                changed = true;
            }
            if ("anthropic".equals(existing.getId())
                    && (existing.getGenerateKwargs() == null
                            || existing.getGenerateKwargs().isEmpty())
                    && builtin.getGenerateKwargs() != null
                    && !builtin.getGenerateKwargs().isEmpty()) {
                existing.setGenerateKwargs(builtin.getGenerateKwargs());
                changed = true;
            }
        }
        return changed;
    }

    private boolean mergeBuiltinModels(ProviderInfo existing, ProviderInfo builtin) {
        boolean changed = false;
        if (existing.getModels() == null) {
            existing.setModels(new ArrayList<>());
            changed = true;
        }
        Map<String, ModelInfo> existingModelsById = new HashMap<>();
        for (ModelInfo model : existing.getModels()) {
            if (model != null && model.getId() != null) {
                existingModelsById.put(model.getId(), model);
            }
        }
        if (existingModelsById.isEmpty()
                && builtin.getModels() != null
                && !builtin.getModels().isEmpty()) {
            existing.setModels(new ArrayList<>());
            for (ModelInfo builtinModel : builtin.getModels()) {
                existing.getModels().add(copyModelInfo(builtinModel));
            }
            return true;
        }
        for (ModelInfo builtinModel : builtin.getModels()) {
            if (builtinModel == null || builtinModel.getId() == null) {
                continue;
            }
            ModelInfo existingModel = existingModelsById.get(builtinModel.getId());
            if (existingModel == null) {
                existing.getModels().add(copyModelInfo(builtinModel));
                changed = true;
            } else {
                if (!existingModel.isBuiltIn()) {
                    existingModel.setBuiltIn(true);
                    changed = true;
                }
                if ((existingModel.getName() == null || existingModel.getName().isBlank())
                        && builtinModel.getName() != null) {
                    existingModel.setName(builtinModel.getName());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private ModelInfo copyModelInfo(ModelInfo source) {
        ModelInfo copy = new ModelInfo();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setBuiltIn(source.isBuiltIn());
        copy.setFree(source.isFree());
        copy.setSupportsImage(source.isSupportsImage());
        copy.setSupportsVideo(source.isSupportsVideo());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setMaxInputLength(source.getMaxInputLength());
        copy.setGenerateKwargs(
                source.getGenerateKwargs() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(source.getGenerateKwargs()));
        return copy;
    }

    /**
     * Migrate legacy Provider IDs, preserving user-saved API Key and Base URL.
     *
     * <p>Emailclaw splits {@code volcano} into {@code volcengine-cn} and
     * {@code volcengine-cn-codingplan}, done here for backward compatibility.
     */
    private boolean migrateLegacyProviderIds(List<ProviderInfo> providers) {
        boolean changed = false;
        ProviderInfo legacyVolcano =
                providers.stream()
                        .filter(item -> "volcano".equals(item.getId()))
                        .findFirst()
                        .orElse(null);
        if (legacyVolcano == null) {
            return false;
        }
        ProviderInfo target =
                providers.stream()
                        .filter(item -> "volcengine-cn".equals(item.getId()))
                        .findFirst()
                        .orElse(null);
        if (target == null) {
            legacyVolcano.setId("volcengine-cn");
            legacyVolcano.setName("Volcano Engine");
            changed = true;
        } else {
            if ((target.getApiKey() == null || target.getApiKey().isBlank())
                    && legacyVolcano.getApiKey() != null) {
                target.setApiKey(legacyVolcano.getApiKey());
                changed = true;
            }
            if ((target.getBaseUrl() == null || target.getBaseUrl().isBlank())
                    && legacyVolcano.getBaseUrl() != null) {
                target.setBaseUrl(legacyVolcano.getBaseUrl());
                changed = true;
            }
            target.getExtraModels().addAll(legacyVolcano.getExtraModels());
            providers.remove(legacyVolcano);
            changed = true;
        }
        return changed;
    }

    // ------------------------- Agents -------------------------
    public List<AgentInfo> getAgents() {
        boolean changed = false;
        synchronized (agentsState.lock) {
            ensureAgentsLoadedLocked();
            if (agentsState.value.isEmpty()) {
                agentsState.value = buildDefaultAgents();
                changed = true;
            }
            if (changed) {
                agentsState.lastWrittenContent =
                        writeJson(
                                paths.agentsFile,
                                agentsState.value,
                                agentsState.lastWrittenContent);
            }
            return agentsState.value;
        }
    }

    public void saveAgents(List<AgentInfo> agents) {
        List<AgentInfo> toSave = agents == null ? new ArrayList<>() : agents;
        synchronized (agentsState.lock) {
            agentsState.value = toSave;
            agentsState.lastWrittenContent =
                    writeJson(paths.agentsFile, toSave, agentsState.lastWrittenContent);
        }
        notifyChange(EVENT_AGENTS);
    }

    private void ensureAgentsLoadedLocked() {
        if (agentsState.value != null) {
            return;
        }
        agentsState.value = readList(paths.agentsFile, AGENTS_REF, agentsState);
    }

    private List<AgentInfo> buildDefaultAgents() {
        List<String> allSkillNames = new ArrayList<>();
        if (java.nio.file.Files.exists(paths.skillsPoolRoot)) {
            java.util.Set<String> uniqueNames = new java.util.HashSet<>();
            try (java.util.stream.Stream<java.nio.file.Path> stream =
                    java.nio.file.Files.list(paths.skillsPoolRoot)) {
                stream.filter(java.nio.file.Files::isDirectory)
                        .forEach(
                                dir -> {
                                    String rawName =
                                            dir.getFileName()
                                                    .toString()
                                                    .replaceFirst("\\.skill$", "");
                                    java.util.regex.Matcher matcher =
                                            BUILTIN_VARIANT_PATTERN.matcher(rawName);
                                    String canonicalName =
                                            matcher.matches() ? matcher.group(1) : rawName;
                                    uniqueNames.add(canonicalName);
                                });
            } catch (java.io.IOException ignored) {
            }
            allSkillNames.addAll(uniqueNames);
            java.util.Collections.sort(allSkillNames);
        }
        LOGGER.info("allSkillNames.size===" + allSkillNames.size());
        List<AgentInfo> agents = new ArrayList<>();
        AgentInfo defaultAgent = new AgentInfo();
        defaultAgent.setId(AgentIds.DEFAULT);
        defaultAgent.setName("Default Agent");
        defaultAgent.setDescription("Default assistant");
        defaultAgent.setWorkspacePath(paths.workspaceRoot.resolve(defaultAgent.getId()).toString());
        defaultAgent.getSkillNames().addAll(allSkillNames);
        agents.add(defaultAgent);

        AgentInfo planner = new AgentInfo();
        planner.setId("planner");
        planner.setName("Planner");
        planner.setDescription(
                "The brain of the system. Receives raw fuzzy instructions from users, breaks them"
                    + " down into ordered sub-tasks, and decides which specific Agent to dispatch"
                    + " the tasks to.");
        planner.setWorkspacePath(paths.workspaceRoot.resolve(planner.getId()).toString());
        planner.getSkillNames().addAll(allSkillNames);
        agents.add(planner);

        AgentInfo executor = new AgentInfo();
        executor.setId("executor");
        executor.setName("Executor");
        executor.setDescription(
                "The hands and feet of the system. Responsible for executing specific actions. It"
                    + " can call external tools (such as search engine API, execute local scripts,"
                    + " query databases) to obtain real data or complete specific calculations.");
        executor.setWorkspacePath(paths.workspaceRoot.resolve(executor.getId()).toString());
        executor.getSkillNames().addAll(allSkillNames);
        agents.add(executor);

        AgentInfo reviewer = new AgentInfo();
        reviewer.setId("reviewer");
        reviewer.setName("Reviewer");
        reviewer.setDescription(
                "The system's quality inspector. Receives output from the executor and checks it"
                    + " based on specific standards (such as code specs, factual accuracy, logical"
                    + " coherence). If it does not meet the standards, it will return the task to"
                    + " the executor for a retry.");
        reviewer.setWorkspacePath(paths.workspaceRoot.resolve(reviewer.getId()).toString());
        reviewer.getSkillNames().addAll(allSkillNames);
        agents.add(reviewer);

        AgentInfo synthesizer = new AgentInfo();
        synthesizer.setId("synthesizer");
        synthesizer.setName("Synthesizer");
        synthesizer.setDescription(
                "The system's external window. Collects fragmented results and review comments from"
                    + " all previous links, integrates, typesets, and converts them into a final"
                    + " response that meets the user's expected format.");
        synthesizer.setWorkspacePath(paths.workspaceRoot.resolve(synthesizer.getId()).toString());
        synthesizer.getSkillNames().addAll(allSkillNames);
        agents.add(synthesizer);

        return agents;
    }

    // ------------------------- Global Config -------------------------
    /**
     * Get global config.
     *
     * <p>Returns default values when config file doesn't exist, and automatically writes a {@code global-config.json},
     * so users can directly edit the file later (and it can be monitored by hot-load).
     */
    public GlobalConfig getGlobalConfig() {
        synchronized (globalConfigState.lock) {
            if (globalConfigState.value == null) {
                GlobalConfig fallback = new GlobalConfig();
                globalConfigState.value =
                        readObject(
                                paths.globalConfigFile,
                                GlobalConfig.class,
                                fallback,
                                globalConfigState,
                                EVENT_GLOBAL_CONFIG);
                if (!Files.exists(paths.globalConfigFile)) {
                    globalConfigState.lastWrittenContent =
                            writeJson(
                                    paths.globalConfigFile,
                                    globalConfigState.value,
                                    globalConfigState.lastWrittenContent);
                }
            }
            return globalConfigState.value;
        }
    }

    /**
     * Save global config and broadcast change events.
     */
    public void saveGlobalConfig(GlobalConfig config) {
        LOGGER.info("saveGlobalConfig starts...");
        GlobalConfig toSave = config == null ? new GlobalConfig() : config;
        synchronized (globalConfigState.lock) {
            globalConfigState.value = toSave;
            globalConfigState.lastWrittenContent =
                    writeJson(paths.globalConfigFile, toSave, globalConfigState.lastWrittenContent);
        }
        notifyChange(EVENT_GLOBAL_CONFIG);
    }

    // ------------------------- Tools -------------------------
    public List<ToolInfo> getTools(List<ToolInfo> defaults) {
        boolean changed = false;
        synchronized (toolsState.lock) {
            if (toolsState.value == null) {
                toolsState.value = readList(paths.toolConfigFile, TOOLS_REF, toolsState);
            }
            if (toolsState.value.isEmpty()) {
                toolsState.value = defaults == null ? new ArrayList<>() : new ArrayList<>(defaults);
                changed = true;
            } else if (defaults != null) {
                // Migrate legacy config: shell execution has uniformly used AgentScope Harness's
                // execute tool,
                // Emailclaw's old execute_shell_command is no longer registered to avoid duplicate
                // entries in UI and tool switches.
                boolean removedRetiredTool =
                        toolsState.value.removeIf(
                                item ->
                                        item != null
                                                && BuiltInToolNames.EXECUTE_SHELL_COMMAND.equals(
                                                        item.name()));
                changed = changed || removedRetiredTool;
                for (ToolInfo builtin : defaults) {
                    boolean exists =
                            toolsState.value.stream()
                                    .anyMatch(item -> item.name().equals(builtin.name()));
                    if (!exists) {
                        toolsState.value.add(builtin);
                        changed = true;
                    }
                }
            }
            if (changed) {
                toolsState.lastWrittenContent =
                        writeJson(
                                paths.toolConfigFile,
                                toolsState.value,
                                toolsState.lastWrittenContent);
            }
            return toolsState.value;
        }
    }

    public void saveTools(List<ToolInfo> tools) {
        LOGGER.info("saveTools starts...");
        List<ToolInfo> toSave = tools == null ? new ArrayList<>() : tools;
        synchronized (toolsState.lock) {
            toolsState.value = toSave;
            toolsState.lastWrittenContent =
                    writeJson(paths.toolConfigFile, toSave, toolsState.lastWrittenContent);
        }
        notifyChange(EVENT_TOOLS);
    }

    // ------------------------- Sessions -------------------------
    public List<ChatSessionInfo> getSessions() {
        synchronized (sessionsState.lock) {
            if (sessionsState.value == null) {
                sessionsState.value = readList(paths.sessionsMetaFile, SESSIONS_REF, sessionsState);
            }
            return sessionsState.value;
        }
    }

    public void saveSessions(List<ChatSessionInfo> sessions) {
        LOGGER.info("saveSessions starts...");
        List<ChatSessionInfo> toSave = sessions == null ? new ArrayList<>() : sessions;
        synchronized (sessionsState.lock) {
            sessionsState.value = toSave;
            sessionsState.lastWrittenContent =
                    writeJson(paths.sessionsMetaFile, toSave, sessionsState.lastWrittenContent);
        }
        notifyChange(EVENT_SESSIONS);
    }

    // ------------------------- Token Usage -------------------------
    public List<TokenUsageRecord> getTokenUsageRecords() {
        synchronized (tokenUsageState.lock) {
            if (tokenUsageState.value == null) {
                tokenUsageState.value =
                        readList(paths.tokenUsageFile, TOKEN_USAGE_REF, tokenUsageState);
            }
            return tokenUsageState.value;
        }
    }

    public void saveTokenUsageRecords(List<TokenUsageRecord> records) {
        LOGGER.info("saveTokenUsageRecords starts...");
        List<TokenUsageRecord> toSave = records == null ? new ArrayList<>() : records;
        synchronized (tokenUsageState.lock) {
            tokenUsageState.value = toSave;
            tokenUsageState.lastWrittenContent = writeJson(paths.tokenUsageFile, toSave);
        }
        notifyChange(EVENT_TOKEN_USAGE);
    }

    // ------------------------- Agent Stats -------------------------
    public List<AgentStatRecord> getAgentStats() {
        synchronized (agentStatsState.lock) {
            if (agentStatsState.value == null) {
                agentStatsState.value =
                        readList(paths.agentStatsFile, AGENT_STATS_REF, agentStatsState);
            }
            return agentStatsState.value;
        }
    }

    public void saveAgentStats(List<AgentStatRecord> records) {
        LOGGER.info("saveAgentStats starts...");
        List<AgentStatRecord> toSave = records == null ? new ArrayList<>() : records;
        synchronized (agentStatsState.lock) {
            agentStatsState.value = toSave;
            agentStatsState.lastWrittenContent =
                    writeJson(paths.agentStatsFile, toSave, agentStatsState.lastWrittenContent);
        }
        notifyChange(EVENT_AGENT_STATS);
    }

    // ------------------------- Channels -------------------------
    public List<ChannelInfo> getChannels() {
        synchronized (channelsState.lock) {
            if (channelsState.value == null) {
                channelsState.value = readList(paths.channelsFile, CHANNELS_REF, channelsState);
            }
            return channelsState.value;
        }
    }

    public void saveChannels(List<ChannelInfo> channels) {
        LOGGER.info("saveChannels starts...");
        List<ChannelInfo> toSave = channels == null ? new ArrayList<>() : channels;
        synchronized (channelsState.lock) {
            channelsState.value = toSave;
            channelsState.lastWrittenContent =
                    writeJson(paths.channelsFile, toSave, channelsState.lastWrittenContent);
        }
        notifyChange(EVENT_CHANNELS);
    }

    // ------------------------- Scheduled -------------------------
    /**
     * Get cron jobs list.
     *
     * <p>Internally unpacks JobsFile wrapper in cron-jobs.json and returns CronJobSpec list.
     * Supports compatible reading of jobsFile's "version" format and legacy pure array format.
     */
    public List<CronJobModel.CronJobSpec> getCronJobs() {
        synchronized (cronJobsState.lock) {
            if (cronJobsState.value == null) {
                cronJobsState.value = readCronJobsFromDisk();
            }
            return cronJobsState.value;
        }
    }

    /**
     * Save cron jobs list.
     *
     * <p>Writes to cron-jobs.json in JobsFile format (with version field),
     * and broadcasts change event after write completes, triggering CronJobService hot-load.
     */
    public void saveCronJobs(List<CronJobModel.CronJobSpec> jobs) {
        LOGGER.info("saveCronJobs starts...");
        List<CronJobModel.CronJobSpec> toSave = jobs == null ? new ArrayList<>() : jobs;
        synchronized (cronJobsState.lock) {
            cronJobsState.value = toSave;
            cronJobsState.lastWrittenContent =
                    writeCronJobsToDisk(toSave, cronJobsState.lastWrittenContent);
        }
        notifyChange(EVENT_CRON_JOBS);
    }

    /**
     * Read cron-jobs.json from disk, compatible with new JobsFile format and legacy pure array format.
     */
    private List<CronJobModel.CronJobSpec> readCronJobsFromDisk() {
        Path file = paths.cronJobsFile;
        if (!Files.exists(file)) {
            cronJobsState.lastWrittenContent = "";
            return new ArrayList<>();
        }
        try {
            String content = Files.readString(file);
            cronJobsState.lastWrittenContent = content;
            if (content.isBlank()) {
                return new ArrayList<>();
            }
            // New version: JobsFile wrapper format containing "version" field
            if (content.contains("\"version\"")) {
                CronJobModel.JobsFile jobsFile =
                        jsonStore.parse(content, CronJobModel.JobsFile.class, null);
                return jobsFile == null || jobsFile.jobs() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(jobsFile.jobs());
            }
            // Legacy compatibility: pure CronJobSpec array
            List<CronJobModel.CronJobSpec> jobs = jsonStore.parseList(content, CRON_JOBS_REF);
            return jobs == null ? new ArrayList<>() : jobs;
        } catch (Exception e) {
            String errorMsg = jsonStore.formatErrorMessage(file, e);
            LOGGER.log(Level.WARNING, "Failed to read cron-jobs.json: " + errorMsg, e);
            cronJobsState.lastWrittenContent = "";
            return new ArrayList<>();
        }
    }

    /**
     * Write cron-jobs.json in JobsFile format, containing fingerprint deduplication and atomic write.
     */
    private String writeCronJobsToDisk(
            List<CronJobModel.CronJobSpec> jobs, String lastWrittenContent) {
        CronJobModel.JobsFile jobsFile = new CronJobModel.JobsFile(1, jobs);
        return jsonStore.writeIfChanged(paths.cronJobsFile, jobsFile, lastWrittenContent);
    }

    /**
     * Hot-load cron-jobs.json from disk, broadcast EVENT_CRON_JOBS when changed.
     */
    private void reloadCronJobsFromDisk() {
        boolean changed = false;
        synchronized (cronJobsState.lock) {
            try {
                String content =
                        Files.exists(paths.cronJobsFile)
                                ? Files.readString(paths.cronJobsFile)
                                : "";
                if (Objects.equals(content, cronJobsState.lastWrittenContent)) {
                    return;
                }
                List<CronJobModel.CronJobSpec> loaded;
                if (content.isBlank()) {
                    loaded = new ArrayList<>();
                } else if (content.contains("\"version\"")) {
                    CronJobModel.JobsFile jobsFile =
                            jsonStore.parse(content, CronJobModel.JobsFile.class, null);
                    loaded =
                            jobsFile == null || jobsFile.jobs() == null
                                    ? new ArrayList<>()
                                    : new ArrayList<>(jobsFile.jobs());
                } else {
                    List<CronJobModel.CronJobSpec> jobs =
                            jsonStore.parseList(content, CRON_JOBS_REF);
                    loaded = jobs == null ? new ArrayList<>() : jobs;
                }
                cronJobsState.value = loaded;
                cronJobsState.lastWrittenContent = content;
                changed = true;
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to hot-load cron-jobs.json, retaining old memory value",
                        e);
            }
        }
        if (changed) {
            notifyChange(EVENT_CRON_JOBS);
        }
    }

    // ------------------------- Projects -------------------------
    public List<ProjectInfo> getProjects() {
        synchronized (projectsState.lock) {
            if (projectsState.value == null || projectsState.value.isEmpty()) {
                projectsState.value = readList(paths.projectsFile, PROJECTS_REF, projectsState);
                // Initialize a project with id ProjectService.PROJECT_ID_DEFAULT when the system
                if (projectsState.value == null || projectsState.value.isEmpty()) {
                    ProjectInfo defaultProject = ProjectService.PROJECT_DEFAULT;
                    projectsState.value.add(defaultProject);
                    projectsState.lastWrittenContent =
                            writeJson(
                                    paths.projectsFile,
                                    projectsState.value,
                                    projectsState.lastWrittenContent);
                }
            }
            return projectsState.value;
        }
    }

    public void saveProjects(List<ProjectInfo> projects) {
        LOGGER.info("saveProjects starts...");
        List<ProjectInfo> toSave = projects == null ? new ArrayList<>() : projects;
        synchronized (projectsState.lock) {
            projectsState.value = toSave;
            projectsState.lastWrittenContent =
                    writeJson(paths.projectsFile, toSave, projectsState.lastWrittenContent);
        }
        notifyChange(EVENT_PROJECTS);
    }

    private void reloadProjectsFromDisk() {
        reloadListState(paths.projectsFile, PROJECTS_REF, projectsState, EVENT_PROJECTS);
    }

    // ------------------------- MCP Clients -------------------------
    public List<McpClientInfo> getMcpClients() {
        synchronized (mcpClientsState.lock) {
            if (mcpClientsState.value == null) {
                mcpClientsState.value =
                        readList(paths.mcpClientsFile, MCP_CLIENTS_REF, mcpClientsState);
            }
            return mcpClientsState.value;
        }
    }

    public void saveMcpClients(List<McpClientInfo> clients) {
        LOGGER.info("saveMcpClients starts...");
        List<McpClientInfo> toSave = clients == null ? new ArrayList<>() : clients;
        synchronized (mcpClientsState.lock) {
            mcpClientsState.value = toSave;
            mcpClientsState.lastWrittenContent =
                    writeJson(paths.mcpClientsFile, toSave, mcpClientsState.lastWrittenContent);
        }
        notifyChange(EVENT_MCP_CLIENTS);
    }

    // ------------------------- ACP Agents -------------------------
    public List<AcpAgentInfo> getAcpAgents() {
        synchronized (acpAgentsState.lock) {
            if (acpAgentsState.value == null) {
                acpAgentsState.value =
                        readList(paths.acpAgentsFile, ACP_AGENTS_REF, acpAgentsState);
            }
            return acpAgentsState.value;
        }
    }

    public void saveAcpAgents(List<AcpAgentInfo> agents) {
        LOGGER.info("saveAcpAgents starts...");
        List<AcpAgentInfo> toSave = agents == null ? new ArrayList<>() : agents;
        synchronized (acpAgentsState.lock) {
            acpAgentsState.value = toSave;
            acpAgentsState.lastWrittenContent =
                    writeJson(paths.acpAgentsFile, toSave, acpAgentsState.lastWrittenContent);
        }
        notifyChange(EVENT_ACP_AGENTS);
    }

    // ------------------------- Environments -------------------------
    public List<EnvVariable> getEnvVariables() {
        synchronized (envVariablesState.lock) {
            if (envVariablesState.value == null) {
                envVariablesState.value = readList(paths.envsFile, ENVS_REF, envVariablesState);
            }
            return envVariablesState.value;
        }
    }

    public void saveEnvVariables(List<EnvVariable> vars) {
        LOGGER.info("saveEnvVariables starts...");
        List<EnvVariable> toSave = vars == null ? new ArrayList<>() : vars;
        synchronized (envVariablesState.lock) {
            envVariablesState.value = toSave;
            envVariablesState.lastWrittenContent =
                    writeJson(paths.envsFile, toSave, envVariablesState.lastWrittenContent);
        }
        notifyChange(EVENT_ENVS);
    }

    // ------------------------- Security Rules -------------------------
    public List<SecurityRule> getSecurityRules() {
        synchronized (securityRulesState.lock) {
            if (securityRulesState.value == null) {
                securityRulesState.value =
                        readList(paths.securityRulesFile, SECURITY_RULES_REF, securityRulesState);
            }
            return securityRulesState.value;
        }
    }

    public void saveSecurityRules(List<SecurityRule> rules) {
        LOGGER.info("saveSecurityRules starts...");
        List<SecurityRule> toSave = rules == null ? new ArrayList<>() : rules;
        synchronized (securityRulesState.lock) {
            securityRulesState.value = toSave;
            securityRulesState.lastWrittenContent =
                    writeJson(
                            paths.securityRulesFile, toSave, securityRulesState.lastWrittenContent);
        }
        notifyChange(EVENT_SECURITY_RULES);
    }

    public SecuritySettings getSecuritySettings() {
        synchronized (securitySettingsState.lock) {
            if (securitySettingsState.value == null) {
                securitySettingsState.value =
                        readObject(
                                paths.securityConfigFile,
                                SecuritySettings.class,
                                new SecuritySettings(),
                                securitySettingsState,
                                EVENT_SECURITY_CONFIG);
                if (securitySettingsState.value == null) {
                    securitySettingsState.value = new SecuritySettings();
                }
            }
            return securitySettingsState.value;
        }
    }

    public void saveSecuritySettings(SecuritySettings settings) {
        SecuritySettings toSave = settings == null ? new SecuritySettings() : settings;
        synchronized (securitySettingsState.lock) {
            securitySettingsState.value = toSave;
            securitySettingsState.lastWrittenContent =
                    writeJson(
                            paths.securityConfigFile,
                            toSave,
                            securitySettingsState.lastWrittenContent);
        }
        notifyChange(EVENT_SECURITY_CONFIG);
    }

    // ------------------------- Backups -------------------------
    public List<BackupInfo> getBackups() {
        synchronized (backupsState.lock) {
            if (backupsState.value == null) {
                backupsState.value = readList(backupsMetaFile, BACKUPS_REF, backupsState);
            }
            return backupsState.value;
        }
    }

    public void saveBackups(List<BackupInfo> backups) {
        LOGGER.info("saveBackups starts...");
        List<BackupInfo> toSave = backups == null ? new ArrayList<>() : backups;
        synchronized (backupsState.lock) {
            backupsState.value = toSave;
            backupsState.lastWrittenContent =
                    writeJson(backupsMetaFile, toSave, backupsState.lastWrittenContent);
        }
        notifyChange(EVENT_BACKUPS);
    }

    // ------------------------- Voice Transcription -------------------------
    public VoiceTranscriptionConfig getVoiceTranscription() {
        synchronized (voiceTranscriptionState.lock) {
            if (voiceTranscriptionState.value == null) {
                voiceTranscriptionState.value =
                        readObject(
                                paths.voiceTranscriptionFile,
                                VoiceTranscriptionConfig.class,
                                new VoiceTranscriptionConfig(),
                                voiceTranscriptionState,
                                EVENT_VOICE_TRANSCRIPTION);
            }
            return voiceTranscriptionState.value;
        }
    }

    public void saveVoiceTranscription(VoiceTranscriptionConfig config) {
        LOGGER.info("saveVoiceTranscription starts...");
        VoiceTranscriptionConfig toSave = config == null ? new VoiceTranscriptionConfig() : config;
        synchronized (voiceTranscriptionState.lock) {
            voiceTranscriptionState.value = toSave;
            voiceTranscriptionState.lastWrittenContent =
                    writeJson(
                            paths.voiceTranscriptionFile,
                            toSave,
                            voiceTranscriptionState.lastWrittenContent);
        }
        notifyChange(EVENT_VOICE_TRANSCRIPTION);
    }

    // ------------------------- Per-agent Heartbeat -------------------------
    public HeartbeatConfig getHeartbeat(String agentId) {
        CachedState<HeartbeatConfig> state =
                heartbeatStateByAgent.computeIfAbsent(agentId, ignored -> new CachedState<>());
        synchronized (state.lock) {
            Path file = workspaceFor(agentId).resolve(WorkspacePaths.HEARTBEAT_FILE);
            registerStateFileWatcherIfNeeded(state, file, () -> reloadHeartbeatFromDisk(agentId));
            if (state.value == null) {
                state.value =
                        readObject(
                                file,
                                HeartbeatConfig.class,
                                new HeartbeatConfig(),
                                state,
                                "heartbeat");
            }
            return state.value;
        }
    }

    public void saveHeartbeat(String agentId, HeartbeatConfig config) {
        LOGGER.info("saveHeartbeat starts...");
        CachedState<HeartbeatConfig> state =
                heartbeatStateByAgent.computeIfAbsent(agentId, ignored -> new CachedState<>());
        HeartbeatConfig toSave = config == null ? new HeartbeatConfig() : config;
        Path file = workspaceFor(agentId).resolve(WorkspacePaths.HEARTBEAT_FILE);
        synchronized (state.lock) {
            state.value = toSave;
            state.lastWrittenContent = writeJson(file, toSave, state.lastWrittenContent);
        }
    }

    // ------------------------- Per-agent Agent Configuration -------------------------
    public AgentConfiguration getAgentConfiguration(String agentId) {
        LOGGER.info(" starts...");
        CachedState<AgentConfiguration> state =
                agentConfigStateByAgent.computeIfAbsent(agentId, ignored -> new CachedState<>());
        synchronized (state.lock) {
            Path file = workspaceFor(agentId).resolve(WorkspacePaths.CONFIGURATION_FILE);
            registerStateFileWatcherIfNeeded(
                    state, file, () -> reloadAgentConfigurationFromDisk(agentId));
            if (state.value == null) {
                state.value =
                        readObject(
                                file,
                                AgentConfiguration.class,
                                new AgentConfiguration(),
                                state,
                                "agent_configuration");
            }
            return state.value;
        }
    }

    public void saveAgentConfiguration(String agentId, AgentConfiguration config) {
        LOGGER.info("saveAgentConfiguration starts...");
        CachedState<AgentConfiguration> state =
                agentConfigStateByAgent.computeIfAbsent(agentId, ignored -> new CachedState<>());
        AgentConfiguration toSave = config == null ? new AgentConfiguration() : config;
        Path file = workspaceFor(agentId).resolve(WorkspacePaths.CONFIGURATION_FILE);
        synchronized (state.lock) {
            state.value = toSave;
            state.lastWrittenContent = writeJson(file, toSave, state.lastWrittenContent);
        }
    }

    // ------------------------- Watch Reload -------------------------
    private void registerGlobalWatchers() {
        registerWatchedFile(paths.providersFile, this::reloadProvidersFromDisk);
        registerWatchedFile(paths.agentsFile, this::reloadAgentsFromDisk);
        registerWatchedFile(paths.globalConfigFile, this::reloadGlobalConfigFromDisk);
        registerWatchedFile(paths.toolConfigFile, this::reloadToolsFromDisk);
        registerWatchedFile(paths.sessionsMetaFile, this::reloadSessionsFromDisk);
        registerWatchedFile(paths.tokenUsageFile, this::reloadTokenUsageFromDisk);
        registerWatchedFile(paths.agentStatsFile, this::reloadAgentStatsFromDisk);
        registerWatchedFile(paths.channelsFile, this::reloadChannelsFromDisk);
        registerWatchedFile(paths.cronJobsFile, this::reloadCronJobsFromDisk);
        registerWatchedFile(paths.projectsFile, this::reloadProjectsFromDisk);
        registerWatchedFile(paths.mcpClientsFile, this::reloadMcpClientsFromDisk);
        registerWatchedFile(paths.acpAgentsFile, this::reloadAcpAgentsFromDisk);
        registerWatchedFile(paths.envsFile, this::reloadEnvVariablesFromDisk);
        registerWatchedFile(paths.securityRulesFile, this::reloadSecurityRulesFromDisk);
        registerWatchedFile(paths.securityConfigFile, this::reloadSecuritySettingsFromDisk);
        registerWatchedFile(backupsMetaFile, this::reloadBackupsFromDisk);
        registerWatchedFile(paths.voiceTranscriptionFile, this::reloadVoiceTranscriptionFromDisk);
    }

    // Background virtual thread: responsible for listening to OS-level modification events
    /**
     * Background virtual thread: responsible for listening to OS-level modification events.
     *
     * <p>Implementation strategy:
     * <br>1) The thread blocks on watchService.take() for a long time, occupying almost no CPU when there are no events;
     * <br>2) Upon receiving an event, converts the relative path to an absolute path, then routes it to the specific reload based on the file-level callback;
     * <br>3) Performs short debouncing on the same batch of events to avoid repetitive refresh storms caused by editors "writing temp files + renaming".
     */
    private void watchFileChanges() {
        while (watcherRunning && !Thread.currentThread().isInterrupted()) {
            WatchKey watchKey;
            try {
                watchKey = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException closed) {
                // Entering this branch upon normal close() is expected behavior.
                break;
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Config watcher thread exception, continuing to the next round of"
                                + " listening",
                        e);
                continue;
            }
            Path watchDirectory = directoryByWatchKey.get(watchKey);
            if (watchDirectory == null) {
                watchKey.reset();
                continue;
            }
            Set<Path> changedFiles = new HashSet<>();
            for (WatchEvent<?> event : watchKey.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Object context = event.context();
                if (!(context instanceof Path fileName)) {
                    continue;
                }
                changedFiles.add(normalize(watchDirectory.resolve(fileName)));
            }
            // Simple debouncing: merge high-frequency events in the same batch to reduce duplicate
            // parsing and notification.
            if (!changedFiles.isEmpty()) {
                try {
                    Thread.sleep(120L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            for (Path changedFile : changedFiles) {
                LOGGER.info("changedFile==" + changedFile);
                CopyOnWriteArrayList<Runnable> callbacks = reloadCallbacksByFile.get(changedFile);
                if (callbacks == null || callbacks.isEmpty()) {
                    continue;
                }
                for (Runnable callback : callbacks) {
                    try {
                        callback.run();
                    } catch (Exception e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Failed to execute config reload callback (ignored): "
                                        + changedFile,
                                e);
                    }
                }
            }
            boolean valid = watchKey.reset();
            if (!valid) {
                Path removedDirectory = directoryByWatchKey.remove(watchKey);
                if (removedDirectory != null) {
                    watchKeyByDirectory.remove(removedDirectory);
                }
            }
        }
        LOGGER.info("ConfigManager config watcher thread has exited");
    }

    /**
     * Register file watch target.
     *
     * <p>Note: WatchService can only directly watch directories, not files.
     * Therefore, it registers "parent directory watch" here, and accurately filters to the specific file after receiving a directory event.
     */
    private void registerWatchedFile(Path file, Runnable callback) {
        if (file == null || callback == null) {
            return;
        }
        Path normalizedFile = normalize(file);
        reloadCallbacksByFile
                .computeIfAbsent(normalizedFile, ignored -> new CopyOnWriteArrayList<>())
                .add(callback);
        registerDirectoryIfNeeded(normalizedFile.getParent());
    }

    /**
     * Lazily register dynamically generated state files (such as heartbeat/configuration) by agentId.
     */
    private <T> void registerStateFileWatcherIfNeeded(
            CachedState<T> state, Path file, Runnable reloadCallback) {
        if (state.watcherRegistered) {
            return;
        }
        registerWatchedFile(file, reloadCallback);
        state.watcherRegistered = true;
    }

    /**
     * Register directory watch (idempotent).
     */
    private void registerDirectoryIfNeeded(Path directory) {
        if (directory == null) {
            return;
        }
        Path normalizedDirectory = normalize(directory);
        if (watchKeyByDirectory.containsKey(normalizedDirectory)) {
            return;
        }
        synchronized (watchKeyByDirectory) {
            if (watchKeyByDirectory.containsKey(normalizedDirectory)) {
                return;
            }
            try {
                Files.createDirectories(normalizedDirectory);
                WatchKey watchKey =
                        normalizedDirectory.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                watchKeyByDirectory.put(normalizedDirectory, watchKey);
                directoryByWatchKey.put(watchKey, normalizedDirectory);
                LOGGER.log(
                        Level.FINE,
                        "Successfully registered config directory watch: {0}",
                        normalizedDirectory);
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to register config directory watch: " + normalizedDirectory,
                        e);
            }
        }
    }

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private void reloadProvidersFromDisk() {
        reloadListState(paths.providersFile, PROVIDERS_REF, providersState, EVENT_PROVIDERS);
    }

    private void reloadAgentsFromDisk() {
        reloadListState(paths.agentsFile, AGENTS_REF, agentsState, EVENT_AGENTS);
    }

    private void reloadGlobalConfigFromDisk() {
        boolean changed = false;
        synchronized (globalConfigState.lock) {
            try {
                String content =
                        Files.exists(paths.globalConfigFile)
                                ? Files.readString(paths.globalConfigFile)
                                : "";
                if (Objects.equals(content, globalConfigState.lastWrittenContent)) {
                    return;
                }
                GlobalConfig loaded =
                        jsonStore.parse(content, GlobalConfig.class, new GlobalConfig());
                globalConfigState.value = loaded;
                globalConfigState.lastWrittenContent = content;
                changed = true;
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to hot-load global_config, retaining old memory value",
                        e);
            }
        }
        if (changed) {
            notifyChange(EVENT_GLOBAL_CONFIG);
        }
    }

    private void reloadToolsFromDisk() {
        reloadListState(paths.toolConfigFile, TOOLS_REF, toolsState, EVENT_TOOLS);
    }

    private void reloadSessionsFromDisk() {
        reloadListState(paths.sessionsMetaFile, SESSIONS_REF, sessionsState, EVENT_SESSIONS);
    }

    private void reloadTokenUsageFromDisk() {
        reloadListState(paths.tokenUsageFile, TOKEN_USAGE_REF, tokenUsageState, EVENT_TOKEN_USAGE);
    }

    private void reloadAgentStatsFromDisk() {
        reloadListState(paths.agentStatsFile, AGENT_STATS_REF, agentStatsState, EVENT_AGENT_STATS);
    }

    private void reloadChannelsFromDisk() {
        reloadListState(paths.channelsFile, CHANNELS_REF, channelsState, EVENT_CHANNELS);
    }

    private void reloadMcpClientsFromDisk() {
        reloadListState(paths.mcpClientsFile, MCP_CLIENTS_REF, mcpClientsState, EVENT_MCP_CLIENTS);
    }

    private void reloadAcpAgentsFromDisk() {
        reloadListState(paths.acpAgentsFile, ACP_AGENTS_REF, acpAgentsState, EVENT_ACP_AGENTS);
    }

    private void reloadEnvVariablesFromDisk() {
        reloadListState(paths.envsFile, ENVS_REF, envVariablesState, EVENT_ENVS);
    }

    private void reloadSecurityRulesFromDisk() {
        reloadListState(
                paths.securityRulesFile,
                SECURITY_RULES_REF,
                securityRulesState,
                EVENT_SECURITY_RULES);
    }

    private void reloadSecuritySettingsFromDisk() {
        synchronized (securitySettingsState.lock) {
            securitySettingsState.value =
                    readObject(
                            paths.securityConfigFile,
                            SecuritySettings.class,
                            new SecuritySettings(),
                            securitySettingsState,
                            EVENT_SECURITY_CONFIG);
            if (securitySettingsState.value == null) {
                securitySettingsState.value = new SecuritySettings();
            }
        }
        notifyChange(EVENT_SECURITY_CONFIG);
    }

    private void reloadBackupsFromDisk() {
        reloadListState(backupsMetaFile, BACKUPS_REF, backupsState, EVENT_BACKUPS);
    }

    private void reloadVoiceTranscriptionFromDisk() {
        boolean changed = false;
        synchronized (voiceTranscriptionState.lock) {
            try {
                String content =
                        Files.exists(paths.voiceTranscriptionFile)
                                ? Files.readString(paths.voiceTranscriptionFile)
                                : "";
                if (Objects.equals(content, voiceTranscriptionState.lastWrittenContent)) {
                    return;
                }
                VoiceTranscriptionConfig loaded =
                        jsonStore.parse(
                                content,
                                VoiceTranscriptionConfig.class,
                                new VoiceTranscriptionConfig());
                voiceTranscriptionState.value = loaded;
                voiceTranscriptionState.lastWrittenContent = content;
                changed = true;
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to hot-load voice_transcription, retaining old memory value",
                        e);
            }
        }
        if (changed) {
            notifyChange(EVENT_VOICE_TRANSCRIPTION);
        }
    }

    private void reloadHeartbeatFromDisk(String agentId) {
        CachedState<HeartbeatConfig> state = heartbeatStateByAgent.get(agentId);
        if (state == null) {
            return;
        }
        Path file = workspaceFor(agentId).resolve(WorkspacePaths.HEARTBEAT_FILE);
        synchronized (state.lock) {
            try {
                String content = Files.exists(file) ? Files.readString(file) : "";
                if (Objects.equals(content, state.lastWrittenContent)) {
                    return;
                }
                HeartbeatConfig loaded =
                        jsonStore.parse(content, HeartbeatConfig.class, new HeartbeatConfig());
                state.value = loaded;
                state.lastWrittenContent = content;
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to hot-load heartbeat, retaining old memory value: " + agentId,
                        e);
            }
        }
    }

    private void reloadAgentConfigurationFromDisk(String agentId) {
        CachedState<AgentConfiguration> state = agentConfigStateByAgent.get(agentId);
        if (state == null) {
            return;
        }
        Path file = workspaceFor(agentId).resolve(WorkspacePaths.CONFIGURATION_FILE);
        synchronized (state.lock) {
            try {
                String content = Files.exists(file) ? Files.readString(file) : "";
                if (Objects.equals(content, state.lastWrittenContent)) {
                    return;
                }
                AgentConfiguration loaded =
                        jsonStore.parse(
                                content, AgentConfiguration.class, new AgentConfiguration());
                state.value = loaded;
                state.lastWrittenContent = content;
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to hot-load agent configuration, retaining old memory value: "
                                + agentId,
                        e);
            }
        }
    }

    // ------------------------- Common IO Helpers -------------------------
    private <T> void reloadListState(
            Path file,
            TypeReference<List<T>> typeReference,
            CachedState<List<T>> state,
            String eventKey) {
        boolean changed = false;
        synchronized (state.lock) {
            try {
                String content = Files.exists(file) ? Files.readString(file) : "";
                if (Objects.equals(content, state.lastWrittenContent)) {
                    return;
                }
                List<T> loaded = jsonStore.parseList(content, typeReference);
                state.value = loaded;
                state.lastWrittenContent = content;
                changed = true;
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to hot-load file, retaining old memory value: "
                                + file.getFileName(),
                        e);
            }
        }
        if (changed) {
            notifyChange(eventKey);
        }
    }

    private <T> List<T> readList(
            Path file, TypeReference<List<T>> typeReference, CachedState<List<T>> state) {
        JsonStore.ReadResult<List<T>> result = jsonStore.readListWithContent(file, typeReference);
        state.lastWrittenContent = result.content();
        return result.value();
    }

    private <T> T readObject(
            Path file, Class<T> type, T fallback, CachedState<T> state, String configName) {
        JsonStore.ReadResult<T> result = jsonStore.readWithContent(file, type, fallback);
        state.lastWrittenContent = result.content();
        return result.value();
    }

    private String writeJson(Path file, Object value) {
        return jsonStore.writeAtomic(file, value);
    }

    private String writeJson(Path file, Object value, String lastWrittenContent) {
        return jsonStore.writeIfChanged(file, value, lastWrittenContent);
    }

    private Path workspaceFor(String agentId) {
        return paths.workspaceRoot.resolve(agentId);
    }

    private void notifyChange(String configKey) {
        CopyOnWriteArrayList<Runnable> listeners = listenersByKey.get(configKey);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to execute config change listener (ignored): " + configKey,
                        e);
            }
        }
    }

    /**
     * Memory state container for a single config item.
     *
     * <p>Each config item holds an independent lock to avoid global big lock causing unrelated configs to block each other.
     */
    private static final class CachedState<T> {

        private final Object lock = new Object();

        private T value;

        private String lastWrittenContent = "";

        private boolean watcherRegistered = false;
    }

    /**
     * Resource close entry.
     *
     * <p>Needs to close WatchService to wake up and terminate the background watcher thread.
     */
    public void close() {
        watcherRunning = false;
        try {
            watchService.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to close WatchService (ignored)", e);
        }
    }
}
