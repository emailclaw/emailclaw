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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Industrial-grade default implementation of {@link CodexProcessRunner}.
 *
 * <p>Spawns the Codex CLI binary via {@link ProcessBuilder} with {@code exec} command
 * ({@code codex exec [OPTIONS] [PROMPT]}), capturing standard output and error streams
 * in non-blocking threads to prevent pipe deadlock, enforcing timeouts and graceful resource destruction.
 */
public class DefaultCodexProcessRunner implements CodexProcessRunner {

    private static final Logger LOGGER =
            Logger.getLogger(DefaultCodexProcessRunner.class.getName());

    private final CodexPathResolver pathResolver;

    public DefaultCodexProcessRunner() {
        this(new DefaultCodexPathResolver());
    }

    public DefaultCodexProcessRunner(CodexPathResolver pathResolver) {
        this.pathResolver = pathResolver != null ? pathResolver : new DefaultCodexPathResolver();
    }

    /**
     * Builds the command arguments for {@code codex exec}.
     *
     * <p>Session flag precedence:
     * <ol>
     *   <li>If {@code conversation} is non-blank, generates {@code codex exec resume [OPTIONS]
     *       <SESSION_ID> [PROMPT]} without {@code --last}.</li>
     *   <li>Otherwise, if {@code continueLastSession} is true (and {@code resume} is not already
     *       present in {@code extraArgs}), generates {@code codex exec resume --last [OPTIONS]
     *       [PROMPT]}.</li>
     *   <li>Otherwise, generates {@code codex exec [OPTIONS] [PROMPT]} for a new session.</li>
     * </ol>
     *
     * <p>Automatically appends {@code --json} unless already specified in {@code extraArgs} so
     * Codex emits structured JSONL events containing {@code thread_id}. Omits {@code -s}/{@code
     * --sandbox} whenever resuming, as {@code codex exec resume} rejects sandbox flags.
     */
    List<String> buildCommand(
            String cliPath,
            String prompt,
            String model,
            String effort,
            String sandbox,
            String extraArgs,
            boolean continueLastSession,
            String conversation) {
        List<String> command = new ArrayList<>();
        String effectiveCliPath =
                (cliPath != null && !cliPath.isBlank()) ? cliPath.trim() : "codex";
        String resolvedCliPath = pathResolver.resolveCliPath(effectiveCliPath);
        command.add(resolvedCliPath);
        command.add("exec");

        String normalizedConversation =
                (conversation != null && !conversation.isBlank()) ? conversation.trim() : null;

        boolean hasResume = false;
        boolean hasLast = false;
        boolean hasSkipGitCheck = false;
        boolean hasSandbox = false;
        boolean hasModel = false;
        boolean hasEffort = false;
        boolean hasJson = false;
        List<String> extraTokens = new ArrayList<>();

        if (extraArgs != null && !extraArgs.isBlank()) {
            for (String token : extraArgs.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    if ("resume".equals(token)) {
                        hasResume = true;
                    }
                    if ("--last".equals(token)) {
                        hasLast = true;
                    }
                    if ("--skip-git-repo-check".equals(token)) {
                        hasSkipGitCheck = true;
                    }
                    if ("-s".equals(token)
                            || "--sandbox".equals(token)
                            || token.startsWith("--sandbox=")) {
                        hasSandbox = true;
                    }
                    if ("-m".equals(token)
                            || "--model".equals(token)
                            || token.startsWith("--model=")) {
                        hasModel = true;
                    }
                    if (token.startsWith("model_reasoning_effort=")
                            || token.contains("model_reasoning_effort=")) {
                        hasEffort = true;
                    }
                    if ("--json".equals(token)) {
                        hasJson = true;
                    }
                    extraTokens.add(token);
                }
            }
        }

        if (normalizedConversation != null && hasLast) {
            LOGGER.warning(
                    "Both explicit conversation ID and --last were supplied; prioritizing"
                            + " conversation ID and stripping conflicting --last from extra_args");
            extraTokens.removeIf("--last"::equals);
            hasLast = false;
        }

        boolean isResume = normalizedConversation != null || hasResume || continueLastSession;

        if (normalizedConversation != null) {
            if (!hasResume) {
                command.add("resume");
            }
        } else if (continueLastSession && !hasResume) {
            command.add("resume");
            if (!hasLast) {
                command.add("--last");
            }
        }

        if (!hasSkipGitCheck) {
            command.add("--skip-git-repo-check");
        }

        if (!hasJson) {
            command.add("--json");
        }

        if (model != null && !model.isBlank() && !hasModel) {
            command.add("-m");
            command.add(model.trim());
        }

        if (effort != null && !effort.isBlank() && !hasEffort) {
            command.add("-c");
            command.add("model_reasoning_effort=\"" + effort.trim() + "\"");
        }

        // -s / --sandbox is only valid for new executions (codex exec), not for 'codex exec resume'
        if (!isResume && sandbox != null && !sandbox.isBlank() && !hasSandbox) {
            command.add("-s");
            command.add(sandbox.trim());
        }

        command.addAll(extraTokens);

        if (normalizedConversation != null) {
            command.add(normalizedConversation);
        }

