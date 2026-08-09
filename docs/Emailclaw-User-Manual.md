# Emailclaw User Manual
<a href="./Emailclaw-User-Manual-zh.md">中文版</a>
## 1. Before You Start

Emailclaw allows you to use AI Agents via email. It is recommended to use a dedicated email account and enable IMAP, SMTP, and an app-specific password/authorization code with that email service provider. Please do not put your personal primary email password, model API keys, or other secrets into the email body or forward them to others.

For the first time use, you need to complete four things: install the application, configure the model Provider, create or confirm the default Agent, and configure and enable the Emailclaw Channel.

## 2. Installation

### 2.1 Using Release Packages (Recommended)

The release process builds desktop installation packages for the following platforms. Please visit <https://github.com/emailclaw/emailclaw/releases/> to download the latest version installation package:

| Platform | Asset Type | Installation Method |
| --- | --- | --- |
| Windows | `.exe` | Double-click to install after downloading, and follow the setup wizard |
| macOS | `.dmg` | Open the disk image and drag the application to the "Applications" folder |
| Linux Portable Deployment | `.tar.gz` | Execute  `mkdir -p ~/.local/ && cd ~/.local/ && wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.tar.gz && tar -xzf emailclaw-linux-latest.tar.gz -C ~/.local/ --no-same-owner`. Launch **Emailclaw** by running `~/.local/emailclaw/bin/emailclaw` |
| Debian/Ubuntu Linux | `.deb` | Execute `wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.deb && sudo apt install ./emailclaw-linux-latest.deb`. Launch **Emailclaw** from the application menu or by running `/opt/emailclaw/bin/emailclaw` |

### 2.2 Building from Source

Use this section only if you need to compile it yourself. Building requires JDK 25, Maven, and the packaging capabilities of the corresponding platform.

```sh
mvn -B package -DskipTests
```

The build configuration will generate a shaded JAR and use `jpackage` to generate platform installation packages. The default profile for the Linux project will additionally generate an app-image and a `.tar.gz`. GitHub Actions will run the corresponding profiles on Windows, macOS, and Linux respectively when tagging; for local builds, please use an environment consistent with your operating system.

## 3. First Launch and Model Configuration

1. Launch the Emailclaw desktop application.
2. Open **Providers**, and create a new Provider.
3. Fill in the Provider name, Base URL, API Key, and add the model IDs allowed by this service provider.
4. Open **Agents**, create or edit an Agent, select the main model, and write the system prompt and responsibilities.
5. Set this Agent as the default Agent; email tasks will be received by the default Agent.

If a fallback Provider/model is configured, the system can retry or fall back according to the Agent settings when the primary model call fails. Please do a simple test using the desktop chat first to confirm that the model credentials and network connection are correct.

## 4. Configuring the Emailclaw Channel

Find **Emailclaw** in **Channels** and open the configuration page. You can use your own email, or follow the interface instructions to register if the system-provided email function is available and you are authorized.

### 4.1 Using Your Own Email

Fill in the following items:

- **Email Address**: The email address used by Emailclaw to send and receive tasks.
- **Email Password**: App-specific password, authorization code, or the credentials required by the email service.
- **IMAP Host / Port / SSL / STARTTLS**: Incoming mail server parameters.
- **SMTP Host / Port / SSL / STARTTLS**: Outgoing mail server parameters.
- **Poll Interval**: Inbox polling interval, minimum 5 seconds, default 30 seconds.
- **Allowed Senders**: Email addresses allowed to send tasks to the Agent, one per line.

Common addresses like Gmail, Outlook, iCloud, 163, 126, QQ, Foxmail, Yahoo, etc., will automatically match preset parameters. If your service provider is not among the presets, please query your service provider for IMAP/SMTP parameters and fill them in manually.

After saving the configuration, enable Emailclaw on the channels page. The system will start polling the mailbox in the background. It is strongly recommended to only add addresses you trust to the allowed senders list.

### 4.2 Using the System-Provided Emailclaw Channel Service

