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
package ai.emailclaw.emailclaw.plugin.tool.opencode;

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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link InvokeOpencodeCliTool} and {@link InvokeOpencodeCliPlugin}.
 */
class InvokeOpencodeCliToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Tool invocation should pass prompt, model flags, and working directory, returning ToolResultBlock")
    void testToolExecutionSuccess() throws Exception {
        AtomicReference<String> capturedCliPath = new AtomicReference<>();
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicReference<Path> capturedWorkingDir = new AtomicReference<>();
        AtomicReference<String> capturedModel = new AtomicReference<>();
        AtomicReference<Boolean> capturedContinue = new AtomicReference<>();

        OpencodeProcessRunner mockRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        timeoutSeconds,
                        extraArgs,
                        continueLastSession) -> {
                    capturedCliPath.set(cliPath);
                    capturedPrompt.set(prompt);
                    capturedWorkingDir.set(workingDirectory);
                    capturedModel.set(model);
                    capturedContinue.set(continueLastSession);
                    return new OpencodeExecutionResult(
                            0,
                            "```json\n"
                                    + "{\"file\":\"main.py\",\"summary\":\"Entry point for the application\"}\n"
                                    + "```",
                            "",
                            false,
                            true,
                            null);
                };

        InvokeOpencodeCliTool tool =
                new InvokeOpencodeCliTool(null, mockRunner, "opencode", 120);

        ToolResultBlock resultBlock =
                tool.invokeOpencodeCli(
                        "解释一下 main.py 这个文件的作用",
                        null,
                        "openai/gpt-4o",
                        60,
                        null,
                        true);

        assertNotNull(resultBlock);
        assertEquals(ToolResultState.SUCCESS, resultBlock.getState());
        assertEquals("opencode", capturedCliPath.get());
        assertEquals("解释一下 main.py 这个文件的作用", capturedPrompt.get());
        assertNotNull(capturedWorkingDir.get());
        assertEquals("openai/gpt-4o", capturedModel.get());
        assertEquals(true, capturedContinue.get());
        assertEquals(0, resultBlock.getMetadata().get("exitCode"));
        assertEquals(false, resultBlock.getMetadata().get("timedOut"));

        // Verify JSON parseability and stripped markdown
        assertFalse(resultBlock.getOutput().isEmpty());
        String outputText = ((TextBlock) resultBlock.getOutput().get(0)).getText();
        JsonNode node = MAPPER.readTree(outputText);
        assertEquals("main.py", node.get("file").asText());
        assertEquals("Entry point for the application", node.get("summary").asText());
    }

    @Test
    @DisplayName("Tool invocation should handle timeout gracefully with error ToolResultBlock")
    void testToolTimeoutHandling() throws Exception {
        OpencodeProcessRunner timeoutRunner =
                (cliPath,
                        prompt,
                        workingDirectory,
                        model,
                        timeoutSeconds,
                        extraArgs,
                        continueLastSession) ->
                        new OpencodeExecutionResult(
                                -1,
                                "",
                                "Process killed due to timeout",
                                true,
                                false,
                                "OpenCode CLI execution timed out after "
                                        + timeoutSeconds
                                        + " seconds.");

        InvokeOpencodeCliTool tool =
                new InvokeOpencodeCliTool(null, timeoutRunner, "opencode", 10);
        ToolResultBlock errorBlock =
                tool.invokeOpencodeCli(
                        "Long running coding task", null, null, 10, null, null);

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
        InvokeOpencodeCliTool tool = new InvokeOpencodeCliTool(null);
        ToolResultBlock errorBlock =
                tool.invokeOpencodeCli("   ", null, null, null, null, null);

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
        InvokeOpencodeCliPlugin plugin = new InvokeOpencodeCliPlugin();
        PluginRegistry registry = new PluginRegistry();

        assertEquals("emailclaw-plugin-tool-invokeOpencodeCli", plugin.id());
        assertEquals("Invoke OpenCode CLI", plugin.displayName());
        assertEquals("invokeOpencodeCli", plugin.getToolName());

        plugin.register(registry);
        assertTrue(
                registry.getTools().containsKey("invokeOpencodeCli"),
                "Registry must contain invokeOpencodeCli tool");

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
        assertEquals(plain, OpencodeJsonUtils.extractCleanJson(plain));

        // Markdown JSON
        String md = "```json\n{\"answer\": 42}\n```";
        assertEquals("{\"answer\": 42}", OpencodeJsonUtils.extractCleanJson(md));

        // Markdown without json tag
        String mdRaw = "```\n{\"foo\": \"bar\"}\n```";
        assertEquals("{\"foo\": \"bar\"}", OpencodeJsonUtils.extractCleanJson(mdRaw));

        // NDJSON (JSON Lines)
        String ndjson = "{\"type\":\"step_start\",\"id\":1}\n{\"type\":\"step_finish\",\"id\":1}";
        String ndjsonResult = OpencodeJsonUtils.extractCleanJson(ndjson);
        JsonNode ndjsonNode = MAPPER.readTree(ndjsonResult);
        assertTrue(ndjsonNode.isArray());
        assertEquals(2, ndjsonNode.size());
        assertEquals("step_start", ndjsonNode.get(0).get("type").asText());

        // Mixed text surrounding JSON object
        String surrounded = "Here is the result:\n{\"key\":\"value\"}\nDone!";
        assertEquals("{\"key\":\"value\"}", OpencodeJsonUtils.extractCleanJson(surrounded));

        // Mixed text surrounding JSON array
        String surroundedArray = "Prefix:\n[{\"id\":1},{\"id\":2}]\nSuffix";
        assertEquals("[{\"id\":1},{\"id\":2}]", OpencodeJsonUtils.extractCleanJson(surroundedArray));

        // Plain raw text fallback
        String raw = "Plain text output from command";
        String rawJson = OpencodeJsonUtils.extractCleanJson(raw);
        JsonNode rawNode = MAPPER.readTree(rawJson);
        assertTrue(rawNode.get("success").asBoolean());
        assertEquals("Plain text output from command", rawNode.get("rawOutput").asText());
    }

    @Test
    @DisplayName("DefaultOpencodeProcessRunner buildCommand should automatically inject --auto and --continue and format arguments")
    void testDefaultOpencodeProcessRunnerBuildCommand() {
        DefaultOpencodeProcessRunner runner = new DefaultOpencodeProcessRunner();

        // 1. Standard prompt with continueLastSession=true
        var cmd1 = runner.buildCommand("opencode", "say hello", null, null, true);
        assertEquals(
                java.util.List.of("opencode", "run", "--continue", "--auto", "say hello"),
                cmd1);

        // 2. Prompt with model and continueLastSession=false
        var cmd2 = runner.buildCommand("opencode", "explain code", "openai/gpt-4o", null, false);
        assertEquals(
                java.util.List.of("opencode", "run", "-m", "openai/gpt-4o", "--auto", "explain code"),
                cmd2);

        // 3. Extra args containing --auto and --continue should not duplicate flags
        var cmd3 = runner.buildCommand("my-opencode", "test task", "claude-3-5", "--auto --verbose --continue", true);
        assertEquals(
                java.util.List.of("my-opencode", "run", "-m", "claude-3-5", "--auto", "--verbose", "--continue", "test task"),
                cmd3);
    }
}
