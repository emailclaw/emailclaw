# DingTalk Plugin Usage & Deployment Guide

The `dingtalk` plugin is Emailclaw's sample **Channel Plugin**, providing bidirectional integration between DingTalk enterprise internal bot messaging and Emailclaw AI Agents.

---

## 1. Compilation & Packaging

To compile and produce the deployable plugin JAR file, navigate to the `plugin-samples` root directory and execute the Maven Reactor build command:

```sh
# Navigate to the plugin-samples root
cd plugin-samples

# Build and package dingtalk using Maven Reactor
mvn -pl dingtalk -am package
```

> **Important**: Do not execute `mvn clean compile` directly inside the `dingtalk` directory without the parent reactor context, as local artifact resolution requires the reactor.

After packaging succeeds, the standalone shaded plugin JAR (containing necessary dependencies like `dingtalk-stream`) will be generated in `dingtalk/target/`:
- **Artifact Path**: `dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar`

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
cp dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar ~/emailclaw/plugins/

# Windows (PowerShell)
Copy-Item dingtalk\target\emailclaw-plugin-channel-dingtalk-1.0.0.jar $HOME\emailclaw\plugins\
```

### Step 3: Verify Activation
- **Dynamic Hot-Loading (No Restart Required)**: Emailclaw's background directory watcher (scanning every 5 seconds) will automatically detect the new JAR, instantiate the channel plugin, and start listening. The console/log will report:
  ```
  INFO: Discovered external plugin: emailclaw-plugin-channel-dingtalk
  INFO: Channel plugin started: dingtalk
  ```
- **Application Startup Loading**: If Emailclaw is not currently running, `PluginManager` will automatically scan `~/emailclaw/plugins/` and load the plugin during system bootstrap.

---

## 3. How to Configure & Use DingTalk in Emailclaw

### Method 1: Visual Configuration via UI (Recommended)
1. Launch Emailclaw and open the "Channels" page in System Settings.
2. Locate the "DingTalk" entry.
3. Click `Configure` to open the configuration panel and input your DingTalk application's `AppKey` (`clientId`) and `AppSecret` (`clientSecret`), with QR code scanning auto-fill support.
4. Toggle the status switch to "Enabled". The bot will immediately connect in the background.

### Method 2: Manual Configuration via JSON File
Alternatively, edit `channels.json` in the user configuration directory:
- **Linux / macOS**: `~/emailclaw/.config/channels.json`
- **Windows**: `%USERPROFILE%\emailclaw\.config\channels.json`

Add or update the `dingtalk` entry:

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

## 4. Field Descriptions

- `clientId`: DingTalk application AppKey / Client ID (Required).
- `clientSecret`: DingTalk application AppSecret / Client Secret (Required).
- `showToolMessages`: Whether to show tool execution progress in DingTalk messages (Boolean, default `true`).
- `showThinking`: Whether to show reasoning/thinking state in messages (Boolean, default `true`).
- `messageType`: Format for standard messages (default `"markdown"`).
- `cronMessageType`: Format for scheduled messages (default `"markdown"`).
- `atSenderOnReply`: Whether to @ the user when replying (Boolean, default `false`).
- `dmPolicy`: Policy for direct messages (default `"open"`).
- `groupPolicy`: Policy for group chats (default `"open"`).
- `requireMention`: Whether @mention is required in group chats (Boolean, default `false`).
- `allowlistUsers`: Array of user IDs permitted to interact with the bot (default `[]` for unrestricted).
