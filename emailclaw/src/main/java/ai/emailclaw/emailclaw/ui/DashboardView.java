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
import ai.emailclaw.emailclaw.model.AgentRuntimeStatus;
import ai.emailclaw.emailclaw.model.AgentStatus;
import ai.emailclaw.emailclaw.model.CronJobModel;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.ProviderStatus;
import ai.emailclaw.emailclaw.model.TokenUsageRecord;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.CronJobService;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.SkillService;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Dashboard view.
 *
 * <p>Aggregates core metrics such as Agents, Sessions, CronJobs, Models, and Token Usage to provide a system-wide overview.
 */
public class DashboardView implements ViewPane {
    private static final Logger LOGGER = Logger.getLogger(DashboardView.class.getName());

    private final AgentService agentService;
    private final ChatService chatService;
    private final CronJobService cronJobService;
    private final ProviderService providerService;
    private final ProjectService projectService;
    private final SkillService skillService;
    private final Consumer<String> router;
    private final Consumer<AgentInfo> onAgentSelected;
    private final AppContext repository;

    private final BorderPane root = new BorderPane();

    // Stat card labels (refreshed on each refresh())
    private final Label agentActiveLabel = new Label("0");
    private final Label agentTotalLabel = new Label("0");
    private final Label projectTotalLabel = new Label("0");
    private final Label taskTotalLabel = new Label("0");
    private final Label cronJobTotalLabel = new Label("0");
    private final Label cronJobEnabledLabel = new Label("0");
    private final Label modelReadyLabel = new Label("0");
    private final Label modelTotalLabel = new Label("0");
    private final Label tokenUsageLabel = new Label("0");

    // Agent cards container
    private final FlowPane agentCards = new FlowPane(16, 16);

    public DashboardView(
            AppContext repository,
            AgentService agentService,
            ChatService chatService,
            CronJobService cronJobService,
            ProviderService providerService,
            ProjectService projectService,
            SkillService skillService,
            Consumer<String> router,
            Consumer<AgentInfo> onAgentSelected) {
        this.repository = repository;
        this.agentService = agentService;
        this.chatService = chatService;
        this.cronJobService = cronJobService;
        this.providerService = providerService;
        this.projectService = projectService;
        this.skillService = skillService;
        this.router = router;
        this.onAgentSelected = onAgentSelected;
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(20);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(24));

        // --- Title ---
        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label subtitle = new Label("System overview at a glance");
        subtitle.getStyleClass().addAll("text-13-muted");
        VBox header = new VBox(4, title, subtitle);

        // --- Stat Cards Row ---
        HBox statsRow = new HBox(16);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        statsRow.getChildren()
                .addAll(
                        buildStatCard(
                                "🤖 Agents",
                                agentActiveLabel,
                                agentTotalLabel,
                                "active",
                                "total",
                                "#3b82f6",
                                () -> {
                                    if (router != null) router.accept(ViewIds.AGENT_MANAGEMENT);
                                }),
                        buildStatCard(
                                "📁 Projects",
                                projectTotalLabel,
                                null,
                                "total",
                                null,
                                "#0ea5e9",
                                () -> {
                                    if (router != null) router.accept(ViewIds.PROJECTS);
                                }),
                        buildStatCard(
                                "📝 Tasks",
                                taskTotalLabel,
                                null,
                                "total",
                                null,
                                "#10b981",
                                () -> {
                                    if (router != null) router.accept(ViewIds.SESSIONS_TASK);
                                }),
                        buildStatCard(
                                "⏰ Scheduled",
                                cronJobEnabledLabel,
                                cronJobTotalLabel,
                                "enabled",
                                "total",
                                "#f59e0b",
                                () -> {
                                    if (router != null) router.accept(ViewIds.CRON_JOBS);
                                }),
                        buildStatCard(
                                "🧠 Models",
                                modelReadyLabel,
                                modelTotalLabel,
                                "ready",
                                "total",
                                "#8b5cf6",
                                () -> {
                                    if (router != null) router.accept(ViewIds.MODELS);
                                }),
                        buildStatCard(
                                "📊 Token Usage",
                                tokenUsageLabel,
                                null,
                                "records",
                                null,
                                "#ec4899",
                                () -> {
                                    if (router != null) router.accept(ViewIds.TOKEN_USAGE);
                                }));

        // --- Active Agents Section ---
        Label agentsTitle = new Label("Active Agents");
        agentsTitle.getStyleClass().addAll("fw-700", "text-16", "text-secondary");

