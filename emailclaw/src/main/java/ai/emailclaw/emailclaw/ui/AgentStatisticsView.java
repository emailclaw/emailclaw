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

import ai.emailclaw.emailclaw.model.AgentStatRecord;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AgentStatisticsView implements ViewPane {
    /**
     * Agent statistics view.
     */
    private static final Logger LOGGER = Logger.getLogger(AgentStatisticsView.class.getName());

    private final AppContext repository;
    private final BorderPane root = new BorderPane();
    private final DatePicker from = new DatePicker(LocalDate.now().minusDays(7));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final Label result = new Label();

    public AgentStatisticsView(AppContext repository) {
        this.repository = repository;
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(10);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        Label title = new Label("Settings  /  Agent Statistics");
        title.getStyleClass().add("page-title");
        HBox filters = new HBox(8, from, to);
        from.valueProperty().addListener((obs, o, n) -> refresh());
        to.valueProperty().addListener((obs, o, n) -> refresh());
        result.getStyleClass().add("muted");
        page.getChildren().addAll(title, filters, result);
        root.setCenter(page);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        LOGGER.fine("Refreshing Agent statistics data");
        LocalDate start = from.getValue();
        LocalDate end = to.getValue();
        List<AgentStatRecord> records =
                repository.loadAgentStats().stream()
                        .filter(
                                r -> {
                                    LocalDate d = LocalDate.parse(r.date());
                                    return (d.isEqual(start) || d.isAfter(start))
                                            && (d.isEqual(end) || d.isBefore(end));
                                })
                        .toList();
        if (records.isEmpty()) {
            result.setText("No statistics data in the selected period");
            return;
        }
        long messages = records.stream().mapToLong(r -> r.messageCount()).sum();
        long toolCalls = records.stream().mapToLong(r -> r.toolCallCount()).sum();
        result.setText(
                "Messages: "
                        + messages
                        + " | Tool Calls: "
                        + toolCalls
                        + " | Records: "
                        + records.size());
    }
}
