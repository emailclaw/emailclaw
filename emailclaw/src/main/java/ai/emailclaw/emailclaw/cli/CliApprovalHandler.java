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
package ai.emailclaw.emailclaw.cli;

import ai.emailclaw.emailclaw.model.security.PendingApproval;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionRule;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedStringBuilder;

/**
 * Interactive security gate handler that prompts the user for tool execution confirmations
 * and resumes suspended sessions via {@link ChatService#resumeWithConfirmResult}.
 */
public class CliApprovalHandler {

    private static final Logger LOGGER = Logger.getLogger(CliApprovalHandler.class.getName());

    private final ChatService chatService;
    private final GovernanceService governanceService;
    private final PrintWriter writer;
    private final Set<String> handledApprovals =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Constructs a CLI approval handler adhering to Pure DI.
     *
     * @param chatService the chat service
     * @param governanceService the security governance service
     * @param writer the terminal print writer
     */
    public CliApprovalHandler(
            ChatService chatService, GovernanceService governanceService, PrintWriter writer) {
        this.chatService = chatService;
        this.governanceService = governanceService;
        this.writer = writer;
    }

    /**
     * Polls for undecided pending approvals for the specified session and prompts the user.
     *
     * @param sessionId the session identifier
     * @param reader the active JLine LineReader (or null to use standard reader)
     * @return true if an approval was processed, false otherwise
     */
    public boolean checkAndHandlePending(String sessionId, LineReader reader) {
        if (governanceService == null || sessionId == null) {
            return false;
        }

        List<PendingApproval> pending =
                governanceService.getPendingApprovals().stream()
                        .filter(
                                p ->
                                        sessionId.equals(p.getSessionId())
                                                && !p.isDecided()
                                                && !handledApprovals.contains(p.getId()))
                        .toList();

        if (pending.isEmpty()) {
            return false;
        }

        for (PendingApproval approval : pending) {
            handledApprovals.add(approval.getId());
            handleSingleApproval(approval, reader);
        }
        return true;
    }

    private void handleSingleApproval(PendingApproval approval, LineReader reader) {
        writer.println();
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_RED)
                        .append(
                                "  ╔═══════════════════════════════════════════════════════════════════╗\n")
                        .append(
                                "  ║         ⚠️  SECURITY APPROVAL REQUIRED (Human-in-the-Loop)    "
                                        + "     ║\n")
                        .append(
                                "  ╠═══════════════════════════════════════════════════════════════════╣\n")
                        .style(CliTheme.STYLE_BOLD_YELLOW)
                        .append("  ║ Tool Name:  ")
                        .style(CliTheme.STYLE_DEFAULT)
                        .append(approval.getToolName())
                        .append("\n")
                        .style(CliTheme.STYLE_BOLD_YELLOW)
                        .append("  ║ Parameters: ")
                        .style(CliTheme.STYLE_DEFAULT)
                        .append(String.valueOf(approval.getToolInput()))
                        .append("\n")
                        .style(CliTheme.STYLE_BOLD_YELLOW)
                        .append("  ║ Session ID: ")
                        .style(CliTheme.STYLE_DEFAULT)
                        .append(approval.getSessionId())
                        .append("\n")
                        .style(CliTheme.STYLE_BOLD_RED)
                        .append(
                                "  ╚═══════════════════════════════════════════════════════════════════╝")
                        .toAnsi());
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  [Y] Approve Once  [A] Always Allow Tool  [N] Reject\n")
                        .style(CliTheme.STYLE_DEFAULT)
                        .append("  Please confirm [y/a/N]: ")
                        .toAnsi());
        writer.flush();

        String input = null;
        try {
            if (reader != null) {
                input = reader.readLine("  > ");
            } else {
                java.io.BufferedReader br =
                        new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                input = br.readLine();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read approval response from terminal", e);
        }

        boolean approved = false;
        boolean remember = false;
        if (input != null) {
            String choice = input.trim().toLowerCase();
            if ("y".equals(choice) || "yes".equals(choice)) {
                approved = true;
            } else if ("a".equals(choice) || "always".equals(choice)) {
                approved = true;
                remember = true;
            }
        }

        LOGGER.log(
                Level.INFO,
                "CLI user decision for tool approval {0}: approved={1}, remember={2}",
                new Object[] {approval.getId(), approved, remember});

        ToolUseBlock toolBlock =
                new ToolUseBlock(approval.getId(), approval.getToolName(), approval.getToolInput());
        List<PermissionRule> rules =
                approved && remember
                        ? List.of(
                                new PermissionRule(
                                        approval.getToolName(),
                                        null,
                                        PermissionBehavior.ALLOW,
                                        "cli_approved"))
                        : List.of();
        ConfirmResult confirmResult = new ConfirmResult(approved, toolBlock, rules);

        Msg resumeMsg =
                chatService.resumeWithConfirmResult(
                        approval.getAgentId(),
                        approval.getSessionId(),
                        "console",
                        approval.getRoute() != null ? approval.getRoute() : Map.of(),
                        List.of(confirmResult));

        writer.println(
                new AttributedStringBuilder()
                        .style(approved ? CliTheme.STYLE_BOLD_GREEN : CliTheme.STYLE_BOLD_RED)
                        .append(
                                approved
                                        ? "  ✔️ Tool execution approved. Resuming agent..."
                                        : "  ❌ Tool execution rejected. Resuming agent...")
                        .toAnsi());
        writer.println();
        writer.flush();
    }
}
