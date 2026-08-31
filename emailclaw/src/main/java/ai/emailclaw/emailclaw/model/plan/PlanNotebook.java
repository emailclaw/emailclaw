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
package ai.emailclaw.emailclaw.model.plan;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Plan notebook - provides a simplified plan context summary for model inference.
 *
 * <p>Unlike a complete {@link Plan}, PlanNotebook only retains the lightweight information needed to generate HintBlock:
 * The set of completed subtask IDs and the context to focus on currently, avoiding serializing the entire plan structure.
 */
public class PlanNotebook {
    /** Associated plan ID. */
    private String planId = "";

    /** User's original goal. */
    private String planGoal = "";

    /** Overall progress description (e.g. "3/5 subtasks completed"). */
    private String overallProgress = "";

    /** Current hint text - will be directly injected as HintBlock. */
    private String currentHint = "";

    /** Previously generated hint text (used for cache comparison to avoid duplicate injection). */
    private String lastHint = "";

    /** Set of completed subtask IDs. */
    private Set<String> completedTaskIds = new LinkedHashSet<>();

    /** Set of failed subtask IDs. */
    private Set<String> failedTaskIds = new LinkedHashSet<>();

    public PlanNotebook() {}

    /**
     * Get the associated plan ID.
     *
     * @return Associated plan ID
     */
    public String getPlanId() {
        return planId;
    }

    /**
     * Set the associated plan ID.
     *
     * @param planId Associated plan ID
     */
    public void setPlanId(String planId) {
        this.planId = planId;
    }

    /**
     * Get the user's original goal.
     *
     * @return User's original goal
     */
    public String getPlanGoal() {
        return planGoal;
    }

    /**
     * Set the user's original goal.
     *
     * @param planGoal User's original goal
     */
    public void setPlanGoal(String planGoal) {
        this.planGoal = planGoal;
    }

    /**
     * Get the overall progress description.
     *
     * @return Overall progress description
     */
    public String getOverallProgress() {
        return overallProgress;
    }

    /**
     * Set the overall progress description.
     *
     * @param overallProgress Overall progress description
     */
    public void setOverallProgress(String overallProgress) {
        this.overallProgress = overallProgress;
    }

    /**
     * Get the current hint text.
     *
     * @return Current hint text
     */
    public String getCurrentHint() {
        return currentHint;
    }

    /**
     * Set the current hint text.
     *
     * @param currentHint Current hint text
     */
    public void setCurrentHint(String currentHint) {
        this.currentHint = currentHint;
    }

    /**
     * Get the previously generated hint text.
     *
     * @return Previously generated hint text
     */
    public String getLastHint() {
        return lastHint;
    }

    /**
     * Set the previously generated hint text.
     *
     * @param lastHint Previously generated hint text
     */
    public void setLastHint(String lastHint) {
        this.lastHint = lastHint;
    }

    /**
     * Get the set of completed subtask IDs.
     *
     * @return Set of completed subtask IDs
     */
    public Set<String> getCompletedTaskIds() {
        return completedTaskIds;
    }

    /**
     * Set the set of completed subtask IDs.
     *
     * @param completedTaskIds Set of completed subtask IDs
     */
    public void setCompletedTaskIds(Set<String> completedTaskIds) {
        this.completedTaskIds = completedTaskIds;
    }

    /**
     * Get the set of failed subtask IDs.
     *
     * @return Set of failed subtask IDs
     */
    public Set<String> getFailedTaskIds() {
        return failedTaskIds;
    }

    /**
     * Set the set of failed subtask IDs.
     *
     * @param failedTaskIds Set of failed subtask IDs
     */
    public void setFailedTaskIds(Set<String> failedTaskIds) {
        this.failedTaskIds = failedTaskIds;
    }

    /**
     * Determine whether the hint content has changed (compared to {@link #lastHint}).
     */
    public boolean isHintChanged() {
        return !currentHint.equals(lastHint);
    }

    /**
     * Synchronize {@link #lastHint} to {@link #currentHint}.
     */
    public void markHintSynced() {
        this.lastHint = this.currentHint;
    }
}
