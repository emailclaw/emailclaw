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
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Flux;

/**
 * Tool result diff middleware.
 *
 * <p>Read file content before and after edit_file / write_file execution, calculate line-level diff,
 * and append the {@code <<<FILE_DIFF>>>} marker to the tool result for ChatView to render an expandable diff panel.
 */
public class ToolResultDiffMiddleware implements MiddlewareBase {

    private static final Logger LOGGER = Logger.getLogger(ToolResultDiffMiddleware.class.getName());

    private static final String EDIT_FILE = "edit_file";
    private static final String WRITE_FILE = "write_file";

    /** Filesystem instance, read file content through overlay (instead of direct disk read). */
    private AbstractFilesystem filesystem;

    /**
     * Set the filesystem instance.
     *
     * @param filesystem overlay filesystem
     */
    public void setFilesystem(AbstractFilesystem filesystem) {
        this.filesystem = filesystem;
        LOGGER.log(
                Level.INFO,
                "ToolResultDiffMiddleware: filesystem set, filesystem={0}",
                filesystem != null ? filesystem.getClass().getSimpleName() : "null");
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {

        LOGGER.log(
                Level.INFO,
                "ToolResultDiffMiddleware.onActing: entering middleware, agent={0}, ctx={1}",
                new Object[] {
                    agent != null ? agent.getName() : "null", ctx != null ? "not-null" : "null"
                });

        if (input == null || input.toolCalls() == null || input.toolCalls().isEmpty()) {
            LOGGER.log(
                    Level.INFO,
                    "ToolResultDiffMiddleware.onActing: input or toolCalls is empty, skipping");
            return next.apply(input);
        }

        LOGGER.log(
                Level.INFO,
                "ToolResultDiffMiddleware.onActing: toolCalls count={0}",
                input.toolCalls().size());
        for (int i = 0; i < input.toolCalls().size(); i++) {
            ToolUseBlock call = input.toolCalls().get(i);
            LOGGER.log(
                    Level.INFO,
                    "ToolResultDiffMiddleware.onActing: toolCall[{0}] name={1}, id={2}",
                    new Object[] {i, call.getName(), call.getId()});
        }

        // Capture old content for suspected file operation calls containing a path parameter
        // key: toolCallId, value: relative path (format expected by overlay)
        Map<String, String> relativePaths = new HashMap<>();
        // key: toolCallId, value: old file content
        Map<String, String> oldContents = new HashMap<>();
        // key: toolCallId, value: absolute path (for display)
        Map<String, String> absPaths = new HashMap<>();

        for (ToolUseBlock call : input.toolCalls()) {
            String name = call.getName();
            // Allow "__fragment__" (UI display placeholder) or explicit edit commands
            if (name != null
                    && !EDIT_FILE.equals(name)
                    && !WRITE_FILE.equals(name)
                    && !"__fragment__".equals(name)) {
                LOGGER.log(
                        Level.INFO,
                        "ToolResultDiffMiddleware.onActing: tool {0} does not match"
                                + " edit_file/write_file/__fragment__, skipping",
                        name);
                continue;
            }
            String path = extractPath(call);
            LOGGER.log(
                    Level.INFO,
                    "ToolResultDiffMiddleware.onActing: tool {0} path={1}",
                    new Object[] {name, path});
            if (path == null || path.isBlank()) {
                LOGGER.log(
                        Level.INFO, "ToolResultDiffMiddleware.onActing: path is empty, skipping");
                continue;
            }
            // Normalize path to overlay expected format (forward slashes, no leading slash)
            String normalizedPath = normalizePath(path);
            String absPath = resolveToAbsolutePath(agent, path);
            LOGGER.log(
                    Level.INFO,
                    "ToolResultDiffMiddleware.onActing: normalizedPath={0}, absPath={1}",
                    new Object[] {normalizedPath, absPath});
            if (absPath == null) {
                LOGGER.log(
                        Level.INFO, "ToolResultDiffMiddleware.onActing: absPath is null, skipping");
                continue;
            }
            relativePaths.put(call.getId(), normalizedPath);
            absPaths.put(call.getId(), absPath);
            // Read old file content
            String oldContent = readFromFilesystem(ctx, agent, normalizedPath);
            LOGGER.log(
                    Level.INFO,
                    "ToolResultDiffMiddleware.onActing: old file content={0}",
                    oldContent != null ? oldContent.length() + " characters" : "null");
            if (oldContent != null) {
                oldContents.put(call.getId(), oldContent);
                LOGGER.log(
                        Level.INFO,
                        "ToolResultDiffMiddleware.onActing: captured old file content: {0} ({1}"
                                + " characters)",
                        new Object[] {absPath, oldContent.length()});
            }
        }

        LOGGER.log(
                Level.INFO,
                "ToolResultDiffMiddleware.onActing: oldContents.size={0}",
                oldContents.size());

        if (oldContents.isEmpty()) {
            LOGGER.log(
                    Level.INFO,
                    "ToolResultDiffMiddleware.onActing: oldContents is empty, executing tool"
                            + " directly");
            return next.apply(input);
        }

        LOGGER.log(
                Level.INFO,
                "ToolResultDiffMiddleware.onActing: has old content, start listening to tool"
                        + " result");
        return next.apply(input)
                .concatMap(
                        event -> {
                            LOGGER.log(
                                    Level.INFO,
                                    "ToolResultDiffMiddleware.onActing: received event {0}",
                                    event.getClass().getSimpleName());
                            if (event instanceof ToolResultEndEvent tre) {
                                String id = tre.getToolCallId();
                                LOGGER.log(
                                        Level.INFO,
                                        "ToolResultDiffMiddleware.onActing: ToolResultEndEvent,"
                                                + " toolCallId={0}, state={1}",
                                        new Object[] {id, tre.getState()});
                                String oldContent = oldContents.get(id);
                                String normalizedPath = relativePaths.get(id);
                                String absPath = absPaths.get(id);

                                // If not found by real ID, try finding records stored via
                                // "__fragment__" (empty ID)
                                if (oldContent == null && oldContents.containsKey("")) {
                                    oldContent = oldContents.get("");
                                    normalizedPath = relativePaths.get("");
                                    absPath = absPaths.get("");
                                    LOGGER.log(
                                            Level.INFO,
                                            "ToolResultDiffMiddleware.onActing: using __fragment__"
                                                    + " fallback record");
                                }

                                if (oldContent != null && normalizedPath != null) {
                                    // Read new file content (via overlay)
                                    String newContent =
                                            readFromFilesystem(ctx, agent, normalizedPath);
                                    LOGGER.log(
                                            Level.INFO,
                                            "ToolResultDiffMiddleware.onActing: new file"
                                                    + " content={0}",
                                            newContent != null
                                                    ? newContent.length() + " characters"
                                                    : "null");
                                    FileDiffRecord diffRecord =
                                            FileDiffUtils.computeDiff(
                                                    absPath != null ? absPath : normalizedPath,
                                                    oldContent,
                                                    newContent);
                                    LOGGER.log(
                                            Level.INFO,
                                            "ToolResultDiffMiddleware.onActing: diffRecord={0}",
                                            diffRecord != null
                                                    ? "linesAdded="
                                                            + diffRecord.getLinesAdded()
                                                            + ", linesDeleted="
                                                            + diffRecord.getLinesDeleted()
                                                    : "null");
                                    if (diffRecord != null
                                            && (diffRecord.getLinesAdded() > 0
                                                    || diffRecord.getLinesDeleted() > 0)) {
                                        String markup = diffRecord.toMarkup();
                                        LOGGER.log(
                                                Level.INFO,
                                                "ToolResultDiffMiddleware.onActing: generated diff"
                                                        + " markup, length={0}",
                                                markup.length());
                                        // Send additional delta event to append diff to tool result
                                        return Flux.just(
                                                new ToolResultTextDeltaEvent(
                                                        tre.getReplyId(),
                                                        id,
                                                        tre.getToolCallName(),
                                                        "\n" + markup),
                                                event);
                                    }
                                }
                            }
                            return Flux.just(event);
                        });
    }

    /**
     * Extract path parameter from tool call input.
     */
    private String extractPath(ToolUseBlock call) {
        Map<String, Object> inp = call.getInput();
        if (inp == null) {
            return null;
        }
        Object path = inp.get("path");
        return path != null ? path.toString() : null;
    }

    /**
     * Normalize path to overlay expected format: forward slashes, no leading slash.
     */
    private String normalizePath(String path) {
        return path.replace('\\', '/').replaceFirst("^/", "");
    }

    /**
     * Resolve relative path to absolute path (for display and diff calculation).
     * Do not check if file exists, because file may be in overlay and not on disk.
     */
    private String resolveToAbsolutePath(Agent agent, String relativePath) {
        if (agent instanceof HarnessAgent ha) {
            WorkspaceManager wm = ha.getWorkspaceManager();
            if (wm != null) {
                Path workspace = wm.getWorkspace();
                if (workspace != null) {
                    return workspace.resolve(relativePath).normalize().toString();
                }
            }
        }
        // Fallback: return relative path
        return relativePath;
    }

    /**
     * Read file content through overlay filesystem.
     *
     * @param ctx runtime context (from middleware parameters)
     * @param agent current agent
     * @param relativePath relative path (format expected by overlay)
     * @return file content, null on failure
     */
    private String readFromFilesystem(RuntimeContext ctx, Agent agent, String relativePath) {
        if (filesystem != null) {
            try {
                RuntimeContext rc =
                        ctx != null
                                ? ctx
                                : (agent instanceof HarnessAgent ha
                                        ? ha.getRuntimeContext()
                                        : null);
                LOGGER.log(
                        Level.INFO,
                        "ToolResultDiffMiddleware.readFromFilesystem: rc={0}, relativePath={1}",
                        new Object[] {rc != null ? "not-null" : "null", relativePath});
                if (rc != null) {
                    ReadResult result = filesystem.read(rc, relativePath, 0, Integer.MAX_VALUE);
                    LOGGER.log(
                            Level.INFO,
                            "ToolResultDiffMiddleware.readFromFilesystem: overlay read={0},"
                                    + " isSuccess={1}",
                            new Object[] {relativePath, result.isSuccess()});
                    if (result.isSuccess() && result.fileData() != null) {
                        return result.fileData().content();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(
                        Level.INFO,
                        "ToolResultDiffMiddleware.readFromFilesystem: overlay read failed: "
                                + relativePath,
                        e);
            }
        }
        // Fallback: read directly from disk
        try {
            Path p = Path.of(relativePath);
            if (Files.exists(p)) {
                String content = Files.readString(p);
                LOGGER.log(
                        Level.INFO,
                        "ToolResultDiffMiddleware.readFromFilesystem: disk read={0}, length={1}",
                        new Object[] {relativePath, content != null ? content.length() : 0});
                return content;
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.INFO,
                    "ToolResultDiffMiddleware.readFromFilesystem: disk read failed: "
                            + relativePath,
                    e);
        }
        return null;
    }
}
