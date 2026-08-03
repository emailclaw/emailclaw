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

import ai.emailclaw.emailclaw.service.ProjectService;

/**
 * Session metadata object.
 *
 * <p>Used to identify chat session and timestamp information.
 */
public class ChatSessionInfo implements TaskDefinition {
    public static final String KIND_CHAT = "CHAT";
    public static final String KIND_TASK = "TASK";

    private String id = "";
    private String name = "";
    private String projectId = ProjectService.PROJECT_ID_DEFAULT;
    private String agentId = "";
    private String userId = SessionDefaults.LOCAL_USER_ID;
    private String channel = SessionDefaults.DEFAULT_CHANNEL;
    private String createdAt = "";
    private String updatedAt = "";
    private boolean pinned = false;
    private String kind = KIND_CHAT;

    public enum TaskStatus {
        ACTIVE("Active"),
        RUNNING("Running"),
        PAUSED("Paused"),
        COMPLETED("Completed"),
        FAILED("Failed"),
        CANCELLED("Cancelled");

        private final String displayName;

        TaskStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static TaskStatus fromString(String text) {
            if (text == null) return ACTIVE;
            for (TaskStatus status : TaskStatus.values()) {
                if (status.name().equalsIgnoreCase(text)
                        || status.displayName.equalsIgnoreCase(text)) {
                    return status;
                }
            }
            return ACTIVE;
        }
    }

    private String description = "";
    private TaskStatus status = TaskStatus.ACTIVE;

    /**
     * Get session unique identifier.
     *
     * @return session ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set session unique identifier.
     *
     * @param id session ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get affiliated project ID.
     *
     * @return affiliated project ID
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Set affiliated project ID.
     *
     * @param projectId affiliated project ID
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * Get affiliated Agent ID.
     *
     * @return affiliated Agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Set affiliated Agent ID.
     *
     * @param agentId affiliated Agent ID
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Get session name.
     *
     * @return session name
     */
    public String getName() {
        return name;
    }

    /**
     * Set session name.
     *
     * @param name session name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get affiliated user ID.
     *
     * @return affiliated user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Set affiliated user ID.
     *
     * @param userId affiliated user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Get session channel.
     *
     * @return session channel
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Set session channel.
     *
     * @param channel session channel
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /**
     * Get session creation time.
     *
     * @return session creation time
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * Set session creation time.
     *
     * @param createdAt session creation time
     */
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Get session update time.
     *
     * @return session update time
     */
    public String getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Set session update time.
     *
     * @param updatedAt session update time
     */
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean _pinned) {
        pinned = _pinned;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    // Implement TaskDefinition methods
    @Override
    public String id() {
        return getId();
    }

    @Override
    public String projectId() {
        return getProjectId();
    }

    @Override
    public String name() {
        return getName();
    }
}
