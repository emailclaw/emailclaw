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
import java.util.Map;
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
     * @param model Optional model name override (e.g. 'gemini-2.5-pro')
     * @param timeoutSeconds Optional execution timeout in seconds (defaults to 300)
     * @param dangerouslySkipPermissions Whether to automatically pass --dangerously-skip-permissions
     * @param extraArgs Optional additional command-line arguments to pass to the agy binary
     * @return Structured {@link ToolResultBlock} containing output text, execution state, and metadata
     */
    @Tool(
            name = "invokeAntigravityCli",
            description =
                    "Invoke Google Antigravity CLI (agy) in headless mode (-p/--print) to execute"
                            + " tasks, run tests, analyze code, or generate artifacts, returning"
                            + " structured JSON results.")
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
                                    "Optional model name or override to pass to Antigravity CLI"
                                            + " (e.g. 'gemini-2.5-pro').",
                            required = false)
                    String model,
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
                                            + " binary.",
                            required = false)
                    String extraArgs) {
        LOGGER.log(
                Level.INFO,
                "InvokeAntigravityCli tool call initiated: promptLength={0}, workingDir={1},"
                        + " model={2}",
                new Object[] {prompt != null ? prompt.length() : 0, workingDirectory, model});

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
                    .metadata(
                            Map.of(
                                    "exitCode", -1,
                                    "error", "Prompt cannot be empty or blank."))
                    .build();
        }

        String effectivePrompt = AntigravityJsonUtils.appendJsonFormatInstruction(prompt);
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
                        + " skipPermissions={2}",
                new Object[] {targetWorkingDir, effectiveTimeout, skipPermissions});

        String actualCliPath = (cliPath != null && !cliPath.isBlank()) ? cliPath : defaultCliPath;

        AntigravityExecutionResult result =
                processRunner.execute(
                        actualCliPath,
                        effectivePrompt,
                        targetWorkingDir,
                        model,
                        effectiveTimeout,
                        skipPermissions,
                        extraArgs);

        LOGGER.log(
                Level.INFO,
                "Antigravity CLI tool execution completed: success={0}, exitCode={1},"
                        + " timedOut={2}",
                new Object[] {result.success(), result.exitCode(), result.timedOut()});

        if (result.timedOut()) {
            String timeoutMessage =
                    "Antigravity CLI execution timed out after "
                            + effectiveTimeout
                            + " seconds.";
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            AntigravityJsonUtils.createErrorJson(
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
            if (project != null
                    && project.getBaseDirectory() != null
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
