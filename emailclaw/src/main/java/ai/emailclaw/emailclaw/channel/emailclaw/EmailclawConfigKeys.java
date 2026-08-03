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

/**
 * JSON key constants used when synchronizing Email channel {@code pluginConfig} with flat fields.
 */
public final class EmailclawConfigKeys {

    public static final String EMAIL_ADDRESS = "emailAddress";
    public static final String EMAIL_PASSWORD = "emailPassword";
    public static final String IMAP_HOST = "imapHost";
    public static final String IMAP_PORT = "imapPort";
    public static final String IMAP_SSL = "imapSsl";
    public static final String IMAP_START_TLS = "imapStartTls";
    public static final String SMTP_HOST = "smtpHost";
    public static final String SMTP_PORT = "smtpPort";
    public static final String SMTP_SSL = "smtpSsl";
    public static final String SMTP_START_TLS = "smtpStartTls";
    public static final String EMAIL_ALLOWLIST_SENDERS = "emailAllowlistSenders";
    public static final String EMAIL_POLL_INTERVAL_SECONDS = "emailPollIntervalSeconds";
    // Caused many issues so not used: public static final String EMAIL_LAST_SEEN_UID =
    // "emailLastSeenUid";
    public static final String SYS_EMAIL_MODE = "sysEmailMode";
    public static final String RESGISTRANT_EMAIL = "registrantEmail";
    public static final String ONE_TIME_PASSWORD = "oneTimePassword";
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";

    private EmailclawConfigKeys() {}
}
