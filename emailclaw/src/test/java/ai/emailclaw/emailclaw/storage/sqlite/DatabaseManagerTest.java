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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DatabaseMetaData;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseManagerTest {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManagerTest.class.getName());

    @Test
    @DisplayName("Verify SQLite JDBC driver initialization and schema creation")
    void testSqliteJdbcDriverInitialization(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("test-emailclaw.db");
        DatabaseManager dbManager = new DatabaseManager(dbPath);

        // Verify driver metadata
        dbManager.executeQuery(
                conn -> {
                    DatabaseMetaData meta = conn.getMetaData();
                    assertNotNull(meta.getDriverName());
                    assertTrue(meta.getDriverName().contains("SQLite"));
                    LOGGER.info(
                            "SQLite Driver: "
                                    + meta.getDriverName()
                                    + " "
                                    + meta.getDriverVersion());
                    return null;
                });

        // Verify tables created
        boolean sessionsTableExists =
                dbManager.executeQuery(
                        conn -> {
                            var rs =
                                    conn.getMetaData().getTables(null, null, "chat_sessions", null);
                            return rs.next();
                        });
        assertTrue(sessionsTableExists, "chat_sessions table must exist");

        // Verify tasks table does NOT exist (dead table removed)
        boolean tasksTableExists =
                dbManager.executeQuery(
                        conn -> {
                            var rs = conn.getMetaData().getTables(null, null, "tasks", null);
                            return rs.next();
                        });
        assertFalse(tasksTableExists, "Legacy tasks table should not exist");

        // Verify WAL checkpoint
        dbManager.walCheckpoint(DatabaseManager.CheckpointMode.PASSIVE);

        dbManager.close();
        assertTrue(Files.exists(dbPath));
    }
}
