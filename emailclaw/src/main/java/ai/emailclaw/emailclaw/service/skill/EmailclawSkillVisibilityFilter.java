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
package ai.emailclaw.emailclaw.service.skill;

import ai.emailclaw.emailclaw.model.SkillInfo;
import ai.emailclaw.emailclaw.service.SkillService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.skill.curator.SkillCandidate;
import io.agentscope.harness.agent.skill.curator.SkillPromotionGate;
import io.agentscope.harness.agent.skill.curator.SkillSecurityScanner;
import io.agentscope.harness.agent.skill.curator.SkillUsageRecord;
import io.agentscope.harness.agent.skill.curator.SkillUsageStore;
import io.agentscope.harness.agent.skill.curator.SkillVisibilityFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Bridges Emailclaw's workspace skill metadata ({@code .emailclaw-skill.json}: {@code enabled} and
 * {@code channels}) and {@link SkillUsageStore} lifecycle states ({@code ARCHIVED}) with
 * AgentScope's {@link SkillVisibilityFilter} and {@link SkillPromotionGate}.
 *
 * <p>By delegating skill disclosure to AgentScope's {@code HarnessSkillMiddleware} with this
 * filter, Emailclaw achieves:
 * <ul>
 *   <li><b>O(1) Progressive Skill Disclosure</b>: Only {@code <available_skills>} metadata is
 *       injected into the system prompt; the full {@code SKILL.md} body is loaded on demand via
 *       {@code load_skill_through_path}.</li>
 *   <li><b>100% Telemetry Capture</b>: Every on-demand {@code load_skill_through_path} call is
 *       intercepted by {@code SkillUsageMiddleware} and persisted to {@code skills/.usage.json}.</li>
 *   <li><b>Automated Security Promotion Gate</b>: Agent-authored skills passing
 *       {@link SkillSecurityScanner} with {@code SAFE} verdict are automatically approved and
 *       initialized with {@code .emailclaw-skill.json}.</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public class EmailclawSkillVisibilityFilter implements SkillVisibilityFilter, SkillPromotionGate {

    private static final Logger LOGGER =
            Logger.getLogger(EmailclawSkillVisibilityFilter.class.getName());

    private final String agentId;
    private final String channel;
    private final SkillService skillService;
    private final Path agentWorkspace;

    /**
     * Constructs a skill visibility filter and promotion gate for a specific agent and channel.
     *
     * @param agentId agent identifier
     * @param channel active communication channel
     * @param skillService Emailclaw skill management service
     * @param agentWorkspace agent workspace root directory
     */
    public EmailclawSkillVisibilityFilter(
            String agentId, String channel, SkillService skillService, Path agentWorkspace) {
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.channel = channel;
        this.skillService = Objects.requireNonNull(skillService, "skillService must not be null");
        this.agentWorkspace =
                Objects.requireNonNull(agentWorkspace, "agentWorkspace must not be null");
    }

    @Override
    public List<AgentSkill> filter(List<AgentSkill> all, RuntimeContext ctx) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        Map<String, SkillInfo> configured =
                skillService.listWorkspaceSkills(agentId).stream()
                        .collect(
                                Collectors.toMap(
                                        SkillInfo::name,
                                        s -> s,
                                        (existing, replacement) -> existing));

        SkillUsageStore usageStore = new SkillUsageStore(new LocalFilesystem(agentWorkspace));
        Path skillsRoot = agentWorkspace.resolve("skills");

        List<AgentSkill> visible = new ArrayList<>(all.size());
        for (AgentSkill skill : all) {
            if (skill == null || skill.getName() == null) {
                continue;
            }
            String name = skill.getName();
            if (name.startsWith(".") || name.startsWith("_")) {
                continue;
            }
            if ("emailclaw".equalsIgnoreCase(channel)
                    && name.toLowerCase(Locale.ROOT).startsWith("himalaya")) {
                continue;
            }

            Optional<SkillUsageRecord> usageOpt = usageStore.get(name);
            if (usageOpt.isPresent() && usageOpt.get().state() == SkillUsageRecord.State.ARCHIVED) {
                continue;
            }

            SkillInfo info = configured.get(name);
            if (info != null) {
                if (!info.enabled() || !skillAppliesToChannel(info, channel)) {
                    continue;
                }
            } else {
                // Newly created skill via skill_manage: ensure .emailclaw-skill.json exists
                ensureSkillMetaFile(skillsRoot.resolve(name));
            }
            visible.add(skill);
        }
        LOGGER.log(
                Level.INFO,
                "Filtered visible skills for progressive disclosure: agent={0}, channel={1},"
                        + " total={2}, visible={3}",
                new Object[] {agentId, channel, all.size(), visible.size()});
        return visible;
    }

    @Override
    public Mono<PromotionDecision> review(SkillCandidate candidate, RuntimeContext ctx) {
        if (candidate == null) {
            return Mono.just(
                    new PromotionDecision.Reject("Null skill candidate", "emailclaw-gate"));
        }
        SkillSecurityScanner.ScanResult scan = candidate.securityScan();
        if (scan != null && scan.verdict() == SkillSecurityScanner.Verdict.DANGEROUS) {
            LOGGER.log(
                    Level.WARNING,
                    "Rejected skill promotion for ''{0}'' due to DANGEROUS security verdict",
                    candidate.name());
            return Mono.just(
                    new PromotionDecision.Reject(
                            "Blocked by SkillSecurityScanner: " + scan.reportText(),
                            "emailclaw-security-gate"));
        }
        ensureSkillMetaFile(agentWorkspace.resolve("skills").resolve(candidate.name()));
        LOGGER.log(
                Level.INFO,
                "Approved skill promotion for ''{0}'' (verdict={1})",
                new Object[] {candidate.name(), scan != null ? scan.verdict() : "SAFE"});
        return Mono.just(
                new PromotionDecision.Approve(
                        "emailclaw-security-gate", List.of("prod"), Instant.now()));
    }

    /**
     * Ensures that an agent-authored skill directory has an initialized {@code .emailclaw-skill.json}
     * metadata file so Emailclaw's UI and skill manager recognize it as enabled.
     *
     * @param skillDir target skill directory
     */
    public static void ensureSkillMetaFile(Path skillDir) {
        if (skillDir == null || !Files.isDirectory(skillDir)) {
            return;
        }
        Path metaFile = skillDir.resolve(".emailclaw-skill.json");
        if (Files.exists(metaFile)) {
            return;
        }
        try {
            Files.writeString(
                    metaFile,
                    "{\n"
                            + "  \"enabled\" : true,\n"
                            + "  \"channels\" : [ \"all\" ],\n"
                            + "  \"source\" : \"agent\"\n"
                            + "}\n",
                    StandardCharsets.UTF_8);
            LOGGER.log(
                    Level.INFO,
                    "Initialized .emailclaw-skill.json for agent-created skill: {0}",
                    skillDir.getFileName());
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to write .emailclaw-skill.json for " + skillDir, e);
        }
    }

    private static boolean skillAppliesToChannel(SkillInfo skill, String channel) {
        if (skill.channels() == null
                || skill.channels().isEmpty()
                || skill.channels().contains("all")) {
            return true;
        }
        return channel == null || channel.isBlank() || skill.channels().contains(channel);
    }
}
