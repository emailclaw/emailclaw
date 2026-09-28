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
package ai.emailclaw.emailclaw.plugin.tool.claude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.emailclaw.emailclaw.plugin.PluginRegistry;
import ai.emailclaw.emailclaw.plugin.PluginStatus;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link InvokeClaudeCodeCliTool} and {@link InvokeClaudeCodeCliPlugin}.
 */
class InvokeClaudeCodeCliToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Tool invocation should pass prompt, model, permissionMode, and working directory, returning ToolResultBlock")
    void testToolExecutionSuccess() throws Exception {
        AtomicReference<String> capturedCliPath = new AtomicReference<>();
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicReference<Path> capturedWorkingDir = new AtomicReference<>();
        AtomicReference<String> capturedModel = new AtomicReference<>();
        AtomicReference<String> capturedPermissionMode = new AtomicReference<>();
        AtomicReference<Boolean> capturedContinueLastSession = new AtomicReference<>();

        ClaudeCodeProcessRunner mockRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        permissionMode,
                        timeoutSeconds,
                        extraArgs,
                        continueLastSession) -> {
                    capturedCliPath.set(cliPath);
                    capturedPrompt.set(prompt);
                    capturedWorkingDir.set(workingDirectory);
                    capturedModel.set(model);
                    capturedPermissionMode.set(permissionMode);
                    capturedContinueLastSession.set(continueLastSession);
                    return new ClaudeCodeExecutionResult(
                            0,
                            "```json\n"
                                    + "{\"status\":\"completed\",\"modifiedFiles\":[\"App.java\"]}\n"
                                    + "```",
                            "",
                            false,
                            true,
                            null);
                };

        InvokeClaudeCodeCliTool tool =
                new InvokeClaudeCodeCliTool(null, mockRunner, "claude", 120);

        ToolResultBlock resultBlock =
                tool.invokeClaudeCodeCli(
                        "Refactor App.java to improve logging",
                        null,
                        "sonnet",
                        "bypassPermissions",
                        60,
                        null,
                        true);

        assertNotNull(resultBlock);
        assertEquals(ToolResultState.SUCCESS, resultBlock.getState());
        assertEquals("claude", capturedCliPath.get());
        assertEquals("Refactor App.java to improve logging", capturedPrompt.get());
        assertNotNull(capturedWorkingDir.get());
        assertEquals("sonnet", capturedModel.get());
        assertEquals("bypassPermissions", capturedPermissionMode.get());
        assertTrue(capturedContinueLastSession.get());
        assertEquals(0, resultBlock.getMetadata().get("exitCode"));
        assertEquals(false, resultBlock.getMetadata().get("timedOut"));

        assertFalse(resultBlock.getOutput().isEmpty());
        String outputText = ((TextBlock) resultBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertEquals("completed", node.get("status").asText());
        assertEquals("App.java", node.get("modifiedFiles").get(0).asText());

        // Test default continue_last_session (null -> true)
        tool.invokeClaudeCodeCli(
                "Refactor App.java to improve logging",
                null,
                "sonnet",
                "bypassPermissions",
                60,
                null,
                null);
        assertTrue(capturedContinueLastSession.get());

        // Test explicit false
        tool.invokeClaudeCodeCli(
                "Refactor App.java to improve logging",
                null,
                "sonnet",
                "bypassPermissions",
                60,
                null,
                false);
        assertFalse(capturedContinueLastSession.get());
    }

    @Test
    @DisplayName("Tool invocation should handle timeout gracefully with error ToolResultBlock")
    void testToolTimeoutHandling() throws Exception {
        ClaudeCodeProcessRunner timeoutRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        permissionMode,
                        timeoutSeconds,
                        extraArgs,
                        continueLastSession) ->
                        new ClaudeCodeExecutionResult(
                                -1,
                                "",
                                "Process killed due to timeout",
                                true,
                                false,
                                "Claude Code CLI execution timed out after "
                                        + timeoutSeconds
                                        + " seconds.");

        InvokeClaudeCodeCliTool tool =
                new InvokeClaudeCodeCliTool(null, timeoutRunner, "claude", 10);
        ToolResultBlock errorBlock =
                tool.invokeClaudeCodeCli(
                        "Long running claude task", null, null, null, 10, null, true);

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
        InvokeClaudeCodeCliTool tool = new InvokeClaudeCodeCliTool(null);
        ToolResultBlock errorBlock =
                tool.invokeClaudeCodeCli("   ", null, null, null, null, null, true);

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
        InvokeClaudeCodeCliPlugin plugin = new InvokeClaudeCodeCliPlugin();
        PluginRegistry registry = new PluginRegistry();

        assertEquals("emailclaw-plugin-tool-invokeClaudeCodeCli", plugin.id());
        assertEquals("Invoke Claude Code CLI", plugin.displayName());
        assertEquals("invokeClaudeCodeCli", plugin.getToolName());

        plugin.register(registry);
        assertTrue(
                registry.getTools().containsKey("invokeClaudeCodeCli"),
                "Registry must contain invokeClaudeCodeCli tool");

        plugin.initialize(null);
        assertEquals(PluginStatus.Phase.INITIALIZED, plugin.status().phase());

        plugin.start();
        assertEquals(
                PluginStatus.Phase.ERROR, plugin.status().phase()); // Context is null in test

        plugin.stop();
        assertEquals(PluginStatus.Phase.STOPPED, plugin.status().phase());
    }

    @Test
    @DisplayName("Clean JSON extraction should handle pure JSON, markdown, NDJSON, and raw text")
    void testExtractCleanJson() throws Exception {
        // Plain JSON object
        String plain = "{\"status\":\"ok\"}";
        assertEquals(plain, ClaudeCodeJsonUtils.extractCleanJson(plain));

        // Markdown JSON
        String md = "```json\n{\"answer\": 42}\n```";
        assertEquals("{\"answer\": 42}", ClaudeCodeJsonUtils.extractCleanJson(md));

        // Markdown without json tag
        String mdRaw = "```\n{\"foo\": \"bar\"}\n```";
        assertEquals("{\"foo\": \"bar\"}", ClaudeCodeJsonUtils.extractCleanJson(mdRaw));

        // NDJSON (JSON Lines)
        String ndjson = "{\"type\":\"turn_start\"}\n{\"type\":\"turn_end\"}";
        String ndjsonResult = ClaudeCodeJsonUtils.extractCleanJson(ndjson);
        JsonNode ndjsonNode = MAPPER.readTree(ndjsonResult);
        assertTrue(ndjsonNode.isArray());
        assertEquals(2, ndjsonNode.size());

        // Mixed text surrounding JSON object
        String surrounded = "Output:\n{\"result\":123}\nDone";
        assertEquals("{\"result\":123}", ClaudeCodeJsonUtils.extractCleanJson(surrounded));

        // Plain raw text fallback
        String raw = "Plain text output from claude";
        String rawJson = ClaudeCodeJsonUtils.extractCleanJson(raw);
        JsonNode rawNode = MAPPER.readTree(rawJson);
        assertTrue(rawNode.get("success").asBoolean());
        assertEquals("Plain text output from claude", rawNode.get("rawOutput").asText());
    }

    @Test
    @DisplayName("DefaultClaudeCodeProcessRunner buildCommand should correctly construct claude -p CLI arguments")
    void testDefaultClaudeCodeProcessRunnerBuildCommand() {
        DefaultClaudeCodeProcessRunner runner = new DefaultClaudeCodeProcessRunner();

        // 1. Standard prompt without model or permissionMode, continueLastSession = false
        List<String> cmd1 =
                runner.buildCommand("claude", "fix bug", null, null, null, false);
        assertEquals(
                List.of("claude", "-p", "--output-format", "json", "fix bug"),
                cmd1);

        // 2. Prompt with model and permissionMode, continueLastSession = false
        List<String> cmd2 =
                runner.buildCommand(
                        "claude",
                        "add feature",
                        "claude-sonnet-5",
                        "bypassPermissions",
                        null,
                        false);
        assertEquals(
                List.of(
                        "claude",
                        "-p",
                        "--output-format",
                        "json",
                        "--model",
                        "claude-sonnet-5",
                        "--permission-mode",
                        "bypassPermissions",
                        "add feature"),
                cmd2);

        // 3. Extra arguments specified, continueLastSession = false
        List<String> cmd3 =
                runner.buildCommand(
                        "/custom/claude",
                        "review code",
                        null,
                        null,
                        "--verbose --dangerously-skip-permissions",
                        false);
        assertEquals(
                List.of(
                        "/custom/claude",
                        "-p",
                        "--output-format",
                        "json",
                        "--verbose",
                        "--dangerously-skip-permissions",
                        "review code"),
                cmd3);

        // 4. continueLastSession = true (default behavior for session continuity)
        List<String> cmd4 =
                runner.buildCommand(
                        "claude", "continue task", "sonnet", null, null, true);
        assertEquals(
                List.of(
                        "claude",
                        "-c",
                        "-p",
                        "--output-format",
                        "json",
                        "--model",
                        "sonnet",
                        "continue task"),
                cmd4);

        // 5. Extra args containing -c and -p should avoid duplicate flags
        List<String> cmd5 =
                runner.buildCommand(
                        "claude", "continue task", null, null, "-c -p", true);
        assertEquals(
                List.of(
                        "claude",
                        "--output-format",
                        "json",
                        "-c",
                        "-p",
                        "continue task"),
                cmd5);
    }
}
