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
package ai.emailclaw.emailclaw.channel.emailclaw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EmailclawRunnerTest {

    @Mock private ChannelService channelService;
    @Mock private ChatService chatService;
    @Mock private AgentService agentService;
    @Mock private ProviderService providerService;
    @Mock private ConfigManager configManager;
    @Mock private ProjectService projectService;

    private EmailclawRunner runner;

    @BeforeEach
    void setUp() {
        runner =
                new EmailclawRunner(
                        channelService,
                        chatService,
                        agentService,
                        providerService,
                        configManager,
                        projectService);
    }

    private Object invokePrivateMethod(String methodName, Class<?>[] argTypes, Object[] args)
            throws Exception {
        Method method = EmailclawRunner.class.getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(runner, args);
    }

    @Test
    void testIsUuid() throws Exception {
        Boolean valid =
                (Boolean)
                        invokePrivateMethod(
                                "isUuid",
                                new Class<?>[] {String.class},
                                new Object[] {"123e4567-e89b-12d3-a456-426614174000"});
        assertTrue(valid);

        Boolean invalid =
                (Boolean)
                        invokePrivateMethod(
                                "isUuid",
                                new Class<?>[] {String.class},
                                new Object[] {"not-a-uuid"});
        assertFalse(invalid);

        Boolean nullValid =
                (Boolean)
                        invokePrivateMethod(
                                "isUuid", new Class<?>[] {String.class}, new Object[] {null});
        assertFalse(nullValid);
    }

    @Test
    void testExtractTrailingSessionId() throws Exception {
        String subject = "Re: Project discussion 123e4567-e89b-12d3-a456-426614174000";
        String extracted =
                (String)
                        invokePrivateMethod(
                                "extractTrailingSessionId",
                                new Class<?>[] {String.class},
                                new Object[] {subject});
        assertEquals("123e4567-e89b-12d3-a456-426614174000", extracted);

        String shortSubject = "Hello";
        String extractedShort =
                (String)
                        invokePrivateMethod(
                                "extractTrailingSessionId",
                                new Class<?>[] {String.class},
                                new Object[] {shortSubject});
        assertEquals("", extractedShort);
    }

    @Test
    void testNormalizeSender() throws Exception {
        String sender1 = "User Name <user@example.com>";
        String normalized1 =
                (String)
                        invokePrivateMethod(
                                "normalizeSender",
                                new Class<?>[] {String.class},
                                new Object[] {sender1});
        assertEquals("user@example.com", normalized1);

        String sender2 = "USER@EXAMPLE.COM";
        String normalized2 =
                (String)
                        invokePrivateMethod(
                                "normalizeSender",
                                new Class<?>[] {String.class},
                                new Object[] {sender2});
        assertEquals("user@example.com", normalized2);
    }

    @Test
    void testStripQuotedBlocksByFormat() throws Exception {
        String body =
                "This is the new reply.\n\n"
                        + "On 2026-07-28 user@example.com wrote:\n"
                        + "> This is old content.\n"
                        + "> Goodbye.";
        String stripped =
                (String)
                        invokePrivateMethod(
                                "stripQuotedBlocksByFormat",
                                new Class<?>[] {String.class},
                                new Object[] {body});

        assertEquals("This is the new reply.\n", stripped);

        String zhBody = "New reply\n\nOn 2026-07-28, user wrote:\n> Old reply";
        String zhStripped =
                (String)
                        invokePrivateMethod(
                                "stripQuotedBlocksByFormat",
                                new Class<?>[] {String.class},
                                new Object[] {zhBody});
        assertEquals("New reply\n", zhStripped);
    }

    @Test
    void testNormalizeMailBody() throws Exception {
        String body = "Line 1\nLine 2\rLine 3\u00a0End";
        String normalized =
                (String)
                        invokePrivateMethod(
                                "normalizeMailBody",
                                new Class<?>[] {String.class},
                                new Object[] {body});
        assertEquals("Line 1\nLine 2\nLine 3 End", normalized);
    }

    @Test
    void testCleanupExtractedBody() throws Exception {
        String body = "Actual message\n-- \nSignature content\nIgnore this";
        String cleaned =
                (String)
                        invokePrivateMethod(
                                "cleanupExtractedBody",
                                new Class<?>[] {String.class},
                                new Object[] {body});
        assertEquals("Actual message", cleaned);
    }

    @Test
    void testAllowedSender() throws Exception {
        ChannelInfo channel = new ChannelInfo();
        EmailclawChannelConfig.setEmailAllowlistSenders(channel, List.of("admin@example.com"));

        Boolean allowed =
                (Boolean)
                        invokePrivateMethod(
                                "allowedSender",
                                new Class<?>[] {ChannelInfo.class, String.class},
                                new Object[] {channel, "Admin User <admin@example.com>"});
        assertTrue(allowed);

        Boolean notAllowed =
                (Boolean)
                        invokePrivateMethod(
                                "allowedSender",
                                new Class<?>[] {ChannelInfo.class, String.class},
                                new Object[] {channel, "Hacker <hacker@example.com>"});
        assertFalse(notAllowed);
    }

    @Test
    void testStop() {
        runner.stop();
        // Just verify it doesn't throw. In real tests we could assert thread termination if exposed
        assertTrue(true);
    }
}
