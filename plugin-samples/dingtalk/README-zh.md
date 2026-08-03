
# DingTalk 插件编译说明

编译生成可部署的插件 JAR文件用于复制到 `plugins` 目录时，请先回到上级目录，确认当前目录中pom.xml文件中emailclaw版本正确（否则编译报错），然后执行：

```sh
cd ..
mvn -pl dingtalk -am package 
```

不要直接在 `dingtalk` 目录内执行 `mvn clean compile`

# DingTalk 插件配置说明
本插件已提供原生配置界面（UI），您可以直接在系统设置的「渠道配置（Channels）」页面找到 DingTalk，点击 `Configure` 按钮进行可视化配置，同时还支持扫描二维码快速自动填充 AppKey 和 AppSecret。

当然，如果您更习惯直接修改配置文件，也可参考下方说明手工编辑：


## 1. 配置文件位置

请打开用户配置目录下的 `channels.json`：

- Linux / macOS: `~/emailclaw/.config/channels.json`
- Windows: `%USERPROFILE%\\emailclaw\\.config\\channels.json`

在该文件中找到或新增钉钉渠道对应的 `ChannelInfo` 条目，然后将 `pluginConfig` 字段补充为以下配置项。

## 2. 配置方式

建议写入 `pluginConfig` 对象内，如：

```json
{
  "pluginConfig": {
    "clientId": "...",
    "clientSecret": "..."
  }
}
```

## 3. 字段说明

- `clientId`
  - 钉钉应用的 AppKey 或客户 ID。
  - 必填，用于插件向钉钉鉴权。

- `clientSecret`
  - 钉钉应用的 AppSecret 或客户密钥。
  - 必填，与 `clientId` 一起完成鉴权。

- `showToolMessages`
  - 是否在钉钉消息中显示工具消息内容。
  - 布尔值，`true` 表示展示，`false` 表示隐藏。
  - 默认值为 `true`。

- `showThinking`
  - 是否在钉钉消息中显示“思考中”或处理中状态。
  - 布尔值，`true` 表示显示，`false` 表示不显示。
  - 默认值为 `true`。

- `messageType`
  - 普通消息的发送格式。
  - 推荐值：`markdown`。
  - 如果你希望使用其他钉钉消息格式，可按钉钉 API 要求填写。
  - 默认值为 `markdown`。

- `cronMessageType`
  - 定时消息（cron 推送）使用的消息格式。
  - 同样推荐值为 `markdown`。
  - 默认值为 `markdown`。

- `atSenderOnReply`
  - 当插件回复时，是否 @ 回复发起者。
  - 布尔值，`true` 表示 @，`false` 表示不 @。
  - 默认值为 `false`。

- `dmPolicy`
  - 私聊消息的访问策略。
  - 目前默认值为 `open`。
  - 如果没有特殊要求，可保持 `open`。

- `groupPolicy`
  - 群聊消息的访问策略。
  - 目前默认值为 `open`。
  - 如果没有特殊要求，可保持 `open`。

- `requireMention`
  - 是否要求群聊消息中必须被 @ 才会触发插件。
  - 布尔值，`true` 表示需要 @，`false` 表示不需要。
  - 默认值为 `false`。

- `allowlistUsers`
  - 白名单用户列表。
  - 写入字符串数组，数组内每个值为允许使用该钉钉渠道的用户 ID。
  - 如果不需要限制，可省略或置为空数组 `[]`。

## 4. 参考示例

下面给出一个常见的 `channels.json` 中钉钉渠道条目示例，仅供参考：

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
    "allowlistUsers": [
      "user1",
      "user2"
    ]
  }
}
```

> 注意：`clientId` 和 `clientSecret` 为必填项，只有同时配置这两个字段后，钉钉渠道才会被视为已配置。
