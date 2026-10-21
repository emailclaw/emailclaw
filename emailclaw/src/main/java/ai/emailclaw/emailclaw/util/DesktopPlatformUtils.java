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

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cross-platform desktop integration utilities.
 *
 * <p>Provides robust desktop OS interactions (opening files with the system default application,
 * revealing files in the desktop file manager with the item highlighted/selected, and opening URLs
 * in the system default web browser).
 *
 * <p>Specifically handles modern Linux (Ubuntu 22.04/24.04/26.04 GNOME/Wayland/X11) desktop
 * integration where standard {@link java.awt.Desktop} often fails silently or lacks support for
 * directory browsing and file selection. Prioritizes {@code xdg-open}, Freedesktop DBus
 * {@code org.freedesktop.FileManager1.ShowItems}, and {@code gio open} on Linux, {@code open}
 * and {@code open -R} on macOS, and Windows Shell / Explorer on Windows.
 */
public final class DesktopPlatformUtils {

    private static final Logger LOGGER = Logger.getLogger(DesktopPlatformUtils.class.getName());

    private static final boolean IS_LINUX;
    private static final boolean IS_MAC;
    private static final boolean IS_WINDOWS;

    static {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        IS_LINUX = os.contains("linux") || os.contains("nix") || os.contains("nux");
        IS_MAC = os.contains("mac") || os.contains("darwin");
        IS_WINDOWS = os.contains("win");
    }

    private DesktopPlatformUtils() {}

    /**
     * Checks if current platform is Linux/Unix.
     *
     * @return true if running on Linux
     */
    public static boolean isLinux() {
        return IS_LINUX;
    }

    /**
     * Checks if current platform is macOS.
     *
     * @return true if running on macOS
     */
    public static boolean isMac() {
        return IS_MAC;
    }

