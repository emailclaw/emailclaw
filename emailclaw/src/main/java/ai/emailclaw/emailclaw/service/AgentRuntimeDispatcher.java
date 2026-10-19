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

import ai.emailclaw.emailclaw.model.AcpAgentInfo;
import ai.emailclaw.emailclaw.model.AgentConfiguration;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.SecuritySettings;
import ai.emailclaw.emailclaw.model.SkillInfo;
import ai.emailclaw.emailclaw.service.memory.MemoryRecallMiddleware;
import ai.emailclaw.emailclaw.service.plan.PlanToHintMiddleware;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.ConflictPolicy;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryTarget;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AgentRuntimeDispatcher {

    private static final Logger LOGGER = Logger.getLogger(AgentRuntimeDispatcher.class.getName());

    /**
     * Default Agent system prompt.
     */
    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful and highly capable agentic assistant. You have access to tools."
                + " IMPORTANT: You must act autonomously. NEVER ask for the user's permission to"
                + " use a tool. If you need to check files, execute commands, or perform any"
                + " actions, use the tools IMMEDIATELY without waiting for user confirmation.\n\n"
                + "# Async Background Tool Execution Rules\n"
                + "When a tool execution exceeds the execution timeout, it is automatically"
                + " offloaded to the background, and you will receive a `<system-reminder>` stating"
                + " that the tool is running in the background. The ID in that reminder is an"
                + " internal execution ID, NOT a subagent task ID. NEVER call `wait_async_results`,"
                + " `task_list`, or `task_output` for this reminder. Instead, reply to the user"
                + " informing them that the tool is executing in the background and end your turn"
                + " without calling any tools. The system will automatically wake you up with the"
                + " completed output when it finishes.";

    /**
     * Universal instruction prompt for delivering task deliverables and artifacts via deliver_artifact tool.
     */
    private static final String ARTIFACT_DELIVERY_INSTRUCTION =
            "\n\n"
                + "# Artifact Delivery Rules\n"
                + "When your task produces any deliverable files (such as reports, documents,"
                + " spreadsheets, images, archives, charts, or generated code), you MUST call the"
                + " `deliver_artifact` tool to deliver each file to the user.\n"
                + "- Deliver silently: the tool call itself records and attaches the deliverable"
                + " for the user, so do not mention the delivery or the `deliver_artifact` tool in"
                + " your reply to the user.\n"
                + "- Only deliver final completed outputs; do not deliver temporary working files"
                + " or sensitive credentials.\n"
                + "- STRICTLY FORBIDDEN: never attempt to send emails, execute mutt/sendmail or run"
                + " mail scripts yourself.";

    private final AppContext repository;

    private final ToolService toolService;

    private final SkillService skillService;

    private final ToolRuntimeContext toolRuntimeContext;

    private final GovernanceService governanceService;

    private final RateLimitMiddleware rateLimitMiddleware;

    private final AdaptiveFinalAnswerFilterMiddleware adaptiveFinalAnswerFilterMiddleware;

    private final ToolResultDiffMiddleware toolResultDiffMiddleware;

    private final PlanToHintMiddleware planToHintMiddleware;

    private final MemoryRecallMiddleware memoryRecallMiddleware;

    private final MessageBusService messageBusService;

    private final ChatSessionRepository chatSessionRepository;

    private final ArtifactDeliveryTarget artifactDeliveryTarget;

    public AgentRuntimeDispatcher(
            AppContext repository,
            ToolService toolService,
            SkillService skillService,
            ToolRuntimeContext toolRuntimeContext,
            GovernanceService governanceService,
            RateLimitMiddleware rateLimitMiddleware,
            AdaptiveFinalAnswerFilterMiddleware adaptiveFinalAnswerFilterMiddleware,
            ToolResultDiffMiddleware toolResultDiffMiddleware,
            MessageBusService messageBusService,
            PlanToHintMiddleware planToHintMiddleware,
            MemoryRecallMiddleware memoryRecallMiddleware,
            ChatSessionRepository chatSessionRepository,
            ArtifactDeliveryTarget artifactDeliveryTarget) {
        this.repository = repository;
        this.toolService = toolService;
        this.skillService = skillService;
        this.toolRuntimeContext = toolRuntimeContext;
        this.governanceService = governanceService;
        this.rateLimitMiddleware = rateLimitMiddleware;
        this.adaptiveFinalAnswerFilterMiddleware = adaptiveFinalAnswerFilterMiddleware;
        this.toolResultDiffMiddleware = toolResultDiffMiddleware;
        this.messageBusService = messageBusService;
        this.planToHintMiddleware = planToHintMiddleware;
        this.memoryRecallMiddleware = memoryRecallMiddleware;
        this.chatSessionRepository = chatSessionRepository;
        this.artifactDeliveryTarget = artifactDeliveryTarget;
    }

    /**
     * Build a ReAct agent based on the configuration.
     * The ReAct agent can autonomously think, decide which tools to call to complete the user's request, and maintain memory.
     *
     * @param agent            Agent basic information
     * @param provider         Model provider configuration
     * @param modelId          LLM ID
     * @param config           Agent running configuration (e.g., max iterations, name, etc.)
     * @param channel          Channel identifier
     * @param sessionId        Session ID
     * @return The built ReActAgent instance
     */
    public HarnessAgent buildAgent(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            AgentConfiguration config, //            boolean isEmailclaw,
            String channel,
            String sessionId) {
        LOGGER.log(
                Level.FINE,
                "Building HarnessAgent: provider={0}, model={1}",
                new Object[] {provider.getId(), modelId});
        LOGGER.log(
                Level.INFO,
                "Agent building started: agent={0}, provider={1}, model={2}, channel={3}",
                new Object[] {agent.getId(), provider.getId(), modelId, channel});
        OpenAIChatModel model =
                OpenAIChatModel.builder()
                        .apiKey(ProviderRequestOptions.apiKeyFor(provider))
                        .baseUrl(provider.getBaseUrl())
                        .modelName(modelId)
                        .stream(true)
                        .generateOptions(
                                buildGenerateOptions(provider, selectedModel(provider, modelId)))
                        .nativeStructuredOutputWithTools(
                                provider.isNativeStructuredOutputWithTools())
                        .build();
        String configuredName =
                config.getProfileName() == null ? "" : config.getProfileName().trim();
        if (configuredName.isBlank()) {
            configuredName =
                    (agent.getName() == null || agent.getName().isBlank())
                            ? "Emailclaw"
                            : agent.getName();
        }
        String sysPrompt = DEFAULT_SYSTEM_PROMPT;

        // Must use absolute path, for HarnessAgent's WorkspacePathNormalizer to correctly strip the
        // workspace prefix.
        Path agentWorkspace = repository.workspaceFor(agent.getId()).toAbsolutePath().normalize();

        ai.emailclaw.emailclaw.model.ProjectInfo project =
                resolveSessionProject(sessionId, toolRuntimeContext);
        toolRuntimeContext.activeProject = project;
        toolRuntimeContext.activeSessionId = sessionId;
        Path projectRoot = agentWorkspace;
        boolean projectWritable = true;

        if (project.getBaseDirectory() != null && !project.getBaseDirectory().isBlank()) {
            Path baseDir =
                    Path.of(FileNameUtils.expandUserHome(project.getBaseDirectory()))
                            .toAbsolutePath()
                            .normalize();
            if (Files.isDirectory(baseDir)) {
                projectRoot = baseDir;
                LOGGER.log(Level.INFO, "Mapped project directory to: {0}", projectRoot);
            }
        }

        ai.emailclaw.emailclaw.model.ChatSessionInfo currentSession = null;
        if (this.repository != null && sessionId != null) {
            currentSession =
                    this.repository.loadSessions().stream()
                            .filter(s -> sessionId.equals(s.getId()))
                            .findFirst()
                            .orElse(null);
        }
        String userId =
                (currentSession != null && currentSession.getUserId() != null)
                        ? currentSession.getUserId()
                        : "";
        String projectIdStr = project.getId();
        String projectNameStr = project.getName();
        String osUser = System.getProperty("user.name", "unknown");
        String userHome =
                this.repository != null && this.repository.paths() != null
                        ? this.repository.paths().root.toString()
                        : System.getProperty("user.home", "");

        sysPrompt +=
                String.format(
                        "\n\n"
                                + "# Context Information\n"
                                + "- Current Channel: %s\n"
                                + "- Current User ID: %s\n"
                                + "- Current Session ID: %s\n"
                                + "- Operating System User: %s\n"
                                + "- User Home Directory (~): %s (Tilde ~ expands to this"
                                + " directory, NOT /root)\n"
                                + "- Current Project ID: %s\n"
                                + "- Current Project Name: %s\n"
                                + "- Project Base Directory: %s (Working directory for all project"
                                + " files, generated documents, code, and attachments)\n"
                                + "- Agent Workspace Directory: %s (Internal storage for agent"
                                + " memory and configuration only)\n",
                        channel == null ? "unknown" : channel,
                        userId,
                        sessionId == null ? "unknown" : sessionId,
                        osUser,
                        userHome,
                        projectIdStr,
                        projectNameStr,
                        projectRoot,
                        agentWorkspace);

        sysPrompt += ARTIFACT_DELIVERY_INSTRUCTION;
        Toolkit toolkit = toolService.buildToolkit(toolRuntimeContext);
        // Load skills and append to sysPrompt
        List<SkillInfo> enabledSkills =
                skillService.listWorkspaceSkills(agent.getId()).stream()
                        .filter(skill -> skill.enabled())
                        .filter(skill -> skillAppliesToChannel(skill, channel))
                        .toList();
        LOGGER.log(
                Level.INFO,
                "Skill call started: agent={0}, channel={1}, enabledSkillCount={2}",
                new Object[] {agent.getId(), channel, enabledSkills.size()});
        if (!enabledSkills.isEmpty()) {
            StringBuilder skillsPrompt = new StringBuilder();
            skillsPrompt.append("\n\n## Available Skills\n\n");
            for (SkillInfo skill : enabledSkills) {
                try {
                    skillsPrompt.append("### Skill: ").append(skill.name()).append("\n");
                    String content = skill.content();
                    if (content != null && repository != null && repository.paths() != null) {
                        String rootPath =
                                repository.paths().root.toAbsolutePath().normalize().toString();
                        content = content.replace("~/emailclaw", rootPath);
                    }
                    skillsPrompt.append(content).append("\n\n");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to read skill: " + skill.name(), e);
                }
            }
            sysPrompt = sysPrompt + skillsPrompt.toString();
        }

        java.util.List<Path> additionalRoots = new java.util.ArrayList<>();
        String sysUserHome = System.getProperty("user.home", "");
        if (sysUserHome != null && !sysUserHome.isBlank()) {
            Path homePath = Path.of(sysUserHome).toAbsolutePath().normalize();
            if (Files.isDirectory(homePath) && !additionalRoots.contains(homePath)) {
                additionalRoots.add(homePath);
            }
        }
        if (repository != null && repository.paths() != null) {
            if (repository.paths().root != null) {
                Path appRoot = repository.paths().root.toAbsolutePath().normalize();
                if (!additionalRoots.contains(appRoot)) {
                    additionalRoots.add(appRoot);
                }
            }
            if (repository.paths().configDir != null) {
                Path configDir = repository.paths().configDir.toAbsolutePath().normalize();
                if (!additionalRoots.contains(configDir)) {
                    additionalRoots.add(configDir);
                }
            }
            if (repository.paths().workspaceRoot != null) {
                Path wsRoot = repository.paths().workspaceRoot.toAbsolutePath().normalize();
                if (!additionalRoots.contains(wsRoot)) {
                    additionalRoots.add(wsRoot);
                }
            }
            if (repository.paths().skillsPoolRoot != null) {
                Path skillsPool = repository.paths().skillsPoolRoot.toAbsolutePath().normalize();
                if (!additionalRoots.contains(skillsPool)) {
                    additionalRoots.add(skillsPool);
                }
            }
            if (repository.paths().projectsRoot != null) {
                Path projectsRoot = repository.paths().projectsRoot.toAbsolutePath().normalize();
                if (!additionalRoots.contains(projectsRoot)) {
                    additionalRoots.add(projectsRoot);
                }
            }
        }
        if (project.getAdditionalDirs() != null) {
            for (String dir : project.getAdditionalDirs().keySet()) {
                if (dir != null && !dir.isBlank()) {
                    Path p =
                            Path.of(FileNameUtils.expandUserHome(dir)).toAbsolutePath().normalize();
                    if (Files.isDirectory(p) && !additionalRoots.contains(p)) {
                        additionalRoots.add(p);
                    }
                }
            }
        }

        // LocalFilesystemSpec defaults to ROOTED mode, only allowing access to project + workspace
        // + additionalRoots.
        // If project is not explicitly set, it falls back to the JVM startup directory (user.dir),
        // causing the workspace absolute path passed by the agent
        // to fail PathPolicy validation, making tools like list_files incorrectly judge the
        // directory as non-existent or empty.
        LocalFilesystemSpec filesystemSpec =
                new LocalFilesystemSpec()
                        .mode(LocalFsMode.ROOTED)
                        .project(projectRoot)
                        .additionalRoots(additionalRoots)
                        .projectWritable(projectWritable)
                        .sharedLocalWorkspace(true)
                        .isolationScope(IsolationScope.GLOBAL);

        LOGGER.log(
                Level.INFO,
                "Filesystem configuration started: mode=ROOTED, workspace={0}, project={1},"
                        + " projectWritable={2}",
                new Object[] {agentWorkspace, projectRoot, projectWritable});

        // Build PermissionContextState based on execution_level
        PermissionContextState permissionContext = buildPermissionContext(config, agent.getId());
        syncSessionPermissionContext(sessionId, permissionContext);

        ExecutionConfig toolExecConfig =
                ExecutionConfig.builder()
                        .timeout(
                                Duration.ofSeconds(
                                        Math.max(
                                                config.effectiveTaskExecutionTimeoutSeconds(),
                                                1800)))
                        .maxAttempts(1)
                        .build();

        HarnessAgent.Builder builder =
                HarnessAgent.builder()
                        .name(configuredName)
                        .agentId(agent.getId())
                        .sysPrompt(sysPrompt)
                        .model(model)
                        .toolkit(toolkit)
                        .toolExecutionConfig(toolExecConfig)
                        .stateStore(repository.agentStateStore())
                        .workspace(agentWorkspace)
                        .filesystem(filesystemSpec)
                        .artifactDeliveryTarget(artifactDeliveryTarget)
                        .messageBus(messageBusService.getMessageBus(project.getId()))
                        .asyncToolTimeout(
                                Duration.ofSeconds(Math.max(120, config.getShellCommandTimeout())))
                        .asyncToolRegistry(messageBusService.getAsyncToolRegistry(project.getId()))
                        .disableWebTools()
                        .compaction(CompactionConfig.builder().build())
                        // Control Agent execution phase (read-only gate + plan_enter/write/exit
                        // tools)
                        .enablePlanMode()
                        .enableSkillManageTool(true)
                        .enableAgentTracingLog(true)
                        .enablePendingToolRecovery(true)
                        .maxIters(Math.max(1, config.getMaxIterations()))
                        .permissionContext(permissionContext)
                        .middleware(rateLimitMiddleware)
                        .middleware(adaptiveFinalAnswerFilterMiddleware)
                        .middleware(toolResultDiffMiddleware)
                        .middleware(planToHintMiddleware)
                        .middleware(memoryRecallMiddleware);

        if (agent.getFallbackModelId() != null && !agent.getFallbackModelId().isBlank()) {
            ProviderInfo fallbackProvider = provider;
            if (agent.getFallbackProviderId() != null && !agent.getFallbackProviderId().isBlank()) {
                fallbackProvider =
                        repository.loadProviders().stream()
                                .filter(p -> p.getId().equals(agent.getFallbackProviderId()))
                                .findFirst()
                                .orElse(provider);
            }
            OpenAIChatModel fallbackModel =
                    OpenAIChatModel.builder()
                            .apiKey(ProviderRequestOptions.apiKeyFor(fallbackProvider))
                            .baseUrl(fallbackProvider.getBaseUrl())
                            .modelName(agent.getFallbackModelId())
                            .stream(true)
                            .generateOptions(
                                    buildGenerateOptions(
                                            fallbackProvider,
                                            selectedModel(
                                                    fallbackProvider, agent.getFallbackModelId())))
                            .nativeStructuredOutputWithTools(
                                    fallbackProvider.isNativeStructuredOutputWithTools())
                            .build();
            builder.fallbackModel(fallbackModel);
            builder.failoverListener(
                    (primaryModel, error) -> {
                        LOGGER.log(
                                Level.WARNING,
                                "Model failover triggered for agent {0}: switching from {1} to {2}"
                                        + " due to: {3}",
                                new Object[] {
                                    agent.getId(),
                                    primaryModel != null ? primaryModel.getModelName() : "primary",
                                    agent.getFallbackModelId(),
                                    error != null ? error.getMessage() : "unknown"
                                });
                    });
        }
        // Register subagent declarations (read from ACP agent configuration)
        List<AcpAgentInfo> acpAgents = repository.configManager().getAcpAgents();
        for (AcpAgentInfo a : acpAgents) {
            if (a.isEnabled()) {
                String desc =
                        "ACP agent: "
                                + a.getCommand()
                                + " "
                                + (a.getArgs() == null ? "" : a.getArgs());
                builder.subagent(
                        SubagentDeclaration.builder()
                                .name(a.getKey())
                                .description(desc.trim())
                                .workspaceMode(WorkspaceMode.SHARED)
                                .persistSession(true)
                                .inheritParentPermissions(true)
                                .mode(SubagentDeclaration.Mode.SUBAGENT)
                                .build());
            }
        }
        try {
            Field innerField = HarnessAgent.Builder.class.getDeclaredField("inner");
            innerField.setAccessible(true);
            ReActAgent.Builder innerBuilder = (ReActAgent.Builder) innerField.get(builder);
            innerBuilder.conflictPolicy(ConflictPolicy.APPEND_MERGE);
            LOGGER.info("Configured ReActAgent ConflictPolicy to APPEND_MERGE");
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to reflectively configure ConflictPolicy.APPEND_MERGE on HarnessAgent"
                            + " inner builder",
                    e);
        }
        HarnessAgent harnessAgent = builder.build();
        io.agentscope.core.tool.Toolkit agentToolkit = harnessAgent.getToolkit();
        if (agentToolkit != null) {
            toolService.reconcileAgentToolkit(agentToolkit, toolkit, toolRuntimeContext);
        }
        return harnessAgent;
    }

    private GenerateOptions buildGenerateOptions(ProviderInfo provider, ModelInfo model) {
        Map<String, String> headers = ProviderRequestOptions.headersFor(provider);
        Map<String, Object> bodyParams = ProviderRequestOptions.bodyParamsFor(provider, model);
        GenerateOptions.Builder builder = GenerateOptions.builder();
        if (!headers.isEmpty()) {
            builder.additionalHeaders(headers);
        }
        if (!bodyParams.isEmpty()) {
            builder.additionalBodyParams(bodyParams);
        }
        return builder.build();
    }

    private ModelInfo selectedModel(ProviderInfo provider, String modelId) {
        if (provider == null || modelId == null) {
            return null;
        }
        return provider.allModels().stream()
                .filter(item -> modelId.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolve the project bound to the given task session via session.projectId; fall back to
     * the global current project when the session has no binding or the project no longer
     * exists.
     */
    private ai.emailclaw.emailclaw.model.ProjectInfo resolveSessionProject(
            String sessionId, ToolRuntimeContext context) {
        ai.emailclaw.emailclaw.model.ProjectInfo fallback = context.currentProject();
        if (sessionId == null || sessionId.isBlank()) {
            return fallback;
        }
        String boundProjectId =
                repository.loadSessions().stream()
                        .filter(s -> sessionId.equals(s.getId()))
                        .findFirst()
                        .map(ChatSessionInfo::getProjectId)
                        .orElse(null);
        if (boundProjectId == null || boundProjectId.isBlank()) {
            return fallback;
        }
        ai.emailclaw.emailclaw.model.ProjectInfo found =
                context.projectService.findById(boundProjectId);
        if (found == ai.emailclaw.emailclaw.service.ProjectService.PROJECT_DEFAULT
                && !ai.emailclaw.emailclaw.service.ProjectService.PROJECT_ID_DEFAULT.equals(
                        boundProjectId)) {
            return fallback;
        }
        return found;
    }

    private boolean skillAppliesToChannel(SkillInfo skill, String channel) {
        if (skill.channels() == null
                || skill.channels().isEmpty()
                || skill.channels().contains("all")) {
            return true;
        }
        return channel == null || channel.isBlank() || skill.channels().contains(channel);
    }

    public PermissionContextState buildPermissionContext(
            AgentConfiguration config, String agentId) {
        PermissionContextState.Builder builder = PermissionContextState.builder();
        // Get PermissionMode
        String modeStr = governanceService.getPermissionMode(agentId);
        PermissionMode mode = parsePermissionMode(modeStr);
        builder.mode(mode);
        LOGGER.log(
                Level.INFO,
                "Building PermissionContext: agent={0}, mode={1}",
                new Object[] {agentId, mode});
        // Decide whether to add rules based on mode
        switch (mode) {
            case BYPASS -> {
                // Allow all tools directly, do not add any rules
                LOGGER.log(Level.INFO, "PermissionContext: BYPASS mode");
            }
            case DEFAULT -> {
                // Add askRules for guarded_tools
                SecuritySettings settings = repository.configManager().getSecuritySettings();
                List<String> guardedTools =
                        settings != null && settings.getToolGuard() != null
                                ? settings.getToolGuard().getGuardedTools()
                                : List.of();
                if (guardedTools.isEmpty()) {
                    guardedTools = defaultDangerousTools();
                }
                for (String toolName : guardedTools) {
                    builder.addAskRule(
                            toolName,
                            new PermissionRule(
                                    toolName,
                                    null,
                                    PermissionBehavior.ASK,
                                    "governance:guarded_tools"));
                }
                LOGGER.log(
                        Level.INFO,
                        "PermissionContext: {0} mode, guarded_tools={1}",
                        new Object[] {mode, guardedTools.size()});
            }
            case ACCEPT_EDITS, EXPLORE, DONT_ASK -> {
                // PermissionEngine automatically handles read-only/write/dont_ask logic, no extra
                // rules needed
                LOGGER.log(Level.INFO, "PermissionContext: {0} mode (auto handled)", mode);
            }
        }
        return builder.build();
    }

    private void syncSessionPermissionContext(
            String sessionId, PermissionContextState permissionContext) {
        if (sessionId == null
                || sessionId.isBlank()
                || permissionContext == null
                || repository == null
                || repository.agentStateStore() == null) {
            return;
        }
        try {
            var stateStore = repository.agentStateStore();
            var existingOpt =
                    stateStore.get(
                            null,
                            sessionId,
                            "agent_state",
                            io.agentscope.core.state.AgentState.class);
            if (existingOpt.isPresent()) {
                io.agentscope.core.state.AgentState state = existingOpt.get();
                PermissionContextState currentCtx = state.getPermissionContext();
                PermissionContextState.Builder merged =
                        PermissionContextState.builder().mode(permissionContext.getMode());
                permissionContext.getWorkingDirectories().forEach(merged::addWorkingDirectory);
                permissionContext
                        .getAllowRules()
                        .forEach(
                                (tool, rules) -> {
                                    if (rules != null) {
                                        rules.forEach(r -> merged.addAllowRule(tool, r));
                                    }
                                });
                if (currentCtx != null && currentCtx.getAllowRules() != null) {
                    currentCtx
                            .getAllowRules()
                            .forEach(
                                    (tool, rules) -> {
                                        if (rules != null) {
                                            rules.forEach(r -> merged.addAllowRule(tool, r));
                                        }
                                    });
                }
                permissionContext
                        .getDenyRules()
                        .forEach(
                                (tool, rules) -> {
                                    if (rules != null) {
                                        rules.forEach(r -> merged.addDenyRule(tool, r));
                                    }
                                });
                permissionContext
                        .getAskRules()
                        .forEach(
                                (tool, rules) -> {
                                    if (rules != null) {
                                        rules.forEach(r -> merged.addAskRule(tool, r));
                                    }
                                });
                state.setPermissionContext(merged.build());
                stateStore.save(null, sessionId, "agent_state", state);
                LOGGER.log(
                        Level.INFO,
                        "Synced PermissionContext into persisted AgentState: session={0}, mode={1}",
                        new Object[] {sessionId, permissionContext.getMode()});
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to sync PermissionContext into persisted AgentState for session="
                            + sessionId,
                    e);
        }
    }

    public boolean selectedModelSupportsImage(ProviderInfo provider, String modelId) {
        if (provider == null || modelId == null) {
            return false;
        }
        return provider.allModels().stream()
                .filter(model -> modelId.equals(model.getId()))
                .findFirst()
                .map(model -> model.isSupportsImage())
                .orElse(false);
    }

    public boolean selectedModelSupportsVideo(ProviderInfo provider, String modelId) {
        if (provider == null || modelId == null) {
            return false;
        }
        return provider.allModels().stream()
                .filter(model -> modelId.equals(model.getId()))
                .findFirst()
                .map(model -> model.isSupportsVideo())
                .orElse(false);
    }

    /**
     * Parse PermissionMode string.
     */
    private PermissionMode parsePermissionMode(String value) {
        if (value == null || value.isBlank()) {
            return PermissionMode.DEFAULT;
        }
        try {
            return PermissionMode.fromString(value.toLowerCase().trim());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Unknown PermissionMode: {0}, fallback to DEFAULT", value);
            return PermissionMode.DEFAULT;
        }
    }

    /**
     * Return default dangerous tools list.
     */
    private List<String> defaultDangerousTools() {
        return List.of(
                "shell",
                "execute",
                "run_command",
                "execute_shell_command",
                "bash",
                "sh",
                "cmd",
                "terminal",
                "file_write",
                "write_file",
                "create_file",
                "file_read",
                "read_file",
                "edit_file",
                "file_delete",
                "delete_file",
                "http_request",
                "url_fetch",
                "curl",
                "web_fetch",
                "env_set",
                "set_env");
    }
}
