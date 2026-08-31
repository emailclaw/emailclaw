/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 */
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.service.memory.MemoryScope;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.logging.Level;

/**
 * Tools for the Agent to actively manage long-term memory with explicit scopes.
 */
public class MemoryTools extends BaseEmailclawTool {

    public MemoryTools() {}

    @Tool(
            name = BuiltInToolNames.SAVE_GLOBAL_PREFERENCE,
            description =
                    "【CRITICAL】Use this tool to save user preferences, language settings, global"
                            + " coding styles, or any facts about the user that apply universally"
                            + " across ALL projects. Do NOT use this for project-specific business"
                            + " logic or architecture.")
    public String saveGlobalPreference(
            @ToolParam(
                            name = "key",
                            description =
                                    "A short unique snake_case string identifying the memory (e.g."
                                            + " 'coding_style', 'language_pref')")
                    String key,
            @ToolParam(name = "content", description = "The actual information to remember")
                    String content) {

        String guard = checkGuard(BuiltInToolNames.SAVE_GLOBAL_PREFERENCE, null);
        if (guard != null) return guard;

        try {
            String agentId = context.currentAgent.getId();
            String projectId = context.currentProject().getId();
            context.getMemoryService()
                    .saveMemoryNote(agentId, key, content, MemoryScope.GLOBAL, projectId);
            LOGGER.log(
                    Level.INFO,
                    "Saved global preference for {0}: {1}",
                    new Object[] {agentId, key});
            return "Global preference saved successfully.";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save global preference", e);
            return "Error saving global preference: " + e.getMessage();
        }
    }

    @Tool(
            name = BuiltInToolNames.SAVE_PROJECT_MEMORY,
            description =
                    "Use this tool to record facts, architectures, dependencies, or tasks "
                            + "that are strictly relevant to the current active project only.")
    public String saveProjectMemory(
            @ToolParam(
                            name = "key",
                            description =
                                    "A short unique snake_case string identifying the memory (e.g."
                                            + " 'architecture', 'active_bugs')")
                    String key,
            @ToolParam(name = "content", description = "The actual information to remember")
                    String content) {

        String guard = checkGuard(BuiltInToolNames.SAVE_PROJECT_MEMORY, null);
        if (guard != null) return guard;

        try {
            String agentId = context.currentAgent.getId();
            String projectId = context.currentProject().getId();
            context.getMemoryService()
                    .saveMemoryNote(agentId, key, content, MemoryScope.PROJECT, projectId);
            LOGGER.log(
                    Level.INFO, "Saved project memory for {0}: {1}", new Object[] {agentId, key});
            return "Project memory saved successfully.";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save project memory", e);
            return "Error saving project memory: " + e.getMessage();
        }
    }
}
