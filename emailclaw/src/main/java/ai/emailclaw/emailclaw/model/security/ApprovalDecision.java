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
 * ToolGuard approval decision enum. Represents the user's final decision on a tool call pending approval.
 */
public enum ApprovalDecision {
    /** Approve execution: User agrees to execute the tool call. */
    APPROVE("Approve"),

    /** Deny execution: User disagrees to execute the tool call. */
    DENY("Deny"),

    /** Execute after modification: User allows executing the tool call after modifying parameters. */
    // Currently not supported MODIFY("Execute after modification"),

    /** No response/timeout: User didn't make a decision within the specified time. */
    TIMEOUT("Timeout without response");

    private final String displayName;

    ApprovalDecision(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Determine whether the decision allows the tool execution.
     *
     * @return Returns true if execution is allowed
     */
    public boolean isExecutionAllowed() {
        return this == APPROVE
        // Currently not supported   		|| this == MODIFY
        ;
    }
}
