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
package ai.emailclaw.emailclaw.cli;

import java.io.PrintWriter;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Terminal UI theme and styling utilities using JLine 4 attributed styles and ANSI sequences.
 */
public final class CliTheme {

    public static final AttributedStyle STYLE_DEFAULT = AttributedStyle.DEFAULT;
    public static final AttributedStyle STYLE_BOLD = AttributedStyle.BOLD;
    public static final AttributedStyle STYLE_DIM = AttributedStyle.DEFAULT.faint();
    public static final AttributedStyle STYLE_CYAN =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    public static final AttributedStyle STYLE_BOLD_CYAN =
            AttributedStyle.BOLD.foreground(AttributedStyle.CYAN);
    public static final AttributedStyle STYLE_GREEN =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    public static final AttributedStyle STYLE_BOLD_GREEN =
            AttributedStyle.BOLD.foreground(AttributedStyle.GREEN);
    public static final AttributedStyle STYLE_YELLOW =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    public static final AttributedStyle STYLE_BOLD_YELLOW =
            AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW);
    public static final AttributedStyle STYLE_RED =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
    public static final AttributedStyle STYLE_BOLD_RED =
            AttributedStyle.BOLD.foreground(AttributedStyle.RED);
    public static final AttributedStyle STYLE_MAGENTA =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA);

    private CliTheme() {}

    /**
     * Prints the industrial-grade Emailclaw welcome banner to the terminal.
     *
     * @param writer the terminal print writer
     * @param appVersion the current application version
     * @param agentName the active agent name
     * @param modelName the active model ID
     * @param workspacePath the application workspace root
     */
    public static void printBanner(
            PrintWriter writer,
            String appVersion,
            String agentName,
            String modelName,
            String workspacePath) {
        writer.println();
        writer.println(
                new AttributedStringBuilder()
                        .style(STYLE_BOLD_CYAN)
                        .append(
                                "  ╔═══════════════════════════════════════════════════════════════════╗\n")
                        .append(
                                "  ║                     EMAILCLAW AGENT CLI                       "
                                        + "    ║\n")
                        .append(
                                "  ║      The Ultimate Autonomous AI Agent for Modern Workflows    "
                                        + "    ║\n")
                        .append(
                                "  ╚═══════════════════════════════════════════════════════════════════╝")
                        .toAnsi());
        writer.println();
        writer.println(
                new AttributedStringBuilder()
                        .style(STYLE_DIM)
                        .append("  Version:   ")
                        .style(STYLE_BOLD_CYAN)
                        .append(appVersion != null ? appVersion : "v26.10")
                        .style(STYLE_DIM)
                        .append("   Agent: ")
                        .style(STYLE_BOLD_GREEN)
                        .append(agentName != null ? agentName : "default")
                        .style(STYLE_DIM)
                        .append("   Model: ")
                        .style(STYLE_BOLD_YELLOW)
                        .append(modelName != null ? modelName : "auto")
                        .toAnsi());
        writer.println(
                new AttributedStringBuilder()
                        .style(STYLE_DIM)
                        .append("  Workspace: ")
                        .style(STYLE_DEFAULT)
                        .append(workspacePath != null ? workspacePath : "~")
                        .toAnsi());
        writer.println(
                new AttributedStringBuilder()
                        .style(STYLE_DIM)
                        .append("  Commands:  Type ")
                        .style(STYLE_BOLD_CYAN)
                        .append("/help")
                        .style(STYLE_DIM)
                        .append(" for commands, ")
                        .style(STYLE_BOLD_CYAN)
                        .append("<TAB>")
                        .style(STYLE_DIM)
                        .append(" for autocomplete, or ")
                        .style(STYLE_BOLD_CYAN)
                        .append("/exit")
                        .style(STYLE_DIM)
                        .append(" to quit.")
                        .toAnsi());
        writer.println();
        writer.flush();
    }

    /**
     * Builds the interactive shell prompt attributed string.
     *
     * @param agentName the active agent identifier
     * @param sessionPreview short label or ID of current session
     * @return the styled prompt string
     */
    public static String buildPrompt(String agentName, String sessionPreview) {
        return new AttributedStringBuilder()
                .style(STYLE_BOLD_CYAN)
                .append("emailclaw")
                .style(STYLE_DIM)
                .append(":")
                .style(STYLE_BOLD_GREEN)
                .append(agentName != null ? agentName : "default")
                .style(STYLE_DIM)
                .append("(")
                .style(STYLE_YELLOW)
                .append(sessionPreview != null ? sessionPreview : "main")
                .style(STYLE_DIM)
                .append(")")
                .style(STYLE_BOLD_CYAN)
                .append(" > ")
                .style(STYLE_DEFAULT)
                .toAnsi();
    }

    /**
     * Formats a section header with border lines.
     *
     * @param title the header title
     * @return styled header string
     */
    public static String formatHeader(String title) {
        return new AttributedStringBuilder()
                .style(STYLE_BOLD_CYAN)
                .append("─── ")
                .append(title)
                .append(" ───────────────────────────────────────────────────────────")
                .toAnsi();
    }

    /**
     * Formats a tool invocation card.
     *
     * @param toolName the name of the tool
     * @param params parameters string or JSON
     * @return styled tool call card
     */
    public static String formatToolCall(String toolName, String params) {
        return new AttributedStringBuilder()
                .style(STYLE_BOLD_YELLOW)
                .append("╭─ 🛠️  Tool Execution: ")
                .append(toolName)
                .append("\n")
                .style(STYLE_DIM)
                .append("│  Input: ")
                .style(STYLE_DEFAULT)
                .append(params != null ? params : "{}")
                .append("\n")
                .style(STYLE_BOLD_YELLOW)
                .append("╰─────────────────────────────────────────────────────────────")
                .toAnsi();
    }

    /**
     * Formats a tool result card.
     *
     * @param toolName the name of the tool
     * @param summary short summary or preview
     * @return styled tool result card
     */
    public static String formatToolResult(String toolName, String summary) {
        return new AttributedStringBuilder()
                .style(STYLE_BOLD_GREEN)
                .append("╭─ ✔️  Tool Result: ")
                .append(toolName)
                .append("\n")
                .style(STYLE_DIM)
                .append("│  ")
                .style(STYLE_DEFAULT)
                .append(summary != null ? summary : "[Done]")
                .append("\n")
                .style(STYLE_BOLD_GREEN)
                .append("╰─────────────────────────────────────────────────────────────")
                .toAnsi();
    }
}
