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
import ai.emailclaw.emailclaw.model.MarketProviderInfo;
import ai.emailclaw.emailclaw.model.MarketProviderPageInfo;
import ai.emailclaw.emailclaw.model.MarketResult;
import ai.emailclaw.emailclaw.model.MarketSearchError;
import ai.emailclaw.emailclaw.model.MarketSearchResponse;
import ai.emailclaw.emailclaw.service.MarketService;
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Skill Market page (Settings / Skill Market).
 *
 * <p>Aligned with Emailclaw 1.1.9: cross-platform search, Provider filtering, empty state and result card display.
 */
public class SkillMarketView implements ViewPane {
    private static final Logger LOGGER = Logger.getLogger(SkillMarketView.class.getName());
    private static final int PER_PROVIDER_LIMIT = 10;
    private static final String EMPTY_HINT = "Type a query above to search the market";

    private final MarketService marketService;
    private final BorderPane root = new BorderPane();
    private final TextField searchField = new TextField();
    private final Button searchBtn = new Button("Search");
    private final HBox providerChips = new HBox(6);
    private final VBox errorBox = new VBox(4);
    private final StackPane resultArea = new StackPane();
    private final FlowPane resultsGrid = new FlowPane();
    private final Label emptyLabel = new Label(EMPTY_HINT);
    private final Button loadMoreBtn = new Button("Load More");
    private final VBox contentBox = new VBox(12);

    private List<MarketProviderInfo> providers = List.of();
    private final Set<String> selectedProviderKeys = new HashSet<>();

    /** Next page number for each platform; {@code null} means no more. */
    private final Map<String, Integer> providerCursors = new LinkedHashMap<>();

    private final List<MarketResult> results = new ArrayList<>();
    private final List<MarketSearchError> errors = new ArrayList<>();
    private volatile boolean loading;
    private volatile boolean hasMore;
    private int searchGeneration;

    public SkillMarketView(MarketService marketService) {
        this.marketService = marketService;
        buildUi();
        loadProviders();
    }

