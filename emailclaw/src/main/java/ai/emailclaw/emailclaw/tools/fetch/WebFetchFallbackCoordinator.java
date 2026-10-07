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

import ai.emailclaw.emailclaw.util.AntiBotDetectionFilter;
import ai.emailclaw.emailclaw.util.PrivateNetworkChecker;
import ai.emailclaw.emailclaw.util.WebExtractUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Industrial-grade multi-tier cascading fallback coordinator for resilient web page content fetching.
 *
 * <p>Orchestrates the 4-tier retrieval pipeline with deadline budgeting and security boundaries:
 * <ol>
 *   <li>Tier 1: Local Fast HTTP (zero latency, static documentation & articles)</li>
 *   <li>Tier 2: Parallel AI Search MCP (cloud residential proxy extraction)</li>
 *   <li>Tier 3: Exa AI MCP (semantic neural web scraper and SPA extractor)</li>
 *   <li>Tier 4: Local Playwright headless browser (ultimate local fallback & intranet support)</li>
 * </ol>
 *
 * <p>Security & Reliability Guarantees:
 * <ul>
 *   <li>Private/Intranet URLs stay within local infrastructure and never leave to external MCPs</li>
 *   <li>Sensitive URLs with auth/session/reset tokens are strictly kept local for privacy</li>
 *   <li>Strict 25-second overall deadline prevents thread hogging across cascading tiers</li>
 *   <li>Diagnostic aggregation provides crystal clear failure reasons across all tiers</li>
 *   <li>Pure Dependency Injection and AutoCloseable resource management</li>
 * </ul>
 */
