# Emailclaw Plugin Developer Guide

This document is for third-party plugin developers and explains the Emailclaw plugin entry point, lifecycle, configuration, Channel plugin integration methods, and the ToolGuard approval integration conventions.

## 1. Plugin Entry Point

The plugin entry point class must implement `ai.emailclaw.emailclaw.plugin.EmailclawPlugin`, and it is recommended to inherit from more specific abstract base classes:

- `AbstractToolPlugin`: Register tools callable by the Agent.
- `AbstractProviderPlugin`: Register models or Provider adapters.
- `AbstractHookPlugin`: Register startup, shutdown, and other lifecycle hooks.
- `AbstractCommandPlugin`: Register commands callable by the host.
- `AbstractFrontendPlugin`: Register frontend entries and optional backend APIs.
- `AbstractChannelPlugin`: Integrate external session channels such as email, IM, Webhook, etc.
- `AbstractGeneralPlugin`: Use when it cannot be classified into the above types.

Third-party published plugins must declare `entry_point` in `plugin.json`, which is the fully qualified class name of the entry point class. `META-INF/services/ai.emailclaw.emailclaw.plugin.EmailclawPlugin` is only suitable for built-in plugins or development debugging, and is not recommended as the main path for third-party publishing.

```json
{
  "id": "my-channel",
  "name": "My Channel",
  "version": "1.0.0",
  "author": "Your Name",
  "type": "channel",
  "entry_point": "com.example.emailclaw.MyChannelPlugin"
}
```

## 2. Lifecycle

When the framework loads a plugin, it calls in the following order:

1. `register(PluginRegistry registry)`: Register capabilities like tools, commands, frontend entries, etc.
2. `initialize(PluginContext context)`: Inject host context to read services, configurations, and the ToolGuard gateway.
3. `start()`: Start runtime resources such as external connections, polling threads, Webhook clients, etc.
4. `stop()`: Stop runtime resources.
5. `destroy()`: Destroy the plugin, which calls `stop()` and `onUnload()` by default.

After inheriting `AbstractEmailclawPlugin`, developers usually only need to override `doInitialize()`, `start()`, `stop()`, or the methods required by specific abstract base classes. For logging, please use the `logger` provided by the base class uniformly, which is of type `java.util.logging.Logger`.

## 3. Configuration Schema

Plugins expose configuration items through `configSchema()`. When inheriting `AbstractEmailclawPlugin`, you should override `pluginConfigSchema()`; when inheriting `AbstractChannelPlugin`, you should override `channelConfigSchema()`.

```java
@Override
protected List<ConfigFieldDescriptor> channelConfigSchema() {
  return List.of(
      new ConfigFieldDescriptor(
          "webhookUrl",
          "Webhook URL",
          ConfigFieldDescriptor.FieldType.TEXT,
          true,
          "",
          "External message callback address",
          "connection"));
}
```

The order of `ConfigFieldDescriptor` fields is:

- `key`: Configuration key, written to `ChannelInfo.pluginConfig`.
- `label`: UI display name.
- `type`: Field type, e.g., `TEXT`, `PASSWORD`, `BOOLEAN`.
- `required`: Whether it is required.
- `defaultValue`: Default value.
- `description`: Explanatory text.
- `group`: Configuration group.

### 3.1 Custom Configuration Panel (UI)

In the latest Emailclaw architecture, the core plugin implementation (including the `EmailclawPlugin` interface, etc.) has been completely decoupled from the JavaFX interface (pure Headless). Plugins no longer support directly overriding methods containing UI logic such as `showConfigDialog`.

For plugins requiring complex configuration interfaces (such as QR code scanning, polling, Tab switching, etc.), the UI layer uses the **SPI extension mechanism (CustomConfigViewProvider)** for adaptation:

1. **Core Plugin Layer**: Only responsible for defining `configSchema()` and does not contain any JavaFX dependencies.
2. **UI Adaptation Layer**: In the `emailclaw-ui` frontend package, implement the `ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider` interface:

```java
public class MyPluginConfigViewProvider implements CustomConfigViewProvider {

    @Override
    public String targetPluginId() {
        return "my-channel"; // Corresponds to your plugin's id()
    }

    @Override
    public Node buildView(Map<String, Object> initialConfig, Consumer<Map<String, Object>> onSave, Runnable onCancel) {
        // Build pure JavaFX view nodes (e.g., VBox, Pane)
        // And pass the result back to the main system to save via onSave.accept(newConfig)
        // Notify the outside to close the current configuration popup via onCancel.run()
        return new MyPluginConfigPane(initialConfig, onSave, onCancel);
    }
}
```

