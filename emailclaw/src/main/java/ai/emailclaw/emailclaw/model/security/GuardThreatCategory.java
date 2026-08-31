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
package ai.emailclaw.emailclaw.model.security;

/**
 * ToolGuard threat category enumeration. Used to classify detected security threats to help users understand the risk type.
 */
public enum GuardThreatCategory {
    /** Dangerous command execution: destructive operations such as rm -rf, dd, format, etc. */
    DANGEROUS_OPERATION(
            "Dangerous Operation", "Detected execution of a destructive system command"),

    /** File access violation: attempting to access sensitive system files or directories. */
    FILE_ACCESS_VIOLATION(
            "File Access Violation",
            "Attempting to access protected sensitive files or directories"),

    /** Network access risk: network request to unknown or blacklisted servers. */
    NETWORK_RISK("Network Risk", "Detected connection to an external network"),

    /** Execution environment risk: environment variable injection, privilege escalation, etc. */
    EXECUTION_RISK(
            "Execution Risk", "Detected environment configuration or privilege escalation risk"),

    /** Data leak risk: attempting to read or transfer sensitive data. */
    DATA_LEAK("Data Leak", "Detected that sensitive data may be accessed or leaked");

    private final String displayName;
    private final String description;

    GuardThreatCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
