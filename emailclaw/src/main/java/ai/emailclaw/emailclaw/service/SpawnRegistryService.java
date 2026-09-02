/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 */
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
        String baseDirStr = project != null ? project.getBaseDirectory() : null;
        Path base =
                (baseDirStr != null && !baseDirStr.isBlank())
                        ? Path.of(FileNameUtils.expandUserHome(baseDirStr))
                        : AppHomeConstants.HOME_RESOLVED
                                .resolve(AppHomeConstants.PROJECTS_DIR)
                                .resolve(projectId != null ? projectId : "default");
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

    public record SpawnEntry(
            String key, String agentId, String sessionId, String label, int depth) {
        public SpawnEntry {}
    }
}
