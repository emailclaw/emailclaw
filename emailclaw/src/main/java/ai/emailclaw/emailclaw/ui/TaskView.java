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
package ai.emailclaw.emailclaw.ui;

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.CronJobService;
import ai.emailclaw.emailclaw.service.ProviderService;
import java.util.function.Consumer;

public class TaskView extends ChatView {

    public TaskView(
            ChatSessionInfo taskInfo,
            AgentInfo agent,
            ProjectInfo currentProject,
            AgentService agentService,
            ProviderService providerService,
            ChatService chatService,
            CronJobService cronJobService,
            Runnable onModelSettings) {
        this(
                taskInfo,
                agent,
                currentProject,
                agentService,
                providerService,
                chatService,
                cronJobService,
                onModelSettings,
                null);
    }

    /**
     * Pure DI constructor accepting an onOpenTask navigation callback.
     *
     * @param taskInfo Initial task info to load
     * @param agent Active agent information
     * @param currentProject Current project information
     * @param agentService Service managing agent instances
     * @param providerService Service managing LLM providers
     * @param chatService Service managing chat sessions and messages
     * @param cronJobService Service managing scheduled jobs
     * @param onModelSettings Callback invoked when model settings are requested
     * @param onOpenTask Callback invoked when a specific task session is requested to be opened
     */
    public TaskView(
            ChatSessionInfo taskInfo,
            AgentInfo agent,
            ProjectInfo currentProject,
            AgentService agentService,
            ProviderService providerService,
            ChatService chatService,
            CronJobService cronJobService,
            Runnable onModelSettings,
            Consumer<ChatSessionInfo> onOpenTask) {
        super(
                agentService,
                providerService,
                chatService,
                cronJobService,
                agent,
                ChatSessionInfo.KIND_TASK,
                currentProject,
                onOpenTask);
        if (taskInfo != null) {
            this.loadSession(taskInfo);
        }
    }

    public ChatSessionInfo getTaskInfo() {
        return this.getCurrentSession();
    }
}
