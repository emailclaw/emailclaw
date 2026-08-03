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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.security.ToolGuardResult;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.service.security.ToolGuardConversationContext;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HITL (Human-In-The-Loop) approval tracker.
 *
 * <p>Responsible for tracking pending tool calls during streaming event processing.
 * When PermissionEngine determines user approval is required, creates a PendingApproval record for each pending tool.
 *
 * <p>This component is extracted from ChatService, decoupling HITL approval logic from streaming event processing,
 * making the approval flow independently testable and maintainable.
 *
 * <p>Workflow:
 * <ol>
 *   <li>PermissionEngine emits RequireUserConfirmEvent</li>
 *   <li>Record the list of pending tool calls</li>
 *   <li>Create PendingApproval (with timeout) for each pending tool</li>
 *   <li>External channels (Console/Email/DingTalk) handle user approval</li>
 * </ol>
 */
final class PendingApprovalTracker {

    /** Logger. */
    private static final Logger LOGGER = Logger.getLogger(PendingApprovalTracker.class.getName());

    /** Default approval timeout (seconds). */
    private static final int DEFAULT_APPROVAL_TIMEOUT_SECONDS = 300;

    /** Governance service: Tool call security analysis and approval management. */
    private final GovernanceService governanceService;

    /** List of pending tool calls (thread-safe). */
    private volatile List<ToolUseBlock> pendingTools;

    /**
     * Construct HITL approval tracker.
     *
     * @param governanceService Governance service instance
     */
    PendingApprovalTracker(GovernanceService governanceService) {
        this.governanceService = governanceService;
        LOGGER.log(Level.FINE, "PendingApprovalTracker initialization completed");
    }

    /**
     * Process RequireUserConfirmEvent event.
     *
     * <p>When PermissionEngine decides ASK, the Agent pauses and waits for approval.
     * This method creates a PendingApproval record for each pending tool, waiting for user decision.
     *
     * @param pendingToolList List of pending tool calls
     * @param agentId         Agent ID
     * @param sessionId       Session ID
     * @param channel         Channel ID
     * @param userId          User ID
     * @param route           Route metadata
     */
    void onRequireUserConfirm(
            List<ToolUseBlock> pendingToolList,
            String agentId,
            String sessionId,
            String channel,
            String userId,
            Map<String, Object> route) {
        LOGGER.log(
                Level.INFO,
                "RequireUserConfirmEvent: pending tools={0}, session={1}",
                new Object[] {pendingToolList.size(), sessionId});
        this.pendingTools = pendingToolList;

        ToolGuardConversationContext convCtx =
                new ToolGuardConversationContext(
                        agentId,
                        sessionId != null ? sessionId : "",
                        channel,
                        userId != null ? userId : "",
                        route != null ? Map.copyOf(route) : Map.of());

        // Create PendingApproval for each pending tool, waiting for user decision
        for (ToolUseBlock tool : pendingToolList) {
            try {
                ToolGuardResult guardResult =
                        governanceService.analyze(tool.getName(), tool.getInput());
                governanceService.createPendingApproval(
                        tool.getName(),
                        tool.getInput(),
                        guardResult,
                        DEFAULT_APPROVAL_TIMEOUT_SECONDS,
                        convCtx);
                LOGGER.log(
                        Level.INFO,
                        "Created PendingApproval: tool={0}, session={1}",
                        new Object[] {tool.getName(), sessionId});
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to create PendingApproval: tool=" + tool.getName(),
                        e);
            }
        }
    }

    /**
     * Get the current list of pending tool calls.
     *
     * @return List of pending tools, or null if there are no pending tools
     */
    List<ToolUseBlock> getPendingTools() {
        return pendingTools;
    }

    /**
     * Clear pending status.
     *
     * <p>Called when the session ends or approval completes.
     */
    void clear() {
        this.pendingTools = null;
        LOGGER.log(Level.FINE, "PendingApprovalTracker state cleared");
    }

    /**
     * Check if there are currently any pending tool calls.
     *
     * @return true if there are pending tools
     */
    boolean hasPendingApprovals() {
        return pendingTools != null && !pendingTools.isEmpty();
    }
}
