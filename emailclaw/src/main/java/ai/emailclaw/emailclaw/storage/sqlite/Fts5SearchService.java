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
package ai.emailclaw.emailclaw.storage.sqlite;

import ai.emailclaw.emailclaw.service.memory.MemoryScope;
import ai.emailclaw.emailclaw.util.DateTimeUtils;
import ai.emailclaw.emailclaw.util.UuidUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite FTS5 cross-session and long-term memory full-text search service.
 *
 * <p>Combines {@link CjkFtsTokenizer} (hybrid CJK bigram/unigram + Unicode61 tokenization) with
 * SQLite FTS5 BM25 ranking and exponential recency decay so that historical conversation turns and
 * structured memory notes can be recalled in sub-millisecond latency without external vector DBs.
 */
public class Fts5SearchService {

    private static final Logger LOGGER = Logger.getLogger(Fts5SearchService.class.getName());

    private static final double TIME_DECAY_ALPHA = 0.35;
    private static final double TIME_DECAY_LAMBDA = 0.05;
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final DatabaseManager databaseManager;

    /**
     * Constructs the FTS5 search service with Pure DI.
     *
     * @param databaseManager SQLite database manager
     */
    public Fts5SearchService(DatabaseManager databaseManager) {
        this.databaseManager =
                Objects.requireNonNull(databaseManager, "databaseManager must not be null");
        LOGGER.info("Fts5SearchService initialized with SQLite FTS5 + CJK hybrid tokenizer");
    }

    /**
     * Immutable search hit from historical conversation sessions.
     *
     * @param messageId unique message ID
     * @param sessionId session identifier
     * @param agentId agent identifier
     * @param projectId project identifier
     * @param sessionTitle human-readable session title
     * @param role message role ({@code user} or {@code assistant})
     * @param snippet excerpt of the matched message
     * @param rawContent full message text
     * @param createdAt epoch milliseconds when the turn was indexed
     * @param score time-decayed BM25 relevance score
     */
    public record SessionSearchHit(
            String messageId,
            String sessionId,
            String agentId,
            String projectId,
            String sessionTitle,
            String role,
            String snippet,
            String rawContent,
            long createdAt,
            double score) {}

    /**
     * Immutable search hit from structured long-term memory notes.
     *
     * @param agentId agent identifier
     * @param projectId project identifier or {@code GLOBAL}
     * @param scope memory scope ({@code GLOBAL} or {@code PROJECT})
     * @param key note key
     * @param snippet excerpt of the matched note
     * @param updatedAt epoch milliseconds when the note was last updated
     * @param score time-decayed BM25 relevance score
     */
    public record MemorySearchHit(
            String agentId,
            String projectId,
            String scope,
            String key,
            String snippet,
            long updatedAt,
            double score) {}

