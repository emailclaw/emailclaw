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
package ai.emailclaw.emailclaw.channel.emailclaw;

import ai.emailclaw.emailclaw.channel.ChannelPluginConfigAccess;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.util.CommonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Typed accessor for Email channel {@link ChannelInfo#pluginConfig}.
 */
public final class EmailclawChannelConfig {

    private static final Logger LOGGER = Logger.getLogger(EmailclawChannelConfig.class.getName());

    private EmailclawChannelConfig() {}

    public static String getEmailAddress(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, EmailclawConfigKeys.EMAIL_ADDRESS, "");
    }

    public static void setEmailAddress(ChannelInfo channel, String email) {
        ChannelPluginConfigAccess.putStr(channel, EmailclawConfigKeys.EMAIL_ADDRESS, email);
    }

    public static String getEmailPassword(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, EmailclawConfigKeys.EMAIL_PASSWORD, "");
    }

    public static void setEmailPassword(ChannelInfo channel, String password) {
        ChannelPluginConfigAccess.putStr(channel, EmailclawConfigKeys.EMAIL_PASSWORD, password);
    }

    public static String getImapHost(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, EmailclawConfigKeys.IMAP_HOST, "");
    }

    public static void setImapHost(ChannelInfo channel, String host) {
        ChannelPluginConfigAccess.putStr(channel, EmailclawConfigKeys.IMAP_HOST, host);
    }

    public static int getImapPort(ChannelInfo channel) {
        return ChannelPluginConfigAccess.intVal(channel, EmailclawConfigKeys.IMAP_PORT, 993);
    }

    public static void setImapPort(ChannelInfo channel, int port) {
        ChannelPluginConfigAccess.putInt(channel, EmailclawConfigKeys.IMAP_PORT, port);
    }

    public static boolean isImapSsl(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(channel, EmailclawConfigKeys.IMAP_SSL, true);
    }

    public static void setImapSsl(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(channel, EmailclawConfigKeys.IMAP_SSL, value);
    }

    public static boolean isImapStartTls(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(channel, EmailclawConfigKeys.IMAP_START_TLS, false);
    }

    public static void setImapStartTls(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(channel, EmailclawConfigKeys.IMAP_START_TLS, value);
    }

    public static String getSmtpHost(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, EmailclawConfigKeys.SMTP_HOST, "");
    }

    public static void setSmtpHost(ChannelInfo channel, String host) {
        ChannelPluginConfigAccess.putStr(channel, EmailclawConfigKeys.SMTP_HOST, host);
    }

    public static int getSmtpPort(ChannelInfo channel) {
        return ChannelPluginConfigAccess.intVal(channel, EmailclawConfigKeys.SMTP_PORT, 465);
    }

    public static void setSmtpPort(ChannelInfo channel, int port) {
        ChannelPluginConfigAccess.putInt(channel, EmailclawConfigKeys.SMTP_PORT, port);
    }

    public static boolean isSmtpSsl(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(channel, EmailclawConfigKeys.SMTP_SSL, true);
    }

    public static void setSmtpSsl(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(channel, EmailclawConfigKeys.SMTP_SSL, value);
    }

    public static boolean isSmtpStartTls(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(channel, EmailclawConfigKeys.SMTP_START_TLS, false);
    }

    public static void setSmtpStartTls(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(channel, EmailclawConfigKeys.SMTP_START_TLS, value);
    }

    public static List<String> getEmailAllowlistSenders(ChannelInfo channel) {
        return ChannelPluginConfigAccess.strList(
                channel, EmailclawConfigKeys.EMAIL_ALLOWLIST_SENDERS);
    }

    public static void setEmailAllowlistSenders(ChannelInfo channel, List<String> senders) {
        ChannelPluginConfigAccess.putStrList(
                channel, EmailclawConfigKeys.EMAIL_ALLOWLIST_SENDERS, senders);
    }

    /**
     * Parse the default recipient for Emailclaw outbound emails.
     *
     * <p>When a session is not bound to a specific email (e.g. sessions automatically created by cron jobs), take
     * the first item of the {@code emailAllowlistSenders} configuration list as the default recipient.
     *
     * @param channel Emailclaw channel configuration
     * @return First allowlist email; returns {@code null} if list is empty or channel is null
     */
    public static String resolveDefaultRecipient(ChannelInfo channel) {
        LOGGER.fine("Parsing Emailclaw default recipient");
        if (channel == null) {
            LOGGER.warning(
                    "Emailclaw channel configuration is null, unable to parse default recipient");
            return null;
        }
        List<String> senders = getEmailAllowlistSenders(channel);
        if (senders.isEmpty()) {
            LOGGER.warning("emailAllowlistSenders is empty, unable to parse default recipient");
            return null;
        }
        for (String sender : senders) {
            if (CommonUtils.notBlank(sender)) {
                String recipient = sender.trim();
                LOGGER.info(
                        "Emailclaw default recipient taken from allowlist first item: "
                                + recipient);
                return recipient;
            }
        }
        LOGGER.warning(
                "emailAllowlistSenders only contains blank items, unable to parse default"
                        + " recipient");
        return null;
    }

    public static int getEmailPollIntervalSeconds(ChannelInfo channel) {
        return ChannelPluginConfigAccess.intVal(
                channel, EmailclawConfigKeys.EMAIL_POLL_INTERVAL_SECONDS, 30);
    }

    public static void setEmailPollIntervalSeconds(ChannelInfo channel, int seconds) {
        ChannelPluginConfigAccess.putInt(
                channel, EmailclawConfigKeys.EMAIL_POLL_INTERVAL_SECONDS, seconds);
    }

    public static boolean isSysEmailMode(ChannelInfo channel) {
        return ChannelPluginConfigAccess.bool(channel, EmailclawConfigKeys.SYS_EMAIL_MODE, false);
    }

    public static void setSysEmailMode(ChannelInfo channel, boolean value) {
        ChannelPluginConfigAccess.putBool(channel, EmailclawConfigKeys.SYS_EMAIL_MODE, value);
    }

    /** Whether it is a built-in preset email domain (server parameters parsed by {@link EmailPresetRegistry}). */
    public static boolean isPresetEmail(ChannelInfo channel) {
        if (channel == null) return false;
        String email = getEmailAddress(channel);
        if (!CommonUtils.notBlank(email)) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at >= email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).trim().toLowerCase();
        return EmailPresetDomains.ALL.contains(domain);
    }

    /**
     * Clear persisted IMAP/SMTP host parameters (used when saving preset emails), unify externally with normalizeEmailclawPluginConfig.
     */
    private static void clearMailServerKeys(ChannelInfo channel) {
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.IMAP_HOST);
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.IMAP_PORT);
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.IMAP_SSL);
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.IMAP_START_TLS);
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.SMTP_HOST);
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.SMTP_PORT);
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.SMTP_SSL);
        ChannelPluginConfigAccess.remove(channel, EmailclawConfigKeys.SMTP_START_TLS);
    }

    /**
     * Write server parameters from UI form to pluginConfig (for non-preset emails).
     */
    /** Deep copy the current plugin configuration for background task snapshots. */
    public static Map<String, Object> copyPluginConfig(ChannelInfo channel) {
        return new LinkedHashMap<>(ChannelPluginConfigAccess.config(channel));
    }

    public static void setMailServerFromForm(
            ChannelInfo channel,
            String imapHost,
            int imapPort,
            boolean imapSsl,
            boolean imapStartTls,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            boolean smtpStartTls) {
        setImapHost(channel, imapHost);
        setImapPort(channel, imapPort);
        setImapSsl(channel, imapSsl);
        setImapStartTls(channel, imapStartTls);
        setSmtpHost(channel, smtpHost);
        setSmtpPort(channel, smtpPort);
        setSmtpSsl(channel, smtpSsl);
        setSmtpStartTls(channel, smtpStartTls);
    }

    /**
     * Preset emails do not persist IMAP/SMTP parameters, they are parsed at runtime by {@link ai.emailclaw.emailclaw.channel.emailclaw.EmailPresetRegistry}.
     *
     * @return Whether redundant keys were removed
     */
    public static boolean normalizeEmailclawPluginConfig(ChannelInfo ch) {
        if (ch == null || ch.getPluginConfig() == null) return false;
        return normalizeEmailclawPluginConfig(ch.getPluginConfig());
    }

    public static boolean normalizeEmailclawPluginConfig(Map<String, Object> pluginConfig) {
        if (pluginConfig == null) return false;
        String email = (String) pluginConfig.getOrDefault(EmailclawConfigKeys.EMAIL_ADDRESS, "");
        if (!CommonUtils.notBlank(email)) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at >= email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).trim().toLowerCase();
        if (!EmailPresetDomains.ALL.contains(domain)) {
            return false;
        }

        boolean modified = false;
        String[] keysToRemove = {
            EmailclawConfigKeys.IMAP_HOST, EmailclawConfigKeys.IMAP_PORT,
            EmailclawConfigKeys.IMAP_SSL, EmailclawConfigKeys.IMAP_START_TLS,
            EmailclawConfigKeys.SMTP_HOST, EmailclawConfigKeys.SMTP_PORT,
            EmailclawConfigKeys.SMTP_SSL, EmailclawConfigKeys.SMTP_START_TLS
        };
        for (String key : keysToRemove) {
            if (pluginConfig.containsKey(key)) {
                pluginConfig.remove(key);
                modified = true;
            }
        }
        return modified;
    }

    public static String getRegistrantEmail(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, EmailclawConfigKeys.RESGISTRANT_EMAIL, "");
    }

    public static void setRegistrantEmail(ChannelInfo channel, String email) {
        ChannelPluginConfigAccess.putStr(channel, EmailclawConfigKeys.RESGISTRANT_EMAIL, email);
    }

    public static String getOneTimePassword(ChannelInfo channel) {
        return ChannelPluginConfigAccess.str(channel, EmailclawConfigKeys.ONE_TIME_PASSWORD, "");
    }

    public static void setOneTimePassword(ChannelInfo channel, String email) {
        ChannelPluginConfigAccess.putStr(channel, EmailclawConfigKeys.ONE_TIME_PASSWORD, email);
    }
}
