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

import ai.emailclaw.emailclaw.channel.spi.ChannelContext;
import ai.emailclaw.emailclaw.channel.spi.ChannelPlugin;
import ai.emailclaw.emailclaw.channel.spi.ChannelStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel plugin lifecycle manager.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Automatically discover all built-in {@link ChannelPlugin} implementations via {@link ServiceLoader}</li>
 *   <li>Load external plugin JARs from {@code ~/emailclaw/plugins/} via {@link ExternalPluginLoader}</li>
 *   <li>Manage plugin lifecycle: initialize → start → stop → destroy</li>
 *   <li>Provide unified query and control entry points</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 *   ChannelContext ctx = new DefaultChannelContext(channelService, chatService, agentService, providerService);
 *   ChannelManager manager = new ChannelManager(ctx, paths.pluginsDir);
 *   // ... upon application exit ...
 *   manager.shutdownAll();
 * }</pre>
 */
public class ChannelManager {

    private static final Logger LOGGER = Logger.getLogger(ChannelManager.class.getName());

    private final Map<String, ChannelPlugin> plugins = new LinkedHashMap<>();
    private final ChannelContext context;
    private final ExternalPluginLoader externalLoader;

    /**
     * Construct and initialize plugin manager (without loading external plugins).
     *
     * @param context Framework unified context
     */
    public ChannelManager(ChannelContext context) {
        this(context, null);
    }

    /**
     * Construct and initialize plugin manager.
     *
     * <p>Executes the following process immediately upon construction:
     * <ol>
     *   <li>Discover all built-in ChannelPlugin implementation classes via SPI</li>
     *   <li>Load ChannelPlugin implementations in external JARs from pluginsDir</li>
     *   <li>Call {@link ChannelPlugin#initialize(ChannelContext)} on each plugin</li>
     *   <li>Call {@link ChannelPlugin#start()} on each plugin</li>
     * </ol>
     *
     * @param context    Framework unified context
     * @param pluginsDir External plugin JAR directory (null to not load external plugins)
     */
    public ChannelManager(ChannelContext context, Path pluginsDir) {
        this.context = context;

        // 1. Discover built-in plugins via Java SPI
        ServiceLoader<ChannelPlugin> loader = ServiceLoader.load(ChannelPlugin.class);
        for (ChannelPlugin plugin : loader) {
            registerPlugin(plugin, "Built-in");
        }

        // 2. Load plugins from external JARs
        if (pluginsDir != null) {
            this.externalLoader = new ExternalPluginLoader(pluginsDir);
            List<ChannelPlugin> externalPlugins = externalLoader.loadPlugins();
            for (ChannelPlugin plugin : externalPlugins) {
                registerPlugin(plugin, "External");
            }
        } else {
            this.externalLoader = null;
        }

        // 3. Initialize and start all discovered plugins
        for (ChannelPlugin plugin : plugins.values()) {
            try {
                plugin.initialize(context);
                plugin.start();
                LOGGER.log(Level.INFO, "Channel plugin started: {0}", plugin.id());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to start Channel plugin: " + plugin.id(), e);
            }
        }
    }

    /**
     * Register a single plugin (deduplicated).
     */
    private void registerPlugin(ChannelPlugin plugin, String source) {
        if (plugins.containsKey(plugin.id())) {
            LOGGER.log(
                    Level.WARNING,
                    "Found duplicate Channel plugin ID: {0} ({1}), ignoring the latter"
                            + " implementation: {2}",
                    new Object[] {plugin.id(), source, plugin.getClass().getName()});
            return;
        }
        plugins.put(plugin.id(), plugin);
        LOGGER.log(
                Level.INFO,
                "Registered {0} Channel plugin: id={1}, name={2}, class={3}",
                new Object[] {
                    source, plugin.id(), plugin.displayName(), plugin.getClass().getName()
                });
    }

    /**
     * Get an immutable list of all registered plugins.
     *
     * @return Plugin list (ordered by discovery)
     */
    public List<ChannelPlugin> registeredPlugins() {
        return Collections.unmodifiableList(new ArrayList<>(plugins.values()));
    }

    /**
     * Get specified plugin by ID.
     *
     * @param id Plugin ID
     * @return Plugin instance, returns null if not found
     */
    public ChannelPlugin getPlugin(String id) {
        return plugins.get(id);
    }

    /**
     * Get status snapshots of all plugins.
     *
     * @return Mapping of pluginId → ChannelStatus
     */
    public Map<String, ChannelStatus> allStatuses() {
        Map<String, ChannelStatus> result = new LinkedHashMap<>();
        for (Map.Entry<String, ChannelPlugin> entry : plugins.entrySet()) {
            try {
                result.put(entry.getKey(), entry.getValue().status());
            } catch (Exception e) {
                result.put(
                        entry.getKey(),
                        ChannelStatus.error("Failed to get status: " + e.getMessage()));
            }
        }
        return result;
    }

    /**
     * Gracefully shutdown all plugins and release external ClassLoader.
     *
     * <p>Calls {@link ChannelPlugin#destroy()} sequentially in registration order,
     * exceptions in a single plugin will not affect the shutdown of others.
     */
    public void shutdownAll() {
        LOGGER.info("Shutting down all Channel plugins...");
        for (Map.Entry<String, ChannelPlugin> entry : plugins.entrySet()) {
            try {
                entry.getValue().destroy();
                LOGGER.log(Level.INFO, "Channel plugin shutdown: {0}", entry.getKey());
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Exception shutting down Channel plugin: " + entry.getKey(),
                        e);
            }
        }
        // Close external plugin ClassLoader
        if (externalLoader != null) {
            externalLoader.close();
        }
        LOGGER.info("All Channel plugins shut down.");
    }
}
