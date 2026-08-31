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
package ai.emailclaw.emailclaw.util;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.jsoup.Jsoup;

/**
 * Fast web page content extraction utility.
 */
public class WebExtractUtils {

    private static final HttpClient HTTP = HttpClient.newBuilder().build();

    public record HttpExtractResult(
            boolean ok,
            boolean dynamicLikely,
            String text,
            String reason,
            int statusCode,
            String contentType) {}

    /**
     * Attempt to directly fetch and extract visible text from web page via HttpClient.
     */
    public static HttpExtractResult tryFastHttpExtract(String url) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header(
                                    "User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                            + " (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .header(
                                    "Accept",
                                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                            .header("Accept-Encoding", "gzip, deflate")
                            .header(
                                    "sec-ch-ua",
                                    "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\","
                                            + " \"Not-A.Brand\";v=\"99\"")
                            .header("sec-ch-ua-mobile", "?0")
                            .header("sec-ch-ua-platform", "\"Windows\"")
                            .header("Sec-Fetch-Dest", "document")
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-Site", "none")
                            .header("Sec-Fetch-User", "?1")
                            .header("Upgrade-Insecure-Requests", "1")
                            .GET()
                            .build();
            HttpResponse<byte[]> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (status < 200 || status >= 300) {
                return new HttpExtractResult(
                        false, true, "", "http status not success", status, contentType);
            }
            String contentTypeLower = contentType.toLowerCase(Locale.ROOT);
            if (!contentTypeLower.contains("text/html")
                    && !contentTypeLower.contains("application/xhtml+xml")) {
                return new HttpExtractResult(
                        false, true, "", "content-type is not html", status, contentType);
            }
            byte[] bodyBytes = response.body();
            String contentEncoding =
                    response.headers()
                            .firstValue("Content-Encoding")
                            .orElse("")
                            .toLowerCase(Locale.ROOT);
            if (contentEncoding.contains("gzip")) {
                try (GZIPInputStream gis =
                        new GZIPInputStream(new ByteArrayInputStream(bodyBytes))) {
                    bodyBytes = gis.readAllBytes();
                }
            } else if (contentEncoding.contains("deflate")) {
                try (InflaterInputStream iis =
                        new InflaterInputStream(new ByteArrayInputStream(bodyBytes))) {
                    bodyBytes = iis.readAllBytes();
                }
            }
            String charset = detectResponseCharset(response, bodyBytes);
            Charset cs;
            try {
                cs = Charset.forName(charset);
            } catch (Exception ignored) {
                cs = StandardCharsets.UTF_8;
            }
            String html = new String(bodyBytes, cs);
            String text = extractVisibleTextFromHtml(html);
            boolean dynamicLikely = isProbablyDynamicPage(html, text);
            return new HttpExtractResult(true, dynamicLikely, text, "ok", status, contentType);
        } catch (Exception e) {
            return new HttpExtractResult(
                    false, true, "", "http extract exception: " + e.getMessage(), -1, "");
        }
    }

    private static String detectResponseCharset(HttpResponse<byte[]> response, byte[] bytes) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        Matcher headerMatcher =
                Pattern.compile("(?i)charset=([a-zA-Z0-9._-]+)").matcher(contentType);
        if (headerMatcher.find()) {
            return headerMatcher.group(1).trim();
        }
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8.name();
        }
        int peekLength = Math.min(bytes.length, 8192);
        String head = new String(bytes, 0, peekLength, StandardCharsets.UTF_8);
        Matcher metaMatcher =
                Pattern.compile("(?i)<meta[^>]+charset\\s*=\\s*['\\\"]?([a-zA-Z0-9._-]+)")
                        .matcher(head);
        if (metaMatcher.find()) {
            return metaMatcher.group(1).trim();
        }
        return StandardCharsets.UTF_8.name();
    }

    private static String extractVisibleTextFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        try {
            String text = Jsoup.parse(html).text();
            return normalizeWhitespace(text);
        } catch (Throwable t) {
            String text = html;
            text = text.replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ");
            text = text.replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ");
            text = text.replaceAll("(?is)<noscript\\b[^>]*>.*?</noscript>", " ");
            text = text.replaceAll("(?is)<template\\b[^>]*>.*?</template>", " ");
            text =
                    text.replaceAll(
                            "(?is)</?(div|p|section|article|main|aside|header|footer|h[1-6]|li|ul|ol|br|tr|td|th)\\b[^>]*>",
                            "\n");
            text = text.replaceAll("(?is)<[^>]+>", " ");
            text = decodeHtmlEntitiesLite(text);
            return normalizeWhitespace(text);
        }
    }

    private static boolean isProbablyDynamicPage(String html, String extractedText) {
        String h = html == null ? "" : html.toLowerCase(Locale.ROOT);
        String t = extractedText == null ? "" : extractedText.toLowerCase(Locale.ROOT);
        int textLen = extractedText == null ? 0 : extractedText.length();
        if (textLen < 120) {
            return true;
        }
        if (h.contains("id=\"root\"")
                || h.contains("id='root'")
                || h.contains("id=\"app\"")
                || h.contains("id='app'")
                || h.contains("__next_data__")
                || h.contains("window.__nuxt__")
                || h.contains("data-reactroot")
                || h.contains("ng-version")) {
            return true;
        }
        int scriptCount = Math.max(countMatches(h, "<script"), countMatches(h, ".js\""));
        if (scriptCount >= 8 && textLen < 500) {
            return true;
        }
        return t.contains("enable javascript")
                || t.contains("please enable javascript")
                || t.contains("please turn on javascript")
                || t.contains("javascript needs to be enabled");
    }

    private static int countMatches(String text, String needle) {
        if (text == null || text.isBlank() || needle == null || needle.isBlank()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    private static String decodeHtmlEntitiesLite(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private static String normalizeWhitespace(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.replace('\u00A0', ' ');
        normalized = normalized.replaceAll("[\\t\\x0B\\f\\r ]+", " ");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }
}
