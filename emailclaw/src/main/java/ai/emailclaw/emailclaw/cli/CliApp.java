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

import ai.emailclaw.emailclaw.ApplicationBootstrap;
import ai.emailclaw.emailclaw.Launcher;
import ai.emailclaw.emailclaw.service.WakeupDispatcherService;
import ai.emailclaw.emailclaw.util.ThreadUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Command-Line Interface (CLI) application entry point and composition root for Emailclaw.
 *
 * <p>Supports interactive rich terminal conversations (REPL) powered by JLine 4, persistent command
 * history, tab auto-completion, syntax highlighting, and non-interactive batch runs for scripts.
 */
public class CliApp {

    private static final Logger LOGGER = Logger.getLogger(CliApp.class.getName());

    public static void main(String[] args) {
        // Disable JavaFX UI thread scheduling
        ThreadUtils.setFxActive(false);

        CliOptions options = CliOptions.parse(args);

        if (options.mode() == CliOptions.Mode.HELP) {
            printHelp();
            return;
        }

        if (options.mode() == CliOptions.Mode.VERSION) {
            System.out.println("Emailclaw " + Launcher.APP_VERSION);
            return;
        }

        LOGGER.log(Level.INFO, "Emailclaw CLI is launching in mode: {0}", options.mode());

        // 1. Core Composition Root initialization via Pure DI
        ApplicationBootstrap.BootstrapResult result = ApplicationBootstrap.initialize();

        // 2. Isolate JUL logging during CLI session to preserve terminal rendering
        JulLogIsolator logIsolator =
                new JulLogIsolator(result.paths().logsDir.resolve("cli-runtime.log"));
        logIsolator.isolate();

        // 3. Initialize background WakeupDispatcherService
        WakeupDispatcherService wakeupDispatcherService = null;
        if (result.messageBusService() != null) {
            wakeupDispatcherService =
                    new WakeupDispatcherService(
                            result.messageBusService(),
                            ApplicationBootstrap.createWakeupTarget(result),
                            result.projectService());
            wakeupDispatcherService.start();
            LOGGER.log(Level.INFO, "Wakeup dispatcher service started for CLI mode");
        }

        // 4. Register shutdown hook for graceful cleanup
        WakeupDispatcherService finalWakeupService = wakeupDispatcherService;
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    LOGGER.info("Shutdown hook triggered for Emailclaw CLI");
                                    if (finalWakeupService != null) {
                                        finalWakeupService.close();
                                    }
                                    logIsolator.restore();
                                    ApplicationBootstrap.shutdown(result);
                                }));

        // 5. Dispatch based on mode
        if (options.mode() == CliOptions.Mode.RUN) {
            PrintWriter stdOutWriter = new PrintWriter(System.out, true);
            CliRunExecutor runExecutor = new CliRunExecutor(result, stdOutWriter);
            int exitCode = runExecutor.execute(options);
            if (wakeupDispatcherService != null) {
                wakeupDispatcherService.close();
            }
            logIsolator.restore();
            ApplicationBootstrap.shutdown(result);
            System.exit(exitCode);
        } else {
            // Interactive REPL Mode
            Terminal terminal = null;
            try {
                terminal =
                        TerminalBuilder.builder()
                                .system(true)
                                .encoding(StandardCharsets.UTF_8)
                                .nativeSignals(true)
                                .signalHandler(Terminal.SignalHandler.SIG_IGN)
                                .build();

                DefaultHistory history = new DefaultHistory();

                LineReader lineReader =
                        LineReaderBuilder.builder()
                                .terminal(terminal)
                                .appName("emailclaw")
                                .completer(new CliCompleter(result.providerService()))
                                .highlighter(new CliHighlighter())
                                .history(history)
                                .variable(LineReader.HISTORY_FILE, result.paths().cliHistoryFile)
                                .variable(LineReader.HISTORY_FILE_SIZE, 5000)
                                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                                .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                                .option(LineReader.Option.AUTO_GROUP, true)
                                .option(LineReader.Option.CASE_INSENSITIVE, true)
                                .build();

                CliSessionRunner runner =
                        new CliSessionRunner(result, terminal, lineReader, history, result.paths());
                runner.start();

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to initialize JLine interactive terminal", e);
                System.err.println(
                        "[Error] Failed to initialize terminal interface: " + e.getMessage());
            } finally {
                if (terminal != null) {
                    try {
                        terminal.close();
                    } catch (IOException ignored) {
                    }
                }
                if (wakeupDispatcherService != null) {
                    wakeupDispatcherService.close();
                }
                logIsolator.restore();
                ApplicationBootstrap.shutdown(result);
            }
        }
    }

    private static void printHelp() {
        System.out.println(
                "Emailclaw " + Launcher.APP_VERSION + " - Industrial-Grade Autonomous AI Agent");
        System.out.println();
        System.out.println("Usage:");
        System.out.println(
                "  emailclaw [options]             Launch interactive desktop GUI (or CLI if"
                        + " headless)");
        System.out.println(
                "  emailclaw cli [options]         Launch interactive CLI REPL session (JLine 4)");
        System.out.println(
                "  emailclaw run <prompt>          Execute single prompt in non-interactive batch"
                        + " mode");
        System.out.println(
                "  emailclaw service               Run in background headless daemon mode");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -p, --prompt <text>             Specify the prompt text to execute");
        System.out.println("  -a, --agent <id>                Target specific agent ID");
        System.out.println(
                "  -s, --session <id>              Resume or attach to an existing session ID");
        System.out.println(
                "  -q, --quiet                     Suppress headers and decorations in run mode");
        System.out.println(
                "  --json                          Output response events as structured JSON");
        System.out.println("  -h, --help                      Show this help message");
        System.out.println("  -v, --version                   Show version information");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  emailclaw cli");
        System.out.println("  emailclaw run \"Check unread emails from boss\"");
        System.out.println("  cat summary.txt | emailclaw run -p \"Review this text\"");
    }
}
