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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.model.ChannelInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class EmailclawChannelConfigTest {

    @Test
    void testGetAndSetEmailAddress() {
        ChannelInfo channel = new ChannelInfo();
        EmailclawChannelConfig.setEmailAddress(channel, "test@example.com");
        assertEquals("test@example.com", EmailclawChannelConfig.getEmailAddress(channel));
    }

    @Test
    void testGetAndSetEmailPassword() {
        ChannelInfo channel = new ChannelInfo();
        EmailclawChannelConfig.setEmailPassword(channel, "pass123");
        assertEquals("pass123", EmailclawChannelConfig.getEmailPassword(channel));
    }

    @Test
    void testGetAndSetImapSmtpConfig() {
        ChannelInfo channel = new ChannelInfo();
        EmailclawChannelConfig.setImapHost(channel, "imap.example.com");
        EmailclawChannelConfig.setImapPort(channel, 143);
        EmailclawChannelConfig.setImapSsl(channel, false);
        EmailclawChannelConfig.setImapStartTls(channel, true);

        EmailclawChannelConfig.setSmtpHost(channel, "smtp.example.com");
        EmailclawChannelConfig.setSmtpPort(channel, 587);
        EmailclawChannelConfig.setSmtpSsl(channel, false);
        EmailclawChannelConfig.setSmtpStartTls(channel, true);

        assertEquals("imap.example.com", EmailclawChannelConfig.getImapHost(channel));
        assertEquals(143, EmailclawChannelConfig.getImapPort(channel));
        assertFalse(EmailclawChannelConfig.isImapSsl(channel));
        assertTrue(EmailclawChannelConfig.isImapStartTls(channel));

        assertEquals("smtp.example.com", EmailclawChannelConfig.getSmtpHost(channel));
        assertEquals(587, EmailclawChannelConfig.getSmtpPort(channel));
        assertFalse(EmailclawChannelConfig.isSmtpSsl(channel));
        assertTrue(EmailclawChannelConfig.isSmtpStartTls(channel));
    }

    @Test
    void testAllowlistSenders() {
        ChannelInfo channel = new ChannelInfo();
        EmailclawChannelConfig.setEmailAllowlistSenders(
                channel, List.of("user1@test.com", "user2@test.com"));
        List<String> senders = EmailclawChannelConfig.getEmailAllowlistSenders(channel);
        assertEquals(2, senders.size());
        assertEquals("user1@test.com", senders.get(0));
    }

    @Test
    void testResolveDefaultRecipient() {
        ChannelInfo channel = new ChannelInfo();
        assertNull(EmailclawChannelConfig.resolveDefaultRecipient(channel));

        EmailclawChannelConfig.setEmailAllowlistSenders(channel, List.of("  ", "user1@test.com"));
        assertEquals("user1@test.com", EmailclawChannelConfig.resolveDefaultRecipient(channel));
    }

    @Test
    void testNormalizeEmailclawPluginConfigWithPreset() {
        Map<String, Object> pluginConfig = new HashMap<>();
        pluginConfig.put(EmailclawConfigKeys.EMAIL_ADDRESS, "test@gmail.com");
        pluginConfig.put(EmailclawConfigKeys.IMAP_HOST, "imap.gmail.com");
        pluginConfig.put(EmailclawConfigKeys.SMTP_HOST, "smtp.gmail.com");

        ChannelInfo channel = new ChannelInfo();
        channel.setPluginConfig(pluginConfig);

        assertTrue(EmailclawChannelConfig.isPresetEmail(channel));

        boolean modified = EmailclawChannelConfig.normalizeEmailclawPluginConfig(channel);
        assertTrue(modified);

        Map<String, Object> normalized = channel.getPluginConfig();
        assertFalse(normalized.containsKey(EmailclawConfigKeys.IMAP_HOST));
        assertFalse(normalized.containsKey(EmailclawConfigKeys.SMTP_HOST));
    }

    @Test
    void testNormalizeEmailclawPluginConfigWithCustom() {
        Map<String, Object> pluginConfig = new HashMap<>();
        pluginConfig.put(EmailclawConfigKeys.EMAIL_ADDRESS, "test@custom-domain.com");
        pluginConfig.put(EmailclawConfigKeys.IMAP_HOST, "imap.custom.com");

        ChannelInfo channel = new ChannelInfo();
        channel.setPluginConfig(pluginConfig);

        assertFalse(EmailclawChannelConfig.isPresetEmail(channel));

        boolean modified = EmailclawChannelConfig.normalizeEmailclawPluginConfig(channel);
        assertFalse(modified);

        Map<String, Object> normalized = channel.getPluginConfig();
        assertTrue(normalized.containsKey(EmailclawConfigKeys.IMAP_HOST));
    }
}
