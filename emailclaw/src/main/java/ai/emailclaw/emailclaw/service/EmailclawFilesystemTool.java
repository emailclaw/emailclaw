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

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.FileInfo;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.GrepMatch;
import io.agentscope.harness.agent.filesystem.model.GrepResult;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import io.agentscope.harness.agent.workspace.WorkspacePathNormalizer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Emailclaw custom filesystem tool, replacing agentscope's default {@code FilesystemTool}.
 *
 * <p>Fixes newline mismatch bug in agentscope {@code LocalFilesystem.edit()}:
 * File content keeps {@code \r\n}, but {@code oldString} is normalized to {@code \n}, causing matching failure.
 *
 * <p>New feature: Supports passing only the {@code new_string} parameter to replace the entire file content with its value.
 */
public class EmailclawFilesystemTool {

    private static final Logger LOGGER = Logger.getLogger(EmailclawFilesystemTool.class.getName());

    private final AbstractFilesystem abstractFilesystem;
    private final WorkspacePathNormalizer pathNormalizer;
    private final ToolRuntimeContext context;

    public EmailclawFilesystemTool(
            AbstractFilesystem abstractFilesystem,
            WorkspacePathNormalizer pathNormalizer,
            ToolRuntimeContext context) {
        this.abstractFilesystem = abstractFilesystem;
        this.pathNormalizer = pathNormalizer;
        this.context = context;
        LOGGER.log(Level.INFO, "EmailclawFilesystemTool initialized successfully");
    }

    private String norm(String path) {
        return pathNormalizer != null ? pathNormalizer.normalize(path) : path;
    }

    @Tool(
            name = "read_file",
            readOnly = true,
            description =
                    "Read file content with line numbers. Supports pagination via offset and"
                            + " limit.")
    public String readFile(
            RuntimeContext runtimeContext,
            @ToolParam(name = "path", description = "File path to read") String path,
            @ToolParam(
                            name = "offset",
                            description = "Start line (0-indexed). Default: 0 (from beginning)")
                    int offset,
            @ToolParam(name = "limit", description = "Max lines to return. Default: 0 (all lines)")
                    int limit) {
        ReadResult r = abstractFilesystem.read(runtimeContext, norm(path), offset, limit);
        if (!r.isSuccess()) {
            return "Error: " + r.error();
        }
        return r.fileData() != null ? r.fileData().content() : "";
    }

    @Tool(
            name = "write_file",
            description = "Write content to a new file, creating parent directories if needed.")
    public String writeFile(
            RuntimeContext runtimeContext,
            @ToolParam(name = "path", description = "Target file path") String path,
            @ToolParam(name = "content", description = "File content to write") String content) {
        String normalizedPath = norm(path);
        if (!context.isWritable(java.nio.file.Path.of(normalizedPath))) {
            return "Error: Access denied (path is not writable or out of bounds): " + path;
        }
        WriteResult r = abstractFilesystem.write(runtimeContext, normalizedPath, content);
        return r.isSuccess() ? "Written to " + r.path() : "Error: " + r.error();
    }

    /**
     * Exact string replacement (fixes newline matching bug).
     *
     * <p>When both {@code old_string} and {@code new_string} are provided, executes exact replacement.
     * File content and search/replace strings are both normalized to {@code \n} before comparison, fixing Windows newline issues.
     *
     * <p>When only {@code new_string} is provided ({@code old_string} is null or empty),
     * replaces the entire file content with the value of {@code new_string}.
     */
    @Tool(
            name = "edit_file",
            description =
                    "Perform exact string replacement in a file. If old_string is provided,"
                        + " replaces that exact string with new_string. If old_string is omitted or"
                        + " empty, replaces the entire file content with new_string.")
    public String editFile(
            RuntimeContext runtimeContext,
            @ToolParam(name = "path", description = "File to edit") String path,
            @ToolParam(
                            name = "old_string",
                            description = "Text to find (omit or empty to replace entire file)",
                            required = false)
                    String oldString,
            @ToolParam(name = "new_string", description = "Replacement text") String newString,
            @ToolParam(
                            name = "replace_all",
                            description = "Replace all occurrences (default: false)",
                            required = false)
                    Boolean replaceAll) {

        String normalizedPath = norm(path);
        if (!context.isWritable(java.nio.file.Path.of(normalizedPath))) {
            return "Error: Access denied (path is not writable or out of bounds): " + path;
        }
        boolean shouldReplaceAll = Boolean.TRUE.equals(replaceAll);

        // Mode 2: old_string is empty -> Full text replacement
        if (oldString == null || oldString.isBlank()) {
            return replaceAll(runtimeContext, normalizedPath, newString);
        }

        // Mode 1: Exact string replacement (fixes newline bug)
        return preciseReplace(
                runtimeContext, normalizedPath, oldString, newString, shouldReplaceAll);
    }

