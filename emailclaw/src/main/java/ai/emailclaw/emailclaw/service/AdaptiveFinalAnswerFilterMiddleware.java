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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.DeliveryMode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.FinalAnswerFilterMiddleware;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Flux;

/**
 * Context-aware adaptive final answer filter middleware.
 *
 * <p>Dynamically gates intermediary text suppression based on {@link DeliveryMode} resolved from
 * {@link RuntimeContext}. When {@link DeliveryMode#FINAL} is active, it delegates to
 * {@link FinalAnswerFilterMiddleware} to suppress intermediary thinking text produced before tool calls,
 * emitting only the clean, final user-facing response. When {@link DeliveryMode#STREAM} or unconfigured
 * (e.g. interactive GUI or Console sessions), it acts as a transparent pass-through to ensure
 * unhindered real-time streaming feedback.
 */
public class AdaptiveFinalAnswerFilterMiddleware implements MiddlewareBase {

    private static final Logger LOGGER =
            Logger.getLogger(AdaptiveFinalAnswerFilterMiddleware.class.getName());

    /** Context attribute key for delivery mode. */
    public static final String CONTEXT_KEY_DELIVERY_MODE = "emailclaw.delivery_mode";

    private final FinalAnswerFilterMiddleware delegate;

    /**
     * Default constructor creating a standard {@link FinalAnswerFilterMiddleware} delegate.
     */
    public AdaptiveFinalAnswerFilterMiddleware() {
        this(new FinalAnswerFilterMiddleware());
    }

    /**
     * Pure DI constructor accepting an injected {@link FinalAnswerFilterMiddleware} instance.
     *
     * @param delegate The underlying final answer filter middleware delegate
     */
    public AdaptiveFinalAnswerFilterMiddleware(FinalAnswerFilterMiddleware delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public int order() {
        return delegate.order();
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        DeliveryMode mode = resolveDeliveryMode(ctx);
        if (mode == DeliveryMode.FINAL) {
            LOGGER.log(
                    Level.INFO,
                    "AdaptiveFinalAnswerFilterMiddleware activated for session: {0}, mode: {1}",
                    new Object[] {ctx != null ? ctx.getSessionId() : "unknown", mode});
            return delegate.onReasoning(agent, ctx, input, next);
        }
        LOGGER.log(
                Level.FINE,
                "AdaptiveFinalAnswerFilterMiddleware bypassed for session: {0}, mode: {1}",
                new Object[] {ctx != null ? ctx.getSessionId() : "unknown", mode});
        return next.apply(input);
    }

    /**
     * Resolves the effective {@link DeliveryMode} from {@link RuntimeContext}.
     *
     * @param ctx The runtime context for the current execution turn
     * @return Resolved delivery mode, defaulting to {@link DeliveryMode#STREAM} if unspecified
     */
    private DeliveryMode resolveDeliveryMode(RuntimeContext ctx) {
        if (ctx == null) {
            return DeliveryMode.STREAM;
        }
        // 1. Check typed attribute
        DeliveryMode typed = ctx.get(DeliveryMode.class);
        if (typed != null) {
            return typed;
        }
        // 2. Check namespaced string key
        Object val = ctx.get(CONTEXT_KEY_DELIVERY_MODE);
        if (val instanceof DeliveryMode dm) {
            return dm;
        } else if (val instanceof String s) {
            return DeliveryMode.fromValue(s);
        }
        // 3. Check generic string key
        Object genericVal = ctx.get("deliveryMode");
        if (genericVal instanceof DeliveryMode dm) {
            return dm;
        } else if (genericVal instanceof String s) {
            return DeliveryMode.fromValue(s);
        }
        // Fallback default: STREAM (transparent streaming for interactive channels)
        return DeliveryMode.STREAM;
    }
}
