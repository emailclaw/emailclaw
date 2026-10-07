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

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class McpCircuitBreakerTest {

    @Test
    @DisplayName(
            "Circuit breaker trips to OPEN after threshold transport failures and resets on"
                    + " success")
    void testCircuitBreakerTripsAndRecovers() throws InterruptedException {
        // 2 failures threshold, short 50ms cooldown for fast test execution
        McpCircuitBreaker breaker = new McpCircuitBreaker("test-breaker", 2, Duration.ofMillis(50));

        assertEquals(McpCircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.allowRequest());

        // First transport failure
        breaker.recordTransportFailure(new RuntimeException("connect timeout"));
        assertEquals(1, breaker.getConsecutiveFailures());
        assertTrue(breaker.allowRequest());

        // Second transport failure -> trips
        breaker.recordTransportFailure(new RuntimeException("503 service unavailable"));
        assertEquals(2, breaker.getConsecutiveFailures());
        assertEquals(McpCircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());

        // Wait for cooldown
        Thread.sleep(60);

        // Should now transition to HALF_OPEN and allow probe
        assertTrue(breaker.allowRequest());
        assertEquals(McpCircuitBreaker.State.HALF_OPEN, breaker.getState());

        // Successful probe resets circuit to CLOSED
        breaker.recordSuccess();
        assertEquals(McpCircuitBreaker.State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getConsecutiveFailures());
        assertTrue(breaker.allowRequest());
    }
}
