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
 * File Guard configuration (sensitive path access interception).
 */
public class FileGuardSettings {
    private boolean enabled = true;
    private List<String> paths = new ArrayList<>();

    /**
     * Determine whether File Guard interception is enabled.
     *
     * @return whether it is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set whether File Guard interception is enabled.
     *
     * @param enabled whether it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Get the list of protected sensitive paths.
     *
     * @return list of sensitive paths
     */
    public List<String> getPaths() {
        return paths;
    }

    /**
     * Set the list of protected sensitive paths.
     *
     * @param paths list of sensitive paths
     */
    public void setPaths(List<String> paths) {
        this.paths = paths;
    }
}
