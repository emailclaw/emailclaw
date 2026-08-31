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
 * ACP agent configuration object.
 */
public class AcpAgentInfo {
    /** ACP agent unique key. */
    private String key = "";

    /** Whether it is a built-in agent. */
    private boolean builtIn = true;

    /** Whether it is enabled. */
    private boolean enabled = true;

    /** Start command. */
    private String command = "";

    /** Start arguments. */
    private String args = "";

    /** Environment variable configuration string. */
    private String envVars = "";

    /** Whether it is trusted. */
    private boolean trusted = true;

    /** Tool parsing mode. */
    private String toolParseMode = "call_title";

    /** Standard I/O buffer limit (bytes). */
    private long stdioBufferLimit = 52428800L;

    /** List of externally broadcast commands. */
    private List<String> advertisedCommands = new ArrayList<>();

    /** Tool parameter mapping. */
    private Map<String, Object> toolParams = new LinkedHashMap<>();

    /** Agent meta information mapping. */
    private Map<String, Object> agentMeta = new LinkedHashMap<>();

    /** Model meta information mapping. */
    private Map<String, Object> modelMeta = new LinkedHashMap<>();

    /** File link list. */
    private List<String> fileLinks = new ArrayList<>();

    /** Last error message. */
    private String lastError = "";

    public AcpAgentInfo() {}

    public AcpAgentInfo(String key, boolean builtIn, boolean enabled, String command, String args) {
        this.key = key;
        this.builtIn = builtIn;
        this.enabled = enabled;
        this.command = command;
        this.args = args;
    }

    /** Get ACP agent unique key. */
    public String getKey() {
        return key;
    }

    /** Set ACP agent unique key. */
    public void setKey(String key) {
        this.key = key;
    }

    /** Determine whether it is a built-in agent. */
    public boolean isBuiltIn() {
        return builtIn;
    }

    /** Set whether it is a built-in agent. */
    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    /** Determine whether it is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Set whether it is enabled. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Get start command. */
    public String getCommand() {
        return command;
    }

    /** Set start command. */
    public void setCommand(String command) {
        this.command = command;
    }

    /** Get start arguments. */
    public String getArgs() {
        return args;
    }

    /** Set start arguments. */
    public void setArgs(String args) {
        this.args = args;
    }

    /** Get environment variable configuration string. */
    public String getEnvVars() {
        return envVars;
    }

    /** Set environment variable configuration string. */
    public void setEnvVars(String envVars) {
        this.envVars = envVars;
    }

    /** Determine whether it is trusted. */
    public boolean isTrusted() {
        return trusted;
    }

    /** Set whether it is trusted. */
    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    /** Get tool parsing mode. */
    public String getToolParseMode() {
        return toolParseMode;
    }

    /** Set tool parsing mode. */
    public void setToolParseMode(String toolParseMode) {
        this.toolParseMode = toolParseMode;
    }

    /** Get standard I/O buffer limit. */
    public long getStdioBufferLimit() {
        return stdioBufferLimit;
    }

    /** Set standard I/O buffer limit. */
    public void setStdioBufferLimit(long stdioBufferLimit) {
        this.stdioBufferLimit = stdioBufferLimit;
    }

    /** Get list of externally broadcast commands. */
    public List<String> getAdvertisedCommands() {
        return advertisedCommands;
    }

    /** Set list of externally broadcast commands. */
    public void setAdvertisedCommands(List<String> advertisedCommands) {
        this.advertisedCommands = advertisedCommands;
    }

    /** Get tool parameter mapping. */
    public Map<String, Object> getToolParams() {
        return toolParams;
    }

    /** Set tool parameter mapping. */
    public void setToolParams(Map<String, Object> toolParams) {
        this.toolParams = toolParams;
    }

    /** Get Agent meta information mapping. */
    public Map<String, Object> getAgentMeta() {
        return agentMeta;
    }

    /** Set Agent meta information mapping. */
    public void setAgentMeta(Map<String, Object> agentMeta) {
        this.agentMeta = agentMeta;
    }

    /** Get model meta information mapping. */
    public Map<String, Object> getModelMeta() {
        return modelMeta;
    }

    /** Set model meta information mapping. */
    public void setModelMeta(Map<String, Object> modelMeta) {
        this.modelMeta = modelMeta;
    }

    /** Get file link list. */
    public List<String> getFileLinks() {
        return fileLinks;
    }

    /** Set file link list. */
    public void setFileLinks(List<String> fileLinks) {
        this.fileLinks = fileLinks;
    }

    /** Get last error message. */
    public String getLastError() {
        return lastError;
    }

    /** Set last error message. */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
