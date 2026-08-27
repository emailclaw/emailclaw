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

import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import ai.emailclaw.emailclaw.util.UuidUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/**
 * Project management view.
 *
 * <p>Card Grid layout displays {@link ProjectInfo}, supports creating, editing, and deleting projects.
 */
public class ProjectsView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(ProjectsView.class.getName());

    private final AppContext repository;

    private final ConfigManager configManager;

    private final ProjectService projectService;

    private final BorderPane root = new BorderPane();

    private final FlowPane cardContainer = new FlowPane(16, 16);

    public ProjectsView(AppContext repository, ProjectService projectService) {
        this.repository = repository;
        this.configManager = repository.configManager();
        this.projectService = projectService;
        this.projectService.addListener(() -> Platform.runLater(this::refresh));
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(16);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(20));
        // --- Header ---
        Label title = new Label("Project  /  All Projects");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button createBtn = new Button("+ New Project");
        createBtn.setStyle(
                "-fx-background-color: #3b82f6; -fx-text-fill: white;"
                        + " -fx-font-weight: 700; -fx-padding: 8 18;"
                        + " -fx-background-radius: 8; -fx-cursor: hand;");
        createBtn.setOnAction(
                e -> {
                    showCreateEditDialog(null)
                            .ifPresent(
                                    project -> {
                                        LOGGER.log(
                                                Level.INFO,
                                                "Successfully created new Project: {0} ({1})",
                                                new Object[] {project.getName(), project.getId()});
                                        projectService.save(project);
                                    });
                });
        HBox header = new HBox(8, title, spacer, createBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        // --- Card container ---
        cardContainer.setAlignment(Pos.TOP_LEFT);
        cardContainer.setPadding(new Insets(4, 0, 0, 0));
        page.getChildren().addAll(header, cardContainer);
        VBox.setVgrow(cardContainer, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page");
        root.setCenter(scroll);
    }

    /**
     * Build card for a single Project.
     */
    private Node buildProjectCard(ProjectInfo project) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setMinWidth(260);
        card.setMaxWidth(320);
        card.setPrefWidth(300);
        card.getStyleClass().add("card-elevated");
        // Name + default marker
        String nameText = "📁 " + project.getName();
        if (project.isDefault()) {
            nameText += " ⭐";
        }
        Label nameLabel = new Label(nameText);
        nameLabel.getStyleClass().addAll("fw-700-15", "text-secondary");
        // Base directory
        Label dirLabel =
                new Label(
                        project.getBaseDirectory().isBlank()
                                ? "(no directory)"
                                : project.getBaseDirectory());
        dirLabel.getStyleClass().add("text-11-muted");
        dirLabel.setWrapText(true);
        // Additional directories count
        int additionalCount =
                project.getAdditionalDirs() != null ? project.getAdditionalDirs().size() : 0;
        Label foldersLabel = new Label("+" + additionalCount + " additional folders");
        foldersLabel.getStyleClass().add("text-11-muted");
        // Task/Session statistics
        Label statsLabel = new Label("Project: " + project.getId());
        statsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280; -fx-font-weight: 600;");
        // Creation time
        String created =
                project.getCreatedAt() == null || project.getCreatedAt().isBlank()
                        ? ""
                        : "Created: " + project.getCreatedAt();
        Label createdLabel = new Label(created);
        createdLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #d1d5db;");
        // Action buttons
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("btn-blue-sm");
        editBtn.setOnAction(
                e -> {
                    showCreateEditDialog(project)
                            .ifPresent(
                                    p -> {
                                        projectService.save(p);
                                    });
                });
        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("btn-red-sm");
        deleteBtn.setDisable(
                project.isDefault() || ProjectService.PROJECT_ID_DEFAULT.equals(project.getId()));
        deleteBtn.setOnAction(
                e -> {
                    confirmDelete(project);
                });
        HBox actions = new HBox(8, editBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        card.getChildren()
                .addAll(nameLabel, dirLabel, foldersLabel, statsLabel, createdLabel, actions);
        return card;
    }

    /**
     * Integrated new/edit project dialog, using a unified and highly optimized interface layout.
     */
    public Optional<ProjectInfo> showCreateEditDialog(ProjectInfo existingProject) {
        boolean isEdit = existingProject != null;
        LOGGER.info(
                isEdit
                        ? "Open edit project dialog: " + existingProject.getId()
                        : "Open new project dialog");
        Dialog<ProjectInfo> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Project" : "Create New Project");
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        String generatedId = isEdit ? existingProject.getId() : UuidUtils.randomUUIDv7().toString();
        Label nameLabel = new Label("Project Name: ");
        TextField nameInput = new TextField(isEdit ? existingProject.getName() : "Project 1");
        nameInput.setPrefWidth(580);
        Label idLabel = new Label("Project ID: ");
        TextField idInput = new TextField(generatedId);
        idInput.setEditable(false);
        idInput.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #4b5563;");
        idInput.setPrefWidth(280);
        Label baseDirLabel = new Label("Base directory: ");
        String initialBaseDir;
        if (isEdit) {
            initialBaseDir = existingProject.getBaseDirectory();
        } else {
            initialBaseDir =
                    AppHomeConstants.HOME_RESOLVED
                                    .resolve(AppHomeConstants.PROJECTS_DIR)
                                    .toAbsolutePath()
                            + "/"
                            + FileNameUtils.sanitizePathName(nameInput.getText(), "Project")
                            + " "
                            + generatedId;
        }
        TextField baseDirInput = new TextField(initialBaseDir);
        baseDirInput.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(baseDirInput, Priority.ALWAYS);
        Button browseBtn = new Button("Browse...");
        browseBtn.setStyle("-fx-cursor: hand;");
        final boolean[] isCustomBaseDir = new boolean[] {isEdit};
        browseBtn.setOnAction(
                e -> {
                    DirectoryChooser chooser = new DirectoryChooser();
                    chooser.setTitle("Select Base Directory");
                    if (!baseDirInput.getText().isBlank()) {
                        try {
                            Path existing = Path.of(baseDirInput.getText().trim());
                            if (Files.exists(existing)) {
                                chooser.setInitialDirectory(existing.toFile());
                            } else if (existing.getParent() != null
                                    && Files.exists(existing.getParent())) {
                                chooser.setInitialDirectory(existing.getParent().toFile());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    File selected =
                            chooser.showDialog(dialog.getDialogPane().getScene().getWindow());
                    if (selected != null) {
                        isCustomBaseDir[0] = true;
                        baseDirInput.setText(selected.getAbsolutePath());
                        nameInput.setText(selected.getName());
                    }
                });
        baseDirInput
                .textProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (isEdit) {
                                isCustomBaseDir[0] = true;
                                return;
                            }
                            String expected =
                                    AppHomeConstants.HOME_RESOLVED
                                                    .resolve(AppHomeConstants.PROJECTS_DIR)
                                                    .toAbsolutePath()
                                            + "/"
                                            + FileNameUtils.sanitizePathName(
                                                    nameInput.getText(), "Project")
                                            + " "
                                            + generatedId;
                            if (!newV.equals(expected)) {
                                isCustomBaseDir[0] = true;
                            }
                        });
        nameInput
                .textProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (!isCustomBaseDir[0]) {
                                baseDirInput.setText(
                                        AppHomeConstants.HOME_RESOLVED
                                                        .resolve(AppHomeConstants.PROJECTS_DIR)
                                                        .toAbsolutePath()
                                                + "/"
                                                + FileNameUtils.sanitizePathName(newV, "Project")
                                                + " "
                                                + generatedId);
                            }
                        });
        HBox baseDirBox = new HBox(6, baseDirInput, browseBtn);
        baseDirBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(baseDirBox, Priority.ALWAYS);
        baseDirBox.setMaxWidth(Double.MAX_VALUE);
        class DirEntry {
            String path;
            boolean writable;

            DirEntry(String path, boolean writable) {
                this.path = path;
                this.writable = writable;
            }
        }
        Label additionalLabel = new Label("Additional Directories:");
        ObservableList<DirEntry> additionalDirsList = FXCollections.observableArrayList();
        if (isEdit && existingProject.getAdditionalDirs() != null) {
            existingProject
                    .getAdditionalDirs()
                    .forEach((k, v) -> additionalDirsList.add(new DirEntry(k, v != null && v)));
        }
        ListView<DirEntry> dirsListView = new ListView<>(additionalDirsList);
        dirsListView.setPrefHeight(120);
        dirsListView.setCellFactory(
                param ->
                        new ListCell<DirEntry>() {

                            private final Label pathLabel = new Label();

                            private final javafx.scene.control.CheckBox writableCheckBox =
                                    new javafx.scene.control.CheckBox("Writable");

                            private final Button removeBtn = new Button("✕");

                            private final HBox container = new HBox(6);

                            {
                                removeBtn.setStyle(
                                        "-fx-text-fill: #ef4444; -fx-background-color: transparent;"
                                                + " -fx-font-weight: bold; -fx-cursor: hand;");
                                removeBtn.setOnAction(
                                        e -> {
                                            DirEntry item = getItem();
                                            if (item != null) {
                                                additionalDirsList.remove(item);
                                            }
                                        });
                                writableCheckBox.setOnAction(
                                        e -> {
                                            DirEntry item = getItem();
                                            if (item != null) {
                                                item.writable = writableCheckBox.isSelected();
                                            }
                                        });
                                HBox spacer = new HBox();
                                HBox.setHgrow(spacer, Priority.ALWAYS);
                                container
                                        .getChildren()
                                        .addAll(pathLabel, spacer, writableCheckBox, removeBtn);
                                container.setAlignment(Pos.CENTER_LEFT);
                            }

                            @Override
                            protected void updateItem(DirEntry item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setGraphic(null);
                                } else {
                                    pathLabel.setText(item.path);
                                    writableCheckBox.setSelected(item.writable);
                                    setGraphic(container);
                                }
                            }
                        });
        Button addDirBtn = new Button("+ Add Directory");
        addDirBtn.setStyle("-fx-cursor: hand;");
        addDirBtn.setOnAction(
                e -> {
                    DirectoryChooser chooser = new DirectoryChooser();
                    chooser.setTitle("Select Additional Directory");
                    File selected =
                            chooser.showDialog(dialog.getDialogPane().getScene().getWindow());
                    if (selected != null) {
                        String path = selected.getAbsolutePath();
                        boolean exists =
                                additionalDirsList.stream().anyMatch(d -> d.path.equals(path));
                        if (!exists) {
                            additionalDirsList.add(new DirEntry(path, false));
                        }
                    }
                });
        HBox addDirBox = new HBox(additionalLabel, new HBox(), addDirBtn);
        HBox.setHgrow(addDirBox.getChildren().get(1), Priority.ALWAYS);
        addDirBox.setAlignment(Pos.CENTER_LEFT);
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(14));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(nameLabel, 0, 0);
        grid.add(nameInput, 1, 0);
        grid.add(idLabel, 0, 1);
        grid.add(idInput, 1, 1);
        grid.add(baseDirLabel, 0, 2);
        grid.add(baseDirBox, 1, 2);
        ColumnConstraints col0 = new ColumnConstraints();
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);
        grid.setMaxWidth(Double.MAX_VALUE);
        layout.getChildren().addAll(grid, addDirBox, dirsListView);
        dialog.getDialogPane().setContent(layout);
        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton
                .disableProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> nameInput.getText().trim().isBlank(),
                                nameInput.textProperty()));
        dialog.setResultConverter(
                btn -> {
                    if (btn != saveType) {
                        return null;
                    }
                    String baseDirStr = baseDirInput.getText().trim();
                    if (!baseDirStr.isBlank()) {
                        try {
                            Path basePath = Path.of(baseDirStr);
                            if (!Files.exists(basePath)) {
                                Files.createDirectories(basePath);
                                LOGGER.log(
                                        Level.INFO,
                                        "Created Base directory for project: {0}",
                                        basePath);
                            }
                        } catch (Exception ex) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Failed to create project Base directory: " + baseDirStr,
                                    ex);
                        }
                    }
                    ProjectInfo p = isEdit ? existingProject : new ProjectInfo();
                    p.setId(generatedId);
                    p.setName(nameInput.getText().trim());
                    p.setBaseDirectory(baseDirStr);
                    java.util.Map<String, Boolean> addDirsMap = new java.util.HashMap<>();
                    for (DirEntry d : additionalDirsList) {
                        addDirsMap.put(d.path, d.writable);
                    }
                    p.setAdditionalDirs(addDirsMap);
                    if (!isEdit) {
                        p.setCreatedAt(LocalDateTime.now().toString());
                    }
                    return p;
                });
        return dialog.showAndWait();
    }

    /**
     * Confirm and execute project deletion, supported via external UI (like MainWindow) or internal card triggers.
     */
    public boolean confirmDelete(ProjectInfo project) {
        if (project.isDefault() || ProjectService.PROJECT_ID_DEFAULT.equals(project.getId())) {
            return false;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Project");
        alert.setContentText(
                "Are you sure you want to delete project \""
                        + project.getName()
                        + "\"? Associated tasks will also be removed.");
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            alert.initOwner(root.getScene().getWindow());
        }
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            projectService.remove(project);
            return true;
        }
        return false;
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        LOGGER.fine("Refresh project list");
        cardContainer.getChildren().clear();
        for (ProjectInfo project : configManager.getProjects()) {
            cardContainer.getChildren().add(buildProjectCard(project));
        }
    }
}