    private void buildUi() {
        VBox page = new VBox(12);
        page.getStyleClass().add("page");
        page.getStyleClass().add("bg-white");
        page.setPadding(new Insets(18));

        Label breadcrumb = new Label("Settings / Skill Market");
        breadcrumb.getStyleClass().add("muted");

        searchField.setPromptText("Search skills across platforms");
        searchField.setOnAction(e -> runSearch(false));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchBtn.getStyleClass().add("primary-btn");
        searchBtn.setOnAction(e -> runSearch(false));
        HBox searchRow = new HBox(0, searchField, searchBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchField.getStyleClass().add("search-left");
        searchBtn.getStyleClass().add("search-right");

        providerChips.setAlignment(Pos.CENTER_LEFT);

        emptyLabel.getStyleClass().add("muted");
        emptyLabel.getStyleClass().add("text-14");
        Label emptyIcon = new Label("📦");
        emptyIcon.getStyleClass().add("text-42");
        VBox emptyState = new VBox(8, emptyIcon, emptyLabel);
        emptyState.setAlignment(Pos.CENTER);

        resultsGrid.setHgap(12);
        resultsGrid.setVgap(12);
        resultsGrid.setPrefWrapLength(1200);
        ScrollPane resultsScroll = new ScrollPane(resultsGrid);
        resultsScroll.setFitToWidth(true);
        resultsScroll.getStyleClass().add("scroll-transparent");
        VBox.setVgrow(resultsScroll, Priority.ALWAYS);

        loadMoreBtn.getStyleClass().add("chip-btn");
        loadMoreBtn.setVisible(false);
        loadMoreBtn.setManaged(false);
        loadMoreBtn.setOnAction(e -> runSearch(true));

        contentBox.getChildren().addAll(errorBox, resultsScroll, loadMoreBtn);
        resultArea.getChildren().addAll(emptyState, contentBox);
        StackPane.setAlignment(emptyState, Pos.CENTER);
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        page.getChildren().addAll(breadcrumb, searchRow, providerChips, resultArea);
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        root.setCenter(page);
    }

    private void loadProviders() {
        Thread.startVirtualThread(
                () -> {
                    try {
                        List<MarketProviderInfo> loaded = marketService.listProviders();
                        Platform.runLater(
                                () -> {
                                    providers = loaded;
                                    providerChips.getChildren().clear();
                                    selectedProviderKeys.clear();
                                    for (MarketProviderInfo info : loaded) {
                                        if (info.isAvailable()) {
                                            selectedProviderKeys.add(info.getKey());
                                        }
                                        providerChips.getChildren().add(buildProviderChip(info));
                                    }
                                });
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Failed to load market platform list", ex);
                    }
                });
    }

    private Node buildProviderChip(MarketProviderInfo info) {
        Label chip = new Label(info.getLabel());
        chip.getStyleClass().add("market-chip");
        if (!info.isAvailable()) {
            chip.getStyleClass().add("market-chip-disabled");
            if (info.getReason() != null && !info.getReason().isBlank()) {
                chip.setTooltip(new Tooltip(info.getReason()));
            }
        } else if (selectedProviderKeys.contains(info.getKey())) {
            chip.getStyleClass().add("market-chip-active");
        }
        chip.setOnMouseClicked(
                e -> {
                    if (!info.isAvailable()) {
                        return;
                    }
                    if (selectedProviderKeys.contains(info.getKey())) {
                        selectedProviderKeys.remove(info.getKey());
                        chip.getStyleClass().remove("market-chip-active");
                    } else {
                        selectedProviderKeys.add(info.getKey());
                        if (!chip.getStyleClass().contains("market-chip-active")) {
                            chip.getStyleClass().add("market-chip-active");
                        }
                    }
                    String query =
                            searchField.getText() == null ? "" : searchField.getText().trim();
                    if (!query.isBlank()) {
                        runSearch(false);
                    }
                });
        return chip;
    }

    private void runSearch(boolean append) {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (query.isBlank()) {
            results.clear();
            errors.clear();
            providerCursors.clear();
            hasMore = false;
            renderResults();
            return;
        }
        if (selectedProviderKeys.isEmpty()) {
            showTransientError("Please select at least one market platform");
            return;
        }
        if (loading) {
            return;
        }

        Map<String, Integer> pages = new LinkedHashMap<>();
        if (append) {
            for (String key : selectedProviderKeys) {
                Integer cursor = providerCursors.get(key);
                if (cursor != null) {
                    pages.put(key, cursor);
                }
            }
            if (pages.isEmpty()) {
                return;
            }
        } else {
            results.clear();
            errors.clear();
            providerCursors.clear();
            for (String key : selectedProviderKeys) {
                pages.put(key, 1);
                providerCursors.put(key, 1);
            }
        }

        loading = true;
        searchBtn.setDisable(true);
        int generation = ++searchGeneration;
        boolean appendMode = append;

        Thread.startVirtualThread(
                () -> {
                    try {
                        MarketSearchResponse resp =
                                marketService.search(query, pages, PER_PROVIDER_LIMIT, "zh-CN");
                        Platform.runLater(
                                () -> {
                                    if (generation != searchGeneration) {
                                        return;
                                    }
                                    if (appendMode) {
                                        results.addAll(resp.getResults());
                                    } else {
                                        results.clear();
                                        results.addAll(resp.getResults());
                                    }
                                    errors.clear();
                                    errors.addAll(resp.getErrors());
                                    updateCursors(resp.getByProvider());
                                    renderResults();
                                });
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Skill market search failed", ex);
                        Platform.runLater(() -> showTransientError(ex.getMessage()));
                    } finally {
                        Platform.runLater(
                                () -> {
                                    if (generation == searchGeneration) {
                                        loading = false;
                                        searchBtn.setDisable(false);
                                    }
                                });
                    }
                });
    }

    private void updateCursors(Map<String, MarketProviderPageInfo> byProvider) {
        for (String key : new ArrayList<>(providerCursors.keySet())) {
            MarketProviderPageInfo info = byProvider.get(key);
            if (info == null || !info.isHasMore()) {
                providerCursors.remove(key);
            } else {
                providerCursors.put(key, providerCursors.getOrDefault(key, 1) + 1);
            }
        }
        hasMore = !providerCursors.isEmpty();
    }

    private void renderResults() {
        errorBox.getChildren().clear();
        for (MarketSearchError err : errors) {
            String label = resolveProviderLabel(err.getProvider());
            Label row = new Label(label + ": " + err.getMessage());
            row.setStyle("-fx-text-fill: #c2410c; -fx-wrap-text: true;");
            row.setWrapText(true);
            errorBox.getChildren().add(row);
        }

        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        Node emptyState = resultArea.getChildren().getFirst();
        boolean showEmpty;
        if (query.isBlank()) {
            emptyLabel.setText(EMPTY_HINT);
            showEmpty = true;
        } else if (results.isEmpty()) {
            emptyLabel.setText("No results found");
            showEmpty = true;
        } else {
            showEmpty = false;
        }
        emptyState.setVisible(showEmpty);
        emptyState.setManaged(showEmpty);
        contentBox.setVisible(!showEmpty);
        contentBox.setManaged(!showEmpty);

        resultsGrid.getChildren().clear();
        for (MarketResult item : results) {
            resultsGrid.getChildren().add(buildResultCard(item));
        }

        loadMoreBtn.setVisible(hasMore && !results.isEmpty());
        loadMoreBtn.setManaged(hasMore && !results.isEmpty());
    }

    private VBox buildResultCard(MarketResult item) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPrefWidth(280);
        card.setMinHeight(160);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label sourceBadge = new Label(sourceLabel(item.getSource()));
        sourceBadge.getStyleClass().add("status-custom");
        top.getChildren().add(sourceBadge);

        Label name = new Label(item.getName());
        name.getStyleClass().add("fw-600-15");
        name.setWrapText(true);

        Label desc =
                new Label(
                        item.getDescription() == null || item.getDescription().isBlank()
                                ? "No description"
                                : item.getDescription());
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        desc.setMaxHeight(60);

        String meta = "";
        if (item.getVersion() != null && !item.getVersion().isBlank()) {
            meta = "v" + item.getVersion();
        }
        if (item.getAuthor() != null && !item.getAuthor().isBlank()) {
            meta = meta.isBlank() ? item.getAuthor() : meta + " · " + item.getAuthor();
        }
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("muted");

        HBox actions = new HBox(8);
        Button openBtn = new Button("Open");
        openBtn.getStyleClass().add("chip-btn");
        openBtn.setOnAction(e -> openInBrowser(item.getSourceUrl()));
        actions.getChildren().add(openBtn);

        card.getChildren().addAll(top, name, desc, metaLabel, actions);
        return card;
    }

    private String sourceLabel(String source) {
        if (source == null) {
            return "Unknown";
        }
        return switch (source) {
            case "clawhub" -> "ClawHub";
            case "modelscope" -> "ModelScope";
            case "aliyun" -> "Aliyun";
            default -> source;
        };
    }

    private String resolveProviderLabel(String key) {
        for (MarketProviderInfo info : providers) {
            if (key != null && key.equals(info.getKey())) {
                return info.getLabel();
            }
        }
        return key == null ? "" : key;
    }

    private void openInBrowser(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Cannot open browser: " + url, ex);
        }
    }

    private void showTransientError(String message) {
        Label row = new Label(message == null ? "Unknown error" : message);
        row.getStyleClass().add("text-orange-700");
        errorBox.getChildren().add(row);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        // Skill Market is a global setting, independent of Agent.
    }

    @Override
    public void refresh() {
        loadProviders();
    }
}
