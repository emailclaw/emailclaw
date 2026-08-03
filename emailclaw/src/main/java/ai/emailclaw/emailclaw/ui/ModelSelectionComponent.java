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

import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.ProviderStatus;
import ai.emailclaw.emailclaw.service.ProviderService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Reusable component for selecting an LLM provider and model.
 */
public class ModelSelectionComponent extends Button {
    private static final Logger LOGGER = Logger.getLogger(ModelSelectionComponent.class.getName());

    private final ProviderService providerService;
    private BiConsumer<ProviderInfo, ModelInfo> onModelSelected;

    public ModelSelectionComponent(String text, ProviderService providerService) {
        super(text);
        this.providerService = providerService;
        this.setOnAction(e -> showModelDialog());
    }

    public void setOnModelSelected(BiConsumer<ProviderInfo, ModelInfo> onModelSelected) {
        this.onModelSelected = onModelSelected;
    }

    private void showModelDialog() {
        LOGGER.info("Open model selection dialog");
        Dialog<Void> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this.getScene().getWindow());
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Select a model");
        dialog.getDialogPane().setPrefWidth(680);
        dialog.getDialogPane().setMinWidth(680);
        dialog.getDialogPane().setPrefHeight(800);
        dialog.getDialogPane().setMinHeight(800);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab proTab = new Tab();
        proTab.setText("PRO");
        VBox proContent = createModelTabContent(false, dialog);
        proTab.setContent(proContent);

        Tab freeTab = new Tab();
        freeTab.setText("FREE");
        VBox freeContent = createModelTabContent(true, dialog);
        freeTab.setContent(freeContent);

        tabPane.getTabs().addAll(freeTab, proTab);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.setOnShown(
                event ->
                        Platform.runLater(
                                () -> {
                                    if (dialog.getDialogPane().getScene() == null) {
                                        return;
                                    }
                                    if (dialog.getDialogPane().getScene().getWindow()
                                            instanceof Stage stage) {
                                        stage.setMinHeight(760);
                                        stage.sizeToScene();
                                        if (stage.getHeight() < 760) {
                                            stage.setHeight(760);
                                        }
                                    }
                                }));

        dialog.setResultConverter(
                button -> {
                    if (button == ButtonType.OK) {
                        Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
                        if (activeTab != null
                                && activeTab.getContent() instanceof VBox tabContent) {
                            @SuppressWarnings("unchecked")
                            ListView<String> listView =
                                    (ListView<String>)
                                            tabContent.getChildren().stream()
                                                    .filter(n -> n instanceof ListView)
                                                    .findFirst()
                                                    .orElse(null);
                            if (listView != null
                                    && listView.getSelectionModel().getSelectedItem() != null) {
                                String item = listView.getSelectionModel().getSelectedItem();
                                String[] parts = item.split("\\|");
                                if (parts.length == 3) {
                                    ProviderInfo provider =
                                            providerService.listProviders().stream()
                                                    .filter(p -> p.getId().equals(parts[1]))
                                                    .findFirst()
                                                    .orElse(null);
                                    if (provider != null) {
                                        ModelInfo model =
                                                provider.allModels().stream()
                                                        .filter(m -> m.getId().equals(parts[2]))
                                                        .findFirst()
                                                        .orElse(null);
                                        if (model != null) {
                                            if (onModelSelected != null) {
                                                onModelSelected.accept(provider, model);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return null;
                });
        dialog.showAndWait();
    }

    private VBox createModelTabContent(boolean isFreeTab, Dialog<Void> dialog) {
        VBox tabContent = new VBox(8);
        tabContent.setPadding(new Insets(12));
        TextField filter = new TextField();
        filter.setPromptText("Search provider or model...");

        if (isFreeTab) {
            Label warningLabel =
                    new Label(
                            "Free models may have rate limits and limited availability. Refer to"
                                    + " the provider for details.");
            warningLabel.setStyle(
                    "-fx-text-fill: #ff6f00; -fx-padding: 8; -fx-background-color: #fff3e0;"
                        + " -fx-border-radius: 4; -fx-border-color: #ffb74d; -fx-border-width: 1;");
            warningLabel.setWrapText(true);
            tabContent.getChildren().add(warningLabel);
        }

        ListView<String> listView = new ListView<>();
        listView.setPrefHeight(500);
        listView.setMinHeight(480);

        List<String> allItems = new ArrayList<>();
        Map<String, List<String>> groupedItems = new LinkedHashMap<>();
        providerService.listProviders().stream()
                .filter(p -> ProviderStatus.READY_WITH_MODELS == providerService.status(p))
                .forEach(
                        provider -> {
                            List<String> providerModels = new ArrayList<>();
                            provider.allModels()
                                    .forEach(
                                            model -> {
                                                boolean isFreeModel =
                                                        model.isFree() || provider.isFreeTier();
                                                if (isFreeModel == isFreeTab) {
                                                    providerModels.add(
                                                            provider.getName()
                                                                    + " / "
                                                                    + model.getName()
                                                                    + "|"
                                                                    + provider.getId()
                                                                    + "|"
                                                                    + model.getId());
                                                }
                                            });
                            if (!providerModels.isEmpty()) {
                                groupedItems.put(provider.getName(), providerModels);
                                allItems.addAll(providerModels);
                            }
                        });
        listView.getItems().setAll(allItems);

        listView.setCellFactory(
                view ->
                        new ListCell<String>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setStyle("");
                                } else {
                                    String[] parts = item.split("\\|");
                                    if (parts.length >= 1) {
                                        setText(parts[0]);
                                        setStyle("");
                                    }
                                }
                            }
                        });

        filter.textProperty()
                .addListener(
                        (obs, oldText, newText) -> {
                            String query = newText == null ? "" : newText.toLowerCase();
                            listView.getItems()
                                    .setAll(
                                            allItems.stream()
                                                    .filter(it -> it.toLowerCase().contains(query))
                                                    .toList());
                        });
        tabContent.getChildren().addAll(filter, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        return tabContent;
    }
}
