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
package ai.emailclaw.emailclaw.model.security;

import ai.emailclaw.emailclaw.util.UuidUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pending tool call approval. When ToolGuard detects a risk requiring user approval, a PendingApproval object is created,
 * waiting for the user's decision.
 */
public class PendingApproval {
    /** The unique identifier of the approval request. */
    private String id;

    /** 4-digit approval code entered by user in a non-button Channel (approve this time only). */
    private String approvalCode;

    /** 4-digit approval code (approve and remember this decision). Different from approvalCode. */
    private String rememberCode;

    /** The Agent ID that triggered this approval. */
    private String agentId;

    /** The session ID that triggered this approval. */
    private String sessionId;

    /** The channel ID that triggered this approval, for example console, emailclaw, dingtalk. */
    private String channelId;

    /** The user ID that triggered this approval, for example email address or IM user ID. */
    private String userId;

    /** The tool name that requires approval. */
    private String toolName;

    /** The input parameters of the tool call. */
    private Map<String, Object> toolInput;

    /** The detected threat level. */
    private GuardSeverity threatLevel;

    /** The list of all detected findings. */
    private List<GuardFinding> findings;

    /** Approval timeout in seconds. If the user does not make a decision within this time, it will be automatically denied. */
    private long timeoutSeconds;

    /** The user's approval decision. Initially null, updated after the user makes a decision. */
    private ApprovalDecision userDecision;

    /** The timestamp when the approval request was created. */
    private long createdAt;

    /** The timestamp when the user made a decision. 0 if not decided. */
    private long decidedAt;

    /** The identity or remark of the decision maker. */
    private String decidedBy;

    /** The user's approval notes or remarks. */
    private String notes;

    /**
     * Routing information specific to the Channel plugin.
     *
     * <p>For example, DingTalk would record sessionWebhook, Emailclaw could record replyTo or subject.
     */
    private Map<String, Object> route;

    /** Whether the approval message has been successfully delivered to the external Channel. */
    private boolean delivered;

    /** The reason for the approval message delivery failure. */
    private String deliveryError;

    /** Constructor: Creates a new pending approval request. */
    public PendingApproval() {
        this.id = UuidUtils.randomUUIDv7().toString();
        this.toolInput = new HashMap<>();
        this.route = new HashMap<>();
        this.timeoutSeconds = 300; // default 5 minutes timeout
        this.createdAt = Instant.now().getEpochSecond();
        this.decidedAt = 0;
    }

    /**
     * Convenience constructor: Creates a new pending approval request, specifying the tool name and threat level.
     *
     * @param toolName Tool name
     * @param threatLevel Threat level
     */
    public PendingApproval(String toolName, GuardSeverity threatLevel) {
        this();
        this.toolName = toolName;
        this.threatLevel = threatLevel;
    }

    /**
     * Gets the unique identifier of the approval request.
     *
     * @return The unique identifier of the approval request
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the approval request.
     *
     * @param id The unique identifier of the approval request
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the 4-digit approval code entered by user in a non-button Channel.
     *
     * @return Approval code
     */
    public String getApprovalCode() {
        return approvalCode;
    }

    /**
     * Sets the 4-digit approval code entered by user in a non-button Channel.
     *
     * @param approvalCode Approval code
     */
    public void setApprovalCode(String approvalCode) {
        this.approvalCode = approvalCode;
    }

    /**
     * Gets the 4-digit approval code to approve and remember this decision.
     *
     * @return Approval code to remember decision
     */
    public String getRememberCode() {
        return rememberCode;
    }

    /**
     * Sets the 4-digit approval code to approve and remember this decision.
     *
     * @param rememberCode Approval code to remember decision
     */
    public void setRememberCode(String rememberCode) {
        this.rememberCode = rememberCode;
    }

    /**
     * Gets the Agent ID that triggered this approval.
     *
     * @return The Agent ID that triggered this approval
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Sets the Agent ID that triggered this approval.
     *
     * @param agentId The Agent ID that triggered this approval
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Gets the session ID that triggered this approval.
     *
     * @return The session ID that triggered this approval
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the session ID that triggered this approval.
     *
     * @param sessionId The session ID that triggered this approval
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Gets the channel ID that triggered this approval.
     *
     * @return The channel ID that triggered this approval
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * Sets the channel ID that triggered this approval.
     *
     * @param channelId The channel ID that triggered this approval
     */
    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    /**
     * Gets the user ID that triggered this approval.
     *
     * @return The user ID that triggered this approval
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID that triggered this approval.
     *
     * @param userId The user ID that triggered this approval
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Gets the tool name that requires approval.
     *
     * @return The tool name that requires approval
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Sets the tool name that requires approval.
     *
     * @param toolName The tool name that requires approval
     */
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    /**
     * Gets the input parameters of the tool call.
     *
     * @return The input parameters of the tool call
     */
    public Map<String, Object> getToolInput() {
        return toolInput;
    }

    /**
     * Sets the input parameters of the tool call.
     *
     * @param toolInput The input parameters of the tool call
     */
    public void setToolInput(Map<String, Object> toolInput) {
        this.toolInput = toolInput;
    }

