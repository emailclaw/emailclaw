<p align="center">
  <img src="emailclaw/src/main/resources/ai/emailclaw/emailclaw/images/logo.jpg" alt="Emailclaw" width="280">
</p>

<h1 align="center">Emailclaw</h1>

<p align="center">
  <strong>Email in. Work out.</strong><br>
  以电子邮件为默认交互入口、以主题为项目边界的本地优先 AI Agent。
</p>

<p align="center">
  <a href="docs/Emailclaw-User-Manual-zh.md">安装与使用</a> ·
</p>

## 为什么是 Emailclaw？

Emailclaw 把 AI Agent 放进每个人都熟悉的邮箱。它不是把电子邮件当成又一个聊天渠道，而是把邮件主题视为一项工作的明确边界：

> **New Subject, New Project.**

一封没有有效 TaskId 的新主题邮件会创建独立任务会话、项目记录和项目目录。系统回信附上 TaskId；用户直接回复这封邮件后，后续消息、附件与 Agent 工作都留在同一任务中。要开始另一件事，只需写一个新主题。

这让 Emailclaw 特别适合 email lovers，以及需要在研究、写作、代码审阅、报表、客户回复和自动化工作之间保持清晰上下文边界的人。

## 核心特性

- **Email-first**：使用 IMAP 收取任务、SMTP 回传结果。无需学习或迁移到新的即时聊天应用。
- **主题级项目隔离**：New Subject, New Project；TaskId 绑定邮件线程、会话历史和项目目录。
- **本地优先**：配置、项目、会话、附件、工作区、日志与插件保存在你控制的机器上。
- **受控的 Agent 行动**：模型、工具、Skills、MCP、定时任务和插件可组合使用；高风险工具调用可通过邮件四位确认码审批。
- **适合长期运行**：支持 Linux 无界面服务模式、开机启动、Cron 自动化和日志排障。
- **可扩展**：基于 Java SPI 与外部 JAR 插件；可扩展工具、Provider、钩子、MCP、界面和渠道。
- **基于 AgentScope Java**：使用 AgentScope Java 2.0 的 Agent、模型、事件、工具、权限与 Harness 能力。

## 它如何工作

```text
你的邮箱
  │  发送一封新主题邮件
  ▼
Emailclaw Channel
  │  创建会话 + 项目，回信附 TaskId
  ▼
回复同一封邮件（保留 TaskId）
  │
  ▼
Agent 在隔离工作区中推理、调用受控工具、生成结果
  │
  ▼
结果、附件或审批请求回到原邮件线程
```

首封新主题邮件用于建立稳定的任务边界；它不会立刻执行模型调用。请回复 Emailclaw 回信，并保留主题末尾完整的 TaskId。

## 快速开始

### 1. 安装发布包

发布流程会为以下平台构建桌面安装包，请到 <https://github.com/emailclaw/emailclaw/releases/> 下载最新版本安装包：

| 平台 | 资产类型 | 安装方式 |
| --- | --- | --- |
| Windows | `.exe` | 下载后双击安装，按安装向导完成操作 |
| macOS | `.dmg` | 打开磁盘映像，将应用拖入“应用程序”目录 |
| Debian/Ubuntu Linux | `.deb` | 使用系统软件中心，或在终端执行 `wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.deb && sudo apt install ./emailclaw-linux-latest.deb` |
| Linux 便携部署 | `.tar.gz` | 解压后从应用目录运行程序；传入 --service 参数则激活服务模式 |

### 2. 配置模型与 Agent

启动桌面应用后：

1. 在 **Providers** 添加模型服务的 Base URL、API Key 和模型 ID。
2. 在 **Agents** 创建或选择一个默认 Agent，并选择模型与系统提示词。
3. 建议先在本地聊天中发一条简单消息，确认模型凭据和网络可用。

### 3. 配置 Emailclaw Channel


点击 ****Emailclaw Settings**，打开配置页面。你可以使用自己的邮箱，或在系统提供邮箱功能可用且你已获授权时按界面指引注册。

### 3.1 使用自己的邮箱

填写以下项目：

- **Email Address**：Emailclaw 用来收发任务的邮箱地址。
- **Email Password**：应用专用密码、授权码或该邮箱服务要求的凭据。
- **IMAP Host / Port / SSL / STARTTLS**：收信服务器参数。
- **SMTP Host / Port / SSL / STARTTLS**：发信服务器参数。
- **Poll Interval**：轮询收件箱间隔，最小 5 秒，默认 30 秒。
- **Allowed Senders**：允许向 Agent 发任务的邮箱地址，每行一个。

Gmail、Outlook、iCloud、163、126、QQ、Foxmail、Yahoo 等常见地址会自动匹配预设参数。若你的服务商不在预设内，请向服务商查询 IMAP/SMTP 参数后手工填写。

保存配置后，在渠道页面启用 Emailclaw。系统会开始在后台轮询邮箱。强烈建议只添加你信任的地址到允许发件人列表。

### 3.2 使用使用系统提供的Emailclaw Channel Service

