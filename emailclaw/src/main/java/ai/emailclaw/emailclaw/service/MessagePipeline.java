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

import ai.emailclaw.emailclaw.channel.ChannelIds;
import ai.emailclaw.emailclaw.model.AgentConfiguration;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentStatRecord;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatMessageRoles;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.TokenUsageRecord;
import ai.emailclaw.emailclaw.service.memory.MemoAutoSync;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.storage.AppContext;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.HintBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.VideoBlock;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessagePipeline {

    private static final Logger LOGGER = Logger.getLogger(MessagePipeline.class.getName());

    private static final String EMBEDDED_TITLE_INSTRUCTION =
            "\n\n"
                + "---\n"
                + "At the very end of your response, provide a concise session title (at most 6"
                + " words, no quotes, no trailing punctuation, same language as the conversation)"
                + " on its own line in this exact format:\n"
                + "[TITLE: xxx]";

    private static final int CHAT_STREAM_TIMEOUT_SECONDS = 300;

    private final AppContext repository;

    private final AgentService agentService;

    private final ProviderService providerService;

    private final GovernanceService governanceService;

    private final AgentRuntimeDispatcher agentRuntimeDispatcher;

    private final ChatSessionRepository chatSessionRepository;

    private final ChatService chatService;

    private final ToolRuntimeContext toolRuntimeContext;

    private final MessageBusService messageBusService;

    private final MemoAutoSync memoAutoSync;

    private final SessionTitleGenerator titleGenerator;

    private final Map<String, AgentChatPending> pendingAgentChatReplies = new ConcurrentHashMap<>();

    private record AgentChatPending(String replyTo, String correlationId) {}

    public MessagePipeline(
            AppContext repository,
            AgentService agentService,
            ProviderService providerService,
            GovernanceService governanceService,
            AgentRuntimeDispatcher agentRuntimeDispatcher,
            ChatSessionRepository chatSessionRepository,
            ToolRuntimeContext toolRuntimeContext,
            MessageBusService messageBusService,
            MemoAutoSync memoAutoSync,
            SessionTitleGenerator titleGenerator,
            ChatService chatService) {
        this.repository = repository;
        this.agentService = agentService;
        this.providerService = providerService;
        this.governanceService = governanceService;
        this.agentRuntimeDispatcher = agentRuntimeDispatcher;
        this.chatSessionRepository = chatSessionRepository;
        this.toolRuntimeContext = toolRuntimeContext;
        this.messageBusService = messageBusService;
        this.memoAutoSync = memoAutoSync;
        this.titleGenerator = titleGenerator;
        this.chatService = chatService;
    }

    /**
     * Send a message to the large language model and process the streaming response.
     * Note: This method will not only send the current prompt, but also load the entire session history (memory) through reactAgent.loadIfExists,
     * thereby sending the context of the entire session to the large model at once.
     *
     * @param agent       The current agent information processing the message
     * @param provider    The model provider information
     * @param modelId     The specific model ID used
     * @param sessionInfo The current chat session information
     * @param prompt      The latest prompt sent by the user (message content)
     * @param callback    Callback interface for processing the streaming response content from the model
     */
    public void sendMessage(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            ChatSessionInfo sessionInfo,
            String prompt,
            StreamCallback callback) {
        sendMessage(agent, provider, modelId, sessionInfo, prompt, List.of(), callback);
    }

    public void sendMessage(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            ChatSessionInfo sessionInfo,
            String prompt,
            List<Path> attachmentPaths,
            StreamCallback callback) {
        sendMessage(
                agent, provider, modelId, sessionInfo, prompt, attachmentPaths, Map.of(), callback);
    }

    public void sendMessage(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            ChatSessionInfo sessionInfo,
            String prompt,
            List<Path> attachmentPaths,
            Map<String, Object> route,
            StreamCallback callback) {
        String completedText = "";
        boolean started = false;
        try {
            LOGGER.log(
                    Level.INFO,
                    "Large model invocation started: agent={0}, provider={1}, model={2},"
                            + " session={3}",
                    new Object[] {agent.getId(), provider.getId(), modelId, sessionInfo.getId()});
            toolRuntimeContext.currentAgent = agent;
            // Get previous history to determine if this is the first message
            List<Msg> history = chatService.loadHistory(agent.getId(), sessionInfo.getId());
            boolean firstUserMessage = history.isEmpty();
            // If the session has no custom name, generate a temporary placeholder name based on the
            // first message
            maybeSetPlaceholderSessionName(sessionInfo, prompt);
            AgentConfiguration config = repository.loadAgentConfig(agent.getId());
            // 1. Build the core HarnessAgent agent
            String channel =
                    sessionInfo == null || sessionInfo.getChannel() == null
                            ? ChannelIds.CONSOLE
                            : sessionInfo.getChannel();
            AgentStateStore sessionStore =
                    new ai.emailclaw.emailclaw.service.MergingAgentStateStore(
                            new JsonFileAgentStateStore(chatService.sessionPath(agent.getId())));
            sanitizeSessionMediaSources(sessionStore, sessionInfo.getId());
            // Append title generation instructions to the first message, asking the LLM to output
            // [TITLE: xxx] at the end of the main reply
            // Note: The instruction text will be persisted by AgentScope along with the user
            // message, therefore in partsOf()
            // when reading history, this instruction will be automatically stripped to avoid dirty
            // data when reopening the session.
            String effectivePrompt = prompt;
            if (firstUserMessage && config.isAutoGenerateSessionTitle()) {
                effectivePrompt = prompt + EMBEDDED_TITLE_INSTRUCTION;
            }
            HarnessAgent reactAgent =
                    agentRuntimeDispatcher.buildAgent(
                            agent, provider, modelId, config, channel, sessionInfo.getId());
            // 2. Wrap the message currently sent by the user into a Msg object
            Msg userMsg =
                    buildUserMessage(
                            agent,
                            sessionInfo,
                            effectivePrompt,
                            attachmentPaths,
                            agentRuntimeDispatcher.selectedModelSupportsImage(provider, modelId),
                            agentRuntimeDispatcher.selectedModelSupportsVideo(provider, modelId));
            List<ChatMessagePart> finalParts = new ArrayList<>();
            boolean hasError = false;
            // ── Initialize tracker components ──────────────────────────────────
            Path diffWorkspace =
                    repository.workspaceFor(agent.getId()).toAbsolutePath().normalize();
            FileDiffTracker diffTracker = new FileDiffTracker(diffWorkspace);
            PendingApprovalTracker approvalTracker = new PendingApprovalTracker(governanceService);
            // Declare streaming event handler
            StreamingEventHandler eventHandler =
                    new StreamingEventHandler(
                            diffTracker,
                            approvalTracker,
                            callback,
                            agent.getId(),
                            sessionInfo.getId(),
                            provider.getId(),
                            modelId);

            agentService.markTaskStarted(agent.getId());
            started = true;
            try {
                int attempts = 0;
                int maxRetries = agent.getMaxRetries();
                boolean success = false;
                boolean usingFallback = false;

                while (attempts <= maxRetries && !success) {
                    try {
                        LOGGER.log(
                                Level.INFO,
                                "Large model streaming event processing started: agent={0},"
                                        + " session={1}, attempt={2}",
                                new Object[] {agent.getId(), sessionInfo.getId(), attempts});
                        RuntimeContext runtimeContext =
                                RuntimeContext.builder()
                                        .sessionId(
                                                sessionInfo != null && sessionInfo.getId() != null
                                                        ? sessionInfo.getId()
                                                        : "default")
                                        .build();
                        StreamingEventHandler currentHandler = eventHandler;
                        reactAgent
                                .streamEvents(userMsg, runtimeContext)
                                .doOnNext(event -> currentHandler.handleEvent(event))
                                .doOnComplete(
                                        () -> {
                                            // Event Sourcing reordering has been handled internally
                                            // by the AgentScope framework.
                                        })
                                .blockLast(Duration.ofSeconds(CHAT_STREAM_TIMEOUT_SECONDS));

                        success = true;
                        hasError = false;

                        if (usingFallback) {
                            emitPart(
                                    finalParts,
                                    callback,
                                    ChatMessagePart.HINT,
                                    "HINT",
                                    "",
                                    "",
                                    "⚠️ Primary model unavailable, system has automatically"
                                            + " downgraded to fallback model ["
                                            + agent.getFallbackModelId()
                                            + "] for reply.",
                                    true);
                        }

                    } catch (Throwable e) {
                        hasError = true;
                        attempts++;
                        LOGGER.log(
                                Level.WARNING,
                                "Streaming conversation execution failed, attempt " + attempts,
                                e);

                        if (attempts <= maxRetries) {
                            try {
                                Thread.sleep(1000L * attempts);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        } else if (!usingFallback
                                && agent.getFallbackModelId() != null
                                && !agent.getFallbackModelId().isBlank()) {
                            // Trigger fallback
                            usingFallback = true;
                            attempts =
                                    0; // Reset attempts, allowing fallback model to retry (or only
                            // one chance, here reset means it also enjoys retries)
                            LOGGER.log(
                                    Level.INFO,
                                    "Primary model exceeded max retries, attempting to switch to"
                                            + " fallback model: {0}",
                                    agent.getFallbackModelId());

                            ProviderInfo fallbackProvider =
                                    providerService.listProviders().stream()
                                            .filter(
                                                    p ->
                                                            p.getId()
                                                                    .equals(
                                                                            agent
                                                                                    .getFallbackProviderId()))
                                            .findFirst()
                                            .orElse(provider);

                            reactAgent =
                                    agentRuntimeDispatcher.buildAgent(
                                            agent,
                                            fallbackProvider,
                                            agent.getFallbackModelId(),
                                            config,
                                            channel,
                                            sessionInfo.getId());

                            eventHandler =
                                    new StreamingEventHandler(
                                            diffTracker,
                                            approvalTracker,
                                            callback,
                                            agent.getId(),
                                            sessionInfo.getId(),
                                            fallbackProvider.getId(),
                                            agent.getFallbackModelId());
                        } else {
                            LOGGER.log(
                                    Level.SEVERE,
                                    "Streaming conversation final execution failed",
                                    e);
                            String err =
                                    "\n[Error] " + e.getClass().getName() + ": " + e.getMessage();
                            emitPart(
                                    finalParts,
                                    callback,
                                    ChatMessagePart.ERROR,
                                    "ERROR",
                                    "",
                                    "",
                                    err,
                                    true);
                            break;
                        }
                    }
                }
            } finally {
                try {
                    agentService.markTaskFinished(agent.getId());
                } catch (Exception markErr) {
                    LOGGER.log(Level.WARNING, "markTaskFinished failed (ignored)", markErr);
                }
                if (reactAgent != null) {
                    try {
                        reactAgent.close();
                    } catch (Exception closeErr) {
                        LOGGER.log(Level.WARNING, "reactAgent.close() failed", closeErr);
                    }
                }
                started = false;
            }
            // Get aggregated streaming parts from StreamingEventHandler
            finalParts = eventHandler.getFinalParts();
            Msg completedMsg =
                    Msg.builder()
                            .name(ChatMessageRoles.ASSISTANT)
                            .role(MsgRole.ASSISTANT)
                            .content(toContentBlocks(finalParts))
                            .timestamp(LocalDateTime.now().toString())
                            .build();
            completedText = completedMsg.getTextContent();
            if (hasError) {
                // When an exception occurs, the Reactor stream of AgentScope is interrupted,
                // usually not triggering the default persistence operation.
                // Therefore, we must manually flush the user input of this round and the output
                // containing the error information to disk, to prevent record loss.
                Msg errorUserMsg =
                        Msg.builder()
                                .name(ChatMessageRoles.USER)
                                .role(MsgRole.USER)
                                .content(
                                        List.of(
                                                TextBlock.builder()
                                                        .text(userMsg.getTextContent())
                                                        .build()))
                                .timestamp(userMsg.getTimestamp())
                                .build();
                chatService.appendHistoryMsg(agent.getId(), sessionInfo.getId(), errorUserMsg);
                chatService.appendHistoryMsg(agent.getId(), sessionInfo.getId(), completedMsg);
            }
            recordUsage(agent.getId(), provider.getId(), modelId, prompt, completedText);
            // Use PostStreamHandler to execute post-processing logic (title generation, memory
            // synchronization, internal communication reply)
            if (!hasError) {
                PostStreamHandler postHandler =
                        new PostStreamHandler(
                                sessionStore,
                                sessionInfo,
                                agent.getId(),
                                memoAutoSync,
                                chatService,
                                titleGenerator);
                postHandler.executePostProcessing(
                        completedText, firstUserMessage, config.isAutoGenerateSessionTitle());
            }
            LOGGER.log(Level.INFO, "Final result output started: session={0}", sessionInfo.getId());
            safeOnCompleted(callback, completedMsg);
        } catch (Throwable fatal) {
            LOGGER.log(Level.SEVERE, "sendMessage encountered unhandled exception", fatal);
            if (started) {
                try {
                    agentService.markTaskFinished(agent.getId());
                } catch (Exception ignore) {
                    LOGGER.log(
                            Level.FINE,
                            "markTaskFinished secondary fallback failed (ignored)",
                            ignore);
                }
            }
            String fatalText = "[Error] " + fatal.getClass().getName() + ": " + fatal.getMessage();
            ChatMessagePart fatalPart =
                    ChatMessagePart.block(ChatMessagePart.ERROR, "ERROR", fatalText);
            Msg fatalMsg =
                    Msg.builder()
                            .name(ChatMessageRoles.ASSISTANT)
                            .role(MsgRole.ASSISTANT)
                            .content(List.of(TextBlock.builder().text(fatalText).build()))
                            .timestamp(LocalDateTime.now().toString())
                            .build();
            Msg fatalUserMsg =
                    Msg.builder()
                            .name(ChatMessageRoles.USER)
                            .role(MsgRole.USER)
                            .content(List.of(TextBlock.builder().text(prompt).build()))
                            .timestamp(LocalDateTime.now().toString())
                            .build();
            chatService.appendHistoryMsg(agent.getId(), sessionInfo.getId(), fatalUserMsg);
            chatService.appendHistoryMsg(agent.getId(), sessionInfo.getId(), fatalMsg);
            ChatService.safeOnPart(callback, fatalPart, true);
            safeOnCompleted(callback, fatalMsg);
        }
    }

    /**
     * If the session has no name, temporarily intercept a segment of text from the user input as the session name.
     */
    private void maybeSetPlaceholderSessionName(ChatSessionInfo session, String prompt) {
        String current = session.getName() == null ? "" : session.getName().trim();
        if (!current.isBlank() && !"New Chat".equalsIgnoreCase(current)) {
            LOGGER.log(
                    Level.FINE,
                    "Session already has custom name, skipping placeholder setting: session={0},"
                            + " name={1}",
                    new Object[] {session.getId(), current});
            return;
        }
        String fallback = truncateSessionName(prompt);
        if (fallback.isBlank()) {
            return;
        }
        session.setName(fallback);
        chatService.updateSession(session);
        LOGGER.log(
                Level.INFO,
                "Session placeholder name setting completed: session={0}, name={1}",
                new Object[] {session.getId(), fallback});
    }

    private void sanitizeSessionMediaSources(AgentStateStore session, String sessionId) {
        if (session == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            chatService.sanitizeMemoryMessages(session, ChatService.SESSION_USER_ID, sessionId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Session media history sanitation failed (ignored)", e);
        }
    }

    private Msg buildUserMessage(
            AgentInfo agent,
            ChatSessionInfo sessionInfo,
            String prompt,
            List<Path> attachmentPaths,
            boolean supportsImage,
            boolean supportsVideo) {
        List<ChatService.StagedAttachment> staged =
                chatService.stageAttachments(agent, sessionInfo, attachmentPaths);
        List<ContentBlock> content = new ArrayList<>();
        String textPrompt = prompt == null ? "" : prompt;
        if (textPrompt.isBlank() && !staged.isEmpty()) {
            textPrompt = "Please review the files I uploaded and provide assistance.";
        }
        if (!textPrompt.isBlank()) {
            content.add(TextBlock.builder().text(textPrompt).build());
        }
        List<String> nonMediaHints = new ArrayList<>();
        for (ChatService.StagedAttachment item : staged) {
            if (chatService.isImageFile(item.path) && supportsImage) {
                Base64Source source = chatService.toBase64Source(item.path, "image/png");
                if (source != null) {
                    content.add(ImageBlock.builder().source(source).build());
                    continue;
                }
                nonMediaHints.add(
                        item.relativePath
                                + " (Original filename: "
                                + item.originalName
                                + ", Image encoding failed)");
            } else if (chatService.isVideoFile(item.path) && supportsVideo) {
                Base64Source source = chatService.toBase64Source(item.path, "video/mp4");
                if (source != null) {
                    content.add(VideoBlock.builder().source(source).build());
                    continue;
                }
                nonMediaHints.add(
                        item.relativePath
                                + " (Original filename: "
                                + item.originalName
                                + ", Video encoding failed)");
            } else {
                nonMediaHints.add(
                        item.relativePath + " (Original filename: " + item.originalName + ")");
                String inline = chatService.inlineTextAttachment(item.path);
                if (!inline.isBlank()) {
                    nonMediaHints.add("`" + item.relativePath + "` Content excerpt:\n" + inline);
                }
            }
        }
        if (!nonMediaHints.isEmpty()) {
            StringBuilder hint = new StringBuilder();
            if (!content.isEmpty()) {
                hint.append("\n\n");
            }
            hint.append("The following files have been attached, please read as needed:\n");
            for (String line : nonMediaHints) {
                hint.append("- ").append(line).append('\n');
            }
            content.add(TextBlock.builder().text(hint.toString().trim()).build());
        }
        if (content.isEmpty()) {
            content.add(TextBlock.builder().text("").build());
        }
        return Msg.builder()
                .name(ChatMessageRoles.USER)
                .role(MsgRole.USER)
                .content(content)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    private String truncateSessionName(String prompt) {
        String line = (prompt == null ? "" : prompt).trim().replace("\n", " ");
        if (line.isBlank()) {
            return "New Chat";
        }
        return line.length() > 30 ? line.substring(0, 30).trim() + "…" : line;
    }

    /**
     * Record the Token usage of this conversation and the statistics of the agent (such as the number of conversations).
     * This is a simple estimation logic, roughly calculating the number of Tokens by dividing the character length by 4.
     *
     * @param agentId    Agent ID
     * @param providerId Model provider ID
     * @param modelId    Used model ID
     * @param prompt     Message content sent by user
     * @param output     Message content returned by large model
     */
    private void recordUsage(
            String agentId, String providerId, String modelId, String prompt, String output) {
        long promptTokens = Math.max(1, prompt.length() / 4L);
        long completionTokens = Math.max(1, output.length() / 4L);
        // cachedTokens cannot be accurately estimated from plain text, defaulting to 0; can
        // integrate real ChatUsage data later
        long cachedTokens = 0;
        List<TokenUsageRecord> usage = repository.loadTokenUsage();
        TokenUsageRecord u =
                new TokenUsageRecord(
                        LocalDate.now().toString(),
                        providerId,
                        modelId,
                        promptTokens,
                        completionTokens,
                        cachedTokens);
        usage.add(u);
        repository.saveTokenUsage(usage);
        LOGGER.log(
                Level.FINE,
                "Recording token usage: agent={0}, provider={1}, model={2}, promptTokens={3},"
                        + " completionTokens={4}, cachedTokens={5}",
                new Object[] {
                    agentId, providerId, modelId, promptTokens, completionTokens, cachedTokens
                });
        List<AgentStatRecord> stats = repository.loadAgentStats();
        AgentStatRecord s = new AgentStatRecord(LocalDate.now().toString(), agentId, 1, 0);
        stats.add(s);
        repository.saveAgentStats(stats);
    }

    /**
     * Write a streaming increment simultaneously to the aggregate result and the UI callback.
     *
     * <p>{@code forceNew=true} means a new UI block must be opened; otherwise it will preferentially append to the last segment of the same type and ID.
     */
    private void emitPart(
            List<ChatMessagePart> aggregateParts,
            StreamCallback callback,
            String type,
            String title,
            String id,
            String toolName,
            String delta,
            boolean forceNew) {
        ChatService.emitPartStatic(
                aggregateParts, callback, type, title, id, toolName, delta, forceNew);
    }

    private void safeOnCompleted(StreamCallback callback, Msg message) {
        if (callback == null) {
            return;
        }
        try {
            callback.onCompleted(message);
        } catch (Exception callbackErr) {
            LOGGER.log(Level.WARNING, "onCompleted callback exception (ignored)", callbackErr);
        }
    }

    /**
     * Losslessly convert {@link ChatMessagePart} list to AgentScope {@link ContentBlock} list.
     *
     * <p>Inverse operation with {@link #partsOf(Msg)}, ensuring the complete reversibility of {@code ChatMessagePart → ContentBlock → Msg}.
     * Tool call arguments ({@code toolInput}) and multimodal sub-parts ({@code subParts}) are also preserved.
     */
    public List<ContentBlock> toContentBlocks(List<ChatMessagePart> parts) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (parts == null) {
            return blocks;
        }
        for (ChatMessagePart part : parts) {
            if (part == null) {
                continue;
            }
            String type = ChatMessagePart.normalizeType(part.getType());
            switch (type) {
                case ChatMessagePart.TEXT:
                    if (part.getText() != null && !part.getText().isEmpty()) {
                        blocks.add(TextBlock.builder().text(part.getText()).build());
                    }
                    break;
                case ChatMessagePart.THINKING:
                    if (part.getText() != null && !part.getText().isEmpty()) {
                        blocks.add(ThinkingBlock.builder().thinking(part.getText()).build());
                    }
                    break;
                case ChatMessagePart.TOOL_CALL:
                    {
                        String toolId =
                                part.getId() == null || part.getId().isBlank() ? "" : part.getId();
                        String toolName =
                                part.getToolName() == null || part.getToolName().isBlank()
                                        ? "unknown"
                                        : part.getToolName();
                        Map<String, Object> input =
                                part.getToolInput() != null && !part.getToolInput().isEmpty()
                                        ? part.getToolInput()
                                        : chatService.parseJsonStringToMap(part.getText());
                        blocks.add(
                                ToolUseBlock.builder()
                                        .id(toolId)
                                        .name(toolName)
                                        .input(input)
                                        .content(part.getText())
                                        .build());
                        break;
                    }
                case ChatMessagePart.TOOL_RESULT:
                    {
                        String toolId =
                                part.getId() == null || part.getId().isBlank() ? "" : part.getId();
                        String toolName =
                                part.getToolName() == null || part.getToolName().isBlank()
                                        ? "unknown"
                                        : part.getToolName();
                        List<ContentBlock> output;
                        if (part.getSubParts() != null && !part.getSubParts().isEmpty()) {
                            output = toContentBlocks(part.getSubParts());
                        } else {
                            output =
                                    List.of(
                                            TextBlock.builder()
                                                    .text(
                                                            part.getText() == null
                                                                    ? ""
                                                                    : part.getText())
                                                    .build());
                        }
                        blocks.add(
                                ToolResultBlock.builder()
                                        .id(toolId)
                                        .name(toolName)
                                        .output(output)
                                        .build());
                        break;
                    }
                case ChatMessagePart.HINT:
                    blocks.add(new HintBlock("", part.getText() == null ? "" : part.getText(), ""));
                    break;
                case ChatMessagePart.ERROR:
                    blocks.add(
                            TextBlock.builder()
                                    .text(
                                            "[ERROR] "
                                                    + (part.getText() == null
                                                            ? ""
                                                            : part.getText()))
                                    .build());
                    break;
                default:
                    if (part.getText() != null && !part.getText().isEmpty()) {
                        blocks.add(TextBlock.builder().text(part.getText()).build());
                    }
                    break;
            }
        }
        return blocks;
    }

    /**
     * Drain the agent_chat inbox of the current agent, replying the inference result to the waiting caller agent.
     *
     * <p>Prioritize using the context registered by runWakeup() in {@link #pendingAgentChatReplies};
     * if not exists, fallback to draining the {@code agentscope:inbox:agent:{agentId}} queue.
     */
    void drainAndReplyAgentChat(String agentId, String responseText) {
        MessageBus bus = messageBusService.getMessageBus();
        // 1) Prioritize checking pending reply contexts registered by runWakeup()
        AgentChatPending pending;
        synchronized (this) {
            pending = pendingAgentChatReplies.remove(agentId);
        }
        if (pending != null) {
            Map<String, Object> reply = new HashMap<>();
            reply.put("result", responseText);
            reply.put("correlationId", pending.correlationId());
            bus.queuePush(pending.replyTo(), reply).block();
            LOGGER.log(
                    Level.FINE,
                    "Internal agent communication reply sent (pending context): {0}",
                    pending.replyTo());
            return;
        }
        // 2) Fallback to inbox queue drain
        String inboxKey = "agentscope:inbox:agent:" + agentId;
        List<BusEntry> entries = bus.queueDrain(inboxKey, 10).block();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (BusEntry entry : entries) {
            Map<String, Object> payload = entry.payload();
            if (!"agent_chat".equals(payload.get("type"))) {
                continue;
            }
            Object replyTo = payload.get("replyTo");
            if (replyTo == null || replyTo.toString().isBlank()) {
                continue;
            }
            Map<String, Object> reply = new HashMap<>();
            reply.put("result", responseText);
            reply.put("correlationId", payload.get("correlationId"));
            bus.queuePush(replyTo.toString(), reply).block();
            LOGGER.log(
                    Level.FINE,
                    "Internal agent communication reply sent (queueDrain): {0}",
                    replyTo);
        }
    }

    /**
     * Register a context waiting for agent_chat reply.
     * Called by WakeupDispatcherService.runWakeup(),
     * removed after consumption by drainAndReplyAgentChat().
     *
     * @param agentId       Target agent ID
     * @param replyTo       Reply queue name
     * @param correlationId Correlation ID
     */
    public synchronized void registerPendingAgentChatReply(
            String agentId, String replyTo, String correlationId) {
        if (replyTo != null && !replyTo.isBlank() && agentId != null && !agentId.isBlank()) {
            pendingAgentChatReplies.put(agentId, new AgentChatPending(replyTo, correlationId));
        }
    }
}