The prerequisite is that you have sent an email to otp@emailclaw.email to obtain a one-time password, and you fully agree to the Emailclaw Channel Service Agreement:
1. The Service Provider reserves all rights, and may suspend or change the terms of this agreement at any time without prior notice.
2. The Service Provider provides no quality guarantee for the services provided. The Service Provider shall not be held liable for any direct or indirect damages arising from the use of this service.
3. The Emailclaw Channel only provides email channel services, and does not provide email storage services. All sent or received emails will be completely and irrecoverably deleted from the server after 15 minutes.
4. If a user registers for the Emailclaw Channel service provided by the system but does not use the service for more than 100 consecutive days, the Service Provider will delete the user's service.
5. By using this service, you agree to comply with all applicable local and international laws and regulations.

Just fill in the following items:

- **Registration Email**: The email address used to register for the service.
- **One-time Password**: The obtained one-time password credential.

After saving the configuration, enable Emailclaw on the channels page. The system will start polling the mailbox in the background. The allowed sender is simply your own email.

### 4.3 Configuration File Location (For Service Mode or Troubleshooting)

The default application home directory is:

- Linux/macOS: `~/emailclaw`
- Windows: `%USERPROFILE%\emailclaw`

It can be changed via the Java system property `emailclaw.home` or the environment variable `EMAILCLAW_HOME`. Channel configurations are located in `.config/channels.json`; Providers, Agents, projects, and cron jobs are also saved in the same `.config/` directory. It is preferred to use the graphical interface to modify configurations. Before manually editing, please back up and stop concurrent modifications.

### 4.4 Manually Modifying the Configuration File channels.json

If you use your own email, please refer to the section above to fill in the relevant fields; if you want to use the system-provided Emailclaw Channel Service, provided you are an invited user, you have sent an email to otp@emailclaw.email to obtain a one-time password, and fully agree to the Emailclaw Channel Service Agreement, you only need to fill in three fields: `"sysEmailMode": true`, `"registrantEmail": "<YOUR EMAIL>"`, `"oneTimePassword": "<YOUR OTP>"`.

## 5. The Most Important Usage: New Subject Means New Project

### 5.1 Initiating a Task

1. Send an email with a **brand new subject** from an allowed sender email to the email configured for Emailclaw.
2. State the goal, background, deliverables, and constraints in the email body; you may attach a single attachment up to 10 MB when materials are needed.
3. Upon receiving it, the system will create an independent task session and project, and reply with an email containing a TaskId at the end of the subject.
4. Reply directly to this email, **keeping the TaskId at the end of the subject**, and supplement with "start execution" or further instructions.
5. The Agent works in the same thread and emails the results back; please continue to reply in this thread for follow-up inquiries.

Example:

```text
To: my-agent@example.com
Subject: Organize competitor abstract for August product review

Please read the three materials in the attachment and output a one-page abstract in Chinese:
1. Three common trends;
2. Differences of each product;
3. Two suggestions for the product team.
```

After receiving the TaskId, please reply to the system email instead of re-entering the same subject. Sending a new subject without a TaskId will create another project, which is exactly how different tasks are isolated.

### 5.2 Continuing, Switching, and Ending Tasks

- **Continue the same task**: Reply to the email with the original TaskId, writing only the new requirements in the body.
- **Start another task**: Create a new email and write a new subject. It gets a new session, project directory, and context.
- **Forward an email**: Forwarded content is usually identified as a blockquote; please clearly write the action you want the Agent to complete this time at the top of the email.
- **Attachment delivery**: When the Agent generates files to send, the result email can include attachments. Keeping the project makes it easier to view files and history later in the local interface.

## 6. Email Approval: Let the Agent Ask Before Doing

When an Agent is about to execute a sensitive tool call, Emailclaw can send an approval email. The email will specify the tool, parameter summary, risk description, and a four-digit confirmation code.

- Only replying with the corresponding four-digit code in the email indicates consent to this operation.
- No reply, replying with wrong content, replying in other threads, or waiting for a timeout will all be considered a rejection.
- The approval code itself will not be sent to the model as a regular question, so it will not pollute the task context.

For initial deployment, it is recommended to set the Agent's permission mode to `default` or `explore`. Only after fully understanding the tool behavior should you consider `accept_edits` or higher permissions; do not use `bypass` for untrusted email inputs.

## 7. Projects, Files, and Automation

### Projects and Files

