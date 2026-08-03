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

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Custom component: Full-featured Cron expression visual editor; Supports: All(*), Interval(/), Range(-), Specific/List(,); Supports bidirectional switching between manual input and visual GUI, plug-and-play.
 * Encapsulates internal state transitions and UI logic, external callers only need to call getText() to get the Cron string
 */
public class CronExpressionEditor extends VBox {
    private final TextField cronTextField;
    private final ToggleButton toggleGuiBtn;
    private final GridPane guiPanel;

    private final CronSegment minSeg;
    private final CronSegment hourSeg;
    private final CronSegment domSeg;
    private final CronSegment monthSeg;
    private final CronSegment dowSeg;

    public CronExpressionEditor() {
        this.setSpacing(10);

        // 1. Bottom input control bar
        cronTextField = new TextField("* * * * *");
        cronTextField.setPromptText("Example: 0 12 * * *");
        cronTextField.setStyle(
                "-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 14px;");
        HBox.setHgrow(cronTextField, Priority.ALWAYS);

        toggleGuiBtn = new ToggleButton();
        toggleGuiBtn.setCursor(javafx.scene.Cursor.HAND);
        toggleGuiBtn
                .textProperty()
                .bind(
                        Bindings.when(toggleGuiBtn.selectedProperty())
                                .then("⬇ Confirm and return to text")
                                .otherwise("⚙ Expand advanced wizard"));

        var bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.getChildren().addAll(cronTextField, toggleGuiBtn);

        // 2. Top visual build panel
        guiPanel = new GridPane();
        guiPanel.setHgap(15);
        guiPanel.setVgap(15);
        guiPanel.setPadding(new Insets(15));
        guiPanel.setStyle(
                "-fx-border-color: #d1d5db; -fx-border-radius: 8; -fx-background-color: #f8fafc;"
                        + " -fx-background-radius: 8;");

        guiPanel.visibleProperty().bind(toggleGuiBtn.selectedProperty());
        guiPanel.managedProperty().bind(toggleGuiBtn.selectedProperty());

        Runnable updateTextAction = this::generateCronFromGui;

        // Initialize paragraph controllers for each time dimension
        minSeg = new CronSegment("Minute (0-59)", "m", 0, 59, updateTextAction);
        hourSeg = new CronSegment("Hour (0-23)", "h", 0, 23, updateTextAction);
        domSeg = new CronSegment("Day (1-31)", "d", 1, 31, updateTextAction);
        monthSeg = new CronSegment("Month (1-12)", "m", 1, 12, updateTextAction);
        dowSeg = new CronSegment("Week (1-7)", "w", 1, 7, updateTextAction);

        // Add to grid layout
        addSegmentToGrid(guiPanel, 0, minSeg);
        addSegmentToGrid(guiPanel, 1, hourSeg);
        addSegmentToGrid(guiPanel, 2, domSeg);
        addSegmentToGrid(guiPanel, 3, monthSeg);
        addSegmentToGrid(guiPanel, 4, dowSeg);

        // 3. Mode switching logic
        toggleGuiBtn
                .selectedProperty()
                .addListener(
                        (obs, oldVal, isGuiMode) -> {
                            if (isGuiMode) {
                                syncTextToGui();
                                cronTextField.setEditable(false);
                                cronTextField.setStyle(
                                        "-fx-font-family: 'Consolas', monospace; -fx-font-size:"
                                            + " 14px; -fx-background-color: #e2e8f0; -fx-text-fill:"
                                            + " #475569;");
                            } else {
                                cronTextField.setEditable(true);
                                cronTextField.setStyle(
                                        "-fx-font-family: 'Consolas', monospace; -fx-font-size:"
                                                + " 14px;");
                            }
                        });

        this.getChildren().addAll(guiPanel, bottomBar);
    }

    public String getCronExpression() {
        return cronTextField.getText().trim();
    }

    /**
     * Set the externally passed Cron expression, and sync to the GUI
     * @param cron The passed expression
     */
    public void setCronExpression(String cron) {
        if (cron != null && !cron.isBlank()) {
            cronTextField.setText(cron);
            if (toggleGuiBtn.isSelected()) {
                syncTextToGui();
            }
        }
    }

    private void addSegmentToGrid(GridPane grid, int row, CronSegment segment) {
        grid.add(segment.getTitleLabel(), 0, row);
        grid.add(segment.getModeCombo(), 1, row);
        grid.add(segment.getInputPane(), 2, row);
    }

