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

import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Plugin runtime record encapsulation.
 *
 * <p>This class represents a plugin node that is currently running or active in the system. It combines the plugin's static metadata ({@link PluginManifest}),
 * physical storage path, built-in indicator, working main plugin instance object, and its allocated dedicated isolated class loader,
 * for unified tracking and management by {@link PluginManager} and other core scheduling components.
 */
public class PluginRecord {
    /**
     * Complete metadata manifest corresponding to the plugin (includes ID, version, name, etc.).
     */
    public final PluginManifest manifest;

    /**
     * Absolute path to the plugin's installation root directory or JAR file on disk.
     * If it's a built-in plugin, this can be {@code null}.
     */
    public final Path sourcePath;

    /**
     * Indicates whether this plugin is enabled.
     */
    public boolean enabled;

    /**
     * The plugin's main entry object instantiated via reflection. This object is the only bridge of communication between the framework and the plugin.
     */
    public EmailclawPlugin instance;

    /**
     * The dedicated class loader allocated for this plugin (if it is an external JAR/ZIP plugin).
     * Used to thoroughly release associated resources and prevent memory leaks upon uninstallation; this can be null for built-in plugins.
     */
    public URLClassLoader classLoader;

    /**
     * Constructs a new plugin runtime record.
     *
     * @param manifest    Plugin static manifest (non-null)
     * @param sourcePath  Disk storage path (can be null for built-in plugins)
     * @param enabled     Whether it is enabled
     * @param instance    Plugin runtime instance (non-null)
     * @param classLoader Dedicated class loader (can be null for built-in plugins)
     */
    public PluginRecord(
            PluginManifest manifest,
            Path sourcePath,
            boolean enabled,
            EmailclawPlugin instance,
            URLClassLoader classLoader) {
        this.manifest = manifest;
        this.sourcePath = sourcePath;
        this.enabled = enabled;
        this.instance = instance;
        this.classLoader = classLoader;
    }
}
