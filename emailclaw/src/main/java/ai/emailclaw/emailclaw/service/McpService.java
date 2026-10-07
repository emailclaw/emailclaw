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
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import io.agentscope.harness.agent.tools.McpServerRegistrationResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP client configuration service.
 *
 * <p>Unified to read and write back through ConfigManager after refactoring.
 */
public class McpService {
    private static final Logger LOGGER = Logger.getLogger(McpService.class.getName());

    private final Object clientsLock = new Object();
    private final ConfigManager configManager;
    private final ConcurrentMap<String, McpServerRegistrationResult> healthMap =
            new ConcurrentHashMap<>();

    public McpService(AppContext repository) {
        this(repository != null ? repository.configManager() : null);
    }

    public McpService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Records the terminal registration result for an MCP server.
     *
     * @param result terminal registration result from McpServerRegistrationListener
     */
    public void recordRegistrationResult(McpServerRegistrationResult result) {
        if (result != null && result.serverName() != null) {
            healthMap.put(result.serverName(), result);
            LOGGER.log(
                    Level.INFO,
                    "MCP server registration result: server={0}, status={1}, cause={2}",
                    new Object[] {
                        result.serverName(),
                        result.status(),
                        result.cause() != null ? result.cause().getMessage() : "none"
                    });
        }
    }

    /**
     * Gets the latest registration/health result for an MCP client key.
     *
     * @param key MCP client key
     * @return registration result, or null if not yet registered
     */
    public McpServerRegistrationResult getHealthResult(String key) {
        return key != null ? healthMap.get(key) : null;
    }

    /**
     * Gets all recorded health results.
     *
     * @return unmodifiable map of health results
     */
    public Map<String, McpServerRegistrationResult> getAllHealthResults() {
        return Collections.unmodifiableMap(healthMap);
    }

    public List<McpClientInfo> list() {
        LOGGER.info("MCP invocation started: Reading MCP client list");
        return configManager.getMcpClients();
    }

    public void add(McpClientInfo client) {
        synchronized (clientsLock) {
            List<McpClientInfo> clients = configManager.getMcpClients();
            clients.add(client);
            configManager.saveMcpClients(clients);
        }
        LOGGER.log(Level.INFO, "Added MCP client: key={0}", client.key());
    }

    public void remove(McpClientInfo client) {
        synchronized (clientsLock) {
            List<McpClientInfo> clients = configManager.getMcpClients();
            clients.removeIf(item -> item.key().equals(client.key()));
            configManager.saveMcpClients(clients);
        }
        LOGGER.log(Level.INFO, "Deleted MCP client: key={0}", client.key());
    }

    public void toggleEnabled(McpClientInfo client) {
        synchronized (clientsLock) {
            List<McpClientInfo> clients = configManager.getMcpClients();
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).key().equals(client.key())) {
                    clients.set(i, clients.get(i).withEnabled(!clients.get(i).enabled()));
                    break;
                }
            }
            configManager.saveMcpClients(clients);
        }
    }

    public void setToolWhitelist(String clientKey, boolean enabled, List<String> allowedTools) {
        synchronized (clientsLock) {
            McpClientInfo client = findClient(clientKey);
            if (client == null) {
                throw new IllegalArgumentException("MCP client not found: " + clientKey);
            }
            List<McpClientInfo> clients = configManager.getMcpClients();
            List<String> normalizedTools = normalizeToolNames(allowedTools);
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).key().equals(clientKey)) {
                    clients.set(
                            i,
                            clients.get(i)
                                    .withToolWhitelistEnabled(enabled)
                                    .withAllowedToolNames(normalizedTools));
                    break;
                }
            }
            configManager.saveMcpClients(clients);
        }
        LOGGER.log(
                Level.INFO,
                "Updated MCP tool whitelist: key={0}, enabled={1}",
                new Object[] {clientKey, enabled});
    }

    public List<String> effectiveToolNames(McpClientInfo client) {
        if (client == null) {
            return List.of();
        }
        List<String> source =
                client.toolWhitelistEnabled() ? client.allowedToolNames() : client.toolNames();
        return normalizeToolNames(source);
    }

    public void save() {
        synchronized (clientsLock) {
            List<McpClientInfo> clients = configManager.getMcpClients();
            List<McpClientInfo> updated = new ArrayList<>();
            for (McpClientInfo client : clients) {
                updated.add(
                        client.withToolNames(normalizeToolNames(client.toolNames()))
                                .withAllowedToolNames(
                                        normalizeToolNames(client.allowedToolNames())));
            }
            configManager.saveMcpClients(updated);
        }
    }

    private McpClientInfo findClient(String clientKey) {
        return configManager.getMcpClients().stream()
                .filter(item -> item.key().equals(clientKey))
                .findFirst()
                .orElse(null);
    }

    private List<String> normalizeToolNames(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String toolName : toolNames) {
            if (toolName == null || toolName.isBlank()) {
                continue;
            }
            String normalized = toolName.trim().replaceAll("[^A-Za-z0-9_-]", "_");
            if (!normalized.matches("^[A-Za-z_][A-Za-z0-9_-]*$")) {
                normalized = "mcp_" + normalized;
            }
            result.add(normalized);
        }
        return new ArrayList<>(result);
    }

    /**
     * Creates a predefined remote Parallel AI Search MCP client configuration.
     *
     * @return pre-configured McpClientInfo for Parallel Search
     */
    public static McpClientInfo createParallelSearchPreset() {
        return ai.emailclaw.emailclaw.service.mcp.McpPresetCatalog.parallelSearchPreset();
    }

    /**
     * Creates a predefined remote Exa AI Search MCP client configuration.
     *
     * @return pre-configured McpClientInfo for Exa Search
     */
    public static McpClientInfo createExaSearchPreset() {
        return ai.emailclaw.emailclaw.service.mcp.McpPresetCatalog.exaSearchPreset();
    }
}
