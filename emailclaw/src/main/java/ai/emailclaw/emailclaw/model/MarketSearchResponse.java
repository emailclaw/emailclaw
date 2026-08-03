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
package ai.emailclaw.emailclaw.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill market aggregated search response.
 */
public class MarketSearchResponse {
    private List<MarketResult> results = new ArrayList<>();
    private List<MarketSearchError> errors = new ArrayList<>();

    /** Pagination information of each platform: key → (hasMore, total). */
    private Map<String, MarketProviderPageInfo> byProvider = new LinkedHashMap<>();

    /**
     * Get the search result list.
     *
     * @return Search result list
     */
    public List<MarketResult> getResults() {
        return results;
    }

    /**
     * Set the search result list.
     *
     * @param results Search result list
     */
    public void setResults(List<MarketResult> results) {
        this.results = results;
    }

    /**
     * Get the search failure information list.
     *
     * @return Search failure information list
     */
    public List<MarketSearchError> getErrors() {
        return errors;
    }

    /**
     * Set the search failure information list.
     *
     * @param errors Search failure information list
     */
    public void setErrors(List<MarketSearchError> errors) {
        this.errors = errors;
    }

    /**
     * Get the pagination information of each platform.
     *
     * @return Pagination information of each platform
     */
    public Map<String, MarketProviderPageInfo> getByProvider() {
        return byProvider;
    }

    /**
     * Set the pagination information of each platform.
     *
     * @param byProvider Pagination information of each platform
     */
    public void setByProvider(Map<String, MarketProviderPageInfo> byProvider) {
        this.byProvider = byProvider;
    }
}
