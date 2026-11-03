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
package ai.emailclaw.emailclaw.util;

import ai.emailclaw.emailclaw.browser.BrowserSessionManager;
import ai.emailclaw.emailclaw.storage.AppPaths;
import com.microsoft.playwright.Page;
import java.nio.file.Path;

/**
 * Legacy static browser manager adapter delegating to instantiated {@link BrowserSessionManager}.
 *
 * @deprecated Use instantiated {@link BrowserSessionManager} injected via Pure DI instead.
 */
@Deprecated(since = "26.10.23", forRemoval = true)
public final class PlaywrightManager {

    /**
     * Global synchronization mutex for all Playwright operations.
     */
    public static final Object PLAYWRIGHT_MUTEX = BrowserSessionManager.getDefaultMutex();

    private PlaywrightManager() {}

    /**
     * Sets the root browser user data directory by initializing a new default manager.
     *
     * @param dir the browser data root directory
     */
    public static void setBrowserDataDir(Path dir) {
        if (dir != null) {
            BrowserSessionManager.setDefault(new BrowserSessionManager(dir));
        }
    }

    /**
     * Gets the root browser user data directory.
     *
     * @return the browser data root directory
     */
    public static Path getBrowserDataDir() {
        return AppPaths.fromDefault().browserDataDir;
    }

    /**
     * Initializes the browser driver and context if needed for the given agent.
     *
     * @param agentId unique agent identifier
     */
    public static void initPlaywrightIfNeeded(String agentId) {
        BrowserSessionManager.getDefault().getOrCreateActivePage(agentId);
    }

    /**
     * Gets or creates the primary active browser page for the specified agent.
     *
     * @param agentId unique agent identifier
     * @return active Playwright Page
     */
    public static Page getOrCreateActivePage(String agentId) {
        return BrowserSessionManager.getDefault().getOrCreateActivePage(agentId);
    }

    /**
     * Gets the active page for the specified agent if one is currently open.
     *
     * @param agentId unique agent identifier
     * @return active Page or null
     */
    public static Page getActivePageIfExists(String agentId) {
        return BrowserSessionManager.getDefault().getActivePageIfExists(agentId);
    }

    /**
     * Creates a fresh short-lived ephemeral page for one-off operations.
     *
     * @param agentId unique agent identifier
     * @return ephemeral Page
     */
    public static Page createEphemeralPage(String agentId) {
        return BrowserSessionManager.getDefault().createEphemeralPage(agentId);
    }

    /**
     * Resets the browser state for the agent after connection-level failures.
     *
     * @param agentId unique agent identifier
     */
    public static void reset(String agentId) {
        BrowserSessionManager.getDefault().reset(agentId);
    }
}
