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
package ai.emailclaw.emailclaw.service.memory;

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.ProviderRequestOptions;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.WorkspacePaths;
import ai.emailclaw.emailclaw.storage.sqlite.DatabaseManager;
import ai.emailclaw.emailclaw.util.DateTimeUtils;
import ai.emailclaw.emailclaw.util.UuidUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Honcho-inspired Dual-Peer Dialectic User Modeling Service.
 *
 * <p>Implements the Hegelian {@code Thesis -> Antithesis -> Synthesis} loop after conversation
 * turns:
 * <ul>
 *   <li><b>Thesis</b>: Existing high-confidence claims about the User Peer (communication style,
 *       domain expertise, workflow habits, implicit goals, decision heuristics) and Agent Peer
 *       (collaboration contract).</li>
 *   <li><b>Antithesis</b>: Implicit and explicit behavioral signals observed in the latest
 *       conversation turn (e.g., user corrections, formatting requests, tone preferences, domain
 *       depth).</li>
 *   <li><b>Synthesis</b>: Context-conditioned resolution of contradictions, updated confidence
 *       scores, and prompt-cache friendly {@code USER.md} and {@code PROFILE.md} snapshot
 *       synthesis.</li>
 * </ul>
 */
public class DialecticUserModelingService implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(DialecticUserModelingService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DatabaseManager databaseManager;
    private final AppContext appContext;
    private final ExecutorService executor;

    /**
     * Constructs the Honcho Dialectic User Modeling Service with Pure DI.
     *
     * @param databaseManager SQLite database manager
     * @param appContext application persistence context
     */
    public DialecticUserModelingService(DatabaseManager databaseManager, AppContext appContext) {
        this.databaseManager =
                Objects.requireNonNull(databaseManager, "databaseManager must not be null");
        this.appContext = Objects.requireNonNull(appContext, "appContext must not be null");
        this.executor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "emailclaw-honcho-dialectic");
                            t.setDaemon(true);
                            return t;
                        });
        LOGGER.info("DialecticUserModelingService initialized (Honcho dual-peer modeling active)");
    }

    /**
     * Immutable representation of a context-conditioned dialectic claim.
     *
     * @param id unique claim ID (UUIDv7)
     * @param agentId agent identifier
     * @param peerType peer target ({@code USER} or {@code AGENT})
     * @param dimension behavioral dimension
     * @param thesisStatement synthesized claim statement
     * @param contextCondition situational condition under which the claim holds
     * @param confidence confidence score in {@code [0.0, 1.0]}
     * @param evidenceCount number of turns supporting this claim
     * @param contradictionCount number of antithesis revisions applied
     * @param updatedAt last update timestamp (epoch millis)
     */
    public record DialecticClaim(
            String id,
            String agentId,
            String peerType,
            String dimension,
            String thesisStatement,
            String contextCondition,
            double confidence,
            int evidenceCount,
            int contradictionCount,
            long updatedAt) {}

    /**
     * Immutable prompt-cache friendly Dual-Peer profile snapshot.
     *
     * @param agentId agent identifier
     * @param userPeerMarkdown synthesized User Peer profile markdown
     * @param agentPeerMarkdown synthesized Agent Peer collaboration contract markdown
     * @param version monotonically increasing snapshot version
     * @param updatedAt last update timestamp (epoch millis)
     */
    public record PeerSnapshot(
            String agentId,
            String userPeerMarkdown,
            String agentPeerMarkdown,
            int version,
            long updatedAt) {}

    /**
     * Asynchronously schedules a post-turn dialectic reasoning pass using the agent's configured
     * provider and model.
     *
     * @param agent current agent info
     * @param provider model provider info
     * @param modelId active model ID
     * @param sessionId session ID
     * @param userMessage user input text from the completed turn
     * @param assistantMessage assistant response text from the completed turn
     */
    public void schedulePostTurnDialecticPass(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            String sessionId,
            String userMessage,
            String assistantMessage) {
        if (agent == null
                || provider == null
                || modelId == null
                || modelId.isBlank()
                || userMessage == null
                || userMessage.strip().length() < 10) {
            return;
        }
        executor.submit(
                () -> {
                    try {
                        Model model = buildChatModel(provider, modelId);
                        runDialecticSynthesis(
                                agent.getId(), sessionId, userMessage, assistantMessage, model);
                    } catch (Exception e) {
                        LOGGER.log(
                                Level.WARNING,
                                "Honcho dialectic user modeling pass failed (non-critical)",
                                e);
                    }
                });
    }

    /**
     * Loads the current prompt-cache friendly User Peer and Agent Peer snapshot for system prompt
     * injection.
     *
     * @param agentId agent identifier
     * @return current {@link PeerSnapshot} (empty fields if none synthesized yet)
     */
    public PeerSnapshot getSnapshot(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return new PeerSnapshot("", "", "", 0, 0L);
        }
        try {
            return databaseManager.executeQuery(
                    conn -> {
                        try (PreparedStatement ps =
                                conn.prepareStatement(
                                        """
                                        SELECT user_peer_markdown, agent_peer_markdown, version, updated_at
                                        FROM dialectic_peer_snapshots
                                        WHERE agent_id = ?
                                        """)) {
                            ps.setString(1, agentId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    return new PeerSnapshot(
                                            agentId,
                                            rs.getString("user_peer_markdown"),
                                            rs.getString("agent_peer_markdown"),
                                            rs.getInt("version"),
                                            rs.getLong("updated_at"));
                                }
                            }
                        }
                        return new PeerSnapshot(agentId, "", "", 0, 0L);
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read dialectic peer snapshot", e);
            return new PeerSnapshot(agentId, "", "", 0, 0L);
        }
    }

    /**
     * Lists existing dialectic claims for an agent ordered by confidence and evidence count
     * descending.
     *
     * @param agentId agent identifier
     * @param limit maximum number of claims to return
     * @return list of {@link DialecticClaim}
     */
    public List<DialecticClaim> listClaims(String agentId, int limit) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        try {
            return databaseManager.executeQuery(
                    conn -> {
                        List<DialecticClaim> claims = new ArrayList<>();
                        try (PreparedStatement ps =
                                conn.prepareStatement(
                                        """
                                        SELECT id, agent_id, peer_type, dimension, thesis_statement,
                                               context_condition, confidence, evidence_count,
                                               contradiction_count, updated_at
                                        FROM dialectic_claims
                                        WHERE agent_id = ?
                                        ORDER BY confidence DESC, evidence_count DESC
                                        LIMIT ?
                                        """)) {
                            ps.setString(1, agentId);
                            ps.setInt(2, limit);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    claims.add(
                                            new DialecticClaim(
                                                    rs.getString("id"),
                                                    rs.getString("agent_id"),
                                                    rs.getString("peer_type"),
                                                    rs.getString("dimension"),
                                                    rs.getString("thesis_statement"),
                                                    rs.getString("context_condition"),
                                                    rs.getDouble("confidence"),
                                                    rs.getInt("evidence_count"),
                                                    rs.getInt("contradiction_count"),
                                                    rs.getLong("updated_at")));
                                }
                            }
                        }
                        return claims;
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to list dialectic claims", e);
            return List.of();
        }
    }

    /**
     * Answers a deep behavioral or situational question about the User Peer using accumulated
     * dialectic claims and the optionally configured LLM reasoner.
     *
     * @param agentId agent identifier
     * @param question specific question about the user's preferences or expectations
     * @param provider optional model provider for active reasoning synthesis
     * @param modelId optional model ID for active reasoning synthesis
     * @return dialectic answer resolving user expectations
     */
    public String queryUserPeerModel(
            String agentId, String question, ProviderInfo provider, String modelId) {
        LOGGER.log(
                Level.INFO,
                "Querying Honcho Dialectic User Model: agent={0}, question={1}",
                new Object[] {agentId, question});
        List<DialecticClaim> claims = listClaims(agentId, 30);
        PeerSnapshot snapshot = getSnapshot(agentId);
        if (claims.isEmpty() && snapshot.userPeerMarkdown().isBlank()) {
            return "No dialectic user profile claims have been accumulated yet for this user.";
        }
        StringBuilder context = new StringBuilder();
        context.append("## Current User Peer Snapshot\n")
                .append(snapshot.userPeerMarkdown())
                .append("\n\n## Agent Peer Collaboration Contract\n")
                .append(snapshot.agentPeerMarkdown())
                .append("\n\n## Granular Dialectic Claims (Thesis + Context + Confidence)\n");
        for (DialecticClaim c : claims) {
            context.append("- [")
                    .append(c.peerType())
                    .append("/")
                    .append(c.dimension())
                    .append(" | context=")
                    .append(c.contextCondition())
                    .append(" | conf=")
                    .append(String.format("%.2f", c.confidence()))
                    .append(" | evidence=")
                    .append(c.evidenceCount())
                    .append("] ")
                    .append(c.thesisStatement())
                    .append("\n");
        }
        if (provider == null || modelId == null || modelId.isBlank()) {
            return context.toString();
        }
        try {
            Model model = buildChatModel(provider, modelId);
            ReActAgent reasoner =
                    ReActAgent.builder()
                            .name("honcho-dialectic-query")
                            .model(model)
                            .maxIters(1)
                            .sysPrompt(
                                    "You are the Honcho Dialectic User Modeling Reasoner. Based on"
                                            + " the accumulated Thesis/Synthesis claims and context"
                                            + " conditions below, answer the agent's question about"
                                            + " how the user prefers tasks to be handled, resolving"
                                            + " any situational nuances concisely.\n\n"
                                            + context)
                            .build();
            Msg reply =
                    reasoner.call(Msg.builder().role(MsgRole.USER).textContent(question).build())
                            .block();
            return reply != null && reply.getTextContent() != null
                    ? reply.getTextContent()
                    : context.toString();
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Fallback to raw dialectic claims due to model invocation error",
                    e);
            return context.toString();
        }
    }

    /**
     * Builds an {@link OpenAIChatModel} from Emailclaw's {@link ProviderInfo} and {@code modelId}.
     *
     * @param provider provider configuration
     * @param modelId target model identifier
     * @return configured {@link OpenAIChatModel}
     */
    public static OpenAIChatModel buildChatModel(ProviderInfo provider, String modelId) {
        ModelInfo selectedModel =
                provider.allModels().stream()
                        .filter(item -> modelId.equals(item.getId()))
                        .findFirst()
                        .orElse(null);
        Map<String, String> headers = ProviderRequestOptions.headersFor(provider);
        Map<String, Object> bodyParams =
                ProviderRequestOptions.bodyParamsFor(provider, selectedModel);
        GenerateOptions.Builder optBuilder = GenerateOptions.builder();
        if (!headers.isEmpty()) {
            optBuilder.additionalHeaders(headers);
        }
        if (!bodyParams.isEmpty()) {
            optBuilder.additionalBodyParams(bodyParams);
        }
        return OpenAIChatModel.builder()
                .apiKey(ProviderRequestOptions.apiKeyFor(provider))
                .baseUrl(provider.getBaseUrl())
                .modelName(modelId)
                .stream(false)
                .generateOptions(optBuilder.build())
                .nativeStructuredOutputWithTools(provider.isNativeStructuredOutputWithTools())
                .build();
    }

    private void runDialecticSynthesis(
            String agentId,
            String sessionId,
            String userMessage,
            String assistantMessage,
            Model model) {
        LOGGER.log(
                Level.INFO,
                "Starting Honcho post-turn dialectic synthesis: agent={0}, session={1}",
                new Object[] {agentId, sessionId});
        List<DialecticClaim> existingClaims = listClaims(agentId, 25);
        StringBuilder thesisBlock = new StringBuilder();
        if (existingClaims.isEmpty()) {
            thesisBlock.append("(No existing claims yet — initial observation phase)");
        } else {
            for (DialecticClaim c : existingClaims) {
                thesisBlock
                        .append("- id=")
                        .append(c.id())
                        .append(" | peer=")
                        .append(c.peerType())
                        .append(" | dim=")
                        .append(c.dimension())
                        .append(" | ctx=")
                        .append(c.contextCondition())
                        .append(" | conf=")
                        .append(String.format("%.2f", c.confidence()))
                        .append(" | statement=")
                        .append(c.thesisStatement())
                        .append("\n");
            }
        }

        String systemPrompt =
                """
                You are the Honcho Dialectic User Modeling Engine.
                Your task is to analyze the latest User-Assistant turn against existing Thesis claims
                and perform Hegelian Dialectic Reasoning (Thesis + Antithesis -> Synthesis).

                Rules:
                1. If the turn contains NO meaningful signal about user preferences, communication style,
                   domain expertise, workflow habits, or collaboration norms, return {"hasSignal": false}.
                2. If the user's behavior or feedback reinforces an existing claim, increment its confidence.
                3. If the user's behavior contradicts an existing claim (Antithesis), DO NOT blindly overwrite!
                   Instead, synthesize a context-conditioned claim (Synthesis) explaining WHEN each preference applies.
                4. Return ONLY valid JSON matching this schema:
                {
                  "hasSignal": true,
                  "observedAntithesis": "Brief description of new behavioral signal or correction",
                  "reasoningTrace": "How Thesis and Antithesis were synthesized",
                  "upsertedClaims": [
                    {
                      "existingId": "optional existing claim id or null",
                      "peerType": "USER or AGENT",
                      "dimension": "COMMUNICATION_STYLE | DOMAIN_EXPERTISE | WORKFLOW_HABITS | IMPLICIT_GOALS | DECISION_HEURISTICS",
                      "thesisStatement": "Synthesized claim statement",
                      "contextCondition": "When this applies (e.g., 'general', 'email_drafting', 'debugging')",
                      "confidence": 0.85
                    }
                  ],
                  "userPeerMarkdown": "Compact markdown bullet summary (< 300 words) of User Peer traits",
                  "agentPeerMarkdown": "Compact markdown bullet summary (< 180 words) of Agent Peer collaboration contract"
                }
                """;

        String userPrompt =
                "## Existing Thesis Claims\n"
                        + thesisBlock
                        + "\n\n## Latest Turn Observation\n[USER]:\n"
                        + userMessage
                        + "\n\n[ASSISTANT]:\n"
                        + (assistantMessage != null ? assistantMessage : "");

        ReActAgent synthesizer =
                ReActAgent.builder()
                        .name("honcho-dialectic-synthesizer")
                        .model(model)
                        .maxIters(1)
                        .sysPrompt(systemPrompt)
                        .build();

        Msg response =
                synthesizer
                        .call(Msg.builder().role(MsgRole.USER).textContent(userPrompt).build())
                        .block();
        if (response == null || response.getTextContent() == null) {
            return;
        }
        String jsonText = extractJsonObject(response.getTextContent());
        if (jsonText == null) {
            return;
        }
        applySynthesisResult(agentId, sessionId, jsonText);
    }

    private void applySynthesisResult(String agentId, String sessionId, String jsonText) {
        try {
            JsonNode root = MAPPER.readTree(jsonText);
            if (!root.path("hasSignal").asBoolean(false)) {
                LOGGER.log(
                        Level.FINE,
                        "Honcho dialectic pass found no new behavioral signals: session={0}",
                        sessionId);
                return;
            }
            long now = DateTimeUtils.currentTimeMillis();
            String observedAntithesis = root.path("observedAntithesis").asText("");
            String reasoningTrace = root.path("reasoningTrace").asText("");
            JsonNode claimsNode = root.path("upsertedClaims");
            String userMd = root.path("userPeerMarkdown").asText("");
            String agentMd = root.path("agentPeerMarkdown").asText("");

            databaseManager.executeTransaction(
                    conn -> {
                        if (claimsNode.isArray()) {
                            for (JsonNode c : claimsNode) {
                                String existingId =
                                        c.hasNonNull("existingId")
                                                        && !c.path("existingId").asText().isBlank()
                                                ? c.path("existingId").asText()
                                                : null;
                                String peerType = c.path("peerType").asText("USER");
                                String dimension = c.path("dimension").asText("WORKFLOW_HABITS");
                                String statement = c.path("thesisStatement").asText("");
                                String condition = c.path("contextCondition").asText("general");
                                double confidence =
                                        Math.max(
                                                0.1,
                                                Math.min(0.99, c.path("confidence").asDouble(0.7)));
                                if (statement.isBlank()) {
                                    continue;
                                }
                                upsertClaim(
                                        conn,
                                        existingId,
                                        agentId,
                                        sessionId,
                                        peerType,
                                        dimension,
                                        statement,
                                        condition,
                                        confidence,
                                        now);
                            }
                        }

                        if (!observedAntithesis.isBlank()) {
                            try (PreparedStatement ps =
                                    conn.prepareStatement(
                                            """
                                            INSERT INTO dialectic_syntheses
                                                (id, agent_id, session_id, prior_thesis, observed_antithesis, resolved_synthesis, reasoning_trace, created_at)
                                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                            """)) {
                                ps.setString(1, UuidUtils.randomUUIDv7().toString());
                                ps.setString(2, agentId);
                                ps.setString(3, sessionId != null ? sessionId : "");
                                ps.setString(4, "");
                                ps.setString(5, observedAntithesis);
                                ps.setString(6, claimsNode.toString());
                                ps.setString(7, reasoningTrace);
                                ps.setLong(8, now);
                                ps.executeUpdate();
                            }
                        }

                        if (!userMd.isBlank() || !agentMd.isBlank()) {
                            try (PreparedStatement ps =
                                    conn.prepareStatement(
                                            """
                                            INSERT INTO dialectic_peer_snapshots
                                                (agent_id, user_peer_markdown, agent_peer_markdown, version, updated_at)
                                            VALUES (?, ?, ?, 1, ?)
                                            ON CONFLICT(agent_id) DO UPDATE SET
                                                user_peer_markdown = CASE WHEN excluded.user_peer_markdown != '' THEN excluded.user_peer_markdown ELSE dialectic_peer_snapshots.user_peer_markdown END,
                                                agent_peer_markdown = CASE WHEN excluded.agent_peer_markdown != '' THEN excluded.agent_peer_markdown ELSE dialectic_peer_snapshots.agent_peer_markdown END,
                                                version = dialectic_peer_snapshots.version + 1,
                                                updated_at = excluded.updated_at
                                            """)) {
                                ps.setString(1, agentId);
                                ps.setString(2, userMd);
                                ps.setString(3, agentMd);
                                ps.setLong(4, now);
                                ps.executeUpdate();
                            }
                        }
                    });

            if (!userMd.isBlank() || !agentMd.isBlank()) {
                syncSnapshotToWorkspaceFiles(agentId, userMd, agentMd);
            }
            LOGGER.log(
                    Level.INFO,
                    "Updated Honcho Dialectic User Model for agent={0}, session={1}",
                    new Object[] {agentId, sessionId});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse/apply Honcho dialectic synthesis JSON", e);
        }
    }

    private void upsertClaim(
            Connection conn,
            String existingId,
            String agentId,
            String sessionId,
            String peerType,
            String dimension,
            String statement,
            String condition,
            double confidence,
            long now)
            throws SQLException {
        if (existingId != null) {
            try (PreparedStatement ps =
                    conn.prepareStatement(
                            """
                            UPDATE dialectic_claims
                            SET thesis_statement = ?,
                                context_condition = ?,
                                confidence = ?,
                                evidence_count = evidence_count + 1,
                                contradiction_count = CASE WHEN context_condition != ? THEN contradiction_count + 1 ELSE contradiction_count END,
                                source_session_id = ?,
                                updated_at = ?
                            WHERE id = ? AND agent_id = ?
                            """)) {
                ps.setString(1, statement);
                ps.setString(2, condition);
                ps.setDouble(3, confidence);
                ps.setString(4, condition);
                ps.setString(5, sessionId);
                ps.setLong(6, now);
                ps.setString(7, existingId);
                ps.setString(8, agentId);
                if (ps.executeUpdate() > 0) {
                    return;
                }
            }
        }
        try (PreparedStatement ps =
                conn.prepareStatement(
                        """
                        INSERT INTO dialectic_claims
                            (id, agent_id, peer_type, dimension, thesis_statement, context_condition,
                             confidence, evidence_count, contradiction_count, supporting_quotes, source_session_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, '[]', ?, ?, ?)
                        """)) {
            ps.setString(1, UuidUtils.randomUUIDv7().toString());
            ps.setString(2, agentId);
            ps.setString(3, peerType);
            ps.setString(4, dimension);
            ps.setString(5, statement);
            ps.setString(6, condition);
            ps.setDouble(7, confidence);
            ps.setString(8, sessionId);
            ps.setLong(9, now);
            ps.setLong(10, now);
            ps.executeUpdate();
        }
    }

    private void syncSnapshotToWorkspaceFiles(String agentId, String userMd, String agentMd) {
        try {
            Path workspace = appContext.workspaceFor(agentId);
            Files.createDirectories(workspace);
            if (userMd != null && !userMd.isBlank()) {
                Path userFile = workspace.resolve("USER.md");
                String content =
                        "# USER PEER DIALECTIC PROFILE (Honcho Synthesis)\n\n"
                                + userMd.strip()
                                + "\n";
                Files.writeString(userFile, content, StandardCharsets.UTF_8);
            }
            if (agentMd != null && !agentMd.isBlank()) {
                Path profileFile = workspace.resolve(WorkspacePaths.PROFILE_MD);
                String content =
                        "# AGENT PEER COLLABORATION CONTRACT (Honcho Synthesis)\n\n"
                                + agentMd.strip()
                                + "\n";
                Files.writeString(profileFile, content, StandardCharsets.UTF_8);
            }
            LOGGER.log(
                    Level.INFO,
                    "Synchronized Honcho USER.md and PROFILE.md in workspace for agent={0}",
                    agentId);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to sync USER.md / PROFILE.md to workspace", e);
        }
    }

    private static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return null;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        LOGGER.info("DialecticUserModelingService shut down");
    }
}
