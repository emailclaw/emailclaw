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

import ai.emailclaw.emailclaw.channel.ChannelIds;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.plugin.EmailclawPlugin;
import ai.emailclaw.emailclaw.plugin.PluginRegistry;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel configuration service.
 *
 * <p>After refactoring, local channels snapshot is no longer maintained, it uniformly reads from ConfigManager.
 */
public class ChannelService {
    private static final Logger LOGGER = Logger.getLogger(ChannelService.class.getName());

    private final Object channelsLock = new Object();
    private final ConfigManager configManager;
    private final PluginRegistry pluginRegistry;

    private static final String[][] BUILTIN_CHANNELS = {
        {ChannelIds.CONSOLE, "Console"},
        //        {ChannelIds.DINGTALK, "DingTalk"},
        {ChannelIds.EMAILCLAW, "Emailclaw"},
        //            {ChannelIds.FEISHU, "Feishu"},
        //            {ChannelIds.IMESSAGE, "iMessage"},
        //            {ChannelIds.DISCORD, "Discord"},
        //            {ChannelIds.TELEGRAM, "Telegram"},
        //            {ChannelIds.QQ, "QQ"},
        //            {ChannelIds.MATRIX, "Matrix"},
        //            {ChannelIds.SIP, "SIP"},
        //            {ChannelIds.XIAOYI, "XiaoYi"},
        //            {ChannelIds.MATTERMOST, "Mattermost"},
        //            {ChannelIds.MQTT, "MQTT"},
        //            {ChannelIds.VOICE, "Twilio Voice"},
        //            {ChannelIds.WECOM, "WeCom"},
        //            {ChannelIds.WEIXIN, "WeChat"},
        //            {ChannelIds.ONEBOT, "OneBot"},
    };

    /**
     * Construct a ChannelService instance.
     *
     * <p>At system startup, the application context and plugin registry are injected via dependency injection, and a channel snapshot check is triggered during the initialization phase to
     * ensure that the built-in channel configuration exists completely in the local file, and automatically executes cross-version data migration.
     *
     * @param repository     System persistent configuration repository, providing read and write access to the underlying configuration files
     * @param pluginRegistry Plugin registry, used to get specific channel plugins to normalize their configurations
     */
    public ChannelService(AppContext repository, PluginRegistry pluginRegistry) {
        this.configManager = repository.configManager();
        this.pluginRegistry = pluginRegistry;
        // Ensure structural integrity once at startup (built-in channels + migration).
        ensureChannelSnapshot();
    }

    /**
     * Get a list of all available channels.
     *
     * <p>Returns a copied collection of the configuration list currently in memory, but the {@link ChannelInfo} objects within the collection themselves are mutable.
     * Note: After external modules (like the UI layer) modify the properties of elements in this list, they must actively call {@link #save()} to persist the changes to disk.
     * If save is not called, unsaved modifications will be lost when hot-reloading or other memory refresh events occur.
     *
     * @return List containing system built-in and user-added channel configuration information
     */
    public List<ChannelInfo> list() {
        //        LOGGER.info("Channel call started: Read channel configuration snapshot");
        // Compatible with existing UI editing process: return a copy of the list, but elements are
        // still editable object references
        return new ArrayList<>(ensureChannelSnapshot());
    }

    /**
     * Toggle the enabled/disabled state of the specified channel.
     *
     * <p>Finds the channel in the system matching this ID, flips its enabled state, and immediately triggers a persistent save.
     *
     * @param channel The channel object whose state needs to be toggled (operates by matching its ID)
     */
    public void toggleEnabled(ChannelInfo channel) {
        synchronized (channelsLock) {
            List<ChannelInfo> channels = ensureChannelSnapshot();
            ChannelInfo target = findChannelById(channels, channel.getId()).orElse(null);
            if (target == null) {
                return;
            }
            target.setEnabled(!target.isEnabled());
            persistSnapshot(channels);
        }
        LOGGER.log(
                Level.INFO,
                "Toggle channel switch: id={0}, enabled={1}",
                new Object[] {channel.getId(), channel.isEnabled()});
    }

    /**
     * Save the current channel state in memory.
     *
     * <p>Note: The existing UI will modify ChannelInfo directly before calling save().
     */
    public void save() {
        synchronized (channelsLock) {
            persistSnapshot(ensureChannelSnapshot());
        }
    }

    /**
     * Set whether a channel has the "share context in group" feature enabled.
     *
     * <p>When enabled, conversation contexts from multiple users in the same group will be merged and processed;
     * When disabled, even in the same group, conversations from different users are isolated.
     *
     * @param channel Target channel object
     * @param share   true means enable sharing context in group, false means disable
     */
    public void setShareSessionInGroup(ChannelInfo channel, boolean share) {
        synchronized (channelsLock) {
            List<ChannelInfo> channels = ensureChannelSnapshot();
            ChannelInfo target = findChannelById(channels, channel.getId()).orElse(null);
            if (target == null) {
                return;
            }
            target.setShareSessionInGroup(share);
            persistSnapshot(channels);
        }
    }

