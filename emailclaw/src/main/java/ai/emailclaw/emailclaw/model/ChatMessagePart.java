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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured content part of a chat message.
 */
public class ChatMessagePart {

    public static final String TEXT = "text";
    public static final String THINKING = "thinking";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String HINT = "hint";
    public static final String ERROR = "error";
    public static final String SUB_AGENT_EVENT = "sub_agent_event";

    /** Content part type. */
    private String type = TEXT;

    /** Content part title. */
    private String title = "";

    /** Text content. */
    private String text = "";

    /** Content part unique identifier. */
    private String id = "";

    /** Tool call name. */
    private String toolName = "";

    /** Tool call input parameters. */
    private Map<String, Object> toolInput = new HashMap<>();

    /** List of sub-content parts. */
    private List<ChatMessagePart> subParts = new ArrayList<>();

    /** Whether large volume data has been offloaded to disk. */
    private boolean offloaded = false;

    /** The complete file path offloaded to disk. */
    private String offloadPath = "";

    /** Get content part type. */
    public String getType() {
        return type;
    }

    /** Set content part type. */
    public void setType(String type) {
        this.type = type;
    }

    /** Get content part title. */
    public String getTitle() {
        return title;
    }

    /** Set content part title. */
    public void setTitle(String title) {
        this.title = title;
    }

    /** Get text content. */
    public String getText() {
        return text;
    }

    /** Set text content. */
    public void setText(String text) {
        this.text = text;
    }

    /** Get content part unique identifier. */
    public String getId() {
        return id;
    }

    /** Set content part unique identifier. */
    public void setId(String id) {
        this.id = id;
    }

    /** Get tool call name. */
    public String getToolName() {
        return toolName;
    }

    /** Set tool call name. */
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    /** Get tool call input parameters. */
    public Map<String, Object> getToolInput() {
        return toolInput;
    }

    /** Set tool call input parameters. */
    public void setToolInput(Map<String, Object> toolInput) {
        this.toolInput = toolInput;
    }

    /** Get list of sub-content parts. */
    public List<ChatMessagePart> getSubParts() {
        return subParts;
    }

    /** Set list of sub-content parts. */
    public void setSubParts(List<ChatMessagePart> subParts) {
        this.subParts = subParts;
    }

    /** Get whether it is offloaded. */
    public boolean isOffloaded() {
        return offloaded;
    }

    /** Set whether it is offloaded. */
    public void setOffloaded(boolean offloaded) {
        this.offloaded = offloaded;
    }

    /** Get offload path. */
    public String getOffloadPath() {
        return offloadPath;
    }

    /** Set offload path. */
    public void setOffloadPath(String offloadPath) {
        this.offloadPath = offloadPath;
    }

    public ChatMessagePart() {}

    public ChatMessagePart(String type, String title, String text) {
        this.type = normalizeType(type);
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
    }

    public ChatMessagePart(
            String type,
            String title,
            String text,
            Map<String, Object> toolInput,
            List<ChatMessagePart> subParts) {
        this(type, title, text);
        this.toolInput = toolInput != null ? new HashMap<>(toolInput) : new HashMap<>();
        this.subParts = subParts != null ? new ArrayList<>(subParts) : new ArrayList<>();
    }

    public static ChatMessagePart text(String text) {
        return new ChatMessagePart(TEXT, "", text);
    }

    public static ChatMessagePart block(String type, String title, String text) {
        return new ChatMessagePart(type, title, text);
    }

    public ChatMessagePart copy() {
        ChatMessagePart copy = new ChatMessagePart(type, title, text);
        copy.id = id == null ? "" : id;
        copy.toolName = toolName == null ? "" : toolName;
        copy.toolInput = new HashMap<>(toolInput != null ? toolInput : new HashMap<>());
        copy.subParts = new ArrayList<>();
        for (ChatMessagePart sp : subParts != null ? subParts : List.<ChatMessagePart>of()) {
            copy.subParts.add(sp.copy());
        }
        copy.offloaded = this.offloaded;
        copy.offloadPath = this.offloadPath == null ? "" : this.offloadPath;
        return copy;
    }

    public void append(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        text = (text == null ? "" : text) + delta;
    }

    public boolean isBlank() {
        return text == null || text.isBlank();
    }

    public boolean sameStreamTarget(ChatMessagePart other) {
        if (other == null) {
            return false;
        }
        return normalizeType(type).equals(normalizeType(other.type))
                && safe(id).equals(safe(other.id))
                && safe(toolName).equals(safe(other.toolName));
    }

    public static String normalizeType(String type) {
        return type == null || type.isBlank() ? TEXT : type;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