3. **SPI Registration**: Declare your Provider in the `META-INF/services/ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider` file of the `emailclaw-ui` module. When the system renders the channel settings interface, it will automatically discover this Provider and pop up a dynamically assembled panel, achieving a complete physical isolation between bottom-level logic and top-level UI.

## 4. Channel Plugins

Channel plugins are used to convert external user messages into Emailclaw chat messages, and send the model's reply back to the original channel. Inheriting from `AbstractChannelPlugin` is recommended.

### 4.1 Basic Responsibilities

A Channel plugin usually needs to:

- Get the current channel configuration from `ChannelService`.
- Start external clients, Webhooks, pollers, or background threads in `start()`.
- After receiving external user messages, locate or create `ChatSessionInfo`.
- Call `ChatService.sendMessage(...)` and pass in the necessary routing information.
- Send the model's final reply back to the external user in `StreamCallback.onCompleted(...)`.

### 4.2 Session Routing

Non-console channels must maintain stable `sessionId`, `channel`, and `userId`:

```java
ChatSessionInfo session = chatService.newSession(agent.id);
session.sessionId = externalConversationId;
session.channel = id();
session.userId = externalUserId;
chatService.updateSession(session);
```

If an approval message requires a temporary callback address, it should be passed in via the `route` parameter of `ChatService.sendMessage(...)`:

```java
chatService.sendMessage(
    agent,
    provider,
    modelId,
    session,
    prompt,
    List.of(),
    Map.of("sessionWebhook", sessionWebhook),
    callback);
```

ToolGuard will copy these routing fields to `ToolGuardApprovalRequest.route()` for the plugin to use when sending approval messages.

## 5. ToolGuard Channel Approval

Emailclaw's ToolGuard creates approval requests before executing high-risk tools. The console still uses button approvals; non-console Channels require the plugin to send the approval content to the user, and pass it back to the core after the user enters the 4-digit approval code.

### 5.1 Implementing the Approval Channel

The Channel plugin needs to implement `ToolGuardApprovalChannel` and return itself in `toolGuardApprovalChannel()`:

```java
public class MyChannelPlugin extends AbstractChannelPlugin implements ToolGuardApprovalChannel {

  private MyChannelRunner runner;

  @Override
  public String id() {
    return "my-channel";
  }

  @Override
  public String channelId() {
    return id();
  }

  @Override
  public Optional<ToolGuardApprovalChannel> toolGuardApprovalChannel() {
    return Optional.of(this);
  }

  @Override
  public ToolGuardApproveResult userApprovalResult(ToolGuardApprovalInfo info) {
    return ToolGuardApproveResult.APPROVED;
  }
}
```

`ToolGuardApprovalRequest` contains read-only snapshots required for approval:

- `approvalId()`: Unique ID for the approval request.
- `approvalCode()`: The 4-digit code the user needs to enter.
- `agentId()`, `sessionId()`, `channelId()`, `userId()`: Approval routing context.
- `toolName()`, `toolInput()`: Tool and parameters to execute.
- `severity()`, `findings()`: Risk level and detection explanation.
- `timeoutSeconds()`: Timeout time; automatically rejects after timeout.
- `route()`: Custom routing fields for the Channel.

`deliverApproval(...)` only means "the approval message has been handed over to the external channel" and does not mean the user has approved. If it cannot be delivered, it should return `ToolGuardDeliveryResult.failed(reason)`; the core will reject this tool call according to the failure closure policy.

### 5.2 User Interaction Conventions

Currently, non-button Channels uniformly use the "only reply with the 4-digit approval code" interaction:

- The plugin sends an approval message, and the body must highlight the 4-digit `approvalCode`.
- The user directly replies with this 4-digit number to indicate approval.
- If the user doesn't reply, replies with a wrong number, replies across sessions, or replies late, it won't be approved.
- Rejection operations don't require an extra command; a timeout is a rejection.

After receiving a user's message, the Channel Runner should first determine if it's an approval code and complete the matching in a local `ConcurrentHashMap`:

