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

import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.service.StreamCallback;
import io.agentscope.core.message.Msg;
import java.io.PrintWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jline.utils.AttributedStringBuilder;

/**
 * Terminal stream callback renderer that transforms {@link ChatMessagePart} streaming events
 * into a rich visual presentation in real time.
 */
public class CliStreamRenderer implements StreamCallback {

    private static final Logger LOGGER = Logger.getLogger(CliStreamRenderer.class.getName());

    private final PrintWriter writer;
    private final boolean quiet;
    private final long startTime;

    private boolean inThinking = false;
    private boolean hasPrintedAgentHeader = false;
    private boolean lastEndedWithNewline = true;

    /**
     * Constructs a stream renderer.
     *
     * @param writer the terminal print writer
     * @param quiet whether to suppress decorational headers
     */
    public CliStreamRenderer(PrintWriter writer, boolean quiet) {
        this.writer = writer;
        this.quiet = quiet;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public synchronized void onPart(ChatMessagePart part, boolean startsNew) {
        if (part == null) {
            return;
        }

        String type = part.getType();
        if (ChatMessagePart.THINKING.equals(type)) {
            renderThinking(part, startsNew);
        } else if (ChatMessagePart.TOOL_CALL.equals(type)) {
            renderToolCall(part);
        } else if (ChatMessagePart.TOOL_RESULT.equals(type)) {
            renderToolResult(part);
        } else if (ChatMessagePart.HINT.equals(type)) {
            renderHint(part);
        } else if (ChatMessagePart.ERROR.equals(type)) {
            renderError(part);
        } else if (ChatMessagePart.SUB_AGENT_EVENT.equals(type)) {
            renderSubAgentEvent(part);
        } else {
            renderText(part, startsNew);
        }
    }

    @Override
    public synchronized void onCompleted(Msg message) {
        if (inThinking) {
            writer.println();
            inThinking = false;
        }

        if (!lastEndedWithNewline) {
            writer.println();
            lastEndedWithNewline = true;
        }

        if (!quiet) {
            long durationMs = System.currentTimeMillis() - startTime;
            writer.println(
                    new AttributedStringBuilder()
                            .style(CliTheme.STYLE_DIM)
                            .append("  [Turn completed in ")
                            .append(String.format("%.2fs", durationMs / 1000.0))
                            .append("]")
                            .toAnsi());
        }
        writer.println();
        writer.flush();
        LOGGER.log(
                Level.FINE,
                "Stream rendering completed in {0} ms",
                System.currentTimeMillis() - startTime);
    }

    private void renderThinking(ChatMessagePart part, boolean startsNew) {
        if (quiet) {
            return;
        }
        if (!inThinking) {
            if (!lastEndedWithNewline) {
                writer.println();
            }
            writer.print(
                    new AttributedStringBuilder()
                            .style(CliTheme.STYLE_DIM)
                            .append("\n  💭 Thinking: ")
                            .toAnsi());
            inThinking = true;
        }
        String text = part.getText();
        if (text != null && !text.isEmpty()) {
            writer.print(
                    new AttributedStringBuilder().style(CliTheme.STYLE_DIM).append(text).toAnsi());
            writer.flush();
            lastEndedWithNewline = text.endsWith("\n");
        }
    }

    private void renderToolCall(ChatMessagePart part) {
        if (inThinking) {
            writer.println();
            inThinking = false;
        }
        if (!lastEndedWithNewline) {
            writer.println();
        }
        String inputStr = formatToolInput(part.getToolInput());
        writer.println(CliTheme.formatToolCall(part.getToolName(), inputStr));
        writer.flush();
        lastEndedWithNewline = true;
    }

    private void renderToolResult(ChatMessagePart part) {
        if (!lastEndedWithNewline) {
            writer.println();
        }
        String preview = part.getText();
        if (preview != null && preview.length() > 300) {
            preview =
                    preview.substring(0, 300)
                            + "... [truncated, "
                            + preview.length()
                            + " chars total]";
        }
        writer.println(CliTheme.formatToolResult(part.getToolName(), preview));
        writer.flush();
        lastEndedWithNewline = true;
    }

    private void renderHint(ChatMessagePart part) {
        if (!lastEndedWithNewline) {
            writer.println();
        }
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_CYAN)
                        .append("💡 [Plan/Hint] ")
                        .style(CliTheme.STYLE_CYAN)
                        .append(part.getText() != null ? part.getText() : "")
                        .toAnsi());
        writer.flush();
        lastEndedWithNewline = true;
    }

    private void renderError(ChatMessagePart part) {
        if (!lastEndedWithNewline) {
            writer.println();
        }
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_RED)
                        .append("❌ [Error] ")
                        .style(CliTheme.STYLE_RED)
                        .append(part.getText() != null ? part.getText() : "")
                        .toAnsi());
        writer.flush();
        lastEndedWithNewline = true;
    }

    private void renderSubAgentEvent(ChatMessagePart part) {
        if (!lastEndedWithNewline) {
            writer.println();
        }
        writer.println(
                new AttributedStringBuilder()
                        .style(CliTheme.STYLE_BOLD_YELLOW)
                        .append("🤖 [SubAgent] ")
                        .style(CliTheme.STYLE_YELLOW)
                        .append(part.getText() != null ? part.getText() : "")
                        .toAnsi());
        writer.flush();
        lastEndedWithNewline = true;
    }

    private void renderText(ChatMessagePart part, boolean startsNew) {
        if (inThinking) {
            writer.println();
            inThinking = false;
        }

        if (!hasPrintedAgentHeader) {
            if (!lastEndedWithNewline) {
                writer.println();
            }
            if (!quiet) {
                writer.print(
                        new AttributedStringBuilder()
                                .style(CliTheme.STYLE_BOLD_CYAN)
                                .append("Agent> ")
                                .style(CliTheme.STYLE_DEFAULT)
                                .toAnsi());
            }
            hasPrintedAgentHeader = true;
        }

        String text = part.getText();
        if (text != null && !text.isEmpty()) {
            writer.print(text);
            writer.flush();
            lastEndedWithNewline = text.endsWith("\n");
        }
    }

    private String formatToolInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        return input.toString();
    }
}
