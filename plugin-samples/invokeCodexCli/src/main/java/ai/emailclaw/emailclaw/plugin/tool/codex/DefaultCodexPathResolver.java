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
package ai.emailclaw.emailclaw.plugin.tool.codex;

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
import java.util.stream.Stream;

/**
 * Industrial-grade default implementation of {@link CodexPathResolver}.
 *
 * <p>Resolves the Codex CLI binary by:
 * <ol>
 *   <li>Expanding tilde ({@code ~}) in user-configured paths and validating executable status.</li>
 *   <li>Checking if the command exists directly on the system {@code PATH}.</li>
 *   <li>Probing standard installation candidates such as {@code $HOME/.local/bin/codex}
 *       (the default destination of {@code curl -fsSL https://chatgpt.com/codex/install.sh | sh}),
 *       {@code $CODEX_INSTALL_DIR/codex}, {@code $CODEX_HOME/packages/standalone/current/codex},
 *       {@code /usr/local/bin/codex}, {@code /usr/bin/codex}, and Node/NVM toolchains.</li>
 * </ol>
 */
public class DefaultCodexPathResolver implements CodexPathResolver {

    private static final Logger LOGGER = Logger.getLogger(DefaultCodexPathResolver.class.getName());

    public static final String DEFAULT_COMMAND_NAME = "codex";

    public DefaultCodexPathResolver() {}

