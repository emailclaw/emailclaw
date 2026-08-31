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
package ai.emailclaw.emailclaw.service.state;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link AgentStateStore} wrapper with in-memory caching and Event Sourcing context reordering.
 *
 * <p><b>Core guarantees:</b>
 * <ul>
 *   <li>For the same {@code (userId, sessionId)}, no matter how many times {@link #get} is called,
 *       it returns <strong>the same {@link AgentState} object reference</strong>.</li>
 *   <li><b>Event Sourcing / Event Insertion:</b> Completely deprecate the append mode (Append-Only trailing).
 *       On every {@link #save} persistence, perform tree flattening reordering on the context message list,
 *       ensuring each {@code tool_result} strictly follows its corresponding {@code tool_calls} message.</li>
 * </ul>
 *
 * <p><b>Architecture description:</b>
 * AgentScope's ReActAgent, after tool execution completes, uses
 * {@code state.contextMutable().add(resultMsg)} to append the tool_result message to the
 * <strong>end</strong> of the context list. When an assistant message contains multiple tool_calls, LLM APIs require
 * each tool_result to strictly follow its corresponding assistant message, otherwise an error will occur.
 *
 * <p>This wrapper solves this issue during the {@link #save} phase through a tree/graph traversal flattening algorithm:
 * <ol>
 *   <li>Build message tree: use assistant message as parent node, its tool_calls as child nodes,
 *       tool_result is matched and mounted as child node of the corresponding tool_call via {@code tool_call_id}</li>
 *   <li>Topological flattening: output message list in tree depth-first order, ensuring each tool_result
 *       strictly follows its corresponding assistant message</li>
 *   <li>Replace {@link AgentState#contextMutable()} content so subsequent reads automatically get the correct order</li>
 * </ol>
 *
 * <p><b>Memory leak prevention (LRU):</b> Uses bounded LRU cache to prevent infinite memory growth.
 *
 * @author Emailclaw Team
 */
public class CachingAgentStateStore implements AgentStateStore {

    private static final Logger LOGGER = Logger.getLogger(CachingAgentStateStore.class.getName());

    /**
     * Maximum cached session count to prevent OOM.
     */
    private static final int MAX_CACHE_SIZE = 1000;

    private final AgentStateStore delegate;

    /**
     * Thread-safe LRU cache implementation.
     */
    private final Map<String, AgentState> cache =
            Collections.synchronizedMap(
                    new LinkedHashMap<String, AgentState>(16, 0.75f, true) {

                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, AgentState> eldest) {
                            boolean remove = size() > MAX_CACHE_SIZE;
                            if (remove) {
                                LOGGER.log(
                                        Level.INFO,
                                        "Cache full ({0}), triggering LRU eviction of oldest state:"
                                                + " {1}",
                                        new Object[] {MAX_CACHE_SIZE, eldest.getKey()});
                            }
                            return remove;
                        }
                    });

    public CachingAgentStateStore(AgentStateStore delegate) {
        this.delegate = delegate;
        LOGGER.info(
                "CachingAgentStateStore initialization complete, max cache capacity: "
                        + MAX_CACHE_SIZE);
    }

    // ==================== Event Sourcing: Context reordering core ====================
    @Override
    public void save(String userId, String sessionId, String key, State value) {
        LOGGER.log(Level.FINE, "Save state to disk: {0}", slotKey(userId, sessionId));
        // Event Sourcing: Perform tree flattening reordering on AgentState's context before
        // persistence
        if (value instanceof AgentState agentState) {
            reorderContextBeforePersist(agentState);
            cache.put(slotKey(userId, sessionId), agentState);
        }
        delegate.save(userId, sessionId, key, value);
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        delegate.save(userId, sessionId, key, values);
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String key, Class<T> type) {
        if (AgentState.class.isAssignableFrom(type)) {
            String slot = slotKey(userId, sessionId);
            @SuppressWarnings("unchecked")
            AgentState cached = cache.get(slot);
            if (cached != null) {
                LOGGER.log(Level.FINE, "Cache hit: {0}", slot);
                @SuppressWarnings("unchecked")
                T result = (T) cached;
                return Optional.of(result);
            }
            LOGGER.log(Level.INFO, "Cache miss, load from disk and put into cache: {0}", slot);
            Optional<T> fromDisk = delegate.get(userId, sessionId, key, type);
            if (fromDisk.isPresent()) {
                @SuppressWarnings("unchecked")
                AgentState loaded = (AgentState) fromDisk.get();
                cache.put(slot, loaded);
            }
            return fromDisk;
        }
        return delegate.get(userId, sessionId, key, type);
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String key, Class<T> itemType) {
        return delegate.getList(userId, sessionId, key, itemType);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return delegate.exists(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        LOGGER.log(Level.INFO, "Delete state from cache and disk: {0}", slotKey(userId, sessionId));
        delegate.delete(userId, sessionId);
        cache.remove(slotKey(userId, sessionId));
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        delegate.delete(userId, sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return delegate.listSessionIds(userId);
    }

    /**
     * Get AgentState directly from cache (without triggering disk load).
     */
    public AgentState getCached(String userId, String sessionId) {
        return cache.get(slotKey(userId, sessionId));
    }

    /**
     * Put AgentState into cache (used for scenarios where external code registers loaded state into cache).
     */
    public void putCached(String userId, String sessionId, AgentState state) {
        if (state != null) {
            String slot = slotKey(userId, sessionId);
            LOGGER.log(Level.FINE, "Manually put into cache: {0}", slot);
            cache.put(slot, state);
        }
    }

    /**
     * Return the underlying store (only for special scenarios needing direct disk access).
     */
    public AgentStateStore getDelegate() {
        return delegate;
    }

    private static String slotKey(String userId, String sessionId) {
        String uid = (userId == null || userId.isBlank()) ? "__anon__" : userId;
        return uid + "/" + sessionId;
    }

    // ==================== Event Sourcing: Tree flattening algorithm ====================
    /**
     * Perform tree flattening reordering on AgentState's context before persistence.
     *
     * <p>Completely deprecate Append-Only mode: by building a message tree and topologically flattening it, tool_result
     * messages are moved from the end of the context to immediately following their corresponding tool_calls message.
     *
     * <p>Since {@link AgentState#contextMutable()} returns a direct reference to the internal ArrayList,
     * this method replaces the content in-place via {@code clear() + addAll()}, so all components holding the same reference
     * (e.g., ReActAgent's CallExecution) automatically see the reordered result.
     *
     * @param agentState AgentState to reorder
     */
    private void reorderContextBeforePersist(AgentState agentState) {
        List<Msg> context = agentState.contextMutable();
        if (context == null || context.size() <= 1) {
            return;
        }
        // Detect if reordering is needed: only process when TOOL role messages exist in context
        boolean hasToolResults =
                context.stream()
                        .anyMatch(
                                m ->
                                        m.getRole() == MsgRole.TOOL
                                                && !m.getContentBlocks(ToolResultBlock.class)
                                                        .isEmpty());
        if (!hasToolResults) {
            return;
        }
        LOGGER.log(Level.FINE, "Event Sourcing reorder: message count={0}", context.size());
        // Step 1: Build message tree (replyToId relationship: tool_result is child of tool_calls)
        MessageNode root = buildMessageTree(context);
        // Step 2: Topological flatten (depth-first traversal)
        List<Msg> flattened = flattenTree(root, context);
        // Step 3: In-place replace context content (utilizing ArrayList reference identity)
        context.clear();
        context.addAll(flattened);
        LOGGER.log(
                Level.FINE,
                "Event Sourcing reorder complete: flattened message count={0}",
                flattened.size());
    }

    /**
     * Message tree node, representing a message and its child node relationships.
     *
     * <p>Tree structure corresponds to replyToId relationships between messages:
     * <ul>
     *   <li>assistant message → contains tool_use block → corresponding tool_result message as child node</li>
     *   <li>other messages (user/system/tool) → no child nodes</li>
     * </ul>
     */
    private static class MessageNode {

        /**
         * Original message
         */
        final Msg msg;

        /**
         * Child node list (tool_result messages, ordered by tool_call_id match)
         */
        final List<MessageNode> children = new ArrayList<>();

        MessageNode(Msg msg) {
            this.msg = msg;
        }
    }

    /**
     * Build message tree from a flat message list.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Index all tool_result messages, grouped by tool_call_id</li>
     *   <li>Iterate through the message list, create node for each message</li>
     *   <li>When encountering assistant message, extract its tool_use block ID,
     *       mount corresponding tool_result node as child node</li>
     *   <li>Unmatched tool_result nodes act as direct child nodes of root (fallback)</li>
     * </ol>
     *
     * @param context Original message list
     * @return Tree root node
     */
    private static MessageNode buildMessageTree(List<Msg> context) {
        MessageNode root = new MessageNode(null);
        // Index tool_result messages: tool_call_id → message list
        Map<String, List<Msg>> toolResultIndex = new HashMap<>();
        for (Msg msg : context) {
            if (msg.getRole() != MsgRole.TOOL) {
                continue;
            }
            for (ToolResultBlock result : msg.getContentBlocks(ToolResultBlock.class)) {
                String callId = result.getId();
                if (callId != null && !callId.isBlank()) {
                    toolResultIndex.computeIfAbsent(callId, k -> new ArrayList<>()).add(msg);
                }
            }
        }
        // Consumed tool_result message ID set (used for fallback processing and deduplication)
        Set<String> consumedResultIds = new HashSet<>();
        // Build tree: iterate context, assistant message as backbone node, tool_result only mounted
        // as child node
        for (Msg msg : context) {
            // TOOL role messages do not join backbone directly—only mounted as child node via
            // assistant tool_use match.
            // This avoids same tool_result appearing twice after flattening (once in root, once in
            // assistant child).
            if (msg.getRole() == MsgRole.TOOL) {
                continue;
            }
            MessageNode node = new MessageNode(msg);
            root.children.add(node);
            // Only extract tool_use block in assistant message, mount corresponding tool_result
            // child node
            if (msg.getRole() != MsgRole.ASSISTANT) {
                continue;
            }
            List<ToolUseBlock> toolCalls = msg.getContentBlocks(ToolUseBlock.class);
            if (toolCalls.isEmpty()) {
                continue;
            }
            for (ToolUseBlock toolCall : toolCalls) {
                String callId = toolCall.getId();
                if (callId == null || callId.isBlank()) {
                    continue;
                }
                List<Msg> matchingResults = toolResultIndex.get(callId);
                if (matchingResults != null) {
                    for (Msg resultMsg : matchingResults) {
                        // Deduplicate: same tool_result message only mounted once (may contain
                        // multiple ToolResultBlock
                        // matching different tool_call_id, but the message itself should only
                        // appear once)
                        if (consumedResultIds.add(resultMsg.getId())) {
                            node.children.add(new MessageNode(resultMsg));
                        }
                    }
                }
            }
        }
        // Fallback: unmatched tool_result appended to end (e.g., tool_call_id cannot be matched)
        for (Msg msg : context) {
            if (msg.getRole() != MsgRole.TOOL) {
                continue;
            }
            if (!consumedResultIds.contains(msg.getId())
                    && !msg.getContentBlocks(ToolResultBlock.class).isEmpty()) {
                root.children.add(new MessageNode(msg));
            }
        }
        return root;
    }

    /**
     * Topologically flatten message tree to linear message list.
     *
     * <p>Traversal rules (depth-first):
     * <ol>
     *   <li>Output node's own message</li>
     *   <li>Recursively output all child nodes (tool_result)</li>
     * </ol>
     *
     * <p>Flattening effect example:
     * <pre>
     * Original order: [assistant(tc1,tc2), user, assistant(tc3), tool_result(tc1), tool_result(tc3), tool_result(tc2)]
     * Tree structure: root → [assistant(tc1,tc2) → [tr1, tr2], user, assistant(tc3) → [tr3]]
     * After flat:     [assistant(tc1,tc2), tr1, tr2, user, assistant(tc3), tr3]
     * </pre>
     *
     * @param root    Tree root node
     * @param context Original message list (used for order reference of fallback messages)
     * @return Flattened message list
     */
    private static List<Msg> flattenTree(MessageNode root, List<Msg> context) {
        List<Msg> result = new ArrayList<>(context.size());
        for (MessageNode child : root.children) {
            flattenNode(child, result);
        }
        return result;
    }

    /**
     * Recursively flatten a single node and its subtree.
     *
     * @param node   Node to flatten
     * @param result Accumulated output list
     */
    private static void flattenNode(MessageNode node, List<Msg> result) {
        if (node.msg != null) {
            result.add(node.msg);
        }
        for (MessageNode child : node.children) {
            flattenNode(child, result);
        }
    }
}
