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
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.service.ToolRuntimeContext;
import ai.emailclaw.emailclaw.storage.sqlite.Fts5SearchService;
import ai.emailclaw.emailclaw.storage.sqlite.Fts5SearchService.SessionSearchHit;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * SQLite FTS5 cross-session search tool with CJK Bigram/Unigram hybrid tokenization and BM25 +
 * time-decay ranking.
 *
 * <p>Replaces AgentScope's default linear-file-scan {@code SessionSearchTool} via
 * {@link ai.emailclaw.emailclaw.service.ToolService#reconcileAgentToolkit}.
 */
public class Fts5SessionSearchTool {

    private static final Logger LOGGER = Logger.getLogger(Fts5SessionSearchTool.class.getName());

    private final Fts5SearchService searchService;
    private final ToolRuntimeContext runtimeContext;

    /**
     * Constructs an FTS5 session search tool with Pure DI.
     *
     * @param searchService SQLite FTS5 search service
     * @param runtimeContext tool runtime context
     */
    public Fts5SessionSearchTool(
            Fts5SearchService searchService, ToolRuntimeContext runtimeContext) {
        this.searchService =
                Objects.requireNonNull(searchService, "searchService must not be null");
        this.runtimeContext = runtimeContext;
    }

    /**
     * Performs a full-text search across historical conversation sessions.
     *
     * @param query search keywords or phrase (supports CJK and English)
     * @param roleFilter optional filter by message role ({@code user} or {@code assistant})
     * @param maxResults maximum number of hits to return
     * @return reactive {@link ToolResultBlock} containing formatted search hits
     */
    @Tool(
            name = "session_search",
            description =
                    "Full-text search across historical conversation sessions using SQLite FTS5"
                            + " (supports both English and Chinese/CJK keywords, phrases, and"
                            + " BM25 relevance ranking). Use this tool when the user references"
                            + " past conversations, previous decisions, historical emails, or"
                            + " workflows solved in earlier sessions.")
    public Mono<ToolResultBlock> sessionSearch(
            @ToolParam(
                            name = "query",
                            description =
                                    "Search keywords or phrase (supports Chinese/CJK and English)")
                    String query,
            @ToolParam(
                            name = "roleFilter",
                            description =
                                    "Optional role filter: 'user' or 'assistant' (leave empty for"
                                            + " all roles)",
                            required = false)
                    String roleFilter,
            @ToolParam(
                            name = "maxResults",
                            description = "Maximum number of results to return (default 8, max 20)",
                            required = false)
                    Integer maxResults) {
        return Mono.fromCallable(
                        () -> {
                            if (query == null || query.isBlank()) {
                                return ToolResultBlock.error("Query must not be empty.");
                            }
                            String agentId =
                                    runtimeContext != null && runtimeContext.currentAgent != null
                                            ? runtimeContext.currentAgent.getId()
                                            : null;
                            int limit = (maxResults != null && maxResults > 0) ? maxResults : 8;
                            LOGGER.log(
                                    Level.INFO,
                                    "Tool session_search invoked: agent={0}, query={1}, limit={2}",
                                    new Object[] {agentId, query, limit});
                            List<SessionSearchHit> hits =
                                    searchService.searchSessions(query, agentId, roleFilter, limit);
                            return ToolResultBlock.text(
                                    searchService.formatSessionHitsForTool(query, hits));
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
