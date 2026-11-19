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
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.util.FileNameUtils;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.filesystem.model.ExecuteResponse;
import io.agentscope.harness.agent.tool.ShellExecuteTool;
import io.agentscope.harness.agent.workspace.PathPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Policy-aware shell execution tool replacing AgentScope Harness's default {@link ShellExecuteTool}
 * via {@link ai.emailclaw.emailclaw.service.ToolService#reconcileAgentToolkit}.
 *
 * <p>AgentScope's stock {@code ShellExecuteTool} rejects every {@code working_directory} starting
 * with {@code /}, {@code ~}, or containing {@code ..} under a single-container {@code /workspace}
 * assumption. In Emailclaw's local rooted overlay architecture, however, shell commands run with
 * {@code pwd} set to the active Project Base Directory while skills, agent workspace files, and
 * user-authorized additional roots live outside that directory. This tool aligns {@code execute}'s
 * {@code working_directory} validation with the session's {@link PathPolicy} (including symlink
 * real-path verification) and respects the agent's configured shell command timeout.
 */
public class EmailclawShellExecuteTool {

    private static final Logger LOGGER =
            Logger.getLogger(EmailclawShellExecuteTool.class.getName());

    private final ShellToolConfig config;
    private final List<Path> realAllowedRoots;
    private final boolean windows;

    /**
     * Constructs a policy-aware shell execution tool using Pure DI.
     *
     * @param config immutable shell execution configuration carrying sandbox backend, project root,
     *     path policy, and default timeout
     */
    public EmailclawShellExecuteTool(ShellToolConfig config) {
        this(config, System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
    }

    /**
     * Package-private constructor allowing deterministic OS mode injection in unit tests.
     *
     * @param config immutable shell execution configuration
     * @param windows whether Windows {@code cmd.exe} quoting semantics should be used
     */
    EmailclawShellExecuteTool(ShellToolConfig config, boolean windows) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.realAllowedRoots = resolveRealAllowedRoots(config.pathPolicy());
        this.windows = windows;
    }

    /**
     * Executes a shell command in the configured local sandbox filesystem.
     *
     * @param runtimeContext per-call agent runtime injected by AgentScope (not an LLM argument);
     *     may be {@code null} when no merged context is available
     * @param command shell command string to execute
     * @param workingDirectory optional target directory; defaults to the active Project Base
     *     Directory when omitted, or accepts a relative path under the project root or an
     *     absolute/{@code ~} path within the configured {@link PathPolicy} roots
     * @param timeout optional timeout in seconds; falls back to {@link
     *     ShellToolConfig#defaultTimeoutSeconds()} when omitted or non-positive
     * @return formatted command result containing exit code, combined output, and truncation notice
     */
    @Tool(
            name = ShellExecuteTool.NAME,
            description =
                    "Execute a shell command. Use for git, npm, python, build, test, and other"
                        + " terminal operations. Commands run with `pwd` set to the Project Base"
                        + " Directory by default. Returns combined output and exit code. If a"
                        + " dedicated tool exists (e.g., read_file, write_file), you MUST use it"
                        + " instead of shell commands.")
    public String execute(
            RuntimeContext runtimeContext,
            @ToolParam(name = "command", description = "Shell command to execute") String command,
            @ToolParam(
                            name = "working_directory",
                            description =
                                    "Optional working directory. Omit when running in the Project"
                                            + " Base Directory (the default `pwd`). Accepts a"
                                            + " relative path under the Project Base Directory, or"
                                            + " an absolute/'~' path within allowed roots (Project"
                                            + " Base Directory, Agent Workspace Directory, or"
                                            + " configured additional roots).",
                            required = false)
                    String workingDirectory,
            @ToolParam(
                            name = "timeout",
                            description =
                                    "Timeout in seconds (optional; defaults to configured agent"
                                            + " shell timeout)",
                            required = false)
                    Integer timeout) {
        String effectiveCommand = command;
        if (workingDirectory != null && !workingDirectory.isBlank()) {
            WorkingDirectoryResolution resolution = resolveWorkingDirectory(workingDirectory);
            if (!resolution.allowed()) {
                LOGGER.log(
                        Level.WARNING,
                        "Rejected shell working_directory={0}: {1}",
                        new Object[] {workingDirectory, resolution.errorMessage()});
                return resolution.errorMessage();
            }
            Path targetDir = resolution.resolvedDirectory();
            if (!isSameDirectory(targetDir, config.projectRoot())) {
                if (windows && containsUnsafeWindowsPathChars(targetDir.toString())) {
                    String error =
                            "Error: working_directory contains unsupported characters for Windows"
                                    + " shell: "
                                    + targetDir;
                    LOGGER.log(Level.WARNING, "{0}", error);
                    return error;
                }
                effectiveCommand = commandWithWorkingDirectory(targetDir.toString(), command);
                LOGGER.log(
                        Level.FINE,
                        "Resolved shell working_directory={0} -> {1}",
                        new Object[] {workingDirectory, targetDir});
            } else {
                LOGGER.log(
                        Level.FINE,
                        "Shell working_directory={0} matches default projectRoot={1}; omitting cd"
                                + " prefix",
                        new Object[] {workingDirectory, config.projectRoot()});
            }
        }

        int timeoutSeconds =
                timeout != null && timeout > 0 ? timeout : config.defaultTimeoutSeconds();
        ExecuteResponse result =
                config.sandbox().execute(runtimeContext, effectiveCommand, timeoutSeconds);

        StringBuilder sb = new StringBuilder();
        sb.append("Exit code: ").append(result.exitCode()).append("\n");
        if (result.output() != null && !result.output().isBlank()) {
            sb.append("\n").append(result.output());
        }
        if (result.truncated()) {
            sb.append("\n(output was truncated)");
        }
        return sb.toString();
    }

