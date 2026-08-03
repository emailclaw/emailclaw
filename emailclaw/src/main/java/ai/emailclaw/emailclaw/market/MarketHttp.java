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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Market module shared HTTP and JSON tools.
 */
public final class MarketHttp {

    /**
     * Single request timeout aligned with Emailclaw {@code MARKET_SEARCH_TIMEOUT_S}.
     */
    public static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(15);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(SEARCH_TIMEOUT)
                    .build();

    private MarketHttp() {}

    public static JsonNode getJson(String url, Map<String, String> query) throws Exception {
        String fullUrl = appendQuery(url, query);
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Accept", "application/json")
                        .header("User-Agent", "Emailclaw")
                        .timeout(SEARCH_TIMEOUT)
                        .GET()
                        .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " for " + fullUrl);
        }
        return JSON.readTree(response.body());
    }

    public static String optText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asString("").trim();
    }

    public static String optText(JsonNode parent, String field) {
        if (parent == null) {
            return "";
        }
        return optText(parent.get(field));
    }

    public static Integer optInt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        String text = node.asString("").trim();
        if (text.matches("-?\\d+")) {
            return Integer.parseInt(text);
        }
        return null;
    }

    /**
     * Normalize various JSON structures returned by interfaces like ClawHub to an object array.
     */
    public static Iterable<JsonNode> normalizeSearchItems(JsonNode data) {
        if (data == null) {
            return List.of();
        }
        if (data.isArray()) {
            return data;
        }
        if (data.isObject()) {
            for (String key : new String[] {"items", "skills", "results", "data"}) {
                JsonNode arr = data.get(key);
                if (arr != null && arr.isArray()) {
                    return arr;
                }
            }
            if (data.has("name") && data.has("slug")) {
                return List.of(data);
            }
        }
        return List.of();
    }

    private static String appendQuery(String url, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return url;
        }
        String qs =
                query.entrySet().stream()
                        .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(Collectors.joining("&"));
        if (qs.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + qs;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static Map<String, String> queryOf(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
