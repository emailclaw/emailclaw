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

import ai.emailclaw.emailclaw.model.AgentStatRecord;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite implementation for AgentStatRecord persistence, replacing agent-stats.json.
 */
public class SqliteAgentStatsRepository {

    private static final Logger LOGGER =
            Logger.getLogger(SqliteAgentStatsRepository.class.getName());

    private final DatabaseManager databaseManager;

    public SqliteAgentStatsRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.databaseManager = databaseManager;
        LOGGER.info("SqliteAgentStatsRepository initialized");
    }

    private AgentStatRecord mapRow(ResultSet rs) throws SQLException {
        return new AgentStatRecord(
                rs.getString("stat_date"),
                rs.getString("agent_id"),
                rs.getLong("message_count"),
                rs.getLong("tool_call_count"));
    }

    /**
     * Loads all agent stat records ordered by date descending.
     *
     * @return list of agent stat records
     */
    public List<AgentStatRecord> loadAll() {
        String sql =
                """
                SELECT stat_date, agent_id, message_count, tool_call_count
                FROM agent_stats
                ORDER BY stat_date DESC
                """;
        return databaseManager.executeQuery(
                conn -> {
                    List<AgentStatRecord> list = new ArrayList<>();
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
     * Batch replaces all agent stat records in an ACID transaction.
     *
     * @param records list of records to persist
     */
    public void saveAll(List<AgentStatRecord> records) {
        if (records == null) {
            return;
        }
        databaseManager.executeTransaction(
                conn -> {
                    try (PreparedStatement del =
                            conn.prepareStatement("DELETE FROM agent_stats;")) {
                        del.executeUpdate();
                    }

                    String insertSql =
                            """
                            INSERT INTO agent_stats (stat_date, agent_id, message_count, tool_call_count)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT(stat_date, agent_id) DO UPDATE SET
                                message_count = excluded.message_count,
                                tool_call_count = excluded.tool_call_count;
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        for (AgentStatRecord r : records) {
                            if (r != null) {
                                ps.setString(1, r.date() != null ? r.date() : "");
                                ps.setString(2, r.agentId() != null ? r.agentId() : "");
                                ps.setLong(3, r.messageCount());
                                ps.setLong(4, r.toolCallCount());
                                ps.addBatch();
                            }
                        }
                        ps.executeBatch();
                    }
                });
        LOGGER.log(Level.INFO, "Persisted {0} agent stat records to SQLite", records.size());
    }

    /**
     * Incrementally records or updates an agent stat record with SQL UPSERT.
     *
     * @param date date string (YYYY-MM-DD)
     * @param agentId agent identifier
     * @param messageCount messages to increment
     * @param toolCallCount tool calls to increment
     */
    public void recordStat(String date, String agentId, long messageCount, long toolCallCount) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        String sql =
                """
                INSERT INTO agent_stats (stat_date, agent_id, message_count, tool_call_count)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(stat_date, agent_id) DO UPDATE SET
                    message_count = agent_stats.message_count + excluded.message_count,
                    tool_call_count = agent_stats.tool_call_count + excluded.tool_call_count;
                """;
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, date != null ? date : "");
                        ps.setString(2, agentId);
                        ps.setLong(3, messageCount);
                        ps.setLong(4, toolCallCount);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.FINE,
                "Incremented stats for agent={0}, date={1}",
                new Object[] {agentId, date});
    }
}
