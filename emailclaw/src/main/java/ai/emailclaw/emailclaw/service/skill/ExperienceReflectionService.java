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
import ai.emailclaw.emailclaw.service.SkillService;
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
import io.agentscope.harness.agent.skill.curator.SkillPromoter;
import io.agentscope.harness.agent.skill.curator.SkillUsageStore;
import io.agentscope.harness.agent.tool.ProposeSkillTool;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import io.agentscope.harness.agent.tool.SkillManageTool;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Hermes-style Post-Turn Experience Reflection and Auto-Safe Skill Promotion Service.
 *
 * <p>Executes two closed-loop operations after each completed conversation turn:
 * <ol>
 *   <li><b>Auto-Promotion &amp; Metadata Sync</b>: Scans {@code workspace/skills/_drafts/} and
 *       {@code workspace/skills/}, runs {@link SkillPromoter} with security verification, and
 *       ensures every promoted or agent-authored skill has a valid {@code .emailclaw-skill.json}
 *       file and is tracked in {@link SkillUsageStore}.</li>
 *   <li><b>Asynchronous Experience Distillation</b>: When a turn involved complex multi-step
 *       problem solving or explicit user procedural instructions, launches a background reflection
 *       sub-agent equipped with {@link SkillManageTool} and {@link ProposeSkillTool} to create or
 *       live-patch procedural skills.</li>
 * </ol>
 */
