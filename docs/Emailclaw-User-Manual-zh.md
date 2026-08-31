# Emailclaw 用户安装使用说明书

## 1. 开始前须知

Emailclaw 让你通过电子邮件使用 AI Agent。建议使用专门的邮箱账号，并在该邮箱服务商中启用 IMAP、SMTP 和应用专用密码/授权码。请不要把个人主邮箱密码、模型 API Key 或其他密钥放进邮件正文或转发给他人。

首次使用需要完成四件事：安装应用、配置模型 Provider、创建或确认默认 Agent、配置并启用 Emailclaw Channel。

## 2. 安装

### 2.1 使用发布包（推荐）

发布流程会为以下平台构建桌面安装包，请到 <https://github.com/emailclaw/emailclaw/releases/> 下载最新版本安装包：

| 平台 | 资产类型 | 安装方式 |
| --- | --- | --- |
| Windows | `.exe` | 下载后双击安装，按安装向导完成操作 |
| macOS | `.dmg` | 打开磁盘映像，将应用拖入“应用程序”目录 |
| Linux 便携部署 | `.tar.gz` | 执行 `mkdir -p ~/.local/ && cd ~/.local/ && wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.tar.gz && tar -xzf emailclaw-linux-latest.tar.gz -C ~/.local/ --no-same-owner`；启动 **Emailclaw** 运行 `~/.local/emailclaw/bin/emailclaw` |
| Debian/Ubuntu Linux | `.deb` | 执行 `wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.deb && sudo apt install ./emailclaw-linux-latest.deb`；从应用菜单启动 **Emailclaw**，或运行  `/opt/emailclaw/bin/emailclaw` |

### 2.2 从源码构建

仅在你需要自行编译时使用本节。构建要求 JDK 25、Maven 以及对应平台的打包能力。

```sh
mvn -B package -DskipTests
```

构建配置会生成 shaded JAR，并通过 `jpackage` 生成平台安装包。Linux 项目的默认 profile 会额外生成 app-image 与 `.tar.gz`。GitHub Actions 会在打标签时分别在 Windows、macOS、Linux 运行对应 profile；本地构建请使用与你的操作系统一致的环境。

## 3. 第一次启动与模型配置

1. 启动 Emailclaw 桌面应用。
2. 打开 **Providers（模型供应商）**，新建一个 Provider。
3. 填写 Provider 名称、Base URL、API Key，并添加该服务商允许使用的模型 ID。
4. 打开 **Agents（智能体）**，创建或编辑 Agent，选择主模型，写入系统提示词和职责。
5. 将该 Agent 设为默认 Agent；邮件任务会由默认 Agent 接收。

如果配置了回退 Provider/模型，主模型调用失败时系统可按 Agent 设置进行重试或回退。请先用桌面聊天做一个简单测试，确认模型凭据和网络连接正确。

## 4. 配置 Emailclaw Channel

在 **Channels（渠道）** 中找到 **Emailclaw**，打开配置页面。你可以使用自己的邮箱，或在系统提供邮箱功能可用且你已获授权时按界面指引注册。

### 4.1 使用自己的邮箱

填写以下项目：

- **Email Address**：Emailclaw 用来收发任务的邮箱地址。
- **Email Password**：应用专用密码、授权码或该邮箱服务要求的凭据。
- **IMAP Host / Port / SSL / STARTTLS**：收信服务器参数。
- **SMTP Host / Port / SSL / STARTTLS**：发信服务器参数。
- **Poll Interval**：轮询收件箱间隔，最小 5 秒，默认 30 秒。
- **Allowed Senders**：允许向 Agent 发任务的邮箱地址，每行一个。

Gmail、Outlook、iCloud、163、126、QQ、Foxmail、Yahoo 等常见地址会自动匹配预设参数。若你的服务商不在预设内，请向服务商查询 IMAP/SMTP 参数后手工填写。

保存配置后，在渠道页面启用 Emailclaw。系统会开始在后台轮询邮箱。强烈建议只添加你信任的地址到允许发件人列表。

### 4.2 使用使用系统提供的Emailclaw Channel Service

