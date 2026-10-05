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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.McpClientInfo;
import ai.emailclaw.emailclaw.model.ToolInfo;
import ai.emailclaw.emailclaw.plugin.PluginRegistry;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import ai.emailclaw.emailclaw.tools.ToolRegistry;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.McpServerRegistrar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Tool toggle and registry service.
 *
 * <p>After refactoring, it no longer holds a tools snapshot, all provided by ConfigManager.
 *
 * <p>Supports custom default vision model config, used for multi-modal tools (like view_image).
 */
public class ToolService {
    private static final Logger LOGGER = Logger.getLogger(ToolService.class.getName());

    private final Object toolsLock = new Object();
    private final ConfigManager configManager;
    private final PluginRegistry pluginRegistry;
    private final McpService mcpService;

    /** Default vision model name, used for multi-modal tools. */
    private String defaultVisionModel = "gpt-4o";

    /** Fixed prompt text returned to the model when the tool is disabled. */
    public static final String TOOL_DISABLED_MESSAGE = "Tool disabled.";

    public ToolService(AppContext repository, PluginRegistry pluginRegistry) {
        this(repository, pluginRegistry, new McpService(repository));
    }

    public ToolService(
            AppContext repository, PluginRegistry pluginRegistry, McpService mcpService) {
        this.configManager = repository.configManager();
        this.pluginRegistry = pluginRegistry;
        this.mcpService = mcpService;
        LOGGER.info("ToolService initialized");
    }

    public List<ToolInfo> list() {
        List<ToolInfo> stored = new ArrayList<>(configManager.getTools(ToolCatalog.defaults()));
        Set<String> storedNames = stored.stream().map(ToolInfo::name).collect(Collectors.toSet());
        for (String pluginToolName : pluginRegistry.getTools().keySet()) {
            if (!storedNames.contains(pluginToolName)) {
                LOGGER.info("Discovered new plugin tool: " + pluginToolName);
                // By default enabled if it's the first time being discovered
                stored.add(
                        new ToolInfo(pluginToolName, "Plugin tool: " + pluginToolName, true, true));
            }
        }
        return stored;
    }

    public void setEnabled(String toolName, boolean enabled) {
        synchronized (toolsLock) {
            List<ToolInfo> tools = list();
            List<ToolInfo> updated =
                    tools.stream()
                            .map(
                                    item ->
                                            item.name().equals(toolName)
                                                    ? item.withEnabled(enabled)
                                                    : item)
                            .toList();
            configManager.saveTools(updated);
        }
        LOGGER.log(
                Level.INFO,
                "Set tool switch: tool={0}, enabled={1}",
                new Object[] {toolName, enabled});
    }

    public void disableAll() {
        synchronized (toolsLock) {
            List<ToolInfo> tools = list();
            List<ToolInfo> updated = tools.stream().map(item -> item.withEnabled(false)).toList();
            configManager.saveTools(updated);
        }
    }

    public void enableAll() {
        synchronized (toolsLock) {
            List<ToolInfo> tools = list();
            List<ToolInfo> updated = tools.stream().map(item -> item.withEnabled(true)).toList();
            configManager.saveTools(updated);
        }
    }

    /**
     * Get default vision model name.
     *
     * @return default vision model name
     */
    public String getDefaultVisionModel() {
        return defaultVisionModel;
    }

    /**
     * Set default vision model name.
     *
     * @param defaultVisionModel default vision model name
     */
    public void setDefaultVisionModel(String defaultVisionModel) {
        if (defaultVisionModel != null && !defaultVisionModel.isBlank()) {
            this.defaultVisionModel = defaultVisionModel;
            LOGGER.log(Level.INFO, "Set default vision model: {0}", defaultVisionModel);
        }
    }

    public Toolkit buildToolkit(ToolRuntimeContext context) {
        LOGGER.info("Tool registration start: start building Toolkit and built-in tools");
        Set<String> enabled =
                list().stream()
                        .filter(item -> item.enabled())
                        .map(item -> item.name())
                        .collect(Collectors.toSet());
        Toolkit toolkit = new Toolkit();
        ToolRegistry.registerAll(toolkit, context, enabled);

        for (Map.Entry<String, Object> entry : pluginRegistry.getTools().entrySet()) {
            if (enabled.contains(entry.getKey())) {
                toolkit.registerTool(entry.getValue());
                LOGGER.log(Level.FINE, "Registered plugin tool to Toolkit: {0}", entry.getKey());
            }
        }

        // Register enabled MCP servers with McpServerRegistrationListener
        registerMcpServers(toolkit);

        LOGGER.log(Level.FINE, "Build Toolkit, enabled tool count: {0}", enabled.size());
        return toolkit;
    }

    /**
     * Registers configured and enabled MCP servers into the Toolkit with registration result tracking.
     */
    private void registerMcpServers(Toolkit toolkit) {
        if (mcpService == null) {
            return;
        }
        List<McpClientInfo> clients = mcpService.list();
        if (clients == null || clients.isEmpty()) {
            return;
        }
        Map<String, McpServerConfig> servers = new HashMap<>();
        for (McpClientInfo client : clients) {
            if (!client.enabled()) {
                continue;
            }
            if (client.command() == null || client.command().isBlank()) {
                continue;
            }
            McpServerConfig cfg = new McpServerConfig();
            String rawCmd = client.command().trim();
            boolean isRemoteHttp =
                    rawCmd.startsWith("http://")
                            || rawCmd.startsWith("https://")
                            || "Remote".equalsIgnoreCase(client.sourceType())
                            || "http".equalsIgnoreCase(client.sourceType())
                            || "sse".equalsIgnoreCase(client.sourceType());

            if (isRemoteHttp) {
                String transport = "sse".equalsIgnoreCase(client.sourceType()) ? "sse" : "http";
                cfg.setTransport(transport);
                cfg.setUrl(rawCmd);
                if (client.envJson() != null && !client.envJson().isBlank()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers =
                                JsonUtils.getJsonCodec().fromJson(client.envJson(), Map.class);
                        cfg.setHeaders(headers);
                    } catch (Exception e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Failed to parse headers/envJson for remote MCP client: "
                                        + client.key(),
                                e);
                    }
                }
            } else {
                cfg.setTransport("stdio");
                cfg.setCommand(rawCmd);
                if (client.args() != null && !client.args().isEmpty()) {
                    cfg.setArgs(client.args());
                }
                if (client.envJson() != null && !client.envJson().isBlank()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> env =
                                JsonUtils.getJsonCodec().fromJson(client.envJson(), Map.class);
                        cfg.setEnv(env);
                    } catch (Exception e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Failed to parse envJson for MCP client: " + client.key(),
                                e);
                    }
                }
            }
            if (client.toolWhitelistEnabled()
                    && client.allowedToolNames() != null
                    && !client.allowedToolNames().isEmpty()) {
                cfg.setEnableTools(client.allowedToolNames());
            }
            servers.put(client.key(), cfg);
        }
        if (!servers.isEmpty()) {
            LOGGER.log(Level.INFO, "Registering {0} MCP server(s)...", servers.size());
            McpServerRegistrar.register(
                    toolkit, servers, result -> mcpService.recordRegistrationResult(result));
        }
    }
}
