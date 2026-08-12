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

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AgentStateStore implemented with the Decorator pattern, solving the concurrency issue of
 * underlying storage being overwritten by external asynchronous threads (like UI approval, background tasks)
 * during the runtime of long-lifecycle Agents.
 * Upon saving, it reads the latest state on disk and merges it with the current state based on Msg ID.
 *
 * Meets architecture design requirements:
 * - Fundamentally solves the problem of state overwriting across different threads.
 * - Uses record instead of traditional POJO.
 * - Uses java.util.logging.Logger for logging.
 */
public record MergingAgentStateStore(
        AgentStateStore delegate,
        java.util.concurrent.atomic.AtomicReference<io.agentscope.harness.agent.HarnessAgent>
                agentRef)
        implements AgentStateStore {

    public MergingAgentStateStore(AgentStateStore delegate) {
        this(delegate, new java.util.concurrent.atomic.AtomicReference<>());
    }

    private static final Logger LOGGER = Logger.getLogger(MergingAgentStateStore.class.getName());
    private static final ThreadLocal<Boolean> CLEARING = ThreadLocal.withInitial(() -> false);

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        if (CLEARING.get()) {
            delegate.save(userId, sessionId, key, value);
            return;
        }
        if (value instanceof AgentState newState) {
            String lockStr = (sessionId != null ? sessionId : "defaultSession").intern();
            synchronized (lockStr) {
                LOGGER.log(
                        Level.INFO,
                        "Preparing to save AgentState, userId: {0}, sessionId: {1}, key: {2}",
                        new Object[] {userId, sessionId, key});
                Optional<AgentState> existingOpt =
                        delegate.get(userId, sessionId, key, AgentState.class);
                // If existing AgentState is found on disk with existingMsgs, merge is required
                if (existingOpt.isPresent()
                        && existingOpt.get().getContext() != null
                        && !existingOpt.get().getContext().isEmpty()) {
                    AgentState existingState = existingOpt.get();
                    List<Msg> existingMsgs = existingState.getContext();
                    List<Msg> newMsgs =
                            newState.getContext() != null
                                    ? newState.getContext()
                                    : new ArrayList<>();
                    // Map is required to remove duplicates by id, and LinkedHashMap is used to
                    // maintain order
                    Map<String, Msg> mergedMap = new HashMap<>();
                    Function<Msg, String> keyGen =
                            m -> {
                                if (m.getId() != null && !m.getId().trim().isEmpty()) {
                                    return m.getId();
                                }
                                // Fallback to use characteristics to prevent duplication of
                                // messages without ID
                                return m.getRole()
                                        + "_"
                                        + m.getTimestamp()
                                        + "_"
                                        + (m.getTextContent() != null
                                                ? m.getTextContent().hashCode()
                                                : 0);
                            };
                    // Retain existing messages on disk (including SYSTEM messages asynchronously
                    // appended externally)
                    for (Msg msg : existingMsgs) {
                        if (msg != null) {
                            mergedMap.put(keyGen.apply(msg), msg);
                        }
                    }
                    // Append new messages in memory (new outputs during Agent runtime), identical
                    // keys will overwrite to retain the latest state of Agent runtime
                    for (Msg msg : newMsgs) {
                        if (msg != null) {
                            mergedMap.put(keyGen.apply(msg), msg);
                        }
                    }
                    List<Msg> mergedList = new ArrayList<>(mergedMap.values());
                    // ── Effective Timestamp Sorting ──
                    // For each TOOL role message, use the timestamp of the ASSISTANT message where
                    // its corresponding TOOL CALL is located
                    // as the sorting key (instead of its own timestamp), ensuring the TOOL result
                    // strictly follows its TOOL CALL.
                    // This simultaneously meets the strict tool_call <-> tool_result alternation
                    // requirement of the LLM API,
                    // and correctly places externally asynchronous appended SYSTEM approval
                    // messages in the timeline.
                    Map<String, String> toolCallTimestamps = new HashMap<>();
                    for (Msg m : mergedList) {
                        for (io.agentscope.core.message.ToolUseBlock tc :
                                m.getContentBlocks(io.agentscope.core.message.ToolUseBlock.class)) {
                            String id = tc.getId();
                            if (id != null && !id.isBlank()) {
                                toolCallTimestamps.putIfAbsent(
                                        id, normalizeTimestamp(m.getTimestamp()));
                            }
                        }
                    }
                    mergedList.sort(
                            (m1, m2) -> {
                                String t1 = effectiveTimestamp(m1, toolCallTimestamps);
                                String t2 = effectiveTimestamp(m2, toolCallTimestamps);
                                if (t1 == null && t2 == null) return 0;
                                if (t1 == null) return -1;
                                if (t2 == null) return 1;
                                return t1.compareTo(t2);
                            });

                    io.agentscope.harness.agent.HarnessAgent agent = agentRef.get();
                    if (agent != null) {
                        try {
                            CLEARING.set(true);
                            agent.clearContext(userId, sessionId);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Failed to call clearContext", e);
                        } finally {
                            CLEARING.set(false);
                        }
                    } else {
                        newState.contextMutable().clear();
                    }
                    newState.contextMutable().addAll(mergedList);
                    LOGGER.log(
                            Level.INFO,
                            "Successfully merged and sorted AgentState by effective timestamp. Disk"
                                + " messages: {0}, Memory messages: {1}, Total after merge: {2}",
                            new Object[] {existingMsgs.size(), newMsgs.size(), mergedList.size()});
                } else {
                    LOGGER.log(
                            Level.INFO, "No existing AgentState found on disk, will save directly");
                }
                delegate.save(userId, sessionId, key, newState);
            }
        } else {
            LOGGER.log(Level.INFO, "Saving non-AgentState object, key: {0}", key);
            delegate.save(userId, sessionId, key, value);
        }
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        LOGGER.log(
                Level.INFO,
                "Saving State list, key: {0}, size: {1}",
                new Object[] {key, values != null ? values.size() : 0});
        delegate.save(userId, sessionId, key, values);
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String key, Class<T> type) {
        LOGGER.log(
                Level.FINE,
                "Reading single state, key: {0}, type: {1}",
                new Object[] {key, type.getSimpleName()});
        return delegate.get(userId, sessionId, key, type);
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String key, Class<T> itemType) {
        LOGGER.log(
                Level.FINE,
                "Reading state list, key: {0}, type: {1}",
                new Object[] {key, itemType.getSimpleName()});
        return delegate.getList(userId, sessionId, key, itemType);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        LOGGER.log(Level.FINE, "Checking if session exists, sessionId: {0}", sessionId);
        return delegate.exists(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        LOGGER.log(Level.INFO, "Deleting session data, sessionId: {0}", sessionId);
        delegate.delete(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        LOGGER.log(
                Level.INFO,
                "Deleting specific session data, sessionId: {0}, key: {1}",
                new Object[] {sessionId, key});
        delegate.delete(userId, sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        LOGGER.log(Level.FINE, "Listing session ID list, userId: {0}", userId);
        return delegate.listSessionIds(userId);
    }

    @Override
    public void close() {
        LOGGER.log(Level.INFO, "Closing AgentStateStore proxy");
        delegate.close();
    }

    // ──────────────────────────────────────────────────
    // Effective Timestamp Calculation (replaces the old fixToolCallResultContinuity)
    // ──────────────────────────────────────────────────
    /**
     * Calculates the "effective timestamp" of a message for sorting.
     *
     * <p>For TOOL role messages (tool execution results), it does not directly use its own timestamp,
     * but looks up the timestamp recorded in {@code toolCallTimestamps} for its corresponding TOOL CALL ID
     * (i.e., the timestamp of the ASSISTANT message where the TOOL CALL is located).
     * This ensures the TOOL result has the same timestamp as the TOOL CALL that triggered it during sorting,
     * thus guaranteeing the sorted TOOL result immediately follows the corresponding TOOL CALL.
     *
     * <p>For non-TOOL messages, it directly returns its own timestamp.
     *
     * @param m                  The message to be sorted
     * @param toolCallTimestamps Mapping of tool_call_id -> timestamp of the owning ASSISTANT message
     * @return Effective timestamp (normalized format, might be null)
     */
    private static String effectiveTimestamp(Msg m, Map<String, String> toolCallTimestamps) {
        if (m.getRole() == io.agentscope.core.message.MsgRole.TOOL) {
            for (ToolResultBlock block : m.getContentBlocks(ToolResultBlock.class)) {
                String callId = block.getId();
                if (callId != null && !callId.isBlank()) {
                    String ts = toolCallTimestamps.get(callId);
                    if (ts != null) {
                        return ts;
                    }
                }
            }
        }
        return normalizeTimestamp(m.getTimestamp());
    }

    /**
     * Normalize timestamp format: replace 'T' with a space, solving the
     * dictionary sorting inconsistency issue when mixing {@code LocalDateTime.now()} (with 'T')
     * and AgentScope default timestamp (with space).
     */
    private static String normalizeTimestamp(String ts) {
        return ts != null ? ts.replace('T', ' ') : null;
    }
}