前提是你已发送邮件到 otp@emailclaw.email 后获取了一次性密码，且完全同意Emailclaw Channel Service Agreement:
1. The Service Provider reserves all rights, and may suspend or change the terms of this agreement at any time without prior notice.
2. The Service Provider provides no quality guarantee for the services provided. The Service Provider shall not be held liable for any direct or indirect damages arising from the use of this service.
3. The Emailclaw Channel only provides email channel services, and does not provide email storage services. All sent or received emails will be completely and irrecoverably deleted from the server after 15 minutes.
4. If a user registers for the Emailclaw Channel service provided by the system but does not use the service for more than 100 consecutive days, the Service Provider will delete the user's service.
5. By using this service, you agree to comply with all applicable local and international laws and regulations.

只需填写以下项目：

- **Registration Email**：用来注册服务的邮箱地址。
- **One-time Password**：获取到的一次性密码凭据。

保存配置后，在渠道页面启用 Emailclaw。系统会开始在后台轮询邮箱。允许发件人就是你自己的邮箱。

### 4.3 配置文件位置（服务模式或排障时）

默认应用主目录为：

- Linux/macOS：`~/emailclaw`
- Windows：`%USERPROFILE%\emailclaw`

可以通过 Java 系统属性 `emailclaw.home` 或环境变量 `EMAILCLAW_HOME` 更改。渠道配置位于 `.config/channels.json`；Provider、Agent、项目和定时任务也保存在同一 `.config/` 目录。优先使用图形界面修改配置，手工编辑前请先备份并停止并发修改。


### 4.4 手工修改配置文件 channels.json

若你使用自己的邮箱，请参考上面章节填写相关字段；若你想使用系统提供的Emailclaw Channel Service，前提是你是被邀请用户，你已发送邮件到 otp@emailclaw.email 后获取了一次性密码，且完全同意Emailclaw Channel Service Agreement，则只需填写三个字段：`sysEmailMode" : true, "registrantEmail" : "<YOUR EMAIL>", "oneTimePassword : "<YOUR OTP>"`。

## 5. 最重要的使用方法：新主题即新项目

### 5.1 发起一个任务

1. 从白名单邮箱向 Emailclaw 配置的邮箱发送一封**全新主题**的邮件。
2. 在邮件正文中说明目标、背景、交付物和限制；需要资料时可附上不超过 10 MB 的单个附件。
3. 系统收到后会创建独立任务会话与项目，并回复一封主题末尾带 TaskId 的邮件。
4. 直接回复这封邮件，**保留主题末尾的 TaskId**，补充“开始执行”或进一步说明。
5. Agent 在同一线程中工作并将结果回邮；后续追问也请继续回复该线程。

示例：

```text
To: my-agent@example.com
Subject: 为 8 月产品评审整理竞品摘要

请阅读附件中的三份资料，输出一页中文摘要：
1. 三个共同趋势；
2. 每家产品的差异；
3. 给产品团队的两个建议。
```

收到 TaskId 后，请回复系统邮件，而不要重新输入同样的主题。重新发一封不含 TaskId 的新主题会创建另一个项目，这正是隔离不同任务的方式。

### 5.2 继续、切换与结束任务

- **继续同一任务**：回复带原 TaskId 的邮件，正文只写本次新增要求即可。
- **开始另一个任务**：新建邮件并写新主题。它得到新的会话、项目目录和上下文。
- **转发邮件**：转发内容通常会被识别为引用块；请在邮件顶部清晰写出本次需要 Agent 完成的动作。
- **附件交付**：当 Agent 生成可发送文件时，结果邮件可以带附件。保留项目可便于以后在本地界面查看文件和历史。

## 6. 邮件审批：让 Agent 先问再做

当 Agent 要执行敏感工具调用时，Emailclaw 可以发送审批邮件。邮件会写明工具、参数摘要、风险说明和四位确认码。

- 只回复邮件中对应的四位代码，即表示同意本次操作。
- 不回复、回复错误内容、在其他线程回复或等待超时，都会被视为拒绝。
- 审批码本身不会作为普通问题发送给模型，因此不会污染任务上下文。

首次上线建议将 Agent 的权限模式设为 `default` 或 `explore`。只有在充分了解工具行为后，才考虑 `accept_edits` 或更高权限；不要把 `bypass` 用于不受信任的邮件输入。

## 7. 项目、文件与自动化

### 项目和文件

每个新主题任务都有对应项目。可在 **Projects** 和 **Files** 中查看项目目录、会话记录及附件。请把需要 Agent 修改的文件置于授权项目/工作区中；如果文件不在允许目录内，系统可能拒绝访问，这是安全边界的一部分。

