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
package ai.emailclaw.emailclaw.service.plan;

import ai.emailclaw.emailclaw.model.plan.Plan;
import ai.emailclaw.emailclaw.model.plan.PlanNotebook;
import ai.emailclaw.emailclaw.model.plan.PlanStatus;
import ai.emailclaw.emailclaw.model.plan.SubTask;
import ai.emailclaw.emailclaw.model.plan.SubTaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plan service - manages plan creation, state machine transitions, hint generation and broadcasting.
 *
 * <p>Core responsibilities:
 * <ul>
 *   <li>Plan CRUD: Create, Query, Delete</li>
 *   <li>State machine gating: Validate legal transitions of PlanStatus/SubTaskStatus</li>
 *   <li>Hint generation: Generate structured hint text based on current plan state</li>
 *   <li>PlanNotebook construction: Provide lightweight extracts for Hint injection</li>
 *   <li>Event broadcasting: Asynchronously notify the UI layer via {@link PlanBroadcaster}</li>
 * </ul>
 *
 * <p>State transition rules:
 * <ul>
 *   <li>Plan: PENDING -> IN_PROGRESS -> COMPLETED / FAILED / CANCELLED</li>
 *   <li>SubTask: PENDING -> IN_PROGRESS -> COMPLETED / FAILED / SKIPPED</li>
 * </ul>
 */
public class PlanService {
    private static final Logger LOGGER = Logger.getLogger(PlanService.class.getName());

    private final PlanStore planStore;
    private final PlanBroadcaster broadcaster;
    private final PlanHintCache hintCache;

    public PlanService(PlanStore planStore, PlanBroadcaster broadcaster, PlanHintCache hintCache) {
        this.planStore = planStore;
        this.broadcaster = broadcaster;
        this.hintCache = hintCache;
        LOGGER.info("PlanService initialization completed");
    }

    // ========== Plan CRUD ==========

    /**
     * Create a new plan.
     *
     * @param agentId   Owning Agent ID
     * @param sessionId Owning session ID
     * @param goal      User goal
     * @return The created plan
     */
    public Plan createPlan(String agentId, String sessionId, String goal) {
        Plan plan = new Plan(agentId, sessionId, goal);
        plan.setStatus(PlanStatus.PENDING);
        plan.setUpdatedAt(LocalDateTime.now().toString());
        planStore.save(plan);
        broadcaster.broadcastPlanCreated(plan);
        LOGGER.log(
                Level.INFO,
                "Plan created: id={0}, goal={1}",
                new Object[] {plan.getId(), truncate(goal, 80)});
        return plan;
    }

    /**
     * Get a plan by ID.
     *
     * @param planId Plan ID
     * @return Matching plan
     */
    public Optional<Plan> getPlan(String planId) {
        return planStore.findById(planId);
    }

    /**
     * Get the current active plan by session ID.
     *
     * @param sessionId Session ID
     * @return Matching plan (if there are multiple, return the latest)
     */
    public Optional<Plan> getPlanBySession(String sessionId) {
        return planStore.findBySessionId(sessionId);
    }

    /**
     * List all plans by Agent ID.
     *
     * @param agentId Agent ID
     * @return List of plans
     */
    public List<Plan> listPlans(String agentId) {
        return planStore.findByAgentId(agentId);
    }

    /**
     * Delete the specified plan.
     *
     * @param planId Plan ID
     */
    public void deletePlan(String planId) {
        planStore.delete(planId);
        hintCache.invalidate(planId);
        LOGGER.log(Level.INFO, "Plan deleted: id={0}", planId);
    }

    // ========== SubTask Operations ==========

