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

import ai.emailclaw.emailclaw.channel.ChannelIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.CronJobModel;
import ai.emailclaw.emailclaw.model.CronJobModel.CronExecutionRecord;
import ai.emailclaw.emailclaw.model.CronJobModel.CronJobSpec;
import ai.emailclaw.emailclaw.model.CronJobModel.CronJobState;
import ai.emailclaw.emailclaw.model.CronJobModel.DispatchSpec;
import ai.emailclaw.emailclaw.model.CronJobModel.DispatchTarget;
import ai.emailclaw.emailclaw.model.CronJobModel.JobRuntimeSpec;
import ai.emailclaw.emailclaw.model.CronJobModel.ScheduleSpec;
import ai.emailclaw.emailclaw.model.CronJobStatus;
import ai.emailclaw.emailclaw.model.DeliveryMode;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.service.CronJobService;
import ai.emailclaw.emailclaw.service.ProjectService;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import tools.jackson.databind.ObjectMapper;

/**
 * Cron job management view.
 *
 * <p>Functionality fully aligned with Emailclaw Console's CronJobsPage:
 * <br>- List view (table display, supports filtering by schedule type)
 * <br>- Create/edit task popup (complete form fields)
 * <br>- Delete confirmation popup
 * <br>- Execute immediately confirmation popup
 * <br>- Execution history view popup
 * <br>- Enable/disable toggle
 */
