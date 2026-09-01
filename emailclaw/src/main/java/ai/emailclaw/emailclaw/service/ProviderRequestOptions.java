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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI-compatible Provider request parameter organizer.
 *
 * <p>Emailclaw automatically puts non-standard {@code generate_kwargs} into {@code extra_body},
 * to be compatible with OpenAI-style interfaces that support private request fields, such as DeepSeek, DashScope, SiliconFlow, etc.
 */
public final class ProviderRequestOptions {

    private static final Set<String> OPENAI_CREATE_PARAMS =
            Set.of(
                    "messages",
                    "model",
                    "audio",
                    "frequency_penalty",
                    "function_call",
                    "functions",
                    "logit_bias",
                    "logprobs",
                    "max_completion_tokens",
                    "max_tokens",
                    "metadata",
                    "modalities",
                    "n",
                    "parallel_tool_calls",
                    "prediction",
                    "presence_penalty",
                    "prompt_cache_key",
                    "prompt_cache_retention",
                    "reasoning_effort",
                    "response_format",
                    "safety_identifier",
                    "seed",
                    "service_tier",
                    "stop",
                    "store",
                    "stream",
                    "stream_options",
                    "temperature",
                    "tool_choice",
                    "tools",
                    "top_logprobs",
                    "top_p",
                    "user",
                    "verbosity",
                    "web_search_options",
                    "extra_headers",
                    "extra_query",
                    "extra_body",
                    "timeout");

    private ProviderRequestOptions() {}

    public static Map<String, String> headersFor(ProviderInfo provider) {
        Map<String, String> headers = new HashMap<>();
        if (provider == null) {
            return headers;
        }
        if (provider.getCustomHeaders() != null) {
            headers.putAll(provider.getCustomHeaders());
        }
        if ("Bearer Token".equals(provider.getAuthMode()) && hasText(provider.getApiKey())) {
            headers.put("Authorization", "Bearer " + provider.getApiKey());
        }
        if ("openrouter".equals(provider.getId())) {
            headers.putIfAbsent("HTTP-Referer", "https://emailclaw.email/");
            headers.putIfAbsent("X-Title", "Emailclaw");
        }
        return headers;
    }

    public static String apiKeyFor(ProviderInfo provider) {
        if (provider == null || !"API Key".equals(provider.getAuthMode())) {
            return "";
        }
        return provider.getApiKey() == null ? "" : provider.getApiKey();
    }

    public static Map<String, Object> bodyParamsFor(ProviderInfo provider, ModelInfo model) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (provider != null && provider.getGenerateKwargs() != null) {
            raw.putAll(provider.getGenerateKwargs());
        }
        if (model != null && model.getMaxInputLength() != null) {
            raw.put("max_input_length", model.getMaxInputLength());
        }
        return routeExtraBody(raw);
    }

    private static Map<String, Object> routeExtraBody(Map<String, Object> raw) {
        Map<String, Object> standard = new LinkedHashMap<>();
        Map<String, Object> extraBody = new LinkedHashMap<>();
        Object configuredExtraBody = raw.remove("extra_body");
        if (configuredExtraBody instanceof Map<?, ?> configured) {
            configured.forEach((key, value) -> extraBody.put(String.valueOf(key), value));
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (OPENAI_CREATE_PARAMS.contains(key)) {
                standard.put(key, entry.getValue());
            } else {
                extraBody.put(key, entry.getValue());
            }
        }
        if (!extraBody.isEmpty()) {
            standard.put("extra_body", extraBody);
        }
        return standard;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
