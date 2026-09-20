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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChatSessionInfoTest {

    @Test
    @DisplayName("TaskStatus toString should return displayName")
    void testToStringReturnsDisplayName() {
        assertEquals("Active", TaskStatus.ACTIVE.toString());
        assertEquals("Running", TaskStatus.RUNNING.toString());
        assertEquals("Paused", TaskStatus.PAUSED.toString());
        assertEquals("Completed", TaskStatus.COMPLETED.toString());
        assertEquals("Failed", TaskStatus.FAILED.toString());
        assertEquals("Cancelled", TaskStatus.CANCELLED.toString());
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIVE, ACTIVE",
        "Active, ACTIVE",
        "active, ACTIVE",
        " RUNNING , RUNNING",
        "running, RUNNING",
        "PAUSED, PAUSED",
        "Paused, PAUSED",
        "COMPLETED, COMPLETED",
        "completed, COMPLETED",
        "FAILED, FAILED",
        "failed, FAILED",
        "CANCELLED, CANCELLED",
        "Cancelled, CANCELLED",
        "CANCELED, CANCELLED",
        "canceled, CANCELLED",
        "Canceled, CANCELLED"
    })
    @DisplayName("TaskStatus fromString parses various name, displayName and alias variants")
    void testFromStringVariants(String input, String expectedName) {
        TaskStatus parsed = TaskStatus.fromString(input);
        assertEquals(expectedName, parsed.name());
    }

    @Test
    @DisplayName("TaskStatus fromString falls back to ACTIVE on null, empty or unknown text")
    void testFromStringFallback() {
        assertEquals(TaskStatus.ACTIVE, TaskStatus.fromString(null));
        assertEquals(TaskStatus.ACTIVE, TaskStatus.fromString(""));
        assertEquals(TaskStatus.ACTIVE, TaskStatus.fromString("   "));
        assertEquals(TaskStatus.ACTIVE, TaskStatus.fromString("UNKNOWN_STATUS"));
    }

    @Test
    @DisplayName("ChatSessionInfo default values and getters/setters")
    void testSessionDefaults() {
        ChatSessionInfo session = new ChatSessionInfo();
        assertEquals(TaskStatus.ACTIVE, session.getStatus());
        assertEquals(ChatSessionInfo.KIND_CHAT, session.getKind());
        assertEquals(0L, session.getCreatedAt());
        assertEquals(0L, session.getUpdatedAt());
        assertFalse(session.isPinned());

        session.setStatus(TaskStatus.RUNNING);
        assertEquals(TaskStatus.RUNNING, session.getStatus());
        assertEquals("Running", session.getStatus().toString());
        assertEquals("RUNNING", session.getStatus().name());

        session.setCreatedAt(1700000000000L);
        session.setUpdatedAt(1700000001000L);
        assertEquals(1700000000000L, session.getCreatedAt());
        assertEquals(1700000001000L, session.getUpdatedAt());

        session.setPinned(true);
        assertTrue(session.isPinned());
    }
}
