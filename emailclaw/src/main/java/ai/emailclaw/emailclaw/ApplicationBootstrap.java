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
package ai.emailclaw.emailclaw;

import ai.emailclaw.emailclaw.channel.ChannelIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentRuntimeStatus;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.CronJobModel;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.plugin.DefaultPluginContext;
import ai.emailclaw.emailclaw.plugin.PluginContext;
import ai.emailclaw.emailclaw.plugin.PluginManager;
import ai.emailclaw.emailclaw.plugin.PluginRecord;
import ai.emailclaw.emailclaw.plugin.PluginRegistry;
import ai.emailclaw.emailclaw.service.AcpService;
import ai.emailclaw.emailclaw.service.AdaptiveFinalAnswerFilterMiddleware;
import ai.emailclaw.emailclaw.service.AgentRuntimeDispatcher;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.BackupService;
import ai.emailclaw.emailclaw.service.BootstrapService;
import ai.emailclaw.emailclaw.service.ChannelMessageBusIntegration;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ChatSessionRepository;
import ai.emailclaw.emailclaw.service.CronJobService;
import ai.emailclaw.emailclaw.service.MarketService;
import ai.emailclaw.emailclaw.service.McpService;
import ai.emailclaw.emailclaw.service.MessageBusService;
import ai.emailclaw.emailclaw.service.MessagePipeline;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.RateLimitMiddleware;
import ai.emailclaw.emailclaw.service.SecurityService;
import ai.emailclaw.emailclaw.service.SessionTitleGenerator;
import ai.emailclaw.emailclaw.service.SkillService;
import ai.emailclaw.emailclaw.service.SpawnRegistryService;
import ai.emailclaw.emailclaw.service.StreamCallback;
import ai.emailclaw.emailclaw.service.ToolResultDiffMiddleware;
import ai.emailclaw.emailclaw.service.ToolRuntimeContext;
import ai.emailclaw.emailclaw.service.ToolService;
import ai.emailclaw.emailclaw.service.WakeupDispatcherService;
import ai.emailclaw.emailclaw.service.memory.MemoAutoSync;
import ai.emailclaw.emailclaw.service.memory.MemoryRecallMiddleware;
import ai.emailclaw.emailclaw.service.memory.MemoryService;
import ai.emailclaw.emailclaw.service.memory.ProactiveMemoryTrigger;
import ai.emailclaw.emailclaw.service.plan.JsonFilePlanStore;
import ai.emailclaw.emailclaw.service.plan.PlanBroadcaster;
import ai.emailclaw.emailclaw.service.plan.PlanHintCache;
import ai.emailclaw.emailclaw.service.plan.PlanService;
import ai.emailclaw.emailclaw.service.plan.PlanStore;
import ai.emailclaw.emailclaw.service.plan.PlanToHintMiddleware;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.AppHomeResolver;
import ai.emailclaw.emailclaw.storage.AppPaths;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import ai.emailclaw.emailclaw.storage.sqlite.DatabaseManager;
import ai.emailclaw.emailclaw.storage.sqlite.SqliteAgentStateStore;
import ai.emailclaw.emailclaw.storage.sqlite.SqliteAgentStatsRepository;
import ai.emailclaw.emailclaw.storage.sqlite.SqliteChatSessionRepository;
import ai.emailclaw.emailclaw.storage.sqlite.SqliteCronHistoryRepository;
import ai.emailclaw.emailclaw.storage.sqlite.SqliteMemoryRepository;
import ai.emailclaw.emailclaw.storage.sqlite.SqliteTokenUsageRepository;
import ai.emailclaw.emailclaw.util.PlaywrightManager;
import ai.emailclaw.emailclaw.util.WebViewUtils;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Mono;

