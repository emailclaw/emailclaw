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
 * Provider configuration object.
 *
 * <p>Contains API access parameters, available models, and custom model list.
 */
public class ProviderInfo {
    private String id = "";
    private String name = "";
    private String baseUrl = "";
    private String apiKey = "";
    private String chatModel = "OpenAIChatModel";
    private boolean local = false; // Whether it is a local provider (local model)
    private boolean custom = false; // Whether it is a user-defined provider
    private boolean freezeUrl = false;
    private boolean requireApiKey = true;
    private boolean supportModelDiscovery = false;
    private boolean supportConnectionCheck = true;
    private boolean supportsOAuth = false;
    private boolean oauthConnected = false;
    private boolean freeTier = false; // Whether it is a Free Tier provider (synced from Emailclaw)
    private String apiKeyPrefix = "";
    private String authMode = "API Key";
    private String oauthAuthorizeUrl = "";
    private String oauthTokenUrl = "";
    private String oauthRedirectUri = "";
    private String oauthScope = "";
    private Map<String, String> customHeaders = new LinkedHashMap<>();
    private List<ModelInfo> models = new ArrayList<>();
    private List<ModelInfo> extraModels = new ArrayList<>();
    private Map<String, Object> generateKwargs = new LinkedHashMap<>();
    private Map<String, Object> meta = new LinkedHashMap<>();

    /** Whether to enable native synergy between structured output and tool calls. Default true. Set to false when the Provider prioritizes response_format causing tool calls to be skipped. */
    private boolean nativeStructuredOutputWithTools = true;

    /** Brand grouping key (e.g., "aliyun", "zhipu", "minimax", "kimi", "siliconflow", "volcengine"). Empty string means no grouping. */
    private String providerGroup = "";

    /** Brand grouping display name (e.g., "Aliyun", "Zhipu"). */
    private String providerGroupName = "";

    /** Variant identifier within the group (e.g., "coding_plan_cn", "token_plan", "open_platform_intl"). */
    private String providerVariant = "";

    public List<ModelInfo> allModels() {
        List<ModelInfo> all = new ArrayList<>(models);
        all.addAll(extraModels);
        return all;
    }

    /**
     * Get Provider unique identifier.
     *
     * @return Provider ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set Provider unique identifier.
     *
     * @param id Provider ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get Provider display name.
     *
     * @return Provider display name
     */
    public String getName() {
        return name;
    }

    /**
     * Set Provider display name.
     *
     * @param name Provider display name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get API base URL.
     *
     * @return API base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Set API base URL.
     *
     * @param baseUrl API base URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Get API Key.
     *
     * @return API Key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Set API Key.
     *
     * @param apiKey API Key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Get chat model class name.
     *
     * @return Chat model class name
     */
    public String getChatModel() {
        return chatModel;
    }

    /**
     * Set chat model class name.
     *
     * @param chatModel Chat model class name
     */
    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Determine whether it is a local provider.
     *
     * @return Whether it is a local provider
     */
    public boolean isLocal() {
        return local;
    }

    /**
     * Set whether it is a local provider.
     *
     * @param local Whether it is a local provider
     */
    public void setLocal(boolean local) {
        this.local = local;
    }

    /**
     * Determine whether it is a user-defined provider.
     *
     * @return Whether it is a user-defined provider
     */
    public boolean isCustom() {
        return custom;
    }

    /**
     * Set whether it is a user-defined provider.
     *
     * @param custom Whether it is a user-defined provider
     */
    public void setCustom(boolean custom) {
        this.custom = custom;
    }

    /**
     * Determine whether URL is frozen (non-editable).
     *
     * @return Whether URL is frozen
     */
    public boolean isFreezeUrl() {
        return freezeUrl;
    }

    /**
     * Set whether URL is frozen.
     *
     * @param freezeUrl Whether URL is frozen
     */
    public void setFreezeUrl(boolean freezeUrl) {
        this.freezeUrl = freezeUrl;
    }