    /**
     * Append a subtask to the plan.
     *
     * @param planId      Plan ID
     * @param title       Subtask title
     * @param description Subtask description
     * @return The created subtask
     */
    public SubTask addSubTask(String planId, String title, String description) {
        Plan plan = planStore.findById(planId).orElse(null);
        if (plan == null) {
            LOGGER.warning("Failed to add subtask: Plan does not exist planId=" + planId);
            return null;
        }
        int seq = plan.getSubTasks().size() + 1;
        SubTask task = new SubTask(seq, title, description);
        plan.getSubTasks().add(task);
        plan.setUpdatedAt(LocalDateTime.now().toString());
        planStore.save(plan);
        hintCache.invalidate(planId);
        broadcaster.broadcastPlanUpdated(plan);
        LOGGER.log(
                Level.FINE, "Subtask added: planId={0}, title={1}", new Object[] {planId, title});
        return task;
    }

    /**
     * Batch set the subtask list (overwrite).
     *
     * @param planId  Plan ID
     * @param subTasks List of subtasks
     */
    public void setSubTasks(String planId, List<SubTask> subTasks) {
        Plan plan = planStore.findById(planId).orElse(null);
        if (plan == null) {
            LOGGER.warning("Failed to set subtask list: Plan does not exist planId=" + planId);
            return;
        }
        plan.setSubTasks(subTasks);
        plan.setUpdatedAt(LocalDateTime.now().toString());
        planStore.save(plan);
        hintCache.invalidate(planId);
        broadcaster.broadcastPlanUpdated(plan);
    }

    // ========== State Machine Transitions ==========

