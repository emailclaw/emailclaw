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
 * Agent runtime configuration object.
 *
 * <p>Carries parameters such as ReAct, context management, rate limiting retry, and tool security.
 */
public class AgentConfiguration {
    // ReAct Agent tab
    /** Agent reply language. */
    private String agentLanguage = "English";

    /** User timezone. */
    private String userTimezone = "America/New_York";

    /** Maximum reasoning iterations. */
    private int maxIterations = 100;

    /** Shell executable path. */
    private String shellExecutable = "bash";

    /** Shell command timeout in seconds. */
    private int shellCommandTimeout = 60;

    /** Total wall-clock timeout in seconds for one full task execution (multi-turn + tools). */
    private int taskExecutionTimeoutSeconds = 1800;

    /** Auto-continue only on plain text. */
    private boolean autoContinueOnTextOnly = false;

    /** Auto-generate session title. */
    private boolean autoGenerateSessionTitle = true;

    /** Auto-generate session title timeout in seconds. */
    private double autoGenerateSessionTitleTimeoutSeconds = 30.0;

    /** Configuration profile name. */
    private String profileName = "";

    /** Console locale. */
    private String consoleLocale = "en";

    // Context Management
    /** Context management backend type. */
    private String contextManagerBackend = "light";

    /** Memory management backend type. */
    private String memoryManagerBackend = "remelight";

    /** Maximum context length. */
    private int maxContextLength = 131072;

    /** Whether it is plan mode. */
    private boolean planMode = false;

    // LLM Auto Retry
    /** Whether LLM auto-retry is enabled. */
    private boolean llmRetryEnabled = true;

    /** LLM maximum retries. */
    private int llmMaxRetries = 3;

    /** LLM backoff base. */
    private double llmBackoffBase = 2.0;

    /** LLM backoff cap. */
    private double llmBackoffCap = 30.0;

    // LLM Rate Limiter
    /** LLM maximum concurrency. */
    private int llmMaxConcurrent = 4;

    /** LLM maximum requests per minute (0 means unlimited). */
    private int llmMaxQpm = 0;

    /** LLM rate limit pause in seconds. */
    private double llmRateLimitPause = 60.0;

    /** LLM rate limit jitter in seconds. */
    private double llmRateLimitJitter = 5.0;

    /** LLM acquire timeout in seconds. */
    private int llmAcquireTimeout = 30;

    // Tool Execution Security
    /** Tool execution permission mode. */
    private String permissionMode = "bypass"; // bypass, default, accept_edits, explore, dont_ask

    // Coding Mode (mirrors Emailclaw coding_mode: enabled + project_dir)
    /** Whether coding mode is enabled. */
    private boolean codingModeEnabled = false;

    public AgentConfiguration() {}

    /** Get Agent reply language. */
    public String getAgentLanguage() {
        return agentLanguage;
    }

    /** Set Agent reply language. */
    public void setAgentLanguage(String agentLanguage) {
        this.agentLanguage = agentLanguage;
    }

    /** Get user timezone. */
    public String getUserTimezone() {
        return userTimezone;
    }

    /** Set user timezone. */
    public void setUserTimezone(String userTimezone) {
        this.userTimezone = userTimezone;
    }

    /** Get maximum reasoning iterations. */
    public int getMaxIterations() {
        return maxIterations;
    }

    /** Set maximum reasoning iterations. */
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    /** Get shell executable path. */
    public String getShellExecutable() {
        return shellExecutable;
    }

    /** Set shell executable path. */
    public void setShellExecutable(String shellExecutable) {
        this.shellExecutable = shellExecutable;
    }

    /** Get shell command timeout in seconds. */
    public int getShellCommandTimeout() {
        return shellCommandTimeout;
    }

    /** Set shell command timeout in seconds. */
    public void setShellCommandTimeout(int shellCommandTimeout) {
        this.shellCommandTimeout = shellCommandTimeout;
    }

    /** Get total wall-clock timeout in seconds for one full task execution. */
    public int getTaskExecutionTimeoutSeconds() {
        return taskExecutionTimeoutSeconds;
    }

    /** Set total wall-clock timeout in seconds for one full task execution. */
    public void setTaskExecutionTimeoutSeconds(int taskExecutionTimeoutSeconds) {
        this.taskExecutionTimeoutSeconds = taskExecutionTimeoutSeconds;
    }

    /** Returns the effective task execution timeout with a 60-second lower bound applied. */
    public int effectiveTaskExecutionTimeoutSeconds() {
        return Math.max(60, taskExecutionTimeoutSeconds);
    }

    /** Check auto-continue only on plain text. */
    public boolean isAutoContinueOnTextOnly() {
        return autoContinueOnTextOnly;
    }

    /** Set auto-continue only on plain text. */
    public void setAutoContinueOnTextOnly(boolean autoContinueOnTextOnly) {
        this.autoContinueOnTextOnly = autoContinueOnTextOnly;
    }

    /** Check auto-generate session title. */
    public boolean isAutoGenerateSessionTitle() {
        return autoGenerateSessionTitle;
    }

    /** Set auto-generate session title. */
    public void setAutoGenerateSessionTitle(boolean autoGenerateSessionTitle) {
        this.autoGenerateSessionTitle = autoGenerateSessionTitle;
    }

