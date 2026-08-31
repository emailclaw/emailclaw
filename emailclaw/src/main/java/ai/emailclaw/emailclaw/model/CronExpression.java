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
package ai.emailclaw.emailclaw.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 5-field Cron expression parser, supports calculating the next execution time.
 *
 * <p>Format: minute hour day month weekday
 * <br>Field values: *, N, N-M, N,M,..., &#42;/N, N-M/N
 * <br>Weekday uses 3-letter abbreviations (mon,tue,...,sun), compatible with Emailclaw.
 *
 * <p>Pure java.time implementation, no third-party dependencies.
 */
public final class CronExpression {
    private static final Logger LOGGER = Logger.getLogger(CronExpression.class.getName());

    /** Value range of each field. */
    private static final int[] MIN_VALUES = {0, 0, 1, 1, 0};

    private static final int[] MAX_VALUES = {59, 23, 31, 12, 7};

    private static final Map<String, Integer> DOW_NAME_TO_NUM =
            Map.of("sun", 7, "mon", 1, "tue", 2, "wed", 3, "thu", 4, "fri", 5, "sat", 6);

    private final BitSet[] fields = new BitSet[5];

    /**
     * Parse 5-field cron expression.
     *
     * @param cronExpr 5-field cron expression (fields separated by spaces)
     * @throws IllegalArgumentException if the expression is invalid
     */
    public CronExpression(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) {
            throw new IllegalArgumentException("Cron expression cannot be empty");
        }
        String normalized = CronJobModel.normalizeCron5Fields(cronExpr);
        String[] parts = normalized.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException(
                    "Cron expression must contain 5 fields, currently "
                            + parts.length
                            + ": "
                            + cronExpr);
        }
        for (int i = 0; i < 5; i++) {
            fields[i] = parseField(parts[i], i);
        }
    }

    /**
     * Calculate the next execution time of this cron expression based on the given reference time.
     *
     * @param after Reference time (excluding this time itself, i.e., finding the next matching time > after)
     * @return Next execution time
     */
    public ZonedDateTime nextAfter(ZonedDateTime after) {
        // Search starting from the next minute of after (since it triggers at most once per minute)
        ZonedDateTime candidate = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        int maxIterations = 525600; // Prevent infinite loop (search up to one year)
        int iterations = 0;

        while (iterations < maxIterations) {
            iterations++;
            int minute = candidate.getMinute();
            int hour = candidate.getHour();
            int day = candidate.getDayOfMonth();
            int month = candidate.getMonthValue();
            int dow = candidate.getDayOfWeek().getValue(); // 1=Mon..7=Sun

            boolean dowMatch = fields[4].get(dow) || (dow == 7 && fields[4].get(0));

            if (!dowMatch
                    || !fields[3].get(month)
                    || !fields[2].get(day)
                    || !fields[1].get(hour)
                    || !fields[0].get(minute)) {
                // Mismatch, skip to the next minute of the candidate time
                candidate = candidate.plusMinutes(1);
                continue;
            }
            return candidate;
        }
        LOGGER.warning(
                "Unable to find the next execution time for the cron expression within a reasonable"
                        + " range: "
                        + this);
        return null;
    }

    /**
     * Calculate the next execution time based on the given reference time (LocalDateTime + ZoneId).
     */
    public ZonedDateTime nextAfter(LocalDateTime after, ZoneId zone) {
        return nextAfter(after.atZone(zone));
    }

    /**
     * Parse a single field.
     *
     * @param field Field string
     * @param index Field index (0=minute, 1=hour, 2=day, 3=month, 4=dayOfWeek)
     */
    private BitSet parseField(String field, int index) {
        BitSet result = new BitSet(MAX_VALUES[index] + 1);
        if ("*".equals(field)) {
            result.set(MIN_VALUES[index], MAX_VALUES[index] + 1);
            return result;
        }
        // Process multiple values/ranges separated by commas
        String[] tokens = field.split(",");
        for (String token : tokens) {
            parseToken(token.trim(), index, result);
        }
        return result;
    }

    private void parseToken(String token, int index, BitSet result) {
        int step = 1;
        String rangePart = token;

        // Process step size: */N, N-M/N
        if (token.contains("/")) {
            String[] parts = token.split("/", 2);
            rangePart = parts[0];
            step = Integer.parseInt(parts[1]);
            if (step < 1) step = 1;
        }

        int low, high;
        if ("*".equals(rangePart)) {
            low = MIN_VALUES[index];
            high = MAX_VALUES[index];
        } else if (rangePart.contains("-")) {
            String[] bounds = rangePart.split("-", 2);
            low = parseFieldValue(bounds[0], index);
            high = parseFieldValue(bounds[1], index);
        } else {
            low = high = parseFieldValue(rangePart, index);
        }

        for (int val = low; val <= high; val += step) {
            if (val >= MIN_VALUES[index] && val <= MAX_VALUES[index]) {
                result.set(val);
            }
        }
    }

    private int parseFieldValue(String value, int index) {
        // Day of week field may have abbreviations
        if (index == 4) {
            Integer num = DOW_NAME_TO_NUM.get(value.toLowerCase());
            if (num != null) return num;
        }
        // Month field may have abbreviations (we only support numbers, but handle for
        // compatibility)
        if (index == 3) {
            String lower = value.toLowerCase();
            Integer monthNum = MONTH_NAME_TO_NUM.get(lower);
            if (monthNum != null) return monthNum;
        }
        return Integer.parseInt(value);
    }

    private static final Map<String, Integer> MONTH_NAME_TO_NUM =
            Map.ofEntries(
                    Map.entry("jan", 1),
                    Map.entry("feb", 2),
                    Map.entry("mar", 3),
                    Map.entry("apr", 4),
                    Map.entry("may", 5),
                    Map.entry("jun", 6),
                    Map.entry("jul", 7),
                    Map.entry("aug", 8),
                    Map.entry("sep", 9),
                    Map.entry("oct", 10),
                    Map.entry("nov", 11),
                    Map.entry("dec", 12));

    @Override
    public String toString() {
        return Arrays.stream(fields)
                .map(
                        bs -> {
                            if (bs.cardinality() == MAX_VALUES[0] + 1) return "*";
                            return bs.stream()
                                    .mapToObj(Integer::toString)
                                    .collect(Collectors.joining(","));
                        })
                .collect(Collectors.joining(" "));
    }
}
