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

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Intranet routing helper for identifying loopback, private intranet, and local network addresses.
 *
 * <p>Directs traffic destined for private enterprise endpoints or local services to the
 * local fetching engine (Tier 1 Fast HTTP or Tier 4 Playwright) rather than exposing internal
 * hosts or intranets to third-party public cloud MCP providers.
 */
public final class PrivateNetworkChecker {

    private static final Logger LOGGER = Logger.getLogger(PrivateNetworkChecker.class.getName());

    private PrivateNetworkChecker() {
        // Utility class, prevent instantiation
    }

    /**
     * Determines whether the specified URL targets a loopback, private intranet, or local network address.
     *
     * @param urlString target URL
     * @return true if the destination host is private or local
     */
    public static boolean isPrivateOrLocalAddress(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(urlString);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String hostLower = host.toLowerCase(Locale.ROOT);

            // Fast-path hostname matching
            if ("localhost".equals(hostLower)
                    || hostLower.endsWith(".local")
                    || hostLower.endsWith(".internal")
                    || hostLower.endsWith(".localhost")
                    || hostLower.endsWith(".lan")
                    || hostLower.endsWith(".intranet")) {
                LOGGER.log(
                        Level.FINE,
                        "PrivateNetworkChecker matched local/intranet domain suffix: {0}",
                        hostLower);
                return true;
            }

            // Strip IPv6 bracket notation if present (e.g. "[::1]" -> "::1")
            String cleanHost = hostLower;
            if (cleanHost.startsWith("[") && cleanHost.endsWith("]")) {
                cleanHost = cleanHost.substring(1, cleanHost.length() - 1);
            }

            InetAddress address = InetAddress.getByName(cleanHost);
            return isPrivateAddress(address);
        } catch (Exception e) {
            LOGGER.log(
                    Level.FINE,
                    "Failed to resolve host for intranet routing check: " + urlString,
                    e);
        }
        return false;
    }

    /**
     * Checks if an {@link InetAddress} belongs to loopback, RFC 1918, CGNAT, link-local,
     * or IPv6 ULA private network ranges.
     *
     * @param address IP address to verify
     * @return true if address is private or loopback
     */
    public static boolean isPrivateAddress(InetAddress address) {
        if (address == null) {
            return false;
        }

        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes == null) {
            return false;
        }

        // IPv4 evaluation (4 bytes)
        if (bytes.length == 4) {
            return isPrivateIpv4(bytes);
        }

        // IPv6 evaluation (16 bytes)
        if (bytes.length == 16) {
            // IPv4-mapped IPv6 (::ffff:0:0/96)
            if (isIpv4MappedIpv6(bytes)) {
                byte[] ipv4 = new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]};
                return isPrivateIpv4(ipv4);
            }

            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // IPv6 Unique Local Address (ULA) fc00::/7 (covers fc00::/8 and fd00::/8)
            if ((b0 & 0xFE) == 0xFC) {
                return true;
            }

            // IPv6 site-local (deprecated fec0::/10)
            if (b0 == 0xFE && (b1 & 0xC0) == 0xC0) {
                return true;
            }

            // IPv6 link-local fe80::/10
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
                return true;
            }
        }

        return false;
    }

    private static boolean isPrivateIpv4(byte[] bytes) {
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;

        // Current network: 0.0.0.0/8
        if (b0 == 0) {
            return true;
        }

        // Loopback: 127.0.0.0/8
        if (b0 == 127) {
            return true;
        }

        // RFC 1918: 10.0.0.0/8
        if (b0 == 10) {
            return true;
        }

        // CGNAT (Carrier-Grade NAT): 100.64.0.0/10 (100.64.0.0 - 100.127.255.255)
        if (b0 == 100 && (b1 & 0xC0) == 64) {
            return true;
        }

        // RFC 1918: 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
            return true;
        }

        // Link-local: 169.254.0.0/16
        if (b0 == 169 && b1 == 254) {
            return true;
        }

        // RFC 1918: 192.168.0.0/16
        if (b0 == 192 && b1 == 168) {
            return true;
        }

        // Broadcast: 255.255.255.255
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        if (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255) {
            return true;
        }

        return false;
    }

    private static boolean isIpv4MappedIpv6(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }
}
