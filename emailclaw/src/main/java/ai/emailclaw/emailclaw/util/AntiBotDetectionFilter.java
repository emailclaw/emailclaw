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

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for detecting anti-bot interstitials, Cloudflare Turnstile, and WAF challenge pages.
 */
public final class AntiBotDetectionFilter {

    private static final Logger LOGGER = Logger.getLogger(AntiBotDetectionFilter.class.getName());

    /**
     * Characteristic signatures found in automated bot challenge pages, interstitials, and WAF blocks.
     */
    private static final List<String> BLOCKED_SIGNATURES =
            List.of(
                    "cf-chl-bypass",
                    "challenge-running",
                    "cf-browser-verification",
                    "turnstile",
                    "checking your browser before accessing",
                    "checking your browser",
                    "just a moment...",
                    "verify you are human",
                    "please verify you are human",
                    "attention required!",
                    "access denied",
                    "datadome",
                    "ddos-guard",
                    "robot or human?",
                    "security verification",
                    "ray id:",
                    "shield-interstitial",
                    "please enable cookies and reload",
                    "please enable javascript and cookies");

    private AntiBotDetectionFilter() {
        // Utility class, prevent instantiation
    }

    /**
     * Checks whether the HTTP response indicates that automated access was blocked.
     *
     * @param statusCode HTTP status code (e.g. 200, 403, 429, 503)
     * @param content HTML or extracted text content
     * @return true if access was blocked by anti-bot/WAF
     */
    public static boolean isBotBlocked(int statusCode, String content) {
        if (statusCode == 403 || statusCode == 429 || statusCode == 503) {
            LOGGER.log(Level.FINE, "Anti-bot filter detected block status code: {0}", statusCode);
            return true;
        }
        return isBotBlockedText(content);
    }

    /**
     * Checks whether extracted text or HTML appears to be an anti-bot challenge interstitial.
     *
     * @param content text or HTML content
     * @return true if content matches challenge page signatures
     */
    public static boolean isBotBlockedText(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        // Only inspect relatively short pages or pages containing challenge tokens
        if (lower.length() < 2500) {
            for (String signature : BLOCKED_SIGNATURES) {
                if (lower.contains(signature)) {
                    LOGGER.log(Level.FINE, "Anti-bot filter matched signature: ''{0}''", signature);
                    return true;
                }
            }
        }
        return false;
    }
}
