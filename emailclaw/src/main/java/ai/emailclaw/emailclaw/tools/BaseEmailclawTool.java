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
package ai.emailclaw.emailclaw.tools;

import ai.emailclaw.emailclaw.service.ToolRuntimeContext;
import ai.emailclaw.emailclaw.service.ToolService;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseEmailclawTool implements EmailclawTool {

    protected static final Logger LOGGER = Logger.getLogger(BaseEmailclawTool.class.getName());

    protected ToolRuntimeContext context;

    protected Set<String> enabled;

    @Override
    public void init(ToolRuntimeContext context, Set<String> enabled) {
        this.context = context;
        this.enabled = enabled;
    }

    protected boolean off(String name) {
        return enabled == null || !enabled.contains(name);
    }

    protected String checkGuard(String toolName, Map<String, Object> input) {
        LOGGER.log(Level.INFO, "Tool call start: tool={0}", toolName);
        if (off(toolName)) {
            return ToolService.TOOL_DISABLED_MESSAGE;
        }
        return null;
    }

    protected boolean activeModelSupportsImage() {
        ai.emailclaw.emailclaw.model.ProviderInfo provider = resolveActiveProvider();
        if (provider == null) {
            return false;
        }
        ai.emailclaw.emailclaw.model.ModelInfo model = resolveActiveModel(provider);
        return model != null && model.isSupportsImage();
    }

    protected boolean activeModelSupportsVideo() {
        ai.emailclaw.emailclaw.model.ProviderInfo provider = resolveActiveProvider();
        if (provider == null) {
            return false;
        }
        ai.emailclaw.emailclaw.model.ModelInfo model = resolveActiveModel(provider);
        return model != null && model.isSupportsVideo();
    }

    protected ai.emailclaw.emailclaw.model.ProviderInfo resolveActiveProvider() {
        if (context == null || context.currentAgent == null) {
            return null;
        }
        if (context.currentAgent.getProviderId() != null
                && !context.currentAgent.getProviderId().isBlank()) {
            ai.emailclaw.emailclaw.model.ProviderInfo provider =
                    context.providerService
                            .getById(context.currentAgent.getProviderId())
                            .orElse(null);
            if (provider != null) {
                return provider;
            }
        }
        return context.providerService.listProviders().stream().findFirst().orElse(null);
    }

    protected ai.emailclaw.emailclaw.model.ModelInfo resolveActiveModel(
            ai.emailclaw.emailclaw.model.ProviderInfo provider) {
        if (provider == null) {
            return null;
        }
        if (context.currentAgent != null
                && context.currentAgent.getModelId() != null
                && !context.currentAgent.getModelId().isBlank()) {
            return provider.allModels().stream()
                    .filter(item -> context.currentAgent.getModelId().equals(item.getId()))
                    .findFirst()
                    .orElseGet(() -> provider.allModels().stream().findFirst().orElse(null));
        }
        return provider.allModels().stream().findFirst().orElse(null);
    }
}
