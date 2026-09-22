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

import ai.emailclaw.emailclaw.storage.AppPaths;
import ai.emailclaw.emailclaw.util.DateTimeUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite embedded database manager adhering to industrial platform standards.
 *
 * <p>Centralizes embedded SQLite connection management, connection lifecycle, WAL mode pragmas,
 * foreign key constraints, and initial schema DDL migrations. Adheres strictly to Pure DI principles
 * with no global singletons.
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;
    private static final long IDLE_CHECK_THRESHOLD_MS = 10_000L;
    private static final int TARGET_SCHEMA_VERSION = 3;

    private final Path databasePath;
    private final String jdbcUrl;
    private final Object lock = new Object();
    private final AtomicBoolean schemaInitialized = new AtomicBoolean(false);
    private final Thread shutdownHook;
    private Connection connection;
    private volatile long lastActiveTimeMs = 0;

    /**
     * SQLite WAL checkpoint modes.
     */
    public enum CheckpointMode {
        PASSIVE,
        FULL,
        RESTART,
        TRUNCATE
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }

    /**
     * Resolves the actual database file location from configured path.
     * If the path is a directory, stores emailclaw.db inside it; otherwise uses the path directly.
     *
     * @param configuredPath configured path
     * @return resolved database file path
     */
    public static Path resolveDatabaseFile(Path configuredPath) {
        if (configuredPath == null) {
            configuredPath = AppPaths.fromDefault().databaseFile;
        }
        if (Files.isDirectory(configuredPath)) {
            return configuredPath.resolve("emailclaw.db");
        }
        return configuredPath;
    }

    /**
     * Creates a DatabaseManager targeting the specified database path.
     *
     * @param configuredPath target database file or directory path
     */
    public DatabaseManager(Path configuredPath) {
        this.databasePath = resolveDatabaseFile(configuredPath);
        try {
            Path parent = this.databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to create directory for database: " + this.databasePath,
                    e);
            throw new DatabaseException("Failed to create database directory", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + this.databasePath.toAbsolutePath();
        LOGGER.log(Level.INFO, "DatabaseManager initialized with JDBC URL: {0}", this.jdbcUrl);

        this.shutdownHook = new Thread(this::handleShutdown, "db-shutdown-hook");
        try {
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }

        initSchema();
    }

    /**
     * Default constructor using {@link AppPaths#fromDefault()}'s databaseFile.
     */
    public DatabaseManager() {
        this(AppPaths.fromDefault().databaseFile);
    }

    /**
     * Gets or creates an active and validated SQLite Connection with WAL mode and pragmas configured.
     * Performs idle health check and auto-reconnect if the connection is dead or tainted.
     *
     * @return active and verified SQLite Connection
     * @throws SQLException if a database access error occurs
     */
    private Connection getOrCreateValidConnection() throws SQLException {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            boolean needValidation = (now - lastActiveTimeMs) > IDLE_CHECK_THRESHOLD_MS;

            if (connection != null && !connection.isClosed()) {
                if (needValidation) {
                    try {
                        if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                            LOGGER.warning(
                                    "SQLite connection failed health validation check. Rebuilding"
                                            + " connection...");
                            invalidateConnection();
                        }
                    } catch (Exception e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Error during SQLite connection validation check. Rebuilding"
                                        + " connection...",
                                e);
                        invalidateConnection();
                    }
                }
            }

            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(jdbcUrl);
                try (Statement stmt = connection.createStatement()) {
                    // Industrial-grade SQLite pragmas for high-concurrency desktop/daemon workloads
                    stmt.execute("PRAGMA journal_mode = WAL;");
                    stmt.execute("PRAGMA synchronous = NORMAL;");
                    stmt.execute("PRAGMA busy_timeout = 10000;");
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    stmt.execute("PRAGMA wal_autocheckpoint = 1000;");
                }
                LOGGER.log(
                        Level.INFO, "New SQLite connection established with WAL pragmas applied.");
            }

            lastActiveTimeMs = System.currentTimeMillis();
            return connection;
        }
    }

    /**
     * Performs a SQLite WAL checkpoint operation.
     *
     * @param mode checkpoint mode (PASSIVE, FULL, RESTART, TRUNCATE)
     */
    public void walCheckpoint(CheckpointMode mode) {
        CheckpointMode targetMode = mode != null ? mode : CheckpointMode.PASSIVE;
        synchronized (lock) {
            try {
                Connection conn = getOrCreateValidConnection();
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(" + targetMode.name() + ");");
                }
                LOGGER.log(Level.FINE, "WAL checkpoint completed with mode: {0}", targetMode);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to execute WAL checkpoint: " + targetMode, e);
            }
        }
    }

    /**
     * Closes and discards the current connection if it has encountered fatal errors or state taint.
     */
    private void invalidateConnection() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        connection.close();
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.FINE, "Failed to close invalidated connection cleanly", e);
                } finally {
                    connection = null;
                }
            }
        }
    }

    /**
     * Tests if an exception indicates a broken, lost, or locked connection that warrants reconnection.
     *
     * @param throwable error to evaluate
     * @return true if error suggests reconnecting might resolve the issue
     */
    private boolean isRecoverableConnectionError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        String msg = throwable.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.contains("closed")
                    || lower.contains("broken pipe")
                    || lower.contains("connection")
                    || lower.contains("interrupted")
                    || lower.contains("io error")
                    || lower.contains("i/o error")
                    || lower.contains("disk i/o error")
                    || lower.contains("cannot open")
                    || lower.contains("database is locked")) {
                return true;
            }
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            return isRecoverableConnectionError(throwable.getCause());
        }
        return false;
    }

    /**
     * Executes a read-only or mapping query against the database under thread synchronization.
     * Includes automatic single retry if a recoverable connection failure is detected.
     *
     * @param function SQL operation receiving Connection and returning result
     * @param <T> return type
     * @return result of the query function
     */
    public <T> T executeQuery(SqlFunction<Connection, T> function) {
        synchronized (lock) {
            try {
                Connection conn = getOrCreateValidConnection();
                T result = function.apply(conn);
                lastActiveTimeMs = System.currentTimeMillis();
                return result;
            } catch (SQLException e) {
                if (isRecoverableConnectionError(e)) {
                    LOGGER.log(
                            Level.WARNING,
                            "Recoverable SQLite error in executeQuery, invalidating connection and"
                                    + " retrying once...",
                            e);
                    invalidateConnection();
                    try {
                        Connection conn = getOrCreateValidConnection();
                        T result = function.apply(conn);
                        lastActiveTimeMs = System.currentTimeMillis();
                        return result;
                    } catch (SQLException retryEx) {
                        LOGGER.log(
                                Level.SEVERE,
                                "SQLite query retry failed after reconnection",
                                retryEx);
                        throw new DatabaseException(
                                "SQLite query retry failed: " + retryEx.getMessage(), retryEx);
                    }
                }
                LOGGER.log(Level.SEVERE, "SQLite query execution failed", e);
                throw new DatabaseException("SQLite query failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Executes an update or statement against the database under thread synchronization.
     * Includes automatic single retry if a recoverable connection failure is detected.
     *
     * @param consumer SQL operation receiving Connection
     */
    public void executeUpdate(SqlConsumer<Connection> consumer) {
        synchronized (lock) {
            try {
                Connection conn = getOrCreateValidConnection();
                consumer.accept(conn);
                lastActiveTimeMs = System.currentTimeMillis();
            } catch (SQLException e) {
                if (isRecoverableConnectionError(e)) {
                    LOGGER.log(
                            Level.WARNING,
                            "Recoverable SQLite error in executeUpdate, invalidating connection and"
                                    + " retrying once...",
                            e);
                    invalidateConnection();
                    try {
                        Connection conn = getOrCreateValidConnection();
                        consumer.accept(conn);
                        lastActiveTimeMs = System.currentTimeMillis();
                        return;
                    } catch (SQLException retryEx) {
                        LOGGER.log(
                                Level.SEVERE,
                                "SQLite update retry failed after reconnection",
                                retryEx);
                        throw new DatabaseException(
                                "SQLite update retry failed: " + retryEx.getMessage(), retryEx);
                    }
                }
                LOGGER.log(Level.SEVERE, "SQLite update execution failed", e);
                throw new DatabaseException("SQLite update failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Executes a set of operations in an ACID transaction with auto commit/rollback.
     * Guards against state taint by immediately invalidating the connection if rollback fails
     * or auto-commit restoration fails.
     *
     * @param consumer SQL operation receiving Connection within transaction
     */
    public void executeTransaction(SqlConsumer<Connection> consumer) {
        synchronized (lock) {
            Connection conn = null;
            boolean originalAutoCommit = true;
            boolean transactionStarted = false;
            try {
                conn = getOrCreateValidConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                transactionStarted = true;
                consumer.accept(conn);
                conn.commit();
                lastActiveTimeMs = System.currentTimeMillis();
            } catch (Exception e) {
                boolean rollbackSuccess = false;
                if (conn != null && transactionStarted) {
                    try {
                        conn.rollback();
                        rollbackSuccess = true;
                    } catch (SQLException rollbackEx) {
                        LOGGER.log(Level.WARNING, "Transaction rollback failed", rollbackEx);
                    }
                }
                if (!rollbackSuccess || isRecoverableConnectionError(e)) {
                    LOGGER.warning(
                            "Transaction failed in an unrecoverable or tainted state; invalidating"
                                    + " connection.");
                    invalidateConnection();
                    conn = null;
                }
                LOGGER.log(Level.SEVERE, "SQLite transaction execution failed", e);
                throw new DatabaseException("SQLite transaction failed: " + e.getMessage(), e);
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(originalAutoCommit);
                    } catch (SQLException autoCommitEx) {
                        LOGGER.log(
                                Level.WARNING,
                                "Failed to restore auto-commit state, closing connection",
                                autoCommitEx);
                        try {
                            if (!conn.isClosed()) {
                                conn.close();
                            }
                        } catch (SQLException closeEx) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Failed to close connection after auto-commit restore failure",
                                    closeEx);
                        } finally {
                            connection = null;
                        }
                    }
                }
            }
        }
    }

    /**
     * Initializes database tables and indexes if they do not exist, and executes version migrations.
     * Guaranteed to be idempotent and skips redundant DDL if schema is already initialized.
     */
    public void initSchema() {
        if (schemaInitialized.get()) {
            return;
        }
        synchronized (lock) {
            if (schemaInitialized.get()) {
                return;
            }
            LOGGER.info("Starting SQLite database schema initialization and migration check...");
            executeTransaction(
                    conn -> {
                        // 1. Ensure schema_version table exists
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(
                                    """
                                    CREATE TABLE IF NOT EXISTS schema_version (
                                        version INTEGER PRIMARY KEY,
                                        applied_at INTEGER NOT NULL,
                                        description TEXT
                                    );
                                    """);
                        }
                        int currentVersion = getCurrentSchemaVersion(conn);
                        LOGGER.log(
                                Level.INFO,
                                "Current schema version: {0}, target version: {1}",
                                new Object[] {currentVersion, TARGET_SCHEMA_VERSION});
                        if (currentVersion < 1) {
                            migrateToV1(conn);
                        }
                        if (currentVersion < 2) {
                            migrateToV2(conn);
                        }
                        if (currentVersion < 3) {
                            migrateToV3(conn, currentVersion);
                        }
                    });
            schemaInitialized.set(true);
            LOGGER.info("SQLite database schema initialization finished successfully.");
        }
    }

    /**
     * Retrieves the highest applied schema version from the database.
     *
     * @param conn active connection
     * @return current version number, or 0 if uninitialized
     * @throws SQLException on database error
     */
    private int getCurrentSchemaVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version;")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Records a newly applied schema version into schema_version table.
     *
     * @param conn active connection
     * @param version version number
     * @param description version description
     * @throws SQLException on database error
     */
    private void recordSchemaVersion(Connection conn, int version, String description)
            throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement(
                        "INSERT INTO schema_version (version, applied_at, description) VALUES (?,"
                                + " ?, ?);")) {
            ps.setInt(1, version);
            ps.setLong(2, DateTimeUtils.currentTimeMillis());
            ps.setString(3, description);
            ps.executeUpdate();
        }
    }

    /**
     * Applies the initial V1 baseline schema migration.
     *
     * @param conn active connection within transaction
     * @throws SQLException on DDL error
     */
    private void migrateToV1(Connection conn) throws SQLException {
        LOGGER.info("Applying schema migration to V1...");
        try (Statement stmt = conn.createStatement()) {
            // 1. Chat sessions table (migrated from sessions.json)
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id VARCHAR(255) PRIMARY KEY,
                        name TEXT NOT NULL,
                        project_id VARCHAR(255) NOT NULL,
                        agent_id VARCHAR(255) NOT NULL,
                        user_id VARCHAR(255) NOT NULL,
                        channel VARCHAR(255) NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        pinned INTEGER NOT NULL DEFAULT 0,
                        kind VARCHAR(32) NOT NULL DEFAULT 'CHAT',
                        description TEXT,
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
                    );
                    """);
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_sessions_lookup ON chat_sessions"
                            + " (project_id, agent_id, pinned DESC, updated_at DESC);");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_sessions_kind ON chat_sessions"
                            + " (kind, updated_at DESC);");

            // 2. Agent state table (migrated from agent_state.json)
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS agent_state (
                        session_id VARCHAR(255) NOT NULL,
                        state_key VARCHAR(255) NOT NULL,
                        item_index INTEGER NOT NULL DEFAULT 0,
                        state_data TEXT NOT NULL,
                        version INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (session_id, state_key, item_index)
                    );
                    """);
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_agent_state_session ON agent_state"
                            + " (session_id, state_key);");

            // 3. Agent stats table (migrated from agent-stats.json)
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS agent_stats (
                        stat_date VARCHAR(32) NOT NULL,
                        agent_id VARCHAR(255) NOT NULL,
                        message_count INTEGER NOT NULL DEFAULT 0,
                        tool_call_count INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (stat_date, agent_id)
                    );
                    """);

            // Drop legacy tasks table if it exists (tasks are fully unified in chat_sessions with
            // kind=TASK)
            stmt.execute("DROP TABLE IF EXISTS tasks;");

            // 4. Token usage table (migrated from token-usage.json)
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS token_usage (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        usage_date VARCHAR(32) NOT NULL,
                        provider_id VARCHAR(255) NOT NULL,
                        model_id VARCHAR(255) NOT NULL,
                        prompt_tokens INTEGER NOT NULL DEFAULT 0,
                        completion_tokens INTEGER NOT NULL DEFAULT 0,
                        cached_tokens INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    );
                    """);
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_token_usage_lookup ON token_usage"
                            + " (usage_date, provider_id, model_id);");

            // 5. Cron execution history table (migrated from logs/cron-history/{jobId}.json)
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS cron_execution_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        job_id VARCHAR(255) NOT NULL,
                        run_at INTEGER NOT NULL,
                        status VARCHAR(64) NOT NULL,
                        error TEXT,
                        trigger_type VARCHAR(64),
                        created_at INTEGER NOT NULL
                    );
                    """);
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_cron_history_job ON"
                            + " cron_execution_history (job_id, run_at DESC);");

            // 6. Memory notes table (migrated from memory/*.json)
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS memory_notes (
                        agent_id VARCHAR(255) NOT NULL,
                        project_id VARCHAR(255) NOT NULL,
                        scope VARCHAR(32) NOT NULL,
                        key VARCHAR(255) NOT NULL,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (agent_id, project_id, scope, key)
                    );
                    """);
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_memory_notes_lookup ON memory_notes"
                            + " (agent_id, project_id, scope, updated_at DESC);");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_memory_notes_key ON memory_notes" + " (key);");
        }
        recordSchemaVersion(conn, 1, "Initial V1 baseline schema");
        LOGGER.info("Schema migration to V1 completed successfully.");
    }

    /**
     * Applies the V2 schema migration: creates spawn_registry table and associated indexes.
     *
     * @param conn active connection within transaction
     * @throws SQLException on DDL error
     */
    private void migrateToV2(Connection conn) throws SQLException {
        LOGGER.info("Applying schema migration to V2 (spawn_registry)...");
        try (Statement stmt = conn.createStatement()) {
            // 8. Spawn registry table (migrated from spawn-registry.json)
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS spawn_registry (
                        project_id VARCHAR(255) NOT NULL,
                        entry_key VARCHAR(255) NOT NULL,
                        agent_id VARCHAR(255) NOT NULL,
                        session_id VARCHAR(255) NOT NULL,
                        label VARCHAR(255) NOT NULL,
                        depth INTEGER NOT NULL DEFAULT 1,
                        parent_session_id VARCHAR(255) NOT NULL DEFAULT '',
                        status VARCHAR(64) NOT NULL DEFAULT 'RUNNING',
                        created_at_ms INTEGER NOT NULL,
                        updated_at_ms INTEGER NOT NULL,
                        PRIMARY KEY (project_id, entry_key)
                    );
                    """);
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_spawn_registry_parent ON spawn_registry"
                            + " (project_id, parent_session_id);");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_spawn_registry_status ON spawn_registry"
                            + " (project_id, status, updated_at_ms DESC);");
        }
        recordSchemaVersion(conn, 2, "Add spawn_registry table and parent/status indexes");
        LOGGER.info("Schema migration to V2 completed successfully.");
    }

    /**
     * Applies the V3 schema migration: converts all timestamp columns to 64-bit INTEGER (epoch millis).
     *
     * @param conn active connection within transaction
     * @param currentVersion version before running migrations
     * @throws SQLException on DDL error
     */
    private void migrateToV3(Connection conn, int currentVersion) throws SQLException {
        LOGGER.info("Applying schema migration to V3 (INTEGER epoch millis timestamps)...");
        if (currentVersion > 0) {
            // Re-create tables with INTEGER timestamps if upgrading from legacy V1/V2
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS chat_sessions;");
                stmt.execute("DROP TABLE IF EXISTS agent_state;");
                stmt.execute("DROP TABLE IF EXISTS tasks;");
                stmt.execute("DROP TABLE IF EXISTS token_usage;");
                stmt.execute("DROP TABLE IF EXISTS cron_execution_history;");
                stmt.execute("DROP TABLE IF EXISTS memory_notes;");
            }
            migrateToV1(conn);
        }
        recordSchemaVersion(conn, 3, "Migrate all timestamp fields to 64-bit INTEGER epoch millis");
        LOGGER.info("Schema migration to V3 completed successfully.");
    }

    /**
     * Gets the resolved path of the SQLite database file.
     *
     * @return database file path
     */
    public Path getDatabasePath() {
        return databasePath;
    }

    @Override
    public void close() {
        try {
            Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }
        handleShutdown();
    }

    /**
     * Handles clean connection closure and WAL truncate checkpoint during shutdown.
     */
    private void handleShutdown() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        try (Statement stmt = connection.createStatement()) {
                            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                        } catch (SQLException e) {
                            LOGGER.log(Level.FINE, "WAL truncate on shutdown skipped", e);
                        }
                        connection.close();
                        LOGGER.info("DatabaseManager SQLite connection closed.");
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed to close SQLite connection cleanly", e);
                } finally {
                    connection = null;
                }
            }
        }
    }
}
