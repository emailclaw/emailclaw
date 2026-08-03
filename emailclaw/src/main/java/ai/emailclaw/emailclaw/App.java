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

import ai.emailclaw.emailclaw.service.MessageBusService;
import ai.emailclaw.emailclaw.service.WakeupDispatcherService;
import ai.emailclaw.emailclaw.ui.MainWindow;
import ai.emailclaw.emailclaw.util.ChromeBrowserSupport;
import ai.emailclaw.emailclaw.util.ThreadUtils;
import java.awt.Desktop;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Emailclaw desktop application main entry class.
 *
 * <p>Inherits from JavaFX's Application class, responsible for the startup flow of the entire application:
 * <ol>
 *   <li>Call {@link ApplicationBootstrap#initialize()} to execute general initialization</li>
 *   <li>Start wakeup dispatcher service</li>
 *   <li>Assemble and show JavaFX main window (MainWindow)</li>
 * </ol>
 *
 * <p>Shares the same initialization logic with {@link ServiceApp}, ensuring consistent behavior between the two startup methods.
 */
public class App extends Application {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    /**
     * Application initialization result, used for lifecycle management.
     */
    private ApplicationBootstrap.BootstrapResult bootstrapResult;

    /**
     * Wakeup dispatcher service.
     */
    private WakeupDispatcherService wakeupDispatcherService;

    @Override
    public void start(Stage stage) {
        ThreadUtils.setFxActive(true);
        LOGGER.info("Emailclaw application is starting...");
        // 1. Execute general initialization (shared with ServiceApp)
        bootstrapResult = ApplicationBootstrap.initialize();
        // 2. Initialize wakeup dispatcher service
        MessageBusService messageBusService = bootstrapResult.messageBusService();
        if (messageBusService != null) {
            wakeupDispatcherService =
                    new WakeupDispatcherService(
                            messageBusService,
                            ApplicationBootstrap.createWakeupTarget(bootstrapResult));
            wakeupDispatcherService.start();
            LOGGER.log(Level.INFO, "Wakeup dispatcher service started");
        }
        LOGGER.info("Building user interface...");
        // 3. Create and show main window
        MainWindow root =
                new MainWindow(
                        bootstrapResult.repository(),
                        bootstrapResult.agentService(),
                        bootstrapResult.projectService(),
                        bootstrapResult.providerService(),
                        bootstrapResult.skillService(),
                        bootstrapResult.toolService(),
                        bootstrapResult.chatService(),
                        bootstrapResult.channelService(),
                        bootstrapResult.cronJobService(),
                        bootstrapResult.mcpService(),
                        bootstrapResult.acpService(),
                        bootstrapResult.securityService(),
                        bootstrapResult.backupService(),
                        bootstrapResult.pluginManager(),
                        bootstrapResult.marketService(),
                        messageBusService,
                        bootstrapResult.toolRuntimeContext());
        // Set default window size
        Scene scene = new Scene(root, 1680, 960);
        // Load global CSS stylesheet
        String cssPath =
                getClass().getResource("/ai/emailclaw/emailclaw/css/app.css").toExternalForm();
        scene.getStylesheets().add(cssPath);
        stage.setTitle("Emailclaw Desktop - Personal Agent Management Terminal");
        try {
            java.net.URL logoUrl =
                    getClass().getResource("/ai/emailclaw/emailclaw/images/logo.jpg");
            if (logoUrl != null) {
                String urlStr = logoUrl.toExternalForm();
                stage.getIcons()
                        .addAll(
                                new javafx.scene.image.Image(urlStr, 16, 16, true, true),
                                new javafx.scene.image.Image(urlStr, 32, 32, true, true),
                                new javafx.scene.image.Image(urlStr, 48, 48, true, true),
                                new javafx.scene.image.Image(urlStr, 64, 64, true, true),
                                new javafx.scene.image.Image(urlStr, 128, 128, true, true),
                                new javafx.scene.image.Image(urlStr, 256, 256, true, true),
                                new javafx.scene.image.Image(urlStr, 512, 512, true, true));
            } else {
                LOGGER.warning("Logo image not found in resources");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load application logo", e);
        }
        stage.setScene(scene);
        stage.show();
        // If official Chrome/Edge is not detected at startup, show installation prompt.
        if (ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE == null) {
            LOGGER.warning(
                    "Official Chrome/Edge not detected at startup, showing installation prompt.");
            showChromeRequirementDialog(stage, ChromeBrowserSupport.INSTALL_GUIDE);
        } else {
            LOGGER.info(
                    "Official Chrome/Edge detected at startup: "
                            + ChromeBrowserSupport.LOCAL_CHROME_EDGE_EXECUTABLE);
        }
        LOGGER.info("Emailclaw desktop application started successfully.");
    }

    @Override
    public void stop() {
        LOGGER.info("Closing application...");
        if (wakeupDispatcherService != null) {
            wakeupDispatcherService.close();
        }
        ApplicationBootstrap.shutdown(bootstrapResult);
    }

    /**
     * Show Chrome dependency prompt dialog, explaining the impact of missing official Chrome/Edge and installation steps.
     */
    private static void showChromeRequirementDialog(Stage owner, String message) {
        try {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle("Functionality Restricted Prompt");
            dialog.setHeaderText("Official Chrome/Edge browser not detected");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
            Label introLabel =
                    new Label(
                            "Currently only providing the ability to get specified static website"
                                + " content. To support dynamic webpages and a stronger automation"
                                + " experience, please install the official Chrome/Edge.");
            introLabel.setWrapText(true);
            TextArea instructionsArea = new TextArea(message);
            instructionsArea.setEditable(false);
            instructionsArea.setWrapText(true);
            instructionsArea.setPrefRowCount(12);
            instructionsArea.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
            Button copyButton = new Button("Copy installation instructions");
            copyButton.setOnAction(event -> copyToClipboard(message));
            Button openInstallPageButton = new Button("Open installation page");
            openInstallPageButton.setOnAction(event -> openInstallPage());
            HBox actionBar = new HBox(8, copyButton, openInstallPageButton);
            VBox content = new VBox(12, introLabel, instructionsArea, actionBar);
            content.setPrefWidth(760);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefWidth(780);
            dialog.showAndWait();
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Failed to show Chrome installation prompt dialog", ex);
        }
    }

    private static void copyToClipboard(String content) {
        try {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
            LOGGER.info("Chrome installation instructions copied to system clipboard.");
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Failed to copy Chrome installation instructions", ex);
        }
    }

    private static void openInstallPage() {
        try {
            String installUrl = ChromeBrowserSupport.INSTALL_GUIDE;
            String url = installUrl.substring(installUrl.lastIndexOf("https://"));
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                LOGGER.info("Attempted to open Chrome installation page: " + url);
            }
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Failed to open Chrome installation page", ex);
        }
    }

    /**
     * Show error dialog.
     */
    private static void showErrorDialog(Throwable t) {
        try {
            Alert alert = new Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("System Exception");
            alert.setHeaderText("An unexpected system exception occurred");
            alert.setContentText(t.getMessage() != null ? t.getMessage() : t.getClass().getName());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            javafx.scene.control.TextArea textArea =
                    new javafx.scene.control.TextArea(sw.toString());
            textArea.setEditable(false);
            textArea.setWrapText(false);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            javafx.scene.layout.VBox expContent =
                    new javafx.scene.layout.VBox(
                            new javafx.scene.control.Label("Exception stack trace:"), textArea);
            javafx.scene.layout.VBox.setVgrow(textArea, javafx.scene.layout.Priority.ALWAYS);
            alert.getDialogPane().setExpandableContent(expContent);
            alert.getDialogPane().setExpanded(false);
            alert.showAndWait();
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to show exception dialog", e);
        }
    }

    /**
     * The true entry point of program execution.
     */
    public static void main(String[] args) {
        // Register global uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> {
                    LOGGER.log(
                            Level.SEVERE,
                            "Uncaught exception occurred in thread " + thread.getName(),
                            throwable);
                    try {
                        if (Platform.isFxApplicationThread()) {
                            showErrorDialog(throwable);
                        } else {
                            Platform.runLater(() -> showErrorDialog(throwable));
                        }
                    } catch (Throwable ex) {
                        LOGGER.log(Level.SEVERE, "Failed to show exception info on UI", ex);
                    }
                });
        // Set log format or perform other global pre-configuration
        LOGGER.info("Preparing to start JavaFX runtime environment...");
        launch(args);
        System.exit(0);
    }
}
