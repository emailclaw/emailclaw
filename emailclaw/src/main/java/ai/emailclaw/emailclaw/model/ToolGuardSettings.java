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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool Guard settings (tool call security scanning and approval).
 */
public class ToolGuardSettings {
    private boolean enabled = true;

    /** Tool names to guard; an empty list indicates using the built-in default set. */
    private List<String> guardedTools = new ArrayList<>();

    /** Tool names to always deny. */
    private List<String> deniedTools = new ArrayList<>();

    /** Shell evasion detection switches, keys match Emailclaw shell_evasion_guardian. */
    private Map<String, Boolean> shellEvasionChecks = new LinkedHashMap<>();

    /**
     * Checks if Tool Guard is enabled.
     *
     * @return Whether it is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether Tool Guard is enabled.
     *
     * @param enabled Whether it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the list of tool names to guard.
     *
     * @return List of guarded tool names
     */
    public List<String> getGuardedTools() {
        return guardedTools;
    }

    /**
     * Sets the list of tool names to guard.
     *
     * @param guardedTools List of guarded tool names
     */
    public void setGuardedTools(List<String> guardedTools) {
        this.guardedTools = guardedTools;
    }

    /**
     * Gets the list of tool names to always deny.
     *
     * @return List of denied tool names
     */
    public List<String> getDeniedTools() {
        return deniedTools;
    }

    /**
     * Sets the list of tool names to always deny.
     *
     * @param deniedTools List of denied tool names
     */
    public void setDeniedTools(List<String> deniedTools) {
        this.deniedTools = deniedTools;
    }

    /**
     * Gets the Shell evasion detection switches.
     *
     * @return Shell evasion detection switches
     */
    public Map<String, Boolean> getShellEvasionChecks() {
        return shellEvasionChecks;
    }

    /**
     * Sets the Shell evasion detection switches.
     *
     * @param shellEvasionChecks Shell evasion detection switches
     */
    public void setShellEvasionChecks(Map<String, Boolean> shellEvasionChecks) {
        this.shellEvasionChecks = shellEvasionChecks;
    }
}
