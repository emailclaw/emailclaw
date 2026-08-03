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

import ai.emailclaw.emailclaw.model.AcpAgentInfo;
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
public class AcpServiceTest {

    @Mock private AppContext repository;
    @Mock private ConfigManager configManager;

    private AcpService acpService;
    private List<AcpAgentInfo> mockAgents;

    @BeforeEach
    public void setUp() {
        mockAgents = new ArrayList<>();
        AcpAgentInfo agent1 = new AcpAgentInfo("agent1", true, true, "cmd", "args");
        mockAgents.add(agent1);

        when(repository.configManager()).thenReturn(configManager);
        when(configManager.getAcpAgents()).thenReturn(mockAgents);

        acpService = new AcpService(repository);
    }

    @Test
    public void testList() {
        List<AcpAgentInfo> agents = acpService.list();
        assertEquals(1, agents.size());
        assertEquals("agent1", agents.get(0).getKey());
    }

    @Test
    public void testAdd() {
        AcpAgentInfo newAgent = new AcpAgentInfo("agent2", true, true, "cmd", "args");
        acpService.add(newAgent);

        assertEquals(2, mockAgents.size());
        assertEquals("agent2", mockAgents.get(1).getKey());
        verify(configManager).saveAcpAgents(mockAgents);
    }

    @Test
    public void testRemove() {
        AcpAgentInfo agentToRemove = mockAgents.get(0);
        acpService.remove(agentToRemove);

        assertTrue(mockAgents.isEmpty());
        verify(configManager).saveAcpAgents(mockAgents);
    }

    @Test
    public void testToggleEnabled() {
        AcpAgentInfo agent = mockAgents.get(0);
        assertTrue(agent.isEnabled());

        acpService.toggleEnabled(agent);

        assertFalse(mockAgents.get(0).isEnabled());
        verify(configManager).saveAcpAgents(mockAgents);
    }
}