Each new subject task has a corresponding project. You can view the project directory, session logs, and attachments in **Projects** and **Files**. Please place the files that need to be modified by the Agent in the authorized project/workspace; if the files are not within the allowed directories, the system may deny access, which is part of the security boundary.

### Cron Jobs

Create scheduled tasks in **Cron Jobs / Automations**, select the project, Agent, task prompt, and Cron expression. You can set the maximum concurrency and the execution count countdown to prevent slow tasks from piling up. Common 6-field expression examples:

```text
0 0 9 * * ?       # Every day at 09:00
0 0/30 * * * ?    # Every 30 minutes
0 0 17 ? * FRI    # Every Friday at 17:00
```
For users unfamiliar with Cron expressions, a fully graphical interface is provided for easy operation. In addition, the system provides a skill named Cron (by default all Agents have this skill), and you can also tell the Agent to configure Cron for the current Task in the Task conversation (note: not Chat conversation).

Cron jobs usually read the latest configuration and reschedule; after modifications, you should still check the task list and execution history to confirm they actually took effect.

## 8. Skills, MCP, and Plugins

Enable suitable operational instructions for tasks in **Skills**, add vetted tool services in **MCP**, and manage extensions in **Plugins**. Plugins can add tools, models, hooks, interfaces, or extra channels, but the default product workflow remains centered around the Emailclaw Channel.

Third-party plugins have the potential for code execution and network access. Install only from trusted sources, read their permissions and configuration instructions, and verify them first on a test account or a low-privilege Agent. Developers can refer to the `plugin-samples/PLUGIN_DEVELOPER_GUIDE.md` and `plugin-samples/dingtalk` examples in the repository.

## 9. Linux Background Service: A Complete Installation Guide for PC Beginners

This section applies to Ubuntu, Debian, and other Debian-based Linux distributions using **systemd**. The background service is suitable for users who want their computer to automatically receive task emails after booting: it has no desktop window, but runs the Emailclaw Channel, cron jobs, and Agents continuously. You still interact with it via email.

> This section applies to the `.tar.gz` and `.deb` files in the release package. It does not apply to macOS, Windows, nor to minimal containers without systemd or some WSL environments. If you only want to use it by opening a graphical window on your own computer, please skip this section and install it in the standard desktop manner.

### 9.1 Understand What Happens After Installation First

| Item | What Happens |
| --- | --- |
| Program | Installs to `/opt/emailclaw/`; a symlink is created at `~/.local/bin/emailclaw` |
| Account | No dedicated system account; the service runs under your own Linux user |
| Service | Creates the user-level unit `~/.config/systemd/user/emailclaw.service` |
| Data Directory | Uses the same `~/emailclaw` directory as the desktop app; no separate service directory |
| Boot Startup | Enables `loginctl enable-linger` and registers the service for the installing user |

This means the service configuration and the `~/emailclaw` you use when you log into the desktop are **the same directory**. Configurations made in the desktop interface are automatically available to the service; no copying or ownership changes are needed.

### 9.2 Pre-installation Checklist

Prepare the following:

1. A 64-bit Ubuntu/Debian computer or server, with internet access.
2. The Linux `.tar.gz` or `.deb` file downloaded from the project Release, e.g., `emailclaw-linux-latest.tar.gz` or `emailclaw-linux-latest.deb`.
3. A dedicated email account with IMAP and SMTP enabled, and the **app-specific password/authorization code** for that email.
4. A usable model Provider's API Key.

The method to open the terminal is usually pressing <kbd>Ctrl</kbd> + <kbd>Alt</kbd> + <kbd>T</kbd>. In the commands below, the `$` at the beginning is just a prompt, **do not type the `$` itself**.

First, confirm the system uses systemd:

```sh
ps -p 1 -o comm=
```

If the output is `systemd`, you can continue. If not, please use the normal desktop mode or seek assistance from someone familiar with Linux service management for deployment.

### 9.3 Install the Package

**Method A — `.tar.gz` (recommended, sudo-free)**

Download and extract the archive to your home directory:

```sh
mkdir -p ~/.local/ && cd ~/.local/
wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.tar.gz
tar -xzf emailclaw-linux-latest.tar.gz -C ~/.local/ --no-same-owner
```

