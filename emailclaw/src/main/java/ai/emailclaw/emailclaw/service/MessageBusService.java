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

import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import io.agentscope.harness.agent.bus.AsyncToolRecord;
import io.agentscope.harness.agent.bus.AsyncToolRegistry;
import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import io.agentscope.harness.agent.bus.WorkspaceAsyncToolRegistry;
import io.agentscope.harness.agent.bus.WorkspaceMessageBus;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import reactor.core.publisher.Flux;

/**
 * Message bus service.
 *
 * <p>Encapsulates agentscope's {@link MessageBus} and {@link AsyncToolRegistry},
 * providing a unified asynchronous message communication infrastructure for Emailclaw.
 *
 * <p>Main functions:
 * <ul>
 *   <li>Inter-agent message routing: supports agents sending/receiving messages via inbox</li>
 *   <li>Asynchronous tool execution tracking: tracks the state of tools offloaded to background execution</li>
 *   <li>Wake-up scheduling: wakes up idle agents when background tasks complete</li>
 * </ul>
 */
public class MessageBusService {
    private static final Logger LOGGER = Logger.getLogger(MessageBusService.class.getName());

    private final ai.emailclaw.emailclaw.service.ProjectService projectService;
    private final Map<String, MessageBus> messageBusMap =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, AsyncToolRegistry> asyncToolRegistryMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    public MessageBusService(ai.emailclaw.emailclaw.service.ProjectService projectService) {
        this.projectService = projectService;
        LOGGER.log(Level.INFO, "Message bus service initialization completed");
    }

    public MessageBus getMessageBus(String projectId) {
        return messageBusMap.computeIfAbsent(
                projectId,
                id -> {
                    ai.emailclaw.emailclaw.model.ProjectInfo project = projectService.findById(id);
                    String baseDirStr = project != null ? project.getBaseDirectory() : null;
                    Path base =
                            (baseDirStr != null && !baseDirStr.isBlank())
                                    ? Path.of(FileNameUtils.expandUserHome(baseDirStr))
                                    : AppHomeConstants.HOME_RESOLVED
                                            .resolve(AppHomeConstants.PROJECTS_DIR)
                                            .resolve(id != null ? id : "default");
                    Path baseDir = base.resolve(AppHomeConstants.AGENT_WORKSPACE_DIR);
                    LocalFilesystem filesystem = new LocalFilesystem(baseDir);
                    return new WorkspaceMessageBus(filesystem, "message-bus");
                });
    }

    public AsyncToolRegistry getAsyncToolRegistry(String projectId) {
        return asyncToolRegistryMap.computeIfAbsent(
                projectId,
                id -> {
                    ai.emailclaw.emailclaw.model.ProjectInfo project = projectService.findById(id);
                    String baseDirStr = project != null ? project.getBaseDirectory() : null;
                    Path base =
                            (baseDirStr != null && !baseDirStr.isBlank())
                                    ? Path.of(FileNameUtils.expandUserHome(baseDirStr))
                                    : AppHomeConstants.HOME_RESOLVED
                                            .resolve(AppHomeConstants.PROJECTS_DIR)
                                            .resolve(id != null ? id : "default");
                    Path baseDir = base.resolve(AppHomeConstants.AGENT_WORKSPACE_DIR);
                    LocalFilesystem filesystem = new LocalFilesystem(baseDir);
                    return new WorkspaceAsyncToolRegistry(filesystem, "async-tools");
                });
    }

    public void inboxPush(String projectId, String sessionId, Map<String, Object> payload) {
        LOGGER.log(Level.FINE, "Pushing message to inbox: session={0}", sessionId);
        getMessageBus(projectId)
                .inboxPush(sessionId, payload)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Message pushed successfully: session={0}",
                                        sessionId))
                .doOnError(e -> LOGGER.log(Level.WARNING, "Message push failed: session={0}", e))
                .subscribe();
    }

    public List<BusEntry> inboxDrain(String projectId, String sessionId, int maxCount) {
        LOGGER.log(
                Level.FINE,
                "Draining inbox: session={0}, maxCount={1}",
                new Object[] {sessionId, maxCount});
        try {
            return getMessageBus(projectId).inboxDrain(sessionId, maxCount).block();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to drain inbox: session={0}", e);
            return List.of();
        }
    }

    public void publish(String projectId, String eventType, Map<String, Object> payload) {
        getMessageBus(projectId).publish(eventType, payload).subscribe();
    }

    public void enqueueWakeup(String projectId, String sessionId, String taskLabel, String logUri) {
        getMessageBus(projectId).enqueueWakeup(sessionId, taskLabel, logUri).subscribe();
    }

    public Flux<Map<String, Object>> subscribeWakeup(String projectId) {
        return getMessageBus(projectId).subscribeWakeup();
    }

    public boolean inboxHasMessages(String projectId, String sessionId) {
        try {
            return getMessageBus(projectId).inboxHasMessages(sessionId).block();
        } catch (Exception e) {
            return false;
        }
    }

    public void registerAsyncTool(String projectId, AsyncToolRecord record) {
        getAsyncToolRegistry(projectId).register(record).subscribe();
    }

    public void completeAsyncTool(String projectId, String id, String result) {
        getAsyncToolRegistry(projectId).complete(id, result).subscribe();
    }

    public void failAsyncTool(String projectId, String id, String error) {
        LOGGER.log(Level.FINE, "Marking asynchronous tool as failed: id={0}", id);
        getAsyncToolRegistry(projectId)
                .fail(id, error)
                .doOnSuccess(
                        v ->
                                LOGGER.log(
                                        Level.FINE,
                                        "Asynchronous tool marked as failed successfully: id={0}",
                                        id))
                .doOnError(
                        e ->
                                LOGGER.log(
                                        Level.WARNING,
                                        "Failed to mark asynchronous tool as failed",
                                        e))
                .subscribe();
    }

    public String sessionPublishEvent(
            String projectId, String sessionId, Map<String, Object> event) {
        try {
            return getMessageBus(projectId).sessionPublishEvent(sessionId, event).block();
        } catch (Exception e) {
            return null;
        }
    }

    public Flux<Map<String, Object>> sessionSubscribeEvents(String projectId, String sessionId) {
        return getMessageBus(projectId).sessionSubscribeEvents(sessionId);
    }

    public List<BusEntry> sessionReadEvents(
            String projectId, String sessionId, String since, int maxCount) {
        try {
            return getMessageBus(projectId).sessionReadEvents(sessionId, since, maxCount).block();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void sessionTrimEvents(String projectId, String sessionId) {
        getMessageBus(projectId).sessionTrimEvents(sessionId).subscribe();
    }

    public void close() {
        messageBusMap.values().forEach(MessageBus::close);
        messageBusMap.clear();
        asyncToolRegistryMap.clear();
    }
}
