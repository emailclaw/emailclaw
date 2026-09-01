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

import ai.emailclaw.emailclaw.model.PluginInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.AppPaths;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Plugin manager business service.
 *
 * <p>Responsible for scanning folders/JAR files under the local plugin directory, loading the official plugin list from the cloud, downloading and extracting plugins, uninstalling plugins, etc.
 */
public class PluginService {

    private static final Logger LOGGER = Logger.getLogger(PluginService.class.getName());

    private final AppPaths paths;

    public PluginService(AppContext appContext) {
        this.paths = appContext.paths();
    }

    /**
     * Get the list of locally installed plugins.
     */
    public List<PluginInfo> getInstalledPlugins() {
        List<PluginInfo> list = new ArrayList<>();
        Path pluginsDir = paths.pluginsDir;
        if (pluginsDir == null || !Files.exists(pluginsDir) || !Files.isDirectory(pluginsDir)) {
            return list;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    Path manifestPath = path.resolve("plugin.json");
                    if (Files.exists(manifestPath)) {
                        PluginInfo info = parsePluginJson(manifestPath);
                        if (info != null) {
                            list.add(info);
                        }
                    }
                } else if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                    PluginInfo info = parsePluginJar(path);
                    if (info != null) {
                        list.add(info);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to list installed plugins", e);
        }
        return list;
    }

