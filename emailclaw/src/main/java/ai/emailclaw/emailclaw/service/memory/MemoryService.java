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
package ai.emailclaw.emailclaw.service.memory;

import ai.emailclaw.emailclaw.storage.WorkspacePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.ObjectMapper;

/**
 * Memory service - provides a CRUD wrapper for structured memory.
 *
 * <p>Stores key-value memory entries in the workspace/{agentId}/memory/ directory,
 * with each memory entry as a separate JSON file.
 *
 * <p>Under the hood, it reuses a JSON file storage pattern similar to JsonFilePlanStore.
 */
public class MemoryService {
    private static final Logger LOGGER = Logger.getLogger(MemoryService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROACTIVE_PREFIX = "proactive_";

    private final Path workspaceRoot;

    public MemoryService(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        LOGGER.info("MemoryService initialization completed");
    }

    private Path memoryDir(String agentId) {
        return workspaceRoot.resolve(agentId).resolve(WorkspacePaths.MEMORY_DIR);
    }

    private Path noteFile(String agentId, String key) {
        return memoryDir(agentId).resolve(key + ".json");
    }

    /**
     * Save structured memory entry.
     *
     * @param agentId The agent ID
     * @param key     The memory key (usually semantic names like user_preferences, project_context)
     * @param content The memory content (any JSON serializable object)
     */
    public void saveMemoryNote(String agentId, String key, Object content) {
        try {
            Path dir = memoryDir(agentId);
            Files.createDirectories(dir);
            MAPPER.writeValue(noteFile(agentId, key).toFile(), content);
            LOGGER.log(Level.FINE, "Memory saved: agent={0}, key={1}", new Object[] {agentId, key});
        } catch (IOException e) {
            LOGGER.log(
                    Level.WARNING, "Failed to save memory: agent=" + agentId + ", key=" + key, e);
        }
    }

    /**
     * Read structured memory entry.
     *
     * @param agentId The agent ID
     * @param key     The memory key
     * @param type    The target type for deserialization
     * @return The memory content (might be empty)
     */
    public <T> Optional<T> readMemoryNote(String agentId, String key, Class<T> type) {
        Path file = noteFile(agentId, key);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.toFile(), type));
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "Failed to read memory: agent=" + agentId + ", key=" + key, e);
            return Optional.empty();
        }
    }

    /**
     * List all memory keys for the specified agent.
     */
    public List<String> listMemoryNotes(String agentId) {
        Path dir = memoryDir(agentId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var files = Files.list(dir)) {
            List<String> keys =
                    files.filter(p -> p.toString().endsWith(".json"))
                            .map(p -> p.getFileName().toString().replace(".json", ""))
                            .toList();
            return keys;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to list memory: agent=" + agentId, e);
            return List.of();
        }
    }

    /**
     * Delete the specified memory entry.
     */
    public void deleteMemoryNote(String agentId, String key) {
        try {
            Files.deleteIfExists(noteFile(agentId, key));
        } catch (IOException e) {
            LOGGER.log(
                    Level.WARNING, "Failed to delete memory: agent=" + agentId + ", key=" + key, e);
        }
    }

    /**
     * List memory entry keys marked as "proactive".
     */
    public List<String> listProactiveKeys(String agentId) {
        return listMemoryNotes(agentId).stream()
                .filter(k -> k.startsWith(PROACTIVE_PREFIX))
                .toList();
    }
}
