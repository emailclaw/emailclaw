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
package ai.emailclaw.emailclaw.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Core component of the plugin loader.
 *
 * <p>In the AI Agent system, a Plugin is the carrier for expanding system capabilities. It can contain Large Language Models (Provider), interaction communication channels (Channel), Tools (Tool), or UI extension components. PluginLoader is responsible for safely and dynamically loading external JAR/ZIP packages into the JVM at runtime.
 *
 * <p>Primary responsibilities and capabilities:
 * <ul>
 *   <li>Dynamically load plugins from local JAR paths: Establish an isolated class loader environment, parse manifests, and inject them into the system.</li>
 *   <li>Plugin installation and deployment: Support installation via remote URLs or local ZIP files, including security verification mechanisms against Zip Slip attacks.</li>
 *   <li>Hot-Reloading of directories: Periodically scan plugin directories to implement a "drop-in-and-load" dynamic extension experience, which is crucial for real-time hot-swapping of agent capabilities.</li>
 * </ul>
 */
public class PluginLoader {

    private static final Logger LOGGER = Logger.getLogger(PluginLoader.class.getName());

    /**
     * Jackson ObjectMapper (using tools.jackson 3.x to align with the main project dependency).
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Dynamic scan interval (seconds).
     */
    private static final long SCAN_INTERVAL_SECONDS = 5;

    /**
     * Set of known JAR filenames, used for deduplication (to avoid reloading processed files).
     */
    private final Set<String> knownJarNames = ConcurrentHashMap.newKeySet();

    /**
     * Scheduler for background periodic scanning.
     */
    private ScheduledExecutorService scheduler;

    // ======================== Core JAR Loading Logic ========================
    /**
     * Load the specified JAR plugin package from disk and initialize it.
     *
     * <p>The loading process happens within an isolated {@link URLClassLoader} to achieve class isolation and avoid library conflicts between different plugins or between plugins and the host.
     * In the AI Agent system, this method converts a static file package into an active instance with runtime functions (like providing new models, new tools).
     *
     * <p>Boundary conditions:
     * 1. The passed {@code jarPath} must exist and end with ".jar", otherwise an exception is thrown.
     * 2. If no valid plugin.json is found in the JAR, it will fallback to using default manifest data to ensure basic compatibility for regular JARs.
     *
     * @param jarPath        Absolute or relative path of the JAR file to be loaded
     * @param context        Plugin context provided for accessing system-level services during plugin initialization (can be null, depending on the plugin's robustness)
     * @param pluginRegistry Unified plugin registry for receiving all exposed services (Tool, Provider, etc.) emitted by the plugin
     * @return {@link PluginRecord} containing the loaded manifest, runtime instance, and isolated class loader
     * @throws Exception     Thrown when an I/O error or instantiation failure occurs
     */
    public PluginRecord loadPlugin(
            Path jarPath, PluginContext context, PluginRegistry pluginRegistry) throws Exception {
        if (!Files.exists(jarPath) || !jarPath.toString().endsWith(".jar")) {
            throw new IllegalArgumentException("Invalid plugin JAR path: " + jarPath);
        }
        // Create an isolated ClassLoader to prevent class conflicts between plugins
        URL[] urls = {jarPath.toUri().toURL()};
        URLClassLoader classLoader =
                new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        // Attempt to read plugin.json from the JAR
        PluginManifest manifest = readManifestFromJar(classLoader, jarPath);
        // Instantiate the plugin entry point
        EmailclawPlugin pluginInstance = instantiatePlugin(classLoader, manifest);
        // Execute registration and initialization callbacks
        if (pluginInstance != null) {
            pluginInstance.register(pluginRegistry);
            if (context != null) {
                pluginInstance.initialize(context);
            }
        }
        return new PluginRecord(manifest, jarPath, true, pluginInstance, classLoader);
    }

