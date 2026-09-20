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

import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite implementation for ChatSessionInfo persistence, replacing sessions.json.
 */
public class SqliteChatSessionRepository {

    private static final Logger LOGGER =
            Logger.getLogger(SqliteChatSessionRepository.class.getName());

    private final DatabaseManager databaseManager;

    public SqliteChatSessionRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.databaseManager = databaseManager;
        LOGGER.info("SqliteChatSessionRepository initialized");
    }

    private ChatSessionInfo mapRow(ResultSet rs) throws SQLException {
        ChatSessionInfo session = new ChatSessionInfo();
        session.setId(rs.getString("id"));
        session.setName(rs.getString("name"));
        session.setProjectId(rs.getString("project_id"));
        session.setAgentId(rs.getString("agent_id"));
        session.setUserId(rs.getString("user_id"));
        session.setChannel(rs.getString("channel"));
        session.setCreatedAt(rs.getLong("created_at"));
        session.setUpdatedAt(rs.getLong("updated_at"));
        session.setPinned(rs.getInt("pinned") != 0);
        session.setKind(rs.getString("kind"));
        session.setDescription(rs.getString("description"));
        session.setStatus(ChatSessionInfo.TaskStatus.fromString(rs.getString("status")));
        return session;
    }

    /**
     * Loads all chat sessions sorted by pinned descending, updated_at descending.
     *
     * @return list of chat sessions
     */
    public List<ChatSessionInfo> loadAll() {
        String sql =
                """
                SELECT id, name, project_id, agent_id, user_id, channel, created_at, updated_at,
                       pinned, kind, description, status
                FROM chat_sessions
                ORDER BY pinned DESC, updated_at DESC
                """;
        return databaseManager.executeQuery(
                conn -> {
                    List<ChatSessionInfo> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql);
                            ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapRow(rs));
                        }
                    }
                    return list;
                });
    }

    /**
     * Finds a single session by its unique ID.
     *
     * @param id session ID
     * @return Optional ChatSessionInfo
     */
    public Optional<ChatSessionInfo> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String sql =
                """
                SELECT id, name, project_id, agent_id, user_id, channel, created_at, updated_at,
                       pinned, kind, description, status
                FROM chat_sessions
                WHERE id = ?
                """;
        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return Optional.of(mapRow(rs));
                            }
                        }
                    }
                    return Optional.empty();
                });
    }

    /**
     * Saves or updates a single chat session.
     *
     * @param session session to save
     */
    public void save(ChatSessionInfo session) {
        if (session == null || session.getId() == null || session.getId().isBlank()) {
            return;
        }
        String sql =
                """
                INSERT INTO chat_sessions (id, name, project_id, agent_id, user_id, channel,
                                           created_at, updated_at, pinned, kind, description, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    project_id = excluded.project_id,
                    agent_id = excluded.agent_id,
                    user_id = excluded.user_id,
                    channel = excluded.channel,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at,
                    pinned = excluded.pinned,
                    kind = excluded.kind,
                    description = excluded.description,
                    status = excluded.status;
                """;
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        bindSession(ps, session);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(Level.FINE, "Saved chat session: {0}", session.getId());
    }

    /**
     * Batch saves or replaces all chat sessions in an ACID transaction.
     *
     * @param sessions list of sessions to persist
     */
    public void saveAll(List<ChatSessionInfo> sessions) {
        if (sessions == null) {
            return;
        }
        String sql =
                """
                INSERT INTO chat_sessions (id, name, project_id, agent_id, user_id, channel,
                                           created_at, updated_at, pinned, kind, description, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    project_id = excluded.project_id,
                    agent_id = excluded.agent_id,
                    user_id = excluded.user_id,
                    channel = excluded.channel,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at,
                    pinned = excluded.pinned,
                    kind = excluded.kind,
                    description = excluded.description,
                    status = excluded.status;
                """;
        databaseManager.executeTransaction(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (ChatSessionInfo s : sessions) {
                            if (s != null && s.getId() != null && !s.getId().isBlank()) {
                                bindSession(ps, s);
                                ps.addBatch();
                            }
                        }
                        ps.executeBatch();
                    }
                });
        LOGGER.log(Level.INFO, "Batch saved {0} chat sessions to SQLite", sessions.size());
    }

    /**
     * Deletes a chat session by ID, cascade deleting associated agent state.
     *
     * @param sessionId session ID to delete
     */
    public void delete(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        databaseManager.executeTransaction(
                conn -> {
                    try (PreparedStatement ps1 =
                                    conn.prepareStatement(
                                            "DELETE FROM agent_state WHERE session_id = ?;");
                            PreparedStatement ps2 =
                                    conn.prepareStatement(
                                            "DELETE FROM chat_sessions WHERE id = ?;")) {
                        ps1.setString(1, sessionId);
                        ps1.executeUpdate();

                        ps2.setString(1, sessionId);
                        ps2.executeUpdate();
                    }
                });
        LOGGER.log(Level.INFO, "Deleted chat session and its state: {0}", sessionId);
    }

    /**
     * Batch deletes chat sessions by IDs in an ACID transaction.
     *
     * @param sessionIds list of session IDs to delete
     */
    public void batchDelete(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        databaseManager.executeTransaction(
                conn -> {
                    try (PreparedStatement psState =
                                    conn.prepareStatement(
                                            "DELETE FROM agent_state WHERE session_id = ?;");
                            PreparedStatement psSession =
                                    conn.prepareStatement(
                                            "DELETE FROM chat_sessions WHERE id = ?;")) {
                        for (String id : sessionIds) {
                            if (id != null && !id.isBlank()) {
                                psState.setString(1, id);
                                psState.addBatch();
                                psSession.setString(1, id);
                                psSession.addBatch();
                            }
                        }
                        psState.executeBatch();
                        psSession.executeBatch();
                    }
                });
        LOGGER.log(Level.INFO, "Batch deleted {0} chat sessions from SQLite", sessionIds.size());
    }

    private void bindSession(PreparedStatement ps, ChatSessionInfo s) throws SQLException {
        ps.setString(1, s.getId() != null ? s.getId() : "");
        ps.setString(2, s.getName() != null ? s.getName() : "");
        ps.setString(3, s.getProjectId());
        ps.setString(4, s.getAgentId() != null ? s.getAgentId() : "");
        ps.setString(5, s.getUserId() != null ? s.getUserId() : "");
        ps.setString(6, s.getChannel() != null ? s.getChannel() : "");
        ps.setLong(7, s.getCreatedAt());
        ps.setLong(8, s.getUpdatedAt());
        ps.setInt(9, s.isPinned() ? 1 : 0);
        ps.setString(10, s.getKind() != null ? s.getKind() : ChatSessionInfo.KIND_CHAT);
        ps.setString(11, s.getDescription() != null ? s.getDescription() : "");
        ps.setString(
                12,
                s.getStatus() != null
                        ? s.getStatus().name()
                        : ChatSessionInfo.TaskStatus.ACTIVE.name());
    }
}
