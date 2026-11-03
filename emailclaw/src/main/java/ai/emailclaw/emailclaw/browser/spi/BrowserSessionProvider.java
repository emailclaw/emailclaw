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
package ai.emailclaw.emailclaw.browser.spi;

import com.microsoft.playwright.Page;

/**
 * Service Provider Interface (SPI) for browser session management backends.
 *
 * <p>Implementations manage the lifecycle of underlying browser processes, persistent or
 * remote browser contexts, and interactive/ephemeral pages for agents.
 */
public interface BrowserSessionProvider extends AutoCloseable {

    /**
     * Unique identifier of this browser session provider.
     *
     * @return provider ID
     */
    String id();

    /**
     * Checks if this provider is supported and available in the current runtime environment.
     *
     * @return true if available
     */
    boolean isAvailable();

    /**
     * Retrieves or initializes the primary active page for the specified agent.
     *
     * @param agentId unique agent identifier
     * @return active Playwright Page
     */
    Page getOrCreateActivePage(String agentId);

    /**
     * Retrieves the active page for the specified agent if one is currently open.
     *
     * @param agentId unique agent identifier
     * @return active Page, or null if no page is open
     */
    Page getActivePageIfExists(String agentId);

    /**
     * Creates a fresh, isolated ephemeral page for short-lived tasks (e.g., text extraction).
     * The caller is responsible for closing the returned page when finished.
     *
     * @param agentId unique agent identifier
     * @return new ephemeral Page
     */
    Page createEphemeralPage(String agentId);

    /**
     * Resets the browser state for the specified agent after a connection failure or crash.
     *
     * @param agentId unique agent identifier
     */
    void reset(String agentId);

    /**
     * Synchronization mutex for coordinating operations across threads on this provider's driver connection.
     *
     * @return synchronization lock object
     */
    Object getMutex();

    /**
     * Shuts down all active contexts and closes any underlying browser resources.
     */
    @Override
    void close();
}
