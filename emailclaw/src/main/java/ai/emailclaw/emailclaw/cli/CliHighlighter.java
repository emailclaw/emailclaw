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

import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;

/**
 * Syntax highlighter for Emailclaw CLI inputs, styling slash commands, flags, and literals.
 */
public class CliHighlighter implements Highlighter {

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        if (buffer == null || buffer.isEmpty()) {
            return new AttributedString("");
        }

        AttributedStringBuilder asb = new AttributedStringBuilder();
        String trimmed = buffer.trim();

        if (trimmed.startsWith("/")) {
            int firstSpace = buffer.indexOf(' ');
            if (firstSpace == -1) {
                // Command only
                asb.style(CliTheme.STYLE_BOLD_CYAN).append(buffer);
            } else {
                String cmd = buffer.substring(0, firstSpace);
                asb.style(CliTheme.STYLE_BOLD_CYAN).append(cmd);

                String rest = buffer.substring(firstSpace);
                int secondSpace = rest.trim().indexOf(' ');
                if (secondSpace == -1 && !rest.isBlank()) {
                    asb.style(CliTheme.STYLE_YELLOW).append(rest);
                } else {
                    asb.style(CliTheme.STYLE_DEFAULT).append(rest);
                }
            }
        } else {
            asb.style(CliTheme.STYLE_DEFAULT).append(buffer);
        }

        return asb.toAttributedString();
    }
}
