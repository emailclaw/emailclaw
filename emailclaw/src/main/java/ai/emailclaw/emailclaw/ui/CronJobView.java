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
import ai.emailclaw.emailclaw.model.CronJobModel.CronJobSpec;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.CronJobService;
import ai.emailclaw.emailclaw.service.ProviderService;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Main view for scheduled tasks, based on TaskView, extending to display scheduling specifications and other information.
 */
public class CronJobView implements ViewPane {

    private final BorderPane root = new BorderPane();
    private final TaskView taskView;
    private final CronJobSpec cronJobSpec;

    public CronJobView(
            CronJobSpec cronJobSpec,
            AgentInfo agent,
            AgentService agentService,
            ProviderService providerService,
            ChatService chatService,
            CronJobService cronJobService,
            Runnable onModelSettings) {
        this.cronJobSpec = cronJobSpec;

        // Find associated ChatSessionInfo
        ChatSessionInfo taskInfo = null;
        if (cronJobSpec.taskId() != null && !cronJobSpec.taskId().isBlank()) {
            List<ChatSessionInfo> tasks = chatService.sessions(agent.getId());
            taskInfo =
                    tasks.stream()
                            .filter(t -> cronJobSpec.taskId().equals(t.getId()))
                            .findFirst()
                            .orElse(null);
        }

        if (taskInfo == null) {
            taskInfo = chatService.newSession(agent.getId());
            taskInfo.setKind(ChatSessionInfo.KIND_TASK);
            taskInfo.setId(cronJobSpec.taskId() != null ? cronJobSpec.taskId() : "");
            taskInfo.setName(cronJobSpec.name() + " (Auto)");
        }

        this.taskView =
                new TaskView(
                        taskInfo,
                        agent,
                        null,
                        agentService,
                        providerService,
                        chatService,
                        cronJobService,
                        onModelSettings);

        root.setCenter(taskView.root());
        root.setRight(buildScheduleSidePanel());
    }

    private Node buildScheduleSidePanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(250);
        panel.setStyle(
                "-fx-background-color: #fdfdfd; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 0"
                        + " 1;");

        Label titleLbl = new Label("Schedule Information");
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        String st = cronJobSpec.schedule() != null ? cronJobSpec.schedule().type() : "?";
        Label typeLbl = new Label("Type: " + ("cron".equals(st) ? "Recurring" : "Once"));

        String cron =
                cronJobSpec.schedule() != null && cronJobSpec.schedule().cron() != null
                        ? cronJobSpec.schedule().cron()
                        : "-";
        Label cronLbl = new Label("Cron: " + cron);

        panel.getChildren().addAll(titleLbl, typeLbl, cronLbl);
        return panel;
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onAgentChanged(AgentInfo agent) {
        taskView.onAgentChanged(agent);
    }

    @Override
    public void refresh() {
        taskView.refresh();
    }
}
