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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Centralized date and time utility functions.
 *
 * <p>Provides standardized epoch millisecond retrieval and localized formatting.
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private DateTimeUtils() {}

    /**
     * Gets current system time in UTC epoch milliseconds.
     *
     * @return current epoch milliseconds
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Formats an epoch millisecond timestamp into a human-readable string using the default pattern
     * "yyyy-MM-dd HH:mm:ss" in system default timezone.
     *
     * @param epochMillis timestamp in milliseconds
     * @return formatted date string, or empty string if timestamp &lt;= 0
     */
    public static String formatEpochMillis(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return DEFAULT_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Formats an epoch millisecond timestamp with minute precision ("yyyy-MM-dd HH:mm").
     *
     * @param epochMillis timestamp in milliseconds
     * @return formatted date string, or empty string if timestamp &lt;= 0
     */
    public static String formatEpochMillisMinute(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return MINUTE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Formats an epoch millisecond timestamp using a custom DateTimeFormatter.
     *
     * @param epochMillis timestamp in milliseconds
     * @param formatter custom formatter
     * @return formatted date string, or empty string if timestamp &lt;= 0
     */
    public static String formatEpochMillis(long epochMillis, DateTimeFormatter formatter) {
        if (epochMillis <= 0) {
            return "";
        }
        DateTimeFormatter targetFormatter = formatter != null ? formatter : DEFAULT_FORMATTER;
        return targetFormatter.format(Instant.ofEpochMilli(epochMillis));
    }
}
