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
import java.util.concurrent.ThreadLocalRandom;

/** Do not use UUID.randomUUID() directly in the project, instead use UuidUtils.randomUUIDv7() uniformly, which is a time-ordered UUID v7
 * UUID utility functions.
 */
public class UuidUtils {
    // Time-ordered UUID v7 simplest, lock-free, highly performant implementation
    public static UUID randomUUIDv7() {
        var random = ThreadLocalRandom.current();
        long timestamp = System.currentTimeMillis();

        // 1. Construct Most Significant Bits
        // (timestamp & 0xFFFFFFFFFFFFL) : Intercept the low 48 bits of the timestamp
        // << 16                         : Left shift 16 bits, making room for version and random
        // number
        // 0x7000L                       : 4-bit version (0111)
        // (random.nextLong() & 0x0FFFL) : 12-bit random number
        long mostSigBits =
                ((timestamp & 0xFFFFFFFFFFFFL) << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);

        // 2. Construct Least Significant Bits
        // 0x8000000000000000L           : Highest bit is 1, next highest is 0, i.e., variant 10
        // (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) : 62-bit random number
        long leastSigBits = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        // 3. Return standard Java UUID object
        return new UUID(mostSigBits, leastSigBits);
    }
}
