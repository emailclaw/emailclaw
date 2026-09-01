# Emailclaw 插件开发指南

本文面向第三方插件开发者，说明 Emailclaw 插件的入口、生命周期、配置、Channel 插件接入方式，以及 ToolGuard 审批能力的集成约定。

## 1. 插件入口

插件入口类必须实现 `ai.emailclaw.emailclaw.plugin.EmailclawPlugin`，推荐继承更具体的抽象基类：

- `AbstractToolPlugin`：注册 Agent 可调用工具。
- `AbstractProviderPlugin`：注册模型或 Provider 适配器。
- `AbstractHookPlugin`：注册启动、关闭等生命周期钩子。
- `AbstractCommandPlugin`：注册宿主可调用命令。
- `AbstractFrontendPlugin`：注册前端入口和可选后端 API。
- `AbstractChannelPlugin`：接入邮件、IM、Webhook 等外部会话渠道。
- `AbstractGeneralPlugin`：无法归类到以上类型时使用。

第三方发布插件必须在 `plugin.json` 中声明 `entry_point`，该值是入口类的全限定类名。`META-INF/services/ai.emailclaw.emailclaw.plugin.EmailclawPlugin` 只适合内置插件或开发调试，不建议作为第三方发布主路径。

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

## 2. 生命周期

框架加载插件时会按以下顺序调用：

1. `register(PluginRegistry registry)`：注册工具、命令、前端入口等能力。
2. `initialize(PluginContext context)`：注入宿主上下文，可读取服务、配置和 ToolGuard 网关。
3. `start()`：启动外部连接、轮询线程、Webhook 客户端等运行时资源。
4. `stop()`：停止运行时资源。
5. `destroy()`：销毁插件，默认会调用 `stop()` 和 `onUnload()`。

继承 `AbstractEmailclawPlugin` 后，开发者通常只需要重写 `doInitialize()`、`start()`、`stop()` 或具体抽象基类要求的方法。日志请统一使用基类提供的 `logger`，其类型为 `java.util.logging.Logger`。

## 3. 配置 Schema

插件通过 `configSchema()` 暴露配置项。继承 `AbstractEmailclawPlugin` 时，应重写 `pluginConfigSchema()`；继承 `AbstractChannelPlugin` 时，应重写 `channelConfigSchema()`。

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
          "外部消息回调地址",
          "connection"));
}
```

`ConfigFieldDescriptor` 字段顺序为：

- `key`：配置键，写入 `ChannelInfo.pluginConfig`。
- `label`：UI 展示名称。
- `type`：字段类型，例如 `TEXT`、`PASSWORD`、`BOOLEAN`。
- `required`：是否必填。
- `defaultValue`：默认值。
- `description`：说明文本。
- `group`：配置分组。

### 3.1 自定义配置面板 (UI)

在最新的 Emailclaw 架构中，插件核心实现（包含 `EmailclawPlugin` 接口等）已经完全与 JavaFX 界面解耦（纯 Headless）。插件不再支持直接覆盖 `showConfigDialog` 等包含 UI 逻辑的方法。

对于需要复杂配置界面（例如扫码、轮询、Tab 切换等）的插件，UI 层采用 **SPI 扩展机制（CustomConfigViewProvider）** 进行适配：

1. **核心插件层**：只负责定义 `configSchema()`，不包含任何 JavaFX 依赖。
2. **UI 适配层**：在 `emailclaw-ui` 前端包内，实现 `ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider` 接口：

```java
public class MyPluginConfigViewProvider implements CustomConfigViewProvider {

    @Override
    public String targetPluginId() {
        return "my-channel"; // 对应你插件的 id()
    }

