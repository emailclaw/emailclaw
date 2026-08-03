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
import ai.emailclaw.emailclaw.service.CronJobService;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
        this.cronService = cronService;
        this.currentAgent = agent;
        initUi();
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
        LOGGER.fine("Refreshing Cron task list, filter condition: " + scheduleTypeFilter);
        jobList.getChildren().clear();
        List<CronJobSpec> allJobs = cronService.list();
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
        runBtn.getStyleClass().add("chip-btn");
        runBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        runBtn.setOnAction(
                e -> {
                    confirmExecuteNow(job);
                    if (onRefresh != null) onRefresh.run();
                });
        Button historyBtn = new Button("History");
        historyBtn.getStyleClass().add("chip-btn");
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
                        : "America/New_York");
        tzBox.setEditable(true);
        TextArea textArea =
                new TextArea(
                        existing != null && existing.inputPrompt() != null
                                ? existing.inputPrompt()
                                : "");
        textArea.setPrefRowCount(3);
        textArea.setPromptText("Message text / Input Prompt");
        ComboBox<String> channelBox = new ComboBox<>();
        channelBox
                .getItems()
                .addAll(ChannelIds.CONSOLE, ChannelIds.EMAILCLAW); // ChannelIds.DINGTALK,
        String initialChannel =
                existing != null && existing.dispatch() != null
                        ? existing.dispatch().channel()
                        : ChannelIds.CONSOLE;
        channelBox.setValue(initialChannel);
        ComboBox<String> sessionBox = new ComboBox<>();
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
        ComboBox<String> modeBox = new ComboBox<>();
        modeBox.getItems().addAll("stream", "final");
        modeBox.setValue(
                existing != null && existing.dispatch() != null
                        ? existing.dispatch().mode()
                        : "final");
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
        TextField misfireField =
                new TextField(
                        existing != null && existing.runtime() != null
                                ? String.valueOf(existing.runtime().misfireGraceSeconds())
                                : "60");
        CheckBox shareSessionCb = new CheckBox("Share session context");
        shareSessionCb.setSelected(
                existing == null
                        || existing.runtime() == null
                        || existing.runtime().shareSession());
        // ===== Form Layout =====
        TabPane tabPane = new TabPane();
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
        rtGrid.addRow(r4++, new Label("Misfire Grace (seconds)"), misfireField);
        rtGrid.addRow(r4++, new Label("Session Strategy"), shareSessionCb);
        Tab rtTab = new Tab("Runtime", new ScrollPane(rtGrid));
        rtTab.setClosable(false);
        tabPane.getTabs().addAll(basicTab, taskTab, dispatchTab, rtTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().setPrefWidth(620);
        dialog.getDialogPane().setPrefHeight(650);
        // ===== Result Conversion =====
        dialog.setResultConverter(
                btn -> {
                    if (btn != saveType) return null;
                    String name = nameField.getText().trim();
                    if (name.isBlank()) {
                        showAlert("Validation Error", "Job name is required.");
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
                    String selectedSessionId = "";
                    if (sessionVal != null && !sessionVal.isBlank()) {
                        selectedSessionId = sessionVal;
                        int parenIdx = sessionVal.indexOf(" (");
                        if (parenIdx > 0) {
                            selectedSessionId = sessionVal.substring(0, parenIdx);
                        }
                    }
                    LOGGER.info(
                            "Saving scheduled task, target session: "
                                    + (selectedSessionId.isBlank()
                                            ? "(Not selected, will auto-create)"
                                            : selectedSessionId));
                    String textContent = textArea.getText();
                    DispatchSpec dispatch =
                            new DispatchSpec(
                                    "channel",
                                    channelBox.getValue(),
                                    new DispatchTarget("", selectedSessionId),
                                    modeBox.getValue(),
                                    Collections.emptyMap());
                    JobRuntimeSpec runtime =
                            new JobRuntimeSpec(
                                    parseInt(concurrencyField.getText(), 1),
                                    parseInt(timeoutField.getText(), 120),
                                    parseInt(misfireField.getText(), 60),
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
                                    existing != null ? existing.projectId() : "default",
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
    private void confirmExecuteNow(CronJobSpec job) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Execute Job Now");
        alert.setHeaderText("Trigger this job immediately?");
        alert.setContentText(
                "Job: "
                        + job.name()
                        + "\nThe job will run right now outside of its normal schedule.");
        alert.showAndWait()
                .ifPresent(
                        btn -> {
                            if (btn == ButtonType.OK) {
                                cronService.executeNow(job.id());
                                showToast("Job \"" + job.name() + "\" has been triggered.");
                                renderJobs();
                            }
                        });
    }

    // ======================== Execution History Dialog ========================
    private void showHistoryDialog(CronJobSpec job) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Execution History");
        dialog.setHeaderText("History for: " + job.name());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
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
        table.setPlaceholder(new Label("No execution history yet."));
        TableColumn<CronExecutionRecord, String> runAtCol = new TableColumn<>("Run At");
        runAtCol.setCellValueFactory(
                cd ->
                        new SimpleStringProperty(
                                cd.getValue().runAt() != null ? cd.getValue().runAt() : "-"));
        runAtCol.setPrefWidth(220);
        TableColumn<CronExecutionRecord, CronJobStatus> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().status()));
        statusCol.setPrefWidth(100);
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
                                } else {
                                    setText(item.getCode());
                                    if (CronJobStatus.SUCCESS == item) {
                                        setStyle("-fx-text-fill: #15a763; -fx-font-weight: 600;");
                                    } else if (CronJobStatus.ERROR == item) {
                                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: 600;");
                                    } else if (CronJobStatus.RUNNING == item) {
                                        setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: 600;");
                                    } else {
                                        setStyle("-fx-text-fill: #999;");
                                    }
                                }
                            }
                        });
        TableColumn<CronExecutionRecord, String> triggerCol = new TableColumn<>("Trigger");
        triggerCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().trigger().getCode()));
        triggerCol.setPrefWidth(100);
        TableColumn<CronExecutionRecord, String> errorCol = new TableColumn<>("Error");
        errorCol.setCellValueFactory(
                cd ->
                        new SimpleStringProperty(
                                cd.getValue().error() != null ? cd.getValue().error() : "-"));
        errorCol.setPrefWidth(300);
        table.getColumns().addAll(runAtCol, statusCol, triggerCol, errorCol);
        ObservableList<CronExecutionRecord> items = FXCollections.observableArrayList(records);
        table.setItems(items);
        table.setPrefHeight(300);
        VBox content = new VBox(12, summaryBox, new Label("Execution Records:"), table);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(700);
        dialog.getDialogPane().setPrefHeight(500);
        dialog.showAndWait();
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
                                CronJobSpec resolved =
                                        cronService.resolveTask(spec, currentAgent.getId());
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
                            "America/New_York",
                            null,
                            null,
                            null,
                            null);
        } else {
            schedule =
                    new ScheduleSpec(
                            "cron", t.cron(), null, "America/New_York", null, null, null, null);
        }
        return new CronJobSpec(
                "",
                "default",
                t.name(),
                true,
                schedule,
                "",
                t.inputPrompt(),
                new DispatchSpec(
                        "channel",
                        t.channel(),
                        new DispatchTarget("", "default"),
                        "final",
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
    public void refresh() {
        renderJobs();
    }
}
