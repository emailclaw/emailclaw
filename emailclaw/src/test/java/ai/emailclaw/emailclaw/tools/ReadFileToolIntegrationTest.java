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
package ai.emailclaw.emailclaw.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.file.ReadFileTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadFileToolIntegrationTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("ToolRegistry registers view_text_file and list_directory when enabled")
    void testToolRegistryRegistersReadFileTool() {
        Toolkit toolkit = new Toolkit();
        Set<String> enabled =
                Set.of(BuiltInToolNames.VIEW_TEXT_FILE, BuiltInToolNames.LIST_DIRECTORY);

        ToolRegistry.registerAll(toolkit, null, enabled);

        assertNotNull(toolkit.getTool(BuiltInToolNames.VIEW_TEXT_FILE));
        assertNotNull(toolkit.getTool(BuiltInToolNames.LIST_DIRECTORY));
    }

    @Test
    @DisplayName("ToolRegistry removes list_directory when only view_text_file is enabled")
    void testToolRegistrySelectiveRegistration() {
        Toolkit toolkit = new Toolkit();
        Set<String> enabled = Set.of(BuiltInToolNames.VIEW_TEXT_FILE);

        ToolRegistry.registerAll(toolkit, null, enabled);

        assertNotNull(toolkit.getTool(BuiltInToolNames.VIEW_TEXT_FILE));
        assertNull(toolkit.getTool(BuiltInToolNames.LIST_DIRECTORY));
    }

    @Test
    @DisplayName("ReadFileTool reads line ranges with line numbers")
    void testReadFileToolLineRanges() throws Exception {
        Path testFile = tempDir.resolve("sample.txt");
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n");

        ReadFileTool tool = new ReadFileTool(tempDir.toString());
        ToolResultBlock result = tool.viewTextFile(testFile.toString(), "2,4").block();

        assertNotNull(result);
        String text = result.getOutput() != null ? result.getOutput().toString() : "";
        assertTrue(text.contains("Line 2"), "Should contain Line 2");
        assertTrue(text.contains("Line 3"), "Should contain Line 3");
        assertTrue(text.contains("Line 4"), "Should contain Line 4");
        assertFalse(text.contains("Line 1"), "Should not contain Line 1");
        assertFalse(text.contains("Line 5"), "Should not contain Line 5");
    }

    @Test
    @DisplayName("ReadFileTool lists directory contents")
    void testReadFileToolListDirectory() throws Exception {
        Files.writeString(tempDir.resolve("fileA.txt"), "hello");
        Files.createDirectory(tempDir.resolve("subDir"));

        ReadFileTool tool = new ReadFileTool(tempDir.toString());
        ToolResultBlock result = tool.listDirectory(tempDir.toString()).block();

        assertNotNull(result);
        String text = result.getOutput() != null ? result.getOutput().toString() : "";
        assertTrue(text.contains("fileA.txt"));
        assertTrue(text.contains("subDir"));
    }
}