    /**
     * Gets the detected threat level.
     *
     * @return The detected threat level
     */
    public GuardSeverity getThreatLevel() {
        return threatLevel;
    }

    /**
     * Sets the detected threat level.
     *
     * @param threatLevel The detected threat level
     */
    public void setThreatLevel(GuardSeverity threatLevel) {
        this.threatLevel = threatLevel;
    }

    /**
     * Gets the list of all detected findings.
     *
     * @return The list of all detected findings
     */
    public List<GuardFinding> getFindings() {
        return findings;
    }

    /**
     * Sets the list of all detected findings.
     *
     * @param findings The list of all detected findings
     */
    public void setFindings(List<GuardFinding> findings) {
        this.findings = findings;
    }

    /**
     * Gets the approval timeout in seconds.
     *
     * @return Approval timeout in seconds
     */
    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Sets the approval timeout in seconds.
     *
     * @param timeoutSeconds Approval timeout in seconds
     */
    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Gets the user's approval decision.
     *
     * @return The user's approval decision
     */
    public ApprovalDecision getUserDecision() {
        return userDecision;
    }

    /**
     * Sets the user's approval decision.
     *
     * @param userDecision The user's approval decision
     */
    public void setUserDecision(ApprovalDecision userDecision) {
        this.userDecision = userDecision;
    }

    /**
     * Gets the timestamp when the approval request was created.
     *
     * @return Timestamp when the approval request was created
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the timestamp when the approval request was created.
     *
     * @param createdAt Timestamp when the approval request was created
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the timestamp when the user made a decision.
     *
     * @return Timestamp when the user made a decision
     */
    public long getDecidedAt() {
        return decidedAt;
    }

    /**
     * Sets the timestamp when the user made a decision.
     *
     * @param decidedAt Timestamp when the user made a decision
     */
    public void setDecidedAt(long decidedAt) {
        this.decidedAt = decidedAt;
    }

    /**
     * Gets the identity or remark of the decision maker.
     *
     * @return Identity or remark of the decision maker
     */
    public String getDecidedBy() {
        return decidedBy;
    }

    /**
     * Sets the identity or remark of the decision maker.
     *
     * @param decidedBy Identity or remark of the decision maker
     */
    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    /**
     * Gets the user's approval notes or remarks.
     *
     * @return The user's approval notes or remarks
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Sets the user's approval notes or remarks.
     *
     * @param notes The user's approval notes or remarks
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Gets the Channel plugin specific routing information.
     *
     * @return Channel plugin specific routing information
     */
    public Map<String, Object> getRoute() {
        return route;
    }

    /**
     * Sets the Channel plugin specific routing information.
     *
     * @param route Channel plugin specific routing information
     */
    public void setRoute(Map<String, Object> route) {
        this.route = route;
    }

    /**
     * Determines whether the approval message has been successfully delivered to the external Channel.
     *
     * @return True if delivered
     */
    public boolean isDelivered() {
        return delivered;
    }

    /**
     * Sets whether the approval message has been successfully delivered to the external Channel.
     *
     * @param delivered True if delivered
     */
    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    /**
     * Gets the reason for the approval message delivery failure.
     *
     * @return Reason for delivery failure
     */
    public String getDeliveryError() {
        return deliveryError;
    }

    /**
     * Sets the reason for the approval message delivery failure.
     *
     * @param deliveryError Reason for delivery failure
     */
    public void setDeliveryError(String deliveryError) {
        this.deliveryError = deliveryError;
    }

    /**
     * Checks if the approval request has expired.
     *
     * @return True if expired
     */
    public boolean isExpired() {
        if (decidedAt != 0) {
            return false; // Already decided requests do not expire
        }
        long elapsedSeconds = Instant.now().getEpochSecond() - createdAt;
        return elapsedSeconds > timeoutSeconds;
    }

    /**
     * Checks if a decision has been made for the approval request.
     *
     * @return True if a decision has been made
     */
    public boolean isDecided() {
        return userDecision != null && decidedAt != 0;
    }

    /**
     * Sets the user's approval decision.
     *
     * @param decision The user's decision
     * @param decidedBy The identity of the decision maker
     * @param notes Approval notes
     */
    public void setDecision(ApprovalDecision decision, String decidedBy, String notes) {
        this.userDecision = decision;
        this.decidedAt = Instant.now().getEpochSecond();
        this.decidedBy = decidedBy;
        this.notes = notes;
    }

    /**
     * Gets the remaining time (in seconds) for the approval request. Returns 0 if expired.
     *
     * @return Remaining time (in seconds)
     */
    public long getRemainingSeconds() {
        if (isDecided()) {
            return 0;
        }
        long elapsedSeconds = Instant.now().getEpochSecond() - createdAt;
        long remaining = timeoutSeconds - elapsedSeconds;
        return Math.max(0, remaining);
    }

    @Override
    public String toString() {
        return String.format(
                "PendingApproval{id=%s, tool=%s, threatLevel=%s, decided=%s}",
                id, toolName, threatLevel.getDisplayName(), isDecided());
    }
}
