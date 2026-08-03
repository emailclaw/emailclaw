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

import ai.emailclaw.emailclaw.model.ChatMessageRoles;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.service.ChatService;
import io.agentscope.core.message.Msg;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Full session search sidebar panel.
 *
 * <p>Aligns with Emailclaw {@code ChatSearchPanel}: enter keywords in the right drawer, serially scan all sessions of the current Agent,
 * displaying title hits and message body hits, and supports clicking results to jump to the corresponding session.
 */
public final class ChatSearchPanel {

    private static final Logger LOGGER = Logger.getLogger(ChatSearchPanel.class.getName());

    /**
     * Sidebar width, consistent with Emailclaw Drawer width=360.
     */
    private static final double PANEL_WIDTH = 360;

    /**
     * Context length to keep around the keyword.
     */
    private static final int CONTEXT_LENGTH = 80;

    /**
     * Input debounce delay (milliseconds).
     */
    private static final Duration DEBOUNCE = Duration.millis(300);

    private final ChatService chatService;

    private final VBox root = new VBox(0);

    private final TextField searchInput = new TextField();

    private final Label statusLabel = new Label();

    private final VBox resultsBox = new VBox(0);

    private final ScrollPane resultsScroll = new ScrollPane();

    private final ProgressIndicator loadingIndicator = new ProgressIndicator();

    private final PauseTransition debounceTimer = new PauseTransition(DEBOUNCE);

    /**
     * Monotonically increasing sequence number, used to discard expired asynchronous search results.
     */
    private final AtomicLong searchSequence = new AtomicLong(0);

    private String agentId = "";

    private String currentKind = ChatSessionInfo.KIND_CHAT;

    private Consumer<ChatSessionInfo> sessionSelectHandler = session -> {};

    private Runnable closeHandler = () -> {};

    /**
     * @param chatService Chat service, used to enumerate sessions and load history
     */
    public ChatSearchPanel(ChatService chatService) {
        this.chatService = chatService;
        buildUi();
        wireEvents();
    }

    /**
     * Returns the root node that can be directly overlaid on the right side of the chat area.
     */
    public VBox root() {
        return root;
    }

    /**
     * Set the current Agent, search scope is limited to the session list of this Agent.
     */
    public void setAgentId(String agentId, String kind) {
        this.agentId = agentId == null ? "" : agentId;
        this.currentKind = kind == null ? ChatSessionInfo.KIND_CHAT : kind;
    }

    /**
     * Register session selected callback (usually used to switch the current session in ChatView).
     */
    public void setOnSessionSelected(Consumer<ChatSessionInfo> handler) {
        this.sessionSelectHandler = handler == null ? session -> {} : handler;
    }

    /**
     * Register close panel callback.
     */
    public void setOnClose(Runnable handler) {
        this.closeHandler = handler == null ? () -> {} : handler;
    }

    /**
     * Open the panel and focus the search box.
     */
    public void open() {
        root.setVisible(true);
        root.setManaged(true);
        Platform.runLater(
                () -> {
                    searchInput.requestFocus();
                    searchInput.selectAll();
                });
    }

    /**
     * Close the panel and clear temporary state.
     */
    public void close() {
        debounceTimer.stop();
        searchSequence.incrementAndGet();
        searchInput.clear();
        resultsBox.getChildren().clear();
        statusLabel.setText("");
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        root.setVisible(false);
        root.setManaged(false);
    }

