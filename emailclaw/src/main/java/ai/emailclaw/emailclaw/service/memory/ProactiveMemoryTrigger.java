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
package ai.emailclaw.emailclaw.service.memory;

import ai.emailclaw.emailclaw.service.MessageBusService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Proactive memory trigger - periodically checks the memory directory of each agent, and when memory entries marked as "proactive" are found,
 * pushes memory events to the agent's inbox via {@link MessageBusService}.
 *
 * <p>Reuses the scheduled scheduling pattern of CronJobService and the event pushing pattern of PlanBroadcaster.
 *
 * <p>Check interval: scans the memory directory of all active agents every 60 seconds.
 */
public class ProactiveMemoryTrigger {

    private static final Logger LOGGER = Logger.getLogger(ProactiveMemoryTrigger.class.getName());

    private static final long SCAN_INTERVAL_SECONDS = 60;

    private final MemoryService memoryService;

    private final MessageBusService messageBusService;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "proactive-memory-trigger"));

    private volatile boolean enabled = true;

    public ProactiveMemoryTrigger(
            MemoryService memoryService, MessageBusService messageBusService) {
        this.memoryService = memoryService;
        this.messageBusService = messageBusService;
        LOGGER.info("ProactiveMemoryTrigger initialization completed");
    }

    /**
     * Start the scheduled scan.
     *
     * @param agentIds List of agent IDs to scan (needs to be called again when dynamically changed)
     */
    public void start(List<String> agentIds) {
        scheduler.scheduleWithFixedDelay(
                () -> {
                    if (!enabled) return;
                    try {
                        for (String agentId : agentIds) {
                            List<String> proactiveKeys = memoryService.listProactiveKeys(agentId);
                            if (proactiveKeys.isEmpty()) continue;
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("type", "memory.proactive_recall");
                            payload.put("agentId", agentId);
                            payload.put("keys", String.join(",", proactiveKeys));
                            payload.put("timestamp", LocalDateTime.now().toString());
                            messageBusService
                                    .getMessageBus()
                                    .publish("memory.proactive_recall", payload)
                                    .subscribe();
                            LOGGER.log(
                                    Level.INFO,
                                    "Proactive memory event pushed: agentId={0}, keys={1}",
                                    new Object[] {agentId, proactiveKeys});
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "ProactiveMemoryTrigger scan exception", e);
                    }
                },
                SCAN_INTERVAL_SECONDS,
                SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        LOGGER.log(
                Level.INFO,
                "ProactiveMemoryTrigger started, scan interval: {0} seconds",
                SCAN_INTERVAL_SECONDS);
    }

    /**
     * Stop the scheduled scan.
     */
    public void stop() {
        enabled = false;
        scheduler.shutdown();
        LOGGER.info("ProactiveMemoryTrigger stopped");
    }
}
