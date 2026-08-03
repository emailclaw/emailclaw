# DingTalk Plugin Compilation Instructions

When compiling to generate a deployable plugin JAR file to be copied into the `plugins` directory, please return to the parent directory first, verify that the `emailclaw` version in the `pom.xml` file in the current directory is correct (otherwise compilation will fail), and then execute:

```sh
cd ..
mvn -pl dingtalk -am package 
```

Do not directly execute `mvn clean compile` inside the `dingtalk` directory.

# DingTalk Plugin Configuration Instructions
This plugin provides a native configuration interface (UI). You can directly find DingTalk on the "Channels" page in system settings, click the `Configure` button for visual configuration. It also supports scanning a QR code to quickly and automatically fill in the AppKey and AppSecret.

Of course, if you are more accustomed to directly modifying the configuration file, you can also refer to the instructions below to edit it manually:


## 1. Configuration File Location

Please open `channels.json` in the user configuration directory:

- Linux / macOS: `~/emailclaw/.config/channels.json`
- Windows: `%USERPROFILE%\emailclaw\.config\channels.json`

Find or add the corresponding `ChannelInfo` entry for the DingTalk channel in this file, and then complete the `pluginConfig` field with the following configuration items.

## 2. Configuration Method

It is recommended to write it inside the `pluginConfig` object, like:

```json
{
  "pluginConfig": {
    "clientId": "...",
    "clientSecret": "..."
  }
}
```

## 3. Field Descriptions

- `clientId`
  - The AppKey or Client ID of the DingTalk application.
  - Required, used for the plugin to authenticate with DingTalk.

- `clientSecret`
  - The AppSecret or Client Secret of the DingTalk application.
  - Required, used with `clientId` to complete authentication.

- `showToolMessages`
  - Whether to display tool message content in DingTalk messages.
  - Boolean, `true` indicates display, `false` indicates hide.
  - Default value is `true`.

- `showThinking`
  - Whether to show the "Thinking" or processing status in DingTalk messages.
  - Boolean, `true` indicates show, `false` indicates do not show.
  - Default value is `true`.

- `messageType`
  - Sending format for normal messages.
  - Recommended value: `markdown`.
  - If you wish to use other DingTalk message formats, you can fill them in according to the DingTalk API requirements.
  - Default value is `markdown`.

- `cronMessageType`
  - Message format used for scheduled messages (cron pushes).
  - The recommended value is also `markdown`.
  - Default value is `markdown`.

- `atSenderOnReply`
  - When the plugin replies, whether to @ the reply initiator.
  - Boolean, `true` indicates @, `false` indicates do not @.
  - Default value is `false`.

- `dmPolicy`
  - Access policy for direct messages.
  - The current default value is `open`.
  - If there are no special requirements, it can be kept as `open`.

- `groupPolicy`
  - Access policy for group messages.
  - The current default value is `open`.
  - If there are no special requirements, it can be kept as `open`.

- `requireMention`
  - Whether it is required to be @'ed in group messages to trigger the plugin.
  - Boolean, `true` indicates @ is required, `false` indicates it is not required.
  - Default value is `false`.

- `allowlistUsers`
  - Whitelist user list.
  - Write a string array, where each value in the array is a user ID permitted to use this DingTalk channel.
  - If no restriction is needed, it can be omitted or set to an empty array `[]`.

## 4. Reference Example

Below is a common example of the DingTalk channel entry in `channels.json` for reference only:

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

> Note: `clientId` and `clientSecret` are required fields. The DingTalk channel will only be considered configured after both of these fields are set.