    private void generateCronFromGui() {
        if (!toggleGuiBtn.isSelected()) return;
        var cron =
                String.format(
                        "%s %s %s %s %s",
                        minSeg.getCronPart(),
                        hourSeg.getCronPart(),
                        domSeg.getCronPart(),
                        monthSeg.getCronPart(),
                        dowSeg.getCronPart());
        cronTextField.setText(cron);
    }

    private void syncTextToGui() {
        var parts = cronTextField.getText().trim().split("\\s+");
        if (parts.length >= 5) {
            minSeg.setCronPart(parts[0]);
            hourSeg.setCronPart(parts[1]);
            domSeg.setCronPart(parts[2]);
            monthSeg.setCronPart(parts[3]);
            String dow = parts[4];
            if ("0".equals(dow)) dow = "7";
            dowSeg.setCronPart(dow);
        }
    }

    /**
     * Controller for a single time dimension, supporting switching between four modes and smart parsing
     */
    private static class CronSegment {
        private final Label titleLabel;
        private final ComboBox<String> modeCombo;
        private final StackPane inputPane;

        // Input components for various modes
        private final Spinner<Integer> exactSpinner;
        private final Spinner<Integer> intervalSpinner;
        private final Spinner<Integer> rangeStartSpinner;
        private final Spinner<Integer> rangeEndSpinner;
        private final TextField listField;

        public CronSegment(String title, String unitName, int min, int max, Runnable onChange) {
            this.titleLabel = new Label(title);
            this.titleLabel.setPrefWidth(90);

            // Mode selector
            this.modeCombo = new ComboBox<>();
            this.modeCombo
                    .getItems()
                    .addAll(
                            "All (*)",
                            "Specific (Value)",
                            "Interval (/)",
                            "Range (-)",
                            "List/Advanced");
            this.modeCombo.setPrefWidth(120);

            // --- Construct dynamic input area ---
            // 1. Specific mode input area
            this.exactSpinner = new Spinner<>(min, max, min); // fallback default is min
            this.exactSpinner.setPrefWidth(120);
            this.exactSpinner.setEditable(true);
            var exactBox = new HBox(5, new Label("is"), exactSpinner, new Label(unitName));
            exactBox.setAlignment(Pos.CENTER_LEFT);

            // 2. Interval mode input area (e.g.: Every 5 minutes)
            this.intervalSpinner = new Spinner<>(1, max, 1);
            this.intervalSpinner.setPrefWidth(70);
            this.intervalSpinner.setEditable(true);
            var intervalBox = new HBox(5, new Label("Every"), intervalSpinner, new Label(unitName));
            intervalBox.setAlignment(Pos.CENTER_LEFT);

            // 3. Range mode input area (e.g.: From 1 to 5)
            this.rangeStartSpinner = new Spinner<>(min, max, min);
            this.rangeEndSpinner = new Spinner<>(min, max, max);
            this.rangeStartSpinner.setPrefWidth(70);
            this.rangeEndSpinner.setPrefWidth(70);
            this.rangeStartSpinner.setEditable(true);
            this.rangeEndSpinner.setEditable(true);
            var rangeBox =
                    new HBox(
                            5,
                            new Label("From"),
                            rangeStartSpinner,
                            new Label("To"),
                            rangeEndSpinner);
            rangeBox.setAlignment(Pos.CENTER_LEFT);

            // 4. List/complex mode input area (e.g.: 1,3,5-7)
            this.listField = new TextField();
            this.listField.setPromptText(
                    "Example: " + min + "," + (min + 2) + " or " + min + "-" + (min + 3) + "/2");
            this.listField.setPrefWidth(220);

            // Put all input areas into StackPane
            this.inputPane = new StackPane(exactBox, intervalBox, rangeBox, listField);
            this.inputPane.setAlignment(Pos.CENTER_LEFT);

            // --- Bind visibility logic ---
            exactBox.visibleProperty()
                    .bind(modeCombo.getSelectionModel().selectedIndexProperty().isEqualTo(1));
            intervalBox
                    .visibleProperty()
                    .bind(modeCombo.getSelectionModel().selectedIndexProperty().isEqualTo(2));
            rangeBox.visibleProperty()
                    .bind(modeCombo.getSelectionModel().selectedIndexProperty().isEqualTo(3));
            listField
                    .visibleProperty()
                    .bind(modeCombo.getSelectionModel().selectedIndexProperty().isEqualTo(4));

            // --- Event listening ---
            // Regenerate Cron when mode changes
            this.modeCombo
                    .getSelectionModel()
                    .selectedIndexProperty()
                    .addListener((o, old, nw) -> onChange.run());

            // Regenerate Cron when value changes
            this.exactSpinner.valueProperty().addListener((o, old, nw) -> onChange.run());
            this.intervalSpinner.valueProperty().addListener((o, old, nw) -> onChange.run());
            this.rangeStartSpinner.valueProperty().addListener((o, old, nw) -> onChange.run());
            this.rangeEndSpinner.valueProperty().addListener((o, old, nw) -> onChange.run());
            this.listField.textProperty().addListener((o, old, nw) -> onChange.run());

            // Fix issue where Spinner doesn't take effect before pressing enter during manual input
            addSpinnerEditorListener(this.exactSpinner, onChange);
            addSpinnerEditorListener(this.intervalSpinner, onChange);
            addSpinnerEditorListener(this.rangeStartSpinner, onChange);
            addSpinnerEditorListener(this.rangeEndSpinner, onChange);

            // Default to select all
            this.modeCombo.getSelectionModel().select(0);
        }

