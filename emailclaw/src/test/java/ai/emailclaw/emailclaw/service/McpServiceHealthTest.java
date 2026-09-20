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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.storage.ConfigManager;
import io.agentscope.harness.agent.tools.McpServerRegistrationResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class McpServiceHealthTest {

    private McpService mcpService;

    @BeforeEach
    void setUp() {
        mcpService = new McpService((ConfigManager) null);
    }

    @Test
    @DisplayName("Record and retrieve MCP server registration results")
    void testRecordAndRetrieveHealthResults() {
        assertNull(mcpService.getHealthResult("server-a"));
        assertTrue(mcpService.getAllHealthResults().isEmpty());

        McpServerRegistrationResult successResult =
                McpServerRegistrationResult.success("server-a", "stdio");
        mcpService.recordRegistrationResult(successResult);

        McpServerRegistrationResult failedResult =
                McpServerRegistrationResult.failed(
                        "server-b", "sse", new RuntimeException("Connection refused"));
        mcpService.recordRegistrationResult(failedResult);

        McpServerRegistrationResult fetchedA = mcpService.getHealthResult("server-a");
        assertNotNull(fetchedA);
        assertEquals(McpServerRegistrationResult.Status.SUCCESS, fetchedA.status());
        assertEquals("stdio", fetchedA.transport());

        McpServerRegistrationResult fetchedB = mcpService.getHealthResult("server-b");
        assertNotNull(fetchedB);
        assertEquals(McpServerRegistrationResult.Status.FAILED, fetchedB.status());
        assertEquals("Connection refused", fetchedB.cause().getMessage());

        Map<String, McpServerRegistrationResult> all = mcpService.getAllHealthResults();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("server-a"));
        assertTrue(all.containsKey("server-b"));
    }
}