    @Override
    public Node buildView(Map<String, Object> initialConfig, Consumer<Map<String, Object>> onSave, Runnable onCancel) {
        // 构建出纯粹的 JavaFX 视图节点 (如 VBox, Pane)
        // 并通过 onSave.accept(newConfig) 把结果回调给主系统保存
        // 通过 onCancel.run() 来通知外部关闭当前配置弹窗
        return new MyPluginConfigPane(initialConfig, onSave, onCancel);
    }
}
```

3. **SPI 注册**：在 `emailclaw-ui` 模块的 `META-INF/services/ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider` 文件中声明你的 Provider 即可。当系统渲染渠道设置界面时，会自动发现该 Provider 并弹出动态装配的面板，彻底实现底层逻辑与顶层 UI 的物理隔离。

## 4. Channel 插件

Channel 插件用于把外部用户消息转成 Emailclaw 会话消息，并把模型回复发送回原通道。推荐继承 `AbstractChannelPlugin`。

### 4.1 基本职责

Channel 插件通常需要完成：

- 从 `ChannelService` 获取当前渠道配置。
- 在 `start()` 中启动外部客户端、Webhook、轮询器或后台线程。
- 收到外部用户消息后，定位或创建 `ChatSessionInfo`。
- 调用 `ChatService.sendMessage(...)`，并传入必要的路由信息。
- 在 `StreamCallback.onCompleted(...)` 中把模型最终回复发回外部用户。

### 4.2 会话路由

非 console 渠道必须维护稳定的 `sessionId`、`channel` 和 `userId`：

```java
ChatSessionInfo session = chatService.newSession(agent.id);
session.sessionId = externalConversationId;
session.channel = id();
session.userId = externalUserId;
chatService.updateSession(session);
```

如果审批消息需要临时回调地址，应通过 `ChatService.sendMessage(...)` 的 `route` 参数传入：

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

ToolGuard 会把这些路由字段复制到 `ToolGuardApprovalRequest.route()`，供插件发送审批消息使用。

## 5. ToolGuard Channel 审批

Emailclaw 的 ToolGuard 会在高风险工具执行前创建审批请求。console 仍使用按钮审批；非 console Channel 需要插件把审批内容发送给用户，并在用户输入 4 位数字审批码后回传给核心。

### 5.1 实现审批通道

Channel 插件需要实现 `ToolGuardApprovalChannel`，并在 `toolGuardApprovalChannel()` 中返回自身：

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

`ToolGuardApprovalRequest` 包含审批所需的只读快照：

- `approvalId()`：审批请求唯一 ID。
- `approvalCode()`：用户需要输入的 4 位数字码。
- `agentId()`、`sessionId()`、`channelId()`、`userId()`：审批路由上下文。
- `toolName()`、`toolInput()`：待执行工具与参数。
- `severity()`、`findings()`：风险等级和检测说明。
- `timeoutSeconds()`：超时时间；超时后自动拒绝。
- `route()`：Channel 自定义路由字段。

`deliverApproval(...)` 只表示“审批消息已交给外部通道”，不表示用户已批准。若无法送达，应返回 `ToolGuardDeliveryResult.failed(reason)`；核心会按失败关闭策略拒绝本次工具调用。

### 5.2 用户交互约定

当前非按钮 Channel 统一使用“只回复 4 位审批码”的交互：

- 插件发送审批消息，正文必须突出显示 4 位 `approvalCode`。
- 用户直接回复该 4 位数字，即表示批准。
- 用户不回复、回复错误数字、跨会话回复或超时回复，都不会批准。
- 拒绝操作不需要额外命令；超时即拒绝。

收到用户消息后，Channel Runner 应先判断是否为审批码，并在本地 `ConcurrentHashMap` 中完成匹配：

```java
// 检查是否有待审批的请求
String pendingKey = session.sessionId + ":" + senderId;
PendingApprovalRequest pendingRequest = pendingApprovals.get(pendingKey);

