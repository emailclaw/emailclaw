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
import java.util.Map;
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
     * @param sandbox Optional sandbox mode policy (passed via {@code -s}, e.g. 'read-only', 'workspace-write', 'danger-full-access')
     * @param timeoutSeconds Optional execution timeout in seconds (defaults to 300)
     * @param extraArgs Optional additional command-line arguments to pass to the codex binary
     * @return Structured {@link ToolResultBlock} containing output text, execution state, and metadata
     */
    @Tool(
            name = "invokeCodexCli",
            description =
                    "Invoke Codex CLI using 'codex exec [OPTIONS] [PROMPT]' to execute coding tasks,"
                            + " analyze code, explain files, or apply project modifications non-interactively,"
                            + " returning structured JSON results.")
    public ToolResultBlock invokeCodexCli(
            @ToolParam(
                            name = "prompt",
                            description =
                                     "The task description or prompt to send to Codex CLI.")
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
                                    "Optional model name or override to pass to Codex CLI via"
                                            + " -m/--model (e.g. 'gpt-5', 'o3-mini').",
                            required = false)
                    String model,
            @ToolParam(
                            name = "sandbox",
                            description =
                                    "Optional sandbox policy mode (e.g. 'read-only',"
                                            + " 'workspace-write', 'danger-full-access').",
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
                                            + " codex binary.",
                            required = false)
                    String extraArgs,
            @ToolParam(
                            name = "continue_last_session",
                            description =
                                    "Whether to continue the last session (resume --last). Defaults to"
                                            + " true.",
                            required = false)
                    Boolean continueLastSession) {
        boolean effectiveContinue = continueLastSession == null || continueLastSession;
        LOGGER.log(
                Level.INFO,
                "InvokeCodexCli tool call initiated: promptLength={0}, model={1}, sandbox={2}, continueLastSession={3}",
                new Object[] {prompt != null ? prompt.length() : 0, model, sandbox, effectiveContinue});

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
                    .metadata(
                            Map.of(
                                    "exitCode", -1,
                                    "error", "Prompt cannot be empty or blank."))
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
                        + " model={3}, sandbox={4}, continueLastSession={5}",
                new Object[] {
                    actualCliPath, targetWorkingDir, effectiveTimeout, model, sandbox, effectiveContinue
                });

        CodexExecutionResult result =
                processRunner.execute(
                        actualCliPath,
                        prompt,
                        targetWorkingDir,
                        model,
                        sandbox,
                        effectiveTimeout,
                        extraArgs,
                        effectiveContinue);

        LOGGER.log(
                Level.INFO,
                "Codex CLI tool execution completed: success={0}, exitCode={1}, timedOut={2}",
                new Object[] {result.success(), result.exitCode(), result.timedOut()});

        if (result.timedOut()) {
            String timeoutMessage =
                    "Codex CLI execution timed out after "
                            + effectiveTimeout
                            + " seconds.";
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            CodexJsonUtils.createErrorJson(
                                                    timeoutMessage, -1))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(
                            Map.of(
                                    "exitCode", -1,
                                    "timedOut", true,
                                    "workingDirectory", targetWorkingDir.toString()))
                    .build();
        }

        if (!result.success()) {
            String error =
                    result.error() != null && !result.error().isBlank()
                            ? result.error()
                            : "Codex CLI execution failed with exit code "
                                    + result.exitCode();
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            CodexJsonUtils.createErrorJson(
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

        String cleanJson = CodexJsonUtils.extractCleanJson(result.stdout());
        Map<String, Object> metadata =
                Map.of(
                        "exitCode", result.exitCode(),
                        "timedOut", false,
                        "workingDirectory", targetWorkingDir.toString());

        return ToolResultBlock.builder()
                .output(TextBlock.builder().text(cleanJson).build())
                .state(ToolResultState.SUCCESS)
                .metadata(metadata)
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
            if (project.getBaseDirectory() != null
                    && !project.getBaseDirectory().isBlank()) {
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
