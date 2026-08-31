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
package ai.emailclaw.emailclaw.plugin.channel.dingtalk;

/**
 * JSON key names and default value constants used when synchronizing {@code pluginConfig} and flattened fields for DingTalk channel.
 */
public final class DingTalkChannelConfigKeys {

    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String SHOW_TOOL_MESSAGES = "showToolMessages";
    public static final String SHOW_THINKING = "showThinking";
    public static final String MESSAGE_TYPE = "messageType";
    public static final String CRON_MESSAGE_TYPE = "cronMessageType";
    public static final String AT_SENDER_ON_REPLY = "atSenderOnReply";
    public static final String DM_POLICY = "dmPolicy";
    public static final String GROUP_POLICY = "groupPolicy";
    public static final String REQUIRE_MENTION = "requireMention";
    public static final String ALLOWLIST_USERS = "allowlistUsers";

    /** Default message body format: Markdown. */
    public static final String DEFAULT_MESSAGE_TYPE = "markdown";

    /** Access policy: Open. */
    public static final String POLICY_OPEN = "open";

    private DingTalkChannelConfigKeys() {}
}
