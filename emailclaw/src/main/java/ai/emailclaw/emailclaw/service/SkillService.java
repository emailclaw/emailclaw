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

import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.model.SkillInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import tools.jackson.databind.ObjectMapper;

/**
 * Skill management service.
 *
 * <p>Responsible for workspace/skill pool scanning, creation, and directory import copying.
 */
public class SkillService {
    private static final Logger LOGGER = Logger.getLogger(SkillService.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String META_FILE = ".emailclaw-skill.json";
    private static final Pattern BUILTIN_VARIANT_PATTERN = Pattern.compile("^(.+)-(en|zh)$");
    private final AppContext repository;

    public SkillService(AppContext repository) {
        this.repository = repository;
        LOGGER.info(
                "SkillService initialized, skill pool root directory: "
                        + repository.paths().skillsPoolRoot);
    }

    public List<SkillInfo> listWorkspaceSkills(String agentId) {
        LOGGER.log(Level.INFO, "Skill call start: scan workspace skills, agent={0}", agentId);
        Path skillsRoot = repository.skillsForWorkspace(agentId);
        return listSkillsFromDir(skillsRoot, false);
    }

    public List<SkillInfo> listSkillPool() {
        List<Path> roots = collectSkillPoolRoots();
        Map<String, SkillInfo> grouped = new LinkedHashMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(Files::isDirectory)
                        .forEach(
                                dir -> {
                                    SkillInfo info = readSkillInfo(dir, true);
                                    if (info == null) {
                                        return;
                                    }
                                    SkillInfo existing = grouped.get(info.name());
                                    if (existing == null
                                            || shouldReplaceGroupedSkill(existing, info)) {
                                        grouped.put(info.name(), info);
                                    }
                                });
            } catch (IOException ignored) {
            }
        }
        List<SkillInfo> result = new ArrayList<>(grouped.values());
        result.sort(Comparator.comparing(s -> s.name()));
        return result;
    }

    private List<Path> collectSkillPoolRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(repository.paths().skillsPoolRoot);
        try {
            GlobalConfig config = repository.loadGlobalConfig();
            if (config != null && config.getSkillPoolPaths() != null) {
                for (String path : config.getSkillPoolPaths()) {
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    Path extra = Paths.get(path.trim());
                    if (!extra.isAbsolute()) {
                        extra = repository.paths().root.resolve(extra).normalize();
                    }
                    if (!roots.contains(extra)) {
                        roots.add(extra);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return roots;
    }

    public SkillInfo createSkill(String agentId, String skillName, String content) {
        Path dir = repository.skillsForWorkspace(agentId).resolve(skillName);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("SKILL.md"), content);
            LOGGER.log(
                    Level.INFO,
                    "Created Skill successfully: agent={0}, skill={1}",
                    new Object[] {agentId, skillName});
        } catch (IOException e) {
            throw new RuntimeException("Failed to create skill", e);
        }
        SkillInfo info =
                new SkillInfo(
                        skillName,
                        skillName,
                        "",
                        "",
                        "",
                        "",
                        skillName,
                        false,
                        true,
                        LocalDateTime.now().toString(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new LinkedHashMap<>());
        return info;
    }

    public void saveSkill(String agentId, String oldName, SkillInfo updated) {
        // The workspace may still retain historical "xxx-en/xxx-zh" directories; parse the real
        // directory first when saving,
        // then uniformly write back the normalized directory name, avoiding the UI seeing multiple
        // language variants of the same built-in Skill.
        Path oldDir = resolveSkillDir(repository.skillsForWorkspace(agentId), oldName);
        if (!Files.isDirectory(oldDir)) {
            throw new IllegalArgumentException("Skill not found: " + oldName);
        }
        String newName = normalizeSkillName(updated.name());
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Skill name is required");
        }
        Path skillsRoot = repository.skillsForWorkspace(agentId);
        Path newDir = skillsRoot.resolve(newName).normalize();
        if (!newDir.startsWith(skillsRoot)) {
            throw new IllegalArgumentException("Invalid skill name: " + newName);
        }
        try {
            if (!oldDir.equals(newDir)) {
                if (Files.exists(newDir)) {
                    throw new IllegalArgumentException("Skill already exists: " + newName);
                }
                Files.createDirectories(newDir.getParent());
                Files.move(oldDir, newDir);
            }
            Files.writeString(
                    newDir.resolve("SKILL.md"), updated.content() == null ? "" : updated.content());
            writeMeta(
                    newDir,
                    updated.enabled(),
                    updated.channels(),
                    updated.tags(),
                    updated.config(),
                    updated.source(),
                    updated.installedFrom());
            LOGGER.log(
                    Level.INFO,
                    "Saved Skill successfully: agent={0}, old={1}, new={2}",
                    new Object[] {agentId, oldName, newName});
        } catch (IOException e) {
            throw new RuntimeException("Failed to save skill", e);
        }
    }

    public void deleteSkill(String agentId, String skillName) {
        // When deleting, clean up language variants of the same name at the same time to prevent
        // old "-en/-zh" directories from showing up again after deleting the normalized directory.
        List<Path> skillDirs = resolveSkillDirs(repository.skillsForWorkspace(agentId), skillName);
        if (skillDirs.isEmpty()) {
            throw new IllegalArgumentException("Skill not found: " + skillName);
        }
        skillDirs.forEach(this::deleteDir);
        LOGGER.log(
                Level.INFO,
                "Deleted Skill successfully: agent={0}, skill={1}",
                new Object[] {agentId, skillName});
    }

    public void importSkillDirToWorkspace(String agentId, Path fromDir) {
        Path target = repository.skillsForWorkspace(agentId).resolve(fromDir.getFileName());
        copyDir(fromDir, target);
        LOGGER.log(
                Level.INFO,
                "Imported Skill to workspace: agent={0}, source={1}",
                new Object[] {agentId, fromDir});
    }

    public void importSkillToPool(Path fromDir) {
        Path target = repository.paths().skillsPoolRoot.resolve(fromDir.getFileName());
        copyDir(fromDir, target);
    }

    public void importSkillZip(String agentId, Path zipFile) {
        Path target = repository.skillsForWorkspace(agentId);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to import skill ZIP", e);
        }
        LOGGER.log(Level.INFO, "Imported skills from zip: {0}", zipFile);
    }

    public void setSkillEnabled(String agentId, String skillName, boolean enabled) {
        Path skillDir = resolveSkillDir(repository.skillsForWorkspace(agentId), skillName);
        if (!Files.isDirectory(skillDir)) {
            throw new IllegalArgumentException("Skill not found: " + skillName);
        }
        SkillInfo current = readSkillInfo(skillDir, false);
        writeMeta(
                skillDir,
                enabled,
                current.channels(),
                current.tags(),
                current.config(),
                current.source(),
                current.installedFrom());
    }

    public Map<String, String> setSkillsEnabled(
            String agentId, List<String> skillNames, boolean enabled) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (String skillName : skillNames) {
            try {
                setSkillEnabled(agentId, skillName, enabled);
            } catch (Exception e) {
                errors.put(skillName, e.getMessage());
            }
        }
        return errors;
    }

    public void applySkillSelections(String agentId, List<String> desiredSkills) {
        // 1. Get pool skills so we can find paths to import
        java.util.Map<String, Path> poolPaths = new java.util.HashMap<>();
        for (Path root : collectSkillPoolRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(Files::isDirectory)
                        .forEach(
                                dir -> {
                                    String name = canonicalSkillName(dir.getFileName().toString());
                                    poolPaths.putIfAbsent(name, dir);
                                });
            } catch (IOException ignored) {
            }
        }

        // 2. Ensure ALL pool skills are imported
        for (java.util.Map.Entry<String, Path> entry : poolPaths.entrySet()) {
            String skillName = entry.getKey();
            Path poolDir = entry.getValue();
            Path workspaceSkillDir =
                    resolveSkillDir(repository.skillsForWorkspace(agentId), skillName);
            if (!Files.isDirectory(workspaceSkillDir)) {
                importSkillDirToWorkspace(agentId, poolDir);
            }
        }

        // 3. Set enabled state for all workspace skills
        List<SkillInfo> workspaceSkills = listWorkspaceSkills(agentId);
        for (SkillInfo wsSkill : workspaceSkills) {
            boolean shouldBeEnabled = desiredSkills.contains(wsSkill.name());
            if (wsSkill.enabled() != shouldBeEnabled) {
                setSkillEnabled(agentId, wsSkill.name(), shouldBeEnabled);
            }
        }
    }

    private List<SkillInfo> listSkillsFromDir(Path root, boolean builtIn) {
        List<SkillInfo> result = new ArrayList<>();
        if (!Files.exists(root)) {
            return result;
        }
        Map<String, SkillInfo> grouped = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .forEach(
                            dir -> {
                                SkillInfo info = readSkillInfo(dir, builtIn);
                                if (info == null) {
                                    return;
                                }
                                SkillInfo old = grouped.get(info.name());
                                if (old == null || shouldReplaceGroupedSkill(old, info)) {
                                    grouped.put(info.name(), info);
                                }
                            });
        } catch (IOException ignored) {
        }
        result.addAll(grouped.values());
        result.sort(Comparator.comparing(s -> s.name()));
        return result;
    }

    private SkillInfo readSkillInfo(Path dir, boolean builtInRoot) {
        // Emailclaw uses SKILL.md as the content source, additional enabled state, tags, and
        // configuration are saved in a lightweight metadata file.
        Path skillMd = dir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) {
            return null;
        }
        String storageName = dir.getFileName().toString();
        String name = canonicalSkillName(storageName);
        boolean builtIn = builtInRoot || !storageName.equals(name);
        String source = builtIn ? "builtin" : "customized";
        String content = "";
        String description = "";
        String updatedAt = "";
        try {
            content = Files.readString(skillMd);
            Map<String, String> frontmatter = parseFrontmatter(content);
            description = frontmatter.getOrDefault("description", firstMeaningfulLine(content));
            updatedAt = Files.getLastModifiedTime(skillMd).toString();
        } catch (IOException ignored) {
        }
        SkillInfo info =
                new SkillInfo(
                        name,
                        name,
                        description,
                        content,
                        source,
                        "",
                        storageName,
                        builtIn,
                        true,
                        updatedAt,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new LinkedHashMap<>());
        Path meta = dir.resolve(META_FILE);
        if (Files.exists(meta)) {
            try {
                info = applyMeta(info, JSON.readValue(meta.toFile(), Map.class));
            } catch (Exception ignored) {
            }
        }
        return info;
    }

    private SkillInfo applyMeta(SkillInfo info, Map<?, ?> payload) {
        // Jackson deserializes to primitive Map/List; here we centrally do type narrowing to
        // prevent the UI layer from processing non-string keys.
        String name = info.name();
        String title = info.title();
        String description = info.description();
        String content = info.content();
        String source = info.source();
        String installedFrom = info.installedFrom();
        String storageName = info.storageName();
        boolean builtIn = info.builtIn();
        boolean enabled = info.enabled();
        String updatedAt = info.updatedAt();
        List<String> channels = info.channels();
        List<String> tags = info.tags();
        Map<String, Object> config = info.config();

        Object enabledObj = payload.get("enabled");
        if (enabledObj instanceof Boolean b) {
            enabled = b;
        }
        Object tagsObj = payload.get("tags");
        if (tagsObj instanceof List<?> t) {
            tags = t.stream().map(String::valueOf).toList();
        }
        Object channelsObj = payload.get("channels");
        if (channelsObj instanceof List<?> c) {
            channels = c.stream().map(String::valueOf).toList();
        }
        Object configObj = payload.get("config");
        if (configObj instanceof Map<?, ?> cfg) {
            Map<String, Object> typedConfig = new LinkedHashMap<>();
            cfg.forEach((key, value) -> typedConfig.put(String.valueOf(key), value));
            config = typedConfig;
        }
        Object sourceObj = payload.get("source");
        if (sourceObj instanceof String s && !s.isBlank()) {
            source = s;
            builtIn = "builtin".equals(s) || s.startsWith("builtin:");
        }
        Object installedFromObj = payload.get("installedFrom");
        if (installedFromObj instanceof String ifr) {
            installedFrom = ifr;
        }
        return new SkillInfo(
                name,
                title,
                description,
                content,
                source,
                installedFrom,
                storageName,
                builtIn,
                enabled,
                updatedAt,
                channels,
                tags,
                config);
    }

    private void writeMeta(
            Path skillDir,
            boolean enabled,
            List<String> channels,
            List<String> tags,
            Map<String, Object> config,
            String source,
            String installedFrom) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("enabled", enabled);
        meta.put("channels", channels == null || channels.isEmpty() ? List.of("all") : channels);
        meta.put("tags", tags == null ? List.of() : tags);
        meta.put("config", config == null ? Map.of() : config);
        if (source != null && !source.isBlank()) {
            meta.put("source", source);
        }
        if (installedFrom != null && !installedFrom.isBlank()) {
            meta.put("installedFrom", installedFrom);
        }
        meta.put("updatedAt", LocalDateTime.now().toString());
        JSON.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir.resolve(META_FILE).toFile(), meta);
        LOGGER.info("META_FILE write successfully.");
    }

    private Path resolveSkillDir(Path root, String skillName) {
        Path exact = root.resolve(skillName).normalize();
        if (exact.startsWith(root) && Files.isDirectory(exact)) {
            return exact;
        }
        for (String suffix : List.of("-en", "-zh")) {
            Path candidate = root.resolve(skillName + suffix).normalize();
            if (candidate.startsWith(root) && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return exact;
    }

    private List<Path> resolveSkillDirs(Path root, String skillName) {
        List<Path> result = new ArrayList<>();
        for (String candidateName : List.of(skillName, skillName + "-en", skillName + "-zh")) {
            Path candidate = root.resolve(candidateName).normalize();
            if (candidate.startsWith(root) && Files.isDirectory(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private String canonicalSkillName(String name) {
        Matcher matcher = BUILTIN_VARIANT_PATTERN.matcher(name);
        return matcher.matches() ? matcher.group(1) : name;
    }

    private boolean shouldReplaceGroupedSkill(SkillInfo old, SkillInfo candidate) {
        // The same built-in Skill may exist in both en/zh resource directories simultaneously; the
        // list only shows one canonical entry.
        // If there is already a canonical directory, prioritize the canonical directory, otherwise
        // prioritize the Chinese variant, consistent with Emailclaw's language variant model.
        if (candidate.storageName().equals(candidate.name())) {
            return true;
        }
        if (old.storageName().equals(old.name())) {
            return false;
        }
        return candidate.storageName().endsWith("-zh") && !old.storageName().endsWith("-zh");
    }

    private Map<String, String> parseFrontmatter(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content == null || !content.startsWith("---")) {
            return result;
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return result;
        }
        String block = content.substring(3, end);
        for (String line : block.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                result.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        return result;
    }

    private String firstMeaningfulLine(String content) {
        if (content == null) {
            return "";
        }
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !line.equals("---") && !line.contains(":"))
                .findFirst()
                .orElse("");
    }

    private String normalizeSkillName(String name) {
        return FileNameUtils.sanitizeEnglishPathName(name);
    }

    private void copyDir(Path source, Path target) {
        try {
            Files.walk(source)
                    .forEach(
                            path -> {
                                Path rel = source.relativize(path);
                                Path out = target.resolve(rel.toString());
                                try {
                                    if (Files.isDirectory(path)) {
                                        Files.createDirectories(out);
                                    } else {
                                        Files.createDirectories(out.getParent());
                                        Files.copy(
                                                path,
                                                out,
                                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy skill", e);
        }
    }

    private void deleteDir(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete skill", e);
        }
    }
}
