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

import java.util.List;

/**
 * MCP client configuration object.
 */
public record McpClientInfo(
        String key,
        String name,
        boolean builtIn,
        boolean enabled,
        String sourceType,
        String description,
        String command,
        List<String> args,
        String envJson,
        List<String> toolNames,
        boolean toolWhitelistEnabled,
        List<String> allowedToolNames,
        String authType,
        String oauthRedirectUri,
        String oauthScope) {
    public McpClientInfo {
        key = key != null ? key : "";
        name = name != null ? name : "";
        sourceType = sourceType != null ? sourceType : "Local";
        description = description != null ? description : "";
        command = command != null ? command : "";
        args = args != null ? List.copyOf(args) : List.of();
        envJson = envJson != null ? envJson : "";
        toolNames = toolNames != null ? List.copyOf(toolNames) : List.of();
        allowedToolNames = allowedToolNames != null ? List.copyOf(allowedToolNames) : List.of();
        authType = authType != null ? authType : "Local";
        oauthRedirectUri = oauthRedirectUri != null ? oauthRedirectUri : "";
        oauthScope = oauthScope != null ? oauthScope : "";
    }

    public McpClientInfo() {
        this("", "", false, false);
    }

    public McpClientInfo(String key, String name, boolean builtIn, boolean enabled) {
        this(
                key, name, builtIn, enabled, null, null, null, null, null, null, false, null, null,
                null, null);
    }

    public McpClientInfo withEnabled(boolean enabled) {
        return new McpClientInfo(
                key,
                name,
                builtIn,
                enabled,
                sourceType,
                description,
                command,
                args,
                envJson,
                toolNames,
                toolWhitelistEnabled,
                allowedToolNames,
                authType,
                oauthRedirectUri,
                oauthScope);
    }

    public McpClientInfo withToolWhitelistEnabled(boolean toolWhitelistEnabled) {
        return new McpClientInfo(
                key,
                name,
                builtIn,
                enabled,
                sourceType,
                description,
                command,
                args,
                envJson,
                toolNames,
                toolWhitelistEnabled,
                allowedToolNames,
                authType,
                oauthRedirectUri,
                oauthScope);
    }

    public McpClientInfo withAllowedToolNames(List<String> allowedToolNames) {
        return new McpClientInfo(
                key,
                name,
                builtIn,
                enabled,
                sourceType,
                description,
                command,
                args,
                envJson,
                toolNames,
                toolWhitelistEnabled,
                allowedToolNames,
                authType,
                oauthRedirectUri,
                oauthScope);
    }

    public McpClientInfo withToolNames(List<String> toolNames) {
        return new McpClientInfo(
                key,
                name,
                builtIn,
                enabled,
                sourceType,
                description,
                command,
                args,
                envJson,
                toolNames,
                toolWhitelistEnabled,
                allowedToolNames,
                authType,
                oauthRedirectUri,
                oauthScope);
    }
}
