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
import ai.emailclaw.emailclaw.plugin.PluginManager;
import ai.emailclaw.emailclaw.plugin.PluginManifest;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Plugin management page.
 *
 * <p>The goal is to align with the core interaction of Emailclaw 1.1.9:
 * <br>1) Installed plugins list and uninstall;
 * <br>2) Official plugins list, filtering, and install/reinstall;
 * <br>3) Support ZIP file installation and URL installation.
 *
 * <p>This view only depends on {@link PluginManager} to complete all operations.
 */
public class PluginManagerView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(PluginManagerView.class.getName());

    private static final String TYPE_BUNDLE = "bundle";

    private static final String TYPE_TOOL = "tool";

    private static final String TYPE_CHANNEL = "channel";

    /**
     * Unified plugin manager reference.
     */
    private final PluginManager pluginManager;

    private final VBox root = new VBox(12);

    private final TableView<PluginManifest> installedTable = new TableView<>();

    private final TableView<PluginManifest> officialTable = new TableView<>();

    private final TextField officialNameFilter = new TextField();

    private final TextField officialKindFilter = new TextField();

    /**
     * Cached installed plugins list.
     */
    private List<PluginManifest> installedPlugins = List.of();

    /**
     * Cached official plugins list.
     */
    private List<PluginManifest> officialPlugins = List.of();

    /**
     * Construct plugin management view.
     *
     * @param pluginManager Plugin manager (provides all functions like install/uninstall/query)
     */
    public PluginManagerView(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        initUi();
        refresh();
    }

    /**
     * Initialize UI layout.
     */
    private void initUi() {
        root.getStyleClass().add("page");
        // Consistent with Emailclaw screenshots: the plugin management page uses a pure white
        // background to improve table readability.
        root.getStyleClass().add("bg-white");
        root.setPadding(new Insets(18));
        // Label breadcrumb = new Label("Settings / Plug-in Manager");
        // breadcrumb.getStyleClass().add("muted");
        Label title = new Label("Settings / Plug-in Manager");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        Button installZipBtn = new Button("Install Plugin");
        installZipBtn.getStyleClass().add("primary-btn");
        installZipBtn.setOnAction(event -> installFromZipDialog());
        Button installUrlBtn = new Button("Install from URL");
        installUrlBtn.getStyleClass().add("chip-btn");
        installUrlBtn.setOnAction(event -> installFromUrlDialog());
        toolbar.getChildren().addAll(installUrlBtn, installZipBtn);
        HBox headerRow = new HBox(10, header, spacer(), toolbar);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("plugin-manager-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab installedTab = new Tab("Installed Plugins", buildInstalledTab());
        Tab officialTab = new Tab("Plugin Market", buildOfficialTab());
        tabs.getTabs().addAll(installedTab, officialTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().addAll(headerRow, tabs);
    }

    /**
     * Build "Installed Plugins" tab content.
     */
    private Node buildInstalledTab() {
        configureInstalledTable();
        VBox.setVgrow(installedTable, Priority.ALWAYS);
        return new VBox(10, installedTable);
    }

    /**
     * Build "Official Plugins" tab content.
     */
    private Node buildOfficialTab() {
        HBox filters = new HBox(8);
        officialNameFilter.setPromptText("Filter by name");
        officialKindFilter.setPromptText("Filter by kind (tool/bundle/channel)");
        officialNameFilter
                .textProperty()
                .addListener((obs, oldV, newV) -> refreshOfficialTableRows());
        officialKindFilter
                .textProperty()
                .addListener((obs, oldV, newV) -> refreshOfficialTableRows());
        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("chip-btn");
        refreshBtn.setOnAction(event -> loadOfficialPlugins());
        filters.getChildren().addAll(officialNameFilter, officialKindFilter, refreshBtn);
        configureOfficialTable();
        VBox wrapper = new VBox(10, filters, officialTable);
        VBox.setVgrow(officialTable, Priority.ALWAYS);
        return wrapper;
    }

    /**
     * Configure columns and styles of the installed plugins table.
     */
    private void configureInstalledTable() {
        installedTable.getColumns().clear();
        installedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // Fix the table to a white background to avoid readability degradation caused by themes or
        // selected state.
        installedTable.getStyleClass().add("table-white");
        installedTable.setRowFactory(
                table -> {
                    TableCellStyleRow<PluginManifest> row = new TableCellStyleRow<>();
                    row.getStyleClass().add("bg-white");
                    return row;
                });
        TableColumn<PluginManifest, String> nameCol = new TableColumn<>("Plugin details");
        nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().name));
        nameCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                PluginManifest plugin = (PluginManifest) getTableRow().getItem();
                                Label name = new Label(plugin.name);
                                name.getStyleClass().add("fw-600");
                                name.setWrapText(true);
                                HBox tags = new HBox(6);
                                Label typeTag = new Label(formatType(plugin.getTypeValue()));
                                typeTag.getStyleClass().add("status-custom");
                                tags.getChildren().add(typeTag);
                                if (plugin.upgradeAvailable) {
                                    Label upgradeTag = new Label("Upgrade");
                                    upgradeTag.getStyleClass().add("status-warn-badge");
                                    tags.getChildren().add(upgradeTag);
                                }
                                Label desc =
                                        new Label(
                                                plugin.description == null
                                                        ? ""
                                                        : plugin.description);
                                desc.getStyleClass().add("muted");
                                desc.setWrapText(true);
                                Label meta =
                                        new Label(
                                                "v"
                                                        + nullToEmpty(plugin.version)
                                                        + (plugin.size == null
                                                                        || plugin.size.isBlank()
                                                                ? ""
                                                                : " · " + plugin.size)
                                                        + (plugin.author == null
                                                                        || plugin.author.isBlank()
                                                                ? ""
                                                                : " · " + plugin.author));
                                meta.getStyleClass().add("muted");
                                VBox box = new VBox(4, name, tags, desc, meta);
                                setGraphic(box);
                                setText(null);
                            }
                        });
        TableColumn<PluginManifest, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(nullToEmpty(data.getValue().getTypeValue())));
        typeCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setGraphic(null);
                                    return;
                                }
                                Label type = new Label(formatType(item));
                                type.getStyleClass().add("status-custom");
                                setGraphic(type);
                                setText(null);
                            }
                        });
        // Version column
        TableColumn<PluginManifest, String> versionCol = new TableColumn<>("Version");
        versionCol.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(nullToEmpty(data.getValue().version)));
        // Author column
        TableColumn<PluginManifest, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(nullToEmpty(data.getValue().author)));
        // Status column
        TableColumn<PluginManifest, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(buildStatus(data.getValue())));
        statusCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null || item.isBlank()) {
                                    setText(null);
                                    setGraphic(null);
                                    return;
                                }
                                Label status = new Label(item);
                                if ("Running".equals(item)) {
                                    status.getStyleClass().add("status-builtin");
                                } else {
                                    status.getStyleClass().add("muted-badge");
                                }
                                setGraphic(status);
                                setText(null);
                            }
                        });
        // Action column (uninstall button)
        TableColumn<PluginManifest, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            private final Button uninstallBtn = new Button("Uninstall");

                            {
                                uninstallBtn.getStyleClass().add("chip-btn");
                                uninstallBtn.setOnAction(
                                        event -> {
                                            PluginManifest plugin =
                                                    getTableView().getItems().get(getIndex());
                                            uninstallPlugin(plugin);
                                        });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getIndex() < 0
                                        || getIndex() >= getTableView().getItems().size()) {
                                    setGraphic(null);
                                    return;
                                }
                                setGraphic(uninstallBtn);
                            }
                        });
        installedTable
                .getColumns()
                .addAll(nameCol, typeCol, versionCol, authorCol, statusCol, actionCol);
    }

    /**
     * Configure columns and styles of the official plugins table.
     */
    private void configureOfficialTable() {
        officialTable.getColumns().clear();
        officialTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // The official plugins list also fixes the white background and disables the selected
        // highlight background color to ensure text is always clear.
        officialTable.getStyleClass().add("table-white");
        officialTable.setRowFactory(
                table -> {
                    TableCellStyleRow<PluginManifest> row = new TableCellStyleRow<>();
                    row.getStyleClass().add("bg-white");
                    return row;
                });
        // Name + Tag + Description + Meta info column
        TableColumn<PluginManifest, String> nameCol = new TableColumn<>("Official Plugins");
        nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().name));
        nameCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                PluginManifest plugin = (PluginManifest) getTableRow().getItem();
                                Label name = new Label(plugin.name);
                                name.getStyleClass().add("fw-600");
                                HBox tags = new HBox(6);
                                Label typeTag = new Label(formatType(plugin.getTypeValue()));
                                typeTag.getStyleClass().add("status-custom");
                                tags.getChildren().add(typeTag);
                                if (plugin.installed) {
                                    Label installedTag =
                                            new Label(
                                                    plugin.upgradeAvailable
                                                            ? "Upgrade"
                                                            : "Installed");
                                    installedTag
                                            .getStyleClass()
                                            .add(
                                                    plugin.upgradeAvailable
                                                            ? "status-warn-badge"
                                                            : "status-builtin");
                                    tags.getChildren().add(installedTag);
                                }
                                Label desc =
                                        new Label(
                                                plugin.description == null
                                                        ? ""
                                                        : plugin.description);
                                desc.getStyleClass().add("muted");
                                desc.setWrapText(true);
                                Label meta =
                                        new Label(
                                                "v"
                                                        + nullToEmpty(plugin.version)
                                                        + (plugin.size == null
                                                                        || plugin.size.isBlank()
                                                                ? ""
                                                                : " · " + plugin.size)
                                                        + (plugin.author == null
                                                                        || plugin.author.isBlank()
                                                                ? ""
                                                                : " · " + plugin.author));
                                meta.getStyleClass().add("muted");
                                VBox box = new VBox(4, name, tags, desc, meta);
                                setGraphic(box);
                            }
                        });
        // Action column (install/reinstall button)
        TableColumn<PluginManifest, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            private final Button installBtn = new Button();

                            {
                                installBtn.getStyleClass().add("primary-btn");
                                installBtn.setOnAction(
                                        event -> {
                                            PluginManifest plugin =
                                                    getTableView().getItems().get(getIndex());
                                            installOfficialPlugin(plugin);
                                        });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getIndex() < 0
                                        || getIndex() >= getTableView().getItems().size()) {
                                    setGraphic(null);
                                    return;
                                }
                                PluginManifest plugin = getTableView().getItems().get(getIndex());
                                installBtn.setText(resolveInstallButtonText(plugin));
                                setGraphic(installBtn);
                            }
                        });
        officialTable.getColumns().addAll(nameCol, actionCol);
    }

    /**
     * Build human-readable status text based on plugin status field.
     */
    private String buildStatus(PluginManifest plugin) {
        if (plugin.loaded) {
            return "Running";
        }
        if (plugin.enabled) {
            return "Enabled";
        }
        return "Disabled";
    }

    /**
     * Determine the install button text based on the plugin installation state.
     */
    private String resolveInstallButtonText(PluginManifest plugin) {
        if (plugin.upgradeAvailable) {
            return "Upgrade";
        }
        if (plugin.installed) {
            return "Reinstall";
        }
        return "Install";
    }

    /**
     * Load installed plugins list into the table.
     */
    private void loadInstalledPlugins() {
        try {
            installedPlugins =
                    pluginManager.getInstalledPlugins().stream()
                            .sorted(Comparator.comparing(p -> p.name == null ? "" : p.name))
                            .toList();
            installedTable.getItems().setAll(installedPlugins);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to load installed plugins", ex);
            showError("Failed to load installed plugins", ex.getMessage());
        }
    }

    /**
     * Load official plugins list and refresh table.
     */
    private void loadOfficialPlugins() {
        try {
            officialPlugins =
                    pluginManager.getOfficialPlugins().stream()
                            .sorted(Comparator.comparing(p -> p.name == null ? "" : p.name))
                            .toList();
            refreshOfficialTableRows();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to load official plugins list", ex);
            showError("Failed to load official plugins list", ex.getMessage());
        }
    }

    /**
     * Refresh official plugins table rows based on filter conditions.
     */
    private void refreshOfficialTableRows() {
        String nameKeyword =
                officialNameFilter.getText() == null
                        ? ""
                        : officialNameFilter.getText().trim().toLowerCase(Locale.ROOT);
        String kindKeyword =
                officialKindFilter.getText() == null
                        ? ""
                        : officialKindFilter.getText().trim().toLowerCase(Locale.ROOT);
        List<PluginManifest> filtered = new ArrayList<>();
        for (PluginManifest plugin : officialPlugins) {
            String pluginName = plugin.name == null ? "" : plugin.name.toLowerCase(Locale.ROOT);
            String pluginKind = plugin.getTypeValue().toLowerCase(Locale.ROOT);
            boolean matchesName = nameKeyword.isBlank() || pluginName.contains(nameKeyword);
            boolean matchesKind = kindKeyword.isBlank() || pluginKind.contains(kindKeyword);
            if (matchesName && matchesKind) {
                filtered.add(plugin);
            }
        }
        officialTable.getItems().setAll(filtered);
    }

    /**
     * Open ZIP file selection dialog and install.
     */
    private void installFromZipDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Plugin ZIP File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP files", "*.zip"));
        File zipFile = chooser.showOpenDialog(resolveWindow());
        if (zipFile == null) {
            return;
        }
        try {
            pluginManager.installPluginFromZip(zipFile, true);
            showInfo(
                    "Plugin Installed Successfully",
                    "Plugin installed from ZIP:" + zipFile.getName());
            refresh();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to install plugin from ZIP", ex);
            showError("Plugin Installation Failed", ex.getMessage());
        }
    }

    /**
     * Open URL input dialog and install.
     */
    private void installFromUrlDialog() {
        TextField urlField = new TextField();
        urlField.setPromptText("https://example.com/plugin.zip");
        VBox box = new VBox(8, new Label("Please enter plugin ZIP URL"), urlField);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Install from URL");
        dialog.setHeaderText("Install Remote Plugin");
        dialog.getDialogPane().setContent(box);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
            return;
        }
        String url = urlField.getText() == null ? "" : urlField.getText().trim();
        if (url.isBlank()) {
            showError("Installation Failed", "URL cannot be empty");
            return;
        }
        try {
            pluginManager.installPluginFromUrl(url, true);
            showInfo("Plugin Installed Successfully", "Plugin installed from URL.");
            refresh();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to install plugin from URL", ex);
            showError("Plugin Installation Failed", ex.getMessage());
        }
    }

    /**
     * Install official plugin.
     */
    private void installOfficialPlugin(PluginManifest plugin) {
        if (plugin.installUrl == null || plugin.installUrl.isBlank()) {
            showError("Installation Failed", "Official plugin is missing installUrl field.");
            return;
        }
        try {
            pluginManager.installPluginFromUrl(plugin.installUrl, true);
            showInfo(
                    "Plugin Installed Successfully",
                    "Plugin " + plugin.name + " has been installed.");
            refresh();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to install official plugin: " + plugin.id, ex);
            showError("Failed to install official plugin", ex.getMessage());
        }
    }

    /**
     * Uninstall the installed plugin (including confirmation dialog).
     */
    private void uninstallPlugin(PluginManifest plugin) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Uninstall Plugin");
        confirm.setHeaderText("Confirm Uninstall Plugin");
        confirm.setContentText("Will remove plugin \"" + plugin.name + "\", continue?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
            return;
        }
        try {
            pluginManager.uninstallPlugin(plugin.id);
            showInfo("Plugin Uninstalled", "Plugin " + plugin.name + " has been removed.");
            refresh();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to uninstall plugin: " + plugin.id, ex);
            showError("Failed to uninstall plugin", ex.getMessage());
        }
    }

    /**
     * Format raw type string into human-readable display text.
     */
    private String formatType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "General";
        }
        return switch (rawType.toLowerCase(Locale.ROOT)) {
            case TYPE_BUNDLE -> "Bundle";
            case TYPE_TOOL -> "Tool";
            case TYPE_CHANNEL -> "Channel";
            default -> capitalize(rawType);
        };
    }

    /**
     * Capitalize first letter.
     */
    private String capitalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    /**
     * Show information alert.
     */
    private void showInfo(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Plugin Manager");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Show error alert.
     */
    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Plugin Manager");
        alert.setHeaderText(header);
        alert.setContentText(content == null || content.isBlank() ? "Unknown error" : content);
        alert.showAndWait();
    }

    /**
     * Get current window reference.
     */
    private Window resolveWindow() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    /**
     * Create flexible spacer node.
     */
    private Node spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /**
     * Convert null to empty string.
     */
    private String nullToEmpty(String val) {
        return val == null ? "" : val;
    }

    /**
     * Custom row style: keep white background and black text even in selected state, preventing dark selection background from obscuring text.
     */
    private static final class TableCellStyleRow<T> extends TableRow<T> {

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                getStyleClass().removeAll("bg-white");
                getStyleClass().add("bg-white");
                return;
            }
            if (isSelected()) {
                getStyleClass().removeAll("bg-white");
                getStyleClass().addAll("bg-white", "text-secondary");
            } else {
                getStyleClass().removeAll("bg-white", "text-secondary");
                getStyleClass().addAll("bg-white", "text-secondary");
            }
        }

        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            getStyleClass().removeAll("bg-white", "text-secondary");
            getStyleClass().addAll("bg-white", "text-secondary");
        }
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        // Plugin management is a global capability, not bound to an Agent, so there is no need to
        // handle Agent switching.
    }

    @Override
    public void refresh() {
        loadInstalledPlugins();
        loadOfficialPlugins();
    }
}
