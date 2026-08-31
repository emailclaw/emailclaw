# Invoke Antigravity CLI 插件使用与部署指南

`invokeAntigravityCli` 是专为 Emailclaw 设计的工业级 **Agent 工具插件（Tool Plugin）**。它允许基于 AgentScope Java 构建的智能体（AI Agent）以无头打印模式（`-p` / `--print`）调用 Google Antigravity CLI（`agy`），并在执行完毕后立即退出且返回结构化 JSON 结果。

---

## 1. 编译与打包

在生成可部署的插件 JAR 文件前，请先进入 `plugin-samples` 根目录，执行 Maven Reactor 多模块打包命令：

```sh
# 进入 plugin-samples 目录
cd plugin-samples

# 使用 Reactor 模式编译并打包 invokeAntigravityCli 模块
mvn -pl invokeAntigravityCli -am package
```

> **注意**：请勿直接在 `invokeAntigravityCli` 目录内单独执行 `mvn clean compile`（单模块缺少父级 Reactor 本地解析依赖会导致报错）。

打包成功后，将在 `invokeAntigravityCli/target/` 目录下生成独立的 Shaded 插件包：
- **产物路径**：`invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar`

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
cp invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item invokeAntigravityCli\target\emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar $HOME\emailclaw\plugins\
```

### 步骤 3：验证插件生效
- **动态热加载（无需重启）**：Emailclaw 内置后台目录监听器（默认每 5 秒扫描一次），检测到新放入的 JAR 文件后会自动加载并完成 `@Tool` 工具注册，控制台/日志将输出：
  ```
  INFO: Discovered external plugin: emailclaw-plugin-tool-invokeAntigravityCli
  INFO: Successfully registered Tool plugin: invokeAntigravityCli
  INFO: InvokeAntigravityCliPlugin started
  ```
- **随系统启动载入**：若 Emailclaw 处于未启动状态，启动应用时 `PluginManager` 会自动扫描 `~/emailclaw/plugins/` 并完成装载。

---

## 3. 在 Emailclaw 中使用本插件

### 3.1 供 AI Agent 自动调用
本插件生效后，`invokeAntigravityCli` 工具会自动汇聚到 Agent 的可用工具库（`Toolkit`）中。

当您在 Emailclaw 聊天界面中与 Agent 交互时，只需下发相关开发指令，Agent 即可自动决策并调用该工具，例如：
- *“请使用 invokeAntigravityCli 运行当前项目的单元测试并给出 JSON 报告”*
- *“调用 Antigravity CLI 分析当前工程代码质量”*
- *“帮我调用 agy 检查一下最新的代码差异”*

Agent 将自动在后台启动无头进程并获取结构化结果进行解析。

---

## 4. 插件配置说明

如果需要自定义 Antigravity CLI 的执行路径、默认超时时间或权限策略，可编辑配置文件。

### 4.1 配置文件位置
- **Linux / macOS**: `~/emailclaw/.config/plugins.json`（或 `tools.json` / `global-config.json`）
- **Windows**: `%USERPROFILE%\emailclaw\.config\plugins.json`

### 4.2 配置示例
在配置文件的对应条目中设置 `pluginConfig`：

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

### 4.3 字段说明
- `cli_path`：Antigravity CLI 可执行文件命令或路径（默认 `"agy"`；若未配置环境变量可填写绝对路径如 `"/usr/local/bin/agy"`）。
- `default_timeout`：默认超时上限（单位：秒，默认 `300`）。
- `dangerously_skip_permissions`：是否自动传递 `--dangerously-skip-permissions`（默认 `true`，无头自动化必备）。

---

## 5. Agent 工具调用契约（Tool Schema）

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `prompt` | String | **是** | - | 发送给 Antigravity CLI 的任务 Prompt，插件自动在末尾附加严格 JSON 指令。 |
| `working_directory` | String | 否 | 当前工程目录 / `.` | CLI 进程执行工作目录。 |
| `model` | String | 否 | `null` | 指定覆盖的大模型名称（如 `gemini-2.5-pro`）。 |
| `timeout_seconds` | Integer | 否 | `300` | 单次执行超时时间（秒）。 |
| `dangerously_skip_permissions` | Boolean | 否 | `true` | 是否跳过交互权限。 |
| `extra_args` | String | 否 | `null` | 附加 CLI 参数（如 `--verbose`）。 |

---

## 6. 交互示例

### Agent 调用入参：
```json
{
  "prompt": "运行测试用例并将测试报告整理为 JSON 格式",
  "working_directory": "/home/user/workspace/project-alpha",
  "model": "gemini-2.5-pro",
  "timeout_seconds": 120,
  "dangerously_skip_permissions": true
}
```

### 工具执行返回（原生 ToolResultBlock 格式）：

工具执行完毕后直接返回 AgentScope 原生 `ToolResultBlock`，避免二次 JSON 转义：
- **`state`**：`ToolResultState.SUCCESS`（执行成功）或 `ToolResultState.ERROR`（执行失败/超时/参数错误）。
- **`output`**：包含纯净结构化 JSON 内容的 `TextBlock`，供大模型直接理解：
  ```json
  {
    "test_suite": "user-service",
    "total": 42,
    "passed": 42,
    "failed": 0,
    "duration_ms": 1540
  }
  ```
- **`metadata`**：供系统监控与追踪审计的执行元数据：
  ```json
  {
    "exitCode": 0,
    "timedOut": false,
    "workingDirectory": "/home/user/workspace/project-alpha"
  }
  ```

