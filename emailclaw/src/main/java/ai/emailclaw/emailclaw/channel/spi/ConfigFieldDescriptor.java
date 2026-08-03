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

/**
 * Describes a single configuration item of a plugin, used for automatically generating UI forms and configuration validation.
 *
 * <p>Each {@link ChannelPlugin} declares the list of configuration items it needs via {@link ChannelPlugin#configSchema()},
 * the framework will use this description to:
 * <ul>
 *   <li>Automatically render corresponding types of input controls on the Channel settings page</li>
 *   <li>Perform non-empty validation on required fields</li>
 *   <li>Persist configuration in the plugin's independent namespace</li>
 * </ul>
 *
 * @param key          Configuration key name, e.g. "clientId"
 * @param label        UI display label, e.g. "Client ID"
 * @param type         Field type, determines whether UI renders as text box, password box, switch, etc.
 * @param required     Whether it is a required field
 * @param defaultValue Default value (can be null to indicate no default)
 * @param description  Help text, e.g. "AppKey obtained from DingTalk Open Platform"
 * @param group        UI grouping label, e.g. "Connection Settings", "Security Settings"
 */
public record ConfigFieldDescriptor(
        String key,
        String label,
        FieldType type,
        boolean required,
        Object defaultValue,
        String description,
        String group) {
    /**
     * Configuration field type enum, drives UI control selection.
     */
    public enum FieldType {
        /** Normal text input */
        TEXT,
        /** Password/Secret input (masked display in UI) */
        PASSWORD,
        /** Integer input */
        INTEGER,
        /** Boolean switch */
        BOOLEAN,
        /** Dropdown selection */
        SELECT,
        /** Email address input (with format validation) */
        EMAIL,
        /** Text list (e.g. whitelist) */
        TEXT_LIST
    }
}
