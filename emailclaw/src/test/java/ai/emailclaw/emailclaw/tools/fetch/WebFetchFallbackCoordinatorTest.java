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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebFetchFallbackCoordinatorTest {

    @Test
    @DisplayName(
            "Private network URLs should bypass remote providers and use local browser fallback")
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
                new WebFetchFallbackCoordinator(List.of(mockProvider));

        String result =
                coordinator.fetch(
                        "http://localhost:8080/dashboard",
                        url -> "local playwright content: dashboard");

        assertEquals("local playwright content: dashboard", result);
        assertTrue(!providerCalled.get(), "Remote provider should not be called for private URL");
    }

    @Test
    @DisplayName("Cascading fallback should invoke second provider if first provider fails")
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
                new WebFetchFallbackCoordinator(List.of(failingProvider, succeedingProvider));

        AtomicBoolean browserCalled = new AtomicBoolean(false);
        String result =
                coordinator.fetch(
                        "https://unreachable-mock-target-1234.org/article",
                        url -> {
                            browserCalled.set(true);
                            return "browser content";
                        });

        assertEquals("# Success from second provider\nContent extracted cleanly.", result);
        assertTrue(
                !browserCalled.get(),
                "Browser fallback should not be called if provider succeeded");
    }

    @Test
    @DisplayName("When all remote providers fail, local browser fallback should be executed")
    void testFallbackToBrowserWhenAllProvidersFail() {
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
                        return null;
                    }
                };

        WebFetchFallbackCoordinator coordinator =
                new WebFetchFallbackCoordinator(List.of(failingProvider));

        String result =
                coordinator.fetch(
                        "https://unreachable-mock-target-1234.org/article",
                        url -> "browser fallback extracted content");

        assertEquals("browser fallback extracted content", result);
    }
}
