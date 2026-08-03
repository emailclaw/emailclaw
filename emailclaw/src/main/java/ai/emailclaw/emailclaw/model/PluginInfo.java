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

import java.util.Map;

/**
 * Plugin information model.
 *
 * <p>Compatible with directory-type plugins (containing plugin.json) and JAR-type Channel plugins.
 */
public record PluginInfo(
        String id,
        String pluginId,
        String name,
        String description,
        String version,
        String author,
        String type,
        String installUrl,
        String size,
        String sha256,
        String installedVersion,
        String frontendEntry,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        boolean installed,
        boolean upgradeAvailable,
        boolean enabled,
        boolean loaded) {
    public PluginInfo {
        if (id == null) id = "";
        if (pluginId == null) pluginId = "";
        if (name == null) name = "";
        if (description == null) description = "";
        if (version == null) version = "";
        if (author == null) author = "";
        if (type == null) type = "";
        if (installUrl == null) installUrl = "";
        if (size == null) size = "";
        if (sha256 == null) sha256 = "";
        if (installedVersion == null) installedVersion = "";
        if (frontendEntry == null) frontendEntry = "";
    }
}
