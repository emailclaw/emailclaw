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
package ai.emailclaw.emailclaw.plugin.tool.claude;

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
 * Execution tool for Claude Code CLI.
 *
 * <p>Exposes the {@code invokeClaudeCodeCli} tool to AgentScope Java agents, allowing autonomous
 * agents to invoke the Claude Code CLI non-interactively via {@code claude -p [OPTIONS] [PROMPT]}
 * and receive structured JSON output wrapped in a {@link ToolResultBlock}.
 */
public class InvokeClaudeCodeCliTool {

    private static final Logger LOGGER = Logger.getLogger(InvokeClaudeCodeCliTool.class.getName());

    public static final String DEFAULT_CLI_PATH = "claude";
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final Set<String> ALLOWED_EFFORTS =
            Set.of("low", "medium", "high", "xhigh", "max");

    private volatile PluginContext context;
    private final ClaudeCodeProcessRunner processRunner;
    private final String defaultCliPath;
    private final int defaultTimeoutSeconds;

    /**
     * Constructs the tool with the framework plugin context, default process runner, and standard settings.
     *
     * @param context Framework plugin context
     */
    public InvokeClaudeCodeCliTool(PluginContext context) {
        this(
                context,
                new DefaultClaudeCodeProcessRunner(),
                DEFAULT_CLI_PATH,
                DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Dependency-injected constructor with custom path resolver adhering to Pure DI.
     *
     * @param context Framework plugin context
     * @param pathResolver Strategy for locating Claude Code CLI executable
     */
    public InvokeClaudeCodeCliTool(PluginContext context, ClaudeCodePathResolver pathResolver) {
        this(
                context,
                new DefaultClaudeCodeProcessRunner(pathResolver),
                DEFAULT_CLI_PATH,
                DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Full dependency-injected constructor adhering to the Pure DI pattern.
     *
     * @param context Framework plugin context
     * @param processRunner Strategy runner for executing the CLI process
     * @param defaultCliPath Default executable binary path (defaults to 'claude')
     * @param defaultTimeoutSeconds Default timeout in seconds (defaults to 300)
     */
    public InvokeClaudeCodeCliTool(
            PluginContext context,
            ClaudeCodeProcessRunner processRunner,
            String defaultCliPath,
            int defaultTimeoutSeconds) {
        this.context = context;
        this.processRunner =
                processRunner != null ? processRunner : new DefaultClaudeCodeProcessRunner();
        this.defaultCliPath =
                defaultCliPath != null && !defaultCliPath.isBlank()
                        ? defaultCliPath
                        : DEFAULT_CLI_PATH;
        this.defaultTimeoutSeconds =
                defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        LOGGER.fine("InvokeClaudeCodeCliTool initialized");
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
     * Executes the Claude Code CLI using {@code claude -p [OPTIONS] [PROMPT]} and returns a structured {@link ToolResultBlock}.
     *
     * @param prompt The task description or prompt to send to Claude Code CLI
     * @param cliPath Optional executable path for claude (defaults to 'claude')
     * @param model Optional model name or alias override (passed via {@code --model})
     * @param effort Optional effort level for the current session (passed via {@code --effort}:
     *     low|medium|high|xhigh|max)
     * @param permissionMode Optional permission mode (passed via {@code --permission-mode})
     * @param timeoutSeconds Optional execution timeout in seconds (defaults to 300)
     * @param extraArgs Optional additional command-line arguments to pass to the claude binary
     * @param continueLastSession Whether to continue the last session (-c / --continue). Defaults
     *     to true when {@code conversation} is omitted. Set to false (and omit {@code
     *     conversation}) to start a new session.
     * @param conversation Optional previous Claude Code session ID or name to pass via {@code
     *     --resume} (Resume a conversation by session ID or name). Takes precedence over {@code
     *     -c}/{@code --continue}.
     * @return Structured {@link ToolResultBlock} containing output text, execution state, and metadata
     */
    @Tool(
            name = "invokeClaudeCodeCli",
            description =
                    "Invoke Claude Code CLI using 'claude -p --output-format json [OPTIONS]"
                            + " [PROMPT]' to execute coding tasks, analyze code, explain files, or"
                            + " apply project modifications non-interactively, returning structured"
                            + " JSON results (including session_id). Supports optional --model"
                            + " (Model for the current session) and --effort (Effort level:"
                            + " low|medium|high|xhigh|max). Session selection rules: (1) If the"
                            + " user asks to start a new session when calling claude, do NOT pass"
                            + " -c, --continue, -r, or --resume (set continue_last_session=false"
                            + " and omit conversation). (2) If the user does NOT ask for a new"
                            + " session, first check the conversation history for any session ID"
                            + " returned by claude (e.g. session_id); if found, pass it via the"
                            + " conversation parameter (--resume: Resume a conversation by session"
                            + " ID or name). (3) If no historical claude session ID exists, use"
                            + " continue_last_session=true (adds -c, equivalent to --continue).")
    public ToolResultBlock invokeClaudeCodeCli(
            @ToolParam(
                            name = "prompt",
                            description =
                                    "The task description or prompt to send to Claude Code CLI.")
                    String prompt,
            @ToolParam(
                            name = "cli_path",
                            description =
                                    "Optional executable path for claude. If omitted, defaults to"
                                            + " 'claude'.",
                            required = false)
                    String cliPath,
            @ToolParam(
                            name = "model",
                            description =
                                    "Optional model for the current Claude Code session to pass via"
                                        + " --model (e.g. 'sonnet', 'opus', 'haiku', 'fable', or a"
                                        + " full model name). Omit or leave empty to use the Claude"
                                        + " Code default model.",
                            required = false)
                    String model,
            @ToolParam(
                            name = "effort",
                            description =
                                    "Optional effort level for the current Claude Code session to"
                                            + " pass via --effort (low|medium|high|xhigh|max). Omit"
                                            + " or leave empty to use the Claude Code default"
                                            + " effort.",
                            required = false)
                    String effort,
            @ToolParam(
                            name = "permission_mode",
                            description =
                                    "Optional permission mode (e.g. 'bypassPermissions', 'auto',"
                                            + " 'plan', 'acceptEdits', 'dontAsk', 'manual').",
                            required = false)
                    String permissionMode,
            @ToolParam(
                            name = "timeout_seconds",
                            description =
                                    "Optional execution timeout in seconds (default is 300"
                                            + " seconds).",
                            required = false)
                    Integer timeoutSeconds,
            @ToolParam(
                            name = "extra_args",
                            description =
                                    "Optional additional command-line arguments to pass to the"
                                            + " claude binary. Do not include -c, --continue, -r,"
                                            + " or --resume when starting a new session.",
                            required = false)
                    String extraArgs,
            @ToolParam(
                            name = "continue_last_session",
                            description =
                                    "Whether to continue the most recent Claude Code conversation"
                                        + " in the current directory (-c / --continue). Defaults to"
                                        + " true when 'conversation' is omitted. Set to false (and"
                                        + " omit 'conversation') when the user requests a new"
                                        + " session so neither -c, --continue, -r, nor --resume is"
                                        + " passed. Ignored when 'conversation' is provided.",
                            required = false)
                    Boolean continueLastSession,
            @ToolParam(
                            name = "conversation",
                            description =
                                    "Optional previous Claude Code session ID or name to pass via"
                                            + " --resume (Resume a conversation by session ID or"
                                            + " name). When the user does not request a new session"
                                            + " and a session_id from claude exists in history,"
                                            + " pass that ID here (takes precedence over"
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
                "InvokeClaudeCodeCli tool call initiated: promptLength={0}, model={1}, effort={2},"
                        + " permissionMode={3}, continueLastSession={4}, conversation={5}",
                new Object[] {
                    prompt != null ? prompt.length() : 0,
                    normalizedModel,
                    normalizedEffort,
                    permissionMode,
                    effectiveContinue,
                    normalizedConversation
                });

        if (prompt == null || prompt.isBlank()) {
            LOGGER.warning("InvokeClaudeCodeCli tool call rejected: prompt is empty");
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            ClaudeCodeJsonUtils.createErrorJson(
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
            LOGGER.log(Level.WARNING, "InvokeClaudeCodeCli tool call rejected: {0}", errorMsg);
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(ClaudeCodeJsonUtils.createErrorJson(errorMsg, -1))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(Map.of("exitCode", -1, "error", errorMsg))
                    .build();
        }

        Path targetWorkingDir = getProjectOrWorkspaceBase();
        int effectiveTimeout =
                (timeoutSeconds != null && timeoutSeconds > 0)
                        ? timeoutSeconds
                        : defaultTimeoutSeconds;
        String actualCliPath =
                (cliPath != null && !cliPath.isBlank()) ? cliPath.trim() : defaultCliPath;

        LOGGER.log(
                Level.INFO,
                "Launching Claude Code CLI tool execution: cliPath={0}, workingDir={1},"
                        + " timeout={2}s, model={3}, effort={4}, permissionMode={5},"
                        + " continueLastSession={6}, conversation={7}",
                new Object[] {
                    actualCliPath,
                    targetWorkingDir,
                    effectiveTimeout,
                    normalizedModel,
                    normalizedEffort,
                    permissionMode,
                    effectiveContinue,
                    normalizedConversation
                });

        ClaudeCodeExecutionResult result =
                processRunner.execute(
                        actualCliPath,
                        prompt,
                        targetWorkingDir,
                        normalizedModel,
                        normalizedEffort,
                        permissionMode,
                        effectiveTimeout,
                        extraArgs,
                        effectiveContinue,
                        normalizedConversation);

        LOGGER.log(
                Level.INFO,
                "Claude Code CLI tool execution completed: success={0}, exitCode={1}, timedOut={2}",
                new Object[] {result.success(), result.exitCode(), result.timedOut()});

        if (result.timedOut()) {
            String timeoutMessage =
                    "Claude Code CLI execution timed out after " + effectiveTimeout + " seconds.";
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(ClaudeCodeJsonUtils.createErrorJson(timeoutMessage, -1))
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
                            : "Claude Code CLI execution failed with exit code "
                                    + result.exitCode();
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            ClaudeCodeJsonUtils.createErrorJson(
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

        String cleanJson = ClaudeCodeJsonUtils.extractCleanJson(result.stdout());
        String returnedConversationId = ClaudeCodeJsonUtils.extractConversationId(cleanJson);
        if (returnedConversationId != null) {
            LOGGER.log(
                    Level.INFO, "Claude Code CLI returned session ID: {0}", returnedConversationId);
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
     * Retrieves the base directory from the current project or falls back to working directory.
     *
     * <p>Adheres strictly to the architectural invariant that projects obtained from
     * {@link ai.emailclaw.emailclaw.service.ProjectService} are non-null.
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
