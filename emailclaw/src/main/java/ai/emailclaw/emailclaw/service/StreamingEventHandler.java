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
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.util.JsonUtils;
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

    /** Cumulative input (prompt) tokens across model calls in this turn. */
    private long totalInputTokens = 0;

    /** Cumulative output (completion) tokens across model calls in this turn. */
    private long totalOutputTokens = 0;

    /** Cumulative cached tokens served from prompt cache across model calls in this turn. */
    private long totalCachedTokens = 0;

    /** Whether at least one ModelCallEndEvent was observed. */
    private boolean hasModelUsage = false;

    /**
     * Constructs streaming event handler.
     *
     * @param approvalTracker  HITL approval tracker
     * @param callback         UI callback interface
     * @param agentId          Agent ID
     * @param sessionId        Session ID
     * @param providerId       Provider ID
     * @param modelId          Model ID
     */
    StreamingEventHandler(
            PendingApprovalTracker approvalTracker,
            ai.emailclaw.emailclaw.service.StreamCallback callback,
            String agentId,
            String sessionId,
            String providerId,
            String modelId) {
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
        if (event instanceof ModelCallEndEvent mce) {
            handleModelCallEnd(mce);
        } else if (event instanceof TextBlockDeltaEvent tb) {
            handleTextBlockDelta(tb);
        } else if (event instanceof ThinkingBlockStartEvent tbs) {
            handleThinkingBlockStart(tbs);
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
        } else if (event instanceof ToolResultDataDeltaEvent trdata) {
            handleToolResultDataDelta(trdata);
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
        } else if (event instanceof io.agentscope.core.event.UserConfirmResultEvent confirmResult) {
            handleUserConfirmResult(confirmResult);
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

    /**
     * Returns true if at least one ModelCallEndEvent was observed with valid token metrics.
     *
     * @return true if model usage was recorded
     */
    boolean hasModelUsage() {
        return hasModelUsage;
    }

    /**
     * Returns cumulative prompt/input tokens used in this turn.
     *
     * @return input tokens
     */
    long getTotalInputTokens() {
        return totalInputTokens;
    }

    /**
     * Returns cumulative completion/output tokens generated in this turn.
     *
     * @return output tokens
     */
    long getTotalOutputTokens() {
        return totalOutputTokens;
    }

    /**
     * Returns cumulative prompt cache hit tokens in this turn.
     *
     * @return cached tokens
     */
    long getTotalCachedTokens() {
        return totalCachedTokens;
    }

    // ── Event Processing Methods ──────────────────────────────────────

    /**
     * Handles text delta events.
     */
    private void handleTextBlockDelta(TextBlockDeltaEvent tb) {
        String subagentName = extractSubagentName(tb);
        emitPart(
                ChatMessagePart.TEXT,
                textBlockTitle(subagentName),
                textBlockId(subagentName),
                "",
                tb.getDelta(),
                false);
    }

    /**
     * Handles thinking block start events.
     */
    private void handleThinkingBlockStart(ThinkingBlockStartEvent tbs) {
        String subagentName = extractSubagentName(tbs);
        emitPart(
                ChatMessagePart.THINKING,
                thinkingBlockTitle(subagentName),
                subagentName == null ? "" : subagentName,
                "",
                "",
                true);
    }

    /**
     * Handles thinking block delta events.
     */
    private void handleThinkingBlockDelta(ThinkingBlockDeltaEvent tbd) {
        String subagentName = extractSubagentName(tbd);
        emitPart(
                ChatMessagePart.THINKING,
                thinkingBlockTitle(subagentName),
                subagentName == null ? "" : subagentName,
                "",
                tbd.getDelta(),
                false);
    }

    /**
     * Handles tool call start events.
     */
    private void handleToolCallStart(ToolCallStartEvent tc) {
        String subagentName = extractSubagentName(tc);
        String tcName = tc.getToolCallName();
        LOGGER.log(
                Level.INFO,
                "Tool call started: agent={0}, subagent={1}, session={2}, tool={3}",
                new Object[] {
                    agentId,
                    subagentName == null ? "main" : subagentName,
                    sessionId,
                    tcName == null ? "unknown" : tcName
                });

        // Cache the actual tool name (not __fragment__ placeholder), for later
        // parsing by ToolCallDeltaEvent
        if (tcName != null && !tcName.startsWith("__")) {
            toolCallNameCache.put(tc.getToolCallId(), tcName);
        }
        emitPart(
                ChatMessagePart.TOOL_CALL,
                toolBlockTitle("TOOL CALL", tcName, subagentName),
                tc.getToolCallId(),
                tcName,
                "",
                true);
    }

    /**
     * Handles tool call delta events.
     */
    private void handleToolCallDelta(ToolCallDeltaEvent tcd) {
        String subagentName = extractSubagentName(tcd);
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
                toolBlockTitle("TOOL CALL", tcdName, subagentName),
                tcd.getToolCallId(),
                tcdName,
                tcd.getDelta(),
                false);
    }

    /**
     * Handles tool result start events.
     */
    private void handleToolResultStart(ToolResultStartEvent tr) {
        String subagentName = extractSubagentName(tr);
        emitPart(
                ChatMessagePart.TOOL_RESULT,
                toolBlockTitle("TOOL RESULT", tr.getToolCallName(), subagentName),
                tr.getToolCallId(),
                tr.getToolCallName(),
                "",
                true);
    }

    /**
     * Handles tool result text delta events.
     */
    private void handleToolResultTextDelta(ToolResultTextDeltaEvent trd) {
        String subagentName = extractSubagentName(trd);
        emitPart(
                ChatMessagePart.TOOL_RESULT,
                toolBlockTitle("TOOL RESULT", trd.getToolCallName(), subagentName),
                trd.getToolCallId(),
                trd.getToolCallName(),
                trd.getDelta(),
                false);
    }

    /**
     * Handles tool result end events.
     *
     * <p>Extracts file diff markup directly from AgentScope 2.0.3 tool result event metadata,
     * achieving complete protocol decoupling from sidecar trackers.
     */
    private void handleToolResultEnd(ToolResultEndEvent tre) {
        String subagentName = extractSubagentName(tre);
        Map<String, Object> metadata = tre.getMetadata();
        if (metadata != null
                && metadata.containsKey(ToolResultDiffMiddleware.METADATA_KEY_FILE_DIFF)) {
            String diffMarkup =
                    (String) metadata.get(ToolResultDiffMiddleware.METADATA_KEY_FILE_DIFF);
            if (diffMarkup != null && !diffMarkup.isBlank()) {
                emitPart(
                        ChatMessagePart.TOOL_RESULT,
                        toolBlockTitle("TOOL RESULT", tre.getToolCallName(), subagentName),
                        tre.getToolCallId(),
                        tre.getToolCallName(),
                        "\n" + diffMarkup,
                        false);
            }
        }
    }

    /**
     * Handles fine-grained tool result data delta events (e.g. multimodal images or structured blocks).
     */
    private void handleToolResultDataDelta(ToolResultDataDeltaEvent trdata) {
        if (trdata == null || trdata.getData() == null) {
            return;
        }
        String subagentName = extractSubagentName(trdata);
        ContentBlock block = trdata.getData();
        if (block instanceof TextBlock tb) {
            emitPart(
                    ChatMessagePart.TOOL_RESULT,
                    toolBlockTitle("TOOL RESULT", trdata.getToolCallName(), subagentName),
                    trdata.getToolCallId(),
                    trdata.getToolCallName(),
                    tb.getText(),
                    false);
        } else if (block instanceof ImageBlock ib) {
            String dataUrl = "";
            if (ib.getSource() instanceof URLSource urlSource) {
                dataUrl = urlSource.getUrl();
            } else if (ib.getSource() instanceof Base64Source base64Source) {
                dataUrl =
                        "data:" + base64Source.getMediaType() + ";base64," + base64Source.getData();
            }
            emitPart(
                    ChatMessagePart.IMAGE,
                    toolBlockTitle("TOOL IMAGE", trdata.getToolCallName(), subagentName),
                    trdata.getToolCallId(),
                    trdata.getToolCallName(),
                    dataUrl != null ? dataUrl : "",
                    true);
        } else {
            String json = JsonUtils.getJsonCodec().toJson(block);
            emitPart(
                    ChatMessagePart.TOOL_RESULT,
                    toolBlockTitle("TOOL RESULT", trdata.getToolCallName(), subagentName),
                    trdata.getToolCallId(),
                    trdata.getToolCallName(),
                    json,
                    false);
        }
    }

    /**
     * Handles model call end events, accumulating token usage and prompt caching metrics.
     */
    private void handleModelCallEnd(ModelCallEndEvent mce) {
        if (mce != null && mce.getUsage() != null) {
            ChatUsage usage = mce.getUsage();
            totalInputTokens += usage.getInputTokens();
            totalOutputTokens += usage.getOutputTokens();
            totalCachedTokens += usage.getCachedTokens();
            hasModelUsage = true;
            LOGGER.log(
                    Level.FINE,
                    "Model call ended for session {0}: input={1}, output={2}, cached={3}",
                    new Object[] {
                        sessionId,
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        usage.getCachedTokens()
                    });
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
        String subagentName = extractSubagentName(ar);
        if (subagentName != null) {
            LOGGER.log(
                    Level.INFO,
                    "Subagent result event ignored for parent session: subagent={0}, session={1}",
                    new Object[] {subagentName, sessionId});
            return;
        }
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
     * Handles UserConfirmResultEvent.
     */
    private void handleUserConfirmResult(io.agentscope.core.event.UserConfirmResultEvent event) {
        LOGGER.log(
                Level.INFO,
                "UserConfirmResultEvent received: destroying pending approvals, session={0}",
                sessionId);
        approvalTracker.onUserConfirmResult(event, sessionId);
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
     * Extracts subagent name/identifier from the event source or metadata.
     *
     * <p>In AgentScope 2.0.3, child agent events are tagged with {@code event.getSource()},
     * which contains a slash-separated path such as {@code parentSession/childAgentId} or
     * {@code main/subagentName}. Top-level parent events return {@code null}.
     *
     * @param event Stream event object
     * @return Subagent name if event originates from a subagent, or {@code null} for top-level agent
     */
    private String extractSubagentName(Object event) {
        if (!(event instanceof AgentEvent ae)) {
            return null;
        }
        String source = ae.getSource();
        if (source == null || source.isBlank()) {
            return null;
        }
        int lastSlash = source.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < source.length() - 1) {
            return source.substring(lastSlash + 1).trim();
        }
        return source.trim();
    }

    /**
     * Generates tool block title with optional subagent prefix.
     *
     * @param prefix       Title prefix (e.g., "TOOL CALL")
     * @param toolName     Tool name
     * @param subagentName Subagent name, if originating from a subagent
     * @return Formatted title
     */
    private String toolBlockTitle(String prefix, String toolName, String subagentName) {
        String safeName = toolName == null || toolName.isBlank() ? "unknown" : toolName;
        String base = prefix + ": " + safeName;
        if (subagentName != null && !subagentName.isBlank()) {
            return "[Subagent: " + subagentName + "] " + base;
        }
        return base;
    }

    /**
     * Generates tool block title.
     *
     * @param prefix   Title prefix (e.g., "TOOL CALL")
     * @param toolName Tool name
     * @return Formatted title
     */
    private String toolBlockTitle(String prefix, String toolName) {
        return toolBlockTitle(prefix, toolName, null);
    }

    /**
     * Generates thinking block title with optional subagent prefix.
     *
     * @param subagentName Subagent name, if originating from a subagent
     * @return Formatted title
     */
    private String thinkingBlockTitle(String subagentName) {
        if (subagentName != null && !subagentName.isBlank()) {
            return "[Subagent: " + subagentName + "] THINKING";
        }
        return "THINKING";
    }

    /**
     * Generates text block title with optional subagent prefix.
     *
     * @param subagentName Subagent name, if originating from a subagent
     * @return Formatted title
     */
    private String textBlockTitle(String subagentName) {
        if (subagentName != null && !subagentName.isBlank()) {
            return "[Subagent: " + subagentName + "]";
        }
        return "";
    }

    /**
     * Generates text block stream target identifier with optional subagent identifier.
     *
     * @param subagentName Subagent name, if originating from a subagent
     * @return Stream target ID
     */
    private String textBlockId(String subagentName) {
        if (subagentName != null && !subagentName.isBlank()) {
            return subagentName;
        }
        return "";
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
