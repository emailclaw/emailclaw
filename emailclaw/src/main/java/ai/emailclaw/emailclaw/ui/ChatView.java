/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw.ui;

import ai.emailclaw.emailclaw.model.AgentConfiguration;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ArtifactRecord;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatMessageRecord;
import ai.emailclaw.emailclaw.model.ChatMessageRoles;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus;
import ai.emailclaw.emailclaw.model.CronJobModel.CronJobSpec;
import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.security.GuardFinding;
import ai.emailclaw.emailclaw.model.security.PendingApproval;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.CronJobService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.StreamCallback;
import ai.emailclaw.emailclaw.util.DesktopPlatformUtils;
import ai.emailclaw.emailclaw.util.WebViewUtils;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionRule;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import netscape.javascript.JSObject;

/**
 * Chat main view.
 *
 * <p>
 * Responsible for displaying history messages, selecting models, and initiating streaming chat requests.
 */
public class ChatView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(ChatView.class.getName());

    private static final long CHAT_ATTACHMENT_MAX_BYTES = 10L * 1024L * 1024L;

    /**
     * CSS styles for WebView message rendering, loaded from resource files.
     */
    private static final String CHAT_WEBVIEW_CSS =
            loadResource("/ai/emailclaw/emailclaw/css/chat-webview.css");

    private final AgentService agentService;

    private final ProviderService providerService;

    private final ChatService chatService;

    private final BorderPane root = new BorderPane();

    private final WebView webView = new WebView();

    private final JavaInterop javaInterop = new JavaInterop();

    private final TextArea input = new TextArea();

    private final Label counter = new Label("0/10000");

    private final Button sendBtn = new Button("Send");

    private final ComboBox<String> permissionModeBox = new ComboBox<>();

    private final Button attachBtn = new Button("Upload file");

    private final Label attachmentStatus = new Label("No attachments");

    private final Tooltip attachTooltip = new Tooltip();

    private final ComboBox<AgentInfo> agentCombo = new ComboBox<>();

    private ModelSelectionComponent selectModelBtn;

    private final Button newChatBtn = new Button("New...");

    private final Button searchBtn = new Button("Search");

    private final Button chatHistoryBtn = new Button("History");

    private ChatSearchPanel chatSearchPanel;

    private final TextField title = new TextField();

    private final List<Path> attachments = new ArrayList<>();

    private final List<ChatMessageRecord> messages = new ArrayList<>();

    private AgentInfo currentAgent;

    private ProviderInfo selectedProvider;

    private ModelInfo selectedModel;

    private ChatSessionInfo currentSession;

    private CodingModePane codingModePane;

    private boolean codingModeActive = false;

    private SplitPane mainSplitPane;

    private ProjectInfo currentProject;

    private final String sessionKind;

    private javafx.scene.layout.HBox taskBar;

    private ComboBox<TaskStatus> taskStatusCombo;

    private javafx.scene.control.TextField taskDescriptionInput;

    private javafx.scene.layout.VBox cronJobContainer;

    private final CronJobService cronJobService;

    private Consumer<ChatSessionInfo> onOpenTask;

    public ChatView(
            AgentService agentService,
            ProviderService providerService,
            ChatService chatService,
            CronJobService cronJobService,
            AgentInfo initialAgent,
            String sessionKind,
            ProjectInfo currentProject) {
        this(
                agentService,
                providerService,
                chatService,
                cronJobService,
                initialAgent,
                sessionKind,
                currentProject,
                null);
    }

    /**
     * Pure DI constructor accepting an onOpenTask navigation callback.
     *
     * @param agentService Service managing agent instances
     * @param providerService Service managing LLM providers
     * @param chatService Service managing chat sessions and messages
     * @param cronJobService Service managing scheduled jobs
     * @param initialAgent Active agent information
     * @param sessionKind Session kind (CHAT or TASK)
     * @param currentProject Current project information
     * @param onOpenTask Callback invoked when a specific task session is requested to be opened
     */
    public ChatView(
            AgentService agentService,
            ProviderService providerService,
            ChatService chatService,
            CronJobService cronJobService,
            AgentInfo initialAgent,
            String sessionKind,
            ProjectInfo currentProject,
            Consumer<ChatSessionInfo> onOpenTask) {
        this.agentService = agentService;
        this.providerService = providerService;
        this.chatService = chatService;
        this.cronJobService = cronJobService;
        this.currentAgent = initialAgent;
        this.currentProject = currentProject;
        this.sessionKind =
                sessionKind == null
                        ? ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_CHAT
                        : sessionKind;
        this.onOpenTask = onOpenTask;
        this.title.setText(defaultSessionTitle());
        initCodingModePane();
        initUi();
        restoreCodingModeState();
        resolveDefaultModel();
        ensureSession();
        refresh();
        startApprovalListener();
        chatService
                .repository()
                .configManager()
                .addChangeListener(
                        ai.emailclaw.emailclaw.storage.ConfigManager.EVENT_SESSIONS,
                        () -> Platform.runLater(this::refreshSessionTitle));
    }

    /**
     * Sets or updates the navigation callback for opening a task session in TaskView.
     *
     * @param onOpenTask Callback accepting the target ChatSessionInfo
     */
    public void setOnOpenTask(Consumer<ChatSessionInfo> onOpenTask) {
        this.onOpenTask = onOpenTask;
    }

    /**
     * Open the specified task session. If a parent onOpenTask handler is registered, delegate
     * to it; otherwise load the session directly in this view.
     *
     * @param targetSession Target session to open
     */
    private void openTaskSession(ChatSessionInfo targetSession) {
        if (targetSession == null) {
            return;
        }
        if (onOpenTask != null) {
            onOpenTask.accept(targetSession);
        } else {
            loadSession(targetSession);
        }
    }

    /**
     * Get the default session title based on the session kind.
     *
     * @return "New Task" for task sessions, or "New Chat" for chat sessions
     */
    private String defaultSessionTitle() {
        return ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK.equals(sessionKind)
                ? "New Task"
                : "New Chat";
    }

    /**
     * Get the default session title for a specific session based on its kind.
     *
     * @param session The session to check
     * @return "New Task" if kind is TASK, or "New Chat" otherwise
     */
    private String defaultTitleFor(ChatSessionInfo session) {
        String kind =
                session != null && session.getKind() != null ? session.getKind() : sessionKind;
        return ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK.equals(kind)
                ? "New Task"
                : "New Chat";
    }

    /**
     * Get the display title for a session, falling back to defaultTitleFor if name is blank.
     *
     * @param session The session to inspect
     * @return Resolved display title
     */
    private String getSessionTitle(ChatSessionInfo session) {
        if (session != null && session.getName() != null && !session.getName().isBlank()) {
            return session.getName();
        }
        return defaultTitleFor(session);
    }

    private void refreshSessionTitle() {
        if (currentSession != null) {
            ChatSessionInfo updatedSession = chatService.findSession(currentSession.getId());
            if (updatedSession != null) {
                currentSession.setName(updatedSession.getName());
                title.setText(getSessionTitle(updatedSession));
            }
        }
    }

    private void initCodingModePane() {
        codingModePane =
                new CodingModePane(
                        currentProject,
                        chatService,
                        new CodingModePane.Callback() {

                            @Override
                            public void copyToChat(String markdown) {
                                String currentInput = input.getText();
                                if (!currentInput.isEmpty() && !currentInput.endsWith("\n")) {
                                    currentInput += "\n";
                                }
                                input.setText(currentInput + markdown);
                            }

                            @Override
                            public AgentConfiguration getAgentConfiguration() {
                                return chatService
                                        .repository()
                                        .loadAgentConfig(currentAgent.getId());
                            }

                            @Override
                            public void saveAgentConfiguration(AgentConfiguration config) {
                                chatService
                                        .repository()
                                        .saveAgentConfig(currentAgent.getId(), config);
                            }
                        });
    }

    public class JavaInterop {

        public void viewOffloadedFile(String offloadPath) {
            Platform.runLater(
                    () -> {
                        try {
                            String content =
                                    java.nio.file.Files.readString(
                                            java.nio.file.Path.of(offloadPath),
                                            java.nio.charset.StandardCharsets.UTF_8);
                            javafx.scene.control.TextArea textArea =
                                    new javafx.scene.control.TextArea(content);
                            textArea.setEditable(false);
                            javafx.scene.Scene scene = new javafx.scene.Scene(textArea, 800, 600);
                            javafx.stage.Stage stage = new javafx.stage.Stage();
                            stage.setTitle(
                                    "Offloaded Content: "
                                            + java.nio.file.Path.of(offloadPath).getFileName());
                            stage.setScene(scene);
                            stage.show();
                        } catch (java.io.IOException e) {
                            LOGGER.log(Level.SEVERE, "Failed to load offload file", e);
                        }
                    });
        }

        private Path resolveArtifactPath(String filePath) {
            if (filePath == null || filePath.isBlank()) {
                return null;
            }
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                return path;
            }
            if (currentProject != null && currentProject.getBaseDirectory() != null) {
                Path base = Path.of(currentProject.getBaseDirectory());
                Path candidate = base.resolve(filePath);
                if (Files.exists(candidate)) {
                    return candidate;
                }
                Path inArtifacts = base.resolve("artifacts").resolve(path.getFileName());
                if (Files.exists(inArtifacts)) {
                    return inArtifacts;
                }
                Path inRoot = base.resolve(path.getFileName());
                if (Files.exists(inRoot)) {
                    return inRoot;
                }
            }
            return path;
        }

        public void openArtifactFile(String filePath) {
            Platform.runLater(
                    () -> {
                        try {
                            Path path = resolveArtifactPath(filePath);
                            if (path == null || !Files.exists(path)) {
                                LOGGER.warning("Artifact file not found: " + filePath);
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Open File");
                                alert.setHeaderText("File Not Found");
                                alert.setContentText(
                                        "The artifact file does not exist at:\n"
                                                + (path != null ? path : filePath));
                                alert.showAndWait();
                                return;
                            }
                            DesktopPlatformUtils.openFile(path);
                            LOGGER.info("Initiated open file for artifact: " + path);
                        } catch (Exception e) {
                            LOGGER.log(
                                    Level.WARNING, "Failed to open artifact file: " + filePath, e);
                        }
                    });
        }

        public void revealArtifactInFolder(String filePath) {
            Platform.runLater(
                    () -> {
                        try {
                            Path path = resolveArtifactPath(filePath);
                            if (path == null) {
                                LOGGER.warning("Artifact path is null/blank");
                                return;
                            }
                            Path dir = Files.isDirectory(path) ? path : path.getParent();
                            if ((dir == null || !Files.exists(dir)) && !Files.exists(path)) {
                                LOGGER.warning(
                                        "Artifact parent directory not found for: " + filePath);
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Show in Folder");
                                alert.setHeaderText("Directory Not Found");
                                alert.setContentText(
                                        "The directory does not exist at:\n"
                                                + (dir != null ? dir : filePath));
                                alert.showAndWait();
                                return;
                            }
                            DesktopPlatformUtils.revealInFolder(path);
                            LOGGER.info("Initiated reveal in folder for artifact: " + path);
                        } catch (Exception e) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Failed to reveal artifact folder: " + filePath,
                                    e);
                        }
                    });
        }

        public void copyArtifactPath(String filePath) {
            Platform.runLater(
                    () -> {
                        try {
                            Clipboard clipboard = Clipboard.getSystemClipboard();
                            ClipboardContent content = new ClipboardContent();
                            content.putString(filePath);
                            clipboard.setContent(content);
                            LOGGER.info("Copied artifact path to clipboard: " + filePath);
                        } catch (Exception e) {
                            LOGGER.log(
                                    Level.WARNING, "Failed to copy artifact path: " + filePath, e);
                        }
                    });
        }

        public void previewArtifact(String filePath) {
            Platform.runLater(
                    () -> {
                        try {
                            Path path = resolveArtifactPath(filePath);
                            if (path == null || !Files.exists(path)) {
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Artifact Preview");
                                alert.setHeaderText("File Not Found");
                                alert.setContentText(
                                        "The artifact file was not found on disk at:\n"
                                                + (path != null ? path : filePath));
                                alert.showAndWait();
                                return;
                            }
                            String fileName = path.getFileName().toString();
                            String lowerName = fileName.toLowerCase(Locale.ROOT);
                            Stage previewStage = new Stage();
                            previewStage.setTitle("Deliverable Preview — " + fileName);

                            BorderPane previewRoot = new BorderPane();
                            previewRoot.setPadding(new Insets(10));

                            HBox topBar = new HBox(12);
                            topBar.setAlignment(Pos.CENTER_LEFT);
                            topBar.setPadding(new Insets(0, 0, 10, 0));
                            Label titleLabel = new Label(fileName);
                            titleLabel.setStyle(
                                    "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill:"
                                            + " #0f172a;");
                            Label sizeLabel =
                                    new Label("(" + formatFileSize(Files.size(path)) + ")");
                            sizeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
                            HBox spacer = new HBox();
                            HBox.setHgrow(spacer, Priority.ALWAYS);

                            final String resolvedPath = path.toAbsolutePath().toString();
                            Button copyPathBtn = new Button("Copy Path");
                            copyPathBtn.setOnAction(e -> copyArtifactPath(resolvedPath));
                            Button openExtBtn = new Button("Open with OS App");
                            openExtBtn.setOnAction(e -> openArtifactFile(resolvedPath));

                            topBar.getChildren()
                                    .addAll(titleLabel, sizeLabel, spacer, copyPathBtn, openExtBtn);
                            previewRoot.setTop(topBar);

                            if (lowerName.endsWith(".png")
                                    || lowerName.endsWith(".jpg")
                                    || lowerName.endsWith(".jpeg")
                                    || lowerName.endsWith(".gif")) {
                                Image img = new Image(path.toUri().toString());
                                ImageView iv = new ImageView(img);
                                iv.setPreserveRatio(true);
                                iv.setFitWidth(800);
                                iv.setFitHeight(560);
                                ScrollPane sp = new ScrollPane(iv);
                                previewRoot.setCenter(sp);
                                previewStage.setScene(new Scene(previewRoot, 840, 640));
                            } else {
                                String text = Files.readString(path, StandardCharsets.UTF_8);
                                if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
                                    WebView mdPreview = new WebView();
                                    WebViewUtils.configureUserDataDirectory(mdPreview);
                                    String mdHtml =
                                            "<!DOCTYPE html><html><head><meta"
                                                + " charset='UTF-8'/><style>body { font-family:"
                                                + " -apple-system, BlinkMacSystemFont, 'Segoe UI',"
                                                + " Roboto, sans-serif; padding: 24px 32px;"
                                                + " line-height: 1.68; color: #1e293b; background:"
                                                + " #ffffff; margin: 0; }h1, h2, h3 { color:"
                                                + " #0f172a; border-bottom: 1px solid #e2e8f0;"
                                                + " padding-bottom: 8px; margin-top: 24px; }table {"
                                                + " border-collapse: collapse; width: 100%; margin:"
                                                + " 16px 0; font-size: 13px; }th, td { border: 1px"
                                                + " solid #cbd5e1; padding: 8px 12px; text-align:"
                                                + " left; }th { background: #f8fafc; font-weight:"
                                                + " 700; color: #334155; }tr:nth-child(even) {"
                                                + " background: #f8fafc; }code { font-family:"
                                                + " 'JetBrains Mono', Consolas, monospace;"
                                                + " background: #f1f5f9; padding: 2px 6px;"
                                                + " border-radius: 4px; font-size: 12px; }pre {"
                                                + " background: #0f172a; color: #f8fafc; padding:"
                                                + " 14px; border-radius: 6px; overflow: auto; }pre"
                                                + " code { background: transparent; padding: 0;"
                                                + " color: inherit; }blockquote { border-left: 4px"
                                                + " solid #3b82f6; margin: 16px 0; padding: 8px"
                                                + " 16px; background: #eff6ff; color: #1e40af; }hr"
                                                + " { border: none; border-top: 1px solid #e2e8f0;"
                                                + " margin: 24px 0; }</style></head><body>"
                                                    + markdownToHtml(text)
                                                    + "</body></html>";
                                    mdPreview.getEngine().loadContent(mdHtml);
                                    previewRoot.setCenter(mdPreview);
                                    previewStage.setScene(new Scene(previewRoot, 880, 680));
                                } else {
                                    TextArea ta = new TextArea(text);
                                    ta.setEditable(false);
                                    ta.setStyle(
                                            "-fx-font-family: 'JetBrains Mono', Consolas,"
                                                    + " monospace; -fx-font-size: 13px;");
                                    previewRoot.setCenter(ta);
                                    previewStage.setScene(new Scene(previewRoot, 840, 600));
                                }
                            }
                            previewStage.show();
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Failed to preview artifact: " + filePath, e);
                            openArtifactFile(filePath);
                        }
                    });
        }

        public void approveTool(String approvalId, boolean remember) {
            Platform.runLater(
                    () -> {
                        Optional<PendingApproval> approval = findPendingApproval(approvalId);
                        if (approval.isPresent()
                                && chatService
                                        .getGovernanceService()
                                        .setApprovalDecision(
                                                approvalId,
                                                ai.emailclaw.emailclaw.model.security
                                                        .ApprovalDecision.APPROVE,
                                                "console",
                                                "")) {
                            addApprovalDecisionMessage(approval.get(), true);
                            activeApprovals.remove(approvalId);
                            renderMessages();
                            resumeAgentAfterApproval(approval.get(), true, remember);
                        }
                    });
        }

        public void denyTool(String approvalId) {
            Platform.runLater(
                    () -> {
                        Optional<PendingApproval> approval = findPendingApproval(approvalId);
                        if (approval.isPresent()
                                && chatService
                                        .getGovernanceService()
                                        .setApprovalDecision(
                                                approvalId,
                                                ai.emailclaw.emailclaw.model.security
                                                        .ApprovalDecision.DENY,
                                                "console",
                                                "")) {
                            addApprovalDecisionMessage(approval.get(), false);
                            activeApprovals.remove(approvalId);
                            renderMessages();
                            resumeAgentAfterApproval(approval.get(), false, false);
                        }
                    });
        }
    }

    private final Set<String> activeApprovals = ConcurrentHashMap.newKeySet();

    private final Map<String, ChatMessageRecord> approvalMessages = new ConcurrentHashMap<>();

    private SendStreamCallback activeStreamCallback;

    /**
     * Reference to the virtual thread currently executing the send operation, used to support the "Stop" button to interrupt the dialog stream.
     */
    private volatile Thread activeSendThread;

    /**
     * Flag indicating whether it's currently in "sending" state, controls sendBtn Send/Stop dual mode switch.
     */
    private volatile boolean isSending = false;

    /**
     * Debounce flag: prevent frequent calls to renderMessages() during streaming which causes WebView to fully rebuild HTML repeatedly.
     */
    private boolean renderScheduled = false;

    private Optional<PendingApproval> findPendingApproval(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return Optional.empty();
        }
        return chatService.getGovernanceService().getPendingApprovals().stream()
                .filter(approval -> approvalId.equals(approval.getId()))
                .findFirst();
    }

    /**
     * Resume Agent execution in a background thread (called after Console approval).
     */
    private void resumeAgentAfterApproval(
            PendingApproval approval, boolean approved, boolean remember) {
        Thread resumeThread =
                new Thread(
                        () -> {
                            try {
                                ToolUseBlock toolBlock =
                                        new ToolUseBlock(
                                                approval.getId(),
                                                approval.getToolName(),
                                                approval.getToolInput());
                                // Only add ALLOW rule when approved && remember ("Remember this
                                // decision")
                                List<PermissionRule> rules =
                                        approved && remember
                                                ? List.of(
                                                        new PermissionRule(
                                                                approval.getToolName(),
                                                                null,
                                                                PermissionBehavior.ALLOW,
                                                                "console_approved"))
                                                : List.of();
                                ConfirmResult confirmResult =
                                        new ConfirmResult(approved, toolBlock, rules);
                                Msg result =
                                        chatService.resumeWithConfirmResult(
                                                approval.getAgentId(),
                                                approval.getSessionId(),
                                                "console",
                                                approval.getRoute() != null
                                                        ? approval.getRoute()
                                                        : Map.of(),
                                                List.of(confirmResult));
                                if (result != null) {
                                    Platform.runLater(
                                            () -> {
                                                // Ensure the current session matches the session of
                                                // the approval request
                                                if (currentSession != null
                                                        && currentSession.getId() != null
                                                        && currentSession
                                                                .getId()
                                                                .equals(approval.getSessionId())) {
                                                    messages.add(
                                                            new ChatMessageRecord(
                                                                    ChatMessageRoles.ASSISTANT,
                                                                    List.of(
                                                                            ChatMessagePart.text(
                                                                                    result
                                                                                            .getTextContent())),
                                                                    LocalDateTime.now()
                                                                            .toString()));
                                                    chatService.touchSession(currentSession);
                                                    renderMessages();
                                                }
                                            });
                                }
                            } catch (Exception e) {
                                LOGGER.log(
                                        Level.WARNING,
                                        "Failed to resume Agent after Console approval",
                                        e);
                            }
                        });
        resumeThread.setDaemon(true);
        resumeThread.start();
    }

    private void addApprovalDecisionMessage(PendingApproval approval, boolean approved) {
        String approvalId = approval == null ? "" : approval.getId();
        String action = approved ? "Approved" : "Denied";
        String target =
                approval == null
                        ? "this tool call"
                        : approval.getToolName() + " " + compactToolInput(approval.getToolInput());
        String content = "User has " + action + " " + target;
        ChatMessageRecord approvalMessage = approvalMessages.remove(approvalId);
        if (approvalMessage != null) {
            approvalMessage.setRole(ChatMessageRoles.SYSTEM);
            approvalMessage.setParts(new ArrayList<>(List.of(ChatMessagePart.text(content))));
            approvalMessage.setCreatedAt(LocalDateTime.now().toString());
            if (activeStreamCallback != null) {
                activeStreamCallback.startNewAssistantSegmentAfter(approvalMessage);
            }
            if (currentAgent != null && currentSession != null) {
                chatService.appendHistory(
                        currentAgent.getId(), currentSession.getId(), approvalMessage);
            }
            return;
        }
        ChatMessageRecord newMsg =
                new ChatMessageRecord(
                        ChatMessageRoles.SYSTEM,
                        List.of(ChatMessagePart.text(content)),
                        LocalDateTime.now().toString());
        messages.add(newMsg);
        if (currentAgent != null && currentSession != null) {
            chatService.appendHistory(currentAgent.getId(), currentSession.getId(), newMsg);
        }
    }

    private String compactToolInput(Map<String, Object> toolInput) {
        if (toolInput == null || toolInput.isEmpty()) {
            return "";
        }
        String text = toolInput.toString().replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() > 180) {
            return text.substring(0, 180).trim() + "...";
        }
        return text;
    }

    private void startApprovalListener() {
        Thread listenerThread =
                new Thread(
                        () -> {
                            while (true) {
                                try {
                                    List<PendingApproval> pendingList =
                                            chatService
                                                    .getGovernanceService()
                                                    .getPendingApprovals();
                                    boolean hasNew = false;
                                    for (PendingApproval approval : pendingList) {
                                        if (!approval.isDecided()
                                                && !activeApprovals.contains(approval.getId())) {
                                            activeApprovals.add(approval.getId());
                                            Platform.runLater(
                                                    () -> addApprovalMessageIfAbsent(approval));
                                            hasNew = true;
                                        }
                                    }
                                    if (hasNew) {
                                        Platform.runLater(this::renderMessages);
                                    }
                                    // Check every second
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void addApprovalMessageIfAbsent(PendingApproval approval) {
        if (approval == null || approval.getId() == null || approval.getId().isBlank()) {
            return;
        }
        if (approvalMessages.containsKey(approval.getId())) {
            return;
        }
        ChatMessageRecord record =
                new ChatMessageRecord(
                        ChatMessageRoles.APPROVAL,
                        List.of(
                                ChatMessagePart.text(
                                        approval.getId() == null ? "" : approval.getId())),
                        LocalDateTime.now().toString());
        approvalMessages.put(approval.getId(), record);
        messages.add(record);
        renderMessages();
    }

    @SuppressWarnings("removal")
    private void initTaskBar() {
        taskStatusCombo = new ComboBox<>();
        taskStatusCombo.setConverter(
                new StringConverter<>() {

                    @Override
                    public String toString(TaskStatus object) {
                        return object == null ? "" : object.getDisplayName();
                    }

                    @Override
                    public TaskStatus fromString(String string) {
                        return TaskStatus.fromString(string);
                    }
                });
        taskStatusCombo
                .valueProperty()
                .addListener(
                        (obs, oldVal, newVal) -> {
                            if (currentSession != null && newVal != null) {
                                if (newVal != currentSession.getStatus()) {
                                    currentSession.setStatus(newVal);
                                    chatService.updateSession(currentSession);
                                }
                            }
                        });
        taskDescriptionInput = new javafx.scene.control.TextField();
        taskDescriptionInput.setPromptText("Enter task description...");
        javafx.scene.layout.HBox.setHgrow(
                taskDescriptionInput, javafx.scene.layout.Priority.ALWAYS);
        taskDescriptionInput
                .focusedProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (!newV && currentSession != null) {
                                if (!taskDescriptionInput
                                        .getText()
                                        .equals(currentSession.getDescription())) {
                                    currentSession.setDescription(taskDescriptionInput.getText());
                                    chatService.updateSession(currentSession);
                                }
                            }
                        });
        taskBar =
                new javafx.scene.layout.HBox(
                        12,
                        new Label("Task status:"),
                        taskStatusCombo,
                        new Label("Task Description:"),
                        taskDescriptionInput);
        taskBar.setAlignment(Pos.CENTER_LEFT);
        taskBar.setPadding(new Insets(8, 12, 8, 12));
        taskBar.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 1"
                        + " 0;");
        taskBar.setManaged(false);
        taskBar.setVisible(false);
        cronJobContainer = new javafx.scene.layout.VBox(0);
        cronJobContainer.setManaged(false);
        cronJobContainer.setVisible(false);
        cronJobContainer.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 1"
                        + " 0; -fx-padding: 8 12 8 12;");
    }

    private void initUi() {
        root.getStyleClass().add("page");
        // Keep SplitPane aligned with main window bounds.
        root.setPadding(new Insets(0));
        root.getStyleClass().add("bg-f6");
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 12, 0));
        top.getStyleClass().add("bg-f6");
        title.getStyleClass().add("page-title");
        title.setMaxWidth(600);
        title.setStyle(
                "-fx-text-fill: #111827; -fx-font-size: 18px; -fx-font-weight: 700;"
                        + " -fx-background-color: transparent; -fx-padding: 0;");
        title.setPromptText("Enter session title...");
        title.focusedProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (!newV && currentSession != null) {
                                currentSession.setName(title.getText());
                                chatService.updateSession(currentSession);
                            }
                        });
        title.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        title.getParent().requestFocus();
                    }
                });
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        agentCombo.getItems().setAll(agentService.list());
        agentCombo.setValue(currentAgent);
        agentCombo.setCellFactory(
                v ->
                        new ListCell<AgentInfo>() {

                            @Override
                            protected void updateItem(AgentInfo item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item.getName());
                            }
                        });
        agentCombo.setButtonCell(
                new ListCell<AgentInfo>() {

                    @Override
                    protected void updateItem(AgentInfo item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.getName());
                    }
                });
        selectModelBtn = new ModelSelectionComponent("Select model", providerService);
        selectModelBtn.setOnModelSelected(
                (provider, model) -> {
                    selectedProvider = provider;
                    selectedModel = model;
                    selectModelBtn.setText("Model: " + model.getName());
                    updateAttachmentTooltip();
                    if (currentAgent != null) {
                        currentAgent.setProviderId(provider.getId());
                        currentAgent.setModelId(model.getId());
                        agentService.save();
                    }
                    LOGGER.log(
                            Level.INFO,
                            "Selected model: provider={0}, model={1}",
                            new Object[] {provider.getId(), model.getName()});
                });

        agentCombo.setStyle(
                "-fx-background-color: transparent; -fx-border-color: #d1d5db; -fx-border-radius:"
                        + " 4; -fx-padding: 0;");
        agentCombo
                .valueProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (newV != null
                                    && (oldV == null || !newV.getId().equals(oldV.getId()))) {
                                currentAgent = newV;
                                resolveDefaultModel();
                                renderMessages();
                            }
                        });
        styleToolbarButton(selectModelBtn, false);
        styleToolbarButton(newChatBtn, false);
        styleToolbarButton(searchBtn, false);
        styleToolbarButton(chatHistoryBtn, false);
        styleToolbarButton(attachBtn, false);
        styleToolbarButton(sendBtn, true);
        sendBtn.setId("btn-chat-send");
        attachBtn.setOnAction(e -> chooseAttachments());
        attachBtn.setTooltip(attachTooltip);
        newChatBtn.setOnAction(
                e -> {
                    currentSession =
                            chatService.newSession(
                                    currentAgent.getId(),
                                    sessionKind,
                                    defaultSessionTitle(),
                                    currentProject.getId());
                    title.setText(currentSession.getName());
                    if (selectedProvider == null || selectedModel == null) {
                        resolveDefaultModel();
                    }
                    attachments.clear();
                    attachmentStatus.setText("No attachments");
                    messages.clear();
                    renderMessages();
                    updateTaskBar();
                });
        searchBtn.setTooltip(new Tooltip("Search all chats"));
        searchBtn.setOnAction(e -> openChatSearchPanel());
        chatHistoryBtn.setOnAction(e -> showChatHistoryDialog());
        top.getChildren()
                .addAll(
                        title,
                        spacer,
                        agentCombo,
                        selectModelBtn,
                        newChatBtn,
                        searchBtn,
                        chatHistoryBtn);
        VBox webViewContainer = new VBox(10);
        webViewContainer.setPadding(new Insets(0));
        webViewContainer.getStyleClass().add("bg-f6");
        webView.setPrefHeight(640);
        webView.getStyleClass().add("bg-f6");
        WebViewUtils.configureUserDataDirectory(webView);
        webView.getEngine()
                .getLoadWorker()
                .stateProperty()
                .addListener(
                        (obs, oldState, newState) -> {
                            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                                JSObject window =
                                        (JSObject) webView.getEngine().executeScript("window");
                                window.setMember("javaInterop", javaInterop);
                            }
                        });
        webViewContainer.getChildren().addAll(webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
        VBox composer = new VBox(8);
        composer.setPadding(new Insets(12));
        composer.setStyle(
                "-fx-background-color: #ffffff;"
                        + "-fx-border-color: #e4e8ef;"
                        + "-fx-border-width: 1;"
                        + "-fx-background-radius: 8;"
                        + "-fx-border-radius: 8;");
        input.setId("field-chat-input");
        input.setPromptText("\"↑↓\" for message navigation - \"/\" for quick commands");
        input.setPrefRowCount(3);
        input.setMinHeight(78);
        input.setStyle(
                "-fx-control-inner-background: #ffffff;"
                        + "-fx-background-color: #ffffff;"
                        + "-fx-border-color: #cbd5e1;"
                        + "-fx-border-radius: 7;"
                        + "-fx-background-radius: 7;"
                        + "-fx-padding: 8 10;"
                        + "-fx-font-size: 13px;"
                        + "-fx-text-fill: #111827;"
                        + "-fx-prompt-text-fill: #94a3b8;");
        input.textProperty()
                .addListener(
                        (obs, oldV, newV) ->
                                counter.setText(Math.min(newV.length(), 10000) + "/10000"));
        input.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                        event.consume();
                        handleSendOrStop();
                    }
                });
        HBox attachmentRow = new HBox(8, attachmentStatus);
        attachmentRow.setAlignment(Pos.CENTER_LEFT);
        attachmentStatus.getStyleClass().addAll("text-12", "text-slate");
        HBox action = new HBox(8);
        action.setAlignment(Pos.CENTER_RIGHT);
        HBox actionSpacer = new HBox();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        counter.getStyleClass().addAll("text-12", "text-slate");
        // Initialize PermissionMode ComboBox
        permissionModeBox
                .getItems()
                .addAll("bypass", "default", "accept_edits", "explore", "dont_ask");
        permissionModeBox.setValue("bypass");
        permissionModeBox.setPrefWidth(120);
        permissionModeBox.setCellFactory(
                lv ->
                        new ListCell<>() {

                            private final javafx.scene.control.Tooltip tooltip =
                                    new javafx.scene.control.Tooltip();

                            {
                                tooltip.setStyle("-fx-font-size: 12px;");
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setTooltip(null);
                                } else {
                                    setText(item);
                                    switch (item) {
                                        case "bypass" ->
                                                tooltip.setText(
                                                        "Bypass Mode — Directly allow all tools, no"
                                                            + " security checks (suitable for fully"
                                                            + " trusted environments)");
                                        case "default" ->
                                                tooltip.setText(
                                                        "Default Mode — Requires user approval"
                                                            + " before executing dangerous tools"
                                                            + " (shell, file writing, etc.)");
                                        case "accept_edits" ->
                                                tooltip.setText(
                                                        "Accept Edits — Auto-allow read-only tools,"
                                                            + " and auto-allow file edits within"
                                                            + " the working directory");
                                        case "explore" ->
                                                tooltip.setText(
                                                        "Explore Mode — Read-only mode, directly"
                                                                + " deny all modifying tools (most"
                                                                + " secure)");
                                        case "dont_ask" ->
                                                tooltip.setText(
                                                        "Unattended — Auto-downgrade ASK decisions"
                                                                + " to DENY (suitable for automated"
                                                                + " scenarios)");
                                        default -> tooltip.setText("");
                                    }
                                    setTooltip(tooltip);
                                }
                            }
                        });
        // Move up after ComboBox pops up
        permissionModeBox
                .showingProperty()
                .addListener(
                        (obs, old, showing) -> {
                            if (showing) {
                                javafx.application.Platform.runLater(
                                        () -> {
                                            Bounds bounds =
                                                    permissionModeBox.localToScreen(
                                                            permissionModeBox.getBoundsInLocal());
                                            if (bounds == null) return;
                                            for (Window w : Window.getWindows()) {
                                                if (w instanceof Popup) {
                                                    w.setX(bounds.getMaxX() - w.getWidth());
                                                    w.setY(bounds.getMinY() - w.getHeight() - 2);
                                                    break;
                                                }
                                            }
                                        });
                            }
                        });
        permissionModeBox
                .valueProperty()
                .addListener(
                        (obs, oldVal, newVal) -> {
                            if (currentAgent != null && newVal != null) {
                                ai.emailclaw.emailclaw.model.AgentConfiguration config =
                                        chatService
                                                .repository()
                                                .loadAgentConfig(currentAgent.getId());
                                if (config != null) {
                                    config.setPermissionMode(newVal);
                                    chatService
                                            .repository()
                                            .saveAgentConfig(currentAgent.getId(), config);
                                    LOGGER.log(
                                            Level.INFO,
                                            "PermissionMode changed: {0} -> {1} (agent={2})",
                                            new Object[] {oldVal, newVal, currentAgent.getId()});
                                }
                            }
                        });
        Label permLabel = new Label("Permission:");
        javafx.scene.control.Tooltip permTooltip =
                new javafx.scene.control.Tooltip(
                        "bypass — All tools directly allowed, no security checks\n"
                            + "default — Requires user approval before executing dangerous tools\n"
                            + "accept_edits — Auto-allow read-only tools, and auto-allow file edits"
                            + " within the working directory\n"
                            + "explore — Read-only mode, directly deny all modifying tools\n"
                            + "dont_ask — ASK decisions auto-downgraded to DENY (unattended)");
        permTooltip.setStyle("-fx-font-size: 12px;");
        javafx.scene.control.Tooltip.install(permLabel, permTooltip);
        HBox levelRow = new HBox(4, permLabel, permissionModeBox);
        levelRow.setAlignment(Pos.CENTER_LEFT);
        action.getChildren().addAll(attachBtn, actionSpacer, levelRow, counter, sendBtn);
        sendBtn.setOnAction(e -> handleSendOrStop());
        composer.getChildren().addAll(input, attachmentRow, action);
        composer.getStyleClass().add("composer");
        initTaskBar();
        javafx.scene.layout.VBox bottomContainer =
                new javafx.scene.layout.VBox(0, cronJobContainer, taskBar, composer);
        BorderPane chatPane = new BorderPane();
        chatPane.setPadding(new Insets(14));
        chatPane.getStyleClass().add("bg-f6");
        chatPane.setTop(top);
        chatPane.setCenter(webViewContainer);
        chatPane.setBottom(bottomContainer);
        chatSearchPanel = new ChatSearchPanel(chatService);
        chatSearchPanel.setAgentId(
                currentAgent.getId(),
                currentSession != null
                        ? currentSession.getKind()
                        : ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_CHAT);
        chatSearchPanel.setOnSessionSelected(this::loadSession);
        chatSearchPanel.setOnClose(() -> {});
        VBox searchDrawer = chatSearchPanel.root();
        searchDrawer.maxHeightProperty().bind(chatPane.heightProperty());
        StackPane chatStack = new StackPane();
        chatStack.getChildren().addAll(chatPane, searchDrawer);
        StackPane.setAlignment(searchDrawer, Pos.CENTER_RIGHT);
        mainSplitPane = new SplitPane();
        mainSplitPane.getStyleClass().add("bg-f6");
        codingModePane.build();
        mainSplitPane.getItems().add(chatStack);
        root.setCenter(mainSplitPane);
    }

    /**
     * Open right full session search drawer, aligning with Emailclaw Search all chats entry.
     */
    private void openChatSearchPanel() {
        if (chatSearchPanel == null || currentAgent == null) {
            return;
        }
        chatSearchPanel.setAgentId(currentAgent.getId(), currentSession.getKind());
        chatSearchPanel.open();
    }

    private void styleToolbarButton(Button button, boolean primary) {
        String base =
                "-fx-background-radius: 7;"
                        + "-fx-border-radius: 7;"
                        + "-fx-font-size: 12px;"
                        + "-fx-font-weight: 600;"
                        + "-fx-padding: 7 12;"
                        + "-fx-cursor: hand;";
        if (primary) {
            button.setStyle(
                    base
                            + "-fx-background-color: #1f5eff;"
                            + "-fx-border-color: #1f5eff;"
                            + "-fx-text-fill: #ffffff;");
            return;
        }
        button.setStyle(
                base
                        + "-fx-background-color: #ffffff;"
                        + "-fx-border-color: #d6dde8;"
                        + "-fx-text-fill: #334155;");
    }

    public void toggleCodingMode() {
        if (!codingModeActive) {
            if (codingModePane.getProjectRoot() == null
                    || !Files.exists(codingModePane.getProjectRoot())) {
                if (!codingModePane.showSelectProjectDialog(
                        webView.getScene() != null ? webView.getScene().getWindow() : null)) {
                    return;
                }
            }
            applyCodingModeUi(true);
        } else {
            applyCodingModeUi(false);
        }
        persistCodingModeState();
    }

    public boolean isCodingModeVisible() {
        return codingModeActive;
    }

    private void applyCodingModeUi(boolean active) {
        codingModeActive = active;
        if (active) {
            if (!mainSplitPane.getItems().contains(codingModePane.getPane())) {
                mainSplitPane.getItems().add(0, codingModePane.getPane());
                mainSplitPane.setDividerPositions(0.7);
            }
            codingModePane.refreshTree();
        } else if (mainSplitPane.getItems().contains(codingModePane.getPane())) {
            mainSplitPane.getItems().remove(codingModePane.getPane());
        }
    }

    private void restoreCodingModeState() {
        try {
            AgentConfiguration cfg = chatService.repository().loadAgentConfig(currentAgent.getId());
            codingModePane.restoreState(cfg);
            if (cfg.isCodingModeEnabled()) {
                if (codingModePane.getProjectRoot() == null
                        || !Files.exists(codingModePane.getProjectRoot())) {
                    applyCodingModeUi(false);
                    cfg.setCodingModeEnabled(false);
                    chatService.repository().saveAgentConfig(currentAgent.getId(), cfg);
                    return;
                }
                applyCodingModeUi(true);
            } else {
                applyCodingModeUi(false);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to restore Coding Mode state", ex);
            applyCodingModeUi(false);
        }
    }

    private void persistCodingModeState() {
        try {
            AgentConfiguration cfg = chatService.repository().loadAgentConfig(currentAgent.getId());
            cfg.setCodingModeEnabled(codingModeActive);
            codingModePane.persistState(cfg);
            chatService.repository().saveAgentConfig(currentAgent.getId(), cfg);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to save Coding Mode state", ex);
        }
    }

    private void resolveDefaultModel() {
        LOGGER.log(
                Level.FINE,
                "Entering resolveDefaultModel, current Agent ID: {0}",
                currentAgent.getId());
        // 1. Try to restore the previously saved provider and model from the current Agent's
        // configuration
        if (currentAgent.getProviderId() != null && !currentAgent.getProviderId().isBlank()) {
            LOGGER.log(
                    Level.FINE, "Persisted providerId detected: {0}", currentAgent.getProviderId());
            selectedProvider = providerService.getById(currentAgent.getProviderId()).orElse(null);
            if (selectedProvider != null && currentAgent.getModelId() != null) {
                LOGGER.log(
                        Level.FINE, "Persisted modelId detected: {0}", currentAgent.getModelId());
                selectedModel =
                        selectedProvider.allModels().stream()
                                .filter(m -> m.getId().equals(currentAgent.getModelId()))
                                .findFirst()
                                .orElse(null);
                if (selectedModel != null) {
                    LOGGER.log(
                            Level.FINE,
                            "Successfully restored saved model: Provider=[{0}], Model=[{1}]",
                            new Object[] {selectedProvider.getName(), selectedModel.getName()});
                } else {
                    LOGGER.log(
                            Level.WARNING,
                            "Cannot find configuration for persisted model ID [{0}], will attempt"
                                    + " fallback",
                            currentAgent.getModelId());
                }
            }
        }
        // 2. If no saved configuration, or if model cannot be found based on saved configuration,
        // use fallback strategy
        if (selectedProvider == null || selectedModel == null) {
            LOGGER.fine(
                    "No valid saved model configuration found, executing fallback strategy to find"
                            + " an available model...");
            selectedModel = providerService.getDefaultModel();
        }
        // 3. Display the name of the finally selected model on the button in the upper right corner
        // of the interface
        if (selectedModel != null) {
            // Only derive the provider from the model when there is no resolved provider, or when
            // the model's explicit providerId disagrees with the resolved provider (fallback path).
            // Catalog models (ProviderCatalog) never set their providerId, so blindly overwriting
            // selectedProvider here would null a valid provider resolved from the agent's persisted
            // configuration.
            String modelProviderId = selectedModel.getProviderId();
            if (selectedProvider == null
                    || (modelProviderId != null
                            && !modelProviderId.isBlank()
                            && !modelProviderId.equals(selectedProvider.getId()))) {
                selectedProvider = providerService.getById(modelProviderId).orElse(null);
            }
            selectModelBtn.setText("Model: " + selectedModel.getName());
        } else {
            selectModelBtn.setText("Select model");
        }
        LOGGER.log(
                Level.INFO,
                "resolveDefaultModel result: selectedProvider={0}, selectedModel={1}",
                new Object[] {
                    selectedProvider == null
                            ? "null"
                            : selectedProvider.getId() + "/" + selectedProvider.getName(),
                    selectedModel == null
                            ? "null"
                            : selectedModel.getId() + "/" + selectedModel.getName()
                });
        updateAttachmentTooltip();
    }

    private boolean sessionMatchesProject(ChatSessionInfo session, ProjectInfo project) {
        if (session == null) {
            return false;
        }
        String sProj = session.getProjectId().isBlank() ? "default" : session.getProjectId();
        String currentProj = project.getId().isBlank() ? "default" : project.getId();
        return currentProj.equals(sProj);
    }

    private void ensureSession() {
        if (currentSession != null
                && currentAgent != null
                && currentAgent.getId().equals(currentSession.getAgentId())
                && sessionMatchesProject(currentSession, currentProject)) {
            return;
        }
        List<ChatSessionInfo> sessions =
                chatService.sessions(currentAgent.getId()).stream()
                        .filter(s -> sessionKind.equals(s.getKind()))
                        .filter(s -> sessionMatchesProject(s, currentProject))
                        .toList();
        if (sessions.isEmpty()) {
            currentSession =
                    chatService.newSession(
                            currentAgent.getId(),
                            sessionKind,
                            defaultSessionTitle(),
                            currentProject.getId());
        } else {
            currentSession = sessions.getFirst();
        }
    }

    public ChatSessionInfo getCurrentSession() {
        return currentSession;
    }

    // Model selection logic extracted to ModelSelectionComponent

    private void chooseAttachments() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Upload file");
        List<File> files = chooser.showOpenMultipleDialog(selectModelBtn.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            attachments.clear();
            int skipped = 0;
            for (File file : files) {
                if (file.length() > CHAT_ATTACHMENT_MAX_BYTES) {
                    skipped++;
                    continue;
                }
                attachments.add(file.toPath().toAbsolutePath().normalize());
            }
            if (skipped > 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("File too large");
                alert.setHeaderText("Some files were not added");
                alert.setContentText("Maximum file size is 10MB, skipped " + skipped + " files.");
                alert.initOwner(selectModelBtn.getScene().getWindow());
                alert.showAndWait();
            }
            attachmentStatus.setText("Attachments: " + attachments.size() + " files");
        }
    }

    /**
     * Handles send/stop button click events.
     *
     * <p>
     * Determines behavior based on whether it is currently sending:
     * <ul>
     * <li>Not sending state -> Execute {@link #sendCurrent()} to initiate a new dialogue</li>
     * <li>Sending state -> Execute {@link #stopCurrent()} to interrupt the dialogue stream</li>
     * </ul>
     */
    private void handleSendOrStop() {
        LOGGER.log(
                Level.INFO,
                "handleSendOrStop called, isSending={0}, selectedProvider={1}, selectedModel={2},"
                    + " agent.providerId={3}, agent.modelId={4}, text.length={5}, attachments={6},"
                    + " sessionId={7}, activeSendThread={8}",
                new Object[] {
                    isSending,
                    selectedProvider == null
                            ? "null"
                            : selectedProvider.getId() + "/" + selectedProvider.getName(),
                    selectedModel == null
                            ? "null"
                            : selectedModel.getId() + "/" + selectedModel.getName(),
                    currentAgent == null ? "null" : currentAgent.getProviderId(),
                    currentAgent == null ? "null" : currentAgent.getModelId(),
                    input.getText() == null ? 0 : input.getText().trim().length(),
                    attachments.size(),
                    currentSession == null ? "null" : currentSession.getId(),
                    activeSendThread == null
                            ? "null"
                            : activeSendThread.getName() + " alive=" + activeSendThread.isAlive()
                });
        if (isSending) {
            stopCurrent();
        } else {
            sendCurrent();
        }
    }

    /**
     * Interrupts the currently ongoing dialogue stream.
     *
     * <p>
     * Cancels blockLast() blocking wait by interrupting the sending virtual thread, the interrupted thread will perform cleanup and UI reset operations in the catch block.
     */
    private void stopCurrent() {
        LOGGER.log(Level.INFO, "User requested to stop the current dialogue stream");
        Thread sendThread = activeSendThread;
        if (sendThread != null && sendThread.isAlive()) {
            LOGGER.log(Level.INFO, "Interrupting sending thread: {0}", sendThread.getName());
            sendThread.interrupt();
        } else {
            LOGGER.fine("No active sending thread needs to be interrupted, directly resetting UI");
            resetSendUiAfterSend();
        }
    }

    /**
     * Switches sendBtn to "Stop" mode (red background, text changed to Stop).
     *
     * <p>
     * This method should be called on the JavaFX Application Thread.
     */
    private void switchToStopMode() {
        LOGGER.fine("sendBtn switched to Stop mode");
        isSending = true;
        sendBtn.setText("Stop");
        String stopStyle =
                "-fx-background-radius: 7;"
                        + "-fx-border-radius: 7;"
                        + "-fx-font-size: 12px;"
                        + "-fx-font-weight: 600;"
                        + "-fx-padding: 7 12;"
                        + "-fx-cursor: hand;"
                        + "-fx-background-color: #dc2626;"
                        + "-fx-border-color: #dc2626;"
                        + "-fx-text-fill: #ffffff;";
        sendBtn.setStyle(stopStyle);
        sendBtn.setDisable(false);
        if (currentSession != null
                && ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK.equals(
                        currentSession.getKind())) {
            currentSession.setStatus(
                    ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus.RUNNING);
            chatService.updateSession(currentSession);
            updateTaskBar();
        }
    }

    /**
     * Restores sendBtn to "Send" mode (blue background, text changed to Send).
     *
     * <p>
     * This method should be called on the JavaFX Application Thread.
     */
    private void switchToSendMode() {
        LOGGER.fine("sendBtn restored to Send mode");
        isSending = false;
        activeSendThread = null;
        sendBtn.setText("Send");
        styleToolbarButton(sendBtn, true);
        sendBtn.setDisable(false);
        if (currentSession != null
                && ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK.equals(
                        currentSession.getKind())) {
            if (ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus.RUNNING
                    == currentSession.getStatus()) {
                currentSession.setStatus(
                        ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus.ACTIVE);
                chatService.updateSession(currentSession);
                updateTaskBar();
            }
        }
    }

    private void sendCurrent() {
        String text = input.getText().trim();
        if (isSending) {
            LOGGER.log(
                    Level.WARNING,
                    "Send ignored: current dialogue is in progress (isSending=true,"
                            + " activeSendThread="
                            + (activeSendThread == null
                                    ? "null"
                                    : activeSendThread.getName()
                                            + " alive="
                                            + activeSendThread.isAlive())
                            + ")");
            return;
        }
        if ((text.isBlank() && attachments.isEmpty())
                || selectedProvider == null
                || selectedModel == null) {
            LOGGER.log(
                    Level.WARNING,
                    "Send ignored: text.blank={0}, attachments.empty={1}, selectedProvider={2},"
                            + " selectedModel={3}",
                    new Object[] {
                        text.isBlank(), attachments.isEmpty(), selectedProvider, selectedModel
                    });
            return;
        }
        LOGGER.log(
                Level.INFO,
                "Started sending chat message: agent={0}, provider={1}, model={2}",
                new Object[] {
                    currentAgent.getId(), selectedProvider.getId(), selectedModel.getId()
                });
        String textWithAttachments = text;
        if (!attachments.isEmpty()) {
            StringBuilder attachText = new StringBuilder(text);
            attachText.append("\n\n[Attachments: ");
            for (int i = 0; i < attachments.size(); i++) {
                // Not just a simple name getFileName()
                attachText.append(attachments.get(i).toAbsolutePath().toString());
                if (i < attachments.size() - 1) {
                    attachText.append(", ");
                }
            }
            attachText.append("]");
            textWithAttachments = attachText.toString();
        }
        input.clear();
        // Switch sendBtn to red "Stop" mode
        switchToStopMode();
        messages.add(
                new ChatMessageRecord(
                        ChatMessageRoles.USER,
                        List.of(ChatMessagePart.text(textWithAttachments)),
                        LocalDateTime.now().toString()));
        messages.add(
                new ChatMessageRecord(
                        ChatMessageRoles.ASSISTANT,
                        new ArrayList<>(),
                        LocalDateTime.now().toString()));
        ChatMessageRecord assistantRecord = messages.get(messages.size() - 1);
        String sendingSessionId = currentSession == null ? "" : currentSession.getId();
        SendStreamCallback streamCallback =
                new SendStreamCallback(sendingSessionId, assistantRecord);
        activeStreamCallback = streamCallback;
        renderMessages();
        // Start virtual thread to send message, and save thread reference to support Stop
        // interruption
        final String sendText = text;
        Thread sendThread =
                Thread.startVirtualThread(
                        () -> {
                            try {
                                chatService.sendMessage(
                                        currentAgent,
                                        selectedProvider,
                                        selectedModel.getId(),
                                        currentSession,
                                        sendText,
                                        List.copyOf(attachments),
                                        streamCallback);
                            } catch (Throwable e) {
                                // Check if exception is caused by user active interruption
                                boolean interrupted =
                                        (e instanceof InterruptedException)
                                                || Thread.currentThread().isInterrupted();
                                if (interrupted) {
                                    LOGGER.log(Level.INFO, "Dialogue stream interrupted by user");
                                } else {
                                    LOGGER.log(
                                            Level.SEVERE,
                                            "Exception occurred in chat sending thread",
                                            e);
                                }
                                Platform.runLater(
                                        () -> {
                                            if (isCurrentSendingSession(sendingSessionId)) {
                                                if (interrupted) {
                                                    streamCallback.appendError(
                                                            "[Stopped] Dialogue has been"
                                                                    + " interrupted by user");
                                                } else {
                                                    streamCallback.appendError(
                                                            "[Error] "
                                                                    + e.getClass().getSimpleName()
                                                                    + ": "
                                                                    + e.getMessage());
                                                }
                                                renderMessages();
                                            }
                                            resetSendUiAfterSend();
                                        });
                            } finally {
                                Platform.runLater(this::resetSendUiAfterSend);
                            }
                        });
        activeSendThread = sendThread;
        LOGGER.log(Level.FINE, "Sending virtual thread started: {0}", sendThread.getName());
    }

    private boolean isCurrentSendingSession(String sendingSessionId) {
        return currentSession != null
                && currentSession.getId() != null
                && currentSession.getId().equals(sendingSessionId);
    }

    /**
     * Resets sending area UI state after dialogue completes or is interrupted.
     *
     * <p>
     * Restores sendBtn to blue "Send" mode, clears attachment list. This method must be called on JavaFX Application Thread.
     */
    private void resetSendUiAfterSend() {
        LOGGER.fine("Resetting sending UI state");
        switchToSendMode();
        attachments.clear();
        attachmentStatus.setText("No attachments");
    }

    private void updateAttachmentTooltip() {
        if (selectedModel == null) {
            attachTooltip.setText("No model currently selected. Maximum single file size 10MB.");
            return;
        }
        boolean supportsImage = selectedModel.isSupportsImage();
        boolean supportsVideo = selectedModel.isSupportsVideo();
        if (supportsImage && supportsVideo) {
            attachTooltip.setText(
                    "Current model supports multimodal (image/video). Maximum single file size"
                            + " 10MB.");
            return;
        }
        if (supportsImage) {
            attachTooltip.setText(
                    "Current model only supports image multimodal, uploaded videos may not be"
                            + " processed. Maximum single file size 10MB.");
            return;
        }
        attachTooltip.setText(
                "No multimodal capabilities detected for current model, images or videos may not be"
                        + " processed correctly. Maximum single file size 10MB.");
    }

    /**
     * Named callback class, to avoid anonymous inner class numbering changing during incremental compilation causing ClassNotFound/NoClassDefFoundError.
     */
    private final class SendStreamCallback implements StreamCallback {

        private final String sendingSessionId;

        private ChatMessageRecord currentAssistant;

        private SendStreamCallback(String sendingSessionId, ChatMessageRecord assistantRecord) {
            this.sendingSessionId = sendingSessionId;
            this.currentAssistant = assistantRecord;
        }

        @Override
        public void onPart(ChatMessagePart part, boolean startsNew) {
            Platform.runLater(
                    () -> {
                        if (!isCurrentSendingSession(sendingSessionId)) {
                            return;
                        }
                        appendStructuredPart(part, startsNew);
                        renderMessages();
                    });
        }

        @Override
        public void onCompleted(Msg completedMessage) {
            Platform.runLater(
                    () -> {
                        if (isCurrentSendingSession(sendingSessionId)) {
                            // Convert the Msg received by onCompleted into a ChatMessageRecord for
                            // display
                            String role = chatService.roleOf(completedMessage);
                            List<ChatMessagePart> parts = chatService.partsOf(completedMessage);
                            if (currentAssistant != null && messages.contains(currentAssistant)) {
                                int idx = messages.indexOf(currentAssistant);
                                currentAssistant =
                                        new ChatMessageRecord(
                                                role, parts, completedMessage.getTimestamp());
                                messages.set(idx, currentAssistant);
                            }
                            removeCurrentAssistantIfBlank();
                            if (activeStreamCallback == this) {
                                activeStreamCallback = null;
                            }
                            chatService.touchSession(currentSession);
                            // Refresh session title immediately after applying [TITLE: xxx]
                            // returned by LLM
                            // Rely on EVENT_SESSIONS listener to refresh the title, just touch it
                            // here
                            renderMessages();
                        }
                        resetSendUiAfterSend();
                    });
        }

        private void appendStructuredPart(ChatMessagePart part, boolean startsNew) {
            if (part == null) {
                return;
            }
            if (currentAssistant == null || !messages.contains(currentAssistant)) {
                currentAssistant =
                        new ChatMessageRecord(
                                ChatMessageRoles.ASSISTANT,
                                new ArrayList<>(),
                                LocalDateTime.now().toString());
                messages.add(currentAssistant);
            }
            if (currentAssistant.getParts() == null) {
                currentAssistant.setParts(new ArrayList<>());
            }
            ChatMessagePart incoming = part.copy();
            if (startsNew || currentAssistant.getParts().isEmpty()) {
                currentAssistant.getParts().add(incoming);
            } else {
                ChatMessagePart last = currentAssistant.getParts().getLast();
                if (last.sameStreamTarget(incoming)) {
                    last.append(incoming.getText());
                } else {
                    currentAssistant.getParts().add(incoming);
                }
            }
        }

        private void appendError(String errorText) {
            appendStructuredPart(
                    ChatMessagePart.block(ChatMessagePart.ERROR, "ERROR", errorText), true);
        }

        private void startNewAssistantSegmentAfter(ChatMessageRecord anchor) {
            if (!isCurrentSendingSession(sendingSessionId) || anchor == null) {
                return;
            }
            removeCurrentAssistantIfBlank();
            ChatMessageRecord next =
                    new ChatMessageRecord(
                            ChatMessageRoles.ASSISTANT,
                            new ArrayList<>(),
                            LocalDateTime.now().toString());
            int anchorIndex = messages.indexOf(anchor);
            if (anchorIndex >= 0 && anchorIndex + 1 <= messages.size()) {
                messages.add(anchorIndex + 1, next);
            } else {
                messages.add(next);
            }
            currentAssistant = next;
        }

        private void removeCurrentAssistantIfBlank() {
            if (currentAssistant != null
                    && ChatMessageRoles.ASSISTANT.equals(currentAssistant.getRole())
                    && currentAssistant.isBlank()) {
                messages.remove(currentAssistant);
            }
        }
    }

    /**
     * Debounce wrapper method: batches high-frequency calls (such as streaming token arrival) to avoid WebView frequent full HTML rebuild.
     *
     * <p>
     * Works on FX thread: first call will delay actual rendering via {@link Platform#runLater},
     * subsequent calls arriving during the delay will be merged. Non-streaming scenarios (such as session switching) should directly call {@link #doRenderMessages()}.
     */
    private void renderMessages() {
        if (!renderScheduled) {
            renderScheduled = true;
            Platform.runLater(
                    () -> {
                        renderScheduled = false;
                        doRenderMessages();
                    });
        }
    }

    /**
     * Executes actual message rendering: builds all messages into a complete HTML page and loads it into WebView.
     *
     * <p>
     * This method should be called directly on the FX thread (suitable for scenarios requiring immediate rendering, like session switching), or indirectly via {@link #renderMessages()} (suitable for high-frequency refreshes during streaming).
     */
    private void doRenderMessages() {
        LOGGER.log(Level.FINE, "Start rendering {0} messages to WebView", messages.size());
        StringBuilder html = new StringBuilder();
        html.append("<html><head>\n");
        html.append("<meta charset='UTF-8'/>\n");
        html.append("<style>\n");
        html.append(CHAT_WEBVIEW_CSS);
        html.append("</style>\n");
        html.append("<script>\n");
        html.append(
                "function scrollToBottom() { window.scrollTo(0, document.body.scrollHeight); }\n");
        html.append("function toggleExpand(el) {\n");
        html.append("  var inner = el.querySelector('.expandable-inner');\n");
        html.append("  var icon = el.querySelector('.expand-icon');\n");
        html.append("  if (inner.classList.contains('collapsed')) {\n");
        html.append(
                "    inner.classList.remove('collapsed'); inner.classList.add('expanded');"
                        + " icon.innerHTML = '▲';\n");
        html.append("  } else {\n");
        html.append(
                "    inner.classList.remove('expanded'); inner.classList.add('collapsed');"
                        + " icon.innerHTML = '▼';\n");
        html.append("  }\n");
        html.append("}\n");
        html.append("window.onload = scrollToBottom;\n");
        // File diff panel expand/collapse toggle
        html.append("function toggleDiff(btn) {\n");
        html.append("  var panel = btn.parentElement;\n");
        html.append("  var body = panel.querySelector('.diff-body');\n");
        html.append("  var icon = btn.querySelector('.diff-toggle-icon');\n");
        html.append("  if (body.classList.contains('diff-collapsed')) {\n");
        html.append("    body.classList.remove('diff-collapsed'); icon.innerHTML = '▲';\n");
        html.append("  } else {\n");
        html.append("    body.classList.add('diff-collapsed'); icon.innerHTML = '▼';\n");
        html.append("  }\n");
        html.append("}\n");
        // Copy artifact path with temporary visual feedback
        html.append("function copyPath(btn, path) {\n");
        html.append("  if (window.javaInterop && window.javaInterop.copyArtifactPath) {\n");
        html.append("    window.javaInterop.copyArtifactPath(path);\n");
        html.append("  }\n");
        html.append("  var orig = btn.innerHTML;\n");
        html.append(
                "  btn.innerHTML = '<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\""
                    + " fill=\"none\" stroke=\"#059669\" stroke-width=\"2.5\""
                    + " stroke-linecap=\"round\" stroke-linejoin=\"round\""
                    + " style=\"vertical-align:-1px; margin-right:3px;\"><polyline points=\"20 6 9"
                    + " 17 4 12\"/></svg>Copied!';\n");
        html.append("  btn.style.borderColor = '#10b981';\n");
        html.append("  btn.style.color = '#059669';\n");
        html.append("  setTimeout(function() {\n");
        html.append("    btn.innerHTML = orig;\n");
        html.append("    btn.style.borderColor = '';\n");
        html.append("    btn.style.color = '';\n");
        html.append("  }, 2000);\n");
        html.append("}\n");
        html.append("</script>\n");
        html.append("</head><body>\n");
        html.append("<main class='timeline'>\n");
        if (messages.isEmpty()) {
            html.append("<div class='empty-state'>Hello, how can I help you today?</div>\n");
        } else {
            for (ChatMessageRecord m : messages) {
                String rawRole =
                        m.getRole() == null
                                ? ChatMessageRoles.ASSISTANT
                                : m.getRole().toLowerCase();
                String safeRole = rawRole;
                if (!ChatMessageRoles.ALL_KNOWN.contains(safeRole)) {
                    safeRole = ChatMessageRoles.ASSISTANT;
                }
                html.append("<div class='msg-container msg-container-")
                        .append(safeRole)
                        .append("' title='")
                        .append(escape(m.getCreatedAt()))
                        .append("'>");
                html.append("<div class='msg msg-").append(safeRole).append("'>");
                if (ChatMessageRoles.APPROVAL.equals(safeRole)) {
                    renderApprovalMessage(html, m.effectiveContent());
                } else if (ChatMessageRoles.SYSTEM.equals(safeRole)) {
                    html.append("<div class='content content-system'>")
                            .append(escape(m.effectiveContent()))
                            .append("</div>");
                } else if (!ChatMessageRoles.ASSISTANT.equals(safeRole)) {
                    html.append("<div class='role role-")
                            .append(safeRole)
                            .append("'>")
                            .append(escape(m.getRole()))
                            .append("</div>");
                    String contentText = m.effectiveContent();
                    if (contentText == null || contentText.isBlank()) {
                        contentText = "(No content)";
                    }
                    html.append("<div class='content content-")
                            .append(safeRole)
                            .append("'>")
                            .append(escape(contentText))
                            .append("</div>");
                } else {
                    html.append("<div class='role role-")
                            .append(safeRole)
                            .append("'>")
                            .append(escape(m.getRole()))
                            .append("</div>");
                    for (ChatMessagePart part : displayParts(m)) {
                        renderAssistantPart(html, part);
                    }
                }
                html.append("</div></div>\n");
            }
        }
        html.append("</main>\n");
        html.append("<script>scrollToBottom();</script>\n");
        html.append("</body></html>");
        // Log generated HTML size for troubleshooting performance issues
        String htmlContent = html.toString();
        LOGGER.log(Level.FINE, "HTML generation complete, size: {0} bytes", htmlContent.length());
        webView.getEngine().loadContent(htmlContent);
    }

    private List<ChatMessagePart> displayParts(ChatMessageRecord record) {
        if (record == null) {
            return List.of();
        }
        if (record.getParts() != null && !record.getParts().isEmpty()) {
            return record.getParts();
        }
        return List.of();
    }

    /**
     * Renders a single structured segment of the assistant message.
     *
     * <p>
     * For tool result types, parses {@code <<<FILE_DIFF>>>} tag blocks and renders them as expandable diff panels,
     * allowing users to visually see which lines were added or deleted in file modifications.
     */
    private void renderAssistantPart(StringBuilder html, ChatMessagePart part) {
        if (part == null || part.getText() == null || part.getText().trim().isEmpty()) {
            return;
        }
        String type = ChatMessagePart.normalizeType(part.getType());
        if (ChatMessagePart.TEXT.equals(type)) {
            html.append("<div class='content content-assistant'>")
                    .append(markdownToHtml(part.getText()))
                    .append("</div>");
            return;
        }

        if (ChatMessagePart.SUB_AGENT_EVENT.equals(type)) {
            String titleText =
                    "🤖 Sub-Agent executing: "
                            + (part.getTitle() == null || part.getTitle().isBlank()
                                    ? "Sub-Agent"
                                    : part.getTitle());
            html.append(
                    "<div class='content content-sub_agent_event expandable-container'"
                            + " onclick='toggleExpand(this)'>");
            html.append("<div class='block-head'>");
            html.append("<span class='block-title'>").append(escape(titleText)).append("</span>");
            html.append("<span class='expand-icon'>▼</span>");
            html.append("</div>");
            html.append(
                    "<div class='block-body expandable-inner collapsed' style='padding-left:16px;"
                            + " border-left:2px solid #ccc;'>");
            // Recursive rendering
            if (part.getSubParts() != null) {
                for (ChatMessagePart subPart : part.getSubParts()) {
                    renderAssistantPart(html, subPart);
                }
            }
            html.append("</div>");
            html.append("</div>");
            return;
        }
        String cssClass =
                switch (type) {
                    case ChatMessagePart.THINKING -> "thinking";
                    case ChatMessagePart.TOOL_CALL -> "tool_call";
                    case ChatMessagePart.TOOL_RESULT -> "tool_result";
                    case ChatMessagePart.HINT -> "hint";
                    case ChatMessagePart.ERROR -> "error";
                    case ChatMessagePart.SUB_AGENT_EVENT -> "sub_agent_event";
                    default -> "assistant";
                };
        if ("assistant".equals(cssClass)) {
            html.append("<div class='content content-assistant'>")
                    .append(markdownToHtml(part.getText()))
                    .append("</div>");
            return;
        }
        // If this part is a successful deliver_artifact result, render the in-place Deliverable
        // Card (Scheme A)
        if (isDeliverArtifactSuccessResult(part)) {
            renderDeliverArtifactCard(html, part);
            return;
        }
        // Extract file diff markup block from tool result (<<<FILE_DIFF>>> ... <<<END_FILE_DIFF>>>)
        String bodyText = part.getText().trim();
        String diffMarkup = "";
        String displayText = bodyText;
        int diffStart = bodyText.indexOf("<<<FILE_DIFF>>>");
        if (diffStart >= 0) {
            int diffEnd = bodyText.indexOf("<<<END_FILE_DIFF>>>");
            if (diffEnd > diffStart) {
                diffMarkup =
                        bodyText.substring(diffStart + "<<<FILE_DIFF>>>".length(), diffEnd).trim();
                // Remove diff markup block from main text, keeping only tool execution result
                // summary
                displayText =
                        (bodyText.substring(0, diffStart)
                                        + bodyText.substring(
                                                diffEnd + "<<<END_FILE_DIFF>>>".length()))
                                .trim();
            }
        }
        String titleText =
                part.getTitle() == null || part.getTitle().isBlank()
                        ? type.toUpperCase()
                        : part.getTitle();
        html.append("<div class='content content-")
                .append(cssClass)
                .append(" expandable-container' onclick='toggleExpand(this)'>");
        html.append("<div class='block-head'>");
        html.append("<span class='block-title'>").append(escape(titleText)).append("</span>");
        html.append("<span class='expand-icon'>▼</span>");
        html.append("</div>");
        html.append("<div class='block-body expandable-inner collapsed'>");
        html.append(escape(displayText));
        html.append("</div>");

        if (part.isOffloaded()) {
            html.append("<div class='offload-action' style='margin-top:8px;'>");
            html.append("<button onclick=\"javaInterop.viewOffloadedFile('")
                    .append(escape(part.getOffloadPath().replace("\\", "\\\\")))
                    .append(
                            "')\" style='background-color:#4a90e2; color:white; border:none;"
                                    + " padding:4px 12px; border-radius:4px; cursor:pointer;'>")
                    .append("📄 View full result details")
                    .append("</button>");
            html.append("</div>");
        }

        // If file diff exists, render expandable diff panel
        if (!diffMarkup.isEmpty()) {
            renderFileDiffPanel(html, diffMarkup);
        }
        html.append("</div>");
    }

    /**
     * Parse the {@code <<<FILE_DIFF>>>} markup block and render it as an expandable HTML diff panel.
     *
     * <p>
     * Parse {@code FILE:}, {@code ADDED:}, {@code DELETED:}, {@code NEW_FILE:} metadata lines,
     * and the unified diff content after the {@code ---} separator, converting it into a diff panel with line-level syntax highlighting.
     */
    private void renderFileDiffPanel(StringBuilder html, String diffMarkup) {
        String[] lines = diffMarkup.split("\n", -1);
        String filePath = "";
        int added = 0;
        int deleted = 0;
        boolean isNew = false;
        int contentStart = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("FILE: ")) {
                filePath = line.substring("FILE: ".length()).trim();
            } else if (line.startsWith("ADDED: ")) {
                try {
                    added = Integer.parseInt(line.substring("ADDED: ".length()).trim());
                } catch (NumberFormatException ignored) {
                }
            } else if (line.startsWith("DELETED: ")) {
                try {
                    deleted = Integer.parseInt(line.substring("DELETED: ".length()).trim());
                } catch (NumberFormatException ignored) {
                }
            } else if (line.startsWith("NEW_FILE: ")) {
                isNew = "true".equalsIgnoreCase(line.substring("NEW_FILE: ".length()).trim());
            } else if ("---".equals(line)) {
                contentStart = i + 1;
                break;
            }
        }
        String safeFile = escape(filePath);
        String safePathShort =
                filePath.length() > 40
                        ? escape(filePath.substring(filePath.length() - 37) + "...")
                        : safeFile;
        html.append("<div class='diff-panel'>");
        html.append("<div class='diff-header' onclick='toggleDiff(this)'>");
        html.append("<div class='diff-stats'>");
        html.append("<span class='diff-stat-add'>+").append(added).append("</span>");
        html.append("<span class='diff-stat-del'>-").append(deleted).append("</span>");
        if (isNew) {
            html.append("<span class='diff-badge-new'>NEW</span>");
        }
        html.append("<span class='diff-stat-file' title='")
                .append(safeFile)
                .append("'>")
                .append(safePathShort)
                .append("</span>");
        html.append("</div>");
        html.append("<span class='diff-toggle-icon'>▼</span>");
        html.append("</div>");
        html.append("<div class='diff-body diff-collapsed'>");
        if (contentStart >= 0 && contentStart < lines.length) {
            for (int i = contentStart; i < lines.length; i++) {
                String raw = lines[i];
                String trimmed = raw.length() > 0 ? raw.substring(0, 1) : "";
                String cssLine;
                switch (trimmed) {
                    case "+" -> cssLine = "diff-line-add";
                    case "-" -> cssLine = "diff-line-del";
                    case "@" -> cssLine = "diff-line-hunk";
                    default -> cssLine = "diff-line-ctx";
                }
                html.append("<div class='diff-line ")
                        .append(cssLine)
                        .append("'>")
                        .append(escape(raw))
                        .append("</div>");
            }
        }
        html.append("</div>");
        html.append("</div>");
    }

    private void updateTaskBar() {
        if (currentSession != null
                && ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK.equals(
                        currentSession.getKind())) {
            System.out.println(
                    "DEBUG: TaskBar is becoming visible for task " + currentSession.getId());
            taskBar.setVisible(true);
            taskBar.setManaged(true);
            TaskStatus currentStatus = currentSession.getStatus();
            // If running, we don't let user change it. If not, they can choose anything
            // except running.
            if (currentStatus == ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus.RUNNING) {
                taskStatusCombo.setDisable(true);
                taskStatusCombo
                        .getItems()
                        .setAll(ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus.RUNNING);
            } else {
                taskStatusCombo.setDisable(false);
                List<TaskStatus> allowedStatuses = new ArrayList<>();
                for (TaskStatus s : TaskStatus.values()) {
                    if (s != ai.emailclaw.emailclaw.model.ChatSessionInfo.TaskStatus.RUNNING) {
                        allowedStatuses.add(s);
                    }
                }
                taskStatusCombo.getItems().setAll(allowedStatuses);
            }
            taskStatusCombo.setValue(currentStatus);
            taskDescriptionInput.setText(currentSession.getDescription());
            // Build cron jobs table
            CronJobListView tmpCronView =
                    new CronJobListView(
                            cronJobService, currentAgent, currentProject, this::openTaskSession);
            List<CronJobSpec> taskJobs =
                    cronJobService.list().stream()
                            .filter(
                                    j ->
                                            currentSession.getId().equals(j.taskId())
                                                    || (j.dispatch() != null
                                                            && j.dispatch().target() != null
                                                            && currentSession
                                                                    .getId()
                                                                    .equals(
                                                                            j.dispatch()
                                                                                    .target()
                                                                                    .sessionId())))
                            .toList();
            if (taskJobs == null || taskJobs.isEmpty()) {
                cronJobContainer.setVisible(false);
                cronJobContainer.setManaged(false);
            } else {
                javafx.scene.Node cronTable =
                        tmpCronView.buildJobsTable(taskJobs, this::updateTaskBar);
                cronJobContainer.getChildren().setAll(cronTable);
                cronJobContainer.setVisible(true);
                cronJobContainer.setManaged(true);
            }
            System.out.println("DEBUG: TaskBar set visible=true, managed=true");
        } else {
            System.out.println(
                    "DEBUG: TaskBar is becoming hidden. currentSession="
                            + (currentSession == null ? "null" : currentSession.getKind()));
            taskBar.setVisible(false);
            taskBar.setManaged(false);
            if (cronJobContainer != null) {
                cronJobContainer.setVisible(false);
                cronJobContainer.setManaged(false);
            }
        }
    }

    private void renderApprovalMessage(StringBuilder html, String approvalId) {
        Optional<PendingApproval> approvalOpt = findPendingApproval(approvalId);
        if (approvalOpt.isEmpty()) {
            html.append(
                    "<div class='content content-system'>Pending security approval is no longer"
                            + " available.</div>");
            return;
        }
        PendingApproval approval = approvalOpt.get();
        html.append("<div class='approval-panel'>");
        html.append("<div class='approval-title'><span>Security Approval: ")
                .append(escape(approval.getToolName()))
                .append("</span><span class='approval-badge'>Awaiting user decision</span></div>");
        html.append(
                "<div class='approval-body'>System detected high-risk tool call, please confirm"
                        + " whether to allow execution.");
        html.append("<code class='approval-code'>")
                .append(escape(approval.getToolInput().toString()))
                .append("</code></div>");
        if (approval.getFindings() != null && !approval.getFindings().isEmpty()) {
            html.append("<ul class='approval-findings'>");
            for (GuardFinding finding : approval.getFindings()) {
                html.append("<li>").append(escape(finding.getDescription())).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("<div class='approval-remember'>");
        html.append("<label><input type='checkbox' id='remember-")
                .append(approval.getId())
                .append("' class='approval-remember-cb'>")
                .append("Remember this decision, auto-allow next time</label>");
        html.append("</div>");
        html.append("<div class='approval-actions'>");
        html.append("<button class='approval-btn approval-btn-deny' onclick='javaInterop.denyTool(")
                .append(jsString(approval.getId()))
                .append(")'>Deny Execution</button>");
        html.append(
                        "<button class='approval-btn approval-btn-approve'"
                                + " onclick='javaInterop.approveTool(")
                .append(jsString(approval.getId()))
                .append(", document.getElementById(")
                .append(jsString("remember-" + approval.getId()))
                .append(").checked)")
                .append("'>Approve Execution</button>");
        html.append("</div>");
        html.append("</div>");
    }

    private static final Pattern ARTIFACT_SOURCE_PATH_PATTERN =
            Pattern.compile("Delivered\\s+(.+?)\\s+to the configured destination", Pattern.DOTALL);
    private static final Pattern ARTIFACT_FILE_NAME_PATTERN =
            Pattern.compile("Delivered\\s+'([^']+)'", Pattern.DOTALL);
    private static final Pattern ARTIFACT_SIZE_PATTERN =
            Pattern.compile("\\((\\d+)\\s+bytes\\)", Pattern.DOTALL);

    // --- Vector SVG Icons for Linux Desktop Compatibility (No Emoji Font Dependency) ---
    private static final String SVG_BADGE_PACKAGE =
            "<svg width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='currentColor'"
                + " stroke-width='2' stroke-linecap='round' stroke-linejoin='round'"
                + " style='vertical-align:-1px; margin-right:4px;'><path d='M21 8a2 2 0 0"
                + " 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0"
                + " 2 0l7-4A2 2 0 0 0 21 16Z'/><path d='m3.3 7 8.7 5 8.7-5'/><path d='M12"
                + " 22V12'/></svg>";

    private static final String SVG_STATUS_CHECK =
            "<svg width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='#059669'"
                + " stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'"
                + " style='vertical-align:-1px; margin-right:3px;'><polyline points='20 6 9 17 4"
                + " 12'/></svg>";

    private static final String SVG_BTN_PREVIEW =
            "<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor'"
                + " stroke-width='2' stroke-linecap='round' stroke-linejoin='round'"
                + " style='vertical-align:-2px; margin-right:4px;'><path d='M2 12s3-7 10-7 10 7 10"
                + " 7-3 7-10 7-10-7-10-7Z'/><circle cx='12' cy='12' r='3'/></svg>";

    private static final String SVG_BTN_OPEN =
            "<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor'"
                + " stroke-width='2' stroke-linecap='round' stroke-linejoin='round'"
                + " style='vertical-align:-2px; margin-right:4px;'><path d='M18 13v6a2 2 0 0 1-2"
                + " 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6'/><polyline points='15 3 21 3 21 9'/><line"
                + " x1='10' y1='14' x2='21' y2='3'/></svg>";

    private static final String SVG_BTN_FOLDER =
            "<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor'"
                + " stroke-width='2' stroke-linecap='round' stroke-linejoin='round'"
                + " style='vertical-align:-2px; margin-right:4px;'><path d='M20 20a2 2 0 0 0"
                + " 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0"
                + " 0-2 2v13a2 2 0 0 0 2 2Z'/></svg>";

    private static final String SVG_BTN_COPY =
            "<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor'"
                    + " stroke-width='2' stroke-linecap='round' stroke-linejoin='round'"
                    + " style='vertical-align:-2px; margin-right:4px;'><rect width='14' height='14'"
                    + " x='8' y='8' rx='2' ry='2'/><path d='M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2"
                    + " 2-2h10c1.1 0 2 .9 2 2'/></svg>";

    private static final String SVG_FILE_DOC =
            "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#2563eb'"
                + " stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M14"
                + " 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/><polyline points='14 2"
                + " 14 8 20 8'/><line x1='16' y1='13' x2='8' y2='13'/><line x1='16' y1='17' x2='8'"
                + " y2='17'/><polyline points='10 9 9 9 8 9'/></svg>";

    private static final String SVG_FILE_PDF =
            "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#dc2626'"
                + " stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M14"
                + " 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/><polyline points='14 2"
                + " 14 8 20 8'/><path d='M9 15v-4h2a1.5 1.5 0 0 1 0 3H9'/><path d='M15"
                + " 15h-2v-4h2'/></svg>";

    private static final String SVG_FILE_SHEET =
            "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#16a34a'"
                + " stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M14"
                + " 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/><polyline points='14 2"
                + " 14 8 20 8'/><path d='M8 13h8'/><path d='M8 17h8'/><path d='M12 9v12'/></svg>";

    private static final String SVG_FILE_ZIP =
            "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#d97706'"
                + " stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M21"
                + " 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7"
                + " 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z'/><line x1='12' y1='22' x2='12'"
                + " y2='12'/><line x1='12' y1='7' x2='12' y2='7.01'/><line x1='12' y1='9' x2='12'"
                + " y2='9.01'/></svg>";

    private static final String SVG_FILE_IMG =
            "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#7c3aed'"
                    + " stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><rect"
                    + " width='18' height='18' x='3' y='3' rx='2' ry='2'/><circle cx='9' cy='9'"
                    + " r='2'/><path d='m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21'/></svg>";

    private static final String SVG_FILE_CODE =
            "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#0891b2'"
                    + " stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><polyline"
                    + " points='16 18 22 12 16 6'/><polyline points='8 6 2 12 8 18'/></svg>";

    private static final String SVG_FILE_GENERIC =
            "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#64748b'"
                + " stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M14"
                + " 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/><polyline points='14 2"
                + " 14 8 20 8'/></svg>";

    private boolean isDeliverArtifactSuccessResult(ChatMessagePart part) {
        if (part == null || part.getText() == null) {
            return false;
        }
        String text = part.getText().trim();
        if (text.startsWith("Error:") || text.contains("was not delivered:")) {
            return false;
        }
        String type = ChatMessagePart.normalizeType(part.getType());
        if (!ChatMessagePart.TOOL_RESULT.equals(type)) {
            return false;
        }
        String tool = part.getToolName();
        if (tool != null && "deliver_artifact".equalsIgnoreCase(tool.trim())) {
            return true;
        }
        String title = part.getTitle();
        if (title != null && title.toLowerCase(Locale.ROOT).contains("deliver_artifact")) {
            return true;
        }
        return text.contains("to the configured destination: Delivered '")
                || (text.startsWith("Delivered ")
                        && text.contains("to the configured destination"));
    }

    private void renderDeliverArtifactCard(StringBuilder html, ChatMessagePart part) {
        String rawText = part.getText().trim();
        if (rawText.startsWith("\"") && rawText.endsWith("\"") && rawText.length() >= 2) {
            rawText = rawText.substring(1, rawText.length() - 1).trim();
        }
        rawText = rawText.replace("\\\"", "\"").replace("\\n", "\n");

        Matcher mSrc = ARTIFACT_SOURCE_PATH_PATTERN.matcher(rawText);
        String sourcePath = mSrc.find() ? mSrc.group(1).trim() : "";

        Matcher mFile = ARTIFACT_FILE_NAME_PATTERN.matcher(rawText);
        String fileName = mFile.find() ? mFile.group(1).trim() : "";
        if (fileName.isBlank() && !sourcePath.isBlank()) {
            try {
                fileName = Path.of(sourcePath).getFileName().toString();
            } catch (Exception ignored) {
            }
        }
        if (fileName.isBlank()) {
            fileName = "deliverable";
        }

        Matcher mSize = ARTIFACT_SIZE_PATTERN.matcher(rawText);
        long sizeBytes = 0L;
        if (mSize.find()) {
            try {
                sizeBytes = Long.parseLong(mSize.group(1).trim());
            } catch (NumberFormatException ignored) {
            }
        }

        String storagePath = null;
        String sha256 = null;
        String description = null;

        // 1. Look up in SQLite artifact repository for the current session
        if (currentSession != null && currentSession.getId() != null) {
            List<ArtifactRecord> records =
                    chatService
                            .repository()
                            .artifactRepository()
                            .findBySessionId(currentSession.getId());
            for (ArtifactRecord r : records) {
                if (fileName.equals(r.fileName())) {
                    storagePath = r.storagePath();
                    if (sizeBytes <= 0) {
                        sizeBytes = r.sizeBytes();
                    }
                    sha256 = r.sha256();
                    description = r.description();
                    break;
                }
            }
        }

        // 2. Probe project directory if storagePath is not yet resolved
        if (storagePath == null
                && currentProject != null
                && currentProject.getBaseDirectory() != null) {
            Path candidate =
                    Path.of(currentProject.getBaseDirectory())
                            .resolve("artifacts")
                            .resolve(fileName);
            if (Files.exists(candidate)) {
                storagePath = candidate.toAbsolutePath().normalize().toString();
            }
        }

        // 3. Fallback to sourcePath on disk
        if (storagePath == null && !sourcePath.isBlank()) {
            Path srcCandidate = Path.of(sourcePath);
            if (Files.exists(srcCandidate)) {
                storagePath = srcCandidate.toAbsolutePath().normalize().toString();
            }
        }

        // 4. Default destination path if not on disk yet
        if (storagePath == null) {
            if (currentProject != null && currentProject.getBaseDirectory() != null) {
                storagePath =
                        Path.of(currentProject.getBaseDirectory())
                                .resolve("artifacts")
                                .resolve(fileName)
                                .toAbsolutePath()
                                .normalize()
                                .toString();
            } else {
                storagePath = !sourcePath.isBlank() ? sourcePath : fileName;
            }
        }

        // 5. Probe actual file size and compute SHA-256 if needed
        Path targetFile = Path.of(storagePath);
        if (Files.exists(targetFile)) {
            if (sizeBytes <= 0) {
                try {
                    sizeBytes = Files.size(targetFile);
                } catch (Exception ignored) {
                }
            }
            if (sha256 == null || sha256.isBlank()) {
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] hash = md.digest(Files.readAllBytes(targetFile));
                    sha256 = HexFormat.of().formatHex(hash);
                } catch (Exception ignored) {
                }
            }
        }

        // Display relative destination path
        String displayPath = storagePath;
        if (currentProject != null && currentProject.getBaseDirectory() != null) {
            String base = currentProject.getBaseDirectory();
            if (displayPath.startsWith(base)) {
                displayPath = displayPath.substring(base.length());
                if (displayPath.startsWith("/") || displayPath.startsWith("\\")) {
                    displayPath = displayPath.substring(1);
                }
            }
        }

        String fileIcon = resolveFileSvgIcon(fileName);
        String ext =
                fileName.contains(".")
                        ? fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)
                        : "FILE";
        String shaDisplay =
                (sha256 != null && sha256.length() >= 12)
                        ? sha256.substring(0, 6) + "..." + sha256.substring(sha256.length() - 4)
                        : (sha256 != null ? sha256 : "");

        String jsSafePath = escape(storagePath.replace("\\", "\\\\").replace("'", "\\'"));

        html.append("<div class='artifact-card'>");
        html.append("<div class='artifact-card-header'>");
        html.append("<div class='artifact-badge-wrap'>");
        html.append("<span class='artifact-badge'>")
                .append(SVG_BADGE_PACKAGE)
                .append("PROJECT DELIVERABLE</span>");
        html.append("<span class='artifact-status'>")
                .append(SVG_STATUS_CHECK)
                .append("Ready &amp; Saved</span>");
        html.append("</div>");
        html.append("<span class='artifact-header-tag'>AgentScope Standard Artifact</span>");
        html.append("</div>");

        html.append("<div class='artifact-card-body'>");
        html.append("<div class='artifact-icon'>").append(fileIcon).append("</div>");
        html.append("<div class='artifact-meta-wrap'>");
        html.append("<div class='artifact-filename' title='")
                .append(escape(fileName))
                .append("'>")
                .append(escape(fileName))
                .append("</div>");
        html.append("<div class='artifact-details'>");
        html.append("<span class='artifact-pill artifact-size'>")
                .append(escape(formatFileSize(sizeBytes)))
                .append("</span>");
        html.append("<span class='artifact-pill artifact-ext'>")
                .append(escape(ext))
                .append("</span>");
        if (!shaDisplay.isBlank()) {
            html.append("<span class='artifact-pill artifact-sha' title='SHA-256: ")
                    .append(escape(sha256))
                    .append("'>SHA: ")
                    .append(escape(shaDisplay))
                    .append("</span>");
        }
        html.append("</div>");

        if (description != null && !description.isBlank()) {
            html.append("<div style='font-size:12px; color:#475569; margin-top:2px;'>")
                    .append(escape(description))
                    .append("</div>");
        }

        html.append("<div class='artifact-path' title='").append(escape(storagePath)).append("'>");
        html.append("<span class='artifact-path-label'>Destination:</span> ")
                .append(escape(displayPath));
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        // Action Buttons with Vector SVG Icons
        html.append("<div class='artifact-actions'>");
        html.append(
                        "<button class='artifact-btn artifact-btn-preview'"
                                + " onclick=\"javaInterop.previewArtifact('")
                .append(jsSafePath)
                .append("')\">")
                .append(SVG_BTN_PREVIEW)
                .append("Preview</button>");
        html.append(
                        "<button class='artifact-btn artifact-btn-open'"
                                + " onclick=\"javaInterop.openArtifactFile('")
                .append(jsSafePath)
                .append("')\">")
                .append(SVG_BTN_OPEN)
                .append("Open File</button>");
        html.append("<button class='artifact-btn' onclick=\"javaInterop.revealArtifactInFolder('")
                .append(jsSafePath)
                .append("')\">")
                .append(SVG_BTN_FOLDER)
                .append("Show in Folder</button>");
        html.append("<button class='artifact-btn' onclick=\"copyPath(this, '")
                .append(jsSafePath)
                .append("')\">")
                .append(SVG_BTN_COPY)
                .append("Copy Path</button>");
        html.append("</div>");

        // Expandable Technical Details
        html.append("<details class='artifact-tech-details'>");
        html.append("<summary>Technical Tool Result (AgentScope protocol log)</summary>");
        html.append("<div class='artifact-raw-log'>").append(escape(rawText)).append("</div>");
        html.append("</details>");

        html.append("</div>");
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private static String resolveFileSvgIcon(String fileName) {
        if (fileName == null) {
            return SVG_FILE_GENERIC;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
            return SVG_FILE_DOC;
        }
        if (lower.endsWith(".pdf")) {
            return SVG_FILE_PDF;
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) {
            return SVG_FILE_SHEET;
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".rtf")) {
            return SVG_FILE_DOC;
        }
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) {
            return SVG_FILE_DOC;
        }
        if (lower.endsWith(".zip")
                || lower.endsWith(".tar")
                || lower.endsWith(".gz")
                || lower.endsWith(".7z")
                || lower.endsWith(".rar")) {
            return SVG_FILE_ZIP;
        }
        if (lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".svg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")) {
            return SVG_FILE_IMG;
        }
        if (lower.endsWith(".java")
                || lower.endsWith(".py")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".json")
                || lower.endsWith(".xml")
                || lower.endsWith(".sql")
                || lower.endsWith(".sh")) {
            return SVG_FILE_CODE;
        }
        return SVG_FILE_GENERIC;
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String jsString(String value) {
        String text = value == null ? "" : value;
        return "\""
                + text.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                + "\"";
    }

    private String markdownToHtml(String markdown) {
        String text = markdown == null ? "" : markdown;
        String[] lines = text.split("\n", -1);
        StringBuilder body = new StringBuilder();
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("```")) {
                if (!inCode) {
                    inCode = true;
                    code.setLength(0);
                } else {
                    body.append("<pre><code>")
                            .append(escape(code.toString()))
                            .append("</code></pre>");
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                code.append(line).append("\n");
            } else if (line.isBlank()) {
                body.append("<br/>");
            } else {
                body.append(escape(line)).append("<br/>");
            }
        }
        if (inCode) {
            body.append("<pre><code>").append(escape(code.toString())).append("</code></pre>");
        }
        return body.toString();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        currentAgent = agent;
        if (chatSearchPanel != null) {
            chatSearchPanel.setAgentId(
                    agent.getId(),
                    currentSession != null
                            ? currentSession.getKind()
                            : ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_CHAT);
            chatSearchPanel.close();
        }
        restoreCodingModeState();
        resolveDefaultModel();
        ensureSession();
        refresh();
    }

    @Override
    public void onProjectChanged(ProjectInfo project) {
        this.currentProject = project;
        if (codingModePane != null) {
            codingModePane.onProjectChanged(project);
        }
        if (currentSession != null && !sessionMatchesProject(currentSession, project)) {
            currentSession = null;
            ensureSession();
            refresh();
        }
    }

    @Override
    public void refresh() {
        if (currentSession == null
                || currentAgent == null
                || !currentAgent.getId().equals(currentSession.getAgentId())
                || !sessionMatchesProject(currentSession, currentProject)) {
            ensureSession();
        }
        // Verify if currentSession still exists when displaying ChatView (to prevent inconsistency
        // after deletion)
        if (currentAgent != null && currentSession != null) {
            ChatSessionInfo validatedSession = validateSessionExists(currentSession.getId());
            if (validatedSession == null) {
                // Session has been deleted or invalid, creating new session
                LOGGER.log(
                        Level.WARNING,
                        "Current session has been deleted or invalid (id={0}), creating/switching"
                                + " session",
                        currentSession.getId());
                currentSession = null;
                ensureSession();
            } else {
                // Use the latest session object queried from the database to ensure reference and
                // data are up-to-date
                currentSession = validatedSession;
            }
            ai.emailclaw.emailclaw.model.AgentConfiguration config =
                    chatService.repository().loadAgentConfig(currentAgent.getId());
            if (config != null) {
                permissionModeBox.setValue(config.getPermissionMode());
            }
        }
        // Set title
        title.setText(getSessionTitle(currentSession));
        // Clear old messages, display empty state (immediate render, no debounce)
        messages.clear();
        doRenderMessages();
        // Load history messages in background thread to avoid blocking UI
        Thread loadThread =
                new Thread(
                        () -> {
                            try {
                                List<Msg> historyMsgs =
                                        chatService.loadHistory(
                                                currentAgent.getId(), currentSession.getId());
                                List<ChatMessageRecord> history = new ArrayList<>();
                                for (Msg msg : historyMsgs) {
                                    history.add(
                                            new ChatMessageRecord(
                                                    chatService.roleOf(msg),
                                                    chatService.partsOf(msg),
                                                    msg.getTimestamp()));
                                }
                                Platform.runLater(
                                        () -> {
                                            messages.clear();
                                            messages.addAll(history);
                                            doRenderMessages();
                                        });
                            } catch (Exception ex) {
                                LOGGER.log(
                                        Level.WARNING,
                                        "Failed to load session history: agentId={0},"
                                                + " sessionId={1}",
                                        new Object[] {
                                            currentAgent.getId(), currentSession.getId()
                                        });
                                Platform.runLater(this::doRenderMessages);
                            }
                        });
        loadThread.setDaemon(true);
        loadThread.start();
        updateTaskBar();
    }

    /**
     * Allow external pages (e.g., ModelsView) to actively trigger ChatView to synchronize the top-right model display after saving the default model.
     *
     * <p>
     * This method will not switch the current session, it will only recalculate the provider/model selection and button text, and then refresh message rendering.
     */
    public void applyPersistedModelSelection() {
        resolveDefaultModel();
        renderMessages();
    }

    /**
     * Load the specified session. Each time it loads, it verifies if the session still exists in the database. If the session has been deleted, it automatically creates a new session and loads it.
     *
     * @param session Session info to load
     */
    public void loadSession(ChatSessionInfo session) {
        if (session == null) {
            currentSession =
                    chatService.newSession(
                            currentAgent.getId(),
                            this.sessionKind,
                            defaultSessionTitle(),
                            currentProject.getId());
            refresh();
            return;
        }
        if (!session.getProjectId().isBlank()) {
            if (!currentProject.getId().equals(session.getProjectId())) {
                this.currentProject = new ProjectInfo();
                this.currentProject.setId(session.getProjectId());
                if (codingModePane != null) {
                    codingModePane.onProjectChanged(this.currentProject);
                }
            }
        }
        // Verify if session still exists in the database
        ChatSessionInfo validatedSession = validateSessionExists(session.getId());
        if (validatedSession == null) {
            LOGGER.log(
                    Level.WARNING,
                    "Session has been deleted (sessionId={0}), creating new session",
                    session.getId());
            currentSession =
                    chatService.newSession(
                            currentAgent.getId(),
                            this.sessionKind,
                            defaultSessionTitle(),
                            currentProject.getId());
        } else {
            // Use the session object queried from the database to ensure data is up-to-date
            currentSession = validatedSession;
        }
        refresh();
    }

    /**
     * Verify if the specified session exists in the database by querying all sessions of the current Agent to check if there is a matching session ID.
     *
     * @param sessionId Session ID to verify
     * @return If session exists, return complete ChatSessionInfo object; otherwise return null
     */
    private ChatSessionInfo validateSessionExists(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || currentAgent == null) {
            return null;
        }
        List<ChatSessionInfo> sessions = chatService.sessions(currentAgent.getId());
        return sessions.stream()
                .filter(s -> s.getId().equals(sessionId))
                .filter(s -> sessionMatchesProject(s, currentProject))
                .findFirst()
                .orElse(null);
    }

    private void showChatHistoryDialog() {
        ChatHistoryDialog dialog =
                new ChatHistoryDialog(chatService, currentAgent.getId(), currentSession);
        dialog.show(
                root.getScene() != null ? root.getScene().getWindow() : null,
                root.getScene() != null ? root.getScene().getStylesheets() : null,
                new ChatHistoryDialog.Callback() {

                    @Override
                    public void switchSession(ChatSessionInfo session) {
                        currentSession = session;
                        refresh();
                        title.setText(getSessionTitle(session));
                    }

                    @Override
                    public ChatSessionInfo createNewSession() {
                        ChatSessionInfo newSession =
                                chatService.newSession(
                                        currentAgent.getId(),
                                        sessionKind,
                                        defaultSessionTitle(),
                                        currentProject.getId());
                        currentSession = newSession;
                        title.setText(getSessionTitle(currentSession));
                        messages.clear();
                        renderMessages();
                        return newSession;
                    }
                });
        currentSession = dialog.getCurrentSession();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Load resource file content from classpath as a string.
     */
    private static String loadResource(String path) {
        try (var is = ChatView.class.getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.warning("Resource file not found: " + path);
                return "";
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load resource file: " + path, e);
            return "";
        }
    }
}
