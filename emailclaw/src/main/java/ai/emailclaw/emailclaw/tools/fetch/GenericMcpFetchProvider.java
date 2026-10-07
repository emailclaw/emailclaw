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
package ai.emailclaw.emailclaw.tools.fetch;

import ai.emailclaw.emailclaw.util.AntiBotDetectionFilter;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Industrial-grade unified remote MCP fetch provider driven by {@link RemoteMcpProviderConfig}.
 *
 * <p>Key features:
 * <ul>
 *   <li>Strict Pure Dependency Injection without hidden environment or property lookups</li>
 *   <li>Multi-version MCP protocol support (2024-11-05, 2025-03-26, 2025-06-18)</li>
 *   <li>Safe header-only credential transmission preventing API key log leaks</li>
 *   <li>Dynamic schema introspection adapting between 'url' and 'urls' arguments</li>
 *   <li>Decoupled circuit breaker isolating transport failures from target content blocks</li>
 *   <li>Safe content extraction preventing binary/image base64 pollution with 50k char capping</li>
 *   <li>URL query sanitization across all log outputs</li>
 * </ul>
 */
public class GenericMcpFetchProvider implements RemoteWebFetchProvider {

    private static final Logger LOGGER = Logger.getLogger(GenericMcpFetchProvider.class.getName());
    private static final int MAX_EXTRACTED_CONTENT_CHARS = 50_000;
    private static final String TRUNCATION_NOTICE =
            "\n\n[Content truncated at 50,000 characters by Emailclaw]";

    private final RemoteMcpProviderConfig config;
    private final McpCircuitBreaker circuitBreaker;
    private final Object clientLock = new Object();
    private volatile McpClientWrapper clientWrapper;

    public GenericMcpFetchProvider(RemoteMcpProviderConfig config) {
        this(
                config,
                new McpCircuitBreaker(
                        Objects.requireNonNull(config, "config must not be null").name()));
    }

    public GenericMcpFetchProvider(
            RemoteMcpProviderConfig config, McpCircuitBreaker circuitBreaker) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.circuitBreaker =
                Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    @Override
    public String getProviderName() {
        return config.name();
    }

    @Override
    public boolean isAvailable() {
        return config.enabled() && circuitBreaker.allowRequest();
    }

    public RemoteMcpProviderConfig getConfig() {
        return config;
    }

    public McpCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public String fetch(String url) {
        if (!isAvailable()) {
            LOGGER.log(
                    Level.FINE,
                    "Remote provider [{0}] is cooling down or disabled; skipping.",
                    config.name());
            return null;
        }

        String safeLogUrl = UrlPrivacyFilter.sanitizeForLog(url);
        LOGGER.log(
                Level.INFO,
                "Attempting remote MCP fetch via [{0}] (tool: {1}) for URL: {2}",
                new Object[] {config.name(), config.toolName(), safeLogUrl});

        try {
            McpClientWrapper client = getOrCreateClient();
            Map<String, Object> arguments = prepareToolArguments(client, url);

            McpSchema.CallToolResult result =
                    client.callTool(config.toolName(), arguments).block(config.requestTimeout());

            if (result == null) {
                LOGGER.log(
                        Level.WARNING,
                        "Remote provider [{0}] returned null CallToolResult for URL: {1}",
                        new Object[] {config.name(), safeLogUrl});
                circuitBreaker.recordTransportFailure(
                        new IllegalStateException("Null CallToolResult returned from MCP server"));
                return null;
            }

            if (Boolean.TRUE.equals(result.isError())) {
                LOGGER.log(
                        Level.WARNING,
                        "Remote provider [{0}] reported tool execution error for URL: {1}",
                        new Object[] {config.name(), safeLogUrl});
                // In MCP, tool errors could be 4xx or 5xx, so we record failure
                circuitBreaker.recordTransportFailure(
                        new IllegalStateException("MCP tool execution error flagged by server"));
                return null;
            }

            if (result.content() == null || result.content().isEmpty()) {
                LOGGER.log(
                        Level.WARNING,
                        "Remote provider [{0}] returned empty content list for URL: {1}",
                        new Object[] {config.name(), safeLogUrl});
                // Target page returned no content, but connection succeeded. Do not trip circuit
                // breaker.
                return null;
            }

            String content = extractTextContent(result.content());
            if (content.isBlank() || AntiBotDetectionFilter.isBotBlockedText(content)) {
                LOGGER.log(
                        Level.WARNING,
                        "Remote provider [{0}] content was blank or matched anti-bot interstitial:"
                                + " URL={1}, length={2}",
                        new Object[] {config.name(), safeLogUrl, content.length()});
                // Content-level block on target website - do NOT trip remote provider's circuit
                // breaker
                return null;
            }

            circuitBreaker.recordSuccess();
            LOGGER.log(
                    Level.INFO,
                    "Remote provider [{0}] successfully retrieved content: URL={1}, length={2}",
                    new Object[] {config.name(), safeLogUrl, content.length()});
            return content;
        } catch (Throwable t) {
            LOGGER.log(
                    Level.WARNING,
                    "Remote provider ["
                            + config.name()
                            + "] transport error for URL: "
                            + safeLogUrl
                            + ", cause: "
                            + t.getMessage(),
                    t);
            circuitBreaker.recordTransportFailure(t);
            // Invalidate cached client wrapper so subsequent calls re-establish clean connection
            synchronized (clientLock) {
                clientWrapper = null;
            }
            return null;
        }
    }

