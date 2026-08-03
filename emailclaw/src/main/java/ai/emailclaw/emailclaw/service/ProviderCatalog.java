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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in Provider catalog.
 *
 * <p>30 built-in Providers consistent with Emailclaw {@code provider_manager.py}:
 * 3 local + 8 Free Tier + 19 Paid.
 */
public final class ProviderCatalog {
    private ProviderCatalog() {}

    /** Total number of built-in Providers (local 2 + Free Tier 8 + Paid 22). */
    public static final int BUILTIN_PROVIDER_COUNT = 32;

    public static List<ProviderInfo> builtins() {
        List<ProviderInfo> providers = new ArrayList<>();

        // ---- Local Providers (2) ----
        providers.add(discoverableLocal("ollama", "Ollama", "http://127.0.0.1:11434"));
        providers.add(discoverableLocal("lmstudio", "LM Studio", "http://localhost:1234/v1"));

        // ---- Free Tier (8) ----
        providers.add(
                freeTierProvider(
                        "openrouter",
                        "OpenRouter",
                        "https://openrouter.ai/api/v1",
                        "sk-or-v1-",
                        true,
                        true));
        providers.add(
                freeTierProvider(
                        "github-models",
                        "GitHub Models",
                        "https://models.inference.ai.azure.com",
                        "ghp_",
                        true,
                        false));
        providers.add(
                freeTierProvider(
                        "opencode", "OpenCode", "https://opencode.ai/zen/v1", "", false, false));
        providers.add(
                freeTierProvider(
                        "kilo", "Kilo Code", "https://api.kilo.ai/api/gateway", "", false, true));
        providers.add(
                freeTierProvider(
                        "gemini",
                        "Google Gemini",
                        "https://generativelanguage.googleapis.com",
                        "",
                        true,
                        true));
        providers.add(
                freeTierProvider(
                        "zhipu-cn",
                        "Zhipu (BigModel)",
                        "https://open.bigmodel.cn/api/paas/v4",
                        "",
                        true,
                        true));
        providers.add(
                freeTierProvider(
                        "siliconflow-cn",
                        "SiliconFlow (China)",
                        "https://api.siliconflow.cn/v1",
                        "sk-",
                        true,
                        true));
        providers.add(
                freeTierProvider(
                        "siliconflow-intl",
                        "SiliconFlow (International)",
                        "https://api.siliconflow.com/v1",
                        "sk-",
                        true,
                        true));

        // ---- Paid (22) ----
        providers.add(
                paidProvider(
                        "modelscope",
                        "ModelScope",
                        "https://api-inference.modelscope.cn/v1",
                        "ms",
                        true));
        // Aliyun group (5 variants)
        providers.add(
                paidProvider(
                        "dashscope",
                        "DashScope",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "sk",
                        false));
        providers.add(
                paidProvider(
                        "aliyun-codingplan",
                        "Aliyun Coding Plan (China)",
                        "https://coding.dashscope.aliyuncs.com/v1",
                        "sk-sp",
                        true));
        providers.add(
                paidProvider(
                        "aliyun-codingplan-intl",
                        "Aliyun Coding Plan (International)",
                        "https://coding-intl.dashscope.aliyuncs.com/v1",
                        "sk-sp",
                        true));
        providers.add(
                paidProvider(
                        "aliyun-tokenplan",
                        "Aliyun Token Plan",
                        "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
                        "sk-sp",
                        true));
        providers.add(
                paidProvider(
                        "aliyun-tokenplan-intl",
                        "Aliyun Token Plan (International)",
                        "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
                        "sk-sp",
                        true));
        providers.add(paidProvider("openai", "OpenAI", "https://api.openai.com/v1", "sk-", true));
        providers.add(paidProvider("azure-openai", "Azure OpenAI", "", "", false));
        providers.add(
                paidProvider(
                        "anthropic", "Anthropic", "https://api.anthropic.com", "sk-ant-", false));
        providers.add(
                paidProvider("deepseek", "DeepSeek", "https://api.deepseek.com", "sk-", true));
        // Kimi group (3 variants)
        providers.add(
                paidProvider("kimi-cn", "Kimi (China)", "https://api.moonshot.cn/v1", "", true));
        providers.add(
                paidProvider(
                        "kimi-intl",
                        "Kimi (International)",
                        "https://api.moonshot.ai/v1",
                        "",
                        true));
        providers.add(
                paidProvider(
                        "kimi-codingplan",
                        "Kimi Coding Plan",
                        "https://api.kimi.com/coding/v1",
                        "sk-kimi-",
                        true));
        // MiniMax group (2 variants)
        providers.add(
                paidProvider(
                        "minimax-cn",
                        "MiniMax (China)",
                        "https://api.minimaxi.com/anthropic",
                        "",
                        true));
        providers.add(
                paidProvider(
                        "minimax",
                        "MiniMax (International)",
                        "https://api.minimax.io/anthropic",
                        "",
                        true));
        // Zhipu group (4 variants)
        providers.add(
                paidProvider(
                        "zhipu-cn-codingplan",
                        "Zhipu Coding Plan (BigModel)",
                        "https://open.bigmodel.cn/api/coding/paas/v4",
                        "",
                        true));
        providers.add(
                paidProvider(
                        "zhipu-intl", "Zhipu (Z.AI)", "https://api.z.ai/api/paas/v4", "", true));
        providers.add(
                paidProvider(
                        "zhipu-intl-codingplan",
                        "Zhipu Coding Plan (Z.AI)",
                        "https://api.z.ai/api/coding/paas/v4",
                        "",
                        true));
        // Volcengine group (2 variants)
        providers.add(
                paidProvider(
                        "volcengine-cn",
                        "Volcano Engine",
                        "https://ark.cn-beijing.volces.com/api/v3",
                        "",
                        true));
        providers.add(
                paidProvider(
                        "volcengine-cn-codingplan",
                        "Volcano Engine Coding Plan",
                        "https://ark.cn-beijing.volces.com/api/coding/v3",
                        "",
                        true));
        providers.add(
                paidProvider(
                        "mimo-tokenplan",
                        "Xiaomi MiMo Token Plan",
                        "https://token-plan-cn.xiaomimimo.com/v1",
                        "",
                        true));

        // ---- Provider models and extended metadata ----
        addModelScopeModels(byId(providers, "modelscope"));
        configureDashScope(byId(providers, "dashscope"));
        addAliyunModels(byId(providers, "aliyun-codingplan"));
        addAliyunModels(byId(providers, "aliyun-codingplan-intl"));
        addAliyunTokenPlanModels(byId(providers, "aliyun-tokenplan"));
        addAliyunTokenPlanModels(byId(providers, "aliyun-tokenplan-intl"));
        configureOpenCode(byId(providers, "opencode"));
        addKiloModels(byId(providers, "kilo"));
        addGitHubModels(byId(providers, "github-models"));
        addOpenAiModels(byId(providers, "openai"));
        addAzureModels(byId(providers, "azure-openai"));
        configureAnthropic(byId(providers, "anthropic"));
        addGeminiModels(byId(providers, "gemini"));
        addDeepSeekModels(byId(providers, "deepseek"));
        addKimiModels(byId(providers, "kimi-cn"));
        addKimiModels(byId(providers, "kimi-intl"));
        addKimiCodingPlanModels(byId(providers, "kimi-codingplan"));
        configureMiniMax(byId(providers, "minimax-cn"));
        configureMiniMax(byId(providers, "minimax"));
        addZhipuModels(byId(providers, "zhipu-cn"));
        addZhipuModels(byId(providers, "zhipu-cn-codingplan"));
        addZhipuModels(byId(providers, "zhipu-intl"));
        addZhipuModels(byId(providers, "zhipu-intl-codingplan"));
        configureOpenRouter(byId(providers, "openrouter"));
        addVolcengineModels(byId(providers, "volcengine-cn"));
        addVolcengineCodingPlanModels(byId(providers, "volcengine-cn-codingplan"));
        addMimoTokenPlanModels(byId(providers, "mimo-tokenplan"));

        // Some Providers do not support connectivity checks without model configuration
        byId(providers, "aliyun-codingplan").setSupportConnectionCheck(false);
        byId(providers, "aliyun-codingplan-intl").setSupportConnectionCheck(false);
        byId(providers, "aliyun-tokenplan").setSupportConnectionCheck(false);
        byId(providers, "aliyun-tokenplan-intl").setSupportConnectionCheck(false);
        byId(providers, "kimi-codingplan").setSupportConnectionCheck(false);
        byId(providers, "zhipu-cn-codingplan").setSupportConnectionCheck(false);
        byId(providers, "zhipu-intl-codingplan").setSupportConnectionCheck(false);
        byId(providers, "volcengine-cn-codingplan").setSupportConnectionCheck(false);

        // ---- Brand grouping metadata ----
        annotateProviderGroups(providers);

        disableNativeStructuredOutputForThirdParty(providers);
        annotateBuiltInModelCapabilities(providers);
        return providers;
    }