public class WebFetchFallbackCoordinator implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(WebFetchFallbackCoordinator.class.getName());

    public static final Duration DEFAULT_OVERALL_BUDGET = Duration.ofSeconds(25);
    private static final Duration MIN_REMAINING_FOR_REMOTE = Duration.ofMillis(2000);
    private static final Duration MIN_REMAINING_FOR_PLAYWRIGHT = Duration.ofMillis(1500);

    @FunctionalInterface
    public interface LocalBrowserFallback {
        /**
         * Executes local browser automation fetch (e.g. Playwright).
         *
         * @param url target URL
         * @return extracted text or error message
         */
        String execute(String url);
    }

    private final LocalHttpFetcher localHttpFetcher;
    private final List<RemoteWebFetchProvider> remoteProviders;
    private final boolean remoteFallbackEnabled;
    private final Duration overallBudget;
    private final WebFetchMetrics metrics;

    public WebFetchFallbackCoordinator(
            LocalHttpFetcher localHttpFetcher,
            List<RemoteWebFetchProvider> remoteProviders,
            boolean remoteFallbackEnabled) {
        this(
                localHttpFetcher,
                remoteProviders,
                remoteFallbackEnabled,
                DEFAULT_OVERALL_BUDGET,
                new WebFetchMetrics());
    }

    public WebFetchFallbackCoordinator(
            LocalHttpFetcher localHttpFetcher,
            List<RemoteWebFetchProvider> remoteProviders,
            boolean remoteFallbackEnabled,
            Duration overallBudget,
            WebFetchMetrics metrics) {
        this.localHttpFetcher =
                Objects.requireNonNull(localHttpFetcher, "localHttpFetcher must not be null");
        this.remoteProviders = remoteProviders != null ? List.copyOf(remoteProviders) : List.of();
        this.remoteFallbackEnabled = remoteFallbackEnabled;
        this.overallBudget = overallBudget != null ? overallBudget : DEFAULT_OVERALL_BUDGET;
        this.metrics = metrics != null ? metrics : new WebFetchMetrics();
    }

    public WebFetchMetrics getMetrics() {
        return metrics;
    }

    public boolean isRemoteFallbackEnabled() {
        return remoteFallbackEnabled;
    }

    public List<RemoteWebFetchProvider> getRemoteProviders() {
        return remoteProviders;
    }

    /**
     * Fetches web content through the cascading fallback pipeline with an overall timeout budget.
     *
     * @param url target URL to fetch
     * @param localBrowserFallback fallback function executing local Playwright
     * @return extracted web text/markdown or diagnostic error message
     */
    public String fetch(String url, LocalBrowserFallback localBrowserFallback) {
        if (url == null || url.isBlank()) {
            return "Error: url is required.";
        }

        long deadlineNanos = System.nanoTime() + overallBudget.toNanos();
        List<String> diagnostics = new ArrayList<>();
        String safeLogUrl = UrlPrivacyFilter.sanitizeForLog(url);

        // Intranet / Private Network Routing Check
        boolean isPrivate = PrivateNetworkChecker.isPrivateOrLocalAddress(url);
        if (isPrivate) {
            metrics.recordPrivateNetworkRouting();
            LOGGER.log(
                    Level.INFO,
                    "Target URL is local or private network: {0}. Skipping remote MCP providers.",
                    safeLogUrl);
            diagnostics.add("Private network detected: remote MCP skipped");
            return fetchPrivateNetwork(url, localBrowserFallback, deadlineNanos, diagnostics);
        }

        // Privacy and Token Leaks Guard
        boolean isSensitive = UrlPrivacyFilter.containsSensitiveParameters(url);
        if (isSensitive) {
            metrics.recordSensitiveUrlBypass();
            LOGGER.log(
                    Level.WARNING,
                    "Target URL contains sensitive token/authentication query parameters: {0}."
                            + " Skipping remote MCP providers.",
                    safeLogUrl);
            diagnostics.add("Sensitive URL parameters detected: remote MCP bypassed for privacy");
            return fetchSensitiveNetwork(url, localBrowserFallback, deadlineNanos, diagnostics);
        }

        return fetchPublicNetwork(url, localBrowserFallback, deadlineNanos, diagnostics);
    }

    private String fetchPrivateNetwork(
            String url,
            LocalBrowserFallback localBrowserFallback,
            long deadlineNanos,
            List<String> diagnostics) {
        String safeLogUrl = UrlPrivacyFilter.sanitizeForLog(url);
        Duration remaining = getRemainingBudget(deadlineNanos);

        // Tier 1: Local Fast HTTP
        metrics.recordFastHttpAttempt();
        Duration fastHttpTimeout = minDuration(Duration.ofSeconds(4), remaining);
        WebExtractUtils.HttpExtractResult fast = localHttpFetcher.fetch(url, fastHttpTimeout);
        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200
                && !AntiBotDetectionFilter.isBotBlocked(fast.statusCode(), fast.text())) {
            metrics.recordFastHttpSuccess();
            LOGGER.log(
                    Level.INFO,
                    "web_fetch Tier 1 (Fast HTTP) succeeded for private URL: url={0}, length={1}",
                    new Object[] {safeLogUrl, fast.text().length()});
            return fast.text();
        }

        diagnostics.add(
                "Tier 1 (Fast HTTP): status="
                        + fast.statusCode()
                        + ", reason="
                        + (fast.reason() != null ? fast.reason() : "dynamic/insufficient length"));

        LOGGER.log(
                Level.INFO,
                "Private URL requires browser rendering, delegating to local Playwright: {0}",
                safeLogUrl);
        return executeBrowserFallback(url, localBrowserFallback, deadlineNanos, diagnostics);
    }

    private String fetchSensitiveNetwork(
            String url,
            LocalBrowserFallback localBrowserFallback,
            long deadlineNanos,
            List<String> diagnostics) {
        String safeLogUrl = UrlPrivacyFilter.sanitizeForLog(url);
        Duration remaining = getRemainingBudget(deadlineNanos);

        // Tier 1: Local Fast HTTP
        metrics.recordFastHttpAttempt();
        Duration fastHttpTimeout = minDuration(Duration.ofSeconds(4), remaining);
        WebExtractUtils.HttpExtractResult fast = localHttpFetcher.fetch(url, fastHttpTimeout);
        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200
                && !AntiBotDetectionFilter.isBotBlocked(fast.statusCode(), fast.text())) {
            metrics.recordFastHttpSuccess();
            LOGGER.log(
                    Level.INFO,
                    "web_fetch Tier 1 (Fast HTTP) succeeded for sensitive URL: url={0}, length={1}",
                    new Object[] {safeLogUrl, fast.text().length()});
            return fast.text();
        }

        diagnostics.add(
                "Tier 1 (Fast HTTP): status="
                        + fast.statusCode()
                        + ", reason="
                        + (fast.reason() != null ? fast.reason() : "dynamic/blocked"));

        LOGGER.log(
                Level.INFO,
                "Sensitive URL cascading directly to local Playwright: {0}",
                safeLogUrl);
        return executeBrowserFallback(url, localBrowserFallback, deadlineNanos, diagnostics);
    }

    private String fetchPublicNetwork(
            String url,
            LocalBrowserFallback localBrowserFallback,
            long deadlineNanos,
            List<String> diagnostics) {
        String safeLogUrl = UrlPrivacyFilter.sanitizeForLog(url);

        // Tier 1: Local Fast HTTP
        metrics.recordFastHttpAttempt();
        Duration remaining = getRemainingBudget(deadlineNanos);
        Duration fastHttpTimeout = minDuration(Duration.ofSeconds(4), remaining);
        WebExtractUtils.HttpExtractResult fast = localHttpFetcher.fetch(url, fastHttpTimeout);

        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200) {
            if (!AntiBotDetectionFilter.isBotBlocked(fast.statusCode(), fast.text())) {
                metrics.recordFastHttpSuccess();
                LOGGER.log(
                        Level.INFO,
                        "web_fetch Tier 1 (Fast HTTP) succeeded: url={0}, length={1}",
                        new Object[] {safeLogUrl, fast.text().length()});
                return fast.text();
            } else {
                diagnostics.add(
                        "Tier 1 (Fast HTTP): Anti-bot challenge or WAF block (status "
                                + fast.statusCode()
                                + ")");
                LOGGER.log(
                        Level.INFO,
                        "web_fetch Tier 1 (Fast HTTP) blocked by anti-bot/WAF: url={0}, status={1},"
                                + " proceeding to remote tiers",
                        new Object[] {safeLogUrl, fast.statusCode()});
            }
        } else {
            diagnostics.add(
                    "Tier 1 (Fast HTTP): " + (fast.reason() != null ? fast.reason() : "skipped"));
            LOGGER.log(
                    Level.INFO,
                    "web_fetch Tier 1 (Fast HTTP) bypassed (dynamic/status/length): url={0},"
                            + " reason={1}",
                    new Object[] {safeLogUrl, fast.reason()});
        }

        // Tier 2 & Tier 3: Remote MCP Providers (if enabled)
        if (!remoteFallbackEnabled) {
            diagnostics.add("Tier 2/3 (Remote MCP): Disabled in settings");
            LOGGER.log(Level.FINE, "Remote MCP fallback is disabled; skipping remote providers.");
        } else {
            for (RemoteWebFetchProvider provider : remoteProviders) {
                Duration currentRemaining = getRemainingBudget(deadlineNanos);
                if (currentRemaining.compareTo(MIN_REMAINING_FOR_REMOTE) < 0) {
                    diagnostics.add(
                            provider.getProviderName()
                                    + ": Skipped due to timeout budget exhaustion");
                    LOGGER.log(
                            Level.WARNING,
                            "Timeout budget exhausted ({0}ms remaining); skipping remote provider"
                                    + " {1}",
                            new Object[] {currentRemaining.toMillis(), provider.getProviderName()});
                    break;
                }

                if (!provider.isAvailable()) {
                    diagnostics.add(
                            provider.getProviderName() + ": Circuit breaker OPEN (cooling down)");
                    LOGGER.log(
                            Level.FINE,
                            "Remote provider {0} circuit breaker is OPEN; skipping.",
                            provider.getProviderName());
                    continue;
                }

                metrics.recordRemoteMcpAttempt();
                LOGGER.log(
                        Level.INFO,
                        "web_fetch cascading to remote provider: {0} for URL: {1}",
                        new Object[] {provider.getProviderName(), safeLogUrl});

                String content = provider.fetch(url);
                if (content != null
                        && !content.isBlank()
                        && !AntiBotDetectionFilter.isBotBlockedText(content)) {
                    metrics.recordRemoteMcpSuccess();
                    LOGGER.log(
                            Level.INFO,
                            "web_fetch successfully retrieved content via {0}: URL={1}, length={2}",
                            new Object[] {
                                provider.getProviderName(), safeLogUrl, content.length()
                            });
                    return content;
                }

                diagnostics.add(provider.getProviderName() + ": Returned null or blocked content");
                LOGGER.log(
                        Level.WARNING,
                        "Remote provider {0} returned null or blocked content for URL: {1}",
                        new Object[] {provider.getProviderName(), safeLogUrl});
            }
        }

        // Tier 4: Local Playwright
        LOGGER.log(
                Level.INFO,
                "web_fetch remote providers exhausted, cascading to Tier 4 (Local Playwright): {0}",
                safeLogUrl);
        return executeBrowserFallback(url, localBrowserFallback, deadlineNanos, diagnostics);
    }

    private String executeBrowserFallback(
            String url,
            LocalBrowserFallback localBrowserFallback,
            long deadlineNanos,
            List<String> diagnostics) {
        if (localBrowserFallback == null) {
            metrics.recordTotalFailure();
            return "Error: Local browser fallback is not configured. Diagnostics: ["
                    + String.join("; ", diagnostics)
                    + "]";
        }

        Duration remaining = getRemainingBudget(deadlineNanos);
        if (remaining.compareTo(MIN_REMAINING_FOR_PLAYWRIGHT) < 0) {
            metrics.recordTotalFailure();
            diagnostics.add(
                    "Tier 4 (Playwright): Aborted due to insufficient remaining time ("
                            + remaining.toMillis()
                            + "ms)");
            return "Error: Overall timeout budget exhausted ("
                    + overallBudget.getSeconds()
                    + "s limit). Diagnostics: ["
                    + String.join("; ", diagnostics)
                    + "]";
        }

        metrics.recordPlaywrightAttempt();
        String result = localBrowserFallback.execute(url);

        if (result == null) {
            metrics.recordTotalFailure();
            diagnostics.add("Tier 4 (Playwright): Returned null result");
            return "Error: Local browser fallback returned null content. Diagnostics: ["
                    + String.join("; ", diagnostics)
                    + "]";
        }

        if (AntiBotDetectionFilter.isBotBlockedText(result)) {
            metrics.recordTotalFailure();
            diagnostics.add(
                    "Tier 4 (Playwright): Cloudflare Turnstile/WAF interstitial detected in browser"
                            + " DOM");
            LOGGER.log(
                    Level.WARNING,
                    "Local Playwright page extraction was blocked by website anti-bot protection:"
                            + " URL={0}",
                    UrlPrivacyFilter.sanitizeForLog(url));
            return "Error: Page access was blocked by website anti-bot protection (Cloudflare/WAF)."
                    + " Content snippet: "
                    + result.substring(0, Math.min(result.length(), 300))
                    + ". Diagnostics: ["
                    + String.join("; ", diagnostics)
                    + "]";
        }

        if (result.startsWith("Error:")
                || result.startsWith("Page read timeout")
                || result.startsWith("Page read failed")) {
            metrics.recordTotalFailure();
            diagnostics.add("Tier 4 (Playwright): " + result);
            return "Error: Failed to fetch web page content after cascading fallback. Diagnostics:"
                    + " ["
                    + String.join("; ", diagnostics)
                    + "]";
        }

        metrics.recordPlaywrightSuccess();
        return result;
    }

    private Duration getRemainingBudget(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos > 0 ? Duration.ofNanos(remainingNanos) : Duration.ZERO;
    }

    private static Duration minDuration(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    @Override
    public void close() {
        LOGGER.info("Closing WebFetchFallbackCoordinator and disposing remote providers...");
        for (RemoteWebFetchProvider provider : remoteProviders) {
            try {
                provider.close();
            } catch (Exception e) {
                LOGGER.log(
                        Level.FINE,
                        "Exception closing provider [" + provider.getProviderName() + "]",
                        e);
            }
        }
    }
}
