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
package ai.emailclaw.emailclaw.browser;

import ai.emailclaw.emailclaw.browser.provider.ManagedPlaywrightBrowserProvider;
import ai.emailclaw.emailclaw.browser.spi.BrowserSessionProvider;
import ai.emailclaw.emailclaw.storage.AppPaths;
import com.microsoft.playwright.Page;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Instantiated browser session manager coordinating browser backends and agent sessions via Provider SPI.
 *
 * <p>Designed under Pure DI principles: components receive this manager instance via constructor injection.
 * For backward compatibility with static callers, a globally accessible default instance is also maintained.
 */
public class BrowserSessionManager implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(BrowserSessionManager.class.getName());

    private static final Object FALLBACK_MUTEX = new Object();

    private static volatile BrowserSessionManager defaultInstance;

    private final ConcurrentMap<String, BrowserSessionProvider> providers =
            new ConcurrentHashMap<>();

    private volatile String defaultProviderId;

    private Thread shutdownHook;

    /**
     * Constructs a manager using the default managed Playwright provider with the given data root directory.
     *
     * @param browserDataDir root path for persistent browser profiles
     */
    public BrowserSessionManager(Path browserDataDir) {
        this(new ManagedPlaywrightBrowserProvider(browserDataDir));
    }

    /**
     * Constructs a manager configured with an initial default browser session provider.
     *
     * @param defaultProvider primary browser session provider
     */
    public BrowserSessionManager(BrowserSessionProvider defaultProvider) {
        Objects.requireNonNull(defaultProvider, "defaultProvider must not be null");
        this.defaultProviderId = defaultProvider.id();
        registerProvider(defaultProvider);
        registerShutdownHook();
        setDefault(this);
        LOGGER.log(
                Level.INFO,
                "BrowserSessionManager initialized with default provider: {0}",
                defaultProvider.id());
    }

    /**
     * Retrieves the global default BrowserSessionManager instance.
     *
     * @return active default manager
     */
    public static BrowserSessionManager getDefault() {
        if (defaultInstance == null) {
            synchronized (BrowserSessionManager.class) {
                if (defaultInstance == null) {
                    Path fallbackDir = AppPaths.fromDefault().browserDataDir;
                    defaultInstance = new BrowserSessionManager(fallbackDir);
                }
            }
        }
        return defaultInstance;
    }

    /**
     * Sets the global default BrowserSessionManager instance.
     *
     * @param manager manager instance to publish as default
     */
    public static void setDefault(BrowserSessionManager manager) {
        defaultInstance = manager;
    }

    /**
     * Returns the global default synchronization mutex for cross-thread driver coordination.
     *
     * @return synchronization lock object
     */
    public static Object getDefaultMutex() {
        BrowserSessionManager current = defaultInstance;
        return current != null ? current.getMutex() : FALLBACK_MUTEX;
    }

    /**
     * Registers a new browser session provider into the manager.
     *
     * @param provider provider implementation
     */
    public void registerProvider(BrowserSessionProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        providers.put(provider.id(), provider);
        LOGGER.log(Level.INFO, "Registered BrowserSessionProvider: {0}", provider.id());
    }

    /**
     * Unregisters and closes a browser session provider.
     *
     * @param providerId unique provider identifier
     */
    public void unregisterProvider(String providerId) {
        if (providerId == null) {
            return;
        }
        BrowserSessionProvider removed = providers.remove(providerId);
        if (removed != null) {
            LOGGER.log(Level.INFO, "Unregistering BrowserSessionProvider: {0}", providerId);
            try {
                removed.close();
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Error occurred while closing unregistered provider: " + providerId,
                        e);
            }
        }
    }

    /**
     * Finds a registered provider by its identifier.
     *
     * @param providerId unique provider identifier
     * @return Optional containing the provider if found
     */
    public Optional<BrowserSessionProvider> getProvider(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(providerId));
    }

    /**
     * Retrieves the primary default browser session provider.
     *
     * @return default browser provider
     */
    public BrowserSessionProvider getDefaultProvider() {
        BrowserSessionProvider provider = providers.get(defaultProviderId);
        if (provider == null) {
            provider = providers.values().stream().findFirst().orElse(null);
        }
        if (provider == null) {
            throw new IllegalStateException("No BrowserSessionProvider registered in manager");
        }
        return provider;
    }

    /**
     * Sets the primary default provider identifier.
     *
     * @param defaultProviderId provider identifier
     */
    public void setDefaultProviderId(String defaultProviderId) {
        this.defaultProviderId =
                Objects.requireNonNull(defaultProviderId, "defaultProviderId must not be null");
        LOGGER.log(Level.INFO, "Updated default BrowserSessionProvider ID: {0}", defaultProviderId);
    }

    /**
     * Gets the primary default provider identifier.
     *
     * @return default provider identifier
     */
    public String getDefaultProviderId() {
        return defaultProviderId;
    }

    /**
     * Returns an unmodifiable snapshot view of all registered providers.
     *
     * @return map of provider IDs to providers
     */
    public Map<String, BrowserSessionProvider> listProviders() {
        return Collections.unmodifiableMap(providers);
    }

    /**
     * Retrieves or initializes the active page for the specified agent using the default provider.
     *
     * @param agentId unique agent identifier
     * @return active Playwright Page
     */
    public Page getOrCreateActivePage(String agentId) {
        return getDefaultProvider().getOrCreateActivePage(agentId);
    }

    /**
     * Retrieves the active page for the specified agent if one is currently open.
     *
     * @param agentId unique agent identifier
     * @return active Page or null
     */
    public Page getActivePageIfExists(String agentId) {
        return getDefaultProvider().getActivePageIfExists(agentId);
    }

    /**
     * Creates a fresh ephemeral page for short-lived tasks via the default provider.
     *
     * @param agentId unique agent identifier
     * @return new ephemeral Page
     */
    public Page createEphemeralPage(String agentId) {
        return getDefaultProvider().createEphemeralPage(agentId);
    }

    /**
     * Resets the browser state for the specified agent after a connection failure or crash.
     *
     * @param agentId unique agent identifier
     */
    public void reset(String agentId) {
        getDefaultProvider().reset(agentId);
    }

    /**
     * Returns the synchronization lock object for coordinating operations on the default provider.
     *
     * @return synchronization lock object
     */
    public Object getMutex() {
        return getDefaultProvider().getMutex();
    }

    /**
     * Registers a JVM shutdown hook to cleanly close all managed browser contexts on process exit.
     */
    public synchronized void registerShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        shutdownHook =
                new Thread(
                        () -> {
                            try {
                                close();
                            } catch (Exception ignored) {
                            }
                        },
                        "emailclaw-browser-session-manager-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public void close() {
        LOGGER.info("Closing BrowserSessionManager and all registered providers...");
        for (Map.Entry<String, BrowserSessionProvider> entry : providers.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to cleanly close browser provider: " + entry.getKey(),
                        e);
            }
        }
        providers.clear();
    }
}
