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
import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ProviderCatalog;
import ai.emailclaw.emailclaw.service.ProviderService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.StringConverter;
import tools.jackson.databind.ObjectMapper;

/**
 * Model management view (aligns with Emailclaw 2.0 Models page).
 *
 * <p>Top Tab switches between Cloud Providers / Local & Custom.
 * Inside the Cloud Tab, it is divided into Configured and Available Providers areas.
 * Multiple variants of the same brand are switched using Segmented Control via ProviderGroupCard.
 */
public class ModelsView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(ModelsView.class.getName());

    private static final List<String> CATALOG_ORDER =
            ProviderCatalog.builtins().stream().map(p -> p.getId()).toList();

    /**
     * Provider letter icon color mapping (aligns with Emailclaw providerLetterIcon.tsx).
     */
    private static final Map<String, String> LETTER_COLORS =
            Map.ofEntries(
                    Map.entry("openai", "#10a37f"),
                    Map.entry("anthropic", "#d97757"),
                    Map.entry("google", "#4285f4"),
                    Map.entry("gemini", "#4285f4"),
                    Map.entry("deepseek", "#4d6bfe"),
                    Map.entry("aliyun", "#ff6a00"),
                    Map.entry("dashscope", "#ff6a00"),
                    Map.entry("kimi", "#000000"),
                    Map.entry("minimax", "#6366f1"),
                    Map.entry("siliconflow", "#6366f1"),
                    Map.entry("zhipu", "#3366ff"),
                    Map.entry("volcengine", "#3b82f6"),
                    Map.entry("volcengine-cn", "#3b82f6"),
                    Map.entry("openrouter", "#e11d48"),
                    Map.entry("ollama", "#ffffff"),
                    Map.entry("lmstudio", "#8b5cf6"),
                    Map.entry("github-models", "#333333"),
                    Map.entry("modelscope", "#ff6a00"),
                    Map.entry("opencode", "#3b82f6"));

    private static final String[] PALETTE = {
        "#ef4444", "#f97316", "#eab308", "#22c55e", "#06b6d4", "#3b82f6", "#8b5cf6", "#ec4899",
        "#f43f5e", "#14b8a6"
    };

    private final ProviderService providerService;

    private final AgentService agentService;

    private final AgentInfo currentAgent;

    private final Runnable onDefaultModelSaved;

    private final BorderPane root = new BorderPane();

    private final VBox contentArea = new VBox(0);

    private final TextField search = new TextField();

    private final ComboBox<ProviderInfo> defaultProvider = new ComboBox<>();

    private final ComboBox<ModelInfo> defaultModel = new ComboBox<>();

    /**
     * Current Tab: "cloud" or "local".
     */
    private String activeTab = "cloud";

    private ToggleButton cloudTab;

    private ToggleButton localTab;

    public ModelsView(
            ProviderService providerService,
            AgentService agentService,
            AgentInfo currentAgent,
            Runnable onDefaultModelSaved) {
        LOGGER.info("Method execution: ModelsView constructor");
        this.providerService = providerService;
        this.agentService = agentService;
        this.currentAgent = currentAgent;
        this.onDefaultModelSaved = onDefaultModelSaved;
        this.providerService.addReloadListener(() -> Platform.runLater(this::refresh));
        buildUi();
        refresh();
    }

    // ================================================================================
    // UI Building
    // ================================================================================
    private void buildUi() {
        LOGGER.info("Method execution: buildUi");
        VBox page = new VBox(0);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        // ---- Title row ----
        Label title = new Label("Settings / Models");
        title.getStyleClass().add("page-title");
        // ---- Default LLM area ----
        VBox defaultBlock = buildDefaultLlmBlock();
        // ---- Title + Search + Refresh + Add Provider ----
        HBox headerRow = buildHeaderRow();
        // ---- Tab navigation ----
        HBox tabBar = buildTabBar();
        // ---- Content area (including ScrollPane) ----
        contentArea.getStyleClass().add("card-lite");
        contentArea.setPadding(new Insets(0));
        ScrollPane scroll = new ScrollPane(contentArea);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        page.getChildren().addAll(title, defaultBlock, headerRow, tabBar, scroll);
        root.setCenter(page);
    }

    private VBox buildDefaultLlmBlock() {
        LOGGER.info("Method execution: buildDefaultLlmBlock");
        VBox block = new VBox(8);
        block.getStyleClass().add("card-lite");
        block.setPadding(new Insets(12));
        Label d = new Label("Default LLM");
        d.getStyleClass().add("fw-bold-14");
        HBox line = new HBox(8);
        line.setAlignment(Pos.CENTER_LEFT);
        defaultProvider.setPromptText("Select provider (must be authorized)");
        defaultModel.setPromptText("Please add a model first");
        defaultProvider.setCellFactory(v -> new ProviderCell());
        defaultProvider.setButtonCell(new ProviderCell());
        defaultModel.setConverter(
                new StringConverter<>() {

                    @Override
                    public String toString(ModelInfo m) {
                        LOGGER.info("Method execution: toString");
                        return m == null ? "" : m.getName();
                    }

                    @Override
                    public ModelInfo fromString(String s) {
                        LOGGER.info("Method execution: fromString");
                        return null;
                    }
                });
        defaultProvider
                .valueProperty()
                .addListener(
                        (obs, o, n) -> {
                            defaultModel.getItems().setAll(n == null ? List.of() : n.allModels());
                            if (n != null && currentAgent.getModelId() != null) {
                                n.allModels().stream()
                                        .filter(m -> m.getId().equals(currentAgent.getModelId()))
                                        .findFirst()
                                        .ifPresent(m -> defaultModel.getSelectionModel().select(m));
                            }
                        });
        Button saveDefault = new Button("Save");
        saveDefault.getStyleClass().add("primary-btn");
        saveDefault.setOnAction(
                e -> {
                    if (defaultProvider.getValue() != null && defaultModel.getValue() != null) {
                        currentAgent.setProviderId(defaultProvider.getValue().getId());
                        currentAgent.setModelId(defaultModel.getValue().getId());
                        agentService.save();
                        if (onDefaultModelSaved != null) onDefaultModelSaved.run();
                        info("Default model updated.");
                    } else {
                        error("Please select both provider and model.");
                    }
                });
        line.getChildren().addAll(defaultProvider, defaultModel, saveDefault);
        HBox.setHgrow(defaultProvider, Priority.ALWAYS);
        HBox.setHgrow(defaultModel, Priority.ALWAYS);
        Label hint = new Label("Get the global default LLM model from an authorized provider.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        block.getChildren().addAll(d, line, hint);
        return block;
    }

    private HBox buildHeaderRow() {
        LOGGER.info("Method execution: buildHeaderRow");
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 0, 6, 0));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        search.setPromptText("Search providers...");
        search.setPrefWidth(180);
        search.textProperty().addListener((obs, o, n) -> renderTabContent());
        Button refreshBtn = new Button("\u27F3");
        refreshBtn.setTooltip(new Tooltip("Refresh"));
        refreshBtn.setOnAction(e -> refresh());
        Button addProvider = new Button("+ Add Provider");
        addProvider.getStyleClass().add("primary-btn");
        addProvider.setOnAction(e -> showAddProviderDialog());
        row.getChildren().addAll(spacer, search, refreshBtn, addProvider);
        return row;
    }

    private HBox buildTabBar() {
        LOGGER.info("Method execution: buildTabBar");
        HBox bar = new HBox(0);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 0, 0, 0));
        ToggleGroup group = new ToggleGroup();
        cloudTab = createTab("Cloud Providers", "cloud", group);
        localTab = createTab("Local & Custom", "local", group);
        group.selectToggle(cloudTab);
        bar.getChildren().addAll(cloudTab, localTab);
        return bar;
    }

    private ToggleButton createTab(String label, String key, ToggleGroup group) {
        LOGGER.info("Method execution: createTab");
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.setPrefHeight(32);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.setStyle("-fx-background-radius: 6 6 0 0; -fx-padding: 6 16;");
        btn.selectedProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (n) {
                                activeTab = key;
                                renderTabContent();
                            }
                        });
        return btn;
    }

    // ================================================================================
    // Tab Content Rendering
    // ================================================================================
    private void renderTabContent() {
        LOGGER.info("Method execution: renderTabContent");
        contentArea.getChildren().clear();
        List<ProviderInfo> all = providerService.listProviders();
        String keyword = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        if ("cloud".equals(activeTab)) {
            renderCloudTab(all, keyword);
        } else {
            renderLocalTab(all, keyword);
        }
    }

    private void renderCloudTab(List<ProviderInfo> all, String keyword) {
        LOGGER.info("Method execution: renderCloudTab");
        List<ProviderInfo> cloud =
                all.stream()
                        .filter(p -> !p.isLocal() && !p.isCustom())
                        .filter(p -> matchesSearch(p, keyword))
                        .toList();
        // Grouping: configured vs available
        Map<String, List<ProviderInfo>> groups = groupByConfigured(cloud);
        List<ProviderInfo> configured = groups.get("configured");
        List<ProviderInfo> available = groups.get("available");
        // Configured area
        VBox configuredSection = new VBox(8);
        configuredSection.setPadding(new Insets(12, 14, 8, 14));
        Label confTitle = new Label("Configured");
        confTitle.getStyleClass().add("fw-bold-15");
        configuredSection.getChildren().add(confTitle);
        if (configured.isEmpty()) {
            Label empty =
                    new Label(
                            "No providers configured yet. Configure a provider below to get"
                                    + " started.");
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            configuredSection.getChildren().add(empty);
        } else {
            // Group by brand
            List<ProviderGroup> groups2 = groupProviders(configured);
            for (ProviderGroup pg : groups2) {
                if (pg.variants.size() > 1) {
                    configuredSection.getChildren().add(buildGroupCard(pg));
                } else {
                    configuredSection.getChildren().add(buildRemoteCard(pg.variants.get(0)));
                }
            }
        }
        // Available Providers area (grouped by brand, one brand per row)
        VBox availableSection = new VBox(8);
        availableSection.setPadding(new Insets(8, 14, 12, 14));
        Label availTitle = new Label("Available Providers");
        availTitle.getStyleClass().add("fw-bold-15");
        availableSection.getChildren().add(availTitle);
        if (available.isEmpty()) {
            Label empty = new Label("All providers are configured.");
            empty.getStyleClass().add("muted");
            availableSection.getChildren().add(empty);
        } else {
            List<ProviderGroup> availGroups = groupProviders(available);
            VBox availGrid = new VBox(8);
            for (ProviderGroup pg : availGroups) {
                availGrid.getChildren().add(buildAvailableGroupRow(pg));
            }
            availableSection.getChildren().add(availGrid);
        }
        Separator sep = new Separator();
        contentArea.getChildren().addAll(configuredSection, sep, availableSection);
    }

    private void renderLocalTab(List<ProviderInfo> all, String keyword) {
        LOGGER.info("Method execution: renderLocalTab");
        List<ProviderInfo> local =
                all.stream()
                        .filter(p -> p.isLocal() || p.isCustom())
                        .filter(p -> matchesSearch(p, keyword))
                        .toList();
        List<ProviderInfo> configured = new ArrayList<>();
        List<ProviderInfo> available = new ArrayList<>();
        for (ProviderInfo p : local) {
            if (getIsConfigured(p)) {
                configured.add(p);
            } else {
                available.add(p);
            }
        }
        // Configured
        VBox configuredSection = new VBox(8);
        configuredSection.setPadding(new Insets(12, 14, 8, 14));
        Label confTitle = new Label("Configured");
        confTitle.getStyleClass().add("fw-bold-15");
        configuredSection.getChildren().add(confTitle);
        if (configured.isEmpty()) {
            Label empty = new Label("No local providers configured yet.");
            empty.getStyleClass().add("muted");
            configuredSection.getChildren().add(empty);
        } else {
            FlowPane grid = new FlowPane(8, 8);
            for (ProviderInfo p : configured) {
                grid.getChildren().add(buildLocalCard(p));
            }
            configuredSection.getChildren().add(grid);
        }
        // Available
        VBox availableSection = new VBox(8);
        availableSection.setPadding(new Insets(8, 14, 12, 14));
        Label availTitle = new Label("Available Providers");
        availTitle.getStyleClass().add("fw-bold-15");
        availableSection.getChildren().add(availTitle);
        if (available.isEmpty()) {
            Label empty = new Label("All local providers are configured.");
            empty.getStyleClass().add("muted");
            availableSection.getChildren().add(empty);
        } else {
            FlowPane grid = new FlowPane(8, 8);
            for (ProviderInfo p : available) {
                grid.getChildren().add(buildLocalCard(p));
            }
            availableSection.getChildren().add(grid);
        }
        Separator sep = new Separator();
        contentArea.getChildren().addAll(configuredSection, sep, availableSection);
    }

    // ================================================================================
    // Grouping logic
    // ================================================================================
    /**
     * Determine whether the provider is configured (aligns with Emailclaw getIsConfigured).
     */
    private static boolean getIsConfigured(ProviderInfo p) {
        LOGGER.info("Method execution: getIsConfigured");
        if (p.isCustom() && p.getBaseUrl() != null && !p.getBaseUrl().isBlank()) return true;
        if (!p.isRequireApiKey()) return true;
        return p.getApiKey() != null && !p.getApiKey().isBlank();
    }

    /**
     * Divide provider list into configured / available groups. If any variant in the same brand group is configured, the entire group enters configured.
     */
    private Map<String, List<ProviderInfo>> groupByConfigured(List<ProviderInfo> providers) {
        List<ProviderInfo> configured = new ArrayList<>();
        List<ProviderInfo> available = new ArrayList<>();
        // Group by brand first
        Map<String, List<ProviderInfo>> byGroup = new LinkedHashMap<>();
        List<ProviderInfo> ungrouped = new ArrayList<>();
        for (ProviderInfo p : providers) {
            if (!p.getProviderGroup().isEmpty()) {
                byGroup.computeIfAbsent(p.getProviderGroup(), k -> new ArrayList<>()).add(p);
            } else {
                ungrouped.add(p);
            }
        }
        // Check if each brand group has any configured variant
        Set<String> configuredGroups = new LinkedHashSet<>();
        for (Map.Entry<String, List<ProviderInfo>> entry : byGroup.entrySet()) {
            for (ProviderInfo p : entry.getValue()) {
                if (getIsConfigured(p)) {
                    configuredGroups.add(entry.getKey());
                    break;
                }
            }
        }
        // Assign
        for (Map.Entry<String, List<ProviderInfo>> entry : byGroup.entrySet()) {
            if (configuredGroups.contains(entry.getKey())) {
                configured.addAll(entry.getValue());
            } else {
                available.addAll(entry.getValue());
            }
        }
        for (ProviderInfo p : ungrouped) {
            if (getIsConfigured(p)) {
                configured.add(p);
            } else {
                available.add(p);
            }
        }
        // Sort by catalog
        sortByCatalogOrder(configured);
        sortByCatalogOrder(available);
        return Map.of("configured", configured, "available", available);
    }

    /**
     * Group provider list into ProviderGroup by brand.
     */
    private List<ProviderGroup> groupProviders(List<ProviderInfo> providers) {
        LOGGER.info("Method execution: groupProviders");
        Map<String, List<ProviderInfo>> byGroup = new LinkedHashMap<>();
        List<ProviderInfo> ungrouped = new ArrayList<>();
        for (ProviderInfo p : providers) {
            if (!p.getProviderGroup().isEmpty()) {
                byGroup.computeIfAbsent(p.getProviderGroup(), k -> new ArrayList<>()).add(p);
            } else {
                ungrouped.add(p);
            }
        }
        List<ProviderGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<ProviderInfo>> entry : byGroup.entrySet()) {
            String groupName = entry.getValue().get(0).getProviderGroupName();
            result.add(new ProviderGroup(entry.getKey(), groupName, entry.getValue()));
        }
        // Ungrouped providers form independent groups
        for (ProviderInfo p : ungrouped) {
            result.add(new ProviderGroup(p.getId(), p.getName(), List.of(p)));
        }
        return result;
    }

    private boolean matchesSearch(ProviderInfo provider, String keyword) {
        LOGGER.info("Method execution: matchesSearch");
        if (keyword.isBlank()) return true;
        return provider.getName().toLowerCase().contains(keyword)
                || provider.getId().toLowerCase().contains(keyword)
                || provider.getProviderGroupName().toLowerCase().contains(keyword);
    }

    private void sortByCatalogOrder(List<ProviderInfo> providers) {
        LOGGER.info("Method execution: sortByCatalogOrder");
        providers.sort(
                Comparator.comparingInt(
                                (ProviderInfo p) -> {
                                    int idx = CATALOG_ORDER.indexOf(p.getId());
                                    return idx >= 0 ? idx : Integer.MAX_VALUE;
                                })
                        .thenComparing(p -> p.getName()));
    }

    // ================================================================================
    // Card Building
    // ================================================================================
    /**
     * Configured brand group card (including Segmented Control switching variants).
     */
    private VBox buildGroupCard(ProviderGroup pg) {
        LOGGER.info("Method execution: buildGroupCard");
        VBox card = new VBox(0);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12));
        card.setStyle(card.getStyle() + "-fx-background-radius: 8;");
        // Header: Icon + Name + "configured" green dot
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren()
                .addAll(
                        letterIcon(pg.groupName, providerColor(pg.variants.get(0).getId())),
                        new Label(pg.groupName),
                        createDot("#22c55e"));
        card.getChildren().add(header);
        // Segmented Control switches variants
        ToggleGroup variantGroup = new ToggleGroup();
        HBox segmented = new HBox(0);
        segmented.setAlignment(Pos.CENTER_LEFT);
        segmented.setPadding(new Insets(8, 0, 0, 0));
        for (int i = 0; i < pg.variants.size(); i++) {
            ProviderInfo v = pg.variants.get(i);
            ToggleButton seg = new ToggleButton(variantLabel(v.getProviderVariant()));
            seg.setToggleGroup(variantGroup);
            seg.setPrefHeight(28);
            seg.setMinWidth(Region.USE_PREF_SIZE);
            seg.setUserData(v);
            if (i == 0) seg.setSelected(true);
        }
        segmented
                .getChildren()
                .addAll(variantGroup.getToggles().stream().map(t -> (ToggleButton) t).toList());
        card.getChildren().add(segmented);
        // Variant content area
        VBox variantContent = new VBox(6);
        variantContent.setPadding(new Insets(8, 0, 0, 0));
        card.getChildren().add(variantContent);
        // Initially display the content of the first variant
        showVariantContent(variantContent, pg.variants.get(0));
        // Listen to variant switching
        variantGroup
                .selectedToggleProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (n != null && n.getUserData() instanceof ProviderInfo v) {
                                variantContent.getChildren().clear();
                                showVariantContent(variantContent, v);
                            }
                        });
        return card;
    }

    /**
     * Display detailed content of the variant (Endpoint / API Key / Models).
     */
    private void showVariantContent(VBox container, ProviderInfo p) {
        LOGGER.info("Method execution: showVariantContent");
        // Endpoint
        HBox endpointRow = new HBox(6);
        endpointRow.setAlignment(Pos.CENTER_LEFT);
        Label epLabel = new Label("Endpoint:");
        epLabel.getStyleClass().add("muted");
        Label epValue =
                new Label(
                        p.getBaseUrl() == null || p.getBaseUrl().isBlank()
                                ? "Not set"
                                : p.getBaseUrl());
        epValue.setWrapText(true);
        endpointRow.getChildren().addAll(epLabel, epValue);
        HBox.setHgrow(epValue, Priority.ALWAYS);
        // API Key
        HBox keyRow = new HBox(6);
        keyRow.setAlignment(Pos.CENTER_LEFT);
        Label keyLabel = new Label("API Key:");
        keyLabel.getStyleClass().add("muted");
        Node keyContent = buildApiKeyContent(p);
        keyRow.getChildren().addAll(keyLabel, keyContent);
        HBox.setHgrow(keyContent, Priority.ALWAYS);
        // Models
        HBox modelRow = new HBox(6);
        modelRow.setAlignment(Pos.CENTER_LEFT);
        Label modelLabel = new Label("Model:");
        modelLabel.getStyleClass().add("muted");
        int count = p.allModels().size();
        Label models = new Label(count > 0 ? count + " models" : "No models");
        modelRow.getChildren().addAll(modelLabel, models);
        // Actions
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(4, 0, 0, 0));
        if (!getIsConfigured(p)) {
            Button configBtn = new Button("Configure \u2192");
            configBtn.getStyleClass().add("primary-btn");
            configBtn.setOnAction(e -> showConfigureDialog(p));
            actions.getChildren().add(configBtn);
        } else {
            Button modelsBtn = new Button("Models");
            modelsBtn.setOnAction(e -> showModelManagerDialog(p));
            Button settingsBtn = new Button("Settings");
            settingsBtn.setOnAction(e -> showConfigureDialog(p));
            actions.getChildren().addAll(modelsBtn, settingsBtn);
        }
        container.getChildren().addAll(endpointRow, keyRow, modelRow, actions);
    }

    /**
     * Configured standalone Provider card.
     */
    private VBox buildRemoteCard(ProviderInfo p) {
        LOGGER.info("Method execution: buildRemoteCard");
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12));
        card.setPrefWidth(400);
        card.setStyle(card.getStyle() + "-fx-background-radius: 8;");
        // Header: Icon + Name + status dot + label
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        boolean configured = getIsConfigured(p);
        header.getChildren().add(letterIcon(p.getName(), providerColor(p.getId())));
        Label name = new Label(p.getName());
        name.getStyleClass().add("fw-700");
        header.getChildren().add(name);
        header.getChildren().add(createDot(configured ? "#22c55e" : "#a3a3a3"));
        if (p.isFreeTier()) {
            header.getChildren().add(tagLabel("FREE", "#dcfce7", "#16a34a"));
        }
        card.getChildren().add(header);
        // Endpoint
        Label ep =
                new Label(
                        p.getBaseUrl() == null || p.getBaseUrl().isBlank()
                                ? "Not set"
                                : p.getBaseUrl());
        ep.setWrapText(true);
        ep.getStyleClass().add("muted");
        ep.getStyleClass().add("text-11");
        card.getChildren().add(ep);
        // API Key
        HBox keyRow = new HBox(6);
        keyRow.setAlignment(Pos.CENTER_LEFT);
        Label keyLbl = new Label("API Key:");
        keyLbl.getStyleClass().add("muted");
        keyRow.getChildren().addAll(keyLbl, buildApiKeyContent(p));
        card.getChildren().add(keyRow);
        // Models count
        int count = p.allModels().size();
        card.getChildren().add(new Label(count > 0 ? count + " models" : "No models"));
        // Actions
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button modelsBtn = new Button("Models");
        modelsBtn.setOnAction(e -> showModelManagerDialog(p));
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> showConfigureDialog(p));
        actions.getChildren().addAll(modelsBtn, settingsBtn);
        card.getChildren().add(actions);
        return card;
    }

    /**
     * Local Provider card.
     */
    private VBox buildLocalCard(ProviderInfo p) {
        LOGGER.info("Method execution: buildLocalCard");
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(10));
        card.setPrefWidth(260);
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().add(letterIcon(p.getName(), providerColor(p.getId())));
        Label name = new Label(p.getName());
        name.getStyleClass().add("fw-700");
        header.getChildren().add(name);
        if (p.isLocal()) {
            header.getChildren().add(tagLabel("Local", "#dbeafe", "#2563eb"));
        }
        if (p.isCustom()) {
            header.getChildren().add(tagLabel("Custom", "#fef3c7", "#d97706"));
        }
        card.getChildren().add(header);
        Label ep =
                new Label(p.getBaseUrl() == null || p.getBaseUrl().isBlank() ? "" : p.getBaseUrl());
        ep.setWrapText(true);
        ep.getStyleClass().add("muted");
        ep.getStyleClass().add("text-11");
        if (!ep.getText().isEmpty()) card.getChildren().add(ep);
        int count = p.allModels().size();
        card.getChildren().add(new Label(count > 0 ? count + " models" : "No models"));
        HBox actions = new HBox(6);
        Button modelsBtn = new Button("Models");
        modelsBtn.setOnAction(e -> showModelManagerDialog(p));
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> showConfigureDialog(p));
        actions.getChildren().addAll(modelsBtn, settingsBtn);
        if (p.isCustom()) {
            Button delBtn = new Button("Delete");
            delBtn.setOnAction(
                    e -> {
                        providerService.removeCustomProvider(p);
                        refresh();
                    });
            actions.getChildren().add(delBtn);
        }
        card.getChildren().add(actions);
        return card;
    }

    /**
     * Brand group row in Available Providers area (aligns with availableItem in Emailclaw availableGrid).
     */
    private HBox buildAvailableGroupRow(ProviderGroup pg) {
        LOGGER.info("Method execution: buildAvailableGroupRow");
        // Get the representative provider in the group (the first one)
        ProviderInfo first = pg.variants.get(0);
        boolean hasFree = pg.variants.stream().anyMatch(p -> p.isFreeTier());
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(8, 12, 8, 12));
        chip.getStyleClass().addAll("bg-f9", "border-e7", "border-1", "radius-8");
        chip.getChildren().add(letterIcon(pg.groupName, providerColor(first.getId())));
        Label name = new Label(pg.groupName);
        name.getStyleClass().add("fw-bold-13");
        chip.getChildren().add(name);
        // Add a gray dot to match the Available status
        chip.getChildren().add(createDot("#a3a3a3"));
        if (hasFree) {
            chip.getChildren().add(tagLabel("FREE", "#dcfce7", "#16a34a"));
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        chip.getChildren().add(spacer);
        Button configureBtn = new Button("Configure \u2192");
        configureBtn.getStyleClass().add("primary-btn");
        int variantCount = pg.variants.size();
        configureBtn.setOnAction(
                e -> {
                    if (variantCount > 1) {
                        showVariantSelectDialog(pg);
                    } else {
                        showConfigureDialog(first);
                    }
                });
        chip.getChildren().add(configureBtn);
        return chip;
    }

    /**
     * Variant selection dialog for brand group (corresponds to Emailclaw variantSelectGroup = system-settings-15-models-variant-*.png).
     */
    private void showVariantSelectDialog(ProviderGroup pg) {
        LOGGER.info("Method execution: showVariantSelectDialog");
        Dialog<ProviderInfo> dialog = new Dialog<>();
        dialog.setTitle("Select " + pg.groupName + " variant");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(440);
        VBox list = new VBox(6);
        list.setPadding(new Insets(12));
        for (ProviderInfo v : pg.variants) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 12, 10, 12));
            row.setStyle(
                    "-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-width: 1;"
                            + " -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;");
            row.getChildren().add(letterIcon(v.getName(), providerColor(v.getId())));
            Label vName = new Label(v.getName());
            vName.getStyleClass().add("fw-bold-13");
            row.getChildren().add(vName);
            if (v.isFreeTier()) {
                row.getChildren().add(tagLabel("FREE", "#dcfce7", "#16a34a"));
            }
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().add(spacer);
            Label arrow = new Label("\u2192");
            arrow.getStyleClass().add("muted");
            row.getChildren().add(arrow);
            row.setOnMouseClicked(
                    evt -> {
                        dialog.setResult(v);
                        dialog.close();
                    });
            list.getChildren().add(row);
        }
        dialog.getDialogPane().setContent(list);
        dialog.showAndWait().ifPresent(this::showConfigureDialog);
    }

    // ================================================================================
    // Configuration dialog (aligns with Emailclaw ProviderConfigModal)
    // ================================================================================
    private void showConfigureDialog(ProviderInfo provider) {
        LOGGER.info("Method execution: showConfigureDialog");
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Configure " + provider.getName());
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(520);
        // Base URL
        TextField baseUrl = new TextField(provider.getBaseUrl());
        baseUrl.setPromptText(baseUrlPlaceholder(provider));
        if (provider.isFreezeUrl()) baseUrl.setEditable(false);
        // Base URL preset (region selection)
        ComboBox<String> baseUrlPreset = new ComboBox<>();
        Object optionsRaw =
                provider.getMeta() == null ? null : provider.getMeta().get("base_url_options");
        if (optionsRaw instanceof List<?> options) {
            for (Object item : options) {
                if (item instanceof Map<?, ?> row) {
                    Object label = row.get("label");
                    Object value = row.get("value");
                    if (label != null && value != null) {
                        baseUrlPreset.getItems().add(label + " -> " + value);
                    }
                }
            }
        }
        baseUrlPreset.setDisable(baseUrlPreset.getItems().isEmpty());
        baseUrlPreset.setPromptText("Select regional endpoint");
        baseUrlPreset
                .valueProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (newV == null) return;
                            int arrow = newV.indexOf("->");
                            if (arrow >= 0 && arrow + 2 < newV.length()) {
                                baseUrl.setText(newV.substring(arrow + 2).trim());
                            }
                        });
        // API Key
        PasswordField apiKey = new PasswordField();
        apiKey.setText(provider.getApiKey());
        String prefix =
                provider.getApiKeyPrefix() == null || provider.getApiKeyPrefix().isBlank()
                        ? "sk-"
                        : provider.getApiKeyPrefix();
        apiKey.setPromptText(prefix + "...");
        // Advanced Configuration (JSON)
        Label advLabel = new Label("Advanced Configuration (JSON)");
        TextArea advanced = new TextArea(json(provider.getGenerateKwargs()));
        advanced.setPrefRowCount(5);
        advanced.setWrapText(true);
        // Test Connection
        Button test = new Button("Test Connection");
        test.setOnAction(
                e -> {
                    ProviderService.TestResult result =
                            providerService.testProviderConfig(
                                    provider, baseUrl.getText(), apiKey.getText());
                    if (result.success()) {
                        info(result.message());
                    } else {
                        error(result.message());
                    }
                });
        VBox body = new VBox(10);
        body.setPadding(new Insets(12));
        body.getChildren()
                .addAll(
                        new Label("Base URL"),
                        baseUrl,
                        baseUrlPreset.getItems().isEmpty() ? new Region() : baseUrlPreset,
                        new Label("API Key"),
                        apiKey,
                        advLabel,
                        advanced,
                        test);
        // Remove empty region
        body.getChildren()
                .removeIf(
                        n ->
                                n instanceof Region
                                        && !(n instanceof TextField)
                                        && !(n instanceof TextArea)
                                        && !(n instanceof ComboBox));
        dialog.getDialogPane().setContent(body);
        dialog.setResultConverter(
                btn -> {
                    if (btn == saveType) {
                        provider.setBaseUrl(baseUrl.getText().trim());
                        provider.setApiKey(apiKey.getText().trim());
                        try {
                            provider.setGenerateKwargs(
                                    new ObjectMapper()
                                            .readValue(advanced.getText(), java.util.Map.class));
                        } catch (Exception ignored) {
                            provider.setGenerateKwargs(new LinkedHashMap<>());
                        }
                        providerService.save();
                        return true;
                    }
                    return false;
                });
        dialog.showAndWait()
                .ifPresent(
                        saved -> {
                            if (saved) refresh();
                        });
    }

    private String baseUrlPlaceholder(ProviderInfo p) {
        LOGGER.info("Method execution: baseUrlPlaceholder");
        return switch (p.getId()) {
            case "azure-openai" -> "https://<resource>.openai.azure.com/openai/v1";
            case "anthropic" -> "https://api.anthropic.com";
            case "openai" -> "https://api.openai.com/v1";
            case "opencode" -> "https://opencode.ai/zen/v1";
            case "ollama" -> "http://localhost:11434";
            case "lmstudio" -> "http://localhost:1234/v1";
            default -> "https://api.example.com/v1";
        };
    }

    // ================================================================================
    // Model management dialog (simplified version)
    // ================================================================================
    private void showModelManagerDialog(ProviderInfo provider) {
        LOGGER.info("Method execution: showModelManagerDialog");
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(provider.getName() + " \u2014 Model Management");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(900);
        dialog.getDialogPane().setMinWidth(900);
        TextField filter = new TextField();
        filter.setPromptText("Search models...");
        VBox list = new VBox(8);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox addPanel = new VBox(8);
        addPanel.getStyleClass().add("card-lite");
        addPanel.setVisible(false);
        addPanel.setManaged(false);
        TextField id = new TextField();
        id.setPromptText("e.g. gpt-4o, gemini-2.0-flash");
        TextField name = new TextField();
        name.setPromptText("e.g. GPT-4o, Gemini 2.0 Flash");
        Button cancelAdd = new Button("Cancel");
        Button add = new Button("Add Model");
        add.getStyleClass().add("primary-btn");
        add.setOnAction(
                e -> {
                    if (!id.getText().isBlank() && !name.getText().isBlank()) {
                        providerService.addModel(
                                provider, id.getText().trim(), name.getText().trim());
                        renderModelRows(provider, list, filter.getText());
                        id.clear();
                        name.clear();
                    }
                });
        cancelAdd.setOnAction(
                e -> {
                    addPanel.setVisible(false);
                    addPanel.setManaged(false);
                });
        addPanel.getChildren()
                .addAll(
                        new Label("Model ID *"),
                        id,
                        new Label("Model Name"),
                        name,
                        new HBox(8, cancelAdd, add));
        Button showAdd = new Button("+  Add Model");
        showAdd.setMaxWidth(Double.MAX_VALUE);
        showAdd.setOnAction(
                e -> {
                    addPanel.setVisible(true);
                    addPanel.setManaged(true);
                });
        filter.textProperty().addListener((obs, o, n) -> renderModelRows(provider, list, n));
        renderModelRows(provider, list, "");
        VBox body = new VBox(10, filter, scroll, showAdd, addPanel);
        body.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(body);
        dialog.showAndWait();
        refresh();
    }

    private void renderModelRows(ProviderInfo provider, VBox list, String keyword) {
        LOGGER.info("Method execution: renderModelRows");
        list.getChildren().clear();
        String key = keyword == null ? "" : keyword.toLowerCase().trim();
        for (ModelInfo model : provider.allModels()) {
            if (!key.isBlank()
                    && !model.getName().toLowerCase().contains(key)
                    && !model.getId().toLowerCase().contains(key)) continue;
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row-lite");
            row.setPadding(new Insets(6, 8, 6, 8));
            VBox modelInfo = new VBox(2);
            Label modelName = new Label(model.getName());
            modelName.getStyleClass().add("fw-bold-14");
            Label modelId = new Label(model.getId());
            modelId.getStyleClass().add("muted");
            modelId.getStyleClass().add("text-11");
            modelInfo.getChildren().addAll(modelName, modelId);
            HBox labelsBox = new HBox(6);
            labelsBox.setAlignment(Pos.CENTER_RIGHT);
            Label typeLabel =
                    new Label(
                            (model.isSupportsImage() || model.isSupportsVideo())
                                    ? "MultiModal"
                                    : "Text");
            typeLabel.getStyleClass().add("badge-tag");
            labelsBox.getChildren().add(typeLabel);
            if (model.isFree()) {
                labelsBox.getChildren().add(tagLabel("Free", "#dcfce7", "#16a34a"));
            }
            labelsBox
                    .getChildren()
                    .add(
                            tagLabel(
                                    model.isBuiltIn() ? "Built-in" : "User-added",
                                    model.isBuiltIn() ? "#dcfce7" : "#fef3c7",
                                    model.isBuiltIn() ? "#16a34a" : "#d97706"));
            HBox actionsBox = new HBox(6);
            actionsBox.setAlignment(Pos.CENTER_RIGHT);
            if (!model.isBuiltIn()) {
                Button delBtn =
                        createIconButton(
                                "\uD83D\uDDD1",
                                "Delete",
                                () -> {
                                    providerService.removeCustomModel(provider, model);
                                    renderModelRows(provider, list, keyword);
                                });
                actionsBox.getChildren().add(delBtn);
            }
            HBox spacer = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(modelInfo, spacer, labelsBox, actionsBox);
            list.getChildren().add(row);
        }
    }

    // ================================================================================
    // Add custom Provider dialog
    // ================================================================================
    private void showAddProviderDialog() {
        LOGGER.info("Method execution: showAddProviderDialog");
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Add Provider");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        TextField id = new TextField();
        id.setPromptText("provider-id");
        TextField name = new TextField();
        name.setPromptText("Provider Name");
        TextField baseUrl = new TextField();
        baseUrl.setPromptText("https://api.example.com/v1");
        TextField apiKeyPrefix = new TextField();
        apiKeyPrefix.setPromptText("sk-");
        VBox body =
                new VBox(
                        10,
                        new Label("ID"),
                        id,
                        new Label("Name"),
                        name,
                        new Label("Base URL"),
                        baseUrl,
                        new Label("API Key Prefix"),
                        apiKeyPrefix);
        body.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(body);
        dialog.setResultConverter(
                btn -> {
                    if (btn == saveType && !id.getText().isBlank()) {
                        ProviderInfo p = new ProviderInfo();
                        p.setId(id.getText().trim());
                        p.setName(name.getText().trim());
                        p.setBaseUrl(baseUrl.getText().trim());
                        p.setApiKeyPrefix(apiKeyPrefix.getText().trim());
                        p.setCustom(true);
                        p.setFreezeUrl(false);
                        p.setRequireApiKey(false);
                        providerService.upsertCustomProvider(p);
                        return true;
                    }
                    return false;
                });
        dialog.showAndWait()
                .ifPresent(
                        saved -> {
                            if (saved) refresh();
                        });
    }

    // ================================================================================
    // Helper methods
    // ================================================================================
    /**
     * Build API Key display area in Provider card.
     */
    private Node buildApiKeyContent(ProviderInfo p) {
        LOGGER.info("Method execution: buildApiKeyContent");
        if (p.getApiKey() != null && !p.getApiKey().isBlank()) {
            return new Label("********");
        }
        if (!p.isRequireApiKey()) {
            Label notRequired = new Label("Not required");
            notRequired.getStyleClass().add("muted");
            return notRequired;
        }
        HBox inline = new HBox(6);
        inline.setAlignment(Pos.CENTER_LEFT);
        PasswordField keyInput = new PasswordField();
        String prefix =
                p.getApiKeyPrefix() == null || p.getApiKeyPrefix().isBlank()
                        ? "sk-"
                        : p.getApiKeyPrefix();
        keyInput.setPromptText(prefix + "...");
        keyInput.setPrefWidth(160);
        Button saveKey = new Button("Save");
        saveKey.setOnAction(
                e -> {
                    if (keyInput.getText().isBlank()) return;
                    p.setApiKey(keyInput.getText().trim());
                    providerService.save();
                    keyInput.clear();
                    refresh();
                });
        inline.getChildren().addAll(keyInput, saveKey);
        return inline;
    }

    /**
     * Letter icon (aligns with Emailclaw ProviderIconComponent).
     */
    private Node letterIcon(String name, String color) {
        LOGGER.info("Method execution: letterIcon");
        String letter = (name == null || name.isBlank()) ? "?" : name.substring(0, 1).toUpperCase();
        Label lbl = new Label(letter);
        lbl.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white; "
                        + "-fx-background-color: "
                        + color
                        + "; -fx-background-radius: 16; -fx-padding: 0; -fx-min-width: 32;"
                        + " -fx-min-height: 32; -fx-max-width: 32; -fx-max-height: 32;"
                        + " -fx-alignment: center;");
        return lbl;
    }

    private String providerColor(String id) {
        LOGGER.info("Method execution: providerColor");
        String key = id.toLowerCase();
        if (LETTER_COLORS.containsKey(key)) return LETTER_COLORS.get(key);
        // Look up from providerGroup
        for (Map.Entry<String, String> e : LETTER_COLORS.entrySet()) {
            if (key.startsWith(e.getKey()) || e.getKey().startsWith(key)) return e.getValue();
        }
        // Hash to palette
        int hash = Math.abs(id.hashCode()) % PALETTE.length;
        return PALETTE[hash];
    }

    /**
     * Create colored dot.
     */
    private Node createDot(String color) {
        LOGGER.info("Method execution: createDot");
        Circle dot = new Circle(5, Color.web(color));
        return dot;
    }

    /**
     * Create label.
     */
    private Label tagLabel(String text, String bgColor, String textColor) {
        LOGGER.info("Method execution: tagLabel");
        Label lbl = new Label(text);
        lbl.setStyle(
                "-fx-padding: 2 8; -fx-background-color: "
                        + bgColor
                        + "; -fx-text-fill: "
                        + textColor
                        + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px;"
                        + " -fx-font-weight: bold;");
        return lbl;
    }

    /**
     * Variant display name.
     */
    private String variantLabel(String variant) {
        LOGGER.info("Method execution: variantLabel");
        return switch (variant) {
            case "dashscope" -> "DashScope";
            case "coding_plan_cn" -> "Coding (CN)";
            case "coding_plan_intl" -> "Coding (Intl)";
            case "coding_plan" -> "Coding Plan";
            case "token_plan" -> "Token Plan";
            case "token_plan_intl" -> "Token (Intl)";
            case "open_platform" -> "Open Platform";
            case "open_platform_cn" -> "China";
            case "open_platform_intl" -> "International";
            case "china" -> "China";
            case "international" -> "International";
            default -> variant;
        };
    }

    private Button createIconButton(String icon, String tooltipText, Runnable action) {
        LOGGER.info("Method execution: createIconButton");
        Button button = new Button();
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("text-14-blue");
        button.setGraphic(iconLabel);
        button.setTooltip(new Tooltip(tooltipText));
        button.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-cursor: hand;");
        button.setOnAction(e -> action.run());
        return button;
    }

    private String json(Object value) {
        LOGGER.info("Method execution: json");
        try {
            return new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void info(String message) {
        LOGGER.info("Method execution: info");
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.showAndWait();
    }

    private void error(String message) {
        LOGGER.info("Method execution: error");
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.showAndWait();
    }

    /**
     * Brand group container.
     */
    private record ProviderGroup(String groupKey, String groupName, List<ProviderInfo> variants) {}

    // ================================================================================
    // ViewPane interface
    // ================================================================================
    @Override
    public Node root() {
        LOGGER.info("Method execution: root");
        return root;
    }

    @Override
    public void refresh() {
        LOGGER.info("Method execution: refresh");
        LOGGER.fine("Execute ModelsView refresh operation");
        List<ProviderInfo> allProviders = providerService.listProviders();
        List<ProviderInfo> cloudProviders =
                allProviders.stream().filter(p -> !p.isLocal() && !p.isCustom()).toList();
        List<ProviderInfo> localProviders =
                allProviders.stream().filter(p -> p.isLocal() || p.isCustom()).toList();
        int cloudCount = groupProviders(cloudProviders).size();
        int localCount = groupProviders(localProviders).size();
        if (cloudTab != null) cloudTab.setText("Cloud Providers (" + cloudCount + ")");
        if (localTab != null) localTab.setText("Local & Custom (" + localCount + ")");
        // Refresh Default LLM dropdown
        List<ProviderInfo> readyProviders =
                providerService.listProviders().stream()
                        .filter(providerService::isEligibleForDefaultLlm)
                        .toList();
        defaultProvider.getItems().setAll(readyProviders);
        if (currentAgent.getProviderId() != null && !currentAgent.getProviderId().isBlank()) {
            readyProviders.stream()
                    .filter(p -> p.getId().equals(currentAgent.getProviderId()))
                    .findFirst()
                    .ifPresent(p -> defaultProvider.getSelectionModel().select(p));
        } else if (!defaultProvider.getItems().isEmpty() && defaultProvider.getValue() == null) {
            defaultProvider.getSelectionModel().selectFirst();
        }
        ProviderInfo p = defaultProvider.getValue();
        defaultModel.getItems().setAll(p == null ? List.of() : p.allModels());
        if (currentAgent.getModelId() != null && !currentAgent.getModelId().isBlank()) {
            defaultModel.getItems().stream()
                    .filter(m -> m.getId().equals(currentAgent.getModelId()))
                    .findFirst()
                    .ifPresent(m -> defaultModel.getSelectionModel().select(m));
        } else if (!defaultModel.getItems().isEmpty() && defaultModel.getValue() == null) {
            defaultModel.getSelectionModel().selectFirst();
        }
        renderTabContent();
    }
}
