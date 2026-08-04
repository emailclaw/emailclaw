<p align="center">
  <img src="emailclaw/src/main/resources/ai/emailclaw/emailclaw/images/logo.jpg" alt="Emailclaw" width="280">
</p>

<h1 align="center">Emailclaw</h1>

<p align="center">
  <strong>Email in. Work out.</strong><br>
  A local-first AI Agent that uses email as its default interactive entry point and subjects as project boundaries.
</p>

<p align="center">
  <a href="./README-zh.md">中文版</a> ·
  <a href="docs/Emailclaw-User-Manual.md">Installation & Usage</a>
</p>

## Why Emailclaw?

Emailclaw puts AI Agents into an inbox everyone is familiar with. It doesn't treat email as just another chat channel, but rather treats the email subject as a clear boundary for a specific task:

> **New Subject, New Project.**

A new subject email without a valid TaskId will create an independent task session, a project record, and a project directory. The system replies with an email attaching a TaskId; once the user replies directly to this email, subsequent messages, attachments, and Agent work will stay within the same task. To start a different piece of work, simply write a new subject.

This makes Emailclaw especially suitable for email lovers and those who need to maintain clear contextual boundaries between research, writing, code review, reporting, customer replies, and automated tasks.

## Core Features

- **Email-first**: Uses IMAP to receive tasks and SMTP to return results. No need to learn or migrate to new instant chat apps.
- **Subject-level Project Isolation**: New Subject, New Project; TaskId binds email threads, session history, and project directories.
- **Local-first**: Configurations, projects, sessions, attachments, workspaces, logs, and plugins are saved on machines you control.
- **Controlled Agent Actions**: Models, tools, Skills, MCP, cron jobs, and plugins can be composed; high-risk tool calls can be approved via a 4-digit confirmation code in email.
- **Suitable for Long-running**: Supports Linux headless service mode, boot startup, Cron automation, and log troubleshooting.
- **Extensible**: Based on Java SPI and external JAR plugins; extensible for tools, Providers, hooks, MCP, UI, and channels.
- **Powered by AgentScope Java**: Utilizes AgentScope Java 2.0's Agent, model, event, tool, permission, and Harness capabilities.

## How It Works

```text
Your Inbox
  │  Send a new subject email
  ▼
Emailclaw Channel
  │  Creates session + project, replies with TaskId
  ▼
Reply to the same email (keeping TaskId)
  │
  ▼
Agent reasons in an isolated workspace, calls controlled tools, generates results
  │
  ▼
Results, attachments, or approval requests return to the original email thread
```

The first new subject email is used to establish a stable task boundary; it will not immediately trigger a model call. Please reply to Emailclaw's response email and keep the complete TaskId at the end of the subject.

## Quick Start

### 1. Install Release Package

The release process builds desktop installation packages for the following platforms. Please visit <https://github.com/emailclaw/emailclaw/releases/> to download the latest version installation package:

| Platform | Asset Type | Installation Method |
| --- | --- | --- |
| Windows | `.exe` | Double-click to install after downloading, and follow the setup wizard |
| macOS | `.dmg` | Open the disk image and drag the application to the "Applications" folder |
| Debian/Ubuntu Linux | `.deb` | Use the system software center, or execute `wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.deb && sudo apt install ./emailclaw-linux-latest.deb` in the terminal |
| Linux Portable Deployment | `.tar.gz` | Extract and run the program from the application directory; passes --service parameter to activate Service mode |

### 2. Configure Model and Agent

After launching the desktop application:

1. In **Providers**, add the Base URL, API Key, and model ID of the model service.
2. In **Agents**, create or select a default Agent, and choose the model and system prompt.
3. It is recommended to send a simple message in local chat first to verify the model credentials and network availability.

### 3. Configure Emailclaw Channel


Click **Emailclaw Settings** to open the configuration page. You can use your own email, or follow the interface instructions to register if the system-provided email function is available and you are authorized.

### 3.1 Using Your Own Email

Fill in the following items:

- **Email Address**: The email address used by Emailclaw to send and receive tasks.
- **Email Password**: App-specific password, authorization code, or the credentials required by the email service.
- **IMAP Host / Port / SSL / STARTTLS**: Incoming mail server parameters.
- **SMTP Host / Port / SSL / STARTTLS**: Outgoing mail server parameters.
- **Poll Interval**: Inbox polling interval, minimum 5 seconds, default 30 seconds.
- **Allowed Senders**: Email addresses allowed to send tasks to the Agent, one per line.

Common addresses like Gmail, Outlook, iCloud, 163, 126, QQ, Foxmail, Yahoo, etc., will automatically match preset parameters. If your service provider is not among the presets, please query your service provider for IMAP/SMTP parameters and fill them in manually.

After saving the configuration, enable Emailclaw on the channels page. The system will start polling the mailbox in the background. It is strongly recommended to only add addresses you trust to the allowed senders list.

### 3.2 Using the System-Provided Emailclaw Channel Service

