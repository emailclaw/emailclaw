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

import java.util.logging.Logger;

/**
 * IMAP/SMTP connection parameter presets for common email service providers.
 *
 * <p>{@link #displayName()} is used for UI prompt copy;
 * mapping between domain names and presets is established via {@link EmailPresetRegistry}.
 *
 * @param displayName  Display name (usually consistent with domain name, can be main domain when sharing presets)
 * @param imapHost     IMAP server address
 * @param imapPort     IMAP server port
 * @param imapSsl      Whether to enable IMAP SSL
 * @param imapStartTls Whether to enable IMAP STARTTLS
 * @param smtpHost     SMTP server address
 * @param smtpPort     SMTP server port
 * @param smtpSsl      Whether to enable SMTP SSL
 * @param smtpStartTls Whether to enable SMTP STARTTLS
 */
public record EmailMailPreset(
        String displayName,
        String imapHost,
        int imapPort,
        boolean imapSsl,
        boolean imapStartTls,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        boolean smtpStartTls) {

    private static final Logger LOGGER = Logger.getLogger(EmailMailPreset.class.getName());

    /**
     * Compact constructor, only for parameter validation logging.
     */
    public EmailMailPreset {
        LOGGER.fine(
                "Create EmailMailPreset: displayName=" + displayName + ", imapHost=" + imapHost);
    }
}
