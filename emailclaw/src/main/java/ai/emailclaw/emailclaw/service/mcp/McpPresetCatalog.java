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
package ai.emailclaw.emailclaw.service.mcp;

import ai.emailclaw.emailclaw.model.McpClientInfo;
import java.util.List;

/**
 * Catalog of ready-to-use remote Model Context Protocol (MCP) server presets.
 */
public final class McpPresetCatalog {

    private McpPresetCatalog() {
        // Utility catalog, prevent instantiation
    }

    /**
     * Pre-configured Parallel AI Web Search preset.
     *
     * @return pre-configured McpClientInfo for Parallel Search
     */
    public static McpClientInfo parallelSearchPreset() {
        return new McpClientInfo(
                "parallel-search",
                "Parallel AI Web Search",
                false,
                true,
                "Remote",
                "High-performance real-time web search powered by Parallel AI",
                "https://search.parallel.ai/mcp",
                List.of(),
                "",
                List.of("web_search"),
                true,
                List.of("web_search"),
                "Local",
                "",
                "",
                "http",
                "https://search.parallel.ai/mcp",
                "");
    }

    /**
     * Pre-configured Exa AI Web Search preset.
     *
     * @return pre-configured McpClientInfo for Exa Search
     */
    public static McpClientInfo exaSearchPreset() {
        return new McpClientInfo(
                "exa-search",
                "Exa AI Web Search",
                false,
                true,
                "Remote",
                "Semantic web search powered by Exa AI",
                "https://mcp.exa.ai/mcp?tools=web_search_exa",
                List.of(),
                "",
                List.of("web_search_exa"),
                true,
                List.of("web_search_exa"),
                "Local",
                "",
                "",
                "http",
                "https://mcp.exa.ai/mcp?tools=web_search_exa",
                "");
    }

    /**
     * Returns all available pre-configured presets.
     *
     * @return unmodifiable list of presets
     */
    public static List<McpClientInfo> allPresets() {
        return List.of(parallelSearchPreset(), exaSearchPreset());
    }
}
