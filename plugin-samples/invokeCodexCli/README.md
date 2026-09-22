# Invoke Codex CLI Plugin Usage & Deployment Guide

The `invokeCodexCli` plugin is an industrial-grade **Tool Plugin** designed for Emailclaw. It enables autonomous AI Agents (built with AgentScope Java) to invoke the Codex CLI via `codex exec [OPTIONS] [PROMPT]` non-interactively and return structured JSON results immediately upon task completion.

---

## 1. Compilation & Packaging

To compile and produce the deployable plugin JAR file, navigate to the `plugin-samples` root directory and execute the Maven Reactor build command:

```sh
# Navigate to the plugin-samples root
cd plugin-samples

# Build and package invokeCodexCli using Maven Reactor
mvn -pl invokeCodexCli -am package
```

> **Important**: Do not execute `mvn clean compile` directly inside the `invokeCodexCli` directory without the parent reactor context, as local artifact resolution requires the reactor.

After packaging succeeds, the standalone shaded plugin JAR will be generated in `invokeCodexCli/target/`:
- **Artifact Path**: `invokeCodexCli/target/emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar`
- **Release Path**: `invokeCodexCli/release/emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar`

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
cp invokeCodexCli/target/emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item invokeCodexCli\target\emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar $HOME\emailclaw\plugins\
```

### Step 3: Verify Activation
- **Dynamic Hot-Loading (No Restart Required)**: Emailclaw's background directory watcher (scanning every 5 seconds) will automatically detect the new JAR, instantiate the plugin, and register the `@Tool` entry into the runtime registry. The console/log will report:
  ```
  INFO: Discovered external plugin: emailclaw-plugin-tool-invokeCodexCli
  INFO: Successfully registered Tool plugin: invokeCodexCli
  INFO: InvokeCodexCliPlugin started
  ```
- **Application Startup Loading**: If Emailclaw is not currently running, `PluginManager` will automatically scan `~/emailclaw/plugins/` and load the plugin during system bootstrap.

---

## 3. How to Use the Plugin in Emailclaw

### 3.1 Autonomous Agent Tool Invocations
Once loaded, `invokeCodexCli` is automatically registered into the Agent's active `Toolkit`.

During chat sessions or automated agent workflows, prompts that require code refactoring, automated coding, file explanation, or CLI execution will trigger the LLM to autonomously call `invokeCodexCli`. For example:
- *"Refactor the authentication module using Codex CLI"*
- *"Run Codex CLI to review recent code changes against the repository"*
- *"Use invokeCodexCli to generate a database migration script"*

The tool executes:
```bash
codex exec --color never --skip-git-repo-check "Refactor the authentication module"
```
in the background with the project working directory set on the process, closes standard input immediately to prevent stdin blocking, extracts clean JSON (supporting JSON objects, arrays, and JSON Lines streams), and returns it directly to the agent.

---

## 4. Configuration Options

If you wish to customize the executable path or default timeout, you can edit Emailclaw's configuration file.

### 4.1 Configuration File Location
- **Linux / macOS**: `~/emailclaw/.config/plugins.json` (or `tools.json` / `global-config.json`)
- **Windows**: `%USERPROFILE%\emailclaw\.config\plugins.json`

### 4.2 Configuration Example
```json
{
  "id": "emailclaw-plugin-tool-invokeCodexCli",
  "name": "Invoke Codex CLI",
  "enabled": true,
  "pluginConfig": {
    "cli_path": "codex",
    "default_timeout": 300,
    "continue_last_session": true
  }
}
```

### 4.3 Field Descriptions
- `cli_path`: The command name or absolute path of the Codex CLI binary (default `"codex"`).
- `default_timeout`: Execution timeout in seconds (default `300`).
- `continue_last_session`: Whether to continue the last session by default (default `true`).

---

## 5. Agent Tool Calling Contract (Tool Schema)

| Parameter | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `prompt` | String | **Yes** | - | Task description or prompt sent to Codex CLI via `codex exec [OPTIONS] [PROMPT]`. |
| `cli_path` | String | No | `codex` | Path or binary name for the Codex CLI executable. |
| `model` | String | No | `null` | Optional model override passed via `-m/--model` (e.g. `gpt-5`, `o3-mini`). |
| `sandbox` | String | No | `null` | Optional sandbox mode policy passed via `-s` (e.g. `read-only`, `workspace-write`, `danger-full-access`). Only applied when starting a new session. |
| `timeout_seconds` | Integer | No | `300` | Optional timeout limit in seconds. |
| `extra_args` | String | No | `null` | Additional CLI flags (e.g. `--dangerously-bypass-approvals-and-sandbox`, `--ephemeral`, `--json`). |
| `continue_last_session` | Boolean | No | `true` | Whether to continue the last interactive session (`codex exec resume --last`). If `false`, starts a new execution. |

---

## 6. Examples

### Agent Invocation Request:
```json
{
  "prompt": "Refactor DatabaseManager.java to optimize connection pooling",
  "model": "gpt-5",
  "sandbox": "workspace-write",
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