```java
// Check if there are pending approval requests
String pendingKey = session.sessionId + ":" + senderId;
PendingApprovalRequest pendingRequest = pendingApprovals.get(pendingKey);

if (pendingRequest != null) {
    String replyContent = content.trim();
    if (replyContent.equals(pendingRequest.code())) {
        // Approval code matches -> Approved
        pendingRequest.future().complete(ToolGuardApproveResult.APPROVED);
    } else {
        // Approval code does not match (or replied with something else) -> Rejected
        pendingRequest.future().complete(ToolGuardApproveResult.REJECTED);
    }
    // Send approval confirmation message to user...
    return;
}
```

Note that the approval must notify the core engine to continue or terminate the tool call by completing the `CompletableFuture` internally in the Runner. The approval code message must be intercepted before entering the large model and cannot be passed on to `ChatService.sendMessage(...)`, otherwise it will pollute the normal dialogue context. For a more detailed code structure, please directly refer to Section 10.

### 5.3 Suggested Format for Approval Messages

The approval message contains at least:

- Approval code: 4-digit number, placed prominently.
- Operation instructions: Reply directly with the 4-digit approval code to indicate consent to execute.
- Tool name and parameter summary.
- Risk level and risk explanation.
- Timeout time.

Example:

```text
Emailclaw detected a high-risk tool call and requires your approval.

Approval code: 1234
Please reply directly with the 4-digit approval code to indicate consent to execute; failing to reply will result in a timeout rejection.

Tool: execute
Risk level: High
Remaining time: 300 seconds
Parameters: {cmd=rm -rf /tmp/demo}
Risk explanation:
- The command contains a delete operation
```

## 6. PluginRegistry Capability Registration

The specific abstract base class will call `PluginRegistry` for the developer:

- `AbstractToolPlugin` calls `registerTool(...)`.
- `AbstractProviderPlugin` calls `registerProvider(...)`.
- `AbstractHookPlugin` calls `registerStartupHook(...)` and `registerShutdownHook(...)`.
- `AbstractCommandPlugin` calls `registerCommand(...)`.
- `AbstractFrontendPlugin` calls `registerFrontendEntry(...)` and optionally calls `registerCustomApi(...)`.
- `AbstractChannelPlugin` doesn't register tool capabilities by default, only participating in lifecycle and Channel configuration management.

Only manually register extra capabilities in `onRegister(...)` when it's genuinely necessary to combine multiple capabilities.

## 7. Publishing and Building

Recommended publishing formats:

- Single JAR: Put `plugin.json` in the root directory of the JAR or `META-INF/`.
- ZIP package: Contains the plugin JAR, `plugin.json`, and optional frontend resources simultaneously.

You can use the sample reactor of this repository during development:

```bash
mvn -q -DskipTests compile
```

When a third-party plugin depends on Emailclaw, it usually uses the `provided` scope because the core APIs are provided by the Emailclaw host at runtime.

## 8. Engineering Standards

- Use `java.util.logging.Logger`, do not introduce other logging frameworks.
- External connections, threads, HTTP clients, and other resources must be released in `stop()`.
- Channel plugins should get the latest `ChannelInfo` through `ChannelService` when reading configurations, don't read or write configuration files directly.
- ToolGuard configuration is off by default; plugins must handle delivery failures and return explicit errors.
- User approval codes must be bound to the same `channelId + sessionId + userId`, do not just match globally by the 4-digit code.
- Do not forward the approval code message to the large model.

## 9. Example Module

`plugin-samples/invokeAntigravityCli` is a Tool plugin sample module, showing:

- The `entry_point` configuration of `plugin.json`.
- The basic inheritance method of `AbstractToolPlugin`.
- Using `@Tool` and `@ToolParam` to define structured tools for LLMs.
- Returning `ToolResultBlock` with structured JSON output and metadata.
- Using `AntigravityProcessRunner` to safely execute background processes.

When implementing new Email, IM, or Webhook Channels, you can prioritize referring to the built-in `EmailclawPlugin`, `DingTalkPlugin`, and the ToolGuard approval code processing flow in their corresponding Runners.

## 10. ToolGuard Approval Flow Implementation Example

### 10.1 DingTalk Plugin Implementation

The DingTalk plugin implements the `ToolGuardApprovalChannel` interface and provides a complete ToolGuard approval flow:

