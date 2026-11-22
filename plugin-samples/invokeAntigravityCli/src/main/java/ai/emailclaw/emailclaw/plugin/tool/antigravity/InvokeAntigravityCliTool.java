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
package ai.emailclaw.emailclaw.plugin.tool.antigravity;

import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.plugin.PluginContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Execution tool for Google Antigravity CLI.
 *
 * <p>Exposes the {@code invokeAntigravityCli} tool to AgentScope Java agents, allowing autonomous
 * agents to invoke the Antigravity CLI in non-interactive headless print mode ({@code -p}/{@code --print})
 * and receive structured JSON output wrapped in a {@link ToolResultBlock}.
 */
public class InvokeAntigravityCliTool {

    private static final Logger LOGGER = Logger.getLogger(InvokeAntigravityCliTool.class.getName());

    public static final String DEFAULT_CLI_PATH = "agy";
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final Set<String> ALLOWED_EFFORTS =
            Set.of("low", "medium", "high", "xhigh", "max");

    private volatile PluginContext context;
    private final AntigravityProcessRunner processRunner;
    private final String defaultCliPath;
    private final int defaultTimeoutSeconds;
    private final boolean defaultDangerouslySkipPermissions;

    /**
     * Constructs the tool with the framework plugin context, default process runner, and standard settings.
     *
     * @param context Framework plugin context
     */
    public InvokeAntigravityCliTool(PluginContext context) {
        this(
                context,
                new DefaultAntigravityProcessRunner(),
                DEFAULT_CLI_PATH,
                DEFAULT_TIMEOUT_SECONDS,
                true);
    }

    /**
     * Dependency-injected constructor with custom path resolver adhering to Pure DI.
     *
     * @param context Framework plugin context
     * @param pathResolver Strategy for locating Antigravity CLI executable
     */
    public InvokeAntigravityCliTool(PluginContext context, AntigravityPathResolver pathResolver) {
        this(
                context,
                new DefaultAntigravityProcessRunner(pathResolver),
                DEFAULT_CLI_PATH,
                DEFAULT_TIMEOUT_SECONDS,
                true);
    }

    /**
     * Full dependency-injected constructor adhering to the Pure DI pattern.
     *
     * @param context Framework plugin context
     * @param processRunner Strategy runner for executing the CLI process
     * @param defaultCliPath Default executable binary path (defaults to 'agy')
     * @param defaultTimeoutSeconds Default timeout in seconds (defaults to 300)
     * @param defaultDangerouslySkipPermissions Whether to skip permission confirmation by default
     */
    public InvokeAntigravityCliTool(
            PluginContext context,
            AntigravityProcessRunner processRunner,
            String defaultCliPath,
            int defaultTimeoutSeconds,
            boolean defaultDangerouslySkipPermissions) {
        this.context = context;
        this.processRunner =
                processRunner != null ? processRunner : new DefaultAntigravityProcessRunner();
        this.defaultCliPath =
                defaultCliPath != null && !defaultCliPath.isBlank()
                        ? defaultCliPath
                        : DEFAULT_CLI_PATH;
        this.defaultTimeoutSeconds =
                defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.defaultDangerouslySkipPermissions = defaultDangerouslySkipPermissions;
        LOGGER.fine("InvokeAntigravityCliTool initialized");
    }

    /**
     * Sets or updates the plugin context.
     *
     * @param context Framework plugin context
     */
    public void setContext(PluginContext context) {
        this.context = context;
    }

