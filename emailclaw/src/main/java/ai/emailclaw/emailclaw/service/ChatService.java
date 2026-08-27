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
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatMessageRecord;
import ai.emailclaw.emailclaw.model.ChatMessageRoles;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.storage.WorkspacePaths;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.VideoBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Chat session service.
 *
 * <p>Responsible for message routing, session management, history persistence, and basic usage stats.
 */
public class ChatService {

    public void sendMessage(
            String agentId,
            String sessionId,
            String prompt,
            List<Path> attachmentPaths,
            Map<String, Object> route,
            StreamCallback callback) {
        AgentInfo agent = agentService.findById(agentId).orElse(null);
        if (agent == null) return;
        ProviderInfo provider = providerService.getById(agent.getProviderId()).orElse(null);
        String modelId =
                agent.getModelId() != null && !agent.getModelId().isBlank()
                        ? agent.getModelId()
                        : provider.allModels().stream()
                                .findFirst()
                                .map(m -> m.getId())
                                .orElse(null);
        sendMessage(agent, provider, modelId, sessionId, prompt, attachmentPaths, route, callback);
    }

    public void sendMessage(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            String sessionId,
            String prompt,
            List<Path> attachmentPaths,
            Map<String, Object> route,
            StreamCallback callback) {
        ChatSessionInfo sessionInfo = findSession(sessionId);
        if (sessionInfo == null) {
            sessionInfo = createSession(agent.getId(), sessionId, "New Session", null);
        }
        sendMessage(
                agent, provider, modelId, sessionInfo, prompt, attachmentPaths, route, callback);
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
        messagePipeline.sendMessage(
                agent, provider, modelId, sessionInfo, prompt, attachmentPaths, route, callback);
    }

