/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 */
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.storage.sqlite.DatabaseManager;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subagent registry service backed by SQLite database.
 *
 * <p>Manages subagent metadata, supporting cross-replica routing, concurrency protection,
 * and session recovery without JSON file corruption or lost updates.
 */
public class SpawnRegistryService {

    private static final Logger LOGGER = Logger.getLogger(SpawnRegistryService.class.getName());

    private final DatabaseManager databaseManager;

    public SpawnRegistryService(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.databaseManager = databaseManager;
        LOGGER.info("SpawnRegistryService initialized with SQLite backend");
    }

    public SpawnRegistryService(
            DatabaseManager databaseManager,
            ai.emailclaw.emailclaw.service.ProjectService projectService) {
        this(databaseManager);
    }

    public SpawnRegistryService(
            ai.emailclaw.emailclaw.service.ProjectService projectService,
            DatabaseManager databaseManager) {
        this(databaseManager);
    }

    private static String resolveProjectId(String projectId) {
        return (projectId != null && !projectId.isBlank()) ? projectId : "default";
    }

    private SpawnEntry mapRow(ResultSet rs) throws SQLException {
        return new SpawnEntry(
                rs.getString("entry_key"),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                rs.getString("label"),
                rs.getInt("depth"),
                rs.getString("parent_session_id"),
                rs.getString("status"),
                rs.getLong("created_at_ms"),
                rs.getLong("updated_at_ms"));
    }

    /**
     * Atomically registers or updates a subagent spawn entry in SQLite using native UPSERT.
     *
     * @param projectId project ID
     * @param key unique spawn entry key
     * @param entry spawn entry details
     */
    public void registerSpawnEntry(String projectId, String key, SpawnEntry entry) {
        if (key == null || key.isBlank() || entry == null) {
            return;
        }
        String pId = resolveProjectId(projectId);
        LOGGER.log(
                Level.INFO,
                "Register subagent entry in SQLite: projectId={0}, key={1}",
                new Object[] {pId, key});

        String sql =
                """
                INSERT INTO spawn_registry (
                    project_id, entry_key, agent_id, session_id, label, depth,
                    parent_session_id, status, created_at_ms, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(project_id, entry_key) DO UPDATE SET
                    agent_id = excluded.agent_id,
                    session_id = excluded.session_id,
                    label = excluded.label,
                    depth = excluded.depth,
                    parent_session_id = excluded.parent_session_id,
                    status = excluded.status,
                    updated_at_ms = excluded.updated_at_ms;
                """;

        long now = System.currentTimeMillis();
        long createdMs = entry.createdAtEpochMs() > 0 ? entry.createdAtEpochMs() : now;
        long updatedMs = entry.updatedAtEpochMs() > 0 ? entry.updatedAtEpochMs() : now;

        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pId);
                        ps.setString(2, key);
                        ps.setString(3, entry.agentId());
                        ps.setString(4, entry.sessionId());
                        ps.setString(5, entry.label());
                        ps.setInt(6, entry.depth());
                        ps.setString(
                                7, entry.parentSessionId() != null ? entry.parentSessionId() : "");
                        ps.setString(8, entry.status() != null ? entry.status() : "RUNNING");
                        ps.setLong(9, createdMs);
                        ps.setLong(10, updatedMs);
                        ps.executeUpdate();
                    }
                });
    }

    /**
     * Updates the status and timestamp of a subagent spawn entry atomically.
     *
     * @param projectId project ID
     * @param key unique spawn entry key
     * @param status new status string
     */
    public void updateStatus(String projectId, String key, String status) {
        if (key == null || key.isBlank() || status == null) {
            return;
        }
        String pId = resolveProjectId(projectId);
        long now = System.currentTimeMillis();
        String sql =
                """
                UPDATE spawn_registry
                SET status = ?, updated_at_ms = ?
                WHERE project_id = ? AND entry_key = ?;
                """;

        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, status);
                        ps.setLong(2, now);
                        ps.setString(3, pId);
                        ps.setString(4, key);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.FINE,
                "Updated subagent entry status in SQLite: key={0}, status={1}",
                new Object[] {key, status});
    }

    /**
     * Finds all subagent entries spawned from a given parent session.
     *
     * <p>Utilizes the indexed column {@code (project_id, parent_session_id)} for fast retrieval.
     *
     * @param projectId project ID
     * @param parentSessionId parent session identifier
     * @return list of matching spawn entries ordered by creation time
     */
    public List<SpawnEntry> findByParentSessionId(String projectId, String parentSessionId) {
        if (parentSessionId == null) {
            return List.of();
        }
        String pId = resolveProjectId(projectId);
        String sql =
                """
                SELECT entry_key, agent_id, session_id, label, depth, parent_session_id, status, created_at_ms, updated_at_ms
                FROM spawn_registry
                WHERE project_id = ? AND parent_session_id = ?
                ORDER BY created_at_ms ASC;
                """;

        return databaseManager.executeQuery(
                conn -> {
                    List<SpawnEntry> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pId);
                        ps.setString(2, parentSessionId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(mapRow(rs));
                            }
                        }
                    }
                    return list;
                });
    }

    /**
     * Retrieves a single spawn entry by its primary key.
     *
     * @param projectId project ID
     * @param key unique spawn entry key
     * @return spawn entry or null if absent
     */
    public SpawnEntry findSpawnEntry(String projectId, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String pId = resolveProjectId(projectId);
        String sql =
                """
                SELECT entry_key, agent_id, session_id, label, depth, parent_session_id, status, created_at_ms, updated_at_ms
                FROM spawn_registry
                WHERE project_id = ? AND entry_key = ?;
                """;

        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pId);
                        ps.setString(2, key);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return mapRow(rs);
                            }
                        }
                    }
                    return null;
                });
    }

    /**
     * Removes a spawn entry by its primary key.
     *
     * @param projectId project ID
     * @param key unique spawn entry key
     */
    public void removeSpawnEntry(String projectId, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String pId = resolveProjectId(projectId);
        LOGGER.log(
                Level.INFO,
                "Remove subagent entry from SQLite: projectId={0}, key={1}",
                new Object[] {pId, key});
        String sql = "DELETE FROM spawn_registry WHERE project_id = ? AND entry_key = ?;";
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pId);
                        ps.setString(2, key);
                        ps.executeUpdate();
                    }
                });
    }

    /**
     * Retrieves all spawn entries belonging to a given project.
     *
     * @param projectId project ID
     * @return map of entry key to spawn entry
     */
    public Map<String, SpawnEntry> getAllSpawnEntries(String projectId) {
        String pId = resolveProjectId(projectId);
        String sql =
                """
                SELECT entry_key, agent_id, session_id, label, depth, parent_session_id, status, created_at_ms, updated_at_ms
                FROM spawn_registry
                WHERE project_id = ?
                ORDER BY created_at_ms ASC;
                """;

        return databaseManager.executeQuery(
                conn -> {
                    Map<String, SpawnEntry> map = new LinkedHashMap<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                SpawnEntry entry = mapRow(rs);
                                map.put(entry.key(), entry);
                            }
                        }
                    }
                    return map;
                });
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
