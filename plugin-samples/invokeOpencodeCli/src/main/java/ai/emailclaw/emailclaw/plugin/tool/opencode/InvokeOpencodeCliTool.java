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
package ai.emailclaw.emailclaw.plugin.tool.opencode;

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
 * Execution tool for OpenCode CLI.
 *
 * <p>Exposes the {@code invokeOpencodeCli} tool to AgentScope Java agents, allowing autonomous
 * agents to invoke the OpenCode CLI in non-interactive mode ({@code --cwd <path> -p <prompt> -f json -q})
 * and receive structured JSON output wrapped in a {@link ToolResultBlock}.
 */
public class InvokeOpencodeCliTool {

    private static final Logger LOGGER = Logger.getLogger(InvokeOpencodeCliTool.class.getName());

    public static final String DEFAULT_CLI_PATH = "opencode";
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final String DEFAULT_FORMAT = "json";

    private volatile PluginContext context;
    private final OpencodeProcessRunner processRunner;
    private final String defaultCliPath;
    private final int defaultTimeoutSeconds;
    private final String defaultFormat;
    private final boolean defaultQuiet;

    /**
     * Constructs the tool with the framework plugin context, default process runner, and standard settings.
     *
     * @param context Framework plugin context
     */
    public InvokeOpencodeCliTool(PluginContext context) {
        this(
                context,
                new DefaultOpencodeProcessRunner(),
                DEFAULT_CLI_PATH,
                DEFAULT_TIMEOUT_SECONDS,
                DEFAULT_FORMAT,
                true);
    }

