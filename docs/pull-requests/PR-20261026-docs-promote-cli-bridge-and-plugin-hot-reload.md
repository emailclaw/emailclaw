# PR: Promote AI CLI Tool Bridging Capabilities and Plugin Hot-Reloading Across Project Documentation

- **PR Title**: `docs: promote AI CLI tool bridging capabilities and plugin hot-reloading across project documentation`
- **Related Issue / Context**: Feature promotion for `invokeAntigravityCli`, `invokeClaudeCodeCli`, `invokeCodexCli`, and `invokeOpencodeCli`
- **Type of Change**: Documentation Update
- **Status**: Verified & Ready to Merge

---

## 1. Summary

This pull request updates all primary Chinese and English documentation across the repository:
1. `github-source/README.md`
2. `github-source/README-zh.md`
3. `github-source/docs/Emailclaw-User-Manual.md`
4. `github-source/docs/Emailclaw-User-Manual-zh.md`

The updates prominently feature Emailclaw's core strength as a universal, zero-friction bridge connecting everyday email users to the industry's four leading autonomous AI coding CLIs:
- **Google Antigravity CLI** (`agy`) via `invokeAntigravityCli`
- **Anthropic Claude Code CLI** (`claude`) via `invokeClaudeCodeCli`
- **OpenAI Codex CLI** (`codex`) via `invokeCodexCli`
- **OpenCode CLI** (`opencode`) via `invokeOpencodeCli`

Furthermore, the documentation provides clear, practical instructions on drop-in deployment into `~/emailclaw/plugins` with dynamic hot-reloading (zero restart required) and intelligent binary location via built-in `PathResolver` strategies.

---

## 2. Motivation & Highlights

### 2.1. Email as the Universal Zero-Install Bridge
Existing AI CLI developer tools require users to sit in front of a desktop terminal or configure complex mobile SSH keys, terminal emulators, and network tunneling. Emailclaw dismantles this friction:
- **No extra apps required**: Email is a universal open protocol already installed and configured on every mobile phone, tablet, and computer.
- **Asynchronous & Anywhere**: Users can initiate coding, refactoring, or code review tasks by sending an email from their phone during commutes or meetings. Emailclaw dispatches the request to local or server-side CLI tools in an isolated project workspace, and returns full output logs and code artifacts directly to the user's inbox thread.

### 2.2. Zero-Downtime Hot-Reloading (`~/emailclaw/plugins`)
Users frequently inquire whether adding new plugins requires stopping the headless background daemon or restarting the desktop app. The documentation explicitly clarifies:
- **Drop-in Deployment**: Simply copy the tool plugin JAR file (e.g. `emailclaw-plugin-tool-invokeAntigravityCli-1.0.1.jar`) into `~/emailclaw/plugins/`.
- **No Restart Needed**: Emailclaw’s dynamic filesystem watcher scans the directory every 5 seconds and performs automated classloading and SPI registration at runtime without service interruption.

### 2.3. Industrial-Grade PathResolver Discovery
Documents the automatic detection of CLI executables installed by official scripts into user-specific directories (`~/.local/bin`, `~/.opencode/bin`, toolchain directories, or system `PATH`), assuring users that headless services will seamlessly invoke them without path errors.

---

## 3. Detailed Changes by Document

### 3.1. `github-source/README-zh.md`
- **Core Features ("核心特性")**: Added a bullet point highlighting Emailclaw as a natural, zero-app bridge to cutting-edge AI CLIs.
- **Extensions and Plugins ("扩展与插件")**: Added subsection `### 桥接四大顶级 AI CLI 工具插件（免重启热加载）`, introducing the four tools, the value of Email as a ubiquitous bridge, and instructions for hot-reload installation into `~/emailclaw/plugins`.

### 3.2. `github-source/README.md`
- **Core Features**: Added `Universal Bridge to Top AI CLIs` feature bullet.
- **Extensions and Plugins**: Added subsection `### Bridging 4 Top-Tier AI CLI Tools (Zero-Downtime Hot-Reload)` with corresponding English descriptions.

### 3.3. `github-source/docs/Emailclaw-User-Manual-zh.md`
- **Section 8 ("Skills、MCP 与插件")**: Added Section 8.1 `### 8.1 桥接四大顶级 AI CLI 工具（以 Email 为唯一桥梁）`, providing:
  - Role definition for each tool plugin (`invokeAntigravityCli`, `invokeClaudeCodeCli`, `invokeCodexCli`, `invokeOpencodeCli`).
  - Analysis of the zero-app barrier and email-based asynchronous dispatch advantage.
  - Step-by-step hot-reload copy commands (`cp *.jar ~/emailclaw/plugins/`).
  - Explanation of automatic `PathResolver` detection and child process environment enrichment.

### 3.4. `github-source/docs/Emailclaw-User-Manual.md`
- **Section 8 ("Skills, MCP, and Plugins")**: Added Section 8.1 `### 8.1 Bridging Four Top-Tier AI CLI Tools (Email as the Sole Universal Bridge)` with complete English guidance covering features, zero-install mobile ergonomics, drop-in hot-reloading, and path resolution.

---

## 4. Verification

- All Markdown documents verified for formatting, table-of-contents alignment, and GitHub alert callouts (`[!IMPORTANT]`).
- Executed `mvn spotless:apply -o && mvn clean compile -o -DskipTests`:
  - **`BUILD SUCCESS`** (Spotless confirmed 286 files clean; 251 source files compiled with 0 errors).

---

## 5. Checklist
- [x] Accurate descriptions of all 4 CLI plugins (`invokeAntigravityCli`, `invokeClaudeCodeCli`, `invokeCodexCli`, `invokeOpencodeCli`).
- [x] Clear emphasis on Email as a zero-install universal bridge.
- [x] Explicit instructions for `~/emailclaw/plugins` drop-in deployment with zero-restart hot-reload.
- [x] Pinpoint edits applied without overwriting unrelated documentation sections.
- [x] English PR description saved to dedicated document.
- [x] Spotless and Maven clean compile verified.
