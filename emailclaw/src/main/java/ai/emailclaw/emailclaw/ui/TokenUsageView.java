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

import ai.emailclaw.emailclaw.model.TokenUsageRecord;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class TokenUsageView implements ViewPane {
    /**
     * Token usage view.
     */
    private static final Logger LOGGER = Logger.getLogger(TokenUsageView.class.getName());

    private final AppContext repository;
    private final BorderPane root = new BorderPane();
    private final DatePicker from = new DatePicker(LocalDate.now().minusDays(30));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final Label result = new Label();
    private final LineChart<String, Number> byModelChart =
            new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final LineChart<String, Number> byTypeChart =
            new LineChart<>(new CategoryAxis(), new NumberAxis());

    public TokenUsageView(AppContext repository) {
        this.repository = repository;
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(10);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        Label title = new Label("Settings  /  Token Usage");
        title.getStyleClass().add("page-title");
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("primary-btn");
        refresh.setOnAction(e -> refresh());
        HBox filters = new HBox(8, from, to, refresh);
        result.getStyleClass().add("muted");
        byModelChart.setTitle("Token Trend by Model");
        byModelChart.setCreateSymbols(false);
        byTypeChart.setTitle("Token Trend by Token Type");
        byTypeChart.setCreateSymbols(false);
        page.getChildren().addAll(title, filters, result, byModelChart, byTypeChart);
        root.setCenter(page);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        LOGGER.fine("Refresh token usage statistics");
        LocalDate start = from.getValue();
        LocalDate end = to.getValue();
        List<TokenUsageRecord> records =
                repository.loadTokenUsage().stream()
                        .filter(
                                r -> {
                                    LocalDate d = LocalDate.parse(r.date());
                                    return (d.isEqual(start) || d.isAfter(start))
                                            && (d.isEqual(end) || d.isBefore(end));
                                })
                        .toList();
        if (records.isEmpty()) {
            result.setText("No token usage data in the selected period");
            byModelChart.getData().clear();
            byTypeChart.getData().clear();
            return;
        }
        long prompt = records.stream().mapToLong(r -> r.promptTokens()).sum();
        long completion = records.stream().mapToLong(r -> r.completionTokens()).sum();
        result.setText(
                "Records: "
                        + records.size()
                        + " | Prompt Tokens: "
                        + prompt
                        + " | Completion Tokens: "
                        + completion
                        + " | Total: "
                        + (prompt + completion));

        Map<String, Map<String, Long>> dailyModel = new LinkedHashMap<>();
        Map<String, Long> dailyPrompt = new LinkedHashMap<>();
        Map<String, Long> dailyCompletion = new LinkedHashMap<>();
        for (TokenUsageRecord r : records) {
            dailyModel.computeIfAbsent(r.modelId(), ignored -> new LinkedHashMap<>());
            Map<String, Long> one = dailyModel.get(r.modelId());
            one.put(
                    r.date(),
                    one.getOrDefault(r.date(), 0L) + r.promptTokens() + r.completionTokens());
            dailyPrompt.put(r.date(), dailyPrompt.getOrDefault(r.date(), 0L) + r.promptTokens());
            dailyCompletion.put(
                    r.date(), dailyCompletion.getOrDefault(r.date(), 0L) + r.completionTokens());
        }

        byModelChart.getData().clear();
        for (Map.Entry<String, Map<String, Long>> e : dailyModel.entrySet()) {
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(e.getKey() == null || e.getKey().isBlank() ? "(unknown)" : e.getKey());
            e.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(x -> s.getData().add(new XYChart.Data<>(x.getKey(), x.getValue())));
            byModelChart.getData().add(s);
        }

        byTypeChart.getData().clear();
        XYChart.Series<String, Number> promptSeries = new XYChart.Series<>();
        promptSeries.setName("prompt_tokens");
        dailyPrompt.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        x ->
                                promptSeries
                                        .getData()
                                        .add(new XYChart.Data<>(x.getKey(), x.getValue())));
        XYChart.Series<String, Number> completionSeries = new XYChart.Series<>();
        completionSeries.setName("completion_tokens");
        dailyCompletion.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        x ->
                                completionSeries
                                        .getData()
                                        .add(new XYChart.Data<>(x.getKey(), x.getValue())));
        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("total_tokens");
        for (Map.Entry<String, Long> p : dailyPrompt.entrySet()) {
            long c = dailyCompletion.getOrDefault(p.getKey(), 0L);
            totalSeries.getData().add(new XYChart.Data<>(p.getKey(), p.getValue() + c));
        }
        byTypeChart.getData().addAll(promptSeries, completionSeries, totalSeries);
    }
}
