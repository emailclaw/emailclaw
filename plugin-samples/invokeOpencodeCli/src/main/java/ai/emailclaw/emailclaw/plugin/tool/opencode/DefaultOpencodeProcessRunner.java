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
 * Industrial-grade default implementation of {@link OpencodeProcessRunner}.
 *
 * <p>Spawns the OpenCode CLI binary via {@link ProcessBuilder} with non-interactive flags
 * ({@code --cwd <path> -p <prompt> -f json -q}), concurrently capturing standard output and error streams
 * in non-blocking threads to prevent pipe deadlock, enforcing timeouts and graceful resource destruction.
 */
public class DefaultOpencodeProcessRunner implements OpencodeProcessRunner {

    private static final Logger LOGGER =
            Logger.getLogger(DefaultOpencodeProcessRunner.class.getName());

    public DefaultOpencodeProcessRunner() {}

    @Override
    public OpencodeExecutionResult execute(
            String cliPath,
            String prompt,
            Path workingDirectory,
            String model,
            String format,
            boolean quiet,
            int timeoutSeconds,
            String extraArgs) {
        List<String> command = new ArrayList<>();
        String effectiveCliPath =
                (cliPath != null && !cliPath.isBlank()) ? cliPath.trim() : "opencode";
        command.add(effectiveCliPath);

        if (workingDirectory != null) {
            command.add("--cwd");
            command.add(workingDirectory.toAbsolutePath().normalize().toString());
        }

        command.add("-p");
        command.add(prompt != null ? prompt : "");

        String effectiveFormat = (format != null && !format.isBlank()) ? format.trim() : "json";
        command.add("-f");
        command.add(effectiveFormat);

        if (quiet) {
            command.add("-q");
        }

        if (model != null && !model.isBlank()) {
            command.add("-m");
            command.add(model.trim());
        }

        if (extraArgs != null && !extraArgs.isBlank()) {
            for (String token : extraArgs.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    command.add(token);
                }
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            File dirFile = workingDirectory.toFile();
            if (dirFile.exists() && dirFile.isDirectory()) {
                processBuilder.directory(dirFile);
            }
        }

        processBuilder.environment().put("TERM", "dumb");
        processBuilder.environment().put("CI", "true");
        processBuilder.environment().put("NO_COLOR", "1");

        LOGGER.log(
                Level.INFO,
                "Launching OpenCode CLI process: executable={0}, workingDir={1}, timeout={2}s,"
                        + " format={3}, quiet={4}",
                new Object[] {effectiveCliPath, workingDirectory, timeoutSeconds, effectiveFormat, quiet});

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to launch OpenCode CLI process: " + effectiveCliPath,
                    e);
            return new OpencodeExecutionResult(
                    -1,
                    "",
                    e.getMessage(),
                    false,
                    false,
                    "Failed to start OpenCode CLI process ('"
                            + effectiveCliPath
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
                                        "Error reading stdout from OpenCode CLI process",
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
                                        "Error reading stderr from OpenCode CLI process",
                                        e);
                            }
                        });

        boolean completed;
        try {
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "OpenCode CLI execution thread was interrupted", e);
            process.destroyForcibly();
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            return new OpencodeExecutionResult(
                    -1,
                    stdoutBuilder.toString(),
                    stderrBuilder.toString(),
                    false,
                    false,
                    "OpenCode CLI execution was interrupted.");
        }

        if (!completed) {
            LOGGER.log(
                    Level.WARNING,
                    "OpenCode CLI execution exceeded timeout of {0} seconds, destroying process",
                    timeoutSeconds);
            process.destroyForcibly();
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            return new OpencodeExecutionResult(
                    -1,
                    stdoutBuilder.toString(),
                    stderrBuilder.toString(),
                    true,
                    false,
                    "OpenCode CLI execution timed out after " + timeoutSeconds + " seconds.");
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
                                : "OpenCode CLI exited with non-zero code " + exitCode);

        LOGGER.log(
                Level.INFO,
                "OpenCode CLI process completed: exitCode={0}, stdoutLength={1}, stderrLength={2}",
                new Object[] {exitCode, stdout.length(), stderr.length()});

        return new OpencodeExecutionResult(exitCode, stdout, stderr, false, success, errorMsg);
    }
}
