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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill market search result item.
 *
 * <p>The field naming is consistent with the Emailclaw frontend {@code MarketResult} to facilitate the subsequent docking of the installation process.
 */
public class MarketResult {
    private String source = "";
    private String slug = "";
    private String name = "";
    private String description = "";
    private String sourceUrl = "";
    private String version = "";
    private String author = "";
    private String iconUrl = "";

    /** Extension statistics information of each platform (downloads, categories, etc.). */
    private Map<String, Object> stats = new LinkedHashMap<>();

    /**
     * Get the source platform of the result.
     *
     * @return Source platform of the result
     */
    public String getSource() {
        return source;
    }

    /**
     * Set the source platform of the result.
     *
     * @param source Source platform of the result
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Get the unique slug of the skill.
     *
     * @return Unique slug of the skill
     */
    public String getSlug() {
        return slug;
    }

    /**
     * Set the unique slug of the skill.
     *
     * @param slug Unique slug of the skill
     */
    public void setSlug(String slug) {
        this.slug = slug;
    }

    /**
     * Get the skill name.
     *
     * @return Skill name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the skill name.
     *
     * @param name Skill name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the skill description.
     *
     * @return Skill description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the skill description.
     *
     * @param description Skill description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the source URL.
     *
     * @return Source URL
     */
    public String getSourceUrl() {
        return sourceUrl;
    }

    /**
     * Set the source URL.
     *
     * @param sourceUrl Source URL
     */
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    /**
     * Get the version number.
     *
     * @return Version number
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set the version number.
     *
     * @param version Version number
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get the author.
     *
     * @return Author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Set the author.
     *
     * @param author Author
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Get the icon URL.
     *
     * @return Icon URL
     */
    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * Set the icon URL.
     *
     * @param iconUrl Icon URL
     */
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    /**
     * Get the extension statistics information of each platform (downloads, categories, etc.).
     *
     * @return Extension statistics information
     */
    public Map<String, Object> getStats() {
        return stats;
    }

    /**
     * Set the extension statistics information of each platform (downloads, categories, etc.).
     *
     * @param stats Extension statistics information
     */
    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }
}
