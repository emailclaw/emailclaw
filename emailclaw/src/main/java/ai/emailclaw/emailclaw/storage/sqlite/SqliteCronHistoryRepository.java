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

import ai.emailclaw.emailclaw.model.CronJobModel.CronExecutionRecord;
import ai.emailclaw.emailclaw.model.CronJobStatus;
import ai.emailclaw.emailclaw.model.CronJobTrigger;
import ai.emailclaw.emailclaw.util.DateTimeUtils;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite implementation for CronExecutionRecord history persistence, replacing cron-jobs-history/*.json.
 */
public class SqliteCronHistoryRepository {

    private static final Logger LOGGER =
            Logger.getLogger(SqliteCronHistoryRepository.class.getName());

    private final DatabaseManager databaseManager;

    public SqliteCronHistoryRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.databaseManager = databaseManager;
        LOGGER.info("SqliteCronHistoryRepository initialized");
    }

    private CronJobStatus parseStatus(String s) {
        if (s == null) return CronJobStatus.ERROR;
        for (CronJobStatus st : CronJobStatus.values()) {
            if (st.name().equalsIgnoreCase(s) || st.getCode().equalsIgnoreCase(s)) {
                return st;
            }
        }
        return CronJobStatus.ERROR;
    }

    private CronJobTrigger parseTrigger(String t) {
        if (t == null) return CronJobTrigger.SCHEDULED;
        for (CronJobTrigger tr : CronJobTrigger.values()) {
            if (tr.name().equalsIgnoreCase(t) || tr.getCode().equalsIgnoreCase(t)) {
                return tr;
            }
        }
        return CronJobTrigger.SCHEDULED;
    }

    /**
     * Loads execution history for a specific job, ordered by most recent first, capped at limit.
     *
     * @param jobId job ID
     * @param limit maximum records to return
     * @return list of cron execution records
     */
    public List<CronExecutionRecord> loadHistory(String jobId, int limit) {
        if (jobId == null || jobId.isBlank()) {
            return new ArrayList<>();
        }
        String sql =
                """
                SELECT run_at, status, error, trigger_type
                FROM cron_execution_history
                WHERE job_id = ?
                ORDER BY id DESC
                LIMIT ?
                """;
        return databaseManager.executeQuery(
                conn -> {
                    List<CronExecutionRecord> list = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, jobId);
                        ps.setInt(2, limit > 0 ? limit : 50);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                long runAt = rs.getLong("run_at");
                                CronJobStatus status = parseStatus(rs.getString("status"));
                                String error = rs.getString("error");
                                CronJobTrigger trigger = parseTrigger(rs.getString("trigger_type"));
                                list.add(new CronExecutionRecord(runAt, status, error, trigger));
                            }
                        }
                    }
                    return list;
                });
    }

    /**
     * Loads default history (up to 50 records) for a job.
     *
     * @param jobId job ID
     * @return list of cron execution records
     */
    public List<CronExecutionRecord> loadHistory(String jobId) {
        return loadHistory(jobId, 50);
    }

    /**
     * Batch replaces execution history for a job.
     *
     * @param jobId job ID
     * @param records records to save
     */
    public void saveHistory(String jobId, List<CronExecutionRecord> records) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        long now = DateTimeUtils.currentTimeMillis();
        databaseManager.executeTransaction(
                conn -> {
                    try (PreparedStatement del =
                            conn.prepareStatement(
                                    "DELETE FROM cron_execution_history WHERE job_id = ?;")) {
                        del.setString(1, jobId);
                        del.executeUpdate();
                    }

                    if (records != null && !records.isEmpty()) {
                        String insertSql =
                                """
                                INSERT INTO cron_execution_history (job_id, run_at, status, error, trigger_type, created_at)
                                VALUES (?, ?, ?, ?, ?, ?);
                                """;
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            for (CronExecutionRecord r : records) {
                                if (r != null) {
                                    ps.setString(1, jobId);
                                    ps.setLong(2, r.runAt() > 0 ? r.runAt() : now);
                                    ps.setString(
                                            3,
                                            r.status() != null
                                                    ? r.status().getCode()
                                                    : CronJobStatus.ERROR.getCode());
                                    ps.setString(4, r.error() != null ? r.error() : "");
                                    ps.setString(
                                            5,
                                            r.trigger() != null
                                                    ? r.trigger().getCode()
                                                    : CronJobTrigger.SCHEDULED.getCode());
                                    ps.setLong(6, now);
                                    ps.addBatch();
                                }
                            }
                            ps.executeBatch();
                        }
                    }
                });
        LOGGER.log(
                Level.INFO,
                "Saved {0} execution history records for job: {1}",
                new Object[] {records != null ? records.size() : 0, jobId});
    }

    /**
     * Records a single execution record and enforces the maximum history retention limit.
     *
     * @param jobId job ID
     * @param record execution record
     * @param maxLimit maximum retention limit
     */
    public void recordExecution(String jobId, CronExecutionRecord record, int maxLimit) {
        if (jobId == null || jobId.isBlank() || record == null) {
            return;
        }
        long now = DateTimeUtils.currentTimeMillis();
        databaseManager.executeTransaction(
                conn -> {
                    String insertSql =
                            """
                            INSERT INTO cron_execution_history (job_id, run_at, status, error, trigger_type, created_at)
                            VALUES (?, ?, ?, ?, ?, ?);
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setString(1, jobId);
                        ps.setLong(2, record.runAt() > 0 ? record.runAt() : now);
                        ps.setString(
                                3,
                                record.status() != null
                                        ? record.status().getCode()
                                        : CronJobStatus.ERROR.getCode());
                        ps.setString(4, record.error() != null ? record.error() : "");
                        ps.setString(
                                5,
                                record.trigger() != null
                                        ? record.trigger().getCode()
                                        : CronJobTrigger.SCHEDULED.getCode());
                        ps.setLong(6, now);
                        ps.executeUpdate();
                    }

                    // Prune excess records older than maxLimit
                    int limit = maxLimit > 0 ? maxLimit : 50;
                    String pruneSql =
                            """
                            DELETE FROM cron_execution_history
                            WHERE job_id = ? AND id NOT IN (
                                SELECT id FROM cron_execution_history
                                WHERE job_id = ?
                                ORDER BY id DESC
                                LIMIT ?
                            );
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(pruneSql)) {
                        ps.setString(1, jobId);
                        ps.setString(2, jobId);
                        ps.setInt(3, limit);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(
                Level.FINE,
                "Recorded cron execution for job: {0}, status: {1}",
                new Object[] {jobId, record.status()});
    }

    /**
     * Deletes all history records for a job.
     *
     * @param jobId job ID
     */
    public void deleteHistory(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        String sql = "DELETE FROM cron_execution_history WHERE job_id = ?";
        databaseManager.executeUpdate(
                conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, jobId);
                        ps.executeUpdate();
                    }
                });
        LOGGER.log(Level.INFO, "Deleted all execution history for job: {0}", jobId);
    }
}
