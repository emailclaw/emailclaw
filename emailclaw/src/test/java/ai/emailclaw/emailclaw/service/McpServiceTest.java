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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import ai.emailclaw.emailclaw.model.McpClientInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class McpServiceTest {

    @Mock private AppContext repository;
    @Mock private ConfigManager configManager;

    private McpService mcpService;
    private List<McpClientInfo> mockClients;

    @BeforeEach
    public void setUp() {
        mockClients = new ArrayList<>();
        McpClientInfo client1 =
                new McpClientInfo(
                        "client1",
                        "Client One",
                        true,
                        true,
                        null,
                        null,
                        "node",
                        List.of("index.js"),
                        null,
                        List.of("tool1"),
                        true,
                        List.of("tool1"),
                        null,
                        null,
                        null);
        mockClients.add(client1);

        lenient().when(repository.configManager()).thenReturn(configManager);
        lenient().when(configManager.getMcpClients()).thenReturn(mockClients);

        mcpService = new McpService(repository);
    }

    @Test
    public void testList() {
        List<McpClientInfo> clients = mcpService.list();
        assertEquals(1, clients.size());
        assertEquals("client1", clients.get(0).key());
    }

    @Test
    public void testAdd() {
        McpClientInfo newClient =
                new McpClientInfo(
                        "client2",
                        "Client Two",
                        true,
                        true,
                        null,
                        null,
                        "node",
                        List.of("index.js"),
                        null,
                        List.of("tool2"),
                        true,
                        List.of("tool2"),
                        null,
                        null,
                        null);
        mcpService.add(newClient);

        assertEquals(2, mockClients.size());
        assertEquals("client2", mockClients.get(1).key());
        verify(configManager).saveMcpClients(mockClients);
    }

    @Test
    public void testRemove() {
        McpClientInfo clientToRemove = mockClients.get(0);
        mcpService.remove(clientToRemove);

        assertTrue(mockClients.isEmpty());
        verify(configManager).saveMcpClients(mockClients);
    }

    @Test
    public void testToggleEnabled() {
        McpClientInfo client = mockClients.get(0);
        assertTrue(client.enabled());

        mcpService.toggleEnabled(client);

        assertFalse(mockClients.get(0).enabled());
        verify(configManager).saveMcpClients(mockClients);
    }

    @Test
    public void testSetToolWhitelist() {
        mcpService.setToolWhitelist("client1", false, List.of("tool_a"));

        McpClientInfo updated = mockClients.get(0);
        assertFalse(updated.toolWhitelistEnabled());
        assertEquals(1, updated.allowedToolNames().size());
        assertEquals("tool_a", updated.allowedToolNames().get(0));
        verify(configManager).saveMcpClients(mockClients);
    }

    @Test
    public void testEffectiveToolNames() {
        McpClientInfo client = mockClients.get(0);
        List<String> effective = mcpService.effectiveToolNames(client);
        assertEquals(1, effective.size());
        assertEquals("tool1", effective.get(0));
    }
}
