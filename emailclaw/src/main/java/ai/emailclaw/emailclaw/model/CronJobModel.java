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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Top-level container for CronJob related data models.
 *
 * <p>This class is not instantiated directly, all scheduled task-related records are defined as internal static records.
 * Also provides {@link #DEFAULTS} as initial value constants for new tasks.
 */
public final class CronJobModel {

    private static final Logger LOGGER = Logger.getLogger(CronJobModel.class.getName());

    private CronJobModel() {}

    /**
     * Default channel name.
     */
    public static final String DEFAULT_CHANNEL = "console";

    /**
     * Default timezone ID for schedules without an explicit timezone.
     *
     * <p>Single source of truth is {@code GlobalConfig.timeZone}: it is seeded at application
     * startup (see ApplicationBootstrap) and refreshed on every global-config change. Falls
     * back to the JVM system default until then.
     */
    private static volatile String defaultTimezone = ZoneId.systemDefault().getId();

    /**
     * Get the default timezone ID used when a schedule omits its own.
     *
     * @return a valid IANA timezone ID, e.g. "Asia/Shanghai"
     */
    public static String getDefaultTimezone() {
        return defaultTimezone;
    }

    /**
     * Set the default timezone ID used when a schedule omits its own.
     *
     * <p>Blank IDs fall back to the JVM system default; invalid IDs are rejected with a
     * warning and the previous value is kept.
     *
     * @param zoneId IANA timezone ID sourced from {@code GlobalConfig.timeZone}
     */
    public static void setDefaultTimezone(String zoneId) {
        String candidate =
                zoneId == null || zoneId.isBlank() ? ZoneId.systemDefault().getId() : zoneId.trim();
        try {
            ZoneId.of(candidate);
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Ignoring invalid default timezone: {0}, keeping {1}",
                    new Object[] {zoneId, defaultTimezone});
            return;
        }
        defaultTimezone = candidate;
    }

    // ======================== Schedule Specification ========================
    /**
     * Schedule plan.
     *
     * @param type              Schedule type: "cron" or "once"
     * @param cron              cron expression (5 fields), valid only when type="cron"
     * @param runAt             Absolute time for one-time execution (ISO-8601), valid only when type="once"
     * @param timezone          Timezone ID, e.g. "America/New_York"
     * @param repeatEveryDays   Repeat interval in days, valid only when type="once" and repeat is needed
     * @param repeatEndType     Repeat end type: "never" / "until" / "count"
     * @param repeatUntil       Repeat until time, valid only when repeatEndType="until"
     * @param repeatCount       Repeat count, valid only when repeatEndType="count"
     */
    public record ScheduleSpec(
            String type,
            String cron,
            String runAt,
            String timezone,
            Integer repeatEveryDays,
            String repeatEndType,
            String repeatUntil,
            Integer repeatCount) {

        /**
         * Create a default cron type schedule (09:00 every day).
         */
        public static ScheduleSpec defaultCron() {
            return new ScheduleSpec(
                    "cron", "0 9 * * *", null, getDefaultTimezone(), null, null, null, null);
        }

        /**
         * Create a default once type schedule (one hour later).
         */
        public static ScheduleSpec defaultOnce() {
            return new ScheduleSpec(
                    "once",
                    null,
                    LocalDateTime.now()
                            .plusHours(1)
                            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    getDefaultTimezone(),
                    null,
                    null,
                    null,
                    null);
        }

        public ScheduleSpec {
            if (type == null) type = "cron";
            if (timezone == null || timezone.isBlank()) timezone = getDefaultTimezone();
            // Normalized validation logic matching Emailclaw ScheduleSpec._validate_schedule_type
            if ("cron".equals(type)) {
                if (cron == null || cron.isBlank()) {
                    cron = "0 9 * * *";
                }
                cron = normalizeCron5Fields(cron);
                runAt = null;
                repeatEveryDays = null;
                repeatEndType = null;
                repeatUntil = null;
                repeatCount = null;
            } else if ("once".equals(type)) {
                cron = null;
                if (repeatEveryDays == null) {
                    repeatEndType = null;
                    repeatUntil = null;
                    repeatCount = null;
                } else {
                    if (repeatEndType == null) repeatEndType = "never";
                    if (!"never".equals(repeatEndType)
                            && !"until".equals(repeatEndType)
                            && !"count".equals(repeatEndType)) {
                        repeatEndType = "never";
                    }
                    if ("never".equals(repeatEndType)) {
                        repeatUntil = null;
                        repeatCount = null;
                    } else if ("until".equals(repeatEndType)) {
                        repeatCount = null;
                    } else if ("count".equals(repeatEndType)) {
                        repeatUntil = null;
                    }
                }
            }
        }
    }

    // ======================== Dispatch Target ========================
    /**
     * Dispatch target (User + Session).
     *
     * @param userId    Target user ID
     * @param sessionId Target session ID
     */
    public record DispatchTarget(String userId, String sessionId) {

        public DispatchTarget {
            if (userId == null) userId = "";
            if (sessionId == null) sessionId = "";
        }
    }

    // ======================== Dispatch Specification ========================
    /**
     * Dispatch specification.
     *
     * @param type    Dispatch type: fixed "channel"
     * @param channel Channel name
     * @param target  Dispatch target
     * @param mode    Dispatch mode: "stream" or "final"
     * @param meta    Additional metadata
     */
    public record DispatchSpec(
            String type,
            String channel,
            DispatchTarget target,
            String mode,
            Map<String, Object> meta) {

        public DispatchSpec {
            if (type == null) type = "channel";
            if (channel == null || channel.isBlank()) channel = DEFAULT_CHANNEL;
            if (target == null) target = new DispatchTarget("", "");
            if (mode == null) mode = "final";
            if (meta == null) meta = Collections.emptyMap();
        }
    }

    // ======================== Runtime Specification ========================
    /**
     * Job runtime specification.
     *
     * @param maxConcurrency      Maximum concurrency
     * @param timeoutSeconds      Timeout in seconds
     * @param misfireGraceSeconds Grace period after misfire (seconds)
     * @param shareSession        Whether to share session context with target user
     */
    public record JobRuntimeSpec(
            int maxConcurrency, int timeoutSeconds, int misfireGraceSeconds, boolean shareSession) {

        public static JobRuntimeSpec defaults() {
            return new JobRuntimeSpec(1, 120, 60, true);
        }

        public JobRuntimeSpec {
            if (maxConcurrency < 1) maxConcurrency = 1;
            if (timeoutSeconds < 1) timeoutSeconds = 120;
            if (misfireGraceSeconds < 0) misfireGraceSeconds = 60;
        }
    }

    // ======================== Request Body ========================
    /**
     * Agent task request body (passed through to AgentScope runner.stream_query).
     *
     * @param input     Request input (can be any structure like List/Map)
     * @param sessionId Session ID
     * @param userId    User ID
     */
    public record CronJobRequest(Object input, String sessionId, String userId) {

        public CronJobRequest {
            if (sessionId == null) sessionId = "";
            if (userId == null) userId = "";
        }
    }

    // ======================== Task Type Constants ========================
    public static final String TASK_TYPE_TEXT = "text";

    public static final String TASK_TYPE_AGENT = "agent";

    // ======================== Job Specification (Core Model) ========================
    /**
     * Scheduled task full specification definition.
     *
     * <p>Corresponds to Emailclaw's {@code CronJobSpec} model.
     *
     * @param id                Task unique identifier
     * @param projectId         Affiliated project ID
     * @param name              Task name
     * @param enabled           Whether enabled
     * @param schedule          Schedule plan
     * @param taskId            Associated task (TaskInfo) ID
     * @param inputPrompt       Prompt sent when task is triggered (text input)
     * @param dispatch          Dispatch specification
     * @param saveResultToInbox Whether to save execution result to Inbox
     * @param runtime           Runtime specification
     * @param meta              Additional metadata
     */
    public record CronJobSpec(
            String id,
            String projectId,
            String name,
            boolean enabled,
            ScheduleSpec schedule,
            String taskId,
            String inputPrompt,
            DispatchSpec dispatch,
            Boolean saveResultToInbox,
            JobRuntimeSpec runtime,
            Map<String, Object> meta,
            Integer countdown)
            implements TaskDefinition {

        public CronJobSpec {
            if (id == null) id = "";
            if (projectId == null || projectId.isBlank()) projectId = "default";
            if (name == null) name = "";
            if (schedule == null) schedule = ScheduleSpec.defaultCron();
            if (taskId == null) taskId = "";
            if (inputPrompt == null) inputPrompt = "";
            if (dispatch == null) {
                dispatch =
                        new DispatchSpec(
                                "channel",
                                DEFAULT_CHANNEL,
                                new DispatchTarget("", ""),
                                "final",
                                Collections.emptyMap());
            }
            if (runtime == null) runtime = JobRuntimeSpec.defaults();
            if (meta == null) meta = Collections.emptyMap();
            if (saveResultToInbox == null) {
                saveResultToInbox = true;
            }
            if (countdown == null) {
                countdown = -1;
            }
        }

        /**
         * Conveniently create an enabled/disabled copy.
         */
        public CronJobSpec withEnabled(boolean enabled) {
            return new CronJobSpec(
                    id,
                    projectId,
                    name,
                    enabled,
                    schedule,
                    taskId,
                    inputPrompt,
                    dispatch,
                    saveResultToInbox,
                    runtime,
                    meta,
                    countdown);
        }

        /**
         * Conveniently create a copy with a replaced ID (for server-side ID generation).
         */
        public CronJobSpec withId(String newId) {
            return new CronJobSpec(
                    newId,
                    projectId,
                    name,
                    enabled,
                    schedule,
                    taskId,
                    inputPrompt,
                    dispatch,
                    saveResultToInbox,
                    runtime,
                    meta,
                    countdown);
        }
    }

    // ======================== File Storage Root Structure ========================
    /**
     * Scheduled task file root structure.
     *
     * @param version File format version
     * @param jobs    Task list
     */
    public record JobsFile(int version, List<CronJobSpec> jobs) {

        public JobsFile {
            if (jobs == null) jobs = List.of();
        }

        public static JobsFile empty() {
            return new JobsFile(1, List.of());
        }
    }

    // ======================== Runtime State ========================
    /**
     * Scheduled task runtime state.
     *
     * @param nextRunAt  Next execution time
     * @param lastRunAt  Last execution time
     * @param lastStatus Last execution status
     * @param lastError  Last execution error message
     */
    public record CronJobState(
            String nextRunAt, String lastRunAt, CronJobStatus lastStatus, String lastError) {

        public static CronJobState empty() {
            return new CronJobState(null, null, null, null);
        }
    }

    // ======================== Execution Record ========================
    /**
     * Single execution record.
     *
     * @param runAt   Execution time (ISO-8601)
     * @param status  Execution status
     * @param error   Error message
     * @param trigger Trigger mode: "scheduled" or "manual"
     */
    public record CronExecutionRecord(
            String runAt, CronJobStatus status, String error, CronJobTrigger trigger) {}

    // ======================== Dispatch Target List Item ========================
    /**
     * Dispatch target list item (for UI dropdown selection).
     *
     * @param channel   Channel
     * @param userId    User ID
     * @param sessionId Session ID
     */
    public record CronDispatchTargetItem(String channel, String userId, String sessionId) {}

    // ======================== New Task Defaults (Matches Emailclaw) ========================
    public static final CronJobSpec DEFAULTS =
            new CronJobSpec( // id
                    "", // projectId
                    "default", // name
                    "", // enabled
                    false,
                    ScheduleSpec.defaultCron(), // taskId
                    "", // inputPrompt
                    "Execute scheduled task",
                    new DispatchSpec(
                            "channel",
                            DEFAULT_CHANNEL,
                            new DispatchTarget("", ""),
                            "final",
                            Collections.emptyMap()), // saveResultToInbox
                    true,
                    JobRuntimeSpec.defaults(),
                    Collections.emptyMap(), // countdown
                    -1);

    // ======================== Utility Methods ========================
    /**
     * Convert the number representation of the 5th field (Day of Week) of the cron expression to a 3-letter abbreviation.
     *
     * <p>Emailclaw compatibility: APScheduler uses ISO 8601 day of week numbering (0=Mon…6=Sun),
     * while standard crontab uses (0=Sun…6=Sat). The 3-letter abbreviation (mon,tue,…) is unambiguous in both systems.
     */
    public static String normalizeCron5Fields(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) return "0 9 * * *";
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length == 5) {
            parts[4] = dowNumbersToNames(parts[4]);
            return String.join(" ", parts);
        }
        if (parts.length == 4) {
            // 4 fields: hour dom month dow -> pad with minute=0
            parts[4] = dowNumbersToNames(parts[3]);
            parts[3] = parts[2];
            parts[2] = parts[1];
            parts[1] = parts[0];
            parts[0] = "0";
            return String.join(" ", parts);
        }
        if (parts.length == 3) {
            // 3 fields: dom month dow -> pad with minute=0 hour=0
            String dow = dowNumbersToNames(parts[2]);
            return "0 0 " + parts[0] + " " + parts[1] + " " + dow;
        }
        // Return default value if it does not meet requirements
        LOGGER.warning("Unable to parse cron expression, using default value: " + cronExpr);
        return "0 9 * * *";
    }

    private static String dowNumbersToNames(String field) {
        if ("*".equals(field)) return field;
        String[] tokens = field.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(convertDowToken(tokens[i].trim()));
        }
        return sb.toString();
    }

    private static String convertDowToken(String token) {
        if (token == null || token.isBlank()) return "*";
        String[] parts;
        if (token.contains("/")) {
            parts = token.split("/", 2);
            return convertDowToken(parts[0]) + "/" + parts[1];
        }
        if (token.contains("-")) {
            parts = token.split("-", 2);
            return numToDowName(parts[0]) + "-" + numToDowName(parts[1]);
        }
        return numToDowName(token);
    }

    private static String numToDowName(String token) {
        return switch (token) {
            case "0", "7" -> "sun";
            case "1" -> "mon";
            case "2" -> "tue";
            case "3" -> "wed";
            case "4" -> "thu";
            case "5" -> "fri";
            case "6" -> "sat";
            default -> token;
        };
    }

    /**
     * Get a human-readable description of the cron expression.
     */
    public static String describeSchedule(ScheduleSpec schedule) {
        if (schedule == null) return "-";
        if ("once".equals(schedule.type())) {
            String s = "One-time: " + (schedule.runAt() != null ? schedule.runAt() : "?");
            if (schedule.repeatEveryDays() != null && schedule.repeatEveryDays() > 0) {
                s += " (Repeats every " + schedule.repeatEveryDays() + " days";
                String endType = schedule.repeatEndType();
                if ("until".equals(endType)) {
                    s +=
                            " until "
                                    + (schedule.repeatUntil() != null
                                            ? schedule.repeatUntil()
                                            : "?");
                } else if ("count".equals(endType)) {
                    s +=
                            " "
                                    + (schedule.repeatCount() != null
                                            ? schedule.repeatCount()
                                            : "?")
                                    + " times";
                }
                s += ")";
            }
            return s;
        }
        // cron type
        return "Periodic: " + (schedule.cron() != null ? schedule.cron() : "?");
    }

    /**
     * Create a new default JSON format request input example for CronJobSpec.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> defaultRequestInput() {
        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("text", "Hello");
        contentItem.put("type", "text");
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", List.of(contentItem));
        return List.of(msg);
    }
}
