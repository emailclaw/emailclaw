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

/**
 * Immutable execution result representing the outcome of an Antigravity CLI process invocation.
 *
 * @param exitCode The process exit code (0 for success, non-zero for error, -1 for initialization failures or timeouts)
 * @param stdout Standard output text captured from the process
 * @param stderr Standard error text captured from the process
 * @param timedOut Whether the CLI process timed out and was forcibly terminated
 * @param success Whether the execution completed successfully with exit code 0
 * @param error Detailed error message if the execution failed, or null if successful
 */
public record AntigravityExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean success,
        String error) {}
