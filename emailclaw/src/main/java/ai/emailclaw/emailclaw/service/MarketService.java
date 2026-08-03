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

import ai.emailclaw.emailclaw.market.AliyunMarketProvider;
import ai.emailclaw.emailclaw.market.ClawHubMarketProvider;
import ai.emailclaw.emailclaw.market.EmailclawMarketProvider;
import ai.emailclaw.emailclaw.market.MarketProvider;
import ai.emailclaw.emailclaw.market.ModelScopeMarketProvider;
import ai.emailclaw.emailclaw.model.MarketProviderInfo;
import ai.emailclaw.emailclaw.model.MarketProviderPageInfo;
import ai.emailclaw.emailclaw.model.MarketResult;
import ai.emailclaw.emailclaw.model.MarketSearchError;
import ai.emailclaw.emailclaw.model.MarketSearchResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Market service.
 *
 * <p>Responsible for interacting with the cloud market to pull lists of available Agents, plugins, and other resources,
 * and supports installing/updating these resources locally.
 */
public class MarketService {
    private static final Logger LOGGER = Logger.getLogger(MarketService.class.getName());
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;

    private final Map<String, MarketProvider> providers = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MarketService() {
        register(new EmailclawMarketProvider());
        register(new ClawHubMarketProvider());
        register(new ModelScopeMarketProvider());
        register(new AliyunMarketProvider());
    }

    private void register(MarketProvider provider) {
        providers.put(provider.key(), provider);
    }

    /** List all market platforms and their availability. */
    public List<MarketProviderInfo> listProviders() {
        List<MarketProviderInfo> list = new ArrayList<>();
        for (MarketProvider provider : providers.values()) {
            list.add(provider.toInfo());
        }
        return list;
    }

    /**
     * Fetch the list of available resources from remote.
     *
     * @param query Search keywords
     * @param providerPages Page numbers for each platform (key → page, starting from 1)
     * @param limit Maximum number of items returned per platform
     * @param lang UI language
     */
    public MarketSearchResponse search(
            String query, Map<String, Integer> providerPages, int limit, String lang) {
        int cappedLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        String safeLang = lang == null || lang.isBlank() ? "en" : lang;

        List<CompletableFuture<ProviderSearchResult>> futures = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : providerPages.entrySet()) {
            MarketProvider provider = providers.get(entry.getKey());
            if (provider == null) {
                continue;
            }
            int page = Math.max(1, entry.getValue() == null ? 1 : entry.getValue());
            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> runOne(provider, query, cappedLimit, page, safeLang), executor));
        }

        MarketSearchResponse response = new MarketSearchResponse();
        for (CompletableFuture<ProviderSearchResult> future : futures) {
            ProviderSearchResult outcome = future.join();
            if (outcome.error() != null) {
                response.getErrors().add(outcome.error());
                continue;
            }
            response.getResults().addAll(outcome.results());
            MarketProviderPageInfo pageInfo = new MarketProviderPageInfo();
            pageInfo.setHasMore(outcome.hasMore());
            pageInfo.setTotal(outcome.total());
            response.getByProvider().put(outcome.providerKey, pageInfo);
        }
        return response;
    }

    private ProviderSearchResult runOne(
            MarketProvider provider, String query, int limit, int page, String lang) {
        MarketProvider.Availability availability = provider.availability();
        if (!availability.available()) {
            MarketSearchError err = new MarketSearchError();
            err.setProvider(provider.key());
            err.setMessage(
                    availability.reason() == null || availability.reason().isBlank()
                            ? "provider unavailable"
                            : availability.reason());
            return ProviderSearchResult.error(err);
        }
        try {
            MarketProvider.SearchOutcome outcome = provider.search(query, limit, page, lang);
            return ProviderSearchResult.success(
                    provider.key(), outcome.results(), outcome.hasMore(), outcome.total());
        } catch (Exception ex) {
            LOGGER.info("MarketService: Pulling resource list from cloud...");
            LOGGER.log(Level.WARNING, "Market provider failed: " + provider.key(), ex);
            MarketSearchError err = new MarketSearchError();
            err.setProvider(provider.key());
            err.setMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return ProviderSearchResult.error(err);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    private record ProviderSearchResult(
            String providerKey,
            List<MarketResult> results,
            boolean hasMore,
            int total,
            MarketSearchError error) {

        static ProviderSearchResult success(
                String key, List<MarketResult> results, boolean hasMore, int total) {
            return new ProviderSearchResult(key, results, hasMore, total, null);
        }

        static ProviderSearchResult error(MarketSearchError error) {
            return new ProviderSearchResult(error.getProvider(), List.of(), false, 0, error);
        }
    }
}
