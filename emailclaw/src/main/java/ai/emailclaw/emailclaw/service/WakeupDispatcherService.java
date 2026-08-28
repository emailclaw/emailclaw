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

import io.agentscope.harness.agent.bus.BusEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * Wakeup dispatcher service.
 *
 * <p>Listens for wakeup signals from the message bus, waking up idle agents to process results when background tasks complete.
 *
 * <p>Main functions:
 * <ul>
 *   <li>Listen to wakeup signal channel</li>
 *   <li>Drain wakeup queue and dispatch wakeup requests</li>
 *   <li>Trigger agent reasoning loop to process new messages</li>
 * </ul>
 */
public class WakeupDispatcherService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(WakeupDispatcherService.class.getName());

    /** Maximum drain count, preventing processing too many wakeup requests at once. */
    private static final int MAX_DRAIN_COUNT = 64;

    /** Message bus service, provides wakeup signal subscription and queue draining capabilities. */
    private final MessageBusService messageBusService;

    /** Wakeup target interface, used to check if session is running and trigger wakeup. */
    private final WakeupTarget wakeupTarget;

    /** Wakeup signal subscription, used for unsubscribing. */
    private volatile Disposable subscription;

    /** Whether the service has started. */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Wakeup target interface.
     *
     * <p>Defines two gateway operations needed by the wakeup dispatcher.
     */
    public interface WakeupTarget {
        /**
         * Check if the specified session is running.
         *
         * @param projectId Project ID
         * @param sessionId Session ID
         * @return true if session is running
         */
        boolean isSessionRunning(String projectId, String sessionId);

        /**
         * Trigger wakeup run for the specified session/agent.
         *
         * @param projectId Project ID
         * @param sessionId Session ID (may be null, in which case agentId identifies wakeup source)
         * @param agentId   Agent ID to wake up
         * @return run result
         */
        Mono<Object> runWakeup(String projectId, String sessionId, String agentId);
    }

    private final ProjectService projectService;

    /**
     * Create wakeup dispatcher service.
     *
     * @param messageBusService message bus service
     * @param wakeupTarget      wakeup target interface
     * @param projectService    project service
     */
    public WakeupDispatcherService(
            MessageBusService messageBusService,
            WakeupTarget wakeupTarget,
            ProjectService projectService) {
        this.messageBusService = messageBusService;
        this.wakeupTarget = wakeupTarget;
        this.projectService = projectService;
        LOGGER.log(Level.INFO, "Wakeup dispatcher service created");
    }

    /**
     * Start wakeup dispatcher.
     *
     * <p>Perform initial drain to handle signals generated during startup, then subscribe to real-time signal channel.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            LOGGER.log(Level.WARNING, "Wakeup dispatcher already started");
            return;
        }

        LOGGER.log(Level.INFO, "Starting wakeup dispatcher");

        // Initial drain: handle signals generated during startup
        drainAndDispatch();

        // Subscribe to real-time wakeup signal channel for all projects
        // (For simplicity, we'll poll all projects periodically instead of using Flux merge,
        //  since projects can be created dynamically)
        subscription =
                reactor.core.publisher.Flux.interval(java.time.Duration.ofSeconds(1))
                        .subscribe(
                                tick -> drainAndDispatch(),
                                err ->
                                        LOGGER.log(
                                                Level.SEVERE,
                                                "Wakeup dispatcher subscription error, dispatcher"
                                                        + " stopped",
                                                err));

        LOGGER.log(Level.INFO, "Wakeup dispatcher startup complete");
    }

    @Override
    public void close() {
        Disposable d = subscription;
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
        subscription = null;
        started.set(false);
        LOGGER.log(Level.INFO, "Wakeup dispatcher stopped");
    }

    /**
     * Drain wakeup queue and dispatch wakeup requests for all projects.
     */
    private void drainAndDispatch() {
        for (ai.emailclaw.emailclaw.model.ProjectInfo project : projectService.list()) {
            try {
                List<BusEntry> entries =
                        messageBusService
                                .getMessageBus(project.getId())
                                .inboxDrain("agentscope:wakeups", MAX_DRAIN_COUNT)
                                .block();
                if (entries == null || entries.isEmpty()) {
                    continue;
                }

                LOGGER.log(
                        Level.FINE,
                        "Drained wakeup queue for project {0}: {1} entries",
                        new Object[] {project.getId(), entries.size()});

                for (BusEntry entry : entries) {
                    dispatch(project.getId(), entry.payload());
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Wakeup drain error for project " + project.getId(), e);
            }
        }
    }

    /**
     * Dispatch single wakeup request.
     *
     * @param projectId project ID
     * @param payload wakeup request payload
     */
    private void dispatch(String projectId, Map<String, Object> payload) {
        String sessionId = getString(payload, "sessionId");
        String agentId = getString(payload, "agentId");

        if ((sessionId == null || sessionId.isBlank()) && (agentId == null || agentId.isBlank())) {
            LOGGER.log(Level.FINE, "Skipped wakeup entry with no sessionId and agentId");
            return;
        }

        // agent-chat wakeup may only have agentId and no sessionId; when sessionId is blank, use
        // agentId as identifier
        String effectiveSessionId =
                (sessionId != null && !sessionId.isBlank()) ? sessionId : agentId;
        String effectiveAgentId =
                (agentId != null && !agentId.isBlank()) ? agentId : effectiveSessionId;

        // Check if session is running
        if (wakeupTarget.isSessionRunning(projectId, effectiveSessionId)) {
            LOGGER.log(Level.FINE, "Session {0} is running, skipping wakeup", effectiveSessionId);
            return;
        }

        LOGGER.log(
                Level.INFO,
                "Waking up idle session: session={0}, agent={1}",
                new Object[] {effectiveSessionId, effectiveAgentId});

        // Trigger wakeup run
        wakeupTarget
                .runWakeup(projectId, effectiveSessionId, effectiveAgentId)
                .subscribe(
                        msg ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Wakeup run complete: session={0}",
                                        effectiveSessionId),
                        err ->
                                LOGGER.log(
                                        Level.WARNING,
                                        "Wakeup run failed: session=" + effectiveSessionId,
                                        err));
    }

    /**
     * Get string value from map.
     *
     * @param map map
     * @param key key
     * @return string value, returns null if it does not exist or is not a string
     */
    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }
}
