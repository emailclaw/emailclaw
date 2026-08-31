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
package ai.emailclaw.emailclaw.market;

import ai.emailclaw.emailclaw.model.MarketResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * ClawHub skill market Provider.
 *
 * <p>Calls {@code GET https://clawhub.ai/api/v1/search}, doing pagination slicing on the client side (consistent with Emailclaw).
 */
public class ClawHubMarketProvider implements MarketProvider {
    private static final String HOMEPAGE = "https://clawhub.ai";
    private static final int OVERFETCH_LIMIT = 500;

    @Override
    public String key() {
        return "clawhub";
    }

    @Override
    public String label() {
        return "ClawHub";
    }

    @Override
    public Availability availability() {
        return new Availability(true, "");
    }

    @Override
    public SearchOutcome search(String query, int limit, int page, String lang) throws Exception {
        Map<String, String> params =
                MarketHttp.queryOf("q", query, "limit", String.valueOf(OVERFETCH_LIMIT));
        JsonNode data = MarketHttp.getJson(HOMEPAGE + "/api/v1/search", params);

        List<MarketResult> all = new ArrayList<>();
        for (JsonNode item : MarketHttp.normalizeSearchItems(data)) {
            String slug = MarketHttp.optText(item, "slug");
            if (slug.isBlank()) {
                slug = MarketHttp.optText(item, "name");
            }
            if (slug.isBlank()) {
                continue;
            }
            MarketResult row = new MarketResult();
            row.setSource(key());
            row.setSlug(slug);
            row.setName(
                    firstNonBlank(
                            MarketHttp.optText(item, "name"),
                            MarketHttp.optText(item, "displayName"),
                            slug));
            row.setDescription(
                    firstNonBlank(
                            MarketHttp.optText(item, "description"),
                            MarketHttp.optText(item, "summary")));
            row.setVersion(MarketHttp.optText(item, "version"));
            row.setSourceUrl(firstNonBlank(MarketHttp.optText(item, "url"), HOMEPAGE + "/" + slug));

            JsonNode owner = item.get("owner");
            if (owner != null && owner.isObject()) {
                row.setAuthor(
                        firstNonBlank(
                                MarketHttp.optText(owner, "displayName"),
                                MarketHttp.optText(owner, "handle")));
                row.setIconUrl(MarketHttp.optText(owner, "image"));
            }
            if (row.getAuthor().isBlank()) {
                row.setAuthor(MarketHttp.optText(item, "ownerHandle"));
            }
            all.add(row);
        }

        int safeLimit = Math.max(1, limit);
        int safePage = Math.max(1, page);
        int start = (safePage - 1) * safeLimit;
        int end = Math.min(start + safeLimit, all.size());
        List<MarketResult> pageItems = start >= all.size() ? List.of() : all.subList(start, end);
        return new SearchOutcome(pageItems, end < all.size(), all.size());
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
