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

import ai.emailclaw.emailclaw.util.WebExtractUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebFetchFallbackCoordinatorTest {

    private static final LocalHttpFetcher FAILING_HTTP_FETCHER =
            (url, timeout) ->
                    new WebExtractUtils.HttpExtractResult(
                            false, true, "", "403 Forbidden / Anti-bot", 403, "text/html");

    @Test
    @DisplayName(
            "Tier 1 Fast HTTP success should return immediately without calling remote MCP or"
                    + " Playwright")
    void testTier1FastHttpSuccess() {
        String longText = "Fast HTTP retrieved article content. " + "A".repeat(300);
        LocalHttpFetcher successfulHttpFetcher =
                (url, timeout) ->
                        new WebExtractUtils.HttpExtractResult(
                                true, false, longText, "ok", 200, "text/html");

        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        RemoteWebFetchProvider remoteProvider =
                new RemoteWebFetchProvider() {
                    @Override
                    public String getProviderName() {
                        return "Remote_Parallel";
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public String fetch(String url) {
                        remoteCalled.set(true);
                        return "remote text";
                    }
                };

        WebFetchFallbackCoordinator coordinator =
                new WebFetchFallbackCoordinator(
                        successfulHttpFetcher,
                        List.of(remoteProvider),
                        true,
                        Duration.ofSeconds(10),
                        new WebFetchMetrics());

        AtomicBoolean browserCalled = new AtomicBoolean(false);
        String result =
                coordinator.fetch(
                        "https://example.com/blog",
                        url -> {
                            browserCalled.set(true);
                            return "browser text";
                        });

        assertEquals(longText, result);
        assertFalse(
                remoteCalled.get(), "Remote provider should not be called when Tier 1 succeeds");
        assertFalse(
                browserCalled.get(), "Browser fallback should not be called when Tier 1 succeeds");
        assertEquals(1, coordinator.getMetrics().getFastHttpSuccesses());
    }

    @Test
    @DisplayName("Private network URLs should bypass remote providers and route to local fetch")
    void testPrivateNetworkBypass() {
        AtomicBoolean providerCalled = new AtomicBoolean(false);
        RemoteWebFetchProvider mockProvider =
                new RemoteWebFetchProvider() {
                    @Override
                    public String getProviderName() {
                        return "MockProvider";
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public String fetch(String url) {
                        providerCalled.set(true);
                        return "remote content";
                    }
                };

        WebFetchFallbackCoordinator coordinator =
                new WebFetchFallbackCoordinator(FAILING_HTTP_FETCHER, List.of(mockProvider), true);

        String result =
                coordinator.fetch(
                        "http://localhost:8080/dashboard",
                        url -> "local playwright content: dashboard");

        assertEquals("local playwright content: dashboard", result);
        assertFalse(providerCalled.get(), "Remote provider should not be called for private URL");
        assertTrue(coordinator.getMetrics().getPrivateNetworkRoutings() > 0);
    }

    @Test
    @DisplayName(
            "Sensitive URLs with tokens/credentials should strictly bypass remote MCPs for privacy")
    void testSensitiveUrlPrivacyBypass() {
        AtomicBoolean providerCalled = new AtomicBoolean(false);
        RemoteWebFetchProvider mockProvider =
                new RemoteWebFetchProvider() {
                    @Override
                    public String getProviderName() {
                        return "MockProvider";
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public String fetch(String url) {
                        providerCalled.set(true);
                        return "remote content";
                    }
                };

        WebFetchFallbackCoordinator coordinator =
                new WebFetchFallbackCoordinator(FAILING_HTTP_FETCHER, List.of(mockProvider), true);

        String sensitiveUrl = "https://mycompany.com/reset-password?token=secret12345&sig=abc";
        String result =
                coordinator.fetch(
                        sensitiveUrl, url -> "local playwright rendered password reset form");

        assertEquals("local playwright rendered password reset form", result);
        assertFalse(
                providerCalled.get(),
                "Remote provider MUST NOT be called for URLs with sensitive tokens");
        assertTrue(coordinator.getMetrics().getSensitiveUrlBypasses() > 0);
    }

    @Test
    @DisplayName("Cascading fallback should invoke second remote provider if first provider fails")
    void testCascadingToSecondProvider() {
        RemoteWebFetchProvider failingProvider =
                new RemoteWebFetchProvider() {
                    @Override
                    public String getProviderName() {
                        return "FailingProvider";
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public String fetch(String url) {
                        return null; // simulate failure
                    }
                };

        RemoteWebFetchProvider succeedingProvider =
                new RemoteWebFetchProvider() {
                    @Override
                    public String getProviderName() {
                        return "SucceedingProvider";
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public String fetch(String url) {
                        return "# Success from second provider\nContent extracted cleanly.";
                    }
                };

        WebFetchFallbackCoordinator coordinator =
                new WebFetchFallbackCoordinator(
                        FAILING_HTTP_FETCHER, List.of(failingProvider, succeedingProvider), true);

        AtomicBoolean browserCalled = new AtomicBoolean(false);
        String result =
                coordinator.fetch(
                        "https://target.com/article",
                        url -> {
                            browserCalled.set(true);
                            return "browser content";
                        });

        assertEquals("# Success from second provider\nContent extracted cleanly.", result);
        assertFalse(
                browserCalled.get(), "Browser fallback should not be called if provider succeeded");
    }

    @Test
    @DisplayName(
            "When remote fallback is disabled in settings, remote MCP providers should be skipped")
    void testRemoteFallbackDisabledSetting() {
        AtomicBoolean providerCalled = new AtomicBoolean(false);
        RemoteWebFetchProvider mockProvider =
                new RemoteWebFetchProvider() {
                    @Override
                    public String getProviderName() {
                        return "MockProvider";
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public String fetch(String url) {
                        providerCalled.set(true);
                        return "remote text";
                    }
                };

        WebFetchFallbackCoordinator coordinator =
                new WebFetchFallbackCoordinator(
                        FAILING_HTTP_FETCHER,
                        List.of(mockProvider),
                        false); // remote fallback disabled

        String result =
                coordinator.fetch("https://target.com/article", url -> "local playwright content");

        assertEquals("local playwright content", result);
        assertFalse(
                providerCalled.get(),
                "Remote provider should not be called when remote fallback is disabled");
    }

    @Test
    @DisplayName("When all tiers fail, aggregated diagnostic message should be returned")
    void testAggregatedFailureDiagnostics() {
        RemoteWebFetchProvider failingProvider =
                new RemoteWebFetchProvider() {
                    @Override
                    public String getProviderName() {
                        return "TestMcp";
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public String fetch(String url) {
                        return null;
                    }
                };

        WebFetchFallbackCoordinator coordinator =
                new WebFetchFallbackCoordinator(
                        FAILING_HTTP_FETCHER, List.of(failingProvider), true);

        String result =
                coordinator.fetch(
                        "https://target.com/article",
                        url -> "Page read timeout and failed to extract text.");

        assertTrue(
                result.startsWith(
                        "Error: Failed to fetch web page content after cascading fallback"));
        assertTrue(result.contains("Diagnostics: [Tier 1 (Fast HTTP):"));
        assertTrue(result.contains("TestMcp: Returned null or blocked content"));
        assertTrue(result.contains("Tier 4 (Playwright): Page read timeout"));
    }
}
