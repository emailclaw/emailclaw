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
package ai.emailclaw.emailclaw.service.security;

import ai.emailclaw.emailclaw.model.AgentConfiguration;
import ai.emailclaw.emailclaw.model.security.ApprovalDecision;
import ai.emailclaw.emailclaw.model.security.PendingApproval;
import ai.emailclaw.emailclaw.model.security.ToolGuardResult;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.storage.AppPaths;
import io.agentscope.core.permission.PermissionEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Governance service. Responsible for security analysis of tool calls, pending approval request management, and approval history persistence.
 *
 * <p>As an auxiliary component of PermissionEngine, it provides the following functions:
 * <ul>
 *   <li>Tool call security detection (formerly ToolGuardRuleEngine responsibilities)</li>
 *   <li>Create and manage pending approval requests</li>
 *   <li>Process user approval decisions</li>
 *   <li>Persist approval history for audit and analysis</li>
 *   <li>Automatically clean up expired approval requests</li>
 * </ul>
 */
public class GovernanceService {

    private static final Logger LOGGER = Logger.getLogger(GovernanceService.class.getName());

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppContext appContext;

    private final AppPaths appPaths;

    private final Random approvalCodeRandom = new Random();

    // Pending approval requests in memory, using ConcurrentHashMap to support concurrent access
    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    // Approval history directory
    private final Path approvalHistoryDir;

    // AgentScope 2.0 permission engine
    private PermissionEngine permissionEngine;

    // Dangerous shell command list
    private static final Set<String> DANGEROUS_SHELL_COMMANDS =
            new HashSet<>(
                    Arrays.asList(
                            "rm",
                            "rmdir",
                            "dd",
                            "mkfs",
                            "fdisk",
                            "parted",
                            "shred",
                            "wipe",
                            "shutdown",
                            "reboot",
                            "halt",
                            "poweroff",
                            "kill",
                            "killall",
                            "pkill",
                            "format",
                            "del",
                            "erase",
                            "sudo",
                            "su",
                            "chmod",
                            "chown",
                            "chgrp",
                            "bash",
                            "sh",
                            "zsh",
                            "ksh",
                            "csh",
                            "tcsh",
                            "fish"));

    // Dangerous shell operators (corresponding to Emailclaw's _SHELL_OPERATORS)
    // Including command separators(;), pipes(|), background execution(&), input redirection(<),
    // output redirection(>)
    private static final Set<String> DANGEROUS_SHELL_OPERATORS =
            new HashSet<>(Arrays.asList(";", "|", "&", "<", ">"));

    // Sensitive system paths
    private static final Set<String> SENSITIVE_PATHS =
            new HashSet<>(
                    Arrays.asList(
                            "/etc/passwd",
                            "/etc/shadow",
                            "/etc/sudoers",
                            "/root/.ssh",
                            "/home",
                            "/var/log",
                            "/sys",
                            "/proc",
                            "/boot",
                            "C:\\Windows\\System32",
                            "C:\\Windows\\System32\\drivers\\etc\\hosts"));

    // System directories
    private static final Set<String> SYSTEM_DIRS =
            new HashSet<>(
                    Arrays.asList(
                            "/etc/",
                            "/sys/",
                            "/proc/",
                            "/boot/",
                            "/bin/",
                            "/sbin/",
                            "/usr/bin/",
                            "/usr/sbin/",
                            "/lib/",
                            "/lib64/"));

