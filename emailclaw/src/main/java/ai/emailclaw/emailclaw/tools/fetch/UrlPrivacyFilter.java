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
package ai.emailclaw.emailclaw.tools.fetch;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * URL privacy filter and log sanitizer.
 *
 * <p>Protects user privacy and enterprise data by preventing sensitive query tokens
 * (password reset links, unsubscribe tokens, signed download tokens, OAuth codes,
 * magic login links) found in emails from being forwarded to third-party public MCP services.
 * Also sanitizes logged URLs by masking query parameters.
 */
public final class UrlPrivacyFilter {

    private static final Logger LOGGER = Logger.getLogger(UrlPrivacyFilter.class.getName());

    private static final Set<String> SENSITIVE_QUERY_KEYS =
            Set.of(
                    "token",
                    "sig",
                    "signature",
                    "code",
                    "key",
                    "auth",
                    "session",
                    "secret",
                    "password",
                    "reset",
                    "verify",
                    "access_token",
                    "refresh_token",
                    "api_key",
                    "apikey");

    private UrlPrivacyFilter() {
        // Utility class, prevent instantiation
    }

    /**
     * Inspects whether the given URL contains sensitive authentication or credential query parameters.
     *
     * @param urlString target URL
     * @return true if sensitive parameters are detected, requiring bypass of external public MCPs
     */
    public static boolean containsSensitiveParameters(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(urlString);
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                return false;
            }
            String[] pairs = rawQuery.split("&");
            for (String pair : pairs) {
                if (pair.isBlank()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key =
                        (eq >= 0 ? pair.substring(0, eq) : pair).toLowerCase(Locale.ROOT).trim();
                if (SENSITIVE_QUERY_KEYS.contains(key)) {
                    return true;
                }
                for (String sensitiveKey : SENSITIVE_QUERY_KEYS) {
                    if (key.contains(sensitiveKey)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // If URI parsing fails, inspect string directly
            String lower = urlString.toLowerCase(Locale.ROOT);
            for (String sensitiveKey : SENSITIVE_QUERY_KEYS) {
                if (lower.contains("?" + sensitiveKey + "=")
                        || lower.contains("&" + sensitiveKey + "=")
                        || lower.contains("?" + sensitiveKey)
                        || lower.contains("&" + sensitiveKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sanitizes a URL for safe logging by masking the query string.
     *
     * @param urlString target URL
     * @return sanitized URL string safe for logging
     */
    public static String sanitizeForLog(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(urlString);
            StringBuilder sb = new StringBuilder();
            if (uri.getScheme() != null) {
                sb.append(uri.getScheme()).append("://");
            }
            if (uri.getRawAuthority() != null) {
                sb.append(uri.getRawAuthority());
            }
            if (uri.getRawPath() != null) {
                sb.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null) {
                sb.append("?[REDACTED_QUERY]");
            }
            return sb.toString();
        } catch (Exception e) {
            int q = urlString.indexOf('?');
            return q >= 0 ? urlString.substring(0, q) + "?[REDACTED_QUERY]" : urlString;
        }
    }
}
