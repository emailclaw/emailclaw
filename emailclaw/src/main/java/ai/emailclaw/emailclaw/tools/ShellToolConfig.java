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

import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;
import io.agentscope.harness.agent.workspace.PathPolicy;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable configuration record for {@link EmailclawShellExecuteTool}.
 *
 * <p>Bundles the underlying shell-capable filesystem backend, default project working directory,
 * rooted {@link PathPolicy} allow-list, and default command timeout so the tool can be constructed
 * via Pure DI during toolkit reconciliation.
 *
 * @param sandbox underlying shell-capable filesystem backend provided by HarnessAgent
 * @param projectRoot normalized absolute path of the active project base directory (default shell
 *     {@code pwd})
 * @param pathPolicy immutable allow-list of host root directories authorized for shell working
 *     directories
 * @param defaultTimeoutSeconds default shell command timeout in seconds when the LLM omits the
 *     {@code timeout} parameter
 */
public record ShellToolConfig(
        AbstractSandboxFilesystem sandbox,
        Path projectRoot,
        PathPolicy pathPolicy,
        int defaultTimeoutSeconds) {

    /** Fallback default timeout in seconds when configured timeout is non-positive. */
    public static final int FALLBACK_TIMEOUT_SECONDS = 60;

    /**
     * Compact constructor validating non-null dependencies and normalizing immutable values.
     */
    public ShellToolConfig {
        Objects.requireNonNull(sandbox, "sandbox must not be null");
        Objects.requireNonNull(projectRoot, "projectRoot must not be null");
        Objects.requireNonNull(pathPolicy, "pathPolicy must not be null");
        projectRoot = projectRoot.toAbsolutePath().normalize();
        if (defaultTimeoutSeconds <= 0) {
            defaultTimeoutSeconds = FALLBACK_TIMEOUT_SECONDS;
        }
    }
}
