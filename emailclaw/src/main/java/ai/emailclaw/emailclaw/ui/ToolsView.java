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

import ai.emailclaw.emailclaw.model.ToolInfo;
import ai.emailclaw.emailclaw.service.ToolService;
import java.util.logging.Logger;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class ToolsView implements ViewPane {
    /**
     * Tool switch view.
     */
    private static final Logger LOGGER = Logger.getLogger(ToolsView.class.getName());

    private final ToolService toolService;
    private final BorderPane root = new BorderPane();
    private final TilePane grid = new TilePane();

    public ToolsView(ToolService toolService) {
        this.toolService = toolService;
        buildUi();
        refresh();
    }

    private void buildUi() {
        VBox page = new VBox(10);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(14));
        HBox head = new HBox(8);
        Label title = new Label("Workspace / Built-in Tools");
        title.getStyleClass().add("page-title");

        Label subtitle =
                new Label(
                        "If a tool plug-in is installed, please manage it on the \"Plug-in"
                                + " Manager\" page.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button disableAll = new Button("Disable All");
        disableAll.setOnAction(
                e -> {
                    toolService.disableAll();
                    refresh();
                });
        head.getChildren().addAll(title, subtitle, spacer, disableAll);

        grid.setPrefColumns(4);
        grid.setHgap(10);
        grid.setVgap(10);
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("left-scroll");
        page.getChildren().addAll(head, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setCenter(page);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        LOGGER.fine("Refresh tool card list");
        grid.getChildren().clear();
        for (ToolInfo tool : toolService.list()) {
            VBox card = new VBox(8);
            card.getStyleClass().add("card");
            Label name = new Label(tool.name());
            name.getStyleClass().add("card-title");
            Label status = new Label(tool.enabled() ? "Enabled" : "Disabled");
            status.getStyleClass().add(tool.enabled() ? "status-ready" : "status-off");
            Label desc = new Label(tool.description());
            desc.setWrapText(true);
            ToggleButton toggle = new ToggleButton(tool.enabled() ? "Disable" : "Enable");
            toggle.setSelected(tool.enabled());
            toggle.setOnAction(
                    e -> {
                        toolService.setEnabled(tool.name(), !tool.enabled());
                        refresh();
                    });
            card.getChildren().addAll(status, name, desc, toggle);
            card.setPrefWidth(300);
            card.setPrefHeight(160);
            grid.getChildren().add(card);
        }
    }
}
