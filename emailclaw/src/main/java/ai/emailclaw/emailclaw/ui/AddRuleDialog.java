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

import ai.emailclaw.emailclaw.model.SecurityRule;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Dialog for adding/editing custom security rules.
 *
 * <p>Provides fields to define: Rule ID, Category, Severity, Description, etc.
 * Users can create new rules or edit existing rules.
 */
public class AddRuleDialog extends Dialog<SecurityRule> {

    private final TextField ruleIdField = new TextField();

    private final ComboBox<String> categoryCombo = new ComboBox<>();

    private final ComboBox<String> severityCombo = new ComboBox<>();

    private final TextArea descriptionArea = new TextArea();

    private SecurityRule editingRule;

    /**
     * Create add rule dialog.
     *
     * @param ownerWindow Parent window
     */
    public AddRuleDialog(Window ownerWindow) {
        initOwner(ownerWindow);
        setTitle("Add Custom Rule");
        setHeaderText("Create a new custom detection rule");
        buildUI();
        setupCloseHandler();
    }

    private void setupCloseHandler() {
        // Ensure the top right X button and cancel button are handled consistently
        getDialogPane()
                .getScene()
                .getWindow()
                .setOnCloseRequest(
                        e -> {
                            setResult(null);
                        });
    }

    private void buildUI() {
        DialogPane dialogPane = getDialogPane();
        dialogPane.setPadding(new Insets(16));
        // Main content area
        VBox content = new VBox(12);
        content.setPadding(new Insets(0));
        // Rule ID field
        Label ruleIdLabel = new Label("Rule ID *");
        ruleIdLabel.getStyleClass().add("fw-600");
        ruleIdField.setPromptText("e.g., TOOL_CMD_CUSTOM_RULE");
        VBox.setVgrow(ruleIdField, Priority.NEVER);
        // Category selection box
        Label categoryLabel = new Label("Category *");
        categoryLabel.getStyleClass().add("fw-600");
        categoryCombo
                .getItems()
                .addAll(
                        "command_injection",
                        "resource_abuse",
                        "code_execution",
                        "network_abuse",
                        "sensitive_file_access",
                        "privilege_escalation");
        categoryCombo.setValue("command_injection");
        // Severity selection box
        Label severityLabel = new Label("Severity *");
        severityLabel.getStyleClass().add("fw-600");
        severityCombo.getItems().addAll("CRITICAL", "HIGH", "MEDIUM", "LOW");
        severityCombo.setValue("HIGH");
        // Description text area
        Label descLabel = new Label("Description");
        descLabel.getStyleClass().add("fw-600");
        descriptionArea.setPromptText("Describe what this rule detects...");
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(6);
        descriptionArea.setStyle("-fx-control-inner-background: #ffffff;");
        // Create grid layout
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        // Row 1: Rule ID
        grid.addRow(0, ruleIdLabel, ruleIdField);
        GridPane.setHgrow(ruleIdField, Priority.ALWAYS);
        // Row 2: Category and Severity
        GridPane.setConstraints(categoryLabel, 0, 1);
        GridPane.setConstraints(categoryCombo, 1, 1);
        GridPane.setConstraints(severityLabel, 2, 1);
        GridPane.setConstraints(severityCombo, 3, 1);
        GridPane.setHgrow(categoryCombo, Priority.ALWAYS);
        GridPane.setHgrow(severityCombo, Priority.ALWAYS);
        grid.getChildren().addAll(categoryLabel, categoryCombo, severityLabel, severityCombo);
        // Add to VBox
        content.getChildren().addAll(grid, descLabel, descriptionArea);
        VBox.setVgrow(descriptionArea, Priority.ALWAYS);
        // Create scroll pane
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("bg-white");
        dialogPane.setContent(scrollPane);
        // Register standard button types
        ButtonType confirmType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(confirmType, cancelType);
        // Set result converter to correctly handle confirm and cancel buttons
        setResultConverter(
                dialogButton -> {
                    if (dialogButton == confirmType) {
                        confirmAction();
                        return editingRule;
                    }
                    // Cancel button or X button both return null
                    return null;
                });
    }

    /**
     * Validate and confirm rule addition. This method is called in setResultConverter.
     * If validation fails, show error prompt and return false.
     *
     * @return Whether validation is successful
     */
    private boolean confirmAction() {
        String ruleId = ruleIdField.getText().trim();
        if (ruleId.isBlank()) {
            showError("Validation Error", "Rule ID cannot be empty.");
            return false;
        }
        String category = categoryCombo.getValue();
        String severity = severityCombo.getValue();
        String description = descriptionArea.getText().trim();
        // Create new rule or update existing rule
        if (editingRule != null) {
            editingRule =
                    new SecurityRule(
                            ruleId,
                            category,
                            description,
                            severity,
                            false,
                            editingRule.enabled(),
                            editingRule.autoDeny(),
                            editingRule.viewDetails());
        } else {
            editingRule = new SecurityRule(ruleId, category, severity, description, false, true);
        }
        return true;
    }

    /**
     * Show error message.
     *
     * @param title Error title
     * @param message Error message
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Set dialog to edit mode for editing existing rules.
     *
     * @param rule Rule to edit
     */
    public void setEditingRule(SecurityRule rule) {
        this.editingRule = rule;
        ruleIdField.setText(rule.ruleId());
        categoryCombo.setValue(rule.category());
        severityCombo.setValue(rule.severity());
        descriptionArea.setText(rule.description());
        setHeaderText("Edit Custom Rule");
    }
}