/**
 * Application Bootstrap: Encapsulates the initialization logic shared by FxApp and ServiceApp. Adheres to Pure DI pattern/principle: ApplicationBootstrap acts as the sole Composition Root.
 * When adding new functional modules, just follow this process:
 * Write constructors for new classes, and declare all required external dependencies in the parameter list.
 * In the internal logic, never secretly obtain instances via XXX.getInstance() or AppContext.get().
 * In ApplicationBootstrap, locate the position of the new module in the dependency topology, instantiate it via new, and pass it to upper-level components that depend on it.
 * If there are complex objects dynamically generated at runtime (like new Task sessions), consider injecting a dedicated Factory rather than using IoC container lookup services at runtime.
 * <p>Extracted the duplicated service initialization, plugin loading, cron job, and wakeup dispatcher startup logic from the two boot entries,
 * ensuring completely consistent behavior between both startup methods.
 *
 * <p>Typical usage:
 * <pre>{@code
 * ApplicationBootstrap.BootstrapResult result = ApplicationBootstrap.initialize();
 * // ... use service instances in result
 * ApplicationBootstrap.shutdown(result);
 * }</pre>
 */
public final class ApplicationBootstrap {

    private static final Logger LOGGER = Logger.getLogger(ApplicationBootstrap.class.getName());

    /**
     * Private constructor to prevent instantiation.
     */
    private ApplicationBootstrap() {}

    /**
     * Initialization result record: contains all initialized service instances.
     *
     * @param repository          Persistence context
     * @param providerService     Model provider service
     * @param agentService        Agent service
     * @param toolRuntimeContext  Tool runtime context
     * @param toolService         Tool service
     * @param skillService        Skill service
     * @param chatService         Chat service
     * @param channelService      Channel service
     * @param mcpService          MCP service
     * @param acpService          ACP service
     * @param securityService     Security service
     * @param backupService       Backup service
     * @param marketService       Market service
     * @param pluginManager       Plugin manager
     * @param cronJobService      Cron job scheduler
     * @param messageBusService   Message bus service
     * @param paths               Application path configuration
     */
    public record BootstrapResult(
            AppContext repository,
            ProviderService providerService,
            AgentService agentService,
            ProjectService projectService,
            ToolRuntimeContext toolRuntimeContext,
            ToolService toolService,
            SkillService skillService,
            ChatService chatService,
            ChannelService channelService,
            McpService mcpService,
            AcpService acpService,
            SecurityService securityService,
            BackupService backupService,
            MarketService marketService,
            PluginManager pluginManager,
            CronJobService cronJobService,
            MessageBusService messageBusService,
            ChatSessionRepository chatSessionRepository,
            AppPaths paths) {}

