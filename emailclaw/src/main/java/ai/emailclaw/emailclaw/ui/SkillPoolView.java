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

import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.model.SkillInfo;
import ai.emailclaw.emailclaw.service.SkillService;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class SkillPoolView implements ViewPane {

    /**
     * Skill Pool view, showing the global skill repository and supporting keyword filtering.
     */
    private static final Logger LOGGER = Logger.getLogger(SkillPoolView.class.getName());

    private final AppContext repository;

    private final SkillService skillService;

    private final BorderPane root = new BorderPane();

    private final TilePane grid = new TilePane();

    private final TextField filter = new TextField();

    private final TextField tagFilter = new TextField();

    private final ListView<String> externalPathsList = new ListView<>();

    private final TextField newPathField = new TextField();

    private final Label externalPathsHint = new Label();

    public SkillPoolView(AppContext repository, SkillService skillService) {
        this.repository = repository;
        this.skillService = skillService;
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(14);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        HBox head = new HBox(8);
        Label title = new Label("Settings  /  Skill Pool");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button reload = new Button("⟳");
        Button broadcast = new Button("Broadcast to Agents");
        Button updateBuiltin = new Button("Update Built-in Skills");
        Button uploadZip = new Button("Upload via Zip");
        Button importHub = new Button("Import Hub");
        Button batch = new Button("Batch Operation");
        Button create = new Button("+ Create Skill");
        Button openPathBtn = new Button("Open Selected Path");
        reload.setOnAction(e -> refresh());
        broadcast.setDisable(true);
        updateBuiltin.setDisable(true);
        importHub.setDisable(true);
        batch.setDisable(true);
        create.setDisable(true);
        // enable upload and open path actions
        uploadZip.getStyleClass().add("chip-btn");
        openPathBtn.getStyleClass().add("chip-btn");
        uploadZip.setOnAction(e -> uploadZipToPool());
        openPathBtn.setOnAction(e -> openSelectedPath());
        head.getChildren()
                .addAll(
                        title,
                        spacer,
                        reload,
                        broadcast,
                        updateBuiltin,
                        uploadZip,
                        importHub,
                        batch,
                        create);
        HBox pathInput = new HBox(8);
        newPathField.setPromptText("Add external skill pool path");
        Button addPathBtn = new Button("Add Path");
        addPathBtn.getStyleClass().add("chip-btn");
        addPathBtn.setOnAction(e -> addSkillPoolPath());
        HBox.setHgrow(newPathField, Priority.ALWAYS);
        pathInput.getChildren().addAll(newPathField, addPathBtn);
        externalPathsList.setPrefHeight(120);
        externalPathsList.setCellFactory(
                listView ->
                        new ListCell<>() {

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item);
                            }
                        });
        Button removePathBtn = new Button("Remove Selected");
        removePathBtn.setOnAction(e -> removeSelectedPath());
        removePathBtn.setDisable(true);
        externalPathsList
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, o, n) -> {
                            removePathBtn.setDisable(n == null || n.isBlank());
                        });
        externalPathsHint.setText(
                "External paths are searched in addition to the built-in skill pool.");
        externalPathsHint.getStyleClass().add("muted");
        VBox externalConfig =
                new VBox(
                        8,
                        new Label("External Skill Pool Paths"),
                        pathInput,
                        externalPathsList,
                        new HBox(8, removePathBtn, openPathBtn),
                        externalPathsHint);
        externalConfig.getStyleClass().add("card-lite");
        externalConfig.setPadding(new Insets(12));
        HBox filters = new HBox(8);
        filter.setPromptText("Filter by name");
        tagFilter.setPromptText("Filter by tag");
        HBox.setHgrow(filter, Priority.ALWAYS);
        HBox.setHgrow(tagFilter, Priority.ALWAYS);
        filter.textProperty().addListener((obs, o, n) -> refresh());
        tagFilter.textProperty().addListener((obs, o, n) -> refresh());
        filters.getChildren().addAll(filter, tagFilter);
        grid.setPrefColumns(5);
        grid.setHgap(10);
        grid.setVgap(10);
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("left-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        page.getChildren().addAll(head, externalConfig, filters, scroll);
        root.setCenter(page);
    }

    private void addSkillPoolPath() {
        String rawPath = newPathField.getText();
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }
        String normalized = rawPath.trim();
        Path candidate = Path.of(normalized);
        if (!candidate.isAbsolute()) {
            candidate = repository.paths().root.resolve(candidate).normalize();
        }
        if (!Files.exists(candidate) || !Files.isDirectory(candidate)) {
            externalPathsHint.setText("Path not found or not a directory: " + normalized);
            externalPathsHint
                    .getStyleClass()
                    .removeAll("text-orange-700", "text-gray6", "text-green2");
            externalPathsHint.getStyleClass().add("text-orange-700");
            return;
        }
        GlobalConfig config = repository.loadGlobalConfig();
        if (config.getSkillPoolPaths().contains(candidate.toString())) {
            externalPathsHint.setText("Path already added.");
            externalPathsHint
                    .getStyleClass()
                    .removeAll("text-orange-700", "text-gray6", "text-green2");
            externalPathsHint.getStyleClass().add("text-gray6");
            newPathField.clear();
            return;
        }
        config.getSkillPoolPaths().add(candidate.toString());
        repository.saveGlobalConfig(config);
        newPathField.clear();
        externalPathsHint.setText("Path added: " + candidate.toString());
        externalPathsHint.getStyleClass().removeAll("text-orange-700", "text-gray6", "text-green2");
        externalPathsHint.getStyleClass().add("text-green2");
        refresh();
    }

    private void removeSelectedPath() {
        String selected = externalPathsList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) {
            return;
        }
        GlobalConfig config = repository.loadGlobalConfig();
        config.getSkillPoolPaths().removeIf(path -> path.equals(selected));
        repository.saveGlobalConfig(config);
        refresh();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        LOGGER.fine("Refresh skill pool view");
        loadExternalPaths();
        grid.getChildren().clear();
        String keyword = filter.getText() == null ? "" : filter.getText().trim().toLowerCase();
        String tagKeyword =
                tagFilter.getText() == null ? "" : tagFilter.getText().trim().toLowerCase();
        List<SkillInfo> skills = skillService.listSkillPool();
        for (SkillInfo skill : skills) {
            if (!keyword.isBlank() && !skill.name().toLowerCase().contains(keyword)) {
                continue;
            }
            if (!tagKeyword.isBlank()
                    && skill.tags().stream()
                            .map(String::toLowerCase)
                            .noneMatch(tagKeyword::contains)) {
                continue;
            }
            VBox card = new VBox(6);
            card.getStyleClass().add("card");
            Label name = new Label(skill.name());
            name.getStyleClass().add("card-title");
            String statusText =
                    skill.builtIn() ? "Built-in" : (skill.enabled() ? "Enabled" : "Disabled");
            Label status = new Label(statusText);
            status.getStyleClass().add(skill.enabled() ? "status-ready" : "status-warn");
            Label badge = new Label(skill.builtIn() ? "Built-in" : "Custom");
            badge.getStyleClass().add("muted-badge");
            Label updated =
                    new Label(
                            skill.updatedAt() == null || skill.updatedAt().isBlank()
                                    ? "Last updated: unknown"
                                    : "Last updated: " + skill.updatedAt());
            updated.getStyleClass().add("muted");
            Label desc =
                    new Label(
                            skill.description() == null || skill.description().isBlank()
                                    ? ""
                                    : skill.description());
            desc.setWrapText(true);
            card.getChildren().addAll(status, name, badge, updated, desc);
            card.setPrefWidth(310);
            card.setPrefHeight(220);
            grid.getChildren().add(card);
        }
        LOGGER.log(
                Level.FINE,
                "Skill pool rendering complete, card count: {0}",
                grid.getChildren().size());
    }

    private void loadExternalPaths() {
        GlobalConfig config = repository.loadGlobalConfig();
        externalPathsList.getItems().setAll(config.getSkillPoolPaths());
    }

    private void openSelectedPath() {
        String selected = externalPathsList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) {
            return;
        }
        try {
            File dir = Path.of(selected).toFile();
            if (!dir.exists()) {
                externalPathsHint.setText("Path does not exist: " + selected);
                externalPathsHint
                        .getStyleClass()
                        .removeAll("text-orange-700", "text-gray6", "text-green2");
                externalPathsHint.getStyleClass().add("text-orange-700");
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to open path: " + selected, ex);
            externalPathsHint.setText("Failed to open path: " + ex.getMessage());
            externalPathsHint
                    .getStyleClass()
                    .removeAll("text-orange-700", "text-gray6", "text-green2");
            externalPathsHint.getStyleClass().add("text-orange-700");
        }
    }

    private void uploadZipToPool() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select skill ZIP to import to pool");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP files", "*.zip"));
        File zipFile =
                chooser.showOpenDialog(
                        root.getScene() == null ? null : root.getScene().getWindow());
        if (zipFile == null) {
            return;
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("emailclaw-skill-import");
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path out = tempDir.resolve(entry.getName()).normalize();
                    if (!out.startsWith(tempDir)) {
                        throw new IOException("Zip entry escapes target: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            skillService.importSkillToPool(tempDir);
            externalPathsHint.setText("Imported ZIP to skill pool: " + zipFile.getName());
            externalPathsHint
                    .getStyleClass()
                    .removeAll("text-orange-700", "text-gray6", "text-green2");
            externalPathsHint.getStyleClass().add("text-green2");
            refresh();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Import ZIP to pool failed", ex);
            externalPathsHint.setText("Import failed: " + ex.getMessage());
            externalPathsHint
                    .getStyleClass()
                    .removeAll("text-orange-700", "text-gray6", "text-green2");
            externalPathsHint.getStyleClass().add("text-orange-700");
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(
                                    p -> {
                                        try {
                                            Files.deleteIfExists(p);
                                        } catch (IOException ignored) {
                                        }
                                    });
                } catch (IOException ignored) {
                }
            }
        }
    }
}