        if (prompt != null && !prompt.isBlank()) {
            command.add(prompt);
        }
        return command;
    }

    /**
     * Backward-compatible overload of {@link #buildCommand} without explicit effort or
     * conversation ID.
     */
    List<String> buildCommand(
            String cliPath,
            String prompt,
            String model,
            String sandbox,
            String extraArgs,
            boolean continueLastSession) {
        return buildCommand(
                cliPath, prompt, model, null, sandbox, extraArgs, continueLastSession, null);
    }

    @Override
    public CodexExecutionResult execute(
            String cliPath,
            String prompt,
            Path workingDirectory,
            String model,
            String effort,
            String sandbox,
            int timeoutSeconds,
            String extraArgs,
            boolean continueLastSession,
            String conversation) {
        List<String> command =
                buildCommand(
                        cliPath,
                        prompt,
                        model,
                        effort,
                        sandbox,
                        extraArgs,
                        continueLastSession,
                        conversation);

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
        enrichSubprocessPath(processBuilder, command.get(0));

        LOGGER.log(
                Level.INFO,
                "Launching Codex CLI process: command={0}, workingDir={1}, timeout={2}s,"
                        + " model={3}, effort={4}, continueLastSession={5}, conversation={6}",
                new Object[] {
                    command,
                    workingDirectory,
                    timeoutSeconds,
                    model,
                    effort,
                    continueLastSession,
                    conversation
                });

        Process process;
        try {
            process = processBuilder.start();
            try {
                // Ensure stdin pipe is immediately closed so subprocess receives EOF on stdin
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to launch Codex CLI process: " + command.get(0), e);
            String diagnostic = buildLaunchFailureDiagnostic(command.get(0), e);
            return new CodexExecutionResult(-1, "", diagnostic, false, false, diagnostic);
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
                                        "Error reading stdout from Codex CLI process",
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
                                        "Error reading stderr from Codex CLI process",
                                        e);
                            }
                        });

        boolean completed;
        try {
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Codex CLI execution thread was interrupted", e);
            process.destroyForcibly();
            try {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
            } catch (Exception ignored) {
            }
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            return new CodexExecutionResult(
                    -1,
                    stdoutBuilder.toString(),
                    stderrBuilder.toString(),
                    false,
                    false,
                    "Codex CLI execution was interrupted.");
        }

        if (!completed) {
            LOGGER.log(
                    Level.WARNING,
                    "Codex CLI execution exceeded timeout of {0} seconds, destroying process",
                    timeoutSeconds);
            process.destroyForcibly();
            try {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
            } catch (Exception ignored) {
            }
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            return new CodexExecutionResult(
                    -1,
                    stdoutBuilder.toString(),
                    stderrBuilder.toString(),
                    true,
                    false,
                    "Codex CLI execution timed out after " + timeoutSeconds + " seconds.");
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
                                : "Codex CLI exited with non-zero code " + exitCode);

        LOGGER.log(
                Level.INFO,
                "Codex CLI process completed: exitCode={0}, stdoutLength={1}, stderrLength={2}",
                new Object[] {exitCode, stdout.length(), stderr.length()});

        return new CodexExecutionResult(exitCode, stdout, stderr, false, success, errorMsg);
    }

    /**
     * Enriches the subprocess environment {@code PATH} with essential directories such as
     * the parent directory of the resolved executable and {@code $HOME/.local/bin},
     * ensuring headless or daemon executions locate companion utilities.
     *
     * @param processBuilder ProcessBuilder instance being prepared
     * @param executablePath Path of the resolved executable binary
     */
    private void enrichSubprocessPath(ProcessBuilder processBuilder, String executablePath) {
        String existingPath = processBuilder.environment().get("PATH");
        if (existingPath == null) {
            existingPath = System.getenv("PATH");
            if (existingPath == null) {
                existingPath = "";
            }
        }

        String userHome = System.getProperty("user.home");
        List<String> extraDirs = new ArrayList<>();

        if (executablePath != null
                && (executablePath.contains("/") || executablePath.contains("\\"))) {
            try {
                Path parent = Paths.get(executablePath).toAbsolutePath().getParent();
                if (parent != null && Files.isDirectory(parent)) {
                    extraDirs.add(parent.normalize().toString());
                }
            } catch (Exception ignored) {
            }
        }

        if (userHome != null && !userHome.isBlank()) {
            Path localBin = Paths.get(userHome, ".local", "bin");
            if (Files.isDirectory(localBin)) {
                extraDirs.add(localBin.toAbsolutePath().normalize().toString());
            }
        }

        if (!extraDirs.isEmpty()) {
            String pathSeparator = File.pathSeparator;
            StringBuilder newPath = new StringBuilder();
            for (String extraDir : extraDirs) {
                if (!existingPath.contains(extraDir)) {
                    newPath.append(extraDir).append(pathSeparator);
                }
            }
            newPath.append(existingPath);
            processBuilder.environment().put("PATH", newPath.toString());
            LOGGER.log(Level.FINE, "Enriched subprocess PATH: {0}", newPath);
        }
    }

    /**
     * Generates a clear, actionable diagnostic message when process spawning fails with {@link IOException}.
     *
     * @param binaryPath Binary path that failed to launch
     * @param ex Caught IOException
     * @return Formatted diagnostic guidance
     */
    private String buildLaunchFailureDiagnostic(String binaryPath, IOException ex) {
        String errorMsg = ex.getMessage() != null ? ex.getMessage() : "";
        if (errorMsg.contains("error=2") || errorMsg.contains("No such file")) {
            return "Failed to start Codex CLI process ('"
                    + binaryPath
                    + "'): Executable not found. Please ensure Codex CLI is installed (e.g. via"
                    + " 'curl -fsSL https://chatgpt.com/codex/install.sh | sh' or 'npm install -g"
                    + " @openai/codex'), ensure $HOME/.local/bin is in your PATH, or configure the"
                    + " 'cli_path' setting.";
        }
        return "Failed to start Codex CLI process ('" + binaryPath + "'): " + errorMsg;
    }
}
