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
package ai.emailclaw.emailclaw.channel.spi;

import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ProviderService;

/**
 * Unified context interface provided to plugins by the framework.
 *
 * <p>Plugins no longer directly depend on specific implementations like ChatService, AgentService, ProviderService,
 * but interact with the core system through standardized methods of this context, achieving true decoupling.
 *
 * <p>Current Phase 1 stage provides a simplified pass-through implementation, which can evolve into richer abstractions later.
 */
public interface ChannelContext {

    /**
     * Get channel configuration info for the specified ID.
     *
     * @param channelId Channel identifier (e.g. "dingtalk", "emailclaw")
     * @return Channel configuration, returns null if not found
     */
    ChannelInfo getChannelInfo(String channelId);

    /**
     * Get ChatService instance.
     *
     * <p>Phase 1 temporarily exposes ChatService directly,
     * subsequent phases may replace it with a more fine-grained MessageBroker interface.
     */
    ChatService chatService();

    /**
     * Get AgentService instance.
     */
    AgentService agentService();

    /**
     * Get ProviderService instance.
     */
    ProviderService providerService();

    /**
     * Get ChannelService instance.
     */
    ChannelService channelService();
}