    /**
     * Determine whether API Key is required.
     *
     * @return Whether API Key is required
     */
    public boolean isRequireApiKey() {
        return requireApiKey;
    }

    /**
     * Set whether API Key is required.
     *
     * @param requireApiKey Whether API Key is required
     */
    public void setRequireApiKey(boolean requireApiKey) {
        this.requireApiKey = requireApiKey;
    }

    /**
     * Determine whether model discovery needs to be supported.
     *
     * @return Whether model discovery is supported
     */
    public boolean isSupportModelDiscovery() {
        return supportModelDiscovery;
    }

    /**
     * Set whether model discovery needs to be supported.
     *
     * @param supportModelDiscovery Whether model discovery is supported
     */
    public void setSupportModelDiscovery(boolean supportModelDiscovery) {
        this.supportModelDiscovery = supportModelDiscovery;
    }

    /**
     * Determine whether connection check needs to be supported.
     *
     * @return Whether connection check is supported
     */
    public boolean isSupportConnectionCheck() {
        return supportConnectionCheck;
    }

    /**
     * Set whether connection check needs to be supported.
     *
     * @param supportConnectionCheck Whether connection check is supported
     */
    public void setSupportConnectionCheck(boolean supportConnectionCheck) {
        this.supportConnectionCheck = supportConnectionCheck;
    }

    /**
     * Determine whether OAuth needs to be supported.
     *
     * @return Whether OAuth is supported
     */
    public boolean isSupportsOAuth() {
        return supportsOAuth;
    }

    /**
     * Set whether OAuth needs to be supported.
     *
     * @param supportsOAuth Whether OAuth is supported
     */
    public void setSupportsOAuth(boolean supportsOAuth) {
        this.supportsOAuth = supportsOAuth;
    }

    /**
     * Determine whether OAuth is connected.
     *
     * @return Whether OAuth is connected
     */
    public boolean isOauthConnected() {
        return oauthConnected;
    }

    /**
     * Set whether OAuth is connected.
     *
     * @param oauthConnected Whether OAuth is connected
     */
    public void setOauthConnected(boolean oauthConnected) {
        this.oauthConnected = oauthConnected;
    }

    /**
     * Determine whether it is a Free Tier provider.
     *
     * @return Whether it is a Free Tier provider
     */
    public boolean isFreeTier() {
        return freeTier;
    }

    /**
     * Set whether it is a Free Tier provider.
     *
     * @param freeTier Whether it is a Free Tier provider
     */
    public void setFreeTier(boolean freeTier) {
        this.freeTier = freeTier;
    }

    /**
     * Get API Key prefix (for desensitized display).
     *
     * @return API Key prefix
     */
    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    /**
     * Set API Key prefix.
     *
     * @param apiKeyPrefix API Key prefix
     */
    public void setApiKeyPrefix(String apiKeyPrefix) {
        this.apiKeyPrefix = apiKeyPrefix;
    }

    /**
     * Get authentication mode.
     *
     * @return Authentication mode
     */
    public String getAuthMode() {
        return authMode;
    }

    /**
     * Set authentication mode.
     *
     * @param authMode Authentication mode
     */
    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    /**
     * Get OAuth authorize URL.
     *
     * @return OAuth authorize URL
     */
    public String getOauthAuthorizeUrl() {
        return oauthAuthorizeUrl;
    }

    /**
     * Set OAuth authorize URL.
     *
     * @param oauthAuthorizeUrl OAuth authorize URL
     */
    public void setOauthAuthorizeUrl(String oauthAuthorizeUrl) {
        this.oauthAuthorizeUrl = oauthAuthorizeUrl;
    }

    /**
     * Get the OAuth Token URL.
     *
     * @return OAuth Token URL
     */
    public String getOauthTokenUrl() {
        return oauthTokenUrl;
    }

    /**
     * Set the OAuth Token URL.
     *
     * @param oauthTokenUrl OAuth Token URL
     */
    public void setOauthTokenUrl(String oauthTokenUrl) {
        this.oauthTokenUrl = oauthTokenUrl;
    }

