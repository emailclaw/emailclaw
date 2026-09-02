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
package ai.emailclaw.emailclaw.plugin.channel.emailclaw;

import ai.emailclaw.emailclaw.model.AgentIds;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Immutable configuration record for a single mailbox account in Emailclaw.
 * Note: emailAddress must be unique across all configured mailboxes in the channel.
 *
 * @param id                  Unique mailbox identifier
 * @param name                Display name or label for this mailbox
 * @param enabled             Whether this mailbox account is enabled
 * @param emailAddress        Email address (must be globally unique across configured mailboxes)
 * @param emailPassword       Email password or authorization code
 * @param imapHost            IMAP server host address
 * @param imapPort            IMAP server port
 * @param imapSsl             Whether to use SSL for IMAP connection
 * @param imapStartTls        Whether to use STARTTLS for IMAP connection
 * @param smtpHost            SMTP server host address
 * @param smtpPort            SMTP server port
 * @param smtpSsl             Whether to use SSL for SMTP connection
 * @param smtpStartTls        Whether to use STARTTLS for SMTP connection
 * @param targetAgentId       Bound Agent ID to route new unassigned inbound emails (blank means default agent)
 * @param allowlistSenders    Allowed sender whitelist for this mailbox (empty means allow all)
 * @param pollIntervalSeconds Polling interval in seconds (minimum 5, default 30)
 */
public record MailboxAccountConfig(
        String id,
        String name,
        boolean enabled,
        String emailAddress,
        String emailPassword,
        String imapHost,
        int imapPort,
        boolean imapSsl,
        boolean imapStartTls,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        boolean smtpStartTls,
        String targetAgentId,
        List<String> allowlistSenders,
        int pollIntervalSeconds) {

    /**
     * Compact constructor with normalization and defaults.
     */
    public MailboxAccountConfig {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
        emailAddress = emailAddress == null ? "" : emailAddress.trim().toLowerCase(Locale.ROOT);
        name =
                (name == null || name.isBlank())
                        ? (emailAddress.isBlank() ? "Mailbox" : emailAddress)
                        : name.trim();
        emailPassword = emailPassword == null ? "" : emailPassword;
        imapHost = imapHost == null ? "" : imapHost.trim();
        imapPort = imapPort <= 0 ? 993 : imapPort;
        smtpHost = smtpHost == null ? "" : smtpHost.trim();
        smtpPort = smtpPort <= 0 ? 465 : smtpPort;
        targetAgentId =
                targetAgentId == null || targetAgentId.isBlank()
                        ? AgentIds.DEFAULT
                        : targetAgentId.trim();
        allowlistSenders = allowlistSenders == null ? List.of() : List.copyOf(allowlistSenders);
        pollIntervalSeconds = pollIntervalSeconds < 5 ? 30 : pollIntervalSeconds;
    }

    /**
     * Creates a default empty mailbox builder record with standard defaults.
     *
     * @param emailAddress Email address
     * @return New MailboxAccountConfig instance
     */
    public static MailboxAccountConfig createDefault(String emailAddress) {
        String normalizedEmail =
                emailAddress == null ? "" : emailAddress.trim().toLowerCase(Locale.ROOT);
        EmailMailPreset preset = EmailPresetRegistry.presetOf(normalizedEmail);
        if (preset != null) {
            return new MailboxAccountConfig(
                    UUID.randomUUID().toString(),
                    preset.displayName(),
                    true,
                    normalizedEmail,
                    "",
                    "",
                    993,
                    true,
                    false,
                    "",
                    465,
                    true,
                    false,
                    "",
                    List.of(),
                    30);
        }
        return new MailboxAccountConfig(
                UUID.randomUUID().toString(),
                normalizedEmail.isBlank() ? "New Mailbox" : normalizedEmail,
                true,
                normalizedEmail,
                "",
                "",
                993,
                true,
                false,
                "",
                465,
                true,
                false,
                "",
                List.of(),
                30);
    }

    /**
     * Resolves the effective IMAP host, falling back to preset if not explicitly configured.
     *
     * @return Effective IMAP host
     */
    public String effectiveImapHost() {
        if (!imapHost.isBlank()) {
            return imapHost;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.imapHost() : "";
    }

    /**
     * Resolves the effective IMAP port.
     *
     * @return Effective IMAP port
     */
    public int effectiveImapPort() {
        if (!imapHost.isBlank()) {
            return imapPort;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.imapPort() : imapPort;
    }

    /**
     * Resolves the effective IMAP SSL flag.
     *
     * @return Effective IMAP SSL flag
     */
    public boolean effectiveImapSsl() {
        if (!imapHost.isBlank()) {
            return imapSsl;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.imapSsl() : imapSsl;
    }

    /**
     * Resolves the effective IMAP STARTTLS flag.
     *
     * @return Effective IMAP STARTTLS flag
     */
    public boolean effectiveImapStartTls() {
        if (!imapHost.isBlank()) {
            return imapStartTls;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.imapStartTls() : imapStartTls;
    }

    /**
     * Resolves the effective SMTP host, falling back to preset if not explicitly configured.
     *
     * @return Effective SMTP host
     */
    public String effectiveSmtpHost() {
        if (!smtpHost.isBlank()) {
            return smtpHost;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.smtpHost() : "";
    }

    /**
     * Resolves the effective SMTP port.
     *
     * @return Effective SMTP port
     */
    public int effectiveSmtpPort() {
        if (!smtpHost.isBlank()) {
            return smtpPort;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.smtpPort() : smtpPort;
    }

    /**
     * Resolves the effective SMTP SSL flag.
     *
     * @return Effective SMTP SSL flag
     */
    public boolean effectiveSmtpSsl() {
        if (!smtpHost.isBlank()) {
            return smtpSsl;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.smtpSsl() : smtpSsl;
    }

    /**
     * Resolves the effective SMTP STARTTLS flag.
     *
     * @return Effective SMTP STARTTLS flag
     */
    public boolean effectiveSmtpStartTls() {
        if (!smtpHost.isBlank()) {
            return smtpStartTls;
        }
        EmailMailPreset preset = EmailPresetRegistry.presetOf(emailAddress);
        return preset != null ? preset.smtpStartTls() : smtpStartTls;
    }

    /**
     * Checks if this mailbox has all required credentials and connection parameters configured.
     *
     * @return True if runnable
     */
    public boolean isRunnable() {
        return enabled
                && !emailAddress.isBlank()
                && !emailPassword.isBlank()
                && !effectiveImapHost().isBlank()
                && !effectiveSmtpHost().isBlank();
    }

    /**
     * Returns a copy of this configuration with the specified enabled state.
     *
     * @param newEnabled the new enabled state
     * @return a new MailboxAccountConfig instance
     */
    public MailboxAccountConfig withEnabled(boolean newEnabled) {
        return new MailboxAccountConfig(
                this.id,
                this.name,
                newEnabled,
                this.emailAddress,
                this.emailPassword,
                this.imapHost,
                this.imapPort,
                this.imapSsl,
                this.imapStartTls,
                this.smtpHost,
                this.smtpPort,
                this.smtpSsl,
                this.smtpStartTls,
                this.targetAgentId,
                this.allowlistSenders,
                this.pollIntervalSeconds);
    }
}
