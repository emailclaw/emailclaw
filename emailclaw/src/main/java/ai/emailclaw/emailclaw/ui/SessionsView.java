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
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Sessions list view.
 *
 * <p>Aligns with Emailclaw Control / Sessions: supports filtering by title, user ID, channel, and batch deletion after checking boxes on the left side of the table.
 */
public class SessionsView implements ViewPane {

    private static final Logger LOGGER = Logger.getLogger(SessionsView.class.getName());

    /**
     * Highlight background color for checked rows, aligned with Emailclaw selectedRow style.
     */
    private static final String SELECTED_ROW_STYLE = "-fx-background-color: #fff8f2;";

    private final ChatService chatService;

    private final ChannelService channelService;

    private AgentInfo agent;

    private final BorderPane root = new BorderPane();

    private final ObservableList<ChatSessionInfo> items = FXCollections.observableArrayList();

    private final TableView<ChatSessionInfo> table = new TableView<>(items);

    private final Consumer<ChatSessionInfo> openSessionCallback;

    private final TextField titleFilter = new TextField();

    private final TextField userFilter = new TextField();

    private final ComboBox<String> channelFilter = new ComboBox<>();

    private final Button batchDeleteBtn = new Button("Batch Delete");

    private final CheckBox selectAllCheckBox = new CheckBox();

    /**
     * Record check status by session ID, preserving existing checked items when refreshing the list.
     */
    private final Map<String, BooleanProperty> selectionById = new HashMap<>();

    private final String kind;

    public SessionsView(
            ChatService chatService,
            ChannelService channelService,
            AgentInfo agent,
            String kind,
            Consumer<ChatSessionInfo> openSessionCallback) {
        this.chatService = chatService;
        this.channelService = channelService;
        this.agent = agent;
        this.kind = kind;
        this.openSessionCallback = openSessionCallback;
        buildUi();
        refreshChannelOptions();
        refresh();
    }

