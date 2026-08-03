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
 *
 * <p>Main functions:
 * <ul>
 *   <li>Register subagent entries to enable cross-session recovery</li>
 *   <li>Look up subagent entries to support cross-replica routing</li>
 *   <li>Remove subagent entries to clean up completed tasks</li>
 * </ul>
 */
public class SpawnRegistryService {
    private static final Logger LOGGER = Logger.getLogger(SpawnRegistryService.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<HashMap<String, SpawnEntry>> MAP_REF =
            new TypeReference<HashMap<String, SpawnEntry>>() {};

    /** Subagent registry, key is the agent key (e.g., "agent:general-purpose:uuid"), value is subagent metadata. */
    private final ConcurrentHashMap<String, SpawnEntry> spawnRegistry = new ConcurrentHashMap<>();

    /** Workspace path, used to persist subagent metadata. */
    private final Path workspacePath;

    /** Persistent file path. */
    private final Path registryFile;

    /**
     * Create subagent registry service.
     *
     * @param workspacePath Workspace path
     */
    public SpawnRegistryService(Path workspacePath) {
        this.workspacePath = workspacePath;
        this.registryFile = workspacePath.resolve("spawn-registry.json");
        loadFromDisk();
        LOGGER.log(
                Level.INFO, "Subagent registry service initialized, workspace: {0}", workspacePath);
    }

    /**
     * Register subagent entry.
     *
     * <p>Subagent entries are persisted to support cross-replica routing and session recovery.
     *
     * @param key     Agent key (e.g., "agent:general-purpose:uuid")
     * @param entry   Subagent metadata
     */
    public void registerSpawnEntry(String key, SpawnEntry entry) {
        if (key == null || entry == null) {
            LOGGER.log(Level.WARNING, "Failed to register subagent entry: key or entry is null");
            return;
        }

        LOGGER.log(
                Level.INFO,
                "Register subagent entry: key={0}, agentId={1}, sessionId={2}",
                new Object[] {key, entry.agentId(), entry.sessionId()});

        spawnRegistry.put(key, entry);

        // Persist to file
        persistRegistry();
    }

    /**
     * Look up subagent entry.
     *
     * @param key Agent key
     * @return Subagent metadata, or null if it does not exist
     */
    public SpawnEntry findSpawnEntry(String key) {
        if (key == null) {
            return null;
        }

        SpawnEntry entry = spawnRegistry.get(key);
        if (entry != null) {
            LOGGER.log(Level.FINE, "Found subagent entry: key={0}", key);
        } else {
            LOGGER.log(Level.FINE, "Subagent entry not found: key={0}", key);
        }

        return entry;
    }

    /**
     * Remove subagent entry.
     *
     * @param key Agent key
     */
    public void removeSpawnEntry(String key) {
        if (key == null) {
            return;
        }

        LOGGER.log(Level.INFO, "Remove subagent entry: key={0}", key);
        spawnRegistry.remove(key);

        // Persist to file
        persistRegistry();
    }

    /**
     * Get all subagent entries.
     *
     * @return All subagent entries
     */
    public Map<String, SpawnEntry> getAllSpawnEntries() {
        return Map.copyOf(spawnRegistry);
    }

    /**
     * Persist registry to file.
     */
    private void persistRegistry() {
        try {
            Files.createDirectories(registryFile.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(registryFile.toFile(), spawnRegistry);
            LOGGER.log(
                    Level.FINE,
                    "Subagent registry persisted, current number of entries: {0}",
                    spawnRegistry.size());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to persist subagent registry", e);
        }
    }

    /**
     * Load registry from file.
     */
    private void loadFromDisk() {
        try {
            if (Files.isRegularFile(registryFile)) {
                HashMap<String, SpawnEntry> loaded = JSON.readValue(registryFile.toFile(), MAP_REF);
                if (loaded != null) {
                    spawnRegistry.putAll(loaded);
                    LOGGER.log(
                            Level.INFO,
                            "Load subagent registry from file: {0}, number of entries: {1}",
                            new Object[] {registryFile, loaded.size()});
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load subagent registry (ignored)", e);
        }
    }

    /**
     * Subagent metadata record.
     *
     * <p>Contains critical information of the subagent, supporting cross-replica routing and session recovery.
     *
     * @param key       Agent key
     * @param agentId   Agent ID
     * @param sessionId Session ID
     * @param label     Label
     * @param depth     Depth
     */
    public record SpawnEntry(
            String key, String agentId, String sessionId, String label, int depth) {

        /**
         * Create subagent metadata.
         *
         * @param key       Agent key
         * @param agentId   Agent ID
         * @param sessionId Session ID
         * @param label     Label
         * @param depth     Depth
         */
        public SpawnEntry {}
    }
}
