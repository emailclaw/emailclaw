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
 * Abstract plugin base class of Command type.
 *
 * <p>Suitable for plugins that provide callable commands or actions to the host system.
 * For example, a command handler can be registered for the system to call by command name.
 */
public abstract class AbstractCommandPlugin extends AbstractEmailclawPlugin {

    @Override
    public final void register(PluginRegistry registry) {
        String commandName = getCommandName();
        Object commandHandler = createCommandHandler();

        if (commandName == null || commandName.isBlank()) {
            throw new IllegalArgumentException("Command name cannot be empty");
        }
        if (commandHandler == null) {
            throw new IllegalArgumentException("Command handler instance cannot be empty");
        }

        registry.registerCommand(commandName, commandHandler);
        logger.info("Successfully registered Command plugin: " + commandName);

        onRegister(registry);
    }

    /**
     * Returns the command name of this Command plugin.
     */
    protected abstract String getCommandName();

    /**
     * Creates and returns the command handler instance.
     * <p>This object typically implements a custom command interface or a handler that can be called by the framework.
     */
    protected abstract Object createCommandHandler();

    /**
     * Subclasses can override this method if additional registration logic needs to be executed.
     */
    protected void onRegister(PluginRegistry registry) {
        // No additional operations by default
    }
}
