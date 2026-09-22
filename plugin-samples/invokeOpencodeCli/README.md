# Invoke OpenCode CLI Plugin Usage & Deployment Guide

The `invokeOpencodeCli` plugin is an industrial-grade **Tool Plugin** designed for Emailclaw. It enables autonomous AI Agents (built with AgentScope Java) to invoke the OpenCode CLI via `opencode run [message..]` and return structured JSON results immediately upon task completion.

---

## 1. Compilation & Packaging

To compile and produce the deployable plugin JAR file, navigate to the `plugin-samples` root directory and execute the Maven Reactor build command:

```sh
# Navigate to the plugin-samples root
cd plugin-samples

# Build and package invokeOpencodeCli using Maven Reactor
mvn -pl invokeOpencodeCli -am package
```

> **Important**: Do not execute `mvn clean compile` directly inside the `invokeOpencodeCli` directory without the parent reactor context, as local artifact resolution requires the reactor.

After packaging succeeds, the standalone shaded plugin JAR will be generated in `invokeOpencodeCli/target/`:
- **Artifact Path**: `invokeOpencodeCli/target/emailclaw-plugin-tool-invokeOpencodeCli-1.0.1.jar`

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
cp invokeOpencodeCli/target/emailclaw-plugin-tool-invokeOpencodeCli-1.0.1.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item invokeOpencodeCli\target\emailclaw-plugin-tool-invokeOpencodeCli-1.0.1.jar $HOME\emailclaw\plugins\
```

### Step 3: Verify Activation
- **Dynamic Hot-Loading (No Restart Required)**: Emailclaw's background directory watcher (scanning every 5 seconds) will automatically detect the new JAR, instantiate the plugin, and register the `@Tool` entry into the runtime registry. The console/log will report:
  ```
  INFO: Discovered external plugin: emailclaw-plugin-tool-invokeOpencodeCli
  INFO: Successfully registered Tool plugin: invokeOpencodeCli
  INFO: InvokeOpencodeCliPlugin started
  ```
- **Application Startup Loading**: If Emailclaw is not currently running, `PluginManager` will automatically scan `~/emailclaw/plugins/` and load the plugin during system bootstrap.

---

## 3. How to Use the Plugin in Emailclaw

### 3.1 Autonomous Agent Tool Invocations
Once loaded, `invokeOpencodeCli` is automatically registered into the Agent's active `Toolkit`.

During chat sessions or automated agent workflows, prompts that require code inspection, automated coding, file explanation, or CLI execution will trigger the LLM to autonomously call `invokeOpencodeCli`. For example:
- *"Explain the purpose of main.py in the current project using OpenCode"*
- *"Run OpenCode CLI to generate a unit test suite in JSON format"*
- *"Use invokeOpencodeCli to review recently modified files"*

The tool executes:
```bash
opencode run "Explain what main.py does"
```
in the background with the project working directory set on the process, extracts clean JSON (supporting JSON objects, arrays, and JSON Lines streams), and returns it directly to the agent.

---

## 4. Configuration Options

If you wish to customize the executable path or default timeout, you can edit Emailclaw's configuration file.

### 4.1 Configuration File Location
- **Linux / macOS**: `~/emailclaw/.config/plugins.json` (or `tools.json` / `global-config.json`)
- **Windows**: `%USERPROFILE%\emailclaw\.config\plugins.json`

### 4.2 Configuration Example
```json
{
  "id": "emailclaw-plugin-tool-invokeOpencodeCli",
  "name": "Invoke OpenCode CLI",
  "enabled": true,
  "pluginConfig": {
    "cli_path": "opencode",
    "default_timeout": 300
  }
}
```

### 4.3 Field Descriptions
- `cli_path`: The command name or absolute path of the OpenCode CLI binary (default `"opencode"`).
- `default_timeout`: Execution timeout in seconds (default `300`).

---

## 5. Agent Tool Calling Contract (Tool Schema)

| Parameter | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `prompt` | String | **Yes** | - | Task description or prompt sent to OpenCode CLI via `opencode run [message..]`. |
| `cli_path` | String | No | `opencode` | Path or binary name for the OpenCode CLI executable. |
| `model` | String | No | `null` | Optional model override passed via `-m` (e.g. `openai/gpt-4o`, `anthropic/claude-3-5-sonnet`). |
| `timeout_seconds` | Integer | No | `300` | Optional timeout limit in seconds. |
| `extra_args` | String | No | `null` | Additional CLI flags (e.g. `--auto`). |
| `continue_last_session` | Boolean | No | `true` | Whether to continue the last active session (`--continue`). Defaults to `true`. |

---

## 6. Examples

### Agent Invocation Request:
```json
{
  "prompt": "解释一下 main.py 这个文件的作用",
  "model": "openai/gpt-4o",
  "timeout_seconds": 60
}
```

### Native ToolResultBlock Return:

The tool returns an AgentScope-native `ToolResultBlock` directly, preventing double JSON serialization:
- **`state`**: `ToolResultState.SUCCESS` (successful execution) or `ToolResultState.ERROR` (on failure, timeout, or invalid prompt).
- **`output`**: A `TextBlock` with clean structured JSON for the LLM to inspect:
  ```json
  {
    "file": "main.py",
    "summary": "Main entry point orchestrating service bootstrap and HTTP server routing.",
    "dependencies": ["fastapi", "uvicorn", "pydantic"]
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
