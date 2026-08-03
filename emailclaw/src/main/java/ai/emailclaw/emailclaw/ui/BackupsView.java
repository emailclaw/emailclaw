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

import ai.emailclaw.emailclaw.model.BackupInfo;
import ai.emailclaw.emailclaw.service.BackupService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class BackupsView implements ViewPane {
    /**
     * Backup management view.
     */
    private static final Logger LOGGER = Logger.getLogger(BackupsView.class.getName());

    private final BackupService backupService;
    private final VBox root = new VBox(14);
    private final VBox backupList = new VBox(8);
    private final TextField searchField = new TextField();

    public BackupsView(BackupService backupService) {
        this.backupService = backupService;
        initUi();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Backups");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button createBtn = new Button("+ Create Backup");
        createBtn.getStyleClass().add("primary-btn");
        createBtn.setOnAction(e -> showCreateDialog());
        Button importBtn = new Button("Import");
        importBtn.getStyleClass().add("chip-btn");
        header.getChildren().addAll(title, spacer, createBtn, importBtn);

        Label desc =
                new Label(
                        "Create, restore, and manage application backups. "
                                + "Backups include agent workspaces, config, and skill pool.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);

        searchField.setPromptText("Search backups...");
        searchField.textProperty().addListener((o, ov, nv) -> renderList());

        ScrollPane scroll = new ScrollPane(backupList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("left-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, desc, searchField, scroll);
        renderList();
    }

    private void renderList() {
        LOGGER.fine("Refreshing backup list");
        backupList.getChildren().clear();
        String q = searchField.getText().toLowerCase().trim();
        var filtered =
                backupService.list().stream()
                        .filter(
                                b ->
                                        q.isEmpty()
                                                || b.name().toLowerCase().contains(q)
                                                || b.id().toLowerCase().contains(q))
                        .toList();
        if (filtered.isEmpty()) {
            Label empty = new Label("No backups found. Create one to safeguard your data.");
            empty.getStyleClass().add("muted");
            empty.setPadding(new Insets(30));
            backupList.getChildren().add(empty);
            return;
        }
        for (BackupInfo b : filtered) {
            backupList.getChildren().add(backupCard(b));
        }
    }

    private Node backupCard(BackupInfo b) {
        HBox card = new HBox(12);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(b.name());
        name.getStyleClass().add("fw-600-15");
        Label id = new Label(b.id());
        id.getStyleClass().add("muted");
        Label date = new Label("Created: " + b.createdAt());
        date.getStyleClass().add("muted");
        HBox scope = new HBox(8);
        if (b.includeAgents()) scope.getChildren().add(badge("Agents"));
        if (b.includeGlobalConfig()) scope.getChildren().add(badge("Config"));
        if (b.includeSkillPool()) scope.getChildren().add(badge("Skills"));
        if (b.includeSecrets()) scope.getChildren().add(badge("Secrets"));
        info.getChildren().addAll(name, id, date, scope);

        VBox actions = new VBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button restore = new Button("Restore");
        restore.getStyleClass().add("chip-btn");
        Button del = new Button("Delete");
        del.getStyleClass().add("chip-btn");
        del.setOnAction(
                e -> {
                    backupService.remove(b);
                    renderList();
                });
        Button export = new Button("Export");
        export.getStyleClass().add("chip-btn");
        actions.getChildren().addAll(restore, export, del);
        card.getChildren().addAll(info, actions);
        return card;
    }

    private Label badge(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted-badge");
        return l;
    }

    private void showCreateDialog() {
        LOGGER.fine("Opening create backup dialog");
        Dialog<BackupInfo> dialog = new Dialog<>();
        dialog.setTitle("Create Backup");
        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(16));
        form.setPrefWidth(460);
        TextField nameF = new TextField();
        nameF.setPromptText("Backup name");
        TextField descF = new TextField();
        descF.setPromptText("Optional description");
        CheckBox agents = new CheckBox("Include Agents");
        agents.setSelected(true);
        CheckBox config = new CheckBox("Include Global Config");
        config.setSelected(true);
        CheckBox skills = new CheckBox("Include Skill Pool");
        skills.setSelected(true);
        CheckBox secrets = new CheckBox("Include Secrets");
        form.addRow(0, new Label("Name *"), nameF);
        form.addRow(1, new Label("Description"), descF);
        form.add(agents, 0, 2, 2, 1);
        form.add(config, 0, 3, 2, 1);
        form.add(skills, 0, 4, 2, 1);
        form.add(secrets, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(
                btn -> {
                    if (btn == createType && !nameF.getText().isBlank()) {
                        return backupService.create(
                                nameF.getText().trim(),
                                descF.getText().trim(),
                                agents.isSelected(),
                                config.isSelected(),
                                secrets.isSelected(),
                                skills.isSelected());
                    }
                    return null;
                });
        dialog.showAndWait().ifPresent(b -> renderList());
        LOGGER.log(Level.FINE, "Create backup process finished");
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        renderList();
    }
}
