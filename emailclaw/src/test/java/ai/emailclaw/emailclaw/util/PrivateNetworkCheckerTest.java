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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrivateNetworkCheckerTest {

    @Test
    @DisplayName("Localhost and 127.0.0.1 should be detected as private/local")
    void testLocalhostAndLoopback() {
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://localhost:8080/docs"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://127.0.0.1:3000/api"));
        assertTrue(
                PrivateNetworkChecker.isPrivateOrLocalAddress("http://service.local/index.html"));
    }

    @Test
    @DisplayName("Private RFC1918 subnets should be detected as private/local")
    void testPrivateSubnets() {
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://10.0.0.1/status"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://192.168.1.100/config"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://172.16.0.1/admin"));
    }

    @Test
    @DisplayName("Public internet domain names should not be detected as private/local")
    void testPublicDomains() {
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("https://example.com"));
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("https://docs.oracle.com/java"));
    }

    @Test
    @DisplayName("Null and blank URLs should return false")
    void testNullOrBlank() {
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress(null));
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress(""));
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("   "));
    }
}
