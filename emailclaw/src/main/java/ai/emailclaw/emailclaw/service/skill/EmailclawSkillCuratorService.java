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

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.memory.DialecticUserModelingService;
import ai.emailclaw.emailclaw.storage.AppContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.skill.WorkspaceSkillRepository;
import io.agentscope.harness.agent.skill.curator.SkillAuditLog;
import io.agentscope.harness.agent.skill.curator.SkillCurator;
import io.agentscope.harness.agent.skill.curator.SkillCuratorConfig;
import io.agentscope.harness.agent.skill.curator.SkillUsageRecord;
import io.agentscope.harness.agent.skill.curator.SkillUsageStore;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import io.agentscope.harness.agent.tool.SkillManageTool;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Host-level Hermes Skill Curator Service for Emailclaw.
 *
 * <p>Because {@code MessagePipeline} closes each {@code HarnessAgent} instance at the end of a
 * streaming turn, running {@link SkillCurator} at the application service level guarantees:
 * <ol>
 *   <li>Reliable periodic execution of AgentScope's {@link SkillCurator} Phase-1 state machine
 *       ({@code ACTIVE -> STALE -> ARCHIVED} and reactivation on new usage).</li>
 *   <li>Live LLM-driven Phase-2 <b>Umbrella Skill Consolidation</b> when 4 or more agent-created
 *       sibling skills share a domain prefix, upgrading AgentScope 2.0.4's dry-run report stub
 *       into an active skill synthesizer.</li>
 * </ol>
 */
