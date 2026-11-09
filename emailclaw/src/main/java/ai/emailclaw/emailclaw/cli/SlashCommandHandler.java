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
package ai.emailclaw.emailclaw.cli;

import ai.emailclaw.emailclaw.ApplicationBootstrap;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.SkillInfo;
import ai.emailclaw.emailclaw.model.plan.Plan;
import ai.emailclaw.emailclaw.model.plan.SubTask;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.SkillService;
import ai.emailclaw.emailclaw.service.plan.PlanService;
import io.agentscope.core.message.Msg;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.InfoCmp;

/**
 * Handles interactive slash commands executed in the REPL loop.
 */
public class SlashCommandHandler {

    private static final Logger LOGGER = Logger.getLogger(SlashCommandHandler.class.getName());

    private final ApplicationBootstrap.BootstrapResult bootstrapResult;
    private final Terminal terminal;
    private final PrintWriter writer;

    /**
     * Constructs a slash command handler adhering to Pure DI.
     *
     * @param bootstrapResult the container of core application services
     * @param terminal the active JLine terminal
     */
    public SlashCommandHandler(
            ApplicationBootstrap.BootstrapResult bootstrapResult, Terminal terminal) {
        this.bootstrapResult = bootstrapResult;
        this.terminal = terminal;
        this.writer = terminal.writer();
    }

