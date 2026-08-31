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
package ai.emailclaw.emailclaw.storage;

/**
 * Relative paths and file name constants within each Agent's workspace.
 */
public final class WorkspacePaths {

    public static final String MEMORY_DIR = "memory";
    public static final String SKILLS_DIR = "skills";
    public static final String SESSIONS_DIR = "sessions";
    public static final String PLANS_DIR = "plans";
    public static final String ATTACHMENTS_DIR = ".attachments";

    public static final String CONFIGURATION_FILE = "configuration.json";
    public static final String HEARTBEAT_FILE = "heartbeat.json";

    public static final String AGENTS_MD = "AGENTS.md";
    public static final String SOUL_MD = "SOUL.md";
    public static final String PROFILE_MD = "PROFILE.md";
    public static final String BOOTSTRAP_MD = "BOOTSTRAP.md";
    public static final String HEARTBEAT_MD = "HEARTBEAT.md";
    public static final String MEMORY_MD = "MEMORY.md";

    /** Fallback session identifier used by attachment directory when session ID is missing. */
    public static final String FALLBACK_SESSION_ID = "default";

    private WorkspacePaths() {}
}
