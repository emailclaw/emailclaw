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

/**
 * Skill market platform (Provider) meta information.
 *
 * <p>Corresponds to a single record returned by Emailclaw {@code GET /api/market/providers}.
 */
public class MarketProviderInfo {
    /** Platform unique key, e.g. clawhub / modelscope / aliyun. */
    private String key = "";

    /** Display name, e.g. ClawHub. */
    private String label = "";

    /** Whether it is available in the current environment. */
    private boolean available = true;

    /** Reason shown to the user when unavailable. */
    private String reason = "";

    /**
     * Get platform unique key.
     *
     * @return platform unique key
     */
    public String getKey() {
        return key;
    }

    /**
     * Set platform unique key.
     *
     * @param key platform unique key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Get platform display name.
     *
     * @return platform display name
     */
    public String getLabel() {
        return label;
    }

    /**
     * Set platform display name.
     *
     * @param label platform display name
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Determine whether it is available in the current environment.
     *
     * @return whether it is available
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Set whether it is available in the current environment.
     *
     * @param available whether it is available
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * Get the reason shown to the user when unavailable.
     *
     * @return reason when unavailable
     */
    public String getReason() {
        return reason;
    }

    /**
     * Set the reason shown to the user when unavailable.
     *
     * @param reason reason when unavailable
     */
    public void setReason(String reason) {
        this.reason = reason;
    }
}
