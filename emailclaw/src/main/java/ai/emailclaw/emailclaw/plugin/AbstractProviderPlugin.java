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
package ai.emailclaw.emailclaw.plugin;

/**
 * Abstract plugin base class of Provider type.
 * It is recommended for third-party developers to directly inherit this class for quick integration of new Large Language Models (LLMs), Embedding models, etc.
 */
public abstract class AbstractProviderPlugin extends AbstractEmailclawPlugin {

    @Override
    public final void register(PluginRegistry registry) {
        String providerId = getProviderId();
        Object providerInstance = createProviderInstance();

        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider ID cannot be empty");
        }
        if (providerInstance == null) {
            throw new IllegalArgumentException("Provider instance cannot be empty");
        }

        registry.registerProvider(providerId, providerInstance);
        logger.info("Successfully registered Provider plugin: " + providerId);

        onRegister(registry);
    }

    /**
     * Returns the globally unique identifier of the Provider (e.g., custom_ollama, my_company_llm)
     */
    protected abstract String getProviderId();

    /**
     * Creates and returns a Provider instance
     * (In actual use, this object typically implements the ModelWrapper or BaseProvider interface of AgentScope Java)
     */
    protected abstract Object createProviderInstance();

    /**
     * Subclasses can override this method if additional registration logic needs to be executed
     */
    protected void onRegister(PluginRegistry registry) {
        // No additional operations by default
    }
}
