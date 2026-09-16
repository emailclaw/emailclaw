/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 */
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Subagent registry service.
 *
 * <p>Manages subagent metadata, supporting cross-replica routing and session recovery.
 */
public class SpawnRegistryService {
    private static final Logger LOGGER = Logger.getLogger(SpawnRegistryService.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<HashMap<String, SpawnEntry>> MAP_REF =
            new TypeReference<HashMap<String, SpawnEntry>>() {};

    private final ai.emailclaw.emailclaw.service.ProjectService projectService;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SpawnEntry>>
            projectRegistries = new ConcurrentHashMap<>();

    public SpawnRegistryService(ai.emailclaw.emailclaw.service.ProjectService projectService) {
        this.projectService = projectService;
        LOGGER.log(Level.INFO, "Subagent registry service initialized");
    }

    private Path registryFile(String projectId) {
        ai.emailclaw.emailclaw.model.ProjectInfo project = projectService.findById(projectId);
        String baseDirStr = project.getBaseDirectory();
        Path base = Path.of(FileNameUtils.expandUserHome(baseDirStr));
        return base.resolve(AppHomeConstants.AGENT_WORKSPACE_DIR).resolve("spawn-registry.json");
    }

    private ConcurrentHashMap<String, SpawnEntry> getRegistry(String projectId) {
        return projectRegistries.computeIfAbsent(
                projectId,
                id -> {
                    ConcurrentHashMap<String, SpawnEntry> reg = new ConcurrentHashMap<>();
                    Path file = registryFile(id);
                    if (Files.exists(file)) {
                        try {
                            Map<String, SpawnEntry> map = JSON.readValue(file.toFile(), MAP_REF);
                            if (map != null) {
                                reg.putAll(map);
                            }
                        } catch (Exception e) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Failed to load subagent registry: file=" + file,
                                    e);
                        }
                    }
                    return reg;
                });
    }

    private void saveToDisk(String projectId) {
        Path file = registryFile(projectId);
        try {
            Files.createDirectories(file.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), getRegistry(projectId));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to persist subagent registry: file=" + file, e);
        }
    }

    public void registerSpawnEntry(String projectId, String key, SpawnEntry entry) {
        if (key == null || entry == null) return;
        LOGGER.log(Level.INFO, "Register subagent entry: key={0}", key);
        getRegistry(projectId).put(key, entry);
        saveToDisk(projectId);
    }

    public void updateStatus(String projectId, String key, String status) {
        if (key == null || status == null) return;
        ConcurrentHashMap<String, SpawnEntry> reg = getRegistry(projectId);
        SpawnEntry existing = reg.get(key);
        if (existing != null) {
            reg.put(key, existing.withStatus(status));
            saveToDisk(projectId);
            LOGGER.log(
                    Level.FINE,
                    "Updated subagent entry status: key={0}, status={1}",
                    new Object[] {key, status});
        }
    }

    public List<SpawnEntry> findByParentSessionId(String projectId, String parentSessionId) {
        if (parentSessionId == null) return List.of();
        return getRegistry(projectId).values().stream()
                .filter(e -> parentSessionId.equals(e.parentSessionId()))
                .toList();
    }

    public SpawnEntry findSpawnEntry(String projectId, String key) {
        if (key == null) return null;
        return getRegistry(projectId).get(key);
    }

    public void removeSpawnEntry(String projectId, String key) {
        if (key == null) return;
        LOGGER.log(Level.INFO, "Remove subagent entry: key={0}", key);
        getRegistry(projectId).remove(key);
        saveToDisk(projectId);
    }

    public Map<String, SpawnEntry> getAllSpawnEntries(String projectId) {
        return Map.copyOf(getRegistry(projectId));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpawnEntry(
            String key,
            String agentId,
            String sessionId,
            String label,
            int depth,
            String parentSessionId,
            String status,
            long createdAtEpochMs,
            long updatedAtEpochMs) {

        public SpawnEntry {
            key = key == null ? "" : key;
            agentId = agentId == null ? "" : agentId;
            sessionId = sessionId == null ? "" : sessionId;
            label = label == null ? "" : label;
            parentSessionId = parentSessionId == null ? "" : parentSessionId;
            status = status == null || status.isBlank() ? "RUNNING" : status;
            if (createdAtEpochMs <= 0) {
                createdAtEpochMs = System.currentTimeMillis();
            }
            if (updatedAtEpochMs <= 0) {
                updatedAtEpochMs = createdAtEpochMs;
            }
        }

        public SpawnEntry(String key, String agentId, String sessionId, String label, int depth) {
            this(
                    key,
                    agentId,
                    sessionId,
                    label,
                    depth,
                    "",
                    "RUNNING",
                    System.currentTimeMillis(),
                    System.currentTimeMillis());
        }

        public SpawnEntry(
                String key,
                String agentId,
                String sessionId,
                String label,
                int depth,
                String parentSessionId) {
            this(
                    key,
                    agentId,
                    sessionId,
                    label,
                    depth,
                    parentSessionId,
                    "RUNNING",
                    System.currentTimeMillis(),
                    System.currentTimeMillis());
        }

        public SpawnEntry withStatus(String newStatus) {
            return new SpawnEntry(
                    this.key,
                    this.agentId,
                    this.sessionId,
                    this.label,
                    this.depth,
                    this.parentSessionId,
                    newStatus,
                    this.createdAtEpochMs,
                    System.currentTimeMillis());
        }
    }
}
