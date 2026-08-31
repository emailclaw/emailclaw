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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.ProviderStatus;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.ObjectMapper;

/**
 * Provider management service.
 *
 * <p>Responsibilities after refactoring:
 * <br>1) The business layer no longer holds Provider snapshots;
 * <br>2) All read and write operations are unified through ConfigManager (the only configuration source);
 * <br>3) UI hot refresh is driven by ConfigManager change events.
 */
public class ProviderService {

    private static final Logger LOGGER = Logger.getLogger(ProviderService.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long PROVIDER_TEST_TIMEOUT_SECONDS = 30L;

    private final Object providersLock = new Object();

    private final ConfigManager configManager;

    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    public ProviderService(AppContext repository) {
        this.configManager = repository.configManager();
        // Unified listening to ConfigManager's provider change events (including external file hot
        // loading and internal save).
        this.configManager.addChangeListener(
                ConfigManager.EVENT_PROVIDERS, this::notifyReloadListeners);
        LOGGER.info(
                "ProviderService initialized, currently loaded "
                        + configManager.getProviders().size()
                        + " model providers");
    }

    /**
     * Register Provider configuration hot update listener.
     */
    public void addReloadListener(Runnable listener) {
        if (listener != null) {
            reloadListeners.add(listener);
        }
    }

    public List<ProviderInfo> listProviders() {
        // Return a copy of the list but keep element references, compatible with the UI "call
        // save() after editing object" workflow.
        List<ProviderInfo> snapshot = new ArrayList<>(configManager.getProviders());
        snapshot.sort(Comparator.comparing(item -> item.getName()));
        return snapshot;
    }

    public Optional<ProviderInfo> getById(String id) {
        return configManager.getProviders().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst();
    }

    public void save() {
        synchronized (providersLock) {
            configManager.saveProviders(configManager.getProviders());
        }
        LOGGER.info("Successfully saved provider configuration snapshot");
    }

    public ProviderStatus status(ProviderInfo provider) {
        int modelCount = provider.getModels().size() + provider.getExtraModels().size();
        if ("emailclaw-local".equals(provider.getId())) {
            return ProviderStatus.UNAVAILABLE;
        }
        if (!isConfigured(provider)) {
            return ProviderStatus.NOT_READY_NOT_CONFIGURED;
        }
        if (modelCount == 0) {
            return ProviderStatus.NOT_READY_NO_MODELS;
        }
        return ProviderStatus.READY_WITH_MODELS;
    }

    /**
     * Determine if Provider has completed credential configuration (aligned with Emailclaw {@code getIsConfigured}).
     */
    public boolean isConfigured(ProviderInfo provider) {
        if ("emailclaw-local".equals(provider.getId())) {
            return true;
        }
        if (provider.isCustom()
                && provider.getBaseUrl() != null
                && !provider.getBaseUrl().isBlank()) {
            return true;
        }
        if (!provider.isRequireApiKey()) {
            return true;
        }
        if (provider.isOauthConnected()) {
            return true;
        }
        return provider.getApiKey() != null && !provider.getApiKey().isBlank();
    }

    /**
     * Determine if Provider is eligible as a global default LLM candidate (aligned with Emailclaw ModelsSection eligible logic).
     */
    public boolean isEligibleForDefaultLlm(ProviderInfo provider) {
        if (provider.allModels().isEmpty()) {
            return false;
        }
        if (!provider.isRequireApiKey()) {
            return provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank();
        }
        if (provider.isCustom()) {
            return provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank();
        }
        return provider.getApiKey() != null && !provider.getApiKey().isBlank();
    }

    public void addModel(ProviderInfo provider, String modelId, String name) {
        synchronized (providersLock) {
            List<ProviderInfo> providers = configManager.getProviders();
            ProviderInfo target = findProviderById(providers, provider.getId()).orElse(null);
            if (target == null) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to add model, provider not found: {0}",
                        provider.getId());
                return;
            }
            if (target.getExtraModels().stream().anyMatch(item -> item.getId().equals(modelId))) {
                LOGGER.log(
                        Level.FINE,
                        "Ignoring duplicate custom model: provider={0}, model={1}",
                        new Object[] {target.getId(), modelId});
                return;
            }
            if (target.getModels().stream().anyMatch(item -> item.getId().equals(modelId))) {
                LOGGER.log(
                        Level.FINE,
                        "Ignoring duplicate built-in model addition: provider={0}, model={1}",
                        new Object[] {target.getId(), modelId});
                return;
            }
            target.getExtraModels().add(new ModelInfo(modelId, name, false));
            configManager.saveProviders(providers);
            LOGGER.log(
                    Level.INFO,
                    "Successfully added custom model: provider={0}, model={1}",
                    new Object[] {target.getId(), modelId});
        }
    }

    public void removeCustomModel(ProviderInfo provider, ModelInfo model) {
        synchronized (providersLock) {
            List<ProviderInfo> providers = configManager.getProviders();
            ProviderInfo target = findProviderById(providers, provider.getId()).orElse(null);
            if (target == null) {
                return;
            }
            boolean changed =
                    target.getExtraModels().removeIf(item -> item.getId().equals(model.getId()));
            if (changed) {
                configManager.saveProviders(providers);
                LOGGER.log(
                        Level.INFO,
                        "Successfully deleted custom model: provider={0}, model={1}",
                        new Object[] {target.getId(), model.getId()});
            }
        }
    }

    public void upsertCustomProvider(ProviderInfo provider) {
        if (provider.getId() == null || provider.getId().isBlank()) {
            LOGGER.warning("Ignoring custom provider write with empty providerId");
            return;
        }
        synchronized (providersLock) {
            List<ProviderInfo> providers = configManager.getProviders();
            providers.removeIf(item -> item.getId().equals(provider.getId()));
            ProviderInfo customProvider = MAPPER.convertValue(provider, ProviderInfo.class);
            customProvider.setCustom(true);
            providers.add(customProvider);
            configManager.saveProviders(providers);
            LOGGER.log(
                    Level.INFO,
                    "Successfully wrote custom provider: provider={0}",
                    provider.getId());
        }
    }

    public void removeCustomProvider(ProviderInfo provider) {
        if (!provider.isCustom()) {
            return;
        }
        synchronized (providersLock) {
            List<ProviderInfo> providers = configManager.getProviders();
            boolean changed = providers.removeIf(item -> item.getId().equals(provider.getId()));
            if (changed) {
                configManager.saveProviders(providers);
                LOGGER.log(
                        Level.INFO,
                        "Successfully deleted custom provider: provider={0}",
                        provider.getId());
            }
        }
    }

    public TestResult testModel(ProviderInfo provider, ModelInfo model) {
        LOGGER.log(
                Level.INFO,
                "Testing model connectivity: provider={0}, model={1}",
                new Object[] {provider.getId(), model.getId()});
        try {
            OpenAIChatModel testModel = buildChatModel(provider, model);
            ChatResponse response =
                    testModel.stream(
                                    List.of(
                                            Msg.builder()
                                                    .textContent("Reply with exactly: pong")
                                                    .build()),
                                    new ArrayList<>(),
                                    null)
                            .blockLast(Duration.ofSeconds(PROVIDER_TEST_TIMEOUT_SECONDS));
            if (response != null
                    && response.getContent() != null
                    && !response.getContent().isEmpty()) {
                LOGGER.log(
                        Level.INFO,
                        "Model connectivity test successful: provider={0}, model={1}",
                        new Object[] {provider.getId(), model.getId()});
                return new TestResult(true, "Connection successful");
            }
            return new TestResult(false, "No response from model");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Model connectivity test failed", e);
            return new TestResult(false, "Connection failed: " + e.getMessage());
        }
    }

    public TestResult testProviderConfig(ProviderInfo provider, String baseUrl, String apiKey) {
        try {
            ProviderInfo cloned = MAPPER.convertValue(provider, ProviderInfo.class);
            cloned.setBaseUrl(baseUrl);
            cloned.setApiKey(apiKey);
            ModelInfo sample = cloned.allModels().stream().findFirst().orElse(null);
            if (sample == null) {
                return new TestResult(false, "No models available");
            }
            return testModel(cloned, sample);
        } catch (Exception e) {
            return new TestResult(false, "Connection failed: " + e.getMessage());
        }
    }

    private OpenAIChatModel buildChatModel(ProviderInfo provider, ModelInfo model) {
        Map<String, String> headers = ProviderRequestOptions.headersFor(provider);
        Map<String, Object> bodyParams = ProviderRequestOptions.bodyParamsFor(provider, model);
        GenerateOptions.Builder optionsBuilder =
                GenerateOptions.builder()
                        .temperature(0.0)
                        .maxTokens(
                                model != null && model.getMaxTokens() != null
                                        ? model.getMaxTokens()
                                        : 32);
        if (!headers.isEmpty()) {
            optionsBuilder.additionalHeaders(headers);
        }
        if (!bodyParams.isEmpty()) {
            optionsBuilder.additionalBodyParams(bodyParams);
        }
        GenerateOptions options = optionsBuilder.build();
        OpenAIChatModel.Builder builder =
                OpenAIChatModel.builder()
                        .apiKey(ProviderRequestOptions.apiKeyFor(provider))
                        .baseUrl(provider.getBaseUrl())
                        .modelName(model == null ? "" : model.getId())
                        .stream(false)
                        .generateOptions(options);

        String pid = provider.getId() == null ? "" : provider.getId().toLowerCase();
        if (pid.contains("deepseek")) {
            builder.formatter(
                            new io.agentscope.extensions.model.openai.compat.deepseek
                                    .DeepSeekFormatter())
                    .nativeStructuredOutput(false)
                    .nativeStructuredOutputWithTools(false);
        } else if (pid.contains("glm") || pid.contains("zhipu")) {
            builder.formatter(new io.agentscope.extensions.model.openai.compat.glm.GLMFormatter())
                    .nativeStructuredOutput(false)
                    .nativeStructuredOutputWithTools(false);
        } else if (pid.contains("kimi") || pid.contains("moonshot")) {
            builder.formatter(new io.agentscope.extensions.model.openai.compat.kimi.KimiFormatter())
                    .nativeStructuredOutput(false)
                    .nativeStructuredOutputWithTools(false);
        }

        return builder.build();
    }

    public void markOAuthConnected(String providerId, String accessToken) {
        synchronized (providersLock) {
            ProviderInfo provider =
                    findProviderById(configManager.getProviders(), providerId).orElse(null);
            if (provider == null) {
                throw new IllegalArgumentException("Provider not found: " + providerId);
            }
            provider.setApiKey(accessToken == null ? "" : accessToken);
            provider.setOauthConnected(
                    provider.isSupportsOAuth() && !provider.getApiKey().isBlank());
            configManager.saveProviders(configManager.getProviders());
            LOGGER.log(
                    Level.INFO,
                    "Updating OAuth connection status: provider={0}, connected={1}",
                    new Object[] {providerId, provider.isOauthConnected()});
        }
    }

    public record TestResult(boolean success, String message) {}

    private Optional<ProviderInfo> findProviderById(
            List<ProviderInfo> providers, String providerId) {
        return providers.stream().filter(item -> item.getId().equals(providerId)).findFirst();
    }

    private void notifyReloadListeners() {
        for (Runnable listener : reloadListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING, "Provider hot reload listener callback failed (ignored)", e);
            }
        }
    }

    // Return default ModelInfo
    public ModelInfo getDefaultModel() {
        ModelInfo defaultModel = null;
        LOGGER.fine("Returning default available model...");
        // Prefer opencode (free and built-in big-pickle model), otherwise take the first available
        // provider
        ProviderInfo defaultProvider =
                this.listProviders().stream()
                        .filter(p -> ProviderStatus.READY_WITH_MODELS == this.status(p))
                        .filter(p -> "opencode".equals(p.getId()))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        this.listProviders().stream()
                                                .filter(
                                                        p ->
                                                                ProviderStatus.READY_WITH_MODELS
                                                                        == this.status(p))
                                                .findFirst()
                                                .orElse(null));
        if (defaultProvider == null) {
            defaultProvider = this.listProviders().stream().findFirst().orElse(null);
        }
        if (defaultProvider != null && !defaultProvider.allModels().isEmpty()) {
            // opencode prefers big-pickle, other providers take the first model
            if ("opencode".equals(defaultProvider.getId())) {
                defaultModel =
                        defaultProvider.allModels().stream()
                                .filter(m -> "big-pickle".equals(m.getId()))
                                .findFirst()
                                .orElse(defaultProvider.allModels().getFirst());
                LOGGER.log(
                        Level.FINE,
                        "Automatically selected opencode provider, preferring big-pickle model:"
                                + " {0}",
                        defaultModel.getName());
            } else {
                defaultModel = defaultProvider.allModels().getFirst();
                LOGGER.log(
                        Level.FINE,
                        "Automatically selected available provider: {0}, default model: {1}",
                        new Object[] {defaultProvider.getName(), defaultModel.getName()});
            }
            // Need to set defaultModel.providerId to return
            defaultModel.setProviderId(defaultProvider.getId());
        } else {
            LOGGER.warning("There are no available providers or models in the system");
        }

        return defaultModel;
    }
}
