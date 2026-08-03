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

import ai.emailclaw.emailclaw.model.ChatMessagePart;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Streaming event handler.
 *
 * <p>Responsible for handling AgentScope streaming events (Reactor Flux), dispatching various event types
 * to the corresponding processing logic, and pushing the results to the UI layer via StreamCallback.
 *
 * <p>This component is extracted from ChatService to separate protocol orchestration logic from business logic,
 * making the event handling process clearer, easier to test and maintain.
 *
 * <p>Supported event types:
 * <ul>
 *   <li>TextBlockDeltaEvent - Text delta</li>
 *   <li>ThinkingBlockStartEvent/DeltaEvent/EndEvent - Thinking process</li>
 *   <li>ToolCallStartEvent/DeltaEvent - Tool call</li>
 *   <li>ToolResultStartEvent/TextDeltaEvent/EndEvent - Tool result</li>
 *   <li>HintBlockEvent - Hint block</li>
 *   <li>AgentResultEvent - Final result</li>
 *   <li>RequireUserConfirmEvent - HITL approval</li>
 *   <li>AllToolsDeniedEvent - All tools denied</li>
 *   <li>RequestStopEvent - Agent pause</li>
 * </ul>
 */
final class StreamingEventHandler {

    /** Logger. */
    private static final Logger LOGGER = Logger.getLogger(StreamingEventHandler.class.getName());

    /** File diff tracker. */
    private final FileDiffTracker diffTracker;

    /** HITL approval tracker. */
    private final PendingApprovalTracker approvalTracker;

    /** List aggregating all stream parts. */
    private final List<ChatMessagePart> finalParts;

    /** UI callback interface. */
    private final ai.emailclaw.emailclaw.service.StreamCallback callback;

    /** Cache mapping tool call ID to actual tool name. */
    private final Map<String, String> toolCallNameCache;

    /** Whether U+FFFD (replacement char) warning has been emitted. */
    private final boolean[] replacementWarned;

    /** Agent ID (for logging). */
    private final String agentId;

    /** Session ID (for logging). */
    private final String sessionId;

    /** Provider ID (for logging). */
    private final String providerId;

    /** Model ID (for logging). */
    private final String modelId;

    /**
     * Constructs streaming event handler.
     *
     * @param diffTracker      File diff tracker
     * @param approvalTracker  HITL approval tracker
     * @param callback         UI callback interface
     * @param agentId          Agent ID
     * @param sessionId        Session ID
     * @param providerId       Provider ID
     * @param modelId          Model ID
     */
    StreamingEventHandler(
            FileDiffTracker diffTracker,
            PendingApprovalTracker approvalTracker,
            ai.emailclaw.emailclaw.service.StreamCallback callback,
            String agentId,
            String sessionId,
            String providerId,
            String modelId) {
        this.diffTracker = diffTracker;
        this.approvalTracker = approvalTracker;
        this.callback = callback;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.providerId = providerId;
        this.modelId = modelId;
        this.finalParts = new ArrayList<>();
        this.toolCallNameCache = new HashMap<>();
        this.replacementWarned = new boolean[] {false};
        LOGGER.log(
                Level.FINE,
                "StreamingEventHandler initialization completed: agent={0}, session={1}",
                new Object[] {agentId, sessionId});
    }

    /**
     * Handles streaming events.
     *
     * <p>Dispatches to corresponding processing logic based on event type.
     *
     * @param event Stream event object
     */
    void handleEvent(Object event) {
        if (event instanceof TextBlockDeltaEvent tb) {
            handleTextBlockDelta(tb);
        } else if (event instanceof ThinkingBlockStartEvent) {
            handleThinkingBlockStart();
        } else if (event instanceof ThinkingBlockDeltaEvent tbd) {
            handleThinkingBlockDelta(tbd);
        } else if (event instanceof ThinkingBlockEndEvent) {
            // Structured parts don't need an explicit end tag; the next start event will naturally
            // start a new part.
            LOGGER.log(Level.FINE, "ThinkingBlockEndEvent: ignored");
        } else if (event instanceof ToolCallStartEvent tc) {
            handleToolCallStart(tc);
        } else if (event instanceof ToolCallDeltaEvent tcd) {
            handleToolCallDelta(tcd);
        } else if (event instanceof ToolResultStartEvent tr) {
            handleToolResultStart(tr);
        } else if (event instanceof ToolResultTextDeltaEvent trd) {
            handleToolResultTextDelta(trd);
        } else if (event instanceof ToolResultEndEvent tre) {
            handleToolResultEnd(tre);
        } else if (event instanceof HintBlockEvent hb) {
            handleHintBlock(hb);
        } else if (event instanceof AgentResultEvent ar) {
            handleAgentResult(ar);
        } else if (event instanceof RequireUserConfirmEvent confirm) {
            handleRequireUserConfirm(confirm);
        } else if (event instanceof AllToolsDeniedEvent denied) {
            handleAllToolsDenied(denied);
        } else if (event instanceof RequestStopEvent stop) {
            handleRequestStop(stop);
        }

        // Check if U+FFFD (replacement char) is included
        checkReplacementChar();
    }

