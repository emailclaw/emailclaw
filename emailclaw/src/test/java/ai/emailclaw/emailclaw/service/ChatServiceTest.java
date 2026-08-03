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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatMessageRoles;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.AppPaths;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock private AppContext repository;
    @Mock private AgentService agentService;
    @Mock private ProviderService providerService;
    @Mock private ToolRuntimeContext toolRuntimeContext;
    @Mock private GovernanceService governanceService;
    @Mock private AgentRuntimeDispatcher agentRuntimeDispatcher;
    @Mock private MessagePipeline messagePipeline;

    private ChatService chatService;
    private AppPaths appPaths;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("emailclaw-test");
        appPaths = new AppPaths(tempDir);

        lenient().when(repository.paths()).thenReturn(appPaths);

        chatService =
                new ChatService(
                        repository,
                        agentService,
                        providerService,
                        toolRuntimeContext,
                        governanceService,
                        agentRuntimeDispatcher);
        chatService.setMessagePipeline(messagePipeline);
    }

    @Test
    void testSendMessageWithAgentId() {
        String agentId = "agent1";
        String sessionId = "session1";
        String prompt = "Hello";
        AgentInfo agent = new AgentInfo();
        agent.setId(agentId);
        agent.setProviderId("provider1");
        agent.setModelId("model1");

        ProviderInfo provider = new ProviderInfo();
        provider.setId("provider1");

        when(agentService.findById(agentId)).thenReturn(Optional.of(agent));
        when(providerService.getById("provider1")).thenReturn(Optional.of(provider));

        ChatSessionInfo session = new ChatSessionInfo();
        session.setId(sessionId);
        when(repository.loadSessions()).thenReturn(List.of(session));

        chatService.sendMessage(agentId, sessionId, prompt, List.of(), Map.of(), null);

        verify(messagePipeline)
                .sendMessage(
                        eq(agent),
                        eq(provider),
                        eq("model1"),
                        eq(session),
                        eq(prompt),
                        eq(List.of()),
                        eq(Map.of()),
                        isNull());
    }

    @Test
    void testCreateSession() {
        when(repository.loadSessions()).thenReturn(new ArrayList<>());

        ChatSessionInfo session =
                chatService.createSession("agent1", "session1", "My Chat", "console");

        assertEquals("session1", session.getId());
        assertEquals("agent1", session.getAgentId());
        assertEquals("My Chat", session.getName());
        assertEquals("console", session.getChannel());
        verify(repository).saveSessions(anyList());
    }

    @Test
    void testFindSession() {
        ChatSessionInfo session = new ChatSessionInfo();
        session.setId("session1");
        when(repository.loadSessions()).thenReturn(List.of(session));

        ChatSessionInfo result = chatService.findSession("session1");
        assertNotNull(result);
        assertEquals("session1", result.getId());

        assertNull(chatService.findSession("unknown"));
    }

    @Test
    void testDeleteSession() {
        ChatSessionInfo session = new ChatSessionInfo();
        session.setId("session1");
        when(repository.loadSessions()).thenReturn(new ArrayList<>(List.of(session)));

        chatService.deleteSession("session1");

        verify(repository).saveSessions(argThat(list -> list.isEmpty()));
    }

    @Test
    void testParseJsonStringToMap() {
        Map<String, Object> map = chatService.parseJsonStringToMap("{\"key\":\"value\"}");
        assertEquals("value", map.get("key"));

        assertTrue(chatService.parseJsonStringToMap("").isEmpty());
        assertTrue(chatService.parseJsonStringToMap("invalid-json").isEmpty());
    }

    @Test
    void testHasPendingApprovalForSession() {
        ai.emailclaw.emailclaw.model.security.PendingApproval approval =
                new ai.emailclaw.emailclaw.model.security.PendingApproval(
                        "tool1", ai.emailclaw.emailclaw.model.security.GuardSeverity.LOW);
        approval.setSessionId("session1");
        when(governanceService.getPendingApprovals()).thenReturn(List.of(approval));

        assertTrue(chatService.hasPendingApprovalForSession("session1"));
        assertFalse(chatService.hasPendingApprovalForSession("session2"));
    }

    @Test
    void testRoleOf() {
        assertEquals(
                ChatMessageRoles.USER,
                chatService.roleOf(Msg.builder().role(MsgRole.USER).build()));
        assertEquals(
                ChatMessageRoles.SYSTEM,
                chatService.roleOf(Msg.builder().role(MsgRole.SYSTEM).build()));
        assertEquals(
                ChatMessageRoles.ASSISTANT,
                chatService.roleOf(Msg.builder().role(MsgRole.ASSISTANT).build()));
        assertEquals(ChatMessageRoles.ASSISTANT, chatService.roleOf(null));
    }

    @Test
    void testIsImageFile() {
        assertTrue(chatService.isImageFile(Path.of("test.png")));
        assertFalse(chatService.isImageFile(Path.of("test.txt")));
    }

    @Test
    void testIsVideoFile() {
        assertTrue(chatService.isVideoFile(Path.of("test.mp4")));
        assertFalse(chatService.isVideoFile(Path.of("test.txt")));
    }

    @Test
    void testInlineTextAttachment() throws Exception {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "hello world");
        String content = chatService.inlineTextAttachment(textFile);
        assertEquals("hello world", content);

        Path imageFile = tempDir.resolve("test.png");
        Files.writeString(imageFile, "binarydata");
        String imageContent = chatService.inlineTextAttachment(imageFile);
        assertEquals("", imageContent);
    }

    @Test
    void testNewSession() {
        ChatSessionInfo createdSession = new ChatSessionInfo();
        createdSession.setId("new-session-id");
        createdSession.setAgentId("agent1");

        when(repository.loadSessions()).thenReturn(new ArrayList<>());
        when(repository.createSession("agent1")).thenReturn(createdSession);

        ChatSessionInfo session = chatService.newSession("agent1");

        assertEquals("new-session-id", session.getId());
        assertEquals("agent1", session.getAgentId());
        verify(repository).saveSessions(anyList());
    }

    @Test
    void testSessions() {
        ChatSessionInfo session1 = new ChatSessionInfo();
        session1.setId("session1");
        session1.setAgentId("agent1");

        ChatSessionInfo session2 = new ChatSessionInfo();
        session2.setId("session2");
        session2.setAgentId("agent2");

        when(repository.loadSessions()).thenReturn(List.of(session1, session2));

        List<ChatSessionInfo> agent1Sessions = chatService.sessions("agent1");
        assertEquals(1, agent1Sessions.size());
        assertEquals("session1", agent1Sessions.get(0).getId());
    }

    @Test
    void testTouchSession() {
        ChatSessionInfo session = new ChatSessionInfo();
        session.setId("session1");
        session.setUpdatedAt("old-time");

        when(repository.loadSessions()).thenReturn(new ArrayList<>(List.of(session)));

        chatService.touchSession(session);

        verify(repository)
                .saveSessions(
                        argThat(
                                list -> {
                                    if (list.isEmpty()) return false;
                                    return !list.get(0).getUpdatedAt().equals("old-time");
                                }));
    }

    @Test
    void testUpdateSession() {
        ChatSessionInfo session = new ChatSessionInfo();
        session.setId("session1");
        session.setName("Old Name");

        when(repository.loadSessions()).thenReturn(new ArrayList<>(List.of(session)));

        ChatSessionInfo updatedSession = new ChatSessionInfo();
        updatedSession.setId("session1");
        updatedSession.setName("New Name");

        chatService.updateSession(updatedSession);

        verify(repository)
                .saveSessions(
                        argThat(
                                list -> {
                                    if (list.isEmpty()) return false;
                                    return list.get(0).getName().equals("New Name");
                                }));
    }

    @Test
    void testBatchDeleteSessions() {
        ChatSessionInfo session1 = new ChatSessionInfo();
        session1.setId("session1");
        ChatSessionInfo session2 = new ChatSessionInfo();
        session2.setId("session2");
        ChatSessionInfo session3 = new ChatSessionInfo();
        session3.setId("session3");

        when(repository.loadSessions())
                .thenReturn(new ArrayList<>(List.of(session1, session2, session3)));

        chatService.batchDeleteSessions(List.of("session1", "session3"));

        verify(repository)
                .saveSessions(
                        argThat(
                                list -> {
                                    return list.size() == 1
                                            && list.get(0).getId().equals("session2");
                                }));
    }
}
