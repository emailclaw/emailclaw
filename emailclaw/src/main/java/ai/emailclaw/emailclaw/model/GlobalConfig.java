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

import java.util.ArrayList;
import java.util.List;

/**
 * Global configuration object (global-config.json).
 *
 * <p>Usage instructions:
 * <br>1) {@code currentAgentId}: currently selected Agent (used for restoring the current Agent in the upper left corner of the main interface);
 * <br>2) {@code country}: country/region code (reserved for future internationalization strategy);
 * <br>3) {@code language}: language code (reserved for future multi-language switching).
 */
public class GlobalConfig {
    private String appVersion = "26.8.5";

    /** Currently selected Agent ID. If empty, it means not explicitly selected yet. */
    private String currentAgentId = "";

    /** Currently selected Project ID. If empty, it means not explicitly selected yet. */
    private String currentProjectId = "";

    /** Country/region code, default CN. */
    private String country = "CN";

    /** Language code, default en. */
    private String language = "en";

    /** Additional skill pool paths; the default skill pool remains fixed to the skills pool in the Emailclaw home directory. */
    private List<String> skillPoolPaths = new ArrayList<>();

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String _appVersion) {
        appVersion = _appVersion;
    }

    /**
     * Get currently selected Agent ID.
     *
     * @return currently selected Agent ID
     */
    public String getCurrentAgentId() {
        return currentAgentId;
    }

    /**
     * Set currently selected Agent ID.
     *
     * @param currentAgentId currently selected Agent ID
     */
    public void setCurrentAgentId(String currentAgentId) {
        this.currentAgentId = currentAgentId;
    }

    /**
     * Get currently selected Project ID.
     *
     * @return currently selected Project ID
     */
    public String getCurrentProjectId() {
        return currentProjectId;
    }

    /**
     * Set currently selected Project ID.
     *
     * @param currentProjectId currently selected Project ID
     */
    public void setCurrentProjectId(String currentProjectId) {
        this.currentProjectId = currentProjectId;
    }

    /**
     * Get country/region code.
     *
     * @return country/region code
     */
    public String getCountry() {
        return country;
    }

    /**
     * Set country/region code.
     *
     * @param country country/region code
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * Get language code.
     *
     * @return language code
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Set language code.
     *
     * @param language language code
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Get additional skill pool paths list.
     *
     * @return additional skill pool paths list
     */
    public List<String> getSkillPoolPaths() {
        return skillPoolPaths;
    }

    /**
     * Set additional skill pool paths list.
     *
     * @param skillPoolPaths additional skill pool paths list
     */
    public void setSkillPoolPaths(List<String> skillPoolPaths) {
        this.skillPoolPaths = skillPoolPaths;
    }
}
