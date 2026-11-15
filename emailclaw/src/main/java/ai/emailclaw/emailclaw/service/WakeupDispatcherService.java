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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /** Dedicated virtual thread executor for asynchronous wakeup executions. */
    private final ExecutorService wakeupExecutor = Executors.newVirtualThreadPerTaskExecutor();

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
         * @param userId    Owning user ID
         * @return run result
         */
        Mono<Object> runWakeup(String projectId, String sessionId, String agentId, String userId);
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

    /**
     * Record holding a deferred wakeup request when a session is active.
     */
    private record DeferredWakeup(
            String projectId,
            String effectiveSessionId,
            String effectiveAgentId,
            String userId,
            Map<String, Object> payload,
            long enqueuedTimeMillis) {}

    /** Map of pending wakeups waiting for target sessions to become idle: key is "projectId:effectiveSessionId". */
    private final Map<String, DeferredWakeup> pendingWakeups = new ConcurrentHashMap<>();

    @Override
    public void close() {
        Disposable d = subscription;
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
        subscription = null;
        pendingWakeups.clear();
        wakeupExecutor.shutdown();
        started.set(false);
        LOGGER.log(Level.INFO, "Wakeup dispatcher stopped");
    }

    /**
     * Drain wakeup queue and dispatch wakeup requests for all projects.
     */
    void drainAndDispatch() {
        // 1. Process previously deferred wakeups whose target sessions have now become idle
        processDeferredWakeups();

        // 2. Collect project IDs
        java.util.Set<String> projectIds = new java.util.HashSet<>();
        for (ai.emailclaw.emailclaw.model.ProjectInfo project : projectService.list()) {
            if (project.getId() != null && !project.getId().isBlank()) {
                projectIds.add(project.getId());
            }
        }
        projectIds.add(ProjectService.PROJECT_ID_DEFAULT);

        // 3. Drain and dispatch new wakeup entries for each project
        for (String projectId : projectIds) {
            try {
                List<BusEntry> entries =
                        messageBusService.queueDrain(
                                projectId, "agentscope:wakeups", MAX_DRAIN_COUNT);
                if (entries == null || entries.isEmpty()) {
                    continue;
                }

                LOGGER.log(
                        Level.INFO,
                        "Drained {0} wakeup entry(ies) from queue for project {1}",
                        new Object[] {entries.size(), projectId});

                for (BusEntry entry : entries) {
                    dispatch(projectId, entry.payload());
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Wakeup drain error for project " + projectId, e);
            }
        }
    }

    /**
     * Check pending deferred wakeups and dispatch any whose sessions have become idle.
     */
    private void processDeferredWakeups() {
        if (pendingWakeups.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, DeferredWakeup> entry : pendingWakeups.entrySet()) {
            String key = entry.getKey();
            DeferredWakeup deferred = entry.getValue();

            // Discard wakeups older than 30 minutes to prevent resource leakage
            if (now - deferred.enqueuedTimeMillis() > 30 * 60 * 1000L) {
                if (pendingWakeups.remove(key, deferred)) {
                    LOGGER.log(
                            Level.WARNING,
                            "Discarded stale deferred wakeup for session {0} after 30 minutes:"
                                    + " agent={1}",
                            new Object[] {
                                deferred.effectiveSessionId(), deferred.effectiveAgentId()
                            });
                }
                continue;
            }

            // Check if session is now idle
            if (!wakeupTarget.isSessionRunning(
                    deferred.projectId(), deferred.effectiveSessionId())) {
                if (pendingWakeups.remove(key, deferred)) {
                    LOGGER.log(
                            Level.INFO,
                            "Session {0} is now idle; triggering previously deferred wakeup"
                                    + " (agent={1}, user={2})",
                            new Object[] {
                                deferred.effectiveSessionId(),
                                deferred.effectiveAgentId(),
                                deferred.userId()
                            });
                    triggerWakeup(
                            deferred.projectId(),
                            deferred.effectiveSessionId(),
                            deferred.effectiveAgentId(),
                            deferred.userId());
                }
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
        String userId = getString(payload, "userId");
        String sessionId = getString(payload, "sessionId");
        String agentId = getString(payload, "agentId");

        if ((sessionId == null || sessionId.isBlank()) && (agentId == null || agentId.isBlank())) {
            LOGGER.log(
                    Level.WARNING,
                    "Skipped invalid wakeup entry with no sessionId and agentId: payload={0}",
                    payload);
            return;
        }

        // agent-chat wakeup may only have agentId and no sessionId; when sessionId is blank, use
        // agentId as identifier
        String effectiveSessionId =
                (sessionId != null && !sessionId.isBlank()) ? sessionId : agentId;
        String effectiveAgentId = (agentId != null && !agentId.isBlank()) ? agentId : "";

        // Check if session is running
        if (wakeupTarget.isSessionRunning(projectId, effectiveSessionId)) {
            String key = projectId + ":" + effectiveSessionId;
            DeferredWakeup deferred =
                    new DeferredWakeup(
                            projectId,
                            effectiveSessionId,
                            effectiveAgentId,
                            userId,
                            payload,
                            System.currentTimeMillis());
            pendingWakeups.put(key, deferred);
            LOGGER.log(
                    Level.INFO,
                    "Session {0} is currently running/busy; deferred wakeup signal (agent={1},"
                            + " user={2}) for retry once session becomes idle",
                    new Object[] {effectiveSessionId, effectiveAgentId, userId});
            return;
        }

        LOGGER.log(
                Level.INFO,
                "Waking up idle session: session={0}, agent={1}, user={2}",
                new Object[] {effectiveSessionId, effectiveAgentId, userId});
        triggerWakeup(projectId, effectiveSessionId, effectiveAgentId, userId);
    }

    /**
     * Trigger wakeup execution asynchronously on the virtual thread executor.
     */
    private void triggerWakeup(
            String projectId, String effectiveSessionId, String effectiveAgentId, String userId) {
        wakeupExecutor.execute(
                () -> {
                    try {
                        wakeupTarget
                                .runWakeup(projectId, effectiveSessionId, effectiveAgentId, userId)
                                .block();
                        LOGGER.log(
                                Level.INFO,
                                "Wakeup run completed successfully: session={0}, agent={1}",
                                new Object[] {effectiveSessionId, effectiveAgentId});
                    } catch (Throwable err) {
                        LOGGER.log(
                                Level.WARNING,
                                "Wakeup run failed: session=" + effectiveSessionId,
                                err);
                    }
                });
    }

    /**
     * Returns count of currently pending deferred wakeups (package-private for testing).
     */
    int getPendingWakeupCount() {
        return pendingWakeups.size();
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