```java
public class DingTalkPlugin extends AbstractChannelPlugin 
    implements ToolGuardApprovalChannel {

    private DingTalkRunner runner;

    @Override
    public Optional<ToolGuardApprovalChannel> toolGuardApprovalChannel() {
        return Optional.of(this);
    }

    @Override
    public ToolGuardApproveResult userApprovalResult(ToolGuardApprovalInfo info) {
        if (runner == null) {
            LOGGER.warning("DingTalk ToolGuard approval failed: DingTalkRunner is not started");
            return ToolGuardApproveResult.REJECTED;
        }
        return runner.userApprovalResult(info);
    }
}
```

### 10.2 Runner Approval Flow Implementation

The Runner class needs to implement the following core methods:

#### 10.2.1 Processing Approval Requests

```java
public ToolGuardApproveResult userApprovalResult(ToolGuardApprovalInfo info) {
    String sessionKey = info.conversation().sessionId() + ":" + info.conversation().userId();
    String code = generateApprovalCode();

    // 1. Send the approval message
    ToolGuardApproveResult messageResult = sendApprovalMessage(info, code);
    if (messageResult != null) {
        return messageResult;
    }

    // 2. Create a Future and block waiting for user's reply
    CompletableFuture<ToolGuardApproveResult> future = new CompletableFuture<>();
    pendingApprovals.put(sessionKey, new PendingApprovalRequest(code, future));

    try {
        return future.get(info.timeoutSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        return ToolGuardApproveResult.TIMEOUT;
    } catch (Exception e) {
        return ToolGuardApproveResult.REJECTED;
    } finally {
        pendingApprovals.remove(sessionKey);
    }
}
```

#### 10.2.2 Approval Code Generation

```java
private String generateApprovalCode() {
    return String.format("%04d", new java.util.Random().nextInt(10_000));
}
```

#### 10.2.3 Constructing Approval Messages

```java
private String buildToolGuardApprovalMessage(ToolGuardApprovalInfo info, String code) {
    StringBuilder body = new StringBuilder();
    body.append("Emailclaw detected a high-risk tool call and requires your approval.\n\n");
    body.append("Approval code: ").append(code).append('\n');
    body.append("How to reply: Please reply directly with the 4-digit approval code to indicate consent to execute; replying with anything else indicates rejection; or failing to reply will result in a timeout rejection.\n\n");
    body.append("Tool: ").append(info.toolName()).append('\n');
    body.append("Risk level: ")
            .append(info.severity() == null ? "" : info.severity().getDisplayName())
            .append('\n');
    body.append("Remaining time: ").append(info.timeoutSeconds()).append(" seconds\n");
    body.append("Parameters: ").append(compactApprovalText(String.valueOf(info.toolInput()), 1200)).append("\n\n");
    if (info.findings() != null && !info.findings().isEmpty()) {
        body.append("Risk explanation:\n");
        info.findings().forEach(finding ->
                body.append("- ")
                        .append(finding == null ? "" : finding.description)
                        .append('\n'));
    }
    return body.toString();
}
```

### 10.3 Detecting Approval Code Replies

When processing user messages, it's necessary to prioritize detecting approval code replies:

```java
private void handleMessage(String request, ChannelInfo channelInfo) {
    // ... parse message ...
    
    ThreadUtils.run(() -> {
        // Find or create session
        ChatSessionInfo session = findOrCreateSession(agent, conversationId, senderId);
        
        // ToolGuard approval code reply detection
        String pendingKey = session.sessionId + ":" + senderId;
        PendingApprovalRequest pendingRequest = pendingApprovals.get(pendingKey);
        if (pendingRequest != null) {
            String replyContent = content.trim();
            handleApprovalReply(pendingKey, replyContent);
            // Send approval result confirmation message
            sendApprovalConfirmation(sessionWebhook, replyContent, pendingRequest.code());
            return;
        }
        
        // Normal message processing flow
        chatService.sendMessage(...);
    });
}
```

### 10.4 Approval Code Verification

```java
private void handleApprovalReply(String sessionKey, String replyContent) {
    PendingApprovalRequest pendingRequest = pendingApprovals.get(sessionKey);
    if (pendingRequest != null) {
        if (replyContent.equals(pendingRequest.code())) {
            // Approval code matches -> Approved
            pendingRequest.future().complete(ToolGuardApproveResult.APPROVED);
        } else {
            // Approval code doesn't match -> Rejected
            pendingRequest.future().complete(ToolGuardApproveResult.REJECTED);
        }
    }
}
```

