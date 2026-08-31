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
package ai.emailclaw.emailclaw.plugin;

/**
 * Abstract plugin base class of Frontend type.
 *
 * <p>Suitable for plugins containing frontend resources, frontend entries, or frontend extensions.
 * This plugin can register a frontend entry path with the host and optionally provide backend API interfaces.
 */
public abstract class AbstractFrontendPlugin extends AbstractEmailclawPlugin {

    @Override
    public final void register(PluginRegistry registry) {
        String frontendId = getFrontendId();
        String frontendEntry = getFrontendEntry();

        if (frontendId == null || frontendId.isBlank()) {
            throw new IllegalArgumentException("Frontend ID cannot be empty");
        }
        if (frontendEntry == null || frontendEntry.isBlank()) {
            throw new IllegalArgumentException("Frontend entry cannot be empty");
        }

        registry.registerFrontendEntry(frontendId, frontendEntry);
        logger.info("Successfully registered Frontend plugin: " + frontendId);

        String apiPathPrefix = getApiPathPrefix();
        Object apiHandler = createApiHandler();
        if (apiPathPrefix != null && !apiPathPrefix.isBlank() && apiHandler != null) {
            registry.registerCustomApi(apiPathPrefix, apiHandler);
            logger.info("Successfully registered Frontend plugin backend API: " + apiPathPrefix);
        }

        onRegister(registry);
    }

    /**
     * Returns the unique ID of the Frontend plugin, defaults to the plugin ID.
     */
    protected String getFrontendId() {
        return id();
    }

    /**
     * Returns the frontend entry file path, for example "index.html" or "dist/index.html".
     */
    protected abstract String getFrontendEntry();

    /**
     * Returns the path prefix for the backend API, for example "/api/my-plugin".
     * <p>Returns null if there is no need to register a backend interface.
     */
    protected String getApiPathPrefix() {
        return null;
    }

    /**
     * Creates and returns the backend API handler.
     * <p>Returns null if there is no need for a backend interface.
     */
    protected Object createApiHandler() {
        return null;
    }

    /**
     * Subclasses can override this method if additional registration logic needs to be executed.
     */
    protected void onRegister(PluginRegistry registry) {
        // No additional operations by default
    }
}
