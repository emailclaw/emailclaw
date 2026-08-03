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
import ai.emailclaw.emailclaw.model.AgentRuntimeStatus;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.SkillService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AgentManagementView implements ViewPane {

    /**
     * Agent management view.
     */
    private static final Logger LOGGER = Logger.getLogger(AgentManagementView.class.getName());

    private final AgentService agentService;

    private final ProviderService providerService;

    private final SkillService skillService;

    private final Consumer<AgentInfo> onCreated;

    private final Consumer<AgentInfo> onAgentSelected;

    private final BorderPane root = new BorderPane();

    private final ObservableList<AgentInfo> agents = FXCollections.observableArrayList();

    private final TableView<AgentInfo> table = new TableView<>(agents);

    public AgentManagementView(
            AgentService agentService,
            ProviderService providerService,
            SkillService skillService,
            Consumer<AgentInfo> onCreated,
            Consumer<AgentInfo> onAgentSelected) {
        this.agentService = agentService;
        this.providerService = providerService;
        this.skillService = skillService;
        this.onCreated = onCreated;
        this.onAgentSelected = onAgentSelected;
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(10);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        HBox head = new HBox(8);
        Label title = new Label("Settings  /  Agents");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button create = new Button("+ Create Agent");
        create.setOnAction(e -> showCreateDialog());
        head.getChildren().addAll(title, spacer, create);
        table.getColumns().add(col("Name", a -> a.getName()));
        table.getColumns().add(col("ID", a -> a.getId()));
        table.getColumns().add(col("Description", a -> a.getDescription()));
        table.getColumns().add(col("Workspace Path", a -> a.getWorkspacePath()));
        table.getColumns()
                .add(
                        col(
                                "Model",
                                a ->
                                        a.getModelId().isBlank()
                                                ? "Use global default"
                                                : a.getModelId()));
        table.getColumns().add(col("Runtime Status", this::statusText));

        table.setRowFactory(
                tv -> {
                    TableRow<AgentInfo> row = new TableRow<>();
                    row.setOnMouseClicked(
                            event -> {
                                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                                    onAgentSelected.accept(row.getItem());
                                }
                            });
                    return row;
                });

        page.getChildren().addAll(head, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(page);
    }

    private TableColumn<AgentInfo, String> col(String title, Function<AgentInfo, String> map) {
        TableColumn<AgentInfo, String> col = new TableColumn<>(title);
        col.setCellValueFactory(v -> new SimpleStringProperty(map.apply(v.getValue())));
        return col;
    }

    private String statusText(AgentInfo agent) {
        AgentRuntimeStatus status = agentService.statusOf(agent.getId());
        return status.status().getDescription() + " / running=" + status.runningTaskCount();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        agents.setAll(agentService.list());
    }

    public void showCreateDialog() {
        AgentInfo agent = new AgentInfo();
        agent.setEnabled(true);
        AgentFormComponent formComponent = new AgentFormComponent(providerService, skillService);
        formComponent.populate(agent, true);

        javafx.scene.control.Dialog<AgentInfo> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Create Agent");
        dialog.setHeaderText("Add a new AI Agent to your workspace");
        if (root.getScene() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }

        dialog.getDialogPane().setContent(formComponent);

        javafx.scene.control.ButtonType saveBtnType =
                new javafx.scene.control.ButtonType(
                        "Save", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(saveBtnType, javafx.scene.control.ButtonType.CANCEL);

        Node saveBtnNode = dialog.getDialogPane().lookupButton(saveBtnType);
        saveBtnNode.disableProperty().bind(formComponent.validProperty().not());

        dialog.setResultConverter(
                dialogButton -> {
                    if (dialogButton == saveBtnType) {
                        formComponent.commitTo(agent);
                        if (agent.getId() == null || agent.getId().isBlank()) {
                            agent.setId(
                                    "agent-"
                                            + java.util
                                                    .UUID
                                                    .randomUUID()
                                                    .toString()
                                                    .substring(0, 8));
                        }
                        return agent;
                    }
                    return null;
                });

        dialog.showAndWait()
                .ifPresent(
                        savedAgent -> {
                            agentService.list().add(savedAgent);
                            agentService.save();
                            skillService.applySkillSelections(
                                    savedAgent.getId(), savedAgent.getSkillNames());
                            refresh();
                            if (onCreated != null) {
                                onCreated.accept(savedAgent);
                            }
                        });
    }
}