if (pendingRequest != null) {
    String replyContent = content.trim();
    if (replyContent.equals(pendingRequest.code())) {
        // 审批码匹配 → 批准
        pendingRequest.future().complete(ToolGuardApproveResult.APPROVED);
    } else {
        // 审批码不匹配（或回复其他内容） → 拒绝
        pendingRequest.future().complete(ToolGuardApproveResult.REJECTED);
    }
    // 发送审批确认消息给用户...
    return;
}
```

注意，审批必须在 Runner 内部通过完成 `CompletableFuture` 来通知核心引擎继续或终止工具调用。审批码消息必须在进入大模型前被截获，不能继续传给 `ChatService.sendMessage(...)`，否则会污染普通对话上下文。有关更详细的代码结构，请直接参考第 10 节。

### 5.3 审批消息建议格式

审批消息至少包含：

- 审批码：4 位数字，放在醒目位置。
- 操作说明：直接回复 4 位审批码表示同意执行。
- 工具名称和参数摘要。
- 风险等级和风险说明。
- 超时时间。

示例：

```text
Emailclaw 检测到高风险工具调用，需要你审批。

审批码：1234
请直接回复 4 位审批码，表示同意执行；不回复将超时拒绝。

工具：execute
风险等级：高
剩余时间：300 秒
参数：{cmd=rm -rf /tmp/demo}
风险说明：
- 命令包含删除操作
```

## 6. PluginRegistry 能力注册

具体抽象基类会替开发者调用 `PluginRegistry`：

- `AbstractToolPlugin` 调用 `registerTool(...)`。
- `AbstractProviderPlugin` 调用 `registerProvider(...)`。
- `AbstractHookPlugin` 调用 `registerStartupHook(...)` 和 `registerShutdownHook(...)`。
- `AbstractCommandPlugin` 调用 `registerCommand(...)`。
- `AbstractFrontendPlugin` 调用 `registerFrontendEntry(...)`，并可选调用 `registerCustomApi(...)`。
- `AbstractChannelPlugin` 默认不注册工具能力，只参与生命周期和 Channel 配置管理。

只有确实需要组合多种能力时，才在 `onRegister(...)` 中手动注册额外能力。

## 7. 发布与构建

推荐发布形态：

- 单 JAR：把 `plugin.json` 放入 JAR 根目录或 `META-INF/`。
- ZIP 包：同时包含插件 JAR、`plugin.json` 和可选前端资源。

开发期可以使用本仓库的 sample reactor：

```bash
mvn -q -DskipTests compile
```

第三方插件依赖 Emailclaw 时通常使用 `provided` scope，因为运行时由 Emailclaw 宿主提供核心 API。

## 8. 工程规范

- 使用 `java.util.logging.Logger`，不要引入其它日志框架。
- 外部连接、线程、HTTP 客户端等资源必须在 `stop()` 中释放。
- Channel 插件读取配置时应通过 `ChannelService` 获取最新 `ChannelInfo`，不要直接读写配置文件。
- ToolGuard 配置默认关闭；插件必须处理送达失败并返回明确错误。
- 用户审批码必须绑定同一 `channelId + sessionId + userId`，不要只按 4 位码全局匹配。
- 不要把审批码消息转发给大模型。

## 9. 示例模块

`plugin-samples/invokeAntigravityCli` 是 Tool 插件样例模块，展示：

- `plugin.json` 的 `entry_point` 配置。
- `AbstractToolPlugin` 的基本继承方式。
- 使用 `@Tool` 与 `@ToolParam` 定义大模型结构化工具。
- 返回带有结构化 JSON 输出和元数据的 `ToolResultBlock`。
- 使用 `AntigravityProcessRunner` 安全地执行后台进程。

实现新的 Email、IM 或 Webhook Channel 时，可优先参考内置 `EmailclawPlugin`、`DingTalkPlugin` 以及对应 Runner 中的 ToolGuard 审批码处理流程。

## 10. ToolGuard 审批流程实现示例

### 10.1 DingTalk Plugin 实现

DingTalk 插件实现了 `ToolGuardApprovalChannel` 接口，提供了完整的 ToolGuard 审批流程：

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
            LOGGER.warning("DingTalk ToolGuard 审批失败: DingTalkRunner 未启动");
            return ToolGuardApproveResult.REJECTED;
        }
        return runner.userApprovalResult(info);
    }
}
```