public class EmailclawSkillCuratorService implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(EmailclawSkillCuratorService.class.getName());

    private final AppContext appContext;
    private final ProviderService providerService;
    private final ExperienceReflectionService reflectionService;
    private final ScheduledExecutorService scheduler;

    /**
     * Constructs and starts the host-level skill curator daemon service with Pure DI.
     *
     * @param appContext application persistence context
     * @param providerService model provider service
     * @param reflectionService experience reflection and draft promotion service
     */
    public EmailclawSkillCuratorService(
            AppContext appContext,
            ProviderService providerService,
            ExperienceReflectionService reflectionService) {
        this.appContext = Objects.requireNonNull(appContext, "appContext must not be null");
        this.providerService =
                Objects.requireNonNull(providerService, "providerService must not be null");
        this.reflectionService =
                Objects.requireNonNull(reflectionService, "reflectionService must not be null");
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "emailclaw-skill-curator-daemon");
                            t.setDaemon(true);
                            return t;
                        });
        this.scheduler.scheduleWithFixedDelay(
                this::curateAllAgentWorkspaces, 10, 360, TimeUnit.MINUTES);
        LOGGER.info("EmailclawSkillCuratorService initialized and scheduled background sweeps");
    }

    /**
     * Executes a full curation pass across all configured agent workspaces.
     */
    public void curateAllAgentWorkspaces() {
        LOGGER.info("Starting scheduled Hermes SkillCurator sweep across all agent workspaces");
        for (AgentInfo agent : appContext.loadAgents()) {
            try {
                ProviderInfo provider =
                        agent.getProviderId() != null
                                ? providerService.getById(agent.getProviderId()).orElse(null)
                                : null;
                Model model =
                        (provider != null
                                        && agent.getModelId() != null
                                        && !agent.getModelId().isBlank())
                                ? DialecticUserModelingService.buildChatModel(
                                        provider, agent.getModelId())
                                : null;
                curateWorkspace(agent.getId(), model);
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Skill curation failed for agent workspace: " + agent.getId(),
                        e);
            }
        }
    }

    /**
     * Runs Phase-1 automatic transitions ({@code ACTIVE -> STALE -> ARCHIVED}) and Phase-2
     * umbrella consolidation for a single agent workspace.
     *
     * @param agentId agent identifier
     * @param model optional LLM model for live umbrella skill consolidation
     * @return curator run report from AgentScope's {@link SkillCurator}
     */
    public SkillCurator.CuratorRunReport curateWorkspace(String agentId, Model model) {
        Path workspace = appContext.workspaceFor(agentId).toAbsolutePath().normalize();
        reflectionService.promoteSafeDraftsAndSyncMeta(agentId, workspace, null);

        LocalFilesystem fs = new LocalFilesystem(workspace);
        SkillUsageStore usageStore = new SkillUsageStore(fs);
        WorkspaceSkillRepository mainRepo =
                new WorkspaceSkillRepository(
                        fs, SkillManageConfig.DEFAULT_MAIN_DIR, "workspace-writable");
        SkillCuratorConfig curatorConfig =
                SkillCuratorConfig.builder()
                        .enabled(true)
                        .intervalHours(12)
                        .staleAfterDays(14)
                        .archiveAfterDays(30)
                        .umbrellaPassMode(SkillCuratorConfig.UmbrellaPassMode.DRY_RUN_ONLY)
                        .build();

        SkillCurator curator = new SkillCurator(fs, usageStore, mainRepo, curatorConfig);
        SkillCurator.CuratorRunReport report = curator.runOnce(Instant.now());
        LOGGER.log(
                Level.INFO,
                "SkillCurator Phase-1 completed for agent={0}: checked={1}, stale={2},"
                        + " archived={3}, reactivated={4}",
                new Object[] {
                    agentId,
                    report.transitions().checked(),
                    report.transitions().markedStale(),
                    report.transitions().archived(),
                    report.transitions().reactivated()
                });

        if (model != null) {
            consolidateUmbrellaClustersIfNeeded(
                    agentId, workspace, fs, usageStore, mainRepo, model);
            reflectionService.promoteSafeDraftsAndSyncMeta(agentId, workspace, null);
        }
        return report;
    }

    private void consolidateUmbrellaClustersIfNeeded(
            String agentId,
            Path workspace,
            LocalFilesystem fs,
            SkillUsageStore usageStore,
            WorkspaceSkillRepository mainRepo,
            Model model) {
        Map<String, List<String>> clusters = new LinkedHashMap<>();
        for (Map.Entry<String, SkillUsageRecord> entry : usageStore.agentCreatedReport()) {
            SkillUsageRecord rec = entry.getValue();
            if (rec == null
                    || !"agent".equals(rec.createdBy())
                    || rec.pinned()
                    || rec.state() == SkillUsageRecord.State.ARCHIVED) {
                continue;
            }
            String name = entry.getKey();
            String prefix = name.split("[-_.]", 2)[0];
            clusters.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
        }

        for (Map.Entry<String, List<String>> entry : clusters.entrySet()) {
            List<String> members = entry.getValue();
            if (members.size() < 4) {
                continue;
            }
            String prefix = entry.getKey();
            String umbrellaName = prefix + "-umbrella";
            if (Files.exists(workspace.resolve("skills").resolve(umbrellaName))) {
                continue;
            }
            runLlmUmbrellaConsolidation(
                    agentId,
                    workspace,
                    fs,
                    usageStore,
                    mainRepo,
                    prefix,
                    umbrellaName,
                    members,
                    model);
        }
    }

    private void runLlmUmbrellaConsolidation(
            String agentId,
            Path workspace,
            LocalFilesystem fs,
            SkillUsageStore usageStore,
            WorkspaceSkillRepository mainRepo,
            String prefix,
            String umbrellaName,
            List<String> members,
            Model model) {
        try {
            StringBuilder siblingMarkdown = new StringBuilder();
            for (String skillName : members) {
                AgentSkill skill = mainRepo.getSkill(skillName, RuntimeContext.empty());
                if (skill == null) {
                    continue;
                }
                siblingMarkdown
                        .append("### Sibling Skill: ")
                        .append(skillName)
                        .append("\nDescription: ")
                        .append(skill.getDescription())
                        .append("\n```markdown\n")
                        .append(skill.getSkillContent())
                        .append("\n```\n\n");
            }
            if (siblingMarkdown.isEmpty()) {
                return;
            }

            WorkspaceManager wsManager = new WorkspaceManager(workspace, fs);
            SkillManageConfig smConfig = SkillManageConfig.builder().autoPromote(true).build();
            WorkspaceSkillRepository draftsRepo =
                    new WorkspaceSkillRepository(fs, smConfig.draftsDir(), "workspace-drafts");
            SkillAuditLog auditLog = new SkillAuditLog(fs, wsManager);
            SkillManageTool skillManageTool =
                    new SkillManageTool(mainRepo, draftsRepo, smConfig, usageStore, auditLog);

            Toolkit toolkit = new Toolkit();
            toolkit.registerAgentTool(skillManageTool);

            ReActAgent consolidator =
                    ReActAgent.builder()
                            .name("hermes-umbrella-consolidator")
                            .model(model)
                            .toolkit(toolkit)
                            .maxIters(4)
                            .sysPrompt(
                                    """
                                    You are the Hermes Skill Curator Umbrella Consolidator.
                                    Multiple granular agent-created skills share the domain prefix '%s'.
                                    Consolidate them into a single clean Umbrella skill named '%s'
                                    using `skill_manage(action="create", name="%s", content=...)` with common routing in SKILL.md
                                    and specialized details in `references/<subtopic>.md` via `skill_manage(action="write_file", ...)`.
                                    """
                                            .formatted(prefix, umbrellaName, umbrellaName))
                            .build();

            Msg reply =
                    consolidator
                            .call(
                                    Msg.builder()
                                            .role(MsgRole.USER)
                                            .textContent(siblingMarkdown.toString())
                                            .build())
                            .block();
            Path umbrellaSkillMd =
                    workspace.resolve("skills").resolve(umbrellaName).resolve("SKILL.md");
            if (!Files.isRegularFile(umbrellaSkillMd)) {
                String body =
                        (reply != null
                                        && reply.getTextContent() != null
                                        && !reply.getTextContent().isBlank())
                                ? reply.getTextContent().trim()
                                : siblingMarkdown.toString();
                AgentSkill fallbackUmbrella =
                        new AgentSkill(
                                umbrellaName,
                                "Consolidated umbrella skill for domain '"
                                        + prefix
                                        + "' (combining "
                                        + String.join(", ", members)
                                        + ")",
                                body,
                                Map.of());
                mainRepo.save(List.of(fallbackUmbrella), true, RuntimeContext.empty());
                usageStore.markAgentCreated(
                        umbrellaName, "hermes-umbrella-consolidator", List.of("umbrella"));
            }
            if (Files.isRegularFile(umbrellaSkillMd)) {
                EmailclawSkillVisibilityFilter.ensureSkillMetaFile(umbrellaSkillMd.getParent());
                for (String member : members) {
                    usageStore.setState(member, SkillUsageRecord.State.ARCHIVED);
                }
            }
            LOGGER.log(
                    Level.INFO,
                    "Consolidated {0} sibling skills into umbrella skill ''{1}'' for agent={2}",
                    new Object[] {members.size(), umbrellaName, agentId});
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "Umbrella skill consolidation failed for prefix=" + prefix, e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        LOGGER.info("EmailclawSkillCuratorService shut down");
    }
}
