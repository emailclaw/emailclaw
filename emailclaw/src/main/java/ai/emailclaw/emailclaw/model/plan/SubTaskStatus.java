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
package ai.emailclaw.emailclaw.model.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Subtask status.
 *
 * <p>State machine:
 * <pre>
 *   PENDING ──→ IN_PROGRESS ──→ COMPLETED
 *       │                             │
 *       └──SKIPPED────────────────────┘
 *                        └──→ FAILED
 * </pre>
 */
public enum SubTaskStatus {
    /** Waiting for execution. */
    PENDING("pending"),
    /** Currently executing. */
    IN_PROGRESS("in_progress"),
    /** Execution successful. */
    COMPLETED("completed"),
    /** Execution failed. */
    FAILED("failed"),
    /** Skipped due to dependencies or decision. */
    SKIPPED("skipped");

    private final String value;

    SubTaskStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static SubTaskStatus fromValue(String value) {
        if (value == null) {
            return PENDING;
        }
        for (SubTaskStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        return PENDING;
    }
}
