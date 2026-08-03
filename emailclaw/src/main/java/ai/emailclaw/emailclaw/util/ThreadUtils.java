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
package ai.emailclaw.emailclaw.util;

import javafx.application.Platform;

/**
 * Thread utility class, provides unified execution scheduling for GUI threads and background threads.
 * Used to implement compatible execution of the same backend package in desktop GUI mode and headless Service mode.
 */
public class ThreadUtils {

    private static volatile boolean fxActive = false;

    /**
     * Sets whether the current JavaFX environment is active.
     *
     * @param active true if in JavaFX desktop mode
     */
    public static void setFxActive(boolean active) {
        fxActive = active;
    }

    /**
     * Whether it is currently in JavaFX desktop mode.
     */
    public static boolean isFxActive() {
        return fxActive;
    }

    /**
     * Executes the task in a suitable environment.
     * If in JavaFX desktop environment, schedules execution via Platform.runLater;
     * If in Headless/Service background mode, executes directly synchronously or in the current thread.
     *
     * @param runnable task to execute
     */
    public static void run(Runnable runnable) {
        if (fxActive) {
            Platform.runLater(runnable);
        } else {
            runnable.run();
        }
    }
}