    /** Create local Provider (no API Key required). */
    private static ProviderInfo localProvider(String id, String name, String baseUrl) {
        ProviderInfo p = new ProviderInfo();
        p.setId(id);
        p.setName(name);
        p.setBaseUrl(baseUrl);
        p.setLocal(true);
        p.setRequireApiKey(false);
        p.setFreezeUrl(true);
        return p;
    }

    /** Create local Provider supporting model discovery (Ollama / LM Studio). */
    private static ProviderInfo discoverableLocal(String id, String name, String baseUrl) {
        ProviderInfo p = localProvider(id, name, baseUrl);
        p.setSupportModelDiscovery(true);
        p.setGenerateKwargs(new LinkedHashMap<>());
        p.getGenerateKwargs().put("max_tokens", null);
        if ("lmstudio".equals(id)) {
            p.setFreezeUrl(false);
        }
        return p;
    }

    /** Create Free Tier remote Provider. */
    private static ProviderInfo freeTierProvider(
            String id,
            String name,
            String baseUrl,
            String apiKeyPrefix,
            boolean requireApiKey,
            boolean freezeUrl) {
        ProviderInfo p = remoteProvider(id, name, baseUrl, apiKeyPrefix, requireApiKey, freezeUrl);
        p.setFreeTier(true);
        p.getMeta().put("is_free_tier", true);
        return p;
    }