    /**
     * Start plan execution: Change plan status from PENDING to IN_PROGRESS.
     *
     * @param planId Plan ID
     * @return true if successful
     */
    public boolean startPlan(String planId) {
        Plan plan = planStore.findById(planId).orElse(null);
        if (plan == null) {
            LOGGER.warning("Failed to start plan: Plan does not exist planId=" + planId);
            return false;
        }
        if (plan.getStatus() != PlanStatus.PENDING) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to start plan: Current status does not allow transition, status={0}",
                    plan.getStatus());
            return false;
        }
        plan.setStatus(PlanStatus.IN_PROGRESS);
        plan.setUpdatedAt(LocalDateTime.now().toString());
        // Automatically set the first pending subtask to IN_PROGRESS
        SubTask first = plan.nextPendingSubTask();
        if (first != null) {
            first.setStatus(SubTaskStatus.IN_PROGRESS);
            plan.setCurrentSubTaskId(first.getId());
        }
        planStore.save(plan);
        hintCache.invalidate(planId);
        broadcaster.broadcastPlanUpdated(plan);
        LOGGER.log(Level.INFO, "Plan execution started: id={0}", planId);
        return true;
    }

    /**
     * Update subtask status and automatically advance the plan.
     *
     * @param planId   Plan ID
     * @param subTaskId Subtask ID
     * @param newStatus New status
     * @param result    Execution result (optional)
     * @return true if transition was successful
     */
    public boolean updateSubTaskStatus(
            String planId, String subTaskId, SubTaskStatus newStatus, String result) {
        Plan plan = planStore.findById(planId).orElse(null);
        if (plan == null) {
            LOGGER.warning("Failed to update subtask status: Plan does not exist planId=" + planId);
            return false;
        }
        SubTask task = plan.findSubTask(subTaskId);
        if (task == null) {
            LOGGER.warning(
                    "Failed to update subtask status: Subtask does not exist subTaskId="
                            + subTaskId);
            return false;
        }
        if (!isValidSubTaskTransition(task.getStatus(), newStatus)) {
            LOGGER.log(
                    Level.WARNING,
                    "Illegal subtask status transition: {0} -> {1}",
                    new Object[] {task.getStatus(), newStatus});
            return false;
        }
        SubTaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        if (result != null) {
            task.setResult(result);
        }
        plan.setUpdatedAt(LocalDateTime.now().toString());
        // If the subtask is completed, automatically advance to the next pending one
        if (newStatus == SubTaskStatus.COMPLETED || newStatus == SubTaskStatus.SKIPPED) {
            SubTask next = plan.nextPendingSubTask();
            if (next != null) {
                next.setStatus(SubTaskStatus.IN_PROGRESS);
                plan.setCurrentSubTaskId(next.getId());
            } else {
                plan.setCurrentSubTaskId("");
                // All subtasks have reached a terminal state
                if (plan.isAllSubTasksTerminal()) {
                    boolean hasFailure =
                            plan.getSubTasks().stream()
                                    .anyMatch(t -> t.getStatus() == SubTaskStatus.FAILED);
                    plan.setStatus(hasFailure ? PlanStatus.FAILED : PlanStatus.COMPLETED);
                    broadcaster.broadcastPlanTerminal(plan);
                }
            }
        }
        planStore.save(plan);
        hintCache.invalidate(planId);
        broadcaster.broadcastSubTaskChanged(plan, task);
        LOGGER.log(
                Level.INFO,
                "Subtask status updated: planId={0}, subTaskId={1}, {2} -> {3}",
                new Object[] {planId, subTaskId, oldStatus, newStatus});
        return true;
    }

    /**
     * Cancel the plan.
     *
     * @param planId Plan ID
     * @return true if successful
     */
    public boolean cancelPlan(String planId) {
        Plan plan = planStore.findById(planId).orElse(null);
        if (plan == null) {
            return false;
        }
        if (plan.getStatus() == PlanStatus.COMPLETED
                || plan.getStatus() == PlanStatus.CANCELLED
                || plan.getStatus() == PlanStatus.FAILED) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to cancel plan: Already reached terminal state, status={0}",
                    plan.getStatus());
            return false;
        }
        plan.setStatus(PlanStatus.CANCELLED);
        plan.setUpdatedAt(LocalDateTime.now().toString());
        // Mark all IN_PROGRESS/PENDING subtasks as SKIPPED
        for (SubTask t : plan.getSubTasks()) {
            if (t.getStatus() == SubTaskStatus.IN_PROGRESS
                    || t.getStatus() == SubTaskStatus.PENDING) {
                t.setStatus(SubTaskStatus.SKIPPED);
            }
        }
        plan.setCurrentSubTaskId("");
        planStore.save(plan);
        hintCache.invalidate(planId);
        broadcaster.broadcastPlanTerminal(plan);
        LOGGER.log(Level.INFO, "Plan cancelled: id={0}", planId);
        return true;
    }

    // ========== Subtask State Machine Validation ==========

    private boolean isValidSubTaskTransition(SubTaskStatus from, SubTaskStatus to) {
        return switch (from) {
            case PENDING -> to == SubTaskStatus.IN_PROGRESS || to == SubTaskStatus.SKIPPED;
            case IN_PROGRESS ->
                    to == SubTaskStatus.COMPLETED
                            || to == SubTaskStatus.FAILED
                            || to == SubTaskStatus.SKIPPED;
            case COMPLETED, FAILED, SKIPPED -> false;
        };
    }

    // ========== Hint Generation (for PlanToHintMiddleware) ==========

    /**
     * Generate structured hint text based on current plan state.
     *
     * <p>The hint content includes: Plan goal, overall progress, current subtask, pending list, and completed/failed summaries.
     *
     * @param plan Plan object
     * @return Hint text, or an empty string if the plan is invalid
     */
    public String generateHint(Plan plan) {
        if (plan == null) {
            return "";
        }
        if (plan.getStatus() == PlanStatus.PENDING) {
            return "[Plan Pending] Goal: " + plan.getGoal();
        }
        if (plan.getStatus() == PlanStatus.COMPLETED) {
            return "[Plan Completed] All subtasks executed. Goal: " + plan.getGoal();
        }
        if (plan.getStatus() == PlanStatus.FAILED) {
            return "[Plan Failed] There are failed subtasks. Goal: " + plan.getGoal();
        }
        if (plan.getStatus() == PlanStatus.CANCELLED) {
            return "";
        }
        // IN_PROGRESS: build detailed hint
        StringBuilder sb = new StringBuilder();
        sb.append("[Current Plan] ").append(plan.getGoal()).append("\n\n");

        int total = plan.getSubTasks().size();
        long done =
                plan.getSubTasks().stream()
                        .filter(
                                t ->
                                        t.getStatus() == SubTaskStatus.COMPLETED
                                                || t.getStatus() == SubTaskStatus.SKIPPED)
                        .count();
        sb.append("Progress: ").append(done).append("/").append(total).append("\n\n");

        // Current subtask
        SubTask current = plan.currentSubTask();
        if (current != null) {
            sb.append("[Current Task] [")
                    .append(current.getSeq())
                    .append("/")
                    .append(total)
                    .append("] ")
                    .append(current.getTitle());
            if (!current.getDescription().isBlank()) {
                sb.append("\n").append(current.getDescription());
            }
            sb.append("\n\n");
        }

        // Pending list
        List<SubTask> pending =
                plan.getSubTasks().stream()
                        .filter(t -> t.getStatus() == SubTaskStatus.PENDING)
                        .toList();
        if (!pending.isEmpty()) {
            sb.append("[Pending Tasks]\n");
            for (SubTask t : pending) {
                sb.append("  [Pending] ").append(t.getTitle());
                if (t.areDependenciesMet(plan)) {
                    sb.append(" (Dependencies met)");
                } else {
                    sb.append(" (Waiting for prerequisite tasks)");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Completed task summary
        List<SubTask> completed =
                plan.getSubTasks().stream()
                        .filter(t -> t.getStatus() == SubTaskStatus.COMPLETED)
                        .toList();
        if (!completed.isEmpty()) {
            sb.append("[Completed]\n");
            for (SubTask t : completed) {
                sb.append("  ✓ ").append(t.getTitle()).append("\n");
            }
            sb.append("\n");
        }

        // Failed tasks
        List<SubTask> failed =
                plan.getSubTasks().stream()
                        .filter(t -> t.getStatus() == SubTaskStatus.FAILED)
                        .toList();
        if (!failed.isEmpty()) {
            sb.append("[Execution Failed]\n");
            for (SubTask t : failed) {
                sb.append("  ✗ ").append(t.getTitle());
                if (!t.getResult().isBlank()) {
                    sb.append(": ").append(t.getResult());
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Build a lightweight PlanNotebook based on the plan (for external UI or cache use).
     *
     * @param plan Plan object
     * @return PlanNotebook instance
     */
    public PlanNotebook buildNotebook(Plan plan) {
        if (plan == null) {
            return new PlanNotebook();
        }
        PlanNotebook notebook = new PlanNotebook();
        notebook.setPlanId(plan.getId());
        notebook.setPlanGoal(plan.getGoal());
        int total = plan.getSubTasks().size();
        long done =
                plan.getSubTasks().stream()
                        .filter(
                                t ->
                                        t.getStatus() == SubTaskStatus.COMPLETED
                                                || t.getStatus() == SubTaskStatus.SKIPPED)
                        .count();
        notebook.setOverallProgress(done + "/" + total + " subtasks completed");
        notebook.setCurrentHint(generateHint(plan));
        for (SubTask t : plan.getSubTasks()) {
            if (t.getStatus() == SubTaskStatus.COMPLETED) {
                notebook.getCompletedTaskIds().add(t.getId());
            } else if (t.getStatus() == SubTaskStatus.FAILED) {
                notebook.getFailedTaskIds().add(t.getId());
            }
        }
        return notebook;
    }

    // ========== Utility Methods ==========

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /** Get the underlying PlanStore reference. */
    public PlanStore planStore() {
        return planStore;
    }

    /** Get the underlying PlanBroadcaster reference. */
    public PlanBroadcaster broadcaster() {
        return broadcaster;
    }

    /** Get the underlying PlanHintCache reference. */
    public PlanHintCache hintCache() {
        return hintCache;
    }
}
