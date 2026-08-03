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
 * Abstract plugin base class of lifecycle Hook type.
 * It is recommended for developers to directly inherit this class to execute custom code logic when Emailclaw starts or shuts down, such as preloading data, clearing cache, etc.
 */
public abstract class AbstractHookPlugin extends AbstractEmailclawPlugin {

    @Override
    public final void register(PluginRegistry registry) {
        String hookName = getHookName();
        if (hookName == null || hookName.isBlank()) {
            throw new IllegalArgumentException("Hook name cannot be empty");
        }

        Runnable startup = getStartupHook();
        if (startup != null) {
            registry.registerStartupHook(hookName, startup);
            logger.info("Successfully registered Startup Hook: " + hookName);
        }

        Runnable shutdown = getShutdownHook();
        if (shutdown != null) {
            registry.registerShutdownHook(hookName, shutdown);
            logger.info("Successfully registered Shutdown Hook: " + hookName);
        }

        onRegister(registry);
    }

    /**
     * Returns the name of the Hook, used for logging or avoiding name conflicts
     */
    protected abstract String getHookName();

    /**
     * Returns the task to be executed when the system starts, returns null if startup events do not need to be handled.
     */
    protected Runnable getStartupHook() {
        return null;
    }

    /**
     * Returns the task to be executed when the system shuts down, returns null if shutdown events do not need to be handled.
     */
    protected Runnable getShutdownHook() {
        return null;
    }

    /**
     * Subclasses can override this method if additional registration logic needs to be executed
     */
    protected void onRegister(PluginRegistry registry) {
        // No additional operations by default
    }
}