    private void buildUi() {
        root.setPrefWidth(PANEL_WIDTH);
        root.setMinWidth(PANEL_WIDTH);
        root.setMaxWidth(PANEL_WIDTH);
        root.setStyle(
                "-fx-background-color: #ffffff;"
                        + "-fx-border-color: #e5e7eb;"
                        + "-fx-border-width: 0 0 0 1;"
                        + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 12, 0, -2, 0);");
        root.setVisible(false);
        root.setManaged(false);
        // --- Title bar ---
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 16, 16, 20));
        Label title = new Label("Search Chat");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 500; -fx-text-fill: #111827;");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Button closeBtn = new Button("→|");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-border-color: transparent;"
                        + "-fx-text-fill: #64748b; -fx-font-size: 14px; -fx-cursor: hand;");
        closeBtn.setOnAction(
                e -> {
                    close();
                    closeHandler.run();
                });
        header.getChildren().addAll(title, headerSpacer, closeBtn);
        // --- Search input ---
        VBox searchSection = new VBox(0);
        searchSection.setPadding(new Insets(12, 16, 12, 16));
        searchInput.setPromptText("Search messages...");
        searchInput.setStyle(
                "-fx-background-radius: 8; -fx-border-radius: 8;"
                        + "-fx-border-color: #d1d5db; -fx-padding: 8 12;"
                        + "-fx-font-size: 13px;");
        searchSection.getChildren().add(searchInput);
        // --- Status row (result count / search progress) ---
        statusLabel.setPadding(new Insets(8, 16, 8, 16));
        statusLabel.getStyleClass().addAll("text-12", "text-slate");
        statusLabel.setWrapText(true);
        // --- Result list ---
        resultsBox.getStyleClass().add("bg-white");
        resultsScroll.setContent(resultsBox);
        resultsScroll.setFitToWidth(true);
        resultsScroll.getStyleClass().addAll("bg-transparent", "border-trans");
        resultsScroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(resultsScroll, Priority.ALWAYS);
        loadingIndicator.setMaxSize(28, 28);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        VBox loadingWrap = new VBox(loadingIndicator);
        loadingWrap.setAlignment(Pos.CENTER);
        loadingWrap.setPadding(new Insets(24));
        root.getChildren().addAll(header, searchSection, statusLabel, loadingWrap, resultsScroll);
    }

    private void wireEvents() {
        debounceTimer.setOnFinished(e -> startSearch(searchInput.getText()));
        searchInput.textProperty().addListener((obs, oldText, newText) -> scheduleSearch(newText));
        searchInput.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        close();
                        closeHandler.run();
                    }
                });
    }

    /**
     * Debounce scheduling: trigger search after user stops typing for {@link #DEBOUNCE}.
     */
    private void scheduleSearch(String query) {
        debounceTimer.stop();
        if (query == null || query.trim().isEmpty()) {
            searchSequence.incrementAndGet();
            resultsBox.getChildren().clear();
            statusLabel.setText("");
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);
            return;
        }
        debounceTimer.playFromStart();
    }

    /**
     * Serially scan all sessions in a background virtual thread to avoid memory peaks caused by one-time loading.
     */
    private void startSearch(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty() || agentId.isBlank()) {
            return;
        }
        long seq = searchSequence.incrementAndGet();
        Platform.runLater(
                () -> {
                    loadingIndicator.setVisible(true);
                    loadingIndicator.setManaged(true);
                    resultsBox.getChildren().clear();
                    statusLabel.setText("Loading...");
                });
        Thread.startVirtualThread(
                () -> {
                    try {
                        List<SearchResult> results = searchAllSessions(seq, query);
                        if (seq != searchSequence.get()) {
                            return;
                        }
                        Platform.runLater(() -> renderResults(query, results, false));
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Chat search failed", ex);
                        if (seq == searchSequence.get()) {
                            Platform.runLater(
                                    () -> {
                                        resultsBox.getChildren().clear();
                                        statusLabel.setText("Search failed");
                                        loadingIndicator.setVisible(false);
                                        loadingIndicator.setManaged(false);
                                    });
                        }
                    }
                });
    }

    /**
     * Serially traverse sessions: first match title, then load history item by item and match message body.
     *
     * @param seq   Current search sequence number
     * @param query Original search keyword (case-insensitive)
     */
    private List<SearchResult> searchAllSessions(long seq, String query) {
        String lowerQuery = query.toLowerCase();
        List<ChatSessionInfo> sessions =
                chatService.sessions(agentId).stream()
                        .filter(
                                s ->
                                        currentKind.equals(
                                                s.getKind() != null
                                                        ? s.getKind()
                                                        : ChatSessionInfo.KIND_CHAT))
                        .toList();
        List<SearchResult> results = new ArrayList<>();
        int total = sessions.size();
        for (int index = 0; index < total; index++) {
            if (seq != searchSequence.get()) {
                return results;
            }
            ChatSessionInfo session = sessions.get(index);
            String chatName =
                    session.getName() == null || session.getName().isBlank()
                            ? "New Chat"
                            : session.getName();
            String timestamp = pickSessionTimestamp(session);
            int current = index + 1;
            Platform.runLater(
                    () -> statusLabel.setText("Searching " + current + "/" + total + "..."));
            // Title hit
            if (chatName.toLowerCase().contains(lowerQuery)) {
                results.add(new SearchResult(session, "Title", chatName, timestamp));
            }
            // Message body hit
            try {
                List<Msg> history = chatService.loadHistory(agentId, session.getId());
                if (seq != searchSequence.get()) {
                    return results;
                }
                for (Msg message : history) {
                    String text = extractSearchableText(message);
                    if (text.isEmpty()) {
                        continue;
                    }
                    String lowerText = text.toLowerCase();
                    if (!lowerText.contains(lowerQuery)) {
                        continue;
                    }
                    int matchIndex = lowerText.indexOf(lowerQuery);
                    results.add(
                            new SearchResult(
                                    session,
                                    roleLabel(chatService.roleOf(message)),
                                    buildSnippet(text, matchIndex, query.length()),
                                    timestamp));
                }
            } catch (Exception ex) {
                LOGGER.log(
                        Level.FINE,
                        "Failed to load session history, skipping: sessionId={0}",
                        session.getId());
            }
            // Refresh intermediate results every 5 sessions to improve responsiveness when
            // searching long lists
            if ((index + 1) % 5 == 0 || index == total - 1) {
                if (seq != searchSequence.get()) {
                    return results;
                }
                List<SearchResult> snapshot = sortResults(results);
                Platform.runLater(() -> renderResults(query, snapshot, true));
            }
        }
        return sortResults(results);
    }

    /**
     * Extract plain text for searching.
     */
    private static String extractSearchableText(Msg message) {
        if (message == null) {
            return "";
        }
        String text = message.getTextContent();
        return text == null ? "" : text;
    }

    /**
     * Return Emailclaw aligned role label text based on role.
     */
    private static String roleLabel(String role) {
        if (ChatMessageRoles.USER.equalsIgnoreCase(role)) {
            return "User";
        }
        return "Assistant";
    }

    /**
     * Build matched snippet with ellipses.
     */
    private static String buildSnippet(String text, int matchIndex, int queryLength) {
        int start = Math.max(0, matchIndex - CONTEXT_LENGTH);
        int end = Math.min(text.length(), matchIndex + queryLength + CONTEXT_LENGTH);
        String snippet = text.substring(start, end);
        return start > 0 ? "..." + snippet : snippet;
    }

    /**
     * Sort in descending order of session time, consistent with Emailclaw.
     */
    private static List<SearchResult> sortResults(List<SearchResult> results) {
        List<SearchResult> sorted = new ArrayList<>(results);
        sorted.sort(
                Comparator.comparing(
                                (SearchResult item) -> parseTimestamp(item.timestamp),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.chatName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // Try to truncate to minute precision
        }
        try {
            if (normalized.length() >= 16) {
                return LocalDateTime.parse(normalized.substring(0, 16));
            }
        } catch (DateTimeParseException ignored) {
            LOGGER.fine("Failed to parse timestamp: " + raw);
        }
        return null;
    }

    private static String pickSessionTimestamp(ChatSessionInfo session) {
        if (session.getUpdatedAt() != null && !session.getUpdatedAt().isBlank()) {
            return session.getUpdatedAt();
        }
        return session.getCreatedAt();
    }

    private static String formatTimestamp(String raw) {
        LocalDateTime dateTime = parseTimestamp(raw);
        if (dateTime == null) {
            if (raw == null) {
                return "";
            }
            String display = raw.replace('T', ' ');
            return display.length() > 16 ? display.substring(0, 16) : display;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private void renderResults(String query, List<SearchResult> results, boolean partial) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        loadingIndicator.setVisible(partial);
        loadingIndicator.setManaged(partial);
        if (partial) {
            statusLabel.setText("Searching...");
        } else {
            statusLabel.setText("Found " + results.size() + " result(s)");
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);
        }
        resultsBox.getChildren().clear();
        if (results.isEmpty() && !partial) {
            Label empty = new Label("No results found");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-padding: 32 16;");
            resultsBox.getChildren().add(empty);
            return;
        }
        for (SearchResult result : results) {
            resultsBox.getChildren().add(buildResultRow(result));
        }
    }

    private VBox buildResultRow(SearchResult result) {
        VBox row = new VBox(6);
        row.setPadding(new Insets(12));
        row.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8; -fx-cursor: hand;");
        row.setOnMouseEntered(
                e ->
                        row.setStyle(
                                "-fx-background-color: #f8fafc; -fx-background-radius: 8;"
                                        + " -fx-cursor: hand;"));
        row.setOnMouseExited(
                e ->
                        row.setStyle(
                                "-fx-background-color: #ffffff; -fx-background-radius: 8;"
                                        + " -fx-cursor: hand;"));
        row.setOnMouseClicked(
                e -> {
                    sessionSelectHandler.accept(result.session);
                    close();
                    closeHandler.run();
                });
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label chatName = new Label(result.chatName);
        chatName.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #111827;");
        chatName.setMaxWidth(PANEL_WIDTH - 120);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label roleBadge = new Label(result.roleLabel);
        roleBadge.setStyle(
                "-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill:"
                        + " #64748b;-fx-background-color: #f3f4f6; -fx-background-radius: 4;"
                        + " -fx-padding: 2 8;");
        header.getChildren().addAll(chatName, spacer, roleBadge);
        Label snippet = new Label(result.matchedText);
        snippet.setWrapText(false);
        snippet.setMaxWidth(PANEL_WIDTH - 40);
        snippet.getStyleClass().add("text-13-slate");
        snippet.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        row.getChildren().add(header);
        row.getChildren().add(snippet);
        String timeText = formatTimestamp(result.timestamp);
        if (!timeText.isBlank()) {
            Label time = new Label(timeText);
            time.getStyleClass().addAll("text-12", "text-muted");
            row.getChildren().add(time);
        }
        return row;
    }

    /**
     * Data carrier for a single search result.
     */
    private static final class SearchResult {

        private final ChatSessionInfo session;

        private final String chatName;

        private final String roleLabel;

        private final String matchedText;

        private final String timestamp;

        private SearchResult(
                ChatSessionInfo session, String roleLabel, String matchedText, String timestamp) {
            this.session = session;
            this.chatName =
                    session.getName() == null || session.getName().isBlank()
                            ? "New Chat"
                            : session.getName();
            this.roleLabel = roleLabel;
            this.matchedText = matchedText;
            this.timestamp = timestamp;
        }
    }
}
