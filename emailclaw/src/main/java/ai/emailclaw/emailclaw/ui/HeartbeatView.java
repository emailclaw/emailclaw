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
import ai.emailclaw.emailclaw.model.HeartbeatConfig;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class HeartbeatView implements ViewPane {
    /**
     * Heartbeat schedule configuration view.
     */
    private static final Logger LOGGER = Logger.getLogger(HeartbeatView.class.getName());

    private final AppContext repository;
    private final VBox root = new VBox(16);
    private AgentInfo currentAgent;
    private HeartbeatConfig config;

    // Controls
    private final CheckBox enabledCheck = new CheckBox("Enable Heartbeat");
    private final Spinner<Integer> intervalSpinner = new Spinner<>(1, 24, 6);
    private final ComboBox<String> intervalUnit = new ComboBox<>();
    private final ComboBox<String> replyTarget = new ComboBox<>();
    private final CheckBox activeHoursCheck = new CheckBox("Active Hours");
    private final TextField startField = new TextField("08:00");
    private final TextField endField = new TextField("22:00");

    public HeartbeatView(AppContext repository, AgentInfo agent) {
        this.repository = repository;
        this.currentAgent = agent;
        this.config = repository.loadHeartbeat(agent.getId());
        initUi();
        loadValues();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));

        Label title = new Label("Heartbeat");
        title.getStyleClass().add("page-title");

        Label desc =
                new Label(
                        "Periodically run the agent with HEARTBEAT.md as query. "
                                + "Configure interval, reply target, and active hours.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);

        // Enable
        enabledCheck.getStyleClass().add("text-14");

        // Interval
        VBox intervalBox = new VBox(6);
        intervalBox.getStyleClass().add("card");
        Label intLabel = new Label("Interval");
        intLabel.getStyleClass().add("fw-600");
        intervalUnit.getItems().addAll("Minutes", "Hours");
        intervalUnit.setValue("Hours");
        HBox intRow = new HBox(8, intervalSpinner, intervalUnit);
        intRow.setAlignment(Pos.CENTER_LEFT);
        intervalBox.getChildren().addAll(intLabel, intRow);

        // Reply Target
        VBox targetBox = new VBox(6);
        targetBox.getStyleClass().add("card");
        Label tLabel = new Label("Reply Target");
        tLabel.getStyleClass().add("fw-600");
        Label tDesc = new Label("Where heartbeat results are dispatched.");
        tDesc.getStyleClass().add("muted");
        replyTarget.getItems().addAll("silent", "last", "main");
        replyTarget.setValue("silent");
        targetBox.getChildren().addAll(tLabel, tDesc, replyTarget);

        // Active Hours
        VBox activeBox = new VBox(6);
        activeBox.getStyleClass().add("card");
        Label aLabel = new Label("Active Hours");
        aLabel.getStyleClass().add("fw-600");
        Label aDesc = new Label("Restrict heartbeat execution to a time window.");
        aDesc.getStyleClass().add("muted");
        HBox hoursRow = new HBox(8, new Label("From"), startField, new Label("To"), endField);
        hoursRow.setAlignment(Pos.CENTER_LEFT);
        startField.setPrefWidth(80);
        endField.setPrefWidth(80);
        activeBox.getChildren().addAll(aLabel, aDesc, activeHoursCheck, hoursRow);

        // Save button
        Button saveBtn = new Button("Save Heartbeat");
        saveBtn.getStyleClass().add("primary-btn");
        saveBtn.setOnAction(e -> saveValues());

        root.getChildren()
                .addAll(title, desc, enabledCheck, intervalBox, targetBox, activeBox, saveBtn);
    }

    private void loadValues() {
        enabledCheck.setSelected(config.enabled());
        intervalSpinner.getValueFactory().setValue(config.intervalValue());
        intervalUnit.setValue(config.intervalUnit());
        replyTarget.setValue(config.replyTarget());
        activeHoursCheck.setSelected(config.activeHoursEnabled());
        startField.setText(config.activeHoursStart());
        endField.setText(config.activeHoursEnd());
    }

    private void saveValues() {
        config =
                new HeartbeatConfig(
                        enabledCheck.isSelected(),
                        intervalSpinner.getValue(),
                        intervalUnit.getValue(),
                        replyTarget.getValue(),
                        activeHoursCheck.isSelected(),
                        startField.getText().trim(),
                        endField.getText().trim());
        repository.saveHeartbeat(currentAgent.getId(), config);
        LOGGER.log(
                Level.INFO,
                "Save heartbeat config: agent={0}, enabled={1}",
                new Object[] {currentAgent.getId(), config.enabled()});
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        currentAgent = agent;
        config = repository.loadHeartbeat(agent.getId());
        loadValues();
    }

    @Override
    public void refresh() {
        config = repository.loadHeartbeat(currentAgent.getId());
        loadValues();
    }
}
