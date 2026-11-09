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
import ai.emailclaw.emailclaw.Launcher;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.storage.AppPaths;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jline.reader.EndOfFileException;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

/**
 * Interactive Read-Eval-Print Loop (REPL) session runner for Emailclaw CLI.
 *
 * <p>Orchestrates rich terminal user interactions using JLine 4, providing syntax highlighting,
 * tab autocompletion, persistent history, streaming token feedback, and interactive safety gates.
 */
public class CliSessionRunner {

    private static final Logger LOGGER = Logger.getLogger(CliSessionRunner.class.getName());

    private final ApplicationBootstrap.BootstrapResult bootstrapResult;
    private final Terminal terminal;
    private final LineReader lineReader;
    private final History history;
    private final AppPaths paths;
    private final SlashCommandHandler slashCommandHandler;
    private final CliApprovalHandler approvalHandler;
    private final PrintWriter writer;

    private AgentInfo currentAgent;
    private ChatSessionInfo currentSession;
    private final AtomicBoolean turnActive = new AtomicBoolean(false);

    /**
     * Constructs a session runner adhering to Pure DI.
     *
     * @param bootstrapResult the container of core application services
     * @param terminal the active JLine terminal
     * @param lineReader the configured JLine line reader
     * @param history the JLine history component
     * @param paths application paths configuration
     */
    public CliSessionRunner(
            ApplicationBootstrap.BootstrapResult bootstrapResult,
            Terminal terminal,
            LineReader lineReader,
            History history,
            AppPaths paths) {
        this.bootstrapResult = bootstrapResult;
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.history = history;
        this.paths = paths;
        this.writer = terminal.writer();
        this.slashCommandHandler = new SlashCommandHandler(bootstrapResult, terminal);
        this.approvalHandler =
                new CliApprovalHandler(
                        bootstrapResult.chatService(),
                        bootstrapResult.chatService().getGovernanceService(),
                        terminal.writer());
    }

    /**
     * Initializes the default active agent and session, then starts the REPL loop.
     */
    public void start() {
        AgentService agentService = bootstrapResult.agentService();
        ChatService chatService = bootstrapResult.chatService();

        this.currentAgent = agentService.currentDefault();
        if (this.currentAgent == null && !agentService.list().isEmpty()) {
            this.currentAgent = agentService.list().get(0);
        }

        if (this.currentAgent != null) {
            List<ChatSessionInfo> sessions = chatService.sessions(this.currentAgent.getId());
            if (!sessions.isEmpty()) {
                this.currentSession = sessions.get(0);
            } else {
                this.currentSession = chatService.newSession(this.currentAgent.getId());
            }
        }

        LOGGER.log(
                Level.INFO,
                "Starting Emailclaw CLI interactive REPL session with agent={0}, session={1}",
                new Object[] {
                    currentAgent != null ? currentAgent.getId() : "none",
                    currentSession != null ? currentSession.getId() : "none"
                });

        ProviderInfo provider =
                currentAgent != null
                        ? bootstrapResult
                                .providerService()
                                .getById(currentAgent.getProviderId())
                                .orElse(null)
                        : null;
        String modelName = currentAgent != null ? currentAgent.getModelId() : "auto";

        CliTheme.printBanner(
                writer,
                Launcher.APP_VERSION,
                currentAgent != null ? currentAgent.getName() : "default",
                modelName,
                paths.root.toString());

        runLoop();
    }

    private void runLoop() {
        ChatService chatService = bootstrapResult.chatService();
        ProviderService providerService = bootstrapResult.providerService();

        boolean running = true;
        while (running) {
            String sessionPreview =
                    currentSession != null
                            ? (currentSession.getName() != null
                                            && !currentSession.getName().isBlank()
                                    ? currentSession
                                            .getName()
                                            .substring(
                                                    0,
                                                    Math.min(30, currentSession.getName().length()))
                                    : currentSession
                                            .getId()
                                            .substring(
                                                    0,
                                                    Math.min(8, currentSession.getId().length())))
                            : "main";

            String prompt =
                    CliTheme.buildPrompt(
                            currentAgent != null ? currentAgent.getName() : "default",
                            sessionPreview);

            String line;
            try {
                line = lineReader.readLine(prompt);
            } catch (UserInterruptException e) {
                // User pressed Ctrl+C
                if (turnActive.get()) {
                    if (currentSession != null) {
                        chatService.cancelRun(currentSession.getId());
                    }
                    writer.println("\n[Interrupted] Turn execution cancelled.");
                    writer.flush();
                } else {
                    writer.println("\nType /exit or press Ctrl+D to exit Emailclaw CLI.");
                    writer.flush();
                }
                continue;
            } catch (EndOfFileException e) {
                // User pressed Ctrl+D
                writer.println("\nExiting Emailclaw CLI.");
                writer.flush();
                break;
            }

            if (line == null) {
                break;
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("/")) {
                boolean exit =
                        slashCommandHandler.handleCommand(
                                line,
                                currentAgent,
                                currentSession,
                                updatedAgent -> this.currentAgent = updatedAgent,
                                updatedSession -> this.currentSession = updatedSession);
                if (exit) {
                    running = false;
                }
                continue;
            }

            // Normal prompt execution turn
            if (currentAgent == null) {
                writer.println("[Error] No active agent configured. Use /agent switch <id>");
                writer.flush();
                continue;
            }

            if (currentSession == null) {
                currentSession = chatService.newSession(currentAgent.getId());
            }

            ProviderInfo provider = chatService.resolveEffectiveProvider(currentAgent);
            String modelId = chatService.resolveEffectiveModelId(currentAgent, provider);

            if (provider == null || modelId == null) {
                writer.println(
                        "[Error] No model provider configured for current agent. Check settings.");
                writer.flush();
                continue;
            }

            turnActive.set(true);
            try {
                CliStreamRenderer renderer = new CliStreamRenderer(writer, false);

                // Run approval watcher in background
                Thread approvalWatcher = startApprovalWatcher(currentSession.getId());

                chatService.sendMessage(
                        currentAgent, provider, modelId, currentSession, line, renderer);

                if (approvalWatcher != null) {
                    approvalWatcher.interrupt();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error occurred during CLI message turn execution", e);
                writer.println("\n[Error] Turn failed: " + e.getMessage());
                writer.flush();
            } finally {
                turnActive.set(false);
            }
        }

        saveHistorySafely();
    }

    private Thread startApprovalWatcher(String sessionId) {
        Thread watcher =
                new Thread(
                        () -> {
                            while (turnActive.get()) {
                                try {
                                    approvalHandler.checkAndHandlePending(sessionId, lineReader);
                                    Thread.sleep(800);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        });
        watcher.setDaemon(true);
        watcher.start();
        return watcher;
    }

    private void saveHistorySafely() {
        if (history != null) {
            try {
                history.save();
                LOGGER.log(
                        Level.INFO, "CLI history saved successfully to: {0}", paths.cliHistoryFile);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to persist CLI command history", e);
            }
        }
    }
}
