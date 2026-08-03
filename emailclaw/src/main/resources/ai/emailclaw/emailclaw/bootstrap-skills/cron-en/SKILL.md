---
name: cron
description: Use this skill only for scheduled or recurring tasks. Cron jobs are managed via the UI or by editing cron-jobs.json directly; CLI commands are not supported.
metadata:
  builtin_skill_version: "1.4"
  emailclaw:
    emoji: "⏰"
---

# Cron (Scheduled Task Management)

## When to Use

Use this skill only when you need to **automatically execute something at a future time** or **repeat execution on a schedule**.

### Should Use
- User asks to do something "daily / weekly / hourly"
- User asks for automatic reminders or execution "tomorrow at 9 AM / next Monday / at a specific time"
- Long-term periodic notifications, checks, or reports are needed

### Should Not Use
- The task only needs to be **executed once right now**
- It is just a normal reply within the current session
- The user has not specified an execution time or schedule
- The target channel / session is still unclear

## Decision Rules

1. **Only use cron for future scheduled or periodic execution**
2. **If it only needs to be done once immediately, do not create a cron job**
3. **Before creating, confirm execution time/schedule, target channel, and target session**
4. **Inform the user: cron jobs can only be managed through the UI (CronJobs view) or by editing the configuration file directly**

---

## Capabilities (UI-based)

### Supported (manual via CronJobs view)
- **Create**: Fill in the form to create a scheduled task
- **Edit**: Double-click a task to modify its parameters
- **Enable/Disable**: Toggle the switch in the task list
- **Run Now**: Click the Run button to trigger execution immediately
- **Delete**: Confirm to delete a task and its history
- **View History**: Review past execution records

### Not Supported
`emailclaw cron` CLI commands are **not available**. Do not attempt to use command-line operations for cron job management.
If the user expects CLI commands, explain that only the UI-based approach is supported in the current version.

---

## Configuration File

Cron jobs are stored in `cron-jobs.json` with the following format:

```json
{
  "version": 1,
  "jobs": [
    {
      "id": "uuid-v7",
      "projectId": "default",
      "name": "Task Name",
      "enabled": true,
      "schedule": {
        "type": "cron",
        "cron": "0 9 * * *",
        "timezone": "Asia/Shanghai"
      },
      "taskId": "",
      "inputPrompt": "Message to send",
      "dispatch": {
        "type": "channel",
        "channel": "console",
        "target": {
          "userId": "",
          "sessionId": "target session ID"
        },
        "mode": "final"
      },
      "saveResultToInbox": true,
      "runtime": {
        "maxConcurrency": 1,
        "timeoutSeconds": 120,
        "misfireGraceSeconds": 60,
        "shareSession": true
      }
    }
  ]
}
```

### Editing Steps (Hot Reload Supported)

1. Edit `~/emailclaw/.config/cron-jobs.json` directly
2. Add, modify, or remove entries in the `jobs` array as needed
3. **No restart required** — changes take effect immediately via hot reload

---

## Cron Expression Examples

```
0 9 * * *      Every day at 9:00
0 */2 * * *    Every 2 hours
30 8 * * 1-5   Weekdays at 8:30
0 0 * * 0      Every Sunday at midnight
*/15 * * * *   Every 15 minutes
```

---

## Common Mistakes

### Mistake 1: Creating a cron job for immediate one-time execution

If the task only needs to be done once right now, do not create a cron job.

### Mistake 2: Attempting to use CLI commands for cron management

The `emailclaw cron` commands are **not supported** in the current version. Use the CronJobs UI view or edit the configuration file directly.

### Mistake 3: Creating a task without complete information

If the user has not specified the time, schedule, target channel, or target session, ask for clarification first.
