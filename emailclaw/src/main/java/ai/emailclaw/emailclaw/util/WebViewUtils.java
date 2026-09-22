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

import ai.emailclaw.emailclaw.storage.AppPaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Utility class for configuring JavaFX {@link WebView} and {@link WebEngine} instances.
 *
 * <p>Centrally enforces custom user data directory paths ({@code ~/emailclaw/.webview})
 * to prevent JavaFX from generating default directories under the user's home directory
 * (such as {@code ~/.ai.emailclaw.emailclaw.FxApp/webview/localstorage}).
 */
public final class WebViewUtils {

    private static final Logger LOGGER = Logger.getLogger(WebViewUtils.class.getName());

    private WebViewUtils() {}

    /**
     * Configures the user data directory for the given {@link WebView} instance.
     * Ensures that WebKit local storage, cookies, and lock files are stored under
     * {@code ~/emailclaw/.webview} instead of the user home root.
     *
     * @param webView the WebView instance whose engine should be configured
     */
    public static void configureUserDataDirectory(WebView webView) {
        if (webView != null) {
            configureUserDataDirectory(webView.getEngine());
        }
    }

    private static volatile Path webviewDataDir = null;

    /**
     * Sets the root WebView user data directory.
     *
     * @param dir the webview data root directory
     */
    public static void setWebviewDataDir(Path dir) {
        webviewDataDir = dir;
    }

    /**
     * Gets the root WebView user data directory, falling back to default AppPaths if unconfigured.
     *
     * @return the webview data root directory
     */
    public static Path getWebviewDataDir() {
        if (webviewDataDir == null) {
            return AppPaths.fromDefault().webviewDir;
        }
        return webviewDataDir;
    }

    /**
     * Configures the user data directory for the given {@link WebEngine} instance.
     *
     * @param engine the WebEngine instance to configure
     */
    public static void configureUserDataDirectory(WebEngine engine) {
        if (engine == null) {
            return;
        }
        try {
            Path targetDir = getWebviewDataDir();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            File dirFile = targetDir.toFile();
            engine.setUserDataDirectory(dirFile);
            LOGGER.log(Level.FINE, "Configured WebEngine userDataDirectory to: {0}", targetDir);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to configure WebEngine userDataDirectory", e);
        }
    }

    private static void deleteDirectoryRecursively(Path root) throws IOException {
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
