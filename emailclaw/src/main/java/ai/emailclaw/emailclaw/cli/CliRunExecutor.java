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
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import io.agentscope.core.message.Msg;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.ObjectMapper;

/**
 * Non-interactive one-off prompt executor for scripting, Unix pipes, and CI/CD automation.
 */
public class CliRunExecutor {

    private static final Logger LOGGER = Logger.getLogger(CliRunExecutor.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApplicationBootstrap.BootstrapResult bootstrapResult;
    private final PrintWriter writer;

    /**
     * Constructs a run executor adhering to Pure DI.
     *
     * @param bootstrapResult the container of core application services
     * @param writer the output print writer
     */
    public CliRunExecutor(
            ApplicationBootstrap.BootstrapResult bootstrapResult, PrintWriter writer) {
        this.bootstrapResult = bootstrapResult;
        this.writer = writer;
    }

    /**
     * Executes the one-off prompt according to options.
     *
     * @param options the parsed CLI options
     * @return 0 on success, non-zero on failure
     */
    public int execute(CliOptions options) {
        String prompt = options.prompt();
        if (prompt == null || prompt.isBlank()) {
            System.err.println(
                    "[Error] No prompt provided to 'run' command. Specify via argument or"
                            + " -p/--prompt.");
            return 1;
        }

        AgentService agentService = bootstrapResult.agentService();
        ChatService chatService = bootstrapResult.chatService();

        AgentInfo agent = null;
        if (options.agentId() != null && !options.agentId().isBlank()) {
            agent = agentService.findById(options.agentId()).orElse(null);
        }
        if (agent == null) {
            agent = agentService.currentDefault();
        }
        if (agent == null && !agentService.list().isEmpty()) {
            agent = agentService.list().get(0);
        }

        if (agent == null) {
            System.err.println("[Error] No agent available to execute prompt.");
            return 1;
        }

        ProviderInfo provider = chatService.resolveEffectiveProvider(agent);
        String modelId = chatService.resolveEffectiveModelId(agent, provider);
        if (provider == null || modelId == null) {
            System.err.println(
                    "[Error] No provider or model configured for agent: " + agent.getId());
            return 1;
        }

        ChatSessionInfo session = null;
        if (options.sessionId() != null && !options.sessionId().isBlank()) {
            session = chatService.findSession(options.sessionId());
        }
        if (session == null) {
            session = chatService.newSession(agent.getId());
        }

        LOGGER.log(
                Level.INFO,
                "Executing one-off CLI prompt with agent={0}, model={1}, session={2}",
                new Object[] {agent.getId(), modelId, session.getId()});

        List<ChatMessagePart> collectedParts = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        if (options.jsonOutput()) {
            chatService.sendMessage(
                    agent,
                    provider,
                    modelId,
                    session,
                    prompt,
                    new ai.emailclaw.emailclaw.service.StreamCallback() {
                        @Override
                        public void onPart(ChatMessagePart part, boolean startsNew) {
                            if (part != null) {
                                collectedParts.add(part);
                            }
                        }

                        @Override
                        public void onCompleted(Msg message) {
                            completed.set(true);
                        }
                    });

            try {
                String jsonStr = JSON.writeValueAsString(collectedParts);
                writer.println(jsonStr);
                writer.flush();
            } catch (Exception e) {
                System.err.println("[Error] Failed to format JSON output: " + e.getMessage());
                return 1;
            }
        } else {
            CliStreamRenderer renderer = new CliStreamRenderer(writer, options.quiet());
            chatService.sendMessage(agent, provider, modelId, session, prompt, renderer);
        }

        return 0;
    }
}
