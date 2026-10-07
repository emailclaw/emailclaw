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
 * Robust filter for detecting anti-bot interstitials, Cloudflare Turnstile, and WAF challenge pages.
 *
 * <p>Uses compound signal matching and strong DOM/HTML indicators to avoid false positives
 * on legitimate articles or technical documentation that mention "turnstile" or "access denied".
 */
public final class AntiBotDetectionFilter {

    private static final Logger LOGGER = Logger.getLogger(AntiBotDetectionFilter.class.getName());

    /**
     * High-confidence HTML/script tags and attributes unique to bot challenge interstitials.
     */
    private static final List<String> STRONG_HTML_INDICATORS =
            List.of(
                    "_cf_chl_opt",
                    "/cdn-cgi/challenge-platform/",
                    "cf-turnstile",
                    "cf-chl-bypass",
                    "challenge-running",
                    "cf-browser-verification",
                    "<title>just a moment...</title>",
                    "<title>attention required! | cloudflare</title>",
                    "<title>access denied</title>",
                    "<title>security check</title>",
                    "<title>ddos-guard</title>",
                    "data-ray=",
                    "shield-interstitial");

    /**
     * Definitive challenge action phrases.
     */
    private static final List<String> DEFINITIVE_CHALLENGE_PHRASES =
            List.of(
                    "checking your browser before accessing",
                    "checking your browser",
                    "please verify you are human",
                    "verify you are human",
                    "attention required! | cloudflare",
                    "robot or human?",
                    "please enable javascript and cookies to continue",
                    "please enable cookies and reload");

    /**
     * Secondary signals that require compound confirmation.
     */
    private static final List<String> SECONDARY_SIGNALS =
            List.of("access denied", "security verification", "ray id:", "datadome", "ddos-guard");

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
            if (content == null || content.isBlank()) {
                LOGGER.log(
                        Level.FINE,
                        "Anti-bot filter detected block status code {0} with empty body",
                        statusCode);
                return true;
            }
            if (isBotBlockedText(content)) {
                return true;
            }
            // For 403/429/503 with short responses, treat as blocked
            if (content.length() < 2500) {
                LOGGER.log(
                        Level.FINE,
                        "Anti-bot filter matched HTTP error status {0} with short body ({1} chars)",
                        new Object[] {statusCode, content.length()});
                return true;
            }
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

        // 1. Strong HTML / Script indicators (immediate match)
        for (String strong : STRONG_HTML_INDICATORS) {
            if (lower.contains(strong)) {
                LOGGER.log(Level.FINE, "Anti-bot filter matched strong indicator: ''{0}''", strong);
                return true;
            }
        }

        // 2. Definitive challenge phrases
        for (String phrase : DEFINITIVE_CHALLENGE_PHRASES) {
            if (lower.contains(phrase)) {
                LOGGER.log(
                        Level.FINE,
                        "Anti-bot filter matched definitive challenge phrase: ''{0}''",
                        phrase);
                return true;
            }
        }

        // 3. Compound secondary signals on short text (< 1200 characters)
        if (lower.length() < 1200) {
            int matchCount = 0;
            for (String secondary : SECONDARY_SIGNALS) {
                if (lower.contains(secondary)) {
                    matchCount++;
                }
            }
            if (matchCount >= 2) {
                LOGGER.log(
                        Level.FINE,
                        "Anti-bot filter matched compound secondary signals (count={0}) on short"
                                + " text",
                        matchCount);
                return true;
            }
        }

        return false;
    }
}
