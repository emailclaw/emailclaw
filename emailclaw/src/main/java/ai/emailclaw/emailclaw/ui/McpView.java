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

import ai.emailclaw.emailclaw.model.McpClientInfo;
import ai.emailclaw.emailclaw.service.McpService;
import java.util.ArrayList;
import java.util.List;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class McpView implements ViewPane {
    /**
     * MCP client management view.
     */
    private static final Logger LOGGER = Logger.getLogger(McpView.class.getName());

    private final McpService mcpService;
    private final VBox root = new VBox(14);
    private final FlowPane grid = new FlowPane(14, 14);

    public McpView(McpService mcpService) {
        this.mcpService = mcpService;
        initUi();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("MCP Clients");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addBtn = new Button("+ Add MCP Client");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setOnAction(e -> showCreateDialog());
        header.getChildren().addAll(title, spacer, addBtn);
        Label desc = new Label("Manage Model Context Protocol (MCP) clients.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        grid.setPadding(new Insets(8, 0, 0, 0));
        root.getChildren().addAll(header, desc, grid);
        renderGrid();
    }

    private void renderGrid() {
        LOGGER.info("Refresh MCP client cards");
        grid.getChildren().clear();
        if (mcpService.list().isEmpty()) {
            Label empty = new Label("No MCP clients configured.");
            empty.getStyleClass().add("muted");
            empty.setPadding(new Insets(40));
            grid.getChildren().add(empty);
            return;
        }
        for (McpClientInfo c : mcpService.list()) {
            grid.getChildren().add(clientCard(c));
        }
    }

    private Node clientCard(McpClientInfo c) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.setPrefWidth(280);
        Label name = new Label(c.name().isBlank() ? c.key() : c.name());
        name.getStyleClass().add("fw-600-15");
        Label key = new Label("Key: " + c.key());
        key.getStyleClass().add("muted");
        Label status = new Label(c.enabled() ? "Connected" : "Disconnected");
        status.getStyleClass().add(c.enabled() ? "status-ready" : "status-off");
        Label cmd = new Label("cmd: " + c.command() + " " + String.join(" ", c.args()));
        cmd.getStyleClass().add("muted");
        cmd.setWrapText(true);
        Label auth =
                new Label(
                        "Auth: "
                                + c.authType()
                                + (c.oauthRedirectUri().isBlank()
                                        ? ""
                                        : " → " + c.oauthRedirectUri()));
        auth.getStyleClass().add("muted");
        Label whitelist =
                new Label(
                        c.toolWhitelistEnabled()
                                ? "Whitelist: " + String.join(", ", c.allowedToolNames())
                                : "Whitelist: disabled");
        whitelist.getStyleClass().add("muted");
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button toggle = new Button(c.enabled() ? "Disconnect" : "Connect");
        toggle.getStyleClass().add("chip-btn");
        toggle.setOnAction(
                e -> {
                    mcpService.toggleEnabled(c);
                    renderGrid();
                });
        Button del = new Button("Remove");
        del.getStyleClass().add("chip-btn");
        del.setOnAction(
                e -> {
                    mcpService.remove(c);
                    renderGrid();
                });
        actions.getChildren().addAll(toggle, del);
        card.getChildren().addAll(name, key, status, cmd, auth, whitelist, actions);
        return card;
    }

    private void showCreateDialog() {
        Dialog<McpClientInfo> dialog = new Dialog<>();
        dialog.setTitle("Add MCP Client");
        ButtonType addType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addType, ButtonType.CANCEL);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(16));
        form.setPrefWidth(480);
        TextField keyF = new TextField();
        keyF.setPromptText("unique-key");
        TextField nameF = new TextField();
        nameF.setPromptText("Display name");
        TextField cmdF = new TextField();
        cmdF.setPromptText("e.g. uvx, npx");
        TextField argsF = new TextField();
        argsF.setPromptText("space-separated args");
        TextArea envF = new TextArea();
        envF.setPrefRowCount(3);
        envF.setPromptText("{\"KEY\":\"VALUE\"}");
        ComboBox<String> authTypeF = new ComboBox<>();
        authTypeF.getItems().addAll("Local", "OAuth", "API Key");
        authTypeF.setValue("Local");
        TextField redirectF = new TextField();
        redirectF.setPromptText("https://example.com/callback");
        TextField oauthScopeF = new TextField();
        oauthScopeF.setPromptText("openid profile email");
        CheckBox whitelistEnabledF = new CheckBox("Enable Whitelist");
        TextField allowedToolsF = new TextField();
        allowedToolsF.setPromptText("tool1, tool2...");
        form.addRow(0, new Label("Key *"), keyF);
        form.addRow(1, new Label("Name"), nameF);
        form.addRow(2, new Label("Command *"), cmdF);
        form.addRow(3, new Label("Arguments"), argsF);
        form.addRow(4, new Label("Auth Type"), authTypeF);
        form.addRow(5, new Label("Redirect URI"), redirectF);
        form.addRow(6, new Label("OAuth Scope"), oauthScopeF);
        form.addRow(7, new Label("Env (JSON)"), envF);
        form.addRow(8, new Label("Whitelist"), whitelistEnabledF);
        form.addRow(9, new Label("Allowed Tools"), allowedToolsF);
        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(
                btn -> {
                    if (btn == addType && !keyF.getText().isBlank() && !cmdF.getText().isBlank()) {
                        List<String> argsList =
                                argsF.getText().isBlank()
                                        ? new ArrayList<>()
                                        : new ArrayList<>(
                                                java.util.List.of(
                                                        argsF.getText().trim().split("\\s+")));
                        List<String> allowedList =
                                allowedToolsF.getText().isBlank()
                                        ? new ArrayList<>()
                                        : new ArrayList<>(
                                                java.util.List.of(
                                                        allowedToolsF
                                                                .getText()
                                                                .trim()
                                                                .split("\\s*,\\s*")));
                        return new McpClientInfo(
                                keyF.getText().trim(),
                                nameF.getText().trim(),
                                false,
                                true,
                                "Local",
                                "",
                                cmdF.getText().trim(),
                                argsList,
                                envF.getText().trim(),
                                new ArrayList<>(),
                                whitelistEnabledF.isSelected(),
                                allowedList,
                                authTypeF.getValue(),
                                redirectF.getText().trim(),
                                oauthScopeF.getText().trim());
                    }
                    return null;
                });
        dialog.showAndWait()
                .ifPresent(
                        c -> {
                            mcpService.add(c);
                            renderGrid();
                        });
        LOGGER.log(Level.FINE, "MCP client creation process ended");
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
