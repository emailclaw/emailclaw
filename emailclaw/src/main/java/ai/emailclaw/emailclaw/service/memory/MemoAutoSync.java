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
package ai.emailclaw.emailclaw.service.memory;

import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.storage.WorkspacePaths;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MEMORY.md automatic synchronization - after CompactionMiddleware generates a new session summary,
 * synchronizes and writes the summary to the MEMORY.md file in the workspace.
 *
 * <p>When AgentState is saved and its summary changes, this component is responsible for appending the new summary
 * to the timeline in MEMORY.md, allowing the agent to read cross-session long-term memory via file tools.
 */
public class MemoAutoSync {
    private static final Logger LOGGER = Logger.getLogger(MemoAutoSync.class.getName());

    private final ai.emailclaw.emailclaw.service.ProjectService projectService;
    private String lastSyncedSummary = "";

    public MemoAutoSync(ai.emailclaw.emailclaw.service.ProjectService projectService) {
        this.projectService = projectService;
        LOGGER.info("MemoAutoSync initialization completed");
    }

    /**
     * Synchronize memory summary to MEMORY.md.
     * <p>
     * Call this method after each AgentState persistence, passing in the current summary.
     * Write is only executed when the summary is different from the last synchronized value.
     *
     * @param agentId The agent ID
     * @param summary The current session summary
     */
    public void syncSummary(String agentId, String summary, String projectId) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        if (summary.equals(lastSyncedSummary)) {
            return;
        }
        lastSyncedSummary = summary;

        try {
            ai.emailclaw.emailclaw.model.ProjectInfo project = projectService.findById(projectId);
            String baseDirStr = project != null ? project.getBaseDirectory() : null;
            Path base =
                    (baseDirStr != null && !baseDirStr.isBlank())
                            ? Path.of(FileNameUtils.expandUserHome(baseDirStr))
                            : AppHomeConstants.HOME_RESOLVED
                                    .resolve(AppHomeConstants.PROJECTS_DIR)
                                    .resolve(projectId != null ? projectId : "default");
            Path baseDir = base.resolve(AppHomeConstants.AGENT_WORKSPACE_DIR);
            Path memoryMd = baseDir.resolve(agentId).resolve(WorkspacePaths.MEMORY_MD);
            Files.createDirectories(memoryMd.getParent());

            String entry = String.format("\n## %s\n\n%s\n", LocalDate.now().toString(), summary);

            if (!Files.exists(memoryMd)) {
                Files.writeString(
                        memoryMd,
                        "# Long-term Memory\n\nAutomatically generated long-term memory file.\n"
                                + entry,
                        StandardOpenOption.CREATE);
            } else {
                Files.writeString(memoryMd, entry, StandardOpenOption.APPEND);
            }

            LOGGER.log(Level.FINE, "MEMORY.md synchronized: agent={0}", agentId);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "MEMORY.md synchronization failed: agent=" + agentId, e);
        }
    }
}
