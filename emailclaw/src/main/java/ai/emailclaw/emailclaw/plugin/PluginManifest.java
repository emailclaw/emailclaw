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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Plugin manifest model (unified data structure).
 *
 * <p>Merges the former {@code PluginManifest} (deserialized from plugin.json inside JAR) and
 * {@code PluginInfo} (UI layer fields for installation status and official catalog).
 * Used in the following scenarios:
 * <ul>
 *   <li>Parsing plugin.json from local JAR / directory</li>
 *   <li>Official catalog entries fetched from CDN</li>
 *   <li>Displaying in PluginManagerView table</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginManifest {

    // ======================== Basic Metadata (plugin.json core fields) ========================

    /** Unique plugin identifier. */
    public String id = "";

    /**
     * Older versions of plugin.json might define pluginId separately.
     */
    @JsonProperty("plugin_id")
    public String pluginId = "";

    /** Display name (supports i18n mapped single string form). */
    public String name = "";

    /** Internationalized name dictionary. */
    @JsonProperty("name_i18n")
    public Map<String, String> nameI18n = Collections.emptyMap();

    /** Description information (supports i18n mapped single string form). */
    public String description = "";

    /** Internationalized description dictionary. */
    @JsonProperty("description_i18n")
    public Map<String, String> descriptionI18n = Collections.emptyMap();

    /** Plugin version (semantic version, e.g., "1.0.0"). */
    public String version = "";

    /** Plugin author or team name. */
    public String author = "";

    /** Plugin type enumeration (tool / channel / bundle / provider etc.). */
    @JsonProperty("type")
    public PluginType pluginType = PluginType.GENERAL;

    /**
     * Fully qualified name of the backend main class entry (for reflective loading).
     */
    @JsonProperty("entry_point")
    public String entryPoint;

    /** Plugin dependencies list. */
    public List<String> dependencies;

    /** Minimum host version required. */
    @JsonProperty("min_version")
    public String minVersion;

    /** General metadata extension fields. */
    public Map<String, Object> meta;

    // ======================== Official Catalog / Installation Management Fields
    // ========================

    /** Official download URL. */
    @JsonProperty("install_url")
    public String installUrl = "";

    /** Installation package size description (e.g., "2.3 MB"). */
    public String size = "";

    /** Installation package SHA-256 checksum. */
    public String sha256 = "";

    /**
     * Frontend resource relative entry path (if any).
     */
    @JsonProperty("frontend_entry")
    public String frontendEntry = "";

    // ======================== Runtime Status Fields (non-serialized) ========================

    /** Whether installed locally. */
    public transient boolean installed = false;

    /**
     * Locally installed version number.
     */
    public transient String installedVersion = "";

    /** Whether an upgrade is available. */
    public transient boolean upgradeAvailable = false;

    /** Whether enabled. */
    public transient boolean enabled = true;

    /**
     * Whether it has been loaded into the runtime.
     */
    public transient boolean loaded = false;

    /** Default constructor. */
    public PluginManifest() {}

    /**
     * Gets the plugin type string representation, compatible with legacy string-based access.
     *
     * @return Plugin type string (e.g., "tool", "channel")
     */
    public String getTypeValue() {
        return pluginType != null ? pluginType.getValue() : "general";
    }
}
