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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.AgentIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentRuntimeStatus;
import ai.emailclaw.emailclaw.model.AgentStatus;
import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import ai.emailclaw.emailclaw.util.UuidUtils;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core agent service, used to manage and distribute all agent instances.
 *
 * <p>Note: The `agents` collection in the old version is now deprecated. The persistent state of the agents is now completely
 * maintained by {@code ConfigManager}. This class mainly acts as a high-level service entry point, delegating the CRUD of agents
 * to the underlying {@code AppContext} and {@code ConfigManager}.
 */
public class AgentService {
    private static final Logger LOGGER = Logger.getLogger(AgentService.class.getName());

    private final AppContext repository;
    private final ConfigManager configManager;
    private final Object agentsLock = new Object();

    /**
     * Runtime status is ephemeral, not stored in the configuration file.
     */
    private final Map<String, AtomicInteger> runningTasks = new HashMap<>();

    private final Map<String, String> lastRunAt = new HashMap<>();
    private final Map<String, String> lastFinishAt = new HashMap<>();

    public AgentService(AppContext repository) {
        this.repository = repository;
        this.configManager = repository.configManager();
        // Note: The original list object in context.agents() should not be modified directly
        // (although usually allowed, it easily causes concurrency issues).
        // The safe practice is to overwrite and save the new configuration by create() or calling
        // configManager directly.
        // Update field logic is provided here.
        syncRuntimeIndexes(configManager.getAgents());
        this.configManager.addChangeListener(
                ConfigManager.EVENT_AGENTS, this::onAgentsConfigChanged);
        LOGGER.info(
                "AgentService initialized, currently loaded "
                        + configManager.getAgents().size()
                        + " agent configurations");
    }

    public List<AgentInfo> list() {
        return configManager.getAgents();
    }

    public Optional<AgentInfo> findById(String id) {
        return configManager.getAgents().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst();
    }

    /**
     * Resolve the current default agent.
     *
     * <p>Selection strategy (persistent and recoverable):
     * <br>1) Prioritize the currentAgentId from global-config.json;
     * <br>2) Fall back to id=default if not found;
     * <br>3) If still unavailable, select based on deterministic rules (default first, then lexicographical order of IDs);
     * <br>4) Write back to global-config immediately upon fallback to ensure consistency for the next launch.
     */
    public AgentInfo currentDefault() {
        synchronized (agentsLock) {
            List<AgentInfo> agents = configManager.getAgents();
            if (agents.isEmpty()) {
                throw new IllegalStateException(
                        "Agent list is empty, cannot resolve currentDefault");
            }

            GlobalConfig globalConfig = configManager.getGlobalConfig();
            if (globalConfig.getCurrentAgentId() != null
                    && !globalConfig.getCurrentAgentId().isBlank()) {
                AgentInfo matched =
                        agents.stream()
                                .filter(
                                        item ->
                                                item.getId()
                                                        .equals(globalConfig.getCurrentAgentId()))
                                .findFirst()
                                .orElse(null);
                if (matched != null) {
                    return matched;
                }
            }

            AgentInfo fallback = resolveFallbackAgent(agents);
            persistCurrentAgentIdLocked(globalConfig, fallback.getId());
            return fallback;
        }
    }