    /**
     * Resolves and validates a caller-supplied {@code working_directory} against {@link
     * ShellToolConfig#pathPolicy()}.
     */
    private WorkingDirectoryResolution resolveWorkingDirectory(String rawWorkingDirectory) {
        String stripped = rawWorkingDirectory.strip();
        String expanded = FileNameUtils.expandUserHome(stripped);
        Path parsed;
        try {
            parsed = Path.of(expanded);
        } catch (InvalidPathException e) {
            return WorkingDirectoryResolution.rejected(
                    "Error: invalid working_directory path: " + stripped);
        }

        Path candidate =
                (parsed.isAbsolute() ? parsed : config.projectRoot().resolve(parsed))
                        .toAbsolutePath()
                        .normalize();

        PathPolicy policy = config.pathPolicy();
        if (!policy.isAllowed(candidate)) {
            return WorkingDirectoryResolution.rejected(
                    "Error: working_directory is outside allowed roots: "
                            + candidate
                            + ". Allowed roots: "
                            + formatAllowedRoots(policy));
        }
        if (!Files.exists(candidate)) {
            return WorkingDirectoryResolution.rejected(
                    "Error: working_directory does not exist: " + candidate);
        }
        if (!Files.isDirectory(candidate)) {
            return WorkingDirectoryResolution.rejected(
                    "Error: working_directory is not a directory: " + candidate);
        }

        try {
            Path realCandidate = candidate.toRealPath();
            if (!isAllowedRealPath(realCandidate)) {
                return WorkingDirectoryResolution.rejected(
                        "Error: working_directory resolves via symlink outside allowed roots: "
                                + realCandidate
                                + ". Allowed roots: "
                                + formatAllowedRoots(policy));
            }
            return WorkingDirectoryResolution.accepted(candidate);
        } catch (IOException e) {
            return WorkingDirectoryResolution.rejected(
                    "Error: unable to access working_directory "
                            + candidate
                            + ": "
                            + e.getMessage());
        }
    }

    private boolean isAllowedRealPath(Path realCandidate) {
        for (Path realRoot : realAllowedRoots) {
            if (realCandidate.startsWith(realRoot)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> resolveRealAllowedRoots(PathPolicy policy) {
        List<Path> resolved = new ArrayList<>();
        for (Path root : policy.roots()) {
            Path normalized = root.toAbsolutePath().normalize();
            try {
                if (Files.exists(normalized)) {
                    resolved.add(normalized.toRealPath());
                } else {
                    resolved.add(normalized);
                }
            } catch (IOException ignored) {
                resolved.add(normalized);
            }
        }
        return List.copyOf(resolved);
    }

    private static boolean isSameDirectory(Path candidate, Path projectRoot) {
        if (candidate.equals(projectRoot)) {
            return true;
        }
        try {
            return Files.exists(projectRoot) && Files.isSameFile(candidate, projectRoot);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean containsUnsafeWindowsPathChars(String path) {
        return path.indexOf('"') >= 0 || path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0;
    }

    private static String formatAllowedRoots(PathPolicy policy) {
        if (policy.isEmpty()) {
            return "(none)";
        }
        return policy.roots().stream().map(Path::toString).collect(Collectors.joining(", "));
    }

    /**
     * Prepends a shell-safe {@code cd} statement targeting {@code workingDirectory} before {@code
     * command}.
     */
    String commandWithWorkingDirectory(String workingDirectory, String command) {
        if (windows) {
            return "cd /d \"" + workingDirectory.replace("\"", "\"\"") + "\" && " + command;
        }
        return "cd '" + workingDirectory.replace("'", "'\\''") + "' && " + command;
    }

    /**
     * Internal immutable outcome record for working directory validation.
     */
    private record WorkingDirectoryResolution(
            boolean allowed, Path resolvedDirectory, String errorMessage) {

        private static WorkingDirectoryResolution accepted(Path resolvedDirectory) {
            return new WorkingDirectoryResolution(true, resolvedDirectory, null);
        }

        private static WorkingDirectoryResolution rejected(String errorMessage) {
            return new WorkingDirectoryResolution(false, null, errorMessage);
        }
    }
}
