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

import java.nio.file.Path;

/**
 * Strategy interface for executing OpenCode CLI commands.
 *
 * <p>Enables clean abstraction and dependency injection (Pure DI) so that process execution
 * strategies can be tested or swapped without modifying core tool logic.
 */
public interface OpencodeProcessRunner {

    /**
     * Executes the OpenCode CLI process with the specified command arguments.
     *
     * @param cliPath Path or binary name of the OpenCode CLI (default 'opencode')
     * @param prompt The prompt passed to the CLI via the {@code -p} flag
     * @param workingDirectory The directory in which the process should execute (passed via {@code --cwd})
     * @param model Optional model override parameter passed via {@code -m}
     * @param format Optional output format passed via {@code -f} (default 'json')
     * @param quiet Whether to pass {@code -q} to suppress interactive spinners and progress animations
     * @param timeoutSeconds Execution timeout limit in seconds
     * @param extraArgs Optional additional CLI flags or arguments
     * @return An immutable {@link OpencodeExecutionResult} capturing the execution status and output
     */
    OpencodeExecutionResult execute(
            String cliPath,
            String prompt,
            Path workingDirectory,
            String model,
            String format,
            boolean quiet,
            int timeoutSeconds,
            String extraArgs);
}
