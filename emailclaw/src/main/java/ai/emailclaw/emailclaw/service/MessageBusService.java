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

import io.agentscope.harness.agent.bus.AsyncToolRecord;
import io.agentscope.harness.agent.bus.AsyncToolRegistry;
import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import io.agentscope.harness.agent.bus.WorkspaceAsyncToolRegistry;
import io.agentscope.harness.agent.bus.WorkspaceMessageBus;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Flux;

/**
 * Message bus service.
 *
 * <p>Encapsulates agentscope's {@link MessageBus} and {@link AsyncToolRegistry},
 * providing a unified asynchronous message communication infrastructure for Emailclaw.
 *
 * <p>Main functions:
 * <ul>
 *   <li>Inter-agent message routing: supports agents sending/receiving messages via inbox</li>
 *   <li>Asynchronous tool execution tracking: tracks the state of tools offloaded to background execution</li>
 *   <li>Wake-up scheduling: wakes up idle agents when background tasks complete</li>
 * </ul>
 */
public class MessageBusService {
    private static final Logger LOGGER = Logger.getLogger(MessageBusService.class.getName());

    /** Message bus instance, provides queue, log, and broadcast consumption modes. */
    private final MessageBus messageBus;

    /** Asynchronous tool registry, tracks the state of tools offloaded to background execution. */
    private final AsyncToolRegistry asyncToolRegistry;

    /**
     * Creates message bus service.
     *
     * @param workspacePath Workspace path, used to persist message state
     */
    public MessageBusService(Path workspacePath) {
        LOGGER.log(Level.INFO, "Initializing message bus service, workspace: {0}", workspacePath);
        LocalFilesystem filesystem = new LocalFilesystem(workspacePath);
        this.messageBus = new WorkspaceMessageBus(filesystem, "message-bus");
        this.asyncToolRegistry = new WorkspaceAsyncToolRegistry(filesystem, "async-tools");
        LOGGER.log(Level.INFO, "Message bus service initialization completed");
    }

    /**
     * Gets message bus instance.
     *
     * @return Message bus instance
     */
    public MessageBus getMessageBus() {
        return messageBus;
    }

    /**
     * Gets asynchronous tool registry instance.
     *
     * @return Asynchronous tool registry instance
     */
    public AsyncToolRegistry getAsyncToolRegistry() {
        return asyncToolRegistry;
    }