    /** Get auto-generate session title timeout in seconds. */
    public double getAutoGenerateSessionTitleTimeoutSeconds() {
        return autoGenerateSessionTitleTimeoutSeconds;
    }

    /** Set auto-generate session title timeout in seconds. */
    public void setAutoGenerateSessionTitleTimeoutSeconds(
            double autoGenerateSessionTitleTimeoutSeconds) {
        this.autoGenerateSessionTitleTimeoutSeconds = autoGenerateSessionTitleTimeoutSeconds;
    }

    /** Get configuration profile name. */
    public String getProfileName() {
        return profileName;
    }

    /** Set configuration profile name. */
    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    /** Get console locale. */
    public String getConsoleLocale() {
        return consoleLocale;
    }

    /** Set console locale. */
    public void setConsoleLocale(String consoleLocale) {
        this.consoleLocale = consoleLocale;
    }

    /** Get context management backend type. */
    public String getContextManagerBackend() {
        return contextManagerBackend;
    }

    /** Set context management backend type. */
    public void setContextManagerBackend(String contextManagerBackend) {
        this.contextManagerBackend = contextManagerBackend;
    }

    /** Get memory management backend type. */
    public String getMemoryManagerBackend() {
        return memoryManagerBackend;
    }

    /** Set memory management backend type. */
    public void setMemoryManagerBackend(String memoryManagerBackend) {
        this.memoryManagerBackend = memoryManagerBackend;
    }

    /** Get maximum context length. */
    public int getMaxContextLength() {
        return maxContextLength;
    }

    /** Set maximum context length. */
    public void setMaxContextLength(int maxContextLength) {
        this.maxContextLength = maxContextLength;
    }

    /** Check whether it is plan mode. */
    public boolean isPlanMode() {
        return planMode;
    }

    /** Set whether it is plan mode. */
    public void setPlanMode(boolean planMode) {
        this.planMode = planMode;
    }

    /** Check whether LLM auto-retry is enabled. */
    public boolean isLlmRetryEnabled() {
        return llmRetryEnabled;
    }

    /** Set whether LLM auto-retry is enabled. */
    public void setLlmRetryEnabled(boolean llmRetryEnabled) {
        this.llmRetryEnabled = llmRetryEnabled;
    }

    /** Get LLM maximum retries. */
    public int getLlmMaxRetries() {
        return llmMaxRetries;
    }

    /** Set LLM maximum retries. */
    public void setLlmMaxRetries(int llmMaxRetries) {
        this.llmMaxRetries = llmMaxRetries;
    }

    /** Get LLM backoff base. */
    public double getLlmBackoffBase() {
        return llmBackoffBase;
    }

    /** Set LLM backoff base. */
    public void setLlmBackoffBase(double llmBackoffBase) {
        this.llmBackoffBase = llmBackoffBase;
    }

    /** Get LLM backoff cap. */
    public double getLlmBackoffCap() {
        return llmBackoffCap;
    }

    /** Set LLM backoff cap. */
    public void setLlmBackoffCap(double llmBackoffCap) {
        this.llmBackoffCap = llmBackoffCap;
    }

    /** Get LLM maximum concurrency. */
    public int getLlmMaxConcurrent() {
        return llmMaxConcurrent;
    }

    /** Set LLM maximum concurrency. */
    public void setLlmMaxConcurrent(int llmMaxConcurrent) {
        this.llmMaxConcurrent = llmMaxConcurrent;
    }

    /** Get LLM maximum requests per minute. */
    public int getLlmMaxQpm() {
        return llmMaxQpm;
    }

    /** Set LLM maximum requests per minute. */
    public void setLlmMaxQpm(int llmMaxQpm) {
        this.llmMaxQpm = llmMaxQpm;
    }

    /** Get LLM rate limit pause in seconds. */
    public double getLlmRateLimitPause() {
        return llmRateLimitPause;
    }

    /** Set LLM rate limit pause in seconds. */
    public void setLlmRateLimitPause(double llmRateLimitPause) {
        this.llmRateLimitPause = llmRateLimitPause;
    }

    /** Get LLM rate limit jitter in seconds. */
    public double getLlmRateLimitJitter() {
        return llmRateLimitJitter;
    }

    /** Set LLM rate limit jitter in seconds. */
    public void setLlmRateLimitJitter(double llmRateLimitJitter) {
        this.llmRateLimitJitter = llmRateLimitJitter;
    }

    /** Get LLM acquire timeout in seconds. */
    public int getLlmAcquireTimeout() {
        return llmAcquireTimeout;
    }

    /** Set LLM acquire timeout in seconds. */
    public void setLlmAcquireTimeout(int llmAcquireTimeout) {
        this.llmAcquireTimeout = llmAcquireTimeout;
    }

    /** Get tool execution permission mode. */
    public String getPermissionMode() {
        return permissionMode;
    }

    /** Set tool execution permission mode. */
    public void setPermissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
    }

    /** Check whether coding mode is enabled. */
    public boolean isCodingModeEnabled() {
        return codingModeEnabled;
    }

    /** Set whether coding mode is enabled. */
    public void setCodingModeEnabled(boolean codingModeEnabled) {
        this.codingModeEnabled = codingModeEnabled;
    }
}
