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

import ai.emailclaw.emailclaw.channel.ChannelIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.CronExpression;
import ai.emailclaw.emailclaw.model.CronJobModel.CronExecutionRecord;
import ai.emailclaw.emailclaw.model.CronJobModel.CronJobSpec;
import ai.emailclaw.emailclaw.model.CronJobModel.CronJobState;
import ai.emailclaw.emailclaw.model.CronJobModel.DispatchSpec;
import ai.emailclaw.emailclaw.model.CronJobModel.DispatchTarget;
import ai.emailclaw.emailclaw.model.CronJobModel.ScheduleSpec;
import ai.emailclaw.emailclaw.model.CronJobStatus;
import ai.emailclaw.emailclaw.model.CronJobTrigger;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.SessionDefaults;
import ai.emailclaw.emailclaw.plugin.PluginManager;
import ai.emailclaw.emailclaw.plugin.PluginRecord;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import ai.emailclaw.emailclaw.util.UuidUtils;
import io.agentscope.core.message.Msg;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Cron job configuration service.
 *
 * <p>Responsibilities:
 * <br>1) CRUD operations (based on ConfigManager's JSON file persistence)
 * <br>2) Task scheduling (uses ScheduledExecutorService to implement scheduled triggers)
 * <br>3) Execution history management
 * <br>4) Runtime state tracking
 */
