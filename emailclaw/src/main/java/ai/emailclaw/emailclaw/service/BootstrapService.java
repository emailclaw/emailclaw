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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.WorkspacePaths;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bootstrap service.
 *
 * <p>Responsible for initial startup directory structure, skill pool seeding and Agent workspace template initialization.
 * {@code BootstrapService} is responsible for providing preprocessing and compatibility detection services in the application startup "Bootstrap phase".
 *
 * <p>It is the first component called in {@link ApplicationBootstrap}.
 * In this phase, the system will:
 * <ul>
 *   <li>Check if critical files exist (e.g., `global-config.json`), if corrupted, it will intercept the startup and prompt for recovery.</li>
 *   <li>In the future, environmental dependency checks (like network, directory permissions, etc.) or data migration logic can be added.</li>
 * </ul>
 *
 * <p>This class is deliberately kept lightweight, not depending on high-level services, only depending on the standard library.
 */
public class BootstrapService {
    private static final Logger LOGGER = Logger.getLogger(BootstrapService.class.getName());
    private static final Pattern BUILTIN_VARIANT_PATTERN = Pattern.compile("^(.+)-(en|zh)$");
    private final AppContext repository;
    private final ProjectService projectService;

    public BootstrapService(AppContext repository, ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    public void preInitializeSkillsPool() {
        ensureSkillPoolSeeded();
        ensureSkillPoolHasBundledSkills();
    }

    public void initialize() {
        LOGGER.info("Starting bootstrap initialization process");
        repository.ensureStructure();
        List<AgentInfo> agents = repository.loadAgents();
        repository.loadProviders();
        projectService.list();
        ensureAgentWorkspaces(agents);
        LOGGER.log(
                Level.INFO, "Bootstrap initialization complete, agent count: {0}", agents.size());
    }

    private void ensureSkillPoolSeeded() {
        Path skillsRoot = repository.paths().skillsPoolRoot;
        try (Stream<Path> children =
                Files.exists(skillsRoot) ? Files.list(skillsRoot) : Stream.empty()) {
            if (children.findAny().isPresent()) {
                return;
            }
        } catch (IOException ignored) {
            return;
        }

        Path fallback = Path.of("src/main/resources/ai/emailclaw/emailclaw/bootstrap-skills");
        if (Files.exists(fallback)) {
            copyDirectory(fallback, skillsRoot);
            LOGGER.log(
                    Level.INFO,
                    "Initialized skill pool using local resources directory: {0}",
                    fallback);
            return;
        }

        try {
            copyResourceTree("ai/emailclaw/emailclaw/bootstrap-skills", skillsRoot);
        } catch (IOException ignored) {
            // keep empty if unavailable
        }
    }

    private void ensureAgentWorkspaces(List<AgentInfo> agents) {
        for (AgentInfo agent : agents) {
            Path workspace = repository.workspaceFor(agent.getId());
            try {
                Files.createDirectories(workspace);
                Files.createDirectories(workspace.resolve(WorkspacePaths.MEMORY_DIR));
                Files.createDirectories(workspace.resolve(WorkspacePaths.SKILLS_DIR));
                Files.createDirectories(workspace.resolve(WorkspacePaths.DOWNLOADS_DIR));
            } catch (IOException e) {
                throw new RuntimeException("Failed to init workspace for " + agent.getId(), e);
            }
            writeIfMissing(
                    workspace.resolve(WorkspacePaths.AGENTS_MD),
                    "# AGENTS\n\nDefault behavior and style.\n");
            writeIfMissing(
                    workspace.resolve(WorkspacePaths.SOUL_MD),
                    "# SOUL\n\nCore personality guidance.\n");
            writeIfMissing(
                    workspace.resolve(WorkspacePaths.PROFILE_MD),
                    "# PROFILE\n\nIdentity and user profile.\n");
            writeIfMissing(
                    workspace.resolve(WorkspacePaths.BOOTSTRAP_MD),
                    "# BOOTSTRAP\n\nStartup constraints.\n");
            writeIfMissing(
                    workspace.resolve(WorkspacePaths.HEARTBEAT_MD),
                    "# HEARTBEAT\n\nRecurring check items.\n");
            writeIfMissing(
                    workspace.resolve(WorkspacePaths.MEMORY_MD),
                    "# MEMORY\n\nLong-term memory notes.\n");

            copySampleSkillsToWorkspace(agent.getId());
        }
    }

    private void copySampleSkillsToWorkspace(String agentId) {
        Path source = repository.paths().skillsPoolRoot;
        Path target = repository.skillsForWorkspace(agentId);
        try {
            Files.createDirectories(target);
            // Emailclaw built-in skills are stored as "skill-en/skill-zh"; Emailclaw workspaces use
            // canonical naming,
            // so during initial setup only one language variant per skill is chosen, displaying 18
            // built-in skills.
            Map<String, Path> selectedSkills = selectCanonicalBuiltinSkills(source);
            if (selectedSkills.isEmpty()) {
                return;
            }
            selectedSkills.forEach(
                    (name, skillDir) -> copyDirectoryIfMissing(skillDir, target.resolve(name)));
        } catch (IOException ignored) {
        }
    }

    private void ensureSkillPoolHasBundledSkills() {
        Path bundled = Path.of("src/main/resources/ai/emailclaw/emailclaw/bootstrap-skills");
        if (!Files.exists(bundled)) {
            return;
        }
        try {
            Files.createDirectories(repository.paths().skillsPoolRoot);
            Map<String, Path> selectedSkills = selectCanonicalBuiltinSkills(bundled);
            selectedSkills.forEach(
                    (name, skillDir) ->
                            copyDirectoryIfMissing(
                                    skillDir, repository.paths().skillsPoolRoot.resolve(name)));
        } catch (IOException ignored) {
        }
    }

    private Map<String, Path> selectCanonicalBuiltinSkills(Path source) throws IOException {
        Map<String, Path> selected = new LinkedHashMap<>();
        String preferredLanguage = Locale.getDefault().getLanguage().equals("zh") ? "zh" : "en";
        try (Stream<Path> stream = Files.list(source)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve("SKILL.md")))
                    .sorted()
                    .forEach(path -> selectBuiltinVariant(selected, path, preferredLanguage));
        }
        return selected;
    }

    private void selectBuiltinVariant(
            Map<String, Path> selected, Path skillDir, String preferredLanguage) {
        String dirName = skillDir.getFileName().toString();
        Matcher matcher = BUILTIN_VARIANT_PATTERN.matcher(dirName);
        String canonicalName = matcher.matches() ? matcher.group(1) : dirName;
        Path current = selected.get(canonicalName);
        if (current == null
                || dirName.endsWith("-" + preferredLanguage)
                || !current.getFileName().toString().endsWith("-en")) {
            selected.put(canonicalName, skillDir);
        }
    }

    private void writeIfMissing(Path path, String content) {
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + path, e);
        }
    }

    private void copyDirectory(Path source, Path target) {
        try {
            Files.walk(source)
                    .forEach(
                            path -> {
                                Path rel = source.relativize(path);
                                Path dst = target.resolve(rel.toString());

                                try {
                                    if (Files.isDirectory(path)) {
                                        Files.createDirectories(dst);
                                    } else {
                                        Files.createDirectories(dst.getParent());
                                        Files.copy(path, dst, StandardCopyOption.REPLACE_EXISTING);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (IOException e) {
            throw new RuntimeException("Copy failed: " + source + " -> " + target, e);
        }
    }

    private void copyDirectoryIfMissing(Path source, Path target) {
        if (Files.exists(target)) {
            return;
        }
        copyDirectory(source, target);
    }

    /**
     * Copy the entire tree from the classpath resource path to the target directory.
     *
     * <p>Supports two run modes:
     * <ul>
     *   <li><b>file protocol</b> (Development phase, classpath is the expanded directory): calls {@link #copyDirectory} directly</li>
     *   <li><b>jar protocol</b> (After packaging, resources inside the JAR): iterates through JAR entries, matches by prefix and writes one by one</li>
     * </ul>
     *
     * @param resourceBase root path of the resource under classpath, e.g., {@code "ai/emailclaw/emailclaw/bootstrap-skills"}
     * @param target       target directory in the file system
     * @throws IOException occurs during copying
     */
    private void copyResourceTree(String resourceBase, Path target) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        var resources = loader.getResources(resourceBase);
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            found = true;
            switch (url.getProtocol()) {
                case "file":
                    copyResourceFromDirectory(url, target);
                    break;
                case "jar":
                    copyResourceFromJar(url, resourceBase, target);
                    break;
                default:
                    LOGGER.log(
                            Level.WARNING,
                            "Unsupported classpath protocol: {0}",
                            url.getProtocol());
            }
        }
        if (!found) {
            LOGGER.log(Level.WARNING, "Resource not found in classpath: {0}", resourceBase);
        } else {
            LOGGER.log(
                    Level.INFO, "Successfully initialized skill pool from classpath: {0}", target);
        }
    }

    /**
     * Copies a directory tree from a file-protocol URL.
     *
     * @param url    file-protocol URL pointing to the resource directory in the classpath
     * @param target target directory
     * @throws IOException occurs during copying
     */
    private void copyResourceFromDirectory(URL url, Path target) throws IOException {
        try {
            Path source = Path.of(url.toURI());
            LOGGER.log(
                    Level.FINE,
                    "Copying resources from directory: {0} -> {1}",
                    new Object[] {source, target});
            copyDirectory(source, target);
        } catch (URISyntaxException e) {
            throw new IOException("Failed to convert URL to Path", e);
        }
    }

    /**
     * Copies a directory and all its contents from a jar file to the target path.
     *
     * <p>JAR entries are matched with the prefix {@code resourceBase/}, and only matched entries are extracted to the target directory,
     * retaining the full relative path structure.
     *
     * @param url          jar protocol URL
     * @param resourceBase classpath resource root path
     * @param target       target directory
     * @throws IOException I/O error occurred during copying
     */
    private void copyResourceFromJar(URL url, String resourceBase, Path target) throws IOException {
        String prefix = resourceBase.endsWith("/") ? resourceBase : resourceBase + "/";
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        try (JarFile jarFile = conn.getJarFile()) {
            LOGGER.log(
                    Level.FINE,
                    "Copying resource from JAR: {0} -> {1}",
                    new Object[] {jarFile.getName(), target});
            var entries = jarFile.entries();
            int fileCount = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || name.equals(prefix)) {
                    continue;
                }
                String relativePath = name.substring(prefix.length());
                Path dest = target.resolve(relativePath);
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    fileCount++;
                }
            }
            LOGGER.log(Level.FINE, "JAR resource copying complete, total {0} files", fileCount);
        }
    }
}
