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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * IMAP/SMTP preset registry for common email domains.
 *
 * <p>Used by {@link EmailclawChannelRunner} and {@link ai.emailclaw.emailclaw.ui.EmailclawChannelConfigDialog},
 * preventing duplicate maintenance of {@code createPresets()} in both places.
 */
public final class EmailPresetRegistry {

    /** Domain name (lowercase) → preset parameters. */
    public static final Map<String, EmailMailPreset> PRESETS = createPresets();

    private EmailPresetRegistry() {}

    /**
     * Parses the domain name from a full email address and returns the corresponding preset.
     *
     * @param email User email, can be null
     * @return Configuration if preset is found, otherwise null
     */
    public static EmailMailPreset presetOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at >= email.length() - 1) {
            return null;
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return PRESETS.get(domain);
    }

    /**
     * Builds the IMAP/SMTP preset table for common email domains.
     */
    private static Map<String, EmailMailPreset> createPresets() {
        Map<String, EmailMailPreset> map = new HashMap<>();
        map.put(
                EmailPresetDomains.EMAILCLAW,
                new EmailMailPreset(
                        EmailPresetDomains.EMAILCLAW,
                        "imap.emailclaw.email",
                        993,
                        true,
                        false,
                        "smtp.emailclaw.email",
                        465,
                        true,
                        false));
        map.put(
                EmailPresetDomains.GMAIL,
                new EmailMailPreset(
                        EmailPresetDomains.GMAIL,
                        "imap.gmail.com",
                        993,
                        true,
                        false,
                        "smtp.gmail.com",
                        465,
                        true,
                        false));
        EmailMailPreset outlook =
                new EmailMailPreset(
                        EmailPresetDomains.OUTLOOK,
                        "imap-mail.outlook.com",
                        993,
                        true,
                        false,
                        "smtp-mail.outlook.com",
                        587,
                        false,
                        true);
        map.put(EmailPresetDomains.OUTLOOK, outlook);
        map.put(EmailPresetDomains.HOTMAIL, outlook);
        map.put(EmailPresetDomains.LIVE, outlook);
        map.put(EmailPresetDomains.MSN, outlook);
        EmailMailPreset icloud =
                new EmailMailPreset(
                        EmailPresetDomains.ICLOUD,
                        "imap.mail.me.com",
                        993,
                        true,
                        false,
                        "smtp.mail.me.com",
                        587,
                        false,
                        true);
        map.put(EmailPresetDomains.ICLOUD, icloud);
        map.put(EmailPresetDomains.ME, icloud);
        map.put(EmailPresetDomains.MAC, icloud);
        map.put(
                EmailPresetDomains.NETEASE_163,
                new EmailMailPreset(
                        EmailPresetDomains.NETEASE_163,
                        "imap.163.com",
                        993,
                        true,
                        false,
                        "smtp.163.com",
                        465,
                        true,
                        false));
        map.put(
                EmailPresetDomains.QQ,
                new EmailMailPreset(
                        EmailPresetDomains.QQ,
                        "imap.qq.com",
                        993,
                        true,
                        false,
                        "smtp.qq.com",
                        465,
                        true,
                        false));
        map.put(
                EmailPresetDomains.FOXMAIL,
                new EmailMailPreset(
                        EmailPresetDomains.FOXMAIL,
                        "imap.qq.com",
                        993,
                        true,
                        false,
                        "smtp.qq.com",
                        465,
                        true,
                        false));
        map.put(
                EmailPresetDomains.NETEASE_126,
                new EmailMailPreset(
                        EmailPresetDomains.NETEASE_126,
                        "imap.126.com",
                        993,
                        true,
                        false,
                        "smtp.126.com",
                        465,
                        true,
                        false));
        map.put(
                EmailPresetDomains.YAHOO,
                new EmailMailPreset(
                        EmailPresetDomains.YAHOO,
                        "imap.mail.yahoo.com",
                        993,
                        true,
                        false,
                        "smtp.mail.yahoo.com",
                        465,
                        true,
                        false));
        map.put(
                EmailPresetDomains.YAHOO_JP,
                new EmailMailPreset(
                        EmailPresetDomains.YAHOO_JP,
                        "imap.mail.yahoo.co.jp",
                        993,
                        true,
                        false,
                        "smtp.mail.yahoo.co.jp",
                        465,
                        true,
                        false));
        EmailMailPreset proton =
                new EmailMailPreset(
                        EmailPresetDomains.PROTON,
                        "127.0.0.1",
                        1143,
                        false,
                        false,
                        "127.0.0.1",
                        1025,
                        false,
                        false);
        map.put(EmailPresetDomains.PROTON, proton);
        map.put(EmailPresetDomains.PROTONMAIL, proton);
        return Map.copyOf(map);
    }
}