### 定时任务

在 **Cron Jobs / Automations** 中创建计划任务，选择项目、Agent、任务提示词和 Cron 表达式。可设置最大并发数以及执行次数倒计时，防止慢任务叠加。常见六段式表达式示例：

```text
0 0 9 * * ?       # 每天 09:00
0 0/30 * * * ?    # 每 30 分钟
0 0 17 ? * FRI    # 每周五 17:00
```
对于不熟悉Cron表达式的用户，系统界面上提供了全图形化的操作便于属于。此外，系统提供了一个名为Cron的skill（默认所有Agents都有该skill），你还可以在Task对话（注意不是Chat对话）中告诉Agent为你将本Task配置Cron即可。

定时任务通常会读取最新配置并重新调度；修改后仍应检查任务列表与运行历史，确认其实际生效。

## 8. Skills、MCP 与插件

在 **Skills** 中启用适合任务的操作说明，在 **MCP** 中添加经审查的工具服务，在 **Plugins** 中管理扩展。插件可以增加工具、模型、钩子、界面或额外渠道，但默认产品工作流仍以 Emailclaw Channel 为中心。

第三方插件拥有代码执行和网络访问的可能性。只从可信来源安装，阅读其权限和配置说明，并先在测试账户或低权限 Agent 上验证。开发者可参考仓库中的 `plugin-samples/PLUGIN_DEVELOPER_GUIDE.md` 与 `plugin-samples/dingtalk` 示例。

## 9. Linux 后台服务：给电脑入门者的完整安装指南

本节适用于 Ubuntu、Debian 及其他使用 **systemd** 的 Debian 系 Linux。后台服务适合希望电脑开机后自动收取任务邮件的用户：它没有桌面窗口，但会持续运行 Emailclaw Channel、定时任务和 Agent。你仍然通过邮件与它交互。

> 本节适用于发布包中的 `.tar.gz` 和 `.deb` 文件。它不适用于 macOS、Windows，也不适用于没有启用 systemd 的精简容器或部分 WSL 环境。若你只想在自己的电脑上打开图形窗口使用，请跳过本节，按普通桌面方式安装即可。

### 9.1 先理解安装后会发生什么

| 项目 | 实际行为 |
| --- | --- |
| 程序 | 安装到 `/opt/emailclaw/`；在 `~/.local/bin/` 建立 `emailclaw` 符号链接 |
| 账户 | **不**创建专用系统账户；服务以你自己的 Linux 用户身份运行 |
| 服务 | 创建用户级单元 `~/.config/systemd/user/emailclaw.service` |
| 数据目录 | 与桌面应用共用同一个 `~/emailclaw`；不存在独立的服务数据目录 |
| 开机启动 | 为安装用户启用 `loginctl enable-linger` 并注册服务 |

这意味着服务配置和你平时登录桌面时的 `~/emailclaw` **是同一个目录**。在桌面界面完成的配置自动对服务生效，无需复制或调整所有权。

### 9.2 安装前检查

准备好以下内容：

1. 一台 64 位 Ubuntu/Debian 电脑或服务器，且可以联网。
2. 从项目 Release 下载的 Linux `.tar.gz` 或 `.deb` 文件，例如 `emailclaw-linux-latest.tar.gz` 或 `emailclaw-linux-latest.deb`。
3. 一个已开通 IMAP 和 SMTP 的专用邮箱，以及该邮箱的**应用专用密码/授权码**。
4. 一个可用的模型 Provider 的 API Key。

打开终端的方法通常是按 <kbd>Ctrl</kbd> + <kbd>Alt</kbd> + <kbd>T</kbd>。下面的命令中，开头的 `$` 只是提示符，**不要输入 `$` 本身**。

先确认系统使用 systemd：

```sh
ps -p 1 -o comm=
```

如果输出为 `systemd`，可以继续。若不是，请使用普通桌面模式或请熟悉 Linux 服务管理的人员协助部署。

### 9.3 安装软件包

**方式 A — `.tar.gz`（推荐，无需 sudo）**

下载并解压到主目录：

```sh
mkdir -p ~/.local/ && cd ~/.local/
wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.tar.gz
tar -xzf emailclaw-linux-latest.tar.gz -C ~/.local/ --no-same-owner
```