    /**
     * Gets all aggregated stream parts.
     *
     * @return List of stream parts
     */
    List<ChatMessagePart> getFinalParts() {
        return finalParts;
    }

    // ── Event Processing Methods ──────────────────────────────────────

    /**
     * Handles text delta events.
     */
    private void handleTextBlockDelta(TextBlockDeltaEvent tb) {
        emitPart(ChatMessagePart.TEXT, "", "", "", tb.getDelta(), false);
    }

    /**
     * Handles thinking block start events.
     */
    private void handleThinkingBlockStart() {
        emitPart(ChatMessagePart.THINKING, "THINKING", "", "", "", true);
    }

    /**
     * Handles thinking block delta events.
     */
    private void handleThinkingBlockDelta(ThinkingBlockDeltaEvent tbd) {
        emitPart(ChatMessagePart.THINKING, "THINKING", "", "", tbd.getDelta(), false);
    }

    /**
     * Handles tool call start events.
     */
    private void handleToolCallStart(ToolCallStartEvent tc) {
        String tcName = tc.getToolCallName();
        LOGGER.log(
                Level.INFO,
                "Tool call started: agent={0}, session={1}, tool={2}",
                new Object[] {agentId, sessionId, tcName == null ? "unknown" : tcName});

        // Cache the actual tool name (not __fragment__ placeholder), for later
        // parsing by ToolCallDeltaEvent
        if (tcName != null && !tcName.startsWith("__")) {
            toolCallNameCache.put(tc.getToolCallId(), tcName);
        }
        emitPart(
                ChatMessagePart.TOOL_CALL,
                toolBlockTitle("TOOL CALL", tcName),
                tc.getToolCallId(),
                tcName,
                "",
                true);
    }

    /**
     * Handles tool call delta events.
     */
    private void handleToolCallDelta(ToolCallDeltaEvent tcd) {
        // Parse tool name: AgentScope's stream parser sets the tool name to
        // __fragment__ placeholder in subsequent delta blocks. Look up actual name from cache here.
        String rawTcdName = tcd.getToolCallName();
        String tcdName = rawTcdName;
        if (rawTcdName == null || rawTcdName.startsWith("__")) {
            String cached = toolCallNameCache.get(tcd.getToolCallId());
            if (cached != null) {
                tcdName = cached;
            }
        }
        emitPart(
                ChatMessagePart.TOOL_CALL,
                toolBlockTitle("TOOL CALL", tcdName),
                tcd.getToolCallId(),
                tcdName,
                tcd.getDelta(),
                false);

        // Diff: Accumulate JSON inputs for edit_file/write_file tools
        if (diffTracker.isDiffTrackedTool(tcdName)) {
            diffTracker.accumulateInput(tcd.getToolCallId(), tcd.getDelta());
        }
    }

    /**
     * Handles tool result start events.
     */
    private void handleToolResultStart(ToolResultStartEvent tr) {
        emitPart(
                ChatMessagePart.TOOL_RESULT,
                toolBlockTitle("TOOL RESULT", tr.getToolCallName()),
                tr.getToolCallId(),
                tr.getToolCallName(),
                "",
                true);

        // Diff: Capture old file content before tool execution
        diffTracker.snapshotOldContent(tr.getToolCallId(), tr.getToolCallName());
    }

    /**
     * Handles tool result text delta events.
     */
    private void handleToolResultTextDelta(ToolResultTextDeltaEvent trd) {
        emitPart(
                ChatMessagePart.TOOL_RESULT,
                toolBlockTitle("TOOL RESULT", trd.getToolCallName()),
                trd.getToolCallId(),
                trd.getToolCallName(),
                trd.getDelta(),
                false);
    }