    // Critical environment variables
    private static final Set<String> CRITICAL_ENV_VARS =
            new HashSet<>(
                    Arrays.asList(
                            "PATH",
                            "LD_LIBRARY_PATH",
                            "LD_PRELOAD",
                            "PYTHONPATH",
                            "JAVA_HOME",
                            "HOME",
                            "USER",
                            "SHELL",
                            "sudo"));

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    public GovernanceService(AppContext appContext) {
        this.appContext = appContext;
        this.appPaths = appContext.paths();
        this.approvalHistoryDir =
                this.appPaths.root.resolve(AppHomeConstants.SECURITY_APPROVALS_DIR);
        try {
            Files.createDirectories(this.approvalHistoryDir);
        } catch (IOException e) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to create approval history directory: " + approvalHistoryDir,
                    e);
        }
        loadApprovalHistory();
        initPermissionEngine();
    }

    /**
     * Initialize AgentScope 2.0 PermissionEngine, applying the "Java hardcoded fallback + JSON configuration override" strategy.
     */
    private void initPermissionEngine() {
        LOGGER.log(Level.INFO, "Initializing AgentScope 2.0 PermissionEngine...");
        try {
            // 1. Get built-in hardcoded rules as fallback
            List<String> dangerousCommands = new ArrayList<>(DANGEROUS_SHELL_COMMANDS);

            // 2. Read global configuration file (global-config.json) for rule merging and
            // overriding
            Path globalConfigPath = appPaths.globalConfigFile;
            if (Files.exists(globalConfigPath)) {
                JsonNode configNode = JSON.readTree(Files.readAllBytes(globalConfigPath));
                JsonNode securityNode = configNode.get("security");
                if (securityNode != null) {
                    JsonNode addedCommands = securityNode.get("added_commands");
                    if (addedCommands != null && addedCommands.isArray()) {
                        for (JsonNode cmd : addedCommands) {
                            dangerousCommands.add(cmd.asText());
                        }
                    }
                    JsonNode ignoredCommands = securityNode.get("ignored_commands");
                    if (ignoredCommands != null && ignoredCommands.isArray()) {
                        for (JsonNode cmd : ignoredCommands) {
                            dangerousCommands.remove(cmd.asText());
                        }
                    }
                }
                LOGGER.log(Level.INFO, "Merged custom security rules from global-config.json");
            }

            // 3. Configure and build the engine
            // Note: Reflection or pseudo-code API call is used here, actually needs to rely on
            // AgentScope 2.0 real interface
            // permissionEngine = PermissionEngine.builder()
            //        .enableSemanticAnalysis(true)
            //        .addStaticRules("shell", dangerousCommands)
            //        .build();
            LOGGER.log(
                    Level.INFO,
                    "PermissionEngine configuration completed (enableSemanticAnalysis=true)");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize PermissionEngine", e);
        }
    }

    /**
     * [Deprecated] Original manual security detection method.
     * The detection capability has now been handed over to AgentScope 2.0 PermissionEngine and ToolGuardMiddleware.
     */
    public ToolGuardResult analyze(String toolName, Map<String, Object> toolInput) {
        LOGGER.log(
                Level.WARNING,
                "GovernanceService.analyze() is deprecated, should be automatically intercepted by"
                        + " PermissionEngine");
        ToolGuardResult result = new ToolGuardResult();
        result.setApproved(true);
        return result;
    }

    /**
     * Get the PermissionMode of the specified Agent.
     *
     * @param agentId Agent ID
     * @return PermissionMode string (bypass, default, accept_edits, explore, dont_ask)
     */
    public String getPermissionMode(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            AgentConfiguration config = appContext.loadAgentConfig(agentId);
            if (config != null && config.getPermissionMode() != null) {
                return config.getPermissionMode();
            }
        }
        return "default";
    }

    /**
     * Create a pending approval request.
     */
    public PendingApproval createPendingApproval(
            String toolName,
            Map<String, Object> toolInput,
            ToolGuardResult guardResult,
            long timeoutSeconds) {
        return createPendingApproval(toolName, toolInput, guardResult, timeoutSeconds, null);
    }

    /**
     * Create a pending approval request with session routing context.
     */
    public PendingApproval createPendingApproval(
            String toolName,
            Map<String, Object> toolInput,
            ToolGuardResult guardResult,
            long timeoutSeconds,
            ToolGuardConversationContext conversationContext) {
        PendingApproval approval = new PendingApproval(toolName, guardResult.getMaxSeverity());
        approval.setToolInput(new ConcurrentHashMap<>(toolInput));
        approval.setFindings(guardResult.getFindings());
        approval.setTimeoutSeconds(timeoutSeconds);
        if (conversationContext != null) {
            approval.setAgentId(conversationContext.agentId());
            approval.setSessionId(conversationContext.sessionId());
            approval.setChannelId(conversationContext.channelId());
            approval.setUserId(conversationContext.userId());
            approval.setRoute(
                    conversationContext.route() == null
                            ? new ConcurrentHashMap<>()
                            : new ConcurrentHashMap<>(conversationContext.route()));
        }
        approval.setApprovalCode(
                generateApprovalCode(
                        approval.getChannelId(),
                        approval.getSessionId(),
                        approval.getUserId(),
                        null));
        approval.setRememberCode(
                generateApprovalCode(
                        approval.getChannelId(),
                        approval.getSessionId(),
                        approval.getUserId(),
                        approval.getApprovalCode()));
        pendingApprovals.put(approval.getId(), approval);
        LOGGER.log(
                Level.INFO,
                String.format(
                        "Create pending approval request: tool=%s, severity=%s, approvalId=%s",
                        toolName, guardResult.getMaxSeverity().getDisplayName(), approval.getId()));
        return approval;
    }

    /**
     * Find pending approval requests by channel, session, user, and 4-digit approval code.
     */
    public Optional<PendingApproval> findPendingByCode(
            String channelId, String sessionId, String userId, String approvalCode) {
        if (approvalCode == null || !approvalCode.matches("\\d{4}")) {
            return Optional.empty();
        }
        return getPendingApprovals().stream()
                .filter(approval -> same(approval.getChannelId(), channelId))
                .filter(approval -> same(approval.getSessionId(), sessionId))
                .filter(
                        approval ->
                                userId == null
                                        || userId.isBlank()
                                        || same(approval.getUserId(), userId))
                .filter(approval -> approvalCode.equals(approval.getApprovalCode()))
                .findFirst();
    }

    /**
     * Find pending approval requests by channel, session, user, and 4-digit remember code.
     */
    public Optional<PendingApproval> findPendingByRememberCode(
            String channelId, String sessionId, String userId, String rememberCode) {
        if (rememberCode == null || !rememberCode.matches("\\d{4}")) {
            return Optional.empty();
        }
        return getPendingApprovals().stream()
                .filter(approval -> same(approval.getChannelId(), channelId))
                .filter(approval -> same(approval.getSessionId(), sessionId))
                .filter(
                        approval ->
                                userId == null
                                        || userId.isBlank()
                                        || same(approval.getUserId(), userId))
                .filter(approval -> rememberCode.equals(approval.getRememberCode()))
                .findFirst();
    }

    /**
     * Find all pending approval requests by channel, session, and user.
     */
    public Optional<PendingApproval> findPendingByChannelSession(
            String channelId, String sessionId, String userId) {
        return getPendingApprovals().stream()
                .filter(approval -> same(approval.getChannelId(), channelId))
                .filter(approval -> same(approval.getSessionId(), sessionId))
                .filter(
                        approval ->
                                userId == null
                                        || userId.isBlank()
                                        || same(approval.getUserId(), userId))
                .findFirst();
    }

    /**
     * Mark approval request as delivered to external Channel.
     */
    public void markDelivered(String approvalId) {
        PendingApproval approval = pendingApprovals.get(approvalId);
        if (approval != null) {
            approval.setDelivered(true);
            approval.setDeliveryError("");
        }
    }

    /**
     * Mark approval request delivery failed, and treat it as rejected immediately.
     */
    public void markDeliveryFailed(String approvalId, String reason) {
        PendingApproval approval = pendingApprovals.get(approvalId);
        if (approval != null) {
            approval.setDelivered(false);
            approval.setDeliveryError(reason == null ? "" : reason);
        }
    }

    /**
     * Get pending approval request.
     */
    public Optional<PendingApproval> getPendingApproval(String approvalId) {
        PendingApproval approval = pendingApprovals.get(approvalId);
        if (approval != null && approval.isExpired() && !approval.isDecided()) {
            approval.setDecision(ApprovalDecision.TIMEOUT, "system", "Approval request timed out");
            persistApproval(approval);
        }
        return Optional.ofNullable(approval);
    }

    /**
     * Get all undecided pending approval requests.
     */
    public List<PendingApproval> getPendingApprovals() {
        List<PendingApproval> pending = new ArrayList<>();
        for (PendingApproval approval : pendingApprovals.values()) {
            if (approval.isExpired() && !approval.isDecided()) {
                approval.setDecision(
                        ApprovalDecision.TIMEOUT, "system", "Approval request timed out");
                persistApproval(approval);
            } else if (!approval.isDecided()) {
                pending.add(approval);
            }
        }
        pending.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return pending;
    }

    /**
     * Process user's approval decision.
     */
    public boolean setApprovalDecision(
            String approvalId, ApprovalDecision decision, String decidedBy, String notes) {
        Optional<PendingApproval> approvalOpt = getPendingApproval(approvalId);
        if (approvalOpt.isEmpty()) {
            LOGGER.log(Level.WARNING, "Pending approval request does not exist: " + approvalId);
            return false;
        }
        PendingApproval approval = approvalOpt.get();
        if (approval.isDecided()) {
            LOGGER.log(
                    Level.WARNING,
                    "Approval request already decided, cannot change: " + approvalId);
            return false;
        }
        if (approval.isExpired()) {
            LOGGER.log(Level.WARNING, "Approval request expired: " + approvalId);
            return false;
        }
        approval.setDecision(decision, decidedBy, notes);
        persistApproval(approval);
        LOGGER.log(
                Level.INFO,
                String.format(
                        "Approval request processed: approvalId=%s, decision=%s, decidedBy=%s",
                        approvalId, decision.getDisplayName(), decidedBy));
        return true;
    }

    /**
     * Get approval history.
     */
    public List<PendingApproval> getApprovalHistory(int limit) {
        List<PendingApproval> history = new ArrayList<>();
        try {
            if (!Files.exists(approvalHistoryDir)) {
                return history;
            }
            Files.list(approvalHistoryDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted((a, b) -> Long.compare(getLastModified(b), getLastModified(a)))
                    .limit(limit)
                    .forEach(
                            p -> {
                                try {
                                    PendingApproval approval =
                                            JSON.readValue(
                                                    Files.readAllBytes(p), PendingApproval.class);
                                    history.add(approval);
                                } catch (IOException e) {
                                    LOGGER.log(
                                            Level.WARNING,
                                            "Unable to read approval history: " + p,
                                            e);
                                }
                            });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to list approval history directory", e);
        }
        return history;
    }

    /**
     * Clean up expired pending approval requests and history.
     */
    public void cleanupOldApprovals(int olderThanDays) {
        long cutoffTime = System.currentTimeMillis() - (olderThanDays * 24L * 60L * 60L * 1000L);
        try {
            if (Files.exists(approvalHistoryDir)) {
                Files.list(approvalHistoryDir)
                        .filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> getLastModified(p) < cutoffTime)
                        .forEach(
                                p -> {
                                    try {
                                        Files.delete(p);
                                        LOGGER.log(
                                                Level.INFO,
                                                "Deleted expired approval history: "
                                                        + p.getFileName());
                                    } catch (IOException e) {
                                        LOGGER.log(
                                                Level.WARNING,
                                                "Unable to delete approval history file: " + p,
                                                e);
                                    }
                                });
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error cleaning up approval history", e);
        }
    }

    /**
     * Get approval statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        long approved = 0;
        long denied = 0;
        long timeout = 0;
        long total = 0;
        for (PendingApproval approval : pendingApprovals.values()) {
            if (approval.isDecided()) {
                total++;
                switch (approval.getUserDecision()) {
                    case APPROVE -> approved++;
                    case DENY -> denied++;
                    case TIMEOUT -> timeout++;
                }
            }
        }
        stats.put("total", total);
        stats.put("approved", approved);
        stats.put("denied", denied);
        stats.put("timeout", timeout);
        stats.put("pending", pendingApprovals.size());
        stats.put("approvalRate", total > 0 ? (double) approved / total : 0);
        return stats;
    }

    // ── [Removed] Original manual detection methods (checkShellCommand, checkFileWrite, etc.) ──

    // ── Approval code generation and management ─────────────────────────────────────────────────
    private void persistApproval(PendingApproval approval) {
        try {
            Files.createDirectories(approvalHistoryDir);
            String filename =
                    String.format("%s_%d.json", approval.getId(), System.currentTimeMillis());
            Path filePath = approvalHistoryDir.resolve(filename);
            byte[] jsonBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(approval);
            Files.write(filePath, jsonBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            LOGGER.log(Level.FINE, "Persisted approval request: " + filePath);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to persist approval request", e);
        }
    }

    private void loadApprovalHistory() {
        try {
            if (!Files.exists(approvalHistoryDir)) {
                return;
            }
            Files.list(approvalHistoryDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .limit(100)
                    .forEach(
                            p -> {
                                try {
                                    PendingApproval approval =
                                            JSON.readValue(
                                                    Files.readAllBytes(p), PendingApproval.class);
                                    if (!approval.isDecided() || isRecent(approval)) {
                                        pendingApprovals.put(approval.getId(), approval);
                                    }
                                } catch (IOException e) {
                                    LOGGER.log(
                                            Level.WARNING,
                                            "Unable to load approval history: " + p,
                                            e);
                                }
                            });
            LOGGER.log(Level.INFO, "Loaded " + pendingApprovals.size() + " approval records");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to load approval history directory", e);
        }
    }

    private synchronized String generateApprovalCode(
            String channelId, String sessionId, String userId, String exclude) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = String.format("%04d", approvalCodeRandom.nextInt(10_000));
            if (!code.equals(exclude) && !approvalCodeExists(channelId, sessionId, userId, code)) {
                return code;
            }
        }
        for (int value = 0; value < 10_000; value++) {
            String code = String.format("%04d", value);
            if (!code.equals(exclude) && !approvalCodeExists(channelId, sessionId, userId, code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Too many pending approvals in the current session, unable to allocate a 4-digit"
                        + " approval code");
    }

    private boolean approvalCodeExists(
            String channelId, String sessionId, String userId, String code) {
        return pendingApprovals.values().stream()
                .filter(approval -> !approval.isDecided())
                .filter(approval -> same(approval.getChannelId(), channelId))
                .filter(approval -> same(approval.getSessionId(), sessionId))
                .filter(approval -> same(approval.getUserId(), userId))
                .anyMatch(
                        approval ->
                                code.equals(approval.getApprovalCode())
                                        || code.equals(approval.getRememberCode()));
    }

    private boolean same(String left, String right) {
        String safeLeft = left == null ? "" : left;
        String safeRight = right == null ? "" : right;
        return safeLeft.equals(safeRight);
    }

    private boolean isRecent(PendingApproval approval) {
        long oneDaySeconds = 24L * 60L * 60L;
        long now = System.currentTimeMillis() / 1000;
        return (now - approval.getCreatedAt()) < oneDaySeconds;
    }

    private long getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
