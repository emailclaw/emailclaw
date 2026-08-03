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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.emailclaw.emailclaw.model.ModelInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProviderServiceTest {

    @Mock private AppContext repository;
    @Mock private ConfigManager configManager;

    private ProviderService providerService;
    private List<ProviderInfo> mockProviders;

    @BeforeEach
    public void setUp() {
        mockProviders = new ArrayList<>();
        ProviderInfo provider1 = new ProviderInfo();
        provider1.setId("provider1");
        provider1.setName("Provider One");
        mockProviders.add(provider1);

        when(repository.configManager()).thenReturn(configManager);
        when(configManager.getProviders()).thenReturn(mockProviders);

        providerService = new ProviderService(repository);
    }

    @Test
    public void testListProviders() {
        List<ProviderInfo> providers = providerService.listProviders();
        assertEquals(1, providers.size());
        assertEquals("provider1", providers.get(0).getId());
    }

    @Test
    public void testGetById() {
        Optional<ProviderInfo> found = providerService.getById("provider1");
        assertTrue(found.isPresent());
        assertEquals("Provider One", found.get().getName());

        Optional<ProviderInfo> notFound = providerService.getById("provider2");
        assertFalse(notFound.isPresent());
    }

    @Test
    public void testSave() {
        providerService.save();
        verify(configManager).saveProviders(mockProviders);
    }

    @Test
    public void testStatus() {
        ProviderInfo provider = new ProviderInfo();
        provider.setId("emailclaw-local");
        assertEquals("Unavailable", providerService.status(provider));

        provider.setId("other");
        provider.setRequireApiKey(true);
        provider.setApiKey("");
        assertEquals("Not Ready (not configured)", providerService.status(provider));

        provider.setApiKey("key");
        assertEquals("Not Ready (no models)", providerService.status(provider));

        provider.getModels().add(new ModelInfo("m1", "M1", false));
        assertEquals("Ready (with models)", providerService.status(provider));
    }

    @Test
    public void testIsConfigured() {
        ProviderInfo provider = new ProviderInfo();
        provider.setId("emailclaw-local");
        assertTrue(providerService.isConfigured(provider));

        provider.setId("other");
        provider.setRequireApiKey(true);
        provider.setApiKey("");
        assertFalse(providerService.isConfigured(provider));

        provider.setApiKey("key");
        assertTrue(providerService.isConfigured(provider));
    }

    @Test
    public void testIsEligibleForDefaultLlm() {
        ProviderInfo provider = new ProviderInfo();
        assertFalse(providerService.isEligibleForDefaultLlm(provider));

        provider.getModels().add(new ModelInfo("m1", "M1", false));
        provider.setRequireApiKey(true);
        provider.setApiKey("key");
        assertTrue(providerService.isEligibleForDefaultLlm(provider));
    }

    @Test
    public void testAddAndRemoveModel() {
        ProviderInfo provider = mockProviders.get(0);

        providerService.addModel(provider, "custom1", "Custom 1");
        assertEquals(1, provider.getExtraModels().size());
        assertEquals("custom1", provider.getExtraModels().get(0).getId());
        verify(configManager).saveProviders(mockProviders);

        providerService.removeCustomModel(provider, provider.getExtraModels().get(0));
        assertTrue(provider.getExtraModels().isEmpty());
    }

    @Test
    public void testUpsertAndRemoveCustomProvider() {
        ProviderInfo customProvider = new ProviderInfo();
        customProvider.setId("custom-provider");
        customProvider.setName("Custom");

        providerService.upsertCustomProvider(customProvider);
        assertEquals(2, mockProviders.size());
        assertTrue(mockProviders.get(1).isCustom());
        verify(configManager).saveProviders(mockProviders);

        providerService.removeCustomProvider(mockProviders.get(1));
        assertEquals(1, mockProviders.size());
    }
}
