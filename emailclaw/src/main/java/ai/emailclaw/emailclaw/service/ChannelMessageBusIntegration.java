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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Flux;

/**
 * Channel and Message Bus Integration Service.
 *
 * <p>Provides bridging capabilities between channels and the message bus, supporting:
 * <ul>
 *   <li>Publishing channel inbound messages to the agent inbox</li>
 *   <li>Routing agent responses back to the channel</li>
 *   <li>Cross-channel message routing</li>
 * </ul>
 */
public class ChannelMessageBusIntegration {

    private static final Logger LOGGER =
            Logger.getLogger(ChannelMessageBusIntegration.class.getName());

    /**
     * Message bus service, provides message publishing and subscription capabilities.
     */
    private final MessageBusService messageBusService;

    /**
     * Create channel and message bus integration service.
     *
     * @param messageBusService Message bus service
     */
    public ChannelMessageBusIntegration(MessageBusService messageBusService) {
        this.messageBusService = messageBusService;
        LOGGER.log(
                Level.INFO, "Channel and message bus integration service initialized successfully");
    }

    /**
     * Publish channel inbound message to agent inbox.
     *
     * <p>When a channel (e.g., Emailclaw, DingTalk) receives a user message, call this method to publish the message to the target agent's inbox.
     *
     * @param channelId   Channel ID
     * @param agentId     Target agent ID
     * @param sessionId   Session ID
     * @param userId      User ID
     * @param message     Message content
     * @param metadata    Extra metadata
     */
    public void publishInboundMessage(
            String channelId,
            String agentId,
            String sessionId,
            String userId,
            String message,
            Map<String, Object> metadata) {
        LOGGER.log(
                Level.INFO,
                "Publish channel inbound message: channel={0}, agent={1}, session={2}",
                new Object[] {channelId, agentId, sessionId});
        // Build message payload
        Map<String, Object> payload =
                Map.of(
                        "type",
                        "channel_inbound",
                        "channelId",
                        channelId,
                        "agentId",
                        agentId,
                        "userId",
                        userId != null ? userId : "",
                        "message",
                        message,
                        "timestamp",
                        System.currentTimeMillis(),
                        "metadata",
                        metadata != null ? Map.copyOf(metadata) : Map.of());
        // Publish to agent's inbox
        messageBusService.inboxPush(sessionId, payload);
        // Publish channel event
        messageBusService.sessionPublishEvent(sessionId, payload);
        LOGGER.log(
                Level.INFO,
                "Channel inbound message published: channel={0}, session={1}",
                new Object[] {channelId, sessionId});
    }

    /**
     * Publish agent response to channel.
     *
     * <p>When the agent finishes processing and generates a response, call this method to route the response back to the channel.
     *
     * @param channelId   Channel ID
     * @param sessionId   Session ID
     * @param response    Agent response
     * @param metadata    Extra metadata
     */
    public void publishOutboundMessage(
            String channelId, String sessionId, String response, Map<String, Object> metadata) {
        LOGGER.log(
                Level.INFO,
                "Publish agent response to channel: channel={0}, session={1}",
                new Object[] {channelId, sessionId});
        // Build response payload
        Map<String, Object> payload =
                Map.of(
                        "type",
                        "channel_outbound",
                        "channelId",
                        channelId,
                        "response",
                        response,
                        "timestamp",
                        System.currentTimeMillis(),
                        "metadata",
                        metadata != null ? Map.copyOf(metadata) : Map.of());
        // Publish channel event
        messageBusService.sessionPublishEvent(sessionId, payload);
        LOGGER.log(
                Level.INFO,
                "Agent response published to channel: channel={0}, session={1}",
                new Object[] {channelId, sessionId});
    }

    /**
     * Subscribe to channel inbound messages.
     *
     * @param sessionId Session ID
     * @return Inbound message flux
     */
    public Flux<Map<String, Object>> subscribeInboundMessages(String sessionId) {
        return messageBusService
                .sessionSubscribeEvents(sessionId)
                .filter(event -> "channel_inbound".equals(event.get("type")));
    }

    /**
     * Subscribe to agent response messages.
     *
     * @param sessionId Session ID
     * @return Response message flux
     */
    public Flux<Map<String, Object>> subscribeOutboundMessages(String sessionId) {
        return messageBusService
                .sessionSubscribeEvents(sessionId)
                .filter(event -> "channel_outbound".equals(event.get("type")));
    }
}
