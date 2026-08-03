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

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentRuntimeStatus;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.plugin.DefaultPluginContext;
import ai.emailclaw.emailclaw.plugin.PluginContext;
import ai.emailclaw.emailclaw.plugin.PluginManager;
import ai.emailclaw.emailclaw.plugin.PluginRegistry;
import ai.emailclaw.emailclaw.service.AcpService;
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
import ai.emailclaw.emailclaw.storage.AppPaths;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import io.agentscope.harness.agent.middleware.AsyncToolMiddleware;
import io.agentscope.harness.agent.middleware.InboxMiddleware;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Mono;

/**
 * Application Bootstrap: Encapsulates the initialization logic shared by App and ServiceApp. Adheres to Pure DI pattern/principle: ApplicationBootstrap acts as the sole Composition Root.
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
        Path appHome = AppPaths.resolveHome();
        LOGGER.log(Level.INFO, "Detected application working directory: {0}", appHome);
        // 2. Initialize persistence layer
        AppPaths paths = new AppPaths(appHome);
        AppContext repository = new AppContext(paths);
        // Ensure directories exist before creating services, preventing WatchService registration
        // failure
        repository.ensureStructure();
        // Ensure skills pool files are extracted/ready, so later services (like AgentService ->
        // ConfigManager) can read the full list of skill names
        new BootstrapService(repository, null).preInitializeSkillsPool();
        // 3. Instantiate core service layer objects, and perform dependency injection (DI)
        LOGGER.fine("Loading service layer components...");
        ProviderService providerService = new ProviderService(repository);
        AgentService agentService = new AgentService(repository);
        ProjectService projectService = new ProjectService(repository);
        // Message bus and sub-agent registry (prerequisite dependencies for ToolRuntimeContext)
        MessageBusService messageBusService =
                new MessageBusService(repository.paths().workspaceRoot);
        SpawnRegistryService spawnRegistryService =
                new SpawnRegistryService(repository.paths().workspaceRoot);
        // Build tool runtime context, allowing tools to access system services and currently
        // selected agent
        ToolRuntimeContext toolRuntimeContext =
                new ToolRuntimeContext(
                        repository,
                        agentService,
                        providerService,
                        messageBusService,
                        spawnRegistryService,
                        projectService);
        ToolService toolService = new ToolService(repository);
        SkillService skillService = new SkillService(repository);
        GovernanceService governanceService = new GovernanceService(repository);
        RateLimitMiddleware rateLimitMiddleware = new RateLimitMiddleware(Duration.ofMillis(1000));
        AsyncToolMiddleware asyncToolMiddleware =
                new AsyncToolMiddleware(
                        messageBusService.getMessageBus(),
                        Duration.ofSeconds(30),
                        messageBusService.getAsyncToolRegistry());
        InboxMiddleware inboxMiddleware =
                new InboxMiddleware(
                        messageBusService.getMessageBus(),
                        100,
                        messageBusService.getAsyncToolRegistry(),
                        Duration.ofMinutes(10));
        PlanStore planStore = new JsonFilePlanStore(repository.paths().workspaceRoot);
        PlanHintCache planHintCache = new PlanHintCache();
        PlanBroadcaster planBroadcaster = new PlanBroadcaster(messageBusService);
        PlanService planService = new PlanService(planStore, planBroadcaster, planHintCache);
        PlanToHintMiddleware planToHintMiddleware =
                new PlanToHintMiddleware(planService, planHintCache);
        MemoryService memoryService = new MemoryService(repository.paths().workspaceRoot);
        MemoryRecallMiddleware memoryRecallMiddleware = new MemoryRecallMiddleware(memoryService);
        MemoAutoSync memoAutoSync = new MemoAutoSync(repository.paths().workspaceRoot);
        List<String> allAgentIds = agentService.list().stream().map(a -> a.getId()).toList();
        ProactiveMemoryTrigger proactiveTrigger =
                new ProactiveMemoryTrigger(memoryService, messageBusService);
        proactiveTrigger.start(allAgentIds);
        ChannelMessageBusIntegration channelMessageBusIntegration =
                new ChannelMessageBusIntegration(messageBusService);
        SessionTitleGenerator titleGenerator = new SessionTitleGenerator(repository);
        ChatSessionRepository chatSessionRepository =
                new ChatSessionRepository(repository.paths().workspaceRoot);
        AgentRuntimeDispatcher agentRuntimeDispatcher =
                new AgentRuntimeDispatcher(
                        repository,
                        toolService,
                        skillService,
                        toolRuntimeContext,
                        governanceService,
                        rateLimitMiddleware,
                        asyncToolMiddleware,
                        inboxMiddleware,
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
        PluginRegistry pluginRegistry = new PluginRegistry();
        ChannelService channelService = new ChannelService(repository, pluginRegistry);
        McpService mcpService = new McpService(repository);
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
                        repository.configManager());
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
        return new WakeupDispatcherService.WakeupTarget() {

            @Override
            public boolean isSessionRunning(String sessionId) {
                // Check if agent has running tasks
                AgentInfo currentAgent = agentService.currentDefault();
                if (currentAgent != null) {
                    AgentRuntimeStatus status = agentService.statusOf(currentAgent.getId());
                    return status.runningTaskCount() > 0;
                }
                return false;
            }

            @Override
            public Mono<Object> runWakeup(String sessionId, String agentId) {
                if (agentId == null || agentId.isBlank()) {
                    LOGGER.log(
                            Level.WARNING, "Wakeup call missing agentId, sessionId={0}", sessionId);
                    return Mono.just("wakeup skipped: no agentId");
                }
                // Drain inbox to get agent_chat request
                MessageBus bus = messageBusService.getMessageBus();
                String inboxKey = "agentscope:inbox:agent:" + agentId;
                List<BusEntry> entries = bus.queueDrain(inboxKey, 1).block();
                if (entries == null || entries.isEmpty()) {
                    LOGGER.log(Level.FINE, "Waking up agent={0} but inbox is empty", agentId);
                    return Mono.just("wakeup: no pending messages");
                }
                Map<String, Object> payload = entries.get(0).payload();
                String text = str(payload, "text");
                String replyTo = str(payload, "replyTo");
                String correlationId = str(payload, "correlationId");
                if (text == null || text.isBlank()) {
                    LOGGER.log(
                            Level.WARNING, "agent_chat request missing text, agent={0}", agentId);
                    return Mono.just("wakeup: empty message");
                }
                // Register pending reply context, drainAndReplyAgentChat will be auto-called after
                // sendMessage completes
                chatService.registerPendingAgentChatReply(agentId, replyTo, correlationId);
                // Create new session and trigger inference
                ChatSessionInfo sessionInfo = chatService.newSession(agentId);
                AgentInfo agent = agentService.findById(agentId).orElse(null);
                if (agent == null) {
                    LOGGER.log(Level.WARNING, "Agent not found: {0}", agentId);
                    return Mono.just("wakeup: agent not found");
                }
                ProviderInfo provider = providerService.getById(agent.getProviderId()).orElse(null);
                chatService.sendMessage(
                        agent,
                        provider,
                        agent.getModelId(),
                        sessionInfo,
                        text,
                        new StreamCallback() {

                            @Override
                            public void onPart(ChatMessagePart part, boolean startsNew) {
                                // Silent callback: agent-chat doesn't need streaming output to UI
                            }

                            @Override
                            public void onCompleted(Msg message) {
                                // drainAndReplyAgentChat is automatically called inside sendMessage
                            }
                        });
                LOGGER.log(
                        Level.INFO,
                        "Wakeup inference triggered: agent={0}, correlationId={1}",
                        new Object[] {agentId, correlationId});
                return Mono.just("wakeup triggered for agent " + agentId);
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