### 10.2 Runner 审批流程实现

Runner 类需要实现以下核心方法：

#### 10.2.1 审批请求处理

```java
public ToolGuardApproveResult userApprovalResult(ToolGuardApprovalInfo info) {
    String sessionKey = info.conversation().sessionId() + ":" + info.conversation().userId();
    String code = generateApprovalCode();

    // 1. 发送审批消息
    ToolGuardApproveResult messageResult = sendApprovalMessage(info, code);
    if (messageResult != null) {
        return messageResult;
    }

    // 2. 创建 Future 并阻塞等待用户回复
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

#### 10.2.2 审批码生成

```java
private String generateApprovalCode() {
    return String.format("%04d", new java.util.Random().nextInt(10_000));
}
```

#### 10.2.3 审批消息构建

```java
private String buildToolGuardApprovalMessage(ToolGuardApprovalInfo info, String code) {
    StringBuilder body = new StringBuilder();
    body.append("Emailclaw 检测到高风险工具调用，需要你审批。\n\n");
    body.append("审批码：").append(code).append('\n');
    body.append("回复方式：请直接回复 4 位审批码，表示同意执行；若回复其它内容则表示拒绝；或不回复将超时拒绝。\n\n");
    body.append("工具：").append(info.toolName()).append('\n');
    body.append("风险等级：")
            .append(info.severity() == null ? "" : info.severity().getDisplayName())
            .append('\n');
    body.append("剩余时间：").append(info.timeoutSeconds()).append(" 秒\n");
    body.append("参数：").append(compactApprovalText(String.valueOf(info.toolInput()), 1200)).append("\n\n");
    if (info.findings() != null && !info.findings().isEmpty()) {
        body.append("风险说明：\n");
        info.findings().forEach(finding ->
                body.append("- ")
                        .append(finding == null ? "" : finding.description)
                        .append('\n'));
    }
    return body.toString();
}
```

### 10.3 审批码回复检测

在处理用户消息时，需要优先检测审批码回复：

```java
private void handleMessage(String request, ChannelInfo channelInfo) {
    // ... 解析消息 ...
    
    ThreadUtils.run(() -> {
        // 查找或创建会话
        ChatSessionInfo session = findOrCreateSession(agent, conversationId, senderId);
        
        // ToolGuard 审批码回复检测
        String pendingKey = session.sessionId + ":" + senderId;
        PendingApprovalRequest pendingRequest = pendingApprovals.get(pendingKey);
        if (pendingRequest != null) {
            String replyContent = content.trim();
            handleApprovalReply(pendingKey, replyContent);
            // 发送审批结果确认消息
            sendApprovalConfirmation(sessionWebhook, replyContent, pendingRequest.code());
            return;
        }
        
        // 正常消息处理流程
        chatService.sendMessage(...);
    });
}
```

### 10.4 审批码验证

```java
private void handleApprovalReply(String sessionKey, String replyContent) {
    PendingApprovalRequest pendingRequest = pendingApprovals.get(sessionKey);
    if (pendingRequest != null) {
        if (replyContent.equals(pendingRequest.code())) {
            // 审批码匹配 → 批准
            pendingRequest.future().complete(ToolGuardApproveResult.APPROVED);
        } else {
            // 审批码不匹配 → 拒绝
            pendingRequest.future().complete(ToolGuardApproveResult.REJECTED);
        }
    }
}
```

### 10.5 数据结构

```java
// 待审批请求：key = sessionId + ":" + userId
private final ConcurrentHashMap<String, PendingApprovalRequest> pendingApprovals 
    = new ConcurrentHashMap<>();

