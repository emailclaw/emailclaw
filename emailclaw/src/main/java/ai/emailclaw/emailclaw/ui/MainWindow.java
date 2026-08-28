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

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.plugin.PluginManager;
import ai.emailclaw.emailclaw.service.AcpService;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.BackupService;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.CronJobService;
import ai.emailclaw.emailclaw.service.MarketService;
import ai.emailclaw.emailclaw.service.McpService;
import ai.emailclaw.emailclaw.service.MessageBusService;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.SecurityService;
import ai.emailclaw.emailclaw.service.SkillService;
import ai.emailclaw.emailclaw.service.ToolRuntimeContext;
import ai.emailclaw.emailclaw.service.ToolService;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.awt.Desktop;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Main window container, responsible for side navigation, Project switching, Agent switching, and lifecycle management of various functional views.
 */
public class MainWindow extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());

    private final AgentService agentService;

    private final ProjectService projectService;

    private final ProviderService providerService;

    private final ChatService chatService;

    private final CronJobService cronJobService;

    private final ChannelService channelService;

    private final ToolRuntimeContext toolRuntimeContext;

    private final MessageBusService messageBusService;

    private final AppContext repository;

    private final Map<String, ViewPane> views = new LinkedHashMap<>();

    private final Map<String, Supplier<ViewPane>> factories = new LinkedHashMap<>();

    private final StackPane content = new StackPane();

    private final ComboBox<AgentInfo> agentCombo = new ComboBox<>();

    private final ComboBox<ProjectInfo> projectCombo = new ComboBox<>();

    private final Button codeModeButton = new Button("Code");

    private final Map<String, Button> menuButtons = new LinkedHashMap<>();

    private final VBox sidebarArea = new VBox();

    private VBox currentProjectBox;

    private VBox agentManagementBox;

    private VBox systemSettingsBox;

    private final VBox tasksList = new VBox(2);

    private final Button toggleAllBtn = new Button("View all");

    private boolean tasksExpanded = false;

    private AgentInfo currentAgent;

    private ProjectInfo currentProject;

    private String currentViewId = ViewIds.CHAT;

    public MainWindow(
            AppContext repository,
            AgentService agentService,
            ProjectService projectService,
            ProviderService providerService,
            SkillService skillService,
            ToolService toolService,
            ChatService chatService,
            ChannelService channelService,
            CronJobService cronJobService,
            McpService mcpService,
            AcpService acpService,
            SecurityService securityService,
            BackupService backupService,
            PluginManager pluginManager,
            MarketService marketService,
            MessageBusService messageBusService,
            ToolRuntimeContext toolRuntimeContext) {
        this.repository = repository;
        this.agentService = agentService;
        this.projectService = projectService;
        this.providerService = providerService;
        this.chatService = chatService;
        this.cronJobService = cronJobService;
        this.channelService = channelService;
        this.messageBusService = messageBusService;
        this.currentAgent = agentService.currentDefault();
        this.currentProject = projectService.currentDefault();
        this.toolRuntimeContext = toolRuntimeContext;
        LOGGER.log(
                Level.INFO,
                "Initializing main window, current Project: {0}, current Agent: {1}",
                new Object[] {
                    currentProject != null ? currentProject.getId() : "null",
                    currentAgent != null ? currentAgent.getId() : "null"
                });
        // First register all view factories to avoid being unable to get View instances when
        // initialization triggers showView later
        factories.put(
                ViewIds.DASHBOARD,
                () ->
                        new DashboardView(
                                repository,
                                agentService,
                                chatService,
                                cronJobService,
                                providerService,
                                projectService,
                                skillService,
                                this::showView,
                                this::selectAgentAndShowProfile));
        factories.put(
                ViewIds.CHAT,
                () ->
                        new ChatView(
                                agentService,
                                providerService,
                                chatService,
                                cronJobService,
                                currentAgent,
                                ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_CHAT,
                                currentProject));
        factories.put(ViewIds.INBOX, () -> new InboxView(messageBusService));
        factories.put(
                ViewIds.SESSIONS_TASK,
                () ->
                        new SessionsView(
                                chatService,
                                channelService,
                                currentAgent,
                                ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK,
                                session -> {
                                    showTaskView(session);
                                }));
        factories.put(
                ViewIds.TASK,
                () ->
                        new TaskView(
                                null,
                                currentAgent,
                                currentProject,
                                agentService,
                                providerService,
                                chatService,
                                cronJobService,
                                () -> {
                                    showSystemSettingsSidebar();
                                    showView(ViewIds.MODELS);
                                }));
        factories.put(
                ViewIds.SESSIONS_CHAT,
                () ->
                        new SessionsView(
                                chatService,
                                channelService,
                                currentAgent,
                                ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_CHAT,
                                session -> {
                                    ChatView chatView =
                                            (ChatView)
                                                    views.computeIfAbsent(
                                                            ViewIds.CHAT,
                                                            id -> factories.get(id).get());
                                    chatView.onAgentChanged(currentAgent);
                                    chatView.loadSession(session);
                                    showView(ViewIds.CHAT);
                                }));
        factories.put(ViewIds.FILES, () -> new FilesView(repository, currentAgent));
        factories.put(ViewIds.PROJECTS, () -> new ProjectsView(repository, projectService));
        factories.put(ViewIds.SKILLS, () -> new SkillsView(repository, skillService, currentAgent));
        factories.put(ViewIds.TOOLS, () -> new ToolsView(toolService));
        factories.put(
                ViewIds.AGENT_MANAGEMENT,
                () ->
                        new AgentManagementView(
                                agentService,
                                providerService,
                                skillService,
                                this::onAgentCreated,
                                this::selectAgentAndShowProfile));
        factories.put(
                ViewIds.AGENT_PROFILE,
                () ->
                        new AgentProfileView(
                                agentService,
                                providerService,
                                skillService,
                                currentAgent,
                                this::onAgentProfileUpdated,
                                this::showMainSidebar));
        factories.put(
                ViewIds.MODELS,
                () ->
                        new ModelsView(
                                providerService,
                                agentService,
                                currentAgent,
                                () -> {
                                    ViewPane chatPane = views.get(ViewIds.CHAT);
                                    if (chatPane instanceof ChatView chatView) {
                                        chatView.applyPersistedModelSelection();
                                    }
                                }));
        factories.put(ViewIds.SKILL_POOL, () -> new SkillPoolView(repository, skillService));
        factories.put(ViewIds.SKILL_MARKET, () -> new SkillMarketView(marketService));
        factories.put(ViewIds.TOKEN_USAGE, () -> new TokenUsageView(repository));
        factories.put(ViewIds.AGENT_STATISTICS, () -> new AgentStatisticsView(repository));
        factories.put(ViewIds.CHANNELS, () -> new ChannelsView(channelService));
        factories.put(ViewIds.CRON_JOBS, () -> new CronJobListView(cronJobService, currentAgent));
        factories.put(ViewIds.HEARTBEAT, () -> new HeartbeatView(repository, currentAgent));
        factories.put(ViewIds.MCP, () -> new McpView(mcpService));
        factories.put(ViewIds.ACP, () -> new AcpView(acpService));
        factories.put(ViewIds.CONFIGURATION, () -> new ConfigurationView(repository, currentAgent));
        factories.put(ViewIds.ENVIRONMENTS, () -> new EnvironmentsView(repository));
        factories.put(ViewIds.SECURITY, () -> new SecurityView(securityService));
        factories.put(ViewIds.BACKUPS, () -> new BackupsView(backupService));
        factories.put(ViewIds.VOICE_TRANSCRIPTION, () -> new VoiceTranscriptionView(repository));
        factories.put(ViewIds.DEBUG, () -> new DebugView(repository.paths()));
        factories.put(ViewIds.PLUGIN_MANAGER, () -> new PluginManagerView(pluginManager));
        // Build layout after view factories are registered
        setTop(buildTopBar());
        setLeft(buildSidebar());
        setCenter(content);
        setPadding(new Insets(0));
        getStyleClass().add("app-root");
        showView(ViewIds.DASHBOARD);
        syncCodeModeButton();
        repository
                .configManager()
                .addChangeListener(
                        ai.emailclaw.emailclaw.storage.ConfigManager.EVENT_SESSIONS,
                        () -> Platform.runLater(this::renderTasksList));
    }

    private Node buildTopBar() {
        HBox top = new HBox();
        top.getStyleClass().add("top-bar");
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 16, 10, 16));
        top.setSpacing(18);
        Label brand = new Label("Emailclaw");
        brand.getStyleClass().add("brand");
        Label version = new Label("v26.8.28");
        version.getStyleClass().add("muted");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuItem tutorialItem = new MenuItem("Tutorial");
        tutorialItem.setOnAction(e -> openUrl("https://github.com/emailclaw/emailclaw"));
        MenuItem demosItem = new MenuItem("Feature Demos");
        demosItem.setOnAction(e -> openUrl("https://github.com/emailclaw/emailclaw"));
        MenuItem changelogItem = new MenuItem("Changelog");
        changelogItem.setOnAction(e -> openUrl("https://github.com/emailclaw/emailclaw/releases"));
        MenuItem faqItem = new MenuItem("FAQ");
        faqItem.setOnAction(e -> openUrl("https://github.com/emailclaw/emailclaw/issues"));
        MenuButton documentation =
                new MenuButton(
                        "Documentation", null, tutorialItem, demosItem, changelogItem, faqItem);
        documentation.getStyleClass().add("link-btn");
        top.getChildren().addAll(brand, version, spacer, documentation);
        top.getChildren().add(navLink("GitHub", "https://github.com/emailclaw/emailclaw"));
        codeModeButton.getStyleClass().add("link-btn");
        codeModeButton.setOnAction(e -> toggleCodingMode(codeModeButton));
        top.getChildren().add(codeModeButton);
        //        top.getChildren().add(navLink("En", null));
        return top;
    }

    private void toggleCodingMode(Button button) {
        showView(ViewIds.CHAT);
        ViewPane pane = views.get(ViewIds.CHAT);
        if (pane instanceof ChatView chatView) {
            chatView.toggleCodingMode();
            syncCodeModeButton();
        }
    }

    private void syncCodeModeButton() {
        ViewPane pane = views.get(ViewIds.CHAT);
        if (pane instanceof ChatView chatView) {
            codeModeButton.setText(chatView.isCodingModeVisible() ? "Chat" : "Code");
        }
    }

    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                LOGGER.log(Level.WARNING, "Desktop browsing not supported for URL: {0}", url);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to open URL: " + url, ex);
        }
    }

    private Node buildSidebar() {
        VBox root = new VBox(8);
        root.getStyleClass().add("sidebar");
        root.setPadding(new Insets(12));
        buildCurrentProjectBox();
        buildAgentManagementBox();
        buildSystemSettingsBox();
        showMainSidebar();
        root.getChildren().add(sidebarArea);
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("left-scroll");
        scroll.setPrefWidth(260);
        return scroll;
    }

    private void buildCurrentProjectBox() {
        currentProjectBox = new VBox(8);
        Label title = new Label("Current Project: ");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        Button addProjectBtn = new Button("+");
        addProjectBtn.getStyleClass().add("link-btn");
        addProjectBtn.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 4;");
        addProjectBtn.setOnAction(
                e -> {
                    ProjectsView pv =
                            (ProjectsView)
                                    views.computeIfAbsent(
                                            ViewIds.PROJECTS, id -> factories.get(id).get());
                    pv.showCreateEditDialog(null)
                            .ifPresent(
                                    project -> {
                                        LOGGER.log(
                                                Level.INFO,
                                                "Successfully created new Project: {0} ({1})",
                                                new Object[] {project.getName(), project.getId()});
                                        projectService.setCurrentProject(project.getId());
                                        currentProject = project;
                                        projectService.save(project);
                                    });
                });
        Button settingsProjectBtn = new Button("⚙");
        settingsProjectBtn.getStyleClass().add("link-btn");
        settingsProjectBtn.setStyle("-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 4;");
        settingsProjectBtn.setOnAction(e -> showView(ViewIds.PROJECTS));
        HBox titleSpacer = new HBox();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleBox = new HBox(4, title, titleSpacer, addProjectBtn, settingsProjectBtn);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setMaxWidth(Double.MAX_VALUE);
        projectCombo.getItems().setAll(projectService.list());
        projectCombo.setValue(currentProject);
        projectService.addListener(
                () -> {
                    Platform.runLater(
                            () -> {
                                ProjectInfo selected = projectService.currentDefault();
                                List<ProjectInfo> newList = projectService.list();
                                projectCombo.getItems().setAll(newList);
                                ProjectInfo newCurrent =
                                        newList.stream()
                                                .filter(
                                                        p ->
                                                                p.getId()
                                                                        .equals(
                                                                                currentProject
                                                                                        .getId()))
                                                .findFirst()
                                                .orElse(selected);
                                projectCombo.setValue(null);
                                projectCombo.setValue(newCurrent);
                            });
                });
        projectCombo.setCellFactory(v -> new ProjectListCell());
        projectCombo.setButtonCell(new ProjectButtonCell());
        projectCombo.setMaxWidth(Double.MAX_VALUE);
        projectCombo
                .valueProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (newV != null) {
                                currentProject = newV;
                                projectService.setCurrentProject(newV.getId());
                                views.values().forEach(v -> v.onProjectChanged(currentProject));
                                renderTasksList();
                            }
                        });
        VBox topSection = new VBox(8);
        topSection.getChildren().add(menuButton("Dashboard", ViewIds.DASHBOARD));
        topSection.getChildren().add(new Separator());
        topSection.getChildren().addAll(titleBox, projectCombo);
        topSection.getChildren().add(menuButton("Scheduled", ViewIds.CRON_JOBS));
        topSection
                .getChildren()
                .add(
                        actionButton(
                                "Skills",
                                () -> {
                                    showAgentManagementSidebar();
                                    showView(ViewIds.SKILLS);
                                }));
        Button chatMenuBtn = menuButton("Chat", ViewIds.CHAT);
        Button chatGearBtn = new Button("⚙");
        chatGearBtn.getStyleClass().add("icon-btn");
        chatGearBtn.setStyle(
                "-fx-font-size: 14px; -fx-cursor: hand; -fx-text-fill: #6b7280;"
                        + " -fx-background-color: transparent; -fx-padding: 4 8;");
        chatGearBtn.setOnAction(e -> showView(ViewIds.SESSIONS_CHAT));
        HBox chatRow = new HBox(4, chatMenuBtn, chatGearBtn);
        chatRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(chatMenuBtn, Priority.ALWAYS);
        topSection.getChildren().add(chatRow);
        Button taskMenuBtn = menuButton("Task", ViewIds.TASK);
        Button taskGearBtn = new Button("⚙");
        taskGearBtn.getStyleClass().add("icon-btn");
        taskGearBtn.setStyle(
                "-fx-font-size: 14px; -fx-cursor: hand; -fx-text-fill: #6b7280;"
                        + " -fx-background-color: transparent; -fx-padding: 4 8;");
        taskGearBtn.setOnAction(e -> showView(ViewIds.SESSIONS_TASK));
        HBox taskRow = new HBox(4, taskMenuBtn, taskGearBtn);
        taskRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(taskMenuBtn, Priority.ALWAYS);
        topSection.getChildren().add(taskRow);
        HBox tasksTitleBox = new HBox();
        tasksTitleBox.setAlignment(Pos.CENTER_LEFT);
        Label tasksTitle = sectionTitle("Tasks");
        HBox tasksTitleSpacer1 = new HBox();
        HBox.setHgrow(tasksTitleSpacer1, Priority.ALWAYS);
        toggleAllBtn.getStyleClass().add("link-btn");
        toggleAllBtn.setStyle("-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 0 4;");
        toggleAllBtn.setOnAction(
                e -> {
                    tasksExpanded = !tasksExpanded;
                    renderTasksList();
                });
        tasksTitleBox.getChildren().addAll(tasksTitle, tasksTitleSpacer1, toggleAllBtn);
        topSection.getChildren().add(tasksTitleBox);
        topSection.getChildren().add(tasksList);
        renderTasksList();
        Separator sep = new Separator();
        Button agentMgmtBtn =
                actionButton(
                        "Agent Management",
                        () -> {
                            showAgentManagementSidebar();
                            showView(ViewIds.AGENT_PROFILE);
                        });

        Button newAgentBtn = new Button("+");
        newAgentBtn.getStyleClass().add("link-btn");
        newAgentBtn.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 4;");
        newAgentBtn.setOnAction(
                e -> {
                    AgentManagementView amv =
                            (AgentManagementView)
                                    views.computeIfAbsent(
                                            ViewIds.AGENT_MANAGEMENT,
                                            id -> factories.get(id).get());
                    amv.showCreateDialog();
                });

        HBox agentMgmtRow = new HBox(4, agentMgmtBtn, newAgentBtn);
        agentMgmtRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(agentMgmtBtn, Priority.ALWAYS);

        Button sysSettingsBtn =
                actionButton(
                        "System Settings",
                        () -> {
                            showSystemSettingsSidebar();
                            showView(ViewIds.MODELS);
                        });
        currentProjectBox.getChildren().addAll(topSection, sep, agentMgmtRow, sysSettingsBtn);

        // Dynamically load channel settings buttons for all channels supporting configuration
        // panels
        for (ai.emailclaw.emailclaw.model.ChannelInfo ch : channelService.list()) {
            ai.emailclaw.emailclaw.plugin.EmailclawPlugin plugin =
                    channelService.getPluginInstance(ch.getId());
            if (plugin != null) {
                var providerOpt =
                        ai.emailclaw.emailclaw.ui.plugin.PluginUIFactory.getProvider(plugin.id());
                if (providerOpt.isPresent()) {
                    ai.emailclaw.emailclaw.model.ChannelInfo targetChannel =
                            channelService.list().stream()
                                    .filter(c -> c.getId().equals(plugin.id()))
                                    .findFirst()
                                    .orElse(null);
                    boolean isEnabled = targetChannel != null && targetChannel.isEnabled();
                    Button btn =
                            new Button(
                                    plugin.displayName()
                                            + " Settings      "
                                            + (isEnabled ? "▶" : "⏸"));
                    btn.getStyleClass().add("menu-btn");
                    btn.setMaxWidth(Double.MAX_VALUE);

                    javafx.animation.Timeline timeline =
                            new javafx.animation.Timeline(
                                    new javafx.animation.KeyFrame(
                                            javafx.util.Duration.millis(500),
                                            event -> {
                                                ai.emailclaw.emailclaw.model.ChannelInfo currentCh =
                                                        channelService.list().stream()
                                                                .filter(
                                                                        c ->
                                                                                c.getId()
                                                                                        .equals(
                                                                                                plugin
                                                                                                        .id()))
                                                                .findFirst()
                                                                .orElse(null);
                                                if (currentCh != null) {
                                                    btn.setText(
                                                            plugin.displayName()
                                                                    + " Settings      "
                                                                    + (currentCh.isEnabled()
                                                                            ? "▶"
                                                                            : "⏸"));
                                                }
                                            }));
                    timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    timeline.play();

                    btn.setOnAction(
                            e -> {
                                javafx.stage.Window owner =
                                        getScene() != null ? getScene().getWindow() : null;
                                if (owner == null) return;
                                ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider provider =
                                        providerOpt.get();
                                ai.emailclaw.emailclaw.model.ChannelInfo channel =
                                        channelService.list().stream()
                                                .filter(c -> c.getId().equals(plugin.id()))
                                                .findFirst()
                                                .orElse(null);
                                if (channel == null) {
                                    channel =
                                            new ai.emailclaw.emailclaw.model.ChannelInfo(
                                                    plugin.id(), plugin.displayName(), true, false);
                                }
                                java.util.Map<String, Object> initialConfig =
                                        channel.getPluginConfig() != null
                                                ? new java.util.HashMap<>(channel.getPluginConfig())
                                                : new java.util.HashMap<>();
                                initialConfig.put("enabled", channel.isEnabled());
                                initialConfig.put("botPrefix", channel.getBotPrefix());

                                javafx.stage.Stage dialogStage = new javafx.stage.Stage();
                                dialogStage.initOwner(owner);
                                dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                                dialogStage.setTitle(plugin.displayName() + " Settings");

                                final ai.emailclaw.emailclaw.model.ChannelInfo finalChannel =
                                        channel;
                                javafx.scene.Node viewNode =
                                        provider.buildView(
                                                initialConfig,
                                                newConfig -> {
                                                    finalChannel.setEnabled(
                                                            (Boolean)
                                                                    newConfig.getOrDefault(
                                                                            "enabled", false));
                                                    finalChannel.setBotPrefix(
                                                            (String)
                                                                    newConfig.getOrDefault(
                                                                            "botPrefix", ""));
                                                    finalChannel.setPluginConfig(newConfig);
                                                    channelService.save();
                                                    btn.setText(
                                                            plugin.displayName()
                                                                    + " Settings "
                                                                    + (finalChannel.isEnabled()
                                                                            ? "✅"
                                                                            : "❌"));
                                                    dialogStage.close();
                                                },
                                                () -> {
                                                    dialogStage.close();
                                                });

                                javafx.scene.Scene scene =
                                        new javafx.scene.Scene(
                                                (javafx.scene.Parent) viewNode, 500, 700);
                                if (owner.getScene() != null) {
                                    scene.getStylesheets()
                                            .addAll(owner.getScene().getStylesheets());
                                }
                                dialogStage.setScene(scene);
                                dialogStage.setResizable(false);
                                dialogStage.showAndWait();
                            });
                    currentProjectBox.getChildren().add(btn);
                }
            }
        }
    }

    private void buildAgentManagementBox() {
        agentManagementBox = new VBox(8);
        Button backBtn = createBackButton(this::showMainSidebar);
        agentCombo.getItems().setAll(agentService.list());
        agentCombo.setValue(currentAgent);
        agentCombo.setCellFactory(v -> new AgentCell());
        agentCombo.setButtonCell(new AgentCell());
        agentCombo.setConverter(
                new javafx.util.StringConverter<AgentInfo>() {
                    @Override
                    public String toString(AgentInfo object) {
                        return object == null ? "" : object.getName();
                    }

                    @Override
                    public AgentInfo fromString(String string) {
                        return null;
                    }
                });
        agentCombo.setMaxWidth(Double.MAX_VALUE);
        agentCombo
                .valueProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (newV != null) {
                                currentAgent = newV;
                                agentService.setCurrentAgent(newV.getId());
                                try {
                                    toolRuntimeContext.refreshForAgent(currentAgent);
                                } catch (Exception ignored) {
                                }
                                views.values().forEach(v2 -> v2.onAgentChanged(currentAgent));
                                showView(currentViewId);
                                syncCodeModeButton();
                            }
                        });
        VBox agentCard = new VBox(8);
        agentCard.getChildren().addAll(backBtn, agentCombo);
        agentCard.getChildren().add(menuButton("Profile", ViewIds.AGENT_PROFILE));
        agentCard.getChildren().add(menuButton("Inbox", ViewIds.INBOX));
        agentCard.getChildren().add(menuButton("Heartbeat", ViewIds.HEARTBEAT));
        agentCard.getChildren().add(sectionTitle("Workspace"));
        agentCard.getChildren().add(menuButton("Files", ViewIds.FILES));
        agentCard.getChildren().add(menuButton("Skills Details", ViewIds.SKILLS));
        agentCard.getChildren().add(menuButton("Tools", ViewIds.TOOLS));
        agentCard.getChildren().add(menuButton("MCP", ViewIds.MCP));
        agentCard.getChildren().add(menuButton("ACP", ViewIds.ACP));
        agentCard.getChildren().add(menuButton("Configuration", ViewIds.CONFIGURATION));
        agentCard.getChildren().add(menuButton("Agent Statistics", ViewIds.AGENT_STATISTICS));
        agentManagementBox.getChildren().add(agentCard);
    }

    private void buildSystemSettingsBox() {
        systemSettingsBox = new VBox(8);
        Button backBtn = createBackButton(this::showMainSidebar);
        VBox sysCard = new VBox(8);
        sysCard.getChildren().addAll(backBtn, sectionTitle("System Settings"));
        sysCard.getChildren().add(menuButton("Models", ViewIds.MODELS));
        sysCard.getChildren().add(menuButton("Skill Pool", ViewIds.SKILL_POOL));
        sysCard.getChildren().add(menuButton("Skill Market", ViewIds.SKILL_MARKET));
        sysCard.getChildren().add(menuButton("Environments", ViewIds.ENVIRONMENTS));
        sysCard.getChildren().add(menuButton("Security", ViewIds.SECURITY));
        sysCard.getChildren().add(menuButton("Token Usage", ViewIds.TOKEN_USAGE));
        sysCard.getChildren().add(menuButton("Backups", ViewIds.BACKUPS));
        sysCard.getChildren().add(menuButton("Voice Transcription", ViewIds.VOICE_TRANSCRIPTION));
        sysCard.getChildren().add(menuButton("Debug", ViewIds.DEBUG));
        sysCard.getChildren().add(menuButton("Plug-in Manager", ViewIds.PLUGIN_MANAGER));
        systemSettingsBox.getChildren().add(sysCard);
    }

    private Button createBackButton(Runnable action) {
        Button b = new Button("← Back to app");
        b.getStyleClass().add("back-to-app-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(
                e -> {
                    if (action != null) {
                        action.run();
                    }
                });
        return b;
    }

    private void showMainSidebar() {
        sidebarArea.getChildren().setAll(currentProjectBox);
        showView(ViewIds.DASHBOARD);
    }

    private void showAgentManagementSidebar() {
        sidebarArea.getChildren().setAll(agentManagementBox);
    }

    private void showSystemSettingsSidebar() {
        sidebarArea.getChildren().setAll(systemSettingsBox);
    }

    private void selectAgentAndShowProfile(AgentInfo agent) {
        agentCombo.setValue(null);
        agentCombo.setValue(agent);
        showAgentManagementSidebar();
        showView(ViewIds.AGENT_PROFILE);
    }

    private Button actionButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("menu-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(
                e -> {
                    if (action != null) {
                        action.run();
                    }
                });
        return b;
    }

    private Button navLink(String name, String url) {
        Button b = new Button(name);
        b.getStyleClass().add("link-btn");
        if (url != null) {
            b.setOnAction(e -> openUrl(url));
        }
        return b;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Button menuButton(String text, String viewId) {
        Button b = new Button(text);
        b.getStyleClass().add("menu-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> showView(viewId));
        menuButtons.put(viewId, b);
        return b;
    }

    private void refreshMenuSelection() {
        for (Map.Entry<String, Button> entry : menuButtons.entrySet()) {
            Button button = entry.getValue();
            if (entry.getKey().equals(currentViewId)) {
                if (!button.getStyleClass().contains("selected-menu-btn")) {
                    button.getStyleClass().add("selected-menu-btn");
                }
            } else {
                button.getStyleClass().remove("selected-menu-btn");
            }
        }
    }

    private void onAgentCreated(AgentInfo newAgent) {
        LOGGER.log(Level.INFO, "New Agent creation completed and switched: {0}", newAgent.getId());
        agentCombo.getItems().setAll(agentService.list());
        agentCombo.setValue(newAgent);
        agentService.setCurrentAgent(newAgent.getId());
        showAgentManagementSidebar();
        showView(ViewIds.AGENT_MANAGEMENT);
    }

    private void onAgentProfileUpdated() {
        LOGGER.info("Agent Profile updated, syncing UI index");
        agentCombo.getItems().setAll(agentService.list());
        Optional<AgentInfo> found = agentService.findById(currentAgent.getId());
        if (found.isPresent()) {
            currentAgent = found.get();
        } else {
            currentAgent = agentService.currentDefault();
        }
        agentCombo.setValue(null);
        agentCombo.setValue(currentAgent);
        views.values().forEach(v -> v.onAgentChanged(currentAgent));
    }

    private void renderTasksList() {
        if (currentProject == null) return;
        List<ChatSessionInfo> projectTasks =
                chatService.sessions(currentAgent.getId()).stream()
                        .filter(
                                t ->
                                        currentProject.getId().equals(t.projectId())
                                                && ai.emailclaw.emailclaw.model.ChatSessionInfo
                                                        .KIND_TASK
                                                        .equals(t.getKind()))
                        .collect(Collectors.toList());
        tasksList.getChildren().clear();
        if (projectTasks.isEmpty()) {
            Label noTask = new Label("No task");
            noTask.getStyleClass().add("muted");
            noTask.setPadding(new Insets(4, 8, 4, 16));
            tasksList.getChildren().add(noTask);
            toggleAllBtn.setVisible(false);
        } else {
            int limit = tasksExpanded ? projectTasks.size() : Math.min(5, projectTasks.size());
            for (int i = 0; i < limit; i++) {
                ChatSessionInfo t = projectTasks.get(i);
                Button tBtn =
                        new Button(t.name() == null || t.name().isBlank() ? "(unnamed)" : t.name());
                tBtn.setMaxWidth(Double.MAX_VALUE);
                tBtn.setAlignment(Pos.CENTER_LEFT);
                tBtn.getStyleClass().add("menu-btn");
                tBtn.setOnAction(e -> showTaskView(t));
                HBox.setHgrow(tBtn, Priority.ALWAYS);
                HBox row = new HBox(2);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().add(tBtn);
                if (projectTasks.size() > 1 && i > 0) {
                    Button upBtn = new Button("↑");
                    upBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 4; -fx-cursor: hand;");
                    upBtn.setOnAction(e -> moveTask(t, "up", projectTasks));
                    row.getChildren().add(upBtn);
                }
                if (projectTasks.size() > 1 && i < projectTasks.size() - 1) {
                    Button downBtn = new Button("↓");
                    downBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 4; -fx-cursor: hand;");
                    downBtn.setOnAction(e -> moveTask(t, "down", projectTasks));
                    row.getChildren().add(downBtn);
                }
                /**
                 * Temporarily comment out pinBtn not to use; currently only provide upBtn and downBtn
                 *                Button pinBtn = new Button(t.isPinned() ? "🖈x" : "🖈"); //📌
                 *                pinBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 4; -fx-cursor: hand;");
                 *                pinBtn.setOnAction(e -> moveTask(t, t.isPinned() ? "unpin" : "pin", projectTasks));
                 *                row.getChildren().add(pinBtn);
                 */
                tasksList.getChildren().add(row);
            }
            toggleAllBtn.setText(tasksExpanded ? "Collapse" : "Expand");
            toggleAllBtn.setVisible(projectTasks.size() > 5);
        }
    }

    private void moveTask(ChatSessionInfo task, String action, List<ChatSessionInfo> projectTasks) {
        List<ChatSessionInfo> allSessions = repository.loadSessions();
        int currentIndex = -1;
        for (int i = 0; i < allSessions.size(); i++) {
            if (allSessions.get(i).getId().equals(task.getId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) return;
        int targetIndex = -1;
        if ("up".equals(action)) {
            for (int i = currentIndex - 1; i >= 0; i--) {
                ChatSessionInfo s = allSessions.get(i);
                if (ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK.equals(s.getKind())
                        && (currentProject == null
                                || currentProject.getId().equals(s.getProjectId()))) {
                    targetIndex = i;
                    break;
                }
            }
        } else if ("down".equals(action)) {
            for (int i = currentIndex + 1; i < allSessions.size(); i++) {
                ChatSessionInfo s = allSessions.get(i);
                if (ai.emailclaw.emailclaw.model.ChatSessionInfo.KIND_TASK.equals(s.getKind())
                        && (currentProject == null
                                || currentProject.getId().equals(s.getProjectId()))) {
                    targetIndex = i;
                    break;
                }
            }
        } else if ("pin".equals(action) || "unpin".equals(action)) {
            boolean pinning = "pin".equals(action);
            task.setPinned(pinning);
            allSessions.get(currentIndex).setPinned(pinning);
            int lastPinnedIndex = -1;
            for (int i = 0; i < allSessions.size(); i++) {
                if (allSessions.get(i).isPinned()) {
                    lastPinnedIndex = i;
                }
            }
            targetIndex = lastPinnedIndex + 1;
            if (targetIndex != -1 && targetIndex != currentIndex) {
                ChatSessionInfo temp = allSessions.remove(currentIndex);
                if (targetIndex > currentIndex) {
                    targetIndex--;
                }
                allSessions.add(targetIndex, temp);
                // skip swap logic below
                targetIndex = -1;
            }
        }
        if (targetIndex != -1) {
            ChatSessionInfo temp = allSessions.get(currentIndex);
            allSessions.set(currentIndex, allSessions.get(targetIndex));
            allSessions.set(targetIndex, temp);
        }
        repository.saveSessions(allSessions);
        renderTasksList();
    }

    private void showTaskView(ChatSessionInfo task) {
        String logId = task == null ? "new" : task.getId();
        LOGGER.log(java.util.logging.Level.INFO, "Switch task view: {0}", logId);
        TaskView taskView =
                (TaskView) views.computeIfAbsent(ViewIds.TASK, id -> factories.get(id).get());
        taskView.onAgentChanged(currentAgent);
        taskView.loadSession(task);
        showView(ViewIds.TASK);
        if (task == null) {
            renderTasksList();
        }
        refreshMenuSelection();
    }

    private void showView(String viewId) {
        LOGGER.log(Level.INFO, "Switch view: {0}", viewId);
        currentViewId = viewId;
        ViewPane pane = views.computeIfAbsent(viewId, id -> factories.get(id).get());
        pane.onAgentChanged(currentAgent);
        pane.refresh();
        content.getChildren().setAll(pane.root());
        refreshMenuSelection();
    }

    private static class ProjectButtonCell extends ListCell<ProjectInfo> {

        @Override
        protected void updateItem(ProjectInfo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getName());
            }
        }
    }

    private class ProjectListCell extends ListCell<ProjectInfo> {

        @Override
        protected void updateItem(ProjectInfo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(null);
                Label nameLabel = new Label(item.getName());
                HBox spacer = new HBox();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                if (!ProjectService.PROJECT_ID_DEFAULT.equals(item.getId())
                        && !item.getId().equals(currentProject.getId())) {
                    Button deleteBtn = new Button("🗑");
                    deleteBtn.setStyle(
                            "-fx-text-fill: #ef4444; -fx-background-color: transparent;"
                                + " -fx-font-weight: bold; -fx-padding: 0 4; -fx-cursor: hand;");
                    deleteBtn.addEventFilter(
                            javafx.scene.input.MouseEvent.MOUSE_PRESSED, Event::consume);
                    deleteBtn.addEventFilter(
                            javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                            e -> {
                                e.consume();
                                Platform.runLater(
                                        () -> {
                                            ProjectsView pv =
                                                    (ProjectsView)
                                                            views.computeIfAbsent(
                                                                    ViewIds.PROJECTS,
                                                                    id -> factories.get(id).get());
                                            pv.confirmDelete(item);
                                        });
                            });
                    HBox box = new HBox(6, nameLabel, spacer, deleteBtn);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                } else {
                    HBox box = new HBox(6, nameLabel, spacer);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        }
    }
}
