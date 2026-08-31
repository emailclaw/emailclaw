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

import ai.emailclaw.emailclaw.model.EnvVariable;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Environment variables management view.
 */
public class EnvironmentsView implements ViewPane {
    private static final Logger LOGGER = Logger.getLogger(EnvironmentsView.class.getName());
    private final AppContext repository;
    private final VBox root = new VBox(14);
    private final VBox envList = new VBox(6);
    private List<EnvVariable> variables;

    public EnvironmentsView(AppContext repository) {
        this.repository = repository;
        this.variables = new ArrayList<>(repository.loadEnvVariables());
        initUi();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Environments");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addBtn = new Button("+ Add Variable");
        addBtn.getStyleClass().add("primary-btn");
        addBtn.setOnAction(e -> addVariable());
        header.getChildren().addAll(title, spacer, addBtn);
        Label desc = new Label("Manage environment variables. Values are encrypted at rest.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        HBox tableHead = new HBox();
        tableHead.getStyleClass().add("row-lite");
        tableHead.getChildren().addAll(hcol("Key", 240), hcol("Value", 340), hcol("Actions", 120));
        ScrollPane scroll = new ScrollPane(envList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("left-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(header, desc, tableHead, scroll);
        renderList();
    }

    private Label hcol(String text, double w) {
        Label l = new Label(text);
        l.setPrefWidth(w);
        l.getStyleClass().add("fw-600-gray");
        return l;
    }

    private void renderList() {
        LOGGER.fine("Refresh environment variables list");
        envList.getChildren().clear();
        if (variables.isEmpty()) {
            Label empty = new Label("No environment variables configured.");
            empty.getStyleClass().add("muted");
            empty.setPadding(new Insets(30));
            envList.getChildren().add(empty);
            return;
        }
        for (EnvVariable v : variables) {
            envList.getChildren().add(envRow(v));
        }
    }

    private Node envRow(EnvVariable v) {
        HBox row = new HBox();
        row.getStyleClass().add("row-lite");
        row.setAlignment(Pos.CENTER_LEFT);
        Label key = new Label(v.key());
        key.setPrefWidth(240);
        key.getStyleClass().add("fw-600");
        Label val = new Label(maskValue(v.value()));
        val.setPrefWidth(340);
        val.getStyleClass().add("muted");
        HBox actions = new HBox(6);
        actions.setPrefWidth(120);
        Button reveal = new Button("Reveal");
        reveal.getStyleClass().add("chip-btn");
        reveal.setOnAction(
                e -> {
                    if ("Reveal".equals(reveal.getText())) {
                        val.setText(v.value());
                        reveal.setText("Hide");
                    } else {
                        val.setText(maskValue(v.value()));
                        reveal.setText("Reveal");
                    }
                });
        Button del = new Button("Delete");
        del.getStyleClass().add("chip-btn");
        del.setOnAction(
                e -> {
                    variables.remove(v);
                    save();
                    renderList();
                });
        actions.getChildren().addAll(reveal, del);
        row.getChildren().addAll(key, val, actions);
        return row;
    }

    private String maskValue(String val) {
        if (val == null || val.length() <= 4) return "****";
        return val.substring(0, 2) + "****" + val.substring(val.length() - 2);
    }

    private void addVariable() {
        Dialog<EnvVariable> dialog = new Dialog<>();
        dialog.setTitle("Add Environment Variable");
        ButtonType addType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addType, ButtonType.CANCEL);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(16));
        form.setPrefWidth(420);
        TextField keyF = new TextField();
        keyF.setPromptText("VARIABLE_NAME");
        TextField valF = new TextField();
        valF.setPromptText("value");
        form.addRow(0, new Label("Key *"), keyF);
        form.addRow(1, new Label("Value *"), valF);
        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(
                btn -> {
                    if (btn == addType && !keyF.getText().isBlank()) {
                        return new EnvVariable(keyF.getText().trim(), valF.getText().trim());
                    }
                    return null;
                });
        dialog.showAndWait()
                .ifPresent(
                        v -> {
                            variables.add(v);
                            save();
                            renderList();
                        });
    }

    private void save() {
        repository.saveEnvVariables(variables);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        variables = new ArrayList<>(repository.loadEnvVariables());
        renderList();
    }
}
