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
package ai.emailclaw.emailclaw.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Channel configuration object.
 *
 * <p>Configuration is divided into two layers:
 * <ul>
 *   <li><b>General fields</b> (id, name, enabled, etc.): Shared by all channels, read directly by the framework.</li>
 *   <li><b>pluginConfig</b>: Exclusive configuration for each plugin (such as DingTalk clientId, Email imapHost, etc.),
 *       please read and write through the exclusive configuration utility classes of each plugin.</li>
 * </ul>
 *
 * <p>If the old {@code channels.json} puts plugin fields at the root level, it will be automatically merged into {@code pluginConfig} during deserialization via {@link #setLegacyProperty}.
 */
public class ChannelInfo {

    private static final Set<String> ROOT_JSON_FIELDS =
            Set.of(
                    "id",
                    "name",
                    "builtIn",
                    "enabled",
                    "botPrefix",
                    "shareSessionInGroup",
                    "pluginConfig");

    /** Channel unique identifier. */
    private String id = "";

    /** Channel display name. */
    private String name = "";

    /** Whether it is a built-in channel. */
    private boolean builtIn = true;

    /** Whether it is enabled. */
    private boolean enabled = false;

    /** Bot prefix. */
    private String botPrefix = "";

    /** Whether to share the session in the group. */
    private boolean shareSessionInGroup = true;

    /**
     * Plugin exclusive configuration container (the only source of plugin parameters during persistence).
     */
    private Map<String, Object> pluginConfig = new LinkedHashMap<>();

    /** Get channel unique identifier. */
    public String getId() {
        return id;
    }

    /** Set channel unique identifier. */
    public void setId(String id) {
        this.id = id;
    }

    /** Get channel display name. */
    public String getName() {
        return name;
    }

    /** Set channel display name. */
    public void setName(String name) {
        this.name = name;
    }

    /** Determine whether it is a built-in channel. */
    public boolean isBuiltIn() {
        return builtIn;
    }

    /** Set whether it is a built-in channel. */
    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    /** Determine whether it is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Set whether it is enabled. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Get bot prefix. */
    public String getBotPrefix() {
        return botPrefix;
    }

    /** Set bot prefix. */
    public void setBotPrefix(String botPrefix) {
        this.botPrefix = botPrefix;
    }

    /** Determine whether to share session in group. */
    public boolean isShareSessionInGroup() {
        return shareSessionInGroup;
    }

    /** Set whether to share session in group. */
    public void setShareSessionInGroup(boolean shareSessionInGroup) {
        this.shareSessionInGroup = shareSessionInGroup;
    }

    /** Get plugin exclusive configuration container. */
    public Map<String, Object> getPluginConfig() {
        return pluginConfig;
    }

    /** Set plugin exclusive configuration container. */
    public void setPluginConfig(Map<String, Object> pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public ChannelInfo() {}

    public ChannelInfo(String id, String name, boolean builtIn, boolean enabled) {
        this.id = id;
        this.name = name;
        this.builtIn = builtIn;
        this.enabled = enabled;
    }

    /**
     * Compatible with old channels.json: root-level plugin fields are written to {@link #pluginConfig}.
     */
    @JsonAnySetter
    public void setLegacyProperty(String key, Object value) {
        if (ROOT_JSON_FIELDS.contains(key)) {
            return;
        }
        if (pluginConfig == null) {
            pluginConfig = new LinkedHashMap<>();
        }
        pluginConfig.put(key, value);
    }
}
