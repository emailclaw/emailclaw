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

/**
 * Unified model for inbound messages passed from plugins to the framework.
 *
 * <p>Different channels have different raw message formats (DingTalk JSON, Email RFC822, etc.),
 * plugins are responsible for parsing them into this standardized structure before handing them to {@link ChannelContext}.
 *
 * @param channelId      Channel identifier (e.g. "dingtalk", "emailclaw")
 * @param conversationId External conversation identifier (DingTalk's conversationId, Email's SessionID, etc.)
 * @param senderId       Sender identifier (User ID or email address)
 * @param content        Message text content
 * @param subject        Message subject (only used by email-like channels, other channels can be null)
 */
public record InboundMessage(
        String channelId, String conversationId, String senderId, String content, String subject) {
    /**
     * Create a simple message without a subject (suitable for instant messaging channels).
     */
    public static InboundMessage simple(
            String channelId, String conversationId, String senderId, String content) {
        return new InboundMessage(channelId, conversationId, senderId, content, null);
    }
}
