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
package ai.emailclaw.emailclaw.plugin.tool.codex;

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
 * Execution tool for Codex CLI.
 *
 * <p>Exposes the {@code invokeCodexCli} tool to AgentScope Java agents, allowing autonomous
 * agents to invoke the Codex CLI non-interactively via {@code codex exec [OPTIONS] [PROMPT]}
 * and receive structured JSON output wrapped in a {@link ToolResultBlock}.
 */
public class InvokeCodexCliTool {

    private static final Logger LOGGER = Logger.getLogger(InvokeCodexCliTool.class.getName());

    public static final String DEFAULT_CLI_PATH = "codex";
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final Set<String> ALLOWED_EFFORTS =
            Set.of("none", "minimal", "low", "medium", "high", "xhigh");

    private volatile PluginContext context;
    private final CodexProcessRunner processRunner;
    private final String defaultCliPath;
    private final int defaultTimeoutSeconds;

    /**
     * Constructs the tool with the framework plugin context, default process runner, and standard settings.
     *
     * @param context Framework plugin context
     */
    public InvokeCodexCliTool(PluginContext context) {
        this(context, new DefaultCodexProcessRunner(), DEFAULT_CLI_PATH, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Dependency-injected constructor with custom path resolver adhering to Pure DI.
     *
     * @param context Framework plugin context
     * @param pathResolver Strategy for locating Codex CLI executable
     */
    public InvokeCodexCliTool(PluginContext context, CodexPathResolver pathResolver) {
        this(
                context,
                new DefaultCodexProcessRunner(pathResolver),
                DEFAULT_CLI_PATH,
                DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Full dependency-injected constructor adhering to the Pure DI pattern.
     *
     * @param context Framework plugin context
     * @param processRunner Strategy runner for executing the CLI process
     * @param defaultCliPath Default executable binary path (defaults to 'codex')
     * @param defaultTimeoutSeconds Default timeout in seconds (defaults to 300)
     */
    public InvokeCodexCliTool(
            PluginContext context,
            CodexProcessRunner processRunner,
            String defaultCliPath,
            int defaultTimeoutSeconds) {
        this.context = context;
        this.processRunner =
                processRunner != null ? processRunner : new DefaultCodexProcessRunner();
        this.defaultCliPath =
                defaultCliPath != null && !defaultCliPath.isBlank()
                        ? defaultCliPath
                        : DEFAULT_CLI_PATH;
        this.defaultTimeoutSeconds =
                defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        LOGGER.fine("InvokeCodexCliTool initialized");
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
     * Executes the Codex CLI using {@code codex exec [OPTIONS] [PROMPT]} and returns a structured {@link ToolResultBlock}.
     *
     * @param prompt The task description or prompt to send to Codex CLI
     * @param cliPath Optional executable path for codex (defaults to 'codex')
     * @param model Optional model name override (passed via {@code -m})
     * @param effort Optional reasoning effort override (passed via {@code -c
     *     model_reasoning_effort="<effort>"}: none|minimal|low|medium|high|xhigh)
     * @param sandbox Optional sandbox mode policy (passed via {@code -s}, e.g. 'read-only', 'workspace-write', 'danger-full-access')
     * @param timeoutSeconds Optional execution timeout in seconds (defaults to 300)
     * @param extraArgs Optional additional command-line arguments to pass to the codex binary
     * @param continueLastSession Whether to continue the last session ({@code resume --last}).
     *     Defaults to true when {@code conversation} is omitted. Set to false (and omit {@code
     *     conversation}) to start a new session.
     * @param conversation Optional previous Codex session/thread ID to resume via {@code codex exec
     *     resume [OPTIONS] <SESSION_ID> [PROMPT]}. Takes precedence over {@code --last}.
     * @return Structured {@link ToolResultBlock} containing output text, execution state, and metadata
     */
    @Tool(
            name = "invokeCodexCli",
            description =
                    "Invoke Codex CLI using 'codex exec --json [OPTIONS] [PROMPT]' to execute"
                            + " coding tasks, analyze code, explain files, or apply project"
                            + " modifications non-interactively, returning structured JSON results"
                            + " (including thread_id). Supports optional model (-m/--model) and"
                            + " effort (-c model_reasoning_effort=<none|minimal|low|medium|high|"
                            + "xhigh>). Session selection rules: (1) If the user asks to start a"
                            + " new session when calling codex, do NOT pass resume, --last, or a"
                            + " session ID (set continue_last_session=false and omit conversation)."
                            + " (2) If the user does NOT ask for a new session, first check the"
                            + " conversation history for any session/thread ID returned by codex"
                            + " (e.g. thread_id); if found, pass it via the conversation parameter"
                            + " (codex exec resume <SESSION_ID>). (3) If no historical codex"
                            + " session ID exists, use continue_last_session=true (adds resume"
                            + " --last).")
    public ToolResultBlock invokeCodexCli(
            @ToolParam(
                            name = "prompt",
                            description = "The task description or prompt to send to Codex CLI.")
                    String prompt,
            @ToolParam(
                            name = "cli_path",
                            description =
                                    "Optional executable path for codex. If omitted, defaults to"
                                            + " 'codex'.",
                            required = false)
                    String cliPath,
            @ToolParam(
                            name = "model",
                            description =
                                    "Optional model for the current Codex CLI session to pass via"
                                            + " -m/--model (e.g. 'gpt-5', 'o3-mini'). Omit or leave"
                                            + " empty to use the Codex default model.",
                            required = false)
                    String model,
            @ToolParam(
                            name = "effort",
                            description =
                                    "Optional reasoning effort for the current Codex CLI session to"
                                            + " pass via -c model_reasoning_effort=\"<effort>\""
                                            + " (none|minimal|low|medium|high|xhigh). Omit or leave"
                                            + " empty to use the Codex default effort.",
                            required = false)
                    String effort,
            @ToolParam(
                            name = "sandbox",
                            description =
                                    "Optional sandbox policy mode (e.g. 'read-only',"
                                            + " 'workspace-write', 'danger-full-access'). Only"
                                            + " applied when starting a new session.",
                            required = false)
                    String sandbox,
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
                                            + " codex binary. Do not include resume or --last when"
                                            + " starting a new session.",
                            required = false)
                    String extraArgs,
            @ToolParam(
                            name = "continue_last_session",
                            description =
                                    "Whether to continue the most recent Codex session (resume"
                                        + " --last). Defaults to true when 'conversation' is"
                                        + " omitted. Set to false (and omit 'conversation') when"
                                        + " the user requests a new session so neither resume nor"
                                        + " --last is passed. Ignored when 'conversation' is"
                                        + " provided.",
                            required = false)
                    Boolean continueLastSession,
            @ToolParam(
                            name = "conversation",
                            description =
                                    "Optional previous Codex session/thread ID (UUID or thread"
                                            + " name) to resume via 'codex exec resume [OPTIONS]"
                                            + " <SESSION_ID> [PROMPT]'. When the user does not"
                                            + " request a new session and a Codex session/thread ID"
                                            + " exists in history, pass that ID here (takes"
                                            + " precedence over --last). Omit when starting a new"
                                            + " session.",
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
                "InvokeCodexCli tool call initiated: promptLength={0}, model={1}, effort={2},"
                        + " sandbox={3}, continueLastSession={4}, conversation={5}",
                new Object[] {
                    prompt != null ? prompt.length() : 0,
                    normalizedModel,
                    normalizedEffort,
                    sandbox,
                    effectiveContinue,
                    normalizedConversation
                });

        if (prompt == null || prompt.isBlank()) {
            LOGGER.warning("InvokeCodexCli tool call rejected: prompt is empty");
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            CodexJsonUtils.createErrorJson(
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
                            + "'. Allowed values are: none, minimal, low, medium, high, xhigh.";
            LOGGER.log(Level.WARNING, "InvokeCodexCli tool call rejected: {0}", errorMsg);
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(CodexJsonUtils.createErrorJson(errorMsg, -1))
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
                "Launching Codex CLI tool execution: cliPath={0}, workingDir={1}, timeout={2}s,"
                        + " model={3}, effort={4}, sandbox={5}, continueLastSession={6},"
                        + " conversation={7}",
                new Object[] {
                    actualCliPath,
                    targetWorkingDir,
                    effectiveTimeout,
                    normalizedModel,
                    normalizedEffort,
                    sandbox,
                    effectiveContinue,
                    normalizedConversation
                });

        CodexExecutionResult result =
                processRunner.execute(
                        actualCliPath,
                        prompt,
                        targetWorkingDir,
                        normalizedModel,
                        normalizedEffort,
                        sandbox,
                        effectiveTimeout,
                        extraArgs,
                        effectiveContinue,
                        normalizedConversation);

        LOGGER.log(
                Level.INFO,
                "Codex CLI tool execution completed: success={0}, exitCode={1}, timedOut={2}",
                new Object[] {result.success(), result.exitCode(), result.timedOut()});

        if (result.timedOut()) {
            String timeoutMessage =
                    "Codex CLI execution timed out after " + effectiveTimeout + " seconds.";
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(CodexJsonUtils.createErrorJson(timeoutMessage, -1))
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
                            : "Codex CLI execution failed with exit code " + result.exitCode();
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(CodexJsonUtils.createErrorJson(error, result.exitCode()))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(
                            Map.of(
                                    "exitCode", result.exitCode(),
                                    "timedOut", false,
                                    "workingDirectory", targetWorkingDir.toString()))
                    .build();
        }

        String cleanJson = CodexJsonUtils.extractCleanJson(result.stdout());
        String returnedConversationId = CodexJsonUtils.extractConversationId(cleanJson);
        if (returnedConversationId != null) {
            LOGGER.log(
                    Level.INFO,
                    "Codex CLI returned session/thread ID: {0}",
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
