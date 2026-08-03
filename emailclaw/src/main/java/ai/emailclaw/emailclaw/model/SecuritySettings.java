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
 * Security settings root object (corresponds to the {@code security} section in Emailclaw config.json).
 */
public class SecuritySettings {
    private ToolGuardSettings toolGuard = new ToolGuardSettings();
    private FileGuardSettings fileGuard = new FileGuardSettings();
    private SkillScannerSettings skillScanner = new SkillScannerSettings();

    /** List of IPs allowed to access the API without authentication. */
    private List<String> allowNoAuthHosts = new ArrayList<>(List.of("127.0.0.1", "::1"));

    /**
     * Gets the Tool Guard settings.
     *
     * @return Tool Guard settings
     */
    public ToolGuardSettings getToolGuard() {
        return toolGuard;
    }

    /**
     * Sets the Tool Guard settings.
     *
     * @param toolGuard Tool Guard settings
     */
    public void setToolGuard(ToolGuardSettings toolGuard) {
        this.toolGuard = toolGuard;
    }

    /**
     * Gets the File Guard settings.
     *
     * @return File Guard settings
     */
    public FileGuardSettings getFileGuard() {
        return fileGuard;
    }

    /**
     * Sets the File Guard settings.
     *
     * @param fileGuard File Guard settings
     */
    public void setFileGuard(FileGuardSettings fileGuard) {
        this.fileGuard = fileGuard;
    }

    /**
     * Gets the Skill Scanner settings.
     *
     * @return Skill Scanner settings
     */
    public SkillScannerSettings getSkillScanner() {
        return skillScanner;
    }

    /**
     * Sets the Skill Scanner settings.
     *
     * @param skillScanner Skill Scanner settings
     */
    public void setSkillScanner(SkillScannerSettings skillScanner) {
        this.skillScanner = skillScanner;
    }

    /**
     * Gets the list of IPs allowed to access the API without authentication.
     *
     * @return Unauthenticated host list
     */
    public List<String> getAllowNoAuthHosts() {
        return allowNoAuthHosts;
    }

    /**
     * Sets the list of IPs allowed to access the API without authentication.
     *
     * @param allowNoAuthHosts Unauthenticated host list
     */
    public void setAllowNoAuthHosts(List<String> allowNoAuthHosts) {
        this.allowNoAuthHosts = allowNoAuthHosts;
    }
}
