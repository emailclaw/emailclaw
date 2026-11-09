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

import java.util.Objects;

/**
 * Immutable command-line configuration options record for Emailclaw CLI mode.
 *
 * @param mode the selected execution mode (REPL, RUN, HELP, VERSION)
 * @param prompt the user prompt to execute in RUN mode
 * @param agentId the optional target agent identifier
 * @param sessionId the optional target session identifier
 * @param quiet whether to suppress terminal headers and banners in RUN mode
 * @param jsonOutput whether to output structured JSON in RUN mode
 */
public record CliOptions(
        Mode mode,
        String prompt,
        String agentId,
        String sessionId,
        boolean quiet,
        boolean jsonOutput) {

    /**
     * Enumeration of CLI execution modes.
     */
    public enum Mode {
        REPL,
        RUN,
        HELP,
        VERSION
    }

    public CliOptions {
        Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * Parses the command-line argument array into a {@link CliOptions} record.
     *
     * @param args the raw command-line arguments
     * @return the parsed {@code CliOptions} instance
     */
    public static CliOptions parse(String[] args) {
        Mode mode = Mode.REPL;
        String prompt = null;
        String agentId = null;
        String sessionId = null;
        boolean quiet = false;
        boolean jsonOutput = false;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i].trim();
                if ("--help".equalsIgnoreCase(arg)
                        || "-h".equalsIgnoreCase(arg)
                        || "help".equalsIgnoreCase(arg)) {
                    mode = Mode.HELP;
                    break;
                }
                if ("--version".equalsIgnoreCase(arg)
                        || "-v".equalsIgnoreCase(arg)
                        || "version".equalsIgnoreCase(arg)) {
                    mode = Mode.VERSION;
                    break;
                }
                if ("run".equalsIgnoreCase(arg)
                        || "--run".equalsIgnoreCase(arg)
                        || "-r".equalsIgnoreCase(arg)) {
                    mode = Mode.RUN;
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        prompt = args[++i];
                    }
                    continue;
                }
                if ("--prompt".equalsIgnoreCase(arg) || "-p".equalsIgnoreCase(arg)) {
                    mode = Mode.RUN;
                    if (i + 1 < args.length) {
                        prompt = args[++i];
                    }
                    continue;
                }
                if ("--agent".equalsIgnoreCase(arg) || "-a".equalsIgnoreCase(arg)) {
                    if (i + 1 < args.length) {
                        agentId = args[++i];
                    }
                    continue;
                }
                if ("--session".equalsIgnoreCase(arg) || "-s".equalsIgnoreCase(arg)) {
                    if (i + 1 < args.length) {
                        sessionId = args[++i];
                    }
                    continue;
                }
                if ("--quiet".equalsIgnoreCase(arg) || "-q".equalsIgnoreCase(arg)) {
                    quiet = true;
                    continue;
                }
                if ("--json".equalsIgnoreCase(arg)) {
                    jsonOutput = true;
                    continue;
                }
                if ("cli".equalsIgnoreCase(arg)
                        || "--cli".equalsIgnoreCase(arg)
                        || "-c".equalsIgnoreCase(arg)) {
                    mode = Mode.REPL;
                    continue;
                }
                // Unrecognized bare argument when mode is RUN and prompt is not yet assigned
                if (mode == Mode.RUN && prompt == null && !arg.startsWith("-")) {
                    prompt = arg;
                }
            }
        }

        return new CliOptions(mode, prompt, agentId, sessionId, quiet, jsonOutput);
    }
}
