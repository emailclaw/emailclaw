# Invoke Codex CLI 插件使用与部署指南

`invokeCodexCli` 是专为 Emailclaw 设计的工业级 **Agent 工具插件（Tool Plugin）**。它允许基于 AgentScope Java 构建的智能体（AI Agent）通过非交互模式调用 Codex CLI（`codex exec [OPTIONS] [PROMPT]`），并在执行完毕后立即退出且返回结构化 JSON 结果。

---

## 1. 编译与打包

在生成可部署的插件 JAR 文件前，请先进入 `plugin-samples` 根目录，执行 Maven Reactor 多模块打包命令：

```sh
# 进入 plugin-samples 目录
cd plugin-samples

# 使用 Reactor 模式编译并打包 invokeCodexCli 模块
mvn -pl invokeCodexCli -am package
```

> **注意**：请勿直接在 `invokeCodexCli` 目录内单独执行 `mvn clean compile`（单模块缺少父级 Reactor 本地解析依赖会导致报错）。

打包成功后，将在 `invokeCodexCli/target/` 目录下生成独立的 Shaded 插件包：
- **产物路径**：`invokeCodexCli/target/emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar`
- **Release 目录**：`invokeCodexCli/release/emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar`

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
cp invokeCodexCli/target/emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item invokeCodexCli\target\emailclaw-plugin-tool-invokeCodexCli-1.0.1.jar $HOME\emailclaw\plugins\
```

### 步骤 3：验证插件生效
- **动态热加载（无需重启）**：Emailclaw 内置后台目录监听器（默认每 5 秒扫描一次），检测到新放入的 JAR 文件后会自动加载并完成 `@Tool` 工具注册，控制台/日志将输出：
  ```
  INFO: Discovered external plugin: emailclaw-plugin-tool-invokeCodexCli
  INFO: Successfully registered Tool plugin: invokeCodexCli
  INFO: InvokeCodexCliPlugin started
  ```
- **随系统启动载入**：若 Emailclaw 处于未启动状态，启动应用时 `PluginManager` 会自动扫描 `~/emailclaw/plugins/` 并完成装载。

---

## 3. 在 Emailclaw 中使用本插件

### 3.1 供 AI Agent 自动调用
本插件生效后，`invokeCodexCli` 工具会自动汇聚到 Agent 的可用工具库（`Toolkit`）中。

当您在 Emailclaw 聊天界面中与 Agent 交互时，只需下发相关代码重构、代码审查或生成指令，Agent 即可自动决策并调用该工具，例如：
- *“使用 Codex CLI 重构项目认证鉴权模块”*
- *“调用 Codex CLI 对当前仓库代码变更进行评审”*
- *“使用 invokeCodexCli 生成数据库变更脚本”*

Agent 将自动在后台以非交互模式执行以下命令（工作目录自动设置为当前工程根目录）：
```bash
codex exec --color never --skip-git-repo-check "使用 Codex CLI 重构项目认证鉴权模块"
```
工具自动关闭标准输入杜绝管道挂起，抽取纯净结构化 JSON（支持 JSON 对象、数组与 JSON Lines 流），并直接回传给 Agent。

---

## 4. 插件配置说明

如果需要自定义 Codex CLI 的执行路径或默认超时时间，可编辑配置文件。

### 4.1 配置文件位置
- **Linux / macOS**: `~/emailclaw/.config/plugins.json`（或 `tools.json` / `global-config.json`)
- **Windows**: `%USERPROFILE%\emailclaw\.config\plugins.json`

### 4.2 配置示例
在配置文件的对应条目中设置 `pluginConfig`：

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

### 4.3 字段说明
- `cli_path`：Codex CLI 可执行文件命令或路径（默认 `"codex"`；若未配置环境变量可填写绝对路径如 `"/usr/local/bin/codex"`）。
- `default_timeout`：默认超时上限（单位：秒，默认 `300`）。
- `continue_last_session`：是否默认继续上一次会话（默认 `true`）。

---

## 5. Agent 工具调用契约（Tool Schema）

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `prompt` | String | **是** | - | 发送给 Codex CLI 的任务 Prompt（通过 `codex exec [OPTIONS] [PROMPT]` 传递）。 |
| `cli_path` | String | 否 | `codex` | 指定 Codex CLI 可执行路径。 |
| `model` | String | 否 | `null` | 指定大模型名称（通过 `-m/--model` 传递，如 `gpt-5`, `o3-mini`）。 |
| `sandbox` | String | 否 | `null` | 指定沙箱安全策略（通过 `-s/--sandbox` 传递，如 `read-only`, `workspace-write`, `danger-full-access`）。仅在新建会话时生效。 |
| `timeout_seconds` | Integer | 否 | `300` | 单次执行超时时间（秒）。 |
| `extra_args` | String | 否 | `null` | 附加 CLI 参数（如 `--dangerously-bypass-approvals-and-sandbox`, `--ephemeral`, `--json` 等）。 |
| `continue_last_session` | Boolean | 否 | `true` | 是否继续上一次交互式会话（`codex exec resume --last`）。设为 `false` 则开启新会话执行。 |

---

## 6. 交互示例

### Agent 调用入参：
```json
{
  "prompt": "重构 DatabaseManager.java 以优化连接池配置",
  "model": "gpt-5",
  "sandbox": "workspace-write",
  "timeout_seconds": 60,
  "continue_last_session": true
}
```

### 工具执行返回（原生 ToolResultBlock 格式）：

工具执行完毕后直接返回 AgentScope 原生 `ToolResultBlock`，避免二次 JSON 转义：
- **`state`**：`ToolResultState.SUCCESS`（执行成功）或 `ToolResultState.ERROR`（执行失败/超时/参数错误）。
- **`output`**：包含纯净结构化 JSON 内容的 `TextBlock`，供大模型直接理解：
  ```json
  {
    "status": "completed",
    "modifiedFiles": ["DatabaseManager.java"],
    "summary": "Optimized connection pooling by configuring HikariCP maximumPoolSize and leakDetectionThreshold."
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
