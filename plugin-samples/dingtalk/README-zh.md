# DingTalk 插件使用与部署指南

`dingtalk` 插件是 Emailclaw 的官方第三方**渠道插件（Channel Plugin）**示例，用于将钉钉企业内部机器人消息与 Emailclaw 的 AI Agent 进行双向集成。

---

## 1. 编译与打包

在生成可部署的插件 JAR 文件前，请先回到 `plugin-samples` 根目录，执行 Maven Reactor 多模块打包命令：

```sh
# 进入 plugin-samples 目录
cd plugin-samples

# 使用 Reactor 模式编译并打包 dingtalk 模块
mvn -pl dingtalk -am package
```

> **注意**：请勿直接在 `dingtalk` 目录内单独执行 `mvn clean compile`（缺少父级 Reactor 本地解析依赖会导致编译失败）。

打包成功后，将在 `dingtalk/target/` 目录下生成包含必要依赖（如 dingtalk-stream）的独立 Shaded 插件包：
- **产物路径**：`dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar`

---

## 2. 部署与生效步骤

Emailclaw 具备外部插件的自动扫描与热加载机制，部署步骤如下：

### 步骤 1：确保插件目录存在
Emailclaw 默认的外部插件加载目录为：
- **Linux / macOS**：`~/emailclaw/plugins/`
- **Windows**：`%USERPROFILE%\emailclaw\plugins\`

如果目录不存在，可先手动创建：
```sh
mkdir -p ~/emailclaw/plugins
```

### 步骤 2：拷贝生成的 JAR 文件
将编译好的插件 JAR 文件拷贝到 `plugins` 目录下：

```sh
# Linux / macOS
cp dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item dingtalk\target\emailclaw-plugin-channel-dingtalk-1.0.0.jar $HOME\emailclaw\plugins\
```

### 步骤 3：验证插件生效
- **动态热加载（无需重启）**：Emailclaw 内置后台目录监听器（默认每 5 秒扫描一次），检测到新放入的 JAR 文件后会自动加载，日志将输出：
  ```
  INFO: Discovered external plugin: emailclaw-plugin-channel-dingtalk
  INFO: Channel plugin started: dingtalk
  ```
- **随系统启动载入**：若 Emailclaw 处于未启动状态，启动应用时 `PluginManager` 会自动扫描 `~/emailclaw/plugins/` 并完成装载。

---

## 3. 在 Emailclaw 中配置与使用 DingTalk 渠道

### 方式一：可视化界面配置（推荐）
1. 打开 Emailclaw 客户端，进入系统设置中的「渠道配置（Channels）」页面。
2. 列表中将展示「DingTalk」渠道项。
3. 点击 `Configure`（配置）按钮，在弹出面板中填入钉钉应用的 `AppKey`（`clientId`）和 `AppSecret`（`clientSecret`），支持扫描二维码快速自动填充。
4. 将渠道状态切换为「已启用（Enabled）」，钉钉机器人即刻开始在后台监听并响应用户消息。

### 方式二：手动编辑配置文件
也可以直接编辑用户配置目录下的 `channels.json`：
- **Linux / macOS**: `~/emailclaw/.config/channels.json`
- **Windows**: `%USERPROFILE%\emailclaw\.config\channels.json`

在 `channels.json` 中添加或补充 `dingtalk` 条目：

```json
{
  "id": "dingtalk",
  "name": "DingTalk",
  "enabled": true,
  "builtIn": false,
  "pluginConfig": {
    "clientId": "your-dingtalk-app-key",
    "clientSecret": "your-dingtalk-app-secret",
    "showToolMessages": true,
    "showThinking": true,
    "messageType": "markdown",
    "cronMessageType": "markdown",
    "atSenderOnReply": false,
    "dmPolicy": "open",
    "groupPolicy": "open",
    "requireMention": false,
    "allowlistUsers": []
  }
}
```

---

## 4. 字段说明

- `clientId`：钉钉应用的 AppKey / Client ID（必填）。
- `clientSecret`：钉钉应用的 AppSecret / Client Secret（必填）。
- `showToolMessages`：是否在钉钉消息中展示工具调用过程（布尔值，默认 `true`）。
- `showThinking`：是否展示思考中状态（布尔值，默认 `true`）。
- `messageType`：消息发送格式（默认 `"markdown"`）。
- `cronMessageType`：定时推送格式（默认 `"markdown"`）。
- `atSenderOnReply`：回复时是否 @ 发送者（布尔值，默认 `false`）。
- `dmPolicy`：私聊访问策略（默认 `"open"`）。
- `groupPolicy`：群聊访问策略（默认 `"open"`）。
- `requireMention`：群聊中是否必须 @ 机器人（布尔值，默认 `false`）。
- `allowlistUsers`：允许使用该渠道的用户 ID 白名单列表（字符串数组，空数组 `[]` 表示不限制）。
