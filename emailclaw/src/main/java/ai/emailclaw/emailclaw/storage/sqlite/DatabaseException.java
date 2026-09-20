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

import java.sql.SQLException;

/**
 * Industrial-grade unchecked exception for SQLite database operations.
 *
 * <p>Preserves native vendor error codes, SQLState strings, and underlying causes to enable
 * fine-grained error diagnosis (e.g. constraints, busy locks, read-only status).
 */
public class DatabaseException extends RuntimeException {

    private final String sqlState;
    private final int errorCode;

    /**
     * Constructs a new DatabaseException with message and SQLException cause.
     *
     * @param message failure message
     * @param cause underlying SQLException
     */
    public DatabaseException(String message, SQLException cause) {
        super(message, cause);
        this.sqlState = cause != null ? cause.getSQLState() : null;
        this.errorCode = cause != null ? cause.getErrorCode() : 0;
    }

    /**
     * Constructs a new DatabaseException with message and general Throwable cause.
     *
     * @param message failure message
     * @param cause underlying cause
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
        if (cause instanceof SQLException sqlEx) {
            this.sqlState = sqlEx.getSQLState();
            this.errorCode = sqlEx.getErrorCode();
        } else {
            this.sqlState = null;
            this.errorCode = 0;
        }
    }

    /**
     * Constructs a new DatabaseException with message only.
     *
     * @param message failure message
     */
    public DatabaseException(String message) {
        super(message);
        this.sqlState = null;
        this.errorCode = 0;
    }

    /**
     * Returns the SQLState string from the underlying driver if available.
     *
     * @return SQLState string or null
     */
    public String getSqlState() {
        return sqlState;
    }

    /**
     * Returns the vendor-specific database error code if available.
     *
     * @return integer error code
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Indicates whether the error was caused by a unique constraint or check constraint failure.
     *
     * @return true if error represents SQLITE_CONSTRAINT (19)
     */
    public boolean isConstraintViolation() {
        return errorCode == 19;
    }

    /**
     * Indicates whether the error was caused by database busy or lock contention.
     *
     * @return true if error represents SQLITE_BUSY (5) or SQLITE_LOCKED (6)
     */
    public boolean isBusyOrLocked() {
        return errorCode == 5 || errorCode == 6;
    }
}