        private void addSpinnerEditorListener(Spinner<Integer> spinner, Runnable onChange) {
            spinner.getEditor()
                    .textProperty()
                    .addListener(
                            (o, old, nw) -> {
                                try {
                                    Integer.parseInt(
                                            nw); // Only trigger update when a valid number is input
                                    onChange.run();
                                } catch (NumberFormatException ignored) {
                                }
                            });
        }

        public Label getTitleLabel() {
            return titleLabel;
        }

        public ComboBox<String> getModeCombo() {
            return modeCombo;
        }

        public StackPane getInputPane() {
            return inputPane;
        }

        /**
         * Generate the Cron string for this part based on the current UI state
         */
        public String getCronPart() {
            int mode = modeCombo.getSelectionModel().getSelectedIndex();
            return switch (mode) {
                case 1 -> String.valueOf(exactSpinner.getValue());
                case 2 -> "*/" + intervalSpinner.getValue();
                case 3 -> rangeStartSpinner.getValue() + "-" + rangeEndSpinner.getValue();
                case 4 -> {
                    String text = listField.getText().trim();
                    yield text.isEmpty() ? "*" : text;
                }
                default -> "*";
            };
        }

        /**
         * Smart reverse parse Cron string and restore corresponding UI state
         */
        public void setCronPart(String part) {
            if (part == null || part.isBlank() || part.equals("*")) {
                modeCombo.getSelectionModel().select(0); // All (*)
                return;
            }

            // Attempt to parse specific single value (pure number)
            if (part.matches("\\d+")) {
                try {
                    int val = Integer.parseInt(part);
                    modeCombo.getSelectionModel().select(1);
                    exactSpinner.getValueFactory().setValue(val);
                    return;
                } catch (Exception ignored) {
                }
            }

            // Attempt to parse interval (/)
            if (part.contains("/")) {
                try {
                    String[] split = part.split("/");
                    // Handle "*/5" or "0/5" cases
                    if (split.length == 2) {
                        int interval = Integer.parseInt(split[1]);
                        modeCombo.getSelectionModel().select(2);
                        intervalSpinner.getValueFactory().setValue(interval);
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
            // Attempt to parse range (-) and cannot contain comma
            else if (part.contains("-") && !part.contains(",")) {
                try {
                    String[] split = part.split("-");
                    if (split.length == 2) {
                        int start = Integer.parseInt(split[0]);
                        int end = Integer.parseInt(split[1]);
                        modeCombo.getSelectionModel().select(3);
                        rangeStartSpinner.getValueFactory().setValue(start);
                        rangeEndSpinner.getValueFactory().setValue(end);
                        return;
                    }
                } catch (Exception ignored) {
                }
            }

            // If none of the above rules match (like 1,3,5 or 1,5-10/2 complex syntax)
            // Downgrade to putting directly into "Specific/List" input box, maximizing retention of
            // user's original input
            modeCombo.getSelectionModel().select(4);
            listField.setText(part);
        }
    }
}