    /**
     * Executes the Google Antigravity CLI in headless print mode and returns a structured {@link ToolResultBlock}.
     *
     * @param prompt The task description or prompt to send to Antigravity CLI
     * @param workingDirectory Optional working directory where the CLI should execute
     * @param model Optional model for the current CLI session ({@code --model})
     * @param effort Optional reasoning effort for the current CLI session ({@code --effort}:
     *     low|medium|high|xhigh|max)
     * @param timeoutSeconds Optional execution timeout in seconds (defaults to 300)
     * @param dangerouslySkipPermissions Whether to automatically pass --dangerously-skip-permissions
     * @param extraArgs Optional additional command-line arguments to pass to the agy binary
     * @param continueLastSession Whether to continue the last session (-c / --continue). Defaults
     *     to true when {@code conversation} is omitted. Set to false (and omit {@code
     *     conversation}) to start a new session.
     * @param conversation Optional previous agy session/conversation ID to pass via {@code
     *     --conversation} (Resume a previous conversation by ID). Takes precedence over {@code
     *     -c}/{@code --continue}.
     * @return Structured {@link ToolResultBlock} containing output text, execution state, and
     *     metadata
     */
    @Tool(
            name = "invokeAntigravityCli",
            description =
                    "Invoke Google Antigravity CLI (agy) in headless print mode (-p/--print"
                            + " --output-format json) to execute tasks, run tests, analyze code,"
                            + " or generate artifacts, returning structured JSON results (including"
                            + " conversation_id). Supports optional --model (Model for the current"
                            + " CLI session) and --effort (Reasoning effort: low|medium|high|xhigh|"
                            + "max). Session selection rules: (1) If the user asks to start a new"
                            + " session when calling agy, do NOT pass -c, --continue, or"
                            + " --conversation (set continue_last_session=false and omit"
                            + " conversation). (2) If the user does NOT ask for a new session,"
                            + " first check the conversation history for any session/conversation"
                            + " ID returned by agy; if found, pass it via the conversation"
                            + " parameter (--conversation: Resume a previous conversation by ID)."
                            + " (3) If no historical agy session ID exists, use"
                            + " continue_last_session=true (adds -c, equivalent to --continue).")
    public ToolResultBlock invokeAntigravityCli(
            @ToolParam(
                            name = "prompt",
                            description =
                                    "The task description or prompt to send to Antigravity CLI.")
                    String prompt,
            @ToolParam(
                            name = "cli_path",
                            description =
                                    "Optional absolute path to the agy executable. If omitted,"
                                            + " uses the default 'agy'.",
                            required = false)
                    String cliPath,
            @ToolParam(
                            name = "working_directory",
                            description =
                                    "Optional working directory where the CLI should execute. If"
                                            + " omitted, the current workspace/project directory is"
                                            + " used.",
                            required = false)
                    String workingDirectory,
            @ToolParam(
                            name = "model",
                            description =
                                    "Optional model for the current CLI session to pass via --model"
                                            + " (e.g. 'gemini-2.5-pro'). Omit or leave empty to use"
                                            + " the agy default model.",
                            required = false)
                    String model,
            @ToolParam(
                            name = "effort",
                            description =
                                    "Optional reasoning effort for the current CLI session to pass"
                                            + " via --effort (low|medium|high|xhigh|max). Omit or"
                                            + " leave empty to use the agy default effort.",
                            required = false)
                    String effort,
            @ToolParam(
                            name = "timeout_seconds",
                            description =
                                    "Optional execution timeout in seconds (default is 300"
                                            + " seconds).",
                            required = false)
                    Integer timeoutSeconds,
            @ToolParam(
                            name = "dangerously_skip_permissions",
                            description =
                                    "Whether to automatically pass --dangerously-skip-permissions"
                                            + " for non-interactive execution (default is true).",
                            required = false)
                    Boolean dangerouslySkipPermissions,
            @ToolParam(
                            name = "extra_args",
                            description =
                                    "Optional additional command-line arguments to pass to the agy"
                                            + " binary. Do not include -c, --continue, or"
                                            + " --conversation when starting a new session.",
                            required = false)
                    String extraArgs,
            @ToolParam(
                            name = "continue_last_session",
                            description =
                                    "Whether to continue the most recent agy conversation (-c /"
                                            + " --continue). Defaults to true when 'conversation'"
                                            + " is omitted. Set to false (and omit 'conversation')"
                                            + " when the user requests a new session so neither"
                                            + " -c, --continue, nor --conversation is passed."
                                            + " Ignored when 'conversation' is provided.",
                            required = false)
                    Boolean continueLastSession,
            @ToolParam(
                            name = "conversation",
                            description =
                                    "Optional previous agy session/conversation ID to pass via"
                                            + " --conversation (Resume a previous conversation by"
                                            + " ID). When the user does not request a new session"
                                            + " and a session/conversation ID from agy exists in"
                                            + " history, pass that ID here (takes precedence over"
                                            + " -c/--continue). Omit when starting a new session.",
                            required = false)
                    String conversation) {
        String normalizedModel = (model != null && !model.isBlank()) ? model.trim() : null;
        String normalizedEffort =
                (effort != null && !effort.isBlank())
                        ? effort.trim().toLowerCase(Locale.ROOT)
                        : null;
        String normalizedConversation =
                (conversation != null && !conversation.isBlank()) ? conversation.trim() : null;
        boolean effectiveContinue =
                normalizedConversation == null
                        && (continueLastSession == null || continueLastSession);
        LOGGER.log(
                Level.INFO,
                "InvokeAntigravityCli tool call initiated: promptLength={0}, workingDir={1},"
                        + " model={2}, effort={3}, continueLastSession={4}, conversation={5}",
                new Object[] {
                    prompt != null ? prompt.length() : 0,
                    workingDirectory,
                    normalizedModel,
                    normalizedEffort,
                    effectiveContinue,
                    normalizedConversation
                });

        if (prompt == null || prompt.isBlank()) {
            LOGGER.warning("InvokeAntigravityCli tool call rejected: prompt is empty");
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            AntigravityJsonUtils.createErrorJson(
                                                    "Prompt cannot be empty or blank.", -1))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(Map.of("exitCode", -1, "error", "Prompt cannot be empty or blank."))
                    .build();
        }

