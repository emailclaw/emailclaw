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
        assertTrue(
                PrivateNetworkChecker.isPrivateOrLocalAddress(
                        "http://internal-app.internal/status"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://database.lan:5432"));
    }

    @Test
    @DisplayName("Private RFC1918 subnets should be detected as private/local")
    void testPrivateSubnets() {
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://10.0.0.1/status"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://192.168.1.100/config"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://172.16.0.1/admin"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://172.31.255.255/admin"));
    }

    @Test
    @DisplayName("CGNAT 100.64.0.0/10 and Link-Local 169.254.0.0/16 should be detected as private")
    void testCgnatAndLinkLocal() {
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://100.64.0.1/status"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://100.127.255.254/status"));
        // 100.128.0.1 is outside the CGNAT 100.64.0.0/10 block
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("http://100.128.0.1/status"));

        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://169.254.1.1/metadata"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://0.0.0.0:8080/"));
    }

    @Test
    @DisplayName("IPv6 loopback, link-local, and ULA should be detected as private")
    void testIpv6PrivateRanges() {
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://[::1]:8080/"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://[fe80::1]:8080/"));
        assertTrue(PrivateNetworkChecker.isPrivateOrLocalAddress("http://[fc00::1]:8080/"));
        assertTrue(
                PrivateNetworkChecker.isPrivateOrLocalAddress("http://[fd12:3456:789a::1]:8080/"));
    }

    @Test
    @DisplayName("Public literal IP addresses should not be detected as private/local")
    void testPublicIps() {
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("http://8.8.8.8/dns"));
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("http://1.1.1.1/dns"));
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("http://93.184.216.34/"));
    }

    @Test
    @DisplayName("Null and blank URLs should return false")
    void testNullOrBlank() {
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress(null));
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress(""));
        assertFalse(PrivateNetworkChecker.isPrivateOrLocalAddress("   "));
    }
}
