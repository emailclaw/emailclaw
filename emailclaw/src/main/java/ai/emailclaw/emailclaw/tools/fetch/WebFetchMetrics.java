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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-memory observability metrics for cascading web fetch operations.
 */
public class WebFetchMetrics {

    private final AtomicLong fastHttpAttempts = new AtomicLong(0);
    private final AtomicLong fastHttpSuccesses = new AtomicLong(0);
    private final AtomicLong remoteMcpAttempts = new AtomicLong(0);
    private final AtomicLong remoteMcpSuccesses = new AtomicLong(0);
    private final AtomicLong playwrightAttempts = new AtomicLong(0);
    private final AtomicLong playwrightSuccesses = new AtomicLong(0);
    private final AtomicLong sensitiveUrlBypasses = new AtomicLong(0);
    private final AtomicLong privateNetworkRoutings = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);

    public void recordFastHttpAttempt() {
        fastHttpAttempts.incrementAndGet();
    }

    public void recordFastHttpSuccess() {
        fastHttpSuccesses.incrementAndGet();
    }

    public void recordRemoteMcpAttempt() {
        remoteMcpAttempts.incrementAndGet();
    }

    public void recordRemoteMcpSuccess() {
        remoteMcpSuccesses.incrementAndGet();
    }

    public void recordPlaywrightAttempt() {
        playwrightAttempts.incrementAndGet();
    }

    public void recordPlaywrightSuccess() {
        playwrightSuccesses.incrementAndGet();
    }

    public void recordSensitiveUrlBypass() {
        sensitiveUrlBypasses.incrementAndGet();
    }

    public void recordPrivateNetworkRouting() {
        privateNetworkRoutings.incrementAndGet();
    }

    public void recordTotalFailure() {
        totalFailures.incrementAndGet();
    }

    public long getFastHttpAttempts() {
        return fastHttpAttempts.get();
    }

    public long getFastHttpSuccesses() {
        return fastHttpSuccesses.get();
    }

    public long getRemoteMcpAttempts() {
        return remoteMcpAttempts.get();
    }

    public long getRemoteMcpSuccesses() {
        return remoteMcpSuccesses.get();
    }

    public long getPlaywrightAttempts() {
        return playwrightAttempts.get();
    }

    public long getPlaywrightSuccesses() {
        return playwrightSuccesses.get();
    }

    public long getSensitiveUrlBypasses() {
        return sensitiveUrlBypasses.get();
    }

    public long getPrivateNetworkRoutings() {
        return privateNetworkRoutings.get();
    }

    public long getTotalFailures() {
        return totalFailures.get();
    }

    @Override
    public String toString() {
        return String.format(
                "WebFetchMetrics[fastHttp=%d/%d, remoteMcp=%d/%d, playwright=%d/%d,"
                        + " sensitiveBypasses=%d, privateRoutings=%d, totalFailures=%d]",
                fastHttpSuccesses.get(),
                fastHttpAttempts.get(),
                remoteMcpSuccesses.get(),
                remoteMcpAttempts.get(),
                playwrightSuccesses.get(),
                playwrightAttempts.get(),
                sensitiveUrlBypasses.get(),
                privateNetworkRoutings.get(),
                totalFailures.get());
    }
}
