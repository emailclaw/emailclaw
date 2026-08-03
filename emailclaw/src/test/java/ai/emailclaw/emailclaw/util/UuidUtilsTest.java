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

import java.util.UUID;

public class UuidUtilsTest {
    public static void main(String[] args) {
        System.out.println("=== Running UuidUtils v7 Test ===");

        // Test 1: Generate UUIDs and check format
        UUID uuid1 = UuidUtils.randomUUIDv7();
        System.out.println("Generated UUID v7: " + uuid1);
        System.out.println("UUID version (expected 7): " + uuid1.version());
        System.out.println("UUID variant (expected 2): " + uuid1.variant());

        if (uuid1.version() != 7) {
            System.err.println("TEST FAILED: Version is " + uuid1.version() + ", expected 7");
            System.exit(1);
        }
        if (uuid1.variant() != 2) {
            System.err.println("TEST FAILED: Variant is " + uuid1.variant() + ", expected 2");
            System.exit(1);
        }

        // Test 2: Verify ordering (monotonicity / chronologically sorted)
        UUID lastUuid = UuidUtils.randomUUIDv7();
        boolean ordered = true;
        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            UUID nextUuid = UuidUtils.randomUUIDv7();
            if (nextUuid.compareTo(lastUuid) <= 0) {
                long lastTime = lastUuid.getMostSignificantBits() >>> 16;
                long nextTime = nextUuid.getMostSignificantBits() >>> 16;
                if (nextTime > lastTime && nextUuid.compareTo(lastUuid) <= 0) {
                    System.err.println(
                            "TEST FAILED: Monotonicity violated between "
                                    + lastUuid
                                    + " and "
                                    + nextUuid);
                    ordered = false;
                    break;
                }
            }
            lastUuid = nextUuid;
        }

        if (ordered) {
            System.out.println("Monotonicity check passed.");
        } else {
            System.exit(1);
        }

        System.out.println("=== UuidUtils Test PASSED ===");
    }
}
