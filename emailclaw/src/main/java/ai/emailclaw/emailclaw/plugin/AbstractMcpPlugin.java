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
 * Abstract plugin base class of MCP (Model Context Protocol) type.
 * Used to bridge an external MCP Server, seamlessly injecting the capabilities (Tools, Prompts, Resources) it provides into the system.
 */
public abstract class AbstractMcpPlugin extends AbstractEmailclawPlugin {

    /**
     * MCP Client configuration class. Defines whether to use stdio or sse.
     */
    public static class McpClientConfig {
        public String type; // "stdio" or "sse"
        public String command;
        public String[] args;
        public String url;

        public static McpClientConfig stdio(String command, String... args) {
            McpClientConfig config = new McpClientConfig();
            config.type = "stdio";
            config.command = command;
            config.args = args;
            return config;
        }

        public static McpClientConfig sse(String url) {
            McpClientConfig config = new McpClientConfig();
            config.type = "sse";
            config.url = url;
            return config;
        }
    }

    @Override
    public final void register(PluginRegistry registry) {
        String serverName = getServerName();
        McpClientConfig config = getMcpConfig();

        if (serverName == null || serverName.isBlank()) {
            throw new IllegalArgumentException("MCP Server name cannot be empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("MCP client configuration cannot be empty");
        }

        // Initialize the underlying MCP Client.
        // (Assuming some abstract MCP Client implementation is used here, in reality it will depend
        // on a specific mcp-client-java SDK)
        Object mcpClientInstance = initializeMcpClient(config);

        registry.registerMcpServer(serverName, mcpClientInstance);
        logger.info(
                "Successfully registered MCP Server plugin: "
                        + serverName
                        + " ["
                        + config.type
                        + "]");

        onRegister(registry, mcpClientInstance);
    }

    /**
     * Returns the MCP Server name to be registered.
     */
    protected abstract String getServerName();

    /**
     * Returns the configuration required to connect to this MCP Server (e.g., stdio or sse configuration).
     */
    protected abstract McpClientConfig getMcpConfig();

    /**
     * Initializes the underlying MCP client.
     */
    private Object initializeMcpClient(McpClientConfig config) {
        // Returns a mock client here. Can be replaced with a real mcp-client-java instance later
        return new Object() {
            @Override
            public String toString() {
                return "McpClient[" + config.type + "]";
            }
        };
    }

    /**
     * Subclasses can override this method if additional registration logic needs to be executed
     */
    protected void onRegister(PluginRegistry registry, Object mcpClientInstance) {
        // No additional operations by default
    }
}
