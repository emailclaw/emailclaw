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
import ai.emailclaw.emailclaw.model.SkillInfo;
import ai.emailclaw.emailclaw.service.SkillService;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import tools.jackson.databind.ObjectMapper;

public class SkillsView implements ViewPane {

    /**
     * Workspace skills view.
     *
     * <p>Responsible for skill browsing, creation, and importing from the skill pool.
     */
    private static final Logger LOGGER = Logger.getLogger(SkillsView.class.getName());

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppContext repository;

    private final SkillService skillService;

    private AgentInfo agent;

    private final BorderPane root = new BorderPane();

    private final TilePane grid = new TilePane();

    private final TextField filter = new TextField();

    public SkillsView(AppContext repository, SkillService skillService, AgentInfo agent) {
        this.repository = repository;
        this.skillService = skillService;
        this.agent = agent;
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(10);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        HBox top = new HBox(8);
        Label title = new Label("Agent's skills");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button reload = new Button("Reload");
        Button loadPool = new Button("Load from Skill Pool");
        Button syncPool = new Button("Sync to Skill Pool");
        Button uploadZip = new Button("Install from ZIP");
        Button importHub = new Button("Import Hub");
        Button batchEnable = new Button("Enable Filtered");
        Button batchDisable = new Button("Disable Filtered");
        Button create = new Button("+ Create Skill");
        reload.setOnAction(e -> refresh());
        loadPool.setOnAction(e -> importFromDirChooser());
        syncPool.setOnAction(e -> syncSelectedToPool());
        uploadZip.setOnAction(e -> installFromZip());
        importHub.setOnAction(e -> showImportHubDialog());
        batchEnable.setOnAction(e -> applyBatchEnabled(true));
        batchDisable.setOnAction(e -> applyBatchEnabled(false));
        create.setOnAction(e -> showCreateSkillDialog());
        top.getChildren()
                .addAll(
                        title,
                        spacer,
                        reload,
                        loadPool,
                        syncPool,
                        uploadZip,
                        importHub,
                        batchEnable,
                        batchDisable,
                        create);
        HBox filters = new HBox(8);
        filter.setPromptText("Filter by name");
        TextField tag = new TextField();
        tag.setPromptText("Filter by tag");
        HBox.setHgrow(filter, Priority.ALWAYS);
        HBox.setHgrow(tag, Priority.ALWAYS);
        filter.textProperty().addListener((obs, o, n) -> refresh());
        filters.getChildren().addAll(filter, tag);
        grid.setPrefColumns(4);
        grid.setHgap(10);
        grid.setVgap(10);
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.getStyleClass().add("transparent-scroll");
        page.getChildren().addAll(top, filters, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setCenter(page);
    }

    private void showCreateSkillDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Create Skill");
        Button create = new Button("Create");
        Button cancel = new Button("Cancel");
        TextField name = new TextField();
        name.setPromptText("e.g., weather_query");
        TextArea content =
                new TextArea(
                        "[Format]\n"
                                + "---\n"
                                + "name: skill_name\n"
                                + "description: Brief description\n"
                                + "---\n\n"
                                + "Skill implementation");
        TextArea config = new TextArea("{}");
        VBox box =
                new VBox(
                        8,
                        new Label("Name *"),
                        name,
                        new Label("Content *"),
                        content,
                        new Label("Config"),
                        config);
        HBox actions = new HBox(8, cancel, create);
        VBox root = new VBox(10, box, actions);
        root.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().clear();
        cancel.setOnAction(e -> dialog.close());
        create.setOnAction(
                e -> {
                    if (!name.getText().isBlank()) {
                        skillService.createSkill(
                                agent.getId(), name.getText().trim(), content.getText());
                        dialog.close();
                        refresh();
                    }
                });
        dialog.showAndWait();
    }

    private void showImportHubDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Import Hub");
        TextField url = new TextField("https://skills.sh/vercel-labs/skills/find-skills");
        VBox body =
                new VBox(
                        12,
                        new Label("External hub import is separate from the local Skill Pool."),
                        new Label("Enter Skill URL"),
                        url,
                        new Label("Supported marketplaces"),
                        marketplaceRow("Skills.sh", "ClawHub", "SkillsMP"),
                        marketplaceRow("LobeHub", "LobeHub Market", "GitHub"),
                        marketplaceRow("ModelScope"),
                        new Label("Examples from Skills.sh"));
        Button cancel = new Button("Cancel");
        Button importBtn = new Button("Import Hub");
        importBtn.setDisable(true);
        HBox actions = new HBox(8, cancel, importBtn);
        VBox box = new VBox(12, body, actions);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().clear();
        cancel.setOnAction(e -> dialog.close());
        importBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    private HBox marketplaceRow(String... names) {
        HBox row = new HBox(8);
        for (String n : names) {
            Button b = new Button(n);
            b.getStyleClass().add("chip-btn");
            row.getChildren().add(b);
        }
        return row;
    }

