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

import ai.emailclaw.emailclaw.util.DateTimeUtils;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.state.VersionedState;
import io.agentscope.core.util.JsonUtils;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite implementation of {@link AgentStateStore} adhering to AgentScope industrial standards.
 *
 * <p>Stores state values in the {@code agent_state} SQLite table, providing ACID transactions,
 * CAS optimistic locking (supportsVersioning), and atomic batch operations.
 */
public class SqliteAgentStateStore implements AgentStateStore {

    private static final Logger LOGGER = Logger.getLogger(SqliteAgentStateStore.class.getName());

    private final DatabaseManager databaseManager;

    public SqliteAgentStateStore(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.databaseManager = databaseManager;
        LOGGER.info("SqliteAgentStateStore initialized");
    }

    private String slotId(String userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        // In Emailclaw, each chat session has a globally unique UUID.
        // If a caller passed a composite key like "userId:sessionId", normalize to canonical
        // sessionId.
        int colonIdx = sessionId.indexOf(':');
        if (colonIdx >= 0 && colonIdx < sessionId.length() - 1) {
            String candidate = sessionId.substring(colonIdx + 1);
            if (candidate.length() >= 32) {
                return candidate;
            }
        }
        return sessionId;
    }

    @Override
    public boolean supportsVersioning() {
        return true;
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        String slot = slotId(userId, sessionId);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key cannot be null or blank");
        }
        String json = JsonUtils.getJsonCodec().toJson(value);
        long now = DateTimeUtils.currentTimeMillis();

