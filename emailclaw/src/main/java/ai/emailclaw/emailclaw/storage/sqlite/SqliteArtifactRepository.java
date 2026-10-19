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

import ai.emailclaw.emailclaw.model.ArtifactRecord;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite implementation for storing and querying {@link ArtifactRecord} deliverables.
 */
public class SqliteArtifactRepository {

    private static final Logger LOGGER = Logger.getLogger(SqliteArtifactRepository.class.getName());

    private final DatabaseManager databaseManager;

    public SqliteArtifactRepository(DatabaseManager databaseManager) {
        this.databaseManager =
                Objects.requireNonNull(databaseManager, "DatabaseManager cannot be null");
        LOGGER.info("SqliteArtifactRepository initialized");
    }

    /**
     * Persists or updates an artifact record in the database.
     *
     * @param record artifact record to save
     */
    public void save(ArtifactRecord record) {
        if (record == null || record.id() == null || record.id().isBlank()) {
            return;
        }
        String sql =
                """
                INSERT INTO artifacts (
                    id, project_id, session_id, agent_id, channel, file_name,
                    source_path, storage_path, mime_type, size_bytes, sha256,
                    description, created_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    project_id = excluded.project_id,
                    session_id = excluded.session_id,
                    agent_id = excluded.agent_id,
                    channel = excluded.channel,
                    file_name = excluded.file_name,
                    source_path = excluded.source_path,
                    storage_path = excluded.storage_path,
                    mime_type = excluded.mime_type,
                    size_bytes = excluded.size_bytes,
                    sha256 = excluded.sha256,
                    description = excluded.description,
                    created_at_ms = excluded.created_at_ms;
                """;
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, record.id());
                        ps.setString(2, record.projectId());
                        ps.setString(3, record.sessionId());
                        ps.setString(4, record.agentId());
                        ps.setString(5, record.channel());
                        ps.setString(6, record.fileName());
                        ps.setString(7, record.sourcePath());
                        ps.setString(8, record.storagePath());
                        ps.setString(9, record.mimeType());
                        ps.setLong(10, record.sizeBytes());
                        ps.setString(11, record.sha256());
                        ps.setString(12, record.description());
                        ps.setLong(13, record.createdAtMs());
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.INFO,
                "Saved artifact record: id={0}, file={1}, size={2}, session={3}, project={4}",
                new Object[] {
                    record.id(),
                    record.fileName(),
                    record.sizeBytes(),
                    record.sessionId(),
                    record.projectId()
                });
    }

    /**
     * Finds an artifact by its unique ID.
     *
     * @param id artifact ID
     * @return Optional containing the artifact record if found
     */
    public Optional<ArtifactRecord> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM artifacts WHERE id = ?;";
        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return Optional.of(mapResultSet(rs));
                            }
                        }
                    }
                    return Optional.empty();
                });
    }

    /**
     * Lists all artifacts associated with a specific chat session, ordered from newest to oldest.
     *
     * @param sessionId session ID
     * @return list of artifacts
     */
    public List<ArtifactRecord> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        String sql = "SELECT * FROM artifacts WHERE session_id = ? ORDER BY created_at_ms DESC;";
        return databaseManager.executeQuery(
                conn -> {
                    List<ArtifactRecord> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, sessionId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(mapResultSet(rs));
                            }
                        }
                    }
                    return list;
                });
    }

    /**
     * Lists all artifacts associated with a specific project, ordered from newest to oldest.
     *
     * @param projectId project ID
     * @return list of artifacts
     */
    public List<ArtifactRecord> findByProjectId(String projectId) {
        String sql = "SELECT * FROM artifacts WHERE project_id = ? ORDER BY created_at_ms DESC;";
        return databaseManager.executeQuery(
                conn -> {
                    List<ArtifactRecord> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, projectId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(mapResultSet(rs));
                            }
                        }
                    }
                    return list;
                });
    }

    /**
     * Deletes an artifact record by ID.
     *
     * @param id artifact ID
     */
    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String sql = "DELETE FROM artifacts WHERE id = ?;";
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, id);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(Level.INFO, "Deleted artifact record by id: {0}", id);
    }

    private ArtifactRecord mapResultSet(ResultSet rs) throws SQLException {
        return new ArtifactRecord(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("session_id"),
                rs.getString("agent_id"),
                rs.getString("channel"),
                rs.getString("file_name"),
                rs.getString("source_path"),
                rs.getString("storage_path"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                rs.getString("description"),
                rs.getLong("created_at_ms"));
    }
}
