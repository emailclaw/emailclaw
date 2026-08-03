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
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Emailclaw official skill market Provider.
 *
 * <p>Calls Emailclaw Platform OpenAPI {@code GET https://platform.emailclaw.ai/openapi/v1/skills} interface.
 * Public interface, no authentication required.
 *
 * <p>This provider supports searching skills in the skill market by keyword and category.
 */
public class EmailclawMarketProvider implements MarketProvider {

    private static final String BASE_URL = "https://platform.emailclaw.ai";

    private static final String SEARCH_PATH = "/openapi/v1/skills";

    // Upstream API limit: page_size must be between 1..100, otherwise returns 400
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public String key() {
        return "emailclaw";
    }

    @Override
    public String label() {
        return "Emailclaw";
    }

    @Override
    public Availability availability() {
        // Emailclaw Platform official service, always available
        return new Availability(true, "");
    }

    @Override
    public SearchOutcome search(String query, int limit, int page, String lang) throws Exception {
        // Validate pagination parameters
        int pageSize = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        int pageNumber = Math.max(1, page);
        // Build query parameters
        Map<String, String> params =
                MarketHttp.queryOf(
                        "page_size",
                        String.valueOf(pageSize),
                        "page_number",
                        String.valueOf(pageNumber));
        // Add search keyword (if any)
        if (query != null && !query.isBlank()) {
            params.put("search", query.trim());
        }
        // Execute HTTP GET request
        String url = BASE_URL + SEARCH_PATH;
        JsonNode responseBody = MarketHttp.getJson(url, params);
        // Check response status
        if (responseBody.isObject()
                && responseBody.has("success")
                && !responseBody.get("success").asBoolean(true)) {
            String errorMsg = MarketHttp.optText(responseBody, "message");
            throw new IllegalStateException("Emailclaw search failed: " + errorMsg);
        }
        // Parse response data
        JsonNode data = responseBody.get("data");
        List<MarketResult> results = new ArrayList<>();
        int total = -1;
        if (data != null && data.isObject()) {
            // Parse total
            Integer upstreamTotal = MarketHttp.optInt(data.get("total"));
            if (upstreamTotal != null && upstreamTotal >= 0) {
                total = upstreamTotal;
            }
            // Parse skill list
            JsonNode skills = data.get("skills");
            if (skills != null && skills.isArray()) {
                for (JsonNode item : skills) {
                    MarketResult converted = toMarketResult(item, lang);
                    if (converted != null) {
                        results.add(converted);
                    }
                }
            }
        }
        // If total is not obtained, use actual result count as total
        if (total < 0) {
            total = results.size();
        }
        // Check if there are more results
        boolean hasMore = pageNumber * pageSize < total;
        return new SearchOutcome(results, hasMore, total);
    }

    /**
     * Convert API response item to MarketResult object.
     *
     * @param item Single skill item in JSON response
     * @param lang UI language code (used for multi-language fields)
     * @return MarketResult object; returns null if item is invalid
     */
    private MarketResult toMarketResult(JsonNode item, String lang) {
        // Extract skill ID (required field)
        String skillId = MarketHttp.optText(item, "id");
        if (skillId.isBlank()) {
            return null;
        }
        MarketResult row = new MarketResult();
        row.setSource(key());
        row.setSlug(skillId);
        // Skill name (priority to use display_name, otherwise use id)
        row.setName(firstNonBlank(MarketHttp.optText(item, "display_name"), skillId));
        // Description info (priority to use multi-language version)
        row.setDescription(pickLocalizedText(item, "description", lang));
        if (row.getDescription().isBlank()) {
            row.setDescription(MarketHttp.optText(item, "description"));
        }
        // Version number
        row.setVersion(MarketHttp.optText(item, "version"));
        // Developer/owner info
        row.setAuthor(
                firstNonBlank(
                        MarketHttp.optText(item, "developer"), MarketHttp.optText(item, "owner")));
        // Generate skill detail page URL (use URLEncode to handle special characters)
        String encodedId = encodeUrlComponent(skillId);
        row.setSourceUrl(BASE_URL + "/skills/" + encodedId);
        return row;
    }

    /**
     * Pick text of specified language from multi-language fields.
     *
     * <p>Priority is given to the user-specified language, fallback to other languages if unavailable.
     *
     * @param item JSON object
     * @param fieldName Field name (e.g. "description")
     * @param lang User language code (e.g. "zh", "en")
     * @return Localized text; returns empty string if unavailable
     */
    private String pickLocalizedText(JsonNode item, String fieldName, String lang) {
        JsonNode locales = item.get("locales");
        if (!locales.isObject()) {
            return "";
        }
        // Determine primary language (Chinese has priority over English)
        String primaryLang = (lang != null && lang.toLowerCase().startsWith("zh")) ? "zh" : "en";
        String fallbackLang = "zh".equals(primaryLang) ? "en" : "zh";
        // Try primary language first, then try fallback language
        for (String langCode : new String[] {primaryLang, fallbackLang}) {
            JsonNode langEntry = locales.get(langCode);
            if (langEntry != null && langEntry.isObject()) {
                String text = MarketHttp.optText(langEntry, fieldName);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * Return the first non-empty string.
     *
     * @param values Candidate string array
     * @return The first non-empty, non-blank string; if all are empty returns empty string
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String s : values) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return "";
    }

    /**
     * Percent-encode URL components, preserving specific safe characters.
     *
     * @param component URL component
     * @return Encoded string
     */
    private String encodeUrlComponent(String component) {
        if (component == null || component.isBlank()) {
            return "";
        }
        try {
            // Encode using URLEncoder, preserve @ and / as safe characters (for @owner/name format)
            String encoded = URLEncoder.encode(component, StandardCharsets.UTF_8);
            // URLEncoder encodes spaces as + instead of %20, need to replace
            return encoded.replace("+", "%20");
        } catch (Exception e) {
            return component;
        }
    }
}
