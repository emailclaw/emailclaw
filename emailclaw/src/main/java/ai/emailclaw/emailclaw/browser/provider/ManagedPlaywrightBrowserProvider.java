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
package ai.emailclaw.emailclaw.browser.provider;

import ai.emailclaw.emailclaw.browser.spi.BrowserSessionProvider;
import ai.emailclaw.emailclaw.util.BrowserStealthSupport;
import ai.emailclaw.emailclaw.util.ChromeBrowserSupport;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default browser session provider executing local Chromium/Edge instances with isolated persistent contexts.
 *
 * <p>Each agent maintains an isolated profile directory under the configured browser data root,
 * fortified with anti-bot stealth scripts. TLS certificate validation is enforced by default to prevent
 * session eavesdropping, and can be relaxed via configuration if operating in intranet environments.
 */
public class ManagedPlaywrightBrowserProvider implements BrowserSessionProvider {

    private static final Logger LOGGER =
            Logger.getLogger(ManagedPlaywrightBrowserProvider.class.getName());

    public static final String PROVIDER_ID = "managed_playwright";

    private static final double DEFAULT_TIMEOUT_MS = 15_000d;

    private final Path browserDataDir;

    private final Object mutex = new Object();

    private Playwright playwright = null;

    private final ConcurrentMap<String, BrowserContext> browserContextMap =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Page> activePageMap = new ConcurrentHashMap<>();

    private volatile boolean closed = false;

    /**
     * Constructs a new managed Playwright browser session provider.
     *
     * @param browserDataDir root directory for storing persistent agent browser contexts
     */
    public ManagedPlaywrightBrowserProvider(Path browserDataDir) {
        this.browserDataDir =
                Objects.requireNonNull(browserDataDir, "browserDataDir must not be null");
        LOGGER.log(
                Level.INFO,
                "Initialized ManagedPlaywrightBrowserProvider with data directory: {0}",
                browserDataDir.toAbsolutePath());
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isAvailable() {
        return ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE != null;
    }

    @Override
    public Object getMutex() {
        return mutex;
    }

    /**
     * Initializes the underlying Playwright driver instance and persistent context for the specified agent.
     *
     * @param agentId unique agent identifier
     */
    public void ensureInitialized(String agentId) {
        synchronized (mutex) {
            if (closed) {
                throw new IllegalStateException("ManagedPlaywrightBrowserProvider has been closed");
            }
            if (playwright == null) {
                String executablePath = ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE;
                if (executablePath == null) {
                    String installGuide = ChromeBrowserSupport.INSTALL_GUIDE;
                    LOGGER.warning(
                            "Playwright initialization pre-check failed, reason: official"
                                    + " Chrome/Edge not detected.");
                    throw new RuntimeException(installGuide);
                }
                LOGGER.log(
                        Level.INFO,
                        "Initializing Playwright driver using physical browser: {0}",
                        executablePath);
                playwright =
                        Playwright.create(
                                new Playwright.CreateOptions()
                                        .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            }
            if (!browserContextMap.containsKey(agentId)) {
                String executablePath = ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE;
                Path userDataDir = browserDataDir.resolve(agentId);

                boolean headless =
                        System.getenv("EMAILCLAW_BROWSER_HEADLESS") != null
                                || GraphicsEnvironment.isHeadless();

                boolean ignoreCertErrors =
                        Boolean.parseBoolean(
                                System.getProperty(
                                        "emailclaw.browser.ignore_cert_errors",
                                        System.getenv("EMAILCLAW_BROWSER_IGNORE_CERT_ERRORS")
                                                        != null
                                                ? System.getenv(
                                                        "EMAILCLAW_BROWSER_IGNORE_CERT_ERRORS")
                                                : "false"));

                List<String> launchArgs =
                        new ArrayList<>(
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
                                        "--disable-plugins-discovery",
                                        "--lang=en-US,en"));

                if (ignoreCertErrors) {
                    LOGGER.warning(
                            "Browser TLS certificate error validation is disabled (insecure) via"
                                    + " configuration.");
                    launchArgs.add("--ignore-certificate-errors");
                } else {
                    LOGGER.info("Browser TLS certificate verification enforced (secure default).");
                }

                BrowserType.LaunchPersistentContextOptions options =
                        new BrowserType.LaunchPersistentContextOptions()
                                .setHeadless(headless)
                                .setArgs(launchArgs)
                                .setExecutablePath(Paths.get(executablePath))
                                .setViewportSize(1920, 1080)
                                .setIgnoreHTTPSErrors(ignoreCertErrors);

                String customUserAgent = System.getenv("EMAILCLAW_BROWSER_USER_AGENT");
                if (customUserAgent != null && !customUserAgent.isBlank()) {
                    LOGGER.info("Using configured custom browser User-Agent");
                    options.setUserAgent(customUserAgent.trim());
                }

                LOGGER.log(
                        Level.INFO,
                        "Initializing browser persistent context: {0}",
                        userDataDir.toAbsolutePath());
                BrowserContext context =
                        playwright.chromium().launchPersistentContext(userDataDir, options);
                BrowserStealthSupport.applyStealth(context);
                context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
                context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);
                browserContextMap.put(agentId, context);
            }
        }
    }

    @Override
    public Page getOrCreateActivePage(String agentId) {
        synchronized (mutex) {
            ensureInitialized(agentId);
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
    }

    @Override
    public Page getActivePageIfExists(String agentId) {
        return activePageMap.get(agentId);
    }

    @Override
    public Page createEphemeralPage(String agentId) {
        synchronized (mutex) {
            ensureInitialized(agentId);
            BrowserContext ctx = browserContextMap.get(agentId);
            if (ctx == null) {
                throw new IllegalStateException(
                        "Browser context not initialized: agent=" + agentId);
            }
            return ctx.newPage();
        }
    }

    @Override
    public void reset(String agentId) {
        synchronized (mutex) {
            LOGGER.log(
                    Level.INFO,
                    "Resetting Playwright browser state after connection failure: agent={0}",
                    agentId);
            for (Page p : activePageMap.values()) {
                try {
                    if (p != null) {
                        p.close();
                    }
                } catch (Exception ignored) {
                }
            }
            activePageMap.clear();
            for (BrowserContext c : browserContextMap.values()) {
                try {
                    if (c != null) {
                        c.close();
                    }
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

    @Override
    public void close() {
        synchronized (mutex) {
            if (closed) {
                return;
            }
            closed = true;
            LOGGER.info("Closing ManagedPlaywrightBrowserProvider and terminating driver...");
            for (Page p : activePageMap.values()) {
                try {
                    if (p != null) {
                        p.close();
                    }
                } catch (Exception ignored) {
                }
            }
            activePageMap.clear();
            for (BrowserContext c : browserContextMap.values()) {
                try {
                    if (c != null) {
                        c.close();
                    }
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
}