    /**
     * Update and persist the current agent selection.
     *
     * <p>Callers (e.g., MainWindow) should invoke this immediately after the user switches the agent,
     * ensuring that the next launch will consistently recover to the same agent.
     */
    public void setCurrentAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        synchronized (agentsLock) {
            boolean exists =
                    configManager.getAgents().stream()
                            .anyMatch(item -> item.getId().equals(agentId));
            if (!exists) {
                LOGGER.log(
                        Level.WARNING, "Ignored non-existent currentAgentId write: {0}", agentId);
                return;
            }
            GlobalConfig globalConfig = configManager.getGlobalConfig();
            persistCurrentAgentIdLocked(globalConfig, agentId);
        }
    }

    public AgentInfo create(
            String id,
            String name,
            String description,
            String providerId,
            String modelId,
            List<String> skills) {
        AgentInfo agent = new AgentInfo();
        agent.setId((id == null || id.isBlank()) ? autoId(name) : id);
        agent.setName(name);
        agent.setDescription(description);
        agent.setProviderId(providerId == null ? "" : providerId);
        agent.setModelId(modelId == null ? "" : modelId);
        agent.setWorkspacePath(repository.workspaceFor(agent.getId()).toString());
        agent.getSkillNames().addAll(skills == null ? List.of() : skills);

        synchronized (agentsLock) {
            List<AgentInfo> agents = configManager.getAgents();
            agents.add(agent);
            runningTasks.putIfAbsent(agent.getId(), new AtomicInteger(0));
            configManager.saveAgents(agents);
        }
        LOGGER.log(
                Level.INFO,
                "Created Agent: id={0}, name={1}",
                new Object[] {agent.getId(), agent.getName()});
        return agent;
    }

    public void remove(AgentInfo agent) {
        if (AgentIds.DEFAULT.equals(agent.getId())) {
            LOGGER.warning("Ignore request to delete default agent");
            return;
        }
        synchronized (agentsLock) {
            List<AgentInfo> agents = configManager.getAgents();
            agents.removeIf(item -> item.getId().equals(agent.getId()));
            runningTasks.remove(agent.getId());
            lastRunAt.remove(agent.getId());
            lastFinishAt.remove(agent.getId());
            configManager.saveAgents(agents);

            // If the currently selected Agent is deleted, immediately fall back and persist the new
            // currentAgentId.
            GlobalConfig globalConfig = configManager.getGlobalConfig();
            if (agent.getId().equals(globalConfig.getCurrentAgentId())) {
                String fallbackId = resolveFallbackAgentId(agents);
                persistCurrentAgentIdLocked(globalConfig, fallbackId);
            }
        }
        LOGGER.log(Level.INFO, "Deleted Agent: id={0}", agent.getId());
    }

    public void markTaskStarted(String agentId) {
        synchronized (agentsLock) {
            runningTasks
                    .computeIfAbsent(agentId, ignored -> new AtomicInteger(0))
                    .incrementAndGet();
            lastRunAt.put(agentId, LocalDateTime.now().toString());
        }
    }

    public void markTaskFinished(String agentId) {
        synchronized (agentsLock) {
            AtomicInteger count =
                    runningTasks.computeIfAbsent(agentId, ignored -> new AtomicInteger(0));
            count.updateAndGet(value -> Math.max(0, value - 1));
            lastFinishAt.put(agentId, LocalDateTime.now().toString());
        }
    }

    public AgentRuntimeStatus statusOf(String agentId) {
        AgentInfo agent = findById(agentId).orElse(null);
        if (agent == null) {
            return new AgentRuntimeStatus(AgentStatus.DISABLED, 0, "", "");
        }
        int count;
        String runAt;
        String finishAt;
        synchronized (agentsLock) {
            count = runningTasks.computeIfAbsent(agentId, ignored -> new AtomicInteger(0)).get();
            runAt = lastRunAt.getOrDefault(agentId, "");
            finishAt = lastFinishAt.getOrDefault(agentId, "");
        }
        if (!agent.isEnabled()) {
            return new AgentRuntimeStatus(AgentStatus.DISABLED, 0, runAt, finishAt);
        }
        AgentStatus status = count > 0 ? AgentStatus.RUNNING : AgentStatus.IDLE;
        return new AgentRuntimeStatus(status, count, runAt, finishAt);
    }

    public void save() {
        synchronized (agentsLock) {
            configManager.saveAgents(configManager.getAgents());
        }
    }

    public Path workspacePath(String agentId) {
        return repository.workspaceFor(agentId);
    }

    private String autoId(String name) {
        String sanitized =
                (name == null ? "agent" : name.toLowerCase().replaceAll("[^a-z0-9_-]", "-"));
        sanitized = sanitized.replaceAll("-{2,}", "-");
        if (sanitized.isBlank()) {
            sanitized = "agent";
        }
        String id = sanitized;
        while (findById(id).isPresent()) {
            id = sanitized + "-" + UuidUtils.randomUUIDv7().toString().substring(30);
        }
        return id;
    }

    private void onAgentsConfigChanged() {
        try {
            syncRuntimeIndexes(configManager.getAgents());
            // After external modification of the agents list, verify currentAgentId is still valid,
            // fall back automatically if invalid.
            synchronized (agentsLock) {
                List<AgentInfo> agents = configManager.getAgents();
                if (agents.isEmpty()) {
                    return;
                }
                GlobalConfig globalConfig = configManager.getGlobalConfig();
                boolean exists =
                        globalConfig.getCurrentAgentId() != null
                                && !globalConfig.getCurrentAgentId().isBlank()
                                && agents.stream()
                                        .anyMatch(
                                                item ->
                                                        item.getId()
                                                                .equals(
                                                                        globalConfig
                                                                                .getCurrentAgentId()));
                if (!exists) {
                    String fallbackId = resolveFallbackAgentId(agents);
                    persistCurrentAgentIdLocked(globalConfig, fallbackId);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to synchronize Agent runtime index (ignored)", e);
        }
    }

    /**
     * Runtime index alignment:
     * <br>1) Pad counters when adding new agent;
     * <br>2) Clean orphan keys when deleting agent to avoid memory bloat.
     */
    private void syncRuntimeIndexes(List<AgentInfo> agents) {
        synchronized (agentsLock) {
            Set<String> activeIds = new HashSet<>();
            for (AgentInfo agent : agents) {
                activeIds.add(agent.getId());
                runningTasks.computeIfAbsent(agent.getId(), ignored -> new AtomicInteger(0));
            }
            runningTasks.keySet().removeIf(id -> !activeIds.contains(id));
            lastRunAt.keySet().removeIf(id -> !activeIds.contains(id));
            lastFinishAt.keySet().removeIf(id -> !activeIds.contains(id));
        }
    }

    /**
     * Resolve fallback Agent ID based on current list.
     */
    private String resolveFallbackAgentId(List<AgentInfo> agents) {
        AgentInfo fallback = resolveFallbackAgent(agents);
        return fallback == null ? "" : fallback.getId();
    }

    /**
     * Select a stable and predictable fallback target from the current Agent list.
     *
     * <p>Selection rules:
     * <br>1) Return id=default with priority;
     * <br>2) If default does not exist, select minimum value by id lexicographical order (deterministic result);
     * <br>3) If all ids are empty, finally fall back to the first element in list (extreme fallback).
     */
    private AgentInfo resolveFallbackAgent(List<AgentInfo> agents) {
        if (agents == null || agents.isEmpty()) {
            return null;
        }
        AgentInfo defaultAgent =
                agents.stream()
                        .filter(item -> AgentIds.DEFAULT.equals(item.getId()))
                        .findFirst()
                        .orElse(null);
        if (defaultAgent != null) {
            return defaultAgent;
        }
        AgentInfo stableFallback =
                agents.stream()
                        .filter(item -> item.getId() != null && !item.getId().isBlank())
                        .min(Comparator.comparing(item -> item.getId()))
                        .orElse(null);
        if (stableFallback != null) {
            return stableFallback;
        }
        // Extreme scenario: all Agent ids are empty, at least return an available object to avoid
        // null pointer.
        return agents.get(0);
    }

    /**
     * Write currentAgentId in locked context to avoid interleaving with concurrent modifications.
     */
    private void persistCurrentAgentIdLocked(GlobalConfig globalConfig, String agentId) {
        if (globalConfig == null || agentId == null || agentId.isBlank()) {
            return;
        }
        if (agentId.equals(globalConfig.getCurrentAgentId())) {
            return;
        }
        globalConfig.setCurrentAgentId(agentId);
        configManager.saveGlobalConfig(globalConfig);
        LOGGER.log(Level.FINE, "Persisted currentAgentId: {0}", agentId);
    }
}
