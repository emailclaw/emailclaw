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
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.ToolRuntimeContext;
import ai.emailclaw.emailclaw.service.memory.DialecticUserModelingService;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Honcho-style on-demand dialectic user model query tool.
 *
 * <p>Enables the Agent to actively query the synthesized Dual-Peer model (User Peer preferences,
 * situational conditions, and Agent Peer collaboration contract) when resolving ambiguous requests.
 */
public class UserPeerDialecticTool {

    private static final Logger LOGGER = Logger.getLogger(UserPeerDialecticTool.class.getName());

    private final DialecticUserModelingService dialecticService;
    private final ToolRuntimeContext runtimeContext;

    /**
     * Constructs a user peer dialectic query tool with Pure DI.
     *
     * @param dialecticService Honcho dialectic user modeling service
     * @param runtimeContext tool runtime context
     */
    public UserPeerDialecticTool(
            DialecticUserModelingService dialecticService, ToolRuntimeContext runtimeContext) {
        this.dialecticService =
                Objects.requireNonNull(dialecticService, "dialecticService must not be null");
        this.runtimeContext = runtimeContext;
    }

    /**
     * Queries the Honcho Dialectic User Model to resolve ambiguity about user preferences or
     * situational expectations.
     *
     * @param question specific question about how the user wants a task or situation handled
     * @return reactive {@link ToolResultBlock} containing the dialectic synthesis answer
     */
    @Tool(
            name = "query_user_model",
            description =
                    "Query the Honcho Dialectic User Model (User Peer & Agent Peer synthesis) to"
                            + " resolve ambiguity about the user's implicit preferences,"
                            + " situational communication style, formatting expectations, or"
                            + " decision heuristics.")
    public Mono<ToolResultBlock> queryUserModel(
            @ToolParam(
                            name = "question",
                            description =
                                    "Specific question about how the user would want the current"
                                            + " task or situation handled")
                    String question) {
        return Mono.fromCallable(
                        () -> {
                            if (question == null || question.isBlank()) {
                                return ToolResultBlock.error("Question must not be empty.");
                            }
                            AgentInfo agent =
                                    runtimeContext != null ? runtimeContext.currentAgent : null;
                            String agentId = agent != null ? agent.getId() : "";
                            ProviderInfo provider =
                                    (agent != null
                                                    && runtimeContext != null
                                                    && runtimeContext.providerService != null)
                                            ? runtimeContext
                                                    .providerService
                                                    .getById(agent.getProviderId())
                                                    .orElse(null)
                                            : null;
                            String modelId = agent != null ? agent.getModelId() : null;
                            LOGGER.log(
                                    Level.INFO,
                                    "Tool query_user_model invoked: agent={0}, question={1}",
                                    new Object[] {agentId, question});
                            String answer =
                                    dialecticService.queryUserPeerModel(
                                            agentId, question, provider, modelId);
                            return ToolResultBlock.text(answer);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