    /**
     * Full dependency-injected constructor adhering to the Pure DI pattern.
     *
     * @param context Framework plugin context
     * @param processRunner Strategy runner for executing the CLI process
     * @param defaultCliPath Default executable binary path (defaults to 'opencode')
     * @param defaultTimeoutSeconds Default timeout in seconds (defaults to 300)
     * @param defaultFormat Default output format flag value (defaults to 'json')
     * @param defaultQuiet Whether to pass -q flag for quiet non-interactive output by default
     */
    public InvokeOpencodeCliTool(
            PluginContext context,
            OpencodeProcessRunner processRunner,
            String defaultCliPath,
            int defaultTimeoutSeconds,
            String defaultFormat,
            boolean defaultQuiet) {
        this.context = context;
        this.processRunner =
                processRunner != null ? processRunner : new DefaultOpencodeProcessRunner();
        this.defaultCliPath =
                defaultCliPath != null && !defaultCliPath.isBlank()
                        ? defaultCliPath
                        : DEFAULT_CLI_PATH;
        this.defaultTimeoutSeconds =
                defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.defaultFormat =
                defaultFormat != null && !defaultFormat.isBlank() ? defaultFormat : DEFAULT_FORMAT;
        this.defaultQuiet = defaultQuiet;
        LOGGER.fine("InvokeOpencodeCliTool initialized");
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
     * Executes the OpenCode CLI in non-interactive mode and returns a structured {@link ToolResultBlock}.
     *
     * @param prompt The task description or prompt to send to OpenCode CLI
     * @param cliPath Optional executable path for opencode (defaults to 'opencode')
     * @param workingDirectory Optional working directory where the CLI should execute (passed via {@code --cwd})
     * @param model Optional model name override (passed via {@code -m})
     * @param format Optional output format (defaults to 'json', passed via {@code -f})
     * @param quiet Whether to pass {@code -q} flag for quiet output (defaults to true)
     * @param timeoutSeconds Optional execution timeout in seconds (defaults to 300)
     * @param extraArgs Optional additional command-line arguments to pass to the opencode binary
     * @return Structured {@link ToolResultBlock} containing output text, execution state, and metadata
     */
    @Tool(
            name = "invokeOpencodeCli",
            description =
                    "Invoke OpenCode CLI in headless non-interactive mode (-p) with JSON format"
                            + " (-f json) and quiet flag (-q) to execute coding tasks, analyze code,"
                            + " explain files, or apply project modifications, returning structured"
                            + " JSON results.")
    public ToolResultBlock invokeOpencodeCli(
            @ToolParam(
                            name = "prompt",
                            description =
                                    "The task description or prompt to send to OpenCode CLI.")
                    String prompt,
            @ToolParam(
                            name = "cli_path",
                            description =
                                    "Optional executable path for opencode. If omitted, defaults to"
                                            + " 'opencode'.",
                            required = false)
                    String cliPath,
            @ToolParam(
                            name = "working_directory",
                            description =
                                    "Optional working directory for OpenCode CLI (--cwd). If"
                                            + " omitted, the current project base directory is"
                                            + " used.",
                            required = false)
                    String workingDirectory,
            @ToolParam(
                            name = "model",
                            description =
                                    "Optional model name or override to pass to OpenCode CLI via"
                                            + " -m/--model (e.g. 'openai/gpt-4o',"
                                            + " 'anthropic/claude-3-5-sonnet').",
                            required = false)
                    String model,
            @ToolParam(
                            name = "format",
                            description =
                                    "Optional output format to pass via -f (default is 'json').",
                            required = false)
                    String format,
            @ToolParam(
                            name = "quiet",
                            description =
                                    "Whether to pass -q flag for quiet non-interactive execution"
                                            + " (default is true).",
                            required = false)
                    Boolean quiet,
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
                                            + " opencode binary.",
                            required = false)
                    String extraArgs) {
        LOGGER.log(
                Level.INFO,
                "InvokeOpencodeCli tool call initiated: promptLength={0}, workingDir={1},"
                        + " model={2}, format={3}, quiet={4}",
                new Object[] {
                    prompt != null ? prompt.length() : 0,
                    workingDirectory,
                    model,
                    format,
                    quiet
                });

        if (prompt == null || prompt.isBlank()) {
            LOGGER.warning("InvokeOpencodeCli tool call rejected: prompt is empty");
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            OpencodeJsonUtils.createErrorJson(
                                                    "Prompt cannot be empty or blank.", -1))
                                    .build())
                    .state(ToolResultState.ERROR)
                    .metadata(
                            Map.of(
                                    "exitCode", -1,
                                    "error", "Prompt cannot be empty or blank."))
                    .build();
        }

        Path targetWorkingDir = resolveWorkingDirectory(workingDirectory);
        int effectiveTimeout =
                (timeoutSeconds != null && timeoutSeconds > 0)
                        ? timeoutSeconds
                        : defaultTimeoutSeconds;
        String actualCliPath =
                (cliPath != null && !cliPath.isBlank()) ? cliPath.trim() : defaultCliPath;
        String effectiveFormat =
                (format != null && !format.isBlank()) ? format.trim() : defaultFormat;
        boolean effectiveQuiet = (quiet != null) ? quiet : defaultQuiet;

        LOGGER.log(
                Level.INFO,
                "Launching OpenCode CLI tool execution: cliPath={0}, workingDir={1}, timeout={2}s,"
                        + " format={3}, quiet={4}",
                new Object[] {
                    actualCliPath,
                    targetWorkingDir,
                    effectiveTimeout,
                    effectiveFormat,
                    effectiveQuiet
                });

        OpencodeExecutionResult result =
                processRunner.execute(
                        actualCliPath,
                        prompt,
                        targetWorkingDir,
                        model,
                        effectiveFormat,
                        effectiveQuiet,
                        effectiveTimeout,
                        extraArgs);

        LOGGER.log(
                Level.INFO,
                "OpenCode CLI tool execution completed: success={0}, exitCode={1}, timedOut={2}",
                new Object[] {result.success(), result.exitCode(), result.timedOut()});

        if (result.timedOut()) {
            String timeoutMessage =
                    "OpenCode CLI execution timed out after "
                            + effectiveTimeout
                            + " seconds.";
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            OpencodeJsonUtils.createErrorJson(
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
                            : "OpenCode CLI execution failed with exit code "
                                    + result.exitCode();
            return ToolResultBlock.builder()
                    .output(
                            TextBlock.builder()
                                    .text(
                                            OpencodeJsonUtils.createErrorJson(
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

        String cleanJson = OpencodeJsonUtils.extractCleanJson(result.stdout());
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
