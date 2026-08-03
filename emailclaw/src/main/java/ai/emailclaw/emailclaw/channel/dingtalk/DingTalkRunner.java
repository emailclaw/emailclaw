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
package ai.emailclaw.emailclaw.channel.dingtalk;

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.security.PendingApproval;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.StreamCallback;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.util.ThreadUtils;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionRule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class DingTalkRunner {

    private static final Logger LOGGER = Logger.getLogger(DingTalkRunner.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChannelService channelService;
    private final ChatService chatService;
    private final AgentService agentService;
    private final ProviderService providerService;
    private final HttpClient httpClient;

    private OpenDingTalkClient client;
    private String currentClientId;
    private volatile boolean running = true;

    // Use GovernanceService to manage pending approval requests, no longer using blocking Map in
    // memory

    public DingTalkRunner(
            ChannelService channelService,
            ChatService chatService,
            AgentService agentService,
            ProviderService providerService) {
        this.channelService = channelService;
        this.chatService = chatService;
        this.agentService = agentService;
        this.providerService = providerService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

        startOrUpdateClient();

        // Start a background thread to periodically check if configuration has changed
        // Emailclaw's channel enable/disable mechanism is fully inherited from agentscope-java,
        // implemented as: background virtual thread periodically checks configuration, taking
        // effect in real-time without restart
        Thread.startVirtualThread(
                () -> {
                    while (running) {
                        try {
                            Thread.sleep(5000);
                            if (running) {
                                startOrUpdateClient();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
    }

    private synchronized void startOrUpdateClient() {
        LOGGER.info("Channel invocation started: DingTalk channel client status check and update");
        ChannelInfo info =
                channelService.list().stream()
                        .filter(c -> DingTalkPlugin.ID_DINGTALK.equals(c.getId()))
                        .findFirst()
                        .orElse(null);
        if (info == null || !info.isEnabled() || !DingTalkChannelConfig.isConfigured(info)) {
            if (client != null) {
                try {
                    client.stop();
                } catch (Exception e) {
                }
                client = null;
                currentClientId = null;
                LOGGER.info("DingTalk client stopped (disabled or missing config).");
            }
            return;
        }

        if (client != null && DingTalkChannelConfig.getClientId(info).equals(currentClientId)) {
            // Already running with current config
            return;
        }

        if (client != null) {
            try {
                client.stop();
            } catch (Exception e) {
            }
        }

        try {
            String clientId = DingTalkChannelConfig.getClientId(info);
            String clientSecret = DingTalkChannelConfig.getClientSecret(info);
            LOGGER.info("Starting DingTalk stream client for " + clientId);
            client =
                    OpenDingTalkStreamClientBuilder.custom()
                            .credential(new AuthClientCredential(clientId, clientSecret))
                            .registerCallbackListener(
                                    "/v1.0/im/bot/messages/get",
                                    new OpenDingTalkCallbackListener<String, String>() {
                                        @Override
                                        public String execute(String request) {
                                            handleMessage(request, info);
                                            return "{\"status\":\"OK\"}";
                                        }
                                    })
                            .build();
            client.start();
            currentClientId = clientId;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to start DingTalk client", e);
            client = null;
            currentClientId = null;
        }
    }

    private void handleMessage(String request, ChannelInfo channelInfo) {
        LOGGER.info(
                "Channel invocation started: DingTalk received new message and entered processing"
                        + " flow");
        try {
            JsonNode root = JSON.readTree(request);
            String msgType = root.path("msgtype").asString();
            if (!"text".equals(msgType)) {
                return; // Currently only supports text
            }
            String content = root.path("text").path("content").asString().trim();
            if (channelInfo.getBotPrefix() != null
                    && !channelInfo.getBotPrefix().isEmpty()
                    && content.startsWith(channelInfo.getBotPrefix())) {
                content = content.substring(channelInfo.getBotPrefix().length()).trim();
            }
            final String finalContent = content;

            String senderId = root.path("senderId").asString();
            String conversationId = root.path("conversationId").asString();
            String sessionWebhook = root.path("sessionWebhook").asString();

            if (content.isEmpty() || sessionWebhook.isEmpty()) return;

            // Get default Agent and Model
            AgentInfo agent = agentService.currentDefault();
            if (agent == null) return;

            ProviderInfo provider = chatService.resolveEffectiveProvider(agent);
            if (provider == null) {
                return;
            }
            String modelId = chatService.resolveEffectiveModelId(agent, provider);
            if (modelId == null || modelId.isBlank()) {
                return;
            }

            // Get or create session
            final String finalPrompt = finalContent;
            final ProviderInfo finalProvider = provider;
            final String finalModelId = modelId;

            ThreadUtils.run(
                    () -> {
                        // Find corresponding session under current agent
                        ChatSessionInfo session =
                                chatService.sessions(agent.getId()).stream()
                                        .filter(s -> conversationId.equals(s.getId()))
                                        .findFirst()
                                        .orElseGet(
                                                () -> {
                                                    ChatSessionInfo s =
                                                            chatService.newSession(agent.getId());
                                                    s.setId(conversationId);
                                                    s.setChannel(DingTalkPlugin.ID_DINGTALK);
                                                    s.setUserId(senderId);
                                                    chatService.updateSession(s);
                                                    return s;
                                                });

                        // Governance service approval code reply detection — supports dual codes
                        // (1234 just this once, 5678 approve and remember)
                        GovernanceService guardService = chatService.getGovernanceService();
                        String trimmedContent = finalContent.trim();
                        if (trimmedContent.matches("\\d{4}")) {
                            // First check one-time code
                            Optional<PendingApproval> pendingOpt =
                                    guardService.findPendingByCode(
                                            DingTalkPlugin.ID_DINGTALK,
                                            session.getId(),
                                            senderId,
                                            trimmedContent);
                            if (pendingOpt.isPresent()) {
                                handleApprovalReply(
                                        channelInfo,
                                        session,
                                        sessionWebhook,
                                        pendingOpt.get(),
                                        trimmedContent,
                                        false);
                                return;
                            }
                            // Then check remember code
                            pendingOpt =
                                    guardService.findPendingByRememberCode(
                                            DingTalkPlugin.ID_DINGTALK,
                                            session.getId(),
                                            senderId,
                                            trimmedContent);
                            if (pendingOpt.isPresent()) {
                                handleApprovalReply(
                                        channelInfo,
                                        session,
                                        sessionWebhook,
                                        pendingOpt.get(),
                                        trimmedContent,
                                        true);
                                return;
                            }
                        }

                        chatService.sendMessage(
                                agent,
                                finalProvider,
                                finalModelId,
                                session,
                                finalPrompt,
                                java.util.List.of(),
                                Map.of(
                                        "sessionWebhook",
                                        sessionWebhook,
                                        "messageType",
                                        DingTalkChannelConfig.getMessageType(channelInfo)),
                                new StreamCallback() {
                                    @Override
                                    public void onPart(ChatMessagePart part, boolean startsNew) {
                                        // ignore intermediate parts for dingtalk stream
                                    }

                                    @Override
                                    public void onCompleted(Msg message) {
                                        // Check if there are pending approval requests triggered by
                                        // PermissionEngine that need messages sent
                                        List<PendingApproval> pendingForSession =
                                                guardService.getPendingApprovals().stream()
                                                        .filter(
                                                                p ->
                                                                        session.getId()
                                                                                        .equals(
                                                                                                p
                                                                                                        .getSessionId())
                                                                                && !p.isDelivered())
                                                        .toList();
                                        if (!pendingForSession.isEmpty()) {
                                            String msgType =
                                                    DingTalkChannelConfig.getMessageType(
                                                            channelInfo);
                                            for (PendingApproval approval : pendingForSession) {
                                                sendApprovalRequestMessage(
                                                        sessionWebhook,
                                                        approval.getToolName(),
                                                        String.valueOf(approval.getToolInput()),
                                                        approval.getApprovalCode(),
                                                        approval.getRememberCode(),
                                                        msgType);
                                                guardService.markDelivered(approval.getId());
                                            }
                                        } else {
                                            sendReply(
                                                    sessionWebhook,
                                                    message.getTextContent(),
                                                    DingTalkChannelConfig.getMessageType(
                                                            channelInfo));
                                        }
                                    }
                                });
                    });

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error handling DingTalk message", e);
        }
    }

    private void sendReply(String webhook, String text, String msgType) {
        Thread.startVirtualThread(
                () -> {
                    try {
                        String safeText = JSON.writeValueAsString(text);
                        String body;
                        if ("markdown".equalsIgnoreCase(msgType)) {
                            body =
                                    "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"Reply\",\"text\":"
                                            + safeText
                                            + "}}";
                        } else {
                            body = "{\"msgtype\":\"text\",\"text\":{\"content\":" + safeText + "}}";
                        }

                        HttpRequest req =
                                HttpRequest.newBuilder()
                                        .uri(URI.create(webhook))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .timeout(Duration.ofSeconds(10))
                                        .build();

                        HttpResponse<String> resp =
                                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() != 200) {
                            LOGGER.warning("Failed to send DingTalk reply: " + resp.body());
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error sending reply to webhook", e);
                    }
                });
    }

    // ========== PermissionEngine HITL Approval Flow (Event-Driven) ==========

    /**
     * Send approval request message to user (non-blocking).
     * Called by onCompleted callback when pending approval requests are detected.
     */
    public void sendApprovalRequestMessage(
            String sessionWebhook,
            String toolName,
            String toolInput,
            String code,
            String rememberCode,
            String msgType) {
        if (sessionWebhook == null || sessionWebhook.isBlank()) {
            LOGGER.warning("DingTalk approval message sending failed: missing sessionWebhook");
            return;
        }
        try {
            String content = buildApprovalMessageBody(toolName, toolInput, code, rememberCode);
            sendDirectMessage(sessionWebhook, content, msgType);
            LOGGER.info(
                    "DingTalk approval message sent successfully: oneTimeCode="
                            + code
                            + ", rememberCode="
                            + rememberCode);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "DingTalk failed to send approval message", e);
        }
    }

    /** Build approval message body. */
    private String buildApprovalMessageBody(
            String toolName, String toolInput, String code, String rememberCode) {
        return "Emailclaw detected a high-risk tool call that requires your approval.\n\n"
                + "Reply method (reply directly with the corresponding 4-digit code):\n"
                + "  Reply "
                + code
                + " → Approve for this time only\n"
                + "  Reply "
                + rememberCode
                + " → Approve and remember this decision (automatically allow similar calls next"
                + " time)\n"
                + "  Replying with anything else or not replying will be treated as rejection\n\n"
                + "Tool: "
                + (toolName == null ? "" : toolName)
                + "\n"
                + "Parameters: "
                + compactApprovalText(toolInput == null ? "" : toolInput, 1200)
                + "\n\n";
    }

    private String compactApprovalText(String text, int maxLength) {
        String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    /**
     * Send direct message to user via DingTalk API.
     * Uses sessionWebhook to send message to specific session.
     */
    private void sendDirectMessage(String sessionWebhook, String content, String messageType)
            throws Exception {
        if (sessionWebhook == null || sessionWebhook.isBlank()) {
            throw new IllegalArgumentException("sessionWebhook cannot be empty");
        }

        String safeText = JSON.writeValueAsString(content);
        String body;
        if ("markdown".equalsIgnoreCase(messageType)) {
            body =
                    "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"Emailclaw"
                            + " Approval\",\"text\":"
                            + safeText
                            + "}}";
        } else {
            body = "{\"msgtype\":\"text\",\"text\":{\"content\":" + safeText + "}}";
        }

        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(sessionWebhook))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(10))
                        .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to send message, status code: "
                            + resp.statusCode()
                            + ", response: "
                            + resp.body());
        }
    }

    /**
     * Handle approval code replied by user (event-driven, non-blocking).
     * Creates ConfirmResult and calls resumeWithConfirmResult to resume Agent execution.
     */
    private void handleApprovalReply(
            ChannelInfo channel,
            ChatSessionInfo session,
            String sessionWebhook,
            PendingApproval approval,
            String code,
            boolean remember) {
        LOGGER.info(
                "DingTalk approval code matched successfully: session="
                        + session.getId()
                        + ", tool="
                        + approval.getToolName()
                        + ", remember="
                        + remember);

        ToolUseBlock toolBlock =
                new ToolUseBlock(approval.getId(), approval.getToolName(), approval.getToolInput());
        // Only add ALLOW rule when remember=true ("remember this decision")
        List<PermissionRule> rules =
                remember
                        ? List.of(
                                new PermissionRule(
                                        approval.getToolName(),
                                        null,
                                        PermissionBehavior.ALLOW,
                                        "dingtalk_approved"))
                        : List.of();
        ConfirmResult confirmResult = new ConfirmResult(true, toolBlock, rules);
        chatService.resumeWithConfirmResult(
                approval.getAgentId(),
                session.getId(),
                DingTalkPlugin.ID_DINGTALK,
                approval.getRoute() != null ? approval.getRoute() : Map.of(),
                List.of(confirmResult));

        String msgType = DingTalkChannelConfig.getMessageType(channel);
        try {
            sendReply(
                    sessionWebhook,
                    "Approval code received, tool call approved: " + approval.getToolName(),
                    msgType);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "DingTalk failed to send approval confirmation message", e);
        }
    }

    public synchronized void stop() {
        running = false;
        if (client != null) {
            try {
                client.stop();
                LOGGER.info("DingTalk client stopped successfully.");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping DingTalk client", e);
            }
            client = null;
        }
    }
}
