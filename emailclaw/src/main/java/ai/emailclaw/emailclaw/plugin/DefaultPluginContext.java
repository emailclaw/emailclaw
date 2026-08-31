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
package ai.emailclaw.emailclaw.plugin;

import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.storage.ConfigManager;

public class DefaultPluginContext implements PluginContext {

    private final ChannelService channelService;
    private final ChatService chatService;
    private final AgentService agentService;
    private final ProviderService providerService;
    private final ConfigManager configManager;
    private final ProjectService projectService;

    public DefaultPluginContext(
            ChannelService channelService,
            ChatService chatService,
            AgentService agentService,
            ProviderService providerService,
            ConfigManager configManager,
            ProjectService projectService) {
        this.channelService = channelService;
        this.chatService = chatService;
        this.agentService = agentService;
        this.providerService = providerService;
        this.configManager = configManager;
        this.projectService = projectService;
    }

    @Override
    public ChannelInfo getChannelInfo(String channelId) {
        return channelService.list().stream()
                .filter(item -> item.getId().equals(channelId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ChatService chatService() {
        return chatService;
    }

    @Override
    public AgentService agentService() {
        return agentService;
    }

    @Override
    public ProviderService providerService() {
        return providerService;
    }

    @Override
    public ChannelService channelService() {
        return channelService;
    }

    @Override
    public ConfigManager configManager() {
        return configManager;
    }

    @Override
    public ProjectService projectService() {
        return projectService;
    }
}
