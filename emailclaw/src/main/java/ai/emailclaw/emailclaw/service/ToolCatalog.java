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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.ToolInfo;
import ai.emailclaw.emailclaw.tools.BuiltInToolNames;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in tool catalog.
 *
 * <p>Centrally define the default tool list, acting as a fallback when the config file is missing.
 */
public final class ToolCatalog {
    private ToolCatalog() {}

    public static List<ToolInfo> defaults() {
        List<ToolInfo> tools = new ArrayList<>();
        tools.add(tool(BuiltInToolNames.BROWSER_USE, "Browser automation and web interaction"));
        tools.add(tool(BuiltInToolNames.DESKTOP_SCREENSHOT, "Capture desktop screenshots"));
        tools.add(
                tool(
                        BuiltInToolNames.VIEW_IMAGE,
                        "Load an image into LLM context for visual analysis"));
        tools.add(
                tool(
                        BuiltInToolNames.VIEW_VIDEO,
                        "Load a video into LLM context for visual analysis"));
        tools.add(tool(BuiltInToolNames.SEND_FILE_TO_USER, "Send files to user"));
        tools.add(tool(BuiltInToolNames.GET_CURRENT_TIME, "Get current date and time"));
        tools.add(tool(BuiltInToolNames.SET_USER_TIMEZONE, "Set user timezone"));
        tools.add(tool(BuiltInToolNames.GET_TOKEN_USAGE, "Get llm token usage"));
        tools.add(
                tool(
                        BuiltInToolNames.DELEGATE_EXTERNAL_AGENT,
                        "Delegate work to an external ACP agent runner"));
        tools.add(tool(BuiltInToolNames.LIST_AGENTS, "List available agents"));
        tools.add(tool(BuiltInToolNames.CHAT_WITH_AGENT, "Chat with another agent"));
        tools.add(
                tool(
                        BuiltInToolNames.SUBMIT_TO_AGENT,
                        "Submit a background task to another agent"));
        tools.add(
                tool(BuiltInToolNames.CHECK_AGENT_TASK, "Check status of a background agent task"));
        return tools;
    }

    private static ToolInfo tool(String name, String desc) {
        return new ToolInfo(name, desc, true, true);
    }
}