The prerequisite is that you are an invited user, you have sent an email to otp@emailclaw.email to obtain a one-time password, and you fully agree to the Emailclaw Channel Service Agreement:
1. The Service Provider reserves all rights, and may suspend or change the terms of this agreement at any time without prior notice.
2. The Service Provider provides no quality guarantee for the services provided. The Service Provider shall not be held liable for any direct or indirect damages arising from the use of this service.
3. The Emailclaw Channel only provides email channel services, and does not provide email storage services. All sent or received emails will be completely and irrecoverably deleted from the server after 15 minutes.
4. If a user registers for the Emailclaw Channel service provided by the system but does not use the service for more than 100 consecutive days, the Service Provider will delete the user's service.
5. By using this service, you agree to comply with all applicable local and international laws and regulations.

Just fill in the following items:

- **Registration Email**: The email address used to register for the service.
- **One-time Password**: The obtained one-time password credential.

After saving the configuration, enable Emailclaw on the channels page. The system will start polling the mailbox in the background. The allowed sender is simply your own email.


### 4. Initiate the First Task

Send a new subject email to the Agent's email address, for example:

```text
Subject: Organize competitor abstract for August product review

Please read the three materials in the attachment and output a one-page abstract in Chinese:
1. Three common trends;
2. Differences of each product;
3. Two suggestions for the product team.
```

Emailclaw will reply and append a TaskId at the end of the subject. Reply directly to this email to start execution; always reply to this thread when continuing the same task. When you need to start a different job, write a new subject email.

## Security Model

Emailclaw's goal is not to give the Agent unrestricted machine access.

- The Agent works within project and workspace boundaries.
- `emailAllowlistSenders` restricts which email addresses can initiate tasks.
- High-risk tool calls can enter a manual approval process via email confirmation codes; timeouts, wrong codes, or replies across sessions will be rejected.
- Permission modes cover `explore` (read-only), `default` (routine confirmation), `accept_edits`, `dont_ask`, and `bypass`. In production environments, please start with `explore` or `default`.
- Third-party plugins, MCP services, and Skills may have file, network, or code execution capabilities. They should only be installed from trusted sources and verified in a low-privilege environment first.

## Linux Background Service

The Linux `.tar.gz` package can be extracted and run directly in your home directory without root privileges. A user-level systemd service (`~/.config/systemd/user/emailclaw.service`) runs the daemon under your own user, sharing the **same `~/emailclaw` data directory** as the desktop app. No config migration is needed.

The `.deb` package installs to `/opt/emailclaw/` (requires `sudo apt install`); the installer creates a symlink at `~/.local/bin/emailclaw` and registers the same user-level service for you.

After installation, enable the background service (no sudo required):

```sh
loginctl enable-linger "$USER"   # keep the daemon running after logout
systemctl --user enable --now emailclaw
systemctl --user status emailclaw
journalctl --user -u emailclaw -f
```

Each Linux user can enable their own independent instance; every instance keeps its own `~/emailclaw` data and configuration. For a complete step-by-step tutorial, see the [User Manual](docs/Emailclaw-User-Manual.md).

## Build from Source

Prerequisites: JDK 25, Maven, and the `jpackage` capabilities of the target operating system.

```sh
cd emailclaw
mvn -B package -DskipTests
```

The build will generate a shaded JAR and platform installation packages. GitHub Actions will build `.exe`, `.dmg`, and `.deb` on Windows, macOS, and Linux respectively when pushing `v*` tags, and additionally generate a Linux `.tar.gz`.

Run tests:

```sh
cd emailclaw
mvn -B test
```

## Directory Structure

```text
emailclaw/                 Java application source code and Maven build
plugin-samples/            Plugin development guide & DingTalk channel sample
docs/                      Product features, architecture, and user manual
.github/workflows/         GitHub Release build workflows
```

The default runtime data root directory is `~/emailclaw`, which can be modified via the Java system property `emailclaw.home` or the environment variable `EMAILCLAW_HOME`. Common subdirectories include:

| Path | Purpose |
| --- | --- |
| `.config/` | JSON configurations for Providers, Agents, channels, projects, Cron, etc. |
| `.chat-history/` | Session and message history |
| `projects/` | Project directories created per task |
| `agent-workspace/` | Agent workspaces |
| `skill-pool/` | Skills |
| `plugins/` | External plugins |
| `logs/` | Application logs |

## Extensions and Plugins

Plugins can provide tools, model Providers, startup/shutdown hooks, MCP, UI, or channel capabilities. Please start with the [Plugin Developer Guide](plugin-samples/PLUGIN-DEVELOPER-GUIDE.md) and [`plugin-samples/dingtalk`](plugin-samples/dingtalk). Channel plugins are responsible for external session mapping, message forwarding, and (if needed) the secure delivery of approval requests.

## Documentation

- [Installation & User Manual](docs/Emailclaw-User-Manual.md)
- [AgentScope Java 2.0 Docs](https://java.agentscope.io/v2/en/docs/index.html)

## Contributing

We welcome improvements to Emailclaw through clear Issues and reviewable Pull Requests. Before submitting:

1. Do not commit API Keys, email passwords, app authorization codes, or personal data.
2. Add or update tests and documentation for behavioral changes.
3. Run relevant Maven checks using JDK 25.
4. Maintain plugin compatibility: external plugins should generally depend on the Emailclaw core API using `provided` scope.

## License

Emailclaw is licensed under the [MIT License](LICENSE).
