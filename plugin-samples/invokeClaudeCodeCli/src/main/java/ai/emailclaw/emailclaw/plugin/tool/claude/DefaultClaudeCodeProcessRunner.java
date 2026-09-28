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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Industrial-grade default implementation of {@link ClaudeCodeProcessRunner}.
 *
 * <p>Spawns the Claude Code CLI binary via {@link ProcessBuilder} with print/non-interactive mode
 * ({@code claude -p [OPTIONS] [PROMPT]}), capturing standard output and error streams
 * in non-blocking threads to prevent pipe deadlock, enforcing timeouts and graceful resource destruction.
 */
public class DefaultClaudeCodeProcessRunner implements ClaudeCodeProcessRunner {

    private static final Logger LOGGER =
            Logger.getLogger(DefaultClaudeCodeProcessRunner.class.getName());

    public DefaultClaudeCodeProcessRunner() {}

    /**
     * Builds the command arguments for claude execution.
     *
     * <p>When continueLastSession is true, generates {@code claude -c -p [OPTIONS] [PROMPT]}
     * to continue the most recent recorded conversation in the current directory.
     * When false, generates {@code claude -p [OPTIONS] [PROMPT]} to start a new execution.
     */
    List<String> buildCommand(
            String cliPath,
            String prompt,
            String model,
            String permissionMode,
            String extraArgs,
            boolean continueLastSession) {
        List<String> command = new ArrayList<>();
        String effectiveCliPath =
                (cliPath != null && !cliPath.isBlank()) ? cliPath.trim() : "claude";
        command.add(effectiveCliPath);

        boolean hasContinue = false;
        boolean hasPrint = false;
        boolean hasOutputFormat = false;
        boolean hasModel = false;
        boolean hasPermissionMode = false;
        List<String> extraTokens = new ArrayList<>();

        if (extraArgs != null && !extraArgs.isBlank()) {
            for (String token : extraArgs.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    if ("-c".equals(token) || "--continue".equals(token)) {
                        hasContinue = true;
                    }
                    if ("-p".equals(token) || "--print".equals(token)) {
                        hasPrint = true;
                    }
                    if ("--output-format".equals(token)) {
                        hasOutputFormat = true;
                    }
                    if ("--model".equals(token)) {
                        hasModel = true;
                    }
                    if ("--permission-mode".equals(token)) {
                        hasPermissionMode = true;
                    }
                    extraTokens.add(token);
                }
            }
        }

        if (continueLastSession && !hasContinue) {
            command.add("-c");
        }

        if (!hasPrint) {
            command.add("-p");
        }

        if (!hasOutputFormat) {
            command.add("--output-format");
            command.add("json");
        }

        if (model != null && !model.isBlank() && !hasModel) {
            command.add("--model");
            command.add(model.trim());
        }

        if (permissionMode != null && !permissionMode.isBlank() && !hasPermissionMode) {
            command.add("--permission-mode");
            command.add(permissionMode.trim());
        }

        command.addAll(extraTokens);

        if (prompt != null && !prompt.isBlank()) {
            command.add(prompt);
        }
        return command;
    }

    @Override
    public ClaudeCodeExecutionResult execute(
            String cliPath,
            String prompt,
            Path workingDirectory,
            String model,
            String permissionMode,
            int timeoutSeconds,
            String extraArgs,
            boolean continueLastSession) {
        List<String> command =
                buildCommand(
                        cliPath,
                        prompt,
                        model,
                        permissionMode,
                        extraArgs,
                        continueLastSession);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            File dirFile = workingDirectory.toFile();
            if (dirFile.exists() && dirFile.isDirectory()) {
                processBuilder.directory(dirFile);
            }
        }

        // Industrial-grade non-blocking environment:
        // Redirect stdin from /dev/null so headless CLI tools never block waiting on stdin/input
        File devNull = new File("/dev/null");
        if (devNull.exists()) {
            processBuilder.redirectInput(ProcessBuilder.Redirect.from(devNull));
        }

        processBuilder.environment().put("TERM", "dumb");
        processBuilder.environment().remove("CI");
        processBuilder.environment().put("NO_COLOR", "1");

        LOGGER.log(
                Level.INFO,
                "Launching Claude Code CLI process: command={0}, workingDir={1}, timeout={2}s",
                new Object[] {command, workingDirectory, timeoutSeconds});

        Process process;
        try {
            process = processBuilder.start();
            try {
                // Ensure stdin pipe is immediately closed so subprocess receives EOF on stdin
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to launch Claude Code CLI process: " + command.get(0),
                    e);
            return new ClaudeCodeExecutionResult(
                    -1,
                    "",
                    e.getMessage(),
                    false,
                    false,
                    "Failed to start Claude Code CLI process ('"
                            + command.get(0)
                            + "'): "
                            + e.getMessage());
        }

        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();

        CompletableFuture<Void> stdoutFuture =
                CompletableFuture.runAsync(
                        () -> {
                            try (BufferedReader reader =
                                    new BufferedReader(
                                            new InputStreamReader(
                                                    process.getInputStream(),
                                                    StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    stdoutBuilder.append(line).append(System.lineSeparator());
                                }
                            } catch (IOException e) {
                                LOGGER.log(
                                        Level.FINE,
                                        "Error reading stdout from Claude Code CLI process",
                                        e);
                            }
                        });

        CompletableFuture<Void> stderrFuture =
                CompletableFuture.runAsync(
                        () -> {
                            try (BufferedReader reader =
                                    new BufferedReader(
                                            new InputStreamReader(
                                                    process.getErrorStream(),
                                                    StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    stderrBuilder.append(line).append(System.lineSeparator());
                                }
                            } catch (IOException e) {
                                LOGGER.log(
                                        Level.FINE,
                                        "Error reading stderr from Claude Code CLI process",
                                        e);
                            }
                        });

        boolean completed;
        try {
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Claude Code CLI execution thread was interrupted", e);
            process.destroyForcibly();
            try {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
            } catch (Exception ignored) {
            }
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            return new ClaudeCodeExecutionResult(
                    -1,
                    stdoutBuilder.toString(),
                    stderrBuilder.toString(),
                    false,
                    false,
                    "Claude Code CLI execution was interrupted.");
        }

        if (!completed) {
            LOGGER.log(
                    Level.WARNING,
                    "Claude Code CLI execution exceeded timeout of {0} seconds, destroying process",
                    timeoutSeconds);
            process.destroyForcibly();
            try {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
            } catch (Exception ignored) {
            }
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            return new ClaudeCodeExecutionResult(
                    -1,
                    stdoutBuilder.toString(),
                    stderrBuilder.toString(),
                    true,
                    false,
                    "Claude Code CLI execution timed out after "
                            + timeoutSeconds
                            + " seconds.");
        }

        try {
            CompletableFuture.allOf(stdoutFuture, stderrFuture).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Timeout or error awaiting stream completion", e);
        }

        int exitCode = process.exitValue();
        String stdout = stdoutBuilder.toString().trim();
        String stderr = stderrBuilder.toString().trim();
        boolean success = (exitCode == 0);
        String errorMsg =
                success
                        ? null
                        : (!stderr.isBlank()
                                ? stderr
                                : "Claude Code CLI exited with non-zero code " + exitCode);

        LOGGER.log(
                Level.INFO,
                "Claude Code CLI process completed: exitCode={0}, stdoutLength={1}, stderrLength={2}",
                new Object[] {exitCode, stdout.length(), stderr.length()});

        return new ClaudeCodeExecutionResult(
                exitCode, stdout, stderr, false, success, errorMsg);
    }
}