    private void importFromDirChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Load skill from Skill Pool directory");
        File selected =
                repository.paths().skillsPoolRoot.toFile().exists()
                        ? chooser.showDialog(
                                root.getScene() == null ? null : root.getScene().getWindow())
                        : null;
        Path source = selected == null ? null : selected.toPath();
        if (source != null) {
            skillService.importSkillDirToWorkspace(agent.getId(), source);
            refresh();
        }
    }

    private void installFromZip() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Install skill from ZIP");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Files", "*.zip"));
        File selected =
                chooser.showOpenDialog(
                        root.getScene() == null ? null : root.getScene().getWindow());
        if (selected != null) {
            skillService.importSkillZip(agent.getId(), selected.toPath());
            refresh();
        }
    }

    private void syncSelectedToPool() {
        SkillInfo first =
                skillService.listWorkspaceSkills(agent.getId()).stream().findFirst().orElse(null);
        if (first == null) {
            return;
        }
        Path dir = repository.skillsForWorkspace(agent.getId()).resolve(first.name());
        skillService.importSkillToPool(dir);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        this.agent = agent;
        refresh();
    }

    @Override
    public void refresh() {
        LOGGER.log(Level.FINE, "Refresh skill list: agent={0}", agent.getId());
        grid.getChildren().clear();
        String keyword = filter.getText() == null ? "" : filter.getText().trim().toLowerCase();
        for (SkillInfo skill : skillService.listWorkspaceSkills(agent.getId())) {
            if (!keyword.isBlank() && !skill.name().toLowerCase().contains(keyword)) {
                continue;
            }
            VBox card = new VBox(6);
            card.getStyleClass().add("card");
            Label title = new Label(skill.name());
            title.getStyleClass().add("card-title");
            Label status = new Label(skill.enabled() ? "Enabled" : "Disabled");
            status.getStyleClass().add(skill.enabled() ? "status-ready" : "status-off");
            Label tag = new Label(skill.builtIn() ? "Built-in" : "Custom");
            tag.getStyleClass().add("muted-badge");
            Label desc = new Label(skill.description());
            desc.setWrapText(true);
            Button toggle = new Button(skill.enabled() ? "Disable" : "Enable");
            toggle.getStyleClass().add("chip-btn");
            toggle.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            toggle.setOnAction(
                    e -> {
                        try {
                            skillService.setSkillEnabled(
                                    agent.getId(), skill.name(), !skill.enabled());
                            refresh();
                        } catch (Exception ex) {
                            new javafx.scene.control.Alert(
                                            javafx.scene.control.Alert.AlertType.ERROR,
                                            ex.getMessage())
                                    .showAndWait();
                        }
                    });
            Button delete = new Button("Delete");
            delete.getStyleClass().add("chip-btn");
            delete.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            delete.setOnAction(e -> deleteSkill(skill));
            HBox actions = new HBox(8, toggle, delete);
            // The whole card except buttons is used as an edit entry; buttons consume mouse events
            // to avoid mistakenly opening the edit dialog.
            card.setOnMouseClicked(e -> showEditSkillDialog(skill));
            card.getChildren().addAll(status, title, tag, desc, actions);
            card.setPrefWidth(280);
            card.setPrefHeight(200);
            grid.getChildren().add(card);
        }
    }

    private void showEditSkillDialog(SkillInfo skill) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Skill");
        TextField name = new TextField(skill.name());
        TextArea description = new TextArea(skill.description() == null ? "" : skill.description());
        description.setPrefRowCount(2);
        TextArea content = new TextArea(skill.content() == null ? "" : skill.content());
        content.setPrefRowCount(14);
        TextField channels =
                new TextField(
                        String.join(
                                ", ",
                                skill.channels().isEmpty() ? List.of("all") : skill.channels()));
        TextField tags = new TextField(String.join(", ", skill.tags()));
        TextArea config = new TextArea(toPrettyJson(skill.config()));
        config.setPrefRowCount(4);
        TextField type = new TextField(skill.builtIn() ? "Built-in" : "Custom");
        type.setDisable(true);
        TextField installedFrom = new TextField(installedFromLabel(skill.installedFrom()));
        installedFrom.setDisable(true);
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Name"), 0, 0);
        form.add(name, 1, 0);
        form.add(new Label("Description"), 0, 1);
        form.add(description, 1, 1);
        form.add(new Label("Content"), 0, 2);
        form.add(content, 1, 2);
        form.add(new Label("Channels"), 0, 3);
        form.add(channels, 1, 3);
        form.add(new Label("Tags"), 0, 4);
        form.add(tags, 1, 4);
        form.add(new Label("Config"), 0, 5);
        form.add(config, 1, 5);
        form.add(new Label("Type"), 0, 6);
        form.add(type, 1, 6);
        form.add(new Label("Installed from"), 0, 7);
        form.add(installedFrom, 1, 7);
        GridPane.setHgrow(name, Priority.ALWAYS);
        GridPane.setHgrow(content, Priority.ALWAYS);
        GridPane.setHgrow(channels, Priority.ALWAYS);
        VBox box = new VBox(10, form);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(cancelType, saveType);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.addEventFilter(
                MouseEvent.MOUSE_CLICKED,
                e -> {
                    try {
                        // Only lock Type / Installed from when saving; other fields are written
                        // back to SKILL.md or metadata file.
                        SkillInfo updated =
                                copyForSave(
                                        skill, name, description, content, channels, tags, config);
                        skillService.saveSkill(agent.getId(), skill.name(), updated);
                        dialog.close();
                        refresh();
                    } catch (Exception ex) {
                        e.consume();
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                    }
                });
        dialog.showAndWait();
    }

    private SkillInfo copyForSave(
            SkillInfo skill,
            TextField name,
            TextArea description,
            TextArea content,
            TextField channels,
            TextField tags,
            TextArea config) {
        String mergedContent =
                mergeFrontmatterDescription(
                        content.getText(), name.getText().trim(), description.getText().trim());
        return new SkillInfo(
                name.getText().trim(),
                skill.title(),
                description.getText().trim(),
                mergedContent,
                skill.source(),
                skill.installedFrom(),
                skill.storageName(),
                skill.builtIn(),
                skill.enabled(),
                skill.updatedAt(),
                parseCsv(channels.getText(), List.of("all")),
                Arrays.stream(tags.getText().split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList(),
                parseConfig(config.getText()));
    }

    private List<String> parseCsv(String text, List<String> defaultValue) {
        List<String> values =
                Arrays.stream((text == null ? "" : text).split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
        return values.isEmpty() ? defaultValue : values;
    }

    private Map<String, Object> parseConfig(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<?, ?> raw = JSON.readValue(text, Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String mergeFrontmatterDescription(String content, String name, String description) {
        // Description on the card comes from SKILL.md frontmatter; when editing the description
        // separately, update frontmatter synchronously,
        // so that the list refresh, runtime reading, and Emailclaw's data structure are consistent.
        String normalizedContent = content == null ? "" : content;
        if (!normalizedContent.startsWith("---")) {
            return "---\nname: "
                    + name
                    + "\ndescription: "
                    + description
                    + "\n---\n\n"
                    + normalizedContent;
        }
        int end = normalizedContent.indexOf("\n---", 3);
        if (end < 0) {
            return normalizedContent;
        }
        String body = normalizedContent.substring(end);
        return "---\nname: " + name + "\ndescription: " + description + body;
    }

    private String toPrettyJson(Map<String, Object> value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String installedFromLabel(String installedFrom) {
        if (installedFrom == null || installedFrom.isBlank()) {
            return "";
        }
        return switch (installedFrom) {
            case "skills-sh" -> "skills.sh";
            case "github" -> "GitHub";
            case "lobehub" -> "LobeHub";
            case "modelscope" -> "ModelScope";
            case "aliyun" -> "Aliyun";
            case "skillsmp" -> "SkillsMP";
            case "clawhub" -> "ClawHub";
            case "zip" -> "ZIP";
            default -> installedFrom;
        };
    }

    private void deleteSkill(SkillInfo skill) {
        Alert confirm =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Delete skill '" + skill.name() + "'?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        confirm.showAndWait()
                .filter(ButtonType.OK::equals)
                .ifPresent(
                        button -> {
                            try {
                                skillService.deleteSkill(agent.getId(), skill.name());
                                refresh();
                            } catch (Exception ex) {
                                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        });
    }

    private void applyBatchEnabled(boolean enabled) {
        List<SkillInfo> filtered = new ArrayList<>();
        String keyword = filter.getText() == null ? "" : filter.getText().trim().toLowerCase();
        for (SkillInfo skill : skillService.listWorkspaceSkills(agent.getId())) {
            if (!keyword.isBlank() && !skill.name().toLowerCase().contains(keyword)) {
                continue;
            }
            filtered.add(skill);
        }
        Map<String, String> errors =
                skillService.setSkillsEnabled(
                        agent.getId(), filtered.stream().map(s -> s.name()).toList(), enabled);
        refresh();
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            errors.forEach(
                    (name, reason) -> sb.append(name).append(": ").append(reason).append("\n"));
            new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.WARNING,
                            "Batch operation completed with errors:\n" + sb)
                    .showAndWait();
        }
    }
}
