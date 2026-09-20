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
package ai.emailclaw.emailclaw.storage.sqlite;

import ai.emailclaw.emailclaw.service.memory.MemoryScope;
import ai.emailclaw.emailclaw.util.DateTimeUtils;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite implementation for structured memory notes, replacing disk-based memory/*.json.
 */
public class SqliteMemoryRepository {

    private static final Logger LOGGER = Logger.getLogger(SqliteMemoryRepository.class.getName());

    private final DatabaseManager databaseManager;

    public SqliteMemoryRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.databaseManager = databaseManager;
        LOGGER.info("SqliteMemoryRepository initialized");
    }

    private String normalizeProject(MemoryScope scope, String projectId) {
        if (scope == MemoryScope.GLOBAL || projectId == null || projectId.isBlank()) {
            return "GLOBAL";
        }
        return projectId;
    }

    /**
     * Saves or updates a memory note in SQLite.
     *
     * @param agentId agent identifier
     * @param key unique note key
     * @param content serialized content
     * @param scope memory scope (GLOBAL or PROJECT)
     * @param projectId affiliated project ID
     */
    public void saveMemoryNote(
            String agentId, String key, String content, MemoryScope scope, String projectId) {
        if (agentId == null || agentId.isBlank() || key == null || key.isBlank()) {
            return;
        }
        String pId = normalizeProject(scope, projectId);
        long now = DateTimeUtils.currentTimeMillis();
        String sql =
                """
                INSERT INTO memory_notes (agent_id, project_id, scope, key, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(agent_id, project_id, scope, key) DO UPDATE SET
                    content = excluded.content,
                    updated_at = excluded.updated_at;
                """;
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, agentId);
                        ps.setString(2, pId);
                        ps.setString(3, scope.name());
                        ps.setString(4, key);
                        ps.setString(5, content != null ? content : "");
                        ps.setLong(6, now);
                        ps.setLong(7, now);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.FINE,
                "Saved memory note: agent={0}, key={1}, scope={2}",
                new Object[] {agentId, key, scope});
    }

    /**
     * Reads a memory note content by key.
     *
     * @param agentId agent identifier
     * @param key unique note key
     * @param scope memory scope
     * @param projectId affiliated project ID
     * @return Optional string containing note content
     */
    public Optional<String> readMemoryNote(
            String agentId, String key, MemoryScope scope, String projectId) {
        if (agentId == null || agentId.isBlank() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        String pId = normalizeProject(scope, projectId);
        String sql =
                """
                SELECT content
                FROM memory_notes
                WHERE agent_id = ? AND project_id = ? AND scope = ? AND key = ?
                """;
        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, agentId);
                        ps.setString(2, pId);
                        ps.setString(3, scope.name());
                        ps.setString(4, key);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return Optional.ofNullable(rs.getString("content"));
                            }
                        }
                    }
                    return Optional.empty();
                });
    }

    /**
     * Lists memory note keys sorted by updated_at descending (recency-first).
     *
     * @param agentId agent identifier
     * @param scope memory scope
     * @param projectId affiliated project ID
     * @return list of note keys
     */
    public List<String> listMemoryNotes(String agentId, MemoryScope scope, String projectId) {
        if (agentId == null || agentId.isBlank()) {
            return new ArrayList<>();
        }
        String pId = normalizeProject(scope, projectId);
        String sql =
                """
                SELECT key
                FROM memory_notes
                WHERE agent_id = ? AND project_id = ? AND scope = ?
                ORDER BY updated_at DESC
                """;
        return databaseManager.executeQuery(
                conn -> {
                    List<String> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, agentId);
                        ps.setString(2, pId);
                        ps.setString(3, scope.name());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(rs.getString("key"));
                            }
                        }
                    }
                    return list;
                });
    }

    /**
     * Deletes a memory note.
     *
     * @param agentId agent identifier
     * @param key unique note key
     * @param scope memory scope
     * @param projectId affiliated project ID
     */
    public void deleteMemoryNote(String agentId, String key, MemoryScope scope, String projectId) {
        if (agentId == null || agentId.isBlank() || key == null || key.isBlank()) {
            return;
        }
        String pId = normalizeProject(scope, projectId);
        String sql =
                "DELETE FROM memory_notes WHERE agent_id = ? AND project_id = ? AND scope = ? AND"
                        + " key = ?";
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, agentId);
                        ps.setString(2, pId);
                        ps.setString(3, scope.name());
                        ps.setString(4, key);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.INFO,
                "Deleted memory note: agent={0}, key={1}, scope={2}",
                new Object[] {agentId, key, scope});
    }

    /**
     * Lists proactive note keys (prefixed with proactive_) ordered by recency.
     *
     * @param agentId agent identifier
     * @param scope memory scope
     * @param projectId affiliated project ID
     * @return list of proactive note keys
     */
    public List<String> listProactiveKeys(String agentId, MemoryScope scope, String projectId) {
        if (agentId == null || agentId.isBlank()) {
            return new ArrayList<>();
        }
        String pId = normalizeProject(scope, projectId);
        String sql =
                """
                SELECT key
                FROM memory_notes
                WHERE agent_id = ? AND project_id = ? AND scope = ? AND key LIKE 'proactive_%'
                ORDER BY updated_at DESC
                """;
        return databaseManager.executeQuery(
                conn -> {
                    List<String> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, agentId);
                        ps.setString(2, pId);
                        ps.setString(3, scope.name());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(rs.getString("key"));
                            }
                        }
                    }
                    return list;
                });
    }
}
