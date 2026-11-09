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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Isolates Java Util Logging (JUL) output during CLI execution to preserve terminal UI integrity.
 *
 * <p>Redirects all background and engine logs away from standard error/out to a dedicated runtime
 * log file, preventing asynchronous logging from corrupting JLine terminal rendering.
 */
public final class JulLogIsolator {

    private static final Logger LOGGER = Logger.getLogger(JulLogIsolator.class.getName());

    private final Path logFilePath;
    private final List<Handler> removedHandlers = new ArrayList<>();
    private FileHandler fileHandler;
    private boolean isolated = false;

    /**
     * Constructs a logger isolator targeting the specified file path.
     *
     * @param logFilePath the destination log file path for isolated CLI logs
     */
    public JulLogIsolator(Path logFilePath) {
        this.logFilePath = logFilePath;
    }

    /**
     * Activates log isolation by removing console handlers from root logger and attaching a file handler.
     */
    public synchronized void isolate() {
        if (isolated) {
            return;
        }

        try {
            if (logFilePath.getParent() != null) {
                Files.createDirectories(logFilePath.getParent());
            }

            Logger rootLogger = LogManager.getLogManager().getLogger("");
            Handler[] currentHandlers = rootLogger.getHandlers();
            for (Handler handler : currentHandlers) {
                if (handler instanceof ConsoleHandler) {
                    rootLogger.removeHandler(handler);
                    removedHandlers.add(handler);
                }
            }

            fileHandler = new FileHandler(logFilePath.toString(), 10 * 1024 * 1024, 3, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            rootLogger.addHandler(fileHandler);
            isolated = true;

            LOGGER.log(
                    Level.INFO,
                    "CLI log isolation activated, runtime logs redirected to: {0}",
                    logFilePath);
        } catch (IOException e) {
            System.err.println(
                    "[Warning] Failed to initialize file logging for CLI mode: " + e.getMessage());
        }
    }

    /**
     * Restores previous root logger handlers on CLI exit.
     */
    public synchronized void restore() {
        if (!isolated) {
            return;
        }

        Logger rootLogger = LogManager.getLogManager().getLogger("");
        if (fileHandler != null) {
            rootLogger.removeHandler(fileHandler);
            fileHandler.close();
            fileHandler = null;
        }

        for (Handler handler : removedHandlers) {
            rootLogger.addHandler(handler);
        }
        removedHandlers.clear();
        isolated = false;
    }
}
