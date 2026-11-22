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

import java.nio.file.Path;

/**
 * Strategy interface for executing the Codex CLI process.
 *
 * <p>Enables clean abstraction for process spawning, test mocking, and cross-platform
 * process lifecycle management following the Pure DI pattern.
 */
public interface CodexProcessRunner {

    /**
     * Executes the Codex CLI non-interactively via {@code codex exec [OPTIONS] [PROMPT]}.
     *
     * @param cliPath Executable path or binary name for Codex (defaults to 'codex')
     * @param prompt User prompt or instruction to execute
     * @param workingDirectory Working directory context for the execution
     * @param model Optional model name override (passed via {@code -m})
     * @param effort Optional reasoning effort override (passed via {@code -c
     *     model_reasoning_effort="<effort>"})
     * @param sandbox Optional sandbox mode (passed via {@code -s}, e.g. 'read-only', 'workspace-write', 'danger-full-access')
     * @param timeoutSeconds Maximum execution duration before timing out
     * @param extraArgs Additional command-line flags or arguments
     * @param continueLastSession Whether to resume the most recent recorded session (resume --last)
     * @param conversation Optional previous Codex session/thread ID to resume via {@code codex exec
     *     resume [OPTIONS] <SESSION_ID> [PROMPT]}
     * @return Execution result containing exit code, stdout, stderr, and status
     */
    CodexExecutionResult execute(
            String cliPath,
            String prompt,
            Path workingDirectory,
            String model,
            String effort,
            String sandbox,
            int timeoutSeconds,
            String extraArgs,
            boolean continueLastSession,
            String conversation);

    /**
     * Overload for backward compatibility without explicit reasoning effort or conversation ID.
     */
    default CodexExecutionResult execute(
            String cliPath,
            String prompt,
            Path workingDirectory,
            String model,
            String sandbox,
            int timeoutSeconds,
            String extraArgs,
            boolean continueLastSession) {
        return execute(
                cliPath,
                prompt,
                workingDirectory,
                model,
                null,
                sandbox,
                timeoutSeconds,
                extraArgs,
                continueLastSession,
                null);
    }
}
