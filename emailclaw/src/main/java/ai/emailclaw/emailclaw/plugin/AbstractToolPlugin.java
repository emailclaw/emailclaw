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
 * Abstract plugin base class of Tool type.
 * It is recommended for third-party developers to directly inherit this class, only needing to implement the tool's definition and registration, to quickly develop tools that Agents can call.
 */
public abstract class AbstractToolPlugin extends AbstractEmailclawPlugin {

    @Override
    public final void register(PluginRegistry registry) {
        String toolName = getToolName();
        Object toolHandler = createToolHandler();

        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Plugin tool name cannot be empty");
        }
        if (toolHandler == null) {
            throw new IllegalArgumentException("Tool handler instance cannot be empty");
        }

        registry.registerTool(toolName, toolHandler);
        logger.info("Successfully registered Tool plugin: " + toolName);

        onRegister(registry);
    }

    /**
     * Returns the tool name to be registered (The Agent will call the tool by this name)
     */
    protected abstract String getToolName();

    /**
     * Creates and returns the execution logic (Handler) of the tool
     * (In actual use, this object typically implements the Tool interface of AgentScope Java or a class with specific annotations)
     */
    protected abstract Object createToolHandler();

    /**
     * Subclasses can override this method if additional registration logic needs to be executed
     */
    protected void onRegister(PluginRegistry registry) {
        // No additional operations by default
    }
}
