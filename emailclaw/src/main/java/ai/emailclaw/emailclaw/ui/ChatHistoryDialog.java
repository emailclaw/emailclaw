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

import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.service.ChatService;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Chat history dialog.
 *
 * <p>Displays all sessions of the current Agent, supports switching sessions and creating new sessions.
 */
class ChatHistoryDialog {

    private final ChatService chatService;

    private final String agentId;

    private ChatSessionInfo currentSession;

    /**
     * Session switch callback.
     */
    interface Callback {

        /**
         * Switch to the specified session.
         */
        void switchSession(ChatSessionInfo session);

        /**
         * Create a new session and return.
         */
        ChatSessionInfo createNewSession();
    }

    ChatHistoryDialog(ChatService chatService, String agentId, ChatSessionInfo currentSession) {
        this.chatService = chatService;
        this.agentId = agentId;
        this.currentSession = currentSession;
    }

    /**
     * Get the currently selected session.
     */
    ChatSessionInfo getCurrentSession() {
        return currentSession;
    }

    /**
     * Show dialog.
     *
     * @param owner     Parent window
     * @param stylesheets Stylesheets to be inherited by the dialog
     * @param callback  Callback interface
     */
    void show(Window owner, List<String> stylesheets, Callback callback) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.initStyle(StageStyle.DECORATED);
        dlg.setTitle("All Chats");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(480);
        dlg.getDialogPane().setPrefHeight(640);
        if (stylesheets != null) {
            dlg.getDialogPane().getStylesheets().addAll(stylesheets);
        }
        VBox container = new VBox(0);
        container.getStyleClass().add("bg-white");
        // --- Title bar ---
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 16, 12, 16));
        String kindStr =
                (currentSession != null
                                && ChatSessionInfo.KIND_TASK.equals(currentSession.getKind()))
                        ? "Tasks"
                        : "Chats";
        Label headerTitle = new Label("All " + kindStr);
        headerTitle.getStyleClass().addAll("text-18", "fw-700");
        header.getChildren().add(headerTitle);
        // --- Create New Chat button ---
        Button createNewBtn = new Button("Create New Chat");
        createNewBtn.setMaxWidth(Double.MAX_VALUE);
        createNewBtn.getStyleClass().add("btn-orange-lg");
        VBox btnWrapper = new VBox(createNewBtn);
        btnWrapper.setPadding(new Insets(0, 16, 12, 16));
        // --- History session list ---
        VBox sessionList = new VBox(0);
        sessionList.getStyleClass().add("bg-white");
        ScrollPane scrollPane = new ScrollPane(sessionList);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("pane-transparent");
        scrollPane.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        Runnable refreshList =
                () -> {
                    sessionList.getChildren().clear();
                    List<ChatSessionInfo> sessions =
                            chatService.sessions(agentId).stream()
                                    .filter(
                                            s ->
                                                    (currentSession.getKind() != null
                                                                    ? currentSession.getKind()
                                                                    : ChatSessionInfo.KIND_CHAT)
                                                            .equals(
                                                                    s.getKind() != null
                                                                            ? s.getKind()
                                                                            : ChatSessionInfo
                                                                                    .KIND_CHAT))
                                    .toList();
                    for (ChatSessionInfo s : sessions) {
                        VBox row = new VBox(4);
                        row.setPadding(new Insets(10, 16, 10, 16));
                        row.setStyle(
                                "-fx-background-color: "
                                        + (s.getId().equals(currentSession.getId())
                                                ? "#fff8f2"
                                                : "#ffffff")
                                        + ";-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;"
                                        + " -fx-cursor: hand;");
                        HBox nameRow = new HBox(8);
                        nameRow.setAlignment(Pos.CENTER_LEFT);
                        Label dot = new Label("\u25CF");
                        dot.setStyle(
                                "-fx-text-fill: "
                                        + (s.getId().equals(currentSession.getId())
                                                ? "#ff8800"
                                                : "#9ca3af")
                                        + "; -fx-font-size: 10px;");
                        Label sessionName =
                                new Label(
                                        s.getName() == null || s.getName().isBlank()
                                                ? "New Chat"
                                                : s.getName());
                        sessionName.getStyleClass().add("fw-600-14");
                        nameRow.getChildren().addAll(dot, sessionName);
                        HBox metaRow = new HBox(8);
                        metaRow.setAlignment(Pos.CENTER_LEFT);
                        Label dateLabel =
                                new Label(
                                        s.getUpdatedAt() == null
                                                ? ""
                                                : s.getUpdatedAt()
                                                        .replace("T", " ")
                                                        .substring(
                                                                0,
                                                                Math.min(
                                                                        19,
                                                                        s.getUpdatedAt()
                                                                                .length())));
                        dateLabel.getStyleClass().add("text-12-muted");
                        Label channelLabel =
                                new Label(
                                        s.getChannel() == null
                                                ? "Console"
                                                : capitalize(s.getChannel()));
                        channelLabel.getStyleClass().add("badge-gray-xs");
                        metaRow.getChildren().addAll(dateLabel, channelLabel);
                        row.getChildren().addAll(nameRow, metaRow);
                        // Click to switch session
                        row.setOnMouseClicked(
                                ev -> {
                                    currentSession = s;
                                    callback.switchSession(s);
                                    dlg.close();
                                });
                        row.setOnMouseEntered(
                                ev ->
                                        row.setStyle(
                                                "-fx-background-color: #fff8f2; -fx-border-color:"
                                                        + " #f0f0f0; -fx-border-width: 0 0 1 0;"
                                                        + " -fx-cursor: hand;"));
                        row.setOnMouseExited(
                                ev ->
                                        row.setStyle(
                                                "-fx-background-color: "
                                                        + (s.getId().equals(currentSession.getId())
                                                                ? "#fff8f2"
                                                                : "#ffffff")
                                                        + "; -fx-border-color: #f0f0f0;"
                                                        + " -fx-border-width: 0 0 1 0; -fx-cursor:"
                                                        + " hand;"));
                        sessionList.getChildren().add(row);
                    }
                    if (sessions.isEmpty()) {
                        Label empty = new Label("No chats yet");
                        empty.getStyleClass().add("empty-label");
                        sessionList.getChildren().add(empty);
                    }
                };
        createNewBtn.setOnAction(
                ev -> {
                    currentSession = callback.createNewSession();
                    refreshList.run();
                });
        refreshList.run();
        container.getChildren().addAll(header, btnWrapper, scrollPane);
        dlg.getDialogPane().setContent(container);
        dlg.showAndWait();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