    /**
     * Indexes a completed conversation turn (user prompt and assistant reply) into
     * {@code session_messages} and {@code session_messages_fts}.
     *
     * @param sessionId session ID
     * @param agentId agent ID
     * @param projectId project ID (non-null)
     * @param sessionTitle session display title
     * @param userText user message text
     * @param assistantText assistant reply text
     */
    public void indexConversationTurn(
            String sessionId,
            String agentId,
            String projectId,
            String sessionTitle,
            String userText,
            String assistantText) {
        if (sessionId == null || sessionId.isBlank() || agentId == null || agentId.isBlank()) {
            return;
        }
        long now = DateTimeUtils.currentTimeMillis();
        try {
            databaseManager.executeTransaction(
                    conn -> {
                        int nextTurn = queryNextTurnIndex(conn, sessionId);
                        if (userText != null && !userText.isBlank()) {
                            insertSessionMessage(
                                    conn,
                                    UuidUtils.randomUUIDv7().toString(),
                                    sessionId,
                                    agentId,
                                    projectId,
                                    nextTurn++,
                                    "user",
                                    sessionTitle,
                                    userText,
                                    now);
                        }
                        if (assistantText != null && !assistantText.isBlank()) {
                            insertSessionMessage(
                                    conn,
                                    UuidUtils.randomUUIDv7().toString(),
                                    sessionId,
                                    agentId,
                                    projectId,
                                    nextTurn,
                                    "assistant",
                                    sessionTitle,
                                    assistantText,
                                    now + 1);
                        }
                    });
            LOGGER.log(
                    Level.INFO,
                    "Indexed conversation turn into SQLite FTS5: session={0}, agent={1}",
                    new Object[] {sessionId, agentId});
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to index conversation turn into FTS5: session=" + sessionId,
                    e);
        }
    }

    /**
     * Synchronizes a memory note into {@code memory_notes_fts}.
     *
     * @param agentId agent ID
     * @param projectId normalized project ID ({@code GLOBAL} or specific project ID)
     * @param scope memory scope
     * @param key note key
     * @param content note content
     */
    public void indexMemoryNote(
            String agentId, String projectId, MemoryScope scope, String key, String content) {
        if (agentId == null || agentId.isBlank() || key == null || key.isBlank()) {
            return;
        }
        String compositeId = buildMemoryCompositeId(agentId, projectId, scope.name(), key);
        String cjkTokens =
                CjkFtsTokenizer.tokenizeForIndex(key + " " + (content != null ? content : ""));
        try {
            databaseManager.executeTransaction(
                    conn -> {
                        try (PreparedStatement del =
                                conn.prepareStatement(
                                        "DELETE FROM memory_notes_fts WHERE note_id = ?")) {
                            del.setString(1, compositeId);
                            del.executeUpdate();
                        }
                        try (PreparedStatement ins =
                                conn.prepareStatement(
                                        """
                                        INSERT INTO memory_notes_fts
                                            (note_id, agent_id, project_id, scope, note_key_raw, note_key, cjk_tokens)
                                        VALUES (?, ?, ?, ?, ?, ?, ?)
                                        """)) {
                            ins.setString(1, compositeId);
                            ins.setString(2, agentId);
                            ins.setString(3, projectId);
                            ins.setString(4, scope.name());
                            ins.setString(5, key);
                            ins.setString(6, CjkFtsTokenizer.tokenizeForIndex(key));
                            ins.setString(7, cjkTokens);
                            ins.executeUpdate();
                        }
                    });
            LOGGER.log(
                    Level.INFO,
                    "Indexed memory note into SQLite FTS5: agent={0}, scope={1}, key={2}",
                    new Object[] {agentId, scope, key});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to index memory note into FTS5: key=" + key, e);
        }
    }

    /**
     * Removes a deleted memory note from {@code memory_notes_fts}.
     *
     * @param agentId agent ID
     * @param projectId normalized project ID
     * @param scope memory scope
     * @param key note key
     */
    public void removeMemoryNoteIndex(
            String agentId, String projectId, MemoryScope scope, String key) {
        if (agentId == null || key == null) {
            return;
        }
        String compositeId = buildMemoryCompositeId(agentId, projectId, scope.name(), key);
        try {
            databaseManager.executeUpdate(
                    conn -> {
                        try (PreparedStatement del =
                                conn.prepareStatement(
                                        "DELETE FROM memory_notes_fts WHERE note_id = ?")) {
                            del.setString(1, compositeId);
                            del.executeUpdate();
                        }
                    });
            LOGGER.log(
                    Level.INFO,
                    "Removed memory note from SQLite FTS5 index: agent={0}, key={1}",
                    new Object[] {agentId, key});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to delete memory note from FTS5: key=" + key, e);
        }
    }

    /**
     * Searches across historical conversation sessions using FTS5 BM25 and time-decay scoring.
     * Automatically falls back from conjunctive ({@code AND}) to disjunctive ({@code OR}) matching
     * when strict matching returns zero hits.
     *
     * @param query natural-language or keyword search query
     * @param agentId optional agent filter
     * @param roleFilter optional role filter ({@code user} or {@code assistant})
     * @param maxResults maximum number of hits to return
     * @return ranked list of {@link SessionSearchHit}
     */
    public List<SessionSearchHit> searchSessions(
            String query, String agentId, String roleFilter, int maxResults) {
        int limit = Math.max(1, Math.min(maxResults, 25));
        String strictFtsQuery = CjkFtsTokenizer.buildFts5MatchQuery(query, true);
        if (strictFtsQuery.isBlank()) {
            return List.of();
        }
        LOGGER.log(
                Level.INFO,
                "Executing FTS5 cross-session search: agent={0}, query={1}, ftsExpr={2}",
                new Object[] {agentId, query, strictFtsQuery});
        List<SessionSearchHit> hits =
                executeSessionFtsQuery(strictFtsQuery, agentId, roleFilter, limit * 2);
        if (hits.isEmpty()) {
            String broadFtsQuery = CjkFtsTokenizer.buildFts5MatchQuery(query, false);
            if (!broadFtsQuery.equals(strictFtsQuery)) {
                hits = executeSessionFtsQuery(broadFtsQuery, agentId, roleFilter, limit * 2);
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(SessionSearchHit::score).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Searches across structured long-term memory notes using FTS5 BM25 and time-decay scoring.
     *
     * @param query natural-language or keyword search query
     * @param agentId agent ID
     * @param projectId active project ID (non-null)
     * @param maxResults maximum number of hits to return
     * @return ranked list of {@link MemorySearchHit}
     */
    public List<MemorySearchHit> searchMemories(
            String query, String agentId, String projectId, int maxResults) {
        int limit = Math.max(1, Math.min(maxResults, 25));
        String ftsQuery = CjkFtsTokenizer.buildFts5MatchQuery(query, false);
        if (ftsQuery.isBlank()) {
            return List.of();
        }
        LOGGER.log(
                Level.INFO,
                "Executing FTS5 memory search: agent={0}, project={1}, query={2}",
                new Object[] {agentId, projectId, query});
        long now = DateTimeUtils.currentTimeMillis();
        String sql =
                """
                SELECT m.agent_id, m.project_id, m.scope, m.key, m.content, m.updated_at,
                       bm25(memory_notes_fts, 0.0, 0.0, 0.0, 0.0, 0.0, 4.0, 1.0) AS raw_bm25
                FROM memory_notes_fts f
                JOIN memory_notes m
                  ON m.agent_id = f.agent_id
                 AND m.project_id = f.project_id
                 AND m.scope = f.scope
                 AND m.key = f.note_key_raw
                WHERE memory_notes_fts MATCH ?
                  AND f.agent_id = ?
                  AND (f.scope = 'GLOBAL' OR f.project_id = ?)
                ORDER BY raw_bm25 ASC
                LIMIT ?
                """;
        List<MemorySearchHit> hits =
                databaseManager.executeQuery(
                        conn -> {
                            List<MemorySearchHit> list = new ArrayList<>();
                            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                                ps.setString(1, ftsQuery);
                                ps.setString(2, agentId != null ? agentId : "");
                                ps.setString(3, projectId);
                                ps.setInt(4, limit);
                                try (ResultSet rs = ps.executeQuery()) {
                                    while (rs.next()) {
                                        long updatedAt = rs.getLong("updated_at");
                                        double bm25 = Math.abs(rs.getDouble("raw_bm25"));
                                        double finalScore = applyTimeDecay(bm25, now, updatedAt);
                                        list.add(
                                                new MemorySearchHit(
                                                        rs.getString("agent_id"),
                                                        rs.getString("project_id"),
                                                        rs.getString("scope"),
                                                        rs.getString("key"),
                                                        truncateSnippet(
                                                                rs.getString("content"), 320),
                                                        updatedAt,
                                                        finalScore));
                                    }
                                }
                            }
                            return list;
                        });
        return hits.stream()
                .sorted(Comparator.comparingDouble(MemorySearchHit::score).reversed())
                .toList();
    }

    /**
     * Formats session search hits into a concise Markdown report for LLM consumption.
     *
     * @param query original search query
     * @param hits matched session hits
     * @return formatted Markdown text
     */
    public String formatSessionHitsForTool(String query, List<SessionSearchHit> hits) {
        if (hits.isEmpty()) {
            return "No matching historical sessions found for query: \"" + query + "\".";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### Cross-Session FTS5 Search Results for `")
                .append(query)
                .append("` (")
                .append(hits.size())
                .append(" matches)\n\n");
        for (int i = 0; i < hits.size(); i++) {
            SessionSearchHit hit = hits.get(i);
            String timeStr = TS_FORMATTER.format(Instant.ofEpochMilli(hit.createdAt()));
            sb.append(i + 1).append(". **[Session `").append(hit.sessionId()).append("`");
            if (hit.sessionTitle() != null && !hit.sessionTitle().isBlank()) {
                sb.append(" — ").append(hit.sessionTitle());
            }
            sb.append("]** (")
                    .append(hit.role())
                    .append(" @ ")
                    .append(timeStr)
                    .append(", score=")
                    .append(String.format("%.2f", hit.score()))
                    .append(")\n   > ")
                    .append(hit.snippet().replace("\n", "\n   > "))
                    .append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Formats memory note search hits into a concise Markdown report for LLM consumption.
     *
     * @param query original search query
     * @param hits matched memory hits
     * @return formatted Markdown text
     */
    public String formatMemoryHitsForTool(String query, List<MemorySearchHit> hits) {
        if (hits.isEmpty()) {
            return "No matching long-term memory notes found for query: \"" + query + "\".";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### Long-Term Memory FTS5 Search Results for `")
                .append(query)
                .append("` (")
                .append(hits.size())
                .append(" matches)\n\n");
        for (int i = 0; i < hits.size(); i++) {
            MemorySearchHit hit = hits.get(i);
            String timeStr = TS_FORMATTER.format(Instant.ofEpochMilli(hit.updatedAt()));
            sb.append(i + 1)
                    .append(". **[")
                    .append(hit.scope())
                    .append(" / `")
                    .append(hit.key())
                    .append("`]** (updated ")
                    .append(timeStr)
                    .append(", score=")
                    .append(String.format("%.2f", hit.score()))
                    .append(")\n   > ")
                    .append(hit.snippet().replace("\n", "\n   > "))
                    .append("\n\n");
        }
        return sb.toString();
    }

    private List<SessionSearchHit> executeSessionFtsQuery(
            String ftsQuery, String agentId, String roleFilter, int limit) {
        long now = DateTimeUtils.currentTimeMillis();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT m.id, m.session_id, m.agent_id, m.project_id, m.session_title,
                               m.role, m.raw_content, m.created_at,
                               bm25(session_messages_fts, 0.0, 0.0, 0.0, 0.0, 5.0, 1.0) AS raw_bm25
                        FROM session_messages_fts f
                        JOIN session_messages m ON m.id = f.message_id
                        WHERE session_messages_fts MATCH ?
                        """);
        if (agentId != null && !agentId.isBlank()) {
            sql.append(" AND m.agent_id = ?");
        }
        if (roleFilter != null && !roleFilter.isBlank()) {
            sql.append(" AND LOWER(m.role) = LOWER(?)");
        }
        sql.append(" ORDER BY raw_bm25 ASC LIMIT ?");

        try {
            return databaseManager.executeQuery(
                    conn -> {
                        List<SessionSearchHit> results = new ArrayList<>();
                        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                            int idx = 1;
                            ps.setString(idx++, ftsQuery);
                            if (agentId != null && !agentId.isBlank()) {
                                ps.setString(idx++, agentId);
                            }
                            if (roleFilter != null && !roleFilter.isBlank()) {
                                ps.setString(idx++, roleFilter);
                            }
                            ps.setInt(idx, limit);

                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    long createdAt = rs.getLong("created_at");
                                    double bm25 = Math.abs(rs.getDouble("raw_bm25"));
                                    double finalScore = applyTimeDecay(bm25, now, createdAt);
                                    String rawContent = rs.getString("raw_content");
                                    results.add(
                                            new SessionSearchHit(
                                                    rs.getString("id"),
                                                    rs.getString("session_id"),
                                                    rs.getString("agent_id"),
                                                    rs.getString("project_id"),
                                                    rs.getString("session_title"),
                                                    rs.getString("role"),
                                                    truncateSnippet(rawContent, 320),
                                                    rawContent,
                                                    createdAt,
                                                    finalScore));
                                }
                            }
                        }
                        return results;
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "FTS5 session search query failed: " + ftsQuery, e);
            return List.of();
        }
    }

    private int queryNextTurnIndex(Connection conn, String sessionId) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement(
                        "SELECT COALESCE(MAX(turn_index), -1) + 1 FROM session_messages WHERE"
                                + " session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void insertSessionMessage(
            Connection conn,
            String id,
            String sessionId,
            String agentId,
            String projectId,
            int turnIndex,
            String role,
            String sessionTitle,
            String rawContent,
            long createdAt)
            throws SQLException {
        String safeTitle = sessionTitle != null ? sessionTitle : "";
        String cjkTokens = CjkFtsTokenizer.tokenizeForIndex(safeTitle + " " + rawContent);
        try (PreparedStatement ps =
                conn.prepareStatement(
                        """
                        INSERT INTO session_messages
                            (id, session_id, agent_id, project_id, turn_index, role, session_title, raw_content, cjk_tokens, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            ps.setString(1, id);
            ps.setString(2, sessionId);
            ps.setString(3, agentId);
            ps.setString(4, projectId);
            ps.setInt(5, turnIndex);
            ps.setString(6, role);
            ps.setString(7, safeTitle);
            ps.setString(8, rawContent);
            ps.setString(9, cjkTokens);
            ps.setLong(10, createdAt);
            ps.executeUpdate();
        }
        try (PreparedStatement ftsPs =
                conn.prepareStatement(
                        """
                        INSERT INTO session_messages_fts
                            (message_id, session_id, agent_id, role, session_title, cjk_tokens)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
            ftsPs.setString(1, id);
            ftsPs.setString(2, sessionId);
            ftsPs.setString(3, agentId);
            ftsPs.setString(4, role);
            ftsPs.setString(5, CjkFtsTokenizer.tokenizeForIndex(safeTitle));
            ftsPs.setString(6, cjkTokens);
            ftsPs.executeUpdate();
        }
    }

    private static String buildMemoryCompositeId(
            String agentId, String projectId, String scope, String key) {
        return agentId + "|" + projectId + "|" + scope + "|" + key;
    }

    private static double applyTimeDecay(double bm25, long nowMillis, long docMillis) {
        double ageDays = Math.max(0.0, (double) (nowMillis - docMillis) / MILLIS_PER_DAY);
        double boost = 1.0 + TIME_DECAY_ALPHA * Math.exp(-TIME_DECAY_LAMBDA * ageDays);
        return bm25 * boost;
    }

    private static String truncateSnippet(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String cleaned = content.strip();
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        return cleaned.substring(0, maxChars) + "...";
    }
}