解压后得到 `~/.local/emailclaw/` 目录，可执行文件位于 `~/.local/emailclaw/bin/emailclaw`。直接运行桌面应用：

```sh
~/.local/emailclaw/bin/emailclaw
```

要配置**无图形界面的后台服务**，需传入 --service 参数激活服务模式，需要手动创建 systemd 单元文件：

```sh
mkdir -p ~/.config/systemd/user

cat > ~/.config/systemd/user/emailclaw.service << EOF
[Unit]
Description=emailclaw 后台守护进程服务
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

**方式 B — `.deb`（需要 sudo）**

`.deb` 安装器会自动完成上述所有步骤（符号链接、systemd 单元、linger）；程序安装到 `/opt/emailclaw/`：

```sh
cd ~/Downloads
wget https://github.com/emailclaw/emailclaw/releases/latest/download/emailclaw-linux-latest.deb
sudo apt install ./emailclaw-linux-latest.deb
```
请不要删除安装包，至少保留到服务测试通过为止。从应用菜单启动 **Emailclaw**，或运行命令  `/opt/emailclaw/bin/emailclaw`。

### 9.4 用桌面界面准备配置（推荐且最适合新手）

如果安装的是有桌面环境的电脑，先从应用菜单启动 **Emailclaw**，或运行命令 `~/.local/emailclaw/bin/emailclaw`（方式 A）或 `/opt/emailclaw/bin/emailclaw`（方式 B），按本手册前面的步骤完成：

1. 在 **Providers** 添加模型服务、API Key 和模型 ID。
2. 在 **Agents** 选择或创建默认 Agent，并选择模型。
3. 在 **Channels → Emailclaw** 填写专用邮箱、应用密码、IMAP/SMTP 参数和允许发件人；保存并启用渠道。
4. 关闭桌面 Emailclaw。

这些配置会保存到 `~/emailclaw`，它与后台服务使用的是**同一个目录**，因此无需复制任何内容。

### 9.5 启用后台服务

以下所有命令**无需 sudo**。启用并启动服务：

```sh
loginctl enable-linger "$USER"
systemctl --user daemon-reload
systemctl --user enable --now emailclaw
```

第一条命令让服务在你退出登录后继续运行，并在开机时自动启动；第二条重新加载用户管理器以识别新的单元文件；第三条注册服务为开机启动并立即启动。

使用方式 A（tar.gz）的用户，守护进程已就绪；使用方式 B（`.deb`）的用户，安装器已自动执行过上述命令。

确认配置目录中已有文件：

```sh
ls -la ~/emailclaw/.config
```

正常情况下能看到 `providers.json`、`agents.json`、`channels.json` 等文件，因为你在 9.4 节通过桌面界面做的配置已经存放在这里。

如果机器上有多个用户，每个用户都可以用同样的命令启用自己的独立实例；每个实例的数据与配置都各自保存在自己的 `~/emailclaw`。

### 9.6 启动、确认并测试服务

查看状态（如果你已按 9.5 节执行过启用命令，服务已经在运行）：

```sh
systemctl --user status emailclaw
```

看到 `Active: active (running)` 就表示服务已经启动。确认开机自启：

```sh
systemctl --user is-enabled emailclaw
```

输出 `enabled` 表示电脑重启后它会自动启动。要实时查看运行日志，执行：

```sh
journalctl --user -u emailclaw -f
```

保持这个终端窗口打开，然后从白名单邮箱向 Agent 邮箱发送一封**新主题**邮件。首次会收到带 TaskId 的建档回信；回复该邮件且不要改动主题末尾的 TaskId。确认能收到 Agent 的结果邮件后，按 <kbd>Ctrl</kbd> + <kbd>C</kbd> 停止"查看日志"命令即可，**不会停止 Emailclaw 服务**。

Emailclaw 邮件渠道主动连接 IMAP/SMTP 服务器，不需要为了收邮件在路由器或云防火墙中开放入站端口；但电脑必须能访问你的模型 Provider 和邮件服务商。

### 9.7 日常管理命令

所有命令**无需 sudo**：

| 想做什么 | 命令 |
| --- | --- |
| 查看是否运行 | `systemctl --user status emailclaw` |
| 启动 | `systemctl --user start emailclaw` |
| 停止 | `systemctl --user stop emailclaw` |
| 重启（修改配置后推荐） | `systemctl --user restart emailclaw` |
| 查看最近 100 行日志 | `journalctl --user -u emailclaw -n 100 --no-pager` |
| 实时查看日志 | `journalctl --user -u emailclaw -f` |
| 取消开机启动 | `systemctl --user disable emailclaw` |
| 恢复开机启动 | `systemctl --user enable emailclaw` |

当你手工修改了 `~/emailclaw` 中的配置后，执行 `systemctl --user restart emailclaw` 是最容易确认设置已重新加载的方式。

### 9.8 常见故障：按这个顺序排查

1. `status` 显示 `failed`：先运行 `journalctl --user -u emailclaw -n 100 --no-pager`，从最下面开始看 `ERROR`、`authentication`、`permission denied` 或网络错误。
2. 日志显示邮箱认证失败：检查是否启用 IMAP/SMTP，是否使用了应用专用密码，邮箱地址和服务器/端口/SSL 是否匹配。
3. 服务运行但不处理邮件：检查 `~/emailclaw/.config/channels.json` 中渠道已启用，发信邮箱在允许列表中，并确认配置存在于你自己的 `~/emailclaw/`。
4. 日志显示模型鉴权或模型不存在：检查 `~/emailclaw/.config/providers.json` 中的 API Key、Base URL 和模型 ID，以及默认 Agent 是否选择了正确的 Provider/模型。
5. `Failed to connect to bus` 或 `Unit emailclaw.service could not be found`：可能是用户管理器未运行或该用户尚未启用服务。运行 `loginctl enable-linger "$USER"` 后重试 `systemctl --user enable --now emailclaw`。如果单元文件本身缺失，检查安装是否正确：`.tar.gz` 方式应有 `~/.local/emailclaw/bin/emailclaw`，`.deb` 方式应有 `/opt/emailclaw/bin/emailclaw`。

### 9.9 更新、备份与卸载

更新前先备份数据。备份文件会放在你的主目录：

```sh
systemctl --user stop emailclaw
tar -czf ~/emailclaw-backup.tar.gz ~/emailclaw --no-same-owner
systemctl --user start emailclaw
```

更新时，解压新的 `.tar.gz` 覆盖现有的 `~/.local/emailclaw/` 目录（方式 A），或重新安装 `.deb`（方式 B，更新 `/opt/emailclaw/`），然后重启：

```sh
systemctl --user restart emailclaw
```

普通卸载会停止并注销服务，但不会删除你在 `~/emailclaw` 中的数据。如果使用 `.deb` 安装：

```sh
sudo apt remove emailclaw
```

要永久删除所有 Emailclaw 数据：

```sh
rm -rf ~/emailclaw
```

注意：`sudo apt purge emailclaw` 只会删除软件包文件和旧版本遗留文件。因为你的数据存放在自己的主目录，purge **不会**删除 `~/emailclaw`；如需删除请按上面命令手动执行。

## 10. 常见问题

### 为什么第一封邮件没有直接得到模型答案？

因为它用于创建独立的会话和项目，并返回 TaskId。请回复该邮件（保留 TaskId）以启动执行；这样可以让同一主题可靠地回到同一个上下文。

### Agent 没有处理我的邮件？

依次检查：渠道是否已启用、发件人是否在允许列表、邮箱是否允许 IMAP/SMTP、应用密码是否有效、服务器参数是否正确，以及日志中是否有认证或网络错误。

### 为什么回复被认为是新任务？

主题末尾的 TaskId 可能被邮件客户端删除、修改或换行截断。直接回复系统回信并保留完整主题；不要手动改写末尾 UUID。

### 为什么 Agent 不能改某个文件或执行命令？

它可能不在当前项目/工作区的授权范围内，或当前权限模式要求审批。先检查项目目录和权限设置，再在审慎的前提下明确授权。

### 我能用自己的 Provider 吗？

可以。创建 Provider 时填写服务商提供的 Base URL、API Key 和模型 ID。确保所选模型与所需的工具调用、流式输出或多模态能力兼容。

## 11. 安全检查清单

- 使用专用邮箱和应用专用密码。
- 只允许可信发件人；不要把白名单设为公开来源。
- API Key 只存放在受保护配置中，不写入邮件和截图。
- 从 `explore` 或 `default` 权限模式开始，保留人工审批。
- 只安装可信 Skill、MCP 服务和插件。
- 定期备份 `.config/`、项目与重要会话，并检查 `logs/`。