    /**
     * Full text replacement: replaces the entire content of the file directly with the value of new_string.
     */
    private String replaceAll(RuntimeContext runtimeContext, String filePath, String newString) {
        try {
            // First confirm the file exists
            ReadResult readResult =
                    abstractFilesystem.read(runtimeContext, filePath, 0, Integer.MAX_VALUE);
            if (!readResult.isSuccess()) {
                return "Error: " + readResult.error();
            }
            // Delete old file, write new content
            abstractFilesystem.delete(runtimeContext, filePath);
            WriteResult writeResult = abstractFilesystem.write(runtimeContext, filePath, newString);
            if (!writeResult.isSuccess()) {
                return "Error: " + writeResult.error();
            }
            LOGGER.log(Level.INFO, "Full text replacement complete: {0}", filePath);
            return "Replaced entire file content: " + filePath;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Full text replacement failed: " + filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Exact string replacement (fixes newline matching bug).
     *
     * <p>Core fix: Normalizes both file content and search/replace strings to {@code \n} before comparison,
     * avoiding mismatch issues between Windows {@code \r\n} and Unix {@code \n}.
     */
    private String preciseReplace(
            RuntimeContext runtimeContext,
            String filePath,
            String oldString,
            String newString,
            boolean replaceAll) {
        try {
            ReadResult readResult =
                    abstractFilesystem.read(runtimeContext, filePath, 0, Integer.MAX_VALUE);
            if (!readResult.isSuccess()) {
                return "Error: " + readResult.error();
            }

            String content = readResult.fileData().content();

            // Normalize all newlines to \n, fixing Windows newline matching bug
            String normalizedContent = content.replace("\r\n", "\n").replace("\r", "\n");
            String normalizedOld = oldString.replace("\r\n", "\n").replace("\r", "\n");
            String normalizedNew = newString.replace("\r\n", "\n").replace("\r", "\n");

            // Execute replacement
            String result;
            int occurrences;
            if (replaceAll) {
                result = normalizedContent.replace(normalizedOld, normalizedNew);
                occurrences = countOccurrences(normalizedContent, normalizedOld);
            } else {
                int idx = normalizedContent.indexOf(normalizedOld);
                if (idx < 0) {
                    return "Error: String not found in file: '"
                            + oldString.replace("\r", "\\r").replace("\n", "\\n")
                            + "'";
                }
                // Check if it's unique
                int idx2 = normalizedContent.indexOf(normalizedOld, idx + normalizedOld.length());
                if (idx2 >= 0) {
                    return "Error: String found multiple times in file. Use replace_all=true or"
                            + " provide more context to make it unique.";
                }
                result =
                        normalizedContent.substring(0, idx)
                                + normalizedNew
                                + normalizedContent.substring(idx + normalizedOld.length());
                occurrences = 1;
            }

            // Delete old file, write new content
            abstractFilesystem.delete(runtimeContext, filePath);
            WriteResult writeResult = abstractFilesystem.write(runtimeContext, filePath, result);
            if (!writeResult.isSuccess()) {
                return "Error: " + writeResult.error();
            }

            LOGGER.log(
                    Level.INFO,
                    "Exact replacement complete: {0}, replacement count: {1}",
                    new Object[] {filePath, occurrences});
            return "Edited " + filePath + " (" + occurrences + " replacement(s))";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exact replacement failed: " + filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    @Tool(
            name = "grep_files",
            readOnly = true,
            description = "Search file contents for a literal text pattern.")
    public String grepFiles(
            RuntimeContext runtimeContext,
            @ToolParam(name = "pattern", description = "Literal text pattern to search for")
                    String pattern,
            @ToolParam(name = "path", description = "Directory or file to search") String path,
            @ToolParam(name = "glob", description = "Optional file glob filter (e.g., *.java)")
                    String glob) {
        GrepResult r = abstractFilesystem.grep(runtimeContext, pattern, norm(path), glob);
        if (!r.isSuccess()) {
            return "Error: " + r.error();
        }
        List<GrepMatch> matches = r.matches();
        if (matches == null || matches.isEmpty()) {
            return "No matches found";
        }
        return matches.stream()
                .map(m -> m.path() + ":" + m.line() + ":" + m.text())
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = "glob_files", readOnly = true, description = "Find files matching a glob pattern.")
    public String globFiles(
            RuntimeContext runtimeContext,
            @ToolParam(name = "pattern", description = "Glob pattern (e.g., **/*.java)")
                    String pattern,
            @ToolParam(name = "path", description = "Base directory to search from") String path) {
        GlobResult r = abstractFilesystem.glob(runtimeContext, pattern, norm(path));
        if (!r.isSuccess()) {
            return "Error: " + r.error();
        }
        List<FileInfo> files = r.matches();
        if (files == null || files.isEmpty()) {
            return "No matching files found";
        }
        return files.stream()
                .map(f -> f.path() + (f.isDirectory() ? "/" : " (" + f.size() + " bytes)"))
                .collect(Collectors.joining("\n"));
    }

    @Tool(
            name = "list_files",
            readOnly = true,
            description = "List files and directories at the given path.")
    public String listFiles(
            RuntimeContext runtimeContext,
            @ToolParam(name = "path", description = "Directory path to list") String path) {
        LsResult r = abstractFilesystem.ls(runtimeContext, norm(path));
        if (!r.isSuccess()) {
            return "Error: " + r.error();
        }
        List<FileInfo> infos = r.entries();
        if (infos == null || infos.isEmpty()) {
            return "Empty or not a directory: " + path;
        }
        return infos.stream()
                .map(
                        f ->
                                (f.isDirectory() ? "[DIR]  " : "[FILE] ")
                                        + f.path()
                                        + (f.isDirectory() ? "" : " (" + f.size() + " bytes)"))
                .collect(Collectors.joining("\n"));
    }
}