    /**
     * Read and execute migration/completion once on demand. Uniformly reads from ConfigManager.
     *
     * <p>This method validates data integrity on every access, but only saves to disk when changes are found.
     */
    private List<ChannelInfo> ensureChannelSnapshot() {
        synchronized (channelsLock) {
            List<ChannelInfo> channels = configManager.getChannels();
            boolean changed = false;

            if (channels.isEmpty()) {
                LOGGER.info("Channel list is empty, initializing built-in channels");
                channels.addAll(buildBuiltinChannels());
                changed = true;
            }

            if (ensureBuiltinChannels(channels)) {
                LOGGER.info("Missing built-in channels detected, automatically filled");
                changed = true;
            }

            if (this.afterLoad(channels)) {
                LOGGER.info("Channel plugin configuration has been dynamically normalized");
                changed = true;
            }

            if (changed) {
                persistSnapshot(channels);
            }
            return channels;
        }
    }

    private List<ChannelInfo> buildBuiltinChannels() {
        List<ChannelInfo> builtin = new ArrayList<>();
        for (String[] channel : BUILTIN_CHANNELS) {
            builtin.add(
                    new ChannelInfo(
                            channel[0], channel[1], true, ChannelIds.CONSOLE.equals(channel[0])));
        }
        //        builtin.stream()
        //                .filter(item -> ChannelIds.WECOM.equals(item.id))
        //                .findFirst()
        //                .ifPresent(item -> item.shareSessionInGroup = true);
        return builtin;
    }

    private boolean ensureBuiltinChannels(List<ChannelInfo> channels) {
        boolean changed = false;
        for (String[] builtin : BUILTIN_CHANNELS) {
            boolean exists = channels.stream().anyMatch(item -> builtin[0].equals(item.getId()));
            if (!exists) {
                channels.add(new ChannelInfo(builtin[0], builtin[1], true, false));
                changed = true;
            }
        }
        return changed;
    }

    private Optional<ChannelInfo> findChannelById(List<ChannelInfo> channels, String channelId) {
        return channels.stream().filter(item -> item.getId().equals(channelId)).findFirst();
    }

    /**
     * Execute bridging synchronization before saving to disk to ensure pluginConfig is consistent with compatible fields.
     */
    private void persistSnapshot(List<ChannelInfo> channels) {
        this.beforeSave(channels);
        configManager.saveChannels(channels);
    }

    /**
     * Pre-save data hook: Execute configuration normalization for each ChannelInfo before saving channels.json.
     *
     * <p>According to the Schema defined by the corresponding plugin in the plugin registry, fill in missing default fields, or remove deprecated configuration items that are no longer supported,
     * ensuring that the JSON data format finally written to disk is up-to-date and safe.
     *
     * @param channels The channel list to be saved
     */
    public void beforeSave(List<ChannelInfo> channels) {
        for (ChannelInfo ch : channels) {
            if (ch.getPluginConfig() == null) {
                ch.setPluginConfig(new LinkedHashMap<>());
            }
            EmailclawPlugin plugin = pluginRegistry.getPluginInstance(ch.getId());
            if (plugin != null) {
                plugin.normalizeConfig(ch.getPluginConfig());
            }
        }
    }

    /**
     * Post-load data hook: Execute configuration normalization for each ChannelInfo after channels.json is loaded.
     *
     * <p>Mainly used after software upgrades to automatically upgrade and apply compatibility processing to data generated by older versions using the latest plugin Schema.
     *
     * @param channels The channel list just read and deserialized from disk
     * @return true means the configuration was automatically corrected during normalization and needs to trigger a silent save; false means the data does not need to be modified
     */
    public boolean afterLoad(List<ChannelInfo> channels) {
        boolean changed = false;
        for (ChannelInfo ch : channels) {
            if (ch.getPluginConfig() == null) {
                ch.setPluginConfig(new LinkedHashMap<>());
                changed = true;
            }
            EmailclawPlugin plugin = pluginRegistry.getPluginInstance(ch.getId());
            if (plugin != null) {
                if (plugin.normalizeConfig(ch.getPluginConfig())) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Get the plugin instance corresponding to the specified channel ID.
     *
     * @param channelId Channel ID
     * @return Plugin instance, returns null if not loaded
     */
    public EmailclawPlugin getPluginInstance(String channelId) {
        return pluginRegistry.getPluginInstance(channelId);
    }
}