        agentCards.setPadding(new Insets(4, 0, 0, 0));
        agentCards.setAlignment(Pos.TOP_LEFT);

        // --- Quick Links Section ---
        Label linksTitle = new Label("Quick Navigation");
        linksTitle.getStyleClass().addAll("fw-700", "text-16", "text-secondary");

        FlowPane quickLinks = new FlowPane(12, 12);
        quickLinks.setAlignment(Pos.CENTER_LEFT);
        quickLinks
                .getChildren()
                .addAll(
                        buildQuickLink(
                                "Chat",
                                "Start a conversation",
                                "#3b82f6",
                                () -> {
                                    if (router != null) router.accept(ViewIds.CHAT);
                                }),
                        buildQuickLink(
                                "Projects",
                                "Manage your projects",
                                "#10b981",
                                () -> {
                                    if (router != null) router.accept(ViewIds.PROJECTS);
                                }),
                        buildQuickLink(
                                "Scheduled",
                                "Schedule automated tasks",
                                "#f59e0b",
                                () -> {
                                    if (router != null) router.accept(ViewIds.CRON_JOBS);
                                }),
                        buildQuickLink(
                                "Skills",
                                "Browse agent capabilities",
                                "#8b5cf6",
                                () -> {
                                    if (router != null) router.accept(ViewIds.SKILLS);
                                }),
                        buildQuickLink(
                                "Models",
                                "Configure AI models",
                                "#ec4899",
                                () -> {
                                    if (router != null) router.accept(ViewIds.MODELS);
                                }),
                        buildQuickLink(
                                "Security",
                                "Review security settings",
                                "#ef4444",
                                () -> {
                                    if (router != null) router.accept(ViewIds.SECURITY);
                                }));

