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
package ai.emailclaw.emailclaw.plugin.tool.antigravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.plugin.PluginRegistry;
import ai.emailclaw.emailclaw.plugin.PluginStatus;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link InvokeAntigravityCliTool} and {@link InvokeAntigravityCliPlugin}.
 */
class InvokeAntigravityCliToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Prompt formatting should always append strict JSON instruction")
    void testPromptFormatting() {
        String inputPrompt = "Run test suite and generate test report";
        String formatted = AntigravityJsonUtils.appendJsonFormatInstruction(inputPrompt);

        assertTrue(
                formatted.endsWith(AntigravityJsonUtils.JSON_FORMAT_INSTRUCTION),
                "Prompt must end with the required JSON format instruction");
        assertTrue(formatted.contains(inputPrompt), "Original prompt must be preserved");

        // If already ending with instruction, it should not duplicate
        String doubleFormatted = AntigravityJsonUtils.appendJsonFormatInstruction(formatted);
        assertEquals(
                formatted,
                doubleFormatted,
                "Instruction must not be duplicated if already present");
    }

    @Test
    @DisplayName("Tool invocation should pass formatted prompt with -p flag and return ToolResultBlock")
    void testToolExecutionSuccess() throws Exception {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicReference<String> capturedCliPath = new AtomicReference<>();
        AtomicReference<Boolean> capturedSkipPerms = new AtomicReference<>();

        AntigravityProcessRunner mockRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        timeoutSeconds,
                        dangerouslySkipPermissions,
                        extraArgs) -> {
                    capturedCliPath.set(cliPath);
                    capturedPrompt.set(prompt);
                    capturedSkipPerms.set(dangerouslySkipPermissions);
                    return new AntigravityExecutionResult(
                            0,
                            "```json\n"
                                + "{\"test_suite\":\"user-service\",\"passed\":42,\"failed\":0}\n"
                                + "```",
                            "",
                            false,
                            true,
                            null);
                };

        InvokeAntigravityCliTool tool =
                new InvokeAntigravityCliTool(null, mockRunner, "agy", 120, true);

        ToolResultBlock resultBlock =
                tool.invokeAntigravityCli(
                        "Generate report", null, null, "gemini-2.5-pro", 60, true, "--verbose");

        assertNotNull(resultBlock);
        assertEquals(ToolResultState.SUCCESS, resultBlock.getState());
        assertEquals("agy", capturedCliPath.get());
        assertTrue(capturedPrompt.get().contains("Generate report"));
        assertTrue(
                capturedPrompt.get().endsWith(AntigravityJsonUtils.JSON_FORMAT_INSTRUCTION));
        assertTrue(capturedSkipPerms.get());
        assertEquals(0, resultBlock.getMetadata().get("exitCode"));
        assertEquals(false, resultBlock.getMetadata().get("timedOut"));

        // Verify JSON parseability and stripped markdown
        assertFalse(resultBlock.getOutput().isEmpty());
        String outputText = ((TextBlock) resultBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertEquals("user-service", node.get("test_suite").asText());
        assertEquals(42, node.get("passed").asInt());
        assertEquals(0, node.get("failed").asInt());
    }

    @Test
    @DisplayName("Tool invocation should handle timeout gracefully with error ToolResultBlock")
    void testToolTimeoutHandling() throws Exception {
        AntigravityProcessRunner timeoutRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        timeoutSeconds,
                        dangerouslySkipPermissions,
                        extraArgs) ->
                        new AntigravityExecutionResult(
                                -1,
                                "",
                                "Process killed due to timeout",
                                true,
                                false,
                                "Antigravity CLI execution timed out after "
                                        + timeoutSeconds
                                        + " seconds.");

        InvokeAntigravityCliTool tool =
                new InvokeAntigravityCliTool(null, timeoutRunner, "agy", 10, true);
        ToolResultBlock errorBlock =
                tool.invokeAntigravityCli("Long running task", null, null, null, 10, true, null);

        assertNotNull(errorBlock);
        assertEquals(ToolResultState.ERROR, errorBlock.getState());
        assertEquals(true, errorBlock.getMetadata().get("timedOut"));
        assertEquals(-1, errorBlock.getMetadata().get("exitCode"));

        String outputText = ((TextBlock) errorBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertFalse(node.get("success").asBoolean());
        assertTrue(node.get("error").asText().contains("timed out"));
        assertEquals(-1, node.get("exitCode").asInt());
    }

    @Test
    @DisplayName("Tool should reject empty prompts immediately with error ToolResultBlock")
    void testEmptyPromptHandling() throws Exception {
        InvokeAntigravityCliTool tool = new InvokeAntigravityCliTool(null);
        ToolResultBlock errorBlock = tool.invokeAntigravityCli("   ", null, null, null, null, null, null);

        assertNotNull(errorBlock);
        assertEquals(ToolResultState.ERROR, errorBlock.getState());
        assertEquals(-1, errorBlock.getMetadata().get("exitCode"));

        String outputText = ((TextBlock) errorBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertFalse(node.get("success").asBoolean());
        assertTrue(node.get("error").asText().contains("empty"));
    }

    @Test
    @DisplayName("Plugin registration and lifecycle test")
    void testPluginRegistration() {
        InvokeAntigravityCliPlugin plugin = new InvokeAntigravityCliPlugin();
        PluginRegistry registry = new PluginRegistry();

        assertEquals("emailclaw-plugin-tool-invokeAntigravityCli", plugin.id());
        assertEquals("Invoke Antigravity CLI", plugin.displayName());
        assertEquals("invokeAntigravityCli", plugin.getToolName());

        plugin.register(registry);
        assertTrue(
                registry.getTools().containsKey("invokeAntigravityCli"),
                "Registry must contain invokeAntigravityCli tool");

        plugin.initialize(null);
        assertEquals(PluginStatus.Phase.INITIALIZED, plugin.status().phase());

        plugin.start();
        assertEquals(
                PluginStatus.Phase.ERROR, plugin.status().phase()); // Context is null in test

        plugin.stop();
        assertEquals(PluginStatus.Phase.STOPPED, plugin.status().phase());
    }

    @Test
    @DisplayName("Clean JSON extraction should handle various envelopes and raw strings")
    void testExtractCleanJson() throws Exception {
        // Plain JSON
        String plain = "{\"status\":\"ok\"}";
        assertEquals(plain, AntigravityJsonUtils.extractCleanJson(plain));

        // Markdown JSON
        String md = "```json\n{\"answer\": 42}\n```";
        assertEquals("{\"answer\": 42}", AntigravityJsonUtils.extractCleanJson(md));

        // Markdown without json tag
        String mdRaw = "```\n{\"foo\": \"bar\"}\n```";
        assertEquals("{\"foo\": \"bar\"}", AntigravityJsonUtils.extractCleanJson(mdRaw));

        // Mixed text surrounding JSON
        String surrounded = "Here is the result:\n{\"key\":\"value\"}\nHope that helps!";
        assertEquals("{\"key\":\"value\"}", AntigravityJsonUtils.extractCleanJson(surrounded));

        // Plain raw text fallback
        String raw = "Plain text output from command";
        String rawJson = AntigravityJsonUtils.extractCleanJson(raw);
        JsonNode rawNode = MAPPER.readTree(rawJson);
        assertTrue(rawNode.get("success").asBoolean());
        assertEquals("Plain text output from command", rawNode.get("rawOutput").asText());
    }
}
