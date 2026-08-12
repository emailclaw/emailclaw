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

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.HintBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Flux;

/**
 * Memory recall middleware - injects the current memory state as a HintBlock before each reasoning step.
 *
 * <p>Reuses the pattern of {@code PlanToHintMiddleware}: loads recent memory entries from MemoryService,
 * and injects them into the message list of the reasoning input via {@link HintBlock}, ensuring the Agent is always aware of long-term memory.
 *
 * <p>Uses a memory cache to avoid repeated injection of the same memory content.
 */
public class MemoryRecallMiddleware implements MiddlewareBase {
    private static final Logger LOGGER = Logger.getLogger(MemoryRecallMiddleware.class.getName());

    private static final int MAX_MEMORY_HINTS = 5;

    private final MemoryService memoryService;

    private final ConcurrentHashMap<String, String> hintCache = new ConcurrentHashMap<>();

    public MemoryRecallMiddleware(MemoryService memoryService) {
        this.memoryService = memoryService;
        LOGGER.info("MemoryRecallMiddleware initialization completed");
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        try {
            String agentId = agent.getAgentId();
            if (agentId == null || agentId.isBlank()) {
                return next.apply(input);
            }

            List<String> keys = memoryService.listMemoryNotes(agentId);
            if (keys.isEmpty()) {
                return next.apply(input);
            }

            List<HintBlock> hints = new ArrayList<>();
            StringBuilder hintText = new StringBuilder();
            hintText.append("The following context has been recalled from long-term memory:\n");

            int count = 0;
            for (String key : keys) {
                if (count >= MAX_MEMORY_HINTS) {
                    break;
                }
                String cached = hintCache.get(key);
                var opt = memoryService.readMemoryNote(agentId, key, Object.class);
                if (opt.isPresent()) {
                    String current = opt.get().toString();
                    if (!current.equals(cached)) {
                        hintCache.put(key, current);
                        hintText.append("- ").append(key).append(": ").append(current).append("\n");
                        count++;
                    }
                }
            }

            if (count == 0) {
                return next.apply(input);
            }

            HintBlock hintBlock = new HintBlock("memory-recall", hintText.toString(), "MEMORY");
            Msg hintMsg = Msg.builder().role(MsgRole.SYSTEM).content(List.of(hintBlock)).build();
            List<Msg> newMessages = new ArrayList<>();
            newMessages.add(hintMsg);
            newMessages.addAll(input.messages());
            ReasoningInput newInput =
                    new ReasoningInput(newMessages, input.tools(), input.options());
            LOGGER.log(
                    Level.FINE,
                    "Memory hints injected: agentId={0}, keys={1}",
                    new Object[] {agentId, keys.size()});
            return next.apply(newInput);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Memory recall injection exception (skipped)", e);
            return next.apply(input);
        }
    }
}