    private void buildUi() {
        VBox container = new VBox(8);
        container.getStyleClass().add("page");
        container.setPadding(new Insets(14));
        Label title = new Label("Project  /  Sessions");
        title.getStyleClass().add("page-title");
        // --- Top filter bar and batch delete button ---
        titleFilter.setPromptText("Filter by Title");
        titleFilter.setPrefWidth(180);
        userFilter.setPromptText("Filter by User ID");
        userFilter.setPrefWidth(180);
        channelFilter.setPromptText("Filter by Channel");
        channelFilter.setPrefWidth(180);
        channelFilter.setMaxWidth(180);
        styleBatchDeleteButton(false);
        batchDeleteBtn.setDisable(true);
        batchDeleteBtn.setOnAction(e -> confirmBatchDelete());
        HBox toolbarSpacer = new HBox();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.getChildren()
                .addAll(toolbarSpacer, titleFilter, userFilter, channelFilter, batchDeleteBtn);
        HBox header = new HBox(8, title, toolbar);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(toolbar, Priority.ALWAYS);
        titleFilter.textProperty().addListener((obs, oldText, newText) -> applyFilters());
        userFilter.textProperty().addListener((obs, oldText, newText) -> applyFilters());
        channelFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        selectAllCheckBox.setOnAction(
                e -> {
                    boolean selectAll = selectAllCheckBox.isSelected();
                    for (ChatSessionInfo session : items) {
                        selectionProperty(session).set(selectAll);
                    }
                    updateBatchDeleteButton();
                });
        // --- Checkbox column ---
        TableColumn<ChatSessionInfo, Void> selectCol = new TableColumn<>();
        selectCol.setPrefWidth(44);
        selectCol.setMinWidth(44);
        selectCol.setMaxWidth(44);
        selectCol.setSortable(false);
        selectCol.setGraphic(selectAllCheckBox);
        selectCol.setCellFactory(
                column ->
                        new TableCell<>() {

                            private final CheckBox checkBox = new CheckBox();

                            {
                                checkBox.setOnAction(
                                        e -> {
                                            ChatSessionInfo session = getTableRow().getItem();
                                            if (session != null) {
                                                selectionProperty(session)
                                                        .set(checkBox.isSelected());
                                            }
                                        });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty
                                        || getTableRow() == null
                                        || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    return;
                                }
                                ChatSessionInfo session = getTableRow().getItem();
                                checkBox.setSelected(selectionProperty(session).get());
                                setGraphic(checkBox);
                            }
                        });
        TableColumn<ChatSessionInfo, String> idCol = column("ID", s -> s.getId());
        idCol.setPrefWidth(250);
        table.getColumns().add(selectCol);
        table.getColumns().add(idCol);
        TableColumn<ChatSessionInfo, String> nameCol = column("Name", s -> s.getName());
        nameCol.setPrefWidth(200);
        table.getColumns().add(nameCol);
        TableColumn<ChatSessionInfo, String> userIdCol = column("UserID", s -> s.getUserId());
        userIdCol.setPrefWidth(120);
        table.getColumns().add(userIdCol);
        TableColumn<ChatSessionInfo, String> channelCol = column("Channel", s -> s.getChannel());
        channelCol.setPrefWidth(100);
        table.getColumns().add(channelCol);
        TableColumn<ChatSessionInfo, String> createdCol =
                column("CreatedAt", s -> s.getCreatedAt());
        createdCol.setPrefWidth(150);
        table.getColumns().add(createdCol);
        TableColumn<ChatSessionInfo, String> updatedCol =
                column("UpdatedAt", s -> s.getUpdatedAt());
        updatedCol.setPrefWidth(150);
        table.getColumns().add(updatedCol);
        TableColumn<ChatSessionInfo, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(180);
        actionCol.setCellFactory(
                col ->
                        new TableCell<>() {

                            private final Button editBtn = new Button("Edit");

                            private final Button viewBtn = new Button("View");

                            private final Button deleteBtn = new Button("Delete");

                            private final HBox container = new HBox(6, editBtn, viewBtn, deleteBtn);

                            {
                                editBtn.getStyleClass().add("btn-blue-xs");
                                editBtn.setOnAction(
                                        e -> {
                                            ChatSessionInfo session =
                                                    getTableView().getItems().get(getIndex());
                                            showEditDialog(session);
                                        });
                                viewBtn.getStyleClass().add("btn-green-xs");
                                viewBtn.setOnAction(
                                        e -> {
                                            ChatSessionInfo session =
                                                    getTableView().getItems().get(getIndex());
                                            if (openSessionCallback != null) {
                                                openSessionCallback.accept(session);
                                            }
                                        });
                                deleteBtn.getStyleClass().add("btn-red-xs");
                                deleteBtn.setOnAction(
                                        e -> {
                                            ChatSessionInfo session =
                                                    getTableView().getItems().get(getIndex());
                                            confirmDeleteSingle(session);
                                        });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(empty ? null : container);
                            }
                        });
        table.getColumns().add(actionCol);
        table.setRowFactory(
                tv ->
                        new TableRow<>() {

                            @Override
                            protected void updateItem(ChatSessionInfo item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setStyle("");
                                    return;
                                }
                                if (selectionProperty(item).get()) {
                                    setStyle(SELECTED_ROW_STYLE);
                                } else {
                                    setStyle("");
                                }
                            }
                        });
        table.setPlaceholder(new Label("No data"));
        container.getChildren().addAll(header, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(container);
    }

    /**
     * Get or create the check property for the specified session.
     */
    private BooleanProperty selectionProperty(ChatSessionInfo session) {
        return selectionById.computeIfAbsent(
                session.getId(),
                id -> {
                    SimpleBooleanProperty property = new SimpleBooleanProperty(false);
                    property.addListener(
                            (obs, wasSelected, isSelected) -> {
                                updateBatchDeleteButton();
                                refreshSelectAllState();
                                table.refresh();
                            });
                    return property;
                });
    }

    /**
     * Refresh channel dropdown options: all Channel configurations within the project.
     */
    private void refreshChannelOptions() {
        Set<String> channels = new LinkedHashSet<>();
        for (ChannelInfo channel : channelService.list()) {
            if (channel.getId() != null && !channel.getId().isBlank()) {
                channels.add(channel.getId());
            }
        }
        // Supplement values that have appeared in sessions but are not in the channel configuration
        // to avoid historical data not being filterable.
        for (ChatSessionInfo session : chatService.sessions(agent.getId())) {
            if (session.getChannel() != null && !session.getChannel().isBlank()) {
                channels.add(session.getChannel());
            }
        }
        String previous = channelFilter.getValue();
        channelFilter.getItems().setAll(channels.stream().sorted().toList());
        if (previous != null && channelFilter.getItems().contains(previous)) {
            channelFilter.setValue(previous);
        } else {
            channelFilter.setValue(null);
        }
    }

    /**
     * Filter the current Agent's session list by title, user ID, and channel.
     */
    private void applyFilters() {
        String title = normalizeFilter(titleFilter.getText());
        String user = normalizeFilter(userFilter.getText());
        String channel = channelFilter.getValue();
        items.setAll(
                chatService.sessions(agent.getId()).stream()
                        .filter(s -> kind == null || kind.equals(s.getKind()))
                        .filter(
                                s ->
                                        title.isBlank()
                                                || normalizeFilter(s.getName()).contains(title))
                        .filter(
                                s ->
                                        user.isBlank()
                                                || normalizeFilter(s.getUserId()).contains(user))
                        .filter(
                                s ->
                                        channel == null
                                                || channel.isBlank()
                                                || channel.equals(s.getChannel()))
                        .toList());
        refreshSelectAllState();
        updateBatchDeleteButton();
    }

    private static String normalizeFilter(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    /**
     * Synchronize the state of the "Select All" checkbox in the table header.
     */
    private void refreshSelectAllState() {
        if (items.isEmpty()) {
            selectAllCheckBox.setSelected(false);
            selectAllCheckBox.setIndeterminate(false);
            return;
        }
        long selectedCount =
                items.stream().filter(session -> selectionProperty(session).get()).count();
        if (selectedCount == 0) {
            selectAllCheckBox.setSelected(false);
            selectAllCheckBox.setIndeterminate(false);
        } else if (selectedCount == items.size()) {
            selectAllCheckBox.setSelected(true);
            selectAllCheckBox.setIndeterminate(false);
        } else {
            selectAllCheckBox.setIndeterminate(true);
        }
    }

    /**
     * Update the batch delete button text and enabled status.
     */
    private void updateBatchDeleteButton() {
        int count = selectedSessionIds().size();
        batchDeleteBtn.setText(count > 0 ? "Batch Delete (" + count + ")" : "Batch Delete");
        batchDeleteBtn.setDisable(count == 0);
        styleBatchDeleteButton(count > 0);
        table.refresh();
    }

    private void styleBatchDeleteButton(boolean enabled) {
        batchDeleteBtn.getStyleClass().removeAll("btn-red-md", "btn-disabled");
        if (enabled) {
            batchDeleteBtn.getStyleClass().add("btn-red-md");
            return;
        }
        batchDeleteBtn.getStyleClass().add("btn-disabled");
    }

    /**
     * Return the list of currently checked session IDs.
     */
    private List<String> selectedSessionIds() {
        return selectionById.entrySet().stream()
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Pop up the batch delete confirmation dialog.
     */
    private void confirmBatchDelete() {
        List<String> selectedIds = selectedSessionIds();
        if (selectedIds.isEmpty()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Sessions");
        alert.setContentText(
                "Are you sure you want to delete selected "
                        + selectedIds.size()
                        + " session(s)? This action cannot be undone.");
        alert.initOwner(table.getScene() != null ? table.getScene().getWindow() : null);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            chatService.batchDeleteSessions(selectedIds);
            selectedIds.forEach(selectionById::remove);
            refresh();
        }
    }

    private void confirmDeleteSingle(ChatSessionInfo session) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Session");
        alert.setContentText(
                "Are you sure you want to delete this session? This action cannot be undone.");
        alert.initOwner(getDialogOwner());
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            chatService.deleteSession(session.getId());
            selectionById.remove(session.getId());
            refresh();
        }
    }

    private void showEditDialog(ChatSessionInfo session) {
        Dialog<ChatSessionInfo> dialog = new Dialog<>();
        dialog.setTitle("Edit Session");
        dialog.setHeaderText("Edit Chat Session Details");
        dialog.initOwner(getDialogOwner());
        ButtonType saveButtonType =
                new ButtonType("Save", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        TextField idField = new TextField(session.getId());
        idField.setEditable(false);
        idField.getStyleClass().addAll("bg-muted", "text-gray");
        TextField nameField = new TextField(session.getName());
        TextField userIdField = new TextField(session.getUserId());
        userIdField.setEditable(false);
        userIdField.getStyleClass().addAll("bg-muted", "text-gray");
        TextField channelField = new TextField(session.getChannel());
        channelField.setEditable(false);
        channelField.getStyleClass().addAll("bg-muted", "text-gray");
        TextField createdAtField = new TextField(session.getCreatedAt());
        createdAtField.setEditable(false);
        createdAtField.getStyleClass().addAll("bg-muted", "text-gray");
        TextField updatedAtField = new TextField(session.getUpdatedAt());
        updatedAtField.setEditable(false);
        updatedAtField.getStyleClass().addAll("bg-muted", "text-gray");
        grid.add(new Label("ID:"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("Name:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("User ID:"), 0, 3);
        grid.add(userIdField, 1, 3);
        grid.add(new Label("Channel:"), 0, 4);
        grid.add(channelField, 1, 4);
        grid.add(new Label("Created At:"), 0, 5);
        grid.add(createdAtField, 1, 5);
        grid.add(new Label("Updated At:"), 0, 6);
        grid.add(updatedAtField, 1, 6);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(
                dialogButton -> {
                    if (dialogButton == saveButtonType) {
                        session.setName(nameField.getText());
                        session.setUpdatedAt(LocalDateTime.now().toString());
                        return session;
                    }
                    return null;
                });
        dialog.showAndWait()
                .ifPresent(
                        updatedSession -> {
                            chatService.updateSession(updatedSession);
                            refresh();
                        });
    }

    private Window getDialogOwner() {
        return table.getScene() != null ? table.getScene().getWindow() : null;
    }

    private TableColumn<ChatSessionInfo, String> column(
            String title, Function<ChatSessionInfo, String> mapper) {
        TableColumn<ChatSessionInfo, String> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new SimpleStringProperty(mapper.apply(c.getValue())));
        return col;
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        this.agent = agent;
        refresh();
    }

    @Override
    public void refresh() {
        LOGGER.fine("Refresh session list");
        // Clean up check status for non-existent sessions.
        Set<String> existingIds =
                chatService.sessions(agent.getId()).stream()
                        .map(session -> session.getId())
                        .collect(Collectors.toSet());
        selectionById.keySet().removeIf(id -> !existingIds.contains(id));
        refreshChannelOptions();
        applyFilters();
    }
}
