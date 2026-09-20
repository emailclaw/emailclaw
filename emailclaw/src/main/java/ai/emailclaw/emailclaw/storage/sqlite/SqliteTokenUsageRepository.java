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

import ai.emailclaw.emailclaw.model.TokenUsageRecord;
import ai.emailclaw.emailclaw.util.DateTimeUtils;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite implementation for TokenUsageRecord persistence, replacing token-usage.json.
 */
public class SqliteTokenUsageRepository {

    private static final Logger LOGGER =
            Logger.getLogger(SqliteTokenUsageRepository.class.getName());

    private final DatabaseManager databaseManager;

    public SqliteTokenUsageRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.databaseManager = databaseManager;
        LOGGER.info("SqliteTokenUsageRepository initialized");
    }

    private TokenUsageRecord mapRow(ResultSet rs) throws SQLException {
        return new TokenUsageRecord(
                rs.getString("usage_date"),
                rs.getString("provider_id"),
                rs.getString("model_id"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                rs.getLong("cached_tokens"));
    }

    /**
     * Loads all token usage records ordered by ID ascending.
     *
     * @return list of token usage records
     */
    public List<TokenUsageRecord> loadAll() {
        String sql =
                """
                SELECT usage_date, provider_id, model_id, prompt_tokens, completion_tokens, cached_tokens
                FROM token_usage
                ORDER BY id ASC
                """;
        return databaseManager.executeQuery(
                conn -> {
                    List<TokenUsageRecord> list = new ArrayList<>();
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
     * Replaces all token usage records in an ACID transaction.
     *
     * @param records list of records to persist
     */
    public void saveAll(List<TokenUsageRecord> records) {
        if (records == null) {
            return;
        }
        long now = DateTimeUtils.currentTimeMillis();
        databaseManager.executeTransaction(
                conn -> {
                    try (PreparedStatement del =
                            conn.prepareStatement("DELETE FROM token_usage;")) {
                        del.executeUpdate();
                    }

                    String insertSql =
                            """
                            INSERT INTO token_usage (usage_date, provider_id, model_id,
                                                     prompt_tokens, completion_tokens, cached_tokens, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?);
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        for (TokenUsageRecord r : records) {
                            if (r != null) {
                                ps.setString(1, r.date() != null ? r.date() : "");
                                ps.setString(2, r.providerId() != null ? r.providerId() : "");
                                ps.setString(3, r.modelId() != null ? r.modelId() : "");
                                ps.setLong(4, r.promptTokens());
                                ps.setLong(5, r.completionTokens());
                                ps.setLong(6, r.cachedTokens());
                                ps.setLong(7, now);
                                ps.addBatch();
                            }
                        }
                        ps.executeBatch();
                    }
                });
        LOGGER.log(Level.INFO, "Persisted {0} token usage records to SQLite", records.size());
    }

    /**
     * Records a single token usage entry incrementally.
     *
     * @param record token usage entry
     */
    public void recordUsage(TokenUsageRecord record) {
        if (record == null) {
            return;
        }
        String sql =
                """
                INSERT INTO token_usage (usage_date, provider_id, model_id,
                                         prompt_tokens, completion_tokens, cached_tokens, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, record.date() != null ? record.date() : "");
                        ps.setString(2, record.providerId() != null ? record.providerId() : "");
                        ps.setString(3, record.modelId() != null ? record.modelId() : "");
                        ps.setLong(4, record.promptTokens());
                        ps.setLong(5, record.completionTokens());
                        ps.setLong(6, record.cachedTokens());
                        ps.setLong(7, DateTimeUtils.currentTimeMillis());
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.FINE,
                "Recorded token usage: provider={0}, model={1}, total={2}",
                new Object[] {
                    record.providerId(),
                    record.modelId(),
                    record.promptTokens() + record.completionTokens()
                });
    }
}
