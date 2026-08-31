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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin registry, used to centrally manage different types of plugin instances, tools, callback hooks, and custom extensions in the system.
 *
 * <p>In the AI Agent system, this registry acts as a capability pool, where all external modules (such as communication channels (Channel), custom LLM Providers,
 * execution tools (Tool), and various UI extension points) are converged.
 *
 * <p>Strictly following the Pure DI principle, this class is no longer provided as a static singleton for access, but is uniformly instantiated by {@code ApplicationBootstrap}
 * and injected into upper-level components that depend on it during bootstrapping, such as {@code PluginManager}, {@code ChannelService}, etc.
 */
public class PluginRegistry {

    private final Map<String, Object> tools = new ConcurrentHashMap<>();
    private final Map<String, Object> providers = new ConcurrentHashMap<>();
    private final Map<String, Object> commands = new ConcurrentHashMap<>();
    private final Map<String, String> frontendEntries = new ConcurrentHashMap<>();
    private final Map<String, Runnable> startupHooks = new ConcurrentHashMap<>();
    private final Map<String, Runnable> shutdownHooks = new ConcurrentHashMap<>();
    private final Map<String, Object> customApis = new ConcurrentHashMap<>();
    private final Map<String, EmailclawPlugin> pluginInstances = new ConcurrentHashMap<>();
    private final Map<String, Object> mcpServers = new ConcurrentHashMap<>();

    /**
     * Default constructor for initialization by the system's unique Composition Root.
     */
    public PluginRegistry() {}

    /**
     * Register a custom tool.
     *
     * <p>In the AI Agent system, a Tool is an external capability that the agent can call (e.g., search, send email, read/write files, etc.).
     *
     * @param name         The globally unique identifier name of the tool, usually consistent with its corresponding Schema identifier
     * @param toolInstance The concrete implementation instance of the tool (usually needs to implement a specific Tool interface or conform to reflection specifications)
     */
    public void registerTool(String name, Object toolInstance) {
        tools.put(name, toolInstance);
    }

    /**
     * Register a custom model provider.
     *
     * @param providerId       The globally unique identifier of the provider
     * @param providerInstance The concrete implementation instance of the provider (responsible for interacting with the corresponding LLM API)
     */
    public void registerProvider(String providerId, Object providerInstance) {
        providers.put(providerId, providerInstance);
    }

    /**
     * Register a callback hook at system startup.
     *
     * <p>It will be called uniformly when the system is fully ready (all core services are loaded).
     *
     * @param name The unique name identifier of the hook to prevent duplicate registration
     * @param hook The specific execution logic
     */
    public void registerStartupHook(String name, Runnable hook) {
        startupHooks.put(name, hook);
    }

    /**
     * Register a callback hook at system shutdown.
     *
     * <p>It will be called when the application lifecycle ends and resources are released, suitable for graceful shutdown logic.
     *
     * @param name The unique name identifier of the hook
     * @param hook The specific execution logic
     */
    public void registerShutdownHook(String name, Runnable hook) {
        shutdownHooks.put(name, hook);
    }

    /**
     * Register a custom command handler.
     *
     * @param commandName    The command name
     * @param commandHandler The instance logic to handle the command
     */
    public void registerCommand(String commandName, Object commandHandler) {
        commands.put(commandName, commandHandler);
    }

    /**
     * Register the entry path for the frontend interface.
     *
     * <p>This method is used to inject WebUI or specific rendering page routing/resource entries into the system.
     *
     * @param frontendId    The unique identifier of the interface
     * @param frontendEntry The entry address or relative resource path
     */
    public void registerFrontendEntry(String frontendId, String frontendEntry) {
        frontendEntries.put(frontendId, frontendEntry);
    }

    /**
     * Register custom API routes and handlers.
     *
     * @param pathPrefix The routing prefix of the API (e.g., "/api/v1/custom")
     * @param apiHandler The concrete instance handling HTTP requests
     */
    public void registerCustomApi(String pathPrefix, Object apiHandler) {
        customApis.put(pathPrefix, apiHandler);
    }

    /**
     * Register the plugin's own entry instance into the system.
     *
     * <p>Usually called automatically by {@code PluginManager} or {@code PluginLoader} after loading the plugin package.
     *
     * @param pluginId Globally unique plugin ID
     * @param plugin   The plugin entry instance implementing {@link EmailclawPlugin}
     */
    public void registerPluginInstance(String pluginId, EmailclawPlugin plugin) {
        pluginInstances.put(pluginId, plugin);
    }

    /**
     * Uninstall (remove) the specified plugin instance from the registry.
     *
     * @param pluginId The ID of the plugin to remove
     */
    public void unregisterPluginInstance(String pluginId) {
        pluginInstances.remove(pluginId);
    }

    /**
     * Get the registered plugin instance.
     *
     * @param pluginId The unique identifier of the plugin
     * @return The corresponding plugin entry instance, or null if not loaded or not exists
     */
    public EmailclawPlugin getPluginInstance(String pluginId) {
        return pluginInstances.get(pluginId);
    }

    /**
     * Register an MCP Server client instance.
     *
     * @param serverName     The MCP Server name
     * @param clientInstance The MCP client instance
     */
    public void registerMcpServer(String serverName, Object clientInstance) {
        mcpServers.put(serverName, clientInstance);
    }

    /**
     * Get all registered MCP Server client collections.
     *
     * @return A Map containing MCP Server instances
     */
    public Map<String, Object> getMcpServers() {
        return mcpServers;
    }

    /**
     * Get all registered custom tool collections.
     *
     * @return A Map containing all tool instances (immutable or operated carefully externally, currently returned by reference)
     */
    public Map<String, Object> getTools() {
        return tools;
    }

    /**
     * Get all registered model provider collections.
     *
     * @return A Map containing all provider instances
     */
    public Map<String, Object> getProviders() {
        return providers;
    }

    /**
     * Get all startup callback hooks.
     *
     * @return A Map containing startup tasks
     */
    public Map<String, Runnable> getStartupHooks() {
        return startupHooks;
    }

    /**
     * Get all shutdown callback hooks.
     *
     * @return A Map containing cleanup tasks
     */
    public Map<String, Runnable> getShutdownHooks() {
        return shutdownHooks;
    }

    /**
     * Get all custom API handler collections.
     *
     * @return A Map containing API routing prefixes and handlers
     */
    public Map<String, Object> getCustomApis() {
        return customApis;
    }

    /**
     * Get all registered command collections.
     *
     * @return A Map containing command handler mappings
     */
    public Map<String, Object> getCommands() {
        return commands;
    }

    /**
     * Get all frontend page entry path collections.
     *
     * @return A Map containing interface identifiers and entry paths
     */
    public Map<String, String> getFrontendEntries() {
        return frontendEntries;
    }
}
