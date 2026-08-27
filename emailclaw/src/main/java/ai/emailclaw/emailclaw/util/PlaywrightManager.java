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

import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaywrightManager {

    private static final Logger LOGGER = Logger.getLogger(PlaywrightManager.class.getName());

    private static final double DEFAULT_TIMEOUT_MS = 15_000d;

    private static Playwright playwright = null;

    private static final ConcurrentMap<String, BrowserContext> browserContextMap =
            new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, Page> activePageMap = new ConcurrentHashMap<>();

    public static synchronized void initPlaywrightIfNeeded(String agentId) {
        if (playwright == null) {
            String executablePath = ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE;
            if (executablePath == null) {
                String installGuide = ChromeBrowserSupport.INSTALL_GUIDE;
                LOGGER.warning(
                        "Playwright initialization pre-check failed, reason: official Chrome/Edge"
                                + " not detected.");
                throw new RuntimeException(installGuide);
            }
            LOGGER.info("Initializing Playwright using physical browser: " + executablePath);
            playwright =
                    Playwright.create(
                            new Playwright.CreateOptions()
                                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try {
                                            for (Page p : activePageMap.values()) {
                                                if (p != null) p.close();
                                            }
                                            for (BrowserContext c : browserContextMap.values()) {
                                                if (c != null) c.close();
                                            }
                                            if (playwright != null) playwright.close();
                                        } catch (Exception ignored) {
                                        }
                                    }));
        }
        if (!browserContextMap.containsKey(agentId)) {
            String executablePath = ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE;
            Path userDataDir = AppHomeConstants.BROWSER_DATA_PATH.resolve(agentId);

            boolean headless =
                    System.getenv("EMAILCLAW_BROWSER_HEADLESS") != null
                            || GraphicsEnvironment.isHeadless();
            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(headless)
                            .setArgs(
                                    List.of(
                                            "--disable-blink-features=AutomationControlled",
                                            "--no-sandbox",
                                            "--disable-dev-shm-usage",
                                            "--disable-infobars",
                                            "--window-size=1920,1080",
                                            "--start-maximized",
                                            "--disable-extensions",
                                            "--disable-popup-blocking",
                                            "--profile-directory=Default",
                                            "--ignore-certificate-errors",
                                            "--disable-plugins-discovery"))
                            .setExecutablePath(Paths.get(executablePath))
                            .setUserAgent(
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                            + " (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .setViewportSize(1920, 1080)
                            .setIgnoreHTTPSErrors(true);
            LOGGER.log(
                    Level.INFO,
                    "Initializing browser persistent context: {0}",
                    userDataDir.toAbsolutePath());
            BrowserContext context =
                    playwright.chromium().launchPersistentContext(userDataDir, options);
            context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
            context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);
            browserContextMap.put(agentId, context);
        }
    }

    public static synchronized Page getOrCreateActivePage(String agentId) {
        Page page = activePageMap.get(agentId);
        BrowserContext ctx = browserContextMap.get(agentId);
        if (page == null || page.isClosed()) {
            if (ctx != null && !ctx.pages().isEmpty()) {
                page = ctx.pages().get(0);
                LOGGER.log(Level.INFO, "Reusing existing browser page: URL={0}", page.url());
            } else if (ctx != null) {
                page = ctx.newPage();
                LOGGER.log(Level.INFO, "Creating new browser page");
            }
            if (page != null) {
                activePageMap.put(agentId, page);
            }
        }
        return page;
    }

    public static synchronized Page getActivePageIfExists(String agentId) {
        return activePageMap.get(agentId);
    }

    /**
     * Create a fresh short-lived page inside the agent's persistent browser context. The caller
     * owns the returned page and must close it when done. Unlike {@link #getOrCreateActivePage},
     * the page is NOT registered as the agent's active page, so concurrent ephemeral pages never
     * interfere with each other or with the interactive page used by browser_use.
     *
     * <p>{@link #initPlaywrightIfNeeded} must have completed for the given agentId before calling
     * this method.
     */
    public static Page createEphemeralPage(String agentId) {
        BrowserContext ctx = browserContextMap.get(agentId);
        if (ctx == null) {
            throw new IllegalStateException("Browser context not initialized: agent=" + agentId);
        }
        return ctx.newPage();
    }

    /**
     * Reset the browser state after a connection-level failure (e.g. the browser process died or
     * its IPC pipe broke). Closes the broken global connection and clears all cached pages and
     * contexts so the next call re-initializes a fresh browser.
     *
     * @param agentId the agent triggering the reset (logged for diagnosis)
     */
    public static synchronized void reset(String agentId) {
        LOGGER.log(
                Level.INFO,
                "Resetting Playwright browser state after connection failure: agent={0}",
                agentId);
        for (Page p : activePageMap.values()) {
            try {
                if (p != null) p.close();
            } catch (Exception ignored) {
            }
        }
        activePageMap.clear();
        for (BrowserContext c : browserContextMap.values()) {
            try {
                if (c != null) c.close();
            } catch (Exception ignored) {
            }
        }
        browserContextMap.clear();
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignored) {
        }
        playwright = null;
    }
}
