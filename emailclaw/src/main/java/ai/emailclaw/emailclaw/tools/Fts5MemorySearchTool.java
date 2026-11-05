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
import ai.emailclaw.emailclaw.storage.sqlite.Fts5SearchService.MemorySearchHit;
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
 * SQLite FTS5 long-term memory search tool with CJK Bigram/Unigram hybrid tokenization and BM25
 * ranking.
 */
public class Fts5MemorySearchTool {

    private static final Logger LOGGER = Logger.getLogger(Fts5MemorySearchTool.class.getName());

    private final Fts5SearchService searchService;
    private final ToolRuntimeContext runtimeContext;

    /**
     * Constructs an FTS5 memory search tool with Pure DI.
     *
     * @param searchService SQLite FTS5 search service
     * @param runtimeContext tool runtime context
     */
    public Fts5MemorySearchTool(
            Fts5SearchService searchService, ToolRuntimeContext runtimeContext) {
        this.searchService =
                Objects.requireNonNull(searchService, "searchService must not be null");
        this.runtimeContext = runtimeContext;
    }

    /**
     * Performs a full-text search across long-term memory notes in SQLite FTS5.
     *
     * @param query search keywords or phrase (supports CJK and English)
     * @param maxResults maximum number of hits to return
     * @return reactive {@link ToolResultBlock} containing formatted memory search hits
     */
    @Tool(
            name = "memory_search",
            description =
                    "Search long-term structured memory notes (both GLOBAL user preferences and"
                            + " PROJECT-specific facts) using SQLite FTS5 with CJK and BM25"
                            + " ranking.")
    public Mono<ToolResultBlock> memorySearch(
            @ToolParam(
                            name = "query",
                            description =
                                    "Search keywords or phrase (supports Chinese/CJK and English)")
                    String query,
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
                                            : "";
                            String projectId =
                                    runtimeContext != null
                                            ? runtimeContext.currentProject().getId()
                                            : "GLOBAL";
                            int limit = (maxResults != null && maxResults > 0) ? maxResults : 8;
                            LOGGER.log(
                                    Level.INFO,
                                    "Tool memory_search invoked: agent={0}, project={1}, query={2}",
                                    new Object[] {agentId, projectId, query});
                            List<MemorySearchHit> hits =
                                    searchService.searchMemories(query, agentId, projectId, limit);
                            return ToolResultBlock.text(
                                    searchService.formatMemoryHitsForTool(query, hits));
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
