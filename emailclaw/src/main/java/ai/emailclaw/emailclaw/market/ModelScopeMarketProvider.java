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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * ModelScope skill market Provider.
 *
 * <p>Calls {@code GET https://www.modelscope.cn/openapi/v1/skills} public interface.
 */
public class ModelScopeMarketProvider implements MarketProvider {
    private static final String BASE = "https://www.modelscope.cn";
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public String key() {
        return "modelscope";
    }

    @Override
    public String label() {
        return "ModelScope";
    }

    @Override
    public Availability availability() {
        return new Availability(true, "");
    }

    @Override
    public SearchOutcome search(String query, int limit, int page, String lang) throws Exception {
        int pageSize = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        int pageNumber = Math.max(1, page);
        Map<String, String> params =
                MarketHttp.queryOf(
                        "page_size", String.valueOf(pageSize),
                        "page_number", String.valueOf(pageNumber));
        if (query != null && !query.isBlank()) {
            params.put("search", query.trim());
        }

        JsonNode body = MarketHttp.getJson(BASE + "/openapi/v1/skills", params);
        if (body.isObject() && body.has("success") && !body.get("success").asBoolean(true)) {
            throw new IllegalStateException(
                    "ModelScope search failed: " + MarketHttp.optText(body, "message"));
        }

        JsonNode data = body.get("data");
        List<MarketResult> results = new ArrayList<>();
        int total = -1;
        if (data != null && data.isObject()) {
            Integer upstreamTotal = MarketHttp.optInt(data.get("total"));
            if (upstreamTotal != null && upstreamTotal >= 0) {
                total = upstreamTotal;
            }
            JsonNode skills = data.get("skills");
            if (skills != null && skills.isArray()) {
                for (JsonNode item : skills) {
                    MarketResult converted = toResult(item, lang);
                    if (converted != null) {
                        results.add(converted);
                    }
                }
            }
        }
        if (total < 0) {
            total = results.size();
        }
        boolean hasMore = pageNumber * pageSize < total;
        return new SearchOutcome(results, hasMore, total);
    }

    private MarketResult toResult(JsonNode item, String lang) {
        String skillId = MarketHttp.optText(item, "id");
        if (skillId.isBlank()) {
            return null;
        }
        MarketResult row = new MarketResult();
        row.setSource(key());
        row.setSlug(skillId);
        row.setName(
                firstNonBlank(
                        MarketHttp.optText(item, "display_name"),
                        MarketHttp.optText(item, "displayName"),
                        skillId));
        row.setDescription(pickLocalized(item, "description", lang));
        if (row.getDescription().isBlank()) {
            row.setDescription(MarketHttp.optText(item, "description"));
        }
        row.setVersion(MarketHttp.optText(item, "version"));
        row.setAuthor(
                firstNonBlank(
                        MarketHttp.optText(item, "developer"), MarketHttp.optText(item, "owner")));
        if (row.getAuthor().isBlank() && skillId.startsWith("@") && skillId.contains("/")) {
            row.setAuthor(skillId.substring(1, skillId.indexOf('/')));
        }
        row.setIconUrl(MarketHttp.optText(item, "logo_url"));
        row.setSourceUrl(BASE + "/skills/" + URLEncoder.encode(skillId, StandardCharsets.UTF_8));

        Integer downloads = MarketHttp.optInt(item.get("downloads"));
        if (downloads != null) {
            row.getStats().put("downloads", downloads);
        }
        Integer views = MarketHttp.optInt(item.get("view_count"));
        if (views != null) {
            row.getStats().put("views", views);
        }
        String category = pickLocalized(item, "category", lang);
        if (category.isBlank()) {
            category = MarketHttp.optText(item, "category");
        }
        if (!category.isBlank()) {
            row.getStats().put("category", category);
        }
        return row;
    }

    private static String pickLocalized(JsonNode item, String field, String lang) {
        JsonNode locales = item.get("locales");
        if (locales == null || !locales.isObject()) {
            return "";
        }
        boolean zh = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("zh");
        String primary = zh ? "zh" : "en";
        String fallback = zh ? "en" : "zh";
        for (String code : new String[] {primary, fallback, "zh-CN", "en-US"}) {
            JsonNode entry = locales.get(code);
            if (entry != null && entry.isObject()) {
                String text = MarketHttp.optText(entry, field);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
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
