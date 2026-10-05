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
 * Utility for detecting loopback, private intranet, or local network addresses.
 *
 * <p>Used to prevent routing private network requests to public remote MCP services
 * and to prevent Server-Side Request Forgery (SSRF).
 */
public final class PrivateNetworkChecker {

    private static final Logger LOGGER = Logger.getLogger(PrivateNetworkChecker.class.getName());

    private PrivateNetworkChecker() {
        // Utility class, prevent instantiation
    }

    /**
     * Determines whether the specified URL targets a loopback, private, or local network address.
     *
     * @param urlString target URL
     * @return true if the address is local or private
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
            if ("localhost".equals(hostLower)
                    || hostLower.endsWith(".local")
                    || hostLower.endsWith(".internal")
                    || hostLower.endsWith(".localhost")) {
                LOGGER.log(
                        Level.FINE,
                        "PrivateNetworkChecker detected local hostname: {0}",
                        hostLower);
                return true;
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                LOGGER.log(
                        Level.FINE,
                        "PrivateNetworkChecker detected private/loopback IP: {0} ({1})",
                        new Object[] {host, address.getHostAddress()});
                return true;
            }

            byte[] bytes = address.getAddress();
            if (bytes != null && bytes.length == 4) {
                int b0 = bytes[0] & 0xFF;
                int b1 = bytes[1] & 0xFF;
                // 10.0.0.0/8
                if (b0 == 10) {
                    return true;
                }
                // 172.16.0.0/12
                if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
                    return true;
                }
                // 192.168.0.0/16
                if (b0 == 192 && b1 == 168) {
                    return true;
                }
                // 127.0.0.0/8
                if (b0 == 127) {
                    return true;
                }
                // 169.254.0.0/16
                if (b0 == 169 && b1 == 254) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.FINE,
                    "Failed to resolve host for private network check: " + urlString,
                    e);
        }
        return false;
    }
}
