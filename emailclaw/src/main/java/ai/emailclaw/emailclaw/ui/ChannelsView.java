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

import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.service.ChannelService;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public class ChannelsView implements ViewPane {
    /**
     * Channel switch management view.
     */
    private static final Logger LOGGER = Logger.getLogger(ChannelsView.class.getName());

    private final ChannelService channelService;
    private final VBox root = new VBox(14);
    private final FlowPane grid = new FlowPane(14, 14);

    public ChannelsView(ChannelService channelService) {
        this.channelService = channelService;
        initUi();
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(18));

        Label title = new Label("Channels");
        title.getStyleClass().add("page-title");

        Label subtitle =
                new Label(
                        "Manage built-in communication channels for this agent. "
                                + channelService.list().stream().filter(c -> c.isBuiltIn()).count()
                                + " built-in channels are provided. If a channel plug-in is"
                                + " installed, please manage it on the \"Plug-in Manager\" page.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        grid.setPadding(new Insets(8, 0, 0, 0));
        root.getChildren().addAll(title, subtitle, grid);
        renderGrid();
    }

    private void renderGrid() {
        LOGGER.fine("Refreshing channel card list");
        grid.getChildren().clear();
        for (ChannelInfo ch : channelService.list()) {
            grid.getChildren().add(channelCard(ch));
        }
    }

    private Node channelCard(ChannelInfo ch) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.setPrefWidth(200);
        card.setPrefHeight(120);

        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(ch.getName());
        name.getStyleClass().add("fw-600-15");
        Label badge = new Label(ch.isBuiltIn() ? "Built-in" : "Custom");
        badge.getStyleClass().add(ch.isBuiltIn() ? "status-builtin" : "status-custom");
        header.getChildren().addAll(name, badge);

        Label id = new Label(ch.getId());
        id.getStyleClass().add("muted");

        Label status = new Label(ch.isEnabled() ? "Enabled" : "Disabled");
        status.getStyleClass().add(ch.isEnabled() ? "status-ready" : "status-off");

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button toggle = new Button(ch.isEnabled() ? "Disable" : "Enable");
        toggle.getStyleClass().add("chip-btn");
        toggle.setOnAction(
                e -> {
                    channelService.toggleEnabled(ch);
                    renderGrid();
                });
        Button configure = new Button("Configure");
        configure.getStyleClass().add("chip-btn");
        configure.setOnAction(
                e -> {
                    Window owner =
                            configure.getScene() != null ? configure.getScene().getWindow() : null;
                    if (owner == null) {
                        return;
                    }
                    ai.emailclaw.emailclaw.plugin.EmailclawPlugin plugin =
                            channelService.getPluginInstance(ch.getId());
                    if (plugin != null) {
                        var providerOpt =
                                ai.emailclaw.emailclaw.ui.plugin.PluginUIFactory.getProvider(
                                        plugin.id());
                        if (providerOpt.isPresent()) {
                            ai.emailclaw.emailclaw.ui.plugin.CustomConfigViewProvider provider =
                                    providerOpt.get();
                            ai.emailclaw.emailclaw.model.ChannelInfo channel =
                                    channelService.list().stream()
                                            .filter(c -> c.getId().equals(plugin.id()))
                                            .findFirst()
                                            .orElse(null);
                            if (channel == null) {
                                channel =
                                        new ai.emailclaw.emailclaw.model.ChannelInfo(
                                                plugin.id(), plugin.displayName(), true, false);
                            }
                            java.util.Map<String, Object> initialConfig =
                                    channel.getPluginConfig() != null
                                            ? new java.util.HashMap<>(channel.getPluginConfig())
                                            : new java.util.HashMap<>();
                            initialConfig.put("enabled", channel.isEnabled());
                            initialConfig.put("botPrefix", channel.getBotPrefix());

                            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
                            dialogStage.initOwner(owner);
                            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                            dialogStage.setTitle(plugin.displayName() + " Channel Settings");

                            // Because lambdas require effectively final variables
                            final ai.emailclaw.emailclaw.model.ChannelInfo finalChannel = channel;
                            javafx.scene.Node viewNode =
                                    provider.buildView(
                                            initialConfig,
                                            newConfig -> {
                                                finalChannel.setEnabled(
                                                        (Boolean)
                                                                newConfig.getOrDefault(
                                                                        "enabled", false));
                                                finalChannel.setBotPrefix(
                                                        (String)
                                                                newConfig.getOrDefault(
                                                                        "botPrefix", ""));
                                                finalChannel.setPluginConfig(newConfig);
                                                channelService.save();
                                                dialogStage.close();
                                                renderGrid();
                                            },
                                            () -> {
                                                dialogStage.close();
                                            });

                            javafx.scene.Scene scene =
                                    new javafx.scene.Scene(
                                            (javafx.scene.Parent) viewNode, 500, 700);
                            if (owner.getScene() != null) {
                                scene.getStylesheets().addAll(owner.getScene().getStylesheets());
                            }
                            dialogStage.setScene(scene);
                            dialogStage.setResizable(false);
                            dialogStage.showAndWait();
                        } else {
                            LOGGER.warning(
                                    "Configuration panel not supported for this channel yet: "
                                            + ch.getId());
                        }
                    }
                });
        actions.getChildren().addAll(toggle, configure);

        card.getChildren().addAll(header, id, status);
        card.getChildren().add(actions);
        return card;
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
