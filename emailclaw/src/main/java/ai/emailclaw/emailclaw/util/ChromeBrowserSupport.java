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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Official Chrome/Edge browser detection and installation prompt tool.
 *
 * <p>This tool is used to determine whether the official Chrome is installed in the current environment when the application starts,
 * and provides user-facing installation instructions for missing scenarios. It also applies to Playwright's pre-initialization validation for browsers.
 */
public final class ChromeBrowserSupport {

    private static final Logger LOGGER = Logger.getLogger(ChromeBrowserSupport.class.getName());

    private static final String OVERRIDE_PROPERTY = "chrome.or.edge.path";
    private static final String OVERRIDE_ENV = "CHROME_OR_EDGE_PATH";
    private static final String CURRENT_OS_NAME =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    public static final String INSTALL_GUIDE = buildInstallGuide();
    public static final String LOCAL_CHROME_EDGE_EXECUTABLE = findLocalBrowserExecutable();

    private ChromeBrowserSupport() {
        // Utility class, instantiation prohibited.
    }

    /**
     * Find the installed and available official Chrome/Edge browser in the current system.
     * <p>Priority:
     * 1. Browser path explicitly specified in system properties or environment variables;
     * 2. Enumerate common installation paths based on the current operating system;
     * 3. Find Chrome/Edge executable files in PATH via system commands.
     * <p>Emailclaw's dynamic web capability requires official Chrome/Edge, so it no longer accepts fallback paths for Firefox or Chromium to ensure behavior is consistent with the software's positioning.
     */
    private static String findLocalBrowserExecutable() {
        LOGGER.info("Start checking official Chrome/Edge browser executable...");

        String override = resolveOverridePath();
        if (override != null && !override.isBlank() && !override.contains("snap")) {
            File candidate = new File(override);
            if (candidate.exists() && candidate.isFile()) {
                LOGGER.info("Using explicitly configured Chrome path: " + override);
                return override;
            }
            LOGGER.warning(
                    "Configured Chrome/Edge path does not exist, continuing with automatic"
                            + " detection: "
                            + override);
        }

        List<String> candidates = buildCandidatePaths(CURRENT_OS_NAME);
        for (String candidatePath : candidates) {
            if (!candidatePath.contains("snap") && isExecutableAvailable(candidatePath)) {
                LOGGER.info("Detected official Chrome/Edge: " + candidatePath);
                return candidatePath;
            }
        }

        LOGGER.warning(
                "No official Chrome/Edge browser detected, an installation guide will be prompted"
                        + " at startup, and browser initialization will be blocked.");
        return null;
    }

    /**
     * Browser detection result.
     *
     * @param available whether official Chrome/Edge is detected
     * @param executablePath executable file path
     * @param osName current operating system name
     * @param userMessage installation prompt message for the current system
     */
    public record BrowserDetectionResult(
            boolean available, String executablePath, String osName, String userMessage) {}

    /**
     * Generate user-facing installation prompt message.
     */
    public static String buildInstallGuide() {
        String osName = CURRENT_OS_NAME;
        StringBuilder builder = new StringBuilder();
        builder.append("Functionality restricted: No Chrome/Edge browser detected!")
                .append(System.lineSeparator())
                .append(
                        "Currently, only the function of getting the web page content of a"
                                + " specified static website is provided.")
                .append(System.lineSeparator())
                .append(
                        "To support dynamic websites and ensure the best stability and most"
                            + " powerful operating experience, this software relies on the official"
                            + " original Chrome/Edge.")
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Solution:");

        if (osName.contains("win")) {
            builder.append(System.lineSeparator())
                    .append(
                            "Please open the terminal and run the following command to install the"
                                    + " official Chrome:")
                    .append(System.lineSeparator())
                    .append("winget install --id Google.Chrome -e")
                    .append(System.lineSeparator())
                    .append("After installation is complete, please restart this software.")
                    .append(System.lineSeparator())
                    .append("Official installation page: https://www.google.com/chrome/");
        } else if (osName.contains("mac")) {
            builder.append(System.lineSeparator())
                    .append(
                            "Please open the terminal and run the following command to install the"
                                    + " official Chrome:")
                    .append(System.lineSeparator())
                    .append("brew install --cask google-chrome")
                    .append(System.lineSeparator())
                    .append("After installation is complete, please restart this software.")
                    .append(System.lineSeparator())
                    .append("Official installation page: https://www.google.com/chrome/");
        } else {
            builder.append(System.lineSeparator())
                    .append(
                            "Please open the terminal and run the following two commands in"
                                    + " sequence to install:")
                    .append(System.lineSeparator())
                    .append(
                            "wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb")
                    .append(System.lineSeparator())
                    .append("sudo apt install -y ./google-chrome-stable_current_amd64.deb")
                    .append(System.lineSeparator())
                    .append("After installation is complete, please restart this software.")
                    .append(System.lineSeparator())
                    .append("Official installation page: https://www.google.com/chrome/");
        }

        LOGGER.info("Generated Chrome installation prompt message, target OS: " + osName);
        return builder.toString();
    }

    // Under Playwright's architecture, calling the system's built-in Chrome and Edge is equivalent
    // in mechanism and risk.
    private static List<String> buildCandidatePaths(String osName) {
        List<String> candidates = new ArrayList<>();
        if (osName.contains("win")) {
            // Google Chrome (Windows)
            candidates.add("C:/Program Files/Google/Chrome/Application/chrome.exe");
            candidates.add("C:/Program Files (x86)/Google/Chrome/Application/chrome.exe");
            candidates.add("C:/Program Files/Google/Chrome/Application/chrome");
            candidates.add("C:/Program Files (x86)/Google/Chrome/Application/chrome");

            // Microsoft Edge (Windows)
            // Note: Edge is installed in the x86 directory by default, even on 64-bit systems.
            candidates.add("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe");
            candidates.add("C:/Program Files/Microsoft/Edge/Application/msedge.exe");
        } else if (osName.contains("mac")) {
            // Google Chrome (macOS)
            candidates.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");

            // Microsoft Edge (macOS)
            candidates.add("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
        } else {
            // Google Chrome (Linux)
            candidates.add("/usr/bin/google-chrome");
            candidates.add("/usr/bin/google-chrome-stable");
            candidates.add("/opt/google/chrome/google-chrome");
            candidates.add("/snap/bin/chromium");
            candidates.add("google-chrome");
            candidates.add("google-chrome-stable");

            // Microsoft Edge (Linux)
            candidates.add("/usr/bin/microsoft-edge");
            candidates.add("/usr/bin/microsoft-edge-stable");
            candidates.add("/opt/microsoft/msedge/msedge"); // Actual binary file location
            candidates.add("microsoft-edge");
            candidates.add("microsoft-edge-stable");
        }
        return candidates;
    }

    private static boolean isExecutableAvailable(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        File file = new File(candidate);
        if (file.exists() && file.isFile()) {
            return file.canExecute();
        }

        try {
            ProcessBuilder pb;
            if (CURRENT_OS_NAME.contains("win")) {
                pb = new ProcessBuilder("where", candidate);
            } else {
                pb = new ProcessBuilder("which", candidate);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            }
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(
                    Level.FINE,
                    "Failed to execute browser executable file probe: " + candidate,
                    ex);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static String resolveOverridePath() {
        String override = System.getProperty(OVERRIDE_PROPERTY, System.getenv(OVERRIDE_ENV));
        if (override == null || override.isBlank()) {
            return null;
        }
        return override.trim();
    }
}
