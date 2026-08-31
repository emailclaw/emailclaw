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

import java.util.List;
import java.util.logging.Logger;

/**
 * Abstract plugin base class, providing default implementations such as common logger objects.
 * It is recommended for developers to extend this base class when developing non-standard classification plugins, rather than directly implementing the interface.
 */
public abstract class AbstractEmailclawPlugin implements EmailclawPlugin {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    protected PluginContext context;

    @Override
    public final void initialize(PluginContext context) {
        this.context = context;
        doInitialize();
    }

    @Override
    public List<ConfigFieldDescriptor> configSchema() {
        return pluginConfigSchema();
    }

    @Override
    public void onUnload() {
        logger.fine("Plugin [" + getClass().getSimpleName() + "] is unloading...");
        doUnload();
    }

    /**
     * Returns the plugin's exclusive configuration schema.
     *
     * <p>Third-party developers usually only need to override this method to declare configuration items, and do not need to directly override {@link #configSchema()}.
     */
    protected List<ConfigFieldDescriptor> pluginConfigSchema() {
        return List.of();
    }

    /**
     * Initialization phase extension hook. At this time {@link #context} is available.
     */
    protected void doInitialize() {
        // Default is to do nothing
    }

    /**
     * If subclasses need to perform additional cleanup work upon unloading (such as closing connection pools, etc.), they can override this method.
     */
    protected void doUnload() {
        // Default is to do nothing
    }
}