    /**
     * Execute the complete core application initialization and assembly process.
     *
     * <p>In the entire AI Agent system architecture, this method plays the crucial role of "cerebral cortex operator". All underlying neurons (core services, data repository models, tool suites,
     * large model provider channels, and memory stream gateways, etc.) are all instantiated and assembled (dependency injected) here.
     *
     * <p>Initialization sequence and boundary conventions:
     * <ol>
     *   <li>Parse and anchor the application main working directory (will throw a fatal exception to terminate startup if failed).</li>
     *   <li>Initialize base persistent structures (AppPaths, AppContext), and ensure underlying file system directories are ready.</li>
     *   <li>Instantiate all core service layer components (force dependency injection via constructors, building a Directed Acyclic Graph).</li>
     *   <li>Execute bootstrap sync tasks (like deploying default Agent data, syncing frontend pages, etc.).</li>
     *   <li>Start third-party extension plugin engine (auto discover and enable external skill plugins).</li>
     *   <li>Start background cron probes (CronJob) to maintain periodic tasks.</li>
     * </ol>
     *
     * @return The entity {@link BootstrapResult} that aggregates all successfully assembled and active singleton services
     */
    public static BootstrapResult initialize() {
        // 1. Parse application main directory
        Path appHome = AppHomeResolver.resolveAppHome();
        LOGGER.log(Level.INFO, "Detected application working directory: {0}", appHome);

        // 2. Initialize persistence layer
        AppPaths paths = new AppPaths(appHome);
        // Ensure directories exist before creating services, preventing WatchService registration
        // failure
        paths.ensureStructure();
        DatabaseManager databaseManager = new DatabaseManager(paths.databaseFile);
        PlaywrightManager.setBrowserDataDir(paths.browserDataDir);
        WebViewUtils.setWebviewDataDir(paths.webviewDir);
        SqliteChatSessionRepository sqliteChatSessionRepository =
                new SqliteChatSessionRepository(databaseManager);
        SqliteAgentStateStore sqliteAgentStateStore = new SqliteAgentStateStore(databaseManager);
        SqliteTokenUsageRepository sqliteTokenUsageRepository =
                new SqliteTokenUsageRepository(databaseManager);
        SqliteAgentStatsRepository sqliteAgentStatsRepository =
                new SqliteAgentStatsRepository(databaseManager);
        SqliteCronHistoryRepository sqliteCronHistoryRepository =
                new SqliteCronHistoryRepository(databaseManager);
        SqliteMemoryRepository sqliteMemoryRepository = new SqliteMemoryRepository(databaseManager);

        ConfigManager configManager =
                new ConfigManager(
                        paths,
                        sqliteChatSessionRepository,
                        sqliteTokenUsageRepository,
                        sqliteAgentStatsRepository);

        AppContext repository =
                new AppContext(
                        paths,
                        configManager,
                        databaseManager,
                        sqliteChatSessionRepository,
                        sqliteAgentStateStore,
                        sqliteTokenUsageRepository,
                        sqliteAgentStatsRepository,
                        sqliteCronHistoryRepository,
                        sqliteMemoryRepository);
        // Seed the schedule default timezone from global configuration and keep it in sync
        // (covers UI saves and external hot reloads of global-config.json)
        CronJobModel.setDefaultTimezone(repository.configManager().getGlobalConfig().getTimeZone());
        repository
                .configManager()
                .addChangeListener(
                        ConfigManager.EVENT_GLOBAL_CONFIG,
                        () ->
                                CronJobModel.setDefaultTimezone(
                                        repository
                                                .configManager()
                                                .getGlobalConfig()
                                                .getTimeZone()));
        // Ensure skills pool files are extracted/ready, so later services (like AgentService ->
        // ConfigManager) can read the full list of skill names
        new BootstrapService(repository, null).preInitializeSkillsPool();
        // 3. Instantiate core service layer objects, and perform dependency injection (DI)
        LOGGER.fine("Loading service layer components...");
        ProviderService providerService = new ProviderService(repository);
        AgentService agentService = new AgentService(repository);
        ai.emailclaw.emailclaw.ui.plugin.PluginUIFactory.setAgentService(agentService);
        ProjectService projectService = new ProjectService(repository);
        // Message bus and sub-agent registry (prerequisite dependencies for ToolRuntimeContext)
        MessageBusService messageBusService = new MessageBusService(projectService);
        SpawnRegistryService spawnRegistryService = new SpawnRegistryService(databaseManager);
        MemoryService memoryService = new MemoryService(sqliteMemoryRepository, projectService);
        ToolRuntimeContext toolRuntimeContext =
                new ToolRuntimeContext(
                        repository,
                        agentService,
                        providerService,
                        messageBusService,
                        spawnRegistryService,
                        projectService,
                        memoryService);
        PluginRegistry pluginRegistry = new PluginRegistry();
        McpService mcpService = new McpService(repository);
        ToolService toolService = new ToolService(repository, pluginRegistry, mcpService);
        SkillService skillService = new SkillService(repository);
        GovernanceService governanceService = new GovernanceService(repository);
        RateLimitMiddleware rateLimitMiddleware = new RateLimitMiddleware(Duration.ofMillis(1000));
        PlanStore planStore = new JsonFilePlanStore(projectService);
        PlanHintCache planHintCache = new PlanHintCache();
        PlanBroadcaster planBroadcaster = new PlanBroadcaster(messageBusService);
        PlanService planService = new PlanService(planStore, planBroadcaster, planHintCache);
        PlanToHintMiddleware planToHintMiddleware =
                new PlanToHintMiddleware(planService, planHintCache);
        MemoryRecallMiddleware memoryRecallMiddleware =
                new MemoryRecallMiddleware(memoryService, toolRuntimeContext);
        MemoAutoSync memoAutoSync = new MemoAutoSync(projectService);
        List<String> allAgentIds = agentService.list().stream().map(a -> a.getId()).toList();
        ProactiveMemoryTrigger proactiveTrigger =
                new ProactiveMemoryTrigger(memoryService, messageBusService, projectService);
        proactiveTrigger.start(allAgentIds);
        ChatSessionRepository chatSessionRepository =
                new ChatSessionRepository(projectService, sqliteAgentStateStore);
        ChannelMessageBusIntegration channelMessageBusIntegration =
                new ChannelMessageBusIntegration(messageBusService);
        SessionTitleGenerator titleGenerator = new SessionTitleGenerator(repository);
        AdaptiveFinalAnswerFilterMiddleware adaptiveFinalAnswerFilterMiddleware =
                new AdaptiveFinalAnswerFilterMiddleware();
        ToolResultDiffMiddleware toolResultDiffMiddleware = new ToolResultDiffMiddleware();
        AgentRuntimeDispatcher agentRuntimeDispatcher =
                new AgentRuntimeDispatcher(
                        repository,
                        toolService,
                        skillService,
                        toolRuntimeContext,
                        governanceService,
                        rateLimitMiddleware,
                        adaptiveFinalAnswerFilterMiddleware,
                        toolResultDiffMiddleware,
                        messageBusService,
                        planToHintMiddleware,
                        memoryRecallMiddleware,
                        chatSessionRepository);
        ChatService chatService =
                new ChatService(
                        repository,
                        agentService,
                        providerService,
                        toolRuntimeContext,
                        governanceService,
                        agentRuntimeDispatcher);
        MessagePipeline messagePipeline =
                new MessagePipeline(
                        repository,
                        agentService,
                        providerService,
                        governanceService,
                        agentRuntimeDispatcher,
                        chatSessionRepository,
                        toolRuntimeContext,
                        messageBusService,
                        memoAutoSync,
                        titleGenerator,
                        chatService);
        chatService.setMessagePipeline(messagePipeline);
        // Other modular services
        ChannelService channelService = new ChannelService(repository, pluginRegistry);
        AcpService acpService = new AcpService(repository);
        SecurityService securityService = new SecurityService(repository);
        BackupService backupService = new BackupService(repository);
        MarketService marketService = new MarketService();
        // 4. Execute bootstrap initialization tasks (like creating default Agent, syncing files,
        // etc.)
        LOGGER.info("Executing system initialization bootstrap...");
        new BootstrapService(repository, projectService).initialize();
        // 5. Start background channel service (managed uniformly via plugin manager, auto-discover
        // built-in + external Plugins)
        PluginContext pluginContext =
                new DefaultPluginContext(
                        channelService,
                        chatService,
                        agentService,
                        providerService,
                        repository.configManager(),
                        projectService);
        PluginManager pluginManager =
                new PluginManager(pluginContext, paths.pluginsDir, pluginRegistry);
        pluginManager.discoverAndLoadAll();
        // 6. Create and start cron job scheduler (after PluginManager, so Emailclaw plugins are
        // ready)
        CronJobService cronJobService =
                new CronJobService(
                        repository, chatService, agentService, providerService, pluginManager);
        cronJobService.start();
        LOGGER.info("Core service layer is ready");
        return new BootstrapResult(
                repository,
                providerService,
                agentService,
                projectService,
                toolRuntimeContext,
                toolService,
                skillService,
                chatService,
                channelService,
                mcpService,
                acpService,
                securityService,
                backupService,
                marketService,
                pluginManager,
                cronJobService,
                messageBusService,
                chatSessionRepository,
                paths);
    }