// 待审批请求内部记录
private record PendingApprovalRequest(
    String code, 
    CompletableFuture<ToolGuardApproveResult> future
) {}
```

### 10.6 关键注意事项

1. **审批码绑定**：必须绑定 `channelId + sessionId + userId`，不能只按 4 位码全局匹配。
2. **消息截获**：审批码消息必须在进入大模型前被截获，不能传给 `ChatService.sendMessage(...)`。
3. **超时处理**：必须处理超时情况，超时后自动拒绝。
4. **确认消息**：收到用户回复后，应发送确认消息告知审批结果。
5. **资源清理**：审批完成后，必须从 `pendingApprovals` 中移除记录。

### 10.7 与 Emailclaw 的对比

| 特性 | Emailclaw | DingTalk |
|------|-----------|----------|
| 审批码生成 | 4位随机数字 | 4位随机数字 |
| 消息发送 | 邮件（IMAP/SMTP） | 机器人消息（Webhook） |
| 用户回复检测 | 邮件轮询 | 消息回调 |
| 超时机制 | CompletableFuture.get() | CompletableFuture.get() |
| 审批确认 | 回复邮件 | 发送确认消息 |

两种实现的核心逻辑相同，主要区别在于消息发送和接收的方式。

## 11. 插件打包、部署与使用说明

### 11.1 编译打包机制

第三方插件与样例插件通常作为独立模块开发。在多模块 Maven Reactor 工程中，为了正确解析宿主 `emailclaw` 依赖，请在工程根目录执行定向打包命令：

```sh
# 示例：打包 Tool 插件
mvn -pl invokeAntigravityCli -am package

# 示例：打包 Channel 插件
mvn -pl dingtalk -am package
```

打包完成后，各子模块的 `target/` 目录将生成包含必要依赖的独立 Shaded JAR：
- `invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar`
- `dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar`

### 11.2 部署到 Emailclaw

Emailclaw 的外部插件根目录为：
- **Linux / macOS**：`~/emailclaw/plugins/`
- **Windows**：`%USERPROFILE%\emailclaw\plugins\`

将编译生成的 JAR 文件拷贝到该目录即可完成部署：

```sh
# 拷贝 Tool 插件
cp invokeAntigravityCli/target/emailclaw-plugin-tool-invokeAntigravityCli-1.0.0.jar ~/emailclaw/plugins/

# 拷贝 Channel 插件
cp dingtalk/target/emailclaw-plugin-channel-dingtalk-1.0.0.jar ~/emailclaw/plugins/
```

### 11.3 动态热重载与生效验证

Emailclaw 提供了开箱即用的插件生命周期管理与热重载机制：
1. **运行期动态热加载**：系统后台守护线程每 5 秒扫描一次 `~/emailclaw/plugins/` 目录。一旦检测到新拷贝入的 `.jar` 文件，将自动通过独立 ClassLoader 装载、解析 `plugin.json`、实例化入口类并调用 `register()` 与 `initialize()`。
2. **启动期全量装配**：每次启动 Emailclaw 客户端或服务时，`PluginManager` 会自动扫描 `~/emailclaw/plugins/` 下的所有 JAR 文件并全量加载。
3. **日志验证**：成功加载后，控制台/日志将输出如下信息：
   ```
   INFO: Discovered external plugin: emailclaw-plugin-tool-invokeAntigravityCli
   INFO: Successfully registered Tool plugin: invokeAntigravityCli
   INFO: InvokeAntigravityCliPlugin started
   ```

### 11.4 在 Emailclaw 中使用插件

1. **渠道插件（Channel Plugin，如 DingTalk）**：
   - 打开客户端「系统设置 -> 渠道配置（Channels）」页面。
   - 列表中将自动出现新插件条目，点击 `Configure` 配置连接凭据（如 AppKey / AppSecret）。
   - 将开关切换为启用，即可通过钉钉与 AI Agent 建立会话交互。
2. **工具插件（Tool Plugin，如 invokeAntigravityCli）**：
   - 插件加载后，工具将自动注入到智能体的工具集（`Toolkit`）中。
   - 在与 Agent 的对话会话中，只需提出相关任务（例如：“请调用 invokeAntigravityCli 运行测试并生成 JSON 报告”），大模型将自动决定并触发该 Tool 的后台执行。

