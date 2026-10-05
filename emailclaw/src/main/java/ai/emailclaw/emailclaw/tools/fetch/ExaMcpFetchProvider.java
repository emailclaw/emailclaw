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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web content fetch provider leveraging Exa AI MCP (Streamable HTTP).
 *
 * <p>Supports zero-key anonymous access by default, and accepts optional API keys
 * via system property {@code emailclaw.mcp.exa.key} or environment {@code EXA_API_KEY}.
 */
public class ExaMcpFetchProvider implements RemoteWebFetchProvider {

    private static final Logger LOGGER = Logger.getLogger(ExaMcpFetchProvider.class.getName());

    public static final String DEFAULT_ENDPOINT = "https://mcp.exa.ai/mcp?tools=web_fetch_exa";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long COOLDOWN_MILLIS = 60_000L;

    private final String endpointUrl;
    private final String apiKey;
    private final Duration requestTimeout;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitTripUntil = new AtomicLong(0L);

    private McpClientWrapper clientWrapper;

    public ExaMcpFetchProvider() {
        this(resolveEndpoint(), resolveApiKey(), DEFAULT_TIMEOUT);
    }

    public ExaMcpFetchProvider(String endpointUrl, String apiKey, Duration requestTimeout) {
        this.endpointUrl =
                (endpointUrl != null && !endpointUrl.isBlank())
                        ? endpointUrl.trim()
                        : DEFAULT_ENDPOINT;
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey.trim() : null;
        this.requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_TIMEOUT;
    }

    private static String resolveEndpoint() {
        String prop = System.getProperty("emailclaw.mcp.exa.url");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("EXA_MCP_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_ENDPOINT;
    }

    private static String resolveApiKey() {
        String prop = System.getProperty("emailclaw.mcp.exa.key");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("EXA_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    @Override
    public String getProviderName() {
        return "Exa_MCP";
    }

    @Override
    public boolean isAvailable() {
        return System.currentTimeMillis() >= circuitTripUntil.get();
    }

    @Override
    public String fetch(String url) {
        if (!isAvailable()) {
            LOGGER.log(
                    Level.FINE,
                    "ExaMcpFetchProvider is currently cooling down due to previous failures,"
                            + " skipping.");
            return null;
        }

        LOGGER.log(
                Level.INFO,
                "Attempting Exa MCP web_fetch_exa for url: {0} (endpoint: {1})",
                new Object[] {url, endpointUrl});

        try {
            McpClientWrapper client = getOrCreateClient();
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("url", url);

            McpSchema.CallToolResult result =
                    client.callTool("web_fetch_exa", arguments).block(requestTimeout);

            if (result == null
                    || Boolean.TRUE.equals(result.isError())
                    || result.content() == null) {
                LOGGER.log(
                        Level.WARNING,
                        "Exa MCP web_fetch_exa returned empty or error result for url: {0}",
                        url);
                recordFailure();
                return null;
            }

            StringBuilder sb = new StringBuilder();
            for (Object item : result.content()) {
                if (item instanceof McpSchema.TextContent tc && tc.text() != null) {
                    sb.append(tc.text());
                } else if (item != null) {
                    sb.append(item.toString());
                }
            }

            String content = sb.toString().trim();
            if (content.isBlank() || AntiBotDetectionFilter.isBotBlockedText(content)) {
                LOGGER.log(
                        Level.WARNING,
                        "Exa MCP extracted content was blank or matched anti-bot interstitial:"
                                + " len={0}",
                        content.length());
                recordFailure();
                return null;
            }

            recordSuccess();
            LOGGER.log(
                    Level.INFO,
                    "Exa MCP web_fetch_exa succeeded: url={0}, length={1}",
                    new Object[] {url, content.length()});
            return content;
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Exa MCP web_fetch_exa failed for url: " + url + ", cause: " + e.getMessage(),
                    e);
            recordFailure();
            closeClientSafely();
            return null;
        }
    }

    private synchronized McpClientWrapper getOrCreateClient() {
        if (clientWrapper != null && clientWrapper.isInitialized()) {
            return clientWrapper;
        }
        closeClientSafely();

        String targetUrl = endpointUrl;
        if (apiKey != null && !apiKey.isBlank() && !targetUrl.contains("exaApiKey=")) {
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "exaApiKey=" + apiKey;
        }

        McpClientBuilder builder =
                McpClientBuilder.create("exa-fetch-client")
                        .streamableHttpTransport(targetUrl)
                        .timeout(requestTimeout)
                        .initializationTimeout(Duration.ofSeconds(8));

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-api-key", apiKey);
        }

        McpClientWrapper wrapper = builder.buildSync();
        wrapper.initialize().block(Duration.ofSeconds(8));
        this.clientWrapper = wrapper;
        LOGGER.log(Level.INFO, "Initialized Exa MCP client successfully: endpoint={0}", targetUrl);
        return clientWrapper;
    }

    private synchronized void closeClientSafely() {
        if (clientWrapper != null) {
            try {
                clientWrapper.close();
            } catch (Exception ignored) {
                // Ignore closing error
            } finally {
                clientWrapper = null;
            }
        }
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
    }

    private void recordFailure() {
        int current = consecutiveFailures.incrementAndGet();
        if (current >= MAX_CONSECUTIVE_FAILURES) {
            long tripUntil = System.currentTimeMillis() + COOLDOWN_MILLIS;
            circuitTripUntil.set(tripUntil);
            LOGGER.log(
                    Level.WARNING,
                    "Exa MCP provider tripped circuit breaker after {0} consecutive failures."
                            + " Cooldown until: {1}",
                    new Object[] {current, tripUntil});
        }
    }
}
