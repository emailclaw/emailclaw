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
package ai.emailclaw.emailclaw.channel;

import ai.emailclaw.emailclaw.channel.spi.ChannelPlugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * External plugin loader.
 *
 * <p>Scans JAR files in the {@code ~/emailclaw/plugins/} directory,
 * and dynamically discovers and loads {@link ChannelPlugin} implementations
 * via {@link URLClassLoader} + {@link ServiceLoader} mechanism.
 *
 * <p>Each external JAR must declare its implementation class in the
 * {@code META-INF/services/ai.emailclaw.emailclaw.channel.spi.ChannelPlugin} file.
 *
 * <p>Class loading isolation strategy:
 * <ul>
 *   <li>All external JARs share a single {@link URLClassLoader},
 *       whose parent is the application's main ClassLoader,
 *       so external plugins can access SPI interfaces and core model classes.</li>
 *   <li>Classes between external plugins are not isolated (shared ClassLoader),
 *       if complete isolation is needed, a separate ClassLoader per JAR can be introduced in Phase 5.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 *   ExternalPluginLoader loader = new ExternalPluginLoader(paths.pluginsDir);
 *   List<ChannelPlugin> externalPlugins = loader.loadPlugins();
 * }</pre>
 */
public class ExternalPluginLoader {

    private static final Logger LOGGER = Logger.getLogger(ExternalPluginLoader.class.getName());

    private final Path pluginsDir;
    private URLClassLoader pluginClassLoader;

    /**
     * Construct external plugin loader.
     *
     * @param pluginsDir Plugin JAR directory path (usually {@code ~/emailclaw/plugins/})
     */
    public ExternalPluginLoader(Path pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    /**
     * Scan plugin directory and load all ChannelPlugin implementations.
     *
     * <p>Returns an empty list if directory does not exist or is empty.
     * Failure of a single JAR does not affect other JARs.
     *
     * @return List of discovered external plugins (immutable)
     */
    public List<ChannelPlugin> loadPlugins() {
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            LOGGER.fine("External plugin directory does not exist, skipping external plugin load");
            return Collections.emptyList();
        }

        List<URL> jarUrls = collectJarUrls();
        if (jarUrls.isEmpty()) {
            LOGGER.fine("External plugin directory is empty, no JAR files");
            return Collections.emptyList();
        }

        LOGGER.log(
                Level.INFO, "Found {0} JAR files in plugin directory, loading...", jarUrls.size());

        // Create shared URLClassLoader, parent is current thread's ClassLoader
        pluginClassLoader =
                new URLClassLoader(
                        jarUrls.toArray(new URL[0]),
                        Thread.currentThread().getContextClassLoader());

        List<ChannelPlugin> plugins = new ArrayList<>();
        ServiceLoader<ChannelPlugin> loader =
                ServiceLoader.load(ChannelPlugin.class, pluginClassLoader);

        for (ChannelPlugin plugin : loader) {
            try {
                LOGGER.log(
                        Level.INFO,
                        "Discovered external Channel plugin: id={0}, name={1}, class={2}",
                        new Object[] {
                            plugin.id(), plugin.displayName(), plugin.getClass().getName()
                        });
                plugins.add(plugin);
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to load external plugin instance: " + plugin.getClass().getName(),
                        e);
            }
        }

        if (plugins.isEmpty()) {
            LOGGER.info("No ChannelPlugin SPI registration found in external JAR, skipping");
        } else {
            LOGGER.log(
                    Level.INFO, "Successfully loaded {0} external Channel plugins", plugins.size());
        }

        return Collections.unmodifiableList(plugins);
    }

    /**
     * Close external plugin's ClassLoader, releasing JAR file handles.
     *
     * <p>Should be called upon application shutdown.
     */
    public void close() {
        if (pluginClassLoader != null) {
            try {
                pluginClassLoader.close();
                LOGGER.fine("External plugin ClassLoader closed");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to close external plugin ClassLoader", e);
            }
            pluginClassLoader = null;
        }
    }

    /**
     * Scan plugin directory, collecting URLs of all .jar files.
     */
    private List<URL> collectJarUrls() {
        List<URL> urls = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                try {
                    urls.add(jar.toUri().toURL());
                    LOGGER.log(Level.FINE, "Discovered plugin JAR: {0}", jar.getFileName());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to resolve JAR path: " + jar, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan plugin directory: " + pluginsDir, e);
        }
        return urls;
    }
}
