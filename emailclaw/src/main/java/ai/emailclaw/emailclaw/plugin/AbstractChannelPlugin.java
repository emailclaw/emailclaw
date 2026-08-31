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

/**
 * Abstract plugin base class of Channel type.
 */
public abstract class AbstractChannelPlugin extends AbstractEmailclawPlugin {

    @Override
    public void register(PluginRegistry registry) {
        // Channel plugins do not need to explicitly register tools or Providers, they only need to
        // be loaded and have their lifecycles managed
    }

    @Override
    protected final List<ConfigFieldDescriptor> pluginConfigSchema() {
        return List.copyOf(channelConfigSchema());
    }

    /**
     * Returns the Channel plugin's own configuration items.
     *
     * <p>ToolGuard is forcibly enabled, switch configuration is no longer provided; subclasses only need to declare connection parameters, whitelists, and other business configurations.
     */
    protected List<ConfigFieldDescriptor> channelConfigSchema() {
        return List.of();
    }

    /**
     * Determines whether the current Channel has ToolGuard enabled.
     *
     * <p>ToolGuard is forcibly enabled, always returns {@code true}, ensuring all dangerous operations from all channels must be approved.
     */
    protected boolean isToolGuardEnabled() {
        return true;
    }

    /**
     * Determines whether the user message is a ToolGuard approval code.
     *
     * @param text external user input content
     * @return {@code true} indicates the content is a pure 4-digit approval code
     */
    protected boolean isToolGuardApprovalCode(String text) {
        return text != null && text.trim().matches("\\d{4}");
    }
}