        if (normalizedEffort != null && !ALLOWED_EFFORTS.contains(normalizedEffort)) {
            String errorMsg =
                    "Invalid effort value '"
                            + effort
                            + "'. Allowed values are: low, medium, high, xhigh, max.";
            LOGGER.log(Level.WARNING, "InvokeAntigravityCli tool call rejected: {0}", errorMsg);
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(AntigravityJsonUtils.createErrorJson(errorMsg, -1))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(Map.of("exitCode", -1, "error", errorMsg))
                    .build();
        }

        Path targetWorkingDir = resolveWorkingDirectory(workingDirectory);
        int effectiveTimeout =
                (timeoutSeconds != null && timeoutSeconds > 0)
                        ? timeoutSeconds
                        : defaultTimeoutSeconds;
        boolean skipPermissions =
                (dangerouslySkipPermissions != null)
                        ? dangerouslySkipPermissions
                        : defaultDangerouslySkipPermissions;

        LOGGER.log(
                Level.INFO,
                "Launching Antigravity CLI tool execution: workingDir={0}, timeout={1}s,"
                        + " model={2}, effort={3}, skipPermissions={4}, continueLastSession={5},"
                        + " conversation={6}",
                new Object[] {
                    targetWorkingDir,
                    effectiveTimeout,
                    normalizedModel,
                    normalizedEffort,
                    skipPermissions,
                    effectiveContinue,
                    normalizedConversation
                });

        String actualCliPath = (cliPath != null && !cliPath.isBlank()) ? cliPath : defaultCliPath;

        AntigravityExecutionResult result =
                processRunner.execute(
                        actualCliPath,
                        prompt,
                        targetWorkingDir,
                        normalizedModel,
                        normalizedEffort,
                        effectiveTimeout,
                        skipPermissions,
                        extraArgs,
                        effectiveContinue,
                        normalizedConversation);

        LOGGER.log(
                Level.INFO,
                "Antigravity CLI tool execution completed: success={0}, exitCode={1},"
                        + " timedOut={2}",
                new Object[] {result.success(), result.exitCode(), result.timedOut()});

        if (result.timedOut()) {
            String timeoutMessage =
                    "Antigravity CLI execution timed out after " + effectiveTimeout + " seconds.";
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(AntigravityJsonUtils.createErrorJson(timeoutMessage, -1))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(
                            Map.of(
                                    "exitCode",
                                    -1,
                                    "timedOut",
                                    true,
                                    "workingDirectory",
                                    targetWorkingDir.toString()))
                    .build();
        }

        if (!result.success()) {
            String error =
                    result.error() != null && !result.error().isBlank()
                            ? result.error()
                            : "Antigravity CLI execution failed with exit code "
                                    + result.exitCode();
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            AntigravityJsonUtils.createErrorJson(
                                                    error, result.exitCode()))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(
                            Map.of(
                                    "exitCode", result.exitCode(),
                                    "timedOut", false,
                                    "workingDirectory", targetWorkingDir.toString()))
                    .build();
        }

        String cleanJson = AntigravityJsonUtils.extractCleanJson(result.stdout());
        String returnedConversationId = AntigravityJsonUtils.extractConversationId(cleanJson);
        if (returnedConversationId != null) {
            LOGGER.log(
                    Level.INFO,
                    "Antigravity CLI returned conversation ID: {0}",
                    returnedConversationId);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exitCode", result.exitCode());
        metadata.put("timedOut", false);
        metadata.put("workingDirectory", targetWorkingDir.toString());
        if (returnedConversationId != null) {
            metadata.put("conversationId", returnedConversationId);
        } else if (normalizedConversation != null) {
            metadata.put("conversationId", normalizedConversation);
        }

        return ToolResultBlock.builder()
                .output(TextBlock.builder().text(cleanJson).build())
                .state(ToolResultState.SUCCESS)
                .metadata(Map.copyOf(metadata))
                .build();
    }

    /**
     * Resolves the target working directory for execution.
     *
     * @param pathInput User-specified working directory path or null
     * @return Resolved absolute Path
     */
    private Path resolveWorkingDirectory(String pathInput) {
        if (pathInput != null && !pathInput.isBlank()) {
            Path custom = Paths.get(pathInput.trim());
            if (custom.isAbsolute()) {
                return custom.normalize();
            }
            Path base = getProjectOrWorkspaceBase();
            return base.resolve(custom).normalize();
        }
        return getProjectOrWorkspaceBase();
    }

    /**
     * Retrieves the base directory from the current project or falls back to working directory.
     */
    private Path getProjectOrWorkspaceBase() {
        if (context != null && context.projectService() != null) {
            ProjectInfo project = context.projectService().currentDefault();
            if (project.getBaseDirectory() != null && !project.getBaseDirectory().isBlank()) {
                Path projectDir =
                        Paths.get(project.getBaseDirectory()).toAbsolutePath().normalize();
                if (Files.exists(projectDir) && Files.isDirectory(projectDir)) {
                    return projectDir;
                }
            }
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }
}