    /**
     * Get the list of plugins from the official download CDN.
     */
    public List<PluginInfo> getOfficialPlugins() {
        List<PluginInfo> list = new ArrayList<>();
        try {
            HttpClient client =
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://platform.emailclaw.email/metadata/plugins/index.json"))
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                JsonNode files = root.get("files");
                // Get installed plugins for comparison
                List<PluginInfo> installed = getInstalledPlugins();
                Map<String, PluginInfo> installedMap = new HashMap<>();
                for (PluginInfo p : installed) {
                    installedMap.put(p.id(), p);
                }
                if (files != null && files.isObject()) {
                    for (Map.Entry<String, JsonNode> field : files.properties()) {
                        JsonNode entry = field.getValue();
                        String id = getJsonString(entry, "id", field.getKey());
                        String pluginId = getJsonString(entry, "plugin_id", id);
                        JsonNode nameNode = entry.get("name");
                        String name = pickLocalized(nameNode, id);
                        Map<String, String> nameI18n = convertToMap(nameNode);
                        JsonNode descNode = entry.get("description");
                        String description = pickLocalized(descNode, "");
                        Map<String, String> descriptionI18n = convertToMap(descNode);
                        String version = getJsonString(entry, "version", "1.0.0");
                        String author = getJsonString(entry, "author", "Unknown");
                        // bundle or tool
                        String type = getJsonString(entry, "platform", "tool");
                        String size = getJsonString(entry, "size", "");
                        String sha256 = getJsonString(entry, "sha256", "");
                        String relUrl = getJsonString(entry, "url", "");
                        String installUrl;
                        if (relUrl.startsWith("/")) {
                            installUrl = "https://platform.emailclaw.email" + relUrl;
                        } else {
                            installUrl = relUrl;
                        }
                        boolean isInstalled;
                        String installedVersion;
                        boolean upgradeAvailable;
                        PluginInfo inst = installedMap.get(pluginId);
                        if (inst != null) {
                            isInstalled = true;
                            installedVersion = inst.version();
                            upgradeAvailable = isUpgradeAvailable(inst.version(), version);
                        } else {
                            isInstalled = false;
                            installedVersion = "";
                            upgradeAvailable = false;
                        }
                        list.add(
                                new PluginInfo(
                                        id,
                                        pluginId,
                                        name,
                                        description,
                                        version,
                                        author,
                                        type,
                                        installUrl,
                                        size,
                                        sha256,
                                        installedVersion,
                                        "",
                                        nameI18n,
                                        descriptionI18n,
                                        isInstalled,
                                        upgradeAvailable,
                                        false,
                                        false));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch official plugin catalog", e);
        }
        list.sort(
                (p1, p2) -> {
                    int c = p1.type().compareTo(p2.type());
                    if (c != 0) return c;
                    return p1.name().compareTo(p2.name());
                });
        return list;
    }

    /**
     * Download and install a ZIP plugin from the given URL.
     */
    public void installPluginFromUrl(String url, boolean force) throws Exception {
        Path tempDir = Files.createTempDirectory("emailclaw-plugin-");
        Path zipFile = tempDir.resolve("plugin.zip");
        try {
            HttpClient client =
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build();
            HttpResponse<Path> response =
                    client.send(request, HttpResponse.BodyHandlers.ofFile(zipFile));
            if (response.statusCode() != 200) {
                throw new IOException(
                        "Failed to download plugin, HTTP status: " + response.statusCode());
            }
            installPluginFromZip(zipFile.toFile(), force);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * Install a plugin from a local ZIP file.
     */
    public void installPluginFromZip(File zipFile, boolean force) throws Exception {
        Path tempDir = Files.createTempDirectory("emailclaw-plugin-extract-");
        try {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                Path resolvedTemp = tempDir.toAbsolutePath().normalize();
                while ((entry = zis.getNextEntry()) != null) {
                    Path newPath = tempDir.resolve(entry.getName()).toAbsolutePath().normalize();
                    if (!newPath.startsWith(resolvedTemp)) {
                        throw new IOException("Zip Slip detected: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(newPath);
                    } else {
                        Files.createDirectories(newPath.getParent());
                        Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            Path pluginSourceDir = findPluginJsonDir(tempDir);
            if (pluginSourceDir == null) {
                throw new IllegalArgumentException("No plugin.json found in ZIP file");
            }
            Path manifestPath = pluginSourceDir.resolve("plugin.json");
            PluginInfo info = parsePluginJson(manifestPath);
            if (info == null || info.id() == null || info.id().isBlank()) {
                throw new IllegalArgumentException("Invalid plugin.json in ZIP file");
            }
            Path destDir = paths.pluginsDir.resolve(info.id());
            if (Files.exists(destDir)) {
                if (!force) {
                    throw new IllegalStateException(
                            "Plugin " + info.id() + " is already installed");
                }
                uninstallPlugin(info.id());
            }
            Files.createDirectories(destDir);
            copyDirectory(pluginSourceDir, destDir);
            LOGGER.info("Successfully installed plugin " + info.id() + " to " + destDir);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * Uninstall the specified plugin.
     */
    public void uninstallPlugin(String id) throws Exception {
        Path pluginDir = paths.pluginsDir.resolve(id);
        if (!Files.exists(pluginDir)) {
            Path jarFile = paths.pluginsDir.resolve(id + ".jar");
            if (Files.exists(jarFile)) {
                Files.delete(jarFile);
                LOGGER.info("Successfully uninstalled JAR plugin: " + id);
                return;
            }
            throw new FileNotFoundException("Plugin directory not found for id: " + id);
        }
        deleteDirectoryRecursively(pluginDir);
        LOGGER.info("Successfully uninstalled plugin: " + id);
    }

    private PluginInfo parsePluginJson(Path manifestPath) {
        try {
            String content = Files.readString(manifestPath);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(content);
            String id =
                    getJsonString(node, "id", manifestPath.getParent().getFileName().toString());
            String pluginId = id;
            JsonNode nameNode = node.get("name");
            String name = pickLocalized(nameNode, id);
            Map<String, String> nameI18n = convertToMap(nameNode);
            JsonNode descNode = node.get("description");
            String description = pickLocalized(descNode, "");
            Map<String, String> descriptionI18n = convertToMap(descNode);
            String version = getJsonString(node, "version", "1.0.0");
            String author = getJsonString(node, "author", "Unknown");
            String type = getJsonString(node, "type", "tool");
            String frontendEntry = "";
            JsonNode entryNode = node.get("entry");
            if (entryNode != null) {
                JsonNode fe = entryNode.get("frontend");
                if (fe != null && !fe.isNull()) {
                    frontendEntry = fe.asString();
                }
            }
            return new PluginInfo(
                    id,
                    pluginId,
                    name,
                    description,
                    version,
                    author,
                    type,
                    "",
                    "",
                    "",
                    "",
                    frontendEntry,
                    nameI18n,
                    descriptionI18n,
                    true,
                    false,
                    true,
                    true);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse plugin.json at " + manifestPath, e);
            return null;
        }
    }

    private PluginInfo parsePluginJar(Path jarPath) {
        String filename = jarPath.getFileName().toString();
        String id = filename.substring(0, filename.length() - 4);
        String pluginId = id;
        String name = id;
        String version = "1.0.0";
        String author = "External JAR";
        String type = "channel";
        String frontendEntry = "";
        Map<String, String> nameI18n = Collections.emptyMap();
        Map<String, String> descriptionI18n = Collections.emptyMap();
        String description = "";
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("plugin.json");
            if (entry != null) {
                try (InputStream is = zip.getInputStream(entry)) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(is);
                    id = getJsonString(node, "id", id);
                    pluginId = id;
                    JsonNode nameNode = node.get("name");
                    name = pickLocalized(nameNode, name);
                    nameI18n = convertToMap(nameNode);
                    JsonNode descNode = node.get("description");
                    description = pickLocalized(descNode, "");
                    descriptionI18n = convertToMap(descNode);
                    version = getJsonString(node, "version", version);
                    author = getJsonString(node, "author", author);
                    type = getJsonString(node, "type", type);
                    JsonNode entryNode = node.get("entry");
                    if (entryNode != null) {
                        JsonNode fe = entryNode.get("frontend");
                        if (fe != null && !fe.isNull()) {
                            frontendEntry = fe.asString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read plugin.json inside JAR: " + jarPath, e);
        }
        return new PluginInfo(
                id,
                pluginId,
                name,
                description,
                version,
                author,
                type,
                "",
                "",
                "",
                "",
                frontendEntry,
                nameI18n,
                descriptionI18n,
                true,
                false,
                true,
                true);
    }

    private String pickLocalized(JsonNode node, String defaultVal) {
        if (node == null || node.isNull()) {
            return defaultVal;
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isObject()) {
            String[] languages = {"zh-CN", "zh", "en-US", "en"};
            for (String lang : languages) {
                JsonNode v = node.get(lang);
                if (v != null && !v.isNull()) {
                    return v.asString();
                }
            }
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                return field.getValue().asString();
            }
        }
        return defaultVal;
    }

    private Map<String, String> convertToMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, JsonNode> f : node.properties()) {
            map.put(f.getKey(), f.getValue().asString());
        }
        return map;
    }

    private String getJsonString(JsonNode node, String fieldName, String defaultVal) {
        JsonNode f = node.get(fieldName);
        if (f != null && !f.isNull()) {
            return f.asString();
        }
        return defaultVal;
    }

    private Path findPluginJsonDir(Path root) throws IOException {
        if (Files.exists(root.resolve("plugin.json"))) {
            return root;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    if (Files.exists(path.resolve("plugin.json"))) {
                        return path;
                    }
                }
            }
        }
        return null;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
                .forEach(
                        p -> {
                            try {
                                Path dest = target.resolve(source.relativize(p));
                                if (Files.isDirectory(p)) {
                                    if (!Files.exists(dest)) {
                                        Files.createDirectories(dest);
                                    }
                                } else {
                                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(
                        p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                // ignore or log
                            }
                        });
    }

    private boolean isUpgradeAvailable(String installedVersion, String catalogVersion) {
        if (installedVersion == null
                || catalogVersion == null
                || installedVersion.isBlank()
                || catalogVersion.isBlank()) {
            return false;
        }
        try {
            String[] instParts = installedVersion.split("\\.");
            String[] catParts = catalogVersion.split("\\.");
            int length = Math.max(instParts.length, catParts.length);
            for (int i = 0; i < length; i++) {
                int instVal = i < instParts.length ? Integer.parseInt(instParts[i]) : 0;
                int catVal = i < catParts.length ? Integer.parseInt(catParts[i]) : 0;
                if (catVal > instVal) {
                    return true;
                } else if (instVal > catVal) {
                    return false;
                }
            }
        } catch (Exception e) {
            return !installedVersion.equals(catalogVersion);
        }
        return false;
    }
}
