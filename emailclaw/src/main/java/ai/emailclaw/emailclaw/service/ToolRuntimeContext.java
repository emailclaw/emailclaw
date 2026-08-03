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

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tool runtime context.
 *
 * <p>Centrally carries the current Agent, repository and timezone information, available for built-in tools to read and write.
 *
 * <p>Added message bus service support for inter-agent communication and asynchronous tool execution tracking.
 */
public class ToolRuntimeContext {
    private static final Logger LOGGER = Logger.getLogger(ToolRuntimeContext.class.getName());
    public final AppContext repository;
    public final AgentService agentService;
    public final ProviderService providerService;
    public final ProjectService projectService;
    public ZoneId userZone = ZoneId.systemDefault();
    public AgentInfo currentAgent;

    /** Message bus service, providing inter-agent communication and asynchronous tool execution tracking capability. */
    private MessageBusService messageBusService;

    /** Spawn registry service, manages child agent metadata to support cross-replica routing and session recovery. */
    private SpawnRegistryService spawnRegistryService;

    public ToolRuntimeContext(
            AppContext repository,
            AgentService agentService,
            ProviderService providerService,
            MessageBusService messageBusService,
            SpawnRegistryService spawnRegistryService,
            ProjectService projectService) {
        this.repository = repository;
        this.agentService = agentService;
        this.providerService = providerService;
        this.messageBusService = messageBusService;
        this.spawnRegistryService = spawnRegistryService;
        this.projectService = projectService;
        this.currentAgent = agentService.currentDefault();
        LOGGER.log(
                Level.INFO,
                "ToolRuntimeContext initialization completed, current Agent: {0}",
                currentAgent.getId());
    }

    /**
     * Get message bus service.
     *
     * @return message bus service instance
     */
    public MessageBusService getMessageBusService() {
        return messageBusService;
    }

    /**
     * Get spawn registry service.
     *
     * @return spawn registry service instance
     */
    public SpawnRegistryService getSpawnRegistryService() {
        return spawnRegistryService;
    }

    public void refreshForAgent(AgentInfo agent) {
        if (agent == null) return;
        this.currentAgent = agent;
    }

    public Path currentWorkspace() {
        return repository.workspaceFor(currentAgent.getId());
    }

    public ProjectInfo currentProject() {
        return projectService.currentDefault();
    }

    public boolean isWritable(Path path) {
        if (path == null) return false;
        Path normalized = path.toAbsolutePath().normalize();

        // 1. Workspace is always writable
        if (normalized.startsWith(currentWorkspace().toAbsolutePath().normalize())) {
            return true;
        }

        // 2. Project scope
        ProjectInfo project = currentProject();
        if (project != null) {
            // Base directory is always writable
            if (project.getBaseDirectory() != null && !project.getBaseDirectory().isBlank()) {
                Path base = Path.of(project.getBaseDirectory()).toAbsolutePath().normalize();
                if (normalized.startsWith(base)) {
                    return true;
                }
            }
            // Additional dirs are writable only if checked
            if (project.getAdditionalDirs() != null) {
                for (java.util.Map.Entry<String, Boolean> entry :
                        project.getAdditionalDirs().entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().isBlank()) {
                        Path additional = Path.of(entry.getKey()).toAbsolutePath().normalize();
                        if (normalized.startsWith(additional)) {
                            return Boolean.TRUE.equals(entry.getValue());
                        }
                    }
                }
            }
        }
        return false;
    }

    public List<AgentInfo> listAgents() {
        return agentService.list();
    }
}