The archive contains a `~/.local/emailclaw/` directory with the binary at `~/.local/emailclaw/bin/emailclaw`. You can run the desktop application directly:

```sh
~/.local/emailclaw/bin/emailclaw
```

To set up the **headless background service**,  passes --service parameter to activate Service mode and create the systemd unit manually:

```sh
mkdir -p ~/.config/systemd/user

cat > ~/.config/systemd/user/emailclaw.service << EOF
[Unit]
Description=emailclaw Background Daemon Service
After=network.target

[Service]
Type=simple
Environment="JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC"
ExecStart=$HOME/.local/emailclaw/bin/emailclaw --service
TimeoutStopSec=20
Restart=on-failure
RestartSec=5s
LimitNOFILE=65535

[Install]
WantedBy=default.target
EOF
```

**Method B — `.deb` (requires sudo)**

The `.deb` installer runs the same steps automatically (symlink, systemd unit, linger):

```sh
cd ~/Downloads
wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.deb
sudo apt install ./emailclaw-linux-latest.deb
```

Please do not delete the installation package, at least keep it until the service test passes. Launch **Emailclaw** from the application menu or by running `/opt/emailclaw/bin/emailclaw`.

### 9.4 Prepare Configuration with the Desktop Interface (Recommended and Best for Beginners)

If installed on a computer with a desktop environment, first launch **Emailclaw** from the application menu or by running `~/.local/emailclaw/bin/emailclaw` (Method A) or `/opt/emailclaw/bin/emailclaw` (Method B), and complete the steps from the earlier sections of this manual:

1. In **Providers**, add the model service, API Key, and model ID.
2. In **Agents**, select or create the default Agent, and choose the model.
3. In **Channels → Emailclaw**, fill in the dedicated email, app password, IMAP/SMTP parameters, and allowed senders; save and enable the channel.
4. Close desktop Emailclaw.

These configurations are saved to `~/emailclaw`, which is the **same directory** the background service uses, so you do not need to copy anything.

### 9.5 Enable the Background Service

All commands below require **no sudo**. Enable and start the service:

```sh
loginctl enable-linger "$USER"
systemctl --user daemon-reload
systemctl --user enable --now emailclaw
```

The first command keeps the service running even when you log out, and makes it start automatically at boot. The second reloads the user manager to pick up the new unit file. The third registers the service to start on boot and starts it immediately.

If you used Method A (tar.gz), the daemon is now ready. If you used Method B (`.deb`), the installer already ran these commands for you.

Confirm the configuration is in place:

```sh
ls -la ~/emailclaw/.config
```

Normally you will see `providers.json`, `agents.json`, `channels.json`, etc., because the desktop configuration you made in section 9.4 is already stored here.

If your machine has multiple users, each of them can enable their own independent instance with the same commands; every instance keeps its own `~/emailclaw` configuration and data.

### 9.6 Start, Verify, and Test the Service

Check the status (if you ran the enable command in section 9.5, the service is already running):

```sh
systemctl --user status emailclaw
```

Seeing `Active: active (running)` means the service has started. Confirm it starts at boot:

```sh
systemctl --user is-enabled emailclaw
```

An output of `enabled` means it will automatically start when the computer reboots. To view the real-time runtime log, execute:

```sh
journalctl --user -u emailclaw -f
```

Keep this terminal window open, and send an email with a **new subject** from an allowed sender email to the Agent's email. You will receive a reply for the first time with a TaskId creating the file; reply to this email without altering the TaskId at the end of the subject. Once you confirm you can receive the Agent's result email, just press <kbd>Ctrl</kbd> + <kbd>C</kbd> to stop the "view log" command, **it will not stop the Emailclaw service**.

The Emailclaw email channel actively connects to the IMAP/SMTP server, so you do not need to open inbound ports in your router or cloud firewall to receive emails; but the computer must be able to access your model Provider and email service provider.

### 9.7 Daily Management Commands

All commands are **sudo-free**:

