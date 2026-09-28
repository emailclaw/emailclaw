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

import java.nio.file.Path;

/**
 * Strategy interface for executing the Claude Code CLI process.
 *
 * <p>Enables clean abstraction for process spawning, test mocking, and cross-platform
 * process lifecycle management following the Pure DI pattern.
 */
public interface ClaudeCodeProcessRunner {

    /**
     * Executes the Claude Code CLI non-interactively via {@code claude -p [OPTIONS] [PROMPT]}.
     *
     * @param cliPath Executable path or binary name for Claude Code (defaults to 'claude')
     * @param prompt User prompt or instruction to execute
     * @param workingDirectory Working directory context for the execution
     * @param model Optional model name override (passed via {@code --model})
     * @param permissionMode Optional permission mode (passed via {@code --permission-mode})
     * @param timeoutSeconds Maximum execution duration before timing out
     * @param extraArgs Additional command-line flags or arguments
     * @param continueLastSession Whether to resume the most recent recorded session (-c / --continue)
     * @return Execution result containing exit code, stdout, stderr, and status
     */
    ClaudeCodeExecutionResult execute(
            String cliPath,
            String prompt,
            Path workingDirectory,
            String model,
            String permissionMode,
            int timeoutSeconds,
            String extraArgs,
            boolean continueLastSession);
}
