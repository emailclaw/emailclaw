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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A single subtask in a plan.
 *
 * <p>Each subtask represents an independently executable unit of work, containing a title, description, dependencies, and execution results.
 */
public class SubTask {
    /** Subtask unique identifier (UUID). */
    private String id = UUID.randomUUID().toString();

    /** Execution sequence number, reflecting the agreed execution order in the plan. */
    private int seq = 0;

    /** Subtask title (short description, e.g. "Refactor DatabaseService class"). */
    private String title = "";

    /** Subtask detailed description. */
    private String description = "";

    /** Current status. */
    private SubTaskStatus status = SubTaskStatus.PENDING;

    /** List of dependent subtask IDs; this task can only be executed after all dependencies are completed. */
    private List<String> dependencies = new ArrayList<>();

    /** Execution result or output summary. */
    private String result = "";

    /** Agent name responsible for executing this subtask (empty string means executed by current agent). */
    private String assignedAgent = "";

    public SubTask() {}

    /**
     * Get the subtask unique identifier.
     *
     * @return Subtask ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the subtask unique identifier.
     *
     * @param id Subtask ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the execution sequence number.
     *
     * @return Execution sequence number
     */
    public int getSeq() {
        return seq;
    }

    /**
     * Set the execution sequence number.
     *
     * @param seq Execution sequence number
     */
    public void setSeq(int seq) {
        this.seq = seq;
    }

    /**
     * Get the subtask title.
     *
     * @return Subtask title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the subtask title.
     *
     * @param title Subtask title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get the subtask detailed description.
     *
     * @return Subtask detailed description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the subtask detailed description.
     *
     * @param description Subtask detailed description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the current status.
     *
     * @return Current status
     */
    public SubTaskStatus getStatus() {
        return status;
    }

    /**
     * Set the current status.
     *
     * @param status Current status
     */
    public void setStatus(SubTaskStatus status) {
        this.status = status;
    }

    /**
     * Get the list of dependent subtask IDs.
     *
     * @return Dependent subtask ID list
     */
    public List<String> getDependencies() {
        return dependencies;
    }

    /**
     * Set the list of dependent subtask IDs.
     *
     * @param dependencies Dependent subtask ID list
     */
    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    /**
     * Get the execution result or output summary.
     *
     * @return Execution result or output summary
     */
    public String getResult() {
        return result;
    }

    /**
     * Set the execution result or output summary.
     *
     * @param result Execution result or output summary
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
     * Get the agent name responsible for executing this subtask.
     *
     * @return Agent name responsible for executing this subtask
     */
    public String getAssignedAgent() {
        return assignedAgent;
    }

    /**
     * Set the agent name responsible for executing this subtask.
     *
     * @param assignedAgent Agent name responsible for executing this subtask
     */
    public void setAssignedAgent(String assignedAgent) {
        this.assignedAgent = assignedAgent;
    }

    /**
     * Create a subtask with a specified title and description.
     *
     * @param seq         Execution sequence number
     * @param title       Title
     * @param description Detailed description
     */
    public SubTask(int seq, String title, String description) {
        this.id = UUID.randomUUID().toString();
        this.seq = seq;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
    }

    /**
     * Determine whether all preconditions of the current subtask have been met (all COMPLETED or SKIPPED).
     *
     * @param plan Belonging plan, used to query the status of dependent subtasks
     * @return Returns true if all dependencies are completed or skipped
     */
    @JsonIgnore
    public boolean areDependenciesMet(Plan plan) {
        if (dependencies == null || dependencies.isEmpty() || plan == null) {
            return true;
        }
        if (plan.getSubTasks() == null) {
            return true;
        }
        for (String depId : dependencies) {
            SubTask dep = plan.findSubTask(depId);
            if (dep == null) {
                continue;
            }
            if (dep.getStatus() != SubTaskStatus.COMPLETED
                    && dep.getStatus() != SubTaskStatus.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubTask other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
