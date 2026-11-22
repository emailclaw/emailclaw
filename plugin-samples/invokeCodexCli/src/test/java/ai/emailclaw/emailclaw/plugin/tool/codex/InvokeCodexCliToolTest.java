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
package ai.emailclaw.emailclaw.plugin.tool.codex;

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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link InvokeCodexCliTool} and {@link InvokeCodexCliPlugin}.
 */
class InvokeCodexCliToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName(
            "Tool invocation should pass prompt, model, sandbox flags, and working directory,"
                    + " returning ToolResultBlock")
    void testToolExecutionSuccess() throws Exception {
        AtomicReference<String> capturedCliPath = new AtomicReference<>();
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicReference<Path> capturedWorkingDir = new AtomicReference<>();
        AtomicReference<String> capturedModel = new AtomicReference<>();
        AtomicReference<String> capturedEffort = new AtomicReference<>();
        AtomicReference<String> capturedSandbox = new AtomicReference<>();
        AtomicReference<Boolean> capturedContinueLastSession = new AtomicReference<>();
        AtomicReference<String> capturedConversation = new AtomicReference<>();

        CodexProcessRunner mockRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        effort,
                        sandbox,
                        timeoutSeconds,
                        extraArgs,
                        continueLastSession,
                        conversation) -> {
                    capturedCliPath.set(cliPath);
                    capturedPrompt.set(prompt);
                    capturedWorkingDir.set(workingDirectory);
                    capturedModel.set(model);
                    capturedEffort.set(effort);
                    capturedSandbox.set(sandbox);
                    capturedContinueLastSession.set(continueLastSession);
                    capturedConversation.set(conversation);
                    return new CodexExecutionResult(
                            0,
                            "{\"type\":\"thread.started\",\"thread_id\":\"019a-thread-1\"}\n"
                                    + "{\"type\":\"item.completed\",\"status\":\"completed\","
                                    + "\"modifiedFiles\":[\"App.java\"]}",
                            "",
                            false,
                            true,
                            null);
                };

        InvokeCodexCliTool tool = new InvokeCodexCliTool(null, mockRunner, "codex", 120);

        ToolResultBlock resultBlock =
                tool.invokeCodexCli(
                        "Refactor App.java to improve logging",
                        null,
                        "gpt-5",
                        "High",
                        "workspace-write",
                        60,
                        null,
                        true,
                        null);

        assertNotNull(resultBlock);
        assertEquals(ToolResultState.SUCCESS, resultBlock.getState());
        assertEquals("codex", capturedCliPath.get());
        assertEquals("Refactor App.java to improve logging", capturedPrompt.get());
        assertNotNull(capturedWorkingDir.get());
        assertEquals("gpt-5", capturedModel.get());
        assertEquals("high", capturedEffort.get());
        assertEquals("workspace-write", capturedSandbox.get());
        assertTrue(capturedContinueLastSession.get());
        assertNull(capturedConversation.get());
        assertEquals(0, resultBlock.getMetadata().get("exitCode"));
        assertEquals(false, resultBlock.getMetadata().get("timedOut"));
        assertEquals("019a-thread-1", resultBlock.getMetadata().get("conversationId"));

        assertFalse(resultBlock.getOutput().isEmpty());
        String outputText = ((TextBlock) resultBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertTrue(node.isArray());
        assertEquals("019a-thread-1", node.get(0).get("thread_id").asText());
        assertEquals("completed", node.get(1).get("status").asText());
        assertEquals("App.java", node.get(1).get("modifiedFiles").get(0).asText());

        // Test default continue_last_session (null -> true) with blank model/effort omitted
        tool.invokeCodexCli(
                "Refactor App.java to improve logging",
                null,
                "  ",
                "  ",
                "workspace-write",
                60,
                null,
                null,
                null);
        assertTrue(capturedContinueLastSession.get());
        assertNull(capturedModel.get());
        assertNull(capturedEffort.get());
        assertNull(capturedConversation.get());

        // Test explicit false (new session)
        tool.invokeCodexCli(
                "Refactor App.java to improve logging",
                null,
                "gpt-5",
                null,
                "workspace-write",
                60,
                null,
                false,
                null);
        assertFalse(capturedContinueLastSession.get());
        assertNull(capturedConversation.get());

        // Test explicit conversation ID overrides continue_last_session (never passes --last)
        tool.invokeCodexCli(
                "Continue thread",
                null,
                "gpt-5",
                "XHIGH",
                "workspace-write",
                60,
                null,
                true,
                "  019a-thread-1  ");
        assertFalse(capturedContinueLastSession.get());
        assertEquals("019a-thread-1", capturedConversation.get());
        assertEquals("xhigh", capturedEffort.get());
    }

    @Test
    @DisplayName("Tool invocation should handle timeout gracefully with error ToolResultBlock")
    void testToolTimeoutHandling() throws Exception {
        CodexProcessRunner timeoutRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        effort,
                        sandbox,
                        timeoutSeconds,
                        extraArgs,
                        continueLastSession,
                        conversation) ->
                        new CodexExecutionResult(
                                -1,
                                "",
                                "Process killed due to timeout",
                                true,
                                false,
                                "Codex CLI execution timed out after "
                                        + timeoutSeconds
                                        + " seconds.");

        InvokeCodexCliTool tool = new InvokeCodexCliTool(null, timeoutRunner, "codex", 10);
        ToolResultBlock errorBlock =
                tool.invokeCodexCli(
                        "Long running codex task", null, null, null, null, 10, null, true, null);

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
        InvokeCodexCliTool tool = new InvokeCodexCliTool(null);
        ToolResultBlock errorBlock =
                tool.invokeCodexCli("   ", null, null, null, null, null, null, true, null);

        assertNotNull(errorBlock);
        assertEquals(ToolResultState.ERROR, errorBlock.getState());
        assertEquals(-1, errorBlock.getMetadata().get("exitCode"));

        String outputText = ((TextBlock) errorBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertFalse(node.get("success").asBoolean());
        assertTrue(node.get("error").asText().contains("empty"));
    }

    @Test
    @DisplayName("Tool should reject unsupported effort values with error ToolResultBlock")
    void testInvalidEffortHandling() throws Exception {
        InvokeCodexCliTool tool = new InvokeCodexCliTool(null);
        ToolResultBlock errorBlock =
                tool.invokeCodexCli(
                        "Check effort", null, null, "ultra", null, null, null, false, null);

        assertNotNull(errorBlock);
        assertEquals(ToolResultState.ERROR, errorBlock.getState());
        assertEquals(-1, errorBlock.getMetadata().get("exitCode"));

        String outputText = ((TextBlock) errorBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertFalse(node.get("success").asBoolean());
        assertTrue(node.get("error").asText().contains("Invalid effort value"));
    }

    @Test
    @DisplayName("Plugin registration and lifecycle test")
    void testPluginRegistration() {
        InvokeCodexCliPlugin plugin = new InvokeCodexCliPlugin();
        PluginRegistry registry = new PluginRegistry();

        assertEquals("emailclaw-plugin-tool-invokeCodexCli", plugin.id());
        assertEquals("Invoke Codex CLI", plugin.displayName());
        assertEquals("invokeCodexCli", plugin.getToolName());

        plugin.register(registry);
        assertTrue(
                registry.getTools().containsKey("invokeCodexCli"),
                "Registry must contain invokeCodexCli tool");

        plugin.initialize(null);
        assertEquals(PluginStatus.Phase.INITIALIZED, plugin.status().phase());

        plugin.start();
        assertEquals(PluginStatus.Phase.ERROR, plugin.status().phase()); // Context is null in test

        plugin.stop();
        assertEquals(PluginStatus.Phase.STOPPED, plugin.status().phase());
    }

    @Test
    @DisplayName("Clean JSON extraction should handle pure JSON, markdown, NDJSON, and raw text")
    void testExtractCleanJson() throws Exception {
        // Plain JSON object
        String plain = "{\"status\":\"ok\"}";
        assertEquals(plain, CodexJsonUtils.extractCleanJson(plain));

        // Markdown JSON
        String md = "```json\n{\"answer\": 42}\n```";
        assertEquals("{\"answer\": 42}", CodexJsonUtils.extractCleanJson(md));

        // Markdown without json tag
        String mdRaw = "```\n{\"foo\": \"bar\"}\n```";
        assertEquals("{\"foo\": \"bar\"}", CodexJsonUtils.extractCleanJson(mdRaw));

        // NDJSON (JSON Lines)
        String ndjson = "{\"type\":\"turn_start\"}\n{\"type\":\"turn_end\"}";
        String ndjsonResult = CodexJsonUtils.extractCleanJson(ndjson);
        JsonNode ndjsonNode = MAPPER.readTree(ndjsonResult);
        assertTrue(ndjsonNode.isArray());
        assertEquals(2, ndjsonNode.size());

        // Mixed text surrounding JSON object
        String surrounded = "Output:\n{\"result\":123}\nDone";
        assertEquals("{\"result\":123}", CodexJsonUtils.extractCleanJson(surrounded));

        // Plain raw text fallback
        String raw = "Plain text output from codex";
        String rawJson = CodexJsonUtils.extractCleanJson(raw);
        JsonNode rawNode = MAPPER.readTree(rawJson);
        assertTrue(rawNode.get("success").asBoolean());
        assertEquals("Plain text output from codex", rawNode.get("rawOutput").asText());
    }

    @Test
    @DisplayName(
            "DefaultCodexProcessRunner buildCommand should correctly construct codex exec CLI"
                    + " arguments including conversation, model, effort, and --json")
    void testDefaultCodexProcessRunnerBuildCommand() {
        CodexPathResolver mockPathResolver = configuredPath -> "/resolved/bin/" + configuredPath;
        DefaultCodexProcessRunner runner = new DefaultCodexProcessRunner(mockPathResolver);

        // 1. New session without model, effort, or sandbox (no resume, no --last, no session id)
        List<String> cmd1 =
                runner.buildCommand("codex", "fix bug", null, null, null, null, false, null);
        assertEquals(
                List.of(
                        "/resolved/bin/codex",
                        "exec",
                        "--skip-git-repo-check",
                        "--json",
                        "fix bug"),
                cmd1);

        // 2. New session with model, effort, and sandbox
        List<String> cmd2 =
                runner.buildCommand(
                        "codex",
                        "add feature",
                        "gpt-5",
                        "high",
                        "workspace-write",
                        null,
                        false,
                        null);
        assertEquals(
                List.of(
                        "/resolved/bin/codex",
                        "exec",
                        "--skip-git-repo-check",
                        "--json",
                        "-m",
                        "gpt-5",
                        "-c",
                        "model_reasoning_effort=\"high\"",
                        "-s",
                        "workspace-write",
                        "add feature"),
                cmd2);

        // 3. Extra arguments specified with --json already present (deduplicated)
        List<String> cmd3 =
                runner.buildCommand(
                        "codex",
                        "review code",
                        null,
                        null,
                        null,
                        "--ephemeral --json",
                        false,
                        null);
        assertEquals(
                List.of(
                        "/resolved/bin/codex",
                        "exec",
                        "--skip-git-repo-check",
                        "--ephemeral",
                        "--json",
                        "review code"),
                cmd3);

        // 4. continueLastSession = true without conversation ID (default resume --last)
        // -s / --sandbox is omitted because 'codex exec resume' rejects -s.
        List<String> cmd4 =
                runner.buildCommand(
                        "codex",
                        "continue task",
                        "gpt-5",
                        "medium",
                        "workspace-write",
                        null,
                        true,
                        null);
        assertEquals(
                List.of(
                        "/resolved/bin/codex",
                        "exec",
                        "resume",
                        "--last",
                        "--skip-git-repo-check",
                        "--json",
                        "-m",
                        "gpt-5",
                        "-c",
                        "model_reasoning_effort=\"medium\"",
                        "continue task"),
                cmd4);

        // 5. Explicit conversation ID resumes that exact thread without --last or -s
        List<String> cmd5 =
                runner.buildCommand(
                        "codex",
                        "follow up",
                        "gpt-5",
                        "xhigh",
                        "workspace-write",
                        null,
                        true,
                        "019a-thread-42");
        assertEquals(
                List.of(
                        "/resolved/bin/codex",
                        "exec",
                        "resume",
                        "--skip-git-repo-check",
                        "--json",
                        "-m",
                        "gpt-5",
                        "-c",
                        "model_reasoning_effort=\"xhigh\"",
                        "019a-thread-42",
                        "follow up"),
                cmd5);

        // 6. Explicit conversation ID strips conflicting --last from extra_args and deduplicates
        // -m / model_reasoning_effort
        List<String> cmd6 =
                runner.buildCommand(
                        "codex",
                        "follow up clean",
                        "gpt-5",
                        "high",
                        null,
                        "--last -m o3 -c model_reasoning_effort=\"low\"",
                        true,
                        "019a-thread-99");
        assertEquals(
                List.of(
                        "/resolved/bin/codex",
                        "exec",
                        "resume",
                        "--skip-git-repo-check",
                        "--json",
                        "-m",
                        "o3",
                        "-c",
                        "model_reasoning_effort=\"low\"",
                        "019a-thread-99",
                        "follow up clean"),
                cmd6);
    }

    @Test
    @DisplayName("CodexJsonUtils should extract thread_id, conversation_id, and session_id")
    void testExtractConversationId() {
        assertEquals(
                "019a-abc",
                CodexJsonUtils.extractConversationId(
                        "[{\"type\":\"thread.started\",\"thread_id\":\"019a-abc\"},"
                                + "{\"type\":\"turn.completed\"}]"));
        assertEquals(
                "conv-001",
                CodexJsonUtils.extractConversationId("{\"conversation_id\":\"conv-001\"}"));
        assertEquals(
                "sess-002", CodexJsonUtils.extractConversationId("{\"session_id\":\"sess-002\"}"));
        assertNull(CodexJsonUtils.extractConversationId("{\"status\":\"ok\"}"));
        assertNull(CodexJsonUtils.extractConversationId("plain text"));
    }

    @Test
    @DisplayName("DefaultCodexPathResolver should expand tildes and resolve paths")
    void testDefaultCodexPathResolver() {
        DefaultCodexPathResolver resolver = new DefaultCodexPathResolver();

        // 1. Tilde expansion
        String expanded = resolver.expandTilde("~/bin/codex");
        String userHome = System.getProperty("user.home");
        assertEquals(userHome + "/bin/codex", expanded);

        // 2. Fallback to command name when binary not found on path
        String resolvedNonExistent = resolver.resolveCliPath("nonexistent-codex-binary-12345");
        assertEquals("nonexistent-codex-binary-12345", resolvedNonExistent);

        // 3. Null or blank resolves to codex or discovered path
        String resolvedDefault = resolver.resolveCliPath(null);
        assertNotNull(resolvedDefault);
        assertFalse(resolvedDefault.isBlank());
    }
}