| What you want to do | Command |
| --- | --- |
| Check if running | `systemctl --user status emailclaw` |
| Start | `systemctl --user start emailclaw` |
| Stop | `systemctl --user stop emailclaw` |
| Restart (recommended after modifying configuration) | `systemctl --user restart emailclaw` |
| View the last 100 lines of log | `journalctl --user -u emailclaw -n 100 --no-pager` |
| View log in real-time | `journalctl --user -u emailclaw -f` |
| Disable start on boot | `systemctl --user disable emailclaw` |
| Enable start on boot | `systemctl --user enable emailclaw` |

After modifying the configuration in `~/emailclaw`, running `systemctl --user restart emailclaw` is the easiest way to ensure the settings are reloaded.

### 9.8 Common Troubleshooting: Check in this order

1. `status` shows `failed`: First run `journalctl --user -u emailclaw -n 100 --no-pager`, look from the bottom up for `ERROR`, `authentication`, `permission denied`, or network errors.
2. Log shows email authentication failed: Check if IMAP/SMTP is enabled, if the app-specific password is used, and if the email address matches the server/port/SSL settings.
3. Service runs but does not process emails: Check if the channel is enabled in `~/emailclaw/.config/channels.json`, if the sender email is in the allowed list, and confirm the configuration exists in your own `~/emailclaw/`.
4. Log shows model authentication or model does not exist: Check the API Key, Base URL, and model ID in `~/emailclaw/.config/providers.json`, and if the default Agent selected the correct Provider/model.
5. `Failed to connect to bus` or `Unit emailclaw.service could not be found`: The user manager may not be running or the service is not enabled for this user. Run `loginctl enable-linger "$USER"` and retry `systemctl --user enable --now emailclaw`. If the unit file itself is missing, check that the installation extracted files correctly: for `.tar.gz` the binary should be at `~/.local/emailclaw/bin/emailclaw`, for `.deb` it should be at `/opt/emailclaw/bin/emailclaw`.

### 9.9 Update, Backup, and Uninstall

Back up your data before updating. The backup file will be placed in your home directory:

```sh
systemctl --user stop emailclaw
tar -czf ~/emailclaw-backup.tar.gz ~/emailclaw --no-same-owner
systemctl --user start emailclaw
```

To update, extract the new `.tar.gz` over the existing `~/.local/emailclaw/` directory (for Method A), or reinstall the `.deb` (for Method B, which updates `/opt/emailclaw/`), then restart:

```sh
systemctl --user restart emailclaw
```

A regular uninstall stops and unregisters the service, but does not delete your data in `~/emailclaw`. If you installed via `.deb`:

```sh
sudo apt remove emailclaw
```

To permanently delete all Emailclaw data after uninstalling:

```sh
rm -rf ~/emailclaw
```

Note: `sudo apt purge emailclaw` only removes package files and leftover files from older versions. Because your data lives in your own home directory, purge does **not** delete `~/emailclaw`; delete it manually as shown above if needed.

## 10. Frequently Asked Questions

### Why didn't the first email directly get a model answer?

Because it is used to create an independent session and project, and return a TaskId. Please reply to that email (keeping the TaskId) to start execution; this allows the same subject to reliably return to the same context.

### The Agent didn't process my email?

Check sequentially: whether the channel is enabled, if the sender is in the allowed list, if the mailbox allows IMAP/SMTP, if the app password is valid, if the server parameters are correct, and if there are authentication or network errors in the logs.

### Why is my reply considered a new task?

The TaskId at the end of the subject might have been deleted, modified, or line-wrapped/truncated by the email client. Directly reply to the system's reply and keep the entire subject intact; do not manually overwrite the UUID at the end.

### Why can't the Agent modify a certain file or execute a command?

It might not be within the authorized scope of the current project/workspace, or the current permission mode requires approval. Check the project directory and permission settings first, then explicitly authorize it under careful consideration.

### Can I use my own Provider?

Yes. When creating a Provider, fill in the Base URL, API Key, and model ID provided by the service provider. Ensure the selected model is compatible with the required tool calls, streaming output, or multimodal capabilities.

## 11. Security Checklist

- Use a dedicated email and an app-specific password.
- Only allow trusted senders; do not set the whitelist to public sources.
- API Keys are only stored in protected configurations, not written in emails or screenshots.
- Start with `explore` or `default` permission mode, keeping manual approval.
- Only install trusted Skills, MCP services, and plugins.
- Regularly back up `.config/`, projects, and important sessions, and check `logs/`.
