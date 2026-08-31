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
package ai.emailclaw.emailclaw.service.plan;

import ai.emailclaw.emailclaw.model.plan.Plan;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Flux;

/**
 * Plan hint middleware - injects the current plan state as a HintBlock before each reasoning step.
 *
 * <p>When there is an active plan in the session, this middleware injects the plan context
 * (pending subtasks, completed progress, etc.) as a {@link HintBlock} into the reasoning input
 * message list, making the Agent always aware of the current plan state.
 *
 * <p>Avoids repeated injection of the same plan state via {@link PlanHintCache}.
 * Generates the hint text by calling {@link PlanService#generateHint(Plan)}.
 */
public class PlanToHintMiddleware implements MiddlewareBase {
    private static final Logger LOGGER = Logger.getLogger(PlanToHintMiddleware.class.getName());

    /** Plan service reference. */
    private final PlanService planService;

    /** Hint cache. */
    private final PlanHintCache hintCache;

    /**
     * Create the plan hint middleware.
     *
     * @param planService Plan service
     * @param hintCache   Hint cache
     */
    public PlanToHintMiddleware(PlanService planService, PlanHintCache hintCache) {
        this.planService = planService;
        this.hintCache = hintCache;
        LOGGER.info("PlanToHintMiddleware initialization completed");
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        try {
            String sessionId = ctx.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                return next.apply(input);
            }
            Optional<Plan> planOpt = planService.getPlanBySession(sessionId);
            if (planOpt.isEmpty()) {
                return next.apply(input);
            }
            Plan plan = planOpt.get();
            String hintText = planService.generateHint(plan);
            if (hintText == null || hintText.isBlank()) {
                return next.apply(input);
            }
            if (!hintCache.putIfChanged(plan.getId(), hintText)) {
                return next.apply(input);
            }
            HintBlock hintBlock = new HintBlock("plan-hint", hintText, "PLAN");
            Msg hintMsg = Msg.builder().role(MsgRole.SYSTEM).content(List.of(hintBlock)).build();
            List<Msg> newMessages = new ArrayList<>();
            newMessages.add(hintMsg);
            newMessages.addAll(input.messages());
            ReasoningInput newInput =
                    new ReasoningInput(newMessages, input.tools(), input.options());
            LOGGER.log(
                    Level.FINE,
                    "Plan hint injected: sessionId={0}, planId={1}",
                    new Object[] {sessionId, plan.getId()});
            return next.apply(newInput);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Plan hint injection exception (skipped)", e);
            return next.apply(input);
        }
    }
}
