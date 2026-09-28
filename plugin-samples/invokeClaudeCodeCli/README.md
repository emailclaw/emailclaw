# Invoke Claude Code CLI Plugin Usage & Deployment Guide

The `invokeClaudeCodeCli` plugin is an industrial-grade **Tool Plugin** designed for Emailclaw. It enables autonomous AI Agents (built with AgentScope Java) to invoke Claude Code CLI non-interactively via `claude -p [OPTIONS] [PROMPT]` and return structured JSON results immediately upon task completion.

---

## 1. Compilation & Packaging

To compile and produce the deployable plugin JAR file, navigate to the `plugin-samples` root directory and execute the Maven Reactor build command:

```sh
# Navigate to the plugin-samples root
cd plugin-samples

# Build and package invokeClaudeCodeCli using Maven Reactor
mvn -pl invokeClaudeCodeCli -am package
```

> **Important**: Do not execute `mvn clean compile` directly inside the `invokeClaudeCodeCli` directory without the parent reactor context, as local artifact resolution requires the reactor.

After packaging succeeds, the standalone shaded plugin JAR will be generated in `invokeClaudeCodeCli/target/`:
- **Artifact Path**: `invokeClaudeCodeCli/target/emailclaw-plugin-tool-invokeClaudeCodeCli-1.0.1.jar`
- **Release Path**: `invokeClaudeCodeCli/release/emailclaw-plugin-tool-invokeClaudeCodeCli-1.0.1.jar`

---

## 2. Deployment & Activation

Emailclaw provides **built-in dynamic scanning and hot-reloading** for external plugins. Deployment is straightforward:

### Step 1: Ensure Plugins Directory Exists
The default external plugins directory in Emailclaw is:
- **Linux / macOS**: `~/emailclaw/plugins/`
- **Windows**: `%USERPROFILE%\emailclaw\plugins\`

If this directory does not exist yet, create it:
```sh
mkdir -p ~/emailclaw/plugins
```

### Step 2: Copy the Generated JAR File
Copy the built plugin JAR into the `plugins` directory:

```sh
# Linux / macOS
cp invokeClaudeCodeCli/target/emailclaw-plugin-tool-invokeClaudeCodeCli-1.0.1.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item invokeClaudeCodeCli\target\emailclaw-plugin-tool-invokeClaudeCodeCli-1.0.1.jar $HOME\emailclaw\plugins\
```

### Step 3: Verify Activation
- **Dynamic Hot-Loading (No Restart Required)**: Emailclaw's background directory watcher (scanning every 5 seconds) will automatically detect the new JAR, instantiate the plugin, and register the `@Tool` entry into the runtime registry. The console/log will report:
  ```
  INFO: Discovered external plugin: emailclaw-plugin-tool-invokeClaudeCodeCli
  INFO: Successfully registered Tool plugin: invokeClaudeCodeCli
  INFO: InvokeClaudeCodeCliPlugin started
  ```
- **Application Startup Loading**: If Emailclaw is not currently running, `PluginManager` will automatically scan `~/emailclaw/plugins/` and load the plugin during system bootstrap.

---

## 3. How to Use the Plugin in Emailclaw

### 3.1 Autonomous Agent Tool Invocations
Once loaded, `invokeClaudeCodeCli` is automatically registered into the Agent's active `Toolkit`.

During chat sessions or automated agent workflows, prompts that require code refactoring, automated coding, file explanation, or CLI execution will trigger the LLM to autonomously call `invokeClaudeCodeCli`. For example:
- *"Refactor the authentication module using Claude Code"*
- *"Run Claude Code to review recent code changes against the repository"*
- *"Use invokeClaudeCodeCli to generate a unit test suite for UserService"*

The tool executes non-interactively in the background:
```bash
claude -c -p --output-format json "Refactor the authentication module"
```
with the project working directory configured on the process, closes standard input immediately to prevent stdin blocking, extracts clean JSON (supporting JSON objects, arrays, and JSON Lines streams), and returns it directly to the agent.

---

## 4. Configuration Options

If you wish to customize the executable path or default timeout, you can edit Emailclaw's configuration file.

### 4.1 Configuration File Location
- **Linux / macOS**: `~/emailclaw/.config/plugins.json` (or `tools.json` / `global-config.json`)
- **Windows**: `%USERPROFILE%\emailclaw\.config\plugins.json`

### 4.2 Configuration Example
```json
{
  "id": "emailclaw-plugin-tool-invokeClaudeCodeCli",
  "name": "Invoke Claude Code CLI",
  "enabled": true,
  "pluginConfig": {
    "cli_path": "claude",
    "default_timeout": 300,
    "continue_last_session": true,
    "permission_mode": "bypassPermissions"
  }
}
```

### 4.3 Field Descriptions
- `cli_path`: The command name or absolute path of the Claude Code CLI binary (default `"claude"`).
- `default_timeout`: Execution timeout in seconds (default `300`).
- `continue_last_session`: Whether to continue the last session by default (`-c` / `--continue`, default `true`).
- `permission_mode`: Default permission mode for automated execution (default `"bypassPermissions"`).

---

## 5. Agent Tool Calling Contract (Tool Schema)

| Parameter | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `prompt` | String | **Yes** | - | Task description or prompt sent to Claude Code CLI via `claude -p [OPTIONS] [PROMPT]`. |
| `cli_path` | String | No | `claude` | Path or binary name for the Claude Code CLI executable. |
| `model` | String | No | `null` | Optional model override passed via `--model` (e.g. `sonnet`, `opus`, `haiku`, `claude-sonnet-5`). |
| `permission_mode` | String | No | `null` | Optional permission mode passed via `--permission-mode` (e.g. `bypassPermissions`, `auto`, `plan`, `acceptEdits`). |
| `timeout_seconds` | Integer | No | `300` | Optional timeout limit in seconds. |
| `extra_args` | String | No | `null` | Additional CLI flags (e.g. `--dangerously-skip-permissions`, `--verbose`, `--bare`). |
| `continue_last_session` | Boolean | No | `true` | Whether to continue the most recent conversation in the directory (`-c` / `--continue`). If `false`, starts a new conversation. |

---

## 6. Examples

### Agent Invocation Request:
```json
{
  "prompt": "Refactor DatabaseManager.java to optimize connection pooling",
  "model": "sonnet",
  "permission_mode": "bypassPermissions",
  "timeout_seconds": 60,
  "continue_last_session": true
}
```

### Native ToolResultBlock Return:

The tool returns an AgentScope-native `ToolResultBlock` directly, preventing double JSON serialization:
- **`state`**: `ToolResultState.SUCCESS` (successful execution) or `ToolResultState.ERROR` (on failure, timeout, or invalid prompt).
- **`output`**: A `TextBlock` with clean structured JSON for the LLM to inspect:
  ```json
  {
    "status": "completed",
    "modifiedFiles": ["DatabaseManager.java"],
    "summary": "Optimized connection pooling by configuring HikariCP maximumPoolSize and leakDetectionThreshold."
  }
  ```
- **`metadata`**: System and execution metrics for tracing and auditing:
  ```json
  {
    "exitCode": 0,
    "timedOut": false,
    "workingDirectory": "/path/to/your/project"
  }
  ```
