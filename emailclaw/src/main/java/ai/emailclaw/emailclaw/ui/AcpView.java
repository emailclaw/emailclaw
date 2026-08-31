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

import ai.emailclaw.emailclaw.model.AcpAgentInfo;
import ai.emailclaw.emailclaw.service.AcpService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AcpView implements ViewPane {
    /**
     * ACP agent management view.
     */
    private static final Logger LOGGER = Logger.getLogger(AcpView.class.getName());

    private final AcpService acpService;
    private final VBox root = new VBox(14);
    private final FlowPane grid = new FlowPane(14, 14);

    public AcpView(AcpService acpService) {
        this.acpService = acpService;
        initUi();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("ACP Agents");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addBtn = new Button("+ Add ACP Agent");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setOnAction(e -> showCreateDialog());
        header.getChildren().addAll(title, spacer, addBtn);
        Label desc =
                new Label(
                        "Agent Communication Protocol (ACP) agents for multi-agent collaboration.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        grid.setPadding(new Insets(8, 0, 0, 0));
        root.getChildren().addAll(header, desc, grid);
        renderGrid();
    }

    private void renderGrid() {
        LOGGER.fine("Refreshing ACP agent cards");
        grid.getChildren().clear();
        for (AcpAgentInfo a : acpService.list()) {
            grid.getChildren().add(agentCard(a));
        }
    }

    private Node agentCard(AcpAgentInfo a) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.setPrefWidth(260);
        HBox hdr = new HBox(6);
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(a.getKey());
        name.getStyleClass().add("fw-600-15");
        Label badge = new Label(a.isBuiltIn() ? "Built-in" : "Custom");
        badge.getStyleClass().add(a.isBuiltIn() ? "status-builtin" : "status-custom");
        hdr.getChildren().addAll(name, badge);
        Label cmd = new Label("$ " + a.getCommand() + " " + a.getArgs());
        cmd.getStyleClass().add("muted");
        cmd.setWrapText(true);
        Label status = new Label(a.isEnabled() ? "Enabled" : "Disabled");
        status.getStyleClass().add(a.isEnabled() ? "status-ready" : "status-off");
        Label trust = new Label("Trusted: " + (a.isTrusted() ? "Yes" : "No"));
        trust.getStyleClass().add("muted");
        Label parseMode = new Label("Parse: " + a.getToolParseMode());
        parseMode.getStyleClass().add("muted");
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button toggle = new Button(a.isEnabled() ? "Disable" : "Enable");
        toggle.getStyleClass().add("chip-btn");
        toggle.setOnAction(
                e -> {
                    acpService.toggleEnabled(a);
                    renderGrid();
                });
        if (!a.isBuiltIn()) {
            Button del = new Button("Remove");
            del.getStyleClass().add("chip-btn");
            del.setOnAction(
                    e -> {
                        acpService.remove(a);
                        renderGrid();
                    });
            actions.getChildren().add(del);
        }
        actions.getChildren().add(0, toggle);
        card.getChildren().addAll(hdr, cmd, status, trust, parseMode, actions);
        return card;
    }

    private void showCreateDialog() {
        Dialog<AcpAgentInfo> dialog = new Dialog<>();
        dialog.setTitle("Add ACP Agent");
        ButtonType addType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addType, ButtonType.CANCEL);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(16));
        form.setPrefWidth(460);
        TextField keyF = new TextField();
        keyF.setPromptText("agent-key");
        TextField cmdF = new TextField();
        cmdF.setPromptText("command");
        TextField argsF = new TextField();
        argsF.setPromptText("arguments");
        CheckBox trustedF = new CheckBox("Trusted");
        trustedF.setSelected(true);
        ComboBox<String> parseF = new ComboBox<>();
        parseF.getItems().addAll("call_title", "call_detail", "update_detail");
        parseF.setValue("call_title");
        form.addRow(0, new Label("Key *"), keyF);
        form.addRow(1, new Label("Command *"), cmdF);
        form.addRow(2, new Label("Arguments"), argsF);
        form.addRow(3, new Label("Trusted"), trustedF);
        form.addRow(4, new Label("Tool Parse Mode"), parseF);
        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(
                btn -> {
                    if (btn == addType && !keyF.getText().isBlank() && !cmdF.getText().isBlank()) {
                        AcpAgentInfo a = new AcpAgentInfo();
                        a.setKey(keyF.getText().trim());
                        a.setCommand(cmdF.getText().trim());
                        a.setArgs(argsF.getText().trim());
                        a.setTrusted(trustedF.isSelected());
                        a.setToolParseMode(parseF.getValue());
                        a.setBuiltIn(false);
                        a.setEnabled(true);
                        return a;
                    }
                    return null;
                });
        dialog.showAndWait()
                .ifPresent(
                        a -> {
                            acpService.add(a);
                            renderGrid();
                        });
        LOGGER.log(Level.FINE, "ACP agent creation process finished");
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        renderGrid();
    }
}
