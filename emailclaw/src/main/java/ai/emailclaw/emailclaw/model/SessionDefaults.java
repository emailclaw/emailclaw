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
package ai.emailclaw.emailclaw.model;

import ai.emailclaw.emailclaw.channel.ChannelIds;

/**
 * Default value constants for model fields such as session and cron tasks.
 */
public final class SessionDefaults {

    public static final String LOCAL_USER_ID = "local-user";
    public static final String DEFAULT_CHANNEL = ChannelIds.CONSOLE;

    /** Aligns with {@link ai.emailclaw.emailclaw.storage.WorkspacePaths#FALLBACK_SESSION_ID}. */
    public static final String DEFAULT_SESSION_ID = "default";

    /** Default request user for cron tasks (system side). */
    public static final String CRON_REQUEST_USER_SYSTEM = ChatMessageRoles.SYSTEM;

    /** Default target user for cron tasks (admin side). */
    public static final String CRON_TARGET_USER_ADMIN = "admin";

    private SessionDefaults() {}
}