    @Override
    public String resolveCliPath(String configuredPath) {
        LOGGER.log(Level.FINE, "Resolving Codex CLI path with input: {0}", configuredPath);

        if (configuredPath != null && !configuredPath.isBlank()) {
            String trimmed = configuredPath.trim();
            String expanded = expandTilde(trimmed);

            if (containsPathSeparator(expanded)) {
                Path directPath = Paths.get(expanded);
                if (Files.isRegularFile(directPath) && Files.isExecutable(directPath)) {
                    String resolved = directPath.toAbsolutePath().normalize().toString();
                    LOGGER.log(
                            Level.INFO,
                            "Using explicitly configured and verified Codex CLI executable: {0}",
                            resolved);
                    return resolved;
                }
                LOGGER.log(
                        Level.WARNING,
                        "Configured Codex CLI path ''{0}'' does not point to an accessible"
                                + " executable file",
                        expanded);
                return expanded;
            }

            if (!DEFAULT_COMMAND_NAME.equalsIgnoreCase(trimmed)) {
                String foundOnPath = findExecutableOnSystemPath(trimmed);
                if (foundOnPath != null) {
                    LOGGER.log(
                            Level.INFO,
                            "Found custom Codex CLI command ''{0}'' on system PATH: {1}",
                            new Object[] {trimmed, foundOnPath});
                    return foundOnPath;
                }
                return trimmed;
            }
        }

        String onPath = findExecutableOnSystemPath(DEFAULT_COMMAND_NAME);
        if (onPath != null) {
            LOGGER.log(Level.FINE, "Found standard Codex CLI on system PATH: {0}", onPath);
            return onPath;
        }

        List<Path> candidates = buildCandidatePaths();
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                String discoveredPath = candidate.toAbsolutePath().normalize().toString();
                LOGGER.log(
                        Level.INFO,
                        "Auto-discovered Codex CLI executable in standard candidate location: {0}",
                        discoveredPath);
                return discoveredPath;
            }
        }

        LOGGER.log(
                Level.WARNING,
                "Codex CLI executable ''{0}'' was not found on system PATH or standard candidate"
                        + " locations (e.g. $HOME/.local/bin/codex). Falling back to bare command"
                        + " name.",
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
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return path;
        }
        if ("~".equals(path)) {
            return userHome;
        }
        if (path.startsWith("~" + File.separator)
                || path.startsWith("~/")
                || path.startsWith("~\\")) {
            return userHome + path.substring(1);
        }
        return path;
    }

    private boolean containsPathSeparator(String path) {
        return path.contains("/") || path.contains("\\") || path.contains(File.separator);
    }

    /**
     * Searches each directory on the system {@code PATH} for the specified command.
     *
     * @param commandName Name of the binary to search for
     * @return Absolute normalized path if found and executable, or {@code null}
     */
    private String findExecutableOnSystemPath(String commandName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }

        boolean isWindows = isWindowsSystem();
        List<String> extensions = isWindows ? List.of("", ".exe", ".cmd", ".bat") : List.of("");

        for (String entry : pathEnv.split(Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) {
                continue;
            }
            Path dir = Paths.get(entry.trim());
            for (String ext : extensions) {
                Path candidate = dir.resolve(commandName + ext);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath().normalize().toString();
                }
            }
        }
        return null;
    }

    /**
     * Builds an ordered list of standard candidate locations where Codex CLI is commonly installed.
     *
     * @return List of candidate executable file paths to probe
     */
    private List<Path> buildCandidatePaths() {
        List<Path> candidates = new ArrayList<>();
        boolean isWindows = isWindowsSystem();
        List<String> extensions = isWindows ? List.of(".exe", ".cmd", ".bat", "") : List.of("");
        String userHome = System.getProperty("user.home");

        // 1. $HOME/.local/bin/codex (Official curl install.sh default target)
        if (userHome != null && !userHome.isBlank()) {
            addCandidates(candidates, Paths.get(userHome, ".local", "bin"), "codex", extensions);
        }

        // 2. CODEX_INSTALL_DIR environment variable
        String codexInstallDir = System.getenv("CODEX_INSTALL_DIR");
        if (codexInstallDir != null && !codexInstallDir.isBlank()) {
            addCandidates(candidates, Paths.get(codexInstallDir.trim()), "codex", extensions);
        }

        // 3. CODEX_HOME environment variable
        String codexHome = System.getenv("CODEX_HOME");
        if (codexHome != null && !codexHome.isBlank()) {
            addCandidates(
                    candidates,
                    Paths.get(codexHome.trim(), "packages", "standalone", "current"),
                    "codex",
                    extensions);
            addCandidates(candidates, Paths.get(codexHome.trim(), "bin"), "codex", extensions);
        }

        // 4. $HOME/.codex/packages/standalone/current/codex (Standalone direct binary target)
        if (userHome != null && !userHome.isBlank()) {
            addCandidates(
                    candidates,
                    Paths.get(userHome, ".codex", "packages", "standalone", "current"),
                    "codex",
                    extensions);
        }

        // 5. Standard user bin locations
        if (userHome != null && !userHome.isBlank()) {
            addCandidates(candidates, Paths.get(userHome, ".cargo", "bin"), "codex", extensions);
            addCandidates(candidates, Paths.get(userHome, "bin"), "codex", extensions);
            addCandidates(
                    candidates, Paths.get(userHome, ".npm-global", "bin"), "codex", extensions);
            addCandidates(
                    candidates,
                    Paths.get(userHome, ".local", "share", "pnpm"),
                    "codex",
                    extensions);
            addCandidates(candidates, Paths.get(userHome, ".yarn", "bin"), "codex", extensions);
            addCandidates(candidates, Paths.get(userHome, ".asdf", "shims"), "codex", extensions);

            // NVM candidates
            String nvmBin = System.getenv("NVM_BIN");
            if (nvmBin != null && !nvmBin.isBlank()) {
                addCandidates(candidates, Paths.get(nvmBin.trim()), "codex", extensions);
            }
            Path nvmVersionsDir = Paths.get(userHome, ".nvm", "versions", "node");
            if (Files.isDirectory(nvmVersionsDir)) {
                try (Stream<Path> stream = Files.list(nvmVersionsDir)) {
                    stream.filter(Files::isDirectory)
                            .forEach(
                                    nodeDir ->
                                            addCandidates(
                                                    candidates,
                                                    nodeDir.resolve("bin"),
                                                    "codex",
                                                    extensions));
                } catch (IOException ignored) {
                }
            }
        }

        // 6. System directories
        if (!isWindows) {
            addCandidates(candidates, Paths.get("/usr/local/bin"), "codex", extensions);
            addCandidates(candidates, Paths.get("/usr/bin"), "codex", extensions);
            addCandidates(candidates, Paths.get("/opt/homebrew/bin"), "codex", extensions);
        } else {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                addCandidates(candidates, Paths.get(appData.trim(), "npm"), "codex", extensions);
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                addCandidates(
                        candidates,
                        Paths.get(localAppData.trim(), "Programs", "codex"),
                        "codex",
                        extensions);
            }
        }

        return candidates;
    }

    private void addCandidates(
            List<Path> targetList, Path parentDir, String baseName, List<String> extensions) {
        for (String ext : extensions) {
            targetList.add(parentDir.resolve(baseName + ext));
        }
    }

    private boolean isWindowsSystem() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }
}
