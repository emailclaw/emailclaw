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
import ai.emailclaw.emailclaw.storage.sqlite.SqliteMemoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.ObjectMapper;

/**
 * Memory service - provides a CRUD wrapper for structured memory, backed by SQLite.
 */
public class MemoryService {
    private static final Logger LOGGER = Logger.getLogger(MemoryService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SqliteMemoryRepository memoryRepository;
    private final ProjectService projectService;

    public MemoryService(SqliteMemoryRepository memoryRepository, ProjectService projectService) {
        this.memoryRepository = memoryRepository;
        this.projectService = projectService;
        LOGGER.info("MemoryService initialization completed with SQLite backend");
    }

    public void saveMemoryNote(
            String agentId, String key, Object content, MemoryScope scope, String projectId) {
        try {
            String jsonStr;
            if (content instanceof String s) {
                jsonStr = s;
            } else {
                jsonStr = MAPPER.writeValueAsString(content);
            }
            memoryRepository.saveMemoryNote(agentId, key, jsonStr, scope, projectId);
            LOGGER.log(
                    Level.FINE,
                    "Memory saved in SQLite: agent={0}, key={1}, scope={2}, project={3}",
                    new Object[] {agentId, key, scope, projectId});
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "Failed to save memory: agent=" + agentId + ", key=" + key, e);
        }
    }

    public <T> Optional<T> readMemoryNote(
            String agentId, String key, Class<T> type, MemoryScope scope, String projectId) {
        Optional<String> contentOpt =
                memoryRepository.readMemoryNote(agentId, key, scope, projectId);
        if (contentOpt.isEmpty()) {
            return Optional.empty();
        }
        String content = contentOpt.get();
        if (type == String.class) {
            @SuppressWarnings("unchecked")
            T cast = (T) content;
            return Optional.of(cast);
        }
        try {
            return Optional.of(MAPPER.readValue(content, type));
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "Failed to read memory: agent=" + agentId + ", key=" + key, e);
            return Optional.empty();
        }
    }

    public List<String> listMemoryNotes(String agentId, MemoryScope scope, String projectId) {
        return memoryRepository.listMemoryNotes(agentId, scope, projectId);
    }

    public void deleteMemoryNote(String agentId, String key, MemoryScope scope, String projectId) {
        memoryRepository.deleteMemoryNote(agentId, key, scope, projectId);
    }

    /**
     * List memory entry keys marked as "proactive".
     */
    public List<String> listProactiveKeys(String agentId, MemoryScope scope, String projectId) {
        return memoryRepository.listProactiveKeys(agentId, scope, projectId);
    }
}