### 10.5 Data Structures

```java
// Pending approval requests: key = sessionId + ":" + userId
private final ConcurrentHashMap<String, PendingApprovalRequest> pendingApprovals 
    = new ConcurrentHashMap<>();

// Internal record for pending approval requests
private record PendingApprovalRequest(
    String code, 
    CompletableFuture<ToolGuardApproveResult> future
) {}
```

### 10.6 Key Considerations

1. **Approval code binding**: Must be bound to `channelId + sessionId + userId`, and cannot just be matched globally by the 4-digit code.
2. **Message interception**: Approval code messages must be intercepted before entering the large model, and cannot be passed to `ChatService.sendMessage(...)`.
3. **Timeout handling**: Must handle timeouts; automatically rejects after a timeout.
4. **Confirmation message**: After receiving a user's reply, a confirmation message should be sent to inform them of the approval result.
5. **Resource cleanup**: After the approval is complete, the record must be removed from `pendingApprovals`.

### 10.7 Comparison with Emailclaw

| Feature | Emailclaw | DingTalk |
|---------|-----------|----------|
| Approval Code Generation | 4-digit random number | 4-digit random number |
| Message Sending | Email (IMAP/SMTP) | Bot message (Webhook) |
| User Reply Detection | Email polling | Message callback |
| Timeout Mechanism | CompletableFuture.get() | CompletableFuture.get() |
| Approval Confirmation | Reply email | Send confirmation message |

The core logic of the two implementations is the same, and the main difference lies in how messages are sent and received.

## 11. Packaging, Deployment & Usage Guide

### 11.1 Compilation & Packaging Mechanism

Third-party and sample plugins are typically developed as standalone modules. In a multi-module Maven Reactor project, to correctly resolve local `emailclaw` host dependencies, execute targeted packaging commands from the project root:

```sh
# Example: Package Tool plugin
mvn -pl invokeAntigravityCli -am package

# Example: Package Channel plugin
mvn -pl dingtalk -am package
```

Upon completion, each submodule's `target/` directory will contain a standalone shaded JAR with bundled third-party dependencies:
- `invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar`
- `dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar`

### 11.2 Deploying to Emailclaw

The default external plugin directory in Emailclaw is:
- **Linux / macOS**: `~/emailclaw/plugins/`
- **Windows**: `%USERPROFILE%\emailclaw\plugins\`

Copy the built JAR files directly into the plugins directory:

```sh
# Deploy Tool plugin
cp invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar ~/emailclaw/plugins/

# Deploy Channel plugin
cp dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar ~/emailclaw/plugins/
```

### 11.3 Dynamic Hot-Reloading & Verification

Emailclaw provides out-of-the-box plugin lifecycle management and hot-reloading:
1. **Runtime Hot-Loading**: A background daemon thread scans `~/emailclaw/plugins/` every 5 seconds. When a new `.jar` is detected, it automatically isolates the ClassLoader, parses `plugin.json`, instantiates the entry point, and invokes `register()` and `initialize()`.
2. **Startup Assembly**: Whenever Emailclaw starts up, `PluginManager` automatically scans `~/emailclaw/plugins/` and loads all available plugins.
3. **Log Verification**: When successfully loaded, the console/log will display:
   ```
   INFO: Discovered external plugin: emailclaw-plugin-tool-invokeAntigravityCli
   INFO: Successfully registered Tool plugin: invokeAntigravityCli
   INFO: InvokeAntigravityCliPlugin started
   ```

### 11.4 Using Plugins in Emailclaw

1. **Channel Plugins (e.g. DingTalk)**:
   - Go to System Settings -> "Channels" page in the UI.
   - The new channel plugin will appear. Click `Configure` to set credentials (`clientId` / `clientSecret`).
   - Toggle the channel to "Enabled" to allow users to interact with AI Agents via DingTalk.
2. **Tool Plugins (e.g. invokeAntigravityCli)**:
   - Once loaded, the tool is dynamically added to the Agent's `Toolkit`.
   - In chat sessions, instructing the Agent with tasks (e.g. *"Use invokeAntigravityCli to run unit tests and output a JSON report"*) will prompt the LLM to autonomously trigger the tool in the background.

