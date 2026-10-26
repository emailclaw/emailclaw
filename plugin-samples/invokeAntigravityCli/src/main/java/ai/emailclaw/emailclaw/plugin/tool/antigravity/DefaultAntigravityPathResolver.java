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
package ai.emailclaw.emailclaw.plugin.tool.antigravity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Industrial-grade default implementation of {@link AntigravityPathResolver}.
 *
 * <p>Resolves the Google Antigravity CLI binary by:
 * <ol>
 *   <li>Expanding tilde ({@code ~}) in user-configured paths and validating executable status.</li>
 *   <li>Checking if the command exists directly on the system {@code PATH}.</li>
 *   <li>Probing standard installation candidates such as {@code $HOME/.local/bin/agy},
 *       {@code $AGY_HOME/bin/agy}, {@code $ANTIGRAVITY_HOME/bin/agy},
 *       {@code /usr/local/bin/agy}, and {@code /usr/bin/agy}.</li>
 * </ol>
 */
public class DefaultAntigravityPathResolver implements AntigravityPathResolver {

    private static final Logger LOGGER =
            Logger.getLogger(DefaultAntigravityPathResolver.class.getName());

    public static final String DEFAULT_COMMAND_NAME = "agy";

    public DefaultAntigravityPathResolver() {}

    @Override
    public String resolveCliPath(String configuredPath) {
        LOGGER.log(Level.FINE, "Resolving Antigravity CLI path with input: {0}", configuredPath);

        if (configuredPath != null && !configuredPath.isBlank()) {
            String trimmed = configuredPath.trim();
            String expanded = expandTilde(trimmed);

            if (containsPathSeparator(expanded)) {
                Path directPath = Paths.get(expanded);
                if (Files.isRegularFile(directPath) && Files.isExecutable(directPath)) {
                    String resolved = directPath.toAbsolutePath().normalize().toString();
                    LOGGER.log(
                            Level.INFO,
                            "Using explicitly configured and verified Antigravity CLI executable:"
                                    + " {0}",
                            resolved);
                    return resolved;
                }
                LOGGER.log(
                        Level.WARNING,
                        "Configured Antigravity CLI path ''{0}'' does not point to an accessible"
                                + " executable file",
                        expanded);
                return expanded;
            }

            if (!DEFAULT_COMMAND_NAME.equalsIgnoreCase(trimmed)) {
                String foundOnPath = findExecutableOnSystemPath(trimmed);
                if (foundOnPath != null) {
                    LOGGER.log(
                            Level.INFO,
                            "Found custom Antigravity CLI command ''{0}'' on system PATH: {1}",
                            new Object[] {trimmed, foundOnPath});
                    return foundOnPath;
                }
                return trimmed;
            }
        }

        String onPath = findExecutableOnSystemPath(DEFAULT_COMMAND_NAME);
        if (onPath != null) {
            LOGGER.log(Level.FINE, "Found standard Antigravity CLI on system PATH: {0}", onPath);
            return onPath;
        }

        List<Path> candidates = buildCandidatePaths();
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                String discoveredPath = candidate.toAbsolutePath().normalize().toString();
                LOGGER.log(
                        Level.INFO,
                        "Auto-discovered Antigravity CLI executable in standard candidate location:"
                                + " {0}",
                        discoveredPath);
                return discoveredPath;
            }
        }

        LOGGER.log(
                Level.WARNING,
                "Antigravity CLI executable ''{0}'' was not found on system PATH or standard"
                        + " candidate locations (e.g. $HOME/.local/bin/agy). Falling back to bare"
                        + " command name.",
                DEFAULT_COMMAND_NAME);
        return DEFAULT_COMMAND_NAME;
    }

    /**
     * Expands a leading tilde ({@code ~}) to the current user's home directory.
     *
     * @param path The path string that may start with {@code ~}
     * @return Path string with {@code ~} replaced by user home directory
     */
    String expandTilde(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("~" + File.separator) || path.equals("~") || path.startsWith("~/")) {
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                if (path.equals("~")) {
                    return userHome;
                }
                return userHome + path.substring(1);
            }
        }
        return path;
    }

    /**
     * Checks whether the path string contains path separator characters.
     *
     * @param path Input path string
     * @return true if the path contains forward or backward slashes
     */
    boolean containsPathSeparator(String path) {
        return path != null && (path.contains("/") || path.contains("\\"));
    }

    /**
     * Scans the directories listed in system {@code PATH} for the specified executable.
     *
     * @param commandName Base executable command name (e.g. 'agy')
     * @return Absolute path string if located and executable, or null if not found
     */
    String findExecutableOnSystemPath(String commandName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }

        boolean isWindows = isWindowsSystem();
        List<String> extensions = isWindows ? List.of(".exe", ".cmd", ".bat", "") : List.of("");

        for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            Path dirPath = Paths.get(dir.trim());
            for (String ext : extensions) {
                Path candidate = dirPath.resolve(commandName + ext);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    try {
                        return candidate.toRealPath().toString();
                    } catch (IOException e) {
                        return candidate.toAbsolutePath().normalize().toString();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Builds an ordered list of standard candidate locations where Antigravity CLI is commonly installed.
     *
     * @return List of candidate executable file paths to probe
     */
    private List<Path> buildCandidatePaths() {
        List<Path> candidates = new ArrayList<>();
        boolean isWindows = isWindowsSystem();
        List<String> extensions = isWindows ? List.of(".exe", ".cmd", ".bat", "") : List.of("");
        String userHome = System.getProperty("user.home");

        // 1. $HOME/.local/bin/agy (Standard user script target)
        if (userHome != null && !userHome.isBlank()) {
            addCandidates(
                    candidates,
                    Paths.get(userHome, ".local", "bin"),
                    DEFAULT_COMMAND_NAME,
                    extensions);
        }

        // 2. ANTIGRAVITY_BIN or AGY_BIN environment variable
        String agyBin = System.getenv("AGY_BIN");
        if (agyBin != null && !agyBin.isBlank()) {
            candidates.add(Paths.get(agyBin.trim()));
        }
        String antigravityBin = System.getenv("ANTIGRAVITY_BIN");
        if (antigravityBin != null && !antigravityBin.isBlank()) {
            candidates.add(Paths.get(antigravityBin.trim()));
        }

        // 3. ANTIGRAVITY_HOME or AGY_HOME environment variable
        String agyHome = System.getenv("AGY_HOME");
        if (agyHome != null && !agyHome.isBlank()) {
            addCandidates(
                    candidates, Paths.get(agyHome.trim(), "bin"), DEFAULT_COMMAND_NAME, extensions);
            addCandidates(candidates, Paths.get(agyHome.trim()), DEFAULT_COMMAND_NAME, extensions);
        }
        String antigravityHome = System.getenv("ANTIGRAVITY_HOME");
        if (antigravityHome != null && !antigravityHome.isBlank()) {
            addCandidates(
                    candidates,
                    Paths.get(antigravityHome.trim(), "bin"),
                    DEFAULT_COMMAND_NAME,
                    extensions);
            addCandidates(
                    candidates,
                    Paths.get(antigravityHome.trim()),
                    DEFAULT_COMMAND_NAME,
                    extensions);
        }

        // 4. Standard user home locations
        if (userHome != null && !userHome.isBlank()) {
            addCandidates(candidates, Paths.get(userHome, "bin"), DEFAULT_COMMAND_NAME, extensions);
            addCandidates(
                    candidates,
                    Paths.get(userHome, ".antigravity", "bin"),
                    DEFAULT_COMMAND_NAME,
                    extensions);
            addCandidates(
                    candidates,
                    Paths.get(userHome, ".gemini", "antigravity", "bin"),
                    DEFAULT_COMMAND_NAME,
                    extensions);
            addCandidates(
                    candidates,
                    Paths.get(userHome, ".cargo", "bin"),
                    DEFAULT_COMMAND_NAME,
                    extensions);
            addCandidates(
                    candidates,
                    Paths.get(userHome, ".npm-global", "bin"),
                    DEFAULT_COMMAND_NAME,
                    extensions);
        }

        // 5. Standard system binary directories
        addCandidates(candidates, Paths.get("/usr/local/bin"), DEFAULT_COMMAND_NAME, extensions);
        addCandidates(candidates, Paths.get("/usr/bin"), DEFAULT_COMMAND_NAME, extensions);
        addCandidates(candidates, Paths.get("/opt/homebrew/bin"), DEFAULT_COMMAND_NAME, extensions);
        addCandidates(
                candidates,
                Paths.get("/usr/local/opt/antigravity/bin"),
                DEFAULT_COMMAND_NAME,
                extensions);

        return candidates;
    }

    private void addCandidates(
            List<Path> target, Path parentDir, String baseName, List<String> extensions) {
        if (parentDir == null) {
            return;
        }
        for (String ext : extensions) {
            target.add(parentDir.resolve(baseName + ext));
        }
    }

    private boolean isWindowsSystem() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
