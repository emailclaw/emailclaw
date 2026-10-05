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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AntiBotDetectionFilterTest {

    @Test
    @DisplayName("Block status codes 403, 429, 503 should be detected as blocked")
    void testBlockedStatusCodes() {
        assertTrue(AntiBotDetectionFilter.isBotBlocked(403, "Forbidden"));
        assertTrue(AntiBotDetectionFilter.isBotBlocked(429, "Too Many Requests"));
        assertTrue(AntiBotDetectionFilter.isBotBlocked(503, "Service Unavailable"));
    }

    @Test
    @DisplayName("Normal content with 200 OK should not be detected as blocked")
    void testNormalContent() {
        assertFalse(
                AntiBotDetectionFilter.isBotBlocked(
                        200,
                        "This is a normal article about software architecture and AI agents."));
    }

    @Test
    @DisplayName("Cloudflare interstitial challenge text should be detected as blocked")
    void testCloudflareChallengeText() {
        String cfText =
                "Just a moment... Please verify you are human to continue. Cloudflare Ray ID:"
                        + " 89ab32c";
        assertTrue(AntiBotDetectionFilter.isBotBlocked(200, cfText));
        assertTrue(AntiBotDetectionFilter.isBotBlockedText(cfText));

        String turnstileText = "Verifying you are human... Turnstile challenge running";
        assertTrue(AntiBotDetectionFilter.isBotBlockedText(turnstileText));
    }

    @Test
    @DisplayName("Null or blank content should not trigger text signature match")
    void testNullOrBlank() {
        assertFalse(AntiBotDetectionFilter.isBotBlockedText(null));
        assertFalse(AntiBotDetectionFilter.isBotBlockedText(""));
        assertFalse(AntiBotDetectionFilter.isBotBlockedText("   "));
    }
}
