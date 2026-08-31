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

import ai.emailclaw.emailclaw.util.UuidUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single security threat discovered by ToolGuard. Each GuardFinding represents a specific security issue detected in a tool call.
 *
 * <p>Contains the threat category, severity, description, and remediation recommendations.
 */
public class GuardFinding {

    /**
     * Unique identifier, used for audit tracking.
     */
    private String id;

    /**
     * Threat category.
     */
    private GuardThreatCategory category;

    /**
     * Threat severity.
     */
    private GuardSeverity severity;

    /**
     * Detailed description of the threat, helping the user understand the risk.
     */
    private String description;

    /**
     * List of recommendations to resolve or mitigate the threat.
     */
    private List<String> recommendations;

    /**
     * Rule ID or name that triggered this finding.
     */
    private String ruleId;

    /**
     * Timestamp of the discovery (ISO 8601 format).
     */
    private String timestamp;

    /**
     * Constructor: creates a new threat finding.
     */
    public GuardFinding() {
        this.id = UuidUtils.randomUUIDv7().toString();
        this.recommendations = new ArrayList<>();
        this.timestamp = Instant.now().toString();
    }

    /**
     * Convenience constructor: creates a new threat finding, specifying the category, severity, and description.
     *
     * @param category Threat category
     * @param severity Threat severity
     * @param description Threat description
     */
    public GuardFinding(GuardThreatCategory category, GuardSeverity severity, String description) {
        this();
        this.category = category;
        this.severity = severity;
        this.description = description;
    }

    /**
     * Get the unique identifier.
     *
     * @return Unique identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Set the unique identifier.
     *
     * @param id Unique identifier
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the threat category.
     *
     * @return Threat category
     */
    public GuardThreatCategory getCategory() {
        return category;
    }

    /**
     * Set the threat category.
     *
     * @param category Threat category
     */
    public void setCategory(GuardThreatCategory category) {
        this.category = category;
    }

    /**
     * Get the threat severity.
     *
     * @return Threat severity
     */
    public GuardSeverity getSeverity() {
        return severity;
    }

    /**
     * Set the threat severity.
     *
     * @param severity Threat severity
     */
    public void setSeverity(GuardSeverity severity) {
        this.severity = severity;
    }

    /**
     * Get the threat description.
     *
     * @return Threat description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the threat description.
     *
     * @param description Threat description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the recommendation list.
     *
     * @return Recommendation list
     */
    public List<String> getRecommendations() {
        return recommendations;
    }

    /**
     * Set the recommendation list.
     *
     * @param recommendations Recommendation list
     */
    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Get the rule ID or name that triggered this finding.
     *
     * @return Rule ID or name that triggered this finding
     */
    public String getRuleId() {
        return ruleId;
    }

    /**
     * Set the rule ID or name that triggered this finding.
     *
     * @param ruleId Rule ID or name that triggered this finding
     */
    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    /**
     * Get the discovery timestamp (ISO 8601 format).
     *
     * @return Discovery timestamp
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * Set the discovery timestamp (ISO 8601 format).
     *
     * @param timestamp Discovery timestamp
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Add a recommendation.
     *
     * @param recommendation Recommendation text
     */
    public void addRecommendation(String recommendation) {
        if (recommendation != null && !recommendation.trim().isEmpty()) {
            this.recommendations.add(recommendation);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s: %s", severity.getDisplayName(), category.getDisplayName(), description);
    }
}
