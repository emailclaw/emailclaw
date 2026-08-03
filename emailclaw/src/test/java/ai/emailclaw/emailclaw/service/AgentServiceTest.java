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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.AgentRuntimeStatus;
import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AgentServiceTest {

    @Mock private AppContext repository;
    @Mock private ConfigManager configManager;

    private AgentService agentService;
    private List<AgentInfo> mockAgents;
    private GlobalConfig mockGlobalConfig;

    @BeforeEach
    public void setUp() {
        mockAgents = new ArrayList<>();
        AgentInfo agent1 = new AgentInfo();
        agent1.setId("agent1");
        agent1.setName("Agent One");
        agent1.setEnabled(true);
        mockAgents.add(agent1);

        mockGlobalConfig = new GlobalConfig();

        when(repository.configManager()).thenReturn(configManager);
        when(configManager.getAgents()).thenReturn(mockAgents);

        agentService = new AgentService(repository);
    }

    @Test
    public void testList() {
        List<AgentInfo> agents = agentService.list();
        assertEquals(1, agents.size());
        assertEquals("agent1", agents.get(0).getId());
    }

    @Test
    public void testFindById() {
        Optional<AgentInfo> found = agentService.findById("agent1");
        assertTrue(found.isPresent());
        assertEquals("Agent One", found.get().getName());

        Optional<AgentInfo> notFound = agentService.findById("agent2");
        assertFalse(notFound.isPresent());
    }

    @Test
    public void testCurrentDefault_WithGlobalConfig() {
        mockGlobalConfig.setCurrentAgentId("agent1");
        when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);

        AgentInfo current = agentService.currentDefault();
        assertEquals("agent1", current.getId());
    }

    @Test
    public void testCurrentDefault_Fallback() {
        when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);

        AgentInfo current = agentService.currentDefault();
        assertEquals("agent1", current.getId());
        verify(configManager).saveGlobalConfig(mockGlobalConfig);
        assertEquals("agent1", mockGlobalConfig.getCurrentAgentId());
    }

    @Test
    public void testSetCurrentAgent() {
        when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);
        agentService.setCurrentAgent("agent1");
        verify(configManager).saveGlobalConfig(mockGlobalConfig);
        assertEquals("agent1", mockGlobalConfig.getCurrentAgentId());
    }

    @Test
    public void testCreate() {
        when(repository.workspaceFor("new-agent")).thenReturn(Path.of("/tmp/new-agent"));
        AgentInfo created =
                agentService.create(
                        "new-agent", "New Agent", "Desc", "provider1", "model1", List.of("skill1"));

        assertNotNull(created);
        assertEquals("new-agent", created.getId());
        assertEquals("New Agent", created.getName());
        assertEquals(2, mockAgents.size());
        verify(configManager).saveAgents(mockAgents);
    }

    @Test
    public void testRemove() {
        AgentInfo agentToRemove = mockAgents.get(0);
        when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);
        agentService.remove(agentToRemove);

        assertTrue(mockAgents.isEmpty());
        verify(configManager).saveAgents(mockAgents);
    }

    @Test
    public void testMarkTaskStartedAndFinished() {
        agentService.markTaskStarted("agent1");
        AgentRuntimeStatus status = agentService.statusOf("agent1");
        assertEquals("running", status.status());
        assertEquals(1, status.runningTaskCount());

        agentService.markTaskFinished("agent1");
        status = agentService.statusOf("agent1");
        assertEquals("idle", status.status());
        assertEquals(0, status.runningTaskCount());
    }
}
