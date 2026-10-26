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

/**
 * Strategy interface for discovering and resolving the OpenCode CLI (opencode) binary path.
 *
 * <p>Enables clean abstraction for executable path resolution, user home tilde expansion,
 * PATH lookups, and fallback candidate discovery adhering to the Pure DI pattern.
 */
public interface OpencodePathResolver {

    /**
     * Resolves the effective executable path for the OpenCode CLI binary.
     *
     * <p>Handles tilde (~/...) expansion, environment variable overrides, PATH scanning,
     * and fallback probing for standard installation locations (such as {@code $HOME/.opencode/bin/opencode}).
     *
     * @param configuredPath Optional configured or user-supplied CLI path (may be null, blank, '~' prefixed, or bare 'opencode')
     * @return The absolute path to the discovered executable, or the fallback command name if not located
     */
    String resolveCliPath(String configuredPath);
}
