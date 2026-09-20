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
package ai.emailclaw.emailclaw.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.harness.agent.gateway.LocalSessionTurnGate;
import io.agentscope.harness.agent.gateway.SessionTurnGate;
import io.agentscope.harness.agent.gateway.TurnLease;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTurnGateTest {

    private SessionTurnGate turnGate;

    @BeforeEach
    void setUp() {
        turnGate = new LocalSessionTurnGate();
    }

    @Test
    @DisplayName("Acquiring lease marks session running until lease is closed")
    void testLeaseLifecycle() throws Exception {
        String sessionId = "session-abc";
        assertFalse(turnGate.isRunning(sessionId));

        try (TurnLease lease = turnGate.acquire(sessionId)) {
            assertTrue(turnGate.isRunning(sessionId));
        }

        assertFalse(turnGate.isRunning(sessionId));
    }

    @Test
    @DisplayName("Distinct session keys can acquire concurrently")
    void testConcurrentDistinctSessions() throws Exception {
        String session1 = "session-1";
        String session2 = "session-2";

        try (TurnLease lease1 = turnGate.acquire(session1);
                TurnLease lease2 = turnGate.acquire(session2)) {
            assertTrue(turnGate.isRunning(session1));
            assertTrue(turnGate.isRunning(session2));
        }

        assertFalse(turnGate.isRunning(session1));
        assertFalse(turnGate.isRunning(session2));
    }

    @Test
    @DisplayName("Same session key acquisition waits until existing lease is closed")
    void testSameSessionMutualExclusion() throws Exception {
        String sessionId = "session-sync";
        TurnLease lease1 = turnGate.acquire(sessionId);
        assertTrue(turnGate.isRunning(sessionId));

        CountDownLatch secondAcquired = new CountDownLatch(1);
        AtomicBoolean secondStarted = new AtomicBoolean(false);

        Thread thread2 =
                new Thread(
                        () -> {
                            try {
                                secondStarted.set(true);
                                try (TurnLease lease2 = turnGate.acquire(sessionId)) {
                                    secondAcquired.countDown();
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        });
        thread2.start();

        // Wait until thread2 is running
        Thread.sleep(100);
        // secondAcquired should NOT be released yet because lease1 is held
        assertFalse(secondAcquired.await(200, TimeUnit.MILLISECONDS));

        // Release lease1
        lease1.close();

        // Now thread2 should acquire and finish
        assertTrue(secondAcquired.await(2, TimeUnit.SECONDS));
        thread2.join(2000);

        assertFalse(turnGate.isRunning(sessionId));
    }
}
