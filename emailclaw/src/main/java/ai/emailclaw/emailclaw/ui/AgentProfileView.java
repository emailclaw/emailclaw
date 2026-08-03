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

import ai.emailclaw.emailclaw.model.AgentIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.SkillService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Agent Profile detailed editing view.
 *
 * <p>Provides an embedded view consistent with Agent property editing, including name, description, model selection, workspace path, enable status,
 * and includes a Delete button on the left of the Save button (disabled for default Agent).
 */
public class AgentProfileView implements ViewPane {
    private static final Logger LOGGER = Logger.getLogger(AgentProfileView.class.getName());

    private final AgentService agentService;
    private final ProviderService providerService;
    private final SkillService skillService;
    private final Runnable onAgentUpdated;
    private final BorderPane root = new BorderPane();

    private AgentInfo currentAgent;
    private AgentFormComponent formComponent;
    private Button deleteBtn;
    private Button saveBtn;
    private Button cancelBtn;
    private final Runnable onCancel;

    /**
     * Construct Agent Profile view.
     *
     * @param agentService    Agent service
     * @param providerService Provider service
     * @param initialAgent    Initially displayed Agent
     * @param onAgentUpdated  Callback notification after data changes
     */
    public AgentProfileView(
            AgentService agentService,
            ProviderService providerService,
            SkillService skillService,
            AgentInfo initialAgent,
            Runnable onAgentUpdated,
            Runnable onCancel) {
        this.agentService = agentService;
        this.providerService = providerService;
        this.skillService = skillService;
        this.currentAgent = initialAgent;
        this.onAgentUpdated = onAgentUpdated;
        this.onCancel = onCancel;
        buildUi();
        populateAgent(currentAgent);
    }

    private void buildUi() {
        VBox page = new VBox(16);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(20));

        // Title header
        Label title = new Label("Agent management - profile");
        title.getStyleClass().add("page-title");
        page.getChildren().add(title);

        // Agent role description
        VBox descriptionBox = new VBox(6);
        descriptionBox.setStyle(
                "-fx-background-color: #eef2ff; -fx-padding: 12; -fx-background-radius: 8;"
                        + " -fx-border-color: #c7d2fe; -fx-border-radius: 8;");
        Label descTitle =
                new Label("←Select an Agent from the dropdown to modify. Agent role descriptions:");
        descTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #3730a3;");
        Label desc1 = new Label("• Default: System default assistant Agent.");
        Label desc2 =
                new Label(
                        "• Planner: The brain of the system. Receives raw ambiguous instructions,"
                            + " breaks them down into ordered subtasks, and decides which specific"
                            + " Agent to dispatch the tasks to.");
        Label desc3 =
                new Label(
                        "• Executor: The hands and feet of the system. Calls external tools (search"
                                + " engines, scripts, etc.) to get real data or complete specific"
                                + " calculations.");
        Label desc4 =
                new Label(
                        "• Reviewer: The quality inspector of the system. Checks the executor's"
                                + " output based on standards (specifications, logic, etc.), and"
                                + " requests a retry if it doesn't meet the standard.");
        Label desc5 =
                new Label(
                        "• Synthesizer: The external window of the system. Collects fragmented"
                                + " results and review opinions, and translates them into a final"
                                + " refined response in the format expected by the user.");
        desc1.setWrapText(true);
        desc2.setWrapText(true);
        desc3.setWrapText(true);
        desc4.setWrapText(true);
        desc5.setWrapText(true);
        descriptionBox.getChildren().addAll(descTitle, desc1, desc2, desc3, desc4, desc5);
        page.getChildren().add(descriptionBox);

        // Main editing form card
        VBox card = new VBox(12);
        card.setMaxWidth(640);
        card.getStyleClass().add("card-elevated");
        card.setPadding(new Insets(20));

        formComponent = new AgentFormComponent(providerService, skillService);
        card.getChildren().add(formComponent);

        // Bottom button bar: Delete is on the left of Save
        deleteBtn = new Button("Delete");
        deleteBtn.setStyle(
                "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: 600;"
                        + " -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        deleteBtn.setPrefWidth(90);
        deleteBtn.setOnAction(e -> handleDelete());

        saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-btn");
        saveBtn.setPrefWidth(90);
        saveBtn.setOnAction(e -> handleSave());
        saveBtn.disableProperty().bind(formComponent.validProperty().not());

        cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(
                "-fx-background-color: #e5e7eb; -fx-text-fill: #374151; -fx-font-weight: 600;"
                        + " -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setPrefWidth(90);
        cancelBtn.setOnAction(
                e -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                });

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBar = new HBox(8, spacer, deleteBtn, saveBtn, cancelBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(buttonBar);

        page.getChildren().add(card);
        root.setCenter(page);
    }

    /**
     * Populate or refresh the currently selected Agent data to the UI.
     *
     * @param agent Agent object
     */
    public void populateAgent(AgentInfo agent) {
        this.currentAgent = agent;
        if (agent == null) {
            return;
        }
        LOGGER.log(Level.FINE, "AgentProfileView loaded Agent: {0}", agent.getId());

        boolean isNew = !agentService.list().contains(agent);
        formComponent.populate(agent, isNew);

        // Disable the delete button for Default Agent
        boolean isDefaultAgent = AgentIds.DEFAULT.equals(agent.getId());
        deleteBtn.setDisable(isDefaultAgent);
    }

    private void handleSave() {
        if (currentAgent == null) {
            return;
        }
        LOGGER.log(Level.INFO, "Saving Agent Profile: {0}", currentAgent.getId());
        formComponent.commitTo(currentAgent);

        agentService.save();
        skillService.applySkillSelections(currentAgent.getId(), currentAgent.getSkillNames());
        if (onAgentUpdated != null) {
            onAgentUpdated.run();
        }
    }

    private void handleDelete() {
        if (currentAgent == null
                || AgentIds.DEFAULT.equals(currentAgent.getId())
                || "default".equals(currentAgent.getId())) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Agent");
        confirm.setContentText(
                "Are you sure you want to delete agent \"" + currentAgent.getName() + "\"?");
        if (root.getScene() != null) {
            confirm.initOwner(root.getScene().getWindow());
        }
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            LOGGER.log(Level.INFO, "Executing delete Agent: {0}", currentAgent.getId());
            agentService.remove(currentAgent);
            if (onAgentUpdated != null) {
                onAgentUpdated.run();
            }
        }
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        populateAgent(currentAgent);
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        populateAgent(agent);
    }
}
