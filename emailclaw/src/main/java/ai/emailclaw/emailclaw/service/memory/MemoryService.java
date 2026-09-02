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

import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.storage.WorkspacePaths;
import ai.emailclaw.emailclaw.util.FileNameUtils;
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
 */
public class MemoryService {
    private static final Logger LOGGER = Logger.getLogger(MemoryService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROACTIVE_PREFIX = "proactive_";

    private final Path globalWorkspaceRoot;
    private final ProjectService projectService;

    public MemoryService(Path globalWorkspaceRoot, ProjectService projectService) {
        this.globalWorkspaceRoot = globalWorkspaceRoot;
        this.projectService = projectService;
        LOGGER.info("MemoryService initialization completed");
    }

    private Path memoryDir(String agentId, MemoryScope scope, String projectId) {
        Path baseDir;
        if (scope == MemoryScope.GLOBAL) {
            baseDir = globalWorkspaceRoot;
        } else {
            ai.emailclaw.emailclaw.model.ProjectInfo project = projectService.findById(projectId);
            String baseDirStr = project != null ? project.getBaseDirectory() : null;
            Path base =
                    (baseDirStr != null && !baseDirStr.isBlank())
                            ? Path.of(FileNameUtils.expandUserHome(baseDirStr))
                            : AppHomeConstants.HOME_RESOLVED
                                    .resolve(AppHomeConstants.PROJECTS_DIR)
                                    .resolve(projectId != null ? projectId : "default");
            baseDir = base.resolve(AppHomeConstants.AGENT_WORKSPACE_DIR);
        }
        return baseDir.resolve(agentId).resolve(WorkspacePaths.MEMORY_DIR);
    }

    private Path noteFile(String agentId, String key, MemoryScope scope, String projectId) {
        return memoryDir(agentId, scope, projectId).resolve(key + ".json");
    }

    public void saveMemoryNote(
            String agentId, String key, Object content, MemoryScope scope, String projectId) {
        try {
            Path dir = memoryDir(agentId, scope, projectId);
            Files.createDirectories(dir);
            MAPPER.writeValue(noteFile(agentId, key, scope, projectId).toFile(), content);
            LOGGER.log(
                    Level.FINE,
                    "Memory saved: agent={0}, key={1}, scope={2}, project={3}",
                    new Object[] {agentId, key, scope, projectId});
        } catch (IOException e) {
            LOGGER.log(
                    Level.WARNING, "Failed to save memory: agent=" + agentId + ", key=" + key, e);
        }
    }

    public <T> Optional<T> readMemoryNote(
            String agentId, String key, Class<T> type, MemoryScope scope, String projectId) {
        Path file = noteFile(agentId, key, scope, projectId);
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

    public List<String> listMemoryNotes(String agentId, MemoryScope scope, String projectId) {
        Path dir = memoryDir(agentId, scope, projectId);
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

    public void deleteMemoryNote(String agentId, String key, MemoryScope scope, String projectId) {
        try {
            Files.deleteIfExists(noteFile(agentId, key, scope, projectId));
        } catch (IOException e) {
            LOGGER.log(
                    Level.WARNING, "Failed to delete memory: agent=" + agentId + ", key=" + key, e);
        }
    }

    /**
     * List memory entry keys marked as "proactive".
     */
    public List<String> listProactiveKeys(String agentId, MemoryScope scope, String projectId) {
        return listMemoryNotes(agentId, scope, projectId).stream()
                .filter(k -> k.startsWith(PROACTIVE_PREFIX))
                .toList();
    }
}
