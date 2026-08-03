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

import ai.emailclaw.emailclaw.model.FileDiffRecord;
import ai.emailclaw.emailclaw.util.FileDiffUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File difference tracker.
 *
 * <p>Responsible for accumulating JSON input of edit_file/write_file during stream tool calls,
 * capturing old and new file contents before and after tool execution, and outputting computed differences.
 *
 * <p>This component is extracted from ChatService, following the single responsibility principle, making the diff logic
 * independently testable and maintainable.
 *
 * <p>Usage flow:
 * <ol>
 *   <li>Call {@link #accumulateInput} at ToolCallDeltaEvent to accumulate JSON input</li>
 *   <li>Call {@link #snapshotOldContent} at ToolResultStartEvent to capture old file content</li>
 *   <li>Call {@link #computeAndCleanup} at ToolResultEndEvent to compute differences and clean up state</li>
 * </ol>
 *
 * <p>edit_file / write_file tool name constants, used for diff computation.
 */
final class FileDiffTracker {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(FileDiffTracker.class.getName());

    /**
     * edit_file tool name constant.
     */
    static final String DIFF_EDIT_FILE = "edit_file";

    /**
     * write_file tool name constant.
     */
    static final String DIFF_WRITE_FILE = "write_file";

    /**
     * Accumulate JSON input snippets for each toolCallId, in order to parse the path parameter from them.
     */
    private final Map<String, StringBuilder> toolCallInputBuffers = new HashMap<>();

    /**
     * Record the old file content of edit_file/write_file corresponding to each toolCallId.
     */
    private final Map<String, String> diffOldContents = new HashMap<>();

    /**
     * Record the absolute file path corresponding to each toolCallId (used for diff display).
     */
    private final Map<String, String> diffFilePaths = new HashMap<>();

    /**
     * Used to obtain the workspace absolute path.
     */
    private final Path workspace;

    /**
     * Construct a file difference tracker.
     *
     * @param workspace absolute path of the agent's workspace
     */
    FileDiffTracker(Path workspace) {
        this.workspace = workspace;
        LOGGER.log(
                Level.FINE, "FileDiffTracker initialized successfully, workspace={0}", workspace);
    }

    /**
     * Determine if the specified tool name requires diff tracking.
     *
     * @param toolName Tool name
     * @return true if it is edit_file or write_file
     */
    boolean isDiffTrackedTool(String toolName) {
        return DIFF_EDIT_FILE.equals(toolName) || DIFF_WRITE_FILE.equals(toolName);
    }

    /**
     * Accumulate JSON input snippets for the tool call during ToolCallDeltaEvent.
     *
     * <p>Tool call parameters arrive as a stream of JSON snippets, and after concatenation they form a JSON string like
     * {@code {"path": "/some/file.java", "content": "..."}}.
     *
     * @param toolCallId Tool call ID
     * @param delta      Current incremental content
     */
    void accumulateInput(String toolCallId, String delta) {
        toolCallInputBuffers
                .computeIfAbsent(toolCallId, k -> new StringBuilder())
                .append(delta != null ? delta : "");
        LOGGER.log(
                Level.FINE,
                "Diff: Accumulating tool input, toolCallId={0}, deltaLength={1}",
                new Object[] {toolCallId, delta != null ? delta.length() : 0});
    }

    /**
     * Capture old file content snapshot during ToolResultStartEvent.
     *
     * <p>Read the current content of the target file before tool execution to compute differences later.
     * If the file does not exist (new file), the old content is treated as an empty string.
     *
     * @param toolCallId Tool call ID
     * @param toolName   Tool name
     */
    void snapshotOldContent(String toolCallId, String toolName) {
        if (!isDiffTrackedTool(toolName)) {
            return;
        }
        String filePath = extractPathFromToolInput(toolCallInputBuffers.get(toolCallId));
        if (filePath == null || filePath.isBlank()) {
            LOGGER.log(
                    Level.FINE,
                    "Diff: Unable to extract path from tool input, toolCallId={0}",
                    toolCallId);
            return;
        }
        Path absPath = workspace.resolve(filePath).normalize();
        diffFilePaths.put(toolCallId, absPath.toString());
        try {
            if (Files.exists(absPath)) {
                diffOldContents.put(toolCallId, Files.readString(absPath));
                LOGGER.log(
                        Level.FINE,
                        "Diff: Captured old file content, path={0}, size={1}",
                        new Object[] {absPath, Files.size(absPath)});
            } else {
                // New file, old content is empty
                diffOldContents.put(toolCallId, "");
                LOGGER.log(Level.FINE, "Diff: New file, old content is empty, path={0}", absPath);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Diff: Failed to read old file content: " + absPath, e);
        }
    }

    /**
     * Compute file differences and clean up state during ToolResultEndEvent.
     *
     * <p>Read the new content of the file and compute the difference with the previously captured old content.
     * If there are differences, return the difference markup; otherwise, return null.
     *
     * @param toolCallId Tool call ID
     * @param toolName   Tool name
     * @return Difference markup text, or null if no differences or an error occurred
     */
    String computeAndCleanup(String toolCallId, String toolName) {
        if (!diffOldContents.containsKey(toolCallId)) {
            return null;
        }
        String absPath = diffFilePaths.get(toolCallId);
        try {
            String oldContent = diffOldContents.remove(toolCallId);
            String newContent = "";
            Path newPath = Path.of(absPath);
            if (Files.exists(newPath)) {
                newContent = Files.readString(newPath);
            }
            FileDiffRecord diffRecord = FileDiffUtils.computeDiff(absPath, oldContent, newContent);
            if (diffRecord != null
                    && (diffRecord.getLinesAdded() > 0 || diffRecord.getLinesDeleted() > 0)) {
                String markup = diffRecord.toMarkup();
                LOGGER.log(
                        Level.INFO,
                        "Diff computation complete: {0}, +{1} -{2}",
                        new Object[] {
                            absPath, diffRecord.getLinesAdded(), diffRecord.getLinesDeleted()
                        });
                return "\n" + markup;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Diff: Failed to compute difference: " + absPath, e);
        } finally {
            diffFilePaths.remove(toolCallId);
            toolCallInputBuffers.remove(toolCallId);
        }
        return null;
    }

    /**
     * Extract the {@code "path"} field value from the accumulated tool call JSON input.
     *
     * <p>Tool call parameters arrive as a stream of JSON snippets (via {@code ToolCallDeltaEvent}),
     * and after concatenation they form a JSON string like {@code {"path": "/some/file.java", "content": "..."}}.
     * We use simple regex extraction here to avoid introducing a full JSON parser dependency.
     *
     * @param inputBuffer Accumulated JSON input, can be null
     * @return Extracted path value, or null if not found
     */
    static String extractPathFromToolInput(StringBuilder inputBuffer) {
        if (inputBuffer == null || inputBuffer.length() == 0) {
            return null;
        }
        String json = inputBuffer.toString();
        // Try to match "path": "value" or "path":"value"
        Matcher matcher = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Reset all tracking state.
     *
     * <p>Called at the end of a session or upon exception recovery to ensure clean state.
     */
    void reset() {
        toolCallInputBuffers.clear();
        diffOldContents.clear();
        diffFilePaths.clear();
        LOGGER.log(Level.FINE, "FileDiffTracker state has been reset");
    }
}