    /**
     * Get the OAuth redirect URI.
     *
     * @return OAuth redirect URI
     */
    public String getOauthRedirectUri() {
        return oauthRedirectUri;
    }

    /**
     * Set the OAuth redirect URI.
     *
     * @param oauthRedirectUri OAuth redirect URI
     */
    public void setOauthRedirectUri(String oauthRedirectUri) {
        this.oauthRedirectUri = oauthRedirectUri;
    }

    /**
     * Get the OAuth scope.
     *
     * @return OAuth scope
     */
    public String getOauthScope() {
        return oauthScope;
    }

    /**
     * Set the OAuth scope.
     *
     * @param oauthScope OAuth scope
     */
    public void setOauthScope(String oauthScope) {
        this.oauthScope = oauthScope;
    }

    /**
     * Get the custom request headers.
     *
     * @return Custom request headers
     */
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    /**
     * Set the custom request headers.
     *
     * @param customHeaders Custom request headers
     */
    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }

    /**
     * Get the model list.
     *
     * @return Model list
     */
    public List<ModelInfo> getModels() {
        return models;
    }

    /**
     * Set the model list.
     *
     * @param models Model list
     */
    public void setModels(List<ModelInfo> models) {
        this.models = models;
    }

    /**
     * Get the extra model list.
     *
     * @return Extra model list
     */
    public List<ModelInfo> getExtraModels() {
        return extraModels;
    }

    /**
     * Set the extra model list.
     *
     * @param extraModels Extra model list
     */
    public void setExtraModels(List<ModelInfo> extraModels) {
        this.extraModels = extraModels;
    }

    /**
     * Get the generation parameters.
     *
     * @return Generation parameters
     */
    public Map<String, Object> getGenerateKwargs() {
        return generateKwargs;
    }

    /**
     * Set the generation parameters.
     *
     * @param generateKwargs Generation parameters
     */
    public void setGenerateKwargs(Map<String, Object> generateKwargs) {
        this.generateKwargs = generateKwargs;
    }

    /**
     * Get the additional metadata.
     *
     * @return Additional metadata
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Set the additional metadata.
     *
     * @param meta Additional metadata
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    /**
     * Determine whether to enable native synergy between structured output and tool calls.
     *
     * @return Whether native synergy is enabled
     */
    public boolean isNativeStructuredOutputWithTools() {
        return nativeStructuredOutputWithTools;
    }

    /**
     * Set whether to enable native synergy between structured output and tool calls.
     *
     * @param nativeStructuredOutputWithTools Whether native synergy is enabled
     */
    public void setNativeStructuredOutputWithTools(boolean nativeStructuredOutputWithTools) {
        this.nativeStructuredOutputWithTools = nativeStructuredOutputWithTools;
    }

    /**
     * Get the brand grouping key.
     *
     * @return Brand grouping key
     */
    public String getProviderGroup() {
        return providerGroup;
    }

    /**
     * Set the brand grouping key.
     *
     * @param providerGroup Brand grouping key
     */
    public void setProviderGroup(String providerGroup) {
        this.providerGroup = providerGroup;
    }

    /**
     * Get the brand grouping display name.
     *
     * @return Brand grouping display name
     */
    public String getProviderGroupName() {
        return providerGroupName;
    }

    /**
     * Set the brand grouping display name.
     *
     * @param providerGroupName Brand grouping display name
     */
    public void setProviderGroupName(String providerGroupName) {
        this.providerGroupName = providerGroupName;
    }

    /**
     * Get the variant identifier within the group.
     *
     * @return Variant identifier within the group
     */
    public String getProviderVariant() {
        return providerVariant;
    }

    /**
     * Set the variant identifier within the group.
     *
     * @param providerVariant Variant identifier within the group
     */
    public void setProviderVariant(String providerVariant) {
        this.providerVariant = providerVariant;
    }
}