    /** Create Paid remote Provider. */
    private static ProviderInfo paidProvider(
            String id, String name, String baseUrl, String apiKeyPrefix, boolean freezeUrl) {
        return remoteProvider(id, name, baseUrl, apiKeyPrefix, true, freezeUrl);
    }

    /** Create generic remote Provider. */
    private static ProviderInfo remoteProvider(
            String id,
            String name,
            String baseUrl,
            String apiKeyPrefix,
            boolean requireApiKey,
            boolean freezeUrl) {
        ProviderInfo p = new ProviderInfo();
        p.setId(id);
        p.setName(name);
        p.setBaseUrl(baseUrl);
        p.setApiKeyPrefix(apiKeyPrefix);
        p.setRequireApiKey(requireApiKey);
        p.setFreezeUrl(freezeUrl);
        p.setLocal(false);
        return p;
    }

    private static ProviderInfo byId(List<ProviderInfo> providers, String id) {
        return providers.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    private static void addModelScopeModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("Qwen/Qwen3.5-122B-A10B", "Qwen3.5-122B-A10B", true));
        p.getModels().add(new ModelInfo("ZhipuAI/GLM-5", "GLM-5", true));
    }

    private static void configureDashScope(ProviderInfo p) {
        p.getModels().add(new ModelInfo("qwen3.7-max", "Qwen3.7 Max", true));
        p.getModels().add(new ModelInfo("qwen3.7-plus", "Qwen3.7 Plus", true));
        p.getModels().add(new ModelInfo("qwen3.6-plus", "Qwen3.6 Plus", true));
        p.getModels().add(new ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", true));
        p.getModels().add(new ModelInfo("glm-5.2", "GLM-5.2", true));
        p.getMeta()
                .put(
                        "base_url_options",
                        List.of(
                                Map.of(
                                        "label",
                                        "China (Beijing)",
                                        "value",
                                        "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                                Map.of(
                                        "label",
                                        "International (Singapore)",
                                        "value",
                                        "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
                                Map.of(
                                        "label",
                                        "US (Virginia)",
                                        "value",
                                        "https://dashscope-us.aliyuncs.com/compatible-mode/v1")));
    }

    private static void configureOpenCode(ProviderInfo p) {
        p.setChatModel("OpenAIChatModel");
        p.setSupportsOAuth(true);
        p.getMeta().put("supports_oauth", true);
        p.getMeta().put("oauth_provider", "opencode");
        p.getMeta()
                .put(
                        "base_url_options",
                        List.of(
                                Map.of("label", "OpenCode", "value", "https://opencode.ai/zen/v1"),
                                Map.of(
                                        "label",
                                        "OpenCode Go",
                                        "value",
                                        "https://opencode.ai/zen/go/v1")));

        ModelInfo bigPickle = new ModelInfo("big-pickle", "Big Pickle", true);
        bigPickle.setFree(true);
        p.getModels().add(bigPickle);
        ModelInfo mimo = new ModelInfo("mimo-v2.5-free", "Mimo V2.5", true);
        mimo.setFree(true);
        p.getModels().add(mimo);
        ModelInfo deepseek = new ModelInfo("deepseek-v4-flash-free", "DeepSeek V4 Flash", true);
        deepseek.setFree(true);
        p.getModels().add(deepseek);
        ModelInfo nemotronUltra = new ModelInfo("nemotron-3-ultra-free", "Nemotron 3 Ultra", true);
        nemotronUltra.setFree(true);
        p.getModels().add(nemotronUltra);
        ModelInfo nemotronSuper = new ModelInfo("nemotron-3-super-free", "Nemotron 3 Super", true);
        nemotronSuper.setFree(true);
        p.getModels().add(nemotronSuper);
    }

    private static void configureOpenRouter(ProviderInfo p) {
        p.setSupportsOAuth(true);
        p.getMeta().put("supports_oauth", true);
    }

    private static void configureAnthropic(ProviderInfo p) {
        p.setChatModel("AnthropicChatModel");
        p.setGenerateKwargs(new LinkedHashMap<>());
        p.getGenerateKwargs().put("max_tokens", 16384);
    }

    private static void configureMiniMax(ProviderInfo p) {
        p.setChatModel("AnthropicChatModel");
        p.setSupportConnectionCheck(false);
        addMiniMaxModels(p);
    }

    private static void addKiloModels(ProviderInfo p) {
        //        addFreeModel(p, "kilo-auto/free", "Kilo Auto (Free Router)");
        //        addFreeModel(p, "nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra
        // 550B");
        addFreeModel(p, "nvidia/nemotron-3-super-120b-a12b:free", "Nemotron 3 Super 120B");
        addFreeModel(p, "poolside/laguna-m.1:free", "Poolside Laguna M.1");
        addFreeModel(p, "poolside/laguna-xs.2:free", "Poolside Laguna XS.2");
        addFreeModel(p, "stepfun/step-3.7-flash:free", "Step 3.7 Flash");
        //        addFreeModel(p, "nex-agi/nex-n2-pro:free", "Nex N2 Pro");
    }

    private static void addGitHubModels(ProviderInfo p) {
        addFreeModel(p, "openai/gpt-4o-mini", "GPT-4o Mini");
        addFreeModel(p, "openai/gpt-4o", "GPT-4o");
    }

    private static void addFreeModel(ProviderInfo p, String id, String name) {
        ModelInfo model = new ModelInfo(id, name, true);
        model.setFree(true);
        p.getModels().add(model);
    }

    private static void addOpenAiModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("gpt-5.2", "GPT-5.2", true));
        p.getModels().add(new ModelInfo("gpt-5", "GPT-5", true));
        p.getModels().add(new ModelInfo("gpt-5-mini", "GPT-5 Mini", true));
        p.getModels().add(new ModelInfo("gpt-5-nano", "GPT-5 Nano", true));
        p.getModels().add(new ModelInfo("gpt-4.1", "GPT-4.1", true));
        p.getModels().add(new ModelInfo("gpt-4.1-mini", "GPT-4.1 Mini", true));
        p.getModels().add(new ModelInfo("gpt-4.1-nano", "GPT-4.1 Nano", true));
        p.getModels().add(new ModelInfo("o3", "o3", true));
        p.getModels().add(new ModelInfo("o4-mini", "o4-mini", true));
        p.getModels().add(new ModelInfo("gpt-4o", "GPT-4o", true));
        p.getModels().add(new ModelInfo("gpt-4o-mini", "GPT-4o Mini", true));
    }

    private static void addAzureModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("gpt-5-chat", "GPT-5 Chat", true));
        p.getModels().add(new ModelInfo("gpt-5-mini", "GPT-5 Mini", true));
        p.getModels().add(new ModelInfo("gpt-5-nano", "GPT-5 Nano", true));
        p.getModels().add(new ModelInfo("gpt-4.1", "GPT-4.1", true));
        p.getModels().add(new ModelInfo("gpt-4.1-mini", "GPT-4.1 Mini", true));
        p.getModels().add(new ModelInfo("gpt-4.1-nano", "GPT-4.1 Nano", true));
        p.getModels().add(new ModelInfo("gpt-4o", "GPT-4o", true));
        p.getModels().add(new ModelInfo("gpt-4o-mini", "GPT-4o Mini", true));
    }

    private static void addDeepSeekModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("deepseek-chat", "DeepSeek Chat", true));
        p.getModels().add(new ModelInfo("deepseek-reasoner", "DeepSeek Reasoner", true));
        p.getModels().add(new ModelInfo("deepseek-v4-flash", "DeepSeek V4 Flash", true));
        p.getModels().add(new ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", true));
    }

    private static void addGeminiModels(ProviderInfo p) {
        p.setChatModel("GeminiChatModel");
        p.getModels().add(new ModelInfo("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", true));
        p.getModels().add(new ModelInfo("gemini-3-flash-preview", "Gemini 3 Flash Preview", true));
        p.getModels()
                .add(
                        new ModelInfo(
                                "gemini-3.1-flash-lite-preview",
                                "Gemini 3.1 Flash Lite Preview",
                                true));
        p.getModels().add(new ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", true));
        p.getModels().add(new ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", true));
        p.getModels().add(new ModelInfo("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", true));
        p.getModels().add(new ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", true));
    }

    private static void addKimiModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("kimi-k2.5", "Kimi K2.5", true));
        p.getModels().add(new ModelInfo("kimi-k2-0905-preview", "Kimi K2 0905 Preview", true));
        p.getModels().add(new ModelInfo("kimi-k2-0711-preview", "Kimi K2 0711 Preview", true));
        p.getModels().add(new ModelInfo("kimi-k2-turbo-preview", "Kimi K2 Turbo Preview", true));
        p.getModels().add(new ModelInfo("kimi-k2-thinking", "Kimi K2 Thinking", true));
        p.getModels().add(new ModelInfo("kimi-k2-thinking-turbo", "Kimi K2 Thinking Turbo", true));
    }

    private static void addKimiCodingPlanModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("kimi-for-coding", "Kimi for Coding", true));
    }

    private static void addMiniMaxModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("MiniMax-M2.5", "MiniMax M2.5", true));
        p.getModels().add(new ModelInfo("MiniMax-M2.5-highspeed", "MiniMax M2.5 Highspeed", true));
        p.getModels().add(new ModelInfo("MiniMax-M2.7", "MiniMax M2.7", true));
        p.getModels().add(new ModelInfo("MiniMax-M2.7-highspeed", "MiniMax M2.7 Highspeed", true));
    }

    private static void addZhipuModels(ProviderInfo p) {
        ModelInfo flash = new ModelInfo("glm-4.7-flash", "GLM-4.7-Flash", true);
        flash.setFree(true);
        p.getModels().add(flash);
        p.getModels().add(new ModelInfo("glm-5", "glm-5", true));
        p.getModels().add(new ModelInfo("glm-5.2", "glm-5.2", true));
        p.getModels().add(new ModelInfo("glm-5.1", "glm-5.1", true));
        p.getModels().add(new ModelInfo("glm-5-turbo", "glm-5-turbo", true));
        p.getModels().add(new ModelInfo("glm-5v-turbo", "glm-5v-turbo", true));
    }

    private static void addAliyunModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("qwen3.6-plus", "Qwen3.6 Plus", true));
        p.getModels().add(new ModelInfo("qwen3.5-plus", "Qwen3.5 Plus", true));
        p.getModels().add(new ModelInfo("glm-5.2", "GLM-5.2", true));
        p.getModels().add(new ModelInfo("glm-5.1", "GLM-5.1", true));
        p.getModels().add(new ModelInfo("glm-5", "GLM-5", true));
        p.getModels().add(new ModelInfo("glm-4.7", "GLM-4.7", true));
        p.getModels().add(new ModelInfo("MiniMax-M2.5", "MiniMax M2.5", true));
        p.getModels().add(new ModelInfo("kimi-k2.5", "Kimi K2.5", true));
        p.getModels().add(new ModelInfo("qwen3-max-2026-01-23", "Qwen3 Max 2026-01-23", true));
        p.getModels().add(new ModelInfo("qwen3-coder-next", "Qwen3 Coder Next", true));
        p.getModels().add(new ModelInfo("qwen3-coder-plus", "Qwen3 Coder Plus", true));
    }

    private static void addAliyunTokenPlanModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("qwen3.7-plus", "Qwen3.7 Plus", true));
        p.getModels().add(new ModelInfo("qwen3.7-max", "Qwen3.7 Max", true));
        p.getModels().add(new ModelInfo("qwen3.6-plus", "Qwen3.6 Plus", true));
        p.getModels().add(new ModelInfo("qwen3.6-flash", "Qwen3.6 Flash", true));
        p.getModels().add(new ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", true));
        p.getModels().add(new ModelInfo("deepseek-v4-flash", "DeepSeek V4 Flash", true));
        p.getModels().add(new ModelInfo("deepseek-v3.2", "DeepSeek-V3.2", true));
        p.getModels().add(new ModelInfo("glm-5.2", "GLM-5.2", true));
        p.getModels().add(new ModelInfo("glm-5.1", "GLM-5.1", true));
        p.getModels().add(new ModelInfo("glm-5", "GLM-5", true));
        p.getModels().add(new ModelInfo("MiniMax-M2.5", "MiniMax M2.5", true));
        p.getModels().add(new ModelInfo("kimi-k2.6", "Kimi K2.6", true));
        p.getModels().add(new ModelInfo("kimi-k2.5", "Kimi K2.5", true));
    }

    private static void addMimoTokenPlanModels(ProviderInfo p) {
        p.getModels().add(new ModelInfo("mimo-v2.5-pro", "MiMo V2.5 Pro", true));
        ModelInfo standard = new ModelInfo("mimo-v2.5", "MiMo V2.5", true);
        standard.setSupportsImage(true);
        standard.setSupportsVideo(true);
        p.getModels().add(standard);
    }

    private static void addVolcengineModels(ProviderInfo p) {
        p.getModels()
                .add(
                        new ModelInfo(
                                "doubao-seed-2-0-code-preview-260215",
                                "Doubao-Seed-2.0-Code",
                                true));
        p.getModels().add(new ModelInfo("doubao-seed-2-0-pro-260215", "Doubao-Seed-2.0-pro", true));
        p.getModels()
                .add(new ModelInfo("doubao-seed-2-0-lite-260428", "Doubao-Seed-2.0-lite", true));
        p.getModels()
                .add(new ModelInfo("doubao-seed-code-preview-251028", "Doubao-Seed-Code", true));
        p.getModels().add(new ModelInfo("glm-4-7-251222", "GLM-4.7", true));
        p.getModels().add(new ModelInfo("deepseek-v3-2-251201", "DeepSeek-V3.2", true));
        p.getModels().add(new ModelInfo("doubao-seed-1-8-251228", "Doubao-Seed-1.8", true));
        p.getModels()
                .add(new ModelInfo("doubao-seed-2-0-mini-260428", "Doubao-Seed-2.0-mini", true));
        p.getModels()
                .add(new ModelInfo("doubao-seed-character-251128", "Doubao-Seed-Character", true));
    }

    private static void addVolcengineCodingPlanModels(ProviderInfo p) {
        p.getModels()
                .add(
                        new ModelInfo(
                                "doubao-seed-2-0-code-preview-260215",
                                "Doubao-Seed-2.0-Code",
                                true));
        p.getModels().add(new ModelInfo("doubao-seed-2-0-pro-260215", "Doubao-Seed-2.0-pro", true));
        p.getModels()
                .add(new ModelInfo("doubao-seed-2-0-lite-260428", "Doubao-Seed-2.0-lite", true));
        p.getModels()
                .add(new ModelInfo("doubao-seed-code-preview-251028", "Doubao-Seed-Code", true));
        p.getModels().add(new ModelInfo("glm-5.1", "GLM-5.1", true));
        p.getModels().add(new ModelInfo("minimax-m2.7", "MiniMax-M2.7", true));
        p.getModels().add(new ModelInfo("kimi-k2.6", "Kimi-K2.6", true));
        p.getModels().add(new ModelInfo("kimi-k2.5", "Kimi-K2.5", true));
        p.getModels().add(new ModelInfo("glm-4-7-251222", "GLM-4.7", true));
        p.getModels().add(new ModelInfo("deepseek-v3-2-251201", "DeepSeek-V3.2", true));
    }

    /** Inject brand grouping metadata into built-in Providers. */
    private static void annotateProviderGroups(List<ProviderInfo> providers) {
        // Aliyun group
        setGroup(providers, "dashscope", "aliyun", "Aliyun", "dashscope");
        setGroup(providers, "aliyun-codingplan", "aliyun", "Aliyun", "coding_plan_cn");
        setGroup(providers, "aliyun-codingplan-intl", "aliyun", "Aliyun", "coding_plan_intl");
        setGroup(providers, "aliyun-tokenplan", "aliyun", "Aliyun", "token_plan");
        setGroup(providers, "aliyun-tokenplan-intl", "aliyun", "Aliyun", "token_plan_intl");
        // Zhipu group
        setGroup(providers, "zhipu-cn", "zhipu", "Zhipu", "open_platform_cn");
        setGroup(providers, "zhipu-cn-codingplan", "zhipu", "Zhipu", "coding_plan_cn");
        setGroup(providers, "zhipu-intl", "zhipu", "Zhipu", "open_platform_intl");
        setGroup(providers, "zhipu-intl-codingplan", "zhipu", "Zhipu", "coding_plan_intl");
        // Kimi group
        setGroup(providers, "kimi-cn", "kimi", "Kimi", "open_platform_cn");
        setGroup(providers, "kimi-intl", "kimi", "Kimi", "open_platform_intl");
        setGroup(providers, "kimi-codingplan", "kimi", "Kimi", "coding_plan");
        // MiniMax group
        setGroup(providers, "minimax-cn", "minimax", "MiniMax", "open_platform_cn");
        setGroup(providers, "minimax", "minimax", "MiniMax", "open_platform_intl");
        // SiliconFlow group
        setGroup(providers, "siliconflow-cn", "siliconflow", "SiliconFlow", "china");
        setGroup(providers, "siliconflow-intl", "siliconflow", "SiliconFlow", "international");
        // Volcengine group
        setGroup(providers, "volcengine-cn", "volcengine", "Volcano Engine", "open_platform");
        setGroup(
                providers,
                "volcengine-cn-codingplan",
                "volcengine",
                "Volcano Engine",
                "coding_plan");
    }

    private static void setGroup(
            List<ProviderInfo> providers,
            String id,
            String group,
            String groupName,
            String variant) {
        byId(providers, id).setProviderGroup(group);
        byId(providers, id).setProviderGroupName(groupName);
        byId(providers, id).setProviderVariant(variant);
    }

    /** Disable native structured output for third-party OpenAI-compatible Providers to prevent response_format from causing tool calls to be skipped.
     * Only OpenAI and Azure OpenAI can safely enable it. */
    private static void disableNativeStructuredOutputForThirdParty(List<ProviderInfo> providers) {
        for (ProviderInfo p : providers) {
            if (!"openai".equals(p.getId()) && !"azure-openai".equals(p.getId())) {
                p.setNativeStructuredOutputWithTools(false);
            }
        }
    }

    private static void annotateBuiltInModelCapabilities(List<ProviderInfo> providers) {
        for (ProviderInfo provider : providers) {
            for (ModelInfo model : provider.allModels()) {
                annotateOneModelCapability(model);
            }
        }
    }

    private static void annotateOneModelCapability(ModelInfo model) {
        if (model == null || model.getId() == null) {
            return;
        }
        String id = model.getId().toLowerCase();

        boolean image =
                id.contains("gpt-4o")
                        || id.contains("gemini")
                        || id.contains("glm-5v")
                        || id.contains("qwen-vl")
                        || id.contains("qwen3-vl")
                        || id.contains("qvq")
                        || id.contains("vision")
                        || id.contains("doubao-seed");
        boolean video =
                id.contains("gemini")
                        || id.contains("qwen3.6-plus")
                        || id.contains("qwen3.5-plus")
                        || id.contains("qwen3-max")
                        || id.contains("doubao-seed");

        model.setSupportsImage(model.isSupportsImage() || image);
        model.setSupportsVideo(model.isSupportsVideo() || video);
    }
}
