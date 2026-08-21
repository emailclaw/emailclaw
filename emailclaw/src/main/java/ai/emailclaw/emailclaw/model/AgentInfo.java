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
 * Agent basic info object.
 *
 * <p>Records Agent identity, model binding, workspace, and skill list.
 */
public class AgentInfo {
    /** Agent unique identifier. */
    private String id = "";

    /** Agent display name. */
    private String name = "";

    /** Agent functional description. */
    private String description = "";

    /** Agent workspace directory path. */
    private String workspacePath = "";

    /** Bound provider identifier. */
    private String providerId = "kilo";

    /** Bound model identifier. */
    private String modelId = "stepfun/step-3.7-flash:free";

    /** Fallback provider identifier. */
    private String fallbackProviderId = "opencode";

    /** Fallback model identifier. */
    private String fallbackModelId = "big-pickle";

    /** Maximum retries. */
    private int maxRetries = 3;

    /** Whether to enable this Agent. */
    private boolean enabled = true;

    /** List of skill names associated with the Agent. */
    private List<String> skillNames = new ArrayList<>();

    /** Get Agent unique identifier. */
    public String getId() {
        return id;
    }

    /** Set Agent unique identifier. */
    public void setId(String id) {
        this.id = id;
    }

    /** Get Agent display name. */
    public String getName() {
        return name;
    }

    /** Set Agent display name. */
    public void setName(String name) {
        this.name = name;
    }

    /** Get Agent functional description. */
    public String getDescription() {
        return description;
    }

    /** Set Agent functional description. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Get Agent workspace directory path. */
    public String getWorkspacePath() {
        return workspacePath;
    }

    /** Set Agent workspace directory path. */
    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    /** Get bound provider identifier. */
    public String getProviderId() {
        return providerId;
    }

    /** Set bound provider identifier. */
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    /** Get bound model identifier. */
    public String getModelId() {
        return modelId;
    }

    /** Set bound model identifier. */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /** Get fallback provider identifier. */
    public String getFallbackProviderId() {
        return fallbackProviderId;
    }

    /** Set fallback provider identifier. */
    public void setFallbackProviderId(String fallbackProviderId) {
        this.fallbackProviderId = fallbackProviderId;
    }

    /** Get fallback model identifier. */
    public String getFallbackModelId() {
        return fallbackModelId;
    }

    /** Set fallback model identifier. */
    public void setFallbackModelId(String fallbackModelId) {
        this.fallbackModelId = fallbackModelId;
    }

    /** Get maximum retries. */
    public int getMaxRetries() {
        return maxRetries;
    }

    /** Set maximum retries. */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /** Check whether to enable this Agent. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Set whether to enable this Agent. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Get list of skill names associated with the Agent. */
    public List<String> getSkillNames() {
        return skillNames;
    }

    /** Set list of skill names associated with the Agent. */
    public void setSkillNames(List<String> skillNames) {
        this.skillNames = skillNames;
    }
}