    public void sendMessage(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            ChatSessionInfo sessionInfo,
            String prompt,
            StreamCallback callback) {
        messagePipeline.sendMessage(
                agent, provider, modelId, sessionInfo, prompt, List.of(), Map.of(), callback);
    }

    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());

    private static final long CHAT_ATTACHMENT_MAX_BYTES = 10L * 1024L * 1024L;

    private static final long TEXT_ATTACHMENT_INLINE_MAX_BYTES = 64L * 1024L;

    private static final String AGENT_STATE_KEY = "agent_state";

    private static final String MEMORY_MESSAGES_KEY = "memory_messages";

    /**
     * Emailclaw does not pass RuntimeContext.userId, so AgentScope persists the session to an anonymous namespace.
     */
    static final String SESSION_USER_ID = null;

    private static final Set<String> IMAGE_EXTS =
            Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".tif", ".tiff");

    private static final Set<String> VIDEO_EXTS =
            Set.of(".mp4", ".mov", ".avi", ".mkv", ".webm", ".mpeg", ".mpg", ".m4v");

    private static final Set<String> TEXT_EXTS =
            Set.of(
                    ".txt",
                    ".md",
                    ".markdown",
                    ".json",
                    ".yaml",
                    ".yml",
                    ".xml",
                    ".csv",
                    ".tsv",
                    ".log",
                    ".java",
                    ".py",
                    ".js",
                    ".ts",
                    ".tsx",
                    ".jsx",
                    ".html",
                    ".css",
                    ".sql",
                    ".sh",
                    ".bat",
                    ".ps1",
                    ".properties",
                    ".toml",
                    ".ini");

    /** Tool result length exceeding this threshold (chars) triggers automatic offloading to prevent UI lag. */
    private static final int TOOL_RESULT_OFFLOAD_THRESHOLD = 10000;

    /** Directory for temporarily caching offloaded files. */
    private static final Path OFFLOAD_DIR = AppHomeConstants.HOME_RESOLVED.resolve(".offloads");

    private final AppContext repository;

    private final AgentService agentService;

    private final ProviderService providerService;

    private final ToolRuntimeContext toolRuntimeContext;

    /**
     * Governance service: Tool call security analysis and approval management.
     */
    private final GovernanceService governanceService;

    /**
     * Context table waiting for agent_chat reply.
     * Set by WakeupDispatcherService.runWakeup(),
     * removed after consumption by drainAndReplyAgentChat().
     */
    private final Map<String, AgentChatPending> pendingAgentChatReplies = new ConcurrentHashMap<>();

    private record AgentChatPending(String replyTo, String correlationId) {}

    /**
     * edit_file / write_file tool name constants, used for diff calculation.
     */
    private static final String DIFF_EDIT_FILE = "edit_file";

    private static final String DIFF_WRITE_FILE = "write_file";

    private MessagePipeline messagePipeline;

    private final AgentRuntimeDispatcher agentRuntimeDispatcher;

    public ChatService(
            AppContext repository,
            AgentService agentService,
            ProviderService providerService,
            ToolRuntimeContext toolRuntimeContext,
            GovernanceService governanceService,
            AgentRuntimeDispatcher agentRuntimeDispatcher) {
        this.repository = repository;
        this.agentService = agentService;
        this.providerService = providerService;
        this.toolRuntimeContext = toolRuntimeContext;
        this.governanceService = governanceService;
        this.agentRuntimeDispatcher = agentRuntimeDispatcher;
        LOGGER.log(
                Level.INFO,
                "ChatService initialization complete (file diff logic extracted to"
                        + " FileDiffTracker)");
    }

    public void setMessagePipeline(MessagePipeline messagePipeline) {
        this.messagePipeline = messagePipeline;
    }

    /**
     * Get the application runtime context repository.
     */
    public AppContext repository() {
        return repository;
    }

    public ToolRuntimeContext toolRuntimeContext() {
        return toolRuntimeContext;
    }

    public ProviderInfo resolveEffectiveProvider(AgentInfo agent) {
        if (agent == null) {
            return null;
        }
        if (agent.getProviderId() != null && !agent.getProviderId().isBlank()) {
            ProviderInfo provider = providerService.getById(agent.getProviderId()).orElse(null);
            if (provider != null) {
                return provider;
            }
        }
        return providerService.listProviders().stream().findFirst().orElse(null);
    }

    public String resolveEffectiveModelId(AgentInfo agent, ProviderInfo provider) {
        if (provider == null) {
            return null;
        }
        if (agent != null && agent.getModelId() != null && !agent.getModelId().isBlank()) {
            return agent.getModelId();
        }
        return provider.allModels().stream().findFirst().map(m -> m.getId()).orElse(null);
    }

    /**
     * Resume session execution suspended by PermissionEngine via ConfirmResult.
     *
     * <p>Called when email approval code reply arrives, constructing a resume message and waking up the Agent to continue execution.
     * The Agent's ASKING state is persisted in AgentStateStore and can be recovered even if JVM restarts.
     *
     * @param agentId        Agent ID
     * @param sessionId      Session ID
     * @param channel        Channel ID
     * @param route          Routing metadata (e.g. originalSubject)
     * @param confirmResults List of user approval results
     * @return Final Msg after resuming execution
     */
    public Msg resumeWithConfirmResult(
            String agentId,
            String sessionId,
            String channel,
            Map<String, Object> route,
            List<ConfirmResult> confirmResults) {
        try {
            AgentInfo agent = agentService.currentDefault();
            if (agent == null) {
                LOGGER.log(Level.WARNING, "resumeWithConfirmResult: No available Agent");
                return null;
            }
            ProviderInfo provider = resolveEffectiveProvider(agent);
            if (provider == null || provider.allModels().isEmpty()) {
                LOGGER.log(Level.WARNING, "resumeWithConfirmResult: No available Provider");
                return null;
            }
            String modelId = resolveEffectiveModelId(agent, provider);
            if (modelId == null || modelId.isBlank()) {
                LOGGER.log(Level.WARNING, "resumeWithConfirmResult: No available Model");
                return null;
            }
            AgentConfiguration config = repository.loadAgentConfig(agent.getId());
            Msg result;
            try (HarnessAgent reactAgent =
                    agentRuntimeDispatcher.buildAgent(
                            agent, provider, modelId, config, channel, sessionId)) {
                Msg resumeMsg =
                        Msg.builder()
                                .role(MsgRole.USER)
                                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                                .build();
                result =
                        reactAgent
                                .call(List.of(resumeMsg))
                                .block(
                                        Duration.ofSeconds(
                                                config.effectiveTaskExecutionTimeoutSeconds()));

                // Persist resume result to history
                if (result != null) {
                    appendHistoryMsg(agent.getId(), sessionId, resumeMsg);
                    appendHistoryMsg(agent.getId(), sessionId, result);
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "resumeWithConfirmResult failed", e);
            return null;
        }
    }

    /**
     * Check if a specific session has PendingApprovals waiting for email approval.
     * EmailclawRunner calls this in onCompleted to determine whether to send an approval email.
     */
    public boolean hasPendingApprovalForSession(String sessionId) {
        return governanceService.getPendingApprovals().stream()
                .anyMatch(p -> sessionId.equals(p.getSessionId()));
    }

    public GovernanceService getGovernanceService() {
        return governanceService;
    }

    /**
     * Load history message records for a specific agent and session.
     *
     * @param agentId   Agent ID
     * @param sessionId Session ID
     * @return List of history messages
     */
    public List<Msg> loadHistory(String agentId, String sessionId) {
        AgentStateStore session =
                new ai.emailclaw.emailclaw.service.MergingAgentStateStore(
                        new JsonFileAgentStateStore(sessionPath(agentId)));
        io.agentscope.core.state.AgentState state = loadAgentState(session, sessionId);
        List<Msg> msgs = state != null ? state.getContext() : null;
        return msgs != null ? new ArrayList<>(msgs) : new ArrayList<>();
    }

    private io.agentscope.core.state.AgentState loadAgentState(
            AgentStateStore session, String sessionId) {
        if (session == null || sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Optional<io.agentscope.core.state.AgentState> current =
                session.get(
                        SESSION_USER_ID,
                        sessionId,
                        AGENT_STATE_KEY,
                        io.agentscope.core.state.AgentState.class);
        if (current.isPresent()) {
            return current.get();
        }
        return null;
    }

    /**
     * Append a history message to a specific session and persist to disk immediately.
     *
     * <p>Uses {@link #toContentBlocks(List)} to losslessly convert structured parts to AgentScope ContentBlock list,
     * without losing type/id/toolName/toolInput/subParts information.
     *
     * <p>If there is an active streaming call ({@link ToolRuntimeContext#getLiveRuntimeContext()} not null),
     * it synchronously injects the message into the Agent's in-memory {@link io.agentscope.core.state.AgentState},
     * preventing the Agent from overwriting the approval record just written to disk by the UI thread when it ends.
     */
    public void appendHistory(String agentId, String sessionId, ChatMessageRecord record) {
        if (record == null) {
            return;
        }
        Msg msg = chatMessageRecordToMsg(record);
        appendHistoryMsg(agentId, sessionId, msg);
    }

    /**
     * Directly append {@link Msg} to session history and persist to disk immediately.
     *
     * <p>This is the core persistence method. The {@code content} of Msg is {@code List<ContentBlock>},
     * ensuring that tool call parameters, thinking processes, and multi-modal content are completely preserved.
     *
     * <p>For TOOL result messages, it will automatically find its corresponding TOOL CALL message and insert it afterwards,
     * ensuring that ASSISTANT(tool_calls) and TOOL(result) always appear in strict pairs when saved to database.
     */
    public void appendHistoryMsg(String agentId, String sessionId, Msg msg) {
        if (msg == null) {
            return;
        }
        try {
            AgentStateStore session =
                    new ai.emailclaw.emailclaw.service.MergingAgentStateStore(
                            new JsonFileAgentStateStore(sessionPath(agentId)));
            io.agentscope.core.state.AgentState state =
                    Optional.ofNullable(loadAgentState(session, sessionId))
                            .orElseGet(
                                    () ->
                                            io.agentscope.core.state.AgentState.builder()
                                                    .sessionId(sessionId)
                                                    .build());
            List<Msg> context = state.contextMutable();
            if (msg.getRole() == MsgRole.TOOL
                    && !msg.getContentBlocks(ToolResultBlock.class).isEmpty()) {
                // TOOL result message: insert it after its corresponding TOOL CALL
                int insertionIndex = findToolCallInsertionIndex(context, msg);
                if (insertionIndex >= 0) {
                    context.add(insertionIndex + 1, msg);
                    LOGGER.log(
                            Level.FINE,
                            "TOOL result inserted after TOOL CALL, agent={0}, session={1}",
                            new Object[] {agentId, sessionId});
                } else {
                    context.add(msg);
                    LOGGER.log(
                            Level.FINE,
                            "Matching TOOL CALL not found, TOOL result appended to end, agent={0},"
                                    + " session={1}",
                            new Object[] {agentId, sessionId});
                }
            } else {
                context.add(msg);
            }
            session.save(SESSION_USER_ID, sessionId, AGENT_STATE_KEY, state);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to append session history Msg", e);
        }
    }

    /**
     *  Find the insertion index for the corresponding TOOL CALL of a TOOL result message in the context.
     *  Search backwards to find the last message with an ASSISTANT role that contains a matching ToolUseBlock.
     * targetIds and ToolUseBlock's id have a one-to-one matching key relationship.
     * In the LLM's tool call protocol:
     * - TOOL CALL (ToolUseBlock) has an id, e.g. "call_abc123"
     * - TOOL RESULT (ToolResultBlock) also has an id, and its value is identical to the corresponding TOOL CALL's id
     * This is a protocol convention of the LLM API - TOOL RESULT uses the id to declare "which TOOL CALL it is answering".
     *  @param context      Current session's message list
     *  @param toolResultMsg The TOOL result message to be inserted
     *  @return The index of the matching TOOL CALL message, returns -1 if not found
     */
    private static int findToolCallInsertionIndex(List<Msg> context, Msg toolResultMsg) {
        // Collect IDs of all ToolResultBlocks in the TOOL result
        Set<String> targetIds = new HashSet<>();
        for (ToolResultBlock block : toolResultMsg.getContentBlocks(ToolResultBlock.class)) {
            String id = block.getId();
            if (id != null && !id.isBlank()) {
                targetIds.add(id);
            }
        }
        if (targetIds.isEmpty()) {
            return -1;
        }
        // Search backwards to find the last ASSISTANT message containing a matching ToolUseBlock
        for (int i = context.size() - 1; i >= 0; i--) {
            Msg m = context.get(i);
            if (m.getRole() == MsgRole.ASSISTANT) {
                for (ToolUseBlock block : m.getContentBlocks(ToolUseBlock.class)) {
                    if (targetIds.contains(block.getId())) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Losslessly convert {@link ChatMessageRecord} (UI layer DTO) to {@link Msg} (persisted authoritative data source).
     */
    private Msg chatMessageRecordToMsg(ChatMessageRecord record) {
        MsgRole role;
        if (ChatMessageRoles.USER.equalsIgnoreCase(record.getRole())) {
            role = MsgRole.USER;
        } else if (ChatMessageRoles.SYSTEM.equalsIgnoreCase(record.getRole())) {
            role = MsgRole.SYSTEM;
        } else {
            role = MsgRole.ASSISTANT;
        }
        List<ContentBlock> blocks = messagePipeline.toContentBlocks(record.getParts());
        if (blocks.isEmpty()) {
            blocks.add(TextBlock.builder().text("").build());
        }
        return Msg.builder()
                .name(record.getRole())
                .role(role)
                .content(blocks)
                .timestamp(record.getCreatedAt())
                .build();
    }

    /**
     * Try to parse a string into a JSON Map; returns an empty Map if parsing fails.
     */
    Map<String, Object> parseJsonStringToMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "parseJsonStringToMap failed, returning empty Map: " + json, e);
            return Map.of();
        }
    }

    /**
     * Create a new chat session for a specific agent.
     *
     * @param agentId Agent ID
     * @return Newly created session info
     */
    public ChatSessionInfo newSession(String agentId) {
        LOGGER.log(Level.INFO, "Created new session: agent={0}", agentId);
        List<ChatSessionInfo> sessions = repository.loadSessions();
        ChatSessionInfo info = repository.createSession(agentId);
        sessions.add(0, info);
        repository.saveSessions(sessions);
        return info;
    }

    /**
     * Create a chat session with specific ID, name and channel.
     *
     * <p>Used in scenarios like scheduled tasks: when saving a task, if the user hasn't selected a target session, a new session is automatically created based on the task name.
     *
     * @param agentId   Agent ID
     * @param sessionId Session ID (usually UUID v7)
     * @param name      Session display name
     * @param channel   Dispatch channel, uses {@code console} if empty
     * @return Newly created and persisted session info
     */
    public ChatSessionInfo createSession(
            String agentId, String sessionId, String name, String channel) {
        LOGGER.log(
                Level.INFO,
                "Created specific session: agent={0}, id={1}, name={2}, channel={3}",
                new Object[] {agentId, sessionId, name, channel});
        List<ChatSessionInfo> sessions = new ArrayList<>(repository.loadSessions());
        ChatSessionInfo info = new ChatSessionInfo();
        info.setId(sessionId);
        info.setAgentId(agentId);
        info.setName(name != null && !name.isBlank() ? name : "New Chat");
        info.setChannel(channel != null && !channel.isBlank() ? channel : ChannelIds.CONSOLE);
        String now = LocalDateTime.now().toString();
        info.setCreatedAt(now);
        info.setUpdatedAt(now);
        sessions.add(0, info);
        repository.saveSessions(sessions);
        LOGGER.log(
                Level.INFO,
                "Specific session creation complete: id={0}, name={1}",
                new Object[] {info.getId(), info.getName()});
        return info;
    }

    /**
     * Get the list of all chat sessions for a specific agent.
     *
     * @param agentId Agent ID
     * @return List of session info
     */
    public List<ChatSessionInfo> sessions(String agentId) {
        return repository.loadSessions().stream()
                .filter(s -> s.getAgentId().equals(agentId))
                .toList();
    }

    /**
     * Find a session by session ID.
     *
     * @param sessionId Session ID
     * @return Session info, returns null if not found
     */
    public ChatSessionInfo findSession(String sessionId) {
        return repository.loadSessions().stream()
                .filter(s -> sessionId != null && s.getId().equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Update the last active time of a session.
     *
     * @param session The session info to update
     */
    public void touchSession(ChatSessionInfo session) {
        LOGGER.log(Level.FINE, "Updated session last active time: session={0}", session.getId());
        List<ChatSessionInfo> sessions = new ArrayList<>(repository.loadSessions());
        Optional<ChatSessionInfo> old =
                sessions.stream().filter(s -> s.getId().equals(session.getId())).findFirst();
        old.ifPresent(s -> s.setUpdatedAt(LocalDateTime.now().toString()));
        repository.saveSessions(sessions);
    }

    /**
     * Build PermissionContextState based on PermissionMode.
     *
     * <p>Directly uses PermissionMode from agentscope-java, no longer defining execution_level manually:
     * <ul>
     *   <li>bypass → allow all tools directly</li>
     *   <li>default → add askRules for guarded_tools</li>
     *   <li>accept_edits → automatically handled by PermissionEngine (read-only allowed, edits within workspace allowed)</li>
     *   <li>explore → automatically handled by PermissionEngine (read-only allowed, modifications denied)</li>
     *   <li>dont_ask → add askRules for guarded_tools (ASK downgraded to DENY)</li>
     * </ul>
     *
     * @param config Agent configuration
     * @param agentId Agent ID
     * @return Built PermissionContextState
     */
    public Path sessionPath(String agentId) {
        return repository
                .paths()
                .workspaceRoot
                .resolve(agentId)
                .resolve(WorkspacePaths.SESSIONS_DIR);
    }

    public String roleOf(Msg msg) {
        if (msg == null || msg.getRole() == null) {
            return ChatMessageRoles.ASSISTANT;
        }
        if (msg.getRole() == MsgRole.USER) {
            return ChatMessageRoles.USER;
        }
        if (msg.getRole() == MsgRole.SYSTEM) {
            return ChatMessageRoles.SYSTEM;
        }
        return ChatMessageRoles.ASSISTANT;
    }

    /**
     * Convert native message blocks from AgentScope into structured parts that can be directly consumed by ChatView.
     *
     * <p>We no longer encode special blocks into body tags here, to avoid polluting the UI chunk protocol when the model naturally outputs tag text.
     */
    public List<ChatMessagePart> partsOf(Msg msg) {
        return partsOfStatic(msg);
    }

    /**
     * Static version: Convert native message blocks from AgentScope into structured parts that can be directly consumed by ChatView.
     *
     * <p>Static method for StreamingEventHandler to call.
     */
    static List<ChatMessagePart> partsOfStatic(Msg msg) {
        List<ChatMessagePart> parts = new ArrayList<>();
        if (msg == null || msg.getContent() == null) {
            return parts;
        }
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                // When reading from persistence history, strip the title generation instruction at
                // the end,
                // to prevent the "[TITLE: xxx]" instruction text from appearing in re-opened
                // sessions.
                String text = MessagePipeline.cutEmbeddedTitleInstruction(tb.getText());
                appendLoadedPart(parts, ChatMessagePart.text(text));
            } else if (block instanceof io.agentscope.core.message.ThinkingBlock tb) {
                ChatMessagePart part =
                        ChatMessagePart.block(
                                ChatMessagePart.THINKING, "THINKING", tb.getThinking());
                appendLoadedPart(parts, part);
            } else if (block instanceof io.agentscope.core.message.ToolUseBlock tub) {
                if (tub.getName() == null
                        || tub.getName().equals("__fragment__")
                        || tub.getName().isBlank()) {
                    continue;
                }
                String text =
                        tub.getContent() != null && !tub.getContent().isEmpty()
                                ? tub.getContent()
                                : String.valueOf(tub.getInput());
                ChatMessagePart part =
                        ChatMessagePart.block(
                                ChatMessagePart.TOOL_CALL,
                                toolBlockTitle("TOOL CALL", tub.getName()),
                                text);
                part.setId(tub.getId());
                part.setToolName(tub.getName());
                appendLoadedPart(parts, part);
            } else if (block instanceof io.agentscope.core.message.ToolResultBlock trb) {
                String fullText = formatBlocks(trb.getOutput());
                ChatMessagePart part;
                if (fullText.length() > TOOL_RESULT_OFFLOAD_THRESHOLD) {
                    // Oversized payload, trigger offload
                    String uuid = UUID.randomUUID().toString();
                    Path offloadPath = OFFLOAD_DIR.resolve(uuid + ".txt");
                    try {
                        Files.createDirectories(OFFLOAD_DIR);
                        Files.writeString(offloadPath, fullText, StandardCharsets.UTF_8);
                        String summary =
                                String.format(
                                        " [Oversized payload collapsed] Result file size: %d bytes",
                                        fullText.length());
                        part =
                                ChatMessagePart.block(
                                        ChatMessagePart.TOOL_RESULT,
                                        toolBlockTitle("TOOL RESULT", trb.getName()),
                                        summary);
                        part.setOffloaded(true);
                        part.setOffloadPath(offloadPath.toString());
                    } catch (IOException e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Failed to write oversized payload to offload directory",
                                e);
                        // Fall back to displaying a truncated summary directly
                        part =
                                ChatMessagePart.block(
                                        ChatMessagePart.TOOL_RESULT,
                                        toolBlockTitle("TOOL RESULT", trb.getName()),
                                        "[Oversized payload warning] Result too long to display and"
                                                + " caching failed. First 500 chars: "
                                                + fullText.substring(
                                                        0, Math.min(500, fullText.length())));
                    }
                } else {
                    part =
                            ChatMessagePart.block(
                                    ChatMessagePart.TOOL_RESULT,
                                    toolBlockTitle("TOOL RESULT", trb.getName()),
                                    fullText);
                }
                part.setId(trb.getId());
                part.setToolName(trb.getName());
                appendLoadedPart(parts, part);
            } else if (block != null) {
                appendLoadedPart(parts, ChatMessagePart.text(block.toString()));
            }
        }
        return parts;
    }

    private static void appendLoadedPart(List<ChatMessagePart> parts, ChatMessagePart part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!parts.isEmpty() && parts.getLast().sameStreamTarget(part)) {
            parts.getLast().append(part.getText());
            return;
        }
        parts.add(part);
    }

    /**
     * Merge displayed content of streaming events with Agent's final result.
     *
     * <p>AgentScope's {@link AgentResultEvent} is the final authoritative result, but some models will simultaneously output the same answer through streaming events.
     * Based on structured fragments, an inclusion relation check is performed here to avoid duplicate display, while ensuring Final Answer is not lost.
     */
    private void mergeFinalResultParts(
            List<ChatMessagePart> streamedParts,
            List<ChatMessagePart> resultParts,
            StreamCallback callback) {
        mergeFinalResultPartsStatic(streamedParts, resultParts, callback);
    }

    /**
     * Static version: Merge displayed content of streaming events with Agent's final result.
     *
     * <p>Static method for StreamingEventHandler to call.
     */
    static void mergeFinalResultPartsStatic(
            List<ChatMessagePart> streamedParts,
            List<ChatMessagePart> resultParts,
            StreamCallback callback) {
        if (resultParts == null || resultParts.isEmpty()) {
            return;
        }
        for (ChatMessagePart resultPart : resultParts) {
            if (resultPart == null || resultPart.isBlank()) {
                continue;
            }
            String delta = deltaForFinalPart(streamedParts, resultPart);
            if (delta.isBlank()) {
                continue;
            }
            boolean forceNew = !sameAsLastPart(streamedParts, resultPart);
            emitPartStatic(
                    streamedParts,
                    callback,
                    resultPart.getType(),
                    resultPart.getTitle(),
                    resultPart.getId(),
                    resultPart.getToolName(),
                    delta,
                    forceNew);
        }
    }

    private static String deltaForFinalPart(
            List<ChatMessagePart> streamedParts, ChatMessagePart resultPart) {
        String current = ChatMessageRecord.textOfParts(streamedParts);
        String result = resultPart.getText() == null ? "" : resultPart.getText();
        if (result.isBlank() || current.contains(result)) {
            return "";
        }
        if (!streamedParts.isEmpty()) {
            ChatMessagePart last = streamedParts.getLast();
            String lastText = last.getText() == null ? "" : last.getText();
            if (last.sameStreamTarget(resultPart) && result.startsWith(lastText)) {
                return result.substring(lastText.length());
            }
        }
        return current.isBlank() ? result : "\n\n" + result.stripLeading();
    }

    private static boolean sameAsLastPart(List<ChatMessagePart> parts, ChatMessagePart part) {
        return parts != null && !parts.isEmpty() && parts.getLast().sameStreamTarget(part);
    }

    /**
     * Static version: Write a stream delta to both aggregate result and UI callback.
     *
     * <p>Static method for StreamingEventHandler to call.
     */
    static void emitPartStatic(
            List<ChatMessagePart> aggregateParts,
            StreamCallback callback,
            String type,
            String title,
            String id,
            String toolName,
            String delta,
            boolean forceNew) {
        ChatMessagePart incoming = ChatMessagePart.block(type, title, delta);
        incoming.setId(id == null ? "" : id);
        incoming.setToolName(toolName == null ? "" : toolName);
        boolean startsNew = forceNew || aggregateParts.isEmpty();
        ChatMessagePart target = null;
        if (!startsNew) {
            ChatMessagePart last = aggregateParts.getLast();
            if (last.sameStreamTarget(incoming)) {
                target = last;
            } else {
                startsNew = true;
            }
        }
        if (target == null) {
            target = incoming.copy();
            aggregateParts.add(target);
        } else {
            target.append(delta);
        }
        safeOnPart(callback, incoming, startsNew);
    }

    private static String lastPartText(List<ChatMessagePart> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        String text = parts.getLast().getText();
        return text == null ? "" : text;
    }

    private static String toolBlockTitle(String prefix, String toolName) {
        String safeName = toolName == null || toolName.isBlank() ? "unknown" : toolName;
        return prefix + ": " + safeName;
    }

    /**
     * Extract {@code "path"} field value from accumulated JSON input of tool call.
     *
     * <p>Tool call parameters arrive as a stream of JSON fragments (via {@code ToolCallDeltaEvent}),
     * which are concatenated to form a JSON string like {@code {"path": "/some/file.java", "content": "..."}}.
     * Simple regex extraction is used here to avoid introducing a full JSON parser dependency.
     *
     * @param inputBuffer Accumulated JSON input, may be null
     * @return Extracted path value, returns null if not found
     */
    private String extractPathFromToolInput(StringBuilder inputBuffer) {
        if (inputBuffer == null || inputBuffer.length() == 0) {
            return null;
        }
        String json = inputBuffer.toString();
        // Try to match "path": "value" or "path":"value"
        Matcher matcher = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String formatBlocks(List<ContentBlock> blocks) {
        if (blocks == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : blocks) {
            if (b instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            } else if (b instanceof ImageBlock ib && ib.getSource() instanceof URLSource us) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[image] ").append(us.getUrl());
            } else if (b instanceof ImageBlock ib && ib.getSource() instanceof Base64Source bs) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[image] inline base64 (").append(bs.getMediaType()).append(')');
            } else if (b instanceof VideoBlock vb && vb.getSource() instanceof URLSource us) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[video] ").append(us.getUrl());
            } else if (b instanceof VideoBlock vb && vb.getSource() instanceof Base64Source bs) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[video] inline base64 (").append(bs.getMediaType()).append(')');
            } else if (b instanceof AudioBlock ab && ab.getSource() instanceof URLSource us) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[audio] ").append(us.getUrl());
            } else if (b instanceof AudioBlock ab && ab.getSource() instanceof Base64Source bs) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[audio] inline base64 (").append(bs.getMediaType()).append(')');
            } else if (b instanceof DataBlock db && db.getSource() instanceof URLSource us) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[file] ")
                        .append(
                                db.getName() != null && !db.getName().isEmpty()
                                        ? db.getName() + " "
                                        : "")
                        .append(us.getUrl());
            } else if (b instanceof DataBlock db && db.getSource() instanceof Base64Source bs) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[file] ")
                        .append(
                                db.getName() != null && !db.getName().isEmpty()
                                        ? db.getName() + " "
                                        : "")
                        .append("inline base64 (")
                        .append(bs.getMediaType())
                        .append(')');
            } else if (b != null) {
                sb.append(b.toString());
            }
        }
        return sb.toString();
    }

    List<StagedAttachment> stageAttachments(
            AgentInfo agent, ChatSessionInfo sessionInfo, List<Path> attachmentPaths) {
        if (attachmentPaths == null || attachmentPaths.isEmpty()) {
            return List.of();
        }
        Path workspace = repository.workspaceFor(agent.getId()).toAbsolutePath().normalize();
        Path targetDir =
                workspace
                        .resolve(".attachments")
                        .resolve(
                                sessionInfo == null || sessionInfo.getId() == null
                                        ? WorkspacePaths.FALLBACK_SESSION_ID
                                        : sessionInfo.getId())
                        .resolve(String.valueOf(System.currentTimeMillis()));
        List<StagedAttachment> staged = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create attachment directory: " + targetDir, e);
            return List.of();
        }
        for (Path rawPath : attachmentPaths) {
            if (rawPath == null) {
                continue;
            }
            Path source = rawPath.toAbsolutePath().normalize();
            String key = source.toString();
            if (!dedupe.add(key)) {
                continue;
            }
            try {
                if (!Files.exists(source) || !Files.isRegularFile(source)) {
                    continue;
                }
                long size = Files.size(source);
                if (size > CHAT_ATTACHMENT_MAX_BYTES) {
                    LOGGER.log(
                            Level.WARNING,
                            "Skipping oversized attachment: {0} ({1} bytes)",
                            new Object[] {source, size});
                    continue;
                }
                String originalName =
                        source.getFileName() == null ? "file" : source.getFileName().toString();
                String safeName = FileNameUtils.sanitizeEnglishPathName(originalName, "file");
                Path dest = uniqueFile(targetDir, safeName);
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                String relative = workspace.relativize(dest).toString().replace('\\', '/');
                staged.add(new StagedAttachment(dest, relative, originalName));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to process attachment: " + source, e);
            }
        }
        return staged;
    }

    boolean isImageFile(Path file) {
        String ext = fileExtension(file);
        return IMAGE_EXTS.contains(ext);
    }

    boolean isVideoFile(Path file) {
        String ext = fileExtension(file);
        return VIDEO_EXTS.contains(ext);
    }

    String inlineTextAttachment(Path file) {
        String ext = fileExtension(file);
        if (!TEXT_EXTS.contains(ext)) {
            return "";
        }
        try {
            long size = Files.size(file);
            if (size <= 0) {
                return "";
            }
            long toRead = Math.min(size, TEXT_ATTACHMENT_INLINE_MAX_BYTES);
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > toRead) {
                byte[] chunk = new byte[(int) toRead];
                System.arraycopy(bytes, 0, chunk, 0, (int) toRead);
                return new String(chunk, StandardCharsets.UTF_8).trim();
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to inline text attachment: " + file, e);
            return "";
        }
    }

    void sanitizeMemoryMessages(AgentStateStore session, String userId, String sessionId) {
        List<Msg> rawMsgs = session.getList(userId, sessionId, MEMORY_MESSAGES_KEY, Msg.class);
        if (rawMsgs == null || rawMsgs.isEmpty()) {
            return;
        }
        boolean changed = false;
        List<Msg> sanitized = new ArrayList<>(rawMsgs.size());
        for (Msg msg : rawMsgs) {
            MsgSanitizeResult result = sanitizeMessageMediaSources(msg);
            sanitized.add(result.msg);
            changed = changed || result.changed;
        }
        if (changed) {
            session.save(userId, sessionId, MEMORY_MESSAGES_KEY, sanitized);
        }
    }

    private MsgSanitizeResult sanitizeMessageMediaSources(Msg msg) {
        if (msg == null || msg.getContent() == null || msg.getContent().isEmpty()) {
            return new MsgSanitizeResult(msg, false);
        }
        ContentSanitizeResult contentResult = sanitizeContentBlocks(msg.getContent());
        if (!contentResult.changed) {
            return new MsgSanitizeResult(msg, false);
        }
        Msg rebuilt =
                Msg.builder()
                        .id(msg.getId())
                        .name(msg.getName())
                        .role(msg.getRole())
                        .content(contentResult.blocks)
                        .metadata(msg.getMetadata())
                        .timestamp(msg.getTimestamp())
                        .build();
        return new MsgSanitizeResult(rebuilt, true);
    }

    private ContentSanitizeResult sanitizeContentBlocks(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new ContentSanitizeResult(List.of(), false);
        }
        boolean changed = false;
        List<ContentBlock> out = new ArrayList<>(blocks.size());
        for (ContentBlock block : blocks) {
            if (block instanceof ImageBlock ib) {
                BlockSanitizeResult converted = sanitizeImageBlock(ib);
                out.add(converted.block);
                changed = changed || converted.changed;
            } else if (block instanceof VideoBlock vb) {
                BlockSanitizeResult converted = sanitizeVideoBlock(vb);
                out.add(converted.block);
                changed = changed || converted.changed;
            } else if (block instanceof io.agentscope.core.message.ToolResultBlock trb) {
                ContentSanitizeResult nested = sanitizeContentBlocks(trb.getOutput());
                if (nested.changed) {
                    out.add(
                            new io.agentscope.core.message.ToolResultBlock(
                                    trb.getId(), trb.getName(), nested.blocks, trb.getMetadata()));
                    changed = true;
                } else {
                    out.add(trb);
                }
            } else if (block != null) {
                out.add(block);
            }
        }
        return new ContentSanitizeResult(out, changed);
    }

    private BlockSanitizeResult sanitizeImageBlock(ImageBlock block) {
        if (block == null || block.getSource() == null) {
            return new BlockSanitizeResult(block, false);
        }
        if (block.getSource() instanceof Base64Source) {
            return new BlockSanitizeResult(block, false);
        }
        if (!(block.getSource() instanceof URLSource us)) {
            return new BlockSanitizeResult(block, false);
        }
        String url = us.getUrl();
        Path localPath = parseLocalPathFromUrl(url);
        if (localPath != null) {
            Base64Source source = toBase64Source(localPath, "image/png");
            if (source != null) {
                ImageBlock rebuilt =
                        ImageBlock.builder()
                                .source(source)
                                .minPixels(block.getMinPixels())
                                .maxPixels(block.getMaxPixels())
                                .build();
                return new BlockSanitizeResult(rebuilt, true);
            }
            return new BlockSanitizeResult(
                    TextBlock.builder()
                            .text(
                                    "Image omitted: local file is unavailable or too large to"
                                            + " inline.")
                            .build(),
                    true);
        }
        if (isValidRemoteOrDataUrl(url)) {
            return new BlockSanitizeResult(block, false);
        }
        return new BlockSanitizeResult(
                TextBlock.builder().text("Image omitted: invalid image URL in history.").build(),
                true);
    }

    private BlockSanitizeResult sanitizeVideoBlock(VideoBlock block) {
        if (block == null || block.getSource() == null) {
            return new BlockSanitizeResult(block, false);
        }
        if (block.getSource() instanceof Base64Source) {
            return new BlockSanitizeResult(block, false);
        }
        if (!(block.getSource() instanceof URLSource us)) {
            return new BlockSanitizeResult(block, false);
        }
        String url = us.getUrl();
        Path localPath = parseLocalPathFromUrl(url);
        if (localPath != null) {
            Base64Source source = toBase64Source(localPath, "video/mp4");
            if (source != null) {
                VideoBlock rebuilt =
                        VideoBlock.builder()
                                .source(source)
                                .fps(block.getFps())
                                .maxFrames(block.getMaxFrames())
                                .minPixels(block.getMinPixels())
                                .maxPixels(block.getMaxPixels())
                                .totalPixels(block.getTotalPixels())
                                .build();
                return new BlockSanitizeResult(rebuilt, true);
            }
            return new BlockSanitizeResult(
                    TextBlock.builder()
                            .text(
                                    "Video omitted: local file is unavailable or too large to"
                                            + " inline.")
                            .build(),
                    true);
        }
        if (isValidRemoteOrDataUrl(url)) {
            return new BlockSanitizeResult(block, false);
        }
        return new BlockSanitizeResult(
                TextBlock.builder().text("Video omitted: invalid video URL in history.").build(),
                true);
    }

    private boolean isValidRemoteOrDataUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("data:");
    }

    private Path parseLocalPathFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            if (url.startsWith("file:")) {
                return Path.of(URI.create(url)).toAbsolutePath().normalize();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse file URL: " + url, e);
            return null;
        }
        if (url.startsWith("/") || url.startsWith("./") || url.startsWith("../")) {
            return Paths.get(url).toAbsolutePath().normalize();
        }
        if (url.matches("^[A-Za-z]:[\\\\/].*")) {
            return Paths.get(url).toAbsolutePath().normalize();
        }
        return null;
    }

    Base64Source toBase64Source(Path file, String fallbackMediaType) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0 || bytes.length > CHAT_ATTACHMENT_MAX_BYTES) {
                return null;
            }
            String mediaType = Files.probeContentType(file);
            if (mediaType == null || mediaType.isBlank()) {
                mediaType = fallbackMediaType;
            }
            return Base64Source.builder()
                    .mediaType(mediaType)
                    .data(Base64.getEncoder().encodeToString(bytes))
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to convert attachment to Base64: " + file, e);
            return null;
        }
    }

    private Path uniqueFile(Path dir, String filename) {
        Path candidate = dir.resolve(filename);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String ext = "";
        String stem = filename;
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            stem = filename.substring(0, dot);
            ext = filename.substring(dot);
        }
        int idx = 1;
        while (true) {
            Path next = dir.resolve(stem + "_" + idx + ext);
            if (!Files.exists(next)) {
                return next;
            }
            idx++;
        }
    }

    private String fileExtension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot).toLowerCase();
    }

    static final class StagedAttachment {

        final Path path;

        final String relativePath;

        final String originalName;

        private StagedAttachment(Path path, String relativePath, String originalName) {
            this.path = path;
            this.relativePath = relativePath;
            this.originalName = originalName;
        }
    }

    private record MsgSanitizeResult(Msg msg, boolean changed) {}

    private record ContentSanitizeResult(List<ContentBlock> blocks, boolean changed) {}

    private record BlockSanitizeResult(ContentBlock block, boolean changed) {}

    /**
     * Parse and apply session title from assistant's first response.
     *
     * <p>LLM has output {@code [TITLE: xxx]} at the end of the first response, this method extracts it
     * and replaces the placeholder name.
     *
     * @param assistantResponse Assistant's full response text
     * @param session           Current session
     */
    private void applyTitleFromAssistantResponse(
            String assistantResponse, ChatSessionInfo session) {
        try {
            String title = parseTitleFromResponse(assistantResponse);
            if (title.isBlank()) {
                LOGGER.log(
                        Level.FINE,
                        "No [TITLE: xxx] marker found in response, skipping auto title generation:"
                                + " session={0}",
                        session.getId());
                return;
            }
            List<ChatSessionInfo> sessions = new ArrayList<>(repository.loadSessions());
            Optional<ChatSessionInfo> latest =
                    sessions.stream().filter(s -> s.getId().equals(session.getId())).findFirst();
            if (latest.isEmpty()) {
                LOGGER.log(
                        Level.WARNING,
                        "Apply session title: session not found (may have been deleted):"
                                + " session={0}",
                        session.getId());
                return;
            }
            ChatSessionInfo current = latest.get();
            // Only overwrite if the name is still a placeholder, avoiding overwriting manually
            // modified names
            String placeholder = session.getName();
            if (!placeholder.equals(current.getName())) {
                LOGGER.log(
                        Level.FINE,
                        "Session title manually modified, skipping auto generation: session={0}",
                        session.getId());
                return;
            }
            current.setName(title);
            current.setUpdatedAt(LocalDateTime.now().toString());
            repository.saveSessions(sessions);
            LOGGER.log(
                    Level.INFO,
                    "Session title auto-generated: session={0}, title={1}",
                    new Object[] {session.getId(), title});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply session title: " + session.getId(), e);
        }
    }

    /**
     * Parse {@code [TITLE: xxx]} marker from assistant response.
     *
     * @param responseText Assistant's full response text
     * @return Extracted title, returns empty string if not found
     */
    private String parseTitleFromResponse(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return "";
        }
        int titleIdx = responseText.lastIndexOf("[TITLE:");
        if (titleIdx < 0) {
            return "";
        }
        int endIdx = responseText.indexOf("]", titleIdx);
        if (endIdx <= titleIdx) {
            return "";
        }
        String title = responseText.substring(titleIdx + 7, endIdx).trim();
        return cleanTitle(title);
    }

    private String textFromChatResponse(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.getContent()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            }
        }
        return sb.toString().trim();
    }

    private String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        String next = title.trim();
        if (next.contains("\n")) {
            next = next.substring(0, next.indexOf('\n')).trim();
        }
        while (!next.isEmpty() && ".,;:!?".indexOf(next.charAt(next.length() - 1)) >= 0) {
            next = next.substring(0, next.length() - 1).trim();
        }
        if (next.length() > 60) {
            next = next.substring(0, 60).trim();
        }
        return next;
    }

    public void updateSession(ChatSessionInfo session) {
        LOGGER.log(
                Level.FINE,
                "Updated session metadata: session={0}, name={1}",
                new Object[] {session.getId(), session.getName()});
        List<ChatSessionInfo> sessions = new ArrayList<>(repository.loadSessions());
        boolean found = false;
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getId().equals(session.getId())) {
                session.setUpdatedAt(LocalDateTime.now().toString());
                sessions.set(i, session);
                found = true;
                break;
            }
        }
        repository.saveSessions(sessions);
        if (found) {
            LOGGER.log(
                    Level.INFO,
                    "Session metadata updated: session={0}, name={1}",
                    new Object[] {session.getId(), session.getName()});
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "Session metadata update: matching session not found (may have been deleted):"
                            + " session={0}",
                    session.getId());
        }
    }

    public void deleteSession(String sessionId) {
        batchDeleteSessions(List.of(sessionId));
    }

    /**
     * Batch delete session metadata.
     *
     * @param sessionIds List of primary key IDs of sessions to delete ({@link ChatSessionInfo#id})
     */
    public void batchDeleteSessions(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        List<ChatSessionInfo> sessions = new ArrayList<>(repository.loadSessions());
        int before = sessions.size();
        sessions.removeIf(s -> sessionIds.contains(s.getId()));
        repository.saveSessions(sessions);
        LOGGER.log(
                Level.INFO,
                "Batch delete sessions: requested={0}, actually removed={1}",
                new Object[] {sessionIds.size(), before - sessions.size()});
    }

    static void safeOnPart(StreamCallback callback, ChatMessagePart part, boolean startsNew) {
        if (callback == null) {
            return;
        }
        try {
            callback.onPart(part == null ? null : part.copy(), startsNew);
        } catch (Exception callbackErr) {
            LOGGER.log(Level.WARNING, "onPart callback exception (ignored)", callbackErr);
        }
    }

    public void drainAndReplyAgentChat(String agentId, String responseText) {
        messagePipeline.drainAndReplyAgentChat(agentId, responseText);
    }

    public synchronized void registerPendingAgentChatReply(
            String agentId, String replyTo, String correlationId) {
        messagePipeline.registerPendingAgentChatReply(agentId, replyTo, correlationId);
    }

    public void sendMessage(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            ChatSessionInfo sessionInfo,
            String prompt,
            List<java.nio.file.Path> attachmentPaths,
            StreamCallback callback) {
        messagePipeline.sendMessage(
                agent,
                provider,
                modelId,
                sessionInfo,
                prompt,
                attachmentPaths,
                java.util.Map.of(),
                callback);
    }
}
