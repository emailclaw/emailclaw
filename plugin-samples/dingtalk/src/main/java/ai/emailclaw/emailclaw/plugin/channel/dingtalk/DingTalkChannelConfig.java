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

import ai.emailclaw.emailclaw.channel.ChannelPluginConfigAccess;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.util.CommonUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed accessor for DingTalk channel {@link ChannelInfo#pluginConfig}.
 */
public final class DingTalkChannelConfig {

    private DingTalkChannelConfig() {}

    public static String getClientId(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, DingTalkChannelConfigKeys.CLIENT_ID, "");
    }

    public static void setClientId(ChannelInfo channel, String clientId) {
        ChannelPluginConfigAccess.putStr(channel, DingTalkChannelConfigKeys.CLIENT_ID, clientId);
    }

    public static String getClientSecret(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, DingTalkChannelConfigKeys.CLIENT_SECRET, "");
    }

    public static void setClientSecret(ChannelInfo channel, String clientSecret) {
        ChannelPluginConfigAccess.putStr(
                channel, DingTalkChannelConfigKeys.CLIENT_SECRET, clientSecret);
    }

    public static boolean isShowToolMessages(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(
                channel, DingTalkChannelConfigKeys.SHOW_TOOL_MESSAGES, true);
    }

    public static void setShowToolMessages(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(
                channel, DingTalkChannelConfigKeys.SHOW_TOOL_MESSAGES, value);
    }

    public static boolean isShowThinking(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(
                channel, DingTalkChannelConfigKeys.SHOW_THINKING, true);
    }

    public static void setShowThinking(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(channel, DingTalkChannelConfigKeys.SHOW_THINKING, value);
    }

    public static String getMessageType(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(
                channel,
                DingTalkChannelConfigKeys.MESSAGE_TYPE,
                DingTalkChannelConfigKeys.DEFAULT_MESSAGE_TYPE);
    }

    public static void setMessageType(ChannelInfo channel, String messageType) {
        ChannelPluginConfigAccess.putStr(
                channel, DingTalkChannelConfigKeys.MESSAGE_TYPE, messageType);
    }

    public static String getCronMessageType(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(
                channel,
                DingTalkChannelConfigKeys.CRON_MESSAGE_TYPE,
                DingTalkChannelConfigKeys.DEFAULT_MESSAGE_TYPE);
    }

    public static void setCronMessageType(ChannelInfo channel, String messageType) {
        ChannelPluginConfigAccess.putStr(
                channel, DingTalkChannelConfigKeys.CRON_MESSAGE_TYPE, messageType);
    }

    public static boolean isAtSenderOnReply(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(
                channel, DingTalkChannelConfigKeys.AT_SENDER_ON_REPLY, false);
    }

    public static void setAtSenderOnReply(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(
                channel, DingTalkChannelConfigKeys.AT_SENDER_ON_REPLY, value);
    }

    public static String getDmPolicy(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(
                channel,
                DingTalkChannelConfigKeys.DM_POLICY,
                DingTalkChannelConfigKeys.POLICY_OPEN);
    }

    public static void setDmPolicy(ChannelInfo channel, String policy) {
        ChannelPluginConfigAccess.putStr(channel, DingTalkChannelConfigKeys.DM_POLICY, policy);
    }

    public static String getGroupPolicy(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(
                channel,
                DingTalkChannelConfigKeys.GROUP_POLICY,
                DingTalkChannelConfigKeys.POLICY_OPEN);
    }

    public static void setGroupPolicy(ChannelInfo channel, String policy) {
        ChannelPluginConfigAccess.putStr(channel, DingTalkChannelConfigKeys.GROUP_POLICY, policy);
    }

    public static boolean isRequireMention(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(
                channel, DingTalkChannelConfigKeys.REQUIRE_MENTION, false);
    }

    public static void setRequireMention(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(
                channel, DingTalkChannelConfigKeys.REQUIRE_MENTION, value);
    }

    public static List<String> getAllowlistUsers(ChannelInfo channel) {
        return ChannelPluginConfigAccess.strList(
                channel, DingTalkChannelConfigKeys.ALLOWLIST_USERS);
    }

    public static void setAllowlistUsers(ChannelInfo channel, List<String> users) {
        ChannelPluginConfigAccess.putStrList(
                channel, DingTalkChannelConfigKeys.ALLOWLIST_USERS, users);
    }

    /** Append user to allowlist (ignored if already exists). */
    public static void addAllowlistUser(ChannelInfo channel, String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        List<String> users = new ArrayList<>(getAllowlistUsers(channel));
        if (!users.contains(userId)) {
            users.add(userId);
            setAllowlistUsers(channel, users);
        }
    }

    public static boolean isConfigured(ChannelInfo channel) {
        return CommonUtils.notBlank(getClientId(channel))
                && CommonUtils.notBlank(getClientSecret(channel));
    }
}
