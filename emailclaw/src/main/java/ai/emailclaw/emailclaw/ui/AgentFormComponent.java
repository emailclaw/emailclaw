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
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.SkillService;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Reusable Agent property editing form component.
 */
public class AgentFormComponent extends VBox {
    private final ProviderService providerService;
    private final SkillService skillService;

    private TextField idField;
    private TextField nameField;
    private TextArea descField;
    private SimpleToggleSwitch enableSwitch;
    private ModelSelectionComponent primaryModelBtn;
    private Spinner<Integer> maxRetriesSpinner;
    private ModelSelectionComponent fallbackModelBtn;
    private TextField workspaceField;

    private FlowPane skillsPane;
    private final List<CheckBox> skillChecks = new ArrayList<>();

    private BooleanBinding validBinding;

    private String fallbackProviderId;
    private String fallbackModelId;

    private String primaryProviderId;
    private String primaryModelId;

    public AgentFormComponent(ProviderService providerService, SkillService skillService) {
        super(12);
        this.providerService = providerService;
        this.skillService = skillService;
        buildUi();
    }

    private void buildUi() {
        idField = new TextField();
        idField.setPromptText("e.g: my-agent");

        nameField = new TextField();
        nameField.setPromptText("e.g: My Agent");

        descField = new TextArea();
        descField.setPromptText("Briefly describe this agent's purpose...");
        descField.setPrefRowCount(4);

        workspaceField = new TextField();
        workspaceField.setPromptText("~/emailclaw/agent-workspace/my-agent");

        enableSwitch = new SimpleToggleSwitch();
        enableSwitch.setSelected(true);

        primaryModelBtn = new ModelSelectionComponent("Select primary model", providerService);

        HBox modelBox = new HBox(8, new Label("Primary model"), primaryModelBtn);
        modelBox.setAlignment(Pos.CENTER_LEFT);

        maxRetriesSpinner = new Spinner<>(0, 10, 3);
        maxRetriesSpinner.setEditable(true);
        maxRetriesSpinner.setPrefWidth(70);

        fallbackModelBtn = new ModelSelectionComponent("Select fallback model", providerService);

        HBox fallbackBox =
                new HBox(
                        12,
                        new Label("Fallback model"),
                        fallbackModelBtn,
                        new Label("Max Retries"),
                        maxRetriesSpinner);
        fallbackBox.setAlignment(Pos.CENTER_LEFT);

        skillsPane = new FlowPane();
        skillsPane.setHgap(8);
        skillsPane.setVgap(8);
        ScrollPane skillsScroll = new ScrollPane(skillsPane);
        skillsScroll.setFitToWidth(true);
        skillsScroll.setPrefHeight(120);
        skillsScroll.setStyle(
                "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");

        Label skillsLabel = new Label("Skills");
        Button selectAllBtn = new Button("Select all");
        selectAllBtn.getStyleClass().add("chip-btn");
        selectAllBtn.setOnAction(e -> skillChecks.forEach(cb -> cb.setSelected(true)));

        Button selectNoneBtn = new Button("Select none");
        selectNoneBtn.getStyleClass().add("chip-btn");
        selectNoneBtn.setOnAction(e -> skillChecks.forEach(cb -> cb.setSelected(false)));

        HBox skillsHeader = new HBox(12, skillsLabel, selectAllBtn, selectNoneBtn);
        skillsHeader.setAlignment(Pos.CENTER_LEFT);

        this.getChildren()
                .addAll(
                        new Label("Agent ID (optional if new)"),
                        idField,
                        new Label("* Name"),
                        nameField,
                        new Label("Description"),
                        descField,
                        modelBox,
                        fallbackBox,
                        new Label("Workspace Path"),
                        workspaceField,
                        skillsHeader,
                        skillsScroll);

        validBinding =
                Bindings.createBooleanBinding(
                        () ->
                                !nameField.getText().trim().isBlank()
                                        && primaryProviderId != null
                                        && primaryModelId != null,
                        nameField.textProperty(),
                        primaryModelBtn.textProperty());
    }

    public SimpleToggleSwitch getEnableSwitch() {
        return enableSwitch;
    }

    public BooleanBinding validProperty() {
        return validBinding;
    }

    public void populate(AgentInfo agent, boolean isNew) {
        idField.setEditable(isNew);
        if (isNew) {
            idField.setPromptText("Auto-generated if empty");
        }

        idField.setText(agent.getId() == null ? "" : agent.getId());
        nameField.setText(agent.getName() == null ? "" : agent.getName());
        descField.setText(agent.getDescription() == null ? "" : agent.getDescription());
        workspaceField.setText(agent.getWorkspacePath() == null ? "" : agent.getWorkspacePath());
        enableSwitch.setSelected(agent.isEnabled());

        this.primaryProviderId = agent.getProviderId();
        this.primaryModelId = agent.getModelId();
        if (this.primaryModelId != null && !this.primaryModelId.isBlank()) {
            primaryModelBtn.setText(this.primaryModelId);
        } else {
            primaryModelBtn.setText("Select primary model");
        }
        primaryModelBtn.setOnModelSelected(
                (p, m) -> {
                    primaryProviderId = p.getId();
                    primaryModelId = m.getId();
                    primaryModelBtn.setText(m.getName());
                });

        maxRetriesSpinner.getValueFactory().setValue(agent.getMaxRetries());

        this.fallbackProviderId = agent.getFallbackProviderId();
        this.fallbackModelId = agent.getFallbackModelId();
        if (this.fallbackModelId != null && !this.fallbackModelId.isBlank()) {
            fallbackModelBtn.setText(this.fallbackModelId);
        } else {
            fallbackModelBtn.setText("Select fallback model");
        }
        fallbackModelBtn.setOnModelSelected(
                (p, m) -> {
                    fallbackProviderId = p.getId();
                    fallbackModelId = m.getId();
                    fallbackModelBtn.setText(m.getName());
                });

        skillsPane.getChildren().clear();
        skillChecks.clear();
        List<String> poolSkills =
                skillService.listSkillPool().stream().map(SkillInfo::name).toList();
        for (String s : poolSkills) {
            CheckBox c = new CheckBox(s);
            if (agent.getSkillNames().contains(s)) {
                c.setSelected(true);
            }
            skillChecks.add(c);
            skillsPane.getChildren().add(c);
        }
    }

    public void commitTo(AgentInfo agent) {
        String newId = idField.getText().trim();
        if (!newId.isBlank()) {
            agent.setId(newId);
        }
        agent.setName(nameField.getText().trim());
        agent.setDescription(descField.getText());
        agent.setWorkspacePath(FileNameUtils.expandUserHome(workspaceField.getText().trim()));
        agent.setEnabled(enableSwitch.isSelected());

        agent.setProviderId(this.primaryProviderId == null ? "" : this.primaryProviderId);
        agent.setModelId(this.primaryModelId == null ? "" : this.primaryModelId);
        agent.setMaxRetries(maxRetriesSpinner.getValue());

        agent.setFallbackProviderId(this.fallbackProviderId);
        agent.setFallbackModelId(this.fallbackModelId);

        List<String> selectedSkills =
                skillChecks.stream().filter(CheckBox::isSelected).map(CheckBox::getText).toList();
        agent.getSkillNames().clear();
        agent.getSkillNames().addAll(selectedSkills);
    }
}
