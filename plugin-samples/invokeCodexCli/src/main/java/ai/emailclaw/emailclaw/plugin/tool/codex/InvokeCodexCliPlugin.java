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
package ai.emailclaw.emailclaw.plugin.tool.codex;

import ai.emailclaw.emailclaw.plugin.AbstractToolPlugin;
import ai.emailclaw.emailclaw.plugin.ConfigFieldDescriptor;
import ai.emailclaw.emailclaw.plugin.PluginStatus;
import java.util.List;
import java.util.logging.Logger;

/**
 * Plugin adapter for the Codex CLI Tool.
 *
 * <p>Registers the {@code invokeCodexCli} tool into Emailclaw's {@link ai.emailclaw.emailclaw.plugin.PluginRegistry},
 * enabling AI agents to execute tasks via Codex CLI non-interactively ({@code codex exec [OPTIONS] [PROMPT]})
 * and ingest structured JSON results.
 */
public class InvokeCodexCliPlugin extends AbstractToolPlugin {

    public static final String PLUGIN_ID = "emailclaw-plugin-tool-invokeCodexCli";
    public static final String PLUGIN_NAME = "Invoke Codex CLI";
    public static final String TOOL_NAME = "invokeCodexCli";

    private static final Logger LOGGER =
            Logger.getLogger(InvokeCodexCliPlugin.class.getName());

    private InvokeCodexCliTool toolHandler;
    private volatile PluginStatus currentStatus = PluginStatus.registered();

    public InvokeCodexCliPlugin() {}

    @Override
    public String id() {
        return PLUGIN_ID;
    }

    @Override
    public String displayName() {
        return PLUGIN_NAME;
    }

    @Override
    protected String getToolName() {
        return TOOL_NAME;
    }

    @Override
    protected Object createToolHandler() {
        if (toolHandler == null) {
            toolHandler = new InvokeCodexCliTool(context);
        }
        return toolHandler;
    }

    @Override
    protected void doInitialize() {
        this.currentStatus = PluginStatus.initialized();
        if (toolHandler != null && context != null) {
            toolHandler.setContext(context);
        }
        LOGGER.info("InvokeCodexCliPlugin initialized successfully");
    }

    @Override
    public void start() {
        if (context == null) {
            currentStatus = PluginStatus.error("Not initialized");
            return;
        }
        currentStatus = PluginStatus.running("Ready for CLI execution");
        LOGGER.info("InvokeCodexCliPlugin started");
    }

    @Override
    public void stop() {
        currentStatus = PluginStatus.stopped();
        LOGGER.info("InvokeCodexCliPlugin stopped");
    }

    @Override
    public PluginStatus status() {
        return currentStatus;
    }

    @Override
    protected List<ConfigFieldDescriptor> pluginConfigSchema() {
        return List.of(
                new ConfigFieldDescriptor(
                        "cli_path",
                        "CLI Executable Path",
                        ConfigFieldDescriptor.FieldType.TEXT,
                        false,
                        "codex",
                        "Path or command name for the Codex CLI executable.",
                        "General"),
                new ConfigFieldDescriptor(
                        "default_timeout",
                        "Default Timeout (Seconds)",
                        ConfigFieldDescriptor.FieldType.INTEGER,
                        false,
                        300,
                        "Default execution timeout in seconds.",
                        "General"),
                new ConfigFieldDescriptor(
                        "continue_last_session",
                        "Continue Last Session",
                        ConfigFieldDescriptor.FieldType.BOOLEAN,
                        false,
                        true,
                        "Whether to continue the last session by default (resume --last).",
                        "General"));
    }
}