    // ======================== Installation Methods (moved from PluginService)
    // ========================
    /**
     * Automatically download a ZIP format plugin package from a remote URL and install it.
     *
     * <p>The system (AI Agent architecture) usually needs to quickly pull new extension skill packages from the cloud or a remote market, and this method serves as the core entry point.
     *
     * <p>Boundary conditions:
     * 1. Network connectivity must be guaranteed. If there is no response within 60 seconds, a timeout will occur.
     * 2. The download process uses a secure temporary directory to prevent file system pollution. Failed downloads are guaranteed to clean up temporary files.
     *
     * @param url        Download address of the remote plugin's ZIP archive (must be a valid URI)
     * @param pluginsDir The root directory on the host machine where plugins are stored
     * @param force      Whether to force overwrite the old version if a plugin with the same ID already exists (true to overwrite, false to throw an exception)
     * @throws Exception Thrown on network anomalies, non-200 download status returns, or local I/O failures
     */
    public void installPluginFromUrl(String url, Path pluginsDir, boolean force) throws Exception {
        Path tempDir = Files.createTempDirectory("emailclaw-plugin-");
        Path zipFile = tempDir.resolve("plugin.zip");
        try {
            // Download remote ZIP to a temporary directory
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
                        "Failed to download plugin, HTTP status code: " + response.statusCode());
            }
            // Delegate to the ZIP installation method
            installPluginFromZip(zipFile.toFile(), pluginsDir, force);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * Extract and install a plugin from a local ZIP archive.
     *
     * <p>Extracts plugin.json from it, parses the plugin ID, and establishes an exclusive plugin execution space in the target directory.
     *
     * <p>Security and Boundary Conditions:
     * 1. Strict protection against Zip Slip attacks: Any entry that attempts to extract outside the target directory will abort the installation and throw an exception.
     * 2. If a valid plugin.json (which must at least contain a valid ID) is not found inside the ZIP file, the installation will be rejected.
     *
     * @param zipFile    Local ZIP file to be extracted and installed
     * @param pluginsDir System-designated root directory for plugins
     * @param force      Whether to forcefully overwrite when a plugin with the same ID exists
     * @throws Exception Thrown when a security vulnerability (such as Zip Slip), metadata deficiency, or I/O processing failure occurs
     */
    public void installPluginFromZip(File zipFile, Path pluginsDir, boolean force)
            throws Exception {
        Path tempDir = Files.createTempDirectory("emailclaw-plugin-extract-");
        try {
            // Extract ZIP to a temporary directory (including Zip Slip security check)
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                Path resolvedTemp = tempDir.toAbsolutePath().normalize();
                while ((entry = zis.getNextEntry()) != null) {
                    Path newPath = tempDir.resolve(entry.getName()).toAbsolutePath().normalize();
                    // Prevent Zip Slip attacks: Ensure the extraction path is always within the
                    // temporary directory
                    if (!newPath.startsWith(resolvedTemp)) {
                        throw new IOException("Zip Slip attack detected: " + entry.getName());
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
            // Find the directory containing plugin.json
            Path pluginSourceDir = findPluginJsonDir(tempDir);
            if (pluginSourceDir == null) {
                throw new IllegalArgumentException("plugin.json not found in the ZIP file");
            }
            // Parse plugin.json to obtain the plugin ID
            Path manifestPath = pluginSourceDir.resolve("plugin.json");
            PluginManifest manifest = parsePluginJsonFile(manifestPath);
            if (manifest == null || manifest.id == null || manifest.id.isBlank()) {
                throw new IllegalArgumentException("Invalid plugin.json in the ZIP file");
            }
            // Install to the target directory
            Path destDir = pluginsDir.resolve(manifest.id);
            if (Files.exists(destDir)) {
                if (!force) {
                    throw new IllegalStateException(
                            "Plugin " + manifest.id + " is already installed");
                }
                deleteDirectoryRecursively(destDir);
            }
            Files.createDirectories(destDir);
            copyDirectory(pluginSourceDir, destDir);
            LOGGER.info("Successfully installed plugin " + manifest.id + " to " + destDir);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    // ======================== Dynamic Directory Scanning ========================
    /**
     * Start a background daemon thread to periodically monitor and scan the plugin directory, enabling hot-plugging load capabilities for plugins.
     *
     * <p>During the runtime cycle of the AI Agent, without downtime, this scanner dynamically captures newly dropped JAR packages in the directory,
     * automatically loading new tools or new models and immediately making them available to the business logic.
     *
     * <p>Boundary Conditions:
     * 1. If the specified {@code pluginsDir} is null or does not exist on disk, the task will not start, and only a warning log will be produced.
     * 2. The scheduler uses daemon threads, ensuring that the system is not blocked during shutdown, thereby guaranteeing a smooth exit of the main program.
     *
     * @param pluginsDir     Target plugin directory to be monitored
     * @param context        System context passed to the new plugin
     * @param pluginRegistry Registry object into which newly discovered plugins are injected
     * @param onLoaded       Callback interface triggered after a new plugin is successfully loaded and instantiated, useful for responses in the UI or internal services
     */
    public void startDirectoryWatcher(
            Path pluginsDir,
            PluginContext context,
            PluginRegistry pluginRegistry,
            BiConsumer<String, PluginRecord> onLoaded) {
        if (pluginsDir == null || !Files.exists(pluginsDir)) {
            LOGGER.warning("Plugin directory does not exist, skipping dynamic scan: " + pluginsDir);
            return;
        }
        // Use a daemon thread scheduler, automatically terminates when the JVM exits
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "plugin-dir-watcher");
                            t.setDaemon(true);
                            return t;
                        });
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        scanForNewJars(pluginsDir, context, pluginRegistry, onLoaded);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error while scanning plugin directory", e);
                    }
                },
                SCAN_INTERVAL_SECONDS,
                SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        LOGGER.info(
                "Started dynamic monitoring of the plugin directory (scanning every "
                        + SCAN_INTERVAL_SECONDS
                        + " seconds): "
                        + pluginsDir);
    }

    /**
     * Stop the background directory scanning.
     */
    public void stopDirectoryWatcher() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            LOGGER.info("Stopped dynamic monitoring of the plugin directory.");
        }
    }

    /**
     * Mark the specified JAR filename as known (to prevent repeated loading).
     *
     * @param jarFileName JAR filename
     */
    public void markAsKnown(String jarFileName) {
        knownJarNames.add(jarFileName);
    }

    // ======================== Internal Utility Methods ========================
    /**
     * Scan the plugin directory for newly added JAR files and automatically load them.
     */
    private void scanForNewJars(
            Path pluginsDir,
            PluginContext context,
            PluginRegistry pluginRegistry,
            BiConsumer<String, PluginRecord> onLoaded) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                String fileName = jar.getFileName().toString();
                // Only process previously unseen JAR files
                if (knownJarNames.add(fileName)) {
                    LOGGER.info(
                            "Found newly added plugin JAR: "
                                    + fileName
                                    + ", automatically loading...");
                    try {
                        PluginRecord record = loadPlugin(jar, context, pluginRegistry);
                        String id =
                                (record.manifest.id != null && !record.manifest.id.isBlank())
                                        ? record.manifest.id
                                        : fileName;
                        if (record.instance != null) {
                            record.instance.start();
                        }
                        if (onLoaded != null) {
                            onLoaded.accept(id, record);
                        }
                        LOGGER.info("Successfully loaded plugin dynamically: " + id);
                    } catch (Exception e) {
                        LOGGER.log(
                                Level.SEVERE, "Failed to load plugin dynamically: " + fileName, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan plugin directory", e);
        }
    }

    /**
     * Read the plugin manifest from the embedded plugin.json within the JAR.
     *
     * @param classLoader The ClassLoader corresponding to the JAR
     * @param jarPath     The JAR file path (used for logging and fallback ID)
     * @return Parsed manifest object
     */
    private PluginManifest readManifestFromJar(URLClassLoader classLoader, Path jarPath) {
        PluginManifest manifest;
        try (InputStream is = classLoader.getResourceAsStream("plugin.json")) {
            if (is != null) {
                manifest = MAPPER.readValue(is, PluginManifest.class);
            } else {
                LOGGER.warning("plugin.json not found in JAR, using default metadata: " + jarPath);
                manifest = new PluginManifest();
                manifest.id = jarPath.getFileName().toString();
                manifest.pluginType = PluginType.GENERAL;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read plugin.json in JAR: " + jarPath, e);
            manifest = new PluginManifest();
            manifest.id = jarPath.getFileName().toString();
            manifest.pluginType = PluginType.GENERAL;
        }
        return manifest;
    }

    /**
     * Instantiate the plugin entry point via SPI or reflection.
     *
     * @param classLoader Plugin-exclusive ClassLoader
     * @param manifest    Plugin manifest
     * @return Instantiated EmailclawPlugin, or null if there is no entry point
     */
    private EmailclawPlugin instantiatePlugin(URLClassLoader classLoader, PluginManifest manifest)
            throws Exception {
        // Try the SPI mechanism first
        ServiceLoader<EmailclawPlugin> serviceLoader =
                ServiceLoader.load(EmailclawPlugin.class, classLoader);
        for (EmailclawPlugin plugin : serviceLoader) {
            // Take the first SPI entry point for each JAR
            return plugin;
        }
        // Fallback to reflective loading of the entry_point declared in plugin.json
        if (manifest.entryPoint != null && !manifest.entryPoint.isBlank()) {
            Class<?> clazz = classLoader.loadClass(manifest.entryPoint);
            return (EmailclawPlugin) clazz.getDeclaredConstructor().newInstance();
        }
        return null;
    }

    /**
     * Parse the plugin.json file on disk into a {@link PluginManifest}.
     *
     * <p>Simultaneously handles nested structures like i18n fields and entry objects that require manual mapping.
     *
     * @param manifestPath Path to the plugin.json file
     * @return Parsed manifest, or null if parsing fails
     */
    PluginManifest parsePluginJsonFile(Path manifestPath) {
        try {
            String content = Files.readString(manifestPath);
            JsonNode node = MAPPER.readTree(content);
            PluginManifest manifest = new PluginManifest();
            manifest.id =
                    getJsonString(node, "id", manifestPath.getParent().getFileName().toString());
            manifest.pluginId = manifest.id;
            // Name and description support i18n objects or pure strings
            JsonNode nameNode = node.get("name");
            manifest.name = pickLocalized(nameNode, manifest.id);
            manifest.nameI18n = convertToMap(nameNode);
            JsonNode descNode = node.get("description");
            manifest.description = pickLocalized(descNode, "");
            manifest.descriptionI18n = convertToMap(descNode);
            manifest.version = getJsonString(node, "version", "1.0.0");
            manifest.author = getJsonString(node, "author", "Unknown");
            // Type field supports "type" or "platform"
            String typeStr = getJsonString(node, "type", null);
            if (typeStr == null) {
                typeStr = getJsonString(node, "platform", "general");
            }
            manifest.pluginType = PluginType.fromValue(typeStr);
            // Frontend entry
            JsonNode entryNode = node.get("entry");
            if (entryNode != null) {
                JsonNode fe = entryNode.get("frontend");
                if (fe != null && !fe.isNull()) {
                    manifest.frontendEntry = fe.asString();
                }
            }
            manifest.installed = true;
            manifest.enabled = true;
            manifest.loaded = true;
            return manifest;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse plugin.json: " + manifestPath, e);
            return null;
        }
    }

    /**
     * Parse plugin information from a JAR file (without ClassLoader loading, only reads metadata).
     *
     * @param jarPath JAR file path
     * @return Parsed manifest object
     */
    PluginManifest parsePluginJarMeta(Path jarPath) {
        PluginManifest manifest = new PluginManifest();
        String filename = jarPath.getFileName().toString();
        manifest.id = filename.substring(0, filename.length() - 4);
        manifest.pluginId = manifest.id;
        manifest.name = manifest.id;
        manifest.version = "1.0.0";
        manifest.author = "External JAR";
        manifest.pluginType = PluginType.CHANNEL;
        manifest.installed = true;
        manifest.enabled = true;
        manifest.loaded = true;
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("plugin.json");
            if (entry != null) {
                try (InputStream is = zip.getInputStream(entry)) {
                    JsonNode node = MAPPER.readTree(is);
                    manifest.id = getJsonString(node, "id", manifest.id);
                    manifest.pluginId = manifest.id;
                    JsonNode nameNode = node.get("name");
                    manifest.name = pickLocalized(nameNode, manifest.name);
                    manifest.nameI18n = convertToMap(nameNode);
                    JsonNode descNode = node.get("description");
                    manifest.description = pickLocalized(descNode, "");
                    manifest.descriptionI18n = convertToMap(descNode);
                    manifest.version = getJsonString(node, "version", manifest.version);
                    manifest.author = getJsonString(node, "author", manifest.author);
                    String typeStr = getJsonString(node, "type", null);
                    if (typeStr != null) {
                        manifest.pluginType = PluginType.fromValue(typeStr);
                    }
                    JsonNode entryNode = node.get("entry");
                    if (entryNode != null) {
                        JsonNode fe = entryNode.get("frontend");
                        if (fe != null && !fe.isNull()) {
                            manifest.frontendEntry = fe.asString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read plugin.json in JAR: " + jarPath, e);
        }
        return manifest;
    }

    // ======================== JSON / File Utility Methods ========================
    /**
     * Pick the optimal localized text from an i18n JSON node.
     * Priority: zh-CN > zh > en-US > en > any first > default value.
     */
    String pickLocalized(JsonNode node, String defaultVal) {
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

    /**
     * Convert an i18n JSON object node to a Map.
     */
    Map<String, String> convertToMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, JsonNode> f : node.properties()) {
            map.put(f.getKey(), f.getValue().asString());
        }
        return map;
    }

    /**
     * Safely read the string value of a JSON field.
     */
    String getJsonString(JsonNode node, String fieldName, String defaultVal) {
        JsonNode f = node.get(fieldName);
        if (f != null && !f.isNull()) {
            return f.asString();
        }
        return defaultVal;
    }

    /**
     * Search the directory tree for a directory containing plugin.json (searching at most one subdirectory level).
     */
    private Path findPluginJsonDir(Path root) throws IOException {
        if (Files.exists(root.resolve("plugin.json"))) {
            return root;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && Files.exists(path.resolve("plugin.json"))) {
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * Recursively copy a directory.
     */
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

    /**
     * Recursively delete a directory and its contents.
     */
    void deleteDirectoryRecursively(Path path) throws IOException {
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
                                LOGGER.log(Level.FINE, "Failed to delete file: " + p, e);
                            }
                        });
    }
}
