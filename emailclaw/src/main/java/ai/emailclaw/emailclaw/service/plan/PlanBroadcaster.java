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
import ai.emailclaw.emailclaw.model.plan.PlanStatus;
import ai.emailclaw.emailclaw.model.plan.SubTask;
import ai.emailclaw.emailclaw.service.MessageBusService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plan broadcaster - asynchronously notifies subscribers of plan status changes via {@link MessageBusService}.
 *
 * <p>Event types:
 * <ul>
 *   <li>{@code plan.created} — Plan created</li>
 *   <li>{@code plan.updated} — Plan properties updated</li>
 *   <li>{@code plan.completed} — Plan completed</li>
 *   <li>{@code plan.failed} — Plan failed</li>
 *   <li>{@code plan.cancelled} — Plan cancelled</li>
 *   <li>{@code subtask.status_changed} — Subtask status changed</li>
 * </ul>
 *
 * <p>The message body is a Map, containing key fields such as planId, sessionId, status, etc.
 */
public class PlanBroadcaster {

    private static final Logger LOGGER = Logger.getLogger(PlanBroadcaster.class.getName());

    /**
     * Message bus service.
     */
    private final MessageBusService messageBusService;

    /**
     * Whether broadcasting is enabled (can be turned off to reduce noise).
     */
    private volatile boolean enabled = true;

    private static final String EVENT_PLAN_CREATED = "plan.created";

    private static final String EVENT_PLAN_UPDATED = "plan.updated";

    private static final String EVENT_PLAN_COMPLETED = "plan.completed";

    private static final String EVENT_PLAN_FAILED = "plan.failed";

    private static final String EVENT_PLAN_CANCELLED = "plan.cancelled";

    private static final String EVENT_SUBTASK_CHANGED = "subtask.status_changed";

    public PlanBroadcaster(MessageBusService messageBusService) {
        this.messageBusService = messageBusService;
        LOGGER.info("PlanBroadcaster initialization completed");
    }

    /**
     * Broadcast plan created event.
     *
     * @param plan Created plan
     */
    public void broadcastPlanCreated(Plan plan) {
        if (!enabled || plan == null) return;
        publish(EVENT_PLAN_CREATED, plan, null, null);
    }

    /**
     * Broadcast plan updated event.
     *
     * @param plan Updated plan
     */
    public void broadcastPlanUpdated(Plan plan) {
        if (!enabled || plan == null) return;
        publish(EVENT_PLAN_UPDATED, plan, null, null);
    }

    /**
     * Broadcast plan terminal state event (COMPLETED/FAILED/CANCELLED).
     *
     * @param plan Plan that has reached a terminal state
     */
    public void broadcastPlanTerminal(Plan plan) {
        if (!enabled || plan == null) return;
        String eventType;
        if (plan.getStatus() == PlanStatus.COMPLETED) {
            eventType = EVENT_PLAN_COMPLETED;
        } else if (plan.getStatus() == PlanStatus.FAILED) {
            eventType = EVENT_PLAN_FAILED;
        } else if (plan.getStatus() == PlanStatus.CANCELLED) {
            eventType = EVENT_PLAN_CANCELLED;
        } else {
            eventType = EVENT_PLAN_UPDATED;
        }
        publish(eventType, plan, null, null);
    }

    /**
     * Broadcast subtask status changed event.
     *
     * @param plan    Owning plan
     * @param subTask Subtask whose status changed
     */
    public void broadcastSubTaskChanged(Plan plan, SubTask subTask) {
        if (!enabled || plan == null || subTask == null) return;
        publish(EVENT_SUBTASK_CHANGED, plan, subTask.getId(), subTask.getStatus().value());
    }

    /**
     * Publish an event to the message bus.
     *
     * @param eventType  Event type
     * @param plan       Associated plan
     * @param subTaskId  Subtask ID (optional)
     * @param subTaskStatus Subtask status (optional)
     */
    private void publish(String eventType, Plan plan, String subTaskId, String subTaskStatus) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", eventType);
            payload.put("planId", plan.getId() == null ? "" : plan.getId());
            payload.put("sessionId", plan.getSessionId() == null ? "" : plan.getSessionId());
            payload.put("agentId", plan.getAgentId() == null ? "" : plan.getAgentId());
            payload.put("status", plan.getStatus() == null ? "unknown" : plan.getStatus().value());
            payload.put("goal", plan.getGoal() == null ? "" : plan.getGoal());
            payload.put("timestamp", LocalDateTime.now().toString());
            if (subTaskId != null) {
                payload.put("subTaskId", subTaskId);
                payload.put("subTaskStatus", subTaskStatus);
            }
            String projectId = plan.getProjectId() != null ? plan.getProjectId() : "default";
            messageBusService.getMessageBus(projectId).publish(eventType, payload).subscribe();
            LOGGER.log(
                    Level.FINE,
                    "Plan event broadcasted: type={0}, planId={1}",
                    new Object[] {eventType, plan.getId()});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to broadcast plan event: type=" + eventType, e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
