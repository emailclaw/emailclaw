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
package ai.emailclaw.emailclaw.model.security;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive result of ToolGuard security detection. Includes whether execution is approved, the highest threat level, and the list of all detected threats.
 */
public class ToolGuardResult {
    /** Whether execution of this tool call is approved. */
    private boolean approved;

    /** The detected highest threat level. NONE if there is no threat. */
    private GuardSeverity maxSeverity;

    /** The list of all detected findings. */
    private List<GuardFinding> findings;

    /** Error message during detection (if any). */
    private String errorMessage;

    /** Constructor: Creates a new detection result. */
    public ToolGuardResult() {
        this.approved = true; // Default allow execution
        this.maxSeverity = GuardSeverity.NONE;
        this.findings = new ArrayList<>();
    }

    /**
     * Checks if execution of this tool call is approved.
     *
     * @return Whether approved
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * Sets whether execution of this tool call is approved.
     *
     * @param approved Whether approved
     */
    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    /**
     * Gets the detected highest threat level.
     *
     * @return The highest threat level
     */
    public GuardSeverity getMaxSeverity() {
        return maxSeverity;
    }

    /**
     * Sets the detected highest threat level.
     *
     * @param maxSeverity The highest threat level
     */
    public void setMaxSeverity(GuardSeverity maxSeverity) {
        this.maxSeverity = maxSeverity;
    }

    /**
     * Gets the list of all detected findings.
     *
     * @return The list of findings
     */
    public List<GuardFinding> getFindings() {
        return findings;
    }

    /**
     * Sets the list of all detected findings.
     *
     * @param findings The list of findings
     */
    public void setFindings(List<GuardFinding> findings) {
        this.findings = findings;
    }

    /**
     * Gets the error message during detection.
     *
     * @return Error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message during detection.
     *
     * @param errorMessage Error message
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Adds a threat finding, and automatically updates the highest threat level.
     *
     * @param finding Threat finding
     */
    public void addFinding(GuardFinding finding) {
        if (finding != null) {
            this.findings.add(finding);
            if (finding.getSeverity().isMoreSevereThan(this.maxSeverity)) {
                this.maxSeverity = finding.getSeverity();
            }
        }
    }

    /**
     * Checks if user approval is required. Based on the highest threat level.
     *
     * @param permissionMode PermissionMode string (bypass, default, accept_edits, explore, dont_ask)
     * @return True if approval is required
     */
    public boolean requiresApproval(String permissionMode) {
        if ("bypass".equalsIgnoreCase(permissionMode)) {
            return false;
        } else if ("default".equalsIgnoreCase(permissionMode)
                || "dont_ask".equalsIgnoreCase(permissionMode)) {
            return this.maxSeverity.requiresApproval();
        } else {
            // accept_edits, explore are handled by PermissionEngine, here default to requiring
            // approval
            return hasThreats();
        }
    }

    /**
     * Checks if there are any threats detected.
     *
     * @return True if threats exist
     */
    public boolean hasThreats() {
        return !findings.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
                "ToolGuardResult{approved=%s, maxSeverity=%s, findingsCount=%d}",
                approved, maxSeverity.getDisplayName(), findings.size());
    }
}
