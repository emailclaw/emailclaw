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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class EmailPresetRegistryTest {

    @Test
    void testPresetOfKnownDomain() {
        EmailMailPreset preset = EmailPresetRegistry.presetOf("user@gmail.com");
        assertNotNull(preset, "Should find preset for @gmail.com");
        assertEquals("imap.gmail.com", preset.imapHost());
        assertTrue(preset.imapSsl());
        assertEquals(993, preset.imapPort());

        EmailMailPreset outlookPreset = EmailPresetRegistry.presetOf("test.user@outlook.com");
        assertNotNull(outlookPreset, "Should find preset for @outlook.com");
        assertEquals("imap-mail.outlook.com", outlookPreset.imapHost());
    }

    @Test
    void testPresetOfCaseInsensitive() {
        EmailMailPreset preset = EmailPresetRegistry.presetOf("USER@GMAIL.COM");
        assertNotNull(preset, "Should find preset for uppercase domain");
        assertEquals("imap.gmail.com", preset.imapHost());
    }

    @Test
    void testPresetOfUnknownDomain() {
        EmailMailPreset preset = EmailPresetRegistry.presetOf("user@unknown-domain-test.com");
        assertNull(preset, "Should return null for unknown domain");
    }

    @Test
    void testPresetOfInvalidEmail() {
        assertNull(EmailPresetRegistry.presetOf("not-an-email"));
        assertNull(EmailPresetRegistry.presetOf(null));
        assertNull(EmailPresetRegistry.presetOf(""));
    }
}
