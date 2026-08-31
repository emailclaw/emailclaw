# Invoke Antigravity CLI Plugin Usage & Deployment Guide

The `invokeAntigravityCli` plugin is an industrial-grade **Tool Plugin** designed for Emailclaw. It enables autonomous AI Agents (built with AgentScope Java) to invoke the Google Antigravity CLI (`agy`) in headless print mode (`-p` / `--print`) and return structured JSON results immediately upon task completion.

---

## 1. Compilation & Packaging

To compile and produce the deployable plugin JAR file, navigate to the `plugin-samples` root directory and execute the Maven Reactor build command:

```sh
# Navigate to the plugin-samples root
cd plugin-samples

# Build and package invokeAntigravityCli using Maven Reactor
mvn -pl invokeAntigravityCli -am package
```

> **Important**: Do not execute `mvn clean compile` directly inside the `invokeAntigravityCli` directory without the parent reactor context, as local artifact resolution requires the reactor.

After packaging succeeds, the standalone shaded plugin JAR will be generated in `invokeAntigravityCli/target/`:
- **Artifact Path**: `invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar`

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
cp invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item invokeAntigravityCli\target\emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar $HOME\emailclaw\plugins\
```

### Step 3: Verify Activation
- **Dynamic Hot-Loading (No Restart Required)**: Emailclaw's background directory watcher (scanning every 5 seconds) will automatically detect the new JAR, instantiate the plugin, and register the `@Tool` entry into the runtime registry. The console/log will report:
  ```
  INFO: Discovered external plugin: emailclaw-plugin-tool-invokeAntigravityCli
  INFO: Successfully registered Tool plugin: invokeAntigravityCli
  INFO: InvokeAntigravityCliPlugin started
  ```
- **Application Startup Loading**: If Emailclaw is not currently running, `PluginManager` will automatically scan `~/emailclaw/plugins/` and load the plugin during system bootstrap.

---

## 3. How to Use the Plugin in Emailclaw

### 3.1 Autonomous Agent Tool Invocations
Once loaded, `invokeAntigravityCli` is automatically registered into the Agent's active `Toolkit`.

During chat sessions or automated agent workflows, prompts that require code inspection, automated testing, or CLI execution will trigger the LLM to autonomously call `invokeAntigravityCli`. For example:
- *"Use invokeAntigravityCli to run unit tests and provide a JSON summary report"*
- *"Run Antigravity CLI to analyze code changes in the current project"*
- *"Execute agy headless mode to generate technical documentation in JSON"*

The tool will execute the CLI command in the background, extract clean JSON, and return it directly to the agent.

---

## 4. Configuration Options

If you wish to customize the executable path, default timeout, or permission policies, you can edit Emailclaw's configuration file.

### 4.1 Configuration File Location
- **Linux / macOS**: `~/emailclaw/.config/plugins.json` (or `tools.json` / `global-config.json`)
- **Windows**: `%USERPROFILE%\emailclaw\.config\plugins.json`

### 4.2 Configuration Example
```json
{
  "id": "emailclaw-plugin-tool-invokeAntigravityCli",
  "name": "Invoke Antigravity CLI",
  "enabled": true,
  "pluginConfig": {
    "cli_path": "agy",
    "default_timeout": 300,
    "dangerously_skip_permissions": true
  }
}
```

### 4.3 Field Descriptions
- `cli_path`: The command name or absolute path of the Antigravity CLI binary (default `"agy"`).
- `default_timeout`: Execution timeout in seconds (default `300`).
- `dangerously_skip_permissions`: Whether to automatically pass `--dangerously-skip-permissions` (default `true`, required for headless non-interactive runs).

---

## 5. Agent Tool Calling Contract (Tool Schema)

| Parameter | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `prompt` | String | **Yes** | - | Task description or prompt sent to Antigravity CLI. Automatically appended with strict JSON formatting instructions. |
| `working_directory` | String | No | Project Base / `.` | Directory in which the CLI process executes. |
| `model` | String | No | `null` | Optional model override (e.g. `gemini-2.5-pro`). |
| `timeout_seconds` | Integer | No | `300` | Optional timeout limit in seconds. |
| `dangerously_skip_permissions` | Boolean | No | `true` | Whether to automatically pass `--dangerously-skip-permissions`. |
| `extra_args` | String | No | `null` | Additional CLI flags (e.g. `--verbose`). |

---

## 6. Examples

### Agent Invocation Request:
```json
{
  "prompt": "Run unit tests and return summary metrics as JSON",
  "working_directory": "/home/user/workspace/project-alpha",
  "model": "gemini-2.5-pro",
  "timeout_seconds": 120,
  "dangerously_skip_permissions": true
}
```

### Native ToolResultBlock Return:

The tool returns an AgentScope-native `ToolResultBlock` directly, preventing double JSON serialization:
- **`state`**: `ToolResultState.SUCCESS` (successful execution) or `ToolResultState.ERROR` (on failure, timeout, or invalid prompt).
- **`output`**: A `TextBlock` with clean structured JSON for the LLM to inspect:
  ```json
  {
    "test_suite": "user-service",
    "total": 42,
    "passed": 42,
    "failed": 0,
    "duration_ms": 1540
  }
  ```
- **`metadata`**: System and execution metrics for tracing and auditing:
  ```json
  {
    "exitCode": 0,
    "timedOut": false,
    "workingDirectory": "/home/user/workspace/project-alpha"
  }
  ```

