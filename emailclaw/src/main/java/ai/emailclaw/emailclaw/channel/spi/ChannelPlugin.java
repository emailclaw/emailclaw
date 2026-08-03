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
package ai.emailclaw.emailclaw.channel.spi;

import java.util.List;

/**
 * Standard contract interface for Channel plugins.
 *
 * <p>All message channels (DingTalk, Emailclaw, Telegram, etc.)
 * connect to the framework by implementing this interface. The framework automatically
 * discovers and manages plugin lifecycles via Java SPI ({@link java.util.ServiceLoader}).
 *
 * <p>Plugin Lifecycle:
 * <pre>
 *   discover → initialize → start ⇄ reconfigure → stop → destroy
 * </pre>
 *
 * <p>Key Design Principles:
 * <ul>
 *   <li>Each plugin returns a unique identifier via {@link #id()}, which must match the id in ChannelService.BUILTIN_CHANNELS</li>
 *   <li>Plugin declares config requirements via {@link #configSchema()}, framework automatically generates UI forms based on this</li>
 *   <li>Plugin interacts with core system via {@link ChannelContext}, without directly depending on concrete Service implementations</li>
 * </ul>
 */
public interface ChannelPlugin {

    /**
     * Plugin unique identifier.
     *
     * <p>Must match the id of the corresponding entry in {@code ChannelService.BUILTIN_CHANNELS},
     * the framework uses this ID for config association and deduplication.
     *
     * @return e.g. "dingtalk", "emailclaw", "telegram", etc.
     */
    String id();

    /**
     * Human-readable display name.
     *
     * @return e.g. "DingTalk", "Emailclaw", "Telegram", etc.
     */
    String displayName();

    /**
     * Declare the configuration item schema required by this plugin.
     *
     * <p>The framework will use this schema to:
     * <ul>
     *   <li>Automatically generate configuration forms on the UI Channel settings page</li>
     *   <li>Perform non-empty validation on required fields</li>
     *   <li>Persist configuration in the plugin's independent namespace</li>
     * </ul>
     *
     * <p>Current Phase 1 stage temporarily returns an empty list (UI still uses existing forms),
     * Phase 2 will enable this mechanism to drive automatic UI generation.
     *
     * @return List of configuration item descriptions, cannot be null
     */
    default List<ConfigFieldDescriptor> configSchema() {
        return List.of();
    }

    /**
     * Initialize the plugin.
     *
     * <p>The framework calls this method to inject the unified context immediately after discovering the plugin.
     * This stage should not establish external connections, only prepare internal state.
     *
     * @param context Unified context provided by the framework
     */
    void initialize(ChannelContext context);

    /**
     * Start the plugin.
     *
     * <p>Only called when the user enables the channel on the UI and the configuration is valid.
     * The plugin should establish external connections, start polling/listening threads, etc. in this method.
     *
     * <p>This method may be called again after {@link #stop()} (user re-enables channel).
     */
    void start();

    /**
     * Stop the plugin.
     *
     * <p>Release external connections, stop background threads, but keep internal state to support another {@link #start()}.
     *
     * <p>Implementations should ensure this method is idempotent - calling it multiple times will not throw exceptions.
     */
    void stop();

    /**
     * Destroy the plugin, release all resources.
     *
     * <p>Called by the framework before JVM exit, no other methods will be called afterwards.
     * Default implementation delegates to {@link #stop()}.
     */
    default void destroy() {
        stop();
    }

    /**
     * Get a snapshot of the current running state.
     *
     * <p>The framework periodically calls this method to get plugin health information,
     * and displays it to the user in the UI Channel panel.
     *
     * @return Current state, cannot be null
     */
    ChannelStatus status();
}
