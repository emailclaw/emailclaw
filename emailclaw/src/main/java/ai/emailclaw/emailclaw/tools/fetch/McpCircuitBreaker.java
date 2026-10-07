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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe circuit breaker with CLOSED, OPEN, and HALF_OPEN states for remote MCP providers.
 *
 * <p>Strictly distinguishes transport-level errors (network timeouts, DNS errors, HTTP 5xx,
 * HTTP 429, protocol mismatch) from target page content blocks. Target page content blocks
 * (such as empty content or anti-bot challenge on the destination site) do NOT trip this
 * circuit breaker.
 */
public class McpCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static final Logger LOGGER = Logger.getLogger(McpCircuitBreaker.class.getName());

    private final String name;
    private final int failureThreshold;
    private final Duration cooldownDuration;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitTripUntil = new AtomicLong(0L);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    public McpCircuitBreaker(String name) {
        this(name, 3, Duration.ofSeconds(60));
    }

    public McpCircuitBreaker(String name, int failureThreshold, Duration cooldownDuration) {
        this.name = (name != null && !name.isBlank()) ? name.trim() : "default_mcp";
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownDuration =
                (cooldownDuration != null) ? cooldownDuration : Duration.ofSeconds(60);
    }

    /**
     * Checks if a request is permitted to proceed.
     * Transitions from OPEN to HALF_OPEN when cooldown expires.
     *
     * @return true if permitted, false if blocked by OPEN circuit
     */
    public boolean allowRequest() {
        long now = System.currentTimeMillis();
        long tripUntil = circuitTripUntil.get();

        if (tripUntil == 0L || now >= tripUntil) {
            if (state.get() == State.OPEN) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    LOGGER.log(
                            Level.INFO,
                            "Circuit breaker [{0}] cooldown elapsed. Transitioning to HALF_OPEN"
                                    + " probe state.",
                            name);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Records a successful remote transport call. Resets failure count and closes circuit.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        circuitTripUntil.set(0L);
        State prev = state.getAndSet(State.CLOSED);
        if (prev != State.CLOSED) {
            LOGGER.log(
                    Level.INFO,
                    "Circuit breaker [{0}] successfully restored to CLOSED state.",
                    name);
        }
    }

    /**
     * Records a transport-level error (connect failure, network timeout, 5xx server error, 429 rate limit).
     *
     * @param cause underlying cause
     */
    public void recordTransportFailure(Throwable cause) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold || state.get() == State.HALF_OPEN) {
            long now = System.currentTimeMillis();
            long tripUntil = now + cooldownDuration.toMillis();
            circuitTripUntil.set(tripUntil);
            state.set(State.OPEN);
            LOGGER.log(
                    Level.WARNING,
                    "Circuit breaker [{0}] tripped to OPEN after {1} failure(s) (cause: {2})."
                            + " Cooldown until: {3}",
                    new Object[] {
                        name, failures, cause != null ? cause.getMessage() : "unknown", tripUntil
                    });
        } else {
            LOGGER.log(
                    Level.FINE,
                    "Circuit breaker [{0}] recorded failure #{1} (cause: {2})",
                    new Object[] {name, failures, cause != null ? cause.getMessage() : "unknown"});
        }
    }

    public State getState() {
        allowRequest();
        return state.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public String getName() {
        return name;
    }
}