        page.getChildren()
                .addAll(header, statsRow, agentsTitle, agentCards, linksTitle, quickLinks);

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page");
        root.setCenter(scroll);
    }

    /**
     * Build a statistics indicator card.
     */
    private Node buildStatCard(
            String title,
            Label primaryLabel,
            Label secondaryLabel,
            String primarySuffix,
            String secondarySuffix,
            String accentColor,
            Runnable onClick) {
        VBox card = new VBox(6);
        if (onClick != null) {
            card.setOnMouseClicked(e -> onClick.run());
            card.setStyle("-fx-cursor: hand;");
        }
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setMinWidth(175);
        card.setMaxWidth(220);
        card.getStyleClass().add("card-elevated");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().addAll("fw-600", "text-13-gray");

        primaryLabel.setStyle(
                "-fx-font-size: 28px; -fx-font-weight: 800; -fx-text-fill: " + accentColor + ";");

        HBox metrics = new HBox(4);
        metrics.setAlignment(Pos.BASELINE_LEFT);
        metrics.getChildren().add(primaryLabel);

        Label primarySufLabel = new Label(primarySuffix);
        primarySufLabel.getStyleClass().add("text-12-muted");
        metrics.getChildren().add(primarySufLabel);

        if (secondaryLabel != null && secondarySuffix != null) {
            Label sep = new Label("/");
            sep.getStyleClass().add("text-14-dim");
            secondaryLabel.getStyleClass().addAll("fw-600", "text-16", "text-gray-light");
            Label secSuf = new Label(secondarySuffix);
            secSuf.getStyleClass().add("text-12-dim");
            metrics.getChildren().addAll(sep, secondaryLabel, secSuf);
        }

        card.getChildren().addAll(titleLabel, metrics);
        return card;
    }

    /**
     * Build a status card for each Agent.
     */
    private Node buildAgentCard(AgentInfo agent, AgentRuntimeStatus status) {
        VBox card = new VBox(8);
        if (onAgentSelected != null) {
            card.setOnMouseClicked(e -> onAgentSelected.accept(agent));
        }
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setMinWidth(200);
        card.setMaxWidth(260);

        String borderColor;
        String statusDot;
        String statusText;
        if (!agent.isEnabled()) {
            borderColor = "#d1d5db";
            statusDot = "⚫";
            statusText = "disabled";
        } else if (AgentStatus.RUNNING == status.status()) {
            borderColor = "#10b981";
            statusDot = "🟢";
            statusText = "running (" + status.runningTaskCount() + ")";
        } else {
            borderColor = "#3b82f6";
            statusDot = "🔵";
            statusText = "idle";
        }

        card.setStyle(
                "-fx-background-color: white;"
                        + " -fx-background-radius: 10;"
                        + " -fx-border-color: "
                        + borderColor
                        + ";"
                        + " -fx-border-radius: 10;"
                        + " -fx-border-width: 1.5;"
                        + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.03), 6, 0, 0, 1);"
                        + (onAgentSelected != null ? " -fx-cursor: hand;" : ""));

        Label nameLabel = new Label("🤖 " + agent.getName());
        nameLabel.getStyleClass().addAll("fw-700");
        nameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #1f2937;");

        Label statusLabel = new Label(statusDot + " " + statusText);
        statusLabel.getStyleClass().addAll("text-12", "text-gray");

        String modelText = agent.getModelId().isBlank() ? "default model" : agent.getModelId();
        Label modelLabel = new Label("🧠 " + modelText);
        modelLabel.getStyleClass().add("text-11-muted");
        modelLabel.setWrapText(true);

        long validSkillsCount = skillService.listWorkspaceSkills(agent.getId()).size();
        Label skillsLabel = new Label("🔧 " + validSkillsCount + " skills");
        skillsLabel.getStyleClass().add("text-11-muted");

        card.getChildren().addAll(nameLabel, statusLabel, modelLabel, skillsLabel);
        return card;
    }

    /**
     * Build a quick navigation entry block.
     */
    private Node buildQuickLink(String name, String desc, String color, Runnable onClick) {
        VBox link = new VBox(4);
        if (onClick != null) {
            link.setOnMouseClicked(e -> onClick.run());
            link.setStyle("-fx-cursor: hand;");
        }
        link.setPadding(new Insets(12, 16, 12, 16));
        link.setMinWidth(170);
        link.getStyleClass().add("card-elevated-sm");

        Label nameLabel = new Label(name);
        nameLabel.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + color + ";");

        Label descLabel = new Label(desc);
        descLabel.getStyleClass().add("text-11-muted");

        link.getChildren().addAll(nameLabel, descLabel);
        return link;
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        LOGGER.fine("Refresh dashboard data");

        // --- Agents ---
        List<AgentInfo> agents = agentService.list();
        long activeCount =
                agents.stream()
                        .filter(a -> a.isEnabled())
                        .filter(
                                a ->
                                        AgentStatus.DISABLED
                                                != agentService.statusOf(a.getId()).status())
                        .count();
        agentActiveLabel.setText(String.valueOf(activeCount));
        agentTotalLabel.setText(String.valueOf(agents.size()));

        // --- Projects ---
        int projectCount = projectService.list().size();
        projectTotalLabel.setText(String.valueOf(projectCount));

        // --- Tasks ---
        int taskCount = 0;
        for (AgentInfo a : agents) {
            taskCount +=
                    (int)
                            chatService.sessions(a.getId()).stream()
                                    .filter(
                                            s ->
                                                    ai.emailclaw.emailclaw.model.ChatSessionInfo
                                                            .KIND_TASK
                                                            .equals(s.getKind()))
                                    .count();
        }
        taskTotalLabel.setText(String.valueOf(taskCount));

        // --- Scheduled ---
        List<CronJobModel.CronJobSpec> jobs = cronJobService.list();
        long enabledJobs = jobs.stream().filter(CronJobModel.CronJobSpec::enabled).count();
        cronJobEnabledLabel.setText(String.valueOf(enabledJobs));
        cronJobTotalLabel.setText(String.valueOf(jobs.size()));

        // --- Models ---
        List<ProviderInfo> providers = providerService.listProviders();
        long readyProviders =
                providers.stream()
                        .filter(p -> providerService.status(p) == ProviderStatus.READY_WITH_MODELS)
                        .count();
        long totalModels = providers.stream().mapToLong(p -> p.allModels().size()).sum();
        modelReadyLabel.setText(String.valueOf(readyProviders));
        modelTotalLabel.setText(String.valueOf(totalModels));

        // --- Token Usage ---
        List<TokenUsageRecord> records = repository.configManager().getTokenUsageRecords();
        tokenUsageLabel.setText(String.valueOf(records.size()));

        // --- Agent cards ---
        agentCards.getChildren().clear();
        for (AgentInfo agent : agents) {
            AgentRuntimeStatus status = agentService.statusOf(agent.getId());
            agentCards.getChildren().add(buildAgentCard(agent, status));
        }
    }
}
