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

import ai.emailclaw.emailclaw.service.MessageBusService;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class InboxView implements ViewPane {
    private static final Logger LOGGER = Logger.getLogger(InboxView.class.getName());

    private final VBox root = new VBox(10);
    private final List<InboxMessage> approvals = new ArrayList<>();
    private final List<InboxMessage> pushMessages = new ArrayList<>();
    private final VBox approvalsList = new VBox(8);
    private final VBox pushMessagesList = new VBox(8);
    private final CheckBox approvalsSelectAll = new CheckBox("Select all");
    private final CheckBox pushSelectAll = new CheckBox("Select all");

    /**
     * Inbox view, receives real-time messages via MessageBus and displays them.
     *
     * @param messageBusService Message bus service, used to subscribe to approval/push messages
     */
    public InboxView(MessageBusService messageBusService) {
        initUi();
        subscribeToMessageBus(messageBusService);
    }

    private void subscribeToMessageBus(MessageBusService messageBusService) {
        if (messageBusService == null) {
            LOGGER.log(
                    Level.WARNING,
                    "MessageBusService is null, Inbox will only show static sample data");
            initSampleMessages();
            return;
        }
        // Subscribe to approval message channel
        try {
            messageBusService
                    .getMessageBus()
                    .subscribe("agentscope:inbox:approvals")
                    .subscribe(
                            payload -> {
                                String text = payload.getOrDefault("text", "").toString();
                                if (!text.isBlank()) {
                                    Platform.runLater(
                                            () -> {
                                                approvals.add(new InboxMessage(text, false));
                                                renderMessageList(approvals, approvalsList);
                                            });
                                }
                            },
                            error ->
                                    LOGGER.log(
                                            Level.WARNING,
                                            "Failed to subscribe to approval messages",
                                            error));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception in subscribing to approval messages", e);
        }
        // Subscribe to push message channel
        try {
            messageBusService
                    .getMessageBus()
                    .subscribe("agentscope:inbox:push")
                    .subscribe(
                            payload -> {
                                String text = payload.getOrDefault("text", "").toString();
                                if (!text.isBlank()) {
                                    Platform.runLater(
                                            () -> {
                                                pushMessages.add(new InboxMessage(text, false));
                                                renderMessageList(pushMessages, pushMessagesList);
                                            });
                                }
                            },
                            error ->
                                    LOGGER.log(
                                            Level.WARNING,
                                            "Failed to subscribe to push messages",
                                            error));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception in subscribing to push messages", e);
        }
    }

    private void initSampleMessages() {
        approvals.add(new InboxMessage("Grant access for external agent execution", false));
        approvals.add(new InboxMessage("Approve new MCP client connection", false));
        approvals.add(new InboxMessage("Confirm upload from ZIP package", false));
        pushMessages.add(new InboxMessage("A new model provider is available.", false));
        pushMessages.add(new InboxMessage("Scheduled cron job completed successfully.", false));
        pushMessages.add(new InboxMessage("Security check produced no issues.", false));
    }

    private void initUi() {
        root.getStyleClass().add("page");
        root.setPadding(new Insets(16));
        Label title = new Label("Inbox");
        title.getStyleClass().add("page-title");

        TabPane tabs = new TabPane();
        tabs.getTabs()
                .addAll(
                        createInboxTab("Approvals", approvals, approvalsList, approvalsSelectAll),
                        createInboxTab(
                                "Push Messages", pushMessages, pushMessagesList, pushSelectAll));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        root.getChildren().addAll(title, tabs);
    }

    private Tab createInboxTab(
            String title, List<InboxMessage> messages, VBox listContainer, CheckBox selectAll) {
        Tab tab = new Tab(title);
        VBox tabRoot = new VBox(10);
        tabRoot.setPadding(new Insets(12));

        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(0, 0, 8, 0));
        selectAll.setOnAction(
                e -> {
                    boolean selected = selectAll.isSelected();
                    messages.forEach(msg -> msg.selected = selected);
                    renderMessageList(messages, listContainer);
                });
        Button deleteSelected = new Button("Delete Selected");
        deleteSelected.setOnAction(
                e -> {
                    messages.removeIf(msg -> msg.selected);
                    selectAll.setSelected(false);
                    renderMessageList(messages, listContainer);
                });
        toolbar.getChildren().addAll(selectAll, deleteSelected);

        ScrollPane scroll = new ScrollPane(listContainer);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        scroll.getStyleClass().add("left-scroll");

        tabRoot.getChildren().addAll(toolbar, scroll);
        tab.setContent(tabRoot);
        renderMessageList(messages, listContainer);
        return tab;
    }

    private void renderMessageList(List<InboxMessage> messages, VBox listContainer) {
        listContainer.getChildren().clear();
        if (messages.isEmpty()) {
            Label empty = new Label("No messages.");
            empty.getStyleClass().add("muted");
            empty.setPadding(new Insets(20));
            listContainer.getChildren().add(empty);
            return;
        }
        for (InboxMessage msg : messages) {
            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            CheckBox box = new CheckBox();
            box.setSelected(msg.selected);
            box.setOnAction(e -> msg.selected = box.isSelected());
            Label text = new Label(msg.text);
            text.setWrapText(true);
            text.setMaxWidth(640);
            row.getChildren().addAll(box, text);
            listContainer.getChildren().add(row);
        }
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        renderMessageList(approvals, approvalsList);
        renderMessageList(pushMessages, pushMessagesList);
    }

    private static class InboxMessage {
        private final String text;
        private boolean selected;

        InboxMessage(String text, boolean selected) {
            this.text = text;
            this.selected = selected;
        }
    }
}
