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

import java.util.ArrayList;
import java.util.List;

/**
 * Chat message record object —— A simple UI display DTO, no longer carrying the content field.
 *
 * <p>The authoritative data source for session persistence is AgentScope Msg, ChatMessageRecord is only used as a display carrier in the UI layer.
 */
public class ChatMessageRecord {
    private String role = "";
    private String createdAt = "";
    private List<ChatMessagePart> parts = new ArrayList<>();

    public ChatMessageRecord() {}

    public ChatMessageRecord(String role, List<ChatMessagePart> parts, String createdAt) {
        this.role = role;
        this.createdAt = createdAt;
        this.parts = copyParts(parts);
    }

    /**
     * Get message role.
     *
     * @return message role
     */
    public String getRole() {
        return role;
    }

    /**
     * Set message role.
     *
     * @param role message role
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Get message creation time.
     *
     * @return message creation time
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * Set message creation time.
     *
     * @param createdAt message creation time
     */
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Get message content parts list.
     *
     * @return message content parts list
     */
    public List<ChatMessagePart> getParts() {
        return parts;
    }

    /**
     * Set message content parts list.
     *
     * @param parts message content parts list
     */
    public void setParts(List<ChatMessagePart> parts) {
        this.parts = parts;
    }

    public boolean isBlank() {
        return effectiveContent().isBlank();
    }

    public String effectiveContent() {
        return textOfParts(parts);
    }

    public static List<ChatMessagePart> copyParts(List<ChatMessagePart> source) {
        List<ChatMessagePart> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (ChatMessagePart part : source) {
            if (part != null) {
                copied.add(part.copy());
            }
        }
        return copied;
    }

    public static String textOfParts(List<ChatMessagePart> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessagePart part : parts) {
            if (part == null || part.getText() == null || part.getText().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(part.getText());
        }
        return sb.toString();
    }
}