public class CronJobService implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(CronJobService.class.getName());

    private static final int CRON_HISTORY_LIMIT = 50;

    private final AppContext appContext;

    private final ConfigManager configManager;

    private final Path historyDir;

    /**
     * History JSON serializer (independent from ConfigManager's mapper to avoid lock contention).
     */
    private final ObjectMapper historyMapper = new ObjectMapper();

    /**
     * Scheduler thread pool.
     */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "CronJobScheduler");
                        t.setDaemon(true);
                        return t;
                    });

    /**
     * Registered scheduled jobs: jobId -> ScheduledFuture.
     */
    private final Map<String, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    /**
     * Runtime state: jobId -> CronJobState.
     */
    private final Map<String, CronJobState> jobStates = new ConcurrentHashMap<>();

    /**
     * Execution history cache: jobId -> list of execution records.
     */
    private final Map<String, List<CronExecutionRecord>> jobHistory = new ConcurrentHashMap<>();

    /**
     * Job-level concurrency semaphore: jobId -> current running count.
     */
    private final Map<String, Integer> runningCounts = new ConcurrentHashMap<>();

    /**
     * Dedicated lock for history persistence (isolated from ConfigManager's cronJobs lock).
     */
    private final Object historyLock = new Object();

    /**
     * Task execution thread pool (independent of the scheduler to avoid blocking it).
     */
    private final ExecutorService jobExecutor =
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean started = false;

    private final ChatService chatService;

    private final AgentService agentService;

    private final ProviderService providerService;

    private final PluginManager pluginManager;

    public CronJobService(
            AppContext appContext,
            ChatService chatService,
            AgentService agentService,
            ProviderService providerService,
            PluginManager pluginManager) {
        this.appContext = appContext;
        this.configManager = appContext.configManager();
        this.chatService = chatService;
        this.agentService = agentService;
        this.providerService = providerService;
        this.pluginManager = pluginManager;
        this.historyDir = appContext.paths().cronJobsFile.resolveSibling("cron-jobs-history");
        // Register hot reload listener: automatically reschedule when cron-jobs.json is modified
        // externally
        this.configManager.addChangeListener(
                ConfigManager.EVENT_CRON_JOBS, this::onCronJobsReloaded);
        LOGGER.info("CronJobService initialized, hot reload listener registered");
    }

    // ======================== Scheduler Lifecycle ========================
    /**
     * Start the scheduler, load all enabled jobs and register scheduled triggers.
     */
    public synchronized void start() {
        if (started) return;
        started = true;
        LOGGER.info("Starting Cron scheduler...");
        List<CronJobSpec> jobs = configManager.getCronJobs();
        for (CronJobSpec job : jobs) {
            // Initialize runtime state
            if (job.id() != null && !job.id().isBlank()) {
                jobStates.put(job.id(), CronJobState.empty());
                runningCounts.put(job.id(), 0);
            }
            if (job.enabled()) {
                try {
                    scheduleJob(job);
                } catch (Exception e) {
                    LOGGER.log(
                            Level.WARNING,
                            "Skipping invalid cron job on startup: id={0}, name={1}",
                            new Object[] {job.id(), job.name()});
                }
            }
        }
        LOGGER.log(Level.INFO, "Cron scheduler is ready, loaded {0} jobs", jobs.size());
    }

    /**
     * Stop the scheduler and cancel all pending tasks.
     */
    public synchronized void stop() {
        if (!started) return;
        started = false;
        LOGGER.info("Stopping Cron scheduler...");
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledJobs.entrySet()) {
            entry.getValue().cancel(false);
        }
        scheduledJobs.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        jobExecutor.shutdown();
        try {
            if (!jobExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                jobExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            jobExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Get all chat sessions for an agent.
     */
    public List<ai.emailclaw.emailclaw.model.ChatSessionInfo> sessions(String agentId) {
        return chatService.sessions(agentId);
    }

    /**
     * Parse and complete the TaskInfo associated with the job.
     *
     * <p>If {@code spec.taskId} is empty or {@code default}, a new task will be created
     * automatically based on the job name (ID will be UUID v7), and the taskId will be written back to the spec.
     *
     * @param spec    Cron job spec to be saved
     * @param agentId Current agent ID
     * @return Job spec with completed taskId
     */
    public CronJobSpec resolveTask(CronJobSpec spec, String agentId) {
        LOGGER.log(
                Level.FINE,
                "Parsing cron job associated task: job={0}, agent={1}",
                new Object[] {spec.name(), agentId});
        String targetTaskId = spec.taskId();
        if (targetTaskId != null && !targetTaskId.isBlank() && !"default".equals(targetTaskId)) {
            LOGGER.log(
                    Level.FINE,
                    "Target task specified, skipping auto-creation: taskId={0}",
                    targetTaskId);
            return spec;
        }
        // Need to automatically create an associated Session
        String channel = spec.dispatch() != null ? spec.dispatch().channel() : ChannelIds.CONSOLE;
        ChatSessionInfo task = chatService.newSession(agentId);
        task.setKind(ChatSessionInfo.KIND_TASK);
        task.setName(spec.name());
        task.setProjectId(spec.projectId());
        task.setChannel(channel);
        task.setDescription("Underlying task automatically created for cron job: " + spec.name());
        task.setStatus(ChatSessionInfo.TaskStatus.ACTIVE);
        // Trigger save to sessions.json
        chatService.touchSession(task);
        LOGGER.log(
                Level.INFO,
                "Cron job didn't specify associated task, auto-created: taskId={0}, name={1}",
                new Object[] {task.getId(), task.getName()});
        // Update dispatch session id
        DispatchSpec oldDispatch = spec.dispatch();
        DispatchTarget oldTarget =
                oldDispatch != null ? oldDispatch.target() : new DispatchTarget("", "");
        DispatchSpec newDispatch =
                new DispatchSpec(
                        oldDispatch != null ? oldDispatch.type() : "channel",
                        oldDispatch != null ? oldDispatch.channel() : channel,
                        new DispatchTarget(oldTarget.userId(), task.getId()),
                        oldDispatch != null ? oldDispatch.mode() : "final",
                        oldDispatch != null ? oldDispatch.meta() : Collections.emptyMap());
        return new CronJobSpec(
                spec.id(),
                spec.projectId(),
                spec.name(),
                spec.enabled(),
                spec.schedule(),
                task.getId(),
                spec.inputPrompt(),
                newDispatch,
                spec.saveResultToInbox(),
                spec.runtime(),
                spec.meta(),
                spec.countdown());
    }

    // ======================== CRUD Operations ========================
    /**
     * List all cron jobs.
     */
    public List<CronJobSpec> list() {
        return configManager.getCronJobs();
    }

    /**
     * Get a single job by ID.
     */
    public CronJobSpec get(String jobId) {
        return configManager.getCronJobs().stream()
                .filter(j -> jobId.equals(j.id()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Add a cron job. Server auto-generates UUID.
     */
    public CronJobSpec add(CronJobSpec spec) {
        String jobId = UuidUtils.randomUUIDv7().toString();
        CronJobSpec created = spec.withId(jobId);
        LOGGER.log(
                Level.INFO,
                "Added cron job: id={0}, name={1}, cron={2}",
                new Object[] {
                    jobId,
                    created.name(),
                    created.schedule() != null ? created.schedule().cron() : "N/A"
                });
        List<CronJobSpec> jobs = new ArrayList<>(configManager.getCronJobs());
        jobs.add(created);
        configManager.saveCronJobs(jobs);
        // Initialize runtime state
        jobStates.put(jobId, CronJobState.empty());
        runningCounts.put(jobId, 0);
        // Register schedule if started and job is enabled
        if (started && created.enabled()) {
            scheduleJob(created);
        }
        return created;
    }

    /**
     * Update (replace) a cron job.
     */
    public CronJobSpec update(CronJobSpec spec) {
        LOGGER.log(
                Level.INFO,
                "Updating cron job: id={0}, name={1}",
                new Object[] {spec.id(), spec.name()});
        // Cancel old schedule
        unscheduleJob(spec.id());
        List<CronJobSpec> jobs = new ArrayList<>(configManager.getCronJobs());
        boolean found = false;
        for (int i = 0; i < jobs.size(); i++) {
            if (spec.id().equals(jobs.get(i).id())) {
                jobs.set(i, spec);
                found = true;
                break;
            }
        }
        if (!found) {
            jobs.add(spec);
        }
        configManager.saveCronJobs(jobs);
        // Register new schedule if started and job is enabled
        if (started && spec.enabled()) {
            scheduleJob(spec);
        }
        return spec;
    }

    /**
     * Remove a cron job.
     */
    public boolean remove(String jobId) {
        LOGGER.log(Level.INFO, "Removed cron job: id={0}", jobId);
        unscheduleJob(jobId);
        jobStates.remove(jobId);
        runningCounts.remove(jobId);
        jobHistory.remove(jobId);
        // Clean up history file
        deleteHistoryFile(jobId);
        List<CronJobSpec> jobs = new ArrayList<>(configManager.getCronJobs());
        boolean removed = jobs.removeIf(j -> jobId.equals(j.id()));
        if (removed) {
            configManager.saveCronJobs(jobs);
        }
        return removed;
    }

    /**
     * Toggle enable/disable status.
     */
    public CronJobSpec toggleEnabled(String jobId) {
        CronJobSpec spec = get(jobId);
        if (spec == null) return null;
        boolean newEnabled = !spec.enabled();
        CronJobSpec updated = spec.withEnabled(newEnabled);
        LOGGER.log(
                Level.INFO,
                "Toggled cron job status: id={0}, enabled={1}",
                new Object[] {jobId, newEnabled});
        if (newEnabled) {
            // Enable: register schedule
            unscheduleJob(jobId);
            if (started) {
                scheduleJob(updated);
            }
        } else {
            // Disable: cancel schedule
            unscheduleJob(jobId);
        }
        List<CronJobSpec> jobs = new ArrayList<>(configManager.getCronJobs());
        for (int i = 0; i < jobs.size(); i++) {
            if (jobId.equals(jobs.get(i).id())) {
                jobs.set(i, updated);
                break;
            }
        }
        configManager.saveCronJobs(jobs);
        return updated;
    }

    /**
     * Execute a specified job immediately once (manual trigger).
     */
    public boolean executeNow(String jobId) {
        CronJobSpec spec = get(jobId);
        if (spec == null) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to execute immediately, job does not exist: id={0}",
                    jobId);
            return false;
        }
        LOGGER.log(
                Level.INFO,
                "Executing cron job immediately: id={0}, name={1}",
                new Object[] {jobId, spec.name()});
        jobExecutor.execute(() -> executeJob(spec, CronJobTrigger.MANUAL));
        return true;
    }

    // ======================== Runtime State ========================
    /**
     * Get runtime state of a job.
     */
    public CronJobState getState(String jobId) {
        return jobStates.getOrDefault(jobId, CronJobState.empty());
    }

    /**
     * Get a state view of all jobs.
     */
    public Map<String, CronJobState> getAllStates() {
        return Collections.unmodifiableMap(jobStates);
    }

    // ======================== Execution History ========================
    /**
     * Get the execution history of a job.
     */
    public List<CronExecutionRecord> getHistory(String jobId) {
        if (jobHistory.containsKey(jobId)) {
            return jobHistory.get(jobId);
        }
        List<CronExecutionRecord> records = loadHistory(jobId);
        jobHistory.put(jobId, records);
        return records;
    }

    // ======================== Internal Methods (Scheduling) ========================
    /**
     * Register a scheduled trigger for a job.
     */
    private void scheduleJob(CronJobSpec spec) {
        if (spec.id() == null || spec.id().isBlank()) return;
        ScheduleSpec schedule = spec.schedule();
        if (schedule == null) return;
        String jobId = spec.id();
        Runnable task = () -> executeJob(spec, CronJobTrigger.SCHEDULED);
        if ("once".equals(schedule.type())) {
            scheduleOnce(jobId, schedule, task);
        } else if ("cron".equals(schedule.type())) {
            scheduleCron(jobId, schedule, task);
        }
    }

    private void scheduleOnce(String jobId, ScheduleSpec schedule, Runnable task) {
        if (schedule.runAt() == null || schedule.runAt().isBlank()) return;
        try {
            ZoneId zone = ZoneId.of(schedule.timezone());
            LocalDateTime runAt =
                    LocalDateTime.parse(schedule.runAt(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            ZonedDateTime runZdt = runAt.atZone(zone);
            ZonedDateTime now = ZonedDateTime.now(zone);
            long delayMs = Duration.between(now, runZdt).toMillis();
            if (delayMs < 0) {
                LOGGER.log(
                        Level.WARNING,
                        "Task execution time has expired: id={0}, runAt={1}",
                        new Object[] {jobId, schedule.runAt()});
                return;
            }
            updateState(
                    jobId, runZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), null, null, null);
            if (schedule.repeatEveryDays() != null && schedule.repeatEveryDays() > 0) {
                // Repeated execution: use fixed rate
                ScheduledFuture<?> future =
                        scheduler.scheduleAtFixedRate(
                                task,
                                delayMs,
                                (long) schedule.repeatEveryDays() * 24 * 3600 * 1000,
                                TimeUnit.MILLISECONDS);
                scheduledJobs.put(jobId, future);
                LOGGER.log(
                        Level.INFO,
                        "Scheduled repeating one-time task: id={0}, delayMs={1}, intervalDays={2}",
                        new Object[] {jobId, delayMs, schedule.repeatEveryDays()});
            } else {
                // Single execution
                ScheduledFuture<?> future =
                        scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
                scheduledJobs.put(jobId, future);
                LOGGER.log(
                        Level.INFO,
                        "Scheduled one-time task: id={0}, delayMs={1}",
                        new Object[] {jobId, delayMs});
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to schedule one-time task: id=" + jobId, e);
        }
    }

    private void scheduleCron(String jobId, ScheduleSpec schedule, Runnable task) {
        try {
            String cronExpr = schedule.cron();
            if (cronExpr == null || cronExpr.isBlank()) return;
            ZoneId zone = ZoneId.of(schedule.timezone());
            CronExpression cron = new CronExpression(cronExpr);
            // Calculate initial execution delay
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime next = cron.nextAfter(now);
            if (next == null) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to calculate next execution time for cron job: id={0}",
                        jobId);
                return;
            }
            long delayMs = Duration.between(now, next).toMillis();
            if (delayMs < 0) delayMs = 0;
            updateState(
                    jobId, next.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), null, null, null);
            // Reschedule next execution after each run
            ScheduledFuture<?> future =
                    scheduler.schedule(
                            () -> {
                                jobExecutor.execute(
                                        () -> {
                                            try {
                                                task.run();
                                            } finally {
                                                // Reschedule next execution after completion
                                                if (started) {
                                                    CronJobSpec currentSpec = get(jobId);
                                                    if (currentSpec != null
                                                            && currentSpec.enabled()) {
                                                        scheduleCron(
                                                                jobId,
                                                                currentSpec.schedule(),
                                                                () ->
                                                                        executeJob(
                                                                                currentSpec,
                                                                                CronJobTrigger
                                                                                        .SCHEDULED));
                                                    }
                                                }
                                            }
                                        });
                            },
                            delayMs,
                            TimeUnit.MILLISECONDS);
            scheduledJobs.put(jobId, future);
            LOGGER.log(
                    Level.INFO,
                    "Scheduled cron job: id={0}, cron={1}, delayMs={2}",
                    new Object[] {jobId, cronExpr, delayMs});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to schedule cron job: id=" + jobId, e);
        }
    }

    /**
     * Cancel the scheduling of a specified job.
     */
    private void unscheduleJob(String jobId) {
        ScheduledFuture<?> future = scheduledJobs.remove(jobId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            LOGGER.log(Level.FINE, "Cancelled job scheduling: id={0}", jobId);
        }
    }

    // ======================== Internal Methods (Execution) ========================
    /**
     * Execute a job (update state and simulate execution via callback).
     */
    private void executeJob(CronJobSpec spec, CronJobTrigger trigger) {
        String jobId = spec.id();
        if (jobId == null || jobId.isBlank()) return;
        // Concurrency control
        int maxConcurrency = spec.runtime() != null ? spec.runtime().maxConcurrency() : 1;
        int currentRunning = runningCounts.getOrDefault(jobId, 0);
        if (currentRunning >= maxConcurrency) {
            LOGGER.log(
                    Level.WARNING,
                    "Job has reached maximum concurrency, skipping execution: id={0}, running={1},"
                            + " max={2}",
                    new Object[] {jobId, currentRunning, maxConcurrency});
            recordExecution(
                    jobId,
                    CronJobStatus.SKIPPED,
                    "Reached maximum concurrency " + maxConcurrency,
                    trigger);
            return;
        }
        runningCounts.merge(jobId, 1, Integer::sum);
        ZonedDateTime runAt = ZonedDateTime.now();
        String runAtStr = runAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        // Update status: running
        updateState(jobId, null, runAtStr, CronJobStatus.RUNNING, null);
        try {
            LOGGER.log(
                    Level.INFO,
                    "Executing scheduled task: id={0}, name={1}, trigger={2}",
                    new Object[] {jobId, spec.name(), trigger});
            AgentInfo agent = agentService.currentDefault();
            if (agent == null || !agent.isEnabled()) {
                throw new RuntimeException("No available Agent");
            }
            ProviderInfo provider = chatService.resolveEffectiveProvider(agent);
            String modelId = chatService.resolveEffectiveModelId(agent, provider);
            ChatSessionInfo session;
            String targetTaskId = spec.taskId();
            if (targetTaskId != null
                    && !targetTaskId.isBlank()
                    && !"default".equals(targetTaskId)) {
                // Fetch the existing ChatSessionInfo
                List<ChatSessionInfo> existingSessions = chatService.sessions(agent.getId());
                ChatSessionInfo task =
                        existingSessions.stream()
                                .filter(t -> targetTaskId.equals(t.getId()))
                                .findFirst()
                                .orElse(null);
                if (task != null) {
                    session = task;
                } else {
                    LOGGER.log(
                            Level.WARNING,
                            "Target task session not found, creating new session: taskId={0}",
                            targetTaskId);
                    session = chatService.newSession(agent.getId());
                }
            } else {
                session = chatService.newSession(agent.getId());
            }
            // Set dispatch channel and user ID
            String dispatchChannel = spec.dispatch() != null ? spec.dispatch().channel() : null;
            session.setChannel(
                    dispatchChannel != null && !dispatchChannel.isBlank()
                            ? dispatchChannel
                            : SessionDefaults.DEFAULT_CHANNEL);
            if (ChannelIds.CONSOLE.equals(session.getChannel())) {
                session.setUserId(SessionDefaults.LOCAL_USER_ID);
            }
            chatService.updateSession(session);
            LOGGER.log(
                    Level.INFO,
                    "Scheduled task session set up: channel={0}, userId={1}",
                    new Object[] {session.getChannel(), session.getUserId()});
            String prompt = spec.inputPrompt() != null ? spec.inputPrompt() : "";
            CountDownLatch latch = new CountDownLatch(1);
            final Msg[] capturedMsg = new Msg[1];
            chatService.sendMessage(
                    agent,
                    provider,
                    modelId,
                    session,
                    prompt,
                    new ai.emailclaw.emailclaw.service.StreamCallback() {

                        @Override
                        public void onPart(ChatMessagePart part, boolean startsNew) {}

                        @Override
                        public void onCompleted(Msg message) {
                            capturedMsg[0] = message;
                            latch.countDown();
                        }
                    });
            latch.await(5, TimeUnit.MINUTES);
            CronJobStatus status = CronJobStatus.SUCCESS;
            updateState(jobId, null, runAtStr, status, null);
            recordExecution(jobId, status, null, trigger);
            LOGGER.log(
                    Level.INFO,
                    "Scheduled task execution complete: id={0}, status={1}",
                    new Object[] {jobId, status});
            // Send Agent reply to channel (if the channel plugin implements replyToSession, it will
            // be delivered to the user as needed)
            if (capturedMsg[0] != null
                    && session.getChannel() != null
                    && !SessionDefaults.DEFAULT_CHANNEL.equals(session.getChannel())) {
                String text = capturedMsg[0].getTextContent();
                if (text != null && !text.isBlank()) {
                    PluginRecord record = pluginManager.getPlugin(session.getChannel());
                    if (record != null && record.instance != null) {
                        record.instance.replyToSession(session.getId(), text);
                    }
                }
            }
        } catch (Exception e) {
            CronJobStatus status = CronJobStatus.ERROR;
            String error = e.getMessage();
            LOGGER.log(Level.WARNING, "Scheduled task execution failed: id=" + jobId, e);
            updateState(jobId, null, runAtStr, status, error);
            recordExecution(jobId, status, error, trigger);
        } finally {
            runningCounts.merge(jobId, -1, Integer::sum);
            // Countdown logic
            CronJobSpec latestSpec = get(jobId);
            if (latestSpec != null
                    && latestSpec.countdown() != null
                    && latestSpec.countdown() != -1) {
                int newCountdown = latestSpec.countdown() - 1;
                boolean enabled = latestSpec.enabled();
                if (newCountdown <= 0) {
                    enabled = false;
                    // After disabling, reset newCountdown to default -1
                    newCountdown = -1;
                    LOGGER.log(
                            Level.INFO,
                            "Scheduled task execution count exhausted, automatically disabled:"
                                    + " id={0}",
                            jobId);
                }
                CronJobSpec updatedSpec =
                        new CronJobSpec(
                                latestSpec.id(),
                                latestSpec.projectId(),
                                latestSpec.name(),
                                enabled,
                                latestSpec.schedule(),
                                latestSpec.taskId(),
                                latestSpec.inputPrompt(),
                                latestSpec.dispatch(),
                                latestSpec.saveResultToInbox(),
                                latestSpec.runtime(),
                                latestSpec.meta(),
                                newCountdown);
                update(updatedSpec);
            }
        }
    }

    /**
     * Update runtime state of the job.
     */
    private void updateState(
            String jobId,
            String nextRunAt,
            String lastRunAt,
            CronJobStatus lastStatus,
            String lastError) {
        CronJobState oldState = jobStates.getOrDefault(jobId, CronJobState.empty());
        CronJobState newState =
                new CronJobState(
                        nextRunAt != null ? nextRunAt : oldState.nextRunAt(),
                        lastRunAt != null ? lastRunAt : oldState.lastRunAt(),
                        lastStatus != null ? lastStatus : oldState.lastStatus(),
                        lastError != null ? lastError : oldState.lastError());
        jobStates.put(jobId, newState);
    }

    /**
     * Record an execution history.
     */
    private void recordExecution(
            String jobId, CronJobStatus status, String error, CronJobTrigger trigger) {
        CronExecutionRecord record =
                new CronExecutionRecord(
                        ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        status,
                        error,
                        trigger);
        synchronized (historyLock) {
            List<CronExecutionRecord> records =
                    jobHistory.computeIfAbsent(jobId, k -> loadHistory(jobId));
            records.add(0, record);
            // Limit the number of history records
            if (records.size() > CRON_HISTORY_LIMIT) {
                records = new ArrayList<>(records.subList(0, CRON_HISTORY_LIMIT));
                jobHistory.put(jobId, records);
            }
            saveHistory(jobId, records);
        }
    }

    // ======================== Hot Reload Handling ========================
    /**
     * Callback when ConfigManager detects external modification of cron-jobs.json and finishes hot reloading.
     *
     * <p>Reschedule all tasks: first cancel existing schedules, then re-register based on the latest configuration.
     */
    private synchronized void onCronJobsReloaded() {
        LOGGER.info("External change detected in cron-jobs.json, starting to reschedule...");
        // 1. Cancel all existing schedules
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledJobs.entrySet()) {
            ScheduledFuture<?> future = entry.getValue();
            if (future != null && !future.isDone()) {
                future.cancel(false);
            }
        }
        scheduledJobs.clear();
        // 2. Clear runtime state, prepare for reloading
        jobStates.clear();
        runningCounts.clear();
        // 3. Get latest configuration from ConfigManager memory state and reschedule
        List<CronJobSpec> jobs = configManager.getCronJobs();
        for (CronJobSpec job : jobs) {
            if (job.id() != null && !job.id().isBlank()) {
                jobStates.put(job.id(), CronJobState.empty());
                runningCounts.put(job.id(), 0);
            }
            if (started && job.enabled()) {
                try {
                    scheduleJob(job);
                } catch (Exception e) {
                    LOGGER.log(
                            Level.WARNING,
                            "Skipping invalid scheduled task after hot reload: id={0}, name={1}",
                            new Object[] {job.id(), job.name()});
                }
            }
        }
        LOGGER.log(
                Level.INFO,
                "cron-jobs.json hot reload complete, rescheduled {0} tasks",
                jobs.size());
    }

    private List<CronExecutionRecord> loadHistory(String jobId) {
        Path file = historyFileFor(jobId);
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            String content = Files.readString(file);
            if (content.isBlank()) return new ArrayList<>();
            return historyMapper.readValue(
                    content, new TypeReference<List<CronExecutionRecord>>() {});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read task history records: id=" + jobId, e);
            return new ArrayList<>();
        }
    }

    private void saveHistory(String jobId, List<CronExecutionRecord> records) {
        try {
            Files.createDirectories(historyDir);
            Path file = historyFileFor(jobId);
            String content =
                    historyMapper.writerWithDefaultPrettyPrinter().writeValueAsString(records);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save task history records: id=" + jobId, e);
        }
    }

    private void deleteHistoryFile(String jobId) {
        try {
            Files.deleteIfExists(historyFileFor(jobId));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete task history file: id=" + jobId, e);
        }
    }

    private Path historyFileFor(String jobId) {
        String encoded = jobId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return historyDir.resolve(encoded + ".json");
    }
}
