---
name: cron
description: 仅在需要未来定时执行或周期执行任务时，使用本 skill。定时任务通过 UI 或直接编辑 cron-jobs.json 管理，当前不支持命令行操作。
metadata:
  builtin_skill_version: "1.4"
  emailclaw:
    emoji: "⏰"
---

# 定时任务管理

## 什么时候用

只有在需要**未来某个时间自动执行**，或**按周期重复执行**时，使用本 skill。

### 应该使用
- 用户要求"每天 / 每周 / 每小时"执行某事
- 用户要求"明天 9 点 / 下周一 / 某个时间"自动提醒或执行
- 需要长期周期性通知、检查、汇报

### 不应使用
- 只是要**现在立即执行一次**
- 只是当前会话中的正常回复
- 用户没有明确执行时间或周期
- 目标 channel / session 还不明确

## 决策规则

1. **只有在未来定时执行或周期执行时才使用 cron**
2. **如果只是立即做一次，通常不要创建 cron**
3. **创建前必须确认执行时间/周期、目标 channel、目标 session**
4. **告知用户：当前仅支持通过 UI（CronJobs 视图）或直接编辑配置文件来管理定时任务**

---

## 系统功能说明

### 当前支持的能力（通过 UI 手动操作）

- **创建**：在 CronJobs 视图中填写表单创建定时任务
- **编辑**：双击已有任务修改参数
- **启用/禁用**：在列表中切换开关
- **立即执行**：点击 Run 按钮手动触发一次
- **删除**：确认后删除任务及历史记录
- **查看历史**：查看每次执行的记录

### 当前不支持的命令行操作

`emailclaw cron` 命令**不可用**。不要尝试使用命令行创建或管理定时任务。
如果用户期望命令行操作，说明当前版本只支持 UI 方式。

---

## 配置方式

定时任务存储在 `cron-jobs.json` 文件中，格式如下：

```json
{
  "version": 1,
  "jobs": [
    {
      "id": "uuid-v7",
      "projectId": "default",
      "name": "任务名称",
      "enabled": true,
      "schedule": {
        "type": "cron",
        "cron": "0 9 * * *",
        "timezone": "Asia/Shanghai"
      },
      "taskId": "",
      "inputPrompt": "要发送的消息",
      "dispatch": {
        "type": "channel",
        "channel": "console",
        "target": {
          "userId": "",
          "sessionId": "目标会话 ID"
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

### 修改步骤（支持热加载）

1. 直接编辑 `~/emailclaw/.config/cron-jobs.json`
2. 按需添加、修改或删除 `jobs` 数组中的任务
3. **无需重启** — 修改保存后立即生效（热加载）

---

## Cron 表达式示例

```
0 9 * * *      每天 9:00
0 */2 * * *    每 2 小时
30 8 * * 1-5   工作日 8:30
0 0 * * 0      每周日零点
*/15 * * * *   每 15 分钟
```

---

## 常见错误

### 错误 1：把一次性立即执行当成 cron

如果只是现在执行一次，通常不要创建 cron。

### 错误 2：尝试使用命令行管理 cron

当前版本不支持 `emailclaw cron` 命令。请使用 UI 的 CronJobs 视图或直接编辑配置文件。

### 错误 3：信息没补全就创建

如果用户没说明时间、周期、目标 channel 或目标 session，应先追问。
