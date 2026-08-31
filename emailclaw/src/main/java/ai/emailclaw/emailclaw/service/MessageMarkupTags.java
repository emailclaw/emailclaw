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

/**
 * Custom markup tag constants in chat streaming output.
 *
 * <p>Written into the assistant message body by {@link ChatService}, parsed and rendered by {@link ai.emailclaw.emailclaw.ui.ChatView};
 * Channel runners (such as Emailclaw) also reference the attachment tag pattern when parsing attachment paths.
 */
public final class MessageMarkupTags {
    public static final String ATTACHMENT_OPEN = "<attachment_to_channel>";
    public static final String ATTACHMENT_CLOSE = "</attachment_to_channel>";

    /** Tag pair for attachment return channel (regex capture group is used to extract the path). */
    public static final String ATTACHMENT_PATTERN = ATTACHMENT_OPEN + "(.*?)" + ATTACHMENT_CLOSE;

    public static final String ATTACHMENT_VALUE =
            ATTACHMENT_OPEN + "/path/to/your/file" + ATTACHMENT_CLOSE;

    public static final String TOOL_CALL_OPEN = "<tool_call>";
    public static final String TOOL_CALL_CLOSE = "</tool_call>";
    public static final String TOOL_RESULT_OPEN = "<tool_result>";
    public static final String TOOL_RESULT_CLOSE = "</tool_result>";

    private MessageMarkupTags() {}
}