    /**
     * Create the target handler for the wakeup dispatcher.
     *
     * <p>Encapsulates the draining of agent_chat messages, session creation, and inference triggering logic.
     *
     * @param result Initialization result
     * @return Wakeup target handler
     */
    public static WakeupDispatcherService.WakeupTarget createWakeupTarget(BootstrapResult result) {
        MessageBusService messageBusService = result.messageBusService();
        AgentService agentService = result.agentService();
        ProviderService providerService = result.providerService();
        ChatService chatService = result.chatService();
        ChatSessionRepository chatSessionRepository = result.chatSessionRepository();
        PluginManager pluginManager = result.pluginManager();
        return new WakeupDispatcherService.WakeupTarget() {

            @Override
            public boolean isSessionRunning(String projectId, String sessionId) {
                if (sessionId != null && chatService.isSessionRunning(sessionId)) {
                    return true;
                }
                // Check if agent has running tasks
                AgentInfo currentAgent = agentService.currentDefault();
                if (currentAgent != null) {
                    AgentRuntimeStatus status = agentService.statusOf(currentAgent.getId());
                    return status.runningTaskCount() > 0;
                }
                return false;
            }

            @Override
            public Mono<Object> runWakeup(
                    String projectId, String sessionId, String agentId, String userId) {
                return Mono.<Object>fromCallable(
                                () -> {
                                    // 1. Check if sessionId is an existing chat session (excluding
                                    // agent-chat internal
                                    // sessions)
                                    if (sessionId != null
                                            && !sessionId.isBlank()
                                            && !sessionId.startsWith("agent-chat:")) {
                                        ChatSessionInfo session =
                                                chatService.findSession(sessionId);
                                        if (session != null) {
                                            LOGGER.log(
                                                    Level.INFO,
                                                    "Waking up existing session: session={0},"
                                                            + " agent={1}, channel={2}",
                                                    new Object[] {
                                                        sessionId,
                                                        session.getAgentId(),
                                                        session.getChannel()
                                                    });
                                            chatService.resumeSessionWakeup(
                                                    session,
                                                    new StreamCallback() {
                                                        @Override
                                                        public void onPart(
                                                                ChatMessagePart part,
                                                                boolean startsNew) {
                                                            String channelId = session.getChannel();
                                                            if (channelId != null
                                                                    && pluginManager != null) {
                                                                PluginRecord plugin =
                                                                        pluginManager.getPlugin(
                                                                                channelId);
                                                                if (plugin != null
                                                                        && plugin.instance != null
                                                                        && plugin.instance
                                                                                .supportsStreaming()) {
                                                                    plugin.instance
                                                                            .streamPartToSession(
                                                                                    sessionId, part,
                                                                                    startsNew);
                                                                }
                                                            }
                                                        }

                                                        @Override
                                                        public void onCompleted(Msg message) {
                                                            String channelId = session.getChannel();
                                                            if (channelId != null
                                                                    && !ChannelIds.CONSOLE
                                                                            .equalsIgnoreCase(
                                                                                    channelId)
                                                                    && pluginManager != null) {
                                                                PluginRecord plugin =
                                                                        pluginManager.getPlugin(
                                                                                channelId);
                                                                if (plugin != null
                                                                        && plugin.instance
                                                                                != null) {
                                                                    String replyText =
                                                                            message != null
                                                                                    ? message
                                                                                            .getTextContent()
                                                                                    : "";
                                                                    LOGGER.log(
                                                                            Level.INFO,
                                                                            "Delivering wakeup"
                                                                                + " reply to"
                                                                                + " channel {0} for"
                                                                                + " session {1}",
                                                                            new Object[] {
                                                                                channelId, sessionId
                                                                            });
                                                                    plugin.instance.replyToSession(
                                                                            sessionId, replyText);
                                                                }
                                                            }
                                                        }
                                                    });
                                            return "wakeup triggered for session " + sessionId;
                                        }
                                    }

                                    // 2. Otherwise handle inter-agent communication (agent_chat)
                                    String effectiveAgentId = agentId;
                                    if ((effectiveAgentId == null || effectiveAgentId.isBlank())
                                            && sessionId != null
                                            && sessionId.startsWith("agent-chat:")) {
                                        String[] parts = sessionId.split(":");
                                        if (parts.length >= 2) {
                                            effectiveAgentId = parts[1];
                                        }
                                    }

                                    if (effectiveAgentId == null || effectiveAgentId.isBlank()) {
                                        LOGGER.log(
                                                Level.WARNING,
                                                "Wakeup call missing agentId and not an existing"
                                                        + " session, sessionId={0}",
                                                sessionId);
                                        return "wakeup skipped: no target";
                                    }

                                    // Drain inbox to get agent_chat request
                                    MessageBus bus = messageBusService.getMessageBus(projectId);
                                    String inboxKey = "agentscope:inbox:agent:" + effectiveAgentId;
                                    List<BusEntry> entries = bus.queueDrain(inboxKey, 1).block();
                                    if (entries == null || entries.isEmpty()) {
                                        LOGGER.log(
                                                Level.FINE,
                                                "Waking up agent={0} but inbox is empty",
                                                effectiveAgentId);
                                        return "wakeup: no pending messages";
                                    }
                                    Map<String, Object> payload = entries.get(0).payload();
                                    String text = str(payload, "text");
                                    String replyTo = str(payload, "replyTo");
                                    String correlationId = str(payload, "correlationId");
                                    if (text == null || text.isBlank()) {
                                        LOGGER.log(
                                                Level.WARNING,
                                                "agent_chat request missing text, agent={0}",
                                                effectiveAgentId);
                                        return "wakeup: empty message";
                                    }
                                    // Register pending reply context, drainAndReplyAgentChat will
                                    // be auto-called after
                                    // sendMessage completes
                                    chatService.registerPendingAgentChatReply(
                                            effectiveAgentId, replyTo, correlationId);
                                    // Create new session and trigger inference
                                    ChatSessionInfo sessionInfo =
                                            chatService.newSession(effectiveAgentId);
                                    AgentInfo agent =
                                            agentService.findById(effectiveAgentId).orElse(null);
                                    if (agent == null) {
                                        LOGGER.log(
                                                Level.WARNING,
                                                "Agent not found: {0}",
                                                effectiveAgentId);
                                        return "wakeup: agent not found";
                                    }
                                    ProviderInfo provider =
                                            providerService
                                                    .getById(agent.getProviderId())
                                                    .orElse(null);
                                    chatService.sendMessage(
                                            agent,
                                            provider,
                                            agent.getModelId(),
                                            sessionInfo,
                                            text,
                                            new StreamCallback() {

                                                @Override
                                                public void onPart(
                                                        ChatMessagePart part, boolean startsNew) {
                                                    // Silent callback: agent-chat doesn't need
                                                    // streaming output to UI
                                                }

                                                @Override
                                                public void onCompleted(Msg message) {
                                                    // drainAndReplyAgentChat is automatically
                                                    // called inside sendMessage
                                                }
                                            });
                                    LOGGER.log(
                                            Level.INFO,
                                            "Wakeup inference triggered: agent={0},"
                                                    + " correlationId={1}",
                                            new Object[] {effectiveAgentId, correlationId});
                                    return "wakeup triggered for agent " + effectiveAgentId;
                                })
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
            }
        };
    }

    /**
     * Gracefully shutdown all initialized services.
     *
     * @param result Initialization result
     */
    public static void shutdown(BootstrapResult result) {
        if (result == null) {
            return;
        }
        LOGGER.info("Closing application, executing cleanup tasks...");
        if (result.cronJobService() != null) {
            result.cronJobService().stop();
        }
        if (result.pluginManager() != null) {
            result.pluginManager().shutdownAll();
        }
        if (result.repository() != null) {
            result.repository().close();
        }
        LOGGER.info("Application stopped safely.");
    }

    /**
     * Safely extract string value from Map.
     *
     * @param map Data map
     * @param key Key name
     * @return String value, returns null if it doesn't exist or is not a string
     */
    static String str(Map<?, ?> map, String key) {
        Object v = map != null ? map.get(key) : null;
        return v != null ? v.toString() : null;
    }
}
