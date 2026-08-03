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

import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.service.memory.MemoAutoSync;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Streaming chat post-processor.
 *
 * <p>Responsible for executing various aspect logics after streaming chat completes, including:
 * <ul>
 *   <li>Automatic session title generation</li>
 *   <li>MEMORY.md automatic synchronization</li>
 *   <li>Internal Agent communication reply</li>
 * </ul>
 *
 * <p>This component is extracted from ChatService, separating these cross-cutting concerns from the core chat logic,
 * following the single responsibility principle, making each logic independently testable and maintainable.
 *
 * <p>Using the design philosophy of the event listener/middleware pattern:
 * Each post-processing step is independent and can be executed on demand after the streaming chat completes,
 * and the failure of any step will not affect other steps.
 */
final class PostStreamHandler {

    /** Logger. */
    private static final Logger LOGGER = Logger.getLogger(PostStreamHandler.class.getName());

    /** Emailclaw does not pass RuntimeContext.userId, so AgentScope persists the session in the anonymous namespace. */
    private static final String SESSION_USER_ID = null;

    /** Agent state storage key. */
    private static final String AGENT_STATE_KEY = "agent_state";

    /** Session storage service. */
    private final AgentStateStore sessionStore;

    /** Session info. */
    private final ChatSessionInfo sessionInfo;

    /** Agent ID. */
    private final String agentId;

    /** MEMORY.md automatic synchronization service. */
    private final MemoAutoSync memoAutoSync;

    /** ChatService instance (for calling drainAndReplyAgentChat). */
    private final ChatService chatService;

    /** Session title generator. */
    private final SessionTitleGenerator titleGenerator;

    /**
     * Construct a streaming chat post-processor.
     *
     * @param sessionStore   Session storage service
     * @param sessionInfo    Session info
     * @param agentId        Agent ID
     * @param memoAutoSync   MEMORY.md automatic synchronization service
     * @param chatService    ChatService instance
     * @param titleGenerator Session title generator
     */
    PostStreamHandler(
            AgentStateStore sessionStore,
            ChatSessionInfo sessionInfo,
            String agentId,
            MemoAutoSync memoAutoSync,
            ChatService chatService,
            SessionTitleGenerator titleGenerator) {
        this.sessionStore = sessionStore;
        this.sessionInfo = sessionInfo;
        this.agentId = agentId;
        this.memoAutoSync = memoAutoSync;
        this.chatService = chatService;
        this.titleGenerator = titleGenerator;
        LOGGER.log(
                Level.FINE,
                "PostStreamHandler initialization completed: agent={0}, session={1}",
                new Object[] {agentId, sessionInfo != null ? sessionInfo.getId() : "null"});
    }

    /**
     * Execute all post-processing logic.
     *
     * <p>Called after streaming chat completes, executing post-processing tasks in order.
     * Each task has independent exception handling, ensuring a single task failure does not affect others.
     *
     * @param completedText  Assistant's completed reply text
     * @param firstUserMessage Whether it is the first user message
     * @param autoGenerateTitle Whether to automatically generate session title
     */
    void executePostProcessing(
            String completedText, boolean firstUserMessage, boolean autoGenerateTitle) {
        LOGGER.log(
                Level.INFO,
                "Started executing streaming chat post-processing: session={0}",
                sessionInfo != null ? sessionInfo.getId() : "null");

        // 1. Automatic session title generation
        if (firstUserMessage && autoGenerateTitle) {
            applyTitleFromResponse(completedText);
        }

        // 2. MEMORY.md automatic synchronization
        syncMemorySummary();

        // 3. Internal Agent communication reply
        drainAndReplyAgentChat(completedText);

        LOGGER.log(
                Level.INFO,
                "Streaming chat post-processing completed: session={0}",
                sessionInfo != null ? sessionInfo.getId() : "null");
    }

    /**
     * Parse and apply session title from assistant response.
     */
    private void applyTitleFromResponse(String completedText) {
        try {
            titleGenerator.applyTitleFromAssistantResponse(completedText, sessionInfo);
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "Automatic session title generation failed (non-critical)", e);
        }
    }

    /**
     * Memory synchronization: write the post-compaction summary to MEMORY.md.
     */
    private void syncMemorySummary() {
        if (sessionInfo == null || sessionInfo.getId() == null) {
            return;
        }
        try {
            sessionStore
                    .get(SESSION_USER_ID, sessionInfo.getId(), AGENT_STATE_KEY, AgentState.class)
                    .ifPresent(state -> memoAutoSync.syncSummary(agentId, state.getSummary()));
            LOGGER.log(
                    Level.FINE,
                    "MEMORY.md synchronization completed: session={0}",
                    sessionInfo.getId());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "MEMORY.md synchronization skipped (non-critical)", e);
        }
    }

    /**
     * Internal agent communication reply: check if there are agent_chat requests in the inbox,
     * if so, push this inference result to the replyTo queue.
     */
    private void drainAndReplyAgentChat(String completedText) {
        if (completedText == null || completedText.isBlank()) {
            return;
        }
        try {
            chatService.drainAndReplyAgentChat(agentId, completedText);
            LOGGER.log(
                    Level.FINE, "Internal agent communication reply completed: agent={0}", agentId);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Internal agent communication reply failed (non-critical)", e);
        }
    }
}
