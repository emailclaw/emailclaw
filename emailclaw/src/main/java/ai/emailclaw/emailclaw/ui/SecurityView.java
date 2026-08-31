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
import ai.emailclaw.emailclaw.model.BlockedSkillRecord;
import ai.emailclaw.emailclaw.model.SecurityRule;
import ai.emailclaw.emailclaw.model.SecuritySettings;
import ai.emailclaw.emailclaw.model.SkillWhitelistEntry;
import ai.emailclaw.emailclaw.model.ToolGuardSettings;
import ai.emailclaw.emailclaw.service.SecurityService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Security settings page: Tool Guard, File Guard, Skill Scanner, Allow No Auth Hosts.
 *
 * <p>Layout and interaction aligned with Emailclaw 1.1.9; TabPane reuses {@code plugin-manager-tabs} style.
 */
public class SecurityView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(SecurityView.class.getName());

    private static final String[] SHELL_EVASION_KEYS = {
        "command_substitution",
        "obfuscated_flags",
        "backslash_escaped_whitespace",
        "backslash_escaped_operators",
        "newlines",
        "comment_quote_desync",
        "quoted_newline"
    };

    private final SecurityService securityService;

    private final BorderPane root = new BorderPane();

    private SecuritySettings workingCopy;

    private List<SecurityRule> workingRules;

    private final TabPane mainTabs = new TabPane();

    private final HBox footer = new HBox(8);

    private final Button resetBtn = new Button("Reset");

    private final Button saveBtn = new Button("Save");

    // Tool Guard controls
    private final TextField guardedToolsField = new TextField();

    private final TextField deniedToolsField = new TextField();

    private final Accordion rulesAccordion = new Accordion();

    private final Map<String, CheckBox> shellEvasionToggles = new LinkedHashMap<>();

    // File Guard controls
    private final CheckBox fileGuardEnabled = new CheckBox("Enable File Guard");

    private final TextField fileGuardPathField = new TextField();

    private final TableView<String> fileGuardTable = new TableView<>();

    // Skill Scanner controls
    private final ComboBox<String> scannerMode = new ComboBox<>();

    private final Spinner<Integer> scannerTimeout = new Spinner<>();

    private final TabPane skillScannerTabs = new TabPane();

    private TableView<SkillWhitelistEntry> whitelistTable;

    // Allow No Auth controls
    private final TextField noAuthHostField = new TextField();

    private final TableView<String> noAuthTable = new TableView<>();

    public SecurityView(SecurityService securityService) {
        this.securityService = securityService;
        buildUi();
        reloadFromService();
    }

    private void buildUi() {
        root.getStyleClass().add("bg-white");
        VBox page = new VBox(12);
        page.setPadding(new Insets(18));
        Label breadcrumb = new Label("Settings / Security");
        breadcrumb.getStyleClass().add("muted");
        mainTabs.getStyleClass().add("plugin-manager-tabs");
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        mainTabs.getTabs()
                .addAll(
                        new Tab("Tool Guard", buildToolGuardTab()),
                        new Tab("File Guard", buildFileGuardTab()),
                        new Tab("Skill Scanner", buildSkillScannerTab()),
                        new Tab("Allow No Auth Hosts", buildAllowNoAuthTab()));
        mainTabs.getSelectionModel()
                .selectedIndexProperty()
                .addListener((obs, o, idx) -> updateFooterVisibility());
        VBox.setVgrow(mainTabs, Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 18, 18, 18));
        resetBtn.getStyleClass().add("chip-btn");
        saveBtn.getStyleClass().add("primary-btn");
        resetBtn.setOnAction(e -> reloadFromService());
        saveBtn.setOnAction(e -> persist());
        footer.getChildren().addAll(resetBtn, saveBtn);
        page.getChildren().addAll(breadcrumb, mainTabs);
        root.setCenter(page);
        root.setBottom(footer);
        updateFooterVisibility();
    }

    private Node buildToolGuardTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(8, 0, 0, 0));
        Label desc =
                new Label(
                        "Configure security scanning for tool calls. Dangerous operations will"
                                + " require your explicit approval before execution.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        VBox configCard = new VBox(12);
        configCard.getStyleClass().add("card");
        guardedToolsField.setPromptText("Select tools or type custom tool names (comma-separated)");
        deniedToolsField.setPromptText("Select tools to always deny (comma-separated)");
        GridPane toolGrid = new GridPane();
        toolGrid.setHgap(12);
        toolGrid.setVgap(10);
        toolGrid.addRow(0, labelCell("Guarded Tools"), guardedToolsField);
        toolGrid.addRow(1, labelCell("Denied Tools"), deniedToolsField);
        GridPane.setHgrow(guardedToolsField, Priority.ALWAYS);
        GridPane.setHgrow(deniedToolsField, Priority.ALWAYS);
        configCard.getChildren().addAll(toolGrid);
        HBox rulesHeader = new HBox(8);
        rulesHeader.setAlignment(Pos.CENTER_LEFT);
        Label rulesTitle = new Label("Detection Rules");
        rulesTitle.getStyleClass().add("fw-700-16");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addRuleBtn = new Button("+ Add Rule");
        addRuleBtn.getStyleClass().add("primary-btn");
        // Add Rule button handler: open dialog to add custom rule
        addRuleBtn.setOnAction(e -> showAddRuleDialog());
        rulesHeader.getChildren().addAll(rulesTitle, spacer, addRuleBtn);
        ScrollPane rulesScroll = new ScrollPane(rulesAccordion);
        rulesScroll.setFitToWidth(true);
        rulesScroll.setPrefViewportHeight(320);
        VBox.setVgrow(rulesScroll, Priority.ALWAYS);
        Label shellTitle = new Label("Shell Evasion Detection");
        shellTitle.getStyleClass().add("fw-700-16");
        Label shellDesc =
                new Label(
                        "Detect attempts to bypass shell command inspection via encoding,"
                                + " substitution, or obfuscation techniques.");
        shellDesc.getStyleClass().add("muted");
        shellDesc.setWrapText(true);
        FlowPane shellGrid = new FlowPane(12, 12);
        shellGrid.getStyleClass().add("shell-evasion-grid");
        shellGrid.setPrefWrapLength(900);
        for (String key : SHELL_EVASION_KEYS) {
            shellGrid.getChildren().add(buildShellEvasionCard(key));
        }
        box.getChildren()
                .addAll(
                        desc,
                        configCard,
                        rulesHeader,
                        rulesScroll,
                        shellTitle,
                        shellDesc,
                        shellGrid);
        return new ScrollPane(box);
    }

    private Node buildFileGuardTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(8, 0, 0, 0));
        Label desc =
                new Label(
                        "Protect sensitive files and directories from being accessed by agent"
                            + " tools. Paths added here will be blocked across all tool calls.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        fileGuardEnabled.getStyleClass().add("fw-600");
        fileGuardPathField.setPromptText(
                "Enter file or directory path (e.g. ~/.ssh/ or /etc/passwd)");
        HBox.setHgrow(fileGuardPathField, Priority.ALWAYS);
        Button addPathBtn = new Button("+ Add");
        addPathBtn.getStyleClass().add("primary-btn");
        addPathBtn.setOnAction(e -> addFileGuardPath());
        fileGuardPathField.setOnAction(e -> addFileGuardPath());
        HBox inputRow = new HBox(8, fileGuardPathField, addPathBtn);
        card.getChildren().addAll(fileGuardEnabled, inputRow);
        configurePathTable(fileGuardTable, true);
        VBox.setVgrow(fileGuardTable, Priority.ALWAYS);
        box.getChildren().addAll(desc, card, fileGuardTable);
        return box;
    }

    private Node buildSkillScannerTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(8, 0, 0, 0));
        Label desc =
                new Label(
                        "Automatically scan skills for security threats before enabling or"
                                + " installing. Unsafe skills can be blocked or whitelisted.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        HBox settingsBar = new HBox(24);
        settingsBar.getStyleClass().add("card");
        settingsBar.setAlignment(Pos.CENTER_LEFT);
        settingsBar.setPadding(new Insets(12));
        scannerMode.getItems().addAll("Warn Only", "Block", "Off");
        scannerMode.setValue("Warn Only");
        scannerMode.setOnAction(e -> applyScannerModeFromUi());
        scannerTimeout.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 300, 30));
        scannerTimeout
                .valueProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (workingCopy != null && n != null) {
                                workingCopy.getSkillScanner().setTimeout(n);
                            }
                        });
        settingsBar
                .getChildren()
                .addAll(
                        labeledField("Scanner Mode", scannerMode),
                        labeledField("Scan Timeout (seconds)", scannerTimeout));
        skillScannerTabs.getStyleClass().add("plugin-manager-tabs");
        skillScannerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        skillScannerTabs
                .getTabs()
                .addAll(new Tab("Scan Alerts", new VBox()), new Tab("Whitelist", new VBox()));
        VBox.setVgrow(skillScannerTabs, Priority.ALWAYS);
        box.getChildren().addAll(desc, settingsBar, skillScannerTabs);
        return box;
    }

    private void refreshSkillScannerTabs() {
        List<BlockedSkillRecord> history =
                workingCopy == null ? List.of() : workingCopy.getSkillScanner().getBlockedHistory();
        VBox alertsPane = new VBox(8);
        alertsPane.setAlignment(Pos.CENTER);
        alertsPane.setPadding(new Insets(48));
        alertsPane.getStyleClass().add("card");
        if (history == null || history.isEmpty()) {
            Label icon = new Label("📦");
            icon.getStyleClass().add("text-42");
            Label text = new Label("No security alerts");
            text.getStyleClass().add("muted");
            alertsPane.getChildren().addAll(icon, text);
        } else {
            VBox list = new VBox(8);
            for (BlockedSkillRecord record : history) {
                list.getChildren()
                        .add(
                                new Label(
                                        record.skillName()
                                                + " · "
                                                + record.maxSeverity()
                                                + " · "
                                                + record.action()));
            }
            ScrollPane scroll = new ScrollPane(list);
            scroll.setFitToWidth(true);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            alertsPane = new VBox(scroll);
            VBox.setVgrow(alertsPane, Priority.ALWAYS);
        }
        skillScannerTabs.getTabs().get(0).setContent(alertsPane);
        whitelistTable = new TableView<>();
        TableColumn<SkillWhitelistEntry, String> nameCol = new TableColumn<>("Skill Name");
        nameCol.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getSkillName()));
        TableColumn<SkillWhitelistEntry, String> hashCol = new TableColumn<>("Content Hash");
        hashCol.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getContentHash()));
        whitelistTable.getColumns().addAll(nameCol, hashCol);
        whitelistTable
                .getItems()
                .setAll(
                        workingCopy == null
                                ? List.of()
                                : workingCopy.getSkillScanner().getWhitelist());
        whitelistTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox whitelistPane = new VBox(whitelistTable);
        VBox.setVgrow(whitelistTable, Priority.ALWAYS);
        skillScannerTabs.getTabs().get(1).setContent(whitelistPane);
    }

    private Node buildAllowNoAuthTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(8, 0, 0, 0));
        VBox warning = new VBox(6);
        warning.getStyleClass().add("security-warning");
        warning.setPadding(new Insets(12));
        Label warnTitle = new Label("⚠ Security Warning");
        warnTitle.getStyleClass().add("fw-700");
        Label warnBody =
                new Label(
                        "IP addresses in this list can access API endpoints without authentication."
                            + " By default, localhost (127.0.0.1 and ::1) are allowed for CLI"
                            + " access. Only add trusted IP addresses. WARNING: Adding untrusted"
                            + " IPs poses a serious security risk.");
        warnBody.setWrapText(true);
        warnBody.getStyleClass().add("muted");
        warning.getChildren().addAll(warnTitle, warnBody);
        HBox inputRow = new HBox(8);
        noAuthHostField.setPromptText("Enter IP address (e.g., 192.168.1.100 or ::1)");
        HBox.setHgrow(noAuthHostField, Priority.ALWAYS);
        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setOnAction(e -> addNoAuthHost());
        noAuthHostField.setOnAction(e -> addNoAuthHost());
        inputRow.getChildren().addAll(noAuthHostField, addBtn);
        configurePathTable(noAuthTable, false);
        VBox.setVgrow(noAuthTable, Priority.ALWAYS);
        box.getChildren().addAll(warning, inputRow, noAuthTable);
        return box;
    }

    private VBox buildShellEvasionCard(String key) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card-lite");
        card.setPrefWidth(200);
        String title = key.replace('_', ' ');
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        Label name = new Label(title);
        name.getStyleClass().add("fw-600");
        CheckBox toggle = new CheckBox();
        shellEvasionToggles.put(key, toggle);
        HBox top = new HBox(8, name, spacer(), toggle);
        top.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(top);
        return card;
    }

    private void configurePathTable(TableView<String> table, boolean fileGuard) {
        table.getColumns().clear();
        TableColumn<String, String> pathCol = new TableColumn<>(fileGuard ? "Path" : "IP Address");
        pathCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue()));
        pathCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setGraphic(null);
                                    return;
                                }
                                HBox row = new HBox(8);
                                row.setAlignment(Pos.CENTER_LEFT);
                                Label pathLabel = new Label(item);
                                if (fileGuard && (item.endsWith("/") || item.endsWith("\\"))) {
                                    Label tag = new Label("Directory");
                                    tag.getStyleClass().add("status-warn-badge");
                                    row.getChildren().addAll(pathLabel, tag);
                                } else if (!fileGuard
                                        && SecurityService.isDefaultNoAuthHost(item)) {
                                    Label tag = new Label("Default");
                                    tag.getStyleClass().add("status-custom");
                                    row.getChildren().addAll(new Label("🛡"), pathLabel, tag);
                                } else if (!fileGuard) {
                                    row.getChildren().addAll(new Label("🛡"), pathLabel);
                                } else {
                                    row.getChildren().add(pathLabel);
                                }
                                setGraphic(row);
                            }
                        });
        TableColumn<String, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(80);
        actionCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            private final Button deleteBtn = new Button("🗑");

                            {
                                deleteBtn.getStyleClass().add("chip-btn");
                                deleteBtn.setOnAction(
                                        e -> {
                                            String val = getTableView().getItems().get(getIndex());
                                            getTableView().getItems().remove(val);
                                        });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(empty || getIndex() < 0 ? null : deleteBtn);
                            }
                        });
        table.getColumns().addAll(pathCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("table-white-simple");
        table.setRowFactory(tv -> new TableCellStyleRow<>());
    }

    private void reloadFromService() {
        workingCopy = copySettings(securityService.getSettings());
        workingRules = new ArrayList<>(securityService.listRules());
        bindUiFromModel();
        LOGGER.fine("Loaded security settings to edit buffer");
    }

    private void bindUiFromModel() {
        ToolGuardSettings tg = workingCopy.getToolGuard();
        guardedToolsField.setText(SecurityService.joinToolList(tg.getGuardedTools()));
        deniedToolsField.setText(SecurityService.joinToolList(tg.getDeniedTools()));
        fileGuardEnabled.setSelected(workingCopy.getFileGuard().isEnabled());
        fileGuardTable.getItems().setAll(workingCopy.getFileGuard().getPaths());
        scannerMode.setValue(modeToLabel(workingCopy.getSkillScanner().getMode()));
        scannerTimeout.getValueFactory().setValue(workingCopy.getSkillScanner().getTimeout());
        noAuthTable.getItems().setAll(workingCopy.getAllowNoAuthHosts());
        for (String key : SHELL_EVASION_KEYS) {
            CheckBox cb = shellEvasionToggles.get(key);
            if (cb != null) {
                cb.setSelected(Boolean.TRUE.equals(tg.getShellEvasionChecks().get(key)));
            }
        }
        renderRulesAccordion();
        refreshSkillScannerTabs();
    }

    private void renderRulesAccordion() {
        rulesAccordion.getPanes().clear();
        Map<String, List<SecurityRule>> grouped =
                workingRules.stream()
                        .collect(
                                Collectors.groupingBy(
                                        r -> r.category() == null ? "other" : r.category(),
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        List<String> order =
                List.of(
                        "command_injection",
                        "resource_abuse",
                        "code_execution",
                        "network_abuse",
                        "sensitive_file_access",
                        "privilege_escalation");
        for (String category : order) {
            List<SecurityRule> rules = grouped.get(category);
            if (rules == null || rules.isEmpty()) {
                continue;
            }
            long enabledCount = rules.stream().filter(r -> r.enabled()).count();
            String title =
                    SecurityService.categoryLabel(category)
                            + "  "
                            + enabledCount
                            + "/"
                            + rules.size();
            TitledPane pane = new TitledPane(title, buildRulesTable(rules));
            pane.setExpanded("command_injection".equals(category));
            rulesAccordion.getPanes().add(pane);
        }
        if (!rulesAccordion.getPanes().isEmpty()) {
            rulesAccordion.setExpandedPane(rulesAccordion.getPanes().getFirst());
        }
    }

    private TableView<SecurityRule> buildRulesTable(List<SecurityRule> rules) {
        TableView<SecurityRule> table = new TableView<>();
        table.getItems().setAll(rules);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("table-white-simple");
        table.setRowFactory(tv -> new TableCellStyleRow<>());
        TableColumn<SecurityRule, String> idCol = new TableColumn<>("Rule ID");
        idCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().ruleId()));
        idCol.setPrefWidth(200);
        TableColumn<SecurityRule, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().severity()));
        sevCol.setCellFactory(col -> severityCell());
        TableColumn<SecurityRule, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().description()));
        TableColumn<SecurityRule, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(
                d -> new ReadOnlyStringWrapper(d.getValue().builtIn() ? "Built-in" : "Custom"));
        TableColumn<SecurityRule, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getIndex() < 0) {
                                    setGraphic(null);
                                    return;
                                }
                                SecurityRule rule = getTableView().getItems().get(getIndex());
                                CheckBox enableToggle = new CheckBox();
                                enableToggle.setSelected(rule.enabled());
                                enableToggle.setOnAction(
                                        e -> {
                                            SecurityRule updated =
                                                    rule.withEnabled(enableToggle.isSelected());
                                            int idx = workingRules.indexOf(rule);
                                            if (idx >= 0) {
                                                workingRules.set(idx, updated);
                                            }
                                            int tableIdx = getTableView().getItems().indexOf(rule);
                                            if (tableIdx >= 0) {
                                                getTableView().getItems().set(tableIdx, updated);
                                            }
                                            renderRulesAccordion();
                                        });
                                Button preview = new Button("👁");
                                preview.getStyleClass().add("chip-btn");
                                preview.setOnAction(
                                        e -> showInfo(rule.ruleId(), rule.description()));
                                HBox box = new HBox(8, enableToggle, preview);
                                setGraphic(box);
                            }
                        });
        table.getColumns().addAll(idCol, sevCol, descCol, sourceCol, actionCol);
        return table;
    }

    private TableCell<SecurityRule, String> severityCell() {
        return new TableCell<>() {

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label tag = new Label(item);
                if ("CRITICAL".equals(item)) {
                    tag.getStyleClass().add("text-red-700");
                } else if ("HIGH".equals(item)) {
                    tag.getStyleClass().add("text-orange-fw");
                } else {
                    tag.getStyleClass().add("muted");
                }
                setGraphic(tag);
            }
        };
    }

    private void persist() {
        try {
            applyUiToModel();
            securityService.saveSettings(workingCopy);
            securityService.saveRules(workingRules);
            showInfo("Saved", "Security settings have been saved.");
            reloadFromService();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to save security settings", ex);
            showError("Save failed", ex.getMessage());
        }
    }

    private void applyUiToModel() {
        ToolGuardSettings tg = workingCopy.getToolGuard();
        tg.setGuardedTools(SecurityService.parseToolList(guardedToolsField.getText()));
        tg.setDeniedTools(SecurityService.parseToolList(deniedToolsField.getText()));
        for (String key : SHELL_EVASION_KEYS) {
            CheckBox cb = shellEvasionToggles.get(key);
            if (cb != null) {
                tg.getShellEvasionChecks().put(key, cb.isSelected());
            }
        }
        workingCopy.getFileGuard().setEnabled(fileGuardEnabled.isSelected());
        workingCopy.getFileGuard().setPaths(new ArrayList<>(fileGuardTable.getItems()));
        workingCopy.setAllowNoAuthHosts(new ArrayList<>(noAuthTable.getItems()));
        workingCopy.getSkillScanner().setMode(labelToMode(scannerMode.getValue()));
        workingCopy.getSkillScanner().setTimeout(scannerTimeout.getValue());
    }

    private void addFileGuardPath() {
        String path =
                fileGuardPathField.getText() == null ? "" : fileGuardPathField.getText().trim();
        if (path.isBlank()) {
            return;
        }
        if (!fileGuardTable.getItems().contains(path)) {
            fileGuardTable.getItems().add(path);
        }
        fileGuardPathField.clear();
    }

    private void addNoAuthHost() {
        String host = noAuthHostField.getText() == null ? "" : noAuthHostField.getText().trim();
        if (!SecurityService.isValidIp(host)) {
            showError("Invalid IP", "Please enter a valid IPv4 or IPv6 address.");
            return;
        }
        if (!noAuthTable.getItems().contains(host)) {
            noAuthTable.getItems().add(host);
        }
        noAuthHostField.clear();
    }

    private void applyScannerModeFromUi() {
        if (workingCopy != null) {
            workingCopy.getSkillScanner().setMode(labelToMode(scannerMode.getValue()));
        }
    }

    private void updateFooterVisibility() {
        int idx = mainTabs.getSelectionModel().getSelectedIndex();
        boolean show = idx == 0 || idx == 1 || idx == 3;
        footer.setVisible(show);
        footer.setManaged(show);
    }

    private static SecuritySettings copySettings(SecuritySettings src) {
        SecuritySettings copy = new SecuritySettings();
        copy.getToolGuard().setEnabled(src.getToolGuard().isEnabled());
        copy.getToolGuard().setGuardedTools(new ArrayList<>(src.getToolGuard().getGuardedTools()));
        copy.getToolGuard().setDeniedTools(new ArrayList<>(src.getToolGuard().getDeniedTools()));
        copy.getToolGuard()
                .setShellEvasionChecks(
                        new LinkedHashMap<>(src.getToolGuard().getShellEvasionChecks()));
        copy.getFileGuard().setEnabled(src.getFileGuard().isEnabled());
        copy.getFileGuard().setPaths(new ArrayList<>(src.getFileGuard().getPaths()));
        copy.getSkillScanner().setMode(src.getSkillScanner().getMode());
        copy.getSkillScanner().setTimeout(src.getSkillScanner().getTimeout());
        copy.getSkillScanner().setWhitelist(new ArrayList<>(src.getSkillScanner().getWhitelist()));
        copy.getSkillScanner()
                .setBlockedHistory(new ArrayList<>(src.getSkillScanner().getBlockedHistory()));
        copy.setAllowNoAuthHosts(new ArrayList<>(src.getAllowNoAuthHosts()));
        return copy;
    }

    private static String modeToLabel(String mode) {
        return switch (mode == null ? "warn" : mode) {
            case "block" -> "Block";
            case "off" -> "Off";
            default -> "Warn Only";
        };
    }

    private static String labelToMode(String label) {
        if ("Block".equals(label)) {
            return "block";
        }
        if ("Off".equals(label)) {
            return "off";
        }
        return "warn";
    }

    private static Label labelCell(String text) {
        Label l = new Label(text);
        l.setMinWidth(120);
        l.getStyleClass().add("fw-600");
        return l;
    }

    private static HBox labeledField(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("fw-600");
        return new HBox(8, l, control);
    }

    private static Node spacer() {
        HBox s = new HBox();
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Security");
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Open the add rule dialog.
     *
     * <p>Allows users to create new custom security rules. Once the rule is successfully added, it is saved to the working copy,
     * and displayed in real time in the rules table.
     */
    private void showAddRuleDialog() {
        if (root.getScene() == null || root.getScene().getWindow() == null) {
            showError("Error", "Cannot open dialog: window not available.");
            return;
        }
        // Create dialog instance
        AddRuleDialog dialog = new AddRuleDialog(root.getScene().getWindow());
        // Show dialog and wait for user input
        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            SecurityRule newRule = result.get();
            // Check if rule ID already exists
            boolean ruleExists =
                    workingRules.stream()
                            .anyMatch(r -> r.ruleId().equalsIgnoreCase(newRule.ruleId()));
            if (ruleExists) {
                showError(
                        "Duplicate Rule",
                        "A rule with ID '" + newRule.ruleId() + "' already exists.");
                return;
            }
            // Add new rule to working copy
            workingRules.add(newRule);
            // Re-render rules accordion to show the latest rules
            renderRulesAccordion();
            showInfo(
                    "Success",
                    "Custom rule '" + newRule.ruleId() + "' has been added successfully.");
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Security");
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {}

    @Override
    public void refresh() {
        reloadFromService();
    }

    /**
     * White background style for table rows to avoid unreadable text when selected.
     */
    private static final class TableCellStyleRow<T> extends TableRow<T> {

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("bg-white", "text-secondary");
            getStyleClass().addAll("bg-white", "text-secondary");
        }

        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            getStyleClass().removeAll("bg-white", "text-secondary");
            getStyleClass().addAll("bg-white", "text-secondary");
        }
    }
}
