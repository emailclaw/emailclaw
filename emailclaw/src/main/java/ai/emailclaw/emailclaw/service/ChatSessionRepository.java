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

import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chat session metadata repository.
 *
 * <p>Now delegates directly to ConfigManager for storage and reading.
 */
public class ChatSessionRepository {

    private static final Logger LOGGER = Logger.getLogger(ChatSessionRepository.class.getName());

    private static final String AGENT_STATE_KEY = "agent_state";

    private static final String SESSION_USER_ID = null;

    private final ProjectService projectService;

    public ChatSessionRepository(ProjectService projectService) {
        this.projectService = projectService;
    }

    public Path sessionPath(String projectId, String agentId) {
        String aId = (agentId == null || agentId.isBlank()) ? "default" : agentId;
        ai.emailclaw.emailclaw.model.ProjectInfo project = projectService.findById(projectId);
        String baseDir = project != null ? project.getBaseDirectory() : null;
        Path base =
                (baseDir != null && !baseDir.isBlank())
                        ? Path.of(FileNameUtils.expandUserHome(baseDir))
                        : AppHomeConstants.HOME_RESOLVED
                                .resolve(AppHomeConstants.PROJECTS_DIR)
                                .resolve(projectId != null ? projectId : "default");
        return base.resolve(AppHomeConstants.AGENT_WORKSPACE_DIR)
                .resolve(aId)
                .resolve(AppHomeConstants.SESSIONS_DIR);
    }

    public List<Msg> loadHistory(String projectId, String agentId, String sessionId) {
        AgentStateStore session =
                new MergingAgentStateStore(
                        new JsonFileAgentStateStore(sessionPath(projectId, agentId)));
        AgentState state = loadAgentState(session, sessionId);
        List<Msg> msgs = state != null ? state.getContext() : null;
        return msgs != null ? new ArrayList<>(msgs) : new ArrayList<>();
    }

    public AgentState loadAgentState(AgentStateStore session, String sessionId) {
        if (session == null || sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Optional<AgentState> current =
                session.get(SESSION_USER_ID, sessionId, AGENT_STATE_KEY, AgentState.class);
        if (current.isPresent()) {
            return current.get();
        }
        return null;
    }

    public void appendHistoryMsg(String projectId, String agentId, String sessionId, Msg msg) {
        if (msg == null) {
            return;
        }
        try {
            /**
             * Batch save session metadata.
             */
            AgentStateStore session =
                    new MergingAgentStateStore(
                            new JsonFileAgentStateStore(sessionPath(projectId, agentId)));
            /**
             * Load all session metadata (sorted descending by updatedAt).
             */
            AgentState state =
                    Optional.ofNullable(loadAgentState(session, sessionId))
                            .orElseGet(() -> AgentState.builder().sessionId(sessionId).build());
            List<Msg> context = state.contextMutable();
            if (msg.getRole() == MsgRole.TOOL
                    && !msg.getContentBlocks(ToolResultBlock.class).isEmpty()) {
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

    private static int findToolCallInsertionIndex(List<Msg> context, Msg toolResultMsg) {
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
}
