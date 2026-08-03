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
package ai.emailclaw.emailclaw;

import ai.emailclaw.emailclaw.service.WakeupDispatcherService;
import ai.emailclaw.emailclaw.util.ChromeBrowserSupport;
import ai.emailclaw.emailclaw.util.ThreadUtils;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Emailclaw background service (Headless Service Mode) startup entry class.
 *
 * <p>Used to start independently in a server or background environment without a graphical interface,
 * activating channels such as DingTalk and Emailclaw to provide automated services.
 *
 * <p>Shares the same initialization logic with {@link App} to ensure consistent behavior in both startup modes.
 */
public class ServiceApp {

    private static final Logger LOGGER = Logger.getLogger(ServiceApp.class.getName());

    public static void main(String[] args) {
        LOGGER.info("====================================================");
        LOGGER.info(" Emailclaw Service (Headless Daemon Mode) is starting...");
        LOGGER.info("====================================================");

        // Explicitly declare to disable JavaFX UI thread mechanism scheduling
        ThreadUtils.setFxActive(false);

        if (ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE == null) {
            LOGGER.warning(
                    "Official Chrome/Edge is not detected when the background service starts,"
                            + " dynamic browsing capability will be limited.");
        } else {
            LOGGER.info(
                    "Official Chrome/Edge detected when background service starts: "
                            + ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE);
        }

        // 1. Execute general initialization (shared with App)
        ApplicationBootstrap.BootstrapResult result = ApplicationBootstrap.initialize();

        // 2. Initialize wakeup dispatcher service
        WakeupDispatcherService wakeupDispatcherService = null;
        if (result.messageBusService() != null) {
            wakeupDispatcherService =
                    new WakeupDispatcherService(
                            result.messageBusService(),
                            ApplicationBootstrap.createWakeupTarget(result));
            wakeupDispatcherService.start();
            LOGGER.log(Level.INFO, "Wakeup dispatcher service started successfully");
        }

        LOGGER.info("====================================================");
        LOGGER.info(" Emailclaw Service is ready and listening in the background...");
        LOGGER.info(" Press Ctrl+C or send SIGTERM signal to exit gracefully.");
        LOGGER.info("====================================================");

        // 3. Register JVM shutdown hook to ensure graceful cleanup when container or system service
        // manager sends SIGTERM signal
        WakeupDispatcherService finalWakeupDispatcherService = wakeupDispatcherService;
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    LOGGER.info(
                                            "Received termination signal, gracefully shutting down"
                                                    + " background service...");
                                    if (finalWakeupDispatcherService != null) {
                                        finalWakeupDispatcherService.close();
                                    }
                                    ApplicationBootstrap.shutdown(result);
                                }));

        // Block the main thread to keep the background process running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            LOGGER.log(
                    Level.WARNING,
                    "Background service main thread was unexpectedly interrupted",
                    e);
            Thread.currentThread().interrupt();
        }
    }
}
