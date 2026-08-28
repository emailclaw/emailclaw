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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plan - decomposes user goals into an executable list of subtasks.
 *
 * <p>A plan contains the original goal, an ordered list of subtasks, and the overall status. All changes are made through
 * {@link ai.emailclaw.emailclaw.service.plan.PlanService} to ensure state machine consistency and persistence.
 */
public class Plan {
    /** Plan unique identifier (UUID). */
    private String id = UUID.randomUUID().toString();

    /** User's original goal description. */
    private String goal = "";

    /** Belonging session ID. */
    private String sessionId = "";

    /** Belonging project ID. */
    private String projectId = "";

    /** Belonging Agent ID. */
    private String agentId = "";

    /** Overall status. */
    private PlanStatus status = PlanStatus.PENDING;

    /** Ordered list of subtasks. */
    private List<SubTask> subTasks = new ArrayList<>();

    /** ID of the currently executing subtask (empty string if none). */
    private String currentSubTaskId = "";

    /** Creation time. */
    private String createdAt = LocalDateTime.now().toString();

    /** Last update time. */
    private String updatedAt = LocalDateTime.now().toString();

    public Plan() {}

    /**
     * Get the plan unique identifier.
     *
     * @return Plan ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the plan unique identifier.
     *
     * @param id Plan ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the user's original goal description.
     *
     * @return User's original goal description
     */
    public String getGoal() {
        return goal;
    }

    /**
     * Set the user's original goal description.
     *
     * @param goal User's original goal description
     */
    public void setGoal(String goal) {
        this.goal = goal;
    }

    /**
     * Get the belonging session ID.
     *
     * @return Belonging session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Set the belonging session ID.
     *
     * @param sessionId Belonging session ID
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Get the belonging project ID.
     *
     * @return Project ID
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Set the belonging project ID.
     *
     * @param projectId Project ID
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * Get the belonging Agent ID.
     *
     * @return Belonging Agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Set the belonging Agent ID.
     *
     * @param agentId Belonging Agent ID
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Get the overall status.
     *
     * @return Overall status
     */
    public PlanStatus getStatus() {
        return status;
    }

    /**
     * Set the overall status.
     *
     * @param status Overall status
     */
    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    /**
     * Get the ordered list of subtasks.
     *
     * @return Ordered list of subtasks
     */
    public List<SubTask> getSubTasks() {
        return subTasks;
    }

    /**
     * Set the ordered list of subtasks.
     *
     * @param subTasks Ordered list of subtasks
     */
    public void setSubTasks(List<SubTask> subTasks) {
        this.subTasks = subTasks;
    }

    /**
     * Get the ID of the currently executing subtask.
     *
     * @return ID of the currently executing subtask
     */
    public String getCurrentSubTaskId() {
        return currentSubTaskId;
    }

    /**
     * Set the ID of the currently executing subtask.
     *
     * @param currentSubTaskId ID of the currently executing subtask
     */
    public void setCurrentSubTaskId(String currentSubTaskId) {
        this.currentSubTaskId = currentSubTaskId;
    }

    /**
     * Get the creation time.
     *
     * @return Creation time
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * Set the creation time.
     *
     * @param createdAt Creation time
     */
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Get the last update time.
     *
     * @return Last update time
     */
    public String getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Set the last update time.
     *
     * @param updatedAt Last update time
     */
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Create a new plan.
     *
     * @param projectId Belonging Project ID
     * @param agentId   Belonging Agent ID
     * @param sessionId Belonging session ID
     * @param goal      User goal
     */
    public Plan(String projectId, String agentId, String sessionId, String goal) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId == null ? "" : projectId;
        this.agentId = agentId == null ? "" : agentId;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.goal = goal == null ? "" : goal;
        this.status = PlanStatus.PENDING;
        this.subTasks = new ArrayList<>();
        this.createdAt = LocalDateTime.now().toString();
        this.updatedAt = this.createdAt;
    }

    /**
     * Get the currently executing subtask.
     *
     * @return Current subtask, or null if none
     */
    @JsonIgnore
    public SubTask currentSubTask() {
        if (currentSubTaskId == null || currentSubTaskId.isBlank()) {
            return null;
        }
        return findSubTask(currentSubTaskId);
    }

    /**
     * Find a subtask by ID.
     *
     * @param subTaskId Subtask ID
     * @return Matching subtask, or null if not found
     */
    public SubTask findSubTask(String subTaskId) {
        if (subTaskId == null || subTasks == null) {
            return null;
        }
        for (SubTask t : subTasks) {
            if (subTaskId.equals(t.getId())) {
                return t;
            }
        }
        return null;
    }

    /**
     * Get the next pending subtask (the first one with PENDING status and met dependencies).
     *
     * @return Next pending subtask, or null if none
     */
    @JsonIgnore
    public SubTask nextPendingSubTask() {
        if (subTasks == null) {
            return null;
        }
        for (SubTask t : subTasks) {
            if (t.getStatus() == SubTaskStatus.PENDING && t.areDependenciesMet(this)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Determine whether all subtasks have reached a terminal state (COMPLETED / FAILED / SKIPPED).
     */
    @JsonIgnore
    public boolean isAllSubTasksTerminal() {
        if (subTasks == null || subTasks.isEmpty()) {
            return true;
        }
        for (SubTask t : subTasks) {
            if (t.getStatus() == SubTaskStatus.PENDING
                    || t.getStatus() == SubTaskStatus.IN_PROGRESS) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plan plan)) return false;
        return Objects.equals(id, plan.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