前提是你是被邀请用户，你已发送邮件到 otp@emailclaw.email 后获取了一次性密码，且完全同意Emailclaw Channel Service Agreement
1. The Service Provider reserves all rights, and may suspend or change the terms of this agreement at any time without prior notice.
2. The Service Provider provides no quality guarantee for the services provided. The Service Provider shall not be held liable for any direct or indirect damages arising from the use of this service.
3. The Emailclaw Channel only provides email channel services, and does not provide email storage services. All sent or received emails will be completely and irrecoverably deleted from the server after 15 minutes.
4. If a user registers for the Emailclaw Channel service provided by the system but does not use the service for more than 100 consecutive days, the Service Provider will delete the user's service.
5. By using this service, you agree to comply with all applicable local and international laws and regulations.

只需填写以下项目：

- **Registration Email**：用来注册服务的邮箱地址。
- **One-time Password**：获取到的一次性密码凭据。

保存配置后，在渠道页面启用 Emailclaw。系统会开始在后台轮询邮箱。允许发件人就是你自己的邮箱。


### 4. 发起第一项任务

向 Agent 邮箱发送一封新主题邮件，例如：

```text
Subject: 为 8 月产品评审整理竞品摘要

请阅读附件中的三份资料，输出一页中文摘要：
1. 三个共同趋势；
2. 每家产品的差异；
3. 给产品团队的两个建议。
```

Emailclaw 会回信并在主题末尾附上 TaskId。直接回复该邮件开始执行；继续同一个任务时始终回复这个线程。需要开始另一件工作时，写一封新的主题邮件。

## 安全模型

Emailclaw 的目标不是让 Agent 获得不受限制的机器权限。

- Agent 工作在项目和工作区边界内。
- `emailAllowlistSenders` 限制可发起任务的邮箱。
- 高风险工具调用可通过邮件确认码进入人工审批流程；超时、错误码或跨会话回复会被拒绝。
- 权限模式覆盖 `explore`（只读）、`default`（常规确认）、`accept_edits`、`dont_ask` 与 `bypass`。生产环境请从 `explore` 或 `default` 开始。
- 第三方插件、MCP 服务和 Skills 可能拥有文件、网络或代码执行能力，只应从可信来源安装并先在低权限环境验证。

## Linux 后台服务

Linux `.tar.gz` 包可直接解压到主目录下运行，无需 root 权限。用户级 systemd 服务（`~/.config/systemd/user/emailclaw.service`）以你自己的用户身份运行，与桌面应用共用**同一个 `~/emailclaw` 数据目录**，无需迁移配置。

`.deb` 包安装到 `/opt/emailclaw/`（需 `sudo apt install`）；安装器会在 `~/.local/bin/` 创建符号链接并为你注册同样的用户级服务。

安装完成后，启用后台服务（无需 sudo）：

```sh
loginctl enable-linger "$USER"   # 让守护进程在退出登录后继续运行
systemctl --user enable --now emailclaw
systemctl --user status emailclaw
journalctl --user -u emailclaw -f
```

每个 Linux 用户可启用独立实例；各实例的数据与配置分别保存在各自的 `~/emailclaw`。完整教程见 [用户手册](docs/Emailclaw-User-Manual-zh.md)。

## 从源码构建

前提：JDK 25、Maven，以及目标操作系统的 `jpackage` 能力。

```sh
cd emailclaw
mvn -B package -DskipTests
```

构建会生成 shaded JAR 和平台安装包。GitHub Actions 在推送 `v*` 标签时分别在 Windows、macOS、Linux 上构建 `.exe`、`.dmg`、`.deb`，并额外生成 Linux `.tar.gz`。

运行测试：

```sh
cd emailclaw
mvn -B test
```

## 目录说明

```text
emailclaw/                 Java 应用源码和 Maven 构建
plugin-samples/            插件开发指南与 DingTalk 渠道示例
docs/                      产品功能、架构和用户手册
.github/workflows/         GitHub Release 构建工作流
```

默认运行数据根目录是 `~/emailclaw`，可通过 Java 系统属性 `emailclaw.home` 或环境变量 `EMAILCLAW_HOME` 修改。常用子目录包括：

| 路径 | 用途 |
| --- | --- |
| `.config/` | Provider、Agent、渠道、项目、Cron 等 JSON 配置 |
| `.chat-history/` | 会话与消息历史 |
| `projects/` | 按任务创建的项目目录 |
| `agent-workspace/` | Agent 工作区 |
| `skill-pool/` | Skills |
| `plugins/` | 外部插件 |
| `logs/` | 应用日志 |

## 扩展与插件

插件可以提供工具、模型 Provider、启动/关闭钩子、MCP、界面或渠道能力。请从 [插件开发指南](plugin-samples/PLUGIN-DEVELOPER-GUIDE-zh.md) 与 [`plugin-samples/dingtalk`](plugin-samples/dingtalk) 开始。渠道插件需要负责外部会话映射、消息转发和（如需要）审批请求的安全投递。

## 文档

- [用户安装使用说明](docs/Emailclaw-User-Manual-zh.md)
- [AgentScope Java 2.0 文档](https://java.agentscope.io/v2/en/docs/index.html)

## 贡献

欢迎通过清晰的 Issue 和可审阅的 Pull Request 改进 Emailclaw。提交前请：

1. 不要提交 API Key、邮箱密码、应用授权码或个人数据。
2. 为行为变化补充或更新测试与文档。
3. 使用 JDK 25 运行相关 Maven 检查。
4. 保持插件兼容性：外部插件通常应以 `provided` 方式依赖 Emailclaw 核心 API。

## 许可证

Emailclaw 采用 [MIT License](LICENSE)。
