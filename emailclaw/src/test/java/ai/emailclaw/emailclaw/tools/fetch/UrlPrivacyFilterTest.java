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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlPrivacyFilterTest {

    @Test
    @DisplayName("Sensitive email tokens, signatures, and auth params must be detected")
    void testDetectsSensitiveQueryParameters() {
        assertTrue(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://example.com/reset?token=xyz123"));
        assertTrue(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://example.com/download?file=doc.pdf&sig=abcdef"));
        assertTrue(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://example.com/oauth?code=4/0AY0e"));
        assertTrue(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://example.com/api?api_key=sk-123456"));
        assertTrue(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://example.com/login?auth=basic123"));
        assertTrue(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://example.com/app?session=sess_998877"));

        // Regular safe URLs without sensitive auth tokens
        assertFalse(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://en.wikipedia.org/wiki/Artificial_intelligence"));
        assertFalse(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://news.ycombinator.com/item?id=40000000"));
        assertFalse(
                UrlPrivacyFilter.containsSensitiveParameters(
                        "https://example.com/search?q=machine+learning&page=2"));
        assertFalse(UrlPrivacyFilter.containsSensitiveParameters(null));
        assertFalse(UrlPrivacyFilter.containsSensitiveParameters(""));
    }

    @Test
    @DisplayName("URLs sanitized for logging should mask query parameters")
    void testSanitizesUrlForLogging() {
        assertEquals(
                "https://example.com/reset?[REDACTED_QUERY]",
                UrlPrivacyFilter.sanitizeForLog(
                        "https://example.com/reset?token=secret123&user=admin"));
        assertEquals(
                "https://example.com/about",
                UrlPrivacyFilter.sanitizeForLog("https://example.com/about"));
        assertEquals("", UrlPrivacyFilter.sanitizeForLog(null));
    }
}
