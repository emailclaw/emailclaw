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

import java.util.Set;

/**
 * Collection of built-in preset email domains.
 *
 * <p>Used to determine if the user is using a preset email (no need to persist IMAP/SMTP hosts in config),
 * shared between email config dialog and Runner preset table.
 */
public final class EmailPresetDomains {

    public static final String EMAILCLAW = "emailclaw.email";
    public static final String GMAIL = "gmail.com";
    public static final String OUTLOOK = "outlook.com";
    public static final String HOTMAIL = "hotmail.com";
    public static final String LIVE = "live.com";
    public static final String MSN = "msn.com";
    public static final String ICLOUD = "icloud.com";
    public static final String ME = "me.com";
    public static final String MAC = "mac.com";
    public static final String NETEASE_163 = "163.com";
    public static final String QQ = "qq.com";
    public static final String FOXMAIL = "foxmail.com";
    public static final String NETEASE_126 = "126.com";
    public static final String YAHOO = "yahoo.com";
    public static final String YAHOO_JP = "yahoo.co.jp";
    public static final String PROTON = "proton.me";
    public static final String PROTONMAIL = "protonmail.com";

    /** All supported one-click preset email domains (lowercase). */
    public static final Set<String> ALL =
            Set.of(
                    EMAILCLAW,
                    GMAIL,
                    OUTLOOK,
                    HOTMAIL,
                    LIVE,
                    MSN,
                    ICLOUD,
                    ME,
                    MAC,
                    NETEASE_163,
                    QQ,
                    FOXMAIL,
                    NETEASE_126,
                    YAHOO,
                    YAHOO_JP,
                    PROTON,
                    PROTONMAIL);

    private EmailPresetDomains() {}
}
