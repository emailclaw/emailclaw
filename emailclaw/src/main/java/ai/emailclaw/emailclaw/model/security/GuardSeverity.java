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
 * ToolGuard threat severity enumeration. Used to indicate the security risk level of tool calls, from low to high:
 * NONE, LOW, MEDIUM, HIGH, CRITICAL.
 *
 * <p>The higher the level, the greater the risk, which may require manual user approval.
 */
public enum GuardSeverity {
    /** No threat, can be executed directly. */
    NONE(0, "No Threat"),

    /** Low risk, recommended to inform user but can be executed automatically. */
    LOW(1, "Low Risk"),

    /** Medium risk, recommended to execute after user approval. */
    MEDIUM(2, "Medium Risk"),

    /** High risk, must be executed after user approval. */
    HIGH(3, "High Risk"),

    /** Critical risk, strongly recommended to deny or only execute in advanced mode. */
    CRITICAL(4, "Critical Risk");

    private final int level;
    private final String displayName;

    GuardSeverity(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Determines whether the current level is more severe than another level.
     *
     * @param other the level to compare
     * @return true if the current level is more severe
     */
    public boolean isMoreSevereThan(GuardSeverity other) {
        return this.level > other.level;
    }

    /**
     * Determines whether user approval is required. Only HIGH and CRITICAL levels require approval.
     *
     * @return true if approval is required
     */
    public boolean requiresApproval() {
        return this == HIGH || this == CRITICAL;
    }
}