    /**
     * Handles tool result end events.
     */
    private void handleToolResultEnd(ToolResultEndEvent tre) {
        // Diff: Compute and send file differences after tool execution completes
        String diffMarkup =
                diffTracker.computeAndCleanup(tre.getToolCallId(), tre.getToolCallName());
        if (diffMarkup != null) {
            emitPart(
                    ChatMessagePart.TOOL_RESULT,
                    toolBlockTitle("TOOL RESULT", tre.getToolCallName()),
                    tre.getToolCallId(),
                    tre.getToolCallName(),
                    diffMarkup,
                    false);
        }
    }

    /**
     * Handles hint block events.
     */
    private void handleHintBlock(HintBlockEvent hb) {
        emitPart(
                ChatMessagePart.HINT,
                hb.getHintSource() == null ? "HINT" : "HINT FROM " + hb.getHintSource(),
                hb.getBlockId(),
                "",
                hb.getHint(),
                true);
    }

    /**
     * Handles final Agent result events.
     *
     * <p>AgentScope sends the final Msg before the stream ends; some models or middlewares only give
     * the actual user-facing Final Answer here, so it must be merged into ChatView as structured parts.
     */
    private void handleAgentResult(AgentResultEvent ar) {
        List<ChatMessagePart> resultParts = ChatService.partsOfStatic(ar.getResult());
        ChatService.mergeFinalResultPartsStatic(finalParts, resultParts, callback);
    }

    /**
     * Handles RequireUserConfirmEvent.
     *
     * <p>PermissionEngine evaluates ASK → Agent pauses to wait for approval
     */
    private void handleRequireUserConfirm(RequireUserConfirmEvent confirm) {
        approvalTracker.onRequireUserConfirm(
                confirm.getToolCalls(),
                agentId,
                sessionId,
                null, // channel is unknown at this layer, passed in by outer layer
                null, // userId is unknown at this layer
                Map.of());
    }

    /**
     * Handles AllToolsDeniedEvent.
     */
    private void handleAllToolsDenied(AllToolsDeniedEvent denied) {
        LOGGER.log(
                Level.INFO,
                "AllToolsDeniedEvent: {0} tools denied, session={1}",
                new Object[] {denied.getDeniedToolCalls().size(), sessionId});
        emitPart(
                ChatMessagePart.HINT,
                "SYSTEM",
                "",
                "",
                "You have denied executing high-risk operations, the current task has been"
                        + " terminated.",
                true);
    }

    /**
     * Handles RequestStopEvent.
     */
    private void handleRequestStop(RequestStopEvent stop) {
        if (stop.getGenerateReason() == GenerateReason.PERMISSION_ASKING) {
            LOGGER.log(
                    Level.INFO,
                    "Agent paused for HITL (PERMISSION_ASKING), reason={0}, session={1}",
                    new Object[] {stop.getReason(), sessionId});
        }
    }

    // ── Helper Methods ───────────────────────────────────────────────

    /**
     * Writes a stream delta to both the aggregate result and the UI callback simultaneously.
     *
     * @param type      Part type
     * @param title     Part title
     * @param id        Part ID
     * @param toolName  Tool name
     * @param delta     Delta content
     * @param forceNew  Whether to force a new UI block
     */
    private void emitPart(
            String type, String title, String id, String toolName, String delta, boolean forceNew) {
        ChatService.emitPartStatic(
                finalParts, callback, type, title, id, toolName, delta, forceNew);
    }

    /**
     * Generates tool block title.
     *
     * @param prefix   Title prefix (e.g., "TOOL CALL")
     * @param toolName Tool name
     * @return Formatted title
     */
    private String toolBlockTitle(String prefix, String toolName) {
        String safeName = toolName == null || toolName.isBlank() ? "unknown" : toolName;
        return prefix + ": " + safeName;
    }

    /**
     * Checks whether U+FFFD (replacement char) is included.
     */
    private void checkReplacementChar() {
        if (replacementWarned[0]) {
            return;
        }
        String emittedText = lastPartText();
        if (emittedText.indexOf('\uFFFD') >= 0) {
            replacementWarned[0] = true;
            LOGGER.log(
                    Level.WARNING,
                    "Detected model output containing U+FFFD (replacement char): provider={0},"
                            + " model={1}, session={2}",
                    new Object[] {providerId, modelId, sessionId});
        }
    }

    /**
     * Gets the text of the last part.
     *
     * @return The text of the last part, or empty string if no parts exist
     */
    private String lastPartText() {
        if (finalParts == null || finalParts.isEmpty()) {
            return "";
        }
        String text = finalParts.getLast().getText();
        return text == null ? "" : text;
    }
}
