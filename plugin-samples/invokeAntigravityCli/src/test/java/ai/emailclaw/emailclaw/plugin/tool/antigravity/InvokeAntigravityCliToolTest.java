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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.plugin.PluginRegistry;
import ai.emailclaw.emailclaw.plugin.PluginStatus;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import java.util.List;
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
    @DisplayName(
            "Tool invocation should pass formatted prompt with -p flag and return ToolResultBlock")
    void testToolExecutionSuccess() throws Exception {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicReference<String> capturedCliPath = new AtomicReference<>();
        AtomicReference<String> capturedModel = new AtomicReference<>();
        AtomicReference<String> capturedEffort = new AtomicReference<>();
        AtomicReference<Boolean> capturedSkipPerms = new AtomicReference<>();
        AtomicReference<Boolean> capturedContinueLastSession = new AtomicReference<>();
        AtomicReference<String> capturedConversation = new AtomicReference<>();

        AntigravityProcessRunner mockRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        effort,
                        timeoutSeconds,
                        dangerouslySkipPermissions,
                        extraArgs,
                        continueLastSession,
                        conversation) -> {
                    capturedCliPath.set(cliPath);
                    capturedPrompt.set(prompt);
                    capturedModel.set(model);
                    capturedEffort.set(effort);
                    capturedSkipPerms.set(dangerouslySkipPermissions);
                    capturedContinueLastSession.set(continueLastSession);
                    capturedConversation.set(conversation);
                    return new AntigravityExecutionResult(
                            0,
                            "```json\n"
                                + "{\"conversation_id\":\"conv-123\",\"test_suite\":\"user-service\",\"passed\":42,\"failed\":0}\n"
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
                        "Generate report",
                        null,
                        null,
                        "gemini-2.5-pro",
                        "High",
                        60,
                        true,
                        "--verbose",
                        true,
                        null);

        assertNotNull(resultBlock);
        assertEquals(ToolResultState.SUCCESS, resultBlock.getState());
        assertEquals("agy", capturedCliPath.get());
        assertEquals("Generate report", capturedPrompt.get());
        assertEquals("gemini-2.5-pro", capturedModel.get());
        assertEquals("high", capturedEffort.get());
        assertTrue(capturedSkipPerms.get());
        assertTrue(capturedContinueLastSession.get());
        assertNull(capturedConversation.get());
        assertEquals(0, resultBlock.getMetadata().get("exitCode"));
        assertEquals(false, resultBlock.getMetadata().get("timedOut"));
        assertEquals("conv-123", resultBlock.getMetadata().get("conversationId"));

        // Verify JSON parseability and stripped markdown
        assertFalse(resultBlock.getOutput().isEmpty());
        String outputText = ((TextBlock) resultBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertEquals("conv-123", node.get("conversation_id").asText());
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
                        effort,
                        timeoutSeconds,
                        dangerouslySkipPermissions,
                        extraArgs,
                        continueLastSession,
                        conversation) ->
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
                tool.invokeAntigravityCli(
                        "Long running task", null, null, null, null, 10, true, null, null, null);

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
        ToolResultBlock errorBlock =
                tool.invokeAntigravityCli(
                        "   ", null, null, null, null, null, null, null, null, null);

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
        assertEquals(PluginStatus.Phase.ERROR, plugin.status().phase()); // Context is null in test

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

    @Test
    @DisplayName(
            "DefaultAntigravityProcessRunner buildCommand should correctly construct agy CLI"
                    + " arguments including --conversation, -c, and --output-format json")
    void testDefaultAntigravityProcessRunnerBuildCommand() {
        DefaultAntigravityProcessRunner runner = new DefaultAntigravityProcessRunner(path -> path);

        // 1. New session: no -c, no --continue, no --conversation
        List<String> cmd1 = runner.buildCommand("agy", "fix bug", null, false, null, false, null);
        assertEquals(List.of("agy", "--output-format", "json", "-p", "fix bug"), cmd1);

        // 2. New session with model and dangerouslySkipPermissions=true
        List<String> cmd2 =
                runner.buildCommand(
                        "agy", "add feature", "gemini-2.5-pro", true, null, false, null);
        assertEquals(
                List.of(
                        "agy",
                        "--output-format",
                        "json",
                        "--dangerously-skip-permissions",
                        "--model",
                        "gemini-2.5-pro",
                        "-p",
                        "add feature"),
                cmd2);

        // 3. Extra arguments specified with -c already present, continueLastSession=true
        List<String> cmd3 =
                runner.buildCommand(
                        "/custom/agy", "review code", null, true, "-c --verbose", true, null);
        assertEquals(
                List.of(
                        "/custom/agy",
                        "--output-format",
                        "json",
                        "--dangerously-skip-permissions",
                        "-c",
                        "--verbose",
                        "-p",
                        "review code"),
                cmd3);

        // 4. continueLastSession=true without conversation ID (default behavior adds -c)
        List<String> cmd4 =
                runner.buildCommand(
                        "agy", "continue task", "gemini-2.5-pro", true, null, true, null);
        assertEquals(
                List.of(
                        "agy",
                        "-c",
                        "--output-format",
                        "json",
                        "--dangerously-skip-permissions",
                        "--model",
                        "gemini-2.5-pro",
                        "-p",
                        "continue task"),
                cmd4);

        // 5. Explicit conversation ID takes precedence over continueLastSession (-c is omitted)
        List<String> cmd5 =
                runner.buildCommand(
                        "agy", "resume task", "gemini-2.5-pro", true, null, true, "conv-abc-123");
        assertEquals(
                List.of(
                        "agy",
                        "--conversation",
                        "conv-abc-123",
                        "--output-format",
                        "json",
                        "--dangerously-skip-permissions",
                        "--model",
                        "gemini-2.5-pro",
                        "-p",
                        "resume task"),
                cmd5);

        // 6. Explicit conversation ID strips conflicting -c/--continue in extra_args
        List<String> cmd6 =
                runner.buildCommand(
                        "agy",
                        "resume clean",
                        null,
                        true,
                        "-c --output-format text --verbose",
                        true,
                        "conv-xyz-789");
        assertEquals(
                List.of(
                        "agy",
                        "--conversation",
                        "conv-xyz-789",
                        "--dangerously-skip-permissions",
                        "--output-format",
                        "text",
                        "--verbose",
                        "-p",
                        "resume clean"),
                cmd6);

        // 7. --conversation supplied via extra_args suppresses -c
        List<String> cmd7 =
                runner.buildCommand(
                        "agy", "extra conv", null, false, "--conversation conv-extra", true, null);
        assertEquals(
                List.of(
                        "agy",
                        "--output-format",
                        "json",
                        "--conversation",
                        "conv-extra",
                        "-p",
                        "extra conv"),
                cmd7);

        // 8. Explicit --model and --effort are both passed when provided
        List<String> cmd8 =
                runner.buildCommand(
                        "agy",
                        "deep analysis",
                        "gemini-2.5-pro",
                        "xhigh",
                        true,
                        null,
                        false,
                        "conv-99");
        assertEquals(
                List.of(
                        "agy",
                        "--conversation",
                        "conv-99",
                        "--output-format",
                        "json",
                        "--dangerously-skip-permissions",
                        "--model",
                        "gemini-2.5-pro",
                        "--effort",
                        "xhigh",
                        "-p",
                        "deep analysis"),
                cmd8);

        // 9. --effort already present in extra_args is not duplicated
        List<String> cmd9 =
                runner.buildCommand(
                        "agy", "dedup effort", null, "high", false, "--effort=max", false, null);
        assertEquals(
                List.of("agy", "--output-format", "json", "--effort=max", "-p", "dedup effort"),
                cmd9);
    }

    @Test
    @DisplayName(
            "Tool invocation should resolve conversation, continueLastSession, and fresh session"
                    + " correctly")
    void testContinueLastSessionDefaultHandling() {
        AtomicReference<Boolean> capturedContinue = new AtomicReference<>();
        AtomicReference<String> capturedConversation = new AtomicReference<>();
        AtomicReference<String> capturedModel = new AtomicReference<>();
        AtomicReference<String> capturedEffort = new AtomicReference<>();
        AntigravityProcessRunner mockRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        effort,
                        timeoutSeconds,
                        dangerouslySkipPermissions,
                        extraArgs,
                        continueLastSession,
                        conversation) -> {
                    capturedModel.set(model);
                    capturedEffort.set(effort);
                    capturedContinue.set(continueLastSession);
                    capturedConversation.set(conversation);
                    return new AntigravityExecutionResult(
                            0, "{\"conversation_id\":\"conv-returned-1\"}", "", false, true, null);
                };

        InvokeAntigravityCliTool tool =
                new InvokeAntigravityCliTool(null, mockRunner, "agy", 10, true);

        // 1. null continueLastSession and null conversation defaults to continueLastSession=true
        tool.invokeAntigravityCli(
                "Continue test", null, null, null, null, 10, true, null, null, null);
        assertTrue(capturedContinue.get(), "null continueLastSession should default to true");
        assertNull(capturedConversation.get());
        assertNull(capturedModel.get());
        assertNull(capturedEffort.get());

        // 2. explicit false continueLastSession and null conversation -> fresh session
        tool.invokeAntigravityCli(
                "Fresh test", null, null, "  ", "  ", 10, true, null, false, null);
        assertFalse(capturedContinue.get(), "explicit false continueLastSession should be false");
        assertNull(capturedConversation.get());
        assertNull(capturedModel.get());
        assertNull(capturedEffort.get());

        // 3. explicit conversation ID overrides continueLastSession (never passes -c)
        tool.invokeAntigravityCli(
                "Resume by ID",
                null,
                null,
                " gemini-2.5-pro ",
                " MAX ",
                10,
                true,
                null,
                true,
                "  conv-42  ");
        assertFalse(
                capturedContinue.get(),
                "continueLastSession must be false when conversation ID is provided");
        assertEquals("conv-42", capturedConversation.get());
        assertEquals("gemini-2.5-pro", capturedModel.get());
        assertEquals("max", capturedEffort.get());
    }

    @Test
    @DisplayName("Tool should reject unsupported effort values with error ToolResultBlock")
    void testInvalidEffortHandling() throws Exception {
        InvokeAntigravityCliTool tool = new InvokeAntigravityCliTool(null);
        ToolResultBlock errorBlock =
                tool.invokeAntigravityCli(
                        "Check effort", null, null, null, "ultra", null, null, null, null, null);

        assertNotNull(errorBlock);
        assertEquals(ToolResultState.ERROR, errorBlock.getState());
        assertEquals(-1, errorBlock.getMetadata().get("exitCode"));

        String outputText = ((TextBlock) errorBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertFalse(node.get("success").asBoolean());
        assertTrue(node.get("error").asText().contains("Invalid effort value"));
    }

    @Test
    @DisplayName("AntigravityJsonUtils should extract conversation_id and session_id cleanly")
    void testExtractConversationId() {
        assertEquals(
                "conv-001",
                AntigravityJsonUtils.extractConversationId(
                        "{\"status\":\"ok\",\"conversation_id\":\"conv-001\",\"response\":\"hi\"}"));
        assertEquals(
                "sess-002",
                AntigravityJsonUtils.extractConversationId("{\"session_id\":\"sess-002\"}"));
        assertEquals(
                "conv-last",
                AntigravityJsonUtils.extractConversationId(
                        "[{\"event\":\"init\"},{\"event\":\"result\",\"conversation_id\":\"conv-last\"}]"));
        assertNull(AntigravityJsonUtils.extractConversationId("{\"status\":\"ok\"}"));
        assertNull(AntigravityJsonUtils.extractConversationId("not-json"));
    }
}
