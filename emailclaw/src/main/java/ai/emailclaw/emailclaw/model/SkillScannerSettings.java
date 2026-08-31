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
 * Skill Scanner settings (static security scan before skill installation).
 */
public class SkillScannerSettings {
    /** Mode: block / warn / off. */
    private String mode = "warn";

    private int timeout = 30;
    private List<SkillWhitelistEntry> whitelist = new ArrayList<>();
    private List<BlockedSkillRecord> blockedHistory = new ArrayList<>();

    /**
     * Gets the scan mode.
     *
     * @return Scan mode (block / warn / off)
     */
    public String getMode() {
        return mode;
    }

    /**
     * Sets the scan mode.
     *
     * @param mode Scan mode (block / warn / off)
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * Gets the scan timeout (in seconds).
     *
     * @return Timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the scan timeout (in seconds).
     *
     * @param timeout Timeout
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets the whitelist entries.
     *
     * @return Whitelist entries
     */
    public List<SkillWhitelistEntry> getWhitelist() {
        return whitelist;
    }

    /**
     * Sets the whitelist entries.
     *
     * @param whitelist Whitelist entries
     */
    public void setWhitelist(List<SkillWhitelistEntry> whitelist) {
        this.whitelist = whitelist;
    }

    /**
     * Gets the blocked skill history records.
     *
     * @return Blocked skill history records
     */
    public List<BlockedSkillRecord> getBlockedHistory() {
        return blockedHistory;
    }

    /**
     * Sets the blocked skill history records.
     *
     * @param blockedHistory Blocked skill history records
     */
    public void setBlockedHistory(List<BlockedSkillRecord> blockedHistory) {
        this.blockedHistory = blockedHistory;
    }
}
