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
package ai.emailclaw.emailclaw.model;

/**
 * Heartbeat scheduling configuration object.
 */
public record HeartbeatConfig(
        boolean enabled,
        int intervalValue,
        String intervalUnit,
        String replyTarget,
        boolean activeHoursEnabled,
        String activeHoursStart,
        String activeHoursEnd) {
    public HeartbeatConfig {
        if (intervalValue == 0) intervalValue = 6;
        if (intervalUnit == null || intervalUnit.isBlank()) intervalUnit = "Hours";
        if (replyTarget == null || replyTarget.isBlank()) replyTarget = "silent";
        if (activeHoursStart == null || activeHoursStart.isBlank()) activeHoursStart = "08:00";
        if (activeHoursEnd == null || activeHoursEnd.isBlank()) activeHoursEnd = "22:00";
    }

    public HeartbeatConfig() {
        this(false, 6, "Hours", "silent", false, "08:00", "22:00");
    }
}
