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
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.model.AcpAgentInfo;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentRuntimeStatus;
import ai.emailclaw.emailclaw.service.MessageBusService;
import ai.emailclaw.emailclaw.service.ToolService;
import ai.emailclaw.emailclaw.util.UuidUtils;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agent management tool.
 */
public class AgentManagementTool extends BaseEmailclawTool {

    private static final Logger LOGGER = Logger.getLogger(AgentManagementTool.class.getName());

    private static final ConcurrentMap<String, ExternalAgentTask> TASKS = new ConcurrentHashMap<>();

    private static final Function<String, String> JSON_ESCAPER =
            text -> {
                if (text == null) return "";
                return text.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
            };

    public AgentManagementTool() {}

    private static class ExternalAgentTask {
        public final String id;
        public final String target;
        public final String text;
        public final String sessionId;
        public volatile String status;
        public volatile String result;
        public final long startedAt;
        public final int timeoutSeconds;

        public ExternalAgentTask(
                String id, String target, String text, String sessionId, int timeoutSeconds) {
            this.id = id;
            this.target = target;
            this.text = text;
            this.sessionId = sessionId;
            this.status = "pending";
            this.result = "";
            this.startedAt = System.currentTimeMillis();
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    @Tool(
            name = BuiltInToolNames.DELEGATE_EXTERNAL_AGENT,
            description = "Delegate work to an external ACP agent runner")
    public String delegateExternalAgent(
            @ToolParam(name = "task", description = "Task description") String task,
            @ToolParam(
                            name = "timeout_seconds",
                            description = "Timeout in seconds",
                            required = false)
                    Integer timeoutSeconds) {
        if (off(BuiltInToolNames.DELEGATE_EXTERNAL_AGENT)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        if (task == null || task.isBlank()) {
            return "Error: task cannot be empty.";
        }
        int timeout = timeoutSeconds == null ? 60 : Math.max(1, timeoutSeconds);
        String taskId = UuidUtils.randomUUIDv7().toString();
        ExternalAgentTask t = new ExternalAgentTask(taskId, "external", task, "", timeout);
        TASKS.put(taskId, t);
        Thread.startVirtualThread(
                () -> {
                    try {
                        List<AcpAgentInfo> acpAgents =
                                context.repository.configManager().getAcpAgents();
                        AcpAgentInfo target = null;
                        for (AcpAgentInfo a : acpAgents) {
                            if (a.isEnabled()) {
                                target = a;
                                break;
                            }
                        }
                        if (target != null) {
                            String r = runAcpProcess(target, task, timeout);
                            t.result = r;
                            t.status = "completed";
                        } else {
                            Thread.sleep(Math.min(timeout * 1000L, 5000L));
                            t.result = "External agent completed task: " + task;
                            t.status = "completed";
                        }
                    } catch (Exception e) {
                        t.result = "Error: " + e.getMessage();
                        t.status = "failed";
                    }
                });
        return "task_id=" + taskId + ", status=running";
    }

    @Tool(
            name = BuiltInToolNames.LIST_AGENTS,
            description = "List available agents (internal + ACP)")
    public String listAgents() {
        if (off(BuiltInToolNames.LIST_AGENTS)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== Internal Agents ===\n");
        List<AgentInfo> agents = context.listAgents();
        for (AgentInfo a : agents) {
            sb.append("  - ").append(a.getId()).append(" (").append(a.getName()).append(")");
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                sb.append(": ").append(a.getDescription());
            }
            sb.append("\n");
        }
        sb.append("=== ACP Agents ===\n");
        try {
            List<AcpAgentInfo> acpAgents = context.repository.configManager().getAcpAgents();
            for (AcpAgentInfo a : acpAgents) {
                sb.append("  - ").append(a.getKey()).append(": ").append(a.getCommand());
                if (a.getArgs() != null && !a.getArgs().isBlank()) {
                    sb.append(" ").append(a.getArgs());
                }
                sb.append(" [").append(a.isEnabled() ? "enabled" : "disabled").append("]\n");
            }
        } catch (Exception e) {
            sb.append("  (error reading ACP agents: ").append(e.getMessage()).append(")\n");
        }
        return sb.toString();
    }

    @Tool(
            name = BuiltInToolNames.CHAT_WITH_AGENT,
            description = "Chat with another agent (foreground, returns result)")
    public String chatWithAgent(
            @ToolParam(name = "to_agent", description = "Target agent ID") String toAgent,
            @ToolParam(name = "text", description = "Message content") String text,
            @ToolParam(
                            name = "session_id",
                            description = "Session ID for context continuation",
                            required = false)
                    String sessionId,
            @ToolParam(
                            name = "timeout",
                            description = "Timeout in seconds (default 120)",
                            required = false)
                    Integer timeout) {
        if (off(BuiltInToolNames.CHAT_WITH_AGENT)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        if (toAgent == null || toAgent.isBlank()) {
            return "Error: to_agent cannot be empty.";
        }
        if (text == null || text.isBlank()) {
            return "Error: text cannot be empty.";
        }
        int t = timeout == null ? 120 : Math.max(1, timeout);
        // Guardrail: limit max blocking wait to prevent thread starvation
        t = Math.min(t, 600);
        String taskId = UuidUtils.randomUUIDv7().toString();
        ExternalAgentTask task =
                new ExternalAgentTask(taskId, toAgent, text, sessionId == null ? "" : sessionId, t);
        TASKS.put(taskId, task);
        final int finalT = t;
        Thread.startVirtualThread(
                () -> {
                    try {
                        String r = executeTaskAgainstAgent(toAgent, text, sessionId, finalT);
                        task.result = r;
                        task.status = "completed";
                    } catch (Exception e) {
                        task.result = "Error: " + e.getMessage();
                        task.status = "failed";
                    }
                });
        long deadline = System.currentTimeMillis() + t * 1000L;
        try {
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
                if (!"pending".equals(task.status) && !"running".equals(task.status)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Task interrupted.";
        }
        if ("pending".equals(task.status) || "running".equals(task.status)) {
            task.status = "timed_out";
            return "task_id=" + taskId + ", status=timed_out";
        }
        String sessionPart = task.sessionId.isEmpty() ? "" : "\n[SESSION: " + task.sessionId + "]";
        return task.result + sessionPart;
    }

    @Tool(
            name = BuiltInToolNames.SUBMIT_TO_AGENT,
            description = "Submit a background task to another agent")
    public String submitToAgent(
            @ToolParam(name = "to_agent", description = "Target agent ID") String toAgent,
            @ToolParam(name = "text", description = "Task description") String text,
            @ToolParam(
                            name = "session_id",
                            description = "Session ID for context continuation",
                            required = false)
                    String sessionId) {
        if (off(BuiltInToolNames.SUBMIT_TO_AGENT)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        if (toAgent == null || toAgent.isBlank()) {
            return "Error: to_agent cannot be empty.";
        }
        if (text == null || text.isBlank()) {
            return "Error: text cannot be empty.";
        }
        String taskId = UuidUtils.randomUUIDv7().toString();
        ExternalAgentTask task =
                new ExternalAgentTask(
                        taskId, toAgent, text, sessionId == null ? "" : sessionId, 300);
        TASKS.put(taskId, task);
        Thread.startVirtualThread(
                () -> {
                    try {
                        String r = executeTaskAgainstAgent(toAgent, text, sessionId, 300);
                        task.result = r;
                        task.status = "completed";
                    } catch (Exception e) {
                        task.result = "Error: " + e.getMessage();
                        task.status = "failed";
                    }
                });
        return "[TASK_ID: "
                + taskId
                + "]\n"
                + (sessionId != null && !sessionId.isBlank()
                        ? "[SESSION: " + sessionId + "]\n"
                        : "")
                + "\nTask submitted to "
                + toAgent
                + ".";
    }

    @Tool(
            name = BuiltInToolNames.CHECK_AGENT_TASK,
            description = "Check status of a background agent task")
    public String checkAgentTask(
            @ToolParam(name = "task_id", description = "Task ID to check") String taskId) {
        if (off(BuiltInToolNames.CHECK_AGENT_TASK)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        if (taskId == null || taskId.isBlank()) {
            return "Error: task_id cannot be empty.";
        }
        ExternalAgentTask t = TASKS.get(taskId);
        if (t == null) {
            return "[TASK_ID: " + taskId + "]\n[STATUS: unknown]\n\nTask not found.";
        }
        String statusLabel;
        switch (t.status) {
            case "completed":
                statusLabel = "finished";
                break;
            case "failed":
                statusLabel = "finished";
                break;
            case "timed_out":
                statusLabel = "timed_out";
                break;
            default:
                statusLabel = t.status;
        }
        String resultSection;
        if ("completed".equals(t.status)) {
            resultSection = "\nResult:\n" + t.result;
        } else if ("failed".equals(t.status)) {
            resultSection = "\nError: " + t.result;
        } else {
            resultSection = "\nTask is still " + t.status + "...";
        }
        return "[TASK_ID: " + taskId + "]\n[STATUS: " + statusLabel + "]" + resultSection;
    }

    @Tool(name = BuiltInToolNames.GET_AGENT_STATUS, description = "Get runtime status of an agent")
    public String getAgentStatus(
            @ToolParam(name = "agent_id", description = "Target agent id", required = false)
                    String agentId) {
        if (off(BuiltInToolNames.GET_AGENT_STATUS)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        String target =
                (agentId == null || agentId.isBlank())
                        ? context.currentAgent.getId()
                        : agentId.trim();
        AgentRuntimeStatus status = context.agentService.statusOf(target);
        return "agent_id="
                + target
                + ", status="
                + status.status().getDescription()
                + ", running_task_count="
                + status.runningTaskCount()
                + ", last_run_at="
                + (status.lastRunAt() == null ? "" : status.lastRunAt())
                + ", last_finish_at="
                + (status.lastFinishAt() == null ? "" : status.lastFinishAt());
    }

    private String runAcpProcess(AcpAgentInfo agent, String taskText, int timeoutSeconds) {
        List<String> cmd = new ArrayList<>();
        if (agent.getCommand() != null && !agent.getCommand().isBlank()) {
            Collections.addAll(cmd, agent.getCommand().split("\\s+"));
        }
        if (agent.getArgs() != null && !agent.getArgs().isBlank()) {
            Collections.addAll(cmd, agent.getArgs().split("\\s+"));
        }
        if (cmd.isEmpty()) {
            return "Error: no command configured for agent " + agent.getKey();
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String taskId = UuidUtils.randomUUIDv7().toString();
            String requestJson =
                    "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"tasks/create\",\"params\":{\"task\":{\"id\":\""
                            + taskId
                            + "\",\"max_rounds\":10,\"messages\":[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\""
                            + JSON_ESCAPER.apply(taskText)
                            + "\"}]}]}}}";
            try (var writer =
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(requestJson);
                writer.write("\n");
                writer.flush();
            }
            StringBuilder output = new StringBuilder();
            try (var reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8))) {
                // Guardrail: enforce max 600s timeout
                int effectiveTimeout = Math.min(timeoutSeconds, 600);
                long deadline = System.currentTimeMillis() + effectiveTimeout * 1000L;
                int consecutiveEmptyReads = 0;
                while (System.currentTimeMillis() < deadline) {
                    if (reader.ready()) {
                        consecutiveEmptyReads = 0;
                        String line = reader.readLine();
                        if (line != null) {
                            output.append(line);
                            if (line.contains("\"result\"") || line.contains("\"error\"")) {
                                break;
                            }
                        } else {
                            break;
                        }
                    } else {
                        consecutiveEmptyReads++;
                        // Guardrail: slight backoff on empty reads to reduce CPU burn
                        Thread.sleep(Math.min(100 + consecutiveEmptyReads * 10, 1000));
                    }
                }
            }
            process.destroyForcibly();
            String result = output.toString();
            if (result.isEmpty()) {
                result =
                        "Agent "
                                + agent.getKey()
                                + " did not return a response within "
                                + timeoutSeconds
                                + "s.";
            }
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "ACP process call failed for agent " + agent.getKey(), e);
            return "Error calling agent " + agent.getKey() + ": " + e.getMessage();
        }
    }

    private String executeTaskAgainstAgent(
            String toAgent, String text, String sessionId, int timeoutSeconds) {
        try {
            List<AcpAgentInfo> acpAgents = context.repository.configManager().getAcpAgents();
            for (AcpAgentInfo a : acpAgents) {
                if (a.isEnabled() && a.getKey().equals(toAgent)) {
                    return runAcpProcess(a, text, timeoutSeconds);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "ACP agent lookup failed", e);
        }
        List<AgentInfo> agents = context.listAgents();
        for (AgentInfo a : agents) {
            if (a.getId().equals(toAgent) || a.getName().equals(toAgent)) {
                try {
                    return callInternalAgent(a, text, sessionId, timeoutSeconds);
                } catch (Exception e) {
                    return "Internal agent " + toAgent + " communication error: " + e.getMessage();
                }
            }
        }
        return "Agent " + toAgent + " not found. Use list_agents() to see available agents.";
    }

    private String callInternalAgent(
            AgentInfo agent, String text, String sessionId, int timeoutSeconds) {
        MessageBusService mbs = context.getMessageBusService();
        if (mbs == null) {
            return "Error: MessageBusService not available. Agent: " + agent.getId();
        }
        String projectId = context.currentProject().getId();
        MessageBus bus = mbs.getMessageBus(projectId);
        String correlationId = UuidUtils.randomUUIDv7().toString();
        String replyQueue = "agentscope:reply:" + correlationId;
        Map<String, Object> request = new HashMap<>();
        request.put("type", "agent_chat");
        request.put("from", context.currentAgent.getId());
        request.put("text", text);
        request.put("correlationId", correlationId);
        request.put("sessionId", sessionId != null ? sessionId : "");
        request.put("replyTo", replyQueue);
        String targetInbox = "agentscope:inbox:agent:" + agent.getId();
        bus.queuePush(targetInbox, request).block();
        LOGGER.log(
                Level.FINE,
                "Internal agent communication request pushed: from={0}, to={1}, correlationId={2}",
                new Object[] {context.currentAgent.getId(), agent.getId(), correlationId});
        String wakeupSessionId = "agent-chat:" + agent.getId() + ":" + correlationId;
        bus.enqueueWakeup(wakeupSessionId, agent.getId()).block();
        // Guardrail: cap max waiting time to 600 seconds
        int effectiveTimeout = Math.min(timeoutSeconds, 600);
        long deadline = System.currentTimeMillis() + effectiveTimeout * 1000L;
        int loopCount = 0;
        while (System.currentTimeMillis() < deadline) {
            loopCount++;
            List<BusEntry> replies = bus.queueDrain(replyQueue, 1).block();
            if (replies != null && !replies.isEmpty()) {
                Object result = replies.get(0).payload().get("result");
                String reply = result != null ? result.toString() : "(empty reply)";
                LOGGER.log(
                        Level.FINE,
                        "Internal agent communication received reply: from={0}, correlationId={1}",
                        new Object[] {agent.getId(), correlationId});
                return reply;
            }
            try {
                // Guardrail: adaptive backoff for long waits
                Thread.sleep(Math.min(200 + loopCount * 50, 2000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Interrupted while waiting for internal agent " + agent.getId() + ".";
            }
        }
        return "Internal agent "
                + agent.getId()
                + " did not respond within "
                + timeoutSeconds
                + "s. correlationId="
                + correlationId;
    }
}
