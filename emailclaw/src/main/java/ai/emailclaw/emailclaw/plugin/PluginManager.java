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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Plugin manager (core coordinating class).
 *
 * <p>In the AI Agent system, this class is the central nerve controlling the lifecycle of external extensions. It is responsible for scheduling the underlying {@link PluginLoader} to execute the actual
 * JAR reading and extraction work, and uniformly loading the loaded plugin instances into the {@link PluginRegistry}, thereby transforming discrete external packages into living tools and models that can be called upon at any time in the system.
 *
 * <p>Merged the original {@code PluginManager} (runtime loading/unloading) and {@code PluginService} (installation status querying,
 * official catalog, uninstalling disk files, etc.), providing a unified API for the full lifecycle management of plugins to the outside:
 * <ul>
 *   <li>Initial scanning and loading of built-in + external plugins ({@link #discoverAndLoadAll})</li>
 *   <li>Install plugins from URL / ZIP (delegated to {@link PluginLoader})</li>
 *   <li>Uninstall local plugins (memory + disk cleanup)</li>
 *   <li>Get a list of installed / official plugins (for the store panel in the UI layer)</li>
 *   <li>Coordinate dynamic directory monitoring and callbacks, integrating dynamically loaded instances into the registry</li>
 * </ul>
 *
 * <p>View layers such as {@code PluginManagerView} only need to depend on this class to obtain a full set of plugin management capabilities.
 */
public class PluginManager {

    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());

    /**
     * Jackson ObjectMapper (uses tools.jackson 3.x).
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * CDN address of the official plugin catalog.
     */
    private static final String OFFICIAL_CATALOG_URL =
            "https://platform.emailclaw.email/metadata/plugins/index.json";

    /**
     * Local plugin installation directory.
     */
    private final Path pluginsDir;

    /**
     * Plugin loader (responsible for JAR loading and ZIP installation).
     */
    private final PluginLoader pluginLoader;

    /**
     * Framework context, providing plugins access to core services.
     */
    private final PluginContext context;

    /**
     * Registry of running plugins (pluginId -> PluginRecord).
     */
    private final Map<String, PluginRecord> loadedPlugins = new ConcurrentHashMap<>();

    private final PluginRegistry pluginRegistry;

    /**
     * Constructs a plugin manager. This process uses Pure DI (Pure Dependency Injection) principles, rejecting any global static state.
     *
     * @param context        Framework context, used to provide system-level service access to plugins
     * @param pluginsDir     Plugin installation root directory; if it does not yet exist on disk, an attempt will be made to automatically create it during construction
     * @param pluginRegistry Global plugin registry, used to centrally store loaded instances
     */
    public PluginManager(PluginContext context, Path pluginsDir, PluginRegistry pluginRegistry) {
        this.context = context;
        this.pluginsDir = pluginsDir;
        this.pluginRegistry = pluginRegistry;
        this.pluginLoader = new PluginLoader();
        // Ensure plugin directory exists
        if (pluginsDir != null && !Files.exists(pluginsDir)) {
            try {
                Files.createDirectories(pluginsDir);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create plugin directory: " + pluginsDir, e);
            }
        }
    }

    // ======================== Startup / Discovery / Shutdown ========================
    /**
     * Called when the system starts: scans and loads all types of plugins, including built-in SPI plugins and external JAR plugins, and starts hot-reloading monitoring.
     *
     * <p>Once loaded, it will automatically call the {@code start()} hook of all plugins, officially taking over these external capabilities.
     *
     * <p>Boundary conditions: If external JAR files are corrupted or do not meet specifications, loading will fail and log a SEVERE message, but it will never block the loading of subsequent plugins,
     * to ensure the core startup of the entire AI system is not affected by individual "bad plugins".
     */
    public void discoverAndLoadAll() {
        LOGGER.info("Plugin call started: Scanning and loading all plugins");
        // 1. Built-in plugins (registered via SPI, like DingTalk, Emailclaw)
        ServiceLoader<EmailclawPlugin> builtinLoader = ServiceLoader.load(EmailclawPlugin.class);
        for (EmailclawPlugin plugin : builtinLoader) {
            registerBuiltinPlugin(plugin);
        }
        // 2. External JAR plugins
        if (pluginsDir != null && Files.exists(pluginsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
                for (Path jar : stream) {
                    try {
                        PluginRecord record = pluginLoader.loadPlugin(jar, context, pluginRegistry);
                        String id =
                                (record.manifest.id != null && !record.manifest.id.isBlank())
                                        ? record.manifest.id
                                        : jar.getFileName().toString();
                        loadedPlugins.put(id, record);
                        pluginRegistry.registerPluginInstance(id, record.instance);
                        // Mark as known to avoid repeated loading by dynamic scanning
                        pluginLoader.markAsKnown(jar.getFileName().toString());
                        LOGGER.info("Successfully loaded external plugin: " + id);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Failed to load external plugin: " + jar, e);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error scanning plugin directory", e);
            }
        }
        // 3. Start all loaded plugins
        for (PluginRecord record : loadedPlugins.values()) {
            if (record.instance != null) {
                try {
                    record.instance.start();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to start plugin: " + record.manifest.id, e);
                }
            }
        }
        // 4. Start dynamic directory monitoring, automatically loading user's newly added JAR files
        pluginLoader.startDirectoryWatcher(
                pluginsDir,
                context,
                pluginRegistry,
                (id, record) -> {
                    loadedPlugins.put(id, record);
                    pluginRegistry.registerPluginInstance(id, record.instance);
                    LOGGER.info("Dynamically loaded plugin registered to manager: " + id);
                });
    }

    /**
     * Registers built-in plugins (SPI plugins compiled into the main JAR).
     */
    private void registerBuiltinPlugin(EmailclawPlugin plugin) {
        try {
            PluginManifest manifest = new PluginManifest();
            manifest.id = plugin.id();
            manifest.name = plugin.displayName();
            manifest.pluginId = manifest.id;
            manifest.pluginType = PluginType.GENERAL;
            manifest.installed = true;
            manifest.enabled = true;
            manifest.loaded = true;
            plugin.register(pluginRegistry);
            if (context != null) {
                plugin.initialize(context);
            }
            PluginRecord record = new PluginRecord(manifest, null, true, plugin, null);
            loadedPlugins.put(manifest.id, record);
            pluginRegistry.registerPluginInstance(manifest.id, plugin);
            LOGGER.info("Successfully loaded built-in plugin: " + manifest.id);
        } catch (Exception e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to load built-in plugin: " + plugin.getClass().getName(),
                    e);
        }
    }

    /**
     * Unloads the plugin with the specified ID from the runtime state (i.e., disables it, but does not delete the physical file from disk).
     *
     * <p>It will automatically call back the plugin's {@code destroy()} method to clean up its occupied resources, then completely close and release the corresponding
     * {@code URLClassLoader}, ensuring memory leaks are prevented and cutting off the AI system's access to the tools or models it provides.
     *
     * @param pluginId The unique ID of the plugin to unload (non-empty)
     */
    public void unloadPlugin(String pluginId) {
        PluginRecord record = loadedPlugins.remove(pluginId);
        pluginRegistry.unregisterPluginInstance(pluginId);
        if (record != null) {
            if (record.instance != null) {
                try {
                    record.instance.destroy();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error executing plugin destroy", e);
                }
            }
            if (record.classLoader != null) {
                try {
                    record.classLoader.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to close plugin ClassLoader", e);
                }
            }
            LOGGER.info("Unloaded plugin: " + pluginId);
        }
    }

    /**
     * Gracefully closes all currently running plugins and stops any background directory scanners.
     *
     * <p>Triggered by the application bootstrap during system shutdown to ensure all external connections, threads, and caches are properly released.
     */
    public void shutdownAll() {
        LOGGER.info("Plugin call started: Close all plugins");
        pluginLoader.stopDirectoryWatcher();
        for (String id : new ArrayList<>(loadedPlugins.keySet())) {
            unloadPlugin(id);
        }
        LOGGER.info("All plugins have been closed.");
    }

    // ======================== Install / Uninstall (Disk Operations) ========================
    /**
     * Retrieves a plugin in archive format from a remote URL, and automatically extracts, deploys, and loads it.
     *
     * <p>The system (AI Agent architecture) usually needs to quickly pull new extension skill packages from the cloud or remote market, and this method is the core entry point.
     *
     * <p>Boundary conditions: If the specified URL fails to download, temporary files and partially extracted files will definitely be automatically cleaned up.
     *
     * @param url   Remote download URL
     * @param force If a plugin with the same name already exists, whether to forcefully uninstall the old version and overwrite deployment
     * @throws Exception Thrown when download timeouts occur, HTTP is not 200, or extraction/installation fails
     */
    public void installPluginFromUrl(String url, boolean force) throws Exception {
        pluginLoader.installPluginFromUrl(url, pluginsDir, force);
    }

    /**
     * Installs a plugin from a specified local ZIP archive package, extracting and deploying it to the plugin directory.
     *
     * @param zipFile File instance pointing to the local ZIP package
     * @param force   If a plugin with the same name is already deployed, whether to force overwrite the old version
     * @throws Exception Thrown when extraction fails, file system read/write errors occur, or security risks (like Zip Slip) are detected
     */
    public void installPluginFromZip(File zipFile, boolean force) throws Exception {
        pluginLoader.installPluginFromZip(zipFile, pluginsDir, force);
    }

    /**
     * Completely removes a plugin from the system (including unloading the memory state and physically deleting the directory or JAR file on disk).
     *
     * <p>This is a dangerous destruction operation; once successful, the AI Agent will permanently lose the capabilities granted by the plugin until it is reinstalled next time.
     *
     * @param id The unique identifier of the target plugin
     * @throws Exception Thrown when stopping the plugin fails, or when disk file deletion encounters I/O errors such as lack of permission or file locks
     */
    public void uninstallPlugin(String id) throws Exception {
        // 1. Retrieve the runtime record to preserve its physical sourcePath before unloading
        PluginRecord record = loadedPlugins.get(id);
        Path targetPath = record != null ? record.sourcePath : null;

        // 2. Unload the running instance from memory
        unloadPlugin(id);

        // 3. If sourcePath was recorded and exists on disk, delete it directly
        if (targetPath != null && Files.exists(targetPath)) {
            if (Files.isDirectory(targetPath)) {
                pluginLoader.deleteDirectoryRecursively(targetPath);
            } else {
                Files.delete(targetPath);
            }
            LOGGER.info("Deleted plugin from sourcePath: " + targetPath);
            return;
        }

        // 4. Try exact directory match
        Path pluginDir = pluginsDir.resolve(id);
        if (Files.exists(pluginDir) && Files.isDirectory(pluginDir)) {
            pluginLoader.deleteDirectoryRecursively(pluginDir);
            LOGGER.info("Deleted plugin directory: " + id);
            return;
        }

        // 5. Try exact JAR match (id.jar)
        Path exactJarFile = pluginsDir.resolve(id + ".jar");
        if (Files.exists(exactJarFile)) {
            Files.delete(exactJarFile);
            LOGGER.info("Deleted exact JAR plugin: " + id);
            return;
        }

        // 6. Scan pluginsDir for versioned JARs (e.g. id-1.0.0.jar) or match by parsing plugin.json
        if (pluginsDir != null && Files.exists(pluginsDir) && Files.isDirectory(pluginsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir)) {
                for (Path p : stream) {
                    String fileName = p.getFileName().toString();
                    // 6.1 Match versioned JAR filename prefix (e.g., id-*.jar)
                    if (Files.isRegularFile(p)
                            && fileName.startsWith(id + "-")
                            && fileName.endsWith(".jar")) {
                        Files.delete(p);
                        LOGGER.info("Deleted versioned JAR plugin: " + fileName);
                        return;
                    }
                    // 6.2 Match by reading plugin.json manifest inside JAR
                    if (Files.isRegularFile(p) && fileName.endsWith(".jar")) {
                        PluginManifest manifest = pluginLoader.parsePluginJarMeta(p);
                        if (manifest != null && id.equals(manifest.id)) {
                            Files.delete(p);
                            LOGGER.info("Deleted matched JAR plugin by manifest ID: " + fileName);
                            return;
                        }
                    }
                }
            }
        }

        throw new FileNotFoundException(
                "Could not find directory or JAR file corresponding to plugin: " + id);
    }

    // ======================== Query Interfaces (for UI use) ========================
    /**
     * Collects a list of manifest information for plugins currently physically deployed locally on the system.
     *
     * <p>By scanning a specified directory, it returns a mixed result of two plugin forms: module plugins with a complete directory structure and a plugin.json,
     * and flat JAR files existing as singletons. This list is often extracted and rendered to the system's "Installed" UI panel.
     *
     * @return Each item exhaustively maps the local plugin's basic information (no error if it doesn't exist, returns an empty list if it's an empty directory)
     */
    public List<PluginManifest> getInstalledPlugins() {
        List<PluginManifest> list = new ArrayList<>();
        if (pluginsDir == null || !Files.exists(pluginsDir) || !Files.isDirectory(pluginsDir)) {
            return list;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    // Directory-type plugin: contains plugin.json
                    Path manifestPath = path.resolve("plugin.json");
                    if (Files.exists(manifestPath)) {
                        PluginManifest manifest = pluginLoader.parsePluginJsonFile(manifestPath);
                        if (manifest != null) {
                            list.add(manifest);
                        }
                    }
                } else if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                    // JAR-type plugin
                    PluginManifest manifest = pluginLoader.parsePluginJarMeta(path);
                    if (manifest != null) {
                        list.add(manifest);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to scan installed plugin list", e);
        }
        return list;
    }

    /**
     * Requests the most comprehensive unified certified plugin catalog from the remote official server (used to implement the app store functionality).
     *
     * <p>Not only will it request external API data, but after obtaining the remote list, it will intelligently call {@link #getInstalledPlugins()} internally for comparison,
     * to mark which remote plugins the user has already installed locally, and compare versions to determine if an update is available.
     *
     * <p>Boundary conditions: When the network environment cannot connect to the outside or the remote server is offline, it will silently swallow the exception and simply return an empty available list, so as not to drag down UI rendering.
     *
     * @return Aggregated manifest list with remote data + local comparison status (installed, upgradable)
     */
    public List<PluginManifest> getOfficialPlugins() {
        List<PluginManifest> list = new ArrayList<>();
        try {
            HttpClient client =
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(OFFICIAL_CATALOG_URL))
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(response.body());
                JsonNode files = root.get("files");
                // Get locally installed plugins to associate state
                List<PluginManifest> installed = getInstalledPlugins();
                Map<String, PluginManifest> installedMap = new HashMap<>();
                for (PluginManifest p : installed) {
                    installedMap.put(p.pluginId, p);
                }
                if (files != null && files.isObject()) {
                    for (Map.Entry<String, JsonNode> field : files.properties()) {
                        JsonNode entry = field.getValue();
                        PluginManifest manifest = new PluginManifest();
                        manifest.id = pluginLoader.getJsonString(entry, "id", field.getKey());
                        manifest.pluginId =
                                pluginLoader.getJsonString(entry, "plugin_id", manifest.id);
                        JsonNode nameNode = entry.get("name");
                        manifest.name = pluginLoader.pickLocalized(nameNode, manifest.id);
                        manifest.nameI18n = pluginLoader.convertToMap(nameNode);
                        JsonNode descNode = entry.get("description");
                        manifest.description = pluginLoader.pickLocalized(descNode, "");
                        manifest.descriptionI18n = pluginLoader.convertToMap(descNode);
                        manifest.version = pluginLoader.getJsonString(entry, "version", "1.0.0");
                        manifest.author = pluginLoader.getJsonString(entry, "author", "Unknown");
                        // The type field is represented by "platform" in the catalog
                        String typeStr = pluginLoader.getJsonString(entry, "platform", "tool");
                        manifest.pluginType = PluginType.fromValue(typeStr);
                        manifest.size = pluginLoader.getJsonString(entry, "size", "");
                        manifest.sha256 = pluginLoader.getJsonString(entry, "sha256", "");
                        // Assemble full download URL
                        String relUrl = pluginLoader.getJsonString(entry, "url", "");
                        if (relUrl.startsWith("/")) {
                            manifest.installUrl = "https://platform.emailclaw.email" + relUrl;
                        } else {
                            manifest.installUrl = relUrl;
                        }
                        // Associate local installation status
                        PluginManifest inst = installedMap.get(manifest.pluginId);
                        if (inst != null) {
                            manifest.installed = true;
                            manifest.installedVersion = inst.version;
                            manifest.upgradeAvailable =
                                    isUpgradeAvailable(inst.version, manifest.version);
                        } else {
                            manifest.installed = false;
                            manifest.installedVersion = "";
                            manifest.upgradeAvailable = false;
                        }
                        list.add(manifest);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get official plugin catalog", e);
        }
        // Sort by type, then sort by name
        list.sort(
                (p1, p2) -> {
                    int c = p1.getTypeValue().compareTo(p2.getTypeValue());
                    if (c != 0) return c;
                    return p1.name.compareTo(p2.name);
                });
        return list;
    }

    /**
     * Returns the runtime status and information records of all currently active plugins in the system.
     *
     * @return A snapshot of all currently running plugins in the form of a List (will not contaminate internal structure via external modifications)
     */
    public List<PluginRecord> getAllPlugins() {
        return new ArrayList<>(loadedPlugins.values());
    }

    /**
     * Gets a specific, active plugin and its runtime wrapper by specifying the plugin ID.
     *
     * @param pluginId The unique identification ID of the plugin to query
     * @return Returns its {@link PluginRecord} if found and in active state; otherwise returns null directly
     */
    public PluginRecord getPlugin(String pluginId) {
        return loadedPlugins.get(pluginId);
    }

    /**
     * Gets the plugin directory path.
     *
     * @return Plugin directory
     */
    public Path getPluginsDir() {
        return pluginsDir;
    }

    // ======================== Internal Helper Methods ========================
    /**
     * Compares two semantic version numbers to determine if an upgrade is available.
     *
     * @param installedVersion Installed version
     * @param catalogVersion   Catalog version
     * @return Whether the catalog version is higher than the installed version
     */
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