public class ExperienceReflectionService implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(ExperienceReflectionService.class.getName());

    private static final List<String> WORKFLOW_CORRECTION_SIGNALS =
            List.of(
                    "以后都按",
                    "记住这个流程",
                    "固定流程",
                    "标准步骤",
                    "沉淀成技能",
                    "保存为技能",
                    "save this workflow",
                    "always follow this procedure",
                    "remember this workflow",
                    "create a skill");

    private final AppContext appContext;
    private final SkillService skillService;
    private final ExecutorService executor;

    /**
     * Constructs the experience reflection service with Pure DI.
     *
     * @param appContext application persistence context
     * @param skillService skill management service
     */
    public ExperienceReflectionService(AppContext appContext, SkillService skillService) {
        this.appContext = Objects.requireNonNull(appContext, "appContext must not be null");
        this.skillService = Objects.requireNonNull(skillService, "skillService must not be null");
        this.executor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "emailclaw-skill-reflection");
                            t.setDaemon(true);
                            return t;
                        });
        LOGGER.info("ExperienceReflectionService initialized (Hermes experience-to-skill loop)");
    }

    /**
     * Invoked asynchronously after a streaming turn completes to promote safe skill drafts and
     * optionally distill complex troubleshooting or workflow turns into reusable skills.
     *
     * @param agent current agent info
     * @param provider model provider info
     * @param modelId active model ID
     * @param sessionId session ID
     * @param userText user message text
     * @param assistantText assistant reply text
     */
    public void onTurnCompleted(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            String sessionId,
            String userText,
            String assistantText) {
        if (agent == null || agent.getId() == null) {
            return;
        }
        executor.submit(
                () ->
                        executeTurnReflectionSync(
                                agent, provider, modelId, sessionId, userText, assistantText));
    }

    /**
     * Synchronously executes draft skill promotion and post-turn experience reflection for the
     * given turn.
     *
     * @param agent current agent info
     * @param provider model provider info
     * @param modelId active model ID
     * @param sessionId session ID
     * @param userText user message text
     * @param assistantText assistant reply text
     */
    public void executeTurnReflectionSync(
            AgentInfo agent,
            ProviderInfo provider,
            String modelId,
            String sessionId,
            String userText,
            String assistantText) {
        if (agent == null || agent.getId() == null) {
            return;
        }
        try {
            Path workspace = appContext.workspaceFor(agent.getId()).toAbsolutePath().normalize();
            promoteSafeDraftsAndSyncMeta(agent.getId(), workspace, sessionId);

            if (provider != null
                    && modelId != null
                    && !modelId.isBlank()
                    && shouldTriggerReflection(userText, assistantText)) {
                Model model = DialecticUserModelingService.buildChatModel(provider, modelId);
                runReflectionPass(
                        agent.getId(), sessionId, workspace, userText, assistantText, model);
                promoteSafeDraftsAndSyncMeta(agent.getId(), workspace, sessionId);
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "Experience reflection post-turn task failed (non-critical)", e);
        }
    }

    /**
     * Scans {@code workspace/skills/_drafts/} to promote safe drafts and ensures all directories in
     * {@code workspace/skills/} have {@code .emailclaw-skill.json} metadata.
     *
     * @param agentId agent identifier
     * @param workspace agent workspace path
     * @param sessionId optional source session ID
     */
    public void promoteSafeDraftsAndSyncMeta(String agentId, Path workspace, String sessionId) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return;
        }
        Path skillsDir = workspace.resolve("skills");
        if (!Files.isDirectory(skillsDir)) {
            return;
        }

        LocalFilesystem fs = new LocalFilesystem(workspace);
        WorkspaceManager wsManager = new WorkspaceManager(workspace, fs);
        WorkspaceSkillRepository draftsRepo =
                new WorkspaceSkillRepository(fs, SkillManageConfig.DEFAULT_DRAFTS_DIR, "drafts");
        WorkspaceSkillRepository mainRepo =
                new WorkspaceSkillRepository(fs, SkillManageConfig.DEFAULT_MAIN_DIR, "workspace");
        SkillUsageStore usageStore = new SkillUsageStore(fs);
        SkillAuditLog auditLog = new SkillAuditLog(fs, wsManager);
        EmailclawSkillVisibilityFilter gate =
                new EmailclawSkillVisibilityFilter(agentId, "all", skillService, workspace);

        SkillPromoter promoter =
                new SkillPromoter(
                        draftsRepo,
                        mainRepo,
                        wsManager,
                        usageStore,
                        gate,
                        SkillManageConfig.DEFAULT_DRAFTS_DIR,
                        SkillManageConfig.DEFAULT_MAIN_DIR,
                        auditLog);

        RuntimeContext ctx =
                sessionId != null && !sessionId.isBlank()
                        ? RuntimeContext.builder().sessionId(sessionId).build()
                        : RuntimeContext.empty();

        List<AgentSkill> drafts = draftsRepo.getAllSkills(ctx);
        for (AgentSkill draft : drafts) {
            if (draft == null || draft.getName() == null) {
                continue;
            }
            SkillPromoter.PromotionResult res =
                    promoter.promote(draft.getName(), "emailclaw-auto-promoter", ctx).block();
            if (res != null && res.status() == SkillPromoter.PromotionResult.Status.APPROVED) {
                EmailclawSkillVisibilityFilter.ensureSkillMetaFile(
                        skillsDir.resolve(draft.getName()));
                LOGGER.log(
                        Level.INFO,
                        "Promoted draft skill ''{0}'' to live workspace skills for agent={1}",
                        new Object[] {draft.getName(), agentId});
            }
        }

        // Also ensure any skill created with autoPromote=true has .emailclaw-skill.json
        try (Stream<Path> stream = Files.list(skillsDir)) {
            stream.filter(Files::isDirectory)
                    .filter(
                            p -> {
                                String n = p.getFileName().toString();
                                return !n.startsWith(".")
                                        && !n.startsWith("_")
                                        && Files.isRegularFile(p.resolve("SKILL.md"));
                            })
                    .forEach(EmailclawSkillVisibilityFilter::ensureSkillMetaFile);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to scan skillsDir for metadata sync", e);
        }
    }

    private boolean shouldTriggerReflection(String userText, String assistantText) {
        if (userText == null || assistantText == null) {
            return false;
        }
        String lowerUser = userText.toLowerCase();
        for (String signal : WORKFLOW_CORRECTION_SIGNALS) {
            if (lowerUser.contains(signal)) {
                return true;
            }
        }
        return assistantText.length() > 1800
                && (assistantText.contains("```")
                        || (assistantText.contains("1.") && assistantText.contains("3.")));
    }

    private void runReflectionPass(
            String agentId,
            String sessionId,
            Path workspace,
            String userText,
            String assistantText,
            Model model) {
        LOGGER.log(
                Level.INFO,
                "Triggering Hermes post-turn experience reflection: agent={0}, session={1}",
                new Object[] {agentId, sessionId});
        try {
            LocalFilesystem fs = new LocalFilesystem(workspace);
            WorkspaceManager wsManager = new WorkspaceManager(workspace, fs);
            SkillManageConfig smConfig = SkillManageConfig.builder().autoPromote(true).build();
            WorkspaceSkillRepository mainRepo =
                    new WorkspaceSkillRepository(fs, smConfig.mainDir(), "workspace-writable");
            WorkspaceSkillRepository draftsRepo =
                    new WorkspaceSkillRepository(fs, smConfig.draftsDir(), "workspace-drafts");
            SkillUsageStore usageStore = new SkillUsageStore(fs);
            SkillAuditLog auditLog = new SkillAuditLog(fs, wsManager);
            SkillManageTool skillManageTool =
                    new SkillManageTool(mainRepo, draftsRepo, smConfig, usageStore, auditLog);

            Toolkit toolkit = new Toolkit();
            toolkit.registerAgentTool(skillManageTool);
            toolkit.registerAgentTool(new ProposeSkillTool(skillManageTool));

            String existingSkills =
                    mainRepo.getAllSkills(RuntimeContext.empty()).stream()
                            .map(AgentSkill::getName)
                            .sorted()
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("(none)");

            String sysPrompt =
                    """
                    You are the Hermes Procedural Memory & Skill Evolution Reflector.
                    Review the completed User-Assistant interaction.
                    Existing workspace skills: %s

                    Rules:
                    1. Only create or patch a skill if the turn contains a REUSABLE, NON-TRIVIAL workflow,
                       SOP, troubleshooting sequence, or explicit user procedural rule.
                    2. If an existing skill matches the domain and needs a fix or improvement, call
                       `skill_manage` with action="patch" or action="edit".
                    3. If a genuinely new reusable procedure was established, call `skill_manage`
                       with action="create" (valid lowercase-hyphen name, YAML frontmatter with name and description,
                       and step-by-step markdown body).
                    4. If nothing warrants a reusable skill update, reply with "NO_SKILL_ACTION" without calling tools.
                    """
                            .formatted(existingSkills);

            ReActAgent reflector =
                    ReActAgent.builder()
                            .name("hermes-skill-reflector")
                            .model(model)
                            .toolkit(toolkit)
                            .maxIters(3)
                            .sysPrompt(sysPrompt)
                            .build();

            String transcript = "[USER]:\n" + userText + "\n\n[ASSISTANT]:\n" + assistantText;
            reflector
                    .call(Msg.builder().role(MsgRole.USER).textContent(transcript).build())
                    .block();
            LOGGER.log(
                    Level.INFO,
                    "Completed Hermes post-turn experience reflection: agent={0}, session={1}",
                    new Object[] {agentId, sessionId});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Post-turn skill reflection pass failed", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        LOGGER.info("ExperienceReflectionService shut down");
    }
}
