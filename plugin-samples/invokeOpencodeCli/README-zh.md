# Invoke OpenCode CLI 插件使用与部署指南

`invokeOpencodeCli` 是专为 Emailclaw 设计的工业级 **Agent 工具插件（Tool Plugin）**。它允许基于 AgentScope Java 构建的智能体（AI Agent）调用 OpenCode CLI（`opencode run [message..]`），并在执行完毕后立即退出且返回结构化 JSON 结果。

---

## 1. 编译与打包

在生成可部署的插件 JAR 文件前，请先进入 `plugin-samples` 根目录，执行 Maven Reactor 多模块打包命令：

```sh
# 进入 plugin-samples 目录
cd plugin-samples

# 使用 Reactor 模式编译并打包 invokeOpencodeCli 模块
mvn -pl invokeOpencodeCli -am package
```

> **注意**：请勿直接在 `invokeOpencodeCli` 目录内单独执行 `mvn clean compile`（单模块缺少父级 Reactor 本地解析依赖会导致报错）。

打包成功后，将在 `invokeOpencodeCli/target/` 目录下生成独立的 Shaded 插件包：
- **产物路径**：`invokeOpencodeCli/target/emailclaw-plugin-tool-invokeOpencodeCli-1.0.1.jar`

---

## 2. 部署与生效步骤

Emailclaw 支持**开箱即用的插件动态扫描与热加载机制**，部署步骤极其简单：

### 步骤 1：确保插件目录存在
Emailclaw 默认的外部插件加载目录为：
- **Linux / macOS**：`~/emailclaw/plugins/`
- **Windows**：`%USERPROFILE%\emailclaw\plugins\`

如果该目录尚不存在，可先手动创建：
```sh
mkdir -p ~/emailclaw/plugins
```

### 步骤 2：拷贝生成的 JAR 文件
将编译生成的插件 JAR 拷贝到 `plugins` 目录下：

```sh
# Linux / macOS
cp invokeOpencodeCli/target/emailclaw-plugin-tool-invokeOpencodeCli-1.0.1.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item invokeOpencodeCli\target\emailclaw-plugin-tool-invokeOpencodeCli-1.0.1.jar $HOME\emailclaw\plugins\
```

### 步骤 3：验证插件生效
- **动态热加载（无需重启）**：Emailclaw 内置后台目录监听器（默认每 5 秒扫描一次），检测到新放入的 JAR 文件后会自动加载并完成 `@Tool` 工具注册，控制台/日志将输出：
  ```
  INFO: Discovered external plugin: emailclaw-plugin-tool-invokeOpencodeCli
  INFO: Successfully registered Tool plugin: invokeOpencodeCli
  INFO: InvokeOpencodeCliPlugin started
  ```
- **随系统启动载入**：若 Emailclaw 处于未启动状态，启动应用时 `PluginManager` 会自动扫描 `~/emailclaw/plugins/` 并完成装载。

---

## 3. 在 Emailclaw 中使用本插件

### 3.1 供 AI Agent 自动调用
本插件生效后，`invokeOpencodeCli` 工具会自动汇聚到 Agent 的可用工具库（`Toolkit`）中。

当您在 Emailclaw 聊天界面中与 Agent 交互时，只需下发相关开发指令，Agent 即可自动决策并调用该工具，例如：
- *“请解释一下 main.py 这个文件的作用”*
- *“使用 OpenCode CLI 分析当前项目架构并以 JSON 格式输出”*
- *“调用 invokeOpencodeCli 生成单元测试用例”*

Agent 将自动在后台执行以下命令（工作目录自动设置为当前工程根目录）并获取结构化结果：
```bash
opencode run "解释一下 main.py 这个文件的作用"
```

---

## 4. 插件配置说明

如果需要自定义 OpenCode CLI 的执行路径或默认超时时间，可编辑配置文件。

### 4.1 配置文件位置
- **Linux / macOS**: `~/emailclaw/.config/plugins.json`（或 `tools.json` / `global-config.json`)
- **Windows**: `%USERPROFILE%\emailclaw\.config\plugins.json`

### 4.2 配置示例
在配置文件的对应条目中设置 `pluginConfig`：

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

### 4.3 字段说明
- `cli_path`：OpenCode CLI 可执行文件命令或路径（默认 `"opencode"`；若未配置环境变量可填写绝对路径如 `"/usr/local/bin/opencode"`）。
- `default_timeout`：默认超时上限（单位：秒，默认 `300`）。

---

## 5. Agent 工具调用契约（Tool Schema）

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `prompt` | String | **是** | - | 发送给 OpenCode CLI 的任务 Prompt（通过 `opencode run [message..]` 传递）。 |
| `cli_path` | String | 否 | `opencode` | 指定 OpenCode CLI 可执行路径。 |
| `model` | String | 否 | `null` | 指定覆盖的大模型名称（通过 `-m` 传递，如 `openai/gpt-4o`, `anthropic/claude-3-5-sonnet`）。 |
| `timeout_seconds` | Integer | 否 | `300` | 单次执行超时时间（秒）。 |
| `extra_args` | String | 否 | `null` | 附加 CLI 参数（如 `--auto`）。 |
| `continue_last_session` | Boolean | 否 | `true` | 是否继续上一个会话（通过 `--continue` 传递）。默认为 `true`。 |

---

## 6. 交互示例

### Agent 调用入参：
```json
{
  "prompt": "解释一下 main.py 这个文件的作用",
  "model": "openai/gpt-4o",
  "timeout_seconds": 60
}
```

### 工具执行返回（原生 ToolResultBlock 格式）：

工具执行完毕后直接返回 AgentScope 原生 `ToolResultBlock`，避免二次 JSON 转义：
- **`state`**：`ToolResultState.SUCCESS`（执行成功）或 `ToolResultState.ERROR`（执行失败/超时/参数错误）。
- **`output`**：包含纯净结构化 JSON 内容的 `TextBlock`，供大模型直接理解：
  ```json
  {
    "file": "main.py",
    "summary": "Main entry point orchestrating service bootstrap and HTTP server routing.",
    "dependencies": ["fastapi", "uvicorn", "pydantic"]
  }
  ```
- **`metadata`**：供系统监控与追踪审计的执行元数据：
  ```json
  {
    "exitCode": 0,
    "timedOut": false,
    "workingDirectory": "/path/to/your/project"
  }
  ```
