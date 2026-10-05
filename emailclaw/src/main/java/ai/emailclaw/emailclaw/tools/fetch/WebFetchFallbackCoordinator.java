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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Multi-tier cascading fallback coordinator for resilient web page content fetching.
 *
 * <p>Orchestrates the 4-tier retrieval pipeline:
 * <ol>
 *   <li>Tier 1: Local Fast HTTP (zero latency, static documentation & articles)</li>
 *   <li>Tier 2: Parallel AI Search MCP (free zero-key cloud residential proxy extraction)</li>
 *   <li>Tier 3: Exa AI MCP (free semantic neural web scraper and SPA extractor)</li>
 *   <li>Tier 4: Local Playwright headless browser (ultimate local fallback & intranet support)</li>
 * </ol>
 */
public class WebFetchFallbackCoordinator {

    private static final Logger LOGGER =
            Logger.getLogger(WebFetchFallbackCoordinator.class.getName());

    @FunctionalInterface
    public interface LocalBrowserFallback {
        /**
         * Executes local browser automation fetch (e.g. Playwright).
         *
         * @param url target URL
         * @return extracted text
         */
        String execute(String url);
    }

    private final List<RemoteWebFetchProvider> remoteProviders;

    /**
     * Default constructor creating standard Parallel and Exa MCP providers.
     */
    public WebFetchFallbackCoordinator() {
        this(List.of(new ParallelMcpFetchProvider(), new ExaMcpFetchProvider()));
    }

    /**
     * Pure DI constructor for injecting custom providers.
     *
     * @param remoteProviders ordered list of remote web fetch providers
     */
    public WebFetchFallbackCoordinator(List<RemoteWebFetchProvider> remoteProviders) {
        this.remoteProviders = remoteProviders != null ? List.copyOf(remoteProviders) : List.of();
    }

    /**
     * Fetches web content through the cascading fallback pipeline.
     *
     * @param url target URL to fetch
     * @param localBrowserFallback fallback function executing local Playwright
     * @return extracted web text/markdown
     */
    public String fetch(String url, LocalBrowserFallback localBrowserFallback) {
        if (url == null || url.isBlank()) {
            return "Error: url is required.";
        }

        boolean isPrivate = PrivateNetworkChecker.isPrivateOrLocalAddress(url);
        if (isPrivate) {
            LOGGER.log(
                    Level.INFO,
                    "Target URL is local or private network ({0}). Skipping remote MCP providers.",
                    url);
            return fetchPrivateNetwork(url, localBrowserFallback);
        }

        return fetchPublicNetwork(url, localBrowserFallback);
    }

    private String fetchPrivateNetwork(String url, LocalBrowserFallback localBrowserFallback) {
        WebExtractUtils.HttpExtractResult fast = WebExtractUtils.tryFastHttpExtract(url);
        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200
                && !AntiBotDetectionFilter.isBotBlocked(fast.statusCode(), fast.text())) {
            LOGGER.log(
                    Level.INFO,
                    "web_fetch Tier 1 (Fast HTTP) succeeded for private URL: url={0}, length={1}",
                    new Object[] {url, fast.text().length()});
            return fast.text();
        }

        LOGGER.log(
                Level.INFO,
                "Private URL requires browser rendering, delegating to local Playwright: {0}",
                url);
        return executeBrowserFallback(url, localBrowserFallback);
    }

    private String fetchPublicNetwork(String url, LocalBrowserFallback localBrowserFallback) {
        // Tier 1: Local Fast HTTP
        WebExtractUtils.HttpExtractResult fast = WebExtractUtils.tryFastHttpExtract(url);
        if (fast.ok()
                && !fast.dynamicLikely()
                && fast.text() != null
                && fast.text().length() >= 200) {
            if (!AntiBotDetectionFilter.isBotBlocked(fast.statusCode(), fast.text())) {
                LOGGER.log(
                        Level.INFO,
                        "web_fetch Tier 1 (Fast HTTP) succeeded: url={0}, length={1}",
                        new Object[] {url, fast.text().length()});
                return fast.text();
            } else {
                LOGGER.log(
                        Level.INFO,
                        "web_fetch Tier 1 (Fast HTTP) blocked by anti-bot/WAF: url={0}, status={1},"
                                + " proceeding to Tier 2",
                        new Object[] {url, fast.statusCode()});
            }
        } else {
            LOGGER.log(
                    Level.INFO,
                    "web_fetch Tier 1 (Fast HTTP) passed (dynamic/status/length): url={0},"
                            + " reason={1}, proceeding to Tier 2",
                    new Object[] {url, fast.reason()});
        }

        // Tier 2 & Tier 3: Remote MCP Providers (Parallel AI, Exa AI)
        for (RemoteWebFetchProvider provider : remoteProviders) {
            if (!provider.isAvailable()) {
                LOGGER.log(
                        Level.FINE,
                        "Remote provider {0} is currently unavailable (cooling down), skipping.",
                        provider.getProviderName());
                continue;
            }

            LOGGER.log(
                    Level.INFO,
                    "web_fetch cascading to remote provider: {0} for url: {1}",
                    new Object[] {provider.getProviderName(), url});

            String content = provider.fetch(url);
            if (content != null
                    && !content.isBlank()
                    && !AntiBotDetectionFilter.isBotBlockedText(content)) {
                LOGGER.log(
                        Level.INFO,
                        "web_fetch successfully retrieved content via {0}: url={1}, length={2}",
                        new Object[] {provider.getProviderName(), url, content.length()});
                return content;
            }

            LOGGER.log(
                    Level.WARNING,
                    "Remote provider {0} failed or was blocked for url: {1}, trying next tier.",
                    new Object[] {provider.getProviderName(), url});
        }

        // Tier 4: Local Playwright
        LOGGER.log(
                Level.INFO,
                "web_fetch remote providers exhausted, cascading to Tier 4 (Local Playwright): {0}",
                url);
        return executeBrowserFallback(url, localBrowserFallback);
    }

    private String executeBrowserFallback(String url, LocalBrowserFallback localBrowserFallback) {
        if (localBrowserFallback == null) {
            return "Error: Local browser fallback is not configured.";
        }
        String result = localBrowserFallback.execute(url);
        if (AntiBotDetectionFilter.isBotBlockedText(result)) {
            LOGGER.log(
                    Level.WARNING,
                    "Local Playwright page extraction was blocked by website anti-bot protection:"
                            + " url={0}",
                    url);
            return "Error: Page access was blocked by website anti-bot protection (Cloudflare/WAF)."
                    + " Content: "
                    + result.substring(0, Math.min(result.length(), 300));
        }
        return result;
    }
}