        String sql =
                """
                INSERT INTO agent_state (session_id, state_key, item_index, state_data, version, created_at, updated_at)
                VALUES (?, ?, 0, ?, 1, ?, ?)
                ON CONFLICT(session_id, state_key, item_index) DO UPDATE SET
                    state_data = excluded.state_data,
                    version = agent_state.version + 1,
                    updated_at = excluded.updated_at;
                """;

        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, slot);
                        ps.setString(2, key);
                        ps.setString(3, json);
                        ps.setLong(4, now);
                        ps.setLong(5, now);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.FINE,
                "Saved state in SQLite: session={0}, key={1}",
                new Object[] {slot, key});
    }

    @Override
    public <T extends State> VersionedState<T> getVersioned(
            String userId, String sessionId, String key, Class<T> type) {
        String slot = slotId(userId, sessionId);
        String sql =
                """
                SELECT state_data, version
                FROM agent_state
                WHERE session_id = ? AND state_key = ? AND item_index = 0
                """;
        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, slot);
                        ps.setString(2, key);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String data = rs.getString("state_data");
                                long version = rs.getLong("version");
                                T obj = JsonUtils.getJsonCodec().fromJson(data, type);
                                return new VersionedState<>(obj, version);
                            }
                        }
                    }
                    return new VersionedState<>(null, 0L);
                });
    }

    @Override
    public long saveIfVersion(
            String userId, String sessionId, String key, State value, long expectedVersion) {
        if (expectedVersion == UNVERSIONED) {
            save(userId, sessionId, key, value);
            return 1L;
        }
        String slot = slotId(userId, sessionId);
        String json = JsonUtils.getJsonCodec().toJson(value);
        long now = DateTimeUtils.currentTimeMillis();

        if (expectedVersion == 0) {
            // Atomic create-if-absent
            String insertSql =
                    """
                    INSERT INTO agent_state (session_id, state_key, item_index, state_data, version, created_at, updated_at)
                    VALUES (?, ?, 0, ?, 1, ?, ?)
                    ON CONFLICT(session_id, state_key, item_index) DO NOTHING;
                    """;
            return databaseManager.executeQuery(
                    conn -> {
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            ps.setString(1, slot);
                            ps.setString(2, key);
                            ps.setString(3, json);
                            ps.setLong(4, now);
                            ps.setLong(5, now);
                            int inserted = ps.executeUpdate();
                            if (inserted > 0) {
                                LOGGER.log(
                                        Level.FINE,
                                        "CAS create-if-absent succeeded for session={0}, key={1},"
                                                + " version=1",
                                        new Object[] {slot, key});
                                return 1L;
                            } else {
                                LOGGER.log(
                                        Level.FINE,
                                        "CAS create-if-absent conflict: key already exists for"
                                                + " session={0}, key={1}",
                                        new Object[] {slot, key});
                                return UNVERSIONED;
                            }
                        }
                    });
        }

        // Atomic single-statement CAS update
        long nextVersion = expectedVersion + 1;
        String updateSql =
                """
                UPDATE agent_state
                SET state_data = ?, version = version + 1, updated_at = ?
                WHERE session_id = ? AND state_key = ? AND item_index = 0 AND version = ?;
                """;
        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, json);
                        ps.setLong(2, now);
                        ps.setString(3, slot);
                        ps.setString(4, key);
                        ps.setLong(5, expectedVersion);
                        int updated = ps.executeUpdate();
                        if (updated > 0) {
                            LOGGER.log(
                                    Level.FINE,
                                    "CAS update succeeded for session={0}, key={1},"
                                            + " newVersion={2}",
                                    new Object[] {slot, key, nextVersion});
                            return nextVersion;
                        } else {
                            LOGGER.log(
                                    Level.FINE,
                                    "CAS update conflict: expectedVersion={0} mismatched for"
                                            + " session={1}, key={2}",
                                    new Object[] {expectedVersion, slot, key});
                            return UNVERSIONED;
                        }
                    }
                });
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        String slot = slotId(userId, sessionId);
        long now = DateTimeUtils.currentTimeMillis();

        databaseManager.executeTransaction(
                conn -> {
                    try (PreparedStatement del =
                            conn.prepareStatement(
                                    "DELETE FROM agent_state WHERE session_id = ? AND state_key ="
                                            + " ?;")) {
                        del.setString(1, slot);
                        del.setString(2, key);
                        del.executeUpdate();
                    }

                    if (values != null && !values.isEmpty()) {
                        String insertSql =
                                """
                                INSERT INTO agent_state (session_id, state_key, item_index, state_data, version, created_at, updated_at)
                                VALUES (?, ?, ?, ?, 1, ?, ?);
                                """;
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            for (int i = 0; i < values.size(); i++) {
                                State val = values.get(i);
                                String json = JsonUtils.getJsonCodec().toJson(val);
                                ps.setString(1, slot);
                                ps.setString(2, key);
                                ps.setInt(3, i);
                                ps.setString(4, json);
                                ps.setLong(5, now);
                                ps.setLong(6, now);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }
                });
        LOGGER.log(
                Level.FINE,
                "Saved state list in SQLite: session={0}, key={1}, count={2}",
                new Object[] {slot, key, values != null ? values.size() : 0});
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String key, Class<T> type) {
        String slot = slotId(userId, sessionId);
        String sql =
                """
                SELECT state_data
                FROM agent_state
                WHERE session_id = ? AND state_key = ? AND item_index = 0
                """;
        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, slot);
                        ps.setString(2, key);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String data = rs.getString("state_data");
                                T obj = JsonUtils.getJsonCodec().fromJson(data, type);
                                return Optional.ofNullable(obj);
                            }
                        }
                    }
                    return Optional.empty();
                });
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String key, Class<T> itemType) {
        String slot = slotId(userId, sessionId);
        String sql =
                """
                SELECT state_data
                FROM agent_state
                WHERE session_id = ? AND state_key = ?
                ORDER BY item_index ASC
                """;
        return databaseManager.executeQuery(
                conn -> {
                    List<T> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, slot);
                        ps.setString(2, key);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String data = rs.getString("state_data");
                                T obj = JsonUtils.getJsonCodec().fromJson(data, itemType);
                                if (obj != null) {
                                    list.add(obj);
                                }
                            }
                        }
                    }
                    return list;
                });
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        String slot = slotId(userId, sessionId);
        String sql = "SELECT 1 FROM agent_state WHERE session_id = ? LIMIT 1";
        return databaseManager.executeQuery(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, slot);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next();
                        }
                    }
                });
    }

    @Override
    public void delete(String userId, String sessionId) {
        String slot = slotId(userId, sessionId);
        String sql = "DELETE FROM agent_state WHERE session_id = ?";
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, slot);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(Level.INFO, "Deleted state for session: {0}", slot);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        String slot = slotId(userId, sessionId);
        String sql = "DELETE FROM agent_state WHERE session_id = ? AND state_key = ?";
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, slot);
                        ps.setString(2, key);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(Level.INFO, "Deleted state for session={0}, key={1}", new Object[] {slot, key});
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        boolean hasUser = userId != null && !userId.isBlank();
        String prefix = hasUser ? userId + ":" : null;
        String sql =
                hasUser
                        ? "SELECT DISTINCT session_id FROM agent_state WHERE session_id LIKE ?"
                        : "SELECT DISTINCT session_id FROM agent_state WHERE session_id NOT LIKE"
                                + " '%:%'";

        return databaseManager.executeQuery(
                conn -> {
                    Set<String> set = new HashSet<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        if (hasUser) {
                            ps.setString(1, prefix + "%");
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String full = rs.getString("session_id");
                                if (hasUser) {
                                    if (full.startsWith(prefix)) {
                                        set.add(full.substring(prefix.length()));
                                    }
                                } else {
                                    set.add(full);
                                }
                            }
                        }
                    }
                    return set;
                });
    }

    @Override
    public void close() {
        // Lifecycle managed by DatabaseManager
    }
}