    /**
     * Executes the given input line as a slash command.
     *
     * @param commandLine the full command input (e.g. "/agent switch bot-1")
     * @param currentAgent the active agent
     * @param currentSession the active session
     * @param agentConsumer callback to update the active agent
     * @param sessionConsumer callback to update the active session
     * @return true if the CLI session should exit, false otherwise
     */
    public boolean handleCommand(
            String commandLine,
            AgentInfo currentAgent,
            ChatSessionInfo currentSession,
            Consumer<AgentInfo> agentConsumer,
            Consumer<ChatSessionInfo> sessionConsumer) {
        if (commandLine == null || commandLine.isBlank()) {
            return false;
        }

        String trimmed = commandLine.trim();
        String[] tokens = trimmed.split("\\s+");
        String primary = tokens[0].toLowerCase();

        LOGGER.log(Level.INFO, "Processing CLI slash command: {0}", primary);

        switch (primary) {
            case "/help", "/?" -> printHelp();
            case "/session" -> handleSession(tokens, currentAgent, currentSession, sessionConsumer);
            case "/agent" -> handleAgent(tokens, currentAgent, agentConsumer);
            case "/model" -> handleModel(tokens, currentAgent);
            case "/skills" -> handleSkills(currentAgent);
            case "/plan" -> handlePlan(currentSession);
            case "/history" -> handleHistory(tokens, currentAgent, currentSession);
            case "/clear" -> handleClear();
            case "/compact" -> handleCompact(currentAgent, currentSession);
            case "/exit", "/quit" -> {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_GREEN)
                                .append("Goodbye from Emailclaw.")
                                .toAnsi());
                writer.flush();
                return true;
            }
            default -> {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Unknown slash command: ")
                                .append(primary)
                                .style(CliTheme.STYLE_DIM)
                                .append(" (Type /help for available commands)")
                                .toAnsi());
                writer.flush();
            }
        }
        return false;
    }

    private void printHelp() {
        writer.println(CliTheme.formatHeader("Available Slash Commands"));
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /help")
                        .style(CliTheme.STYLE_DIM)
                        .append("                           Show this help message\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /session list")
                        .style(CliTheme.STYLE_DIM)
                        .append(
                                "                   List all conversation sessions for current"
                                        + " agent\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /session new")
                        .style(CliTheme.STYLE_DIM)
                        .append("                    Start a fresh conversation session\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /session switch <id>")
                        .style(CliTheme.STYLE_DIM)
                        .append("            Switch active session to specified ID\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /agent list")
                        .style(CliTheme.STYLE_DIM)
                        .append("                     List all available agents\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /agent switch <id>")
                        .style(CliTheme.STYLE_DIM)
                        .append("              Switch active agent\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /model list")
                        .style(CliTheme.STYLE_DIM)
                        .append(
                                "                     List providers [providerId (providerName)]"
                                        + " and models\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /model switch <[p/]m>")
                        .style(CliTheme.STYLE_DIM)
                        .append(
                                "          Switch model ID (e.g. /model switch"
                                        + " deepseek/deepseek-chat)\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /skills")
                        .style(CliTheme.STYLE_DIM)
                        .append(
                                "                          Inspect active workspace and pool"
                                        + " skills\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /plan")
                        .style(CliTheme.STYLE_DIM)
                        .append(
                                "                            View current plan and subtask"
                                        + " statuses\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /history [count]")
                        .style(CliTheme.STYLE_DIM)
                        .append("                 Display last n messages from current session\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /compact")
                        .style(CliTheme.STYLE_DIM)
                        .append("                        Trigger conversation memory compaction\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /clear")
                        .style(CliTheme.STYLE_DIM)
                        .append("                          Clear terminal screen\n")
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  /exit | /quit")
                        .style(CliTheme.STYLE_DIM)
                        .append("                  Exit Emailclaw CLI")
                        .toAnsi());
        writer.println();
        writer.flush();
    }

    private void handleSession(
            String[] tokens,
            AgentInfo currentAgent,
            ChatSessionInfo currentSession,
            Consumer<ChatSessionInfo> sessionConsumer) {
        ChatService chatService = bootstrapResult.chatService();
        String sub = tokens.length > 1 ? tokens[1].toLowerCase() : "list";

        if ("new".equals(sub)) {
            ChatSessionInfo newSession = chatService.newSession(currentAgent.getId());
            sessionConsumer.accept(newSession);
            writer.println(
                    new AttributedStringBuilder()
                            .style(CliTheme.STYLE_BOLD_GREEN)
                            .append("Created and switched to new session: ")
                            .style(CliTheme.STYLE_DEFAULT)
                            .append(newSession.getId())
                            .toAnsi());
        } else if ("switch".equals(sub)) {
            if (tokens.length < 3) {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Usage: /session switch <sessionId>")
                                .toAnsi());
                return;
            }
            String targetId = tokens[2].trim();
            ChatSessionInfo found = chatService.findSession(targetId);
            if (found != null) {
                sessionConsumer.accept(found);
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_GREEN)
                                .append("Switched active session to: ")
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(found.getId())
                                .append(" [")
                                .append(found.getName() != null ? found.getName() : "Untitled")
                                .append("]")
                                .toAnsi());
            } else {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Session not found: ")
                                .append(targetId)
                                .toAnsi());
            }
        } else {
            // list
            List<ChatSessionInfo> sessions = chatService.sessions(currentAgent.getId());
            writer.println(CliTheme.formatHeader("Sessions for Agent: " + currentAgent.getName()));
            if (sessions.isEmpty()) {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_DIM)
                                .append("  (No sessions found. Type /session new to create one.)")
                                .toAnsi());
            } else {
                for (ChatSessionInfo s : sessions) {
                    boolean isCurrent =
                            currentSession != null && s.getId().equals(currentSession.getId());
                    writer.println(
                            new AttributedStringBuilder()
                                    .style(
                                            isCurrent
                                                    ? CliTheme.STYLE_BOLD_GREEN
                                                    : CliTheme.STYLE_DEFAULT)
                                    .append(isCurrent ? "  * " : "    ")
                                    .append(s.getId())
                                    .style(CliTheme.STYLE_DIM)
                                    .append("  | Name: ")
                                    .style(CliTheme.STYLE_DEFAULT)
                                    .append(s.getName() != null ? s.getName() : "Untitled")
                                    .toAnsi());
                }
            }
        }
        writer.println();
        writer.flush();
    }

    private void handleAgent(
            String[] tokens, AgentInfo currentAgent, Consumer<AgentInfo> agentConsumer) {
        AgentService agentService = bootstrapResult.agentService();
        String sub = tokens.length > 1 ? tokens[1].toLowerCase() : "list";

        if ("switch".equals(sub)) {
            if (tokens.length < 3) {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Usage: /agent switch <agentId>")
                                .toAnsi());
                return;
            }
            String targetId = tokens[2].trim();
            Optional<AgentInfo> found = agentService.findById(targetId);
            if (found.isPresent()) {
                agentService.setCurrentAgent(targetId);
                agentConsumer.accept(found.get());
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_GREEN)
                                .append("Active agent switched to: ")
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(found.get().getName())
                                .append(" (")
                                .append(found.get().getId())
                                .append(")")
                                .toAnsi());
            } else {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Agent not found with ID: ")
                                .append(targetId)
                                .toAnsi());
            }
        } else {
            List<AgentInfo> agents = agentService.list();
            writer.println(CliTheme.formatHeader("Configured Agents"));
            for (AgentInfo a : agents) {
                boolean isCurrent = currentAgent != null && a.getId().equals(currentAgent.getId());
                writer.println(
                        new AttributedStringBuilder()
                                .style(
                                        isCurrent
                                                ? CliTheme.STYLE_BOLD_GREEN
                                                : CliTheme.STYLE_DEFAULT)
                                .append(isCurrent ? "  * " : "    ")
                                .append(a.getId())
                                .style(CliTheme.STYLE_DIM)
                                .append("  | Name: ")
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(a.getName() != null ? a.getName() : a.getId())
                                .style(CliTheme.STYLE_DIM)
                                .append(" | Provider: ")
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(a.getProviderId() != null ? a.getProviderId() : "default")
                                .toAnsi());
            }
        }
        writer.println();
        writer.flush();
    }

    private void handleModel(String[] tokens, AgentInfo currentAgent) {
        ProviderService providerService = bootstrapResult.providerService();
        String sub = tokens.length > 1 ? tokens[1].toLowerCase() : "list";

        if (currentAgent == null) {
            writer.println(
                    new AttributedStringBuilder()
                            .style(CliTheme.STYLE_BOLD_RED)
                            .append("No active agent selected.")
                            .toAnsi());
            return;
        }

        if ("switch".equals(sub)) {
            if (tokens.length < 3) {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Usage: /model switch <modelId>\n")
                                .append("       /model switch <providerId>/<modelId>\n")
                                .append("       /model switch <providerId>:<modelId>")
                                .toAnsi());
                return;
            }
            String rawArg = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length)).trim();
            if (rawArg.isEmpty()) {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Usage: /model switch <[providerId/]modelId>")
                                .toAnsi());
                return;
            }

            String extractedProviderId = null;
            String extractedModelId = rawArg;

            if (rawArg.contains("/")) {
                int idx = rawArg.indexOf('/');
                extractedProviderId = rawArg.substring(0, idx).trim();
                extractedModelId = rawArg.substring(idx + 1).trim();
            } else if (rawArg.contains(":")) {
                int idx = rawArg.indexOf(':');
                extractedProviderId = rawArg.substring(0, idx).trim();
                extractedModelId = rawArg.substring(idx + 1).trim();
            }

            if (extractedModelId.isEmpty()) {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Model ID cannot be empty.")
                                .toAnsi());
                return;
            }

            final String targetProviderId = extractedProviderId;
            final String targetModelId = extractedModelId;

            List<ProviderInfo> allProviders = providerService.listProviders();

            if (targetProviderId != null && !targetProviderId.isEmpty()) {
                String searchPid = targetProviderId;
                Optional<ProviderInfo> provMatch =
                        allProviders.stream()
                                .filter(p -> p.getId().equalsIgnoreCase(searchPid))
                                .findFirst();

                if (provMatch.isEmpty()) {
                    writer.println(
                            new AttributedStringBuilder()
                                    .style(CliTheme.STYLE_BOLD_RED)
                                    .append("Provider not found: '")
                                    .append(targetProviderId)
                                    .append("'. Run '/model list' to see all available providers.")
                                    .toAnsi());
                    return;
                }

                ProviderInfo provider = provMatch.get();
                Optional<ModelInfo> modelMatch =
                        provider.allModels().stream()
                                .filter(m -> m.getId().equalsIgnoreCase(targetModelId))
                                .findFirst();
                String finalModelId = modelMatch.map(ModelInfo::getId).orElse(targetModelId);

                currentAgent.setProviderId(provider.getId());
                currentAgent.setModelId(finalModelId);
                bootstrapResult.agentService().save();
                LOGGER.info(
                        "Switched agent "
                                + currentAgent.getId()
                                + " to provider "
                                + provider.getId()
                                + " and model "
                                + finalModelId);

                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_GREEN)
                                .append("Switched agent '")
                                .append(currentAgent.getName())
                                .append("' to model: ")
                                .style(CliTheme.STYLE_BOLD_CYAN)
                                .append(finalModelId)
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(" under provider: ")
                                .style(CliTheme.STYLE_BOLD_YELLOW)
                                .append(provider.getId())
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(" (")
                                .append(provider.getName())
                                .append(")")
                                .toAnsi());
                return;
            }

            // No provider prefix specified: check current provider first
            Optional<ProviderInfo> currentProvOpt =
                    providerService.getById(currentAgent.getProviderId());
            boolean existsInCurrent =
                    currentProvOpt.isPresent()
                            && currentProvOpt.get().allModels().stream()
                                    .anyMatch(m -> m.getId().equalsIgnoreCase(targetModelId));

            if (existsInCurrent) {
                ProviderInfo currentProv = currentProvOpt.get();
                Optional<ModelInfo> modelMatch =
                        currentProv.allModels().stream()
                                .filter(m -> m.getId().equalsIgnoreCase(targetModelId))
                                .findFirst();
                String finalModelId = modelMatch.map(ModelInfo::getId).orElse(targetModelId);

                currentAgent.setModelId(finalModelId);
                bootstrapResult.agentService().save();
                LOGGER.info("Switched agent " + currentAgent.getId() + " model to " + finalModelId);

                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_GREEN)
                                .append("Switched agent '")
                                .append(currentAgent.getName())
                                .append("' to model: ")
                                .style(CliTheme.STYLE_BOLD_CYAN)
                                .append(finalModelId)
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(" under provider: ")
                                .style(CliTheme.STYLE_BOLD_YELLOW)
                                .append(currentProv.getId())
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(" (")
                                .append(currentProv.getName())
                                .append(")")
                                .toAnsi());
                return;
            }

            // Search across all other providers
            String finalTargetModelId = targetModelId;
            List<ProviderInfo> matchingProviders =
                    allProviders.stream()
                            .filter(
                                    p ->
                                            p.allModels().stream()
                                                    .anyMatch(
                                                            m ->
                                                                    m.getId()
                                                                            .equalsIgnoreCase(
                                                                                    finalTargetModelId)))
                            .toList();

            if (matchingProviders.size() == 1) {
                ProviderInfo autoProv = matchingProviders.get(0);
                Optional<ModelInfo> modelMatch =
                        autoProv.allModels().stream()
                                .filter(m -> m.getId().equalsIgnoreCase(finalTargetModelId))
                                .findFirst();
                String finalModelId = modelMatch.map(ModelInfo::getId).orElse(finalTargetModelId);

                currentAgent.setProviderId(autoProv.getId());
                currentAgent.setModelId(finalModelId);
                bootstrapResult.agentService().save();
                LOGGER.info(
                        "Switched agent "
                                + currentAgent.getId()
                                + " to provider "
                                + autoProv.getId()
                                + " and model "
                                + finalModelId);

                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_GREEN)
                                .append("Switched agent '")
                                .append(currentAgent.getName())
                                .append("' to model: ")
                                .style(CliTheme.STYLE_BOLD_CYAN)
                                .append(finalModelId)
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(" and auto-switched provider to: ")
                                .style(CliTheme.STYLE_BOLD_YELLOW)
                                .append(autoProv.getId())
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(" (")
                                .append(autoProv.getName())
                                .append(")")
                                .toAnsi());
                return;
            } else if (matchingProviders.size() > 1) {
                AttributedStringBuilder errorSb =
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_RED)
                                .append("Model ID '")
                                .append(targetModelId)
                                .append("' is available under multiple providers:\n");
                for (ProviderInfo p : matchingProviders) {
                    errorSb.style(CliTheme.STYLE_DEFAULT)
                            .append("  - ")
                            .style(CliTheme.STYLE_BOLD_YELLOW)
                            .append(p.getId())
                            .style(CliTheme.STYLE_DEFAULT)
                            .append(" (")
                            .append(p.getName())
                            .append(")\n");
                }
                errorSb.style(CliTheme.STYLE_BOLD_RED)
                        .append(
                                "Please specify provider explicitly: /model switch"
                                        + " <providerId>/<modelId> or /model switch"
                                        + " <providerId>:<modelId>");
                writer.println(errorSb.toAnsi());
                return;
            } else {
                currentAgent.setModelId(targetModelId);
                bootstrapResult.agentService().save();
                LOGGER.info(
                        "Switched agent "
                                + currentAgent.getId()
                                + " model to unlisted ID "
                                + targetModelId);

                String provDesc =
                        currentProvOpt
                                .map(p -> p.getId() + " (" + p.getName() + ")")
                                .orElse(currentAgent.getProviderId());
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_YELLOW)
                                .append("Notice: Model '")
                                .append(targetModelId)
                                .append("' is not in known provider catalogs.\n")
                                .style(CliTheme.STYLE_BOLD_GREEN)
                                .append("Switched custom model for agent '")
                                .append(currentAgent.getName())
                                .append("' to: ")
                                .style(CliTheme.STYLE_BOLD_CYAN)
                                .append(targetModelId)
                                .style(CliTheme.STYLE_DEFAULT)
                                .append(" under provider: ")
                                .style(CliTheme.STYLE_BOLD_YELLOW)
                                .append(provDesc)
                                .toAnsi());
                return;
            }
        } else {
            // List providers and models
            List<ProviderInfo> providers = providerService.listProviders();
            writer.println(CliTheme.formatHeader("Configured Model Providers & Models"));
            if (providers.isEmpty()) {
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_DIM)
                                .append("  (No model providers configured)")
                                .toAnsi());
            } else {
                for (ProviderInfo provider : providers) {
                    boolean isCurrentProvider =
                            provider.getId().equalsIgnoreCase(currentAgent.getProviderId());
                    AttributedStringBuilder pHeader = new AttributedStringBuilder();
                    pHeader.style(
                                    isCurrentProvider
                                            ? CliTheme.STYLE_BOLD_GREEN
                                            : CliTheme.STYLE_BOLD_CYAN)
                            .append(isCurrentProvider ? "▶ Provider: " : "  Provider: ")
                            .style(CliTheme.STYLE_BOLD_YELLOW)
                            .append(provider.getId())
                            .style(CliTheme.STYLE_DEFAULT)
                            .append(" (")
                            .append(provider.getName())
                            .append(")")
                            .style(
                                    isCurrentProvider
                                            ? CliTheme.STYLE_BOLD_GREEN
                                            : CliTheme.STYLE_DIM)
                            .append(isCurrentProvider ? " [Active Provider]" : "");
                    writer.println(pHeader.toAnsi());

                    List<ModelInfo> models = provider.allModels();
                    if (models.isEmpty()) {
                        writer.println(
                                new AttributedStringBuilder()
                                        .style(CliTheme.STYLE_DIM)
                                        .append("      (No models configured)")
                                        .toAnsi());
                    } else {
                        for (ModelInfo m : models) {
                            boolean isCurrentModel =
                                    isCurrentProvider
                                            && m.getId()
                                                    .equalsIgnoreCase(currentAgent.getModelId());
                            writer.println(
                                    new AttributedStringBuilder()
                                            .style(
                                                    isCurrentModel
                                                            ? CliTheme.STYLE_BOLD_GREEN
                                                            : CliTheme.STYLE_DEFAULT)
                                            .append(isCurrentModel ? "    * " : "      ")
                                            .append(m.getId())
                                            .style(CliTheme.STYLE_DIM)
                                            .append("  | Name: ")
                                            .style(CliTheme.STYLE_DEFAULT)
                                            .append(m.getName())
                                            .toAnsi());
                        }
                    }
                }
                writer.println();
                writer.println(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_DIM)
                                .append("Syntax: /model switch <modelId>\n")
                                .append("        /model switch <providerId>/<modelId>\n")
                                .append("        /model switch <providerId>:<modelId>")
                                .toAnsi());
            }
        }
        writer.println();
        writer.flush();
    }

    private void handleSkills(AgentInfo currentAgent) {
        SkillService skillService = bootstrapResult.skillService();
        writer.println(CliTheme.formatHeader("Agent Skills"));

        if (currentAgent != null) {
            List<SkillInfo> workspaceSkills =
                    skillService.listWorkspaceSkills(currentAgent.getId());
            writer.println(
                    new AttributedStringBuilder()
                            .style(CliTheme.STYLE_BOLD_CYAN)
                            .append("  [Workspace Skills for ")
                            .append(currentAgent.getName())
                            .append("]")
                            .toAnsi());
            if (workspaceSkills.isEmpty()) {
                writer.println("    (No workspace-specific skills configured)");
            } else {
                for (SkillInfo s : workspaceSkills) {
                    writer.println(
                            "    - "
                                    + s.name()
                                    + " ("
                                    + (s.enabled() ? "enabled" : "disabled")
                                    + ")");
                }
            }
        }

        List<SkillInfo> poolSkills = skillService.listSkillPool();
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("  [Shared Skill Pool]")
                        .toAnsi());
        if (poolSkills.isEmpty()) {
            writer.println("    (Skill pool is empty)");
        } else {
            for (SkillInfo s : poolSkills) {
                writer.println("    - " + s.name() + " (source: " + s.source() + ")");
            }
        }
        writer.println();
        writer.flush();
    }

    private void handlePlan(ChatSessionInfo currentSession) {
        if (currentSession == null) {
            writer.println("No active session.");
            return;
        }

        PlanService planService =
                new PlanService(
                        new ai.emailclaw.emailclaw.service.plan.JsonFilePlanStore(
                                bootstrapResult.projectService()),
                        new ai.emailclaw.emailclaw.service.plan.PlanBroadcaster(
                                bootstrapResult.messageBusService()),
                        new ai.emailclaw.emailclaw.service.plan.PlanHintCache());

        Optional<Plan> planOpt = planService.getPlanBySession(currentSession.getId());
        writer.println(CliTheme.formatHeader("Session Plan"));
        if (planOpt.isEmpty()) {
            writer.println("  (No active plan generated for this session yet)");
        } else {
            Plan plan = planOpt.get();
            writer.println("  Goal: " + plan.getGoal());
            writer.println("  Subtasks:");
            for (SubTask st : plan.getSubTasks()) {
                writer.println("    [" + st.getStatus() + "] " + st.getTitle());
            }
        }
        writer.println();
        writer.flush();
    }

    private void handleHistory(
            String[] tokens, AgentInfo currentAgent, ChatSessionInfo currentSession) {
        if (currentAgent == null || currentSession == null) {
            writer.println("No active agent or session.");
            return;
        }

        int count = 5;
        if (tokens.length > 1) {
            try {
                count = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException ignored) {
                count = 5;
            }
        }

        ChatService chatService = bootstrapResult.chatService();
        List<Msg> history = chatService.loadHistory(currentAgent.getId(), currentSession.getId());
        writer.println(
                CliTheme.formatHeader(
                        "Recent History (" + Math.min(count, history.size()) + " messages)"));

        int start = Math.max(0, history.size() - count);
        for (int i = start; i < history.size(); i++) {
            Msg m = history.get(i);
            String role = chatService.roleOf(m);
            List<ChatMessagePart> parts = chatService.partsOf(m);
            String content =
                    parts.stream()
                            .filter(p -> ChatMessagePart.TEXT.equals(p.getType()))
                            .map(ChatMessagePart::getText)
                            .reduce("", (a, b) -> a + b);

            writer.println(
                    new AttributedStringBuilder()
                            .style(
                                    "user".equalsIgnoreCase(role)
                                            ? CliTheme.STYLE_BOLD_GREEN
                                            : CliTheme.STYLE_BOLD_CYAN)
                            .append("  [")
                            .append(role != null ? role.toUpperCase() : "MSG")
                            .append("]: ")
                            .style(CliTheme.STYLE_DEFAULT)
                            .append(
                                    content != null && content.length() > 200
                                            ? content.substring(0, 200) + "..."
                                            : content)
                            .toAnsi());
        }
        writer.println();
        writer.flush();
    }

    private void handleClear() {
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();
        } catch (Exception e) {
            // ANSI fallback
            writer.print("\033[H\033[2J");
            writer.flush();
        }
    }

    private void handleCompact(AgentInfo currentAgent, ChatSessionInfo currentSession) {
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_YELLOW)
                        .append("Compacting conversation memory for session: ")
                        .append(currentSession != null ? currentSession.getId() : "none")
                        .toAnsi());
        writer.println("Context compaction requested.");
        writer.println();
        writer.flush();
    }
}