    /**
     * Pushes message to the specified session's inbox.
     *
     * <p>Messages are persisted, they will not be lost even if the target agent is currently offline.
     *
     * @param sessionId Target session ID
     * @param payload   Message content (JSON serializable)
     */
    public void inboxPush(String sessionId, Map<String, Object> payload) {
        LOGGER.log(Level.FINE, "Pushing message to inbox: session={0}", sessionId);
        messageBus
                .inboxPush(sessionId, payload)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Message pushed successfully: session={0}",
                                        sessionId))
                .doOnError(e -> LOGGER.log(Level.WARNING, "Message push failed: session={0}", e))
                .subscribe();
    }

    /**
     * Drains messages from the specified session's inbox.
     *
     * <p>The drain operation is atomic, returned messages will be removed from the queue.
     *
     * @param sessionId Target session ID
     * @param maxCount  Maximum drain count
     * @return List of drained messages
     */
    public List<BusEntry> inboxDrain(String sessionId, int maxCount) {
        LOGGER.log(
                Level.FINE,
                "Draining inbox: session={0}, maxCount={1}",
                new Object[] {sessionId, maxCount});
        try {
            return messageBus.inboxDrain(sessionId, maxCount).block();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to drain inbox: session={0}", e);
            return List.of();
        }
    }

    /**
     * Checks if the specified session's inbox has pending messages.
     *
     * @param sessionId Target session ID
     * @return true if there are messages
     */
    public boolean inboxHasMessages(String sessionId) {
        try {
            return messageBus.inboxHasMessages(sessionId).block();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check inbox", e);
            return false;
        }
    }

    /**
     * Enqueues wake-up request and notifies scheduler.
     *
     * <p>When a background task completes, call this method to wake up the target agent to process results.
     *
     * @param userId    User ID
     * @param sessionId Target session ID
     * @param agentId   Target agent ID
     */
    public void enqueueWakeup(String userId, String sessionId, String agentId) {
        LOGGER.log(
                Level.INFO,
                "Enqueuing wake-up request: user={0}, session={1}, agent={2}",
                new Object[] {userId, sessionId, agentId});
        messageBus
                .enqueueWakeup(userId, sessionId, agentId)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Wake-up request enqueued successfully: session={0}",
                                        sessionId))
                .doOnError(e -> LOGGER.log(Level.WARNING, "Wake-up request enqueuing failed", e))
                .subscribe();
    }

    /**
     * Subscribes to wake-up signal channel.
     *
     * @return Wake-up signal flux
     */
    public Flux<Map<String, Object>> subscribeWakeup() {
        return messageBus.subscribeWakeup();
    }

    /**
     * Registers asynchronous tool execution record.
     *
     * <p>When tool execution exceeds the configured timeout, AsyncToolMiddleware calls this method to register asynchronous execution.
     *
     * @param record Asynchronous tool execution record
     */
    public void registerAsyncTool(AsyncToolRecord record) {
        LOGGER.log(
                Level.FINE,
                "Registering asynchronous tool: id={0}, tool={1}",
                new Object[] {record.id(), record.toolName()});
        asyncToolRegistry
                .register(record)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Asynchronous tool registered successfully: id={0}",
                                        record.id()))
                .doOnError(
                        e -> LOGGER.log(Level.WARNING, "Asynchronous tool registration failed", e))
                .subscribe();
    }

    /**
     * Marks asynchronous tool execution as complete.
     *
     * @param id     Asynchronous tool record ID
     * @param result Execution result
     */
    public void completeAsyncTool(String id, String result) {
        LOGGER.log(Level.FINE, "Completing asynchronous tool: id={0}", id);
        asyncToolRegistry
                .complete(id, result)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Asynchronous tool marked as complete successfully: id={0}",
                                        id))
                .doOnError(
                        e ->
                                LOGGER.log(
                                        Level.WARNING,
                                        "Failed to mark asynchronous tool as complete",
                                        e))
                .subscribe();
    }

    /**
     * Marks asynchronous tool execution as failed.
     *
     * @param id    Asynchronous tool record ID
     * @param error Error message
     */
    public void failAsyncTool(String id, String error) {
        LOGGER.log(Level.FINE, "Marking asynchronous tool as failed: id={0}", id);
        asyncToolRegistry
                .fail(id, error)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Asynchronous tool marked as failed successfully: id={0}",
                                        id))
                .doOnError(
                        e ->
                                LOGGER.log(
                                        Level.WARNING,
                                        "Failed to mark asynchronous tool as failed",
                                        e))
                .subscribe();
    }

    /**
     * Finds stale asynchronous tool records in the specified session.
     *
     * <p>Stale records are usually orphan records caused by process crashes.
     *
     * @param sessionId Session ID
     * @param ttl       Time-to-live threshold
     * @return List of stale asynchronous tool records
     */
    public List<AsyncToolRecord> findStaleAsyncTools(String sessionId, Duration ttl) {
        LOGGER.log(
                Level.FINE,
                "Finding stale asynchronous tools: session={0}, ttl={1}",
                new Object[] {sessionId, ttl});
        try {
            return asyncToolRegistry.findStale(sessionId, ttl).block();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to find stale asynchronous tools", e);
            return List.of();
        }
    }

    /**
     * Marks asynchronous tool record as timed out.
     *
     * @param id Asynchronous tool record ID
     */
    public void markAsyncToolTimeout(String id) {
        LOGGER.log(Level.FINE, "Marking asynchronous tool as timed out: id={0}", id);
        asyncToolRegistry
                .markTimeout(id)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Asynchronous tool marked as timed out successfully:"
                                                + " id={0}",
                                        id))
                .doOnError(
                        e ->
                                LOGGER.log(
                                        Level.WARNING,
                                        "Failed to mark asynchronous tool as timed out",
                                        e))
                .subscribe();
    }

    /**
     * Publishes session event.
     *
     * <p>Events are appended to the replay log and broadcasted in real-time to subscribed listeners.
     *
     * @param sessionId Session ID
     * @param event     Event content
     * @return Replay log entry ID
     */
    public String sessionPublishEvent(String sessionId, Map<String, Object> event) {
        LOGGER.log(Level.FINE, "Publishing session event: session={0}", sessionId);
        try {
            return messageBus.sessionPublishEvent(sessionId, event).block();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to publish session event", e);
            return null;
        }
    }

    /**
     * Subscribes to session events.
     *
     * @param sessionId Session ID
     * @return Event flux
     */
    public Flux<Map<String, Object>> sessionSubscribeEvents(String sessionId) {
        return messageBus.sessionSubscribeEvents(sessionId);
    }

    /**
     * Reads session events for catching up/reconnection.
     *
     * @param sessionId Session ID
     * @param since     Cursor, returns entries strictly after this ID
     * @param maxCount  Maximum number of entries to return
     * @return List of event entries
     */
    public List<BusEntry> sessionReadEvents(String sessionId, String since, int maxCount) {
        LOGGER.log(
                Level.FINE,
                "Reading session events: session={0}, since={1}, maxCount={2}",
                new Object[] {sessionId, since, maxCount});
        try {
            return messageBus.sessionReadEvents(sessionId, since, maxCount).block();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read session events", e);
            return List.of();
        }
    }

    /**
     * Trims session event replay log.
     *
     * <p>Call after session execution completes to prevent late subscribers from replaying stale events from the previous run.
     *
     * @param sessionId Session ID
     */
    public void sessionTrimEvents(String sessionId) {
        LOGGER.log(Level.FINE, "Trimming session event log: session={0}", sessionId);
        messageBus
                .sessionTrimEvents(sessionId)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Session event log trimmed successfully: session={0}",
                                        sessionId))
                .doOnError(e -> LOGGER.log(Level.WARNING, "Failed to trim session event log", e))
                .subscribe();
    }

    /**
     * Closes message bus service, releasing underlying transport resources.
     */
    public void close() {
        LOGGER.log(Level.INFO, "Closing message bus service");
        messageBus.close();
    }
}
