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
package ai.emailclaw.emailclaw.tools;

/**
 * Built-in Agent tool name constants.
 *
 * <p>Consistent with AgentScope {@code @Tool(name=...)}, {@code tools.json} and {@link ai.emailclaw.emailclaw.service.ToolCatalog}
 * to avoid hardcoding the same tool ID in multiple places.
 */
public final class BuiltInToolNames {

    public static final String EXECUTE_SHELL_COMMAND = "execute_shell_command";
    public static final String WEB_FETCH = "web_fetch";
    public static final String BROWSER_USE = "browser_use";
    public static final String DESKTOP_SCREENSHOT = "desktop_screenshot";
    public static final String VIEW_IMAGE = "view_image";
    public static final String VIEW_VIDEO = "view_video";
    public static final String SEND_FILE_TO_USER = "send_file_to_user";
    public static final String GET_CURRENT_TIME = "get_current_time";
    public static final String SET_USER_TIMEZONE = "set_user_timezone";
    public static final String GET_TOKEN_USAGE = "get_token_usage";
    public static final String DELEGATE_EXTERNAL_AGENT = "delegate_external_agent";
    public static final String GET_AGENT_STATUS = "get_agent_status";
    public static final String LIST_AGENTS = "list_agents";
    public static final String CHAT_WITH_AGENT = "chat_with_agent";
    public static final String SUBMIT_TO_AGENT = "submit_to_agent";
    public static final String CHECK_AGENT_TASK = "check_agent_task";
    public static final String SAVE_GLOBAL_PREFERENCE = "save_global_preference";
    public static final String SAVE_PROJECT_MEMORY = "save_project_memory";
    public static final String INVOKE_ANTIGRAVITY_CLI = "invokeAntigravityCli";

    private BuiltInToolNames() {}
}
