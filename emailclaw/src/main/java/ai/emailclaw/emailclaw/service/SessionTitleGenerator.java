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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session title generator.
 *
 * <p>Responsible for parsing session title from assistant response and updating session metadata.
 *
 * <p>Title generation process:
 * <ol>
 *   <li>Before the first message is sent, append {@code [TITLE: xxx]} instruction to the end of the user prompt</li>
 *   <li>Assistant outputs {@code [TITLE: xxx]} marker at the end of the response</li>
 *   <li>After the streaming conversation completes, parse the marker and replace the placeholder name</li>
 * </ol>
 *
 * <p>This component is extracted from ChatService, separating the title generation logic from the core conversation logic,
 * making the title generation independently testable and maintainable.
 */
public final class SessionTitleGenerator {

    /** Logger. */
    private static final Logger LOGGER = Logger.getLogger(SessionTitleGenerator.class.getName());

    /** Application context library (data persistence). */
    private final AppContext repository;

    /**
     * Construct session title generator.
     *
     * @param repository Application context library
     */
    public SessionTitleGenerator(AppContext repository) {
        this.repository = repository;
        LOGGER.log(Level.FINE, "SessionTitleGenerator initialization completed");
    }

    /**
     * Parse and apply session title from assistant response.
     *
     * <p>LLM has output {@code [TITLE: xxx]} at the end of the first response, this method extracts it
     * and replaces the placeholder name.
     *
     * @param assistantResponse Assistant's complete response text
     * @param session           Current session
     */
    void applyTitleFromAssistantResponse(String assistantResponse, ChatSessionInfo session) {
        if (session == null || session.getId() == null) {
            LOGGER.log(Level.WARNING, "Apply session title: session info is empty");
            return;
        }
        try {
            String title = parseTitleFromResponse(assistantResponse);
            if (title.isBlank()) {
                LOGGER.log(
                        Level.FINE,
                        "Marker [TITLE: xxx] not found in response, skipping automatic title"
                                + " generation: session={0}",
                        session.getId());
                return;
            }
            List<ChatSessionInfo> sessions = new ArrayList<>(repository.loadSessions());
            Optional<ChatSessionInfo> latest =
                    sessions.stream().filter(s -> s.getId().equals(session.getId())).findFirst();
            if (latest.isEmpty()) {
                LOGGER.log(
                        Level.WARNING,
                        "Apply session title: session does not exist (may have been deleted):"
                                + " session={0}",
                        session.getId());
                return;
            }
            ChatSessionInfo current = latest.get();
            // Only overwrite if the name is still a placeholder, avoiding overwriting manually
            // modified names
            String placeholder = session.getName();
            if (!placeholder.equals(current.getName())) {
                LOGGER.log(
                        Level.FINE,
                        "Session title has been manually modified, skipping automatic generation:"
                                + " session={0}",
                        session.getId());
                return;
            }
            current.setName(title);
            current.setUpdatedAt(LocalDateTime.now().toString());
            repository.saveSessions(sessions);
            LOGGER.log(
                    Level.INFO,
                    "Automatic session title generation completed: session={0}, title={1}",
                    new Object[] {session.getId(), title});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply session title: " + session.getId(), e);
        }
    }

    /**
     * Parse {@code [TITLE: xxx]} marker from assistant response.
     *
     * @param responseText Assistant complete response text
     * @return Extracted title, returns empty string if not found
     */
    String parseTitleFromResponse(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return "";
        }
        int titleIdx = responseText.lastIndexOf("[TITLE:");
        if (titleIdx < 0) {
            return "";
        }
        int endIdx = responseText.indexOf("]", titleIdx);
        if (endIdx <= titleIdx) {
            return "";
        }
        String title = responseText.substring(titleIdx + 7, endIdx).trim();
        return cleanTitle(title);
    }

    /**
     * Clean title (remove newlines/trailing punctuation/truncate to 60 characters).
     *
     * @param title Original title
     * @return Cleaned title
     */
    private String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        String next = title.trim();
        if (next.contains("\n")) {
            next = next.substring(0, next.indexOf('\n')).trim();
        }
        while (!next.isEmpty() && ".,;:!?".indexOf(next.charAt(next.length() - 1)) >= 0) {
            next = next.substring(0, next.length() - 1).trim();
        }
        if (next.length() > 60) {
            next = next.substring(0, 60).trim();
        }
        return next;
    }

    /**
     * Truncate session name to 30 characters.
     *
     * @param prompt User prompt
     * @return Truncated session name
     */
    static String truncateSessionName(String prompt) {
        String line = (prompt == null ? "" : prompt).trim().replace("\n", " ");
        if (line.isBlank()) {
            return "New Chat";
        }
        return line.length() > 30 ? line.substring(0, 30).trim() + "…" : line;
    }
}