    /**
     * Checks if current platform is Windows.
     *
     * @return true if running on Windows
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Opens a file or directory using the OS default application asynchronously in a virtual thread.
     *
     * @param path target file or directory path to open
     */
    public static void openFile(Path path) {
        if (path == null) {
            return;
        }
        Thread.startVirtualThread(
                () -> {
                    try {
                        Path absPath = path.toAbsolutePath().normalize();
                        if (!Files.exists(absPath)) {
                            LOGGER.warning("Cannot open non-existent path: " + absPath);
                            return;
                        }
                        if (IS_LINUX) {
                            openLinux(absPath);
                        } else if (IS_MAC) {
                            openMac(absPath);
                        } else if (IS_WINDOWS) {
                            openWindows(absPath);
                        } else {
                            openAwtFallback(absPath);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to open path: " + path, e);
                    }
                });
    }

    /**
     * Reveals a file or directory in the system file manager, highlighting/selecting the target
     * file if supported by the desktop environment.
     *
     * @param path target file or directory path to reveal
     */
    public static void revealInFolder(Path path) {
        if (path == null) {
            return;
        }
        Thread.startVirtualThread(
                () -> {
                    try {
                        Path absPath = path.toAbsolutePath().normalize();
                        if (IS_LINUX) {
                            revealLinux(absPath);
                        } else if (IS_MAC) {
                            revealMac(absPath);
                        } else if (IS_WINDOWS) {
                            revealWindows(absPath);
                        } else {
                            revealAwtFallback(absPath);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to reveal path in folder: " + path, e);
                    }
                });
    }

    /**
     * Opens a web URI in the system default browser.
     *
     * @param uri URI to browse
     */
    public static void browseUri(URI uri) {
        if (uri == null) {
            return;
        }
        Thread.startVirtualThread(
                () -> {
                    try {
                        if (IS_LINUX) {
                            if (executeCommand("xdg-open", uri.toString())) {
                                return;
                            }
                            if (executeCommand("gio", "open", uri.toString())) {
                                return;
                            }
                        } else if (IS_MAC) {
                            if (executeCommand("open", uri.toString())) {
                                return;
                            }
                        } else if (IS_WINDOWS) {
                            if (Desktop.isDesktopSupported()
                                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                Desktop.getDesktop().browse(uri);
                                return;
                            }
                            if (executeCommand("cmd.exe", "/c", "start", "\"\"", uri.toString())) {
                                return;
                            }
                        }
                        if (Desktop.isDesktopSupported()
                                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(uri);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to browse URI: " + uri, e);
                    }
                });
    }

    private static void openLinux(Path path) {
        if (executeCommand("xdg-open", path.toString())) {
            LOGGER.info("Opened file via xdg-open: " + path);
            return;
        }
        if (executeCommand("gio", "open", path.toString())) {
            LOGGER.info("Opened file via gio open: " + path);
            return;
        }
        openAwtFallback(path);
    }

    private static void revealLinux(Path path) {
        if (!Files.isDirectory(path)) {
            String uri = path.toUri().toString();
            if (uri.startsWith("file:/") && !uri.startsWith("file:///")) {
                uri = "file://" + uri.substring(5);
            }
            boolean dbusOk =
                    executeCommand(
                            "dbus-send",
                            "--session",
                            "--dest=org.freedesktop.FileManager1",
                            "--type=method_call",
                            "/org/freedesktop/FileManager1",
                            "org.freedesktop.FileManager1.ShowItems",
                            "array:string:" + uri,
                            "string:");
            if (dbusOk) {
                LOGGER.info("Revealed and selected artifact via DBus ShowItems: " + path);
                return;
            }
            if (executeCommand("nautilus", "--select", path.toString())) {
                LOGGER.info("Revealed and selected artifact via nautilus --select: " + path);
                return;
            }
        }

        Path dir = Files.isDirectory(path) ? path : path.getParent();
        if (dir == null || !Files.exists(dir)) {
            LOGGER.warning("Parent directory does not exist for: " + path);
            return;
        }
        if (executeCommand("xdg-open", dir.toString())) {
            LOGGER.info("Opened parent directory via xdg-open: " + dir);
            return;
        }
        if (executeCommand("gio", "open", dir.toString())) {
            LOGGER.info("Opened parent directory via gio open: " + dir);
            return;
        }
        revealAwtFallback(dir);
    }

    private static void openMac(Path path) {
        if (executeCommand("open", path.toString())) {
            LOGGER.info("Opened file via macOS open: " + path);
            return;
        }
        openAwtFallback(path);
    }

    private static void revealMac(Path path) {
        if (executeCommand("open", "-R", path.toString())) {
            LOGGER.info("Revealed file via macOS open -R: " + path);
            return;
        }
        Path dir = Files.isDirectory(path) ? path : path.getParent();
        if (dir != null && Files.exists(dir)) {
            executeCommand("open", dir.toString());
        }
    }

    private static void openWindows(Path path) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(path.toFile());
                LOGGER.info("Opened file via Windows Desktop.open: " + path);
                return;
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Windows Desktop.open failed, trying cmd start", ex);
            }
        }
        if (executeCommand("cmd.exe", "/c", "start", "\"\"", path.toString())) {
            LOGGER.info("Opened file via Windows cmd start: " + path);
            return;
        }
        openAwtFallback(path);
    }

    private static void revealWindows(Path path) {
        if (Files.isDirectory(path)) {
            if (executeCommand("explorer.exe", path.toString())) {
                LOGGER.info("Opened directory via Windows explorer: " + path);
                return;
            }
        } else {
            if (executeCommand("explorer.exe", "/select,", path.toString())) {
                LOGGER.info("Revealed file via Windows explorer /select: " + path);
                return;
            }
        }
        Path dir = Files.isDirectory(path) ? path : path.getParent();
        if (dir != null && Files.exists(dir)) {
            revealAwtFallback(dir);
        }
    }

    private static void openAwtFallback(Path path) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
                LOGGER.info("Opened file via Desktop.open: " + path);
            } else {
                LOGGER.warning("Desktop.Action.OPEN is not supported on this platform: " + path);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open via Desktop.open: " + path, e);
        }
    }

    private static void revealAwtFallback(Path dir) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
                LOGGER.info("Revealed directory via Desktop.open: " + dir);
            } else {
                LOGGER.warning("Desktop.Action.OPEN is not supported on this platform: " + dir);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to reveal directory via Desktop.open: " + dir, e);
        }
    }

    private static boolean executeCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                return true;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return true;
            }
            LOGGER.fine(
                    () ->
                            "Command returned non-zero exit code "
                                    + exitCode
                                    + ": "
                                    + String.join(" ", command));
            return false;
        } catch (IOException e) {
            LOGGER.fine(
                    () ->
                            "Command failed to launch: "
                                    + String.join(" ", command)
                                    + " ("
                                    + e.getMessage()
                                    + ")");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