    private Map<String, Object> prepareToolArguments(McpClientWrapper client, String url) {
        Map<String, Object> arguments = new HashMap<>();
        try {
            McpSchema.Tool tool = client.getCachedTool(config.toolName());
            if (tool != null
                    && tool.inputSchema() != null
                    && tool.inputSchema().properties() != null) {
                Map<String, Object> props = tool.inputSchema().properties();
                if (props.containsKey("urls")) {
                    arguments.put("urls", List.of(url));
                    return arguments;
                }
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.FINE,
                    "Could not inspect tool schema for [{0}], defaulting to 'url' parameter",
                    config.toolName());
        }
        arguments.put("url", url);
        return arguments;
    }

    private String extractTextContent(List<?> contentList) {
        StringBuilder sb = new StringBuilder();
        for (Object item : contentList) {
            if (item instanceof McpSchema.TextContent tc && tc.text() != null) {
                sb.append(tc.text());
            } else if (item instanceof McpSchema.EmbeddedResource er) {
                if (er.resource() instanceof McpSchema.TextResourceContents trc
                        && trc.text() != null) {
                    sb.append(trc.text());
                }
            }
            // Explicitly ignore McpSchema.ImageContent or other binary representations
        }

        String content = sb.toString().trim();
        if (content.length() > MAX_EXTRACTED_CONTENT_CHARS) {
            content = content.substring(0, MAX_EXTRACTED_CONTENT_CHARS) + TRUNCATION_NOTICE;
        }
        return content;
    }

    private McpClientWrapper getOrCreateClient() {
        McpClientWrapper existing = clientWrapper;
        if (existing != null && existing.isInitialized()) {
            return existing;
        }

        synchronized (clientLock) {
            if (clientWrapper != null && clientWrapper.isInitialized()) {
                return clientWrapper;
            }

            String endpoint = config.endpointUrl();
            LOGGER.log(
                    Level.INFO,
                    "Initializing remote MCP client [{0}] at endpoint: {1}",
                    new Object[] {config.name(), UrlPrivacyFilter.sanitizeForLog(endpoint)});

            McpClientBuilder builder =
                    McpClientBuilder.create(config.name() + "-client")
                            .streamableHttpTransport(endpoint)
                            .protocolVersions("2024-11-05", "2025-03-26", "2025-06-18")
                            .timeout(config.requestTimeout())
                            .initializationTimeout(Duration.ofSeconds(8));

            if (config.authHeaderName() != null && config.authHeaderValue() != null) {
                builder.header(config.authHeaderName(), config.authHeaderValue());
            }

            McpClientWrapper wrapper = builder.buildSync();
            wrapper.initialize().block(Duration.ofSeconds(8));
            this.clientWrapper = wrapper;
            LOGGER.log(
                    Level.INFO, "Initialized remote MCP client [{0}] successfully", config.name());
            return clientWrapper;
        }
    }

    @Override
    public void close() {
        synchronized (clientLock) {
            if (clientWrapper != null) {
                try {
                    clientWrapper.close();
                    LOGGER.log(Level.INFO, "Closed remote MCP client [{0}]", config.name());
                } catch (Exception e) {
                    LOGGER.log(
                            Level.FINE, "Exception closing MCP client [" + config.name() + "]", e);
                } finally {
                    clientWrapper = null;
                }
            }
        }
    }
}