public class CronJobListView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(CronJobListView.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CronJobService cronService;

    private final StackPane root = new StackPane();

    private final VBox contentBox = new VBox(14);

    private final VBox jobList = new VBox(8);

    private final Label statusLabel = new Label();

    private AgentInfo currentAgent;

    private ProjectInfo currentProject;

    private Consumer<ChatSessionInfo> onOpenTask;

    /**
     * Currently filtered schedule type.
     */
    private String scheduleTypeFilter = "all";

    /**
     * Currently edited task (null means new).
     */
    private CronJobSpec editingJob;

    /**
     * Cached history dialog to avoid repeated creation.
     */
    private Dialog<Void> historyDialog;

    /**
     * Currently active confirmation alert for immediate execution.
     */
    private Dialog<?> activeConfirmationAlert;

    /**
     * Get the active confirmation alert dialog if currently displayed.
     *
     * @return Active confirmation dialog, or null
     */
    public Dialog<?> getActiveConfirmationAlert() {
        return activeConfirmationAlert;
    }

    /**
     * Get the active history dialog if currently displayed.
     *
     * @return Active history dialog, or null
     */
    public Dialog<Void> getActiveHistoryDialog() {
        return historyDialog;
    }

    /**
     * Built-in template record
     */
    record Template(
            String name,
            String description,
            String scheduleType,
            String cron,
            String inputPrompt,
            String channel) {}

    public CronJobListView(CronJobService cronService, AgentInfo agent) {
        this(cronService, agent, ProjectService.PROJECT_DEFAULT, null);
    }

    public CronJobListView(CronJobService cronService, AgentInfo agent, ProjectInfo project) {
        this(cronService, agent, project, null);
    }

    public CronJobListView(
            CronJobService cronService,
            AgentInfo agent,
            ProjectInfo project,
            Consumer<ChatSessionInfo> onOpenTask) {
        this.cronService = cronService;
        this.currentAgent = agent;
        this.currentProject = project;
        this.onOpenTask = onOpenTask;
        initUi();
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
     * Retrieve the current normalized project ID ("default" if null or blank).
     */
    private String getCurrentProjectId() {
        if (currentProject.getId().isBlank()) {
            return "default";
        }
        return currentProject.getId();
    }

    private void initUi() {
        contentBox.getStyleClass().add("page");
        contentBox.setPadding(new Insets(18));
        // ===== Title Row =====
        Label title = new Label("Project / Scheduled");
        title.getStyleClass().add("page-title");
        Label subtitle =
                new Label(
                        "Schedule automated tasks with cron expressions. Jobs run on the configured"
                                + " schedule and dispatch results to the target channel.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);
        // ===== Top Action Bar =====
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        // Schedule type filter
        ToggleGroup filterGroup = new ToggleGroup();
        ToggleButton allBtn = createFilterToggle("All", "all", filterGroup, true);
        ToggleButton enabledBtn = createFilterToggle("Enabled", "enabled", filterGroup, false);
        ToggleButton disabledBtn = createFilterToggle("Disabled", "disabled", filterGroup, false);
        allBtn.setOnAction(
                e -> {
                    scheduleTypeFilter = "all";
                    renderJobs();
                });
        enabledBtn.setOnAction(
                e -> {
                    scheduleTypeFilter = "enabled";
                    renderJobs();
                });
        disabledBtn.setOnAction(
                e -> {
                    scheduleTypeFilter = "disabled";
                    renderJobs();
                });
        HBox filterBox = new HBox(0, allBtn, enabledBtn, disabledBtn);
        filterBox.getStyleClass().addAll("border-e5", "radius-6");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button createBtn = new Button("+ Create Job");
        createBtn.setId("btn-create-job");
        createBtn.getStyleClass().add("primary-btn");
        createBtn.setOnAction(e -> showCreateDialog());
        Button templateBtn = new Button("From Template");
        templateBtn.getStyleClass().add("chip-btn");
        templateBtn.setOnAction(e -> showTemplatePicker());
        statusLabel.getStyleClass().add("muted");
        toolbar.getChildren().addAll(filterBox, spacer, statusLabel, templateBtn, createBtn);
        // ===== Table Header =====
        HBox tableHead = buildTableHead();
        // ===== Task List =====
        ScrollPane scroll = new ScrollPane(jobList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("left-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        contentBox.getChildren().addAll(title, subtitle, toolbar, tableHead, scroll);
        root.getChildren().add(contentBox);
        renderJobs();
    }

    private ToggleButton createFilterToggle(
            String text, String value, ToggleGroup group, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setSelected(selected);
        btn.setUserData(value);
        btn.getStyleClass().add("tab-inactive");
        btn.setOnAction(
                e -> {
                    scheduleTypeFilter = (String) btn.getUserData();
                    renderJobs();
                });
        btn.selectedProperty()
                .addListener(
                        (obs, oldVal, newVal) -> {
                            if (newVal) {
                                btn.setStyle(
                                        "-fx-background-radius: 0; -fx-padding: 6 16;"
                                            + " -fx-background-color: #ff8800; -fx-border-color:"
                                            + " #e67a00; -fx-text-fill: #111; -fx-font-weight:"
                                            + " 600;");
                            } else {
                                btn.setStyle(
                                        "-fx-background-radius: 0; -fx-padding: 6 16;"
                                            + " -fx-background-color: #f5f5f5; -fx-border-color:"
                                            + " #e5e5e5; -fx-text-fill: #555;");
                            }
                        });
        // Initial selected state style
        if (selected) {
            btn.getStyleClass().add("tab-active");
        }
        return btn;
    }

    private Label headerCol(String text, double width) {
        Label l = new Label(text);
        l.setPrefWidth(width);
        l.setStyle("-fx-font-weight: 600; -fx-text-fill: #888; -fx-font-size: 12px;");
        return l;
    }

    private void renderJobs() {
        LOGGER.fine(
                () ->
                        "Refreshing Cron task list for project: "
                                + getCurrentProjectId()
                                + ", filter condition: "
                                + scheduleTypeFilter);
        jobList.getChildren().clear();
        List<CronJobSpec> allJobs = cronService.listByProject(getCurrentProjectId());
        // Filter
        List<CronJobSpec> filtered;
        if ("enabled".equals(scheduleTypeFilter)) {
            filtered = allJobs.stream().filter(CronJobSpec::enabled).toList();
        } else if ("disabled".equals(scheduleTypeFilter)) {
            filtered = allJobs.stream().filter(j -> !j.enabled()).toList();
        } else {
            filtered = allJobs;
        }
        statusLabel.setText("Total: " + allJobs.size() + " jobs");
        if (filtered.isEmpty()) {
            Label empty =
                    new Label(
                            allJobs.isEmpty()
                                    ? "No cron jobs configured. Click \"+ Create Job\" to add one."
                                    : "No jobs match the current filter.");
            empty.getStyleClass().add("muted");
            empty.setPadding(new Insets(40));
            empty.setAlignment(Pos.CENTER);
            jobList.getChildren().add(empty);
            return;
        }
        for (CronJobSpec job : filtered) {
            jobList.getChildren().add(buildJobRow(job, this::renderJobs));
        }
    }

    public HBox buildTableHead() {
        HBox tableHead = new HBox();
        tableHead.getStyleClass().add("row-lite");
        tableHead.setStyle("-fx-background-radius: 0; -fx-border-radius: 0;");
        tableHead.setSpacing(0);
        tableHead.setPadding(new Insets(6, 8, 6, 8));
        tableHead
                .getChildren()
                .addAll(
                        headerCol("Enabled", 70),
                        headerCol("Name", 150),
                        headerCol("Schedule", 180),
                        headerCol("Next Run", 160),
                        headerCol("Timezone", 120),
                        headerCol("Channel", 100),
                        headerCol("Actions", 280));
        return tableHead;
    }

    public Node buildJobsTable(List<CronJobSpec> jobs, Runnable onRefresh) {
        VBox container = new VBox(0);
        container.getStyleClass().add("pane-card");
        container.setStyle("-fx-background-radius: 0; -fx-border-radius: 0;");
        container.getChildren().add(buildTableHead());
        VBox rowsBox = new VBox(0);
        if (jobs == null || jobs.isEmpty()) {
            Label empty = new Label("No automated tasks.");
            empty.getStyleClass().add("muted");
            empty.setPadding(new Insets(20));
            empty.setAlignment(Pos.CENTER);
            rowsBox.getChildren().add(empty);
        } else {
            for (CronJobSpec job : jobs) {
                rowsBox.getChildren().add(buildJobRow(job, onRefresh));
            }
        }
        container.getChildren().add(rowsBox);
        return container;
    }

    public Node buildJobRow(CronJobSpec job, Runnable onRefresh) {
        HBox row = new HBox();
        row.getStyleClass().add("row-lite");
        row.setSpacing(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-radius: 0; -fx-border-radius: 0; -fx-cursor: hand;");
        // Enabled CheckBox
        CheckBox enabledCb = new CheckBox();
        enabledCb.setPrefWidth(70);
        enabledCb.setSelected(job.enabled());
        enabledCb.setOnAction(
                e -> {
                    cronService.toggleEnabled(job.id());
                    if (onRefresh != null) onRefresh.run();
                });
        // Name
        Label nameLbl =
                new Label(job.name() != null && !job.name().isBlank() ? job.name() : "(unnamed)");
        nameLbl.setPrefWidth(150);
        nameLbl.getStyleClass().add("fw-600");
        Tooltip.install(nameLbl, new Tooltip("ID: " + job.id()));
        // Schedule
        String schedType = job.schedule() != null ? job.schedule().type() : "?";
        String schedDesc = CronJobModel.describeSchedule(job.schedule());
        Label schedLbl = new Label(schedDesc);
        schedLbl.setPrefWidth(180);
        schedLbl.getStyleClass().add("muted");
        Tooltip.install(schedLbl, new Tooltip("Type: " + schedType));
        // Next Run
        ai.emailclaw.emailclaw.model.CronJobModel.CronJobState state =
                cronService.getState(job.id());
        String nextRun = state != null && state.nextRunAt() != null ? state.nextRunAt() : "-";
        if (!"-".equals(nextRun)) {
            try {
                java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(nextRun);
                nextRun =
                        zdt.format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ignored) {
            }
        }
        Label nextRunLbl = new Label(nextRun);
        nextRunLbl.setPrefWidth(160);
        nextRunLbl.getStyleClass().add("muted");
        // Timezone
        Label tzLbl = new Label(job.schedule() != null ? job.schedule().timezone() : "-");
        tzLbl.setPrefWidth(120);
        tzLbl.getStyleClass().add("muted");
        // Channel
        String channel = job.dispatch() != null ? job.dispatch().channel() : "-";
        Label chLbl = new Label(channel);
        chLbl.setPrefWidth(100);
        // ===== Action Buttons Column =====
        HBox actions = new HBox(6);
        actions.setPrefWidth(280);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button runBtn = new Button("Run");
        runBtn.setId("btn-run-job-" + (job.name() != null ? job.name() : job.id()));
        runBtn.getStyleClass().addAll("chip-btn", "btn-run-job");
        runBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        runBtn.setOnAction(
                e -> {
                    confirmExecuteNow(job);
                    if (onRefresh != null) onRefresh.run();
                });
        Button historyBtn = new Button("History");
        historyBtn.setId("btn-history-job-" + (job.name() != null ? job.name() : job.id()));
        historyBtn.getStyleClass().addAll("chip-btn", "btn-history-job");
        historyBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        historyBtn.setOnAction(e -> showHistoryDialog(job));
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("chip-btn");
        editBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        editBtn.setOnAction(
                e -> {
                    showEditDialog(job);
                    if (onRefresh != null) onRefresh.run();
                });
        Button delBtn = new Button("Del");
        delBtn.getStyleClass().add("chip-btn");
        delBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8; -fx-text-fill: #e74c3c;");
        delBtn.setOnAction(
                e -> {
                    confirmDelete(job);
                    if (onRefresh != null) onRefresh.run();
                });
        actions.getChildren().addAll(runBtn, historyBtn, editBtn, delBtn);
        row.getChildren().addAll(enabledCb, nameLbl, schedLbl, nextRunLbl, tzLbl, chLbl, actions);
        // Double click to edit
        row.setOnMouseClicked(
                e -> {
                    if (e.getClickCount() == 2) {
                        showEditDialog(job);
                        if (onRefresh != null) onRefresh.run();
                    }
                });
        return row;
    }

    // ======================== Create / Edit Dialog ========================
    private void showCreateDialog() {
        editingJob = null;
        showJobDialog(null, "Create Scheduled");
    }

    private void showEditDialog(CronJobSpec job) {
        editingJob = job;
        showJobDialog(job, "Edit Scheduled");
    }

    /**
     * Unified create setting dialog.
     * <p>Contains complete fields: name, enabled, save to Inbox, schedule type (cron/once),
     * cron expression or one-time time, timezone, task type (text/agent),
     * text content or input prompt, dispatch channel/user/session/mode, runtime parameters.
     */
    private void showJobDialog(CronJobSpec existing, String title) {
        Dialog<CronJobSpec> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(
                existing == null
                        ? "Schedule a new automated task"
                        : "Edit job: " + existing.name());
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        // ===== Form Fields =====
        TextField nameField = new TextField(existing != null ? existing.name() : "");
        nameField.setId("field-job-name");
        nameField.setPromptText("e.g. Morning Brief");
        CheckBox enabledCb = new CheckBox("Enabled");
        enabledCb.setSelected(existing == null || existing.enabled());
        CheckBox inboxCb = new CheckBox("Save result to Inbox");
        inboxCb.setSelected(
                existing == null
                        ? CronJobModel.DEFAULTS.saveResultToInbox()
                        : (existing.saveResultToInbox() != null && existing.saveResultToInbox()));
        CronExpressionEditor cronEditor = new CronExpressionEditor();
        cronEditor.setCronExpression(
                existing != null && existing.schedule() != null
                        ? existing.schedule().cron()
                        : "0 9 * * *");
        ComboBox<String> tzBox = new ComboBox<>();
        tzBox.getItems()
                .addAll(
                        "Asia/Shanghai",
                        "Asia/Tokyo",
                        "Asia/Singapore",
                        "Asia/Hong_Kong",
                        "Asia/Kolkata",
                        "Asia/Dubai",
                        "America/New_York",
                        "America/Chicago",
                        "America/Denver",
                        "America/Los_Angeles",
                        "Europe/London",
                        "Europe/Paris",
                        "Europe/Berlin",
                        "Europe/Moscow",
                        "Pacific/Auckland",
                        "Australia/Sydney",
                        "UTC");
        tzBox.setValue(
                existing != null
                                && existing.schedule() != null
                                && existing.schedule().timezone() != null
                                && !existing.schedule().timezone().isBlank()
                        ? existing.schedule().timezone()
                        : CronJobModel.getDefaultTimezone());
        tzBox.setEditable(true);
        TextArea textArea =
                new TextArea(
                        existing != null && existing.inputPrompt() != null
                                ? existing.inputPrompt()
                                : "");
        textArea.setId("field-job-prompt");
        textArea.setPrefRowCount(3);
        textArea.setPromptText("Message text / Input Prompt");
        ComboBox<String> channelBox = new ComboBox<>();
        channelBox.setId("combo-target-channel");
        channelBox
                .getItems()
                .addAll(ChannelIds.CONSOLE, ChannelIds.EMAILCLAW); // ChannelIds.DINGTALK,
        String initialChannel =
                existing != null && existing.dispatch() != null
                        ? existing.dispatch().channel()
                        : ChannelIds.CONSOLE;
        channelBox.setValue(initialChannel);
        ComboBox<String> sessionBox = new ComboBox<>();
        sessionBox.setId("combo-target-session");
        sessionBox.setPromptText("Select a session (optional, auto-create if empty)");
        sessionBox.setEditable(false);
        sessionBox.setValue(null);
        // Filter sessions based on current agent and selected channel
        Runnable refreshSessions =
                () -> {
                    String ch = channelBox.getValue();
                    List<ChatSessionInfo> all = cronService.sessions(currentAgent.getId());
                    List<ChatSessionInfo> filtered =
                            ch == null || ch.isBlank()
                                    ? all
                                    : all.stream().filter(s -> ch.equals(s.getChannel())).toList();
                    filtered =
                            filtered.stream()
                                    .filter(s -> ChatSessionInfo.KIND_TASK.equals(s.getKind()))
                                    .filter(
                                            s -> {
                                                String sProj =
                                                        s.getProjectId().isBlank()
                                                                ? "default"
                                                                : s.getProjectId();
                                                return getCurrentProjectId().equals(sProj);
                                            })
                                    .toList();
                    ObservableList<String> items =
                            FXCollections.observableArrayList(
                                    filtered.stream()
                                            .map(s -> s.getId() + " (" + s.getName() + ")")
                                            .toList());
                    sessionBox.setItems(items);
                };
        refreshSessions.run();
        channelBox.setOnAction(e -> refreshSessions.run());
        String initialSessionId =
                existing != null
                                && existing.dispatch() != null
                                && existing.dispatch().target() != null
                        ? existing.dispatch().target().sessionId()
                        : null;
        if (initialSessionId != null
                && !initialSessionId.isBlank()
                && !"default".equals(initialSessionId)) {
            // Find the matching session and select it
            List<ChatSessionInfo> all = cronService.sessions(currentAgent.getId());
            ChatSessionInfo match =
                    all.stream()
                            .filter(s -> initialSessionId.equals(s.getId()))
                            .findFirst()
                            .orElse(null);
            if (match != null) {
                sessionBox.setValue(match.getId() + " (" + match.getName() + ")");
            }
        }
        sessionBox.setOnAction(
                e -> {
                    String selected = sessionBox.getValue();
                    if (selected != null && !selected.isBlank()) {
                        int parenIdx = selected.indexOf(" (");
                        if (parenIdx > 0 && selected.endsWith(")")) {
                            String sessionName =
                                    selected.substring(parenIdx + 2, selected.length() - 1).trim();
                            if (!sessionName.isBlank()) {
                                String current = textArea.getText();
                                if (current == null || current.isBlank()) {
                                    textArea.setText(sessionName);
                                } else {
                                    String trimmed = current.trim();
                                    boolean alreadyAppended =
                                            trimmed.equals(sessionName)
                                                    || trimmed.endsWith("\n" + sessionName)
                                                    || trimmed.startsWith(sessionName + "\n")
                                                    || trimmed.contains("\n" + sessionName + "\n");
                                    if (!alreadyAppended) {
                                        if (current.endsWith("\n")) {
                                            textArea.appendText(sessionName);
                                        } else {
                                            textArea.appendText("\n" + sessionName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
        ComboBox<DeliveryMode> modeBox = new ComboBox<>();
        modeBox.getItems().addAll(DeliveryMode.FINAL, DeliveryMode.STREAM);
        modeBox.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(DeliveryMode object) {
                        return object != null ? object.getValue() : "";
                    }

                    @Override
                    public DeliveryMode fromString(String string) {
                        return DeliveryMode.fromValue(string);
                    }
                });
        modeBox.setValue(
                existing != null
                                && existing.dispatch() != null
                                && existing.dispatch().mode() != null
                        ? existing.dispatch().mode()
                        : DeliveryMode.FINAL);
        // Runtime fields
        TextField concurrencyField =
                new TextField(
                        existing != null && existing.runtime() != null
                                ? String.valueOf(existing.runtime().maxConcurrency())
                                : "1");
        TextField timeoutField =
                new TextField(
                        existing != null && existing.runtime() != null
                                ? String.valueOf(existing.runtime().timeoutSeconds())
                                : "120");
        CheckBox shareSessionCb = new CheckBox("Share session context");
        shareSessionCb.setSelected(
                existing == null
                        || existing.runtime() == null
                        || existing.runtime().shareSession());
        // ===== Form Layout =====
        TabPane tabPane = new TabPane();
        tabPane.setId("tab-pane-job");
        // Tab 1: Basic Information
        VBox basicBox = new VBox(12);
        basicBox.setPadding(new Insets(16));
        GridPane basicGrid = new GridPane();
        basicGrid.setHgap(12);
        basicGrid.setVgap(10);
        int r = 0;
        basicGrid.addRow(r++, new Label("Job Name *"), nameField);
        basicGrid.addRow(r++, new Label("Enabled"), enabledCb);
        basicGrid.addRow(r++, new Label("Save to Inbox"), inboxCb);
        basicGrid.addRow(r++, new Label("Timezone"), tzBox);
        CheckBox countdownCb = new CheckBox();
        Label countdownLbl = new Label("Limit the remaining executions for this task:");
        TextField countdownField = new TextField();
        countdownField.setPrefWidth(100);
        boolean hasCountdown =
                existing != null && existing.countdown() != null && existing.countdown() > 0;
        countdownCb.setSelected(hasCountdown);
        if (hasCountdown) {
            countdownField.setText(String.valueOf(existing.countdown()));
            countdownField.setDisable(false);
        } else {
            countdownField.setText("No limit");
            countdownField.setDisable(true);
        }
        countdownCb.setOnAction(
                e -> {
                    if (countdownCb.isSelected()) {
                        countdownField.setDisable(false);
                        if ("No limit".equals(countdownField.getText())
                                || countdownField.getText().isEmpty()) {
                            countdownField.setText("1");
                        }
                    } else {
                        countdownField.setDisable(true);
                        countdownField.setText("No limit");
                    }
                });
        countdownField
                .textProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (newV != null && !newV.equals("No limit") && !newV.isEmpty()) {
                                if (!newV.matches("[1-9]\\d*")) {
                                    countdownField.setText(
                                            oldV != null && oldV.matches("[1-9]\\d*") ? oldV : "1");
                                }
                            }
                        });
        HBox countdownBox = new HBox(8, countdownCb, countdownLbl, countdownField);
        countdownBox.setAlignment(Pos.CENTER_LEFT);
        basicBox.getChildren()
                .addAll(basicGrid, new Label("Schedule (Cron):"), cronEditor, countdownBox);
        Tab basicTab = new Tab("Basic", new ScrollPane(basicBox));
        basicTab.setClosable(false);
        // Tab 2: Task
        VBox taskBox = new VBox(10);
        taskBox.setPadding(new Insets(16));
        taskBox.getChildren().addAll(new Label("Task Prompt:"), textArea);
        Tab taskTab = new Tab("Task", new ScrollPane(taskBox));
        taskTab.setClosable(false);
        // Tab 3: Dispatch
        GridPane dispatchGrid = new GridPane();
        dispatchGrid.setHgap(12);
        dispatchGrid.setVgap(10);
        dispatchGrid.setPadding(new Insets(16));
        int d = 0;
        dispatchGrid.addRow(d++, new Label("Target Channel"), channelBox);
        dispatchGrid.addRow(d++, new Label("Target Session"), sessionBox);
        dispatchGrid.addRow(d++, new Label("Delivery Mode"), modeBox);
        Tab dispatchTab = new Tab("Dispatch", new ScrollPane(dispatchGrid));
        dispatchTab.setClosable(false);
        // Tab 4: Runtime
        GridPane rtGrid = new GridPane();
        rtGrid.setHgap(12);
        rtGrid.setVgap(10);
        rtGrid.setPadding(new Insets(16));
        int r4 = 0;
        rtGrid.addRow(r4++, new Label("Max Concurrency"), concurrencyField);
        rtGrid.addRow(r4++, new Label("Timeout (seconds)"), timeoutField);
        rtGrid.addRow(r4++, new Label("Session Strategy"), shareSessionCb);
        Tab rtTab = new Tab("Runtime", new ScrollPane(rtGrid));
        rtTab.setClosable(false);
        tabPane.getTabs().addAll(basicTab, taskTab, dispatchTab, rtTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Label validationHint = new Label();
        validationHint.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        validationHint.setWrapText(true);
        validationHint.setPadding(new Insets(4, 16, 4, 16));

        VBox dialogContent = new VBox(6, tabPane, validationHint);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        dialog.getDialogPane().setContent(dialogContent);
        dialog.getDialogPane().setPrefWidth(620);
        dialog.getDialogPane().setPrefHeight(650);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        if (saveButton != null) {
            saveButton.setId("btn-save-job");
            if (!saveButton.getStyleClass().contains("btn-primary")) {
                saveButton.getStyleClass().add("btn-primary");
            }
        }

        Runnable refreshSaveState =
                () -> {
                    String error = null;
                    if (nameField.getText() == null || nameField.getText().trim().isBlank()) {
                        error = "Job name is required.";
                    } else if (textArea.getText() == null || textArea.getText().trim().isBlank()) {
                        error = "Task Prompt is required.";
                    } else if (cronEditor.getCronExpression() == null
                            || cronEditor.getCronExpression().isBlank()) {
                        error = "Cron expression is required.";
                    } else if (tzBox.getValue() == null || tzBox.getValue().trim().isBlank()) {
                        error = "Timezone is required.";
                    } else if (concurrencyField.getText() == null
                            || !concurrencyField.getText().trim().matches("[1-9]\\d*")) {
                        error = "Max Concurrency must be a positive integer.";
                    } else if (timeoutField.getText() == null
                            || !timeoutField.getText().trim().matches("[1-9]\\d*")) {
                        error = "Timeout must be a positive integer.";
                    } else if (countdownCb.isSelected()
                            && (countdownField.getText() == null
                                    || !countdownField.getText().trim().matches("[1-9]\\d*"))) {
                        error = "Countdown limit must be a positive integer.";
                    }

                    boolean valid = (error == null);
                    if (saveButton != null) {
                        saveButton.setDisable(!valid);
                    }
                    validationHint.setText(valid ? "" : error);
                };

        nameField.textProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());
        textArea.textProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());
        cronEditor
                .cronExpressionProperty()
                .addListener((obs, oldV, newV) -> refreshSaveState.run());
        tzBox.valueProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());
        tzBox.getEditor().textProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());
        concurrencyField.textProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());
        timeoutField.textProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());
        countdownCb.selectedProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());
        countdownField.textProperty().addListener((obs, oldV, newV) -> refreshSaveState.run());

        refreshSaveState.run();

        // ===== Result Conversion =====
        dialog.setResultConverter(
                btn -> {
                    if (btn != saveType) return null;
                    String name = nameField.getText().trim();
                    if (name.isBlank()) {
                        return null;
                    }
                    ScheduleSpec schedule =
                            new ScheduleSpec(
                                    "cron",
                                    cronEditor.getCronExpression(),
                                    null,
                                    tzBox.getValue(),
                                    null,
                                    null,
                                    null,
                                    null);
                    // Target session optional: if not selected, service layer will auto-create new
                    // session by task name after saving
                    String sessionVal = sessionBox.getValue();
                    String parsedSessionId = "";
                    if (sessionVal != null && !sessionVal.isBlank()) {
                        int parenIdx = sessionVal.indexOf(" (");
                        parsedSessionId =
                                (parenIdx > 0) ? sessionVal.substring(0, parenIdx) : sessionVal;
                    }
                    final String selectedSessionId = parsedSessionId;
                    LOGGER.info(
                            "Saving scheduled task, target session: "
                                    + (selectedSessionId.isBlank()
                                            ? "(Not selected, will auto-create)"
                                            : selectedSessionId));
                    String textContent =
                            textArea.getText() != null ? textArea.getText().trim() : "";
                    if (textContent.isBlank() && !selectedSessionId.isBlank()) {
                        List<ChatSessionInfo> all = cronService.sessions(currentAgent.getId());
                        ChatSessionInfo match =
                                all.stream()
                                        .filter(s -> selectedSessionId.equals(s.getId()))
                                        .findFirst()
                                        .orElse(null);
                        if (match != null
                                && match.getName() != null
                                && !match.getName().isBlank()) {
                            textContent = match.getName().trim();
                        }
                    }
                    if (textContent.isBlank()) {
                        return null;
                    }
                    DispatchSpec dispatch =
                            new DispatchSpec(
                                    "channel",
                                    channelBox.getValue(),
                                    new DispatchTarget("", selectedSessionId),
                                    modeBox.getValue() != null
                                            ? modeBox.getValue()
                                            : DeliveryMode.FINAL,
                                    Collections.emptyMap());
                    JobRuntimeSpec runtime =
                            new JobRuntimeSpec(
                                    parseInt(concurrencyField.getText(), 1),
                                    parseInt(timeoutField.getText(), 120),
                                    // misfireGraceSeconds not yet implemented by the engine;
                                    // preserve any pre-existing value instead of editing it
                                    existing != null && existing.runtime() != null
                                            ? existing.runtime().misfireGraceSeconds()
                                            : 60,
                                    shareSessionCb.isSelected());
                    Boolean saveInbox = inboxCb.isSelected();
                    Integer countdownVal = -1;
                    if (countdownCb.isSelected()) {
                        try {
                            countdownVal = Integer.parseInt(countdownField.getText().trim());
                        } catch (NumberFormatException e) {
                            countdownVal = 1;
                        }
                    }
                    CronJobSpec spec =
                            new CronJobSpec(
                                    existing != null ? existing.id() : "",
                                    existing != null
                                                    && existing.projectId() != null
                                                    && !existing.projectId().isBlank()
                                                    && !"default".equals(existing.projectId())
                                            ? existing.projectId()
                                            : getCurrentProjectId(),
                                    name,
                                    enabledCb.isSelected(),
                                    schedule,
                                    selectedSessionId != null ? selectedSessionId : "",
                                    textContent,
                                    dispatch,
                                    saveInbox,
                                    runtime,
                                    Collections.emptyMap(),
                                    countdownVal);
                    return spec;
                });
        dialog.showAndWait()
                .ifPresent(
                        spec -> {
                            CronJobSpec resolved =
                                    cronService.resolveTask(spec, currentAgent.getId());
                            if (existing != null) {
                                cronService.update(resolved);
                            } else {
                                cronService.add(resolved);
                            }
                            renderJobs();
                        });
    }

    private int parseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ======================== Delete Confirmation ========================
    private void confirmDelete(CronJobSpec job) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Scheduled");
        alert.setHeaderText("Are you sure you want to delete this job?");
        alert.setContentText(
                "Job: " + job.name() + " (ID: " + job.id() + ")\nThis action cannot be undone.");
        alert.showAndWait()
                .ifPresent(
                        btn -> {
                            if (btn == ButtonType.OK) {
                                cronService.remove(job.id());
                                renderJobs();
                            }
                        });
    }

    // ======================== Execute Immediately Confirmation ========================
    public void confirmExecuteNow(CronJobSpec job) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Execute Job Now");
        alert.setHeaderText("Trigger this job immediately?");
        alert.setContentText(
                "Job: "
                        + job.name()
                        + "\nThe job will run right now outside of its normal schedule.");
        alert.getDialogPane().setId("dialog-execute-job-now");
        this.activeConfirmationAlert = alert;
        try {
            alert.showAndWait()
                    .ifPresent(
                            btn -> {
                                if (btn == ButtonType.OK) {
                                    cronService.executeNow(job.id());
                                    showToast("Job \"" + job.name() + "\" has been triggered.");
                                    renderJobs();
                                }
                            });
        } finally {
            this.activeConfirmationAlert = null;
        }
    }

    // ======================== Execution History Dialog ========================
    public void showHistoryDialog(CronJobSpec job) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Execution History");
        dialog.setHeaderText("History for: " + job.name());
        dialog.getDialogPane().setId("dialog-cron-history");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        this.historyDialog = dialog;
        List<CronExecutionRecord> records = cronService.getHistory(job.id());
        CronJobState state = cronService.getState(job.id());
        // ===== Status Summary =====
        VBox summaryBox = new VBox(6);
        summaryBox.setPadding(new Insets(8));
        summaryBox.getStyleClass().addAll("bg-fa", "radius-6");
        Label statusTitle = new Label("Runtime State");
        statusTitle.getStyleClass().add("fw-600");
        summaryBox
                .getChildren()
                .addAll(
                        statusTitle,
                        new Label(
                                "Last Run: "
                                        + (state.lastRunAt() != null ? state.lastRunAt() : "-")),
                        new Label(
                                "Last Status: "
                                        + (state.lastStatus() != null ? state.lastStatus() : "-")),
                        new Label(
                                "Next Run: "
                                        + (state.nextRunAt() != null ? state.nextRunAt() : "-")));
        if (state.lastError() != null) {
            Label errLbl = new Label("Error: " + state.lastError());
            errLbl.getStyleClass().add("text-red");
            summaryBox.getChildren().add(errLbl);
        }
        // ===== History Records Table =====
        TableView<CronExecutionRecord> table = new TableView<>();
        table.setId("table-cron-history");
        table.setPlaceholder(new Label("No execution history yet."));
        TableColumn<CronExecutionRecord, String> runAtCol = new TableColumn<>("Run At");
        runAtCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().formattedRunAt()));
        runAtCol.setPrefWidth(200);
        TableColumn<CronExecutionRecord, CronJobStatus> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().status()));
        statusCol.setPrefWidth(90);
        statusCol.setCellFactory(
                col ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(CronJobStatus item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setGraphic(null);
                                    setStyle("");
                                    setTooltip(null);
                                } else {
                                    setText(item.getCode());
                                    if (CronJobStatus.SUCCESS == item) {
                                        setStyle("-fx-text-fill: #15a763; -fx-font-weight: 600;");
                                        setTooltip(null);
                                    } else if (CronJobStatus.ERROR == item) {
                                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: 600;");
                                        CronExecutionRecord record =
                                                getTableRow() != null
                                                        ? getTableRow().getItem()
                                                        : null;
                                        if (record != null
                                                && record.error() != null
                                                && !record.error().isBlank()) {
                                            setTooltip(new Tooltip(record.error()));
                                        } else {
                                            setTooltip(null);
                                        }
                                    } else if (CronJobStatus.RUNNING == item) {
                                        setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: 600;");
                                        setTooltip(null);
                                    } else {
                                        setStyle("-fx-text-fill: #999;");
                                        setTooltip(null);
                                    }
                                }
                            }
                        });
        TableColumn<CronExecutionRecord, String> triggerCol = new TableColumn<>("Trigger");
        triggerCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().trigger().getCode()));
        triggerCol.setPrefWidth(90);

        TableColumn<CronExecutionRecord, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(120);
        actionsCol.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final Button detailsBtn = new Button("See details");

                            {
                                detailsBtn.setId("btn-see-details");
                                detailsBtn.getStyleClass().addAll("chip-btn", "btn-see-details");
                                detailsBtn.setStyle(
                                        "-fx-font-size: 11px; -fx-padding: 3 8; -fx-cursor: hand;");
                                detailsBtn.setOnAction(
                                        e -> {
                                            CronExecutionRecord record =
                                                    getTableRow() != null
                                                            ? getTableRow().getItem()
                                                            : null;
                                            if (record == null
                                                    && table.getItems() != null
                                                    && !table.getItems().isEmpty()) {
                                                record = table.getItems().get(0);
                                            }
                                            if (record != null) {
                                                openSessionDetails(job, record, dialog);
                                            }
                                        });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                } else {
                                    setGraphic(detailsBtn);
                                }
                            }
                        });

        table.getColumns().addAll(runAtCol, statusCol, triggerCol, actionsCol);
        ObservableList<CronExecutionRecord> items = FXCollections.observableArrayList(records);
        table.setItems(items);
        table.setPrefHeight(300);
        VBox content = new VBox(12, summaryBox, new Label("Execution Records:"), table);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setPrefHeight(460);
        try {
            dialog.showAndWait();
        } finally {
            this.historyDialog = null;
        }
    }

    public void openSessionDetails(
            CronJobSpec job, CronExecutionRecord record, Dialog<Void> dialog) {
        if (onOpenTask == null) {
            LOGGER.warning("Unable to open task details: onOpenTask handler is null");
            return;
        }
        String targetSessionId = record.sessionId();
        if (targetSessionId == null || targetSessionId.isBlank()) {
            targetSessionId = job.taskId();
        }
        if ((targetSessionId == null || targetSessionId.isBlank())
                && job.dispatch() != null
                && job.dispatch().target() != null) {
            targetSessionId = job.dispatch().target().sessionId();
        }
        ChatSessionInfo targetSession = null;
        if (targetSessionId != null && !targetSessionId.isBlank()) {
            targetSession = cronService.findSession(targetSessionId);
        }
        if (targetSession == null && job.taskId() != null && !job.taskId().isBlank()) {
            targetSession = cronService.findSession(job.taskId());
        }
        if (targetSession == null
                && job.dispatch() != null
                && job.dispatch().target() != null
                && job.dispatch().target().sessionId() != null
                && !job.dispatch().target().sessionId().isBlank()) {
            targetSession = cronService.findSession(job.dispatch().target().sessionId());
        }
        if (targetSession != null) {
            LOGGER.log(
                    Level.INFO,
                    "Navigating to task session from cron history: jobId={0}, sessionId={1}",
                    new Object[] {job.id(), targetSession.getId()});
            final ChatSessionInfo sessionToOpen = targetSession;
            dialog.close();
            Platform.runLater(() -> onOpenTask.accept(sessionToOpen));
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "Session not found for cron execution: jobId={0}, sessionId={1}",
                    new Object[] {job.id(), targetSessionId});
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Session Not Found");
            alert.setHeaderText(null);
            alert.setContentText("The execution session could not be found.");
            alert.showAndWait();
        }
    }

    // ======================== Template Selection ========================
    private void showTemplatePicker() {
        Dialog<CronJobSpec> dialog = new Dialog<>();
        dialog.setTitle("Create Job from Template");
        dialog.setHeaderText("Choose a template to pre-fill the job form");
        ButtonType useType = new ButtonType("Use Template", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(useType, ButtonType.CANCEL);
        List<Template> templates =
                List.of(
                        new Template(
                                "Daily Tech News Brief",
                                "Daily tech news summary",
                                "cron",
                                "0 8 * * *",
                                "Summary of today's tech news",
                                "console"),
                        new Template(
                                "Morning Greeting",
                                "Daily greeting",
                                "cron",
                                "0 9 * * *",
                                "Good morning! Have a great day!",
                                "console"),
                        new Template(
                                "Weekly Summary",
                                "Weekly work summary",
                                "cron",
                                "0 17 * * 5",
                                "Summary of the week's work",
                                "console"),
                        new Template(
                                "Pomodoro Break Reminder",
                                "Remind to rest every 25 minutes",
                                "cron",
                                "* * * * *",
                                "Time for a break! Stand up and stretch.",
                                "console"),
                        new Template(
                                "Pet Care Reminder",
                                "Pet feeding reminder",
                                "cron",
                                "0 8,18 * * *",
                                "Time to feed your pet!",
                                "console"),
                        new Template(
                                "Business Trip Prep",
                                "Pre-trip preparation check",
                                "once",
                                null,
                                "Checklist for business trip",
                                "console"));
        VBox templateList = new VBox(8);
        templateList.setPadding(new Insets(16));
        // Group by schedule type
        Label recurringLabel = new Label("Recurring (Cron)");
        recurringLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-padding: 8 0 4 0;");
        templateList.getChildren().add(recurringLabel);
        for (Template t : templates) {
            if (!"cron".equals(t.scheduleType)) continue;
            VBox card = new VBox(4);
            card.setStyle(
                    "-fx-background-color: #fafafa; -fx-border-color: #e5e5e5; "
                            + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");
            card.setPrefWidth(500);
            Label nameLbl = new Label(t.name());
            nameLbl.getStyleClass().add("fw-600");
            Label descLbl = new Label(t.description());
            descLbl.getStyleClass().add("muted");
            Label metaLbl = new Label("Cron: " + t.cron());
            metaLbl.getStyleClass().add("text-11-gray");
            Button useBtn = new Button("Use");
            useBtn.getStyleClass().add("chip-btn");
            useBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 10;");
            useBtn.setOnAction(
                    e -> {
                        dialog.setResult(createFromTemplate(t));
                        dialog.close();
                    });
            HBox cardRow = new HBox(12, new VBox(nameLbl, descLbl, metaLbl), useBtn);
            cardRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(cardRow);
            templateList.getChildren().add(card);
        }
        Label onceLabel = new Label("Once (Scheduled)");
        onceLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-padding: 8 0 4 0;");
        templateList.getChildren().add(onceLabel);
        for (Template t : templates) {
            if (!"once".equals(t.scheduleType)) continue;
            VBox card = new VBox(4);
            card.setStyle(
                    "-fx-background-color: #fafafa; -fx-border-color: #e5e5e5; "
                            + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");
            card.setPrefWidth(500);
            Label nameLbl = new Label(t.name());
            nameLbl.getStyleClass().add("fw-600");
            Label descLbl = new Label(t.description());
            descLbl.getStyleClass().add("muted");
            Label metaLbl = new Label();
            metaLbl.getStyleClass().add("text-11-gray");
            Button useBtn = new Button("Use");
            useBtn.getStyleClass().add("chip-btn");
            useBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 10;");
            useBtn.setOnAction(
                    e -> {
                        dialog.setResult(createFromTemplate(t));
                        dialog.close();
                    });
            HBox cardRow = new HBox(12, new VBox(nameLbl, descLbl, metaLbl), useBtn);
            cardRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(cardRow);
            templateList.getChildren().add(card);
        }
        ScrollPane scroll = new ScrollPane(templateList);
        scroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setPrefHeight(500);
        dialog.setResultConverter(btn -> btn == useType ? CronJobModel.DEFAULTS : null);
        dialog.showAndWait()
                .ifPresent(
                        spec -> {
                            if (spec != null) {
                                CronJobSpec specWithProject =
                                        (spec.projectId() == null
                                                                || spec.projectId().isBlank()
                                                                || "default"
                                                                        .equals(spec.projectId()))
                                                        && !"default".equals(getCurrentProjectId())
                                                ? spec.withProjectId(getCurrentProjectId())
                                                : spec;
                                CronJobSpec resolved =
                                        cronService.resolveTask(
                                                specWithProject, currentAgent.getId());
                                cronService.add(resolved);
                                renderJobs();
                            }
                        });
    }

    private CronJobSpec createFromTemplate(Template t) {
        ScheduleSpec schedule;
        if ("once".equals(t.scheduleType())) {
            schedule =
                    new ScheduleSpec(
                            "once",
                            null,
                            ZonedDateTime.now()
                                    .plusDays(1)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            CronJobModel.getDefaultTimezone(),
                            null,
                            null,
                            null,
                            null);
        } else {
            schedule =
                    new ScheduleSpec(
                            "cron",
                            t.cron(),
                            null,
                            CronJobModel.getDefaultTimezone(),
                            null,
                            null,
                            null,
                            null);
        }
        return new CronJobSpec(
                "",
                getCurrentProjectId(),
                t.name(),
                true,
                schedule,
                "",
                t.inputPrompt(),
                new DispatchSpec(
                        "channel",
                        t.channel(),
                        new DispatchTarget("", "default"),
                        DeliveryMode.FINAL,
                        Collections.emptyMap()),
                !("cron".equals(t.scheduleType())),
                JobRuntimeSpec.defaults(),
                Collections.emptyMap(),
                -1);
    }

    // ======================== Utility Methods ========================
    private void showToast(String message) {
        Label toast = new Label(message);
        toast.getStyleClass().add("toast");
        toast.setOpacity(0);
        StackPane.setAlignment(toast, javafx.geometry.Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(10, 0, 0, 0));
        root.getChildren().add(toast);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
        new Timeline(
                        new KeyFrame(
                                Duration.seconds(5),
                                e -> {
                                    FadeTransition fadeOut =
                                            new FadeTransition(Duration.millis(400), toast);
                                    fadeOut.setFromValue(1);
                                    fadeOut.setToValue(0);
                                    fadeOut.setOnFinished(ev -> root.getChildren().remove(toast));
                                    fadeOut.play();
                                }))
                .play();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        currentAgent = agent;
    }

    @Override
    public void onProjectChanged(ProjectInfo project) {
        this.currentProject = project;
        renderJobs();
    }

    @Override
    public void refresh() {
        renderJobs();
    }
}
