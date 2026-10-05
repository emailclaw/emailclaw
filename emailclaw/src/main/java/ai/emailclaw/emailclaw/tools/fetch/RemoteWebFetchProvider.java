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
package ai.emailclaw.emailclaw.tools.fetch;

/**
 * Strategy interface for remote web fetch providers.
 */
public interface RemoteWebFetchProvider {

    /**
     * Gets the unique provider identifier (e.g. "Parallel_MCP", "Exa_MCP").
     *
     * @return provider name
     */
    String getProviderName();

    /**
     * Checks if this provider is currently available (e.g. not tripped by circuit breaker).
     *
     * @return true if available
     */
    boolean isAvailable();

    /**
     * Fetches the web page content as markdown or cleaned text.
     *
     * @param url target URL to fetch
     * @return extracted content, or null if fetch failed or was blocked
     */
    String fetch(String url);
}
